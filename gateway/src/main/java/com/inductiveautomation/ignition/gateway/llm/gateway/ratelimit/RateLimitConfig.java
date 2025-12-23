package com.inductiveautomation.ignition.gateway.llm.gateway.ratelimit;

/**
 * Configuration for rate limiting.
 */
public final class RateLimitConfig {

    private final int requestsPerMinute;
    private final int tokensPerMinute;
    private final int burstAllowance;

    private RateLimitConfig(Builder builder) {
        this.requestsPerMinute = builder.requestsPerMinute;
        this.tokensPerMinute = builder.tokensPerMinute;
        this.burstAllowance = builder.burstAllowance;
    }

    /**
     * Maximum requests per minute.
     */
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    /**
     * Maximum tokens per minute (for LLM API usage).
     */
    public int getTokensPerMinute() {
        return tokensPerMinute;
    }

    /**
     * Additional burst allowance above normal limits.
     */
    public int getBurstAllowance() {
        return burstAllowance;
    }

    /**
     * Creates a default configuration with conservative limits.
     */
    public static RateLimitConfig defaultConfig() {
        return defaults();
    }

    /**
     * Creates a default configuration with conservative limits.
     */
    public static RateLimitConfig defaults() {
        return builder()
                .requestsPerMinute(60)
                .tokensPerMinute(100000)
                .burstAllowance(10)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int requestsPerMinute = 60;
        private int tokensPerMinute = 100000;
        private int burstAllowance = 10;

        public Builder requestsPerMinute(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
            return this;
        }

        public Builder tokensPerMinute(int tokensPerMinute) {
            this.tokensPerMinute = tokensPerMinute;
            return this;
        }

        public Builder burstAllowance(int burstAllowance) {
            this.burstAllowance = burstAllowance;
            return this;
        }

        public RateLimitConfig build() {
            return new RateLimitConfig(this);
        }
    }

    @Override
    public String toString() {
        return "RateLimitConfig{" +
                "requestsPerMinute=" + requestsPerMinute +
                ", tokensPerMinute=" + tokensPerMinute +
                ", burstAllowance=" + burstAllowance +
                '}';
    }
}
