# Foundation `@ResourceLock` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `paytech-foundation/paytech-redis-starter` 内交付经启动期校验的同步方法级 `@ResourceLock`，以受限参数绑定、确定多锁顺序、单一等待预算和统一 Fail-Closed 语义包住消费者自己的完整本地事务，同时消除 Foundation Redis 自动测试的 Testcontainers 偏差。

**Architecture:** 公共 API 只有 `ResourceLock` 与 Repeatable 容器；内部编译器在启动期把具体实现方法编译为不可变元数据，运行期解析器只投影已批准的标量 Key。Spring Advisor/Interceptor 在业务代理外层调用一个 Redisson 执行器，先解析全部 Key，再按逻辑 Key 排序获取、调用同步业务方法、逆序 owner-safe 释放。Starter 不创建事务、不选择业务资源、不提供降级；没有 Annotation 的服务仍可关闭协调能力，有 Annotation 的服务必须显式配置 Wait/Lease 并具备 Redisson 与可代理 Bean。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring AOP Alliance Advisor/MethodInterceptor、Redisson、Spring Data Redis/Lettuce、Micrometer、JUnit 5、AssertJ、Awaitility、仓库 `local-environment` Redis 8.2.1；版本均由现有 Parent/BOM 管理。

**Spec:** [`docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md`](../specs/2026-09-05-foundation-resource-lock-design.md)

## Global Constraints

- 本计划实现 Foundation 公共能力、配置合同、自动测试和文档，不迁移 Platform 的业务 Key、Processor 或数据库锁。Platform 首个消费者迁移必须另按 [`2026-09-05-platform-support-resource-lock-optimization-design.md`](../specs/2026-09-05-platform-support-resource-lock-optimization-design.md) 编写并执行专项计划；Foundation 通过不代表 Platform 已迁移或已验证。
- 执行前分别检查根工作区、`paytech-build`、`paytech-foundation` 和未来消费者仓库的 `git status --short --branch`。当前根工作区与 `paytech-foundation/README.md` 已有用户修改；不得重置、覆盖或把无关文件混入本切片。
- 不提交、不建分支、不推送、不发布制品、不部署、不启动/停止/重建容器。运行 Redis 自动测试前由执行者人工确认仓库 `local-environment` 已启动；测试本身不得调用 Docker API。
- 每个生产行为严格 TDD：先写最小失败测试并实际运行确认 RED 原因正确，再写最小实现，再运行相同测试确认 GREEN。不得先批量写生产代码后补测试，也不得把环境未准备、旧失败或编译无关失败冒充行为 RED。
- Task 1–8 的 Maven 命令默认工作目录均为 `/Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation`；命令块未重复写 `cd` 时不得从根目录误执行另一套 Wrapper。
- 测试不使用 Mockito、`@MockBean`、Spy、Testcontainers、嵌入式 Redis、固定 `Thread.sleep` 或方法级行为 Stub。纯调度算法允许使用一个有固定状态机的 test-only Fake；Redis 协议、竞争、租约、跨客户端和错误凭据必须再由真实 `mor-local` Redis 证明。
- `@ResourceLock` 只支持 public、非 static、非 final、具体实现类声明的同步 Spring 代理方法；不得标在 Controller、Repository、Mapper、`@Transactional` 方法或事务回调。消费者继续在该公开方法内部使用 `TransactionTemplate`。
- V1 不引入完整 SpEL、Resolver Bean、任意方法调用、集合导航、Watchdog、fencing token、自动重试、读写锁、Semaphore 或 Fail-Open。公共 Annotation 不增加 Wait、Lease、顺序号、错误码或扩展点。
- 动态资源值不得进入异常、日志、Trace、Metric tag 或审计属性。测试断言错误和诊断只含安全 Bean 类型、方法签名、Annotation 序号、固定 Prefix、表达式源码和固定错误分类。
- `redis.coordination.resource-lock.wait-time` 与 `lease-time` 是无默认值的服务级配置。没有 Annotation 时不要求它们，也不因 Resource Lock 能力强制创建 Redisson；宿主若已为其他协调能力显式启用 `redis.coordination.enabled=true`，仍按现有合同创建唯一 Client。存在 Annotation 时 coordination 关闭、配置缺失/非法、Namespace/Redisson 缺失均启动失败。
- Redis 只协调实际资源。消费者采用本能力前仍须记录“不变量—冲突事务—Redis 资源—数据库最终保护”，并保留必要 CAS、条件 DML、唯一索引、隔离级别和局部数据库锁。

---

## Task 0: 冻结基线、依赖边界与 RED 证据

**Read-only evidence:**

