# Platform Controller 注解式尽力审计决策

Decision status: Confirmed
Implementation status: Complete
Deployment status: Not deployed
Decision date: 2026-09-05

## 决定与理由

用户在 Platform Support 任务中明确确认：

1. 操作审计失败不应改变主业务结果，不与业务共享数据库事务。
2. 接受进程崩溃、队列满和审计写入故障导致部分审计记录永久丢失。
3. `@Audit` 放在 Platform 的 Controller **实现方法**，不放在共享 HTTP API 接口。
4. Controller 声明动作；业务现场提供安全事实；后台独立保存。账号锁定、权限变更、Session 撤销等业务状态及其并发保护不随审计异步化。
5. 后续重构将审计生命周期进一步从业务编排隐藏：Processor 不直接依赖审计 Service/Context，也不逐处登记事务；业务 Service 只提交类型化事实、可信身份、动态目标或幂等重放抑制。统一事务执行器在真实事务回调内登记完成通知。

因此选择提交结果确认后的有界内存分发和独立审计事务，不为这批允许丢失的记录引入 MQ、持久化补偿或自动重试。Controller 审计不自动覆盖未进入方法的请求，也不覆盖绕过 Controller 的任务调用。低侵入不等于零业务事实：before/after、细分拒绝原因和真实资源 ID 仍只能由已经取得这些事实的业务位置显式提供，切面不反射领域对象或回查数据库猜测。

具体组件、异常隔离、覆盖清单及验收契约见[已确认正式设计](../../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)，执行步骤见[实施计划](../../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)。用户已确认正式设计；设计及计划不能作为代码实现或验证已完成的证据。后续状态只在[当前实现台账](../CURRENT_IMPLEMENTATION.md)维护。

## Supersedes / Superseded sections

只取代下列文档中 **Platform 已有 Operations HTTP 操作审计必须与业务同事务、审计失败可阻断业务** 的目标语义：

- [Platform 物理决策](../../platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md)“22 表代码约束与注释同步”中的 Audit/Idempotency 同事务说明；业务幂等记录仍与业务共同提交。
- [Platform 资源锁重构记录](../plans/2026-09-04-platform-resource-lock-refactoring.md)中“授权拒绝因写审计需要可写事务”的理由；需要保护业务一致性的事务和锁不因此删除。
- [审查修复设计](../../docs/superpowers/specs/2026-09-05-platform-support-review-refactoring-design.md)中对此批既有审计事务边界保持不变的约束。
- [统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)中涉及既有 Operations Target/Route HTTP 状态命令的“配置与 Audit Event 同事务”约束；不改变配置写入本身的事务、CAS、幂等或全局校验，也不扩展至尚未实现的管理命令。

审计内部 Chain/Event/Attribute/Security Event 原子性、认证与授权规则、Hash 编码、历史记录不可变和敏感数据白名单继续有效。不取代 Vault 访问审计、其他系统 BCR/账务事实、尚未实现的 Platform 审计来源或可靠异步工作的规范。

## 不包含的授权

本决策不授权提交、推送、部署、修改运行数据库、迁移模型或新增外部资源；设计与计划阶段仅形成文档及其窄范围路由。
