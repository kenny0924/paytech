# 明细账运维 · 阶段 7 mor-test 验收（runbook 第 11 步 / 第 4 节 V-0—V-8）

日期：2026-10-30（UTC 17:32—17:44）· 入口：运营 `http://localhost:28080`（mor-test Nginx → Gateway → operations-console-bff）· 库：只用 `mor-test-mysql-1` 的 root READ ONLY 会话（`--init-command=SET SESSION TRANSACTION READ ONLY`，口令只经 `MYSQL_PWD` 传入）· 现网镜像：ledger `b65223c1`、account `6ad6a22b`、Platform `1bfed883`、ops-bff `4bdfce6e`、ops-web `5784fa81`。
未停服务，未制造落后，未删改规则，未直接写库，未做 O-1。

## 1. 结论

**V-0—V-8 全部 PASS，无 FAIL。** 写入严格限于授权的三项，清理已完成。

| # | 结果 | 证据 |
|---|---|---|
| V-0 健康 | PASS | 5 个容器 healthy，镜像前缀与发布记录一致。`mor_platform` 中 ACTIVE 路由 **675**，块 148 的 4 条路由（900000000000148401—148404）均为 ACTIVE。Web 入口为 `assets/index-CqHRNw-B.js`，与发布树 candidate dist 相同。说明：mor-test Gateway 容器 `docker logs` 自 13:10Z 重启后没有输出，看不到 routeCount 日志，改用 SQL 的 ACTIVE 路由数加块 148 路由实际可达（V-1—V-6 的请求都经这 4 条路由）判定。`result-plan-20261030T173319Z.json` |
| V-1 进度（OE 账簿 `238137396670066688`） | PASS | B1 200（1.30 s）。① billing 已发布 11 = account 已应用 11，最大事实号一致；② 1 条流；③ `OE.AE001.USD.0` account 最大 12 = ledger 已消费 12 = SQL（`t_account_posting_stream.last_sequence` 12、`t_consumed_aggregate_version.last_version` 12），lag 0。三段均为 CONSISTENT，`stuckPosting` 为 null。截图 `*-P1-oe-consistent.png` |
| V-2 进度（FE 账簿 `238324787049492480`，PLATFORM_HQ） | PASS | B1 200，三段 CONSISTENT，流清单为空，页面显示"这本账簿还没有过账流"。SQL：`mor_account.t_balance_applied_fact` 中 `finance_entity_id=238324786164494336` 的服务费事实为 0 条，所以服务费所在 OE 流为空，与关账第 3 步口径一致。截图 `*-P9-fe-book.png` |
| V-3 批次浏览 | PASS | B2 `fromSequence=1&limit=20` 返回 12 批，与 `mor_account.t_account_posting` 逐行相同（postingId、序号、factId、来源），`nextFromSequence` 为 null。批次详情（已有路由）200，有 2 行流水。页面：从流行进入、打开抽屉、返回后仍保留所选账簿。截图 `*-P6-browse.png`、`*-P6b-drawer.png`、`*-P6c-back.png` |
| V-4 追平（已一致的流） | PASS | 管理员经运营入口调用一次 B3：200，`{"processedCount":"0","lastSequence":"12","caughtUp":true}`，耗时 0.21 s。前后 ledger 已消费 12 → 12，account 流 12 → 12；近 2 分钟 `mor_ledger.t_journal_entry` 没有新分录。`result-catchup-20261030T173636Z.json`、`catchup-done.json`。**与 runbook 的差异**：流已一致时页面不提供"追平"按钮（按钮只在落后的流上出现，设计如此），所以看不到"已追平"结果条，改为经接口执行。页面已核对一致时无按钮：`zh-CN-1440-V4-consistent-no-catchup-button.png` |
| V-5 重建（AE001 · USD） | PASS | en-390 页面点"Rebuild Balances"，出现确认弹窗（`en-390-P4-rebuild-confirm.png`），确认后显示结果条："Balances match entries; nothing corrected · Recalculated 9 accounts; no balance changed · 10/30/2026, 21:42:31 · by o***@example.test"（`en-390-P5-rebuild-result.png`）。请求 200，经 Gateway 耗时 312 ms。account 日志：`入账流余额已重建: OPERATING_ENTITY/AE001/USD, 操作人 221546852372267008, 账户 9, 余额有变化的账户 0, 流水 21`。重建前后各 SELECT 一次该流全部 9 个账户（账户号、科目、Owner、桶、借合计、贷合计、净额），**逐行相同**（diff 为空）；重建后每个账户的借 / 贷合计都等于流水合计。只有 `balance_version` +1、`update_time` 变化（元数据）。`snapshot-before.json`、`snapshot-after.json`、`result-compare-20261030T174333Z.json` |
| V-6 权限 | PASS | 合成只读账号的导航只有 `OPERATIONS_FINANCE_POSTING_OPS`；B1、B2 返回 200；B3、B4 直接调用都返回 **403** `OPERATIONS_PERMISSION_DENIED`，前后 SQL 不变。页面（四个组合）没有"追平""重建余额"，只有"刷新 / 浏览批次"，批次浏览可用（`*-P10-readonly.png`、`*-P10b-readonly-browse.png`）。管理员导航含页面与两个按钮。`result-readonly-20261030T173458Z.json` |
| V-7 浏览器 | PASS | Playwright 真实 Chromium，zh-CN / en × 1440 / 390 共 55 步、55 张截图（只读干跑的同名截图已被正式执行覆盖）。全部无横向滚动、无裸文案键、无错误提示；HTTP ≥ 400 为 0，控制台 error / warning 为 0，重新登录 0 次。文案来自语言包 **#30**：按前端相同方式（`?locale=`）读取 manifest，en 与 zh-CN 的 finance 各含 144 个 `postingOps` 键，页面按钮文字与发布包中的值一致（"重建余额" / "Rebuild Balances"）。`browser-result-2026-10-30T1743Z.json`、`browser-run.log`、`screens/` |
| V-8 回归 | PASS | 四个组合分别只读打开明细账查询、余额查询、期间关账、收款账户核验、Owner 账户与余额：页面正常，没有错误提示，没有 HTTP ≥ 400。截图 `*-V8-finance-*.png` |

