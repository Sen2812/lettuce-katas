package com.example.valkey.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** valkey 配置根对象，前缀为 {@code valkey}。 */
@ConfigurationProperties(prefix = "valkey")
public class ValkeyProperties {

  /** 多数据源配置：key 为数据源名称（resource 名称），value 为该数据源的配置项。 */
  private final Map<String, ValkeyDataSourceProperties> datasources = new LinkedHashMap<>();

  /** 命令重试配置。 */
  private final Retry retry = new Retry();

  public Map<String, ValkeyDataSourceProperties> getDatasources() {
    return datasources;
  }

  public Retry getRetry() {
    return retry;
  }

  /** 命令重试参数。 */
  public static class Retry {

    /** 包含首次在内的最大尝试次数。 */
    private int maxAttempts = 3;

    /** 两次尝试之间的固定退避时间。 */
    private Duration backoff = Duration.ofMillis(200);

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getBackoff() {
      return backoff;
    }

    public void setBackoff(Duration backoff) {
      this.backoff = backoff;
    }
  }
}
