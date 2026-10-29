# risk-decision：承保记录 · 技术方案

> 状态：**阶段 2 已确认（2026-10-29，S-01—S-13 按建议）；第 7—13 节为阶段 3 评审稿（2026-10-29，设计稿，未改代码 / 未执行 SQL）**。页面原型由主会话在 `operations-console-web/docs/operations-console.pen` 绘制（"原型 风控 · 承保 01—05"），确认前不写前端。
> 依据：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) 第 3.3 节（`GOLIVE_UNDERWRITING_APPROVED`）；需求 [requirement.md](./requirement.md)（阶段 1 已确认：Q-1—Q-12 按建议，补充 R8 估算敞口、R9 行业实践对照，Owner = risk-decision）。

路径缩写：`rd/` = `paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/`；`rd-api/` = `paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/`；`rd-sql/` = `paytech/risk-decision/docs/database/sql/`；`obff/` = `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/`；`oweb/` = `operations-console-web/src/`；`cmp-api/` = `paytech/compliance/paytech-compliance-api/src/main/java/com/paytech/compliance/api/`。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| risk-decision 模块结构 | 单个 core 模块，两个业务包：`rd/decision/`（决策点、规则、决策流、评估、限额，56 个 Processor）与 `rd/control/`（名单、Manual Payout 资格；`service/processor/AddRiskListEntryProcessor.java`、`CreateRiskListProcessor.java`、`DeactivateRiskListEntryProcessor.java`、`ManualPayoutEligibilityProcessor.java`、`RiskListLocks.java`）；Controller 在 `paytech-risk-decision-app/.../ext/rest/control|decision/`；`paytech-risk-decision-core/pom.xml` **无任何其他服务的 `*-api` 依赖、无 `paytech-messaging`**（没有外部调用客户端，也没有 MQ 消费者） |
| 写接口样板 | `rd/control/service/processor/AddRiskListEntryProcessor.java`：`AbstractLockProcessor` + `LockCommand.tryLocks(RiskListLocks…)`；Processor 做校验与构造，`RiskListService` 本地事务保存；`active_slot` 唯一索引兜底；操作人取请求的 `actorAccountId`。Controller `ext/rest/control/RiskListManagementController.java:33-62`：`context.parse(actorAccountId, sessionId)` 只解析身份，**不在 risk-decision 内重检权限**（权限由 Gateway 路由绑定）。没有通用 BCR / 行级历史表，版本历史由各对象自带（如 `t_limit_version`、`t_rule_set_version` 的 `version_no` + `status`） |
| API 风格 | `rd-api/control/api/RiskListManagementApi.java:24-52`（`@HttpExchange("/risk-lists/v1")`，`X-Gateway-Actor-Subject` + `X-Gateway-Session-Id` 两个头） |
| 限额与名单 | `rd-sql/risk-decision-schema.sql:903-925` `t_limit_version`（按 `limit_code` + `version_no` 全局冻结，`subject_type` 可为 CLIENT，但阈值不逐 Client）；`:812-852` `t_risk_list` / `_entry`（`supported_subject_type` 不含 MCC；`list_purpose` BLOCK / ALLOW）；`:791-811` `t_risk_restriction`（COOLDOWN / BLOCK / REVIEW_ONLY，由 Action Effect 产生） |
| 保证金事实 | compliance `t_settlement_control`（`paytech/compliance/docs/database/sql/compliance-schema.sql`，`control_type` SETTLEMENT_BLOCK / MIN_SECURITY_DEPOSIT，`required_amount` / `required_currency`，`source_action_effect_id` **NOT NULL 且唯一**，表注释"由真实 Action Effect 创建"）；`cmp-api/SettlementControlApi.java:15-19`："阶段 0 只定合同与 Schema 归属：**compliance 尚未提供 Controller 与 Processor**"，创建由 risk-decision Action Effect 触发、读取在出款检查点；risk-decision 代码中**没有任何 deposit 相关实现** |
| ops-bff 现有读取能力 | `obff/service/processor/ClientReviewDetailsProcessor.java:42-70`：按 `caseId` 并行取 compliance 案件（`ClientReviewRiskClient.detail`，含 `riskRating`、`tier`、`version`、`clientId`）、client-master 申请资料（`ClientReviewCctClient.application`，values 含 REQ-12 四项与 `draftRevision`）、分类（`classifications`：`confirmed`、`confirmedAt`、`items` 行业 / MCC）、Platform 行业与 MCC 参考数据（`PlatformReferenceDataClient.mccsByCodes` → `MccResponseBody.networkHighRisk`），总等待 3 秒；`obff/service/processor/ClientDetailsProcessor.java:36`：按 `clientId` 取 client-master admin 详情（申请列表含 `dueDiligenceCaseId`）。ops-bff 已有 risk-decision 客户端（`configuration/RiskClientConfiguration.java:44`，并有 `RiskOwnerResponseShapeValidator` 严格校验 risk 响应形状） |
| compliance 案件 | `cmp-api/response/ClientReviewCaseResponseBody.java`：`riskRating`（LOW / MEDIUM / HIGH，未评为空）、`tier`（SIMPLIFIED / STANDARD / ENHANCED）、`version`、`clientId`；事件 `ClientReviewDecided` v2 带 `riskRating`、`applicationId`，消费者只有 client-master（`paytech/foundation/paytech-messaging/catalog/events.yaml:25-32`） |
| Platform 权限与注册 | 权限表字段 `permission_code / resource_code / action_code / risk_level`；风控菜单父资源 `OPERATIONS_RISK_MANAGEMENT`（`900000000000000565`），现有子菜单 8 个（规则配置、拒绝原因、主体标签、决策流、变量、名单、执行发布、评估）；mor-test 无 `RISK_UNDERWRITING*` 权限或角色；注册块现用到 **142**（REQ-05：`t_gateway_route` 最大 `900000000000142403`），`t_permission` 最大 `…139101`，`t_console_resource` 最大 `…139301` |
| data-query 数据源 | mor-test `mor_platform.t_data_source`：6 个 `MOR_*_REPLICA` 均为 `host=mysql`、`username=data_query_demo_reader`；该账号授权只有 `SELECT ON mor_acquiring / mor_channel_connector / mor_client_master / mor_commercial / mor_ledger / mor_reconciliation`，**无 `mor_risk_decision`**；risk-decision 库名 mor-test 与本机均为 `mor_risk_decision`（`test-environment/compose.applications.yaml:94`）；data-query 要求数据源账号只读、密码进 Vault（`DATA_SOURCE_PASSWORD_V1`），启用前检查授权 |
| 运营页面 | `oweb/risk/`（规则、决策流、名单、评估等，无承保）；风控路由 `/risk/*` 由 Platform 菜单 `frontend_route` 驱动 |
| 数据现状（只读） | mor-test `mor_compliance.t_due_diligence_case`：APPROVED 37（**全部无 `risk_rating`**）、IN_REVIEW 1（LOW）、SUBMITTED 21、RETURNED 3、WITHDRAWN 3；`mor_platform.t_ref_mcc` 298 个、`network_high_risk=1` 14 个 |
| 规划与上游 | `design-discuss/client-activation/requirement-plan.md:192-210`（N2）、`:313-322`（N11 观察期）、`:509`（REQ-04：档位名单 + 分支、不新建档位表、不逐 Client 阈值）、`:523`（REQ-18：Risk 最低保证金要求入口、到账、Manual Payout 检查点读取）；`ops-line.md:188-201`（T06 / T08 操作清单）；REQ-12 `requirements/20261028/client-master-业务信息补采/technique.md`（四项已入 values；F-07 业务资料摘要列无写入） |
| 边界依据 | `AGENTS.md` Fixed architecture boundaries：Finance 以 `CLIENT_SECURITY_DEPOSIT/NONE` 负债账户表示保证金、**不得恢复 Reserve Case / Restricted Fund / 其他余额工作流**；"Risk owns the final minimum deposit requirement"；Manual Payout 四个检查点须 fresh Risk check，Client 要求零 Recovery Receivable 与足额 Security Deposit |

## 2. 现状发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | risk-decision 是单 core 模块、`control` / `decision` 两包；写接口统一 `AbstractLockProcessor` + 本地 Service 事务 + `active_slot` 唯一索引；无 BCR，版本历史由对象自带 | 承保放 `rd/control/underwriting`（与名单、出款资格同为"控制"类），照名单维护样板；版本历史用"每个版本一行"自带，不引入 BCR |
| F-02 | risk-decision 不重检权限，只解析操作人；权限由 Gateway 路由绑定 | 四眼的"复核权限"靠复核路由绑定 `RISK_UNDERWRITING_APPROVE`；"复核人 ≠ 提交人"在 risk-decision 按 `actorAccountId` 校验（S-05） |
| F-03 | risk-decision 没有对外调用客户端与 MQ 消费者 | 印证 Q-2 / Q-3：输入由 ops-bff 聚合提交，risk-decision 冻结快照；不新增外部依赖，保持 M |
| F-04 | ops-bff 已能按 `caseId` 并行取齐 CRR、档次、案件版本、申请 values（含 REQ-12 四项、`draftRevision`）、分类确认与行业 / MCC、MCC 高风险标记；按 `clientId` 能取到申请列表与 `dueDiligenceCaseId` | 承保页聚合可复用现有客户端：`clientId` → 最近一个已批准申请的案件 → 复用审核详情的四路读取；无需 compliance / client-master / Platform 新接口（S-02） |
| F-05 | 来源坐标可用：案件 `caseId + version`、申请 `applicationId + draftRevision`、分类 `confirmedAt`；主体资料摘要（`t_party_profile.content_hash`）未对外暴露，业务资料摘要列无写入（REQ-12 F-07） | 快照来源坐标用前三者，不用内容摘要（S-04） |
| F-06 | 限额阈值全局（`t_limit_version`），REQ-04 档位按名单 + 分支、不逐 Client | Q-4 结论成立：本期不记逐 Client 额度；承保记录给 REQ-04 提供"是否有有效 APPROVED"和观察期天数 / 笔数 |
| F-07 | 名单主体类型不含 MCC；Platform MCC 有 `network_high_risk` | 高风险 MCC 集合 = Platform 标记 ∪ 规则版本中的排除清单（Q-6）；排除清单存在承保规则配置中，不扩名单主体类型 |
| F-08 | compliance `t_settlement_control` 的 `MIN_SECURITY_DEPOSIT` 要求 `source_action_effect_id` 非空唯一，且 API 无实现；risk-decision 无保证金实现 | 承保记录只能作为"Risk 最低保证金要求"的**决策事实**；变成可执行控制须 REQ-18 解决来源（让承保产生 Action Effect，或放宽来源列）与 compliance 实现，本期不写 compliance（S-08） |
| F-09 | mor-test 37 个已批准案件无 CRR | Q-9：只能人工承保、按 MEDIUM；不能自动、不需四眼 |
| F-10 | data-query 只读账号未授权 `mor_risk_decision` | 发布时 DBA 执行 `GRANT SELECT ON mor_risk_decision.* TO data_query_demo_reader`（或新建专用只读账号）并登记 `MOR_RISK_DECISION_REPLICA`；模板只读承保表，**不得读规则 / 评估等其他表**（S-10） |
| F-11 | ops-bff 对 risk 响应有严格形状校验（`RiskOwnerResponseShapeValidator`） | 新接口须登记形状，否则 BFF 拒收（实现期注意点） |
| F-12 | `ClientReviewDecided` 已带 `riskRating` 与 `applicationId`，消费者只有 client-master | 未来"全自动承保"的触发源已存在；本期不消费（Q-3），在行业对照中列为延期 |
| F-13 | 注册块下一个可用号为 **143**（142 已被 REQ-05 使用） | 阶段 3 执行前在 mor-test 复核 |
| F-14 | 估算敞口所需输入：预计月流水与币种（申请 values）、交付方式与天数（REQ-12，新申请才有）、T+N（承保员填写） | 存量申请缺交付方式 → 显示"无法估算"；计算放 risk-decision 服务端（提交时按快照重算并冻结），页面实时预览用同一公式（S-07） |

