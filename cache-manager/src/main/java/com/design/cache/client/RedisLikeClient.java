package com.design.cache.client;

import java.util.Optional;

/**
 * Abstraction that mirrors the Redis GET / SET / DEL interface.
 *
 * In an interview this demonstrates:
 *  1. How to expose a familiar Redis-style API over any backing store.
 *  2. How switching the implementation (in-memory vs. real Redis)
 *     becomes transparent to callers — classic Strategy / Adapter pattern.
 */
public interface RedisLikeClient {

    Optional<String> get(String key);

    void put(String key, String value);

    boolean del(String key);

    void flushAll();

    int dbSize();
}
