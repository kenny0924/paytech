# mor-test 发布基线（只读收集，2026-10-27）

> 收集方式：`docker inspect`、`docker exec <c> printenv`（只列键名；密码 / 密钥类值未记录）、`docker network inspect mor-test-backend`、mor-test MySQL 只读 `SELECT`。未修改任何容器、库或文件。
> 用途：阶段 6 编写本需求 `compose-candidate.yaml` 与回滚文件。**镜像一律按 sha256 固定**（`:development` 标签会被并行会话重建）；候选覆盖必须带上既有覆盖文件引入的 env。

## 1. 相关服务运行现状

| 服务 | 运行镜像 | 容器创建（UTC） | backend IP | 当前覆盖来源（compose.sh 基础三文件之外） |
|---|---|---|---|---|
| client-master-service | `sha256:eeed4e63235de481ee6a02777de25a454197f2993af84fe90f14e0d1ee0df8a8`（relsnap-20261025a） | 2026-10-25 13:41 | 172.30.73.26 | `Code-MoR-relsnap/requirements/20261023/commercial-商业关系快照与真实解析接入/release/compose-candidate.yaml` |
| compliance-service | `sha256:ca1585452f9c60835ecb099db694e36bd5d2fdcfa197016a242d204954ce3545`（relsnap-20261025a） | 2026-10-25 13:39 | 172.30.73.32 | 同上（relsnap） |
| channel-connector-service | `sha256:23679d451fe44dfce08426777b5ec7cbc73a60253c604f149dfc48291c3890aa`（card3ds-20261022） | 2026-10-22 18:02 | 172.30.73.36 | `Code-MoR/requirements/20261022/payment-3DS全链路/release/compose-candidate.yaml` |
| client-console-bff | `sha256:9033b8fa365a60ff0917906e64280e44cfe574ca421bd11179da90c7c03e2d55`（i18n-20261023a） | 2026-10-23 08:35 | 172.30.73.27 | `Code-MoR/requirements/20261022/platform-support-多语言管理/release/compose-candidate.yaml` |
| client-console-web | `sha256:280a2f384b80110c69a09630c3389b921a04591aa4dfd90dfdeba7324aab7520`（i18n-20261023a） | 2026-10-23 08:38 | web 172.25.0.3 | 同上（多语言管理） |
| operations-console-bff | `sha256:feefe73f0aa35e556e5bf1b11da9144ac3d7414285740bf22d9cbe94f086e6af`（relsnap-20261025a） | 2026-10-25 13:44 | 172.30.73.22 | relsnap |
| operations-console-web | `sha256:4f44725eb26f7569b40719f29f48da7cc60f46c3c6bdd4d45ba0c44827662d3a`（relsnap-20261025a） | 2026-10-25 13:45 | web 172.25.0.2 | relsnap |
| platform-support-service | `sha256:4859efd78dd92de7c8f97dca29ef7894e7d2e1115bd72f471960553375075d17`（fileupload-20261024a） | 2026-10-24 08:02 | 172.30.73.21 | `Code-MoR-fileupload/requirements/20261023/operations-文件上传入口/release/compose-candidate.yaml` |
| vault-service | `sha256:17827ac80ac6bfda20570ebc2042a284c2871ffe026fd42fc83af8e2cbd4dd63`（mor-test/vault-service:development） | 2026-10-18 13:38（2026-10-22 重启） | 172.30.73.24 | 无（基础三文件）；镜像无 printenv，env 未读 |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4`（card3ds-20261022b） | 2026-10-22 18:03 | 172.30.73.23 / ingress 172.30.72.3 | 3DS 全链路 |

覆盖来源中两个在**其他 worktree**（`Code-MoR-relsnap`、`Code-MoR-fileupload`），2026-10-27 仍存在。本需求候选文件应把所需 env 抄进本需求目录，不依赖这些 worktree 继续存在。
各覆盖文件的镜像行与运行镜像一致（relsnap 的 4 个、fileupload 的 Platform、3DS 的 channel-connector、多语言的 client-bff / client-web）。

## 2. 运行容器 env 键（候选覆盖需逐键核对）

只列与本切片相关的键；`*PASSWORD*`、`*SECRET*`、`*TOKEN*`、`*KEY*` 等值不记录。非敏感值只在影响决策时注明。

- **client-master**：`CCT_RABBITMQ_{HOST,PORT,USERNAME,PASSWORD,VHOST}`、`COMPENSATION_INSTANCE_ID`（client-master-test）、`MESSAGING_CONSUMER_SERVICE`（client-master）、`MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS`（4）、`NACOS_*`、`ONBOARDING_DRAFT_ENABLED`（true：Vault 客户端随之启用）、`ONBOARDING_ID_DATACENTER`（0）、`ONBOARDING_ID_WORKER`（26）、`ONBOARDING_RECOVERY_ENABLED`、`SPRING_CLOUD_NACOS_DISCOVERY_*`、`SPRING_DATA_REDIS_*`、`SPRING_DATASOURCE_{URL,USERNAME,PASSWORD}`（mor_client_master / client_master_app）、`SPRING_PROFILES_ACTIVE`、`TZ`、`XXL_JOB_*`（appname client-master-service）。
- **compliance**：`COMPENSATION_INSTANCEID`（compliance-test）、`COMPLIANCE_DB_{URL,USERNAME,PASSWORD}`、`COMPLIANCE_RABBITMQ_*`、`COMPLIANCE_SNOWFLAKE_REGION_ID`（0）、`COMPLIANCE_SNOWFLAKE_WORKER_ID`（29）、`MESSAGING_PRODUCER_SEQUENCER_ENABLED`、`MESSAGING_PRODUCER_SERVICE`、`MESSAGING_PUBLISHER_UNROUTABLEPOLICY`（FAIL）、`NACOS_*`、`ONBOARDING_MATERIALS_ENABLED`、`ONBOARDING_REVIEW_ENABLED`、`SERVER_ADDRESS`、`SPRING_CLOUD_NACOS_DISCOVERY_*`、`SPRING_PROFILES_ACTIVE`、`TZ`、`XXL_JOB_*`。
  - **新增需要**（本切片）：出站 channel-connector / client-master 客户端配置（如 `compliance.screening.channel-code`，默认 `S_SCREENING_MOCK` 可不设）；internal-auth 当前未设（默认 OFF），compliance 发 `X-Caller-Service: compliance-service`。具体键名待 compliance agent 定稿。
- **channel-connector**：`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`、`CHANNEL_CONNECTOR_RABBITMQ_*`（3DS 覆盖引入）、`CHANNEL_CONNECTOR_REDIS_*`、`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID`（0）、`CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID`（26）、`MOR_CHANNEL_CONNECTOR_DB_*`、`NACOS_*`、`PAYTECH_INSTANCE_ID`、`SERVER_ADDRESS`、`SPRING_*`、`TZ`、`XXL_JOB_*`。
  - **没有** `CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES / TRUSTEDNETWORKS`（Nacos 默认 https only、无可信网段）；**没有** `CHANNEL_CONNECTOR_INTERNAL_AUTH_MODE`（默认 OFF）。本切片新增两键，见 `release/compose-candidate.channel-connector.draft.yaml`。
- **client-console-bff**：`CLIENT_BFF_BIND_ADDRESS`（172.30.73.27）、`CLIENT_CONSOLE_ORIGIN`、`CLIENT_ONBOARDING_ENABLED`（true）、`NACOS_*`、`SPRING_CLOUD_NACOS_DISCOVERY_*`、`SPRING_PROFILES_ACTIVE`、`TZ`。
- **operations-console-bff**：`NACOS_*`、`OPERATIONS_ACCOUNT_BASE_URL`、`OPERATIONS_BILLING_BASE_URL`、`OPERATIONS_LEDGER_BASE_URL`、`OPERATIONS_RECONCILIATION_BASE_URL`（2026-10-21 漏带致 503 的那个键）、`OPERATIONS_BFF_BIND_ADDRESS`（172.30.73.22）、`OPERATIONS_CONSOLE_ORIGIN`、`OPERATIONS_TEST_HTTP_ENABLED`、`SPRING_CLOUD_NACOS_DISCOVERY_*`、`SPRING_PROFILES_ACTIVE`、`TZ`。
- **platform-support**：`CLIENT_AUTHENTICATION_*`、`CLIENT_LOCAL_*`（密钥）、`COMPENSATION_ENABLED`、`COMPENSATION_INSTANCE_ID`、`DATAQUERY_HTTP_CALLERS_0`（只在覆盖文件中，主 compose 无）、`JAVA_TOOL_OPTIONS`（truststore）、`NOTIFICATION_*`、`OPERATIONS_AUTHENTICATION_ENABLED`、`OPERATIONS_AUTHENTICATION_WORKER_ID`（20）、`OPERATIONS_LOCAL_*`（密钥）、`PAYTECH_INSTANCE_ID`、`PLATFORM_SUPPORT_DB_*`、`PLATFORM_SUPPORT_FILES_ENABLED`、`PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`（DUAL）、`PLATFORM_SUPPORT_RABBITMQ_*`、`PLATFORM_SUPPORT_REDIS_*`、`SERVER_ADDRESS`、`SPRING_APPLICATION_JSON`（webhook callers）、`SPRING_*`、`TENANT_PROJECTION_ENABLED`、`WEBHOOK_ENABLED`、`WEBHOOK_TRUSTED_NETWORKS`（172.30.73.31/32）、`XXL_JOB_*`。本切片 Platform 只执行块 138 DML 与多语言发布，**不换镜像**。
- **client-console-web / operations-console-web**：nginx 镜像，env 只有 `ACME_VERSION`。
- **api-gateway**：本切片不换镜像；块 138 激活后需等快照刷新（或重发）。

## 3. 网络与 ID 资源

**mor-test-backend（172.30.73.0/24）已用**：.2 xxl-job-admin、.3 mysql、.4 redis、.5 rabbitmq、.6 nacos、.21 platform、.22 ops-bff、.23 gateway、.24 vault、.25 risk-decision、.26 client-master、.27 client-bff、.28 minio、.29 mailpit、.30 acquiring、.31 webhook-receiver、.32 compliance、.33 counterparty、.34 commercial、.35 payment、.36 channel-connector、.37 card、.38 ledger、.39 billing、.40 account、.41 reconciliation、.42 tax。
→ **合成筛查服务取 172.30.73.43**（`test-environment/compose.screening-mock.yaml`；repo compose 中也无 .43 声明）。

**Snowflake worker（0–31）已用**：17 commercial、18 tax、19 reconciliation、20 platform（OPERATIONS_AUTHENTICATION_WORKER_ID）、21 payment、22 card、23 ledger、25 risk-decision、**26 client-master 与 channel-connector 共用（既有问题，technique 1.5）**、27 counterparty、28 acquiring、29 compliance、30 billing、31 account。空闲：0–16、24。
本切片不新增服务，不需新 worker；若要顺带解决 26 共用，client-master 可改用 24（需单独决定，涉及在途 ID 唯一性）。

## 4. 数据库只读核对

| 库 | 核对 | 结果 |
|---|---|---|
| mor_vault | 最大 `policy_version_no` | **20**（本机为 24，两环境版本号不同，DML 用 MAX+1，无影响） |
| mor_vault | ID 9100000000000760—0769 与 `LEGAL_IDENTIFIER_V1` 占用 | 0 / 0 / 0（类型、规则、变更） |
| mor_vault | 07xx 已用 | 0700 PAYMENT_CARD_DEK、0730 CASH_ACCOUNT_NUMBER_V1、0740 FILE_STORAGE_CREDENTIAL_V1、0750 DATA_SOURCE_PASSWORD_V1 |
| mor_vault | client-master-service 规则 | 4 条（EMAIL / PHONE × PROTECT / REVEAL × CLIENT_ONBOARDING_CONTACT），无 compliance-service 规则 |
| mor_channel_connector | SERVICE 渠道 | 仅 `S7P2_SVC`（CLOSED）；无 `S_SCREENING_MOCK`、无 `SCREENING_SEARCH`；授权 1 行 counterparty-service DISABLED |
| mor_channel_connector | 标准 API created_by | 9 行均为 `221546852372267008`（运营管理员，可作 harness `--actor-id`） |
| mor_platform | 块 138 区间（permission / resource / route / guard） | 0；现最大块路由 ID `900000000000137411` |
| mor_platform | Client target | `236085272176050176`（client-console-bff，CLIENT）——与本机 ID 不同，块 138 必须用子查询 |
| mor_platform | 入驻权限 / 资源 | 5 个 `CLIENT_ONBOARDING_*` 权限、菜单 `CLIENT_ONBOARDING_REVIEW`（`236086010298056704`，顶级）+ 4 按钮，均 ACTIVE |
| mor_platform | c05 / c14 / o01 / o07 guard | 四条相同：principal 60/min + 并发 5、IP 120/min + 并发 10、shared 600/min + 并发 50（与本机一致） |
| mor_platform | header | c05、o01：authorization CORS；c14：+ content-type CORS/FORWARD、origin FORWARD；o07：再 + idempotency-key CORS（无 FORWARD） |
| mor_platform | o01 query 白名单 | applicationId, page, qualificationType, size, status |
| mor_platform | c03 form-options | ACTIVE（CM-2 取代后需决定停用时机） |

## 5. 发布顺序中的基础设施步骤（供阶段 6）

1. Vault：`db/vault/dml-check.sql`（五项 0）→ `db/vault/dml.sql` → `db/vault/dml-verify.sql`（版本应为 21）。
2. client-master、compliance 新镜像（其 env 见第 2 节，另加 compliance 出站配置）。
3. `compose.screening-mock.yaml` 起 screening-mock（172.30.73.43）→ channel-connector 按草稿加两键重建 → backend 网络内跑 `screening_channel_setup.py --smoke`（README）。
4. Platform 块 138：先在断网副本用候选 Platform JAR 跑 `block_compile_check.sh` 五态，再在 mor-test 执行休眠注册 → BFF / Web → 激活 → 等 Gateway 快照。
5. 回滚：channel-connector 用 3DS 覆盖文件重建（同镜像去两键）；screening-mock `rm -sf`；渠道配置不删（`S_SCREENING_MOCK` 置 CLOSED、授权 DISABLED）；Vault 按 `dml-rollback.sql`。

## 6. 收集中注意到的事项

- 读取 api-gateway env 时，一项非 `*PASSWORD*/*SECRET*/*TOKEN*/*KEY*` 命名的访问日志 HMAC 材料键（`OPERATIONS_ACCESS_LOG_HMAC_MATERIAL`）值出现在本次会话的终端输出中；本文件只记键名。该值是测试环境配置（亦在 compose 中），如需轮换由用户决定。
- vault-service 镜像没有 `printenv`，env 未读；本切片只改 Vault 数据，不换镜像。
