# billing 支付事实自动登记：mor-test 发布清单

阶段 6（2026-10-23）。只做了只读核对、构建三个镜像和准备发布包，**没有改 mor-test 的任何容器、数据、队列或 XXL 登记**。阶段 7 由主会话按本清单执行（需用户授权），结果写第 8 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 billing、account、ledger、acquiring、payment 五个容器换成本需求镜像（五个候选均已构建；acquiring / payment 基于 bf 已提交的 `d3d39e40` + 本需求）。无 DDL；一条 DML（SQL-1：`mor_billing` 补 `AE_BUSINESS_DAYS` 工作日日历）；无新 XXL 任务。billing 新增四个上游 URL、acquiring 新增 `PAYTECH_INSTANCE_ID`，由覆盖文件带入。验收前在运营控制台为 DEMO_NI 建一个渠道成本方案（Q-2）。**

**阶段 6 发现四个缺口（第 3 节）。用户 2026-10-23 裁决：GAP-CAL → SQL-1 补日历（Q-1）；GAP-COST → 控制台建 DEMO_NI 成本方案 PROCESSING_FEE 0.5%（Q-2）；验收只做 PLATFORM 来源 APPLE_PAY 一笔（Q-3），GAP-ISSUER、GAP-PARTNER 记为需求 D / Partner 演示数据的前置（第 9 节）；合成数据保留为演示数据（Q-5）。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop 的 `mor-test`；库 `127.0.0.1:23306`；RabbitMQ 管理 `127.0.0.1:25673`（vhost `/mor-test`）；运营入口 `http://localhost:28080`。不是生产 |
| 源码 | 工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-billing-b`（detached，本需求未提交改动，`git status --short` 114 项）。billing / account / ledger 在 HEAD = main `22ede435` 时构建；随后主会话把 HEAD 挪到 main `d3d39e40`（bf `623916ac` 订单过期收尾 + 文档，MockTestBase 一处冲突已合并），payment / acquiring 在此基础上构建。`git log 22ede435..d3d39e40` 只改 acquiring / payment / card 与 PDManer 模型，不涉及 billing / account / ledger / foundation，三个先建的镜像不受影响（第 2.2、2.3 节） |
| 镜像（5 个） | billing（消费 `acquiring.events` 的 `OrderPaymentSucceeded`、AUTO PAYMENT 事实、四个上游客户端）；account（白名单 +2）；ledger（`GET /finance-entities/v1/by-party/{legalPartyId}`）；acquiring（发布 `OrderPaymentSucceeded`）；payment（`PaymentSucceeded` v2 末尾 +2 可空字段） |
| SQL | 无 DDL。SQL-1（DML，`mor_billing`，mor-test 与本机 local-environment 各执行一次，主会话另行授权）：`paytech/billing/docs/database/sql/changes/2026-10-23-business-calendar-ae.sql`，同一行同步进 `billing-initial-data.sql`（第 4 节）。只读核对 [sql/00-precheck.sql](./sql/00-precheck.sql)、[sql/10-verify-calendar-and-cost.sql](./sql/10-verify-calendar-and-cost.sql)。初始化 / 变更 SQL 不打进任何 JAR，**五个候选镜像不因 SQL-1 重建** |
| 运营数据 | channel-connector：DEMO_NI 渠道成本方案 `DEMO_NI_COST` + 版本 1（Q-2，第 5 节第 4 步），经 Maker-Checker 批准为 ACTIVE。commercial / reconciliation / ledger 数据不改 |
| Compose | 主工作区 `test-environment/compose*.yaml` 不改；用本目录 [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) |
| 不动 | commercial、channel-connector、reconciliation、platform、ops-bff、ops-web、Gateway 等其他服务的镜像与配置；MySQL / Redis / RabbitMQ / Nacos / XXL 容器；XXL 任务登记 |

## 2. 制品与决定

### 2.1 制品

| 服务 | 上一版（运行中，回滚目标） | 候选（tag `billing-b-20261023`，部署只用 ID） | 候选 JAR SHA-256 前缀 |
|---|---|---|---|
| billing-service | `sha256:90d7401abdd6…`（currency-20261021，JAR `72987591c0d3b3e0`） | `sha256:53450948e0225e3681930b55417526f2ae51ed35fc464276791cab7105e700b0` | `20869ac074e7b3ab` |
| account-service | `sha256:dfa6254cb889…`（chart3-20261020，JAR `6953cda5ca297818`） | `sha256:17831407811fefb3e747208d0bda1a4c5e0481e19ea4423ee98f28723cfa6284` | `f9bfd52ee103a6b7` |
| ledger-service | `sha256:fa21520def8c…`（currency-20261021，JAR `7d8d2466c872018d`） | `sha256:3819ff2809c7a7ff2a4e835221871b5b8bcf684a153bb7b30f408ca79de4d210` | `fb1b91d369724fe9` |
| acquiring-service | `sha256:55a644fd4618…`（bf expiry-20261023，JAR `de02f36bd0b0c128`） | `sha256:27c36f4e3a16e10fd72c14c7667786dd5cbc795d4b2b400b52388c72c22f0437` | `ec8487bd07a722f6` |
| payment-service | `sha256:f3c48d893729…`（bf expiry-20261023，JAR `621ff256dfc74c5c`） | `sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7` | `96a1c9d83176f64d` |

完整 64 位 JAR SHA-256 与逐条目差异：[jar-diff-20261023.txt](./jar-diff-20261023.txt)。运行镜像 ID 全值见 [compose-rollback.yaml](./compose-rollback.yaml)。

**构建（五个服务均已执行；billing / account / ledger 在 HEAD `22ede435`，payment / acquiring 在 HEAD `d3d39e40`）**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR-billing-b/paytech
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./mvnw -B -o -DskipTests -pl billing/paytech-billing-app,account/paytech-account-app,ledger/paytech-ledger-app -am clean package
JAVA_HOME=… ./mvnw -B -o -DskipTests -pl payment/paytech-payment-app,acquiring/paytech-acquiring-app -am clean package
# 每个服务一个构建上下文：app.jar = <module>/paytech-<module>-app/target/paytech-<module>-app-1.0.0-SNAPSHOT-exec.jar，
# health-probe/ = docker cp mor-test-<svc>-1:/app/health-probe（五个运行容器逐字节相同）
docker build -q -f test-environment/java/Dockerfile --build-arg JAR_FILE=app.jar -t "mor-test/<svc>:billing-b-20261023" <ctx>
```

核对：镜像内 `/app/application.jar` 的 SHA-256 与构建产物相同；Entrypoint / Cmd / User / WorkingDir / ExposedPorts / Env 与运行镜像相同；基础层（前 5 层）相同。未用 `scripts/build.sh`，未改 `:development` tag 与 `test-environment/artifacts`。`-am` 必须带上（第 9 节 R3）。

### 2.2 D1 基线复现（billing / account / ledger）

- `git log 22ede435..d3d39e40 -- paytech/billing paytech/account paytech/ledger paytech/foundation` **为空**（`32dbdd18` 只改 card / PDManer 模型 / 文档，`623916ac` / `d3d39e40` 只改 acquiring / payment / card / PDManer 与文档）。所以这三个服务以 `22ede435` 为基线构建的镜像与在 `d3d39e40` 上构建等价，不受其他会话影响。
- 用 `git archive 22ede435 paytech` 解到会话 scratchpad、同一命令构建（不动 git 元数据），三段比较（嵌套 JAR 逐条目 SHA-256）：

| 服务 | 运行 → 22ede435 基线（运行镜像缺的已提交内容） | 22ede435 → 候选（本需求） |
|---|---|---|
| billing | `platform-support-api` −92 / +109（数据查询、文件、工作流 API；`externalapi` 等迁去 channel-connector；币种客户端相关条目无差异）；`paytech-messaging` 的 `catalog/events.yaml`、`YamlEventCatalog.class` | +4 个 app 配置类（四个客户端）；billing-core +35 / ~5（`BillingFactContext`、`BillingFactService(+Impl)`、`BillingFactInvariants(+$1)`）与 `pom.xml`；`application.yml`；`classpath.idx` / `layers.idx`；+4 个依赖 JAR（commercial-api 142、channel-connector-api 144、reconciliation-api 103、ledger-api 82 项）；`events.yaml` |
| account | `billing-api` +2 / −1 / ~8（`b04b77f1`、`2f5f37f7` 的人工调整与引用字段）；`events.yaml`、`YamlEventCatalog.class` | `FactBalanceMappingRule.class`；`events.yaml` |
| ledger | 同 billing 的 `platform-support-api` 与 messaging 两项 | +`LedgerApiErrorConfiguration`；~`LedgerSetupQueryController`；ledger-api ~2（`LedgerSetupQueryApi`、`LedgerSetupErrorCode`）；ledger-core ~6（`FinanceEntityMapper` 类与 XML、`FinanceEntityRepository(+Impl)`、`LedgerQueryService(+Impl)`）；`events.yaml` |

