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

## 5. 迁移执行（2026-10-13）

### 5.1 用户确认

用户审阅影响分析后确认（原话要点）：

| 问题 | 用户决定 | 落实位置 |
|---|---|---|
| 与 2026-09-21“单次执行”决定冲突 | 按新设计：对内提供标准 API，渠道可以进行接口编排 | 新增[2026-10-13 决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)，09-21 决定标注取代范围 |
| 流程内冲正与 Payment 恢复边界 | 冲正属于危险操作，理论上不会这么编排 | 现行需求与流程设计明确不编排冲正、撤销、退款等资金反向操作 |
| 旧 DML 误执行风险 | 按建议 | `db/README.md` 增加禁止执行说明；SQL 文件本身不改 |
| `t_integration` 命名混淆 | 按建议 | 技术说明第 3 节区分 Integration Readiness，改名列入待决 |
| 需求站点同步范围 | 按建议 | 不新增接口或时序，站点内容不改 |

并要求“如果没有异议，可以开始执行”。本次授权仅覆盖文档迁移；应用代码、SQL、提交、推送和部署不在授权内。

### 5.2 实际变更

- `git mv` 原目录到 `requirements/20260914/platform-support-外部API配置与执行/`；原 `requirement.md`、`technique.md` 改名为 `legacy-requirement.md`、`legacy-technique.md`，目录内对两者的 6 处链接同步更新。
- 新增现行 `requirement.md`、`technique.md`、`data-design.md`、`flow-design.md`；`delivery.md` 增加第 23 节。
- 17 份历史 Markdown 标题后增加迁移后继说明；`registry-contract.md` 检查命令路径更新。历史正文、SQL、JSON、PNG、Python 内容未改。
- 原目录仅保留 [README 迁移说明](../../20260914/payment-渠道API逻辑定义/README.md)，含旧路径到新路径对应关系。
- 权威与入口同步：根 `AGENTS.md`、`agent-docs/README.md`、`DOCUMENT_GOVERNANCE.md`、`PROJECT_CONTEXT.md`、`APPLICATION_ARCHITECTURE.md`、`database/PLATFORM_SUPPORT_DATABASE.md`、`CURRENT_IMPLEMENTATION.md`、09-21 两份决定、Payment 账号与 MID 查询需求、`requirements/README.md`。
- 未修改 `requirements-site/content/*`：现有条目不含此能力（`PLT-I13` Integration Readiness 为不同概念），按“不因资料改名新增业务接口”保持不变。

### 5.3 验证

- 文件完整性：迁移前后按 SHA-1 比对，81 个文件全部存在；全部非 Markdown 文件（23 JSON、36 PNG、2 Python、2 SQL）内容一致。
- JSON：23 个文件语法检查通过。
- 链接：受影响的 38 份 Markdown 中 1466 处本地链接与锚点检查，本次新增或修改的链接全部可解析；唯一告警是 `CURRENT_IMPLEMENTATION.md` 中既有的 Payment `processor-conventions.md` 锚点，与本次无关，未修改。
- 旧路径残留：仅在原目录迁移说明、迁移需求的历史描述、历史交付命令及后继说明中保留。
- `npm --prefix requirements-site test`：137 项中 128 通过、9 失败，退出码 1；失败项与第 4 节列出的 9 项完全相同，本次没有新增失败。
- 未运行两个历史检查脚本：它们会重写 `assets/*-verification.json`，运行会改变历史验证结果。Pencil 文件为加密格式，无法扫描其中引用。
- 未运行 Java、数据库或外部接口测试；本次不涉及。

### 5.4 验收对照

| 编号 | 结果 |
|---|---|
| MIG-01 | 通过：Platform 命名目录，原日期 `20260914` 保留，原目录提供路径对应 |
| MIG-02 | 通过：[数据设计](../../20260914/platform-support-外部API配置与执行/data-design.md)六表，公共模型/响应合同标为被取代 |
| MIG-03 | 通过：四方向、四位置、完整路径、数组规则与唯一范围 |
| MIG-04 | 通过：[流程设计](../../20260914/platform-support-外部API配置与执行/flow-design.md)第 2、3 节 |
| MIG-05 | 通过：条件连线单分支、兜底；表达式异常返回 `ERROR` 标为建议待 Schema 确认 |
| MIG-06 | 通过：原子/标准两层次数边界，无运行表与续跑；补充不编排资金反向操作 |
| MIG-07 | 通过：Payment 源码、模型、SQL 未改动，仅更新一处引用文字 |
| MIG-08 | 通过：历史文件内容哈希一致，旧 DDL/DML 标注禁止执行 |
| MIG-09 | 通过：链接/锚点检查 |
| MIG-10 | 通过：治理、入口、索引与台账同步；站点不涉及 |
| MIG-11 | 通过：[技术说明第 6 节](../../20260914/platform-support-外部API配置与执行/technique.md#6-待决事项)列出 8 项待决 |
| MIG-12 | 通过：无代码、模型、SQL、环境或权限变更，无提交或部署 |

### 5.5 遗留

技术说明第 6 节的 8 项待决事项另行立项细化；本次变更未提交，等待用户决定是否提交。
