# ledger：自动记账按业务时间选择规则版本 · 技术方案

状态：**已确认并实施（2026-10-20）**；实施、验证与发布记录见 [delivery.md](./delivery.md)。需求见 [requirement.md](./requirement.md)。

**规模：** 1 个系统（ledger），新增 / 修改接口 0，DDL 0，DML 0，页面 0；ledger 修改 1 个领域方法、新增 1 个仓储查询（复用已有 SQL），测试新增 6 个场景、调整 1 个；Q1 若采纳，ledger 审批逻辑再改 1 处、Operations Web 改 1 句提示。

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 |
|---|---|---|---|
| R1 选版本 | `TransactionPostingServiceImpl.effectiveBundleVer`：只取 `bundle.current_ver_id`，要求其 ACTIVE 且区间覆盖业务时间，否则抛 `BizCheckFailException`（“业务时间无生效的自动记账规则版本”） | 在全部版本中取区间覆盖业务时间的版本 | 只看当前版本 |
| R2 待切换 | 审批时已到生效时间直接 ACTIVE；未来生效为 APPROVED，`AccountingRuleVersionActivationJobHandler` 每分钟切换，期间旧版本 `effective_to` 仍为空 | 已到期的 APPROVED 版本立即适用 | 切换前约 1 分钟内按旧版本记账，切换后同一业务时间按新版本，结果不确定 |
| R3 零长度 | `retireSuperseded` 把被取代的 APPROVED 版本置 RETIRED，`effective_to = effective_from` | 不参与 | 现状因只看当前版本而不受影响；新逻辑需显式排除（区间判断天然排除） |
| R4 拒绝 | 已有：规则包不存在 / 未启用 / 无当前版本 → “无可用自动记账规则集” | 保留；找不到覆盖版本 → 拒绝并带业务时间 | 文案调整 |
| Q1 回溯生效 | `approveVersion`：`effectiveAt(approvedTime)` 为真即 ACTIVE，`effective_from` 保留用户填写的过去时间；创建只要求晚于当前版本生效时间 | 审批时间晚于生效时间时，生效时间记为审批时间 | 新规则覆盖已过账时段 |

版本数据（mor-test `mor_ledger`，2026-10-20）：`STANDARD_TRANSACTION` v1 ACTIVE；`STANDARD_ADJUSTMENT` v1 RETIRED、v2 VOIDED、v3 ACTIVE。每个规则包版本数为个位数。

| 系统 | 影响点 | 改动 | 依赖与兼容 |
|---|---|---|---|
| ledger | 自动记账选版本（`TransactionPostingServiceImpl`）、`AccountingRuleBundleVerRepository`；Q1：`AccountingRuleServiceImpl.approveVersion` | 改代码与测试 | 只影响此后处理的批次；已过账分录不变；被拒绝而停住的批次在下一次重试时按新逻辑继续 |
| account / billing | 无 | 不改 | 事件合同不变 |
| Operations BFF / Web | 无（Q1 采纳时 Web 新建版本表单提示改一句） | Q1 时改 1 处文案 | 无接口变化 |
| Platform / Gateway | 无 | 不改 | — |

## 2. 方案

### 2.1 选版本规则（R1—R4）

`effectiveBundleVer(fact, bundleCode, at)` 改为：

1. 读取规则包；不存在或非 ACTIVE → 拒绝“无可用自动记账规则集”（不变）。
2. 读取该规则包全部版本（已有 `selectByBundle`，按 `bundle_ver` 倒序，个位数行）。
3. 当前 ACTIVE 版本为 `active`（可为空）。候选版本：
   - `ACTIVE` 或 `RETIRED`，且 `effective_from ≤ at` 且（`effective_to` 为空或 `at < effective_to`）；`RETIRED` 必须有 `effective_to`，为空视为数据损坏、不作候选；
   - `APPROVED`，且 `effective_from ≤ at`，且 `active` 为空或 `effective_from > active.effective_from`（排除已被取代、等待任务置 RETIRED 的版本）。
4. 取 `effective_from` 最大的候选；相同则取 `bundle_ver` 较大者。没有候选 → 拒绝：“业务时间无适用的自动记账规则版本: 计费事实 {factId}, 规则集 {bundleCode}, 业务时间 {at}”。

正确性依据：

- 生效版本链由 `switchCurrentVersion` 维护：旧版本 `effective_to` = 新版本 `effective_from`，区间首尾相接、不重叠；退役后区间不再变化，所以同一业务时间对应的版本固定（AC-06）。
- 零长度区间 `[from, from)` 不覆盖任何时点（R3）。
- 到期未切换的 APPROVED 版本按其生效时间立即参与，与任务切换后的区间完全一致（R2 / AC-03）。

实现位置：选择逻辑放在 `AccountingRuleService`（领域服务，新增 `findApplicableVersion(bundle, at)`，interface + Impl），`TransactionPostingServiceImpl` 调用它；不改 Mapper SQL，`AccountingRuleBundleVerRepository.findByBundle` 已存在。不加锁：读取的是已提交的版本数据，与审批 / 切换并发时，结果与先后顺序之一一致，且两种顺序选中的版本相同（由 R2 保证）。

### 2.2 Q1 回溯生效（待确认）

`approveVersion` 在同一事务内：若 `effective_from < approvedTime`，将版本 `effective_from` 更新为 `approvedTime` 后再置 ACTIVE 并切换（旧版本 `effective_to` 随之等于审批时间）。新建时的“晚于当前版本生效时间”校验不变。需在 `AccountingRuleBundleVerRepository.review` 增加可选生效时间参数（同一 UPDATE，不新增 SQL 语句）。Web 新建版本表单的提示由“已到的时间审批后立即生效”改为“审批时已过的生效时间按审批时间生效”。

### 2.3 兼容

- 无接口、事件、表结构、初始化数据变化。
- 规则包维护页面行为不变（Q1 除生效时间回写外）。
- 部署后，因版本切换而被拒绝的批次在 ledger 自动重试时按新逻辑记账；发布前查询 mor-test 是否存在停住的批次并记录。

## 3. 研发与验证计划

入口：ledger 现有自动记账测试基类 `AbstractTransactionPostingTest`（真实 `AccountPosted` 处理链路，本地 MySQL），Mocker 插入规则版本与规则行。

| 场景 | 验收 | 测试 |
|---|---|---|
| v1 退役 [T1, T2)、v2 生效：业务时间在区间内的迟到事实按 v1 科目记账 | AC-01 | 新增 |
| 业务时间 ≥ T2 按 v2 | AC-02 | 新增 |
| v2 APPROVED 已到期未切换：≥ T2 按 v2，< T2 按 v1 | AC-03 | 新增 |
| 零长度 RETIRED、VOIDED、REJECTED、DRAFT、PENDING_APPROVAL 不被选中 | AC-04 | 新增 |
| 业务时间早于首个版本 → 拒绝且无残留 | AC-05 | 新增 |
| 同一事实先按 APPROVED 处理、任务切换后重放，选中版本相同 | AC-06 | 新增 |
| 现有 `test_retiredCurrentVersionRejected`（把当前版本改成 RETIRED、失效时间为空） | AC-05 | 调整断言为新拒绝信息（RETIRED 缺失效时间不作候选） |
| Q1：审批时生效时间已过 → 版本与旧版本区间按审批时间 | AC-07 | `RuleVersionLifecycleTest` 新增 1 例 |

验证：ledger 模块单测（仅本模块，不跑全仓）、构建；Q1 时 Operations Web 测试与构建。发布：mor-test 只替换 ledger（Q1 时加 Operations Web），按镜像 ID 固定，回滚为当前运行镜像。
