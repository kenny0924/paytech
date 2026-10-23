# platform-support 工作流体验优化 · mor-test 发布包

阶段 6（2026-10-23 05:40—06:05Z）。本阶段只做了：只读核对（`docker inspect`、临时容器取出镜像内容、容器内 `sha256sum`、`printenv` / `Config.Env` 只比较不输出值、只读 SELECT、只读冒烟 `wfux_smoke.py --before`）；在 worktree 构建三个镜像；写发布包。**没有停 / 起 / 替换 mor-test 任何容器，没有执行任何写 SQL，没有调用 mor-test 写接口（冒烟只有登录与 GET），没有提交、没有 install、没有改 `:development` tag，没有 docker cp 进运行容器。** 阶段 7 由主会话按第 5 节执行（用户已授权发布），结果写第 8 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **源码：** main `be688db7`（未推送）= 我们的 `f707f2da`（工作流页面体验优化）+ `92d2d1db`（BFF 补交）之上合入公开桶提交 `be688db7`（code-mor-42，父 `ae6bd995`）。
- **换镜像（3 个）：** platform-support-service、operations-console-bff、operations-console-web。
- **数据库（`mor_platform`）：** `../sql/10-action-run-status.sql`（动作状态 VERIFIED → COMPLETED、UNVERIFIED → PENDING 与注释；**必须在停旧 Platform 与起新 Platform 之间执行**）、`../sql/20-registration.sql`（块 127：加菜单 127311「我发起的」、删重新验证路由 127429 及 6 header / 6 guard、角色 510 version+1；新镜像都起来之后执行）。
- **环境变量：** 不新增、不删除（第 2.3 节）。
- **不动：** Gateway、Nginx、Nacos、Vault、XXL、主 compose、card / client-master / client-console-bff 与其余容器。

## 1. 基线（2026-10-23 05:50Z，`docker inspect`，= 回滚目标）

| 服务 | 运行镜像 | 来源 | 启动（UTC） | 健康 / 重启 | 运行 JAR SHA-256 |
|---|---|---|---|---|---|
| platform-support-service | `sha256:68ba631faa8dfb65cb09296cd28a37f6e8c2bcf406bfdecd4de70016e3a6475f` | `pubbucket-20261023a` | 05:24:43 | healthy / 0 | `9d735ffd2321621b…`（容器内与镜像内一致） |
| operations-console-bff | `sha256:06d850d16a3f697f98f8bcc8fe455eed09cbb0cc1dc5adcd8e5935c323d524da` | 同上 | 05:24:10 | healthy / 0 | `fef610e60dac3754…`（同上） |
| operations-console-web | `sha256:f8b5dc3234a587285a5d47c4016240aedaf07248b43185c954343381251d7e5e` | 同上 | 05:25:16 | healthy / 0 | dist 195 个文件 |
| card-service / client-master-service / client-console-bff | `810b6b72…` / `2e495b40…` / `b8ecaaea…` | 同上（本包不替换） | 05:23 | healthy / 0 | `5cc2fd8d…` / `795461b0…` / `aea366e5…` |

六个容器的 compose `config_files` = 主三件 + `20261022/platform-support-文件公开桶/release/compose-candidate.yaml`。api-gateway 为 `270c8ba2…`（3DS 包），本包不涉及。

## 2. 构建与核对

### 2.1 构建源

