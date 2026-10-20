# ledger：自动记账按业务时间选择规则版本 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-20 | 来源 | 讨论记账规则时发现：规则切换后迟到事实被拒绝并卡住分片；用户选择方向一“按业务时间找当时生效的版本” | 已确认 |
| 2026-10-20 | 1—3（合并评审） | [需求](./requirement.md)、[技术方案](./technique.md)；用户：按建议，Q1 一起修，阶段 1—3 合并 | 已确认 |
| 2026-10-20 | 4 研发 | ledger 15 个文件（生产 10、测试 5，含新增 `RuleVersionSelectionPostingTest`）；Operations Web 3 处提示（`RuleVersionDrawers.tsx` 2、`FinanceAccountingRules.tsx` 1）。研发在独立 worktree 完成后合入主工作区（未提交） | 完成 |
| 2026-10-20 | 5 本地验证 | 本地 `mor_ledger` 落后 Schema，经用户同意按现行 `ledger-schema.sql` + `ledger-initial-data.sql` 重建（33 表、74 科目、36 规则行；重建前备份在会话临时目录）。三个相关测试类 32/32；ledger 模块全部 319/319、BUILD SUCCESS；Operations Web 构建通过、测试 1157/1157 | 通过 |
| 2026-10-20 | 6—7 mor-test 发布与验收 | 用户“部署吧”。ledger 镜像构建自干净 worktree（main `5f12cf3e` + 本需求改动，构建前该基线 ledger 测试 319/319），`mor-test/ledger-service:rulever-20261020` = `sha256:ff20e335…`；override [release/compose-candidate.yaml](./release/compose-candidate.yaml)（XXL 环境沿用财务发布），只 `up --no-deps ledger-service`，环境变量前后一致；回滚目标 `sha256:d846efae…`。Operations Web **未重新部署**：mor-test 当前 `105afba9…`（另一会话自主工作区构建）已包含本需求 3 处提示文字，重发会覆盖其后续改动。验收：健康 healthy；运营入口规则包列表 / 详情 / 现行版本详情均 200，`STANDARD_ADJUSTMENT` v1 失效时间 = v3 生效时间，区间首尾相接；启动后无 ERROR；XXL 任务 19 已注册执行。未执行 SQL、未写验收数据 | 完成 |
| 2026-10-20 | 8 提交与归档 | 用户“可以”。私有索引提交于 main（父 `5f12cf3e`，未推送）：ledger 15 文件、记账规则页提示 2 文件、本需求目录、`requirements/README.md` 与 `agent-docs/CURRENT_IMPLEMENTATION.md` 各一处；主工作区其他会话的未提交改动（ledger `BillingFactClientImpl` / `BillingFactMocker`、ops-web 其他页面）不在提交内 | 完成 |

### 阶段 4—5 说明

- AC 对应测试：AC-01 `RuleVersionSelectionPostingTest.test_lateFactUsesRetiredVersion`；AC-02 `test_factAtSwitchTimeUsesNewVersion`；AC-03 `test_dueApprovedVersionAppliesBeforeSwitch`；AC-04 `test_nonEffectiveVersionsNeverSelected`；AC-05 `TransactionPostingRejectionTest.test_businessTimeBeforeFirstVersionRejected` 与调整后的 `test_retiredCurrentVersionRejected`；AC-06 `test_sameFactSelectsSameVersionAcrossSwitch`；AC-07 `RuleVersionLifecycleTest.test_pastEffectiveFromRewrittenToApprovalTime`（并给 `test_futureEffectiveApprovalStaysApproved` 补断言）。
- 偏离方案：`test_approveActivatesAndRetiresPrevious` 随 Q1 调整为“旧版本失效时间 = 审批时间”；`ApproveRuleVersionProcessor`、`AccountingRuleBundleApi`、`CreateRuleVersionRequestBody` 的说明随 Q1 更新，接口合同不变。
- 已知边界（不在本期）：v2 已批准且到期、定时任务尚未切换的约 1 分钟内，若又立即审批 v3，v2 会被视为已取代；该分钟内先按 v2 处理的事实与之后重放的结果不同。修复需审批时先激活到期版本。
- 运行测试期间，测试清理组件按既有逻辑清空了本地 `mor_ledger` 业务表（重建前已为空，备份见上）。
