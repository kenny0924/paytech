# Platform Controller Best-effort Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Platform 已有 Operations HTTP 操作审计改为 Controller 声明、业务现场采集、后台独立事务尽力写入，使可恢复的审计故障不改变业务结果。

**Architecture:** Controller 切面管理调用作用域，显式业务事务边界报告真实事务完成状态，二者通过一次性完成门闩汇合。安全不可变事件进入有界内存队列，由一个后台线程通过独立小连接池及 MyBatis 写会话追加现有审计链；业务查询继续使用主库会话。

**Tech Stack:** 仓库选定的 Java 25、Spring Boot 4.0.8、Spring MVC/AOP、MyBatis starter 4.0.1、MySQL、Micrometer、JUnit Jupiter、RestTestClient、Awaitility；使用现有 Maven Wrapper/BOM，不自行升级依赖。

**Spec:** [已确认正式设计](../specs/2026-09-05-platform-controller-best-effort-audit-design.md)，精确取代范围见[审计决策](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)。执行前完整阅读两者及本计划。

## Global Constraints

- Controller 注解仅放实现方法，不进入 `platform-support-api`；不新增公共审计 JAR、HTTP 契约、表、模型字段或 Schema 迁移。
- 接受进程崩溃、队列满和审计故障造成永久丢失；不增加 MQ、Outbox、补偿事件、自动重试、落盘或历史补写。
- 账号锁定、密码失败计数、Session/Refresh 撤销、权限变更、业务幂等、资源锁和 CAS 仍是必须成功的业务事务内容。
- 后台写线程 / 专用连接池最大连接各 1；专用池最小空闲连接为 0；队列容量 256 个冻结事件。
- 单事件安全字段总 UTF-8 数据量 64 KiB，包含元数据与属性，集合最多 256 个属性；单属性值 4 KiB，且不得超过现有列的可存储长度。
- 连接获取 / 建连 / InnoDB 锁等待各最多 1 秒；SQL / 审计事务 / 网络读取分别 2 秒 / 3 秒 / 3 秒；正常停机排空最多 5 秒。
- 非阻塞 offer；禁止 CallerRunsPolicy、公共 ForkJoinPool、无界线程/队列和请求线程写入回退。
- 冻结事件不得持有 Request、JoinPoint、Throwable、Supplier、业务 DataObject、可变集合或密码/Token；不序列化完整请求或响应。
- 继续使用显式 TransactionTemplate，不引入 @Transactional；所有业务 Service/Domain Service 和 Repository 保留 interface + Impl。
- 实现时遵守[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[模块边界](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)、[文档治理](../../../agent-docs/DOCUMENT_GOVERNANCE.md)及最近目录规则。
- auto 只使用仓库自有 Loopback MySQL `127.0.0.1:13306/mor_platform`、Redis `127.0.0.1:16379`；不操作容器生命周期，不初始化 Schema，不连接 mor-test/Nacos/其他服务，不使用方法 Stub/Mock/Spy。
- 检查并保留两个仓库的已有工作区修改。所有任务以验证检查点替代 Git commit；本计划不授权建分支、提交、推送、部署或修改外部资源。

---

## 文件地图与协作边界

以下路径均相对 Code-MoR 工作区根目录。为避免重复长前缀，后文使用这四个**确定的目录别名**，不是待选择路径：

- `W` = `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support`
- `R` = `platform-support-service/platform-support-web/src/main/resources`
- `T` = `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support`
- `P` = `platform-support-service`

| 文件 | 操作与唯一职责 |
|---|---|
| `W/domain/audit/AuditModels.java` | 新建；安全值记录及内部终态枚举 |
| `W/domain/audit/AuditCompletion.java` | 新建；调用与事务终态汇合、结果选择、最多一次取出 |
| `W/domain/audit/AuditChainClosedException.java` | 新建；后台封闭链拒写的固定技术信号，不是 HTTP 业务错误 |
| `W/core/audit/AuditContext.java` | 新建；作用域栈、事务观察、采集边界，不访问数据库 |
| `W/core/audit/AuditDispatcher.java` | 新建；有界队列、单 worker、生命周期及有界指标 |
| `W/configuration/OperationsAuditProperties.java` | 新建；已批准资源预算及配置校验 |
| `W/configuration/OperationsAuditPersistenceConfiguration.java` | 新建；专用池/事务管理器/MyBatis 会话的显式装配 |
| `W/configuration/PersistenceConfiguration.java` | 修改；主 Mapper/事务绑定保持唯一，复用已有类型注册 |
| `W/ext/rest/audit/Audit.java`、`W/ext/rest/audit/AuditAspect.java` | 新建；声明元数据、启动校验、Controller 调用作用域 |
| `W/domainservice/service/OperationsAuditService.java`、`W/domainservice/service/impl/OperationsAuditServiceImpl.java` | 修改；从数据库追加改为类型化内存采集，保留安全属性编码职责 |
| `W/domainservice/service/OperationsAuditWriter.java`、`W/domainservice/service/impl/OperationsAuditWriterImpl.java` | 新建；从现有实现迁出 Hash 和独立原子写入，不重新设计格式 |
| `W/domainservice/repository/OperationsAuditWriteRepository.java`、`W/domainservice/repository/impl/OperationsAuditWriteRepositoryImpl.java` | 新建；专用会话的六个写能力 |
| `W/domainservice/repository/OperationsAuditRepository.java`、`W/domainservice/repository/impl/OperationsAuditRepositoryImpl.java` | 修改；只保留 events/eventCount/event/attributes 四个主会话查询 |
| `W/core/dal/mapper/OperationsAuditMapper.java`、`R/mapper/OperationsAuditMapper.xml` | 修改；同一 SQL 权威，区分发生/接收时间，删除错误归属的 Tenant 锁 |
| `W/domainservice/service/OperationsSessionService.java`、`W/domainservice/service/impl/OperationsSessionServiceImpl.java` | 修改；接回 Tenant 业务资格保护；验证 Session 后提供可信审计身份 |
| `W/domainservice/repository/OperationsIdentityRepository.java`、`W/domainservice/repository/impl/OperationsIdentityRepositoryImpl.java`、`W/core/dal/mapper/OperationsIdentityMapper.java`、`R/mapper/OperationsIdentityMapper.xml` | 修改；承接原 lockTenant SQL，不改变锁顺序 |
| `W/service/processor/OperationsAuthenticationProcessor.java`、`OperationsManagementProcessor.java`、`GatewayConfigurationManagementProcessor.java` | 修改同目录三文件；观察真实业务事务，删除同步失败审计包装，保留业务编排 |
| `W/domainservice/service/impl/OperationsAuthenticationServiceImpl.java`、`OperationsAccountServiceImpl.java`、`OperationsAuthorizationServiceImpl.java`、`OperationsRoleServiceImpl.java`、`GatewayConfigurationManagementServiceImpl.java` | 修改同目录五文件；现场安全事实、重放标记，主业务不写审计表 |
| `W/ext/rest/authentication/OperationsAuthenticationController.java`、`OperationsManagementController.java`、`OperationsAuthorizationController.java`；`W/ext/rest/gatewayconfiguration/GatewayConfigurationManagementController.java` | 修改四个 Controller；29 个方法的声明，不增加业务逻辑 |
| `P/platform-support-web/pom.xml`、`R/application.yml` | 修改；BOM 管理的 AspectJ starter 及审计资源预算，不修改业务 readiness 组 |
| `T/auto/operations/OperationsAuditCompletionAutoTest.java` | 新建；纯算法状态机/快照/大小边界测试 |
| `T/auto/operations/OperationsAuditBestEffortHttpAutoTest.java` | 新建；真实 HTTP 的队列、锁、原子失败、恢复和连接绑定回归 |
| `T/auto/operations/OperationsAuditConnectionFailureHttpAutoTest.java` | 新建；启动前仅配置审计池连接失败，验证 HTTP 与 readiness |
| `T/auto/operations/OperationsAuthenticationHttpAutoTest.java`、`OperationsDirectoryRefactoringHttpAutoTest.java`；`T/auto/gatewayconfiguration/GatewayConfigurationManagementHttpAutoTest.java` | 修改现有业务级回归；异步等待、旧成功/拒绝/快照/幂等契约 |
| `T/test/operations/OperationsTestMapper.java`、`OperationsFixtureMocker.java`、`OperationsHttpChecker.java`、`OperationsSqlProbe.java`、`OperationsSqlBarrier.java` | 修改现有夹具；精确故障状态、只读观测及释放后清理 |
| `T/test/operations/OperationsAuditProbe.java` | 新建；有界等待审计完成/排空，禁止实现生产业务或替换返回 |
| `T/auto/architecture/PlatformArchitectureAutoTest.java`、`T/auto/operations/OperationsPersistenceContractAutoTest.java`、`T/auto/gatewayconfiguration/GatewayConfigurationManagementPersistenceContractAutoTest.java` | 修改；注解范围、绑定、SQL 与接口结构门禁 |
| `agent-docs/CURRENT_IMPLEMENTATION.md` | 实施后按实际证据更新 AUDIT-01；不复制实施状态到设计文档 |

一个代理只编辑其分配的文件；共享接口冻结后，允许另一个代理准备故障测试。不要在同一个 Platform target 目录并发运行 Maven。Controller/采集器/Processor 切换由同一负责人整合，避免中间版本双写或漏写。

## 执行前置：先保存真实 HTTP 失败证据

本节在修改生产代码前执行；这是 RED 证据，不是已经通过的测试。Task 1–3 可独立运行聚焦门禁，但不得在 Task 4 切换完成前声称整个分支已绿。

- [ ] 检查本地规则和状态，确认 JDK 25、Wrapper 可用及上述 Loopback 依赖前置条件。当前用户已允许读取 local 环境凭据；只在测试进程内传递已有值，不打印、不写入计划、源码或日志；缺依赖时按现有明确 setup 错误停止该项，不能改连其他环境。

```bash
git status --short
git -C platform-support-service status --short
java -version
cd platform-support-service
./mvnw -version
```

- [ ] 在现有 `OperationsTestMapper` 新增只作用于本测试持有链的状态准备方法；不创建 CHECK/Trigger/权限或修改共享初始化数据。

```java
public void sealOwnedAuditChain(long chainId) {
    require(jdbc.update("UPDATE t_audit_chain SET status='SEALED' "
        + "WHERE chain_id=? AND partition_date='2001-02-03' AND status='OPEN'", chainId) == 1);
}
```

- [ ] 在现有 `OperationsAuthenticationHttpAutoTest` 增加以下方法，使用该类已有 `fixtures`、`mapper`、`check`、`client` 和常量；不新造假的认证服务。

```java
@Test
void auditFailureDoesNotRollbackWrongPasswordCounter() {
    var account = fixtures.mock(OperationsFixtureBuilder.administrator());
    mapper.sealOwnedAuditChain(fixtures.chainId());
    String requestId = fixtures.requestId();
    var response = client.post()
        .uri("/platform-support/authentication/v1/operations/login")
        .header("X-Request-Id", requestId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new LoginRequestBody(account.email(), INITIAL_PASSWORD + "-wrong"))
        .exchange();
    Assertions.assertAll(
        () -> check.rejected(response, "OPERATIONS_AUTHENTICATION_FAILED", requestId),
        () -> Assertions.assertEquals(1,
            mapper.account(account.accountId()).get("consecutive_password_failures")));
}
```

- [ ] 从 `P` 运行并记录真实失败，而不是把推断写成执行结果：

```bash
./mvnw -q -pl platform-support-test -am -Dtest=OperationsAuthenticationHttpAutoTest#auditFailureDoesNotRollbackWrongPasswordCounter -Dsurefire.failIfNoSpecifiedTests=false test
```

预期旧实现返回的公共错误被审计写入异常替换，且失败计数未提交。若不是这个失败，先使用 systematic-debugging 检查链状态、错误码和事务事实，再调整最小复现；不要通过删除断言或停用测试制造 RED/GREEN。

## Task 1：可冻结的安全事实与一次性终态合并

**Files:** 新建文件地图中的 `AuditModels`、`AuditCompletion` 和 `OperationsAuditCompletionAutoTest`。

**Interfaces:** 不依赖 Spring、Servlet、DataObject 或 JDBC。`AuditModels` 中定义以下值模型；所有 enum 常量间空一行，记录的 compact constructor 先检查大小再防御复制 List/Set，非法值抛 IllegalArgumentException，由 Collector/Context 自身隔离边界丢弃计数。`Fact` 只表达现场事实，`Event` 才是可排队事件。构造和格式化均在隔离边界内，不能让参数求值异常先于该边界传播到业务。

```java
public record Identity(String actorId, Long tenantId) {}
public record Target(String type, String id) {}
public record Attribute(String key, String value) {}
public record Invocation(String action, Target fallback, boolean recordSuccess,
    String requestId, Instant startedAt, Set<String> allowedResourceTypes) {
    public Invocation(String action, Target fallback, boolean recordSuccess,
        String requestId, Instant startedAt) {
        this(action, fallback, recordSuccess, requestId, startedAt, Set.of(fallback.type()));
    }
}
public record Fact(Identity identity, Target target, Result result,
    List<Attribute> attributes, Instant occurredAt) {}
public record Event(Identity identity, String action, Target target, Result result,
    List<Attribute> attributes, String requestId, Instant occurredAt) {}
public record StatusVersion(String status, int version) {}
public record GatewayChange(Target target, String sessionId, StatusVersion before,
    StatusVersion after, String reason, String idempotencyReference) {}
```

内部枚举为 `Result { SUCCEEDED, DENIED, FAILED }`、`CallOutcome { RETURNED, DENIED, FAILED }`、`TransactionOutcome { NOT_STARTED, ACTIVE, COMMITTED, ROLLED_BACK, UNKNOWN }`。内部观察状态不映射数据库；写库使用原 Code 字符串。`AuditCompletion` 公开契约：

```java
public AuditCompletion(Invocation invocation);
public synchronized void identify(Identity identity);
public synchronized void target(Target target);
public synchronized void observeTransaction();
public synchronized void transactionCompleted(TransactionOutcome outcome);
public synchronized void record(Fact fact);
public synchronized void replay();
public synchronized void discard();
public synchronized void callCompleted(CallOutcome outcome, String safeReason);
public synchronized Optional<Event> drain();
```

- [ ] 先写“回滚回调先于最终业务拒绝”的纯算法测试；不使用 Spring Stub。

```java
@Test
void waitsForInvocationWhenRollbackNotificationArrivesFirst() {
    var completion = new AuditCompletion(new Invocation("OPERATIONS_ROLE_UPDATE",
        new Target("ROLE", "42"), true, "audit-test-request", Instant.EPOCH));
    completion.observeTransaction();
    completion.transactionCompleted(TransactionOutcome.ROLLED_BACK);
    Assertions.assertTrue(completion.drain().isEmpty());
    completion.callCompleted(CallOutcome.DENIED, "CONCURRENT_REQUEST");
    var event = completion.drain().orElseThrow();
    Assertions.assertEquals(Result.DENIED, event.result());
    Assertions.assertEquals(List.of(new Attribute("reason", "CONCURRENT_REQUEST")), event.attributes());
    Assertions.assertTrue(completion.drain().isEmpty());
}
```

- [ ] 运行聚焦 RED，预期新类型缺失；不要先实现再补证据。

```bash
./mvnw -q -pl platform-support-test -am -Dtest=OperationsAuditCompletionAutoTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 实现门闩。仅调用和事务都终结时尝试 drain；用 synchronized 保护一次消费，重复通知幂等。按以下顺序选择结果，丢弃意味着消费完成而非永远等待。

```text
call 未终结 或 transaction == ACTIVE        => 等待
discarded 或 replay                       => 不产出
transaction == UNKNOWN                    => FAILED + AUDIT_COMMIT_OUTCOME_UNKNOWN
transaction == COMMITTED 且存在明确 Fact    => 保留该事实（含 DENIED）
transaction == ROLLED_BACK                 => 丢掉成功快照，按调用/明确拒绝产出安全尝试
没有事务且明确 DENIED / FAILED             => 安全尝试
无已提交成功事实且 CallOutcome 非 RETURNED   => DENIED 或 FAILED + 固定原因
普通成功查询 / 缺少明确成功事实              => 不合成成功事件
```

提交后的成功事实即使 Controller 后续转换失败也保持 SUCCEEDED；拒绝的详细原因优先于通用 HTTP 错误码。回滚失败尝试只保留安全 reason/资源/身份，不保留已回滚成功 before/after。UNKNOWN 使用固定 reason，不推断必然回滚。没有可信身份采用现有 UNKNOWN 编码。正常查询 `recordSuccess=false` 不产生成功事件。

- [ ] 增加提交后异常及未知提交的测试，再补双顺序、无事务、正常返回 DENIED、重放、重复通知、并发 drain 和快照防御复制的参数化用例。

```java
@Test
void committedFactSurvivesLaterResponseFailure() {
    var completion = new AuditCompletion(new Invocation("OPERATIONS_ROLE_CREATE",
        new Target("ROLE", "NEW_ROLE"), true, "audit-test-request", Instant.EPOCH));
    completion.observeTransaction();
    completion.record(new Fact(new Identity("7", 1L), new Target("ROLE", "42"),
        Result.SUCCEEDED, List.of(new Attribute("reason", "新增岗位")), Instant.EPOCH));
    completion.transactionCompleted(TransactionOutcome.COMMITTED);
    completion.callCompleted(CallOutcome.FAILED, "AUDIT_SYSTEM_FAILURE");
    Assertions.assertEquals(Result.SUCCEEDED, completion.drain().orElseThrow().result());
}

@Test
void unknownCommitNeverBecomesSuccess() {
    var completion = new AuditCompletion(new Invocation("OPERATIONS_ROLE_CREATE",
        new Target("ROLE", "NEW_ROLE"), true, "audit-test-request", Instant.EPOCH));
    completion.observeTransaction();
    completion.transactionCompleted(TransactionOutcome.UNKNOWN);
    completion.callCompleted(CallOutcome.FAILED, "AUDIT_SYSTEM_FAILURE");
    var event = completion.drain().orElseThrow();
    Assertions.assertEquals(Result.FAILED, event.result());
    Assertions.assertEquals(List.of(new Attribute("reason", "AUDIT_COMMIT_OUTCOME_UNKNOWN")),
        event.attributes());
}
```

- [ ] 实现 `AuditModels.freeze(Fact fact)` 和 `AuditModels.freeze(Event event)` 返回 `Optional`；检查有界标量、总字节、属性数量、每个 value 的 UTF-8 与列长度后复制。格式化权限列表前先检查输入个数并增量预算，不能先 join 巨串再截断。覆盖 256/257 属性、4096/4097 字节、多字节字符、总量65536边界；超限整条拒收，不截断集合。返回空由调用层计数，不能抛业务异常。

```java
public static Optional<Fact> freeze(Fact fact);
public static Optional<Event> freeze(Event event);
```

- [ ] 重跑本任务聚焦测试并检查 `git diff --check`。完成标准：状态机和冻结规则有独立失败/通过证据，不声称 HTTP RED 已修复。

## Task 2：独立审计持久化，保持业务数据源和锁归属

**Files:** 文件地图中的 Properties/Persistence 配置、Writer/WriteRepository、原审计 Repository/Mapper、Identity/Session 的锁方法及持久化测试。

**Interfaces:** `OperationsAuditWriter.write(Event event)`；WriteRepository 承接原 `ensureChain/lockChain/insertEvent/insertAttribute/advanceChain/insertSecurity` 六个方法。原参数顺序和 MyBatis 参数名保持，`insertEvent/insertSecurity` 在原 `Instant now` 后新增 `Instant receivedAt`；原 `now` 明确表示 occurredAt。其他创建/更新时间使用 receivedAt。Security 表没有 received_time，只分离其 occurred_time 和 create_time，不新增列。`OperationsSessionService.lockTenant(Long tenantId)` 调用 IdentityRepository 的 `Long lockTenant(long tenantId)`。

本任务先并列引入未接入的新写入器，保证中间状态可编译；旧 Repository 的六个写方法和旧 lockTenant 保留至 Task 4 一次性切换后删除。旧 insertEvent/insertSecurity 适配新 Mapper 参数时临时将原 now 同时传给 occurred/received，不让未切换的业务缺方法或双写。Task 2 新写入器绝不调用业务 Tenant 锁。

- [ ] 在持久化契约测试锁定 Bean/Mapper 归属，再通过真实 HTTP 用例的 SQL Probe 验证业务 SQL 仍走主连接，审计查询也仍走主连接。对 XML 分别断言 occurred_time/received_time 使用不同参数，Task 4 完成后禁止恢复 Tenant 锁到 Audit Mapper。

```java
Assertions.assertNotSame(context.getBean("dataSource"), context.getBean("auditWriteDataSource"));
Assertions.assertNotSame(context.getBean("transactionManager"), context.getBean("auditWriteTransactionManager"));
var main = context.getBean("sqlSessionTemplate", SqlSessionTemplate.class);
var audit = context.getBean("auditWriteSqlSessionTemplate", SqlSessionTemplate.class);
Assertions.assertNotSame(main.getSqlSessionFactory().getConfiguration(),
    audit.getSqlSessionFactory().getConfiguration());
Assertions.assertSame(context.getBean("auditWriteDataSource"),
    audit.getSqlSessionFactory().getConfiguration().getEnvironment().getDataSource());
```

上述 `context` 为当前真实 `@SpringBootTest` 注入的 ApplicationContext，不能新建未取得全局测试隔离锁的应用上下文。配置 Bean 断言放现有 Operations HTTP 类，XML/架构断言放对应纯契约类。

- [ ] 运行 `OperationsPersistenceContractAutoTest` 和对应新增 HTTP 配置用例，记录 Bean 不存在的 RED。增加专用配置和同库池，所有辅助 Bean 使用 `@Bean(defaultCandidate=false)` 与明确名称，不只加 @Primary。

```java
@Bean(defaultCandidate = false)
HikariDataSource auditWriteDataSource(DataSourceProperties properties, Environment environment) {
    HikariDataSource pool = properties.initializeDataSourceBuilder()
        .type(HikariDataSource.class).build();
    Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(pool));
    pool.setPoolName("platform-operations-audit");
    pool.setMaximumPoolSize(1);
    pool.setMinimumIdle(0);
    pool.setInitializationFailTimeout(-1);
    pool.setConnectionTimeout(1000);
    pool.setValidationTimeout(500);
    pool.addDataSourceProperty("connectTimeout", "1000");
    pool.addDataSourceProperty("socketTimeout", "3000");
    pool.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout = 1");
    return pool;
}
```

使用 Boot 4 的 `org.springframework.boot.jdbc.autoconfigure.DataSourceProperties`。上述数字取自 `OperationsAuditProperties` 的已批准默认值；属性类只暴露现有预算，不新增业务开关或第二份凭据。检查宿主连接初始化配置：必须保留现有 UTC 语义及安全属性，合并已存在的 sessionVariables/初始化 SQL，不能静默覆盖。禁止对运行中的主 Hikari 池使用浅拷贝再修改共享 Properties。

- [ ] 装配专用 `JdbcTransactionManager`、独立 Configuration/SqlSessionFactory/SqlSessionTemplate。只加载原 `classpath*:mapper/OperationsAuditMapper.xml`，调用现有 `persistenceConfigurationCustomizer` 注册 Enum/Instant handlers，设置 defaultStatementTimeout=2；注册所有现有 MyBatis Interceptor Bean，使生产及测试 Probe/Barrier 同样观察审计会话。

```java
@Bean(defaultCandidate = false)
JdbcTransactionManager auditWriteTransactionManager(
    @Qualifier("auditWriteDataSource") DataSource dataSource) {
    return new JdbcTransactionManager(dataSource);
}

