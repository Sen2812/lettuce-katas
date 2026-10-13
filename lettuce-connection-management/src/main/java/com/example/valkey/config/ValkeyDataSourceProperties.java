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

  /** 连接超时与命令超时（默认值）；可用 {@code connect-timeout}、{@code command-timeout} 分别覆盖。 */
  private Duration timeout = Duration.ofSeconds(10);

  /** 连接建立超时；未配置时回退到 {@link #timeout}。 */
  private Duration connectTimeout;

  /** 命令响应超时；未配置时回退到 {@link #timeout}。 */
  private Duration commandTimeout;

  /** 是否启用 TLS。 */
  private boolean ssl;

  /** 自定义信任证书（PEM 文件路径），用于私有 CA / 自签证书；未配置时使用系统信任库。 */
  private String sslTrustCertificate;

  /** 客户端名称；未配置时默认使用数据源名称，便于服务端 CLIENT LIST 定位连接来源。 */
  private String clientName;

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

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getCommandTimeout() {
    return commandTimeout;
  }

  public void setCommandTimeout(Duration commandTimeout) {
    this.commandTimeout = commandTimeout;
  }

  public boolean isSsl() {
    return ssl;
  }

  public void setSsl(boolean ssl) {
    this.ssl = ssl;
  }

  public String getSslTrustCertificate() {
    return sslTrustCertificate;
  }

  public void setSslTrustCertificate(String sslTrustCertificate) {
    this.sslTrustCertificate = sslTrustCertificate;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(String clientName) {
    this.clientName = clientName;
  }

  /** 生效的连接超时：优先 {@code connect-timeout}，未配置时回退到 {@link #timeout}。 */
  public Duration resolveConnectTimeout() {
    return connectTimeout != null ? connectTimeout : timeout;
  }

  /** 生效的命令超时：优先 {@code command-timeout}，未配置时回退到 {@link #timeout}。 */
  public Duration resolveCommandTimeout() {
    return commandTimeout != null ? commandTimeout : timeout;
  }

  /** 生效的客户端名称：优先 {@code client-name}，未配置时使用数据源名称。 */
  public String resolveClientName(String dataSourceName) {
    return clientName != null && !clientName.isBlank() ? clientName : dataSourceName;
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
        + ", timeout=" + timeout + ", connectTimeout=" + connectTimeout + ", commandTimeout="
        + commandTimeout + ", ssl=" + ssl + ", sslTrustCertificate=" + sslTrustCertificate
        + ", clientName=" + clientName + ", maxRedirects=" + maxRedirects
        + ", topologyRefreshPeriod=" + topologyRefreshPeriod + "}";
  }
}
