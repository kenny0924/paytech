# 文档治理与规范冲突收敛决策

- Decision status: Confirmed
- Implementation status: In progress
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Supersedes: 与本文六项决定冲突的现行规范表述；历史设计本身不重写

## 1. 背景

Code-MoR 的稳定规范、当前实现状态、历史设计和仓库运行说明长期追加在多个入口中，已经出现重复、状态漂移和互相矛盾。本文只冻结需要跨文档传播的决定；各业务系统的事实仍由其专题文档和已批准模型拥有。

## 2. 已确认决定

### D1. 文档按主题拥有权威，不使用一条笼统的全局优先级

冲突先按作用域处理：当前用户决定最高；项目根 `AGENTS.md` 只拥有 Agent 行为、项目级安全护栏、变更纪律和任务路由；命中主题的唯一权威文档拥有业务、架构和技术语义。其后才依次参考仓库 README 的本仓库实现/运行事实、自动注入规则摘要、全局 fallback、历史设计与计划。根 `AGENTS.md` 中的业务边界只是防误操作的薄投影，不能覆盖主题 Owner。

同一主题只能有一个现行规则来源。导航页和自动注入规则只能链接或薄投影，不能另行定义同义规范。易变的实现、验证和部署事实集中到 `agent-docs/CURRENT_IMPLEMENTATION.md`；架构与编码规范不复制这类状态。

### D2. Vault V1 的正式实现是 Go Server + Java Client

独立 `vault-service` 的 Go Server 与 Java Client 是 Vault V1 的正式代码实现，不是临时 Simulator。当前 local/test 运行、安全材料和调用方接入成熟度不构成生产就绪声明：生产 KMS、强服务身份、真实业务调用方接入、部署和合规验证仍需各自完成。

Vault V1 只承诺当前三表模型与 Protect/Reveal。Binding、Copy、Blind Index、Masked Value、Metadata/Record Version、Retire/Destroy 等能力不属于 V1。现有业务文档对这些能力的依赖记录为待迁移缺口；不得假装现有 V1 已支持，也不得仅删字段而破坏消费者能力。新增能力必须由真实消费者触发一个窄范围 V2 设计，并同步 API、模型、安全策略和迁移方案。

### D3. DataObject 以 Maven 业务模块为可见性边界

`DataObject` 是所属业务能力 JAR 内部的持久化模型。该 JAR 内的 Repository、Domain Service、Service 和必要的内部应用代码可以直接使用或返回自己的 DataObject；这不是跨模块合同，也不授权 Controller/API 将其作为传输对象。

跨 Maven 模块时，调用方不得导入、返回、接收或依赖对方的 DataObject、Repository、Mapper、表或 CRUD 接口。例如 Accounting 不得使用 Tax 的 DataObject。跨模块协作只能调用 Owner 模块明确发布的窄业务能力接口；该接口的参数和返回值使用 Owner 定义的 Domain / Application Command / Result。远程 `*-api` DTO 仍只属于 HTTP 传输边界。

模块内允许 DataObject 不等于鼓励任意穿透。若领域模型需要保护不变量、聚合多个表、隔离持久化变化或跨模块发布，应在边界处转换为 Domain/Application 对象；简单且没有这些隔离价值的模块内流程无需为了形式额外复制同义对象。

> Superseded scope (2026-09-05): 下列历史口径仅在“Domain Service 接口可选”范围内由 [`2026-09-05-superpowers-and-service-interface-policy.md`](./2026-09-05-superpowers-and-service-interface-policy.md) D2 取代；本节的 DataObject Owner-JAR 边界继续有效。
>
> 历史口径：Domain Service 接口也遵守必要性门禁；跨模块发布的业务能力或可替换端口必须使用接口，仅在本模块内部使用、只有一个实现且没有替换边界时可以直接使用具体类。

### D4. Foundation 不使用 Testcontainers 或启动时自动重建测试 Schema

Foundation 与业务服务统一复用仓库 `local-environment` 的 MySQL、Redis 和 RabbitMQ。自动测试不得启动 Testcontainers，不得通过 `spring.sql.init`、应用启动器或测试代码执行 `DROP/CREATE`、Migration 或 Seed。

测试 Schema/DML 是审核后的手工 SQL，由操作员显式应用。测试只清理自己命名空间或当前用例拥有的数据；依赖或 Schema 缺失时必须快速失败并给出可执行的准备说明。现有 Foundation Redis Testcontainers 和 Compensation Event 自动 Schema 初始化是已决定待迁移项，在代码及门禁完成前不能标记为已符合规范。

### D5. Finance 手工缴税分离“事前承诺”与“事后事实”

正常计划内缴税先创建本系统 Tax Payment，并在外部操作前校验现金容量、并发活动槽和其他当前不变量；只有通过后才允许形成新的外部支付承诺。

已经发生的真实银行扣款属于不可丢失的外部事实。它不得因为事前容量、归属或匹配条件不满足而被拒绝登记，也不得被伪装成已经满足正常 Tax Payment 创建门禁。系统保留银行流水/证据并进入 Reconciliation Exception；在匹配和解决前不得把税务负债标记为已结清。当前模型能否无损表达这条分支以 Finance 专项核对为准，缺字段或状态时先设计迁移，不能虚构已有能力。

### D6. 历史、规范和状态分开维护

现行规范保留稳定规则；实施计划和历史设计保留当时上下文，并用 `Decision status`、`Implementation status`、`Deployment status`、`Superseded sections` 标识其有效范围。历史文件不通过大规模改写伪装成现行文档。

数据库模型、DDL 和代码中的既有偏差必须进入状态台账，并通过专项实施和验证关闭。文档修正只代表规则已收敛，不代表六库模型、物理数据库、Foundation 测试或生产环境已经迁移。

## 3. 主题权威入口

完整路由见 [`DOCUMENT_GOVERNANCE.md`](../DOCUMENT_GOVERNANCE.md)，当前落地状态见 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。本决策负责上述六项冲突的结论，不替代各系统业务模型。
