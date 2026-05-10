package com.cache.model;

/**
 * Snapshot of cache-level metrics: size, capacity, hits, misses, evictions.
 * Returned by the /api/cache/stats endpoint.
 */
public class CacheStats {

    private final int currentSize;
    private final int maxCapacity;
    private final long totalHits;
    private final long totalMisses;
    private final long totalEvictions;

    public CacheStats(int currentSize, int maxCapacity, long totalHits, long totalMisses, long totalEvictions) {
        this.currentSize = currentSize;
        this.maxCapacity = maxCapacity;
        this.totalHits = totalHits;
        this.totalMisses = totalMisses;
        this.totalEvictions = totalEvictions;
    }

    public int getCurrentSize() { return currentSize; }
    public int getMaxCapacity() { return maxCapacity; }
    public long getTotalHits() { return totalHits; }
    public long getTotalMisses() { return totalMisses; }
    public long getTotalEvictions() { return totalEvictions; }

    public double getHitRatio() {
        long total = totalHits + totalMisses;
        return total == 0 ? 0.0 : (double) totalHits / total;
    }
}
