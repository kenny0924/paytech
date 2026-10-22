# EXP-C 报告：acquiring 订单与会话过期收尾核心

> 执行代理报告，2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached HEAD，基线 `42879401` + EXP-A、EXP-B 未提交改动）。未提交、未推送、未建分支，未对共享环境执行 SQL，未部署，未改 payment 代码。无 DDL。

## 1. 结论

- 到期订单的扫描与单订单收尾已实现，按 technique 3.2 的决定映射处理五种 Payment 结果与 Payment 不可用。
- `order.expired` 事件类型与载荷已实现，在过期写入的同一事务登记。
- 门禁：acquiring `auto` 830 例全部通过（原 809 例 + 新增 21 例）；全 reactor `test-compile` 通过，118 个模块。
- 有三处与卡面描述不完全一致，均已说明理由，见第 8 节。最需要主会话确认的是第 1 项：拿不到订单锁时的“跳过”由扫描 Processor 计数，而不是收尾 Processor 自己返回。

## 2. 已读规范与合同

- `AGENTS.md`（根）
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`（含第 5 节事务、5.1 锁、5.2 编排、5.3 Repository）
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- `agent-docs/examples/WRITE_API_EXAMPLE.md`
- `agent-docs/UNIT_TEST_GUIDE.md`
- `agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- `agent-docs/COMPENSATION_EVENT_GUIDE.md`
- `agent-docs/DATABASE_DESIGN_GUIDE.md`（Mapper、版本与条件更新相关部分）
- 任务卡 `tasks/cards.md`（EXP-C 行、1.1 裁决含运行提示、第 2 节门禁）
- `technique.md` 第 1.1、2.1（R2、R5、R6）、2.4（D3、D4）、3.2、3.3.3、3.3.4、3.4、3.6 节
- `requirement.md` B1—B7、AC-01—AC-06、AC-08、AC-09
- `tasks/EXP-A-report.md`、`tasks/EXP-B-report.md`
- 样板代码：`CancelOrderProcessor`、`OrderCancellationContext`、`OrderCancellationService(+Impl)`、`CommerceOrderRepository(+Impl)`、`CommerceOrderMapper.xml`、`CheckoutSessionMapper.xml`、`ClientWebhookEvent`、`ClientWebhookSubmissionHandler.buildEvent`、payment `TerminateIntentProcessor` 与 `Locks`、Foundation `AbstractLockProcessor`、`SubmitCustomerPaymentProcessor`、`ApplyPaymentSucceededProcessor`

实施前的说明：入口是 acquiring core 的 `ExpireOrderProcessor`，Context 为 `OrderExpiryContext`。本地原子写入有两段。第一段是 `requestTermination` 单独的短事务。第二段是 `OrderExpiryService.expire` 的一个 REQUIRED 事务，写订单、会话、投影和 Webhook 登记。Payment 按来源查询与终止都在事务外。

## 3. 改动文件

路径相对 `paytech/acquiring/`。

**生产代码（paytech-acquiring-core）新增**

| 文件 | 说明 |
|---|---|
| `core/constant/Locks.java` | `getOrderLock(orderId)` 返回 `order:<orderId>`，与支付提交原有 Key 相同 |
| `domain/order/OrderExpiryContext.java` | 订单、会话、投影、是否有接受证据、`now`；纯判断 `isClosed`、`isDue`、`canExpireLocally`、`paymentIntentId`、`isPendingBeyond(Duration)` |
| `domainservice/service/order/OrderExpiryService.java` 与 `impl/OrderExpiryServiceImpl.java` | `requestTermination` 与 `expire` |
| `service/processor/order/ExpireOrderProcessor.java` | `AbstractLockProcessor`，订单锁 TRY_LOCK |
| `service/processor/order/ScanExpiredOrdersProcessor.java` | `AbstractProcessor`，游标分页、分片、逐单调用 |
| `service/processor/order/request/ExpireOrderRequest.java` | orderId、now |
| `service/processor/order/request/ScanExpiredOrdersRequest.java` | shardIndex、shardTotal、batchSize（≤1000）、maxBatches、now |
| `service/processor/order/response/ExpireOrderResponse.java` | 枚举 EXPIRED / PENDING / ALREADY_PAID / REVERSAL_REQUIRED / SKIPPED / RETRY |
| `service/processor/order/response/ScanExpiredOrdersResponse.java` | 扫描条数、各结果条数、失败条数 |

