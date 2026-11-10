package com.urlshortener.dto;

public record ShortenUrlResponse(
    String shortCode,
    String shortUrl,
    String originalUrl
) {
}

