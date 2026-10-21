# reconciliation 渠道资金对账切片 1：阶段 5 本机验收报告

> 2026-10-20（UTC 19:05–19:55），只在本机执行；未碰 mor-test，未提交 / 推送 / 部署，未改生产源码。需求 [requirement.md](../requirement.md)，方案 [technique.md](../technique.md)。

## 1. 环境与版本

| 项 | 值 |
|---|---|
| 代码 | `main` HEAD `04e127c3`，工作区有本需求及其他会话的未提交改动（`git status` 231 项，其中 reconciliation 相关 124 项），验收用的是工作区当前源码 |
| JDK / 构建 | JDK 25，`./mvnw -B -o`；本次构建：billing-api、reconciliation api/core/app、ledger api/core/app、billing core/app、account api/core/app、operations-console-bff app（`-am`）`install -DskipTests` |
| 共享基础设施 | local-environment：MySQL 13306、Redis 16379（db 3）、RabbitMQ 5672、Nacos `mor-local`、Vault 容器 `mor-local-vault-service-1` |
| 共享进程（他人启动，只读使用，未重启） | platform 18106、channel-connector 18110、Vite ops-web 18301 |
| 私有栈（本次启动，已停止） | [stack.sh](./stack.sh)：reconciliation 18115（`mor_reconciliation_v2e2e`、临时 vhost `/mor-recon5`、XXL 关闭）、ops-bff 18211（当前源码，Nacos 只发现不注册）、Gateway 18090（SimpleDiscovery → platform 18106 / ops-bff 18211）、Nginx 18454 |

**为何另起私有栈：** 本机 run-service.sh 栈的 ops-bff（18201，他人会话 10:21 启动）是不含对账接口的旧 JAR，任务规则不允许重启他人进程；共享 Gateway 经 Nacos 选 BFF 实例，另注册一个实例会被轮询到旧实例。因此私有 BFF 不注册 Nacos，私有 Gateway 用固定发现；RabbitMQ 另建临时 vhost，避免运行中的 account（`/mor-e2e`）消费合成 `BillingFactRecorded`。run-service.sh 未修改。

## 2. 模块测试门禁（本机 local-environment，逐个串行）

| 模块 | 结果 | 说明 |
|---|---|---|
| `paytech-reconciliation-test` verify | **90/90 通过** | |
| `paytech-ledger-test` verify | **319/319 通过** | 本机 `mor_ledger` 今日重建后首次补跑 |
| `paytech-billing-test` verify | **81/81 通过** | |
| `paytech-account-test` verify | 63 例：5 通过、57 失败、1 错误（**既有环境问题，与本需求无关**） | 根因：本机 `mor_account` 缺 `t_owner_account_no_sequence`（`e73764d1` 会计科目分级引入，Schema 有、本机库未同步），首例 `BadSqlGrammarException` 后清理器判定环境不可复用，其余全部连带失败。本需求未改 account；本机 `mor_account` 重建需另行授权 |

## 3. 本机全链路 S1（经 Nginx → Gateway 权限点 → BFF → reconciliation）

脚本 [s1_flow.py](./s1_flow.py)（公共件 [recon5_common.py](./recon5_common.py)，合成事实发布 [BillingFactPublisher.java](./BillingFactPublisher.java)，补充 [extra_checks.py](./extra_checks.py)）。

- **人员：** 经 IAM 真实接口新建 MAKER（对账读 + 操作 + 财务读）、CHECKER（对账读 + 审批 + 财务读）两名合成人员（首登改口令）；浏览器另建 NOPERM（只有财务读）。
- **MID：** 运营管理员经 channel-admin 接口建渠道 `PAYERMAX_MOCK`（Platform 已有同编码接入方）、AE001 技术账号（Vault Protect 配置后激活）+ ACTIVE MID + USD 能力组，AE002 账号 + DRAFT MID（负例）。
- **事实：** 以正式发布器（`RabbitDomainEventPublisher` + `DomainEventCodec`，与 reconciliation-test 的 `BillingEventPublisher` 相同）发布到 `billing.events`：PAYMENT 100.00（`CHANNEL_COST` 0.45）、REFUND 30.00，带 `CLEARING_ACCOUNT`、`CHANNEL_TRANSACTION_REF`（`NI_ORDER_REF` / `NI_REFUND_REF`）引用；另发重复 PAYMENT、MANUAL_ADJUSTMENT、缺清算账户引用的 PAYMENT 各一条。
- **观察：** 临时队列 `recon5.probe.reconciliation-events` 绑定 `reconciliation.events`（事件按 I-08 无订阅方会发布失败，验收期间由该队列承接）。

