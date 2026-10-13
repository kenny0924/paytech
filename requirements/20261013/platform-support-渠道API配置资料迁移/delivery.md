# 渠道 API 配置资料迁移：确认与交付记录

## 1. 用户请求与当前范围

2026-10-13，用户在确认基本结构后要求“帮我写一个迁移需求出来”。本轮交付[需求评审稿](./requirement.md)和[资料盘点与方案草案](./technique.md)，登记到需求索引。

本次不恢复之前暂停的目录迁移，不修改原功能文档、应用代码、模型或 SQL，不执行数据库、部署或提交操作。

## 2. 沿用的对话决定

- 通用外部 API 配置和执行归 Platform，业务方经内部 HTTP 调用。
- 当前六表结构、共用字段表的方向/位置/路径/数组规则。
- 渠道标准 API 实现保存流程 JSON；先初始化依赖，再在本次上下文中执行节点及条件分支。
- 不保存流程运行状态，不提供跨调用或重启后的自动恢复；此前持续恢复的建议已经撤回。
- Payment 账号/MID/路由/业务状态、Vault 与 Card 权限边界保持其 Owner。

以上是需求整理的输入，不表示迁移实施、完整 JSON、HTTP 或物理模型已经完成。

## 3. 后续阶段建议

用户确认本次需求后，先细化迁移文件清单和受影响引用，再交付可审阅的迁移方案；实际搬迁和文档更新在明确启动后执行，完成后按 MIG-01～MIG-12 验收。应用研发、SQL 执行、发布及提交不属于这次资料迁移阶段。

## 4. 本轮验证

2026-10-13 实际检查结果：

- 新增三份需求材料，需求索引增加一条；19 处本地链接/锚点检查通过，索引到三份文档的引用通过，空白检查通过。
- 原功能目录仍存在，拟迁入的 Platform 目录尚未创建；本轮没有执行资料迁移。
- 按项目要求运行 `npm --prefix requirements-site test`：站点构建通过，137 项测试中 128 项通过、9 项失败，命令退出码为 1。失败项检查的是本次未修改的既有交易、模型、导航和规范内容；本轮不修改这些断言或其主题文件。
- 未运行 Java、数据库或真实外部接口测试；纯需求文档不涉及这些运行能力。

站点失败项名称保留如下，避免将站点检查记录为通过：

1. `checkout renders the immutable order context as the MVP sales model`
2. `Payment owns refund capacity and publishes only authoritative refund terminals`
3. `Commerce Client API V1 freezes exactly ten Hosted Checkout routes and five scopes`
4. `Commerce authority freezes the generated 15-table Client API model while preserving historical evidence`
5. `the current implementation ledger owns core model snapshots while entry documents route to it`
6. `Project context owns exactly the current public control plane without runtime limiter state`
7. `Commerce design and navigation expose the current privacy and Fulfillment-removal decision`
8. `Java tests are organized by executable entry and independent risk scenarios`
9. `shared writing conventions retain Payment responsibilities without promoting temporary exceptions`

历史应用实现及数据库状态继续引用[统一台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-integration-http-execution-20260921)。本节仅记录本次需求文档交付的验证证据，不表示迁移或功能实现完成。
