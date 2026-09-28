# 业务入口测试示例

本例配合 [UNIT_TEST_GUIDE.md](../UNIT_TEST_GUIDE.md) 使用，展示 Builder、Mocker、Checker 和两级生命周期的组合方式。**这是文档模板，不是已经存在、编译或运行过的测试实现。** 类名、DTO 字段、SQL 和 API 路径必须替换为当前系统的真实合同；省略 import、依赖注入构造器和普通 getter/setter，不复制为新的生产接口。

## 1. 示例合同与组件

为避免混淆“支付受理”和“支付成功”，本例使用一个抽象取消场景：已存在的未支付订单关联一个 Payment Intent；下游确认终止后，本地订单才进入 CANCELLED。订单已支付时应拒绝且不调用下游。该假设只服务于示例，实际取消规则和状态仍读取业务 Owner。

| 测试组件 | 最小职责 |
|---|---|
| `GlobalDataMocker` | `mockDefaults()` 插入相同的全局公共配置 |
| `OrderMocker` | `mockClassDefaults()` 插入本类配置；`mockUnpaidOrder()` / `mockPaidOrder()` 插入本例订单及必要关联 |
| `TestDataCleaner implements Clear` | `assertEmpty()` 检查测试环境基线和可复用状态；`clear()` 清理该专用环境的所有测试数据，SQL 在 TestMapper；失败标记环境不可复用 |
| `PaymentMocker implements Clear` | 保存终止响应 DTO、请求记录和意外调用，提供断言及清理 |
| `MockPaymentController` | 在独立 Mock HTTP 服务中实现真实 Payment 接口合同 |
| `OrderCheckerFactory` | 从真实数据库加载订单及必要关联，生成只读 Checker |
| `CancelOrderHttpClient` | 测试侧薄封装，用公共 HttpTestInvoker 发真实 HTTP 请求，返回业务 ResponseChecker |

这些辅助能力都位于测试模块。表中方法应按已有测试设施复用或实现，不引入新的业务 Service，也不因模板列出名称就预建空类。

HTTP 和统一 AfterEach 清理复用 [`paytech-test-support`](../../paytech-foundation/paytech-test-support/README.md)，依赖限定为 test scope。
本例调用器只保留接口路径、请求适配与 Checker 构造；简单场景也可直接写成测试类的 `invoke` 方法。
业务 ResponseChecker 继承 `AbstractHttpResponseChecker<具体类型>`，只补业务断言，不重复实现状态、Header 和 Problem 检查；数据库 Checker 无需继承 HTTP 基类。

## 2. 两级初始化和全局清理

以下业务场景同步访问数据库与 Mock HTTP，启动时复用正式应用。下例的 `CommerceApplication` 指本服务已经存在的正式启动类，其他服务替换为自己的正式类，不另建一个同名测试应用。同一环境串行执行；应用和 Mock HTTP 服务可共享启动成本，业务数据与 Mock 状态逐例重建。

测试只附加第 4 节的 Mock 配置；现有 `@AutoConfigureRestTestClient` 自动加载 Foundation 公共 HTTP 测试组件，复用宿主传输与随机端口，无需手写客户端配置。正式类上的 `@SpringBootApplication` 负责生产组件扫描，不在测试中再次列举 Controller、Processor、Service、Repository。`application-mock.yml` 负责本地依赖、外部地址和无关后台任务开关；被测 Handler 的执行另按其真实入口组织。

