package com.example.valkey.config;

import java.time.Duration;

/** 单个 valkey 数据源（resource）的配置项。 */
public class ValkeyDataSourceProperties {

  /** 部署模式，默认单机。 */
  private ValkeyMode mode = ValkeyMode.STANDALONE;

  /** 主机地址；集群模式下作为拓扑发现的种子节点。 */
  private String host = "localhost";

  /** 端口；集群模式下作为种子节点端口。 */
  private int port = 6379;

  /** ACL 用户名；未配置时使用默认用户。 */
  private String username;

  /** 认证密码；未配置时以无认证方式连接。 */
  private String password;

  /** 连接超时与命令超时。 */
  private Duration timeout = Duration.ofSeconds(10);

  /** 集群模式下的最大重定向次数（达到上限后命令失败并触发监视日志）。 */
  private int maxRedirects = 5;

  /** 集群模式下的周期性拓扑刷新间隔（自动更新拓扑）。 */
  private Duration topologyRefreshPeriod = Duration.ofSeconds(30);

  public ValkeyMode getMode() {
    return mode;
  }

  public void setMode(ValkeyMode mode) {
    this.mode = mode;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public int getMaxRedirects() {
    return maxRedirects;
  }

  public void setMaxRedirects(int maxRedirects) {
    this.maxRedirects = maxRedirects;
  }

  public Duration getTopologyRefreshPeriod() {
    return topologyRefreshPeriod;
  }

  public void setTopologyRefreshPeriod(Duration topologyRefreshPeriod) {
    this.topologyRefreshPeriod = topologyRefreshPeriod;
  }

  @Override
  public String toString() {
    return "ValkeyDataSourceProperties{mode=" + mode + ", host='" + host + "', port=" + port
        + ", timeout=" + timeout + ", maxRedirects=" + maxRedirects + ", topologyRefreshPeriod="
        + topologyRefreshPeriod + "}";
  }
}
