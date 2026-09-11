# 开发约定与编码习惯

最后更新：2026-09-10

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
- 公开 HTTP 接口及已发布内部 `*-api` 契约中，直接表示完整请求或响应的顶层 DTO 分别命名为 `*RequestBody`、`*ResponseBody`。GET 查询 DTO 也使用 `*RequestBody`，但仍按接口声明通过 Query/Path 等参数传递；名称不要求增加 HTTP Body 或 `@RequestBody`。
- 仅作为字段嵌套的业务模型、内部领域对象和应用层命令/结果不强制上述后缀，继续按各自业务语义命名。不要为了命名规则创建一层同义包装，或给所有成功响应添加通用外壳。
- 新增和调整 HTTP DTO 时统一遵循该命名规则。重命名已发布 Java 契约时同步调整提供方与调用方，单纯类型命名变化不改变 JSON 字段、HTTP 方法或参数位置。
- 同一业务能力的 HTTP 请求、响应及相关嵌套模型集中放在独立的 `*Models` 类型中，例如 `AuthenticationModels`、`NavigationModels`；使用 `public final` 容器、私有构造器和 `public record`。`*Api` 接口只声明 HTTP 方法契约，不在接口内定义 DTO。核心服务的传输模型仍发布在自己的轻量 `*-api` 模块，BFF 的本地页面模型由 BFF 自己维护和组装。
- HTTP Request record 默认使用 Jackson 正常属性绑定，并以 Jakarta Bean Validation 表达必填、长度、格式和集合约束；不得为了手工取字段而给每个 DTO 增加 `@JsonCreator(mode = DELEGATING)`、`Map<String, ?>` 工厂和字段名常量。只有正常绑定无法表达、且专项 Wire 明确要求区分的输入语义才增加自定义反序列化，并必须用入口契约测试证明缺失、显式 `null`、类型错误和未知字段行为。
- 已发布的 `*Api` 方法、顶层 Request/Response 及其非显然 record component 必须有中文 JavaDoc；外部 API 同时维护 OpenAPI operation、parameter、schema 和 property 描述。注释说明语义、单位、可空条件、默认和兼容约束，不只是把标识符翻译成中文，JavaDoc 与 OpenAPI 不得定义两套含义。
- 数据库映射对象直接使用业务实体名，由 `dataobject` 包表达其持久化职责；类型名不得追加 `DO` 后缀。DataObject 以所属业务能力 Maven JAR 为可见性边界：该 JAR 内的 Repository、Service、Domain Service 和必要内部应用代码可以使用自己的 DataObject，但 Controller/HTTP API 不得把它直接作为传输对象，其他 Maven 模块也不得导入、接收或返回它。
- DataObject 的每个字段都要有中文 JavaDoc，说明业务含义、单位、可空条件或关键约束；不得只把字段名翻译一遍。Record Component 同样遵守此规则。
- `*Mapper` 专用于 MyBatis 持久化接口和明确的数据库映射职责；Wire DTO、Application/Domain 模型、Owner Command/Result 或两个非持久化边界之间的转换组件统一命名为 `*Converter`。Jackson 的 `ObjectMapper` / `JsonMapper` 是第三方固定类型名，不在此规则内。不得用 `Mapper` 为普通转换类制造数据库层歧义。
- Spring 配置前缀直接从能力开始，例如 `compensation.*`、`xxl.job.*`，不增加无意义的 `paytech` 中间层。
- Topic 使用小写 `topic_{source_system}_{business_subject}`，Consumer Group/Queue 使用小写 `cg_{consumer_system}_{consumer_subject}`；消息统一使用 UTF-8 JSON。

## 3. 包结构与依赖方向

