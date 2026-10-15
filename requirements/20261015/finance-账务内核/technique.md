# Finance Accounting 账务内核 — 技术方案

状态：阶段 2 已确认（2026-10-15）；阶段 3（方案、接口与数据设计）评审稿见第 7 节起。

**规模总结（本次方案）：** 只改 Finance 1 个系统；新增 9 个写接口、1 个查询接口，修改 2 个查询接口（余额参数改为 entityNo）；新增表 0（16 张目标表已在 `finance-schema.sql`），删除本地旧切片表 3 张（0 行），PDManer 字段变化 0；初始化 DML 31 行；Gateway/Permission 注册 DML 0；页面 0。

## 1. 依据

- 业务与数据：[FINANCE_DATABASE.md](../../../agent-docs/database/FINANCE_DATABASE.md) §4.1、§4.3、§6.1、§6.6、§7；[Finance 模块化代码设计](../../../agent-docs/plans/2026-09-04-finance-modular-code-design.md) §3—§9；[OE/Pricing 四系统决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)（Finance `entity_id`、Account `owner_id` 为 VARCHAR(64)，OE 存 entityNo）。
- 工程：[DEVELOPMENT_CONVENTIONS.md](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md) §5.1 锁与并发；[UNIT_TEST_GUIDE.md](../../../agent-docs/UNIT_TEST_GUIDE.md)。
- 现状：`finance-service/` 源码、`finance-service/docs/database/sql/`、本地 `mor_finance` 只读查询（2026-10-15）。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| S1 基础资料 | 无代码；本地库只有旧切片 3 表 `t_ledger_book`、`t_chart_account`、`t_account`，均 0 行 | 9 张基础表；币种/科目 DML 初始化；FE、账簿、日历、期间、账户内部 HTTP 创建 | 表、DML、接口、领域逻辑全部缺失；旧 3 表 `entity_id/owner_id` 为 BIGINT，新 Schema 为 VARCHAR(64) ascii_bin | 替换为 `finance-schema.sql` 中 16 张表定义；新增初始化 DML |
| S2 过账内核 | 无 | Instruction→Journal→余额投影单事务；`book_sequence` 行锁取号；账户 ID 升序锁、`balance_version` CAS | 全部缺失 | 新建 Accounting 过账 Domain Service |
| S3 人工调整 | 无；需求目录无对应 FIN-I 接口 | 创建/提交/审批/驳回/过账；Maker≠Checker；Content Hash | 接口、表、Hash 规则、操作人来源缺失 | 新增内部 HTTP 接口与 Processor；接口编号补入需求目录 |
| S4 余额查询 | `BalanceQueryApi` 以 `operatingEntityId` 正整数（`[1-9][0-9]{0,18}`）查询；Mapper 按 BIGINT `entity_id/owner_id` 读取 | OE 用 entityNo（CHAR(5)），Owner ID 为规范十进制字符串 | 接口参数、校验、Mapper 类型与新 Schema 不兼容 | 改为 entityNo 查询；属于接口不兼容变更（当前无外部消费者，未接 Gateway） |
| AC-9 测试 | 5 个测试类直接用 `JdbcTemplate` 插数据、`auto` Profile；无 MockTestBase/Builder/Mocker/Checker/TestMapper | 按 UNIT_TEST_GUIDE 的两级 BeforeEach、全局 AfterEach、Builder/Mocker/Checker | 测试基础设施缺失 | 新建 Finance 测试基础设施；余额测试同步迁移 |

## 3. 能力缺口与依赖

