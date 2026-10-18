# Payment 对接 PayerMax 卡直付：交付记录

## 1. 阶段划分

见[技术方案第 9 节](./technique.md#9-阶段划分)。阶段 1 **已确认**（2026-10-14）；阶段 2 **已确认**（2026-10-14）；阶段 3 **已交付，待确认**。

## 2. 用户决定（2026-10-14）

- “先做 payment 对接，先把 payment 链路跑通”：暂停 PayerMax 退款阶段 3（mor-test 已下单的 UAT 订单 `PMXV456542980737` 未付款，退款配置保留）。
- 收银台打开时就要决定采集方式；用户选择“先走卡的模式，不使用渠道收银台”，并要求调整 PayerMax API。
- “PCI 一定要拿到的，所以先走渠道使用卡支付的模式”：采用服务端卡直付（PayerMax Direct API - CARD），PCI 认证与 PayerMax 开通作为上生产前提。

## 3. 阶段 1 结果（2026-10-14）

- 已读：需求第 2 节与技术方案依据所列文档与源码；Payment 现状由只读梳理确认（PAY/Risk/Authorize/Authenticate Actor 全为 TODO、路由硬编码、Card 仅 Mock、无 Platform 客户端、无恢复任务、`PayResponse` 无交互指令）。
- 发现的核心冲突：渠道协议统一在 Platform 执行，而 Card 规则与 Platform 内部接口禁止卡原值进入 Platform；PayerMax 签名覆盖卡号，排除出站代理替换方案。建议方案 B（技术方案第 2 节）。
- 未修改代码、配置或数据库；未提交。

## 4. 阶段 1 修订（2026-10-14，第 2 版评审稿）

用户意见：① 卡在服务间用卡 ID/Token 传递，Platform 可读取，但不能打印日志、只能在内存中；② 一步步来，风控先 Mock；③ 路由按建议；④ 恢复将来依赖渠道通知加重试，暂不考虑，先打通主链路。

据此修订：卡数据通道改为 Token + Platform 初始化函数 `PAYMENT_CARD` 回读 Payment Card（取消原方案 B 的专用卡执行入口）；恢复移出本期，阶段由 5 个减为 4 个。T0、T2～T5、T8、T9、T11 仍待确认。

## 5. 阶段 1 确认（2026-10-14）

用户回复“可以”：第 2 版评审稿确认，T0、T2～T5、T8、T9、T11 按建议；授权本会话修改 Platform 与 Payment（execution、card、channel、routing、web、api、test）相应代码，不含提交、推送与共享环境变更。进入阶段 2（Platform）。

## 6. 阶段 2 结果（2026-10-14，待确认）

已读：[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)第 7 节与[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)相关章节（本任务此前已完整读取、未变化），执行引擎、校验器、验证运行与测试设施源码。本阶段不新增 Platform 业务写接口与数据库访问；读卡在执行引擎初始化阶段、数据库事务外进行。

**Payment API（合同）：** `paytech-payment-api` 新增 `ChannelCardMaterialApi`（`POST /payment-card/v1/channel-card-materials/reveal`，Caller Header、Token、支付尝试 ID、用途；响应 toString 脱敏）。服务端实现在阶段 3。

**Platform `platform-support-integration`：**
- `CardValue`（卡值句柄，与 `ProtectedHandle` 分开，无 getter，toString 与遮蔽文本不含原值）；`JsonValues` 增加卡值写法 `REJECT`（默认）/`WIRE`/`MASKED` 与 `containsCard`。
- `ChannelRequestBuilder`：`cardFields` 声明的字段按 `CARD` 类型取值；JSON 发送字节写原值，新增 `maskedJsonBody`；FORM 编码遇到卡值失败。`AtomicApiExecutor`：沙箱验证轨迹的请求体改用遮蔽副本（仅在有轨迹时生成）。`ValueResolver` 增加 `CARD` 类型，`FieldSchema` 的 STRING 接受卡值句柄。
- 注册函数 `PAYMENT_CARD`（`PaymentCardFunction`：调用 Payment 读卡接口并校验格式，失败只报固定文字）；沙箱验证用 `VerifyPaymentCardFunction` 读验证账号 `testCard.*`；`InitializerRunner` 分派。
- 校验：`IntegrationApiDefinitionValidator` 校验 `cardFields`（`CARD_FIELD_INVALID/FIXED/DUPLICATED/ENCODING`）；`FlowDefinitionValidator` 卡字段只接受 `resources.<id>.<卡值>` 的 PATH（`CARD_BINDING_INVALID`），其他位置引用卡值为 `REFERENCE_NOT_ALLOWED`；`ApiContract` 携带卡字段。
- `platform-support-web`：`PaymentCardMaterialConfiguration`（私网服务发现 `payment-orchestration-service`，连接 1 秒、读取 2 秒、不跟随重定向，出站客户端不记 Body）。

**Operations 前端：** 原子 API 编辑页“接口与 HTTP 传输”增加“卡字段”多选（BODY 字符串字段），`toDraft`/`toDefinition` 保留 `cardFields`（此前编辑保存会丢弃未知属性）。流程编辑器未区分卡值句柄的提示，保存以服务端校验为准（遗留）。

**合同与决定：** 新增[卡数据经 Platform 渠道执行决定](../../../agent-docs/decisions/2026-10-14-card-data-platform-execution.md)；修订 Card 需求 CARD-01、Platform 内部接口第 2 节、[原子 API 结构](../../20260914/platform-support-外部API配置与执行/integration-api-definition-contract.md)第 2、5.3 节与[流程结构](../../20260914/platform-support-外部API配置与执行/flow-definition-contract.md)第 3、6 节。

**测试（实际运行）：**
- 新增 `CardFieldRulesTest`（3，纯 Java：卡值写法与遮蔽、`cardFields` 校验、流程卡值绑定）与 `StandardApiCardPaymentTest`（4，真实 HTTP，Payment 与渠道为测试 Mock HTTP 服务：卡值只在渠道字节且被签名覆盖、读卡请求的调用方/Token/支付尝试/用途、日志无卡号与 CVV；读卡被拒与卡格式无效均 503 且零渠道请求；沙箱验证不调用 Payment、证据与库中轨迹只有遮蔽文本）。测试设施新增 `PaymentCardMocker`、`PaymentCardMockController` 与服务发现映射，并纳入全局意外调用检查。
- `ExternalApiEditorTest` 函数目录断言加入 `PAYMENT_CARD`。
- JDK 25 `./mvnw -o -pl platform-support-test -am test`：**424/424**。`operations-console-web`：`npm test` **990/990**，`npm run build` 通过。

**PayerMax 配置与本机验证（[harness](./assets/harness/README.md)，本机 `mor_platform_external_api`，Web 代理 → Gateway → BFF → Platform）：**
- `configure_card.py PAYERMAX_MOCK PAYERMAX`：两个接入方的收银台版 `PAY` 流程先按版本删除，再改标准 API `PAY`、原子 API `ORDER_AND_PAY`（`Direct_Payment`、五个卡字段、`buyerInfo`）并创建卡直付流程；重跑按版本修改。按 T4，本机收银台版本不保留；mor-test 未改。
- 合成 PayerMax 增加卡直付（检查卡字段格式，按 `subject` 返回结果，被拒返回 `ISSUER_PAYMENT_REJECTED`）；运行目录已有密钥时沿用。
- `verify_card.py mock`：7 个用例 **PASSED**（成功、3DS、处理中、被拒、参数错误 FAIL、系统繁忙 ERROR、响应签名错误 ERROR），合成渠道收到的 7 个请求签名、外层、时间、金额与卡字段全部有效。[回执](./assets/stage2-mock-receipt.json)
- `verify_card.py uat`：PayerMax UAT 测试卡（Visa 4444 33** **** 1111）4 个用例 **PASSED**：成功（`SUCCEEDED`）、3DS（`PENDING` + 跳转指令）、处理中、被拒（`FAILED`，`ISSUER_PAYMENT_REJECTED`）；PayerMax 对请求验签通过、Platform 对响应验签通过。[回执](./assets/stage2-uat-receipt.json)
- UAT 实测修正见[技术方案第 11 节](./technique.md#11-阶段-2-实现记录2026-10-14)：`userId` 与 `buyerInfo` 必填、被拒的外层码不是 `APPLY_SUCCESS`、无 `data` 的拒绝须先判空。
- 检索：Platform/BFF/Gateway/合成渠道日志、调用记录、回执中测试卡号与 CVV 原值 0 次；库中验证步骤 0 行含卡号原值（66 处为遮蔽文本）。

**待确认：** 技术方案评审点 T12（向渠道传消费者 IP 与 UA）。未提交；本机服务保持运行。

## 7. 阶段 2 确认（2026-10-14）

用户回复“可以，T12 按建议，开始第 3 阶段”：阶段 2 确认；T12 采用建议——向收单渠道只传消费者 IP 与 UA（`buyerInfo`）作为收单必需的最小披露，不传设备指纹，需在 Commerce 隐私设计中补记（Commerce 文档不在本会话范围，列为遗留）。进入阶段 3（Payment）。

## 8. 阶段 3 结果（2026-10-14，待确认）

已读：[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)（模块、HTTP 契约、外部集成、依赖方向）、[写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)、[Payment 专项补充](../../../paytech/payment/docs/development/processor-conventions.md)。

**本次 Processor / 事务：** 读卡入口 `RevealChannelCardMaterialProcessor`（Execution），无数据库写、无事务、不加 Intent 锁（调用发生在 pay 持锁期间）。pay 沿用 `PayProcessor`：候选查询与选路在受理事务前；`ActionFlow` 每个动作的外部调用（Risk、Platform）在事务外，结果、渠道交易引用与执行位切换在一个短事务内保存。实现与差异见[技术方案第 12 节](./technique.md#12-阶段-3-实现记录2026-10-14)。

**数据库（经用户授权）：** 本机测试库 `mor_payment_channel_test` 补建执行链路五张空表（DDL 原文取自正式 Schema，见 [db/test-execution-tables.sql](./db/test-execution-tables.sql)，并同步测试 `init/ddl.sql`）；未改 `mor_payment` 与共享环境。

**测试（实际运行）：**
- 新增 `PayCardChannelTest`（8，真实 pay HTTP 入口，Platform 与 Risk 为测试 HTTP 服务；Platform 测试服务像真实 Platform 一样回调 Payment 读卡接口）：成功（Platform 输入含冻结账号身份、Vault 引用与 Token、按币种精度金额，不含卡号；读卡取得卡号与一次性 CVV；Attempt/Intent 成功、渠道交易引用落库）、3DS（202 + 临时指令，Action 处理中并记 3DS，跳转地址不落库）、拒绝（失败码、Intent 释放）、未知（UNKNOWN、Attempt 保持处理中）、Platform 未发请求（FAILED）、风控不可用（失败关闭、未调用 Platform）、原提交重放（不再评估/执行）、读卡约束（非 Platform 调用方 404、非执行中 400）。
- 测试设施：`PlatformMocker`/`MockPlatformController`、`RiskMocker`/`MockRiskController`、执行五表的计数与清理、`cardFixture`；Channel 公开合同登记新增的四个类型。
- Payment `./mvnw -o -pl paytech-payment-test test`：**64/64**（卡直付 8、Channel 56 未退化）；测试日志中卡号、CVV、跳转地址出现 0 次。Platform 全量 **424/424**（读卡地址改为可配置 context path，默认 `/payment`）。

**配置：** Payment 新增 `payment.outbound.*`、`payment.card.mock-pans`，同步 Nacos 模板与[配置清单](../../../paytech/payment/docs/development/configuration.md)；Platform 新增 `ps.integration.execution.payment-context-path`。

**遗留：** Risk 支付评估为 Payment 草案；恢复（渠道通知与重试）未做；AUTHORIZE/AUTHENTICATE Actor 仍为 TODO；Commerce 隐私设计补记 T12；未提交。下一阶段 4：本机端到端（需另行授权本机运行库建表）。
