package com.example.valkey.factory;

import com.example.valkey.ValkeyClusterConnection;
import com.example.valkey.ValkeyClusterConnectionFactory;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.connection.DefaultValkeyClusterConnection;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import java.util.List;

/**
 * 集群模式连接工厂。
 *
 * <p>启用周期性拓扑刷新与全部自适应刷新触发器（MOVED/ASK 重定向、节点未知、槽位未覆盖、持久重连），
 * 由 Lettuce 自动维护拓扑与重定向，应用层无需自行处理；重定向上限由配置的 {@code max-redirects}
 * 控制。
 */
public class DefaultValkeyClusterConnectionFactory implements ValkeyClusterConnectionFactory {

  private final ClientResources clientResources;
  private final ValkeySerializer serializer;
  private final ValkeyCommandExecutor executor;

  public DefaultValkeyClusterConnectionFactory(
      ClientResources clientResources, ValkeySerializer serializer, ValkeyCommandExecutor executor) {
    this.clientResources = clientResources;
    this.serializer = serializer;
    this.executor = executor;
  }

  @Override
  public ValkeyClusterConnection createConnection(
      String dataSourceName, ValkeyDataSourceProperties properties) {
    return executor.execute(
        dataSourceName,
        () -> {
          RedisClusterClient client =
              RedisClusterClient.create(clientResources, List.of(ValkeyUris.build(properties)));
          try {
            ClusterTopologyRefreshOptions topologyRefreshOptions =
                ClusterTopologyRefreshOptions.builder()
                    .enablePeriodicRefresh(properties.getTopologyRefreshPeriod())
                    .enableAllAdaptiveRefreshTriggers()
                    .build();
            ClusterClientOptions clientOptions =
                ClusterClientOptions.builder()
                    .autoReconnect(true)
                    .maxRedirects(properties.getMaxRedirects())
                    .topologyRefreshOptions(topologyRefreshOptions)
                    .socketOptions(
                        SocketOptions.builder()
                            .connectTimeout(properties.getTimeout())
                            .build())
                    .timeoutOptions(TimeoutOptions.enabled(properties.getTimeout()))
                    .build();
            client.setOptions(clientOptions);
            StatefulRedisClusterConnection<String, String> connection =
                client.connect(StringCodec.UTF8);
            return new DefaultValkeyClusterConnection(
                dataSourceName, client, connection, serializer, executor);
          } catch (RuntimeException e) {
            client.shutdown();
            throw e;
          }
        });
  }
}
