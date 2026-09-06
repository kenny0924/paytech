# Code-MoR 全系统 RFC 9457-based Problem Profile 统一设计

- Decision status: Confirmed
- Written spec review: Completed (2026-09-06)
- Decision date: 2026-09-06
- Implementation status: Source-complete for currently implemented paths (Tasks 1–12); rollout-gated cleanup pending
- Verification status: Available local gates passed; Go/toolchain and credential-backed integration gaps remain
- Deployment status: Not deployed
- Legacy reader removal: Pending deployed Producer/Consumer evidence
- Decision record: [`2026-09-06-system-wide-rfc-9457-problem-details.md`](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)
- Scope: 第一方 HTTP 4xx/5xx Wire Contract、异常出口、关联 ID、跨层转换、兼容迁移和验证
- Implementation, verification and deployment: see [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)

本文冻结 Code-MoR 第一方 HTTP API 的统一错误合同。当前工作区已完成现有路径的源码迁移，不代表制品已发布、环境已部署或 Legacy Reader 已满足删除门禁；实现、验证和部署事实只由 `CURRENT_IMPLEMENTATION.md` 维护。

## 1. 目标与非目标

### 1.1 目标

1. 第一方 HTTP 调用方只需理解一种基于 RFC 9457 的 Code-MoR Problem 形状。
2. HTTP 状态、稳定业务错误码、人类说明、关联 ID 和错误时间各自只有一个明确含义。
3. MVC 服务继续共享一个异常出口，业务系统不复制 Advice 或错误 DTO。
4. Gateway、BFF、内部 HTTP Client 和 Vault 在各自技术边界使用同一 Wire Contract，同时保留安全转换职责。
5. 通过消费者优先迁移避免多仓库同时发布的 Big Bang 风险，并在迁移结束后删除旧协议。

### 1.2 非目标

本设计不改变：

- 任何成功响应 DTO；
- 业务状态机、业务错误判断、幂等、事务或数据库；
- MQ、Webhook 业务 Payload 或第三方 Provider 协议；
- MVC Body 日志、Vault 敏感日志、Nginx TLS 或可信 Client IP 的既有边界；
- Actuator、静态资源或 HTML 页面。

无法形成 HTTP 响应的 TLS 握手、连接中断和客户端主动断开不是 Problem Details。本 Profile 从入口 HTTP 栈已成功解析足够的请求、可信入口能够确定 `X-Request-Id` 并进入 Gateway 或应用错误 Writer 后开始。Nginx 或应用 Server Connector 在此之前因畸形请求行/Header、绝对 URI/Header/Body 安全上限、TLS/连接问题或下一级应用不可达而直接产生的响应不属于 JSON API Problem 合同；调用方必须把它们按传输/边缘失败处理，且不得把默认 HTML/空响应当作合规 Problem。凡可配置，Nginx 和 Connector 的绝对安全上限必须高于 Gateway/应用公布的 API 限额，使正常可解析的 408/413/431 等合同错误由第一方错误 Writer 产生；只有无法安全交给 Writer 的极端输入和边缘故障使用该例外。Nginx 与 Connector 不实现第二套业务错误码或 Problem Writer。

## 2. 方案选择

### 2.1 采用：统一目标合同，消费者优先分阶段切换

先发布共享模型和兼容 Reader，再升级所有消费者，随后逐个切换 Producer，最后移除旧 Reader。优点是每个仓库可以独立验证和回滚，终态仍只有一种协议。

### 2.2 未采用：所有仓库一次性切换

Big Bang 可以减少短期双读代码，但 Foundation、Gateway、Platform、BFF、Web 和 Vault 必须在同一窗口原子部署；任一遗漏都会把合法业务错误误判为上游协议异常，当前多仓库状态不适合这种风险。

### 2.3 未采用：长期保留两套响应或按 Accept 协商

永久双格式会扩大测试矩阵，让消费者长期停留在旧协议，并使同一路径的日志、缓存和 Gateway 校验不稳定。本设计只允许迁移期 Reader 双读；Producer 不提供长期格式协商。

## 3. 统一 Wire Contract

### 3.1 HTTP

除 `HEAD` 外，每个纳入范围的 4xx/5xx 响应必须包含：

```http
Content-Type: application/problem+json
X-Request-Id: req_01K4...
Date: <standard HTTP date>
```

