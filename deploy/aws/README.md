# Ejecutar XYZ Bank en AWS (ECS Fargate)

Este directorio prepara el paso a AWS; no crea nada. Contiene una task definition de ECS por
servicio (`task-definitions/`), y `scripts/push-images-to-ecr.sh` sube las imágenes que usan. Los
archivos son plantillas: los valores escritos como `${...}` se sustituyen al registrarlas.

## Arquitectura objetivo

```
                    Internet
                       |
        ALB (HTTPS, certificado ACM)            NLB (TCP 8083, TLS passthrough)
        /                        \                     |
   bff-web (8081)          bff-mobile (8082)       bff-atm (8083, terminales mTLS)
        \______________________|_______________________/
                               | subredes privadas, namespace Cloud Map xyz-bank.local
                         api-gateway (8090)
        ____________________|______________________________________
        |            |              |               |              |
  core-service  customers-    payments-       interests-     auth-server (9000)
   (8080/8453)   service        service         service
        |            |              |               |              |
        +---- RDS PostgreSQL ------+          MSK (Kafka)     RDS PostgreSQL (auth)
        config-server (8888)   eureka-server (8761)       RDS MySQL (job data-migration)
```

- **Tráfico**: un Application Load Balancer termina TLS (ACM) para `bff-web` y `bff-mobile`.
  `bff-atm` necesita TLS mutuo con los terminales, así que va detrás de un Network Load Balancer que
  deja pasar el TLS sin terminarlo. Desde fuera solo se alcanzan los tres BFFs y `auth-server`
  (páginas de login, JWKS); todo lo demás vive en subredes privadas.
- **Descubrimiento de servicios**: Eureka sigue registrando los servicios; `config-server`,
  `eureka-server`, `api-gateway`, `auth-server` y `core-service:8453` (conector de PIN) se alcanzan
  por los nombres de Cloud Map `<servicio>.xyz-bank.local`, que las task definitions ya usan.
- **Datos**: RDS for PostgreSQL aloja la base compartida `core_service` (un esquema por servicio)
  y una segunda instancia aloja `auth_server`; RDS for MySQL atiende el job `data-migration`.
- **Mensajería**: Amazon MSK reemplaza el nodo único de Kafka. Una tarea puntual debe crear
  `interests.calculated`, `interests.credit-results`, `transactions.confirmed` y `security.alerts`
  y sus tópicos `.DLT` (3 particiones cada uno), como hace `kafka-init` en local.
- **Secretos**: cada contraseña y secreto de cliente es un secreto de Secrets Manager llamado
  `xyz-bank/<VARIABLE>` (por ejemplo `xyz-bank/CORE_DB_PASSWORD`, `xyz-bank/BFF_WEB_CLIENT_SECRET`),
  referenciado desde `secrets[].valueFrom`. Ninguna task definition contiene el valor de un secreto.
- **Escalado**: un servicio ECS por task definition. Los tres microservicios de negocio
  (`core-service`, `customers-service`, `payments-service`), `api-gateway` y los BFFs corren con al
  menos 2 tareas y autoscaling por seguimiento de objetivo sobre la CPU promedio (cerca del 60 %);
  los servicios no tienen estado y se pueden escalar sin riesgo (los relays del outbox usan
  `SKIP LOCKED`, y los consumidores Kafka de `customers-service` comparten un grupo y guardan cada
  evento una sola vez por `eventId`). `config-server` y `eureka-server` se quedan en 1 (2 para
  disponibilidad).
- **Telemetría**: los logs van a CloudWatch (`awslogs`, grupo `/ecs/xyz-bank/<servicio>`). Cada
  servicio expone `/actuator/prometheus` en su puerto interno y traza con un muestreo del 10 %; la
  recolección del lado de AWS (Managed Prometheus, un colector de OpenTelemetry o Zipkin) no forma
  parte de estos archivos.

## Placeholders

| Placeholder | Significado |
|---|---|
| `${AWS_ACCOUNT_ID}`, `${AWS_REGION}` | Cuenta y región (roles, ARNs de secretos, región de los logs) |
| `${ECR_REGISTRY}`, `${TAG}` | `<cuenta>.dkr.ecr.<región>.amazonaws.com` y el tag de la imagen |
| `${RDS_POSTGRES_HOST}`, `${RDS_AUTH_POSTGRES_HOST}`, `${RDS_MYSQL_HOST}` | Endpoints de las bases de datos |
| `${MSK_BOOTSTRAP_SERVERS}` | Brokers de bootstrap de MSK |
| `${AUTH_PUBLIC_ISSUER}`, `${BFF_WEB_PUBLIC_URL}`, `${BFF_MOBILE_PUBLIC_URL}` | URLs HTTPS públicas detrás de los balanceadores |

Para registrar una, por ejemplo:
`envsubst < task-definitions/core-service.json | aws ecs register-task-definition --cli-input-json file:///dev/stdin`
(los roles `xyz-bank-ecs-execution` y `xyz-bank-ecs-task` y los grupos de logs deben existir).

## Imágenes

Construir para la arquitectura de Fargate y subir: `docker compose build` (con `--platform linux/amd64`
en Apple silicon), y luego `AWS_ACCOUNT_ID=... AWS_REGION=... TAG=... ./scripts/push-images-to-ecr.sh`.
Los repositorios ECR `xyz-bank/<servicio>` deben existir; el script no crea nada.

## Validar las task definitions

```bash
for f in deploy/aws/task-definitions/*.json; do
  jq -e '.requiresCompatibilities == ["FARGATE"] and .networkMode == "awsvpc"
    and (.containerDefinitions[0].image | startswith("${ECR_REGISTRY}/xyz-bank/"))
    and .containerDefinitions[0].logConfiguration.logDriver == "awslogs"
    and ((.containerDefinitions[0].healthCheck | type) == "object" or .family == "xyz-bank-data-migration")
    and ([.containerDefinitions[].environment[] | select(.name | test("PASSWORD|SECRET"))] | length == 0)
    and ([.containerDefinitions[].secrets[]? | .valueFrom | startswith("arn:aws:secretsmanager:")] | all)' "$f" > /dev/null \
    && echo "ok  $f" || echo "BAD $f"
done
```

## Pendientes

- **Material TLS**: los BFFs, `core-service` (conector de PIN) y `auth-server` leen keystores y un
  truststore desde `file:/certs/...`; Fargate no puede montar secretos como archivos, así que está
  sin decidir cómo entregarlos (EFS, un paso de inicialización, o terminar TLS de otra forma). No se
  define `JAVA_TOOL_OPTIONS`, que apunta la JVM a la CA de desarrollo: la confianza en `auth-server`
  debe venir de una CA pública o privada disponible para la imagen.
- **TLS mutuo del ATM** de punta a punta (passthrough en el NLB, certificados de terminal): está
  descrito, no diseñado.
- **`AUTH_DEMO_PASSWORD`** crea el cliente demo; producción no debería tenerlo.
- **Repositorio de configuración**: `config-server` sirve `config-repo/` empaquetado en su imagen,
  así que un cambio ahí requiere una imagen nueva.
- **Aquí no se crea ningún recurso de AWS**: falta aprovisionar VPC, ECR, servicios ECS,
  balanceadores, RDS, MSK, roles IAM, secretos y grupos de logs.
