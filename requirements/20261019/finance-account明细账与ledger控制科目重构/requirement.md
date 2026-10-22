# account 明细账与 ledger 控制科目重构（含 Operations 财务页面适配）

> 状态：阶段 1—3 已确认（2026-10-19）；阶段 4 研发已交付，待确认（3 项偏离待裁决，见 delivery.md）。确认记录见 [delivery.md](./delivery.md)，现状差异与方案见 [technique.md](./technique.md)。

## 1. 背景与来源

[2026-10-18 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)与[详细设计](../../../design-discuss/finance-account-ledger-v3/design.md)已确认：account 是明细账（Owner 账户、余额桶、流水、入账批次），ledger 只记控制科目并消费 account 的 `AccountPosted`；删除金额核对，一致性由构造保证。决定记录的「实现状态」为 Not started，现行代码仍是第 11b 步的双消费实现（account 与 ledger 各自消费 billing `EconomicFactRecorded`，日终比对金额）。

用户 2026-10-19 指示：开始重构任务，Operations 控制台一并修改；设计与分析由主会话完成，研发由 Opus 5.5 多 agent 执行。

当前事实（2026-10-19，依据四份只读源码调查，细节见 technique.md 第 1 节）：

| 项 | 现状 |
|---|---|
| account | 8 张表、34 个 auto 测试。消费 `EconomicFactRecorded`，按 `fact_id + fact_version` 幂等，版本替代做冲回，CAS 更新余额；所有映射金额只进 PENDING，**没有桶移动命令、没有到期转可用、不发布任何事件**；`t_balance_movement` 与 `BalanceMovedPayload` 只是空壳。三处同步调 ledger（账簿查找、账簿详情、`account-totals`），一处调 billing（重建重放）。日终核对 Job 写 `t_balance_discrepancy` |
| ledger | 31 张表、约 215 个 auto 测试。消费同一事件，规则包推导含 CLIENT / LOCAL_PARTNER 的 Owner 行并自动开户；**不处理版本替代**（同一 `economic_fact_id` 已有指令即返回）；关账步骤 2 调 billing `pending` 与 account `consistency`，步骤 3 `RECONCILE_ACCOUNT_BALANCES` 只核渠道 / 银行账户非零数；`PROJECTION_REBUILD` 整条链路在生产中不可达（试算只产生 CHART_ACCOUNT 异常）。Enforcer 禁止 compensation-event 与 XXL-Job |
| billing | 只能人工登记 `MANUAL_ADJUSTMENT`、`COST_ADJUSTMENT`（PAYMENT 事实尚无生产入口，测试经 Probe 发布）。`balance_available_time` 只存在于 billing 表与 DataObject，**不在事件载荷里**，也没有写入逻辑 |
| payout / tax / reconciliation | payout 只有模型（Maven 中注释掉）；tax、reconciliation 是空壳。模型中 `t_payout.available_account_id / locked_account_id`、`t_tax_payment.tax_payable_account_id` 仍指 ledger 账户 |
| Operations 控制台 | 2026-10-19 已提交并部署 mor-test（`7acbcf4f`）：18 条 `/finance/**` 路由，账簿 → 账户 → 流水 → 分录四级下钻与 Client / Partner 余额查询。BFF 正则与 Web 常量硬编码 7 种 Owner 类型（含 CLIENT、LOCAL_PARTNER），余额查询依赖 account 的 `LEDGER_BOOK_NOT_FOUND`，分录来源只认 AUTO / MANUAL |
| 消息 | `EconomicFactRecorded` 目录消费者 [ledger, account]，`strictOrder=false`；目录中 `BalanceMoved` 无生产者。Foundation 严格有序消费对**首次见到的聚合接受任意版本作为基线** |
| 环境 | mor-test 运行 11b 双消费镜像；`mor_ledger` 31 表（86 行种子）、`mor_account` 8 表 0 行；初始化脚本只填空库，已有库的结构变更需手工 SQL |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 账户系统（payout、tax、运营余额查询的调用方） | 在 account 一处得到 Client / Partner / 银行现金的实时余额与桶，出款锁定与到期释放不再依赖会计分录 |
| 财务核对人员 | ledger 控制科目总额与 account 明细合计由构造一致，不再看差异表；从分录行可下钻到 account 流水，从流水可追溯到经济事实 |
| 财务关账人员 | 关账前一次看到两段入账进度（billing → account、account → ledger），任一段未追平即失败关闭 |
| 运营 / 客服 | 余额查询不再要求先建 ledger 账簿；可按主体 + 币种浏览 Owner 账户与流水 |

