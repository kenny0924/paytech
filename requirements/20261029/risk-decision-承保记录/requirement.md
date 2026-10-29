# risk-decision：承保记录 · 需求

> 状态：**阶段 1 已确认（2026-10-29：Q-1—Q-12 按建议；补充估算敞口 R8 / B10 / AC-12 与行业实践对照要求）**。来源：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)（REQ-03，契约 3.3 的 `GOLIVE_UNDERWRITING_APPROVED`）；上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md) N2 风控承保、第 14 节 REQ-03 / REQ-04 拆分、第 15 节 Q-3 / Q-8，及 [运营作业线](../../../design-discuss/client-activation/ops-line.md) T05 / T06 / T08。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- 客户上线流程 N2 要求风控在收款上线前做**承保**：判断平台愿意承担该 Client 多大的交易与拒付风险，定下观察期、保证金要求与结算周期建议。现在承保没有任何系统记录（规划 P3："事后无法证明上线前做过什么"）。
- 承保结论是后续几件事的依据：
  - REQ-04 限额档位：Client 进入 VERIFICATION 档的前提，运行期检查 ③"无承保记录 = 额度为零"由 REQ-04 实现；
  - REQ-18 入驻保证金：Risk 的最低保证金要求；
  - 合同页"破标"提示：结算周期低于建议、免保证金（P1，不在本期）。
- 承保输入已经齐备：
  - 尽调 CRR / 档次：compliance 案件 `riskRating`、`tier`；
  - 行业与 MCC：client-master 运营确认的经营分类；
  - 业务信息：client-master 申请资料，含 REQ-12 新增的期望支付方式、退款政策链接、交付方式与天数。
- risk-decision 现状（已部署 mor-test）：
  - 已有决策点、决策流、规则、名单与限额版本，限额阈值按限额编码全局冻结（`t_limit_version`），不按 Client；
  - 没有任何承保对象，也没有对外部服务的调用客户端或 MQ 消费者；
  - 运营控制台"风控"菜单已有 8 个页面。
- Platform 权限中没有 `RISK_UNDERWRITING` / `RISK_LEAD` 相关权限。Platform MCC 参考数据有 `network_high_risk`（卡组织公布的高风险类目，mor-test 298 个 MCC 中 14 个）。
- 最低保证金的执行事实归 compliance 的 `t_settlement_control`（`MIN_SECURITY_DEPOSIT`），其 API 目前只定了合同、尚无实现，由 REQ-18 接入。

## 2. 目标

