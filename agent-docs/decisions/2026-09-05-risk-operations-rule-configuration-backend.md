# Risk Operations Rule Configuration Backend 首期决策

- Decision status: Confirmed
- Implementation status: Implemented and locally verified
- Deployment status: Not deployed
- Decision date: 2026-09-05
- Scope: Risk 私有 `CHECKOUT_PREPARE` Rule Set 作者配置、冻结、TEST Candidate 与八项静态检查
- Supersedes: 无广泛取代；仅在本记录明确的首期生命周期和配置边界内补充当前 Risk Decision Engine 权威
- Superseded sections: 无

> 实现、物理数据库、验证和部署的当前状态及精确证据只以
> [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 为准。本记录冻结业务与架构决定，不复制
> 会变化的本地验证快照；本地实现完成不表示已经部署。

## 已确认决定

1. 首期只在 `risk-compliance-service` 提供受控私有后端：Authoring Context、Rule Set 的完整不可变
   DRAFT 快照与 Freeze、单一冻结快照的 TEST Candidate、查询和八项静态检查。它不实现 Operations
   Web/BFF、Gateway/Platform 权限、Shadow、审批、激活、真实 Checkout 评估、Feature 执行或部署。
2. 对外只发布 `DecisionRuleSetManagementApi` 与 `DecisionReleaseManagementApi` 两个轻量内部
   `@HttpExchange` 合同。Risk 继续拥有内容、状态、幂等、并发和审计不变量；未来浏览器访问必须经
   Operations BFF 和既有授权边界，不能直连 Risk。
3. 保存始终创建下一份完整 DRAFT；新的保存可以在同一事务将当前被替代的 DRAFT 与其 Companion Point
   Version 转为 `RETIRED`，但不能改写已冻结或已退役的内容子图。八项检查全部通过后 Candidate 保持
   `VALIDATING`；只有后续独立 Shadow 命令在重验后才可进入 `SHADOWING`。
4. 四种写命令使用服务级持久幂等和版本化 Request Hash。Validate 采用“事务 A、只读一致性快照装载、
   事务外纯检查、事务 B”的同键恢复：`VALIDATING + zero checks` 只能由原 `Idempotency-Key` 恢复；
   部分检查或不匹配状态一律失败关闭，不加入后台任务、MQ 或额外恢复表。Authoring Context、两类列表与
   两类详情等多 SQL 查询也各自在一个短 `read-only REPEATABLE_READ` 快照中完成，不加读锁。
5. 首期 Template 恰有九个可引用 Context Field、三个零参数终态 Decision Action；Candidate 闭包必须证明
   Feature/Feature Dependency、Action Parameter Definition/Binding、System Obligation 与 Limit Binding
   都是显式零集合，不能把缺失查询当作通过。
6. 静态检查固定且按顺序持久记录：`REFERENCE_INTEGRITY`、`AST_SHAPE`、`AST_TYPES`、
   `FEATURE_DEPENDENCY`、`HANDLER_HASH`、`ACTION_PARAMETERS`、`TERMINAL_ACTION`、
   `SYSTEM_OBLIGATION`。每次完成 Validate 恰好生成八条可重取事实；不首错即停。
7. Risk 首次数据库实施必须交付与批准 PDManer 一致的**完整** Schema DDL 与唯一初始化 DML，不能只建
   本切片表。DDL 不得创建物理 `CHECK`/`FOREIGN KEY`，也不得引入 Flyway、Liquibase、Testcontainers 或
   应用启动建表/Seed。生成 DDL 前仍须闭合 Risk 侧 COMP-01、DB-01 与 Vault V1 模型前置。
8. 首期 `release_no` 是 `1..2147483647` 的无前导零正十进制值。因为本期只有一个可创建 Candidate 的
   `CHECKOUT_PREPARE` Point，创建命令在该 Point Root 锁内校验已有编号并分配全局下一值；增加第二 Point、
   第二写入方或更大编号前必须另行批准显式全局序列资源，不能使用未锁定 `MAX` 或截断 Snowflake。
9. Authoring、首次保存、Freeze 与 Candidate 创建继续要求完整工程 Action 目录为 `ACTIVE`。已经持久化的
   历史 Rule Set/Candidate 详情及成功命令重放只校验实际引用 Action Version 的稳定身份、Handler 与实现
   Hash；被引用版本后来合法转为 `RETIRED` 或无关目录项变化，不得使历史事实不可读或不可重放。

## 权威与状态

- 完整 API、数据投影、Hash、AST、并发、校验和验收合同只由
  [`2026-09-05-risk-operations-rule-configuration-backend-design.md`](../../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)
  持有；本决策不复制第二套业务合同。
- 执行顺序、文件范围和任务门禁只由
  [`2026-09-05-risk-operations-rule-configuration-backend-implementation.md`](../../docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md)
  持有。
- 当前实现、验证、物理数据库、部署和 Gate 状态只由
  [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 持有。本地实现和验证不表示本切片已经部署
  或具备生产可用性。
