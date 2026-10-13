package com.example.valkey.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.config.ValkeyMode;
import com.example.valkey.config.ValkeyProperties;
import com.example.valkey.template.ValkeyStringTemplate;
import com.example.valkey.template.ValkeyTemplateFactory;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 装配集成测试：验证 {@code ValkeyAutoConfiguration} 与 {@code application.yaml} 的绑定，
 * 并通过两种模式的数据源完成端到端读写。
 */
@SpringBootTest
class ValkeySpringBootIntegrationTest {

  @Autowired
  private ValkeyProperties properties;
  @Autowired
  private ValkeyConnectionManager connectionManager;
  @Autowired
  private ValkeyTemplateFactory templateFactory;

  private static String uniqueKey(String prefix) {
    return "it:spring:" + prefix + ":" + UUID.randomUUID();
  }

  @Test
  void shouldBindDataSourcesFromApplicationYaml() {
    assertThat(properties.getDatasources()).containsOnlyKeys("datasource-01", "datasource-02");
    assertThat(properties.getDatasources().get("datasource-01").getMode())
        .isEqualTo(ValkeyMode.STANDALONE);
    assertThat(properties.getDatasources().get("datasource-02").getMode())
        .isEqualTo(ValkeyMode.CLUSTER);
    assertThat(connectionManager.getDataSourceNames()).containsExactlyInAnyOrder("datasource-01",
        "datasource-02");
  }

  @Test
  void shouldReuseOneConnectionPerDataSource() {
    assertThat(connectionManager.getConnection("datasource-01"))
        .isSameAs(connectionManager.getConnection("datasource-01"));
  }

  @Test
  void shouldOperateOnStandaloneDataSource() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable("127.0.0.1", 6379),
        "standalone valkey is not reachable, test skipped");

    ValkeyStringTemplate template = templateFactory.getTemplate("datasource-01");
    String key = uniqueKey("standalone");

    template.opsForValue().set(key, "spring-standalone");
    assertThat(template.opsForValue().get(key)).isEqualTo("spring-standalone");
    assertThat(template.opsForKey().delete(key)).isTrue();
  }

  @Test
  void shouldOperateOnClusterDataSource() {
    Assumptions.assumeTrue(IntegrationTestSupport.reachable("127.0.0.1", 7001),
        "cluster valkey is not reachable, test skipped");

    ValkeyStringTemplate template = templateFactory.getTemplate("datasource-02");
    String key = uniqueKey("cluster");

    template.opsForValue().set(key, "spring-cluster");
    assertThat(template.opsForValue().get(key)).isEqualTo("spring-cluster");
    assertThat(template.opsForKey().delete(key)).isTrue();
  }
}
