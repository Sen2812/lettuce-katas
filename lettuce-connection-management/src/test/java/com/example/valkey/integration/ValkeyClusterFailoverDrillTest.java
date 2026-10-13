package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.ValkeyAccessException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群节点故障演练（默认禁用，设置环境变量 {@code VALKEY_FAILOVER_DRILL=true} 后启用）。
 *
 * <p>
 * 演练内容：停止当前持有 slot 0 的主节点（通过 Podman），验证 Lettuce 在副本提升后自动完成 拓扑刷新与重连，客户端复用同一连接即可恢复读写，无需应用层重建任何连接。
 *
 * <p>
 * 演练需要 WSL + Podman 环境以及 {@code scripts/valkey-cluster.sh up} 创建的集群。
 */
@EnabledIfEnvironmentVariable(named = "VALKEY_FAILOVER_DRILL", matches = "true")
class ValkeyClusterFailoverDrillTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ValkeyClusterFailoverDrillTest.class);

  private static final String DATA_SOURCE = "cluster-drill";
  private static final String HOST = "127.0.0.1";
  private static final int SEED_PORT = 7001;
  private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(120);

  private static ClientResources clientResources;
  private static ValkeyConnectionManager connectionManager;
  private static ValkeyStringTemplate template;

  @BeforeAll
  static void setUp() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable(HOST, SEED_PORT),
        () -> "valkey cluster seed @ " + HOST + ":" + SEED_PORT
            + " is not reachable, drill skipped");

    clientResources = DefaultClientResources.create();
    ValkeySerializer serializer = JacksonValkeySerializer.create();
    ValkeyCommandExecutor executor =
        new ValkeyCommandExecutor(new ValkeyRetryOptions(2, Duration.ofMillis(50)));
    DefaultValkeyConnectionFactory connectionFactory =
        new DefaultValkeyConnectionFactory(clientResources, serializer, executor);
    DefaultValkeyClusterConnectionFactory clusterConnectionFactory =
        new DefaultValkeyClusterConnectionFactory(clientResources, serializer, executor);

    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setMode(ValkeyMode.CLUSTER);
    properties.setHost(HOST);
    properties.setPort(SEED_PORT);
    properties.setPassword(IntegrationTestSupport.CLUSTER_PASSWORD);

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

  @Test
  void shouldRecoverAutomaticallyAfterMasterFailure() throws Exception {
    String master = findMasterContainerHostingSlot(0);
    LOGGER.info("[drill] master hosting slot 0 is {}", master);

    String prefix = "it:drill:" + UUID.randomUUID() + ":";
    int keyCount = 30;
    for (int i = 0; i < keyCount; i++) {
      template.opsForValue().set(prefix + i, "value-" + i);
    }

    boolean stopped = false;
    try {
      IntegrationTestSupport.podman("stop", master);
      stopped = true;

      // 故障初期：落在该主节点槽位上的 key 必然读取失败，证明中断真实发生
      assertThat(countReadFailures(prefix, keyCount))
          .as("stopping %s must break reads of keys on its slots", master)
          .isGreaterThan(0);

      Instant deadline = Instant.now().plus(RECOVERY_TIMEOUT);
      long startNanos = System.nanoTime();
      int failures;
      do {
        Thread.sleep(1000);
        failures = countReadFailures(prefix, keyCount);
      } while (failures > 0 && Instant.now().isBefore(deadline));

      double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
      assertThat(failures)
          .as("cluster should recover within %s after stopping %s", RECOVERY_TIMEOUT, master)
          .isZero();
      LOGGER.info(
          "[drill] cluster recovered in {} s after stopping {} (topology refreshed by Lettuce)",
          String.format("%.1f", elapsedSeconds), master);

      for (int i = 0; i < keyCount; i++) {
        assertThat(template.opsForValue().get(prefix + i)).isEqualTo("value-" + i);
      }
    } finally {
      if (stopped) {
        IntegrationTestSupport.podman("start", master);
      }
      for (int i = 0; i < keyCount; i++) {
        try {
          template.opsForKey().delete(prefix + i);
        } catch (ValkeyAccessException ignored) {
          // 清理尽力而为
        }
      }
    }
  }

  private static int countReadFailures(String prefix, int keyCount) {
    int failures = 0;
    for (int i = 0; i < keyCount; i++) {
      try {
        template.opsForValue().get(prefix + i);
      } catch (ValkeyAccessException e) {
        failures++;
      }
    }
    return failures;
  }

  /** 解析当前持有指定槽位的主节点容器名。 */
  private static String findMasterContainerHostingSlot(int slot) throws Exception {
    List<IntegrationTestSupport.ClusterNode> nodes = IntegrationTestSupport.clusterNodes(SEED_PORT);
    for (IntegrationTestSupport.ClusterNode node : nodes) {
      if (node.master() && node.covers(slot)) {
        return "valkey-node-" + node.port();
      }
    }
    throw new IllegalStateException("no master found for slot " + slot + " in " + nodes);
  }
}
