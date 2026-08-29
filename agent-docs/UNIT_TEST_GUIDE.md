# Java 单元测试与自动化测试规范

最后更新：2026-08-28

## 1. 使用范围

当任务涉及 Java 生产逻辑或以下任一测试能力时，Agent 必须完整读取本文：

- JUnit 测试类和测试基类；
- Builder、Mocker、Simulator、Probe、Checker 或测试数据工具；
- Mapper/Repository、数据库、Redis 或 MQ 测试；
- 外部 HTTP 接口、Provider、消息、幂等、超时或并发测试；
- 测试配置、Maven 执行范围和覆盖率策略。

本文中的 `auto` 指默认由 Maven 和 CI 自动执行的测试集合，不等同于狭义的纯单元测试。纯单元测试、Repository 测试、服务级自动测试和契约测试都可以属于 `auto`，但必须遵守各自的隔离边界。

涉及 `compensation_event`/XXL-Job 或 Vault 时，还必须分别读取 `COMPENSATION_EVENT_GUIDE.md` 和 `VAULT_SERVICE_ARCHITECTURE.md`，并把其中的并发、安全与失败关闭规则转成测试场景。

## 2. 核心原则：测试必须内部闭环

`auto` 的目标不是在运行期间替换某个 Java 方法的返回值，而是让请求经过本服务的真实生产链路，并由测试管理的基础设施或有状态外部系统 Simulator 完成依赖响应。

一个 `auto` 测试只有同时满足以下条件，才算内部闭环：

1. 不连接任何真实业务环境或人工维护的共享测试环境。
2. 不依赖真实 Nacos、配置中心、其他微服务、Provider、共享数据库、共享 Redis 或共享 MQ。
3. 所有依赖由测试进程或 Testcontainers 自动创建、配置和销毁。
4. 当前测试所覆盖的 Controller、Processor、Domain Service、Repository、Mapper 和 Integration Client 使用真实生产实现。
5. 外部系统通过真实 HTTP/MQ 协议连接有状态 Simulator，不通过 `when(...).thenReturn(...)` 等方法级 Stub 改写运行时行为。
6. 未准备业务数据、未识别的请求或未消费的预期调用必须快速失败并输出上下文。
7. 调用后同时验证接口结果、本地权威数据、`compensation_event`/消息、外部请求以及不应发生的副作用。
8. 测试可独立、可重复、可随机顺序执行，不依赖上一个测试留下的状态。

闭环范围是当前微服务及其边界，不是一次启动六个核心系统。Simulator 证明当前服务遵守外部契约；外部系统真实实现的正确性由其自身 `auto`、契约测试和少量跨服务端到端测试负责。

## 3. 参考项目结论

`Code-PMX/transaction` 使用独立 `transaction-test` 模块，主要模式为：

```text
TestBase
└── MockTestBase
    └── AbstractPaymentProcessorTest
        └── 具体业务测试
```

配套结构包括 `auto/manual/test`、Builder、Mocker、Checker 和 TestMapper。值得保留的做法是：

- 从公开业务入口调用，而不是直接拼接内部实现步骤；
- 使用 Builder/Mocker 表达前置业务场景；
- 清理本地数据和测试状态；
- 同时验证响应、订单、参与方和清算记录；
- 用 `success/fail/processing` 等业务语言描述外部场景。

参考项目仍存在以下不能照搬的闭环缺口：

- 启动配置仍可能访问配置中心；
- 数据库和 Redis 依赖人工启动的固定本地实例及固定凭据；
- 多个 Mock Facade 直接返回共享 `response` 字段，未准备时可能返回 `null`；
- 部分 Mocker 和内部 Map 没有在每个测试前完整重置；
- Mock 未统一记录请求，无法验证调用参数、次数和幂等键；
- 固定端口、`PER_CLASS`、方法顺序和 `Thread.sleep` 容易掩盖状态污染；
- Maven 未通过明确规则保证 `manual` 永远排除在默认测试之外。

