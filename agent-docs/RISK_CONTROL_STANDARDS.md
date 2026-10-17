# 风控标准目录

> 状态：分类框架经用户 2026-10-14 确认；各条目按下文“状态”列生效。实现、验证与部署状态只见 [CURRENT_IMPLEMENTATION](./CURRENT_IMPLEMENTATION.md)。

本文定义全系统风控共用的标准词汇：风控点、风险主体、风险事件、风险特征、处置结果、原因码、风险等级与案件。新增风控能力应组合本文已有定义；需要新条目时，由对应需求在阶段 3 提出并在确认后写回本文。表结构、字段与状态机仍由 [Risk 数据库设计](./database/RISK_COMPLIANCE_DATABASE.md)拥有，本文只拥有分类、编码与语义。

业务背景：平台是 Merchant of Record，拒付损失与销售合规最终由平台承担，因此同时防范**消费者侧**（盗卡、卡测试、友好欺诈）与 **Client 侧**（虚假准入、交易洗钱、bust-out 跑路）两类风险。首批 Client 为游戏行业。风控领域固定三个：

| 领域 | 含义 |
|---|---|
| FRAUD | 反欺诈：消费者或 Client 以欺骗手段造成资金损失 |
| AML | 反洗钱与制裁合规：资金来源、去向、主体身份与司法辖区 |
| CREDIT | 信用与敞口：Client 无法承担拒付、退款或追偿造成的平台损失 |

条目状态：**已确认**＝已有实现或已确认需求在使用；**候选**＝初版清单，由具体需求确认后改为已确认，确认前不得作为实现依据。

## 1. 风控点

风控点是“在某个业务时刻做一次风险决定”。每个风控点必须定义：编码、领域、触发时机、同步/异步、输入合同、允许结果、默认结果、失败策略、超时、调用方与接口。

“状态”指风控点本身；“接口状态”指下表接口路径：**已确认**＝已有确认的接口合同；**候选**＝本文给出的预期路径，由对应需求阶段 3 确认或改定后写回。是否已实现、部署只见 CURRENT_IMPLEMENTATION。

### 1.1 同步决策点

同步风控点共用统一评估接口 `POST /risk-decisions/v1/evaluations`（[R7](./decisions/2026-10-14-risk-payment-fraud-phase1.md)），请求以 `pointCode + contractVersion` 选择输入合同，`businessKey` 为该点的业务身份；新增同步风控点只登记输入合同、附加输出与评估器，不新增接口。

| 编码 | 领域 | 触发时机 | 调用方 | 失败策略 | 接口 | 业务身份 | 状态 | 接口状态 |
|---|---|---|---|---|---|---|---|---|
| CHECKOUT_PREPARE（RCO-I03） | FRAUD | Commerce 支付准备，正式订单落库前 | Commerce | FAIL_CLOSED | `POST /risk-decisions/v1/evaluations` | 预分配 `order_id` | 已确认 | 已实现（mor-test 已部署）：输入合同 `ORDER_PREPARATION` v5，v4 只读，见[结账点运行期评估](../requirements/20261016/risk-结账点运行期评估/design.md)；写入累计事件，开放 Client / 运营主体的实时累计与名单变量，见[结账点实时累计](../requirements/20261017/risk-结账点实时累计/design.md) |
| PAYMENT_ATTEMPT_RISK（RCO-I04） | FRAUD | Payment 每个支付尝试提交渠道前；同步，500 毫秒 | Payment | FAIL_CLOSED | `POST /risk-decisions/v1/evaluations` | `payment_attempt_id` | 已确认 | 已确认 |
| PAYOUT_CREATION（RCO-I09） | CREDIT、AML | 创建手工出款 | Finance | FAIL_CLOSED | 现为 `POST /manual-payout-eligibility/v1/evaluations`；运行期实现时迁入统一接口并废弃该专用接口 | `payout_id` | 已确认（设计） | 已确认（专用接口）；迁移后路径候选 |
| PAYOUT_FINAL_APPROVAL（RCO-I09） | CREDIT、AML | 出款最终 Finance 审批 | Finance | FAIL_CLOSED | 同上 | `payout_id` + 版本 | 已确认（设计） | 同上 |
| PAYOUT_EXECUTION_CREATION（RCO-I09） | CREDIT、AML | 创建出款执行 | Finance | FAIL_CLOSED | 同上 | `execution_id` | 已确认（设计） | 同上 |
| PAYOUT_EXTERNAL_SUBMISSION（RCO-I09） | CREDIT、AML | 每次实际提交银行 | Finance | FAIL_CLOSED | 同上 | `execution_id` + 版本 | 已确认（设计） | 同上 |
| REFUND_REQUEST | FRAUD、AML | Client 或运营发起退款，Commerce 调 Payment 退款前 | Commerce | 待定 | `POST /risk-decisions/v1/evaluations` | `refund_request_id` | 候选 | 候选 |

