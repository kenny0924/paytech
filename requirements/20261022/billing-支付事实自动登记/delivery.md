# billing 支付事实自动登记 —— 交付记录

需求正文：[requirement.md](./requirement.md) · 技术方案：[technique.md](./technique.md)（阶段 2、3 填写）。跨仓库当前状态见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)，本文不另建状态台账。

## 1. 阶段确认

| 阶段 | 状态 | 日期 | 说明 |
|---|---|---|---|
| 1. 需求评审与范围确认 | 已确认 | 2026-10-22 | 用户"继续"后对三项业务选择明确作答：**Q1** 手续费行写 渠道 → OE（Client 只作为本金收方）；**Q2** 平台服务费由 OE 承担（OE → 平台 Finance Entity，Partner 分润不扣，按 AGENTS.md 双账簿成对过账）；**Q8** 对账四类事件另立需求 C。其余 Q3—Q7、Q9—Q12 按建议。范围与 AC-01—AC-11 按需求稿 |
| 2. 现状差异与影响分析 | 已确认（用户"下一步"，Q13—Q16 按建议，需求 D 另立） | 2026-10-22 | [technique.md](./technique.md) 第 1—3 节。核对结论：Q5 命名空间一致但 acquiring 商业事实是固定 Mock（硬前置 P1，另立需求 D）；Q6 Partner 条款同一接口可命中，服务费"分润 × N%"基数不存在（Q13）；Q10 只在 PAY 成功发一次、`capturedAmount == amount`；ledger 服务费规则已存在（需求稿更正）；缺口 G1 Party → FE 映射（Q14）；AC-11 验收方式调整（Q15）；`schemaVersion` 保持 2（Q16）。待用户确认 Q13—Q16 与需求稿四处修正后进入阶段 3 |
| 3. 方案、接口与数据设计 | 已确认（用户"继续"，S1—S10 按建议） | 2026-10-22 | [technique.md](./technique.md) 第 4—6 节：调用链与事务边界、Payment 两字段、acquiring / ledger 新只读接口、billing 类职责与事实头 / 定价 / 引用 / 行目录、组件计算与 T+N 规则、错误分类、account 白名单、配置、分线与场景矩阵；无 DDL / DML。待确认 S1—S10 |
| 4. 研发实现 | 已确认（用户"继续"，2026-10-23） | 2026-10-22—23 | 独立工作树 `../Code-MoR-billing-b`（detached，基于 main `54d6efc4`）；T0（acquiring / ledger 接口、事件目录）与 B（account 白名单）并行由 Opus 5.5 agent 执行；A（billing）在 T0 之后；payment 两字段等 card BIN 会话提交后再做。进度：T0 完成（acquiring 814 / ledger 344 / 目录 12 / arch-rules 68 全过；两处差异 S12）、B 完成（account 67/67）；工作树 HEAD 挪到 main `22ede435`（含 card BIN / 3DS 提交，`events.yaml` 注释冲突已合并）；A（billing）与 P（payment）并行进行。阶段 4 新增决定 **S11**（用户 2026-10-22 确认）：税务快照只记引用、不生成税行、不重试。**S13**（用户 2026-10-22 确认）：A 线发现 billing 调 acquiring 违反 D08 / arch-rules R3（领域层不得依赖流程层 `-api`），阶段 3 的 acquiring 只读接口作废，改为 acquiring 在支付成功落实事务内登记补偿事件、提交后发布 `OrderPaymentSucceeded`（订单冻结商业字段 + 支付事实），billing 消费该事件；T0 重新执行 acquiring 部分，A 按新输入合同继续。四线结果：T0 重做完成（acquiring 820 / messaging 73 / arch-rules 68 全过）、B 完成（account 67/67）、P 完成（payment 相关 55/55）、A 完成（billing 142/142）。独立评审（Opus agent，只读）无阻塞项，发现零手续费与 ledger 必需行不一致 → 用户确认 **S14**（billing 拒绝 Σ CLIENT_FEE = 0），修复后 billing 143/143；文档过期项（4.5 / 4.10 / AC-01 / AC-06）已同步。compose 变量（billing 四个 URL、acquiring `PAYTECH_INSTANCE_ID`）已在工作树加入。待用户确认后进入阶段 5 |
| 5. 综合验证与本地验收 | 已确认（用户"继续"） | 2026-10-23 | 串行重跑 payment / acquiring / ledger / account / billing 五个 `-test` 模块 auto 全量 + messaging + arch-rules + 全 reactor test-compile；acquiring 载荷 ↔ billing 读取器字段静态交叉核对；真实跨服务串跑按 Q15 留到 mor-test |
| 6. test 发布准备 | 已确认（用户"按建议执行"：确认发布卡、复核方式 A、授权阶段 7 动作） | 2026-10-23 | 发布卡 [release/release.md](./release/release.md)：五个候选镜像已构建并钉 ID（billing / account / ledger 基于 `22ede435`，payment / acquiring 基于 bf 提交 `d3d39e40`，D1 基线复现 0 差异；rebase 后 acquiring 853/0、payment 111/0）；只读预检发现 4 个缺口，用户决定：S15 加 `AE_BUSINESS_DAYS` 日历（一条 DML）、阶段 7 建 DEMO_NI 成本方案、验收用 APPLE_PAY 无 Partner、合成数据保留为演示数据 |
| 7. test 部署与环境验收 | 已执行，待确认 | 2026-10-23 07:38–08:05Z | 全部记录在 [release/release.md 第 8 节](./release/release.md)。五个容器已换为候选镜像（healthy、重启 0、无 ERROR）；SQL-1 在 mor-test 与本机各执行一次；DEMO_NI 成本方案经 IAM 合成复核人（方式 A，账号 `billb-checker-cb6e8a@example.test` / `239292995902455808`，保留）批准 ACTIVE；合成 `OrderPaymentSucceeded` 第一次发布因载荷支付时间早于成本版本生效时间被拒进 DLQ（发布清单编排缺陷，未写数据），改载荷时间与版本后第二次发布 **全链通过**：billing 事实 `239295097391407104`（4 行 2.70 / 0.30 / 97.00 / 0.50，到期 2026-10-27 23:59:59Z）→ account PENDING 贷 97.00 → ledger PAYMENT_SUCCEEDED 分录借贷 100.50 → reconciliation 平台流水 OPEN 100.00 / 0.50；控制台 10 / 11。**遗留**：F1 控制台计费事实详情抽屉对 AUTO 事实报 INVALID_RESPONSE（ops-web 校验 `sourceSystem` 要求大写，billing 写 `acquiring`；待用户定 ops-web 放宽或 billing 改值）；`billing.acquiring.dlq` 残留 1 条被拒消息（清除需授权）；X1 基线 acquiring FAILED 10（既有退款提交任务） |
| 8. 代码提交与交付归档 | 未开始 | | |

