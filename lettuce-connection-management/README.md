# lettuce-connection-management

基于 Lettuce 7.8 的 valkey 连接封装框架。提供多数据源连接管理、少量固定命令（SET / GET / DEL）、
键值统一 String 传递、Jackson 序列化、统一异常转换与有限重试，并对集群模式的拓扑自动更新、
重定向行为与超限监视做了完整封装与演练验证。

- 运行环境：Java 25 / Spring Boot 4.1.1 / Lettuce 7.8.0.RELEASE / Jackson 3.1.5
- 模块路径：`lettuce-connection-management`

---

## 1. 能力总览

| 能力 | 说明 |
| --- | --- |
| 多数据源 | 配置中以 `valkey.datasources.<name>` 定义任意个数据源，支持 `standalone` 与 `cluster` 两种模式 |
| 有限命令集 | 仅封装 `SET`、`GET`、`DEL` 三个命令，同步与响应式两套操作集合 |
| 连接复用 | 同一数据源名称在管理器生命周期内始终复用同一个连接实例 |
| 序列化 | 键与值统一以 String 传递，固定由 Jackson 3 序列化为 UTF-8 字符串 |
| 异常模型 | 操作异常、命令异常、连接创建异常统一转换为 `ValkeyAccessException`，保留原始异常 |
| 有限重试 | 仅对“命令未发送到服务端”的连接类异常重试（默认 3 次、200ms 退避），次数可配置 |
| 集群自动更新 | 周期性拓扑刷新 + 全部自适应刷新触发器（MOVED / ASK / 持续重连 / 未覆盖槽位 / 未知节点） |
| 集群监视 | 拓扑刷新与重连由日志监视；重定向达到上限时输出 `[ClusterMonitor]` 警告日志 |

---

## 2. 组件职责与强约束

### 2.1 连接层

#### `ValkeyConnection`

职责：单个 valkey 数据源连接的门面，向外暴露操作集合，并管理连接生命周期。

强约束：

- `getOperations()` 返回的操作集合在任何时候都绑定同一个数据源，不允许跨数据源复用。
- `close()` 同时关闭底层 `StatefulRedisConnection` 与该数据源独占的 `RedisClient`；共享的
  `ClientResources` 由框架统一释放，不在此处关闭。
- `isOpen()` 反映底层连接的实时状态；连接关闭后调用操作集合会抛出 `ValkeyAccessException`。

#### `ValkeyClusterConnection`（继承 `ValkeyConnection`）

职责：集群模式连接门面，额外暴露集群拓扑诊断信息。

强约束：

- `getNodeIds()` 返回的是客户端“最近一次拓扑刷新后已知”的节点集合；节点故障转移后该集合会随
  自动刷新而变化，不能作为强一致视图使用。
- 转交的外部类型上不要依赖此接口的同步语义；拓扑刷新由 Lettuce 在后台线程完成。

#### `ValkeyConnectionFactory` / `ValkeyClusterConnectionFactory`

职责：根据数据源配置创建连接。

强约束：

- 创建过程中出现的任何异常（连接被拒、认证失败、重试耗尽）都必须转换为
  `ValkeyAccessException` 并保留原始异常。
- 工厂只负责“创建一个连接”；连接的复用与缓存由管理器负责，工厂实现不得自行缓存。
- 集群工厂必须启用拓扑自动刷新与自适应刷新触发器；创建失败时必须释放已创建的客户端资源
  （先 `shutdown()` 再抛出）。

#### `DefaultValkeyConnectionFactory` / `DefaultValkeyClusterConnectionFactory`

职责：Lettuce 客户端的具体装配实现（单机 `RedisClient` / 集群 `RedisClusterClient`）。

强约束：

- 所有连接使用 `StringCodec.UTF8` 编解码，保证写入链路上的字节与 UTF-8 一致。
- 每次创建尝试都在 `ValkeyCommandExecutor` 内执行，因此连接类失败会自动重试有限次。
- 单机模式必须设置 `autoReconnect(true)`、连接超时与命令超时；集群模式还必须设置
  `maxRedirects`（默认 5）与拓扑刷新策略。