| 缺口 | 说明 | 建议 |
|---|---|---|
| ID 生成 | Finance 未依赖 `paytech-snowflake-id`；新表主键都是服务侧 Snowflake | Web 引入 `paytech-snowflake-id`（Risk/CCT 已使用） |
| 锁机制 | 规范：需要跨实例协调的新单资源写 Processor 使用 Foundation `AbstractLockProcessor`（依赖 Redis）；Finance 目前无 Redis | **待阶段 3 决定**，见 §5 风险 R1 |
| Content Hash | 人工调整 `content_hash` 要求“覆盖申请头和有序明细的规范化 SHA-256”，未定义精确输入 | 阶段 3 定义确定性 Hash 输入（字段顺序、金额规范化），是否使用 Foundation RFC 8785 JAR 同时决定 |
| 操作人身份 | `maker_id/checker_id/actor_id` 为平台用户 BIGINT；Finance 无入站身份上下文 | 本期由内部接口请求显式传入操作人 ID，并在合同中标明“未接统一身份，仅内部开发接口”，与余额接口同口径 |
| 期间生成 | 期间 FUTURE→OPEN 需要入口；本期不做关账 | 提供创建日历/期间、开放期间接口；不提供 CLOSING/CLOSED 流转 |
| 账户创建 | 调整明细引用 `account_id`，账户必须先存在；Owner/Bucket/科目组合有约束（§4.1） | 显式创建账户接口，按文档约束校验组合 |
| 需求目录 | `requirements-site/content/requirements.ts` 只有 FIN-I01—I22，无人工调整及基础资料维护接口 | 阶段 3 定义新接口编号，阶段 4 同步目录并运行 `npm --prefix requirements-site test` |

## 4. 逐系统影响

| 系统/仓库 | 业务职责 | 影响点 | 改代码/模型/配置 | 上下游与兼容风险 |
|---|---|---|---|---|
| Finance `paytech-finance-accounting` | 账簿、账户、Journal、调整的 Owner | 新增领域模型、Domain Service、Repository、Mapper（16 表） | 是 | 无外部消费者 |
| Finance `paytech-finance-api` | 对外 HTTP 契约 | 新增基础资料、人工调整接口；余额接口参数改为 entityNo | 是 | 余额接口不兼容变更；当前无 Gateway/BFF 消费 |
| Finance `paytech-finance-web` | 启动与编排 | Controller、Processor、事务；新增 Snowflake（及可能的 Redis）依赖与配置 | 是 | 新基础设施依赖需更新 README 的“当前不包含” |
| Finance `paytech-finance-test` | 自动测试 | 新测试基础设施与用例；旧余额测试迁移 | 是 | 本地 MySQL（及可能的 Redis）为前置 |
| Finance 数据库 `mor_finance`（本地） | Finance 独立库 | 删除旧 3 表，按新 Schema 建 16 表，执行初始化 DML | 需单独授权 | 旧表 0 行，无数据迁移；mor-test/生产不涉及 |
| Finance PDManer / `finance-schema.sql` | 设计源与完整 DDL | 16 表已存在于模型与完整 Schema，预计无结构变化 | 预计否（阶段 3 核对） | 若发现缺口需先改模型 |
| `finance-balance-query-schema.sql` | 旧切片 SQL | 被新 Schema 取代 | 已删除（阶段 4） | — |
| requirements-site | 需求目录 | 新增接口用例 | 是（文档） | 运行目录测试 |
| CCT / Commerce / Payment / Risk / Platform / Gateway / BFF | — | 无 | 否：本期不消费事件、不调用外部服务、不注册 Route | 后续 AUTO 记账批次再接入 |
| `agent-docs/CURRENT_IMPLEMENTATION.md` | 当前状态台账 | 完成后更新 Finance 行 | 是（文档，阶段 5） | 共享文件，只改 Finance 段落 |

## 5. 主要风险

