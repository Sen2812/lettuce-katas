package com.example.valkey;

import com.example.valkey.config.ValkeyDataSourceProperties;

/**
 * 数据源连接工厂：根据数据源配置创建并初始化连接。
 *
 * <p>
 * 创建过程中出现的异常（含连接失败重试耗尽）统一转换为 {@link ValkeyAccessException}。
 */
public interface ValkeyConnectionFactory {

  /**
   * 创建指定数据源的连接。
   *
   * @param dataSourceName 数据源名称，用于日志与诊断
   * @param properties 数据源配置项
   */
  ValkeyConnection createConnection(String dataSourceName, ValkeyDataSourceProperties properties);
}
