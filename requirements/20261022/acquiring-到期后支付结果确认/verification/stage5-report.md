# 阶段 5 报告：acquiring 订单到期后的支付结果确认 · 本机 e2e 验收

> 执行日期 2026-10-23（UTC），08:27:51—08:29:15。执行代理 EXP-S5。只写验收脚本与结果；未改生产代码或测试代码，未跑 Maven，未提交，未碰 mor-test，未启停服务。
> 脚本 [`stage5_result_after_expiry.py`](./stage5_result_after_expiry.py)（`python3 -I stage5_result_after_expiry.py [s1|s2|s3|s4|s5|all]`，`-h` 看用法），结果 [`stage5-result.json`](./stage5-result.json)。

## 1. 结论

- **S1 挑战中到期后成功、S2 挑战中到期后失败、S3 到期后限频、S4 未提交支付到期、S5 日志泄漏检索全部通过**，每个场景运行一次。
- 对应 AC：AC-01—AC-06 全部通过，B4 通过。AC-03 的“5 秒内只查单一次”已在合成 PayerMax 流水和 payment 请求耗时中观察到。明细见第 4 节。
- 订单过期收尾阶段 5 的绕行不再需要。那次的绕行是以 acquiring-service 身份直连 Payment 刷新。本次 S1、S2 只用消费者查询提交结果接口，就让支付收敛到成功或失败。
- 没有发现产品缺陷。有两项预期的环境现象和两项现有行为观察，见第 6 节。

## 2. 环境与 JAR 指纹

| 项 | 值 |
|---|---|
| acquiring JAR（运行中，pid 13533，08:24:14Z 启动） | `paytech-acquiring-app-exec.jar` `dca404ec4d301876`。由主会话从 main `a816bd14` 构建，即 `98210e0b` 加本需求未提交的改动 |
| acquiring 旧 JAR 备份 | `.pre-f1` `de02f36bd0b0c128`，即订单过期收尾阶段 5 的 JAR |
| payment JAR（运行中，pid 50002，10-22 19:47:22Z 启动） | `paytech-payment-app-exec.jar` `621ff256dfc74c5c`，未变 |
| acquiring XXL 执行器 | 127.0.0.1:19992，Handler `orderExpiryScan`，日志目录 `local-environment/e2e/runtime/expiry/xxl-acquiring` |
| 合成 PayerMax | 3DS 扩展版 `payment-3DS全链路/verification/harness/PayerMax3dsMock.java`（pid 14101，08:24:40Z 启动），控制接口 `/__control/query` 可用；流水 `runtime/payermax/payermax-journal.jsonl` |
| Client | `237161687952666625`（CLIENT，owner_status ACTIVE） |
| 风控发布 | 沿用订单过期收尾阶段 5 的发布：CHECKOUT_PREPARE 放行，PAYMENT_ATTEMPT_RISK 要求 333 USD 做 3DS。本次未改动 |

指纹取 `shasum -a 256` 前 16 位。

## 3. 场景

### S1 挑战中到期，挑战成功（AC-01、AC-05、AC-06）

步骤：建结账 333.00 USD → 保存消费者资料、确认合同、提交卡支付，进入 3DS 挑战 → 回拨 `expires_at` 到 now − 2 分钟 → 消费者查询 → 查看收银台、保存资料、再提交、Client GET 订单 → 合成 PayerMax 查单结果设为 SUCCESS → 6 秒后消费者再查询 → 等订单 PAID → 触发扫描 → 再查询。

| 检查 | 证据（订单 `ord_3L5VmtoflSHBWF4HfQIXjWPwa6Q4G6`，Intent `239303824416735232`，Attempt `239303832952143872`） | 结果 |
|---|---|---|
| 进入挑战 | 提交 `REQUIRES_ACTION` / `THREE_DS_CHALLENGE`；Intent OPEN，有活动尝试 | 通过 |
| 到期后查询（改动点） | **200 `REQUIRES_ACTION`**，带 `nextAction`（`THREE_DS_CHALLENGE`）。改前是 410 | 通过 |
| 到期后写操作与查看收银台（AC-05） | 查看收银台、保存资料、提交支付都返回 410 `CHECKOUT_EXPIRED` | 通过 |
| Client GET（AC-06） | 200，`checkout.url` 为空 | 通过 |
| 挑战成功后查询 | 第一次重查（距上次 6 秒）就返回 200 `SUCCEEDED` | 通过 |
| 订单 PAID | 订单 OPEN v4 → PAID v5，`paid_at` 08:28:14.674Z；会话 COMPLETED；投影 SUCCEEDED；Intent SUCCEEDED；Attempt SUCCEEDED；Platform `order.paid` 1 行，`source_version=5` | 通过 |
| 扫描后 | handleCode 200，`scanned 1, expired 1`。被扫到的是 S4 的未支付订单，S1 订单不在其中；S1 订单仍 PAID，版本和 `update_time` 不变，无 `order.expired` | 通过 |
| PAID 后再查询 | 200 `SUCCEEDED` | 通过 |