| 编号 | 风险 | 影响 | 处理方向（阶段 3 定） |
|---|---|---|---|
| R1 | 锁机制选型：规范要求跨实例写 Processor 用 Redis `AbstractLockProcessor`；Finance 账务文档又明确用 `book_sequence FOR UPDATE`、账户升序锁与 CAS 作为过账保护 | 引入 Redis 会扩大 Finance 基础设施；不引入则需论证为何 DB 保护足够 | 推荐：人工调整提交/审批按 `adjustmentId` 用 `AbstractLockProcessor`（引入 Redis）；过账内部仍以数据库行锁 + CAS 为最终防线 |
| R2 | 锁顺序：设计文档 §7 要求写用例锁矩阵，避免反向取锁 | 死锁或序号/余额竞态 | 本期只有过账与调整两个写用例，阶段 3 给出固定顺序：Adjustment → Instruction → Period → Book Sequence → Account（ID 升序） |
| R3 | 隔离级别：MySQL 默认 RR 下锁后读取需使用当前读 | 锁后用旧快照导致校验失效 | 锁后校验全部用 `FOR UPDATE` 当前读；双连接并发测试证明 |
| R4 | 余额接口不兼容变更 | 若有未登记的调用方会失败 | 已核对无 Gateway Route、无 BFF 调用；在合同中说明 |
| R5 | 操作人 ID 由请求传入 | 可伪造 Maker/Checker | 仅内部开发接口，不接 Gateway；接入统一身份前不得对外发布 |
| R6 | 金额精度：DECIMAL(24,8) 与 `currency_spec.minor_unit` | 超精度金额入账 | 提交时按币种 `minor_unit` 校验，超精度拒绝而不是自动舍入 |

## 6. 工作量依据

新增约 16 张表的 DataObject/Mapper/Repository，约 6 组领域服务（基础资料 5 类 + 过账 + 调整），约 10 个内部 HTTP 接口（基础资料创建/查询、调整 5 个动作、余额 2 个），以及新的测试基础设施和并发测试。属于 Finance 单服务、单库变更，不涉及跨系统联调。

## 7. 方案总览

已读：开发规范、Java 工程规范、公共写接口示例、FINANCE_DATABASE §4.1/§4.3/§6.1/§6.6/§7、TECHNOLOGY_STACK §6.7。

- **归属：** 16 张表全部由 `paytech-finance-accounting` 拥有。Processor 放 `accounting/service/processor`，Domain Service 放 `accounting/domainservice/service`，Controller/Converter 放 Web `ext/rest/accounting`。不涉及 Treasury/Tax，不新增模块依赖。
- **事务外步骤：** 无。本期没有外部调用、MQ 或补偿任务；所有写入都是本地原子事务。
- **R1 锁选型（按阶段 2 推荐方案，待本阶段确认）：** 人工调整审批/驳回、期间创建使用 Foundation `AbstractLockProcessor`（Finance 新增 `paytech-redis-starter`，`redis.coordination.enabled=true`）；其余写入口用 `AbstractProcessor`，靠唯一键和 CAS。过账内部的数据库行锁与 CAS 是最终防线，不因 Redis 锁而省略。

## 8. 接口设计

全部为 Finance 内部开发接口：不接 Gateway、不做 Owner/OE 授权；操作人 ID 由请求显式传入（风险 R5）。金额是币种主单位十进制字符串，64 位 ID 是十进制字符串。错误统一走 Foundation RFC 9457 Problem：参数错 400 `INVALID_REQUEST`，业务拒绝 400 `BUSINESS_CHECK_FAILED`，版本/锁冲突 409。新接口编号 **FIN-I23 维护会计基础资料**、**FIN-I24 人工会计调整**，阶段 4 补入 `requirements.ts`。

