# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY data-migration data-migration
COPY platform platform
COPY bff bff
COPY docs/contracts docs/contracts
RUN --mount=type=cache,target=/root/.m2 mvn -pl data-migration -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /src/data-migration/target/xyz-bank-data-migration-0.0.1-SNAPSHOT-exec.jar app.jar
COPY --chown=app:app data-migration/data /app/data
ENV MIGRATION_DAILY_TRANSACTIONS=file:/app/data/semana_3/transacciones.csv
ENV MIGRATION_MONTHLY_INTERESTS=file:/app/data/semana_3/intereses.csv
ENV MIGRATION_ANNUAL_ACCOUNTS=file:/app/data/semana_3/cuentas_anuales.csv
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
