# Java 单元测试与自动化测试规范

最后更新：2026-09-19

状态：Confirmed。本文是 Code-MoR 新增及本次修改测试的唯一现行规范。确认与取代范围见[测试规范决定](decisions/2026-09-17-test-dto-mock-lifecycle.md)；[修订前版本](UNIT_TEST_GUIDE_BEFORE_20260917.md)仅用于历史解释。规范生效不表示既有测试已迁移或验证，实际状态见[当前台账](CURRENT_IMPLEMENTATION.md#test-dto-mock-lifecycle-20260917)。

本次整理纳入已确认的测试职责、真实数据库与外部 DTO Mock、公共组件、两级初始化和后置清理、自动装配、配置与 DDL、用例拆分及入口引用；服务级入口测试的启动方式按第 12 节复用正式应用，取代此前手动列举生产组件的切片装配方式。具体写法见[公共示例](examples/UNIT_TEST_EXAMPLE.md)，实施状态不由本文推断。

## 1. 使用范围

编写或修改 Java 测试、测试基类、Builder、Mocker、Checker、测试数据和执行配置前，必须实际读取本文及本次涉及的业务接口合同。完整落地示例见 [UNIT_TEST_EXAMPLE.md](examples/UNIT_TEST_EXAMPLE.md)。示例中的辅助类是测试侧示意，不能假设项目已经实现。

本文所称单元测试包含项目习惯中的服务级业务自动测试；`auto` 是默认自动执行集合，不限于不启动 Spring 的纯单元测试。纯计算与纯 Java 对象测试按第 5 节选择最小范围。

AI 开始测试任务时先做以下工作，再实施已授权修改：

1. 查看工作区和既有测试模块，保留用户修改；读取被测入口、真实调用链及现有测试工具。
2. 明确本次入口、场景、真实数据库事实、需要 Mock 的外部接口和需要检查的副作用。
3. 区分全局公共数据、当前测试类公共配置和当前方法独有数据；复用现有工具，不预建空类。
4. 实现用例及所需测试辅助能力，交付前按第 20 节核对；如缺少真实业务能力或环境，明确报告，不能用生产 Mock 补齐。

本规范不自动授权执行 SQL、修改生产逻辑、删除共享数据、提交或部署。当前用户明确限定不运行测试时如实记录未执行范围。

## 2. 核心原则与生产代码红线

**测试从真实业务入口执行本服务代码。本地前置数据通过插入准备；外部接口通过测试专用接口返回配置好的 DTO。**

- 本服务的 Controller、Processor、Service、Repository、Mapper 和出站 Client/HTTP 代理使用真实实现。
- 测试通过配置把真实出站 Client 指向本地 Mock HTTP 接口，不用方法 Stub 替换本服务生产链路。
- 外部 Mocker 可以直接配置响应 DTO；不要求先实现外部系统的数据库、状态机或领域模型。
- 测试用例描述业务场景，Builder 构建对象，Mocker 准备数据或响应，`invoke` 调用入口，Checker 读取和断言。
- 用例验证响应、业务事实及相关正反副作用，不以覆盖率、日志或“没有抛异常”代替断言。

**禁止为了测试在业务逻辑中增加任何测试行为。** 例如：

```java
// 禁止出现在生产业务代码中。
if (mockEnvironment) {
    return fabricatedSuccess;
}
```

同样禁止生产模块中的 `@Profile("mock")` 模拟实现、测试开关、固定成功结果、伪造下游编号、测试计数器/故障注入点、绕过校验或事务的入口，以及仅供测试使用的回调或访问方法。默认关闭、改名或把条件移到另一个生产类都不构成隔离。

Mock Controller、DTO 配置、调用记录、故障控制和生命周期工具只放 `src/test/java`、`src/test/resources` 或已批准的测试专用工件，不能进入生产运行依赖和制品。测试应用允许使用测试 Profile 加载连接配置，但生产业务不得据此改变语义。测试发现真实业务缺陷时按实际业务要求修复，不能仅为使测试通过改变规则。

## 3. 参考项目结论

参考 `Code-PMX/transaction` 的 `TransactionFacade#payment`：独立测试模块，公开 Facade 入口，Builder/Mocker 场景准备，ResponseChecker 与数据库 CheckerFactory，基类统一初始化和清理。

保留其可读的业务测试结构，不照搬旧 Dubbo 传输或已发现的缺口：共享响应未重置、缺少调用记录、集合目标不存在仍通过、全局/子类同名初始化需手动调用 `super`、在一个测试中混杂多种场景、继承并调用另一个 `@Test` 准备数据。

当前项目使用 HTTP，所以外部替身实现对应 HTTP 合同；内存保存预设响应 DTO 是已批准的写法。仍然禁止用 Mockito 等替换本服务内部方法来拼接成功链路。

## 4. 测试模块结构

```text
<service>-test/
├── pom.xml
└── src/test/
    ├── java/<base-package>/
    │   ├── auto/
    │   │   ├── ext/http/              # 业务接口入口测试
    │   │   ├── ext/eventhandler/      # Handler 入口测试
    │   │   ├── domain/                # 纯规则、计算、状态机
    │   │   └── repository/            # 有独立 SQL/约束风险时
    │   ├── manual/                    # 显式运行的真实环境联调
    │   └── test/
    │       ├── base/                  # TestBase、MockTestBase、清理协调
    │       ├── configuration/         # 测试应用、Mock 服务及客户端地址
    │       ├── builder/               # 请求、DB 对象、外部响应 DTO
    │       ├── mocker/local/          # 本地数据库/缓存前置数据
    │       ├── mocker/external/       # 外部响应配置及调用记录
    │       ├── mock/external/         # 测试专用外部 HTTP 实现
    │       ├── checker/               # 响应、数据、外部调用断言
    │       ├── dal/                   # 测试专用 TestMapper
    │       └── util/                  # 有实际用途的测试工具
    └── resources/
        ├── application-mock.yml         # MockTestBase 使用；固定配置完整
        ├── application-auto.yml         # 既有未迁移测试按需保留
        ├── application-manual.yml
        └── junit-platform.properties
```

- `test` 模块依赖 `web`，生产模块不得反向依赖 `test`；共享测试工件只以 test scope 消费。
- 用例放在 `auto/manual`，`test` 包只放辅助工具。按已有包名和能力组织，不为目录完整制造空文件。
- 不为每个测试新建应用启动配置，也不为了 Mock 创建新的生产模块。

### 4.1 公共测试组件

通用 HTTP 测试和后置清理能力由 Foundation 的 [`paytech-test-support`](../paytech/foundation/paytech-test-support/README.md) 提供，各服务测试模块显式以 `test` scope 引入，版本由统一 BOM 管理；业务 API、Web 和其他生产工件不得依赖它。

- `HttpTestInvoker` 使用宿主配置好的真实 `RestTestClient`，发出请求并返回原始结果；不修复输入、不补 Header、不默认断言成功。超时、禁重试和禁重定向由宿主传输配置负责。
- `TestRequestHeaders` 提供 Client、Customer 和内部调用的请求头工厂，每次返回独立可修改的 Header；不默认添加 Idempotency-Key。Customer/内部入口的身份和访问凭据按实际接口合同补充，不能套用 Client Gateway 身份。
- `AbstractHttpResponseChecker<S>` 统一状态、媒体类型、Header、Request ID、空响应、Problem 和字段错误断言。业务 ResponseChecker 继承它，继续维护订单、支付、退款等业务断言，公共断言后可以链式调用子类方法。
- `Clear` 定义组件自身的清理能力；`TestBase` 注入测试上下文的 `List<Clear>`，只在 `@AfterEach` 聚合执行全部清理。新增清理组件实现接口并注册为测试 Bean，无需再维护手写清理清单。
- `MockHttpServer` 在独立的本机随机端口 Servlet 服务中装配显式传入的 Mock Controller；有共享 API JAR 时，测试实现该 API，由 Spring MVC 复用接口映射和参数绑定，不手写另一份路径分发与 Query/Body 解析。Mock Controller 不进入宿主业务上下文，真实出站 API 代理和 LoadBalancer 保持原样，测试仅配置服务发现实例地址。
- `MockHttpFault` 仅供测试 Controller 配置 HTTP JSON 错误、畸形响应和实际断连。公共服务记录未匹配请求、绑定错误及未处理异常，宿主在清理前统一检查；正常响应仍直接返回对方 DTO。
- Spring 测试通过 `@EnableMockHttpServer` 声明 Mock API 和测试辅助组件的扫描包；组件标注 `@TestComponent`，Controller 同时保留 MVC 注解。公共组件自动注册本地/外部 Mocker、共享 Checker、时钟和观察器等，以内部 Bean 创建并注入 Controller、创建服务器、动态注册地址并管理关闭；基类直接注入，无需逐个手写 Bean 工厂。Controller 不进入宿主顶层 Bean / 路由，避免与真实 API 代理冲突；配置类和普通组件不被自动扫描。
- 应用启动、两级 BeforeEach、业务数据、外部 DTO Mock、数据库 Checker 和具体清理实现继续留在各服务。宿主先完成异步停止和意外调用检查，再由公共基类清理。没有共同实现的 Checker 不强制继承一个空的顶层基类。

组件使用说明只提供公共机制，实际接入及验证状态见[当前台账](CURRENT_IMPLEMENTATION.md#foundation-test-support-20260917)。

## 5. 测试分层

| 场景 | 入口和执行范围 |
|---|---|
| 服务级业务测试 | 复用正式应用启动类，经过真实 HTTP 入口、本服务生产链、真实本地数据库及相关中间件，外部为本地 Mock HTTP 接口 |
| Handler 测试 | 真实 Handler 或生产 Dispatcher，保留需要验证的调度、持久化和后续动作 |
| Repository 测试 | 真实 MyBatis 与本地数据库，验证独立 SQL、映射和并发约束风险 |
| 纯 Java 测试 | 直接调用公开解析、计算、值对象或状态机，不强制 Spring/数据库/MockTestBase |
| 普通 Foundation JAR | 从公开 Java API 或自动配置入口验证；涉及数据库/Redis时用真实基础设施，不把纯机制测试说成完整业务证明 |

### 5.1 以可执行入口组织测试

测试围绕业务入口及风险组织，不按 Service 方法或生产类数量机械建测试。HTTP 测试实际发出请求，覆盖绑定、校验、异常转换和生产链；内部模块公开接口本身为业务入口时可以直接调用。

**具体入口测试类的类级 JavaDoc 必须使用 `@see` 引用被测接口的具体方法。** HTTP 测试引用正式 API 契约方法；直接测试 Service、Handler 等入口时引用其实际入口方法，不用 Mock、测试调用器或内部辅助类代替。按场景拆分的每个测试类都单独保留引用；覆盖多个入口时分别列出，方法签名变化时同步维护。引用应能在 IDE 解析跳转，使用正确的参数类型消除重载歧义，例如：

```java
/**
 * 创建成功、金额计算和合法边界。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
```

示例类型需要对应 import 或使用全限定类名。`@see` 是 JavaDoc 标签，不是 Java 运行时注解；不为此修改业务接口。

**Handler 与 HTTP Controller 一样是独立入口，新增或修改业务 Handler 必须有独立入口测试类。** HTTP 用例间接执行 Handler 不能代替该测试。Job 可显式触发生产 Handler；MQ 协议通过真实本地 Broker 进入 Listener；补偿重试/Claim/Lease/完成回写需经过实际 Dispatcher/Scanner。生产 Handler 不添加测试探针。

正常补偿事件通过 Handler 的事件工厂构造；负例仅改变本次待验证字段，不让旧事件类型先拦截其他分支。事务与外部调用观察全部在测试侧完成。

### 5.2 同一接口的用例拆分

**测试类是否拆分以及拆成几个，由功能复杂度、场景规模和可读性决定，没有固定数量要求。优先保持简单，简单功能使用一个测试类即可。**

存在成功、参数校验、异常等不同场景，并不自动意味着需要多个测试类。例如，简单详情查询的成功、参数错误和不存在场景可以放在同一个 `GetXxxTest` 中；各场景仍使用独立测试方法。

只有场景较多、职责或前置配置差异明显，放在一个类中影响阅读和维护时，才按业务场景拆分并归入该操作的包。类名说明测试范围，不用 `Part1/Part2`，不按固定行数、用例数量或每个测试方法机械拆类。已有接口拆成四类只是该接口的组织选择，不是所有接口的模板；不得为了凑齐“成功、校验、异常、并发”等分类创建无必要的测试类。

入口公共路径、`invoke` 和确有复用的辅助方法放在 `test` 包的接口测试基类，基类不放 `@Test`。各具体测试类继续通过自己的 `@BeforeEach` 配置公共 DTO/数据，`MockTestBase` 负责全局准备与清理；参数源保留在使用它的场景类中。共用现有测试应用，不能为拆文件新增启动配置或共享未清理的业务状态。

测试类数量不决定覆盖范围；保留一个类也要覆盖适用的业务风险。拆分或合并时保留原场景、参数组合和断言，同步 Maven 编译/执行选择与文档命令，并核对发现的用例数量、通过/失败集合；禁止通过遗漏文件或排除失败场景让结果变绿。

## 6. 服务级 auto 标准链路

```text
测试 HTTP 客户端
  → 真实 Controller → Processor → Service → Repository/Mapper → 本地测试数据库
                        ↓ 事务外
                    真实出站 Client / @HttpExchange 代理
                        ↓ HTTP
                    本地 Mock Controller
                        ↓
                    ExternalMocker：匹配请求，记录调用，返回预设 DTO
```

Mock Controller 必须属于独立的外部 Mock HTTP 服务/应用上下文，在随机 Loopback 端口启动；不能误扫进被测应用，造成路径冲突或绕过真实出站请求。通过测试专用配置让实际客户端访问该端口，保留真实转换和错误处理。

Mock 服务可在一组测试间复用生命周期，但响应配置和请求记录必须逐用例重建/清理。现有只返回 unavailable 的外部适配器不是已实现的 HTTP Client；若本次只授权写测试，应报告缺口，不能改成生产 Mock 或声称完成真实协议覆盖。

## 7. 测试基础设施

### 7.1 数据库

- 使用与项目一致的真实数据库，复用手工启动的仓库本地基础设施；不使用 H2 或 Testcontainers 替代。
- Schema 和基础设施初始化 DML 由开发者手工应用，测试不建表、不迁移、不运行服务初始化脚本。
- 当前 Mock 需要的完整建表语句集中放在测试模块 `src/test/resources/init/ddl.sql`，包括业务写入、查询、Checker 和清理所依赖的表。沿用正式 Schema 的完整字段、约束、索引与注释，表结构或测试依赖变化时同步维护；用于手工初始化空测试 Schema，已有数据库使用评审后的增量 SQL，不加入自动执行或重建数据库逻辑。
- 该文件同时包含本地测试数据库、用户创建及本库授权语句，使管理员可从同一入口准备完整环境；账号与 `application-mock.yml` 保持一致，已有库和账号不自动删除或重置。测试账号只授予当前测试所需的本库权限，不授予全局或其他业务库权限；仅允许使用仓库隔离本地默认凭据。
- **每个用例的公共业务数据和场景数据由 Mocker 在生命周期内插入，这是测试 Fixture，不是 Schema 初始化。** 数据操作集中于 Mapper/TestMapper/测试 Repository，不在用例散落 SQL。
- 测试专用数据库/Schema 的全部测试数据可以统一清理；与开发数据或其他任务共用时必须隔离，不能直接整库/整表删除。

### 7.2 Redis

生产链涉及 Redis 时使用真实本地客户端与测试专用 Namespace，验证相关值、TTL、锁等；清理所有本测试环境拥有的 Key，不执行共享实例 `FLUSHDB`。没有 Redis 业务就不引入 Redis 测试设施。

资源锁验证选择实际资源，覆盖相关竞争、释放、中断与 Lease 失效下的数据库保护；当前 AbstractLockProcessor 与既有注解锁分别遵守各自合同。不能用 Mock 锁成功证明并发安全。

### 7.3 MQ

涉及消息时使用真实本地 Broker 和测试专用 Namespace。入站走真实 Listener；出站由测试消费者记录，Checker 检查内容、次数及不应发布的消息。清理队列/记录前先结束本用例生产和消费工作，不删除其他环境的队列或拓扑。

事实登记（2026-10-18，V2 搬迁第 12x 步）：`paytech-messaging` 以 test-jar 发布 `PartitionQueueBaseline` 与 `PartitionQueueBaselineConfiguration`，是消费方 `-test` 模块的标准接入。模块以 `type=test-jar`、`scope=test` 引入 `com.paytech:paytech-messaging`，在测试组合根 `@Import(PartitionQueueBaselineConfiguration.class)`；它在分区监听容器启动前按宿主实际订阅重复声明拓扑，并清空本服务的 `<consumer>.<producer>.p*` 与 DLQ，每例 purge 仍由本服务 Mocker 负责。已接入的模块为 acquiring、client-master、card。

### 7.4 Vault 与敏感数据

Vault 及其他外部服务同样允许配置 DTO，由测试专用 HTTP 接口返回；只有用例实际需要多次相关交互时才加入最小状态。只使用合成测试值，遵守当前业务 Owner 已批准的敏感字段范围，不通过 Mock 放宽生产合同。

请求日志和 Checker 只保留必要字段；不能为断言持久化 PAN/CVV、密钥、真实 Token 或敏感原文。响应、错误和日志的零泄漏场景按当前接口风险验证。

### 7.5 本地中间件生命周期

测试不创建、停止或重建 MySQL/Redis/RabbitMQ 容器。依赖缺失或 Schema 不匹配时明确失败并提示准备方法，不静默跳过，不改连真实环境。测试只清理自身数据，不通过停止共享中间件制造故障。

### 7.6 本地共享服务规则

地址、端口和固定本地测试账号统一由 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](LOCAL_DEVELOPMENT_ENVIRONMENT.md)及仓库 `local-environment` 配置确定，不在本文件维护第二份版本/端口清单。Mock 测试的固定连接配置集中于 `application-mock.yml`，复用 Spring 管理的真实客户端；Java 动态配置只注入运行时 Mock 端口、隔离标识和明确的场景变量。

自动测试禁用 Nacos、真实配置中心和无关定时扫描；只连接指定的 Loopback 中间件与测试创建的外部 Mock 服务。仅隔离、Loopback-only、无生产权限的本地测试账号可以版本化维护固定凭据；真实环境凭据不能进入文件。

“全局清理”是清理当前测试专用环境的全部数据和 Mock，不是清空共享物理实例。不同进程/任务复用同一中间件时必须有独立数据库/Schema、Redis/MQ Namespace 或串行独占执行安排；JUnit 类内串行不能隔离其他进程。

## 8. 外部接口 DTO Mock

### 8.1 最小组成

| 对象 | 职责 |
|---|---|
| `PaymentMocker` | 按操作和业务标识配置响应 DTO，保存调用记录及意外调用；提供清理 |
| `MockPaymentController` | 实现对方 HTTP 合同，读取请求，委托 Mocker，输出 DTO/约定故障 |
| `PaymentRequestChecker` | 检查实际请求的关键参数、调用次数和没有发生的调用 |
| `PaymentResponseBuilder` | 响应构造干扰场景表达时提供合法默认 DTO 与具名字段覆盖 |

类名仅示意，优先复用现有实现。小 Mocker 可以直接返回具名 Checker，无需额外 CheckerFactory；不引入没有消费者的通用 Mock 框架。

### 8.2 匹配与返回

- 默认以“操作 + 业务标识”匹配。例如同一 Intent 的查询与终止分别配置；跨调用方标识不唯一时增加必要作用域。
- 直接配置响应 DTO，保持当前测试独有；配置后不再修改同一个对象，避免期待值随响应一起改变。
- 普通用例可用 Map 保存“匹配键 → 响应 DTO”的配置；响应体本身复用正式 DTO。确需先处理中后成功时支持明确的顺序响应；序列耗尽明确失败，不自动循环或回退成功。
- 没有配置、关键身份不匹配、序列耗尽均记录为意外调用并使测试失败，不能默认成功或返回 null。
- 仅在 Mock HTTP 线程抛异常还不够：生产业务可能捕获该异常。清理前必须检查意外调用记录，让这种用例也失败。 未知路径、错误方法或反序列化失败可能尚未进入 Controller；测试 Mock HTTP 层也要记录这些意外请求并纳入同一检查。
- 调用记录保存必要的稳定字段和次数；不通过记录整份敏感请求图省事。
- Mock 服务不拥有真实业务事实，不需要实现外部数据库。需要创建后查询等连续交互时，仅增加当前测试必须的状态。

### 8.3 故障

业务成功、拒绝、处理中用 DTO 表达；HTTP 错误用约定状态和错误体；延迟、断连和畸形响应由测试 HTTP 服务控制。故障不通过修改生产 Client 返回值或在业务代码增加开关实现。

第一方 HTTP 错误直接使用公共 `ApiProblemResponse`，不手写 Map 复制 type、title、code 等协议字段；只有明确测试缺失字段、非法类型或畸形响应时才使用原始 JSON 等负例输入。

## 9. 允许的替身与禁止的替换

**允许：** 外部 Mock Controller 根据请求匹配 Mocker 中的 DTO；测试配置连接地址；测试模块内的协议故障控制；按需最小外部状态。

**禁止：** `when(repository.load(...)).thenReturn(...)`、`@MockBean`/`@MockitoBean`/Spy 等替换本服务 Repository、Service、Processor、出站 Client/HTTP 代理来声称服务链路通过；直接返回一个未按请求匹配、跨测试共享且未清理的全局 Response。

本节取代旧版“Mocker 不得保存待返回 DTO”“所有外部 Mock 必须实现有状态 Simulator”的要求。使用真实 HTTP 的预设 DTO 响应，不等同于在生产方法上打 Stub。

## 10. Builder 与 Mocker

### 10.1 Builder

- `template()` 提供最小合法默认对象；用例只覆盖与场景有关的字段。
- 请求和外部响应 DTO 均适用：参数较多、存在难辨认的布尔/null 位置参数或重复默认值时，使用测试侧 Builder，避免在用例中展开构造器参数列表。Builder 放在本服务测试模块，复用正式 DTO，不为测试修改生产 DTO。
- Builder 不访问数据库、Redis、MQ 或网络，不登记响应或改变全局状态。
- 支持明确设置 null、非法枚举等负例输入；`build()` 不能偷偷修复用例刻意设置的非法值，也不能做生产同等业务校验而使请求到不了被测入口。
- 公共基线使用稳定值；用例自己的业务标识可显式指定或隔离生成。不得依赖当前随机结果构造无法重现的断言。

### 10.2 本地数据 Mocker

调用 Mapper/TestMapper 插入配置与业务前置数据，返回本次创建的对象/身份供用例关联。可链式构造有关联的配置，不调用被测业务命令偷偷完成 Arrange，不自行提交覆盖整个测试方法的事务。

### 10.3 外部 Mocker

通过清晰方法配置 DTO 和故障，提供按操作/业务身份读取的调用记录；可供 Checker 使用。每个 Mocker 必须纳入统一清理，不要求测试方法手动逐项清理。全局或测试类不预设所有外部接口成功，避免掩盖意外调用。

## 11. Checker、Probe 与请求记录

- ResponseChecker 检查 HTTP/协议结果及业务状态，区分“请求正常受理但业务失败”和“请求本身失败”。
- 数据 Checker 从真实数据库读取并核对订单、关联行、金额、币种、状态及相关版本/时间，不只看内存 Context。
- CheckerFactory 负责注入查询依赖、构建单次 Checker；Checker 不保存跨用例状态。按实际需要使用，不强制每个断言一套 Factory。
- 外部调用 Checker 验证次数、关键参数、稳定业务操作身份和零调用路径；HTTP Idempotency-Key 属于 Gateway 专项测试，不强迫业务测试新增该字段。
- 集合必须检查数量和目标存在性；未找到显式失败，不只在循环匹配时断言。金额比较遵循项目 Money 与币种精度，不只比展示字符串。
- Checker/Probe 只读和断言，不创建前置数据、不推进命令、不修改响应配置。预期值来自用例业务约定，不重新调用被测算法计算，不从实际结果复制。
- 失败信息给出安全的业务标识、期望和实际值。失败场景检查应保留的数据、应回滚的写入和不应发生的副作用，不能把所有失败都理解为零落库。

## 12. 测试基类与配置

```text
TestBase                         # Foundation：List<Clear>、统一 AfterEach
└── MockTestBase                  # 正式应用 + 测试配置、全局准备、异步停止和调用检查
    └── AbstractXxxApiTest        # 按需提供 invoke 和入口公共辅助
        └── XxxApiTest           # 本类公共配置及具体测试方法
```

**应用启动与自动装配：**

- 服务级入口测试复用本服务正式的 `@SpringBootApplication` 启动类，使用生产已有的组件扫描、配置和依赖注入；不再为单个接口通过长串 `@Import` 手工枚举 Controller、Processor、Service、Repository 等生产 Bean，也不维护一套平行的业务装配清单。
- 测试只附加测试专用配置，包括 Mock HTTP 服务、Fixture/Checker、测试 Mapper、可控时间和必要的 HTTP 客户端定制。同一服务的入口测试复用公共启动与配置，不为每个接口或场景类新建独立启动应用。正式应用可直接传给 `@SpringBootTest`；确需测试组合类时，仅引用正式启动类及测试配置，不重新拼装生产链。
- 正式启动类决定生产组件如何装配；`application-mock.yml` 决定本地连接和运行配置。复用正式启动类不等于连接真实业务环境：禁用 Nacos/真实配置中心，外部地址指向测试 HTTP 服务，无关后台任务通过已有配置关闭；被测 Handler/Job 的实际执行仍遵循第 5.1 节。缺失必要隔离能力时明确报告，不在业务代码新增测试分支或任意排除生产组件以掩盖问题。
- **能够自动装配的测试组件默认自动装配。** 本地/外部 Mocker、共享数据库 Checker、时钟、观察器等使用 `@TestComponent` 和构造器注入，复用已有测试扫描或类导入；不编写仅 `return new Xxx(容器依赖)` 的 Bean 工厂。仅第三方对象定制、显式工厂参数或特殊初始化时机等确有需要时保留 `@Bean`，不为自动装配新增一层同义配置。
- Mock Controller 由公共 Mock 服务隔离装配，不进入被测应用的业务路由或真实 API 代理候选集合。测试声明扫描范围和服务发现映射，公共组件负责创建、动态端点注册和关闭；正常注入、初始化与销毁遵循 Spring 语义。
- 按请求构造的 Builder、DTO、响应 Checker 仍由用例创建，不强制注册为共享 Bean。纯计算、值对象和公共组件机制测试继续按第 5 节选择必要上下文，不为此启动完整业务应用。

**两级 `@BeforeEach` 加用例独有准备：**

| 位置 | 职责 | 时机 |
|---|---|---|
| `MockTestBase.setUpGlobalMocks()` | 所有用例需要且相同的公共数据/Mock | 每个测试方法前 |
| 当前测试类 `setUpClassMocks()` | 当前类需要的公共业务配置 | 每个测试方法前，父类之后 |
| `@Test` 方法内 | 当前用例独有数据、DTO 或故障 | 调用被测入口之前 |
| `MockTestBase.tearDownGlobalMocks()` | 异步停止及意外调用检查 | 每个测试方法后 |
| `TestBase.clearAfterEach()` | 执行所有 Clear 并聚合清理失败 | 子类 AfterEach 之后 |

- 初始化方法使用不同名称；父类全局方法可声明 final。子类不覆盖、不手动调用 `super.setUpGlobalMocks()`，由 JUnit 生命周期执行，防止漏执行/执行两次。
- 清理仅放在公共 `@AfterEach`；`@BeforeEach` 检查空基线并重建公共数据，不安排前置清理。父类 AfterEach 由 JUnit 在子类之后执行，不手动调用 `clearAfterEach()`，不为生命周期引入额外排序逻辑。
- 公共业务数据不能放 `@BeforeAll`，不能依赖上一例留下的行。类级生命周期只复用启动成本，如 Mock HTTP 服务本身。
- 不通过 `PER_CLASS`、`@Order` 或固定顺序修补状态依赖。使用默认逐方法测试实例，Spring 单例 Mocker 仍需显式清理。
- `MockTestBase` 使用 `@ActiveProfiles("mock")`，固定配置完整放在测试资源 `application-mock.yml`，动态 Mock 地址在被测应用创建客户端之前注入。
- `application-mock.yml` 同时是本系统的配置字段清单。系统自定义配置、已接入公共组件的配置字段，以及本服务实际使用的框架配置均须显式列出；不能因字段可选、有默认值、能力关闭或当前测试未使用而省略。中间件、框架和公共组件配置放在上方，系统自定义配置放在下方；公共配置不逐项添加冗长注释，自定义字段说明必要的用途、单位、默认值与 Mock 取值差异和启用条件。运行时注入项也要列明键与注入来源；真实凭据只用安全占位。
- 新增、修改或删除配置字段时同步更新 `application-mock.yml`，对照配置类和配置消费点核对完整性，使读者只看此文件即可了解本系统的配置。保留关闭能力的字段不代表启用它们，也不要求罗列框架全部未采用的通用选项。
- `application-mock.yml` 配齐所需数据库连接、隔离本地默认凭据、业务必填配置、HTTP/服务发现和调度开关；本地基础设施及 Schema 就绪后，IDE/Maven 不需要额外设置必填环境变量。随机 Mock 端口等运行时值允许使用由测试注入的占位符，不能把静态配置重新散放到注解或 Java 中。响应 DTO 仍按用例配置。
- `auto` 是 Maven/JUnit 的默认执行集合，`mock` 是 Spring 测试配置 Profile，两者职责不同。既有 `application-auto.yml` 消费者按本次授权范围迁移，不机械改动其他服务。
- 不给整个测试方法加回滚事务冒充完整隔离；生产 Service 的提交/回滚边界应真实发生。测试准备事务需在请求前提交，清理使用独立操作。
- 纯规则和公共组件机制测试按第 5 节选择测试基类；没有 DB/外部依赖时不制造假初始化流程。

完整的父类、子类和清理示例见[公共测试示例](examples/UNIT_TEST_EXAMPLE.md)。

## 13. 全局清理与独立性

正常生命周期固定为：

```text
全局 @BeforeEach → 本类 @BeforeEach → 方法准备 → 调用与断言
  → 结束本用例异步工作 → 检查意外调用 → 全局 @AfterEach 清理
```

- 正常完成、断言失败和准备过程中失败，都由适用的 JUnit 清理回调尝试释放本例已取得的资源；不把清理放在 `@Test` 尾部。进程被强制终止不保证执行回调，重新运行前需显式恢复测试专用环境。
- 清理覆盖测试数据库内所有本环境数据、Redis/MQ 测试状态、外部响应 DTO、故障/顺序响应配置、调用与意外调用记录、测试时钟/编号工具等共享可变状态。依赖拓扑决定安全清理顺序，不能只清响应而遗留数据库或计数器。
- 先记录/断言意外调用，再清除记录。使用 JUnit `assertAll` 或等价测试侧聚合机制，某项失败仍尝试其他清理，最后完整报告；不能吞异常当作成功，也不能让首个断言阻止 teardown。
- 新规范 Spring 测试继承公共 TestBase；拥有可变状态的 Mocker、时钟和观察器等实现 Clear 并注册为测试 Bean，由 List 注入统一收集。确有清理依赖时使用 Spring 的 Bean 排序；SQL 留在 TestMapper，公共基类不承载业务。旧测试按授权范围迁移。
- 宿主有异步工作时在自身 AfterEach 中先等待结束；停止失败后标记环境不可复用，并通过 `checkClearAllowed()` 拒绝会被后台继续写入的清理。同步测试无需覆盖此方法。
- 全局清理失败后该环境不能视为干净，后续用例不能继续复用污染状态；本次运行应失败并停止复用，恢复后重新运行。
- 同一测试专用环境默认串行；包括同一 Maven JVM 内的类和方法。多个任务/进程并行需独立环境，不能只依靠 JUnit 串行配置。

## 14. 标准测试流程

每个用例只表达一个明确场景，成功、业务拒绝、系统故障、重复业务操作分别组织。相同校验结构的多组输入可以参数化。

```java
@Test
void test_paymentSuccess() {
    // Arrange：本类公共配置已由两级 BeforeEach 完成，只准备本例差异。
    paymentMocker.mockPayment(intentId, PaymentResponseBuilder.success().build());
    var request = PayRequestBuilder.template().orderNo(orderNo).build();

    // Act：invoke 经过真实业务入口。
    var response = invoke(request);

    // Assert：响应、数据库事实与外部调用分别检查。
    response.assertSuccess();
    orderCheckerFactory.create(orderNo).assertPaid("10.00", "AED");
    paymentMocker.checker(intentId).calledOnce().amount("10.00").currency("AED");
}
```

以上 API 仅为写法示意；完整自洽示例见链接文档。不能假设“支付受理”必然已把订单置为 PAID；实际断言遵守当前业务合同。调用另一个 `@Test` 方法准备前置数据禁止；复用 Builder/Mocker 或明确的测试 Fixture。

## 15. 异步与时间

- 使用有超时上限的条件等待观察数据库、事件或调用记录，不用固定 `Thread.sleep` 或日志判断完成。
- 本例异步任务完成，或通过测试侧可控生命周期确认停止后，才能清理它可能继续写入的资源。
- 无法停止时明确失败并停止复用该环境，不能清完数据后让后台线程继续回写。其余不受该任务影响的清理仍应尝试并报告。
- 故障与观察工具位于测试侧，不为等待异步修改生产分支、增加测试任务模式或计数器。
- 有现成 Clock/时间配置时使用确定性输入；不能为了测试新增业务环境分支或修改宿主机时间。

## 16. 用例覆盖

按入口业务风险选择，至少评估：

- 正常成功、无数据、当前状态不允许及归属错误。
- 参数空值、非法格式/枚举、金额/币种/时间/集合边界。
- 业务唯一编号重复、同一业务身份改内容、数据库竞争/条件更新失败。
- 外部拒绝、处理中、HTTP 错误、超时和未知结果。
- 失败后的本地原子性、已提交事实保留、没有后续外部调用/消息等正反副作用。
- 有异步能力时的未决、完成、重复消费及恢复；有锁时相关竞争与释放。

Gateway 请求 Key、72 小时和首次 HTTP 响应重放归 Gateway 测试；业务服务继续验证自身防重。外部超时不证明操作未执行，不能在测试里把未知当作确定失败或期待盲目重做。

## 17. Maven 执行规则

默认 Maven/CI 只运行 `auto`，`manual` 通过明确 Profile 显式运行。目录名本身不能保证隔离，Surefire 必须有对应 include/exclude 或等价 Tag 配置；测试模块不发布、不部署。

同一测试环境默认关闭 JUnit 并行，使用一个测试 JVM；具体 Surefire/JUnit 配置与版本复用项目父 POM，不自行引入版本。示例文件给出最小配置片段。

测试应支持单个方法、单个类及全部 auto 独立运行。只有实际运行过才能报告通过；构建通过、测试源码存在或文档示例不能证明数据库/HTTP/并发测试成功。

## 18. 明确禁止的做法

- 为测试修改生产业务分支、增加 Mock Profile、固定成功、测试探针或绕过入口。
- 替换本服务内部链路，或直接 Stub 出站 Client 来冒充真实协议覆盖。
- 外部 Mock 对未知请求默认成功、返回 null，或被业务捕获后不检查意外调用。
- 公共业务 Fixture 只初始化一次；依赖前例结果、共享未重置 DTO 或方法顺序。
- 子类覆盖全局初始化却漏调父类，或重复手动调用生命周期。
- 只在测试成功时清理；首个清理失败后不再执行其他安全清理；异步仍写入时清库。
- 整体清理共享开发库、其他任务数据、共享 Redis/MQ 或通过启停基础设施制造故障。
- Builder 修复负例、Checker 推进业务、从实际结果生成预期值、缺少目标元素仍通过。
- 为覆盖率机械建内部方法测试、排除核心逻辑、写空测试或只有日志的测试。

## 19. 新增测试能力的完成条件

新增出站依赖测试时，应具有真实本服务 Client、对应 Mock HTTP 接口、DTO 配置、调用记录/Checker、未配置失败、每例清理和相关故障场景；已有端口模拟只能声明它实际证明的范围。

新增本地数据库/中间件测试时，应明确测试专用资源、数据准备与全局清理范围、必要事务/竞争场景。新增 Handler 时有独立入口测试。

代码或环境尚不支持时明确缺口，不建立没有当前用例的通用 Simulator 或生产替身。覆盖率目标仍为 90% 以上，业务断言和关键风险优先，不通过排除生产代码凑数字。

## 20. 完成检查清单

- [ ] 已读取现行规范和业务合同，确认被测真实入口及应有事实，没有把示例当真实 API。
- [ ] 测试位于独立模块；生产代码、依赖和制品没有测试实现/分支。
- [ ] 服务级入口测试复用正式启动类，测试只附加专用配置，没有逐个手工列举生产 Bean。
- [ ] 可自动装配的共享测试组件通过容器注入；保留的 Bean 工厂有实际定制或生命周期理由，Mock Controller 与业务路由隔离。
- [ ] 本地数据真实插入，本服务链路真实执行；外部通过实际 Client 访问测试 HTTP 接口。
- [ ] 外部直接配置 DTO，按操作和业务标识匹配；调用记录完整且不保存敏感原文。
- [ ] 成功/错误响应复用正式 DTO；多参数请求和外部响应使用测试 Builder，不用手写 Map 复制正常协议体。
- [ ] 未配置/意外调用会让测试失败，即使业务捕获了 Mock 异常；零调用路径有断言。
- [ ] 两级 BeforeEach 每例执行且方法名不同，没有手动 super/BeforeAll 公共业务 Fixture。
- [ ] 基类 AfterEach 在断言失败后仍清理；清理动作集中，失败聚合报告，未遗漏新增 Mocker。
- [ ] 异步先结束再清理；污染环境不被后续用例复用；同一环境串行。
- [ ] 响应、数据库和副作用按场景断言；集合检查数量和存在性，预期值独立。
- [ ] Handler 有独立入口测试；没有逐 Service 方法机械建测试或相互调用 Test 方法。
- [ ] 每个入口测试类的类级 JavaDoc 有可解析的 `@see`，指向其实际被测接口方法；拆分场景类没有遗漏。
- [ ] 测试类数量与功能复杂度、场景规模和可读性相称；简单功能可保留一个类，需要拆分时才按场景组织并共用入口调用和生命周期，不机械套用四类模板。公共测试机制复用 Foundation，业务 Mock 与断言留在各服务。
- [ ] 数据库/Redis/MQ 只操作本环境资源，基础设施预先手工准备，不访问真实环境。
- [ ] Mock 配置已对照系统配置类及消费点列全字段，含默认/可选/关闭能力；用途、单位、默认/Mock 值及动态来源清楚。
- [ ] Mock 所需的建库、建用户、授权和完整建表 SQL 集中在测试 init/ddl.sql，应用及测试不自动执行。
- [ ] Maven 默认只运行 auto，manual 显式；报告实际运行结果或明确未执行原因。