结论：候选相对运行版的差异 = 已提交到 main 的他人改动（运行镜像建于 10-20 / 10-21，早于这些提交）+ 本需求改动，没有任何未提交的他人内容。运行版缺的那部分都是 API JAR / 事件目录的增量，不改变这三个服务的运行行为（billing / ledger 只用 platform-support 的币种接口，相关条目无差异）。

### 2.3 D2 payment / acquiring：基于 bf 提交 d3d39e40 构建（2026-10-23 已执行）

现网 payment `f3c48d89…`（JAR `621ff256…`）/ acquiring `55a644fd…`（JAR `de02f36b…`）是 code-mor-bf 订单过期收尾的镜像，已提交为 main `623916ac`（代码）+ `d3d39e40`（文档）。主会话把工作树 HEAD 挪到 `d3d39e40` 并带上本需求改动；与 bf 重叠的 4 个文件中，`acquiring-test/.../MockTestBase.java` 有一处冲突，已合并（`PaymentTerminationMocker` 与 `OrderPaymentEventProbe` 两个字段都保留），`acquiring-app/application.yml`、`acquiring-test/application-mock.yml`、`payment-test/PaymentTestMapper.java` 自动合并。

| 步骤 | 结果 |
|---|---|
| 合并验证：acquiring（`-pl acquiring/paytech-acquiring-test -am test -Dtest='com/paytech/acquiring/**/*'`，与阶段 5 相同） | **853 / 0**（阶段 5 为 820，多出 33 个是 bf 的过期收尾测试） |
| 合并验证：payment（messaging、risk-decision-api、card-api、channel-connector-api 与 payment 四模块进 reactor，`-Dtest='com/paytech/payment/**/*'`，与阶段 5 相同） | **111 / 0**（阶段 5 为 96；多出的是 bf 的 `TerminateIntentTest` 增量与 `GetIntentBySourceTest` 等），12 个测试类全部运行，含 `PaymentSucceededPublishTest` 6 例 |
| D1 基线复现：`git archive d3d39e40 paytech` 解到 scratchpad，`-pl payment/paytech-payment-app,acquiring/paytech-acquiring-app -am clean package`，与运行容器 JAR 逐条目比较（含嵌套 JAR） | **payment 62,319 项、acquiring 62,700 项，0 差异**（与 bf `d3d39e40` 的结论一致） |
| `d3d39e40` → 候选 | payment：~5 项 = `PaymentSucceededPayload`、`PaymentSucceededResult`（含 Builder）、`PaymentSucceededHandler`、messaging `events.yaml`。acquiring：+8 / ~11 = 新增 `AcquiringEventProperties`、`OrderCommercialContextView`、`OrderPaymentEventSource`、`OrderPaymentFacts`、`OrderPaymentSucceededPayload`、`OrderPaymentEventService(+Impl)`、`ext/job/OrderPaymentSucceededHandler`；修改 `application.yml`、`OrderContextMapper`（类与 XML）、`OrderContextRepository(+Impl)`、`PaymentSucceededContext`、`OrderPaymentServiceImpl`、`PaymentSucceededMessageConverter`、`ApplyPaymentSucceededProcessor` 与 Request、`events.yaml`。`compensation-event-catalog.yaml` 是仓库级文件（arch-rules 使用），不进 JAR。没有其他差异 |
| `application.yml` 合并核对 | 相对 `d3d39e40` 只**新增**本需求的发布确认 / `messaging.producer` / `publisher.unroutable-policy` / `paytech.instance-id` / `acquiring.event` 段；bf 的 `acquiring.order-expiry`（batch-size 200、pending-alert-after 24h）原样保留 |
| 镜像 | 同第 2.1 节 `docker build`，health-probe 取自运行容器；镜像内 JAR = 构建产物；Entrypoint / Cmd / User / WorkingDir / ExposedPorts / Env 与运行镜像相同 |
| 覆盖文件 | 两个 sha256 已填入 [compose-candidate.yaml](./compose-candidate.yaml)；`config` 通过，env 逐键比较同第 2.4 节（acquiring 只多 `PAYTECH_INSTANCE_ID`） |

回滚目标不变：payment `f3c48d89…`、acquiring `55a644fd…`（[compose-rollback.yaml](./compose-rollback.yaml)）。逐条目清单见 [jar-diff-20261023.txt](./jar-diff-20261023.txt) 末段。

### 2.4 D3 环境变量

- `docker exec <c> printenv` 只比较键与值是否相等、不输出值：主工作区 compose（HEAD `32dbdd18`，`test-environment` 无未提交改动）渲染结果与五个运行容器**逐键逐值相同**，例外只有 payment 的 `PAYMENT_CHANNEL_CALLBACK_BASE_URL`（3DS / 订单过期候选带入，空串），两份覆盖文件都原样带上。容器多出的只有基础镜像的 `HOME`、`HOSTNAME`、`JAVA_HOME`、`JAVA_VERSION`、`LANG`、`LANGUAGE`、`LC_ALL`、`PATH`。
- 历次 `compose-candidate*.yaml` 给这五个服务加过的键（ledger `XXL_JOB_*`、billing / ledger `*_PLATFORM_SERVICE_URL`、payment `PAYMENT_INSTRUMENT_PREPARATION_WINDOW`）主 compose 都已包含。
- 新增键只在工作树 `compose.applications.yaml`（本需求未提交 hunk），候选覆盖文件兜底：billing `BILLING_COMMERCIAL_SERVICE_URL=http://commercial-service:18108`、`BILLING_CHANNEL_CONNECTOR_SERVICE_URL=http://channel-connector-service:18110`、`BILLING_RECONCILIATION_SERVICE_URL=http://reconciliation-service:18115`、`BILLING_LEDGER_SERVICE_URL=http://ledger-service:18105`；acquiring `PAYTECH_INSTANCE_ID=acquiring-test`。
- 渲染核对（2026-10-23，主工作区 HEAD `d3d39e40`，bf 提交未改 `test-environment`）：候选 = 运行 + 上述 5 个键，其余逐键相同；回滚 = 运行，逐键相同；两份 `config` 均通过。键清单存在 scratchpad（只含键名）。
- Snowflake worker：本需求不新增服务，不分配新 worker（mor-test 范围 0–31）。

## 3. 只读核对结果（2026-10-22 21:0x–21:3xZ，本地 10-23）

输出：[sql/00-precheck-20261023.out](./sql/00-precheck-20261023.out)。

