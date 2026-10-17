# pay 接口阅读说明

编码方式见 [写接口开发规范](development/processor-conventions.md)，业务语义见[受理链合同](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#7-pay-受理链合同)，验证及迁移状态见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。当前 pay 沿用用户重构后的 AbstractLockProcessor，在 Intent 资源锁内查询原提交、完整构造 Context、保存和推进。来源提交防重取代前序 Claim、Hash 与补命令绑定流程；旧的无锁父类、空 Context 逐步填充及保存冲突自动重查描述不再适用。

## 先看这三个文件

1. [PayProcessor](../paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/processor/PayProcessor.java)：完整接口顺序。
2. [PayContext](../paytech-payment-execution/src/main/java/com/paytech/payment/execution/domain/PayContext.java)：一次请求涉及的意图、尝试和工具。
3. [PaymentExecutionServiceImpl](../paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/impl/PaymentExecutionServiceImpl.java)：唯一受理事务。

```text
开始
  │
  ├─ MVC @Valid：执行 RequestBody 字段约束
  ├─ Web Converter：解析路径 ID、核对固定幂等键、构造 PayRequest
  ├─ AbstractProcessor.process：拒绝调用方已有事务，执行公共校验
  ├─ AbstractLockProcessor：TRY_LOCK 立即尝试获取 Intent 资源锁
  ├─ getAndCheckPaymentIntent：查询 Intent 并验证 Client 归属
  ├─ checkAndGetContext：按 sourceSubmissionId 查询原 Attempt
  │    ├─ 已存在：核对 Client、Intent、方式、捕获模式及工具 → 响应
  │    └─ 不存在：继续构建受理上下文
  │
  ├─ buildPayContext：验证新建资格与工具，构造 PROCESSING Attempt
  │    └─ new PayContext(intent, attempt, token)
  │
  ├─ route：仅新 Attempt 调用 RouteService，绑定 Channel / Account / MID
  ├─ savePayContext：一次本地事务，插入 Attempt 并绑定 Intent
  │    ├─ 成功：继续执行新尝试
  │    └─ 受理冲突或失败：整体回滚并抛出异常
  ├─ ActionExecutionService.execute：选择 Flow，独立事务创建并绑定首动作
  ├─ ActionFlow.execute：执行 Actor → 保存结果并切换执行位 → 下一动作
  └─ processResponse → PayResponse → 释放资源锁 → Web Converter → PayResponseBody → 结束
```

## 受理前选路

[RouteService](../paytech-payment-routing/src/main/java/com/paytech/payment/routing/service/RouteService.java) 属于 Routing 模块，参数使用该模块的 RouteContext，包含 Client、OE、金额、币种、支付方式和 capture mode；不传 Execution 数据库对象或工具 Token。返回 RouteResult，仅含 channelId、channelAccountId、midId，由 Processor 填入本次 Attempt。

调用发生在本地事务前，选路异常会直接结束请求，不保存新 Attempt 或 Intent 活动指针。原提交命中提前响应，不覆盖原渠道，包括未绑定渠道的历史 Attempt。同 Intent 的请求由公共父类协调，未获取锁返回并发冲突；不会等待前一个请求完成后自动重放。后续动作沿用受理时冻结的渠道三元组。

[RouteServiceImpl](../paytech-payment-routing/src/main/java/com/paytech/payment/routing/service/impl/RouteServiceImpl.java) 按用户本期要求仅返回固定 Mock `9001/9002/9003`。它未验证同 OE、渠道能力、配置状态或健康状态，也不保存选择证据；这些由 Routing 后续实现。本次没有独立 Route 进程或远程 HTTP 调用。

## 事务实际做什么

每张表使用自己的 Repository 和 Mapper，仅访问 Payment 本库：

| 表 | Repository | Mapper SQL |
|---|---|---|
| `t_payment_intent` | `PaymentIntentRepository` | [PaymentIntentMapper.xml](../paytech-payment-execution/src/main/resources/mapper/PaymentIntentMapper.xml) |
| `t_payment_attempt` | `PaymentAttemptRepository` | [PaymentAttemptMapper.xml](../paytech-payment-execution/src/main/resources/mapper/PaymentAttemptMapper.xml) |
| `t_payment_instrument_token` | `PaymentInstrumentTokenRepository` | [PaymentInstrumentTokenMapper.xml](../paytech-payment-execution/src/main/resources/mapper/PaymentInstrumentTokenMapper.xml) |

Action 使用 PaymentActionRepository，资金引用使用 ChannelTransactionRefRepository，均对应同名 Mapper/XML。pay 不再访问公共命令表；动作执行不使用补偿表。

Context 只用于 Service 层编排，调用 Repository 时拆成数据库对象和明确参数。例如 `bindAttempt(intent, attempt)`、`bindAction(attempt, actionId)`；Mapper/XML 同样不依赖 Context。Intent 的目标状态由 Service 判断，Repository 写入成功后同步对象。

| 顺序 | 新 Attempt |
|---|---|
| 1 | 插入含渠道三元组的 `t_payment_attempt`；SQL 检查工具未撤销及工具/Intent 有效期 |
| 2 | CAS 更新 `t_payment_intent.active_attempt_id` 和 `version` |

两步在一个 TransactionTemplate 中提交；CAS、有效期、唯一键或影响行数不匹配，整体回滚。Service 不重新解析请求或重复关联验证，Processor 没有保存异常自动重查分支。原提交的查询与内容比较发生在保存前；内容不一致返回 409。资源锁包住完整请求，数据库事务只覆盖本地原子保存，事务中没有 Risk、Commerce、Card、Channel 或 Redis 调用。

`PaymentIntentRepository.bindAttempt` 确认更新恰好一行后，同步传入 Intent 的 `activeAttemptId`、`version` 和 `updateTime`。Service 不再重复设置这些字段。发生回滚时本次请求失败，候选对象不用于响应或后续执行；下一次调用重新读取原提交并构造 Context。

新 Attempt 为 `PROCESSING`，后续状态由 Action 执行更新；响应始终从原业务对象生成，不再保存独立命令状态、响应快照或租约。旧 `t_command_idempotency` 在本轮不再被运行代码读写，模型、Schema 和实际数据库尚未执行表清理；旧公共表列迁移不再是 pay 启动前置。

来源关联由 Intent 自身及唯一索引维护，pay 不再依赖独立 Binding 表。创建和终止的顺序见[字段修订第 8 节](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#8-删除来源-binding)。

## 请求与结果

私有合同：`POST /payment-execution/v1/intents/{intentId}/pay`。默认应用另有 `/payment` Context Path。

```http
Idempotency-Key: submission:401
Content-Type: application/json
```

```json
{
  "clientId": "101",
  "sourceSubmissionId": "401",
  "captureMode": "AUTOMATIC",
  "payMethod": {
    "paymentMethodCode": "CARD_TEST",
    "paymentInstrumentToken": "synthetic-transaction-tool"
  }
}
```

支付方式和工具已按用户重构合并为必填的 `payMethod` 对象；调用方需同步请求结构，不再传顶层的方式和工具字段。Java 响应类型为 `PayResponseBody`，响应 JSON 字段保留。以上是合成合同示例，不会自动创建对应数据。金额、币种和 OE 全部来自路径指定的原 Intent。

RequestBody 的必填、格式、长度、捕获模式和 ID 上界由 Hibernate Validator 校验，失败沿用公共 `422 / VALIDATION_ERROR` 字段错误响应；JSON 类型错误、路径解析和 Header 对应关系仍属于入口协议错误。Processor 不再重复基础字段校验。

- 响应只包含 `attemptId` 和 `status`，直接取本次新建或幂等命中的 Attempt；不再查询 Action。直接执行正常结束后按 Context 中的 Attempt 状态响应，等待或未知为 `202 / PROCESSING`，终态为 200。
- 相同提交和相同内容返回原 Attempt 当前状态；改方式、capture mode 或 Token 返回 409。原提交按来源提交唯一键查询，原 Attempt 已结束或 Intent 已有另一活动 Attempt 时仍可读取。
- 已受理提交即使 Intent 已关闭或 Token 过期/撤销，仍可幂等重放；不会创建替代 Attempt。
- pay 只受理支付或返回原提交结果，不提供只读查询开关；不要求存在独立命令记录。
- `PROCESSING` 返回 202；`SUCCEEDED/FAILED/CANCELLED` 返回 200。MANUAL 授权后未收款仍为 PROCESSING，因此也返回 202，不再按授权金额设例外。响应均为 `Cache-Control: no-store`。
- 本次受理的参数错误、业务冲突等沿用公共 Problem。回显字段、金额、Action ID、失败原因、卡展示、观察时刻和交互指令均不作为当前受理回执；后续按实际执行需求扩展。

响应示例：

```json
{
  "attemptId": "501",
  "status": "PROCESSING"
}
```

## 后续流程的位置

受理事务提交后，ActionExecutionService.execute 按工具类型和入口操作从 ActionFlowFactory 取得 CARD:PAY，再创建首动作，并在同一个短事务中以 active_action_id IS NULL 和版本条件绑定执行位。原提交命中直接返回结果，受理失败直接抛出异常，均不进入 Flow。ActionFlow.execute 沿用当前 PayContext，ActionFactory 按 ActionType 获取 Actor。

首动作和后继动作创建时均为 PROCESSING，与执行位一起提交。成功创建或切换的调用方直接执行，不再经过 beginAction 启动事务；读取到 PROCESSING/UNKNOWN 不能据此再次执行。外部调用在事务外。终态结果、下一 Action 与 active_action_id 切换同事务提交，提交后循环执行下一步；等待或未知结果留在原动作。

Actor.doAction 只读原 PaymentAction，将完整执行结果放入 ActionResult；异常或缺失状态由 AbstractActionActor 转为 UNKNOWN。结果事务从 ActionResult 取更新值，确认 SQL 更新一行后 Repository 才同步原对象。具体 Actor 负责业务结果解释，Flow 负责调度；当前 CARD + PAY 的 FAILED 分支调用 attempts.updateToFail 和 intents.releaseAttempt。后继与结束处理的编码约束见[写接口规范第 12 节](development/processor-conventions.md#12-提交后的业务推进)，不能从本页推导所有后续操作使用同一终态规则。

阅读 [ActionFlow.execute](../paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/action/flow/ActionFlow.java) 查看执行和结果事务，阅读 [CardPayFlow](../paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/action/flow/CardPayFlow.java) 查看分支，各 Actor.doInTransaction 负责自己的业务结果。AUTHORIZE 成功记录授权金额，Attempt 保持 PROCESSING；PAY 成功记录收款金额并使 Attempt/Intent SUCCEEDED；本次支付失败使 Attempt FAILED 并释放 Intent 的活动尝试。Attempt 的金额、状态和清空执行位在一次更新中完成，数据库更新成功后才同步对象；Action、Attempt 和相关 Intent 写入同事务提交。详见[动作修订](../../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。

Risk、认证、资金动作的真实调用及资金引用处理保留 TODO；未接入异常由 Actor 转为 UNKNOWN，保存成功后 pay 返回 202 / PROCESSING，数据库冲突或保存失败仍向上抛出。当前接口不交付 3DS 跳转材料，也未实现 Capture/Void/Refund 或通知。不登记补偿事件或自动重试；同键重放读取原 Attempt，不重新执行。若保存上下文后尚未建动作就中断，当前也不自动补建，恢复入口另行实现。真实 Routing 启用时仍须补齐选择证据并替换固定 Mock。
