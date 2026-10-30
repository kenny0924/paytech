# W8 · 阶段 6：候选源码、测试、镜像与 compose（只读核对 + 本机构建）

## 目标

为 payout S1 产出可审批的 mor-test 发布制品：干净候选 worktree、测试结果、固定 `sha256:` 的候选镜像、现网基线核对、每个服务的 candidate / rollback compose 覆盖文件。**不改 mor-test 任何东西**。另一 agent W9 并行做 SQL / 注册块 147 / Vault / 语言包 / 验收脚本；主会话汇总 runbook 与 stage6-report。

## 输入

- 需求目录 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/payout-收款账户与余额解释/`：`requirement.md`、`technique.md`（第 8.7 节合同修订）、`delivery.md`（"发布基线提示"）、`verification/stage5-backend-report.md`、`verification/stage5-browser-report.md`、`tasks/W4-integration.md`、`tasks/W5-e2e-combo.md`。
- 最终代码：集成 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-po-int`（基于 `30361be7`，未提交；含阶段 5 修正：payout `PAYOUT_ACCOUNT_SERVICE_URL`、Platform 通知 base-url `lb://platform-support-service/platform-support`、ops-web 核验页计数串行——先核对这三处确实在 po-int，缺的从 `/Users/kenny/Desktop/Workspace/Code-MoR-po-e2e` / `-po-e2e-ops` 补回并说明）。
- 基线：main **`9eab368c`**（含 REQ-04 `b369e9b6`、arch-rules `264ea3d2`、联合查询、多语言发布变更记录、tax）。开工时 `git log -1 main` 再确认；若 main 前进，以新 HEAD 为准并说明。
- 先例（结构与粒度照做）：`requirements/20261029/risk-decision-限额档位/release/`（`baseline.md`、`stage6-report.md`、`evidence/`、compose 文件）与 `requirements/20261029/tax-结账算税与事件登记/release/`（新服务 tax-service 首发：`compose-candidate.tax-service.yaml`、`task-card-stage6.md`）。
- 经验（必须读并遵守）：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `mor-test-pin-image-ids.md`、`mor-test-compose-sh-overrides.md`、`mor-test-release-override-env.md`、`mor-test-release-card-lessons.md`、`mor-test-deploy-lessons.md`、`mor-test-new-service-checklist.md`、`ops-web-shared-tree-release.md`、`local-vs-mor-test-containers.md`、`maven-jdk25-and-local-test-run.md`、`background-agent-and-shared-local-tests.md`、`worktree-path-and-commit-mode-pitfalls.md`。

## 产出（写在需求目录 `release/`；大文件 / 取出物放 `~/Code-MoR-backups/payout-s1-stage6/`，目录 0700、文件 0600，不入库）