Code-MoR 保留测试 DSL 思想，但把方法级 Stub 升级为“业务场景数据 + 真实适配器 + 有状态 Simulator”。

## 4. 测试模块结构

继续使用已确认的方案 A，在独立 `<service>-test` 模块中保留 `auto/manual/test`：

```text
<service>-test/
├── pom.xml
└── src/test/
    ├── java/<base-package>/
    │   ├── auto/
    │   │   ├── domain/                 # 聚合、值对象、规则和状态机
    │   │   ├── domainservice/          # Domain Service 与 Processor
    │   │   ├── ext/
    │   │   │   ├── http/               # HTTP 入口闭环测试
    │   │   │   └── eventhandler/       # MQ/事件入口闭环测试
    │   │   └── repository/             # Repository、Mapper 和约束
    │   ├── manual/                     # 真实环境人工联调，默认不执行
    │   └── test/
    │       ├── base/                   # 分层测试基类
    │       ├── configuration/          # 测试应用与动态连接配置
    │       ├── builder/                # 请求、本地实体和场景 Builder
    │       ├── mocker/
    │       │   ├── local/              # 本服务前置数据
    │       │   └── external/           # Simulator 场景数据
    │       ├── simulator/              # 有状态外部系统模拟器
    │       ├── checker/                # 响应、数据、请求和事件断言
    │       ├── probe/                  # 只观察请求、消息和异步结果
    │       ├── infrastructure/         # DB、Redis、MQ、网络故障设施
    │       └── util/
    └── resources/
        ├── application-auto.yml
        ├── application-manual.yml
        ├── db/migration/
        └── fixtures/
```

规则：

- `test` 模块依赖被测 `web` 模块，生产模块不得反向依赖 `test`。
- 可执行测试只放在 `auto` 或 `manual`；`test` 包只存放共享测试基础设施。
- `auto` 优先按业务能力和生产入口组织，不为了测试术语创建空目录。
- 没有对应能力时不创建 Simulator、Redis、MQ 或网络故障目录。

## 5. 测试分层

按最小充分范围选择测试，但不能通过缩小范围绕过真实链路风险：

| 类型 | 目标 | 运行边界 |
|---|---|---|
| 纯单元测试 | 值对象、聚合行为、状态机、规则、计算和转换 | JUnit 5；不启动 Spring、容器或 Mock 框架 |
| 组件测试 | 不涉及协议和持久化的领域协作 | 直接装配小范围真实对象；测试数据通过明确的 Fake/内存实现提供 |
| Repository 测试 | SQL、映射、唯一约束、分页、事务和乐观锁 | 真实 Repository/Mapper + Testcontainers 数据库 |
| 服务级自动测试 | 从公开入口验证本服务完整用例 | 真实本服务链路 + 测试基础设施 + 外部 Simulator |
| 契约测试 | HTTP/Event 路径、字段、序列化和兼容性 | 真实契约序列化与协议交互 |

纯单元测试只用于天然不需要外部协作的对象。如果一个用例只有依赖 Repository、HTTP、Redis 或 MQ 才能成立，应把它放入相应的闭环测试，不使用 Mockito 把依赖返回值拼成一条看似成功的链路。

## 6. 服务级 auto 标准链路

服务级 `auto` 是本规范的核心形态：

```text
真实测试客户端
  → 随机端口 HTTP Controller / 真实消息入口
    → Processor
      → Domain Service / Domain
        → RepositoryImpl → Mapper → 测试数据库
        → Redis Client → 测试 Redis
        → MQ Producer → 测试 Broker → Message Probe
        → Integration ClientImpl
          → @HttpExchange / Provider HTTP Client
            → 本地外部系统 Simulator
```

