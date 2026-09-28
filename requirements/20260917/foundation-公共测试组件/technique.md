# 公共测试组件技术说明

本次涉及 Foundation、Build BOM 与 Commerce 测试模块；新增一个测试专用 JAR，新增业务 HTTP 接口、数据库表和字段均为 0。

## 类型与边界

公开包为 `com.paytech.foundation.testsupport.api`：

- `HttpTestInvoker` 接收宿主配置好的 `RestTestClient`。`get/post/exchange` 发出一次请求，保留原始 `EntityExchangeResult<byte[]>`；地址、超时、自动重试/重定向策略仍由宿主传输配置负责。调用器不补 Header，不修复非法请求，不断言业务成功。
- `TestRequestHeaders` 每次返回独立可修改的 `HttpHeaders`。Client 构造可信身份 Header，Customer/内部只提供 JSON/Request ID 基线，其访问凭据、调用方身份及网络信息由接口按合同补充。Idempotency-Key 不自动添加，当前 Commerce 旧接口占位保留在本地 invoke。
- `AbstractHttpResponseChecker<S>` 保存实际结果并按需解析 JSON；`self()` 保持链式子类类型。公共断言覆盖协议，订单/金额/状态断言留在 Commerce。数据库 Checker 无共同实现时不强制继承 HTTP 基类，不建立空的顶层 AbstractChecker。
- `Clear` 定义 `void clear()`；`TestBase` 注入 `List<Clear>` 并在 final AfterEach 中用 assertAll 调用全部组件。没有 BeforeEach 清理。子类的 AfterEach 先结束异步和检查调用，公共清理随后执行；异步宿主可覆盖 `checkClearAllowed()` 拒绝不安全清理。具体数据范围和环境不可复用标记仍由宿主负责。
- `MockHttpServer` 只装配传入的测试 Controller，以独立 Tomcat / MVC 上下文监听 Loopback 随机端口，API 注解由 MVC 原生读取。Controller 使用宿主传入的 Mocker 实例，不加入业务上下文。服务器跟踪活动请求及未匹配/错误请求，实现 Clear，结束时 close；MockHttpFault 统一表达 JSON、原文与实际断连故障。

自动装配补充：宿主以 EnableMockHttpServer 指定 Mock API / Mocker 包和动态地址键；公共 Registrar 只扫描其中标注 TestComponent 的类，排除配置类。Mocker 注册为宿主 Bean，Controller 使用 Spring 内部 Bean 定义进行构造器注入和生命周期管理，只交给独立 MVC，不作为宿主顶层 API Bean 或路由候选。MockHttpServer 与 DynamicPropertyRegistrar 自动注册，服务关闭时释放端口与内部 Controller。每个上下文一个服务，既有显式构造入口保留供纯机制测试使用。

该显式启用的测试扫描取代前序“不做类路径扫描”的限制，不启用 Boot 应用自动配置或扫描业务组件，不把 Checkout、CCT、DataObject、Mocker 或数据库清理 SQL 搬入 Foundation。MockTestBase 继承公共 TestBase，自动注册的 Clear Bean 继续参与后置清理；两级 BeforeEach 保持初始化职责。

## 工程与消费

版本由 `paytech-build/paytech-dependencies` 管理。模块正常发布为 JAR，JUnit API 和 Spring Test 是其实现依赖；消费者必须使用 test scope，使整个传递闭包仅对测试可见。生产 Common Core、Starter、API/Web 不依赖该模块。

Commerce 仅改测试 POM、HTTP 测试配置、MockTestBase、CreateCheckout invoke、响应 Checker，以及数据 Mocker、CCT Mocker、测试 Clock 和事务观察器的 Clear 接入。后续 Mock API 调整新增测试侧 MockCheckoutEligibilityApi 并删除 CctCheckoutMockServer，服务发现仍用原配置。正式业务服务源码不参与本轮变更。

## 验证

先编写真实 HTTP 行为测试并记录公共 API 缺失的失败，再实现公共组件。Foundation 指定模块及依赖运行测试和 JaCoCo；本机制不访问数据库。Commerce 使用已批准的 `checkout-create-tests` 切片编译/打包及定向运行，并检查 Web 制品与 runtime 依赖。

仅本机安装 BOM 和 JAR 以验证跨仓库消费，未发布 Nexus。已有测试缺列时继续明确失败，不执行 DDL，不降低测试断言。