- 复杂核心服务可以按 `JAVA_PROJECT_STRUCTURE.md` 增加少量业务能力 Maven JAR，但模块不是微服务：仍只有一个 `web` 启动进程、一个数据库和一个事务管理器。初始通常控制在 3–5 个模块；没有当前完整业务能力、明确表归属和真实消费者时不拆模块。
- 每张业务表只能由一个业务模块拥有。其他模块不得访问其 Repository、Mapper、DataObject 或表，不得用跨模块 JOIN、复制 SQL、View、存储过程或 `JdbcTemplate` 绕过；需要数据时调用 Owner 模块明确发布的业务能力接口，或由 `web` Processor 编排。该跨模块接口使用 Owner 定义的 Domain / Application Command / Result，不使用 DataObject 或通用 CRUD 模型。所谓“只调用对方的原子 Repository”同样禁止。
- 业务能力模块默认不建立彼此的直接 Maven 依赖。只有稳定、可复用的 Owner 业务能力同时满足窄业务接口、Domain/Application 模型、非跨模块用例编排、长期单向和无环条件时，调用方才可以单向依赖 Owner；调用方只依赖 Owner 发布的接口，不得依赖 `*Impl` 或未发布的内部 Domain Service。完整判断规则见 `JAVA_PROJECT_STRUCTURE.md`。
- `web` 作为组合根依赖各业务模块，并编排不满足上述单向门禁的跨模块用例、循环风险和跨 Owner 写事务；业务模块不依赖 `web`。同库强一致写操作使用一个本地事务，不能为了未来拆服务改成 Compensation Event；补偿只处理提交后异步/外部副作用，或真正拆成独立服务后的最终一致性。
- Foundation JAR 中的 `api` 表示允许宿主直接依赖的稳定入口、模型或扩展点，不代表 HTTP API；内部实现不得因为 `public` 修饰符就放入 `api`。
- Foundation 组件（当前主要是 `compensation-event`）使用 `model`、`service`、`persistence` 分层，`persistence` 下再分 `repository` 和 `mapper`；业务服务继续遵守 `JAVA_PROJECT_STRUCTURE.md` 的 `domain` / `domainservice` / `core/dal` 结构，不套用 Foundation 包布局。
- 数据访问抽象统一命名为 `*Repository`，使用 `interface + *RepositoryImpl`；不要新增 `*Store` 表达同一职责。它在 Foundation 中位于 `persistence/repository`，在业务服务中位于专项工程规范指定的位置。
- Foundation 的 `core` 只放不直接参与业务分层、但属于组件核心机制的内部能力，例如 `error`、`observability`、`serialization`、`transaction` 和 `autoconfigure`；业务服务的 `core/dal` 按 `JAVA_PROJECT_STRUCTURE.md` 使用，两者都不能成为杂物目录。
- 外部机制触发内部服务工作时，入站适配器统一放在与 `service` 平级的 `ext` 包下：XXL-Job 等调度入口放 `ext/job`，既有或迁移期 Dubbo Provider 放 `ext/dubbo`，REST Controller 放 `ext/rest`。其他协议只有存在真实入口时才新增 `ext/<protocol>`，不得创建空包。
- `ext` 只负责协议解析、入口校验、上下文转换和委托 `service`（BFF 简单转发可直接委托本地 Client Port），不承载业务规则、事务编排或持久化逻辑；出站 HTTP、Provider 和第三方 SDK 适配器放独立 `integration` 边界。`ext/dubbo` 只定义兼容代码的位置，不改变当前禁止新增 Dubbo/OpenFeign 的技术边界。
- `ext/rest` 中的 Controller/API 只做协议适配；简单用例可以直接委托一个 Domain Service，BFF 简单转发可以直接委托一个本地 Client Port，复杂用例由 `service` 中的 Processor 编排。Domain Service 承载规则，Repository 屏蔽持久化，Mapper 表达 SQL；不得跨层走捷径。
- Service 和 Domain Service 的模块内方法可以返回本模块 DataObject 或本模块定义的 Domain/Application 对象；是否转换取决于是否需要保护领域不变量、聚合多表或隔离持久化变化，不为形式对称机械复制同义对象。作为跨 Maven 模块合同发布的业务能力接口必须使用 Owner 定义的 Domain / Application Command / Result，不能暴露 DataObject、Repository/Mapper 类型或 `*-api` Request/Response DTO。Controller、Facade 或其他入站适配器负责完成 HTTP 传输对象转换。
- 所有业务 Service 必须使用 `interface + *Impl`；所有 Domain Service 必须使用 `interface + *Impl`。接口按业务能力命名，实现使用同名接口加 `Impl`，只有实现类标注 `@Service`。该规则不要求创建没有业务职责的空 Service；先判断是否需要 Service，一旦创建就不得以模块内单实现为由省略接口。调用方只依赖接口，不依赖 `*Impl`；Processor、Controller 仍使用具体类，Repository 保持 `interface + *RepositoryImpl`。
- 优先构造器注入和 `final` 字段。构造器只把全部参数赋给字段时使用 Lombok `@AllArgsConstructor`；构造器包含参数校验、防御性复制、派生对象创建或资源配置时保留显式实现。相同逻辑组内的注入字段按依赖类型名称字符数由短到长排列，长度相同再按字典序；显式构造器参数保持相同顺序。