- 入站 HTTP 测试默认使用随机端口，覆盖请求反序列化、校验、Controller 和错误响应。
- 不需要验证 HTTP 协议的领域规则测试直接调用领域对象，不强制启动 Web Server。
- 当前服务内部不使用替代实现；测试应覆盖与生产一致的依赖方向。
- 服务发现不是业务链路的一部分。`auto` 禁用 Nacos 和配置中心，通过测试动态配置把服务名或客户端地址解析到 Simulator。
- 不经过 Gateway 验证内部服务调用。

## 7. 测试基础设施

### 7.1 数据库

Repository 和服务级 `auto` 使用与生产一致类型的数据库容器：

```text
Domain Service → RepositoryImpl → Mapper → Testcontainers Database
```

- 由测试自动启动并使用随机映射端口；开发者不得手工准备数据库。
- 使用正式 migration/DDL 建表，不维护一份行为不同的简化结构。
- 默认不使用 H2 替代 MySQL/PostgreSQL，以免掩盖 SQL 方言、索引、锁和事务差异。
- Repository 测试覆盖唯一约束、空结果、映射、分页、事务回滚、乐观锁和旧状态更新失败。
- 容器可以按测试套件共享以降低启动成本，但每个测试的数据必须隔离并清理。
- Code-MoR 使用 MySQL 8.4 LTS；测试容器必须与当前批准的 MySQL 8.4 基线一致，不使用 H2 或其他方言替代。

### 7.2 Redis

生产链路涉及缓存、TTL、幂等键、分布式锁、原子操作、Lua 或序列化时，使用真实 Redis 容器：

```text
生产 Redis Client → Testcontainers Redis
```

- 不通过方法 Stub 返回缓存命中或未命中。
- Mocker 写入真实测试 Redis，Checker 验证 Key、Value、TTL 和锁释放。
- 普通 Redis 能力通过真实 Lettuce/Spring Data Redis 链路验证；Redisson 锁或信号量场景还必须覆盖租约/过期、竞争、释放和客户端中断。
- 当前用例完全不使用 Redis 时，不为了形式一致启动容器。

### 7.3 MQ

生产链路涉及消息发布或消费时，使用真实测试 Broker：

```text
出站：真实 Producer → 测试 Broker → Message Probe
入站：测试 Producer → 测试 Broker → 真实 Listener → 本地业务链路
```

- Message Probe 是真实测试消费者，只记录消息，不修改业务返回。
- 测试 Broker 使用 RabbitMQ 4.2 基线和 Spring AMQP 真实客户端，验证 `topic_*` Topic Exchange、`cg_*` Queue、Routing Key、JSON 信封和受控 DLQ 拓扑。
- 异步发布流程应覆盖业务数据、同事务 `compensation_event`、提交后 Dispatcher、Broker Confirm 和消息内容；成功删除记录不能替代对消息与业务事实的断言。
- Consumer 测试覆盖 Persistent Message、Manual Ack、重复、延迟、乱序、幂等、Confirm Nack/Timeout、Delivery Limit、重试和死信策略中与当前能力相关的场景。
- 不直接调用一个被 Stub 的 `EventPublisher` 来声称消息链路已经通过。

### 7.4 Vault

业务服务的 `auto` 不连接共享 Vault 环境，而是通过真实 HTTP 调用有状态 Vault Simulator：

