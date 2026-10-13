package com.example.valkey.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.config.ValkeyDataSourceProperties;
import io.lettuce.core.RedisURI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ValkeyUrisTest {

  @Test
  void shouldBuildPlainUriWithDefaults() {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setHost("127.0.0.1");
    properties.setPort(6379);

    RedisURI uri = ValkeyUris.build(properties, "datasource-01");

    assertThat(uri.getHost()).isEqualTo("127.0.0.1");
    assertThat(uri.getPort()).isEqualTo(6379);
    assertThat(uri.isSsl()).isFalse();
    assertThat(uri.getClientName()).isEqualTo("datasource-01");
    assertThat(uri.getTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void shouldApplyCustomClientNameSslAndCommandTimeout() {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setHost("valkey.example.com");
    properties.setPort(6380);
    properties.setUsername("default");
    properties.setPassword("secret");
    properties.setSsl(true);
    properties.setClientName("checkout-service");
    properties.setConnectTimeout(Duration.ofSeconds(3));
    properties.setCommandTimeout(Duration.ofSeconds(7));

    RedisURI uri = ValkeyUris.build(properties, "datasource-02");

    assertThat(uri.isSsl()).isTrue();
    assertThat(uri.getClientName()).isEqualTo("checkout-service");
    assertThat(uri.getTimeout()).isEqualTo(Duration.ofSeconds(7));
  }
}
