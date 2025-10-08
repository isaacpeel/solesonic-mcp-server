FROM openjdk:24-jdk-slim

WORKDIR /app

COPY target/solesonic-mcp-server-0.1.0.jar app.jar

EXPOSE 9443

ENTRYPOINT ["java", "-Dspring.profiles.active=prod,ssl", "-jar", "app.jar"]