- Simulator 生成随机 opaque token，并记录 Tenant、Purpose、版本、元数据 Hash、Blind Index 与授权上下文；不得把它简化成按方法名返回固定 DTO。
- 覆盖 Protect、Reveal、Bind、Derive Blind Index、Read Record Blind Indexes、CopyForBinding、Rewrap、Retire/Destroy 的当前服务实际调用，验证 Purpose/Reason/Resource、mTLS 身份模拟、最小字段返回、Derive 限流/异常枚举检测、Copy 来源与目标授权，以及所有高权限操作的审计和失败关闭。
- Checker 分层验证：业务数据库只按模型保存完整 Token Tuple；只有明确批准且通过 `CopyForBinding` 生成接收方专属 Token 的事件才可携带该 Token Tuple；日志、错误信息、Trace、Metric 与普通事件不得出现完整 Token、完整 Blind Index、Reveal 明文或 Vault 内部密文。
- 覆盖 Vault 超时、不可用、Token 不存在、版本冲突、权限拒绝、Legal Hold 和相同 Blind Index 业务冲突等相关场景。
- PAN、CVV/CVC、完整磁道数据、PIN/PIN Block 和卡有效期不属于 Vault Simulator 或 Fixture 的合法输入；必须覆盖拒绝持久化、日志零泄漏与 Security Event。Payment 集成测试只使用 Provider Token 与批准的卡片描述字段。
- Vault 服务自身的 `auto` 使用真实测试数据库与隔离的 KMS Simulator/测试密钥，验证 AAD、密钥轮换、密文篡改、审计事件和销毁不可恢复性。

### 7.5 容器生命周期

- 完整服务级 `auto` 的本地前置条件是 JDK、Maven 和 Docker/兼容容器运行环境。
- Testcontainers 负责启动、动态连接信息和销毁，不要求人工维护 Mock 环境。
- 容器镜像必须固定经过批准的版本，不使用漂移的 `latest`。
- 并行执行前必须证明数据库、Redis Key、Topic/Consumer Group 和清理逻辑互不干扰。

## 8. 外部系统 Simulator

### 8.1 定义

Simulator 是一个通过真实协议提供服务的简化外部系统，而不是方法返回值仓库。典型组成：

```text
<ExternalSystem>Simulator
├── HTTP Controller / MQ Listener
├── Request Validator
├── 简化业务处理
├── Scenario Store
├── Response Converter
└── Request Journal
```

Simulator 必须：

- 运行在测试进程的随机端口或测试管理的容器中；
- 使用与生产客户端相同的 HTTP/MQ 契约；
- 根据收到的请求和已经准备的业务场景数据计算结果；
- 校验关键字段，不接受与场景无关的请求；
- 记录请求、顺序、时间和关联标识，供 Checker 验证；
- 对缺失场景和未识别请求显式失败；
- 在每个测试前重置自身数据和 Request Journal。

Simulator 只实现当前服务依赖的最小外部语义，不复制另一个微服务的全部领域模型。

### 8.2 内部 Spring HTTP 服务

内部服务 Simulator 优先依赖提供方的 `*-api` JAR，并实现相同的 `@HttpExchange` 契约：

```text
本服务 Domain Service
  → 本地 *Client
    → 真实 *ClientImpl
      → 真实 @HttpExchange 代理
        → Simulator HTTP Controller
```

这样能够覆盖 URL、HTTP Method、Path/Query/Header、JSON、错误映射、超时配置和幂等键。不能直接替换 `*ClientImpl` 或 `@HttpExchange` 接口返回值来代替这类测试。

### 8.3 第三方 Provider

Provider Simulator 按已批准的第三方协议提供 HTTP Endpoint，并维护最小测试状态，例如账户、外部订单、支付结果或回调记录。生产 Provider Adapter 必须真实发出请求并完成外部状态到本地语义的转换。

### 8.4 故障注入

业务失败通过场景数据产生；网络失败通过测试基础设施产生：

| 场景 | 推荐方式 |
|---|---|
| 业务拒绝、无数据、状态不允许 | 准备 Simulator 业务状态，由其规则生成响应 |
| HTTP 500 或协议错误 | Simulator 的显式故障场景 |
| 延迟、超时、断连 | Toxiproxy 或等价的测试管理网络代理 |
| MQ/Redis 不可用 | 暂停或停止对应测试容器 |
| 非法 JSON/缺失字段 | 专门的协议异常场景 |

故障注入不得通过修改被测 Spring Bean 的方法返回值实现，并且必须在测试结束后恢复。

## 9. 禁止方法级行为 Stub

Code-MoR 自动测试不使用以下方式构造业务结果：

