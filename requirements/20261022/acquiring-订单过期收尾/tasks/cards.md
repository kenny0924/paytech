# acquiring：订单与会话过期收尾 · 阶段 4 任务卡（拆分稿）

> 依据 [technique.md](../technique.md) 第 3 节（已确认）。执行代理在独立工作目录按卡实施；主会话负责门禁、合并与阶段报告。每张卡实施前按 AGENTS 要求读取 `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`（含示例）与 `agent-docs/COMPENSATION_EVENT_GUIDE.md`（卡 D），并在卡报告中列出已读路径与第 11 节自查结果。

## 1. 卡与依赖

| 卡 | 系统 | 内容 | 依赖 | 对应 AC |
|---|---|---|---|---|
| EXP-A | payment | 先写 `TerminateIntentTest` 基线并通过；`TerminationReason`（ORDER_CANCELLED / OPERATING_ENTITY_CLOSED / ORDER_EXPIRED，各自目标状态）；`TerminateIntentRequestBody.reason` 校验扩值；Converter → Request → Context 传递；`PaymentIntentRepository.terminateIntent(intent, status)` 与 Mapper；`getIntentBySource` 接口（`PaymentIntentApi`、Controller、Converter、`GetIntentBySourceProcessor`）；`GetIntentBySourceTest` | 无 | AC-02、AC-11 |
| EXP-B | acquiring | `PaymentTerminationClient` 请求 / Status / `TerminationReason` 改造；`PaymentTerminationClientImpl`；删除 `FixedPaymentTerminationClient`；`CancelOrderProcessor` 传 intentId 与原因、ALREADY_PAID → 409；测试设施：`MockPaymentIntentApi.getIntentBySource / terminateIntent` DTO、`PaymentTerminationMocker` 改 HTTP 记录；改 `CancelOrderTest`、`ClientWebhookOrderCancelledTest`、`CheckoutIntentCreationTest` 并通过 | EXP-A（api 模块） | AC-07 |
| EXP-C | acquiring | Mapper 语句 1—4 与仓储方法；`OrderExpiryContext`、`OrderExpiryService` + Impl、`ExpireOrderProcessor`（`AbstractLockProcessor` + TRY_LOCK 订单锁）、`ScanExpiredOrdersProcessor`；`ClientWebhookEventType.ORDER_EXPIRED`、`ClientWebhookEvent.orderExpired`；`OrderExpiryDataMocker`、`OrderExpiryChecker`；`ExpireOrderTest`（经 Processor 入口先行）与 `ClientWebhookOrderExpiredTest` | EXP-B | AC-01—AC-06、AC-08、AC-09 |
| EXP-D | acquiring | `OrderExpiryProperties`、`application.yml`、`OrderExpiryJobHandler`（`@XxlJob("orderExpiryScan")`，分片参数）；`ExpireOrderTest` 入口改为 `runOnce`，补分片不命中、未到期、24 小时 WARN、Payment 503 场景 | EXP-C | AC-01、AC-09、AC-10 |
| EXP-E | 脚本与文档 | `client_api_flow.py` 过期场景（D5）；`COMMERCE_DATABASE.md` 原因目录 `EXPIRY_REACHED`；`requirements.ts` COM-05 补过期行为并跑 `npm --prefix requirements-site test`；acquiring API 文档 `order.expired`；CURRENT_IMPLEMENTATION 台账条目草稿 | EXP-D | AC-12 |

顺序：A → B → C → D → E；A 与 C 的非接口部分（Mapper、仓储、事件）可并行起草，但 C 的测试依赖 B 的 Mock 设施。

## 1.1 裁决记录

- EXP-A：跨 Client 终止保持 400（非 404）；非法 reason 422；`GetIntentResponse.of` 共用组装。acquiring 终止客户端只把 **404** 当作“Intent 不存在”，400 / 其它 `FailException` 原样抛出。

- EXP-B：通过。运行提示（EXP-C / D 适用）：在工作目录跑 acquiring 测试须从主工作区 `local-environment/.env.local` 只读导出 `MOR_RABBITMQ_USER`、`MOR_RABBITMQ_PASSWORD`、`MOR_RABBITMQ_VHOST`、`MOR_REDIS_PASSWORD`，并加 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`；Problem 测试体的 `title` 用标准原因短语。
- EXP-C：通过。锁竞争 / 版本冲突由扫描计 SKIPPED；提交支付到期返回 410；`PENDING_ALERT_AFTER` 常量留给 EXP-D 配置化。

## 2. 门禁

- 每卡：所属模块 `auto` 测试通过（本地 `local-environment`），`mvn -q test-compile` 全 reactor 通过；不新增 Mockito Stub；日志不含 Token / URL / 卡数据。
- 合并门禁：payment、acquiring、arch-rules、messaging 全量 `auto`；requirements-site 测试（既有失败另记）。

## 3. 与 3DS 需求的重叠面（合入对齐用）

| 文件 | 3DS 改动 | 本需求改动 | 对齐方式 |
|---|---|---|---|
| `paytech-payment-api/.../PaymentIntentApi.java` | 新增刷新支付结果接口 | 新增 `getIntentBySource` | 两处追加，保留两边 |
| `payment/.../ext/rest/execution/PaymentIntentController.java`、Converter | 新增刷新方法 | 新增按来源查询 | 追加 |
| `acquiring-test/.../mock/external/MockPaymentIntentApi.java` | 实现刷新方法 | 实现 `getIntentBySource`、`terminateIntent` DTO | 追加 |
| `payment/.../TerminateIntentProcessor.java` | 不改（共享 Intent 锁） | 传递 reason | 无冲突 |
| `acquiring/.../CheckoutCardPaymentPortImpl.java` | 轮询调用刷新 | 不改 | 无冲突 |
