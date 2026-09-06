# Foundation `@ResourceLock` 设计

状态：Confirmed design

实现、验证、制品发布与部署状态：仅见状态台账，不由本设计文档维护

日期：2026-09-05

决策来源：[`2026-09-05-redis-first-resource-lock.md`](../../../agent-docs/decisions/2026-09-05-redis-first-resource-lock.md)。当前实现、验证、制品发布和部署状态仅见 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 与 [`paytech-foundation/README.md`](../../../paytech-foundation/README.md)。

## 1. 目标与范围

本设计在 `paytech-redis-starter` 内增加一个同步、方法级、排他型资源锁能力，使业务服务能够声明“本次用例操作哪个实际资源”，而不用复制 Redisson 的获取、等待、中断、异常和释放模板。

目标：

1. 用最小 API `@ResourceLock(prefix, key)` 表达标准资源锁；
2. 在应用启动时验证 Annotation、参数绑定和服务级配置；
3. 锁住整个本地事务，而不是在事务已经占用连接后等待 Redis；
4. 多锁先完整解析，再按确定顺序获取并逆序释放；
5. Redis 获取、竞争、中断和释放错误具有统一安全语义；
6. 明确 Redis-first 只做跨实例协调，数据库按业务不变量保留最终保护；
7. 不把敏感或无界输入写入 Redis Key、日志、Trace 或 Metric Label。

适用范围：引入 `paytech-redis-starter`、使用同步 Spring Bean 调用且已经证明需要跨实例资源协调的 Java 服务。Platform Support 是首个迁移候选，但该候选不构成已实施声明。

## 2. 非目标

本能力不负责：

- 选择业务资源、推导 Tenant/Account/Member/Role 等业务等价关系；
- 启动、提交或回滚数据库事务；
- 替代 Version CAS、期望状态、唯一索引、幂等记录、数据库行锁或隔离级别；
- 提供 fencing token、无限 Lease、自动续租正确性证明或 Redis 业务权威状态；
- 提供读写锁、公平锁、信号量、队列、调度、跨服务事务或通用工作流；
- 支持完整 SpEL、Bean 方法调用、任意对象导航或动态脚本；
- 支持 Reactor、Kotlin Coroutine、`CompletionStage` 等方法返回后的异步持锁；
- 改造 API Gateway 的 Reactive Redis/Lua Guard；
- 自动审计锁事件或依赖 Platform Audit。

## 3. 公共 Java API

公共类型位于 `com.paytech.foundation.redis.api`：

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

`ResourceLocks` 只是 Java Repeatable Annotation 所需的公开容器，不增加其他语义。V1 不在 Annotation 上增加 Wait、Lease、TimeUnit、Error Code、是否 Fail-Open、排序号或 Key Resolver Bean；这些参数要么属于服务运行画像，要么会把业务扩展点带入公共基础设施。

`key` 为空时表示 `prefix` 本身就是唯一固定资源，适用于全局 Catalog 这类没有动态资源 ID 的场景：

```java
@ResourceLock(prefix = "gateway-configuration:catalog")
```

典型单锁用法：

```java
@ResourceLock(prefix = "operations:account", key = "#targetAccountId")
public void disable(String actorId, String sessionId, String targetAccountId,
    DisableAccountRequestBody request) {
    transactions.write(() -> accountService.disable(actorId, sessionId, targetAccountId, request));
}
```

一层属性用法：

```java
@ResourceLock(prefix = "payment:refund", key = "#command.refundId")
public RefundResult refund(RefundCommand command) {
    return transactions.write(() -> refundService.refund(command));
}
```

上述示例只说明语法。真实 Prefix 和资源 ID 必须由业务不变量评审决定。不得把邮箱形式的 `loginIdentifier`、Token、Secret 或请求 Body 直接作为 Key。

## 4. Prefix、表达式与 Key 合同

### 4.1 Prefix

`prefix` 是固定逻辑前缀，表示能力和实际资源类型，例如 `operations:account`、`payment:refund`。它必须：

