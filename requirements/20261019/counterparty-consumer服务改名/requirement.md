# counterparty：consumer 服务改名（切片 1）

> 状态：阶段 1 评审稿（2026-10-19 登记）。决定记录 [2026-10-19 counterparty 重新定位](../../../agent-docs/decisions/2026-10-19-counterparty-repositioning.md)（Confirmed）；详细设计 [design-discuss/counterparty/design.md](../../../design-discuss/counterparty/design.md) 第 2.3、11 节，画板 [design.pen](../../../design-discuss/counterparty/design.pen)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景

2026-10-19 用户确认把 consumer 服务重新定位为"Client 命名空间内、不与平台签约的交易相对方主档"，改名 counterparty，为将来给 Client 下的收款人代发预留位置。当前服务、库、发现名与代码仍为 `consumer`；调用方只有 acquiring（另有 card 的调用方白名单常量），Gateway 尚无它的 Route，是改名成本最低的时点。mor-test 已运行 `consumer-service`，`mor_consumer` 中有数据。

## 2. 目标

只改名、不改行为：服务、库、代码与配置全部以 counterparty 命名，所有调用方同步切换，Client API 对外合同不变，mor-test 数据完整保留。

## 3. 本期范围

| 编号 | 范围 |
|---|---|
| S1 | **工程与代码**：`paytech/consumer` → `paytech/counterparty`；Maven 坐标 `paytech-consumer-*` → `paytech-counterparty-*`；Java 包 `com.paytech.consumer` → `com.paytech.counterparty`；表示服务身份的类改名（如 `ConsumerApplication`、`ConsumerCustomerApi`、`ConsumerErrorCode`、`Consumer*Configuration`、`ConsumerTransactionExecutor`、测试基类）；表示业务概念的名字不改（`Customer*`、`consumerReference` / `ConsumerReferencePolicy`、错误码字符串值）。同步修正 `PaymentStoredCardClient` → 指向 card 的命名与 README |
| S2 | **运行身份与内部合同**：`spring.application.name` / 发现名 `consumer-service` → `counterparty-service`；内部合同前缀 `/consumer/v1` → `/counterparty/v1`；配置前缀 `consumer.*` → `counterparty.*`；端口 18109 / 19109 不变；按 D42 不保留旧名别名 |
| S3 | **调用方**：acquiring 的 client、配置、发现名、测试 Mock 与 Mocker；card `CardApiConstants` 调用方白名单（`X-Caller-Service`） |
| S4 | **数据库**：库 `mor_consumer` → `mor_counterparty`，账号 `consumer_app` → `counterparty_app`；PDManer 模型与生成器（`scripts/pdmaner/generate-consumer-model.mjs`、`consumer-model.test.mjs`、`commerce-model-core.mjs`）、Schema / 初始化 DML 文件名、物理决策文档；表结构不变 |
| S5 | **工具与本机环境**：arch-rules `table-ownership.yaml` / `known-deviations.yaml`、instance-manifest `services.yaml`、`local-environment` 建库脚本、`.env.example`、README、`LOCAL_DEVELOPMENT_ENVIRONMENT.md`；本机新建 `mor_counterparty` 并迁入数据，旧库保留至验收后另行确认 |
| S6 | **mor-test**：compose、构建与初始化脚本、健康探针测试；`mor_consumer` 数据迁到 `mor_counterparty`（行数核对）；XXL-Job 执行器改名重新登记；旧库保留至验收后另行确认 |
| S7 | **文档**：服务 README、CURRENT_IMPLEMENTATION、V2 词汇表与目标架构决定中的现行描述（加注改名，不改历史结论原文）、AGENTS.md 路由 |

## 4. 不做项

行为与合同变化（档案、Vault 保护、资料提交入口、Gateway Route 属切片 2 / 3）；Client API 路径与错误码字符串；历史需求目录（20261017、20261018 等）中的记录不改写；删除旧库（另行确认）。

## 5. 验收标准

| 编号 | 标准 |
|---|---|
| AC-01 | 除历史记录（已完成需求目录、决定记录原文、本需求文档）外，仓库中不再出现 `consumer-service`、`mor_consumer`、`consumer_app`、`paytech-consumer`、`com.paytech.consumer`、`/consumer/v1`；检查命令与白名单写入交付记录 |
| AC-02 | counterparty 与 acquiring、card 测试全绿，全 Reactor `verify` 全绿，前后测试数一致或只增不减 |
| AC-03 | Client API 6 个路径、请求响应与错误码不变；内部 3 个接口语义不变，仅前缀改为 `/counterparty/v1`；acquiring 建单关联 Customer 与存卡资格链路通过 |
| AC-04 | PDManer 模型门禁通过；`mor_counterparty` 结构与 Schema 一致 |
| AC-05 | 本机：`mor_counterparty` 建好且数据与旧库逐表行数一致，服务以新名注册并通过健康检查 |
| AC-06 | mor-test：`counterparty-service` 运行、数据逐表行数一致、经 acquiring 的建单关联 Customer 冒烟通过、XXL-Job 执行器在线、`consumer-service` 容器已停 |

## 6. 阶段安排（建议，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 本文 |
| 2. 现状差异与影响分析 | 与阶段 3 合并：引用清单逐文件归类（改 / 不改 / 历史保留） |
| 3. 方案、接口与数据设计 | 改名映射表（包、类、配置键、环境变量）、本机与 mor-test 迁库 SQL 与恢复方案 |
| 4. 研发实现 | Opus 5.5 agent 在独立 worktree 执行，主会话写卡与门禁 |
| 5. 综合验证与本地验收 | 全 Reactor、本机迁库与冒烟 |
| 6. test 发布准备 | mor-test 迁库 SQL、镜像、回滚步骤 |
| 7. test 部署与验收 | 按清单 |
| 8. 提交与归档 | 私有索引提交 |
