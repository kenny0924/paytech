# 客户上线（Client Go-live）：开通项门禁、限额档位与上线流程编排决定

- Decision status: Proposed（2026-10-28 用户在讨论中逐条确认 Q1–Q9、Q-1–Q-10 及同日复核调整；作为仓库决定仍待用户确认登记）
- Implementation status: Partially implemented（REQ-01 已实现并部署 mor-test、已本地提交；REQ-05 已实现并部署 mor-test、已本地提交 `af972805`；REQ-12、REQ-03、REQ-04 已实现并部署 mor-test、已本地提交，见第 5 节）
- Deployment status: REQ-01 deployed to mor-test（2026-10-28 20:09—20:33Z，见 CURRENT_IMPLEMENTATION）
- Decision date: 2026-10-28
- 详细设计：[design-discuss/client-activation/design.md](../../design-discuss/client-activation/design.md)（全流程 P0–P7、门禁、缺口）、[requirement-plan.md](../../design-discuss/client-activation/requirement-plan.md)（逐步设计、系统结合、需求拆分 REQ-00–19、里程碑 M1–M4）、[ops-line.md](../../design-discuss/client-activation/ops-line.md)（内部运营作业线路 T01–T28）、`client-activation.pen`（七张画板）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 决定

| # | 决定 |
|---|---|
| ① | **`t_client.status` 只表示合作关系是否有效**（PENDING → ACTIVE → SUSPENDED / TERMINATED）。**可收款 / 可出款由 commercial 合同开通项（`t_agreement_capability`）控制**：支付方式类开通项 ENABLED 即可收款，出款通道类开通项 ENABLED 即可出款。案件批准置 Client ACTIVE 的现有行为保留，其含义改为"资格通过、关系有效"。 |
| ② | **开通项拆为成员资格 `status`（ACTIVE / DISABLED，定价用）与执行状态 `execution_status`（PENDING_PREREQUISITES / ENABLED / SUSPENDED_BY_PREREQUISITE / SUSPENDED_MANUALLY，Checkout 用）**（REQ-01 阶段 2 方案 A：原一列两义导致价格激活与门禁循环依赖）；生效前由 commercial 在事务外核对**三项前置**：① Client 角色资格 APPROVED 且未逾期（复用已有 `QualificationEvaluationApi`）、② 合同与价格版本 ACTIVE（本库）、④ 该支付方式在该 OE 下有生效渠道身份（channel-connector 渠道身份就绪查询）。满足后短事务置 ENABLED 并发布 `AgreementCapabilityStatusChanged`；资格 / 渠道事件到达时重查，失效则置 SUSPENDED。**承保 / 限额、收款账户、结算控制、保证金不进门禁**，由各 Owner 运行期检查（Risk 限额档位；Manual Payout 四个检查点）。存量已 ENABLED 的开通项不回溯。 |
| ③ | **放量由 risk-decision 限额档位控制**：VERIFICATION（只够生产验证）→ OBSERVATION → STANDARD，任意 → RESTRICTED。实现为 `t_risk_list.list_purpose = TIER` 的档位名单 + 决策流按名单 Feature 分支绑不同限额版本；不在任何档位名单 = 拒绝。开通项首次 ENABLED 时 Risk 把 Client 加入 VERIFICATION；上线复核在 Risk 档位变更页四眼批准后切 OBSERVATION；观察期满切 STANDARD。不新建档位表、不改限额绑定解析。 |
| ④ | **Platform workflow 承载"客户上线"流程，但不是执行开关**（红线二）。节点完成以 `DATA_QUERY` 只读查询 Owner 事实判定，运营在 Owner 页面做完即自动推进；流程只回答"谁做、在哪做、做什么、卡在谁"。 |
| ⑤ | **流程只由内部运营在页面按钮发起**（入驻审核列表 / 工作台 / Client 详情），按模板编码 `CLIENT_GO_LIVE` + 业务关联键 `CLIENT:{clientId}` 防重；Client / PayFac 的提交不触发任何内部流程；发起时间不限，已满足的节点发起后自动完成。 |
| ⑥ | **每个 Client 一位上线负责人**（实例级变量，发起时指定）：所有"等 Client"节点的跟进人、Client 的唯一人工联系人、上线复核提交人。四眼拆成"处理 + 复核"两张任务，复核任务候选人排除前序任务执行人。 |
| ⑦ | **完成条件查询的 SQL 模板由 Owner 交付与维护**（与其状态机同步变更），data-query 只登记与执行；Platform 不自行编写或修改这些模板。 |
| ⑧ | **收款上线与出款就绪是同一实例的两个出口**：出款账户、保证金不阻塞收款上线（Risk 可按 Client 标记保证金阻塞收款，默认不阻塞）。 |
| ⑨ | **首期入驻审核全部由平台运营代操作**；入驻码只发给 AFFILIATED PayFac 与 C3 直连客户；Partner 门户 PayFac 自审另立项。M1 覆盖 C1 / C3，C2 子商户登记在 M3。 |
| ⑩ | **C2 子商户对外报送在合同 ACTIVE 之后**（收单行 / 卡组织要求已签署的子商户协议）；签署前只做预填与核对。C3 MID 录入、C1 MCC 核对可与签署并行。 |
| ⑪ | **M1 的 workflow 只补最小集**：按钮发起防重、`DATA_QUERY` 完成条件与输出变量、等待节点与跟进人、队列认领与转交、四眼排除、交接备注。粘性分配、认领上限、不在岗、沟通记录、SLA 暂停与运营工作日历放 M2。 |
| 已定项 | 其余 Q2–Q8（BFF 实时聚合"还差什么"、限额归 Risk、保证金默认只阻塞出款、子商户与高风险登记事实归 channel-connector、生产验证必选、Client 沙箱另立项、观察期默认 30 天或前 N 笔）与 Q-1–Q-8（实例按 Client + 后续"开通变更"实例、自动承保阈值配置化、复核人按 CRR 分档、生产验证首期不等 T+1 对账、存量不补建流程）见 requirement-plan.md 第 15 节。 |

