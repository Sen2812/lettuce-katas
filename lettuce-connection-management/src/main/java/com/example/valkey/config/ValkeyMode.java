package com.example.valkey.config;

/** 数据源部署模式。 */
public enum ValkeyMode {

  /** 单机（或主从）模式。 */
  STANDALONE,

  /** 集群模式。 */
  CLUSTER
}
