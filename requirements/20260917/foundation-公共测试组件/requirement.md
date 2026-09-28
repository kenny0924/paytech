# 公共测试组件

用户已确认在现有 Foundation 中增加测试专用模块，并补充要求抽取 Checker 基类；本次承接前序讨论实施本地代码和针对性验证。

## 范围

- `paytech-test-support` 提供 HTTP 调用器、入口请求头工厂、HTTP 响应 Checker 基类和 Clear / TestBase 后置清理机制。
- Client Gateway、Customer Gateway、内部 HTTP 请求共用传输；身份字段按入口提供，不统一注入 Client 身份或请求幂等键。
- 公共 Checker 检查 HTTP 状态、Header、Request ID、媒体类型、空 Body、Problem 和字段错误；业务 Checker 保留金额、订单等业务断言，并保持链式调用的具体类型。
- Commerce CreateCheckout 是首个接入方。Payment 及其他系统的测试迁移另行实施，不因本模块存在视为已接入。
- 独立 JAR 的实现位于自身 `src/main/java`，消费者只以 `test` scope 引入，不进入业务运行制品；不改业务代码、数据库或应用启动。
- 后续确认：需要清理的组件实现 Clear，公共 TestBase 注入 List<Clear>，**只在 AfterEach 清理**；用户已撤回 BeforeEach 清理方案。不引入前置清理或生命周期优先级机制，两级 BeforeEach 保持初始化职责。
- Mock API 补充：公共 MockHttpServer 承载独立 MVC Controller；Commerce 测试新增 MockCheckoutEligibilityApi 实现正式 CheckoutEligibilityApi，由 MVC 继承接口映射、参数绑定和 JSON 转换，删除手写路径拦截与 Query 解析。真实 Client / LoadBalancer 和测试服务发现映射保持。
- 自动装配补充：用户要求 MockHttpServer、Mock API 与 Mocker 不再逐个手写 Bean 工厂。测试声明一次扫描范围，公共组件完成测试组件注册、共享依赖注入、服务创建、随机地址注册和关闭；Mock API 不参与宿主的业务路由或 API 代理注入。

## 验收

1. 真实 Loopback HTTP 验证 DTO/原始内容、Header 覆盖与删除、非成功及空响应，调用器不自行重试或断言成功。
2. Checker 能发现错误码、请求关联、Problem 字段及时间格式错误；业务子类可继续链式断言。
3. Commerce 新创建测试编译并使用公共组件；实际运行遇到既有 Schema 阻塞须如实报告。
4. 核对测试作用域、生产包隔离和公共模块覆盖率。不执行 SQL，不向 Nexus 发布，不提交或部署。
5. 真实 JUnit/Spring 生命周期验证自动收集、两级初始化与后置清理顺序、失败仍清理及多错误汇总；Commerce 保留异步停止后才清理的限制。
6. 真实 HTTP 验证继承 API 的 GET/Header/Query 和 POST Body 映射；未知路径、错误方法与缺参可观察；已有错误、畸形响应、断连、超时和并发场景保持。
7. 自动扫描只处理指定测试组件，Mocker 共享且可纳入 Clear；Controller 不暴露为宿主 Bean/路由，空扫描明确失败，容器关闭和重建正确释放并重建服务。