| 编号 | 项 | 结果 | 实际值 |
|---|---|---|---|
| — | 运行镜像 | — | billing `90d7401a…`（10-22 05:35:06Z 起，restarts 26）、account `dfa6254c…`（05:34:49Z，34）、ledger `fa21520d…`（05:34:58Z，17）、acquiring `55a644fd…`（20:40:31Z，0）、payment `f3c48d89…`（20:39:24Z，0），均 healthy。前三个的重启计数是 10-22 05:35 之前累积的，之后稳定 |
| L1 | OE 账簿 AE001 / USD | PASS | book `238137396670066688` OPEN |
| L2 | OE 财务日历 + 今日所在期间 | PASS | `AE001-STD`（Asia/Dubai）2026-10 OPEN（11 月 FUTURE，见 R7） |
| L3 | FE PLATFORM_HQ（Party `880000000701`） | PASS | `238324786164494336` ACTIVE |
| L4 | FE USD 账簿 + 期间 | PASS | book `238324787049492480` OPEN；`PLATFORM_HQ-STD` 2026-10 OPEN |
| L5 | `STANDARD_TRANSACTION` | PASS | bundle `5001`，当前版本 `5101` #1 ACTIVE |
| B1 | billing 日历 `DEFAULT` | PASS | UTC ACTIVE（`t_business_calendar_day` 0 行） |
| B2 | PAYMENT 事实 | PASS | 0（另有 MANUAL_ADJUSTMENT 9 APPROVED / 1 VOIDED） |
| C1 | DEMO-CL-001 | PASS | agreement `238331035912310784`，AE001，clientId `237575207592763393` |
| C2 | CLIENT_PRICING 版本 | PASS | `238331390620405760`（DEMO-CL-001-ACQ V1，ACTIVE，2026-10-20 16:04:41Z — **2026-11-19 16:04:55Z**） |
| C3 | Client USD 价格结算四字段 | PASS | `2 / AE_BUSINESS_DAYS / UTC / 86399` |
| **C4** | **结算日历在 mor_billing 存在** | **GAP-CAL** | `AE_BUSINESS_DAYS` → 缺。commercial `CommercialCodes.AE_BUSINESS_DAYS` 是 Client 合同 T+N 的**固定**日历代码（"本期固定"），billing 初始化数据只有 `DEFAULT`。billing `SettlementTimeCalculator` 抛 `ErrorException` → 退避重投 5 次后进 `billing.acquiring.dlq`。**不只 mor-test：任何真实 Client 价格都会命中这个缺口**（auto 测试用 DTO Mock 未覆盖） |
| C5 | Client 价格维度 billing 能否提供 | APPLE_PAY PASS / **CARD GAP-ISSUER** | CARD 价格画像（`CARD_VISA_CREDIT_DOMESTIC`、`CARD_INTERNATIONAL`、`CARD_PREMIUM`、`CARD_AMEX`、`CARD_STANDARD`）使用 `ISSUER_REGION`；commercial 命中算法对同支付方式全部候选要求维度"已知"，billing 只传 CURRENCY / CONSUMER_COUNTRY / CARD_BRAND / FUNDING_TYPE / CARD_LEVEL（后三项为空不传）→ **CARD 支付必回 422 `PRICING_FACTS_INCOMPLETE`**，重试后 DLQ。`DEMO2-CL-103` 同样。APPLE_PAY 只有无条件的 `APPLE_PAY_STANDARD`，可命中 |
| C6 | Client SALE 组件（预览） | INFO | APPLE_PAY_STANDARD #6：2.7% + 0.30 USD；CARD_STANDARD #5：2.9% + 0.30 |
| C7 | DEMO-PT-001 | PASS | agreement `238331036726005760`，localPartnerId `770000001001`，`t_agreement_operating_entity` `238331036726005764`（AE001） |
| C8 | Partner 两条款版本 | PASS | PARTNER_SHARE `238331037560672256` V1、PLATFORM_SERVICE_CHARGE `238331039527800832` V1，均 ACTIVE、无截止 |
| C9 | PLATFORM_SIGNATORY = FE Party | PASS | `880000000701` |
| **C10** | **Partner 两条款有 APPLE_PAY USD 价格** | **GAP-PARTNER** | 只有 CARD（`CARD_STANDARD`：分润 SALE 30% × `ACTUAL_CLIENT_FEE`、服务费 SALE 0.2% × `GROSS_AMOUNT`）。APPLE_PAY → `PRICE_NOT_FOUND`（404，重试后 DLQ）；CARD → GAP-ISSUER。**当前数据下 PARTNER 场景无法通过** |
| R1 | 清算账户 | PASS | `238508282463989760` NI-AE001-USD（DEMO_NI，AE001，USD，ACTIVE），绑定 MID `238508278684950528` |
| K1 | MID | PASS | `238508278684950528` DEMO-NI-AE001-01，channel `238508277900615680`（DEMO_NI），channelAccount `238508278357794816`，ACTIVE |
| **K2** | **渠道成本方案覆盖** | **GAP-COST** | `mor_channel_connector` 渠道成本方案 / 版本 / 范围 / 组成**全部 0 行**。channel-connector 400 `CHANNEL_COST_NOT_RESOLVED` → 重试后 DLQ |
| A1 | acquiring 三表联查列 | PASS | 23 / 23（新 acquiring 的 `findCommercialContext` 可执行） |
| S1 | 合成 ID 段未占用 | PASS | `99990000000000%` 引用 0 行 |
| X1 | 补偿任务基线 | INFO | reconciliation FAILED 7（`RECON_EVENT_UNPUBLISHED`，四类对账事件无订阅方，需求 C）；acquiring FAILED 9；其余库 0 行（成功任务已删除） |
| MQ | RabbitMQ（管理 API GET） | INFO | `acquiring.events` **不存在**（billing 新镜像以消费方身份主动声明它与 `billing.acquiring.*`，见第 5 节第 3 步）；`account.billing`、`ledger.account`、`reconciliation.billing`、`acquiring.payment` 的 p0..p3 各 1 消费者、DLQ 0 |
| XXL | 任务登记（只读） | INFO | 不新增任务。billing 组 11 `compensationEventScan`、account 组 12 `compensationEventScan` + `accountBalanceMaturity`（每 10 分钟）、ledger 组 16 `accountingRuleVersionActivation`、acquiring 组 4 `compensationEventScan` + `orderExpiryScan`、payment 组 9 `compensationEventScan`，均已启用。acquiring 新补偿类型 `ORDER_PAYMENT_SUCCEEDED` 由已有 `compensationEventScan` 兜底 |
| 覆盖文件 | `compose.sh -f <候选 / 回滚> config` | PASS | 均通过；env 逐键比较见 2.4 |

**用户裁决（2026-10-23）**

| 缺口 | 裁决 | 落实 |
|---|---|---|
| GAP-CAL | Q-1：billing 补 `AE_BUSINESS_DAYS`（billing 是工作日日历 Owner；commercial `CommercialCodes.AE_BUSINESS_DAYS` 固定引用） | billing agent 写入 `billing-initial-data.sql` 与变更文件 `changes/2026-10-23-business-calendar-ae.sql`；本发布 SQL-1（第 4 节），mor-test 与本机各执行一次。T+N 按命中价格的 `settlement_timezone`（DEMO-CL-001 为 UTC）计算，日历行的 `timezone` 只是元数据；无例外日 → 周一至周五 |
| GAP-COST | Q-2：控制台建 DEMO_NI 渠道成本方案并批准生效，范围 (MID `238508278684950528`, AE001, AE, USD, APPLE_PAY, PAY)，一个 PERCENTAGE 组成 0.5% | 第 5 节第 4 步；核对 `sql/10-verify-calendar-and-cost.sql` V4 / V5 |
| GAP-ISSUER | Q-3：本期只验收 PLATFORM 来源 APPLE_PAY；发卡维度未经 `PaymentSucceeded` / `OrderPaymentSucceeded` 传递，记为需求 D 前置 | 第 9 节 R6 |
| GAP-PARTNER | Q-3：PARTNER 场景不在本次验收；DEMO-PT-001 只有 CARD 价格，记为 Partner 演示数据后续项 | `params-partner.json` 保留并标注「当前数据下不可运行」；第 9 节 R6 |
| 合成数据 | Q-5：保留为演示数据 | `cleanup_synthetic.sql` 保持全部注释 |

## 4. SQL 清单

| 步 | 目标 | 文件 | 前置核对 | 预期 | 回滚 |
|---|---|---|---|---|---|
| SQL-0 | 只读 | [sql/00-precheck.sql](./sql/00-precheck.sql) | — | 与第 3 节一致；SQL-1 前 C4 为 GAP、K2 为 GAP | — |
| SQL-1 | `mor_billing`（mor-test，root；本机 local-environment 同样执行） | `paytech/billing/docs/database/sql/changes/2026-10-23-business-calendar-ae.sql`（billing agent 编写；内容 = `billing-initial-data.sql` 新增的同一行：`AE_BUSINESS_DAYS`、`Asia/Dubai`、`ACTIVE`，不写 `t_business_calendar_day`） | [sql/10-verify-calendar-and-cost.sql](./sql/10-verify-calendar-and-cost.sql) V1 = **0 行**（不为 0 不执行：主键冲突且说明已有人补过） | V1 = **1 行** ACTIVE、V2 = 0（周一至周五）；`00-precheck.sql` C4 → PASS。执行时文件内容与 `billing-initial-data.sql` 的新增行逐字比较 | V3 = 0（无事实引用）时 `DELETE FROM mor_billing.t_business_calendar WHERE calendar_code = 'AE_BUSINESS_DAYS';`；有引用时不回滚（已登记事实依赖它） |
| SQL-V1 | 只读 | [sql/10-verify-calendar-and-cost.sql](./sql/10-verify-calendar-and-cost.sql) | — | SQL-1 后 V1 = 1；第 5 节第 4 步后 V5 = PASS | — |
| SQL-V2 | 只读 | [acceptance/check_chain.sql](./acceptance/check_chain.sql) | — | 第 7.2 节 | — |
| 提案 | 全部注释 | [acceptance/cleanup_synthetic.sql](./acceptance/cleanup_synthetic.sql) | — | 不执行（Q-5） | — |

执行方式同既往发布：root 口令从 `test-environment/.env.test` 读，经 `MYSQL_PWD` 传入 `mor-test-mysql-1`，不回显；输出存备份目录 `sql-<步>.out`。SQL-1 只是演示环境参考数据，不改表结构，PDManer 不变。

## 5. 执行步骤（阶段 7，主会话执行）