| # | Method / Path | 编号 | 请求要点 | 响应 | 锁 / 并发保护 |
|---|---|---|---|---|---|
| 1 | `POST /finance-entities/v1` | I23 | `entityCode`、`legalPartyId`、`displayName` | 201 `financeEntityId` | 唯一键 `uk_fin_entity_code/party`，冲突时 400 |
| 2 | `POST /ledger-books/v1` | I23 | `entityType`(OPERATING_ENTITY/FINANCE_ENTITY)、`entityId`、`currency`、`chartCode` | 201 `ledgerBookId` | 同事务插入 Book + `book_sequence(next_value=1)`；`uk_ledger_book_entity` |
| 3 | `POST /ledger-books/v1/{ledgerBookId}/accounts` | I23 | `accountCode`、`ownerType`、`ownerId`、`balanceBucket` | 201 `accountId` | `uk_account_identity` |
| 4 | `POST /fiscal-calendars/v1` | I23 | `entityType`、`entityId`、`calendarCode`、`timezone`(IANA)、`yearStartMonth` | 201 `fiscalCalendarId` | `uk_fiscal_cal` |
| 5 | `POST /fiscal-calendars/v1/{calendarId}/periods` | I23 | `fiscalYear`、`periodNo`、`startDate`、`endDate` | 201 `accountingPeriodId`，状态 FUTURE | Redis `fiscal-calendar:{id}` + 日历行 `FOR UPDATE` 作为日期不重叠的哨兵 |
| 6 | `POST /accounting-periods/v1/{periodId}/open` | I23 | 无 Body | 200，状态 OPEN | `status=FUTURE AND version=?` CAS |
| 7 | `POST /manual-adjustments/v1` | I24 | `ledgerBookId`、`accountingPeriodId`、`currency`、`reasonCode`、`fileId?`+`fileContentHash?`（成对）、`makerId`、`lines[{accountId, entrySide, amount, lineDescription}]`（≥2 行） | 201 `manualAdjustmentId`、`adjustmentNo`、`contentHash`、`status=SUBMITTED` | 新资源，无锁 |
| 8 | `POST /manual-adjustments/v1/{id}/approve` | I24 | `checkerId`、`approvedContentHash` | 200 `status=POSTED`、`journalEntryId`、`journalSequence`、`postingDate` | Redis `manual-adjustment:{id}`（TRY_LOCK）+ 状态 CAS + 过账行锁 |
| 9 | `POST /manual-adjustments/v1/{id}/reject` | I24 | `checkerId`、`approvedContentHash` | 200 `status=REJECTED` | 同上 Redis 锁 + CAS |
| 10 | `GET /manual-adjustments/v1/{id}` | I24 | — | 头、明细、审批记录、关联 Journal | 无 |
| 11 | `GET /balances/v1/clients/{clientId}?entityNo=&currency=` | I01 | `operatingEntityId` **改为** `entityNo` | 不变 | 无 |
| 12 | `GET /balances/v1/partners/{partnerId}?entityNo=&currency=` | I02 | 同上 | 不变 | 无 |

**关键校验：**
- **账簿：** 币种必须在 `currency_spec` 中且为 ACTIVE；科目表 ACTIVE。FE 类型要求本地 Finance Entity ACTIVE。OE 类型只校验 entityNo 格式（`[A-Z]{2}` + `001`—`999`），不调用 CCT 核对 OE 是否存在，在合同中写明。
- **账户：** 科目属于账簿采用的科目表且 `posting_allowed=1`；`normal_side` 从科目复制；Owner/Bucket 矩阵见下方。
- **期间：** `startDate ≤ endDate`；同一日历内日期不重叠；年度与期号唯一。本期不提供 CLOSING/CLOSED/REOPENING 流转。
- **调整创建：** 账簿 OPEN；期间 OPEN 且属于账簿主体的日历；币种等于账簿币种；每行账户属于该账簿且为 ACTIVE；金额 > 0，小数位不超过币种 `minor_unit`（超出拒绝，不舍入）；借贷合计相等且 > 0。
- **审批/驳回：** Checker ≠ Maker；`approvedContentHash` 等于 `content_hash`；只能从 SUBMITTED 进入。
- **重放：** 已 POSTED（或 REJECTED）时，如果同一 Checker、同一 Hash 再次提交同一决定，返回原结果（AC-7）；其他情况 400“调整已处理”。

**账户 Owner / Bucket 矩阵（本期执行规则）：**

