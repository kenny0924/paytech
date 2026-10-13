# 外部 API 配置与执行

- 首次登记：2026-09-14（原目录 `requirements/20260914/payment-渠道API逻辑定义/`）
- 现行整理：2026-10-13，依据[资料迁移需求](../../20261013/platform-support-渠道API配置资料迁移/requirement.md)
- Owner：Platform Support；关联方：Payment、Operations Web/BFF
- 状态：现行设计评审稿。归属与分层已确认，完整 JSON、HTTP Wire、材料交接及物理模型待细化；没有应用代码、正式模型或 SQL 执行。

- [技术说明与边界](./technique.md)
- [六表数据设计](./data-design.md)
- [初始化、运行上下文与流程](./flow-design.md)
- [flow_definition_json 结构（已确认）](./flow-definition-contract.md)
- [原子 API definition_json 结构（已确认）](./integration-api-definition-contract.md)
- [标准 API 内部调用接口（已确认）](./internal-api.md)
- [渠道账号材料交接（已确认）](./material-handoff.md)
- [六表物理设计（已确认）](./physical-design.md)
- [Operations 页面方案（已确认）](./page-design.md)
- [管理接口与注册数据（已确认）](./admin-api.md)
- [研发计划 · 第 1 步（已确认）](./dev-plan-step1.md)
- [研发计划 · 第 2 步（已确认）](./dev-plan-step2.md)
- [研发计划 · 第 3 步（已确认）](./dev-plan-step3.md)
- [交付记录](./delivery.md)
- 权威决定：[2026-09-21 Platform 归属](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)、[2026-10-13 标准 API 与流程编排](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 实现与验证状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-standard-api-flow-20261013)

本目录内 `legacy-*`、各 amendment/contract/design 及 `assets/`、`db/` 是 2026-09-14～09-21 的历史资料，取代范围见[技术说明第 5 节](./technique.md#5-历史资料与取代关系)。

## 1. 背景与目标

外部接口配置最初以 Payment 渠道接入为场景设计，后续推广为通用外部 API 配置与执行，并确认由 Platform 管理和执行。业务系统需要的是稳定的“标准 API”，而不是直接面对每个渠道各不相同的协议步骤。

目标：

1. Platform 对内提供标准 API（例如 `PAY`、`AUTHORIZE`），统一业务请求与响应字段。
2. 每个渠道通过原子 API 描述单次外部协议操作，再用流程把原子 API 组合成该渠道对标准 API 的实现。
3. 字段结构、依赖初始化、运行上下文与条件分支有统一、可配置、可校验的规则。
4. 保持 Payment 等业务 Owner 对账号、MID、路由、交易与资金状态及恢复的所有权。

使用者是熟悉渠道文档的接入人员、Platform/Payment 开发者和后续维护者。

## 2. 能力范围

| 能力 | 说明 |
|---|---|
| 标准 API | 定义业务请求与响应字段；独立模块不等于新增部署服务 |
| 接入方 | 渠道或其他外部接入方的基本信息 |
| 渠道原子 API | 单次外部协议操作：请求组装、脚本处理、传输、响应解析与结果映射 |
| 公共脚本 | 接入方内可复用的脚本、参数与执行阶段；复杂签名、加密由代码注册函数提供 |
| 渠道标准 API 实现 | 渠道实现某标准 API 的关系，含初始化、节点、条件连线、节点取值和最终返回映射 |
| 共用字段 | 标准 API 与原子 API 的请求、响应字段定义 |

调用关系：Payment 等业务系统经 Platform 内部 HTTP 调用标准 API；Platform 加载该渠道实现，先初始化依赖，再按流程执行原子 API 节点，最后返回 `executeStatus` 与业务 `outputs`。管理入口经过 Operations BFF。

## 3. 已确认规则

- 原子 API 只执行本 API 的一次目标操作。标准 API 可以按显式配置调用多个原子节点；这不是底层 HTTP 客户端自动重试。
- 同一个标准 API 可以由多个渠道分别实现；`(integration_id, standard_api_id)` 唯一表示一份当前渠道实现。
- 流程可以按渠道合同有条件地查询原操作；查询节点执行成功不等于支付成功，分支可同时判断 `executeStatus` 与业务 `outputs`。不能把所有 `ERROR` 或超时统一当作支付失败。
- **冲正、撤销、退款等资金反向操作不在流程中编排**，也不作为超时或 `ERROR` 的自动补救；确有需要必须另行评审决定。
- 依赖初始化在流程节点之前执行；初始化结果仅属于本次调用，不覆盖调用方冻结的账号或交易身份。
- 不保存流程实例、节点记录、运行上下文或请求/响应报文，不登记补偿任务，不自动重试，不跨调用或重启续跑。
- 不引入发布版本：修改对新调用生效，已开始调用使用一次加载的完整配置。
- Platform 不选择 MID，不判断支付资格，不读取 Payment 数据库，不接管调用结束后的业务恢复。
- Vault 仅为 Platform 新增渠道账号配置的执行用 Reveal（[材料交接](./material-handoff.md)）；Card 原始材料权限不变。

## 4. 本期不包含

- Java、前端、BFF 与执行引擎研发；已实现 Payment 账号/MID 查询的迁移。
- 正式 HTTP 路径、DTO、权限注册、系统函数完整签名与跨系统凭据传输方案。
- `definition_json`、`flow_definition_json` 的完整可执行 Schema 和真实渠道适配。
- 正式 PDManer/Schema、DDL/DML 执行、数据迁移及 Vault 策略调整。
- 流程状态落库、执行历史、自动重试、后台恢复、发布版本或新增部署服务。
- 新页面绘制；原 [Pencil 设计](../../../operations-console-web/docs/design.pen)保留，与现行结构的差异见[技术说明](./technique.md#4-operations-页面差异)。

## 5. 验收标准（设计评审）

| 编号 | 验收内容 | 入口 |
|---|---|---|
| EAPI-01 | 标准 API、原子 API、渠道实现三层职责与调用次数边界清楚 | [技术说明](./technique.md) |
| EAPI-02 | 六张配置表职责、关联与唯一性明确，无公共模型/响应合同表的并行现行目标 | [数据设计](./data-design.md) |
| EAPI-03 | 共用字段表的方向、位置、完整路径、数组规则及唯一性范围明确 | [数据设计第 3 节](./data-design.md#3-共用字段表-t_api_field) |
| EAPI-04 | 初始化先于节点执行，`input`/`resources`/`steps` 三区域与失败边界明确 | [流程设计第 2 节](./flow-design.md#2-依赖初始化) |
| EAPI-05 | 条件连线按优先级单分支匹配，兜底与表达式异常行为明确 | [流程设计第 4 节](./flow-design.md#4-流程与条件连线) |
| EAPI-06 | 流程不编排资金反向操作；无流程实例/节点运行表及续跑承诺 | [流程设计第 6 节](./flow-design.md#6-结果持久化与危险操作边界) |
| EAPI-07 | Payment 账号/MID/路由/业务状态 Owner 不变 | [技术说明第 2 节](./technique.md#2-系统边界) |
| EAPI-08 | 待决项明确列出，不编造完成状态 | [技术说明第 6 节](./technique.md#6-待决事项) |
