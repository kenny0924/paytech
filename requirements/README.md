# 需求管理目录

这里按单项需求归集需求说明、技术方案与数据库变更入口。执行流程见[需求交付流程与阶段确认](../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)；每阶段完成后停下等待用户确认。

用户已指定日期目录和需求目录结构，并确认 `delivery.md` 为固定文件，`assets/` 与多库子目录按需创建。此前已有文档暂不批量迁移，本目录通过链接关联它们。

## 1. 目录与命名

```text
requirements/
├── README.md
└── 20260911/
    └── operations-console-风控规则改动/
        ├── requirement.md
        ├── technique.md
        ├── delivery.md
        └── db/
            ├── ddl.sql
            └── dml.sql
```

- 日期目录采用 `YYYYMMDD`，必须是有效日期。建议使用需求首次登记日期，跨日研发或部署时继续更新原目录，不按工作日复制需求。
- 需求目录采用 `<系统>-<改动内容>`，保留项目系统名称，例如 `operations-console-风控规则改动`。建议使用业务主入口或主要 Owner 命名，所有受影响系统列在技术方案中，不为一个跨系统需求创建多份正文。
- 同一天、同目录名视为同一需求；创建前先查索引与已有目录，不能覆盖旧文件。确为不同需求时使用更具体的改动名称。
- 需求与技术正文使用 Markdown；链接优先使用仓库相对路径。源代码、构建产物、凭据、数据库备份不放入需求目录。

## 2. 文件职责

| 文件 | 内容 | 主要维护阶段 |
|---|---|---|
| `requirement.md` | 背景、目标、业务场景、本期范围、不做项、验收标准与业务变更说明；确认记录链接 `delivery.md` | 需求评审及范围变更时 |
| `technique.md` | 现状差异、能力缺口、系统影响、技术方案、接口定义、数据库变更、兼容策略、实施与验证计划 | 分析、设计及技术变更时 |
| `delivery.md` | 阶段确认、测试与验收证据、部署/恢复方案、SQL 执行记录入口、提交号和遗留项 | 需求登记时创建，随各阶段推进更新 |
| `db/ddl.sql` | 本需求涉及的结构变更；明确是执行文件、归档快照还是引用索引 | 数据设计与发布准备 |
| `db/dml.sql` | 本需求涉及的数据变更，包括按需产生的业务目录、API Route/权限等注册数据；明确目标库与文件性质 | 数据设计与发布准备 |

`requirement.md`、`technique.md`、`delivery.md` 是每项需求的固定文件，按阶段逐步完善；未确认内容标为草案，不为了填满模板编造结论。无数据库变更时，明确标注“不涉及”；不要生成无意义 SQL。

## 3. 交付记录与按需目录

- **`delivery.md`：** 固定承载阶段确认及交付记录，避免需求与方案正文混入大量操作记录。记录历史证据时标明日期、范围和版本；跨仓库当前状态仍链接 `CURRENT_IMPLEMENTATION.md`，不另建一份状态台账。
- **`assets/`：** 有页面原型、评审截图或其他非敏感附件时再创建；只有实际需要时保留，不预建空目录。
- **多库变更：** 涉及多个数据库时使用 `db/<所属系统>/ddl.sql`、`db/<所属系统>/dml.sql`，由技术方案列明分别连接的数据库和执行顺序；不把跨库 SQL 混在一个脚本中。单库保留用户给出的扁平结构，BFF/Web 等无数据库系统不创建空的数据库子目录。

本例已创建 `delivery.md`，通过既有台账关联历史交付证据。没有需要归档的附件，且只涉及 Risk 一个数据库，因此不创建 `assets/` 或多库子目录。

## 4. 与现有权威文档和 SQL 的关系

