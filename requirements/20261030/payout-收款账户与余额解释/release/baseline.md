# payout S1 · mor-test 现网基线（阶段 6，W8，2026-10-30 13:20—14:10Z）

> 只读：`docker inspect / exec printenv / sha256sum`、`docker cp` 从容器取出、mor-test MySQL 只读 SELECT（`xxl_job`、`mysql.user`、`SHOW DATABASES`）。未停 / 起容器，未写任何东西。取出物在 `~/Code-MoR-backups/payout-s1-stage6/live/`（0700 / 0600，不入库）。
> 对照构建：`/Users/kenny/Desktop/Workspace/Code-MoR-po-base`（detached `9eab368c`，W8 新建，只用于构建；`-DskipTests clean package` 与 `npm run build`），产物在 `~/Code-MoR-backups/payout-s1-stage6/base/`。

## 1. 镜像与制品

主机 16:59 +04 卡死重启后，现网容器由主会话 `docker start` 原样拉起：StartedAt 都在 13:05—13:12Z，**镜像 ID 与任务卡记录一致**。重启期间依赖未就绪，`unless-stopped` 反复重启（RestartCount account 12、Platform 11、client-bff 26、ops-bff 23、Gateway 19、Vault 14），现在全部 healthy。

| 服务 | 容器镜像 ID | tag | JAR / dist（sha256 前 16 位） | 与 main `9eab368c` 构建比对（jarcmp：去掉 MANIFEST / classpath.idx / layers.idx / pom.properties，paytech-* 内层 JAR 展开逐条目 CRC） | 现网独有 |
|---|---|---|---|---|---|
| account-service | `sha256:8a21dab8e54b…` | tax-20261029a | `67e2ab60ed1cb858` | 555 = 555，0 改动 | **0** |
| platform-support-service | `sha256:af13e77a05c3…` | rq04-20261030b | `9b2a86e18682be60` | 3238 = 3238，0 改动 | **0** |
| client-console-bff | `sha256:8fe7f24c3ef7…` | legal-20261028a（本机 tag 名为 `client-console-bffegal-20261028a`，ID 正确） | `e91a2bc5401fb47d` | 现网 1022 / main 1061：现网独有 0、main 新增 39、改动 32，**全部**在 `paytech-platform-support-api`（联合查询、工作流数据查询）与 `paytech-client-master-api`（REQ-12 入驻表单）两个提供方 JAR 内，提供方均已在现网；client-bff 自身类 0 差异 | **0**（落后 main） |
| operations-console-bff | `sha256:4bd4c3dfb16e…` | rq04-20261030b | `9f660a77af785cbd` | 3061 = 3061，0 改动 | **0** |
| operations-console-web | `sha256:2f7f8780d5ab…` | rq04-20261030b | dist 277 个文件 | 与 `9eab368c` `npm run build` **277 个文件逐字节相同**（[ops-web-dist-live-2f7f8780.sha256](./evidence/ops-web-dist-live-2f7f8780.sha256) = [ops-web-dist-main-9eab368c.sha256](./evidence/ops-web-dist-main-9eab368c.sha256)） | **0** |
| api-gateway | `sha256:d51daee3c4ca…` | rq04-20261030b-gw | `d3dd5aa279ea6672` | 988 = 988，0 改动 | **0**（本需求不发） |
| vault-service | `sha256:17827ac80ac6…` | development（2026-10-18） | — | 本需求只有 `mor_vault` DML（W9），不换镜像 | — |
| payout-service | 无容器 | — | — | 新服务 | — |

明细：[evidence/jar-live-vs-main-9eab368c.txt](./evidence/jar-live-vs-main-9eab368c.txt)。容器内 `sha256sum /app/application.jar` 与取出文件一致（5 个 JAR 前 16 位逐一核对）。

health-probe：现网 5 个 Java 容器的 `/app/health-probe/*` 逐文件哈希与 `9eab368c` 源码 `javac --release 25` 编译结果相同。

## 2. 环境变量（键名；值只在 env_check 中比较，不抄写）

