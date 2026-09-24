# Architecture

XYZ Bank exposes three channel-specific backends for frontend (BFFs) in front of an internal platform: `core-service` (sole PostgreSQL owner), plus `config-server`, `eureka-server`, and `interests-service` for annual interest calculation and cutover. A single Apache Kafka broker in KRaft mode carries the interest-credit saga. The legacy CSV sanitization job writes reports to a separate MySQL instance; those reports are not loaded into core-service tables.

## Topology

Each channel proves who the caller is with a real credential instead of a trusted header: OAuth2/OIDC session cookie for web, a device-bound JWT for mobile, and mTLS plus a PIN-verified session for ATM. Every client-facing edge is TLS; BFF→platform edges stay plain HTTP except the one call that carries a raw PIN, which is TLS-only by design.

`bff-web` routes interest-summary traffic to `interests-service` (feature flag on by default). Mobile and ATM keep talking to `core-service` directly. `interests-service` loads config from `config-server`, registers with Eureka, discovers `core-service` by service id (LoadBalancer), and wraps outbound HTTP calls to core with a Resilience4j circuit breaker. The annual interest summary GET stays synchronous and forwards the user bearer. Interest credit stays on that HTTP path while `FEATURE_INTEREST_CREDIT_VIA_KAFKA` is `false` (the default): `interests-service` authenticates with its service credential and JWT scope `interests:write`. With the flag `true`, the same calculation is published as `InterestCalculated` and `core-service` credits the account, then publishes the result. Decision record: [`docs/adr/002-event-architecture.md`](adr/002-event-architecture.md).

```mermaid
flowchart LR
  subgraph clients [Clients]
    WebClient[Web client]
    MobileClient[Mobile client]
    AtmClient[ATM client]
  end

  subgraph bffs [BFFs - channel auth]
    BffWeb[bff-web OAuth2/OIDC session cookie]
    BffMobile[bff-mobile device-bound JWT]
    BffAtm[bff-atm mTLS + PIN session]
  end

  subgraph platform [Platform]
    ConfigServer[config-server]
    EurekaServer[eureka-server]
    InterestsService[interests-service]
  end

  CoreService[core-service]
  CoreServicePin[core-service PIN-verification connector]
  Kafka[(Kafka KRaft)]
  Postgres[(PostgreSQL 16)]
  MySQL[(MySQL 8.4)]
  Migration[data-migration one-shot]

  WebClient -- HTTPS --> BffWeb
  MobileClient -- HTTPS --> BffMobile
  AtmClient -- HTTPS + mTLS --> BffAtm
  BffWeb -- HTTP --> InterestsService
  BffWeb -- HTTP --> CoreService
  BffMobile -- HTTP --> CoreService
  BffAtm -- HTTP --> CoreService
  BffAtm -- HTTPS --> CoreServicePin
  InterestsService --> ConfigServer
  InterestsService --> EurekaServer
  CoreService --> EurekaServer
  InterestsService -- HTTP --> CoreService
  InterestsService -- "InterestCalculated" --> Kafka
  Kafka -- "InterestCalculated" --> CoreService
  CoreService -- "credit result" --> Kafka
  Kafka -- "credit result" --> InterestsService
  CoreService --> Postgres
  CoreServicePin -.-> CoreService
  Migration --> MySQL
```

`core-service`'s PIN-verification connector (`CoreServicePin` above) is a second Tomcat connector on the same service, not a separate deployable — it shares `core-service`'s process and database access, drawn separately here only to show it terminates TLS while every other `core-service` endpoint stays plain HTTP.

What stays constant regardless of channel: one BFF per channel, `core-service` as the only database owner for banking entities, MySQL reserved for migration reports, and the existing BFF payload contracts. What each channel's credential proves and how it's validated is documented in `docs/contracts/*/openapi.yaml`. Kafka coordinates the interest credit when the feature flag is on. It does not own account state.

## Interest flows

