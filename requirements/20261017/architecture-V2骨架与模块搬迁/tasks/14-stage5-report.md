# 第 14 步报告 · 阶段 5 本机全链路：宿主机起全部 V2 服务，连 local-environment，跑通已验收流程

- 分支：`refactor/step-14-stage5-local-full-chain`（起点 `refactor-main@1c5e708c`）；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step14`。
- 运行时间：2026-10-18 07:40–08:30 UTC。主会话执行了本步的评审 SQL（`sql/apply-e2e.sh all` 与 `sql/41`）；本步 agent 没有执行任何建库、Schema 或种子 SQL。
- 结论：18 个 Java 服务、合成 PayerMax、nginx、两个 Web 与 Vault 容器全部就绪。六条流程中五条全部通过，Client API 流程 90/90 通过，另有 1 项退款生产缺口（不是 wiring，本步不补）。发现并修复 1 处生产代码 wiring 缺陷（acquiring 拒收 `PaymentSucceeded` v2）。

## 1. 环境与启动器

| 项 | 做法 |
|---|---|
| 启动器 | `verification/local-e2e/run-service.sh start\|stop\|status\|check\|jars <服务\|all\|java\|web>`。逐个启动并等待 `/actuator/health/readiness`（Platform 为 `/platform-support/actuator/health/readiness`）。JAR 先复制到 `local-environment/e2e/jars/`，源码重建不影响运行中的进程。 |
| JVM | `-Xms128m -Xmx384m`，另加 `-XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=320m`。 |
| 私密值 | 只从主仓库 `local-environment/.env.local` 与 `e2e/secrets.env` 读取（`E2E_LOCAL_ENV` 可改），经环境变量传入进程，不打印。新库账号口令按 `.env.local` 的 `MOR_<X>_DB_PASSWORD` 优先，否则取该服务 `*-test/init/ddl.sql` 的本机默认值。 |
| 运行状态 | worktree 的 `local-environment/e2e/`（PID、nginx、运行文件）与 `local-environment/logs/`（服务日志、`flows/` 流程日志、`browser-shots/` 截图），均被 `.gitignore` 忽略。 |
| Vault | 沿用 local-environment 容器 `mor-local-vault-service-1`（18107），不另起。Vault 策略从 `mor_vault` 读取，`policy.local.json` 已不生效。 |
| nginx | 18443 CLIENT、18444 OPERATIONS、18445 CLIENT_API、18446 CHECKOUT（本步无 Checkout 路由，未使用）。 |
| data-service | 依赖 Doris，不在 `all` 内，本步未起。 |

### 数据库与隔离（任务卡 §2.3 二选一：选 `*_v2e2e` 专用库）

auto 测试以 `mor_*` 为独占库，全局 AfterEach 会清空表并拒绝脏环境，因此全链路数据不能写入 `mor_*`。旧 `*_e2e` 库属于旧架构全链路，保留不动。

| 资源 | 本步使用 |
|---|---|
| MySQL | 15 个 `mor_<svc>_v2e2e` 库。14 个服务库用 `paytech/<svc>/docs/database/sql/<svc>-schema.sql` 与 initial-data 新建。`mor_platform_v2e2e` 由 `mor_platform_e2e` 复制后补差，沿用已交接的管理员与 Console 路由。Vault 用 `mor_vault`。 |
| RabbitMQ | vhost `/mor-e2e`（auto 测试会清空 `/mor-local` 的分区队列）。 |
| Redis | database 3（auto 测试与旧全链路用 0）。 |
| Nacos | 全部注册 `mor-local`。 |

### 评审 SQL（`verification/local-e2e/sql/`，均由主会话执行）

| 文件 | 目标 | 内容 |
|---|---|---|
| `00-e2e-databases.sql` | root | 15 个库，并给既有 `*_app` 账号授 DML |
| `apply-e2e.sh schemas` | 14 个服务库 | Schema、initial-data；compliance 加通用材料程序（第二轮起先设 `@approved_by`） |
| `apply-e2e.sh platform` + `20-platform-v2e2e-delta.sql` | `mor_platform_v2e2e` | 整库复制；补 `t_currency_spec` 与 4 个 Client API Scope |
| `30-vault-v2-callers.sql` | `mor_vault` | 7 条访问规则（见第 2 节） |
| `41-compliance-material-approver.sql` | `mor_compliance_v2e2e` | 补 3 行材料程序审批人（第一轮漏设 `@approved_by`） |
| `50-channel-connector-payermax-mock.sql` | `mor_channel_connector_v2e2e` | 合成 PayerMax 渠道、账号（DRAFT）、MID、能力 |
| `51-platform-external-api-routes.sql` | `mor_platform_v2e2e` | 44 条外部 API 与渠道沙箱运营路由、5 个资源、2 个授权 |

## 2. 一致性修正清单

| 项 | 修正 | 方式 |
|---|---|---|
| Nacos namespace | client-master、commercial、consumer、acquiring、gateway 默认 `public`，其余 `mor-local`。全部统一为 `mor-local`。 | 启动器设 `NACOS_NAMESPACE=mor-local` 与 `--spring.cloud.nacos.discovery.namespace=mor-local`；注册账号 `--spring.cloud.nacos.discovery.username`，密码经 `SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD` |
| Finance 直连 | billing、ledger、account、reconciliation、tax 不注册，以 URL 直连 | `LEDGER_ACCOUNT_SERVICE_URL`、`LEDGER_BILLING_SERVICE_URL`、`ACCOUNT_LEDGER_SERVICE_URL`、`ACCOUNT_BILLING_SERVICE_URL` |
| internal-auth | 全部 `OFF` | 有键的服务显式设置 `ACQUIRING_/CARD_/CHANNEL_CONNECTOR_/COMPLIANCE_/CONSUMER_/PAYMENT_/PLATFORM_SUPPORT_/RISK_DECISION_INTERNAL_AUTH_MODE=OFF`；client-master、commercial、gateway 在 application.yml 固定为 `"OFF"` |
| Vault 调用方 | 新增 `client-master-service`（入驻联系人 Email/Phone 的 Protect/Reveal）、`card-service`（`PAYMENT_CARD_DEK` 的 CARD_STORAGE/CARD_USE）、`channel-connector-service`（`CHANNEL_ACCOUNT_CONFIG_V1` 的 CHANNEL_EXECUTION Reveal） | 生效：`sql/30`；评审对照：`local-environment/vault/policy.local.json`（`git show 4f200b9b -- local-environment/vault/policy.local.json`） |
| Platform Webhook 调用方 | `commerce-service` 改为 `acquiring-service` | `--webhook.http.callers.acquiring-service=order.paid,order.cancelled,refund.created` |
| Platform 外部 API 执行 | 合成 PayerMax 是 `http://127.0.0.1:18190` | 仅本机 `--ps.integration.execution.allowed-schemes=https,http` |
| Snowflake 坐标 | 各服务独立 worker（Platform 1/2、client-master 3、risk-decision 4、compliance 5、acquiring 6、Vault 7、payment 8、card 9、consumer 10、billing 11、ledger 12、account 13、reconciliation 14、tax 15） | 各服务 `*_SNOWFLAKE_*` 环境变量 |
| card 指纹密钥 | 首次启动生成 32 字节随机值，存 `local-environment/e2e/runtime/card-fingerprint.key`（0600） | `CARD_FINGERPRINT_KEY` |

