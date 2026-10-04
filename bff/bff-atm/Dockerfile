# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY data-migration data-migration
COPY platform platform
COPY bff bff
COPY docs/contracts docs/contracts
RUN --mount=type=cache,target=/root/.m2 mvn -pl bff/bff-atm -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /src/bff/bff-atm/target/bff-atm-0.0.1-SNAPSHOT-exec.jar app.jar
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