- 共享 `ClientResources` 由构造注入，同一个进程内所有数据源共用同一份事件循环资源。

#### `ValkeyConnectionManager` / `DefaultValkeyConnectionManager`

职责：按数据源名称提供（并复用）连接。

强约束：

- **同一名称返回同一实例**：`getConnection(name)` 在管理器生命周期内始终返回同一个
  `ValkeyConnection`（懒创建 + 缓存）。
- 连接创建失败时异常直接抛出且**不写入缓存**，下次调用会重新尝试创建。
- 未配置的数据源名称必须抛出 `ValkeyAccessException`（消息包含名称与已配置集合）。
- 按 `mode` 分派工厂：`STANDALONE` 走普通工厂，`CLUSTER` 走集群工厂。
- `close()` 关闭所有已创建连接；单个连接关闭失败只记录警告，不中断其余连接的关闭。

### 2.2 操作层

#### `ValkeyOperations`

职责：一个数据源上操作集合的聚合入口，由连接门面暴露。

强约束：

- 四个子操作集合（同步/响应式 × 值/key）共享同一序列化器、执行器与数据源名称。
- 操作集合本身无状态，可在多线程间共享（底层 Lettuce 连接线程安全）。

#### `ValueOperations` / `KeyOperations`

职责：同步 `SET` / `GET` / `DEL` 命令的封装。

强约束：

- 键与值先经序列化器转换再交给 Lettuce；**序列化失败抛出 `ValkeyAccessException`**。
- `get(key)` 在 key 不存在时返回 `null`；`delete(key)` 只在确实删除存在的 key 时返回 `true`。
- 所有命令执行都经过 `ValkeyCommandExecutor`，异常转换与有限重试对调用方透明。

#### `ReactiveValueOperations` / `ReactiveKeyOperations`

职责：与同步版本等价的响应式（Reactor `Mono`）封装。

强约束：

- 失败信号同样被转换为 `ValkeyAccessException`（以 `onError` 发出），cause 保留原始异常
  （重试耗尽时保留最后一次失败原因，与同步路径一致）。
- `get(key)` 在 key 不存在时发出空 `Mono`；`set(key, value)` 完成时发出完成信号。
- 惰性执行：命令在订阅时才真正执行，重复订阅会重复执行命令。

### 2.3 模板层

#### `ValkeyStringTemplate` / `ReactiveValkeyStringTemplate`

职责：面向单个数据源的操作入口，向调用方暴露 `opsForValue()` 与 `opsForKey()`。

强约束：

- 每次取操作集合都通过连接管理器获取同一数据源的**复用连接**，再经
  `ValkeyConnection#getOperations()` 暴露对应集合；模板自身不做缓存、不持有连接引用。
- 模板与数据源名称绑定，不允许一个模板访问另一个数据源。

#### `ValkeyTemplateFactory`

职责：按数据源名称创建同步/响应式模板。

强约束：

- 只是模板的构造入口；模板与数据源名称一一绑定，调用方需自行保存与选择。

### 2.4 支撑组件

#### `ValkeySerializer` / `JacksonValkeySerializer`

职责：键值的序列化契约与 Jackson 3 固定实现。

强约束：

- 序列化器契约固定为 String → String；`null` 双向原样透传。
- Jackson 实现把值编码为 JSON 文本（字符串带引号，属于合法 UTF-8 字符串），反序列化严格按
  JSON 解析；解析失败抛出 `ValkeyAccessException`。
- **同一 key 的读写必须使用同一序列化器**；用其他方式（如裸 `redis-cli` 明文）写入的数据
  可能无法被本框架反序列化。
- 序列化器是无状态、线程安全的，可全局共享。

#### `ValkeyAccessException`

职责：框架对外唯一的访问异常类型。

强约束：

