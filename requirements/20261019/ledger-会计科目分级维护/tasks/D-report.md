# 任务卡 D 报告：注册数据与文档

完成时间：2026-10-19。工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart`（未提交、未建分支）。

## 开工说明（WRITE_API_EXAMPLE 第 1 节）

本卡不新增或修改接口实现，不涉及 Processor / Context、本地原子写入或事务外步骤；只追加 Platform 路由注册 DML（人工执行的 SQL 文件，未执行）与修订文档。已读：00-common.md、D-registration-docs.md、requirement.md、technique.md 第 2 节、决定记录、`db/platform-support/registration-dml.sql`。

## 改动文件

| 文件 | 内容 | 对应 |
|---|---|---|
| `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾（块 99 之后）追加块 91，与阅读副本第 5 行起逐字一致（`diff` 无差异）；行数 route 8 / header 32 / query_param 8 / guard_rule 48 = 96 | technique 2.6、AC-07 路由前提 |
| `agent-docs/database/FINANCE_DATABASE.md` | §4.1 `chart_account` 行加 `account_no`、`account_status`、`uk_chart_acct_no`、编号规则、父子类别一致、采用后冻结范围与仍允许的操作；第 146 行段落改写（汇总在查询时计算、启停规则、维护不审批留痕、科目表锁）并新增可读账户号段；§4.1.1 开头补 STANDARD 两级树说明 | 决定第 2 节 1—7 条 |
| `paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md` | §4.11 改写（编号、启停修订"科目没有状态"、冻结范围、变更记录、科目表锁、`t_ledger_account.account_no` 与序列表）；第 7 节不变量清单的 Chart Account 行补编号、类别、停用约束（worktree 中为第 488 行，对应主工作区第 493 行） | 决定第 2 节 1—7 条 |
| `agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md` | 从主工作区原样复制（新文件） | — |
| `agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md` | 第 7 条末尾加一句补充引用 | 决定第 8 条 |
| `paytech/ledger/README.md` | 账户身份矩阵段后加"科目分级与账户号"一条 | — |
| `paytech/account/README.md` | 链路"账户状态"后加"账户号"一条 | — |
| `requirements-site/content/requirements.ts` | FIN-22 加按级次汇总；FIN-I05 加按账户号精确查询；科目规则描述加 4-2-2-2 编号、末级记账、启停、采用后冻结；新增 `FIN-25 维护分级会计科目`（新增 / 修改 / 停用 / 启用、留痕、采用后冻结、停用约束） | AC-01、05—08、11 |

`global-use-case-sequences.mjs` 未改：科目维护是 Finance 单系统基础资料操作，不构成跨系统 E2E 时序。`CURRENT_IMPLEMENTATION.md` 按卡片要求未改。

## ID 冲突核对

- worktree 与主工作区的 `platform-support-initial-data.sql` 改动前均无 `900000000000091xxx` 与 `900000000000095xxx` ID；`requirements/` 下其他 SQL 也未使用 914xx—917xx。块 95（commercial 会话）使用 95 段，不冲突。
- 块 91 引用的既有 ID 均存在且唯一：权限 97101、97102，路由 97410、97415、99401（target 900000000000000001 沿用既有块）。

## 执行的命令与结果

- `diff`（追加块 vs 阅读副本第 5 行起）：无差异。
- `npm --prefix requirements-site test`（worktree 无 node_modules，临时软链主工作区 `requirements-site/node_modules`，跑完已删除软链与构建产物 `.next/.vinext/.wrangler/dist/next-env.d.ts`）：
  - 改动前基线：137 tests，127 pass，**10 fail**；
  - 改动后：137 tests，127 pass，**10 fail**，失败用例名单与基线完全相同（均为既有失败：checkout immutable order context、Commerce 15-table model、Commerce ten routes、Commerce privacy decision、Java tests organized by entry、Payment refund capacity、Project context public control plane、shared writing conventions（命令幂等组件措辞）、current implementation ledger core model snapshots、current ledger Platform snapshot）。本次改动未引入新失败。

## 未运行 / 未完成

- 未执行任何 SQL（块 91 仅追加到文件，执行授权在阶段 5 / 6）。
- 未运行 `scripts/pdmaner` 模型测试：PDManer 模型由任务卡 A / B 修改，本卡未改模型。
- 未写启用脚本与能力清单 JSON（technique 2.7 的 D 行提到，但任务卡 D 未列入；需求目录 `db/platform-support/` 已有 activation / test-activate / test-deactivate 阅读副本）。

## 偏离与提示

- `FINANCE_DATABASE.md` 与 `FINANCE_PHYSICAL_DECISIONS.md` 新增链接指向 `requirements/20261019/ledger-会计科目分级维护/` 与新决定记录；worktree 中需求目录尚不存在（只在主工作区），合并时需一并带入，否则链接暂时失效。
- `FINANCE_PHYSICAL_DECISIONS.md` §4.11 原文的 `t_account.account_status` 保留原写法（V3 实际表名为 `t_ledger_account`），新增账户号句子使用实际表名 `t_ledger_account`；未顺带修正旧表名。
- 主工作区 `FINANCE_DATABASE.md` 有未提交改动，与 worktree 版本不同；本卡只改 worktree 版本，合并时注意。