## 2a. 行业实践对照（R9）

参照收单行与 PayFac 的商户承保做法（卡组织对 PayFac / 子商户的承保与监控要求、常见收单承保政策），本期取舍如下：

| 行业做法 | 本期 | 说明 |
|---|---|---|
| **敞口评估**（交付延迟 × 流水 ≈ 未交付风险 / 拒付风险） | 采用（参考值） | R8 / B10：月流水 ×（交付天数 + T+N）/ 30，提交冻结；只作参考，不做门禁 |
| **分级承保**（低风险自动、其余人工，高风险加签） | 采用 | 自动规则（CRR LOW、流水阈值、非高风险 MCC）一键生成；HIGH 四眼 |
| **决定不可变、版本留痕**（承保依据可审计） | 采用 | 每版本一行、提交冻结输入快照与来源坐标、APPROVED 不可改、修订替换 |
| **额度爬坡**（新户低额、观察后放量） | 采用（由 REQ-04 执行） | 承保记观察期天数 / 笔数；档位名单 VERIFICATION → OBSERVATION → STANDARD 在 REQ-04 |
| **逐商户处理额度**（单笔 / 日 / 月） | 延期 | 现有限额全局冻结，逐 Client 阈值无执行路径（F-06）；等真有逐户阈值需求再做"档位正规化" |
| **延迟结算**（T+N 加长） | 部分（只记建议） | 承保记 T+N 建议；合同 T+N 低于建议的"破标"提示归 commercial P1，结算执行不在 Risk |
| **保证金 / 储备金** | 部分：只记最低保证金要求；不做滚动储备金 | `AGENTS.md` 禁止 Reserve Case / Restricted Fund / 其他余额工作流，保证金为 Finance 负债账户；控制下发与 Manual Payout 检查归 REQ-18 |
| **卡组织高风险登记**（Visa / Mastercard 高风险 MCC 子商户登记） | 部分（只记"需登记"） | 按 Platform `network_high_risk` 预勾；登记事实与报送在 channel-connector（REQ-13） |
| **重新承保触发**（流水超预期、拒付率、MCC / 资格变化、定期复审） | 延期 | 本期只支持人工修订；自动触发需要交易与拒付监控、资格事件消费，列入后续"持续监控"需求 |
| **黑名单 / 终止商户名单核查**（如 MATCH） | 不在承保 | 属 compliance 尽调与筛查 |
| **财务资料审查**（报表、银行流水） | 不做结构化 | 承保说明中人工记录 |

## 3. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 / 原因 | 处理建议 |
|---|---|---|---|---|
| R1 承保记录 | 无（F-01） | 每 Client 版本化记录、状态机、快照 | 未实现 | 新表（S-01、S-03） |
| R2 命令与查询 | 无 | 草稿 / 提交 / 复核 / 自动 / 修订；当前 / 历史 / 列表 | 未实现 | 照名单样板：按 Client 加锁 + 版本 CAS + 槽位唯一索引 |
| R3 自动规则 | 无；名单不支持 MCC（F-07） | 版本化规则（币种阈值、排除 MCC） | 未实现 | 规则配置表 + DML 初值（S-06） |
| R4 运营页面与聚合 | 审核详情已有四路读取（F-04） | 承保页聚合 + 命令转发 | 按 Client 定位案件、命令转发未实现 | 新 ops-bff Processor 复用现有客户端（S-02） |
| R5 注册与文案 | 无承保权限（F-13） | 三权限、菜单、按钮、路由 | 未实现 | 块 143 |
| R6 查询模板 | 只读账号无 `mor_risk_decision`（F-10） | `GOLIVE_UNDERWRITING_APPROVED` | 数据源缺失 | 模板文本 + 发布时授权与登记 |
| R8 估算敞口 | 无 | 预览 + 冻结 | 未实现；存量无交付方式（F-14） | 服务端计算并冻结（S-07） |
| R9 行业对照 | — | 说明取舍 | — | 第 2a 节 |
| B2 前提 | 案件 CRR / 分类确认可读（F-04） | 不满足不能提交 | 校验点在 risk-decision，事实来自快照 | 快照必填项 + 来源坐标；无 CRR 按 MEDIUM（F-09） |
| B4 四眼 | risk-decision 不重检权限（F-02） | HIGH 由复核权限且 ≠ 提交人 | 需组合路由权限与服务内校验 | S-05 |
| B5 保证金 | compliance 控制未实现（F-08） | 记录要求 | 只能记录 | S-08 |
| REQ-04 读取 | — | 有效 APPROVED、观察期 | 同库 | 发布本地 Domain Service 查询（S-09） |

## 4. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 是否改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| risk-decision | 承保决定与规则 Owner | `rd/control/underwriting`：记录、规则配置、状态机、四眼、自动规则、敞口计算、修订；`rd-api` 新承保接口；Controller；本地 Domain Service 查询供 REQ-04；auto 测试 | 代码：是；模型：是（新表 3 张，S-01 / S-06）；DML：规则初值 | 不新增外部依赖；与决策评估链路无交集（不影响支付 / 结账评估） |
| operations-console-bff | 页面聚合 | 承保页聚合 Processor（`clientId` → 已批准申请的案件 → 复用 compliance / client-master / Platform 读取 + risk-decision 当前记录与历史）；命令转发（把展示用输入与来源坐标随提交带给 risk-decision）；列表转发；响应形状登记 | 代码：是 | 依赖 compliance 案件、client-master 申请与分类、Platform MCC 读取的现有接口；任一来源不可用时页面降级显示、禁止提交 |
| operations-console-web | 运营页面 | "风控 → 承保"列表、承保页（输入快照、自动判定清单、敞口、结论表单、复核区、版本历史）；文案；原型先行 | 代码：是；Pencil：是 | — |
| Platform | 权限、菜单、路由、语言包、data-query 登记 | 块 143：权限 3、菜单 1、按钮约 3、路由约 9；operations-console 语言包一版；`MOR_RISK_DECISION_REPLICA` 数据源与模板（运行时登记） | 注册 DML：是；运行时登记：是 | 离线编译 Gateway 快照后执行 |
| mor-test MySQL（DBA） | 只读账号 | `GRANT SELECT ON mor_risk_decision.*`（或专用账号） | 是（root SQL，发布动作） | 账号授权范围扩大到风控库：模板只读承保表（S-10） |
| compliance | 尽调 | 不改（只读案件详情） | 否 | — |
| client-master | 申请资料 | 不改（只读申请 values、分类） | 否 | — |
| commercial / channel-connector / finance | 破标、卡组织登记、保证金执行 | 不改（P1 / REQ-13 / REQ-18） | 否 | — |
| workflow（REQ-05 / REQ-07） | 流程 | 消费模板；REQ-07 建 N2 节点 | 否（本需求只交付模板） | 模板输出名按 camelCase |
| 文档 | — | risk-decision 数据库文档与物理决策、决定 3.3 输出名、`CURRENT_IMPLEMENTATION.md` | 是 | — |

## 5. 待决事项

| 编号 | 问题 | 建议 |
|---|---|---|
| S-01 | 承保记录表形态 | **单表每版本一行** `t_client_underwriting`：`client_id`、`version_no`、`status`（DRAFT / SUBMITTED / APPROVED / DECLINED / SUPERSEDED）、`open_slot`（DRAFT / SUBMITTED 为 1）与 `effective_slot`（APPROVED 为 1）两个稀疏槽位分别唯一 `(client_id, open_slot)`、`(client_id, effective_slot)`、`supersedes_underwriting_id`、结论列、快照列、决定人与时间、`decision_source`（MANUAL / AUTO_RULE）、`rule_version_id`、`version`（CAS）。不建 root + version 双表（只有一个可变 Root 属性即"当前版本"，由槽位表达） |
| S-02 | 页面按 Client 定位案件 | ops-bff：client-master admin 详情 → 最近一个 `APPROVED` 申请的 `dueDiligenceCaseId` → 复用审核详情的四路读取；没有已批准申请时页面提示"尽调未批准"、禁止提交 |
| S-03 | 输入快照存储 | **结构化列**（承保规则与查询要用）：`crr`、`dd_tier`、`mcc_code`、`industry_code`、`expected_monthly_volume`、`volume_currency`、`expected_ticket`、`fulfillment_type`、`fulfillment_days`、`website_url`、`refund_policy_url`、`expected_payment_methods`；来源坐标：`source_case_id`、`source_case_version`、`source_application_id`、`source_draft_revision`、`source_classification_confirmed_time`；不用无 Schema JSON |
| S-04 | 快照可信度 | 输入由 ops-bff 从 Owner 读取后随提交传入；risk-decision 校验必填、格式与枚举后冻结（与 Q-2 一致）。不调用 Owner 复核（risk-decision 无客户端，F-03）；风险可接受：BFF 为受信私网组件，来源坐标可事后追溯 |
| S-05 | 四眼实现 | 复核接口单独路由并绑定 `RISK_UNDERWRITING_APPROVE`；risk-decision 校验：只对 `status=SUBMITTED` 生效、`actorAccountId ≠ submitted_by`；LOW / MEDIUM 提交接口直接置 APPROVED / DECLINED（绑定 `RISK_UNDERWRITING_MANAGE`）；HIGH 提交只到 SUBMITTED |
| S-06 | 自动规则配置表 | `t_underwriting_rule_version`（`version_no`、`status` DRAFT / ACTIVE / RETIRED、`active_slot`、`excluded_mcc_codes` 逗号串、`content_hash` 可选）+ `t_underwriting_rule_threshold`（`rule_version_id`、`currency`、`monthly_volume_limit`）；初值 v1：USD 50,000、排除清单空（风控确认）；无配置页面，DML 发布新版本 |
| S-07 | 估算敞口计算位置与口径 | risk-decision 领域对象计算（提交与自动生成时按快照重算并冻结 `estimated_exposure` + `exposure_days`），列表 / 详情直接读；ops-bff 预览接口调用 risk-decision 同一计算（避免前端重复公式）或页面按同公式预览（阶段 3 选，倾向后端预览接口）；口径按 B10；币种 = 流水币种；T+N 未填按 0 并标注 |
| S-08 | 保证金边界 | **本期**：risk-decision 承保记录保存"最低保证金要求"（要求 / 金额 / 币种 / 是否阻塞收款）作为 Risk 决策事实。**REQ-18**：把有效 APPROVED 的要求下发为 compliance `MIN_SECURITY_DEPOSIT` 控制（需解决 `source_action_effect_id` 来源）并实现 compliance 接口，Finance 记到账、Manual Payout 检查点读取；"阻塞收款"的运行期执行另由 REQ-04 / REQ-18 决定。本期不写 compliance |
| S-09 | REQ-04 读取形状 | risk-decision 内部发布 Domain Service `ClientUnderwritingQueryService.findEffective(clientId)` → 有效 APPROVED 的结论摘要（观察期天数 / 笔数、是否要求保证金、`highRiskMcc`、T+N）；同库同进程调用，不新增 HTTP 接口；"无承保 = 额度为零"由 REQ-04 在决策流 Feature 中实现 |
| S-10 | 查询模板数据源 | 发布时 DBA 给只读账号加 `mor_risk_decision` 的 SELECT（沿用 `data_query_demo_reader`，与其他六库一致），登记 `MOR_RISK_DECISION_REPLICA`；模板只引用 `t_client_underwriting`；列别名 camelCase：`satisfied`、`out_requireDeposit`、`out_depositBlocksCollection`、`out_highRiskMcc`、`out_suggestedSettlementDays`、`missing` |
| S-11 | 权限与菜单 | 块 143：`RISK_UNDERWRITING_READ`（菜单、列表、详情、聚合读取）、`RISK_UNDERWRITING_MANAGE`（草稿、保存、提交、自动生成、修订）、`RISK_UNDERWRITING_APPROVE`（复核批准 / 拒绝）；菜单 `OPERATIONS_RISK_UNDERWRITING`（`/risk/underwriting`）挂在 `OPERATIONS_RISK_MANAGEMENT` 下；按钮资源对应 MANAGE / APPROVE；角色组合用现有角色管理（"风控承保员" = `RISK_DECISION_CONFIG_READ` + READ + MANAGE，"风控负责人" 再加 APPROVE），不在本需求建角色。**2026-10-29 阶段 5 补充（用户选 A）**：父菜单 `OPERATIONS_RISK_MANAGEMENT` 受 `RISK_DECISION_CONFIG_READ` 控制，Platform 导航不返回无权限的目录、控制台也不补父级，只有承保三项权限的角色看不到"风控"菜单；因此承保角色同时授予 `RISK_DECISION_CONFIG_READ`（可只读查看风控规则配置），不改菜单挂载与 Platform 导航 |
| S-12 | 并发与重复 | 锁 `risk-underwriting:client:{clientId}`（TRY_LOCK），行 `version` CAS；`open_slot` / `effective_slot` 唯一索引兜底；同一 Client 同时只能有一份草稿；修订批准时同事务把旧 APPROVED 置 SUPERSEDED 并清 `effective_slot` |
| S-13 | `UnderwritingDecided` | 不发（规划已定）；不登记补偿任务，REQ-04 读本库 |

