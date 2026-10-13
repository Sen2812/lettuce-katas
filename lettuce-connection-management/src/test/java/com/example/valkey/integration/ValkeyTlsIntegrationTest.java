package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.config.ValkeyMode;
import com.example.valkey.factory.DefaultValkeyClusterConnectionFactory;
import com.example.valkey.factory.DefaultValkeyConnectionFactory;
import com.example.valkey.manager.DefaultValkeyConnectionManager;
import com.example.valkey.serializer.JacksonValkeySerializer;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import com.example.valkey.support.ValkeyRetryOptions;
import com.example.valkey.template.ValkeyStringTemplate;
import com.example.valkey.template.ValkeyTemplateFactory;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TLS 集成测试：连接 WSL 中启用 TLS 的 valkey（127.0.0.1:6380，自签 CA）。
 *
 * <p>
 * 前置条件：已执行 {@code scripts/valkey-tls.sh up}（CA 证书位于 target/tls/ca.crt 且服务可达）， 否则自动跳过。
 */
class ValkeyTlsIntegrationTest {

  private static final String DATA_SOURCE = "tls-it";
  private static final String HOST = "127.0.0.1";
  private static final int TLS_PORT = 6380;
  private static final String PASSWORD = "change_me";
  private static final Path CA_CERTIFICATE = Path.of("target", "tls", "ca.crt");

  private static ClientResources clientResources;
  private static DefaultValkeyConnectionFactory connectionFactory;
  private static DefaultValkeyClusterConnectionFactory clusterConnectionFactory;
  private static ValkeyConnectionManager connectionManager;
  private static ValkeyStringTemplate template;

  @BeforeAll
  static void setUp() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable(HOST, TLS_PORT),
        () -> "TLS valkey @ " + HOST + ":" + TLS_PORT + " is not reachable, test skipped");
    Assumptions.assumeTrue(Files.isRegularFile(CA_CERTIFICATE), () -> "CA certificate not found at "
        + CA_CERTIFICATE.toAbsolutePath() + "; run scripts/valkey-tls.sh up");

    clientResources = DefaultClientResources.create();
    ValkeySerializer serializer = JacksonValkeySerializer.create();
    ValkeyCommandExecutor executor = new ValkeyCommandExecutor(ValkeyRetryOptions.DEFAULT);
    connectionFactory = new DefaultValkeyConnectionFactory(clientResources, serializer, executor);
    clusterConnectionFactory =
        new DefaultValkeyClusterConnectionFactory(clientResources, serializer, executor);

    connectionManager = new DefaultValkeyConnectionManager(Map.of(DATA_SOURCE, tlsProperties(true)),
        connectionFactory, clusterConnectionFactory);
    template = new ValkeyTemplateFactory(connectionManager).getTemplate(DATA_SOURCE);
  }

  @AfterAll
  static void tearDown() {
    if (connectionManager != null) {
      connectionManager.close();
    }
    if (clientResources != null) {
      clientResources.shutdown();
    }
  }

  private static ValkeyDataSourceProperties tlsProperties(boolean withTrustCertificate) {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setMode(ValkeyMode.STANDALONE);
    properties.setHost(HOST);
    properties.setPort(TLS_PORT);
    properties.setPassword(PASSWORD);
    properties.setSsl(true);
    if (withTrustCertificate) {
      properties.setSslTrustCertificate(CA_CERTIFICATE.toAbsolutePath().toString());
    }
    return properties;
  }

  @Test
  void shouldSetGetAndDeleteOverTls() {
    String key = "it:tls:" + UUID.randomUUID();

    template.opsForValue().set(key, "tls-value");

    assertThat(template.opsForValue().get(key)).isEqualTo("tls-value");
    assertThat(template.opsForKey().delete(key)).isTrue();
  }

  @Test
  void pingShouldSucceedOverTls() {
    ValkeyConnection connection = connectionManager.getConnection(DATA_SOURCE);

    connection.ping();

    assertThat(connection.isOpen()).isTrue();
  }

  @Test
  void shouldRejectSelfSignedCertificateWithoutTrustConfiguration() {
    try (ValkeyConnectionManager untrusting =
        new DefaultValkeyConnectionManager(Map.of("tls-untrusted", tlsProperties(false)),
            connectionFactory, clusterConnectionFactory)) {

      assertThatThrownBy(() -> untrusting.getConnection("tls-untrusted"))
          .isInstanceOf(ValkeyAccessException.class);
    }
  }
}