```java
private final Clock clock;
private final EventRepository eventRepository;
private final RecoveryAuditRecorder recoveryAuditRecorder;
```

### BFF 聚合层职责

- 所有 `*-bff` 都是面向前端的聚合层，负责页面所需的多接口、跨服务授权查询组合、字段裁剪转换和展示模型组装；不得为了页面便利把这些职责全部下沉到核心服务。
- 聚合流程放在 BFF 的 `service` / Processor 中，由本地 Client Port 获取各核心服务的授权结果；Controller 保持协议适配，`integration` 保持单个远端契约适配。有实际查询编排或响应组装职责时保留相应应用层，不能因“精简层级”而删除；简单转发仍不创建空代理层。
- 核心服务提供可复用的领域能力，拥有业务事实、领域规则、授权决策和业务事务。BFF 保持无状态，执行入口授权并保留各上游的资源授权约束，不自行替代核心服务的业务判断，也不编排跨系统写事务。
- 例如交易详情页的订单、支付和账务视图由 BFF 查询并组装；退款命令交给归属核心服务执行，BFF 不自行协调支付和账务变更。

### 公共分页复用

- 分页查询复用 `paytech-common-core` 的 `com.paytech.foundation.web.paging.PagingRequestBody` 与 `PagingResponseBody<T>`，不在服务内新建同义分页请求、响应或范围校验工具。
- `PagingRequestBody.of(page, size)` 只为缺失参数补默认 page=1、size=10；显式 page 限 1 到 100000，size 仅允许 10、20、50、100、200。非法值通过 `InvalidParameterException` 返回公共 `INVALID_REQUEST`，不静默取整或截断；数据库偏移使用已校验请求的 `offset()`。
- 分页响应保持 `{items, page, size, total}`，total 使用 long；它只承载分页列表，不作为所有成功响应的通用外壳。BFF 元素转换复用 `map(...)`，保留页码、条数和总数，不从当前页 items 数量推导总数。
- 核心服务在同一授权范围及相同筛选条件下执行数据库 `LIMIT/OFFSET` 和 `COUNT`，分页排序必须稳定；不通过查询全部数据后内存截取实现常规列表分页。BFF 不持有数据库分页事实，也不绕过上游授权。
- 前端列表复用统一分页状态与加载逻辑；改变条数时回到第一页。数组改为分页对象属于响应契约变更，提供方 API、BFF 与前端同步发布，并同步维护 Gateway Route 的 page/size 查询参数白名单；目录增量仍由操作员手工审阅和执行。

### MyBatis SQL 存放规则

- 生产业务 SQL 统一写入 `src/main/resources` 下、由 `mybatis.mapper-locations` 加载的 `*Mapper.xml`。Java Mapper 只保留方法签名和必要的 `@Param` 等绑定注解，不使用 `@Select`、`@Insert`、`@Update`、`@Delete` 或对应 `*Provider` 注解承载 SQL，也不把 SQL 拼接转移到 Repository/Service。
- SQL 片段、动态条件、批量 `foreach`、结果映射及语句级缓存/主键配置在 XML 中维护。迁移时保持参数类型、TypeHandler、查询范围、排序、分页、行锁和条件更新语义；加锁查询已有的 `flushCache` / `useCache` 设置不得遗漏。
- 只有测试源码（`src/test/java`）中的 `TestMapper` 等数据准备、清理和断言工具允许使用注解 SQL；测试仍须调用真实生产 Mapper，不得复制业务 SQL 来代替生产链路验证。手工 Schema DDL 和初始化 DML 继续按数据库规范保存在独立 SQL 文件中，不放入 Mapper 或应用启动逻辑。

## 4. 异常与枚举

