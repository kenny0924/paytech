# platform-support 工作流 · 附件内嵌预览跟进 · mor-test 发布包

阶段 6，2026-10-22，核对时间 17:00–17:20Z。本阶段只做了：只读核对（`docker inspect`、临时容器取出镜像内容、容器内 `sha256sum` / `printenv` 只取键名、只读 SELECT）；在干净 worktree 构建两个 Java 镜像；用本机临时库演练 SQL；写发布包。**没有改 mor-test 的任何容器、数据库、Gateway 或 compose 主文件，没有提交，没有 install，没有 docker cp 进运行容器。** 阶段 7 由主会话按第 5 节执行，结果写第 8 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：**
- **源码：** main `42879401`（父 `5e72174b`）。#28 附件下载授权增加可选 Query `disposition`（Platform API / Controller / Processor、BFF 透传）；ops-web `src/workflow/*` 的“查看”内嵌预览。
- **数据库（只有 `mor_platform`）：** 块 127 追加 `t_gateway_route_query_param (900000000000127030, 900000000000127432, 'disposition')`，路由 127432 `version+1`。
- **镜像：** platform-support-service、operations-console-bff。**operations-console-web 本包不替换**（第 0 节 B1）。

## 0. 阻塞与待决定

| 编号 | 事实 | 影响 | 建议 |
|---|---|---|---|
| **B1** | **现网 ops-web 已不是 `7551dc3b`。** 17:02Z 读到 `mor-test-operations-console-web-1` 运行 `sha256:5a9eb12df3803ee0d34a4520255defa37c1f1f286b61e8e442341bef4a446692`（tag `dataquery-ux-20261022a`，16:43Z 构建、16:44Z 启动），由 code-mor-55（数据查询页面体验）按 `requirements/20261022/platform-support-数据查询服务/release-ux/compose-candidate.yaml` 发布。<br>**归因已证实：** `5e72174b` 的 `operations-console-web` 导出后叠加主工作区 16 个未提交文件构建，`dist/` 与现网 188 个文件**逐字节一致**（nginx.conf 也一致）。16 个文件：按钮布局 6 个（code-mor-42：`src/pages/ApiCredentials.tsx`、`Audit.tsx`、`GatewayConfiguration.tsx`、`src/configuration/configuration.css`、`src/styles.css`、新增 `src/components/RefreshButton.tsx`）+ 数据查询体验 10 个（code-mor-55：`src/dataQuery/DeclarationPanel.tsx`、`TemplateEditor.tsx`、`TrialPanel.tsx`、`VersionDrawer.tsx`、`dataQuery.css`、`templateModel.ts`、新增 `templateSync.ts`、`templateSync.test.ts`、`src/pages/DataSources.tsx`、`QueryTemplates.tsx`）。均未提交。 | 只用 `42879401` 构建 ops-web 会**回退**按钮布局与数据查询体验；按任务约定，遇到他人未提交内容已上线即停止 ops-web 构建，交主会话决定 | 两条路，二选一：<br>**(a)** 本次只发 Platform + ops-bff + SQL（本包现状）。后端改动向后兼容：现网 ops-web 不发 `disposition`，行为与现在相同；新 ops-web 留待 16 个文件提交（或其 Owner / 用户同意按当前内容带上）后再发。<br>**(b)** 主会话/用户同意“`42879401` + 主工作区 16 个文件当前内容”作为 ops-web 构建源：在本 worktree 叠加 16 个文件 → `npm run build` → 先确认去掉 `src/workflow` 改动时与 `5a9eb12d` 逐字节一致（本次已用 `5e72174b` 证明这 16 个文件当前内容可复现现网）→ 再确认差异只在 `WorkflowParts-*.js` → `docker build`。 |
| **B2** | **与 3DS + 卡 BIN 合并发布包互斥。** `requirements/20261022/payment-3DS全链路/release/` 也要替换 platform、ops-bff、ops-web，构建源是 `Code-MoR-card3ds-rel`（`73a2460d` + 两项需求补丁），**不含 `42879401`**。 | 3DS 包若在本包之后执行，会把 #28 `disposition` 回退（数据库行保留无害）；本包若在 3DS 之后执行，会回退 3DS / BIN 的 Platform、ops-bff 改动 | 第 5 节第 0 步与 3DS / BIN 会话约定先后。后发的一方须在先发者的源码上重建：若本包先发，3DS 的 Platform / ops-bff 构建源需加上 `42879401` 的 9 个 Java 文件 |
| N1 | Gateway 对未登记的 Query 返回 400 `GATEWAY_INVALID_REQUEST`（本机 e2e 首轮已遇到，delivery.md 记录）。 | 只要有会发 `disposition` 的 ops-web 上线，SQL 必须已提交；回滚时反之（先回退 ops-web 再删行） | 本包的顺序：SQL 先于任何镜像。本包不换 ops-web，所以 Platform / ops-bff 与 SQL 的先后都不影响现网 |

