package com.example.valkey.template.impl;

import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.operation.ReactiveKeyOperations;
import com.example.valkey.operation.ReactiveValueOperations;
import com.example.valkey.template.ReactiveValkeyStringTemplate;

/** 响应式模板实现：每次取操作集合时都通过连接管理器获取同一数据源的复用连接。 */
public class DefaultReactiveValkeyStringTemplate implements ReactiveValkeyStringTemplate {

  private final ValkeyConnectionManager connectionManager;
  private final String dataSourceName;

  public DefaultReactiveValkeyStringTemplate(
      ValkeyConnectionManager connectionManager, String dataSourceName) {
    this.connectionManager = connectionManager;
    this.dataSourceName = dataSourceName;
  }

  @Override
  public ReactiveValueOperations opsForValue() {
    return connectionManager
        .getConnection(dataSourceName)
        .getOperations()
        .reactiveValueOperations();
  }

  @Override
  public ReactiveKeyOperations opsForKey() {
    return connectionManager.getConnection(dataSourceName).getOperations().reactiveKeyOperations();
  }
}
