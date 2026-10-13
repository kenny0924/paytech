# 研发计划 · 第 3 步：标准 API 执行引擎

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户对计划回复“可以”，G1～G8 按建议执行，含 G1 引入 Aviator 5.3.3、G8 修改 Platform 代码与技术栈登记的授权；不含提交、推送、共享环境 Vault 策略或 SQL。
- 范围：Platform 内部标准 API 执行接口、流程执行引擎、原子 API 单次执行（组装、脚本、HTTP 传输、解码、状态规则、输出）、Aviator 表达式与脚本、注册函数（含 Vault 读取渠道账号）、保存时的表达式编译校验、测试与本机端到端。
- 不含：Payment 改造与接入（第 4 步）；任何共享或线上环境的 Vault 策略、SQL、部署；真实渠道（NI 等）协议接入。
- 依据（均已确认）：[需求](./requirement.md)、[内部接口](./internal-api.md)（W1～W6）、[流程结构](./flow-definition-contract.md)、[原子 API 结构](./integration-api-definition-contract.md)、[账号材料交接](./material-handoff.md)（方案 C）、[流程设计](./flow-design.md)，决定 [2026-10-13 标准 API 与流程](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)、[2026-10-13 Platform 读取渠道账号](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)。

## 0. 已读规范与本次说明

已读：开发约定、Java 工程规范、公共写接口示例、测试规范、技术栈第 7 节（内部调用、`RestClient`）、上述合同与决定，以及 Payment 现有 `ChannelAccountConfigReader`（Vault Reveal 与严格解析的样板）。

- **入口与 Processor：** `ExecuteStandardApiProcessor`（`platform-support-integration`，继承 `AbstractProcessor`，不加锁、不写库）。
- **本地事务：** 只有一次短只读事务（REPEATABLE_READ）加载执行快照；之后初始化、外部 HTTP、脚本与流程全部在事务外执行。不写任何业务表、不持久化报文或运行上下文。
- **事务外步骤：** Vault Reveal、渠道 HTTP 请求。都不重试。

## 1. 调用链

```text
Payment（事务外，第 4 步接入）
  → POST /platform-support/standard-apis/v1/{standardApiCode}/execute   （不经 Gateway/BFF，无调用方授权，W3）
    → ExecuteStandardApiProcessor
        1. 只读事务加载快照：标准 API + 字段、接入方 + 出站地址、实现流程、流程引用的原子 API + 字段、被引用脚本
        2. 运行前校验：用第 1 步的静态校验器重新校验快照          → 500 INTEGRATION_CONFIG_INVALID
        3. 校验 input（标准 API 请求字段，未知字段拒绝）           → 400 VALIDATION_ERROR
        4. 初始化项按依赖顺序执行（Vault Reveal 等）               → 503 INTEGRATION_INITIALIZATION_FAILED
        5. 预算检查                                               → 503 INTEGRATION_BUDGET_EXHAUSTED
        ───────── 第一个节点开始：此后一律 200，任何异常都转为 executeStatus=ERROR ─────────
        6. 流程引擎：节点 → 原子 API 单次执行 → steps.<id> → 按优先级判断出边 → 出口映射
    ← 200 {executeStatus, outputs}
```

标准 API 或实现不存在分别返回 404 `STANDARD_API_NOT_FOUND`、`INTEGRATION_IMPLEMENTATION_NOT_FOUND`。第 6 步之前的 Problem 保证没有发出渠道请求（内部接口 3.2 节）。

## 2. 模块与包（platform-support-integration）

| 包 | 内容 |
|---|---|
| `service/processor` | `ExecuteStandardApiProcessor`、`ExecutionSnapshotLoader`（只读事务加载） |
| `domain/execution` | `ExecutionSnapshot`、`ExecutionContext`（input / resources / steps）、`Deadline`（流程预算）、`ProtectedHandle`、`ExecutionResult` |
| `domainservice/execution` | `FlowEngine`（出边判断、节点调度、出口映射）、`AtomicApiExecutor`（单次原子 API）、`ValueResolver`（PATH / EXPRESSION / FUNCTION 取值与运行时缺失、类型规则）、`ResponseFieldChecker` |
| `domainservice/expression` | `ExpressionEngine`（Aviator 白名单剖面、编译缓存）、`ScriptRuntime`（HTTP 工作对象、Header 容器、阶段读写权限） |
| `domainservice/function` | 注册函数实现：`ChannelAccountConfigFunction`（Vault）、`MoneyMinorUnitsFunction`，以及脚本函数（第 5 节） |
| `integration/outbound` | `ChannelHttpClient`：出站 HTTP（`RestClient` + JDK `HttpClient`） |
| `platform-support-api` | `StandardApiCommandApi`、`ExecuteStandardApiRequestBody`、`ExecuteStandardApiResponseBody`、执行 Problem 错误码枚举 |
| `platform-support-web` | `StandardApiExecutionController`（只做协议适配） |