脚本按运行记录续跑，共三次：第一次在第二个银行账户提交处中断（复跑同一请求成功，手工补做该项两条检查），第二次银行对账单开始请求漏传 `bankAccountLast4` 返回 422（脚本问题，修正后第三次通过）。三次合计 **78 项检查通过**（含准备 14 项），补充脚本 6 项通过；除上述 2 项脚本问题外无失败。

| AC | 结果 | 本机证据（摘要） |
|---|---|---|
| AC-01 | 不在本阶段重验 | 阶段 4 门禁已过（模型 / Schema / 测试 DDL 一致、表归属、生成器摘除）；本机 `mor_reconciliation_v2e2e` 15 张表供本次全链路使用 |
| AC-02 | **通过** | 未归属 MID 列表含新激活 MID（USD）、不含 DRAFT MID；新建清算账户、挂 MID（channel-connector 实时核对）；同一 MID + 币种重复归属被拒；摘 MID 后挂到 AE002 账户被拒「MID 经营主体与清算账户不一致」；DRAFT MID 被拒「MID 未激活」；不存在 MID 被拒；挂上后不再出现在未归属列表；解析接口命中返回账户编码与 AE001，EUR 命中不到返回 `CLEARING_ACCOUNT_NOT_RESOLVED` |
| AC-03 | **通过** | Maker 提交 → `PENDING_VERIFICATION`；Checker 批准 → `ACTIVE`；库中只有 43 位 Vault Token + 末四位 4321，整行无明文；详情、列表、内部 `cash-accounts/current` 只返回末四位；第二个同主体 + 币种 + 用途账户批准被拒，拒绝后 `CLOSED`；同时有操作与审批权限的管理员复核自己的提交被 reconciliation 拒绝「提交人不能复核自己的提交」 |
| AC-04 | **通过** | PAYMENT → `RECEIVABLE` 100.00、预估费 0.45；REFUND → `PAYABLE` 30.00；同一事实重复投递只一条，MANUAL_ADJUSTMENT 不生成；缺清算账户引用的事实重投后进 `reconciliation.billing.dlq`（1 条）留存 |
| AC-05 | **通过** | 账单头与明细合计不一致 → 完成导入整单 `REJECTED`，返回 2 条原因；S1 账单分 1 段导入 → `MATCHING`（3 行）；同批次有 `MATCHING` 版本时再导入被拒「须先作废」；已对平账单作废被拒 |
| AC-06 | 部分（S1 涉及的规则通过） | 带交易号的 PAYMENT / REFUND 行完全匹配（行 1 实际费 0.45）；调整行开阻塞的 `UNEXPLAINED_ADJUSTMENT`（差异 −2.00）。规则 1–7 其余分支（部分匹配、超出、多条命中、跨账单重复、带 / 不带交易号 FEE 行、新流水重试）由 reconciliation-test 90 例覆盖，本机未逐一重走 |
| AC-07 | 部分 | 实际费 0.45 = 预估 0.45 → 未发 `ActualChannelCostConfirmed`（按任务给定金额）；"不同才发"与作废回退由模块测试覆盖 |
| AC-08 | **通过** | 对平前详情门禁显示「调整行已归类」「无阻塞异常」未满足；处置批准后 `RECONCILED` 并生成预期银行流水；`ChannelBatchReconciled`：净额 67.55 = 100.00 − 30.00 − 0.45 + (−2.00)，`classifiedLines` 带行 3、`CHANNEL_FEE_OTHER`、−2.00、处置 ID |
| AC-09 | **通过** | 期初 + 流水 ≠ 期末整单拒绝；S1 对账单 `IMPORTED`（首张无缺口异常）；同银行账户 + 交易号再次出现时整单拒绝（I-15），库中该交易号仍只有 1 行。`BANK_STATEMENT_GAP` 本机未单独构造（模块测试覆盖） |
| AC-10 | **通过** | 67.40 ≠ 67.55 没有一对一建议；手工匹配 67.41 超出被拒，67.40 → `CONFIRMED`；`ChannelSettlementConfirmed` 一条（聚合 `BANK_MATCH`、67.40、银行流水 ID）；最终账单 `CASH_CONFIRMED`、已到账 67.55。"先到钱后有账单自动出建议"由模块测试覆盖 |
| AC-11 | **通过**（三种处置中本机走了 CLASSIFY） | 账单行异常只列 4 个归类码，`BANK_FEE` 被拒，金额不等于差异被拒；Checker 批准同一事务内 `APPLIED` + 异常 `RESOLVED`；账单行归类未单独发 `ReconAdjustmentApproved`；预期银行流水人工开 `BANK_AMOUNT_MISMATCH`（系统算 0.15），可选码 `BANK_FEE`、`RECON_DIFFERENCE`，CLASSIFY `BANK_FEE` 批准后发一条 `ReconAdjustmentApproved`（`EXPECTED_BANK_MOVEMENT`、0.15、银行账户 ID、maker ≠ checker），预期流水 `CONFIRMED`（确认 67.40 + 归类 0.15）。平台流水 / 银行流水的归类、`RE_MATCH`、`ACCEPT_TIMING_DIFFERENCE` 由模块测试覆盖 |
| AC-12 | **通过**（发布侧） | 三类事件信封 `sourceService=reconciliation`、聚合版本 1、聚合类型正确，均在本地提交后经补偿任务发布到 `reconciliation.events`；目录登记见阶段 4 |
| AC-13 | 本机未覆盖 | 私有栈未连 XXL（本机无调度中心）；`channelLineWaitExpiry` 由模块测试覆盖 |
| AC-14 | **通过** | 经真实 Gateway：CHECKER 调提交路由（新建银行账户、提交处置）403；MAKER 调复核路由（银行账户、处置）403；reconciliation 拒绝提交人复核自己的提交（见 AC-03） |
| AC-15 | 本阶段只重跑上表模块 | BFF 1939、Web 1194、arch-rules、PDManer 门禁见阶段 4 记录；`npm --prefix requirements-site test` 本阶段未重跑 |
| AC-16 | 不适用 | 阶段 6–7 |
| 汇总（F5） | **通过** | 导入前全 0；对平待到账：已对平 1 / 未到账 67.55；`CASH_CONFIRMED` 后回零 |

