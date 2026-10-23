FROM eclipse-temurin:17.0.20.1_1-jre-alpine

RUN addgroup -S -g 10001 session \
    && adduser -S -D -H -u 10001 -G session session
WORKDIR /app
COPY target/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=5s --start-period=20s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
