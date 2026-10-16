# Finance 交易自动记账 — 技术方案

状态：阶段 2 已确认（2026-10-16）；阶段 3 方案、接口与数据设计待确认。

## 1. 需求项差异

| # | 需求 | 现状 | 差异 |
|---|---|---|---|
| P1 | 规则 Bundle 版本管理 | `t_accounting_rule_bundle / _ver / _line` 三表已在 Schema 与本地库；无 DataObject、Mapper、接口 | 新增三表持久化、版本生命周期与 Maker-Checker 接口 |
| P2 | AUTO 过账引擎 | `JournalPostingService` 已支持任意 VALIDATED 指令（关账 AUTO 指令已在用）；`t_accounting_instruction` 已有 `transaction_economics_id`、`accounting_rule_bundle_ver_id`、`input_hash`，`t_instruction_line` 已有 `accounting_rule_line_id` | 新增规则解释器：读取交易经济与组件 → 按指令类型取金额来源 → Owner Role 解析账户 → 生成指令与指令行 → 复用过账内核 |
| P2 | 读取交易经济 | `t_transaction_economics / _component` 已在 Schema；无 DataObject/Mapper；本期无创建入口 | 新增只读持久化；测试由 Mocker 直接插入经济结果夹具 |
| P2 | 渠道应收账户 | `t_channel_account`（Finance 渠道清算账户，按 MID + 币种唯一）已在 Schema；无 DataObject；`OwnerType.CHANNEL_ACCOUNT` 已存在 | 新增只读查询；交易经济 Header 不含 Channel/MID，需由调用方传入（见 D6） |
| P3 | 分录科目 | 29 个科目已预置，覆盖 P3 全部科目；`OwnerType` 含 CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、TAX_AUTHORITY | 无需新增科目；新增 Owner Role 与金额来源代码目录（代码常量/枚举） |
| P5 | Bundle 初始化 | `finance-initial-data.sql` 无规则 Bundle | 追加 DML（Bundle、Version、Rule Line 固定 ID） |

## 2. 影响

- **模块**：只改 `finance-service`；`paytech-finance-api` 新增规则 Bundle 接口与请求/响应体；`paytech-finance-accounting` 新增持久化、规则服务、过账引擎；`paytech-finance-web` 新增 Controller 与枚举注册；测试模块新增 Builder/Mocker/用例。
- **过账内核**：不改签名；引擎在调用方事务内多次调用 `post`（每账簿一条指令），锁顺序沿用“期间 → 账簿序号 → 账户升序”，多账簿按账簿 ID 升序依次过账。
- **数据库**：预计无 DDL（三张规则表、经济结果表、渠道清算账户表均已存在）；P5 新增 DML。若阶段 3 发现字段约束冲突，再单列 DDL 与授权。
- **其他系统**：无。PaymentSucceeded 消费、`pom` 禁令放开、补偿事件表迁移都留在消费批次。
- **共享文档**：`requirements/README.md` 索引行；阶段 5 更新 `CURRENT_IMPLEMENTATION.md` Finance 段落；`FINANCE_DATABASE.md` 仅在需要补充例外或代码目录时追加。

## 3. 风险与待定事项（阶段 3 方案中给出建议）

| # | 事项 | 说明 | 初步建议 |
|---|---|---|---|
| D5 | 引擎入口与事务 | 设计要求交易经济、指令、Journal 同一本地事务；本期没有生产调用方 | 引擎为模块领域服务（interface + Impl），要求调用方事务；测试从该服务入口触发，用事务模板包裹并以夹具插入交易经济 |
| D6 | 渠道应收 Owner | 借方 `CHANNEL_UNSETTLED_RECEIVABLE` 的 Owner 是 Finance 渠道清算账户，但交易经济 Header 不含 MID | 引擎命令同时接收 Payment 已冻结的 `midId`，按 MID + 币种唯一命中 ACTIVE 清算账户；缺失即 Fail-Closed，不自动创建（维护入口见依赖清单 X5） |
| D7 | 账户自动创建 | 首笔交易时 Client/Partner/主体账户通常不存在 | 对 CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY 角色按身份矩阵在事务内自动创建（同关账 D5）；CHANNEL_ACCOUNT 不自动创建 |
| D8 | 生效版本选择 | Bundle 有 Current Pointer 与生效区间两套信息 | 取 Current Pointer 指向的 ACTIVE 版本，并要求 `effective_from ≤ payment_succeeded_time` 且未过 `effective_to`；不满足 Fail-Closed |
| D9 | 过账日期与期间 | 交易成功时间可能落在已关闭期间（晚到） | 过账日取 `payment_succeeded_time` 在主体日历时区的日期，期间必须 OPEN，否则拒绝（消费批次再决定晚到策略） |
| D10 | 规则版本状态 | 状态有 APPROVED 与 ACTIVE 两个值 | 批准时若已到生效时间则直接 ACTIVE 并切换 Pointer、旧版本 RETIRED；未到生效时间则 APPROVED，切换留待后续批次 |
| R1 | 无生产调用方 | 引擎在消费批次前不会被真实流量触发 | 用户已选方案 1 接受；测试覆盖从领域服务入口出发的完整过账链路 |
| R2 | 合同漂移 | X1—X3 定稿后交易经济字段可能调整 | 引擎只读取少量 Header 字段（ID、类型、来源 Tuple、entityNo、Client、Partner、Finance Entity、币种、Gross、成功时间）与组件金额，变更面可控 |