```java
@SpringBootTest(classes = CommerceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mock")
@AutoConfigureRestTestClient
@Import(ExternalMockConfiguration.class)
public abstract class MockTestBase extends TestBase {

    @Autowired
    protected GlobalDataMocker globalDataMocker;

    @Autowired
    protected TestDataCleaner testDataCleaner;

    @Autowired
    protected PaymentMocker paymentMocker;

    @Autowired
    protected MockHttpServer mockHttpServer;

    /** 每例重新准备全局公共数据，不承接上一例的状态。 */
    @BeforeEach
    protected final void setUpGlobalMocks() {
        testDataCleaner.assertEmpty();
        paymentMocker.assertEmpty();
        globalDataMocker.mockDefaults();
    }

    /** 子类先检查意外调用；即使失败，JUnit 仍执行父类的清理。 */
    @AfterEach
    protected final void tearDownGlobalMocks() {
        try {
            mockHttpServer.awaitIdle();
        } catch (RuntimeException | AssertionError failure) {
            testDataCleaner.markUnusable();
            throw failure;
        }
        assertAll(paymentMocker::assertNoUnexpectedRequests, mockHttpServer::assertNoUnexpectedRequests);
    }

    /** 未结束的请求可能继续写入，公共清理必须先确认环境可安全回收。 */
    @Override
    protected void checkClearAllowed() {
        testDataCleaner.assertReusable();
    }
}
```