## 3. 本期范围（建议）

1. **account 改为明细账**（设计第 4 节）
   - 模型：`t_account_balance` → `t_owner_account`（状态 ACTIVE / FROZEN / CLOSED，主体字段改名）、新增 `t_account_posting`（`shard_no` 固定 0 + `posting_sequence`）、`t_balance_applied_entry` → `t_account_entry`（挂批次，`reverses_entry_id`）、`t_balance_movement` 与 `t_balance_applied_fact` 小改、删除 `t_balance_discrepancy`；`account.pdma.json`、`account-schema.sql`、测试 DDL、变更 SQL 同步。
   - 入账：消费 `EconomicFactRecorded` 后在同一事务写批次 + 流水 + 余额，分配主体 + 币种内连续序号，不影响 Owner 的事实生成 0 条流水批次；版本替代冲回写带 `reverses_entry_id` 的流水行（现在不写行）。
   - 发布：经本地补偿事件可靠发布 `AccountPosted`（聚合 `entity_id:currency:shard_no`，版本 `posting_sequence`，严格有序）。
   - 桶移动命令：`source_type + source_ref` 幂等的通用命令（AVAILABLE ↔ LOCKED、PENDING → AVAILABLE），记 `t_balance_movement` 与一条批次；银行现金 `CASH_ACCOUNT` 账户同样按此占用 / 释放（设计 4.4），本期只提供能力，不接 payout / tax。
   - 到期转可用：account 定时任务按事实 `balance_available_time` 做 PENDING → AVAILABLE（设计 8.5），需 billing 在事件载荷补该字段（Q-02）。
   - 删除：对 ledger 的全部同步调用与 `paytech-ledger-api` 依赖、日终核对 Job、差异表、`consistency` 接口；重建改为按主体 + 币种从本库流水重算（设计 4.5）。
   - 新增查询：入账进度（供 ledger 关账两段检查与 catch-up）、Owner 账户列表、账户流水、入账批次详情（供控制台）。
   - 账户状态只约束命令不拒绝事实（8.7）；Owner 账户类型目录在 account-api 代码中。
2. **ledger 改为总账控制科目**（设计第 5 节）
   - 模型：`t_ledger_account.owner_type` 删除 CLIENT / LOCAL_PARTNER；`t_instruction_line` 加 `account_entry_id`；`origin_type` 加 `ACCOUNT_POSTING`；`t_close_step.step_type` 改 `CHECK_ACCOUNT_POSTING_PROGRESS`；`resolution_type` 删 `PROJECTION_REBUILD`；`ledger.pdma.json` 脱离 Finance 生成器直接维护；Schema、测试 DDL、变更 SQL、初始化 DML（规则包删 8 条 Owner 行）同步。
   - 消费：改为严格有序消费 `AccountPosted`；Owner 流水逐行记到该科目的控制账户（每账簿每科目一行，Owner = 账簿主体，桶 NONE），非 Owner 行事务外经 billing-api 读事实明细后按规则包推导；`BALANCE_MOVEMENT` 批次只推进进度；冲回流水生成冲回分录；自行校验序号连续（Q-06）。
   - 删除：`EconomicFactRecorded` 消费、`account-totals` 接口、`consistency` 调用、`PROJECTION_REBUILD` 整条链路（`TrialExceptionApi`、两个 Processor、Validator）。
   - 关账：`CHECK_ACCOUNT_POSTING_PROGRESS` 两段检查（8.8）；已关账期间的事实记入下一个开放期间并保留业务日期（8.9）；人工调整改为拒绝 Owner 控制科目。
   - Owner 类型枚举归属调整（Q-05）；处理卡住后的继续机制（Q-06）。