- 在编译期写死，不能包含属性占位符或表达式；
- 使用小写 kebab-case 分段，格式为 `[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*`；
- 长度为 1–128 个 ASCII 字符；
- 不包含服务名，服务 Namespace 由 Starter 的 Name Mapper 只添加一次；
- 不使用 Controller 方法名或动作名区分同一个资源。

### 4.2 受限 Key 表达式

`key` 不是完整 SpEL，除固定资源使用空字符串外，只接受以下两种完整形式：

```text
#param
#param.field
```

语法精确定义为：

```text
#[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)?
```

规则：

- `param` 必须是被注解方法的真实 Java 参数名；
- `field` 最多一层，只能对应 Record Component accessor 或启动期已验证可访问的 public 无参数 JavaBean getter；
- 不读取 private field，也不接受表达式内任意方法调用；上述 accessor 必须是纯、快速且不访问外部状态的值读取；
- 禁止 `#root`、`#this`、`@bean`、`T(Type)`、`new`、方法调用、索引、数组/集合/Map 访问、安全导航、Elvis、运算、拼接、三元表达式、投影和选择；
- 禁止使用任意对象 `toString()` 生成 Key。

项目 Parent 已启用 Java Parameter Metadata；若消费者未保留参数名，启动校验失败，不能回退到 `arg0`。

### 4.3 支持的求值类型和值

表达式的静态结果只支持：

- `String`；
- `byte/short/int/long` 及其包装类型；
- `UUID`。

不支持浮点数、布尔值、Enum、数组、集合、Map、Optional、任意 DTO 或领域对象。数值使用十进制无分隔符表示，UUID 使用标准小写连字符格式。String 运行期必须满足 `[A-Za-z0-9][A-Za-z0-9._:-]{0,255}`。

所有求值结果必须非 `null`、非空且长度有界。运行期值不合法时，在取得任何锁和进入业务方法前抛出不回显原值的 `InvalidParameterException`。业务方若需要让非法请求继续进入自身校验，可以显式传入一个固定、非敏感且有界的安全资源 Key；Foundation 不猜测 fallback。

邮箱、手机号、姓名、政府标识、Token、Cookie、Secret、Vault 值、敏感明文、完整请求体和可被无限扩张的自由文本，即使字符格式合法也禁止用作 Key。普通 SHA-256 不能自动把低熵邮箱变成安全标识；任何敏感输入派生方案必须另行设计带密钥的不可逆映射及轮换边界。

### 4.4 逻辑与物理 Key

单个动态 Annotation 的规范逻辑 Key 为：

```text
{prefix}:{canonical-evaluated-key}
```

固定资源的 `key` 为空时，规范逻辑 Key 就是 `{prefix}`，不追加空分隔段。

Starter 现有 Namespace 机制再生成物理 Key：

```text
{service-prefix}:{prefix}:{canonical-evaluated-key}
```

V1 不额外插入 `lock:` 段，以便批准的服务在迁移时保持既有互斥域。业务方不得把服务 Namespace 重复放入 `prefix`。

## 5. 服务级配置

资源锁继续依赖现有协调开关，并增加两个无默认值的服务级 Duration：

```yaml
redis:
  coordination:
    enabled: true
    resource-lock:
      wait-time: 500ms
      lease-time: 30s
```

约束：

- `wait-time` 必须大于或等于 0 且有限；
- `lease-time` 必须大于 0 且有限；
- 配置在应用启动时绑定，不支持运行期热刷新；修改后通过重启使单实例画像一致；
- 存在 `@ResourceLock` 时，协调能力关闭、Redisson Bean 缺失、任一配置缺失或非法都使启动失败；
- 没有任何 `@ResourceLock` 的服务可以保持协调能力关闭，不为形式创建连接；
- 服务必须记录选择依据，并保证 Lease 覆盖正常受保护操作和数据库事务的预期上界；这只是容量约束，不是 fencing 保证。

