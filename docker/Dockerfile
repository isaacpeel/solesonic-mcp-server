FROM amazoncorretto:25-jdk

WORKDIR /app

COPY target/solesonic-mcp-server-*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-Dspring.profiles.active=prod-nginx", "-jar", "app.jar"]