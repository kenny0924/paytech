# C 银行层与处置线交付报告（阶段 4）

> 范围：technique.md §2.9 C 线。未提交、未推送、未部署；未对任何数据库执行写 SQL（本机 `mor_reconciliation` 只由测试自身插入并清理，结束时只读核对 0 行）；未调用 Superpowers skill。只改了 `paytech/reconciliation/**` 与本报告。

## 1. 已读依据

AGENTS.md；agent-docs/DEVELOPMENT_CONVENTIONS.md（全文）；JAVA_PROJECT_STRUCTURE.md（按 B 报告与现有代码核对包结构）；examples/WRITE_API_EXAMPLE.md（全文，§11 自查见第 4 节）；UNIT_TEST_GUIDE.md 与 examples/UNIT_TEST_EXAMPLE.md；COMPENSATION_EVENT_GUIDE.md（与 B 的 Handler 实现对照）；VAULT_SERVICE_ARCHITECTURE.md（Protect / 幂等键 / 日志边界部分）；TECHNOLOGY_STACK.md §6.7。需求目录：requirement.md（AC-03、AC-09–AC-12、AC-14）、technique.md（§2.1 D-01–D-04、D-10、D-11；§2.3 #7–#11、#18–#28 与处置应用规则；§2.4；§2.5；§2.10 I-01–I-14）、db/reconciliation/ddl.sql、tasks/T0、A（§4）、B 报告。design-discuss/reconciliation/design.md §3 S1、§4 F2 / F3、§6.4、§7。记忆：maven-jdk25、tests-use-enums、processor-template-no-javadoc、test-restclient-503。参照源码：channel-connector `ConfigureChannelAccountProcessor`（Vault Protect 与幂等键）、Vault Java Client。

## 2. 改动（按领域）

**core**
- 锁（`ReconciliationLocks` 追加）：`reconExceptionKey`、`bankStatement`、`cashAccount`、`cashAccountScope`、`bankMatchSuggestion`、`bankMatch(lineId, movementIds)`；全部经 `LockCommand.tryLocks`。
- 配置：`core/configuration/BankMatchSuggestionProperties`（`reconciliation.bank-match.suggestion-window-business-days`，默认 3），已登记 application.yml、Nacos 模板、application-mock.yml。
- 领域：`BankMatching`（带符号贡献、剩余、状态推导、工作日差）、`BankMatchContext`（确认匹配不变量 `check()`）、`ReconResolutionRules`（异常类型 → 处置方式、来源对象 → 归类码、内容摘要、是否发调整事件）、`CashAccountReviews`（账户复核摘要）、`ResolutionApplyContext`；View `ReconExceptionDetailView`、`ReconSummaryView`（三个聚合结果行）。
- 服务（interface + Impl）：`CashAccountService`、`BankStatementService`、`BankMatchService`、`ReconResolutionService`、`BankReconciliationQueryService`、`ReconExceptionQueryService`；扩展 B 的 `ExpectedBankMovementServiceImpl`（同事务登记 `BANK_MATCH_SUGGEST`，子类型 `EXPECTED_MOVEMENT-<id>`）与 `ReconciliationEventQueryService`（两类载荷）、`ReconciliationEventPayloads`（两类 JSON）。
- Processor：CreateCashAccount / RequestCashAccountClosure / ReviewCashAccount；Start / Append / CompleteBankStatementImport；SuggestBankMatches；Confirm / Reject / CreateBankMatches；OpenReconException；Submit / ReviewReconResolution。
- Handler：`BankMatchSuggestHandler`（键 `cash-account:<id>`，子类型 `BANK_STATEMENT-<id>` / `EXPECTED_MOVEMENT-<id>`）、`ChannelSettlementConfirmedHandler`、`ReconAdjustmentApprovedHandler`（继承 `AbstractEventPublishHandler`）。
- 持久化扩展：`BankStatementLineRepository.listByStatementAndLineNos / deleteByStatement / sumUnmatched`；`ChannelStatementRepository.sumInTransit`；`ReconExceptionRepository.sumOpenByAge`；`selectOpen` 只取已 IMPORTED 对账单的流水；分页排序按合同改为银行匹配 `create_time, id` 升序、预期流水 `expected_date, id` 升序、银行流水（按对账单查为行号升序，跨对账单为起息日升序）。

**app**：`ext/rest/{CashAccountController, BankStatementController, ExpectedBankMovementController, BankMatchController, ReconExceptionController, ReconResolutionController, ReconSummaryController}`，`converter/{CashAccountConverter, BankReconciliationConverter, ReconExceptionConverter}`。

**test**：基类 `test/base/AbstractBankReconciliationTest`；Builder `BankStatementImportBuilder`；测试类见第 3 节（新增 26 例）。