@Bean(defaultCandidate = false)
SqlSessionTemplate auditWriteSqlSessionTemplate(
    @Qualifier("auditWriteSqlSessionFactory") SqlSessionFactory factory) {
    return new SqlSessionTemplate(factory);
}
```

`auditWriteSqlSessionFactory` 同样 `defaultCandidate=false`，只注入专用 DataSource 和指定 customizer。主 `@MapperScan` 显式 `sqlSessionTemplateRef="sqlSessionTemplate"`；Snapshot 的 TransactionTemplate 与三个 Processor 显式注入 `@Qualifier("transactionManager")`。不要创建第二个无限定通用 TransactionTemplate Bean。辅助组件与当前 `operations.authentication.enabled=true` 条件一致。

- [ ] WriteRepositoryImpl 通过限定的专用 SqlSessionTemplate `getMapper(OperationsAuditMapper.class)` 取得代理，原读 Repository 保持主扫描 Mapper。将旧 AuditServiceImpl 的链/hash/写入算法迁入 Writer，使用自身的 TransactionTemplate；不改 Canonical 字段顺序、空值/分隔编码、历史单属性兼容分支或 hash 算法。

```java
public OperationsAuditWriteRepositoryImpl(
    @Qualifier("auditWriteSqlSessionTemplate") SqlSessionTemplate template) {
    mapper = template.getMapper(OperationsAuditMapper.class);
}

