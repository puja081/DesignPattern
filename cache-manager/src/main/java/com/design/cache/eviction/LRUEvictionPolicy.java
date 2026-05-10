package com.design.cache.eviction;

import com.design.cache.core.DoublyLinkedList;

import java.util.HashMap;
import java.util.Map;

/**
 * Least Recently Used eviction policy.
 * Maintains a doubly-linked list ordered by access time.
 * Head = least recently used (eviction candidate), Tail = most recently used.
 * All operations are O(1).
 */
public class LRUEvictionPolicy<K> implements EvictionPolicy<K> {

    private final DoublyLinkedList<K> accessOrder = new DoublyLinkedList<>();
    private final Map<K, DoublyLinkedList.Node<K>> nodeMap = new HashMap<>();

    @Override
    public void onAccess(K key) {
        DoublyLinkedList.Node<K> node = nodeMap.get(key);
        if (node != null) {
            accessOrder.moveToTail(node);
        }
    }

    @Override
    public void onInsert(K key) {
        DoublyLinkedList.Node<K> node = accessOrder.addToTail(key);
        nodeMap.put(key, node);
    }

    @Override
    public void onRemove(K key) {
        DoublyLinkedList.Node<K> node = nodeMap.remove(key);
        if (node != null) {
            accessOrder.remove(node);
        }
    }

    @Override
    public K evict() {
        K key = accessOrder.removeHead();
        if (key != null) {
            nodeMap.remove(key);
        }
        return key;
    }
}
