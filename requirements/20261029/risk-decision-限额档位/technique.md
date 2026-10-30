# risk-decision：限额档位 · 技术方案

> 状态：**阶段 2 已确认（2026-10-29，S-01—S-13 按建议，S-05 改为按币种分别设额度）；第 7—13 节为阶段 3 评审稿（2026-10-29，设计稿，未改代码 / 未执行 SQL）**。页面原型由主会话在 `operations-console.pen` 绘制（"原型 风控 · 限额档位 01—05"）。
> 依据：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) 第 3.3 节（`GOLIVE_LIMIT_TIER`）；需求 [requirement.md](./requirement.md)（阶段 1 已确认：Q-1—Q-14 按建议）。

路径缩写：`rd/` = `paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/`；`rd-sql` = `paytech/risk-decision/docs/database/sql/risk-decision-schema.sql`；`obff/` = `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/`。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| 决策流执行 | `rd/decision/domain/runtime/DecisionEngine.java`：<br>- `:63-88`：从 START 沿连线走到 END；RULE_SET 节点执行规则集（同版本复用结果）；<br>- `:106-117` 出口规则：CONDITIONAL 按优先级取首个成立，否则 ELSE / UNCONDITIONAL；<br>- `:120-131`：连线条件只接受统一变量操作数（`PredicateEvaluator`）；<br>- `:196-212`：变量按来源取值（INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST）；<br>- `:99-104`：END 节点带 `decisionCode`（ALLOW / REVIEW / DENY）与原因码 |
| 流程结构表 | `rd-sql:456-491`：节点类型只有 START / RULE_SET / END；连线类型 UNCONDITIONAL / CONDITIONAL / ELSE，`condition_json` 为受控条件树。`rd-sql:504-543`：`t_risk_variable.source_type` 含 VELOCITY、LIST；`rd/decision/domain/variable/VariableDefinitionValidator.java:58` 要求 LIST 变量为 BOOLEAN |
| 执行发布 | `rd/decision/service/processor/ActivateExecutionReleaseProcessor.java:30`：激活时原 ACTIVE 进入 SUPERSEDED，候选经 REVIEW_PENDING、APPROVED 进入 ACTIVE。`ListExecutionReleasesProcessor.java:18`："不提供重新激活已替代发布：**回滚以旧决策流版本新建执行发布**" |
| mor-test 生效发布 | `PAYMENT_ATTEMPT_RISK` 发布 11：流程 `PAY_ATTEMPT_FLOW` v1，结构为 start → `block` 规则集 → 条件连线 to_deny / to_review / to_3ds → `auth` 规则集 → allow / allow_3ds / deny / review。`CHECKOUT_PREPARE` 发布 15：`CHECKOUT_BASELINE_ALLOW` v1（start → allow）。两者都不按档位分支 |
| 限额义务 | `rd-sql:107-120` `t_decision_point_obligation`：按事件点版本 + 决定，类型含 RESERVE_LIMIT_GROUP。`rd-sql:926-947` `t_decision_point_limit_binding`：绑在义务上，**不按流程分支**。`rd-sql:903-925` `t_limit_version`：全局版本。mor-test 无限额版本，`t_limit_position` 0 行 |
| 实时累计 | `rd/decision/service/query/FeatureValueReader.java`：<br>- `:28-41`：先算全部累计，再算名单命中；<br>- `:57-79`：窗口为 `[评估时间 − window_seconds, 评估时间)`，是**滑动窗口**，不是自然日 / 月；`SUM_AMOUNT` 只累计与**本次输入币种**相同的事件，`include_current` 时加上本次金额。<br>`rd-sql:153-164`：事件码为 PAYMENT_ATTEMPT_ASSESSED / _REJECTED、CHECKOUT_ASSESSED / _REJECTED，锚定主体含 CLIENT |
| 累计事件写入 | `rd/decision/domain/runtime/RuntimeEvaluationFacts.java:199-208`：**每次评估都写 `*_ASSESSED`（含被拒的）**，非 ALLOW 时再写 `*_REJECTED`。<br>写入点 `PaymentRiskFacts.java:66`、`CheckoutRiskFacts.java:61`；事件码定义 `rd/decision/domain/enums/VelocityEventCode.java:15-24`。<br>CLIENT 在两个事件点都可作累计与名单主体（`DecisionPointAuthoringDescriptor.java:54-62`）。<br>mor-test `t_velocity_event`：CHECKOUT_ASSESSED 45、PAYMENT_ATTEMPT_ASSESSED 10、PAYMENT_ATTEMPT_REJECTED 2 |
| 名单 | `rd/control/core/enums/RiskListPurpose.java:8-11`：只有 BLOCK、ALLOW；表结构 `rd-sql:812-852`。<br>命中判定：`FeatureValueReader.java:92-96` 调用 `RiskListMembershipService.isMember`；`rd/control/service/impl/RiskListMembershipServiceImpl.java:37-44` 在名单停用或主体类型不符时视为未命中；`RiskListEntryRepositoryImpl.java:65-67` 用 `countEffective(listId, subjectType, subjectKey, at)` **直接查库、无缓存**，成员变化即时生效。<br>人工维护入口：`rd/control/service/processor/CreateRiskListProcessor.java`、`AddRiskListEntryProcessor.java`、`DeactivateRiskListEntryProcessor.java`。<br>写入能力：`rd/control/service/RiskListService.java:15-21`（`addEntry` / `deactivateEntry`）；`rd/control/domainservice/repository/RiskListEntryRepository.java:19-41`（`insert`、`findActive`、`close`） |
| 名单 / 累计变量的创建 | LIST、VELOCITY 变量经现有变量管理创建：`RiskVariable.java:34,59` 在创建变量时同时提交 Feature 参数，由代码生成 Feature 版本与实现摘要（`FeatureHandlers.java:23-35`）；名单定义须先存在 |
| 输入主体 | `PaymentRiskInput.java:101-103`：CLIENT 键 = clientId。`CheckoutRiskInput.java:45,76`：金额取 `net_amount` |
| 支付成功事实 | `rd-sql:853-873` `t_payment_attempt_fact` 设计为 Payment 状态投影，mor-test 0 行；事件目录中 `PaymentSucceeded` 的消费者只有 acquiring、card（`foundation/paytech-messaging/catalog/events.yaml:11-16`） |
| REQ-03 批准链路 | `rd/control/service/impl/UnderwritingServiceImpl.java:66-83`：`decide`（LOW / MEDIUM 提交与 HIGH 复核）与 `autoApprove` 各自在一个 `TransactionTemplate` 中先 `supersedePrevious` 再写结论。<br>调用点 `SubmitUnderwritingProcessor.java:80`、`ReviewUnderwritingProcessor.java:70`、`AutoUnderwritingProcessor.java:63`，都持有 `UnderwritingLocks.client(clientId)` 资源锁（`UnderwritingLocks.java:12-23`，500ms / 30s）。<br>有效结论读取：`ClientUnderwritingQueryServiceImpl`（`findEffective`） |
| ops-bff 可复用读取 | `obff/integration/QualificationAdminClient.java:27`：`subjectQualifications(CLIENT, clientId)`。<br>`ClientReviewRiskClient`：案件 CRR。<br>`RiskUnderwritingClient`：REQ-03 当前承保。<br>`CommercialConsoleQueryClient.java:32`（`listAgreements`）+ `CommercialAgreementClient`：合同详情含开通项 `executionStatus`。<br>`OperationsAccountLabelClient`：操作人显示名。<br>`RiskListClient`：名单页 |
| 注册与权限 | mor-test `t_gateway_route` 最大 `900000000000143412`，`t_permission` 最大 `…143103`（REQ-03 块 143 已部署）。`RISK_UNDERWRITING_READ / _MANAGE / _APPROVE` 为 ACTIVE。初始化 DML 无 144 段 → **下一块 144** |
| data-query | mor-test 只读账号授权为**表级**：`GRANT SELECT ON mor_risk_decision.t_client_underwriting`。数据源 `MOR_RISK_DECISION_REPLICA` ACTIVE；`GOLIVE_UNDERWRITING_APPROVED` 等 4 条 GOLIVE 模板 ACTIVE |
| 存量 | mor-test：有 ENABLED 开通项的 CLIENT 合同对应 Client **4 个**（`237575207592763393`、`237573476041465857`、`237573267559391233`、`237573040244891649`）；有效承保 2 条；TIER 名单 0。本机 `mor_commercial_v2e2e` 3 个，`mor_commercial` 0 个 |
| 事件 | `AgreementCapabilityStatusChanged` v1 已发布、无消费者（`events.yaml:112-124`）；阶段 1 Q-2 已定不消费 |

## 2. 现状发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | 决策流用"条件连线 → 规则集 / END"表达分支；条件操作数是风控变量，LIST 变量是布尔名单命中 | 档位分支 = START 后按四个 LIST 变量（是否在 TIER_VERIFICATION 名单等）建条件连线，ELSE 指向 END(DENY，原因"无交易额度")；**无需改引擎** |
| F-02 | 硬限额绑定在事件点版本的义务上，与分支无关；mor-test 无限额版本 | 按档位额度只能用"分支下规则集 + 累计变量"实现（阶段 1 Q-3 成立）；硬限额保持不用 |
| F-03 | 累计窗口是滑动窗口；`SUM_AMOUNT` 只累计与本次同币种的事件，并可计入本次金额 | "日 / 月"额度实为滚动 24 小时 / 30 天；额度按币种分别计算，USD 额度只约束 USD 交易；非 USD 交易需在档位规则中显式处理（S-05） |
| F-04 | **每次评估都写 `*_ASSESSED`，含被拒的评估** | 若用 ASSESSED 累计做额度，超额被拒的那一笔也会计入已用额度，之后额度内的交易也被拒。必须改为只累计放行金额：新增 `PAYMENT_ATTEMPT_ALLOWED` / `CHECKOUT_ALLOWED` 事件码，在 ALLOW 时写入（S-04）。仍按"放行"而非"支付成功"计，偏保守 |
| F-05 | 名单命中直接查库、无缓存 | 档位变更批准后下一次评估即生效。名单停用会使所有成员"未命中"、全部走 ELSE 拒绝，所以 TIER 名单须禁止停用 |
| F-06 | 名单成员可经运营接口人工增删；`list_purpose` 只有 BLOCK / ALLOW；名单之间无互斥 | 新增 `TIER` 用途。TIER 名单只由 DML / 迁移创建：`CreateRiskListProcessor` 拒绝创建；`AddRiskListEntryProcessor`、`DeactivateRiskListEntryProcessor` 对其拒绝；名单停用接口（如有）同样拒绝。成员由档位服务经 `RiskListEntryRepository` 同事务维护 |
| F-07 | LIST / VELOCITY 变量与其 Feature 版本由变量管理在代码中生成（含实现摘要） | 四个档位变量与累计变量不用 DML 直接插 Feature 表，而由风控按配置说明在现有变量页创建；DML 只建四张名单（S-07） |
| F-08 | 回滚只能用旧流程版本新建发布再激活 | 激活带档位分支的发布即开关；回滚路径须在发布记录里预先准备（旧流程 `PAY_ATTEMPT_FLOW` v1、`CHECKOUT_BASELINE_ALLOW` v1） |
| F-09 | REQ-03 的三条批准路径各自在 `UnderwritingServiceImpl` 的本地事务里写结论，均持有 Client 级承保锁；`superseded == null` 即"首个有效结论" | 档位初始化挂在 `decide`（结论为 APPROVED 时）与 `autoApprove` 的同一事务内：无有效前序版本且该 Client 无档位时，插入 VERIFICATION 并加入 TIER_VERIFICATION 名单；不额外取锁，用档位表 `client_id` 唯一约束兜底（S-03） |
| F-10 | Risk 无支付成功事实 | 观察期笔数用新增的 `PAYMENT_ATTEMPT_ALLOWED` 事件按 CLIENT、`occurred_time ≥ 观察开始时间` 计数（近似）；天数 = now − 观察开始时间 |
| F-11 | mor-test 有 4 个可交易 Client、只有 2 条有效承保，且无任何档位 | 激活带档位分支的发布前必须先把这 4 个迁移为 STANDARD，否则全被拒；本机 e2e 库有 3 个 |
| F-12 | data-query 只读账号是表级授权（只到 `t_client_underwriting`） | `GOLIVE_LIMIT_TIER` 须给新档位表加同样的表级 `GRANT SELECT`。模板只读档位表，`observationDue` 只按天数判定（笔数不进模板，避免授权累计事件表，S-09） |
| F-13 | ops-bff 已有资格、案件 CRR、承保、合同与开通项执行状态、操作人显示名的读取客户端 | 复核清单系统项与档位页聚合不需要新增 Owner 接口 |
| F-14 | 下一个注册块为 144；REQ-03 的承保权限已存在 | 档位的四个权限新建；菜单挂在风控父资源下 |

