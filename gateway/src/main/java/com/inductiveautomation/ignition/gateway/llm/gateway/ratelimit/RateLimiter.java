package com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for LLM Gateway API requests.
 * Uses a token bucket algorithm with separate limits for requests and LLM tokens.
 */
public class RateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitConfig defaultConfig;
    private final ScheduledExecutorService scheduler;

    public RateLimiter(RateLimitConfig defaultConfig) {
        this.defaultConfig = defaultConfig;

        // Cleanup expired buckets periodically
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RateLimiter-Cleanup");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::cleanupExpiredBuckets, 1, 1, TimeUnit.MINUTES);

        logger.info("RateLimiter initialized with config: {}", defaultConfig);
    }

    /**
     * Checks if a request is allowed under rate limits.
     *
     * @param apiKeyId The API key identifier
     * @param estimatedTokens Estimated tokens for the request
     * @return Rate limit result
     */
    public RateLimitResult checkLimit(String apiKeyId, int estimatedTokens) {
        TokenBucket bucket = buckets.computeIfAbsent(apiKeyId,
                k -> new TokenBucket(defaultConfig));

        return bucket.tryConsume(estimatedTokens);
    }

    /**
     * Records actual token usage after LLM response.
     * Adjusts the token count based on actual vs estimated usage.
     *
     * @param apiKeyId The API key identifier
     * @param actualTokens Actual tokens used
     */
    public void recordUsage(String apiKeyId, int actualTokens) {
        TokenBucket bucket = buckets.get(apiKeyId);
        if (bucket != null) {
            bucket.adjustForActualUsage(actualTokens);
        }
    }

    /**
     * Gets the current rate limit status for an API key.
     */
    public RateLimitResult getStatus(String apiKeyId) {
        TokenBucket bucket = buckets.get(apiKeyId);
        if (bucket == null) {
            return RateLimitResult.allowed(
                    defaultConfig.getRequestsPerMinute(),
                    defaultConfig.getTokensPerMinute());
        }
        return bucket.getStatus();
    }

    /**
     * Cleans up expired buckets that haven't been used in a while.
     */
    private void cleanupExpiredBuckets() {
        Instant threshold = Instant.now().minusSeconds(300); // 5 minutes

        buckets.entrySet().removeIf(entry -> {
            TokenBucket bucket = entry.getValue();
            if (bucket.getLastAccessTime().isBefore(threshold)) {
                logger.debug("Removing expired rate limit bucket for key: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Shuts down the rate limiter.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Token bucket implementation for rate limiting.
     */
    private static class TokenBucket {
        private final int maxRequestsPerMinute;
        private final int maxTokensPerMinute;
        private final int burstAllowance;

        private final AtomicInteger requestsRemaining;
        private final AtomicInteger tokensRemaining;
        private volatile Instant windowStart;
        private volatile Instant lastAccessTime;

        // Track estimated vs actual for adjustment
        private int estimatedTokensUsed = 0;

        TokenBucket(RateLimitConfig config) {
            this.maxRequestsPerMinute = config.getRequestsPerMinute();
            this.maxTokensPerMinute = config.getTokensPerMinute();
            this.burstAllowance = config.getBurstAllowance();

            this.requestsRemaining = new AtomicInteger(maxRequestsPerMinute + burstAllowance);
            this.tokensRemaining = new AtomicInteger(maxTokensPerMinute);
            this.windowStart = Instant.now();
            this.lastAccessTime = Instant.now();
        }

        synchronized RateLimitResult tryConsume(int estimatedTokens) {
            lastAccessTime = Instant.now();
            maybeResetWindow();

            // Check request limit
            if (requestsRemaining.get() <= 0) {
                return RateLimitResult.requestLimitExceeded(getResetTime());
            }

            // Check token limit
            if (tokensRemaining.get() < estimatedTokens) {
                return RateLimitResult.tokenLimitExceeded(
                        getResetTime(), estimatedTokens, tokensRemaining.get());
            }

            // Consume
            int remainingReqs = requestsRemaining.decrementAndGet();
            int remainingToks = tokensRemaining.addAndGet(-estimatedTokens);
            estimatedTokensUsed += estimatedTokens;

            return RateLimitResult.allowed(remainingReqs, remainingToks);
        }

        synchronized void adjustForActualUsage(int actualTokens) {
            // If actual usage was less than estimated, add back the difference
            int difference = estimatedTokensUsed - actualTokens;
            if (difference > 0) {
                tokensRemaining.addAndGet(difference);
            }
            estimatedTokensUsed = actualTokens;
        }

        synchronized RateLimitResult getStatus() {
            maybeResetWindow();
            return RateLimitResult.allowed(requestsRemaining.get(), tokensRemaining.get());
        }

        Instant getLastAccessTime() {
            return lastAccessTime;
        }

        private void maybeResetWindow() {
            Instant now = Instant.now();
            if (now.isAfter(windowStart.plusSeconds(60))) {
                resetWindow();
            }
        }

        private void resetWindow() {
            windowStart = Instant.now();
            requestsRemaining.set(maxRequestsPerMinute + burstAllowance);
            tokensRemaining.set(maxTokensPerMinute);
            estimatedTokensUsed = 0;
        }

        private Instant getResetTime() {
            return windowStart.plusSeconds(60);
        }
    }
}
