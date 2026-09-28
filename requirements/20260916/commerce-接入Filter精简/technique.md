# 接入 Filter 精简技术说明

## 当前改动与职责

五个 Filter 均由 `@Component` 自动注册，删除源码即可移除该专用处理，无需新增配置或替代机制。

Client 幂等 Filter 只验证 POST Header 的存在、单值、字符集与长度，不提供真正请求幂等。删除后，三个 Client 写接口仍保留既有 `@RequestHeader` 参数，缺失 Header 的框架绑定行为仍在；Commerce 不再负责专有 `IDEMPOTENCY_KEY_REQUIRED/INVALID_IDEMPOTENCY_KEY` Filter 错误或其严格格式规则。公开 Header 格式仍是接入中间件合同，不能宣称本轮已实现中间件。

缓存 Filter 原先匹配 Client 和 Customer 路径，并覆盖 Response 的 set/add/reset 等方法，强制 `Cache-Control: no-store` 和 `Pragma: no-cache`。删除后，Commerce 不再提供该路径统一的响应头保护；公开响应由 Gateway 输出，不通过业务代码补回。本轮未修改或验证 Gateway，直连服务的响应不能继续套用公网缓存头断言。

Order/Refund Query Filter 同步删除。Client API 原有字段注解继续表达必填、长度、格式及分页范围；RefundCursor 的解析和 Processor 的归属、锚点验证保持。未知 Query、重复单值参数、GET/Cancel Body 不再由 Commerce 的这两个 Filter 拒绝，空 limit 等输入按框架绑定规则处理；游标基础长度校验与业务 INVALID_CURSOR 不再保证原 Filter 的错误优先级。公开协议的严格限制归上游，本次未补写通用 Filter 或在 Processor 读取 Servlet 请求。

ClientApiTrustedContextFilter 同步删除：它仅检查 `X-Gateway-Client-Id` 和 `X-Gateway-Credential-Id` 单值，并填充、恢复 `clientId/credentialId` MDC；没有认证或业务授权逻辑。当前 Commerce 业务不依赖这两个 MDC 字段，身份经 API 参数显式传入。认证、外部伪造 Header 清理及可信身份注入仍归 Gateway；Commerce 的私网可信入口边界、身份参数必填/long 类型/`@Positive` 约束和 Client 资源归属校验保持。删除后不再由该 Filter 为日志填充这两个字段，现有 Request ID 链路不变，不声称 Foundation 已接管这两个字段。

Customer 自有请求处理保留。订单永久唯一、Client 退款业务号防重、内部下游稳定身份及原事务边界均不受影响。没有新 Processor、Context、Service、数据库或 HTTP Body 结构。

## 文档与后续

API 方法注释、Client OpenAPI 和 README 明确区分参数绑定、专用 Key 校验与请求幂等；缓存 Header 的公开合同保留并标注 Gateway Owner。Customer OpenAPI 同步缓存头职责，历史实施说明增加窄范围后继说明。

后续测试应分别覆盖 Commerce 字段/归属/业务防重，以及 Gateway 的可信身份注入、Query/Body 协议、公开缓存头、Key 协议和最终幂等方案；旧直连测试的 Query/Body 拒绝、缓存头、缺失/非法 Key 专有错误及部分游标错误断言需适配，身份 Header 单值和 MDC 断言不再以旧 Filter 为依据。没有在本轮运行这些检查。

状态见[CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-ingress-filter-simplification-20260916)。
