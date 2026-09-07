# XYZ Bank Data Migration

Migración de datos bancarios con **Spring Boot 3.5** y **Spring Batch 5**. Procesa los CSV de `data/semana_3` mediante tres jobs independientes (Reader → Processor → Writer), con persistencia JDBC en **MySQL**, skip/retry personalizados y process steps multithread.

Documentación ampliada:

- [docs/jobs.md](docs/jobs.md) — diagramas y flujo de cada job
- [docs/mysql.md](docs/mysql.md) — Docker MySQL, conexión y consultas de reportes
- [docs/entrega/](docs/entrega/) — documentos de entrega del grupo

## Stack

| Tecnología | Uso |
|---|---|
| Java 17+ | Lenguaje |
| Spring Boot 3.5 | Bootstrap |
| Spring Batch 5 | Jobs, steps, skip/retry |
| MySQL 8.4 | Datos de negocio + JobRepository Batch |
| Docker Compose | MySQL local |
| Maven Wrapper | Build y ejecución |

## Arquitectura

Hexagonal por módulo. Dominio sin Spring. Batch e adapters JDBC en infraestructura.

```
src/main/java/com/xyzbank/migration/
├── shared/
│   ├── application/ports/      MigrationExecutionPort
│   └── infrastructure/
│       ├── adapters/           JdbcMigrationExecutionAdapter
│       └── batch/              Guard, ledger, skip/retry, CsvFieldNormalizer
├── dailytransactions/
│   ├── application/ports/      DailyReportWriter
│   └── infrastructure/
│       ├── adapters/           JdbcDailyReportWriter
│       └── batch/              dailyTransactionsJob
├── monthlyinterests/ ...       AccountBalanceWriter → JdbcAccountBalanceWriter
└── annualreports/ ...          AnnualAuditWriter → JdbcAnnualAuditWriter
```

## Jobs (resumen)

| Job | Guard | Process | Tabla MySQL |
|---|---|---|---|
| `dailyTransactionsJob` | `checkDailyMigrationNotDone` | `processDailyTransactions` | `daily_transaction_reports` |
| `monthlyInterestsJob` | `checkMonthlyMigrationNotDone` | `calculateMonthlyInterests` | `account_balances` |
| `annualGenerationJob` | `checkAnnualMigrationNotDone` | `compileAnnualAudit` | `annual_audit_reports` |

Si el job ya tiene `SUCCESS` en `migration_executions`, se omite el process (`ALREADY_MIGRATED`). Cada lanzamiento usa `RunIdIncrementer` para crear una nueva instancia Batch y consultar el ledger. Detalle en [docs/jobs.md](docs/jobs.md).

## Escalado y resiliencia

| Parámetro | Default | Descripción |
|---|---|---|
| `migration.batch.chunk-size` | `5` | Tamaño de chunk |
| `migration.batch.throttle-limit` | `3` | Hilos del `TaskExecutor` en el process step |
| `migration.batch.skip-limit` | `2000` | Tope de skips de dominio/parse |
| `migration.batch.retry-limit` | `3` | Reintentos JDBC transitorios |

Los process steps usan **multithreading** (`SynchronizedItemStreamReader` + `TaskExecutorRepeatTemplate`), `DomainSkipPolicy`, `TransientDataAccessRetryPolicy`, `ExponentialBackOffPolicy` (1s ×2 hasta 10s), `LoggingRetryListener` (log INFO con `attempt` y `thread` en cada reintento JDBC) y listeners de métricas (`Step metrics ... throughputPerSec`). No hay particionado: MT cubre el requisito de escalado paralelo.

Los readers normalizan el CSV con `CsvFieldNormalizer` (trim de texto/fechas, decimales `1500,50` / `1.500,50` / `1,500.50`, escala a 2 decimales). Si el monto no se puede corregir, se lanza `DomainError` y el ítem se omite.

