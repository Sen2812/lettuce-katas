package com.example.valkey.operation;

import reactor.core.publisher.Mono;

/** 响应式 key 级操作集合。 */
public interface ReactiveKeyOperations {

  /** 删除指定的 key，返回是否真正删除了一个存在的 key。 */
  Mono<Boolean> delete(String key);
}