## 3. Health UP 清单

18 个 Java 服务 readiness 全部 UP，payermax-mock 与 nginx 监听，Vault 容器 readiness UP，共 21 项。启动耗时为启动器从拉起进程到 readiness UP 的秒数（依赖 Doris 的 data-service 未起）。内存为 `top` 的 MEM 列（macOS 含压缩），采样于全部流程跑完后。

| 服务 | 端口（业务/管理） | 启动耗时 | 内存 |
|---|---|---|---|
| platform | 18106（同端口） | 9s | 649M |
| client-master | 18101 / 19101 | 7s | 349M |
| commercial | 18108 / 19108 | 4s | 228M |
| compliance | 18112 / 19112 | 5s | 267M |
| risk-decision | 18104 / 19104 | 6s | 300M |
| consumer | 18109 / 19109 | 5s | 238M |
| card | 18111 / 19111 | 6s | 292M |
| channel-connector | 18110 / 19110 | 5s | 245M |
| payment | 18103 / 19103 | 6s | 313M |
| acquiring | 18102 / 19102 | 7s（修复后重启 6s） | 312M |
| billing | 18113 / 19113 | 5s | 239M |
| ledger | 18105 / 19105 | 4s | 248M |
| account | 18114 / 19114 | 5s | 196M |
| reconciliation | 18115 / 19115 | 2s | 160M |
| tax | 18116 / 19116 | 2s | 153M |
| client-bff | 18202 / 19202 | 4s | 254M |
| ops-bff | 18201 / 19201 | 4s | 280M |
| gateway | 18080 / 19080 | 首次超时（见第 5 节第 2 项），进程正常 | 287M |
| payermax-mock | 18190 | 2s | 126M |
| nginx | 18443–18446 | 0s | — |
| vault（容器） | 18107 | — | — |

