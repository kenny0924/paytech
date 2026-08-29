# Commerce 消费者分析与网络证据实施计划

最后更新：2026-08-24  
状态：历史 23 表实施阶段，已被 [Commerce 隐私与履约精简实施计划](./2026-08-24-commerce-privacy-fulfillment-simplification-implementation-plan.md) 取代  
范围：仅保留实施过程证据，不得作为当前模型或任务入口

> 当前任务不得按本文恢复 Consumer Blind Index、Network Vault Token、Key Version、设备标识、Acceptance Summary 或主动履约结构。

## 1. 自动契约先行

1. 更新 Commerce 模型测试的字段、表、索引、关系、图组、敏感字段禁止项和统计预期。
2. 更新现实场景测试，要求完整覆盖 `checkout_network_evidence`，并禁止恢复 `consumer_acceptance_summary`。
3. 运行旧生成器，确认测试因旧 Summary、旧 Vault 套餐和缺失网络分析结构而失败。

## 2. 物理模型生成

1. 修改 `generate-commerce-model.mjs` 的字段、索引、关系、图组和可变/不可变表清单。
2. 修改 `commerce-field-metadata.mjs`，为新增字段补齐中文名称、中文说明、闭合枚举和开放目录来源。
3. 重新生成 `commerce.pdma.json`，保持共享模板字节不变、内部 ID 确定性、数据库连接为空。
4. 以生成结果回填准确表/字段/索引/关系总数与模型/结构 SHA-256。

## 3. 权威文档同步

1. 更新 Commerce 逻辑数据库、物理决策和现实场景。
2. 更新数据库总览、项目上下文、文档导航和根目录长期规则。
3. 更新 Vault 规范，把 Token、Blind Index、Masked Value、Record Version、Metadata Hash 改为按真实消费者选配，并明确 Vault 不创建业务专表。
4. 更新 Payment Readiness 与 Payment Attempt 冻结字段，改为三种显式 Acceptance ID。
5. 更新 Risk 输入与类型化事实，明确假名化关联键、结构化维度及不得跨库读取的边界。
6. 更新需求目录、全局时序与相应测试。

## 4. 验证

1. 运行 Commerce 字段元数据、模型、现实场景和生成器 `--check`。
2. 运行 requirements-site 全量测试与相关 lint。
3. 搜索旧 Summary、Commerce 无消费者的 Vault 元数据/脱敏字段和旧 Payment Readiness Tuple，区分当前文档与明确标注的历史计划。
4. 检查 Git Diff，只保留本次授权范围内的设计资产修改。

PDManer 4.9.3 应用内检查由人工完成；未通过该门禁前不能宣称应用兼容性已经验证。