public OperationsAuditWriterImpl(Clock clock, IdGenerator ids, ObjectMapper json,
    @Qualifier("auditWriteTransactionManager") PlatformTransactionManager manager,
    OperationsAuditWriteRepository repository) {
    this.clock = clock;
    this.ids = ids;
    this.json = json;
    this.repository = repository;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(3);
}
```

IdGenerator 使用现有 `com.paytech.foundation.id.IdGenerator`，ObjectMapper 使用 `tools.jackson.databind.ObjectMapper`。`write(Event)` 中使用 `transaction.executeWithoutResult` 一次完成 ensure/lock/insert/attributes/advance/security。发生日取 event.occurredAt 的 UTC 日期，receivedAt 由 Clock 在写入开始取得；发生/接收时间均沿用微秒精度，避免 Hash 输入与数据库精度不符。SEALED/ARCHIVED 拒写，不重开。异常由后台分发器消费，Writer 不内部自动重试。

- [ ] 将原 `lockTenant` XML 的同等 SQL 加入 Identity Mapper，并由 SessionService 按现有 Tenant 存在性/资格错误规则调用；替换 AuthenticationService 中四处调用，保持原业务事务位置、锁序和重读。旧 Audit Mapper/Service 的同步写入依赖保留至 Task 4 删除，本任务不提前破坏旧生产路径。

```java
// 在原 audit.lockTenant 调用位置替换，不调整相邻资格检查的先后顺序。
sessions.lockTenant(member == null ? null : member.tenantId());
```

- [ ] 运行持久化/架构聚焦测试及现有 Tenant/Account 竞争业务测试。Task 4 完成前原审计采集实现仍可保留旧写路径作为过渡，不能双写；本任务新 Writer 不在请求线程挂接。审查点：主自动配置未退让、未复制 SQL、没有业务锁被异步化。

## Task 3：作用域、事务观察、有界分发与 Controller 元数据

**Files:** `AuditContext`、`AuditDispatcher`、`Audit.java`、`AuditAspect.java`、Properties、Web POM；扩展 Probe 和测试基础设施。

**Interfaces:** Context 是技术组件，Domain Service 可调用，不依赖 ext/rest 类型。定义 `AuditContext.Scope implements AutoCloseable`，持有前一个作用域并在 close 恢复；以下方法均捕获自身可恢复 RuntimeException 并丢弃/计数，不捕获后再重跑业务。

```java
public Scope open(Invocation invocation);
public void capture(Runnable auditCapture);
public void observeTransaction();
public void identify(Identity identity);
public void target(Target target);
public void record(Fact fact);
public void replay();
public void complete(CallOutcome outcome, String safeReason);
public void discard();
```

`AuditDispatcher.offer(Event event)` 返回 boolean；`pendingCount()` 返回含排队和正在执行的未完成事件数量，供关闭和观测；`close()` 停止接收并有界排空。事务尚未终结的作用域由 Context 单独记录 active 数量，不能以 queue.size==0 宣称整个审计空闲。这些数字也是运行诊断，不是测试替换接口。

- [ ] 在状态机测试增加大小超限和集合变更 RED，在真实 HTTP 测试骨架加入“请求返回后后台仍被 SQL 屏障阻塞”的断言；骨架复用现有 Loopback 前置检查和全局隔离 Listener，不再实现业务 Controller。

```java
List<Attribute> source = new ArrayList<>();
source.add(new Attribute("reason", "冻结前"));
var frozen = AuditModels.freeze(new Fact(new Identity("7", 1L),
    new Target("ROLE", "42"), Result.SUCCEEDED, source, Instant.EPOCH)).orElseThrow();
