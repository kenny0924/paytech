# 业务能力 Maven 模块协作决策

- Decision status: Confirmed
- Implementation status: In progress
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Supersedes: `agent-docs/plans/2026-09-04-finance-modular-code-design.md` 第 4 节中“三个业务模块不相互依赖”的绝对限制；其他现行文档中把“同库”解释为可跨模块访问持久化对象，或把任意跨模块调用都强制提升到 `web` 的表述

## 1. 背景与适用范围

复杂核心服务可以把稳定业务能力拆成同一进程内的多个 Maven JAR。过去的“业务模块完全互不依赖”可以避免耦合，却会迫使稳定能力也经由 `web` 往返；反过来，因为共用一个数据库就直接访问其他模块的 DataObject、Repository、Mapper 或表，又会破坏所有权边界。

本文只决定同一个可部署 Java 服务内部的业务能力 Maven JAR 如何协作。跨微服务同步调用仍使用提供方轻量 `*-api` HTTP 契约；Foundation JAR 的提取和依赖规则不因本文改变。

## 2. 已确认决定

### D1. 默认无直接依赖，稳定能力允许单向依赖

业务能力模块默认不建立彼此的 Maven 依赖。只有同时满足以下条件时，调用方模块才可以单向依赖 Owner 模块：

1. 被调用方提供的是已经存在、具有明确业务语义并会被复用的稳定能力，而不是表查询、通用 CRUD 或为当前流程临时拆出的步骤；
2. Owner 明确发布窄业务 Service 接口及其 Domain / Application Command / Result；
3. 调用不承担跨模块用例编排，依赖方向能够长期保持单向；
4. 新依赖不会形成直接或间接 Maven 环。

不能满足任一条件时，由 `web` Processor 组合模块能力，不为消除依赖而新增模糊的 `common` 模块。

### D2. 依赖接口，不依赖实现或持久化

所有业务 Service 和 Domain Service 继续使用 `interface + *Impl`，调用方只依赖 Owner 发布的接口。允许进入跨模块合同的类型只有 Owner 定义的 Domain / Application Command / Result。

DataObject 可以在其 Owner 业务能力 JAR 内被 Repository、Service、Domain Service 和必要内部应用代码使用；一旦跨 Maven 模块，就不得导入、接收、返回或依赖对方的 DataObject、`*Impl`、内部 Domain Service、Repository、Mapper、表、SQL、数据库 View、存储过程或通用 CRUD 接口。同一个物理数据库不放宽该边界，HTTP `*-api` DTO 也不得被当作内部领域模型使用。

### D3. 跨模块用例和事务由 Web Processor 编排

出现以下任一情况时，协作属于更高层用例，必须由 `web` Processor 编排：

- 两个模块互相需要对方能力，或新依赖会形成环；
- 一个命令需要协调多个模块并定义整体成功、失败、幂等或补偿语义；
- 一个本地写事务需要同时修改多个 Owner 模块的数据。

同一进程、同一数据库的强一致写用例使用一个由 `web` Processor 持有的本地 `TransactionTemplate`。模块方法加入调用方已有事务，不得自行 `REQUIRES_NEW`、提前提交或切换线程。不能为了未来可能拆服务而把当前可原子提交的本地事务改成 Compensation Event。

### D4. 依赖边界必须可验证

新增或调整业务模块时必须检查 Maven 依赖图无环，并用架构测试阻止跨模块的 DataObject、Repository、Mapper、实现包和 SQL 越权。系统专项模块文档负责记录模块清单、表唯一归属及获准的单向依赖；它不能另行改写公共协作规则。

## 3. 权威与投影

- [`JAVA_PROJECT_STRUCTURE.md`](../JAVA_PROJECT_STRUCTURE.md) 是 Java 模块、跨模块边界和事务编排的唯一现行权威。
- [`DEVELOPMENT_CONVENTIONS.md`](../DEVELOPMENT_CONVENTIONS.md) 只保留编码侧摘要。
- `.cursor/rules/java-business-module-boundaries.mdc` 是 POM、Java 和 Mapper XML 的自动注入薄投影。
- Finance 模块设计是首个系统映射，只记录 Accounting、Treasury、Tax、Web、API、Test 的具体归属与依赖选择。

文档落地不代表既有 Java 仓库已经完成依赖审计、架构测试或迁移；当前差距统一记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。
