# W9 · 阶段 6：SQL、备份、注册块 147 演练、Vault、语言包、验收脚本（只读核对 + 离线演练）

## 目标

为 payout S1 产出可审批的 mor-test 数据侧发布材料与验收脚本。**不改 mor-test 任何东西**。另一 agent W8 并行做候选源码 / 镜像 / compose（候选 JAR 会放在 `~/Code-MoR-backups/payout-s1-stage6/candidate/`）；主会话汇总 runbook 与 stage6-report。

## 输入

- 需求目录 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/payout-收款账户与余额解释/`：`requirement.md`（AC-01—AC-11、AC-V1—V4）、`technique.md`（第 7—10 节、8.7）、`db/payout/ddl.sql`（现行全量 DDL，31 列 `t_beneficiary_account` 已含 notes 两列）、`db/payout/ddl-20261030-notes.sql`（本机增量，mor-test 首建不需要——核对后说明）、`db/vault/dml.sql`、`db/platform-support/dml.sql`、`verification/stage5-backend-report.md`（第 4 节合成数据与清理草稿）、`verification/local-e2e/stage5_backend.py`、`verification/stage5/stage5_browser.mjs`、`tasks/W2-account-platform.md`、`tasks/W6-stage5-backend.md`、`tasks/W7-stage5-browser.md`。
- 注册块 147 六件套：`/Users/kenny/Desktop/Workspace/Code-MoR-po-int/release-draft/`（`mor_platform-block-147{,-activate,-deactivate,-precheck,-rollback,-verify}.sql`、`gen_block147.py`、`SHA256SUMS`）。
- 协调结论：块 147 本需求用（144 联合查询、145 REQ-04、146 tax 已占；148 明细账运维 / 交易查询预定；149 测试假权限 ID；150 REQ-06 预定）。ops 语言包现网最新 #28（REQ-04），发布号取阶段 7 当时下一个空闲号。
- 先例（照做）：`requirements/20261029/risk-decision-限额档位/release/`（`sql/`、`harness/BlockSnapshotCheck.java`、`harness/GatewaySnapshotDecodeCheck.java`、`rq04_bundles.py`、`i18n-bundles/`、`acceptance/rq04_acceptance.py`、`evidence/rehearsal-*`、`runbook.md`）与 `requirements/20261029/tax-结账算税与事件登记/release/`（新库首建、`tax_bundles.py`、`acceptance/`）。
- 经验（必须读并遵守）：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `registration-block-gateway-compile.md`、`mor-test-release-card-lessons.md`、`mor-test-release-override-env.md`、`mor-test-deploy-lessons.md`、`local-vs-mor-test-containers.md`、`data-query-delivered.md`（Vault 策略在 mor_vault 库）、`i18n-localization-mor-test.md`、`i18n-release-change-delivered.md`、`acceptance-needs-browser-pass.md`。

## 产出（写在需求目录 `release/`；导出与演练目录放 `~/Code-MoR-backups/payout-s1-stage6/`，目录 0700、文件 0600，不入库）

1. **只读预检与导出**：mor-test `mor_payout` 库与 `payout_app` 账号不存在；`mor_vault` 中 DML 用到的 ID（9100000000000880—885）、策略编码与当前策略版本（本机是 25 → 26，mor-test 可能不同——DML 若写死版本号必须改为按现网计算或给出 mor-test 专用版）；Vault 服务如何加载策略（启动读 / 缓存 / 刷新），决定阶段 7 是否要重启 vault-service；`mor_platform` 块 147 七表区间、编码、路由路径、权限编码、通知模板编码均未占用，依赖（菜单父级、角色、发送账号 / SES 或 Mailpit 发送配置）都在——特别核对 mor-test 是否有邮件发送账号，否则 4 个模板路由为 0、AC-09 无法验证。`mysqldump --single-transaction` 只读导出 `mor_platform`、`mor_vault`（存本机备份目录）。
2. **SQL 包** `release/sql/`（编号顺序执行，每个都带回滚、前置检查、执行后核对）：建库与账号（密码不入库，用变量 / 秘密文件，写清从哪里读）、权限（只 DML，照其他服务账号的授权粒度）、`mor_payout` DDL、Vault DML（+ 回滚）、注册块 147 六件套（复制并按 mor-test 现网核对；需要改时在 `release/sql/` 下改并说明差异）。是否需要 data-query 数据源 / 授权：S1 没有就写明不需要。
3. **离线演练**（断网 `mysql:8.4.11` 容器载入只读导出；演练后删除容器）：Vault DML 前滚 → 核对 → 回滚后与导出逐行相同；块 147 休眠 → 激活 → 重复激活 → 停用 → 回滚，回滚后与导出逐行相同；每个状态用**现网 Platform JAR**（`docker cp` 从 `mor-test-platform-support-service-1` 取出）的真实编译器编译，序列化快照报字节数，并用**现网 Gateway JAR** 的生产解析解码（`GatewaySnapshotDecodeCheck`），报路由数（现网约 652，激活后期望 +19）与距 4 MiB 上限的余量。W8 候选 Platform JAR 出现后再用它复跑一遍（若等待超过 30 分钟，先交付现网 JAR 的结果并注明待复跑）。`mor_payout` DDL 在断网容器建库验证可重跑性（`IF NOT EXISTS` 行为）与回滚（DROP 前须确认无数据）。
4. **语言包**：ops 控制台本需求新增 / 变更键的计划（相对现网 #28 的差异：新增、基准变更、废弃 0、zh-CN 导入数、是否新建命名空间），只读干跑；脚本照 `rq04_bundles.py`。Client 门户本期无 Web 页面，确认不需要 client-console 语言包。
5. **验收脚本** `release/acceptance/`：把本机 `stage5_backend.py` 与 `stage5_browser.mjs` 改造成 mor-test 版（入口 `https://127.0.0.1:18444` / Gateway 28080 等，以先例为准），子命令 `accounts` / `prepare` / `api` / `browser` / `check` / `cleanup`，写操作需 `--yes`；覆盖 AC-01—AC-11（含 AC-09：Platform 受理通知并经 Mailpit 收到邮件——确认 mor-test Mailpit 查询方式）、权限与无权限分支、他方回归（REQ-04 限额档位列表、联合查询、多语言）、zh-CN / en × 1440 / 390 浏览器走查。列出需要的 IAM 合成账号（Maker / Checker 分离的运营账号、Client 租户四角色），怎么创建（照先例），以及使用哪个 Client / Local Partner 合成数据（只读查 mor-test 现有 DEMO 数据可用性）。本阶段只做语法检查与只读子命令干跑。
6. **合成数据清理计划**：mor-test 验收后清理范围与 SQL 草稿（只删本次验收创建的、按 ID 或标记）；本机 `mor_payout_v2e2e` 等 W6 合成数据的清理 SQL 草稿一并整理（不执行）。
7. **报告** `release/W9-report.md`：结论、预检读数、演练证据路径、风险、待裁决、阶段 7 需要的数据侧动作清单（每步命令、预期、失败即停判断、回滚命令）。

## 规则

- mor-test 只读：只允许 `docker inspect / ps / logs`、`docker exec … printenv / cat`（读）、`docker cp` **取出**、root 只读会话 SELECT（凭据从 test-environment 秘密机制读入变量，不打印）、`mysqldump --single-transaction` 导出到本机、Mailpit / Nacos / XXL 只读查询。不写任何 SQL、不调任何写接口、不登录运营 API 写数据、不 `docker cp` 写入、不停 / 起容器。
- 本机 SQL 只用 `mor-local-mysql-1` 或你自建的断网演练容器（用完删除）；本机 e2e 共享栈（`local-environment/e2e`，会话 43 即将接手）不动。
- 不提交、不推送；不改主工作区（需求目录 `release/`、`tasks/` 除外）和其他会话的 worktree。
- 凭据只读入变量，不打印、不写文件。
- 不调用任何 Superpowers skill。
- 卡住超过 10 分钟或遇到需要业务裁决的问题，写进报告"待裁决"并继续其他部分。完成后回复一段摘要（≤ 25 行）。