source.set(0, new Attribute("reason", "冻结后"));
Assertions.assertEquals("冻结前", frozen.attributes().getFirst().value());
```

- [ ] 增加 BOM 管理的 AOP 依赖，声明只支持安全标量绑定的注解。不要复制到 API 接口。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {
    String action();
    String resourceType();
    String resourceIdParameter() default "";
    String fallbackResourceId() default "UNKNOWN";
    String[] alternateResourceTypes() default {};
    boolean recordSuccess() default true;
}
```

启动校验 29 个现有方法：绑定必须精确匹配真实方法参数名且为 String/数值安全标量；禁止 SpEL/对象路径/任意 toString。Login/Logout 允许 USER_ACCOUNT/AUTH_SESSION，Authorize 保留通用拒绝 USER_ACCOUNT 与现场拒绝 ROUTE 的已有双资源类型；其余动作只接受其既有类型。Aspect 将默认与 alternateResourceTypes 的有限集合传入 Invocation.allowedResourceTypes，Context 在 record/target 时校验。大小/字符校验不通过用已批准 fallback，不把未验证 actor Header 当可信 Identity。来自 body 的 routeCode 在现有 Processor/Domain 取得后调用 `target(Target)`，不做注解深层取值。

- [ ] 实现 Context 的事务桥：open 时观察已有事务；每个实际业务 transaction.execute 回调入口再次 observe。按当前实际事务注册一个 synchronization，不因同一上下文的重复观察注册两次。注册失败使本次审计丢弃，不是假定 NOT_STARTED。

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCompletion(int status) {
        try {
            completion.transactionCompleted(switch (status) {
                case STATUS_COMMITTED -> TransactionOutcome.COMMITTED;
                case STATUS_ROLLED_BACK -> TransactionOutcome.ROLLED_BACK;
                default -> TransactionOutcome.UNKNOWN;
            });
            completion.drain().ifPresent(dispatcher::offer);
        } catch (RuntimeException failure) {
            completion.discard();
        }
    }
});
```

`completion` 是 open 创建的当前 AuditCompletion，`dispatcher` 是注入的 AuditDispatcher；回调持有该对象而不是到回调时查 ThreadLocal。两边都可调用 drain，但只有一个成功。异常分支通过有限标签 `capture` 计数并安全限频诊断；不能输出 failure.getMessage。终态未到前保存冻结值，Scope close 只清理当前线程，不丢掉外层事务完成回调。

- [ ] 切面为实现方法建立 Invocation，读取现有 RequestIdFilter 属性，业务 `proceed()` 恰好一次。成功、FailException、其他异常分别完成 RETURNED/DENIED/FAILED；完整 Throwable 不留在 Context，公共业务 code 必须来自白名单而不是 exception message。

```java
try (AuditContext.Scope scope = context.open(invocation)) {
    try {
        Object result = joinPoint.proceed();
        context.complete(CallOutcome.RETURNED, null);
        return result;
    } catch (FailException failure) {
        context.complete(CallOutcome.DENIED, failure.getCode());
        throw failure;
    } catch (Throwable failure) {
        context.complete(CallOutcome.FAILED, "AUDIT_SYSTEM_FAILURE");
        throw failure;
    }
}
```

open/complete/close 自身必须 fail-open：元数据或采集失败返回 no-op Scope，但仍执行业务一次。严重 JVM 错误不作恢复保证；上面的 catch 仅用于保留原业务异常，不能将业务 Error 转成正常返回。嵌套 Scope 恢复前值并对同事件复用完成状态，不重复分发。

- [ ] Dispatcher 使用有界 `ArrayBlockingQueue<Event>`、专用单后台线程及原子 pending 计数；offer 与 shutdown 的接收状态转换在同一短临界区完成。计数在事件可被消费者看到前增加，写入 finally 递减，避免出队至 active++ 窗口产生假空闲。

```java
try {
    writer.write(event);
    written.increment();
} catch (RuntimeException failure) {
    writeFailed.increment();
} finally {
    pending.decrementAndGet();
}
```

`writer` 是 Task 2 的 OperationsAuditWriter；`written/writeFailed` 为 MeterRegistry Counter，`pending` 为 AtomicInteger。单条失败继续消费，不 retry。关闭先停接收再最多等待5秒，超时丢弃剩余队列、计数并停止线程；不能调用请求线程写入。工作线程 MDC 每次 finally 清理；挂起连接/SQL 还受独立超时限制，不宣称中断能绝对终止任意底层阻塞。

- [ ] 注册固定指标：`platform.audit.accepted`、`platform.audit.written`、`platform.audit.dropped`（reason 仅 capture/oversize/full/closed/write/late_chain）、`platform.audit.pending`、`platform.audit.queue.size`、`platform.audit.active.scopes`、`platform.audit.write.duration`。写失败识别封闭链使用固定内部分类，不解析异常文本。安全限频日志只含固定分类和累计数，不含 IDs、Body、SQL 参数或完整异常堆栈。事件过大丢弃必须覆盖元数据，不只测 attributes。

```yaml
operations:
  audit:
    queue-capacity: 256
