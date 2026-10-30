# risk-decision：限额档位 · 需求

> 状态：**阶段 1 已确认（2026-10-29，Q-1—Q-14 按建议；阶段 2 确认时 S-05 改为按币种分别设额度，同步修订 B8、B11、AC-06、AC-11、Q-13）**。来源：
> - [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)：REQ-04、契约 3.3 的 `GOLIVE_LIMIT_TIER`；
> - 上游讨论 [design-discuss/client-activation](../../../design-discuss/client-activation/requirement-plan.md)：5.1 限额档位、5.2 执行开关、N8 档位联动、N10 上线复核、N11 观察期、运行期检查 ③、第 14 节 REQ-04；
> - [运营作业线](../../../design-discuss/client-activation/ops-line.md) T26 / T27 / T28；
> - 前序需求 [REQ-03 承保记录](../risk-decision-承保记录/requirement.md)。
>
> 确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- 客户上线规划把"放量"做成 Risk 的执行开关：开通项生效后 Client 已能交易，但只有**验证额度**；上线复核（N10）批准后进入**观察额度**，观察期满（N11）进入**标准额度**；异常时可切到**受限**。没有承保记录的 Client 额度为零（运行期检查 ③）。
- 现状（代码与 mor-test 只读核对）：
  - **限额与决策流**：
    - risk-decision 的硬限额（`t_limit_version`）是按限额编码全局冻结的版本。它绑定在事件点版本的系统义务上（`t_decision_point_limit_binding`），**不按决策流分支、也不按 Client 区分**。mor-test 目前没有任何限额版本。
    - 决策流支持条件连线（`t_decision_flow_edge.condition_json`）分支到不同规则集。
    - 规则可用名单命中 Feature 与实时累计 Feature（`t_velocity_feature_definition`：CLIENT 维度、SUM_AMOUNT / COUNT、按窗口秒数，事件为 CHECKOUT_ASSESSED / PAYMENT_ATTEMPT_ASSESSED 等）。
  - **名单**：`t_risk_list.list_purpose` 只有 BLOCK / ALLOW；同一名单同一主体至多一个生效成员，但跨名单没有互斥。
  - **事件点**：`CHECKOUT_PREPARE`（结账）与 `PAYMENT_ATTEMPT_RISK`（支付尝试）在 mor-test 都有 ACTIVE 发布，现有流程不按档位分支。
  - **消息**：risk-decision 没有任何 MQ 消费者与外部调用客户端。
  - **承保**：REQ-03 已提供 `ClientUnderwritingQueryService.findEffective(clientId)`，结论含观察期天数 / 笔数，承保页"起始档位"写明"验证档 · 观察期满由 REQ-04 推进"。
  - **事件**：commercial 已发布 `AgreementCapabilityStatusChanged` v1（`commercial.events`，一个合同版本一条，`changes[]` 带 from / to 执行状态），目前无消费者，目录注明"risk-decision 在 REQ-04 接入"。
  - **mor-test 数据**：ACTIVE Client 39 个，有 ENABLED 开通项的 Client 4 个，已有承保记录 5 条；**没有任何 Client 在档位中**——若直接上线"不在任何档位 = 拒绝"，现有可交易 Client 会被全部拒绝。
  - **交易计数**：risk-decision 没有支付成功事实（`t_payment_attempt_fact` 0 行，无 `PaymentSucceeded` 消费），只有自己的评估事件（`t_velocity_event`）。

## 2. 目标