- `docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md`
- `agent-docs/decisions/2026-09-05-redis-first-resource-lock.md`
- `paytech-build/paytech-parent/pom.xml`
- `paytech-foundation/pom.xml`
- `paytech-foundation/paytech-redis-starter/pom.xml`
- `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/**`
- `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/**`
- `local-environment/compose.yaml`

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/architecture/ResourceLockArchitectureAutoTest.java`

- [ ] 记录根工作区、Build、Foundation 的 Branch、HEAD、`git status --short`，以及本计划所列已有文件的 SHA-256；明确 `paytech-foundation/README.md` 的既有修改归用户所有。
- [ ] 核对已解析 `paytech-parent` 的 Effective POM 保持 `<maven.compiler.parameters>true</maven.compiler.parameters>`，不在 Redis Starter 重复配置 `-parameters`。运行：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -q -pl paytech-redis-starter help:effective-pom \
  -Doutput=paytech-redis-starter/target/resource-lock-effective-pom.xml
rg -n '<parameters>true</parameters>' paytech-redis-starter/target/resource-lock-effective-pom.xml
```

- [ ] 先创建架构 RED，精确断言：`com.paytech.foundation.redis.api.ResourceLock` 与 `ResourceLocks` 存在；Annotation 公共形状无多余成员；内部包不依赖 MVC、审计、Repository、Mapper 或 Platform；Redis Starter POM 无 Testcontainers；生产代码不调用 `RKeys`、`forceUnlock`、`flushdb`、`flushall`、`KEYS *` 或原始 Redis 连接逃逸。
- [ ] 运行并记录 RED；预期只因公共类型尚不存在且 POM 仍有 `testcontainers-junit-jupiter` 失败，而不是因为测试扫描了 `target` 或其他仓库：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockArchitectureAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 本 Task 不删除 Testcontainers 依赖；该 RED 留到 Task 7 与真实 Redis 迁移同时转绿，避免先移依赖导致现有集成测试无法编译。

## Task 1: 发布最小公共 Annotation API

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/api/ResourceLock.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/api/ResourceLocks.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/api/ResourceLockAnnotationContractAutoTest.java`

- [ ] 先写反射 RED：`ResourceLock` 必须恰好有 `prefix()` 和默认空串 `key()`；带 `@Documented`、Runtime Retention、Method Target、`@Repeatable(ResourceLocks.class)`。`ResourceLocks` 必须恰好有 `ResourceLock[] value()`，无其他业务成员。
- [ ] 运行聚焦测试，确认因两个类型不存在而编译失败：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockAnnotationContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现精确 API，不加入 Wait、Lease、TimeUnit、Resolver、Order、Error Code 或 Fail-Open：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ResourceLocks.class)
public @interface ResourceLock {
    String prefix();
    String key() default "";
}
```

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResourceLocks {
    ResourceLock[] value();
}
```

- [ ] 重跑 Annotation Contract 与 Task 0 架构测试；本阶段 Annotation Contract 应 GREEN，架构测试仍应只因 Testcontainers 偏差保持 RED。

## Task 2: 编译受限表达式与启动期静态元数据

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockValueAccessor.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockBinding.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockMethodMetadata.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockMetadataCompiler.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockMetadataRegistry.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/lock/ResourceLockMetadataCompilerAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/fixture/NoParameterMetadataCompiler.java`

- [ ] 先写编译器 RED，覆盖合法固定资源、`#param`、Record Component 和标准 `getXxx()` JavaBean getter；若 `isXxx()` 返回 boolean，则因静态结果类型不受支持而拒绝。元数据保持 Annotation 序号、Prefix 和已解析参数索引/Accessor，运行期不再做名称查找或重新解释表达式。
- [ ] 在同一测试逐个覆盖启动拒绝：非法/超长/非 ASCII Prefix；非空但只含空白的 Key（精确空字符串是固定资源的合法默认）；`#root`、`#this`、`@bean`、`T(...)`、`new`、调用、索引、数组、集合、Map、安全导航、Elvis、运算、拼接、三元、投影、选择和两层字段；未知/重复参数名；private field-only；不存在/非 public/带参数 getter；重复 Annotation；String/八种整数/UUID 之外的静态类型。
- [ ] 使用 `NoParameterMetadataCompiler` 调用 JDK `JavaCompiler` 在 JUnit 临时目录编译一个未带 `-parameters` 的 Fixture，再断言真实 `Parameter.isNamePresent()==false` 并启动编译失败；不得以拼错 `#arg0` 冒充 Parameter Metadata 测试。
- [ ] 运行 RED，确认失败原因为编译器/元数据类型尚不存在：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockMetadataCompilerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现包内不可变模型：

