package com.example.valkey.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;

class ValkeyPropertiesTest {

  @Test
  void shouldBindDataSourcesAndRetryConfiguration() {
    Map<String, Object> source = new HashMap<>();
    source.put("valkey.retry.max-attempts", "5");
    source.put("valkey.retry.backoff", "300ms");
    source.put("valkey.datasources.ds1.mode", "cluster");
    source.put("valkey.datasources.ds1.host", "127.0.0.1");
    source.put("valkey.datasources.ds1.port", "7001");
    source.put("valkey.datasources.ds1.username", "default");
    source.put("valkey.datasources.ds1.password", "change_me");
    source.put("valkey.datasources.ds1.timeout", "10s");
    source.put("valkey.datasources.ds1.max-redirects", "7");
    source.put("valkey.datasources.ds1.topology-refresh-period", "45s");
    source.put("valkey.datasources.ds2.mode", "standalone");
    source.put("valkey.datasources.ds2.host", "127.0.0.1");
    source.put("valkey.datasources.ds2.port", "6379");

    ValkeyProperties properties = bind(source);

    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(5);
    assertThat(properties.getRetry().getBackoff()).isEqualTo(Duration.ofMillis(300));
    assertThat(properties.getDatasources()).containsOnlyKeys("ds1", "ds2");

    ValkeyDataSourceProperties cluster = properties.getDatasources().get("ds1");
    assertThat(cluster.getMode()).isEqualTo(ValkeyMode.CLUSTER);
    assertThat(cluster.getHost()).isEqualTo("127.0.0.1");
    assertThat(cluster.getPort()).isEqualTo(7001);
    assertThat(cluster.getUsername()).isEqualTo("default");
    assertThat(cluster.getPassword()).isEqualTo("change_me");
    assertThat(cluster.getTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(cluster.getMaxRedirects()).isEqualTo(7);
    assertThat(cluster.getTopologyRefreshPeriod()).isEqualTo(Duration.ofSeconds(45));

    ValkeyDataSourceProperties standalone = properties.getDatasources().get("ds2");
    assertThat(standalone.getMode()).isEqualTo(ValkeyMode.STANDALONE);
    assertThat(standalone.getPort()).isEqualTo(6379);
    assertThat(standalone.getTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void shouldFallBackToDefaultValues() {
    ValkeyProperties properties = bind(new HashMap<>());

    assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
    assertThat(properties.getRetry().getBackoff()).isEqualTo(Duration.ofMillis(200));
    assertThat(properties.getDatasources()).isEmpty();

    ValkeyDataSourceProperties defaults = new ValkeyDataSourceProperties();
    assertThat(defaults.getMode()).isEqualTo(ValkeyMode.STANDALONE);
    assertThat(defaults.getHost()).isEqualTo("localhost");
    assertThat(defaults.getPort()).isEqualTo(6379);
    assertThat(defaults.getTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(defaults.getMaxRedirects()).isEqualTo(5);
    assertThat(defaults.getTopologyRefreshPeriod()).isEqualTo(Duration.ofSeconds(30));
  }

  private static ValkeyProperties bind(Map<String, Object> source) {
    Binder binder =
        new Binder(
            List.of(new MapConfigurationPropertySource(source)),
            null,
            ApplicationConversionService.getSharedInstance());
    return binder.bind("valkey", Bindable.of(ValkeyProperties.class)).orElseGet(ValkeyProperties::new);
  }
}