## 4. 浏览器验收 AC-V1–V6

脚本 [browser_acceptance.py](./browser_acceptance.py)（Playwright 无头 Chromium，1512×900，经 https://127.0.0.1:18454 真实登录）。场景 S2：PAYMENT 50.00（预估费 0.30）与 60.00 两条事实；账单 A（50.00 + 1.00 调整行）、账单 B（60.00）；银行一笔 100.00 人工多选匹配。共跑两次（第一次菜单检查脚本把已展开的「财务」点收起，改为按需展开后通过）；第二次 28 项中 27 项通过，唯一"失败"为脚本期望值：「已解决」筛选命中 2 条（第一次运行留下的 S2 异常也已解决），第一次运行该项为 1 条通过——不是产品问题。

| AC | 结果 | 证据（截图在 [screenshots/](./screenshots/)） |
|---|---|---|
| AC-V1 | **通过** | `01-menu-recon-group`：财务菜单下「对账」分组含 7 个入口；`18-noperm-menu`：只有财务读权限的人看得到财务菜单、看不到「对账」；CHECKER 看不到「上传渠道账单」按钮与提交处置表单（I-10：MAKER / CHECKER 角色均带 `FINANCE_ACCOUNTING_READ`） |
| AC-V2 | **通过** | `02-upload-local-invalid`（本地校验失败不上传，逐行原因）、`03-upload-server-rejected`（服务端拒绝：批次号 + 原因表）、`04-upload-success`（「渠道账单 S2-…-A 已导入 2 行，进入匹配」）、`10-bank-upload-success`（编号、行数、连续） |
| AC-V3 | **通过** | `06-statement-detail-gate`：行的匹配状态、平台流水 ID（自动）、实际费 0.30；门禁五项中「调整行已归类」「无阻塞异常」标红 |
| AC-V4 | **通过** | `07-exceptions-filtered`（「共 n 条，按账龄从长到短」，按类型 + 清算账户筛选命中 1 条）、`08-resolution-form-codes`（账单行只列 4 个归类码）、`09-checker-review`（待复核：归类 · CHANNEL_FEE_OTHER · 金额 −1.00，提交人 ID；复核人批准后账单对平） |
| AC-V5 | **通过** | `11-manual-match-exceeds`：勾选两笔 48.70 + 60.00，带符号合计 108.70、剩余 −8.70、「超出流水金额」，按钮禁用；`12-manual-match-ok`：改为 51.30 后合计 100.00、剩余 0.00 可提交，提交后「已确认 2 条匹配」，流水 `MATCHED` |
| AC-V6 | **通过** | `05-business-error-detail`（重复上传同批次，页面显示后端 detail「…须先作废」）、`17-empty-list`（平台流水无结果说明文字）、银行流水抽屉「系统没有给出建议…」 |
| 其他 | | `13-summary`、`14-clearing-accounts`、`15-cash-accounts`（只显示末四位）、`16-platform-txns`；控制台无非预期 JS 错误。既有：`App.tsx` 第 37 行 `Spin tip` 弃用警告（HEAD 已有，与本需求无关）15 条 |

