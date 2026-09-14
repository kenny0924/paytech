# Payment 内部调用信任修订

确认日期：2026-09-14。用户明确要求移除 Caller，并确定 Payment 的内部调用方完全可信。本次在既有 Execution 研发范围内执行；实现、验证和提交状态仅见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 调用边界

- Payment Execution 和 Instrument HTTP 只供可信内部服务调用；Payment 不再逐次识别服务身份、检查 capability 或调用 Caller 授权端口。
- 请求仍提供 `clientId`，Payment 按它核对 Intent、来源、Attempt、Action、Refund 和 Token 的业务归属；Token 的绑定范围、期限及当前状态校验继续执行。
- Commerce 冻结事实和法律接受由上游核验后传入；Payment 不反向调用 Commerce，详见同日后继[调用方向修订](./payment-commerce-direction-amendment.md)。Risk、当前资格、Routing、Card、Channel 的业务校验保留。
- 不增加替代 Caller、固定 ALLOW 的适配器、授权 Header 或权限配置。本次不修改外部 Client API、Gateway、BFF、Platform 或 Vault 的认证授权，也不新增公共路由。

## 幂等

- 同步命令使用 `client:<clientId> + operation_type + idempotency_key` 定位原受理。同 Client 的同操作同 key 重放，其他 Client 使用相同操作/key 时互相隔离。
- 服务端由现有 `CommandIdentity` 生成 Client 命名空间，请求不能自行指定 scope。本次移除 Caller 不改变操作或 Hash；其后创建命令新增国家并升级 V2 的边界见调用方向修订。
- 现有 `t_command_idempotency.caller_scope` 列作为技术命名空间继续使用，本次不改表、字段或索引；它不再表示一项 Caller 授权结果。
- 旧 `test:commerce:<clientId>` 是前序测试替身命名空间。已有旧命令的环境不能直接换前缀上线，否则会丢失原重放定位；需保留旧版本或另行审阅兼容迁移。本次不编造历史调用身份，不更新旧数据；本机实际记录数见验证证据。

## 替代范围与验证要求

本修订取代[旧接口合同](./api-contract.md)的逐次 Caller 认证/能力授权及调用身份生成 scope 的约定，以及[工具 Token 修订](./instrument-token-amendment.md)中对应的调用者授权表述。业务数据归属、敏感材料边界和其他状态/恢复约定保持。

验证覆盖：无 Caller 的真实内部查询、卡工具入口缺失资源检查、业务 Owner 不可用、Client 命名空间的重放/冲突/隔离、跨 Client 来源拒绝和命令回滚。测试通过真实 HTTP、生产 Processor/Service/Mapper 和仓库本地 MySQL/Redis；Token 相关完整回归仍要求新 Schema 已获授权并手工应用。
