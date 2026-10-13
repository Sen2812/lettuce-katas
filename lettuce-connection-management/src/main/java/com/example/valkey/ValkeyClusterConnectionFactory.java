package com.example.valkey;

import com.example.valkey.config.ValkeyDataSourceProperties;

/**
 * 集群模式连接工厂：创建带拓扑自动刷新与重定向上限配置的集群连接。
 *
 * <p>创建过程中出现的异常（含连接失败重试耗尽）统一转换为 {@link ValkeyAccessException}。
 */
public interface ValkeyClusterConnectionFactory extends ValkeyConnectionFactory {

  @Override
  ValkeyClusterConnection createConnection(
      String dataSourceName, ValkeyDataSourceProperties properties);
}
