# REQ-02 · mor-test 现网基线（阶段 6，W3，2026-10-30 17:50—18:05Z）

> 只读：`docker inspect / ps / logs / exec（printenv 键名、sha256sum）`、`docker cp` 从容器取出、mor-test MySQL root 只读 SELECT（READ ONLY 会话）、Nacos 实例列表、XXL Admin 分组 / 任务查询。未停 / 起容器，未写任何东西。取出物在 `~/Code-MoR-backups/req02-stage6/live/`（0700 / 0600，不入库）。
> 对照构建：候选树 `Code-MoR-req02-rel` 迁入改动之前，在 main `331d8e0e` 上 `-DskipTests -pl <三个 app> -am clean package`；ops-web 用"331d8e0e + 明细账运维 18 个 ops-web 文件"`npm ci --offline && npm run build`。产物在 `~/Code-MoR-backups/req02-stage6/base/`。

## 1. 镜像与制品

| 服务 | 容器镜像 ID | tag | JAR / dist（sha256 前 16 位） | 与对照构建比对（jarcmp：去掉 MANIFEST / classpath.idx / layers.idx / pom.properties，paytech-* 内层 JAR 展开逐条目 CRC） | 现网独有 |
|---|---|---|---|---|---|
| channel-connector-service | `sha256:23679d451fe4…` | card3ds-20261022（本机 tag 名为 `channel-connector-serviceard3ds-20261022`，zsh `:c` 修饰符吞字，ID 正确） | `9cb23e8161abc430` | 现网 1320 / main 1321：现网独有 0、main 新增 1、改动 4，全部在提供方 / 基础 JAR（card-api BIN 库 8a441c90 10-28、http-client-support f707f2da 10-23、messaging 事件目录 fa21d5ce 10-30），源码提交均晚于镜像构建（10-22 17:12Z） | **0**（落后 main） |
| client-master-service | `sha256:2b12c2e2184b…` | rq12-20261029a | `ca0b61fba694929d` | 现网 1902 / main 1935：现网独有 0、main 新增 33、改动 9，全部在 platform-support-api（联合查询 d673817f 10-30、通知 tenantRecipients b92c0fde 10-30）与 messaging 事件目录；均晚于镜像构建（10-29 12:49Z） | **0**（落后 main） |
| commercial-service | `sha256:806043a320cf…` | gate-20261029a | `40be77e24894562b` | 1304 = 1304，改动 17：client-master-api（REQ-12 入驻表单 88453366 10-29 18:33 +04）与 messaging 事件目录；均晚于镜像构建（10-28 19:39Z） | **0**（落后 main） |
| operations-console-web | `sha256:5784fa817c47…` | plops-20261030a（明细账运维 17:27Z 发布） | dist 291 个文件 | 与"331d8e0e + plops 18 个 ops-web 文件"构建 **291 个文件逐字节相同**（[live](./evidence/ops-web-dist-live-5784fa81.sha256) = [rebuild](./evidence/ops-web-dist-plopsbase-rebuild.sha256)） | **0** |

明细：[evidence/jar-live-vs-main-331d8e0e.txt](./evidence/jar-live-vs-main-331d8e0e.txt)。容器内 `sha256sum /app/application.jar` 与取出文件一致（3 个 JAR 逐一核对）。

health-probe：三个容器 `/app/health-probe/*` 5 个 class 相同；`LocalHttpHealthProbe.class` 为 `3466e45e…`（payout 之前的版本），main 源码编译为 `8b8d8348…`（payout S1 加了 19117）。**候选镜像沿用现网探针**（从 commercial 容器 `docker cp` 取出），与现网逐文件相同；本需求三个服务端口不变，不需要新探针。

## 2. 环境变量（键名；值只在 env_check 中比较，不抄写）

| 服务 | 键数 | 键名 |
|---|---|---|
| channel-connector-service | 40 | CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES / _TRUSTEDNETWORKS、CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID、CHANNEL_CONNECTOR_RABBITMQ_* 5、CHANNEL_CONNECTOR_REDIS_* 3、CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID / WORKER_ID、MOR_CHANNEL_CONNECTOR_DB_* 3、NACOS_* 2、PAYTECH_INSTANCE_ID、SERVER_ADDRESS、SPRING_CLOUD_NACOS_DISCOVERY_* 4、SPRING_PROFILES_ACTIVE、TZ、XXL_JOB_* 8 + 镜像自带 6（JAVA_HOME JAVA_VERSION LANG LANGUAGE LC_ALL PATH）。**无 CHANNEL_CONNECTOR_INTERNAL_AUTH_MODE**（默认 OFF） |
| client-master-service | 40 | CCT_RABBITMQ_* 5、COMPENSATION_INSTANCE_ID、MESSAGING_CONSUMER_SERVICE、MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS、NACOS_* 2、ONBOARDING_* 4、SPRING_CLOUD_NACOS_DISCOVERY_* 4、SPRING_DATASOURCE_* 3、SPRING_DATA_REDIS_* 3、SPRING_PROFILES_ACTIVE、TZ、XXL_JOB_* 8 + 镜像自带 6 |
| commercial-service | 37 | COMMERCIAL_COMPENSATION_INSTANCE_ID、COMMERCIAL_RABBITMQ_* 5、COMMERCIAL_REDIS_* 3、COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED、COMMERCIAL_SNOWFLAKE_* 2、COMMERCIAL_XXL_JOB_ENABLED、NACOS_* 2、SPRING_CLOUD_NACOS_DISCOVERY_* 4、SPRING_DATASOURCE_* 3、SPRING_PROFILES_ACTIVE、TZ、XXL_JOB_* 7 + 镜像自带 6 |
| operations-console-web | 7 | nginx 镜像自带 |

