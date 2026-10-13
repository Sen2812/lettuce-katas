package com.example.valkey.template;

import com.example.valkey.operation.ReactiveKeyOperations;
import com.example.valkey.operation.ReactiveValueOperations;

/**
 * 响应式操作模板：面向单个数据源，通过连接管理器获取（复用的）连接并暴露响应式操作集合。
 */
public interface ReactiveValkeyStringTemplate {

  /** 获取响应式 String 值操作集合。 */
  ReactiveValueOperations opsForValue();

  /** 获取响应式 key 级操作集合。 */
  ReactiveKeyOperations opsForKey();
}
