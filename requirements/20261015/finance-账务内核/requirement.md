# Finance Accounting 账务内核（第一批）

状态：阶段 1 已确认（2026-10-15），确认记录见 [delivery.md](./delivery.md)。

## 1. 背景

Finance 目前只有工程骨架和一个基于旧数字身份的余额只读切片。按 [Finance 模块化代码设计](../../../agent-docs/plans/2026-09-04-finance-modular-code-design.md) 第 9 节，第一批只做账务内核闭环：基础初始化、合法指令过账、查询、幂等、余额和期间控制；经济事实、成熟、对账、出款与税务后续分批接入。

## 2. 目标与使用者

- 使用者：Finance 内部能力及 Operations 财务人员（人工调整 Maker/Checker）；后续支付、退款、拒付、成熟、出款和税务记账都复用本内核。
- 目标：合法、借贷平衡的记账指令在一个本地事务内原子形成不可变 Journal 并更新统一账户余额投影；重复不重复记账，非开放期间拒绝写入。
- 首个真实入口：人工会计调整（Manual Adjustment），不依赖交易经济、规则 Bundle 或其他系统。

## 3. 本期范围

| # | 内容 | 表（Accounting 37 表中的 16 张） |
|---|---|---|
| S1 | 基础资料：币种、科目表与核心科目基线、Finance Entity、账簿、统一账户、财务日历、会计期间、账簿序号 | `currency_spec`、`chart_of_accounts`、`chart_account`、`finance_entity`、`ledger_book`、`account`、`fiscal_calendar`、`accounting_period`、`book_sequence` |
| S2 | 过账内核：Instruction 校验 → Journal → 余额投影 | `accounting_instruction`、`instruction_line`、`journal_entry`、`journal_line` |
| S3 | 人工调整 Maker-Checker：创建、提交、审批/驳回，审批后过账 | `manual_adjustment`、`manual_adjustment_line`、`adjustment_approval` |
| S4 | 余额查询（FIN-I01/I02）迁移到新 Schema 身份 | 复用 `ledger_book`、`chart_account`、`account` |

已确认的实现口径：

- 币种、科目表和核心科目基线使用初始化 DML；Finance Entity、账簿、日历、期间、账户通过 Finance 内部 HTTP 接口创建。
- 人工调整附件只保存 `file_id + file_content_hash`，本期不调用 Platform 校验文件。
- 本地 `mor_finance` 的 Schema 变更在阶段 3 给出具体文件、备份方案后单独授权。

## 4. 明确不做

交易经济、Channel 成本、自动记账规则 Bundle 与 AUTO Instruction、MQ 事件消费；合同成熟、保证金、追偿；试算平衡、关账与期间重开（本期仅实现“期间 OPEN 才能过账”门禁）；Treasury、Tax；`compensation_event` 出站事件；Gateway 路由、Owner/OE 资源授权；test/生产部署。

## 5. 验收标准

| 编号 | 标准 |
|---|---|
| AC-1 | 每张 Journal 借贷相等、只属于一个账簿、金额 > 0，写入后不可修改 |
| AC-2 | 过账在一个本地事务内完成 Instruction 状态、序号、Entry/Line、账户余额；任一步失败整体回滚，序号不跳号 |
| AC-3 | 每账簿序号严格递增；并发过账（双连接）不重号、不丢余额更新 |
| AC-4 | 余额按账户 `normal_side` 计算，允许负的反向余额；从 Journal 重算与投影一致 |
| AC-5 | 期间非 OPEN、账户非 ACTIVE、账户跨账簿一律拒绝，并返回带上下文的业务错误 |
| AC-6 | 人工调整 Maker≠Checker；审批 Hash 必须等于内容 Hash；提交后不可修改；驳回后只能新建 |
| AC-7 | 同一 Adjustment 只生成一条 Instruction；重复审批或重放不会重复记账 |
| AC-8 | 余额查询读新 Schema，结果与 Journal 一致 |
| AC-9 | 测试从真实 HTTP 入口运行，使用本地 MySQL，不用 Mockito；`clean verify` 通过 |

## 6. 适用阶段

阶段 1—5（本地验收后结束）；阶段 6—8（test 发布、部署、提交）本次不适用，如需要另行确认。
