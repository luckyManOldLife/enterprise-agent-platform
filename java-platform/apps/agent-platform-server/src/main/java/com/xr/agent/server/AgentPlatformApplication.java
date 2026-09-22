package com.xr.agent.server;

public final class AgentPlatformApplication {

    private AgentPlatformApplication() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty(
                "server.port",
                System.getenv().getOrDefault("PORT", "8080")));
        PlatformHttpServer server = PlatformHttpServer.create(
                LocalPlatformConfiguration.createApi(),
                port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("agent-platform-server listening on http://127.0.0.1:" + server.port());
    }
}
