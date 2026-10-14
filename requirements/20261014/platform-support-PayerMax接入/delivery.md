# PayerMax 接入交付记录

## 1. 阶段划分（2026-10-14 用户确认）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 需求与接入设计：[需求](./requirement.md)、[技术方案](./technique.md) | **已确认**（2026-10-14） |
| 2 | 引擎补能力：`sealJsonBody`、RSA 签名与验签、`nowRfc3339`、`decimalEncoding`，含 `auto` 测试 | **已确认**（2026-10-14） |
| 3 | PayerMax 配置：经真实管理接口保存两个标准 API、接入方、原子 API、脚本与流程；本机合成 PayerMax 验证 | **已交付，待确认** |
| 4 | 本机联调：先合成 PayerMax，再直连 UAT（下单 → 浏览器付款 → 查询） | 未开始 |

用户同时确认：已有 PayerMax UAT 商户账号，可联调真实 UAT；支付模式选择收银台下单（`integrate=Hosted_Checkout`）。

## 2. 阶段 1 结果（2026-10-14）

- 协议来源：PayerMax 官方 OpenAPI 原始文件（New Version v1.0 中文）、签名规则与交易状态/错误码页面；只在本机临时目录读取，未存入仓库。
- 已读：外部 API 需求目录下的原子 API 结构、流程结构、内部接口、账号材料交接、公共脚本设计、研发计划第 3 步，以及执行引擎相关源码。
- 结论：引擎需要补四项通用能力（技术方案第 2、3 节）；其余能力足够。数据库不涉及结构变更。
- 未修改代码、配置或数据库；未提交。

## 3. 阶段 1 确认（2026-10-14）