| 科目 | 允许 ownerType | 允许 balanceBucket |
|---|---|---|
| `CLIENT_PAYABLE` | CLIENT | PENDING / AVAILABLE / LOCKED |
| `PARTNER_PAYABLE` | LOCAL_PARTNER | PENDING / AVAILABLE / LOCKED |
| `CLIENT_SECURITY_DEPOSIT`、`CLIENT_RECOVERY_RECEIVABLE` | CLIENT | NONE |
| `PARTNER_RECOVERY_RECEIVABLE` | LOCAL_PARTNER | NONE |
| 其他 24 个科目 | OPERATING_ENTITY / FINANCE_ENTITY / CHANNEL_ACCOUNT / CASH_ACCOUNT / TAX_AUTHORITY | NONE |

ownerId 格式：OPERATING_ENTITY 用 entityNo，其余用规范十进制正整数字符串。

## 9. 过账内核与锁顺序

`JournalPostingService.post(...)` 是 Accounting 的 Domain Service，必须在调用方已有事务内执行（无事务直接失败）。后续 AUTO 记账批次复用它。

```text
审批事务（ApproveManualAdjustmentProcessor 开启一个 TransactionTemplate）
  1. manual_adjustment  CAS：SUBMITTED → APPROVED（version）；插入 CHECKER 审批记录
  2. 插入 accounting_instruction（MANUAL、VALIDATED、input_hash=content_hash）及 instruction_line（逐行对应调整明细）
  3. JournalPostingService.post：
     a. accounting_period  FOR SHARE —— 必须 OPEN、属于账簿主体日历、posting_date 落在区间内
     b. ledger_book        普通读 —— 必须 OPEN（本期没有账簿状态变更入口）
     c. book_sequence      FOR UPDATE —— 取 next_value 并 +1
     d. account            按 ID 升序 FOR UPDATE —— 必须 ACTIVE、属于本账簿
     e. 校验借贷平衡；插入 journal_entry（ADJUSTMENT）和 journal_line
     f. 逐账户更新 debit/credit_total、net_balance（按 normal_side）、last_journal_sequence，条件 balance_version=旧值
     g. instruction CAS：VALIDATED → POSTED
  4. manual_adjustment  CAS：APPROVED → POSTED
任一步失败整体回滚：序号、余额、状态都不留痕（AC-2）。
```

- **全局锁顺序：** Redis 调整锁 → 调整行 → 期间（共享）→ 账簿序号（排他）→ 账户（ID 升序，排他）。没有反向取锁。期间加共享锁，所以同主体不同币种的账簿可以并行过账，未来关账流程取排他锁即可阻断过账。
- **隔离级别：** MySQL 默认 RR。锁后的所有校验都用当前读（`FOR SHARE`/`FOR UPDATE`），不复用事务前查询的快照（R3）。
- **posting_date：** 取审批时刻在日历时区的日期，再收敛到期间 `[start_date, end_date]` 区间内。调整表没有过账日期字段，所以采用这个确定性规则。
- **余额计算：** 借方正常账户 `net = debit_total - credit_total`，贷方正常账户 `net = credit_total - debit_total`，允许为负（AC-4）。

## 10. Content Hash

`content_hash = SHA-256 小写 hex( RFC 8785 canonical JSON )`，使用 Foundation `paytech-json-canonicalization`（Finance 是实际消费者，按 RFC 8785 决定只由 accounting 模块直接依赖）。输入对象：

```json
{"schema":"CODE-MOR:FINANCE:MANUAL_ADJUSTMENT:1","adjustmentNo":"MA…","ledgerBookId":"…","accountingPeriodId":"…",
 "currency":"USD","reasonCode":"…","fileId":null,"fileContentHash":null,"makerId":"…",
 "totalDebit":"100.00","totalCredit":"100.00",
 "lines":[{"lineNo":1,"accountId":"…","entrySide":"DEBIT","amount":"100.00","lineDescription":"…"}]}
```