## 2. 写入清单（只有授权的三项）

| # | 写入 | 时间（UTC） | 结果 |
|---|---|---|---|
| 1 | IAM：合成角色 `PLOPS_MT_READONLY_1030213457`（`241978194859737088`，只含 `FINANCE_ACCOUNTING_READ` 900000000000097101）；合成账号 `plops-mt-readonly-1030213457@example.test`（`241978195895730176`），首登改密 | 17:34:57 | 成功；凭据在 `~/Code-MoR-backups/plops-stage7/accounts.json`（0600，目录 0700），不在仓库 |
| 2 | 对 `OE.AE001.USD.0` 空追平一次（管理员，经运营入口接口） | 17:36:36 | processedCount 0、caughtUp true；ledger 没有写分录 |
| 3 | 对 AE001 · USD 同值重建一次（管理员，en-390 页面） | 17:42:31 | changedAccountCount 0；9 个账户余额逐行不变，只有版本与更新时间变化 |

其余组合与步骤都是只读。脚本中的追平子命令用 `catchup-done.json` 防止重复执行；浏览器脚本里没有任何追平点击。

## 3. 清理

- 17:43:46 经 IAM 接口 `POST /iam/v1/accounts/{id}/disable` **停用合成账号**，返回 204。SQL 核对 `t_user_account.status = DISABLED`，用原口令登录被拒（401 `OPERATIONS_AUTHENTICATION_FAILED`）。
- 撤销合成角色：`DELETE /iam/v1/roles/{id}` 返回 400 `BUSINESS_CHECK_FAILED`，因为角色仍挂在已停用账号的成员上，不能删除。于是改用 `PUT /iam/v1/roles/{id}` 把权限清空，返回 200，`permissionIds []`；SQL 中 `t_role_permission` 那一行变为 `REVOKED`。角色 version 2，没有任何生效权限。
- 没有删除审计记录和日志。V-4 / V-5 没有业务数据需要清理。
- `result-cleanup-20261030T174346Z.json`

## 4. 过程记录

- **只读干跑**（17:32—17:33）：
  - 第一次运行时脚本列名写错（`version` 应为 `last_version`），在写结果前就中断了，无结果文件。
  - 第二次（`result-plan-20261030T173213Z.json`）V-7 记为 FAIL，原因也在脚本：manifest 没带 `?locale=`，所以只返回 en 的 bundle。修正后与前端读法一致。
  - 第三次（`result-plan-20261030T173319Z.json`）15 项全部通过。
- **浏览器干跑**：zh-CN-1440 关闭写操作跑了一遍（`browser-result-2026-10-30T1736Z-dryrun.json`），13 步全部正常，之后才正式执行。
- 正式执行顺序：snapshot before → 空追平 → 浏览器四个组合（只有 en-390 重建一次）→ snapshot after → 逐行比较 → 清理。

## 5. 新发现的问题

1. **V-4 无法在页面上执行（文档问题，不是缺陷）**：流已一致时页面不显示"追平"按钮，runbook 写的"页面显示已追平结果条"在已一致的数据上做不到。建议把 runbook 的 V-4 改为"经接口执行；页面核对已一致时没有追平按钮"。
2. **Gateway 日志不可见（环境）**：mor-test Gateway 容器自 13:10Z 重启后 `docker logs` 一行也没有，runbook 的 `gwlog` 看不到 routeCount。本次用 `mor_platform` 的 ACTIVE 路由数代替。建议排查 Gateway 的日志输出配置。
3. **合成角色无法删除（已知行为）**：角色挂在已停用账号上时删除被拒，只能清空权限。如果需要彻底删除，要先删除账号（`DELETE /iam/v1/accounts/{id}`）。本次按"停用账号、撤销角色"的要求，没有删除账号。
4. **测试脚本局限，非产品问题**：390 宽度下侧栏收为图标栏，脚本按菜单文字判断"菜单可见"会得到 false。菜单可见性改以 1440 截图和导航接口为准。

## 6. 产物

`release/acceptance/` 中：
- 脚本：`plops_mt.py`（API / SQL / IAM）、`plops_browser.mortest.mjs`（浏览器）
- 结果：`result-*.json`、`browser-result-*.json`、`browser-run.log`、`snapshot-before.json` / `snapshot-after.json`、`catchup-done.json`
- 截图：`screens/`，共 55 张
