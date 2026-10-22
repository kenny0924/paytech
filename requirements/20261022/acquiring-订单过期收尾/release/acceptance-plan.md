# acquiring 订单与会话过期收尾 · mor-test 验收计划（阶段 6 编写，阶段 7 执行）

> 发布清单见 [release.md](./release.md)。AC 映射参照[阶段 5 报告](../verification/stage5-report.md)：这里只列 mor-test 能验证的内容，做不到的项注明沿用的阶段 5 或阶段 4 证据。
> 本阶段没有运行任何验收脚本，也没有对 mor-test 做任何写入。

## 1. 前提与 mor-test 的限制

| 项 | 现状（阶段 6 核对） | 处理 |
|---|---|---|
| 验收脚本位置 | `flow_expired` 只在候选工作目录的 V2 脚本里（`/Users/kenny/Desktop/Workspace/Code-MoR-expiry/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/client_api_flow.py`）。主工作区同名脚本要到阶段 8 提交后才有该场景 | 阶段 7 从候选工作目录运行。脚本读取私密值的默认目录 `E2E_TEST_ENV` 指向主工作区 `test-environment`，无需改动 |
| 接收端 | `mor-test-webhook-receiver-1` 自 10-22 05:28Z 起运行，状态 healthy | 不需要重启 |
| 到期订单 | 测试没有真实时钟等待：脚本按技术方案 D5，用 SQL 把本次新建订单的 `expires_at` 回拨 1 分钟 | 这是对 `mor_acquiring` 的直接写入，只改一行一列（release.md 需要决定 D3） |
| Payment 终止链路 | mor-test 没有 Hosted Checkout 站点，也没有可控渠道。`flow_expired` 新建的订单没有 Intent 投影，也没有接受证据，只走本地收尾分支，**不会调用 Payment 新接口** | 另设第 3 节 P-1 探针场景，直接验证 Payment 新接口（release.md D4）。有 Intent 的完整过期链路沿用阶段 5 的 S2 |
| 到期存量 | 16 笔 OPEN 订单在 10-17 到 10-20 之间到期，都是历次验收留下的。它们都没有投影、没有接受证据，Payment 也没有对应 Intent | 第一轮扫描会把它们本地收尾（release.md D1）。第 2 节先核对这一轮的结果 |
| 端点订阅 | 现有 13 个端点里，没有任何 ACTIVE 端点订阅 `order.expired`。唯一订阅了它的端点是 DISABLED 状态 | 存量 16 笔只会在 Platform 产生事件行，不会产生投递。`flow_expired` 的新 Client 端点会订阅 4 种事件 |

驱动方式：
- **Client API：** `E2E_TARGET=test`，入口为 `https://localhost:28444/api/v1`，即 Nginx CLIENT_API → Gateway → acquiring。
- **Payment 探针：** 沿用 3DS 阶段 7 的 `payment-3DS全链路/release/acceptance/mt.py`，起一次性探针容器（`E2E_PROBE_IMAGE`，默认为 Platform 现网镜像 `96da579bb45a`）。探针以 `X-Caller-Service: acquiring-service` 直连 Payment 内部接口。
- **只读核对：** 用 `scripts/precheck.sh` 和按需的只读 SELECT，`xxl_job_log` 也只读查询。

## 2. 第一轮扫描（发布影响面核对，阶段 7 S6 之后、跑验收脚本之前）

| 检查 | 期望 |
|---|---|
| XXL 执行日志（第一次调度或手动触发） | handle 200，汇总行为 `order expiry scan: scanned 16, expired 16, pending 0, already paid 0, reversal required 0, skipped 0, retry 0, failed 0`。条数以 S1 复核的 P3 为准 |
| `mor_acquiring` | 这 16 笔订单变为 `EXPIRED`，`status_reason_code=EXPIRY_REACHED`，并写入 `termination_requested_at`。对应会话变为 `EXPIRED`。`cancelled_at` 仍为空 |
| 补偿任务 | 新增的 16 个 `CLIENT_WEBHOOK_REQUESTED / order.expired` 任务在 30 秒内执行完并删除。原有 8 条 `FAILED` 任务（10-17、10-18 留下）不变 |
| Platform | `t_webhook_event` 新增 16 行 `order.expired / acquiring-service`，`source_event_key=evt_<orderNo>_expired`。`t_webhook_delivery` 对这些事件新增 0 行，因为没有订阅端点 |
| 第二轮 | `scanned 0`，订单版本与 `update_time` 不变 |
| Payment | 不应有任何终止或按来源查询请求，可从 payment 日志与 `t_payment_intent` 版本判断 |

## 3. Payment 终止原因与按来源查询（P-1，探针；release.md 需要决定 D4，建议做）

