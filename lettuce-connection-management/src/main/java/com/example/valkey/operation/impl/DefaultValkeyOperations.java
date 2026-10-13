package com.example.valkey.operation.impl;

import com.example.valkey.operation.KeyOperations;
import com.example.valkey.operation.ReactiveKeyOperations;
import com.example.valkey.operation.ReactiveValueOperations;
import com.example.valkey.operation.ValueOperations;
import com.example.valkey.operation.ValkeyOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import java.util.function.Supplier;

/** 操作集合的默认实现：组合同步与响应式的值、key 操作。 */
public class DefaultValkeyOperations implements ValkeyOperations {

  private final ValueOperations valueOperations;
  private final KeyOperations keyOperations;
  private final ReactiveValueOperations reactiveValueOperations;
  private final ReactiveKeyOperations reactiveKeyOperations;

  public DefaultValkeyOperations(
      String dataSourceName,
      Supplier<RedisClusterCommands<String, String>> syncCommands,
      Supplier<RedisClusterReactiveCommands<String, String>> reactiveCommands,
      ValkeySerializer serializer,
      ValkeyCommandExecutor executor) {
    this.valueOperations =
        new DefaultValueOperations(dataSourceName, syncCommands, serializer, executor);
    this.keyOperations =
        new DefaultKeyOperations(dataSourceName, syncCommands, serializer, executor);
    this.reactiveValueOperations =
        new DefaultReactiveValueOperations(dataSourceName, reactiveCommands, serializer, executor);
    this.reactiveKeyOperations =
        new DefaultReactiveKeyOperations(dataSourceName, reactiveCommands, serializer, executor);
  }

  @Override
  public ValueOperations valueOperations() {
    return valueOperations;
  }

  @Override
  public KeyOperations keyOperations() {
    return keyOperations;
  }

  @Override
  public ReactiveValueOperations reactiveValueOperations() {
    return reactiveValueOperations;
  }

  @Override
  public ReactiveKeyOperations reactiveKeyOperations() {
    return reactiveKeyOperations;
  }
}
