# 阶段 3 设计总览（V2 骨架与模块搬迁）

- 状态：阶段 3「方案、接口与数据设计」评审稿，C1–C7 已确认并回写各文档，待用户确认整体方案后进入阶段 4 研发。
- 上游：[requirement.md](../requirement.md)（12 步搬迁）、[technique.md](../technique.md)（阶段 2 现状差异，S1–S10）、[决定草案 D01–D42](../../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)。

## 一句话规模总结

本次方案涉及 12 个现有工程重组为 24 个独立部署的逻辑服务（每服务 api / core / app / test 四模块，约 96 个 Maven 模块）与 1 个新 Foundation 模块（`paytech-messaging`）；新增内部接口 12 组（客户主档候选 2、卡域票据与工具 3、计费经济事实登记与查询 2、会计查询与账户一致性 2、合规结算控制 1、消费者资格 1、迁移执行器 1）；新增事件 3 个（`EconomicFactRecorded`、`SettlementRestrictionChanged`、`BalanceMoved` 仅 Schema）；新增表 7 张（账户余额表、账户已应用事实表、账户差异表、卡域票据表、桶移动表、各库 `t_consumed_event`、`t_compensation_event` 每库一份）、改表 5 张（`t_account` 去余额列并更名，经济事实三表更名补列，`t_accounting_instruction` 列更名）、删除死表 `t_command_idempotency` 五处与 Commerce 残留列；无新页面；DML 为空；不执行任何 SQL。

## 文档索引

| 编号 | 文件 | 内容 | 对应待决 |
|---|---|---|---|
| 01 | [01-skeleton-and-conventions.md](./01-skeleton-and-conventions.md) | `paytech/` Reactor、父 POM、api / core / app / test 约定、命名、端口、配置命名空间、实例清单 Schema | D09、D36、Q1、Q5 |
| 02 | [02-archunit-rules.md](./02-archunit-rules.md) | 构建期边界规则集与放置位置 | D08、D09 |
| 03 | [03-module-and-table-ownership.md](./03-module-and-table-ownership.md) | 服务与 Maven 坐标表、逐表归属、共享引用数据处理 | S2、S5、S7、S10 |
| 04 | [04-finance-account-ledger-split.md](./04-finance-account-ledger-split.md) | 账户与会计分库：阶段 0 最小计费事实、`t_account` 拆表、`EconomicFactRecorded` 双消费、关账与试算的跨服务查询、日终核对 | S1、D05、D16、C1 |
| 05 | [05-payment-card-isolation.md](./05-payment-card-isolation.md) | 卡域独立：一次性读卡票据、卡工具两段创建、支付成功卡表事件化、风控属性读取 | S3、D10 |
| 06 | [06-cct-two-stage-resolution.md](./06-cct-two-stage-resolution.md) | 客户主档候选接口、商务两段式解析、商务查询参数化、人员共享标记 | S4、S5 |
| 07 | [07-risk-split.md](./07-risk-split.md) | 风控决策与合规双库双进程、risk-control 并入、`risk-api` 一次性拆分、结算控制归合规 | S2、S8 |
| 08 | [08-messaging-module.md](./08-messaging-module.md) | `paytech-messaging`：事件信封、一致性哈希交换机、单活消费者、消费幂等表、与补偿事件的分工 | D24、D34、S9 |
| 09 | [09-path-coupling-fixes.md](./09-path-coupling-fixes.md) | 路径耦合修复清单（Maven、测试、脚本、Node 工具、PDManer、文档链接） | 保留系统热点 |
| 10 | [10-test-baseline-and-cleanup.md](./10-test-baseline-and-cleanup.md) | 测试基线、测试类拆分、清理器拆分、测试 DDL、本地环境新增库 | 验收 1、7 |
| 11 | [11-dev-plan.md](./11-dev-plan.md) | 15 步研发计划（00–13，11 拆 a/b）、短分支、任务卡、门禁、并行安排 | 阶段 4 |

## 阶段 3 设计选择（C1–C7 已全部由用户确认）

| 编号 | 选择 | 文档 |
|---|---|---|
| C1 | **已定（用户）**：阶段 0 引入最小计费事实，账户从一开始只消费经济事实；影响余额的人工调整须经计费登记为经济事实 | 04 |
| C2 | **已定（用户）**：读卡改为一次性票据，Platform 凭票据直接读 card，Payment 不再提供读卡接口 | 05 |
| C3 | **已定（用户）**：`t_settlement_control` 三表归合规；决策经 compliance-api 创建与读取 | 07 |
| C4 | **已定（用户）**：两表归客户主档，随候选返回 | 06 |
| C5 | **已定（用户）**：事件信封新增 Foundation 模块 `paytech-messaging`，不改 compensation-event；Platform 放开 AMQP 禁令 | 08 |
| C6 | **已定（用户）**：`risk-api` 一次性拆为 `risk-decision-api` 与 `compliance-api`，不保留聚合坐标，五个消费方在第 6 步同时改 | 07 |
| C7 | **已定（用户）**：死表 `t_command_idempotency`、Commerce 准备阶段残留列与 PREPARING 状态、`CheckoutRepository` 等死代码在搬迁时删除 | 03 |

## 本阶段不做

不写代码、不执行 SQL、不改现行 `agent-docs/` 权威正文（V2 规范草案只对 `paytech/` 生效，见 01 第 9 节）。