- 默认复用少数公共异常：业务检查使用 `BizCheckFailException(message)`（400），参数错误使用 `InvalidParameterException(message)`（400），版本或并发命令冲突使用 `ConcurrentRequestException(message)`（409），系统异常使用 `ErrorException`（500），依赖不可用使用 `ServiceUnavailableException`（503）。不为每条业务拒绝创建异常类或错误码。
- 普通业务规则只需公共 `BUSINESS_CHECK_FAILED` 加具体中文消息，例如 `throw new BizCheckFailException("不能停用当前登录账号");`。只有前端或其他调用方确实有区别处理的契约时才定义专有 `CodeMessageEnum`，并写明消费者及处理行为；不同提示文字不构成新增错误码的理由。
- `FailException` 继续作为结果明确的失败基类；`BizCheckFailException` 是新增普通业务检查的首选，`BusinessException` 仅保留兼容。`ErrorException` 表示系统或结果未知的失败，是否重试必须由具备幂等、次数上限和退避的调用契约决定；异常类型不授权自动重试。
- 数据库记录、外部依赖结果或内部恢复状态违反系统不变量时使用 `ErrorException` 并提供安全的诊断上下文，不用 `IllegalStateException` 充当服务级系统错误。`IllegalStateException` 只保留给不越过业务/协议边界的纯 Java 对象编程错误；一旦该异常代表可在运行中遇到的数据或系统状态，就必须转换为项目异常体系并保留 Cause。
- 所有业务枚举都实现 `CodeEnum`；只有带可安全外显默认消息的错误枚举实现继承 `CodeEnum` 的 `CodeMessageEnum`。对外发布、调用方需要理解的稳定 Code 枚举定义放在提供方 `*-api` 的 `api/enums`；仅服务内部使用的业务或持久化枚举放在 `*-web` 的 `domain/enums`。提供方 Web 直接复用自己的 API 枚举，不在领域包复制同一组 Code。
- 每个枚举常量单独声明，相邻枚举项之间保留一行空行，不将多项挤在同一行；若下一项带 JavaDoc 或注解，空行放在上一项与下一项的 JavaDoc/注解之前。API、内部领域及测试枚举均遵循此排版，不改变 Code 值或声明顺序。
- 数据库、HTTP、MQ、日志和业务比较使用 `getCode()`，禁止依赖 `name()`、`ordinal()` 或枚举声明顺序。Code 发布后不可修改，Java 枚举常量名可以安全重构。
- DataObject 的闭合枚举列直接使用实现 `CodeEnum` 的 Java 枚举：契约已发布的 Code 复用本服务 `*-api` 枚举，内部 Code 使用 `domain/enums` 枚举。通过 `paytech-mybatis-support` 的 `CodeEnumTypeHandler` 为每个枚举类型显式注册；不启用全局扫描或默认枚举替换。未知数据库 Code 必须使当前读取 Fail-Closed，不能映射为 `null`。MySQL `DATETIME(6)` 到 `Instant` 统一使用同模块的 UTC `InstantTypeHandler`，服务不得复制同义 Handler。
- 数据库中由配置或主数据动态维护的目录型 Code 属于开放集合，不得建成 Java 枚举或注册封闭枚举 TypeHandler；使用 `String` 或经过校验的开放值对象承载，使数据库新增合法目录项不会令旧代码反序列化失败。只有由代码控制、取值真正封闭的状态、方向和类型才使用枚举。业务必须依赖的稳定目录项可集中定义在职责明确的 `*Codes` 常量类中，禁止散落魔法字符串，也禁止用不完整枚举冒充完整目录或用长串条件穷举筛选。
- 对外发布的 `*-api` DTO 和 MQ 契约字段仍以 `String` 承载枚举 Code，不使用 Java enum 作为序列化字段类型；`api/enums` 只发布稳定 Code 定义，不改变传输兼容策略。提供方在边界内从 `CodeEnum#getCode()` 转换，消费方显式解析和校验；未知 Code 必须进入可观察、Fail-Closed 的兼容性处理，不能依赖反序列化异常、`null` 或兜底枚举静默放行。
- 错误 Code 使用一眼可读、稳定的英文大写语义，例如 `COMPENSATION_EVENT_CONFLICT`，不使用 `PAYTECH-COMMON-400001` 一类公司前缀加数字流水号。
- 业务、参数与并发异常的 `Exception#getMessage()` 是可安全外显的人类提示，说明必要业务上下文，不包含凭据、敏感明文、SQL 或完整 Payload；MVC 边界把它投影为 Problem `detail`。系统异常只对外返回公共安全说明，不返回内部 Cause/message。BFF 与前端须保留约定公共业务语义，不统一替换成模糊的“操作失败”。
- Spring MVC 服务引入 `paytech-mvc-web-starter` 后统一使用其 `GlobalExceptionHandler`；服务不得重复创建本地 `@RestControllerAdvice`、私有 Problem/Error Body 或同义状态映射。公共错误类别归 Foundation；确有消费契约的业务错误码才由服务发布，Foundation 不依赖业务系统。
- `ApiErrorStatusResolver` 仅用于 401/403/415 等明确外部契约的状态扩展；公共并发冲突 409、上游无效响应 502 与依赖不可用 503 由共享处理器映射。`FailException` 扩展只接受 4xx，`ErrorException` 只接受 5xx，其他结果回退默认。不得借此创建逐业务提示的错误目录，也不得把系统失败改成业务拒绝。
- 调用内部共享错误契约的同步 `@HttpExchange` API 时，复用 `paytech-http-client-support` 的 `HttpServiceClientFactory` 统一转换网络、HTTP 状态、RFC 9457-based Code-MoR Problem 和解码异常，并保留原始 Cause。接口代理由配置层注册单例 Bean，业务适配器直接注入调用，不重复编写 `execute/invoke` 或逐请求 catch 包装。特殊错误码只在工厂配置中显式注册；只有真正不同的业务转换、恢复或审计职责才在业务层捕获。该公共处理不安装全局 AOP、不自动重试，也不套用到第三方不同的错误协议。旧四字段错误体只允许按[全系统迁移设计](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)在兼容 Reader 内临时读取。
- HTTP 状态是调用结果的第一判断依据：`2xx` 返回当前接口的正常 typed DTO；除 `HEAD` 外，`4xx/5xx` 返回 Foundation 统一的 RFC 9457-based Code-MoR Problem，必填 `type`、`title`、`detail`、`code`、`requestId`、`timestamp`，请求 Body 字段错误可附 `errors[{field,message}]`。Body 不复制 `status`；响应 `X-Request-Id` 必须与 Body 一致。`HEAD` 错误只返回状态和必要 Header，不生成 Body。当前 `type=about:blank` 只承担通用问题标识，业务细分使用应用扩展 `code`；不得为了“统一外壳”包装全部成功响应。
- 未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 记录并映射为 `500`。不得用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、换一个同义异常或把所有失败改成 `503`；只有当前层能够恢复、必须释放资源/回写异步状态，或确实承担不同的外部协议错误契约时才捕获，并保留原始 Cause。
- 同一 Starter 自动记录每个 MVC 请求的一条终态 `INFO` 日志；服务不得再增加重复 Body 日志 Filter。普通端点保留完整 Request/Response Body 与耗时，不脱敏、不截断、不记录 Header/Cookie。2026-09-04 Operations 首版增加共享 `web.logging.metadata-only-path-prefixes`（默认空）：敏感前缀在缓存前分流，仅保留安全元数据，不读取/缓存/记录 Body 或 Query；未知编码路径保守处理。新增流式、SSE、文件或二进制接口前仍须重新评估全量缓存与日志边界。
- 上述完整 Body 行为只适用于没有更窄专项安全契约禁止日志的接口。专项文档优先：例如认证和 Vault 禁止 Token、敏感明文和完整 Payload 进入普通日志，必须完整配置并验证上述共享 metadata-only 边界；不得通过服务本地 Filter 绕过公共边界。

