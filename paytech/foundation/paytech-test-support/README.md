# 公共测试支持

为服务测试提供真实 HTTP 调用、独立 MVC Mock 服务、请求头构造、协议 Checker 和统一后置清理。消费方只使用 `test` scope：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-test-support</artifactId>
    <scope>test</scope>
</dependency>
```

版本由 PayTech BOM 管理。工具实现是本测试专用 JAR 的 `src/main/java`，自身验证位于 `src/test/java`；业务服务的生产模块及制品不得依赖本 JAR。

## HTTP 调用

```java
@AutoConfigureRestTestClient
@SpringBootTest(classes = ServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiTest {
    @Autowired
    HttpTestInvoker http;
}
```

沿用 Boot 的 `@AutoConfigureRestTestClient`，无需额外注解、`@Import` 或手写 Bean。公共配置只在该注解启用时加载，不因 JAR 在 Classpath 就参与普通应用启动。真实 Servlet 服务测试复用宿主 `ClientHttpRequestFactory`，保留超时、禁自动重试/重定向、随机端口和 Context Path，并按顺序应用全部 `RestTestClientBuilderCustomizer`。客户端与调用器延迟初始化，显式定义的同类型 Bean 优先；没有实际服务器或宿主传输工厂时保留 Boot 的客户端装配。

注入后发送请求：

```java
var headers = TestRequestHeaders.client(requestId, clientId, credentialId);
headers.remove(ApiHeaderNames.GATEWAY_CREDENTIAL_ID); // 保留缺失 Header 负例
var response = http.post(path, headers, requestBody);
```

超时、禁自动重试/重定向策略仍由宿主传输配置负责；公共配置不另设这些策略。纯机制测试仍可通过 `new HttpTestInvoker(restTestClient)` 手工构造。`get/post/exchange` 返回实际状态、Header 和原始响应字节，不自动断言成功，也不补回被删除的 Header。可以发送原始 JSON 字符串、字节及明确的媒体类型；null Body 表示不发送内容。

`TestRequestHeaders.customer(requestId)` 与 `internal(requestId)` 提供独立 JSON/Request ID 基线，不携带 Client Gateway 身份。Checkout Token、调用方认证、网络信息和其他必要 Header 由接口自身按合同补充；无默认 Idempotency-Key。每次创建独立 Header，对一个请求的修改不影响其他请求。

Client 工厂模拟的是 Gateway 转发给业务服务的请求。Gateway 认证测试必须实际调用 Gateway，不能用该工厂证明认证通过。

## Checker 基类

```java
public final class OrderResponseChecker extends AbstractHttpResponseChecker<OrderResponseChecker> {
    public OrderResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected OrderResponseChecker self() {
        return this;
    }

    public OrderResponseChecker assertOrderNo(String expected) {
        assertEquals(expected, body().path("orderNo").asString());
        return this;
    }
}
```

使用示例：

```java
checker.assertStatus(201).assertRequestId().assertOrderNo(expectedOrderNo);
checker.assertProblem(422, "VALIDATION_ERROR").assertFieldError("amount");
```

公共断言包括状态、媒体类型、单个 Header、Request ID、空 Body、项目 Problem 格式和字段错误。JSON 仅在需要时解析，HEAD/204 可以只检查协议头及无内容。Problem 状态及错误码由用例提供；标准 title、UTC 六位小数 timestamp 和 Header/Body Request ID 一致性由基类校验。

业务 Checker 继承该基类并维护领域断言。数据库 Checker、Fixture、Mocker、TestMapper、应用启动和具体清理实现留在各服务，不为统一类名增加空继承层。

## 统一清理

```java
@TestComponent
public class PaymentMocker implements Clear {
    @Override
    public void clear() {
        responses.clear();
        calls.clear();
    }
}

public abstract class MockTestBase extends TestBase {
    @BeforeEach
    protected final void setUpGlobalMocks() {
        // 检查环境基线并重新准备公共数据。
    }

    @AfterEach
    protected final void checkExternalCalls() {
        paymentMocker.assertNoUnexpectedCalls();
    }
}
```

宿主使用 Spring 测试上下文，将需要清理的组件注册为 Bean 并实现 `Clear`。`TestBase` 注入 `List<Clear>`，仅在 `@AfterEach` 调用全部 `clear()`；不在 BeforeEach 清理，不要求维护另一份回调清单。公共基类的 AfterEach 在子类 AfterEach 之后执行，子类检查失败也会进入清理。某个 Clear 失败仍尝试其他组件，最后汇总失败。清理间确有依赖时沿用 Spring `@Order`，不依赖 Bean 扫描顺序。

两级 BeforeEach 继续负责全局和当前测试类的初始化。宿主有异步工作时先等待停止，再检查意外调用；无法停止则记录环境不可复用，并覆盖 `checkClearAllowed()` 拒绝不安全的清理。具体清理仅回收自有数据；清理失败后的环境不可复用判定仍由宿主负责。公共组件不包含 SQL、业务 Mock、自动建表或业务应用启动配置。

## 实现外部 API 的 Mock Controller

以下 `EligibilityApi`、DTO 和 Mocker 是业务侧类型的示意，实际引用对方的 API JAR：

```java
@RestController
@TestComponent
public class MockEligibilityApi implements EligibilityApi {
    private final EligibilityMocker mocker;

    public MockEligibilityApi(EligibilityMocker mocker) {
        this.mocker = mocker;
    }

    @Override
    public EligibilityResponseBody check(String clientId) {
        return mocker.responseFor(clientId);
    }
}

```

Mocker 标注 `@TestComponent`，测试配置声明一次扫描包即可；不需要逐个编写 Bean 工厂或静态字段：

```java
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.example.test.mock.external", "com.example.test.mocker.external"},
    baseUrlProperty = "test.eligibility.base-url")