Platform 迁移时继续使用已经验证的 500ms Wait 和 30s Lease，除非另有专项决定。

## 6. 启动期 Fail-Fast

自动配置必须在 Application Context 完成刷新并对外进入 Readiness 前扫描实际 Spring Bean 的目标方法。每个 Annotation 逐项验证：

1. 所在 Bean 是启动期可预测、非 lazy 的 singleton，已纳入 Spring AOP，且实际代理包含适用于该方法的 Resource Lock Advisor；
2. 方法是 `public`、非 `static`、非 `final`，目标类非 `final`；
3. Annotation 声明在具体实现方法上，而不是只存在于接口；
4. 返回类型是同步结果，不是 `Future`、`CompletionStage`、`Callable`/`Runnable`、JDK 函数式或迭代遍历接口、Java `Stream/BaseStream`、Reactive Streams、Java `Flow`、Kotlin Flow、Kotlin suspend 或其他延迟执行类型；
5. Prefix 格式和长度合法；
6. Key 表达式完整匹配受限语法；
7. 参数名唯一存在且 Parameter Metadata 可用；
8. 一层属性存在、可读且不需要任意方法调用；
9. 表达式静态结果属于支持类型；
10. Redisson、Namespace、Wait 和 Lease 配置完整合法；
11. 实际代理使用唯一且对象身份匹配 Foundation 固定 Bean 的 Resource Lock Advisor/执行链，并位于所有适用事务 Advisor 外层；任何其他适用 Advisor 复用或另建 `ResourceLockMethodInterceptor` 都必须失败启动；
12. `prefix` 没有重复包含当前服务 Namespace，RedissonClient 是 Foundation 从同一套 Spring Data Redis 配置创建并登记来源的唯一单机客户端；唯一性覆盖 singleton、prototype 和 scoped 候选，启动验证后执行器冻结该客户端对象身份；NameMapper 可精确映射和反向映射每个逻辑 Key。

同一方法完全重复的 Annotation 是配置错误并在启动时拒绝。不同表达式在运行期解析为同一个逻辑 Key 时允许去重。

具体父类方法没有被 override 时可以沿用其声明；一旦子类 override，具体 override 必须完整复写全部祖先锁声明，否则启动失败，防止重写后静默丢锁。FactoryBean 自身不得声明锁，带锁 product 必须是 Readiness 前已初始化且完成代理校验的 singleton；Starter 不会为了扫描强制初始化无关 FactoryBean product。实现类型被隐藏在 lazy 工厂的接口返回类型之后时，Spring 无法在不实例化无关 lazy Bean 的情况下完成启动扫描，因此该形态不在支持范围；Starter 会在该 Bean 首次创建、任何业务方法执行前失败关闭。正常 Platform 消费者必须使用启动期可见的具体非 lazy singleton，从而在 Readiness 前完成表达式和代理校验。

校验失败抛出包含 Bean 类型、方法签名和 Annotation 序号的安全启动异常；可以写出 Prefix 和表达式源码，但不得求值或输出运行期参数。不得只记录 Warning、跳过该方法或把错误延迟到首次调用。

Spring AOP 无法可靠静态证明所有调用都经代理进入，因此消费者还必须用架构测试禁止对带 Annotation 方法的 `this` 自调用和实现类绕过代理调用。

## 7. 运行流程与多锁

Aspect 每次调用遵循固定流程：

1. 读取启动期已编译并校验的方法元数据；
2. 对全部 Annotation 求值并规范化 Key；
3. 在连接 Redis 前完成所有运行期格式和安全校验；
4. 生成完整逻辑 Key，去重后按 ASCII 字典序升序排列；
5. 使用一个服务级 `wait-time` 作为本次方法获取全部锁的总预算；
6. 按排序结果逐个获取，后续锁只使用剩余等待预算；
7. 全部成功后调用业务方法；
8. 无论业务返回或抛错，都按获取顺序的逆序释放。