D5—D10 在阶段 3 按上表建议采用，细化见下文。

## 4. 代码目录（固定解释器使用，均为 Java 枚举，不入库为配置）

**指令类型与账簿（固定映射，不在规则行中配置）**

| 指令类型 | 目标账簿 | 生成条件 |
|---|---|---|
| `PAYMENT_SUCCEEDED` | 交易 OE 账簿（`OPERATING_ENTITY` + `entity_no` + 币种） | 必有 |
| `PLATFORM_SERVICE_CHARGE_PARTNER` | 交易 OE 账簿 | 存在 `PLATFORM_SERVICE_CHARGE` 组件且金额 > 0 |
| `PLATFORM_SERVICE_CHARGE_PLATFORM` | Platform Finance Entity 账簿（`FINANCE_ENTITY` + `finance_entity_id` + 币种） | 同上 |

**金额来源**（同一来源多个组件按 `posted_amount` 求和）

| 代码 | 取值 |
|---|---|
| `GROSS_AMOUNT` | Header `gross_amount` |
| `CLIENT_PRINCIPAL` | 组件类型 `CLIENT_PRINCIPAL` |
| `CLIENT_FEE` | 组件类型 `CLIENT_FEE` |
| `SELLER_TAX` | 组件类型 `SELLER_TAX` |
| `CHANNEL_COST` | 组件类型 `CHANNEL_COST` |
| `PARTNER_SHARE` | 组件类型 `PARTNER_SHARE` |
| `PLATFORM_SERVICE_CHARGE` | 组件类型 `PLATFORM_SERVICE_CHARGE` |

**Owner Role**

| 代码 | 解析为（owner_type / owner_id） |
|---|---|
| `BOOK_ENTITY` | 目标账簿自身主体（`OPERATING_ENTITY/entityNo` 或 `FINANCE_ENTITY/id`） |
| `CLIENT` | `CLIENT` / Header `client_id` |
| `LOCAL_PARTNER` | `LOCAL_PARTNER` / Header `local_partner_id` |
| `CHANNEL_ACCOUNT` | `CHANNEL_ACCOUNT` / 按命令 `midId` + 币种命中的 ACTIVE `t_channel_account.id`（D6） |
| `PLATFORM_FINANCE_ENTITY` | `FINANCE_ENTITY` / Header `finance_entity_id` |
| `TRANSACTION_OE` | `OPERATING_ENTITY` / Header `entity_no` |
| `TAX_AUTHORITY` | `TAX_AUTHORITY` / `SELLER_TAX` 组件唯一的 `party_id`（多个不同税务机关 Fail-Closed） |

## 5. 接口设计（`AccountingRuleBundleApi`，前缀 `/accounting-rule-bundles/v1`，内部开发接口）

| # | 方法与路径 | 请求体 | 结果 |
|---|---|---|---|
| R1 | `POST /{bundleCode}/versions` | `makerId, effectiveFrom, lines[{instructionType, lineNo, lineCode, amountSourceCode, ownerRole, accountCode, balanceBucket, entrySide, requiredFlag}]` | 201 版本详情（DRAFT，`bundleVer` = 最大值 + 1） |
| R2 | `POST /{bundleCode}/versions/{bundleVer}/submit` | `makerId` | 200 版本详情（PENDING_APPROVAL） |
| R3 | `POST /{bundleCode}/versions/{bundleVer}/approve` | `checkerId` | 200 版本详情（ACTIVE 或 APPROVED） |
| R4 | `POST /{bundleCode}/versions/{bundleVer}/reject` | `checkerId` | 200 版本详情（REJECTED） |
| R5 | `GET /{bundleCode}` | — | Bundle（状态、当前版本号）与全部版本摘要（版本号降序） |
| R6 | `GET /{bundleCode}/versions/{bundleVer}` | — | 版本详情含按指令类型、行号排序的规则行 |