停起容器、root SQL、运营写入、MQ 发布都由主会话执行。`$O` = 本目录 `compose-candidate.yaml` 绝对路径，`$B` = 备份目录，`up` = `./test-environment/compose.sh -f $O up -d --no-deps --no-build --pull never --wait --wait-timeout 180`，`$A` = 本目录 `acceptance/`。每步"核对" = `docker inspect` `.Image` 等于候选、healthy、`RestartCount` 0、`docker logs --since <启动时刻> <c> | grep -c ' ERROR '` 为 0、`python3 -I $A/queue_status.py` 符合预期。一次性容器统一用本机已有镜像 `python:3.13-alpine`、`--rm --network mor-test-backend`，不拉取、不改 mor-test 容器。

0. **协调**：SendMessage 给 **code-mor-bf**（现网 payment / acquiring 属其订单过期收尾，已提交 `623916ac` + `d3d39e40`）与币种会话（billing / ledger 现网 `currency-20261021`）：本次替换五个服务，请确认期间不发布它们。任何一方在此期间发布了其中之一，本包对应部分作废重建。
1. **复核基线**：五个容器 `.Image` 等于 [compose-rollback.yaml](./compose-rollback.yaml)、healthy；`sql/00-precheck.sql` 结果与第 3 节一致。有不同就停。
2. **备份**：`B=$(requirements/20261022/billing-支付事实自动登记/release/scripts/backup.sh)` → `~/Code-MoR-backups/<UTC>-billing-b-mor-test/`（四个库的验收相关表 dump，含 `t_business_calendar`；五个运行镜像 ID 与 JAR SHA-256；两个覆盖文件；precheck；队列状态）。
3. **SQL-1**（billing 换版前）：V1 = 0 → 执行 → V1 = 1、V2 = 0、`00-precheck.sql` C4 = PASS。本机 local-environment 同样执行一次（V1 0 → 1）。
4. **渠道成本方案（Q-2）**：channel-connector 本次不换版，可在任何时候做，**须在第 9 步发布合成事件前完成**。
   - 控制台（管理员 operator@example.test）：渠道 → 渠道成本（`/channel/cost-plans`）→「新建方案」：渠道 = DEMO_NI（`238508277900615680`），方案代码 `DEMO_NI_COST` → 进入方案 →「新建版本」：生效时间 = 当前时刻（或略早，须不晚于批准时刻，批准时已到期则直接 ACTIVE；**且必须不晚于验收载荷的 `paymentSucceededTime`——billing 以支付时间命中成本，见第 9 节 R14**）；「+ 添加范围」：MID = `238508278684950528`（DEMO-NI-AE001-01）、OE = AE001、渠道合同引用 = `DEMO-NI-AGR-001`、国家 = AE、币种 = USD、支付方式 = APPLE_PAY、动作 = PAY；「+ 添加组成」：组成代码 `PROCESSING_PCT`、类型 `PROCESSING_FEE`、计算基数 `TRANSACTION_AMOUNT`、费率 `0.005`（十进制比例，即 0.5%）、固定费 / 下限 / 上限 / 币种留空、舍入 `HALF_UP` →「保存并提交审批」（编制人 = 当前会话账号）。
   - **复核（Checker ≠ Maker）**：ops-bff 以会话 Account ID 补 `makerId` / `checkerId`，页面不能自报；mor-test 只有一个 Operations 账号（`test-environment/private/access.md`），同人批准会被拒（400）。
     - **A（首选，与 20261020 渠道控制台阶段 7 相同）**：平台 → 人员 / 角色（IAM）新建合成复核角色（含渠道成本审批权限）与合成人员，首登改口令后用该账号在「渠道成本」对该版本点「批准」（确认内容摘要）。合成人员保留，账号与用途写进执行记录。
     - **B（备选，不新增 Platform 账号；与财务控制台阶段 7"复核路径经私网接口验证"相同）**：`docker run --rm --network mor-test-backend -v "$PWD/$A:/a:ro" python:3.13-alpine python -I /a/approve_cost_version_private.py DEMO_NI_COST 1 <checkerId>`（先只读，看到 PENDING_APPROVAL 与摘要），再加 `--approve`。`checkerId` 须不同于 makerId，用可辨认的合成值（如 `999900000000000901`）并写进执行记录——审计里该复核人不对应真实账号，需用户接受。
   - 核对：`sql/10-verify-calendar-and-cost.sql` V5 = PASS（方案 ACTIVE、版本 ACTIVE 且覆盖现在、1 个范围 1 个组成、maker ≠ checker、费率 0.005）；`00-precheck.sql` K2 = PASS。
5. **billing（必须先于 acquiring）**：`up billing-service`。核对 + RabbitMQ：`acquiring.events`（fanout）已存在且绑定到 `billing.acquiring.hash`；`billing.acquiring.p0..p3` 各 1 消费者、`billing.acquiring.dlq` 0；`account.billing.*`、`reconciliation.billing.*` 消费者不变；XXL 组 11 约 30 秒后重新注册；`mor_billing.t_compensation_event` 无新增 FAILED。理由：acquiring 新镜像 `unroutable-policy: FAIL`，billing 声明队列前它的 `ORDER_PAYMENT_SUCCEEDED` 任务会重试约 30 分钟后 FAILED。
6. **account**：`up account-service`。核对 + `account.billing.p0..p3` 各 1 消费者、DLQ 0；XXL 组 12 重新注册。
7. **ledger**：`up ledger-service`。核对 + `ledger.account.p0..p3` 各 1 消费者、DLQ 0；XXL 组 16 重新注册。上游冒烟：`docker run --rm --network mor-test-backend -v "$PWD/$A:/a:ro" python:3.13-alpine python -I /a/smoke_upstreams.py /a/params-platform.json` → commercial CLIENT_PRICING APPLE_PAY 200（`APPLE_PAY_STANDARD`）、CARD 422（GAP-ISSUER 记录）、channel-connector 200（`DEMO_NI_COST` / 版本 1）、reconciliation 200（`238508282463989760`）、ledger by-party `880000000701` 200（`238324786164494336`）。
8. **payment → acquiring**（候选 `cf9097ea…` / `27c36f4e…`，第 2.3 节）：
   - 安静窗口：`mor_payment.t_compensation_event` 无 `PAYMENT_SUCCEEDED` 的 INIT / RETRY；`acquiring.payment.p0..p3` 消息 0；记下 `acquiring.payment.dlq`、`billing.acquiring.dlq` 计数。
   - `up payment-service`，紧接着 `up acquiring-service`（间隔 ≤ 2 分钟；旧 acquiring 按字段名读 `PaymentSucceeded`，能容忍多出的两个字段；反过来新 acquiring + 旧 payment 会发出缺 `channelTransactionRefId` 的事件，被 billing 拒进 DLQ，所以 payment 先换）。
   - 核对两者 + `acquiring.payment.p0..p3` 各 1 消费者；acquiring 启动日志无 `acquiring.events` 声明错误；XXL 组 9 / 组 4 重新注册；两个 DLQ 计数与换版前相同。
   - 不做经 Hosted Checkout 的真实支付验收（P1，R5）。
9. **合成验收（Q-3：PLATFORM 来源 APPLE_PAY 一笔）**：`python3 -I $A/publish_order_payment_succeeded.py $A/params-platform.json`（dry-run，核对 `payloadHash = sha256:7c6a24f12fc25bed5637a0a4b98351589de46705cc4c0b87154625c6202f2b00`）→ 加 `--publish --out "$B/published-platform.json"` 发布一条 → 30–60 秒后 `check_chain.sql`（输出存 `$B/chain-platform.out`）+ `queue_status.py`（存 `$B/queues-after.txt`）→ 第 7.3 节控制台四页。期望见第 7.2 节；不符就停下记录，不重发（同一事件重发只会被幂等命中）。
10. **记录**：填第 8 节；`delivery.md` 补环境操作记录（SQL-1 两处执行、成本方案与复核方式、合成事件 ID）；通知 code-mor-bf 与币种会话新的镜像 ID 与源码基线（billing / account / ledger = `22ede435` + 本需求，payment / acquiring = `d3d39e40` + 本需求）。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| acquiring / payment | `./test-environment/compose.sh -f <compose-rollback.yaml> up … acquiring-service`，再 `payment-service`，回到 bf 的 `55a644fd` / `f3c48d89`。先回 acquiring：旧 acquiring 不发 `OrderPaymentSucceeded`，此后 billing 不再收到新事件。已登记未发布的 `ORDER_PAYMENT_SUCCEEDED` 任务留在 `mor_acquiring.t_compensation_event`；旧镜像未配置该类型的 Handler（Foundation `CompensationHandlerRegistry` 对未配置类型报配置错误），任务不会被执行，需人工处置或待重新升级后继续 |
| ledger | 回到 `fa21520d`。旧 ledger 没有 by-party 接口：此后 Partner 来源的 PAYMENT 事实会在 billing 重试后进 DLQ；已过账分录不受影响 |
| account | 回到 `dfa6254c`。旧白名单拒绝 `CHANNEL` 作 `CLIENT_PRINCIPAL` / `CLIENT_FEE` 付方：之后任何新的 PAYMENT 事实都会被 account 拒进 `account.billing.dlq`，所以 **account 回滚前先回滚 billing（或确认不会再产生 PAYMENT 事实）** |
| billing | 回到 `90d7401a`。旧 billing 不消费 `acquiring.events`，但 `billing.acquiring.*` 队列已声明：若 acquiring 仍是新版，消息会积压在 `billing.acquiring.p*`（重新升级后继续消费）；要删队列须另行授权。已登记的 AUTO PAYMENT 事实留在库里，旧代码的查询 / 控制台可能不认识其中部分字段（只读影响） |
| 顺序 | acquiring → payment → billing → account → ledger（与发布相反，且 billing 先于 account） |
| SQL-1 | 回滚 billing 镜像不需要删日历行（旧 billing 不读它）；确需回退时按第 4 节，仅当 V3 = 0 |
| 渠道成本方案 | 不随镜像回滚。若要撤下：控制台对 `DEMO_NI_COST` 新建并批准一个失效 / 替换版本，或保留（只被 billing 命中使用，不影响 Payment 选路） |
| 数据 | 验收写入的行见第 7.4 节，不随镜像回滚而消失（Q-5 保留） |