```

实施裁定：首版仅允许 `queue-capacity` 在1至256内缩小，其他已批准 byte/time 预算保持固定，由 Properties 的明确 Millis/Seconds getter 提供；不新增没有当前调参消费者的 Duration 或 max-byte YAML 字段。连接获取/建连/锁/SQL/事务/读取仍分别落实1s/1s/1s/2s/3s/3s；这不降低故障隔离验收。固定预算需要调整时修改代码并复核对应测试，不让配置扩大批准上限。

- [ ] 扩展 SqlProbe 保留原 `stop(): List<String>`，另加 `observations(): List<SqlObservation>`；记录 `SqlObservation(String sql, String threadName, long threadId)`，不记录参数。审计会话必须注册该真实 MyBatis 拦截器。`OperationsAuditProbe` 注入 Context/Dispatcher/MeterRegistry，提供 `awaitIdle(Duration timeout)` 和 `awaitEvent(OperationsTestMapper mapper, String requestId, Duration timeout)`；前者条件为 active scopes=0 且 pending=0，后者只读取本 requestId 事件，均 Awaitility 有界等待。

```java
Awaitility.await().atMost(timeout).untilAsserted(() -> {
    Assertions.assertEquals(0, dispatcher.pendingCount());
    Assertions.assertEquals(0, context.activeScopeCount());
});
```

增加 Context 的只读 `activeScopeCount()`；记数生命周期包括等待外层事务终态的状态。Task 4 开启注解后的 HTTP 测试才是本任务生产联通验收；当前聚焦模型/装配门禁先通过。

## Task 4：统一切换29个 Controller 与全部业务采集点

**Files:** 文件地图中的四 Controller、三个 Processor、五业务 ServiceImpl、SessionServiceImpl、AuditService interface/Impl、现有业务 HTTP 测试和 Checkers。

**Interfaces:** AuditService 成为现场采集服务。动作/RequestId 从 Context 获取；现有安全属性名、排序和具体原因不改变。最终接口为下列方法，避免 Gateway 再出现十余个位置参数：

```java
void capture(Runnable auditCapture);
void observeTransaction();
void identify(String actorId, Long tenantId);
void target(String resourceType, String resourceId);
void replay();
void append(String actorId, Long tenantId, String resourceType, String resourceId,
    Result result, String reason, Instant now);