- 所有对外异常都必须携带原始异常（`getCause()`），不得丢失根因。
- 不继承检查异常，避免调用方被迫捕获。

#### `ValkeyCommandExecutor` / `ValkeyRetryOptions`

职责：统一执行命令、有限重试、异常转换与集群重定向监视。

强约束：

- **只有连接类异常（`RedisConnectionException`，即命令大概率未送达服务端）才重试**；
  服务端执行错误、命令超时、重定向超限等一律不重试，避免对非幂等语义的命令重复执行。
- 重试次数与退避由 `ValkeyRetryOptions` 限定（默认 3 次尝试、200ms 固定退避），重试耗尽后
  抛出携带最后一次原始异常的 `ValkeyAccessException`。
- 响应式路径使用 Reactor `retryWhen` 实现同等语义，重试耗尽保留最后一次失败原因。
- 异常链中出现 `MOVED` / `ASK` 消息（重定向达到上限）时，输出 `[ClusterMonitor]` 警告日志。
- 中断重试（`InterruptedException`）时必须恢复线程中断标志。

### 2.5 配置与装配

#### `ValkeyDataSourceProperties`

职责：单个数据源（resource）的配置项载体。

强约束：

- `mode` 决定工厂分派；`host` / `port` 在集群模式下表示拓扑发现的**种子节点**，其余节点由
  Lettuce 自动发现。
- `password` / `username` 可选；未配置时以无认证方式连接，配置了密码而未配置用户名时使用
  默认用户。
- `max-redirects` 与 `topology-refresh-period` 仅集群模式生效。

#### `ValkeyProperties`

职责：`valkey` 前缀的配置根对象。

强约束：

- `datasources` 的 key 即数据源名称，不可为空、不可重复。
- `retry.max-attempts` 至少为 1；`retry.backoff` 为固定退避时长。

#### `ValkeyAutoConfiguration`

职责：注册框架全部组件；所有 Bean 均带 `@ConditionalOnMissingBean`，应用侧可覆盖。

强约束：

- `ClientResources` 为进程内单例，销毁时调用 `shutdown()`。
- `ValkeyConnectionManager` 销毁时调用 `close()`，统一释放所有数据源连接。
- 序列化器固定注册 Jackson 实现；不提供第二个内建实现。

---

## 3. 公共 API 方法签名表

### 3.1 连接层

**`ValkeyConnection`**（`com.example.valkey.ValkeyConnection`）

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyOperations getOperations()` | 暴露该连接上的操作集合（值操作与 key 操作） |
| `boolean isOpen()` | 底层连接当前是否打开 |
| `String getDataSourceName()` | 该连接绑定的数据源名称 |
| `void close()` | 关闭连接并释放该连接独占的客户端资源 |

**`ValkeyClusterConnection extends ValkeyConnection`**

| 方法签名 | 功能说明 |
| --- | --- |
| `Set<String> getNodeIds()` | 当前客户端已知的集群节点 ID 集合（随拓扑刷新变化） |

**`ValkeyConnectionFactory`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyConnection createConnection(String, ValkeyDataSourceProperties)` | 创建指定数据源的连接（失败重试后转换异常） |

**`ValkeyClusterConnectionFactory extends ValkeyConnectionFactory`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyClusterConnection createConnection(String, ValkeyDataSourceProperties)` | 创建集群连接（启用拓扑自动刷新与重定向上限） |

**`ValkeyConnectionManager extends AutoCloseable`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyConnection getConnection(String)` | 按数据源名称获取复用连接；未配置或创建失败抛出 `ValkeyAccessException` |
| `Set<String> getDataSourceNames()` | 已配置的数据源名称集合 |
| `void close()` | 关闭所有已创建的连接 |

**`DefaultValkeyConnectionManager`**

| 方法签名 | 功能说明 |
| --- | --- |
| `DefaultValkeyConnectionManager(Map<String, ValkeyDataSourceProperties>, ValkeyConnectionFactory, ValkeyClusterConnectionFactory)` | 使用数据源配置与两种模式工厂构造管理器 |