- 本步进程（含两个 vite，各约 35M）合计约 5.2 GB。
- 宿主机峰值：PhysMem 23G used（2884M wired、11G compressor）、98M unused；含 Docker local-environment 与其他会话进程。

## 4. 六条流程结果

| 流程 | 脚本 | 结果 | 关键 ID / 说明 |
|---|---|---|---|
| Client 入驻 | `acceptance_flow.py --approve` | 41/41 | 原 39 步，加 2 步核对审核结论经 compliance → client-master 新信封落地。applicationId `237484270057172992`，caseId `237484274834493440` |
| | `scenario_flow.py` | 85/85 | 与原步数一致；身份键改读 `mor_client_master_v2e2e` |
| Client Console | `workspace_flow.py` / `developer_flow.py` / `team_flow.py` | 53/53、83/83、123/123 | 与原步数一致 |
| | 浏览器版（playwright-core 1.58.1） | 18/18、10/10、12/12、9/9 | `browser_acceptance`、`workspace_browser`、`developer_browser`、`team_browser`。浏览器脚本修正见第 5 节第 7–9 项 |
| Client API 下单 / 取消 / 退款（真实卡支付） | `register_client_api.py` → `client_api_flow.py` | 90/90；GAP 1（不计入） | 9 条路由经 Gateway Snapshot 生效，`idempotencyMode=REQUIRED`。真实支付：Checkout Customer API → Payment card-tokens → card → channel-connector → Platform PAY → 合成 PayerMax。attemptId `237487662708064256`，订单 `ord_3KrM85rKIOZsqN0zjSlp0c95HpWFrh` PAID，acquiring 与 card 各消费 `PaymentSucceeded` v2 一次。退款 `rfd_3KrM8efcch9Uj7XwSEiXGN0qGDC9l6` 创建成功。GAP：退款到不了 `RefundSucceeded`（第 6 节） |
| 通知与 Webhook | 含在 `client_api_flow.py` 与 `developer_flow.py` 中 | 29 步 Webhook 相关全部通过 | order.cancelled、order.paid、refund.created 投递到本机临时接收端，签名、冻结 Envelope、来源 `acquiring-service`、重复不再投递都已核对。邮件：本机未配置 SES，验证链接按旧做法在 DB 铸造（`mint_signup_link`），没有走 Mailpit |
| 卡直付 PayerMax 合成渠道（冒烟） | `risk_setup.py`、`channel_setup.py`、`card_smoke.py` | 9/9 | 5 个渠道沙箱用例，证据卡号只有 `444433******1111`。真实成功 Attempt 的 PAY_QUERY 回传，channelTransactionRef 一致。合成渠道 REFUND 同步成功 `RFD729333754072`。注：查询返回 `paymentStatus=FAILED`，是合成渠道按尝试号末位（6）决定的场景，不是系统缺陷 |
| Finance 账务内核（冒烟） | `finance_kernel_smoke.py` | 16/16 | factId `237482245391167488`，账簿 `237482244476813312`（OE `SM130`）。billing 编制人自批被拒、另一人批准后 EconomicFactRecorded 发布；ledger 分录借贷 25.00 平衡（OE 调整费用借 / Client 应付贷）；account 余额与 consistency 追平（appliedFactCount=1）；ledger、account 分区队列排空 |

日志：`local-environment/logs/flows/`（以 `-N` 后缀区分重跑，最后一次为准）。

## 5. 发现并修复的问题

### 生产代码 wiring 缺陷（1 处）

1. **acquiring 拒收 `PaymentSucceeded` v2，Hosted Checkout 订单永远到不了 PAID。**
   - 现象：真实卡支付 SUCCEEDED，card 消费了事件，acquiring 把事件送入 `acquiring.payment.dlq`，订单停在 OPEN，order.paid 不投递，退款也因订单未支付被拒（422）。
   - 根因：第 12 步把 Payment 的信封升为 `schemaVersion=2`（追加 card 使用的 `instrumentRef`、`consentToSave`、`customerId`），`PaymentSucceededMessageConverter#checkEnvelope` 仍要求 `schemaVersion == 1`。acquiring 测试的 `PaymentEventMocker` 也固定发 v1，所以 auto 测试没有发现。
   - 改动：
     - 转换器接受 v1 至 v2。
     - 测试 Mocker 默认发 Payment 当前的 v2，基类载荷补 v2 字段。
     - 新增 `PaymentSucceededListenerTest#test_schemaVersionCompatibility`：v1 仍推进为已支付，v3 进入 DLQ 且订单与投影不变。
   - 验证：`paytech-acquiring-test` 804/804 通过。acquiring 重打包、重启后，`client_api_flow` 订单 PAID、order.paid 送达。

