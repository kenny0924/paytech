# 阶段 5 · 本机联调报告（后端，W5，2026-10-29）

> 只操作本机：`mor-local-*` 容器（MySQL 13306 的 `*_v2e2e` 库、RabbitMQ `/mor-e2e`）与 `local-environment/e2e` 进程。未连接 mor-test，未替换 ops-bff / Gateway / platform-support，未提交、未推送。新 JAR 与 tax-service **保持运行**；acquiring 按主会话裁决（方案 A）**保持 REAL 模式**。
> 场景驱动：`verification/stage5/stage5_tax.py`（`python3 -I stage5_tax.py s1|s2|s3|s4|s5`，REAL 模式下加 `TAX5_DECLARED=US`）；结果：`verification/stage5/stage5-tax-result.json`（不含卡号与 Checkout Token）。

## 0. 结论

| 场景 | 结果 |
|---|---|
| S1 沙特 MoR | **tax 侧全部通过**：资料提交后 SA 15% 最终报价 115.00、合同重渲染、支付、正式事实、底稿 PENDING（汇率缺失，D-01 预期）、税票 ISSUED 且可经 Platform 下载。**billing → account → ledger 未走通**：本机财务基础数据缺失（第 6 节） |
| S2 被阻断 | 通过 |
| S3 NOT_MANAGED | tax 侧通过（billing 同样未走通，"无 SELLER_TAX" 没有 billing 实证） |
| S4 AC-10 | **tax 侧通过**：tax 停机时付款成功，事件在 `tax.acquiring.p3` 等待（消费者 0），tax 恢复后补登记，税额与冻结计算一致、税票 ISSUED。billing 在 tax 停机期间照常处理该事件、调用链不含 tax，但因同一财务基础数据缺失未能入账，"金额与 billing 一致"未能实证 |
| S5 存量订单 | 读取通过；存量未付订单均已过期，未实走存量支付回调 |

## 1. 现状记录（替换前）

