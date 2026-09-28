# 公共测试组件交付记录

用户确认前序公共测试组件建议，并要求抽取 Checker 基类，本轮据此实施。需求与结构见 [requirement.md](requirement.md)、[technique.md](technique.md)；现行实现、验证结果及缺口统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#foundation-test-support-20260917)。

## 文件范围

- Foundation：Reactor 注册 `paytech-test-support`，三个公开辅助类、真实 Loopback HTTP 测试及使用 README。
- Build：统一 BOM 增加测试组件版本管理，保留已有其他依赖修改。
- Commerce：仅测试模块 POM、HTTP 客户端测试配置、MockTestBase 注入、CreateCheckout invoke 与响应 Checker，以及 README。没有改业务实现、SQL 或清理语义。
- 全局规范：测试规范明确公共组件与领域 Checker 的职责、配置排列与注释规则；技术矩阵与需求索引增加路由。

## 可复现验证

使用仓库 Wrapper 与 JDK 25，先在 `paytech-build` 本机安装 BOM：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -pl paytech-dependencies -am install
```

在 `paytech-foundation` 执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -pl paytech-test-support -am org.jacoco:jacoco-maven-plugin:prepare-agent install org.jacoco:jacoco-maven-plugin:report
```

在 `commerce-service` 执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -Pcheckout-create-tests -DskipTests package
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -Pcheckout-create-tests '-Dtest=CreateCheckoutApiTest#test_createCheckout' test
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -Pcheckout-create-tests -Dtest=CreateCheckoutApiTest test
```

上述命令保留本轮原始验证类名。后续场景拆分后的执行入口见 [Commerce 测试技术说明](../commerce-CreateCheckout测试重建/technique.md#执行)。

最后一个命令当前因两项已记录业务缺口失败，不能当成通过门禁；具体数量、行为与日志见台账。本次测试不自动初始化 Schema。额外通过 ZIP 目录检查 Commerce executable JAR，确认部署依赖中没有公共测试组件、JUnit 或 Spring Test；通过 Maven dependency tree 核对组件及传递测试库均为 test scope。

## 规范自查

- 本次是测试专用公共机制，不是同步业务接口改造，无业务 Processor/Context、Service 事务、锁或提交后外部步骤变更。
- 使用真实 Loopback HTTP 验证公共 API，负例不被工厂修复；Checker 只读响应，延迟解析 JSON，支持无 Body 与非成功状态，子类链式断言保留类型。
- 所有测试实现留在测试专用 JAR 或 Commerce 测试模块，没有生产 Mock、开关或探针。应用及数据库生命周期保留在宿主，没有通用清库器或空的顶层 Checker。
- 保留工作区既有修改，本机安装仅供跨仓库验证；未发布 Nexus、执行 SQL、提交、推送或部署。其他系统接入另行实施。

## 后置清理抽取补充（2026-09-17）

- 用户追加公共 Clear 与 TestBase 要求，并最终确认只在 AfterEach 清理，撤回 BeforeEach 清理方案。
- 新增 `Clear#clear()`、`TestBase` 的 `List<Clear>` 注入与 final AfterEach；用 JUnit assertAll 聚合全部清理，不添加前置清理或生命周期排序机制。
- Commerce 原有数据 Mocker、CCT Mocker、可控 Clock 和事务观察器实现 Clear，MockTestBase 删除手写清理列表。两级初始化保持；宿主先等待异步结束并检查意外调用，异步停止失败时通过 `checkClearAllowed()` 禁止不安全清理。
- 增加实际 JUnit Launcher / Spring Context 生命周期测试，覆盖自动收集、两例隔离、初始化/用例/后置检查失败、多项清理失败汇总和异步停止失败。公共支持模块依赖仍只在业务测试 scope 使用。
- 测试规范、示例和 AI 路由同步；具体代码只涉及测试工件，没有业务 Processor/Context、Service 事务或 SQL 变更。本补充的验证证据见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#foundation-test-clear-20260917)。

可复现命令沿用上方 Foundation 目标模块 install / JaCoCo；Commerce 使用当前场景拆分后的入口：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -Pcheckout-create-tests test
```

## Mock API 接口实现补充（2026-09-17）

- 按用户要求，CCT 测试 Controller 实现正式 CheckoutEligibilityApi；删除手写路径匹配、Query 解析与 CCT 专用 HTTP Server。接口的路径与参数注解由 Spring MVC 继承，无生产 API/客户端改动。
- Foundation 增加 MockHttpServer 和 MockHttpFault：显式 Controller 装配、独立 Servlet 容器、活动请求等待、未匹配请求记录和 Clear；正常 DTO 使用 MVC，配置的错误/畸形响应及实际断连使用测试故障处理。只有测试 scope 消费，具体 API 和业务 Mocker 仍在 Commerce。
- Commerce 仍通过 SimpleDiscovery 将 client-contract-service 指向随机 Mock 地址，继续使用生产 API 代理、LoadBalancer 和错误转换。Mock Controller 标注 TestComponent 并只向独立服务显式提供，避免宿主扫描。
- 新增真实 HTTP 机制测试，并完整执行原 CreateCheckout 场景，未改原测试断言或业务逻辑。验证、覆盖率和制品隔离结果见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#foundation-mock-api-server-20260917)。

### 自动装配补充（2026-09-17）

- EnableMockHttpServer 声明测试包与动态地址键，内部 Registrar 自动注册 Mocker、Controller 内部 Bean、MockHttpServer 和 DynamicPropertyRegistrar；生命周期交给 Spring，Controller 不进入宿主顶层 API/路由。
- Commerce 删除服务器、CCT Mocker 与端点注册的三个 Bean 工厂；Mocker 标注 TestComponent，测试基类继续直接注入。
- 自动装配机制测试覆盖真实 HTTP、共享 Mocker、宿主隔离、Clear 收集、普通组件/配置排除、重复扫描去重、空扫描失败、关闭与重建。验证记录仍只写[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#foundation-mock-api-server-20260917)。
- 用户进一步要求能自动装配的测试组件均自动装配：Commerce 本地 Mocker、数据库 Checker、时钟与事务观察器纳入现有扫描，删除只负责构造的 Bean 工厂；保留客户端构建时机/传输定制和拦截器挂载。旧时钟配置通过类导入继续供旧测试使用，规范第 12 节明确此默认规则。
