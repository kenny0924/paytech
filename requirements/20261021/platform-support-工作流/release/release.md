# platform-support 工作流 · mor-test 发布包

阶段 6，2026-10-22，核对时间 11:55–12:20Z。本阶段做了以下工作：
- 只读核对；
- 在干净 worktree 构建镜像；
- 用本机临时库演练 SQL；
- 准备发布包。

**没有改 mor-test 的任何容器、数据库、RabbitMQ、XXL 或 compose 主文件，也没有调用任何写接口（包括登录）。** 阶段 7 由主会话按第 5 节执行，结果写第 8 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：**
- **镜像：** mor-test 的 platform-support、operations-console-bff、operations-console-web 三个容器换成新镜像。gateway、card、channel-connector 及其余容器不动。
- **环境变量：** Platform 只新增工作流 RabbitMQ 发布端 5 个键，其余逐键不变。
- **数据库（只有 `mor_platform`）：**
  - 建 10 张工作流表，改两列注释；
  - 写入块 127 共 416 行，默认休眠；
  - 激活：32 / 10 / 7 / 1。
- **XXL：** 不改。工作流的两类补偿任务共用现有扫描任务。

## 1. 基线与源码

| 项 | 内容 |
|---|---|
| 当前运行（= 回滚目标） | 三个容器都在 channel-connector 第二段最终版上，healthy，重启 0：<br>· platform `sha256:d50f28864d6c153bf0f77df6dd3df43b442b26852cd988e7eca3ccffc43f5671`（ccu2-20261022，06:49Z 起，JAR `b780c9686fe5`）；<br>· ops-bff `sha256:0abb662a764aa2af09f7dec546396b616f81fe7d8b56ebacc5434e3aa5a6e65f`（06:49Z）；<br>· ops-web `sha256:9eaa85211107a08bc1f4825517ec167444643e8f923363627d344978599cd9bc`（ccu2-20261022b，07:11Z）。<br>以上与第二段记录一致，第二段之后没有其他会话发布过这三个服务。<br>不动的容器：card `542a16f0…`、channel-connector `798940ea…`、api-gateway。 |
| 现网源码 = `edabfb46` | · **ops-web：** 把 `edabfb46` 的 `operations-console-web` 导出后构建，dist 与现网镜像 172 个文件**逐字节一致**。<br>· **Java：** 现网 JAR 与候选 JAR 逐条目比较，没有删除项；新增与改动只有工作流内容（见下）。<br>· **`4b5426e5 → edabfb46` 的路径差异：** paytech 无改动；ops-web 只有 D-3 的 3 个渠道页，`a5479950` 已包含，现网已带上。<br>结论：现网三件 ⊂ `1d39cd94`。线上没有 `1d39cd94` 以外的内容。 |
| 发布源码 | main `1d39cd94`（父 `edabfb46`，未推送）。它包含：<br>· 数据查询 `9a209e6f` / `4b5426e5`；<br>· 文件存储第一段 `346a1822`；<br>· 渠道第二段 `654cb14f` / `a5479950`；<br>· MPGS `edabfb46`。<br>干净 worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-wf-rel`（detached `1d39cd94`，`git status` 干净） |
| 构建 | **Java：** `./mvnw -B -o -T 4 -pl platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package -DskipTests`（JDK 25），成功，**没有 install**。<br>镜像用 `test-environment/java/Dockerfile`；`health-probe/` 从运行容器读出（两个服务相同）。<br>**Web：** 借用主工作区 `node_modules`（`package-lock.json` 与 `1d39cd94` 相同，用符号链接，用完已删）。`npm test` 1325/1325 通过，`npm run build` 成功，再 `docker build`（`operations-console-web/Dockerfile`）。<br>`:development` tag 和 `test-environment/artifacts` 都没有改。 |
| 镜像核对 | · 镜像内 `/app/application.jar` 的 SHA-256 与构建产物相同；<br>· Web 镜像内 dist 与构建产物逐字节相同，`nginx.conf` 与现网相同；<br>· 三个镜像的 Entrypoint、Cmd、User、WorkingDir、Env 与对应运行镜像相同。 |

| 服务 | 候选（tag `wf-20261022a`） | 与现网的差异 |
|---|---|---|
| platform-support-service | `sha256:b9301714dfda42217fda8d2e5e80e45df02ec89f4feb2a4f19d187ae48a63b40`（JAR `53c0903f2d0ab3c9`） | 现网 2505 项，候选 2802 项。<br>**新增 297 项：**<br>· `paytech-platform-support-workflow` 229 项（含 11 个 Mapper XML）；<br>· api 47 项；<br>· access-control 9 项（选人 `OperationsPersonnel*`）；<br>· core 6 项（附件 #26–#28 Processor）；<br>· app 5 项（4 个 Controller 与 Converter）；<br>· `spring-boot-amqp-4.0.8.jar`。<br>**改动 8 项：**<br>· `application.yml`；<br>· `PersistenceConfiguration`；<br>· messaging 的 `events.yaml` 与 `YamlEventCatalog`（服务名去掉 workflow-approval）；<br>· `OperationsManagementPolicyService(Impl)`；<br>· `console-resource-capabilities.json`：51 → 55，只多 4 个 `OPERATIONS_WORKFLOW_*`，其余逐项相同。<br>**删除 0 项。** |
| operations-console-bff | `sha256:f1fbcea1d4e5106f2973bfc0dba31ed67dda32dd035556ca7db3582c295297c0`（JAR `463a0efbf5710daa`） | **新增 69 项：** 工作流 4 个 Controller、`PlatformWorkflowClient(Impl)`、`WorkflowModels`，以及 api 47 项。<br>**改动 5 项：**<br>· `application.yml`：metadata-only 前缀 `/workflow-admin`、`/workflow`；<br>· `ConfigurationManagementRequestConfiguration`，含 1 个内部类；<br>· `PlatformClientConfiguration`，含 1 个内部类。<br>**删除 0 项。** |
| operations-console-web | `sha256:c7646648b9d1fe1ebb53c92b5efa1f7f61361808e1d23546794b2207b46e2693` | 现网 172 个文件，候选 187 个。<br>同名文件 72 个，其中 71 个逐字节相同，另 1 个是 `index.html`。其余文件因 Vite 内容哈希级联而改名。<br>新增一级菜单「工作流」，含 4 页（`WorkflowParts-*.js`）。 |

## 2. 环境变量

只列键名，不列值。比对方法：用 `compose.sh -f <覆盖文件> config` 渲染，与运行容器的 `Config.Env` 逐键、逐值比较（镜像自带的 JAVA_HOME、PATH、LANG 等除外）。

| 服务 | 候选 | 回滚 |
|---|---|---|
| platform-support-service | 渲染 52 个键 = 现网 47 个 + `PLATFORM_SUPPORT_RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST`。<br>没有缺失键，也没有值不同的键。 | 47 个键，与现网完全相同 |
| operations-console-bff | 14 个键，与现网相同 | 相同 |
| operations-console-web | 没有 compose 环境变量，与现网相同 | 相同 |

- **RabbitMQ：** 引用方式与主 `compose.applications.yaml` 相同：
  - host `rabbitmq`，端口 5672；
  - 用户名和口令取 `${MOR_RABBITMQ_USER}` / `${MOR_RABBITMQ_PASSWORD}`；
  - vhost `/mor-test`。

  第二段候选把这 5 个键置空（`!reset null`）。本包改为写实值，回滚文件仍置空。
- **沿用第二段（逐键核对相同）：**
  - `DATAQUERY_HTTP_CALLERS_0=operations-console-bff`；
  - `PLATFORM_SUPPORT_FILES_ENABLED=true`、`FILES_ONBOARDING_ENABLED=true`；
  - `COMPENSATION_ENABLED=true`、`COMPENSATION_INSTANCE_ID`、`OPERATIONS_AUTHENTICATION_ENABLED=true`、`NOTIFICATION_*`、`XXL_JOB_*`、`WEBHOOK_*` 等。
- **旧存储键：** 8 个 `FILES_ONBOARDING_STORAGE_*`、`FILES_ONBOARDING_MINIO_*`、`FILES_ONBOARDING_SCAN_POLICY_CODE` 继续置空。主 compose 中也已没有这些键。
- **补偿开关：** 新 `application.yml` 写的是 `compensation.enabled: ${PLATFORM_SUPPORT_COMPENSATION_ENABLED:${PLATFORM_SUPPORT_FILES_ENABLED:${operations.authentication.enabled}}}`。现网另有 `COMPENSATION_ENABLED=true`，属于环境变量来源，优先级高于 yml，因此结果是 true，不受影响。
- **RabbitMQ 只读核对（12:0xZ）：**
  - vhost `/mor-test` 存在，应用账号在其上的权限为 configure / write / read 全部 `.*`；
  - 现有交换机：account / billing / commercial / compliance / payment / reconciliation `.events`，**没有 `platform-support.events`**。

  paytech-messaging 在创建发布端 Bean 时会执行 `declarer.declareProducer(...)`，所以**启动时就连接 Broker 并声明 `platform-support.events`**（Durable Fanout），不会等到首次发布。这有两个后果：
  - Broker 不可达或凭据错误时，Platform 启动失败；
  - rabbit 健康检查已关闭，启动成功后 Broker 断开不影响 readiness。
- **主 compose 已有的缺口（不属于本次，不处理）：** 主 `compose.applications.yaml` 没有 `DATAQUERY_HTTP_CALLERS_0`，现网的这个值来自数据查询的覆盖文件。以后有人用主 compose 重建 Platform，会丢失数据查询的 BFF 调用方。需由数据查询会话补进主 compose。

## 3. 只读核对（阶段 6 已做）

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22 12:0xZ 执行，全部 ok=1，输出见 [`sql/00-precheck-20261022.out`](./sql/00-precheck-20261022.out)。

| 核对项 | 结果 |
|---|---|
| `t_workflow_%` 表 | 0（`mor_platform` 共 53 张表） |
| 块 127 ID 段 900000000000127001—127999 | 10 张注册表全部为空 |
| 唯一键冲突 | 以下都不存在：`WORKFLOW_*` 权限码、`OPERATIONS_WORKFLOW*` 资源码、`operations-workflow-*` 路由码、`/api/operations/workflow*` 路径、模板码 `WORKFLOW_NOTIFICATION` |
| PLATFORM_ADMIN `900000000000000510` | ACTIVE，**version 43**（激活后应为 44） |
| Gateway 目标 `900000000000000001` | operations-console-bff / OPERATIONS / ACTIVE |
| SES `900000000000070001` | `SES_PRIMARY`，AMAZON_SES，ACTIVE，发件地址与密钥已配置 |
| 工作流附件前置 | 扫描策略 `CLIENT_ONBOARDING_FORMAT` 为 ACTIVE；存储默认路由 `*` 存在（文件存储第一段已登记） |
| 已用路由块号最大值 | 131 |
| XXL | `platform-support-service` 有一个任务 `compensationEventScan`（id 2，`*/30 * * * * ?`，参数为空，启用中）。参数为空即扫描全部事件类型，所以 `WORKFLOW_NOTIFICATION`、`WORKFLOW_CALLBACK_EVENT` 不需要新任务。执行器心跳 `http://172.30.73.21:9999/` 正常 |

