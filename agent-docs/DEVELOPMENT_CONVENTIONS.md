# 开发约定与编码习惯

最后更新：2026-08-29

## 1. 使用范围与优先级

本文记录 Code-MoR 已确认、可跨模块复用的编码习惯。编写或重构 Java、配置和测试代码前必须读取本文，并继续读取任务命中的专项文档。

- 当前用户请求始终优先。
- 本文负责命名、异常与枚举、事务、包与公开边界、代码可读性及通用测试习惯；同类旧示例与本文冲突时，以本文为准。
- 业务事实、系统职责、数据模型、技术版本和专项并发/安全契约仍由对应权威文档决定，本文不得改变它们。
- 发现两份权威文档仍存在实质冲突时，先指出具体路径和语义，不自行拼接一个第三种方案。

## 2. 项目标识与命名

- Java 基础包使用 `com.paytech...`。
- 公司级 Maven Artifact 和模块目录统一使用 `paytech-*`；`Code-MoR` 产品名以及既有 `mor-local`、`mor_*` 本地运行资源名称不因此改名。新增 Java 类名、包名、Bean 名、配置前缀、日志/指标、错误 Code、Topic/Group 和业务标识不得带 `Mor` / `mor-` 技术前缀；协议兼容确实无法避免时必须写明原因。
- 类、方法和字段使用完整、专业的业务名称；避免 `getData`、`handle2`、`CommonUtil` 等含义模糊的命名。
- Spring 配置前缀直接从能力开始，例如 `compensation.*`、`xxl.job.*`，不增加无意义的 `paytech` 中间层。
- Topic 使用小写 `topic_{source_system}_{business_subject}`，Consumer Group/Queue 使用小写 `cg_{consumer_system}_{consumer_subject}`；消息统一使用 UTF-8 JSON。

## 3. 包结构与依赖方向

- Foundation JAR 中的 `api` 表示允许宿主直接依赖的稳定入口、模型或扩展点，不代表 HTTP API；内部实现不得因为 `public` 修饰符就放入 `api`。
- Foundation 组件（当前主要是 `compensation-event`）使用 `model`、`service`、`persistence` 分层，`persistence` 下再分 `repository` 和 `mapper`；业务服务继续遵守 `JAVA_PROJECT_STRUCTURE.md` 的 `domain` / `domainservice` / `core/dal` 结构，不套用 Foundation 包布局。
- 数据访问抽象统一命名为 `*Repository`，使用 `interface + *RepositoryImpl`；不要新增 `*Store` 表达同一职责。它在 Foundation 中位于 `persistence/repository`，在业务服务中位于专项工程规范指定的位置。
- Foundation 的 `core` 只放不直接参与业务分层、但属于组件核心机制的内部能力，例如 `error`、`observability`、`serialization`、`transaction` 和 `autoconfigure`；业务服务的 `core/dal` 按 `JAVA_PROJECT_STRUCTURE.md` 使用，两者都不能成为杂物目录。
- 外部机制触发内部服务工作时，入站适配器统一放在与 `service` 平级的 `ext` 包下：XXL-Job 等调度入口放 `ext/job`，既有或迁移期 Dubbo Provider 放 `ext/dubbo`，REST Controller 放 `ext/rest`。其他协议只有存在真实入口时才新增 `ext/<protocol>`，不得创建空包。
- `ext` 只负责协议解析、入口校验、上下文转换和委托 `service`，不承载业务规则、事务编排或持久化逻辑；出站 HTTP、Provider 和第三方 SDK 适配器放独立 `integration` 边界。`ext/dubbo` 只定义兼容代码的位置，不改变当前禁止新增 Dubbo/OpenFeign 的技术边界。
- `ext/rest` 中的 Controller/API 只做协议适配，`service` 中的 Processor 编排用例，Domain Service 承载规则，Repository 屏蔽持久化，Mapper 表达 SQL；不得跨层走捷径。
- 优先构造器注入和 `final` 字段。相同逻辑组内的注入字段按依赖类型名称字符数由短到长排列，长度相同再按字典序；构造器参数保持相同顺序。

```java
private final Clock clock;
private final EventRepository eventRepository;
private final RecoveryAuditRecorder recoveryAuditRecorder;
```

## 4. 异常与枚举

- 新代码只按失败语义区分两类基础异常：`FailException` 表示结果明确、不可重试；`ErrorException` 表示未知或系统错误，可进入具备幂等、次数上限和退避的重试路径。
- 默认通过不同的 `CodeMessageEnum` 向上述两类异常传递错误语义，不为每个错误新建异常子类；只有调用方确实需要独立捕获或处理行为时才增加专用异常。
- `BusinessException` 和 `BizCheckFailException` 只用于兼容已有代码；`JAVA_PROJECT_STRUCTURE.md` 中仍以 `BizCheckFailException` 展示的旧示例不适用于新增实现。
- 所有项目枚举都实现 `CodeEnum`；带默认消息的错误枚举实现继承 `CodeEnum` 的 `CodeMessageEnum`。
- 数据库、HTTP、MQ、日志和业务比较使用 `getCode()`，禁止依赖 `name()`、`ordinal()` 或枚举声明顺序。Code 发布后不可修改，Java 枚举常量名可以安全重构。
- 错误 Code 使用一眼可读、稳定的英文大写语义，例如 `COMPENSATION_EVENT_CONFLICT`，不使用 `PAYTECH-COMMON-400001` 一类公司前缀加数字流水号。
- 面向开发者和运营的异常消息使用中文，并包含必要的实体标识、当前状态和操作上下文；不得包含凭据、敏感明文或完整 Payload。