1. risk-decision 为每个 Client 维护一个**限额档位**（VERIFICATION / OBSERVATION / STANDARD / RESTRICTED），变更可追溯、四眼批准。
2. 决策流按档位分支，各档位有自己的单笔 / 日 / 月额度；不在任何档位的 Client 拒绝（= 无承保额度为零）。
3. 承保批准后自动进入 VERIFICATION；上线复核批准后进入 OBSERVATION；观察期满由风控确认进入 STANDARD；任意档位可切 RESTRICTED。
4. 交付 data-query 模板 `GOLIVE_LIMIT_TIER`，供客户上线流程 N10 / N11 节点判断完成。
5. 存量可交易 Client 平滑过渡，不因上线本需求被误拒。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 系统 | 承保记录 APPROVED（人工或自动） | Client 无档位时自动进入 VERIFICATION，记录来源 = 承保版本 |
| 上线负责人 / 平台运营 | 生产验证完成后，在"限额档位"页对 Client 申请 VERIFICATION → OBSERVATION（上线复核） | 页面带出复核清单（资格与 CRR、承保结论、合同与开通项状态等），逐项确认并写上线说明后提交申请 |
| 风控（LOW：`RISK_UNDERWRITING`；MEDIUM / HIGH：`RISK_LEAD`） | 批准或驳回档位变更申请 | 批准后档位立即生效，申请人 ≠ 批准人 |
| 风控 | 观察期内查看进度 | 页面显示观察开始时间、已过天数 / 目标天数、放行笔数 / 目标笔数，到期提示 |
| 风控 | 观察期满确认 OBSERVATION → STANDARD，或延长观察、切 RESTRICTED | 同一四眼申请机制；生效后记录 |
| 风控 | 发现异常把 Client 切 RESTRICTED，或从 RESTRICTED 恢复 | 同一四眼机制；RESTRICTED 下新交易按流程配置拒绝 |
| 支付链路（acquiring 结账、payment 支付尝试） | 调用既有 Risk 评估 | 决策流按档位走对应分支与额度；无档位拒绝 |
| 客户上线流程（REQ-05 / REQ-07） | N10、N11 节点 `DATA_QUERY` | `GOLIVE_LIMIT_TIER` 返回当前档位与是否达到目标档位 |
| 风控配置员 | 在现有决策流 / 规则集页面配置档位分支与额度规则 | 档位名单命中可作为分支条件；激活发布后生效 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | 档位事实：每个 Client 当前档位（至多一个）、生效时间、来源（承保初始化 / 变更申请 / 迁移）；档位变更历史 |
| R2 | 档位变更申请与四眼批准：申请（目标档位、理由、上线复核清单确认）、批准 / 驳回、撤回；批准人分级按 CRR；申请人 ≠ 批准人 |
| R3 | 自动初始化：承保 APPROVED 且 Client 无档位时，同一事务设为 VERIFICATION |
| R4 | 决策流可读档位：档位以名单命中 Feature 暴露给决策流（四张系统维护的档位名单，`list_purpose = TIER`）；名单成员随档位变更同事务维护，运营不能手工增删档位名单成员 |
| R5 | 额度：在现有决策流 / 规则集中按档位分支配置单笔 / 日 / 月额度规则（实时累计 Feature）；交付基线配置说明与一套可导入的 TEST 环境示例配置；"不在任何档位"走拒绝分支 |
| R6 | 观察期进度：按承保结论的观察期天数 / 笔数计算进度与到期提示（只提示，不自动升档） |
| R7 | 运营控制台"风控 → 限额档位"：档位列表（筛选档位、到期待确认、待审批）、Client 档位页（当前档位、观察期进度、复核清单、变更申请、审批、历史） |
| R8 | 存量迁移：上线前把已有 ENABLED 开通项的 Client 设为 STANDARD（来源 = 迁移，记录批准人）；其余存量 Client 无档位 |
| R9 | data-query：`GOLIVE_LIMIT_TIER` 模板（参数 `clientId` 与目标档位）与登记步骤；Platform 注册（权限、菜单、路由）与语言包 |
| R10 | 文档：risk-decision 数据库文档、决策流档位配置说明、决定 3.3 输出名、CURRENT_IMPLEMENTATION |

## 5. 不做项

