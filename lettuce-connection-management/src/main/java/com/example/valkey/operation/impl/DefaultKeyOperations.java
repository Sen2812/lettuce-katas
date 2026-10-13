package com.example.valkey.operation.impl;

import com.example.valkey.operation.KeyOperations;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import java.util.function.Supplier;

/** 同步 key 级操作实现：序列化 key 后执行 DEL。 */
public class DefaultKeyOperations implements KeyOperations {

  private final String dataSourceName;
  private final Supplier<RedisClusterCommands<String, String>> commands;
  private final ValkeySerializer serializer;
  private final ValkeyCommandExecutor executor;

  public DefaultKeyOperations(String dataSourceName,
      Supplier<RedisClusterCommands<String, String>> commands, ValkeySerializer serializer,
      ValkeyCommandExecutor executor) {
    this.dataSourceName = dataSourceName;
    this.commands = commands;
    this.serializer = serializer;
    this.executor = executor;
  }

  @Override
  public boolean delete(String key) {
    String serializedKey = serializer.serialize(key);
    Long deleted = executor.execute(dataSourceName, () -> commands.get().del(serializedKey));
    return deleted != null && deleted > 0;
  }
}
