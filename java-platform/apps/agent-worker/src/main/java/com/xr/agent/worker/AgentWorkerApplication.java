package com.xr.agent.worker;

public final class AgentWorkerApplication {

    private AgentWorkerApplication() {
    }

    public static void main(String[] args) {
        try {
            AgentWorkerRuntime runtime = AgentWorkerRuntime.create(System.getenv());
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "agent-worker-shutdown"));
            System.out.println("agent-worker started");
            runtime.runUntilStopped();
        } catch (IllegalArgumentException exception) {
            System.err.println("agent-worker configuration invalid");
            System.exit(2);
        }
    }
}
