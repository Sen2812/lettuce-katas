package com.example.valkey.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.valkey.ValkeyAccessException;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

class JacksonValkeySerializerTest {

  private final JacksonValkeySerializer serializer = JacksonValkeySerializer.create();

  @Test
  void shouldSerializeStringAsJsonStringLiteral() {
    assertThat(serializer.serialize("hello")).isEqualTo("\"hello\"");
  }

  @Test
  void shouldRoundTripAsciiChineseEmojiAndSpecialCharacters() {
    String[] values = {"hello", "中文值", "emoji \uD83D\uDE80", "", "line\nfeed", "quote\"inside"};

    for (String value : values) {
      String serialized = serializer.serialize(value);

      assertThat(serialized).startsWith("\"");
      assertThat(serializer.deserialize(serialized)).isEqualTo(value);
    }
  }

  @Test
  void nullShouldPassThroughInBothDirections() {
    assertThat(serializer.serialize(null)).isNull();
    assertThat(serializer.deserialize(null)).isNull();
  }

  @Test
  void shouldFailWithValkeyAccessExceptionOnNonJsonInput() {
    assertThatThrownBy(() -> serializer.deserialize("plain-text"))
        .isInstanceOf(ValkeyAccessException.class)
        .hasCauseInstanceOf(JacksonException.class);
  }
}
