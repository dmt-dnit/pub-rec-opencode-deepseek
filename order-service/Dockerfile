FROM docker.io/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY shared-model/ shared-model/
RUN mvn -q -f shared-model/pom.xml -DskipTests install

COPY order-service/ order-service/
RUN mvn -q -f order-service/pom.xml -DskipTests package

FROM docker.io/library/eclipse-temurin:21-jre
# curl is needed for the compose-level healthcheck (CMD curl -f /actuator/health).
# eclipse-temurin:21-jre (Ubuntu 22.04 base) does not ship curl by default.
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/order-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
