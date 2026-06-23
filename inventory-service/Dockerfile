FROM docker.io/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY shared-model/ shared-model/
RUN mvn -q -f shared-model/pom.xml -DskipTests install

COPY inventory-service/ inventory-service/
RUN mvn -q -f inventory-service/pom.xml -DskipTests package

FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
