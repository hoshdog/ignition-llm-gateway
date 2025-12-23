package com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiter.
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;
    private RateLimitConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = RateLimitConfig.builder()
                .requestsPerMinute(10)
                .tokensPerMinute(1000)
                .burstAllowance(2)
                .build();
        rateLimiter = new RateLimiter(testConfig);
    }

    @AfterEach
    void tearDown() {
        if (rateLimiter != null) {
            rateLimiter.shutdown();
        }
    }

    // ========== Basic Rate Limiting Tests ==========

    @Test
    void testCheckLimit_allowedRequest() {
        RateLimitResult result = rateLimiter.checkLimit("api-key-1", 100);
        assertTrue(result.isAllowed());
    }

    @Test
    void testCheckLimit_remainingRequestsDecrease() {
        RateLimitResult first = rateLimiter.checkLimit("api-key-1", 100);
        assertTrue(first.isAllowed());
        int firstRemaining = first.getRemainingRequests();

        RateLimitResult second = rateLimiter.checkLimit("api-key-1", 100);
        assertTrue(second.isAllowed());
        assertEquals(firstRemaining - 1, second.getRemainingRequests());
    }

    @Test
    void testCheckLimit_remainingTokensDecrease() {
        RateLimitResult first = rateLimiter.checkLimit("api-key-1", 100);
        assertTrue(first.isAllowed());
        int firstTokens = first.getRemainingTokens();

        RateLimitResult second = rateLimiter.checkLimit("api-key-1", 200);
        assertTrue(second.isAllowed());
        assertEquals(firstTokens - 200, second.getRemainingTokens());
    }

    @Test
    void testCheckLimit_separateBucketsPerApiKey() {
        RateLimitResult result1 = rateLimiter.checkLimit("api-key-1", 500);
        RateLimitResult result2 = rateLimiter.checkLimit("api-key-2", 500);

        assertTrue(result1.isAllowed());
        assertTrue(result2.isAllowed());

        // Both should have similar remaining tokens (both fresh buckets)
        assertEquals(result1.getRemainingTokens(), result2.getRemainingTokens());
    }

    // ========== Request Limit Tests ==========

    @Test
    void testCheckLimit_requestLimitExceeded() {
        // Config: 10 requests + 2 burst = 12 total
        for (int i = 0; i < 12; i++) {
            RateLimitResult result = rateLimiter.checkLimit("api-key-1", 10);
            assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
        }

        // 13th request should be denied
        RateLimitResult denied = rateLimiter.checkLimit("api-key-1", 10);
        assertFalse(denied.isAllowed());
        assertEquals(0, denied.getRemainingRequests());
        assertNotNull(denied.getMessage());
        assertTrue(denied.getMessage().contains("Request rate limit"));
    }

    @Test
    void testCheckLimit_requestLimitReturnsResetTime() {
        // Exhaust requests
        for (int i = 0; i < 12; i++) {
            rateLimiter.checkLimit("api-key-1", 10);
        }

        RateLimitResult denied = rateLimiter.checkLimit("api-key-1", 10);
        assertFalse(denied.isAllowed());
        assertNotNull(denied.getResetTime());
        assertTrue(denied.getSecondsUntilReset() > 0);
        assertTrue(denied.getSecondsUntilReset() <= 60);
    }

    // ========== Token Limit Tests ==========

    @Test
    void testCheckLimit_tokenLimitExceeded() {
        // Config: 1000 tokens per minute
        // Request more tokens than available
        RateLimitResult denied = rateLimiter.checkLimit("api-key-1", 2000);
        assertFalse(denied.isAllowed());
        assertTrue(denied.getMessage().contains("Token rate limit"));
    }

    @Test
    void testCheckLimit_tokenLimitGradualExhaustion() {
        // Use up tokens gradually
        for (int i = 0; i < 9; i++) {
            RateLimitResult result = rateLimiter.checkLimit("api-key-1", 100);
            assertTrue(result.isAllowed());
        }

        // 900 tokens used, 100 remaining
        RateLimitResult remaining = rateLimiter.getStatus("api-key-1");
        assertEquals(100, remaining.getRemainingTokens());

        // Request 200 more tokens - should fail
        RateLimitResult denied = rateLimiter.checkLimit("api-key-1", 200);
        assertFalse(denied.isAllowed());
    }

    // ========== Record Usage Tests ==========

    @Test
    void testRecordUsage_adjustsTokens() {
        // Request with estimated 500 tokens
        RateLimitResult before = rateLimiter.checkLimit("api-key-1", 500);
        assertTrue(before.isAllowed());
        int tokensAfterEstimate = before.getRemainingTokens();

        // Actual usage was only 300 tokens
        rateLimiter.recordUsage("api-key-1", 300);

        // Should have 200 tokens back
        RateLimitResult after = rateLimiter.getStatus("api-key-1");
        assertEquals(tokensAfterEstimate + 200, after.getRemainingTokens());
    }

    @Test
    void testRecordUsage_noAdjustmentIfActualHigher() {
        // Request with estimated 500 tokens
        RateLimitResult before = rateLimiter.checkLimit("api-key-1", 500);
        int tokensAfterEstimate = before.getRemainingTokens();

        // Actual usage was higher - no refund
        rateLimiter.recordUsage("api-key-1", 600);

        // Tokens should not increase (difference is negative)
        RateLimitResult after = rateLimiter.getStatus("api-key-1");
        assertEquals(tokensAfterEstimate, after.getRemainingTokens());
    }

    @Test
    void testRecordUsage_nonexistentKey() {
        // Should not throw
        rateLimiter.recordUsage("nonexistent-key", 100);
    }

    // ========== Get Status Tests ==========

    @Test
    void testGetStatus_newApiKey() {
        RateLimitResult status = rateLimiter.getStatus("new-api-key");
        assertTrue(status.isAllowed());
        assertEquals(testConfig.getRequestsPerMinute(), status.getRemainingRequests());
        assertEquals(testConfig.getTokensPerMinute(), status.getRemainingTokens());
    }

    @Test
    void testGetStatus_existingApiKey() {
        // Make some requests first
        rateLimiter.checkLimit("api-key-1", 100);
        rateLimiter.checkLimit("api-key-1", 200);

        RateLimitResult status = rateLimiter.getStatus("api-key-1");
        assertTrue(status.isAllowed());
        // Should reflect usage
        assertTrue(status.getRemainingRequests() < testConfig.getRequestsPerMinute() + testConfig.getBurstAllowance());
        assertTrue(status.getRemainingTokens() < testConfig.getTokensPerMinute());
    }

    // ========== Configuration Tests ==========

    @Test
    void testRateLimitConfig_defaults() {
        RateLimitConfig defaults = RateLimitConfig.defaults();
        assertEquals(60, defaults.getRequestsPerMinute());
        assertEquals(100000, defaults.getTokensPerMinute());
        assertEquals(10, defaults.getBurstAllowance());
    }

    @Test
    void testRateLimitConfig_builder() {
        RateLimitConfig config = RateLimitConfig.builder()
                .requestsPerMinute(20)
                .tokensPerMinute(50000)
                .burstAllowance(5)
                .build();

        assertEquals(20, config.getRequestsPerMinute());
        assertEquals(50000, config.getTokensPerMinute());
        assertEquals(5, config.getBurstAllowance());
    }

    @Test
    void testRateLimitConfig_toString() {
        String str = testConfig.toString();
        assertTrue(str.contains("10"));
        assertTrue(str.contains("1000"));
        assertTrue(str.contains("2"));
    }

    // ========== Rate Limit Result Tests ==========

    @Test
    void testRateLimitResult_allowed() {
        RateLimitResult result = RateLimitResult.allowed(5, 500);
        assertTrue(result.isAllowed());
        assertEquals(5, result.getRemainingRequests());
        assertEquals(500, result.getRemainingTokens());
        assertNull(result.getResetTime());
        assertNull(result.getMessage());
    }

    @Test
    void testRateLimitResult_requestLimitExceeded() {
        java.time.Instant resetTime = java.time.Instant.now().plusSeconds(30);
        RateLimitResult result = RateLimitResult.requestLimitExceeded(resetTime);

        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemainingRequests());
        assertNotNull(result.getResetTime());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("Request rate limit"));
    }

    @Test
    void testRateLimitResult_tokenLimitExceeded() {
        java.time.Instant resetTime = java.time.Instant.now().plusSeconds(30);
        RateLimitResult result = RateLimitResult.tokenLimitExceeded(resetTime, 1000, 500);

        assertFalse(result.isAllowed());
        assertEquals(500, result.getRemainingTokens());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("Token rate limit"));
        assertTrue(result.getMessage().contains("1000"));
        assertTrue(result.getMessage().contains("500"));
    }

    @Test
    void testRateLimitResult_secondsUntilReset() {
        java.time.Instant resetTime = java.time.Instant.now().plusSeconds(45);
        RateLimitResult result = RateLimitResult.requestLimitExceeded(resetTime);

        long seconds = result.getSecondsUntilReset();
        assertTrue(seconds >= 44 && seconds <= 46);
    }

    @Test
    void testRateLimitResult_secondsUntilReset_noResetTime() {
        RateLimitResult result = RateLimitResult.allowed(5, 500);
        assertEquals(0, result.getSecondsUntilReset());
    }

    @Test
    void testRateLimitResult_toString() {
        RateLimitResult result = RateLimitResult.allowed(5, 500);
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("allowed=true"));
        assertTrue(str.contains("remainingRequests=5"));
        assertTrue(str.contains("remainingTokens=500"));
    }

    // ========== Burst Allowance Tests ==========

    @Test
    void testBurstAllowance_included() {
        // Config: 10 requests + 2 burst = 12 total initially available
        RateLimitResult first = rateLimiter.checkLimit("api-key-1", 10);
        assertTrue(first.isAllowed());
        assertEquals(11, first.getRemainingRequests()); // 12 - 1
    }

    // ========== Shutdown Tests ==========

    @Test
    void testShutdown_graceful() {
        // Should not throw
        rateLimiter.shutdown();

        // Create a new one for other tests
        rateLimiter = new RateLimiter(testConfig);
    }

    // ========== Edge Cases ==========

    @Test
    void testCheckLimit_zeroTokens() {
        RateLimitResult result = rateLimiter.checkLimit("api-key-1", 0);
        assertTrue(result.isAllowed());
        // Tokens should not decrease
        assertEquals(testConfig.getTokensPerMinute(), result.getRemainingTokens());
    }

    @Test
    void testCheckLimit_exactTokenLimit() {
        // Request exactly the limit
        RateLimitResult result = rateLimiter.checkLimit("api-key-1", 1000);
        assertTrue(result.isAllowed());
        assertEquals(0, result.getRemainingTokens());

        // Next request should fail
        RateLimitResult denied = rateLimiter.checkLimit("api-key-1", 1);
        assertFalse(denied.isAllowed());
    }

    @Test
    void testCheckLimit_multipleApiKeys() {
        String[] keys = {"key-1", "key-2", "key-3"};

        for (String key : keys) {
            RateLimitResult result = rateLimiter.checkLimit(key, 100);
            assertTrue(result.isAllowed());
        }

        // All should still have plenty of capacity
        for (String key : keys) {
            RateLimitResult status = rateLimiter.getStatus(key);
            assertEquals(testConfig.getTokensPerMinute() - 100, status.getRemainingTokens());
        }
    }

    @Test
    void testCheckLimit_concurrentAccess() throws InterruptedException {
        Thread[] threads = new Thread[5];
        boolean[] results = new boolean[5];

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                RateLimitResult result = rateLimiter.checkLimit("concurrent-key", 100);
                results[idx] = result.isAllowed();
            });
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        // All should have succeeded
        for (boolean result : results) {
            assertTrue(result);
        }

        // Total usage should be 500 tokens
        RateLimitResult status = rateLimiter.getStatus("concurrent-key");
        assertEquals(500, status.getRemainingTokens());
    }
}