规则：
- Bundle 本身只由 DML 创建；不存在返回业务错误。写入口均以 `LOCK accounting-rule-bundle:{bundleCode}` 串行化（AbstractLockProcessor），事务内再按“Bundle → Version”顺序 `FOR UPDATE`。
- R1 校验：行集非空；代码均属第 4 节目录；`accountCode` 在 STANDARD 科目表中存在且可过账；Owner Role 解析出的 owner_type 与余额桶组合合法（Client/Partner 可用 PENDING/AVAILABLE/LOCKED/NONE，`PARTNER_PAYABLE` 仅 PENDING/AVAILABLE/LOCKED，其余仅 NONE）；`(instructionType, lineNo)`、`(instructionType, lineCode)` 唯一；同一 Bundle 已有 DRAFT 或 PENDING_APPROVAL 版本时拒绝。行集随版本一次写入，DRAFT 期间不提供单行追加（需要修改则驳回/放弃后新建版本）。
- R2：仅 DRAFT、提交人 = 编制人；每个指令类型至少一借一贷；冻结后行集不可变。
- R3：仅 PENDING_APPROVAL、Checker ≠ Maker；写 `checker_id/approved_time`。若 `effectiveFrom ≤ 当前时间`：本版本 ACTIVE、原当前版本 RETIRED 且 `effective_to` = 本版本 `effective_from`、Bundle `current_ver_id` 原子切换并 `version+1`；否则保持 APPROVED（D10，切换不在本期）。
- R4：仅 PENDING_APPROVAL、Checker ≠ Maker；写 `checker_id`，`approved_time` 为空。

## 6. 过账引擎（`TransactionPostingService`，领域服务，D5）

`List<PostedInstruction> post(TransactionPostingCommand command)`；命令为 `transactionEconomicsId + midId`；必须在调用方事务内执行。

1. **幂等**：按 `transaction_economics_id` 查已有 AUTO 指令，存在则直接返回原指令与 Journal（AC-5），不重算。
2. **读取与校验**：经济结果须为 PAYMENT 且有 `payment_succeeded_time`；Partner 三元组全有或全无；有 Partner 时必须存在 `PARTNER_SHARE` 与 `PLATFORM_SERVICE_CHARGE` 组件，无 Partner 时两者不得存在。
3. **规则版本**（D8）：Bundle `STANDARD_TRANSACTION` 当前版本须 ACTIVE 且 `effective_from ≤ payment_succeeded_time <` `effective_to`（为空视为无穷）。
4. **生成**：按第 4 节逐指令类型取规则行 → 取金额来源（`required_flag=1` 且来源缺失则拒绝；金额为零则该来源全部行跳过）→ Owner Role 解析账户（D7：不存在的 `t_account` 自动创建，含 `CHANNEL_ACCOUNT` 角色；但 `t_channel_account` 清算账户配置必须已存在且 ACTIVE，不自动创建）→ 指令行按规则行号排序；指令行为空则不生成该指令；每条指令借贷必须平衡，否则拒绝。
5. **账簿与期间**（D9）：账簿须存在且 ACTIVE；过账日 = `payment_succeeded_time` 在该账簿主体日历时区的日期，所在期间须 OPEN。
6. **指令**：`origin_type=AUTO`、来源 Tuple 取经济结果（`source_system/source_event_key/source_version/source_payload_hash`）、`transaction_economics_id`、`accounting_rule_bundle_ver_id`、`input_hash` = SHA-256（Bundle 版本 ID、经济结果 ID、指令类型、按行号的“规则行 ID|账户 ID|方向|金额”），状态 VALIDATED；指令行引用 `accounting_rule_line_id`。
7. **过账**：按账簿 ID 升序逐条调用 `JournalPostingService.post`（Journal 类型 STANDARD），过账后指令 POSTED。任何一步异常整体回滚；唯一键 `uk_instr_source` 兜底并发重复。

Fail-Closed 错误一律 `BizCheckFailException`，消息含经济结果 ID、指令类型与缺失项。

## 7. 数据库与文档变更

- **DDL**：无。
- **DML**（[db/dml.sql](./db/dml.sql)，同步追加到 `finance-initial-data.sql`）：Bundle `STANDARD_TRANSACTION`（5001，ACTIVE，`current_ver_id=5101`）；版本 5101（`bundle_ver=1`，ACTIVE，`effective_from=2000-01-01`，`maker_id=1`、`checker_id=2` 代表初始化双人复核）；规则行 5201—5212 如下。