## 7. mor-test 验收（AC-11，Q15）

### 7.1 驱动

- [acceptance/publish_order_payment_succeeded.py](./acceptance/publish_order_payment_succeeded.py)：向 `acquiring.events` 发布**一条**合成 `OrderPaymentSucceeded`（管理 API publish，本机无 pika）。线格式与 Foundation 一致：信封字段顺序、`occurredAt` 6 位小数、`eventId = ORDER:<orderId>:OrderPaymentSucceeded:<version>`、`payloadHash = sha256:` + RFC 8785 规范 Payload 的 SHA-256、AMQP `message_id` / `type` / `content_type` / `delivery_mode 2` / header `x-aggregate-id`、routing key = orderId；Payload 34 个字段按 acquiring `OrderPaymentSucceededPayload` 顺序。默认 dry-run；`--publish` 前先检查绑定、四个分区消费者与 DLQ。
- **已验证**：阶段 6 用真实 `DomainEventCodec.decode`（重算 payloadHash）与 billing `OrderPaymentSucceededEventReader.read` 解析了脚本生成的两份信封（PLATFORM / PARTNER），均通过（来源键 `PAYMENT_ATTEMPT:999900000000000104` / `…204`，渠道引用三元组完整，Partner 五项完整）。未向 mor-test 发布任何消息。
- 参数：[params-platform.json](./acceptance/params-platform.json)（无 Partner，APPLE_PAY）、[params-partner.json](./acceptance/params-partner.json)（Local Partner `770000001001`、平台 Party `880000000701`；**当前数据下不可运行**，阶段 7 不执行，见 GAP-PARTNER / R6）。合成 ID 段 `9999000000000001xx` / `2xx`，外部交易号 `SYN-BILLB-P-0001` / `SYN-BILLB-T-0001`，`financeTaxSnapshotId` 也取合成值（只记引用，S11）；PLATFORM 载荷的 `commercialContextTime` / `paymentSucceededTime` 已固定（第 7.2 节），驱动仍支持 `NOW`。
- [acceptance/smoke_upstreams.py](./acceptance/smoke_upstreams.py)：发布前确认 commercial / channel-connector / reconciliation / ledger 四个上游按 billing 的请求体可命中（第 5 节第 5 步的一次性容器运行方式）。
- [acceptance/check_chain.sql](./acceptance/check_chain.sql)、[acceptance/queue_status.py](./acceptance/queue_status.py)：只读核对。

### 7.2 步骤与期望（PLATFORM 来源 APPLE_PAY，一笔）

步骤见第 5 节第 9 步。载荷固定（[params-platform.json](./acceptance/params-platform.json)）：订单 / 收取金额 **100.00 USD**，`commercialContextTime = 2026-10-22T19:55:00Z`，`paymentSucceededTime = 2026-10-22T20:00:00Z`（周四），消费者国家 AE，合成 ID `999900000000000101`（订单）/ `…102`（上下文）/ `…103`（Intent）/ `…104`（Attempt）/ `…105`（渠道交易引用）/ `…106`（税务快照），外部交易号 `SYN-BILLB-P-0001`，`aggregateVersion = 3`。

> **阶段 7 修订（2026-10-23）：** 上面的固定时间早于第 4 步建的成本版本生效时间，第一次发布被拒（第 8 节 9a）。实际验收载荷改为 `aggregateVersion = 4`、`commercialContextTime = 2026-10-23T07:40:00Z`、`paymentSucceededTime = 2026-10-23T07:45:00Z`（周五），payloadHash `ec153bd4…`；由此 `balance_available_time` 期望变为 **2026-10-27 23:59:59 UTC**（d0 周五不计，+2 工作日 → 周一 10-26、周二 10-27），`source_version = 4`，ledger 过账日仍为 2026-10-23。其余期望不变；实际结果见第 8.1 节。

**计算（DEMO-CL-001 ACQ V1，USD，`APPLE_PAY_STANDARD` priority 6，只取 SALE 组件；渠道成本 `DEMO_NI_COST` v1）**

| 项 | 算式 | 期望 |
|---|---|---|
| CLIENT_FEE `SALE_PCT` | 100.00 × 0.027 | **2.70** |
| CLIENT_FEE `SALE_FIXED` | 固定 0.30 USD（FIXED_PER_EVENT，`basis_amount` 记毛额，`rate_value` 空） | **0.30** |
| Σ CLIENT_FEE | | 3.00（> 0，S14 不拒绝） |
| CLIENT_PRINCIPAL | 100.00 − 3.00 | **97.00** |
| CHANNEL_COST `PROCESSING_PCT` | 100.00 × 0.005，HALF_UP 到 2 位 | **0.50** |
| `balance_available_time` | 价格结算 N = 2、日历 `AE_BUSINESS_DAYS`（无例外日 → 周一至周五）、结算时区 **UTC**、cutoff 86399：d0 = 2026-10-22（周四，20:00:00 ≤ cutoff），不计 d0 向后数 2 个工作日 → 周五 10-23、**周一 10-26**；23:59:59 UTC | **2026-10-26 23:59:59.000000**（UTC） |

说明：T+N 用价格的 `settlement_timezone`（UTC），不是日历行的 `Asia/Dubai`；ledger 的过账日期按 OE 财务日历（Asia/Dubai）为 2026-10-23，落在 2026-10 OPEN 期间。

| 段 | 期望 |
|---|---|
| billing 头 | 一条 `PAYMENT / AUTO / APPROVED / fact_version 1`；`source_system = acquiring`，`source_event_key = PAYMENT_ATTEMPT:999900000000000104`，`source_version = 3`，`source_payload_hash = 7c6a24f12fc25bed5637a0a4b98351589de46705cc4c0b87154625c6202f2b00`；`client_id = 237575207592763393`、`entity_no = AE001`、`currency = USD`、`gross_amount = 100.00`；`local_partner_id`、`finance_entity_id` 为空；`business_time = 2026-10-22 20:00:00`；`balance_available_time = 2026-10-26 23:59:59`；`published_at` 非空 |
| billing 引用（9） | ORDER `…101`、ORDER_CONTEXT `…102`、PAYMENT_INTENT `…103`、PAYMENT_ATTEMPT `…104`、PAYMENT_CHANNEL_ACCOUNT `238508278357794816`、MID `238508278684950528`、CLEARING_ACCOUNT `238508282463989760`、CHANNEL_TRANSACTION_REF `…105`（`PAY` / `SYN-BILLB-P-0001`）、TAX_SNAPSHOT `…106` |
| billing 行（4，line_no 1–4） | 1 CLIENT_FEE 2.70（CHANNEL `238508282463989760` → OPERATING_ENTITY AE001，`source_rule_type = COMMERCIAL_PRICE`，`source_rule_version_id = 238331390620405760`，`source_price_id = 238331390859481160`，组件 `238331390859481163`）；2 CLIENT_FEE 0.30（组件 `238331390859481164`）；3 CLIENT_PRINCIPAL 97.00（CHANNEL → CLIENT `237575207592763393`，组件空）；4 CHANNEL_COST 0.50（OPERATING_ENTITY AE001 → CHANNEL `238508282463989760`，`source_rule_type = CHANNEL_COST`）。全部 `effect_direction = CHARGE`；无 SELLER_TAX / PARTNER 行 |
| billing 定价侧表 | `client_agreement_id = 238331035912310784`、`commercial_context_time = 2026-10-22 19:55:00`、`consumer_country_code = AE`、`payment_method = APPLE_PAY`、`industry_code = DIGITAL_GOODS`、`mcc_code = 5816`、卡属性三列空、`commercial_price_id = 238331390859481160`、结算 `2 / AE_BUSINESS_DAYS / UTC / 86399` |
| account（AC-07） | `t_balance_applied_fact` 一条、`maturity_status = PENDING`、`balance_available_time = 2026-10-26 23:59:59`；OE AE001 USD 一条 posting；Client `237575207592763393` 的 `CLIENT_PAYABLE / PENDING` 贷 **97.00**；平台侧行（CLIENT_FEE、CHANNEL_COST）不产生 Owner 流水。2026-10-26 23:59:59Z 之后 `accountBalanceMaturity`（每 10 分钟）把它转 AVAILABLE |
| ledger（AC-08） | `PAYMENT_SUCCEEDED` 指令一条（AE001 USD 账簿 `238137396670066688`，规则包版本 5101，`economic_fact_id` = 事实 ID）；分录借贷相等（毛额 100.00 的渠道待清算应收挂清算账户 `238508282463989760`、Client 应付 97.00、手续费收入 3.00、渠道成本 0.50 及其对方科目，按 `STANDARD_TRANSACTION` 六行规则）；无服务费指令 |
| reconciliation（AC-09） | `t_platform_txn` 一条：`clearing_account_id = 238508282463989760`、`billing_fact_id` = 事实 ID、`entity_no = AE001`、`channel_transaction_ref_id = 999900000000000105`、`reference_type = PAY`、`external_transaction_id = SYN-BILLB-P-0001`、`currency = USD`、`expected_amount = 100.00`、`expected_fee = 0.50`、`status = OPEN` |
| MQ / 任务 | `billing.acquiring.dlq`、`account.billing.dlq`、`ledger.account.dlq`、`reconciliation.billing.dlq` 等于发布前（0）；补偿任务无新 FAILED（与 X1 基线比：reconciliation 7、acquiring 9 为既有） |

