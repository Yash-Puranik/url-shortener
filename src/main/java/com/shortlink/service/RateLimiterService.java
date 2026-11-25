package com.shortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * System Design Pattern: Token Bucket Rate Limiter
 * Protects write endpoints from bursts and abuse per client IP.
 */
@Service
public class RateLimiterService {

    private final double capacity;
    private final double refillRatePerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${shortlink.rate-limit.capacity:10}") double capacity,
            @Value("${shortlink.rate-limit.refill-tokens-per-second:2}") double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    public boolean allowRequest(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, System.nanoTime()));
        synchronized (bucket) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - bucket.lastRefillTimestamp) / 1_000_000_000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillRatePerSecond);
            bucket.lastRefillTimestamp = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    private static class Bucket {
        double tokens;
        long lastRefillTimestamp;

        Bucket(double tokens, long lastRefillTimestamp) {
            this.tokens = tokens;
            this.lastRefillTimestamp = lastRefillTimestamp;
        }
    }
}