Para comparar parámetros y elegir la config óptima, ver la tabla y checklist en [docs/jobs.md](docs/jobs.md#comparación-de-parámetros-configuración-óptima-local).

## Reglas de negocio

### Transacciones diarias

Catálogo cerrado de `tipo`: solo `debito`/`débito` y `credito`/`crédito`. Valores como `invalid` o `desconocido` se omiten (`skip`); no se reinterpretan.

También se omiten:

- `monto` vacío o `<= 0`
- `fecha` inválida
- `id` vacío
- duplicados por business key (`fecha|monto|tipo`) en el mismo run

La anomalía de monto alto (`HIGH_AMOUNT`, monto > 2000) se registra en el reporte y el ítem se escribe. Los duplicados lanzan `DomainError` y se omiten (`skip`) mediante `DomainSkipPolicy`.

### Intereses mensuales

Catálogo cerrado de `tipo`: solo `ahorro`, `prestamo`/`préstamo` e `hipoteca`. Valores como `-1` o `unknown` se omiten (`skip`); no se reinterpretan como producto con tasa.

También se omiten:

- `saldo` vacío o `<= 0`
- `edad` vacía o fuera del rango 18–100
- `nombre` vacío
- `cuenta_id` vacío o duplicado en el mismo run

Tasas:

| Tipo | Condición | Tasa |
|---|---|---|
| ahorro | edad menor a 65 | 1.00% |
| ahorro | edad 65 o más | 1.50% |
| prestamo | — | 1.50% |
| hipoteca | — | 0.80% |

### Auditoría anual

Catálogo cerrado de `transaccion`: solo `deposito`/`depósito`, `retiro` y `compra`. Otros valores (p. ej. `pago`) se omiten (`skip`); no se reinterpretan.

También se omiten:

- depósito con `monto == 0`
- campos nulos / monto vacío
- fechas inválidas
- duplicados

Los retiros/compras con montos negativos son válidos. El writer consolida **una fila por `cuenta_id`** en `annual_audit_reports` (no una por línea del CSV).

### Normalización de CSV

Antes de persistir, los tres jobs recortan espacios en campos de texto/fecha y formatean montos a 2 decimales. Formatos inconsistentes se corrigen automáticamente cuando es posible; si el valor es inválido, se omite (`skip`). Los catálogos de tipo (diario, mensual y anual) normalizan tildes (`débito` → `debito`, `préstamo` → `prestamo`, `depósito` → `deposito`).

## Requisitos previos

Para poder ejecutar el proyecto necesitas tener instalado:
- **JDK 17+** (configurado en el `PATH` o mediante `JAVA_HOME`).
- **Docker** y **Docker Compose** (para levantar la base de datos MySQL local).

*Nota: No es necesario tener Maven instalado de forma global, ya que el proyecto incluye **Maven Wrapper** (`mvnw` / `mvnw.cmd`).*

## Cómo ejecutar

### 1. Levantar MySQL

```bash
docker compose up -d
```

Conexión: `localhost:3306`, DB `xyz_bank_migration`, user/password `migration`/`migration`. Más detalle en [docs/mysql.md](docs/mysql.md).

### 2. Tests

```bash
# En Windows (CMD / PowerShell):
.\mvnw.cmd test

# En Linux / macOS:
./mvnw test
```

### 3. Correr un job

```bash
# En Windows (CMD / PowerShell):
.\mvnw.cmd spring-boot:run -D"spring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionsJob"
.\mvnw.cmd spring-boot:run -D"spring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=monthlyInterestsJob"
.\mvnw.cmd spring-boot:run -D"spring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=annualGenerationJob"

# En Linux / macOS:
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionsJob"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=monthlyInterestsJob"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=annualGenerationJob"
```

Por defecto `spring.batch.job.enabled=false`.

### 4. Demo de performance (CSV sintético + comparación)

```bash
python3 scripts/generate-performance-data.py

# Comparar throttle-limit=1 vs 3 (revertir entre corridas)
./mvnw spring-boot:run -Dspring-boot.run.profiles=performance \
  -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionsJob --migration.batch.throttle-limit=1"
```

Los CSV grandes viven en `data/performance/` (ignorados por git). En los logs buscá `Step metrics` y `Starting job=... chunkSize=... throttleLimit=...`. Detalle y tabla de comparación: [docs/jobs.md](docs/jobs.md#comparación-de-parámetros-configuración-óptima-local).

### 5. Ver reportes migrados

```bash
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT * FROM migration_executions; SELECT * FROM daily_transaction_reports LIMIT 10;"
```

Consultas adicionales en [docs/mysql.md](docs/mysql.md).

## Revertir y volver a migrar

```bash
# En Linux / macOS / CMD:
docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration < scripts/revert-migration.sql

# En Windows (PowerShell):
Get-Content scripts\revert-migration.sql | docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration
```

Luego vuelve a ejecutar el job deseado. El script limpia tablas de negocio, `migration_executions` y metadatos `BATCH_*`.

## Datos de entrada

Default: **semana_3** (~1000 filas por CSV, con ruido intencional). También existen `data/semana_1`, `data/semana_2` y CSVs sintéticos en `data/performance/` (generados). Los tests unitarios de job siguen usando `semana_2`.

| Archivo | Job |
|---|---|
| [`data/semana_3/transacciones.csv`](data/semana_3/transacciones.csv) | dailyTransactionsJob |
| [`data/semana_3/intereses.csv`](data/semana_3/intereses.csv) | monthlyInterestsJob |
| [`data/semana_3/cuentas_anuales.csv`](data/semana_3/cuentas_anuales.csv) | annualGenerationJob |

Fechas aceptadas: `yyyy-MM-dd`, `yyyy/MM/dd`, `dd-MM-yyyy`, `dd/MM/yyyy`. `skip-limit` por defecto es `2000` para absorber el ruido de semana_3.
