# 2026-10-22 决定：工作流并入 Platform Support，不建独立 workflow-approval 服务

- Decision status: Confirmed
- Implementation status: In progress（需求 [platform-support：工作流](../../requirements/20261021/platform-support-工作流/requirement.md) 阶段 4 研发中）
- Deployment status: Not deployed
- Decision date: 2026-10-22（用户 2026-10-21 决定归属，2026-10-22 确认技术方案）
- Supersedes / Superseded sections：[目标架构 V2 决定草案](./2026-10-17-target-architecture-v2.md)及其[服务坐标表](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md)中独立服务 `workflow-approval`（`workflow-approval-service`、端口 18121、库 `mor_workflow_approval`、`paytech-workflow-approval-{api,core,app,test}`）的规划；D13 的语义不变。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 背景

目标架构 V2 把审批规划为独立服务 `workflow-approval`，阶段 0 只留下 `paytech/workflow-approval/README.md` 占位，没有工程、库和表。用户要做的不只是审批，而是“流程 + 节点动作”：Operations 用流程图把跨部门工作串在一条线上，节点可以挂审批人，也可以挂带输入、输出与验证的动作。它只服务 Operations 用户，人员、权限、文件、通知和审计都已经在 Platform Support。

## 决定

1. **归属：** 工作流是 Platform Support 的业务模块 `paytech-platform-support-workflow`（包根 `com.paytech.platform.support.workflow`），表放在 `mor_platform`，由 Platform 的进程、事务管理器和注册数据承载；不建独立服务、端口或库。
2. **删除规划坐标：** 撤销 `workflow-approval` 的目录、Reactor 注释、服务目录、事件目录服务名与本地端口登记；V2 的服务数相应减少一个。
3. **D13 不变：** 工作流只记录“谁在何时完成了什么动作、谁同意或驳回了什么”及动作输出与证据；不复制业务对象，不调用业务写接口，不作为业务执行开关。业务服务执行时仍须自行校验并记录最终决定，“审批通过 ≠ 可执行”。
4. **对业务的通知只走 MQ：** 状态变化的业务回调发布到 `topic_platform_workflow_event`，订阅方自行幂等、自行校验业务事实；不回调外部 HTTP 地址。
5. **现有 Maker-Checker 不迁移：** Finance、billing、commercial 等服务内已有的本地 Maker-Checker 保持原样；接入工作流须由各 Owner 单独立项。

## 影响

- `paytech/workflow-approval/` 与 `services.yaml`、arch-rules `ServiceCatalog`、`YamlEventCatalog` 中的 `workflow-approval` 删除；本地环境端口表不再预留 18121，`mor_workflow_approval` 不再创建。
- 文档中“审批经 workflow-approval”的规划改读为“经 Platform 工作流”；已完成需求与历史计划正文保留原文。
- 表设计、接口与页面以需求技术方案为准；数据库是否执行、代码与部署进度只记录在当前台账。