### S2 挑战中到期，挑战失败，扫描后 EXPIRED（AC-02、B4）

步骤：同 S1 进入挑战 → 回拨 → 消费者查询 → 查单结果设为 FAILED → 6 秒后消费者再查询 → 触发扫描 → 6 秒后再查询。全程只用消费者接口确认结果，没有直连 Payment。

| 检查 | 证据（订单 `ord_3L5VqzKg1xn47QoMdArnu3tCEOx4A6`，Intent `239303961650167808`，Attempt `239303962379976704`） | 结果 |
|---|---|---|
| 到期后查询 | 200 `REQUIRES_ACTION` | 通过 |
| 挑战失败后查询 | 第一次重查就返回 200 `FAILED`，`failure.code=PAYMENT_FAILED`；Attempt FAILED，Intent OPEN、无活动尝试，订单仍 OPEN | 通过 |
| 扫描（AC-02） | `scanned 1, expired 1`；订单 EXPIRED v6、`EXPIRY_REACHED`；会话 EXPIRED；投影和 Intent 都是 EXPIRED；Client GET `orderStatus=EXPIRED`、`paymentStatus=EXPIRED`、无 `checkout.url`；Platform `order.expired` 1 行，`source_version=6` | 通过 |
| 订单 EXPIRED 后查询（B4） | 200 `FAILED` / `PAYMENT_FAILED` | 通过 |

### S3 到期后挑战仍处理中，5 秒内两次查询（AC-03）

步骤：同 S1 进入挑战 → 回拨 → 等 6 秒，越过 `pay_checkout` 轮询留下的限频窗口 → 连续两次查询（间隔 198 ms）→ 等 6 秒再查一次。查单次数取合成 PayerMax 流水中该 Attempt 的 `orderQuery` 条数。

| 检查 | 证据（订单 `ord_3L5VtfpT3lEH8Wg8rBw8d2PRLnt5yC`，Attempt `239304051177586688`） | 结果 |
|---|---|---|
| 两次查询 | 都是 200 `REQUIRES_ACTION` | 通过 |
| 窗口内查单 | 两次查询前流水 2 条，两次查询后 3 条，只增加 1 条。payment 请求日志中，第一次刷新耗时 146 ms，含渠道查单；第二次刷新 6 ms，被限频，没有查单 | 通过 |
| 超过 5 秒后 | 第三次查询 200 `REQUIRES_ACTION`，流水增加 1 条，说明限频窗口过后会再查单 | 通过 |
| 订单 | 仍 OPEN，Attempt PROCESSING | 通过（留作残留，见第 7 节） |

### S4 未提交支付的订单到期（AC-04）

| 检查 | 证据（订单 `ord_3L5VmDrv3RzABUTsf22BhBHsO2BDy7`） | 结果 |
|---|---|---|
| 到期前查询（回归） | 404 `PAYMENT_SUBMISSION_NOT_FOUND` | 通过 |
| 到期后查询 | 提交 ID `1` 和一个大数 ID 都返回 410 `CHECKOUT_EXPIRED` | 通过 |
| 无支付 | 无投影，Payment 无 Intent | 通过 |

D1（有支付记录，但 Payment 未找到该提交，到期后返回 410）本机无法制造：本机提交支付总会创建 Attempt。这一项以 `test_expiredSubmissionNotFound` 为证。

### S5 日志泄漏检索

检索窗口从 08:27:51.63Z（第一个场景开始）到结束。范围是 `local-environment/logs/acquiring.log` 136 行、`payment.log` 22 行。检索对象是窗口内的 4 个 Checkout Public Token，原值从库中读取，只在内存中使用。

| 检索项 | acquiring | payment |
|---|---|---|
| 测试卡号（3 个） | 0 | 0 |
| `cvv` 键 | 0 | 0 |
| `acsSecret` | 0 | 0 |
| Checkout Public Token 原值 / `chk_` 形 | 0 / 0 | 0 / 0 |
| 3DS / ACS 跳转地址 | 0 | 0 |
| WARN / ERROR | 4 / 0 | 0 / 0 |

acquiring 的 4 条 WARN 有两类。一类是启动期常见的 `LoadBalancerCacheManager not available`，另一类是 `Returned message but no callback available`。后者是 `OrderPaymentSucceeded` 发到 `acquiring.events` 时无路由被退回，见第 6 节 E-1。

