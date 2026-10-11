# XYZ Bank Data Migration

Migración de datos bancarios con **Spring Boot 3.5** y **Spring Batch 5**. Procesa los CSV de `data/semana_3` mediante tres jobs independientes (Reader → Processor → Writer), con persistencia JDBC idempotente en **MySQL**, skip/retry personalizados, **particionado local** por rangos de líneas y **reanudación automática** desde el último chunk confirmado.

Documentación ampliada:

- **Plataforma completa (MySQL + PostgreSQL + config-server + eureka-server + core-service + interests-service + BFFs):** Compose en la raíz del repo — ver el [README raíz](../README.md). Ese es el camino soportado para levantar todo.
- [docs/jobs.md](docs/jobs.md) — diagramas, reinicio, idempotencia, rendimiento antes/después y equivalencia con el legacy
- [docs/mysql.md](docs/mysql.md) — Docker MySQL, conexión y consultas de reportes
- [docs/entrega/](docs/entrega/) — documentos de entrega del grupo

MySQL aislado para experimentar solo el job: [`docker-compose.yml`](docker-compose.yml) en este módulo (`data-migration/docker-compose.yml`).

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
│   ├── domain/                 SourceLine, Money, BusinessDate, Id
│   ├── application/ports/      MigrationExecutionPort
│   └── infrastructure/
│       ├── adapters/           JdbcMigrationExecutionAdapter, FirstWinsUpsert
│       └── batch/              Guard, ledger, skip/retry, partitioner, MigrationStepFactory,
│                               JobRestartLauncher, RunAllMigrationsRunner
├── dailytransactions/
│   ├── application/ports/      DailyReportWriter, DailyReportPublication, DailySummaryProjection
│   └── infrastructure/
│       ├── adapters/           JdbcDailyReportWriter (staging), JdbcDailyReportPublication,
│       │                       JdbcDailySummaryProjection
│       └── batch/              dailyTransactionsJob
├── monthlyinterests/ ...       AccountBalanceWriter → JdbcAccountBalanceWriter
└── annualreports/ ...          AnnualMovementStore → JdbcAnnualMovementStore,
                                AnnualAuditConsolidation → JdbcAnnualAuditConsolidation
```

## Jobs (resumen)

| Job | Steps | Tablas MySQL |
|---|---|---|
| `dailyTransactionsJob` | guard → `processDailyTransactionsManager` → `publishDailyTransactions` → `summarizeDailyTransactions` | `daily_transaction_lines` (staging), `daily_transaction_reports`, `daily_transaction_summaries` |
| `monthlyInterestsJob` | guard → `calculateMonthlyInterestsManager` | `account_balances` |
| `annualGenerationJob` | guard → `stageAnnualMovementsManager` → `consolidateAnnualAudit` | `annual_movements` (staging), `annual_audit_reports` |

Si el job ya tiene `SUCCESS` en `migration_executions`, se omite el process (`ALREADY_MIGRATED`). Detalle en [docs/jobs.md](docs/jobs.md).

**Resumen diario:** `daily_transaction_summaries` guarda por fecha el total de débitos, el total de créditos, la cantidad de transacciones y la cantidad de anomalías. Se recalcula completa en cada corrida.

## Reejecución automática e idempotencia

- `RunAllMigrationsRunner` (`MIGRATION_RUN_ALL=true`) lanza los tres jobs en orden con `JobRestartLauncher`. Si la última ejecución de un job para el mismo CSV quedó `FAILED`/`STOPPED`, o `STARTED` porque el proceso murió, la **reinicia** con `JobOperator.restart`: solo corren los rangos sin terminar, desde su último chunk confirmado.
- Máximo `MIGRATION_MAX_RESTARTS` reinicios por job (default 3, contados en el `JobRepository`, también entre procesos). Si se agotan, no se lanzan los jobs siguientes y el proceso sale con código 1. En Compose, `restart: "on-failure:3"` vuelve a levantar el contenedor.
- Todas las escrituras son upserts por lote (`INSERT … ON DUPLICATE KEY UPDATE` + `batchUpdate`); ante claves repetidas gana la fila de menor línea del CSV. Reprocesar un CSV no duplica ni falla.

## Escalado y resiliencia

| Parámetro | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `migration.batch.chunk-size` | `MIGRATION_CHUNK_SIZE` | `500` | Filas por commit |
| `migration.batch.throttle-limit` | `MIGRATION_THROTTLE_LIMIT` | `4` | Rangos en paralelo por job |
| `migration.batch.max-restarts` | `MIGRATION_MAX_RESTARTS` | `3` | Reinicios por job antes de salir con código 1 |
| `migration.batch.skip-limit` | `MIGRATION_SKIP_LIMIT` | `2000` | Tope de skips de dominio/parse por rango |
| `migration.batch.retry-limit` | — | `3` | Reintentos JDBC transitorios |

Cada job se **particiona localmente**: `CsvLineRangePartitioner` divide el CSV en `throttle-limit` rangos y cada rango es un worker single-thread con su propia posición de lectura (reiniciable). Se mantienen `DomainSkipPolicy`, `TransientDataAccessRetryPolicy`, `ExponentialBackOffPolicy` (1s ×2 hasta 10s), `LoggingRetryListener` y las métricas (`Step metrics ... throughputPerSec`).

Los readers normalizan el CSV con `CsvFieldNormalizer` (trim de texto/fechas, decimales `1500,50` / `1.500,50` / `1,500.50`, escala a 2 decimales). Si el monto no se puede corregir, se lanza `DomainError` y el ítem se omite.

Con `data/performance`, los tres jobs pasan de 30,2 s / 4,6 s / 9,3 s a 2,0 s / 0,3 s / 0,7 s (chunk 500, 4 rangos). Tabla completa en [docs/jobs.md](docs/jobs.md#rendimiento-antes--después).

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

Los retiros/compras con montos negativos son válidos. Cada movimiento aceptado se guarda en `annual_movements` y `consolidateAnnualAudit` escribe **una fila por `cuenta_id`** en `annual_audit_reports` (no una por línea del CSV).

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
.\mvnw.cmd verify

# En Linux / macOS:
./mvnw verify
```

