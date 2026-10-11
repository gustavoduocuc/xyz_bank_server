# Instrucciones para ejecutar y probar cada componente

Guía paso a paso para levantar XYZ Bank en local y verificar que cada parte del sistema funciona. Los comandos están pensados para ejecutarse desde la raíz del repositorio. Los IDs de demo coinciden con el seed de Flyway:

| Recurso | Valor |
|---|---|
| Cliente | `11111111-1111-1111-1111-111111111111` |
| Cuenta | `22222222-2222-2222-2222-222222222222` |
| Tarjeta ATM | `77777777-7777-7777-7777-777777777777`, PIN `1234` |
| Login | usuario `demo`, contraseña `demo-password` |

Documentación relacionada: [README.md](README.md), [docs/architecture.md](docs/architecture.md), colección [docs/collections/xyz-bank.postman_collection.json](docs/collections/xyz-bank.postman_collection.json).

---

## 1. Prerrequisitos

- Java 21
- Docker Desktop (o un daemon Docker compatible) con Compose v2
- Maven 3.9+
- `jq` (para parsear JSON en la terminal)
- Opcional: Postman, o Newman vía `npx newman` para la prueba integral

Comprueba las versiones:

```bash
java -version
docker compose version
mvn -v
jq --version
```

---

## 2. Preparación (solo la primera vez)

```bash
# Genera la CA de desarrollo, los certificados TLS y la clave de firma de tokens
./scripts/generate-dev-tls-certs.sh

# Crea tu .env (no versionado) a partir del ejemplo
cp .env.example .env
```

Sin el paso de certificados, `auth-server`, los BFFs y el conector TLS de `core-service` no arrancan. Sin `.env`, Compose se detiene nombrando la variable que falta.

---

## 3. Levantar el stack

```bash
docker compose up --build -d
docker compose ps
```

**Resultado esperado:** todos los servicios de larga duración en `healthy`. `data-migration` debe quedar en `Exited (0)` (terminó los tres jobs).

| Servicio | Puerto host | Rol |
|---|---|---|
| MySQL | 3306 | Reportes de la migración |
| PostgreSQL | 5432 | Datos de la plataforma (un esquema por servicio) |
| auth-postgres | (interno) | Estado de `auth-server` |
| Kafka | 9092 | Mensajería |
| config-server | 8888 | Configuración (`config-repo/`) |
| eureka-server | 8761 | Service discovery |
| api-gateway | 8090 (loopback) | Entrada a los microservicios |
| interests-service | 8084 | Intereses anuales |
| core / customers / payments | (interno; vía gateway) | Microservicios de negocio |
| bff-web / bff-mobile / bff-atm | 8081 / 8082 / 8083 | Canales HTTPS |
| auth-server | 9000 | OAuth2 / OIDC (HTTPS) |
| Prometheus / Zipkin | 9090 / 9411 | Observabilidad (solo override local) |

El primer arranque puede tardar varios minutos (build + migración + healthchecks).

---

## 4. Probar cada componente

### 4.1 Parte 1 — data-migration (Spring Batch)

**Objetivo:** confirmar que los tres jobs legacy terminaron en SUCCESS.

```bash
docker compose exec mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT job_name, status FROM migration_executions"'

docker compose exec mysql sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT COUNT(*) AS reports FROM daily_transaction_reports"'
```

**Esperado:** filas `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob` con `status = SUCCESS`, y conteos > 0 en los reportes.

Detalle de los jobs: [data-migration/docs/jobs.md](data-migration/docs/jobs.md).

### 4.2 config-server y eureka-server

```bash
curl -sS http://localhost:8888/actuator/health
curl -sS http://localhost:8888/interests-service/default | head
curl -sS http://localhost:8761/actuator/health
```

Abre Eureka en http://localhost:8761. Deben figurar, entre otros: `CORE-SERVICE`, `CUSTOMERS-SERVICE`, `PAYMENTS-SERVICE`, `INTERESTS-SERVICE`, `API-GATEWAY` y los BFFs.

```bash
curl -sS -H 'Accept: application/json' http://localhost:8761/eureka/apps/CORE-SERVICE \
  | jq '.application.instance | length'
```

**Esperado:** al menos `1` instancia UP de `CORE-SERVICE`.

### 4.3 api-gateway

```bash
curl -sS http://localhost:8090/core-service/actuator/health
curl -sS http://localhost:8090/customers-service/actuator/health
curl -sS http://localhost:8090/payments-service/actuator/health
curl -sS http://localhost:8084/actuator/health
```