## 5. 性能：2 万行渠道账单（[perf_20k.py](./perf_20k.py)）

做法：先以正式发布器 32 线程并行发布 20,000 条 PAYMENT 事实（10.00、预估费 0.05，外部交易号互不相同），等全部登记为平台流水；再以 MAKER 经运营入口导入 20,000 行账单（每行与一笔平台流水完全匹配、费用相同，期望全部匹配并对平，最大化匹配任务的写入量）。私有栈 reconciliation 的 JVM 参数与 mor-test 相同档位（`-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`，本机 `-Xmx512m`、mor-test `-Xmx384m`）。另以 300、1,000 行各跑一次作对照。

| 指标 | 300 行 | 1,000 行 | **20,000 行** |
|---|---|---|---|
| 事实发布 → 全部登记为平台流水 | — | — | 发布 15.6 s；全部登记 168.6 s（约 120 条/s，4 分区） |
| 开始导入 | | | 0.2 s 内 |
| 分段追加 | 3 段 0.4 s | 10 段 0.6 s | **200 段 10.3 s**（每段 p50 43 ms、最大 211 ms；最大正文 25.6 KB < 64 KiB；未触发 300 次/分限流） |
| 完成导入（校验 + 登记匹配任务） | 0.09 s | 0.04 s | **0.42 s** |
| `CHANNEL_STATEMENT_MATCH` 匹配任务 | 2.2 s | 4.3 s | **≈ 100.8 s，超过 100 s 单次超时** |

20,000 行结果：任务在 100.0 s 被补偿框架判为 `HANDLER_TIMEOUT`（`execute_status=ERROR`，`execute_count=1`，2 s 后可重试），并中断处理线程；但处理线程已进入保存阶段，约 0.8 s 后仍然提交——账单 `RECONCILED`、20,000 行全部 `MATCHED`、预期银行流水 199,000.00、`ChannelBatchReconciled`（`platformTxnCount=20000`，信封 977 字节）都已产生。随后 `AbstractLockProcessor` 记 `WARN Processor 释放资源锁失败，异常类型：RedisException`（线程被中断），账单锁要等 5 分钟 Lease 自然过期。

**结论：不满足。** 在 mor-test 同档 JVM 下，2 万行匹配耗时约等于 100 s 超时上限（补偿 Lease 默认 2 分钟，本次未超 Lease），已越线，见缺陷 D-1。按 300 / 1,000 / 20,000 行三点估算约 5 ms/行（逐行查重 + 按交易号查询平台流水 + 逐行写回），数据量或机器稍有波动就会超时。

## 6. 发现的缺陷与风险

