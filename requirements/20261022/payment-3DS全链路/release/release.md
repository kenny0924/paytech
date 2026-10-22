# 3DS 全链路 + 卡 BIN 库 · mor-test 合并发布包

阶段 6（2026-10-22，只读核对 19:50—20:25 +04 / 15:50—16:25Z）。卡 BIN 库需求的 [delivery](../../card-BIN库与卡属性/delivery.md) 链接到本文件，两项需求共用这一份清单。

本阶段只做了三件事：只读核对 mor-test（`docker inspect`、容器内 `printenv` 只取键名、容器内 `sha256sum` 运行 JAR、只读 SELECT、`rabbitmqctl list_*`、`compose.sh config`）；把已评审 SQL 复制成发布副本；写发布包。**没有停启或替换任何容器，没有写任何库、XXL、Vault、RabbitMQ，没有 docker cp，没有构建或打 tag 镜像。** 镜像由卡 F（`Code-MoR-card3ds-rel`，main `73a2460d` 重建）完成后另行构建；阶段 7 须用户另行授权，由主会话按第 6 节执行，结果写第 11 节。

## 0. 需要决定（会改变发布方案的事实）

| 编号 | 事实（阶段 6 核对） | 影响 | 建议 |
|---|---|---|---|
| N1 | **api-gateway 现网镜像已丢失**：容器 `mor-test-api-gateway-1` 运行 `sha256:4cf14912…`（10-18 13:39Z 起，`:development`，JAR `1054e867223a9a28`），本机镜像库里已没有这个 ID，也没有任何 api-gateway 镜像（推测 10-22 磁盘事故后被清理） | Gateway **没有回滚镜像**；若不处理，Gateway 换新后出问题只能前修 | 阶段 7 前由主会话按 release-s2 第 1.2 节做法重建回滚镜像：从 `e119cbd1`（同时刻 13:39Z 的 client-console-bff 即由它构建）构建 api-gateway，与运行容器 JAR 逐条目比较到 0 差异后，把 `compose-rollback.yaml` 的占位换成该镜像 ID。比较须从容器读 JAR，属阶段 7 授权范围（本卡不可 docker cp） |
| N2 | **mor-test 不具备 AC-12 的前提**：`PAYERMAX` 渠道为 DRAFT，**没有技术账号、没有 MID**（只有一条沙箱验证账号）；PAY 流程、原子 API `ORDER_AND_PAY` 仍是 10-14 的**收银台版**（无 `paymentDetail.cardInfo`），`PAY_QUERY` 流程未输出 `CLOSED`；mor-test 也没有 `PAYERMAX_MOCK` | `configure_3ds.py` 的前提（已执行 `configure_card.py`、PAY 为卡直付）不成立；没有账号与 MID，Payment 选不到 PayerMax | 两种做法，请选择：**(a)** 阶段 7 一并做 AC-12：在运营台为 AE001 建 PAYERMAX 技术账号（UAT 凭据，录入方式同沙箱验证账号）、MID、`CARD/*/*/USD/<MCC>` 能力（`ACTION.PAY=true`、`SECURITY.3DS_MODE=SUPPORTED`），按序运行 `configure_card.py PAYERMAX` → `configure_3ds.py PAYERMAX`；代价是 mor-test PayerMax PAY 由收银台版改为卡直付版（收银台流程被删除，见第 8.3 节）；**(b)** 本次只发布代码与数据、不运行两个 harness，AC-12 继续记为遗留。倾向 (a)，因为阶段 5 已把 AC-12 留到 mor-test |
| N3 | 回调定义 DML（`sql/21`）按 `PAYERMAX`、`PAYERMAX_MOCK` 各 1 行写，文件注释“预期影响 2 行；不是 2 行时 ROLLBACK”；mor-test 只有 `PAYERMAX` | 执行结果是 1 行。文件本身无条件 COMMIT，注释的判定需人工看输出 | 接受 1 行（只核对输出为 `PAYERMAX / "RSA_SHA256"` 一行），不改文件；在执行记录注明 |
| N4 | Payment 新键 `PAYMENT_CHANNEL_CALLBACK_BASE_URL`：mor-test 没有公网入口，Nginx 也没有回调 location | 有值时 PAY 会把 notifyUrl 传给 PayerMax UAT，但通知到不了；为空时不传 notifyUrl | 置空（候选文件已置空）。AC-07 真实通知为遗留；mor-test 只验 Gateway 与验签拒绝（验收计划第 2 节） |
| N5 | **支付点 ACTIVE 发布 release 11 含 R-11 `THREE_DS_AMOUNT`（金额 > 500 → THREE_DS_REQUIRED）**；mor-test 仅有的 3 条 MID 能力（`DEMO_NI`，`scheme_code` 全为 `*`）都没有 `SECURITY.3DS_MODE` | 新 Payment 上线后，金额 > 500 的卡支付若落在 `DEMO_NI` 会直接失败 `THREE_DS_CHANNEL_UNAVAILABLE`（旧版本走 AUTHENTICATE）。现状：`mor_payment` 全空、`DEMO_NI` 没有任何标准 API 实现，实际不会造成回归 | 接受，不给 `DEMO_NI` 加 3DS 能力（它不能真的做 3DS）；验收借此验证 AC-02。`scheme_code` 全为 `*`，BIN 带 `schemeCode` 后没有精确度冲突 |
| N6 | **ops-web 现网 `7551dc3b` 含未提交改动**：code-mor-42 的按钮布局（`ApiCredentials.tsx`、`Audit.tsx`、`GatewayConfiguration.tsx`、`configuration.css`、`styles.css`）只在主工作区未提交；main `73a2460d` 不含。主工作区另有工作流附件的未提交改动（platform、ops-bff、ops-web 共十余个文件），不在 mor-test | 只从 `73a2460d` + 本需求构建 ops-web 会**回退按钮布局**；把主工作区整个带上又会夹带未发布的工作流附件改动 | ops-web 构建源 = 卡 F 结果 + 按钮布局 5 个文件（以 code-mor-42 提交为准，提交前按文件取 diff）；构建后与 `7551dc3b` 比 `dist/` 差异，只应出现 BIN 页面与 S5-01 / S5-02 相关的块。Platform / ops-bff 不得带工作流附件未提交改动 |
| N7 | AC-08“规则按发卡国命中”需要基于点版本 220003 新建并激活一个 Risk 执行发布 | 会替换 mor-test 所有人共用的支付点发布 release 11 | 默认不做，沿用阶段 5 证据；mor-test 只核对评估快照 3 列冻结。若要做，新发布须完整复制 release 11 的规则再加发卡国规则，验收后再发布回同等内容 |
| N8 | **（已核对：九个服务应用代码无非本需求变化、messaging 连接恢复已在现网，结论见本节末“卡 H 处理结果”，本格为阶段 6 预判）** 本次重建会把各服务现网镜像之后、`73a2460d` 之前的已提交改动一起带上：payment / risk-decision（现网 10-18 15:55Z `:development`）、api-gateway（10-18 13:39Z）、acquiring / counterparty（10-20 `counterparty-20261020`）、card（`ccu2-20261022`）。已知涉及 Foundation：messaging 连接恢复（`339db7b8`、`8d123532`）、HTTP 客户端 Problem detail 开关（`beb21618`，默认关）、工作流（`1d39cd94`）；counterparty 改名（`c661e9fc`）只动 payment / acquiring 的测试与文档 | 第一次把这些改动带上 mor-test，属行为回归风险（各自已有 auto 测试） | 构建时按 release-s2 第 1.2 节对每个服务做“现网 JAR ↔ 基线构建”比较，把差异逐项列进本文件第 2.4 节后再请用户确认 |
| N9 | 并行会话：现网 `mor_platform` 有 15:31—15:41Z 新建的桶 `mor-test-platform-files-k7q2` 与 `client-console-bff` 精确写入路由（来源未登记，疑为文件公开桶或第二段验收）；工作流附件改动未提交；code-mor-46（CYBS）channel-connector `5f22b55b` 为现网基线 | 期间若有人发布这十个服务中的任一个，本包的回滚基线与环境核对作废 | 第 6 节 S0 协调；S1 复核镜像与 `00-precheck` 全部等于阶段 6 结果，否则停止并重做阶段 6 核对 |

其余核对项都符合方案，见第 3 节。

> **N6 裁决（2026-10-22，code-mor-42 答复）：** 按钮布局不提交，ops-web 构建源 = 卡 F 结果 + 主工作区 6 个文件的当前内容：`operations-console-web/src/pages/ApiCredentials.tsx`、`src/pages/Audit.tsx`、`src/pages/GatewayConfiguration.tsx`、`src/configuration/configuration.css`、`src/styles.css`、新增未跟踪 `src/components/RefreshButton.tsx`。校验方法：`1d39cd94` + 这 6 个文件构建的 `dist/` 应与现网 `7551dc3b` 逐字节一致；本次构建与 `7551dc3b` 的差异只应来自本需求与 `1d39cd94..73a2460d` 的已提交改动。