需求目录 RCO-I02“检查业务是否允许交易”不单设风控点，建议并入 CHECKOUT_PREPARE（候选）。

### 1.2 异步审核（尽调案件）

以案件为中心、最终由人决定，规则结果只作建议；正式结论是不可变的资格决定（`qualification_decision`），不走统一评估接口。

| 编码 / 资格类型 | 领域 | 触发时机 | 调用方 | 失败策略 | 接口 | 状态 | 接口状态 |
|---|---|---|---|---|---|---|---|
| CLIENT_ONBOARDING：CLIENT + INDIVIDUAL_KYC / ORGANIZATION_KYB，关联人 RELATED_PERSON_KYC（RCO-I11、RCO-I01） | AML | Client 提交入驻资料 | CCT、运营后台 | 不通过 | 提交 `POST /client-onboarding-review/v1/submissions`；补充分类 `POST /client-onboarding-review/v1/cases/{caseId}/classifications`；结果 `GET /client-onboarding-review/v1/applications/{applicationId}/result`；材料要求 `GET /internal/due-diligence/material-requirements`；运营审核 `/client-onboarding-admin/v1/cases…`（列表、详情、证据审核、退回、通过、拒绝） | 已确认 | 已确认 |
| OPERATING_ENTITY + ENTITY_UNDERWRITING（RCO-I11） | AML、CREDIT | Operating Entity 提交申请 | CCT | 不通过 | `POST /due-diligence/v1/cases`（`sourceNamespace=CCT_OPERATING_ENTITY_APPLICATION`）；结果 `GET /due-diligence/v1/applications/{applicationId}/result` | 已确认（设计） | 候选 |
| LOCAL_PARTNER + PARTNER_DUE_DILIGENCE（RCO-I11） | AML | Local Partner 提交申请 | CCT | 不通过 | 同上（`sourceNamespace=CCT_LOCAL_PARTNER_APPLICATION`） | 已确认（设计） | 候选 |
| CLIENT_PROFILE_CHANGE | AML、CREDIT | Client 变更收款账户、受益人、网站或主营业务 | CCT | 变更不生效 | `POST /due-diligence/v1/cases`（来源为资料变更）；结果同上 | 候选 | 候选 |
| CONTINUOUS_REVIEW（周期复审） | AML | 资格按复审频率到期 | Risk 定时任务 | 资格到期按规则处理 | 无对外接口：定时任务创建复审案件，结果以资格事件发布 | 已确认（设计） | 候选 |

Client 入驻现用专用路径 `/client-onboarding-review/v1`；是否收拢到通用尽调入口 `/due-diligence/v1`，在 Operating Entity 或 Local Partner 尽调立项时决定。

### 1.3 事件驱动与批量

| 编码 | 领域 | 触发 | 失败策略 | 接口 | 状态 | 接口状态 |
|---|---|---|---|---|---|---|
| DISPUTE_LIABILITY（拒付责任判定） | CREDIT | Payment 发布的拒付开启、结果事件 | 规则建议或人工判定，二者择一 | 消费 MQ `DisputeOpened`、拒付结果事件；补交证据 `POST /disputes/v1/cases/{caseId}/evidence`（RCO-I07）；查询 `GET /disputes/v1/cases/{caseId}`（RCO-I08） | 已确认（设计）；编码候选 | 候选 |
| CLIENT_MONITORING | CREDIT、AML | 定时按 Client 汇总 | 不处置，下次重试 | Risk 定时任务；结果以风险等级变化事件发布；运营查询 `GET /client-risk-monitoring/v1/clients/{clientId}` | 候选 | 候选 |

授权结果、退款成功、拒付等**不是风控点**，不做决定，只作为风险事件（第 3 节）喂给特征与监控。

### 1.4 共用接口

| 用途 | 接口 | 接口状态 |
|---|---|---|
| 查询风险决定（RCO-I06） | `GET /risk-decisions/v1/evaluations/{evaluationId}`；按业务身份 `GET /risk-decisions/v1/evaluations?pointCode&businessKey`（[合同](../requirements/20261015/risk-评估结果查询与解释/design.md)） | 已确认 |
| 查询评估解释（RCO-I10） | `GET /risk-decisions/v1/evaluations/{evaluationId}/explanation` | 已确认 |
| 规则、流程、变量、发布、名单、字典配置（RCO-I12） | `/decision-rule-sets/v1`、`/decision-flows/v1`、`/decision-variables/v1`、`/decision-releases/v1`、`/risk-lists/v1`、`/decision-dictionaries/v1`，按 `pointCode` 区分风控点 | 已确认 |

## 2. 风险主体