需在 `payment-3DS全链路/release/acceptance/` 旁新写 `accept_terminate.py`，复用 `mt.py` 与 `accept_3ds.py` 的建 Intent 辅助，写在本目录 `release/acceptance/`。`sourceId` 使用随机合成值（`exp-<run>`），不关联任何订单。

| 步骤 | 期望 | 对应 |
|---|---|---|
| 建 Intent A（`COMMERCE_ORDER` / 合成 sourceId） | 201，状态 OPEN | — |
| `GET /intents/by-source/COMMERCE_ORDER/{sourceId}?clientId=` | 200，响应 `intentId` 等于 A | D1、S3 |
| 同一 sourceId，换成另一个 clientId 查询 | 404 `NOT_FOUND` | 3.3.1 |
| 不存在的 sourceId | 404 `NOT_FOUND` | 3.3.1 |
| 非法 sourceType（小写） | 400 | 3.3.1 |
| `POST /intents/A/terminate`，`reason=ORDER_EXPIRED` | 200，`decision=TERMINATED`，`status=EXPIRED`；库内 Intent 为 `EXPIRED` | AC-02、AC-11 |
| 重复终止 A | 200，`ALREADY_TERMINATED`，Intent 版本不变 | AC-11 |
| 建 Intent B，`reason=ORDER_CANCELLED` | `TERMINATED`、`CANCELLED`。旧原因语义不变（Cancel 回归） | AC-07 |
| 用另一个 clientId 终止 | 400 `BUSINESS_CHECK_FAILED`（EXP-A 裁决①） | — |
| `reason=EXPIRED`（非法值） | 422（EXP-A 裁决②） | — |

PENDING、REVERSAL_REQUIRED 与 ALREADY_PAID 这三个决定需要可控渠道，沿用阶段 4 的 `TerminateIntentTest` 与阶段 5 的 S2。

## 4. Client API 全量回归与 AC-12（`client_api_flow.py`）

```
cd /Users/kenny/Desktop/Workspace/Code-MoR-expiry/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e
E2E_TARGET=test python3 -I client_api_flow.py
```

| 场景 | 期望 |
|---|---|
| 入驻 Client、自助凭证、端点（订阅 4 种事件） | 端点 201，签名密钥以 `whsec_` 开头 |
| 查询、拒绝（无凭证、错凭证、缺 Scope、缺 Idempotency-Key）、Gateway 请求幂等全套 | 与 10-18 以来的历次运行一致，全部 PASS |
| `order.cancelled`（含重复取消） | PASS。Cancel 现在走真实 PAY-I12：订单没有投影也没有接受证据时走本地取消，不调用 Payment |
| **AC-12 `flow_expired`** | 建结账（期限 5 分钟加 60 秒）→ 回拨 → 90 秒内 GET 返回 `EXPIRED`，`checkout.url` 为空，会话 `EXPIRED`，`paymentStatus=NOT_STARTED` → 库内为 `EXPIRY_REACHED` → `order.expired` 只送达 1 次，签名正确，Platform 冻结 Envelope 一致，`sourceVersion` 等于对象 `version` → `data.object` 与 GET 一致，`expiredAt` 等于 `expiresAt` → 过期后取消返回 409 `ORDER_NOT_CANCELLABLE`，不改写订单，不产生 `order.cancelled` |
| `order.paid` 夹具 / `refund.created` / 退款查询 | PASS。夹具写入投影，并向 `acquiring.payment.hash` 发 PaymentSucceeded v1，与历次运行相同 |
| 接收端总数 | 4（paid、cancelled、expired、refund.created） |

**重跑间隔：** 每次运行留下 3 笔 OPEN 订单（query、两笔幂等订单），默认期限 30 分钟。它们所属 Client 的端点现在订阅了 `order.expired`。约 30 分钟后，这些订单会被扫描收尾，并向同一接收端投递。接收端按文件时间计数，不区分 Client。因此上一次运行结束后的 35 分钟内再跑，“接收端总数 = 4”可能误报 FAIL。处理方法有两种：间隔 35 分钟以上再跑；或者核对多出的事件都是上一个 Client 的 `order.expired`，然后按观察记录。

## 5. 3DS 回归（本次替换 payment，不得影响 3DS）

本需求对 Payment 的改动集中在终止、按来源查询和 `GetIntentResponse` 组装（release.md 第 2.4 节）。PAY、查单、刷新、回调与 PaymentSucceeded 的代码没有变化，相对现网 JAR 的差异中不含这些类。回归只验证新镜像上这些链路仍然连通，不重做 UAT 全链路。

