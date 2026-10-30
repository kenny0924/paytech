# tax 结账算税与事件登记 · mor-test 现网基线（阶段 6，2026-10-29 19:34—20:45Z 只读读取）

> 读取方式：`docker inspect`、`docker exec … printenv`（报告只记键名与非机密值）、`docker exec … sha256sum`、`docker cp` 从容器**取出** JAR / dist / health-probe、root 只读会话（`SET SESSION TRANSACTION READ ONLY`）、`mysqldump --single-transaction`（只读导出到 `~/Desktop/Workspace/Code-MoR-backups/tax-20261029/stage6-readonly-20261029T193746Z/`，0600，不入库）、`rabbitmqctl list_*`、对 `xxl_job` 库的只读 SELECT。
> 未停 / 起 / 重启任何容器，未写任何库、XXL、MQ、Platform 数据，未发布语言包，未 `docker cp` 写入容器，未登录 XXL Admin 或运营 API。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | tag / 构建时间 | 容器启动 | JAR / dist | 来源核对 |
|---|---|---|---|---|---|
| tax-service | `sha256:aeebac7adf46021fe10258fc15d2612ea1cc0c1632e75790572504d3e463aa0a`（**镜像记录已被删除**，见 1.1） | 原 `:development`（容器 2026-10-18 创建） | 10-22 05:28Z | JAR `a67caf6dd35ba906`（空壳，229 项） | = main `a90e4292` 构建，**0 差异** |
| acquiring-service | `sha256:e0a1f618a050e30b85059aa4214fea43bedeb35cf48438374d444e735e107299` | `relsnap-20261025a`，10-25 11:19Z | 10-25 14:12Z | JAR `d64644a67a9e7713` | 自身模块 0 差异；只差其他 Owner 的 api JAR（card / commercial / platform-support / risk-decision）与 `events.yaml`，均为已上线提供方的接口模型 |
| billing-service | `sha256:cf580cfbd05786e55c0e09b9749e1c1ba1a4fe54acef383e355bef7ac6c05650` | `refclient-20261028b`，10-28 03:33Z | 10-28 04:01Z | JAR `b218dc10ad9d062c` | 自身模块 0 差异；只差 commercial-api / platform-support-api 与 `events.yaml` |
| account-service | `sha256:17831407811fefb3e747208d0bda1a4c5e0481e19ea4423ee98f28723cfa6284` | `billing-b-20261023`，10-22 21:04Z | 10-23 07:46Z | JAR `f9bfd52ee103a6b7` | 只差 `events.yaml` |
| operations-console-bff | `sha256:4f08f1e601beb791f8e61c088e471e8839fc65783acbf513e412e5fcaf200b6b` | `rq03-20261029a`，10-29 18:01Z | 10-29 18:47Z | JAR `1646cf7af4c87a13` | = main 构建，**2877 项 0 差异** |
| operations-console-web | `sha256:a9d5000088e9bc239c85734c967a17f4d7f40f43efae94438f2241bba8078aa9` | `wfpanel-20261029d`，10-29 19:29Z | 10-29 19:29Z（阶段 6 开始前 4 分钟） | dist 258 个文件 | = main **`30361be7`** 构建，**258 个文件逐字节相同**（工作流节点编辑体验，任务卡写的基线 `a90e4292` 之后已提交） |
| platform-support-service | `sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9` | `rq03-20261029a`，10-29 18:28Z | 10-29 18:47Z | JAR `6d7ab1069d63938b` | = main 构建，**3122 项 0 差异**；**本需求不换**（第 3 节） |
| ledger-service | `sha256:51a57a3b721840b079798159f8d149a4362bbb76e763111a7183ab1bb08864e9` | `refclient-20261028b` | 10-28 04:00Z | JAR `8dca8af87933ead2` | 本需求只改 ledger **测试**文件，不发 |
| payment-service | `sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7` | `billing-b-20261023` | 10-23 07:48Z | JAR `96a1c9d83176f64d` | 本需求无改动，不发（验收探针容器用其 JDK） |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4` | `api-gatewayard3ds-20261022b`（tag 被 zsh 截断） | 10-22 18:04Z | — | 不换 |

**发布基线（主会话已确认）= main `30361be7` + tax 文件**：`a90e4292..main` 只有 `30361be7` 一个提交（工作流面板 2 个 web 文件与发布记录 / 文档），与 tax 的 379 个文件交集为 0（阶段 6 两次核对）。

比较方法：`git archive 30361be7 paytech` 构建 6 个 app（`-DskipTests -am clean package`，JDK 25 离线），`harness/jarcmp.py` 按条目 CRC 比较（排除 MANIFEST / maven 元数据 / 索引），明细 `evidence/jar-baseline-*.txt`；ops-web 用 `git archive 30361be7 operations-console-web` + 主工作树 `node_modules`（`package-lock.json` 相同）`npm run build`，与 `docker cp` 出的现网 dist 逐文件 SHA-256 比较（`evidence/ops-web-dist-*.sha256`）。结论：**候选在 30361be7 之上叠加本需求，不回退任何现网内容。**

### 1.1 tax-service 回滚镜像

现网 tax 容器读到的镜像 ID `aeebac7a…` 在本机已无镜像记录（`docker image inspect` → No such image；容器 2026-10-18 由 `:development` 创建，镜像之后被强制删除）。容器一旦被替换就无法再按该 ID 起回。阶段 6 用同一 `test-environment/java/Dockerfile`、从现网容器取出的 JAR（`a67caf6d…`，与运行中容器内 `sha256sum` 一致）和现网 health-probe 重建了回滚镜像 **`sha256:884728dc7be5d69a45f9f9692d939f468707475aff3da1ebf2fbe5eeac83155f`**（tag `mor-test/tax-service:tax-rollback-20261029a`）：镜像内 JAR 与现网相同，JRE `jdk-25.0.4+7`、User、WorkDir 与现网相同（Entrypoint 由 compose 覆盖）。`compose-rollback.tax-service.yaml` 用它。另：本机 6 个服务的 `:development` tag 都已不存在，不带覆盖文件不能起服务（只认 sha256 的做法不受影响）。

## 2. 容器环境变量（键名；非机密值）

| 服务 | 现网键（不含镜像自带 `HOME HOSTNAME JAVA_* LANG LANGUAGE LC_ALL PATH`） | 相关值 |
|---|---|---|
| tax-service（14） | `NACOS_*` `SERVER_ADDRESS` `SPRING_CLOUD_NACOS_DISCOVERY_{GROUP,IP,PASSWORD,USERNAME}` `SPRING_PROFILES_ACTIVE` `TAX_DB_{PASSWORD,URL,USERNAME}` `TAX_SNOWFLAKE_{REGION_ID,WORKER_ID}` `TZ` | DB `mor_tax` / `tax_app`；Snowflake 0 / **18**（0–31 内唯一，沿用）；IP 172.30.73.42；**无** RabbitMQ / XXL / Platform 地址 / 信任库 |
| acquiring-service（37） | 主 compose 全部键 + 覆盖键 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE` | **MOCK**（relsnap 覆盖）；Snowflake 28；XXL 执行器已启用 |
| billing-service（37） | 主 compose 全部键 | 四个领域服务固定地址、Snowflake 30、XXL 启用 |
| account-service（29） | 主 compose 全部键 | Snowflake 31 |
| operations-console-bff（14） | 主 compose 全部键（含 `OPERATIONS_RECONCILIATION_BASE_URL`） | 无 `OPERATIONS_TAX_BASE_URL` |
| operations-console-web | 仅 nginx 镜像自带 | — |
| platform-support-service | 59 键 | `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`；`JAVA_TOOL_OPTIONS` 指向挂载的 `platform-truststore.p12` |

