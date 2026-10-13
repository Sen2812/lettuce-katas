package com.example;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.support.ConnectionPoolSupport;

public class RedisConnectionTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(RedisConnectionTest.class);

  private static ClientResources RESOURCES = DefaultClientResources.create();
  private static RedisClient CLIENT;
  private static StatefulRedisConnection<String, String> CONNECTION;
  private static RedisCommands<String, String> COMMANDS;

  private static String RAW_LUA = "local key = KEYS[1]\n" + "local value = ARGV[1]\n"
      + "local timeout = ARGV[2]\n" + "redis.call('SETEX', key, tonumber(timeout), value)\n"
      + "local result = redis.call('GET', key)\n" + "return result;";
  private static AtomicReference<String> LUA_SHA = new AtomicReference<>();

  @BeforeAll
  public static void beforeClass() {
    RedisURI redisUri = RedisURI.builder()
        .withHost("localhost")
        .withPort(6379)
        .withTimeout(Duration.of(10, ChronoUnit.SECONDS))
        .withAuthentication("default", "change_me")
        .build();
    CLIENT = RedisClient.create(RESOURCES, redisUri);
    CONNECTION = CLIENT.connect();

    COMMANDS = CONNECTION.sync();
  }

  @AfterAll
  public static void afterClass() throws Exception {
    CONNECTION.close();
    CLIENT.shutdown();
    RESOURCES.shutdown();
  }

  @Test
  public void testSyncPing() throws Exception {
    String pong = COMMANDS.ping();
    Assertions.assertThat(pong).isEqualToIgnoringCase("PONG");
  }

  @Test
  public void testSyncSetAndGet() throws Exception {
    SetArgs setArgs = SetArgs.Builder.nx().ex(5);
    COMMANDS.set("name", "throwable", setArgs);
    String value = COMMANDS.get("name");
    LOGGER.info("Get value: {}", value);
  }

  @Test
  public void testLua() throws Exception {
    LUA_SHA.compareAndSet(null, COMMANDS.scriptLoad(RAW_LUA));
    String[] keys = new String[] {"name"};
    String[] args = new String[] {"throwable", "5000"};
    String result = COMMANDS.evalsha(LUA_SHA.get(), ScriptOutputType.VALUE, keys, args);
    LOGGER.info("Get value:{}", result);
  }

  @Test
  public void testUseConnectionPool() throws Exception {
    RedisURI redisUri = RedisURI.builder()
        .withHost("localhost")
        .withPort(6379)
        .withTimeout(Duration.of(10, ChronoUnit.SECONDS))
        .withAuthentication("default", "change_me")
        .build();

    RedisClient redisClient = RedisClient.create(redisUri);

    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
    GenericObjectPool<StatefulRedisConnection<String, String>> pool =
        ConnectionPoolSupport.createGenericObjectPool(redisClient::connect, poolConfig);

    try (StatefulRedisConnection<String, String> connection = pool.borrowObject()) {
      RedisCommands<String, String> command = connection.sync();
      SetArgs setArgs = SetArgs.Builder.nx().ex(5);
      command.set("name", "throwable", setArgs);
      String n = command.get("name");
      LOGGER.info("Get value:{}", n);
    }

    pool.close();
    redisClient.shutdown();
  }

}