`RegisteredFunctionCatalog` 保持函数签名的唯一来源；E30 能力目录随之自动包含新增的脚本函数。

## 3. 关键行为

| 项 | 做法 |
|---|---|
| 配置加载 | 每次调用一次只读事务，不缓存配置（保存即对新调用生效，无发布版本）；表达式按文本缓存编译结果（LRU，不缓存数据） |
| 取值 | PATH 读上下文；EXPRESSION 用 Aviator 求值；FUNCTION 调纯函数后用 `result` 表达式取值。按流程结构第 5 节：必填缺失、null、类型或精度不符一律 ERROR，非必填缺失则省略，不隐式转换或舍入 |
| 请求组装 | `fixedValues` + 节点 `request` → HEADER（大小写不敏感）/ QUERY / PATH（按单个路径段编码）/ BODY（按点分路径组装对象）；`requestEncoding` 为 JSON、FORM_URLENCODED、NONE |
| 脚本 | REQUEST 阶段读写 `http.requestHeaders`、`http.requestBody`；RESPONSE 阶段读写响应工作副本，只读已发送请求与状态码；`params.*` 只读。受控句柄只能交给注册函数，脚本读不到明文 |
| 传输 | 关闭自动重试、重定向、Cookie、隐式认证；超时取原子 API 配置与流程剩余预算的较小者；请求与响应 Body 各 1 MiB、Header 合计 16 KiB 上限；`Host`、`Content-Length` 等由传输层处理 |
| 响应 | `responseDecoding` 为 JSON 时解析到 `http.responseBody`，NONE 交给脚本；保留原始字节供验签函数读取；`statusRules` 第一条为真决定 SUCCESS/FAIL，全部为假为 ERROR；SUCCESS 时校验响应必填字段 |
| 节点结果 | `steps.<id>.executeStatus` 与 `steps.<id>.outputs`；节点内任何异常（含超时、解码失败）都记为该节点 ERROR，流程继续按出边判断 |
| 出边与出口 | 条件非布尔或异常：整次调用 ERROR；出口按标准 API 响应字段映射，SUCCESS/FAIL 必填输出缺失时整次改为 ERROR |
| 凭据 | `ProtectedHandle` 不实现明文 `toString`，异常信息只用固定文字；只在本次调用内存中存在 |
| 日志 | 只记 Request ID、`standardApiCode`、`integrationCode`、节点 ID、`executeStatus`、渠道 HTTP 状态与耗时；不记 Header/Body/字段值（内部接口第 6 节） |

## 4. Aviator（第 1 步评审点 D3 的落实）

- **引入：** 新增依赖 `com.googlecode.aviator:aviator`，并在[技术栈](../../../agent-docs/TECHNOLOGY_STACK.md)登记版本与使用边界（只用于外部 API 配置的表达式与公共脚本）。
- **白名单剖面：** 关闭模块导入、`new`、静态方法、反射、`use`、Lambda 外部变量捕获等能力，`ALLOWED_CLASS_SET` 为空；限制循环次数与单次求值时间。
  - 条件与取值表达式：只读，不允许赋值。
  - 公共脚本：允许对 `http.*` 工作对象赋值，以及 `let`、`if`、`for` 等有限语句。
- **保存时编译：** 第 1 步只检查表达式长度与路径引用。本步在各静态校验器中增加白名单编译，失败返回新问题码 `EXPRESSION_INVALID`（位置指向具体表达式），页面校验与保存自动生效。

## 5. 注册函数（首批）

