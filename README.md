# XYZ Bank Server

Plataforma BFF de XYZ Bank: tres backends por canal (`bff-web`, `bff-mobile`, `bff-atm`) frente a un `core-service` interno, más un job de migración CSV hacia MySQL.

**Esto no está listo para producción.** No hay autenticación ni autorización. Los BFFs confían en cabeceras (`X-Customer-Id`, `X-Channel` y, en ATM, `X-Terminal-Id`). Cualquier cliente que envíe esas cabeceras es tratado como ese cliente. El modelo de identidad actual es temporal.

## Prerrequisitos

- Java 21
- Docker Desktop (o un daemon Docker compatible) con Compose v2
- Maven 3.9+ (o el wrapper del módulo de migración si se usa de forma aislada)

## Arranque local (camino soportado)

Desde la raíz del repositorio:

```bash
docker compose up --build
```

Eso levanta:

| Servicio | Puerto | Rol |
|---|---|---|
| MySQL 8.4 | 3306 | Reportes de la migración CSV |
| PostgreSQL 16 | 5432 | Datos de `core-service` |
| data-migration | (one-shot) | Procesa los CSV y sale con código 0 |
| core-service | 8080 | API interna de dominio |
| bff-web | 8081 | Dashboard, historial e intereses |
| bff-mobile | 8082 | Resumen aplanado de cuenta |
| bff-atm | 8083 | Saldo y retiro |

El job espera a que MySQL esté sano. `core-service` espera a PostgreSQL **y** a que la migración termine con éxito. Los BFFs esperan a que `core-service` reporte `/actuator/health` en UP.

Para apagar: `docker compose down`. Para resetear volúmenes (incluido el seed de demo): `docker compose down -v`.

## Datos de demo

Tras un arranque limpio, PostgreSQL contiene un cliente y una cuenta fijos (Flyway `V6__seed_demo_data.sql`). No se copian filas desde MySQL.

| Recurso | UUID |
|---|---|
| Cliente | `11111111-1111-1111-1111-111111111111` |
| Cuenta | `22222222-2222-2222-2222-222222222222` |
| Número de cuenta | `1000000001` |
| Resumen de intereses | año `2025` |

## Verificar la migración (MySQL)

```bash
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT * FROM migration_executions;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM daily_transaction_reports;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM account_balances;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM annual_audit_reports;"
```

`status = SUCCESS` en `migration_executions` para `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob` indica que el job ya corrió. Un segundo `docker compose up` reutiliza el volumen y el job vuelve a salir 0 (ya migrado).

## Ejemplos de curl

Sustituye nada: estos IDs coinciden con el seed.

**bff-web — dashboard**

```bash
curl -sS http://localhost:8081/customers/11111111-1111-1111-1111-111111111111/dashboard \
  -H "X-Customer-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-Channel: web"
```

**bff-mobile — resumen de cuenta**

```bash
curl -sS http://localhost:8082/accounts/22222222-2222-2222-2222-222222222222/summary \
  -H "X-Customer-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-Channel: mobile"
```

**bff-atm — saldo y retiro**

```bash
curl -sS http://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/balance \
  -H "X-Customer-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-Channel: atm" \
  -H "X-Terminal-Id: ATM-001"

curl -sS -X POST http://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/withdrawals \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: 11111111-1111-1111-1111-111111111111" \
  -H "X-Channel: atm" \
  -H "X-Terminal-Id: ATM-001" \
  -H "Idempotency-Key: demo-withdrawal-1" \
  -d '{"amount":40.00,"currency":"USD"}'
```

Health y OpenAPI (sin cabeceras de identidad):

```bash
curl -sS http://localhost:8080/actuator/health
curl -sS http://localhost:8081/v3/api-docs
```

Contratos en el repo: [`docs/contracts/`](docs/contracts/). Arquitectura: [`docs/architecture.md`](docs/architecture.md). ADR de BFFs: [`docs/adr/001-bff-strategy.md`](docs/adr/001-bff-strategy.md).

## Tests

```bash
# Unitarios y E2E que no requieren failsafe (incluye Testcontainers saltados si no hay Docker)
mvn test

# Incluye tests de integración (`*IT`)
mvn verify
```

Los ITs de PostgreSQL/MySQL usan Testcontainers. Sin Docker se omiten (`disabledWithoutDocker`) en lugar de fallar.

## Troubleshooting

- **Puertos 3306 o 5432 ocupados.** Otro MySQL/Postgres local está usando el puerto. Para este stack esos puertos deben estar libres, o para el stack con `docker compose down` (eso no apaga bases de otros proyectos).
- **El seed de demo desapareció o el dashboard da 404.** Flyway no reinserta filas de una versión ya aplicada. Reset: `docker compose down -v` y vuelve a `up --build`.
- **La migración falló y core-service no arranca.** Compose espera `service_completed_successfully`. Revisa `docker compose logs data-migration`.
- **PostgreSQL cae con el stack ya arriba.** `GET http://localhost:8080/actuator/health` deja de reportar UP (Actuator incluye el datasource). Los BFFs no tienen base propia: su health sigue UP aunque Postgres esté caído.
- **Testcontainers skipped.** Arranca Docker Desktop y vuelve a `mvn verify`.
- **Solo quieres experimentar el job CSV.** Sigue usando [`data-migration/docker-compose.yml`](data-migration/docker-compose.yml) (MySQL aislado). El camino soportado de plataforma completa es el Compose de la raíz.

## Identidad (temporal)

| Cabecera | Quién la envía | Uso |
|---|---|---|
| `X-Customer-Id` | los tres BFFs | identidad del cliente |
| `X-Channel` | los tres BFFs | `web`, `mobile` o `atm` |
| `X-Terminal-Id` | solo ATM | obligatorio en el canal ATM |
| `Idempotency-Key` | ATM en retiros | reenvío seguro del mismo retiro |
| `X-Correlation-Id` | opcional | si falta, cada BFF genera un UUID y lo propaga a `core-service` |

No hay login, OAuth2, mTLS ni PIN. No despliegues esto en un entorno real tal como está.