**SQL 演练：** 在本机 `mor-local-mysql-1` 建临时库 `wf_rel_check`，用 mor-test `mor_platform` 的结构（`--no-data`）加 PLATFORM_ADMIN 一行，依次执行：
1. 01 建表、02 改注释、10 注册块 127：都成功；
2. 激活：32 / 10 / 7 / 1，guard 为 `counts ok`，角色版本 43 → 44；
3. 99 postcheck：全部 ok=1；
4. 90 停用：数目符合；
5. 91 删除注册行：192 / 29 / 136 / 32 / 10 / 7 / 7 / 1 / 1 / 1，合计 416；
6. 92 删表：成功。

临时库已删除。另外在 mor-test 上用纯 SELECT 验证了 guard 的行为：数目不符时报 ERROR 1242，客户端随即停止。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 固定 sha256 的候选 / 回滚覆盖文件，只含三个服务；渲染和逐键核对见第 2 节 |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 执行前只读核对 |
| [sql/01-workflow-tables.sql](./sql/01-workflow-tables.sql) | `changes/2026-10-22-workflow.sql` 的逐字副本，建 10 张空表 |
| [sql/02-workflow-comment.sql](./sql/02-workflow-comment.sql) | `changes/2026-10-22-workflow-comment.sql` 的逐字副本，改两列注释 |
| [sql/10-registration-block127.sql](./sql/10-registration-block127.sql) | 初始化 DML「Operations workflow console」块（`1d39cd94`）的逐字副本，416 行，自带事务 |
| [test-activate.sql](./test-activate.sql) | 激活 32 / 10 / 7 / 1。文件内不含 COMMIT，末尾有 guard |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 激活后只读核对 |
| [sql/90-rollback-deactivate.sql](./sql/90-rollback-deactivate.sql) | 停用 32 / 10 / 7 / 1 |
| [sql/91-rollback-remove-block127.sql](./sql/91-rollback-remove-block127.sql) | 可选：删除块 127 的 416 行 |
| [sql/92-rollback-drop-workflow-tables.sql](./sql/92-rollback-drop-workflow-tables.sql) | 可选：删除 10 张表，不可回滚 |
| [scripts/backup.sh](./scripts/backup.sh) | 备份 `mor_platform` 整库，记录镜像 ID、JAR SHA-256、Platform 环境变量键名和 precheck 输出，写入 `~/Code-MoR-backups/<UTC>-workflow-mor-test/` |
| [scripts/apply_sql.sh](./scripts/apply_sql.sh) | SQL 执行包装。root 口令只经 `MYSQL_PWD` 传入，不回显。<br>`activate` / `deactivate` / `remove-block127` 会在文件后追加 COMMIT；带 `-dry` 的版本不追加，断开即回滚 |
| [acceptance/wf7_smoke.py](./acceptance/wf7_smoke.py) | 只读冒烟：<br>· 登录；<br>· 导航含「工作流」及 4 个菜单；<br>· 8 个工作流 GET 返回 200；<br>· 数据查询、存储、渠道各 1 个 GET 回归。<br>`--before` 用于发布前对照，此时工作流路由应返回 404 |

