# REQ-01 开通项前置门禁 · mor-test 现网基线（阶段 6，2026-10-28 23:00—23:50 +04 只读读取）

> 读取方式：`docker inspect`、`docker exec … printenv`（只取键名）、`docker cp` 从容器**取出** JAR / dist、root 只读会话（`SET SESSION TRANSACTION READ ONLY`）、`mysqldump --single-transaction`（只读导出到 `~/Desktop/Workspace/Code-MoR-backups/gate-20261029/stage6-readonly-20261028T194134Z/`，不入库）、`rabbitmqctl list_*`。
> 未停 / 起 / 重启任何容器，未写任何库、XXL、MQ、Platform 数据，未发布语言包，未 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 镜像 tag / 构建时间 | 容器启动 | JAR / dist | 来源 |
|---|---|---|---|---|---|
| commercial-service | `sha256:e5c1ee6b0d037c4c899f43bf3583dcfdea8175f408f661d6f51580a36d3dd28c` | `mor-test/commercial-service:relsnap-20261025a`，10-25 11:19Z | 10-25 14:11Z | JAR `1dae75ab534567c3`（容器内 `sha256sum` 与取出文件一致） | 商业关系快照与真实解析接入（`4b661830`，已推送） |
| operations-console-bff | `sha256:769edbcebd219db68eb9ea41514606f966329a16a458e1d62ea937634e8f924b` | 本机 tag 误为 `mor-test/operations-console-bffegal-20261028a:latest`（zsh `:l` 修饰符吞掉了 `:l`），10-28 05:29Z | 10-28 05:42Z | JAR `6045fdabb20b092a` | 国家包法务修订（阶段 6 期间 23:38 提交为 `ac3145f4`，未推送；源码 worktree `Code-MoR-legal-rel` 已移除） |
| operations-console-web | `sha256:45ee11cb4e8c788d557973c40bc5bea9bbc379b610c4aed8e84b9f65d34c81db` | 本机 tag 误为 `mor-test/operations-console-webegal-20261028a:latest`，10-28 05:29Z | 10-28 05:42Z | dist 250 个文件（清单 `evidence/ops-web-dist-live-45ee11cb.sha256`），`nginx.conf` 与仓库一致 | 国家包法务修订 |
| platform-support-service | `sha256:1e54f3498feeaf7831c4fe41247cabda8ce1982d9e872bdc716d49c84f37256d` | `refclient-20261028b`，10-28 03:32Z | 10-28 04:06Z | JAR `e8eedf4d9f548fd2` | 参考数据客户端组件（`e2185c1b`）；**本需求不换** |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4` | tag 误为 `mor-test/api-gatewayard3ds-20261022b:latest`，10-22 17:51Z | 10-22 18:03Z | — | 3DS 全链路；**本需求不换** |

与 `CURRENT_IMPLEMENTATION` / 各需求 release 记录一致：commercial 见 `requirements/20261023/commercial-商业关系快照与真实解析接入/release/compose-candidate.yaml`；ops-bff / ops-web 见 `requirements/20261027/client-master-国家包法务修订/release/compose-candidate.operations-console-{bff,web}.yaml`（其 `memory/legal-pack-revision-delivered` 同）。

### 1.1 基线证明（候选源码不回退现网）

| 项 | 方法 | 结果 |
|---|---|---|
| ops-web | `Code-MoR-gate-rel` = `663e5e4a` + 法务修订 93 个文件，`npm ci && npm run build`，dist 与 `docker cp mor-test-operations-console-web-1:/usr/share/nginx/operations-console/.` 逐文件 SHA-256 比较 | **250 个文件逐字节相同**（`evidence/ops-web-dist-baseline-compare.txt`） |
| ops-bff | 同一树 `-am package`，`jarcmp.py`（条目 CRC，排除 MANIFEST / maven 元数据 / 索引）对现网 JAR | **2800 项，0 差异**（`evidence/jar-baseline-opsbff-live-vs-663e5e4a+legal.txt`） |
| platform（编译器来源） | 同上 | **3086 项，0 差异**（`evidence/jar-baseline-platform-live-vs-663e5e4a.txt`） |
| commercial | 同上 | 1210 → 1271：只差 Owner API 与 Foundation：`paytech-client-master-api`（切片 1 `9f506f0f`、法务修订）、`paytech-compliance-api`（切片 1）、`paytech-messaging!catalog/events.yaml`；**commercial 自身 core / app 0 差异**（`evidence/jar-baseline-commercial-live-vs-663e5e4a+legal.txt`）。这些差异全部是已上线服务的接口模型，随本次 commercial 镜像一起生效 |

## 2. 容器环境变量键（只记键名）

| 服务 | 键（`printenv`，不含值） |
|---|---|
| commercial-service（37） | `COMMERCIAL_COMPENSATION_INSTANCE_ID` `COMMERCIAL_RABBITMQ_{HOST,PASSWORD,PORT,USERNAME,VHOST}` `COMMERCIAL_REDIS_{HOST,PASSWORD,PORT}` `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED` `COMMERCIAL_SNOWFLAKE_{REGION_ID,WORKER_ID}` `COMMERCIAL_XXL_JOB_ENABLED` `NACOS_{NAMESPACE,SERVER_ADDR}` `SPRING_CLOUD_NACOS_DISCOVERY_{GROUP,IP,PASSWORD,USERNAME}` `SPRING_DATASOURCE_{PASSWORD,URL,USERNAME}` `SPRING_PROFILES_ACTIVE` `TZ` `XXL_JOB_ADMIN_{ACCESSTOKEN,ADDRESSES}` `XXL_JOB_EXECUTOR_{APPNAME,EXCLUDEDPACKAGES,IP,LOGPATH,PORT}` + 镜像自带 `HOME HOSTNAME JAVA_HOME JAVA_VERSION LANG LANGUAGE LC_ALL PATH` |
| operations-console-bff（20） | `OPERATIONS_{ACCOUNT,BILLING,LEDGER,RECONCILIATION}_BASE_URL` `OPERATIONS_BFF_BIND_ADDRESS` `OPERATIONS_CONSOLE_ORIGIN` `OPERATIONS_TEST_HTTP_ENABLED` `NACOS_*` `SPRING_CLOUD_NACOS_DISCOVERY_{GROUP,PASSWORD,USERNAME}` `SPRING_PROFILES_ACTIVE` `TZ` + 镜像自带 |
| operations-console-web（7） | 仅 nginx 镜像自带（`NGINX_VERSION` 等），无应用 env |
| platform-support-service | 59 键（容器配置 env；含 `DATAQUERY_HTTP_CALLERS_0`、`XXL_JOB_*`、`PLATFORM_SUPPORT_RABBITMQ_*`），本需求不换镜像 |
| api-gateway | 30 键（容器配置 env，不含运行时 HOSTNAME / HOME），本需求不换镜像 |

历史覆盖文件中这三个服务的 `environment` 键：commercial 的全部应用键只在 commercial 历史覆盖（`20261019`→`20261020` 两份→`20261023` relsnap 候选，后者多 `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED`；`compose-ac11-window.yaml` 只含该开关），主 compose 的 commercial 段是旧形状；ops-bff 的 7 个应用键与法务修订候选相同（含 `OPERATIONS_RECONCILIATION_BASE_URL`）。`scripts/env_check.sh` 渲染本目录 6 个覆盖文件与运行容器**逐键逐值相同**（commercial 37、ops-bff 20、ops-web 7）。

## 3. mor_commercial（只读）

| 项 | 读数 |
|---|---|
| `t_agreement_capability` | **15 行，全部 `status=ENABLED`**，均属 CLIENT / ACTIVE 合同；`execution_status` 列不存在 |
| 索引 | `PRIMARY`、`uk_agreement_capability`、`idx_agreement_capability_lookup(entity_no, capability_code, status)`、**`idx_agreement_capability_code(capability_code)`**（仓库 DDL 也有，迁移脚本不动它） |
| 表 / 列注释 | 与 `mor_commercial-rollback.sql` 恢复的注释逐字相同 |
| 消费幂等表 | `t_consumed_event` / `t_consumed_aggregate_version` 不存在（迁移脚本 `CREATE TABLE IF NOT EXISTS`） |
| 账号 | `commercial_app`：`SELECT, INSERT, UPDATE, DELETE ON mor_commercial.*`（新表自动覆盖，无需新授权）；迁移用 root |
| ACTIVE CLIENT 合同 | 7 份（CL-F1105E、DEMO-CL-001、DEMO-CL-002、DEMO2-CL-101/102/103/901），均为 AE001 / MOR；Client 与 AE001 在 `mor_compliance.t_role_qualification` 均为 APPROVED |
| CLIENT_PRICING 版本 | **有 ACTIVE 版本**：DEMO-CL-001（v1 ACTIVE、v2 SCHEDULED）、DEMO2-CL-101（v2）、DEMO2-CL-103（v1）；**没有 ACTIVE 版本**：CL-F1105E（无方案）、DEMO-CL-002（v1 PENDING_APPROVAL）、DEMO2-CL-102（v1 APPROVED）、DEMO2-CL-901（v1—v4 CLIENT_SIGNED）——见 stage6-report 风险 R1 |

开通项明细（合同 → 开通项）：CL-F1105E → CARD；DEMO-CL-001 → APPLE_PAY、CARD、PAYOUT_LOCAL；DEMO-CL-002 → CARD、GOOGLE_PAY；DEMO2-CL-101 → CARD、TOKEN_STORAGE；DEMO2-CL-102 → APPLE_PAY、CARD；DEMO2-CL-103 → CARD、GOOGLE_PAY、STC_PAY；DEMO2-CL-901 → CARD、TOKEN_STORAGE。

## 4. MQ / XXL / 开关

| 项 | 读数 |
|---|---|
| `compliance.events` | 存在（fanout，durable）；当前只绑定 `client-master.compliance.hash`（x-consistent-hash） → `client-master.compliance.p0..p3`（quorum，各 1 消费者）+ `.dlx` / `.dlq` |
| `commercial.events` | 存在（fanout），commercial 发布端 |
| commercial 消费配置 | 现网**没有**（无 `MESSAGING_CONSUMER_*` env，旧 `application.yml` 只有 producer）；`commercial.compliance.*` 队列 / 交换机 0 个 |
| RabbitMQ 权限 | vhost `/mor-test` 只有用户 `mor_test`（administrator），`configure / write / read = .* .* .*`；commercial 用的就是该账号（`${MOR_RABBITMQ_USER}`），**可以声明** `commercial.compliance.hash / p0..p3 / dlx / dlq` 并绑定 `compliance.events`；vhost 无 policy |
| commercial XXL 执行器 | `COMMERCIAL_XXL_JOB_ENABLED=true`；分组 id **14** `commercial-service`，地址 `http://172.30.73.34:9999/`（心跳 19:46:30Z）；现有任务 16 `commercialTermVersionActivation`、17 `compensationEventScan`（每 30 秒，SHARDING_BROADCAST，启用）；`capabilityPrerequisiteRecheck` 0 行；`xxl_job_info` 最大 id 24 |
| XXL Admin | 容器 10-28 18:41Z 刚重启，累计重启 **127** 次（不是本卡所为；登记前确认 Admin 可登录） |
| 时区 | XXL Admin 与 commercial 容器 `TZ=UTC`（cron 按 UTC） |
| 关系快照开关 | `COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED=false`；acquiring `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=MOCK`（mor-test 收单实际不调用 commercial 解析） |