### 启动器与流程脚本（不涉及生产代码）

2. Gateway 的 `/actuator/health` 经身份过滤器返回 401，readiness 实际早已 UP。启动器改查 `/actuator/health/readiness`。
3. Platform 没有独立管理端口，设置 `management.server.address` 会使启动失败。该参数只对其他服务设置。
4. macOS 自带 bash 3.2 在 `set -u` 下不能展开空数组；中文标点紧跟 `$var` 时被当作变量名的一部分。均已改写。
5. `apply-e2e.sh` 漏设 `@approved_by`，材料要求接口 503。主会话已执行 `sql/41` 补数据，脚本已修正。
6. `risk_setup.py`：Operations BFF 要求流程节点与连线的全部键都出现；validate、activate 路由的 body 上限为 0，不能带 `{}`。
7. `channel_setup.py`：复制来的 `mor_platform_v2e2e` 留有旧会话不完整的外部 API 配置（标准 API PAY / PAY_QUERY 0 个字段，接入方 PAYERMAX 引用它们），任何保存都被拒。脚本检测到后经运营入口按依赖倒序删除，再配置 `PAYERMAX_MOCK`。只影响副本，`mor_platform_e2e` 不变。
8. Python 3.9 没有 `datetime.UTC`。
9. 浏览器脚本：
   - `developer_browser.mjs` 的调用方仍是 `commerce-service`，已改为 `acquiring-service`。
   - `browser_acceptance.mjs` 用了不等待的 `isVisible({timeout})`，页面数据异步加载后才出现。改为 `waitFor({state:'visible'})`。
   - 运营列表中刚提交的案件显示“待审核”（SUBMITTED），脚本只认“审核中”。新旧 compliance 代码都不产生 IN_REVIEW，改为两种进行中状态都可打开。
   - 本机 MinIO 自签名证书下，Chromium 把下载记为 ERR_ABORTED 而不发出 download 事件。改为以浏览器实际请求的同一预签名地址核对 200 与 `Content-Disposition: attachment`（193 字节）。服务端下载能力另经 API 核对一致。

## 6. 未通过项与偏差

| 项 | 原因 | 处理 |
|---|---|---|
| 退款经 PAY-I04 到 `RefundSucceeded` | acquiring 的 `PaymentRefundClient` 是失败关闭的 `UnavailablePaymentRefundClient`（“Payment 退款接口尚未接入”），Payment 没有退款接口，仓库内没有 `RefundSucceeded` 发布方。退款停在 PROCESSING | 功能缺口，需单独需求；流程记为 GAP，不计入 |
| Consumer 付款不经 Gateway | Gateway 没有 CHECKOUT_SESSION 实现，Platform 没有 CHECKOUT 路由 | 私网直调 acquiring Customer API；nginx 18446 预留 |
| acquiring 内置固定 Mock | 商业事实（AE001、MCC 5999）、税、合同文档、Consumer 保护、结账页支付状态查询仍是 acquiring 内固定 Mock | 渠道种子按 AE001 / 5999 登记；结账页状态以数据库与提交接口为准 |
| `--outage`（Platform 不可用后补交） | 本机没有 XXL-Job Admin，补偿只在提交后执行一次，失败不重试 | 未验证；阶段 6 或 mor-test 再验 |
| 合成 PayerMax 约 30% 尝试失败 | 合成渠道按尝试号末位决定结果 | `client_api_flow` 换新订单重试，最多 5 次；本次第 1 次即成功 |
| 认证邮件不走 Mailpit | Platform 认证邮件只有 SES 适配器 | 沿用旧做法在 DB 铸造验证链接 |

## 7. 停止与清理

- `run-service.sh stop all`：22 个进程（含两个 vite）全部停止；表中全部业务、管理、nginx、vite、mock 端口均无监听，PID 文件为 0。
- Nacos `mor-local`：实例为临时实例，停机后已注销；`client-master-service`、`commercial-service`、`platform-support-service` 的空服务名由 Nacos 稍后自动清理。
- 本步数据只在专用资源中，没有写入 `mor_*` 或 `/mor-local`。是否删除由主会话决定，命令如下：