> **卡 H 处理结果（2026-10-22，详见第 2.4 节）：**
> - **N1 已处理：** 从 `e119cbd1` 重建 api-gateway，JAR 与运行容器逐条目 0 差异，回滚镜像 `sha256:da54e23c…`（`rollback-gateway-e119cbd1`）已填入 `compose-rollback.yaml`。
> - **N6 已决（B）：** ops-web 构建源 = rel 的 `operations-console-web` + 主工作区 16 个未提交文件（按钮布局 6 + code-mor-55 数据查询页面体验 10，不含 `docs/*.pen`）。校验：`1d39cd94`（与 `73a2460d` 无 ops-web 差异）+ 6 文件与 `7551dc3b` 逐字节一致，+ 16 文件与现网 `5a9eb12d` 逐字节一致（188 个文件，`nginx.conf` 一致）。候选 `sha256:9cefde8e…`（`card3ds-20261022-dq`）源码 `tsc --noEmit` 通过、`npm test` 1342 / 1342；`CardBinLibrary` 引用的 `DataQueryParts`（`Note`、`useResource`）与 `dataQueryCommon`（`hasButton`、`localTime`）未受数据查询改动影响。相对 `5a9eb12d` 只多 BIN 页面、导航登记与 `capabilityConditions`（第 2.4.4 节）。17:18Z `42879401` 发布后改以 rel2 重建为 `sha256:8bdbdd44…`（`card3ds-20261022b`，`42879401` + 16 文件复现现网 `11f1d24e` 逐字节一致，`npm test` 1344 / 1344，第 2.4.6 节），已填入候选文件；回滚文件改指现网 `11f1d24e`。BIN 页面在新 `dataQuery.css` 下的布局列入阶段 7 验收（验收计划第 3 节）。
> - **N8 已核对（取代上表 N8 的预判）：** 九个 Java 服务现网 JAR 与基线构建逐条目 0 差异，现网源码基线为 `91333d28`（risk-decision、payment）、`c661e9fc`（acquiring、counterparty）、`4b5426e5`（card）、`e119cbd1`（api-gateway）、`73a2460d`（platform-support、channel-connector、ops-bff）。基线到 `73a2460d` 之间九个服务的应用代码（`BOOT-INF/classes`）全部 0 差异；随本次带上的只有共享库 / 合同库：`beb21618` HTTP 客户端 Problem detail 开关（默认关，调用方未改用）、messaging 事件目录（这些服务不生产也不消费相关事件）、`CardApiConstants` 编译期常量、以及 payment / acquiring / gateway / counterparty 不调用的 channel-connector-api、platform-support-api、client-master-api DTO（第 2.4.3 节）。messaging 连接恢复（`339db7b8`、`8d123532`）**已在现网**，不属于顺带上线；counterparty 改名 `c661e9fc` 已在 acquiring / counterparty 现网。不需要 DDL、数据或新环境变量。另：**主工作区 `operations-console-web/node_modules` 已与 lockfile 漂移**，用它构建 `1d39cd94` 与现网 `c7646648` 不一致，干净 `npm ci` 后逐字节一致；今后 ops-web 发布构建须先 `npm ci`。

> **N6 再修订（2026-10-22 16:44Z 之后）：** mor-test ops-web 已被 code-mor-55（数据查询页面体验，会话已结束）换成 `5a9eb12d`（tag `dataquery-ux-20261022a`）= `73a2460d` + 按钮布局 6 个文件 + 数据查询体验 10 个文件（`src/dataQuery/DeclarationPanel.tsx`、`TemplateEditor.tsx`、`TrialPanel.tsx`、`VersionDrawer.tsx`、`dataQuery.css`、`templateModel.ts`、新增 `templateSync.ts` 与 `templateSync.test.ts`、`src/pages/DataSources.tsx`、`QueryTemplates.tsx`），均未提交。ops-web 构建源改为 rel + 这 16 个文件；校验：`73a2460d` + 16 个文件应与 `5a9eb12d` 逐字节一致。回滚目标改为 `5a9eb12d`。

## 1. 环境身份

| 项 | 值 |
|---|---|
| 环境 | `mor-test`（本机 Docker，context `desktop-linux`，Compose 项目 `mor-test`，入口 `test-environment/compose.sh`） |
| 数据库 | 容器 `mor-test-mysql-1`（`49ee33da1c49`，MySQL 8.4.11），宿主机 `127.0.0.1:23306`；执行身份 root（口令只经 `MYSQL_PWD` 从 `test-environment/.env.test` 传入）；目标库 `mor_risk_decision`、`mor_payment`、`mor_channel_connector`、`mor_platform`、`mor_card`。不涉及本机开发库（13306）、e2e 库（`*_v2e2e`）或任何生产库 |
| MQ | `mor-test-rabbitmq-1`，vhost `/mor-test` |
| XXL | `mor-test-xxl-job-admin-1`，库 `xxl_job`（不改） |
| 运营入口 | `http://localhost:28080`（`operator@example.test`） |
| 代码 | 卡 F 集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-rel`（detached `73a2460d` + 两项需求补丁；未提交）。阶段 5 验收版本为 `Code-MoR-card3ds-int`（基线 `1d39cd94`） |

## 2. 现网快照（只读，2026-10-22 ~16:00Z）

### 2.1 当前镜像 = 回滚目标

明细：[snapshot/containers.txt](./snapshot/containers.txt)、[snapshot/images.txt](./snapshot/images.txt)、[snapshot/jar-sha.txt](./snapshot/jar-sha.txt)。

| 服务 | 运行镜像 | 来源 tag / 构建时间 | 启动（UTC） | 重启 | 运行 JAR SHA-256 前 16 位 | 本机镜像 |
|---|---|---|---|---|---|---|
| risk-decision-service | `0a5de56f…` | `:development`，10-18 15:55Z | 10-22 05:37:16 | 18 | `3b2738eeab8beba9` | 在 |
| payment-service | `447406cb…` | `:development`，10-18 15:55Z | 10-22 05:37:47 | 24 | `668bc1332f064e51` | 在 |
| acquiring-service | `03729ba8…` | `counterparty-20261020` | 10-22 05:36:40 | 16 | `225936725982042c` | 在 |
| counterparty-service | `c7143680…` | `counterparty-20261020` | 10-22 05:36:42 | 19 | `fe1e3d9c62a669a5` | 在 |
| platform-support-service | `ac0b4845…`（阶段 6 为 `7cb30402…`） | `wfinline-20261022`（code-mor-ae，main `42879401`） | 10-22 17:18:52 | 0 | `d306f83b935d85e4` | 在 |
| card-service | `542a16f0…` | `ccu2-20261022`，10-22 06:20Z | 10-22 06:47:31 | 0 | `192f4a2c30f0efef` | 在 |
| channel-connector-service | `5f22b55b…` | `cybs-20261022`（code-mor-46） | 10-22 12:49:56 | 0 | `8c9db543ae40a973` | 在 |
| api-gateway | `4cf14912…` | `:development`，10-18 13:39Z | 10-22 05:36:34 | 19 | `1054e867223a9a28` | **丢失（N1）** |
| operations-console-bff | `798d5e4c…`（阶段 6 为 `a5b9a17c…`） | `wfinline-20261022`（code-mor-ae，main `42879401`） | 10-22 17:18:29 | 0 | `caf95fee3688094b` | 在 |
| operations-console-web | `11f1d24e…`（阶段 6 为 `7551dc3b…`，16:44Z 起 `5a9eb12d…`） | `wfinline-20261022`（`42879401` + 16 个未提交文件，N6） | 10-22 17:18:01 | 0 | — | 在 |

重启计数是 10-18 以来的累计（最近一次是 10-22 05:3x 磁盘事故恢复）。十个容器全部 healthy。**阶段 6 之后现网变化两次：16:44Z code-mor-55 换 ops-web 为 `5a9eb12d…`（数据查询页面体验）；17:18Z code-mor-ae 发布 main `42879401`（工作流附件内嵌预览），换 platform、ops-bff、ops-web 并写 1 行 Platform 数据（`query_param` 127030）。上表三行已更新为 17:18Z 之后的现网，S1 复核以上表为准（第 2.4.6 节）**；其余七个容器复核仍与阶段 6 一致，JAR SHA-256 相同。payment / risk-decision 的 `:development` 目前仍指向运行 ID，回滚文件已改钉 sha256。

### 2.2 环境变量键（只记键名）

`printenv` 键名清单：[snapshot/env-keys-*.txt](./snapshot/)；主 compose 渲染键名：[snapshot/compose-base-keys-*.txt](./snapshot/)。

- **主 compose 与运行容器逐键、逐值相同**（比较只输出键名）：card 32、payment 32、channel-connector 27、risk-decision 27、acquiring 35、counterparty 23、api-gateway 24、ops-bff 14 个应用键。**唯一例外：Platform 运行中有 `DATAQUERY_HTTP_CALLERS_0`，主 compose 没有**（数据查询发布遗留）。
- **历次 `requirements/*/release*/compose-candidate*.yaml` 中这十个服务出现过的键**：Platform `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`、`PAYTECH_INSTANCE_ID`、5 个 `PLATFORM_SUPPORT_RABBITMQ_*`、`PLATFORM_SUPPORT_FILES_ENABLED`、`COMPENSATION_ENABLED`、`DATAQUERY_HTTP_CALLERS_0`、旧 `FILES_ONBOARDING_*`（已废弃，主 compose 与现网都没有）；Gateway `GATEWAY_CLIENT_SECURITY_AUDIENCE / ISSUER`；ops-bff `OPERATIONS_ACCOUNT / BILLING / LEDGER / RECONCILIATION_BASE_URL`。除 `DATAQUERY_HTTP_CALLERS_0` 外主 compose 都已包含，候选与回滚文件都显式带上它。
- Platform internal-auth：`PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID=platform-support-test`（主 compose 与现网一致）。其他服务 internal-auth 均为默认 OFF。
- 渲染核对（`compose.sh -f <文件> config`，只比较键名与值是否相等）：**候选**相对现网只多 payment `PAYMENT_CHANNEL_CALLBACK_BASE_URL`、channel-connector 5 个 `CHANNEL_CONNECTOR_RABBITMQ_*`、card `JAVA_TOOL_OPTIONS`（+ 信任库挂载），其余逐键相同；**回滚**与现网逐键相同。两份 `config --quiet` 都通过（镜像占位不影响渲染）。

### 2.3 RabbitMQ 与 XXL

- `/mor-test`：Payment 目前只是生产者（`payment.events`）；没有 `channel-connector.events`、没有 `payment.channel-connector.*`。现有订阅：`acquiring.payment.p0—p3`、`card.payment.p0—p3`（PaymentSucceeded；Card Handler 按 JsonNode 取字段，追加字段不影响）、`account.billing`、`client-master.compliance`、`ledger.account`、`reconciliation.billing`，全部 0 消息、各 1 消费者。
- XXL：执行器分组 payment-service(9)、card-service(10)、channel-connector-service(13) 心跳正常；任务 `compensationEventScan` 9 / 10 / 22（`*/30 * * * * ?`，启用），另有 card `cardOrphanInstrumentPurge`(11)、channel-connector `channelCostVersionActivation`(15)。**本次不改 XXL**：`PAY_RESULT_QUERY`、`CARD_BIN_LIBRARY_IMPORT`、`CHANNEL_PAYMENT_NOTIFIED` 都由已有的 `compensationEventScan` 扫描。

### 2.4 候选镜像与差异（卡 H，2026-10-22 16:50—17:16Z）

只做了本机构建、显式 tag 的本机镜像、对 mor-test 容器 `docker inspect` 与 `docker cp` 读出（JAR、`health-probe/`、ops-web `dist/` 与 `nginx.conf`）。没有向容器写入、没有停启替换、没有 compose up、没有 SQL / XXL、没有推送、没有 install、没有提交；`:development` 与任何现网 tag 未动。

#### 2.4.1 构建方法

| 项 | 内容 |
|---|---|
| 候选源 | `Code-MoR-card3ds-rel`（detached `73a2460d` + 两项需求暂存改动，工作区无未暂存 / 未跟踪文件）。构建前 `./mvnw -B -o -pl <9 个 app> -am clean test-compile` BUILD SUCCESS |
| Java | JDK 25、`~/.m3`、`./mvnw -B -o -DskipTests -pl <app> -am clean package`，不 install。镜像用 `test-environment/java/Dockerfile`（自 `e119cbd1` 未变），`health-probe/` 从运行容器读出（十个容器同一份，内容摘要 `4efb59ac…`）。镜像内 JAR、`health-probe/` 与构建产物逐字节相同；Entrypoint / User / WorkingDir / Env 与运行镜像相同 |
| 基线构建 | 独立 worktree（会话临时目录，`73a2460d` 一个、历史提交一个）。比较方法同 release-s2 第 1.2 节：`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目，第三方 JAR 整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引（本节条目数不计目录项，与 release-s2 的计数口径不同） |
| ops-web | `npm ci`（按 lockfile 干净安装）→ `npm run build` → `docker build`（`operations-console-web/Dockerfile`，`deploy/nginx.conf` 与现网逐字节相同）。**主工作区 `node_modules` 已与 lockfile 漂移**：直接用它构建 `1d39cd94` 与现网 `c7646648` 不一致（370 行 SHA 差异），`npm ci` 后逐字节一致（0 差异）；今后 ops-web 发布须先 `npm ci` |

