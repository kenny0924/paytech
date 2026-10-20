# 任务卡 D：注册数据与文档

先读 `00-common.md`。范围：`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（仅追加块 91）、`agent-docs/**`、`paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md`、`requirements-site/**`。不改任何 Java / TS 代码。

1. **注册块 91：** 把需求目录 `db/platform-support/registration-dml.sql` 的块追加到 worktree 的 `platform-support-initial-data.sql` 末尾（块 99 之后），保持与阅读副本逐字一致；核对 ID 不与 95（commercial 会话今晚追加）及已有块冲突。
2. **文档：**
   - `agent-docs/database/FINANCE_DATABASE.md` §4.1（`chart_account` 行与第 146 行）与 `FINANCE_PHYSICAL_DECISIONS.md` §4.11、第 493 行：按决定记录第 2 节修订（编号、科目状态、冻结范围、采用前后可做的操作、汇总在查询时计算、可读账户号）。
   - 复制决定记录到 worktree `agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md`（来源为主工作区同名文件），并在 `2026-10-18-account-ledger-subledger-control-account.md` 第 7 条后加一句引用补充。
   - `paytech/ledger/README.md`、`paytech/account/README.md` 中科目 / 账户相关段落补一句现状（如有对应段落）。
   - 不改 `CURRENT_IMPLEMENTATION.md`（主会话在部署后统一更新）。
3. **requirements-site：** `content/requirements.ts` 更新 FIN-22（试算平衡按级次汇总）、FIN-I05（按账户号查流水）、科目规则描述（约第 562 行，加分级编号与启停）；按现有格式新增一个科目维护用例（新增 / 修改 / 停用 / 启用、留痕、采用后冻结）；必要时同步 `global-use-case-sequences.mjs`。运行 `npm --prefix requirements-site test`，报告通过数，并区分既有失败（先在未改动时跑一次记录基线）。
