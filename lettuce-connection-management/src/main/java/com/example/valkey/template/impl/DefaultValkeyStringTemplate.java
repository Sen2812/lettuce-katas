package com.example.valkey.template.impl;

import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.operation.KeyOperations;
import com.example.valkey.operation.ValueOperations;
import com.example.valkey.template.ValkeyStringTemplate;

/** 同步模板实现：每次取操作集合时都通过连接管理器获取同一数据源的复用连接。 */
public class DefaultValkeyStringTemplate implements ValkeyStringTemplate {

  private final ValkeyConnectionManager connectionManager;
  private final String dataSourceName;

  public DefaultValkeyStringTemplate(ValkeyConnectionManager connectionManager,
      String dataSourceName) {
    this.connectionManager = connectionManager;
    this.dataSourceName = dataSourceName;
  }

  @Override
  public ValueOperations opsForValue() {
    return connectionManager.getConnection(dataSourceName).getOperations().valueOperations();
  }

  @Override
  public KeyOperations opsForKey() {
    return connectionManager.getConnection(dataSourceName).getOperations().keyOperations();
  }
}