**Esperado:** `"status":"UP"` en cada respuesta. `core-service`, `customers-service` y `payments-service` no publican puerto propio: se alcanzan solo por el gateway.

### 4.4 auth-server

```bash
curl -sS --cacert dev/certs/ca.crt https://localhost:9000/actuator/health
curl -sS --cacert dev/certs/ca.crt https://localhost:9000/.well-known/openid-configuration | jq .issuer
curl -sS --cacert dev/certs/ca.crt https://localhost:9000/oauth2/jwks | jq '.keys | length'
```

Token de servicio (ej. `customers-admin`):

```bash
TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  -u "customers-admin:$(grep ^CUSTOMERS_ADMIN_CLIENT_SECRET= .env | cut -d= -f2)" \
  -d grant_type=client_credentials https://localhost:9000/oauth2/token | jq -r .access_token)
echo "${TOKEN:0:40}..."
```

Un cliente de canal no puede pedir scopes de otro canal:

```bash
curl -sS --cacert dev/certs/ca.crt -H "Accept: text/html" -o /dev/null -w '%{redirect_url}\n' \
  "https://localhost:9000/oauth2/authorize?response_type=code&client_id=bff-web&redirect_uri=https://localhost:8081/login/oauth2/code/oidc&scope=openid%20mobile:accounts:read&state=s&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
```

**Esperado:** la URL de redirección incluye `error=invalid_scope`.

### 4.5 bff-atm (mTLS + PIN)

Cada request al ATM necesita el certificado del terminal:

```bash
TERMINAL_CERT="dev/certs/atm-terminal/keystore.p12:xyzbank-dev"

SESSION_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/pin-verifications \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"77777777-7777-7777-7777-777777777777","pin":"1234"}' \
  | jq -r .sessionToken)

curl -sS --cacert dev/certs/ca.crt --cert-type P12 --cert "$TERMINAL_CERT" \
  https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/balance \
  -H "Authorization: Bearer $SESSION_TOKEN"

curl -sS --cacert dev/certs/ca.crt --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/withdrawals \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Idempotency-Key: demo-withdrawal-$(date +%s)" \
  -d '{"amount":10.00,"currency":"USD"}'
```

**Esperado:** PIN → 200 con `sessionToken`; saldo → 200; retiro → 201. Repetir el retiro con la **misma** `Idempotency-Key` no debita dos veces.

Notas:
- La sesión dura 120 segundos. Si recibes 422, vuelve a verificar el PIN.
- Tres PINs incorrectos seguidos bloquean la tarjeta (423). Desbloqueo: `./scripts/reset-dev-card-lock.sh`.

### 4.6 bff-web (cookie de sesión OIDC)

```bash
SESSION_COOKIE=$(./scripts/dev-web-login.sh | jq -r .session)

curl -sS --cacert dev/certs/ca.crt -b "session=$SESSION_COOKIE" \
  https://localhost:8081/customers/11111111-1111-1111-1111-111111111111/dashboard

curl -sS --cacert dev/certs/ca.crt -b "session=$SESSION_COOKIE" \
  https://localhost:8081/accounts/22222222-2222-2222-2222-222222222222/transactions

curl -sS --cacert dev/certs/ca.crt -b "session=$SESSION_COOKIE" \
  "https://localhost:8081/accounts/22222222-2222-2222-2222-222222222222/interest-summary?year=2025"

curl -sS --cacert dev/certs/ca.crt -b "session=$SESSION_COOKIE" \
  https://localhost:8081/customers/11111111-1111-1111-1111-111111111111/notifications
```

**Esperado:** 200 en dashboard, historial, resumen de intereses y feed de notificaciones.

Alternativa en el navegador: confía en `dev/certs/ca.crt`, abre `https://localhost:8081/oauth2/authorization/oidc` e inicia sesión con `demo` / `demo-password`.

### 4.7 bff-mobile (JWT ligado al dispositivo)

```bash
DEVICE_ID="demo-phone-$(date +%s)"
DEVICE_TOKEN=$(./scripts/dev-mobile-login.sh "$DEVICE_ID" | jq -r .sessionToken)

curl -sS --cacert dev/certs/ca.crt \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "X-Device-Id: $DEVICE_ID" \
  https://localhost:8082/accounts/22222222-2222-2222-2222-222222222222/summary
```

**Esperado:** 200 con el resumen aplanado. Un `X-Device-Id` distinto al del login se rechaza.

### 4.8 customers-service (vía gateway)

