FROM eclipse-temurin:21-jre
WORKDIR /app
COPY apps/agent-platform-server/target/agent-platform-server-*.jar app.jar
COPY apps/agent-platform-server/target/lib/ lib/
USER 10001
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.xr.agent.server.AgentPlatformApplication"]
