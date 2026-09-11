# PDManer 单模型与短文件名决策

Decision status: Confirmed\
Implementation status: Complete\
Deployment status: Not applicable\
Decision date: 2026-09-11\
Superseded sections: 当前文档、生成器或工作区中使用 `Code-MoR ... Database V1.pdma.json` 作为模型路径，以及同一持库服务可长期保留多个 PDManer 文件的解释

## 背景

PDManer 4.9.3 打开或保存模型时可能按 JSON 内部展示名称生成新的长文件名，同时留下短名删除、长名未跟踪和 `.back_*` 备份。展示元数据变化不代表产生了第二个物理数据库设计；若生成器和文档跟随该文件名，会造成路径漂移和模型检查失效。

## 决定

1. 每个持有数据库的部署服务默认只有一个权威 PDManer 模型；只有另行批准的独立物理数据库和生命周期才允许第二个模型。
2. 权威文件固定使用 `docs/database/<service-short-name>.pdma.json`，文件名小写、无空格并采用 `kebab-case`。六个核心服务分别使用 `client-contract.pdma.json`、`commerce.pdma.json`、`payment-orchestration.pdma.json`、`risk-compliance.pdma.json`、`finance.pdma.json`、`platform-support.pdma.json`；Vault 使用 `vault.pdma.json`。
3. JSON 内部 `name`、`describe` 可以使用完整展示名称，但不得改变权威文件路径。生成器、测试、Schema 来源注释、README 和当前文档只引用短路径。
4. PDManer Save-As 文件和同目录长名副本只属于本地临时证据。发现副本时先比较结构并把真实修改合并回短名文件，再移除副本；不得同时提交两个模型。`.back_*` 是被 Git 忽略的本地恢复备份，不属于权威模型，不得被生成器、测试或文档引用。
5. 模型文件名收敛不改变任何表、字段、关系、索引或业务决定，也不授权执行 DDL、迁移数据库或部署。

## 结果

文件路径稳定且便于脚本、链接和命令使用；PDManer 展示元数据变化不再触发全仓路径迁移。模型内容、生成状态和人工 GUI Review 继续按各系统 Owner 与 `CURRENT_IMPLEMENTATION.md` 判断。
