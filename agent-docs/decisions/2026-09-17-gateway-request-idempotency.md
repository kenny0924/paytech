# 全项目请求幂等与业务防重职责决定

- Decision status: Confirmed
- Decision date: 2026-09-17
- Scope: 全项目 Gateway、BFF、核心系统、内部 API、Foundation 及开发示例；不限于 Client Gateway API。
- Superseded sections: 取代旧规范中由业务服务处理 HTTP `Idempotency-Key`、按需新增 `paytech-command-idempotency` 消费者，以及 Gateway 只透传 Key、不处理请求幂等的要求。存量机制说明仅用于迁移和历史解释。

## 确认依据与现行权威

用户确认把此前讨论的幂等边界抽象为全项目规范：`Idempotency-Key` 在 Gateway 处理，防止其他系统继续接入公共命令幂等组件。此前确认的 72 小时保留、原响应重放（含已记录系统异常）及不恢复业务执行继续适用。

完整开发规则统一维护在[开发规范第 5.4 节](../DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)。Gateway 入口边界由[应用架构](../APPLICATION_ARCHITECTURE.md)投影；旧组件的禁止新增接入与存量边界由[技术栈第 6.12 节](../TECHNOLOGY_STACK.md#612-paytech-command-idempotency)投影，不在各系统复制独立幂等规范。

## 取代与保留边界

- HTTP 请求 Key、请求摘要及响应重放由 Gateway 统一承担，不下沉至 BFF、Controller、Processor、Service 或业务专用 Filter/Adapter。
- 所有业务系统禁止新增 `paytech-command-idempotency` 依赖、启用或调用；不复制同义命令表、Hash、Claim/Lease、恢复阶段和响应快照方案来规避限制。
- 业务唯一编号、原业务记录比较、状态机、唯一约束、CAS、必要事务、下游稳定操作身份和业务恢复仍由对应 Owner 负责。内部调用绕过 Gateway 时，使用明确的业务身份与业务合同，不补建服务本地 HTTP 请求重放层。
- 旧组件的尚未迁移调用、表、历史记录和内部 Header 是待迁移事项，不是新接入例外。逐项迁移时保留已发生业务和必要恢复能力，不因本次文档变更直接删除数据或修改运行协议。
- 此决定不改变 MQ 消费防重、补偿事件执行器的 Claim/Lease、资源锁或第三方 Channel 自有协议；这些机制不属于 Gateway 请求响应重放。

本次仅沉淀规范；不实现 Gateway 中间件，不调整实际 API、业务代码、数据库或部署配置。哪些接口启用、Key 作用域编码、72 小时起算点、执行中重复请求及响应未记录时的处理等实现细节另行设计；不得用已批准的职责边界宣称实现已生效。

本次材料见[需求记录](../../requirements/20260917/foundation-请求幂等职责规范/requirement.md)，实现、迁移及验证状态仅见[当前台账](../CURRENT_IMPLEMENTATION.md#gateway-request-idempotency-conventions-20260917)。
