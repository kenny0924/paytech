# Execution 数据方案

> **交易级 Token 修订评审：** 用户已确认统一 pay、交易级工具 Token、Attempt 直接关联及删除旧 Instrument 的方向；详细合同见[修订稿](./instrument-token-amendment.md)。下文保留上一版合同与评审上下文，受影响部分以修订稿的准确取代范围为准；新合同的实现与验证状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

> 阶段 3 已获用户确认，当前进入阶段 4 Execution 本地切片研发；其他 Owner 仅测试 Mock + TODO。实际实现与验证边界见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)，阶段确认见[交付记录](./delivery.md)。

## 1. 表取舍与结构增量

| 表 | Owner | 本稿处置 |
|---|---|---|
| `t_payment_intent_binding` | Execution | 新增 8 字段，2 个唯一二级索引；无枚举状态 |
| `t_payment_intent` | Execution | 结构保留；唯一来源、活动/成功指针、关闭与成功语义明确 |
| `t_payment_attempt` | Execution | 新增 4 字段、1 个唯一索引；来源提交直接在此去重 |
| `t_payment_action` | Execution | 新增 9 字段、3 个普通索引；4 个旧字段注释更新 |
| `t_payment_customer_action` | Execution | 删除整表 10 字段、2 个二级索引和主键；必要交互事实移入 Action |
| `t_channel_transaction_ref` | Execution | 结构保留；只保存成功资金动作引用，不存认证/查询引用 |
| `t_payment_instrument` | Execution | 结构保留；可选、稳定 Channel 工具引用才建行 |
| `t_payment_pricing_fact` | Execution | 结构保留；成功后的完整不可变定价事实 |
| `t_payment_refund_position` | Execution | 结构保留；成功支付时创建、退款时原子预占 |
| `t_channel_refund` | Execution | 结构保留；以 Commerce Refund ID 唯一绑定 |
| `t_duplicate_payment_case` | Execution | 结构保留；第二笔真实成功及其补偿事实 |
| `t_payment_state_transition` | Execution | 结构保留；已有业务聚合状态变化留痕，不记录流程节点 |
| `t_command_idempotency` | Web | 结构保留；短事务受理结果，不承担远程资金执行 Lease |
| `t_compensation_event` | Web | 对齐 Foundation：新增 1 字段、删除 6、结构修改 10；替换 3 个二级索引 |

合计新增 1 表 8 字段；3 张旧表新增 14 字段、修改 10 个字段结构、删除 6 字段；另删除 1 表的 10 字段。Action 4 个注释修改单独计数。二级索引新增 9、删除 5；主键新增/删除各 1。业务、路由/权限/控制台注册 DML 均为 0；没有表重命名或数据回填 SQL。

## 2. 来源绑定表

| 字段 | MySQL 类型 / 可空 | 写入与用途 |
|---|---|---|
| payment_intent_binding_id | BIGINT / 否 | Snowflake 主键 |
| source_type | VARCHAR(64) ascii_bin / 否 | 当前 `COMMERCE_ORDER`，受控来源命名空间 |
| source_id | BIGINT / 否 | Commerce Order ID；与 source_type 一起唯一 |
| client_id | BIGINT / 否 | 校验来源/资源归属，不允许从另一个 Client 抢占绑定 |
| payment_intent_id | BIGINT / 是 | 已创建 Intent 时必填；关闭先到时为空 |
| closed_at | DATETIME(6) / 是 | 不可撤销关闭新建、新 Attempt、首次 AUTHORIZE/SALE 的资格；不表示已有资金被取消 |
| create_time | DATETIME(6) / 否 | UTC，应用写入 |
| update_time | DATETIME(6) / 否 | UTC，应用写入 |

无业务默认值。新增主键、`uk_pay_binding_source(source_type,source_id)`、`uk_pay_binding_intent(payment_intent_id)`。唯一来源覆盖“没有 Intent 行”的关闭；MySQL nullable unique 允许多个已关闭且无 Intent 的来源。关联由应用校验，不建 FK/CHECK。

