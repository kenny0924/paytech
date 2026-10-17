# Payment 接口开发入口

路径相对于本文件。单独打开 Payment 仓库时也遵守本入口；公共规则不在此复制。

新增、修改或重构同步业务接口，在首次修改实现前实际读取：

1. [Code-MoR 协作要求](../../AGENTS.md)及其任务路由。
2. [公共开发规范](../../agent-docs/DEVELOPMENT_CONVENTIONS.md)。
3. [Java 工程规范](../../agent-docs/JAVA_PROJECT_STRUCTURE.md)。
4. [公共写接口开发示例](../../agent-docs/examples/WRITE_API_EXAMPLE.md)。
5. [Payment 专项补充](docs/development/processor-conventions.md)及本次接口的业务合同。

实现前简述已读文档、本次 Processor/Context、本地原子写入和事务外步骤，然后执行已授权工作。完成后按公共示例第 11 节自查，说明对应实现、实际验证及不适用项；不增加用户审批。

本任务已完整读取且未变化的内容可以复用，不凭历史对话或文件名推断规则。公共文档缺失时先定位 Code-MoR 工作区；仍无法读取则说明缺失路径，暂停依赖该规范的实现，不自行复制另一套规范。Payment 既有源码中已标注的偏差不能作为新接口的例外。

未经用户明确要求不提交、推送、建分支、部署或执行数据库迁移；保留工作区已有修改。