```sql
-- MySQL（root）：本步 15 个库只有全链路数据
DROP DATABASE mor_client_master_v2e2e; DROP DATABASE mor_commercial_v2e2e; DROP DATABASE mor_compliance_v2e2e;
DROP DATABASE mor_risk_decision_v2e2e; DROP DATABASE mor_acquiring_v2e2e; DROP DATABASE mor_consumer_v2e2e;
DROP DATABASE mor_payment_v2e2e; DROP DATABASE mor_card_v2e2e; DROP DATABASE mor_channel_connector_v2e2e;
DROP DATABASE mor_billing_v2e2e; DROP DATABASE mor_ledger_v2e2e; DROP DATABASE mor_account_v2e2e;
DROP DATABASE mor_reconciliation_v2e2e; DROP DATABASE mor_tax_v2e2e; DROP DATABASE mor_platform_v2e2e;
-- mor_vault：按 sql/30 头部回滚删除 7 条规则与 1 条变更记录；另有 channel_setup.py 经 Vault 人工加密生成的 1 个渠道账号配置 Token（REFERENCE）
```

```bash
# RabbitMQ：vhost /mor-e2e（acquiring.payment.dlq 里有 1 条修复前被拒的 PaymentSucceeded）
docker exec mor-local-rabbitmq-1 rabbitmqctl delete_vhost /mor-e2e
# Redis：database 3 当前 21 个 Key（会话、锁、Gateway 幂等与限流）
docker exec -e REDISCLI_AUTH=<MOR_REDIS_PASSWORD> mor-local-redis-1 redis-cli -n 3 FLUSHDB
# MinIO：mor-platform-files 中本步上传的入驻测试文件（每次入驻 1–2 个 193 字节 PDF）随 Platform 库删除后不再被引用
```

- 保留库时的增量清理：Finance 冒烟可用 `python3 finance_kernel_smoke.py --cleanup local-environment/e2e/runtime/finance-smoke-20261018114939.json` 打印清理 SQL。第一次因 Python 3.9 报错中断的运行另留下 1 个账簿（OE `SM378`，`237482217926868992`）与 1 个日历（`237482218019143680`）。`risk_setup.py` 第二次运行中断后留下 1 个未激活的流程与发布草稿，它们不影响 ACTIVE 发布。

## 8. delivery.md 阶段 5 行草稿

| 阶段 | 内容 | 状态 |
|---|---|---|
| 5 本机全链路 | 宿主机起 18 个 V2 Java 服务 + 合成 PayerMax + nginx + 两个 Web，连 local-environment（`*_v2e2e` 专用库、vhost `/mor-e2e`、Redis DB 3、Nacos `mor-local`、Vault 容器），21 项 Health UP。入驻 41/41 与 85/85；Console HTTP 53/53、83/83、123/123，浏览器 18/18、10/10、12/12、9/9；Client API 真实卡支付 90/90（退款到 RefundSucceeded 为已知生产缺口）；卡直付冒烟 9/9；Finance 账务内核冒烟 16/16。修复 acquiring 拒收 PaymentSucceeded v2（acquiring 804/804）。报告：[14-stage5-report.md](tasks/14-stage5-report.md) | 2026-10-18 本机通过 |

## 9. 后续

1. Payment 退款接口（PAY-I04）与 `RefundSucceeded` 发布，接通 acquiring 的 `PaymentRefundClient`（需单独需求）。
2. Gateway CHECKOUT_SESSION 与 Platform CHECKOUT 路由，让 Consumer 付款经正式入口。
3. Vault `internal-auth` 由 OFF 切到 DUAL / REQUIRED（D26），并为各服务登记签发白名单。
4. Nacos namespace 统一进各服务 application.yml 默认值（目前靠启动器覆盖：client-master、commercial、consumer、acquiring、gateway 默认 `public`）。
5. billing `messaging.publisher.unroutable-policy` 仍为 `ACK_AND_WARN`，注释写明 ledger / account 消费者接入（第 11b 步）后改回 `FAIL`。本步未改（运行中没有造成失败），建议单独修改并跑 billing 测试。
6. 本机 XXL-Job Admin（`compose.scheduler.yaml`）接入后补验补偿重试与 `--outage` 场景。
7. 浏览器下载在自签名 MinIO 下不触发 download 事件；mor-test 用受信 CA 时可恢复原断言。