覆盖链：以空覆盖渲染主 compose 与现网比较，**只有 acquiring 的 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=MOCK` 来自历史覆盖**（`requirements/20261023/commercial-商业关系快照与真实解析接入/release/compose-candidate.yaml`）；其余服务现网 env 全部来自主 compose。`scripts/env_check.sh` 渲染本目录 12 个覆盖文件：回滚 6 行与运行容器**逐键逐值相同**；候选只多本需求新增键（tax 17、acquiring 1、ops-bff 1）——`evidence/env-check.txt`。

## 3. mor_tax / mor_acquiring / mor_billing / mor_client_master（只读）

| 项 | 读数 |
|---|---|
| mor_tax | 旧 Finance 来源 **24 张表，逐表 `COUNT(*)` 合计 0 行**；`tax_app` 库级 DML（新表自动覆盖） |
| AE001 纳税主体 | `mor_client_master.t_operating_entity`：AE001 → `party_id 880000000702` ACTIVE；acquiring MOCK 配置 `AE001_PARTY_ID = 880000000702` → **MOCK 与 REAL 一致**，`tax-initial-data.sql` 原值即正确值，发布副本与仓库逐字节相同 |
| mor_acquiring | 订单 51（CANCELLED 11 / EXPIRED 30 / PAID 10，**无 OPEN**，最近建单 10-25 14:12Z）；订单行 81 行税类**全部 `STANDARD`**；上下文 51 行快照非空；无多合同订单；新表 / 新列均不存在；`acquiring_app` 库级 DML |
| 迁移触及列的现网定义 | `t_commerce_order.tax_amount / rounding_amount / total_amount` 已可空，注释与回滚脚本恢复值逐字相同；`t_order_context.finance_tax_snapshot_*` NOT NULL；`uk_consumer_contract(order_id)`；表注释与回滚脚本逐字相同 |
| mor_billing | `t_billing_fact_ref.ref_type` 注释无 `TAX_CALCULATION`（`mor_billing-comment-rollback.sql` 恢复的就是现网原文）；事实 12 条，其中 AUTO PAYMENT 只有 1 条（billing B 合成事件，10-23）——**mor-test 从未有真实订单形成 PAYMENT 事实** |
| 支付 | `mor_payment` 尝试 6 次：唯一成功为 10-22 18:38Z（PAYERMAX 通道，3DS 验收后 MID 已 SUSPENDED）；之后路由到 DEMO_NI 的 3 次全部 FAILED。**mor-test 当前没有能成功的卡渠道** |
| billing 上游数据（合成验收用） | DEMO-CL-001 CLIENT_PRICING v1 ACTIVE（10-20—11-19）；`DEMO_NI_COST` v1 ACTIVE（10-23 07:42:56Z 起）；清算账户 NI-AE001-USD / NI-AE001-USD-B ACTIVE；ledger AE001 USD 账簿 OPEN、2026-10 期间 OPEN、**2026-11 期间 FUTURE**；Finance Entity 仅 PLATFORM_HQ（Party 880000000701）。MOCK 商业配置的定价 / 合同 ID（104 / 108 / 109 / 105）在 mor_commercial **不存在** |

## 4. MQ / XXL / 内部认证 / 存储

| 项 | 读数 |
|---|---|
| RabbitMQ | vhost `/mor-test` 唯一用户 `mor_test`，`configure / write / read = .* .* .*`，无 policy；`acquiring.events`（fanout）当前只绑定 `billing.acquiring.hash`；`tax.*` 交换机 / 队列 0 个；`billing.acquiring.dlq` / `account.billing.dlq` / `acquiring.payment.dlq` 均 0 条 |
| XXL | Admin 10-29 19:29Z 刚重启（他人所为）；`xxl_job_group` 最大 id 17，**无 `tax-service`**；`xxl_job_info` 最大 id 27；现有 12 个 `compensationEventScan` 任务参数一致（`*/30 * * * * ?`、SHARDING_BROADCAST、SERIAL_EXECUTION、60 s、0 次重试、DO_NOTHING） |
| 内部认证 | tax / acquiring / billing / ops-bff 均未设 `*_INTERNAL_AUTH_MODE`（缺省 OFF）；Platform `DUAL`（接受 Token，回退 `X-Caller-Service`）；Platform 文件接口**没有调用方白名单**（只有通知、Webhook、data-query、内部 Token 签发有），来源系统按 `X-Caller-Service=tax-service` 识别，写入需 `(tax-service, PRIVATE)` 写入路由（块 146 第 C 段）。tax 入站在 OFF 下不校验，acquiring / ops-bff 调 tax 无需登记 |
| 存储 | 默认私有写入路由 `(*, PRIVATE)` → 桶 `mor-platform-files`（MINIO 实例 `https://minio.mor-test.localhost:29400`）、扫描策略 `CLIENT_ONBOARDING_FORMAT`（`application/pdf` 允许，10 MiB，ACTIVE）；容器内 `minio.mor-test.localhost` 是 minio 容器的网络别名；证书 CN/SAN `minio.mor-test.localhost`，由 `test-environment/private/test-ca.crt` 签发（`openssl verify` OK，到期 2027-10-14）；`private/platform-truststore.p12`（JDK cacerts 118 + test CA）已被 Platform 与 card 以 `JAVA_TOOL_OPTIONS` 挂载使用 |

