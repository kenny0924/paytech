# W3 · 阶段 6：mor-test 发布准备（只读核对 + 本机构建）

## 目标

为 REQ-02 产出可审批的 mor-test 发布包：现网基线、候选源码树、测试、固定 `sha256:` 的候选镜像、compose 覆盖、只读巡检 SQL、语言包计划、data-query 登记步骤、验收脚本、runbook 草稿。**不改 mor-test 任何东西**（只允许 `docker inspect / ps / logs / exec printenv|sha256sum|cat`、`docker cp` 取出、root 只读 SELECT、`mysqldump --single-transaction` 导出到本机、Nacos / XXL / Platform 只读查询）。不提交、不推送；不改主工作区（本需求目录 `release/`、`tasks/` 除外）与其他会话的 worktree；不改 `test-environment/compose*.yaml` 基础文件。

## 输入

- 需求目录 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/channel-connector-渠道身份就绪查询/`：`requirement.md`、`technique.md`（第 8—16 节；第 16 节发布顺序）、`delivery.md`、`tasks/REPORT-W1.md`、`tasks/REPORT-W2.md`、`db/data-query-templates.sql`。
- 研发树：`/Users/kenny/Desktop/Workspace/Code-MoR-req02`（main `331d8e0e` + 本需求，未提交）。
- 先例（结构照做，规模更小）：`requirements/20261030/payout-收款账户与余额解释/release/`（`baseline.md`、`stage6-report.md`、`runbook.md`、`scripts/source_drift_check.sh`、`scripts/env_check.sh`、`harness/envcmp.py`、`harness/jarcmp.py`、`harness/image_verify.sh`、`payout_bundles.py`、`acceptance/`）与 `requirements/20261028/commercial-开通项前置门禁/release/`（data-query 附录、巡检 SQL）。
- 经验（必须读）：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `mor-test-pin-image-ids.md`、`mor-test-compose-sh-overrides.md`、`mor-test-release-override-env.md`、`mor-test-release-card-lessons.md`、`mor-test-deploy-lessons.md`、`ops-web-shared-tree-release.md`、`local-vs-mor-test-containers.md`、`maven-jdk25-and-local-test-run.md`、`background-agent-and-shared-local-tests.md`、`worktree-path-and-commit-mode-pitfalls.md`、`i18n-localization-mor-test.md`、`acceptance-needs-browser-pass.md`。

## 发布范围

| 服务 | 现网（2026-10-30 18:00 读数，开工时重读） | 候选 |
|---|---|---|
| channel-connector-service | `sha256:23679d451fe4`（card3ds-20261022） | 本需求 |
| client-master-service | `sha256:2b12c2e2184b`（rq12-20261029a） | 本需求 |
| commercial-service | `sha256:806043a320cf`（gate-20261029a） | 本需求 |
| operations-console-web | `sha256:5784fa817c47`（**plops-20261030a**，明细账运维会话 17:27Z 发布） | 明细账运维源码 + 本需求（基线由主会话另行告知，见下） |
| Platform / ops-bff / Gateway | 不发 | — |

**ops-web 基线（重要，明细账运维会话 2026-10-30 确认）**：现网 ops-web `5784fa81`（291 个 dist 文件）由发布树 `/Users/kenny/Desktop/Workspace/Code-MoR-plops-rel` 构建（main `331d8e0e` + plops 68 个文件，其中 ops-web 18 个；差异指纹 `78ccfd1dc42e8c62`，可用 `requirements/20261030/operations-明细账运维/release/scripts/source_drift_check.sh` 核对，**只读，不要改那棵树**）。plops 尚未提交，提交后会告知提交号；未提交前候选 ops-web = 该发布树的 `operations-console-web` + 本需求 ops-web 差异（与 plops 同改的文件三方合并，以 `331d8e0e` 为共同祖先）。先用 plops 发布树构建一次 dist 与现网逐文件比对（"现网独有"必须为 0），再构建候选。后端三服务以 main 为基线，不含 plops 文件。
**语言包号**：现网运营包 #30；#31 已由 REQ-06（会话 design-discuss-5f）预约，本需求的发布号由主会话与 REQ-06 协调后告知，计划脚本不要写死号码。

## 产出（`release/`；大文件放 `~/Code-MoR-backups/req02-stage6/`，目录 0700、文件 0600）

1. **基线** `baseline.md`：四个服务现网镜像 ID / tag；`docker cp` 取出 JAR / dist，与 main `331d8e0e` 构建产物逐条目比对，给出每个服务"现网独有内容"数量（后端三服务预期 0；非 0 停在待裁决）；`printenv` 键名（不抄凭据值）；历史 override 链（grep `requirements/*/release/compose-candidate*.<service>.yaml`）。
2. **候选源码树** `/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel`（`git worktree add --detach <绝对路径> <基线>`，后端基线 main 最新 HEAD，开工时 `git log -1 main` 确认），迁入研发树改动（文件清单 = 研发树 `git status --short`（M + ??）∩ `git diff --name-only 331d8e0e`，排除 `node_modules / target / dist`）；`evidence/source-files.sha256`、漂移检查脚本 `scripts/source_drift_check.sh`。ops-web 部分按主会话告知的 plops 基线合并（三方合并，冲突逐个写 `evidence/merge-notes.md`）。
3. **测试**（候选树，`package -pl … -am`，只用 `mor-local-*`）：channel-connector、client-master、commercial、arch-rules；ops-web `tsc --noEmit`、`npm test`、`npm run build`、`i18n:export-source`；全 reactor `test-compile`。
4. **镜像**：按 `test-environment` 的方式构建四个服务，显式 tag `req02-20261030a`，不得改动 `:development` 或其他 tag；记录 `sha256:`；`evidence/image-verify.txt`（镜像内 JAR / dist = 构建产物；Entrypoint / User / Healthcheck / 端口 / health-probe 与现网同类一致）；候选相对现网的条目差异摘要（新增 / 改动 / 删除，删除必须解释）。
5. **compose**：每个服务 `compose-candidate.<service>.yaml` 与 `compose-rollback.<service>.yaml`，固定 `sha256:`，env 完整继承现网（逐键逐值比对，`scripts/env_check.sh` + `evidence/env-check.txt`，凭据只比哈希）。本需求不新增 env 键（commercial 经服务发现调 channel-connector / client-master）；若发现需要，列为待裁决。
6. **内部调用核对**：commercial → channel-connector、commercial → client-master 在 mor-test 的服务发现与内部认证（internal-auth 模式、令牌 / 受众 / 调用方白名单）是否已放行；需要登记的写出步骤（只读核对现网配置）。
7. **只读巡检 SQL** `sql/01-precheck-capabilities-to-suspend.sql`：按新口径列出现网 ENABLED 支付方式开通项中会被暂停的（mor_commercial × mor_channel_connector × mor_client_master，root 只读会话可跨库读，仅用于巡检）；在 mor-test 只读执行并把读数写入 `precheck/`（阶段 3 预期：仅 DEMO-CL-001 APPLE_PAY）。
8. **语言包**：照 `payout_bundles.py` 写 `req02_bundles.py`，计划相对现网最新运营包（开工时读现网号，预计 #30 或更高）：commercial 命名空间新增 / 基准变更 / 废弃（2 个 `CHANNEL_IDENTITY_NOT_CONNECTED`）/ zh-CN 导入数；只读干跑。
9. **data-query**：`GOLIVE_MID_READY` 登记步骤（数据源 `MOR_CHANNEL_CONNECTOR_REPLICA`，参数 4 个、maxRows 1、超时 5 秒），只读确认数据源存在、模板编码未占用。
10. **验收脚本** `acceptance/req02_acceptance.py`：子命令 `plan` / `prepare` / `api` / `check` / `cleanup`，写操作需 `--yes`；经真实入口覆盖 AC-06—AC-12（门禁结论、"还差什么"原因、发布后重查）与就绪查询（经私网探针调 channel-connector，照 payout 先例 `E2E_PROBE_IMAGE`）；浏览器脚本 `acceptance/req02_browser.mortest.mjs`（合同详情"还差什么"渠道身份、去配置 MID 跳转与 MID 页预填、无 MID 菜单权限不显示链接、zh-CN / en × 1440 / 390）。合成数据只用现有 DEMO 合同 / 临时运营账号，写清创建与清理。只做语法检查与只读子命令干跑。
11. **报告** `W3-report.md`：以上结论、待裁决、阶段 7 动作清单（每步命令、预期、失败即停、回滚；顺序按 technique 第 16 节：巡检 → client-master、channel-connector → commercial → ops-web → 语言包 → 手动触发 `capabilityPrerequisiteRecheck`（`includeEnabled`）→ data-query 登记）。

## 规则

- 凭据只读入变量，不打印、不写文件。不调用任何 Superpowers skill。不跑全 reactor 测试。
- 卡住超过 15 分钟或遇到业务裁决、与他人线上改动冲突，写进报告"待裁决"并继续其他部分。
- 子代理写主工作区文件若被拦，把报告全文放在最终回复里，由主会话落盘。完成后回复 ≤ 25 行摘要。
