# commercial：开通项前置门禁 · 需求

> 状态：**阶段 1 评审稿（2026-10-28）**。来源：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)第 ①② 条（REQ-01）；上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md)。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- 现状：合同开通项（`t_agreement_capability`）由 `AddAgreementCapabilityProcessor` 创建时直接 ENABLED；Checkout 解析只输出 ENABLED 且已定价的支付方式。也就是说，**合同一加开通项，Client 就能收款**，不管资格是否仍有效、合同是否已签、渠道上有没有 MID。
- 2026-10-28 决定①：Client 状态只表示合作关系；可收款 / 可出款由开通项控制。因此开通项必须在前置事实满足后才生效，失效后自动暂停。
- 决定②把门禁收窄为三项：① 资格、② 合同价格、④ 渠道身份。承保 / 限额、收款账户、结算控制、保证金由各 Owner 运行期检查，不进门禁。

## 2. 目标

1. 新开通项创建后处于 `PENDING_PREREQUISITES`，只有 ①②④ 全部满足才变 ENABLED；执行链路（Checkout 解析、出款）不改，继续只认 ENABLED。
2. 前置失效（资格撤销 / 逾期、渠道身份失效）时自动 SUSPENDED；恢复后自动 ENABLED。
3. 运营在合同页能看到每个开通项"还差什么"，并能手动触发重查。
4. 为客户上线流程提供三条完成条件查询模板。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 商务 | 在合同里加开通项 | 开通项显示"等前置"，列出未满足项（如"OE AE001 无 CARD 的 ACTIVE MID"）；不需要手动"开通" |
| 商务 / 渠道运营 | 补齐前置（MID 录入、签署生效） | 最迟一个重查周期内开通项自动 ENABLED；可点"重新检查前置"立即生效 |
| 合规 | 撤销或暂停 Client 资格 | 该 Client 所有开通项自动 SUSPENDED，原因显示"资格 REVOKED" |
| Client | 开通项生效 | 门户 / Checkout 可用该支付方式（现有行为） |
| 上线流程（workflow） | N3 / N4 / N8 节点 | 通过 `GOLIVE_AGREEMENT_DRAFTED / agreement_active / collection_capabilities_enabled` 自动判定完成 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | `AgreementCapabilityStatus` 新增 `PENDING_PREREQUISITES`；新建开通项默认该状态（含合同激活时批量创建的） |
| R2 | 前置检查器框架：`QUALIFICATION`（复用 `ComplianceQualificationClientImpl`）、`AGREEMENT_TERM`（本库：合同 ACTIVE 且有 ACTIVE 价格版本覆盖该开通项）、`CHANNEL_IDENTITY`（调 channel-connector 就绪查询，REQ-02 交付前以开关关闭） |
| R3 | 门禁执行：事务外逐项查询 → 短事务按开通项版本 CAS 置 ENABLED / 保持 PENDING / 置 SUSPENDED；记录最近一次检查结果（时间、未满足项） |
| R4 | 触发：合同 ACTIVE 时；消费 `QualificationChanged` 时（该 Client 全部开通项）；XXL-Job 定时重查 PENDING_PREREQUISITES 与 SUSPENDED（默认 5 分钟）；运营"重新检查前置"操作 |
| R5 | 发布 `AgreementCapabilityStatusChanged`（载荷见决定 3.1） |
| R6 | 运营合同页：开通项状态与"还差什么"清单、"重新检查前置"按钮；BFF 聚合 |
| R7 | data-query 登记三条查询模板（Owner = commercial）：`GOLIVE_AGREEMENT_DRAFTED`（outputs：agreementId、businessMode、entityNo、capabilities、localMethods）、`GOLIVE_AGREEMENT_ACTIVE`、`GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（outputs：pendingCapabilities） |
| R8 | 存量：已 ENABLED 的开通项保持不变，不回溯；只对新增开通项启用门禁；提供一次性巡检查询（只读报告，不改数据） |
| R9 | 文档：commercial 数据库文档、`CURRENT_IMPLEMENTATION.md`、design-discuss commercial 相关描述 |

## 5. 不做项

- 不把承保、收款账户、结算控制、保证金作为门禁前置（决定②）。
- 不改 Checkout 解析、出款的判定逻辑（它们已只认 ENABLED）。
- 不做 Client 门户"上线清单"（REQ-10）、不做子商户 / 登记事实（M3）。
- 不提供运营手动"强制 ENABLED"；需要绕过时先在 Owner 侧补事实。
- 不新建通用"就绪聚合"服务。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 状态机：`PENDING_PREREQUISITES` → ENABLED（①②④ 全满足）；ENABLED → SUSPENDED（任一失效）；SUSPENDED → ENABLED（恢复）；ENABLED / SUSPENDED / PENDING → DISABLED（运营关闭，已有）。DISABLED 终态不再重查 |
| B2 | 出款通道类开通项同样走 ①②，④ 判定为"该 OE 下对应出款通道配置存在"（阶段 2 核实出款通道配置的 Owner 与查询口径） |
| B3 | 查询失败 / 超时 = 该项"尚未满足"，不报错、不改状态（PENDING 保持；ENABLED 不因查询失败而 SUSPENDED，只因明确的失效结论而 SUSPENDED） |
| B4 | 同一开通项的检查串行（按开通项加锁或 CAS），并发触发只执行一次 |
| B5 | `CHANNEL_IDENTITY` 检查器开关关闭时，④ 视为满足并在检查结果中标注"渠道身份未校验（开关关闭）"；REQ-02 发布时打开开关并全量重查 |
| B6 | 事件消费幂等：按事件 ID；事件只触发重查，结论以同步查询为准 |
| B7 | "还差什么"清单文案为文案键 + 参数，前端多语言 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **commercial** | 枚举 `AgreementCapabilityStatus` 新值；`t_agreement_capability` 新增最近检查时间 / 结果列（阶段 3 定）；PDManer + DDL | 模型 / 表 |
| | 前置检查器接口与三个实现；门禁 Processor（事务外查 + 短事务置位）；"重新检查前置"写接口；合同激活 Processor 改为创建 PENDING 开通项并触发门禁 | 代码 |
| | `QualificationChanged` 消费者（新）；`AgreementCapabilityStatusChanged` 发布（compensation-event） | 事件 |
| | XXL-Job 重查任务 | Job |
| | `-api`：开通项响应增加状态与检查结果；"重新检查前置"接口 | 接口 |
| | auto 测试：状态机五条路径、查询失败不改状态、并发只执行一次、事件幂等 | 测试 |
| **channel-connector** | 无改动（就绪查询由 REQ-02 交付；本需求只定义客户端与开关） | — |
| **compliance** | 无改动（`QualificationEvaluationApi` 已有） | — |
| **Platform data-query** | 登记 commercial 只读数据源（如尚未）与三条查询模板（模板文本由本需求交付） | 配置 |
| **operations-console-bff / web** | 合同详情：开通项状态徽标、"还差什么"列表、"重新检查前置"按钮；i18n 文案键 | 页面 |
| **Platform Gateway / 注册** | "重新检查前置"接口的 Route 与权限注册 | 注册数据 |
| **文档** | commercial 数据库文档、design-discuss、CURRENT_IMPLEMENTATION | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | 新建开通项状态为 PENDING_PREREQUISITES；Checkout 解析不输出它 |
| AC-02 | 资格 APPROVED + 合同 / 价格 ACTIVE + 渠道身份就绪（或开关关闭）→ 一个重查周期内变 ENABLED，发布事件 |
| AC-03 | 任一前置未满足时保持 PENDING，检查结果列出未满足项与文案键 |
| AC-04 | 消费 `QualificationChanged`（REVOKED）→ 该 Client 所有 ENABLED 开通项变 SUSPENDED；资格恢复 → 自动 ENABLED |
| AC-05 | 就绪查询超时 / 5xx → 状态不变，检查结果记"查询失败" |
| AC-06 | "重新检查前置"立即执行并返回最新结果；并发两次只执行一次 |
| AC-07 | 存量 ENABLED 开通项在发布后不变；巡检报告能列出"存量 ENABLED 但 ①②④ 不满足"的记录 |
| AC-08 | 三条 data-query 模板在 data-query 执行返回 `satisfied / outputs / missing` 形状 |

视觉 / 交互（合同详情页）：

| 编号 | 标准 |
|---|---|
| AC-09 | 开通项列表每行显示状态徽标（等前置 / 已开通 / 已暂停 / 已关闭）；等前置与已暂停行可展开"还差什么"，每条带 Owner 与说明 |
| AC-10 | "重新检查前置"按钮在等前置 / 已暂停时可用，点击后行内刷新，失败有错误反馈 |
| AC-11 | 空状态：合同无开通项时提示；en / zh-CN 文案完整 |

## 9. 已确认事实、假设与待确认

已确认：决定①②、Q-8（存量不回溯）、门禁只查 ①②④。

假设：

- A1 出款通道类开通项的"渠道身份"口径为"该 OE 下存在对应出款通道配置"（阶段 2 核实 Owner）。

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | `CHANNEL_IDENTITY` 开关关闭期间 ④ 视为满足（B5），还是视为不满足（新开通项在 REQ-02 前全部卡住）？ | 视为满足并标注；REQ-02 发布时全量重查。否则 mor-test 上 REQ-01 无法单独验收 |
| Q2 | 重查周期默认 5 分钟是否可接受？ | 可配置，默认 5 分钟；"重新检查前置"覆盖急用场景 |
| Q3 | 合同 ACTIVE 时批量创建的开通项是否也走 PENDING（而不是沿用现有直接 ENABLED）？ | 走 PENDING；否则门禁有漏洞 |

## 10. 阶段安排

默认八阶段。阶段 2 核实：出款通道配置 Owner；`QualificationChanged` 载荷；Checkout 解析对 PENDING 的处理是否已天然排除；合同激活时开通项创建路径。SQL 应用、部署、提交分别授权。
