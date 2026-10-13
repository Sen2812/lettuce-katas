package com.example.valkey.serializer;

import com.example.valkey.ValkeyAccessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于 Jackson 3 的字符串序列化器。
 *
 * <p>
 * 键与值统一交给 Jackson 序列化为 JSON 文本（UTF-8 字符串），再经 Lettuce 的 UTF-8 编解码器写入 服务端；读取时由 Jackson
 * 反向还原。字符串在存储中表现为带引号的 JSON 字符串字面量。
 */
public class JacksonValkeySerializer implements ValkeySerializer {

  private final ObjectMapper objectMapper;

  public JacksonValkeySerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 使用默认配置的 Jackson 3 {@link JsonMapper} 创建序列化器。 */
  public static JacksonValkeySerializer create() {
    return new JacksonValkeySerializer(JsonMapper.builder().build());
  }

  @Override
  public String serialize(String value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException e) {
      throw new ValkeyAccessException("Failed to serialize value with Jackson", e);
    }
  }

  @Override
  public String deserialize(String value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readValue(value, String.class);
    } catch (JacksonException e) {
      throw new ValkeyAccessException(
          "Failed to deserialize value with Jackson, raw value: " + value, e);
    }
  }
}
