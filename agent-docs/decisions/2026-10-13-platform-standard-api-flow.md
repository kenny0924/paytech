# Platform 标准 API 与渠道流程编排决定

- Decision status: Confirmed（标准 API 分层、渠道流程编排、资金反向操作排除）；完整 JSON、HTTP Wire、运行材料交接与物理模型继续细化。
- Implementation status: Not started
- Deployment status: Not applicable
- Decision date: 2026-10-13
- 用户决定：对内提供标准 API，渠道可以按标准 API 进行接口编排；冲正属于危险操作，不在流程中编排。
- 详细合同：[外部 API 配置与执行现行需求](../../requirements/20260914/platform-support-外部API配置与执行/requirement.md)、[六表数据设计](../../requirements/20260914/platform-support-外部API配置与执行/data-design.md)、[流程与运行上下文](../../requirements/20260914/platform-support-外部API配置与执行/flow-design.md)。
- 迁移记录：[渠道 API 配置资料迁移](../../requirements/20261013/platform-support-渠道API配置资料迁移/requirement.md)；实施与验证状态只见[当前台账](../CURRENT_IMPLEMENTATION.md#platform-standard-api-flow-20261013)。

## 1. 决定

Platform 对内提供标准 API（例如 `PAY`、`AUTHORIZE`），定义统一业务请求和响应。渠道原子 API 只负责一次外部协议操作；渠道实现某个标准 API 时，可以在一次同步调用中按显式配置的流程组合多个原子 API，并按条件连线分支。同一个标准 API 可以由多个渠道分别实现。

Payment 等业务系统通过 Platform 内部 HTTP 调用标准 API，管理入口仍经过 Operations BFF。Platform 不选择 MID，不接管支付资格、路由冻结、交易与资金状态，也不承担调用结束后的业务恢复。

流程中允许按渠道合同有条件地查询原操作；查询成功不等于支付成功。冲正、撤销、退款等资金反向操作属于危险操作，当前设计不在标准 API 流程中编排，也不作为超时或 `ERROR` 的自动补救。如某渠道确需此类编排，必须另行评审决定。

执行仍不保存流程实例、节点记录、运行上下文或报文，不自动重试、不跨调用或重启续跑；`executeStatus` 不等于资金状态。

## 2. 精确取代范围

- 取代 [2026-09-21 Integration 决定](./2026-09-21-platform-integration-http-execution.md)第 1 节“单次最多一个目标外部操作……无跨 API 业务编排”中关于一次调用只能发起一个目标操作、不得组合 API 的部分，以及“API、公共脚本、数据模型和响应合同”的配置对象列举。该决定的 Owner、内部 HTTP、无独立部署服务、Gateway 职责、Vault/Card 边界继续有效。
- 取代[Platform HTTP 执行修订](../../requirements/20260914/platform-support-外部API配置与执行/platform-http-execution-amendment.md)第 5 节“单次 execute 最多一个目标外部操作”和第 6 节五表草案；改为原子 API 单次操作、标准 API 同步流程两层，以及六表逻辑结构。
- 取代独立公共数据模型表、响应合同表及 `response_contract_id` 关联的现行目标；字段结构统一由共用字段表维护。
- [PROJECT_CONTEXT](../PROJECT_CONTEXT.md) 与 [Platform 数据库规范](../database/PLATFORM_SUPPORT_DATABASE.md)中的窄例外由“受控外部 API 定义及单次执行”扩展为“受控外部 API 定义、标准 API 及其渠道同步流程执行”。仍不扩展为任意配置中心、跨系统业务事务编排、重试或恢复服务。

## 3. 后续细化

原子 API `definition_json`、`flow_definition_json` 完整结构与静态校验、Payment → Platform 请求响应合同及调用授权、两段 HTTP 超时与流程总预算、账号材料交接、六表物理类型与索引，按现行需求的待决清单分别评审。