## 3. 覆盖链（`requirements/*/release/compose-candidate*.yaml`，只在覆盖文件里出现、base 没有的键）

| 服务 | 来源 | 键 | 本次处理 |
|---|---|---|---|
| channel-connector-service | payment-3DS全链路（10-22）→ client-master-全球合规入驻与资格切片1（10-27，草稿并入 `compose-candidate.yaml`） | RABBITMQ 5 键（base 也有，同值）、`CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http`、`…TRUSTEDNETWORKS=172.30.73.43/32`、`depends_on rabbitmq` | 候选与回滚都带上 |
| client-master-service | client-master-业务信息补采（rq12，10-29）等 | 全部 34 键（base 已有，同值；覆盖文件原样携带） | 原样携带 |
| commercial-service | 商业关系快照（10-23）→ 开通项前置门禁 REQ-01（10-28） | RABBITMQ 5、REDIS 3、SNOWFLAKE 2（Worker 17）、COMPENSATION_INSTANCE_ID、XXL_JOB_ENABLED、XXL 7、`COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED=false`、`depends_on rabbitmq / redis` | 候选与回滚都带上（base 只有 11 键，**不带覆盖文件起 commercial 会丢 26 键**） |
| operations-console-web | — | 无 | — |

渲染比对见 [evidence/env-check.txt](./evidence/env-check.txt)：8 个文件全部 `missing=[] extra=[] valuediff=[]`。

## 4. 服务发现与内部认证（任务卡第 6 项）

| 项 | 现网读数 | 结论 |
|---|---|---|
| Nacos（namespace `mor-test`、group `DEFAULT_GROUP`） | channel-connector-service `172.30.73.36:18110`、client-master-service `172.30.73.26:18101`、commercial-service `172.30.73.34:18108`，均 healthy / enabled | commercial 以 `http://channel-connector-service` / `http://client-master-service` 经 `LoadBalancerClient` 选实例（`CheckoutResolutionConfiguration.discoveryTransport`），与现有 `ClientMasterCheckoutClient` 同法；commercial 近 6 小时日志无 `discovery is unavailable` / `No servers available` |
| internal-auth | 三个服务 `application.yml` 默认 `OFF`，env 均无 `*_INTERNAL_AUTH_MODE` | `InternalCallTokenOptions` 在 OFF 时不附加令牌；提供方不校验 → **无需登记令牌 / 受众** |
| 调用方白名单 / 路径过滤 | 两个新 Controller 无 caller 校验；client-master 只对入驻表单路径加了文档请求拦截器；channel-connector 的 `TRUSTEDNETWORKS` 只作用于对外协议执行 | **无需登记**；新接口不经 Gateway，无注册块 |

## 5. 业务数据读数（影响本次发布）

- 支付方式开通项（成员资格 ACTIVE）12 条，全在 AE001，7 个 Client 主 MCC 均为 5999（已确认）：ENABLED 4、`SUSPENDED_BY_PREREQUISITE` 8（`PENDING_PREREQUISITES` 0）。
- AE001 可用 MID 能力：CARD × USD × 5999 两行（MID `238508278684950528` PAY+REFUND、`238508278957580288` PAY+AUTHENTICATE），CARD × `*` × 5816 一行（只有 AUTHENTICATE）；第三个 MID `239093386823442432` SUSPENDED。**没有任何 AED 能力行**。
- 4 条 ENABLED 中 3 条 CARD 的 CURRENCY 条件含 AED（DEMO-CL-001 AED+USD、DEMO2-CL-101 仅 AED、DEMO2-CL-103 AED+USD），ACTIVE 条款里都有 AED 的 CARD 价格。按原 Q-4（所有币种）4 条全部会被暂停；按 2026-10-30 修订的 Q-4（任一币种就绪即满足）**暂停 2 条**（DEMO-CL-001 APPLE_PAY、DEMO2-CL-101 CARD），DEMO-CL-001 / DEMO2-CL-103 CARD 保持 ENABLED 并带 AED 缺 MID 提醒（见 [precheck](./precheck/)）。
- data-query：数据源 `MOR_CHANNEL_CONNECTOR_REPLICA`（id `239060842660839424`，`mysql:3306/mor_channel_connector`，ACTIVE，最近检查 PASSED 07:03Z，账号 `data_query_demo_reader` 对 `mor_channel_connector.*` SELECT）；模板编码 `GOLIVE_MID_READY` 未占用。
- XXL：commercial-service 分组 14（在线 `http://172.30.73.34:9999/`）；`capabilityPrerequisiteRecheck` 任务 25（`scope=PENDING`，每 5 分钟）、26（`scope=ALL`，每日 03:00 UTC），均 RUNNING。
- 语言包：operations-console 当前发布 #30（plops）；与 REQ-06 约定谁先进阶段 7 谁先发，本需求预计 #31，REQ-06 先发则 #32。
