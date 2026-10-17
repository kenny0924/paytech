# Commerce Client Webhook 业务事件 — 交付记录

## 适用阶段

阶段 1—5 与 8；阶段 6—7（test 发布）按 Q4 不适用，Commerce 上 test 另立需求。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-17 | [requirement.md](./requirement.md) | “按建议”：F-01—F-06、AC-01—08；Q1 本期 6 种事件；Q2 可推导稳定事件 ID；Q3 4xx 不重试记失败告警；Q4 只做本机验证；Q5 与 Commerce 会话协调后由本会话只改 Webhook 相关文件 | 阶段 2 现状差异与影响分析 |
| 2. 现状差异与影响分析 | 2026-10-17 | [technique.md](./technique.md) 第 1—4 节 | “按建议”：D1 本期只做 `order.paid`、`order.cancelled`、`refund.created`，退款终态 3 种随“Commerce 接入 Payment 退款”另立需求；D2 404 视为暂不可用可重试；D3 沿用默认重试 | 阶段 3 方案、接口与数据设计 |
| 3. 方案、接口与数据设计 | 2026-10-17 | [technique.md](./technique.md) 第 5—11 节 | “继续” | 阶段 4 研发（本机代码与测试；不含提交、推送、部署、SQL） |
| 4. 研发实现 | 2026-10-17 | 见下文“阶段 4 研发结果” | “可以” | 阶段 5 本机全链路验证 |
| 5. 本机全链路验证 | 2026-10-17 | 见下文“阶段 5 本机全链路验证” | “继续”（回复“可以本地提交并进入阶段 8 收尾吗”） | 本地提交（独立暂存区，只含本需求文件与段落）；阶段 8 收尾 |
| 8. 收尾 | 2026-10-17 | 见下文“阶段 8 收尾” | — | 结束；推送、部署未授权 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本机代码修改与测试、本机全链路验证 | 阶段 4—5 范围内 |
| 本地提交 | 2026-10-17 已授权并执行（不推送） |
| SQL（本需求不涉及）、推送、部署 | 未授权 |

## 当前阶段

已结束：阶段 1—5 与 8 完成，已本地提交（未推送、未部署）。实现状态见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-webhook-events-20261017)。

## 阶段 4 研发结果（2026-10-17，未提交）

| 项 | 结果 |
|---|---|
| 生产代码 | `ClientWebhookEventType`、`ClientWebhookEvent`（3 种对象）、`ClientWebhookSubmissionHandler`（`buildEvent` 冻结 + `handle` 核对 Hash 并提交 Platform）、`PlatformClientConfiguration`；`OrderPaymentServiceImpl`、`OrderCancellationServiceImpl`、`RefundServiceImpl` 各一处登记；金额格式抽到 `CommerceDecimalRules.toAmountString`，GET 订单与事件共用 |
| 配置与依赖 | commerce `pom.xml` 依赖管理与 commerce-web 依赖 `platform-support-api`；`application.yml` 服务组 `platform-support-service`（`lb://platform-support-service/platform-support`，连接 2 秒、读取 5 秒、不跟随重定向） |
| 测试 | 新增 `ClientWebhookOrderPaidTest` 2、`ClientWebhookOrderCancelledTest` 4、`ClientWebhookRefundCreatedTest` 2、`ClientWebhookSubmissionTest` 12，共 **20/20**；测试设施 `MockWebhookEventApi`、`PlatformWebhookMocker`、`ClientWebhookChecker`、`ClientWebhookTestMapper`；`MockTestBase` 全局默认受理并检查意外调用 |
| 全量 | Commerce `./mvnw -o test` **810/810**；`verify` 测试全过后被既有 Hosted Checkout 覆盖率门禁拦截（行 0.85 / 要求 0.90，台账已记录为既有） |
| 已确认 | 严格 JSON 客户端可处理 Platform 无正文 204（G7）；503 每次只调用一次，无隐藏传输重试 |

偏离设计：
1. 未建 `ClientWebhookEventService`：仓库规范禁止 Service 调用 Service，改为与 `RefundSubmissionHandler` 相同写法——Handler 静态 `buildEvent` 负责规范化、Hash 与大小检查，各业务 Service 在自己的事务里 `register(Handler.buildEvent(...))`。
2. 422 `VALIDATION_ERROR` 也记为明确失败：客户端组加 `withRequestValidationFailures()`，否则 422 会解码为未知错误被重试。
3. `refund.created` 与 GET Refund 比对时，把提交任务随后推进的 `refundStatus`、`version`、`updatedAt` 还原为创建时的值（退款提交在 Payment 未接入时会进入 PROCESSING），其余字段逐字比对。
4. 4096 字节超限只在 `buildEvent` 单元路径验证：真实输入达不到上限。
5. 测试端点默认对所有事件返回 204：否则所有支付成功、取消、退款的既有测试都会留下失败任务和意外调用（设计第 9 节已约定）。

风险：`ClientWebhookSubmissionTest` 调用真实补偿扫描（限定 `CLIENT_WEBHOOK_REQUESTED`），若另一 JVM 同时对同一 `mor_commerce` 跑 Commerce 测试，可能扫到对方到期的同类任务；本次未见干扰。

## 阶段 5 本机全链路验证（2026-10-17，未提交）