#### 2.4.2 基线证明（N8）：现网 JAR ↔ 基线构建

| 容器 | 现网 JAR | 与 `73a2460d` 构建 | 确定的现网源码基线 |
|---|---|---|---|
| risk-decision-service | `3b2738ee…` | **0 差异**（1,371 项） | `91333d28`（10-18 messaging 重试合入）构建，0 差异；`91333d28..73a2460d` 对 Risk 无类变化 |
| payment-service | `668bc133…` | 应用类 0 差异；库差异：+125、~9 | **`91333d28`，0 差异**（858 项）。**现网已含 messaging 连接恢复 `339db7b8` / `8d123532`**（第 0 节 N8 原文“随本次带上”不成立） |
| acquiring-service | `22593672…` | 应用类 0 差异；库差异：−90、+110、~10 | **`c661e9fc`，0 差异**（1,427 项） |
| counterparty-service | `fe1e3d9c…` | 应用类 0 差异；库差异：−3、~10 | **`c661e9fc`，0 差异**（527 项） |
| platform-support-service | `2d12967f…` | **0 差异**（2,690 项） | `73a2460d` |
| card-service | `192f4a2c…` | 应用类 0 差异；`paytech-messaging` ~2 | **`4b5426e5`，0 差异**（661 项）；`4b5426e5..edabfb46` 对 card 无类变化 |
| channel-connector-service | `8c9db543…` | **0 差异**（1,196 项） | `73a2460d`（含 CYBS `3a4574c7`） |
| api-gateway | `1054e867…` | 应用类 0 差异；`paytech-platform-support-api` −90、+110、~1 | **`e119cbd1`，0 差异**（838 项） |
| operations-console-bff | `3d10e562…` | **0 差异**（2,458 项） | `73a2460d` |
| operations-console-web | 阶段 6 为 `7551dc3b` | — | **N6 校验通过：** `1d39cd94` + 按钮布局 6 文件，`dist/` 188 个文件与 `7551dc3b` 逐字节一致，`nginx.conf` 一致。**但现网已变（见 2.4.4）** |

#### 2.4.3 候选相对现网带上的非本需求改动（N8）

九个 Java 服务的**应用代码**（`BOOT-INF/classes`）在现网基线与 `73a2460d` 之间全部 0 差异；带上的只有下列共享库 / 合同库变化。所有服务的 `src/main/resources` 未因这些提交变化，不需要 DDL、数据或新环境变量。

| 库 | 提交 | 内容 | 涉及服务（现网尚无） | 影响 |
|---|---|---|---|---|
| `paytech-http-client-support`（6 类） | `beb21618` | HTTP 客户端 Owner Problem detail 显式开关 | payment、acquiring、counterparty | 默认关闭，这三个服务未改用，行为不变 |
| `paytech-messaging`（`catalog/events.yaml`、`YamlEventCatalog`） | `080909fb`、`8b7b4e7a`、`f057ce94` / `975a848e`、`2f5f37f7`、`9a209e6f`、`1d39cd94` | 事件目录：`EconomicFactRecorded`→`BillingFactRecorded`、`BalanceMoved`→`AccountPosted`，新增 `AgreementStatusChanged`、`CommercialTermVersionActivated`、`ActualChannelCostConfirmed`、`ChannelBatchReconciled`、`ChannelSettlementConfirmed`、`ReconAdjustmentApproved`、`WorkflowCallbackRaised`；域白名单去掉 `workflow-approval` | payment、acquiring（全部）；card（只有 `1d39cd94` 的 `WorkflowCallbackRaised` 与白名单） | 这三个服务不生产也不消费这些事件；目录在启动时加载校验。mor-test 上这些生产方早已是新目录，三者换新后目录与生产方一致 |
| `paytech-card-api`（`CardApiConstants`） | `c661e9fc`、`ecab136c`、`654cb14f` | `CONSUMER_SERVICE`→`COUNTERPARTY_SERVICE`，新增 `CHANNEL_CONNECTOR_SERVICE`，注释改写 | payment、acquiring、counterparty | 编译期常量；mor-test card 现网（`4b5426e5`）已用新常量 |
| `paytech-channel-connector-api`（+125 类） | `8b7b4e7a`、`5f12cf3e`、`ecab136c`、`654cb14f` | 渠道 / 账号 / MID / 能力 / 成本方案 / 标准 API 运营接口与 DTO | payment | 只增加合同类，payment 不调用这些接口 |
| `paytech-platform-support-api`（−90、+110、~1） | `5f12cf3e`、`13c02c87`、`ecab136c`、`9a209e6f`、`346a1822`、`1d39cd94`、`73a2460d`、`42879401` | 外部 API 迁出（−）、数据查询 / 文件存储 / 工作流 / 入驻文件第二段 DTO（+），`CurrencySpecResponseBody` 加 `version`；rel2 起另含 `WorkflowAttachmentApi` 的可选 `disposition` 参数 | acquiring、api-gateway | 两者不调用这些接口，也不读币种规格；Platform 现网已是 `73a2460d` |
| `paytech-client-master-api`（−3、~3） | `73a2460d` | 删除 `ClientFileTarget*`，`ClientDocumentModels` 改 `fileAddress` | counterparty | counterparty 只用已存卡设置客户端，不用文档 / 文件目标接口；client-master 现网已是第二段 |

结论：除本需求外，没有带上任何应用行为变化；风险限于上表合同库与目录，均无调用方。第 0 节 N8 原文列的 messaging 连接恢复已在现网，不再属于“顺带上线”。

#### 2.4.4 ops-web 现网已变（N6 / N9，已决：B）

- 16:44:01Z 现网 ops-web 被换成 `sha256:5a9eb12d…`（tag `dataquery-ux-20261022a`，code-mor-55，见 `requirements/20261022/platform-support-数据查询服务/release-ux/compose-candidate.yaml`）= `7551dc3b` + 数据查询页面体验（主工作区未提交的 10 个文件：`src/dataQuery/` 下 `DeclarationPanel.tsx`、`TemplateEditor.tsx`、`TrialPanel.tsx`、`VersionDrawer.tsx`、`dataQuery.css`、`templateModel.ts`、未跟踪 `templateSync.ts` / `templateSync.test.ts`，以及 `src/pages/DataSources.tsx`、`QueryTemplates.tsx`）。已复现：`1d39cd94` + 6 + 这 10 个文件构建，188 个文件与 `5a9eb12d` 逐字节一致。
- 按任务卡构建的 A 会回退这批页面；主会话裁决采用 B（已填入 `compose-candidate.yaml`）。B 的源码 `tsc --noEmit` 通过、`npm test` 1342 / 1342（测试读取 rel 的 `paytech/` 合同源文件）：

