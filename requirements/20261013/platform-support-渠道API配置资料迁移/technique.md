# 渠道 API 配置资料迁移：资料盘点与方案草案

本稿服务于[迁移需求](./requirement.md)，记录迁移前证据和执行清单。2026-10-13 用户确认后已执行，下文第 1 节链接已指向迁移后位置，执行结果见[交付记录第 5 节](./delivery.md#5-迁移执行2026-10-13)。

## 1. 现状证据

2026-10-13 只读盘点：原目录 `requirements/20260914/payment-渠道API逻辑定义/` 共 81 个文件，包含 18 个 Markdown、23 个 JSON、36 张 PNG、2 个 Python 检查脚本及 2 个 SQL 草稿。该数量是本次迁移输入清单摘要，不是已实现功能或正式数据库模型统计；实际执行前重新核对。

建议目标 `requirements/20260914/platform-support-外部API配置与执行/` 尚不存在。

| 现有资料 | 与当前讨论的差异 | 迁移处理 |
|---|---|---|
| [原需求](../../20260914/platform-support-外部API配置与执行/legacy-requirement.md) | 标准 API 编排仍列为后续，输入/输出引用旧公共模型 | 按已确认的六表、标准 API 与原子 API 分层整理现行正文 |
| [归属决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)及[执行修订](../../20260914/platform-support-外部API配置与执行/platform-http-execution-amendment.md) | Owner 已为 Platform，但仍描述五类对象与统一单次目标调用 | 精确修订为原子单次操作与标准 API 同步流程两层，不扩大业务状态 Owner |
| [公共模型与响应场景](../../20260914/platform-support-外部API配置与执行/model-library-and-response-scenes.md) | 仍以独立对象模型和响应合同维护 | 保留历史范围，新字段合同归共用字段表 |
| [公共脚本](../../20260914/platform-support-外部API配置与执行/public-script-http-context-design.md) | 脚本 HTTP 对象原则适用；模型引用和上下文说明需同步 | 保留受控脚本语义，更新现行入口与上下文引用 |
| [原数据与 SQL 入口](../../20260914/platform-support-外部API配置与执行/db/README.md) | 前序 Payment 配置表与旧管理注册不能作为 Platform 六表 Schema | 保留历史 SQL 原文，新增清楚的适用范围与新设计入口 |
| [原页面说明](../../20260914/platform-support-外部API配置与执行/ui.md)与[设计源](../../../operations-console-web/docs/operations-console.pen) | 页面曾包含公共模型等前序结构 | 保留设计文件位置，标注与本轮结构的差异，不重画页面 |
| [Payment 账号与 MID 查询需求](../../20260921/payment-渠道账号与MID能力查询/requirement.md) | 有指向旧通用执行资料的引用 | 只更新相关链接，不迁移其模型、源码或查询合同 |

## 2. 建议实施步骤

1. 保存迁移输入清单，记录原路径、新路径、资料类型和处理方式；迁移前再次检查工作区与目标目录，保留其他任务修改。
2. 将原需求目录归位至 Platform 命名目录；原目录留下迁移说明。未经核对不覆盖同名目标。
3. 将现行功能需求、技术入口、六表字段稿和标准 API 流程设计集中整理；本迁移目录只记录任务边界和验收，不长期复制当前合同。
4. 对旧方案增加后继标记，保留历史正文与原适用范围；历史 SQL 和二进制图片不改内容。
5. 逐项更新当前引用及导航，再检查旧路径剩余命中；只允许迁移说明或明确的历史路径记录保留旧路径。
6. 检查本地链接、锚点、图片、JSON 语法与相关工具路径；旧样例校验如运行，只证明其历史样例没有损坏，不证明新执行器可运行。
7. 对照 MIG-01～MIG-12 交付迁移结果，更新统一状态台账；不进入应用研发或物理数据库实施。

## 3. 入口同步清单

实际执行前对全文引用重新扫描，至少覆盖：

- `requirements/README.md` 的原功能项和本次迁移项。
- `agent-docs/README.md`、`DOCUMENT_GOVERNANCE.md`、`PROJECT_CONTEXT.md`、`APPLICATION_ARCHITECTURE.md`。
- `agent-docs/decisions/2026-09-21-platform-integration-http-execution.md`。
- `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md` 与 `CURRENT_IMPLEMENTATION.md`。
- Payment 渠道账号与 MID 查询需求、相关决定中的通用执行引用。
- `requirements-site/content/requirements.ts` 与 `global-use-case-sequences.mjs` 中确受职责调整影响的条目。
- 迁移目录内部的 Markdown、示例、检查脚本和附件索引。

当前任务只新增迁移需求材料及其索引，不提前把这些待同步入口改成已迁移。

## 4. 验证与边界

| 验证 | 当前需求编写 | 后续实际迁移 |
|---|---|---|
| 新文档链接、锚点、空白与索引 | 检查 | 复核 |
| 来源文件与迁移清单完整性 | 记录盘点 | 逐文件核对；图片和历史 SQL 内容保持一致 |
| 旧路径残留与六表/上下文/流程一致性 | 列出要求 | 扫描并人工核对 |
| 需求站点 | 按项目要求运行，失败区分是否与本次相关 | 同步相关目录后再次验证 |
| Java、数据库、真实渠道或 Vault | 不适用 | 本资料迁移仍不涉及 |

本需求无新增或修改运行 HTTP 接口、无应用代码或正式模型变更、无可执行 DDL/DML、无部署动作。后续执行时只按文档迁移要求验证，不能使用编译或旧静态样例结果替代新引擎的测试。