## 2. 取代范围（定稿时生效）

- `design-discuss/client-onboarding/design.md` 6.1 节"案件批准即可交易"的语义：Client ACTIVE 不再等于可交易。
- `design-discuss/commercial-capability-pricing/design.md` 中开通项"创建即 ENABLED"的描述：改为经门禁生效。
- `design-discuss/workflow/design.md` 中 `DATA_QUERY`"本期只预留"：由本决定 ④ 启用。
- `AGENTS.md` 的 Platform data-query 路由行不变（只读、不驱动业务执行）；本决定 ④⑦ 是其既定用途"流程验证"的具体化。

## 3. 契约附件（REQ-00）

跨需求共用、各车道并行实现时不再互相等待。字段命名在各需求阶段 3 按 DEVELOPMENT_CONVENTIONS 定稿；这里定语义与形状。

### 3.1 事件

| 事件 | 发布方 | 载荷（最小） | 消费方 | 幂等键 |
|---|---|---|---|---|
| `AgreementCapabilityStatusChanged` | commercial | 每个合同 BCR Root 版本一条：`agreementId, clientId, changeVersion, occurredAt, changes[{agreementCapabilityId, entityNo, capabilityCode, capabilityKind, fromExecutionStatus, toExecutionStatus, trigger}]`（REQ-01 阶段 3 修订：一次核对可迁移多个开通项） | risk-decision（首次 ENABLED → 加入 VERIFICATION 档位名单）、client-console / ops（通知、清单刷新） | `AGREEMENT:{agreementId}:AgreementCapabilityStatusChanged:{changeVersion}` |
| `ClientLimitTierChanged` | risk-decision | `clientId, fromTier, toTier, changedBy, approvedBy, reasonCode, occurredAt` | client-console（"已上线"通知）、ops（看板） | `clientId + toTier + occurredAt` |
| `QualificationChanged`（已有） | compliance | 现有载荷 | **commercial 新增消费**：重查 / SUSPENDED | 现有 |
| `ChannelIdentityChanged`（新，M1 可先不发） | channel-connector | `entityNo, paymentMethodCode?, midId?, changeKind` | commercial：重查 ④ | — |

事件只当"该重查了"的提醒，消费方不信任事件里的结论，重查以同步查询为准。

### 3.2 门禁同步就绪查询（只有两个）