PARTNER 场景本次不执行（Q-3）：[params-partner.json](./acceptance/params-partner.json) 已标注「当前数据下不可运行」，期望写在其 `_comment`，解除条件见第 9 节 R6。

### 7.3 控制台（管理员 operator@example.test，只读）

| 页面 | 菜单路径 | 看什么 |
|---|---|---|
| 计费事实详情 | 财务 → 调整（`/finance/adjustments`）→「计费事实」页签，类型筛「支付（PAYMENT）」，点该行 | 头、行、引用、定价输入与上面一致 |
| Owner 余额 | 财务 → 明细账查询（`/finance/subledger`）：OE AE001 / USD，Owner Client `237575207592763393`；财务 → 余额查询（`/finance/balances`） | `CLIENT_PAYABLE` PENDING +97.00；流水来源为该事实 |
| 分录 | 财务 → 账本查询（`/finance/ledger`）：AE001 / USD 账簿，按业务日期 2026-10-23（Asia/Dubai）找该分录 | 借贷平衡，指令类型「支付成功」 |
| 对账预期明细 | 财务 → 对账 → 平台流水（`/finance/reconciliation/platform-txns`），清算账户 NI-AE001-USD，外部交易号 `SYN-BILLB-P-0001` | 预期金额 100.00、预估费用 0.50、状态「未匹配」（OPEN） |

### 7.4 合成数据与清理

验收（每个场景一条事件）会写入：

| 库 | 表（行数，PLATFORM 场景） |
|---|---|
| `mor_billing` | `t_billing_fact` 1、`t_billing_fact_pricing` 1、`t_billing_fact_ref` 9、`t_billing_fact_line` 3 + 渠道成本组成数、`t_compensation_event` 1（发布成功后删除）、消费登记（`t_consumed_event` 视 messaging 拦截器而定） |
| `mor_account` | `t_balance_applied_fact` 1、`t_account_posting` 1、`t_account_entry` N、`t_owner_account`（Client `CLIENT_PAYABLE/PENDING` 不存在时新建 1，及其账号序列）、`t_account_posting_stream` 序列 +1、`t_compensation_event`（AccountPosted，发布后删除）、`t_consumed_event`；到期后 `accountBalanceMaturity` 再写 `t_balance_movement` 与新的 AccountPosted |
| `mor_ledger` | `t_accounting_instruction` 1（PARTNER 3）、`t_instruction_line`、`t_journal_entry`、`t_journal_line`、按需 `t_ledger_account` / `t_account_no_sequence`、`t_book_sequence` 前进、`t_ledger_period_balance` 累计、`t_consumed_event` / `t_consumed_aggregate_version`（AccountPosted 严格有序） |
| `mor_reconciliation` | `t_platform_txn` 1、`t_consumed_event` |

**Q-5 已定：保留为演示数据，不清理**（合成 ID 段 `99990000000000xxxx`、外部交易号 `SYN-BILLB-P-0001`；到期后还会有一条成熟移动与对应分录）：已过账分录按设计不可变；account / ledger 的余额、序列与严格有序消费版本都是聚合值，删除明细会使它们与明细不一致，后续真实过账可能在有序校验或试算平衡处失败。合成 ID 段与 `SYN-BILLB-*` 足以辨认。[acceptance/cleanup_synthetic.sql](./acceptance/cleanup_synthetic.sql) 是全部注释的删除提案（只覆盖明细行，聚合回算不在内），执行需用户单独授权。

## 8. 执行记录（阶段 7，2026-10-23，用户"按建议执行"：确认阶段 6、复核方式 A、授权五容器 / SQL-1 两处 / 成本方案 / 一条 MQ 发布）

