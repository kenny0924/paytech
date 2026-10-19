# design-discuss

设计讨论目录（2026-10-19 由 `system-design/` 改名并按主题分文件夹）。放"正在讨论或已讨论定稿的系统设计"：**一个主题一个文件夹**，正文 `design.md`，配套图同文件夹下的 `.pen`。这里的文件是讨论稿与定稿解释，**不是权威规则**：讨论得出结论后登记到 `agent-docs/decisions/`，实现与部署状态只记在 `agent-docs/CURRENT_IMPLEMENTATION.md`，`AGENTS.md` 的路由指向决定记录并链接到这里的详细设计。

## 主题索引

| 文件夹 | 内容 | 状态（2026-10-19） |
|---|---|---|
| （目标架构 V2 画板） | 整个系统按 MoR + PSP 重写的目标架构草图（能力地图原图、目标架构 V2、服务与数据库边界、关键流程、新旧映射与迁移路径、实例 × 模块 × 品牌、决策登记表）**不在本目录**：唯一一份在 `requirements/20261017/architecture-目标架构V2/assets/architecture-design.pen`（2026-10-19 已把工作副本同步过去并删除副本），决定正文见 `agent-docs/decisions/2026-10-17-target-architecture-v2.md` | 已确认并已按它完成 V2 骨架搬迁；再讨论时直接改 assets 里的那份 |
| `billing/` | `design.md`：billing（计费）的职责边界——拥有什么 / 不拥有什么、设计原则、一笔支付的走数例子、事实类型目录、进出合同、待讨论问题 Q1–Q11、首个切片的上游缺口。`design.pen`：同内容的一张长图（9 节：职责、走数、事实类型、合同、问题 Q1–Q12、上游缺口、数据库模型、枚举目录、接口与事件），导出 `assets/design.png`。`database.md`：`mor_billing` 模型精简版——9 → 11 张表（`billing_fact` 四表 + 引用表 + 定价侧表 + 日历两表 + 消息两表；渠道成本四表迁出）、不变量、关系图、待定点；`ddl-draft.sql`：字段级 DDL 草案；`cases.md` + `design.pen` 右侧 Case C1–C10 画板：每种事实一个业务案例（同一笔 100 USD 生意串起来：支付、退款、争议、重复扣款、成本差异、渠道到账、出款、缴税、人工调整、外汇） | **已确认 / 实施中**：决定 `agent-docs/decisions/2026-10-19-billing-fact-engine.md`（Confirmed 2026-10-19）；需求 A 已登记 `requirements/20261019/billing-计费事实模型重构与渠道成本迁出/` |
| `commercial/` | `design.md`：commercial（商务 · 售卖）的职责边界、现状差距、六层产品定价模型（产品 / 能力开通 / 计费事件 / 定价画像 / 价格组件 / 条款附属）、逐产品收费构成、模型改动清单、三条流程、进出合同、已定与待定 Q1–Q8、现状偏差。`design.pen`：两张画板（职责与设计；数据库表与枚举） | **已确认，未实施**：决定 `agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md`；`design.pen` 第三张画板为六层模型例子 |
| `finance-account-ledger-v3/` | `design.md`：account 与 ledger 的模型划分方案 3「明细账 + 控制科目」——职责与主体 / 余额归属、account 9 张表与 ledger 变化、一笔支付从 billing 到 ledger 的完整走数例子与平台利润去向、事件合同、运行约束 8.1–8.10、已定事项、实施影响。`design.pen`：同一方案的图（链路对比、控制科目例子、归属、两库表关系、支付走数、运行约束卡片、事件合同） | **已确认并已实施**：决定 `agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md`；实现见 `requirements/20261019/finance-account明细账与ledger控制科目重构/`，提交 `080909fb`，mor-test 已部署。两处实施修订（聚合 ID 用 `.`、迟到事实过账日期取下一开放期间开始日）已回写 `design.md`，`design.pen` 上的文字未更新 |

## 使用约定

- 新讨论：新建 `<主题>/design.md`，开头写一行状态（讨论中 / 已确认（链接决定）/ 已实施（链接需求目录））和日期；需要图再加 `<主题>/design.pen`，附件放 `<主题>/assets/`。
- 讨论定稿：登记决定记录，并在决定记录里链接本目录的文件；`AGENTS.md` 只在需要路由时加一条。
- 已实施后：实现偏离设计的地方回写到 `design.md`（标注日期与原因），不改历史结论的措辞。
- `.pen` 只用 Pencil 工具读写；Pen 应用里的修改要手动保存才会落盘；文件移动后要在 Pen 中重新打开新路径。
