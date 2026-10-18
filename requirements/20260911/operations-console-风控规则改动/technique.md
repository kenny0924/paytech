# Operations 风控规则 V1：技术方案入口

文件性质：现行技术合同的导航与系统影响摘要，不替代或复制完整 Wire、SQL 和模型定义。

完整方案、差异、接口定义、数据与兼容策略见[需求与接口合同](../../../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)；业务范围见[requirement.md](./requirement.md)。

## 1. 系统影响

| 系统 | 本需求职责 |
|---|---|
| `risk-compliance-service` | API 与目录、配置校验、持久化、规范化和历史兼容、配置冻结限制 |
| `operations-console-bff` | 已授权请求转发、严格协议适配与安全目录投影 |
| `operations-console-web` | 原 V1 页面、规则编辑、动态配置表单与回显 |
| Platform / Gateway / Nginx | 既有权限、路由、请求大小及入口链路核对 |
| 根仓库 | 需求、模型工具、跨系统文档和环境验收入口 |

## 2. 接口入口

本次扩展 Authoring Context、Save、Detail 三个已有接口；新增 API、Platform 权限与路由注册 DML 均为 0。精确 Method/Path、DTO、必填/可空、上限、错误与兼容合同只在[现行合同](../../../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)维护。

## 3. 数据库入口

涉及持库服务为 Risk，数据库为 `mor_risk`；目录名中的 Operations Console 不表示 BFF/Web 拥有数据库。

| 工件 | 本例位置与性质 |
|---|---|
| DDL 分类入口 | [db/ddl.sql](./db/ddl.sql)，仅注释索引 |
| DML 分类入口 | [db/dml.sql](./db/dml.sql)，仅注释索引 |
| 已评审混合增量 | [Risk V1 增量 SQL](../../../paytech/risk-decision/docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql)，现有唯一增量执行来源 |
| 模型 | [Risk PDManer](../../../paytech/risk-decision/docs/database/risk-decision.pdma.json) |
| 物理规则及完整 SQL 入口 | [Risk 物理建模文档](../../../paytech/risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md) |

现有增量包含结构变更和配置目录数据。此处未将已执行的脚本拆成新的可执行迁移，也未新增初始化数据来源。SQL 文件存在不代表需要再次执行；具体环境、备份、迁移和核验记录见[交付台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#15-operations-risk-v1-配置增量2026-09-11)。

## 4. 研发、验收与交付入口

- 阶段确认、历史验收证据索引和提交记录：[delivery.md](./delivery.md)。
- 研发顺序、关键不变量与验证要求：见现行技术合同。
- 实际测试结果、浏览器与 test 验收、制品和提交记录：见[当前状态台账第 15 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#15-operations-risk-v1-配置增量2026-09-11)。
- 后续需求流程：见[公共流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)。新增范围先评审受影响部分，不借本次归档重新开展业务研发或部署。
