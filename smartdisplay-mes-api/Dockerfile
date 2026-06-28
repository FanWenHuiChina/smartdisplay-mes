FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -Dspring-boot.repackage.classifier=exec -Dmaven.wagon.http.retryHandler.count=5 package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
# curl is used by the container healthcheck to reach the Actuator health probe
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/smartdisplay-mes-api-1.0.0-SNAPSHOT-exec.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD curl -fsS http://localhost:8080/api/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