## 5. 执行步骤（阶段 7，主会话执行）

在主工作区根目录执行（shell 为 zsh，所以 up 参数写全，不放进变量），设置：
- `R="$PWD/requirements/20261021/platform-support-工作流/release"`
- `O="$R/compose-candidate.yaml"`
- `RB="$R/compose-rollback.yaml"`

任一步不符合预期就停，并按第 6 节回滚。

0. **协调：** 与可能发布同三件的会话约定窗口：
   - 文件存储第二段（worktree `Code-MoR-fs2-*`）；
   - card BIN（`Code-MoR-bin-plat` / `bin-ops`）；
   - 3DS（`Code-MoR-3ds*`）；
   - 渠道 / CYBS 会话；
   - wf-file-web。

   **失效条件：** 期间有人先替换了这三个服务中的任何一个，本包作废，须在新基线上重建，并重做第 1 节的证明。
1. **复核基线：**

   ```
   for s in platform-support-service operations-console-bff operations-console-web; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done
   ```

   `.Image` 必须等于 `compose-rollback.yaml`（`d50f2886…` / `0abb662a…` / `9eaa8521…`），全部 healthy。
2. **备份与对照：**
   - `B=$("$R/scripts/backup.sh")`，然后打开 `$B/00-precheck.out`，确认与第 3 节一致：全部 ok=1，角色版本 43；
   - `python3 -I "$R/acceptance/wf7_smoke.py" --before`，期望全部 PASS：没有工作流菜单，工作流路由返回 404，回归 3 项返回 200。
