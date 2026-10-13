package com.example.valkey.operation.impl;

import com.example.valkey.operation.ReactiveKeyOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/** 响应式 key 级操作实现：重试与异常转换由 {@link ValkeyCommandExecutor} 统一处理。 */
public class DefaultReactiveKeyOperations implements ReactiveKeyOperations {

  private final String dataSourceName;
  private final Supplier<RedisClusterReactiveCommands<String, String>> commands;
  private final ValkeySerializer serializer;
  private final ValkeyCommandExecutor executor;

  public DefaultReactiveKeyOperations(String dataSourceName,
      Supplier<RedisClusterReactiveCommands<String, String>> commands, ValkeySerializer serializer,
      ValkeyCommandExecutor executor) {
    this.dataSourceName = dataSourceName;
    this.commands = commands;
    this.serializer = serializer;
    this.executor = executor;
  }

  @Override
  public Mono<Boolean> delete(String key) {
    String serializedKey = serializer.serialize(key);
    return executor.executeReactive(dataSourceName, () -> commands.get().del(serializedKey))
        .map(deleted -> deleted != null && deleted > 0);
  }
}