| 编号 | 级别 | 现象 | 位置 | 复现 | 期望 vs 实际 |
|---|---|---|---|---|---|
| D-1 | 高（性能 / I-14 未达成） | 2 万行账单匹配任务约 100.8 s，超过 `CHANNEL_STATEMENT_MATCH` 单次超时 100 s | `paytech/reconciliation/paytech-reconciliation-core/src/main/java/com/paytech/reconciliation/service/processor/MatchChannelStatementProcessor.java` 第 83–99 行（`processWithinLock` 逐行 `planTransactionLine`：每行在第 121 行 `platformTxnRepository.listByReference` 查平台流水，并经第 192–198 行 `duplicated` 再做 `channelStatementLineRepository.listByReference` + `channelStatementRepository.findByIds` 两次查询，2 万行约 6 万次查询；之后在一个事务内逐行保存）；超时策略在 `service/handler/ChannelStatementMatchHandler.java` 第 95 行 | `perf_20k.py <run> 20000`：发布 2 万条事实 → 导入 2 万行 → 完成导入后观察 `t_compensation_event` | 期望 2 万行在 100 s 内完成（I-14、`ChannelStatementMatchHandler` 注释）；实际 ≈ 100.8 s，任务记 `HANDLER_TIMEOUT` |
| D-2 | 中（与 D-1 同源，超时后的语义） | 超时后处理线程仍提交了结果，而补偿记录是 `ERROR`（待重试）；中断导致 Redis 锁释放失败，账单锁残留到 5 分钟 Lease 过期 | `paytech/foundation/paytech-compensation-event/.../core/execution/CompensationEventDispatcher.java` 第 197–199 行（超时只 `interrupt()`，不等待处理结束）；锁释放 WARN 来自 Foundation `AbstractLockProcessor` | 同 D-1 | 期望超时与提交二者一致（要么回滚、要么记成功）；实际已提交但任务待重试。重试时 `processWithinLock` 第 86 行见状态非 `MATCHING` 直接返回，结果不会错，但锁残留期间对该账单的作废 / 处置等加锁操作会等锁超时（10 s）失败，任务记录也会多一次无效重试。本机未连 XXL，未观察到重试本身 |

建议（供决策，未改代码）：匹配改为按交易号批量查询平台流水与跨账单重复、批量写回，或按 I-14 同时调大该任务超时与 `compensation.lease-duration`（如 300 s / 6 min）后再测；D-2 属补偿组件通用行为，按 Foundation Owner 另评。

**其他观察（非缺陷）：** ① 本机 `mor_account` 缺表导致 account-test 无法运行（第 2 节）；② `BillingFactPublisher` 每次运行固定约 60 s 收尾（连接关闭），是验收夹具自身开销，不影响服务侧计时；③ 账单详情「净额」与方向之间无空格（`48.70贷（收）`），纯展示细节。

## 7. 清理

执行 `stack.sh stop`（停止本次启动的 reconciliation / ops-bff / Gateway / Nginx，端口 18115、18211、18090、18454 已释放；他人进程未动）后运行 [cleanup.py](./cleanup.py)：

| 对象 | 处理 |
|---|---|
| `mor_reconciliation_v2e2e`（验收前 15 张表均 0 行） | 逐表删除：账单行 21,314、平台流水 21,306、消费去重 21,307 + 21,307、账单 11、银行对账单 5、银行流水 3、预期流水 8、匹配 5、异常 6、处置 4、清算账户 3 + MID 1、银行账户 3、补偿 1（超时那条）→ 全部 0 |
| `mor_channel_connector_v2e2e` | 按本次渠道 ID 删除：变更记录 10、能力明细 2、能力组 1、MID 2、技术账号 2、渠道 1（照 channel-connector 阶段 5 清理 SQL 顺序） |
| `mor_platform_v2e2e` | 删除 3 名合成人员（MAKER / CHECKER / NOPERM）与 3 个合成角色：刷新令牌 26、会话 26、口令 3、成员角色 3、成员 3、账号 3、角色授权 7、角色 3；审计事件按先例留痕不删 |
| `mor_vault` | 按先例只计数不删：`CASH_ACCOUNT_NUMBER_V1` 3 条（全部为本次）、`CHANNEL_ACCOUNT_CONFIG_V1` 4 条（含本次 1 条） |
| RabbitMQ | 临时 vhost `/mor-recon5` 整体删除（含 DLQ 中 1 条缺引用事实、观察队列） |
| Redis db 3 | `local-recon5:*` 无残留键；对账锁按 Lease 自然过期 |
| Nacos | reconciliation-service 临时实例随进程停止注销 |

