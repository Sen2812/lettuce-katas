package com.example.valkey.operation;

/** 同步 String 值操作集合。 */
public interface ValueOperations {

  /**
   * 设置 key 对应的值。
   *
   * @param key 键，不能为 {@code null}
   * @param value 值，不能为 {@code null}
   */
  void set(String key, String value);

  /**
   * 获取 key 对应的值。
   *
   * @param key 键，不能为 {@code null}
   * @return 值；key 不存在时返回 {@code null}
   */
  String get(String key);
}
