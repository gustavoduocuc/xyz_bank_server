# MySQL — levantar, conectar y consultar reportes

## Requisitos

- Docker y Docker Compose
- Puerto `3306` libre en el host

## Levantar la base

Desde la raíz del proyecto:

```bash
docker compose up -d
```

Esperar a que el healthcheck esté sano:

```bash
docker compose ps
```

En el primer arranque se aplica [`src/main/resources/db/schema.sql`](../src/main/resources/db/schema.sql) (tablas de negocio + ledger). Spring Batch crea las tablas `BATCH_*` al iniciar la aplicación.

## Datos de conexión

| Parámetro | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `3306` |
| Database | `xyz_bank_migration` |
| Usuario | `migration` |
| Password | `migration` |
| JDBC URL | `jdbc:mysql://localhost:3306/xyz_bank_migration` |

Root (solo administración del contenedor): usuario `root`, password `root`.

## Clientes

### CLI dentro del contenedor

```bash
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration
```

### CLI local (si tienes el cliente MySQL instalado)

```bash
mysql -h 127.0.0.1 -P 3306 -umigration -pmigration xyz_bank_migration
```

### DBeaver / MySQL Workbench / DataGrip

1. Nueva conexión MySQL
2. Host `localhost`, puerto `3306`
3. Database `xyz_bank_migration`
4. User `migration` / password `migration`
5. Probar conexión y guardar

## Consultar reportes migrados

```sql
-- Ledger de migraciones
SELECT * FROM migration_executions;

-- Reporte diario
SELECT * FROM daily_transaction_reports ORDER BY transaction_date, transaction_id;

-- Saldos con interés
SELECT * FROM account_balances ORDER BY account_id;

-- Auditoría anual
SELECT * FROM annual_audit_reports ORDER BY account_id;
```

Ejemplos útiles:

```sql
SELECT transaction_type, COUNT(*) AS total
FROM daily_transaction_reports
GROUP BY transaction_type;

SELECT account_id, previous_balance, interest_rate, final_balance
FROM account_balances
WHERE final_balance > previous_balance;

SELECT account_id, net_balance, movement_count
FROM annual_audit_reports
WHERE net_balance < 0;
```

## Revertir migración

```bash
docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration < scripts/revert-migration.sql
```

Eso vacía tablas de negocio, el ledger y (opcionalmente) metadatos Batch. Después puedes volver a ejecutar los jobs.

## Troubleshooting

| Síntoma | Qué revisar |
|---|---|
| Connection refused | `docker compose up -d` y que el contenedor esté `healthy` |
| Access denied | User/password `migration`/`migration` y DB `xyz_bank_migration` |
| Tablas de negocio vacías / no existen | Volumen nuevo: `docker compose down -v && docker compose up -d` |
| Job dice already migrated | Ver `migration_executions` o correr el script de revert |
| Puerto 3306 ocupado | Parar otro MySQL local, o crear un `docker-compose.override.yml` local (gitignored) mapeando otro puerto host, p. ej. `3307:3306`, y apuntar JDBC a ese puerto |

Detener:

```bash
docker compose down
```

Borrar datos persistentes:

```bash
docker compose down -v
```