### 3.2 操作层

**`ValkeyOperations`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValueOperations valueOperations()` | 同步 String 值操作集合 |
| `KeyOperations keyOperations()` | 同步 key 级操作集合 |
| `ReactiveValueOperations reactiveValueOperations()` | 响应式 String 值操作集合 |
| `ReactiveKeyOperations reactiveKeyOperations()` | 响应式 key 级操作集合 |

**`ValueOperations` / `ReactiveValueOperations`**

| 方法签名 | 功能说明 |
| --- | --- |
| `void set(String key, String value)` | 设置 key 的值（同步） |
| `String get(String key)` | 读取 key 的值；不存在返回 `null`（同步） |
| `Mono<Void> set(String key, String value)` | 设置 key 的值（响应式，完成信号） |
| `Mono<String> get(String key)` | 读取 key 的值（响应式，空 Mono 表示不存在） |

**`KeyOperations` / `ReactiveKeyOperations`**

| 方法签名 | 功能说明 |
| --- | --- |
| `boolean delete(String key)` | 删除 key；是否删除了存在的 key（同步） |
| `Mono<Boolean> delete(String key)` | 删除 key（响应式） |

### 3.3 模板层

**`ValkeyStringTemplate` / `ReactiveValkeyStringTemplate`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValueOperations opsForValue()` | 获取 String 值操作集合（经连接的 `getOperations()`） |
| `KeyOperations opsForKey()` | 获取 key 级操作集合 |
| `ReactiveValueOperations opsForValue()` | 响应式 String 值操作集合 |
| `ReactiveKeyOperations opsForKey()` | 响应式 key 级操作集合 |

**`ValkeyTemplateFactory`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyStringTemplate getTemplate(String dataSourceName)` | 创建指定数据源的同步模板 |
| `ReactiveValkeyStringTemplate getReactiveTemplate(String dataSourceName)` | 创建指定数据源的响应式模板 |

### 3.4 支撑组件

**`ValkeySerializer`**

| 方法签名 | 功能说明 |
| --- | --- |
| `String serialize(String value)` | 序列化为 UTF-8 字符串；`null` 透传 |
| `String deserialize(String value)` | 从字符串还原；失败抛出 `ValkeyAccessException` |

**`JacksonValkeySerializer`**

| 方法签名 | 功能说明 |
| --- | --- |
| `JacksonValkeySerializer(ObjectMapper)` | 使用给定 Jackson 3 `ObjectMapper` 构造 |
| `static JacksonValkeySerializer create()` | 使用默认 `JsonMapper` 构造 |
| `String serialize(String)` / `String deserialize(String)` | 见 `ValkeySerializer` |

**`ValkeyCommandExecutor`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyCommandExecutor(ValkeyRetryOptions)` | 使用重试策略构造 |
| `void executeVoid(String dataSourceName, Runnable action)` | 执行无返回值动作（重试 + 转换） |
| `<T> T execute(String dataSourceName, Supplier<T> action)` | 执行有返回值动作（重试 + 转换） |
| `<T> Mono<T> executeReactive(String dataSourceName, Supplier<Mono<T>> action)` | 响应式：惰性执行 + 重试 + 转换 |

**`ValkeyRetryOptions`**

| 方法签名 | 功能说明 |
| --- | --- |
| `ValkeyRetryOptions(int maxAttempts, Duration backoff)` | 构造；`maxAttempts < 1` 抛出 `IllegalArgumentException` |
| `static ValkeyRetryOptions DEFAULT` | 默认策略（3 次、200ms） |
| `int getMaxAttempts()` / `Duration getBackoff()` | 读取重试参数 |

### 3.5 配置与装配

**`ValkeyProperties`**（前缀 `valkey`）

