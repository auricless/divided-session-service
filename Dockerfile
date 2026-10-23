FROM maven:3.10.0-eclipse-temurin-17-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY session-manager-core/pom.xml session-manager-core/pom.xml
COPY session-manager-service/pom.xml session-manager-service/pom.xml
COPY session-manager-core/src session-manager-core/src
COPY session-manager-service/src session-manager-service/src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17.0.20.1_1-jre-alpine

RUN addgroup -S -g 10001 session \
    && adduser -S -D -H -u 10001 -G session session
WORKDIR /app
COPY --from=build /workspace/session-manager-service/target/session-manager-service-1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=20s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