```java
@FunctionalInterface
interface ResourceLockValueAccessor {
    Object read(Object[] arguments);
}

record ResourceLockBinding(int annotationIndex, String prefix,
    String expression, Class<?> valueType, ResourceLockValueAccessor accessor) {}

record ResourceLockMethodMetadata(Method method,
    List<ResourceLockBinding> bindings) {}
```

- [ ] `ResourceLockMetadataCompiler` 只接受完整正则 `#[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)?`，通过反射 Parameter Metadata 定位根参数；一层属性只接受 Record Component accessor 或 JavaBeans `PropertyDescriptor` 的 public、无参 read method，绝不读 private field、调用任意表达式方法或使用 `toString()`。
- [ ] Prefix 只接受 `[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*` 且 ASCII 长度 1–128；静态结果只接受 String、byte/short/int/long 及包装类型、UUID。重复 Annotation 按 `prefix + key` 精确拒绝。
- [ ] 所有静态失败统一构造安全 `FailException(RedisErrorCode.CONFIGURATION_INVALID, ...)`，消息包含 Bean 类型、完整方法签名、1-based Annotation 序号、Prefix/表达式源码和固定原因，不求值、不打印参数。
- [ ] `ResourceLockMetadataRegistry` 只允许启动校验器一次性注册不可变 `Method -> ResourceLockMethodMetadata`；首次调用找不到已编译元数据时 Fail-Closed，不在热路径懒编译。
- [ ] 重跑本 Task 测试，确认全部合法/禁止矩阵 GREEN；再跑 `ResourceLockAnnotationContractAutoTest` 防止编译器需求污染公共 API。

## Task 3: 运行期 Key 投影、全量预校验、去重与排序

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResolvedResourceLock.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockKeyResolver.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/lock/ResourceLockKeyResolverAutoTest.java`

- [ ] 先写纯单元 RED：String 原样、byte/short/int/long 十进制、UUID 标准小写连字符；固定资源只返回 Prefix，不追加尾冒号；动态资源返回 `{prefix}:{canonicalValue}`。
- [ ] 覆盖所有运行期非法值：`null`、空、首字符非法、空格/非 ASCII、字符集外符号、256 字符以上，以及批准的 Record/JavaBean accessor 求值时抛错。断言均在任何锁客户端调用前抛不回显值的 `InvalidParameterException`；accessor 原异常只作为 Cause 保留，错误消息不包含原值、完整逻辑 Key 或参数对象。
- [ ] 覆盖多 Annotation：先对全部值求值并校验，再按完整逻辑 Key 的 ASCII 升序排序；不同 Annotation 运行时解析为同一逻辑 Key 时只保留一次；其中最后一个值非法时不得返回前面已解析的部分列表。
- [ ] 运行 RED：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockKeyResolverAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现 `ResolvedResourceLock` 仅在内存持有安全 Prefix 与逻辑 Key；不得覆写会泄漏逻辑 Key 的 `toString()`，不得把动态值放入异常或观察 Tag。
- [ ] 实现 `ResourceLockKeyResolver.resolve(metadata, arguments)`：完整求值与校验后，用 `TreeMap<String, ResolvedResourceLock>` 或等价 ASCII 稳定排序/去重。String 必须匹配 `[A-Za-z0-9][A-Za-z0-9._:-]{0,255}`；整数用对应 `toString`；UUID 用 `UUID.toString()`。
- [ ] 重跑本 Task 测试并确认 GREEN；同时用一个计数 Fake 证明非法值路径没有调用任何 Redis 适配器。

## Task 4: 服务级配置、Advisor 注册和两阶段启动 Fail-Fast

**Files:**

- Modify: `paytech-foundation/paytech-redis-starter/pom.xml`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/autoconfigure/RedisResourceLockProperties.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/autoconfigure/RedisResourceLockAutoConfiguration.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockPointcut.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockAdvisor.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockBeanDefinitionValidator.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockStartupValidator.java`
- Modify: `paytech-foundation/paytech-redis-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/autoconfigure/RedisResourceLockAutoConfigurationAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/resources/application-resource-lock-auto.yml`