void appendRoleChange(String actorId, Long tenantId, RoleDetails before,
    RoleDetails after, String reason, Instant now);
void appendGatewayStatusChange(String actorId, Long tenantId, GatewayChange change, Instant now);
void appendRoleDeletion(String actorId, Long tenantId, RoleDetails before, String reason, Instant now);
void appendAccountEnable(String actorId, OperationsAccount account, OperationsMember member,
    String reason, Instant now);
void appendAccountDeletion(String actorId, OperationsAccount account, OperationsMember member,
    List<Long> roleIds, String reason, Instant now);
```

这些 DataObject 只在 Owner Web JAR 内同步读取为白名单值，绝不进入队列。Collector 不依赖 Repository/Mapper/DataSource/事务管理器，只依赖 Context 和现有纯格式化能力；所有尺寸检查在大集合复制/join 前执行，失败 context.discard 并计数。

实施裁定：Java 会先求值方法参数，Gateway 的有界记录构造和仅供审计的幂等引用计算也可能在进入 Collector 前失败。增加 `AuditContext.capture(Runnable auditCapture)` 并由 AuditService 同名方法转交，立即执行且不保存闭包；无 Context 时跳过，仅包围审计表达式，不能包围 CAS、命令完成或业务返回投影。这样保留 GatewayChange 强类型与构造校验，队列仍只接收冻结 Event。

- [ ] 扩展架构测试，锁定完整方法清单和策略，预期尚无注解为 RED。总计当前30方法中29标注：成功+拒绝15个，仅拒绝14个；context无注解。端点清单不能仅用总数替代逐方法断言。

```text
OperationsAuthenticationController:
  login,refresh,logout,changePassword,createFirstAdministrator => success + denied
  context => none
OperationsManagementController:
  createAccount,disableAccount,enableAccount,deleteAccount,assignRoles,
  createRole,updateRole,deleteRole => success + denied
  accounts,roles,roleEmployeeCounts,role,permissions,roleResources,auditEvents,auditEvent => denied only
OperationsAuthorizationController:
  authorize,navigation => denied only
GatewayConfigurationManagementController:
  changeTargetStatus,changeRouteStatus => success + denied
  targetServices,targetService,routes,route => denied only
```

- [ ] 从现有 Processor 的 action/resource/fallback 映射逐项迁入 Controller 声明；例如保留 `OPERATIONS_ACCOUNT_ROLES`（不要误换成权限 Code `OPERATIONS_ACCOUNT_ASSIGN_ROLES`）。现有分页校验仍在方法体，新增拒绝可被切面观察；未进入 Controller 的解析/校验/Filter 失败不补全局审计。

```java
@Audit(action = "OPERATIONS_LOGIN", resourceType = "USER_ACCOUNT",
    alternateResourceTypes = "AUTH_SESSION")
@Override
public AuthenticationTokensResponseBody login(LoginRequestBody body) {
    return processor.login(body, requestId());
}

@Audit(action = "OPERATIONS_ROLE_DETAIL", resourceType = "ROLE",
    resourceIdParameter = "roleId", fallbackResourceId = "INVALID_ROLE_ID", recordSuccess = false)
@Override
public RoleResponseBody role(String actor, String session, String roleId) {
    return processor.role(actor, session, roleId, requestId());
}
```

精确校验现有 `auditRoleId/auditCode` 的 fallback 及合法值规则后迁移，不能因为示例字符串覆盖已有契约。路由/资源参数绑定不等于认证 actor。登录失败中已查到的 Account 引用可保留现有事实含义，但不能标成已认证身份；其他 actor 在 SessionPolicy 校验成功后 identify。

- [ ] 在所有实际业务事务回调入口 observe；构造器保留必要的 TransactionTemplate 初始化，不为了 Lombok 删除初始化语义。登录和 refresh 仍在事务返回后抛公共 FailException。

```java
var outcome = transaction.execute(status -> {
    audit.observeTransaction();
    return authentication.login(command, requestId);
});
if (outcome == null) {
    throw new IllegalStateException("认证事务未返回结果");
}
if (outcome.error() != null) {
    throw new FailException(outcome.error());
}
return tokenBody(outcome.tokens());
```

Management/Gateway 的 Redis 锁仍包围原业务 transaction.execute；进入回调再观察，锁失败就是 NOT_STARTED。查询保留已有必要一致性事务，删除仅为写失败审计新开的事务，不借本次扩大普通查询重构。

- [ ] 实现类型化 Collector 并在现有现场调用新签名；Gateway before/after 转为 `GatewayChange`；保留角色/账号删除前的快照、原因和安全幂等引用。显式 `allowed=false` 分支记录 DENIED，即使 Controller 正常返回。每个成功幂等重放的早返回分支先 `audit.replay()`，同键异请求仍由原业务拒绝。

```java
audit.capture(() -> audit.appendGatewayStatusChange(actor, tenantId,
    new GatewayChange(new Target("GATEWAY_ROUTE", routeCode), sessionId,
        new StatusVersion(beforeStatus, beforeVersion),
        new StatusVersion(afterStatus, afterVersion), reason, idempotencyReference), now));
