# 接入 Filter 精简交付记录

2026-09-16，用户先确认从 Commerce 移除专用 Idempotency-Key 校验和缓存响应包装，随后要求删除 Order/Refund Query Filter，再要求删除 ClientApiTrustedContextFilter。执行范围已扩为五个 Filter；参数注解、Processor 业务校验和 Client 资源归属校验保留，没有新增替代拦截器。认证与可信身份注入归 Gateway，旧身份 Filter 的单值检查及 `clientId/credentialId` MDC 填充一并移除；现有 Request ID 链路保持。

沿用用户暂不处理测试、不运行验证的要求，没有运行构建、测试、格式化或其他验证。旧直连测试的 Query/Body 拒绝、缓存头及专有 Key/游标错误断言需后续适配，身份 Header 单值与 MDC 断言也应按新边界调整。本轮不证明 Gateway 的协议拦截、缓存头或幂等中间件已经接入。

未改 Gateway、Foundation、数据库或 PDManer，未执行 SQL、提交、推送或部署。当前状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-ingress-filter-simplification-20260916)。