```java
when(client.call(request)).thenReturn(response);
doThrow(exception).when(client).call(request);
```

同样禁止：

- `@MockBean`、`@MockitoBean` 或同类机制替换生产 Repository、Client、Publisher 后声称链路已闭环；
- Mock Facade 直接返回一个测试期间可变的共享 `response` 字段；
- 在测试方法中临时修改生产单例 Bean 的状态；
- 用 Spy 绕过真实分支、事务、序列化或持久化；
- 通过返回 `null` 表示“未配置场景”。

如果极小的纯算法对象需要一个协作者，应优先重构出显式输入、值对象或无副作用策略。确实需要内存实现时，编写行为稳定、可独立验证的 Fake；Fake 根据输入和内部状态运行，不能由每个测试动态指定某次方法返回什么。

## 10. Builder 与 Mocker

### 10.1 Builder

Builder 提供语义化、合法的默认对象：

```java
public final class ExternalCaseBuilder {
    private final ExternalCase externalCase = new ExternalCase();

    public static ExternalCaseBuilder template() {
        return new ExternalCaseBuilder()
            .externalId(TestIds.next())
            .status(ExternalCaseStatus.AVAILABLE);
    }

    public ExternalCaseBuilder externalId(String value) {
        externalCase.setExternalId(value);
        return this;
    }

    public ExternalCase build() {
        Assertions.assertNotNull(externalCase.getExternalId());
        return externalCase;
    }
}
```

- `template()` 返回最小合法默认值，测试只覆盖与场景有关的字段。
- 提供 `clearXxx()`、`addXxx(...)` 等安全的集合操作。
- Builder 不访问数据库、Redis、MQ 或外部服务，也不改变全局测试状态。
- 随机数据必须满足格式约束；幂等、快照和关联断言需要稳定值时显式传入。

### 10.2 Mocker

Mocker 的含义限定为“准备业务场景数据”，不再表示 Mock Java 方法：

```text
Builder 构建场景数据
  → Mocker 写入本地数据库/Redis或 Simulator Store
    → 真实生产链路读取该数据
      → Simulator/本服务按规则产生结果
```

推荐 API：

```java
ExternalCase externalCase = externalCaseMocker.mock(
    ExternalCaseBuilder.template().externalId(externalId)
);
```

- 本地数据 Mocker 通过 Mapper/Test Repository 准备本服务拥有的数据。
- 外部数据 Mocker 只写入对应 Simulator 的 Scenario Store。
- Mocker 可以保持本场景创建对象，支持链式准备关联数据。
- Mocker 不调用被测业务命令偷偷完成 Arrange。
- `success()`、`rejected()` 等名称必须落到明确业务状态，而不是保存一个待返回的 Response。

## 11. Checker、Probe 与请求记录

Checker 把响应、数据库、Redis、消息和外部调用断言组织成可读 DSL：

```java
invoke(request)
    .success()
    .storedExpectedState()
    .compensationEventCount("ExpectedEvent", 1);

externalRequestChecker
    .calledOnceWith(externalId, idempotencyKey)
    .noUnexpectedCalls();
```

规则：

- Checker 只读取和断言，不创建前置数据、不调用业务命令、不改变 Simulator 行为。
- Probe 只观察真实 HTTP 请求、消息或异步结果，不参与业务响应。
- 集合断言必须验证总数和目标元素；未找到时显式失败。
- 外部调用验证至少包含次数、关键参数、幂等键和必要的关联 ID。
- 失败信息包含期望、实际值、业务标识和已记录请求/事件摘要。
- 失败场景同时验证事务回滚、未发布事件和未发生后续外部调用。

## 12. 测试基类与配置

避免所有测试继承一个启动完整应用的巨大基类：

```text
TestBase
├── ServiceAutoTestBase
├── RepositoryAutoTestBase
└── MessagingAutoTestBase
```