- 不改限额引擎：不让硬限额（`t_limit_version`）按分支或按 Client 选版本；档位额度用规则 + 实时累计实现（见 Q-3）。
- 不做逐 Client 自定义额度（REQ-03 Q-4 已定）。
- 不按支付成功笔数精确计数观察期（risk-decision 没有支付成功事实）；本期用放行的支付尝试数近似（见 Q-6）。
- 不自动升档：观察期到期只提示，升 STANDARD 需人工确认。
- 不发布 `ClientLimitTierChanged`（见 Q-8）。
- 不在本需求中消费 `AgreementCapabilityStatusChanged`（见 Q-2）。
- 不做"阻塞收款直到保证金到账"的执行（REQ-18）。
- 不做复核清单中尚无 Owner 查询的项的系统核对：渠道报备、技术接入、生产验证在本期为人工勾选确认，由 REQ-02 / REQ-09 等后续接入。
- 不新建角色与队列（REQ-06）。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 档位取值 VERIFICATION、OBSERVATION、STANDARD、RESTRICTED；每个 Client 同时至多一个当前档位；无档位视为"额度为零" |
| B2 | 自动初始化只在 Client 无档位时发生：承保 APPROVED（首次或修订）时 Client 尚无档位 → VERIFICATION；已有档位时承保批准（含修订）不改档位；承保 DECLINED 不影响已有档位（阶段 5 修订 F-1，用户 2026-10-30 选 a：REQ-04 上线前已批准、无开通项的存量 Client 在下次修订批准时获得 VERIFICATION） |
| B3 | 允许的人工变更：VERIFICATION → OBSERVATION（上线复核）；OBSERVATION → STANDARD（观察期满）；OBSERVATION → OBSERVATION（延长观察，重置观察开始时间）；任意 → RESTRICTED；RESTRICTED → 先前档位或更低档位。其他组合拒绝 |
| B4 | 变更须申请 + 批准：申请人与批准人不同；CRR = LOW 时持有档位审批权限者可批准，CRR = MEDIUM / HIGH（或无 CRR）须持有高级审批权限；同一 Client 同时至多一个待审批申请 |
| B5 | VERIFICATION → OBSERVATION 申请须提交复核清单：系统带出项（资格状态与 CRR、有效承保结论、合同与支付方式开通项状态）为只读，人工确认项（渠道报备、技术接入、生产验证、未结事项）须逐项勾选；并写上线说明 |
| B6 | 批准即生效：同一事务更新当前档位、写历史、维护四张档位名单成员（移出旧档位名单、加入新档位名单） |
| B7 | 观察期：进入 OBSERVATION 时记开始时间；目标取有效承保结论的观察期天数 / 笔数（无承保时默认 30 天 / 100 笔）；任一达到即标"到期待确认"；放行笔数 = 该 Client 进入观察期后 `PAYMENT_ATTEMPT_RISK` 放行的评估次数（近似值，页面注明） |
| B8 | 决策流约定：结账与支付尝试两个事件点的生效流程第一层按档位名单分支；无档位走拒绝（原因"未完成承保 / 无交易额度"）；RESTRICTED 默认拒绝；各档位额度规则由风控在规则集中配置，按币种分别设额度（见 B11） |
| B9 | 迁移：上线顺序为"迁移存量 → 激活带档位分支的发布"；激活前所有已能交易的 Client 必须已有档位 |
| B10 | `GOLIVE_LIMIT_TIER`：参数 `clientId`、`targetTier`（OBSERVATION / STANDARD）；当前档位不低于目标档位（VERIFICATION < OBSERVATION < STANDARD，RESTRICTED 不满足）即 satisfied；输出 `tier`、`observationDue`；`missing` 为 `LIMIT_TIER_NOT_SET` / `LIMIT_TIER_BELOW_TARGET` / `LIMIT_TIER_RESTRICTED` |
| B11 | 额度按档位 × 币种分别配置单笔、UTC 当日、近 30 个 UTC 自然日（含当日）三个值（如 USD、AED、SAR、EGP 各一组；阶段 4 修订：日 / 月按 UTC 自然日汇总，原"滚动 24 小时 / 30 天"不再适用），**不做汇率换算，不同币种分别累计、互不合并**；某档位未配置某币种时：VERIFICATION / OBSERVATION / RESTRICTED 拒绝该币种交易，STANDARD 放行（不设额度）；统一折算为 USD 的额度需要汇率来源，另立需求 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **risk-decision** | 新表：Client 当前档位（含观察开始时间、来源、版本）、档位变更申请（申请人、目标档位、理由、复核清单快照、批准人、状态）；PDManer + DDL | 表 |
| | `t_risk_list.list_purpose` 增加 `TIER`（列注释、枚举）；初始化 DML 建四张系统维护的档位名单（CLIENT 主体）与对应名单命中 Feature 版本 | 表注释 / DML |
| | 名单维护：TIER 名单拒绝人工增删成员（现有名单接口加校验） | 代码 |
| | 档位 Processor / Service：申请、批准 / 驳回、撤回；四眼与分级；同事务维护名单成员；当前档位与历史查询；观察期进度计算 | 代码 / 接口 |
| | 承保批准链路（REQ-03 的提交 / 复核 / 自动承保）：APPROVED 时 Client 尚无档位则同事务初始化 VERIFICATION | 代码（改 REQ-03 处理器） |
| | 存量迁移命令或 DML：已有 ENABLED 开通项的 Client 设为 STANDARD（名单列表由发布时从 commercial 只读导出） | DML / 运维 |
| | `-api` 档位接口与请求 / 响应体；错误码 | 接口 |
| | 决策流档位配置说明与 TEST 示例配置（档位分支、额度规则、拒绝分支） | 配置 / 文档 |
| | auto 测试：状态迁移、四眼与分级、名单同步、承保初始化、观察期进度、迁移、按档位分支的评估（含无档位拒绝） | 测试 |
| **operations-console-bff** | 档位页聚合：risk-decision 档位与申请、REQ-03 承保结论、compliance 资格与 CRR、commercial 合同与开通项状态；命令转发；列表转发；操作人显示名 | 接口 / 代码 |
| **operations-console-web** | "风控 → 限额档位"列表页、Client 档位页（当前档位、观察期进度、复核清单、申请 / 审批 / 历史）；Pencil 原型先行；en / zh-CN | 页面 |
| **Platform** | 注册块：档位读、申请、审批、高级审批权限，菜单与按钮，路由；语言包 | 注册 DML / 语言包 |
| | data-query：`GOLIVE_LIMIT_TIER` 模板登记（复用 REQ-03 的 `MOR_RISK_DECISION_REPLICA`） | 运行时登记 |
| **commercial / compliance** | 无改动（只读现有合同 / 开通项、资格 / 案件接口） | — |
| **acquiring / payment** | 无代码改动；行为变化来自 Risk 发布：无档位 Client 的结账 / 支付被拒，超额被拒 | 行为 |
| **文档** | risk-decision 数据库文档、档位配置说明、决定 3.3、`CURRENT_IMPLEMENTATION.md` | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | 承保 APPROVED（人工或自动，首次或修订）后，无档位的 Client 立即为 VERIFICATION，历史记来源承保版本；已有档位时修订批准不改档位 |
| AC-02 | 申请 VERIFICATION → OBSERVATION 须完成复核清单与说明；LOW 由档位审批人批准，MEDIUM / HIGH 须高级审批人；申请人不能批准自己的申请 |
| AC-03 | 批准后档位立即生效，四张档位名单成员与当前档位一致（任何时刻一个 Client 至多在一张档位名单中） |
| AC-04 | 不允许的迁移（如 VERIFICATION → STANDARD）被拒；同一 Client 同时只有一个待审批申请 |
| AC-05 | 观察期进度按承保的天数 / 笔数显示，任一到达标"到期待确认"；不自动升档 |
| AC-06 | 在配置了档位分支的 TEST 发布下：无档位 Client 的结账 / 支付尝试被拒；VERIFICATION 单笔超额被拒、额度内放行；OBSERVATION / STANDARD 按各自额度；RESTRICTED 被拒；同一 Client 的 USD 与 AED 交易分别累计、分别受限；VERIFICATION 下未配置额度的币种被拒，STANDARD 下未配置额度的币种放行 ；阶段 4 修订：日 / 月额度为 UTC 当日与近 30 个 UTC 自然日，结账点不做金额额度 |
| AC-07 | 存量迁移后，迁移名单中的 Client 为 STANDARD；激活带档位分支的发布后其交易不受影响 |
| AC-08 | 运营不能通过名单页面手工增删档位名单成员 |
| AC-09 | `GOLIVE_LIMIT_TIER` 按目标档位返回 satisfied 与输出；可被 REQ-05 的 DATA_QUERY 消费 |

