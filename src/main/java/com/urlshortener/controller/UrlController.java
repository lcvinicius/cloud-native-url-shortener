package com.urlshortener.controller;

import com.urlshortener.dto.ShortenUrlRequest;
import com.urlshortener.dto.ShortenUrlResponse;
import com.urlshortener.service.UrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/v1/urls")
    public ResponseEntity<ShortenUrlResponse> shortenUrl(@RequestBody ShortenUrlRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ShortenUrlResponse response = urlService.shortenUrl(request.url().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        return urlService.getOriginalUrl(shortCode)
                .map(originalUrl -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setLocation(URI.create(originalUrl));
                    return new ResponseEntity<Void>(headers, HttpStatus.FOUND); // 302 Redirect
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<ShortenUrlResponse> getUrlDetails(@PathVariable String shortCode) {
        return urlService.getOriginalUrl(shortCode)
                .map(originalUrl -> ResponseEntity.ok(new ShortenUrlResponse(shortCode, null, originalUrl)))
                .orElse(ResponseEntity.notFound().build());
    }
}