Annotation 的源码顺序、Java Reflection 返回顺序和业务调用顺序都不参与排序。受限字符集保证所有 JVM 对同一逻辑 Key 得到相同顺序。

任一锁获取失败时，不调用业务方法，并逆序释放本次已取得的锁。相同线程在同一调用中解析出的重复 Key 只获取一次。V1 不鼓励嵌套调用另一个带 `@ResourceLock` 的公开方法；如确需组合，外层用例应一次声明完整资源集合，避免依赖 Redisson 可重入计数形成隐藏顺序。

## 8. AOP 与事务边界

`ResourceLockAspect` 是 Foundation 内部基础设施，不进入业务 `*-api`，也不依赖 MVC、审计、Repository 或 Mapper。

标准写入结构为：

```text
Controller / Listener
  -> Spring proxy
    -> ResourceLockAspect
      -> public Processor command
        -> TransactionTemplate
          -> Domain Service / Repository / Mapper
```

顺序不变量：

- 全部 Redis 锁在创建数据库事务、借出连接和取得数据库行锁之前获得；
- Interceptor 入口如果已有活动数据库事务，必须在解析动态 Key 和访问 Redis 前 Fail-Fast，禁止把取锁等待放进一个外层已开启的事务；
- 业务方法返回时，其内部 `TransactionTemplate` 已完成提交；抛错时已完成回滚；
- Redis 锁只在上述终态之后释放；
- Annotation 自身不创建事务，也不接受 `@Transactional` 作为替代；
- 若未来存在事务 Advisor，Resource Lock Advisor 的顺序必须在其外层，并有启动/架构测试；当前项目仍统一使用显式 `TransactionTemplate`。

复杂写用例把 Annotation 放在公开 Processor 方法。Controller 不承载锁或事务，Repository/Mapper 不感知 Redis。private helper、同类自调用、直接 `new` 出来的对象和绕过 Spring Proxy 的调用均不受支持。一个入口一次声明全部资源，不通过嵌套带锁调用或方法引用拼装锁集合。

Platform 的 Controller `@Audit` 位于入站边界，可以包住 Processor 的资源锁拒绝；Foundation Resource Lock 不调用审计 API，也不决定哪些拒绝应落审计。

## 9. 获取、异常与释放语义

### 9.1 获取

每个底层锁使用明确 Wait 和 Lease，不启用无限 Watchdog Lease。获取结果：

- 全部锁在总预算内取得：进入业务方法；
- 竞争导致预算耗尽：抛出 `ConcurrentRequestException("该资源正在处理其他请求，请稍后重试")`；
- `InterruptedException`，或 Redisson 包装的直接 Cause 为 `InterruptedException`：恢复线程中断位，抛出带 Cause 的 `ConcurrentRequestException`；
- 其他 Redis 连接、认证、超时、命令或协议异常：抛出保留 Cause 的 `ServiceUnavailableException`。

不能在 Redis 异常时执行未加锁业务，不能切换到 JVM 本地锁、另一个 Redis Client 或数据库通用锁形成第二互斥域。

### 9.2 业务异常

业务方法的返回值和异常保持原样。Aspect 不做自动重试，不把业务拒绝改成 Redis 错误，也不吞掉 Error。是否重试仍由拥有幂等契约的调用方决定。

### 9.3 释放

只对本次成功取得且仍由当前线程持有的锁调用普通 `unlock`，禁止 `forceUnlock`、先读 Owner 再删除 Key 或释放其他线程的锁。

释放期间的 `IllegalMonitorStateException` 和 Redis 异常只产生固定低基数 Warning/Metric，不能覆盖正常业务结果或原业务异常。日志可以包含安全 Prefix 和错误分类，不包含完整物理 Key、动态 Key 值、参数或请求体。有限 Lease 最终回收遗留锁；该回收不证明并发业务安全，数据库保护仍须生效。

若业务方法抛错且释放也失败，传播原业务异常，释放失败只作为诊断记录，不替换 Cause 链。

## 10. Redis-first 与数据库最终保护