| 项 | 内容 |
|---|---|
| worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-wfux-rel`（detached `be688db7`；`operations-console-web/node_modules` 链到主工作区）。叠加了主工作区 10 个 ops-web 未提交文件（见 2.4），保留到发布完成，用完由主会话 `git worktree remove --force` |
| Java | `JAVA_HOME=…/jdk-25.jdk ./mvnw -o -q -pl platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package -DskipTests`（worktree 有上次构建残留 target，加了 `clean`），成功，**没有 install**。取 `*-exec.jar`（同 `test-environment/scripts/build.sh`） |
| 镜像 | Java：`test-environment/java/Dockerfile`（worktree 与主工作区相同），构建目录 = `application.jar` + 从现网镜像经临时容器取出的 `health-probe/`（两服务相同）。ops-web：`operations-console-web/Dockerfile`（`deploy/nginx.conf` 与现网镜像逐字节相同）。tag `wfux-20261023`，没有动 `:development` |
| 镜像核对 | 镜像内 `/app/application.jar` = 构建产物（cmp 一致）、`health-probe/` = 现网；ops-web 镜像内 dist 203 个文件 = 构建、nginx.conf = 现网；Entrypoint / Cmd / User / WorkingDir / Env / ExposedPorts / Healthcheck 与对应现网镜像完全相同 |

| 服务 | 候选镜像 ID | JAR SHA-256 |
|---|---|---|
| platform-support-service | `sha256:76587ed4a4b9451789654280cd4940149825433a72c80fecdf03f1fe2b543a55` | `c7357ede0b64ac5c…` |
| operations-console-bff | `sha256:c9a70b8dfe086925d6f74b77dec6eeaa4625c08dd288b4ef2396a7e3f008bebf` | `6a7a8b4fe3dae51f…` |
| operations-console-web | `sha256:a8bb58d7e349acf44a8ec9efc59e352baa134631330940c2d5f267913f81d674` | dist 203 个文件 |

### 2.2 Java 超集证明（现网 ⊂ `be688db7`，且只多本次改动）

方法：现网 JAR 从镜像经临时容器取出（`docker create` → `docker cp` → `docker rm`），sha 与运行容器内 `sha256sum` 一致。`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目比较 CRC，第三方 JAR 按整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引。明细：[superset-platform-support-service.txt](./superset-platform-support-service.txt)、[superset-operations-console-bff.txt](./superset-operations-console-bff.txt)。

| 服务 | 条目 | 删除 | 新增 | 变化 |
|---|---|---|---|---|
| platform-support-service | 现网 2798 / 候选 2796 | 4：`ReverifyWorkflowActionProcessor`、`ReverifyActionRequest`、`ActionOutputVerifier`（允许）+ `FormDefinitionRules$1`（编译器生成的 enum switch 映射类：`f707f2da` 删除了 `FormDefinitionRules` 里唯一的 `switch (type)`，`be688db7` 源码中已无 switch） | 2：`ActionOutputChecker`、`WorkflowTemplateReferenceCountView` | 42：全部在 workflow 模块、`paytech-platform-support-api` workflow 响应 / API、`ext/rest/workflow` 两个类、Foundation `paytech-http-client-support` 两个类（`HttpRequestValidationException`、`HttpServiceClientInterceptor`） |
| operations-console-bff | 现网 2529 / 候选 2530 | 0 | 1：`WorkflowRuntimeValidationConverter` | 11：BFF workflow 控制器 / 客户端 / 转换器 5 个、`ConfigurationValidationConverter`、http-client-support 2 个、platform-support-api workflow 4 个 |

**归因：** `git log 22ede435..be688db7` 共 7 个提交。逐提交列出 `paytech/{foundation,operations-console-bff,platform-support}` 的 `src/main` 改动：`32dbdd18`（PDManer / card 测试）、`623916ac`（acquiring / payment）、`d3d39e40`（文档）、`ae6bd995`（acquiring e2e）为 **0 个**；`be688db7`（公开桶）有 171 个，但现网正是公开桶镜像，与候选 **0 差异**；以上 4 删 / 2 + 1 增 / 42 + 11 变化的每个条目都在 `f707f2da`（51 个 main 文件，含 Foundation http-client-support 2 个）或 `92d2d1db`（BFF 6 个）的文件清单内。**没有现网有而 `be688db7` 没有的类**（除上述 3 个允许删除 + 1 个编译器合成类），即现网没有未提交 Java 代码。

### 2.3 环境变量

主 compose（`test-environment/compose.applications.yaml`，主工作区未修改）已包含六个服务的全部键，候选 / 回滚文件只钉镜像。`compose.sh -f <文件> config --quiet` 两份都通过；渲染 JSON 后在进程内与六个运行容器 `docker inspect .Config.Env` 逐键、逐值比较（期望 = 镜像 Env + compose environment，只输出键名与是否一致）：

| 服务 | compose 键 | 运行键 | 缺 / 多 / 值不同 | 挂载点 |
|---|---|---|---|---|
| platform-support-service | 53 | 59 | 0 / 0 / 0 | 一致 |
| operations-console-bff | 14 | 20 | 0 / 0 / 0 | 一致 |
| operations-console-web | 0 | 7 | 0 / 0 / 0 | 一致 |
| card-service / client-master-service / client-console-bff | 33 / 34 / 10 | 39 / 40 / 16 | 0 / 0 / 0 | 一致 |

候选与回滚两份结果相同。本需求不新增变量。

### 2.4 ops-web：先复现现网，再构建候选