## 3. AC → 测试（`paytech-reconciliation-test/.../auto/ext/`）

| AC | 测试 |
|---|---|
| AC-03 | `http/CashAccountApiTest`：Vault Protect（类型 / 用途 / REFERENCE / 幂等键前缀）后只存令牌与末四位；响应与日志（OutputCapture）不含明文；提交人不能复核、摘要不符、拒绝新建 → CLOSED、重复复核拒绝；第二个 ACTIVE 被拒；关闭申请 → 拒绝 → 再申请 → 批准 CLOSED；主体标识不符零调用；Vault 断连 503 且不落库；当前账户只返回主键 + 末四位 |
| AC-09 | `http/BankStatementImportTest`：三步导入与行号幂等；期初 + Σ ≠ 期末与起息日越界整单 REJECTED（逐行原因、流水删除、重复完成不再返回原因）；同号不同内容、金额 0 在追加时拒绝；末四位 / 币种 / 期间 / 编号重复 / 精度；连续性：接得上不开、接不上开不阻塞 `BANK_STATEMENT_GAP`（差异 = 期初 − 上张期末）；`test_duplicateRejectedAndResend`：交易号已在其他对账单导入或本文件重复 → 计数（重发不重复计）、完成时整单 REJECTED 带逐行原因、库内不出现重复行；同编号更正重传复用原头并导入成功；IMPORTED / RECEIVING 同编号拒绝 |
| AC-10 | `http/BankMatchApiTest`：只给一对一建议（金额不等不建议）；确认后流水 MATCHED、预期 CONFIRMED、账单 CASH_CONFIRMED、一条 `ChannelSettlementConfirmed`（载荷核对），重复确认不重发；拒绝后不再建议；人工多选合并到账 + 借方抵扣（3 条事件）；超出流水金额、超出预期剩余、跨银行账户、重复预期流水、匹配不存在被拒且零写入；钱比账单先到：经扩展点生成预期流水后出建议，确认后 `BANK_LINE_UNMATCHED` AUTO_CLOSED。`compensation/BankCompensationHandlerTest`：±3 工作日窗口（周四出、周五不出）、方向 / 金额不符、身份非法终态、两个发布 Handler 终态失败 |
| AC-11 | `http/ReconResolutionApiTest`：三类来源人工开异常的差异与主体、同类型未结拒绝、默认只列未结、详情可用处置与归类码、账龄汇总；平台流水 CLASSIFY → SETTLED + `ReconAdjustmentApproved`；银行流水 CLASSIFY → MATCHED + 事件；银行层 RE_MATCH → 带处置 ID 的 CONFIRMED 匹配 + 到账事件；ACCEPT 结案不发事件；归类码不属来源、金额 ≠ 差异、类型不允许、字段缺失被拒；批准时剩余已变化整笔回滚。账单行 CLASSIFY 不单发（S1 用例） |
| AC-12 | 两类事件载荷字段在上述用例与 S1 用例中逐项断言；补偿目录已有登记；Handler 在事务外发布、失败可重试（沿用 B 基类） |
| AC-14 | `ReconResolutionApiTest.test_validationAndMakerChecker`（提交人复核被拒、摘要不符、拒绝回 OPEN 再提交序号 2、同人同决定重放）、`CashAccountApiTest.test_makerCheckerAndRejectCreate` |
| S1 全程 | `http/ReconciliationS1WalkthroughTest`：接口新建并复核银行账户 → PAYMENT 100（预估 0.50）、REFUND 30 → 账单含 2.00 调整行 → 经处置接口（Maker + Checker）归类 CHANNEL_FEE_OTHER → RECONCILED 67.55，`ChannelBatchReconciled` 带归类行（3、−2.00、处置 ID）→ 银行 67.40 无建议 → 人工匹配 → `ChannelSettlementConfirmed` → 开 `BANK_AMOUNT_MISMATCH` 0.15 → 归类 BANK_FEE → `ReconAdjustmentApproved` → CASH_CONFIRMED；每步核对在途汇总 |

## 4. WRITE_API_EXAMPLE §11 自查

