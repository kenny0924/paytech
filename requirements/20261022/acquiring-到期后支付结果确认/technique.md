# acquiring：订单到期后的支付结果确认 · 技术方案

> 状态：第 1 节为**阶段 1 现状证据**（2026-10-23，main `d3d39e40`，只读核对）。第 2 节起在阶段 2、3 填写。

## 1. 现状证据

| 环节 | 现状 | 证据 |
|---|---|---|
| 查询支付提交结果 | 先 `order.checkExpiry(now)`，到期即抛 `CHECKOUT_EXPIRED`，之后才调用 Payment 刷新；响应前再查一次到期 | `paytech/acquiring/paytech-acquiring-core/src/main/java/com/paytech/acquiring/service/processor/customer/GetCustomerPaymentProcessor.java:74-81,97-110,113-117` |
| 查看收银台 | 读取后 `order.checkExpiry(now)` 抛 `CHECKOUT_EXPIRED`；`allowedActions` 由 `CheckoutQueryContext.isOpen()`（未请求终止且 CREATED / OPEN）决定 | `GetCustomerCheckoutProcessor.java:90,113,178,197`；`domain/customer/CheckoutQueryContext.java:57-60,77-89` |
| 到期判断 | `expires_at` 不晚于当前时刻或订单已 EXPIRED 即视为到期 | `core/dal/dataobject/order/CommerceOrder.java:106-110` |
| Payment 刷新 | 3DS 交付的刷新接口，处理中时 Redis `SET NX` 5 秒限频后查单推进 | `paytech/payment/.../service/processor/RefreshSubmissionProcessor.java:23,32,35,61` |
| 3DS 交互期限 | 30 分钟 | `paytech/payment/.../core/action/PayActionActor.java:63,263` |
| 设计规则 | `expiresAt` 前终态页面可只读显示统一结果；到期后 `checkoutUrl` 为 null，任何状态不能再发起支付 | `docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md:523,622` |
| 过期收尾 | 有未决动作时 PENDING 不写 EXPIRED，晚到成功照常推进 | [订单过期收尾](../acquiring-订单过期收尾/technique.md) 第 3.2 节 |
| 阶段 5 发现 | 到期后消费者轮询不再触发刷新，本机验收改以 acquiring-service 身份直连 Payment 刷新 | [订单过期收尾阶段 5 报告](../acquiring-订单过期收尾/verification/stage5-report.md) |

## 2. 差异与影响（阶段 2）

> 2026-10-23，main 只读核对；对应 [需求第 2 版](./requirement.md)。

### 2.1 补充现状

| 环节 | 现状 | 证据 |
|---|---|---|
| 支付记录写入时机 | `OrderPaymentProjection` 在 Intent 绑定时写入（`bindPayment`），早于调用 Payment 发起支付；Payment 不可用或只申请了工具时，可能**有支付记录但没有支付尝试** | `domainservice/service/customer/impl/CheckoutSubmissionServiceImpl.java:50-60` |
| Payment 按提交查询 | 只校验 Intent 归属与提交对应的尝试，**不看 Intent 状态**；Intent 已终止（EXPIRED / CANCELLED）时仍返回尝试的最终状态（FAILED 及失败代码） | `paytech/payment/.../service/processor/GetSubmissionProcessor.java` |
| Payment 刷新 | 只对 PROCESSING 尝试限频确认；终态直接返回当前事实 | `RefreshSubmissionProcessor.java:41-53` |
| 现有测试 | 结果查询没有到期场景的用例；`ExpireOrderTest:229-237` 只断言过期后查看收银台与提交支付 410，本需求不改变 | `GetCustomerPaymentTest`、`ExpireOrderTest` |

### 2.2 行为差异

| 场景（订单已过 `expires_at` 或已 EXPIRED） | 现在 | 改后 |
|---|---|---|
| 有支付记录，提交仍处理中（3DS 挑战中或等待确认） | 410 | 200，触发刷新（5 秒限频），返回处理中 / 需要验证 / 成功 / 失败 |
| 有支付记录，订单已 PAID | 410 | 200 SUCCEEDED |
| 有支付记录，订单已 EXPIRED（尝试已失败、Intent 已终止） | 410 | 200 FAILED（Payment 照常返回最终尝试状态，无特殊分支，满足 B4） |
| 有支付记录，订单已 CANCELLED | 410 | 200，返回原尝试的最终状态 |
| 有支付记录，但该提交在 Payment 没有尝试 | 410 | **见 D1** |
| 没有支付记录 | 410 | 410（不变；先判断到期再报 `PAYMENT_SUBMISSION_NOT_FOUND`，保持现有错误优先级） |
| Payment 不可用 | 410 | 200 处理中（与到期前降级一致，B3） |
| 提交支付、保存资料、查看收银台 | 410 | 410（不变） |