3. **billing**：`EconomicFactRecorded` 载荷加可选 `balanceAvailableTime`（Q-02）；事件目录消费者改为只有 account。
4. **Foundation 事件目录与架构规则**：`events.yaml` 删 `BalanceMoved`、加 `AccountPosted`；表归属生成器与 design/03、`TableOwnershipAutoTest` 同步；`scripts/pdmaner` 生成器摘出 ledger 并新增 `ledger-model.test.mjs`。
5. **Operations 控制台**（Q-04）
   - 适配：账户列表 / 新建账户的 Owner 类型与余额桶选项按 ledger 新枚举；分录抽屉支持 `ACCOUNT_POSTING` 来源并在控制科目行显示 `account_entry_id`；余额查询错误合同改为 account 自身错误码（Q-03）。
   - 新增「明细账查询」二级菜单：主体 + 币种 → Owner 账户列表（可按 Owner 类型 / ID / 科目 / 桶筛选）→ 账户流水 → 入账批次（来源事实或桶移动）；分录抽屉控制科目行可跳转到对应流水。只读，沿用 `FINANCE_ACCOUNTING_READ`。
   - BFF 新增转发、Platform 注册 DML（新路由与 Console Resource）、Pencil 画板补充。
6. **文档与目录**：ledger / account README、`CURRENT_IMPLEMENTATION.md`、决定记录实现状态、`FINANCE_DATABASE.md` 取代说明不变；requirements-site 直接冲突条目（Q-07）。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| payout / tax 接入 account 桶移动，`t_payout.*_account_id`、`t_tax_payment.tax_payable_account_id` 模型调整 | payout 无代码、tax 空壳；随各自切片设计（决定第 6 节） |
| 退款、争议败诉的扣减顺序与保证金使用（设计 8.6） | billing 尚不登记 REFUND / DISPUTE 事实，ledger 也无对应非 Owner 规则；随「退款与争议入账」切片一起做，本期映射只覆盖 billing 现有的 PAYMENT / MANUAL_ADJUSTMENT / COST_ADJUSTMENT（Q-01） |
| 渠道结算到账、出款完成、缴税完成登记经济事实的合同 | 决定第 6 节：各自切片设计 |
| 多分片（`shard_no > 0`） | 预留字段与合同，不实现分片规则 |
| 控制台的余额重建、入账进度、桶移动、ledger catch-up 操作入口 | 修复 / 关账辅助命令，随关账工作台批次接入；本期只有内部接口 |
| Client / Partner Console 余额页面 | 另属 Client / Partner BFF 需求 |
| 历史数据迁移 | 决定第 11 条：ledger 现有 Owner 账户与分录只有本机与测试数据，清空重建（设计 8.4） |
| 执行 SQL、部署、提交 | 按阶段单独授权 |

## 5. 验收标准

**功能验收（链路）**

| 编号 | 验收标准 |
|---|---|
| AC-01 | billing 发布的每条已批准事实，account 恰好生成一条 `t_account_posting`（含 0 流水批次），同主体 + 币种 + 分片的 `posting_sequence` 连续无缺口；重复投递与乱序到达不产生第二条批次 |
| AC-02 | 版本替代：版本 2 事实入账时，版本 1 的每条流水有一条反向流水且 `reverses_entry_id` 指向它；余额等于已应用流水合计；ledger 生成对应冲回分录 |
| AC-03 | `AccountPosted` 经本地补偿事件可靠发布，严格有序；ledger 对每条事实批次生成一条 `ACCOUNT_POSTING` 指令，Owner 控制科目行逐行带 `account_entry_id`，非 Owner 行按规则包推导，借贷平衡；对 `BALANCE_MOVEMENT` 批次不生成分录只推进进度 |
| AC-04 | 任一账簿内，每个 Owner 科目控制账户的分录合计等于 account 中该主体 + 币种 + 科目代码下全部 Owner 账户（各桶）的流水合计 |
| AC-05 | 桶移动命令按 `source_type + source_ref` 幂等；余额不足拒绝；每次移动生成一条批次与两条流水；`CASH_ACCOUNT` 的 AVAILABLE → LOCKED / LOCKED → AVAILABLE 同样成立 |
| AC-06 | 到期转可用任务只移动 `balance_available_time` 已到且未成熟的 PENDING 金额，重复执行不重复移动 |
| AC-07 | 关账 `CHECK_ACCOUNT_POSTING_PROGRESS`：billing 有未入账事实、account 有卡在死信的事实、或 ledger 落后于 account 最大序号时，关账失败关闭并记录阻断项；追平后通过 |
| AC-08 | ledger 某批次处理失败时，同分片后续批次不被跳过；修复后可从原批次继续并最终追平（Q-06 机制） |
| AC-09 | 已关账期间的事实到达时记入下一个开放期间，分录保留原业务日期 |
| AC-10 | 人工调整涉及 Owner 控制科目时被拒绝（现有错误码语义不变）；FROZEN / CLOSED 账户收到事实照常入账并告警 |
| AC-11 | 重建按主体 + 币种从本库流水重算余额，结果与重建前一致，`balance_version` 单调加一；不再调用 ledger 或 billing |
| AC-12 | account 不再依赖 `paytech-ledger-api`；ledger 不再消费 `EconomicFactRecorded`、不再暴露 `account-totals`、不再有 `PROJECTION_REBUILD`；`BalanceMoved` 从目录删除；架构规则（R4 表归属、R12 事件目录、模块边界测试）全部通过 |
| AC-13 | account、ledger、billing、Operations BFF、Operations Web、platform-support、arch-rules、`scripts/pdmaner`、requirements-site 的既有测试全部通过；新增链路测试覆盖设计第 10 节列出的场景 |

