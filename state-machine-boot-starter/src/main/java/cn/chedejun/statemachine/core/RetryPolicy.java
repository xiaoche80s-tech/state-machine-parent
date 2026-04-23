package cn.chedejun.statemachine.core;

import java.util.concurrent.TimeUnit;

public class RetryPolicy {
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double backoffFactor;

    private RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs, double backoffFactor) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.backoffFactor = backoffFactor;
    }

    public static Builder exponentialBackoff() { return new Builder(); }
    public static RetryPolicy none() { return new Builder().maxAttempts(0).build(); }

    public int getMaxAttempts() { return maxAttempts; }
    public long getInitialDelayMs() { return initialDelayMs; }
    public long getMaxDelayMs() { return maxDelayMs; }
    public double getBackoffFactor() { return backoffFactor; }

    public long getDelayForAttempt(int attempt) {
        if (attempt <= 0) return 0;
        long delay = (long) (initialDelayMs * Math.pow(backoffFactor, attempt - 1));
        return Math.min(delay, maxDelayMs);
    }

    public static class Builder {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private long maxDelayMs = 30000;
        private double backoffFactor = 2.0;

        public Builder maxAttempts(int v) { this.maxAttempts = v; return this; }
        public Builder initialDelay(long d, TimeUnit u) { this.initialDelayMs = u.toMillis(d); return this; }
        public Builder maxDelay(long d, TimeUnit u) { this.maxDelayMs = u.toMillis(d); return this; }
        public Builder backoffFactor(double f) { this.backoffFactor = f; return this; }
        public RetryPolicy build() { return new RetryPolicy(maxAttempts, initialDelayMs, maxDelayMs, backoffFactor); }
    }
}