- 用户提出标准 API 不需要区分 hosted：不区分时由渠道整合流程路由。采纳为 T6 修订：标准 API 用 `PAY` / `PAY_QUERY`，输出 `paymentStatus` + 可选 `nextAction`；入口按 `input` 分支的流程能力本期不做，见[技术方案第 4.5 节](./technique.md#45-标准-api渠道无关草案)。
- 其余评审点 T1～T5、T7 用户确认“没问题”，按建议执行；T7 授权阶段 2 修改 Platform `integration`、`api`、`test` 模块与 `operations-console-web/src/externalApi/` 中 `decimalEncoding` 相关文件，不含提交、推送与共享环境变更。

## 4. 阶段 2 结果（2026-10-14，待确认）

已读：[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)第 7 节、[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)第 2、5、15～20 节，以及引擎与测试设施源码。本阶段不新增业务接口、事务或数据库访问：执行仍是事务外的渠道调用，改动全部在原子 API 单次执行内部。

**Platform `platform-support-integration`：**
- `HttpWork`：REQUEST 阶段可 `sealJsonBody()` 锁定请求体（JSON 编码器由执行器按本 API 规则传入），锁定后 `requestBody` 替换为只读副本，赋值与嵌套修改都失败；`sealedJsonBody()` 交给传输层。
- `ChannelRequestBuilder`：`jsonBody(api, body)` 统一 JSON 编码（`WRITE_BIGDECIMAL_AS_PLAIN`，按 `decimalEncoding` 写数字或字符串）；有锁定字节时原样发送。
- `AtomicApiExecutor`：传入编码器与锁定字节；JSON 响应小数精确解析；`decimalEncoding=NUMBER` 时把已声明 DECIMAL 的响应整数转为十进制数（支持 `[]` 路径）。
- `ScriptFunctions`：新增 `sealJsonBody`、`rsaSha256Sign`（私钥只经受控句柄）、`requireRsaSha256Signature`（失败即异常）、`nowRfc3339`；`ExpressionEngine` 改为注入应用 `Clock`（保留无参构造，供只做编译校验的场景）。
- `RegisteredFunctionCatalog` 登记四个函数；`IntegrationApiDefinitionValidator` 接受可选 `decimalEncoding`（非法值 `DECIMAL_ENCODING_INVALID`）；E30 能力目录增加 `decimalEncodings`。
- `ExecutionApi.isDecimalAsNumber()`、`JsonValues.toJson(value, decimalAsNumber)`。

**Operations 前端：** 原子 API 编辑页“接口与 HTTP 传输”增加“小数格式”选择（缺省 STRING 时保存不写该属性，已有定义不变）。

**合同同步：** [原子 API 结构](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)第 2、5.2、6 节。

**测试（实际运行）：**
- 新增 `StandardApiSignedChannelTest`（5）：经真实 HTTP 执行，渠道 Mock 用商户公钥独立验签收到的字节、按渠道私钥签名响应；覆盖成功（请求体逐字节、金额 JSON 数字、协议时间取测试时钟、整数金额输出为 DECIMAL、日志不含私钥）、签名有效的明确拒绝、其他私钥签名的响应 → ERROR、缺少响应签名 → ERROR、锁定后修改请求体 → ERROR 且渠道零请求。
- 新增纯 Java `ScriptSigningFunctionsTest`（6）：目录与剖面开放、锁定后只读与重复锁定、不可锁定的编码与阶段、验签各类失败且异常不含公钥/签名、错误私钥与非句柄私钥、协议时间格式。
- 补充 `IntegrationApiManagementTest`（非法 `decimalEncoding` 的 422 位置与校验问题）、`ExternalApiEditorTest`（`decimalEncodings` 枚举）。
- JDK 25 `./mvnw -o -pl platform-support-test -am test`：**349/349** 通过（0 失败、0 错误、0 跳过）。首次运行 36/37：新测试的第二个标准 API 少声明了流程映射的响应字段，运行前配置校验按设计返回 500，修正测试后通过。
- `operations-console-web`：`npm test` 982/982、`npm run build` 通过。

**遗留：** 必填父对象（如 PayerMax `data`）需声明为非必填才能只靠子字段赋值，见技术方案第 4.4 节；未改该规则。未提交。

## 5. 阶段 3 结果（2026-10-14，待确认）

用户 2026-10-14 回复“可以”确认阶段 2 并进入阶段 3。

**harness（[assets/harness](./assets/harness/README.md)）：** `start.py`、`PlatformPayerMaxE2e.java`（真实 Platform、Vault Mock 登记 PAYERMAX 账号、去掉测试固定时钟）、`PayerMaxMock.java`（本机合成 PayerMax，真实 SHA256withRSA 双向签名，检查外层字段、`requestTime` 两分钟时效与金额为 JSON 数字）、`configure.py`（技术方案第 4 节配置的唯一执行来源）、`verify_mock.py`、`common.py`。

**运行（本机，`mor_platform_external_api` 独立库；Web 代理 → Gateway → BFF → Platform 全链路）：**
- `configure.py PAYERMAX_MOCK …` 首次 9 项创建全部通过；重跑 9 项按版本更新通过（首次重跑时脚本自身未把已有实现的版本带给校验接口，返回 `IMPLEMENTATION_EXISTS`，修正脚本后通过）。
- `verify_mock.py` **12 项 PASS**：未知接入方 404、输入缺字段 422、账号身份与 Vault 不一致 503，三者渠道零请求；下单受理（`PENDING` + REDIRECT）、明确拒绝（`FAILED` + `PARAMS_INVALID`）、`SYSTEM_ERROR` → ERROR、响应用错误私钥签名 → ERROR；查询 `SUCCEEDED`（整数金额 → `"10"`、支付方式 `CARD`）、`PENDING`、`FAILED`（带 `PAYMENT_FAILED`）、`CLOSED`、订单不存在 FAIL（`NOT_FOUND`）。合成渠道收到 9 个请求，签名、外层字段、`requestTime` 与数字金额全部有效。
- 各进程日志与调用记录均不含商户私钥。回执：[stage3-receipt.json](./assets/stage3-receipt.json)（合成跳转地址已脱敏）。

**实现中定下的细节：** `PAY` FAIL 出口 `paymentStatus=FAILED`；`PAY_QUERY` 订单不存在为 FAIL + `NOT_FOUND`；`nextAction.type` 声明为非必填（原因见技术方案第 4.5 节）；下单 SUCCESS 规则另要求 `data.status=PENDING` 且有 `redirectUrl`。

未改生产代码、未执行 SQL、未提交。进程已停止。

## 6. 提交（2026-10-14）

用户在查看本机配置后要求“代码提交”。阶段 1～3 的需求文档、Platform 引擎与测试、Operations 编辑页、harness 与回执按本任务范围本地提交到 `main`；不含其他会话的未提交改动，未推送。阶段 3 结果视同确认，阶段 4 待用户提供 UAT 账号文件。

## 7. 阶段 4 调整（2026-10-14）

用户不采用本地 UAT 账号文件，要求在 Operations 页面上完成验证。阶段 4 改由[渠道沙箱验证](../platform-support-渠道沙箱验证/requirement.md)功能完成：在页面录入 PayerMax UAT 账号与用例并运行，保存验证结果。本需求阶段 4 待该功能完成后执行。

## 8. 阶段 4 结果：UAT（2026-10-14，经渠道沙箱验证）

通过 Operations 渠道沙箱验证对 PayerMax UAT 运行（详见[沙箱验证交付记录](../platform-support-渠道沙箱验证/delivery.md)第 7 节、[回执](../platform-support-渠道沙箱验证/assets/stage5-uat-receipt.json)）：下单返回 `APPLY_SUCCESS`、可用的收银台地址与 PENDING，查询同一订单 PENDING，查询不存在订单 `ORDER_NOT_EXIST` → NOT_FOUND；PayerMax 对请求验签通过，Platform 对三个响应验签通过。
- **T5 定论：** UAT 的 HTTP 200 FAIL 响应（`ORDER_NOT_EXIST`）同样带 `sign`，“HTTP 200 一律验签”保持不变。
- **未完成：** 收银台付款后查询 `SUCCEEDED`（需求验收第 3 项后半），需人工付款。
