# Commerce 接入 Filter 精简决定

> **2026-09-17 后继职责：** [全项目请求幂等决定](2026-09-17-gateway-request-idempotency.md)将本文的 Commerce 接入边界推广为全项目规范，明确 Idempotency-Key 由 Gateway 处理，禁止业务系统新增旧命令幂等组件消费。本文保留当次 Filter 删除与未迁移 Header 的历史边界，不作为其他系统继续接入组件的依据。

- Decision status: Confirmed
- Implementation and deployment status: see [CURRENT_IMPLEMENTATION](../CURRENT_IMPLEMENTATION.md#commerce-ingress-filter-simplification-20260916)
- Decision date: 2026-09-16
- Scope: Commerce 的 ClientApiIdempotencyKeyFilter、ClientApiNoStoreResponseFilter、ClientOrderRequestFilter、ClientRefundRequestFilter、ClientApiTrustedContextFilter 及其说明
- Superseded sections: [Client API 设计](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)第 3.1 节要求 Commerce 私网 Adapter 固定 no-store/no-cache 的部分；先前实现中由 Commerce 专用 Filter 校验 Client Idempotency-Key 的职责落点

## 已确认边界

Commerce 上游为 Gateway。Client 请求级幂等交由接入层中间件设计和处理，Commerce 不为此保留专用 Filter，也不把其逻辑搬入 Controller、Processor 或新组件。订单号、业务退款号防重、数据库约束及下游稳定操作身份仍由业务拥有。本决定不定义幂等中间件的具体实现或启用时间。

对外缓存策略由 Gateway 统一输出 `Cache-Control: no-store` 和 `Pragma: no-cache`。删除 Commerce 的响应包装 Filter；该旧 Filter 同时覆盖 Client 和 Customer 路径，因此两类入口均不再依赖它提供私网直连的缓存头保证。Gateway 自身不存储响应不等于下游缓存策略已经生效。

现行公开 Header 合同保留，Client API 方法的 `@RequestHeader` 声明仍在；框架的必填参数绑定不等同于被移除的单值、字符集和长度检查。未新增方法参数约束来替代 Filter，也不新增请求重放逻辑。

用户随后确认同时删除 Order/Refund 专用请求 Filter。参数必填、格式、长度和范围继续由 MVC / Jakarta Validation 处理；订单、退款和游标的业务判断继续由 Processor / 业务对象处理。未知 Query、重复单值 Query、禁止 GET/Cancel Body 等公开协议限制归上游接入层，不在 Commerce 按业务路径复制拦截规则，也不将 Servlet 请求解析搬入 Processor。

ClientApiTrustedContextFilter 同步删除。该 Filter 只检查两个身份 Header 的单值边界并填充 MDC，不执行 Credential 认证。认证、清理外部伪造身份 Header 并注入可信身份由 Gateway 负责；Commerce 只接受私网可信入口，保留身份 Header 的必填、long 类型绑定及 `@Positive` 约束，按 Client ID 执行资源归属校验。删除该 Filter 同时移除其 `clientId/credentialId` MDC 填充，不新增日志替代组件；现有 Request ID 链路保持。

## 本次执行范围

删除上述五个 Commerce Filter 并同步文档；不修改 Gateway、Foundation、数据库或部署配置，保留 Customer 自有请求处理。本轮不处理测试、不运行构建或验证；接入层的协议限制、缓存头和幂等中间件仍需后续接入或验证，不能从本次删除推断已生效。

实施材料见[本项需求](../../requirements/20260916/commerce-接入Filter精简/technique.md)。
