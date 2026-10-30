# 阶段 6 任务卡：mor-test 发布准备（只读核对 + 本机构建）

## 目标

产出可审阅、可执行的 mor-test 发布清单。**本阶段不改 mor-test 任何东西**：不停 / 起容器，不执行任何写 SQL，不写 XXL / MQ / Platform 数据，不发布语言包，不 `docker cp` 写入容器。阶段 7 的环境变更由主会话执行。

## 输入

- 需求目录：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261029/tax-结账算税与事件登记/`（`requirement.md`、`technique.md` 第 8.4、12、15.3、16 节，`verification/stage4-report.md`、`verification/stage5-report.md` 第 5 节"阶段 6 前置"、`verification/stage5-report-backend.md` 第 7、8 节、`verification/stage5-report-ops.md`、`db/platform-registration.sql`）。
- 代码：tax worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax`（阶段 4、5 完成态，基于 `e3e0c12f`，未提交）。
- 基线：main `a90e4292`（含 REQ-03 提交 `9d272ae6`；REQ-03 会话确认 mor-test 的 risk-decision / ops-bff / platform-support / ops-web 与 `9d272ae6` 一致）。**更正（2026-10-30）**：main 已前进到 `30361be7`（工作流节点编辑面板，mor-test ops-web 与之逐字节一致），候选基线改为 `30361be7` + tax 改动。
- 阶段 6 中补的测试修复：`paytech-messaging` 的 `YamlEventCatalogAutoTest` 第 53 行改为 `List.of("billing", "tax")`（事件目录消费方已加 tax；阶段 4 未跑 messaging 模块测试而漏掉），已在开发 worktree 修复，12 / 12 通过。
- 先例（结构与粒度照做）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/release/`（`task-card-stage6.md`、`runbook.md`、`baseline.md`、`stage6-report.md`、`acceptance/`）。
- 经验（必须读并遵守）：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `mor-test-pin-image-ids.md`、`mor-test-compose-sh-overrides.md`、`mor-test-release-override-env.md`、`mor-test-release-card-lessons.md`、`mor-test-deploy-lessons.md`、`mor-test-new-service-checklist.md`、`registration-block-gateway-compile.md`、`ops-web-shared-tree-release.md`、`file-public-bucket-delivered.md`（consumers-before-provider）、`local-vs-mor-test-containers.md`。

## 产出（全部写在需求目录 `release/` 下）

1. **`baseline.md`**：mor-test 现网 tax-service、acquiring、billing、account、ledger、payment、operations-console-bff、operations-console-web、platform-support、api-gateway 的镜像 ID（`docker inspect … {{.Image}}`）、来源核对（`docker cp` 取出 JAR / dist 后与 main `a90e4292` 构建产物比对，或对照 CURRENT_IMPLEMENTATION 与各需求 release 记录）、`printenv` 中与本次相关的键（不抄凭据值）、现有 compose override 链。
2. **候选源码**：用绝对路径建 `/Users/kenny/Desktop/Workspace/Code-MoR-tax-rel`（`git worktree add --detach … a90e4292`），把 tax worktree 的改动迁入：文件清单以 tax worktree 的 `git status --short`（M + ??）为准，排除 `node_modules`、`target`、`.vite-cache`；与 REQ-03 改动重叠的文件（ops-bff 测试基类 / 配置、`pom.xml`、`console-resource-capabilities.json`、ops-web 导航与能力文件）按三方合并，保留两边内容（参照 `/Users/kenny/Desktop/Workspace/Code-MoR-taxcombo` 中已做过的合并）。列出全部迁入文件与冲突处理。
3. **验证**（候选 worktree）：tax、acquiring、billing、account、ledger、ops-bff 模块测试（`package`，只用 `mor-local-*`），ops-web `npm test` / `build` / `i18n:export-source`，全 reactor `test-compile`；arch-rules `TableOwnershipAutoTest`。不跑全 reactor 测试。
4. **镜像**：按 `test-environment` 的方式为 tax-service、acquiring、billing、account、operations-console-bff、operations-console-web 构建镜像，显式 tag `tax-20261029a`，记录 `sha256:`。判断 platform-support 是否必须随发：若 mor-test 现网 platform-support 的能力清单缺 `OPERATIONS_FINANCE_TAX` 会导致注册或菜单失败，则也构建 platform-support（基于候选源码）并说明理由；否则不发。ledger、payment 不发（说明依据）。
5. **SQL**（文件 + 回滚 + 执行前备份命令）：
   - `sql/mor_tax-rebuild.sql` / `-rollback.sql`（来自 `changes/2026-10-29-tax-rebuild.sql`），`sql/mor_tax-initial-data.sql`：只读查出 mor-test 实际运行模式下 AE001 的 Party（acquiring 商业解析 MOCK 时为 MOCK 配置中的 `880000000702`；REAL 时取 `mor_client_master.t_operating_entity`），代入并写明依据。先确认 mor-test `mor_tax` 无业务数据。
   - `sql/mor_acquiring-migrate.sql` / `-rollback.sql`（`changes/2026-10-29-order-tax-calculation.sql`），附回滚前置检查（存在新订单时回滚的影响）。
   - `sql/mor_billing-comment.sql`（只改注释的变更文件）。
   - `sql/mor_platform-block-<N>.sql` / `-rollback.sql`：由 `db/platform-registration.sql` 生成，块号按现网已用号段（REQ-03 用到 143）只读查询后分配；文件写入路由先只读确认默认私有策略允许 `application/pdf`。**按 `registration-block-gateway-compile.md` 在无网络 MySQL 容器载入现网备份，休眠态与激活态都用候选源码的真实编译器编译通过。**
6. **MQ / XXL / 内部认证**：tax 声明 `tax.acquiring.*` 队列所需的 RabbitMQ 权限（只读查看）；tax XXL 执行器组与 `compensationEventScan` 任务登记参数；mor-test `internal-auth` 模式下 tax（调 Platform 文件）、acquiring（调 tax）、ops-bff（调 tax）所需的白名单 / 受众登记（只读核对，写出登记步骤）。
7. **compose 文件**：`compose-candidate.<service>.yaml` 与 `compose-rollback.<service>.yaml`（每个发布的服务），固定 `sha256:`；env 完整继承现网 `printenv` 与历史 override 中的键，并新增：tax 的 DB / RabbitMQ / XXL / Platform 地址 / Snowflake worker（0–31 中未占用）/ 存储证书信任（mor-test 存储端点若为自签证书，写出信任方式）；acquiring `ACQUIRING_TAX_SERVICE_URL`；ops-bff `OPERATIONS_TAX_BASE_URL`。
8. **语言包**：Platform 多语言管理创建 `tax` 命名空间、导入 en / zh-CN（含阶段 5 新增的 2 个 `DONE_BLOCKED` 键与导航键），发布步骤；确认 REQ-03 的 #23 内容不被覆盖。
9. **影响盘点（只读）**：
   - mor-test 的 acquiring 商业解析模式；`billing.acquiring.dlq` 现状；据此判断阶段 7 能否端到端验证 billing → account → ledger（入账段在本机因缺财务基础数据未验证）。
   - mor-test 现有演示数据、Client API 验收脚本、其他会话 `requirements/*/release/acceptance/` 中使用的商品税类（如 `STANDARD`）：发布后这些建单会因未知税类而 BLOCKED。列出所有出处（文件、行号、谁的需求），给出选项（改用 `SAAS_AUTOMATED`、在演示规则包登记别名、或发布前后各自处理），**不要改别人的文件**。
10. **`runbook.md`**：按顺序的执行步骤（命令、预期结果、失败即停判断点）：通知其他会话 → 生成回滚文件（按现网）→ 备份 → Platform 注册块（休眠编译 → 激活）→ `mor_tax` 重建 + 初始化 → tax-service 新镜像 → XXL 登记 → billing、account（消费方先上）→ `mor_acquiring` 迁移 → acquiring → ops-bff → （platform-support 如需）→ ops-web → 语言包 → 验收 → 记录。回滚按逆序，写清每步回滚命令与判断点。
11. **验收脚本**（`acceptance/`）：API 冒烟 + Playwright（参照先例的登录方式）：①发布前记录现网一笔演示建单的金额与状态；②发布后：沙特订单（`SAAS_AUTOMATED`）暂估 → 资料提交重算 15% → 付款（mor-test 可用的渠道 mock）→ tax 正式事实与税票；科威特 BLOCKED；PSP / CLIENT 来源 NOT_MANAGED（如可造）；存量订单；运营税务页 zh-CN / en × 1440 / 390；REQ-03 承保页回归。合成数据的时间注意 `mor-test-release-card-lessons.md` 第 1、2 条。
12. **`stage6-report.md`**：以上全部结论、发现、风险、待裁决项，以及**需要用户授权的阶段 7 具体动作清单**。

## 规则

- mor-test 只读：只允许 `docker inspect`、`docker exec … printenv`、只读 SQL、`docker cp` 从容器**取出**文件、RabbitMQ / XXL 只读查询。
- 本机只用 `mor-local-*` 容器；本机 e2e 进程不动（阶段 5 环境保持）。
- 不提交、不推送；不改主工作区与其他会话的 worktree；不改 `test-environment/compose*.yaml` 基础文件。
- 前台执行；卡住超过 10 分钟或遇到需要业务裁决、与他人线上改动冲突的问题，写进报告"待裁决"并继续其他部分。