## 5. 事务与代码可读性

- 所有新增代码、抽象、接口、配置、模块、表和中间层都必须先通过必要性门禁：能够指出当前真实用例、删除该实现会产生的具体错误，以及现有机制为什么不能承担。无法证明这三点时不新增；“以后可能需要”、形式对称、所谓最佳实践或追求层次完整都不构成理由。
- 优先删除不影响当前正确性、契约和可观测性的代码。评审不能只问“这样写是否正确”，还必须问“完全不写是否仍然正确”；若答案为是，就不保留该实现。
- 业务和 Foundation 事务统一使用 `TransactionTemplate` 显式表达边界、传播行为和返回值，不使用 `@Transactional`。
- 普通展示、列表和详情读取默认不创建显式事务，即使内部执行多条 SQL，只要业务允许它们观察相邻的已提交状态。只有写操作、显式锁，或已证明多次读取混合不同提交时点会违反当前业务不变量时，才使用事务；选择只读事务时必须在代码或权威设计中说明需要保护的一致性快照及其失败后果。
- Processor 是按需的用例编排层。Controller 方法少于 10 条有效可执行语句，且只委托一个 Domain Service（BFF 简单转发可为一个本地 Client Port）、没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排时，可以不创建 Processor；空行、注释、花括号和格式换行不计入语句数，不能通过改写格式规避该边界。
- 任一上述条件不满足时都必须使用 Processor，不因物理代码行数少而例外。Controller 始终不得直接访问 Repository、Mapper 或第三方 SDK，也不得持有 `TransactionTemplate`；确实通过上述必要性门禁的简单一致性读可由 Domain Service 持有只读事务。
- 一个方法只承担一个明确职责，原则上不超过 30 行；优先卫语句和早期返回，嵌套不超过三层。
- 每个手写 Java 方法都必须有中文 JavaDoc；Lombok、record accessor 等生成方法不要求补写。简单方法至少用一行说明业务目的；涉及事务、幂等、恢复、并发、外部调用、异常转换或重要副作用的复杂方法使用多行 JavaDoc，解释必要的入参、返回值、边界和特殊结果。`@Override` 不因继承了类型签名而省略当前实现承担的业务语义。
- Processor 的内部辅助方法默认使用包级可见性，不机械声明 `private`；模板扩展点使用 `protected`，真正提供给包外调用方的入口才使用 `public`。包级可见不代表公共 API，调用方仍只能从已批准的 Controller、Service 或 Processor 入口进入。
- 复杂命令入口应能顺序读出“校验输入与业务前置条件 → 执行业务步骤 → 在明确事务中提交本地事实/检查点”。不依赖数据库当前状态的校验在昂贵外部调用和写事务前完成；依赖当前状态、Lease、唯一键或 CAS 的条件必须在相应事务内、紧邻写入重新确认。不得为了形式让 Controller、Processor、Service、Repository 逐层重复同一校验。
- Processor 的主处理方法只保留有业务含义的阶段调用，并用简短行内注释标出这些阶段；Hash、协议转换、异常映射、事务细节和恢复分支下沉到命名准确的辅助方法。阶段注释帮助读者理解用例顺序，不逐行复述代码。
- 多个入口真实复用、且无副作用的校验可以集中到职责明确的 `*Validator`；一次性卫语句留在所属方法，不把散落的 `require*` 机械搬进总揽式公共校验器。Validator 不打开事务、不访问 Repository/Mapper/外部 Client，也不能替代数据库约束、受影响行数和事务内重证。
- 同一状态对象在多个调用点需要判断“是否属于若干状态之一”时，可由该对象提供类型安全的 `statusIn(Status...)`；单一状态判断继续直接使用 `==`。不建立接受任意枚举/字符串的全局状态工具，也不以该辅助方法绕过合法状态迁移校验。
- 不为不存在的职责预建 `Abstract*`、空接口或通用上下文。业务 Service 和 Domain Service 的 `interface + *Impl` 是已确认的类型形态，不等于必须创建一个没有业务职责的 Service；通常至少两个真实用例共享稳定流程、字段和不变量后才提取抽象基类。经项目明确确认的跨用例入口标准模板是窄例外，但必须无具体业务依赖，只固定稳定执行骨架；不得借该例外创建单业务父类或提前放入幂等、事务、日志、异常响应和协议上下文。请求 ID、认证、网络信任和追踪等协议上下文优先留在 Web/Security 基础设施，不机械复制到每个 Processor 请求。
- 从获批用例所需的最小类、模块和配置开始，不预建一行转发类、空层、未来开关或带系统名前缀的同义基础设施。新增服务内通用技术能力前先检查 Foundation；能力天然跨服务，或至少两个真实消费者共享稳定契约时，才提取为职责单一的小型 Foundation JAR。业务 DTO、业务枚举、Repository、Mapper 和第三方模型仍留在所属服务。
- 参考其他系统实现时，先沿“入口或 Facade → 抽象模板 → 具体 Processor/Client → 实际依赖”读完整调用链，再提炼职责和执行顺序。参考代码只是设计证据；不复制与 Code-MoR 当前 HTTP、Foundation、异常、日志、事务或依赖边界冲突的历史实现。
- 注释和 JavaDoc 默认使用中文，协议固定词、标准名称和无法准确翻译的专有名词可以保留英文。注释解释约束、原因和边界，不复述代码字面行为。
- 不运行仓库级批量格式化，也不为了格式重写无关代码；保持改动聚焦，最终格式由项目既定工具或用户 IDE 处理。
- 不保留空实现、占位测试或 `TODO by Codex`；发现该标记时直接完成对应实现，确实受业务决策阻塞时说明具体缺口。