```bash
TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  -u "customers-admin:$(grep ^CUSTOMERS_ADMIN_CLIENT_SECRET= .env | cut -d= -f2)" \
  -d grant_type=client_credentials https://localhost:9000/oauth2/token | jq -r .access_token)

curl -sS http://localhost:8090/customers-service/internal/customers/11111111-1111-1111-1111-111111111111 \
  -H "Authorization: Bearer $TOKEN"

curl -sS -X POST http://localhost:8090/customers-service/internal/customers \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: alta-$(date +%s)" \
  -d '{"fullName":"Cliente Prueba","email":"prueba@xyzbank.cl","phone":"+56911111111","address":"Av. Demo 1"}'
```

**Esperado:** GET del demo → 200; POST → 201. Repetir el POST con la misma `Idempotency-Key` devuelve el mismo cliente.

### 4.9 core-service — cuentas (vía gateway)

```bash
ACC_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  -u "accounts-admin:$(grep ^ACCOUNTS_ADMIN_CLIENT_SECRET= .env | cut -d= -f2)" \
  -d grant_type=client_credentials https://localhost:9000/oauth2/token | jq -r .access_token)

curl -sS -X POST http://localhost:8090/core-service/internal/accounts \
  -H "Authorization: Bearer $ACC_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: apertura-$(date +%s)" \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","currency":"USD","alias":"Ahorro prueba"}'
```

**Esperado:** 201. Cliente inexistente → 422. `customers-service` caído → 503 y no se crea nada.

Lecturas con el token del usuario (mismo valor que la cookie `session` de bff-web):

```bash
WEB_TOKEN=$(./scripts/dev-web-login.sh | jq -r .session)
curl -sS http://localhost:8090/core-service/internal/accounts/22222222-2222-2222-2222-222222222222/balance \
  -H "Authorization: Bearer $WEB_TOKEN"
```

### 4.10 payments-service (vía gateway)

```bash
PAY_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  -u "payments-admin:$(grep ^PAYMENTS_ADMIN_CLIENT_SECRET= .env | cut -d= -f2)" \
  -d grant_type=client_credentials https://localhost:9000/oauth2/token | jq -r .access_token)

# Usa el id de una cuenta abierta en el paso anterior, o la cuenta demo
DEST=22222222-2222-2222-2222-222222222222

curl -sS -X POST http://localhost:8090/payments-service/internal/deposits \
  -H "Authorization: Bearer $PAY_TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: deposito-$(date +%s)" \
  -d "{\"destinationAccountId\":\"$DEST\",\"amount\":50.00,\"currency\":\"USD\"}"
```

**Esperado:** 201 con `status: COMPLETED`. Una transferencia con la misma `Idempotency-Key` no mueve saldo dos veces. Fondos insuficientes → 201 con `status: REJECTED`. Si `core-service` está caído, el pago queda `PENDING`.

### 4.11 interests-service y Kafka

```bash
INTERESTS_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  -u "interests-service:$(grep ^INTERESTS_SERVICE_CLIENT_SECRET= .env | cut -d= -f2)" \
  -d grant_type=client_credentials https://localhost:9000/oauth2/token | jq -r .access_token)

WEB_TOKEN=$(./scripts/dev-web-login.sh | jq -r .session)

curl -sS "http://localhost:8084/accounts/22222222-2222-2222-2222-222222222222/interest-summary?year=2025" \
  -H "Authorization: Bearer $WEB_TOKEN"

# El año 2025 ya está en el seed; usa un año libre (ej. 2027)
curl -sS -X POST \
  -H "Authorization: Bearer $INTERESTS_TOKEN" \
  "http://localhost:8084/accounts/22222222-2222-2222-2222-222222222222/interest-applications?year=2027"
```

Saga de punta a punta (cálculo, crédito, idempotencia y mensaje al DLT):

```bash
./scripts/verify-interest-saga.sh
```

**Esperado:** el script termina con un mensaje de éxito ("The interest saga works end to end on this stack").

### 4.12 Tolerancia a fallos (BFFs)

```bash
curl -sS http://127.0.0.1:9081/actuator/circuitbreakers   # bff-web
curl -sS http://127.0.0.1:9082/actuator/circuitbreakers   # bff-mobile
curl -sS http://127.0.0.1:9083/actuator/circuitbreakers   # bff-atm
```

Opcional — observar un 503 cuando cae el núcleo:

```bash
docker compose stop core-service
# Una lectura del BFF web debería responder 503 ProblemDetail, no datos en caché
docker compose start core-service
```

### 4.13 Escalado horizontal

```bash
docker compose up -d --no-build \
  --scale core-service=2 --scale customers-service=2 --scale payments-service=2

# En Eureka (http://localhost:8761) deben verse 2 instancias UP de cada uno
./scripts/smoke-atm-scaled.sh

# Volver a una réplica
docker compose up -d --no-build \
  --scale core-service=1 --scale customers-service=1 --scale payments-service=1
```

