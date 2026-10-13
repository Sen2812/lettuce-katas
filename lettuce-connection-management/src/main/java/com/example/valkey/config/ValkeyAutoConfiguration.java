package com.example.valkey.config;

import com.example.valkey.ValkeyClusterConnectionFactory;
import com.example.valkey.ValkeyConnectionFactory;
import com.example.valkey.ValkeyConnectionManager;
import com.example.valkey.factory.DefaultValkeyClusterConnectionFactory;
import com.example.valkey.factory.DefaultValkeyConnectionFactory;
import com.example.valkey.manager.DefaultValkeyConnectionManager;
import com.example.valkey.serializer.JacksonValkeySerializer;
import com.example.valkey.serializer.ValkeySerializer;
import com.example.valkey.support.ValkeyCommandExecutor;
import com.example.valkey.support.ValkeyRetryOptions;
import com.example.valkey.template.ValkeyTemplateFactory;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * valkey 框架的 Spring 装配。
 *
 * <p>
 * 注册共享 {@link ClientResources}（进程内单例，供所有数据源的客户端复用）、Jackson 序列化器、 重试执行器、两种连接工厂、连接管理器与模板工厂；所有 Bean
 * 均允许应用侧以自定义实现覆盖。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ValkeyProperties.class)
public class ValkeyAutoConfiguration {

  /** 进程内共享的 Lettuce 资源（事件循环线程池等），随上下文关闭而释放。 */
  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public ClientResources valkeyClientResources() {
    return DefaultClientResources.create();
  }

  @Bean
  @ConditionalOnMissingBean
  public ValkeySerializer valkeySerializer() {
    return JacksonValkeySerializer.create();
  }

  @Bean
  @ConditionalOnMissingBean
  public ValkeyCommandExecutor valkeyCommandExecutor(ValkeyProperties properties) {
    ValkeyProperties.Retry retry = properties.getRetry();
    return new ValkeyCommandExecutor(
        new ValkeyRetryOptions(retry.getMaxAttempts(), retry.getBackoff()));
  }

  @Bean
  @ConditionalOnMissingBean
  public ValkeyConnectionFactory valkeyConnectionFactory(ClientResources valkeyClientResources,
      ValkeySerializer valkeySerializer, ValkeyCommandExecutor valkeyCommandExecutor) {
    return new DefaultValkeyConnectionFactory(valkeyClientResources, valkeySerializer,
        valkeyCommandExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  public ValkeyClusterConnectionFactory valkeyClusterConnectionFactory(
      ClientResources valkeyClientResources, ValkeySerializer valkeySerializer,
      ValkeyCommandExecutor valkeyCommandExecutor) {
    return new DefaultValkeyClusterConnectionFactory(valkeyClientResources, valkeySerializer,
        valkeyCommandExecutor);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public ValkeyConnectionManager valkeyConnectionManager(ValkeyProperties properties,
      ValkeyConnectionFactory valkeyConnectionFactory,
      ValkeyClusterConnectionFactory valkeyClusterConnectionFactory) {
    return new DefaultValkeyConnectionManager(properties.getDatasources(), valkeyConnectionFactory,
        valkeyClusterConnectionFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public ValkeyTemplateFactory valkeyTemplateFactory(
      ValkeyConnectionManager valkeyConnectionManager) {
    return new ValkeyTemplateFactory(valkeyConnectionManager);
  }
}