`Content-Type` 使用 `application/problem+json`，不退回 `application/json`。标准 `Date` Header 继续由 HTTP 栈产生，不增加 `X-Timestamp`。缓存、CORS、Retry-After、WWW-Authenticate 等 Header 仍由对应 API 或 Gateway 合同决定。

HTTP `HEAD` 响应不得包含 Content。它应返回与对应错误一致的状态、`Content-Type`、`X-Request-Id` 和其他标准 Header，但不生成或传输 JSON Body；这是 HTTP Method 语义的唯一例外，不授权其他方法省略 Problem。

### 3.2 Body

除 `HEAD` 外，目标 JSON 形状为：

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "detail": "One or more request fields are invalid.",
  "code": "VALIDATION_ERROR",
  "requestId": "req_01K4ABCDEF0123456789",
  "timestamp": "2026-09-06T10:20:30.123456Z",
  "errors": [
    {
      "field": "items[0].unitAmount",
      "message": "The amount must be a non-negative decimal string."
    }
  ]
}
```

必填成员：

| 成员 | 规则 |
|---|---|
| `type` | 当前固定为 `about:blank`。没有已拥有并长期维护的问题类型域名，不伪造 URI |
| `title` | 与实际 HTTP 状态一致的标准英文 Reason Phrase；不能放业务提示 |
| `detail` | 本次错误的安全人类说明；允许按入口语言呈现，调用方不得解析 |
| `code` | 大写、稳定、可读的唯一机器分支码；调用方结合 HTTP 状态使用 |
| `requestId` | 与同一响应的 `X-Request-Id` 完全相同 |
| `timestamp` | 当前 Problem 生成时刻，UTC RFC 3339、固定六位小数 |

可选成员：

| 成员 | 规则 |
|---|---|
| `errors` | 仅在请求 Body 字段错误存在时返回；非空数组；每项只有非空 `field` 和安全 `message` |

Producer 当前不输出 `status` 或 `instance`。`status` 缺失是有意设计，HTTP 状态行是传输状态唯一权威。Consumer 必须忽略未知扩展成员，使未来增加可选诊断字段不破坏读取；但将 `type` 从 `about:blank` 改成每 Code URI 必须发布明确的新合同版本。

本 Profile 有意不把每个 `code` 声明成独立 RFC Problem Type。消费者先使用 HTTP 状态和 `about:blank` 识别 RFC 层的通用问题，再使用 `code` 做 Code-MoR 应用层细分。由于 RFC 9457 把 `type` 定义为 Problem Type 的主要标识，并规定 `about:blank` 不表达 HTTP 状态之外的 Problem Type 语义，本文准确称为“基于 RFC 9457 的 Code-MoR Problem Profile”，不宣称严格采用每 Code Problem Type。若未来需要严格的每 Code `type`，必须选择稳定 URI、维护 `type/code/status` 映射，并通过新版本发布。

### 3.3 字段路径

`errors[].field` 使用外部 JSON 属性名，嵌套对象以 `.` 连接，数组使用从零开始的 `[index]`，例如 `items[0].unitAmount`。它不能使用 Java 字段名、类名、数据库列名或内部对象路径。

字段 `message` 与顶层 `detail` 都只供人阅读。Client 不得通过字符串比较推断错误类型；如果确有程序分支需求，使用顶层 `code`。

## 4. 错误分类与状态

### 4.1 字段错误

请求 Body 是合法 JSON，但缺少字段、字段格式或值域不合法时，返回顶层 `VALIDATION_ERROR`，并可携带所有已安全化的字段错误：

```text
HTTP 422
code = VALIDATION_ERROR
errors = [{field,message}, ...]
```

Malformed JSON、非法 Header、Path、Query、媒体类型或方法不把位置伪装成 Body 字段，使用相应顶层错误码和 `detail`，不返回 `errors`。

### 4.2 业务执行检查

业务检查失败直接使用具体顶层错误码，不返回 `errors`：

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
X-Request-Id: req_01K4ABCDEF0123456789
```

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "detail": "The order has already been paid and cannot be cancelled.",
  "code": "ORDER_ALREADY_PAID",
  "requestId": "req_01K4ABCDEF0123456789",
  "timestamp": "2026-09-06T10:20:30.123456Z"
}
```

一次普通请求只有一个主要 Problem。业务代码以确定、稳定的检查顺序选择最先需要调用方处理的错误；不返回内部检查清单，也不新增 `violations` 或通用业务错误数组。

### 4.3 HTTP 状态责任

HTTP 状态是第一分支，顶层 `code` 是同一状态下的稳定细分。通用语义为：

| HTTP | 语义示例 |
|---:|---|
| 400 | Malformed JSON、Header/Path/Query 合同错误 |
| 401 | 未认证 |
| 403 | 已认证但权限或 Scope 不足 |
| 404 | 资源不存在或按安全合同隐藏其存在性 |
| 405 | Method 不允许 |
| 408 | 请求接收超时 |
| 409 | 幂等、版本、并发或资源状态冲突 |
| 413 | Body 超限 |
| 415 | 不支持的媒体类型 |
| 422 | 合法 JSON 的字段语义错误或明确的业务可处理拒绝 |
| 429 | 限流或并发额度超限 |
| 431 | Header 超限 |
| 500 | 未预期内部错误 |
| 502 | 上游响应不符合第一方协议 |
| 503 | 当前服务或依赖暂时不可用 |
| 504 | 依赖调用超时 |

具体 Owner 错误码与状态继续由发布该能力的系统拥有。`ApiErrorStatusResolver` 仍只扩展明确对外的状态映射，并保持 Fail 类异常只能映射到 4xx、Error 类异常只能映射到 5xx。迁移不批量重命名已有 Owner Code；只有两个以上真实消费者共享稳定语义时，才把新公共 Code 放入 Foundation。

## 5. Request ID 与时间

### 5.1 Request ID

唯一合法格式为：

```regex
^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$
```

- Header 名大小写不敏感，值大小写敏感；只允许一个值。
- Client API 入口保留单个合法外部值；缺失、重复或非法值由可信入口替换为新生成值。
- Operations 入口忽略外部值并生成新值。
- Gateway、BFF 和服务间 `@HttpExchange` 调用传播当前值，不为同一次调用链任意换值。
- 除 `HEAD` 外，接收方始终把最终值同时写入响应 Header 和 Problem Body；`HEAD` 只写 Header。
- 非 `HEAD` 解码方要求 Header/Body 字节级相等；不一致是上游协议错误，不能选择其一继续使用。`HEAD` 不尝试读取 Body。

Request ID 不是 Idempotency-Key、业务 ID、认证信息或安全证明，不进入业务唯一索引或业务状态机。

### 5.2 Timestamp

非 `HEAD` Problem 的 `timestamp` 使用以下 Wire 形式：

```text
yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'
```

生成器必须在构造一个 Problem 时只读取一次时钟，并以 UTC 输出恰好六位小数。该值描述 Problem 生成时间，不是请求开始、业务事件或日志写入时间：

- MVC/Gateway 本地创建 Problem：使用本地创建时刻；
- BFF 安全重建上游错误：使用 BFF 重建时刻；
- Gateway 校验后原样透传 Owner Problem：保留 Owner 时间；
- Gateway 将非法 Owner 响应改写为 502：使用 Gateway 改写时刻。

## 6. 组件边界

### 6.1 Foundation framework-neutral 模块

Foundation 新增职责单一的 `paytech-http-problem`：

- `ApiProblemResponse`；
- `ProblemFieldError`；
- Wire 常量和纯校验规则；
- 不依赖 Spring MVC、WebFlux 或任何业务系统。

该模块不拥有 Controller Advice、WebFlux Writer、业务错误码全集或第三方错误解析。它使 MVC Starter、Gateway 和 HTTP Client 使用同一模型，又不迫使 Gateway 引入 Servlet 依赖。

### 6.2 Spring MVC

`paytech-mvc-web-starter` 是所有 MVC 应用的唯一 HTTP 异常到 Problem 转换器：

1. `GlobalExceptionHandler` 捕获 MVC 参数、解析、业务、系统和未知异常；
2. 字段绑定结果只投影安全的外部属性路径与消息；
3. `FailException`、`ErrorException` 和 Owner Code 继续表达业务/系统语义；
4. `ApiErrorStatusResolver` 继续提供服务拥有的状态映射；
5. Starter 统一写 Content-Type、Header、Body 和安全日志；
6. DispatcherServlet 之前的 Filter/Security 错误通过 Starter 拥有的前置兜底出口进入同一 Writer，不能退回 Spring Boot 默认错误 JSON 或 HTML。

Controller、Processor、Service 和 Domain Service 不为格式转换增加宽泛 `try/catch`。业务模块不创建本地 `@RestControllerAdvice`、私有 Problem DTO 或同义响应工厂。

### 6.3 内部 HTTP Client

`paytech-http-client-support` 在目标模式下对非 2xx、非 `HEAD` 响应执行：

1. 先按 HTTP 状态分类；
2. 要求 `application/problem+json`；
3. 校验全部必填字段、`type/title/timestamp`、`errors` 结构以及 Header/Body Request ID；
4. 只允许显式注册的 Owner 4xx Code 保留为本地明确失败；
5. 未知 Code、非法 Problem、重定向、解码失败转换为本地上游协议错误；
6. 远程 5xx、连接失败和超时按调用合同转换，不自动重试。

对 `HEAD` 的非 2xx 响应只校验 HTTP 状态、Problem 媒体类型和 `X-Request-Id`，不得因协议要求无 Body 而把它误判为非法上游响应，也不得尝试构造不存在的远端 `code/detail/timestamp`。调用方按 HTTP 状态映射为本地通用错误；若某个 `HEAD` API 需要可程序判断的 Owner 业务 Code，必须改用可携带 Problem Body 的 Method。

迁移期 Decoder 在进入上述目标模式前先按媒体类型分流：`application/problem+json` 只进入新 Problem 严格校验；`application/json` 只允许进入显式启用的旧 `{code,message,requestId,timestamp}` Reader。旧 Reader 接受旧 Producer 可解析的 `Instant` 时间和旧 Request ID 规则，但只转换为内部安全异常，绝不把这些旧值重新作为新合同输出；其他媒体类型一律视为上游协议错误。该兼容开关按已知第一方 Target 配置，不能自动猜测，也必须在全部 Producer 切换后删除。业务 Adapter 不重复写 JSON 解码器，也不记录上游完整错误 Body。

### 6.4 BFF

BFF 是浏览器协议和展示安全边界，不字节级透传核心服务 Problem。它只对明确注册的安全 4xx Code 保留业务语义，并使用当前浏览器请求的 Request ID、新 `timestamp` 和安全 `detail` 构造自己的 Problem：

- 未知/非法上游 4xx -> `502` 上游协议错误；
- 上游 5xx 或网络不可用 -> BFF 的 `503 DEPENDENCY_UNAVAILABLE`；
- BFF 自身等待依赖超时或收到合规上游 504 -> `504 DEPENDENCY_TIMEOUT`；
- 上游 requestId、内部 URI、敏感 detail 和原始 Body 不向浏览器泄露。

`HEAD` 请求没有可供 BFF 安全转换的上游 Problem Body；BFF 只按上游 HTTP 状态映射本地通用错误、返回自身 `X-Request-Id` 和 Problem 媒体类型，并保持无 Body。前端只对非 `HEAD` 响应根据 HTTP 状态和顶层 `code` 分支，使用 `detail` 展示安全提示，并展示 `requestId` 供支持排查；不得解析 `title/detail/errors[].message`。

### 6.5 Gateway

Gateway 使用 WebFlux 专用 Writer 生成相同 Problem，不引入 MVC Starter：

- Route、认证、Scope、Guard、限流、Body、超时和本地异常由 Gateway 生成自己的 Problem；
- 非 `HEAD` 的合法 Owner Problem 只有在状态、Content-Type、必填字段、时间、标题和 Request ID 全部合规时才保持原状态和 Body；
- 非 `HEAD` 响应由 Gateway 写出最终 `X-Request-Id`，其值必须与透传 Body 相同；
- Gateway 本地和 Owner `HEAD` 错误只校验/写出状态、Problem 媒体类型、`X-Request-Id` 和其他允许 Header，并要求无 Content；合法 Owner `HEAD` 不因缺少 Problem Body 被改写为 `502`；
- Owner 的错误 Body 不合规、被截断或协议解码失败时，Gateway 丢弃原 Body，生成 `502 UPSTREAM_PROTOCOL_ERROR`；
- Gateway 不把 Owner 业务 4xx 改成 5xx，也不解析 `detail` 作程序判断。

### 6.6 Vault

Vault Go Server 使用同一 JSON 成员、媒体类型、Request ID 和时间格式。Vault Java Client 使用相同严格 Decoder。Vault 不依赖 Java MVC Starter，且普通日志仍不得记录 Token、敏感明文、完整 Payload 或上游完整错误体。

### 6.7 OpenAPI 和前端

每个公开 OpenAPI 复用同一 Problem Schema 语义：六个必填成员，字段错误项只有 `field/message`，无 `status`。Java API JAR 不为每个系统复制同义 Problem Record；公共错误 Wire 类型来自 Foundation，业务 API 只发布自身明确需要的 Code Catalog。

浏览器端可以定义最小只读 TypeScript 投影，但必须允许未知成员。读取顺序不再使用旧顶层 `message`；诊断 ID 以 Header/Body一致为合同，展示时可使用已经校验的 Body 值。

## 7. 安全与可观察性

- `detail` 和字段 `message` 只能包含可安全外显的信息。
- 不得返回异常 Cause、堆栈、类名、SQL、表/列名、依赖 URI、Provider 原始错误、Credential、Token、密钥、个人敏感信息或完整 Payload。
- 未知异常始终转换为通用 `500 INTERNAL_ERROR`，内部日志通过 `requestId` 关联诊断。
- BFF 和 Gateway 记录错误分类、状态、Code 和 Request ID，不记录未经批准的上游 Body。
- 现有 metadata-only 日志路径和 Vault 专项禁日志规则继续优先；统一 Problem 不授权扩大日志范围。
- `type=about:blank` 是当前无自有长期域名时的有意选择，不使用并不受控的示例域名。

## 8. 迁移计划边界

### Phase 1：共享模型与兼容 Reader

1. 在 Foundation 增加 `paytech-http-problem`、序列化/校验测试和共享时间/Request ID 规则。
2. `paytech-http-client-support` 增加新 Problem Decoder，迁移期开启受控旧格式读取。
3. MVC Producer 暂时继续旧输出，确保新 Foundation 版本可先被消费者采用。

### Phase 2：先升级消费者

1. Operations BFF 的 Platform Client 与 Simulator；
2. Operations Web 的错误解析；
3. Gateway 的 Owner Problem 校验器；
4. Vault Java Client；
5. 已存在的其他内部 `@HttpExchange` 消费者。

消费者必须同时通过新格式、旧格式和非法格式反例。双读只存在于内部兼容层，业务代码看不到两种 DTO。

### Phase 3：切换 Producer

1. `paytech-mvc-web-starter` 切换为唯一 Code-MoR Problem Profile Writer；
2. 各 MVC 应用升级 Foundation 并验证 Owner Code/Status；
3. Gateway WebFlux Writer 切换并启用合法 Owner 透传；
4. 对齐 Nginx、Server Connector 与 Gateway/应用 Writer 的限额顺序，保证公开 API 限额由第一方 Writer 生成 Problem，并以配置测试冻结 pre-application 边缘例外；
5. Vault Go Server 切换；
6. Commerce OpenAPI、契约测试、Requirements 和其他公开文档同步目标 Schema。

同一 Producer 版本只输出新格式，不按 `Accept` 回退旧格式。处于迁移窗口的 Consumer 必须容忍新旧 Producer，以支持单仓库回滚。

[窄范围后继决策](../../../agent-docs/decisions/2026-09-06-remove-unused-api-error-response-dto.md)确认：旧 `ApiErrorResponse` Java record 不是 Legacy Reader 的运行时合同，也不承担兼容解析。确认工作区生产代码无引用、测试 fixture 已与共享 DTO 解耦后，可以独立删除该 record；这不代表 Legacy 协议已经下线，也不放宽下述 Reader 删除门禁。

### Phase 4：移除 Legacy

当已部署的所有 Producer 都已切换、所有 Consumer 都有新格式验证证据后：

- 删除旧 Decoder；
- 删除旧顶层 `message` 分支和兼容测试；
- 静态扫描不再发现业务应用生产旧四字段 Body；
- 更新 `CURRENT_IMPLEMENTATION.md`，但只记录实际完成、验证和部署的范围。

旧 Reader 删除后，不再保证旧 Producer 可直接回滚；如需回滚，必须先恢复兼容 Reader。不得为了方便回滚永久保留双协议。

## 9. 测试设计

### 9.1 Foundation

- `ApiProblemResponse` 的精确 JSON、必填字段、无 `status`、可选 `errors` 和未知成员兼容；
- `about:blank` 与标准 Reason Phrase；
- UTC 六位小数时间固定向量；
- Request ID 正例、边界和反例；
- 非 `HEAD` Header/Body 一致与不一致；
- MVC 真实测试 Controller 覆盖字段校验、业务失败、并发冲突、依赖错误、未知异常和 Filter 前置失败；
- 响应媒体类型固定为 `application/problem+json`。
- 标准 `Date` Header 可解析，且响应不存在自定义 `X-Timestamp`。
- `HEAD` 错误无 Body，但保留正确状态、Problem 媒体类型和 Request ID Header。
- Nginx/Connector 安全上限高于 Gateway/应用公开限额；可安全解析的超限请求由第一方 Writer 返回 Problem，畸形/极端请求和下一级应用不可达被明确验证为 pre-application 边缘例外且不送入普通 Problem Decoder。

### 9.2 内部 Client、BFF 和 Gateway

- 使用真实 Loopback HTTP Simulator，不用方法级 Mock 替换生产链；
- 覆盖新 Problem、迁移期旧错误、空 Body、错误媒体类型、缺字段、坏时间、坏 title、未知 Code、未知扩展和 Request ID 不一致；
- BFF 验证上游 Body/requestId/detail 不泄露且新时间生成；
- BFF 验证 `HEAD` 错误无 Body、只返回自身 Request ID 和 Problem Content-Type，并仅按上游状态映射本地通用错误；
- Gateway 验证本地错误、合法 Owner 透传、非法 Owner 改写 502，以及非 `HEAD` 最终 Header/Body ID 相同；本地和 Owner `HEAD` 错误必须无 Body，并且合法 Owner `HEAD` 不被误改写为 502；
- Simulator 对未声明调用失败，并在测试间复位状态。

### 9.3 业务服务、Vault 和 Web

- 每个业务服务只补充自己拥有的 Code/Status 映射，不机械重复 Foundation 全集；
- Vault Go Server 与 Java Client 使用相同固定 JSON 向量，并保留敏感日志反例；
- Web 验证按 HTTP 状态和顶层 Code 分支、显示 detail/requestId、忽略未知成员且不依赖旧 message；
- Commerce OpenAPI 投影验证六个必填字段、字段错误无二级 Code、Body 无 status。

### 9.4 验证命令

实施计划必须按改动仓库列出聚焦测试和完整门禁，至少包括：

- Foundation 目标模块与 `clean verify`；
- Gateway、Platform Support、Operations BFF、现有受影响核心服务和 Vault 的相关完整测试；
- Operations Web 的测试和生产构建；
- Commerce contract/OpenAPI drift 与 purity 测试；
- `npm --prefix requirements-site test`；
- 各仓库 `git diff --check` 和冲突文案精确扫描。

自动测试遵守现有 `auto/manual`、Loopback 基础设施和 Simulator 规则，不引入 Testcontainers、自动 Schema 迁移或方法级行为 Stub。

## 10. 验收标准

1. 除 HTTP `HEAD` 的无 Content 语义及无法进入第一方错误 Writer 的 pre-application 传输/边缘失败外，纳入范围的所有第一方 4xx/5xx 都返回 `application/problem+json` Problem Body；`HEAD` 仍返回相同状态和必要 Header。
2. 非 `HEAD` Body 恰有六个必填基础成员；不含 `status`，`errors` 仅按需出现；`HEAD` 不含 Body。
3. `errors[]` 每项只有 `field/message`，业务执行错误没有字段数组。
4. 非 `HEAD` 的 `X-Request-Id` 与 Body `requestId` 完全相同，格式统一且内部传播不变；`HEAD` 只保留 Header。
5. 非 `HEAD` 的 `timestamp` 是 Problem 生成时刻，UTC 且固定六位小数。
6. MVC 应用只通过共享 Starter 生成错误响应；不存在服务本地 Advice 或私有同义 Body。
7. Gateway、BFF、内部 Client 和 Vault 分别满足其验证、转换和安全边界；Nginx/Connector 限额不抢占 Gateway/应用公布的 API 错误，pre-application 边缘例外被明确测试和记录。
8. 所有 OpenAPI、Requirements、Java/Go/TypeScript 投影和运行时响应一致。
9. 迁移结束后生产代码不再生成或依赖旧顶层 `message` 四字段错误体。
10. 相关构建、测试、静态检查和需求站点测试通过；状态台账只声明实际完成的范围。
