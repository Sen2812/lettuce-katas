package com.example.valkey.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.config.ValkeyDataSourceProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ValkeyClientOptionsTest {

  private static ValkeyDataSourceProperties properties() {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setTimeout(Duration.ofSeconds(10));
    return properties;
  }

  @Test
  void standaloneShouldApplyAutoReconnectAndTimeoutsByDefault() {
    ClientOptions options = ValkeyClientOptions.standalone(properties());

    assertThat(options.isAutoReconnect()).isTrue();
    assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
  }

  @Test
  void shouldUseDedicatedConnectTimeoutWhenConfigured() {
    ValkeyDataSourceProperties properties = properties();
    properties.setConnectTimeout(Duration.ofSeconds(3));
    properties.setCommandTimeout(Duration.ofSeconds(7));

    ClientOptions options = ValkeyClientOptions.standalone(properties);

    assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
  }

  @Test
  void clusterShouldApplyRedirectLimitAndTopologyRefresh() {
    ValkeyDataSourceProperties properties = properties();
    properties.setMaxRedirects(7);
    properties.setTopologyRefreshPeriod(Duration.ofSeconds(45));

    ClusterClientOptions options = ValkeyClientOptions.cluster(properties);

    assertThat(options.getMaxRedirects()).isEqualTo(7);
    assertThat(options.getTopologyRefreshOptions().isPeriodicRefreshEnabled()).isTrue();
    assertThat(options.getTopologyRefreshOptions().getRefreshPeriod())
        .isEqualTo(Duration.ofSeconds(45));
    assertThat(options.getTopologyRefreshOptions().getAdaptiveRefreshTriggers()).hasSize(5);
    assertThat(options.isAutoReconnect()).isTrue();
  }

  @Test
  void shouldEnableSslWithSystemTrustStoreWhenNoCertificateConfigured() {
    ValkeyDataSourceProperties properties = properties();
    properties.setSsl(true);

    ClientOptions options = ValkeyClientOptions.standalone(properties);

    assertThat(options.getSslOptions()).isNotNull();
  }

  @Test
  void shouldFailFastWhenTrustCertificateDoesNotExist() {
    ValkeyDataSourceProperties properties = properties();
    properties.setSsl(true);
    properties.setSslTrustCertificate("/path/does/not/exist/ca.crt");

    assertThatThrownBy(() -> ValkeyClientOptions.standalone(properties))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("SSL trust certificate file does not exist");
  }

  @Test
  void sslOptionsShouldBeNullWhenSslIsDisabled() {
    assertThat(ValkeyClientOptions.sslOptions(properties())).isNull();
  }
}