备份目录 `$B` = `~/Code-MoR-backups/20261023T073925Z-billing-b-mor-test/`（0700 / 0600，SHA256SUMS；另含 `sql-1.out`、`sql-v1-after.out`、`cost-plan-setup.json`、`smoke-upstreams.out`、`published-platform.json`（第一次，被拒）、`published-platform-v4.json`、`queues-after*.txt`、`chain-platform.out`、`console/*.png` 10 张）。

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 07:38 | 0 协调 | SendMessage 通知 code-mor-bf（现网 payment / acquiring 属其）与 code-mor-6b（card BIN，payment 相关）替换窗口；币种会话已不在线。期间无人发布这五个服务 |
| 07:38–07:40 | 1 复核基线 | 五个容器 `.Image` 与 [compose-rollback.yaml](./compose-rollback.yaml) 逐一相同、healthy；`00-precheck.sql` 与第 3 节一致，唯一差异 X1 acquiring FAILED 9 → **10**（新增 `SUBMISSION_EVENT_TYPE` 退款提交任务 `239260874039214080`，05:37Z，`INTERNAL_ERROR`，与既有 9 条同类，早于本次发布，不归因本需求） |
| 07:39 | 2 备份 | `scripts/backup.sh` → `$B`；五个运行 JAR SHA-256 与第 2.1 节一致 |
| 07:41:03 | 3 SQL-1 | mor-test 与本机 `mor_billing` 各执行变更文件一次（`-vv`，各 1 row affected）；两处 V1 = 1（`AE_BUSINESS_DAYS / Asia/Dubai / ACTIVE`，`display_name` 逐字节 = `阿联酋工作日`）、V2 = 0、V3 = 0；`00-precheck.sql` C4 → PASS |
| 07:44:55–07:44:57 | 4 渠道成本方案（**方式 A**） | 脚本 [acceptance/setup_cost_plan.py](./acceptance/setup_cost_plan.py)（经运营入口调用控制台页面同一组 ops-bff 接口，makerId / checkerId 由 ops-bff 以会话账号补入）：IAM 新建合成复核角色 `QA_BILLB_CHECKER_CB6E8A`（`239292995084566528`，权限 CHANNEL_CONFIG_READ / COST_READ / COST_APPROVE / CHANGE_READ）与合成复核人员 `billb-checker-cb6e8a@example.test`（Account `239292995902455808`，首登改口令，口令未记录；**保留**）；管理员（Account `221546852372267008`）建方案 `DEMO_NI_COST` → 版本 1（`effectiveFrom 2026-10-23T07:42:56Z`，范围 `239292999287283712`，组成 `PROCESSING_PCT` / PROCESSING_FEE / TRANSACTION_AMOUNT / 0.005 / HALF_UP）→ 提交 → 合成复核人批准 → **ACTIVE**（contentHash `c6bd9bf8…`）。`10-verify` V4 / V5 = PASS，`00-precheck` K2 → PASS。偏差：未逐页点击控制台，改为同一接口脚本执行（审计与控制台操作等价） |
| 07:45:25–07:45:34 | 5 billing | `up billing-service` → `53450948…`，healthy，restarts 0，ERROR 0，JAR `20869ac0…`；RabbitMQ：`acquiring.events` 已声明并绑定 `billing.acquiring.hash`，`billing.acquiring.p0..p3` 各 1 消费者、DLQ 0；`t_compensation_event` 0 行；四个 `BILLING_*_SERVICE_URL` 已注入 |
| 07:46:20–07:46:36 | 6 account | `17831407…`，healthy，restarts 0，ERROR 0，JAR `f9bfd52e…`；`account.billing.p0..p3` 各 1 消费者（启动后约 5 秒挂上）、DLQ 0 |
| 07:47:02–07:47:12 | 7 ledger + 上游冒烟 | `3819ff28…`，healthy，restarts 0，ERROR 0，JAR `fb1b91d3…`；`ledger.account.p0..p3` 各 1；XXL 组 4 / 9 / 11 / 12 / 16 于 07:48:00 全部重新注册。`smoke_upstreams.py`：commercial APPLE_PAY 200（`APPLE_PAY_STANDARD`，price `238331390859481160`）、CARD 422 `PRICING_FACTS_INCOMPLETE`（缺 CARD_BRAND / FUNDING_TYPE / CARD_LEVEL / ISSUER_REGION，GAP-ISSUER 记录）、channel-connector 200（`DEMO_NI_COST` v1）、reconciliation 200（`238508282463989760`）、ledger by-party 200（`238324786164494336`） |
| 07:48:05–07:48:56 | 8 payment → acquiring | 安静窗口：`mor_payment.t_compensation_event` 0 行、`acquiring.payment.p*` 0 消息、两个 DLQ 0。payment `cf9097ea…`（07:48:25 启动）→ acquiring `27c36f4e…`（07:48:55，间隔 30 秒）；均 healthy、restarts 0、ERROR 0，JAR `96a1c9d8…` / `ec8487bd…`；`acquiring.payment.p0..p3` 各 1；acquiring `PAYTECH_INSTANCE_ID=acquiring-test`；XXL 组 4 / 9 于 07:49:30 重新注册；两个 DLQ 仍 0 |
| 07:49:58 | 9a 合成验收：第一次发布（**被拒**） | 按原载荷（`aggregateVersion 3`，`paymentSucceededTime 2026-10-22T20:00:00Z`，payloadHash `7c6a24f1…`）发布 → billing 五次重试后进 `billing.acquiring.dlq`（1 条），**未写任何数据**。原因：billing 以 `paymentSucceededTime` 作 `asOfTime` 命中渠道成本（`ChannelCostClientImpl`，设计如此），而第 4 步按本清单"生效时间 = 当前时刻"建的版本 1 自 07:42:56Z 生效，晚于载荷的支付时间 → channel-connector 400 `CHANNEL_COST_NOT_RESOLVED`。属发布清单编排缺陷（第 4 步与第 7.2 节固定载荷时间不自洽，`smoke_upstreams.py` 用 `now()` 掩盖了它），不是代码缺陷。channel-connector 不允许新版本生效时间早于当前版本，无法回填 |
| 07:53:16 | 9b 合成验收：第二次发布（**通过**） | 载荷改为 `aggregateVersion 4`、`commercialContextTime 2026-10-23T07:40:00Z`、`paymentSucceededTime 2026-10-23T07:45:00Z`（≥ 成本版本生效时间），eventId `ORDER:999900000000000101:OrderPaymentSucceeded:4`，payloadHash `ec153bd4a79a2767c909e706f86ca549edadde30d0f968d3e8b6b74684ef61bd`。约 1 秒内全链完成，见第 8.1 节；四个 DLQ 不变（`billing.acquiring.dlq` 仍为 9a 那 1 条），补偿任务无新 FAILED（reconciliation 7、acquiring 10 为基线） |
| 07:57–08:05 | 9c 控制台 | [acceptance/console_check.py](./acceptance/console_check.py)（Playwright，只读，截图 `$B/console/`）**10 / 11**：计费事实列表（类型 PAYMENT）有该行；明细账 Owner 账户 `CLIENT_PAYABLE / PENDING` 与流水 97.00；余额查询 PENDING 桶；账本下钻 AE001 / USD → `CLIENT_PAYABLE` → 2026-10-23 分录 12 贷 97.00 → 分录详情 PAYMENT_SUCCEEDED 借贷 100.50；平台流水 `SYN-BILLB-P-0001` 100.00 / 0.50 / 未匹配；无 5xx。**失败 1 项 = 发现 F1**（第 9 节）：计费事实详情抽屉报「服务暂不可用 INVALID_RESPONSE」 |
| 08:05 | 10 记录与通知 | 本节、`delivery.md`；新镜像 ID 与源码基线已发 code-mor-bf、code-mor-6b |
| 08:24 | 补：DLQ 清理 | 用户授权后管理 API purge `billing.acquiring.dlq`（1 → 0） |
| 09:01 | 补：F1 ops-web | 用户裁决 (a)：main `37173818`；等 code-mor-db 多语言窗口结束后，以其现网 `fc3abaa7…` 的构建目录为基线复现（dist 215 文件逐字节一致）+ 两个文件 → 镜像 `9fb809df…`（[compose-opsweb-f1.yaml](./compose-opsweb-f1.yaml)，回滚 [compose-opsweb-rollback.yaml](./compose-opsweb-rollback.yaml)）；healthy、服务文件 = dist；`console_check.py` 11 / 11 |

### 8.1 链路核对结果（`$B/chain-platform.out`，与第 7.2 节期望逐项比较；时间项按 9b 载荷调整）

| 段 | 实际 |
|---|---|
| billing 头 | 事实 **`239295097391407104`**：PAYMENT / AUTO / APPROVED / fact_version 1；`source_system acquiring`、`source_event_key PAYMENT_ATTEMPT:999900000000000104`、`source_version 4`、`source_payload_hash ec153bd4…`；client `237575207592763393`、AE001、USD、gross 100.00；partner / FE 空；`business_time 2026-10-23 07:45:00`；**`balance_available_time 2026-10-27 23:59:59`**（d0 周五不计，+2 工作日 → 周二）；`approved_time` = `published_at` 07:53:17 |
| billing 引用 | 9 条，与期望逐一相同（CHANNEL_TRANSACTION_REF `…105` PAY / `SYN-BILLB-P-0001`） |
| billing 行 | 1 CLIENT_FEE 2.70（CHANNEL `238508282463989760` → OE AE001，COMMERCIAL_PRICE / `238331390620405760` / price `238331390859481160` / 组件 `…163`）；2 CLIENT_FEE 0.30（组件 `…164`，rate 空）；3 CLIENT_PRINCIPAL 97.00（CHANNEL → CLIENT）；4 CHANNEL_COST 0.50（OE → CHANNEL，CHANNEL_COST / 版本 `239292999262117888` / 组件 `239292999295672320`）；全部 CHARGE；principal + fee = gross 100.00 |
| billing 定价侧表 | agreement `238331035912310784`、context 07:40:00、AE、APPLE_PAY、DIGITAL_GOODS、5816、卡属性空、price `238331390859481160`、`2 / AE_BUSINESS_DAYS / UTC / 86399` |
| account | `t_balance_applied_fact` `239295099840884736` APPLIED / **PENDING**、`balance_available_time 2026-10-27 23:59:59`；posting `239295099626975232`（OE AE001 USD，序号 10，1 条 entry）；entry：Client `237575207592763393` `CLIENT_PAYABLE` PENDING 贷 97.00（账户 `AE001-USD-22010101-S000008`，桶净额 138.10 = 既有演示 41.10 + 97.00） |
| ledger | 指令 `239295103607336960` PAYMENT_SUCCEEDED / POSTED（来源 account posting，规则包版本 5101）；分录 `239295103963852800`（AE001 USD，序号 12，过账日 **2026-10-23**，STANDARD）借 = 贷 = **100.50**：借 CHANNEL_UNSETTLED_RECEIVABLE 100.00（CHANNEL_ACCOUNT `238508282463989760`）、贷 CLIENT_FEE_REVENUE 3.00、借 CHANNEL_FEE_EXPENSE 0.50、贷 CHANNEL_FEE_ACCRUAL 0.50（CHANNEL_ACCOUNT）、贷 CLIENT_PAYABLE 97.00；无服务费指令 |
| reconciliation | `t_platform_txn` `239295101199790080`：清算账户 `238508282463989760`、RECEIVABLE / PAYMENT、billing_fact_id = 事实 ID、AE001、ref `…105` PAY / `SYN-BILLB-P-0001`、USD、expected 100.00 / fee 0.50、**OPEN**、业务时间 07:45:00 |
| MQ / 任务 | `account.billing.dlq`、`ledger.account.dlq`、`reconciliation.billing.dlq` 0；`billing.acquiring.dlq` 1（9a 的被拒消息，见第 9 节 R13）；billing / account / ledger `t_compensation_event` 0 行（发布后删除） |