## 5. 事务与代码可读性

- 业务和 Foundation 事务统一使用 `TransactionTemplate` 显式表达边界、传播行为和返回值，不使用 `@Transactional`。
- 一个方法只承担一个明确职责，原则上不超过 30 行；优先卫语句和早期返回，嵌套不超过三层。
- 注释和 JavaDoc 默认使用中文，协议固定词、标准名称和无法准确翻译的专有名词可以保留英文。注释解释约束、原因和边界，不复述代码字面行为。
- 不运行仓库级批量格式化，也不为了格式重写无关代码；保持改动聚焦，最终格式由项目既定工具或用户 IDE 处理。
- 不保留空实现、占位测试或 `TODO by Codex`；发现该标记时直接完成对应实现，确实受业务决策阻塞时说明具体缺口。

## 6. Compensation Event 的当前精简习惯

涉及补偿时还必须读取 `COMPENSATION_EVENT_GUIDE.md` 和 `paytech-foundation/README.md`。指南中的业务事务、`afterCommit`、Claim/Lease、至少一次、线程池、错误分类、人工恢复、RabbitMQ 与 XXL-Job 职责继续有效；其中所有依赖 `execution_type`、`source_type`、`source_id`、`source_version`、`schema_version`、`payload_hash`、Canonical Envelope、`LocalCompensationEventHandler`、`RabbitMqEventRoute` 或 `mor.compensation.*` 的表/API/配置/测试描述，已被当前精简实现和下列规则替代，不得恢复：

- 一个 `CompensationEventHandler` 只完成一件事，不再使用 `CompensationExecutionType` 区分执行方式。
- Handler 运行时以 `eventKey` 和 `subEventType` 查询宿主业务数据库；`payloadJson` 可为空，只保存确实没有其他持久化来源的最小补充数据，不能承载完整业务上下文。
- 历史 `sourceId` / `sourceType` 命名分别收敛为 `eventKey` / `subEventType`；身份字段和接口参数顺序固定为 `eventKey, eventType, subEventType`。
- 不再保留 `sourceVersion`、`schemaVersion`、Canonical Envelope 或 Payload Hash。
- Foundation 只要求宿主创建 `t_compensation_event`。人工恢复审计的存储/API/权限由宿主按需实现，不因为引入 JAR 自动要求额外业务表。
- `t_compensation_event` 不定义 `CHECK CONSTRAINT`；状态、次数、Lease、锁版本和 Payload 合法性由入口校验、条件更新与开发共识共同保证。
- 补偿登记、Claim 和结果回写继续使用显式 `TransactionTemplate`。`paytech-xxl-job-starter` 只提供与业务能力无关的通用 Executor 配置，不得依赖或引用补偿包；`paytech-compensation-event` 主动依赖该 Starter、自己持有补偿扫描 Handler，并把 XXL-Job 限定为兜底唤醒和分片，不能替代数据库 Claim/Lease。

## 7. 测试代码习惯

- 测试基础设施、`auto/manual` 边界、Testcontainers 和 Simulator 规则以 `UNIT_TEST_GUIDE.md` 为准；自动测试不得连接 Nacos、配置中心或真实业务环境。
- 同一模块、同一测试切片共享一个最小 `TestApplication`，不要让每个测试类重复声明启动应用；不同切片也不要被迫继承一个启动全部能力的巨型基类。
- 数据库准备、清理和只读断言集中到注解式 `TestMapper` 或测试 Repository，测试方法和 Mocker 中不要散落大量 `JdbcTemplate` SQL。
- Builder 只构建合法数据，Mocker 只准备状态，Checker/Probe 只观察和断言；不要使用 Mockito 行为 Stub 替换真实生产链路。
- 测试方法使用清晰的场景名称，例如 `test_invalidParameter`、`test_normalFlow`、`test_retryableError`；异步断言使用有上限的条件等待，不使用固定 `Thread.sleep`。
- 多个任务或 Maven 进程并行执行时必须使用独立数据库/schema、Redis 前缀和 MQ 命名空间，禁止共享测试库后互相执行 `DROP` 或全表清理。

## 8. Maven、协作与工具

- `paytech-dependencies` 统一管理依赖版本，`paytech-parent` 统一管理 Java、插件和测试规则；子模块只声明实际使用的依赖，不重复写版本。
- 公共代码拆为边界清晰的小型 Foundation JAR，不创建聚合式 `common.jar`，也不把业务 DTO、Repository 或第三方模型塞入公共包。
- 多任务并行时先检查工作区和路径所有权；共享文件必须有 single-writer，基于最新版逐段增量修改，禁止整文件覆盖或回滚其他任务改动。
- 除非用户在当前请求中明确要求，否则不要使用 Superpowers 系列 skill。选择更直接、足够完成任务的工具和验证路径。