**主工作区现状：** 任务说明中的 16 个现网未提交文件，按钮布局 6 个（`ApiCredentials.tsx`、`Audit.tsx`、`GatewayConfiguration.tsx`、`configuration.css`、`styles.css`、`RefreshButton.tsx`）**已随公开桶提交进 `be688db7`**（主工作区与 `be688db7` 逐字节相同）；仍未提交的是数据查询体验 10 个（code-mor-55）：`src/dataQuery/{DeclarationPanel,TemplateEditor,TrialPanel,VersionDrawer}.tsx`、`dataQuery.css`、`templateModel.ts`、`templateSync.ts`、`templateSync.test.ts`、`src/pages/{DataSources,QueryTemplates}.tsx`，最后修改 10-22 16:38Z（早于现网构建 20:40Z）。SHA-256 见 [ops-web-uncommitted-10.sha256](./ops-web-uncommitted-10.sha256)。主工作区 `src/workflow` 等其余 ops-web 文件无未提交改动。

**复现现网（方法）：** `git archive be688db7 operations-console-web` 导出到暂存区 → 反向应用 `f707f2da` 的 ops-web 补丁（43 个文件；`22ede435..ae6bd995` 之间只有 `f707f2da` 改 ops-web）。唯一冲突是测试文件 `configurationModel.test.ts`（两个提交都改了页面数断言，不进 dist），取 `22ede435` 版本 + 公开桶的 65→66。其余与公开桶重叠的 `ConsolePage.tsx`、`pageCapabilities.ts` 反向补丁干净应用。这等价于“`22ede435` + 公开桶提交”，即现网构建源。叠加 10 个文件后 `npm run build`：**dist 195 个文件与现网镜像逐字节一致**（nginx.conf 也一致），证明 10 个文件当前内容 = 现网，且 Vite 构建可复现。

**候选：** worktree `be688db7` + 同样 10 个文件 → `npm run build`（203 个文件）。构建输入与上面复现输入的差异**只有** `f707f2da` 的 43 个文件（`src/workflow/*` 32 个、`src/pages/Workflow*.tsx` 5 个含新增 `WorkflowStarted.tsx`、`src/navigation/{ConsolePage.tsx,pageCapabilities.ts,menu.test.ts}`、`configurationModel.test.ts`、`package.json` / lock 显式声明已安装的 `@ant-design/icons@6.3.4`），见 [ops-web-source-diff.txt](./ops-web-source-diff.txt)。

**dist 层面（去哈希后）与预期不同：** 不只 workflow / 导航 / 入口块变化，共 49 个块去哈希后相同，约 130 个块不同（[ops-web-dist-diff.txt](./ops-web-dist-diff.txt)）。已用两级归一化排查（去掉 import / export 别名、再把 ≤3 字符的压缩标识符统一）后仍有约 100 个块不同，原因是 Rollup 共享块重新切分：新代码引入了新的共享模块（新增 `checkbox`、`style`、`prefetchModel`、`DeleteOutlined`、`FileOutlined`、`PaperClipOutlined`、`WarningOutlined` 等块，`pageCapabilities` / `EllipsisOutlined` / `FieldDesigner` 块并入他处），所有引用这些共享块的页面块的 import 语句与压缩变量名随之改变。**结论：** 产物层面的差异来自打包切分，不是源码差异；源码层面已严格证明只多 `f707f2da`。与 2.2 不同，ops-web 无法做到“只差 workflow 块”的产物级证明，请主会话知悉（见第 7 节风险 4）。

## 3. 只读核对

### 3.1 数据库预检

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-23T05:53Z 在 `mor-test-mysql-1`（root，只读）执行：**18 项全部 ok=1**，输出 [`sql/00-precheck-20261023.out`](./sql/00-precheck-20261023.out)。

| 项 | 结果 |
|---|---|
| 菜单 127311、`OPERATIONS_WORKFLOW_STARTED`、`/workflow/started` | 均未占用 |
| 父菜单 127301 | ACTIVE（子菜单 127302—127305 四个 ACTIVE） |
| 权限 127105、角色 510 PLATFORM_ADMIN | 存在；角色 version **46** |
| 路由 127429 `operations-workflow-action-reverify` | 存在、ACTIVE、version 2；header 6 / 6（127613—127618）、guard 6 / 6（127869—127874）、query_param 0 |
| 块 127 计数 | console_resource 10、gateway_route 32、route_header 136、guard_rule 192、query_param 30 |
| `t_workflow_action_run` | **只有 VERIFIED 4 行**（UNVERIFIED 0、PENDING 0、CANCELLED 0），无未知状态 → 10 的期望 `completed=4`、`pending=0` |

