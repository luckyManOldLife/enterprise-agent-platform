FROM eclipse-temurin:21-jre
WORKDIR /app
COPY apps/agent-worker/target/agent-worker-*.jar app.jar
COPY apps/agent-worker/target/lib/ lib/
USER 10001
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.xr.agent.worker.AgentWorkerApplication"]
