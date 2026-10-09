# ADR 004: Descomposición en microservicios por capacidad de negocio

## Estado

Aceptado e implementado: `customers-service` extraído, ciclo de vida de cuentas en `core-service` y `payments-service` creado.

## Contexto

`core-service` concentraba todos los datos bancarios: clientes, cuentas, movimientos, retiros, intereses y PIN del cajero. La Parte 3 pide repartir esa responsabilidad en microservicios por capacidad de negocio, sin perder las garantías de escritura ya acordadas (idempotencia por `Idempotency-Key`, bloqueo optimista) ni la autenticación por tokens de `auth-server`.

## Decisión

- **Tres servicios de dominio.**
  - `core-service` es **Gestión de Cuentas**: apertura, mantenimiento y cierre de cuentas, saldos, asientos (`/internal/postings`), movimientos, retiros, crédito de intereses y PIN del cajero.
  - `customers-service` (:8085) es **Gestión de Clientes**: datos personales, perfiles y el feed de notificaciones por cliente.
  - `payments-service` (:8086) es **Procesamiento de Pagos**: transferencias, depósitos y pagos de cuentas. Registra cada pago en su esquema y pide a `core-service` que aplique los asientos.
- **Se conserva `core-service` como servicio de cuentas en lugar de crear un `accounts-service` nuevo.** Ya contenía las cuentas, los saldos con bloqueo optimista, el outbox y la saga de intereses; moverlos a un deployable nuevo habría obligado a reescribir los BFFs, la saga y los contratos sin cambiar ninguna capacidad.
- **Una PostgreSQL compartida, un esquema por servicio.**
  - Cada servicio lee y escribe solo su esquema, nunca hace joins entre esquemas y corre su propio Flyway, con su tabla de historial dentro de su esquema.
  - `customers-service` usa el esquema `customers`, `payments-service` el esquema `payments` e `interests-service` el esquema `interests`.
  - Los identificadores que cruzan servicios (por ejemplo `accounts.customer_id`) quedan como valores sin clave foránea.
- **El dueño del dato es el único que lo escribe.** Toda escritura ocurre en el servicio dueño, es idempotente por `Idempotency-Key` y usa bloqueo optimista sobre el agregado.
- **Comunicación síncrona por nombre de Eureka con Resilience4j.** Cada llamada remota tiene timeout, circuit breaker y, si es una lectura, reintento. Los BFFs entran a la plataforma por `api-gateway` (Spring Cloud Gateway, rutas `lb://`) y mantienen un breaker por servicio.
  - `core-service` → `customers-service` al abrir una cuenta: si no responde, 503 y no se crea la cuenta.
  - `payments-service` → `core-service` al aplicar un pago: si no responde, el pago queda `PENDING` y repetir la misma `Idempotency-Key` lo completa; un rechazo de negocio queda `REJECTED` sin reintento.
- **Comunicación asíncrona por Kafka.** `core-service` publica `transactions.confirmed` (cada movimiento confirmado, incluidos los asientos de pagos) y `security.alerts` por su outbox; `auth-server` publica `security.alerts`; `customers-service` consume ambos para el feed de notificaciones. Ver el [ADR 002](002-event-architecture.md).
- **Seguridad estándar en todos los servicios.**
  - `core-service`, `customers-service`, `payments-service` e `interests-service` usan `spring-boot-starter-oauth2-resource-server`: validan firma (JWKS de `auth-server`), emisor y audiencia propia, y exigen un scope por endpoint.
  - La administración de clientes usa un cliente `client_credentials` propio, `customers-admin`, con scopes `customers:read` y `customers:write`; los pagos usan `payments:read` y `payments:write`.
- **Escalado horizontal.** Los servicios no guardan estado en memoria ni fijan `container_name`; el outbox se reclama con `SELECT ... FOR UPDATE SKIP LOCKED` y los consumidores son idempotentes por `eventId`, así que cada servicio admite varias réplicas detrás de Eureka.
- **Extracción de clientes.**
  - `customers-service` siembra al cliente demo.
  - `core-service` elimina su tabla `customers` y sus FKs (Flyway `V14`) y deja de servir `GET /internal/customers/{id}`.
  - `bff-web` lee el perfil desde `customers-service`.

## Consecuencias

- El aislamiento entre esquemas es por convención: los servicios comparten base de datos y usuario. Un rol por esquema queda como mejora posterior.
- `core-service` ya no puede verificar que un cliente exista: `GET /internal/customers/{id}/accounts` devuelve `[]` para un id sin cuentas. El 404 del dashboard viene ahora de `customers-service`.
- No hubo migración de filas entre esquemas: el único dato de clientes era el seed. En un volumen existente, `V14` borra `public.customers`.
- Hay dos servicios más que levantar, monitorear y versionar, con sus contratos en `docs/contracts/customers-service/` y `docs/contracts/payments-service/`.
- Un pago entre servicios no es una transacción distribuida: la consistencia descansa en la idempotencia de `/internal/postings` por `paymentId` y en que un pago `PENDING` se completa repitiendo su `Idempotency-Key`. No hay reintento automático de los pendientes.