3. **DDL（对现网无影响，旧 Platform 不读这些表）：**
   - `"$R/scripts/apply_sql.sh" "$B" ddl`
   - `"$R/scripts/apply_sql.sh" "$B" ddl-comment`
4. **块 127 休眠注册：** `"$R/scripts/apply_sql.sh" "$B" register`，416 行。
   - 权限 7 行为 ACTIVE；授权 REVOKED；资源和路由 DISABLED；通知模板、内容、路由 ACTIVE。
   - 对现网无影响：工作流 NOTIFY 规则只有经休眠路由才会触发。
5. **ops-bff → ops-web：**
   - `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-bff`
   - `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 operations-console-web`

   每次都核对：healthy、重启 0、`.Image` 等于候选、`docker logs --since <开始时间> mor-test-<svc>-1 2>&1 | grep -c ' ERROR '` 为 0。工作流菜单此时还不可见。
6. **Platform：** `./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service`
   - 核对 healthy、重启 0、`.Image` = `b9301714…`，启动日志 ERROR 为 0。
   - `docker exec mor-test-platform-support-service-1 printenv | cut -d= -f1 | sort | diff "$B/platform-env-keys-before.txt" -`：只应多出 5 个 `PLATFORM_SUPPORT_RABBITMQ_*`。
   - RabbitMQ：
     - `docker exec mor-test-rabbitmq-1 rabbitmqctl list_exchanges -q -p /mor-test name type durable | grep platform-support`，应为 `platform-support.events fanout true`；
     - `docker exec mor-test-rabbitmq-1 rabbitmqctl list_connections -q peer_host vhost state | grep 172.30.73.21`，应为 `/mor-test running`。
   - XXL：约 30 秒内 `xxl_job_registry` 中 `platform-support-service` 的心跳刷新。
