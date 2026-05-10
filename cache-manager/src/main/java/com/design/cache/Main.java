package com.design.cache;

import com.design.cache.api.CacheServer;
import com.design.cache.client.InMemoryRedisClient;
import com.design.cache.client.RedisLikeClient;

import java.util.Optional;

public class Main {

    public static void main(String[] args) throws Exception {

        System.out.println("=".repeat(60));
        System.out.println("  PART 1 — Redis-like Client (redis.get / redis.put)");
        System.out.println("=".repeat(60));
        demoRedisClient();

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  PART 2 — REST API Server");
        System.out.println("=".repeat(60));
        demoRestServer();
    }

    /**
     * Demonstrates the redis.get(key) / redis.put(key, value) interface
     * backed by an in-memory LRU cache with capacity = 3.
     */
    private static void demoRedisClient() {
        RedisLikeClient redis = new InMemoryRedisClient("demo", 3);

        redis.put("user:1", "Alice");
        redis.put("user:2", "Bob");
        redis.put("user:3", "Charlie");

        System.out.println("After inserting 3 entries (capacity = 3):");
        System.out.println("  redis.get(\"user:1\") = " + redis.get("user:1").orElse("(nil)"));
        System.out.println("  redis.get(\"user:2\") = " + redis.get("user:2").orElse("(nil)"));
        System.out.println("  redis.get(\"user:3\") = " + redis.get("user:3").orElse("(nil)"));
        System.out.println("  redis.dbSize()      = " + redis.dbSize());

        System.out.println();
        System.out.println("Accessing user:1 to make it recently used, then inserting user:4...");
        redis.get("user:1");
        redis.put("user:4", "Diana");

        System.out.println("After LRU eviction (user:2 was least recently used):");
        System.out.println("  redis.get(\"user:1\") = " + redis.get("user:1").orElse("(nil)"));
        System.out.println("  redis.get(\"user:2\") = " + redis.get("user:2").orElse("(nil)") + "  <-- evicted");
        System.out.println("  redis.get(\"user:3\") = " + redis.get("user:3").orElse("(nil)"));
        System.out.println("  redis.get(\"user:4\") = " + redis.get("user:4").orElse("(nil)"));

        System.out.println();
        System.out.println("redis.del(\"user:4\") = " + redis.del("user:4"));
        System.out.println("  redis.get(\"user:4\") = " + redis.get("user:4").orElse("(nil)") + "  <-- deleted");
        System.out.println("  redis.dbSize()      = " + redis.dbSize());
    }

    /**
     * Starts the REST server so you can interact via curl:
     *   curl -X PUT  http://localhost:8080/cache/mykey -d '{"value":"hello"}'
     *   curl         http://localhost:8080/cache/mykey
     *   curl -X DELETE http://localhost:8080/cache/mykey
     *   curl         http://localhost:8080/cache?action=stats
     */
    private static void demoRestServer() throws Exception {
        CacheServer server = new CacheServer(8080);
        server.start();
        System.out.println();
        System.out.println("Try these commands in another terminal:");
        System.out.println("  curl -X PUT  http://localhost:8080/cache/city -d '{\"value\":\"Bangalore\"}'");
        System.out.println("  curl         http://localhost:8080/cache/city");
        System.out.println("  curl -X DELETE http://localhost:8080/cache/city");
        System.out.println("  curl         http://localhost:8080/cache?action=stats");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
