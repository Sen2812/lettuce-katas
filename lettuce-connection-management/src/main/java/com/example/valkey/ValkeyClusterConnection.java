package com.example.valkey;

import java.util.Set;

/**
 * 集群模式连接门面：在普通连接能力之外暴露集群拓扑诊断信息。
 *
 * <p>
 * 拓扑的自动更新、MOVED/ASK 重定向处理、节点故障后的重连均由 Lettuce 内部完成（依据 {@code
 * ClusterClientOptions} 与 {@code ClusterTopologyRefreshOptions} 配置），无需应用层额外实现。
 */
public interface ValkeyClusterConnection extends ValkeyConnection {

  /** 当前客户端已知的集群节点 ID 集合（随拓扑刷新而变化）。 */
  Set<String> getNodeIds();
}
