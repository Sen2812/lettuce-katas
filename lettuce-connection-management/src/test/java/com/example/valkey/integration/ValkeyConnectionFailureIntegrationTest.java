package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyConnectionFactory;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.factory.DefaultValkeyClusterConnectionFactory;
import com.example.valkey.factory.DefaultValkeyConnectionFactory;
import com.example.valkey.serializer.JacksonValkeySerializer;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import com.example.valkey.support.ValkeyRetryOptions;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 连接失败场景集成测试：目标端口未监听时，连接创建应经过有限重试后转换为 {@link ValkeyAccessException}，并保留原始连接异常。
 */
class ValkeyConnectionFailureIntegrationTest {

  private static final String HOST = "127.0.0.1";
  private static final int UNUSED_PORT = 6399;
  private static final ValkeyRetryOptions RETRY_OPTIONS =
      new ValkeyRetryOptions(3, Duration.ofMillis(100));

  private static ClientResources clientResources;
  private static ValkeyConnectionFactory connectionFactory;

  @BeforeAll
  static void setUp() {
    Assumptions.assumeFalse(IntegrationTestSupport.reachable(HOST, UNUSED_PORT),
        () -> "port " + UNUSED_PORT + " is unexpectedly reachable, test skipped");

    clientResources = DefaultClientResources.create();
    ValkeySerializer serializer = JacksonValkeySerializer.create();
    ValkeyCommandExecutor executor = new ValkeyCommandExecutor(RETRY_OPTIONS);
    connectionFactory = new DefaultValkeyConnectionFactory(clientResources, serializer, executor);
  }

  @AfterAll
  static void tearDown() {
    if (clientResources != null) {
      clientResources.shutdown();
    }
  }

  private static ValkeyDataSourceProperties unreachableProperties() {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setHost(HOST);
    properties.setPort(UNUSED_PORT);
    properties.setTimeout(Duration.ofSeconds(2));
    return properties;
  }

  @Test
  void shouldTranslateConnectionCreationFailureAndKeepOriginalCause() {
    assertThatThrownBy(
        () -> connectionFactory.createConnection("broken-ds", unreachableProperties()))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("broken-ds")
        .hasCauseInstanceOf(RedisConnectionException.class);
  }

  @Test
  void shouldPerformLimitedRetriesBeforeGivingUp() {
    // 3 次尝试之间间隔 2 次固定退避，耗时下限约为 2 x 100ms
    Instant start = Instant.now();

    assertThatThrownBy(
        () -> connectionFactory.createConnection("broken-ds", unreachableProperties()))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("after 3 attempts");

    assertThat(Duration.between(start, Instant.now()))
        .isGreaterThanOrEqualTo(Duration.ofMillis(200));
  }

  @Test
  void clusterFactoryShouldAlsoTranslateConnectionFailure() {
    ValkeyCommandExecutor executor = new ValkeyCommandExecutor(RETRY_OPTIONS);
    DefaultValkeyClusterConnectionFactory clusterFactory =
        new DefaultValkeyClusterConnectionFactory(clientResources, JacksonValkeySerializer.create(),
            executor);

    ValkeyDataSourceProperties properties = unreachableProperties();
    properties.setMode(com.example.valkey.config.ValkeyMode.CLUSTER);

    assertThatThrownBy(() -> clusterFactory.createConnection("broken-cluster", properties))
        .isInstanceOf(ValkeyAccessException.class)
        .hasCauseInstanceOf(RedisConnectionException.class);
  }
}
