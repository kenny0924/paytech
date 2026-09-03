# 开发约定与编码习惯

最后更新：2026-09-03

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
- 数据库映射对象直接使用业务实体名，由 `dataobject` 包表达其持久化职责；类型名不得追加 `DO` 后缀。跨层转换通过 Repository 完成，不能因为类型名精简而把数据库对象暴露到 API 或领域层。
- DataObject 的每个字段都要有中文 JavaDoc，说明业务含义、单位、可空条件或关键约束；不得只把字段名翻译一遍。Record Component 同样遵守此规则。
- Spring 配置前缀直接从能力开始，例如 `compensation.*`、`xxl.job.*`，不增加无意义的 `paytech` 中间层。
- Topic 使用小写 `topic_{source_system}_{business_subject}`，Consumer Group/Queue 使用小写 `cg_{consumer_system}_{consumer_subject}`；消息统一使用 UTF-8 JSON。

## 3. 包结构与依赖方向

- Foundation JAR 中的 `api` 表示允许宿主直接依赖的稳定入口、模型或扩展点，不代表 HTTP API；内部实现不得因为 `public` 修饰符就放入 `api`。
- Foundation 组件（当前主要是 `compensation-event`）使用 `model`、`service`、`persistence` 分层，`persistence` 下再分 `repository` 和 `mapper`；业务服务继续遵守 `JAVA_PROJECT_STRUCTURE.md` 的 `domain` / `domainservice` / `core/dal` 结构，不套用 Foundation 包布局。
- 数据访问抽象统一命名为 `*Repository`，使用 `interface + *RepositoryImpl`；不要新增 `*Store` 表达同一职责。它在 Foundation 中位于 `persistence/repository`，在业务服务中位于专项工程规范指定的位置。
- Foundation 的 `core` 只放不直接参与业务分层、但属于组件核心机制的内部能力，例如 `error`、`observability`、`serialization`、`transaction` 和 `autoconfigure`；业务服务的 `core/dal` 按 `JAVA_PROJECT_STRUCTURE.md` 使用，两者都不能成为杂物目录。
- 外部机制触发内部服务工作时，入站适配器统一放在与 `service` 平级的 `ext` 包下：XXL-Job 等调度入口放 `ext/job`，既有或迁移期 Dubbo Provider 放 `ext/dubbo`，REST Controller 放 `ext/rest`。其他协议只有存在真实入口时才新增 `ext/<protocol>`，不得创建空包。
- `ext` 只负责协议解析、入口校验、上下文转换和委托 `service`，不承载业务规则、事务编排或持久化逻辑；出站 HTTP、Provider 和第三方 SDK 适配器放独立 `integration` 边界。`ext/dubbo` 只定义兼容代码的位置，不改变当前禁止新增 Dubbo/OpenFeign 的技术边界。
- `ext/rest` 中的 Controller/API 只做协议适配；简单用例可以直接委托一个 Domain Service，复杂用例由 `service` 中的 Processor 编排。Domain Service 承载规则，Repository 屏蔽持久化，Mapper 表达 SQL；不得跨层走捷径。
- 优先构造器注入和 `final` 字段。构造器只把全部参数赋给字段时使用 Lombok `@AllArgsConstructor`；构造器包含参数校验、防御性复制、派生对象创建或资源配置时保留显式实现。相同逻辑组内的注入字段按依赖类型名称字符数由短到长排列，长度相同再按字典序；显式构造器参数保持相同顺序。

```java
private final Clock clock;
private final EventRepository eventRepository;
private final RecoveryAuditRecorder recoveryAuditRecorder;
```

## 4. 异常与枚举

