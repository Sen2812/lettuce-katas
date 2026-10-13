package com.example.valkey.factory;

import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionFactory;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.connection.DefaultValkeyConnection;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;

/**
 * 单机（或主从）模式连接工厂。
 *
 * <p>
 * 连接以 {@link StringCodec#UTF8} 编解码，共享框架级 {@link ClientResources}；创建失败（含重试耗尽） 统一转换为
 * {@code ValkeyAccessException}。
 */
public class DefaultValkeyConnectionFactory implements ValkeyConnectionFactory {

  private final ClientResources clientResources;
  private final ValkeySerializer serializer;
  private final ValkeyCommandExecutor executor;

  public DefaultValkeyConnectionFactory(ClientResources clientResources,
      ValkeySerializer serializer, ValkeyCommandExecutor executor) {
    this.clientResources = clientResources;
    this.serializer = serializer;
    this.executor = executor;
  }

  @Override
  public ValkeyConnection createConnection(String dataSourceName,
      ValkeyDataSourceProperties properties) {
    return executor.execute(dataSourceName, () -> {
      RedisClient client = RedisClient.create(clientResources, ValkeyUris.build(properties));
      try {
        client.setOptions(ClientOptions.builder()
            .autoReconnect(true)
            .socketOptions(SocketOptions.builder().connectTimeout(properties.getTimeout()).build())
            .timeoutOptions(TimeoutOptions.enabled(properties.getTimeout()))
            .build());
        StatefulRedisConnection<String, String> connection = client.connect(StringCodec.UTF8);
        return new DefaultValkeyConnection(dataSourceName, client, connection, serializer,
            executor);
      } catch (RuntimeException e) {
        client.shutdown();
        throw e;
      }
    });
  }
}
