# OE 与 Client 属性及模型边界梳理

> **2026-09-11 实施范围更新：** 用户已授权 CCT、Commerce、Payment、Finance 的本期模型调整，并明确 PDManer 一起改；Risk 暂缓。前文各阶段“不改 PDManer/待进入阶段4”仅为当时记录。当前模型、SQL 工件和未迁移边界见 [本期模型实施记录](./model-implementation.md)。Java 业务实现不计入本次模型完成声明。

本文为第八版需求评审稿：沿用准确 Client/OE 合同下管理定价的方向，按用户要求将 Agreement Party 与最终签署结果保存在一张表，按合同/Party/角色唯一，不单设角色子表。同一合同、同一个法律 Party 的一次签署覆盖多个角色，各角色行保存一致的最终签署结果，由应用事务保证整体写入。最新 D1–D3 已确认：保留 UTC/Cutoff 两列、初期 23:59:59；支付准备固定合同/条款版本，完整支付事实齐备后解析准确 Price；创建只返回基础金额，准备后确定税额/总额。确认与交付记录见 [delivery.md](./delivery.md)，属性清单和概念模型见 [technique.md](./technique.md)。本文不取代现行 CCT 模型或表示工件已迁移。

用户已同意以本版已确认部分为后续基线，阶段 2 的现状差异与影响分析见 technique.md 第 11 节；随后已获准进入阶段 3。第 12–17 节及 [阶段 3 合同设计](./stage3-contracts.md)组成当前详细方案；三项业务答复已确认，新增的字段、接口与恢复方案待整体审阅。进入阶段不代表接口、结算算法、历史兼容或实施方案全部通过评审。

## 1. 来源与目标

用户在讨论 Commerce 下单与 CCT 的 OE 分配后，要求重新整理：

1. OE 对象应该具备哪些属性，模型应如何组织；
2. Client 对象应该具备哪些属性，模型应如何组织。

前序讨论发现：现有模型可以保存多组 Client–OE 四要素适用关系，但没有独立的 OE 四要素准入范围。不能据此宣称已具备 OE 范围上限校验，也不能把 MID 技术能力当作 OE 业务准入依据。

本轮目标是交付可审阅的属性、基数、所有权与维护边界，为后续模型差异分析提供明确范围。

## 2. 本轮范围

- 区分 Party 法律身份、OE/Client 角色、业务资料、准入范围和双方授权关系。
- 列出 OE、Client 现有核心属性及需要通过关联取得的属性。
- 评审新增 OE 独立交易准入范围的必要性和使用约束。
- 说明国家、币种、Industry、MCC 的准确语义；区别 OE 经营国家、Party 注册国家和消费者国家。
- 用不可变、最多五字符的 entity_no 作为 OE 唯一主键及全部对内、对外引用，评审编号细则。
- 简化 Client–OE Affiliation，评审将优先级合并于关系；按要求取消 Assignment，说明下单解析及订单恢复的责任。
- 创建 Checkout 只做输入、Client 及候选可用性的基础预检，不绑定最终 OE；支付准备时重新完整验证、确定 OE 和交易上下文，并在消费者确认实际卖方/金额/条款之前完成相应固定。
- 本期不引入 Client 独立国家/币种准入范围或双方的范围例外；更细控制留待具体业务场景。
- 本期 OE 选择仅沿用 Affiliation 优先级与四要素、合同等既定验证；新增支付方式/卡特征参与 OE 候选选择、成本或利润动态排序均留待后续优化。Payment 在固定 OE 下的既有支付方式/MID 能力校验不受此延期影响。
- 评审 Client Agreement、Pricing Plan/Version/Price 的归属和支付方式相关的结算到期条款；允许具有明确用途、由父级派生并校验一致的身份引用冗余。
- 将 CCT 合同参与方与其单次最终签署结果合并到 agreement_party，复用原 Signatory/Acceptance 的证据职责；不扩展完整签署尝试流程，不涉及 Commerce 的消费者 Acceptance。

本轮形成新跨系统合同的设计草案，不变更正式 PDManer、生成器、SQL、Java、OpenAPI 或运行环境。设计评审不等于工件实施授权。

## 3. 建议评审基线

