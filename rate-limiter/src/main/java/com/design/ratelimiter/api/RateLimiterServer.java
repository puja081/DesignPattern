package com.design.ratelimiter.api;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * REST server that exposes rate-limited API endpoints.
 */
public class RateLimiterServer {

    private final HttpServer server;
    private final int port;

    public RateLimiterServer(int port) throws IOException {
        this.port = bindToAvailablePort(port);
        this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
        this.server.createContext("/api", new RateLimiterHandler());
        this.server.setExecutor(Executors.newFixedThreadPool(10));
    }

    private int bindToAvailablePort(int preferredPort) {
        for (int p = preferredPort; p < preferredPort + 10; p++) {
            try (var ss = new java.net.ServerSocket(p)) {
                return p;
            } catch (IOException ignored) {
            }
        }
        throw new RuntimeException("No available port in range " + preferredPort + "-" + (preferredPort + 9));
    }

    public void start() {
        server.start();
        System.out.println("Rate Limiter server started on http://localhost:" + port);
        System.out.println("  GET /api/{resource}?clientId={id}  — rate-limited request");
    }

    public void stop() {
        server.stop(0);
    }
}
