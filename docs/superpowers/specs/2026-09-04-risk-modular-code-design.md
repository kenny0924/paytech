# Risk 模块化代码设计

日期：2026-09-04

状态：用户已确认三个业务模块及 `risk-*` 命名；本文记录代码结构与实施边界，不代表 Java、SQL、数据库或模型变更已实施。

## 1. 决策与范围

一个服务、一个数据库、一个部署单元，按业务能力拆分三个内部 Maven JAR：Decision、Control、Compliance。不拆独立微服务，不按表生成业务 Service，不预建通用规则平台或案件工作流框架。

用户指定项目目录和 Maven Artifact 使用 `risk-*`，覆盖通用 `paytech-*` 命名；仅真正承载尽调、资格和争议业务的模块使用 `risk-compliance`。`risk-protection` 不作为最终名称。

权威依据：

- [开发约定](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[Java 工程结构](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)。
- [Risk 数据库权威](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md)、[物理决策](../../../paytech/risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md)与[生产 Manifest](../../../scripts/pdmaner/risk/manifest.mjs)。
- [本地补偿规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)与[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)。

当前模型为 71 表 / 865 字段 / 106 唯一索引 / 74 访问索引 / 111 本库关系 / 9 个 1:1 / 8 图组，另有 83 条 CHECK。目录规划不改变该基线，也不代表旧模型已兼容最新 Foundation。

## 2. 目标工程

```text
risk-service/
├── pom.xml
├── docs/
├── risk-api/
├── risk-decision/
├── risk-control/
├── risk-compliance/
├── risk-web/
└── risk-test/
```

这是目标结构，本轮不移动现有 `risk-compliance-service` 数据库资产目录。实际工程建立时另行处理仓库路径、模型生成路径与文档链接，不通过全局字符串替换修改真实 Compliance 业务名称。

- `risk-api`：轻量外部 HTTP 契约，按能力定义 Api、Models 及公开 Code；不是共享持久化对象库。
- `risk-decision`：规则配置、发布、评估、Action Intent/Effect 与三类 Assessment。
- `risk-control`：冷却、名单、Velocity、限额与长期出款 Control。
- `risk-compliance`：尽调、资格、争议与责任分类；内部用 duediligence/dispute 包区分，不建通用 Case 引擎。
- `risk-web`：唯一启动单元，ext/rest、ext/mq、ext/job 入站，按需 service/processor 编排及显式事务，服务级幂等与补偿接入。
- `risk-test`：真实业务链、数据库并发、协议、纯算法和架构边界测试。

不另建 application/common/engine/action/limit/screening/settlement 工程。目录按实际用例建立，不预建空层。业务 JAR 内沿用 domain、domainservice、core/dal，必要出站适配放 integration；DataObject 不暴露给其他模块或 API。

## 3. 全部表的唯一归属

以下采用 Manifest 逻辑名，省略 `t_` 前缀。唯一缩写为 settlement_control_state_change，其物理名仍为 t_settle_control_state_change。

### 3.1 Decision：27 表

配置与发布 19 表：

`decision_point`、`decision_point_version`、`decision_point_input_field`、`decision_point_feature`、`decision_point_rule_set`、`decision_point_obligation`、`feature_version`、`feature_dependency`、`rule_set_version`、`risk_rule`、`rule_node`、`rule_operand`、`action_version`、`action_parameter_definition`、`rule_action`、`rule_action_parameter`、`decision_release`、`release_audit_event`、`release_shadow_run`。

评估与执行证据 8 表：

`decision_evaluation`、`evaluation_feature_result`、`evaluation_rule_trace`、`evaluation_action_intent`、`action_effect`、`checkout_input_snapshot`、`transaction_risk_assessment`、`settlement_risk_assessment`。

Assessment 是冻结的执行输入与结果，因此留在 Decision；Action Effect 记录动作来源、合并及执行证据，不拥有其他模块的实际业务状态。

### 3.2 Control：16 表

风险数据 6 表：

`risk_restriction`、`risk_list`、`risk_list_entry`、`payment_attempt_fact`、`velocity_event`、`velocity_event_subject`。

限额 7 表：

`limit_version`、`decision_point_limit_binding`、`limit_position`、`limit_reservation_group`、`limit_reservation`、`limit_group_state_change`、`limit_movement`。

出款控制 3 表：

`settlement_control`、`settlement_control_scope`、`settlement_control_state_change`。

Limit Binding 归 Control，因为其取值、窗口、阈值及版本语义属于限额；发布校验必须经 Control 公开能力获取完整闭包，Decision 不直接查询这些表。

### 3.3 Compliance：26 表

尽调 19 表：

`dd_program`、`dd_program_version`、`dd_program_requirement`、`dd_program_check`、`dd_program_screening`、`due_diligence_case`、`dd_case_subject`、`dd_case_dependency`、`dd_party_snapshot`、`dd_requirement`、`dd_evidence_ref`、`dd_check`、`screening_run`、`screening_hit`、`screening_resolution`、`dd_case_state_change`、`qualification_decision`、`role_qualification`、`qualification_state_change`。

争议 7 表：

`dispute_case`、`dispute_state_change`、`dispute_evidence_package`、`dispute_evidence_ref`、`dispute_submission_request`、`dispute_outcome`、`dispute_liability_decision`。

### 3.4 服务级技术表：2 表

`compensation_event`、`command_idempotency`。

Web 服务级基础设施是接入归属；补偿持久化复用 Foundation，命令幂等使用明确的内部能力，Processor 不直接访问 Mapper。业务模块不反向依赖 Web，也不各建技术表。

计数：27 + 16 + 26 + 2 = 71。模型图组不必等于 Maven 模块；每张表仅一个持久化归属。

## 4. 模块公开能力与依赖

Web 依赖 API 和三个业务 JAR，Test 依赖实际测试切片。三个业务 JAR 不互相依赖、不回调 Processor。稳定对外 Code 可复用 API，但不为消除循环把内部模型搬进 API。

- Decision：规则版本校验/冻结、候选 Release、Shadow/审批、执行计划装载、评估计算、Action 合并、最终证据持久化、准确历史结果查询。
- Control：有效限制/名单查询、Velocity 查询、受控冷却/名单变更、整组限额占用和权威事实收口、Control 创建/精确释放、锁内资格检查。
- Compliance：申请接收、材料和检查、Screening 完成/处置、正式资格决定、争议建案/证据提交、权威 Outcome 与更正。

公开能力返回业务结果、接收完整业务命令，不开放任意 updateStatus、updateCounter 或通用单表保存。各模块内部自行完成状态机、金额/类型矩阵、版本与来源校验，不把规则全部搬到 Processor。

共享决策按以下方式组合：

1. Decision 定义狭窄 Feature 扩展协议。Web 的具体适配器调用 Control/Compliance 公开查询并转换类型，不回调 Processor，不在纯 AST 中执行数据库或 HTTP。
2. Decision 产生已验证动作计划。Web 选择明确的处理能力，Control/Compliance 完成实际业务动作，Decision 保存 Effect 证据；本地原子部分共享事务。
3. DD 推荐由 Decision 产生，Compliance 自己验证材料、依赖、权限和状态后形成 Qualification；Dispute 正式责任也归 Compliance，不能直接把通用业务决定映射为 LOST。
4. Release 静态校验组合 Decision 配置和 Control 的准确 Limit/Binding 闭包。缺行、版本漂移、缺 Handler 或非法类型必须拒绝；缓存不能掩盖未冻结配置。

配置装载、Feature 获取、纯规则计算、业务最终提交是不同职责；它们可以是少量具体类，不意味着新增四个工程或抽象框架。

## 5. 数据与事务协议

同库不开放跨模块 Repository、Mapper、DataObject、SQL/JOIN、视图或存储过程访问。查询经公开能力组合；仍保留现有本库 FK。

复杂用例由 Web Processor 使用 TransactionTemplate；模块参与同一个 DataSource/事务管理器的当前事务。不得用 REQUIRES_NEW、异步线程或补偿拆散必须原子提交的部分。

| 用例 | 原子边界 |
|---|---|
| Payment ALLOW | 正式 Evaluation、Group、Reservation、Movement、Position、适用的本地 Effect/领域状态与必要任务 |
| 冷却/名单 Action | Effect 证据和实际 Restriction/List 变更 |
| 出款资格 | 锁内 Control 匹配、冻结 Assessment 结果与 Evaluation |
| 资格决定 | 相关 Decision 证据、Qualification Decision/当前资格/历史与必要任务 |
| 争议规则责任 | 相关 Effect、Outcome/Liability、Case 指针/版本/历史及必要任务 |

远程调用、文件上传和人工动作不在数据库锁内执行。长流程分阶段，在最后短事务内复验相关版本、Scope 和证据。模块只拥有局部锁并不足够，实施计划必须定义跨模块完整资源顺序。

限额失败不能遗留部分占用；UNKNOWN 不自动释放；零 Binding 的 Payment ALLOW 仍有空 Group。RCO-I09 四个检查点仍分别创建新的 pair，同一命令重放复用原 pair，使用已确认的变更前 expected version；Finance 独立执行实际账户门禁。

事务失败后的技术失败证据如何持久化，必须作为评估用例的明确恢复协议设计。不得在原子业务事务内提前独立提交成功片段，或把回滚后的执行标记成成功。

## 6. Shadow、错误与敏感数据

Shadow/Replay 不获得真实业务写入能力，不创建 Restriction、List Entry、Control、Limit Group、Velocity 或补偿任务；技术失败无业务决定且无 Effect。不能只靠散落在 Handler 中的 shadow 布尔判断。

相同输入和 evaluation_time 不足以保证可变 Feature 一致。各 Feature 在实施前明确复用冻结值或读取可证明的历史事实；无法满足者不得伪装成准确旁路比较。

业务 ALLOW/REVIEW/DENY 与技术失败分离；不得把技术失败当 DENY 或默认 ALLOW。HTTP 使用当前 Foundation 正常 typed DTO/共享错误协议，未知异常不得宽泛捕获后同义包装。受限输入、Consumer Token、网络原值和尽调资料必须执行专项日志限制；不能直接使用普通端点全 Body 日志。

## 7. 实施前门禁

1. 补偿契约：Risk Manifest 仍有旧 execution_type/source_version/schema_version/payload_hash 等列，而 Foundation 已精简。接入前单独对齐模型、Policy、文档、SQL与测试，不能仅改 Java。
2. 不可变事件重建：逐事件证明 Handler 能从稳定来源重建原内容；成功任务删除不等于永久幂等证据删除，不得从最新可变根重建旧事件。
3. 全资源锁矩阵：覆盖幂等键、Case、Control Role Range、Position、Effect 等资源；锁后复验变化时退出重试或拒绝，不反向补锁。特别验证零 Control 的范围锁和并发创建。
4. 配置与 Handler 闭包：固定准确版本和实现 Hash；运行中不重新解析当前 Release，也不允许跨模块漏装配 Limit Binding。
5. Java Canonical 实现：沿用现有黄金向量验证数字、空值、排序、UTC精度和Hash；现有 JavaScript 校验器是设计期规格，不是已实现的 Java 引擎。
6. 架构门禁：拒绝业务 JAR 循环依赖、跨模块持久化对象/SQL 和业务模块反向调用 Web。

上述门禁是后续具体实施计划的必交付项，不宣称锁协议、失败恢复或并发运行已经验证。

## 8. 分阶段交付与验收

先以 Checkout 最小评估链带出工程骨架、纯 AST、输入/轨迹/结果查询，再补完整发布与 Shadow；测试 Fixture Release 不作为绕过生产审批的通道。正式流量前必须完成发布门禁。

随后完成 Payment 冷却/名单/限额及权威事件收口、四点出款资格，再按独立切片实现 DD 和 Dispute。工程分期不是业务安全豁免。

每个切片同时验证正常结果、幂等重放/异内容冲突、技术失败无副作用、事务中途失败、并发及相关事件乱序。纯算法使用聚焦单元测试；持久化和协议使用真实生产链、仓库 local-environment 与有状态 Simulator。不使用 Flyway、Testcontainers、方法级 Stub 或应用启动建表。

本文仅保存设计；后续需先审阅本文，再拆出具体实施计划。本轮不创建 Java 工程、不改数据库模型/DDL、不修改共享入口、不提交 Git。
