package com.design.cache.core;

/**
 * A doubly-linked list that supports O(1) add-to-tail, remove-by-node,
 * and remove-from-head operations. Used by LRU eviction to maintain
 * access order — head is least-recently-used, tail is most-recently-used.
 */
public class DoublyLinkedList<K> {

    public static class Node<K> {
        K key;
        Node<K> prev;
        Node<K> next;

        public Node(K key) {
            this.key = key;
        }

        public K getKey() {
            return key;
        }
    }

    private final Node<K> head;
    private final Node<K> tail;

    public DoublyLinkedList() {
        head = new Node<>(null);
        tail = new Node<>(null);
        head.next = tail;
        tail.prev = head;
    }

    public Node<K> addToTail(K key) {
        Node<K> node = new Node<>(key);
        linkBefore(node, tail);
        return node;
    }

    public void moveToTail(Node<K> node) {
        unlink(node);
        linkBefore(node, tail);
    }

    public K removeHead() {
        if (isEmpty()) {
            return null;
        }
        Node<K> first = head.next;
        unlink(first);
        return first.key;
    }

    public void remove(Node<K> node) {
        unlink(node);
    }

    public boolean isEmpty() {
        return head.next == tail;
    }

    private void linkBefore(Node<K> node, Node<K> before) {
        node.prev = before.prev;
        node.next = before;
        before.prev.next = node;
        before.prev = node;
    }

    private void unlink(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }
}