### 5.1 锁与并发控制

- 先说明需要保护的业务规则及冲突资源，再选择普通一致性读、原子条件更新/CAS、Redis 锁或数据库锁；不默认给每个业务加 `FOR UPDATE`，也不机械替换成 Redis。
- 默认锁实际操作的资源。给成员分配角色时按 `tenantId + memberId` 协调，编辑角色按 `roleId` 协调；全局账号状态、密码等账号级变化按 `accountId` 协调。不同资源不应仅因属于同一租户、同一操作者而被排他串行。
- 修改同一资源的不同业务入口必须共用资源锁命名，不能各自按接口/动作命名而互相绕过。租户成员锁不等同于全局账号锁；当前仅有一个固定租户的切片可使用经说明的唯一账号映射，扩展多租户前重新评估。
- 低并发、允许等待时可以适度扩大 Redis 锁范围以简化实现，但必须评估并发量、最长持锁时间、外部调用、竞争和失败影响；“性能要求低”不自动授权租户级/系统级大锁。跨资源规则只增加其确实需要的局部协调，例如最后一个管理员保护，不能据此扩大所有普通操作的锁。
- 普通查询不默认加排他锁或 Redis 锁；多次读取需要一致时使用明确的一致性查询/事务快照。授权检查与写入之间的竞态必须独立评估，不能直接删除原保护。
- 已判定需要跨实例排他协调的同步 MVC 用例，默认 Redis-first，并复用 Foundation `paytech-redis-starter` 的标准 `@ResourceLock(prefix, key)`；不得在业务服务新建同义注解、Aspect、执行器、锁表或重复 Redisson Client。只有标准注解无法表达且已有批准契约的协调原语才直接使用 Redisson。API、配置和失败合同见 [`TECHNOLOGY_STACK.md` 第 6.7 节](./TECHNOLOGY_STACK.md#67-paytech-redis-starter)、[资源锁决策](./decisions/2026-09-05-redis-first-resource-lock.md)和[完整设计](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)。
- 固定全局资源可省略 `@ResourceLock.key`；动态 Key 只写受限 `#param` 或一层 `#param.field`，不写完整 SpEL、方法调用、拼接、索引或多层导航。一层 accessor 必须是纯、快速且不访问外部状态的值读取。资源标识必须在进入用例前已经形成稳定的 `String`、整数或 `UUID`；Key 不包含邮箱、Token、Secret、敏感明文或自由文本。敏感登录标识不能直接进入 Key，普通 SHA-256 也不自动使低熵值安全；确需派生资源标识时必须另行批准带密钥的不可逆映射和轮换边界。
- 注解只放在非 `final` Spring Bean 的公开、非 `static`、非 `final` Processor 具体实现方法，并包住该方法内部完整的 `TransactionTemplate`；不得只标接口，也不得放在 Controller、Repository、Mapper、私有方法或依赖同类自调用触发。一个用例的全部资源必须在该入口一次声明，不通过另一个带锁 Bean、方法引用或嵌套代理拼装锁集合。所有资源 Key 在数据库事务开始前一次性解析并获取，提交或回滚后逆序释放；多锁先规范化、去重，再按完整逻辑 Key 的稳定 ASCII 字典序在一个总 Wait 预算内获取，不能依赖注解声明顺序。
- 使用 `@ResourceLock` 的目标必须是启动期可预测、非懒加载的 singleton Bean；override 必须在具体方法完整复写所有祖先锁声明，只有没有 override 的具体父类方法可以沿用声明。应用必须显式配置服务级有限 Wait/Lease，并在启动期校验协调开关、Foundation Redisson 来源、所有 scope 下唯一且验证后冻结的客户端对象身份、Namespace/Wait/Lease、FactoryBean/Bean 生命周期、实际代理与固定 Advisor/执行链身份和顺序、其他 Advisor 未复用或另建锁 Interceptor、JDK 接口暴露、方法可代理性、参数元数据、表达式、属性读取和声明类型；配置或静态绑定无效时失败启动，不能把错误延迟到首个请求。`Future`、`CompletionStage`、`Callable`/`Runnable`、JDK 函数式或迭代遍历接口、Java `Stream/BaseStream`、Reactive Streams、Java `Flow`、Kotlin Flow、Kotlin suspend 等延迟执行入口不支持持锁。竞争失败或中断不执行业务，中断状态必须恢复；Redis 不可用失败关闭；释放失败或失去 Owner 不得掩盖业务异常，也不得强制解锁或静默切换到另一互斥域。
- Redis Lease 到期、进程暂停、连接中断与释放失败都必须评估；锁不是永久所有权凭证，续租或提交前所有权检查也不等价于数据库原子性。Redis-first 不等于 Redis-only：Expected State/Version CAS、唯一索引、幂等、本地事务，以及关键关联、权限上限、资金与跨行不变量确实需要的数据库行/哨兵保护仍是最终防线；捕获异常只承担明确恢复、资源清理或协议转换职责，保留 Cause。
- 不把数据库事务和悲观锁混为一谈：去掉显式锁不表示取消事务，`UPDATE` 本身仍会获取行锁。幂等与旧版本冲突也不能只交给有 TTL 的 Redis Key。
- 验证至少覆盖相关的同资源竞争、不同资源并行、事务失败后释放、Lease 过期/中断、依赖不可用，以及跨资源不变量。使用真实仓库本地 MySQL/Redis，不用方法 Stub 证明分布式协调。

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
- 仓库隔离且仅监听 Loopback 的本地测试中间件可以使用提交到测试配置的固定本地账号和密码，使 Maven 与 IDE 中的 `auto` 测试无需手工加载 `.env.local` 即可直接运行。该便利仅适用于明确标记为本地测试、没有生产权限且允许随时重建的账号；生产、共享测试、第三方、Vault、Token、密钥及任何真实环境凭据仍必须通过安全配置注入，禁止写入代码或版本库。固定本地凭据必须与 `local-environment` 初始化值一致，并保留显式环境变量覆盖入口。
- 同一模块、同一测试切片共享一个最小 `TestApplication`，不要让每个测试类重复声明启动应用；不同切片也不要被迫继承一个启动全部能力的巨型基类。
- 数据库准备、清理和只读断言集中到注解式 `TestMapper` 或测试 Repository，测试方法和 Mocker 中不要散落大量 `JdbcTemplate` SQL。
- Builder 只构建合法数据，Mocker 只准备状态，Checker/Probe 只观察和断言；不要使用 Mockito 行为 Stub 替换真实生产链路。
- 测试从业务能力的实际入口触发：HTTP/API、模块公开 Service/Domain Service 接口、MQ Listener/Event Handler、Job Handler、Compensation Event Handler，或公开静态/纯 Java 合同。同一入口下的一组独立测试用例覆盖真实下游链路和更多相关业务场景；不为了覆盖率给每个 Service 方法或内部类机械配一组测试。纯算法、状态机和转换器在能明显缩小故障定位范围时再补充聚焦单元测试，完整入口与场景矩阵以 `UNIT_TEST_GUIDE.md` 为准。
- 测试方法使用清晰的场景名称，例如 `test_invalidParameter`、`test_normalFlow`、`test_retryableError`；异步断言使用有上限的条件等待，不使用固定 `Thread.sleep`。
- 多个任务或 Maven 进程并行执行时必须使用独立数据库/schema、Redis 前缀和 MQ 命名空间，禁止共享测试库后互相执行 `DROP` 或全表清理。

## 8. Maven、协作与工具

- `paytech-dependencies` 统一管理依赖版本，`paytech-parent` 统一管理 Java、插件和测试规则；子模块只声明实际使用的依赖，不重复写版本。
- 公共代码拆为边界清晰的小型 Foundation JAR，不创建聚合式 `common.jar`，也不把业务 DTO、Repository 或第三方模型塞入公共包。
- 多任务并行时先检查工作区和路径所有权；共享文件必须有 single-writer，基于最新版逐段增量修改，禁止整文件覆盖或回滚其他任务改动。
- 下列规则是根 `AGENTS.md` 中 `Change discipline` 的开发侧薄投影；发生漂移时修复投影，不把本文解释成第二个工作流 Owner。
- 默认不得调用任何 Superpowers skill。只有当前用户在当前请求中明确指定使用 Superpowers 或点名具体 Superpowers skill 时，才可在该请求授权范围内调用；不得从任务类型、历史回合、计划中的 `REQUIRED SUB-SKILL`、文档路径或其他 Agent 的选择中推断授权，也不得跨请求沿用。
- 未获明确授权时，仍须执行必要设计、测试先行、系统化诊断和完成前验证，但使用普通工作流与工具，不调用 Superpowers skill。`docs/superpowers/**` 只是文档路径，不构成授权。
- 非 Superpowers skill 继续按正常触发规则使用；任何 skill 都不授权自行创建分支、提交、推送或 PR。完整决定见 [`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)。