- 纯单元测试通常不继承 Spring 基类。
- `TestBase` 只提供无状态常量、固定时间和确定性 ID 工具。
- `ServiceAutoTestBase` 管理 Spring、随机端口、测试基础设施、Simulator 和统一重置。
- `RepositoryAutoTestBase` 只启动数据库和 MyBatis。
- `MessagingAutoTestBase` 管理 Broker、测试 Producer/Consumer 和消息清理。
- `@BeforeEach` 清理当前测试涉及的数据、缓存、消息、Simulator 状态和 Request Journal。
- 不使用 `@Order`、`PER_CLASS` 或共享可变字段修复测试依赖。

`application-auto.yml` 必须：

- 关闭 Nacos、外部配置中心、定时任务和非本测试需要的真实 Consumer；
- 不包含真实环境 URL、用户名、密码或 Token；
- 由测试动态注入数据库、Redis、MQ 和 Simulator 地址；
- 在启动时拒绝未由测试基础设施注册的外部 Endpoint。

## 13. 数据和状态隔离

- TestMapper 提供按聚合 ID 清理、批量清理和只读测试查询。
- 清理顺序遵守外键，数据库 migration 与生产保持一致。
- Redis 使用独立前缀/数据库并清理相关 Key。
- MQ 使用测试专属 VHost 或唯一 Exchange/Queue Namespace；生产命名规则映射为测试唯一名，并清理消息、Binding 和消费状态。
- Simulator Scenario Store 与 Request Journal 每个测试重置。
- 固定 `Clock`、ID Generator 和随机源在每个测试恢复默认测试状态。
- 如果并行测试共享容器，必须使用唯一业务键、schema、前缀或消息命名空间，禁止互相清理数据。

## 14. 标准测试流程

服务级测试使用 Arrange → Act → Assert，但 Arrange 只准备状态：

```java
@Test
void test_normalFlow() {
    LocalEntity entity = localEntityMocker.mock(LocalEntityBuilder.template());
    externalCaseMocker.mock(
        ExternalCaseBuilder.template().externalId(entity.getExternalId())
    );

    BusinessResponseChecker checker = invoke(
        BusinessRequestBuilder.template().entityId(entity.getEntityId())
    );

    checker.success()
        .storedExpectedState()
        .compensationEventCount("ExpectedEvent", 1);
    externalRequestChecker
        .calledOnceFor(entity.getExternalId())
        .noUnexpectedCalls();
}
```

这个示例中的 Mocker 只写入状态；`invoke` 后的所有处理都经过真实生产实现和真实测试协议。

## 15. 异步与时间

- 禁止使用固定 `Thread.sleep` 判断异步完成。
- 使用有上限的条件等待，例如 Awaitility，持续检查数据库、消息或 Probe 条件。
- 超时失败必须输出尚未满足的条件、相关业务 ID、已记录消息和外部请求。
- 能显式触发 Event Handler、Compensation Dispatcher 或 XXL 扫描 Handler 时，不等待真实定时周期；测试仍必须经过真实 Claim/Lease 和生产 Handler。
- 时间相关规则注入固定 `Clock`；不要修改宿主机时间。

## 16. 用例覆盖

一个业务能力至少评估以下相关场景：

- 参数为空、格式错误和非法枚举；
- 本地权威数据或外部 Simulator 数据不存在；
- 正常成功路径；
- 状态不允许或业务规则违反；
- 金额、时间、集合和分页边界；
- 相同幂等键重复请求；
- 相同幂等键但业务参数不同；
- 并发状态更新或乐观锁冲突；
- 外部业务失败、处理中、协议错误、超时、断连和未知结果；
- MQ 重复、乱序和延迟；
- 事务回滚后数据库、Redis、`compensation_event` 和 MQ 的一致性；
- 不应调用后续外部系统的短路路径。

不是每个方法都机械创建全部场景；根据该能力的真实分支和风险选择，但不能只测试 happy path。

## 17. Maven 执行规则