## 5. mor_platform（只读）

| 项 | 读数 |
|---|---|
| 注册块 | 现网最大块 **140**（路由 1 条，卡 BIN 号段列表）；139 / 138 / 137 已用；仓库 `platform-support-initial-data.sql`（main 与各 worktree）最大 140；仓库与需求目录无 141 引用 → **本需求用块 141** |
| 路由计数 | ACTIVE 613、DISABLED 6（快照编译：loaded 619、effective 613） |
| 依赖 | 权限 `900000000000095101 COMMERCIAL_CONTRACT_READ`、`900000000000095102 COMMERCIAL_CONTRACT_MANAGE` ACTIVE；target `900000000000000001 operations-console-bff` ACTIVE；同形路由 95405（activate，无 Body POST）/ 95409（capability-update）header 6 / guard 6，属性相同 |
| 新 route code / (方法, 路径) | 0 / 0 |
| 语言包 | operations-console 当前 **#19**（READY，10-28 05:44Z，法务修订）；client-console #7；26 个命名空间、10218 个 ACTIVE key |
| data-query | 数据源 `MOR_COMMERCIAL_REPLICA`（`data_query_demo_reader`，库级 `SELECT mor_commercial.*`，ACTIVE / PASSED）已存在；无 golive 模板；模板编码校验 `[A-Z][A-Z0-9_]{1,63}` |