采用 Annotation 前，每个用例必须写出“不变量—冲突事务—Redis 资源—数据库最终保护”四项。选择示例：

| 不变量类型 | Redis 的作用 | 必须保留或新增的数据库保护 |
|---|---|---|
| 同一行状态机 | 降低同资源并发和快速返回竞争 | `WHERE status/version` 条件更新，校验影响行数 |
| 业务唯一性 | 减少重复创建 | 唯一索引；只精确转换已识别的 Duplicate Key |
| 同一聚合多表写 | 在事务外串行同聚合命令 | 一个本地事务；聚合根 Version/CAS 或必要行锁 |
| 至少保留一个管理员等集合不变量 | 只协调已知命令 | 哨兵行/角色局部排他锁、计数条件更新或批准的隔离协议 |
| 授权检查到写入 | 降低并发撤权窗口 | 锁定/版本化 Actor 与授权依赖，或在最终写入原子复验 |
| 资金和额度守恒 | 降低热点冲突 | 数据库余额/Position 行锁、CAS、唯一事实和事务内重算 |

若 Redis Lease 在业务事务提交前到期，第二执行者可以取得相同 Redis Key。此时数据库保护必须令两个事务得到一个合法串行结果、一个明确并发冲突，或另一个由业务设计批准的安全结果。测试只证明 Redis 互斥而没有制造 Lease 重叠，不能证明最终不变量。

## 11. 安全与可观测性

- 物理 Key 自动包含服务 Namespace；跨服务不能借同名 Prefix 假设共享互斥域；
- 动态 Key 不进入普通日志、异常消息、Trace、Metric Label 或审计属性；
- Metric 只使用 Prefix、`acquired/contended/interrupted/unavailable/release-failed` 等固定低基数维度；
- 可以记录等待时长和持锁时长直方图，但不记录资源 ID；
- 连接配置、密码和 SSL 继续复用 `spring.data.redis.*`，不新增凭据入口；
- 不使用 `RKeys`、`KEYS *`、`FLUSHDB`、`forceUnlock` 或绕过 Namespace 的原始连接；
- Prefix/表达式元数据属于代码配置，动态值仍按最小暴露处理。

## 12. Platform 迁移约束

Platform Support 当前使用服务内 `OperationsResourceLock`，并由 Processor 手工把 Redis 锁包在 `OperationsTransactionExecutor` 外。公共能力的迁移必须同时遵守 [`2026-09-05-platform-support-resource-lock-optimization-design.md`](2026-09-05-platform-support-resource-lock-optimization-design.md) 中已批准的具体资源和数据库锁矩阵；Foundation 本身不拥有 Platform 的锁 Key 选择。

迁移必须保持：

- Account、Role、Role Code 等同一实际资源的跨入口互斥域；Create Account 改用带域分隔的键控摘要，Gateway Target/Route 改为共用单个 Catalog Key，不保留旧的原始幂等 Key 或内层 Target/Route Key；
- Wait 500ms、Lease 30s；
- 竞争/中断映射 409，Redis 不可用映射 503；
- 提交/回滚后释放，释放失败不覆盖业务结果；
- Expected Version、唯一索引、最后管理员保护、Actor/Role 数据库锁和其他已证明必要的局部数据库保护；
- Foundation 保留 Repeatable Annotation 及确定排序能力，但 Platform 当前每个命令只声明一把实际资源锁，不为复制旧嵌套包装而人为声明多锁；
- 锁拒绝发生在业务事务前，现有 Controller 尽力审计是否记录该拒绝仍由审计合同决定。

如果公共确定排序或 Platform 的新 Key 与当前嵌套顺序/互斥域不同，迁移必须先证明所有同资源入口统一使用新合同，避免滚动期间形成两个互不可见的互斥域；必要时采用单版本切换或经证明的兼容阶段。

## 13. 测试与验收

### 13.1 Foundation 自动测试

使用仓库 `local-environment` 的真实 Redis，不使用 Testcontainers、嵌入式 Redis 或方法级行为 Stub。至少覆盖：