| 项 | 内容 |
|---|---|
| acquiring | pid 68066，JAR 2026-10-25 10:02；商业解析模式 MOCK（未设 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE`） |
| billing | pid 40263，JAR 2026-10-27 23:36 |
| account | pid 63920，2026-10-19 起运行；**磁盘上的 JAR 已被删除**（进程持有已删除文件），无法 `cp -p` 备份，见第 2 节 |
| payment | pid 50002，JAR 2026-10-22 23:47（早于 `98210e0b`） |
| ledger | pid 40150（未动）；`mor_ledger_v2e2e` 只有 1 个科目表（29 个科目），**没有 Finance Entity、账簿、财年日历与会计期间** |
| reconciliation | 本机 e2e **没有 reconciliation 进程** |
| tax | 本机 e2e 无 tax 进程；`jars/paytech-tax-app-exec.jar` 为 2026-10-19 旧版；`mor_tax_v2e2e` 存在但无表 |
| AE001 Party | `mor_client_master_v2e2e` 为 900000000001380102；acquiring MOCK 配置为 880000000702。先按 MOCK 取 880000000702，方案 A 后改为真实值（第 2 节 22:49） |
| tax_app 权限 | 对 `mor_tax_v2e2e` 已有 DML 权限，无需改动 |
| internal-auth | tax / acquiring / billing / account / payment 均为 OFF；Platform 为 DUAL（接受 `X-Caller-Service` 回退） |

## 2. 环境变更清单

| 时间（+04） | 动作 | 备份 / 回滚依据 |
|---|---|---|
| 22:20 | worktree 构建 tax / acquiring / billing / account exec JAR（`package -DskipTests -pl … -am`），复制到 `local-environment/e2e/staging/tax-20261029a/`（含 `SHA256SUMS`） | — |
| 22:21 | mysqldump `mor_tax_v2e2e`（空库）、`mor_acquiring_v2e2e` 的 `t_commerce_order` / `t_order_context` / `t_consumer_contract`（各 14 行） | `stage5/backup/mor_tax_v2e2e.full.20261029.sql`、`stage5/backup/mor_acquiring_v2e2e.order-context-contract.20261029.sql` |
| 22:21:21 | `mor_tax_v2e2e` 执行 `changes/2026-10-29-tax-rebuild.sql` 与 `tax-initial-data.sql`（脚本无 `USE`，以库名参数执行；`@ae001_party_id` = 880000000702） | 16 张表；种子：税类 1（`SAAS_AUTOMATED`）、准入 3、注册 3、规则包 3、税票序列 3。回滚：`changes/2026-10-29-tax-rebuild-rollback.sql` |
| 22:21:29 | `mor_acquiring_v2e2e` 执行 `changes/2026-10-29-order-tax-calculation.sql` | 新建 `t_order_tax_calculation`；14 笔存量订单 `amount_status=FINAL`。回滚：`changes/2026-10-29-order-tax-calculation-rollback.sql` |
| 22:22:57 | **本机专用** Platform 文件写入路由 `(tax-service, PRIVATE)`：`stage5/30-platform-tax-file-route-local.sql`（ID 900000000000990801，复制默认私有路由的桶与扫描策略 `CLIENT_ONBOARDING_FORMAT`，允许 PDF） | 备份 `stage5/backup/mor_platform_v2e2e.t_file_storage_route.20261029.sql`；回滚 `stage5/30-platform-tax-file-route-local-rollback.sql`。不需要 internal-auth 白名单：Platform 文件接口没有调用方白名单配置，DUAL 接受 `X-Caller-Service` |
| 22:23:29 | 安装 tax JAR（`.tmp` + `mv -f`），首次启动 tax-service | 旧 JAR 备份 `jars/paytech-tax-app-exec.jar.pre-tax`；readiness UP（19116）；`tax.acquiring.p0..p3` 各 1 个消费者，`tax.acquiring.dlq` 已声明 |
| 22:24 | 为 account 生成回滚 JAR：临时 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax-base`（`e3e0c12f`，detached）构建 account | `jars/paytech-account-app-exec.jar.pre-tax`（= `e3e0c12f` 的 account；原运行 JAR 已不在磁盘） |
| 22:25:06 | billing：停 → 安装 → 按原 argv / 环境重启 | 备份 `jars/paytech-billing-app-exec.jar.pre-tax`；pid 9787，readiness UP（19113），`billing.acquiring.p0..p3` 各 1 |
| 22:25:14 | account：同上 | pid 9900，readiness UP（19114），`account.billing.p0..p3` 各 1 |
| 22:25:37 | acquiring：同上（库迁移已先完成） | 备份 `jars/paytech-acquiring-app-exec.jar.pre-tax`；readiness UP（19102），`acquiring.payment.p0..p3` 各 1 |
| 22:32:46 | tax 重启：加本机信任库与 XXL 执行器（第 4 节 B-2、B-4） | 信任库 `local-environment/e2e/runtime/tax/minio-truststore.p12`（JDK cacerts + 本机 MinIO 自签证书，证书取自 `mor_platform_v2e2e.t_file_storage_instance`，口令为 JDK 默认值）；执行器 127.0.0.1:19993，`appname=tax-service`，调度中心地址沿用 acquiring 的占位 `http://127.0.0.1:1` |
| 22:35—22:38 | S3：acquiring 两次临时以 REAL 重启，场景后恢复 MOCK | — |
| 22:46:23 | 主会话批准后替换 payment：worktree 构建（Payment 与 `e3e0c12f` 相同；自本机旧 JAR 以来无 Payment 库结构变更），停 → `.tmp` + `mv -f` → 按原 argv / 环境重启 | 备份 `jars/paytech-payment-app-exec.jar.pre-tax`（2026-10-22 23:47 版）；pid 24346，readiness UP（19103），`payment.channel-connector.p0..p3` 各 1 |
| 22:49 | 方案 A：mysqldump `mor_tax_v2e2e` 全库后执行 `stage5/40-tax-demo-party-real-local.sql`：准入、注册、税票序列各 3 行的主体 880000000702 → 900000000001380102。重跑 `tax-initial-data.sql` 会因固定 ID 冲突，改为原位更新；已有 S1—S3 计算、交易与税票保留原值（可读） | 备份 `stage5/backup/mor_tax_v2e2e.full.before-real-party.20261029.sql`；回滚 `stage5/40-tax-demo-party-real-local-rollback.sql` |
| 22:50:01 | tax 重启（读取新种子） | — |
| 22:50:11 | acquiring 以原 argv / 环境 + `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL` 重启，**保持 REAL** | 切回 MOCK 见第 8 节 |
| 22:52—22:58 | S4 两次停 / 启 tax（场景内） | 当前 tax pid 39660，readiness UP，四个分区各 1 个消费者 |