风险主体是计数、去重、名单与处置的对象。每类主体必须定义规范化规则、敏感级别与展示脱敏方式；同一规则同时用于名单成员与事件主体，保证两者可以相互命中。

| 主体 | 规范化 | 敏感与展示 | 状态 |
|---|---|---|---|
| CLIENT | 正整数 Client ID | 原样 | 已确认 |
| OPERATING_ENTITY | 5 位 entity_no | 原样 | 已确认 |
| CONSUMER | `clientId:consumerReference`；游戏行业即玩家账号 | 前 6 位 + 掩码 | 已确认 |
| CARD_FINGERPRINT | Card 生成的跨交易稳定指纹 | 前 6 位 + 掩码 | 已确认 |
| CARD_BIN | 6～8 位数字 | 原样 | 已确认 |
| IP_ADDRESS | IPv4 原值；IPv6 取 /64 前缀；不做 DNS 解析 | 保留前两段 | 已确认 |
| EMAIL_TOKEN / PHONE_TOKEN | Commerce 稳定 Vault Value Token | 不展示 | 已确认（一期不写入） |
| DEVICE_ID | 待设备指纹来源确定 | 待定 | 候选 |
| BANK_ACCOUNT | 出款收款账户的稳定 Token | 不展示原值 | 候选 |
| PERSON | Client 受益人、法人，用于制裁筛查 | 不展示原值 | 候选 |
| PARTNER | Local Partner ID | 原样 | 候选 |

## 3. 风险事件

风险事件是全部累计特征与监控的唯一数据来源，写入 Risk 本地 `t_velocity_event` 及其主体。事件编码归属风控点，变量只能统计本点事件，可选主体按风控点声明。每个事件统一包含：事件编码、来源系统、来源 ID 与版本、发生时间、金额与币种（可空）、关联主体列表。事件只追加、不修改；同一来源 ID + 事件编码只记一次。

| 事件 | 来源 | 发生时机 | 用途 | 状态 |
|---|---|---|---|---|
| PAYMENT_ATTEMPT_ASSESSED | Risk | 支付尝试评估成功 | 评估次数、金额累计 | 已确认 |
| PAYMENT_ATTEMPT_REJECTED | Risk | 评估结果为 REVIEW 或 DENY | 风控拦截次数 | 已确认 |
| CHECKOUT_ASSESSED | Risk | 结账评估成功（主体 Client、运营主体；金额为税前净额） | 结账评估次数、净额累计 | 已确认 |
| CHECKOUT_REJECTED | Risk | 结账评估结果为 REVIEW 或 DENY | 结账拦截次数 | 已确认 |
| PAYMENT_AUTHORIZED | Payment | 渠道授权（或直接扣款）成功 | 成交、授权率 | 候选（授权结果回流需求，暂缓） |
| PAYMENT_DECLINED | Payment | 渠道明确拒绝授权 | 卡测试、授权拒绝率 | 候选（授权结果回流需求，暂缓） |
| PAYMENT_CAPTURED | Payment | 请款成功 | 实际成交额 | 候选 |
| REFUND_SUCCEEDED | Payment | 渠道退款成功 | 退款率；AML 充值后退款 | 候选 |
| DISPUTE_OPENED / DISPUTE_LOST | Risk | 拒付开立、判负 | 拒付率 | 候选 |
| PAYOUT_PAID | Finance | 出款完成 | 资金流出速度 | 候选 |
| CLIENT_BANK_ACCOUNT_CHANGED | CCT | 收款账户变更生效 | 变更后首次出款重点审核 | 候选 |

**渠道结果未知（UNKNOWN）不产生事件**，待 Payment 恢复为确定结果后再产生对应事件。

### 3.1 授权拒绝分类

各渠道原始拒绝码必须先映射到以下标准分类，再进入风险事件；原始码只保留在 Payment，Risk 不按渠道原始码写规则。

状态：候选，由[授权结果回流](../requirements/20261014/risk-授权结果回流/requirement.md)需求确认（该需求暂缓）。

| 分类 | 含义 | 计入风险累计 |
|---|---|---|
| INSUFFICIENT_FUNDS | 余额或额度不足 | 是 |
| INVALID_CARD_DATA | 卡号、有效期、CVV 等卡数据错误 | 是（卡测试主要信号） |
| LOST_STOLEN | 卡已挂失、被盗或冻结 | 是（卡测试主要信号） |
| SUSPECTED_FRAUD | 发卡行或渠道怀疑欺诈 | 是 |
| DO_NOT_HONOR | 发卡行笼统拒绝、未给具体原因 | 是 |
| AUTHENTICATION_FAILED | 3DS 等持卡人认证失败 | 是 |
| TECHNICAL | 渠道或网络技术原因，与持卡人无关 | 否 |
| OTHER | 未映射的原始码 | 是 |

## 4. 风险特征

