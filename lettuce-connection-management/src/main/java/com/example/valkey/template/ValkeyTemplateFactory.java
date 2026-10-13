package com.example.valkey.template;

import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.template.impl.DefaultReactiveValkeyStringTemplate;
import com.example.valkey.template.impl.DefaultValkeyStringTemplate;

/** 模板工厂：按数据源名称创建与数据源绑定的同步/响应式模板。 */
public class ValkeyTemplateFactory {

  private final ValkeyConnectionManager connectionManager;

  public ValkeyTemplateFactory(ValkeyConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  /**
   * 创建指定数据源的同步模板。
   *
   * @param dataSourceName 已配置的数据源名称
   */
  public ValkeyStringTemplate getTemplate(String dataSourceName) {
    return new DefaultValkeyStringTemplate(connectionManager, dataSourceName);
  }

  /**
   * 创建指定数据源的响应式模板。
   *
   * @param dataSourceName 已配置的数据源名称
   */
  public ReactiveValkeyStringTemplate getReactiveTemplate(String dataSourceName) {
    return new DefaultReactiveValkeyStringTemplate(connectionManager, dataSourceName);
  }
}
