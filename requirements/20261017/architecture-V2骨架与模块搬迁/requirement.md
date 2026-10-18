# V2 骨架与模块搬迁（阶段 0 重构）需求说明

- 状态：阶段 1 需求评审稿，待用户确认范围、顺序与待决问题。
- 登记日期：2026-10-17
- 上游：[目标架构 V2 需求](../architecture-目标架构V2/requirement.md)、[决定草案 D01–D42](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)，本需求对应其中 D09、D11、D22、D24、D25、D26、D31、D33、D34、D35、D36。
- 分支：长期集成分支 `refactor-main`，工作目录 `Code-MoR-refactor`。每个搬迁步骤用短分支 `refactor/step-N-<名称>` 合入 `refactor-main`，**不逐步合回 `main`**；每步后把 `main` 合入 `refactor-main` 控制分叉。全部搬完后在 `refactor-main` 做既有功能全链路验证，验收通过再整体合并到 `main`（D36）。
- 确认记录：[delivery.md](./delivery.md)

## 1. 目标

把现有仓库的 11 个 Java 工程重组为 V2 的逻辑服务结构，并补齐阶段 0 的公共契约。完成后：每个逻辑服务是 `api + core + app` 模块加独立库，**每个服务独立进程**（D09 修订）；服务间只经 `*-api` 的 HTTP 调用；事件信封带聚合键与版本号；边界由 ArchUnit 在构建期强制。除两处必须重做的事务逻辑（见 2.2 第 11、12 步）外**不改变任何业务行为**。

## 2. 范围

### 2.1 新骨架

- 新建仓库顶层 `paytech/` 单一 reactor 与统一父 POM（项目名 paytech），下按 V2 服务分目录，定义 `api / core / app / test` 模块约定、依赖白名单、ArchUnit 规则集（core 不得 import 其他 core 内部包；core 不得依赖 app；领域层 core 不得依赖流程层 api）。
- 实例清单 Schema（实例 ID、运营方、业务模式、模块、品牌、渠道、版本）；每个逻辑服务一个 `*-app`，部署描述按实例清单生成。
- `*-api` 的 HTTP Client 绑定为唯一生产绑定；进程内本地 Bean 绑定只用于测试组合。

### 2.2 搬迁顺序（每步一个短分支合入 refactor-main，随后合入 main 的最新提交；第 11、12 步为逻辑重做）

| 步 | 来源 | 目标 | 变化 |
|---|---|---|---|
| 1 | `paytech-foundation` | `foundation` | 目录与模块布局；新增内部签名 Token（D26）、事件信封（聚合键、版本号、事件 ID、Schema 版本，D24） |
| 2 | `platform-support-service`、`api-gateway`、三个 `*-console-bff`、`vault-service` | 同名 V2 目录 | 只改目录与模块布局，业务不动 |
| 3 | `commerce-service` | `acquiring`（transaction / checkout / command / client-api 四模块共用一库，一个 app）+ `consumer`（独立 core、库、app） | 按 D31 命名；Payment 已存卡以 `customer_id` 为键，ID 保持不变；资格判断改经 `consumer-api` 并定义降级 |
| 4 | `client-contract-service` | `client-master` 与 `commercial` 各自 core / 库 / app | 11 条商务查询改为以 OE 编号与合作伙伴 ID 列表为参数；Checkout 解析改为两段式（候选 + 根版本 → 解析 → 回查） |
| 5 | `payment-orchestration-service` | `payment`、`channel-connector`、`card` 各自 core / 库 / app | 渠道结果映射迁入 channel-connector；卡域独立见第 12 步 |
| 6 | `risk-compliance-service` | `risk-decision`（并入 risk-control）与 `compliance` 各自 core / 库 / app | 争议处理留待业务切片；`t_settlement_control` 归属按 S2 |
| 7 | `finance-service` | `billing` / `account` / `ledger` / `reconciliation` / `tax` 五个 core，各自库与 app | 各自持库（D16）；只搬现有代码与表归属，不实现缺失能力；账户与会计的事务重做见第 11 步 |
| 8 | `compensation-event` 库 | RabbitMQ 一致性哈希交换机 + 单活消费者 + 事件信封（D34） | 事件目录 v1 文档同步 |
| 9 | `local-environment` | 加 Doris + Flink CDC 容器、白名单表约定、`data-service` 骨架（D35） | 不做查询实现 |
| 10 | 新增 | 实例内迁移执行器骨架（D11 / D22） | 读版本化 SQL、按实例执行、记录日志；不接入运维平面 |
| 11 | Finance 账户与会计 | `t_account` 拆为会计科目账户与账户余额账户；过账内核、自动记账、人工调整、损益结转、重开冲销、投影重建六处事务按 D16 / S1 改为最终一致 | **逻辑重做**；阶段 3 出方案，阶段 4 实现；现有 80 例测试全部通过并新增一致性测试 |
| 12 | Payment 卡域 | 支付成功对卡表的写入改为事件驱动；读卡回调改为 payment 签发的一次性有范围授权（S3）；卡域独立进程与库 | **逻辑重做**；阶段 3 出方案，阶段 4 实现；现有 6 个链路测试通过并新增授权一次性与失败不可重取测试 |

