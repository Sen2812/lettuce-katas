package com.example.valkey.operation;

/**
 * 一个数据源上操作集合的聚合入口。
 *
 * <p>由连接门面 {@code ValkeyConnection#getOperations()} 暴露，模板类再从中取出 {@code
 * opsForValue}、{@code opsForKey} 对应的操作集合。
 */
public interface ValkeyOperations {

  /** 同步 String 值操作集合。 */
  ValueOperations valueOperations();

  /** 同步 key 级操作集合。 */
  KeyOperations keyOperations();

  /** 响应式 String 值操作集合。 */
  ReactiveValueOperations reactiveValueOperations();

  /** 响应式 key 级操作集合。 */
  ReactiveKeyOperations reactiveKeyOperations();
}
