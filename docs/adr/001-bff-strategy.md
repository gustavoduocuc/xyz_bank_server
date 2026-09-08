# ADR 001: BFF per channel

## Status

Accepted

## Context

XYZ Bank serves three clients with different constraints: a rich web dashboard, a bandwidth-sensitive mobile app, and an ATM that must only inquire balance and withdraw. All three need the same core banking data. We needed a strategy for how those clients reach `core-service`.

## Options

### A. Dedicated BFF per channel (chosen)

One deployable per channel (`bff-web`, `bff-mobile`, `bff-atm`). Each owns its routes, DTOs, and aggregation. Identity is enforced in that process. Channels can deploy and fail independently.

### B. Single shared BFF with channel-specific serializers

One HTTP process that branches on `X-Channel` (or later on token claims) and picks a serializer. Fewer moving parts, but every payload change and every ATM restriction shares a release train and a blast radius. Channel isolation becomes a coding convention instead of a process boundary.

### C. API gateway with aggregation

A gateway (or BFF-as-a-gateway) that fans out to `core-service` and composes responses. Attractive for cross-cutting auth and routing, but aggregation and ATM surface restriction still need application code. We would either push that logic into the gateway (hard to test, weak domain language) or still write per-channel aggregators behind it.

## Decision

Use option A: a dedicated BFF per channel.

Justification for this system:

- Payload shapes already diverge (web dashboard vs flattened mobile summary vs ATM's two endpoints).
- ATM must not grow extra operations by accident; a separate deployable makes that restriction structural.
- Independent deploy and ArchUnit-style isolation between BFF modules match the existing reactor.
- The cost is duplicated HTTP adapters (`RestClient`, error mapping, correlation filter). That duplication is accepted until the copies drift enough to extract a library.

## Consequences

- Three images, three ports, three OpenAPI files.
- `core-service` remains the only component with a database.
- Cross-cutting concerns (health, correlation id) are copied into each BFF rather than shared as a sixth module, on purpose.
- Replacing header identity later (see `docs/architecture.md`) happens inside each BFF without rewriting `core-service`.
