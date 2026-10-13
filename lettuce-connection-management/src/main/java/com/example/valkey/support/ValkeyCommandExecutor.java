package com.example.valkey.support;

import com.example.valkey.ValkeyAccessException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * 统一执行 valkey 命令的入口：负责有限次重试与异常转换。
 *
 * <p>只有“命令未发送到服务端”的连接类异常（{@link RedisConnectionException}）才会重试，尝试次数由
 * {@link ValkeyRetryOptions} 限定；命令已送达服务端后出现的异常（服务端错误、命令超时、集群重定向
 * 超过上限等）不重试。所有最终失败的异常都会转换并包装为 {@link ValkeyAccessException}，原始异常
 * 通过 cause 保留。
 */
public class ValkeyCommandExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValkeyCommandExecutor.class);

  private static final String TOO_MANY_REDIRECTIONS = "Too many cluster redirections";

  private final ValkeyRetryOptions retryOptions;

  public ValkeyCommandExecutor(ValkeyRetryOptions retryOptions) {
    this.retryOptions = retryOptions;
  }

  /** 执行无返回值的命令动作。 */
  public void executeVoid(String dataSourceName, Runnable action) {
    execute(
        dataSourceName,
        () -> {
          action.run();
          return null;
        });
  }

  /**
   * 执行命令动作并返回结果。
   *
   * @throws ValkeyAccessException 重试耗尽或出现不可重试异常时抛出
   */
  public <T> T execute(String dataSourceName, Supplier<T> action) {
    RedisConnectionException lastConnectionError = null;
    for (int attempt = 1; attempt <= retryOptions.getMaxAttempts(); attempt++) {
      try {
        return action.get();
      } catch (RedisConnectionException e) {
        // 连接类异常：命令未送达服务端，可安全重试（本框架封装的命令均为幂等语义）
        lastConnectionError = e;
        LOGGER.warn(
            "Command on data source [{}] failed before reaching the server (attempt {}/{}): {}",
            dataSourceName,
            attempt,
            retryOptions.getMaxAttempts(),
            e.getMessage());
        if (attempt < retryOptions.getMaxAttempts()) {
          sleep();
        }
      } catch (RedisException e) {
        throw translate(dataSourceName, e);
      } catch (ValkeyAccessException e) {
        throw e;
      } catch (RuntimeException e) {
        throw translate(dataSourceName, e);
      }
    }
    throw new ValkeyAccessException(
        "Valkey access failed for data source ["
            + dataSourceName
            + "] after "
            + retryOptions.getMaxAttempts()
            + " attempts",
        lastConnectionError);
  }

  /**
   * 响应式版本的执行入口：使用 Reactor 对连接类异常做同样次数的重试。
   *
   * @throws ValkeyAccessException（以 onError 信号）重试耗尽或出现不可重试异常时抛出
   */
  public <T> Mono<T> executeReactive(String dataSourceName, Supplier<Mono<T>> action) {
    RetryBackoffSpec retrySpec =
        Retry.fixedDelay(retryOptions.getMaxAttempts() - 1L, retryOptions.getBackoff())
            .filter(ValkeyCommandExecutor::isConnectionFailure)
            .doBeforeRetry(
                signal ->
                    LOGGER.warn(
                        "Reactive command on data source [{}] failed before reaching the server"
                            + " (retry {}/{}): {}",
                        dataSourceName,
                        signal.totalRetries() + 1,
                        retryOptions.getMaxAttempts() - 1,
                        signal.failure().getMessage()));
    return Mono.defer(action)
        .retryWhen(retrySpec)
        .onErrorMap(error -> translate(dataSourceName, error));
  }

  private static boolean isConnectionFailure(Throwable error) {
    return error instanceof RedisConnectionException;
  }

  private ValkeyAccessException translate(String dataSourceName, Throwable error) {
    if (error instanceof ValkeyAccessException valkeyAccessException) {
      return valkeyAccessException;
    }
    monitorRedirections(dataSourceName, error);
    return new ValkeyAccessException(
        "Valkey access failed for data source ["
            + dataSourceName
            + "]: "
            + error.getMessage(),
        error);
  }

  /** 集群重定向达到上限时输出监视日志，便于与拓扑刷新日志相互印证。 */
  static void monitorRedirections(String dataSourceName, Throwable error) {
    String message = error.getMessage();
    if (message != null && message.contains(TOO_MANY_REDIRECTIONS)) {
      LOGGER.warn(
          "[ClusterMonitor] Data source [{}] reached the maximum number of cluster redirections,"
              + " a topology refresh will follow: {}",
          dataSourceName,
          message);
    }
  }

  private void sleep() {
    try {
      Thread.sleep(retryOptions.getBackoff().toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ValkeyAccessException("Valkey command retry was interrupted", e);
    }
  }
}
