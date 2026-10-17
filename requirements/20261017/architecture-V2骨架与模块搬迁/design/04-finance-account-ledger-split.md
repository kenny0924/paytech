# 04 · 账户与会计分库方案（S1，含阶段 0 最小计费事实）

依据 D05、D16、D36（修订）与 C1（用户已定：阶段 0 引入最小计费事实，账户从一开始只消费经济事实）。现状见 [analysis/finance.md](../analysis/finance.md) 第 3、4、9 节：`t_account` 同时是过账科目账户与余额投影，六处事务同时写分录与余额。

## 1. 目标与原则

- **计费（billing）是唯一经济事实来源**：任何会改变 Client / 合作伙伴 / 平台账户余额的金额，先在 billing 落为不可变、带版本的经济事实并发布事件。
- **会计（ledger）** 消费经济事实自动过账，并拥有账簿、科目、期间、分录、调整、试算、关账与科目账户身份；会计内部的结转、重开、纯科目重分类不改变 Owner 余额。
- **账户（account）** 只消费经济事实维护余额桶，是可用额度的实时权威；不消费过账事件。
- 两者独立库、独立进程；一致性靠同一经济事实 ID 的幂等消费加周期核对（D05）。

## 2. 阶段 0 的最小计费（billing-service）

现有 `t_transaction_economics`、`t_transaction_economic_component`（有读代码、无生产写入）与 `t_transaction_economic_comp_link` 迁入 `mor_billing` 并成为经济事实模型：

| 表 | 用途 | 变化 |
|---|---|---|
| `t_transaction_economics` → 更名 `t_economic_fact` | 经济事实头：`fact_id`、`fact_type`（PAYMENT / REFUND / DISPUTE / PAYOUT_FEE / **MANUAL_ADJUSTMENT** / COST_ADJUSTMENT）、来源引用、Client / 合作伙伴 / OE / Finance Entity、币种、`fact_version`、`supersedes_fact_id`、`status` | 新增 `fact_type = MANUAL_ADJUSTMENT`、`supersedes_fact_id`、`status` |
| `t_transaction_economic_component` → 更名 `t_economic_fact_line` | 明细行：`fee_type_code`（沿用 `AmountSource` 代码）、付方 / 收方角色与主体、金额、方向 | 不变 |
| `t_transaction_economic_comp_link` → 更名 `t_economic_fact_line_link` | 更正链 | 不变 |
| `t_channel_cost_plan*` 四表 | 渠道成本配置（已实现） | 随 billing |

阶段 0 实现的最小能力：

- `POST /billing/v1/economic-facts`：内部命令，登记一条 `MANUAL_ADJUSTMENT` 或 `COST_ADJUSTMENT` 经济事实（Maker 提交、Checker 批准两步，复用现有人工调整的 Maker-Checker 语义；工单引擎未就绪前由两个内部接口承担）。
- `GET /billing/v1/economic-facts/{factId}`。
- 事件 `EconomicFactRecorded`（聚合键 = `fact_id`，版本 = `fact_version`）：Payload 含头与全部明细行，消费者无需回查。
- 现有 `TransactionPostingServiceImpl` 的"billing 不变量"（合作伙伴三元组全有或全无、平台服务费主体等于 Finance Entity）移到 billing 的登记校验中。

支付、退款、争议、出款费用的经济事实生成属业务切片，阶段 0 不实现；但模型、事件与消费者在阶段 0 已就位，后续只是增加事实来源。

## 3. 表变更

### 3.1 ledger 库（`mor_ledger`，原 `mor_finance` 改名）

- `t_account` → 更名 `t_ledger_account`，**删除**余额列 `debit_total`、`credit_total`、`net_balance`、`last_journal_sequence`、`balance_version`；保留身份列。`id` 继续作为全局账户 ID。
- `t_accounting_instruction.transaction_economics_id` → 更名 `economic_fact_id`，只存 ID。
- 其余 ledger 表不变。

### 3.2 account 库（`mor_account`，新建）

- `t_account_balance`：`account_id`（= ledger 账户 ID，主键）、`ledger_book_id`、`owner_type`、`owner_id`、`balance_bucket`、`currency`、`debit_total`、`credit_total`、`net_balance`、`balance_version`、`last_fact_id`、`last_fact_version`、`status`、时间列。唯一 `(ledger_book_id, owner_type, owner_id, balance_bucket)`。
- `t_balance_applied_fact`：`fact_id`、`fact_version`、`applied_time`、`lines_hash`；唯一 `(fact_id, fact_version)`。
- `t_balance_movement`：账户侧桶移动命令记录（阶段 0 只建表，无生产调用方）。
- `t_consumed_event`（messaging 标准表）、`t_compensation_event`。

## 4. 事务拆分

| 现有事务 | 拆分后 |
|---|---|
| `JournalPostingServiceImpl#post`：锁账户 → 写分录 → 更新余额 → 指令 POSTED | ledger 本地事务：校验期间与账簿 → 取序号 → 校验账户存在且 ACTIVE（不锁余额、不更新余额）→ 平衡校验 → 写分录 → 指令 POSTED。**不再写任何余额。** |
| `TransactionPostingServiceImpl#post`（自动记账） | 成为 ledger 的 `EconomicFactRecorded` 消费 Handler：按规则包生成指令并过账，一个 ledger 本地事务；billing 不变量校验移走；`resolveAccount` 开账户仍在 ledger（科目账户身份） |
| `ApproveManualAdjustmentProcessor` | 拆两类：**影响 Owner 余额的调整**不再允许直接在 ledger 提交，必须经 billing 登记 `MANUAL_ADJUSTMENT` 经济事实（ledger 校验：调整行涉及 `owner_type ∈ {CLIENT, LOCAL_PARTNER}` 的账户即拒绝）；**纯科目重分类**保留在 ledger，只写分录 |
| `StartPeriodCloseProcessor#postBookProfitClose`、`ApprovePeriodCloseProcessor#approveReopen` | ledger 本地事务，只写分录与期间；损益结转不涉及 Owner 余额桶 |
| `TrialExceptionServiceImpl#approveProjectionRebuild` | 分裂为：ledger 侧试算异常处置只写 `t_trial_balance_exception`；account 侧新增命令 `POST /balances/v1/books/{bookId}/rebuild`，从 billing 重放经济事实（`GET /billing/v1/economic-facts?bookId=&fromFactId=` 分页）重算余额 |