到期前的行为全部不变。

### 2.3 影响面

| 对象 | 影响 | 处理 |
|---|---|---|
| acquiring 支付结果查询 | 唯一改动入口 | 阶段 3 设计 |
| acquiring 写操作与查看收银台 | 仍用原 `checkExpiry` | 不改 |
| 过期扫描（`orderExpiryScan`） | 无交互：查询不写 acquiring 库；刷新只加快 Payment 收敛，成功经原事件变 PAID，失败后由下一轮扫描写 EXPIRED | 不改 |
| Payment | 接口、限频不变；到期后的轮询量只来自真正提交过支付的页面，每次刷新受 5 秒限频；终态查询是单表读 | 不改；限频沿用（Q3） |
| Token 暴露面 | Checkout Token 在到期后仍可读支付结果（状态、卡后四位、失败代码），不能读消费者资料、不能做任何写操作 | 与到期前同等数据范围，接受 |
| Client API / Webhook | `checkoutUrl` 到期即为空、事件不变 | 不改（AC-06） |
| Gateway / Platform 路由 | 原路由，状态码由 410 变 200 | 不改 |
| 后续 `checkout-web` | 结果响应里 `expiresAt` 可能已过去，前端不能据此判定失败；跳回后先查支付结果 | 写入需求不做项（已在需求第 6 节） |
| 设计文档 | 第 523、622 行 | 实施阶段修订（Q4） |
| 部署 | 只换 acquiring；code-mor-78 2026-10-23 正在替换 mor-test 的 payment / acquiring | 阶段 6 以其新镜像为回滚目标并协调 |

### 2.4 待决定

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 订单已到期、有支付记录、但 Payment 返回该提交不存在（只建了 Intent 未真正发起支付）时返回什么？到期前这种情况返回 404 `PAYMENT_SUBMISSION_NOT_FOUND` | **返回 410 `CHECKOUT_EXPIRED`**：对消费者而言这笔没付过，与 B2"没提交过支付的到期返回 410"一致；只在到期后把这一种未找到改写，到期前不变 |

## 3. 方案、接口与测试设计（阶段 3）

> 2026-10-23；阶段 2 与 D1 已由用户确认（D1：到期后 Payment 未找到该提交返回 410）。

### 3.1 改动形态

只改 `GetCustomerPaymentProcessor`，**不新增领域方法**：到期判断仍用 `CommerceOrder.checkExpiry`，只是从"一进来就拦"挪到两个"未找到"分支里。这样"有支付结果就返回、没有才看是否到期"的语义直接体现在代码位置上，阶段 1 设想的新领域方法不需要。

| 方法 | 现在 | 改后 |
|---|---|---|
| `getAndCheckOrder` | 订单存在 + `checkExpiry` | 只校验订单存在 |
| `getAndCheckPayment` | 无支付记录 → 404 `PAYMENT_SUBMISSION_NOT_FOUND` | 无支付记录 → 先 `checkExpiry`（到期 410），否则 404；入参改为订单 |
| `queryPayment` | Payment 404 → 404 | Payment 404 → 先 `checkExpiry`（到期 410，D1），否则 404 |
| `processResponse` | 再次 `checkExpiry` | 删除该检查：已取得 Payment 结果就返回 |

```java
/** 无支付记录时视同未提交：到期返回 CHECKOUT_EXPIRED，未到期返回提交不存在。 */
OrderPaymentProjection getAndCheckPayment(CommerceOrder order) {
    var projection = paymentRepository.findByOrderId(order.getOrderId());
    if (projection == null) {
        order.checkExpiry(clock.instant());
        throw new FailException(PAYMENT_SUBMISSION_NOT_FOUND);
    }
    return projection;
}

// queryPayment 中 Payment 未找到时
if (result == null) {
    context.getOrder().checkExpiry(clock.instant());
    throw new FailException(PAYMENT_SUBMISSION_NOT_FOUND);
}
```

类注释补一条：订单到期不影响已提交支付的结果查询（本需求），未提交时到期返回 `CHECKOUT_EXPIRED`。

### 3.2 错误优先级