## 6. 工作量与风险

| 项 | 判断 |
|---|---|
| 规模 | **M**：risk-decision 新聚合（3 表、约 9 个接口：草稿 / 保存 / 提交 / 复核 / 自动生成 / 修订 / 当前 / 历史 / 列表 + 敞口预览）、规则判定与敞口计算；ops-bff 1 个聚合 + 约 9 个转发；ops-web 2 页（列表、承保页多态）；注册块 1；模板 1 + 发布登记。不新增外部依赖与 MQ |
| 最大风险 | 快照来自 BFF 聚合（S-04）：若 ops-bff 取错案件（如退回重提的多轮案件），快照会落错来源。靠"只取最近已批准申请的当前案件"规则 + 来源坐标 + auto 测试兜底 |
| 第二风险 | 保证金只记录不执行（S-08）：业务可能误以为承保要求了保证金就已生效；页面与文档须标注"由 REQ-18 执行" |
| 第三风险 | 只读账号授权扩大到风控库（S-10）：模板须只读承保表，评审时核对 |
| 依赖 | REQ-12（已交付）提供业务四项；REQ-04 / REQ-18 消费本需求；无阻塞 |
| 需要页面原型的点 | ① 承保列表（状态、CRR、待我复核筛选）；② 承保页四态：新建（输入快照、自动判定清单、估算敞口）、人工填写、HIGH 待复核（复核区）、只读详情（版本历史、审批来源、来源坐标） |

**确认点**：用户确认 F-01—F-14、第 2a 节行业对照取舍、S-01—S-13 与第 4 节影响范围后进入阶段 3。

> **阶段 2 已确认（2026-10-29）**：用户接受 S-01—S-13 全部建议。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-29）

**一句话规模**：
- **系统**：本次方案涉及 risk-decision、operations-console-bff、operations-console-web 与 Platform 注册 / data-query 登记；compliance、client-master、commercial 不改。
- **接口**：按业务 Method + Path 去重新增 13 个（risk-decision 12 个 U1—U12；ops-bff 对外 12 个：11 个与 risk-decision 同路径转发，另加工作台聚合 1 个，U3 只供 BFF 内部使用），调整 0 个。
- **数据库**：新增表 3 张（`t_client_underwriting` 58 列、`t_underwriting_rule_version` 8 列、`t_underwriting_rule_threshold` 6 列），修改旧表 0。
- **初始化 DML**：2 行（规则 v1 与 USD 阈值）。
- **注册 DML 块 143**：权限 3、角色授权 3、控制台资源 3、路由 12、路由头 52、查询参数 8、守卫 72，共 153 行。
- **data-query**：数据源 1、模板 1（运行时登记）。
- **页面原型**：5 张（"原型 风控 · 承保 01—05"，`operations-console.pen` y 99660 一排，导出图在 `prototypes/`）。

已读规范：
- `DEVELOPMENT_CONVENTIONS.md`（第 4—6 节）、`JAVA_PROJECT_STRUCTURE.md`、`DATABASE_DESIGN_GUIDE.md`（active_slot、JSON 限制）、`UNIT_TEST_GUIDE.md`、`examples/WRITE_API_EXAMPLE.md`。
- RFC 9457 Problem 设计（`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`：`errors[]` 只用于字段校验，业务执行错误不带字段数组）。
- risk-decision 名单维护样板（第 1 节）。

本地原子写入与事务外步骤：

| 用例 | 事务外 | 本地事务（`mor_risk_decision`） |
|---|---|---|
| 工作台读取（ops-bff） | 并行读 client-master admin 详情 → compliance 案件、client-master 申请 values 与分类、Platform MCC、risk-decision 当前记录与预览 | 无 |
| 建草稿 / 保存 / 提交 / 自动承保 / 修订（risk-decision） | 无外部调用（输入由 ops-bff 随请求带入） | 锁 Client → 校验 → 写版本行（含槽位与 CAS） |
| 复核 | 无 | 锁 Client → CAS 改状态；批准修订时同事务把旧 APPROVED 置 SUPERSEDED |

## 7. 方案总览

```
ops-web 承保页 ─GET workspace─▶ ops-bff UnderwritingWorkspaceProcessor
                                  1 client-master admin client(clientId) → 最近 APPROVED 申请 → dueDiligenceCaseId
                                  2 并行（≤ 3 秒）：compliance 案件 (CRR、档次、version) · client-master 案件申请 values (含 REQ-12 四项、draftRevision)
                                     · client-master 案件分类 (confirmed、confirmedAt、MCC / 行业) · Platform MCC (networkHighRisk)
                                     · risk-decision 当前记录 GET /clients/{clientId}/underwriting
                                  3 组装 UnderwritingInput（即将冻结的快照）→ risk-decision POST /previews（自动承保判定 + 估算敞口）
                                  → 页面：输入面板 / 自动判定清单 / 敞口 / 当前与进行中记录 / 版本入口

ops-web 命令（只发结论字段）─▶ ops-bff 转发前按步骤 1—2 重新取输入，附 input 一并提交（页面不能伪造输入）
     ├ POST /clients/{clientId}/underwritings          建草稿（open_slot=1，DRAFT）
     ├ PUT  /underwritings/{id}                        保存草稿（CAS）
     ├ POST /underwritings/{id}/submission             冻结快照：CRR ≠ HIGH → APPROVED / DECLINED（effective_slot 切换）
     │                                                             CRR = HIGH → SUBMITTED（等待复核）
     ├ POST /underwritings/{id}/review   (APPROVE 权限) 复核人 ≠ 提交人：APPROVED / DECLINED
     ├ POST /clients/{clientId}/auto-underwritings     服务端按 ACTIVE 规则重判，满足才生成 APPROVED（AUTO_RULE）
     └ POST /underwritings/{id}/revision               复制有效 APPROVED 的结论为新 DRAFT（版本号 + 1）

risk-decision rd/control/underwriting：
  锁 risk-underwriting:client:{clientId}（TRY_LOCK 500ms / 30s）→ 校验 → UnderwritingService 本地事务
  → t_client_underwriting（每版本一行；open_slot / effective_slot 唯一）→ 返回版本行视图
REQ-04（后续）：同进程 ClientUnderwritingQueryService.findEffective(clientId)
data-query：MOR_RISK_DECISION_REPLICA · GOLIVE_UNDERWRITING_APPROVED（只读 t_client_underwriting）→ REQ-05 DATA_QUERY
```

## 8. 接口

### 8.1 risk-decision `rd-api/decision/api/control/ClientUnderwritingApi.java`（新增，`@HttpExchange("/risk-underwriting/v1")`）