### 2.3 不做项

- 除第 11、12 步外，不新增或修改任何业务功能、接口语义、表结构（表的"归属"变化不等于结构变化）。第 11 步会新增账户余额表并改 `t_account` 职责，第 12 步会新增卡域事件与授权表，均在阶段 3 以 SQL 归档并单独授权执行。
- 不实现计费、出款、争议、数据服务查询等缺失能力。
- 不部署 mor-test，不推送，不合并 main（推送、整体合并各自单独授权）。
- 不修改现行 `agent-docs/` 权威正文；D11 / D26 / D09 的规范修订作为本需求末尾的独立确认项。

## 3. 验收标准

1. 每一步搬迁后，该工程的全部 `auto` 测试在新位置通过，数量不少于搬迁前（以当前台账的测试数为基线）。
2. ArchUnit 规则在全部 V2 模块上通过；人为引入一次跨 core 内部包 import 必须失败。
3. 每个逻辑服务的 `*-app` 可独立启动并通过自身 `auto` 测试；服务间调用全部经 `*-api` HTTP Client，用一个最小往返验证。
4. 事件信封在 compensation-event 的本地 RabbitMQ 测试中证明：同聚合键事件落同一队列并按序消费；不同键并行。
5. Doris 与 Flink CDC 容器在 `local-environment` 可启动并同步一张白名单表的 DML（骨架级，一张表即可）。
6. 旧根目录全部搬空并删除；`git log --follow` 可追溯任一搬迁文件的历史。
7. 每步后 `main` 可无冲突或仅路径冲突地合入 `refactor-main`；全部搬完后在 `refactor-main` 上既有功能全链路验证通过（用例清单在阶段 5 列出），再整体合并 `main`。

## 4. 已决问题（2026-10-17 用户确认）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | V2 目录树根名称 | 仓库顶层 `paytech/` 单一 reactor；项目名 paytech |
| Q2 | 搬迁期间其他会话的配合与分支策略 | 按系统逐个暂停；不逐步合回 main，用长期集成分支 `refactor-main`，搬完后全链路验证再整体合并 |
| Q3 | 卡域是否在本阶段独立 app | 作为 payment-app 内模块，等 D10 |
| Q4 | 规范修订时机 | 阶段 3 写 V2 规范草案只对 `paytech/` 生效，搬完后替换现行文档 |
| Q5 | 部署粒度 | 每个逻辑服务独立进程与库，不做组合部署（D09 修订）；测试环境按 8 核 64 GB 规划 |
| Q6 | 账户与会计、支付执行与卡域两对耦合 | 阶段 0 重做事务逻辑并彻底拆开，方案在阶段 3、实现在阶段 4（D36 修订） |

## 5. 阶段安排

阶段 1 需求评审（本文）→ 阶段 2 现状差异（逐工程列模块、表、测试基线与跨表 SQL）→ 阶段 3 方案（父 POM、模块约定、ArchUnit 规则、事件信封与一致性哈希拓扑、实例清单 Schema）→ 阶段 4 按 2.2 顺序研发，每步阶段 5 验证并合入 refactor-main；全部完成后阶段 5 全链路验证 → 整体合并 main → 阶段 8 归档。不涉及 test 部署，阶段 6、7 不适用。