| 方案 | 镜像 | 构建源 | 去哈希后与现网对应镜像的差异 |
|---|---|---|---|
| A（任务卡） | `sha256:99432f7709cbf51399814ecd49dad1fc8c2da8caf9e2c89a44f6ec88079ffd5a`（`card3ds-20261022`） | rel + 按钮布局 6 文件 | 相对 `7551dc3b`：新增 `CardBinLibrary.js/.css`；`pageCapabilities`、`index`（路由表）、`capabilityConditions`（S5 修复）变化；因 `CardBinLibrary` 引用 `dataQuery/DataQueryParts`、`dataQueryCommon`、`dataQuery.css`，数据查询公共块重新切分（新 `templateModel` 块，`dataQuery`、`DataSources`、`QueryTemplates`、`TemplateEditor` 的块导入变化）。**相对现网 `5a9eb12d` 另会回退 `dataQuery.css` 等数据查询页面体验** |
| **B（采用）** | `sha256:9cefde8ec6a763bdd3ef2e8af04aca4fb3fcabd3be586ddb80a9274d425c802b`（`card3ds-20261022-dq`） | A + 上述 10 个数据查询文件 | 相对现网 `5a9eb12d`：差异集合与 A 相对 `7551dc3b` 相同（188 = 185 + 3 个新块），不回退任何现网内容 |

- B 的附带影响：BIN 页面引用 `dataQuery.css`，B 下用的是数据查询体验调整后的样式，阶段 5 验收的是旧样式；选 B 时阶段 7 验收须看一眼 BIN 页面布局。两种方案都与 rel 的 ops-web 改动无文件重叠。
- 回滚文件的 ops-web 已改指现网 `5a9eb12d`（原指 `7551dc3b` 会回退数据查询页面）。

#### 2.4.5 候选镜像（tag `card3ds-20261022`）与 Gateway 回滚镜像（N1）