| 顺序 | 条件 | 结果 |
|---|---|---|
| 1 | Token 格式非法 / 会话或订单不存在 | 404 `CHECKOUT_NOT_FOUND`（不变） |
| 2 | 无支付记录 | 到期 410，否则 404 `PAYMENT_SUBMISSION_NOT_FOUND`（与现在相同） |
| 3 | Payment 不可用 | 200 处理中（不论是否到期，B3） |
| 4 | Payment 未找到该提交 | 到期 410（D1），否则 404 |
| 5 | Payment 返回归属不符 / 状态非法 | 502 `UPSTREAM_PROTOCOL_ERROR`（不变） |
| 6 | 其他 | 200 真实结果（不论是否到期） |

### 3.3 接口规范核对

只读查询接口，规范中写接口相关项不适用：无事务、无锁、不写库、不发消息（3DS 交付时已如此）；Processor 仍为 `AbstractProcessor`，validate 不变；无新错误码（沿用 `CHECKOUT_EXPIRED`、`PAYMENT_SUBMISSION_NOT_FOUND`）；API 模块、OpenAPI 字段不变，仅状态码语义变化，写入设计文档修订。

### 3.4 设计文档修订（Q4）

Client API v1 设计第 523、622 行各补一句：

> 到期后不得发起新支付；Consumer 对**到期前已提交**支付的结果查询不受 `expiresAt` 限制（仍刷新 Payment，限频不变），未提交过支付时返回 `CHECKOUT_EXPIRED`；见 [到期后支付结果确认](../../../requirements/20261022/acquiring-到期后支付结果确认/requirement.md)。

### 3.5 测试设计

入口：`GetCustomerPaymentTest`（真实 HTTP → 公共客户端 → Payment 测试端点 `PaymentSubmissionMocker`）。到期用已有 `AdjustableUtcClock` 把时钟拨过 `expires_at`；订单 EXPIRED 状态用测试 Mapper 直接改状态。

| 用例 | 场景 | 断言 | 对应 |
|---|---|---|---|
| `test_expiredResultQueried`（参数化） | 时钟过期；Payment 返回 PROCESSING+3DS 交互 / SUCCEEDED / FAILED | 200，状态分别为 REQUIRES_ACTION / SUCCEEDED / FAILED；Payment 收到一次 REFRESH | AC-01、AC-02 |
| `test_expiredProcessing` | 时钟过期；PROCESSING 无交互 | 200 PROCESSING、`pollAfterMs` 2000 | AC-03 |
| `test_expiredOrderStatus` | 订单状态 EXPIRED；Payment 返回 FAILED | 200 FAILED | AC-02、B4 |
| `test_expiredWithoutPayment` | 时钟过期；删除支付记录 | 410 `CHECKOUT_EXPIRED`；Payment 零调用 | AC-04 |
| `test_expiredSubmissionNotFound` | 时钟过期；Payment 404 | 410 `CHECKOUT_EXPIRED`；Payment 一次调用 | D1 |
| `test_expiredPaymentUnavailable` | 时钟过期；Payment 断连 | 200 PROCESSING | B3 |
| 现有 `test_noDataFound` | 未到期 Payment 404 | 仍 404（回归） | D1 |

不新增、已覆盖：AC-05 由 `ExpireOrderTest:229-237`（过期后查看收银台、提交支付 410）与 `SaveConsumerTest:272`（保存资料 410）覆盖；AC-06 由 `GetOrderByClientOrderIdTest:248`（到期后 `checkout.url` 为空，`GetOrderProcessor.checkoutUrl`）覆盖，本需求不改该代码。

运行范围：acquiring `auto` 全量（先与其他会话确认无并发 acquiring 测试，见过期收尾的串扰教训）；Payment 不改，不跑。

### 3.6 本机 e2e（阶段 5）

沿用过期收尾阶段 5 的 `*_v2e2e` 环境与合成 PayerMax 3DS：提交卡支付进入 3DS 挑战 → 用脚本把 `expires_at` 拨到过去 → 以消费者身份查询提交结果得 REQUIRES_ACTION（不再是 410）→ 完成挑战成功 → 再查询得 SUCCEEDED、订单 PAID；另一笔挑战失败 → 查询得 FAILED，下一轮扫描订单 EXPIRED 后再查仍 FAILED；未提交订单到期查询 410。不再需要以 acquiring-service 身份直连 Payment 刷新的绕行。

### 3.7 回滚

只换 acquiring 镜像，无数据变化；回滚即换回前一镜像。mor-test 当前 acquiring 为 code-mor-78 2026-10-23 07:49Z 发布的 `27c36f4e…`（d3d39e40 + billing B，含 `OrderPaymentSucceeded` 发布）：本需求的候选必须基于 code-mor-78 的提交 `98210e0b`（与 mor-test 运行源码一致）之后的 main 构建，否则会丢掉 billing B；回滚目标即该镜像。
