# CCT 实时交易资格与 Retention 延期设计

## 1. 决策

本设计取代 CCT 当前的持久化 Eligibility Evaluation 和六系统 MVP 自动 Retention/Legal Hold 方案。

- CCT 删除 `client_eligibility_evaluation`、`eligibility_dimension_evaluation`、`eligibility_source_evaluation`、`eligibility_source_fact`。
- CCT 删除 `retention_policy_projection`、`retention_resource_control`、`retention_hold_projection`、`retention_action_log`。
- 交易资格在使用点实时校验，不保存 Evaluation、固定七维结果、来源事实副本或相关 Hash。
- MVP 暂不建设通用自动留存、Legal Hold、归档、匿名化或销毁基础设施。数据默认保留，不自动删除或销毁。

该变更只授权文档、需求站点、PDManer 设计及其验证脚本；不授权 Java、Migration、物理数据库或运行环境。

## 2. 实时资格校验

### CCT-I10：诊断查询

CCT-I10 是无持久化副作用的同步诊断查询。调用方提供 `client_id`、`country_code`、`currency`、`industry_code`、`mcc_code`。CCT 在调用时读取自身当前有效事实，并同步查询事实拥有系统：

- CCT：Client、Profile/Classification、Assignment/Operating Entity、Agreement、Legal 与 Commercial Term；
- Risk & Compliance：当前角色资格与合规限制；
- Payment Orchestration：准确范围的当前路由覆盖；
- Platform Support：当前集成就绪状态。

响应只表达 `status=READY|NOT_READY|RESTRICTED`、`checked_at`，以及非 `READY` 时的稳定 `reason_code`。它不返回 ID、版本、有效期、七维明细或集合 Hash，也不是后续交易的可复用证明。

### CCT-I13：交易准入门禁

CCT-I13 在每次调用中先执行同一套实时校验，再原子解析 CCT 自有的商业、合同与法律上下文。任一必要条件不满足或依赖超时/失败时均 fail-closed，且不返回部分 Bundle。成功响应本身只证明本次调用通过门禁，不生成或持久化 Eligibility 对象。

`context_as_of_time` 仅用于解析 CCT 自有的有效期事实，不允许调用方用历史时间重放外部系统的旧 `READY` 状态。

## 3. 商业上下文 V4

删除 Eligibility 证明会改变 Canonical Payload，因此 `commercial_context_hash` 升级为 `CODE-MOR:CCT:COMMERCIAL_CONTEXT:4`。V4 保留 Client、可空 Local Partner、Operating Entity、Profile、Classification、Assignment、Agreement、Consumer Legal、Seller Disclosure、Client Pricing 和条件存在的 Partner Share；删除所有 Eligibility ID、Version、Status、Reason/Dimension/Source Hash 输入。V3 与 V4 不得混用。

Commerce `order_snapshot` 和 Risk `checkout_assessment` 删除七个 CCT Eligibility 快照字段，不增加替代字段。订单创建只在 CCT-I13 成功后继续；订单仍冻结同一次 V4 Bundle 的实际商业与法律事实。Payment 与 Finance 继续继承订单快照，不重新查询 CCT 改写历史。

## 4. Retention 延期边界

MVP 不发布或消费通用 `RetentionPolicyChanged`、`LegalHoldPlaced`、`LegalHoldReleased` 事件，不维护通用 Retention 投影、资源控制、Hold 投影、销毁任务或动作日志。

延期不表示允许任意删除：

- 业务与技术数据默认保留；
- 不执行自动归档、匿名化或销毁；
- 保留业务专用过期字段、幂等记录生命周期、不可变业务审计和补偿事件清理边界；
- Vault 可 Retire/解绑并禁止 Reveal，但 MVP 不承诺物理销毁；
- 接入真实受监管生产数据前，必须完成数据分类、适用法规、留存期限、Legal Hold、备份处置、Vault 生命周期、受控销毁、审计及法律/合规评审。

## 5. 不变项

- `compensation_event`、`command_idempotency` 和业务变更历史继续保留；只有 `compensation_event.lock_version` 保留独立技术乐观锁。
- Platform 自身的 Integration Readiness 与 Payment 的 Routing Coverage 仍由各自系统拥有，只是不再异步投影成 CCT Eligibility。
- Risk 仍保存每笔交易自己的 assessment/decision 证据；删除的是 CCT 的预计算资格证明，不是交易风控。
- 本次不改变六系统独立数据库、跨库禁止访问和同步 HTTP/MQ 的既定边界。

## 6. 验收标准

- CCT PDManer 中精确删除上述八张表、所有字段、索引、关系和图中节点。
- 生成器、字段元数据、结构测试和摘要与新模型完全一致。
- 当前规范、六库文档、Vault 文档、需求目录、全局序列和页面展示不存在仍声称上述能力已实现的内容。
- 历史计划保留原文，仅标注由本设计取代。
- Requirements 测试与 PDManer 自动验证通过；PDManer 4.9.3 应用内检查仍为人工门禁。
