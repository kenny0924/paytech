# Risk 流程结构材料

[数据说明](../../data.md)定义用途、状态、资格和事务。model.patch.json 是阶段 3 针对唯一 PDManer 的追加提案证据，model-baseline.json 固定原基线，ddl.sql 为对应 4 表差异；dml.sql 为无必需数据说明。

现行生成源为 `scripts/pdmaner/risk/manifests/09-decision-flow.mjs`，原 manifest/策略/Oracle 统一生成模型与完整 Schema，不以提案补丁作为第二份生成权威。当前状态见 [当前台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。本机人工 SQL 授权及执行证据见 [local-application.md](./local-application.md)。不得由本机授权推断 test 可以执行；应用及测试不自动建表或初始化业务数据。
