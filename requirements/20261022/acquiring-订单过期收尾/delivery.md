# acquiring-订单过期收尾 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-22 | 来源 | 主链路缺口分析（商户下单 → 收银台卡支付 → 3DS → 支付成功）第 4 项"订单 / 会话过期 + `order.expired`"；用户询问"订单 / 会话过期需要做什么"后答复"可以"登记 | 已确认 |
| 2026-10-22 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R7、规则 B1—B7、验收 AC-01—AC-12、待确认 Q1—Q7；现状证据见 [technique.md](./technique.md) 第 1 节（主工作区 `73a2460d`） | 已确认 |
| 2026-10-22 | 1 确认 | 用户“可以”：需求稿与 Q1—Q7 全部按建议确认（Q1 XXL 每分钟扫描；Q2 终止区分原因；Q3 终态时刻用 `expires_at` 不加列；Q4 `EXPIRY_REACHED`；Q5 只记告警；Q6 Payment 不兜底扫描；Q7 阶段 4 待 3DS 合入后开始） | 已确认 |
| 2026-10-22 | 2 现状差异 | [technique.md](./technique.md) 第 2 节评审稿：需求项差异、逐系统影响、依赖与风险、待确认 D1—D5 | 已确认 |
| 2026-10-22 | 2 确认 | 用户“继续”：阶段 2 分析与 D1—D5 全部按建议（D1 Payment 加按来源查询；D2 请求带可空 intentId、Status 加 ALREADY_PAID；D3 过期同事务写投影 EXPIRED；D4 每轮调用不退避、超 24 小时 WARN；D5 脚本回拨 `expires_at`） | 已确认 |
| 2026-10-22 | 3 方案设计 | [technique.md](./technique.md) 第 3 节评审稿：规模总结、主流程与时序、2 个接口变更 + 内部端口 + `order.expired` + XXL Handler、数据（无 DDL / DML，5 条 Mapper 语句）、配置、研发与测试计划；待确认 S1—S3 | 已确认 |
| 2026-10-22 | 3 确认 | 用户“继续”：设计包与 S1—S3 按建议确认（S1 收尾 Processor 用 `AbstractLockProcessor` + TRY_LOCK 锁订单；S2 `MOD(order_id, total)` 分片；S3 按来源查询用 Path 形态） | 已确认 |
| 2026-10-22 | 4 前置 | Q7 前提核对：3DS 需求处于阶段 6（发布准备，集成目录 `Code-MoR-card3ds-rel`），尚未合入主工作区（main `42879401`）。任务卡拆分稿见 [tasks/cards.md](./tasks/cards.md)；阶段 4 开工时机待用户决定（等 3DS 合入，或先在 main 上开工、合入时再对齐） | 已决定 |
| 2026-10-22 | 4 开工 | 用户“按照建议”：选项 2，在 main `42879401` 上先行；独立工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached，无分支），执行代理按 [tasks/cards.md](./tasks/cards.md) EXP-A → E 顺序实施，主会话门禁；3DS 合入时按 cards.md 第 3 节对齐 | 进行中 |
| 2026-10-22 | 4 卡 EXP-A | payment 终止原因与按来源查询完成（[报告](./tasks/EXP-A-report.md)）：payment auto 78/78、全 reactor test-compile 通过。裁决：① 跨 Client 终止保持 400 `BUSINESS_CHECK_FAILED`（不改为 404，避免 acquiring 把归属错配当成“无 Intent”本地取消）；② 非法 reason 按 Foundation 映射为 422，不单独处理；③ 响应组装放 `GetIntentResponse.of`；④ acquiring `MockPaymentIntentApi` 的占位实现由 EXP-B 替换 | 完成 |
| 2026-10-22 | 4 卡 EXP-B | acquiring 真实终止客户端完成（[报告](./tasks/EXP-B-report.md)）：`PaymentTerminationClientImpl`（有投影直接终止，无投影先按来源查）、删除固定 Mock、Cancel 对 ALREADY_PAID 返回 409；测试设施改为 HTTP 侧 Mocker 并加 unexpected-call 检查；acquiring auto 809/809、全 reactor test-compile 通过。说明：事务外断言改用 `PaymentTransactionProbe`；工作目录跑 acquiring 测试需导出 4 个 Rabbit / Redis 环境变量 | 完成 |
| 2026-10-22 | 4 卡 EXP-C | 过期收尾核心完成（[报告](./tasks/EXP-C-report.md)）：Mapper 4 条、仓储、`OrderExpiryContext` / `OrderExpiryService`、`ExpireOrderProcessor`（订单 TRY_LOCK）、`ScanExpiredOrdersProcessor`、`order.expired`、`ExpireOrderTest` 17 例 + `ClientWebhookOrderExpiredTest` 4 例；acquiring auto 830/830、全 reactor test-compile 通过。裁决：① 锁竞争与版本冲突由扫描计为 SKIPPED（不重写模板加锁）；② 到期后提交支付返回 410 `CHECKOUT_EXPIRED`（AC-03 / AC-08 的“被拒”按此理解）；③ 有投影但终止 404 同样收尾并把投影写 EXPIRED；④ 新增 `core/constant/Locks`，提交支付改用同一锁键。遗留：扫描查询在生产数据量下可能 filesort，执行计划待阶段 7 / 后续核对 | 完成 |
| 2026-10-22 | 4 卡 EXP-E | 验收脚本与文档完成（[报告](./tasks/EXP-E-report.md)）：V2 验收脚本 `local-e2e/client_api_flow.py` 新增 `flow_expired`（创建 5 分钟期限 → 回拨 `expires_at` → 等扫描 → GET EXPIRED、`order.expired` 校验、Cancel 409、不产生 `order.cancelled`），`py_compile` 通过，未对 mor-test 运行；`COMMERCE_DATABASE.md` 原因码目录、COM-05 文案、acquiring README `order.expired`；requirements-site 137 例 10 例失败（与 3DS 基线相同，既有）。裁决：V1 脚本不改（mor-test 已切 V2）；设计稿 13.5“已实现三种”在阶段 8 随台账一并改为四种 | 完成 |
| 2026-10-22 | 4 卡 EXP-D | XXL 任务与配置完成（[报告](./tasks/EXP-D-report.md)）：`OrderExpiryProperties`（200 / 50 / 24h）、`OrderExpiryJobHandler`（`orderExpiryScan`，分片取自 XXL 上下文，无上下文按 0/1）、告警阈值经请求传入；测试入口改为 Handler，新增 7 例；acquiring auto 837/837、reactor test-compile 通过。裁决：有订单收尾失败时任务标记失败（与 ledger 样板一致）；mor-test acquiring 执行器已由 compose 环境变量启用，阶段 6 只需登记任务 | 完成 |
| 2026-10-22 | 4 合并门禁 | 工作目录 `Code-MoR-expiry`（基线 `42879401`，未提交）：全 reactor test-compile 通过（118 模块）；payment auto 78/78、acquiring auto 837/837；arch-rules、arch-rules-reactor-test、messaging 等 11 个测试模块全部通过（messaging 73、arch-rules 68）。首轮 messaging 31 例报错是门禁命令漏传 `-Dpaytech.local-environment.dir`，补传后通过，非代码问题。requirements-site 137 例 10 例失败（既有，与 3DS 基线一致）。无 DDL / DML；未部署 | 阶段 4 完成 |
| 2026-10-22 | 4 确认 | 用户“继续”：接受阶段 4 结果与偏离；授权阶段 5 两项动作：本机 e2e 替换 acquiring、payment JAR（保留旧 JAR），e2e 库内回拨订单 `expires_at` 模拟到期 | 已确认 |
| 2026-10-22 | 5 授权扩大 | 本机 e2e 的 acquiring 从未运行、`mor_acquiring_v2e2e` 为空库，payment 为 3DS 集成版。用户选 A：用已评审的 `apply-e2e.sh` 在空的 `mor_acquiring_v2e2e`、`mor_counterparty_v2e2e` 建 Schema 与初始化数据（先确认为空），启动 acquiring；payment 换本需求 JAR（旧 JAR 保留，验完换回并通知 code-mor-6b） | 已确认 |
| 2026-10-22 | 5 环境准备 | 本机 e2e：在原为空的 `mor_acquiring_v2e2e`（16 表）、`mor_counterparty_v2e2e`（2 表）执行仓库 Schema 与初始化数据（执行前核对 0 表）；首次启动 acquiring（本机 XXL 执行器 127.0.0.1:19992，Admin 为不可达占位，按 Admin 协议 `POST /run` 触发）与 counterparty；旧 JAR 备份为 `*.pre-expiry`。发现：本机启动器 Platform Webhook 调用方只放行 3 种事件，已在 `local-e2e/run-service.sh` 加入 `order.expired`（mor-test compose 已含全部 8 种，不受影响）；Platform 按 3DS 阶段 5 做法以 DUAL + JWKS 启动 | 完成 |
| 2026-10-22 | 5 基线对齐 | 3DS 需求合入 main（`de4f7ba4`、`22ede435`）。工作目录改为基于 `22ede435`：本需求改动以三方补丁重放，无冲突；重叠文件中 3DS `refreshSubmission` 与本需求 `getIntentBySource` 并存。新基线门禁：test-compile、arch-rules / messaging 通过；payment 111 例 1 例报错（`TerminateIntentTest.test_authorizedReversalRequired` 409 锁冲突，推测为 3DS `PAY_RESULT_QUERY` 补偿抢 Intent 锁，测试侧返工中）；acquiring 839 例 1 例失败（`PaymentSucceededListenerTest.test_replayedEventSkipped` 指标计数 3→4，本需求未改该链路，待重跑判断是否偶发） | 进行中 |
| 2026-10-22 | 5 返工 | ① payment `TerminateIntentTest`：根因确认为 PAY 结果未知时同事务登记 `PAY_RESULT_QUERY`、提交后立即执行并持有 Intent 锁，终止 TRY_LOCK 竞争得 409（`test_activeAttemptPending` 同样存在竞态、此前侥幸通过）；改为继承 3DS 的 `AbstractPayResultTest`，等首轮查单结束再终止，只改测试；payment auto 111/111，`TerminateIntentTest` 另隔离跑 2 次 11/11。② acquiring `PaymentSucceededListenerTest` 单独跑 2 次 8/8，判为偶发。③ acquiring 全量又出现本需求 `ExpireOrderTest.test_repeatedScanWritesOnce` 偶发失败（第二次扫描选中他例残留的到期订单；单类连跑 3 次 24/24），交 EXP-C 查根因并修隔离 | 完成 |
| 2026-10-22 | 5 门禁复验 | ③ 根因：`Code-MoR-billing-b` 会话同时在共享 `mor_acquiring` 与同一 vhost 上跑 acquiring 全量，过期扫描是全库扫描，时钟前拨后选中对方夹具订单；`PaymentSucceededListenerTest` 同源（两 JVM 消费同一队列）。不是本需求缺陷。裁决不为此改测试（生产扫描本为全局扫描，守卫会掩盖真实行为）；EXP-C 曾加的只读守卫与时钟改动已撤回，测试文件与合并基线一致。等对方进程退出后，前台连续两次全量 839/839 通过（23:48—23:57，期间无其它 acquiring 测试进程）。约束：跑 acquiring 全量前须确认本地 `mor_acquiring` 无他人使用 | 完成 |
| 2026-10-22 | 5 e2e 部署 | 合并版（22ede435 + 本需求）JAR 换入本机 e2e：acquiring `de02f36bd0b0c128`、payment `621ff256dfc74c5c`（前 16 位；payment 含 3DS `PayResultQueryHandler` 与本需求 `TerminationReason`）；验收由执行代理 EXP-S5 运行（S1 无 Intent 本地过期、S2 3DS 挑战中到期保持 OPEN 后挑战失败再过期、S3 已支付不受影响、S4 日志泄漏检索） | 完成 |
| 2026-10-22 | 5 完成 | [阶段 5 报告](./verification/stage5-report.md)（脚本 `stage5_expiry.py`、结果 `stage5-expiry.json`）：S1 无 Intent 本地过期、S2 3DS 挑战中到期保持 OPEN → 挑战失败后过期（Intent `EXPIRED`）、S3 已支付不受影响、S4 日志泄漏 0 命中，全部通过，未发现产品缺陷。AC-01/02/03/08/11 通过；AC-04/06/09 部分（ALREADY_PAID 竞态、签名送达、多实例未在本机做）；AC-05/07/10 以自动测试为证；AC-12 留 mor-test。环境操作：建 acquiring / counterparty Schema；回拨 5 个测试订单 `expires_at`；执行代理经运营入口补建 risk CHECKOUT_PREPARE 放行发布 `239113350301106176`；主会话临时换 3DS 扩展版 PayerMax（S2 后已恢复原版）。行为发现 F1：订单到期后消费者轮询先返回 `CHECKOUT_EXPIRED`、不再触发 Payment 刷新，挂起的 3DS 结果只靠 Payment 退避查单或渠道通知收敛 | 阶段 5 完成 |
| 2026-10-23 | 5 确认 | 用户“继续”：接受阶段 5 结果与证据边界；F1 按建议本需求不改，登记为收银台前端需求待办；进入阶段 6（只读核对 mor-test、编写发布清单，不改环境） | 已确认 |
| 2026-10-23 | 6 发布准备 | [发布包](./release/release.md)（执行代理 EXP-R6，只读）：回滚目标 payment `4cf16f15…`、acquiring `3958983e…`（3DS 发布后）；候选（本地 tag `expiry-20261023`）payment `f3c48d89…`（JAR `621ff256`）、acquiring `55a644fd…`（JAR `de02f36b`），JAR 逐条目差异全部归属本需求；环境逐键一致，只需继承 payment `PAYMENT_CHANNEL_CALLBACK_BASE_URL`。影响面：存量到期订单 16 笔（OPEN，10-17—10-20 到期，均为历次验收订单，无投影 / 接受证据 / Intent），首轮扫描本地收尾，Platform 新增 16 行 `order.expired`，相关端点均未订阅、投递 0。XXL：执行器心跳正常，`orderExpiryScan` 未登记，payment `compensationEventScan` 启用（F1 兜底成立）。Platform 已放行 `order.expired`。无 DDL。顺序 payment 先、acquiring 后；回滚 acquiring 先（旧 Payment 对新路由返回 404，新 acquiring 会误判为 Intent 不存在）。待用户决定发布包第 0 节 D1—D5 | 完成 |
| 2026-10-23 | 6 确认 | 用户“继续”：发布包 D1—D5 按建议（D1 接受首轮扫描收尾 16 笔存量订单；D2 候选镜像以现网镜像为底覆盖 JAR，仅 mor-test；D3 验收写入合成数据；D4 增加 Payment 探针 P-1；D5 超时 300 秒、重试 0、DO_NOTHING）；授权阶段 7：按 release.md 第 6 节 S0—S8 替换 mor-test payment、acquiring，登记并启用 XXL `orderExpiryScan`，执行验收 | 已确认 |
| 2026-10-23 | 7 部署 | 主会话执行（UTC 10-22）：S0 通知 code-mor-6b、code-mor-78（6b 确认无占用，提醒的两项 payment env 已核对保留）；S1 基线复核与阶段 6 一致；S2 备份 `~/Code-MoR-backups/<UTC>-expiry-mor-test/`（快照、`mor_acquiring` 整库、XXL 任务与分组表，0700，SHA256SUMS）；S3 20:39 payment → `f3c48d89…`（JAR `621ff256`，healthy、0 重启、0 ERROR、4 个分区消费者）；S4 20:40 acquiring → `55a644fd…`（JAR `de02f36b`，healthy、0 ERROR，环境逐键一致，分区消费者启动约 15 秒后上线）；S5 XXL 任务 23 `orderExpiryScan` 经 Admin API 登记并启用（首次启用请求时 XXL Admin 自行重启，第 13 次，恢复后重试成功）；S6 20:43 首轮 `scanned 16, expired 16`，16 笔存量订单与会话 EXPIRED、`EXPIRY_REACHED`，16 个 `order.expired` 任务完成，Platform 受理 16 行、投递 0，Payment 无调用；20:44 第二轮 `scanned 0` | 完成 |
| 2026-10-23 | 7 验收 | [release.md 第 11 节](./release/release.md)（执行代理 EXP-S7）：P-1 Payment 探针 11/11；3DS AC-02、BIN 冻结、消费者与执行器、健康、日志泄漏检索全部 PASS。Client API 回归首轮中止于入驻 F01（共用脚本 `acceptance_flow.py` 落后于文件多存储第二段 `73a2460d` 的上传合同，残留 1 个入驻账号与 1 个 DRAFT 申请）；在 scratchpad 副本只改上传字段与材料绑定键后重跑一次（20:58:58—21:00:14Z）91 PASS / 0 FAIL：AC-12 全部子项通过（GET 16 秒后 EXPIRED、`order.expired` 签名送达 1 次、冻结 Envelope 一致、`expiredAt=expiresAt`、过期后取消 409 不产生 `order.cancelled`），AC-01/06/07/08 通过。退款尾段因测试脚本读到接收端临时文件崩溃，只读补查确认接收端 4 个事件、Platform 各 1 行、`refund.created` 投递 SUCCEEDED；`refund.created` 签名与 GET 比对、clientRefundId 重放未复验（本次未改退款代码，不重跑）。脚本补丁存 `release/acceptance/acceptance_flow-upload-contract.patch`，交脚本维护方合入 | 阶段 7 完成 |
| 2026-10-23 | 7 确认 | 用户“继续”：接受阶段 7 结果；退款尾段不补跑；授权阶段 8 本地提交（不推送） | 已确认 |
| 2026-10-23 | 8 提交与归档 | 以 main `32dbdd18` 为父、私有索引构建单个本地提交（未推送）：本需求代码（payment、acquiring 及测试）、验收脚本与启动器、文档（COMMERCE_DATABASE 原因码目录、requirements.ts COM-05、acquiring README、设计规格 13.5 节改为四种、CURRENT_IMPLEMENTATION 台账）与本需求目录；提交号见 `git log`，并通知 code-mor-78 rebase | 完成 |

## 当前阶段与下一确认点

阶段 8 完成：已本地提交，未推送；待用户确认本次结束。遗留见下节。

## 实现 / 验证 / 部署状态

未开始；台账见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 遗留

- F1（2026-10-22 阶段 5 发现，用户 2026-10-23 确认本需求不改）：订单到期后收银台查看与轮询先返回 410 `CHECKOUT_EXPIRED`、不再触发 Payment 刷新；持卡人到期后才从银行跳回只能看到“已过期”，结果确认只靠 Payment 退避查单（`compensationEventScan`）或渠道通知。归收银台前端需求处理（到期后的结果页 / 查单入口）。

## 依赖与协调

- [payment-3DS全链路](../payment-3DS全链路/delivery.md) 阶段 4 进行中（独立工作目录）：本需求阶段 4 待其合入主工作区后开始（Q7）。
- 本需求不登记 Platform ID 块（无新路由）。