## 5. mor_platform（只读）

| 项 | 读数 |
|---|---|
| 注册块 | 七张表最大块 **143**（REQ-03）。跨会话约定（2026-10-29，主会话）：144 = 联合查询（design-discuss-19，`Code-MoR-jq` 已写入注册 SQL 与 initial-data）、145 = REQ-04（design-discuss-b8）、**146 = 本需求**；146 区间七张表 0 行（`precheck/block-146-precheck.out`），阶段 7 第 ③ 步再确认 |
| 编码 / 路径 | `TAX_VIEW`、`OPERATIONS_FINANCE_TAX`、`/finance/tax`、`operations-finance-tax-*`、`/api/operations/tax/*`、写入路由 `tax-service` 均未占用 |
| 依赖 | `PLATFORM_ADMIN`（510，version 52）、财务菜单 97301、target 1（ops-bff）均 ACTIVE；财务子菜单排序 10—80（新菜单 75 落在记账规则 70 与对账 80 之间） |
| 路由计数 | ACTIVE 631 / DISABLED 6（最大 route_id …143412） |
| 同形路由 | GET `operations-file-record-detail`（134411）、下载 POST `operations-file-record-download`（134412）：请求头、Guard、body 上限、超时与本块两条路由逐项相同 |
| 语言包 | operations-console 当前 **#23**（REQ-03）；26 个命名空间、10 688 个 ACTIVE key；无 `tax` 命名空间 |
| 能力清单 | 现网 Platform 的 `console-resource-capabilities.json` 无 `OPERATIONS_FINANCE_TAX`（候选源码有） |
