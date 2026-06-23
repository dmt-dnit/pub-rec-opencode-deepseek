FROM docker.io/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY shared-model/ shared-model/
RUN mvn -q -f shared-model/pom.xml -DskipTests install

COPY auth-server/ auth-server/
RUN mvn -q -f auth-server/pom.xml -DskipTests package

FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/auth-server/target/*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