- 单锁成功、业务返回和业务异常原样传播；
- `#param`、Record Component、JavaBean getter 的合法解析；
- 每一种禁止表达式和不支持静态类型均在启动期失败；
- coordination 关闭、缺少 Wait/Lease、无 Parameter Metadata、不可代理方法在启动期失败；
- `null`、空值、非法字符和超长运行期值在取锁前失败且不回显；
- 同 Key 跨线程/JVM 竞争，不同 Key 并行；
- 多 Annotation 与不同声明顺序得到相同获取顺序；
- 重复运行期 Key 去重，部分获取失败后逆序清理；
- 总 Wait 预算不会随锁数量线性放大；
- Lease 到期后新 Owner 可进入，旧 Owner 不会释放新 Owner 的锁；
- 等待中断恢复中断位；Redis 未监听端口、错误凭据等真实 Fail-Closed；
- 业务成功/失败叠加释放异常时，业务结果/原异常不被覆盖；
- Namespace 只添加一次，日志和指标不包含动态 Key。

并发断言使用 Barrier、Latch、受控事务断点或 Awaitility，不用固定 `Thread.sleep` 证明时序。

### 13.2 消费者服务测试

每个迁移服务从真实公开入口经过 Controller、Spring Proxy、带 Annotation 的 Processor、`TransactionTemplate`、Domain Service 和本地数据库。至少证明：

- 锁在事务开始和数据库连接占用之前取得；
- 提交与回滚完成后才释放；
- 同资源不同动作进入同一 Key，不同资源可并行；
- Wait 超时、Redis 不可用和中断时业务 SQL 未执行；
- Lease 过期造成真实重叠时，数据库 CAS/唯一索引/局部锁仍保护业务不变量；
- 多锁入口没有死锁或部分执行；
- 架构测试禁止业务本地同义锁包装、直接普通 `RedissonClient#getLock`、private/final/self-invocation 绕过和 Annotation 放错层；
- 测试结束后只按本次唯一 Namespace 清理，无遗留 Key，不触碰其他运行。

Platform 迁移还必须重跑其账号跨动作、角色删除/分配、最后管理员、创建唯一键、Lease 过期、中断、Redis Fail-Closed、事务回滚、审计拒绝和完整覆盖率门禁。公共 Foundation 测试通过不能替代这些业务测试。

## 14. 文档与状态验收

实现变更应同步但不得复制职责：

- `TECHNOLOGY_STACK.md §6.7` 增加公共能力与依赖边界；
- `DEVELOPMENT_CONVENTIONS.md §5.1` 把“不得新建通用锁框架”收窄为“业务服务不得自建同义能力”，并保留锁选择/数据库保护规则；
- `JAVA_PROJECT_STRUCTURE.md` 增加 API 包、Processor、代理和事务位置；
- `UNIT_TEST_GUIDE.md` 增加 Annotation、真实 Redis、Lease 重叠和架构门禁；
- 根 `AGENTS.md` 与 `agent-docs/README.md` 只增加路由；
- `paytech-foundation/README.md` 记录实际 Maven/API/配置示例和可复现命令；
- Platform 专项计划只增加窄后继链接，不重写历史 Key、测试数字或当时状态；
- `CURRENT_IMPLEMENTATION.md` 分开记录 Foundation 实现/验证/发布和 Platform 迁移/验证/部署。

在代码尚未存在时，状态台账只能写 Designed/Pending。只有源码和自动配置已进入当前主工作区后才能写 Implemented；只有真实 `mor-local` Redis 和消费者数据库门禁实际通过后才能写 Verified；本地 Maven 安装或 Nexus 发布不等于 Deployed。

## 15. 状态归属

本文只冻结设计合同，不保存易过期的实现快照。Foundation 源码、真实 Redis 验证、本地 Maven 安装或远端制品发布，
以及 Platform Support 迁移、验证与部署必须分别登记在
[`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)；其中任一状态都不能从本设计已确认推导。
