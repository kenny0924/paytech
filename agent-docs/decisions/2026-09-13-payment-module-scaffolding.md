# Payment 四业务模块工程落地

- Decision status: Confirmed
- Implementation status: Complete（仅工程骨架）；证据见当前台账
- Deployment status: Not applicable
- Decision date: 2026-09-13
- Superseded sections: 仅对本次 Payment 工程拆分，取代模块方案中“等待首个业务用例再创建 JAR”的时序限制；不改变全项目默认不创建空模块的规则。

用户已确认 Execution、Routing、Channel、Card 四个业务模块，并在完成 Channel 命名统一后明确要求“那现在帮我拆一下项目模块吧”。本次按已确认的职责与依赖方案落地 Maven 工程边界及本地开发验证。

当前 Payment 只有 api/web/test 应用初始化代码，没有业务实现可以迁移。因此先创建四个普通业务 JAR 的 POM、包归属说明和边界检查，由 Web 组合根直接依赖。模块骨架不代表路由、支付、渠道或 Card 已实现；不为填充模块创建占位 Service、DTO、持久化对象或交易逻辑。

默认依赖为 `test → web → api/execution/routing/channel/card`。四业务模块不互相依赖，不依赖 api/web/test；各表及未来持久化代码沿[模块方案](../../requirements/20260913/payment-业务模块划分/technique.md)保持唯一 Owner。Web 是唯一 Spring Boot 启动和跨模块编排入口，仍只有一个进程和一个业务数据库。获准的稳定单向依赖继续按[Java 模块规范](../JAVA_PROJECT_STRUCTURE.md#34-复杂核心服务的业务能力模块)单独评估。

2026-09-15 用户确认 pay 在受理前调用 RouteService，按上述门禁增加 Execution → Routing 的单向窄接口依赖；接口不暴露 DataObject，不授权其他业务模块互依或独立 Route 进程。具体范围及本期 Mock 约定见[Execution 字段修订第 9 节](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#9-受理前路由)，取代本骨架在该依赖上的初始隔离规则。

本次不增加 HTTP 接口、业务表、SQL、外部集成、数据库迁移或部署。Card 原始数据处理和具体接口/字段/恢复合同仍由原专项拥有。

确认及开发检查记录见[交付记录](../../requirements/20260913/payment-业务模块划分/delivery.md)；实现和验证状态只在[当前台账](../CURRENT_IMPLEMENTATION.md)汇总。