| 服务 | 键数 | 键名 |
|---|---|---|
| account-service | 35 | ACCOUNT_BILLING_SERVICE_URL ACCOUNT_COMPENSATION_INSTANCE_ID ACCOUNT_DB_PASSWORD ACCOUNT_DB_URL ACCOUNT_DB_USERNAME ACCOUNT_RABBITMQ_HOST/PASSWORD/PORT/USERNAME/VHOST ACCOUNT_SNOWFLAKE_REGION_ID ACCOUNT_SNOWFLAKE_WORKER_ID（31）+ 发现 6 键 + SERVER_ADDRESS + XXL 8 键 + 镜像自带（JAVA_HOME JAVA_VERSION LANG LANGUAGE LC_ALL PATH） |
| platform-support-service | 59 | 认证 / HMAC（CLIENT_* / OPERATIONS_*，来自 `private/platform.env`、`client-onboarding.env`）、COMPENSATION_* DATAQUERY_HTTP_CALLERS_0 JAVA_TOOL_OPTIONS NOTIFICATION_ENABLED NOTIFICATION_SES_ENABLED NOTIFICATION_SES_EVENTS_ENABLED PAYTECH_INSTANCE_ID PLATFORM_SUPPORT_DB_* PLATFORM_SUPPORT_FILES_ENABLED **PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL** PLATFORM_SUPPORT_RABBITMQ_* PLATFORM_SUPPORT_REDIS_* SERVER_ADDRESS **SPRING_APPLICATION_JSON**（只有 `webhook.http.callers.acquiring-service` 8 个事件）TENANT_PROJECTION_ENABLED WEBHOOK_ENABLED WEBHOOK_TRUSTED_NETWORKS XXL 8 键 + 发现 + 镜像自带 |
| client-console-bff | 16 | CLIENT_BFF_BIND_ADDRESS CLIENT_CONSOLE_ORIGIN CLIENT_ONBOARDING_ENABLED + 发现 + 镜像自带 |
| operations-console-bff | 21 | OPERATIONS_ACCOUNT_BASE_URL OPERATIONS_BFF_BIND_ADDRESS OPERATIONS_BILLING_BASE_URL OPERATIONS_CONSOLE_ORIGIN OPERATIONS_LEDGER_BASE_URL OPERATIONS_RECONCILIATION_BASE_URL **OPERATIONS_TAX_BASE_URL** OPERATIONS_TEST_HTTP_ENABLED + 发现 + 镜像自带 |
| operations-console-web | 7 | nginx 镜像自带 |
| api-gateway | 29 | （不发，略） |
| vault-service | 20 | VAULT_ADMIN_CALLERS（= operations-console-bff）VAULT_NACOS_ENABLED 等（不发） |

`notification.http.callers` 现网为空（`application.yml` 默认 `{}`，env 与 JSON 均未配置）。

## 3. 覆盖链（历史 `requirements/*/release/compose-candidate*.yaml`，2026-10-20 起）

| 服务 | 只在覆盖文件里出现、base 没有的键 | 本次处理 |
|---|---|---|
| operations-console-bff | `OPERATIONS_TAX_BASE_URL=http://tax-service:18116`（联合查询 / REQ-04 / tax 三份） | 候选与回滚都带上 |
| platform-support-service | `DATAQUERY_HTTP_CALLERS_0`（已进 base）；旧 `FILES_ONBOARDING_*`（已废弃，新 Platform 遇到会拒绝启动，不得带） | 不再需要 |
| client-console-bff | `CLIENT_BFF_BIND_ADDRESS` 等 3 个（与 base 同值） | base 已有 |
| account-service | 无 | — |

渲染比对见 [evidence/env-check.txt](./evidence/env-check.txt)。

## 4. 新服务落位读数

| 项 | 现网读数 | 取值 |
|---|---|---|
| 后端网络 `mor-test-backend` | .2—.6 基础设施、.21—.42 应用、**.43 = screening-mock**（`compose.screening-mock.yaml`） | payout **172.30.73.44**（技术方案写的 .43 已被占用） |
| Snowflake | Region 全部 0；Worker 17—31 已用（commercial 17、tax 18、reconciliation 19、Platform 认证 20、payment 21、card 22、ledger 23、Vault 24、risk 25、channel-connector 26、counterparty 27、acquiring 28、compliance 29、billing 30、account 31；client-master 入驻 ID 也用 26，另一套号段，非本需求） | **Region 0 / Worker 16** |
| 端口 | 18117 业务 / 19117 管理（`127.0.0.1`） | 同 |
| DB | `mor_payout` 不存在、`payout_app` 账号不存在（`tax_app` 存在） | runbook：`prepare.py` 追加 `MOR_PAYOUT_DB_PASSWORD` → `initialize.py --create-service-databases` → W9 DDL |
| XXL | `xxl_job_group` 最大 id 18（tax-service），`xxl_job_info` 最大 id 28；15 个 `compensationEventScan` 参数一致 | 新分组 `payout-service`（预计 19）+ 任务（预计 29），`scripts/xxl_payout.py` |
| Nacos | 运行在 `test` Profile 的服务不读 Nacos 配置（payout 的 `nacos:payout-service.yaml` 只在 `nacos` Profile 生效） | **不需要** `payout-service.yaml`；只做服务注册（namespace `mor-test`、group `DEFAULT_GROUP`、账号 `mor-test-services`） |
| Platform 发现 | acquiring 在 mor-test 以 `lb://platform-support-service/platform-support` 调 Platform（近 6 小时日志无 `No servers available`） | payout 同样经发现调 Platform；account 不注册 Nacos，用 `PAYOUT_ACCOUNT_SERVICE_URL` |
| 内部认证 | Platform `DUAL`；account、两个 BFF、payout 均 `OFF` | 无 Token 登记；Platform 通知按 `X-Caller-Service=payout-service` 与 `notification.http.callers` 放行 |
