# 财务控制台三项（人工调整与计费事实 / 期间关账与试算 / 记账规则包）：技术方案

状态：**阶段 2、3 按连续执行授权完成（2026-10-20）**。本文是三个需求的共用方案：[人工调整与计费事实](./requirement.md)、[期间关账与试算](../finance-期间关账与试算/requirement.md)、[记账规则包维护](../finance-记账规则包维护/requirement.md)。三个需求按顺序研发，合并一次 mor-test 发布。

**规模（本次方案）：** 涉及 5 个系统（ledger、billing、Operations BFF、Operations Web、Platform）；ledger 新增 4 个接口（人工调整列表、规则包列表、作废规则草稿、无 HTTP 的定时任务 1 个）、调整 13 个命令的操作人来源；billing 新增 2 个接口（计费事实管理列表、作废草稿）、调整 2 个命令；BFF 新增约 24 条转发与 1 个组合查询；不新增表，状态目录各加 1 个值（`VOIDED`），列注释更新；Platform 新增 1 个权限、3 个菜单与 7 个按钮资源、约 25 条路由（块 120—122）；新增 3 个页面、试算平衡页签加 1 个按钮。

## 1. 现状差异（阶段 2）

| 项 | 现状（main `e73764d1`） | 缺口 | 处理 |
|---|---|---|---|
| 操作人 | 人工调整、关账 / 重开、规则版本、计费事实命令都从请求体取 `makerId` / `checkerId`；基础资料命令已读 `X-Gateway-Actor-Subject` | 不能对外接入 | 改读 Header，去掉 Body 字段（D1） |
| 人工调整 | 提交 / 审批 / 驳回 / 详情齐全，复核比对内容哈希、禁止本人复核 | 无列表 | ledger 新增列表（L1） |
| 计费事实 | 登记 / 审批 / 详情；列表只含已批准 / 已替代，按主体游标 | 无草稿列表、无驳回 / 作废 | billing 新增管理列表（B1）、作废（B2，`VOIDED`） |
| 关账 / 重开 | 7 步关账、3 步重开、双人复核与关账哈希、按期间列表与详情 | 无跨期间看板 | BFF 组合日历期间 + 期间关账列表（F1） |
| 发起试算 | 接口在、无操作人 | 控制台无入口 | BFF 转发 + 试算页签按钮 |
| 规则包 | 版本生命周期齐全；规则包只能 SQL 建 | 无规则包列表、草稿不能作废、未来生效版本无人切换 | ledger 新增列表（L2）、作废（L3）、定时任务（L4） |
| 控制台 | 已有财务四个页面、块 91/97/98/99 | 无相关页面、无复核权限 | 新增 3 个菜单页、按钮、权限、路由；改 Platform 能力清单（Platform 镜像需重建） |
| 定时任务 | ledger 没有 XXL 执行器 | L4 需要 | ledger 引入 `paytech-xxl-job-starter`，Compose 增加执行器配置，mor-test 登记任务 |

影响系统：ledger、billing（代码、测试、Schema 注释）；Operations BFF、Web（代码、测试）；platform-support（`console-resource-capabilities.json` + 初始化 SQL 块 120—122）；test-environment（ledger XXL 执行器、BFF billing 地址、XXL 任务登记）；文档与 requirements-site。account、channel-connector、Gateway 不改。

## 2. 共用设计

**D1 操作人。** 下列命令改为 `@RequestHeader("X-Gateway-Actor-Subject") String operatorId, @RequestHeader("X-Gateway-Session-Id") String sessionId`，请求体删除 `makerId` / `checkerId`，Processor 以 Header 值作为 maker / checker：

- ledger：人工调整提交 / 审批 / 驳回；关账发起、重开发起、关账（含重开）审批 / 驳回；规则版本新建 / 提交 / 审批 / 驳回 / 作废；试算发起（只记日志，不落库）。
- billing：计费事实登记 / 审批 / 作废。

操作人格式沿用 `[1-9][0-9]{0,18}`（Operations 账号 ID）。既有业务规则不变：复核人不能是制单人；规则版本提交人必须是制单人。内部服务暂无调用方使用这些命令，测试相应改为传 Header。

**D2 权限。** 新增 `FINANCE_ACCOUNTING_APPROVE`（`900000000000120101`，HIGH，授予 PLATFORM_ADMIN，初始 REVOKED 由启用脚本激活）。路由绑定：查询 → READ；制单、发起、作废 → MANAGE；审批、驳回 → APPROVE。

**D3 菜单与按钮（块 120—122）。**

