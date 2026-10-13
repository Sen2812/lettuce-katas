package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.config.ValkeyMode;
import com.example.valkey.factory.DefaultValkeyClusterConnectionFactory;
import com.example.valkey.factory.DefaultValkeyConnectionFactory;
import com.example.valkey.manager.DefaultValkeyConnectionManager;
import com.example.valkey.serializer.JacksonValkeySerializer;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import com.example.valkey.support.ValkeyRetryOptions;
import com.example.valkey.template.ReactiveValkeyStringTemplate;
import com.example.valkey.template.ValkeyStringTemplate;
import com.example.valkey.template.ValkeyTemplateFactory;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 单机模式集成测试：连接 WSL 中的 valkey（127.0.0.1:6379）。
 *
 * <p>服务不可达时自动跳过，保证在无服务环境下构建可继续。
 */
class ValkeyStandaloneIntegrationTest {

  private static final String DATA_SOURCE = "standalone-it";
  private static final String HOST = "127.0.0.1";
  private static final int PORT = 6379;
  private static final String PASSWORD = "change_me";

  private static ClientResources clientResources;
  private static ValkeyConnectionManager connectionManager;
  private static ValkeyStringTemplate template;
  private static ReactiveValkeyStringTemplate reactiveTemplate;

  @BeforeAll
  static void setUp() {
    Assumptions.assumeTrue(
        IntegrationTestSupport.reachable(HOST, PORT),
        () -> "valkey standalone @ " + HOST + ":" + PORT + " is not reachable, test skipped");

    clientResources = DefaultClientResources.create();
    ValkeySerializer serializer = JacksonValkeySerializer.create();
    ValkeyCommandExecutor executor = new ValkeyCommandExecutor(ValkeyRetryOptions.DEFAULT);
    DefaultValkeyConnectionFactory connectionFactory =
        new DefaultValkeyConnectionFactory(clientResources, serializer, executor);
    DefaultValkeyClusterConnectionFactory clusterConnectionFactory =
        new DefaultValkeyClusterConnectionFactory(clientResources, serializer, executor);

    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setMode(ValkeyMode.STANDALONE);
    properties.setHost(HOST);
    properties.setPort(PORT);
    properties.setPassword(PASSWORD);

    connectionManager =
        new DefaultValkeyConnectionManager(
            Map.of(DATA_SOURCE, properties), connectionFactory, clusterConnectionFactory);
    ValkeyTemplateFactory templateFactory = new ValkeyTemplateFactory(connectionManager);
    template = templateFactory.getTemplate(DATA_SOURCE);
    reactiveTemplate = templateFactory.getReactiveTemplate(DATA_SOURCE);
  }

  @AfterAll
  static void tearDown() {
    if (connectionManager != null) {
      connectionManager.close();
    }
    if (clientResources != null) {
      clientResources.shutdown();
    }
  }

  private static String uniqueKey(String prefix) {
    return "it:standalone:" + prefix + ":" + UUID.randomUUID();
  }

  @Test
  void shouldSetGetAndDeleteValue() {
    String key = uniqueKey("basic");

    assertThat(template.opsForValue().get(key)).isNull();

    template.opsForValue().set(key, "throwable");
    assertThat(template.opsForValue().get(key)).isEqualTo("throwable");

    assertThat(template.opsForKey().delete(key)).isTrue();
    assertThat(template.opsForValue().get(key)).isNull();
    assertThat(template.opsForKey().delete(key)).isFalse();
  }

  @Test
  void shouldRoundTripChineseEmojiAndControlCharacters() {
    String key = uniqueKey("utf8");
    String value = "中文值 🚀 \n\t\"quoted\"";

    template.opsForValue().set(key, value);

    assertThat(template.opsForValue().get(key)).isEqualTo(value);
    template.opsForKey().delete(key);
  }

  @Test
  void shouldReuseTheSameConnectionInstance() {
    assertThat(connectionManager.getConnection(DATA_SOURCE))
        .isSameAs(connectionManager.getConnection(DATA_SOURCE));
  }

  @Test
  void reactiveOperationsShouldBehaveTheSameWay() {
    String key = uniqueKey("reactive");
    Duration timeout = Duration.ofSeconds(5);

    reactiveTemplate.opsForValue().set(key, "reactive-value").block(timeout);
    assertThat(reactiveTemplate.opsForValue().get(key).block(timeout)).isEqualTo("reactive-value");

    assertThat(reactiveTemplate.opsForKey().delete(key).block(timeout)).isTrue();
    assertThat(reactiveTemplate.opsForValue().get(key).block(timeout)).isNull();
  }
}
