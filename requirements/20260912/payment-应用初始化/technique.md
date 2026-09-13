# Payment 应用初始化技术方案

## 依据和范围

按[Java 工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[技术栈](../../../agent-docs/TECHNOLOGY_STACK.md)、[应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md)和[本地环境](../../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md)初始化。原仓库只有数据库设计资料，缺少构建工程与启动入口。

## 工程设计

- 根工程 `paytech-payment-service` 继承 `com.paytech:paytech-parent:1.0.0-SNAPSHOT`，Parent `relativePath` 为空；统一使用 JDK 25、Maven Wrapper 3.9.16、BOM 管理的 Spring Boot 4.0.8。
- `paytech-payment-api` 是轻量契约模块，本期只定义包边界；具体接口和 Models 随业务用例增加。
- `paytech-payment-web` 是唯一部署入口，包名 `com.paytech.payment`，启动类 `PaymentApplication`。引入自己的 API、公共 MVC Starter、Web MVC、Actuator 和 Prometheus。
- `paytech-payment-test` 依赖 Web，禁止生产反向依赖；禁止安装或发布。使用真实 HTTP、随机端口，复用共享 `auto/manual` Maven 规则和 JaCoCo。
- 不预建空 Processor、Service、Repository 或业务能力模块。没有数据库、Redis、MQ、Nacos、XXL-Job、补偿事件和 Provider 连接配置。

## 运行边界

默认应用名 `payment-orchestration-service`，监听 `127.0.0.1:18103`，Context Path `/payment`。Actuator 仅开放 `health/info/prometheus`，关闭发现页和 JMX；Liveness/Readiness 只包含进程状态，隐藏健康组件和详情。

错误与 Request ID 使用[公共 Problem Profile](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)。访问日志直接配置公共 MVC Starter 的 `metadata-only-path-prefixes: [/]`，不复制 Filter/Advice；日志格式输出其键值元信息。没有新增支付业务协议或 Card 数据处理。

## 数据与验证

数据库变更：不涉及。保留 `docs/database/` 原有资产，不生成或执行 SQL。

验证生产启动入口、健康探针、管理端点白名单、Prometheus、统一 404/Request ID 和日志内容边界；构建后以可执行 JAR 核验默认端口并停止验证进程。当前实现状态只维护在[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-application-foundation)。