7. **激活：**
   - 先试运行：`"$R/scripts/apply_sql.sh" "$B" activate-dry`，期望 32 / 10 / 7 / 1，`counts ok`，版本 44（断开即回滚）；
   - 再提交：`"$R/scripts/apply_sql.sh" "$B" activate`，期望同上，并输出 `committed`；
   - 核对：`"$R/scripts/apply_sql.sh" "$B" postcheck`，全部 ok=1，PLATFORM_ADMIN 为 44；
   - 等 Gateway Snapshot 刷新，最多 60 秒。
8. **冒烟：** `python3 -I "$R/acceptance/wf7_smoke.py"`，期望全部 PASS：
   - 登录；
   - 导航中有「工作流」和我的待办 / 流程实例 / 流程定义 / 动作目录；
   - 8 个工作流 GET 返回 200；
   - 数据查询、存储、渠道回归返回 200。

   之后：
   - `docker logs --since <第 5 步开始> mor-test-platform-support-service-1 2>&1 | grep ' ERROR '`，应为空；ops-bff 同样检查；
   - 可选浏览器（只读）：管理员打开「工作流」4 页，确认控制台无错误、没有 4xx / 5xx；
   - 提醒其他会话刷新已打开的运营标签页。
9. **可选功能验收（会写 mor-test 业务数据，需用户同意）：** 建一个只有「开始 → 审批 → 结束」的定义，发布并发起，审批通过，确认实例完成。若定义里有回调，再确认 `platform-support.events` 发布成功；当前没有订阅方，按 `ACK_AND_WARN` 只记告警。验收数据保留，流程定义置为 INACTIVE。
10. **记录：**
    - 填第 8 节，并在 `delivery.md` 补环境操作记录；
    - 把新镜像 ID 和源码基线（`1d39cd94`）通知第 0 步的各会话。今后这三个服务的候选必须基于 `1d39cd94` 或其后代；Platform 覆盖文件须保留 RabbitMQ 5 个键（主 compose 已有），并且不再置空。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| 只关入口 | `apply_sql.sh $B deactivate-dry` → `deactivate`，期望 32 / 10 / 7 / 1，角色版本再 +1。工作流表和注册行保留 |
| 镜像 | 1. 先停用入口（上一行）。旧 ops-web 和旧 Platform 没有 `OPERATIONS_WORKFLOW_*` 组件。<br>2. 按 **ops-web → ops-bff → platform** 的顺序：`./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service>`。<br>回滚文件会把 RabbitMQ 键置空，环境与发布前逐键相同。旧 Platform 不读写工作流表 |
| 注册行 | 一般保持休眠。要彻底移除时执行 `remove-block127-dry` → `remove-block127`（共 416 行）。如果工作流已经发过通知，投递记录会引用模板 `900000000000127081`，先核对 |
| 表 | 一般保留。确需删除时，在镜像回滚后执行 `apply_sql.sh $B drop-tables`。该操作不可回滚，可用 `$B/mor_platform-full.sql` 整库恢复 |
| RabbitMQ | `platform-support.events` 交换机保留无害。如需删除：`rabbitmqctl delete_exchange -p /mor-test platform-support.events`（当前没有绑定） |

## 7. 风险与待主会话决定的事项

1. **启动依赖 RabbitMQ：** Broker 不可达或凭据错误时，新 Platform 起不来。第 6 步若失败，先看日志里的 AMQP 错误，然后立即按回滚文件恢复 Platform（ops-bff / ops-web 可以留在新版本，入口尚未激活）。
2. **不可用窗口：** 只有第 6 步替换 Platform 期间，约 30–60 秒。Platform 承载登录、导航和所有运营接口，期间运营台会短暂报错。
3. **并行会话：** 文件存储第二段、BIN、3DS 的 worktree 可能也要发布同一组三件。本包与它们互斥，先发的一方要让另一方在新基线上重建。
4. **功能验收写数据：** 第 9 步会在 mor-test 留下工作流定义和实例。默认不做，等用户决定。
5. **主 compose 缺 `DATAQUERY_HTTP_CALLERS_0`：** 属于既有问题，见第 2 节，建议通知数据查询会话。
6. **构建 worktree：** `Code-MoR-wf-rel` 保留到发布完成，以便复核；用完由主会话删除（`git worktree remove`）。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| | | |