## 2. 数据库变更

- 无 DDL。**一条 DML（S15，阶段 6 新增）**：`mor_billing.t_business_calendar` 增加 `AE_BUSINESS_DAYS`，文件 `paytech/billing/docs/database/sql/changes/2026-10-23-business-calendar-ae.sql`。**已执行（2026-10-23 07:41:03Z，用户授权）**：mor-test `mor_billing` 与本机 local-environment `mor_billing` 各一次，root、utf8mb4，各 1 行；执行前后核对 V1 0 → 1（`AE_BUSINESS_DAYS / Asia/Dubai / ACTIVE`）、V2 = 0、V3 = 0，输出存备份目录 `sql-1.out`。阶段 8 在 CURRENT_IMPLEMENTATION 记两处已执行。
- **mor-test 运营数据（非 SQL，2026-10-23 07:44Z）**：channel-connector 渠道成本方案 `DEMO_NI_COST` 版本 1 ACTIVE（范围 MID `238508278684950528` / AE001 / AE / USD / APPLE_PAY / PAY，组成 PROCESSING_FEE 0.5%）；Platform IAM 合成复核角色 `QA_BILLB_CHECKER_CB6E8A` 与人员 `billb-checker-cb6e8a@example.test`（保留，用途仅为该方案复核）。
- **本机环境修复（2026-10-22，用户授权）：** 本机 local-environment `mor_account` 缺 2026-10-19 需求的结构变更，导致 account 全部 DB 测试在基类初始化失败。以 root 执行 [`paytech/account/docs/database/sql/changes/2026-10-19-owner-account-no.sql`](../../../paytech/account/docs/database/sql/changes/2026-10-19-owner-account-no.sql) 第 1、2 段（执行前 `t_owner_account` 0 行；2.3 核对返回 0；`uk_owner_account_no` 已建）。只动本机，不涉及 mor-test。

