package com.example.valkey.support;

import java.time.Duration;
import java.util.Objects;

/** valkey 命令重试策略参数：包含首次在内的最大尝试次数与两次尝试之间的固定退避时间。 */
public final class ValkeyRetryOptions {

  /** 默认策略：最多尝试 3 次，每次间隔 200 毫秒。 */
  public static final ValkeyRetryOptions DEFAULT =
      new ValkeyRetryOptions(3, Duration.ofMillis(200));

  private final int maxAttempts;
  private final Duration backoff;

  public ValkeyRetryOptions(int maxAttempts, Duration backoff) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1, but was " + maxAttempts);
    }
    this.maxAttempts = maxAttempts;
    this.backoff = Objects.requireNonNull(backoff, "backoff must not be null");
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public Duration getBackoff() {
    return backoff;
  }
}