- 保留现有 Party/Profile 与角色分层；同一法律主体兼任 Client 和 OE 时复用同一 Party。
- 保留 OE 主档及独立来源关系，建议增加一对多的 OE 交易准入范围，每行一个准确四要素组合。
- OE 删除数字 operating_entity_id，使用不可变 entity_no 主键；使用创建时 OE 经营国家二位代码加三位数字，例如 AE001，编号不参与路由。OE 自身经营国家为单值；party_id 不得改绑到另一个法律卖方。
- 新接口删除 assignmentId，OE 引用使用字符串 entityNo，替换数字 operatingEntityId；不为兼容新交易继续返回两套身份。历史合同的只读兼容独立设计，不回写历史订单。
- 保留 Client 主档、版本化 Business Profile、多条 Classification 及唯一 Primary 的现行语义。
- Affiliation 不维护四要素，授权覆盖该 OE 当前及以后支持、且符合 Client 批准分类的范围；准入扩展不要求逐 Client 补授权。
- 本版将优先级放在 Affiliation，对该 Client 的所有范围统一生效，不再单设 Rule；同一 Client 的有效优先级建议唯一。
- 取消 client_entity_assignment；CCT 分别提供基础候选预检和支付准备时的完整只读解析，Commerce 保存最终选定 OE。同一次准备命令已有固定选择后，恢复及后续 Payment Attempt 不得切换主体。
- 候选按 Affiliation.priority_no 升序逐个完整验证，包括合同等必需条件；候选明确未通过则继续，返回第一个全部通过的 OE，全部未通过则拒绝。无法判断和整次交易拒绝的具体错误边界见技术稿，不将依赖异常伪装成候选不合格。
- OE Scope 经批准后才生效，同一 OE/准确组合的有效范围不得重叠冲突；扩展 Scope 不自动补齐合同或法律文件。
- 本次模型/配置调整不修改历史订单，不替换历史身份或重新计算其原始业务上下文；历史读取兼容由后续设计承接。
- 一份 Client Agreement 对应一个 Client 和一个 OE；同一 Client/OE 可保留多份历史或待生效合同，同一业务时点至多一份适用合同。每份合同管理一个 Client Pricing Plan，Plan 有多个版本，同一时点至多一个有效版本；不再要求每个 Client 全局唯一 Plan。
- Client 结算到期条款放到 commercial_term_price，随该 Price 的 Payment Method Profile 一起匹配，与其收费组件采用同一不可变价格事实。Finance 根据实际支付成功时间和准确条款计算并冻结资金可用时间；不把该配置解释为 Provider 实际到账日或自动银行打款时间。
- 结算保留 settlement_timezone 和 cutoff_second_of_day，初期固定 UTC/86399（23:59:59）。settlement_business_days 与 business_calendar_code 保持，按工作日和 FOLLOWING 计算，不能改成 N×24 小时；详细边界见阶段 3 合同第 5 节。
- 冗余字段按明确的查询、校验或追溯用途选择；Client/OE 等身份冗余必须与合同父链一致，不能作为另一套可独立修改的关系。具体保留列、索引和写入校验留待物理设计。
- agreement_party 按 agreement_id/party_id/party_role 唯一，保存封存资料版本及最终签署方式、签署人、签署时间和证据，不单设角色子表；party_role 不复用表示个人/机构的 party_type。同一合同/Party 的多个角色行使用同一封存资料版本，一次签署覆盖待签时冻结的全部角色并原子写入一致结果。身份、角色和资料版本在待签时冻结，签署结果在允许状态内一次性完整补齐后冻结。
- 合同参与方必须与 Agreement 的 Client/OE 归属一致。必签集合不能根据签署字段是否为空动态判断；并发签署、取消和重复回调须在同一合同的受控事务内验证，已取消合同不得被迟到回调恢复。
- Risk 拥有资格审核结论；Payment 拥有 MID 和渠道能力；Finance 拥有余额及计算结果；Platform 拥有人类账户、Tenant 和 API Credential。

## 4. 验收标准