**功能验收（控制台）**

| 编号 | 验收标准 |
|---|---|
| AC-14 | 账簿下账户列表与新建账户只提供 ledger 的 Owner 类型（OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY），余额桶固定 NONE；对 CLIENT / LOCAL_PARTNER 的请求被 BFF 拒绝（Query 参数 400，Body 字段 422 `VALIDATION_ERROR`，与现有 BFF 校验口径一致；2026-10-19 阶段 5 修订） |
| AC-15 | 分录详情显示 `ACCOUNT_POSTING` 来源（account 批次 ID 与序号），控制科目行显示 account 流水 ID 并可跳转到明细账流水 |
| AC-16 | 余额查询不再要求账簿存在；Owner 在该主体 + 币种下无任何账户时显示 account 的业务错误（Q-03），有账户时缺的桶显示 0 |
| AC-17 | 「明细账查询」按主体 + 币种列出 Owner 账户（类型、ID、科目、桶、状态、借贷合计、净余额），可筛选分页；账户流水显示批次序号、来源（事实 ID / 版本或桶移动）、方向、金额、冲回关系；入账批次抽屉显示批次头与全部流水 |
| AC-18 | 注册 DML（新路由、Console Resource、Query 白名单、限流）写入 Platform 初始化 SQL 独立块；R10 / R11 的 Owner 类型白名单同步收窄；SQL 不执行，激活另行授权 |

**视觉 / 交互验收**

| 编号 | 验收标准 |
|---|---|
| AC-19 | 新页面与现有财务页面风格一致，有加载、空状态（未选主体、无账户、无流水）与错误反馈；金额右对齐按币种精度显示，桶与状态用统一标签 |
| AC-20 | 分录行 → 流水、流水 → 批次为逐级下钻，返回保留筛选条件；Pencil 画板补充新页面与改动页面（账户表单、分录抽屉、余额错误） |