| 块 | 资源 | 类型 | 路径 / 父级 |
|---|---|---|---|
| 100 | `OPERATIONS_FINANCE_ADJUSTMENTS`「调整」 | MENU | `/finance/adjustments`，父 97301，sort 50 |
| 100 | `OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE`「制单」、`…_APPROVE`「复核」 | BUTTON | 父为上一行 |
| 101 | `OPERATIONS_FINANCE_PERIOD_CLOSE`「期间关账」 | MENU | `/finance/period-close`，sort 60 |
| 101 | `OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE`、`…_APPROVE` | BUTTON | 父为上一行 |
| 101 | `OPERATIONS_FINANCE_LEDGER_MANAGE`「发起试算」 | BUTTON | 父 97303（账本查询） |
| 102 | `OPERATIONS_FINANCE_ACCOUNTING_RULES`「记账规则」 | MENU | `/finance/accounting-rules`，sort 70 |
| 102 | `OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE`、`…_APPROVE` | BUTTON | 父为上一行 |

同步 `console-resource-capabilities.json` 与 Web `pageCapabilities.ts`（三个新组件及 actions；LEDGER 组件 actions 增加 `OPERATIONS_FINANCE_LEDGER_MANAGE`）。

**D4 错误码（2026-10-20 修订）。** Foundation `HttpServiceClientInterceptor` 不透传上游 `BUSINESS_CHECK_FAILED` 的 detail（设计如此），未登记的业务拒绝到页面只剩通用提示。因此为页面需要区分提示的拒绝新增错误码（Owner 以 `FailException(code)` 抛出，BFF 在 `HttpServiceClientFactory` 显式登记，Web 按 Code 显示默认消息或落到字段）：

| 枚举 | Code | 默认消息 |
|---|---|---|
| ledger `LedgerErrorCode` | `SELF_REVIEW_FORBIDDEN` | 制单人不能复核自己提交的单据 |
| | `REVIEW_CONTENT_CHANGED` | 单据内容已变化，请刷新后重新复核 |
| | `REVIEW_STATUS_INVALID` | 单据不在待复核状态 |
| | `ADJUSTMENT_UNBALANCED` | 分录借贷不平衡 |
| | `ADJUSTMENT_PERIOD_NOT_OPEN` | 会计期间未开放 |
| | `ADJUSTMENT_ACCOUNT_INVALID` | 账户不属于该账簿、已停用或不可记账 |
| | `PERIOD_CLOSE_ORDER_INVALID` | 之前的期间尚未关账 |
| | `PERIOD_NOT_OPEN` | 期间不是开放状态，不能关账 |
| | `PERIOD_REOPEN_NOT_ALLOWED` | 只能重开最近一期已关账期间 |
| | `RULE_VERSION_IN_PROGRESS` | 规则包已有草稿或待审批版本 |
| | `RULE_VERSION_STATUS_INVALID` | 版本当前状态不允许该操作 |
| | `RULE_SUBMITTER_NOT_MAKER` | 只有制单人可以提交或作废草稿 |
| | `RULE_LINE_INVALID` | 规则行不合法：科目、代码或借贷配置不符合要求 |
| | `RULE_EFFECTIVE_FROM_INVALID` | 生效时间必须晚于当前生效版本 |
| billing `BillingFactErrorCode`（新） | `SELF_REVIEW_FORBIDDEN` | 制单人不能复核自己提交的计费事实 |
| | `FACT_NOT_DRAFT` | 只能批准或作废草稿计费事实 |
| | `FACT_LINE_INVALID` | 明细行不合法：金额组件、收付方、目标余额桶或金额精度不符合规则 |
| | `FACT_SUPERSEDE_INVALID` | 被替代的计费事实不存在或不可替代 |

其余少见拒绝仍为公共 `BUSINESS_CHECK_FAILED`；Web 显示通用提示并保留上游 Request ID。

## 3. 需求 1：人工调整与计费事实

**L1 ledger `GET /manual-adjustments/v1`**：`ledgerBookId`（必填）、`accountingPeriodId`、`status`（SUBMITTED / POSTED / REJECTED）、`makerId`、`page`、`size`；按 `submitted_time` 倒序；元素：`manualAdjustmentId`、`adjustmentNo`、`ledgerBookId`、`accountingPeriodId`、`currency`、`reasonCode`、`status`、`totalDebit`、`totalCredit`、`makerId`、`submittedTime`、`journalEntryId`（可空）。用现有索引 `idx_manual_adj_book`。

**B1 billing `GET /billing/v1/facts/search`**：`entityNo`（必填）、`currency`（必填）、`clientId`、`status`（DRAFT / APPROVED / SUPERSEDED / VOIDED）、`factType`、`createdFrom`、`createdTo`、`page`、`size`；按 `id` 倒序；元素为事实头摘要（`factId`、`factType`、`sourceReference`、`clientId`、`status`、`grossAmount`、`makerId`、`checkerId`、`createTime`、`approvedTime`）。用现有 `idx_fact_book_oe`。