允许三种提交后形态：已绑定且开放、已绑定且关闭、未绑定且关闭。`payment_intent_id` 和 `closed_at` 同时为空不得提交。Create/Close 使用同一来源锁与数据库唯一约束；不存在的行由唯一 insert 竞争，失败者重新读取。所有资格检查只在本地事务外取得外部事实，事务内重新核对本地版本。

**关闭不是撤销资金。** 已创建 Intent 的重复 Create 仍返回原 Intent；关闭后不能新建或恢复首次资金发送，但原已提交资金必须查询确认。已经存在的成功授权仍可通过明确 Capture 或 Reversal 处理，Close 不暗中 Void。Terminate 只有确认无成功 AUTHORIZE/CAPTURE/SALE 且无提交未决资金才能返回 TERMINATED。Reversal 创建 Void 后，原授权永久禁止再创建 Capture，即使该次 Void 明确失败也只能重试 Void或人工处理。

绑定关闭只能从 null 变时间；无业务状态枚举，不写状态历史。使用行锁与唯一约束已足够，不额外机械增加 version。此记录与来源 Intent 一起保留，不能随技术幂等记录清理。

## 3. Attempt 新增字段

| 字段 | 类型 / 可空 | 规则 |
|---|---|---|
| source_submission_id | BIGINT / 是 | 新执行必填，Commerce 命令 ID；一提交最多一个 Attempt；新唯一索引 `uk_pay_attempt_submission` |
| payment_goal | VARCHAR(32) ascii_bin / 是 | 新执行必填且不可变；`PAY_NOW / AUTHORIZE_ONLY` |
| authentication_requirement | VARCHAR(32) ascii_bin / 是 | Risk 允许后必填且冻结；`NOT_REQUIRED / THREE_DS_REQUIRED / CHALLENGE_REQUIRED`；Risk 尚无确定结果时为空 |
| card_reference | VARCHAR(128) ascii_bin / 是 | 新 Card 执行必填、稳定、非 bearer 的受控 Card 引用；其他未来方式的可空含义须随其用例评审 |

全部默认 NULL 是对现存行的结构兼容，**不是允许新 Card 写入缺失数据**。历史行缺少目标、提交身份时不得猜测回填或走新恢复流程；需要独立迁移。source_submission_id 当前只接收受认证 Commerce 的全局命令 ID；未来出现第二种来源必须先评审命名空间，不把任意调用方 ID 混入此唯一索引。

现有 `transaction_assessment_id` 在发起 Risk 前分配并冻结，Risk 使用它幂等恢复；`risk_result` 表示评估结果，不能替代认证要求。`order_version`、三条接受 ID、接受时间及 consumer_contract_id 在首次受理冻结。平台 Card 引用不能写入 `channel_instrument_ref`。

## 4. Action 新增字段

| 字段 | 类型 / 可空 | 必填条件与用途 |
|---|---|---|
| target_payment_action_id | BIGINT / 是 | QUERY 必填，指向被查询原 Action；资金操作通过原资金交易引用导航，不用 QUERY 的结果代替原交易 |
| authentication_action_id | BIGINT / 是 | 需要认证的 AUTHORIZE/SALE 必填；同 Attempt 的成功 AUTHENTICATE，其他类型为空 |
| channel_operation_ref | VARCHAR(255) ascii_bin / 是 | 渠道已返回稳定非 bearer 操作/认证上下文引用时保存；唯一性范围由渠道定义，不假设全局唯一 |
| interaction_type | VARCHAR(32) ascii_bin / 是 | 等待消费者时必填；受控目录 `REDIRECT / THREE_DS_CHALLENGE / SDK / APP_SWITCH / QR_CODE`，本期只写 THREE_DS_CHALLENGE |
| interaction_expires_at | DATETIME(6) / 是 | REQUIRES_ACTION 时必填；等待指令期限，终态后保留历史值 |
| authentication_outcome | VARCHAR(32) ascii_bin / 是 | AUTHENTICATE 收到可信结论后必填：`AUTHENTICATED / ATTEMPTED / FAILED / UNAVAILABLE`；不直接透传协议码 |
| challenge_performed | TINYINT / 是 | 认证可信结论具有证据时为 0/1；未知为空，不把没有回跳解释为未挑战 |
| authentication_valid_until | DATETIME(6) / 是 | 成功 AUTHENTICATE 必填，认证证明有效期；与交互等待到期不是同一事实 |
| status_reason_code | VARCHAR(64) ascii_bin / 是 | 失败/未知/受控等待的安全原因，非自由文本或远程原始错误 |