| 前置 | 接口 | 提供方 | 现状 |
|---|---|---|---|
| ① 资格 | `POST /compliance/v1/qualifications/evaluations`，返回 `eligible / status / validFrom / validTo` | compliance | **已有**；commercial 的 `ComplianceQualificationClientImpl` 已调用 |
| ④ 渠道身份 | `GET /channel-connector/v1/channel-identities/readiness?entityNo&paymentMethodCode&currency&mccCode` → `{ ready, missing[] }`；判定：该 OE 下存在 ACTIVE `t_channel_mid`，其 ENABLED `t_mid_capability` 覆盖支付方式 × 币种且 `mcc_code` 包含 Client MCC，且 MID 在生效路由策略内。M3 并入子商户绑定 / 登记事实 | channel-connector | 新（REQ-02） |

### 3.3 流程完成条件查询（data-query 登记）

模板编码遵守 Platform data-query 规则 `[A-Z][A-Z0-9_]{1,63}`，统一前缀 `GOLIVE_`（REQ-01 阶段 6 发现原 `golive.*` 不合规后修订）。统一形状（REQ-01 阶段 3 细化为 SQL 行约定）：模板返回**单行**；列 `satisfied`（0/1）；`out_<name>` 列写入同名流程变量（供 EXCLUSIVE 网关分支）；`missing` 列为逗号分隔的原因码（可空，前端按文案键多语言）。无行视为未满足；查询失败或超时一律视为"尚未满足"，不报错。

| 查询编码 | 节点 | Owner（交付并维护模板） | outputs |
|---|---|---|---|
| `GOLIVE_CASE_APPROVED` | N1 | compliance | `crr, mcc, industryCode, ddTier` |
| `GOLIVE_UNDERWRITING_APPROVED` | N2 | risk-decision | `requireDeposit, depositBlocksCollection, highRiskMcc` |
| `GOLIVE_AGREEMENT_DRAFTED` | N3 | commercial | `agreementId, businessMode, entityNo, capabilities[], localMethods[]` |
| `GOLIVE_AGREEMENT_ACTIVE` | N4 | commercial | — |
| `GOLIVE_MID_READY` | N5c / N5d | channel-connector | `missingMethods[]` |
| `GOLIVE_SUB_MERCHANT_SUBMITTED / GOLIVE_SUB_MERCHANT_ACTIVE` | N5a / N5b | channel-connector（M3） | — |
| `GOLIVE_CHANNEL_REGISTRATION_APPROVED` | N5e / N5f | channel-connector（M3） | — |
| `GOLIVE_DEPOSIT_RECEIVED` | N6a | finance（M4） | — |
| `GOLIVE_BENEFICIARY_SUBMITTED / GOLIVE_BENEFICIARY_VERIFIED` | N6b / N6c | payout（M4） | — |
| `GOLIVE_INTEGRATION_READY` | N7 | Platform（access-control + delivery） | — |
| `GOLIVE_COLLECTION_CAPABILITIES_ENABLED` | N8 | commercial | `pendingCapabilities[]` |
| `GOLIVE_PRODUCTION_VERIFIED` | N9 | acquiring + payment + Platform delivery | `failedChecks[]` |
| `GOLIVE_LIMIT_TIER` | N10 / N11 | risk-decision | `tier, observationDue`（参数 `clientId`、`targetTier` = OBSERVATION / STANDARD；`missing` 为 `LIMIT_TIER_NOT_SET` / `LIMIT_TIER_BELOW_TARGET` / `LIMIT_TIER_RESTRICTED`；REQ-04 技术方案 8.5） |

> **补注（2026-10-29，REQ-05 阶段 2 S-05）**：流程变量没有列表类型。上表带 `[]` 的列表输出一律以 TEXT 返回、逗号分隔（如 REQ-01 `GOLIVE_AGREEMENT_DRAFTED` 的 `out_capability_codes`），排他网关用 `CONTAINS` 判断。`out_<name>` 的 `<name>` 由 DATA_QUERY 动作模板的输出声明给出，按不区分大小写匹配列名，流程变量名为 `<actionKey>.<name>`。

### 3.4 workflow 发起与节点约定

