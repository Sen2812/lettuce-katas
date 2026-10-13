package com.example.valkey;

import com.example.valkey.operation.ValkeyOperations;

/**
 * 单个 valkey 数据源连接的门面接口。
 *
 * <p>
 * 由连接工厂创建、由连接管理器按名称复用；通过 {@link #getOperations()} 向外暴露该连接上的操作集合。
 */
public interface ValkeyConnection extends AutoCloseable {

  /** 暴露该连接上的操作集合（值操作与 key 操作）。 */
  ValkeyOperations getOperations();

  /** 底层连接当前是否处于打开状态。 */
  boolean isOpen();

  /**
   * 向服务端发送 PING 检查连接健康；失败时抛出 {@code ValkeyAccessException}。
   *
   * <p>
   * 与操作集合一样经过统一执行器（含有限重试与异常转换），供健康检查等场景使用。
   */
  void ping();

  /** 该连接绑定的数据源名称。 */
  String getDataSourceName();

  /** 关闭连接并释放该连接独占的客户端资源。 */
  @Override
  void close();
}