- 新代码只按失败语义区分两类基础异常：`FailException` 表示结果明确、不可重试；`ErrorException` 表示未知或系统错误，可进入具备幂等、次数上限和退避的重试路径。
- 默认通过不同的 `CodeMessageEnum` 向上述两类异常传递错误语义，不为每个错误新建异常子类；只有调用方确实需要独立捕获或处理行为时才增加专用异常。`ServiceUnavailableException` 是唯一已批准的窄化公共子类：它仍属于 `ErrorException`，只在调用方契约明确要求以安全默认消息返回 HTTP `503` 时使用，不代表自动重试策略。
- `BusinessException` 和 `BizCheckFailException` 只用于兼容已有代码；新增实现不得继续使用。
- 所有业务枚举都实现 `CodeEnum`；只有带可安全外显默认消息的错误枚举实现继承 `CodeEnum` 的 `CodeMessageEnum`。对外发布、调用方需要理解的稳定 Code 枚举定义放在提供方 `*-api` 的 `api/enums`；仅服务内部使用的业务或持久化枚举放在 `*-web` 的 `domain/enums`。提供方 Web 直接复用自己的 API 枚举，不在领域包复制同一组 Code。
- 数据库、HTTP、MQ、日志和业务比较使用 `getCode()`，禁止依赖 `name()`、`ordinal()` 或枚举声明顺序。Code 发布后不可修改，Java 枚举常量名可以安全重构。
- DataObject 的闭合枚举列直接使用实现 `CodeEnum` 的 Java 枚举：契约已发布的 Code 复用本服务 `*-api` 枚举，内部 Code 使用 `domain/enums` 枚举。通过 `paytech-mybatis-support` 的 `CodeEnumTypeHandler` 为每个枚举类型显式注册；不启用全局扫描或默认枚举替换。未知数据库 Code 必须使当前读取 Fail-Closed，不能映射为 `null`。MySQL `DATETIME(6)` 到 `Instant` 统一使用同模块的 UTC `InstantTypeHandler`，服务不得复制同义 Handler。
- 对外发布的 `*-api` DTO 和 MQ 契约字段仍以 `String` 承载枚举 Code，不使用 Java enum 作为序列化字段类型；`api/enums` 只发布稳定 Code 定义，不改变传输兼容策略。提供方在边界内从 `CodeEnum#getCode()` 转换，消费方显式解析和校验；未知 Code 必须进入可观察、Fail-Closed 的兼容性处理，不能依赖反序列化异常、`null` 或兜底枚举静默放行。
- 错误 Code 使用一眼可读、稳定的英文大写语义，例如 `COMPENSATION_EVENT_CONFLICT`，不使用 `PAYTECH-COMMON-400001` 一类公司前缀加数字流水号。
- 面向开发者和运营的异常消息使用中文，并包含必要的实体标识、当前状态和操作上下文；不得包含凭据、敏感明文或完整 Payload。
- Spring MVC 服务引入 `paytech-mvc-web-starter` 后统一使用其 `GlobalExceptionHandler`；服务不得重复创建本地 `@RestControllerAdvice`、私有 Problem/Error Body 或同义状态映射。业务错误 Code 仍由服务自己的 `CodeMessageEnum` 提供，Foundation 不依赖业务系统。
- HTTP 状态是调用结果的第一判断依据：`2xx` 返回当前接口的正常 typed DTO，`4xx/5xx` 返回 Foundation 统一的直接错误体 `code`、`message`、`requestId`、`timestamp`；不得为了“统一外壳”包装全部成功响应。
- 未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 记录并映射为 `500`。不得用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、换一个同义异常或把所有失败改成 `503`；只有当前层能够恢复、必须释放资源/回写异步状态，或确实承担不同的外部协议错误契约时才捕获，并保留原始 Cause。
- 同一 Starter 自动记录每个 MVC 请求的一条完整 Request Body、Response Body 与耗时 `INFO` 日志；服务不得再增加重复 Body 日志 Filter。当前明确不脱敏、不截断、不记录 Header/Cookie，也不提供开关；新增流式、SSE、文件或二进制接口前必须重新评估全量缓存与日志边界。
- 上述完整 Body 行为只适用于没有更窄专项安全契约禁止日志的接口。专项文档优先：例如 Vault 禁止 Token、敏感明文和完整 Payload 进入普通日志，因此在专门日志边界获批前不得直接引入该 Starter；不得通过服务本地 Filter 绕过公共边界。

