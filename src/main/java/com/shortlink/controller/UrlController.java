package com.shortlink.controller;

import com.shortlink.dto.ShortenRequest;
import com.shortlink.dto.ShortenResponse;
import com.shortlink.model.UrlMapping;
import com.shortlink.service.LruCacheService;
import com.shortlink.service.RateLimiterService;
import com.shortlink.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;

@RestController
public class UrlController {

    private final UrlShortenerService shortenerService;
    private final RateLimiterService rateLimiterService;
    private final LruCacheService cacheService;
    private final String baseUrl;

    public UrlController(
            UrlShortenerService shortenerService,
            RateLimiterService rateLimiterService,
            LruCacheService cacheService,
            @Value("${shortlink.base-url:http://localhost:8080}") String baseUrl) {
        this.shortenerService = shortenerService;
        this.rateLimiterService = rateLimiterService;
        this.cacheService = cacheService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/v1/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortenResponse shortenUrl(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        if (!rateLimiterService.allowRequest(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again shortly.");
        }

        UrlMapping mapping = shortenerService.shorten(request.originalUrl());
        return new ShortenResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl()
        );
    }

    @GetMapping("/{shortCode:[a-zA-Z0-9]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = shortenerService.resolve(shortCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/v1/urls/{shortCode}/stats")
    public UrlMapping getUrlStats(@PathVariable String shortCode) {
        return shortenerService.getStats(shortCode);
    }

    @GetMapping("/api/v1/system/cache-stats")
    public Map<String, Object> getCacheStats() {
        return cacheService.getStats();
    }
}
