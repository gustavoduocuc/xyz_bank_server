# ADR 004: Descomposición en microservicios por capacidad de negocio

## Estado

Aceptado. Primer paso: extracción de `customers-service`.

## Contexto

`core-service` concentraba todos los datos bancarios: clientes, cuentas, movimientos, retiros, intereses y PIN del cajero. La Parte 3 pide repartir esa responsabilidad en microservicios por capacidad de negocio, sin perder las garantías de escritura ya acordadas (idempotencia por `Idempotency-Key`, bloqueo optimista) ni la autenticación por tokens de `auth-server`.

## Decisión

- **Tres servicios de dominio.**
  - `core-service` es **Gestión de Cuentas**: cuentas, saldos, movimientos, retiros, crédito de intereses y PIN del cajero.
  - `customers-service` (:8085) es **Gestión de Clientes**: datos personales y perfiles.
  - `payments-service` (:8086) será **Procesamiento de Pagos**, en un cambio posterior.
- **Una PostgreSQL compartida, un esquema por servicio.**
  - Cada servicio lee y escribe solo su esquema, nunca hace joins entre esquemas y corre su propio Flyway, con su tabla de historial dentro de su esquema.
  - `customers-service` usa el esquema `customers`.
  - Los identificadores que cruzan servicios (por ejemplo `accounts.customer_id`) quedan como valores sin clave foránea.
- **El dueño del dato es el único que lo escribe.** Toda escritura ocurre en el servicio dueño, es idempotente por `Idempotency-Key` y usa bloqueo optimista sobre el agregado.
- **Comunicación síncrona por nombre de Eureka con Resilience4j.** Cada llamada remota tiene timeout, circuit breaker y, si es una lectura, reintento. Los BFFs mantienen un breaker por servicio.
- **Seguridad estándar en los servicios nuevos.**
  - `customers-service` usa `spring-boot-starter-oauth2-resource-server`: valida firma (JWKS de `auth-server`), emisor y audiencia `customers-service`, y exige un scope por endpoint.
  - La administración de clientes usa un cliente `client_credentials` propio, `customers-admin`, con scopes `customers:read` y `customers:write`.
- **Extracción de clientes.**
  - `customers-service` siembra al cliente demo.
  - `core-service` elimina su tabla `customers` y sus FKs (Flyway `V14`) y deja de servir `GET /internal/customers/{id}`.
  - `bff-web` lee el perfil desde `customers-service`.

## Consecuencias

- El aislamiento entre esquemas es por convención: los servicios comparten base de datos y usuario. Un rol por esquema queda como mejora posterior.
- `core-service` ya no puede verificar que un cliente exista: `GET /internal/customers/{id}/accounts` devuelve `[]` para un id sin cuentas. El 404 del dashboard viene ahora de `customers-service`.
- No hubo migración de filas entre esquemas: el único dato de clientes era el seed. En un volumen existente, `V14` borra `public.customers`.
- Hay un servicio más que levantar, monitorear y versionar, con su propio contrato en `docs/contracts/customers-service/`.
