# DTO Mock 与逐用例测试生命周期

日期：2026-09-17。状态：Confirmed，依据用户对 TransactionFacade payment 测试模式、外部 DTO 响应、两级 BeforeEach 和全局 AfterEach 的确认。

## 决定与 Owner

完整规则由 [UNIT_TEST_GUIDE.md](../UNIT_TEST_GUIDE.md) 唯一维护，[公共示例](../examples/UNIT_TEST_EXAMPLE.md)展示落地形态。根 AGENTS、开发/工程规范和 AI 规则仅作入口与薄投影。

- 保留真实本服务链路和实际出站协议；外部测试 HTTP 接口直接返回 Mocker 中按操作/业务身份配置的 DTO，不要求先建立完整外部 Simulator。
- Builder 构造对象，本地 Mocker 插入数据，外部 Mocker 配置 DTO/故障并记录调用，Checker 只读断言。
- 每例先执行 MockTestBase 的全局 BeforeEach，再执行当前测试类的 BeforeEach，最后准备方法独有数据；公共 Fixture 每次重建。
- 基类 AfterEach 检查意外调用并清理测试专用环境内全部数据和 Mock 状态；失败仍清理，清理失败聚合报告，不复用污染环境。异步先结束再清理，同一环境串行。
- 禁止为了测试在生产业务代码增加分支、Profile Mock、假成功、探针或测试专用行为；测试实现只在测试侧。

## 取代范围

[修订前归档](../UNIT_TEST_GUIDE_BEFORE_20260917.md)只用于解释历史记录；以下要求被取代：

1. 所有外部依赖必须使用有状态 Simulator、Mocker 不能保存待返回响应 DTO。
2. 将每例之前重置、跨用例共享响应或没有统一 AfterEach 的参考代码作为新测试范式。
3. 固定要求旧基类名称/结构，而忽略本次确认的 MockTestBase、两级准备和完整清理职责。

[2026-09-08 中间件配置决定](2026-09-08-middleware-auto-test-configuration.md)中的强制有状态 Simulator 部分按本决定替换；Spring 真实连接、固定测试 YAML、动态端点注入、手工准备基础设施等未受影响要求继续有效。

不改变真实本地数据库/Redis/MQ、auto/manual 边界、入口测试与 Handler 独立测试、覆盖率目标、敏感数据及事务边界。既有测试不因本次文档修改自动视为已迁移；代码调整按后续明确任务实施。

## 配置补充（2026-09-17）

用户进一步确认：Mock 测试的配置应完整集中在 `src/test/resources/application-mock.yml`，使本地基础设施就绪后可以直接从 IDE/Maven 启动。`MockTestBase` 启用 `mock` Spring Profile；`auto` 继续表示默认自动执行集合，不等同于 Spring Profile。

用户补充说明其目的：只查看 Mock 配置文件就能了解系统定义的配置字段。因此该文件同时承担配置清单职责，自定义字段和已接入公共组件字段须列全，包括可选项、代码已有默认值、关闭能力的附属项和运行时注入项；本服务实际使用的框架配置一并列出。字段变更同步此文件，并说明用途、单位、默认值/Mock 值及启用条件。

固定数据库连接、隔离本地凭据、业务必填配置、HTTP 策略、外部服务绑定和后台调度开关写入 YAML，不依赖手工补充必填环境变量。只有随机 Mock 端口等运行时值由测试注入；DTO 响应仍由用例配置，不能全局默认成功。既有 `application-auto.yml` 消费者按任务逐步迁移。生产测试分支禁令、真实数据库及手工 Schema 准备边界保持。

## 公共组件、自动装配与正式启动类补充（2026-09-17）

用户要求先整理前序调整、确认后再更新规范，本轮已确认更新。完整规则继续由测试规范各专题维护，本补充记录相较前序写法的取舍：

- 复用 Foundation 的调用器、协议 Checker、Clear / TestBase 和独立 Mock HTTP 服务；业务数据、DTO 配置和业务断言留在各系统，测试依赖不进入运行制品。
- 清理只在 AfterEach 分派；Mock API 实现正式外部接口，返回正式 DTO，错误不用 Map 复制正常协议体，多参数构造使用测试 Builder。配置清单、集中手工 DDL、场景拆类和具体入口的 `@see` 要求一并归入现行规范。
- 能自动装配的共享测试组件使用容器扫描与构造器注入，取消只负责构造对象的工厂；特殊定制和初始化时机按实际需要保留。用例级 Builder、DTO 与响应 Checker 不强制单例化。
- **服务级入口测试复用正式应用启动类和生产组件扫描**，测试只附加专用配置；不为单个接口手动列举生产 Bean。此项取代前序“共享最小 TestApplication / 优先手工切片装配”的默认要求；纯计算和公共组件机制测试仍按所需范围选择上下文。
- 正式启动类的复用不改变本地基础设施、外部 Mock 和生产隔离边界。Mock Controller 仍独立运行；无关后台任务由已有测试配置关闭，不为启动测试改业务分支或绕过被测能力。

本轮仅统一规范、示例及 AI 薄投影，不迁移 Java 启动代码；已有长串 Import 的测试应用按后续代码任务处理。当前实施与验证范围仍只见[状态台账](../CURRENT_IMPLEMENTATION.md#test-startup-conventions-20260917)。

## 实施范围

原规范任务仅维护本地规范、示例、AI 阅读入口与历史路由。不修改测试框架或业务代码，不执行 SQL，不运行构建/业务测试，不提交或部署。实际文档核对与迁移状态见[当前台账](../CURRENT_IMPLEMENTATION.md#test-dto-mock-lifecycle-20260917)。

上述配置补充已由 Commerce CreateCheckout 测试任务承接；其代码和验证范围见[专项记录](../CURRENT_IMPLEMENTATION.md#commerce-create-checkout-tests-20260917)。
