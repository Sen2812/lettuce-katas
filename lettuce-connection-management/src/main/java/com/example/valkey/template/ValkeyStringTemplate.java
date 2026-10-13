package com.example.valkey.template;

import com.example.valkey.operation.KeyOperations;
import com.example.valkey.operation.ValueOperations;

/**
 * 同步操作模板：面向单个数据源，通过连接管理器获取（复用的）连接并暴露操作集合。
 *
 * <p>
 * {@code opsForValue()} 与 {@code opsForKey()} 的实现均取自连接的 {@code getOperations()}。
 */
public interface ValkeyStringTemplate {

  /** 获取 String 值操作集合（SET / GET）。 */
  ValueOperations opsForValue();

  /** 获取 key 级操作集合（DEL）。 */
  KeyOperations opsForKey();
}
