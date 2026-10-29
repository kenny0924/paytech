# REQ-05 workflow 按钮发起与数据验证 · mor-test 现网基线（阶段 6，2026-10-29 12:45—13:05 +04 只读读取）

> 读取方式：`docker inspect`、`docker exec … printenv`（只取键名）、`docker exec … sha256sum`、`docker cp` 从容器**取出** JAR / dist、root 只读会话（`SET SESSION TRANSACTION READ ONLY`，`@@transaction_read_only = 1` 已确认）、`mysqldump --single-transaction` 只读导出到本机 `~/Desktop/Workspace/Code-MoR-backups/wfdq-20261029/`（含数据，不入库）。
> 未停 / 起 / 重启任何容器，未写任何库、XXL、MQ、Platform 数据，未登录运营 API / XXL Admin，未发布语言包，未 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 容器启动（UTC） | JAR / dist | 来源 |
|---|---|---|---|---|
| platform-support-service | `sha256:1e54f3498feeaf7831c4fe41247cabda8ce1982d9e872bdc716d49c84f37256d` | 10-28 04:06 | JAR `e8eedf4d9f548fd2`（容器内 `sha256sum` 与取出文件一致） | 参考数据客户端组件 refclient-20261028b（`e2185c1b`）；REQ-01 未换 |
| operations-console-bff | `sha256:eca2df8231f999f3f977a830ad8baa758449aa8bceee12234da28d77ae3762b8` | 10-28 20:10 | JAR `2534a1fa42686768` | REQ-01 开通项前置门禁 gate-20261029a（`ba0e15b5`） |
| operations-console-web | `sha256:8aec3df8901f83e7271e2f83f3eac94884bef3acbc83d26bf7c406fd3870c959` | 10-28 20:33 | dist 253 个文件（`evidence/ops-web-dist-live-8aec3df8.sha256`） | REQ-01 gate-20261029b（`ba0e15b5`） |
| card-service | `sha256:32d6f27a49864edc836d669d0e261dbb2a67937afe2deb7216afe71eb9b90718` | 10-28 04:01 | JAR `2725f2a5e9cc3e9e`：含 `paytech-platform-support-reference-data-client`，`paytech-card-core` 内**无** `PlatformCountryRegionClient*` | refclient 发布（`e2185c1b`）——已是新形态，不调用已删除的 `/platform/v1/country-regions`，**本需求不换** |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4` | 10-22 18:04 | — | 3DS 全链路；本需求不换，只验证快照编译 |

与 `CURRENT_IMPLEMENTATION` / 各需求 release 记录一致：Platform 见 `requirements/20261027/platform-support-参考数据客户端组件/release/compose-candidate.platform-support-service.yaml`；ops-bff / ops-web 见 `requirements/20261028/commercial-开通项前置门禁/release/compose-candidate.operations-console-{bff,web}{,.b}.yaml`。

### 1.1 基线证明（候选源码不回退现网）

候选基线 = `8d24df8a`（main HEAD，含 REQ-01 `ba0e15b5` 与 refclient `e2185c1b`），干净 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq-rel`，先不加本需求文件构建：

| 项 | 方法 | 结果 |
|---|---|---|
| ops-web | `npm ci && npm run build`，dist 与 `docker cp mor-test-operations-console-web-1:/usr/share/nginx/operations-console/.` 逐文件 SHA-256 | **253 个文件逐字节相同**（`evidence/ops-web-dist-baseline-compare.txt` 为空） |
| ops-bff | `-am clean package -DskipTests`，`harness/jarcmp.py`（条目 CRC，排除 MANIFEST / maven 元数据 / 索引） | **2815 项，0 差异**（`evidence/jar-baseline-opsbff-live-vs-8d24df8a.txt`） |
| platform | 同上 | 3086 项，**只差 `paytech-messaging.jar!catalog/events.yaml`**（REQ-01 新增 commercial 消费 `compliance.events` 的目录项，`ba0e15b5`；Platform 不消费该事件，只是共享目录随包更新） |

结论：现网三个服务没有 `8d24df8a` 以外的未提交内容；本需求候选 = `8d24df8a` + 本需求文件，不回退任何现网工作。当前 main 无更新提交（`git log 8d24df8a..main` 为空）。

## 2. 容器环境变量键（只记键名）

| 服务 | 键 |
|---|---|
| platform-support-service（59） | 全部来自主 compose 与 `env_file`（`private/platform.env`、`private/client-onboarding.env`）；含 `XXL_JOB_{ADMIN_ACCESSTOKEN,ADMIN_ADDRESSES,EXECUTOR_APPNAME,EXECUTOR_ENABLED,EXECUTOR_EXCLUDEDPACKAGES,EXECUTOR_IP,EXECUTOR_LOGPATH,EXECUTOR_PORT}`、`OPERATIONS_AUTHENTICATION_{ENABLED,WORKER_ID}`（`ENABLED=true`，workflow Processor 与新 JobHandler 的条件）、`DATAQUERY_HTTP_CALLERS_0`、`PLATFORM_SUPPORT_RABBITMQ_*`、`FILES_ONBOARDING_*`、`TZ=UTC`。refclient 覆盖不带 environment |
| operations-console-bff（20） | `OPERATIONS_{ACCOUNT,BILLING,LEDGER,RECONCILIATION}_BASE_URL` `OPERATIONS_BFF_BIND_ADDRESS` `OPERATIONS_CONSOLE_ORIGIN` `OPERATIONS_TEST_HTTP_ENABLED` `NACOS_*` `SPRING_CLOUD_NACOS_DISCOVERY_*` `SPRING_PROFILES_ACTIVE` `TZ` + 镜像自带 |
| operations-console-web（7） | 仅 nginx 镜像自带，无应用 env |