| 函数 | 类型 | 说明 |
|---|---|---|
| `CHANNEL_ACCOUNT_CONFIG` | 初始化 | 按材料交接第 4 节：以 Caller `platform-support-service`、Purpose `CHANNEL_EXECUTION` Reveal，核对 `channelCode/channelAccountId/entityNo` 一致；返回 `parameters`（字符串）与 `credentials`（受控句柄）；任何失败都是初始化失败，不返回部分结果 |
| `MONEY_MINOR_UNITS` | 纯函数 | 金额字符串 + 币种 → 最小单位整数；按币种精度，不隐式舍入 |
| `bearerAuth(handle)` | 脚本 | 用受控句柄生成 `Authorization: Bearer …` |
| `basicAuth(userHandle, passwordHandle)` | 脚本 | 生成 Basic 认证头 |
| `hmacSha256Hex` / `hmacSha256Base64(handle, text)` | 脚本 | 用句柄作密钥对文本签名 |
| `sha256Hex(text)`、`base64Encode(text)` | 脚本 | 常用摘要与编码 |
| `json.get / json.set(obj, path, value)` | 脚本 | 按点分路径读写工作对象中的嵌套值 |

渠道专用签名或加密（如 NI 的具体规则）等拿到真实渠道合同后再按“代码注册函数”增加，不放进脚本。

## 6. 测试

| 位置 | 内容 |
|---|---|
| 纯 Java（`auto/domain`） | Aviator 剖面（禁用能力被拒绝、超时与循环限制）、`ValueResolver` 的缺失/null/类型规则、请求组装（Header 大小写、Path 段编码、FORM 编码、NONE 不带 Body）、`MONEY_MINOR_UNITS` 精度 |
| 入口测试（`auto/ext/http`） | 从 `StandardApiCommandApi` 发真实 HTTP。渠道由测试专用 Mock HTTP 服务模拟（按路径与方法返回配置好的响应，记录收到的请求），Vault 由现有 Vault Mock 返回账号配置 |
| 场景 | 成功；FAIL 分支；ERROR 后按条件补查再成功；全部规则不匹配为 ERROR；渠道超时或断连为 ERROR；400 输入校验；404 两种；500 配置无效；503 初始化失败与身份不一致；503 预算耗尽且渠道零调用；签名 Header 正确；明文凭据不出现在响应、异常与日志 |
| 页面能力 | 补一条 E30 测试：能力目录包含新增脚本函数 |

“200 之前保证没发出渠道请求”要用渠道 Mock 的零调用断言证明。

## 7. 实施顺序与验证

| 子步 | 内容 | 完成标准 |
|---|---|---|
| 3a | 引入 Aviator、白名单剖面、保存时编译校验（`EXPRESSION_INVALID`），技术栈登记 | 纯 Java 测试与第 1 步全部测试通过 |
| 3b | 执行引擎核心：快照加载、取值、流程引擎、出口映射 | 纯 Java 测试覆盖取值、出边与出口规则；整条链路在 3c 用入口测试验证，不替换内部组件 |
| 3c-1 | （2026-10-13 调整）提前接入执行入口：`StandardApiCommandApi`、Controller、Processor 第 1～3 步与第一个节点前的 Problem 映射，使快照加载器有调用方 | 入口测试覆盖 404、500 与 422 |
| 3c-2 | 初始化项、预算检查、出站 HTTP、脚本运行时、注册函数、Vault 读取，删除 3c-1 的未实现异常 | 入口测试（渠道 Mock + Vault Mock）走通完整流程 |
| 3d | 503 Problem、日志边界、“第一个节点开始后一律 200”的异常兜底与零调用断言 | Platform `auto` 全部通过 |
| 3e | 本机端到端：沿用 2e 独立库，本机 Mock 渠道与 Vault；更新台账 | 端到端走通 PAY 示例的成功、拒绝、补查三条路径 |

每个子步结束交付结果并等待确认。

**3a 结果（2026-10-13，待确认）：** 新增 `domainservice/expression`（`ExpressionProfile` 只读/脚本两个剖面、`ExpressionEngine`、`ExpressionInvalidException`）。取值、条件、FUNCTION result 与公共脚本保存时编译，失败报 `EXPRESSION_INVALID`；只读表达式另外拒绝上下文根以外的变量。与第 4 节的差异：Aviator 5.3.3 没有求值超时选项，单次耗时改由“无 while、无 fn/Lambda 递归、禁用正则匹配 `=~`、for 循环最多 1000 次、源码长度上限”约束，整次调用的时限在 3b 由流程预算控制。

