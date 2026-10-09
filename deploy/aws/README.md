# Running XYZ Bank on AWS (ECS Fargate)

This directory prepares the move to AWS; it creates nothing. It holds one ECS task definition per
service (`task-definitions/`), and `scripts/push-images-to-ecr.sh` pushes the images they use. The
files are templates: values written as `${...}` are substituted at registration time.

## Target architecture

```
                    Internet
                       |
        ALB (HTTPS, ACM certificate)            NLB (TCP 8083, TLS passthrough)
        /                        \                     |
   bff-web (8081)          bff-mobile (8082)       bff-atm (8083, mTLS terminals)
        \______________________|_______________________/
                               | private subnets, Cloud Map namespace xyz-bank.local
                         api-gateway (8090)
        ____________________|______________________________________
        |            |              |               |              |
  core-service  customers-    payments-       interests-     auth-server (9000)
   (8080/8453)   service        service         service
        |            |              |               |              |
        +---- RDS PostgreSQL ------+          MSK (Kafka)     RDS PostgreSQL (auth)
        config-server (8888)   eureka-server (8761)       RDS MySQL (data-migration job)
```

- **Traffic**: an Application Load Balancer terminates TLS (ACM) for `bff-web` and `bff-mobile`.
  `bff-atm` needs mutual TLS with the terminals, so it sits behind a Network Load Balancer that
  passes TLS through. Only the three BFFs and `auth-server` (login pages, JWKS) are reachable from
  outside; everything else lives in private subnets.
- **Service discovery**: Eureka keeps registering the services; `config-server`, `eureka-server`,
  `api-gateway`, `auth-server` and `core-service:8453` (PIN connector) are reached through Cloud Map
  names `<service>.xyz-bank.local`, which the task definitions already use.
- **Data**: RDS for PostgreSQL holds the shared `core_service` database (one schema per service)
  and a second instance holds `auth_server`; RDS for MySQL serves the `data-migration` job.
- **Messaging**: Amazon MSK replaces the single Kafka node. A one-off task must create
  `interests.calculated`, `interests.credit-results`, `transactions.confirmed` and `security.alerts`
  and their `.DLT` topics (3 partitions each), as `kafka-init` does locally.
- **Secrets**: every password and client secret is a Secrets Manager secret named
  `xyz-bank/<VARIABLE>` (for example `xyz-bank/CORE_DB_PASSWORD`, `xyz-bank/BFF_WEB_CLIENT_SECRET`),
  referenced from `secrets[].valueFrom`. No task definition contains a secret value.
- **Scaling**: one ECS service per task definition. `core-service`, `payments-service`,
  `api-gateway` and the BFFs run at least 2 tasks with target-tracking autoscaling on average CPU
  (about 60%); the services are stateless and safe to scale (outbox relays use `SKIP LOCKED`).
  `config-server` and `eureka-server` stay at 1 (2 for availability).
- **Telemetry**: logs go to CloudWatch (`awslogs`, group `/ecs/xyz-bank/<service>`). Every service
  exposes `/actuator/prometheus` on its internal port and traces at 10% sampling; AWS-side
  collection (Managed Prometheus, an OpenTelemetry collector or Zipkin) is not part of these files.

## Placeholders

| Placeholder | Meaning |
|---|---|
| `${AWS_ACCOUNT_ID}`, `${AWS_REGION}` | Account and region (roles, secret ARNs, log region) |
| `${ECR_REGISTRY}`, `${TAG}` | `<account>.dkr.ecr.<region>.amazonaws.com` and the image tag |
| `${RDS_POSTGRES_HOST}`, `${RDS_AUTH_POSTGRES_HOST}`, `${RDS_MYSQL_HOST}` | Database endpoints |
| `${MSK_BOOTSTRAP_SERVERS}` | MSK bootstrap brokers |
| `${AUTH_PUBLIC_ISSUER}`, `${BFF_WEB_PUBLIC_URL}`, `${BFF_MOBILE_PUBLIC_URL}` | Public HTTPS URLs behind the load balancers |

Register one with, for example:
`envsubst < task-definitions/core-service.json | aws ecs register-task-definition --cli-input-json file:///dev/stdin`
(the roles `xyz-bank-ecs-execution` and `xyz-bank-ecs-task` and the log groups must exist).

## Images

Build for Fargate's architecture and push: `docker compose build` (with `--platform linux/amd64`
on Apple silicon), then `AWS_ACCOUNT_ID=... AWS_REGION=... TAG=... ./scripts/push-images-to-ecr.sh`.
The ECR repositories `xyz-bank/<service>` must exist; the script creates nothing.

## Validating the task definitions

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

## Open items

- **TLS material**: the BFFs, `core-service` (PIN connector) and `auth-server` read keystores and a
  truststore from `file:/certs/...`; Fargate cannot mount secrets as files, so delivering them
  (EFS, an init step, or terminating TLS differently) is undecided. `JAVA_TOOL_OPTIONS`, which
  points the JVM at the development CA, is not set: trust for `auth-server` must come from a public
  or private CA available to the image.
- **ATM mutual TLS** end to end (NLB passthrough, terminal certificates) is described, not designed.
- **`AUTH_DEMO_PASSWORD`** creates the demo customer; production should not have it.
- **Config repo**: `config-server` serves `config-repo/` baked into its image, so a change there
  needs a new image.
- **No AWS resource is created** here: VPC, ECR, ECS services, load balancers, RDS, MSK, IAM roles,
  secrets and log groups are still to be provisioned.
