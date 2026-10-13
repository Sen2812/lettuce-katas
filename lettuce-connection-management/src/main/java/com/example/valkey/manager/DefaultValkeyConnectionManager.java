package com.example.valkey.manager;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionFactory;
import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.ValkeyClusterConnectionFactory;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.config.ValkeyMode;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多数据源连接管理器实现。
 *
 * <p>
 * 按数据源名称懒创建连接并复用：同一名称在管理器生命周期内始终返回同一个连接实例；创建失败的异常 由工厂转换为 {@code ValkeyAccessException} 抛出，且不会写入缓存。
 */
public class DefaultValkeyConnectionManager implements ValkeyConnectionManager {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultValkeyConnectionManager.class);

  private final Map<String, ValkeyDataSourceProperties> dataSources;
  private final ValkeyConnectionFactory connectionFactory;
  private final ValkeyClusterConnectionFactory clusterConnectionFactory;
  private final Map<String, ValkeyConnection> connections = new ConcurrentHashMap<>();

  public DefaultValkeyConnectionManager(Map<String, ValkeyDataSourceProperties> dataSources,
      ValkeyConnectionFactory connectionFactory,
      ValkeyClusterConnectionFactory clusterConnectionFactory) {
    this.dataSources = Map.copyOf(dataSources);
    this.connectionFactory = connectionFactory;
    this.clusterConnectionFactory = clusterConnectionFactory;
  }

  @Override
  public ValkeyConnection getConnection(String dataSourceName) {
    ValkeyDataSourceProperties properties = dataSources.get(dataSourceName);
    if (properties == null) {
      throw new ValkeyAccessException("Unknown valkey data source [" + dataSourceName
          + "], configured data sources: " + dataSources.keySet());
    }
    return connections.computeIfAbsent(dataSourceName, name -> createConnection(name, properties));
  }

  private ValkeyConnection createConnection(String name, ValkeyDataSourceProperties properties) {
    LOGGER.debug("Creating valkey connection for data source [{}] in {} mode", name,
        properties.getMode());
    if (properties.getMode() == ValkeyMode.CLUSTER) {
      return clusterConnectionFactory.createConnection(name, properties);
    }
    return connectionFactory.createConnection(name, properties);
  }

  @Override
  public Set<String> getDataSourceNames() {
    return Set.copyOf(dataSources.keySet());
  }

  @Override
  public void close() {
    connections.values().forEach(connection -> {
      try {
        connection.close();
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to close valkey connection for data source [{}]: {}",
            connection.getDataSourceName(), e.getMessage());
      }
    });
    connections.clear();
  }
}
