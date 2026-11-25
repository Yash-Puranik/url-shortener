package com.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * System Design Pattern: Cache-Aside with Thread-Safe LRU Eviction
 * Keeps hot short codes in memory for O(1) redirect lookups.
 */
@Service
public class LruCacheService {

    private final Map<String, String> cache;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);

    public LruCacheService(@Value("${shortlink.cache.capacity:1000}") int capacity) {
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized Optional<String> get(String shortCode) {
        String url = cache.get(shortCode);
        if (url != null) {
            hits.incrementAndGet();
            return Optional.of(url);
        }
        misses.incrementAndGet();
        return Optional.empty();
    }

    public synchronized void put(String shortCode, String originalUrl) {
        cache.put(shortCode, originalUrl);
    }

    public Map<String, Object> getStats() {
        long h = hits.get();
        long m = misses.get();
        double hitRatio = (h + m) == 0 ? 0.0 : (double) h / (h + m);
        return Map.of(
            "hits", h,
            "misses", m,
            "hitRatio", Math.round(hitRatio * 1000.0) / 1000.0,
            "currentSize", cache.size()
        );
    }
}