金额按币种 `minor_unit` 补齐为普通十进制字符串，ID 一律用字符串；明细按 `lineNo` 升序排列。`adjustmentNo` 由服务端生成，格式为 `MA` + 调整主键。创建请求本身不做业务防重（不同时间的两次申请是两条独立调整，各自都要审批）；HTTP 层的重复请求由 Gateway 的 `Idempotency-Key` 负责。

## 11. 代码落点

| 层 | 新增 / 修改 |
|---|---|
| `paytech-finance-api` | `accounting/` 下新增 `FinanceEntityCommandApi`、`LedgerBookCommandApi`、`FiscalCalendarCommandApi`、`AccountingPeriodCommandApi`、`ManualAdjustmentApi`，以及 request/response class；`BalanceQueryApi` 参数改为 entityNo；`BalanceModels` 拆成独立 ResponseBody class（对象形态规范：DTO 用 class，不用 record） |
| `paytech-finance-accounting` | 16 张表的 DataObject、Mapper（XML）、Repository；内部枚举放 `core/enums`（如 `InstructionStatus`、`AdjustmentStatus`、`PeriodStatus`、`EntrySide`）；Domain Service：`LedgerSetupService`（FE/账簿/账户）、`AccountingPeriodService`（日历/期间/开放）、`ManualAdjustmentService`（提交/审批/驳回保存）、`JournalPostingService`（过账）；9 个 Processor 及 Context；`ManualAdjustmentContentHasher`（纯计算）；余额 Mapper 改为字符串身份 |
| `paytech-finance-web` | Controller + Converter；依赖 `paytech-snowflake-id`、`paytech-redis-starter`；Redis 配置；README 更新 |
| `paytech-finance-test` | 按 UNIT_TEST_GUIDE 新建 `test/base/MockTestBase`、Builder、本地 Mocker、Checker、`TestMapper`；新用例见第 13 节；旧余额测试迁移 |
| 文档 | 删除 `finance-balance-query-schema.sql`；新增 `finance-initial-data.sql`；更新 `balance-query-api.md`、README；`CURRENT_IMPLEMENTATION.md` 的 Finance 段落在阶段 5 更新（`requirements.ts` 不改，见第 15 节） |

## 12. 数据库变更与执行计划

| 项 | 内容 |
|---|---|
| Schema DDL | 无新结构；16 张目标表沿用 `finance-schema.sql`（与 PDManer 一致，阶段 4 再逐字段核对 DataObject 映射） |
| 本地迁移 | [db/ddl.sql](./db/ddl.sql)：删除旧切片 3 张空表，然后执行完整 `finance-schema.sql`（84 表），再执行 `finance-initial-data.sql` |
| 初始化 DML | 新文件 `finance-service/docs/database/sql/finance-initial-data.sql`：USD 1 行、STANDARD 科目表 1 行、核心科目 29 行；固定 ID 小于 100 万 |
| 注册 DML | 0 |
| 目标环境 | 仅本地 `mor-local-mysql-1` / `mor_finance`；不涉及 mor-test 或生产 |

选用完整 84 表而不是只建 16 张，是因为 `finance-schema.sql` 是唯一的权威执行来源，只抽出部分表等于造了一份副本。其余 68 张表本期不读不写。

## 13. 测试与验收映射

测试按 UNIT_TEST_GUIDE 从真实 HTTP 入口运行，使用正式启动类、本地 MySQL 和本地 Redis，不用 Mockito。

- **数据清理：** 全局 BeforeEach/AfterEach 清空 16 张业务表，但保留初始化 DML 的固定 ID 行（这部分是只读的全局基础数据）。
- **入口测试类：** 每个 API 一个测试类，放在 `auto/ext/http/accounting/...`。
- **测试辅助：** Mocker 直接插入账簿、期间、账户等前置数据；Checker 读取 Journal 和账户并断言。