## 1. 基线与源码

### 1.1 现网（17:02Z，`docker inspect`，= 回滚目标）

| 服务 | 运行镜像 | 来源 | 启动（UTC） | 健康 / 重启 | 运行 JAR SHA-256 |
|---|---|---|---|---|---|
| platform-support-service | `sha256:7cb304024b520513dd461e22d6c423d11072ede95b47c9a56cf1c6e4f322f226` | `filestore-s2-20261022`（源码 `73a2460d`） | 10-22 15:23:10 | healthy / 0 | `2d12967f2320998c…`（容器内 `sha256sum` 与镜像内一致） |
| operations-console-bff | `sha256:a5b9a17cf88c4b6cc043ef543d890ca5f5e3e8cf1a99d2fcd1c1c34ceabf4820` | `filestore-s2-20261022` | 10-22 15:24:06 | healthy / 0 | `3d10e562aaa68f80…`（同上） |
| operations-console-web | `sha256:5a9eb12df3803ee0d34a4520255defa37c1f1f286b61e8e442341bef4a446692` | `dataquery-ux-20261022a`（B1） | 10-22 16:44:01 | healthy / 0 | 运行容器 `dist/` 188 个文件与镜像内逐字节一致 |

三个容器的 compose 标签 `config_files` = 主三件 + `release-s2/compose-candidate.yaml` + `数据查询服务/release-ux/compose-candidate.yaml`。

### 1.2 构建源

| 项 | 内容 |
|---|---|
| worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-wfinline-rel`（detached `42879401`，`git status` 干净）。保留到发布完成，用完由主会话 `git worktree remove` |
| `73a2460d..5e72174b` | paytech / ops-web 只改 `platform-support.pdma.json` 与测试 `FileStorageTestMapper.java`，不影响运行产物 |
| Java | `JAVA_HOME=…/jdk-25.jdk ./mvnw -o -q -pl platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am package -DskipTests`，成功，**没有 install**。取 `*-exec.jar`（同 `test-environment/scripts/build.sh`） |
| 镜像 | `test-environment/java/Dockerfile`（worktree 与主工作区相同）；`health-probe/` 从现网镜像经临时容器取出（两服务相同）。tag `wfinline-20261022`，没有动 `:development` |
| 镜像核对 | 镜像内 `/app/application.jar` SHA-256 = 构建产物；`health-probe/` 与现网相同；Entrypoint / Cmd / User / WorkingDir / Env 与现网镜像相同 |

### 1.3 超集证明（现网 ⊂ `42879401`，且只多本次改动）

方法：现网 JAR 从镜像经临时容器取出（`docker create` → `docker cp` → `docker rm`，未碰运行容器）。`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目比较 CRC，第三方 JAR 按整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引。

