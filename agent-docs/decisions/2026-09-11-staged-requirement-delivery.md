# 需求交付逐阶段确认决策

- Decision status: Confirmed（逐阶段确认原则；公共文档中的八阶段划分待用户评审）
- Implementation status: In progress（流程文档评审中）
- Deployment status: Not applicable
- Decision date: 2026-09-11
- Supersedes / Superseded sections: 仅取代新需求默认连续推进到研发、部署和提交的工作方式；保留根 `AGENTS.md` 的独立动作授权、Superpowers 显式授权及各主题 Owner 规则。

## 背景与决定

用户在完成一次需求评审、跨系统研发、test 部署和提交后，要求提炼公共流程，并明确要求“每一步完成都应该停一下让我确认一下”。

此要求适用于 Code-MoR 后续需求交付。每个已约定阶段完成后，Agent 提供可审阅的结果、证据、遗留项和下一阶段的具体范围，结束当前阶段并等待用户确认；不得将沉默、超时、开始时的一次“开始做”视为后续阶段全部获批。阶段内的普通编辑和检查不反复请示。

用户可以明确调整本次阶段、合并确认点或限定终点。确认范围沿当前需求持续有效；不重复询问已经明确授权的相同事项，也不把前一个需求的部署、SQL 或提交授权迁移到新需求。

## 权威与范围

用户随后明确指定需求目录为 `requirements/YYYYMMDD/<系统>-<改动内容>/`，包含 `requirement.md`、`technique.md`、`db/ddl.sql`、`db/dml.sql`，并确认固定增加 `delivery.md`，`assets/` 与多库子目录按需创建。该结构替代公共流程草案中新增需求记录默认放在 `agent-docs/plans/` 的建议；既有设计与执行脚本不因此迁移或改变权威。目录入口见 [requirements/README.md](../../requirements/README.md)。本次确认对象是目录补充方案，不据此将此前八阶段划分标记为已确认。

- 根 [`AGENTS.md`](../../AGENTS.md) 的 `Change discipline` 继续拥有 Agent 行为和动作授权。
- [`REQUIREMENT_DELIVERY_WORKFLOW.md`](../REQUIREMENT_DELIVERY_WORKFLOW.md) 提供公共阶段、交付物和评审模板；首次八阶段划分作为草案供本次用户确认。
- 业务、架构、接口、数据库和测试规范仍由 [`DOCUMENT_GOVERNANCE.md`](../DOCUMENT_GOVERNANCE.md) 路由，不借流程修改其语义。
- 本决策不授权业务代码改动、执行 SQL、部署、提交、推送、发布或使用 Superpowers；每项具体行动按当前需求和确认范围判断。
- 本文记录长期协作方式，不复制任何应用的实现、测试、部署或提交状态。
