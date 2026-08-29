# Finance 平衡型物理模型设计

> 历史基线：本文件记录 126 表的首版设计，已被 [Finance 统一账户模型设计](./2026-08-26-finance-unified-account-model-design.md)、后续 [Account Balance 合并](./2026-08-26-finance-account-balance-merge-implementation-plan.md)以及[交易经济与会计规则精简](./2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md)取代。当前实现、评审和后续任务必须以 121 表当前模型、`FINANCE_DATABASE.md` 与生成的 PDManer 为准；不得从本历史文件恢复已删除的双账户、双余额、独立 `t_account_balance` 或旧 Economics/Accounting Rule 结构。

最后更新：2026-08-26  
状态：历史设计，仅用于追溯第一版决策

## 1. 目标与范围

本设计把已经冻结的 Finance V1 逻辑模型落成一个独立数据库的物理模型。模型覆盖交易经济计算、多国税务、复式账本、余额成熟、准备金、追偿、统一资金承诺、Provider 清算、银行对账、Client/Partner 结算、人工付款和期间关账。

本次授权覆盖：

- Finance 逻辑数据库文档的物理化收口；
- 确定性 PDManer 生成器、独立契约测试和现实场景文档；
- `finance-service/docs/database/finance.pdma.json`；
- 为保持跨系统一致性而必须同步的需求、序列和文档导航。

本次不创建 Java 模块、Migration、数据库实例、运行环境或外部资源，不修改共享 PDManer 模板，也不把模型发布到任何环境。

## 2. 方案判断

| 方案 | 规模 | 特点 | 结论 |
|---|---:|---|---|
| 过度扩展型 | 130 表以上 | 在当前模型外继续增加通用 History、Inbox/Outbox、工作流、银行自动付款和未出现的跨主体往来结构 | 存在超前设计，不采用 |
| 平衡型 | 126 表 | 保留承担会计、税务、资金、对账和审批不变量的独立事实；科目表使用稳定 Root 和直接父子关系，自动记账使用结构化规则版本，不新增闭包表、规则制品、跨主体转移、通用历史、跨库投影或自动付款基础设施 | 采用 |
| 激进型 | 约 90–105 表 | 合并规则版本、税务证据、资金移动、结算候选、付款事件和对账证据 | 会削弱法定证据、资金守恒、UNKNOWN 恢复或 Maker-Checker，不采用 |

表数量不是本次优化的首要指标。只有职责、生命周期、唯一性和审计口径都相同的结构才允许合并；Journal、Movement、Event、Approval、Snapshot、Reconciliation Evidence 不因字段相近而合并。

## 3. 物理子域与 126 表

### 3.1 会计基础与多级科目（9）

币种规则、稳定科目表、直接父子科目、账簿、总账科目、明细子账、财年、会计期间和账簿序号。账簿以 `Operating Entity + currency` 隔离；Journal 序号在账簿内严格递增。多级汇总使用递归 CTE 或应用遍历，不维护第二套科目闭包表。

### 3.2 税务、经济计算与 Provider Cost（36）

覆盖多国税务辖区、税务机关与注册、规则/税率/证据、不可变 Tax Snapshot、Fiscal Record、Tax Return/Payment，以及 PAYMENT、REFUND、DISPUTE、COST_ADJUSTMENT 四类 Economics Snapshot 和预计/实际 Provider Cost。

### 3.3 记账指令与总账（12）

会计规则发布、Accounting Instruction、Instruction Line、不可变 Journal Entry/Line 和固定双人审批的 Manual Adjustment。规则版本通过 `engine_contract_version` 选择代码内固定解释协议，不部署 SQL、脚本或外部规则制品；V1 不预建跨主体转移。Journal 是唯一会计事实，余额表仅为可重建投影。

### 3.4 余额、准备金、追偿与资金承诺（22）

Balance Lot 的 `PENDING/AVAILABLE/RESERVED/LOCKED` 四桶守恒、可用性计算、Risk 准备金、Client Recovery Receivable、Funding Position/Commitment/Allocation/Move，以及退款资金预占。

### 3.5 Provider 应收、现金账户与对账（16）

Provider 应收/退款与争议扣款/重复扣款项目、Provider Settlement Batch、预期现金项、平台共享银行账户、银行流水和 Reconciliation。V1 按每个 MID 的单一净现金腿建模，支持批次净 Credit、净 Debit 和零现金；Provider 清算匹配只确认渠道结算，只有银行 Credit/Debit 匹配才确认现金真实到账或扣款。

### 3.6 收款档案、结算与人工付款（21）