### Query — annual interest summary

```mermaid
sequenceDiagram
  participant Web as bff-web
  participant Interests as interests-service
  participant Core as core-service
  participant Db as PostgreSQL

  Web->>Interests: GET /accounts/{id}/interest-summary
  Note over Web,Interests: user JWT + channel session
  Interests->>Core: GET /internal/accounts/{id}/interest-summary
  Note over Interests,Core: X-Service-Credential interests + user JWT
  Core->>Db: read annual_interest_summaries
  Db-->>Core: row
  Core-->>Interests: summary
  Interests-->>Web: summary
```

### Command — apply annual interest (HTTP, default)

`FEATURE_INTEREST_CREDIT_VIA_KAFKA=false`. This is the path Compose starts with, and the path the current end-to-end tests exercise.

```mermaid
sequenceDiagram
  participant Caller as interests-service client
  participant Interests as interests-service
  participant Core as core-service
  participant Db as PostgreSQL

  Caller->>Interests: POST /accounts/{id}/interest-applications?year=
  Interests->>Core: GET /internal/accounts/{id}/balance
  Note over Interests,Core: interests credential + JWT interests:write
  Core->>Db: read accounts
  Db-->>Core: balance
  Core-->>Interests: balance
  Note over Interests: rate from Config Server, compute amount
  Interests->>Core: POST /internal/accounts/{id}/interest-credits
  Note over Interests,Core: Idempotency-Key interest-{id}-{year}
  Core->>Db: credit balance, CREDIT txn, summary
  Db-->>Core: ok or 409
  Core-->>Interests: InterestCreditResponse
  Interests-->>Caller: InterestSummaryResponse
```

Optimistic locking on `accounts.version` and the unique `(account_id, year)` on summaries prevent double application; repeating the same `Idempotency-Key` replays the original credit. Outbound calls from `interests-service` to `core-service` use Resilience4j circuit breaker/retry so repeated core failures open the breaker and fail fast. With the Kafka flag on, `creditInterest` is not called, so that breaker no longer covers the credit. `fetchInterestSummary` and `fetchAccountBalance` stay on it.

### Command — apply annual interest (Kafka saga)

`FEATURE_INTEREST_CREDIT_VIA_KAFKA=true`. The POST returns the calculated summary as soon as `InterestCalculated` is published. The calculation stays `PENDING` in the in-memory repository until `interests.credit-results` closes it. The HTTP credit endpoint remains available for the flag-off path.

```mermaid
sequenceDiagram
  participant Caller as interests-service client
  participant Interests as interests-service
  participant Calculated as interests.calculated
  participant Core as core-service
  participant Db as PostgreSQL
  participant Results as interests.credit-results

  Caller->>Interests: POST /accounts/{id}/interest-applications?year=
  Interests->>Core: GET /internal/accounts/{id}/balance
  Core-->>Interests: balance
  Note over Interests: compute amount, save PENDING
  Interests->>Calculated: InterestCalculated key accountId
  Interests-->>Caller: InterestSummaryResponse
  Core->>Calculated: consume
  Core->>Db: credit plus outbox in one transaction
  Core->>Results: relay publishes InterestCreditApplied or InterestCreditRejected
  Interests->>Results: consume
  Note over Interests: close calculation APPLIED or REJECTED, idempotent by eventId
```

The partition key is `accountId`. Delivery is at-least-once. `eventId` is `interest:{accountId}:{year}`. The HTTP idempotency key on the synchronous path stays `interest-{accountId}-{year}`. A duplicate `eventId` does not credit the balance twice. A failed credit transaction leaves no outbox row. A business rejection (`VALIDATION` or `NOT_FOUND`) publishes `InterestCreditRejected` with `reason` and does not change the balance. `core-service` is the only service with an outbox, because it is the only service with a local database transaction around the credit. See [`docs/adr/002-event-architecture.md`](adr/002-event-architecture.md).