**生产代码修改**

| 文件 | 说明 |
|---|---|
| `core/dal/mapper/order/CommerceOrderMapper.java` 与 `resources/mapper/order/CommerceOrderMapper.xml` | 新增 `selectExpiredOrderIds`、`expire` |
| `core/dal/mapper/checkout/CheckoutSessionMapper.java` 与 `.xml` | 新增 `expire` |
| `core/dal/mapper/order/OrderPaymentProjectionMapper.java` 与 `.xml` | 新增 `recordTerminated` |
| `domainservice/repository/order/CommerceOrderRepository(+Impl)` | 新增 `findExpiredOrderIds`、`expire` |
| `domainservice/repository/checkout/CheckoutSessionRepository(+Impl)` | 新增 `expire` |
| `domainservice/repository/order/OrderPaymentProjectionRepository(+Impl)` | 新增 `recordTerminated` |
| `domain/enums/ClientWebhookEventType.java` | 新增 `ORDER_EXPIRED("order.expired", "expired")` |
| `domain/webhook/ClientWebhookEvent.java` | 新增常量 `EXPIRY_REACHED_REASON`（与 `CLIENT_REQUESTED_REASON` 并列）与工厂 `orderExpired(order, session, now)` |
| `service/processor/customer/SubmitCustomerPaymentProcessor.java` | 锁 Key 改为调用 `Locks.getOrderLock`，Key 值不变 |

**测试代码（paytech-acquiring-test）新增**

| 文件 | 说明 |
|---|---|
| `test/dal/OrderExpiryTestMapper.java` | 改写到期时刻、读取原因码 |
| `test/mocker/local/OrderExpiryDataMocker.java` | 到期订单夹具：CREATED 无会话、OPEN 有会话、有投影、有接受证据无投影，以及任意 Builder 加指定 `expires_at` |
| `test/checker/order/OrderExpiryChecker.java` | 订单、会话、投影状态与原因码断言 |
| `auto/ext/job/ExpireOrderTest.java` | 17 例 |
| `auto/ext/job/ClientWebhookOrderExpiredTest.java` | 4 例 |

既有测试类与测试设施未修改。

## 4. SQL 原文

所有语句都在 Mapper XML 中，没有使用注解 SQL。

**语句 1：`CommerceOrderMapper.selectExpiredOrderIds`**

```xml
<select id="selectExpiredOrderIds" resultType="java.lang.Long" useCache="false" flushCache="true">
    SELECT order_id FROM t_commerce_order
    WHERE order_status IN ('CREATED','OPEN') AND expires_at &lt;= #{now} AND order_id &gt; #{afterOrderId}
        AND MOD(order_id, #{shardTotal}) = #{shardIndex}
    ORDER BY order_id LIMIT #{limit}
</select>
```

**语句 2：`CommerceOrderMapper.expire`**

```xml
<update id="expire">
    UPDATE t_commerce_order SET order_status='EXPIRED',status_reason_code='EXPIRY_REACHED',
    version=version+1,update_time=#{now}
    WHERE order_id=#{order.orderId} AND client_id=#{order.clientId} AND version=#{order.version}
    AND order_status IN ('CREATED','OPEN') AND termination_requested_at IS NOT NULL AND paid_at IS NULL
    AND expires_at &lt;= #{now}
    <choose>
        <when test="payment != null">
            AND EXISTS (
            SELECT 1 FROM t_order_payment_projection p
            WHERE p.order_id=#{order.orderId} AND p.payment_intent_id=#{payment.paymentIntentId}
            AND p.version=#{payment.version} AND p.successful_attempt_id IS NULL
            AND p.captured_amount=0 AND p.intent_status &lt;&gt; 'SUCCEEDED'
            )
        </when>
        <otherwise>
            <if test="localOnly">
                AND NOT EXISTS (SELECT 1 FROM t_consumer_acceptance a JOIN t_consumer_contract c
                ON c.consumer_contract_id=a.consumer_contract_id WHERE c.order_id=#{order.orderId})
            </if>
            AND NOT EXISTS (SELECT 1 FROM t_order_payment_projection p WHERE p.order_id=#{order.orderId})
        </otherwise>
    </choose>
</update>
```

**语句 3：`CheckoutSessionMapper.expire`**