**环境**
- 本机 Platform 重启（pid 38451）：制品不变（Webhook 运营会话的 `e2e-artifacts-webhook-ops`），只把 `run-service.sh` 中 `commerce-service` 白名单扩为 `order.paid,order.cancelled,refund.created`。重启前取得 Webhook 会话与 Payment 会话同意。
- Commerce 从当前工作区构建，复制到 scratchpad 后在 18102 启动。参数：
  - 库 `mor_commerce`，Nacos `mor-local`，RabbitMQ `/mor-local`；
  - `COMMERCE_CARD_PAYMENT_METHOD_CODE=CARD`、`COMMERCE_CHECKOUT_PUBLIC_ORIGIN`、Snowflake 0/5、`--command-idempotency.enabled=false`；
  - Commerce 自行声明支付结果队列。

  验证期间没有运行 Commerce 测试，结束后 Commerce 已停止，并通知了 Payment 会话。
- Commerce Client API 直连 18102，带 Gateway 注入的可信 Header（`X-Gateway-Client-Id` 等），因为本机没有 Client API 业务路由。建结账时真实调用 Risk，CCT、税务、文档为 Commerce 进程内 Mock。

**结果**：新增 `verification/local-e2e/commerce_webhook_flow.py`，**66/66 通过**。

| 验收 | 结果 |
|---|---|
| AC-03 `order.cancelled` | 取消 200；接收端收到 1 次，签名可用端点密钥校验；`evt_ord_…_cancelled`；Envelope 5 个顶层键；字段与 GET 订单、取消响应一致，`reasonCode=CLIENT_REQUESTED` 与库一致，版本与库、Platform `source_version` 一致；重复取消不产生第二个任务、事件或投递 |
| AC-01 `order.paid` | 向 `topic_payment_result` 发布合规 PaymentSucceeded；订单 PAID，收到 1 次，签名有效；字段逐项等于 GET 订单 |
| AC-02 | 同一消息再发一次：订单版本不变，没有新任务，Platform 仍为 1 个事件、1 次投递 |
| AC-04 `refund.created` | 201，收到 1 次，签名有效；键集合与取值等于创建后立即读取的 GET 退款（version 1、PENDING）；同一 `clientRefundId` 重放返回同一退款号且没有新事件；之后退款变为 PROCESSING v2，事件仍冻结在 v1 |
| 3 种事件共有 | Commerce 任务完成（Foundation 成功后删除任务行）；Platform 各 1 个事件、1 次 SUCCEEDED 投递（attempt 1）；接收到的正文与 Platform 受理的载荷逐字节相同；接收端共收到 3 次 |
| AC-07 | `data.object` 无内部数字 ID、entityNo、Consumer 或风控字段；Commerce 与 Platform 日志无 `whsec_`、载荷片段、clientOrderId 与 clientRefundId 原值，无 ERROR |
| AC-05（部分） | 让 Commerce 访问不到 Platform（base-url 指向关闭端口）：取消仍成功，任务保持可重试的 ERROR（`PLATFORM_UNAVAILABLE`，已排下次执行时间，冻结 Envelope 保留），Platform 无事件。**Platform 恢复后的补交未验证**：补偿组件提交后只唤醒新登记的任务，重试依赖 XXL-Job 扫描，本机未启用（与 D3 一致） |

**夹具（已标注）**
- 本机没有 Payment 进程，两个已支付订单的 Intent 关联由手工插入的 `t_order_payment_projection` 行代替（ID `9400000048713826`、`9400000002050866`）。真实链路中这两行由 Payment createIntent 产生。
- 除此之外，没有直接写库。

**观察（非本需求缺陷）**
- 已有的退款提交任务因 Commerce 未接 Payment 退款而失败（预期）。
- 该任务的 `event_type` 存的是字面值 `SUBMISSION_EVENT_TYPE`（`RefundSubmissionHandler.java:24`），像是占位名，属既有提交代码。

**遗留数据**
- 3 个已批准验收 Client，端点指向已关闭的本机端口。
- `mor_commerce`：5 个订单、2 个退款、2 行夹具投影，以及 3 行 ERROR 任务（2 个退款提交、1 个 AC-05 Webhook）。Commerce 自动测试会清理 `mor_commerce`。
- `mor_platform_e2e`：6 个 Webhook 事件、6 次成功投递。
- `mor_risk`：结账评估记录。

## 阶段 8 收尾（2026-10-17）

| 项 | 结果 |
|---|---|
| 合同文档 | [Commerce Client API V1 设计](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 13.5 改为补偿任务事务外调用 Platform 内部受理接口，写明重试与失败口径、本期 3 种事件与 Event ID 规则 |
| Commerce README | 新增“Client Webhook 业务事件”一节（触发点、配置、Platform 前提）；“尚未实现”改为退款终态、过期与资金可用事件 |
| 台账 | `CURRENT_IMPLEMENTATION.md` 本需求条目、`requirements/README.md` 登记行 |
| 提交 | 独立暂存区本地提交，不含 Commerce 目录外其他会话改动；未推送 |

后续需求：Commerce 接入 Payment 退款（PAY-I04，含 `refund.succeeded/failed/cancelled`）；订单过期与 `order.expired`；资金可用与 `order.funds_available`；Commerce 部署 mor-test（含 XXL-Job 补偿扫描，补验 AC-05 恢复后补交）。
