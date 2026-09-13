# Payment 应用初始化

本次用户要求：“Commerce 暂时保持这样吧，下一步帮我先初始化一下 Payment 服务这个应用，好像现在还没有，按照规范来。”

目标是在现有 `payment-orchestration-service` 独立仓库建立可构建、可启动、可验证的核心服务基础工程，供后续 Payment 用例实现使用。

范围包括 Maven Wrapper、统一 Parent/BOM、标准 `api / web / test` 模块、Spring Boot 入口、最小管理端点、公共 MVC 能力、自动测试及运行说明。

本次不实现支付、退款、渠道路由、NI、Card、Commerce 联调、数据库读写或外部基础设施，不修改 Commerce、Payment PDManer 或已有 Schema。不注册 Gateway、不发布、不部署，也不自动提交代码。业务功能继续分别确认范围。

验收标准：

1. JDK 25 下执行 Wrapper `clean verify` 成功，版本由统一构建治理提供。
2. 可执行 JAR 默认通过 `127.0.0.1:18103/payment` 提供健康与指标端点。
3. 无数据库或其他服务也可启动；未开放路径使用公共错误处理，访问日志不记录支付载荷。
4. 测试位于独立模块，默认只执行 `auto`，验证真实应用入口与 HTTP。

技术选择见[技术方案](./technique.md)，用户授权与验证记录见[交付记录](./delivery.md)。