| 服务 | 候选镜像 | JAR SHA-256 前 16 位 | 相对 `73a2460d` 基线构建的差异（全部属于两项需求） |
|---|---|---|---|
| risk-decision-service | `sha256:d69ca678416aaceb746d3fac0933e0e138c6eb36441c6e8ea7e75804d0bca523` | `3b0195e5267cf692` | ~17（risk-decision-core 16、risk-decision-api 1） |
| payment-service | `sha256:4cf16f15d7840f4027f79c1a743f87d2325e57d4d37df558606085ef9c5e8356` | `c452c35e0b9e060f` | +28、−2、~48（payment-core / api、card-api、channel-connector-api、risk-decision-api、messaging 目录、应用 3） |
| acquiring-service | `sha256:3958983e2fa397ccca4415b9b373f24dd3b248fffb96752ca0e723d3cedacb21`（`card3ds-20261022b`，rel2；原 `b5e57643…` 作废） | `68a30b6cbd287210` | +18、~29 |
| counterparty-service | `sha256:6586bd73b9223c92186978fe457540272c34ee8c9c7772526b4cd867c6de4a81` | `c8fd57787d79e61b` | +14、~8 |
| platform-support-service | `sha256:96da579bb45a88f7a4639a75961ed1dd85b1858e6bdca77085fd7f7073b396c4`（`card3ds-20261022b`，rel2；原 `60175851…` 作废） | `8f35483745704c94` | 相对 `42879401` 构建 +12、~4（与原 rel 候选相对 `73a2460d` 的差异集合相同） |
| card-service | `sha256:c39a39908a69415a3f1e512f0f0b3767a2775e1e8d16aa1664afa7b25b233465`（`card3ds-20261022b`，rel2；原 `5f308086…` 作废） | `221dd930caf4a95e` | +437、~12（新依赖 platform-support-api 359、http-client-support 12；card-core +48） |
| channel-connector-service | `sha256:23679d451fe44dfce08426777b5ec7cbc73a60253c604f149dfc48291c3890aa` | `9cb23e8161abc430` | +105、~22（新依赖 messaging / messaging-api 与 spring-amqp、spring-rabbit、amqp-client、spring-messaging、spring-boot-amqp、jackson-dataformat-yaml、snakeyaml-engine） |
| api-gateway | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4`（`card3ds-20261022b`，rel2；原 `bc794f07…` 作废） | `fc425b2e5eea0856` | +4（应用 1、platform-support-api 3） |
| operations-console-bff | `sha256:9c382db3d4985e0c1d8079d55ef36e3df93e535d65e277390879c9c8d0b750fa`（`card3ds-20261022b`，rel2；原 `68fc66f1…` 作废） | `9a310cf3af3e53d3` | 相对 `42879401` 构建 +51、~9（同上） |
| operations-console-web | `sha256:8bdbdd443f956e4e8548e41882cda15f07aa5d57919d7eed3fc76ac25b87ae66`（`card3ds-20261022b`，方案 B 基于 rel2；原 `9cefde8e…` 作废） | — | 见 2.4.4、2.4.6 |
| **api-gateway 回滚（N1）** | `sha256:da54e23c2b17c0e49dc11a9393ca2987489ace8273f03c7037976f17c97f6937`（`rollback-gateway-e119cbd1`） | `1e05b91f4b37c662` | 从 `e119cbd1` 构建，与运行容器 JAR 逐条目 **0 差异**（838 项）；镜像内 JAR 与构建产物逐字节相同 |

`compose-candidate.yaml` 与 `compose-rollback.yaml` 已填入上述 ID，两份 `./test-environment/compose.sh -f <文件> config --quiet` 通过。

#### 2.4.6 基线改为 `42879401`（17:18Z 工作流附件内嵌预览发布之后）

code-mor-ae 于 17:18Z 发布 main `42879401`（父 `5e72174b` ← `73a2460d`；9 个 Java 文件与 ops-web 4 个文件，加 1 行 Platform `query_param`），现网 platform `ac0b4845…`、ops-bff `798d5e4c…`、ops-web `11f1d24e…`。按 rel 构建的这三个候选会回退其 `disposition` 功能，因此重建：

| 项 | 结果 |
|---|---|
| 新集成目录 | `/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-rel2`（detached `42879401`），把 rel 相对 `73a2460d` 的暂存差异（368 个文件）`git apply --3way --index` 进去，未提交。唯一冲突：`platform-support.pdma.json` 一行 `programCode`（两边都删 `t_file_owner_ref`，rel 另加 `t_workflow_verification`、`t_workflow_transfer`），取合并后的一行，JSON 校验通过。两边都改的另一文件 `platform-support-initial-data.sql` 自动合并，`127030 disposition` 行与本需求块 132 都在 |
| 全 Reactor `test-compile` | `./mvnw -B -o clean test-compile` BUILD SUCCESS（118 个模块） |
| 基线证明 | `42879401` 构建的 platform、ops-bff 与现网 `ac0b4845`、`798d5e4c` 的 JAR 逐条目 **0 差异**（2,690 / 2,458 项）。ops-web：主工作区 16 个未提交文件与上次构建所用内容逐字节相同，`42879401` 没有提交其中任何一个；`42879401` + 16 文件构建的 `dist/` 188 个文件与现网 `11f1d24e` **逐字节一致**，`nginx.conf` 一致 |
| 候选差异 | rel2 platform / ops-bff 相对现网的差异条目集合与 rel 候选相对 `73a2460d` 的完全相同（+12 ~4 / +51 ~9）；ops-web 相对现网去哈希后差异与 2.4.4 相同（新增 `CardBinLibrary.js/.css`、`templateModel` 块，`pageCapabilities`、`index`、`capabilityConditions` 与数据查询公共块重新切分） |
| 测试 | ops-web（rel2 + 16 文件，`npm ci`）：`tsc --noEmit` 通过，`npm test` 1344 / 1344，`npm run build` 通过。ops-bff：`-pl paytech-operations-console-bff-test -am test`，ops-bff 测试模块 2236 / 0 失败（上游 Foundation 模块同时运行，全部通过）。platform-support（主会话授权窗口，本机 `mor_platform` 13306，17:42—17:51Z）：`-pl paytech-platform-support-test -am test -Dtest='com/paytech/platform/**/*,!**/manual/**'`，测试模块 803 / 0、workflow 64 / 0、data-query 48 / 0，BUILD SUCCESS。rel2 是新 worktree，没有被 git 忽略的本机 MinIO 证书，测试期间临时把 `local-environment/minio/certs` 软链到主工作区证书，结束后删除（首次 17:34Z 因此失败 24 项，非代码问题）。payment / acquiring 测试本轮未跑（其他会话占用，主会话指示） |
| 镜像核对 | 三个镜像内 JAR / `dist/` / `nginx.conf` 与构建产物逐字节相同，`health-probe/` 同前 |
| 其余七个服务 | rel2 与 rel 构建比较：risk-decision、payment、counterparty、channel-connector **0 差异**，沿用原 `card3ds-20261022` 镜像；acquiring、card、api-gateway 各差 1 项 `paytech-platform-support-api` 的 `WorkflowAttachmentApi.class`（`42879401` 给 #28 加可选 `disposition` 参数，三者不调用），按主会话决定（镜像源码与最终提交一致）从 rel2 重建为 `card3ds-20261022b`，镜像内 JAR 与构建产物逐字节相同 |

旧候选 `60175851…`、`68fc66f1…`、`9cefde8e…`、`99432f77…`、`b5e57643…`、`5f308086…`、`bc794f07…` 只留在本机，不得再用。

**环境变量核对：** 用 `compose.sh -f <文件> config` 渲染，platform（53 键）、ops-bff（14 键）、ops-web（0 键）在本包候选文件、回滚文件与工作流发布的 `requirements/20261021/platform-support-工作流/release-inline/compose-candidate.yaml` 三者之间逐键、逐值相同，且与三个运行容器的 `printenv` 一致（只比较是否相等，不输出值）。

**数据核对：** `42879401` 发布写入的 `mor_platform.t_gateway_route_query_param` 127030（路由 127432 `disposition`，路由版本 3）本包 SQL 不触碰；`sql/00-precheck.sql` 第 62 项、`sql/99-postcheck.sql` 第 38 项核对它仍在（卡 H 未对 mor-test 执行 SQL）。

## 3. 数据库只读预检结论

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22T16:17Z 首次执行，16:27Z 补第二段删除 DDL 核对后重跑，45 项全部等于期望，输出 [`sql/00-precheck-20261022.out`](./sql/00-precheck-20261022.out)。

| 核对项 | 结果 |
|---|---|
| 新增对象 | 全部不存在：`mor_card` BIN 4 表、`mor_payment` 消费表 2 张与 Token 8 列、`mor_risk_decision` 评估快照 3 列、`t_channel.callback_definition_json`、`mor_platform.t_country_region`。两处 MODIFY 的现有列定义与草案一致（`interaction_type` varchar(32) ascii_bin 可空；`target_type` varchar(64) NOT NULL utf8mb4；`received_at` datetime(6) NOT NULL），只改注释。表数 card 8 / payment 15 / channel_connector 28 / risk_decision 54 / platform 62 |
| 文件多存储第二段删除 DDL（主会话转告，已核实） | 已在 mor-test 执行：`mor_platform.t_file_upload` 已无 `owner_system / owner_type / owner_ref_id`，`t_file_owner_ref` 已删除（Platform 因此 62 表）；`mor_client_master.t_application_document` 已无 `file_id`，只有 `file_address`。见第 8.2、8.3 节回滚限制 |
| 块 132 / 133 | 两个 ID 段在 9 张表中全空；权限码、`CARD_BIN_CSV`、路由码 `channel-payment-callback`、`/api/channel-callbacks/…`、`…/bin-library…`、`/card-bin-library`、`channel-connector-service` Target 与 `CHANNEL_CALLBACK` 渠道均未占用。依赖的 ops-bff Target `900000000000000001`、父菜单「平台配置」`900000000000000562` ACTIVE。PLATFORM_ADMIN 版本 44；路由 526 条、521 ACTIVE（现有块到 131） |
| Risk | 220003 与 210002 版本 2 未占用；220002 摘要 `265ad285…` 与初始化数据一致；变量 93014—016 与三个变量码未占用（当前最大 93013）。支付点 ACTIVE 发布 release 11（点版本 220002，10-15 起），含 R-11（N5） |
| 渠道 | 见 N2、N3、N5。标准 API 版本 PAY 3 / PAY_QUERY 1 / REFUND 2 / CAPTURE 2；PAY 尚无 `payment.threeDsRequired`；code-mor-46 的 `refund.paymentChannelRef`、`capture.paymentChannelRef` 在（configure_3ds 只追加 PAY 字段，不会覆盖）。实现 PAY 的接入方：ALIPAY_PLUS、DLOCAL、I_CYBS_NI、I_MPGS_NI、PAYERMAX、TOSS_PAY（修改标准 API PAY 会复核它们的 PAY 流程，沙箱用例变“需重新验证”）。回调两表为空 |
| 交易数据 | `mor_payment` 意图 / 尝试 / 动作 / Token / 补偿事件全为 0；`mor_card` 补偿事件 0；`t_transaction_risk_assessment` 5 行。DDL 执行代价可忽略 |
| 文件存储 | 没有 `operations-console-bff` 写入路由；现有 `*` → `mor-platform-files`（`CLIENT_ONBOARDING_FORMAT`）与 `client-console-bff` → `mor-test-platform-files-k7q2`（N9）。工作流附件以 `platform-support` 为来源系统，不受 ops-bff 路由影响 |
| Card 下载对象存储 | Card 在 `mor-test-backend`，`minio.mor-test.localhost` 解析到 MinIO（172.30.73.28）；JDK 默认信任库不认 test-ca，候选给 Card 挂 Platform 同一信任库 |

`sql/99-postcheck.sql` 在发布前执行会因 BIN 表不存在而报错，属预期。

## 4. 替换服务、顺序与兼容性

顺序（与任务卡一致，同层内细化为单服务逐个替换）：

**Risk → Payment → acquiring → counterparty → Platform → Card → channel-connector → api-gateway → ops-bff → ops-web**

| 约束 | 原因（代码核对） |
|---|---|
| Risk 先于 Payment | Payment 的 Risk 客户端严格 JSON；新 Payment 发送 3 个卡属性输入，旧 Risk 合同不认 |
| Payment 先于 acquiring | 新 acquiring 轮询时调用 Payment 新接口 `…/submissions/{id}/refresh`，旧 Payment 返回 404 |
| Payment、acquiring、counterparty 先于 Card | 三者的 Card 客户端严格 JSON，须先认识 `cardAttributes` / `cardBrand`；新 Payment 收到旧 Card 缺字段的响应按“未识别”处理（BIN-D 裁决） |
| Platform 先于 Card | Card 导入读 Platform `GET /platform/v1/country-regions`、内部文件接口（带 `X-Caller-Service: card-service`，DUAL 识别） |
| **Payment 先于 channel-connector** | Payment 的 channel-connector 客户端严格 JSON（`withStrictJsonResponses`）；新 channel-connector 的候选与操作响应新增 `threeDsMode`、`nextActionType` 等字段，旧 Payment 会拒绝。反过来新 Payment + 旧 channel-connector 只在 3DS / QUERY 分支失败（mor-test 无交易） |
| channel-connector 先于回调定义 DML（`sql/21`） | 3DS technique 3.4 ④ |
| **api-gateway 之后才写块 133（`sql/23`）并激活** | 旧 Gateway（10-18）的快照校验认识 `CHANNEL_CALLBACK`，但没有安全链，激活后请求落 `denyAll`；按 technique 3.4 ⑥ 放在新 Gateway 之后 |
| ops-bff → ops-web 最后，块 132 在三者之后激活 | 菜单与路由激活前页面不可见 |
| MQ 拓扑顺序无关 | 订阅方声明拓扑时同时声明发布方交换机（`MessagingTopologyDeclarer.declareSubscription`）；新 Payment 启动即建 `channel-connector.events`、`payment.channel-connector.hash`、`p0—p3`、`dlq` |

**不可用窗口：** Platform 替换约 30—60 秒（运营台登录与全部运营接口、客户控制台登录与入驻文件）；api-gateway 替换约 30—60 秒（全部经 Nginx 的运营、客户、Client API 入口）。其余服务各自替换期间只影响自身接口（mor-test 无交易）。

## 5. 配置差异

| 服务 | 变化 | 说明 |
|---|---|---|
| payment | `+ PAYMENT_CHANNEL_CALLBACK_BASE_URL=""` | N4。MQ 消费（`messaging.consumer.service=payment`、`consumers.channel-connector.partitions=4`）与 `PAY_RESULT_QUERY` 策略写在 `application.yml` / Handler，复用已有 `PAYMENT_RABBITMQ_*`、`MOR_REDIS_*`（刷新限频键 TTL 5 秒为常量） |
| channel-connector | `+ CHANNEL_CONNECTOR_RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST`（`rabbitmq`、5672、`MOR_RABBITMQ_USER/PASSWORD`、`/mor-test`）；`depends_on rabbitmq` | 首次作为生产者（`channel-connector.events`，Publisher Confirm、`unroutable-policy: FAIL`）；**Broker 不可达时新版本起不来**。Web 日志 metadata-only 前缀 `/channel-callbacks/` 写在 yml |
| card | `+ JAVA_TOOL_OPTIONS`（信任库）、挂载 `./private/platform-truststore.p12:ro` | 下载 MinIO 预签名地址。补偿 lease 20m、`redis.coordination.enabled=true`（Redisson，复用 `MOR_REDIS_*`）、Platform 服务客户端写在 yml |
| platform-support | 保留 `DATAQUERY_HTTP_CALLERS_0=operations-console-bff`（主 compose 缺） | DUAL、`PAYTECH_INSTANCE_ID`、5 个 RabbitMQ 键、`PLATFORM_SUPPORT_FILES_ENABLED` 由主 compose 提供，候选不重复写 |
| risk-decision、acquiring、counterparty、api-gateway、ops-bff、ops-web | 只换镜像 | Gateway `CHANNEL_CALLBACK` 安全链写在代码；ops-bff 的 BIN 上传 metadata-only 前缀写在 yml |
| Nginx、Nacos、Vault、XXL | 不变 | mor-test 不加回调 location（N4） |

阶段 7 完成后另做主 compose 补丁（本包不改 `test-environment/compose.applications.yaml`）：payment 新键、channel-connector RabbitMQ 键与依赖、card 信任库、Platform `DATAQUERY_HTTP_CALLERS_0`。否则下次有人用主 compose 重建这些服务会丢配置（channel-connector 起不来、Card 下载失败）。

## 6. 执行步骤（阶段 7，另需用户授权；主会话执行）

记号：`R=requirements/20261022/payment-3DS全链路/release`，`O=$PWD/$R/compose-candidate.yaml`，`RB=$PWD/$R/compose-rollback.yaml`，`B` 为备份目录，`up <svc>` 指：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <svc>
```