## 6. 问题与建议

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 事实类型覆盖范围 | **建议 B**：本期 account 映射与 ledger 规则只覆盖 billing 现有的 PAYMENT / MANUAL_ADJUSTMENT / COST_ADJUSTMENT；REFUND / DISPUTE 的扣减顺序（8.6）随退款与争议入账切片一起做，避免 account 能入账而 ledger 无规则卡住。A：本期在 account 实现 8.6 映射骨架但 ledger 不加规则 |
| Q-02 | `balance_available_time` 不在 `EconomicFactRecorded` 载荷中，account 消费在事务内不能回查 billing | **建议 A**：billing 在载荷头加可选 `balanceAvailableTime`（Schema 版本不变，现有消费者忽略未知字段），account 落在 `t_balance_applied_fact`，定时任务 `accountBalanceMaturity`（XXL-Job）按此做 PENDING → AVAILABLE；billing 现无写入该字段的生产路径，测试经 Probe 设置。B：account 事务外经 billing-api 回查后再开短事务，多一次调用且重建路径更复杂 |
| Q-03 | 余额查询（R17 / R18）不再有账簿概念，`LEDGER_BOOK_NOT_FOUND` 失效 | **建议 A**：Owner 在该主体 + 币种下没有任何账户时返回 `OWNER_ACCOUNT_NOT_FOUND`（400），Web 映射到 Owner ID 字段并改画板 11 文案；有账户时缺的桶返回 0。B：一律返回 0，不报错 |
| Q-04 | 控制台改动范围 | **建议 A**：适配现有三页 + 新增「明细账查询」二级菜单（范围第 5 项），account 新增 3 个只读接口，BFF 新增 3 条路由，Platform 注册 DML 一个新块，沿用 `FINANCE_ACCOUNTING_READ`。B：只适配现有页面，明细账查询另立需求（AC-15 的跳转目标也随之延后） |
| Q-05 | Owner 类型 / 角色枚举现在都在 account-api，ledger 持久化也用它 | **建议**：ledger-api 新增 `LedgerOwnerType`（OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY）与规则行角色枚举（删 CLIENT / LOCAL_PARTNER），ledger 只为 `AccountPosted` 载荷与 `StandardAccountCodes` 保留对 account-api 的依赖；account-api `OwnerType` 收窄为 CLIENT、LOCAL_PARTNER、CASH_ACCOUNT；BFF / Web 的账户枚举改引 ledger 值 |
| Q-06 | ledger 处理卡住后「修复后从原批次继续、不允许跳过」（8.2）：ledger Enforcer 禁止 compensation-event 与 XXL-Job，消息库 5 次投递后进死信，且首次见到的聚合接受任意版本 | **建议**：事件驱动 + HTTP 追平。ledger 消费时自行校验 `posting_sequence` 连续（上一序号已过账或序号为 1，否则抛并发异常等待重投）；达到投递上限进死信并由关账检查阻断；ledger 提供内部命令 `catch-up`（按主体 + 币种 + 分片经 account-api 分页读取未处理批次，事务外逐条过账），修复后由运维调用，死信消息重放时按已消费跳过。不解除 Enforcer 禁令 |
| Q-07 | requirements-site 仍把 Finance 当单系统，E2E-06 / 09 / 10 / 12 与 FIN-18 / 19 / 21 / I16 描述 BANK_CASH 账户行锁定、成熟 Journal 与账户余额核对 | **建议 A**：本期只改与本决定直接冲突的条目（关账核对改两段进度检查、成熟与锁定改为 account 桶移动、BANK_CASH 额度改 account `CASH_ACCOUNT` 桶）及对应测试断言；Finance 拆分为 billing / account / ledger 的整体目录重写另立文档需求。B：全部延后 |
| Q-08 | 适用阶段与执行方式 | **建议**：阶段 1 与 2 合并一次确认（本评审稿已含两阶段交付物）；3 设计（接口、DDL / DML 清单、页面画板）；4 研发按 Opus 5.5 多 agent 并行（T0 合同先行，再 account、ledger、BFF + Web、模型 / SQL / 规则 / 文档四线），主会话拆任务、门禁与独立重跑；5 本机验收；6–7 mor-test 适用（含 `mor_ledger` / `mor_account` 变更 SQL 与 Finance 测试数据清空重建）；8 提交单独授权 |
| Q-09 | 并行会话 | 工作区有其他会话的未提交改动（compliance Controller、本会话的模型与文档）；本需求只改 account、ledger、billing-api 载荷、Foundation 事件目录、arch-rules、`scripts/pdmaner`、Operations BFF / Web 财务文件、Platform 初始化 SQL 增量块、test-environment Compose 增量；共享文件只做增量 |

## 7. 依据

- [2026-10-18 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[详细设计 V3](../../../design-discuss/finance-account-ledger-v3/design.md)、[pen 图](../../../design-discuss/finance-account-ledger-v3/finance-account-ledger-v3.pen)
- 前序：[11b 拆分方案](../../20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md)（已被取代部分见决定第 3 节）、[Operations 财务第一批](../../20261018/operations-console-财务基础资料与账本余额查询/requirement.md)
- 合同：`paytech/account/paytech-account-api`、`paytech/ledger/paytech-ledger-api`、`paytech/billing/paytech-billing-api`、`paytech/foundation/paytech-messaging/catalog/events.yaml`
- 规范：[数据库设计](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)、[补偿事件](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)、[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)第 5 节事务公理、[单元测试](../../../agent-docs/UNIT_TEST_GUIDE.md)