```

这里局部值对应当前 Gateway Service 已取得的现场变量；不得新增后台查询来补字段。删除三个 Processor 的 `audited/recordFailure` 写库包装和重复 action/requestId 参数传递，仅保留对 Context 的事务观察及必要目标定位。此时同时删除旧 AuditRepository/Impl 的六个写方法、AuditService/Mapper/XML 的 lockTenant，仅保留新的 Identity 业务锁。调用链扫描确保业务已无 Audit Mapper/WriteRepository/Writer 访问，也没有老 Collector SQL 路径；对外审计查询仍调用四个主会话读方法。

- [ ] 所有成功/拒绝审计断言先按 requestId 有界等待；Checkers 继续只读。每个 HTTP fixture 的 `@AfterEach` 顺序为释放 SQL Barrier/手工锁 → `auditProbe.awaitIdle(Duration.ofSeconds(10))` → `fixtures.close()`；若未排空，先报告且保留精确现场，不能删完再允许后台回写。

```java
auditProbe.awaitEvent(mapper, requestId, Duration.ofSeconds(10));
check.auditChain(fixtures.chainId());
```

审计查询自身只有被拒绝才记录；成功查询无事件时不能 awaitEvent。共享 Spring Dispatcher 不在每个测试方法关闭，应用销毁只执行一次 close。已有强制 ID 测试必须改成 SQL 屏障定位后台阶段后才安排受控 ID，不能让单个“下一 ID”在请求/后台之间竞态。

- [ ] 重跑前置 RED 和三类业务 HTTP 回归，必须全部 GREEN；再运行架构/持久化测试。检查现有 Controller 外直接 Java 调用不因缺 Context 失败，但不宣称其产生操作审计。

```bash
./mvnw -q -pl platform-support-test -am -Dtest=OperationsAuditCompletionAutoTest,OperationsAuthenticationHttpAutoTest,OperationsDirectoryRefactoringHttpAutoTest,GatewayConfigurationManagementHttpAutoTest,PlatformArchitectureAutoTest,OperationsPersistenceContractAutoTest,GatewayConfigurationManagementPersistenceContractAutoTest -Dsurefire.failIfNoSpecifiedTests=false test
```

审查门禁：首次成功写入与成功重放不重复；失败计数/撤销已提交但响应仍拒绝；没有 audit 异常替换原业务异常；详细原因和原 Hash 校验不退化。

## Task 5：真实故障、资源上限和异步时间契约

**Files:** 两个新审计 HTTP 测试、Fixture/Mapper/SqlBarrier/SqlProbe/AuditProbe、现有 HTTP 回归；只在测试中准备受控真实状态。

**Interfaces:** 沿用既有 `OperationsSqlBarrier.arm(String statementSuffix, Long parameterId, int parties)`、`armLock(String statementSuffix, Long parameterId)` 及 Gate 的有界 await/release/close。新增 Mapper 方法：`withExclusiveAuditChainLock(long chainId, Runnable operation)`；实现复用现有独立连接 withLock。SqlBarrier 可增加 `armAfter(String statementSuffix, int parties)`：先真实 invocation.proceed，再等待屏障；不替换返回、不修改参数。

- [ ] 新类复用现有测试生命周期/运行时 KeyMaterial、固定 Clock、Loopback 前置检查、随机 Redis namespace 和 `PlatformDatabaseIsolationListener`。为新 Spring 上下文使用未占用 worker-id=32/33（执行前查其他并发修改），server.port=0；不要再复制一套全局锁 Listener。可将现有 TestClock/前置检查的通用准备提取至既有 test/infrastructure，但必须保持原套件行为和密钥不落盘。

```java
public void withExclusiveAuditChainLock(long chainId, Runnable operation) {
    withLock("SELECT chain_id FROM t_audit_chain WHERE chain_id=? FOR UPDATE", chainId, operation);
}
```

- [ ] 先写真实链锁不阻断 HTTP 的回归再执行。注意 `ensureChain` 的 ON DUPLICATE KEY UPDATE 可能先等待链锁，不能只观察 lockChain。测试用独立连接持锁，客户端请求有界完成，然后验证失败计数或创建状态已提交；通过审计专用1秒锁等待产生真实故障，释放后新请求的审计可写。SQL Probe 断言审计 INSERT/UPDATE 只来自专用 worker，业务 SQL 不在 audit worker。

```java
mapper.withExclusiveAuditChainLock(fixtures.chainId(), () -> {
    var response = client.post()
        .uri("/platform-support/authentication/v1/operations/login")
        .header("X-Request-Id", requestId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new LoginRequestBody(account.email(), INITIAL_PASSWORD + "-wrong"))
        .exchange();
    check.rejected(response, "OPERATIONS_AUTHENTICATION_FAILED", requestId);
    Assertions.assertEquals(1, mapper.account(account.accountId()).get("consecutive_password_failures"));
});
```

`account/requestId` 在测试内由 fixture 创建；随后 awaitIdle，再发新的成功/拒绝请求观察新 event。用 Gate 判断后台实际进入竞争阶段，HTTP 响应限时只证明不等待 Gate 释放，不做机器性能阈值承诺。

- [ ] 用启动属性把测试队列缩小到2；真实后台在 ensureChain 屏障等待时继续发送 HTTP 填满两项，再一项触发 full 计数；pending 不超过3（worker1+queue2）。释放后写入队列中事件并继续处理，不在请求线程执行 Writer。另用限定 audit DataSource 持有唯一连接验证池耗尽，主业务 datasource 仍可用；测试 finally 归还连接后 drain。

```java
Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
    Assertions.assertEquals(2.0, registry.get("platform.audit.queue.size").gauge().value());
    Assertions.assertTrue(registry.get("platform.audit.dropped").tag("reason", "full")
        .counter().count() >= 1.0);
});
```

计数断言使用测试开始时基线差值，避免共享上下文跨方法累计；引用的 registry 为真实 MeterRegistry。屏障等待上限/释放顺序必须小于测试框架总超时，禁止固定 sleep。

- [ ] 在不同 SQL 阶段用真实约束冲突/受控 ID 触发失败，验证审计事务全回滚。沿用现有 ControllableIdGenerator，只在生产审计已被屏障停在目标阶段后安排冲突，业务请求不在此时并行消耗该 ID。测试分别覆盖 insertEvent、insertAttribute、advanceChain/CAS、insertSecurity；不可通过 Mockito 或修改生产单例返回制造失败。

```text
记录本链 sequence/hash 和事件/属性/Security 行数基线
以真实 HTTP 提交本次业务，后台在已执行阶段的屏障暂停
仅准备本测试持有主键/版本的冲突，再 release
awaitIdle，断言业务提交且审计所有计数/链头仍等于基线
恢复本测试冲突状态，再发一条 HTTP，验证下一事件完整可见
```

对无法安全在外部修改的已锁行，使用受控 ID 导致后续真实唯一键失败，不尝试绕过 InnoDB 锁；CAS 返回零的纯分支可由契约测试覆盖，不伪造数据库更新返回。Audit Writer 产生未知提交结果时不自动重试；终态合并 UNKNOWN 的确定性语义由 Task 1 验证，不将本地 SQL 失败伪称真实网络提交未知。

- [ ] `OperationsAuditConnectionFailureHttpAutoTest` 在上下文初始化期间通过测试 BeanPostProcessor **只对名为 auditWriteDataSource 的实际 Hikari Bean**设置不存在的无敏感用户名，使真实 MySQL 认证失败；不创建数据库用户、不改主池、不在测试方法中修改运行 Bean。HTTP 登录/查询和 `/actuator/health/readiness` 仍按原权限可用，审计 write/drop 指标上升；主库错误仍由原前置检查失败关闭。

```java
@Override
public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if ("auditWriteDataSource".equals(beanName) && bean instanceof HikariDataSource pool) {
        pool.setUsername("platform_audit_unavailable_test");
    }
    return bean;
}
```

该类独立测试配置且只用于故障上下文。默认 DataSource health 可能汇总审计池并令整体 `/actuator/health` DOWN；**不得禁用全体 db health**。验收针对现有 readiness 组 `readinessState,gatewaySnapshot`，审计故障单独可观测而不追加到这个组。

- [ ] 加入跨日/封链/多实例与关闭测试：在 fixture 持有的两个日期创建链并登记精确 ID，Clock 只用专用测试配置的可控实例；先冻结发生时间，再前移到下一天并释放 Gate，断言 occurred_day 不变、received_time 为实际写入日。封闭旧链掉本条，不重开；两个独立 Writer/专用会话在同库并发追加，以原 Checker 验证序号唯一及链完整，不断言序号严格按发生时间排序。

```java
Assertions.assertEquals(occurredAt, storedOccurredAt);
Assertions.assertTrue(storedReceivedAt.isAfter(storedOccurredAt));
check.auditChain(chainId);
```

`occurredAt` 为请求现场冻结 Instant，stored 两个值由 TestMapper 读取实际列后转 Instant，chainId 为 fixture 本用例持有值。多实例追加测试属于持久化并发边界，允许直接调用真实 Writer，不替代主要业务 HTTP 回归；独立 Writer/池必须在本用例全局隔离锁内关闭。

测试实施注意：Writer 在进入 `write` 时、开始事务与 `ensureChain` 前就冻结 `receivedAt`。跨日用例必须先用一条哨兵事件阻塞单 worker，把目标 HTTP 事件以 day-1 `occurredAt` 排入其后，再前移测试 Clock 到 day-2 并释放哨兵；不能阻塞目标事件自身的 SQL 后才改 Clock 并把旧 receivedAt 误当成功。Fixture 需先精确登记两个日期各自的 chain ID，并在后台完全排空后按所有权清理。

- [ ] 用独立可关闭的分发器测试关闭后 offer 返回 false、5秒预算内停止接收和丢弃剩余事件；用真实 HTTP 线程复用证明 RequestId/actor 不串用。包含原异常与审计同时失败、无事务锁拒绝、正常返回 allowed=false、超限整条丢失和日志无敏感值。登录失败内部原因继续校验；预 Controller 绑定失败无操作审计，正常 context/JWKS/Snapshot 也无新增事件。

```java
dispatcher.close();
Assertions.assertFalse(dispatcher.offer(event));
Assertions.assertEquals(0, dispatcher.pendingCount());
```

该片段只用于独立实例且已释放正在执行的 SQL/Gate；关闭超时仍有 in-flight 写入时不得把 pending 伪置0。生产共用 Dispatcher 不由测试方法关闭；Scope 清理在所有成功/异常路径检查 activeScopeCount 最终为0。

- [ ] 运行两类新故障 HTTP 测试、纯状态机测试和现有业务 HTTP 回归。观察故障后恢复能力和测试数据库无本次残留；只有实际经过的故障才标为已验证。

```bash
./mvnw -q -pl platform-support-test -am -Dtest=OperationsAuditCompletionAutoTest,OperationsAuditBestEffortHttpAutoTest,OperationsAuditConnectionFailureHttpAutoTest,OperationsAuthenticationHttpAutoTest,OperationsDirectoryRefactoringHttpAutoTest,GatewayConfigurationManagementHttpAutoTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Task 6：完整门禁、代码评审与状态交付

