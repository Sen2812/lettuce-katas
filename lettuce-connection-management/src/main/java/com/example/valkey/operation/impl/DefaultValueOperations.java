package com.example.valkey.operation.impl;

import com.example.valkey.operation.ValueOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import java.util.function.Supplier;

/** 同步 String 值操作实现：序列化键值后执行 SET / GET。 */
public class DefaultValueOperations implements ValueOperations {

  private final String dataSourceName;
  private final Supplier<RedisClusterCommands<String, String>> commands;
  private final ValkeySerializer serializer;
  private final ValkeyCommandExecutor executor;

  public DefaultValueOperations(String dataSourceName,
      Supplier<RedisClusterCommands<String, String>> commands, ValkeySerializer serializer,
      ValkeyCommandExecutor executor) {
    this.dataSourceName = dataSourceName;
    this.commands = commands;
    this.serializer = serializer;
    this.executor = executor;
  }

  @Override
  public void set(String key, String value) {
    String serializedKey = serializer.serialize(key);
    String serializedValue = serializer.serialize(value);
    executor.executeVoid(dataSourceName, () -> commands.get().set(serializedKey, serializedValue));
  }

  @Override
  public String get(String key) {
    String serializedKey = serializer.serialize(key);
    String value = executor.execute(dataSourceName, () -> commands.get().get(serializedKey));
    return serializer.deserialize(value);
  }
}
