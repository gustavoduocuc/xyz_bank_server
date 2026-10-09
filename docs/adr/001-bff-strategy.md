# ADR 001: Un BFF por canal

## Estado

Aceptado. Actualizado con la autenticación por canal, el `api-gateway` interno y la descomposición del [ADR 004](004-service-decomposition.md).

## Contexto

XYZ Bank atiende tres clientes con restricciones distintas: un dashboard web rico, una app móvil sensible al ancho de banda, y un cajero automático que solo debe consultar saldo y retirar. Antes, los tres hablaban con el mismo backend monolítico y recibían los mismos datos: sobrecarga en mobile, superficie de más en el ATM, y los equipos de frontend atados a los tiempos del backend. Había que decidir cómo llegan esos clientes a la plataforma.

## Opciones

### A. Un BFF dedicado por canal (elegida)

Un deployable por canal (`bff-web`, `bff-mobile`, `bff-atm`). Cada uno es dueño de sus rutas, DTOs, agregación y autenticación. Los canales se despliegan y fallan de forma independiente.

### B. Un BFF compartido con serializadores por canal

Un solo proceso HTTP que decide por canal qué serializador usar. Menos piezas, pero cada cambio de payload y cada restricción del ATM comparten tren de release y radio de impacto. El aislamiento entre canales pasa a ser una convención de código, no un límite de proceso.

### C. Solo un API gateway con agregación

Un gateway que reparte hacia los servicios y compone las respuestas. Sirve para enrutamiento transversal, pero la agregación y la restricción de superficie del ATM siguen necesitando código de aplicación: o se empuja esa lógica al gateway (difícil de probar, sin lenguaje de dominio) o se escriben igual agregadores por canal detrás de él.

## Decisión

Opción A: un BFF por canal, delante de un `api-gateway` interno (Spring Cloud Gateway) que solo enruta.

| Canal | Payload | Autenticación | Superficie |
|---|---|---|---|
| `bff-web` | Completo: dashboard agregado (perfil de `customers-service`, cuentas y movimientos de `core-service`), historial paginado, intereses, notificaciones | OAuth2/OIDC con PKCE; sesión en cookie HttpOnly con refresh rotatorio; scopes `web:*` | Lecturas del cliente de la sesión |
| `bff-mobile` | Mínimo: resumen aplanado de cuenta con solo los campos esenciales | JWT ligado al dispositivo, revocación por dispositivo; scopes `mobile:*` | Resumen de cuenta, refresh y revocación de dispositivo |
| `bff-atm` | Mínimo: saldo y resultado del retiro | mTLS del terminal más PIN de tarjeta, sesión de 120 s; scopes `atm:read-balance` y `atm:withdraw` | Solo verificación de PIN, saldo y retiro idempotente |

Justificación:

- Los payloads ya divergen (dashboard web, resumen aplanado móvil, dos endpoints del ATM). Separarlos por proceso permite optimizar cada uno sin afectar a los demás.
- El ATM no debe ganar operaciones por accidente: un deployable aparte vuelve esa restricción estructural, y `auth-server` rechaza que un cliente pida scopes de otro canal (`invalid_scope`).
- Cada canal tiene su propia credencial y su propio modelo de amenaza; validarla en su BFF evita una cabecera de identidad confiada.
- Despliegue independiente y desarrollo en paralelo: cada equipo de frontend evoluciona su BFF y su contrato (`docs/contracts/bff-*/openapi.yaml`) sin esperar al backend.
- El gateway centraliza el descubrimiento (`lb://` vía Eureka) y los timeouts por ruta, pero no contiene lógica de canal ni valida tokens: eso queda en los BFFs y en cada servicio.

## Consecuencias

- Tres imágenes, tres puertos y tres contratos OpenAPI.
- Los BFFs no acceden a bases de datos, no contienen reglas de negocio y no comparten DTOs entre ellos.
- Cada BFF tiene circuit breaker, timeouts y reintentos solo en operaciones idempotentes hacia cada dependencia (ver `docs/architecture.md`, Fault tolerance).
- Se acepta duplicar adaptadores HTTP (cliente, mapeo de errores, filtro de correlación) entre BFFs. Lo único compartido es la librería `shared-security` (modelo de canales y scopes).
- La verificación de PIN de `bff-atm` va directa al conector TLS de `core-service` (puerto 8453) y no pasa por el gateway, para no ampliar el borde de confianza del PIN.
