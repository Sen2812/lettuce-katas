package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.config.ValkeyAutoConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * 生产关闭路径验证：Spring 上下文销毁时，连接管理器先关闭各数据源连接，共享 {@code
 * ClientResources} 最后释放，整个关闭过程不抛异常。
 */
class ValkeyGracefulShutdownTest {

  @Test
  void shouldCloseConnectionsCleanlyOnContextShutdown() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable("127.0.0.1", 6379),
        "standalone valkey is not reachable, test skipped");

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.getEnvironment()
        .getPropertySources()
        .addFirst(new MapPropertySource("valkey-shutdown-test",
            Map.of("valkey.datasources.ds.host", "127.0.0.1", "valkey.datasources.ds.port", "6379",
                "valkey.datasources.ds.password", "change_me")));
    context.register(ValkeyAutoConfiguration.class);
    context.refresh();

    ValkeyConnection connection =
        context.getBean(ValkeyConnectionManager.class).getConnection("ds");
    connection.ping();
    assertThat(connection.isOpen()).isTrue();

    context.close();

    assertThat(connection.isOpen()).isFalse();
  }
}