| 服务 | 候选 | 结果 |
|---|---|---|
| platform-support-service | `sha256:ac0b484565011b0cc328329bbae77b0236d706c493aed141984d24a0a48fd211`（JAR `d306f83b935d85e4`） | 2690 = 2690 项，**删除 0、新增 0、改动 4**：`ext/rest/workflow/WorkflowAttachmentController`；api `WorkflowAttachmentApi`；core `AuthorizeWorkflowAttachmentDownloadProcessor`、`AuthorizeWorkflowAttachmentDownloadRequest` |
| operations-console-bff | `sha256:798d5e4cd450486d98b79c39bf8a37ade084e2d31ae44b0987b5f6fcfe1618af`（JAR `caf95fee3688094b`） | 2458 = 2458 项，**删除 0、新增 0、改动 4**：`ext/rest/WorkflowAttachmentController`、`integration/PlatformWorkflowClient`、`integration/impl/PlatformWorkflowClientImpl`；嵌套 api `WorkflowAttachmentApi` |
| operations-console-web | 未构建（B1） | **Check A：** `5e72174b` 导出构建 187 个文件，与现网 188 个不一致；去哈希后差异只在 `ApiCredentials`、`Audit`、`GatewayConfiguration`、`DataSources`、`QueryTemplates`、`TemplateEditor`、`RefreshButton`（现网独有）、`configuration.css`、`dataQuery.css`、`index.css`、`index.js`。`5e72174b` + 16 个文件 = 现网逐字节一致。<br>**Check B：** `42879401` 与 `5e72174b` 构建去哈希比较，只有 `WorkflowParts-*.js`（2 个块）内容不同，其余 185 个相同 |

## 2. 环境变量

只列键名，不列值。方法：`compose.sh -f <覆盖文件> config --format json` 渲染，与运行容器 `Config.Env` 逐键、逐值在进程内比较（镜像自带的同值键除外）。

| 服务 | 候选 | 回滚 |
|---|---|---|
| platform-support-service | 53 = 现网 53，缺 0、多 0、值不同 0 | 同左 |
| operations-console-bff | 14 = 14，缺 0、多 0、值不同 0 | 同左 |
| operations-console-web | 0 = 0 | 同左 |

- 两个文件都以 `release-s2/compose-candidate.yaml` 为起点，只改 image；去掉不属于本包的 client-* 三项；ops-web 钉现网 `5a9eb12d`，避免叠加本文件时回退。
- 已核对保留：`PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`（DUAL）、`PAYTECH_INSTANCE_ID`、5 个 `PLATFORM_SUPPORT_RABBITMQ_*`、`DATAQUERY_HTTP_CALLERS_0`、`PLATFORM_SUPPORT_FILES_ENABLED`、8 个旧存储键置空；ops-bff 的 `OPERATIONS_RECONCILIATION_BASE_URL`、`OPERATIONS_ACCOUNT/BILLING/LEDGER_BASE_URL` 等 14 键。

## 3. 只读核对（阶段 6 已做）

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22T17:08:52Z 执行，6 项全部 ok=1，输出 [`sql/00-precheck-20261022.out`](./sql/00-precheck-20261022.out)：
- `route_query_param_id 127030` 不存在；路由 127432 没有任何 Query 参数；
- 路由 127432 `operations-workflow-attachment-download` POST `/api/operations/workflow/v1/instances/{instanceId}/files/{fileId}/download`，ACTIVE，**version 2**（执行后应为 3）；
- 块 127 query_param 29 行、gateway_route ACTIVE 32 条。

**SQL 演练（本机 `mor-local-mysql-1` 临时库 `wfi_rel_check`，用后已删）：** 结构取 mor-test 两张表 `--no-data` + 路由 127432 一行。
1. 原文件（库名保护 `mor_platform`）在临时库：影响 0 / 0，guard 报错，未改数据；
2. 库名替换后：1 / 1、version 2 → 3、`counts ok`、提交；99 postcheck 前两项 ok（块 127 计数项因临时库只有一行不适用）；
3. 90 回滚：1 / 1、`counts ok`，参数行 0，version 4。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256；platform / ops-bff 换新，ops-web 两份都钉现网 `5a9eb12d` |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 执行前只读核对 |
| [sql/10-route-query-param.sql](./sql/10-route-query-param.sql) | INSERT 127030 + 路由 127432 `version+1`；`DATABASE()='mor_platform'` 保护；不含 COMMIT，末尾 guard 1 / 1 |
| [sql/90-rollback.sql](./sql/90-rollback.sql) | 删 127030 + 路由 `version+1`；不含 COMMIT，guard 1 / 1 |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 执行后只读核对（块 127 query_param 30） |
| [scripts/backup.sh](./scripts/backup.sh) | `mor_platform` 整库、镜像 / JAR SHA-256（容器内 `sha256sum`）、两个 Java 服务环境变量键名、覆盖文件、precheck，写 `~/Code-MoR-backups/<UTC>-workflow-inline-mor-test/`（**未运行**） |
| [scripts/apply_sql.sh](./scripts/apply_sql.sh) | `precheck` / `query-param-dry` / `query-param` / `postcheck` / `rollback-dry` / `rollback`；非 dry 追加 COMMIT；root 口令只经 `MYSQL_PWD` |
| [acceptance/inline_mortest.mjs](./acceptance/inline_mortest.mjs) | API 造数（前缀 `WF-INLINE-20261022`）+ API 检查（INLINE / ATTACHMENT / 缺省 / 非法 400，存储响应头）+ 浏览器检查（查看内嵌、下载 ATTACHMENT）+ 终止实例。**未运行**；会写 mor-test 业务数据。浏览器段需要新 ops-web，B1 未解决前用 `SKIP_BROWSER=1` |

