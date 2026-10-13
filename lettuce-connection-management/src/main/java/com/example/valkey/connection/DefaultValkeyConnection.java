package com.example.valkey.connection;

import com.example.valkey.ValkeyConnection;
import com.example.valkey.operation.ValkeyOperations;
import com.example.valkey.operation.impl.DefaultValkeyOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

/**
 * 单机（或主从）模式的连接实现。
 *
 * <p>
 * 持有该数据源独占的 {@link RedisClient}；关闭连接时同时关闭客户端，共享的 {@code ClientResources} 由框架统一管理。
 */
public class DefaultValkeyConnection implements ValkeyConnection {

  private final String dataSourceName;
  private final RedisClient client;
  private final StatefulRedisConnection<String, String> connection;
  private final ValkeyCommandExecutor executor;
  private final ValkeyOperations operations;

  public DefaultValkeyConnection(String dataSourceName, RedisClient client,
      StatefulRedisConnection<String, String> connection, ValkeySerializer serializer,
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
  public void close() {
    connection.close();
    client.shutdown();
  }
}