每次替换后核对：healthy、重启 0、`.Image` 等于候选、`docker logs --since <本步开始> mor-test-<svc>-1 2>&1 | grep -c ' ERROR '` 为 0，环境变量键与第 5 节一致（`printenv | cut -d= -f1`）。任一步失败：停止，按第 8 节回滚已替换的服务。

| 步骤 | 动作 | 核对 |
|---|---|---|
| S0 协调 | 通知文件存储 / 公开桶、工作流附件、CYBS、数据查询等会话：本次会换上述十个服务。先把 `compose-candidate.yaml` 的 `<待构建>` 换成钉 sha256 的候选镜像，N1 回滚镜像就位，第 2.4 节填好，N2—N8 有结论 | 候选镜像存在、`config --quiet` 通过 |
| S1 复核基线 | 十个容器 `.Image` 等于第 2.1 节（Gateway 仍是 `4cf14912`），全部 healthy；`apply_sql.sh` 尚不可用时直接执行 `00-precheck.sql` | 46 项 ok=1：前 45 项与 `00-precheck-20261022.out` 相同，第 62 项（`42879401` 发布的 `disposition` 查询参数 127030 仍在、路由 127432 版本 3）为卡 H 新增 |
| S2 备份 | `B=$($R/scripts/backup.sh)`：五库整库 dump、镜像 / JAR / 环境键名、precheck 输出；另 `docker exec mor-test-rabbitmq-1 rabbitmqctl export_definitions /tmp/defs.json` 后由主会话取回到 `$B`（可选） | `$B/SHA256SUMS`；`$B/00-precheck.out` 同 S1 |
| S3 兼容旧镜像的 DDL / DML | 依次：`apply_sql.sh $B risk` → `payment-token` → `payment-consumed` → `cc-ddl` → `platform-country-ddl` → `platform-country-seed` → `card-ddl` → `card-seed` → `register132` | 各文件末尾核对；旧镜像不读新列、新表，休眠注册行不进快照。Risk 文件注明旧 Risk 加列后可运行、220002 的生效发布不受影响 |
| S4 Risk | `up risk-decision-service` | 评估接口健康；生效发布仍为 release 11 |
| S5 Payment | `up payment-service` | RabbitMQ：`channel-connector.events`（fanout）、`payment.channel-connector.hash`、`payment.channel-connector.p0—p3`（各 1 消费者）、`.dlq`、`.dlx` 出现；XXL `payment-service` 心跳 30 秒内刷新 |
| S6 acquiring | `up acquiring-service` | — |
| S7 counterparty | `up counterparty-service` | — |
| S8 Platform（窗口） | `up platform-support-service`，然后 `apply_sql.sh $B platform-scan-policy` | DUAL、`PAYTECH_INSTANCE_ID`、`DATAQUERY_HTTP_CALLERS_0`、5 个 RabbitMQ 键都在；运营登录、Gateway 快照刷新正常 |
| S9 Card | `up card-service` | `JAVA_TOOL_OPTIONS` 与挂载存在；启动日志无 Redisson / 补偿 lease 校验错误；XXL `card-service` 心跳 |
| S10 channel-connector | `up channel-connector-service`，然后 `apply_sql.sh $B cc-callback-def` | AMQP 连接 `/mor-test running`；`channel-connector.events` 已绑定 `payment.channel-connector.hash`；DML 输出 1 行 `PAYERMAX`（N3） |
| S11 api-gateway（窗口） | `up api-gateway`，然后 `apply_sql.sh $B register133` | Gateway 日志无快照拒绝；运营台、客户控制台、Client API 健康 |
| S12 ops-bff | `up operations-console-bff` | — |
| S13 ops-web | `up operations-console-web` | 按钮布局仍在（N6） |
| S14 存储写入路由 | 运营台「存储管理」新建写入路由：来源系统 `operations-console-bff` → 桶 `mor-platform-files`（238885048240128000）、扫描策略 `CARD_BIN_CSV`、`maxUploadSeconds=900`（技术方案 3.4 / 3.5.3，不走 SQL） | 第 99 项 36 |
| S15 激活 | `activate132-dry` → `activate132`（期望 2 / 2 / 9 / 1，角色版本 45）；`activate133-dry` → `activate133`（期望 1）；等 Gateway 快照刷新（≤60 秒）；`apply_sql.sh $B postcheck` | 99-postcheck 全部 ok=1（路由 536 / 531 ACTIVE；Platform 63 表） |
| S16 渠道配置（N2 选 a 时） | 运营台建 PAYERMAX 账号 / MID / 能力；准备私有运行目录 `RT`（0700，`platform.json` = `{"port": 0, "admin": "operator@example.test", "password": "<access.md>"}`、`web.json` = `{"origin": "http://localhost:28080"}`，不入库）；`E2E_RUNTIME=$RT python3 -I requirements/20261014/payment-PayerMax卡直付对接/assets/harness/configure_card.py PAYERMAX`，再 `E2E_RUNTIME=$RT python3 -I requirements/20261022/payment-3DS全链路/assets/harness/configure_3ds.py PAYERMAX`，第二次运行应无修改 | `$RT/configure-3ds-PAYERMAX.json`；标准 API PAY 版本 4 且含 `payment.threeDsRequired`；REFUND / CAPTURE 的 `paymentChannelRef` 仍在；`PAY_QUERY` 输出 `CLOSED` / `NOT_FOUND` |
| S17 验收 | 按 [acceptance-plan.md](./acceptance-plan.md) | 第 11 节记录 |
| S18 记录 | 填第 11 节；两项需求 `delivery.md` 补环境操作记录；CURRENT_IMPLEMENTATION 台账；通知各会话新镜像与基线（今后这十个服务的候选须基于本次构建源）；主 compose 补丁（第 5 节末） | — |

## 7. 激活与环境配置说明

- **块 132**：运营页面「平台配置 → 卡 BIN 库」权限 2、授权 2、菜单 2、路由 9（休眠写入，S15 激活，角色版本 +1 让会话重算导航；后者沿用工作流发布先例）。
- **块 133**：`POST /api/channel-callbacks/v1/{channelCode}/{channelAccountId}` → channel-connector，Gateway 不认证、channel-connector 验签；Target ACTIVE、路由休眠，S15 激活。
- **Gateway 快照**：Platform 编译、Gateway 每 5 秒拉取，激活后不需要额外“发布”动作，等待刷新即可（阶段 5：416 → 426 条）。
- **CSV 写入路由**：写入路由按来源系统划分，登记后 ops-bff 今后所有上传都用 `CARD_BIN_CSV`（只允许 text/csv）；目前 ops-bff 只有 BIN 上传，工作流附件来源系统是 `platform-support`，不受影响。按上传原因细分路由另立需求。
- **Nginx 与回调入口**：mor-test 没有公网入口，`nginx.conf` 只有 OPERATIONS / CLIENT / CLIENT_API 三个入口，不加 `CHANNEL_CALLBACK` location。AC-07 真实 PayerMax 通知为遗留（需要公网地址 + Nginx location 写 `X-Entry-Channel: CHANNEL_CALLBACK` + 覆盖 `X-Real-IP` + `PAYMENT_CHANNEL_CALLBACK_BASE_URL`）。
- **`configure_3ds.py`**：只做增量读改写，已是目标状态时不发起修改；会使所有实现 PAY 的接入方沙箱用例变“需重新验证”。`dynamic3DS` 需 PayerMax 为商户开通，未开通时的行为是 AC-12 要实测的内容。

## 8. 恢复方案

### 8.1 触发条件

任一替换后 healthy 失败或启动 ERROR；运营登录 / 导航 / 现有页面回归失败；Gateway 快照被拒；PaymentSucceeded 订阅方（acquiring、card）进 DLQ；channel-connector 无法连 Broker；验收发现阻断缺陷且不能在当天前修。

### 8.2 应用回退（不动数据）

1. **先停入口：** `apply_sql.sh $B deactivate-dry` → `deactivate`（块 133 路由 1、块 132 路由 9 / 菜单 2 / 授权 2，角色版本 +1）；等快照刷新。若已登记 S14 写入路由且要回退 Platform，在存储管理页删除它（旧 Platform 不认 text/csv 扫描）。
2. **查未完成的新补偿任务：** `mor_payment.t_compensation_event` 中 `PAY_RESULT_QUERY`、`mor_card` 中 `CARD_BIN_LIBRARY_IMPORT`、`mor_channel_connector` 中 `CHANNEL_PAYMENT_NOTIFIED` 未完成的条数须为 0。旧镜像没有对应 Handler，留下的任务会反复失败；有未完成的 3DS 交易时先人工确认其渠道结果。
3. **镜像按发布逆序回退：** `./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <svc>`，顺序 ops-web → ops-bff → api-gateway（须 N1 重建镜像）→ **channel-connector（必须先于 Payment，旧 Payment 严格拒绝新响应字段）** → card → platform → counterparty → acquiring → payment → risk-decision。回滚文件把本次新增键置空，渲染与现网逐键相同。可以只回退出问题的服务及其之后的服务，前提是不违反第 4 节的依赖。
   **Platform 只能回退到现网 `ac0b4845…`（第二段 + `42879401` 工作流附件内嵌预览；下限是第二段版本 `7cb30402…`）**：第二段删除 DDL 已在 mor-test 执行（`t_file_upload` 的 `owner_*` 三列、`t_file_owner_ref` 已删；CCT `t_application_document.file_id` 已删），第二段之前的任何 Platform 镜像（含工作流 `b9301714…`）都会因缺列失败；同理 client-master 不能回到 `e3fded0b…` 之前。本包不替换 client-master，回滚文件中的 Platform 目标已是 `ac0b4845…`，不得改成早于 `7cb30402…` 的镜像。
