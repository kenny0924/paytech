# 补偿事件定义与工厂归属 Handler

- Decision status: Confirmed
- Decision date: 2026-09-12
- Implementation status: Complete（规范沉淀；不表示存量代码已全部迁移）
- Deployment status: Not applicable
- 依据：用户在 Commerce 退款重构中将事件常量及 `buildEvent()` 收拢到 Handler，确认复核提交后要求沉淀为开发规范。
- 作用域：Java 服务本地补偿任务的定义归属、登记请求构造、模块内依赖和测试；具体业务状态及跨系统接口继续由业务 Owner 管理。
- Superseded sections：`JAVA_PROJECT_STRUCTURE.md` 第 5.2 节的 Processor 调用边界增加“同一服务、同一编排模块内调用补偿 Handler 的纯静态事件工厂”这一窄例外；不允许依赖 Handler 实例或直接执行任务。

## 决定与理由

1. 任务类型和登记请求构造由对应 Handler 管理，Domain Models 保留业务命令与结果。定义靠近执行入口，调用方无需了解重复的补偿协议字段。
2. Handler 提供纯静态 `buildEvent(业务标识, 必要参数)`，调用方提供稳定业务标识和已冻结参数；工厂只构造不可变请求，不查库、不登记任务、不调度或调用下游。
3. 构建、登记、执行分属不同职责：工厂构建请求，用例在本地业务事务内登记，Foundation 在提交后唤醒和调度；不因工厂位于 Handler 类上而把执行入口带入事务。
4. 无业务子类型的任务由工厂使用 `"-"` 占位，Handler 不为占位值增加分流逻辑；真实需要子类型的任务继续由其业务合同定义。可从权威表恢复的上下文不重复放入 Payload。
5. 事件身份参与持久化和恢复。移动代码位置默认保留身份取值；确需修改时同步登记、扫描、恢复、测试和已有待执行任务的处理，不把常量重命名等同于持久化值可随意更换。

## 权威路由

- 编码与构造职责：[开发约定第 6 节](../DEVELOPMENT_CONVENTIONS.md#6-compensation-event-的当前精简习惯)。
- 包与模块内依赖：[Java 工程结构第 5.2、5.6 节](../JAVA_PROJECT_STRUCTURE.md#56-mq-与业务事件)。
- 身份、子类型、Payload 与提交后调度：[补偿任务规范](../COMPENSATION_EVENT_GUIDE.md#4-handler-与-mq-边界)。
- 测试与重构联动：[测试规范第 5.1 节](../UNIT_TEST_GUIDE.md#51-以可执行入口组织测试)。

本决定不要求新增通用工厂层、自动迁移存量任务或修改其他系统业务模型。既有实现状态继续以当前状态台账及组件证据为准。