## 5. 执行步骤（阶段 7，主会话执行）

在主工作区根目录执行：`R="$PWD/requirements/20261021/platform-support-工作流/release-inline"`、`O="$R/compose-candidate.yaml"`、`RB="$R/compose-rollback.yaml"`。任一步不符合预期就停，并按第 6 节回滚。

0. **协调（B2）：** 与 3DS / 卡 BIN 会话（`payment-3DS全链路/release/`）及可能发布 platform / ops-bff / ops-web 的会话约定先后；B1 选 (a) 或 (b)。期间有人先替换了这三个服务中的任何一个，本包作废，在新基线上重建并重做第 1.3 节。
1. **复核基线：**
   ```
   for s in platform-support-service operations-console-bff operations-console-web; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done
   ```
   `.Image` 必须等于 `compose-rollback.yaml`（`7cb30402…` / `a5b9a17c…` / `5a9eb12d…`），全部 healthy。
2. **备份：** `B=$("$R/scripts/backup.sh")`；打开 `$B/00-precheck.out`，确认 6 项 ok=1、路由 version 2。
3. **SQL：**
   - `"$R/scripts/apply_sql.sh" "$B" query-param-dry`：期望 `query_param_1=1`、`route_1=1`、`route_version_after=3`、`counts ok`（断开即回滚）；
   - `"$R/scripts/apply_sql.sh" "$B" query-param`：同上并输出 `committed`；
   - `"$R/scripts/apply_sql.sh" "$B" postcheck`：全部 ok=1，version 3；
   - 等 Gateway Snapshot 刷新（最多 60 秒）。对现网无影响：现网 ops-web 不发 `disposition`。
4. **ops-web：** B1 选 (a) 时**跳过**。选 (b) 时，用新构建的镜像 ID 更新两个 yaml 的 ops-web 行（回滚仍为 `5a9eb12d`），然后 `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-web`。
5. **ops-bff：** `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-bff`
   - 核对 healthy、重启 0、`.Image` = `sha256:798d5e4c…`；`docker logs --since <开始时间> mor-test-operations-console-bff-1 2>&1 | grep -c ' ERROR '` 为 0；
   - `docker exec mor-test-operations-console-bff-1 printenv | cut -d= -f1 | sort | diff "$B/operations-console-bff-env-keys-before.txt" -` 为空。
6. **Platform：** `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service`
   - 核对 healthy、重启 0、`.Image` = `sha256:ac0b4845…`，启动日志 ERROR 为 0；
   - `docker exec mor-test-platform-support-service-1 printenv | cut -d= -f1 | sort | diff "$B/platform-support-service-env-keys-before.txt" -` 为空；
   - 不可用窗口约 30–60 秒（Platform 承载登录与所有运营接口）。
7. **冒烟：**
   - 只读回归：`python3 -I requirements/20261021/platform-support-工作流/release/acceptance/wf7_smoke.py`，期望全部 PASS（登录、工作流 4 菜单、8 个工作流 GET、数据查询 / 存储 / 渠道回归）；
   - 写数据验收（需用户同意）：`node "$R/acceptance/inline_mortest.mjs"`（B1 选 (a) 时加 `SKIP_BROWSER=1`）。期望 API 段 5 项 PASS：PDF / PNG `INLINE` 且存储 `Content-Disposition: inline…`，`ATTACHMENT` 与缺省为 attachment，`BOGUS` 400；浏览器段（新 ops-web）4 项 PASS；实例被终止；
   - `docker logs --since <第 3 步开始> mor-test-platform-support-service-1 2>&1 | grep ' ERROR '` 为空，ops-bff、api-gateway 同样检查。
