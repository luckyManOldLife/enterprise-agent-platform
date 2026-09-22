package com.xr.agent.server;

import com.xr.agent.api.PlatformApiFacade;

public final class AgentPlatformApplication {

    private AgentPlatformApplication() {
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(System.getProperty(
                    "server.port",
                    System.getenv().getOrDefault("PORT", "8080")));
            String host = System.getProperty(
                    "server.host",
                    System.getenv().getOrDefault("SERVER_HOST", "127.0.0.1"));
            PlatformHttpServer server = PlatformHttpServer.create(createApi(System.getenv()), host, port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "agent-platform-server-shutdown"));
            server.start();
            System.out.println("agent-platform-server started");
        } catch (IllegalArgumentException exception) {
            System.err.println("agent-platform-server configuration invalid");
            System.exit(2);
        }
    }

    private static PlatformApiFacade createApi(java.util.Map<String, String> environment) {
        String runtime = environment.getOrDefault("PLATFORM_RUNTIME", "local");
        return switch (runtime) {
            case "local" -> LocalPlatformConfiguration.createApi();
            case "postgres" -> PostgresPlatformConfiguration.createApi(environment);
            default -> throw new IllegalArgumentException("PLATFORM_RUNTIME must be local or postgres");
        };
    }
}