`scripts/env_check.sh` 渲染本目录 6 个覆盖文件，与运行容器 **逐键逐值相同**（Platform 59、ops-bff 20、ops-web 7；`evidence/env-check-20261029.txt`）。本需求不新增 env：`workflow.data-query.*` 用 `application.yml` 默认值（超时 5 秒、锁租约 30 秒、每分片每轮 100、最小间隔 4 分钟、每轮 240 秒）。

## 3. mor_platform 工作流（只读，`precheck/mor_platform-precheck-20261029.out`）

| 项 | 读数 |
|---|---|
| 实例 | COMPLETED 4 / RUNNING 3 / TERMINATED 2，全部带 business_key；**同一定义下重复的 RUNNING business_key 0 行** → 前滚回填 3 行槽位 |
| 新列 / 索引 | `active_slot` 与 5 个检查列、`uk_workflow_inst_active` / `idx_workflow_action_check` 均不存在 |
| DATA_QUERY | 动作模板 0、动作运行 0（动作模板只有 FILE_UPLOAD 4、FORM 2、MANUAL_CONFIRM 2、PAGE_TASK 2）→ 不需要阶段 5 那种旧形状模板处理 |

## 4. mor_platform 注册与 Gateway（只读）

| 项 | 读数 |
|---|---|
| 注册块 | 现网最大块 **141**（REQ-01，路由最大 `900000000000141403`）；块 **142** 区间路由 / header / query / guard **均 0**；3 个新 route_code 与 (方法, 路径) **均未占用** |
| 路由计数 | ACTIVE **616**、DISABLED 6（离线编译：loaded 622、effective 616） |
| 依赖 | 权限 `900000000000127103 WORKFLOW_ACTION_TEMPLATE_MANAGE`、`900000000000127105 WORKFLOW_TASK_HANDLE`（另 `127104 WORKFLOW_INSTANCE_START`）ACTIVE；target `900000000000000001`（operations-console-bff）ACTIVE |

## 5. XXL-Job（只读 SQL，未登录 Admin）

| 项 | 读数 |
|---|---|
| Platform 执行器 | 分组 id **2** `platform-support-service`，`address_list = http://172.30.73.21:9999/`，`xxl_job_registry` 心跳 08:53:00Z（在线）；容器 `XXL_JOB_EXECUTOR_ENABLED=true`、`TZ=UTC` |
| 现有任务 | 分组 2 只有任务 2 `compensationEventScan`（每 30 秒，SHARDING_BROADCAST，启用）；`workflowDataQueryRecheck` 0 行；`xxl_job_info` 最大 id 26（REQ-01 的 25 / 26） |
| Admin | 运行中、healthy，但累计重启 **158** 次（REQ-01 阶段 6 时 127 次；最近一次 08:58Z）；登记前须确认可登录 |

## 6. data-query（只读）

| 项 | 读数 |
|---|---|
| 数据源 | `MOR_COMMERCIAL_REPLICA`（id `239060838932103168`）：`mysql:3306` / `mor_commercial` / 账号 `data_query_demo_reader`，ACTIVE，最近检查 PASSED（10-22）；账号权限 `GRANT SELECT ON mor_commercial.*`（库级只读，覆盖三条模板用到的 `t_agreement`、`t_commercial_term_plan`、`t_commercial_term_version`、`t_agreement_capability`、`t_product_capability`）。另有 6 个演示数据源 |
| 查询模板 | 7 条 DEMO_*；**无 GOLIVE_\*** |
| 块 131 后台 | 18 条路由（131401—131418）全部 **ACTIVE**；平台管理员角色 `900000000000000510` 的 DATA_QUERY_READ / DATA_SOURCE_MANAGE / DATA_QUERY_TEMPLATE_MANAGE / DATA_QUERY_EXECUTE 均 ACTIVE → mor-test **不需要**阶段 5 那种本机启用，可直接经运营 API 登记 |
| 模板推演 | 三条 GOLIVE_* 正文以只读会话、字面量参数在 mor-test `mor_commercial` 逐 Client 执行（`precheck/golive-templates-mortest-20261029.out`），见 runbook 第 6 节验收数据 |

## 7. 语言包（只读，`precheck/i18n-plan-dryrun-accept-deprecations.out`）

operations-console 当前 **#20**（REQ-01）；client-console #7；26 个命名空间、10 309 个 ACTIVE key。计划 #21：新增 123、英文基准变更 4、废弃 13，**全部在 workflow 命名空间**；zh-CN 只导入 workflow 127 条；其余 10 305 条 zh-CN 保持线上；不新建命名空间；`zhChangedNotImported` 0。key 清单 `i18n-bundles/workflow-key-changes.tsv`。