**3b 结果（2026-10-13，待确认）：** 新增 `domain/execution`（快照、流程、上下文、字段结构树 `FieldSchema`、`Deadline`、节点与调用结果、`JsonValues`）、`domainservice/execution`（`ValueResolver`、`FlowEngine`、`NodeRunner`）、`domainservice/function`（`PureFunction`、`MoneyMinorUnitsFunction`）、`service/processor/ExecutionSnapshotLoader`，以及 api 枚举 `ExecuteStatus`、`StandardApiExecutionErrorCode`。实施中确定的规则：
- 字段结构没有 nullable，取值时 null 与缺失同样表示“没有值”（必填 ERROR、非必填省略），与原子 API 合同第 8 节示例用 `nil` 表示省略一致；输入中的显式 null 返回 400。
- 没有声明子字段的 OBJECT、没有声明元素的 ARRAY 视为不透明，内容不再按字段校验；声明了子字段的对象拒绝未声明属性。
- DECIMAL 在 Wire 上是十进制字符串（不接受指数），环境中为 BigDecimal；INTEGER 是 JSON 整数，环境中为 Long；输出时 BigDecimal 写回字符串。类型严格匹配，不做整数与小数互转。
- `MONEY_MINOR_UNITS` 的金额参数按注册签名为 STRING（非负十进制），币种精度取 JDK ISO 4217 表。
- 节点执行通过 `NodeRunner` 接入，3c 的原子 API 执行器实现它；`FlowEngine.run` 的完整链路在 3c/3d 用入口测试验证。

**调整（2026-10-13）：** 用户指出 3b 的 `ExecutionSnapshotLoader` 没有调用方。按用户确认，把执行入口提前为 3c-1，3c 其余内容改为 3c-2，3d 收窄为收尾。

**3c-1 结果（2026-10-13，待确认）：** 新增 `api/standardapi`（`StandardApiCommandApi`、请求与响应 Body）、`ExecuteStandardApiProcessor`（第 1～3 步）、`StandardApiExecutionController`，并在 `PlatformApiErrorConfiguration` 映射 5 个执行错误码。与内部接口的差异：
- **input 不满足字段：** 返回 **422** `VALIDATION_ERROR` 并带 `errors[]`（位置以 `input.` 开头），而不是内部接口 3.2 表中的 400；依据是[全系统 Problem 设计](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)第 4.1 节“Body 字段错误一律 422”。JSON 格式错误仍为 400。
- **5xx 代码：** Foundation 只把 `FailException` 映射为 4xx，`INTEGRATION_CONFIG_INVALID` 等 5xx 代码必须用 `ErrorException` 抛出。
- **临时状态：** input 通过校验后，Processor 暂时抛出“尚未接入初始化与节点执行”的 500（代码中有 `TODO by CLAUDE`），3c-2 接入后删除；本步没有测试或依赖这条路径。

**3c-2 结果（2026-10-13，待确认）：** Processor 接上第 4～6 步，3c-1 的临时异常与 TODO 已删除。新增：
- `InitializerRunner`（按 resources 引用拓扑串行执行；必需项失败 503 `INTEGRATION_INITIALIZATION_FAILED`，执行前预算耗尽 503 `INTEGRATION_BUDGET_EXHAUSTED`）与 `ChannelAccountConfigFunction`（Vault Reveal，严格解析并核对 channelCode/channelAccountId/entityNo，credentials 包装为 `ProtectedHandle`）。
- `AtomicApiExecutor`（实现 `NodeRunner`）、`ChannelRequestBuilder`（取值分组、BODY 结构校验、JSON/FORM/NONE 编码、路径段编码、超时取较小值）、`ScriptRuntime` 与 `HttpWork`（阶段读写权限）、`ext/outbound/ChannelHttpClient`（JDK HttpClient，不重定向、无 Cookie、无认证、不重试，1 MiB/16 KiB 上限）。
- 脚本函数 `bearerAuth`、`basicAuth`、`hmacSha256Hex/Base64`、`sha256Hex`、`base64Encode`、`json.get/json.set` 登记到注册函数目录（类别 `SCRIPT`，用途 `INTEGRATION_SCRIPT`）并注册到脚本剖面。
- 配置 `ps.integration.execution.allowed-schemes`（默认 https）：保存出站地址与执行前都按它检查；只有测试 mock 配置加入 http。

