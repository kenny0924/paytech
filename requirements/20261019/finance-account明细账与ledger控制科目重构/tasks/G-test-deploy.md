# 任务卡 G · 阶段 7 mor-test 部署与环境验收

用户 2026-10-19 回复"继续"，确认 `release/release.md`：D1=A（HEAD `2bae8597` + `source-overlay.txt` 441 文件，候选镜像 tag `subledger-20261019` 已构建）、D2=A（验收后清理回备份基线）、D3 无需动作（计数非 0 即停止回报）、D4=A（XXL 登记 `accountBalanceMaturity`、CRON `0 */10 * * * ?`、启用并手动触发一次）、D5 计数为 0 直接删，非 0 停止回报。

先读 `00-common.md`（git / Superpowers 禁令仍适用；本卡授权的环境动作例外）、`release/release.md` 全文（第 4 节执行顺序逐步执行，第 5 节恢复方案备用）、`tasks/F-report.md`、`verification/stage5-report.md`、前序先例 `requirements/20261018/operations-console-财务基础资料与账本余额查询/release/release.md` 第 6 节执行记录与其 `delivery.md` 阶段 7。

## 授权范围（只限 mor-test 环境与本机备份目录）

按 release.md 第 4 节第 1—11 步执行：复核 → 备份到 `~/Code-MoR-backups/<UTC>-subledger-test/`（核验 dump 可读与 SHA256SUMS）→ 停 account、ledger → 四份 SQL（root 与 platform_app，按顺序，保存输出）→ 用 `release/compose-candidate.yaml` 依次替换 Platform → billing → ledger → account → BFF → Web（`--no-deps --no-build --pull never --wait`，逐个核对 healthy、启动日志 ERROR 0 行、队列声明）→ 删 `ledger.billing.*` 旧队列与两个交换机（先计数，D5）→ XXL 登记并手动触发（D4）→ `release/test-activate.sql`（试跑 ROLLBACK 核对 3 / 1 / 1 后提交）→ 验收 `E2E_TARGET=test`（接口期望 58/58、浏览器 31/31 截图存 `assets/stage7/browser/`、全页面回归 34/34）→ `--cleanup` 并核对三库行数回到备份基线 → 记录。

**不在授权内：** 其他容器、其他库、`mor-test` 以外的环境、本机 `*_v2e2e` / `mor_*` 库、git 提交；若任一步与清单不符、健康检查失败、SQL 核对不等、队列计数非 0、验收失败，**立即停止**，保留现场，在报告中写明现象、影响和可选处置（含是否按第 5 节回退），由主会话决定，不要自行回退或重试超过一次。

## 报告

- 把执行记录写入 `release/release.md` 第 6 节（照前序：每步时间、命令摘要、核对输出、镜像 ID、验收计数、截图路径、清理基线对比）。
- 更新 `agent-docs/CURRENT_IMPLEMENTATION.md` 本需求条目（mor-test 已部署：镜像 ID、SQL 已执行、队列已删、XXL 任务、验收计数、备份路径）与 `agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md` 的 Implementation / Deployment status 行；`delivery.md` 由主会话写。
- `tasks/G-report.md` 摘要；最终回复三行：报告路径、验收计数、阻塞项（无则写无）。

## 执行状态

2026-10-19：主会话启动本卡的 agent 时被 Claude Code 自动权限模式分类器拒绝（「修改共享资源」）。待用户决定执行方式。

2026-10-19 10:40—10:55Z：用户"继续部署"后由 G agent 执行完成，第 1—11 步全部通过，见 `release/release.md` 第 6 节与 `tasks/G-report.md`。