- 合同：沿用 T0 api 未改 Body；内部 Request 为 class + Bean Validation；建议任务入口由 Handler 校验身份。含明文账号的 `CreateCashAccountRequest` 不用 Builder、不生成 toString。
- Controller / Converter 只做协议转换；每个写操作一个 Processor，主方法显示提前返回（已确认 / 已拒绝 / 已完成 / 已复核重放）。
- Processor 只查 Repository 或查询 Service（`BankReconciliationQueryService.loadMatchContext` 统一装载匹配上下文），写入经 Service；多 Service 原子写由 Processor 开 `TransactionTemplate`：完成导入（IMPORTED + 登记建议 + 开 GAP）、确认匹配（匹配 + 流水 / 预期 / 账单 + 事件 + 自动结案）、提交处置（处置 + UNDER_REVIEW）、批准（APPLIED + RESOLVED + 应用 + 事件 + 门禁重算）。
- 事务外：Vault Protect 在 `CreateCashAccountProcessor`（不注入 TransactionTemplate，满足 R7），Service 无外部调用。
- Context：`BankMatchContext`、`ResolutionApplyContext` 持本模块 DataObject；条件 SQL 成功后由 Repository 同步对象。
- 锁：账户（关闭、复核 + 范围锁）、对账单（追加、完成）、流水 + 预期流水（确认、拒绝、人工匹配、银行层处置）、异常（提交、复核，复核另加应用对象锁：渠道账单 / 流水 / 预期流水）、建议任务按银行账户；开始导入、人工开异常不加锁，由 uk 兜底。版本 CAS 仍是最终防线。
- 业务防重：对账单编号、行号同内容、交易号、建议对（任意状态出现过不再建议）、异常 active_slot、处置序号；无 Idempotency-Key 处理。
- View 只用于多表组合（异常详情、在途汇总）；DataObject 不出 core / app。
- 没有生产 Mock 或测试分支。验证与未覆盖见第 5、6 节。

## 5. 验证（JDK 25，`paytech/` 下）

| 命令 | 结果 |
|---|---|
| `./mvnw -B -o -pl reconciliation/paytech-reconciliation-api,reconciliation/paytech-reconciliation-core,reconciliation/paytech-reconciliation-app install -DskipTests` | BUILD SUCCESS |
| `./mvnw -B -o -pl reconciliation/paytech-reconciliation-test verify` | 90/90 通过（B 的 64 + C 新增 26），含 ArchitectureAutoTest、ModuleBoundaryTest，BUILD SUCCESS |
| 只读 `SELECT COUNT(*)` 核对 `mor_reconciliation` 业务表、补偿与消费表 | 全部 0 行 |

没跑：全仓 reactor、其他服务、`npm --prefix requirements-site test`（本线未改站点）。

## 6. 偏差、决定与待确认

1. **流水金额必须 > 0（协调人追加）**：API 正则仍允许 0，服务在追加时逐行拒绝，原因见第 7 节；`BankStatementImportTest.test_rejections` 覆盖。
2. **起息日须在对账单期间内（协调人追加，采纳建议）**：完成导入时逐行校验，不在期间内整单 REJECTED，带行号原因。
3. ~~重复交易号跳过并计数~~：已按主会话决定改为整单拒绝，见第 8 节。
4. ~~被拒对账单占用编号~~：已按主会话决定支持同编号更正重传，见第 8 节。
5. **建议规则细节**：只对 UNMATCHED 流水建议，比较“流水剩余 = 预期剩余”；币种一致由同一银行账户保证；任何状态出现过的同一对（含已拒绝）不再建议；已有 PROPOSED 的预期流水不再配对。工作日只排除周末。
6. **渠道账单到账确认金额 = 预期流水已确认 + 已归类**，满额 CASH_CONFIRMED；汇总的“未确认”= 净额 − 该值。
7. **处置方式可用范围**（`ReconResolutionRules`）：UNEXPLAINED_ADJUSTMENT 仅 CLASSIFY；CHANNEL_LINE_UNMATCHED / AMBIGUOUS_REFERENCE / AMOUNT_MISMATCH 为 RE_MATCH、CLASSIFY；DUPLICATE_SETTLEMENT、PLATFORM_ITEM_UNSETTLED、BANK_AMOUNT_MISMATCH 为 ACCEPT、CLASSIFY；BANK_LINE_UNMATCHED 三种都可；其余（COST_DEVIATION、BANK_STATEMENT_GAP 等）仅 ACCEPT。银行层允许 ACCEPT 是对 technique（只列了交易层三类）的补充。
8. **差异金额符号**：平台流水 应收 +/应付 −；预期流水 = 预期 − 已确认 − 已归类（不足为正）；银行流水 贷 +/借 −；GAP = 本张期初 − 上张期末。归类金额必须等于差异，批准时对预期流水 / 银行流水再按当前剩余校验，变了就整笔回滚。
9. **人工开银行层异常不改流水的匹配状态**（不置 EXCEPTION），以便“钱先到”的流水仍能被建议；确认使流水满额 / 预期流水满额时自动结案 OPEN 的 BANK_LINE_UNMATCHED / BANK_AMOUNT_MISMATCH（UNDER_REVIEW 不动）。
10. **银行流水归类**只把匹配状态置 MATCHED，`matched_amount` 不变（归类不是匹配）。
11. **Vault 幂等键** = `recon-cash-account.` + SHA-256（提交人与全部输入，含账号），沿用 channel-connector 先例；同一命令重试得到同一 Token。
12. **在途汇总**的 entityNo 过滤：渠道账单经清算账户、异常按 OPERATING_ENTITY 主体、银行流水经银行账户主体（财务主体的银行账户只在不传 entityNo 时计入）。
13. 改动了 A / B 文件：`ReconciliationLocks`、`ExpectedBankMovementServiceImpl`、`ReconciliationEventQueryService(Impl)`、`ReconciliationEventPayloads`、上述 Mapper / XML / Repository 与排序。

