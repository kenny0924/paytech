# Payment 公共命令幂等包：接入约定与迁移差异

> **历史接入记录，已被取代。** 2026-09-15 用户确认 Payment pay 移除公共命令幂等组件，改用 Attempt 的来源提交唯一键及原记录比较，暂不加锁。当前流程见 [pay 阅读说明](../pay-interface-walkthrough.md)。下文的 Claim、Hash、Lease、Complete 和命令表迁移仅保留为历史依据，不再是 Payment 的运行要求；Foundation 与 Commerce 的使用范围不变。

2026-09-15，用户在差异分析后明确“改造吧，使用公共包”。**Payment 使用 `com.paytech:paytech-command-idempotency`。** 本文维护已确认的消费约定和迁移前后差异；代码、验证及数据库执行状态见[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

沿用现有公共包，通过 Payment 本地表与调用方式适配完成接入，不另做轻量幂等包。调整 Payment 一张现有技术表，不新增业务表、不改变 HTTP 字段；pay 仍保持一次本地受理事务和当前支付状态的响应语义。

## 1. 差异总览

| 事项 | 迁移前 Payment | 公共包实际能力 | 对齐方式 |
|---|---|---|---|
| 记录与持久化 | 自有 CommandIdempotency、CommandStatus、查询/插入 SQL | 公共 CommandRecord、CommandProcessStatus、Mapper/Repository | 删除本地重复实现，调用公共 API |
| 表合同 | 11 列，状态列 command_status | 固定读取 24 列，状态列 process_status | 新增 13 列、状态列改名；其他物理差异见下文 |
| 第一次查询 | findCommand 后自行比较 Hash | find 返回 Optional，不比较业务 Hash | 替换查询，Payment 保留原 Hash 比较及 409 |
| 并发受理 | 最后插命令，唯一键冲突使整次受理回滚 | claim 锁定唯一坐标并返回实际胜出的记录，可能属于另一个请求 | 事务内先 Claim、检查结果，确认本请求取得记录后才写 Attempt |
| 完成记录 | 直接插入 SUCCEEDED 和 Attempt ID | claim 写 PROCESSING；completeSuccess 通过版本、阶段、Owner、Lease CAS 写终态 | 两次公共调用与 Attempt 写入同一事务提交 |
| 事务边界 | Payment 自己创建 TransactionTemplate | 公共包不创建事务；写方法要求已有本地可写事务 | 保留 Payment 唯一受理事务 |
| 响应 | 按原 Attempt 当前状态返回 200/202 | 可以保存回执元数据与可选响应快照；业务决定如何重放 | 只冻结受理事实，继续读取当前支付状态；不冻结临时交互正文 |
| 业务校验 | Intent、来源提交、方式、capture mode、工具范围 | 不理解这些 Payment 业务含义 | 全部继续由 Payment 负责 |
| 原实现范围 | 9 月 9 日首次迁移仅包含 Commerce | 已有原子命令和多阶段恢复的真实消费者 | 本轮已确认 Payment 的复用方向，本次按下列约定接入，不再将旧首批范围视为永久限制 |

依据：[公共 API](../../../foundation/paytech-command-idempotency/src/main/java/com/paytech/foundation/idempotency/api/CommandIdempotencyService.java)、[参数与事务校验](../../../foundation/paytech-command-idempotency/src/main/java/com/paytech/foundation/idempotency/service/CommandIdempotencyValidator.java)、[公共仓储](../../../foundation/paytech-command-idempotency/src/main/java/com/paytech/foundation/idempotency/persistence/repository/CommandIdempotencyRepositoryImpl.java)、[当前 PayProcessor](../../paytech-payment-core/src/main/java/com/paytech/payment/execution/service/processor/PayProcessor.java)。

## 2. 表结构差异

对照来源为 [Payment Schema](../database/sql/payment-schema.sql)、[Commerce Schema](../../../acquiring/docs/database/sql/acquiring-schema.sql)和[公共 Mapper XML](../../../foundation/paytech-command-idempotency/src/main/resources/META-INF/mybatis/CommandIdempotencyMapper.xml)。公共包的 24 列清单与当前 Commerce 模型一致，但 Commerce 的业务注释、目录和恢复阶段不能复制给 Payment。

### 2.1 新增的 13 列

“可空”指现有 Commerce 表定义；API 调用可能有更严格要求。

| 字段 | 类型 / 可空 | 公共机制要求与 pay 约定 |
|---|---|---|
| request_hash_version | INT / 非空 | Claim 必须为正数；新 pay 的现有算法可标为 1，不能借此改写原 Hash |
| request_snapshot_schema_version | INT / 可空 | 请求快照版本；本次原子受理不需快照，为空 |
| request_snapshot_json | JSON / 可空 | 最小恢复输入；本次为空，禁止保存请求 Token 明文 |
| operation_context_time | DATETIME(6) / 非空 | Claim 必填的原操作时刻；原命令恢复时不重置，不代替新写入时的有效期检查 |
| recovery_stage | VARCHAR(64) / 可空 | Claim/完成 API 实际必填；仅 CLAIMED → COMPLETED，不保存风控、3DS、扣款步骤 |
| resource_no | VARCHAR(64) / 可空 | 可选业务编号；已有 ATTEMPT + resource_id 足够，为空 |
| response_http_status | INT / 可空 | completeSuccess 实际必填；记录命令完成回执的状态，不覆盖后续动态观察的 HTTP 状态 |
| response_snapshot_schema_version | INT / 可空 | 无响应正文快照时为空 |
| response_location | VARCHAR(255) / 可空 | 本接口没有创建资源 Location 合同，为空 |
| response_body_json | JSON / 可空 | 为空；当前 attemptId/status 从原 Attempt 投影，不冻结响应正文 |
| lock_owner | VARCHAR(64) / 可空 | Claim 实际必填；使用本次调用的唯一 Owner，不是 Client、Intent 或 Token |
| lock_until | DATETIME(6) / 可空 | Claim 实际必填，完成 CAS 要求租约尚有效；完成后公共包清空 |
| version | BIGINT / 非空 | 公共 Claim 初始化为 1；完成使用实际返回版本并递增，不复用 Intent.version |

快照字段可以为空，但公共 Mapper 每次都会选择这些列，因此不能只增加当前填值的列而省略其余列。

### 2.2 已有列和索引

| 项目 | 迁移前 Payment | 公共包承载模型 / 接入约定 |
|---|---|---|
| 状态列 | command_status VARCHAR(64) | 改名 process_status；PROCESSING/SUCCEEDED/FAILED 三个值不变 |
| idempotency_key | VARCHAR(128)，继承表排序规则 | Commerce 为 VARCHAR(255)、ascii_bin；物理结构统一，pay 仍只接收原 submission:<ID> 格式 |
| caller_scope | VARCHAR(128)，继承表排序规则 | Commerce 为同长度、ascii_bin；统一大小写敏感身份比较 |
| response_code | VARCHAR(64)，当前 pay 成功也未赋值 | completeSuccess 要求非空；使用 PAY_ACCEPTED 表示“受理完成”，不使用渠道码 |
| 主键和唯一索引 | ID 主键；scope + operation + key 唯一 | 保留，作用域和值均不改写 |
| idx_command_recovery | 无 | Commerce 有 process_status + lock_until + ID 索引；公共包当前无按该索引扫描的 API，pay 同事务完成也不消费它，不能称为接入必需；本期保留原唯一索引，不新增无扫描消费者的访问索引 |

其余原有字段类型和长度一致。新增 resource_no、response_location 的现有承载模型使用 ascii_bin；字段注释须表达 Payment 事实。这里没有要求创建 FK/CHECK，也没有新增表。

## 3. 接入后，pay 不需要变成复杂恢复流程

受理流程：

```text
基础字段校验、按原格式计算 Hash
    ↓
公共 find → Hash 命中则按原 Attempt 返回当前结果
    ↓
业务验证，构建同一个 PayContext
    ↓
一次本地事务
    公共 claim → 验证返回记录属于本次受理
    新提交：Intent CAS / Token 保护 / Attempt 插入
    旧提交补绑定：沿用原 Attempt，不再写业务表
    公共 completeSuccess
    ↓
提交成功 → 支付方式流程 TODO → 按 Context 构建响应
```

与迁移前相比，事务从“业务写入后插命令”改为“Claim → 业务写入 → Complete”。它仍是一笔事务，不增加事务外写入，不拆成 Claim、业务、完成三笔事务。任一步失败全部回滚，正常路径不会留下已提交的 PROCESSING 命令等后台接管。

公共 Lease 是技术 CAS 条件，与 Redis 的 Intent 锁不同；Claim Owner、到期时刻仍须提供。完成时使用实际当前时刻；超过租约导致完成失败，应回滚本次受理。当前无需调用续租、接管或 advanceStage，也不增加扫描任务。本期命令 Lease 为 30 秒，本地事务超时为 5 秒；每个新 Claim 使用随机 UUID 作为 Owner。操作时刻在事务前冻结，完成时刻取当前 UTC 微秒时间。

Commerce 已有[原子 Customer 命令](../../../counterparty/paytech-counterparty-core/src/main/java/com/paytech/counterparty/domainservice/service/impl)采用公共 Claim/Complete，证明公共包不要求所有消费者都复制 Create Checkout 的多阶段恢复流程。

### 必须保留的语义

1. **先比较 Hash，再查工具。** 公共 find/claim 不替 Payment 比较 Hash；同键换成不存在的 Token，仍须优先返回 409。算法保持原六字段顺序与 UTF-8 长度前缀，不能因接入公共包换成 JCS。
2. **Claim 返回值不能丢弃。** 唯一键竞争可能返回另一个请求的记录；再次比较 Hash，确认 Owner、资源和技术状态。若并发获胜记录已存在，保持原 409/原请求重试路径即可；不得用当前候选 Attempt ID 覆盖获胜记录，也不得继续创建第二笔尝试。
3. **受理完成不等于支付完成。** Command 的 SUCCEEDED 表示接受了这次提交，Attempt 仍可为 PROCESSING。Risk、认证和资金状态继续在业务表表达。
4. **保留动态响应。** 原命令匹配后用 resource_id 导航原 Attempt；其后支付可能从 PROCESSING 变为 SUCCEEDED。当前只返回 attemptId/status，不能一直重放第一次的 202/PROCESSING。公共包允许 response_body_json 为空；ResponseBody 仍由 Payment 按当前事实构造。
5. **命令重放不重复 Claim。** 命中并核对原命令后返回原提交的当前结果；工具过期/撤销不影响幂等重放。pay 不提供只读查询开关。
6. **保留旧提交补绑定。** 没有新版命令但原 Attempt 已存在，比较原方式、模式及工具；一致时只新增公共命令并完成，不新建 Attempt。
7. **失败不自动冻结。** 当前资格失败或 CAS 冲突没有永久失败回执；不能接入后无条件 completeFailure，把下一次本可成功的原请求固定为失败。未决资金恢复也不转移到这张技术表。

公共 completeSuccess 必须接收 response_code 和 response_http_status，但不强制响应正文。本期固定 `response_code=PAY_ACCEPTED`、`response_http_status=202`，表示原提交的受理/绑定已完成；即使为终态 Attempt 补绑定也使用该回执。HTTP 200/202 由 Attempt 状态决定（PROCESSING 为 202，终态为 200），不重放技术表的 HTTP 元数据。`request_hash_version=1` 仅对应原 `PAY_INSTRUMENT_V1` 算法；所有请求/响应快照、快照版本、resource_no 和 response_location 均为空。这是 Payment 的消费约定，无需给 Foundation 增加 Payment 分支。

## 4. 代码与系统影响

| 位置 | 对齐内容 | 不变的业务职责 |
|---|---|---|
| Payment Execution / Web POM | 直接使用公共 API 的模块显式依赖公共 JAR；版本继续由已有 BOM 管理 | HTTP API 模块保持轻量，不引入持久化组件 |
| application.yml | 开启 command-idempotency.enabled | 原数据源、事务管理器、Redis 资源锁继续使用本服务配置 |
| 自有 CommandIdempotency.java | 删除；使用公开的 NewCommandClaim / CommandRecord | 不直接依赖 Foundation persistence 包的 CommandIdempotencyRow |
| ExecutionCodes / PaymentConfiguration | 删除 CommandStatus 和其本地 Handler 注册，使用 CommandProcessStatus | Intent/Attempt/Action 的业务枚举仍属 Payment |
| 按表拆分的 Repository / Impl / Mapper / XML | 使用公共命令访问，不复制本地幂等列映射 | 当前保留 Intent、Token、Attempt；来源 Binding 已删除 |
| PayContext / PayProcessor | 将本地技术记录换成公共 API 类型；使用公共 find，保留内容和资源校验 | 原 RequestBody → 同一个 Context → ResponseBody，不新增同义 Input/View |
| PaymentExecutionServiceImpl | 在现有事务调用公共 Claim/Complete，并处理竞争返回值 | 继续原子保存受理事实；不新增仅做转发的幂等 Service |
| Payment 模型、Schema、人工迁移稿 | 对齐幂等技术表和字段注释 | Intent/Attempt/Action、工具表及外部接口不因这次接入改变 |
| Foundation | 当前 API 能力足以支持上述原子受理，无需修改公共实现 | 继续只提供机制，不拥有 Hash、业务阶段、响应解释或事务开启 |
| Commerce | 作为现有消费者的代码与表结构参照，本次无改造需求 | 不复制它的订单阶段、回执正文或业务错误码到 Payment |
| 其他系统 | 无本轮代码/模型迁移 | 通用复用原则不等于自动执行全系统迁移 |

不新增 HTTP 接口、页面或 Gateway/Platform 注册 DML。旧调用方 JSON、幂等键和值、资源身份及 Payment→Commerce 禁止反调的边界保持不变。

## 5. 数据迁移和验证边界

具体空库/旧空表执行路径见[人工迁移说明](../../../../requirements/20260913/payment-Execution设计/db/command-idempotency-migration.md)。公共包固定读取全部 24 列，不能只部署新 JAR 而保留旧表。

- 先核对实际环境的幂等表及全部 operation_type。表被多个命令共用，不能只迁 PAY_INSTRUMENT_V1 后忽略其他行；本次没有重新连接数据库，先前空库记录不代替执行前盘点。
- 空表可直接按批准方案对齐结构；非空表须分类补齐版本、阶段、时间及回执元数据。旧 PROCESSING 不能直接填成 COMPLETED，也不能凭空赋新 Lease 后恢复。历史 SUCCEEDED/FAILED 的资源与回执依据必须可验证。
- 保留原 scope、key、请求 Hash 与资源绑定。不同历史操作的 Hash 版本分别映射，不能把未知历史统一填 1；快照缺失不得伪造 Token、请求或响应。
- 对 caller_scope / key 改为 ascii_bin 前检查非 ASCII 值、大小写/空格及旧比较规则，不能顺手规范化历史键。列注释、可空性、索引和字段顺序须同步 PDManer 与手工 SQL。
- 数据库切换后需验证：同键同参重放、同键改 Token 的先行 409、Claim 竞争胜出者、只读零写入、旧 Attempt 补绑定、业务/CAS/租约失败完整回滚、动态状态更新、Token/URL 不进入快照或日志。
- 公共包的实现和历史验证证据不等于 Payment 已通过这些场景；制品发布、数据库切换与当前接入状态只见台账。本轮维护 Java/POM/模型和待执行 SQL；按用户要求不编写或运行 Java 单元测试。实际数据库迁移及 HTTP/数据库业务回归单独记录，不能用编译和模型检查代替。

## 6. 当前其他公共能力的复用核对

pay 已使用 Foundation 的 Redis ResourceLock、Snowflake IdGenerator、公共异常/Problem 响应、CodeEnum/UTC Instant TypeHandler 和 MVC 元信息日志。此次明确的重复实现是命令幂等持久化。

Hash 仍是 Payment 的业务合同：公共幂等包不计算它，公共 JSON Canonicalization 也不实现现有长度前缀协议。保持原 Hash 算法不属于重复实现公共幂等机制。今后先核对真实公共能力，有适用公共包就直接使用；存在缺口先列差异，不自行维护平行机制。

公共机制边界：[Foundation 决定](../../../../agent-docs/decisions/2026-09-09-foundation-command-idempotency.md)、[组件说明](../../../foundation/README.md#command-idempotency)。本文属于[原 Execution 需求](../../../../requirements/20260913/payment-Execution设计/technique.md)的后继接入约定，不另建第二份业务合同。