- [ ] 先写配置 RED：无 Annotation、coordination=false、无 Wait/Lease 时 Context 正常且无 Redisson 连接；存在 Annotation 时 coordination 关闭、Redisson 缺失、Namespace 缺失、Wait/Lease 缺失、Wait 负数、Lease 非正数或 Duration 转纳秒溢出均在刷新完成前失败。`wait-time=0ms` 合法，`lease-time=1ms` 合法。
- [ ] 写代理/方法形状 RED：public concrete method 得到 Spring Proxy；private/static/final method、final class、接口单独声明 Annotation、`@Transactional` 同方法、`CompletionStage`/`Future`/Reactor Publisher/其他延迟返回均启动失败；消息包含安全 Bean/方法/Annotation index。
- [ ] 写 Advisor 顺序 RED：Resource Lock Advisor 的 order 固定早于 Spring transaction advisor；测试 Fixture 内部用 `TransactionTemplate`，进入方法时尚未由 Annotation 创建事务。另断言 Controller/Repository/Mapper 类型上的 Annotation 被启动架构门禁拒绝。
- [ ] 运行 RED，预期因 Properties/AutoConfiguration/Advisor/Validator 尚不存在失败：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=RedisResourceLockAutoConfigurationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 在 POM 增加直接生产依赖 `org.springframework:spring-aop` 和 `io.micrometer:micrometer-core`，不增加 AspectJ 表达式或完整 SpEL 依赖；沿用 Parent 管理版本。
- [ ] 实现无默认值配置：

```java
@ConfigurationProperties(prefix = "redis.coordination.resource-lock")
public class RedisResourceLockProperties {
    private Duration waitTime;
    private Duration leaseTime;
    // getters/setters; validate only when annotated methods exist
}
```

- [ ] `validateRequiredConfiguration()` 接受非负有限 Wait、正有限 Lease，并预先安全转换为纳秒；配置对象启动后只读使用，不提供 RefreshScope 或动态 Setter 消费路径。`application-resource-lock-auto.yml` 精确展示服务级 `500ms/30s`，测试覆盖 YAML 绑定而非只手工 new Properties。
- [ ] `RedisResourceLockAutoConfiguration` 必须始终注册 Annotation 扫描/Advisor 基础设施，不能把整个配置放在 `@ConditionalOnProperty(enabled=true)` 后导致 Annotation 静默无锁；Redisson 仍由现有 `RedisAutoConfiguration` 只在 coordination=true 时创建。
- [ ] AutoConfiguration 显式启用 class-based Spring auto-proxy（例如内部配置上的 `@EnableAspectJAutoProxy(proxyTargetClass = true)`），使只引入 Starter 的消费者也能应用自定义 Advisor；不得假设消费者碰巧引入另一套 AspectJ 配置。
- [ ] `ResourceLockBeanDefinitionValidator` 在代理创建前扫描 Bean Definition/`@Bean` 返回类型，先拒绝 final/static/private/接口-only/异步/`@Transactional` 等确定性缺陷，避免 Spring 先抛无上下文代理错误。`ResourceLockStartupValidator` 在 singleton 初始化后扫描实际 target class，编译全部元数据、检查 `AopUtils.isAopProxy(bean)`、唯一 Redisson/Namespace/配置，并原子冻结 Registry。
- [ ] `ResourceLockPointcut` 只匹配具体 target method 上直接/Repeatable Annotation；接口 Annotation 不作为匹配来源。Pointcut 在 Bean 代理阶段独立判断 Annotation，不能依赖尚未冻结的 Registry。
- [ ] `ResourceLockAdvisor` 使用自定义 Pointcut 与后续 Interceptor，固定 `Ordered.HIGHEST_PRECEDENCE + 100`（或一个测试冻结且明确早于事务 advisor 的同等常量）；禁止用源码 Annotation 顺序决定锁顺序。
- [ ] 把 `RedisResourceLockAutoConfiguration` 加入 `AutoConfiguration.imports`，顺序放在现有 Namespace 与 Redisson 配置之后；无 Annotation 的普通 Redis/Cache Context 回归不得额外连接 Redis。
- [ ] 重跑本 Task 测试确认 GREEN，并回归 `RedisAutoConfigurationAutoTest,RedisCoordinationPropertiesAutoTest,RedisNamespacePropertiesAutoTest`。

## Task 5: 实现单一等待预算、异常映射和 owner-safe 释放

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockOperations.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/RedissonResourceLockOperations.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockObservation.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockExecutor.java`
- Create: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/lock/ResourceLockMethodInterceptor.java`
- Modify: `paytech-foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/autoconfigure/RedisResourceLockAutoConfiguration.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/lock/ResourceLockExecutorAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/lock/ResourceLockMethodInterceptorAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/integration/ResourceLockTransactionBoundaryAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/configuration/ResourceLockTestConsumerConfiguration.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/fake/InMemoryResourceLockOperations.java`