| AC | 主要用例 |
|---|---|
| AC-1 | 审批过账后 Journal 借贷相等、单账簿、金额 > 0；Journal 表没有更新语句，由 Mapper 审查和测试共同保证 |
| AC-2 | 审批前通过 TestMapper 把一个账户设为 INACTIVE：审批失败，调整仍是 SUBMITTED，序号、余额、Instruction 都没有写入 |
| AC-3 | 两个线程并发审批同一账簿的不同调整：得到序号 1 和 2，余额等于两笔之和；并发审批同一调整：一个 POSTED，另一个 409 |
| AC-4 | 贷方正常账户被借记后 `net_balance` 为负；从 `journal_line` 重算的结果等于账户投影 |
| AC-5 | 期间为 FUTURE/CLOSED、账户 INACTIVE、账户属于其他账簿、币种不符、精度超限、借贷不平：分别返回 400 且带上下文 |
| AC-6 | Maker = Checker、Hash 不符：拒绝；驳回后再审批：拒绝 |
| AC-7 | 同一 Checker、同一 Hash 重复审批：返回同一 `journalEntryId`，只有一条 Instruction |
| AC-8 | 余额查询使用 entityNo；审批后的结果等于 Journal 重算值 |
| AC-9 | `JAVA_HOME=JDK25 ./mvnw clean verify` 全部通过；`npm --prefix requirements-site test` 通过 |

另外还要覆盖 Redis 锁相关场景：锁竞争返回 409；Redis 不可用时返回 503（测试连到一个不存在的 loopback 端口）。

## 14. 已知限制

- OE 是否存在不向 CCT 核对；操作人 ID 由请求传入：两者都要等统一身份和 CCT 接入后再处理。
- 不提供账簿/账户列表查询（FIN-I05 账本流水留给后续批次）：创建接口会返回 ID。
- 不提供期间关账/重开、账簿停用、账户停用入口；测试通过 TestMapper 构造这些状态。

## 15. 研发实现偏差（阶段 4）

| 项 | 设计稿 | 实际实现 | 原因 |
|---|---|---|---|
| 接口编号 | 新增 FIN-I23、FIN-I24 并写入 `requirements.ts` | 不新增编号；API JavaDoc 标注所属内部能力（Finance Entity、Ledger & Account Balance、Accounting Instruction）与 FIN-10 | requirements-site 测试锁定 Finance 内部能力用 `internalDomains` 表达、对外接口用例最大为 FIN-I22；这些内部域已在目录中，本次不改目录 |
| 字段校验错误 | 统一写作 400 | 请求体 Bean Validation 失败为 Foundation 合同的 422 `VALIDATION_ERROR`；Processor 内的参数关系校验为 400 `INVALID_REQUEST`；业务拒绝为 400 `BUSINESS_CHECK_FAILED`；锁冲突为 409 | 沿用共享 GlobalExceptionHandler，不在 Finance 重写映射 |
| 余额查询事务 | 未说明 | 去掉旧切片的只读 RR 事务 | Owner 全部账户由一条 SQL 读取，单语句即一致快照，账簿身份不可变；符合“只读查询默认不开事务”规范 |
| 旧测试 | 迁移 | `BalanceQueryContractAutoTest`（record 构造器校验与 operatingEntityId 契约）删除，场景由 HTTP 入口测试覆盖；`FinanceApplicationAutoTest`、`FinanceModuleBoundaryAutoTest` 改为 `*Test` 并迁到 mock Profile / 新依赖白名单；`application-auto.yml` 由完整 `application-mock.yml` 取代 | DTO 改为 class 后不再在构造器校验币种；测试规范要求 MockTestBase + mock Profile |
| 既有分层 | 未列出 | 余额切片的 record DataObject/模型改为 class，`domain/enums`、`domain/constants` 迁到 `core/enums`、`core/constant` | 本次改动涉及这些类型，按现行对象形态与包规范迁移 |
| Redis 不可用 | 测试 503 | 未覆盖 | Redisson 在启动时连接，无法在同一上下文中模拟依赖不可用；锁冲突 409 已通过预占同名锁覆盖 |
