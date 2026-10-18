# 全系统基于 RFC 9457 的 Problem Profile 决策

- Decision status: Confirmed
- Implementation status: In progress
- Source status: Current implemented paths source-complete (Tasks 1–12)
- Deployment status: Not deployed
- Legacy reader removal: Pending deployed Producer/Consumer evidence
- Decision date: 2026-09-06
- Scope: Code-MoR 第一方 HTTP API 的 4xx/5xx 错误 Wire Contract、关联 ID、错误时间和跨层传播
- Supersedes:
  - `docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md` 第 5 节的 `{code,message,requestId,timestamp}` 错误体；该文档的日志和网络边界不变
  - `agent-docs/decisions/2026-09-05-commerce-client-api-v1.md` D3、D10 中“Request ID 只在 Header、Body 不含 Request ID”的部分
  - `docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md` 第 12 节的 Problem `type`、Request ID、时间和 `errors[].code` 形状
  - `docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md` 第 8.2 节的旧 Problem 投影；契约模块边界和成功响应不变
  - `docs/superpowers/specs/2026-09-05-client-api-gateway-design.md` 第 12 节中 Problem Body 不含 `requestId` 的部分
  - `agent-docs/decisions/2026-09-05-client-api-gateway-contract.md` D8 中 Problem Body 不含 Request ID 的部分
  - `vault-service/api/openapi/v1.yaml` 中旧四字段错误体、`application/json` 错误媒体类型和旧 Request ID 子合同；Vault 的业务 API 与安全语义不变
  - `requirements-site/content/requirements.ts` 与 `commerce-service/api/openapi/client-v1.yaml` 中冲突的 Client API Problem 投影；这些资产的修改属于后继实施，不代表本决策已经落地
  - `docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md` 第 11 节及其实施计划 Global Constraints 中的旧四字段错误响应；Risk 业务 Code/Status 不变
  - 2026-09-06 之前实施计划中与本决策冲突的旧错误 Body、媒体类型、Request ID 位置和字段错误步骤；其已完成历史证据不改写

## 1. 背景

当前 MVC 服务、Gateway、Vault、内部 HTTP Client 和 Commerce Client API 设计存在不同错误体。只为 Commerce 新增一种格式会使 BFF、Gateway、内部 `@HttpExchange`、前端和 Vault 长期维护多套协议。用户确认以 RFC 9457 的媒体类型和对象结构为共同基础，定义 Code-MoR Problem Profile，再通过消费者优先的受控迁移逐步替换现有四字段格式。

本决策统一的是第一方 HTTP 错误 Wire Contract，不把业务错误判断、业务错误码目录或第三方 Provider 协议集中到 Foundation。

## 2. 已确认决定

### D1. 适用范围

除 HTTP 明确禁止响应内容的 `HEAD` 外，所有第一方 JSON HTTP API 的 4xx/5xx 都使用 `application/problem+json`，包括：

- Gateway 对外产生或透传的错误；
- BFF 对浏览器产生的错误；
- 六个核心系统的外部和内部 HTTP API；
- Java `@HttpExchange` 服务间调用；
- Platform Support 和 Vault 的第一方 HTTP 接口。

Actuator、静态资源、HTML 页面、MQ、第三方 Provider 协议以及无法形成 HTTP 响应的 TLS/连接失败不属于本合同。第三方错误必须在 Adapter 边界转换为本地异常，不能原样进入第一方响应。

本合同从入口 HTTP 栈已成功解析足够的请求、可信入口能够确定 `X-Request-Id` 并进入 Gateway 或应用错误 Writer 后开始。Nginx 或应用 Server Connector 在此之前因畸形请求行/Header、绝对 URI/Header/Body 安全上限、TLS/连接问题或下一级应用不可达而直接产生的响应不属于 JSON API Problem 合同；调用方必须把它们按传输/边缘失败处理，文档也不能把默认 HTML/空响应当作合规 Problem。凡可配置，Nginx 和 Connector 的绝对安全上限必须高于 Gateway/应用公布的 API 限额，使正常可解析的 408/413/431 等合同错误由第一方错误 Writer 生成 Problem；只有无法安全交给 Writer 的极端输入和边缘故障使用该例外。

`HEAD` 错误必须遵守 HTTP 的无响应内容语义，只返回与对应错误一致的 HTTP 状态、`Content-Type`、`X-Request-Id` 和标准 Header；不能为了满足 Problem Body 规则违规写入内容。

### D2. 唯一 Problem 形状

除 `HEAD` 外，Problem Body 必填成员固定为：

- `type`：当前固定为 `about:blank`；
- `title`：实际 HTTP 状态的标准英文 Reason Phrase；
- `detail`：本次错误的安全人类说明，调用方不得解析；
- `code`：唯一机器分支错误码；
- `requestId`：与响应 `X-Request-Id` 完全相同；
- `timestamp`：错误响应生成时刻。

可选 `errors` 只用于请求 Body 字段校验，每项只含必填 `field` 和 `message`。Problem Body 不含 `status`，HTTP 状态行是唯一状态权威；当前 Profile 也不产生 `instance`。消费者必须忽略未知扩展成员。

未来若改为每个错误码独立的 `type` URI，必须通过明确版本化合同发布，不能静默改变 `about:blank` 的既有含义。

