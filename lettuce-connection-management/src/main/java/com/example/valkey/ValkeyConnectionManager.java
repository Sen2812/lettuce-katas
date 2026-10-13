package com.example.valkey;

import java.util.Set;

/**
 * 多数据源连接管理器：按数据源名称提供连接。
 *
 * <p>
 * 同一数据源名称在其生命周期内始终返回同一个连接实例（懒创建并复用）；连接创建失败不会污染缓存， 下次调用会重新尝试创建。
 */
public interface ValkeyConnectionManager extends AutoCloseable {

  /**
   * 获取指定数据源的连接。
   *
   * @param dataSourceName 已配置的数据源名称
   * @throws ValkeyAccessException 名称未配置或连接创建失败时抛出
   */
  ValkeyConnection getConnection(String dataSourceName);

  /** 已配置的数据源名称集合。 */
  Set<String> getDataSourceNames();

  /** 关闭所有已创建的连接。 */
  @Override
  void close();
}
