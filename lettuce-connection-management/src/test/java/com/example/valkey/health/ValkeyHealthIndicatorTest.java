package com.example.valkey.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.valkey.ValkeyAccessException;
import com.example.valkey.ValkeyConnection;
import com.example.valkey.ValkeyConnectionManager;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class ValkeyHealthIndicatorTest {

  private final ValkeyConnectionManager connectionManager = mock(ValkeyConnectionManager.class);

  @Test
  void shouldReportUpWhenAllDataSourcesRespondToPing() {
    ValkeyConnection connection = mock(ValkeyConnection.class);
    when(connectionManager.getDataSourceNames()).thenReturn(Set.of("ds-1"));
    when(connectionManager.getConnection("ds-1")).thenReturn(connection);

    Health health = new ValkeyHealthIndicator(connectionManager).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("ds-1", "UP");
  }

  @Test
  void shouldReportDownWhenAnyDataSourceFails() {
    ValkeyConnection healthy = mock(ValkeyConnection.class);
    ValkeyConnection broken = mock(ValkeyConnection.class);
    when(connectionManager.getDataSourceNames()).thenReturn(Set.of("ds-ok", "ds-broken"));
    when(connectionManager.getConnection("ds-ok")).thenReturn(healthy);
    when(connectionManager.getConnection("ds-broken")).thenReturn(broken);
    doThrow(new ValkeyAccessException("connection refused")).when(broken).ping();

    Health health = new ValkeyHealthIndicator(connectionManager).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("ds-ok", "UP");
    assertThat(health.getDetails().get("ds-broken").toString()).contains("DOWN")
        .contains("connection refused");
  }

  @Test
  void shouldReportDownWhenConnectionCreationFails() {
    when(connectionManager.getDataSourceNames()).thenReturn(Set.of("ds-broken"));
    when(connectionManager.getConnection("ds-broken"))
        .thenThrow(new ValkeyAccessException("unknown data source"));

    Health health = new ValkeyHealthIndicator(connectionManager).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("ds-broken").toString()).contains("unknown data source");
  }
}
