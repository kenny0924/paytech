# 结账点实时累计

- 日期：2026-10-17
- 状态：**阶段 1 已确认**（2026-10-17，Q1～Q7 按建议）；阶段 2 已确认（[technique.md](./technique.md)，D1～D4 按建议）；阶段 3～7 已确认（[design.md](./design.md)；mor-test 已部署）；阶段 8 已本地提交，未推送
- 主入口 / Owner：Risk & Compliance（`CHECKOUT_PREPARE` 风控点的累计事件与实时累计、名单变量）；关联 Operations Web（变量创建页）
- 依据：[结账点运行期评估](../../20261016/risk-结账点运行期评估/requirement.md)第 4 节不做项“结账评估的累计事件与实时累计变量”；[风控标准目录](../../../agent-docs/RISK_CONTROL_STANDARDS.md)第 1.1 节；[支付反欺诈一期](../../20261014/risk-支付反欺诈一期/design.md)的累计机制
- 技术方案：[technique.md](./technique.md)；方案设计：[design.md](./design.md)；确认记录：[delivery.md](./delivery.md)

## 1. 背景与目标

结账风控点已上线（本机与 mor-test），但规则只能看**单笔订单**的输入：国家、金额、行业等。按需求约定，结账评估不写累计事件，也不能创建实时累计、名单变量，所以识别不了“同一 Client 短时间内大量下单”“某运营主体当日成交额异常”“已封禁的 Client 继续下单”这类批量风险。

**目标：**结账评估写入累计事件，开放结账点的实时累计变量与名单命中变量，运营可以在结账规则中直接使用，例如：

- “同一 Client 1 小时内结账评估次数 > 50 → 审核”；
- “同一运营主体 24 小时内结账净额合计 > 100000 USD → 拒绝”；
- “同一 Client 10 分钟内被拦截次数 ≥ 3 → 拒绝”；
- “Client 在封禁名单中 → 拒绝”。

## 2. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 风控运营 | 在结账点创建实时累计变量（选事件、主体、指标、窗口），写进结账规则，经执行发布激活 | 新的结账评估按累计值判定；评估解释中显示该变量取值 |
| 风控运营 | 创建 Client / 运营主体名单命中变量，用于结账规则 | 名单中的 Client 下单被拦截 |
| 风控运营 | 修改累计阈值前做影子运行 | 以原评估时间为锚点回放，看到结论变化 |
| Commerce（系统） | 创建结账 | 无改动；评估时延仍在 1 秒读超时内 |

## 3. 本期范围（草案）

1. **结账累计事件：**结账正式评估成功时（技术状态 SUCCEEDED）在评估同一事务中写累计事件；按支付做法分两类：`CHECKOUT_ASSESSED`（全部结论）与 `CHECKOUT_REJECTED`（非 ALLOW），主体为 Client 与运营主体，金额为税前净额、币种为订单币种。重放不重复写；影子运行不写。
2. **开放结账的实时累计变量：**指标沿用现有 5 种（次数、同币种金额合计、去重数、最近金额、距上次秒数），窗口沿用滑动窗口（10 分钟、1 小时、24 小时、7 天）。
3. **开放结账的名单命中变量：**主体为 Client、运营主体。
4. **按风控点限定可选项：**事件编码归属风控点（结账变量只能选结账事件，支付同理）；可选主体按风控点声明（结账不出现卡指纹、IP 等无值主体）。
5. **Operations Web：**变量创建页的实时累计、名单变量不再写死支付点，按所选风控点从 Risk 创作上下文读取事件、主体、币种变量。
6. **文档：**风控标准目录中结账点状态更新为“已实现，mor-test 已部署”（顺带修正上一需求遗留的“待部署”）。

## 4. 不做项

- 日历日窗口（“今日”）与时区口径：本期用滑动 24 小时替代。
- Risk 放行后 Commerce 失败（税、文件、取消）时冲销事件：累计口径为“结账评估次数/金额”，不是“成功订单”。
- 新增主体类型（Local Partner、消费者国家、行业）：国家可用输入变量 + 集合规则表达。
- 结账规则读取支付事件（跨风控点累计）。
- 累计事件的保留期与清理任务、预聚合计数（见风险，记为后续）。
- Commerce 改动。

## 5. 已确认事实

