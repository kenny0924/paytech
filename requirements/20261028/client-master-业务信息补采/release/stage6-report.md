# REQ-12 阶段 6 报告 · mor-test 发布准备（2026-10-29 16:20—17:00 +04）

> 只做只读核对、只读导出到本机、本机构建与离线演练。**未改 mor-test 任何东西**：未停 / 起容器、未写 SQL、未调用 Platform 写接口、未发布语言包、未 `docker cp` 写入容器。不提交、不推送。阶段 7 需用户明确授权容器停 / 起与 SQL 写入，由主会话按 [runbook.md](./runbook.md) 执行。

## 1. 结论

| 项 | 结果 |
|---|---|
| 现网基线（[baseline.md](./baseline.md)） | client-master `c9bb7b8e`、client-console-bff `8fe7f24c`、client-console-web `3db62571`（均为法务修订 legal-20261028a）；operations-console-web `96992aba`、operations-console-bff `6833f484`（REQ-05）。与 base `6753faba` 干净构建比对：**两个 Web dist 逐字节相同（11 / 255 个文件）**；client-master 与 client-console-bff **现网独有内容 0**，差异只是 base 随包的更新 Owner API（REQ-01 commercial-api、REQ-05 platform-support workflow API、messaging 目录），两服务自身类 0 差异且不引用这些 API。现网没有 `6753faba` 以外的改动 |
| 发布范围 | **client-master、client-console-web、operations-console-web** 三个镜像 + mor_client_master DDL / DML + 两个语言包。client-console-bff **不发布**（本需求只加测试；mor-test 现网已含法务修订 BFF 改动；新值都是已有字符串字段，严格解码兼容）。ops-bff 不发布（`af972805..6753faba` 只改文档） |
| 候选镜像（tag `rq12-20261029a`） | client-master `sha256:2b12c2e2184bb9e926c5fb6a74822c103ba5a659a53dcbacb9a4d6bb60c414bc`（JAR `ca0b61fb`）；client-console-web `sha256:e17a9a0a43b936ebc2344338df1e934f6cece672081385c2841b0bde358f0cf0`；operations-console-web `sha256:0b456b8aed53df0d31719f995a755d8f74d1abbd2a8b05e6de53ef5e0c34ae72`。镜像内 JAR / dist 与构建产物逐字节相同；health-probe、nginx.conf 与现网相同；Entrypoint / Cmd / User / 端口 / 工作目录与现网镜像相同（`evidence/image-verify.txt`） |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-rq12-rel` = `6753faba` + `Code-MoR-rq12` 的 72 个本需求文件（`git status` 的 M + ??，不含 requirements/；清单与指纹 `evidence/rq12-source-files.sha256`）；`scripts/source_drift_check.sh` 当前"all 72 files unchanged / no new files / main 未前进"。候选源码测试：client-console-web 153/153、operations-console-web 1601/1601、两端 tsc 0 |
| compose | 6 个覆盖文件（候选 / 回滚 × 3），只认 sha256；`scripts/env_check.sh`：6 行全部 `missing=[] extra=[] valuediff=[]`（client-master 40 键、两个 Web 各 7 键；`evidence/env-check-20261029.txt`） |
| SQL 预检（mor-test 只读） | 新列 0、ID 段 `9281200000…` 0、保留目录码 0、新 field_key 0；ACTIVE 9（v2 8 + 自由职业者 v1）、字段 602；在途申请全部冻结 v1（UNDER_REVIEW 8、DRAFT 3）；经营资料 51 行（`precheck/mor_client_master-precheck-20261029.out`） |
| D-2（静态 display_order） | 现网 9 个 v2 的 BUSINESS 最大序号与 DML 静态值**完全相同**——关闭 |
| 离线演练 | mor-test `mor_client_master` 只读导出（`Code-MoR-backups/rq12-20261029/mortest-mor_client_master-readonly-20261029T125241Z.sql`）→ 本机临时库：16 步全部按设计（DML 先于 DDL STOP；DDL 重跑；DRAFT → 核对 → ACTIVE → 重跑空操作；有 v3 申请时 DML / DDL 回滚均 STOP；回滚 / 重跑 / DDL 回滚 / 再前滚）；最终与全新建库**目录、Schema、字段、4 张表结构 0 行差异**；临时库已删除（`evidence/rehearsal-20261029T125241Z/`）。第一次尝试因核对脚本别名 `schemas` 为保留字报 1064（同法务修订的坑），改名后重跑通过（`…-attempt1/` 保留） |
| commercial（只读） | ACTIVE PAYMENT_METHOD **4 项**：STC_PAY、APPLE_PAY、GOOGLE_PAY、CARD（可开通项 ID 升序，即门户选项顺序）。`CARD` 名称是合法 UTF-8 `银行卡`，阶段 2 的 `???` 是终端显示问题；client-master 不用 `capability_name`，标签取语言包键——**不影响** |
| 语言包（只读 dry-run） | client-console **#7 → #8**：新增 23（onboarding-schema 17、onboarding 6）；operations-console **#21 → #22**：新增 22（client-review 9、onboarding-schema 13）；基准变更 / 废弃 / 恢复 0；不新建命名空间；`zhChangedNotImported` 0；key 清单 `i18n-bundles/rq12-key-changes.tsv`（45 行） |
| 验收脚本 | `acceptance/rq12_api.py`（F / O / I / R，不提交、不审批、草稿最后撤回）本机演练 **32 PASS / 0 FAIL**（`acceptance/local-dryrun/`）；浏览器计划 `acceptance/browser-plan.md` |
| technique 更正 | 8.1 / 8.4：门户收到 503 `SERVICE_UNAVAILABLE`（BFF 通用码，用户确认保持），client-master 合同仍为 `ONBOARDING_DEPENDENCY_UNAVAILABLE` |

## 2. 产出清单（本目录）

`baseline.md`、`runbook.md`、`stage6-report.md`；`compose-{candidate,rollback}.{client-master-service,client-console-web,operations-console-web}.yaml`；`sql/00`（预检）、`10`（DDL）、`20`（v3 DML）、`30`（核对）、`90`（DML 回滚）、`91`（DDL 回滚）——10 / 20 / 90 / 91 与 `paytech/client-master/docs/database/sql/changes/2026-10-29-*` 逐字节相同；`harness/`（jarcmp、envcmp、rehearse）；`scripts/`（env_check、source_drift_check）；`rq12_bundles.py` 与 `i18n-bundles/`；`precheck/`；`acceptance/`；`evidence/`。

本机以外的数据（不入库）：`~/Desktop/Workspace/Code-MoR-backups/rq12-20261029/`（现网 JAR / dist 取出件、mor-test 只读导出、候选镜像取出件、验收账号凭据 0600）。

## 3. 风险

1. **v3 发布后不可回滚数据**：一旦有申请冻结 v3 或资料填了交付方式，DML 回滚脚本拒绝，只能向前修复（R1）。⑨ DRAFT 核对是最后一个无代价的停止点。
2. **commercial 依赖**：v3 发布后，新申请的 form-schema / 需求清单 / 提交依赖 commercial；commercial 不可用时门户 503（通用"服务暂时不可用"），草稿保存不受影响。已知 commercial 不可用时 client-master 对同一请求会多次重试（用户已接受延迟）。
3. **⑤ 重启窗口**约 40 秒：client-master 全部接口（门户入驻、运营审核、Checkout 候选解析）503。
4. **共享工作区漂移**：候选来自 `Code-MoR-rq12` 当时的文件；若 rq12-web / 后端在阶段 7 前再改，① 的漂移检查会拦下，需要重建候选与 compose 文件。
5. 本机演练账号：验收脚本本机演练新注册了 1 个本机测试账号并撤回 2 份草稿（`mor_*_v2e2e`），属本机测试数据。

## 4. 待确认 / 阶段 7 前提

| 编号 | 事项 | 建议 |
|---|---|---|
| P-1 | 阶段 7 需用户明确授权：mor-test 容器停 / 起（3 个服务）、mor_client_master DDL + DML、两个语言包发布、验收注册账号的挑战改写（`mint_signup_link`，只作用于本次新账号） | 授权后由主会话执行 runbook ①—⑫ |
| P-2 | 是否在 mor-test 提交一份测试申请以复验运营端"有值"展示（会进入审核工作台、产生 compliance 案件） | 建议不提交（本机阶段 5 已覆盖）；如需要，验收后撤回 / 拒绝并按法务修订方式清理 |
| P-3 | 门户选项顺序为 commercial 可开通项 ID 顺序（STC_PAY 在前） | 按现状；如需按名称或固定顺序另议 |
| P-4 | 发布前通知的并行会话 | 阶段 7 第 ① 步用 ListAgents 确认后通知（本阶段未通知） |
