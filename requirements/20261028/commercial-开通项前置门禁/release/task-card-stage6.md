# REQ-01 阶段 6 任务卡：mor-test 发布准备（只读核对 + 本机构建）

## 目标

产出可审阅、可执行的 mor-test 发布清单。**本阶段不改 mor-test 任何东西**（不停 / 起容器、不执行 SQL、不写 XXL / MQ / Platform 数据、不发布语言包）；只做只读核对、备份脚本准备、本机构建与本机演练。阶段 7 的环境变更由主会话执行。

> **更正（2026-10-29）**：国家包法务修订已提交 main `ac3145f4` + `365c0e5e`（未推送）。候选基线改为 `365c0e5e`，不再从 Code-MoR-legal-rel 拷文件；仍须与现网逐字节核对。

## 输入

- 方案：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/technique.md`（第 8.3 节部署顺序约束、第 14 节注册与 data-query、第 15.3 节发布要点）
- 阶段 4 / 5 报告：同目录 `verification/stage4-report.md`、`verification/stage5-report.md`（本机专用路由 SQL、语言包导入方式可参考）
- 代码：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-gate`（基于 `663e5e4a`，本需求改动未提交）
- **必须合入的他人线上改动**：国家包法务修订（requirements/20261027/client-master-国家包法务修订）已部署 mor-test 但未提交，源码在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-legal-rel`（= `e2185c1b` + 93 个文件，其中 ops-bff 1 个、ops-web 12 个）。本需求的 ops-bff / ops-web 候选**必须包含**这些改动，否则会回退线上功能。其发布记录在该需求 `release/`（compose-candidate / rollback 文件，可参考 env 与做法）。
- 经验（必须遵守）：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `mor-test-pin-image-ids.md`、`mor-test-compose-sh-overrides.md`、`mor-test-release-override-env.md`、`mor-test-release-card-lessons.md`、`mor-test-deploy-lessons.md`、`registration-block-gateway-compile.md`、`ops-web-shared-tree-release.md`、`legal-pack-revision-delivered.md`；mor-test 访问方式见 `test-environment/private/access.md`。

## 产出（全部写在 `requirements/20261028/commercial-开通项前置门禁/release/`）

1. **`baseline.md`**：mor-test 现网 commercial / operations-console-bff / operations-console-web / platform-support / api-gateway 的容器镜像 ID（`docker inspect … {{.Image}}`）、对应来源（核对 CURRENT_IMPLEMENTATION 与各需求 release 记录）、各容器 `printenv` 的键清单（不记录值）、历史 compose-candidate 中这些服务的 environment 键；mor-test `mor_commercial.t_agreement_capability` 行数与 status 分布（只读）；`compliance.events` 交换机是否存在、commercial 当前是否有 MQ 消费配置、commercial XXL 执行器是否启用（env）；mor_platform 当前最大注册块编号与可用编号；mor-test ops 语言包当前版本号。
2. **候选源码**：新建 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-gate-rel`（`git worktree add --detach` 用绝对路径，基于 `663e5e4a`）→ 先拷入法务修订的 93 个文件（从 Code-MoR-legal-rel 按 `git status --short` 列表复制）→ **先构建 ops-web 并与 mor-test 现网 ops-web 的静态文件逐字节比对**（证明基线 = 现网 45ee11cb 内容），ops-bff 同理核对 JAR 内容清单 → 再拷入本需求 125 个文件（从 Code-MoR-gate 按 `git status --short`）→ 处理冲突（重叠文件逐个三方比对，写进报告）。私有 Maven 仓库用 `~/.m3-gate`。
3. **验证**：在候选 worktree 跑 commercial auto（全部）、ops-bff 商务相关 + 全量、ops-web `npm test` / `build` / `i18n:export-source`、全 reactor test-compile。注意导出 `local-environment/.env.local` 凭据（不要打印）。**不跑全 reactor 测试。**
4. **镜像**：用 `test-environment` 的方式为 commercial、operations-console-bff、operations-console-web 构建镜像，显式 tag `gate-20261029a`，记录 `sha256:` ID；不要用 `:development` 标签部署。
5. **SQL**：
   - `sql/mor_commercial-migrate.sql`（= `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status.sql`）与 `sql/mor_commercial-rollback.sql`，加执行前备份命令（`mysqldump mor_commercial t_agreement_capability`）。
   - `sql/mor_platform-block-<N>.sql` 与 `…-rollback.sql`：3 条 ops Route（suspend / resume / prerequisite-check，权限分别为合同维护 / 合同维护 / 合同查看，guard 参照同形状既有 Operations 路由），按 `registration-block-gateway-compile.md` 在无网络 MySQL 容器载入 mor-test `mor_platform` 只读备份后执行该块并用候选 Platform 源码的真实编译器验证 Gateway 快照可编译（休眠与激活两种状态）。只读备份通过只读查询导出，不在 mor-test 写入。