1. 风控在运营控制台为每个 Client 建立、提交、复核**承保记录**：输入快照可追溯，结论（APPROVED / DECLINED）不可变，修订产生新版本。
2. 满足自动承保规则（CRR = LOW、预计月流水 ≤ 阈值、MCC 非高风险）的 Client 可按标准模板直接生成 APPROVED 记录（审批人记为系统规则与规则版本），其余走人工；HIGH 风险需风控负责人复核（四眼）。
3. 交付 data-query 模板 `GOLIVE_UNDERWRITING_APPROVED`，让客户上线流程 N2 节点查到"已承保"并取得输出变量。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 风控承保员（`RISK_UNDERWRITING`） | 尽调批准后打开承保页 `/risk/underwriting/new?clientId=` | 页面带出 CRR、尽调档次、MCC / 行业、网站、预计月流水与客单价、期望支付方式、退款政策、交付方式与天数；系统提示是否满足自动承保及原因 |
| 风控承保员 | 满足自动承保 | 一键按标准模板生成 APPROVED（审批来源 = 自动规则 + 规则版本，操作人留痕） |
| 风控承保员 | 人工承保 LOW / MEDIUM | 填观察期、保证金要求、结算周期建议、高风险 MCC 卡组织登记标记与说明，提交即 APPROVED（或 DECLINED，须写原因） |
| 风控承保员 → 风控负责人（`RISK_LEAD`） | 人工承保 HIGH | 承保员提交为 SUBMITTED；负责人（≠ 提交人）批准或拒绝 |
| 风控 | 条件变化需要修订 | 基于当前 APPROVED 建修订草稿，批准后新版本生效，旧版本 SUPERSEDED |
| 客户上线流程（REQ-05 / REQ-07） | N2 节点 `DATA_QUERY` | `GOLIVE_UNDERWRITING_APPROVED` 返回 satisfied 与输出变量，节点自动完成 |
| 商务 / 上线负责人 | 查看承保结论 | 承保列表与详情只读可见（结论、保证金要求、T+N 建议、交接备注） |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | risk-decision 承保记录：每 Client 一条当前记录 + 版本历史；状态 DRAFT → SUBMITTED → APPROVED / DECLINED，APPROVED 修订后 SUPERSEDED；结论字段见 B5；提交时冻结输入快照与来源坐标 |
| R2 | 承保命令：建草稿、保存、提交（LOW / MEDIUM 提交即决定；HIGH 进入待复核）、复核批准 / 拒绝（四眼）、按自动规则生成、基于 APPROVED 建修订；查询：按 Client 取当前记录、历史版本、列表（状态 / CRR / 待我复核筛选） |
| R3 | 自动承保规则：条件 CRR = LOW、预计月流水 ≤ 币种阈值、MCC 不属于高风险（卡组织高风险 ∪ 风控配置排除清单）；规则版本化配置，本期以 DML 维护（Q-3 已定"风控给初值、配置化"） |
| R4 | 运营控制台"风控 → 承保"：列表页、承保页（新建 / 编辑 / 提交 / 复核 / 修订 / 只读详情）；ops-bff 聚合 compliance 案件、client-master 资料与分类、Platform MCC 参考数据和 risk-decision 记录 |
| R5 | Platform 注册：承保读、维护、复核三项权限，菜单与按钮资源，路由；语言包 en / zh-CN |
| R6 | data-query：`GOLIVE_UNDERWRITING_APPROVED` 模板正文（单行约定，参数 `clientId`）与 risk-decision 只读数据源登记步骤（发布动作） |
| R7 | 文档：risk-decision 数据库文档、决定 3.3 输出名、CURRENT_IMPLEMENTATION |
| R8 | **估算敞口**（2026-10-29 用户补充）：承保页按 B10 自动计算并显示，提交时冻结进输入快照；只作参考，不做门禁、不参与自动承保判定 |
| R9 | 方案对照收单 / PayFac 承保行业实践（敞口、额度爬坡、延迟结算、保证金 / 储备金、卡组织高风险登记、自动 / 人工分级与四眼、不可变版本决定、重新承保触发），在技术方案中逐项说明本期采用、延期及原因 |

## 5. 不做项