- 本目录管理单次需求的工作材料；全项目需求目录与全局时序仍由 `requirements-site/content/requirements.ts` 和 `requirements-site/content/global-use-case-sequences.mjs` 拥有。需求批准后同步这些入口，不另设相互独立的业务事实源。
- 稳定业务/技术规范与跨系统合同按[文档治理](../agent-docs/DOCUMENT_GOVERNANCE.md)确定 Owner。已有正式合同优先链接；需要迁移其权威位置时单独核对引用，不在归档时复制成两个现行版本。
- 服务的 PDManer、完整 Schema 和单一初始化 DML 仍在所属服务仓库。需求级增量 SQL 与完整初始化 DML 用途不同，不能替换或重复执行。
- 本例的 `ddl.sql`、`dml.sql` **仅为注释索引，不含可执行语句**，均指向此前已评审的 Risk 混合增量脚本；不是本次新增的两份迁移。历史执行状态以台账为准，不能因为新建文件再次执行历史迁移。
- 未来产生实际 SQL 时，在技术设计阶段明确唯一执行来源、目标库、前置状态、顺序、是否允许重跑、恢复方式与核验方法，再评审执行文件。本目录不自动成为迁移工具的输入，不自动扫描执行，也不携带 SQL 应用授权。

## 5. 需求索引

| 登记目录 | 需求 | 主入口/Owner | 关联编号 | 材料 |
|---|---|---|---|---|
| `20260911` | Operations 风控规则 V1 配置改动 | Operations Console / Risk | RCO-14、RCO-I12、E2E-15 | [需求](./20260911/operations-console-风控规则改动/requirement.md) · [技术入口](./20260911/operations-console-风控规则改动/technique.md) · [交付记录](./20260911/operations-console-风控规则改动/delivery.md) |
| `20260911` | 风控规则集列表优化 | Operations Console / Risk | RCO-15 / RCO-I12 | [需求](./20260911/operations-console-风控规则集列表优化/requirement.md) · [技术入口](./20260911/operations-console-风控规则集列表优化/technique.md) · [交付记录](./20260911/operations-console-风控规则集列表优化/delivery.md) |
| `20260911` | 风控规则集历史与字典管理 | Operations Console / Risk | RCO-16 / RCO-I12 / E2E-15 | [需求](./20260911/operations-console-风控规则集历史与字典管理/requirement.md) · [差异与影响](./20260911/operations-console-风控规则集历史与字典管理/technique.md) · [交付记录](./20260911/operations-console-风控规则集历史与字典管理/delivery.md) |
| `20260911` | OE 与 Client 属性及模型边界梳理 | Client & Contract | CCT-04、CCT-06、CCT-11、CCT-I11/I13 | [需求](./20260911/client-contract-OE与Client模型梳理/requirement.md) · [模型评审稿](./20260911/client-contract-OE与Client模型梳理/technique.md) · [交付记录](./20260911/client-contract-OE与Client模型梳理/delivery.md) · [四系统模型实施](./20260911/client-contract-OE与Client模型梳理/model-implementation.md) |
| `20260912` | 决策流配置 | Operations Console / Risk | RCO-17，阶段 1/2 已确认，阶段 3 待整体确认（动作可选简化已确认） | [需求](./20260912/operations-console-决策流配置/requirement.md) · [差异与影响](./20260912/operations-console-决策流配置/technique.md) · [页面方案](./20260912/operations-console-决策流配置/ui.md) · [交付记录](./20260912/operations-console-决策流配置/delivery.md) |
| `20260912` | 自建 Card Vault 与支付卡采集 | Payment / Card 模块 | Payment单请求提交已确认；阶段3字段/恢复与数据稿已同步修订 | [需求](./20260912/payment-自建CardVault/requirement.md) · [差异与影响](./20260912/payment-自建CardVault/technique.md) · [接口字段](./20260912/payment-自建CardVault/api-contract.md) · [数据设计](./20260912/payment-自建CardVault/data-design.md) · [交付记录](./20260912/payment-自建CardVault/delivery.md) |