`sql/99-postcheck.sql` 也在 mor-test 只读执行过一次确认语法（发布前期望全部 ok=0，除 query_param 30）。

### 3.2 只读冒烟（发布前）

`python3 -I acceptance/wfux_smoke.py --before`：15/15（登录、4 个旧工作流菜单且无「我发起的」、8 个工作流 GET、数据查询 / 存储实例 / 检查策略 / 渠道回归）。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件；六个服务（起点 = 公开桶候选，只换三个镜像，其余三个钉现网） |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 只读预检（备份时再执行一次） |
| `../sql/10-action-run-status.sql` / `../sql/20-registration.sql` | 需求目录下的两份增量 SQL，本包不复制，由 `apply_sql.sh` 直接引用 |
| [sql/90-rollback-status.sql](./sql/90-rollback-status.sql) | 回滚：COMPLETED → VERIFIED 与旧注释（取自 10 文件头回滚段，加守卫与 guard） |
| [sql/91-rollback-registration.sql](./sql/91-rollback-registration.sql) | 回滚：删 127311，按现网逐列值重插 127429 + 6 header + 6 guard（route version 取 3），角色 510 version+1 |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 发布后只读核对（16 项） |
| [scripts/backup.sh](./scripts/backup.sh) / [scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份（`mor_platform` 全库、镜像、JAR、env 键名、precheck）与 SQL 包装；root 口令只经 `MYSQL_PWD` |
| [acceptance/wfux_smoke.py](./acceptance/wfux_smoke.py) | 只读冒烟（改编自 `20261021/…/wf7_smoke.py`：加「我发起的」菜单与检查策略回归；原脚本只调 GET，不调已删除的重新验证路由，仍可用，但它只认 4 个菜单） |
| [acceptance/wfux_mortest.mjs](./acceptance/wfux_mortest.mjs) | 写数据验收（**未运行**），第 5 节第 11 步 |
| `superset-*.txt`、`ops-web-*.txt`、`ops-web-uncommitted-10.sha256` | 第 2 节证据 |

## 5. 执行步骤（阶段 7，主会话执行）

在主工作区根目录：

```
R="$PWD/requirements/20261022/platform-support-工作流体验优化/release"
O="$R/compose-candidate.yaml"; RB="$R/compose-rollback.yaml"
up() { ./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
```

任一步不符合预期就停，按第 6 节回滚。

0. **协调：** 通知会发布 platform / ops-bff / ops-web 的会话（公开桶 code-mor-42、数据查询 code-mor-55、wf-file-web、3DS / BIN code-mor-6b、多语言 code-mor-db）本窗口；期间有人替换了这三个服务中的任何一个，本包作废，在新基线上重建并重做第 2 节。
1. **复核基线：**
   ```
   for s in platform-support-service operations-console-bff operations-console-web card-service client-master-service client-console-bff; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done
   ```
   `.Image` 必须等于 `compose-rollback.yaml`，全部 healthy。
2. **备份：** `B=$("$R/scripts/backup.sh")`；打开 `$B/00-precheck.out`，18 项 ok=1，记录 VERIFIED 行数 **V**、UNVERIFIED 行数 **U**（阶段 6 为 4 / 0，可增长）与角色 510 version（阶段 6 为 46）。
3. **停旧 Platform（停机窗口开始）：** `./test-environment/compose.sh -f "$O" stop platform-support-service`（只停该服务；与 `up` 同一个覆盖文件，`stop` 不改镜像。登录与全部运营接口从此不可用）。
4. **状态迁移：**
   - `"$R/scripts/apply_sql.sh" "$B" status-dry`：期望 `verified_before=V`、`completed=V`、`unverified_before=U`、`pending=U`、`legacy_left_0=0`、`guard=counts ok`（第 1 步注释为 DDL，dry 也会生效，可重复执行、无行为影响）；
   - `"$R/scripts/apply_sql.sh" "$B" status`：同上并输出 `committed`。
5. **起新 Platform：** `up platform-support-service`；核对 healthy、重启 0、`.Image` = `sha256:76587ed4…`，`docker logs --since <第 5 步开始> mor-test-platform-support-service-1 2>&1 | grep -c ' ERROR '` 为 0、无 `snapshot refresh failed`；`docker exec mor-test-platform-support-service-1 printenv | cut -d= -f1 | sort | diff "$B/platform-support-service-env-keys-before.txt" -` 为空。
6. **ops-bff（紧接第 5 步）：** `up operations-console-bff`；核对同上（`.Image` = `sha256:c9a70b8d…`）。说明：BFF 对 Platform 响应严格反序列化，第 5—6 步之间旧 ops-bff 读新 Platform 的动作目录列表（新增 `referencedDefinitionCount`）会失败，窗口约 30—60 秒。
7. **ops-web：** `up operations-console-web`；核对 healthy、重启 0、`.Image` = `sha256:a8bb58d7…`。
8. **注册：**
   - `"$R/scripts/apply_sql.sh" "$B" reg-dry`：期望 `resource_1=1`、`guard_rules_6=6`、`route_headers_6=6`、`route_1=1`、`role_1=1`、`counts ok`；
   - `"$R/scripts/apply_sql.sh" "$B" reg`：同上并输出 `committed`。
9. **postcheck：** `"$R/scripts/apply_sql.sh" "$B" postcheck`：16 项 ok=1（角色 version 期望 = 第 2 步记录值 + 1；阶段 6 记录 46 → 47。若第 2 步记录值不是 46，以记录值 + 1 判断该项）；`COMPLETED` 行数 = V。
10. **等 Gateway Snapshot（约 60 秒）：** `docker logs --since <第 8 步开始> mor-test-api-gateway-1 2>&1 | grep -i snapshot | tail -3` 看到刷新；提醒用户刷新已打开的运营控制台标签页。
11. **冒烟：**
    - 只读：`python3 -I "$R/acceptance/wfux_smoke.py"`，期望 15/15（5 个工作流菜单含「我发起的」）；
    - 写数据验收（用户已授权发布；验收会写数据，见第 7 节风险 3）：`node "$R/acceptance/wfux_mortest.mjs"`，期望全部 PASS：422 `startValues.channelCode` / `output.monthlyVolume`「必填」、收件箱合并「2 个动作」与计数、页面完成表单、409 含模板名称、我发起的菜单与页面、完整详情弹窗、动作目录列表 / 抽屉 / 编辑页、流程定义卡片 / 编辑器、审批人文件卡片 + INLINE 预览 + 查看完整流程、同意 → 复核 → COMPLETED、停用合成审批人；
    - `docker logs --since <第 3 步开始> <容器> 2>&1 | grep ' ERROR '`：platform、ops-bff、api-gateway 为空（ops-bff 客户端断开 ERROR 属已知 BFF 噪声）。
12. **记录：** 填第 8 节，`delivery.md` 补环境操作记录；把新镜像 ID 与源码基线 `be688db7` 通知第 0 步各会话：今后 platform / ops-bff / ops-web 候选须基于 `be688db7` 或之后，ops-web 另须带上数据查询 10 个未提交文件（直到其提交）。

## 6. 回滚

| 情形 | 做法（`$RB` = compose-rollback.yaml；`upr` = `./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180`） |
|---|---|
| 第 4 步失败（未提交） | 无需回滚 SQL（dry / guard 失败时事务随断开回滚；注释 DDL 可留可回：`rollback-status` 的第 1 步）。`upr platform-support-service` 起回旧 Platform |
| 第 5 步新 Platform 起不来（状态已迁移） | `stop platform-support-service` → `apply_sql.sh $B rollback-status-dry`（期望 `completed_before=V`、`verified=V`、`counts ok`）→ `rollback-status` → `upr platform-support-service` |
| 全部回滚（第 8 步之后） | 顺序：① `apply_sql.sh $B rollback-reg-dry` → `rollback-reg`（1 / 1 / 6 / 6 / 1；删「我发起的」、恢复重新验证路由）② `upr operations-console-web` ③ `stop platform-support-service` → `rollback-status-dry` → `rollback-status` → `upr platform-support-service` ④ `upr operations-console-bff`（旧 ops-bff 必须面对旧 Platform；③ 与 ④ 之间新 ops-bff 读旧 Platform 的提交响应会失败，约 30 秒） ⑤ postcheck 不适用，改看 00-precheck 恢复为 ok（角色版本 +2、路由 127429 version 3 除外） |
| 只回滚 ops-web | 先 `rollback-reg`（旧 ops-web 不认识 `OPERATIONS_WORKFLOW_STARTED`），再 `upr operations-console-web`；后端保持新版本即可（旧 ops-web 的「重新验证」会得到 Platform 404） |

注意：
- 状态回滚是有损的：原 UNVERIFIED 迁移后与 PENDING 无法区分，回滚后保持 PENDING（本次 U=0，无影响）；新版本期间完成的动作回滚为 VERIFIED。
- 新旧 Platform 不能读对方的状态码：`rollback-status` 必须在新 Platform 已停、旧 Platform 未起时执行。
- `90` / `91` 未在任何数据库演练（本阶段不执行写 SQL）；dry 模式即演练，先跑 dry。
- 备份 `$B/mor_platform-full.sql` 为最后手段（整库恢复会丢失窗口后的所有 `mor_platform` 写入）。

## 7. 风险

1. **停机窗口：** 第 3—5 步 Platform 不可用（登录与全部运营接口），约 1—2 分钟；第 5—6 步之间动作目录列表与部分工作流接口因严格反序列化短暂失败。
2. **公开桶与本包同改 Platform / ops-bff / ops-web：** 本包基于 `be688db7` 构建，已包含公开桶；2.2 已证明 Java 无回退。若期间有人发布这三个服务，本包作废。
3. **验收写数据：** 动作模板 2、定义 1、实例 1（结束为 COMPLETED）、PDF 1（约 0.6 KB，私有桶）、IAM 合成角色 1 + 账号 1（结束时停用账号，角色保留以便历史显示；如需删除见 pubbucket `stage7_cleanup.py` 做法）。数据前缀 `WF-UX-20261023`，保留作证据；模板 / 定义可在运营页面停用。
4. **ops-web 产物级证明不完整（2.4）：** 源码级证明严格（只多 `f707f2da`），但 dist 因共享块重新切分有约 130 个块哈希与内容变化，无法逐块证明“只有 workflow 块变化”。复现现网逐字节一致证明了构建链与 10 个文件内容可靠。
5. **wf-file-web 会话：** 正在本会话内并行工作；本候选不含 `be688db7` 之后的任何 ops-web 改动（主工作区 `src/workflow` 当前无未提交改动）。若该会话随后要发布 ops-web，须基于本包候选（`be688db7` + 10 个文件）重建，否则会回退本次工作流页面。
6. **数据查询 10 个文件仍未提交：** 本候选与现网一样带着它们（内容与现网逐字节相同），Owner 为 code-mor-55。
7. **role 510 版本：** `20-registration.sql` 递增角色版本使会话重新计算菜单，已登录的管理员会话会刷新菜单（与历次注册相同）。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 06:03 | 基线 + 备份 | 三容器 = 回滚文件镜像（68ba631f / 06d850d1 / f8b5dc32），healthy / 0；备份 `~/Code-MoR-backups/20261023T060329Z-workflow-ux-mor-test`（mor_platform 全库、6 服务 env 键、镜像 / JAR） |
| 06:04 | 停 Platform + 状态迁移 | `stop platform-support-service`；status-dry：verified_before 4、completed 4、unverified 0、`counts ok`；status 提交 `committed` |
| 06:04 | 镜像 | platform `76587ed4…` → ops-bff `c9a70b8d…` → ops-web `a8bb58d7…`，均 healthy / 重启 0 |
| 06:05 | 注册 | reg-dry：1 / 6 / 6 / 1 / 1 `counts ok`；reg `committed`；postcheck 16 项全 ok（遗留状态 0、列与表注释已更新、菜单 127311 ACTIVE、路由 127429 及子行已删、块 127 计数 11 / 31 / 130 / 186 / 30、角色 510 版本 47） |
| 06:07 | 冒烟 | `acceptance/wfux_smoke.py` 15/15（导航含「我发起的」、工作流读接口、数据查询 / 存储 / 渠道回归）；platform / ops-bff / ops-web / api-gateway 自 06:04 起 ERROR 0 |
| 06:07 | 通知 | code-mor-42、code-mor-6b 已通知新基线；未包含 code-mor-42 的 bfb17bc9（ScanPolicyTab 列宽）——其 ops-web 需另行发布并以本包镜像为回滚基线 |
| — | 写数据验收 | `acceptance/wfux_mortest.mjs` 未执行：需在 mor-test 新建 IAM 角色 `QA_WFUX_APPROVER` 与测试账号（审批人与执行人须不同），待用户确认 |

### 8.x 写数据验收（2026-10-23 07:38–07:39Z）

用户同意后运行 `acceptance/wfux_mortest.mjs`，结果 **17/17 PASS**，网络 ≥400 与页面错误均为 0，实例走完两级审批后为 COMPLETED。合成角色 `239291461131124736`（`QA_WFUX_APPROVER`）保留，审批人账号 `239291462418776064` 已停用。造数 ID 见 `acceptance/wfux-mortest-state.json`，截图见 `acceptance/screenshots/wfux-01…10`。