- 不在交易执行中使用承保结论：限额档位、档位名单、决策流分支、"无承保 = 额度为零"归 REQ-04。
- 不按 Client 设定限额阈值：REQ-04 的限额为档位级全局版本，本期不记录逐 Client 单笔 / 日 / 月额度（见 Q-4）。
- 不创建 compliance 最低保证金控制，也不阻塞收款：REQ-03 只记录保证金要求，下发与执行归 REQ-18 / REQ-04。
- 不做卡组织高风险登记事实（REQ-13 channel-connector），本期只记"需要登记"标记。
- 不做合同页"破标"提示（P1，commercial 另立项）。
- 不发布 `UnderwritingDecided` 事件（规划已定"本期不发"）。
- 不做同 UBO 其他 Client 历史带出。
- 不做尽调未批准 Client 的承保；不因承保拒绝撤销资格（二者是不同判断）。
- 不新建角色与工作队列：角色由现有角色管理组合权限，队列与认领归 REQ-06。
- 不做自动承保规则的维护页面（本期 DML）。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 承保对象是 Client（`clientId`），每个 Client 同时最多一条进行中的记录（DRAFT / SUBMITTED）和一条有效结论（APPROVED）；历史版本保留 |
| B2 | 前提：Client 的入驻尽调案件已批准且有 CRR（LOW / MEDIUM / HIGH），MCC 经运营确认。不满足不能提交（可保存草稿）。存量 Client 尽调无 CRR 时只能人工承保，按 MEDIUM 处理（Q-9） |
| B3 | 自动承保：同时满足 CRR = LOW、预计月流水 ≤ 规则版本中该币种阈值（无该币种阈值视为不满足）、MCC 不在高风险集合 → 可按标准模板生成 APPROVED，审批来源 `AUTO_RULE`，记规则版本与触发人；任一不满足给出原因 |
| B4 | 四眼：CRR = HIGH 的人工承保提交后为 SUBMITTED，须持有复核权限且不是提交人的风控负责人批准或拒绝；LOW / MEDIUM 由承保员提交即决定 |
| B5 | 结论字段：观察期天数（默认 30，1—365）与笔数（默认 100，1—100000）；是否要求保证金、金额与币种（要求时必填，金额 > 0）、是否阻塞收款（仅要求保证金时可选，默认否）；结算周期建议 T+N（整数，0—90）；是否高风险 MCC 需卡组织登记（按高风险集合预勾，可改并须写理由）；交付周期与拒付暴露评估（交付方式带出，承保员确认）；承保说明与交接备注（≤ 500 字） |
| B6 | DECLINED 须写原因；不撤销资格；之后可重新承保（新版本） |
| B7 | APPROVED 记录不可变；修订复制当前结论为新草稿，走同样提交 / 复核；新版本批准时旧版本 SUPERSEDED，有效结论始终唯一 |
| B8 | 提交时冻结输入快照：CRR、尽调档次、MCC、行业、业务信息各项与来源坐标（尽调案件 ID 与版本、申请 ID、主体资料摘要）；之后来源变化不改已提交记录 |
| B9 | 查询模板 `GOLIVE_UNDERWRITING_APPROVED` 以"有效结论为 APPROVED"为满足；输出 `requireDeposit`、`depositBlocksCollection`、`highRiskMcc`、`suggestedSettlementDays`；未满足时 `missing` 为 `UNDERWRITING_NOT_STARTED` / `UNDERWRITING_PENDING_REVIEW` / `UNDERWRITING_DECLINED` |
| B10 | 估算敞口 = 预计月流水 ×（风险天数）/ 30，币种同预计月流水；风险天数按交付方式：PRE_ORDER = 交付天数 + 结算周期建议 T+N；INSTANT = T+N；SUBSCRIPTION = 30（一个计费周期）+ T+N；SERVICE = T+N（事后履约的服务如需更长，承保员在说明中写明）。结算周期建议未填时按 0 计并标注；业务信息缺失时显示"无法估算"。数值保留 2 位小数，只读、不可手改 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **risk-decision** | 新表：承保记录（含版本号、状态、结论、输入快照、来源坐标、决定人 / 时间、审批来源、规则版本、有效槽位）；自动承保规则版本（币种阈值、排除 MCC 清单）；PDManer + 手工 DDL + 规则初值 DML | 表 / DML |
| | 承保 Processor 与 Service：草稿 / 保存 / 提交 / 复核 / 自动生成 / 修订；状态机、四眼、并发（按 Client 加锁 + 版本 CAS）；查询（当前、历史、列表） | 代码 / 接口 |
| | `-api` 新增承保接口与请求 / 响应体（运营调用，带操作人头） | 接口 |
| | auto 测试：状态机、四眼、自动规则（各不满足原因）、修订替换、并发、输入快照冻结、查询 | 测试 |
| **operations-console-bff** | 承保页聚合：compliance 案件（CRR、档次、案件状态）、client-master 申请资料与经营分类（含 REQ-12 四项）、Platform MCC 参考数据（高风险标记）、risk-decision 当前记录；命令转发；列表转发 | 接口 / 代码 |
| **operations-console-web** | "风控 → 承保"菜单：列表页、承保页（输入面板、自动承保判定、结论表单、四眼复核、版本历史、只读详情）；en / zh-CN 文案；Pencil 原型先行 | 页面 |
| **Platform** | 注册块：权限 `RISK_UNDERWRITING_READ` / `RISK_UNDERWRITING_MANAGE` / `RISK_UNDERWRITING_APPROVE`、菜单与按钮资源、BFF 路由；语言包 operations-console 一版 | 注册 DML / 语言包 |
| | data-query：risk-decision 只读数据源（如 `MOR_RISK_REPLICA`，需只读账号）与模板 `GOLIVE_UNDERWRITING_APPROVED` 登记（运行时数据，发布动作） | 运行时登记 |
| **compliance** | 无改动（只读现有案件详情接口） | — |
| **client-master** | 无改动（只读现有申请 / admin values 与分类接口；REQ-12 字段已可读） | — |
| **commercial / channel-connector / finance** | 无改动（破标提示、卡组织登记、保证金执行分别在 P1 / REQ-13 / REQ-18） | — |
| **文档** | risk-decision 数据库文档、决定 3.3 输出名、`CURRENT_IMPLEMENTATION.md` | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | 尽调已批准的 Client 打开承保页，输入面板带出 CRR、档次、MCC / 行业、业务信息（含期望支付方式、退款政策、交付方式与天数），缺失项显示"未采集" |
| AC-02 | CRR = LOW、流水不超阈值、MCC 非高风险时，页面显示"满足自动承保"，一键生成 APPROVED，记录审批来源、规则版本与触发人；任一条件不满足时显示具体原因且不能自动生成 |
| AC-03 | LOW / MEDIUM 人工承保提交即 APPROVED（或填原因 DECLINED）；结论字段按 B5 校验 |
| AC-04 | HIGH 人工承保提交后为 SUBMITTED；提交人不能复核自己；风控负责人批准 / 拒绝后定稿 |
| AC-05 | APPROVED 后修订：新版本批准前旧版本仍有效；批准后旧版本 SUPERSEDED，查询"当前"只返回新版本 |
| AC-06 | 已提交记录的输入快照不随来源变化改变；详情显示快照与来源坐标 |
| AC-07 | 尽调未批准、无 CRR 或 MCC 未确认时不能提交（存量无 CRR 只能人工，按 MEDIUM） |
| AC-08 | `GOLIVE_UNDERWRITING_APPROVED` 在本机与 mor-test 返回单行：APPROVED 时 `satisfied=1` 与四个输出；未开始 / 待复核 / 拒绝时 `satisfied=0` 与对应原因码；可被 REQ-05 的 DATA_QUERY 动作消费 |
| AC-09 | 只有持有对应权限的运营可见菜单、可维护、可复核；同一 Client 并发提交只有一个成功 |

