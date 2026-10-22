# 阶段 5 报告：acquiring 订单与会话过期收尾 · 本机 e2e 验收

> 执行日期 2026-10-22（UTC），19:50—19:58。执行代理 EXP-S5。只写验收脚本与结果；未改生产代码或测试代码，未跑 Maven，未提交，未碰 mor-test，未启停服务。
> 脚本 [`stage5_expiry.py`](./stage5_expiry.py)（`python3 -I stage5_expiry.py [s1|s2|s3|s4|all]`，`-h` 看用法），结果 [`stage5-expiry.json`](./stage5-expiry.json)。

## 1. 结论

- **S1 无 Intent 本地过期、S2 3DS 挑战中到期、S3 已支付订单、S4 日志泄漏检索全部通过。**
- 对应 AC：AC-01、AC-02、AC-03、AC-08、AC-11 通过；AC-04、AC-06、AC-09 部分通过；AC-05、AC-07、AC-10 本阶段未覆盖；AC-12 留到 mor-test。明细见第 4 节。
- 没有发现产品缺陷。发现两项环境问题和一项需要记录的行为，见第 6 节。

## 2. 环境与 JAR 指纹

| 项 | 值 |
|---|---|
| acquiring JAR（运行中，pid 50221，19:47:31Z 启动） | `paytech-acquiring-app-exec.jar` `de02f36bd0b0c128` |
| payment JAR（运行中，pid 50002，19:47:22Z 启动） | `paytech-payment-app-exec.jar` `621ff256dfc74c5c` |
| 旧 JAR 备份 | acquiring `.pre-expiry` `a50d6f7feea7990e`；payment `.pre-expiry` `694d62e992d353be` |
| acquiring XXL 执行器 | 127.0.0.1:19992，Handler `orderExpiryScan`，日志目录 `local-environment/e2e/runtime/expiry/xxl-acquiring` |
| 合成 PayerMax | 3DS 扩展版 `payment-3DS全链路/verification/harness/PayerMax3dsMock.java`（pid 60362，19:53:21Z 由主会话替换，同一运行目录与合成密钥）。S1、S2、S3 的保留结果都在替换之后运行，见第 6 节 |
| Client | `237161687952666625`（`mor_platform_v2e2e.t_access_tenant`，CLIENT，owner_status ACTIVE） |
| 风控发布 | CHECKOUT_PREPARE `239113350301106176`（本次经运营入口 Nginx → Gateway → ops-bff → risk-decision 补建“开始 → 放行”发布，主会话已接受）；PAYMENT_ATTEMPT_RISK `239045324600000512`（3DS 阶段 5 发布：333 USD 或 GB 发卡要求 3DS） |

指纹取 `shasum -a 256` 前 16 位。两个进程启动时间与 JAR 修改时间（23:47 本地 = 19:47Z）一致。

## 3. 场景

### S1 无 Intent 本地过期

步骤：建结账 10.00 USD，不进入支付 → 回拨 `expires_at` 到 now − 2 分钟 → 触发扫描 → 核对 → Cancel、消费者 GET、提交支付 → 再触发扫描。

