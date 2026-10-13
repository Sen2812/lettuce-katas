package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.lettuce.core.RedisException;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 重定向行为演练（默认禁用，设置环境变量 {@code VALKEY_REDIRECTION_DRILL=true} 后启用）。
 *
 * <p>
 * 演练 1：计划内主从切换（CLUSTER FAILOVER）后，持有旧拓扑的客户端仍能通过 Lettuce 自动跟随 MOVED 重定向完成读写 —— 证明重定向无需应用层实现。
 *
 * <p>
 * 演练 2：max-redirects 为 0 的客户端在同样场景下立即触发重定向超限，框架输出 {@code [ClusterMonitor]} 监视日志并转换
 * {@link ValkeyAccessException} —— 证明超限监视有效。
 *
 * <p>
 * 为避免周期性拓扑刷新干扰观察，演练客户端把 topology-refresh-period 设为 10 分钟，只保留 重定向驱动的自适应刷新路径。
 */
@EnabledIfEnvironmentVariable(named = "VALKEY_REDIRECTION_DRILL", matches = "true")
class ValkeyClusterRedirectionDrillTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ValkeyClusterRedirectionDrillTest.class);

  private static final String HOST = "127.0.0.1";
  private static final int SEED_PORT = 7001;
  private static final Duration ROLE_SWITCH_TIMEOUT = Duration.ofSeconds(30);

  @BeforeAll
  static void checkEnvironment() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable(HOST, SEED_PORT),
        () -> "valkey cluster seed @ " + HOST + ":" + SEED_PORT
            + " is not reachable, drill skipped");
  }

  @Test
  void shouldFollowRedirectionAutomaticallyAfterPlannedFailover() throws Exception {
    String key = IntegrationTestSupport.drillKey("auto");
    MasterReplica pair = findMasterAndReplicaFor(key);
    LOGGER.info("[drill] planned failover of slot owner {} -> replica {}", pair.master(),
        pair.replica());

    try (DrillClient client = new DrillClient(5)) {
      client.template.opsForValue().set(key, "before-failover");
      assertThat(client.template.opsForValue().get(key)).isEqualTo("before-failover");

      IntegrationTestSupport.clusterFailover(pair.replica());
      IntegrationTestSupport.awaitRole(SEED_PORT, pair.replica(), true, ROLE_SWITCH_TIMEOUT);

      // 客户端仍持有切换前的拓扑，命令会先命中旧节点并收到 MOVED；
      // Lettuce 自动重定向到新主节点，因此读写无需任何应用层介入即可成功。
      assertThat(client.template.opsForValue().get(key)).isEqualTo("before-failover");
      client.template.opsForValue().set(key, "after-failover");
      assertThat(client.template.opsForValue().get(key)).isEqualTo("after-failover");
      assertThat(client.template.opsForKey().delete(key)).isTrue();
      LOGGER.info("[drill] redirection was followed automatically, client kept working");
    } finally {
      try (DrillClient cleanup = new DrillClient(5)) {
        cleanup.template.opsForKey().delete(key);
      } catch (RuntimeException ignored) {
        // 清理尽力而为
      }
    }
  }

  @Test
  void shouldMonitorRedirectionLimitWhenExceeded() throws Exception {
    String key = IntegrationTestSupport.drillKey("limit");
    MasterReplica pair = findMasterAndReplicaFor(key);
    LOGGER.info("[drill] moving slot owner away from {} to {}", pair.master(), pair.replica());

    try (DrillClient limited = new DrillClient(0)) {
      // 预热：让客户端基于当前拓扑建立连接
      limited.template.opsForValue().set(key, "value");

      // 槽位所有权切换后，客户端旧拓扑立即失效
      IntegrationTestSupport.clusterFailover(pair.replica());
      IntegrationTestSupport.awaitRole(SEED_PORT, pair.replica(), true, ROLE_SWITCH_TIMEOUT);

      // max-redirects=0：Lettuce 不再跟随 MOVED，直接把重定向响应作为异常抛出；
      // 框架输出 [ClusterMonitor] 监视日志并转换为 ValkeyAccessException（保留原始异常）。
      assertThatThrownBy(() -> limited.template.opsForValue().get(key))
          .isInstanceOf(ValkeyAccessException.class)
          .hasCauseInstanceOf(RedisException.class)
          .satisfies(error -> {
            String message = error.getCause().getMessage();
            assertThat(message.startsWith("MOVED ") || message.startsWith("ASK ")
                || message.contains("Too many cluster redirections"))
                .as("unexpected redirection failure message: %s", message)
                .isTrue();
          });
      LOGGER.info("[drill] redirection limit was reached and translated as expected");
    } finally {
      try (DrillClient cleanup = new DrillClient(5)) {
        cleanup.template.opsForKey().delete(key);
      } catch (RuntimeException ignored) {
        // 清理尽力而为
      }
    }
  }

  private static MasterReplica findMasterAndReplicaFor(String key) throws Exception {
    int slot = IntegrationTestSupport.clusterKeySlot(SEED_PORT, key);
    List<IntegrationTestSupport.ClusterNode> nodes = IntegrationTestSupport.clusterNodes(SEED_PORT);
    IntegrationTestSupport.ClusterNode master = nodes.stream()
        .filter(IntegrationTestSupport.ClusterNode::master)
        .filter(node -> node.covers(slot))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no master covers slot " + slot));
    IntegrationTestSupport.ClusterNode replica = nodes.stream()
        .filter(node -> !node.master() && master.nodeId().equals(node.masterId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no replica for master " + master.port()));
    return new MasterReplica(master.port(), replica.port());
  }

  private record MasterReplica(int master, int replica) {}

  /** 面向演练的独立客户端：与常规测试隔离，可以指定 max-redirects 与较长的周期刷新间隔。 */
  private static final class DrillClient implements AutoCloseable {

    private final ClientResources resources;
    private final ValkeyConnectionManager manager;
    private final ValkeyStringTemplate template;

    DrillClient(int maxRedirects) {
      resources = DefaultClientResources.create();
      ValkeySerializer serializer = JacksonValkeySerializer.create();
      ValkeyCommandExecutor executor =
          new ValkeyCommandExecutor(new ValkeyRetryOptions(2, Duration.ofMillis(50)));
      DefaultValkeyConnectionFactory connectionFactory =
          new DefaultValkeyConnectionFactory(resources, serializer, executor);
      DefaultValkeyClusterConnectionFactory clusterConnectionFactory =
          new DefaultValkeyClusterConnectionFactory(resources, serializer, executor);

      ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
      properties.setMode(ValkeyMode.CLUSTER);
      properties.setHost(HOST);
      properties.setPort(SEED_PORT);
      properties.setPassword(IntegrationTestSupport.CLUSTER_PASSWORD);
      properties.setMaxRedirects(maxRedirects);
      properties.setTopologyRefreshPeriod(Duration.ofMinutes(10));

      manager = new DefaultValkeyConnectionManager(Map.of("drill", properties), connectionFactory,
          clusterConnectionFactory);
      template = new ValkeyTemplateFactory(manager).getTemplate("drill");
    }

    @Override
    public void close() {
      manager.close();
      resources.shutdown();
    }
  }
}