## 3. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 档位事实 | 无 | 每 Client 一个当前档位 + 历史 | 未实现 | 新表（S-01） |
| R2 申请与四眼 | 无 | 申请 / 批准 / 驳回 / 撤回，按 CRR 分级 | 未实现；Risk 不重检权限 | 申请表 + 两个审批接口分绑权限（S-06） |
| R3 承保初始化 | REQ-03 批准事务（F-09） | APPROVED 且无档位 → VERIFICATION | 未实现 | 挂入 `decide` / `autoApprove` 同事务（S-03） |
| R4 名单投影 | 无 TIER 用途（F-06） | 四张系统名单，禁止人工改 | 未实现 | 枚举 + 注释 + 校验 + 同事务维护 |
| R5 额度 | 硬限额不分支（F-02）；累计含被拒（F-04） | 按档位单笔 / 日 / 月 | 累计口径不对 | 新增 ALLOWED 事件码 + 配置说明 + TEST 示例（S-04、S-05、S-07） |
| R6 观察期进度 | 无成功事实（F-10） | 天数 / 笔数进度、到期提示 | 无计数来源 | 用 ALLOWED 事件计数（近似） |
| R7 页面 | 无 | 列表 + Client 档位页 | 未实现 | ops-bff 聚合复用现有客户端（F-13） |
| R8 迁移 | 无档位（F-11） | 存量可交易 Client → STANDARD | 未实现 | 迁移入口（S-08） |
| R9 模板 / 注册 | 表级授权（F-12）；块 144 空闲（F-14） | 模板与注册 | 未实现 | 加表级授权；用块 144 |
| B8 决策流约定 | 两个事件点现有流程不分支（第 1 节） | 第一层按档位分支，ELSE 拒绝 | 配置未做 | 新流程版本（配置，非代码） |
| B9 迁移先于激活 | — | 先迁移再激活 | — | 发布清单强制顺序（F-08） |

## 4. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 是否改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| risk-decision | 档位与额度 Owner | 档位表、申请表；`RiskListPurpose.TIER` 与名单接口校验；档位 Service / Processor / API；REQ-03 批准事务挂初始化；新增 `PAYMENT_ATTEMPT_ALLOWED`、`CHECKOUT_ALLOWED` 事件码与写入；观察期进度计算；迁移入口；auto 测试 | 代码：是。<br>模型：是，新表 2；改注释：`t_risk_list.list_purpose`、`t_velocity_feature_definition.event_code`、`t_velocity_event.event_code`。<br>DML：四张 TIER 名单 | 新事件码只多写一行事件，不改已有事件语义。现有发布不引用新名单与新事件码，**行为不变**，直到激活新流程 |
| risk-decision（配置，非代码） | 风控配置 | 四个 LIST 变量；按档位的 VELOCITY 变量（ALLOWED、24h / 30 天、CLIENT、含本次）与金额常量；两个事件点的新流程版本（档位分支 + ELSE 拒绝 + 各档位额度规则集 + 原有规则）；TEST 发布与激活 | 配置：是（运营页面执行，单独授权） | 激活即生效；回滚 = 用旧流程版本新建发布并激活（F-08） |
| operations-console-bff | 页面聚合 | 档位列表转发；Client 档位页聚合（档位 + 申请 + 观察期进度、承保、资格与 CRR、合同与开通项、操作人显示名）；申请 / 审批 / 撤回转发；错误映射与响应形状登记 | 代码：是 | 依赖现有 compliance、commercial、risk 读取；任一系统项来源不可用时清单显示"无法读取"，不阻止查看，但阻止提交上线复核 |
| operations-console-web | 运营页面 | "风控 → 限额档位"列表、Client 档位页；原型先行 | 代码：是；Pencil：是 | — |
| Platform | 注册、语言包、data-query | 块 144：四权限、菜单与按钮、路由；语言包；`GOLIVE_LIMIT_TIER` 模板登记 | 注册 DML：是；运行时登记：是 | 离线编译快照 |
| mor-test MySQL（DBA） | 只读授权 | `GRANT SELECT ON mor_risk_decision.<档位表>` | 是（发布动作） | 表级，范围最小 |
| acquiring / payment | 调用 Risk | 不改代码；新流程激活后，无档位、超额、RESTRICTED 的交易被拒 | 否 | 激活前须完成迁移与配置核对 |
| commercial / compliance / client-master | — | 不改（只读） | 否 | — |
| 文档 | — | risk-decision 数据库文档；档位配置说明（含 TEST 示例与回滚步骤）；决定 3.3；`CURRENT_IMPLEMENTATION.md` | 是 | — |

## 5. 待决事项

| 编号 | 问题 | 建议 |
|---|---|---|
| S-01 | 档位表形态 | **`t_client_limit_tier`**（每 Client 一行，`client_id` 唯一）存：<br>- 当前档位、生效时间、`version`；<br>- 来源（UNDERWRITING_INIT / CHANGE_REQUEST / MIGRATION）与来源 ID；<br>- 观察开始时间与观察目标天数 / 笔数：进入 OBSERVATION 时从有效承保冻结，无承保默认 30 / 100。<br>**`t_client_limit_tier_change`** 记变更历史与四眼：<br>- 每次申请一行，状态 PENDING / APPROVED / REJECTED / WITHDRAWN；`pending_slot` 保证每 Client 至多一个待审批；<br>- 存 from / to、理由、复核清单快照（结构化列 + 人工勾选项）、申请人 / 批准人 / 时间；<br>- 初始化与迁移也写一行：来源不同、直接 APPROVED，批准人为空或为执行人 |
| S-02 | 档位名单投影 | 四张系统名单 `TIER_VERIFICATION`、`TIER_OBSERVATION`、`TIER_STANDARD`、`TIER_RESTRICTED`（CLIENT 主体，`list_purpose=TIER`，DML 建，ACTIVE）。<br>档位变化时同事务 `close` 旧档位成员、`insert` 新档位成员（`source_type` 用系统来源，原因写申请 ID）。<br>TIER 名单禁止人工创建、增删成员、停用 |
| S-03 | 初始化挂点与锁 | 在 `UnderwritingServiceImpl.decide`（结论 APPROVED）与 `autoApprove` 的事务内调用本模块 `ClientLimitTierService.initializeIfAbsent(clientId, underwritingId, now)`。<br>批准（首次或修订）时该 Client 无档位行即插入 VERIFICATION 与名单成员；已有档位不改（阶段 5 修订 F-1，用户选 a：原写法"仅首个有效承保"会让 REQ-04 上线前已批准、未迁移的 Client 永远没有档位）。<br>不取档位锁，靠 `uk(client_id)` 兜底；并发冲突时视为已初始化，不让承保失败。<br>档位命令用独立锁 `risk-limit-tier:client:{clientId}`，不嵌套承保锁 |
| S-04 | 额度累计口径 | 新增事件码 `PAYMENT_ATTEMPT_ALLOWED`、`CHECKOUT_ALLOWED`：评估结论为 ALLOW 时，与 ASSESSED 同事务写入。<br>档位额度变量用 ALLOWED + `SUM_AMOUNT` + CLIENT + `include_current=1`。<br>放行后支付失败的金额仍计入（保守）；精确按成功计需消费 `PaymentSucceeded`，另立项 |
| S-05 | 额度口径与币种（**2026-10-29 用户定方案 A**） | 单笔 = 本次金额；"日" = 滚动 86400 秒；"月" = 滚动 2592000 秒。<br>**每个档位按币种分别配置**单笔 / 日 / 月额度（USD、AED、SAR、EGP…）；不做汇率换算，不同币种分别累计、互不合并（累计 Feature 本就只累计同币种，`FeatureValueReader.java:57-79`）。<br>某档位未配置某币种：VERIFICATION / OBSERVATION / RESTRICTED 拒绝，STANDARD 放行（不设额度）。<br>统一折算为 USD 的额度需要汇率来源，另立需求。写入配置说明 |
| S-06 | 审批分级 | 四个权限：`RISK_LIMIT_TIER_READ`、`RISK_LIMIT_TIER_REQUEST`、`RISK_LIMIT_TIER_APPROVE`、`RISK_LIMIT_TIER_APPROVE_ELEVATED`。<br>两个审批接口：`…/approval`（绑 APPROVE）与 `…/elevated-approval`（绑 APPROVE_ELEVATED）。<br>risk-decision 按申请冻结的 CRR 校验：LOW 可走任一接口；MEDIUM / HIGH / 无 CRR 只能走 elevated。驳回同样分两个接口；申请人 ≠ 批准人 |
| S-07 | 决策流配置交付方式 | 代码只交付名单与事件码能力；变量、规则集、流程版本由风控在现有页面按《档位配置说明》创建，TEST 环境配置作为验收步骤（单独授权激活）。<br>说明内附两个事件点的目标流程图、变量清单、规则表与回滚步骤 |
| S-08 | 迁移 | risk-decision 提供迁移入口：内部接口或一次性 DML 生成器，阶段 3 定，倾向内部接口 `POST /limit-tiers/migrations`。按 Client ID 列表设 STANDARD，来源 MIGRATION；幂等，已有档位的跳过。<br>Client 列表由发布执行人只读导出 commercial（ENABLED 开通项的 CLIENT 合同）。<br>顺序：代码上线 → 迁移 → 风控配置 → 激活发布 |
| S-09 | `GOLIVE_LIMIT_TIER` | 只读档位表；参数 `clientId`、`targetTier`。<br>输出 `tier`、`observationDue`（OBSERVATION 且已过天数 ≥ 目标天数）；`missing` 三码。<br>发布时 DBA 加表级授权 |
| S-10 | 观察期进度 | 天数 = 当前时间 − `observation_started_time`。<br>笔数 = `t_velocity_event_subject`（CLIENT、clientId）关联 `PAYMENT_ATTEMPT_ALLOWED` 且时间 ≥ 观察开始。<br>任一达到目标即"到期待确认"。列表"到期待确认"筛选只按天数（可索引），页面详情再显示笔数 |
| S-11 | 结账点是否也做额度 | 结账点只做"无档位 / RESTRICTED 拒绝 + 单笔上限"；日 / 月额度在支付尝试点（扣款点）判定，避免结账与支付重复累计 |
| S-12 | 延长观察 | 作为 OBSERVATION → OBSERVATION 的申请，批准时重置 `observation_started_time`，可改目标天数 / 笔数 |
| S-13 | 页面原型 | 阶段 3 出图：<br>- 档位列表；<br>- Client 档位页四态：VERIFICATION 申请上线复核、OBSERVATION 进度与到期确认、待审批（审批人视角）、RESTRICTED 与历史 |

## 6. 工作量与风险

| 项 | 判断 |
|---|---|
| 规模 | **M（偏上）**：<br>- risk-decision：新表 2、档位服务与 6—8 个接口、名单校验、REQ-03 事务挂点、新事件码写入、迁移入口；<br>- ops-bff：1 个聚合 + 转发；<br>- ops-web：2 页；<br>- 注册块 1、模板 1；<br>- 另有一份风控配置（非代码，单独授权激活） |
| 最大风险 | 激活带档位分支的发布前，漏迁移任何可交易 Client → 交易被拒。缓解：发布清单把"迁移结果核对（所有 ENABLED 开通项 Client 都有档位）"作为激活前置检查，并保留旧流程版本回滚发布 |
| 第二风险 | 累计按"放行"而非"成功"：失败支付占用额度（保守）；验证档额度小时客户体验受影响。在配置说明与页面中注明 |
| 第三风险 | 档位名单被停用或误改，会让成员全部走拒绝分支。靠 TIER 名单禁止人工维护 / 停用兜底 |
| 依赖 | REQ-03（已交付）；无阻塞 |
| 需要页面原型的点 | 见 S-13 |