- [ ] 先用固定状态机 `InMemoryResourceLockOperations` 写 Executor RED：单锁成功；业务返回值/checked exception/runtime exception/Error 原样传播；全部锁按已排序列表获取并逆序释放；运行时重复 Key 只获取一次；部分获取失败逆序清理且业务零调用。
- [ ] 写总预算 RED：向 Executor 注入包内 test constructor 的单调 `nanoTime`，让第一个锁消耗部分预算，断言第二个只收到剩余纳秒；N 个锁的总等待不超过一次 `wait-time`。剩余预算为零时仍对后续锁执行一次 `tryLock(0, lease)` 即时尝试，不能因 `wait-time=0` 跳过本可立即取得的锁。
- [ ] 写异常 RED：竞争精确抛 `ConcurrentRequestException("该资源正在处理其他请求，请稍后重试")`；直接 `InterruptedException` 或 RuntimeException 的直接 Cause 为 InterruptedException 时恢复 interrupt flag 并用带 Cause 的 ConcurrentRequestException；其他连接/认证/命令异常映射保留 Cause 的 ServiceUnavailableException。每测结束清理 interrupt flag。
- [ ] 写 Lease/释放 RED：每次 `tryLock` 都显式收到同一 lease-time，不走无参/Watchdog；只释放本次 acquired 且 `isHeldByCurrentThread()` 为 true 的锁；Lease 已过期时旧 Owner 不 unlock；`isHeldByCurrentThread`、`unlock` 的 IllegalMonitorState/Redis 异常不覆盖业务结果或原业务异常。
- [ ] 写观察 RED：Metric 只使用代码 Prefix 与固定 status `acquired/contended/interrupted/unavailable/release-failed`，记录等待/持有时间但无动态 Key；Release warning 只含 Prefix 与固定错误分类。通过 Log Capture 与 `SimpleMeterRegistry` 断言秘密值不出现。
- [ ] 在写生产 Interceptor 前创建真实 Spring Proxy/事务边界 RED：Fixture 固定为 `caller bean -> proxied public command -> TransactionTemplate callback`，事件必须精确为 `resolve-all -> acquire-all -> transaction-begin -> business -> commit/rollback -> reverse-release`；竞争/Fail-Closed/非法值时 transaction/business 计数均为零。
- [ ] 在同一边界测试增加“外层事务已激活后调用带 Annotation 方法”的 RED：Interceptor 必须在 Key 求值和 Redis 访问前 Fail-Fast，业务计数为零，错误不含动态 Key。
- [ ] 运行 RED：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockExecutorAutoTest,ResourceLockMethodInterceptorAutoTest,ResourceLockTransactionBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 定义最窄内部端口，不能公开成业务 Resolver SPI：

```java
interface ResourceLockOperations {
    LockHandle lock(String logicalKey);

    interface LockHandle {
        boolean tryLock(long waitNanos, long leaseNanos) throws InterruptedException;
        boolean isHeldByCurrentThread();
        void unlock();
    }
}
```

- [ ] `RedissonResourceLockOperations` 只调用 `redissonClient.getLock(logicalKey)`、`tryLock(wait, lease, NANOSECONDS)`、`isHeldByCurrentThread()` 和普通 `unlock()`；不得调用 `forceUnlock`、RKeys 或自己重复拼 Namespace/`lock:`。
- [ ] `ResourceLockExecutor` 在调用 Redis 前接收 Task 3 已完整解析/验证/排序的列表；用 `System.nanoTime()` 计算一个总 deadline，并防止加法溢出。每次获取成功才加入 acquired stack，任何退出路径都逆序处理。
- [ ] 业务调用结果先保存、业务 Throwable 先捕获；释放故障只交给 `ResourceLockObservation`，随后原样 return/rethrow 原业务结果。没有业务 Throwable 时释放错误也不改变成功结果。
- [ ] `ResourceLockMethodInterceptor.invoke(MethodInvocation)` 先用 `TransactionSynchronizationManager.isActualTransactionActive()` 拒绝已活动事务，再做：从冻结 Registry 取 metadata → Key Resolver 全量解析 → Executor 包住 `invocation.proceed()`；不创建事务、不捕获并改写业务异常、不重试。
- [ ] AutoConfiguration 用 `ObjectProvider<RedissonClient>` 延迟连接依赖，使无 Annotation/coordination=false 的服务不创建 Redisson；Startup Validator 保证任何带 Annotation 的方法执行前 Provider 必须唯一可用。
- [ ] 重跑本 Task 测试确认 GREEN，再重跑 Task 3/4 测试确认非法值仍先于 Redis、Advisor 仍早于事务。

## Task 6: 收敛 Spring 代理、事务与服务级配置验收

**Files:**

