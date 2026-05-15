package com.arquisoft.genai.application.service;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AiGenerationCoordinator {

    private final Clock clock = Clock.systemUTC();

    @Value("${ai.request-cache-ttl-seconds:300}")
    private long requestCacheTtlSeconds;

    @Value("${ai.max-concurrent-requests:1}")
    private int maxConcurrentRequests;

    @Value("${ai.circuit-breaker-failure-threshold:3}")
    private int failureThreshold;

    @Value("${ai.circuit-breaker-open-seconds:60}")
    private long circuitOpenSeconds;

    private final ConcurrentHashMap<String, CacheEntry> requestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ArchitectureOutput>> inflightRequests = new ConcurrentHashMap<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilMillis = new AtomicLong(0L);
    private Semaphore concurrencySemaphore;

    @PostConstruct
    void init() {
        concurrencySemaphore = new Semaphore(Math.max(1, maxConcurrentRequests), true);
    }

    public ArchitectureOutput execute(ArchitectureInput input, Supplier<ArchitectureOutput> action) {
        String requestKey = fingerprint(input);

        ArchitectureOutput cached = getCached(requestKey);
        if (cached != null) {
            log.debug("AI request cache hit for key {}", requestKey);
            return cached;
        }

        CompletableFuture<ArchitectureOutput> newFuture = new CompletableFuture<>();
        CompletableFuture<ArchitectureOutput> existing = inflightRequests.putIfAbsent(requestKey, newFuture);
        if (existing != null) {
            log.debug("Joining in-flight AI request for key {}", requestKey);
            return await(existing);
        }

        boolean acquired = false;
        try {
            ensureCircuitClosed();
            acquirePermit();
            acquired = true;

            ArchitectureOutput output = action.get();
            cacheSuccess(requestKey, output);
            resetCircuit();
            newFuture.complete(output);
            return output;

        } catch (RuntimeException ex) {
            recordFailure(ex);
            newFuture.completeExceptionally(ex);
            throw ex;
        } finally {
            inflightRequests.remove(requestKey, newFuture);
            if (acquired) {
                releasePermit();
            }
        }
    }

    private ArchitectureOutput await(CompletableFuture<ArchitectureOutput> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            Throwable cause = Optional.ofNullable(ex.getCause()).orElse(ex);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AiProviderException(cause.getMessage(), cause);
        }
    }

    private ArchitectureOutput getCached(String requestKey) {
        CacheEntry entry = requestCache.get(requestKey);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock.millis())) {
            requestCache.remove(requestKey, entry);
            return null;
        }
        return entry.output;
    }

    private void cacheSuccess(String requestKey, ArchitectureOutput output) {
        long expiry = clock.millis() + TimeUnit.SECONDS.toMillis(Math.max(1L, requestCacheTtlSeconds));
        requestCache.put(requestKey, new CacheEntry(output, expiry));
    }

    private void ensureCircuitClosed() {
        long openUntil = circuitOpenUntilMillis.get();
        long now = clock.millis();
        if (now < openUntil) {
            long remainingSeconds = Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(openUntil - now));
            throw new AiProviderException(
                    "AI generation is temporarily paused after repeated failures. Retry in about "
                            + remainingSeconds + " seconds.");
        }
    }

    private void recordFailure(RuntimeException ex) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failureThreshold > 0 && failures >= failureThreshold) {
            long openUntil = clock.millis() + TimeUnit.SECONDS.toMillis(Math.max(1L, circuitOpenSeconds));
            circuitOpenUntilMillis.set(openUntil);
            consecutiveFailures.set(0);
            log.warn("Opening AI circuit breaker for {} seconds after repeated failures: {}",
                    circuitOpenSeconds, ex.getMessage());
        }
    }

    private void resetCircuit() {
        consecutiveFailures.set(0);
        circuitOpenUntilMillis.set(0L);
    }

    private void acquirePermit() {
        try {
            concurrencySemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting for AI request slot", e);
        }
    }

    private void releasePermit() {
        concurrencySemaphore.release();
    }

    private String fingerprint(ArchitectureInput input) {
        String canonical = normalize(input.getDomain()) + "|"
                + normalizeList(input.getQualityAttributes()) + "|"
                + normalizeList(input.getTechStackConstraints()) + "|"
                + normalize(input.getNaturalLanguageDescription());
        return sha256(canonical);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AiProviderException("Unable to compute request fingerprint", e);
        }
    }

    private record CacheEntry(ArchitectureOutput output, long expiresAtMillis) {
        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}