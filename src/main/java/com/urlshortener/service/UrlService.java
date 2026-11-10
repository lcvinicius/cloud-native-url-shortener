package com.urlshortener.service;

import com.urlshortener.dto.ShortenUrlResponse;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 7;
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_PREFIX = "url:";

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public UrlService(UrlRepository urlRepository, StringRedisTemplate redisTemplate) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public ShortenUrlResponse shortenUrl(String originalUrl) {
        String shortCode = generateUniqueShortCode();
        UrlMapping mapping = new UrlMapping(shortCode, originalUrl);
        urlRepository.save(mapping);

        // Armazena no cache do Redis para acesso instantâneo
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl, CACHE_TTL);
            log.info("URL encurtada com sucesso e cacheada: {} -> {}", shortCode, originalUrl);
        } catch (Exception e) {
            log.warn("Falha ao salvar no cache Redis, persistido apenas no banco: {}", e.getMessage());
        }

        String shortUrl = baseUrl + "/" + shortCode;
        return new ShortenUrlResponse(shortCode, shortUrl, originalUrl);
    }

    public Optional<String> getOriginalUrl(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;

        // 1. Tenta buscar no Redis (Cache Hit)
        try {
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null) {
                log.debug("Cache HIT no Redis para o código: {}", shortCode);
                return Optional.of(cachedUrl);
            }
        } catch (Exception e) {
            log.warn("Erro ao consultar Redis, fazendo fallback para o banco: {}", e.getMessage());
        }

        // 2. Fallback para PostgreSQL (Cache Miss)
        log.debug("Cache MISS no Redis para o código: {}. Consultando Postgres.", shortCode);
        Optional<UrlMapping> mapping = urlRepository.findByShortCode(shortCode);

        // 3. Atualiza o Redis se encontrou no banco
        mapping.ifPresent(m -> {
            try {
                redisTemplate.opsForValue().set(cacheKey, m.getOriginalUrl(), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Falha ao re-alimentar o Redis: {}", e.getMessage());
            }
        });

        return mapping.map(UrlMapping::getOriginalUrl);
    }

    private String generateUniqueShortCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (urlRepository.existsByShortCode(code));
        return code;
    }
}

