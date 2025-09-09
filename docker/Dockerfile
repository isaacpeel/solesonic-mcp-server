FROM openjdk:24-jdk-slim

WORKDIR /app

COPY target/solesonic-mcp-server-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 9443

ENTRYPOINT ["java", "-jar", "app.jar"]