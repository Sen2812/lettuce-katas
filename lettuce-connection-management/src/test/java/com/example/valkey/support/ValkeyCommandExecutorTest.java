package com.example.valkey.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.valkey.ValkeyAccessException;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisConnectionException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class ValkeyCommandExecutorTest {

  private final ValkeyCommandExecutor executor =
      new ValkeyCommandExecutor(new ValkeyRetryOptions(3, Duration.ofMillis(10)));

  @Test
  void shouldReturnResultOnFirstSuccess() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor.execute(
            "ds-1",
            () -> {
              attempts.incrementAndGet();
              return "value";
            });

    assertThat(result).isEqualTo("value");
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void shouldRetryConnectionFailureUntilSuccess() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor.execute(
            "ds-1",
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new RedisConnectionException("connection refused");
              }
              return "ok";
            });

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void shouldExhaustRetriesAndTranslateWithOriginalCause() {
    AtomicInteger attempts = new AtomicInteger();
    RedisConnectionException failure = new RedisConnectionException("connection refused");

    assertThatThrownBy(
            () ->
                executor.execute(
                    "ds-1",
                    () -> {
                      attempts.incrementAndGet();
                      throw failure;
                    }))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("after 3 attempts")
        .hasCause(failure);

    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryServerSideErrors() {
    AtomicInteger attempts = new AtomicInteger();
    RedisCommandExecutionException failure = new RedisCommandExecutionException("WRONGTYPE");

    assertThatThrownBy(
            () ->
                executor.execute(
                    "ds-1",
                    () -> {
                      attempts.incrementAndGet();
                      throw failure;
                    }))
        .isInstanceOf(ValkeyAccessException.class)
        .hasCause(failure);

    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void shouldWrapUnexpectedExceptionsWithoutRetry() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor.execute(
                    "ds-1",
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("connection is closed");
                    }))
        .isInstanceOf(ValkeyAccessException.class)
        .hasCauseInstanceOf(IllegalStateException.class);

    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  void reactiveShouldRetryConnectionFailureUntilSuccess() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        executor
            .executeReactive(
                "ds-1",
                () ->
                    Mono.fromCallable(
                        () -> {
                          if (attempts.incrementAndGet() < 2) {
                            throw new RedisConnectionException("connection refused");
                          }
                          return "ok";
                        }))
            .block();

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void reactiveShouldExhaustRetriesAndTranslate() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor
                    .executeReactive(
                        "ds-1",
                        () ->
                            Mono.fromCallable(
                                () -> {
                                  attempts.incrementAndGet();
                                  throw new RedisConnectionException("connection refused");
                                }))
                    .block())
        .isInstanceOf(ValkeyAccessException.class)
        .hasCauseInstanceOf(RedisConnectionException.class);

    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void reactiveShouldNotRetryServerSideErrors() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                executor
                    .executeReactive(
                        "ds-1",
                        () ->
                            Mono.fromCallable(
                                () -> {
                                  attempts.incrementAndGet();
                                  throw new RedisCommandExecutionException("WRONGTYPE");
                                }))
                    .block())
        .isInstanceOf(ValkeyAccessException.class)
        .hasCauseInstanceOf(RedisCommandExecutionException.class);

    assertThat(attempts.get()).isEqualTo(1);
  }
}