1. **候选 worktree** `/Users/kenny/Desktop/Workspace/Code-MoR-po-rel`（`git worktree add --detach <绝对路径> 9eab368c`）。迁入 po-int 的改动：文件清单以 po-int `git status --short`（M + ??）与 `git diff --name-only 30361be7` 的交集为准，排除 `node_modules`、`target`、`dist`、`.vite-cache`、`release-draft/`、`REPORT-*.md`。`30361be7..9eab368c` 之间被改过的文件按三方合并（`git merge-file`，以 `30361be7` 版为共同祖先），保留两边内容；已知重叠：`table-ownership.yaml` / `TableOwnershipAutoTest`（264ea3d2）、REQ-04 碰过的 ops-bff / ops-web / Platform 文件（`console-resource-capabilities.json`、Platform 初始化 SQL、导航与能力文件、i18n 源）。产出 `release/evidence/source-files.sha256`（本需求文件清单与哈希）与 `release/evidence/merge-notes.md`（逐个冲突的处理）。**漂移检查**：候选树与 `9eab368c` 的差异只能是本需求文件。
2. **验证**（候选树，`package`，模块级 `-am`，只用 `mor-local-*` 本机库；payout 测试用测试库 `mor_payout`，**不要**碰 `mor_payout_v2e2e` 与本机 e2e 进程）：payout、account、platform-support（受影响测试类即可，共享库导致的失败要按类重跑区分）、client-console-bff、operations-console-bff、arch-rules；ops-web `tsc --noEmit`、`npm test`、`npm run build`、`i18n:export-source`；全 reactor `test-compile`。
3. **现网基线**（`release/baseline.md`）：mor-test 现网 account-service、platform-support-service、client-console-bff、operations-console-bff、operations-console-web、api-gateway、vault-service 的镜像 ID、tag；`docker cp` **取出** JAR / dist 后与 main `9eab368c` 构建产物逐条目比对（JAR 内条目哈希、dist 文件 sha256），给出每个服务"现网独有内容"数量（必须为 0，否则停在待裁决）；`printenv` 中相关键名（不抄凭据值）；现有 override 链（grep 历史 `requirements/*/release/compose-candidate*.yaml`）。已知现网：account `tax-20261029a`（8a21dab8）、Platform `rq04-20261030b`（af13e77a）、client-bff `legal-20261028a`（8fe7f24c）、ops-bff `rq04-20261030b`（4bd4c3df）、ops-web `rq04-20261030b`（2f7f8780）、gateway d51daee3。
4. **镜像**：按 `test-environment` 的方式构建 payout-service（新）、account-service、platform-support-service、client-console-bff、operations-console-bff、operations-console-web，显式 tag `payout-s1-20261030a`，**不得**改动 `:development` 或其他 tag。记录 `sha256:`，核对镜像内 JAR / dist 与构建产物相同、health-probe / Entrypoint / User / Healthcheck / 端口与同类现网镜像一致（`release/evidence/image-verify.txt`）。每个服务写候选相对现网的条目差异摘要（新增 / 改动 / 删除，删除必须解释）。判断每个服务是否必须随发并说明（例如 client-bff 若只为 Client 接口而不发，Client 侧验收做不了）。候选 JAR 另存 `~/Code-MoR-backups/payout-s1-stage6/candidate/`（W9 要用候选 Platform JAR 复跑注册块编译）。
5. **新服务 payout-service 落位**（照 `mor-test-new-service-checklist.md` 与 tax-service 首发）：固定 IP（`docker network inspect mor-test-backend` 只读 + 两个 compose 基础文件 grep）、Snowflake region / worker（0–31 中未占用）、端口 18117、DB（`mor_payout` / `payout_app`，密码放 test-environment 现有的秘密机制里——照 tax-service 怎么放的做，写出步骤，不生成进库）、Redis、Nacos（`payout-service.yaml` 是否必须存在、mor-test 命名空间与 group）、`PAYOUT_ACCOUNT_SERVICE_URL=http://account-service:18114`、Platform 地址（lb 发现是否在 mor-test 可用：payout 是否注册 Nacos、Platform 是否注册）、XXL 执行器组与 `compensationEventScan` 任务登记参数、health-probe 允许列表（若需改 `test-environment/java/LocalHttpHealthProbe.java` 与 `scripts/test_java_image_health_probe.py`，改在候选树并重编译进镜像）、mor-test 内部认证（internal-auth）下 payout→account、payout→Platform 通知、client-bff / ops-bff→payout 所需的白名单 / 受众登记（只读核对现网配置，写出登记步骤）。Platform 需新增 `notification.http.callers.payout-service[0..3]`（四个通知模板）——确认 mor-test 的 Platform 用什么机制给（env / Nacos），写进 Platform 候选覆盖。
6. **compose 文件**：每个发布服务 `release/compose-candidate.<service>.yaml` 与 `release/compose-rollback.<service>.yaml`，固定 `sha256:`；env 完整继承现网 `printenv` 与历史 override 中的键（逐键逐值比对渲染结果，写 `release/evidence/env-check.txt`，凭据只比哈希不写值）；新增键单独列出。ops-bff / client-bff 新增 payout 地址键。
7. **报告** `release/W8-report.md`：以上结论、差异、风险、待裁决、阶段 7 需要的容器动作清单（停 / 起哪些、顺序依赖如"消费方先上"、每个约多少秒中断）。

## 规则

- mor-test 只读：只允许 `docker inspect / ps / logs / network inspect`、`docker exec … printenv / sha256sum / cat`（读）、`docker cp` 从容器**取出**、只读 SQL、Nacos / XXL / RabbitMQ 只读查询。不停 / 起容器，不 `compose.sh up`，不写 SQL / Nacos / XXL / MQ，不 `docker cp` 写入。
- 本机 SQL 只用 `mor-local-mysql-1`；本机 e2e 共享栈（`local-environment/e2e`，另一会话 43 即将接手）不动。
- 不提交、不推送；不改主工作区（需求目录 `release/` 与 `tasks/` 除外）和其他会话的 worktree；不改 `test-environment/compose*.yaml` 基础文件（必要的仓库改动写进候选树并在报告列出）。
- 凭据只读入变量，不打印、不写文件；`.env.local`、`test-environment` 秘密文件同理。
- 不调用任何 Superpowers skill。不跑全 reactor 测试。
- 卡住超过 10 分钟或遇到需要业务裁决、与他人线上改动冲突的问题，写进报告"待裁决"并继续其他部分。完成后回复一段摘要（≤ 25 行）。