## 5. 事件与消费

### `EconomicFactRecorded`（billing → ledger、account）

- ledger 消费：按 `fact_id + fact_version` 幂等；生成指令、过账；失败按补偿事件重试；过账结果不回写 billing。
- account 消费：按 `fact_id + fact_version` 幂等；把明细行映射为账户余额变化（映射规则 = 现 `AccountIdentityRule` 搬到 account：`fee_type_code` × 付方 / 收方角色 → `owner_type / owner_id / balance_bucket`）；`strictOrder = false`（不同事实之间无顺序依赖，同一事实的版本递增由版本判定）。
- 两边都读同一份 Payload，金额与舍入由 billing 一次决定（D16）。

### `BalanceMoved`（account → ledger，阶段 0 只定义 Schema）

桶移动（AVAILABLE → LOCKED 等）由 account 作为命令 Owner 执行并发布，ledger 消费补记分录（`sourceType = BALANCE_MOVEMENT_ECHO`）。阶段 0 无生产调用方。

## 6. 跨服务读与关账

| 现有跨 core 读 | 方案 |
|---|---|
| `AccountMapper.selectOwnerBalances` 等联 `t_chart_account` | account 自存 `chart_account_id`，科目名经 `ledger-api` 批量读或 BFF 聚合 |
| `selectProjectionChecks`、`selectTrialAggregates`、`selectProfitCloseBalances` 读余额列 | ledger 全部改为按分录聚合；需与余额比对时调 `account-api` |
| 关账步骤 2 统计全库待处理补偿事件 | ledger 统计本库；并调 `account-api` `GET /balances/v1/books/{bookId}/consistency` 与 `billing-api` `GET /billing/v1/economic-facts/pending?bookId=`，要求 account 已消费 billing 已发布的全部事实（按最大 fact_id 比对）；否则关账失败关闭 |
| 关账步骤 3 统计 CHANNEL_ACCOUNT / CASH_ACCOUNT 非零余额 | 同一 consistency 接口返回按 owner_type 的非零账户数 |
| `BalanceQueryServiceImpl` 读账簿与科目 | 经 `ledger-api`，失败关闭 |
| `TransactionPostingServiceImpl` 事务内读 `t_channel_account`（reconciliation） | 渠道清算账户的 ID 由 billing 在经济事实头中给出（`channel_account_id`），ledger 不再读 reconciliation |

## 7. 核对

account 新增日终核对任务：按账簿比对 `t_account_balance` 与 `ledger-api` `GET /ledger/v1/books/{bookId}/account-totals`（按分录聚合的 Owner 账户余额），差异写 `t_balance_discrepancy` 并告警；阶段 0 建表与任务，不接工单。差异裁决依据是 billing 的经济事实（D05）。

## 8. 接口清单

- billing：`POST /billing/v1/economic-facts`（提交）、`POST /billing/v1/economic-facts/{id}/approve`、`GET /billing/v1/economic-facts/{id}`、`GET /billing/v1/economic-facts?bookId=&fromFactId=`、`GET /billing/v1/economic-facts/pending?bookId=`；现有 `/channel-cost-plans/v1` 迁入。
- ledger：`GET /ledger/v1/books/{bookId}`、`GET /ledger/v1/chart-accounts?ids=`、`GET /ledger/v1/books/{bookId}/account-totals`；现有 accounting 合同迁入；人工调整接口增加"不得涉及 Owner 账户"校验。
- account：`GET /balances/v1/clients/{clientId}`、`/partners/{partnerId}`（迁入）、`GET /balances/v1/books/{bookId}/consistency`、`GET /balances/v1/books/{bookId}/accounts`、`POST /balances/v1/books/{bookId}/rebuild`。
- 事件：`EconomicFactRecorded`、`BalanceMoved`（Schema）。

## 9. 测试门禁

- 现有 Finance 236 例按归属拆分（design/10）：过账、规则包、关账、试算、期间、账簿与科目去 ledger；渠道成本与经济事实去 billing；余额查询去 account。
- 原 `TransactionPostingTest` 改为 billing 登记事实 → ledger 消费过账 → account 消费余额的三服务链路测试（各自 `-test` 用 DTO Mock 与本地 Broker）。
- 新增：同一事实重复投递只应用一次；版本 2 事实覆盖版本 1；ledger 拒绝涉及 Owner 账户的直接人工调整；关账在 account 未追平时失败关闭；重建后余额与 ledger 分录和一致；核对任务发现人为制造的差异。

## 10. 风险

- 阶段 0 范围因 C1 增加了 billing 最小实现（两张事实表的更名与补字段、两个内部接口、一个事件、两个消费者），估计一到两个执行步的工作量；它同时让计费切片的起点从"新建"变为"扩展事实来源"。
- 人工调整路径的业务语义变化（影响余额的调整必须经计费）需要在运营后台文案中体现；阶段 0 后台不改，仅接口返回明确错误码。
