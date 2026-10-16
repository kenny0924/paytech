# Finance 交易自动记账（规则 Bundle 与 AUTO 过账引擎）

状态：阶段 1 已确认（2026-10-16）：用户选择“方案 1”，D1—D4 按建议执行。

## 1. 背景与目标

FIN-07（生成指令/双账簿）、FIN-08（复式记账）与 [Finance 数据库设计](../../../agent-docs/database/FINANCE_DATABASE.md) §4.4、全局不变量 27：支付成功后，Finance 按已冻结的交易经济组件（Transaction Economic Component）金额，经已批准的自动记账规则 Bundle 确定性生成 AUTO 指令并过账。

阶段 1 调研结论：完整的“支付成功入账”链路依赖 4 项尚未实现的外部或内部输入（Commerce 订单上下文查询 COM-I04、CCT 价格解析、Payment 定价事实替代合同、Finance 税务快照），Finance 现在无法在不猜费率的前提下创建交易经济结果。因此本需求只交付 Finance 自有、无外部合同依赖的部分：**规则 Bundle 管理 + 按已计算组件生成并过账 AUTO 指令的引擎**；全链路所需的跨系统合同另见 [跨系统依赖清单](./dependencies.md)，由用户分派各系统。

使用者：Finance 内部（规则维护为内部开发接口，不接 Gateway/授权；引擎为模块内领域服务，供后续 PaymentSucceeded 消费者在同一本地事务内调用）。

## 2. 本期范围

| # | 内容 |
|---|---|
| P1 | 记账规则 Bundle 版本管理（Maker-Checker）：以完整有序规则行集创建 DRAFT 版本、提交 PENDING_APPROVAL（冻结行集并校验行号/行代码唯一与预期借贷平衡）、Checker（≠ Maker）批准后生效并原子切换 Current Pointer、驳回；版本与规则行查询 |
| P2 | 交易自动记账引擎：输入一条已存在的 PAYMENT 交易经济结果及其组件，按当前生效 Bundle 版本逐指令类型取金额来源、解析 Owner Role → 唯一账户、生成 AUTO 指令（冻结 Source Tuple、Bundle Version、Input Hash、`transaction_economics_id`）并过账；OE 账簿与 Platform Finance Entity 账簿的指令在同一本地事务内按账簿 ID、账户 ID 升序加锁，一次提交 |
| P3 | 分录覆盖（D1）：OE 账簿 Dr 渠道待清算应收（Gross）/ Cr Client 应付 PENDING（本金）/ Cr Client 手续费收入 / Cr 应交税费（有税时）；渠道成本组件存在时 Dr 渠道成本 / Cr 渠道费用预提；有 Partner 时 Dr 分润费用 / Cr Partner 应付 PENDING；平台服务费 Partner OE 账簿 Dr 平台服务费费用 / Cr 平台服务费应付，Platform Finance Entity 账簿 Dr 平台服务费应收 / Cr 平台服务费收入 |
| P4 | 规则语义：`required_flag=1` 的来源缺失拒绝生成；来源存在但金额为零时该来源全部规则行整体跳过；同一来源重复调用返回已有结果，输入不一致进入冲突（Fail-Closed） |
| P5 | 初始化 DML（D3）：预置 STANDARD 交易记账 Bundle 第 1 版（ACTIVE），覆盖 P3 的指令类型与规则行 |
| P6 | 跨系统依赖清单文档：Payment 事件 v2 字段、Commerce 订单上下文查询、CCT 价格解析、Finance 税务快照 |

## 3. 明确不做

PaymentSucceeded MQ 消费（含放开 pom 对 RabbitMQ/补偿事件组件的禁令、`t_compensation_event` 迁移到新形态，随消费批次一起做）、交易经济结果与组件的创建和费率计算、税务快照、资金成熟（D2）、渠道成本配置（D4）、渠道 Open Item（依赖 Payment 渠道交易引用合同，随消费批次）、出站事件（EconomicsCalculated/LedgerPosted/BalanceChanged）、退款/争议/重复扣款/成本调整类经济结果、Gateway/授权、页面。

## 4. 已确认决定

| # | 决定 |
|---|---|
| D1 | Partner 分润与平台服务费双账簿纳入规则引擎，两账簿指令同一事务过账 |
| D2 | 资金成熟（PENDING → AVAILABLE 定时任务）本期不做 |
| D3 | 标准交易记账 Bundle 用初始化 DML 预置；后续修改走 P1 的 Maker-Checker 接口 |
| D4 | Finance 渠道成本配置本期不做；引擎按“组件存在才记账、金额为零整组跳过”处理渠道成本，不假设成本为零（组件由后续交易经济计算负责） |

## 5. 验收标准（草案）

| 编号 | 标准 |
|---|---|
| AC-1 | 规则版本：DRAFT 带完整行集创建；提交后行集冻结；Checker ≠ Maker 批准后为 ACTIVE 且 Current Pointer 指向新版本、旧版本 RETIRED；驳回为 REJECTED；非 DRAFT 不可再改行 |
| AC-2 | 无 Partner 的支付：OE 账簿生成 1 条 AUTO 指令与 1 条 Journal，借贷平衡；Client 应付进入 PENDING 桶；指令冻结 Bundle Version、Input Hash 与交易经济 ID |
| AC-3 | 有 Partner 的支付：OE 账簿含分润行；Partner OE 账簿与 Platform Finance Entity 账簿各 1 条平台服务费指令与 Journal，三者同一事务，任一失败全部回滚 |
| AC-4 | 金额为零的来源整组跳过；必需来源缺失被拒绝；缺少目标账簿、期间未开放、无生效 Bundle 均 Fail-Closed |
| AC-5 | 同一交易经济重复调用返回原指令与 Journal，不重复记账 |
| AC-6 | `clean verify` 通过；新增能力有业务级测试覆盖 |

## 6. 适用阶段

阶段 1—5；P5 初始化 DML 与可能的模型调整涉及本地 SQL（阶段 3 给出后单独授权）；提交另行确认。