- Modify only if Task 5 RED exposed an integration defect: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/integration/ResourceLockTransactionBoundaryAutoTest.java`
- Modify only if binding evidence is incomplete: `paytech-foundation/paytech-redis-starter/src/test/resources/application-resource-lock-auto.yml`

- [ ] 重跑 Task 5 已先红后绿的真实 Spring Proxy Fixture；禁止直接 new Interceptor 或直接调用 Executor 代替生产链。Fixture 以服务配置文件启用 coordination 并配置 `wait-time: 500ms`、`lease-time: 30s`；Annotation 不携带运行参数。
- [ ] 用 test-only `PlatformTransactionManager`/同步钩子（不用 Mockito）确认提交返回与回滚抛错时，Interceptor 都只在事务终态以后释放。增加同类 `this.annotatedMethod()` Fixture，架构测试必须拒绝该绕过，而不是让行为测试误称已加锁。
- [ ] 运行验收：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockTransactionBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 若事件序列不准确，只修生产 Advisor/Interceptor 顺序或 Fixture，不能把断言放宽为“最终都发生过”。确认 GREEN 后将这个 Fixture 作为后续消费者测试的最小参考，不发布进 main JAR；本 Task 不新增生产行为。

## Task 7: 把 Redis 自动测试迁移到仓库 `local-environment`

**Files:**

- Modify: `paytech-foundation/paytech-redis-starter/pom.xml`
- Modify: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/integration/RedisStarterIntegrationAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/integration/ResourceLockRedisIntegrationAutoTest.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/RedisLocalEnvironmentPrecondition.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/RedisTestNamespace.java`
- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/ResourceLockProcessProbe.java`

- [ ] 先扩展 Task 0 架构 RED，扫描整个 Foundation Reactor 的 effective test dependencies 与源码 import，拒绝 `org.testcontainers`、Docker client 和 embedded Redis；当前应精确命中 Redis Starter 现有依赖/import。
- [ ] 创建 `RedisLocalEnvironmentPrecondition`，只读取 `PAYTECH_REDIS_PASSWORD`，固定连接 `127.0.0.1:16379`，在缺密码、端口不可达、认证失败或版本不符时以 AssertionError 快速失败，并输出不含密码的准备命令：`local-environment` Compose `ps redis` 与安全加载 `.env.local`。不得 `Assume` 跳过、读取 `.env.local` 或连接 6379。
- [ ] 创建每次 Maven/JVM 不可预测唯一的 `RedisTestNamespace`；Setup/Teardown 只以 SCAN 找到该物理前缀并精确删除，禁止 `KEYS *`、FLUSHDB/FLUSHALL、RKeys 和删除其他运行 Namespace。先写隔离测试，在相邻 Namespace 放 Sentinel 并证明清理后仍存在。
- [ ] 将现有 `RedisStarterIntegrationAutoTest` 移除 `@Testcontainers`、`@Container`、`GenericContainer` 和临时密码，改用同一 Precondition、固定 Loopback 地址、环境密码和运行级唯一 Namespace；保留 Lettuce/JSON/Cache/Redisson/TTL/NameMapper 的原有生产链断言。
- [ ] 新建真实 Resource Lock 集成 RED，至少覆盖：单锁代理成功、业务异常原样；同 Key 两线程竞争和不同 Key 并行；两个独立 ApplicationContext/RedissonClient 的跨客户端竞争；多 Annotation 不同源码顺序得到相同获取顺序；运行重复 Key 去重；部分失败逆序释放；总 Wait 预算。
- [ ] 为跨 JVM 证据用 `ProcessBuilder` 启动 test classpath 上的 `ResourceLockProcessProbe`，父子进程通过 stdin/stdout 的 `READY/RELEASED` 协议协调，不用 sleep；子进程只使用本次 Namespace 与环境密码，退出码/超时失败时输出安全摘要。
- [ ] 覆盖真实 Lease 到期：第一 Owner 用 Latch 停在业务内，Awaitility 观察 Lease 消失后第二 Owner 进入；释放第一 Owner 时证明不会释放第二 Owner。数据库最终保护不是 Foundation 测试职责，需由消费者 Lease-overlap 测试另证。
- [ ] 覆盖等待线程 interrupt 并确认 interrupt flag；Redis 未监听 Loopback 端口与错误凭据分别得到 ServiceUnavailableException/保留 Cause。不得停止共享 Redis 模拟不可用。
- [ ] Release 故障不通过破坏共享 Redis 注入；该分支由 Task 5 固定 Fake 证明。真实 Redis 测试只验证 Lease 过期 owner-safe 行为与正常释放。
- [ ] 先运行真实 Redis RED，确认失败由现有 Testcontainers Fixture/公共锁链缺失造成，而不是 `mor-local` 未准备：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base ps redis
set -a
source local-environment/.env.local
set +a
cd paytech-foundation
./mvnw -pl paytech-redis-starter -am \
  -Dtest=RedisStarterIntegrationAutoTest,ResourceLockRedisIntegrationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现测试迁移后，从 POM 删除 `testcontainers-junit-jupiter`，确认 `dependency:tree` 不再解析任何 Testcontainers/Docker/embedded Redis：

```bash
./mvnw -q -pl paytech-redis-starter dependency:tree \
  -Dscope=test \
  -Dincludes=org.testcontainers:*,com.github.docker-java:*,it.ozimov:embedded-redis
