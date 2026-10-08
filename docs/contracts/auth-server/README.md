# auth-server contract

`auth-server` exposes only standard OAuth 2.0 / OpenID Connect endpoints (Spring Authorization Server), so its contract is the protocol itself rather than an OpenAPI file. The authoritative, machine-readable description is the server's own discovery document:

```bash
curl -sS --cacert dev/certs/ca.crt https://localhost:9000/.well-known/openid-configuration
```

| Endpoint | Caller | Purpose |
|---|---|---|
| `GET https://localhost:9000/oauth2/authorize` | browser / embedded browser | Authorization request (`response_type=code`, PKCE `S256` required) |
| `GET/POST https://localhost:9000/login` | browser | Customer login form |
| `POST https://auth-server:9000/oauth2/token` | `bff-web`, `bff-mobile`, `bff-atm`, `interests-service` | `authorization_code` (web and mobile), `refresh_token` (web and mobile), `client_credentials` (`bff-atm` and `interests-service` only) |
| `GET https://auth-server:9000/oauth2/jwks` | `bff-web`, `bff-mobile`, `core-service`, `interests-service` | Public RS256 signing key(s) |
| `POST https://auth-server:9000/devices/{deviceId}/revocations` | `bff-mobile` | Revoke one device. HTTP Basic (`bff-mobile` client secret) plus the device's current access token (`access_token` query parameter). The token's `azp` must be `bff-mobile` and its `device_id` must equal the path. |
| `GET https://localhost:9000/actuator/health` | healthcheck | Liveness (`UP`, no details) |

The `refresh_token` grant rotates the refresh token on every use (`reuseRefreshTokens=false`). Presenting an already-rotated token, a token bound to another device, or a token for a revoked device returns `invalid_grant`. Web refresh tokens live 30 days; mobile refresh tokens live 180 days. Mobile refresh requests must send the same `device_id` the login used.

Registered clients and what each may obtain:

| Client | Type | Grants | Redirect URI | Scopes | `channel` claim |
|---|---|---|---|---|---|
| `bff-web` | confidential (`client_secret_basic`) | `authorization_code`, `refresh_token` | `https://localhost:8081/login/oauth2/code/oidc` | `openid profile web:accounts:read web:customers:read web:transactions:read web:interests:read` | `WEB` |
| `bff-mobile` | confidential (`client_secret_basic`) | `authorization_code`, `refresh_token` | `https://localhost:8082/login/oauth2/code/oidc` | `openid profile mobile:accounts:read mobile:transactions:read` | `MOBILE` |
| `bff-atm` | confidential (`client_secret_basic`) | `client_credentials` | — | `atm:read-balance atm:withdraw` | `ATM` |
| `interests-service` | confidential (`client_secret_basic`) | `client_credentials` | — | `interests:write` | `INTERESTS` |

User access tokens have `iss = https://localhost:9000` and `sub` = the customer id. `bff-atm` and `interests-service` tokens have no customer subject. Audience is `core-service`, plus `interests-service` on `bff-web` and `interests-service` tokens. See [`docs/architecture.md`](../../architecture.md#authorization-server) for the reasoning behind the public/internal URL split.

## Events

When `FEATURE_SECURITY_ALERTS` is `true` (the Compose default), `auth-server` publishes a `SecurityAlertRaised` event on the `security.alerts` topic (key `customerId`, 3 partitions, dead-letter topic `security.alerts.DLT`) each time it detects a rotated refresh token being replayed and revokes the login:

```json
{"eventId":"<uuid>","eventType":"SecurityAlertRaised","schemaVersion":1,"alertType":"REFRESH_TOKEN_REUSE","customerId":"<customer id>","occurredAt":"<ISO-8601 instant>"}
```

Publication is best effort and asynchronous: if the broker is unreachable the login is still revoked and the caller still receives `invalid_grant`. The event carries no token, device id or username. The event catalogue is in [`docs/adr/002-event-architecture.md`](../../adr/002-event-architecture.md).