6. **MQ / XXL**：commercial 新消费 `compliance.events` 的队列拓扑（commercial.compliance.p0..p3、DLX）由应用启动声明——核对 mor-test RabbitMQ 账号权限是否允许 commercial 声明这些队列（只读查看权限配置）；写出两个 XXL 任务（scope=PENDING 每 5 分钟、scope=ALL 每日 03:00）的登记参数与执行器组；确认 commercial 容器需要新增 / 改动的 env（如 XXL 启用、消费开关），写进 compose-candidate。
7. **compose 文件**：`compose-candidate.<service>.yaml` 与 `compose-rollback.<service>.yaml`（三个服务），固定 `sha256:` 镜像 ID，env 键完整继承现网与历史 override（含法务修订的 ops-bff / ops-web 候选里的键）。
8. **语言包**：本需求 commercial 命名空间新增 / 删除 key 清单（en + zh-CN），导入与发布步骤；确认法务修订的 #19 内容不被覆盖（只导入 commercial 命名空间）。
9. **data-query（单独授权项）**：只读账号创建 SQL（mor_commercial，只 SELECT 本需求三条模板涉及的表）、Vault 存密码步骤、数据源与 3 条模板登记步骤——写成独立的可选附录，不和主发布混在一起。
10. **`runbook.md`**：按顺序的执行步骤（含每步命令、预期结果、失败即停的判断点）：通知其他会话 → 生成回滚文件（按现网）→ 备份 → 停 commercial → mor_commercial 迁移 → 起 commercial 新镜像 → ops-bff → ops-web → 注册块（先休眠编译验证再激活）→ XXL 登记 → 语言包 → 验收 → 记录。回滚步骤（逆序：先 ops-web / ops-bff 旧镜像、再停 commercial → 回滚 SQL → 旧 commercial 镜像 → 注册块回滚）。**部署顺序约束**：commercial 新代码与迁移 SQL 必须相邻；旧 ops-web 读不懂 ACTIVE，所以 ops-bff / ops-web 紧随其后，窗口内合同详情页可能短暂报错，写进影响说明。
11. **验收脚本**：`acceptance/` 下 Playwright（参照法务修订 `verification/ui-smoke/ops_legal.mortest.mjs` 的登录方式）+ API 冒烟：现网演示合同（DEMO-CL-001…）开通项全部仍为 ACTIVE + ENABLED、Checkout 解析结果与发布前一致（发布前先记录一次）；在一份演示合同上新增开通项 → 等前置 → 检查前置 → 结果符合预期（数据时间 ≥ 各配置生效时间）；暂停 / 恢复；页面 en / zh-CN 1440 截图。写明验收会留下哪些数据。
12. **`stage6-report.md`**：以上全部的结论、发现、风险、需要用户授权的具体动作清单（主发布 / data-query 附录分开列）。

## 规则

- mor-test 只读：只允许 `docker inspect`、`docker exec … printenv`、只读 SQL 查询、`docker cp` 从容器**取出**文件、RabbitMQ / XXL 只读查询。禁止 stop / up / restart、任何写 SQL、XXL 写、MQ 声明、语言包发布、`docker cp` 写入容器。
- 不提交、不推送；不改主工作区代码；不改 `test-environment/compose*.yaml` 基础文件。
- 所有命令前台执行；收到"暂停"消息就在当前命令结束后停下、更新报告。
- 遇业务判断或与他人线上改动冲突无法自行判定时，写进报告"待裁决"，继续做其他部分。
