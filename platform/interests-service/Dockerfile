# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY data-migration data-migration
COPY platform platform
COPY bff bff
RUN --mount=type=cache,target=/root/.m2 mvn -pl platform/interests-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /src/platform/interests-service/target/interests-service-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