视觉 / 交互：

| 编号 | 标准 |
|---|---|
| AC-10 | 承保页左侧输入快照、右侧结论表单（或原型确认的布局）；自动承保判定以清单形式逐条显示通过 / 未通过；HIGH 待复核态显示复核人操作区 |
| AC-11 | 列表可按状态、CRR、"待我复核"筛选；详情显示版本历史与审批来源；en / zh-CN 完整；1440 / 390 宽度可用 |
| AC-12 | 估算敞口按 B10 随交付方式与 T+N 实时重算，提交后冻结在快照中、详情显示计算公式与各项取值；缺业务信息时显示"无法估算"，不阻止提交 |

## 9. 已确认事实、假设与待确认

已确认：
- **2026-10-29 用户确认阶段 1：Q-1—Q-12 均按建议**（下表"建议"即结论）；Owner 为 risk-decision（非 compliance）；新增估算敞口（R8 / B10）与行业实践对照（R9）。
- 决定 REQ-03 范围、Q-3（自动承保阈值风控给初值、配置化）、Q-8（观察期默认 30 天或前 N 笔）。
- REQ-03 / REQ-04 拆分：执行与档位归 REQ-04。
- `UnderwritingDecided` 本期不发。
- REQ-12 已交付业务信息四项。
- mor-test 现状：
  - 尽调案件 APPROVED 37 个均无 CRR（存量），IN_REVIEW 1 个为 LOW；
  - 无 risk-decision 只读数据源；
  - 无承保相关权限。

假设：A1 运营角色管理可把新权限组合为"风控承保员""风控负责人"两个角色，不需要新建角色机制。

