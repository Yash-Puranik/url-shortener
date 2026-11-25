package com.shortlink;

import com.shortlink.service.LruCacheService;
import com.shortlink.service.RateLimiterService;
import com.shortlink.util.Base62Encoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemDesignUnitTests {

    @Test
    void base62EncoderProducesDeterministicShortCodes() {
        String code1 = Base62Encoder.encode(1L);
        String code2 = Base62Encoder.encode(2L);
        assertNotNull(code1);
        assertNotEquals(code1, code2);
        assertEquals(code1, Base62Encoder.encode(1L));
    }

    @Test
    void lruCacheEvictsLeastRecentlyUsedEntry() {
        LruCacheService cache = new LruCacheService(2);
        cache.put("a", "https://a.com");
        cache.put("b", "https://b.com");
        // Access "a" so "b" becomes least recently used
        assertTrue(cache.get("a").isPresent());
        cache.put("c", "https://c.com");

        assertTrue(cache.get("a").isPresent());
        assertTrue(cache.get("c").isPresent());
        assertTrue(cache.get("b").isEmpty());
    }

    @Test
    void tokenBucketRateLimiterBlocksWhenExhausted() {
        RateLimiterService limiter = new RateLimiterService(2, 0);
        assertTrue(limiter.allowRequest("127.0.0.1"));
        assertTrue(limiter.allowRequest("127.0.0.1"));
        assertFalse(limiter.allowRequest("127.0.0.1"));
    }
}