| ID | 指令类型 | 行号 | 行代码 | 金额来源 | Owner Role | 科目 | 桶 | 方向 | 必需 |
|---|---|---|---|---|---|---|---|---|---|
| 5201 | PAYMENT_SUCCEEDED | 1 | GROSS_RECEIVABLE | GROSS_AMOUNT | CHANNEL_ACCOUNT | CHANNEL_UNSETTLED_RECEIVABLE | NONE | DEBIT | 1 |
| 5202 | PAYMENT_SUCCEEDED | 2 | CLIENT_PRINCIPAL_PAYABLE | CLIENT_PRINCIPAL | CLIENT | CLIENT_PAYABLE | PENDING | CREDIT | 1 |
| 5203 | PAYMENT_SUCCEEDED | 3 | CLIENT_FEE_REVENUE | CLIENT_FEE | BOOK_ENTITY | CLIENT_FEE_REVENUE | NONE | CREDIT | 1 |
| 5204 | PAYMENT_SUCCEEDED | 4 | SELLER_TAX_PAYABLE | SELLER_TAX | TAX_AUTHORITY | TAX_PAYABLE | NONE | CREDIT | 0 |
| 5205 | PAYMENT_SUCCEEDED | 5 | CHANNEL_COST_EXPENSE | CHANNEL_COST | BOOK_ENTITY | CHANNEL_FEE_EXPENSE | NONE | DEBIT | 0 |
| 5206 | PAYMENT_SUCCEEDED | 6 | CHANNEL_COST_ACCRUAL | CHANNEL_COST | CHANNEL_ACCOUNT | CHANNEL_FEE_ACCRUAL | NONE | CREDIT | 0 |
| 5207 | PAYMENT_SUCCEEDED | 7 | PARTNER_SHARE_EXPENSE | PARTNER_SHARE | BOOK_ENTITY | PARTNER_SHARE_EXPENSE | NONE | DEBIT | 0 |
| 5208 | PAYMENT_SUCCEEDED | 8 | PARTNER_SHARE_PAYABLE | PARTNER_SHARE | LOCAL_PARTNER | PARTNER_PAYABLE | PENDING | CREDIT | 0 |
| 5209 | PLATFORM_SERVICE_CHARGE_PARTNER | 1 | SERVICE_FEE_EXPENSE | PLATFORM_SERVICE_CHARGE | BOOK_ENTITY | PLATFORM_SERVICE_FEE_EXPENSE | NONE | DEBIT | 0 |
| 5210 | PLATFORM_SERVICE_CHARGE_PARTNER | 2 | SERVICE_PAYABLE | PLATFORM_SERVICE_CHARGE | PLATFORM_FINANCE_ENTITY | PLATFORM_SERVICE_PAYABLE | NONE | CREDIT | 0 |
| 5211 | PLATFORM_SERVICE_CHARGE_PLATFORM | 1 | SERVICE_RECEIVABLE | PLATFORM_SERVICE_CHARGE | TRANSACTION_OE | PLATFORM_SERVICE_RECEIVABLE | NONE | DEBIT | 0 |
| 5212 | PLATFORM_SERVICE_CHARGE_PLATFORM | 2 | SERVICE_REVENUE | PLATFORM_SERVICE_CHARGE | BOOK_ENTITY | PLATFORM_SERVICE_REVENUE | NONE | CREDIT | 0 |

  `PAYMENT_SUCCEEDED` 平衡依赖经济结果满足 `GROSS = CLIENT_PRINCIPAL + CLIENT_FEE + SELLER_TAX`（交易经济计算的职责），引擎在生成后逐指令校验借贷相等。
- **文档**：`FINANCE_DATABASE.md` 自动记账段落后追加“V1 指令类型/金额来源/Owner Role 目录与账簿映射”说明（本文第 4 节）。
- **测试数据清理**：三张规则表按保留行处理（ID < 1,000,000 为初始化数据）；清理时删除测试写入的版本与规则行，并把 Bundle 5001 与版本 5101 恢复为初始化状态。

## 8. 测试与验收映射

| AC | 用例（入口） |
|---|---|
| AC-1 | 规则接口：创建 DRAFT、提交、批准切换（当前版本、旧版本 RETIRED）、未到生效时间仅 APPROVED、驳回、Maker 自审、非 DRAFT 提交、非法代码/科目/桶、重复行号、已有待审版本、查询（HTTP） |
| AC-2 | 引擎：无 Partner 支付，验证指令、Journal、PENDING 桶、Input Hash、自动建账户（领域服务入口 + 事务模板） |
| AC-3 | 引擎：有 Partner，三条指令同一事务；Platform 账簿缺失时全部回滚 |
| AC-4 | 引擎：零手续费跳过、含税与渠道成本、必需来源缺失、渠道清算账户缺失、期间未开放、无生效 Bundle、Partner 组件不一致 |
| AC-5 | 引擎：重复调用返回原结果 |
| AC-6 | `clean verify` |

## 9. 阶段 4 多 agent 分工（建议）

- A：规则 Bundle 持久化、R1—R6 接口与 Processor、DML/文档。
- C：交易经济与渠道清算账户只读持久化、过账引擎。
- B：测试（Builder/Mocker/Checker、清理器保留行恢复、全部用例）。
- 主会话：合并核对、`clean verify`。