| `20260912` | 决策流新增节点处理结果条件 | Operations Console / Risk | RCO-18，纳入 RCO-19 评审 | [需求](./20260912/operations-console-决策流节点处理结果/requirement.md) · [技术入口](./20260912/operations-console-决策流节点处理结果/technique.md) · [交付记录](./20260912/operations-console-决策流节点处理结果/delivery.md) |
| `20260912` | 统一风控变量管理与条件编排 | Operations Console / Risk | RCO-19，阶段 1/2/3 已确认、阶段 4 研发中 | [需求](./20260912/operations-console-统一风控变量管理与条件编排/requirement.md) · [差异与影响](./20260912/operations-console-统一风控变量管理与条件编排/technique.md) · [完整方案](./20260912/operations-console-统一风控变量管理与条件编排/design.md) · [交付记录](./20260912/operations-console-统一风控变量管理与条件编排/delivery.md) |

| `20260912` | Payment 应用初始化 | Payment Orchestration | 应用基础工程 | [需求](./20260912/payment-应用初始化/requirement.md) · [技术方案](./20260912/payment-应用初始化/technique.md) · [交付记录](./20260912/payment-应用初始化/delivery.md) |

| `20260913` | Payment 业务模块划分 | Payment Orchestration | 四业务模块工程拆分；验证与状态见台账 | [需求](./20260913/payment-业务模块划分/requirement.md) · [模块方案](./20260913/payment-业务模块划分/technique.md) · [交付记录](./20260913/payment-业务模块划分/delivery.md) |

| `20260913` | 全系统支付渠道命名统一 | Payment / 关联核心系统与入口 | 映射已确认；本地实施与验证记录见当前台账 | [需求](./20260913/payment-支付渠道命名统一/requirement.md) · [关联评审](./20260913/payment-支付渠道命名统一/technique.md) · [交付记录](./20260913/payment-支付渠道命名统一/delivery.md) |

| `20260913` | Payment Execution 设计 | Payment / Execution，关联 Commerce | 原基线与 Token 后继修订分开评审；实际状态见台账 | [需求](./20260913/payment-Execution设计/requirement.md) · [技术入口](./20260913/payment-Execution设计/technique.md) · [详细设计](./20260913/payment-Execution设计/design.md) · [Token 修订](./20260913/payment-Execution设计/instrument-token-amendment.md) · [交付记录](./20260913/payment-Execution设计/delivery.md) |

| `20260913` | 菜单权限与 API 配置管理 | Platform Support / Operations Console | PLT-16、PLT-17、E2E-16；阶段 1/2 已确认；36 个新端点、零 DDL、227 行注册、7 张 Pencil 的阶段 3 评审稿 | [需求](./20260913/platform-support-菜单权限与API配置管理/requirement.md) · [技术入口](./20260913/platform-support-菜单权限与API配置管理/technique.md) · [交付记录](./20260913/platform-support-菜单权限与API配置管理/delivery.md) |

本索引只做检索，不复制实现、测试数量或部署状态。

| `20260913` | Client API 接入管理 | Platform Support / Operations Console | PLT-02、PLT-I03、PLT-16、PLT-I19、PLT-I06、E2E-16；Scope 目录、凭证查看与紧急撤销；实现、验证、部署及提交状态见当前台账 | [需求](./20260913/platform-support-ClientAPI接入管理/requirement.md) · [分析入口](./20260913/platform-support-ClientAPI接入管理/technique.md) · [阶段 3 方案](./20260913/platform-support-ClientAPI接入管理/design.md) · [页面](./20260913/platform-support-ClientAPI接入管理/ui.md) · [交付记录](./20260913/platform-support-ClientAPI接入管理/delivery.md) |
| `20260914` | 租户访问管理与客户/合作伙伴成员权限查询 | Platform Support / Operations Console | PLT-15、PLT-I02、E2E-16；租户安全访问管理、成员权限只读；当前进度见实施台账 | [需求](./20260914/platform-support-租户访问与成员权限查询/requirement.md) · [差异与影响](./20260914/platform-support-租户访问与成员权限查询/technique.md) · [方案与页面](./20260914/platform-support-租户访问与成员权限查询/design.md) · [交付记录](./20260914/platform-support-租户访问与成员权限查询/delivery.md) |