```xml
<update id="expire">
    UPDATE t_checkout_session SET session_status='EXPIRED',version=version+1,update_time=#{now}
    WHERE checkout_session_id=#{session.checkoutSessionId} AND order_id=#{session.orderId}
        AND version=#{session.version} AND session_status IN ('PREPARING','OPEN','PAYMENT_PROCESSING')
</update>
```

**语句 4：`OrderPaymentProjectionMapper.recordTerminated`**

```xml
<update id="recordTerminated">
    UPDATE t_order_payment_projection SET intent_status='EXPIRED',version=version+1,update_time=#{now}
    WHERE order_payment_projection_id=#{payment.orderPaymentProjectionId} AND order_id=#{payment.orderId}
        AND payment_intent_id=#{payment.paymentIntentId} AND version=#{payment.version}
        AND successful_attempt_id IS NULL AND captured_amount=0
</update>
```

仓储层的处理：三个写方法在行数不为 1 时抛 `ConcurrentRequestException`。成功后同步内存对象。订单同步状态、版本和 `updateTime`；会话同步状态、版本和 `updateTime`；投影同步 `intentStatus` 和版本，因为投影 DataObject 没有 `updateTime` 字段。订单 DataObject 也没有原因码字段，与取消一致，原因码只写在 SQL 中。

## 5. Processor 流程与决定处理

**`ExpireOrderProcessor`**（类注释写明约束，模板 override 不写 JavaDoc）：

1. `lockCommand` 使用 `LockCommand.tryLock(Locks.getOrderLock(orderId))`。它与支付提交共用同一互斥域，因为两者都改订单的支付资格。
2. 锁内读取订单、实际关联的会话、支付投影和接受证据，然后一次构造 Context。订单缺失，或 Hosted Checkout 缺会话，抛 `ErrorException`。
3. 订单已关闭（非 CREATED/OPEN，或 `paid_at` 非空）或未到期时返回 SKIPPED。
4. `canExpireLocally()`（无投影且无接受证据）成立时直接调用 `expire`，返回 EXPIRED。
5. 否则先调用 `requestTermination` 提交短事务。之后在事务外调用 `PaymentTerminationClient.terminate`，参数为 clientId、orderId、投影 intentId（可空）和 `ORDER_EXPIRED`。

| 结果 | 处理 | 返回 |
|---|---|---|
| TERMINATED（含 Payment 的 ALREADY_TERMINATED） | `expire` | EXPIRED |
| `FailException` 且 code 为 `NOT_FOUND` | `expire` | EXPIRED |
| 其他 `FailException` | 原样抛出 | — |
| PENDING | 不写；`termination_requested_at` 早于 `now - 24h` 时记一条 WARN，含 orderId、intentId | PENDING |
| ALREADY_PAID | 不写，记 INFO | ALREADY_PAID |
| REVERSAL_REQUIRED | 不写，记 WARN | REVERSAL_REQUIRED |
| `ServiceUnavailableException` | 不写 | RETRY |
| 响应归属不一致或决定未知 | 抛 `CheckoutUpstreamProtocolException` | — |

PENDING 的告警阈值本卡用常量 `PENDING_ALERT_AFTER = 24h`，由 EXP-D 改为配置注入。日志只含 orderId、intentId 和决定，不含 Token、URL 或卡数据。

**`OrderExpiryService.expire`** 在一个 `CommerceTransactionExecutor.write`（REQUIRED）事务内依次执行：

1. 如未写终止意图，先调用 `requestTermination`（本地收尾分支会走到这里）。
2. 订单 `expire`，`localOnly = canExpireLocally()`。
3. 有会话时，会话 `expire`。
4. 有投影时，投影 `recordTerminated`（D3）。
5. 用 `ClientWebhookSubmissionHandler.buildEvent(ClientWebhookEvent.orderExpired(...))` 构造登记请求，交给 `CompensationEventRegistrar` 登记。

事务内没有外部调用。订单 SQL 先用投影的原版本做守卫，之后才推进投影版本。

**`order.expired` 载荷**：字段为 orderNo、clientOrderId、orderStatus、checkoutStatus（仅有会话时）、`reasonCode=EXPIRY_REACHED`、`expiredAt=expires_at`、version（写入后的订单版本）。Event ID 是 `evt_<orderNo>_expired`，Envelope `createdAt` 是写入时刻截断到秒。

**`ScanExpiredOrdersProcessor`**：