| 事实 | 依据 |
|---|---|
| 累计读取已通用：Feature 计算接收 `RuntimeRiskInput`（主体键、金额、币种），按 `[评估时间 − 窗口, 评估时间)` 直接查库，不用 Redis | `FeatureValueReader`、`VelocityEventSubjectMapper.xml` |
| 写入只有支付：事件编码只有 `PAYMENT_ATTEMPT_ASSESSED/REJECTED`，事件构造在 `PaymentRiskFacts`（来源类型 `TRANSACTION_RISK_ASSESSMENT`），与评估同一事务 | `VelocityEventCode`、`PaymentRiskFacts:77-84,136-159`、`PaymentRiskAssessmentServiceImpl:35-43` |
| 结账门禁：描述符 `featureVariablesSupported=false`；结账输入只能提供 Client、运营主体两个主体 | `DecisionPointAuthoringDescriptor:57`、`CheckoutRiskInput:90-96` |
| 事件编码、可选主体目前是全局常量，没有风控点维度；结账变量若放开会能选支付事件和卡指纹等无值主体 | `FeatureHandlers:37-45` |
| “包含本次”只认 `PAYMENT_ATTEMPT_ASSESSED`（后端与 Web 均写死） | `FeatureHandlers:71-73`、`riskVariable.ts:53` |
| Web 变量页写死支付点、支付币种变量、支付事件，默认主体为卡指纹 | `riskVariable.ts:42-53`、`FeatureVariableFields.tsx:8` |
| 影子回放不写事件，以原评估时间为锚点 | `ShadowRunHandler:50,146` |
| 单个 Feature 计算超时 200 ms，超时记 `FEATURE_ERROR` | `FeatureHandlers:34` |

## 6. 前提与依赖

- 改 Risk、Operations BFF 与 Operations Web（阶段 2 更正：BFF 严格校验并转换变量创作上下文，需同批改动与发布）；Commerce 不改。
- 本机与 mor-test 结账点都已有生效的默认放行基线，可直接验证。
- mor-test 没有 Commerce，test 验收用直接调用 Risk 评估接口的合成订单。

## 7. 验收标准（草案）

| 编号 | 标准 |
|---|---|
| AC-1 | 结账正式评估 SUCCEEDED 时写入 `CHECKOUT_ASSESSED`，非 ALLOW 另写 `CHECKOUT_REJECTED`，主体为 Client 与运营主体；FAILED/TIMED_OUT、重放、影子不写 |
| AC-2 | 可在结账点创建实时累计变量与 Client/运营主体名单变量，经执行发布后结账评估按累计值、名单判定；解释中显示取值 |
| AC-3 | 结账变量只能选结账事件与结账可用主体；支付变量只能选支付事件（现有支付变量不受影响） |
| AC-4 | 影子运行回放结账评估时累计值按原评估时间计算 |
| AC-5 | Web 变量创建页对结账点显示结账事件、主体与币种变量；支付点行为不变 |
| AC-6 | 支付评估、累计与现有变量回归通过；结账评估时延在本机与 test 抽样中不明显增加 |

## 8. 待决问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 哪些结论计入 | 与支付一致：`CHECKOUT_ASSESSED` 计全部结论，`CHECKOUT_REJECTED` 计审核与拒绝；不另设“仅放行”事件 |
| Q2 | 主体范围 | Client、运营主体两个；Local Partner、国家等新主体不做 |
| Q3 | 是否需要“今日”日历窗口 | 不做，用滑动 24 小时 |
| Q4 | 结账规则能否读支付事件 | 不能；事件编码归属风控点，本期两点各读各的 |
| Q5 | Commerce 在 Risk 放行后失败的订单 | 不冲销；变量名称与说明写明是“结账评估”口径 |
| Q6 | 事件保留与性能 | 本期不做清理与预聚合；阶段 2 在本机造量测 7 天窗口查询耗时，若接近 200 ms 再定是否纳入索引或预聚合 |
| Q7 | Operations Web 是否纳入 | 纳入（变量页按风控点读取上下文）；部署 test 与否在阶段 6 定 |

## 9. 阶段安排

默认 8 阶段。阶段 2 核对 Risk 写入路径、门禁与 Web 变量页；阶段 6/7 部署 Risk（及按 Q7 决定的 Web）到 mor-test。