- 发起：`definitionCode + businessKey`，同一 `definitionCode + businessKey` 只允许一个 RUNNING 实例，重复发起返回已有实例（REQ-05 落库为 `t_workflow_instance(definition_id, business_key, active_slot)` 唯一索引）；发起人为登录运营账号（不新增系统身份）。
- 实例变量：`clientId, applicationId, caseId, payfacPartnerId, payfacType, jurisdiction, onboardingOwner`，加各节点 `outputs`。
- `DATA_QUERY` 动作：引用 data-query 模板编码；节点激活时查一次；之后由 XXL-Job 定时重查（默认 5 分钟）与卡片"立即检查"触发；`satisfied` 即完成动作并写 `outputs`。
- 等待节点（更正 2026-10-29，REQ-05 阶段 2 S-18：workflow 没有 WAITING 节点类型，`WAITING` 只是并行汇合的节点运行状态；"等待节点"表达为只挂 `DATA_QUERY` 动作、不配审批的 TASK 节点，该动作的执行人即跟进人）：无提交表单；跟进人 = 实例变量 `onboardingOwner` 或节点配置；催办经 Platform notification。
- 四眼：复核节点候选人排除指定前序节点的执行人（节点配置 `excludeExecutorsOf: [nodeKey]`）。

### 3.5 Owner 页面深链

节点 `PAGE_TASK` 路由模板 + 变量映射：`/client-onboarding/reviews/{caseId}`、`/risk/underwriting/new?clientId=`、`/commercial/agreements/new?clientId=`、`/channels/mids/new?entityNo=`、`/risk/limit-tiers/{clientId}`。新页面随各 Owner 需求交付；未交付前对应节点用 `MANUAL_CONFIRM` + 证据。

## 4. 需求拆分与里程碑

见 [requirement-plan.md 第 14 节](../../design-discuss/client-activation/requirement-plan.md#14-需求拆分与路线图2026-10-28-细化)：REQ-00–19，M1 可控上线（REQ-00–07、12）、M2 双方同屏（08–11）、M3 C2 与本地方式（13–15）、M4 出款就绪（16–18）、生产前（19）。

## 5. 已登记需求

| 编号 | 目录 | 阶段 |
|---|---|---|
| REQ-00 | 本决定 | 评审稿 |
| REQ-01 | [requirements/20261028/commercial-开通项前置门禁](../../requirements/20261028/commercial-开通项前置门禁/requirement.md) | 阶段 1—8 完成：已部署 mor-test，已本地提交 `ba0e15b5` |
| REQ-03 | [requirements/20261029/risk-decision-承保记录](../../requirements/20261029/risk-decision-承保记录/requirement.md) | 已实现，2026-10-29 部署 mor-test（rq03-20261029a，块 143），已本地提交 |
| REQ-03b | 承保建议策略（待办，未立项） | 待风控给出政策数值：按 CRR × 交付方式配置建议观察期、结算周期 T+N、保证金比例（× 估算敞口）；承保页预填建议值并提示偏离；自动承保改为套用建议值（替代固定标准模板，现模板 T+N 为空） |
| REQ-04 | [requirements/20261029/risk-decision-限额档位](../../requirements/20261029/risk-decision-限额档位/requirement.md) | 已实现，2026-10-30 部署 mor-test（rq04-20261030b，块 145；Gateway Snapshot 上限 4 MiB），已本地提交 |
| REQ-04c | 风控配置缓存（待办，未立项；REQ-04 档位门禁上生产的前提） | REQ-04 阶段 6 实测：档位流程每次支付评估 205 条 SQL（逐个读变量定义），4 并发时 25.5% 评估超时（FAIL_CLOSED 拒绝）。范围：批量读取变量定义 + 按激活发布缓存配置快照（常量、名单成员、累计实时读）；目标 p95 ≈ 61 ms。完成前生产环境不得激活档位分支发布 |
| REQ-05 | [requirements/20261028/platform-support-workflow按钮发起与数据验证](../../requirements/20261028/platform-support-workflow按钮发起与数据验证/requirement.md) | 已实现，2026-10-29 部署 mor-test，提交 `af972805`（未推送） |
| REQ-12 | [requirements/20261028/client-master-业务信息补采](../../requirements/20261028/client-master-业务信息补采/requirement.md) | 已实现，2026-10-29 部署 mor-test（rq12-20261029a、入驻 Schema v3），已本地提交 |

是否登记到 `agent-docs/README.md` 与 `DOCUMENT_GOVERNANCE.md` 路由表，由用户决定。
