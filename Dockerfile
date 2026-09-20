FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
COPY couple-common/pom.xml couple-common/pom.xml
COPY couple-service/pom.xml couple-service/pom.xml
COPY couple-api/pom.xml couple-api/pom.xml
RUN mvn -q -pl couple-api -am dependency:go-offline

COPY couple-common couple-common
COPY couple-service couple-service
COPY couple-api couple-api
RUN mvn -q -pl couple-api -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/couple-api/target/couple-api-1.0.0-SNAPSHOT.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