新增普通索引：`idx_pay_action_target_action(target_payment_action_id)`、`idx_pay_action_authentication(authentication_action_id)`、`idx_pay_action_operation(channel_account_id,channel_operation_ref)`。既有唯一 Action key、父对象 action_no 索引保持。

旧字段仅更新以下 4 个注释，类型/可空/默认值保持：

| 字段 | 新语义 |
|---|---|
| action_type | 原类型新增 AUTHENTICATE；仍无 REVERSAL |
| target_channel_txn_ref_id | CAPTURE/VOID 指成功 AUTHORIZE；REFUND 指 SALE/CAPTURE；QUERY 可在原成功引用存在时附带，始终以 target_payment_action_id 定位 |
| amount | AUTHENTICATE/QUERY 保存其绑定操作金额用于一致性校验，不因此形成资金事实；本期仍为正金额 |
| channel_idempotency_key | 一次语义动作的稳定平台 key；存在该字段不代表渠道承诺幂等，重发还需适配器能力证明 |

父对象仍严格 XOR：`payment_attempt_id / channel_refund_id / duplicate_payment_case_id` 恰有一个非空。QUERY 复制被查询 Action 的父对象、渠道坐标与金额币种，不能查询另一 Intent。每次有限的查询尝试可以建独立 QUERY Action；同一次查询传输重试沿同一个 QUERY Action。

认证严格绑定同 Attempt、card_reference、金额/币种、Channel/Account/MID；AUTHENTICATE 后的资金动作复制认证 Action 已冻结坐标，不重新路由。`THREE_DS_REQUIRED` 只接受 AUTHENTICATED；`CHALLENGE_REQUIRED` 额外要求 challenge_performed=1。仅 ATTEMPTED 不满足本期强制认证要求；可信无感成功不满足强制挑战时记录实际 outcome，但 Action FAILED，原因 REQUIREMENT_NOT_MET。

交互指令只存在于即时响应；channel_operation_ref 必须不能凭持有绕开调用方/账户授权。不能满足这一点的渠道引用不允许明文入表，需由 Channel/Card Owner 提供安全间接引用后再接入。本期 Mock 用合成引用。删除交互表后 completion 由原 Action 终态、channel_result_time 和状态历史表达，不再重复一套交互状态机。

## 5. 保留表的关键约束

| 对象 | 本稿明确的使用规则 |
|---|---|
| Intent | 一来源一 Intent；active_attempt_id 占用包含等待、UNKNOWN、AUTHORIZED；只有确定可结束后释放。successful_attempt_id 只在第一笔真实收款成功时冻结，不被第二笔成功覆盖 |
| Transaction Ref | 成功 AUTHORIZE/CAPTURE/SALE/VOID/REFUND 才建；同原 Action/渠道结果去重；实际交易金额/币种核对。不以 AUTHENTICATE、QUERY 或浏览器回跳创建资金引用 |
| Instrument | 有稳定 Channel 工具引用才建可选行；没有引用可以返回当前请求已知 last4，但不为展示伪造 Instrument，也不承诺重启查询必有 last4 |
| Pricing Fact | 成功后字段齐备才插入一次；consumerCountry 来自固定 Commerce context；实际认证分类由 Channel 可信事实确认，不能从“没有 Challenge”推断 NON_THREE_DS |
| Refund Position | canonical SALE/全额 CAPTURE 成功时创建；`reserved + refunded ≤ original`，退款 UNKNOWN 不释放 reserved |
| Channel Refund | 先在本地事务创建并预占，再外部发送；同 commerceRefundId 同内容返回原 Refund，异内容冲突 |
| Duplicate Case | 第二笔真实成功有不同资金动作/引用才建；正常 AUTHORIZE→CAPTURE 不是重复支付；一 duplicateAction 最多一 Case |
| State Transition | 只对真实发生的业务状态变化追加；版本/CAS 决定一次生效，重投事件不新增相同变化；无流程节点、CVV、交互指令或卡引用 |