| 编号 | 本轮评审成果应满足 |
|---|---|
| AC-01 | OE 与 Client 的身份、状态、业务资料、准入范围和外部关联有清楚的属性清单与所有者 |
| AC-02 | 法律名称、个人/机构类型、注册信息和地址等不在 Party、Client、OE 重复成为多个可写权威 |
| AC-03 | 明确多国家/币种/MCC 的基数；四要素按准确组合表达，不由四份列表自动产生笛卡尔积 |
| AC-04 | 明确 OE 范围是新增建议，现有 Affiliation 无法代替 OE 独立范围上限 |
| AC-05 | 说明 Client 多 Classification 与当前 Checkout 仅使用唯一 Primary 的区别 |
| AC-06 | 给出范围维护、依赖失效和新订单分配的示例，历史订单的冻结 OE 不被改写 |
| AC-07 | 不混淆业务属性、页面聚合字段与物理表列，不把 Risk/Payment/Finance/Platform 的事实复制进角色主表 |
| AC-08 | 列出尚待确认的问题及其影响；不把本稿标为正式模型或实现完成 |
| AC-09 | OE 只有一个不可变字符串身份，编号不复用；所有内部和外部 OE 引用使用 entity_no，不保留平行数字身份 |
| AC-10 | OE 新范围生效后，已有有效 Affiliation 自动覆盖符合 Client 分类的组合；本期无双方范围例外 |
| AC-11 | 简化图不包含 Rule/Assignment 表；说明 Affiliation 优先级、无匹配/歧义失败及 Commerce 恢复时不换 OE 的要求 |
| AC-12 | 优先级较高 OE 合同过期、较低 OE 全部通过时，返回后者；所有候选明确未通过时拒绝，不返回部分通过结果 |
| AC-13 | OE Scope 批准后生效且准确组合有效区间不重叠；OE 不可改绑其他 Party；本次模型/配置变更不改写历史订单 |
| AC-14 | 创建 Checkout 无基础合格候选则拒绝；有候选则可建立基础 Checkout，但不绑定最终 entityNo，也不将预检结果当成支付准备通过证明 |
| AC-15 | 支付准备使用该阶段的有效事实重新完整验证；预检后候选失效时可选其他完整合格候选，全部未通过时禁止进入支付；最终卖方/金额/条款对应实际确认内容 |
| AC-16 | 同一 Client 可在不同 OE 下拥有独立合同和有效价格版本；同一 Client/OE 的适用合同有效区间不冲突，同一 Plan 的适用版本有效区间不冲突 |
| AC-17 | 同一合同版本内，不同支付方式对应的 Price 可配置不同 T+N 和日历；费用与结算到期条款从同一命中 Price 取得，不在合同上维护竞争性默认值 |
| AC-18 | 冗余身份字段由权威父链派生并验证，非法 Client/OE/Agreement/Version 组合被拒绝；费率或结算到期条款变化创建新版本，不回写历史订单或已冻结的资金可用时间 |
| AC-19 | 明确时区/Cutoff 的保留选择及统一配置；保留工作日语义，成熟时刻规则覆盖 N=0、非工作日和跨日情形，且不得早于支付成功时间；不将字段待决标记误作可忽略的运行输入 |
| AC-20 | Agreement Party 身份与合同 Client/OE/Party Profile 一致；待签后不能改绑身份、角色或资料版本，签署结果仍允许在受控命令中一次性补齐 |
| AC-21 | 同一最终签署结果重复提交幂等返回，冲突结果不覆盖；签署字段按方式原子写入，证据属于同一合同且保留不可变文档引用 |
| AC-22 | 最后两个必签方并发完成与合同取消竞争不会丢失状态变化或恢复取消合同；全部必签方及文件条件满足后仅形成一次 SIGNED 事实 |
| AC-23 | 区分实际签署时间与结果登记时间、实际签署账号与登记操作者；同一合同/Party/角色不可重复，同一合同/Party 的多个角色行原子保存一致的最终签署结果，一次签署覆盖冻结角色集合，完成判定按必签 Party 去重 |
| AC-24 | 三列唯一键不代替合同角色的业务基数校验；签署引用允许在同一合同/Party 的角色行重复，不再采用单列全局唯一索引，跨合同/Party 不得冒用同一参与方级签署引用 |

## 5. 已收敛决定与实施前置

D1–D3 已确认，不再等待重复答复。编号、Scope 审批、单表共签、Client/Partner 共表方案见 technique.md 第 12–13 节；CCT/Payment 新合同、Commerce 恢复、Finance 成熟、历史兼容和验收见 [阶段 3 合同设计](./stage3-contracts.md)。新增技术方案等待整体评审，不代表已经实现。

- OE 是否必须 Organization Party、预计交易规模周期口径，本期不新增限制，保留现行语义，后续出现明确用例再决定。
- 原始 Provider 支付值到标准化事实的映射须有接入证据；没有证明的值域不能启用依赖它的 Profile。
- 历史数据量、旧身份映射、合同/签署归类及实际 DML 行数由实施前环境盘点确定；不猜测空库，也不提前执行迁移。
- 正式物理工件、PDManer/生成器差异及对应 SQL 属于后续获准模型切片；用户“不要改 PDManer”的约束继续有效。
- Client API 既有消费者必须适配创建时税额/总额可空，才可启用新创建行为；不新增 HTTP 版本掩盖兼容问题。

## 6. 权威与状态入口

- [项目背景与术语](../../../agent-docs/PROJECT_CONTEXT.md)
- [CCT 业务模型](../../../agent-docs/database/CLIENT_CONTRACT_DATABASE.md)
- [CCT 物理决策](../../../paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md)
- [CCT PDManer](../../../paytech/client-master/docs/database/client-master.pdma.json)
- [Commerce 下单分类合同](../../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md)
- [CCT 定价目标与迁移边界](../../../agent-docs/decisions/2026-09-10-cct-payment-method-profile-pricing.md)
- [当前实现、验证及部署状态](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