当前进程：tax 39660、acquiring 28410（REAL）、billing 9787、account 9900、payment 24346。

tax-service 启动方式（与 billing 同一进程模板，凭据不落报告）：JDK 25、`-jar jars/paytech-tax-app-exec.jar`，加 `-Djavax.net.ssl.trustStore=…/runtime/tax/minio-truststore.p12`；`TAX_DB_URL=…/mor_tax_v2e2e`、`TAX_DB_USERNAME=tax_app`（口令为 tax-test 本机默认值）、RabbitMQ `/mor-e2e`（`mor_local`）、`TAX_PLATFORM_SERVICE_URL=http://127.0.0.1:18106/platform-support`、`TAX_INTERNAL_AUTH_MODE=OFF`、`TAX_COMPENSATION_INSTANCE_ID=tax-local-e2e`、Snowflake Region 0 / **Worker 14**（本机已占用 1—13、16）、`XXL_JOB_EXECUTOR_*`（端口 19993、appname `tax-service`），端口 18116 / 19116；`tax.pid` / `tax.started` 已写，日志 `local-environment/logs/tax.log`。

## 3. 场景结果

Client 侧经私网直连 acquiring Client API（带 Gateway 可信 Header，与 20261022、20261023 阶段 5 相同；本机没有 Client API `ck-` 凭证，经 Gateway 需先走 Client 入驻自助建凭证）。卡支付走 Payment → card / risk-decision / channel-connector → Platform → 合成 PayerMax。

### 3.1 S1 沙特 MoR

正式运行（REAL）：`ord_3LNgHd0TWGaDLjatKpMN9CnRwDu7TN`；MOCK 期间另有两笔（`ord_3LNdh7rIvmeRORFbLYgk4LTbAip0GR` 探索运行、`ord_3LNforgSkJWwLAGP5KSSyRd8mMj5b0` Payment 替换后），tax 侧结果相同。

| 步骤 | 结果 |
|---|---|
| 建单 | REAL 下该 Client 的 OE Scope 只覆盖声明国家 US（AE → `NO_ELIGIBLE_ENTITY`），故声明 US：rev 1 `PROVISIONAL BLOCKED`（`MARKET_NOT_ENABLED`），建单成功（D-02）。MOCK 期间声明 AE：rev 1 `PROVISIONAL READY`，`ESTIMATED` 100.00 + 5.00 = 105.00 |
| 资料提交（账单国家 SA） | `FINAL`，100.00 + 15.00 = 115.00；rev 2 `FINAL READY` SA，`expires_at` = 计算时间 + 24h；合同重渲染（第 2 份合同绑定 rev 2）；可 PAY |
| 支付 | SUCCEEDED（尝试 241634929207705600），订单 PAID，捕获 115.00 |
| tax 正式事实 | `t_tax_transaction` 241634933796298752：ORDER_PAYMENT_SUCCEEDED，AUTO / ORIGINAL，引用 FINAL 计算 241634923092434944；`PENDING` / `FX_RATE_MISSING`，底稿 0 行（D-01：交易币种 USD、申报币种 SAR，汇率源未接入） |
| tax 订单查询 | `GET /tax/v1/orders/{orderNo}` 200：计算 2 条，当前 READY，交易 1 条 |
| 税票 | `SA-RCPT-2026-000003` ISSUED，开票方为真实 Party 900000000001380102，`/files/v1/241634936086335488`。Platform 元数据：tax-service / TAX_RECEIPT / PRIVATE / application/pdf / 1140 字节；以 ops-bff 身份签发下载地址并下载：PDF 魔数正确，SHA-256 与 `document_digest` 一致，内容含序列号、`DEMO-SA-TRN-0001`、15.00、115.00、USD（英文版，阿拉伯语按方案 A 待补）。全部税票号 000001—000004 连续，无跳号 |
| billing → account → ledger | **未走通**，见第 6 节 |

