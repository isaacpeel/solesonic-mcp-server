FROM amazoncorretto:25-jdk

WORKDIR /app

COPY target/solesonic-mcp-server-*.jar app.jar

EXPOSE 8181

ENTRYPOINT ["java", "-Dspring.profiles.active=prod-nginx", "-jar", "app.jar"]