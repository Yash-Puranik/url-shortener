package com.shortlink.service;

import com.shortlink.model.UrlMapping;
import com.shortlink.repository.UrlRepository;
import com.shortlink.util.Base62Encoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final LruCacheService cacheService;

    public UrlShortenerService(UrlRepository urlRepository, LruCacheService cacheService) {
        this.urlRepository = urlRepository;
        this.cacheService = cacheService;
    }

    @Transactional
    public UrlMapping shorten(String originalUrl) {
        // Idempotency / Deduplication: return existing short code if URL was already shortened
        return urlRepository.findByOriginalUrl(originalUrl).orElseGet(() -> {
            UrlMapping mapping = new UrlMapping();
            mapping.setOriginalUrl(originalUrl);
            UrlMapping saved = urlRepository.save(mapping);

            String shortCode = Base62Encoder.encode(saved.getId());
            saved.setShortCode(shortCode);
            UrlMapping updated = urlRepository.save(saved);

            cacheService.put(shortCode, originalUrl);
            return updated;
        });
    }

    @Transactional
    public String resolve(String shortCode) {
        // Cache-Aside Pattern: check LRU cache first, fall back to DB on miss
        String originalUrl = cacheService.get(shortCode).orElseGet(() -> {
            UrlMapping mapping = urlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));
            cacheService.put(shortCode, mapping.getOriginalUrl());
            return mapping.getOriginalUrl();
        });

        urlRepository.findByShortCode(shortCode).ifPresent(m -> {
            m.setClickCount(m.getClickCount() + 1);
            urlRepository.save(m);
        });

        return originalUrl;
    }

    public UrlMapping getStats(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));
    }
}