### 3.2 其余场景

| 场景 | 订单号 | 结果 |
|---|---|---|
| S2 被阻断 | `ord_3LNeFr4WMLAQVvoRXmXnUZLX3a6ARo` | 通过：建单 AE 暂估 105.00 → 资料 KW → FINAL `BLOCKED`（`MARKET_NOT_ENABLED`），金额 `taxAmount/totalAmount=null`；支付 409 `CHECKOUT_NOT_READY`；订单 OPEN、无支付投影；tax 无正式事实 |
| S3 NOT_MANAGED | `ord_3LNeiE9WoN6f1SMqHv3iaNOfMSv7fc` | tax 侧通过：acquiring 临时 REAL；PSP Client 900000000001380201（TAX_RESPONSIBLE = Client Party 900000000001380202）。PROVISIONAL（US）与 FINAL（SA）均 `NOT_MANAGED`（`SUBJECT_NOT_REGISTERED`），金额 100.00 无税；已支付；tax 无正式事实、无税票。billing 未走通，"无 SELLER_TAX" 没有 billing 实证 |
| S4 AC-10 | `ord_3LNh7dqb0r8HtVN9eyLdmSSjiVw7Hp` | tax 侧通过，见下 |
| S5 存量订单 | 3 笔替换前 PAID 订单 | 读取通过：Client GET 200、`amountStatus=FINAL`、`taxAmount=0.00`；`t_order_tax_calculation` 0 行；tax 订单查询 404 `TAX_ORDER_NOT_FOUND`（不导入）。6 笔存量 OPEN 订单均已过期（本机无到期扫描），未改库造数，存量订单支付回调未在本机实走（acquiring auto 测试覆盖） |

S4 过程（REAL，声明 US、账单 SA）：

| 时间（UTC） | 事件 |
|---|---|
| 18:57 前 | tax 运行时备好 3 笔报价完成的订单（合成 PayerMax 对尝试 ID 末位 2/3/4 返回错误，首轮运行即因末位 4 失败，故预备多笔） |
| 18:57:52 | 停 tax；readiness 不可达 |
| 18:57:5x | 第 1 笔支付 SUCCEEDED（尝试 241636691075432448），订单 PAID，115.00 |
| 18:57:57 | billing 照常消费该 OrderPaymentSucceeded（tax 已停机），取价通过，失败点为 ledger Finance Entity（第 6 节），调用链不含 tax |
| 停机期间 | `tax.acquiring.p3` 消息 1、消费者 0；tax 无该订单正式事实 |
| 18:58:45 | 启 tax，readiness UP |
| 18:58:51 | 正式交易 241636919497252864 登记，引用 FINAL 计算 241636668187140096（SA VAT 15.00）；税票 `SA-RCPT-2026-000004` ISSUED；`tax.acquiring.dlq` 0 |

"恢复后补登记"在本机是消息在分区队列中等待消费者恢复（没有消费失败，也就没有消息重投）：tax 不在线时事件不丢、不进 DLQ。

## 4. 发现的问题与处理

