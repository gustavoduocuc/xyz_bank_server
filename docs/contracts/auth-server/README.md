# auth-server contract

`auth-server` exposes only standard OAuth 2.0 / OpenID Connect endpoints (Spring Authorization Server), so its contract is the protocol itself rather than an OpenAPI file. The authoritative, machine-readable description is the server's own discovery document:

```bash
curl -sS --cacert dev/certs/ca.crt https://localhost:9000/.well-known/openid-configuration
```

| Endpoint | Caller | Purpose |
|---|---|---|
| `GET https://localhost:9000/oauth2/authorize` | browser / embedded browser | Authorization request (`response_type=code`, PKCE `S256` required) |
| `GET/POST https://localhost:9000/login` | browser | Customer login form |
| `POST https://auth-server:9000/oauth2/token` | `bff-web`, `bff-mobile` containers | Code exchange (`grant_type=authorization_code` only) |
| `GET https://auth-server:9000/oauth2/jwks` | `bff-web`, `bff-mobile` containers | Public RS256 signing key(s) |
| `GET https://localhost:9000/actuator/health` | healthcheck | Liveness (`UP`, no details) |

Registered clients and what each may obtain:

| Client | Type | Redirect URI | Scopes | `channel` claim |
|---|---|---|---|---|
| `bff-web` | confidential (`client_secret_basic`) | `https://localhost:8081/login/oauth2/code/oidc` | `openid profile web:accounts:read web:customers:read web:transactions:read web:interests:read` | `WEB` |
| `bff-mobile` | public (`none`) | `https://localhost:8082/login/oauth2/code/oidc` | `openid profile mobile:accounts:read mobile:transactions:read` | `MOBILE` |

Every issued token has `iss = https://localhost:9000` and `sub` = the customer id. See [`docs/architecture.md`](../../architecture.md#authorization-server) for the reasoning behind the public/internal URL split.
