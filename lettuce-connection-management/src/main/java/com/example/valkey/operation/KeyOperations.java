package com.example.valkey.operation;

/** 同步 key 级操作集合。 */
public interface KeyOperations {

  /**
   * 删除指定的 key。
   *
   * @param key 键，不能为 {@code null}
   * @return 是否真正删除了一个存在的 key
   */
  boolean delete(String key);
}