| 检查 | 证据（订单 `ord_3L42TwGQur5Anf86YZbF4h81zvR3bk`） | 结果 |
|---|---|---|
| 扫描 | handleCode 200；`scanned 1, expired 1, pending 0 … failed 0` | 通过 |
| 订单 / 会话 | 订单 OPEN v1 → EXPIRED v3、`status_reason_code=EXPIRY_REACHED`、`termination_requested_at` 已写、`cancelled_at` 空；会话 OPEN → EXPIRED | 通过 |
| 无 Intent | 无支付投影，Payment 无 `COMMERCE_ORDER` 来源 Intent | 通过 |
| GET 订单 | `orderStatus=EXPIRED`、`checkout.status=EXPIRED`、无 `checkout.url`、`paymentStatus=NOT_STARTED` | 通过 |
| `order.expired` 受理 | acquiring 补偿任务已完成（行已删除）；Platform `t_webhook_event` 1 行，`event_type=order.expired`、`source_event_key=evt_<orderNo>_expired`、`source_system=acquiring-service`、`source_version=3` | 通过 |
| 载荷 | 对象键正好 7 个；`reasonCode=EXPIRY_REACHED`；`expiredAt` 等于库中 `expires_at`；`version=3` 等于订单版本；Envelope 键 `eventId/eventType/schemaVersion/createdAt/data` | 通过 |
| Cancel | 409 `ORDER_NOT_CANCELLABLE` | 通过 |
| 消费者 GET 收银台 / 提交支付 | 都是 410 `CHECKOUT_EXPIRED` | 通过 |
| 再次扫描 | `scanned 0`；订单版本与 `update_time` 不变；Platform 仍 1 行、无新任务；无 `order.cancelled` | 通过 |

第一次运行（`ord_3L42JY2E5TnDIFrrB3fQNs5WNKZ5s8`）结果相同。那次脚本还没有记录 handleCode，JSON 中保留的是第二次运行。

### S2 3DS 挑战中到期保持 OPEN，挑战失败后过期

步骤：建结账 333.00 USD → 经 Customer API 保存消费者资料、确认合同、提交卡支付 → 回拨到期 → 扫描 → 再提交 → 合成 PayerMax 把查单结果设为 FAILED → 刷新使 Attempt 收敛 → 扫描 → 重复终止与扫描。

| 检查 | 证据（订单 `ord_3L42Ujs6lq1hUUc78W7ykTJs5VzGji`，Intent `239114908187262976`，Attempt `239114908992569344`） | 结果 |
|---|---|---|
| 进入挑战 | 提交状态 `REQUIRES_ACTION`，交互类型 `THREE_DS_CHALLENGE`；Attempt PROCESSING；Intent OPEN 且有活动尝试 | 通过 |
| 到期后首轮扫描（B1、AC-03） | `scanned 1, expired 0, pending 1`；订单保持 OPEN（v4 → v5，只写了 `termination_requested_at`）；会话 OPEN；Intent 仍 OPEN v2；无补偿任务，Platform 无 `order.expired` | 通过 |
| 到期后再提交 | 410 `CHECKOUT_EXPIRED`；消费者轮询提交结果也是 410 | 通过 |
| 挑战失败收敛 | 以 acquiring-service 身份调用 Payment 刷新，一次返回 FAILED `PAYMENT_FAILED`；Attempt FAILED，Intent OPEN、无活动尝试 | 通过 |
| 第二轮扫描（AC-02） | `expired 1`；Intent OPEN → **EXPIRED**（不是 CANCELLED）；订单 EXPIRED v6、`EXPIRY_REACHED`；会话 EXPIRED；投影 `intent_status=EXPIRED` | 通过 |
| GET 订单 | `orderStatus=EXPIRED`、`paymentStatus=EXPIRED`、无 `checkout.url` | 通过 |
| `order.expired` | Platform 1 行，`source_version=6`；载荷字段与库一致（同 S1 的检查） | 通过 |
| 重复终止（AC-11） | 直接调用 `POST /intents/{id}/terminate` 带 `ORDER_EXPIRED`：200 `ALREADY_TERMINATED`、`status=EXPIRED`；Intent 版本不变 | 通过 |
| 第三轮扫描 | `scanned 0`；订单版本不变，Platform 仍 1 行 | 通过 |

### S3 已支付订单不受影响

步骤：普通金额 42.90 USD 真实支付（复用 `webhook_flow.paid_order`，两次运行都是第一次就成功）→ 订单 PAID → 回拨 `expires_at` → 扫描。