## 7. 拒绝原因文本（供格式说明对齐）

追加流水（HTTP 400 `BUSINESS_CHECK_FAILED`，`detail`）：
- `同一段内行号重复: 对账单 {id} 第 {n} 行`
- `行号超出声明行数 {N}: 对账单 {id} 第 {n} 行`
- `流水金额必须大于 0: 对账单 {id} 第 {n} 行`
- `对账单 {id} 第 {n} 行 金额 的小数位超过币种 {CCY} 的精度 {d}: {amount}`
- `同一行号已接收不同内容: 对账单 {id} 第 {n} 行`
- 银行交易号重复不在追加时拒绝，见下方完成导入的逐行原因。

完成导入（REJECTED，`rejections[]` 整单原因在前、逐行原因按行号升序；`rejectReason` = `校验不通过，共 {k} 条原因：…`，截断 1000 字）：
- 整单：`已接收 {a} 行、交易号重复 {d} 行，与声明行数 {N} 不一致`
- 整单：`期初余额 {o} 加流水合计 {s} 等于 {c}，与期末余额 {closing} 不一致`（金额按币种精度；有交易号重复行时不给本条）
- 整单：`另有 {k} 行银行交易号重复，原因超出记录长度未逐行列出`（逐行原因累计超过 1000 字时）
- 逐行：`银行交易号 {ref} 已在对账单 {statement_ref} 导入，请上传不重叠的对账单`
- 逐行：`银行交易号 {ref} 与第 {m} 行重复`
- 逐行：`起息日 {date} 不在对账单期间 {start} 至 {end} 内`

开始导入（400）：`银行账户不存在或不是 ACTIVE: {id}`；`对账单的银行账号末四位与所选银行账户不一致: …`；`对账单币种必须等于银行账户币种: …`；`对账单期间结束日早于开始日: {ref}`；`同一银行账户的对账单编号已存在: 银行账户 {id}, 编号 {ref}, 状态 {RECEIVING|IMPORTED}`（REJECTED 的同编号改为复用重传）；余额精度同上格式。

## 8. 后续（主会话 2026-10-20 决定，不改 DDL）

1. **交易号重复整单拒绝**：追加时，同一银行账户已在其他对账单导入的交易号、或本文件另一行已用的交易号，受 uk 约束不入库；计入 `duplicate_line_count`，并把「第 n 行：原因」暂记在 RECEIVING 对账单的 `reject_reason`（`domain/BankStatementDuplicates`，按行号去重，同一段重发不重复计；接收中响应不外显该列）。完成时有重复行即整单 REJECTED，逐行原因见第 7 节；行数核对按「已入库 + 重复」覆盖全文件；没有重复行时余额恒等式覆盖全文件。AC-09（库内无重复行）保持。原先「内容不同 / 本单重复在追加时 400」改为完成时逐行原因。
2. **同编号更正重传**：开始导入时同一银行账户 + 编号已有 REJECTED 对账单，复用该行重置为 RECEIVING（新期间、期初期末、文件名 / Hash、声明行数、上传人，计数清零、拒绝原因清空、版本 +1；流水已在拒绝时删除），返回原主键；RECEIVING、IMPORTED 仍拒绝。新增 `BankStatementRepository.restart`（条件 `status = 'REJECTED'` + 版本）与 `BankStatementService.restart`；`updateImportProgress` 增加 `rejectReason` 参数。
3. 处置方式可用范围按第 6 节第 7 条接受。
4. 取舍：`reject_reason` 在 RECEIVING 时暂存逐行重复原因，是不改表结构下的做法；超过 1000 字后不再逐行记录，但仍计数并整单拒绝。
5. 验证：新增 `BankStatementImportTest.test_duplicateRejectedAndResend`（替换原跳过计数用例）；`paytech-reconciliation-test verify` 90/90 通过，BUILD SUCCESS；结束后只读核对相关表 0 行。