| 编号 | 问题 | 处理 |
|---|---|---|
| B-1 | acquiring 以 `lb://tax-service` 调用 tax，但 tax 不注册 Nacos（tax 父 POM enforcer 禁用 `com.alibaba.cloud:*`，与 billing / account / ledger 同一做法；ops-bff 也以固定地址 `OPERATIONS_TAX_BASE_URL` 访问 tax）。部署后 acquiring 建单无法解析 tax 实例 | **worktree 修复（主会话已接受）**：acquiring `application.yml` 的 `tax-service.base-url` 改为 `${ACQUIRING_TAX_SERVICE_URL:http://127.0.0.1:18116}`，`TaxClientConfiguration` 类注释同步；测试配置不变（仍用 simple discovery）。重测：acquiring 模块 auto **916 / 916**（`package`，本机 MySQL） |
| B-2 | 税票上传失败 `TAX_DOCUMENT_UPLOAD_FAILED`：tax 是第一个**服务端**直传对象存储的服务（其余上传均由浏览器直传），本机 MinIO 为 `https://127.0.0.1:19400` 自签证书，tax JVM 不信任 | 本机环境处理（主会话已接受）：tax 启动参数加信任库。未改代码。修复后经补偿扫描重试，**同号**签发（SA-RCPT-2026-000001） |
| B-3 | 补偿任务失败只记 `Tax document upload requires retry`，tax 日志无原因 | 未改（不影响功能）；建议 `PlatformTaxDocumentFileClient` 失败时记录异常类型与 HTTP 状态（不记地址） |
| B-4 | tax 补偿任务 ERROR 后依赖 XXL 兜底扫描；tax `application.yml` 无 `xxl.job.executor` 段 | 本机以环境变量启用执行器，手动 `/run` 触发 `compensationEventScan`，重试成功 |
| B-5 | 共享 e2e 脚本使用税类 `STANDARD`（判为未知税类 → BLOCKED） | 未修改，清单见第 5 节 |
| B-6 | acquiring MOCK 商业配置写死 `clientPricingVersionId=104` / `partnerShareVersionId=108` / `platformChargeVersionId=109` / `clientAgreementId=105`，本机 commercial 中不存在，MOCK 模式下 billing 恒取价失败（与 tax 无关） | 按方案 A 改用 REAL |

## 5. 使用 `STANDARD` 税类的既有脚本（未修改）

- `requirements/20261022/acquiring-到期后支付结果确认/release/acceptance/accept_result_after_expiry.py:276`
- `requirements/20261022/acquiring-到期后支付结果确认/verification/stage5_result_after_expiry.py:115`
- `requirements/20261022/acquiring-订单过期收尾/verification/stage5_expiry.py:109`
- `requirements/20261023/commercial-商业关系快照与真实解析接入/release/acceptance/stage7_relsnap.py:199`
- `requirements/20261023/commercial-商业关系快照与真实解析接入/verification/local-e2e/stage5_backend.py:104`
- `requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/commerce_webhook_flow.py:122,124`
- `requirements/20261017/commerce-ClientAPI测试环境联通/verification/client_api_flow.py:203`
- `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/client_api_flow.py:365`
- `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/webhook_flow.py:123,125`

## 6. 阻断：本机 billing 无法登记支付事实（停在已授权范围）

依次排除的原因：

1. 本机 payment JAR 早于 `98210e0b`，事件缺 `channelTransactionRefId` → **已替换 Payment 解决**。
2. MOCK 商业配置的定价版本不存在（B-6）→ **已按方案 A 改 REAL 解决**。
3. 当前失败（REAL 下 S1、S4 两笔一致）：`法律 Party 没有 ACTIVE Finance Entity: 900000000001380900`（平台签约 Party），billing 在 `RecordPaymentFactProcessor.loadPartnerTerms` 处重投 5 次后进入 `billing.acquiring.dlq`（现有 5 条，均为本阶段订单）。

**未继续处理的原因：**主会话授权的是"ledger 记账因缺账簿失败时，经运营 / ledger API 建最小 AE001 / USD 账套"。现状超出这个范围：