本次未修改 run-service.sh；本机脚本运行记录（含合成人员口令）只在会话 scratchpad。

## 8. D-1 修复与复测（2026-10-21，本机）

**修复：** `MatchChannelStatementProcessor` 改为集合式执行，业务规则不变（详见 [tasks/F-report.md](../tasks/F-report.md)）。待规划交易行的候选平台流水、跨账单相同行及其账单、重复结算 / 成本偏差的最近异常都按交易号 / 主键每 1,000 个一次批量读取；规划只在内存（行、异常、同交易号 FEE 行、计划结果建索引，去掉逐行全表扫描）；保存时账单行、平台流水（匹配进度与对平结清）、异常开立 / 自动结案都按段多行写入，仍按版本 CAS，任一行不符整个任务事务回滚、由补偿重试。`ChannelStatementMatchHandler` 100 s 超时与 Lease 未改。

**SQL 次数（2 万行全部匹配并对平）：** 修复前约 10 万条（每行查平台流水 + 跨账单查重 2 次、逐行写行、每笔平台流水写两次）；修复后约 130 条（行 / 异常各 1 次，候选与查重各 20 段，事务内重读 20 段，写行 20 段、写进度 20 段、结清 20 段，及账单状态与事件登记），与行数按 1/1000 增长。批量 SQL 在 2 万行数据上 EXPLAIN 均为索引 range（`uk_platform_txn_external`、`idx_channel_line_ref`、PRIMARY）。

**模块测试：** `reconciliation/paytech-reconciliation-test verify` **93/93 通过**（原 90 + `PersistenceMappingTest.test_matchBatchReadsAndWrites` + 新 `ChannelStatementBulkMatchTest` 2 例：约 330 行混合规则经真实分段导入与补偿任务入口、重跑不重复开异常；600 行一次对平与成本确认事件）。

**性能复测：** 私有 reconciliation（`stack.sh start-recon`，JVM 同第 5 节：`-Xmx512m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC`，库 `mor_reconciliation_v2e2e`，临时 vhost `/mor-recon5`），新脚本 [perf_direct.py](./perf_direct.py) 直连 18115（不经 Gateway / BFF，避免再建 IAM 合成人员；事实发布、登记、导入、计时口径同 perf_20k.py）。

| 指标 | 1,000 行 | **20,000 行** |
|---|---|---|
| 修复前匹配任务（第 5 节） | 4.3 s | ≈ 100.8 s，HANDLER_TIMEOUT |
| **修复后匹配任务**（完成导入 → RECONCILED 且任务结束） | **0.67 s** | **4.86 s** |
| 结果 | 1,000 行 MATCHED、平台流水 SETTLED | 20,000 行 MATCHED、平台流水 SETTLED、账单 RECONCILED |
| 任务尝试 | 1 次成功（HANDLED，记录删除） | 1 次成功（HANDLED，记录删除，无超时 / 错误日志） |

另：两条 `CHANNEL_BATCH_RECONCILED` 发布任务为 `ERROR / RECON_EVENT_UNPUBLISHED`——临时 vhost 未绑观察队列，`reconciliation.events` 无订阅方（I-08 预期行为），与匹配无关。D-1 关闭；D-2（补偿超时只中断不等待）属 Foundation 通用行为，仍按原建议另评。

**清理：** `stack.sh stop`（只停本次启动的 reconciliation，18115 已释放）；`perf_direct.py cleanup` 删除 `mor_reconciliation_v2e2e` 合成行（账单行 21,000、平台流水 21,000、消费去重 21,000 + 21,000、账单 2、预期流水 2、补偿 2、清算账户 1、银行账户 1）→ 15 张表全部 0；临时 vhost `/mor-recon5` 已删除；`mor_vault` 因新建银行账户经真实 API 多 1 条 `CASH_ACCOUNT_NUMBER_V1`，按先例不删；Redis db 3 无 recon 残留键。
