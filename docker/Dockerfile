FROM openjdk:24-jdk-slim as build

RUN apt-get update && apt-get install -y maven && apt-get clean

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM openjdk:24-jdk-slim

WORKDIR /app

COPY --from=build /app/target/solesonic-mcp-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=8081"]