## 5. 事务与代码可读性

- 业务和 Foundation 事务统一使用 `TransactionTemplate` 显式表达边界、传播行为和返回值，不使用 `@Transactional`。
- Processor 是按需的用例编排层。Controller 方法少于 10 条有效可执行语句，且只委托一个 Domain Service、没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排时，可以不创建 Processor；空行、注释、花括号和格式换行不计入语句数，不能通过改写格式规避该边界。
- 任一上述条件不满足时都必须使用 Processor，不因物理代码行数少而例外。Controller 始终不得直接访问 Repository、Mapper 或第三方 SDK，也不得持有 `TransactionTemplate`；没有 Processor 的简单一致性读可由 Domain Service 持有只读事务。
- 一个方法只承担一个明确职责，原则上不超过 30 行；优先卫语句和早期返回，嵌套不超过三层。
- 不为单一实现预建 `Abstract*`、空接口或通用上下文。至少两个真实用例共享稳定流程、字段和不变量后才提取抽象；请求 ID、认证、网络信任和追踪等协议上下文优先留在 Web/Security 基础设施，不机械复制到每个 Processor 请求。
- 从获批用例所需的最小类、模块和配置开始，不预建一行转发类、空层、未来开关或带系统名前缀的同义基础设施。新增服务内通用技术能力前先检查 Foundation；能力天然跨服务，或至少两个真实消费者共享稳定契约时，才提取为职责单一的小型 Foundation JAR。业务 DTO、业务枚举、Repository、Mapper 和第三方模型仍留在所属服务。
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

- 测试基础设施、`auto/manual` 边界、本地中间件和 Simulator 规则以 `UNIT_TEST_GUIDE.md` 为准；测试复用仓库 `local-environment`，不得连接 Nacos、配置中心或任何真实业务环境。
- 同一模块、同一测试切片共享一个最小 `TestApplication`，不要让每个测试类重复声明启动应用；不同切片也不要被迫继承一个启动全部能力的巨型基类。
- 数据库准备、清理和只读断言集中到注解式 `TestMapper` 或测试 Repository，测试方法和 Mocker 中不要散落大量 `JdbcTemplate` SQL。
- Builder 只构建合法数据，Mocker 只准备状态，Checker/Probe 只观察和断言；不要使用 Mockito 行为 Stub 替换真实生产链路。
- 优先用少量业务级用例覆盖“公开入口 → 真实 Controller → 按需 Processor → Domain Service → Repository/Mapper → 本地数据库”及关键副作用，不为了覆盖率给每个内部方法或类机械配一组测试。纯算法、状态机和转换器在能明显缩小故障定位范围时再补充聚焦单元测试。
- 测试方法使用清晰的场景名称，例如 `test_invalidParameter`、`test_normalFlow`、`test_retryableError`；异步断言使用有上限的条件等待，不使用固定 `Thread.sleep`。
- 多个任务或 Maven 进程并行执行时必须使用独立数据库/schema、Redis 前缀和 MQ 命名空间，禁止共享测试库后互相执行 `DROP` 或全表清理。

## 8. Maven、协作与工具

- `paytech-dependencies` 统一管理依赖版本，`paytech-parent` 统一管理 Java、插件和测试规则；子模块只声明实际使用的依赖，不重复写版本。
- 公共代码拆为边界清晰的小型 Foundation JAR，不创建聚合式 `common.jar`，也不把业务 DTO、Repository 或第三方模型塞入公共包。
- 多任务并行时先检查工作区和路径所有权；共享文件必须有 single-writer，基于最新版逐段增量修改，禁止整文件覆盖或回滚其他任务改动。
- 除非用户在当前请求中明确要求，否则不要使用 Superpowers 系列 skill。选择更直接、足够完成任务的工具和验证路径。
