# 请求幂等职责规范

用户要求将已讨论的幂等边界抽象为全项目开发规范：`Idempotency-Key` 在 Gateway 处理，不限于 Client Gateway API，防止其他系统继续接入旧公共命令幂等组件。

本次交付为文档沉淀：区分请求响应重放、业务防重和业务恢复；保留已确认的 72 小时与原响应语义；明确存量待迁移和禁止新增消费。完整规则只维护在[开发规范第 5.4 节](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)，取代范围见[职责决定](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)。

验收范围：公共规范、Agent 入口、自动规则和示例指向同一职责；旧文档不再被解释为新增组件接入授权。不实现 Gateway、不修改业务代码/实际 API、不删除组件或历史数据，不涉及数据库与 SQL。