**B2 billing `POST /billing/v1/facts/{factId}/void`**：只允许 DRAFT → `VOIDED`（新状态，条件更新），不发布事件；已作废再作废直接返回；非草稿拒绝。`t_billing_fact.status` 注释加 `VOIDED`。

**BFF**：新增 billing Client（`OPERATIONS_BILLING_BASE_URL`，mor-test 为 `http://billing-service:18113`）；转发 L1、人工调整提交 / 审批 / 驳回 / 详情、B1、事实详情、登记、审批、作废。登记事实时 BFF 固定 `sourceSystem=OPERATIONS`，`sourceReference` 由页面生成（`OPS-` + UUID），重复提交返回原事实。

**Web「调整」页**：页签「人工调整」（账簿选择 → 列表 → 制单抽屉：期间、原因、分录行科目按"账户号 + 科目编号名称"选择，实时借贷合计 → 详情抽屉：分录行、哈希、审批记录、复核按钮）；页签「计费事实」（主体 + 币种 + 筛选 → 列表 → 登记抽屉：Client、明细行（金额组件、付方 / 收方、金额、方向、目标桶）、工单号、替代事实 → 详情：明细行、状态、批准 / 作废按钮）。

## 4. 需求 2：期间关账与试算

**F1 BFF 组合 `GET /finance/period-close/v1/board?fiscalCalendarId=`**：先取日历期间列表，再对每个期间取关账记录第 1 页（size 10）的最新一条，组装 `{periods:[{period…, latestClose}]}`；只读、无事务；期间数受日历限制（≤ 24）。

**转发**：发起关账、发起重开、审批、驳回、关账列表、详情（`PeriodCloseApi` 原路径）；发起试算（`TrialBalanceApi.runTrialBalance`）。

**Web「期间关账」页**：日历选择 → 期间看板（状态、最近记录、操作按钮：发起关账只对最早的开放期间显示；重开只对最近已关账期间显示）→ 关账详情抽屉（7 个步骤结果、账簿最终试算链接、结转指令、哈希、审批记录、复核按钮；失败时显示失败步骤与原因；试算异常只读列表）。试算平衡页签加「发起试算」（需 `OPERATIONS_FINANCE_LEDGER_MANAGE`）。

## 5. 需求 3：记账规则包

**L2 ledger `GET /accounting-rule-bundles/v1`**：返回全部规则包 `bundleCode`、`bundleName`、`status`、`currentBundleVer`、`currentEffectiveFrom`。

**L3 ledger `POST /accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void`**：DRAFT → `VOIDED`，只能制单人；`t_accounting_rule_bundle_ver.status` 注释加 `VOIDED`；新建版本的"已有草稿 / 待审批"检查不计入 VOIDED。

**L4 定时任务 `accountingRuleVersionActivation`**（ledger 新增 XXL 执行器，每分钟）：查找 `status=APPROVED AND effective_from <= now` 的版本，逐个复用现有生效逻辑（同规则包加规则包锁、旧 ACTIVE → RETIRED、当前指针切换），幂等。

**Web「记账规则」页**：规则包列表 → 版本列表 → 版本详情（按指令类型分组的规则行，科目显示编号 + 名称）→ 新建（以当前生效版本为模板，行编辑器增删改、生效时间）→ 提交 / 审批 / 驳回 / 作废。

## 6. 数据库

不新增表与字段。ledger、billing 各一份只改列注释的 change SQL（`status` 允许值加 `VOIDED`），PDManer 同步。Platform：块 120—122 注册 DML 与启用 / 停用脚本（权限 1、角色授权 1、资源 10、路由约 25 及 Header / 查询参数 / Guard 行）。mor-test：XXL 任务 1 条（ledger 执行器分组 + `accountingRuleVersionActivation`）。

## 7. 研发与发布

worktree `../Code-MoR-fin3`（基于当时 main）。任务卡：A ledger、B billing、C Platform 能力清单 + 注册 SQL + Compose + 文档 / requirements-site（与 A、B 并行），D BFF（A、B 合同定稿后），E Web（D 合同定稿后）。本机测试库 `mor_ledger_fin3`、`mor_billing_fin3`、vhost `/mor-fin3`。

mor-test 一次发布：ledger、billing、platform-support、operations-console-bff、operations-console-web 五个镜像；SQL：ledger / billing 注释、Platform 块 120—122 + 启用；XXL 任务登记；Compose 覆盖（ledger XXL 执行器环境变量与固定 IP、BFF billing 地址）。发布前通知 commercial（a3）与 channel-connector（b6）会话。验收：三个页面主流程经运营入口真实调用 + 浏览器检查。
