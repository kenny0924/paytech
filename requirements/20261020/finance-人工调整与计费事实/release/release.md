# 财务控制台三项：mor-test 发布清单

阶段 6（2026-10-20，按连续执行授权）。三个需求合并一次发布：[人工调整与计费事实](../requirement.md)、[期间关账与试算](../../finance-期间关账与试算/requirement.md)、[记账规则包维护](../../finance-记账规则包维护/requirement.md)。时间均为 UTC。

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop 的 `mor-test`；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306` |
| 源码 | commercial 第二切片合入后的 main + worktree `../Code-MoR-fin3` 本需求改动（rebase 后重新跑受影响模块测试） |
| 镜像（5 个） | ledger（操作人 Header、人工调整列表、规则包列表、作废草稿、`accountingRuleVersionActivation`、14 个错误码）、billing（操作人 Header、管理列表、作废、`BillingFactErrorCode`）、platform-support（能力清单：本需求 3 个菜单与 LEDGER 发起试算，并首次带上 commercial 块 93 / 95 的能力清单改动；运行中的能力清单与 `080909fb` 一致，已核对）、operations-console-bff（26 条转发、看板组合、billing Client、长耗时读超时 9 秒）、operations-console-web（调整、期间关账、记账规则三个页面与发起试算按钮） |
| SQL | `mor_ledger` ← `paytech/ledger/docs/database/sql/changes/2026-10-20-finance-console-statuses.sql`（只改注释）；`mor_billing` ← `paytech/billing/docs/database/sql/changes/2026-10-20-billing-fact-void.sql`（只改注释）；`mor_platform` ← 块 120—122 [registration-dml.sql](../db/platform-support/registration-dml.sql) + [test-activate.sql](../db/platform-support/test-activate.sql)（26 路由、10 资源、1 授权、角色版本 +1） |
| Compose 覆盖 | ledger-service 增加 XXL 执行器环境变量（APPNAME `ledger-service`，IP `172.30.73.38`，端口 9999）；operations-console-bff 增加 `OPERATIONS_BILLING_BASE_URL`。基础 `compose.applications.yaml` 的同样改动随提交进入 main |
| XXL-Job | Admin 新建执行器分组 `ledger-service`（自动注册），新建任务 `accountingRuleVersionActivation`，CRON `0 * * * * ?`，路由 FIRST，单机串行，启用 |
| 不动 | Gateway、Nginx、Vault、account、commercial 等其余服务；不使用 `--remove-orphans` |

## 2. 顺序

1. 只读核对：运行镜像（回退位点）、`mor_platform` 块 120—122 ID 段为空、PLATFORM_ADMIN 版本、Compose 渲染与运行环境变量差异。
2. 备份：`mor_ledger`、`mor_billing`、`mor_platform` 整库 dump，五个容器 inspect。
3. 执行 ledger、billing 注释 SQL（先于新镜像：旧镜像读不了 `VOIDED`，注释 SQL 不改数据）。
4. 替换：platform-support → ledger → billing → operations-console-bff → operations-console-web，逐个 healthy。
5. Platform 块 120—122 注册并启用；等待 Gateway 快照刷新。
6. XXL 登记 `accountingRuleVersionActivation` 并手动触发一次。
7. 验收：经运营入口走三个页面主流程（人工调整制单 → 复核过账；计费事实登记 → 批准 → 余额变化 / 作废；发起试算；关账发起 → 驳回 → 再发起 → 审批 → 重开 → 审批（最后 10 月期间回到开放，演示账簿可继续使用）；规则包以现行版本为模板新建 → 提交 → 审批 → 生效），并回归 commercial 页面与财务既有页面；浏览器检查。
8. 验收数据：保留在 AE001 演示账簿上（与既有演示数据一致）。

## 3. 回退

应用：用第 1 步记录的镜像 ID 回退五个容器。SQL 只改注释，无需回退；若已产生 `VOIDED` 数据而回退旧镜像，旧镜像读取会失败，需先把这些行处理掉（本次验收只在演示数据上作废）。Platform：执行 `test-deactivate.sql` 使块 120—122 休眠。XXL：停用任务。

## 4. 执行记录（2026-10-20）

| 步骤 | 结果 |
|---|---|
| 源码 | main `d565f85f`（含 commercial 第二切片）+ worktree `../Code-MoR-fin4`（211 个文件，3 处冲突已解决），复跑：ledger 312、billing 80、BFF 1766、arch-rules 61、PDManer 15、Web 1145 全部通过 |
| 注册块改号 | mor-test `mor_platform` 中风控控制台（2026-10-15）以另一 ID 规则占用 `900000000000101xxx—104xxx`、`111xxx—114xxx`（不在 initial-data.sql 中），与原定块 100—102 的资源 ID 冲突；改为块 120—122，并在 mor-test 数据副本上演练注册 + 启用通过 |
| 镜像（tag `fin-20261020`） | platform-support `sha256:d800c3ce…`、ledger `sha256:d846efae…`、billing `sha256:99839156…`、operations-console-bff `sha256:adec3f63…`、operations-console-web `sha256:c6661078…`；见 [compose-candidate.yaml](./compose-candidate.yaml)，回退见 [compose-rollback.yaml](./compose-rollback.yaml) |
| 备份（06:09Z） | `~/Code-MoR-backups/20261020T060913Z-fin-console-release/`（`mor_ledger` 33、`mor_billing` 11、`mor_platform` 57 表、五个容器 inspect、`SHA256SUMS`） |
| SQL | ledger、billing 状态注释加 `VOIDED`；块 120—122 注册并启用：26 路由、10 资源 ACTIVE，`FINANCE_ACCOUNTING_APPROVE` ACTIVE，PLATFORM_ADMIN 34 → 35 |
| 替换（06:09—06:10Z） | 五个容器逐个 healthy，启动日志无 ERROR；Compose 渲染与运行环境变量只差本次新增的 ledger XXL 执行器与 BFF billing 地址 |
| XXL | 分组 16 `ledger-service`（执行器 `172.30.73.38:9999` 已注册）、任务 19 `accountingRuleVersionActivation` 每分钟；首次 06:12Z 执行成功 |
| 接口验收 | [test_acceptance.py](../verification/test_acceptance.py) **28/28**：人工调整制单 / 借贷不平衡拒绝 / 列表 / 本人复核拒绝 / 他人过账；计费事实登记（来源 OPERATIONS）/ 草稿列表 / 本人批准拒绝 / 他人批准 / Client 余额 +10.00 / 作废后不能批准；发起试算（借贷 358.20 平衡）/ 看板 / 非开放期间关账拒绝 / 关账（0.17 秒）/ 本人复核拒绝 / 驳回回开放 / 再发起审批已关账 / 已关账期间不能调整 / 重开审批回开放；规则包列表 / 模板新建并作废 / 提交与本人审批拒绝 / 未来版本待生效 / 定时任务到时生效并退役旧版本 |
| 浏览器 | 无头 Chromium 经运营入口登录 [test_browser.py](../verification/test_browser.py) **8/8**：财务新菜单、调整两页签与列表、期间关账看板、记账规则列表、试算平衡「发起试算」按钮、商务价目 / 工作台回归，控制台无 JS 错误；截图 `assets/stage7/`。Chrome 扩展仍无法访问 `localhost:28080`（`curl` 正常），改用无头浏览器 |
| 复核人说明 | mor-test 只有一个运营账号；制单与"本人不能复核"走运营入口，复核由另一操作人 ID 直接调 ledger / billing 私网接口完成 |

演示数据（AE001 账簿，保留）：人工调整 `MA238182800904253440`（5.00，已过账）；计费事实 1 条已批准（Client 本金 10.00）、1 条已作废；10 月期间关账 3 次记录（驳回 / 完成 / 重开完成），现为开放；`STANDARD_ADJUSTMENT` 当前版本 3（内容与版本 1 相同），版本 1 退役、版本 2 作废。

## 5. 后续基线变化

2026-10-20 07:25—07:28Z，channel-connector 会话发布块 92（tag `channel-20261020`，基于 `b04b77f1` + 其未提交改动），替换 platform-support `sha256:e5a06814…`、operations-console-bff `sha256:bec85826…`（保留 `OPERATIONS_BILLING_BASE_URL`）、operations-console-web `sha256:be32382a…`（随后修复展示缺陷重发为 `sha256:3ae27f3d…`，tag `channel-20261020b`）；PLATFORM_ADMIN 35 → 36。ledger、billing 未变。本需求无头浏览器回归 8/8 通过。此后回退这三个容器应使用其镜像，而非本清单第 1 步记录的回退位点。