- 失败发生在 billing，还没到 ledger；所需 Finance Entity 属于平台 Party 900000000001380900，不属于 AE001。
- `mor_ledger_v2e2e` 没有任何 Finance Entity、财年日历、期间或账簿，需要从头建账套。
- billing 支付事实还依赖 channel-connector 渠道成本计划与 reconciliation 清算账户（参照 `requirements/20261022/billing-支付事实自动登记/release/acceptance/smoke_upstreams.py` 的四个上游）；**本机没有 reconciliation 进程**。

因此 billing 的 `SELLER_TAX` 行（AC-09 本机实证）、account 入账、ledger `TAX_PAYABLE` 贷记，以及 AC-10 的"金额与 billing 一致"，本机均未验证。这些已由 auto 测试覆盖（billing 142、account 68、ledger 332），端到端实证建议放到 mor-test 阶段 6—7（该环境的 commercial、ledger、channel-connector、reconciliation 已在 `98210e0b` 跑通）。

## 7. 阶段 6 前置条件

1. **acquiring**：发布含 B-1 修复的镜像，并配置 `ACQUIRING_TAX_SERVICE_URL` 指向 tax-service 私网地址（tax 不注册 Nacos）。
2. **tax 服务端直传对象存储**：确认 mor-test 存储端点的协议与证书，tax 容器须信任该证书（或走私网 http）；否则税票全部停在 RESERVED。
3. **tax XXL 执行器**：以 `XXL_JOB_*` 环境变量启用执行器，并在 xxl-job-admin 登记 `compensationEventScan`；否则开票失败后不会重试。
4. **tax 演示纳税主体**：方案 A 后本机已使用真实 AE001 Party；mor-test 的 `tax-initial-data.sql` 须把 `@ae001_party_id` 替换为 mor-test `client-master` 中 AE001 的实际 `party_id`（与 acquiring 运行模式一致）。MOCK 演示值 880000000702 在 mor-test 不再适用。
5. Platform 注册块中的 tax-service 私有写入路由：本机默认私有扫描策略允许 PDF；mor-test 执行前同样核对。

## 8. 恢复方法

1. **acquiring 切回 MOCK**：按 `ps eww` 捕获的当前环境去掉 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL` 后重启（argv 不变）；同时对 `mor_tax_v2e2e` 执行 `stage5/40-tax-demo-party-real-local-rollback.sql`，**tax 演示主体须为 880000000702**（MOCK 配置的 AE001 Party），否则 MoR 订单会成为 NOT_MANAGED；再重启 tax。
2. 停 tax：`kill $(cat local-environment/e2e/tax.pid)`（本机原本没有 tax 进程）。
3. JAR：billing / account / acquiring / payment 各以 `*.pre-tax` 经 `.tmp` + `mv -f` 还原，按原 argv / 环境重启（account 的 `.pre-tax` 为 `e3e0c12f` 构建；payment 的 `.pre-tax` 为 2026-10-22 版，还原后 billing 会再次因缺渠道交易引用拒绝事件）；tax 的 `.pre-tax` 为 2026-10-19 旧 JAR。
4. 库：先停 acquiring，对 `mor_acquiring_v2e2e` 执行 `paytech/acquiring/docs/database/sql/changes/2026-10-29-order-tax-calculation-rollback.sql`（worktree），再还原 acquiring JAR；`mor_tax_v2e2e` 执行 `2026-10-29-tax-rebuild-rollback.sql`，或按备份恢复为空库。
5. Platform：`mor_platform_v2e2e` 执行 `stage5/30-platform-tax-file-route-local-rollback.sql`。tax-service 已上传 4 份税票，按脚本注释只保留路由、不删除，或先处理已上传文件。
6. RabbitMQ：`billing.acquiring.dlq` 中本阶段 5 条消息可保留或清理，不会被自动重放。
7. 临时 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax-base` 不再需要 account 回滚 JAR 时可 `git worktree remove`。
