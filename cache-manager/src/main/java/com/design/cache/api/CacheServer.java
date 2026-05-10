package com.design.cache.api;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Lightweight REST server built on the JDK's built-in HttpServer.
 * Uses a thread pool to handle concurrent requests,
 * ensuring high availability of the cache API.
 */
public class CacheServer {

    private final HttpServer server;
    private final int port;

    public CacheServer(int port) throws IOException {
        this.port = bindToAvailablePort(port);
        this.server = HttpServer.create(new InetSocketAddress(this.port), 0);
        this.server.createContext("/cache", new CacheHandler());
        this.server.setExecutor(Executors.newFixedThreadPool(10));
    }

    private int bindToAvailablePort(int preferredPort) {
        for (int p = preferredPort; p < preferredPort + 10; p++) {
            try (var ss = new java.net.ServerSocket(p)) {
                return p;
            } catch (IOException ignored) {
            }
        }
        throw new RuntimeException("No available port found in range " + preferredPort + "-" + (preferredPort + 9));
    }

    public void start() {
        server.start();
        System.out.println("Cache REST server started on http://localhost:" + port);
        System.out.println("Endpoints:");
        System.out.println("  GET    /cache/{key}         - fetch a value");
        System.out.println("  PUT    /cache/{key}         - store a value  (body: {\"value\": \"...\"})");
        System.out.println("  DELETE /cache/{key}         - remove a key");
        System.out.println("  DELETE /cache               - clear entire cache");
        System.out.println("  GET    /cache?action=stats  - cache statistics");
    }

    public void stop() {
        server.stop(0);
        System.out.println("Cache REST server stopped.");
    }
}