该选择有意不把每个 `code` 定义为一个严格的 RFC Problem Type：`about:blank` 与 HTTP 状态承担 RFC 层的通用问题标识，`code` 是 Code-MoR 应用层的二级机器分类。因而本文称其为“基于 RFC 9457 的 Code-MoR Problem Profile”，不宣称每个业务错误都严格采用 RFC 的独立 `type` 语义。

### D3. 错误码只有一层

顶层 `code` 是唯一供程序判断的错误码。字段错误不再拥有第二层 `errors[].code`：

- JSON 结构、Header、Path 或 Query 错误通过顶层 `code/detail` 表达；
- 请求 Body 字段错误使用顶层 `VALIDATION_ERROR` 并可附带 `errors[]`；
- Order 状态冲突、Checkout 过期、幂等冲突、依赖不可用等业务或执行错误只使用具体顶层 `code/detail`，不返回 `errors[]`；
- 一次普通业务请求返回一个主要 Problem，不增加业务错误列表。批量 API 若有逐项结果，必须由该批量 API 独立设计，不能复用字段错误数组。

本迁移不批量重命名已经发布、确有消费者的 Owner 业务错误码。Foundation 只拥有公共错误类别；业务系统继续拥有自己的稳定错误码和状态映射。

### D4. Request ID

`X-Request-Id` 值大小写敏感，必须是一个 Header 值，并满足：

```text
[A-Za-z0-9][A-Za-z0-9._-]{0,63}
```

Client API 可信入口保留合法外部值，缺失、重复或非法时生成新的合规值；Operations 入口忽略外部值并生成新的合规值。第一方内部调用原样传播当前值。除 `HEAD` 外，每个第一方错误响应都必须同时写 Header 和 Body，并保证两者字节级相等；`HEAD` 只写响应 Header，不生成 Body。

Request ID 只用于诊断关联，不是认证、授权、资源标识或业务幂等键。

### D5. 错误时间

非 `HEAD` Problem 的 `timestamp` 必填，表示当前 Problem Body 的生成时刻，使用 UTC RFC 3339 且固定六位小数：

```text
2026-09-06T10:20:30.123456Z
```

BFF 将上游错误重建为自身 Problem 时生成新的 `timestamp`。Gateway 合法透传 Owner Problem 时保留 Owner 的 `timestamp`；Gateway 自己改写错误时生成新值。响应继续使用标准 `Date` Header，不增加 `X-Timestamp`。

### D6. 异常与响应所有权

- Spring MVC 应用统一由 `paytech-mvc-web-starter` 的全局异常边界生成 Problem；服务不得创建本地 Advice 或私有错误 Body。
- 业务模块负责抛出统一异常并提供 Owner 错误码与 HTTP 状态映射，Foundation 不依赖任何业务系统。
- Gateway 是 WebFlux，使用独立 Writer，但复用同一个无 MVC/WebFlux 依赖的 Foundation Problem 模型与验证规则。
- BFF 不向浏览器透传上游原始错误体；它只转换已注册的安全语义并生成自己的 Problem。
- 内部 HTTP Client 对非 `HEAD` 错误校验 Content-Type、必填字段、时间、HTTP 状态与 `title`、以及 Header/Body Request ID 一致性；`HEAD` 只校验状态和 Header 且不尝试解码 Body。不合规响应转换为本地上游协议错误。
- Vault Go Server 独立实现同一 Wire Contract，Vault Java Client 使用同一校验规则；Vault 的敏感日志边界不因本决策放宽。

### D7. 迁移方式

采用消费者优先迁移：先让内部 Client、Gateway、BFF 和前端读取新旧两种格式，再逐步切换 Foundation MVC、各服务、Gateway 和 Vault 的生产者，最后删除旧格式解析。生产者在任一时刻只输出一种格式，不根据 `Accept` 长期协商两种错误体。公网迁移还必须校验 Nginx 与 Gateway 的限额顺序和上述边缘例外；Nginx 不被改造成第二个 Problem 业务 Writer。

迁移不修改数据库，也不改变成功响应、业务状态机、事务、幂等或 MQ 合同。当前实现、验证与部署状态只记录在 `agent-docs/CURRENT_IMPLEMENTATION.md`。

## 3. 权威与后继

完整合同、组件职责、迁移顺序和验收门禁由 [`2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md) 持有。后继实施计划必须覆盖 Foundation、Gateway、现有 BFF/Web、Platform Support、当前核心 Java 服务、Vault、Commerce OpenAPI 和 Requirements Site，且不得把设计完成写成运行时已迁移。

## 2026-10-18 修订：Owner 业务码可携带字段级 `errors[]`

- **范围**：仅限请求 Body 字段校验失败、由 Owner 以自己的业务 Code 表达的 4xx（首例 client-master `INVALID_DRAFT`，422）。业务执行检查（状态、资格、并发）仍不带 `errors[]`。
- **Foundation**：`paytech-http-problem` 的 `RequestBodyValidationException` 增加 Owner Code 构造；`MvcProblemExceptionMapper` 按 Owner 状态解析输出 `errors[]`；`paytech-http-client-support` 对已登记合同 Code 的 4xx Problem 同时保留 `field` 与 `message`，仅在调用方开启请求校验透传且请求带 Body 时生效，使 BFF 重新映射后 `errors[]` 继续存在。
- **依据**：用户 2026-10-18 确认（Client Console 保存资料需逐字段提示）；实现与测试见 `requirements/20261017/architecture-V2骨架与模块搬迁/tasks/16-draft-field-errors-report.md`。本节是对上文"字段错误只用 `VALIDATION_ERROR`"规则的登记例外，不改变其余合同。
