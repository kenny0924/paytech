# CCT 实时交易资格与 Retention 延期实施计划

> 状态：已完成（2026-08-23）。设计依据见 [实时交易资格与 Retention 延期设计](./2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)。自动验证为 PDManer 76/76、Requirements 49/49 且 lint 通过；PDManer 4.9.3 应用内检查仍是人工门禁。本计划不授权 Java、Migration、物理数据库或部署。

## 任务 1：用测试锁定删除边界

- 在 PDManer 合同测试中先要求八张表不存在、模型统计重基线、仅保留必要技术锁。
- 在 Requirements 测试中先要求 CCT-I10/I13 为实时校验、V4 Hash 且不返回持久化 Eligibility 证明。
- 分别运行目标测试并确认它们因旧设计仍存在而失败。

## 任务 2：重建 CCT PDManer

- 从 Catalog 删除四张 Eligibility 表和四张 Retention 表。
- 同步生成器的类型、空值、注释、索引、关系、图组与字段元数据。
- 通过生成器重建 `client-contract.pdma.json`，禁止手工编辑生成物。
- 用结构测试取得最终表、字段、索引、关系、图和摘要数据。

## 任务 3：替换接口与下游快照语义

- CCT-I10 改为非持久化实时诊断；CCT-I13 改为每次调用 fail-closed 门禁。
- `commercial_context_hash` 从 V3 升 V4，并删除 Eligibility 输入。
- Commerce、Risk 和全局序列删除 Eligibility ID/Version/Hash 快照；Payment、Platform 的事实改为同步查询语义。
- 退役 CCT `ClientEligibilityChanged` 及仅用于 Evaluation 的异步订阅。

## 任务 4：全局延期 Retention

- 六库、Platform Support、Vault、数据库总览和需求目录统一为“默认保留、不自动销毁”。
- 删除当前设计中的通用 Retention/Legal Hold 表、事件、任务与自动销毁承诺。
- 保留业务专用过期、补偿、幂等、审计；生产真实受监管数据前设置重新设计与评审门禁。

## 任务 5：同步导航、统计与历史

- 更新 `AGENTS.md`、文档导航、项目上下文、数据库指南、CCT 物理决策与根 README。
- 将生成后的最终统计与 SHA-256 写回当前状态文档。
- 在仍可能被误读为当前规范的历史计划顶部增加取代说明，不改写历史正文。

## 任务 6：验证

- 运行 PDManer 生成器和完整模型测试。
- 运行 `npm --prefix requirements-site test`，并对涉及代码执行 lint。
- 搜索当前文档与需求源中的旧表名、七维 Evaluation、V3 Hash 和自动 Retention 现行语义。
- 报告自动验证结果，并保留 PDManer 4.9.3 应用内人工兼容性门禁。