| 项 | 做法（对照 3DS release.md 第 11.1 节） | 期望 |
|---|---|---|
| 3DS AC-02 | `python3 -I requirements/20261022/payment-3DS全链路/release/acceptance/accept_3ds.py ac02`，结果写入该目录的 `accept-3ds.json`。阶段 7 改为写入本目录，见第 7 节 | `DEMO_NI` 600.00 USD → PAY_RISK `THREE_DS_REQUIRED`（R-11）→ PAY FAILED `THREE_DS_CHANNEL_UNAVAILABLE`，`t_channel_interaction` 新增 0 行。3DS 曾出现首笔 Risk 冷启动超时（S7-03），本次 Risk 不重启，不受影响 |
| BIN 冻结 | 同一笔支付的卡工具快照 | 卡属性与 BIN 生效版本照常冻结（BIN AC-02） |
| Payment 消费与执行器 | 替换 payment 后核对 | `payment.channel-connector.p0—p3` 各 1 个消费者；XXL `payment-service` 心跳在 30 秒内刷新；任务 9 `compensationEventScan` handle 200 |
| PaymentSucceeded 订阅方 | 第 4 节的 `order.paid` 夹具只发往 `acquiring.payment.hash` | `acquiring.payment.p0—p3` 各 1 个消费者，DLQ 为 0 |
| 健康 | `accept_regression.py health <发布开始 UTC>`，只读 | acquiring、payment healthy，重启 0，日志 ` ERROR ` 为 0（可解释的逐条记录） |
| 不做 | PayerMax UAT 3DS 全链路（3DS AC-12）。PAYERMAX 的 MID 已 SUSPENDED，能力已 DISABLED，还需要人工 ACS | 沿用 3DS 阶段 7 的结论（18:41Z 通过）。如要做，须先把 MID 与能力恢复为 ACTIVE / ENABLED，再按 3DS release.md S16、S17 执行，结束后复原 |

## 6. AC 对照

| AC | mor-test | 证据或沿用 |
|---|---|---|
| AC-01 | 第 2 节存量 16 笔与第 4 节 `flow_expired`（无 Intent 本地收尾） | — |
| AC-02 | 第 3 节 P-1 只验证 Payment 侧（终止 → Intent EXPIRED）。acquiring 侧有投影的收尾 | 阶段 5 S2 |
| AC-03、AC-04、AC-05、AC-10 | 不做（需要可控渠道或 24 小时拨时） | 阶段 4 `ExpireOrderTest`、阶段 5 S2 / S3 |
| AC-06 | 第 4 节：签名送达、Event ID、冻结载荷、只送达 1 次 | 补齐阶段 5 缺的签名送达 |
| AC-07 | 第 4 节 Cancel 本地分支、第 3 节 `ORDER_CANCELLED` | 202 / 409 分支用阶段 4 `CancelOrderTest` |
| AC-08 | 第 4 节过期后取消返回 409。消费者收银台返回 `CHECKOUT_EXPIRED` 与提交被拒 | 阶段 5 S1（mor-test 不经 Customer API） |
| AC-09 | 第 2 节第二轮 `scanned 0`。单实例部署，多实例并发不做 | 阶段 4 自动测试 |
| AC-11 | 第 3 节 P-1 | 阶段 5 S2 |
| AC-12 | 第 4 节 | — |

## 7. 日志与泄漏检索

替换后，检索 acquiring、payment 自发布开始以来的 `docker logs`：

- Checkout Public Token 原值与 `chk_` 形串、`checkout.url` 主机、卡号与 Luhn 卡号形串：0 命中。可改写 3DS 的 `accept_leak_scan.py`，只统计计数。
- 过期相关 WARN 只含 orderId、intentId 与决定。本次预计 0 条，因为不会走到 PENDING 超 24 小时或 REVERSAL_REQUIRED 分支。

结果写入本目录 `acceptance/`（阶段 7 新建）。

## 8. 合成数据与残留

| 数据 | 处理 |
|---|---|
| 存量 16 笔订单与会话变为 EXPIRED，Platform 新增 16 行 `order.expired` 事件 | 保留。这是生产语义下的正确终态，没有“取消过期”的操作 |
| `flow_expired` 与其他场景新建的 Client、凭证、端点和订单，回拨过 `expires_at` 的 1 笔订单 | 保留，与历次验收一致。运行记录写入 `release/acceptance/*.json`，不含凭证与签名密钥 |
| P-1 新建的 Intent A、B（EXPIRED / CANCELLED） | 保留。sourceId 为合成值，不关联订单 |
| 3DS AC-02 的 Intent、Attempt、Action、Token、Risk 快照 | 保留，与 3DS 阶段 7 相同 |
| XXL 任务 `orderExpiryScan` | 保留，属于正式配置 |
