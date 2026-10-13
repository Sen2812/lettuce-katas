package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.ValkeyClusterConnection;
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
import com.example.valkey.template.ValkeyStringTemplate;
import com.example.valkey.template.ValkeyTemplateFactory;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 集群模式集成测试：连接 WSL 中的 3 主 3 从 valkey 集群（种子 127.0.0.1:7001）。
 *
 * <p>
 * 集群由 {@code scripts/valkey-cluster.sh up} 创建；不可达时自动跳过。
 */
class ValkeyClusterIntegrationTest {

  private static final String DATA_SOURCE = "cluster-it";
  private static final String HOST = "127.0.0.1";
  private static final int SEED_PORT = 7001;
  private static final String PASSWORD = "change_me";

  private static ClientResources clientResources;
  private static ValkeyConnectionManager connectionManager;
  private static ValkeyStringTemplate template;

  @BeforeAll
  static void setUp() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable(HOST, SEED_PORT),
        () -> "valkey cluster seed @ " + HOST + ":" + SEED_PORT
            + " is not reachable, test skipped");

    clientResources = DefaultClientResources.create();
    ValkeySerializer serializer = JacksonValkeySerializer.create();
    ValkeyCommandExecutor executor = new ValkeyCommandExecutor(ValkeyRetryOptions.DEFAULT);
    DefaultValkeyConnectionFactory connectionFactory =
        new DefaultValkeyConnectionFactory(clientResources, serializer, executor);
    DefaultValkeyClusterConnectionFactory clusterConnectionFactory =
        new DefaultValkeyClusterConnectionFactory(clientResources, serializer, executor);

    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setMode(ValkeyMode.CLUSTER);
    properties.setHost(HOST);
    properties.setPort(SEED_PORT);
    properties.setPassword(PASSWORD);

    connectionManager = new DefaultValkeyConnectionManager(Map.of(DATA_SOURCE, properties),
        connectionFactory, clusterConnectionFactory);
    template = new ValkeyTemplateFactory(connectionManager).getTemplate(DATA_SOURCE);
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
    return "it:cluster:" + prefix + ":" + UUID.randomUUID();
  }

  @Test
  void shouldDiscoverAllClusterNodesFromSingleSeed() {
    ValkeyClusterConnection connection =
        (ValkeyClusterConnection) connectionManager.getConnection(DATA_SOURCE);

    assertThat(connection.getNodeIds()).hasSize(6);
    assertThat(connection.isOpen()).isTrue();
  }

  @Test
  void shouldSetGetAndDeleteValue() {
    String key = uniqueKey("basic");

    template.opsForValue().set(key, "cluster-value");
    assertThat(template.opsForValue().get(key)).isEqualTo("cluster-value");

    assertThat(template.opsForKey().delete(key)).isTrue();
    assertThat(template.opsForValue().get(key)).isNull();
  }

  @Test
  void shouldRouteKeysAcrossSlotsAndNodes() {
    String prefix = "it:cluster:slots:" + UUID.randomUUID();
    int keyCount = 30;

    for (int i = 0; i < keyCount; i++) {
      template.opsForValue().set(prefix + ":" + i, "value-" + i);
    }
    for (int i = 0; i < keyCount; i++) {
      assertThat(template.opsForValue().get(prefix + ":" + i)).isEqualTo("value-" + i);
    }
    for (int i = 0; i < keyCount; i++) {
      assertThat(template.opsForKey().delete(prefix + ":" + i)).isTrue();
    }
  }

  @Test
  void shouldRoundTripChineseTextAcrossCluster() {
    String key = uniqueKey("utf8");
    String value = "集群中文值 🚀";

    template.opsForValue().set(key, value);

    assertThat(template.opsForValue().get(key)).isEqualTo(value);
    template.opsForKey().delete(key);
  }
}
