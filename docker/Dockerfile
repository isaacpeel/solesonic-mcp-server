FROM amazoncorretto:25-jdk

WORKDIR /app

COPY target/solesonic-mcp-server-*.jar app.jar

EXPOSE 9443

ENTRYPOINT ["java", "-Dspring.profiles.active=prod,ssl", "-jar", "app.jar"]