| 方法签名 | 功能说明 |
| --- | --- |
| `Map<String, ValkeyDataSourceProperties> getDatasources()` | 多数据源配置 |
| `ValkeyProperties.Retry getRetry()` | 重试配置（`max-attempts`、`backoff`） |

**`ValkeyDataSourceProperties`**

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mode` | `ValkeyMode` | `STANDALONE` | `standalone` / `cluster` |
| `host` | `String` | `localhost` | 主机地址；集群模式为种子节点 |
| `port` | `int` | `6379` | 端口；集群模式为种子端口 |
| `username` | `String` | 空 | ACL 用户名 |
| `password` | `String` | 空 | 认证密码 |
| `timeout` | `Duration` | `10s` | 连接超时与命令超时 |
| `max-redirects` | `int` | `5` | 集群最大重定向次数 |
| `topology-refresh-period` | `Duration` | `30s` | 集群周期性拓扑刷新间隔 |

**`ValkeyAutoConfiguration`** 注册的 Bean

| Bean 名称 | 类型 | 说明 |
| --- | --- | --- |
| `valkeyClientResources` | `ClientResources` | 进程内共享的 Lettuce 资源（destroy：shutdown） |
| `valkeySerializer` | `ValkeySerializer` | Jackson 3 实现 |
| `valkeyCommandExecutor` | `ValkeyCommandExecutor` | 重试执行器（读取 `valkey.retry.*`） |
| `valkeyConnectionFactory` | `ValkeyConnectionFactory` | 单机连接工厂 |
| `valkeyClusterConnectionFactory` | `ValkeyClusterConnectionFactory` | 集群连接工厂 |
| `valkeyConnectionManager` | `ValkeyConnectionManager` | 连接管理器（destroy：close） |
| `valkeyTemplateFactory` | `ValkeyTemplateFactory` | 模板工厂 |

---

## 4. 配置参考

`application.yaml` 示例：

```yaml
valkey:
    # 命令重试策略：仅对连接类异常（命令未送达服务端）生效
    retry:
        max-attempts: 3
        backoff: 200ms
    datasources:
        # 单机模式数据源（resource）
        datasource-01:
            mode: standalone
            host: localhost
            port: 6379
            password: change_me
            timeout: 10s
        # 集群模式数据源；host/port 为拓扑发现的种子节点，其余节点由 Lettuce 自动发现
        datasource-02:
            mode: cluster
            host: localhost
            port: 7001
            password: change_me
            timeout: 10s
            max-redirects: 5
            topology-refresh-period: 30s
```

使用示例：

```java
// 通过 Spring 装配注入
ValkeyTemplateFactory templateFactory = context.getBean(ValkeyTemplateFactory.class);
ValkeyStringTemplate template = templateFactory.getTemplate("datasource-01");

template.opsForValue().set("name", "throwable");
String value = template.opsForValue().get("name");     // "throwable"
boolean deleted = template.opsForKey().delete("name"); // true

