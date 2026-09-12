# 用例编排、事务边界与测试隔离决定

- Decision status: Confirmed
- Decision date: 2026-09-12
- 依据：用户在 Commerce 退款重构中明确六阶段 Processor、测试实现不得进入业务代码、补偿 Handler 无须专用 Processor，并要求将“事务内不允许调用外部服务，只能操作数据库”抽象为开发规范。
- 作用域：将上述职责与边界推广为项目开发规则；不改变各系统业务合同、事务技术选型、Service 接口形态或测试基础设施。
- 取代范围：旧规则中要求所有 `ext` 入口只能转发、不得编排的表述，对补偿 Handler 不再适用；普通 HTTP 入站适配器仍保持协议适配职责。

## 已确认原则

1. 用例入口负责阶段顺序与事务边界，业务 Service 负责业务规则和本地事实；不把整个用例塞入一个 Service 方法，也不因拆方法就新增一层。
2. Processor 按基础字段验证、请求业务验证、上下文构建、本地事务业务处理、下游操作、响应构建组织；无职责的阶段不创建空实现。
3. 补偿 Handler 直接编排自身任务，业务规则委托 Domain Service，无须再创建专用 Processor。
4. 事务内只操作本服务数据库，禁止完整调用链中的外部查询和操作；必要纯内存处理可随数据库操作执行。
5. 测试替身和测试控制逻辑留在测试源码及配置中，不进入生产实现或运行制品；获准暂缓的外部集成显式报告未实现或不可用，不伪造成功。
6. 重构保留既有幂等、并发、额度、状态与恢复语义，不为形式完整或测试便利引入无业务消费者的抽象。
7. 用户随后明确“Handler 也应该要单独的单元测试，Handler 就和 HTTP 的入口一样”：业务 Handler 必须维护独立入口测试类，HTTP 链路的间接覆盖不能替代；该要求不改变真实本地链路、框架调度及测试隔离规则。

## 权威与适用方式

- 完整编码规则由 [DEVELOPMENT_CONVENTIONS.md 第 5、7 节](../DEVELOPMENT_CONVENTIONS.md#5-事务与代码可读性)拥有。
- 包、入口及依赖方向由 [JAVA_PROJECT_STRUCTURE.md 第 5 节](../JAVA_PROJECT_STRUCTURE.md#5-web-模块分层)拥有；测试链路、Simulator 和基础设施继续由 [UNIT_TEST_GUIDE.md](../UNIT_TEST_GUIDE.md)拥有。
- `.cursor/rules` 只投影规范。后续新增或修改相关代码按规则执行；本记录不表示既有代码已完成全量审计或迁移，实现状态仍只读 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md)。
