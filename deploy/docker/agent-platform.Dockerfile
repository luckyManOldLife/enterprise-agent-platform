FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/agent-platform-server.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
