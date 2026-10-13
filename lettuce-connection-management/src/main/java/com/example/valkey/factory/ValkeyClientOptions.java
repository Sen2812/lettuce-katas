package com.example.valkey.factory;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.config.ValkeyDataSourceProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import java.io.File;

/**
 * 由数据源配置构造 Lettuce 客户端选项。
 *
 * <p>
 * 统一在这里应用自动重连、连接/命令超时、TLS 与集群拓扑刷新策略，保证两种连接模式行为一致； 配置无效（如信任证书文件不存在）时快速失败，避免带着错误配置启动。
 */
final class ValkeyClientOptions {

  private ValkeyClientOptions() {}

  static ClientOptions standalone(ValkeyDataSourceProperties properties) {
    ClientOptions.Builder builder = ClientOptions.builder();
    applyCommon(builder, properties);
    return builder.build();
  }

  static ClusterClientOptions cluster(ValkeyDataSourceProperties properties) {
    ClusterClientOptions.Builder builder = ClusterClientOptions.builder();
    applyCommon(builder, properties);
    return builder.maxRedirects(properties.getMaxRedirects())
        .topologyRefreshOptions(ClusterTopologyRefreshOptions.builder()
            .enablePeriodicRefresh(properties.getTopologyRefreshPeriod())
            .enableAllAdaptiveRefreshTriggers()
            .build())
        .build();
  }

  private static void applyCommon(ClientOptions.Builder builder,
      ValkeyDataSourceProperties properties) {
    builder.autoReconnect(true)
        .socketOptions(
            SocketOptions.builder().connectTimeout(properties.resolveConnectTimeout()).build())
        .timeoutOptions(TimeoutOptions.enabled(properties.resolveCommandTimeout()));
    SslOptions sslOptions = sslOptions(properties);
    if (sslOptions != null) {
      builder.sslOptions(sslOptions);
    }
  }

  /** 构造 TLS 选项；未启用 TLS 时返回 {@code null}。 */
  static SslOptions sslOptions(ValkeyDataSourceProperties properties) {
    if (!properties.isSsl()) {
      return null;
    }
    String trustCertificate = properties.getSslTrustCertificate();
    if (trustCertificate == null || trustCertificate.isBlank()) {
      return SslOptions.create();
    }
    File certificate = new File(trustCertificate);
    if (!certificate.isFile()) {
      throw new ValkeyAccessException(
          "SSL trust certificate file does not exist: " + certificate.getAbsolutePath());
    }
    return SslOptions.builder().trustManager(certificate).build();
  }
}
