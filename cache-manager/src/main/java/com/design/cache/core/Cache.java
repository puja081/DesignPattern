package com.design.cache.core;

import java.util.Optional;

/**
 * Core cache contract. Implementations must be thread-safe.
 */
public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    boolean remove(K key);

    void clear();

    int size();

    int capacity();
}