特征是规则可以引用的计算值，由变量创建时冻结参数的 Feature Version 产生（见[支付反欺诈一期设计](../requirements/20261014/risk-支付反欺诈一期/design.md)第 4 节）。

**命名：**`{主体}_{事件}_{算法}_{窗口}`，例如 `CARD_DECLINED_COUNT_10M`、`CLIENT_CHARGEBACK_RATE_30D`。变量编码遵循该格式，便于运营按名称理解口径。

| 维度 | 取值 | 状态 |
|---|---|---|
| 窗口 | 10 分钟、1 小时、1 天、7 天 | 已确认 |
| 窗口 | 30 天、90 天（比率类监控） | 候选 |
| 算法 | COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT、SECONDS_SINCE_LAST | 已确认 |
| 算法 | RATE（两类事件计数之比，如拒绝/尝试、拒付/成交） | 候选 |
| 窗口口径 | [评估时间 − 窗口, 评估时间)；金额只合计同币种 | 已确认 |
| 低基数主体限制 | Client、运营主体：次数、最近金额、距上次秒数窗口最长 1 天，金额合计最长 1 小时，不支持去重数（单个 Feature 200 ms 上限；见[结账点实时累计](../requirements/20261017/risk-结账点实时累计/technique.md)第 4 节）；更长窗口待小时预聚合 | 已确认 |

游戏行业首批特征库（候选，作为模板随需求确认）：

| 特征 | 识别的风险 |
|---|---|
| 同卡 10 分钟授权拒绝次数 | 卡测试 |
| 同 IP 1 小时不同卡数 | 卡测试、批量盗卡 |
| 同玩家 1 天不同卡数 | 盗卡、多卡充值洗钱 |
| 同卡 1 天不同玩家数 | 卡被转售、代充 |
| 同玩家 1 天充值金额合计 | 大额异常、限额 |
| Client 30 天拒付率、退款率、授权拒绝率 | Client 质量下降、bust-out 前兆 |
| Client 当日交易量 / 30 天日均 | 交易量突增 |

## 5. 处置结果

交易级结果由风控点当场返回；主体级动作对后续交易持续生效。二者分开定义，不混用。

| 层级 | 结果 | 状态 |
|---|---|---|
| 交易级 | ALLOW、REVIEW、DENY；技术终态 FAILED、TIMED_OUT | 已确认 |
| 交易级 | 附加输出：风控点在合同中声明，由放行结束节点配置；支付点声明认证要求 NOT_REQUIRED、THREE_DS_REQUIRED（[R8](./decisions/2026-10-14-risk-payment-fraud-phase1.md)） | 已确认 |
| 主体级 | 加入名单 | 已确认（人工）；规则自动加入为候选 |
| 主体级 | 冷却一段时间 | 候选 |
| 主体级 | 限制出款（Settlement Control） | 已确认（设计） |
| 主体级 | 提高保证金或准备金 | 候选 |
| 主体级 | 暂停收款 | 候选 |
| 主体级 | 创建 AML 告警案件 | 候选 |

## 6. 原因码

编码为 `领域_类别_细项`，全大写下划线分隔，符合现有拒绝原因字典的编码规则（`[A-Z][A-Z0-9_]{0,63}`），例如 `FRAUD_VELOCITY_CARD_DECLINES`、`FRAUD_LIST_IP`、`AML_SANCTIONS_HIT`、`AML_PATTERN_TOPUP_REFUND`、`CREDIT_CHARGEBACK_RATIO`。

- 原因码是内部排查与统计口径；对消费者与 Client 只返回笼统的对外文案，避免暴露规则。
- 沿用现有拒绝原因字典承载，新增编码按本格式命名；已有编码不改名。
- 状态：格式为候选，随首个使用它的需求确认。

## 7. 风险等级与案件

| 项 | 定义 | 状态 |
|---|---|---|
| Client 风险等级 | LOW、MEDIUM、HIGH；入驻初评，监控动态调整；决定准备金比例、结算延迟天数与复审频率（反洗钱的基于风险方法） | 候选 |
| AML 告警案件 | 告警类型、严重度、处理时限、处置结论（误报、升级、上报可疑交易）与证据清单 | 候选 |

游戏行业 AML 关注模式（候选，作为监控规则模板）：充值后短时间退款；拆分为多笔低于阈值的小额；一个玩家大量卡充值或大量玩家共用一张卡（游戏币、礼品卡转售）；Client 交易量与申报经营规模不符；出款收款方位于高风险司法辖区；制裁筛查覆盖 Client、受益人、收款方及消费者所在国家。

## 8. 维护规则

- 新增或改变条目：在对应需求阶段 3 提出，用户确认后由该需求同步本文并把状态改为已确认。
- 本文不记录实现进度、测试数量或部署状态。
- 已确认条目的编码不重命名；废弃时标注取代关系，不删除。