**确认点**：用户确认 F-01—F-14、S-01—S-13 与第 4 节影响范围后进入阶段 3。

> **阶段 2 已确认（2026-10-29）**：S-01—S-13 按建议，S-05 改为方案 A（按档位 × 币种分别设额度，不换汇、分币种累计）。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-30）

**一句话规模**：
- **系统**：risk-decision（代码 + 风控配置）、operations-console-bff、operations-console-web、Platform 注册与 data-query 登记；acquiring / payment / commercial / compliance 不改代码。
- **接口**：按业务 Method + Path 去重新增 13 个（risk-decision 12 个 L1—L12，其中迁移 L12 为内部接口不经 Gateway；ops-bff 对外 11 个：10 个同路径转发 + 档位工作台聚合 1 个），调整 0 个。
- **数据库**：新增表 2 张（`t_client_limit_tier` 19 列、`t_client_limit_tier_change` 35 列）；修改旧表注释 3 处（`t_risk_list.list_purpose`、`t_risk_list_entry.source_type`、`t_velocity_feature_definition.event_code`），无结构变化。
- **初始化 DML**：4 行（四张 TIER 名单）。
- **注册 DML 块 145**：权限 4、角色授权 4、控制台资源 4、路由 11、路由头 46、查询参数 6、守卫 66，共 141 行。
- **data-query**：模板 1 + 表级授权 1。
- **风控配置（非代码，运营页面）**：每个事件点 1 个新流程版本、4 个档位名单变量、2 个累计变量、按档位 × 币种的额度常量与币种集合。
- **页面原型**：5 张（"原型 风控 · 限额档位 01—05"）。

已读规范同 REQ-03 阶段 3；另读 `DecisionEngine`、`PredicateEvaluator`、`FeatureValueReader`、`RuntimeEvaluationFacts`、`ExecutionSnapshotReader`（第 1 节证据）。

本地原子写入与事务外步骤：

| 用例 | 事务外 | 本地事务（`mor_risk_decision`） |
|---|---|---|
| 承保批准且无档位 | — | REQ-03 现有事务内追加：插入档位 VERIFICATION、变更记录、TIER_VERIFICATION 名单成员 |
| 档位工作台（ops-bff） | 并行读 risk 档位 / 承保、compliance 资格、commercial 合同与开通项、操作人显示名 | 无 |
| 申请 / 撤回 / 审批 / 驳回 | 无（系统清单项由 ops-bff 随申请带入） | 锁 Client → 校验 → 写申请；批准时同事务更新档位、关闭旧名单成员、插入新名单成员 |
| 迁移 | — | 每个 Client 一个短事务（幂等） |
| 评估（运行期） | — | 评估事实写入事务内追加 `*_ALLOWED` 累计事件（ALLOW 时） |

## 7. 方案总览

```
            承保 APPROVED 且无档位（REQ-03 decide / autoApprove 事务内）
                         │ initializeIfAbsent
                         ▼
 t_client_limit_tier (每 Client 一行) ──同事务投影──▶ t_risk_list_entry
   VERIFICATION ─申请+审批─▶ OBSERVATION ─申请+审批─▶ STANDARD          TIER_VERIFICATION / TIER_OBSERVATION /
   任意 ─申请+审批─▶ RESTRICTED ─申请+审批─▶ 先前或更低档位              TIER_STANDARD / TIER_RESTRICTED（list_purpose=TIER）
   OBSERVATION ─申请+审批─▶ OBSERVATION（延长）
 t_client_limit_tier_change（申请、清单快照、四眼、初始化 / 迁移记录）

 运行期（风控配置，不改引擎）：
 CHECKOUT_PREPARE / PAYMENT_ATTEMPT_RISK 新流程版本
   START ─[TIER_RESTRICTED 命中]─▶ END(DENY, LIMIT_TIER_RESTRICTED)
         ─[TIER_VERIFICATION 命中]─▶ RULE_SET 档位额度·验证 ─┐
         ─[TIER_OBSERVATION 命中]──▶ RULE_SET 档位额度·观察 ─┼─[额度规则命中 DENY]─▶ END(DENY, LIMIT_EXCEEDED / CURRENCY_NOT_ALLOWED)
         ─[TIER_STANDARD 命中]─────▶ RULE_SET 档位额度·标准 ─┘         └─ELSE─▶ 原流程（block → auth → …）
         ─ELSE──────────────────────▶ END(DENY, LIMIT_TIER_NOT_SET)
   额度规则 = 币种不在本档位允许集合 → DENY；单笔 > 常量 → DENY；ALLOWED 累计（滚动 24h / 30d，含本次）> 常量 → DENY
   （常量为 MONEY + 固定币种：币种不同的比较一律为假，所以每条规则只约束同币种交易）

 评估事实：结论 ALLOW 时追加 PAYMENT_ATTEMPT_ALLOWED / CHECKOUT_ALLOWED 累计事件（含 CLIENT 主体）
 GOLIVE_LIMIT_TIER（data-query，只读 t_client_limit_tier）→ REQ-05 / REQ-07 N10、N11
```

## 8. 接口

### 8.1 risk-decision `rd-api/decision/api/control/ClientLimitTierApi.java`（新增，`@HttpExchange("/risk-limit-tiers/v1")`）