`test` corre los unitarios. `verify` agrega los `*IT` (adapters JDBC, jobs, reinicio, equivalencia con el legacy), que usan **MySQL 8.4 con Testcontainers** y necesitan Docker; sin Docker se omiten.

### 3. Correr los tres jobs (con reinicio automático)

```bash
MIGRATION_RUN_ALL=true ./mvnw spring-boot:run
```

### 3b. Correr un job suelto (sin reinicio automático)

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

# Revertir entre corridas y variar MIGRATION_THROTTLE_LIMIT / MIGRATION_CHUNK_SIZE
MIGRATION_RUN_ALL=true MIGRATION_SKIP_LIMIT=100000 MIGRATION_THROTTLE_LIMIT=4 \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=performance
```

Los CSV grandes viven en `data/performance/` (ignorados por git). En los logs buscá `Step metrics` y `Starting job=... chunkSize=... throttleLimit=...`. Tabla antes/después: [docs/jobs.md](docs/jobs.md#rendimiento-antes--después).

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

Luego vuelve a ejecutar el job deseado. El script limpia tablas de negocio y de staging, `migration_executions` y metadatos `BATCH_*`.

### Volúmenes creados antes de esta versión

Las tablas de negocio ganaron la columna `source_line` y hay tablas nuevas (`daily_transaction_lines`, `daily_transaction_summaries`, `annual_movements`). La aplicación crea las tablas que faltan al arrancar, pero **no altera** tablas existentes. Si tu volumen MySQL es anterior, recrealo una vez (los datos se vuelven a migrar desde los CSV):

```bash
docker compose down -v && docker compose up -d
```

## Datos de entrada

Default: **semana_3** (~1000 filas por CSV, con ruido intencional). También existen `data/semana_1`, `data/semana_2` y CSVs sintéticos en `data/performance/` (generados). Los ITs de job usan fixtures chicos en `src/test/resources/fixtures/`, y los de equivalencia/reinicio usan `semana_3` contra los golden files de `src/test/resources/expected/semana_3/`.

| Archivo | Job |
|---|---|
| [`data/semana_3/transacciones.csv`](data/semana_3/transacciones.csv) | dailyTransactionsJob |
| [`data/semana_3/intereses.csv`](data/semana_3/intereses.csv) | monthlyInterestsJob |
| [`data/semana_3/cuentas_anuales.csv`](data/semana_3/cuentas_anuales.csv) | annualGenerationJob |

Fechas aceptadas: `yyyy-MM-dd`, `yyyy/MM/dd`, `dd-MM-yyyy`, `dd/MM/yyyy`. `skip-limit` por defecto es `2000` (por rango) para absorber el ruido de semana_3.