## 3. 测试与验收证据

### 阶段 5 本机综合验证（2026-10-23，工作树 `../Code-MoR-billing-b`，HEAD main `22ede435` + 本需求未提交改动；JDK 25、`./mvnw -o`、本机 local-environment）

| 模块 | 命令要点 | 结果 |
|---|---|---|
| 全 reactor | `test-compile -DskipTests -T 4` | 通过（35s） |
| acquiring | `-pl acquiring/paytech-acquiring-test -am test -Dtest='com/paytech/acquiring/**/*'` | 820 run / 1 error：`PaymentSucceededOrderEventTest.test_registeredPaymentFactsOnly`，重跑同样失败。原因：测试假定解绑观察队列后发布不可路由，但本机 Broker `acquiring.events` 已绑定 billing 测试建的持久队列 `billing.acquiring.hash`（管理 API 核实），发布成功、任务被删，ERROR 不可观察——测试对共享 Broker 状态有隐含依赖，非业务缺陷。**修复**：测试侧 `CompensationCoordinatorHold`（@TestComponent，占满 Foundation 补偿协调线程池让提交后的 Claim 只能排队）→ 断言 INIT 任务的事件键 / 类型 / 13 个 payload 键 → 释放后观察真实发布；不再解绑探针、不依赖 ERROR 状态、不动 billing 队列；生产代码未改。**重跑：820 / 0**（billing 五个队列存在时） |
| ledger | `-pl ledger/paytech-ledger-test -am test -Dtest='com/paytech/ledger/**/*'` | 344 / 0 |
| account | 四模块 `test`（不加 `-am`） | 67 / 0 |
| billing | 四模块 `test`（不加 `-am`） | 143 / 0 |
| payment | 全模块 + messaging / card-api / channel-connector-api / risk-decision-api 进 reactor，`-Dtest='com/paytech/payment/**/*'` | 96 / 0（10 个测试类全部运行） |
| messaging | `-pl foundation/paytech-messaging test -Dpaytech.local-environment.dir=…` | 73 / 0 |
| arch-rules | `-pl tools/arch-rules -am test -Dtest='com/paytech/arch/**/*'` | 68 / 0 |

- 运行前后 `git status` 均 113 项，无源码被测试改动。日志：主会话 scratchpad `step*.log`。
- 静态交叉核对：acquiring `OrderPaymentSucceededPayload` 34 个字段与 billing `OrderPaymentSucceededEventReader` 引用字段逐一对应（仅 `entitySourceType` billing 不读），信封校验一致。
- 真实跨服务串跑（acquiring → billing → account → ledger）按 Q15 留到 mor-test 阶段 7。

### 阶段 7 mor-test 验收（2026-10-23，AC-11 / Q15）

- 驱动与证据：[release/release.md 第 8 节](./release/release.md)（执行记录）与第 8.1 节（`check_chain.sql` 逐项结果）；备份目录 `~/Code-MoR-backups/20261023T073925Z-billing-b-mor-test/`（含 `chain-platform.out`、`console/*.png`）。
- 结果：一条合成 `OrderPaymentSucceeded`（PLATFORM 来源，APPLE_PAY，100.00 USD）经 billing → account → ledger → reconciliation 全链在约 1 秒内完成，金额、到期时间、引用、分录借贷与对账预期全部符合（时间项按修订后的载荷）；四个下游 DLQ 不变、无新 FAILED 补偿任务；控制台五页 10 / 11（F1 见 release.md 第 9 节）。
- 未验收（按 Q-3）：PARTNER 来源与 CARD 支付方式（GAP-PARTNER / GAP-ISSUER，需求 D 前置）；真实 Hosted Checkout 端到端（P1，需求 D）。

## 4. 提交与遗留

待阶段 8 填写。