头部与风格同 REQ-03（`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；risk-decision 不重检权限；ID / 金额 / 时间为字符串）。

| # | 方法与路径 | 用途 | 成功 | 路由权限 |
|---|---|---|---|---|
| L1 | `GET /client-tiers?tier&pendingApproval&observationDue&keyword&page&size` | 列表：`tier` 为四档之一，空 = 全部；`pendingApproval=true` 只列有待审批申请的；`observationDue=true` 只列 OBSERVATION 且已过天数 ≥ 目标天数的；`keyword` 匹配客户名称包含或编号 / ID 相等；按 `update_time` 倒序 | 200 `PagingResponseBody<ClientTierSummaryBody>` | READ |
| L2 | `GET /clients/{clientId}/tier` | 当前档位详情（含观察期进度、本档位额度、按币种已用、待审批申请） | 200 `ClientTierDetailResponseBody`；无档位时 `tier=null` | READ（经工作台） |
| L3 | `GET /clients/{clientId}/tier-changes` | 变更历史（倒序，≤ 100） | 200 `ClientTierChangeListResponseBody` | READ |
| L4 | `POST /clients/{clientId}/tier-changes` | 提交变更申请 | 201 `ClientTierChangeResponseBody` | REQUEST |
| L5 | `POST /tier-changes/{changeId}/withdrawal` | 申请人撤回 | 200 | REQUEST |
| L6 | `POST /tier-changes/{changeId}/approval` | 审批通过（仅 `approvalLevel=STANDARD`） | 200 | APPROVE |
| L7 | `POST /tier-changes/{changeId}/elevated-approval` | 审批通过（任意级别） | 200 | APPROVE_ELEVATED |
| L8 | `POST /tier-changes/{changeId}/rejection` | 驳回（仅 STANDARD） | 200 | APPROVE |
| L9 | `POST /tier-changes/{changeId}/elevated-rejection` | 驳回（任意级别） | 200 | APPROVE_ELEVATED |
| L10 | `GET /tier-quotas` | 四档 × 币种额度（由风控常量按命名约定解析，见 8.3） | 200 `TierQuotaListResponseBody` | READ |
| L11 | `GET /tier-statistics` | 列表顶部统计：各档位数量、待审批、观察期到期待确认 | 200 `TierStatisticsResponseBody` | READ |
| L12 | `POST /internal/tier-migrations` | 存量迁移（内部，不注册 Gateway 路由；只接受私网调用，`X-Caller-Service` 白名单配置 `risk.limit-tier.migration-callers`，默认空即关闭） | 200 `TierMigrationResponseBody` | — |

**请求体**

| 接口 | Body |
|---|---|
| L4 | `{ "expectedTierVersion": Integer, "toTier": "OBSERVATION" \| "STANDARD" \| "RESTRICTED" \| "VERIFICATION", "reason": String(1—1000), "goLiveNote": String?(≤ 2000), "observationTargetDays": Integer?(1—365), "observationTargetTransactions": Integer?(1—100000), "checklist": ChecklistBody? }` |
| `ChecklistBody` | 系统项（ops-bff 填，页面不能提交）：`qualificationStatus`（APPROVED / 其他 / UNAVAILABLE）、`qualificationValidTo`、`underwritingId`、`underwritingVersionNo`、`agreementId`、`agreementStatus`、`enabledPaymentCapabilities`（逗号串）、`pendingPaymentCapabilities`（逗号串）；人工项（页面提交）：`channelRegistrationConfirmed`、`integrationConfirmed`、`productionVerified`、`openIssuesCleared`（布尔） |
| L5 | `{ "expectedVersion": Integer }` |
| L6—L9 | `{ "expectedVersion": Integer, "comment": String? }`；驳回 `comment` 必填（1—500） |
| L12 | `{ "reason": String, "clients": [ { "clientId", "clientDisplayName", "clientNo", "clientType", "jurisdictionCode" } ] }`（≤ 500 个） |

**申请规则（L4）**

| 当前 → 目标 | 额外要求 |
|---|---|
| VERIFICATION → OBSERVATION | `checklist` 必填：系统项须 `qualificationStatus=APPROVED`、有有效承保、`enabledPaymentCapabilities` 非空；四个人工项须全部 true；`goLiveNote` 必填 |
| OBSERVATION → STANDARD | 无（页面展示观察期进度；未到期也可申请，说明理由） |
| OBSERVATION → OBSERVATION | 延长：`observationTargetDays` / `observationTargetTransactions` 可改（缺省沿用），批准时重置观察开始时间 |
| 任意 → RESTRICTED | 无 |
| RESTRICTED → VERIFICATION / OBSERVATION / STANDARD | 目标不得高于进入 RESTRICTED 前的档位（`t_client_limit_tier.previous_tier`） |
| 其他 | 409 `RISK_LIMIT_TIER_TRANSITION_INVALID` |

- **审批级别**：申请时冻结 `crr_snapshot`（取有效承保快照的 CRR，无承保或 CRR 为空按 MEDIUM）；LOW → `approvalLevel=STANDARD`，其余 → `ELEVATED`。
- **ELEVATED 申请的处理**：只能经 L7 / L9 审批；走 L6 / L8 返回 409 `RISK_LIMIT_TIER_ELEVATION_REQUIRED`。
- **批准时的四眼校验**：审批人 ≠ 申请人，否则 409 `RISK_LIMIT_TIER_SELF_APPROVAL`。
- **批准后同事务执行**：
  - 档位改为目标档位、`version + 1`；
  - 进入 OBSERVATION 时写观察开始时间与目标（延长也重置）；
  - 关闭旧 TIER 名单成员（状态 SUPERSEDED），插入新名单成员（`source_type=TIER`、`reason=change:{changeId}`）。

**响应体**

- `ClientTierSummaryBody`：`clientId`、`clientDisplayName`、`clientNo`、`jurisdictionCode`、`clientType`、`crr`（同库读取当前有效承保的 CRR，批量按 clientId 取，无则 null）、`tier`、`effectiveTime`、`sourceType`、`sourceReason`（产生当前档位的变更记录理由）、`observationStartedTime`、`observationDaysElapsed`、`observationTargetDays`、`observationAllowedTransactions`、`observationTargetTransactions`、`observationDue`、`pendingChange`（`{changeId, fromTier, toTier, approvalLevel, requestedBy, requestedTime}` 或 null）、`updatedAt`。列表的放行笔数只对当前页 OBSERVATION 行计算（每行一次索引计数）。
- `ClientTierDetailResponseBody` = Summary + `version` + `previousTier` + `observation`（`{startedTime, daysElapsed, targetDays, allowedTransactions, targetTransactions, due}`）+ `quotas`（本档位各币种 `{currency, single, day, last30Days, usedToday, usedLast30Days}`；阶段 4 修订：原 `rolling24h / rolling30d / used24h / used30d` 改名，口径为 UTC 当日与近 30 个 UTC 自然日）+ `unconfiguredCurrencies`（在任一档位配置过、但本档位未配置的币种，页面在额度表中显示为"未配置"）+ `unconfiguredCurrencyPolicy`（DENY / ALLOW）。
- **"已用"与观察期笔数的计算**（只在详情 L2 计算，列表不算已用）：按 `t_velocity_event_subject` 现有索引 `idx_velocity_subject (subject_type, subject_key, event_code, occurred_time, velocity_event_id)` 取 CLIENT 主体、`PAYMENT_ATTEMPT_ALLOWED`、近 30 天的事件，按主键回表 `t_velocity_event` 后按 `currency` 分组求和（24 小时为其中子集，同一查询条件求和）；观察期笔数为同索引自 `observation_started_time` 起计数。与评估时累计变量走的是同一索引，代价与该 Client 30 天内放行笔数成正比；单 Client 详情可接受，大客户见 O-7。
  > **阶段 4 修订**："已用"改读日放行汇总表 `t_client_allowed_daily_total`（UTC 当日 1 行、近 30 日 ≤ 30 行），与日 / 月额度评估同一口径；观察期笔数仍按放行事件计数。
- `ClientTierChangeResponseBody`：`changeId`、`clientId`、`changeType`（UNDERWRITING_INIT / MIGRATION / REQUEST）、`fromTier`、`toTier`、`status`、`approvalLevel`、`crrSnapshot`、`reason`、`goLiveNote`、`checklist`、`observationTargetDays`、`observationTargetTransactions`、`observationDaysAtRequest`、`observationAllowedAtRequest`（申请时冻结的观察期进度，审批页展示）、`requestedBy`、`requestedTime`、`decidedBy`、`decidedTime`、`decisionComment`、`version`。
- `TierQuotaListResponseBody`：`tiers[]`（`{tier, currencies[{currency, single, day, last30Days}], unconfiguredCurrencyPolicy}`）、`configurationIssues[]`（命名不规范、缺项、币种集合与常量不一致等，供页面提示）。
- `TierStatisticsResponseBody`：`verification`、`observation`、`standard`、`restricted`、`pendingApproval`、`observationDue`。
- `TierMigrationResponseBody`：`results[]`（`{clientId, outcome: CREATED | SKIPPED_EXISTING}`）。

**错误**（新增 `rd-api/decision/api/enums/RiskLimitTierErrorCode`；`RiskApiErrorConfiguration` 与 ops-bff `OperationsErrorStatusResolver` 映射）

| 情形 | HTTP | code |
|---|---|---|
| 字段格式 / 必填 / 范围 | 422 | `VALIDATION_ERROR`（`errors[].field`） |
| Client 无档位（申请时）/ 申请不存在 | 404 | `RISK_LIMIT_TIER_NOT_FOUND` / `RISK_LIMIT_TIER_CHANGE_NOT_FOUND` |
| 不允许的迁移 | 409 | `RISK_LIMIT_TIER_TRANSITION_INVALID` |
| 已有待审批申请 | 409 | `RISK_LIMIT_TIER_CHANGE_PENDING_EXISTS` |
| `expectedTierVersion` / `expectedVersion` 不符 | 409 | `RISK_LIMIT_TIER_VERSION_CONFLICT` |
| 复核清单未满足（系统项不通过或人工项未勾） | 409 | `RISK_LIMIT_TIER_CHECKLIST_NOT_SATISFIED` |
| ELEVATED 申请走普通审批接口 | 409 | `RISK_LIMIT_TIER_ELEVATION_REQUIRED` |
| 审批人是申请人 / 撤回人不是申请人 | 409 | `RISK_LIMIT_TIER_SELF_APPROVAL` / `RISK_LIMIT_TIER_NOT_REQUESTER` |
| 状态不允许（非 PENDING） | 409 | `RISK_LIMIT_TIER_CHANGE_STATE_INVALID` |
| 迁移调用方不在白名单 | 403 | `FORBIDDEN` |
| 资源锁未取得 | 409 | `CONCURRENT_REQUEST` |

### 8.2 名单与累计事件（risk-decision 代码改动）

| 位置 | 改动 |
|---|---|
| `rd/control/core/enums/RiskListPurpose.java` | 新增 `TIER`（相邻常量空一行） |
| `rd/control/.../RiskListEntrySource`（成员来源枚举） | 新增 `TIER` |
| `CreateRiskListProcessor` | `purpose=TIER` → 400 `BUSINESS_CHECK_FAILED`（"档位名单由系统维护"） |
| `AddRiskListEntryProcessor`、`DeactivateRiskListEntryProcessor`（及名单停用入口，如有） | 名单 `list_purpose=TIER` → 同上拒绝 |
| `rd/decision/domain/enums/VelocityEventCode.java` | 新增 `PAYMENT_ATTEMPT_ALLOWED`（"支付尝试放行"，`PAYMENT_ATTEMPT_RISK`，**允许计入本次** = true：含义为"若本次放行后的累计"）与 `CHECKOUT_ALLOWED`（同上，`CHECKOUT_PREPARE`） |
| `rd/decision/domain/runtime/RuntimeEvaluationFacts.velocityFacts` | 增加参数 `allowed`；技术成功且结论 ALLOW 时追加该事件（与 ASSESSED 同主体、同时间）；`PaymentRiskFacts.java:66`、`CheckoutRiskFacts.java:61` 传入新事件码 |
| 变量创建校验 | 累计变量可选新事件码（沿用 `FeatureHandlers` 现有校验：事件所属风控点须与变量风控点一致） |

### 8.3 额度值放在哪（O-1 结论：风控常量 + 命名约定，引擎不改）

已核：`ExecutionSnapshotReader.java:1-26`"流程与规则集按发布冻结的版本读取；**变量读取当前定义与成员**"——CONSTANT 与 COLLECTION 变量在每次评估时读当前值，不随发布冻结；MONEY 常量可固定币种（`currency_code`），`PredicateEvaluator.java:108-129` 对币种不同的金额比较返回假。因此：

| 变量（风控在现有变量页创建） | 类型 | 说明 |
|---|---|---|
| `TIER_IN_VERIFICATION` / `TIER_IN_OBSERVATION` / `TIER_IN_STANDARD` / `TIER_IN_RESTRICTED`（两个事件点各一套，前缀 `PAY_` 用于支付点） | LIST（BOOLEAN） | 对应四张 TIER 名单，主体 CLIENT |
| `PAY_CLIENT_ALLOWED_AMOUNT_24H` / `PAY_CLIENT_ALLOWED_AMOUNT_30D` | VELOCITY，`SUM_AMOUNT`，`PAYMENT_ATTEMPT_ALLOWED`，CLIENT，86400 / 2592000 秒，含本次；MONEY，币种变量 `PAY_INPUT_CURRENCY` | 同币种放行累计（含本次） |
| `TIER_{TIER}_{CCY}_SINGLE` / `_DAY` / `_MONTH`（如 `TIER_VERIFICATION_USD_SINGLE`） | CONSTANT，MONEY，固定币种 `{CCY}` | 额度值；页面经 L10 按此命名解析展示 |
| `TIER_{TIER}_CURRENCIES` | COLLECTION（成员为币种代码） | 该档位已配置额度的币种集合 |

- **页面怎么显示**：L10 由 risk-decision 读取 `TIER_` 前缀常量与集合，按命名解析为档位 × 币种额度；L2 再按 CLIENT 与币种汇总 `PAYMENT_ATTEMPT_ALLOWED` 的近 24 小时 / 30 天放行金额，作为"已用"。
- **生效与权限**：改额度只需修改常量，**下一次评估即生效、无需新发布**；权限沿用 `RISK_VARIABLE_MANAGE`，没有四眼（O-1）。
- **不采用专用额度表的理由**：需要新的变量来源或 Feature 类型，属于引擎改动，不划算。

> **阶段 4 修订（2026-10-30，主会话批准，D-2）**：规则保存时引擎要求比较两侧金额的币种来源相同（`VariableConditionValidator.compatible`），固定币种常量不能与支付金额（币种来源 `PAY_INPUT_CURRENCY`）比较。改为：
> - `TIER_{TIER}_{CCY}_{SINGLE|DAY|MONTH}` 为 `PAYMENT_ATTEMPT_RISK` 点的 MONEY 常量，`currencyVariableCode = PAY_INPUT_CURRENCY`；每条额度规则写成 `PAY_INPUT_CURRENCY EQ '{CCY}' AND <金额或放行累计> GT <常量>`；`TIER_{TIER}_CURRENCIES` 为全局 STRING 集合。
> - L10 / L2 解析时 `{CCY}` 取自常量名；常量币种来源为支付币种或与名称一致的固定币种都接受。
> - 结账点（`CHECKOUT_PREPARE`）只做档位分支 + RESTRICTED 拒绝 + 币种集合检查；单笔 / 日 / 月额度全部在支付尝试点判定。此项修改 S-11 / O-5（原"结账点也做单笔上限"），不建结账点重复常量。
> - 日 / 月额度：CLIENT 金额合计窗口最长 3600 秒、无 30 天窗口；用户选方案 B，按 UTC 自然日汇总，新增统计方式 `DAILY_SUM_AMOUNT`（见末节，已确认实施）。

### 8.4 operations-console-bff

| 项 | 设计 |
|---|---|
| 客户端 | `obff/integration/RiskLimitTierClient` + Impl（`RiskClientConfiguration`，`risk-decision-service`）；响应形状登记 |
| 工作台 `GET /risk-limit-tiers/v1/clients/{clientId}/workspace` | `LimitTierWorkspaceProcessor` 并行（总 3 秒）读取以下来源：<br>- L2 档位详情与 L3 历史；<br>- `RiskUnderwritingClient.current`：有效承保与 CRR；<br>- `QualificationAdminClient.subjectQualifications(CLIENT, clientId)`：资格状态与有效期；<br>- `CommercialConsoleQueryClient.listAgreements`（clientId，CLIENT 合同）+ `CommercialAgreementClient` 合同详情：支付方式开通项的 `executionStatus`；<br>- `OperatorLabelResolver`：操作人显示名。<br>组装复核清单系统项（各项带 `status`：OK / NOT_OK / UNAVAILABLE、来源与链接），返回 `canRequest`、`requestableTargets`（按当前档位与权限） |
| 申请转发 L4 | 页面只发 `toTier`、`reason`、`goLiveNote`、观察目标、四个人工项；ops-bff 严格解析，**重新读取系统项**组装 `checklist` 转发；系统项来源不可用 → 503 |
| 其他转发 | L1（`pendingApproval`、`observationDue`、`keyword` 透传）、L3、L5—L11 原样转发；列表与详情的人员 ID 换显示名 |
| 错误 | 映射 `RiskLimitTierErrorCode` |

**工作台响应体（阶段 4 与 ops-web 约定，2026-10-30）** `GET /risk-limit-tiers/v1/clients/{clientId}/workspace` → 200：

```
{
  "client": { "clientId", "clientNo"?, "displayName"?, "clientType"?, "jurisdictionCode"? },        // 取档位行，无档位时取有效承保快照；不明为 null
  "tier": <L2 ClientTierDetailResponseBody 原样；无档位时 tier 字段为 null>,                         // pendingChange 另加 "requestedByLabel"
  "history": { "items": [ <L3 ClientTierChangeResponseBody 字段平铺 + "requestedByLabel"?, "decidedByLabel"?> ] },  // 申请时间倒序
  "underwriting": { "underwritingId", "versionNo", "crr"|null, "observationDays"|null, "observationTransactions"|null,
                    "suggestedSettlementDays"|null, "decisionSource" } | null,                        // 当前有效承保
  "checklist": {
    "items": [ { "code": "QUALIFICATION"|"UNDERWRITING"|"AGREEMENT"|"CAPABILITIES", "status": "OK"|"NOT_OK"|"UNAVAILABLE", "source" } ],  // 恰好四项，顺序固定
    "facts": { "qualificationStatus"|null, "qualificationValidTo"|null, "underwritingId"|null, "underwritingVersionNo"|null, "crr"|null,
               "agreementId"|null, "agreementStatus"|null, "enabledPaymentCapabilities": [codes], "pendingPaymentCapabilities": [codes] }
  },
  "canRequest": boolean,                       // 当前操作人是否有申请权限（Platform 实时鉴权申请路由）
  "requestableTargets": [tiers]                // 按 B3 与当前档位 / 受限前档位；无申请权限、无档位或已有待审批时为空
}
```

ID、时间为字符串（UTC ISO-8601 `Z`），金额为字符串。L4 页面请求体：`{expectedTierVersion, toTier, reason, goLiveNote?, observationTargetDays?, observationTargetTransactions?, checklist?: {channelRegistrationConfirmed, integrationConfirmed, productionVerified, openIssuesCleared}}`，未知字段或系统项字段被拒（400，沿用 REQ-03 严格解析）。L5—L9 页面不读响应体，成功后重取工作台。

### 8.5 `GOLIVE_LIMIT_TIER`（data-query，发布时登记）

- **授权**：发布时 DBA 执行 `GRANT SELECT ON mor_risk_decision.t_client_limit_tier TO 'data_query_demo_reader'@'%';`，与 REQ-03 的表级授权同法。
- **参数**：`clientId`（INTEGER 必填）、`targetTier`（ENUM：OBSERVATION / STANDARD）。
- **约束**：`pagingMode=NONE`、`maxRows=1`、`timeoutSeconds=5`。

```sql
SELECT
  CASE
    WHEN t.tier IS NULL OR t.tier = 'RESTRICTED' THEN 0
    WHEN #{targetTier} = 'OBSERVATION' AND t.tier IN ('OBSERVATION', 'STANDARD') THEN 1
    WHEN #{targetTier} = 'STANDARD' AND t.tier = 'STANDARD' THEN 1
    ELSE 0
  END AS satisfied,
  t.tier AS out_tier,
  IF(t.tier = 'OBSERVATION' AND t.observation_started_time IS NOT NULL
     AND TIMESTAMPDIFF(DAY, t.observation_started_time, UTC_TIMESTAMP(6)) >= t.observation_target_days, 1, 0) AS out_observationDue,
  CASE
    WHEN t.tier IS NULL THEN 'LIMIT_TIER_NOT_SET'
    WHEN t.tier = 'RESTRICTED' THEN 'LIMIT_TIER_RESTRICTED'
    WHEN (#{targetTier} = 'OBSERVATION' AND t.tier IN ('OBSERVATION', 'STANDARD')) OR (#{targetTier} = 'STANDARD' AND t.tier = 'STANDARD') THEN NULL
    ELSE 'LIMIT_TIER_BELOW_TARGET'
  END AS missing
FROM (SELECT 1 AS anchor) anchor
LEFT JOIN t_client_limit_tier t ON t.client_id = #{clientId};
```

REQ-05 动作模板输出声明：`tier` ENUM（VERIFICATION、OBSERVATION、STANDARD、RESTRICTED）、`observationDue` BOOLEAN。

## 9. 数据库（`mor_risk_decision`）

### 9.1 `t_client_limit_tier`

```sql
CREATE TABLE `t_client_limit_tier` (
  `client_limit_tier_id` BIGINT NOT NULL COMMENT 'Client 限额档位行的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识（client-master 角色 ID，跨库值引用）。',
  `tier` VARCHAR(32) NOT NULL COMMENT '当前限额档位。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED；无行表示无档位（额度为零）。',
  `previous_tier` VARCHAR(32) NULL COMMENT '进入 RESTRICTED 前的档位，用于限制恢复目标；非 RESTRICTED 时为空。',
  `client_display_name` VARCHAR(255) NOT NULL COMMENT '建档时 Client 名称（取承保快照或迁移输入），仅供展示。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '建档时 Client 编号，仅供展示与搜索。',
  `client_type` VARCHAR(16) NOT NULL COMMENT '建档时 Client 类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '建档时 Client 入驻辖区代码。取值来源：client-master 辖区包；非固定枚举。',
  `source_type` VARCHAR(32) NOT NULL COMMENT '当前档位的来源。允许值：UNDERWRITING_INIT、MIGRATION、REQUEST。',
  `source_change_id` BIGINT NOT NULL COMMENT '产生当前档位的变更记录 ID（t_client_limit_tier_change）。',
  `effective_time` DATETIME(6) NOT NULL COMMENT '当前档位生效的 UTC 微秒时间。',
  `observation_started_time` DATETIME(6) NULL COMMENT '观察期开始时间（进入或延长 OBSERVATION 时写入）；非 OBSERVATION 为空。',
  `observation_target_days` SMALLINT NULL COMMENT '观察期目标天数 1—365（从有效承保冻结，无承保默认 30）；非 OBSERVATION 为空。',
  `observation_target_transactions` INT NULL COMMENT '观察期目标放行笔数 1—100000（从有效承保冻结，无承保默认 100）；非 OBSERVATION 为空。',
  `observation_started_by_change_id` BIGINT NULL COMMENT '开始或延长当前观察期的变更记录 ID；非 OBSERVATION 为空。',
  `created_by` BIGINT NULL COMMENT '建档操作人：承保初始化时为承保决定人或触发人，迁移时为执行人。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1，每次档位变化加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`client_limit_tier_id`),
  UNIQUE KEY `uk_client_limit_tier_client` (`client_id`) COMMENT '每个 Client 至多一个当前档位',
  KEY `idx_client_limit_tier_list` (`tier`, `update_time`) COMMENT '支持访问路径：tier、update_time（列表）',
  KEY `idx_client_limit_tier_observation` (`tier`, `observation_started_time`) COMMENT '支持访问路径：观察期到期筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 当前限额档位（交易放量开关的权威事实）；变化与四张 TIER 名单成员在同一事务维护；历史见 t_client_limit_tier_change';
```

### 9.2 `t_client_limit_tier_change`

```sql
CREATE TABLE `t_client_limit_tier_change` (
  `change_id` BIGINT NOT NULL COMMENT '档位变更记录的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识。',
  `change_type` VARCHAR(32) NOT NULL COMMENT '变更来源。允许值：UNDERWRITING_INIT（承保批准时 Client 尚无档位自动建档）、MIGRATION（存量迁移）、REQUEST（运营申请、四眼审批）。',
  `from_tier` VARCHAR(32) NULL COMMENT '变更前档位；建档时为空。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED。',
  `to_tier` VARCHAR(32) NOT NULL COMMENT '目标档位。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED。',
  `status` VARCHAR(32) NOT NULL COMMENT '变更状态。允许值：PENDING、APPROVED、REJECTED、WITHDRAWN；建档与迁移直接为 APPROVED。',
  `pending_slot` TINYINT(1) NULL COMMENT 'PENDING 为 1，其他为 NULL；保证每个 Client 至多一个待审批申请。',
  `approval_level` VARCHAR(16) NULL COMMENT '审批级别：STANDARD（CRR=LOW）或 ELEVATED（MEDIUM、HIGH 或无 CRR）；建档与迁移为空。',
  `crr_snapshot` VARCHAR(16) NULL COMMENT '申请时有效承保快照的 CRR。允许值：LOW、MEDIUM、HIGH；无承保或未评为空（按 ELEVATED）。',
  `underwriting_id` BIGINT NULL COMMENT '建档或申请时的有效承保版本 ID；无承保为空。',
  `reason` VARCHAR(1000) NOT NULL COMMENT '变更理由；建档为"承保批准"，迁移为执行时填写的理由。',
  `go_live_note` VARCHAR(2000) NULL COMMENT '上线复核说明（VERIFICATION → OBSERVATION 必填）。',
  `chk_qualification_status` VARCHAR(32) NULL COMMENT '复核清单：申请时 Client 角色资格状态（APPROVED、其他状态或 UNAVAILABLE）。',
  `chk_qualification_valid_to` DATETIME(6) NULL COMMENT '复核清单：资格有效期。',
  `chk_underwriting_version_no` INT NULL COMMENT '复核清单：有效承保版本号。',
  `chk_agreement_id` BIGINT NULL COMMENT '复核清单：CLIENT 合同 ID。',
  `chk_agreement_status` VARCHAR(32) NULL COMMENT '复核清单：合同状态。',
  `chk_enabled_capabilities` VARCHAR(512) NULL COMMENT '复核清单：执行状态 ENABLED 的支付方式开通项代码，逗号分隔。',
  `chk_pending_capabilities` VARCHAR(512) NULL COMMENT '复核清单：未 ENABLED 的支付方式开通项代码，逗号分隔。',
  `chk_channel_registration` TINYINT(1) NULL COMMENT '复核清单人工项：渠道报备已确认。',
  `chk_integration` TINYINT(1) NULL COMMENT '复核清单人工项：技术接入已确认。',
  `chk_production_verified` TINYINT(1) NULL COMMENT '复核清单人工项：生产交易验证已完成。',
  `chk_open_issues_cleared` TINYINT(1) NULL COMMENT '复核清单人工项：未结事项（筛查待复核、证件到期等）已处理。',
  `observation_target_days` SMALLINT NULL COMMENT '进入或延长观察期时设定的目标天数；其他变更为空。',
  `observation_target_transactions` INT NULL COMMENT '进入或延长观察期时设定的目标放行笔数；其他变更为空。',
  `observation_days_at_request` SMALLINT NULL COMMENT '申请时已过的观察期天数（当前档位为 OBSERVATION 时冻结）；其他为空。',
  `observation_allowed_at_request` INT NULL COMMENT '申请时观察期内已放行笔数（当前档位为 OBSERVATION 时冻结）；其他为空。',
  `requested_by` BIGINT NULL COMMENT '申请人 Operations 账号 ID；承保建档为空，迁移为执行人。',
  `requested_time` DATETIME(6) NOT NULL COMMENT '申请（或建档、迁移）的 UTC 微秒时间。',
  `decided_by` BIGINT NULL COMMENT '审批人 Operations 账号 ID；须不同于 requested_by；建档与迁移为空。',
  `decided_time` DATETIME(6) NULL COMMENT '批准、驳回或撤回的 UTC 微秒时间。',
  `decision_comment` VARCHAR(500) NULL COMMENT '审批意见；驳回必填。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_client_tier_change_pending` (`client_id`, `pending_slot`) COMMENT '每个 Client 至多一个待审批申请',
  KEY `idx_client_tier_change_client` (`client_id`, `requested_time`) COMMENT '支持访问路径：client_id、requested_time（历史）',
  KEY `idx_client_tier_change_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time（待审批统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 限额档位的变更记录：承保建档、存量迁移与运营申请（含复核清单快照与四眼审批）；追加为主，只有 PENDING 行可变';
```

### 9.3 其他结构与数据

| 项 | 内容 |
|---|---|
| 注释变更（MODIFY COLUMN，原类型原可空） | `t_risk_list.list_purpose` 允许值加 `TIER`；`t_risk_list_entry.source_type` 允许值加 `TIER`；`t_velocity_feature_definition.event_code` 允许值加 `PAYMENT_ATTEMPT_ALLOWED`、`CHECKOUT_ALLOWED` |
| 初始化 DML（`risk-decision-initial-data.sql` 与增量文件） | `t_risk_list` 4 行：`300001` `TIER_VERIFICATION`（"限额档位 · 验证"）、`300002` `TIER_OBSERVATION`、`300003` `TIER_STANDARD`、`300004` `TIER_RESTRICTED`；`supported_subject_type=CLIENT`、`matching_mode=EXACT`、`list_purpose=TIER`、`status=ACTIVE`、`version=1` |
| 增量文件 | `paytech/risk-decision/docs/database/sql/changes/2026-10-30-client-limit-tier.sql`：建 2 表（IF NOT EXISTS）→ 注释 → 4 名单（`INSERT … ON DUPLICATE KEY UPDATE`）；`…-rollback.sql`：两表有行或任一 TIER 名单有成员时 SIGNAL 停止（审计与执行依据）；否则删名单与表、恢复注释 |
| PDManer / 文档 | `risk-decision.pdma.json` 直接编辑（2 实体 + 3 处注释）；`RISK_DECISION_PHYSICAL_DECISIONS.md`、`agent-docs/database/RISK_COMPLIANCE_DATABASE.md`；测试 `init/ddl.sql` 同步；需求目录 `db/ddl.sql`、`db/dml.sql` 为索引 |
| 本机 auto 库 | 人工执行前滚（本机授权内） |

### 9.4 Platform 注册块 145（`mor_platform`）

> **阶段 4 修订（2026-10-30）**：块 144 已被并行需求（联合查询）占用，REQ-04 改用块 145；ID 规则不变（`9000000000001451xx` 权限 … `1456xx` 守卫、`1450xx` 查询参数）。已在本机 `mor_platform`、`mor_platform_v2e2e` 只读核对七张表 145 段为 0，主仓库初始化 DML 无 145 段。

初始化 DML 追加（资源 / 路由 DISABLED，角色授权 REVOKED），需求目录 `db/platform/registration-145.sql`、`-activate.sql`、`-rollback.sql`；执行前在 mor-test 复核 `…145001—…145999` 空闲。

| 表 | ID | 内容 |
|---|---|---|
| `t_permission` | 145101—145104 | `RISK_LIMIT_TIER_READ`（LOW）、`RISK_LIMIT_TIER_REQUEST`（HIGH）、`RISK_LIMIT_TIER_APPROVE`（HIGH）、`RISK_LIMIT_TIER_APPROVE_ELEVATED`（HIGH），资源类型 `RISK_LIMIT_TIER` |
| `t_role_permission` | 145201—145204 | 管理员角色 `900000000000000510` × 4，REVOKED |
| `t_console_resource` | 145301 | MENU `OPERATIONS_RISK_LIMIT_TIERS`，父 `900000000000000565`，"限额档位"，`/risk/limit-tiers`，权限 145101，sort 95 |
| | 145302—145304 | BUTTON：`OPERATIONS_RISK_LIMIT_TIER_REQUEST`（"申请变更"）、`OPERATIONS_RISK_LIMIT_TIER_APPROVE`（"审批"）、`OPERATIONS_RISK_LIMIT_TIER_APPROVE_ELEVATED`（"高级审批"） |
| `t_gateway_route` | 145401—145411 | 见下表；target ops-bff、`OPERATIONS_JWT`、`HUMAN_PERMISSION`、`idempotency_mode=NONE`、5000 / 10000 |
| `t_gateway_route_header` | 145501—145546 | GET 5 条 × 2；POST 6 条 × 6（同 REQ-03） |
| `t_gateway_route_query_param` | 145001—145006 | 145401：tier、pendingApproval、observationDue、keyword、page、size |
| `t_gateway_guard_rule` | 145601—145666 | 每路由 6 条：GET 主体 60 / IP 60 / 路由 600 每 60 秒；POST 主体 10 / IP 30 / 路由 300；CONCURRENCY 2 / 3 / 50 |

| route | code | 方法 | 外部路径（`/api/operations` + 下列） | 权限 | body |
|---|---|---|---|---|---|
| 145401 | `operations-risk-limit-tier-list` | GET | `/risk-limit-tiers/v1/client-tiers` | READ | 0 |
| 145402 | `operations-risk-limit-tier-statistics` | GET | `/risk-limit-tiers/v1/tier-statistics` | READ | 0 |
| 145403 | `operations-risk-limit-tier-quotas` | GET | `/risk-limit-tiers/v1/tier-quotas` | READ | 0 |
| 145404 | `operations-risk-limit-tier-workspace` | GET | `/risk-limit-tiers/v1/clients/{clientId}/workspace` | READ | 0 |
| 145405 | `operations-risk-limit-tier-history` | GET | `/risk-limit-tiers/v1/clients/{clientId}/tier-changes` | READ | 0 |
| 145406 | `operations-risk-limit-tier-request` | POST | `/risk-limit-tiers/v1/clients/{clientId}/tier-changes` | REQUEST | 8192 |
| 145407 | `operations-risk-limit-tier-withdraw` | POST | `/risk-limit-tiers/v1/tier-changes/{changeId}/withdrawal` | REQUEST | 1024 |
| 145408 | `operations-risk-limit-tier-approve` | POST | `/risk-limit-tiers/v1/tier-changes/{changeId}/approval` | APPROVE | 2048 |
| 145409 | `operations-risk-limit-tier-approve-elevated` | POST | `/risk-limit-tiers/v1/tier-changes/{changeId}/elevated-approval` | APPROVE_ELEVATED | 2048 |
| 145410 | `operations-risk-limit-tier-reject` | POST | `/risk-limit-tiers/v1/tier-changes/{changeId}/rejection` | APPROVE | 2048 |
| 145411 | `operations-risk-limit-tier-reject-elevated` | POST | `/risk-limit-tiers/v1/tier-changes/{changeId}/elevated-rejection` | APPROVE_ELEVATED | 2048 |

合计 141 行。L12 迁移不注册路由。

## 10. 决策流档位配置说明（大纲，交付为 `paytech/risk-decision/docs/limit-tier-configuration.md`）

> **阶段 4 修订（2026-10-30）**：已交付的配置说明按实测引擎约束改写，与下列大纲有三处不同：常量币种来源为支付币种、规则显式限定币种、结账点不做额度（8.3 修订，D-2）；START 只能有一条无条件出口，档位分支挂在 START 后的"档位分支"规则集节点上（D-4）；日 / 月额度暂不能配置（D-3）。以配置说明为准。

1. **前提**：代码已部署（TIER 名单存在、ALLOWED 事件已写入）；迁移已完成；风控确认各档位 × 币种额度（下表为 TEST 示例，正式值由风控给）。
2. **变量清单**（两个事件点各建一套 LIST 变量；累计变量只在支付点）：

   | 变量 | 用途 |
   |---|---|
   | `CO_TIER_IN_*` / `PAY_TIER_IN_*`（4 × 2） | 档位分支条件 |
   | `PAY_CLIENT_ALLOWED_AMOUNT_24H`、`PAY_CLIENT_ALLOWED_AMOUNT_30D` | 日 / 月放行累计（同币种、含本次） |
   | `TIER_{TIER}_{CCY}_SINGLE / _DAY / _MONTH` | 额度常量（MONEY，固定币种） |
   | `TIER_{TIER}_CURRENCIES` | 该档位已配置的币种集合 |

3. **TEST 示例额度**（仅示例）：VERIFICATION USD 单笔 50 / 日 250 / 月 1,000；OBSERVATION USD 2,000 / 20,000 / 200,000；STANDARD USD 不设或按风控；AED、SAR、EGP 各自另设一组。**各币种分别累计，不合并、不换汇**；某档位未配置的币种：VERIFICATION / OBSERVATION / RESTRICTED 拒绝，STANDARD 放行。
4. **规则集**（每个档位一个，FIRST_TERMINAL 顺序）：

   | 序 | 条件 | 结论 / 原因 |
   |---|---|---|
   | 1 | `{INPUT_CURRENCY} NOT_IN TIER_{TIER}_CURRENCIES`（STANDARD 档省略本条） | DENY `LIMIT_CURRENCY_NOT_ALLOWED` |
   | 2 | 金额 `GT TIER_{TIER}_{CCY}_SINGLE`（每个已配置币种一条，币种不同自动为假） | DENY `LIMIT_SINGLE_EXCEEDED` |
   | 3 | `PAY_CLIENT_ALLOWED_AMOUNT_24H GT TIER_{TIER}_{CCY}_DAY`（支付点） | DENY `LIMIT_DAILY_EXCEEDED` |
   | 4 | `PAY_CLIENT_ALLOWED_AMOUNT_30D GT TIER_{TIER}_{CCY}_MONTH`（支付点） | DENY `LIMIT_MONTHLY_EXCEEDED` |

   结账点只用 1、2（S-11）。
5. **目标流程**：
   - `CHECKOUT_PREPARE` 新版本：START → RESTRICTED 命中 → DENY；VERIFICATION / OBSERVATION / STANDARD 命中 → 各自规则集 → 命中 DENY → END(DENY)，否则 → 原 allow；ELSE → END(DENY `LIMIT_TIER_NOT_SET`)。
   - `PAYMENT_ATTEMPT_RISK` 新版本：同样的档位层，之后接原 `PAY_ATTEMPT_FLOW` v1 的 `block` → … 全部节点。
6. **激活前检查**（发布执行人逐项记录）：
   - 只读核对"所有有 ENABLED 支付方式开通项的 CLIENT 合同的 Client 都在 `t_client_limit_tier`"，结果须为 0 缺口；
   - TEST 发布影子运行无意外拒绝；
   - L10 无 `configurationIssues`。
7. **激活**：现有执行发布流程（创建 → 校验 / 影子 → 激活）。
8. **回滚**：用旧流程版本（`PAY_ATTEMPT_FLOW` v1、`CHECKOUT_BASELINE_ALLOW` v1）新建执行发布并激活；档位数据保留不动。
9. **额度调整**：改常量即时生效（无需发布）；记录在变更单中。

## 11. 页面与文案

### 11.1 页面状态与原型对应（主会话补节点 ID）

| 原型 | 页面状态 | 路由 | 接口 |
|---|---|---|---|
| 原型 风控 · 限额档位 01 档位列表 `oAoTu` | 统计卡（验证 / 观察 / 到期待确认 / 标准 / 受限 / 待审批申请）；档位页签；搜索；"只看到期待确认"（`observationDue`）、待审批筛选（`pendingApproval`）；列：客户（编号 · 辖区 · 类型）、CRR、当前档位、进入档位（时间 + 事由 `sourceReason`）、观察期进度（天数条 + 已过天数 + 放行笔数 + 到期待确认）、待审批（from → to + 级别）、来源（`sourceType`）、更新时间、操作 | `/risk/limit-tiers` | L1、L11 |
| 原型 风控 · 限额档位 02 验证档 · 申请上线复核 `WDX9M` | 当前档位卡（档位、进入时间、来源、CRR、有效承保链接含观察期目标与 T+N——工作台读 `RiskUnderwritingClient.current`）；按币种额度表（币种、单笔上限、近 24 小时 已用 / 上限 + 条、近 30 天 已用 / 上限 + 条；`unconfiguredCurrencies` 显示"未配置"）；复核清单 4 系统核对 + 4 人工确认；上线说明；"提交申请"在清单满足前禁用；变更历史 | `/risk/limit-tiers/{clientId}` | 工作台、L4 |
| 原型 风控 · 限额档位 03 观察档 · 进度与到期 `NaNPa` | 观察期进度（天数条、放行笔数条、近似值说明）；额度表；操作：升到标准档（→ STANDARD）、延长观察期（→ OBSERVATION，可改目标）、降为受限档（→ RESTRICTED）；变更历史 | 同上 | 工作台、L4 |
| 原型 风控 · 限额档位 04 待审批 · 审批人视角 `uT41G` | 冻结的申请（变更、理由、申请时观察期进度 `observationDaysAtRequest` / `observationAllowedAtRequest`、审批级别 高级）；批准 / 驳回（驳回意见必填）；对照框：普通审批人看 ELEVATED 时按钮禁用、申请人本人禁用审批但可撤回（L5） | 同上 | L5—L9 |
| 原型 风控 · 限额档位 05 受限档 · 变更历史 `P3zYbn` | 受限影响说明；申请恢复（目标不得高于受限前档位）；完整变更历史（建档、申请、批准、驳回、撤回，含驳回条目与意见） | 同上 | L3、L4 |

原型位于 `operations-console-web/docs/operations-console.pen`（行 y 103863），导出图在 `prototypes/01—05`。核对结论：原型所需字段均已在 L1 / L2 / 工作台 / 变更记录中提供；为对齐原型新增的字段是列表 `crr`、`sourceType`、`sourceReason`、观察期放行笔数，详情 `unconfiguredCurrencies`，变更记录申请时观察期进度（两列）。

### 11.2 文案键（`operations-console-web/src/i18n/en/risk.json` 与 `i18n/zh-CN/risk.json`；菜单 `nav.json`）

| Key | en | zh-CN |
|---|---|---|
| `nav.resource.OPERATIONS_RISK_LIMIT_TIERS` | Limit tiers | 限额档位 |
| `risk.limitTier.title` | Limit tiers | 限额档位 |
| `risk.limitTier.tier.VERIFICATION` / `OBSERVATION` / `STANDARD` / `RESTRICTED` / `NONE` | Verification / Observation / Standard / Restricted / No tier | 验证档 / 观察档 / 标准档 / 受限 / 无档位 |
| `risk.limitTier.noTierHint` | No limit until underwriting is approved. | 承保批准前额度为零。 |
| `risk.limitTier.filter.pendingApproval` / `observationDue` / `search` | Awaiting approval / Observation due / Search client name or number | 待审批 / 观察期到期 / 搜索客户名称或编号 |
| `risk.limitTier.stats.*`（verification、observation、standard、restricted、pendingApproval、observationDue） | 同上各档名 + Awaiting approval / Observation due | 同上 |
| `risk.limitTier.quota.title` / `single` / `rolling24h` / `rolling30d` / `used` | Limits by currency / Per transaction / Rolling 24 hours / Rolling 30 days / Used | 按币种额度 / 单笔 / 滚动 24 小时 / 滚动 30 天 / 已用 |
| `risk.limitTier.quota.separateCurrencies` | Each currency has its own limits and is counted separately; amounts are not converted. | 各币种分别设额度、分别累计，不做换算。 |
| `risk.limitTier.quota.unconfigured.DENY` / `ALLOW` | Other currencies: not allowed / Other currencies: no limit | 其他币种：不允许 / 其他币种：不限 |
| `risk.limitTier.quota.usedHint` | Counts amounts approved by Risk, including payments that later failed. | 按风控放行金额计，含之后支付失败的。 |
| `risk.limitTier.observation.title` / `days` / `transactions` / `due` / `transactionsHint` | Observation period / {elapsed} of {target} days / {count} of {target} approved attempts / Observation period complete—confirm next step / Approved payment attempts; approximate | 观察期 / 已过 {elapsed} / {target} 天 / 已放行 {count} / {target} 笔 / 观察期已到，请确认下一步 / 按风控放行的支付尝试计，近似值 |
| `risk.limitTier.checklist.title` | Go-live review checklist | 上线复核清单 |
| `risk.limitTier.checklist.QUALIFICATION` / `UNDERWRITING` / `AGREEMENT` / `CAPABILITIES` | Qualification approved / Underwriting approved / Agreement active / Payment methods enabled | 资格已批准 / 承保已批准 / 合同已生效 / 支付方式已开通 |
| `risk.limitTier.checklist.CHANNEL` / `INTEGRATION` / `PRODUCTION` / `OPEN_ISSUES` | Channel registration confirmed / Integration confirmed / Production transaction verified / Open issues cleared | 渠道报备已确认 / 技术接入已确认 / 生产交易已验证 / 未结事项已处理 |
| `risk.limitTier.checklist.status.OK` / `NOT_OK` / `UNAVAILABLE` | Done / Not done / Couldn't load | 已满足 / 未满足 / 无法读取 |
| `risk.limitTier.request.title` / `toTier` / `reason` / `goLiveNote` / `submit` / `withdraw` | Request a tier change / Target tier / Reason / Go-live note / Submit request / Withdraw | 申请档位变更 / 目标档位 / 理由 / 上线说明 / 提交申请 / 撤回 |
| `risk.limitTier.request.extend` / `targetDays` / `targetTransactions` | Extend observation / Target days / Target approved attempts | 延长观察 / 目标天数 / 目标放行笔数 |
| `risk.limitTier.approval.level.STANDARD` / `ELEVATED` | Standard approval / Senior approval (CRR MEDIUM / HIGH) | 普通审批 / 高级审批（CRR 为 MEDIUM / HIGH） |
| `risk.limitTier.approval.approve` / `reject` / `comment` / `selfBlocked` | Approve / Reject / Comment (required to reject) / You requested this change, so someone else must approve it. | 批准 / 驳回 / 意见（驳回必填） / 你是申请人，须由其他人审批。 |
| `risk.limitTier.history.title` / `changeType.UNDERWRITING_INIT` / `MIGRATION` / `REQUEST` | History / Set by underwriting approval / Migrated / Requested | 变更历史 / 承保批准自动建档 / 存量迁移 / 运营申请 |
| `risk.limitTier.restricted.hint` / `restoreLimit` | New transactions are declined while restricted. / Can't restore above {tier}. | 受限期间新交易被拒绝。 / 恢复档位不得高于 {tier}。 |
| `risk.limitTier.column.client` / `crr` / `tier` / `entered` / `observation` / `pending` / `source` / `updated` | Client / CRR / Current tier / Entered tier / Observation / Pending request / Source / Updated | 客户 / CRR / 当前档位 / 进入档位 / 观察期进度 / 待审批 / 来源 / 更新时间 |
| `risk.limitTier.source.UNDERWRITING_INIT` / `MIGRATION` / `REQUEST` | Underwriting / Migration / Request | 承保建档 / 存量迁移 / 运营申请 |
| `risk.limitTier.action.promoteStandard` / `extendObservation` / `restrict` / `requestRestore` | Move to Standard / Extend observation / Restrict / Request restore | 升到标准档 / 延长观察期 / 降为受限档 / 申请恢复 |
| `risk.limitTier.quota.notConfigured` | Not configured | 未配置 |
| `risk.limitTier.approval.atRequest` | Observation at request: {days} days, {count} approved attempts | 申请时观察期：{days} 天，放行 {count} 笔 |
| `risk.limitTier.error.*`（8.1 各错误码） | 各一句 | 各一句 |

## 12. 测试与验证计划

### 12.1 risk-decision auto（本机 `mor_risk_decision`，HTTP 与真实评估入口）

| 验收 | 入口 | 场景 |
|---|---|---|
| AC-01 | REQ-03 承保接口（提交 / 复核 / 自动） | APPROVED 且无档位 → 档位 VERIFICATION、变更记录 UNDERWRITING_INIT、TIER_VERIFICATION 成员；无档位 Client 的修订批准同样建档；已有档位时修订批准不改档位；已有档位（迁移）时不重复建；并发两次批准不出错（唯一约束） |
| AC-02 / AC-04 | L4—L9 | VERIFICATION → OBSERVATION 清单不全 → 409；LOW 走 L6 批准；MEDIUM / HIGH / 无 CRR 走 L6 → 409 ELEVATION_REQUIRED、L7 通过；申请人自批 → 409；不允许迁移 → 409；第二个待审批 → 409；撤回非申请人 → 409 |
| AC-03 | L6 / L7 | 批准后档位、名单成员一致（旧成员 SUPERSEDED、新成员 ACTIVE；任一时刻至多一张 TIER 名单命中） |
| AC-05 | L2 | 观察期天数（构造开始时间）与 ALLOWED 计数；任一达到 → due；延长重置 |
| AC-06 / AC-11 | 支付尝试评估（`RiskDecisionApi` 评估入口）+ 测试用 TEST 发布（测试内经现有配置接口创建变量、规则集、流程并激活） | 无档位 → DENY `LIMIT_TIER_NOT_SET`；VERIFICATION USD 单笔超额 → DENY、额度内 ALLOW；累计：多笔放行后第 N 笔超日额 → DENY，被拒那笔不计入后续（ALLOWED 口径）；USD 与 AED 分别累计；VERIFICATION 未配置 SAR → DENY；STANDARD 未配置 SAR → ALLOW；RESTRICTED → DENY；改常量后下一次评估即用新值 |
| AC-07 | L12 | 迁移 4 个 → STANDARD；重复执行 SKIPPED；调用方不在白名单 → 403 |
| AC-08 | 名单接口 | 创建 TIER 名单 / 增删 TIER 成员 → 400 |
| 事件码 | 结账与支付评估 | ALLOW 写 ASSESSED + ALLOWED；DENY 写 ASSESSED + REJECTED；技术失败不写 |
| L10 | 额度解析 | 命名规范的常量正确归档；缺项 / 币种集合不一致进 `configurationIssues` |
| AC-09（模板） | 本库只读执行模板正文 | 无档位、VERIFICATION、OBSERVATION（到期 / 未到期）、STANDARD、RESTRICTED × 两个目标档位 |

### 12.2 ops-bff / ops-web

- **ops-bff auto（测试外部端点）**：
  - 工作台各来源 OK / 不可用的清单状态；
  - 申请转发时重新读取系统项，忽略页面伪造的系统项（严格解析 422）；
  - 列表筛选透传；
  - 错误映射。
- **ops-web 单测**：
  - 档位徽标、按币种额度表与"其他币种"策略；
  - 观察期进度；
  - 清单交互（系统项只读、人工项勾选、提交按钮启用条件）；
  - 审批按钮按级别 / 权限 / 本人禁用；
  - 恢复目标限制；
  - 另跑 tsc、build、i18n 校验。

### 12.3 本机联调与 mor-test

- **本机 e2e**：
  - `mor_risk_decision_v2e2e` 执行前滚；本机只读账号加表级授权；块 145 + 激活；JAR 替换。
  - 迁移本机 3 个可交易 Client。
  - 按配置说明建 TEST 流程，影子运行后激活。
  - 浏览器 en / zh-CN × 1440 / 390，走原型 01—05。
  - REQ-05 DATA_QUERY 消费模板一次。
- **mor-test（单独授权）**：按第 13 节顺序；迁移 4 个 Client；激活前检查记录；激活后用演示 Client 验证一笔额度内、一笔超额、一笔未配置币种；保留回滚发布草稿。

## 13. 实施切分与发布

| # | 任务 | 执行者 | 依赖 | 产物 |
|---|---|---|---|---|
| T1 | DDL、注释、4 名单 DML、回滚、`schema.sql` / `initial-data`、PDManer、文档；本机 auto 库前滚 | risk-decision 后端 | — | 第 9 节 |
| T2 | 名单 TIER 用途与校验；ALLOWED 事件码与写入 | risk-decision 后端 | T1 | 8.2 |
| T3 | 档位领域、Service、Processor（L1—L12）、REQ-03 事务挂点、错误码、Controller、迁移白名单配置 | risk-decision 后端 | T1 T2 | 8.1、8.3 |
| T4 | risk-decision auto 测试（12.1，含真实评估链路与 TEST 配置） | risk-decision 后端 | T3 | 测试报告 |
| T5 | ops-bff：客户端、工作台、转发、严格解析、错误映射、测试 | ops-bff | T3 | 8.4 |
| T6 | 块 145 SQL（初始化 + 插入 / 激活 / 回滚）、离线编译 | ops-bff 执行者或主会话 | T5 | 9.4 |
| T7 | ops-web（**原型 01—05 确认前不开始**） | ops-web | T5 + 原型 | 第 11 节 |
| T8 | 语言包 | 主会话或 ops-web 执行者 | T7 | `release/i18n-bundles/` |
| T9 | 配置说明文档、模板文本、发布清单（含激活前检查 SQL） | risk-decision 后端 | T3 | 第 10 节、8.5 |
| T10 | 文档：决定 3.3、`CURRENT_IMPLEMENTATION.md`（实现后） | risk-decision 后端 | T4 | — |

发布（mor-test，单独授权；严格顺序 **代码 → 迁移 → 流程配置 → 激活**）：

| 顺序 | 动作 | 回滚 |
|---|---|---|
| 1 | `mor_risk_decision` 前滚 SQL | 回滚 SQL（有档位数据时拒绝） |
| 2 | risk-decision 新镜像（开启迁移白名单仅在步骤 3 期间） | 旧镜像（新表、名单保留无害；ALLOWED 事件停写） |
| 3 | 迁移：只读导出有 ENABLED 支付方式开通项的 Client → L12 → 核对 0 缺口 → 关闭迁移白名单 | 档位数据保留（未激活前无影响） |
| 4 | operations-console-bff 新镜像 → 块 145 插入 / 编译 / 激活 → operations-console-web + 语言包 | 逆序 |
| 5 | DBA 表级授权 → 登记 `GOLIVE_LIMIT_TIER` | 停用模板 → 撤销授权 |
| 6 | 风控按配置说明建变量 / 规则集 / 新流程版本 → TEST 发布校验与影子 → **激活前检查** → 激活 | 用旧流程版本新建发布并激活 |

### 13.1 待主会话确认

| 编号 | 问题 | 本稿取舍 |
|---|---|---|
| O-1 | 额度值存放：(a) 只在规则常量里、(b) 专用额度表、(c) 只在说明文档 | **(b) 的等价做法，但不改引擎**：额度值就是风控 CONSTANT 变量（MONEY + 固定币种），按命名约定 `TIER_{TIER}_{CCY}_{SINGLE\|DAY\|MONTH}` 与集合 `TIER_{TIER}_CURRENCIES`；常量在评估时读当前值（不随发布冻结），页面经 L10 按命名解析展示。代价：改额度即时生效、走变量管理权限、无四眼与历史表——如需四眼改额度，另立"额度变更审批"需求 |
| O-2 | ALLOWED 事件"允许计入本次" | 允许，含义为"若本次放行后的累计"，这是额度判断需要的口径 |
| O-3 | 迁移入口形式 | 内部接口 + 调用方白名单（默认关闭，迁移时临时开启），比一次性 DML 更安全（同事务维护名单成员、记录变更） |
| O-4 | 档位行的 Client 展示字段来源 | 承保建档取承保快照；迁移取输入（发布执行人从 client-master 只读导出） |
| O-5 | 结账点只做"无档位 / 受限 / 币种 / 单笔"，日 / 月额度只在支付点 | 按此（S-11） |
| O-6 | 菜单排序 95（风控菜单最后，在承保之后） | 按此 |
| O-7 | "已用"金额的计算代价 | 详情页实时按现有累计索引汇总（与评估同一索引，单 Client 可接受）；列表不算已用。若后续出现 30 天放行量很大的 Client，再加按日汇总表，本期不做 |
| O-8 | 列表 CRR 来源 | risk-decision 同库读当前有效承保（REQ-03 `t_client_underwriting`），不经 ops-bff 逐行调用 |

---

# 阶段 4 修订：日 / 月额度汇总表（2026-10-30；用户已确认 R-Q1—R-Q4 按建议，已实施）

> 背景：交付记录 D-3。CLIENT 主体的金额合计 Feature 最长 3600 秒，且没有 30 天窗口（`rd/decision/domain/feature/FeatureHandlers.java:31-46,62-71`）。原因是每次评估都会计算发布引用的全部累计 Feature（`rd/decision/service/query/FeatureValueReader.java:52-55`），单个 Feature 时限 200 ms，高量 Client 的宽窗口扫描事件会超时，并按 FAIL_CLOSED 拒绝。用户选择方案 B：新增"Client × 事件 × 币种 × UTC 日"的放行金额汇总表，与放行事件同一事务维护，评估时最多读 30 行。

## R-1 窗口语义（建议）

| 额度 | 原口径 | 建议口径 | 从汇总行计算 |
|---|---|---|---|
| 日 `TIER_{TIER}_{CCY}_DAY` | 滚动 24 小时 | **UTC 当日**（00:00—24:00 UTC，迪拜 04:00 切日） | 当日 1 行 |
| 月 `TIER_{TIER}_{CCY}_MONTH` | 滚动 30 天 | **近 30 个 UTC 自然日（含当日）** | `event_date ∈ [当日 − 29, 当日]`，≤ 30 行 |

- 这两个口径都能由日汇总精确算出，不需要估算。
- 不采用"昨日按比例"的近似：同一笔额度会因评估时刻不同而得出不同结论，不可解释。
- 不采用小时汇总：滚动 24 小时需要 25 个桶，切点处仍有最多 1 小时误差，表的行数也是日汇总的 24 倍。
- 用户看到的变化：日额度在 UTC 零点清零，不再是任意 24 小时内的累计；月额度仍是"近 30 天"，只是按 UTC 自然日对齐。
- 页面文案相应改为"当日（UTC）""近 30 天（UTC 自然日，含今日）"。需求 B11、AC-06、AC-11 的"滚动 24 小时 / 30 天"字样要同步修订。

## R-2 表 `t_client_allowed_daily_total`（mor_risk_decision）

```sql
CREATE TABLE `t_client_allowed_daily_total` (
  `daily_total_id` BIGINT NOT NULL COMMENT 'Client 日放行汇总行的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识（CLIENT 累计主体键）。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '汇总的放行事件。允许值：PAYMENT_ATTEMPT_ALLOWED、CHECKOUT_ALLOWED。',
  `currency` CHAR(3) NOT NULL COMMENT '事件币种（ISO 4217）；各币种分别汇总，不换汇。取值来源：ISO 4217；非固定枚举。',
  `event_date` DATE NOT NULL COMMENT '事件发生时间（评估时间）所在的 UTC 自然日。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '当日该币种放行金额合计。',
  `event_count` INT NOT NULL COMMENT '当日该币种放行事件数。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`daily_total_id`),
  UNIQUE KEY `uk_client_allowed_daily_total` (`client_id`, `event_code`, `currency`, `event_date`) COMMENT '每个 Client、事件、币种、UTC 日一行；也是评估读取路径'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 按 UTC 日的放行金额汇总：与放行累计事件同一事务累加，供日 / 月额度 Feature 与档位页"已用"读取；事件表仍是明细事实';
```

- 写入：放行评估的本地保存事务中，在插入 `*_ALLOWED` 事件之后执行一次 `INSERT … ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), event_count = event_count + 1, update_time = …`（主键 ID 预先生成，冲突时不用）。只为 CLIENT 主体写，金额与币种取输入，日期取评估时间的 UTC 日。
- 拒绝、审核与技术失败都不写；重放走原评估返回，不重复累加；影子运行不写（与现有累计事件一致）。
- 只增不减：后续支付失败不回冲，与"按放行计、偏保守"的口径一致（S-04）。

## R-3 并发与热点

- 写：同一 Client、币种、UTC 日只有一行。并发放行在这一行上按行锁串行，持锁到评估事务提交为止；事务里只有本地插入，持锁时间为毫秒级。每个事务只锁一行，不会形成环形等待。
- 热点风险：单个 Client、单个币种的放行 TPS 很高时会排队。按现有规模（mor-test 每日几十笔）不构成问题。若出现大商户高并发，再按 `(client, currency, date, slot)` 分 N 个槽写、读时合计，本期不做。
- 读写竞争：额度判定在事务前读取汇总，两笔并发评估可能都看到"未超额"而都放行，最多超出一笔金额。这与现有累计 Feature 的行为相同。额度是软控制，不是余额扣减；需要严格上限时另立需求（预占）。

## R-4 Feature 与变量配置（引擎改动点）

不新增变量来源。沿用 VELOCITY 变量，新增统计方式 `DAILY_SUM_AMOUNT`（"按 UTC 日汇总的放行金额"），因此变量页面、Feature 版本、发布冻结与哈希都沿用现有机制。

| 位置 | 改动 |
|---|---|
| `rd/decision/domain/enums/VelocityMetric.java` | 新增 `DAILY_SUM_AMOUNT` |
| `rd/decision/domain/feature/FeatureHandlers.java:31-46,62-71,86-110` | `DAILY_SUM_AMOUNT` 只允许：锚定主体 CLIENT、事件 `*_ALLOWED`、窗口 86400（UTC 当日）或 2592000（近 30 个 UTC 日）、金额量纲、可计入本次；其他统计方式的窗口集合不变（不放开 CLIENT 的宽窗口 SUM_AMOUNT） |
| `rd/decision/domain/feature/FeatureCatalog.java:43-47` | 变量编辑上下文按上述规则列出新统计方式与两个窗口（页面由服务端目录驱动） |
| `rd/decision/service/query/FeatureValueReader.java:79-100` | `DAILY_SUM_AMOUNT` 分支：按 `(client, event, 输入币种, event_date 区间)` 读汇总合计；计入本次时加本次金额 |
| `rd/decision/domain/runtime/RuntimeEvaluationFacts.java`（`velocityFacts`）、`PaymentRiskFacts.java:65`、`CheckoutRiskFacts.java:59` | 放行时额外产生一条日汇总增量，放入评估 Context |
| `rd/decision/domainservice/service/impl/PaymentRiskAssessmentServiceImpl.java:41-42`、`CheckoutRiskAssessmentServiceImpl.java:41-42` | 在同一事务中、写完放行事件后 upsert 日汇总 |
| 新增 DataObject / Mapper / XML / Repository | `ClientAllowedDailyTotal`：`upsertAdd`、`sumAmount(client, event, currency, fromDate, toDate)`、`totalsByCurrency(client, event, fromDate, toDate)` |
| control 端口 `LimitTierUsageReader.allowedPaymentAmounts` 与实现 | 改读汇总表：返回每个币种的当日与近 30 日合计（L2 "已用"与评估口径一致） |
| 列注释 | `t_velocity_feature_definition.metric` 允许值加 `DAILY_SUM_AMOUNT`；`window_seconds` 注释说明 `DAILY_SUM_AMOUNT` 的两个取值 |
| ops-web | `risk.json` 增加统计方式文案 `DAILY_SUM_AMOUNT`（en / zh-CN），以及两个窗口的显示（"UTC 当日"、"近 30 个 UTC 日"）；档位页额度表列名与说明改为新口径 |

配置（写入配置说明）：
- 两个变量 `PAY_CLIENT_ALLOWED_DAY` / `PAY_CLIENT_ALLOWED_30D`：VELOCITY、`PAYMENT_ATTEMPT_ALLOWED`、CLIENT、`DAILY_SUM_AMOUNT`、86400 / 2592000、计入本次，MONEY，币种变量 `PAY_INPUT_CURRENCY`。
- 规则：`PAY_INPUT_CURRENCY EQ '{CCY}' AND PAY_CLIENT_ALLOWED_DAY GT TIER_{TIER}_{CCY}_DAY`，月额度同理。

## R-5 性能

- 每次放行评估增加 1 次主键附近的 upsert。
- 每个日 / 月变量每次评估读取 ≤ 30 行（唯一索引范围），与 Client 交易量无关，可以放心对所有 Client 计算。
- 档位页 L2 的"已用"改为同一路径读取，不再扫描事件明细。

## R-6 迁移与回填

- 新表随本需求的前滚 SQL 一起创建（并入 `2026-10-30-client-limit-tier.sql` 或新增增量文件），回滚 SQL 同步。PDManer、schema、测试 init DDL、清理器与库前置检查同步修改。
- 回填不需要：`*_ALLOWED` 事件与新表在同一个镜像中首次出现，上线前没有放行事件可汇总。
- 本机 auto 库已有本阶段的测试放行事件，全部是测试数据，每例清理。如果放行事件写入先于汇总表上线，用一条 `INSERT … SELECT … GROUP BY` 从 `t_velocity_event` 加 `t_velocity_event_subject` 回填，随文件提供，默认不执行。

## R-7 测试

- 评估入口（`PaymentRiskLimitTierApiAutoTest` 扩展）：
  - 日额度：同一 UTC 日累计到上限后拒绝；被拒那笔不进汇总；USD 与 AED 各自一行；
  - 跨日：昨日汇总行不计入当日，但计入 30 日；第 31 天之前的行不计入；
  - 每条放行评估恰好让汇总加一笔，拒绝与重放不加。
- 变量创建（`FeatureVariableCreateApiAutoTest`）：
  - `DAILY_SUM_AMOUNT` 只接受 CLIENT 加 `*_ALLOWED` 加两个窗口；
  - 其他组合 422；CLIENT 加 SUM_AMOUNT 加 86400 仍被拒。
- 变量编辑上下文：目录含新统计方式。
- 档位详情 L2：已用按汇总表、按币种。
- 并发：同一 Client、同币种、同日 N 个并发放行后，汇总金额与笔数等于 N 笔之和（行锁正确，无丢失更新）。

## R-8 规模

S 偏 M：risk-decision 约 8 个类改动、新增 1 套表访问；DDL 1 张表；ops-web 文案与额度表口径；配置说明与需求 B11、AC-06、AC-11 字样修订。HTTP 合同字段名可保持（`rolling24h / used24h` 改为表示"当日"），也可改名为 `day / usedToday`，需要 ops-bff 与 ops-web 同步，见 R-9。

## R-9 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| R-Q1 | 日口径：UTC 当日，还是保留滚动 24 小时（只能用小时桶近似） | UTC 当日 |
| R-Q2 | 月口径：近 30 个 UTC 日（含今日），还是 UTC 自然月 | 近 30 个 UTC 日（与原"30 天"最接近） |
| R-Q3 | L2 / 工作台字段名：沿用 `rolling24h`、`used24h`、`rolling30d`、`used30d`（只改含义），还是改为 `day`、`usedToday` | 改名，避免误读；三端同改，字段少，风险低。**已确认**：`day`、`last30Days`、`usedToday`、`usedLast30Days`（与 ops-web 约定） |
| R-Q4 | 汇总表是否同时为结账点（`CHECKOUT_ALLOWED`）维护 | 维护（同一处代码，成本可忽略），但结账点不配置日 / 月规则（D-2） |

> **实施说明（2026-10-30）**：R-2 的日期列实施时命名为 `event_date`（`utc_date` 与 MySQL 函数名 `UTC_DATE` 冲突，未加反引号的 SQL 会语法错误）；其余按 R-1—R-8 实施，R-Q1—R-Q4 全部按建议。