// 响应式
ReactiveValkeyStringTemplate reactive = templateFactory.getReactiveTemplate("datasource-02");
reactive.opsForValue().set("k", "v").block();
reactive.opsForValue().get("k").block();               // "v"
```

---

## 5. 异常与重试模型

异常来源与处理行为：

| 异常来源 | 判定 | 行为 |
| --- | --- | --- |
| 连接创建失败（拒绝连接、认证失败等） | `RedisConnectionException` | 有限重试；耗尽后转换 `ValkeyAccessException`（cause 为最后一次原始异常） |
| 命令执行时连接中断、连接未建立 | `RedisConnectionException` | 同上 |
| 服务端执行错误（如 WRONGTYPE） | `RedisCommandExecutionException` | 不重试，直接转换 |
| 命令超时（响应未返回） | `RedisCommandTimeoutException` | 不重试（命令可能已送达），直接转换 |
| 集群重定向达到上限 | `RedisException`，消息为 `MOVED ...` / `ASK ...` 或固定文案 | 不重试；输出 `[ClusterMonitor]` 警告日志后转换 |
| 序列化 / 反序列化失败 | Jackson 异常 | 转换 `ValkeyAccessException`（cause 保留 Jackson 异常） |
| 连接已关闭等状态错误 | 其他运行时异常 | 转换 `ValkeyAccessException` |

重试规则：

- **重试**：仅连接类异常（命令在发送前失败，重试安全）。
- **不重试**：服务端错误、超时、重定向超限、序列化错误。
- 当前封装的 `SET` / `GET` / `DEL` 在语义上均幂等；若未来扩展到非幂等命令，需重新评估重试策略。
- 响应式路径与同步路径的语义一致（重试次数、退避、最终异常）。

---

## 6. 集群行为确认

### 6.1 重定向是 Lettuce 自动处理的吗？需要额外实现吗？

**结论：MOVED / ASK 重定向、拓扑变化跟随、断线重连全部由 Lettuce 自动完成，应用层不需要实现
任何重定向逻辑。**

| 场景 | Lettuce 的内建行为 | 框架采取的动作 |
| --- | --- | --- |
| 收到 MOVED | 解析新槽位归属，更新本地槽位映射，自动向新节点重发命令；同时触发 `MOVED_REDIRECT` 自适应拓扑刷新 | 无（仅配置 `max-redirects`） |
| 收到 ASK | 向目标节点发送 `ASKING` 后重发命令；触发 `ASK_REDIRECT` 刷新 | 无 |
| 主节点下线 | `ConnectionWatchdog` 自动重连；持续失败触发 `PERSISTENT_RECONNECTS` 自适应刷新；副本提升后拓扑刷新将流量路由到新主 | 无；命令层的连接异常会做有限重试，跨过重连窗口 |
| 拓扑节点更新 | 周期性刷新（默认 30s，可配置）+ 自适应触发器（MOVED / ASK / 持续重连 / 未覆盖槽位 / 未知节点） | 通过 `ClusterTopologyRefreshOptions` 启用全部触发器 |
| 重定向超过上限 | 抛出携带 `MOVED`/`ASK` 响应内容的 `RedisException` | 输出 `[ClusterMonitor]` 警告并转换 `ValkeyAccessException` |

框架在集群侧只做三件事，均不需要自定义重定向实现：

1. **配置**：`maxRedirects`、`autoReconnect(true)`、周期性刷新 + 全部自适应触发器。
2. **监视**：重定向超限时输出 `[ClusterMonitor]` 警告日志（见 6.3）。
3. **有限重试**：对“命令未送达”的连接异常重试，覆盖节点切换窗口内的瞬时失败。

### 6.2 集群自动更新的配置（已内建）

```java
ClusterTopologyRefreshOptions.builder()
    .enablePeriodicRefresh(properties.getTopologyRefreshPeriod()) // 周期性刷新
    .enableAllAdaptiveRefreshTriggers()                            // 全部自适应触发器
    .build();

ClusterClientOptions.builder()
    .autoReconnect(true)                         // 断线自动重连
    .maxRedirects(properties.getMaxRedirects())  // 重定向上限（默认 5）
    .topologyRefreshOptions(topologyRefreshOptions)
    .socketOptions(...)
    .timeoutOptions(...)
    .build();