**Esperado:** `smoke-atm-scaled.sh` confirma que dos réplicas de `core-service` atienden el flujo ATM.

### 4.14 Observabilidad

- Prometheus: http://localhost:9090 → Status → Targets. Los servicios scrapeados deben estar `up`.
- Zipkin: http://localhost:9411. Tras las pruebas anteriores, aparecen trazas HTTP y Kafka.

```bash
curl -sS -o /dev/null -w 'prometheus %{http_code}\n' http://localhost:9090/-/ready
curl -sS -o /dev/null -w 'zipkin %{http_code}\n' http://localhost:9411/zipkin/
```

---

## 5. Prueba integral con Postman / Newman

### Desde Postman

1. Importa [docs/collections/xyz-bank.postman_collection.json](docs/collections/xyz-bank.postman_collection.json).
2. Settings → General → desactiva *SSL certificate verification*.
3. Settings → Certificates → agrega host `localhost`, puerto `8083`, PFX `dev/certs/atm-terminal/keystore.p12`, passphrase `xyzbank-dev`.
4. Ejecuta `./scripts/dev-web-login.sh` y copia `session` → variable `webAccessToken`, `refreshToken` → `webRefreshToken`.
5. Elige un `deviceId` nuevo, ponlo en la variable `deviceId`, ejecuta `./scripts/dev-mobile-login.sh <deviceId>` y copia `sessionToken` / `refreshToken` a las variables mobile.
6. Corre la colección en orden con el Runner (carpeta 01 obtiene los tokens de servicio).

La última request de mobile **revoca** el dispositivo: cada corrida necesita un `deviceId` nuevo.

### Desde la terminal (Newman)

```bash
DEVICE_ID="postman-$(date +%s)"
WEB=$(./scripts/dev-web-login.sh)
MOB=$(./scripts/dev-mobile-login.sh "$DEVICE_ID")
echo "[{\"name\":\"atm\",\"matches\":[\"https://localhost:8083/*\"],\"pfx\":{\"src\":\"$PWD/dev/certs/atm-terminal/keystore.p12\"},\"passphrase\":\"xyzbank-dev\"}]" > /tmp/atm-cert.json

npx newman run docs/collections/xyz-bank.postman_collection.json --insecure \
  --ssl-client-cert-list /tmp/atm-cert.json \
  --env-var "deviceId=$DEVICE_ID" \
  --env-var "webAccessToken=$(jq -r .session <<<"$WEB")" \
  --env-var "webRefreshToken=$(jq -r .refreshToken <<<"$WEB")" \
  --env-var "mobileSessionToken=$(jq -r .sessionToken <<<"$MOB")" \
  --env-var "mobileRefreshToken=$(jq -r .refreshToken <<<"$MOB")"
```

**Esperado:** 70 requests, 0 fallos en assertions.

---

## 6. Tests automatizados

Los tests de integración de `core-service` levantan el conector de PIN en el puerto fijo **8453**, el mismo que publica el contenedor. Apaga el stack antes:

```bash
docker compose down
mvn verify
```

Sin Docker, los tests con Testcontainers se omiten (`disabledWithoutDocker`) en lugar de fallar. Para solo unitarios: `mvn test`.

---

## 7. Apagar y limpiar

```bash
docker compose down          # detiene contenedores; conserva volúmenes (seed y migración)
docker compose down -v       # también borra volúmenes (arranque limpio la próxima vez)
```

---

## 8. Problemas frecuentes

| Síntoma | Qué hacer |
|---|---|
| Servicios TLS no arrancan / faltan archivos en `dev/certs/` | `./scripts/generate-dev-tls-certs.sh` y recrear contenedores |
| Compose falla por variable faltante | Comprueba que existe `.env` (`cp .env.example .env`) |
| MySQL "Access denied" con usuario `migration` | Usa las variables del contenedor (`$MYSQL_USER` / `$MYSQL_PASSWORD`), no la contraseña hardcodeada |
| Mobile login `access_denied` / dispositivo revocado | Usa un `deviceId` nuevo |
| ATM responde 423 | `./scripts/reset-dev-card-lock.sh` |
| `mvn verify` falla por puerto 8453 | `docker compose down` antes de los tests |
| Navegador no confía en HTTPS local | Instala `dev/certs/ca.crt` en el sistema o el navegador (ver [README.md](README.md#certificados-tls-de-desarrollo)) |

Más detalle en la sección [Troubleshooting](README.md#troubleshooting) del README.