8. **记录：** 填第 8 节，`delivery.md` 补环境操作记录；把新镜像 ID 与源码基线 `42879401` 通知第 0 步各会话：今后 platform / ops-bff 的候选须包含 `42879401` 的改动。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| 镜像 | 按 **ops-web（若第 4 步换过）→ ops-bff → platform** 的顺序：`./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service>`。Platform 只能回到 `7cb30402`：第二段删除 DDL 已执行，更早的镜像会缺列失败 |
| SQL | 一般保留：旧 Platform / ops-bff 忽略多余 Query，旧 ops-web 不发。确需删除时，先确认现网 ops-web 不发 `disposition`（否则“查看”“下载”都会被 Gateway 400），再 `apply_sql.sh $B rollback-dry` → `rollback`（1 / 1），`postcheck` 期望第一项 ok=0（已删除） |
| 验收数据 | 保留为证据；如需移除，在运营页面停用模板 `WF_INLINE_20261022_UPLOAD` 与定义 `WF_INLINE_20261022_FLOW`（实例已终止，只读历史） |

## 7. 风险

1. **B1 未决前没有用户可见的变化**：方案 (a) 只让后端具备能力，“查看”内嵌要等新 ops-web 发布。
2. **B2 互斥**：两包谁后发，谁负责在对方源码上重建；否则回退对方改动。
3. **验收会写数据**：模板、定义、实例各 1、文件 2（PDF 约 0.5 KB、PNG 约 0.2 KB），默认不做，等用户同意。
4. **同一账号限制**：mor-test 只有一个管理员，验收流程里执行人与审批人相同，页面会禁用审批决定（“你提交过本节点的动作”），但“文件预览”区照常显示；脚本最后终止实例。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 17:12 | B1 决定 | 采用 (b)：主会话在 worktree 叠加主工作区 16 个现网文件；`src/workflow` 退回 `5e72174b` 时构建与现网 `5a9eb12d` 188 个文件逐字节一致，恢复 `42879401` 后去哈希比较只差 `WorkflowParts-*.js`。镜像 `mor-test/operations-console-web:wfinline-20261022` = `sha256:11f1d24eb8df09113a484cfb06324c050be925cd64ce9c1a37b06fa80f07442d`（镜像内 dist 与构建一致，Entrypoint / User / Cmd 同现网），写入 candidate。B2 已通知 code-mor-6b（其包在本包后按新 HEAD 重建），ops-web 带入文件已通知 code-mor-42 |
| 17:17 | 基线 + 备份 | 三容器 = 回滚文件镜像、healthy / 0；备份 `~/Code-MoR-backups/20261022T171725Z-workflow-inline-mor-test`（mor_platform 全库、env 键、镜像 / JAR）；precheck 6 项 ok |
| 17:17 | SQL | dry `counts ok`；正式 1 / 1、`committed`；postcheck：127030 存在、块 127 query_param 30、路由 127432 ACTIVE、块 127 ACTIVE 路由 32 |
| 17:18 | 镜像 | ops-web `11f1d24e…` → ops-bff `798d5e4c…` → platform `ac0b4845…`，均 healthy / 重启 0；env 键与发布前相同；17:18:18Z 起 platform / ops-bff / gateway ERROR 0 |
| 17:19 | 冒烟 | `release/acceptance/wf7_smoke.py` 14/14 |
| 17:21 | 验收 | `acceptance/inline_mortest.mjs`（首轮因 Node 不解析 `*.localhost` 在存储 PUT 处中断，已遗留动作模板 `WF_INLINE_20261022_UPLOAD`；脚本改用 curl `--resolve` 访问存储并加 `RUN` 后缀，`RUN=B` 重跑）：API INLINE（PDF / PNG）、ATTACHMENT、缺省 ATTACHMENT、非法值 400，存储响应头 inline / attachment 带文件名；浏览器审批页文件预览、PDF iframe、PNG img、下载 ATTACHMENT 全部 PASS，无 ≥400、无页面错误；实例已终止。数据前缀 WF-INLINE-20261022(-B) 保留作证据 |