```

### 6.3 日志监视

| 监视目标 | Logger / 日志来源 | 级别 | 说明 |
| --- | --- | --- | --- |
| 自适应拓扑刷新 | `io.lettuce.core.cluster.ClusterTopologyRefreshScheduler` | DEBUG | 输出触发原因，如 `Adaptive refresh event due to: MOVED_REDIRECT`、`PERSISTENT_RECONNECTS` |
| 拓扑刷新执行 | `io.lettuce.core.cluster.topology.DefaultClusterTopologyRefresh` | DEBUG | 请求分区、节点连接失败等 |
| 连接断开与重连 | `io.lettuce.core.protocol.ConnectionWatchdog` | DEBUG | `scheduleReconnect()`、`Reconnect attempt N`、`Cannot reconnect ...` |
| 重定向达到上限 | `com.example.valkey.support.ValkeyCommandExecutor`（`[ClusterMonitor]`） | WARN | 输出数据源名与原始 MOVED/ASK 消息 |

日志后端配置（`logback-spring.xml` 非 prod profile、`logback-test.xml`）：

```xml
<!-- 集群拓扑刷新与重定向事件的日志监视 -->
<logger name="io.lettuce.core.cluster" level="DEBUG"/>
<logger name="io.lettuce.core.protocol.ConnectionWatchdog" level="DEBUG"/>
```

### 6.4 实测演练记录（WSL + Podman，valkey 9.1.1，3 主 3 从）

演练脚本与环境变量见第 7 节。以下为实际运行输出摘录：

**节点故障演练**（`VALKEY_FAILOVER_DRILL=true`）：

```
[drill] master hosting slot 0 is valkey-node-7001
ConnectionWatchdog - Cannot reconnect to [127.0.0.1/<unresolved>:7001]: Connection refused
ClusterTopologyRefreshScheduler - Adaptive refresh event due to: PERSISTENT_RECONNECTS attempt 5
ClusterTopologyRefreshScheduler - ClusterTopologyRefreshTask requesting partitions
[drill] cluster recovered in 10.3 s after stopping valkey-node-7001 (topology refreshed by Lettuce)
```

- 停止主节点后，落在其槽位上的 key 立即不可读；副本在数秒内被提升为新主。
- 客户端在**不重建任何连接**的情况下自动恢复读写（同一 `ValkeyConnection` 实例）。
- 恢复后重启原节点，其自动以新主的从节点身份重新加入集群，`cluster_state:ok`。

**重定向演练**（`VALKEY_REDIRECTION_DRILL=true`）：

```
[drill] planned failover of slot owner 7003 -> replica 7006
ClusterTopologyRefreshScheduler - Adaptive refresh event due to: MOVED_REDIRECT
ClusterTopologyRefreshScheduler - ClusterTopologyRefreshTask requesting partitions
[drill] redirection was followed automatically, client kept working
```

- 计划内主从切换（`CLUSTER FAILOVER`）后，持有旧拓扑的客户端命令先命中旧节点并收到 MOVED；
  Lettuce 自动跟随重定向并刷新拓扑，命令成功返回，**应用无感知**。
- `max-redirects=0` 的客户端在同样场景下立即失败：

```
[ClusterMonitor] Data source [drill] could not follow a cluster redirection, the redirection
limit was reached; topology refresh will follow: MOVED 11733 127.0.0.1:7003
[drill] redirection limit was reached and translated as expected
```

- 监视日志按预期输出，异常转换为 `ValkeyAccessException`（cause 为原始 `RedisException`）。

### 6.5 结论

1. 重定向**无需应用层实现**：Lettuce 内部处理 MOVED/ASK 并自适应刷新拓扑。
2. 需要做的只是**正确配置**（重定向上限、自动重连、拓扑刷新策略）——框架已内建。
3. 需要补充的是**监视**：拓扑/重连事件的日志监视 + 重定向超限的告警式日志——框架已实现。
4. 命令层对连接异常的**有限重试**能进一步平滑节点切换窗口，与 Lettuce 的自动恢复互补。

---

## 7. 测试指南

### 7.1 测试清单

| 测试类 | 类型 | 覆盖内容 | 缺服务时 |
| --- | --- | --- | --- |
| `JacksonValkeySerializerTest` | 单元 | JSON 序列化往返（ASCII/中文/emoji/控制字符）、非法输入、null | 始终运行 |
| `ValkeyCommandExecutorTest` | 单元 | 重试策略、异常转换、响应式语义、`[ClusterMonitor]` 日志 | 始终运行 |
| `DefaultValkeyConnectionManagerTest` | 单元 | 同名复用、模式分派、未知名称、失败不缓存、关闭 | 始终运行 |
| `ValkeyPropertiesTest` | 单元 | `application.yaml` 结构绑定与默认值 | 始终运行 |
| `ValkeyStandaloneIntegrationTest` | 集成 | 单机模式端到端（含响应式） | 跳过 |
| `ValkeyClusterIntegrationTest` | 集成 | 集群端到端、跨槽位路由、6 节点拓扑发现 | 跳过 |
| `ValkeySpringBootIntegrationTest` | 集成 | Spring 装配、配置绑定、两种模式数据源操作 | 跳过 |
| `ValkeyConnectionFailureIntegrationTest` | 集成 | 连接失败转换、有限重试行为（无需服务端） | 始终运行 |
| `ValkeyClusterFailoverDrillTest` | 演练 | 主节点故障后拓扑自愈（默认禁用） | 跳过 |
| `ValkeyClusterRedirectionDrillTest` | 演练 | 自动重定向跟随、重定向超限监视（默认禁用） | 跳过 |

运行全部测试：

```powershell
.\mvnw.cmd test -pl lettuce-connection-management
```

### 7.2 在 WSL 中搭建集群（Podman）

前提：WSL2 + rootless Podman，valkey 镜像 `docker.io/valkey/valkey:9.1.1`。

```bash
# 在 WSL 中执行（仓库路径按需替换）
bash /mnt/d/Workspaces/lettuce-katas/lettuce-connection-management/scripts/valkey-cluster.sh up
bash .../scripts/valkey-cluster.sh status
bash .../scripts/valkey-cluster.sh down
```

脚本行为：

- 以 **host 网络**启动 6 个容器：`valkey-node-7001` ~ `valkey-node-7006`（3 主 3 从）。
- 节点监听 `127.0.0.1:7001-7006`，总线端口 `17001-17006`。
- `cluster-announce-ip` 固定为 `127.0.0.1`，因此 Windows 侧客户端经 WSL localhost 转发即可直连。
- 认证口令 `change_me`，与 `application.yaml` 中的配置一致。
- Windows 侧验证：`Test-NetConnection 127.0.0.1 -Port 7001` 应返回 `True`。

### 7.3 运行演练

演练默认禁用，通过环境变量开启（需要上述集群已创建）：

```powershell
# 节点故障演练：停止持有 slot 0 的主节点，验证客户端自动恢复
$env:VALKEY_FAILOVER_DRILL = "true"
.\mvnw.cmd test -pl lettuce-connection-management -Dtest=ValkeyClusterFailoverDrillTest -Dmaven.build.cache.enabled=false