class ExternalMockConfiguration {
}
```

`@EnableMockHttpServer` 来自本组件的 `api` 包。它只扫描指定包内的 `@TestComponent`，排除配置类和普通组件：本地/外部 Mocker、共享 Checker、时钟和观察器等成为宿主 Bean，Controller 由 Spring 作为 MockHttpServer 的内部 Bean 创建并注入依赖，不进入宿主的顶层 Bean 集合或 HTTP 路由，避免与真实出站 API 代理发生类型冲突。初始化、关闭和销毁统一由容器管理，测试基类直接 `@Autowired MockHttpServer` 和具体辅助组件。

每个测试上下文创建一个 MockHttpServer，承载该扫描范围内的 API。公共组件自动注册 DynamicPropertyRegistrar，在宿主客户端创建前将随机地址写入 `baseUrlProperty`（默认 `test.mock-http.base-url`）；宿主 YAML 继续负责把该地址映射到所需的服务名。扫描范围没有 Mock Controller 时启动失败。包范围声明一次，之后新增已标注的 Mock API / Mocker 不必追加 Bean 工厂。

公共服务使用独立 Tomcat / Spring MVC 上下文，绑定 `127.0.0.1` 随机端口，只注册筛选出的 Controller 实例。MVC 直接读取 API 上的 `@HttpExchange` / 方法和参数注解；实现类不再复制路径或手工解析 Query/Body。没有业务组件扫描、Boot 自动配置、数据库或 Nacos 启动。Controller 的协作者与用例共享，Foundation 不依赖业务 API 包。纯机制测试仍可通过 `new MockHttpServer(controller)` 显式构造和关闭。

宿主在创建真实 Client 前，将 `server.baseUrl()` 注入测试服务发现对应服务名的实例列表；真实 API 代理和 LoadBalancer 保持原样。Mock Controller 只装配到此独立服务，不能被宿主应用扫描，也不能替换宿主的出站 API 代理。Boot 测试侧 Controller 标注 `@TestComponent`，避免被宿主测试组件扫描误收集；独立服务通过显式传入实例装配。

每例先 `server.awaitIdle()`，再 `server.assertNoUnexpectedRequests()` 和业务 Mocker 的调用检查，最后统一 Clear；服务器本身实现 Clear 并清理基础设施错误记录，具体 DTO 配置仍由 Mocker 清理。上下文结束时 `close()` 释放服务器。未知路径、错误方法、参数绑定失败和未处理异常均记录为意外请求。

正常路径返回真实响应 DTO。测试协议故障时可抛出 `MockHttpFault.json(status, problemDto)`、`raw(status, malformedJson)` 或 `disconnect()`，由服务统一写出配置响应或实际断开连接；明确配置的故障不记为意外请求。超时仍由测试侧屏障控制，客户端发生真实超时后释放请求并等待结束。此能力只在测试工件中使用。

## 本地验证

使用 JDK 25，在 `paytech-foundation` 执行：

```bash
./mvnw -pl paytech-test-support -am test
```

测试使用临时 Loopback HTTP / Servlet 服务，以及实际 JUnit/Spring 生命周期验证清理，不需要业务数据库或其他中间件。更完整的依赖、验证和迁移状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#foundation-test-support-20260917)。
