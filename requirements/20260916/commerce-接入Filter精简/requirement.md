# Commerce 接入 Filter 精简

用户确认 Commerce 上游为 Gateway，传输幂等交由中间件，缓存策略由 Gateway 统一处理，并随后要求删除 Order/Refund 专用请求 Filter 和 Client 可信身份 Filter。本项合计精简五个 Filter：

- 删除 `ClientApiIdempotencyKeyFilter`，不在 Commerce 新增替代的 Key 格式、单值校验或请求重放。
- 删除 `ClientApiNoStoreResponseFilter`，不在 Controller、Processor 或新拦截器中重建响应包装。
- 删除 `ClientOrderRequestFilter` 与 `ClientRefundRequestFilter`，不再按业务路径拦截 Query 白名单、重复单值参数或禁用 Body；公开协议限制归上游接入层。
- 删除 `ClientApiTrustedContextFilter`，移除其身份 Header 单值检查及 `clientId/credentialId` MDC 填充；认证和可信身份注入由 Gateway 负责。
- 保留 Client API 原 Header 参数声明、身份参数约束、业务防重与字段校验；Client 资源归属及订单/退款/游标的业务判断继续在 Processor 和业务对象中执行。
- 公开响应的 no-store/no-cache 合同保留，由 Gateway 承担；原缓存 Filter 也覆盖 Customer Checkout，因此该路径一并退出 Commerce 私网缓存头保证。

本轮不改 Gateway、Foundation、数据库、PDManer、部署配置或测试，不运行构建或验证。幂等中间件方案与实际接入另行处理，不把职责确认当作已实现。

已确认边界见[决定](../../../agent-docs/decisions/2026-09-16-commerce-ingress-filter-simplification.md)。