```

- [ ] 重跑真实 Redis 测试确认 GREEN，再重跑 `ResourceLockArchitectureAutoTest`，此时 Task 0 暂存的 Testcontainers RED 必须转绿。

## Task 8: 增加消费者不可绕过门禁模板与 Platform 迁移 Gate

**Files:**

- Create: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/architecture/ResourceLockConsumerContractAutoTest.java`
- Modify: `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/architecture/ResourceLockArchitectureAutoTest.java`
- Modify later under the separate Platform implementation plan, not this plan: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformArchitectureAutoTest.java`
- Modify later under the separate Platform implementation plan, not this plan: `platform-support-service/platform-support-web/src/main/resources/application.yml`
- Modify later under the separate Platform implementation plan, not this plan: `platform-support-service/platform-support-test/src/test/resources/application-auto.yml`

- [ ] 先写 Foundation 消费者 Fixture RED，扫描字节码和 Spring Context，拒绝带 Annotation 方法的 `this` 自调用、直接 new 实现类绕过、private/final 方法、Controller/Repository/Mapper 放置、同方法 `@Transactional`、业务侧自建同义 Redisson wrapper；允许公开 Processor 在方法内部调用 `TransactionTemplate`。
- [ ] 实现测试侧最小扫描器/Fixture，不把通用 ArchUnit 或扫描 API 发布进生产 JAR。对无法由 Foundation 从任意消费者 JAR 自动证明的 self-invocation，测试名称和文档必须明确这是消费者应复制/适配的门禁模板，不声称运行期已阻止所有绕过。
- [ ] 运行并确认 GREEN：

```bash
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockArchitectureAutoTest,ResourceLockConsumerContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 为 Platform 专项计划冻结进入 Gate：Foundation 目标源码/测试已完成；可解析的 Snapshot 制品已安装/发布；Platform 所有同资源入口一次切换到批准的新 Key 合同；Platform 的 `application.yml` 与 `application-auto.yml` 显式配置 500ms/30s；真实 Controller→Proxy→Processor→TransactionTemplate→DB 测试和 Lease-overlap 最终保护通过。
- [ ] 本计划不编辑上述三个 Platform 文件，不删除 `OperationsResourceLock`，不改变认证链/数据库锁，不提前把 Platform 标成 Migrated/Verified。若执行者需要继续 Platform，停止并先创建专项 implementation plan。

## Task 9: 同步 README、通用规则、路由和准确状态

**Files:**

- Modify: `paytech-foundation/README.md`
- Modify: `agent-docs/TECHNOLOGY_STACK.md`
- Modify: `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `agent-docs/UNIT_TEST_GUIDE.md`
- Modify: `AGENTS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/DOCUMENT_GOVERNANCE.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Modify: `agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md`