4. **MQ：** 回退 Payment 后 `payment.channel-connector.p0—p3` 没有消费者，回退 channel-connector 后不再有新消息；两者都回退后可删除 `payment.channel-connector.*`、`channel-connector.events`（`rabbitmqctl delete_queue / delete_exchange -p /mor-test`），保留也无害。

### 8.3 数据恢复与不可逆项

| 对象 | 回退方式 | 说明 |
|---|---|---|
| 新列、新表（Payment 8 列 + 2 表、Risk 3 列、`callback_definition_json`、Card 4 表、`t_country_region`） | **保留** | 旧镜像不读，兼容。删除（各文件头 DROP 语句）会丢失已冻结的卡属性快照与评估输入，**不可逆**，不建议 |
| 注册行（块 132 / 133）、扫描策略、国家区域种子 | 停用即可；确需移除用 `sql/91-rollback-platform-data.sql`（dry 后提交） | 前置：删除引用 `CARD_BIN_CSV` 的写入路由；BIN 导入产生的 Platform 文件与 MinIO 对象保留 |
| Card 种子、回调定义、Risk 220003 / 变量 | `sql/92-rollback-service-dml.sql` 三段（注释状态，逐段人工执行） | **Risk 220003 一旦被任何执行发布引用就不能删；BIN 库新版本与激活记录、运营导入数据不能用种子回退语句清除** |
| 标准 API PAY、PayerMax 原子 API 与流程（S16） | 镜像回退**不会**还原。按 `configure_3ds` README：经运营台先删流程中的映射、再删原子 API 字段、最后删标准 API 字段 | `configure_card.py` 会删除收银台版 PAY 流程，恢复只能重跑接入 harness 的 `configure.py`（收银台版）——**实质不可逆** |
| 发布后新写入的业务数据（验收交易、BIN 版本、回调行、评估快照） | 整库恢复会一并丢失发布期间其他会话的写入 | 只在结构损坏时按表从 `$B/<db>-full.sql` 恢复，**不整库覆盖** |
| 文件多存储第二段删除 DDL（他人已执行，非本包） | 不在本包范围，不可逆 | 决定了 Platform / client-master 的回滚下限是第二段版本；如需更早版本，只能先按 release-s2 的 02 / 03 文件头回退语句与其备份恢复结构和数据，本包不做 |
| 外部副作用 | 无法回退 | PayerMax UAT 订单（沙箱）；已发出的 PaymentSucceeded v2（订阅方容忍追加字段） |

## 9. 验收

见 [acceptance-plan.md](./acceptance-plan.md)：3DS AC-01—AC-04、AC-07（反向）、AC-11、AC-12 及可选 AC-06 在 mor-test 验证，其余沿用阶段 5；BIN AC-01—AC-07、AC-10—AC-12 与并发在 mor-test 验证，AC-08 规则命中与 AC-09 沿用阶段 5（N7）。合成数据保留清单在其第 5 节。

