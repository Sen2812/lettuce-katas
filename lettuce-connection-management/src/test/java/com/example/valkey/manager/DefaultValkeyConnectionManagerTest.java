package com.example.valkey.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyClusterConnection;
import com.example.valkey.ValkeyClusterConnectionFactory;
import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionFactory;
import com.example.valkey.config.ValkeyDataSourceProperties;
import com.example.valkey.config.ValkeyMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultValkeyConnectionManagerTest {

  private final ValkeyConnectionFactory connectionFactory = mock(ValkeyConnectionFactory.class);
  private final ValkeyClusterConnectionFactory clusterConnectionFactory =
      mock(ValkeyClusterConnectionFactory.class);
  private final Map<String, ValkeyDataSourceProperties> dataSources = new LinkedHashMap<>();
  private final ValkeyConnection standaloneConnection = mock(ValkeyConnection.class);
  private final ValkeyClusterConnection clusterConnection = mock(ValkeyClusterConnection.class);

  private DefaultValkeyConnectionManager manager;

  @BeforeEach
  void setUp() {
    dataSources.put("standalone-ds", dataSource(ValkeyMode.STANDALONE));
    dataSources.put("cluster-ds", dataSource(ValkeyMode.CLUSTER));
    manager = new DefaultValkeyConnectionManager(dataSources, connectionFactory,
        clusterConnectionFactory);
  }

  private static ValkeyDataSourceProperties dataSource(ValkeyMode mode) {
    ValkeyDataSourceProperties properties = new ValkeyDataSourceProperties();
    properties.setMode(mode);
    return properties;
  }

  @Test
  void shouldReuseTheSameConnectionInstancePerDataSourceName() {
    when(connectionFactory.createConnection(eq("standalone-ds"), any()))
        .thenReturn(standaloneConnection);

    ValkeyConnection first = manager.getConnection("standalone-ds");
    ValkeyConnection second = manager.getConnection("standalone-ds");

    assertThat(first).isSameAs(second).isSameAs(standaloneConnection);
    verify(connectionFactory, times(1)).createConnection(eq("standalone-ds"), any());
  }

  @Test
  void shouldCreateDifferentConnectionsForDifferentDataSources() {
    when(connectionFactory.createConnection(eq("standalone-ds"), any()))
        .thenReturn(standaloneConnection);
    when(clusterConnectionFactory.createConnection(eq("cluster-ds"), any()))
        .thenReturn(clusterConnection);

    assertThat(manager.getConnection("standalone-ds")).isSameAs(standaloneConnection);
    assertThat(manager.getConnection("cluster-ds")).isSameAs(clusterConnection);
    verify(connectionFactory, times(1)).createConnection(eq("standalone-ds"), any());
    verify(clusterConnectionFactory, times(1)).createConnection(eq("cluster-ds"), any());
  }

  @Test
  void shouldRouteClusterModeToClusterFactory() {
    when(clusterConnectionFactory.createConnection(eq("cluster-ds"), any()))
        .thenReturn(clusterConnection);

    manager.getConnection("cluster-ds");

    verify(clusterConnectionFactory, times(1)).createConnection(eq("cluster-ds"), any());
    verify(connectionFactory, times(0)).createConnection(eq("cluster-ds"), any());
  }

  @Test
  void shouldRejectUnknownDataSourceName() {
    assertThatThrownBy(() -> manager.getConnection("unknown"))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("Unknown valkey data source [unknown]");
  }

  @Test
  void shouldNotCacheFailedCreationAndRetryOnNextCall() {
    when(connectionFactory.createConnection(eq("standalone-ds"), any()))
        .thenThrow(new ValkeyAccessException("creation failed"))
        .thenReturn(standaloneConnection);

    assertThatThrownBy(() -> manager.getConnection("standalone-ds"))
        .isInstanceOf(ValkeyAccessException.class)
        .hasMessageContaining("creation failed");

    assertThat(manager.getConnection("standalone-ds")).isSameAs(standaloneConnection);
    verify(connectionFactory, times(2)).createConnection(eq("standalone-ds"), any());
  }

  @Test
  void shouldExposeConfiguredDataSourceNames() {
    assertThat(manager.getDataSourceNames()).containsExactlyInAnyOrder("standalone-ds",
        "cluster-ds");
  }

  @Test
  void closeShouldCloseEveryCreatedConnection() {
    when(connectionFactory.createConnection(eq("standalone-ds"), any()))
        .thenReturn(standaloneConnection);
    when(clusterConnectionFactory.createConnection(eq("cluster-ds"), any()))
        .thenReturn(clusterConnection);

    manager.getConnection("standalone-ds");
    manager.getConnection("cluster-ds");
    manager.close();

    verify(standaloneConnection, times(1)).close();
    verify(clusterConnection, times(1)).close();
  }
}