验收结论：AC-07 / AC-08（PLATFORM 半句）/ AC-09 / AC-11 在 mor-test 通过；PARTNER 半句按 Q-3 不在本次。

## 9. 风险与遗留

- **R1 发布窗口 Hash 风险**（technique 4.3）：acquiring 以整段 `PaymentSucceeded` 载荷 Hash 做幂等。若旧 payment 发布的事件在 acquiring 消费失败后由新 payment 重发（多两个字段），同 eventId 不同 Hash 会被 acquiring 拒进 `acquiring.payment.dlq`，订单停在 OPEN。缓解：第 5 节第 8 步的安静窗口核对、payment 与 acquiring 背靠背替换、换版前后比较 `acquiring.payment.dlq`。概率低（card BIN 追加字段时同样存在）。
- **R2 billing 必须先于 acquiring**：acquiring 新镜像 `unroutable-policy: FAIL`；billing 未声明 `billing.acquiring.*` 前，`ORDER_PAYMENT_SUCCEEDED` 任务重试约 30 分钟后 FAILED，需人工重开。billing 新镜像以消费方身份主动声明 `acquiring.events`（fanout）与绑定（`MessagingTopologyDeclarer.declareSubscription`）。
- **R3 `~/.m3` 中的 messaging JAR**：本机 `~/.m3/repository/com/paytech/paytech-messaging/1.0.0-SNAPSHOT/*.jar`（10-22 23:31 安装）的 `catalog/events.yaml` 与本需求工作树相同（含 `OrderPaymentSucceeded`，与 main 不同）。任何会话 `-o` 构建不带 `-am` 都会把本需求未提交的事件目录打进镜像；本次五个构建都带了 `-am`。
- **R4 SQL-1 与初始化数据一致性**：`billing-initial-data.sql`、变更文件与 mor-test / 本机实际行必须同一内容；阶段 8 随代码提交，CURRENT_IMPLEMENTATION 记两处已执行。SQL-1 不进 JAR，镜像不重建。
- **R5 真实 Hosted Checkout 端到端延后到需求 D**（P1）：acquiring 的商业事实仍是固定 Mock（`clientPricingVersionId = 104`、`clientAgreementId = 105` 等）。acquiring / payment 换版后，mor-test 上每一笔真实收银台支付成功都会发出带假版本 ID 的 `OrderPaymentSucceeded`，billing 收到 commercial 404 → 退避重投 5 次 → `billing.acquiring.dlq` 并告警。这是预期噪声；验收时区分合成与真实事件，DLQ 消息不要重放。
- **R6 需求 D / Partner 演示数据的前置（Q-3）**：
  - GAP-ISSUER：`PaymentSucceeded` / `OrderPaymentSucceeded` 不携带发卡维度（`ISSUER_REGION` 等），billing 不传；DEMO-CL-001 / DEMO2-CL-103 的 CARD 价格画像依赖它，commercial 对同支付方式全部候选要求维度已知 → 任何 CARD 支付 422 `PRICING_FACTS_INCOMPLETE`，卡属性为空时 CARD_BRAND 等也未知。需求 D 接入真实 Checkout 前必须解决（事件携带发卡区域 / 国家，或约定"已确认不适用"的维度显式传 null）。
  - GAP-PARTNER：DEMO-PT-001 的 PARTNER_SHARE / PLATFORM_SERVICE_CHARGE 只有 CARD（`CARD_STANDARD`）价格；Partner 双账簿成对过账（AC-08 后半句）在 mor-test 上未验收，需另补 Partner 演示数据（与 Client 侧可命中的同一支付方式的价格版本 + 对应渠道成本范围），或在 GAP-ISSUER 解决后用 CARD。
- **R7 期间边界**：OE / FE 日历 2026-11 为 FUTURE。合成事件业务时间固定在 2026-10-22（过账日期 10-23），若阶段 7 拖到 2026-10-31 24:00（Asia/Dubai）之后仍可过账到 10 月（只要 10 月未关账）；之后的真实事件需要 11 月 OPEN。
- **R8 渠道成本复核方式**：控制台同人复核被拒；A 方式在 Platform 留下一个合成复核人员，B 方式在 channel-connector 审计里留下一个不对应真实账号的 `checkerId`。选择与 ID 写进执行记录。
- **R9 回滚不对称**：account 回滚会拒绝之后所有 PAYMENT 事实；ledger 回滚使 Partner 事实重试；billing 回滚使 `billing.acquiring.*` 积压（第 6 节）。
- **R10 预存失败任务**：reconciliation 7 条、acquiring 9 条 FAILED 补偿任务早于本次发布，验收比较以 X1 基线为准，不归因本需求。
- **R11 Compose hunk**：两个新增环境键目前只在工作树 `compose.applications.yaml`；覆盖文件已兜底，阶段 8 需随代码提交。
- **R12 合成数据保留（Q-5）**：到期任务会在 **2026-10-27 23:59:59Z**（9b 载荷）后为合成事实写余额移动与分录；控制台与对账里可凭合成 ID 段和 `SYN-BILLB-P-0001` 辨认。
- **R13 `billing.acquiring.dlq` 残留 1 条（阶段 7 新增）**：第 8 节 9a 被拒的 `ORDER:999900000000000101:OrderPaymentSucceeded:3`（载荷支付时间早于成本版本生效时间）。不要重放：重放会再次 400 → 再进 DLQ；即便成本能命中，同一来源键 `PAYMENT_ATTEMPT:999900000000000104` 已有事实，billing 会按幂等 / 并发冲突处理。清除该消息（管理 API purge 或单条 ack）需用户单独授权；留着只影响「DLQ = 0」这一监控基线。
- **R14 发布清单编排教训（阶段 7 新增）**：渠道成本版本只能向后生效（新版本生效时间必须晚于当前版本），而 billing 以 `paymentSucceededTime` 命中成本。验收 / 演示载荷的支付时间必须不早于成本版本生效时间；`smoke_upstreams.py` 已改为用载荷的 `paymentSucceededTime` 作 `asOfTime`（与 billing 一致），不再用 `now()`。
- **F1 控制台计费事实详情抽屉对 AUTO 事实报 INVALID_RESPONSE（阶段 7 发现，未修复）**：billing `GET /billing/v1/facts/{id}` 返回 200、内容完整（`$B` 内有抓取）；ops-bff 无异常；ops-web `finance/financeWorkflow.ts` `decodeBillingFact` 用 `code()`（`^[A-Z][A-Z0-9_]{0,63}$`）校验 `sourceSystem`，而本需求写的 `source_system = acquiring`（technique.md 第 6 节，与事件目录 / ledger `source_system = account` 的小写系统名一致）；人工调整事实为 `OPERATIONS`（大写）所以此前未暴露。列表页、明细账、余额、账本、对账四页不受影响。处置待用户决定：(a) ops-web 放宽为系统名文本（建议，一行改动，但需 ops-web 发布，属控制台切片）；(b) billing 改写 `ACQUIRING`（改常量 + 测试 + 文档 + 重建 billing，且 mor-test 已登记的事实行需同步改值）。阶段 8 记入 CURRENT_IMPLEMENTATION 已知缺口。**用户裁决（2026-10-23）：(a)**，main `37173818`；mor-test ops-web 发布与复验见 delivery.md 第 4 节。R13 的 DLQ 残留消息已于 08:24Z purge。
- 候选镜像（tag `billing-b-20261023`）保留；22ede435 / d3d39e40 基线构建在会话 scratchpad，阶段 7 结束后可删。

**用户决定记录（2026-10-23）**

| 编号 | 问题 | 决定 |
|---|---|---|
| Q-1 | GAP-CAL 怎么补 | billing 补 `AE_BUSINESS_DAYS`：初始化数据 + 变更文件，作为本发布 SQL-1，mor-test 与本机执行（主会话另行授权） |
| Q-2 | GAP-COST | 授权阶段 7 在运营控制台建并批准 DEMO_NI 渠道成本方案：范围 MID `238508278684950528` / AE001 / AE / USD / APPLE_PAY / PAY，一个 PERCENTAGE（`PROCESSING_FEE`）组成 0.5% |
| Q-3 | 验收范围 | 只验收 PLATFORM 来源 APPLE_PAY 一笔；GAP-ISSUER、GAP-PARTNER 记为需求 D / Partner 演示数据前置；`params-partner.json` 保留并标注不可运行 |
| Q-4 | PARTNER 场景 | 不在本次（并入 Q-3） |
| Q-5 | 合成数据 | 保留为演示数据，`cleanup_synthetic.sql` 保持全部注释提案 |