# 重定向演练：计划内主从切换，验证自动重定向与超限监视
$env:VALKEY_REDIRECTION_DRILL = "true"
.\mvnw.cmd test -pl lettuce-connection-management -Dtest=ValkeyClusterRedirectionDrillTest -Dmaven.build.cache.enabled=false
```

提示：运行演练前建议 `valkey-cluster.sh status` 确认 `cluster_state:ok`；演练会真实停止/重启
容器并触发主从切换，结束后集群自动收敛（`cluster_state:ok`），但主从角色可能发生变化。

---

## 8. 已知约束与限制

- **命令集固定**：仅 `SET` / `GET` / `DEL`；扩展命令需在操作层补充并复用现有执行器。
- **键值固定为 String**：复杂对象需在外层完成编解码；序列化的 JSON 存储形态为带引号的字符串
  字面量，使用 `redis-cli` 直接读取会看到引号。
- **同一 key 必须使用同一序列化器**：混用不同序列化写入的数据可能无法还原。
- **重试仅针对连接类异常**：避免对可能已执行的非幂等命令造成重复副作用。
- **演练环境依赖**：故障/重定向演练需要 WSL + Podman 与 `scripts/valkey-cluster.sh` 创建的
  集群；常规单元/集成测试不依赖 Podman。
- **密码配置**：示例配置中的明文口令 `change_me` 仅用于本地演练，生产环境应使用密钥管理或
  环境变量注入。