Beneficiary Account、Settlement Profile、Client/Partner Payout Request、Risk Restriction Projection、Settlement Run/Candidate、结算义务、双人审批、取消、顺序付款 Attempt、银行事件与付款证据。

### 3.7 试算与期间关账（8）

Trial Balance、异常、Period Close/Reopen、逐账簿关账范围、固定七步流程、双人审批和损益结转规则。一个期间可以包含多个 OE/币种账簿，因此每个账簿分别冻结 Journal Sequence 水位、最终试算和结转指令；OPEN Trial Exception 必须阻止关账。

### 3.8 本地可靠性与命令幂等（2）

`t_compensation_event` 只负责本服务提交后 MQ 发布或本地恢复；`t_command_idempotency` 负责同步写命令。V1 不建立通用 Inbox/Outbox 或中央补偿库。

## 4. 本次落模前必须收口的契约

### 4.1 删除失效的 Payment 退款尝试引用

Payment 已把旧 `channel_refund_attempt` 合并到 `t_channel_refund`。Finance 使用统一的 `t_provider_debit_item` 表示 Client 退款、重复扣款补偿退款或争议形成的 Provider 扣款事实；三个来源分支严格互斥，每笔 Debit Item 唯一绑定对应的 REFUND/DUPLICATE_COMPENSATION/DISPUTE Economics Snapshot，不恢复旧 Refund Attempt ID。

### 4.2 Reserve Requirement 冻结完整事件证据

`t_reserve_case` 增加 `source_event_key`、`source_version`、`source_payload_hash`。同一来源版本的 Payload 不一致必须拒绝，旧版本不得覆盖新版本；Risk ID 仅是跨库稳定引用，不建外键。

### 4.3 Platform 文件统一使用二元组

所有文件引用都显式保存 `file_id + file_content_hash`。业务内容 Hash 或来源 Hash 不能替代 Platform 返回的文件内容 Hash。该规则覆盖 Tax Return、Manual Adjustment、Provider Settlement Batch、Bank Statement、Settlement Cancel 与 Settlement Evidence。

### 4.4 Vault 使用 Finance 接收方绑定

若 Buyer Tax ID 来自 Commerce，FIN-I17 必须先以最终 Finance 绑定身份和命令幂等键同步执行 Vault `Prepare/CopyForBinding`，再在本地事务写不可变 Tax Snapshot 与 Command Idempotency，提交后同步 Confirm；只有 Token 为 ACTIVE 才返回成功。同命令重试恢复 Confirm 或返回既有 Snapshot，回滚后的 Pending 引用 Retire/Reconcile，Reveal 对非 ACTIVE Token Fail-Closed。不能保存或转发 Commerce Token；Token、Blind Index/Key Version、Masked Value、Vault Record Version 和 Metadata Hash 必须来自同一次受控响应并成组冻结。

### 4.5 税务报告币种可重建

Tax Registration 固定法定报告币种；Tax Line 同时冻结交易币种 Tax Basis 与报告币种 Reporting Tax Basis，零税率外币行也能参与 Liability Summary。V1 一个 Tax Snapshot 内全部 Registration/Line 的报告币种必须与 Header 相同，否则拒绝计算；Tax Liability Summary 使用完整税务维度唯一键防止重复计入 Return。

## 5. 关键物理原则

### 5.1 会计恒等式和唯一事实源

- 每条 Journal 在单一账簿和币种内借贷绝对相等。
- Journal Entry/Line 只追加；`REVERSAL` 只用于一整条原分录的逐行严格反向冲销，部分退款或非完整镜像调整使用新的 `STANDARD/ADJUSTMENT` 分录。
- Account/Subledger/Period Balance 可以从 Journal 重建，不得成为第二事实源。
- Revenue、Expense、Asset、Liability、Equity 五类科目和多级父子科目全部保留；整套 Chart 不做 V1 业务版本化，一旦被账簿采用，既有科目的代码、父级、类别、正常方向和 `posting_allowed` 均冻结，只允许追加新科目，账簿内停用由 Ledger Account 状态表达。
- 每笔业务事实和 Journal 只属于一个 Operating Entity；V1 不建立跨主体往来科目、净额或 Transfer，未来出现真实合同与资金流后再专项设计。

### 5.2 资金守恒和并发顺序

