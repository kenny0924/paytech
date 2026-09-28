# Processor 基础字段校验归公共父类

- Decision status: Confirmed
- Decision date: 2026-09-17
- 确认来源：用户要求将 `OperationsManagementSupport.validate` 的基础字段校验收敛到公共父类。

## 决定

- `AbstractProcessor.process` 保持拒绝已有事务 → `validateBasic` → `validate` → `processRequest` 的顺序；默认 `validateBasic` 统一检查请求非空，并执行内部 Request 的 Jakarta Bean Validation 默认分组及 `@Valid` 嵌套约束。
- 父类通过方法注入复用宿主 Spring 管理的 `Validator`，不要求业务子类重复声明或传递该依赖，不自行创建 ValidatorFactory。非 Spring 创建的实例须在调用前显式设置校验器；没有装配时不静默跳过。
- 基础参数失败使用公共 `InvalidParameterException`；`validate` 只保留用例特有、仅依赖输入的附加校验。授权、持久化事实、状态和版本判断不下沉 Foundation。
- HTTP Body 的 `@Valid` 继续负责协议入口，公共模板负责应用请求入口；业务 Processor 不再为触发同一字段校验而重写 `validate`。
- 保留已存在且有独立合同的 `validateBasic` 扩展，例如 API Credential 缺失或畸形统一返回 401；不借此修改认证错误语义。
- 本次清理 Operations Management 的 16 个重复重写与 Support 中的校验器依赖；其他业务校验不批量迁移。

通用规则由 [开发规范](../DEVELOPMENT_CONVENTIONS.md) 和 [工程规范](../JAVA_PROJECT_STRUCTURE.md) 维护；[公共示例](../examples/WRITE_API_EXAMPLE.md) 同步说明入口。实现、编译及未验证范围只记录在 [当前台账](../CURRENT_IMPLEMENTATION.md)。