所有接口带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`（Controller 用现有 `context.parse` 解析操作人；risk-decision 不重检权限，权限由路由绑定）。ID、金额、时间一律字符串（十进制 / 微秒 UTC）。

| # | 方法与路径 | 用途 | 成功 | 绑定权限（Gateway） |
|---|---|---|---|---|
| U1 | `GET /underwritings?status&crr&decisionSource&keyword&clientId&pendingReviewFor&page&size` | 列表（按 `update_time` 倒序；**不传 `status`（"全部"）时每个 Client 只列最新版本（`version_no` 最大），传 `status` 时列该状态的全部版本**（已作废 / 已拒绝页签可见旧版本），其余筛选在此基础上叠加，`total` 同口径；历史在详情页看。阶段 5 浏览器走查后主会话按原型 01 确定；`status` 可为 DRAFT / SUBMITTED / APPROVED / DECLINED / SUPERSEDED，空 = 全部；`decisionSource` MANUAL / AUTO_RULE；`keyword` 匹配 `client_display_name` 包含或 `client_no` / `client_id` 相等；`pendingReviewFor`=账号 ID 时只列 SUBMITTED 且提交人 ≠ 该账号） | 200 `PagingResponseBody<UnderwritingSummaryBody>` | READ |
| U2 | `GET /underwritings/{underwritingId}` | 单版本详情 | 200 `UnderwritingResponseBody` | READ |
| U3 | `GET /clients/{clientId}/underwriting` | 当前：`effective`（有效 APPROVED）、`open`（DRAFT / SUBMITTED）、`latest`（最新版本） | 200 `ClientUnderwritingCurrentResponseBody`（三者均可为 null） | READ（经工作台） |
| U4 | `GET /clients/{clientId}/underwritings` | 版本历史（`version_no` 倒序，最多 100） | 200 `UnderwritingHistoryResponseBody` | READ |
| U5 | `POST /clients/{clientId}/underwritings` | 建草稿 | 201 `UnderwritingResponseBody` | MANAGE |
| U6 | `PUT /underwritings/{underwritingId}` | 保存草稿 | 200 | MANAGE |
| U7 | `POST /underwritings/{underwritingId}/submission` | 提交 | 200 | MANAGE |
| U8 | `POST /underwritings/{underwritingId}/review` | 复核（HIGH） | 200 | APPROVE |
| U9 | `POST /clients/{clientId}/auto-underwritings` | 按规则自动承保 | 201 | MANAGE |
| U10 | `POST /underwritings/{underwritingId}/revision` | 基于有效 APPROVED 建修订草稿（`underwritingId` 须为有效版本）：结论复制自有效版本，**输入按最新来源重新取得**（ops-bff 附带），记录修订原因 | 201 | MANAGE |
| U11 | `POST /previews` | 自动承保判定 + 估算敞口（纯计算，读 ACTIVE 规则，不写库） | 200 `UnderwritingPreviewResponseBody` | MANAGE |
| U12 | `GET /underwriting-statistics` | 列表顶部统计：生效中（`effective_slot=1`）、草稿、待复核、本月已批准、本月已拒绝（按 `decided_time` 所在 UTC 自然月） | 200 `UnderwritingStatisticsResponseBody` | READ |

**请求体**

`UnderwritingInputBody`（ops-bff 组装；页面不能直接提交给 risk-decision）：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `clientDisplayName` | String | 是 | ≤ 255；快照时的 Client 法定名或个人姓名（列表展示用，冻结） |
| `clientNo` / `clientType` / `jurisdictionCode` | String | 是 | 快照时的 Client 编号、类型（INDIVIDUAL / ORGANIZATION）与辖区（列表「编号 · 辖区 · 类型」） |
| `crr` | String | 否 | LOW / MEDIUM / HIGH；为空 = 尽调未评 CRR，**按 MEDIUM 处理**（Q-9），`crrMissing=true` |
| `ddTier` | String | 否 | SIMPLIFIED / STANDARD / ENHANCED |
| `mccCode` | String | 提交必填 | `[0-9]{4}` |
| `industryCode` | String | 否 | ≤ 64 |
| `networkHighRiskMcc` | Boolean | 是 | Platform `MccResponseBody.networkHighRisk` |
| `expectedMonthlyVolume` / `volumeCurrency` / `expectedTicket` | String / String / String | 否 | 十进制 ≥ 0，≤ 16 位整数、8 位小数；币种 `[A-Z]{3}`（有金额时必填） |
| `fulfillmentType` | String | 否 | INSTANT / PRE_ORDER / SUBSCRIPTION / SERVICE |
| `fulfillmentDays` | Integer | 否 | 1—365，仅 PRE_ORDER |
| `websiteUrl` / `refundPolicyUrl` | String | 否 | ≤ 2048 |
| `expectedPaymentMethods` | String | 否 | ≤ 512，规范化逗号串（原样冻结） |
| `sourceCaseId` / `sourceCaseVersion` | String | 提交必填 | 十进制 ID / 十进制版本 |
| `sourceApplicationId` / `sourceDraftRevision` | String | 提交必填 | 十进制 |
| `sourceClassificationConfirmedAt` | String | 提交必填 | 微秒 UTC；为空 = 分类未确认 → 不能提交 |

`UnderwritingDecisionBody`（页面填写）：

| 字段 | 类型 | 提交必填 | 规则 |
|---|---|---|---|
| `observationDays` | Integer | 是 | 1—365，默认 30 |
| `observationTransactions` | Integer | 是 | 1—100000，默认 100 |
| `requireDeposit` | Boolean | 是 | 默认 false |
| `depositAmount` / `depositCurrency` | String | `requireDeposit=true` 时必填 | 金额 > 0；币种 `[A-Z]{3}`；不要求时须为空 |
| `depositBlocksCollection` | Boolean | 是 | 只有 `requireDeposit=true` 时可为 true |
| `suggestedSettlementDays` | Integer | 否 | 0—90；为空时敞口按 0 计并标注 |
| `highRiskMcc` | Boolean | 是 | 默认 = `networkHighRiskMcc` |
| `highRiskMccReason` | String | 与默认不同时必填 | ≤ 500 |
| `limitNote` | String | 否 | ≤ 500（额度说明，Q-4） |
| `underwritingNote` | String | 否 | ≤ 2000（交付周期与拒付暴露评估、其他判断） |
| `handoverNote` | String | 否 | ≤ 500（交接给商务） |

| 接口 | Body |
|---|---|
| U5 | `{ "input": UnderwritingInputBody, "decision": UnderwritingDecisionBody? }` |
| U6 | `{ "expectedVersion": Integer, "input": …, "decision": … }` |
| U7 | `{ "expectedVersion": Integer, "input": …, "decision": …, "outcome": "APPROVE" \| "DECLINE", "declineReason": String? }`：DECLINE 须 `declineReason`（≤ 500）；CRR = HIGH 时 `outcome` 只能 APPROVE（提交待复核） |
| U8 | `{ "expectedVersion": Integer, "decision": "APPROVE" \| "DECLINE", "comment": String? }`：`comment` ≤ 500；DECLINE 时必填并作为拒绝原因（存 `decline_reason`），APPROVE 时可空（存 `review_comment`），对应原型 04 的「复核意见（拒绝时必填）」 |
| U9 | `{ "input": UnderwritingInputBody }`：结论取规则版本的标准模板（观察期 30 天 / 100 笔、不要求保证金、`highRiskMcc=false`、T+N 为空） |
| U10 | `{ "expectedVersion": Integer, "revisionReason": String, "input": UnderwritingInputBody }`：`expectedVersion` 为有效版本的 version；`revisionReason` 必填 ≤ 500（原型 05 版本历史显示）；`input` 由 ops-bff 重新取得附带 |
| U11 | `{ "input": UnderwritingInputBody, "suggestedSettlementDays": Integer? }` |

**响应体**

`UnderwritingResponseBody`：`underwritingId`、`clientId`、`versionNo`、`status`、`decisionSource`（MANUAL / AUTO_RULE / null）、`ruleVersionNo`、`reviewLevel`（LOW / MEDIUM / HIGH，`crr` 为空时 MEDIUM）、`input`（同请求体字段 + `crrMissing`）、`decision`（同上）、`estimatedExposure`（`{ amount, currency, riskDays, settlementDaysAssumed, formula }` 或 null + `exposureUnavailableReason`）、`declineReason`、`reviewComment`、`revisionReason`、`supersedesUnderwritingId`、`createdBy`、`submittedBy`、`submittedAt`、`decidedBy`、`decidedAt`、`supersededAt`、`version`。

`UnderwritingSummaryBody`（列表，原型 01 各列）：`underwritingId`、`clientId`、`clientDisplayName`、`clientNo`、`jurisdictionCode`、`clientType`、`crr`、`mccCode`、`networkHighRiskMcc`、`versionNo`、`status`、`reviewLevel`、`decisionSource`、`ruleVersionNo`、`estimatedExposure`（同详情）、`fulfillmentType`、`suggestedSettlementDays`、`requireDeposit`、`submittedBy`、`decidedBy`、`updatedAt`。

`UnderwritingStatisticsResponseBody`：`effective`、`draft`、`submitted`、`approvedThisMonth`、`declinedThisMonth`（整数）。

`UnderwritingPreviewResponseBody`：`ruleVersionNo`、`autoEligible`、`checks[]`（`{ code, passed }`，8 项固定顺序，code 见 8.2）、`thresholdAmount` / `thresholdCurrency`（输入流水币种在 ACTIVE 规则中的月流水上限，十进制字符串 + 币种；无规则、未申报流水或该币种无阈值时为 null；阶段 4 按原型 02 / 03"≤ 阈值"增补）、`estimatedExposure`（同上）、`exposureUnavailableReason`、`reviewLevel`。

**错误**（新增 `rd-api/decision/api/enums/RiskUnderwritingErrorCode`，`CodeMessageEnum`；HTTP 状态在 `paytech-risk-decision-app/.../configuration/RiskApiErrorConfiguration.java` 映射，ops-bff `OperationsErrorStatusResolver` 同步）：

| 情形 | HTTP | code |
|---|---|---|
| 请求字段格式 / 必填 / 范围（含"提交必填"） | 422 | `VALIDATION_ERROR`（`errors[].field` = `input.mccCode`、`decision.depositAmount` 等） |
| 记录不存在 / 不属于该 Client | 404 | `RISK_UNDERWRITING_NOT_FOUND` |
| 已有进行中记录（建草稿、自动承保、修订时） | 409 | `RISK_UNDERWRITING_OPEN_EXISTS` |
| `expectedVersion` 不符 / 并发 | 409 | `RISK_UNDERWRITING_VERSION_CONFLICT` |
| 状态不允许（如对 APPROVED 保存、对 DRAFT 复核、对非有效版本修订） | 409 | `RISK_UNDERWRITING_STATE_INVALID` |
| 复核人是提交人 | 409 | `RISK_UNDERWRITING_SELF_REVIEW` |
| HIGH 提交 `outcome=DECLINE` | 409 | `RISK_UNDERWRITING_DECLINE_REQUIRES_REVIEW` |
| 自动承保条件不满足 / 无 ACTIVE 规则 | 409 | `RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE`（`detail` 为该错误码的固定公开文案——Problem 只公开错误码文案，未通过的 check code 只在服务端异常消息中；页面以 U11 预览展示原因。阶段 5 联调核实后更正） |
| 资源锁未取得 | 409 | `CONCURRENT_REQUEST`（Foundation） |

Problem 形状按系统约定：`type`、`title`、`status`、`detail`、`code`、`requestId`、`timestamp`；仅 422 带 `errors[{field, message}]`。

### 8.2 规则

**自动承保判定（U9 与 U11 同一实现 `UnderwritingAutoRule`，读 ACTIVE 规则版本）**，全部通过才 eligible：

| check code | 通过条件 |
|---|---|
| `RULE_CONFIGURED` | 存在 ACTIVE 规则版本 |
| `CRR_LOW` | `crr = LOW`（为空不通过） |
| `VOLUME_PROVIDED` | `expectedMonthlyVolume` 与 `volumeCurrency` 非空 |
| `CURRENCY_CONFIGURED` | 规则版本有该币种阈值 |
| `VOLUME_WITHIN_THRESHOLD` | 月流水 ≤ 阈值 |
| `MCC_NOT_NETWORK_HIGH_RISK` | `networkHighRiskMcc = false` |
| `MCC_NOT_EXCLUDED` | `mccCode` 不在规则版本 `excluded_mcc_codes` |
| `CLASSIFICATION_CONFIRMED` | `sourceClassificationConfirmedAt` 非空 |

**估算敞口（`UnderwritingExposure`，B10）**：`riskDays` = PRE_ORDER：`fulfillmentDays + T+N`；INSTANT：`T+N`；SUBSCRIPTION：`30 + T+N`；SERVICE：`T+N`。`amount = expectedMonthlyVolume × riskDays / 30`，`HALF_UP` 保留 2 位，币种 = `volumeCurrency`；T+N 为空按 0 并置 `settlementDaysAssumed=true`。不可算时 `estimatedExposure=null`，原因 `VOLUME_MISSING` / `FULFILLMENT_TYPE_MISSING` / `FULFILLMENT_DAYS_MISSING`。保存与提交时随快照重算冻结（`estimated_exposure`、`exposure_currency`、`exposure_risk_days`）。

**状态机**：

| 从 | 动作 | 到 | 条件 |
|---|---|---|---|
| — | U5 / U10 建草稿 | DRAFT | 无进行中记录；U10 须有有效 APPROVED（复制其 decision、用最新输入、写 `revision_reason`） |
| DRAFT | U6 保存 | DRAFT | — |
| DRAFT | U7 APPROVE | APPROVED | reviewLevel ≠ HIGH；冻结快照；若有有效 APPROVED，同事务旧版本 → SUPERSEDED |
| DRAFT | U7 DECLINE | DECLINED | reviewLevel ≠ HIGH；须原因；有效 APPROVED 不变 |
| DRAFT | U7 APPROVE | SUBMITTED | reviewLevel = HIGH；冻结快照；`submitted_by` = 操作人 |
| SUBMITTED | U8 APPROVE | APPROVED | 操作人 ≠ `submitted_by`；旧有效版本 → SUPERSEDED |
| SUBMITTED | U8 DECLINE | DECLINED | 操作人 ≠ `submitted_by`；须原因 |
| — | U9 | APPROVED（AUTO_RULE） | 无进行中记录、eligible；`submitted_by = decided_by = NULL`，`created_by` = 触发人，记 `rule_version_id`；旧有效版本 → SUPERSEDED |

- 槽位：DRAFT / SUBMITTED 时 `open_slot=1`，终态置空。APPROVED 时 `effective_slot=1`，被替代时置空。DECLINED 不影响有效 APPROVED。
- 草稿可丢弃吗：本期不提供（O-2）。

## 9. 数据库（`mor_risk_decision`）

### 9.1 `t_client_underwriting`

```sql
CREATE TABLE `t_client_underwriting` (
  `underwriting_id` BIGINT NOT NULL COMMENT '承保记录版本的稳定业务标识（每个版本一行）。',
  `client_id` BIGINT NOT NULL COMMENT '被承保 Client 的稳定业务标识（client-master 角色 ID，跨库值引用，不建外键）。',
  `version_no` INT NOT NULL COMMENT '同一 Client 内从 1 递增的承保版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '承保版本状态。允许值：DRAFT、SUBMITTED、APPROVED、DECLINED、SUPERSEDED。APPROVED 后内容不可变，被新版本批准替代时为 SUPERSEDED。',
  `open_slot` TINYINT(1) NULL COMMENT '进行中占位：DRAFT、SUBMITTED 为 1，其他为 NULL；保证同一 Client 至多一个进行中版本。',
  `effective_slot` TINYINT(1) NULL COMMENT '有效结论占位：当前有效的 APPROVED 为 1，其他为 NULL；保证同一 Client 至多一个有效承保结论。',
  `supersedes_underwriting_id` BIGINT NULL COMMENT '本版本批准后替代的上一有效版本 ID；首个版本或未替代时为空。',
  `decision_source` VARCHAR(32) NULL COMMENT '结论来源。允许值：MANUAL、AUTO_RULE；未决定时为空。',
  `rule_version_id` BIGINT NULL COMMENT 'AUTO_RULE 时使用的自动承保规则版本 ID；人工结论为空。',
  `client_display_name` VARCHAR(255) NOT NULL COMMENT '快照时 Client 的法定名称或个人姓名，仅供列表展示，冻结后不随主档变化。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '快照时 Client 编号，仅供列表展示与搜索。',
  `client_type` VARCHAR(16) NOT NULL COMMENT '快照时 Client 类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '快照时 Client 入驻辖区代码。取值来源：client-master 辖区包；非固定枚举。',
  `crr` VARCHAR(16) NULL COMMENT '快照时尽调客户风险评级。允许值：LOW、MEDIUM、HIGH；存量尽调未评为空，按 MEDIUM 处理。',
  `review_level` VARCHAR(16) NOT NULL COMMENT '决定复核要求的风险级别：crr 非空时等于 crr，为空时为 MEDIUM。允许值：LOW、MEDIUM、HIGH。HIGH 须四眼复核。',
  `dd_tier` VARCHAR(32) NULL COMMENT '快照时尽调档次。允许值：SIMPLIFIED、STANDARD、ENHANCED。',
  `mcc_code` CHAR(4) NULL COMMENT '快照时运营确认的主 MCC，四位数字保留前导零；草稿可空，提交必填。取值来源：Platform MCC 参考数据；非固定枚举。',
  `industry_code` VARCHAR(64) NULL COMMENT '快照时运营确认的主行业代码。取值来源：Platform 行业参考数据；非固定枚举。',
  `network_high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '快照时该 MCC 是否被卡组织公布为高风险（Platform t_ref_mcc.network_high_risk）。1 是，0 否。',
  `expected_monthly_volume` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计月交易金额，币种为 volume_currency。',
  `volume_currency` CHAR(3) NULL COMMENT '预计月交易金额与客单价的 ISO 4217 币种。取值来源：ISO 4217；非固定枚举。',
  `expected_ticket` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计单笔金额，币种为 volume_currency。',
  `fulfillment_type` VARCHAR(32) NULL COMMENT '快照时申报的交付方式。允许值：INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE；存量申请未采集为空。',
  `fulfillment_days` SMALLINT NULL COMMENT '快照时申报的预售交付天数 1—365；仅 PRE_ORDER 有值。',
  `website_url` VARCHAR(2048) NULL COMMENT '快照时申报的网站。',
  `refund_policy_url` VARCHAR(2048) NULL COMMENT '快照时申报的退款政策链接。',
  `expected_payment_methods` VARCHAR(512) NULL COMMENT '快照时申报的期望支付方式（commercial capability_code 规范化逗号串），原样冻结。',
  `source_case_id` BIGINT NULL COMMENT '输入来源：compliance 入驻尽调案件 ID；草稿可空，提交必填。',
  `source_case_version` BIGINT NULL COMMENT '输入来源：读取时的尽调案件版本。',
  `source_application_id` BIGINT NULL COMMENT '输入来源：client-master 入驻申请 ID。',
  `source_draft_revision` BIGINT NULL COMMENT '输入来源：读取时的申请资料版本 draft_revision。',
  `source_classification_confirmed_time` DATETIME(6) NULL COMMENT '输入来源：运营确认 MCC / 行业分类的 UTC 微秒时间；为空表示分类未确认，不能提交。',
  `observation_days` SMALLINT NULL COMMENT '承保设定的观察期天数 1—365；提交必填，默认 30。',
  `observation_transactions` INT NULL COMMENT '承保设定的观察期交易笔数 1—100000；提交必填，默认 100；以先到者为准由 REQ-04 解释。',
  `require_deposit` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求最低保证金（Risk 决策事实；下发 compliance 控制与执行归 REQ-18）。1 是，0 否。',
  `deposit_amount` DECIMAL(24,8) NULL COMMENT '要求的最低保证金金额，> 0；不要求时为空。',
  `deposit_currency` CHAR(3) NULL COMMENT '最低保证金 ISO 4217 币种；不要求时为空。取值来源：ISO 4217；非固定枚举。',
  `deposit_blocks_collection` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '保证金未到账前是否建议阻塞收款；只有 require_deposit=1 时可为 1。执行另由 REQ-04 / REQ-18 决定。',
  `suggested_settlement_days` SMALLINT NULL COMMENT '结算周期建议 T+N 的 N（0—90）；未给出为空。',
  `high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否属于需卡组织高风险登记的 MCC（默认取 network_high_risk_mcc，可人工改并写理由）；登记事实归 channel-connector。1 是，0 否。',
  `high_risk_mcc_reason` VARCHAR(500) NULL COMMENT 'high_risk_mcc 与默认值不同时的理由。',
  `estimated_exposure` DECIMAL(24,8) NULL COMMENT '估算敞口金额（B10，参考值）：expected_monthly_volume × exposure_risk_days / 30，保留 2 位；不可算为空。',
  `exposure_risk_days` SMALLINT NULL COMMENT '估算敞口使用的风险天数（交付方式 + T+N，T+N 为空按 0）；不可算为空。',
  `limit_note` VARCHAR(500) NULL COMMENT '额度说明（不设逐 Client 额度，见需求 Q-4）。',
  `underwriting_note` VARCHAR(2000) NULL COMMENT '承保说明：交付周期与拒付暴露评估及其他判断。',
  `handover_note` VARCHAR(500) NULL COMMENT '交接给商务的备注。',
  `decline_reason` VARCHAR(500) NULL COMMENT 'DECLINED 时的拒绝原因；其他状态为空。',
  `revision_reason` VARCHAR(500) NULL COMMENT '由修订创建的版本的修订原因；首个版本与其他新建版本为空。',
  `review_comment` VARCHAR(500) NULL COMMENT 'HIGH 复核时复核人填写的意见；可空。',
  `created_by` BIGINT NOT NULL COMMENT '建草稿或触发自动承保的 Operations 账号 ID。',
  `submitted_by` BIGINT NULL COMMENT '提交人 Operations 账号 ID；自动承保为空。',
  `submitted_time` DATETIME(6) NULL COMMENT '提交（输入快照冻结）的 UTC 微秒时间；自动承保为生成时间。',
  `decided_by` BIGINT NULL COMMENT '作出 APPROVED / DECLINED 的 Operations 账号 ID；LOW / MEDIUM 等于提交人，HIGH 为复核人，自动承保为空。',
  `decided_time` DATETIME(6) NULL COMMENT '作出结论的 UTC 微秒时间。',
  `superseded_time` DATETIME(6) NULL COMMENT '被新版本替代的 UTC 微秒时间；未替代为空。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1，每次条件更新加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`underwriting_id`),
  UNIQUE KEY `uk_client_underwriting_ver` (`client_id`, `version_no`) COMMENT '保证业务唯一性：client_id、version_no',
  UNIQUE KEY `uk_client_underwriting_open` (`client_id`, `open_slot`) COMMENT '同一 Client 至多一个进行中版本',
  UNIQUE KEY `uk_client_underwriting_effective` (`client_id`, `effective_slot`) COMMENT '同一 Client 至多一个有效结论',
  KEY `idx_client_underwriting_list` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time（列表与待复核）',
  KEY `idx_client_underwriting_decided` (`status`, `decided_time`) COMMENT '支持访问路径：status、decided_time（本月已批准 / 已拒绝统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 承保记录：每个版本一行，提交时冻结输入快照与来源坐标；APPROVED 不可变，修订产生新版本并替代旧版本；Risk 最低保证金要求的决策事实';
```


### 9.2 自动承保规则

```sql
CREATE TABLE `t_underwriting_rule_version` (
  `rule_version_id` BIGINT NOT NULL COMMENT '自动承保规则版本的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '从 1 递增的规则版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '规则版本状态。允许值：DRAFT、ACTIVE、RETIRED；同时至多一个 ACTIVE。',
  `active_slot` TINYINT(1) NULL COMMENT 'ACTIVE 为 1，其他为 NULL。',
  `excluded_mcc_codes` VARCHAR(1024) NULL COMMENT '风控额外排除（不可自动承保）的 MCC，四位数字、升序、英文逗号分隔；为空表示只排除卡组织高风险。',
  `activated_time` DATETIME(6) NULL COMMENT '置为 ACTIVE 的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`rule_version_id`),
  UNIQUE KEY `uk_uw_rule_version_no` (`version_no`) COMMENT '保证业务唯一性：version_no',
  UNIQUE KEY `uk_uw_rule_version_active` (`active_slot`) COMMENT '至多一个 ACTIVE 版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本（CRR=LOW、币种月流水阈值、非高风险 MCC）；ACTIVE 后内容不改，新阈值以新版本 DML 发布';

CREATE TABLE `t_underwriting_rule_threshold` (
  `threshold_id` BIGINT NOT NULL COMMENT '阈值行的稳定业务标识。',
  `rule_version_id` BIGINT NOT NULL COMMENT '所属自动承保规则版本 ID。',
  `currency` CHAR(3) NOT NULL COMMENT '预计月流水币种（ISO 4217）；没有阈值的币种不能自动承保。取值来源：ISO 4217；非固定枚举。',
  `monthly_volume_limit` DECIMAL(24,8) NOT NULL COMMENT '可自动承保的预计月流水上限（含），> 0。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`threshold_id`),
  UNIQUE KEY `uk_uw_rule_threshold` (`rule_version_id`, `currency`) COMMENT '保证业务唯一性：rule_version_id、currency'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本的币种月流水阈值';
```

初始化 DML（`risk-decision-initial-data.sql` 追加，固定 ID 低于 Snowflake 范围）：

```sql
INSERT INTO `t_underwriting_rule_version` (`rule_version_id`, `version_no`, `status`, `active_slot`, `excluded_mcc_codes`, `activated_time`, `create_time`, `update_time`) VALUES
  (290001, 1, 'ACTIVE', 1, NULL, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000');
INSERT INTO `t_underwriting_rule_threshold` (`threshold_id`, `rule_version_id`, `currency`, `monthly_volume_limit`, `create_time`, `update_time`) VALUES
  (290101, 290001, 'USD', 50000.00000000, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000');
```

（初值待风控确认，O-1。）

### 9.3 SQL 文件与 PDManer

| 文件 | 内容 |
|---|---|
| `paytech/risk-decision/docs/database/sql/changes/2026-10-29-client-underwriting.sql` | 3 张表 `CREATE TABLE IF NOT EXISTS` + 规则 v1 与 USD 阈值 `INSERT … ON DUPLICATE KEY UPDATE`（不改已存在行）；可重跑 |
| 同目录 `…-rollback.sql` | 预检：`t_client_underwriting` 有行时 SIGNAL 停止（承保记录是审计依据，有数据不删，需人工决定）；否则 DROP 3 表 |
| `risk-decision-schema.sql`、`risk-decision-initial-data.sql` | 同步 |
| `paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql`（如存在） | 同步；本机 auto 库 `mor_risk_decision` 人工执行前滚 |
| `risk-decision.pdma.json` | 直接编辑：新增 3 个实体（新 UUID；VARCHAR / BIGINT / DECIMAL / DATETIME / TINYINT / SMALLINT 的 `baseType` 取同库已有同类型字段），索引同 DDL；新建图组"承保"或放入 control 图组 |
| `RISK_DECISION_PHYSICAL_DECISIONS.md`、`agent-docs/database/RISK_COMPLIANCE_DATABASE.md` | 记 3 表、槽位规则、快照列、与 compliance `t_settlement_control` 的边界 |
| 需求目录 `db/ddl.sql`、`db/dml.sql` | 注释索引，指向上述执行文件与注册 DML |

### 9.4 Platform 注册块 143（`mor_platform`）

在 `platform-support-initial-data.sql` 末尾追加（路由、资源 DISABLED，角色授权 REVOKED，与既有块一致）；需求目录 `db/platform/registration-143.sql`（插入）、`registration-143-activate.sql`、`registration-143-rollback.sql`。执行前在 mor-test 复核 `…143001—…143999` 无行、代码与 (方法, 外部路径) 未被占用。

| 表 | ID | 内容 |
|---|---|---|
| `t_permission` | 143101 / 143102 / 143103 | `RISK_UNDERWRITING_READ`（`RISK_UNDERWRITING` / READ / LOW）、`RISK_UNDERWRITING_MANAGE`（MANAGE / HIGH）、`RISK_UNDERWRITING_APPROVE`（APPROVE / HIGH），ACTIVE |
| `t_role_permission` | 143201—143203 | 管理员角色 `900000000000000510` × 上述 3 项，REVOKED（激活脚本置 ACTIVE）。业务角色在角色管理中组合时须同时勾选 `RISK_DECISION_CONFIG_READ`（风控目录权限，见 S-11 补充），块 143 不为其建授权行 |
| `t_console_resource` | 143301 | `OPERATIONS_RISK_UNDERWRITING`，MENU，父 `900000000000000565`，"承保"，`/risk/underwriting`，component `OPERATIONS_RISK_UNDERWRITING`，权限 143101，sort 90，DISABLED |
| | 143302 / 143303 | BUTTON：`OPERATIONS_RISK_UNDERWRITING_MANAGE`（"维护"，143102）、`OPERATIONS_RISK_UNDERWRITING_APPROVE`（"复核"，143103），父 143301 |
| `t_gateway_route` | 143401—143412 | 下表；target `900000000000000001`（ops-bff）、`OPERATIONS_JWT`、`HUMAN_PERMISSION`、cookie DROP / NONE、`idempotency_mode=NONE`、body 接收 5000、响应 10000、DISABLED |
| `t_gateway_route_header` | 143501—143552 | GET 5 条 × 2（authorization CORS_REQUEST_ALLOWED、origin FORWARD_REQUEST）；POST / PUT 7 条 × 6（再加 content-type CORS + FORWARD、idempotency-key CORS + FORWARD），同 127416 / 127420 |
| `t_gateway_route_query_param` | 143001—143008 | 143401：status、crr、decisionSource、keyword、clientId、pendingReviewForMe、page、size |
| `t_gateway_guard_rule` | 143601—143672 | 每路由 6 条：GET 与 U11 预览 RATE_LIMIT 主体 60 / IP 60 / 路由 600 每 60 秒；写命令主体 10 / IP 30 / 路由 300；CONCURRENCY 主体 2 / IP 3 / 路由 50 |

| route | code | 方法 | 外部路径（`/api/operations` + 下列）→ 下游同路径去前缀 | 权限 | body |
|---|---|---|---|---|---|
| 143401 | `operations-risk-underwriting-list` | GET | `/risk-underwriting/v1/underwritings` | READ | 0 |
| 143402 | `operations-risk-underwriting-detail` | GET | `/risk-underwriting/v1/underwritings/{underwritingId}` | READ | 0 |
| 143403 | `operations-risk-underwriting-workspace` | GET | `/risk-underwriting/v1/clients/{clientId}/workspace` | READ | 0 |
| 143404 | `operations-risk-underwriting-history` | GET | `/risk-underwriting/v1/clients/{clientId}/underwritings` | READ | 0 |
| 143405 | `operations-risk-underwriting-create` | POST | `/risk-underwriting/v1/clients/{clientId}/underwritings` | MANAGE | 16384 |
| 143406 | `operations-risk-underwriting-save` | PUT | `/risk-underwriting/v1/underwritings/{underwritingId}` | MANAGE | 16384 |
| 143407 | `operations-risk-underwriting-submit` | POST | `/risk-underwriting/v1/underwritings/{underwritingId}/submission` | MANAGE | 16384 |
| 143408 | `operations-risk-underwriting-review` | POST | `/risk-underwriting/v1/underwritings/{underwritingId}/review` | APPROVE | 4096 |
| 143409 | `operations-risk-underwriting-auto` | POST | `/risk-underwriting/v1/clients/{clientId}/auto-underwritings` | MANAGE | 1024 |
| 143410 | `operations-risk-underwriting-revision` | POST | `/risk-underwriting/v1/underwritings/{underwritingId}/revision` | MANAGE | 1024 |
| 143411 | `operations-risk-underwriting-preview` | POST | `/risk-underwriting/v1/previews` | MANAGE | 16384 |
| 143412 | `operations-risk-underwriting-statistics` | GET | `/risk-underwriting/v1/underwriting-statistics` | READ | 0 |

合计 153 行。激活脚本：角色授权 3、资源 3、路由 12 置 ACTIVE；之后重新发布 Gateway 快照。先离线编译快照（DISABLED 路由同样参与编译）。

## 10. ops-bff 与运行时登记

### 10.1 operations-console-bff

| 项 | 设计 |
|---|---|
| 客户端 | `obff/integration/RiskUnderwritingClient` + Impl，经 `configuration/RiskClientConfiguration.java`（`risk-decision-service`）创建 `ClientUnderwritingApi` 客户端；`RiskOwnerResponseShapeValidator` 登记新响应形状 |
| 工作台 `GET /risk-underwriting/v1/clients/{clientId}/workspace` | `UnderwritingWorkspaceProcessor`：1）`OnboardingAdminClient.client(clientId)` 取申请列表，选 `status=APPROVED` 中 `submittedTime` 最新的申请及其 `dueDiligenceCaseId`（没有 → `inputStatus=DD_NOT_APPROVED`）；2）并行（总 3 秒）复用 `ClientReviewRiskClient.detail(caseId)`、`ClientReviewCctClient.application(caseId)`、`ClientReviewCctClient.classifications(caseId)`、`PlatformReferenceDataClient.mccsByCodes`、`RiskUnderwritingClient.current(clientId)`；3）组装 `UnderwritingInputBody`；4）调用 U11 预览。响应：`client`（ID、名称、类型、辖区）、`input` + `inputStatus`（`READY` / `DD_NOT_APPROVED` / `CLASSIFICATION_NOT_CONFIRMED` / `SOURCE_UNAVAILABLE`，及各来源可用性）、`preview`、`current`（U3）、`canSubmit` |
| 命令转发 | U5 / U6 / U7 / U9 / U10：页面 Body 只含 `decision`、`expectedVersion`、`outcome`、`declineReason`、`revisionReason`；ops-bff 严格解析（未知字段 → 422），按工作台步骤 1—3 **重新取输入**并附 `input` 转发；任一来源不可用 → 503（不提交）。U8：原样转发 |
| 列表与统计 | U1 / U12 转发；列表与详情的 `submittedBy`、`decidedBy`、`createdBy` 由 ops-bff 现有 `obff/service/processor/OperatorLabelResolver.java`（Platform `GET /operations-accounts/v1/labels`，每次 ≤ 50 个、失败回退）换成显示名，响应加 `submittedByLabel` / `decidedByLabel` / `createdByLabel` |
| 预览转发 U11 | 页面发 `suggestedSettlementDays`（与可选 `decision` 字段），ops-bff 用最近一次工作台组装的输入（重新取，同命令）调用 U11 |
| 列表 / 详情 / 历史 | 原样转发；`pendingReviewForMe=true` 时 ops-bff 把当前操作人账号 ID 填入 `pendingReviewFor` |
| 错误 | `OperationsErrorStatusResolver` 映射 `RiskUnderwritingErrorCode`（404 / 409）；上游 Problem 按现有 BFF 规则转换 |

### 10.2 data-query（发布时登记，运营 API，非 DML）

1. DBA（mor-test root，主会话执行）：`GRANT SELECT ON mor_risk_decision.* TO 'data_query_demo_reader'@'%';`（与其他 6 库一致；本机 local-environment 对应只读账号同样授权）。
2. 运营后台登记数据源 `MOR_RISK_DECISION_REPLICA`（MYSQL、host `mysql`、3306、`mor_risk_decision`、账号同上，密码沿用 Vault 中该账号），连接检查通过后启用。
3. 登记模板 `GOLIVE_UNDERWRITING_APPROVED`：参数 `clientId`（INTEGER，必填）；约束 `pagingMode=NONE`、`maxRows=1`、`timeoutSeconds=5`；正文（交付文本 `requirements/20261029/risk-decision-承保记录/db/data-query-templates.sql`）：

```sql
SELECT
  IF(e.underwriting_id IS NOT NULL, 1, 0) AS satisfied,
  e.require_deposit AS out_requireDeposit,
  e.deposit_blocks_collection AS out_depositBlocksCollection,
  e.high_risk_mcc AS out_highRiskMcc,
  e.suggested_settlement_days AS out_suggestedSettlementDays,
  CASE
    WHEN e.underwriting_id IS NOT NULL THEN NULL
    WHEN l.status = 'SUBMITTED' THEN 'UNDERWRITING_PENDING_REVIEW'
    WHEN l.status = 'DECLINED' THEN 'UNDERWRITING_DECLINED'
    ELSE 'UNDERWRITING_NOT_STARTED'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN t_client_underwriting e ON e.client_id = #{clientId} AND e.effective_slot = 1
LEFT JOIN (
  SELECT status FROM t_client_underwriting WHERE client_id = #{clientId} ORDER BY version_no DESC LIMIT 1
) l ON 1 = 1;
```

REQ-05 动作模板输出声明：`requireDeposit` BOOLEAN、`depositBlocksCollection` BOOLEAN、`highRiskMcc` BOOLEAN、`suggestedSettlementDays` NUMBER。

### 10.3 REQ-04 读取（本需求交付，同进程）

`rd/control/underwriting/domainservice/service/ClientUnderwritingQueryService.findEffective(long clientId)` → `Optional<EffectiveUnderwriting>`（`underwritingId`、`versionNo`、`observationDays`、`observationTransactions`、`requireDeposit`、`depositAmount`、`depositCurrency`、`depositBlocksCollection`、`suggestedSettlementDays`、`highRiskMcc`、`decidedTime`）。本需求只提供接口与测试，不接入决策流。

## 11. 页面与文案

### 11.1 页面状态与原型对应

原型在 `operations-console-web/docs/operations-console.pen`（y 99660 一排），导出图在 [prototypes/](./prototypes/)。

| 原型（节点） | 页面状态 | 路由 | 接口 | 关键内容与对齐说明 |
|---|---|---|---|---|
| 原型 风控 · 承保 01 承保列表（`jJ8w3`，[图](./prototypes/01-承保列表.png)） | 列表 | `/risk/underwriting` | U1、U12 | 状态页签：全部 / 草稿 / 待复核 / 已批准 / 已拒绝 / 已作废（= `status`，已作废即 SUPERSEDED）；搜索（`keyword`）、CRR、来源（`decisionSource`）筛选，另有"待我复核"；列：客户（编号 · 辖区 · 类型，取快照列）、CRR、MCC（卡组织高风险标）、状态、版本、来源（人工 / 自动规则 v{n}）、估算敞口（交付 · T+N）、提交人、复核人（`decidedBy`，ops-bff 换显示名）、更新时间、操作；统计卡（生效中 / 草稿 / 待复核 / 本月已批准 / 本月已拒绝）由 U12 提供（单表 5 个 COUNT，有索引，成本低） |
| 原型 风控 · 承保 02 新建 · 符合自动承保（`h2Gxg3`，[图](./prototypes/02-新建-符合自动承保.png)） | A | `/risk/underwriting/new?clientId=` | 工作台、U11、U9 | 输入（尽调结论、业务信息含 REQ-12 四项、估算敞口与公式）；"自动承保判定"卡显示三个条件（CRR = LOW、流水 ≤ 阈值、MCC 非高风险）+ 规则版本，分别对应 check `CRR_LOW`、`VOLUME_PROVIDED` + `CURRENCY_CONFIGURED` + `VOLUME_WITHIN_THRESHOLD`、`MCC_NOT_NETWORK_HIGH_RISK` + `MCC_NOT_EXCLUDED`；`RULE_CONFIGURED`、`CLASSIFICATION_CONFIRMED` 作为前提只在不通过时显示；标准模板结论只读；按钮"改为人工承保" / "一键自动承保"（U9，记 AUTO_RULE + 规则版本 + 操作人 `created_by`） |
| 原型 风控 · 承保 03 新建 · 人工承保 HIGH（`OrXDB`，[图](./prototypes/03-新建-人工承保-HIGH.png)） | B | 同上 | 工作台、U5 / U6、U7、U11 | 可编辑结论：起始档位为固定文案"验证档 · 观察期满由 REQ-04 推进"（无字段）、观察期天 / 笔、保证金（要求 + 金额 + 未到账前阻塞收款，注"由 REQ-18 执行"）、建议结算周期 T+N（变化即调用 U11 刷新敞口）、卡组织登记（按 MCC 预勾，改动须理由）、额度说明；按钮"保存草稿"（U5 首次 / U6）/"提交复核"（U7 `outcome=APPROVE` → SUBMITTED）；HIGH 提交人无"拒绝"（U7 HIGH 只允许 APPROVE）。LOW / MEDIUM 人工态沿用同一布局，按钮为"提交" / "拒绝" |
| 原型 风控 · 承保 04 待复核 · 复核人视角（`LJnUv`，[图](./prototypes/04-待复核-复核人视角.png)） | C | `/risk/underwriting/{id}` | U2、U8 | 冻结输入 + 输入来源（案件 + 版本、申请 + `draftRevision`、分类确认时间）；结论只读；复核意见（拒绝时必填，U8 `comment`）；"拒绝 / 批准"；提交人本人打开时按钮禁用并提示（页面按 `submittedBy` 判断，服务端以 `RISK_UNDERWRITING_SELF_REVIEW` 兜底） |
| 原型 风控 · 承保 05 已批准详情 · 版本历史（`LDrqc`，[图](./prototypes/05-已批准详情-版本历史.png)） | D | `/risk/underwriting/{id}` | U2、U4、U10 | 只读结论、来源（人工 / 自动 + 规则版本）、敞口公式；版本历史（v2 生效并显示修订原因 `revisionReason`，v1 已作废）；"修订"按钮（MANAGE）弹出修订原因 → U10：结论复制自有效版本，输入按最新来源重新取得 |

### 11.2 文案键（`operations-console-web/src/i18n/en/risk.json` 与 `operations-console-web/i18n/zh-CN/risk.json`；菜单在 `nav.json`）

| Key | en | zh-CN |
|---|---|---|
| `nav.resource.OPERATIONS_RISK_UNDERWRITING` | Underwriting | 承保 |
| `risk.underwriting.title` | Underwriting | 承保 |
| `risk.underwriting.new` | New underwriting | 新建承保 |
| `risk.underwriting.filter.pendingReviewForMe` | Awaiting my review | 待我复核 |
| `risk.underwriting.status.DRAFT` / `SUBMITTED` / `APPROVED` / `DECLINED` / `SUPERSEDED` | Draft / Awaiting review / Approved / Declined / Superseded | 草稿 / 待复核 / 已批准 / 已拒绝 / 已作废 |
| `risk.underwriting.source.MANUAL` / `AUTO_RULE` | Manual / Auto rule | 人工 / 自动规则 |
| `risk.underwriting.input.title` | Inputs (frozen on submit) | 输入快照（提交时冻结） |
| `risk.underwriting.input.crr` / `ddTier` / `mcc` / `industry` / `networkHighRisk` | CRR / Due-diligence tier / MCC / Industry / Card-network high-risk | 客户风险评级 / 尽调档次 / MCC / 行业 / 卡组织高风险 |
| `risk.underwriting.input.crrMissing` | Not rated in due diligence; treated as MEDIUM | 尽调未评 CRR，按 MEDIUM 处理 |
| `risk.underwriting.input.notCollected` | Not collected | 未采集 |
| `risk.underwriting.input.source` | Source: case {caseId} v{caseVersion} · application {applicationId} r{revision} | 来源：案件 {caseId} v{caseVersion} · 申请 {applicationId} r{revision} |
| `risk.underwriting.inputStatus.DD_NOT_APPROVED` | Due diligence isn't approved yet | 尽调尚未批准 |
| `risk.underwriting.inputStatus.CLASSIFICATION_NOT_CONFIRMED` | MCC classification isn't confirmed | MCC 分类尚未确认 |
| `risk.underwriting.inputStatus.SOURCE_UNAVAILABLE` | Some inputs couldn't be loaded. Try again. | 部分输入暂时无法读取，请重试 |
| `risk.underwriting.auto.title` | Auto-underwriting check (rule v{version}) | 自动承保判定（规则 v{version}） |
| `risk.underwriting.auto.check.RULE_CONFIGURED` … `CLASSIFICATION_CONFIRMED` | Rule configured / CRR is LOW / Volume provided / Currency has a threshold / Volume within threshold / MCC not card-network high-risk / MCC not excluded / Classification confirmed | 已配置规则 / CRR 为 LOW / 已申报流水 / 币种有阈值 / 流水不超过阈值 / MCC 非卡组织高风险 / MCC 未被排除 / 分类已确认 |
| `risk.underwriting.auto.apply` | Approve with standard template | 按标准模板承保 |
| `risk.underwriting.auto.manual` | Underwrite manually | 改为人工承保 |
| `risk.underwriting.exposure.title` | Estimated exposure | 估算敞口 |
| `risk.underwriting.exposure.formula` | {volume} × {days} days ÷ 30 | {volume} × {days} 天 ÷ 30 |
| `risk.underwriting.exposure.assumed` | Settlement days not set; counted as 0 | 未填结算周期，按 0 计 |
| `risk.underwriting.exposure.unavailable.VOLUME_MISSING` / `FULFILLMENT_TYPE_MISSING` / `FULFILLMENT_DAYS_MISSING` | Can't estimate: no expected volume / no fulfillment type / no delivery days | 无法估算：未申报流水 / 未采集交付方式 / 未填预售天数 |
| `risk.underwriting.exposure.hint` | Reference only; doesn't block approval. | 仅供参考，不影响审批。 |
| `risk.underwriting.decision.observationDays` / `observationTransactions` | Observation period (days) / Observation period (transactions) | 观察期天数 / 观察期笔数 |
| `risk.underwriting.decision.requireDeposit` / `depositAmount` / `depositBlocksCollection` | Require a security deposit / Deposit amount / Block collection until received | 要求保证金 / 保证金金额 / 到账前阻塞收款 |
| `risk.underwriting.decision.depositHint` | Recorded as Risk's requirement; collection and enforcement come later (REQ-18). | 作为风控要求记录；收取与执行由后续需求（REQ-18）落实。 |
| `risk.underwriting.decision.suggestedSettlementDays` | Suggested settlement (T+N) | 结算周期建议（T+N） |
| `risk.underwriting.decision.highRiskMcc` / `highRiskMccReason` | Needs card-network high-risk registration / Reason for changing the default | 需卡组织高风险登记 / 更改默认值的理由 |
| `risk.underwriting.decision.limitNote` / `underwritingNote` / `handoverNote` | Limit notes / Underwriting notes / Handover notes for Commercial | 额度说明 / 承保说明 / 交接备注（给商务） |
| `risk.underwriting.submit` / `submitForReview` / `saveDraft` / `decline` / `declineReason` | Submit / Submit for review / Save draft / Decline / Reason for declining | 提交 / 提交复核 / 保存草稿 / 拒绝 / 拒绝原因 |
| `risk.underwriting.revisionReason` | Reason for revision | 修订原因 |
| `risk.underwriting.startingTier` | Verification tier · moves on after the observation period (REQ-04) | 验证档 · 观察期满由 REQ-04 推进 |
| `risk.underwriting.stats.effective` / `draft` / `submitted` / `approvedThisMonth` / `declinedThisMonth` | Effective / Drafts / Awaiting review / Approved this month / Declined this month | 生效中 / 草稿 / 待复核 / 本月已批准 / 本月已拒绝 |
| `risk.underwriting.filter.source` / `search` | Source / Search client name or number | 来源 / 搜索客户名称或编号 |
| `risk.underwriting.highNotice` | HIGH risk: a Risk lead must review after you submit. | HIGH 风险：提交后需风控负责人复核。 |
| `risk.underwriting.review.title` / `approve` / `comment` / `commentRequired` | Review / Approve / Review comment / Required when declining | 复核 / 批准 / 复核意见 / 拒绝时必填 |
| `risk.underwriting.review.selfBlocked` | You submitted this underwriting, so someone else must review it. | 你是提交人，须由其他人复核。 |
| `risk.underwriting.history.title` | Versions | 版本历史 |
| `risk.underwriting.revise` | Revise | 修订 |
| `risk.underwriting.error.RISK_UNDERWRITING_OPEN_EXISTS` | This Client already has underwriting in progress. | 该 Client 已有进行中的承保。 |
| `risk.underwriting.error.RISK_UNDERWRITING_VERSION_CONFLICT` | Someone else changed this underwriting. Reload and try again. | 承保已被其他人修改，请刷新后重试。 |
| `risk.underwriting.error.RISK_UNDERWRITING_SELF_REVIEW` | You can't review your own submission. | 不能复核自己提交的承保。 |
| `risk.underwriting.error.RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE` | This Client no longer meets the auto-underwriting rule. | 该 Client 已不满足自动承保条件。 |

最终措辞以原型确认为准；随 operations-console 语言包发布。

## 12. 测试与验证计划

### 12.1 risk-decision auto（本机 `mor_risk_decision`，HTTP 真实入口）

新测试 `paytech-risk-decision-test/.../auto/ext/http/control/ClientUnderwritingHttpTest`（必要时按场景拆分），Builder 构造 `UnderwritingInputBody` / `UnderwritingDecisionBody`，Checker 断言响应与表行。

| 验收 | 场景 |
|---|---|
| AC-02 | 预览：全部通过 → eligible；逐项不满足（CRR MEDIUM / 为空、无流水、EUR 无阈值、超阈值、卡组织高风险、排除清单命中、分类未确认、无 ACTIVE 规则）各给出对应 check；U9 满足 → APPROVED（AUTO_RULE、`rule_version_id`、`submitted_by` / `decided_by` 为空）；不满足 → 409 `RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE` |
| AC-03 | LOW / MEDIUM：建草稿 → 保存 → 提交 APPROVE → APPROVED；DECLINE 无原因 → 422、有原因 → DECLINED；B5 字段校验（观察期范围、保证金三项联动、`depositBlocksCollection` 无保证金 → 422、高风险改默认无理由 → 422、T+N 91 → 422） |
| AC-04 | HIGH：提交 → SUBMITTED；提交人复核 → 409 `RISK_UNDERWRITING_SELF_REVIEW`；他人批准 / 拒绝；HIGH 提交 DECLINE → 409 |
| AC-05 | 修订：有效 v1 → U10 建 v2 DRAFT（结论复制、输入为最新来源、无 `revisionReason` → 422）→ 提交前 U3 `effective` 仍为 v1 → 批准后 v1 SUPERSEDED、`effective` 为 v2；v2 被拒绝时 v1 仍有效 |
| AC-06 | 快照冻结：提交后再保存 → 409 STATE_INVALID；详情返回来源坐标 |
| AC-07 | 提交缺 `mccCode` / 来源坐标 / 分类确认 → 422；`crr` 为空 → `reviewLevel=MEDIUM`、不能自动 |
| AC-09 | 并发：同 Client 两个建草稿只有一个成功（另一个 409 OPEN_EXISTS 或 CONCURRENT_REQUEST）；`expectedVersion` 过期 → 409 |
| AC-12 | 敞口四种交付方式、T+N 为空按 0、各不可算原因；保存与提交冻结、详情返回 |
| REQ-04 接口 | `ClientUnderwritingQueryService.findEffective` 有 / 无有效版本 |
| AC-08（模板） | 测试用本库只读执行模板正文（替换参数），覆盖已批准 / 待复核 / 拒绝 / 未开始四态与输出值 |

### 12.2 operations-console-bff auto

用现有测试外部端点（compliance、client-master、Platform、risk-decision）配置 DTO：工作台四种 `inputStatus`、最近已批准申请选择（含退回重提多轮）、来源超时降级、命令转发时重新取输入且忽略页面伪造的 `input`（严格解析 422）、预览转发、`pendingReviewForMe` 填充、错误映射（404 / 409 / 503）。

### 12.3 operations-console-web

单测：输入面板（未采集、CRR 缺失）、自动判定清单、敞口格式化、表单联动校验（保证金、高风险理由）、复核区禁用（本人）、状态与来源标签；`tsc`、build、i18n 校验。

### 12.4 本机联调与 mor-test

- 本机 e2e：`mor_risk_decision_v2e2e` 执行前滚 SQL；`mor_platform_v2e2e` 块 143 + 激活；risk-decision / ops-bff JAR 替换；ops-web 本地构建。本机 MySQL 只读账号授权 `mor_risk_decision_v2e2e` 并登记数据源与模板。
- 浏览器 en / zh-CN × 1440 / 390 走原型 01—05 五态（LOW 自动、MEDIUM 人工、HIGH 提交与另一账号复核、修订替换、无 CRR 存量 Client）。
- 用 REQ-05 的 DATA_QUERY 动作消费模板一次（满足 / 待复核两态）。
- mor-test（单独授权）：同序发布（第 13 节）；API 验收与浏览器 en / zh-CN × 1440 / 390；验收数据（承保记录）保留或按发布记录清理。

## 13. 实施切分与发布

| # | 任务 | 执行者 | 依赖 | 产物 |
|---|---|---|---|---|
| T1 | DDL 与回滚、初始化 DML、`schema.sql`、PDManer、物理决策与数据库文档；本机 auto 库执行前滚 | risk-decision 后端 | — | 9.1—9.3 |
| T2 | 领域与 Processor：`rd/control/underwriting`（实体、Repository / Mapper、`UnderwritingService`、`UnderwritingAutoRule`、`UnderwritingExposure`、状态机、锁）、`ClientUnderwritingQueryService`；`rd-api` 接口与 Body、错误码；Controller 与错误映射 | risk-decision 后端 | T1 | 第 8 节 |
| T3 | risk-decision auto 测试（12.1）；全模块串行跑 | risk-decision 后端 | T2 | 测试报告 |
| T4 | ops-bff：客户端、工作台 Processor、命令 / 预览 / 列表转发、严格解析、错误映射、响应形状登记、测试（12.2） | ops-bff | T2 | 10.1 |
| T5 | 注册块 143（初始化 DML 追加 + 需求目录插入 / 激活 / 回滚）、离线编译 | ops-bff 执行者或主会话 | T4 | 9.4 |
| T6 | ops-web（**原型 01—05 确认前不开始**）：路由与菜单能力、列表页、承保页五态、API 与解码、文案 en / zh-CN、单测 | ops-web | T4 + 原型 | 第 11 节 |
| T7 | 语言包（operations-console）与发布计划 | 主会话或 ops-web 执行者 | T6 | `release/i18n-bundles/` |
| T8 | data-query 模板文本与登记步骤、本机验证 | risk-decision 后端 | T1 | 10.2 |
| T9 | 文档：决定 3.3 输出名、`CURRENT_IMPLEMENTATION.md`（实现后） | risk-decision 后端 | T3 | — |

发布（mor-test，单独授权，主会话执行 root SQL 与 XXL / 数据源登记）：

| 顺序 | 动作 | 回滚 |
|---|---|---|
| 1 | `mor_risk_decision` 前滚 DDL + 规则 DML | 回滚 SQL（有承保数据时拒绝） |
| 2 | risk-decision 新镜像（固定 sha256） | 旧镜像（新表保留无害） |
| 3 | operations-console-bff 新镜像 | 旧镜像 |
| 4 | 块 143 插入 → 离线编译 → 激活 → 重新发布 Gateway 快照 | 激活回退 → 删除块（子表先删） |
| 5 | operations-console-web 新镜像 + 语言包 | 旧镜像 / 上一语言包 |
| 6 | DBA 授权 `mor_risk_decision` → 登记 `MOR_RISK_DECISION_REPLICA` → 登记模板 | 停用模板与数据源 → 撤销授权 |

compliance、client-master、commercial、workflow：不发布。

### 13.1 待主会话确认

| 编号 | 问题 | 本稿取舍 |
|---|---|---|
| O-1 | 自动承保规则初值：USD 月流水 50,000、排除清单为空 | 按此写入 v1；风控给正式值后以新版本 DML 发布 |
| O-2 | 草稿能否丢弃 | 本期不提供（草稿占用 `open_slot`，可继续编辑后提交或拒绝）；如原型需要"放弃草稿"，加一个 MANAGE 接口与路由 |
| O-3 | 列表展示 Client 名称、编号、辖区、类型：快照列 `client_display_name` / `client_no` / `jurisdiction_code` / `client_type`（冻结），而不是列表时向 client-master 批量查 | 按快照列；名称变更后列表显示快照时值，详情可链到 Client 详情 |
| O-8 | 原型 01 的统计卡 | 保留：新增 U12（单表 5 个 COUNT，`idx_client_underwriting_list` / `_decided` 覆盖）与路由 143412；"本月"按 UTC 自然月 |
| O-9 | HIGH 草稿提交人不能拒绝、本期也不能丢弃草稿 | 承保员若认为应拒绝，提交复核并在承保说明写明，由复核人拒绝；如需"放弃草稿"按 O-2 另加 |
| O-4 | 自动承保的审计身份：`created_by` = 触发人、`submitted_by` / `decided_by` 为空、`decision_source=AUTO_RULE` | 按此（"审批人 = 系统规则"） |
| O-5 | 预览与工作台都需要 MANAGE 吗：只读用户打开承保页时不调用预览（只看冻结值） | 工作台对无 MANAGE 的用户不返回 `preview`；预览路由绑定 MANAGE |
| O-6 | 选案件规则：取 `APPROVED` 申请中提交时间最新者的当前案件 | 按此；同一 Client 多次入驻（被拒后重申）以最近一次批准为准 |
| O-7 | 菜单排序 90（风控菜单最后） | 按此 |