## 4. 与验收标准的对应

| AC | 结果 | 依据 |
|---|---|---|
| AC-01 | 通过 | S1：到期后查询 200 `REQUIRES_ACTION`，触发刷新；挑战成功后返回 `SUCCEEDED`，订单 PAID，扫描不改写 |
| AC-02 | 通过 | S2：返回 `FAILED`，下一轮扫描后 EXPIRED，此后再查仍 `FAILED` |
| AC-03 | 通过 | S3：两次都返回处理中；5 秒内只查单一次，超过 5 秒再查一次 |
| AC-04 | 通过 | S4 |
| AC-05 | 通过 | S1：查看收银台、保存资料、提交支付都返回 410 `CHECKOUT_EXPIRED` |
| AC-06 | 通过 | S1（OPEN 已到期）、S2（EXPIRED）：Client GET 无 `checkout.url` |
| B3（Payment 不可用返回处理中） | 本机未做 | 需要停 payment，不在授权范围内；以 `test_expiredPaymentUnavailable` 为证 |
| B4 | 通过 | S2 |
| D1 | 本机无法制造 | 以 `test_expiredSubmissionNotFound` 为证 |

## 5. 证据边界

- **真实进程：** acquiring、payment、card、channel-connector、risk-decision、counterparty、platform、MySQL、Redis、RabbitMQ。
- **合成：**
  - PayerMax 是本机合成服务，3DS 结果由脚本经控制接口设定查单结果来模拟。持卡人没有真正跳转 ACS 页面。
  - acquiring 的 commercial、tax、document、consumer protection 端口在本机使用应用内固定 Mock。
- **到期：** 用回拨本次订单 `expires_at` 模拟，只按 order_no 改本次创建的 4 个订单。这是本次唯一的直接写库。
- **XXL：** 本机没有 Admin。脚本按 Admin 协议 `POST /run` 触发扫描，并从执行器日志读取汇总与 handleCode。
- **入口：** Client API 和 Customer API 都是私网直连 acquiring，带 Gateway 可信 Header，不经 Gateway / Nginx。
- **Webhook：** 只核对到 Platform `t_webhook_event` 受理，签名送达不在本需求范围内。
- **限频观察：** 合成 PayerMax 流水按 Attempt 计数，payment 请求日志按请求耗时区分。本机 payment XXL 未启用，没有后台退避查单，所以流水增量全部来自消费者查询触发的刷新。

## 6. 发现

| 编号 | 类型 | 描述 | 处理 |
|---|---|---|---|
| E-1 | 环境，预期 | 新 acquiring 把 `OrderPaymentSucceeded` 发到 `acquiring.events`，本机没有 billing 绑定，消息无路由被退回。S1 订单的补偿事件 `ORDER_PAYMENT_SUCCEEDED` 为 ERROR，`execute_count=1`，acquiring 日志有 `Returned message but no callback available` | 按主会话说明记录，不属本需求缺陷 |
| E-2 | 环境 | 首轮扫描扫到并过期了 S4 订单。S4 订单已到期且没有 Intent，这是预期结果；同一轮中 S1 的 PAID 订单没有被选中 | 记录 |
| O-1 | 现有行为 | 挑战进行中（订单 OPEN）时，Client GET 的 `paymentStatus` 为 `NOT_STARTED`，投影 `intent_status=REQUIRES_PAYMENT`。投影只在成功和过期时更新，与订单过期收尾阶段 5 的观察相同 | 不属本需求 |
| O-2 | 现有行为 | `REQUIRES_ACTION` 响应的 `pollAfterMs` 为空，只有 `PROCESSING` 带 2000。与到期前相同 | 不属本需求 |

## 7. 环境残留

- acquiring 库中有本次 4 个订单。S1 为 PAID；S2 为 EXPIRED；S4 为 EXPIRED，由 S1 的扫描写入。**S3 为 OPEN**，`expires_at` 已回拨，Attempt PROCESSING，合成 PayerMax 查单结果为 PENDING。下一次扫描会因为有未决动作把它判为 PENDING。若要收尾，可以把它的查单结果设为 FAILED，再经消费者接口查询一次，然后扫描。
- S1 订单的 `ORDER_PAYMENT_SUCCEEDED` 补偿事件处于 ERROR，会按退避重试，原因见 E-1。
- Platform 新增 `order.paid` 1 行、`order.expired` 2 行（S2、S4）。
- 执行器日志在 `runtime/expiry/xxl-acquiring/2026-10-23/`。
- 服务、JAR 和合成 PayerMax 都保持原样运行。
