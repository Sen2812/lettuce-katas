package com.example.valkey.serializer;

import com.example.valkey.ValkeyAccessException;

/**
 * 键值序列化契约：把 String 键与值编码为服务端可见的 UTF-8 字符串表示，并支持反向还原。
 *
 * <p>框架固定使用 Jackson 实现（{@link JacksonValkeySerializer}），序列化与反序列化失败时抛出
 * {@link ValkeyAccessException} 并保留原始异常。同一 key 的读写必须使用同一实现，否则无法还原。
 */
public interface ValkeySerializer {

  /**
   * 将传入值序列化为 UTF-8 字符串；{@code null} 原样返回。
   *
   * @throws ValkeyAccessException 序列化失败
   */
  String serialize(String value);

  /**
   * 将服务端字符串反序列化为原值；{@code null} 原样返回。
   *
   * @throws ValkeyAccessException 反序列化失败
   */
  String deserialize(String value);
}