实施中定下的做法：
- **认证函数返回受控句柄：** `bearerAuth`/`basicAuth` 的结果是句柄，赋给请求 Header 后由传输层发送时取出明文，脚本读不到凭据（材料交接第 5 节）；HMAC 等摘要结果是普通字符串。
- **JSON 响应体为空** 时 `http.responseBody` 为 nil，不算解码失败，交给状态规则判断。
- **响应字段校验** 只检查已声明字段：出现即校验类型，必填只在 SUCCESS 且上级存在时检查；不拒绝渠道返回的其他字段。
- **缺少 Content-Type** 时按编码补 `application/json` 或 `application/x-www-form-urlencoded`。
- **连接超时** 按整秒向上取整复用 HttpClient（最多 10 个）；等待响应超时按毫秒精确设置。
- 503 Problem 由 Foundation 以 `ErrorException` 映射，其公共日志记为 “系统处理结果未知”（ERROR 级别），属 Foundation 现有行为。

**3d 结果（2026-10-13，待确认）：** 补齐 3c-2 未覆盖的入口场景，没有修改生产代码。`StandardApiExecutionScenarioTest`（5 个）覆盖：GET + 固定 QUERY + 无请求体 + `bearerAuth` + RESPONSE 脚本 `json.get/json.set`；表单编码 + `basicAuth`；执行前预算耗尽 503；非必需初始化项失败（节点因受控句柄缺失 ERROR，渠道零请求）；第一个节点之后出边条件非布尔（200 + ERROR）；并用输出捕获断言 Platform 日志中没有凭据与渠道响应报文。`StandardApiExecutionTest` 的 500/422 用例补充“渠道零请求、Vault 零读取”断言。
- 测试发现：本机 Mock 服务（Tomcat）默认拒绝路径中的 `%2F`，返回非 JSON 错误页，执行器按约定记为节点 ERROR；路径段编码改用 `?`（`%3F`）验证。真实渠道是否接受 `%2F` 取决于渠道服务端。

**3e 结果（2026-10-13，待确认）：** 本机端到端通过，回执见 [stage3e/e2e-receipt.json](./assets/stage3e/e2e-receipt.json)，启动器与脚本见 [stage3e/harness](./assets/stage3e/harness/)。配置经 Web 代理 → Gateway → BFF → Platform 保存（标准 API、接入方、签名脚本、下单与查询两个原子 API、带补查分支的流程；越权脚本 `eval(...)` 保存被 422 拒绝），执行直连 Platform 内部接口：成功、拒绝、补查（下单 504 → 查询 CAPTURED）三条路径结果正确；前置 404/422 时渠道零请求；Gateway 不路由执行接口；合成渠道验证下单签名全部有效；各服务日志不含 API Key。2e 验证脚本中无法保存的示例源码已替换为白名单写法。

## 8. 风险

- **表达式安全：** Aviator 默认能力过宽，剖面配置错误可能执行任意代码。用专门的拒绝测试锁定白名单。
- **“未发出请求”承诺：** 第一个节点开始后的任何异常（包括框架异常）都必须转为 `200 + ERROR`，用入口测试覆盖。
- **真实渠道缺失：** 本步只用合成渠道验证；真实 NI 协议、签名函数与网络白名单在接入时补。
- **Vault 策略：** 本机与测试用 Vault Mock；共享环境的策略合并与部署需单独授权（策略片段已在需求目录）。

## 9. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| G1 | Aviator 版本 | **5.3.3**：本机 Maven 仓库已有、离线可构建；5.4.x 需要经 Nexus 拉取。登记到技术栈 |
| G2 | 表达式与脚本的能力范围 | 按第 4 节：条件与取值只读；脚本只允许改 `http.*` 工作对象，关闭 `new`、静态方法、反射与模块导入，限制循环次数与求值时间 |
| G3 | 首批脚本函数 | 按第 5 节的通用集合；渠道专用算法拿到真实合同再加 |
| G4 | 配置是否缓存 | **不缓存配置**，每次调用一次只读事务加载；只缓存表达式编译结果 |
| G5 | 出站地址协议 | 生产只允许 https（保存时已校验）；执行时再检查一次协议，允许的协议做成配置项，本机 mock 配置允许 http 指向测试服务 |
| G6 | 运行前是否重新校验配置 | **是**：每次执行前用保存时的同一套校验器校验快照，防止配置被跨版本或手工改坏后带病执行（返回 500，未发请求） |
| G7 | Vault 策略 | 本步只用 Vault Mock；不修改共享或线上环境的 Vault 策略 |
| G8 | 代码授权 | 授权修改 Platform 的 integration、api、web、test 模块与根 `pom.xml`，以及[技术栈](../../../agent-docs/TECHNOLOGY_STACK.md)登记 Aviator |