视觉 / 交互：

| 编号 | 标准 |
|---|---|
| AC-10 | 档位列表可按档位、到期待确认、待审批筛选；Client 档位页显示当前档位徽标、观察期进度条、复核清单（系统项带来源与状态、人工项可勾选）、申请 / 审批区、历史时间线；en / zh-CN；1440 / 390 |
| AC-11 | Client 档位页显示该档位各币种的单笔 / UTC 当日 / 近 30 个 UTC 自然日额度与当前已用（放行累计，按 UTC 自然日汇总）；未配置的币种按档位显示"不允许"或"不限" |

## 9. 已确认事实、假设与待确认

已确认：
- 决定 REQ-00 原则 2（限额看 Risk 限额档位）。
- 规划已定事项：
  - Q-2：引入限额档位作为放量开关；
  - Q-6：上线复核分档，LOW 单人、MEDIUM / HIGH 负责人；
  - Q8：观察期默认 30 天或前 N 笔。
- REQ-03 已提供 `findEffective`；REQ-03 Q-4 不设逐 Client 额度。
- mor-test 现状见第 1 节。

假设：
- A1 决策流条件连线可以用名单命中 Feature 作条件（规划第 16 节已核：名单"可做 Feature 供决策流分支"；阶段 2 复核）。
- A2 实时累计 Feature 的 SUM_AMOUNT 可按 CLIENT 与 1 天 / 30 天窗口配置并在规则中比较。（阶段 4 核实不成立：CLIENT 金额合计最长 1 小时；用户选方案 B，新增按 UTC 日汇总的 `DAILY_SUM_AMOUNT`，见技术方案末节。）

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-1 | 档位存在哪：只用三 / 四张名单（规划），还是专用档位表 | **专用档位表为权威 + 四张 TIER 名单为投影**：档位表保证"一个 Client 一个档位"、四眼申请与历史；批准时同事务维护名单成员，决策流继续用现有名单命中 Feature 分支，不改决策引擎 |
| Q-2 | 初始化触发：消费 commercial `AgreementCapabilityStatusChanged`（规划），还是承保 APPROVED | **承保首次 APPROVED 时本地同事务初始化**：不引入 risk-decision 第一个 MQ 消费者；开通项未 ENABLED 时 commercial 门禁本来就不放行交易，提前进入 VERIFICATION 无副作用，且天然满足"无承保 = 额度为零" |
| Q-3 | 各档位额度如何落到现有引擎 | **规则 + 实时累计 Feature**：各档位分支下的规则集比较单笔金额、Client 1 天 / 30 天放行金额累计，超限拒绝；不动硬限额（全局版本，不能按分支选）。局限：累计基于评估事件而非结算，阶段 2 核实精度 |
| Q-4 | 额度在哪个事件点生效 | 两个事件点都按档位分支：结账点做"无档位 / RESTRICTED 拒绝 + 单笔上限"，支付尝试点做单笔 / 日 / 月额度（实际扣款点） |
| Q-5 | 审批分级怎么落到权限（Risk 不重检权限，路由只绑一个权限） | 新增四个权限：读、申请、审批、高级审批；两个审批接口分别绑定"审批"与"高级审批"，risk-decision 按 CRR 校验请求走的是哪个接口 |
| Q-6 | 观察期"笔数"怎么数 | 本期用 Risk 自有评估事件：进入观察期后 `PAYMENT_ATTEMPT_RISK` 放行次数（近似）；精确的支付成功计数需消费 `PaymentSucceeded`，另立项 |
| Q-7 | 观察期 "天数或笔数" 的含义 | 任一达到即提示到期；升档仍人工确认（风控可视情况等两者都满足） |
| Q-8 | `ClientLimitTierChanged` 是否本期发布 | 不发：目前无消费方（门户通知、看板未建），流程用 `GOLIVE_LIMIT_TIER` 查询；有消费方时再发 |
| Q-9 | RESTRICTED 的效果 | 默认拒绝新交易；如需"降额继续"由风控在流程中给 RESTRICTED 分支配规则 |
| Q-10 | 存量 Client 迁移 | 发布时只读导出 commercial 中有 ENABLED 开通项的 Client，执行迁移设为 STANDARD（来源 MIGRATION，批准人记发布执行人）；无开通项的 Client 不设档位，将来走正常流程。必须在激活带档位分支的发布之前完成 |
| Q-11 | 发布安全（开关） | 不加代码开关：档位分支只存在于决策流配置，**激活带分支的发布就是开关**；回退 = 激活上一发布。档位表与名单可先上线、空跑 |
| Q-12 | 复核清单系统项范围 | 本期系统带出：资格状态与 CRR（compliance）、有效承保（risk）、合同与支付方式开通项执行状态（commercial）；渠道报备、技术接入、生产验证、未结事项为人工勾选，后续 Owner 查询就绪再改为系统项 |
| Q-13 | 额度币种 | ~~本期 USD~~ **阶段 2 定稿（S-05 方案 A）**：每个档位按币种分别配置额度，不换汇、分币种累计；未配置币种按 B11 处理；统一 USD 额度待有汇率来源后另立需求 |
| Q-14 | 页面原型 | 阶段 3 在 `operations-console.pen` 出图：档位列表、Client 档位页（VERIFICATION 申请上线复核、OBSERVATION 进度与到期、待审批审批人视角、RESTRICTED、历史） |

## 10. 阶段安排

默认八阶段。阶段 2 需核实：

- 决策流连线条件对名单命中 Feature 的支持与写法；
- 实时累计 Feature 的窗口、金额与币种语义，以及评估事件写入时机（放行 / 拒绝）；
- 两个事件点现有生效发布的结构；
- 名单接口的加校验点；
- REQ-03 承保批准链路的挂点；
- ops-bff 可复用的 compliance / commercial / risk 读取；
- Platform 注册块号（143 之后）；
- mor-test 存量迁移名单。

阶段 3 页面原型须先确认。SQL 应用、部署、提交、决策流配置激活分别授权。
