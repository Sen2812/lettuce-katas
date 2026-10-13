package com.example.valkey.factory;

import com.example.valkey.config.ValkeyDataSourceProperties;
import io.lettuce.core.RedisURI;
import org.springframework.util.StringUtils;

/** 数据源配置到 Lettuce {@link RedisURI} 的转换工具。 */
final class ValkeyUris {

  private ValkeyUris() {}

  static RedisURI build(ValkeyDataSourceProperties properties, String dataSourceName) {
    RedisURI.Builder builder = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
        .withTimeout(properties.resolveCommandTimeout())
        .withSsl(properties.isSsl())
        .withClientName(properties.resolveClientName(dataSourceName));
    if (StringUtils.hasText(properties.getPassword())) {
      if (StringUtils.hasText(properties.getUsername())) {
        builder.withAuthentication(properties.getUsername(), properties.getPassword());
      } else {
        builder.withPassword(properties.getPassword());
      }
    }
    return builder.build();
  }
}