### 8.1 执行记录（2026-10-22，主会话）

- 窗口：fs2（code-mor-38）与 BIN / 3DS（code-mor-6b）均确认近期不发布这三个服务，工作流先发。
- 第 1 步：三个容器镜像与回滚文件一致（platform d50f2886…、ops-bff 0abb662a…、ops-web 9eaa8521…），均 healthy、重启 0。
- 第 2 步：备份 `/Users/kenny/Code-MoR-backups/20261022T121943Z-workflow-mor-test`（mor_platform 整库 7.3 MB、镜像 / JAR / 环境变量键名、precheck）；发布前冒烟 7/7。
- 第 3—4 步：ddl、ddl-comment、register 完成；核对 10 张表、注释 48 KiB、块 127 权限 7、路由 32（全部 DISABLED）、header 136、guard 192。
- 第 5 步：ops-bff → `sha256:f1fbcea1…`、ops-web → `sha256:c7646648…`，均 healthy、重启 0、ERROR 0。
- 第 6 步：platform → `sha256:b9301714…`，healthy、重启 0；环境变量只多 5 个 `PLATFORM_SUPPORT_RABBITMQ_*`、无缺失；`/mor-test` 已有 `platform-support.events fanout durable`；ERROR 0。
- 第 7 步：activate-dry 32 / 10 / 7 / 1、版本 44、counts ok；activate 提交；postcheck 全部 ok。
- 第 8 步：冒烟 14/14（登录、导航含「工作流」4 菜单、8 个工作流 GET 200、数据查询 / 存储 / 渠道回归 200）；platform、ops-bff、gateway 近 10 分钟 ERROR 0。
- 第 9 步（写数据的最小流程）：未做，待用户同意。
- 源码基线：main `1d39cd94`（父 `edabfb46`），构建 worktree `Code-MoR-wf-rel`。

### 8.2 写数据冒烟（2026-10-22 12:24 UTC，用户同意）

- 入口 `http://localhost:28080`（Gateway → ops-bff → platform-support），管理员 o***@example.test；未建账号、未改权限、未执行 SQL（只读查询 `t_workflow_callback`）。脚本 `acceptance/wf8_write_smoke.py`（API）与 `acceptance/wf8_browser.mjs`（浏览器），ID 记录在 `acceptance/wf8-state.json`。
- 新建数据（前缀 `WF-SMOKE-20261022` / `WF_SMOKE_20261022_`）：
  - FORM 动作模板 `239000941964115968`（`WF_SMOKE_20261022_FORM`，字段 summary TEXT、result ENUM OK/NG）；
  - 流程定义 `239000942396129280`（`WF_SMOKE_20261022_FLOW`）v1：启动表单 topic TEXT；`fill` 只挂该 FORM，执行人 admin、无审批人；`approve` 纯审批，审批人 admin；`end`。不配通知与回调规则。同一人限制只针对同一节点，执行与审批分属两个节点，规则允许；
  - 流程实例 `239000944916905984`（“WF-SMOKE-20261022 冒烟实例”）。
- 结果：草稿 0 问题、发布 v1；发起后我的待办出现 `ACTION/fill`；浏览器在我的待办提交表单 HTTP 200 `VERIFIED`；按 A 键后提交决定 HTTP 200；实例 `COMPLETED`，节点 start / fill / approve / end 全部 `PASSED`，审批 `APPROVED`；活动记录 4 条（提交、节点通过 ×2、审批同意），无通知 / 回调；`t_workflow_callback` 无记录（未配规则，未发邮件）。浏览器无 ≥400 响应、无页面错误。
- 截图：`acceptance/screenshots/mortest-01-my-tasks-form.png`、`-02-my-tasks-submitted.png`、`-03-my-tasks-approval.png`、`-04-instance-graph.png`、`-05-instance-activities.png`。
- 日志：12:20 UTC 起 platform、ops-bff、gateway 容器日志 ERROR 0。
- 清理：数据保留作为 mor-test 冒烟证据；如需移除，停用流程定义 `239000942396129280` 与模板 `239000941964115968`（运营页面操作，实例为只读历史）。