- 初次运行需要干净且专用的测试环境；不要在 `assertEmpty()` 中偷偷删除数据。若环境缺失，报告本地准备步骤。
- `TestDataCleaner.markUnusable()` 与 `assertReusable()` 在本示例中维护当前共享测试环境的可复用状态；前者标记禁止复用，后者在标记后抛出断言失败。使用已有测试侧隔离工具实现，不向业务代码增加开关；它的 `assertEmpty()` 同样先检查此状态。
- 清理失败由实际清理组件保存不可复用标记，并在下次基线检查时拒绝继续执行；跨进程隔离仍依靠独立 Schema/Namespace 或独占串行执行安排。
- 子类使用不同方法名，由 JUnit 自动先执行父类再执行子类；不覆盖父类、不手动调用 `super`。
- 清理组件实现 Clear 并注册为测试 Bean，由 TestBase 的 List 注入收集；新增外部 Mocker、Redis/MQ 或其他共享状态时无需维护手写清理清单。公共 AfterEach 使用 assertAll 汇总错误；有依赖时使用 Spring Bean 排序，不依赖扫描顺序。
- 只在 AfterEach 清理，BeforeEach 只检查和初始化。JUnit 自动先执行子类 AfterEach 再执行父类，不手调父类清理方法。
- 有异步写入时，必须在检查和清理前有界等待本例任务结束或停止。停止失败时先令环境不可复用，并覆盖 `checkClearAllowed()` 阻止不安全清理，不能擦除仍会被回写的数据。完整规则见[规范第 13、15 节](../UNIT_TEST_GUIDE.md#13-全局清理与独立性)。

## 3. 外部 DTO Mocker

这里只实现“终止”一种操作，因此 Map Key 为 `intentId`；增加查询等操作时分 Map 或显式增加操作维度。跨调用方 ID 不唯一时加入 Client 等真实作用域。不要使用一个无条件全局 Response。

`TerminationResponseInfo` 代表对方真实响应 DTO。请求日志只记录不敏感的 `intentId`；实际需要断言金额、业务操作号等字段时，增加最小只读调用快照，不能保存完整敏感请求。

```java
@TestComponent
public class PaymentMocker implements Clear {

    private final Map<String, TerminationResponseInfo> responses = new HashMap<>();

    private final List<String> calls = new ArrayList<>();

    private final List<String> unexpectedCalls = new ArrayList<>();

    /** 直接配置本用例需要的响应 DTO；配置后不再修改同一对象。 */
    public synchronized void mockTermination(String intentId, TerminationResponseInfo response) {
        responses.put(Objects.requireNonNull(intentId), Objects.requireNonNull(response));
    }

    /** 由 Mock HTTP Controller 调用，保留协议链路与调用证据。 */
    public synchronized TerminationResponseInfo terminate(String intentId) {
        calls.add(intentId);
        var response = responses.get(intentId);
        if (response == null) {
            unexpectedCalls.add(intentId);
            throw new IllegalStateException("未配置终止响应：" + intentId);
        }
        return response;
    }

    /** 同时检查数量与身份，不允许没有匹配元素仍通过。 */
    public synchronized void assertOnlyTerminationOf(String intentId) {
        assertEquals(List.of(intentId), calls);
    }

    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用 Payment，实际：" + calls);
    }

    /** 即使生产代码捕获 Mock HTTP 错误，仍由 teardown 让测试失败。 */
    public synchronized void assertNoUnexpectedRequests() {
        assertTrue(unexpectedCalls.isEmpty(), "存在未配置调用：" + unexpectedCalls);
    }

    public synchronized void assertEmpty() {
        assertAll("Mock 应处于空基线",
            () -> assertTrue(responses.isEmpty()),
            () -> assertTrue(calls.isEmpty()),
            () -> assertTrue(unexpectedCalls.isEmpty()));
    }

    /** 由公共 TestBase 在每例结束统一调用。 */
    @Override
    public synchronized void clear() {
        responses.clear();
        calls.clear();
        unexpectedCalls.clear();
    }
}
```

本例未引入无当前需求的通用注册中心、脚本语言或外部数据库。需要顺序响应时单独增加队列并明确耗尽失败；需要 HTTP 500、断连或超时时在测试 HTTP 层配置协议故障，不能把“业务失败 DTO”当作超时。

## 4. Mock HTTP 装配

```java
// 位于 test/mock/external；只装配到独立的 Mock HTTP 应用。
@RestController
@TestComponent
class MockPaymentController implements PaymentTerminationApi {

    private final PaymentMocker paymentMocker;

    MockPaymentController(PaymentMocker paymentMocker) {
        this.paymentMocker = paymentMocker;
    }

    @Override
    public TerminationResponseInfo terminate(TerminationRequestBody requestBody) {
        return paymentMocker.terminate(requestBody.getIntentId());
    }
}
```

`PaymentTerminationApi` 是示意名称，落地时直接使用真实对方 API 的方法、路径、校验和错误合同，不能为了测试新增这个生产接口。第三方没有共享 API JAR 时在测试侧实现它的真实 Wire Contract。

有共享 `@HttpExchange` API 时，实现类直接继承方法和参数映射，不重复声明路径、不手写 URI/Query 解析。Mocker 标注 `@TestComponent` 后，由公共组件自动扫描和装配：

```java
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.example.test.mock.external", "com.example.test.mocker",
    "com.example.test.checker", "com.example.test.util"},
    baseUrlProperty = "test.payment.base-url")
class ExternalMockConfiguration {
}
```

包名替换为本服务测试模块的实际包。扫描范围包含 Mock API、本地/外部 Mocker、共享 Checker 及其他测试工具；需要注入的辅助类标注 `@TestComponent` 并使用构造器注入，不手写只负责 new 的 Bean 工厂。普通组件和配置类不被此测试扫描自动导入；同一个 Mocker Bean 供用例与 Controller 共享。Controller 作为服务内部 Bean 交给独立 MVC，不进入宿主 API 代理或路由的候选集合。基类通过 `@Autowired` 使用 MockHttpServer 与 Mocker，公共组件创建服务、动态注册地址并负责关闭。YAML 将动态地址映射到对应服务名实例列表，客户端继续走真实 API 代理和 LoadBalancer。每例等待服务空闲、检查意外请求后执行 Clear。

上述测试扫描只处理测试辅助组件，生产组件仍由正式启动类扫描。测试 Mapper 使用现有 MapperScan；公共 HTTP 客户端由 Foundation 自动装配，传输策略仍由宿主配置负责。Builder、DTO 和每次响应对应的 Checker 在用例中构造，不注册成共享单例。

装配顺序必须明确：

1. 测试夹具启动最小 Mock HTTP 服务，绑定 `127.0.0.1` 的随机端口，只注册 Mock Controller 与所需序列化/错误处理。
2. 本例 Mocker 实例同时供 Mock Controller 和测试用例使用；不能各建一份而导致配置与消费分离。
3. 在被测应用创建真实出站 Client 前，使用项目测试配置机制注入 Mock 服务地址。固定配置完整放在 `application-mock.yml`，动态注入只承担运行时端点。
4. 被测应用仍使用真实 Client、序列化、HTTP 传输和错误转换；不使用 `@MockBean` 替换 Client，不把 Mock Controller 扫进被测应用。
5. 在测试上下文关闭时释放创建的 Mock HTTP 服务；每例只清其配置与记录，不启停仓库 MySQL/Redis/RabbitMQ。

未知路径、错误方法、无法解析的请求可能还没进入 Mocker 就被框架拒绝。测试 Mock HTTP 服务也应记录这些未匹配请求，并纳入同一个清理前检查；不能只对已经进入 Controller 的调用做检查。错误响应仍使用所模拟接口的合同，不能新增生产错误处理分支。

## 5. 用例、Builder 和 Checker

```java
/**
 * 取消订单的确认终止与已支付拒绝场景。
 *
 * @see ClientOrderApi#cancelOrder(long, long, String, String)
 */
class CancelOrderApiTest extends MockTestBase {

    @Autowired
    private OrderMocker orderMocker;

    @Autowired
    private OrderCheckerFactory orderCheckerFactory;

    @Autowired
    private CancelOrderHttpClient orderHttp;

    /** 只准备本类所有用例共同需要的配置。 */
    @BeforeEach
    void setUpClassMocks() {
        orderMocker.mockClassDefaults();
    }

    @Test
    void test_cancelAfterPaymentConfirmsTermination() {
        orderMocker.mockUnpaidOrder("order-001", "intent-001");
        paymentMocker.mockTermination("intent-001",
            TerminationResponseBuilder.terminated().build());
        var request = CancelOrderRequestBuilder.template()
            .orderNo("order-001").build();

        var response = orderHttp.invoke(request);

        response.assertCancelled("order-001");
        orderCheckerFactory.create("order-001").assertCancelled();
        paymentMocker.assertOnlyTerminationOf("intent-001");
    }

    @Test
    void test_rejectPaidOrderWithoutCallingPayment() {
        orderMocker.mockPaidOrder("order-002", "intent-002");
        var request = CancelOrderRequestBuilder.template()
            .orderNo("order-002").build();

        var response = orderHttp.invoke(request);

        response.assertCancellationRejected();
        orderCheckerFactory.create("order-002").assertPaid();
        paymentMocker.assertNoCalls();
    }
}
```

`OrderMocker` 通过 TestMapper 插入并提交前置事实；不调用另一个测试或被测下单接口偷偷制造数据。Builder 只组装请求，`.orderNo(null)` 必须能生成缺字段负例，`build()` 不自动补回默认值。

CheckerFactory 读取真实数据库。创建 Checker 时先断言订单存在；涉及关联集合时检查数量、目标存在性和字段。`assertCancelled()` 的期望来自本例约定的 CANCELLED，不能从响应复制状态再比较数据库，从而让两边一起错仍通过。负例必须验证原事实保留，以及不该发生的外部调用/写入/消息；本例中的零调用检查不能只依赖没有响应配置。

HTTP Checker 按真实成功 DTO 与 RFC 9457 错误合同区分协议结果和业务结果。业务拒绝、处理中、HTTP 故障、超时未知结果另立用例；断言其应有事实，不机械断言所有失败都不落库。

## 6. 默认执行和串行配置

在已有 Parent 管理的 Surefire/JUnit 版本与配置上做必要增量，不重新声明版本。以下仅展示 auto 路径与单 JVM 的关键配置：

```xml
<configuration>
    <includes>
        <include>**/auto/**/*Test.java</include>
    </includes>
    <excludes>
        <exclude>**/manual/**</exclude>
    </excludes>
    <forkCount>1</forkCount>
    <reuseForks>true</reuseForks>
</configuration>
```

`src/test/resources/junit-platform.properties`：

```properties
junit.jupiter.execution.parallel.enabled=false
```

命名不以 `Test` 结尾时同步实际 include 规则；manual 使用显式 Profile 替换选择集合，检查 Maven 配置合并后仍不会进入默认执行。这些设置不隔离其他 Maven 进程，不授权多个任务共用一个可全局清理的测试环境。

## 7. AI 交付自查

按[规范第 20 节](../UNIT_TEST_GUIDE.md#20-完成检查清单)检查，并说明真实入口、Mock 外部合同、两级初始化、全局清理覆盖范围及实际执行结果。本示例不构成测试框架已实现的证据，也不要求创建全部示例辅助类后才写第一个用例。