## 6. 补偿表兼容差异

当前 Payment 完整 Schema 仍含 execution_type、source_*、schema_version、payload_hash，且 event_key 全局唯一，与[现行补偿规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)的 `(event_key,event_type,sub_event_type)` 不一致。不能直接挂接现行 JAR 并假设兼容。

候选迁移仅改 Payment 自己的 Web 技术表：

- 新增 `sub_event_type VARCHAR(64) utf8mb4_0900_bin NOT NULL`。
- 删除 `execution_type, source_type, source_id, source_version, schema_version, payload_hash` 六字段。
- 修改十字段：event_key/event_type 改大小写敏感；payload_json 改 nullable VARCHAR(4096)；execute_status 改 VARCHAR(16) ascii_bin、默认 INIT；execute_count 默认 0；next_execute_at 默认当前微秒；last_error_summary 改 VARCHAR(1000)；lock_version 默认 1；create_time/update_time 默认当前微秒。
- 删除旧三个索引，新增唯一 `uk_comp_event_identity(event_key,event_type,sub_event_type)` 与 `idx_comp_event_ready(execute_status,next_execute_at,id)`、`idx_comp_event_lease(execute_status,lock_until,id)`。

这不是 generic Inbox/Outbox，也不是 Foundation 命令幂等消费方迁移。成功补偿记录删除；业务事实长期由业务表负责。payload_json 默认 NULL，只按 event_key 重读本库事实；4 KiB **UTF-8 字节**限制由框架校验，不能仅依赖 VARCHAR 字符长度。本切片不新增人工重开 HTTP 或审计表；FAILED 记录告警并保留，未来开放人工重开前必须补齐规范要求的审计。

## 7. 迁移、历史与恢复边界

DDL 文件性质：**待评审增量草稿**，目标仅为未来明确指定的 Payment 服务本地库；当前没有连接或核验任何真实库，因此不声明它为空，也没有 SQL 执行授权。脚本不含 IF EXISTS/自动跳过，不可重复执行；应用启动不建表、不迁移、不初始化。

执行前置是：本设计获批、PDManer/生成器/完整 Schema 同步核对、停止写入，并确认涉及的新执行业务表与旧补偿表无历史数据、无待处理任务。若任一存在数据，本稿删除/收窄语句**不得执行**；必须另做历史迁移，逐笔迁移交互引用和期限、来源绑定、原提交身份，排空或按旧 Handler 完成旧补偿任务，不能猜值回填或丢弃 UNKNOWN。

执行顺序：先备份并验证可恢复 → 创建来源绑定 → 加 Attempt/Action 字段和索引 → 对齐补偿表 → 核验交互承接 → 删除空 Customer Action 表 → 核对模型/Schema/索引/注释。MySQL DDL 不保证整包原子，任一步失败停止，依据已完成语句检查，不盲目重跑。

回退只允许在尚无新写入时按验证过的备份恢复并匹配旧代码；出现新执行后不能直接 DROP 新字段或恢复旧备份覆盖事实。需要停止新受理、保留所有支付/退款/UNKNOWN 记录并单独评审前向修复。具体库名、备份路径、执行窗口由后续执行授权明确，本轮不制造连接信息或可误用的一键迁移命令。