- `sum(balance_lot_bucket.amount) = balance_lot.open_amount`。
- 活动 Funding Allocation 合计不得超过 Funding Position 容量。
- Refund、Settlement、Tax Payment 的 Commitment 必须由对应角色的 Allocation 等额覆盖。
- 同一事务更新 Lot/Position/Allocation/Movement/Journal/可靠事件；多 Position 按 ID 全序加锁。
- Funding Allocation 只使用 `ACTIVE/CONSUMED/RELEASED` 三态，并与 consumed/released/remaining/active slot 精确对应；V1 不保留没有确定结算语义的部分终态。
- `UNKNOWN/RESULT_UNKNOWN` 表示结果未知而不是失败，不能释放资金或启动下一次付款。
- Settlement 的 `fee_bearer` 决定现金占用：BENEFICIARY 时 `net=gross-fee`、`cash_required=net`；PLATFORM 且费用随同一次付款扣收时 `net=gross`、`cash_required=gross+fee`。

### 5.3 快照只冻结历史解释所需事实

Tax Snapshot、Economics Snapshot、Fiscal Record、Settlement Requirement Snapshot 保存交易时点或审批时点的必要证据；不复制完整上游对象。Finance 通过稳定 ID 精确查询 CCT/Commerce/Payment/Risk，再冻结实际使用的字段、版本和计算参数。

### 5.4 Version、Hash 与 Active Slot

- 只在真实并发 Root、不可变规则版本、事件来源完整性、幂等请求或内容审批中保留 Version/Hash。
- Accounting Rule Version 的 Content Hash 精确覆盖 `bundle_id + bundle_ver + engine_contract_version + effective_from + ordered mappings/conditions`，排除失效时间、状态、审批人和审计字段；失效时间仅在原子切换至已批准替代版本时设置且不可再次修改。
- `active_slot` 只保留在 Funding Allocation、Settlement Line、Settlement Cancel 与 Period Close 四个必须由数据库保证活动唯一性的地方，并采用 `1/NULL` 语义。规则版本和付款 Attempt 由 Root 的 `current_ver_id/current_attempt_id` 作为唯一当前权威，不能同时维护第二套活动槽。
- 状态历史使用领域 Event/State Change；不新增通用 BCR 或全表 History。

### 5.5 跨系统边界

- CCT、Commerce、Payment、Risk、Platform 和 Vault 标识均不建物理外键。
- PaymentSucceeded 先与 COM-I04 对 Order/Context/OE/Assignment、行业/MCC、金额和币种逐字段交叉校验，再按 CCT-I14 精确读取历史 Scope。
- 暂时依赖失败不 ACK，等待 Broker 重投；永久完整性错误进入 DLQ，不能创建半成品 Economics、Instruction 或 Journal。

## 6. 八个 Diagram Group

1. `ledger_and_close`：账簿、科目、Journal、Trial Balance 和 Period Close。
2. `tax_and_fiscal`：税务规则、Tax Snapshot、Fiscal Record、Tax Return 和 Tax Payment。
3. `economics_and_cost`：Economics Snapshot/Component、Provider Cost Plan/Confirmation。
4. `balance_and_funding`：Balance Lot、Availability、Reserve、Recovery、Funding。
5. `provider_cash_recon`：Provider Open Item、Settlement Batch、Cash/Bank、Reconciliation。
6. `settlement_obligation`：Beneficiary/Profile、Payout、Restriction、Candidate、Settlement/Approval/Cancel。
7. `settlement_payment`：Settlement Payment/Attempt/Event/Evidence、Funding 和 Bank Match。
8. `reliability`：Command Idempotency 和 Compensation Event。

每张表恰好有一个主图组；跨组只作为阅读锚点重复展示。每条本库关系只在子表主图组绘制一次。

## 7. 物理规范与交付门禁

- 全部表名以 `t_` 开头；唯一索引以 `uk_` 开头；普通索引以 `idx_` 开头。
- 表名、字段名和说明使用直接中文；闭合枚举列全值，开放代码注明权威来源。
- 主键为服务侧 Snowflake `BIGINT`；金额为 `DECIMAL(24,8)`；比例和汇率为 `DECIMAL(18,10)`；UTC 时间为 `DATETIME(6)` 并映射 Java `Instant`，无时区业务日期为 `DATE` 并映射 `LocalDate`；币种为 ISO 4217 `CHAR(3)`。
- PDManer 模型不保存数据库连接、凭证、真实敏感值、二进制文件、URL 或跨库 FK。
- 生成器必须支持 `--check` 字节校验；测试 Oracle 与生产 Manifest 相互独立，并校验精确表/字段/索引/关系、中文元数据、枚举、CHECK、场景和禁止项。
- 自动 JSON/结构校验通过后，PDManer 4.9.3 应用内打开、关系图和规范检查仍由用户人工完成。