- `validate` 要求 `shardIndex < shardTotal`。
- 从游标 `afterOrderId = 0` 开始，最多读取 maxBatches 页。不足一页时结束，否则游标移到本页最后一个 ID。
- 逐单调用 `ExpireOrderProcessor.process`。`ConcurrentRequestException`（订单锁被占，或写入时版本已变）计为 SKIPPED。其他运行时异常记 WARN 并计入失败数，然后继续下一单。
- 本身不加锁、不开事务。

## 6. 测试命令与结果

在 `paytech/` 下执行。四项 RabbitMQ 和 Redis 变量从主工作区 `local-environment/.env.local` 只读导出，未打印：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
set -a; eval "$(grep -E '^(MOR_RABBITMQ_USER|MOR_RABBITMQ_PASSWORD|MOR_RABBITMQ_VHOST|MOR_REDIS_PASSWORD)=' \
  /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local)"; set +a
./mvnw -o -pl acquiring/paytech-acquiring-test -am test -Dtest='com.paytech.acquiring.auto.**' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
./mvnw -o test-compile
```

| 步骤 | 结果 |
|---|---|
| 新增两类 | ExpireOrderTest 17、ClientWebhookOrderExpiredTest 4，全部通过 |
| acquiring 全部 auto | 830 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS |
| 全 reactor `test-compile` | BUILD SUCCESS，118 个模块 |

首轮新增测试有 6 例失败，均为测试侧问题，已修正：

1. **收银台提交的预期状态写错（2 例）。** 我原先断言 409 `CHECKOUT_CLOSED`。实际 `SubmitCustomerPaymentProcessor` 先执行 `checkExpiry`，已到期的订单先返回 410 `CHECKOUT_EXPIRED`。断言已改为 410，这是现有读侧行为，不改语义。
2. **同一用例建两单时唯一键冲突（3 例）。** 两单使用了同一个默认 `clientOrderId`，触发 `uk_order_client_order`。`OrderExpiryDataMocker.template()` 现在为每单生成不同的 Client 订单号。
3. **取消订单的原因码断言写错（1 例）。** 先取消再扫描的场景里，Checker 要求原因码为空，但取消会写 `CLIENT_REQUESTED`。现改为“不是 `EXPIRY_REACHED`”，并单独断言 `CLIENT_REQUESTED`。

**ExpireOrderTest 场景**（入口为 `ScanExpiredOrdersProcessor.process`，部分用例另调 `ExpireOrderProcessor.process` 模拟另一实例）：

| 用例 | 覆盖 |
|---|---|
| `test_expireWithoutIntent`（CREATED 无会话 / OPEN 有会话） | AC-01：订单与会话 EXPIRED、原因码、未调用 Payment；GET 订单为 EXPIRED、`paymentStatus=NOT_STARTED`，收银台地址为空 |
| `test_terminatedWithProjection`（TERMINATED / ALREADY_TERMINATED） | AC-02：终止请求为投影 intentId 加 `ORDER_EXPIRED`，不按来源查询；订单、会话、投影 EXPIRED；GET `paymentStatus=EXPIRED` |
| `test_acceptedSourceFoundTerminates` | 无投影有接受证据：先按来源查询，再终止，然后收尾，不补写投影 |
| `test_acceptedSourceNotFoundExpires` | 按来源查询返回 404：本地收尾，不调用终止 |
| `test_pendingKeepsOpenUntilPaymentConverges` | AC-03：订单保持 OPEN，终止意图已写，会话与投影不变，未登记事件，提交支付被拒（410）；Payment 收敛后下一轮收尾，终止意图保留首次时刻 |
| `test_paidOrAuthorizedKeepsOpen`（ALREADY_PAID / REVERSAL_REQUIRED） | AC-04、AC-05：订单保持 OPEN，只写终止意图，不登记事件 |
| `test_paymentUnavailableRetries` | Payment 503 返回 RETRY，订单保持 OPEN，除终止意图外不写 |
| `test_expiredOrderRejectsCancelCheckoutAndPayment` | AC-08：Cancel 返回 409 `ORDER_NOT_CANCELLABLE`；GET 收银台与提交支付返回 410 `CHECKOUT_EXPIRED` |
| `test_repeatedScanWritesOnce` | AC-09：第二次扫描不再选中，直接重放返回 SKIPPED；版本不变，只终止一次，只提交一次 Webhook |
| `test_cancelledBeforeScanNotRewritten` | AC-09：先取消后扫描，不选中、直接重放跳过，取消事实与版本不变 |
| `test_lockedOrderSkipped` | 另一线程持有订单锁时计为 SKIPPED 且不写；锁释放后下一轮收尾 |
| `test_notDueOrPaidSkipped` | 未到期与已 PAID 订单不被选中，直接重放跳过，不调用 Payment |
| `test_singleFailureDoesNotStopScan` | 一单收到 Payment 400 计为失败，同批另一单仍收尾 |
| `test_pagingStopsAtMaxBatches` | 页数上限截断，剩余订单下一轮处理 |

**ClientWebhookOrderExpiredTest 场景**（AC-06，以 `ClientWebhookOrderCancelledTest` 为模板）：

- 本地收尾时冻结载荷，Envelope 与 Hash 由 `ClientWebhookChecker.assertFrozen` 独立复算。有会话与无会话两例都覆盖，无会话时省略 `checkoutStatus`。
- Event ID 为 `evt_<orderNo>_expired`。
- 重复扫描不再登记，也不再提交。
- Payment 确认终止后的收尾提交一次。
- PENDING 时不登记，也不提交。

未使用 Mockito Stub、`@MockBean` 或 Spy。Payment 通过 EXP-B 的 `MockPaymentIntentApi` 和 `PaymentTerminationMocker` 返回 HTTP DTO。未配置的调用由基类在清理前判失败。

## 7. WRITE_API_EXAMPLE 第 11 节自查

| 检查项 | 结果 |
|---|---|
| API 与 Body 合同、非 HTTP 入口校验触发点 | 无 HTTP 入口。两个内部 Request 用 Bean Validation 注解（`@Positive`、`@PositiveOrZero`、`@Max`、`@NotNull`），由公共 `validateBasic` 触发；`shardIndex < shardTotal` 放在 `validate` |
| Controller / Converter 仅适配；Processor 位置 | 不涉及 Controller。两个 Processor 在 acquiring core 的 `service/processor/order` |
| Processor 只查本模块 Repository | 符合。`ExpireOrderProcessor` 读四个本模块 Repository，写入全部通过 `OrderExpiryService`；扫描只读 `findExpiredOrderIds` |
| Context | 符合。`OrderExpiryContext` 直接持有订单、会话、投影 DataObject，一次构造；纯方法不访问外部，`now` 由调用方传入 |
| Service 不调外部、不调其他 Service | 符合。`OrderExpiryServiceImpl` 只用三个 Repository、事务执行器和登记器；`buildEvent` 是纯静态工厂 |
| 本地原子写入与事务位置 | 符合。终止意图单独提交；订单、会话、投影和 Webhook 在同一 REQUIRED 事务内；Payment 查询与终止在两段事务之间、事务外执行 |
| Repository 条件 SQL、行数检查、同步原对象 | 符合。见第 4 节，失败抛 `ConcurrentRequestException`，成功后直接改传入对象 |
| 锁 | 符合。锁住实际资源（订单），与支付提交共用 `Locks.getOrderLock`；TRY_LOCK；条件 SQL 仍是最终保护 |
| 原请求比较、终态重放 | 不适用原请求比较：收尾没有命令身份，按当前订单事实处理。已终态订单重放跳过，有测试覆盖 |
| Gateway 请求幂等 | 不涉及。未引入 Idempotency-Key 或命令幂等组件 |
| View | 不适用 |
| 无生产 Mock、无多余抽象 | 符合。新增类型都有当前消费者；`Locks` 有两个调用方 |
| 实际验证 | 见第 6 节 |

## 8. 偏离与原因

1. **拿不到锁时的“跳过”由扫描 Processor 计数。** 卡面写“拿不到锁返回跳过”。`AbstractLockProcessor` 在 TRY_LOCK 失败时抛 `ConcurrentRequestException`。要在收尾 Processor 内部返回 SKIPPED，只能重写模板的加锁流程，而开发规范禁止这样做。所以 `ExpireOrderProcessor.process` 在锁竞争时抛该异常，由 `ScanExpiredOrdersProcessor` 计为 SKIPPED。写入时版本已变（B3“放弃本轮”）也是同一异常，同样计为 SKIPPED。如果希望直接调用收尾 Processor 时也得到 SKIPPED 返回值，需要另行决定。
2. **收银台提交被拒的状态码是 410，不是 409。** AC-03 与 AC-08 只要求提交被拒。现有提交 Processor 先检查到期，已到期的订单返回 410 `CHECKOUT_EXPIRED`，所以终止意图在此路径上不是第一道拒绝。读侧语义未改（R6）。
3. **有投影但终止返回 404 时，也会把投影写为 EXPIRED。** technique 3.2 的表格对 404 注明“SQL 要求无投影”，说的是按来源查询返回 404 的情形。如果投影存在而终止返回 404，我沿用取消的处理：订单 SQL 走投影守卫分支并收尾，`expire` 同时把投影写为 EXPIRED。这种情形表示投影引用的 Intent 不存在，属于数据异常，没有单独编写测试。
4. **支付提交 Processor 有一处小改动。** 锁 Key 原来是字面量 `"order:" + orderId`，现改为调用 `Locks.getOrderLock(orderId)`，Key 值不变。依据是规范“同资源入口共用锁命名”。

## 9. 未完成项与提示

- 卡内工作已全部完成，未进入 EXP-D。以下留给 EXP-D：XXL Handler、`OrderExpiryProperties`、`application.yml`、把测试入口改为 `runOnce`、分片不命中、24 小时 WARN 场景、PENDING 告警阈值配置化。
- AC-04 的后半段“`PaymentSucceeded` 到达后置 PAID 并发 `order.paid`”由现有 `ApplyPaymentSucceededProcessor` 承担，本卡未改，也未在过期测试中重复覆盖。现有 `PaymentSucceededListenerTest` 与 `ClientWebhookOrderPaidTest` 已覆盖 OPEN 订单收到支付成功的路径。
- 扫描查询按 `order_id` 排序，同时在 `expires_at` 上做范围过滤，可能使用 filesort。本地数据量下没有问题，生产数据量下的执行计划未验证。

## 10. 新基线返工（main 22ede435，含 3DS 合入与 EXP-D 三方合并）

### 10.1 现象

全量 839 例中 `ExpireOrderTest.test_repeatedScanWritesOnce` 失败一次：第 254 行第二次扫描的 `getScannedCount()` 为 1。该类单独连跑三次都通过。

### 10.2 根因

另一个会话在同一时间跑 acquiring 全量测试，与本侧共用一套 local-environment 的 `mor_acquiring` 库和 RabbitMQ vhost。

- **进程。** 工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-billing-b` 在运行 `paytech-acquiring-test`，参数为 `-Dtest=com/paytech/acquiring/**/*`。它的 `application-mock.yml` 指向同一个 `127.0.0.1:13306/mor_acquiring` 和同一个 vhost。
- **数据。** 只读查询时，库里有一条不属于本上下文的 OPEN 订单。它的 `client_id=1047714957050`，是另一个测试上下文的随机命名空间；`client_order_id=intent-<id>`，来自 `CheckoutPaymentDataMocker.mockPrepared`；`expires_at=2026-09-08 00:30`，即固定时钟加夹具默认的 30 分钟。几秒后该行已被对方清理，而本侧当时没有在运行。
- **为何只命中这一例。** 本例第二次扫描前把时钟拨快了 1 小时。生产扫描本来就是全局扫描，所以选中了对方的这单。首次扫描在固定时钟上进行，选不到它。
- **`PaymentSucceededListenerTest.test_replayedEventSkipped`（handled 3→4）同源。** 两个 JVM 消费同一 vhost 上的同一组队列，一方发布的消息可能被另一方处理，指标因此多出一条。

结论按主会话裁决：这是两个会话并发跑全量造成的环境冲突，不是本需求测试的隔离缺陷，也不是生产缺陷。

### 10.3 改动

无。曾经加过一个测试侧守卫：扫描前检查有没有其他 Client 的到期订单，同时把两处二次扫描的时钟拨动从 1 小时改为 1 分钟。按裁决已全部撤回。工作区与合并基线相比，这四个测试文件没有差异。生产代码也未改动。

### 10.4 结果

先在前台等待 billing-b 的 Maven 进程（PID 47252）退出，于 23:47:55 退出。随后在前台连续运行两次，命令同第 6 节。

| 运行 | 时间 | 结果 |
|---|---|---|
| 全量第 1 次 | 23:48—23:53 | 839 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS |
| 全量第 2 次 | 23:53—23:57 | 839 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS |

核对其他工作目录的 acquiring surefire 报告，最新时间分别是：billing-b 23:36:55，card3ds-int 17:40:35。两者都早于这两次运行，运行期间没有并发的 acquiring 测试。
