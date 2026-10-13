package com.example.valkey.connection;

import com.example.valkey.ValkeyClusterConnection;
import com.example.valkey.operation.ValkeyOperations;
import com.example.valkey.operation.impl.DefaultValkeyOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 集群模式的连接实现。
 *
 * <p>
 * 持有该数据源独占的 {@link RedisClusterClient}；拓扑变化由 Lettuce 在后台自动刷新，{@link #getNodeIds()}
 * 反映当前一次刷新后客户端已知的节点集合。
 */
public class DefaultValkeyClusterConnection implements ValkeyClusterConnection {

  private final String dataSourceName;
  private final RedisClusterClient client;
  private final StatefulRedisClusterConnection<String, String> connection;
  private final ValkeyCommandExecutor executor;
  private final ValkeyOperations operations;

  public DefaultValkeyClusterConnection(String dataSourceName, RedisClusterClient client,
      StatefulRedisClusterConnection<String, String> connection, ValkeySerializer serializer,
      ValkeyCommandExecutor executor) {
    this.dataSourceName = dataSourceName;
    this.client = client;
    this.connection = connection;
    this.executor = executor;
    this.operations = new DefaultValkeyOperations(dataSourceName, () -> connection.sync(),
        () -> connection.reactive(), serializer, executor);
  }

  @Override
  public ValkeyOperations getOperations() {
    return operations;
  }

  @Override
  public boolean isOpen() {
    return connection.isOpen();
  }

  @Override
  public void ping() {
    executor.executeVoid(dataSourceName, () -> connection.sync().ping());
  }

  @Override
  public String getDataSourceName() {
    return dataSourceName;
  }

  @Override
  public Set<String> getNodeIds() {
    return connection.getPartitions()
        .getPartitions()
        .stream()
        .map(RedisClusterNode::getNodeId)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void close() {
    connection.close();
    client.shutdown();
  }
}
