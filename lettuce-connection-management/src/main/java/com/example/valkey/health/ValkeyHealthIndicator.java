package com.example.valkey.health;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyConnectionManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * valkey 健康检查（Spring Boot Actuator 可选集成）。
 *
 * <p>
 * 对每个已配置数据源执行 PING：全部成功为 {@code UP}，任一失败为 {@code DOWN}，details
 * 中记录每个数据源的状态。检查复用连接管理器中的已建立连接（懒创建），不会额外维护连接池。
 *
 * <p>
 * 仅当应用 classpath 中存在 Actuator 的 {@code HealthIndicator}（例如引入
 * spring-boot-starter-actuator）时才会被自动注册；PING 同样经过统一执行器（含有限重试与异常转换）。
 */
public class ValkeyHealthIndicator implements HealthIndicator {

  private final ValkeyConnectionManager connectionManager;

  public ValkeyHealthIndicator(ValkeyConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    boolean healthy = true;
    for (String dataSourceName : connectionManager.getDataSourceNames()) {
      try {
        connectionManager.getConnection(dataSourceName).ping();
        details.put(dataSourceName, "UP");
      } catch (ValkeyAccessException e) {
        healthy = false;
        details.put(dataSourceName, "DOWN: " + e.getMessage());
      }
    }
    Health.Builder builder = healthy ? Health.up() : Health.down();
    return builder.withDetails(details).build();
  }
}