## 10. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) | 候选覆盖文件（镜像 `<待构建>`，环境完整） |
| [compose-rollback.yaml](./compose-rollback.yaml) | 回滚覆盖文件（钉现网 sha256；Gateway 占位待 N1） |
| [sql/00-precheck.sql](./sql/00-precheck.sql) / [00-precheck-20261022.out](./sql/00-precheck-20261022.out) | 执行前只读核对（45 项，含第二段删除 DDL 已执行的确认）与阶段 6 结果 |
| `sql/10`—`sql/17` | S3 兼容旧镜像的 DDL / DML（risk、payment ×2、channel-connector DDL、platform 国家区域 DDL / 种子、card DDL / 种子） |
| `sql/20`—`sql/23` | 部署中执行：扫描策略（新 Platform 后）、回调定义（新 channel-connector 后）、块 132 注册（S3）、块 133 注册（新 Gateway 后） |
| `sql/30`、`sql/31` | 块 132 / 133 激活（无 COMMIT，带行数 guard） |
| `sql/90`、`sql/91`、`sql/92` | 停用入口、移除 Platform 数据、其余三库 DML 回退（注释） |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 执行后只读核对 |
| [scripts/backup.sh](./scripts/backup.sh)、[scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份与 SQL 包装（root 口令只经 `MYSQL_PWD`；未执行） |
| [acceptance-plan.md](./acceptance-plan.md) | 验收计划 |
| [snapshot/](./snapshot/) | 阶段 6 快照：容器与镜像、JAR 哈希、环境键名、主 compose 键名 |

SQL 副本来源：`10` 为服务仓库 `paytech/risk-decision/docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql`（需求 db 注明以它为唯一执行来源；与需求 DDL + DML 去注释后只差一行 `SET NAMES`；**取自卡 F 未提交的集成目录，卡 F 提交后须复核同名文件 SHA-256 `3d8deb49…`**）；其余取自两项需求的 `db/`（未改业务语句，每个副本首行记来源与 SHA-256；`15`、`20`、`22` 为 BIN Platform DML 的三段切分）。

## 11. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| | | |

### 11.1 卡 I：S14、S16、S17（接口层配置与验收）

脚本与结果在 [acceptance/](./acceptance/)：`mt.py`（公共辅助，E2E_TARGET=test）、`accept_3ds.py` → `accept-3ds.json`、`accept_bin.py` → `accept-bin.json`、`accept_bin_screens.mjs` → `screens/s7-*.png`、`accept_pages.mjs` → `screens/reg-*.png`、`accept_regression.py` → `accept-regression.json`、`accept_leak_scan.py` → `accept-leak-scan.json`、`s14-storage-route.json`、`workflow-inline/`。运营口令只在内存，未写文件。

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 18:09:59 | S14 存储写入路由 | `POST /storage-admin/v1/routes`（新 Platform 要求 `reason`，首次 422 后补字段）→ 200，objectId **239087906591948800**：`operations-console-bff` → MINIO / `mor-platform-files` / `CARD_BIN_CSV` / 900 秒。`*` 与 `client-console-bff`（S3_TEST）路由未改 |
| 18:10 | S16 渠道配置 | **阻塞**：建 PAYERMAX 技术账号需要 UAT `merchantPrivateKey`；mor-test 只有沙箱验证账号 UAT_MAIN（私钥在 Vault），运营接口不能复制凭据，仓库与 `test-environment/private` 无 UAT 账号文件。已报告主会话，主会话随后在私有 0700 目录提供账号文件（用户提供，keyVersion 补为 1） |
| 18:11 | S7-01（阻断） | Payment 未配置 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW`（默认 PT0S），申请卡工具 400「支付工具申请窗口尚未配置」。主会话 18:14:42 重建 payment-service 加键后恢复 |
| 18:13—18:14 | BIN AC-01 / AC-05 / 并发 | v2（good，8 行）VALIDATING → VALID 5.4 秒，重复提交返回原版本，未激活时查询仍为 v1；v3（9 行错误：重复前缀 2、非法卡组织、缺发卡国、XX 未登记、类卡号前缀不回显、字母、列数）INVALID，v4 表头错误为文件级失败；v3 / v4 激活 400；v5（good2）与 v2 并发激活 → v5 200、v2 409「当前资源正在执行」；过期期望版本 → 409「生效版本已被他人修改」 |
| 18:14—18:15 | BIN AC-12 + 布局（N6） | 无头 Chromium 逐屏：摘要、BIN 查询（8 位命中 / 未识别 / 非数字即时提示）、5 个状态版本详情、导入弹窗、激活 / 回切确认弹窗、状态筛选、数据查询「数据源」「查询模板」：**控制台错误 0、HTTP ≥ 400 为 0（无 429）**，各屏无横向溢出、弹窗与按钮无越界（`screens/s7-browser-log.json`） |
| 18:15:05 | 3DS AC-07（反向，预检） | ingress 网络探针直连 `api-gateway:18080`（Gateway 只绑定 ingress，`GATEWAY_BIND_ADDRESS`）：Gateway 放行，channel-connector 对不存在的账号返回 404「渠道通知入口不可用」，无回调行。签名拒绝（401）需 S16 建账号后重做 |
| 18:16 | 工作流内嵌预览回归 | `RUN=C inline_mortest.mjs`：API 5 项与浏览器 4 项全部 PASS，网络错误 0，实例已终止；状态与截图复制到 `acceptance/workflow-inline/`，原目录的已提交状态文件与截图已 `git restore` |
| 18:17—18:18 | 运营台回归 | 工作流 4 页、存储管理、数据查询 2 页、渠道、MID、标准 API、Gateway 配置、BIN 页面均可打开；标准 API REFUND / CAPTURE 仍含 `paymentChannelRef`（v2）；PAY 仍为 v3（S16 未执行）。**S7-02（低）**：流程定义页 4 张卡片并发取详情，2 个 429，两张卡片缩略图空白（与 S5-01 同类，工作流页面既有行为，非本需求改动） |
| 18:27—18:29 | CYBS / MPGS 沙箱回归 | I_CYBS_NI `SBX_AUTH_CAPTURE`（run 239090632642109440）、I_MPGS_NI `SBX_AUTH_CAPTURE_CHAIN`（run 239091405828497408）均 **PASSED**，全部步骤 PASSED |
| 18:28:06 | 3DS AC-02 首次 | PAY_RISK FAILED：Risk 重建后的第一次请求冷启动约 2.3 秒，超过 Payment 风控超时，重试仍超时，失败关闭（行为正确）。记 **S7-03（观察）**：服务重建后首笔支付可能因 Risk 冷启动失败关闭 |
| 18:28:53 | 3DS AC-02 | **通过**：DEMO_NI 600.00 USD → PAY_RISK SUCCEEDED `THREE_DS_REQUIRED`（R-11）→ PAY FAILED `THREE_DS_CHANNEL_UNAVAILABLE`，`t_channel_interaction` 0 → 0；Risk 快照 AE / VISA / CREDIT 冻结（BIN AC-08 三列） |
| 18:29 | BIN AC-02 / 03 / 04 / 06 | v5 生效：GB 测试卡 → VISA / CREDIT / PLATINUM / AE / GCC、版本 5、8 位；未识别 BIN → 属性全空、版本 5；回切 v1（ROLLBACK，操作人、previousVersionNo 5）→ 新工具 VISA / DEBIT / CLASSIC / GB、版本 1；回切后 v5 工具快照不变 |
| 18:29:50 | AC-11 预扫 | 十个服务日志、补偿表、BIN 错误明细、动作表、回调表：卡号 / `acsSecret=` / Luhn 卡号形串 0 命中（AC-12 后带跳转主机重扫） |
| 18:30 | 健康 | 十个服务 healthy、重启 0、17:55Z 以来 ` ERROR ` 0 |
| 18:31:44 | S16 账号 / MID / 能力 | `accept_s16.py setup`（账号文件经 `PMX_UAT_ACCOUNT_FILE` 只读入内存）：PAYERMAX 渠道 DRAFT → ACTIVE；技术账号 **239093383992287232**（AE001，ACTIVE，凭据入 Vault）；MID **239093386823442432**（`PMX-UAT-AE001-10221831`，ACTIVE）；能力 **239093387985264640**（CARD / * / * / USD / 5999：ACTION.PAY、AMOUNT 0.01—100000、SECURITY.3DS_MODE=SUPPORTED） |
| 18:31:58 | S16 `configure_card.py PAYERMAX` | 收银台版 PAY 流程已按版本删除（计划内，第 8.3 节不可逆项）；“改标准 API PAY”一步 **400 业务校验未通过**：mor-test PAY v3 为 APM 版超集（另有 `language`、`expireSeconds`、`paymentMethodType`，`card` 可选，`subject` 必填），被 ALIPAY_PLUS、DLOCAL、I_CYBS_NI、I_MPGS_NI、TOSS_PAY 实现，harness 定义会删字段、把 card 改必填。**S7-04**：改为只执行 harness 的接入方部分 `configure_card(token, 'PAYERMAX')`（`acceptance/run_configure_card_only.py`）：原子 API ORDER_AND_PAY 更新、卡直付 PAY 流程创建 |
| 18:32—18:33 | S16 `configure_3ds.py PAYERMAX` ×2 | 第一次：标准 API PAY 追加 `payment.threeDsRequired`（→ v4）、ORDER_AND_PAY 追加 `dynamic3DS`、PAY 流程更新；PAY_QUERY 已输出 CLOSED / NOT_FOUND，未改。第二次：全部 `false`（无修改）。核对：PAY v4 含 threeDsRequired、实现方 6 个；REFUND / CAPTURE v2 仍含 `paymentChannelRef`；PayerMax PAY 流程含 `PAYMENT_CARD` 与 `do3DS`。调用记录 `acceptance/configure-3ds-PAYERMAX.json`、`configure-card-PAYERMAX-only.json`（只含名称、路径、状态码） |
| 18:33:12 | 3DS AC-07（反向） | **通过**：随意签名的 PayerMax 形通知 → Gateway 放行 → channel-connector 401 `CHANNEL_CALLBACK_SIGNATURE_INVALID`；`t_channel_callback` 新增 1 行 REJECTED / REJECTED（账号 239093383992287232）；Payment 无 ChannelPaymentNotified |
| 18:33:21 | AC-12 首次 | 600.00 USD、PayerMax UAT 测试卡：PAY_RISK SUCCEEDED `THREE_DS_REQUIRED`，MID 冻结为 PMX-UAT；**PAY FAILED `VALIDATION_ERROR`（NOT_SENT，渠道请求 0）**。原因：mor-test PAY v4 的 `payment.subject` 必填，Payment `PayInput` 不传 subject——mor-test 上 Payment 发起的任何 PAY 都会如此（S7-04 续）。建议只把该字段改为可选（harness 目标定义即可选；PayerMax 流程缺省 `'Order ' + attemptId`），本卡执行该修改被权限拒绝，已交主会话决定 |
| 18:37 | S7-04 处理 | 用户确认后主会话执行 `acceptance/s7_04_subject_optional.py`：标准 API PAY v4 → **v5**，只把 `payment.subject` 改为可选（PUT 200，其余字段不变）。本卡核对：6 个 PAY 实现（ALIPAY_PLUS v2、DLOCAL v1、I_CYBS_NI v5、I_MPGS_NI v2、PAYERMAX v2、TOSS_PAY v1）`implementations/validate` 全部 valid。回退：改回 required=true |
| 18:38:03 | AC-12 发起 | 600.00 USD、PayerMax UAT 测试卡（attempt **239094961201565696**，intent 239094951181373440）：pay **202 PROCESSING + `THREE_DS_CHALLENGE`**，跳转主机 `cashier-n-uat.payermax.com`，交互 30 分钟过期；PAY_RISK `THREE_DS_REQUIRED`（R-11），PAY 动作 PROCESSING（渠道 PENDING），`PAY_RESULT_QUERY` 按退避重试（`PAY_RESULT_PENDING`）。→ **`dynamic3DS=do3DS` 在该 UAT 商户下返回挑战（D6 实测）**。跳转地址只发给主会话；结果文件曾短暂含跳转地址，已立即改写为只留主机名并修正 `scrub`，检索 0 残留 |
| 18:41:30 | AC-12 / AC-04 | 用户在浏览器完成 PayerMax ACS；**未调用刷新**，`PAY_RESULT_QUERY` 退避重试推进：PAY SUCCEEDED（渠道 SUCCESS）、Attempt SUCCEEDED（captured 600.00）、Intent SUCCEEDED，补偿任务清空。18:41:59 提交查询 200 `SUCCEEDED`、`interaction=null`。**AC-12、AC-04 通过** |
| 18:41 | AC-01 / AC-03 | AC-01：Risk 快照 `THREE_DS_REQUIRED`（R-11，600 > 500），动作只有 PAY_RISK、PAY（无 AUTHENTICATE），PayerMax 返回挑战即 `dynamic3DS=do3DS` 生效（渠道请求原文不可读，以挑战结果为证）。AC-03：`THREE_DS_CHALLENGE` 经 channel-connector → Payment → pay 响应 `nextAction.type` 如实返回、跳转地址存在且不落库。**通过**。AC-06（可选）未做：交易已终态，刷新不再查单 |
| 18:41 | 卡属性 | UAT 测试卡 BIN 不在种子 v1 号段：Token 卡属性全空、`bin_library_version_no=1`，Risk 三列为空（未识别按设计不阻断，BIN AC-04 在真实成功交易上复证） |
| 18:41:31 | PaymentSucceeded 订阅 | card：`card.payment.p0—p3` 消费、DLQ 0。acquiring：**拒绝进 DLQ**（`acquiring.payment.dlq` 1 条）——`ApplyPaymentSucceededProcessor` 找不到订单：探针直连 Payment 建 Intent（sourceId 为随机合成值，不经 acquiring 建单，验收计划第 1 节驱动方式），属预期，不是缺陷。阶段 7 不写 RabbitMQ，DLQ 消息保留（登记为合成残留）；AC-10（schemaVersion=2 卡属性字段）不读消息正文，沿用阶段 5 |
| 18:42:23 | AC-11 终扫 | 十个服务 17:55Z 以来日志、补偿表、BIN 错误明细、动作表、回调表：跳转主机 `cashier-n-uat.payermax.com`、`processApiV2`、跳转 `token` 参数值、`tradeToken=`、`payRequestNo=`、`acsSecret=`、测试卡号、Luhn 卡号形串 **全部 0 命中**；`acceptance/` 目录检索 0 残留。MQ 未建捕获队列（不写 RabbitMQ），正文检索不覆盖 |
| 18:42:33 | S16 收尾 | MID 239093386823442432 → SUSPENDED；能力 239093387985264640 → DISABLED（带 `reason` 时 400，去掉后 200）；账号保留 ACTIVE、PAYERMAX 渠道保留 ACTIVE |
| 18:42:34 | 健康 | 十个服务 healthy、重启 0；` ERROR ` 仅 acquiring 1 行（上行 DLQ，已解释） |

**卡 I 缺陷 / 观察汇总：** S7-01（阻断，Payment 缺工具申请窗口，主会话已修）；S7-02（低，流程定义页并发 429，既有）；S7-03（观察，Risk 冷启动超时失败关闭）；S7-04（配置，mor-test 标准 API PAY 为 APM 超集：harness 改为只配置接入方，`payment.subject` 改可选，PAY v5）。

**合成数据 / 残留（保留）：** `mor_payment` Intent 7 笔（AC-02 两笔、BIN 工具三笔、AC-12 两笔）及其 Token / Attempt / Action；Risk 评估快照；BIN v2—v5、导入错误明细、激活记录（已回切 v1）；4 个 CSV 文件对象；AC-07 REJECTED 回调行 1（不存在账号的预检未落行）；`acquiring.payment.dlq` 1 条；工作流 `WF_INLINE_20261022_C_*` 模板 / 定义（实例已终止）；PayerMax UAT 订单 2 笔（一笔 NOT_SENT 未到渠道、一笔 SUCCESS）；S14 写入路由（正式配置）；PAYERMAX 账号（ACTIVE）、MID（SUSPENDED）、能力（DISABLED）。私有运行目录 `s16-rt`（0700，含运营口令）在会话 scratchpad，未入库。
