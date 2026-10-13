package com.example.valkey.operation;

import reactor.core.publisher.Mono;

/** 响应式 String 值操作集合。 */
public interface ReactiveValueOperations {

  /** 设置 key 对应的值，完成时发出完成信号。 */
  Mono<Void> set(String key, String value);

  /** 获取 key 对应的值；key 不存在时发出空 Mono。 */
  Mono<String> get(String key);
}
