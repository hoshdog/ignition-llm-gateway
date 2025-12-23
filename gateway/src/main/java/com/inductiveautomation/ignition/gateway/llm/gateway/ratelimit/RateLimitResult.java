package com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit;

import java.time.Instant;

/**
 * Result of a rate limit check.
 */
public final class RateLimitResult {

    private final boolean allowed;
    private final int remainingRequests;
    private final int remainingTokens;
    private final Instant resetTime;
    private final String message;

    private RateLimitResult(boolean allowed, int remainingRequests, int remainingTokens,
                            Instant resetTime, String message) {
        this.allowed = allowed;
        this.remainingRequests = remainingRequests;
        this.remainingTokens = remainingTokens;
        this.resetTime = resetTime;
        this.message = message;
    }

    /**
     * Returns true if the request is allowed.
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * Returns the number of remaining requests in the current window.
     */
    public int getRemainingRequests() {
        return remainingRequests;
    }

    /**
     * Returns the number of remaining tokens in the current window.
     */
    public int getRemainingTokens() {
        return remainingTokens;
    }

    /**
     * Returns the time when the rate limit will reset.
     */
    public Instant getResetTime() {
        return resetTime;
    }

    /**
     * Returns a message explaining the rate limit status.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the number of seconds until the rate limit resets.
     */
    public long getSecondsUntilReset() {
        if (resetTime == null) {
            return 0;
        }
        long seconds = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds);
    }

    /**
     * Creates an allowed result.
     */
    public static RateLimitResult allowed(int remainingRequests, int remainingTokens) {
        return new RateLimitResult(true, remainingRequests, remainingTokens, null, null);
    }

    /**
     * Creates a denied result due to request limit.
     */
    public static RateLimitResult requestLimitExceeded(Instant resetTime) {
        long seconds = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        String message = "Request rate limit exceeded. Try again in " + seconds + " seconds.";
        return new RateLimitResult(false, 0, 0, resetTime, message);
    }

    /**
     * Creates a denied result due to token limit.
     */
    public static RateLimitResult tokenLimitExceeded(Instant resetTime, int tokensRequested,
                                                      int tokensRemaining) {
        long seconds = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
        String message = "Token rate limit exceeded. Requested " + tokensRequested +
                " tokens but only " + tokensRemaining + " remaining. Try again in " +
                seconds + " seconds.";
        return new RateLimitResult(false, 0, tokensRemaining, resetTime, message);
    }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", remainingRequests=" + remainingRequests +
                ", remainingTokens=" + remainingTokens +
                ", resetTime=" + resetTime +
                ", message='" + message + '\'' +
                '}';
    }
}