角色组合（2026-10-29 阶段 5 用户选 A）：风控承保员 = `RISK_DECISION_CONFIG_READ` + `RISK_UNDERWRITING_READ` + `RISK_UNDERWRITING_MANAGE`；风控负责人再加 `RISK_UNDERWRITING_APPROVE`。原因：承保菜单挂在"风控"目录下，该目录受 `RISK_DECISION_CONFIG_READ` 控制，缺少它看不到菜单；附带效果是承保角色可只读查看风控规则配置。不改菜单挂载与 Platform 导航。

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-1 | 承保页放在哪里 | 运营控制台"风控"菜单新增"承保"：列表 `/risk/underwriting`、承保页 `/risk/underwriting/new?clientId=` 与 `/risk/underwriting/{id}`（与决定 3.5 深链一致） |
| Q-2 | 输入怎么取、谁是快照来源 | 页面展示由 ops-bff 聚合 compliance / client-master / Platform；提交时 ops-bff 把展示所用输入与来源坐标一并提交，risk-decision 校验必填并冻结为快照。不让 risk-decision 新建对 client-master / compliance 的调用客户端（它目前没有任何外部客户端），保持 M |
| Q-3 | 自动承保是"系统自动生成"还是"页面一键生成" | **本期页面一键**：规则在 risk-decision 服务端按快照判定，满足才允许生成 APPROVED（审批来源 = 自动规则，不需四眼）。全自动（risk-decision 消费 `ClientReviewDecided` 并自行取数）需要首个 MQ 消费者、client-master 类型化读接口与 compliance 读取，规模到 L，另立项 |
| Q-4 | 承保要不要设定逐 Client 三档额度 | **不设**。REQ-04 的档位限额是全局版本，逐 Client 额度没有执行路径；本期记观察期与"额度说明"文本，逐 Client 阈值等真有需求再立项 |
| Q-5 | 保证金要求记到哪 | 承保记录只记"要求 / 金额 / 币种 / 是否阻塞收款"；向 compliance 创建 `MIN_SECURITY_DEPOSIT` 控制与执行归 REQ-18（compliance 该 API 尚未实现） |
| Q-6 | 高风险 MCC 集合来源 | Platform `t_ref_mcc.network_high_risk`（卡组织公布）∪ 自动承保规则版本中的风控排除清单；页面"需卡组织登记"按前者预勾，自动规则用两者并集 |
| Q-7 | 自动承保规则配置形态 | risk-decision 版本化配置表（币种 → 月流水阈值、排除 MCC 列表），本期 DML 维护，记录引用规则版本；初值：USD 50,000、排除清单为空（由风控确认） |
| Q-8 | 权限与四眼 | 新增三项权限：读（列表 / 详情）、维护（草稿 / 提交 / 自动生成 / 修订）、复核（HIGH 批准 / 拒绝）；复核人 ≠ 提交人由 risk-decision 校验；角色组合用现有角色管理（承保角色另需 `RISK_DECISION_CONFIG_READ` 才能看到"风控"目录，见第 9 节"角色组合"） |
| Q-9 | 存量 Client 尽调无 CRR（mor-test 37 个）如何承保 | 允许人工承保并按 MEDIUM 处理（不能自动、不需四眼）；页面提示"尽调未评 CRR" |
| Q-10 | 谁能 DECLINED | LOW / MEDIUM 承保员可直接拒绝（写原因）；HIGH 只能由复核人拒绝；拒绝后通知由客户上线流程负责（REQ-07），risk-decision 不发通知 |
| Q-11 | 查询模板数据源 | 新登记 risk-decision 只读数据源（mor-test 由 DBA 建只读账号、密码进 Vault），模板以 camelCase 列别名输出（`out_requireDeposit` 等），与 REQ-05 O-2 建议一致 |
| Q-12 | 合同页"破标"提示 | 不在本期（P1，commercial 另立项读取承保建议） |

## 10. 阶段安排

默认八阶段。阶段 2 核实：
- risk-decision 模块结构与写接口样板（锁、CAS、BCR / 历史）；
- ops-bff 现有 compliance 案件、client-master 资料 / 分类与 Platform MCC 参考数据读取能力；
- Platform 注册块号与权限模型；
- data-query 数据源登记前提；
- REQ-04 对承保结论的读取形状。

阶段 3 页面原型（承保列表、承保页四态：新建自动判定 / 人工填写 / HIGH 待复核 / 只读详情含版本历史）须先在 `operations-console.pen` 出图并确认。SQL 应用、部署、提交分别授权。