**Files:** 所有本次差异；只在 `agent-docs/CURRENT_IMPLEMENTATION.md` 记录实现/验证状态。若只是接入方式变化，不重写其他系统需求和历史设计，不修改六系统模型。

**Interfaces:** 输入为 Tasks 1–5 的代码与真实结果；输出为可复查测试报告和明确未验证边界，不是部署/合并声明。

- [ ] 由另一代理对当前差异做只读评审，重点检查事务通知先后、主库绑定、快照容量、同步异常隔离、旧覆盖/幂等、Tenant 锁和测试清理。实施负责人自己运行门禁，不以代理结论替代结果。发现缺陷使用 TDD 修复并重跑受影响项。

- [ ] 按项目现有格式化命令处理本次文件，再从 `P` 运行完整验证，不能因新增异步类拉低覆盖而删除门禁、排除生产包或机械铺内部方法 Mock 测试。

```bash
./mvnw -q clean verify
```

- [ ] 从工作区执行需求站点测试和双仓库差异检查；验证本计划/设计/决策/台账的 Markdown 相对链接能解析。读取 Surefire/JaCoCo 的实际测试数、失败数和门禁结果。

```bash
npm --prefix requirements-site test
git diff --check
git -C platform-support-service diff --check
rg -n 'audit\.lockTenant|recordFailure|private .*audited' platform-support-service/platform-support-web/src/main/java
rg -n 'CallerRunsPolicy|@Transactional|newCachedThreadPool|ForkJoinPool' platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/audit
```

后两个检查预期无旧包装/禁用实现；rg 无匹配 exit1 是预期，不当成测试故障。结合结构测试证明审计写 SQL 不在业务请求线程，不能仅靠命名扫描宣布隔离成功。

- [ ] 将 AUDIT-01 改为实际完成范围，登记执行日期、精确命令、结果及仍未验证的网络未知提交/强制终止边界；若有失败或缺本地依赖则保留 pending 原因。注明同 JVM/同 MySQL 非物理零影响、审计允许丢失、当前仅本地实现未部署；不要写“已合并”。

实施完读取新结果后再写台账，不预填成功记录。设计中的资源初值不是生产吞吐或绝对故障时延 SLA。

## 设计覆盖与实施审查索引

| 正式设计契约 | 实施/测试门禁 |
|---|---|
| Controller 范围、安全参数绑定、身份来源、入口盲区 | Tasks 3–4、架构逐方法清单、Task 5 HTTP 边界 |
| 调用与真实事务双终态、未知提交、无事务、提交后异常 | Task 1 纯状态机、Tasks 3–4 事务桥、Task 5 故障 |
| 失败计数、Refresh 撤销、Tenant/Account/Session 锁 | 前置 RED、Task 2 锁归属、Task 4 原业务 HTTP |
| 业务幂等重放不补审计、细分拒绝、before/after | Task 4 Collector 与现有 Checkers、Task 5 对照 |
| 有界内存/超时/单 worker/隔离连接/关闭 | Tasks 2–3 配置、Task 5 队列/池/锁/关闭 |
| 同库独立审计原子性、Hash/时间/封链/顺序语义 | Task 2 Writer、Task 5 真实失败和链验证 |
| 无敏感日志/高基数指标/上下文残留 | Tasks 3–5 安全诊断、线程复用和冻结测试 |
| readiness、不重试、不补偿、测试安全清理 | Tasks 3/5/6；无 Schema、容器、外部资源变更 |

## 装配依据

本计划对 Boot 4 的多 DataSource 装配使用非默认候选 Bean，而不是仅用 Primary 解决注入歧义；框架自动配置仍应创建主数据源相关组件。[Spring Boot 4 data-access how-to](https://docs.spring.io/spring-boot/4.0/how-to/data-access.html)

AOP starter 使用当前 Boot 4 管理的 AspectJ 集成，并通过实际 Controller 代理的 HTTP 回归验收，不以注解存在替代调用覆盖。[Spring Boot 4 AOP reference](https://docs.spring.io/spring-boot/4.0/reference/features/aop.html)