默认测试生命周期只运行 `auto`：

```text
mvn test
  → include **/auto/**/*Test.java
  → exclude manual
```

`manual` 必须通过独立 Maven Profile 显式运行，例如 `-Pmanual-test`。目录名本身不是隔离措施，父 POM/测试模块必须配置 Surefire 的 include/exclude 或等价 JUnit Tag 规则。

具体 artifactId 创建后使用：

```bash
mvn test
mvn -pl <service>-test -am test
mvn -pl <service>-test -Dtest=SpecificTest test
```

- 默认 `mvn test` 应完成全部 `auto` 闭环并在 CI 使用相同入口。
- 可以用 `unit`、`repository`、`service`、`contract` Tag 做本地筛选，但不能改变默认全量范围。
- 涉及公共契约或父 POM 时运行完整 reactor 测试。
- 目标覆盖率为 90% 以上，但有效状态、分支和副作用断言优先于数字。
- 缺少 Docker 等已声明基础条件时，报告为环境阻塞，不得切换到真实共享环境或声称测试通过。

## 18. 明确禁止的做法

- 使用 `when(...).thenReturn(...)`、`doThrow(...).when(...)` 等方法级行为 Stub 拼装业务流程；
- 用 `@MockBean`、`@MockitoBean` 或 Spy 替换生产链路后声称服务闭环通过；
- 自动测试连接真实微服务、Provider、Nacos、配置中心或共享基础设施；
- 依赖人工启动的固定端口 MySQL、Redis、MQ 或 Mock Server；
- 测试配置写入个人地址、共享账号、真实密码或 Token；
- 所有测试默认使用固定端口的 `@SpringBootTest`；
- 测试依赖固定方法顺序或共享实例状态；
- Builder 访问基础设施或隐式改变 Simulator；
- Mocker 保存一个待返回 Response 来改变后续方法结果；
- Checker 创建数据、重新调用命令或对未找到元素静默通过；
- 用固定 `Thread.sleep`、日志输出或人工观察代替断言；
- 空测试、被注释的测试或只打印 `Running` 的占位测试；
- 为提高覆盖率排除核心业务包或执行代码但不验证结果。

## 19. 新增外部依赖的完成条件

新增任何 HTTP Client、Provider Adapter、Redis 能力或 MQ Producer/Consumer 时，必须同步具备：

- 可由 `auto` 启动的真实测试基础设施或有状态 Simulator；
- 对应 Scenario Builder/Mocker；
- Request/Message Probe 与 Checker；
- 成功、业务失败和相关技术故障场景；
- 未准备场景和意外请求的快速失败机制；
- 每个测试后的完整状态重置；
- 不访问真实环境的动态测试配置。

如果一个新功能只能连接真实环境才能验证，则不能视为测试完成。

## 20. 完成检查清单

- [ ] 测试选择了最小充分层级，但没有绕过需要验证的真实链路。
- [ ] 纯单元测试没有启动 Spring、容器或行为 Stub 框架。
- [ ] 服务级测试经过真实 Controller/Listener、Processor、Domain Service 和 Integration/Repository 实现。
- [ ] 数据库、Redis 和 MQ 在相关场景中由测试自动启动并使用真实客户端连接。
- [ ] 外部 HTTP/MQ 通过有状态 Simulator 和真实协议完成，没有方法级 Stub。
- [ ] Builder 只构建数据，Mocker 只准备状态，Checker/Probe 只观察和断言。
- [ ] 未准备场景、意外请求和多余调用都会显式失败。
- [ ] 响应、本地数据、缓存、事件和外部调用按风险完成联合断言。
- [ ] 每个测试独立、可重复并且不依赖顺序。
- [ ] 没有真实凭据、环境地址、固定端口、固定睡眠或人工观察式断言。
- [ ] 默认 Maven 测试只运行 `auto`，`manual` 需要显式 Profile。
- [ ] 已运行相关 Maven 测试并记录实际结果。