| 检查 | 证据（订单 `ord_3L42YeikwE0RHdBz3cO1VMAXz759xv`） | 结果 |
|---|---|---|
| 支付 | 提交 SUCCEEDED；PaymentSucceeded 被 acquiring 消费（APPLIED）；订单 PAID v5，会话 COMPLETED | 通过 |
| 扫描 | handleCode 200；`scanned 0`，订单没有被选中 | 通过 |
| 不受影响 | 订单仍 PAID，版本与 `update_time` 不变；`termination_requested_at` 为空，说明没有走到终止调用；Intent SUCCEEDED 且版本不变；无 `order.expired` 任务或 Platform 行 | 通过 |

第一次运行（`ord_3L42M7hAlBTiG3MlqKlqCr8oLID25V`）结果相同。

### S4 日志泄漏检索

检索窗口从 19:55:46.97Z（第一个场景开始）到结束。范围是 `local-environment/logs/acquiring.log` 156 行、`payment.log` 22 行。检索对象是窗口内创建的 5 个会话的 Checkout Public Token，原值从库中读取，只在内存中使用。

| 检索项 | acquiring | payment |
|---|---|---|
| 测试卡号（3 个） | 0 | 0 |
| `cvv` 键 | 0 | 0 |
| `acsSecret` | 0 | 0 |
| Checkout Public Token 原值 / `chk_` 形 | 0 / 0 | 0 / 0 |
| 3DS / ACS 跳转地址 | 0 | 0 |
| 过期相关 WARN | 0 条 | 0 条 |

过期相关 WARN 是 0 条，原因是本次没有走到会记 WARN 的分支。这两个分支是 PENDING 超过 24 小时与 REVERSAL_REQUIRED。所以“WARN 只含 orderId / intentId”在本机无法观察，以阶段 4 自动测试为证。扫描汇总写在 XXL 执行器日志里，只有条数。

## 4. 与验收标准的对应

| AC | 结果 | 依据 |
|---|---|---|
| AC-01 | 通过 | S1 |
| AC-02 | 通过 | S2：TERMINATED，Intent 按 Q2 为 EXPIRED，订单、会话、投影 EXPIRED |
| AC-03 | 通过 | S2：PENDING 时 OPEN、`termination_requested_at` 已写、提交被拒（410 `CHECKOUT_EXPIRED`，即 EXP-C 裁决②）；收敛后下一轮完成 |
| AC-04 | 部分 | S3 只证明已 PAID 订单不被扫描改写；“扫描时 Payment 返回 ALREADY_PAID → 等 PaymentSucceeded 置 PAID”需要在终止请求与成功之间制造竞态，本机未做，以 `ExpireOrderTest` 为证 |
| AC-05 | 未覆盖 | REVERSAL_REQUIRED 要求已授权未捕获；本机支付是 AUTOMATIC 捕获，无法制造，以自动测试为证 |
| AC-06 | 部分 | 受理、Event ID、冻结载荷、只一个事件已核对；签名送达留到 mor-test（AC-12） |
| AC-07 | 未覆盖 | 本阶段只经过期链路使用真实 PAY-I12（TERMINATED、ALREADY_TERMINATED）；Cancel 的 202 / 409 / 本地取消未在本机跑，以 `CancelOrderTest` 为证 |
| AC-08 | 通过 | S1：Cancel 409、收银台 `CHECKOUT_EXPIRED`、提交被拒 |
| AC-09 | 部分 | 重复扫描只写一次、只一个事件（S1、S2）；多实例并发本机只有一个实例，以自动测试为证 |
| AC-10 | 未覆盖 | 需要 24 小时拨时，以 `test_pendingBeyondThresholdWarns` 为证 |
| AC-11 | 通过 | S2：原因 `ORDER_EXPIRED` 写 EXPIRED；重复终止 ALREADY_TERMINATED、不改写 |
| AC-12 | 留到 mor-test | — |

## 5. 证据边界

- **真实进程：** acquiring、payment、card、channel-connector、risk-decision、counterparty、platform、MySQL、Redis、RabbitMQ。
- **合成：**
  - PayerMax 是本机合成服务，3DS 持卡人验证结果由脚本经控制接口设定。
  - acquiring 的 commercial、tax、document、consumer protection 端口在本机使用应用内固定 Mock（`CustomerCheckoutIntegrationConfiguration`），client-master 与 commercial 没有运行。