- [ ] 在生产代码与真实 Redis 测试尚未 GREEN 前，只允许状态为 Designed/In progress；不得提前写 Implemented、Verified、Published 或 Deployed。
- [ ] 更新 `paytech-foundation/README.md`：公共 Annotation 完整示例；固定资源与 `#param/#param.field`；支持类型/字符限制；500ms/30s 服务级配置示例；Advisor→Processor→TransactionTemplate 顺序；409/503/interrupt/release 语义；Redis-first 与数据库最终保护；真实 `local-environment` 准备和可复现命令。删除旧 Testcontainers “当前偏差”描述，只在验证确已通过后写实际结果。
- [ ] `TECHNOLOGY_STACK.md §6.7/Redis` 只增加已实现公共能力和依赖边界；保留 Gateway Reactive Lua 不使用本 Starter、普通 Lettuce 与协调 Redisson 的边界。
- [ ] `DEVELOPMENT_CONVENTIONS.md §5.1` 将“不得新建通用锁框架”收窄为“业务服务不得自建同义能力”，继续要求实际资源、Redis/CAS/DB 局部保护分开评估，禁止 Tenant/Operator 默认大锁。
- [ ] `JAVA_PROJECT_STRUCTURE.md` 记录 `com.paytech.foundation.redis.api` 公共边界、Foundation internal advisor/interceptor，以及消费者 Annotation 只能位于代理可达 public Processor/协调组件、事务位于方法内部。
- [ ] `UNIT_TEST_GUIDE.md` 增加真实 Redis Annotation、跨客户端/JVM竞争、确定多锁、总预算、Lease-overlap、interrupt、错误凭据、事务顺序与消费者架构门禁；删除 Foundation Redis Testcontainers 仍可接受的任何表述。
- [ ] 根 `AGENTS.md` 与 `agent-docs/README.md` 只加主题路由，不复制 API/Key/异常完整合同。`DOCUMENT_GOVERNANCE.md` 明确 Decision→Foundation Spec→本 Plan，以及 Platform 专项 Spec 的 Owner 分界。
- [ ] `agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md` 只增加“后继由 2026-09-05 公共锁与 Platform 优化设计接管”的窄链接，不重写其历史 Key、测试数字或当时状态。
- [ ] `CURRENT_IMPLEMENTATION.md` 分栏记录：Foundation source implemented、真实 Redis verified、artifact published、Platform migrated/verified/deployed。只勾实际已发生项；Redis Testcontainers 子偏差仅在依赖扫描和真实测试均通过后关闭。`TEST-01` 仍含 Compensation Event 自动重建 Schema 的独立偏差，不得把整项误标完成；Platform 仍保持 Pending。
- [ ] 运行链接/术语/状态扫描，确认无旧偏差或夸大：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
rg -n 'Testcontainers|OperationsResourceLock|@ResourceLock|resource-lock|TEST-01' \
  AGENTS.md agent-docs paytech-foundation/README.md \
  docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md \
  docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md
node - <<'NODE'
const fs = require('node:fs');
const files = [
  'docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md',
  'paytech-foundation/README.md',
  'agent-docs/CURRENT_IMPLEMENTATION.md'
];
const forbidden = ['TO' + 'DO', 'T' + 'BD', 'place' + 'holder', '\u5f85' + '\u8865'];
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  for (const term of forbidden) {
    if (text.includes(term)) throw new Error(`${file} contains forbidden marker ${term}`);
  }
}
NODE
npm --prefix requirements-site test
```

## Task 10: 全量验证、制品边界与交付记录

**Files:**

- Modify only if verification facts changed: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Modify only if commands/results changed: `paytech-foundation/README.md`

- [ ] 确认 `mor-local` Redis 健康并安全加载 `PAYTECH_REDIS_PASSWORD`，不输出环境变量；先运行 Redis Starter 全模块测试：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-redis-starter -am test
```

- [ ] 运行 Foundation 全 Reactor，验证公共依赖没有破坏其他 Starter：

```bash
./mvnw clean verify
```

- [ ] 检查生产/测试依赖与 JAR 公共面：

```bash
./mvnw -q -pl paytech-redis-starter dependency:tree
jar tf paytech-redis-starter/target/paytech-redis-starter-1.0.0-SNAPSHOT.jar \
  | rg 'ResourceLock|ResourceLocks|ResourceLockAdvisor|ResourceLockMethodInterceptor'
javap -classpath paytech-redis-starter/target/paytech-redis-starter-1.0.0-SNAPSHOT.jar \
  com.paytech.foundation.redis.api.ResourceLock \
  com.paytech.foundation.redis.api.ResourceLocks
```

- [ ] 断言资源锁新增的公共 API 只有两个 Annotation；Compiler/Registry/Advisor/Interceptor/Operations/Observation 均位于 internal `core` 包且不构成业务 SPI。确认 AutoConfiguration imports 含资源锁配置且无 Annotation 的最小 Context 不创建 Redisson。
- [ ] 重跑文档检查与 `npm --prefix requirements-site test`。若 requirements-site 存在与本切片无关的旧失败，记录精确命令/失败，不修改无关需求数据。
- [ ] 最后分别执行根、Build、Foundation `git diff --check` 与 `git status --short`；核对只包含本计划授权文件与执行前已记录的用户修改，不暂存、不提交。
- [ ] 将真实测试命令、退出码、Redis 前置、Foundation HEAD/工作树和未运行项写入交付记录。只有本 Task 的真实 Redis与全 Reactor均 exit 0，才把 Foundation 标记 Verified；没有 `deploy/install/deploy` 证据时 Published/Deployed 仍为 No。
- [ ] 明确交付后的独立下一步：依据 Platform 专项 Spec 编写其 implementation plan，完成 500ms/30s 服务配置、业务 Key 单版本切换、数据库锁/CAS 优化和真实消费者链路测试；不得把该未来工作算进本计划完成度。
