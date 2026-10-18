# 终止 Intent 接口修订

本修订落实用户已确认的 `terminateIntent` 入口，属于既有 EX-I04 的研发范围。沿用[删除 Binding 后的终止边界](execution-field-simplification-amendment.md#8-删除来源-binding)，只终止已存在且能够安全关闭的 Intent；不新增表、字段、状态或后台任务。

本入口取代旧 EX-I04 的独立命令幂等、通用状态历史、补偿登记及宽响应要求。其他接口不随本修订改变。代码组织遵循[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)与[Payment 专项补充](../../../paytech/payment/docs/development/processor-conventions.md)；实现、验证和部署状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 1. 接口

`POST /payment-execution/v1/intents/{intentId}/terminate`，可信内部 HTTP。应用 Context Path 为 `/payment`，不注册公共 Gateway 路由。

| 输入 | 类型 | 要求 |
|---|---|---|
| intentId | Path String | 正十进制 Long，无前导零 |
| clientId | Body String | 必填，同上；必须与 Intent 归属一致 |
| reason | Body String | 必填，`ORDER_CANCELLED` 或 `OPERATING_ENTITY_CLOSED` |

ID 上限为 `9223372036854775807`，不接受 JSON 数字或未知 Body 字段。两种原因均执行相同的安全终止条件，不直接改变 OE 状态、不触发撤销或退款；本期不另存终止原因或新增历史表。

请求不要求 `Idempotency-Key`。它表示“将这个 Intent 安全终止”，按资源当前事实处理重复调用，不创建独立命令、Hash 或结果快照。上游不得把 PENDING 当成已经关闭，也不能把未查询到 Intent 当作未创建；创建结果未知时先恢复原创建结果。

```json
{"clientId":"101","reason":"ORDER_CANCELLED"}
```

响应仅包含 `intentId/status/decision`，均为字符串；`status` 是 Intent 当前持久化状态，`decision` 是本次终止判断。正常业务结果（包括 PENDING）统一返回 HTTP 200，由上游判断 `decision`，均设置 `Cache-Control: no-store`。参数、归属、并发及系统错误继续使用相应的 4xx/5xx。

```json
{"intentId":"601","status":"CANCELLED","decision":"TERMINATED"}
```

## 2. 判断与数据影响

| 已加载事实 | decision | 数据操作 |
|---|---|---|
| Intent 已成功或存在成功 Attempt 指针 | ALREADY_PAID | 保留资金事实，退款另行发起 |
| Intent 已 CANCELLED 或 EXPIRED | ALREADY_TERMINATED | 返回原状态，不重复更新版本或时间 |
| OPEN 且没有活动或成功 Attempt | TERMINATED | 仅将 Intent 改为 CANCELLED，version + 1，更新 update_time |
| 活动 Attempt 已成功或存在 captured_amount | ALREADY_PAID | 不终止 |
| 活动 Attempt 仍绑定 active_action_id | PENDING | 保留原 Action 和执行位，不查询渠道、不重试动作 |
| 活动 Attempt 无执行位，authorized_amount > 0 | REVERSAL_REQUIRED | 不终止；Reversal/Void 属于独立能力 |
| 其余活动 Attempt，包括受理后尚未创建 Action | PENDING | 保留尝试，等待原流程明确收敛 |

当前流程使用 active_attempt_id 保留执行或授权风险，Action 的 PROCESSING/UNKNOWN 通过 active_action_id 保留占位。因此本入口读取 Intent 和活动 Attempt 即可判断，不扫描历史 Action，不把空 active_action_id 等同于可关闭。

活动指针指向缺失记录或其他 Intent 的 Attempt 时返回系统错误，不能按“没有活动尝试”处理。Intent 不存在返回 404，Client 归属不符沿用当前 Payment 业务拒绝 400，锁竞争或写入条件冲突返回 409；错误 Body 复用 Foundation Problem。

PENDING 只是当前未决结果，不登记异步终止任务、不承诺已经阻断首次资金发送。调用方在原流程收敛后可再次调用。认证等待、风控未决和未知资金动作不在本接口强制取消；真实恢复、Void、Refund、MQ 通知及 Commerce 适配由后续范围接入。

## 3. 处理顺序与并发

```text
Body 字段校验 → Converter 解析 ID
  → TerminateIntentProcessor：TRY_LOCK，同 pay 的 Intent 资源 Key
  → 查询 Intent 并核对 Client → 按 active_attempt_id 查询并核对 Attempt
  → 构造 TerminateIntentContext → 计算终止决定
      ├─ 已终态、已收款、未决或需撤销：直接响应
      └─ 可以终止：PaymentExecutionService 的一个短事务
          → PaymentIntentRepository → Mapper 条件 UPDATE
          → 更新成功后同步原 Intent 对象 → 响应
```

Context 直接持有本模块 Intent、可空活动 Attempt；判断是基于已加载事实的纯业务方法，不访问外部依赖。Service 不重复查询和验证，不调用其他 Service。Repository 不接收 Context。当前仅有 HTTP 入口，内部 Request 不发布为独立调用接口，字段校验由 API 的 @Valid 执行。

SQL 同时要求原 version、`intent_status = OPEN`、`active_attempt_id IS NULL`、`successful_attempt_id IS NULL`，并检查影响行数为 1。与 pay 绑定 Attempt 的条件更新竞争时，只允许符合当前事实的一方提交；pay 绑定失败会回滚同事务的新 Attempt。Repository 只在 SQL 成功后修改原对象的 status/version/updateTime，事务失败后丢弃 Context。

不新增幂等表、终止标志、状态历史或 Action，也不撤销工具 Token；Intent 终态已阻止新工具申请和新支付受理，原 pay 重放仍按既有合同返回原提交。

## 4. 验证范围

按用户本次要求暂不编写单元测试。开发检查覆盖编译、协议和分层静态核对、Mapper 条件及文档链接；这不能代替真实 HTTP/MySQL/Redis 验证。

后续入口验证应覆盖安全终止与重复调用、已过期/已支付、授权等待 Capture、活动 Action 与 UNKNOWN、受理后尚未创建 Action、缺失/错绑 Attempt、Client 归属拒绝，以及和 pay 的同资源竞争、CAS 冲突及回滚。无外部调用，外部请求编排和结果落库阶段不适用。