- **到期：** 用回拨本次订单 `expires_at` 模拟，只按 order_no 改本次创建的 5 个订单。这是本次唯一的直接写库，S3 的 PAID 订单也回拨了。
- **XXL：** 本机没有 Admin。脚本按 Admin 协议 `POST /run`（分片 0/1）触发，并从执行器日志读取汇总与 handleCode。回调 Admin 失败的日志属于占位地址，可以忽略。
- **Client 入口：** 私网直连 acquiring Client API，带 Gateway 可信 Header，不经 Gateway / Nginx。Customer API 同样私网直连。
- **Webhook：** 本机没有接收端和端点订阅。Platform 受理后 `t_webhook_delivery` 为 0 行，签名送达未验证。
- **S2 收敛方式：** 订单到期后，acquiring 的消费者轮询会先抛 `CHECKOUT_EXPIRED`，不再调用 Payment 刷新。payment 本机 XXL 未启用，退避查单不会执行。因此脚本以 acquiring-service 身份直接调用 Payment 刷新接口，这是 acquiring 轮询调用的同一接口。S2 的重复终止也是脚本直接调用 Payment 终止接口。

## 6. 发现的问题

| 编号 | 类型 | 描述 | 处理 |
|---|---|---|---|
| E-1 | 环境 | risk-decision 本机没有 CHECKOUT_PREPARE 生效发布，建结账 503 `DEPENDENCY_UNAVAILABLE`（risk 侧 `RISK_DECISION_NOT_CONFIGURED`） | 已用现有 `local-e2e/risk_setup.py` 经运营入口补建放行发布 `239113350301106176`，PAYMENT_ATTEMPT_RISK 未动。这是真实接口写入，主会话已接受 |
| E-2 | 环境 | 19:52Z 探测时 18190 运行原版合成 PayerMax（pid 64998），`/__control/query` 404，S2 无法执行 | 已报主会话。主会话在 19:53:21Z 换成 3DS 扩展版（pid 60362），S2 在替换后执行。验完保持该版本运行，由主会话处理 |
| P-1 | 行为发现（按主会话裁决记录，不改代码） | 订单到期后，消费者 GET 收银台与轮询提交结果都先返回 410 `CHECKOUT_EXPIRED`，不再调用 Payment 刷新。持卡人完成 3DS 后从银行跳回，只能看到“已过期”。结果确认只能靠 Payment 退避查单（XXL `compensationEventScan`）或渠道通知。本机 payment XXL 未启用，因此 S2 以 acquiring-service 身份直连 Payment 刷新接口收敛 | 记录为已知行为。mor-test 须启用 payment 执行器并登记该任务，建议列入阶段 6 核对项 |

附带观察（现有行为，不属本需求）：支付处理中时投影的 `intent_status` 为 `REQUIRES_PAYMENT`，`last_attempt_*` 为空。投影只在成功和本需求的过期时更新，与 technique 2.1 R6 的记录一致。

## 7. 环境残留

- acquiring 库中有本次 5 个订单：EXPIRED 3 个（S1 两个、S2 一个），PAID 2 个（S3，`expires_at` 已回拨）。Platform 有 `order.expired` 3 行、`order.paid` 2 行。acquiring 无残留补偿任务。
- risk-decision 新增 CHECKOUT_PREPARE 放行发布 `239113350301106176`（ACTIVE）。
- Payment 中有 S2 的 EXPIRED Intent 和 FAILED Attempt，以及 S3 的成功支付。
- 执行器日志在 `runtime/expiry/xxl-acquiring/2026-10-22/`。
- 服务与 JAR 保持原样运行。合成 PayerMax 已由主会话在验收结束后换回原版 `PayerMaxMock.java`（经 `run-service.sh start payermax-mock`，同一运行目录与合成密钥）。
