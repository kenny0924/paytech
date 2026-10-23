# 开发约定与编码习惯

最后更新：2026-10-22

> 通用写接口规则按 [2026-09-16 Payment 基准合并决定](decisions/2026-09-16-payment-based-coding-conventions.md)统一。开发接口同时读取[公共写接口开发示例](examples/WRITE_API_EXAMPLE.md)，按完整调用链实现并执行其中的交付自查；Payment 本地文档只保留业务补充。阶段性 Mock、跳过测试及业务状态不作为公共默认。

## 1. 使用范围与优先级

本文记录 Code-MoR 已确认、可跨模块复用的编码习惯。编写或重构 Java、配置和测试代码前必须读取本文，并继续读取任务命中的专项文档。

- 当前用户请求始终优先。
- 本文负责命名、异常与枚举、事务、包与公开边界、代码可读性及通用测试习惯；同类旧示例与本文冲突时，以本文为准。
- 第 10 节另外约束前端 `*-web` 的界面文案与语言接入；编写或修改前端页面文案时同样读取该节。
- 业务事实、系统职责、数据模型、技术版本和专项并发/安全契约仍由对应权威文档决定，本文不得改变它们。
- 发现两份权威文档仍存在实质冲突时，先指出具体路径和语义，不自行拼接一个第三种方案。

## 2. 项目标识与命名

- Java 基础包使用 `com.paytech...`。
- 公司级 Maven Artifact 和模块目录统一使用 `paytech-*`；`Code-MoR` 产品名以及既有 `mor-local`、`mor_*` 本地运行资源名称不因此改名。新增 Java 类名、包名、Bean 名、配置前缀、日志/指标、错误 Code、Topic/Group 和业务标识不得带 `Mor` / `mor-` 技术前缀；协议兼容确实无法避免时必须写明原因。
- 类、方法和字段使用完整、专业的业务名称；避免 `getData`、`handle2`、`CommonUtil` 等含义模糊的命名。
- 用户未明确指定接口版本时，已有接口沿用原版本/路径并在原合同上调整，不自行升级；新接口默认 `/v1`。仍需明确消费者兼容、发布和回退方式，存在无法兼容的具体约束时提交方案请用户决定。
- 类、接口、record 等类型名不带 `V1`、`V2`、`Version1` 等协议版本标记，例如 `RuleSetDetailResponseBody`。业务对象 `RuleSetVersion`、字段 `versionNo/baseRuleSetVersionId` 及持久化 Hash Schema 的版本语义继续保留。新增及本次涉及的类型按此规则命名，重命名同步提供方、调用方和测试；不扩大为无关仓库批量重构。确认依据见[接口版本与类型命名决策](./decisions/2026-09-11-api-version-and-type-naming.md)。
- 公开 HTTP 接口及已发布内部 `*-api` 契约中，直接表示完整请求或响应的顶层 DTO 分别命名为 `*RequestBody`、`*ResponseBody`。GET 查询 DTO 也使用 `*RequestBody`，但仍按接口声明通过 Query/Path 等参数传递；名称不要求增加 HTTP Body 或 `@RequestBody`。
- 仅作为字段嵌套的业务模型、内部领域对象和应用层命令/结果不强制上述后缀，继续按各自业务语义命名。不要为了命名规则创建一层同义包装，或给所有成功响应添加通用外壳。
- 新增和调整 HTTP DTO 时统一遵循该命名规则。重命名已发布 Java 契约时同步调整提供方与调用方，单纯类型命名变化不改变 JSON 字段、HTTP 方法或参数位置。
- **对象形态统一约定（2026-09-16 用户确认）：DB 对象（数据库映射/DataObject）、Context、RequestBody、ResponseBody 均使用普通 Java class，不使用 record。** DB 对象的规则同时适用于只读查询和状态更新，不限于可变实体。可使用 Lombok 简化构造器和访问方法；字段是否可修改按业务需要决定。新增和本次修改涉及的对象遵守此规则，既有代码按任务范围迁移，不因规范更新批量重构无关代码。
- HTTP 请求、响应分别使用独立的 `*RequestBody`、`*ResponseBody` class，按能力放入 `request`、`response` 包；必要嵌套对象也使用 class。可使用 Lombok `@Getter/@Setter`，不使用 record，不强制 `*Models` 容器，不在 `*Api` 内定义 DTO。核心服务模型发布在自己的轻量 `*-api` 模块，BFF 页面模型仍由 BFF 维护。该规则不要求批量改写 Foundation 已发布的机制模型。
- HTTP RequestBody 使用 Jackson 正常属性绑定，以 Jakarta Bean Validation / Hibernate Validator 表达必填、长度、格式、值域和嵌套约束；通过入口 `@Valid` 触发。内部 Request 的注解由 `AbstractProcessor.validateBasic` 统一触发，复用宿主的 Validator，业务 Processor 不重复手写字段检查或重写 validate 来调用校验器；非模板入口仍须明确校验触发点。见 [2026-09-17 公共校验决定](decisions/2026-09-17-processor-basic-validation.md)。不得为普通绑定增加 DELEGATING JsonCreator、Map 工厂和字段名常量；特殊 Wire 语义仍须明确合同并验证。
- API 方法、RequestBody、ResponseBody 及字段使用中文 JavaDoc；方法首行简短说明，以 `@param/@return` 解释输入输出及必要 HTTP 语义。字段首行说明含义，单位、可空条件、默认值和特殊约束使用 `<li>...</li>` 补充。外部 API 同步维护 OpenAPI 描述，两者不得定义不同含义。
- 数据库映射对象直接使用业务实体名，由 `dataobject` 包表达其持久化职责；类型名不得追加 `DO` 后缀。DataObject 以所属业务能力 Maven JAR 为可见性边界：该 JAR 内的 Repository、Service、Domain Service 和必要内部应用代码可以使用自己的 DataObject，但 Controller/HTTP API 不得把它直接作为传输对象，其他 Maven 模块也不得导入、接收或返回它。
- DataObject 每个字段使用同样的中文 JavaDoc 格式：首行简短含义，必要的单位、可空和业务约束用 `<li>` 补充。注释简短不等于省略调用方需要的合同。
- `*Mapper` 专用于 MyBatis 持久化接口和明确的数据库映射职责；Wire DTO、Application/Domain 模型、Owner Command/Result 或两个非持久化边界之间的转换组件统一命名为 `*Converter`。Jackson 的 `ObjectMapper` / `JsonMapper` 是第三方固定类型名，不在此规则内。不得用 `Mapper` 为普通转换类制造数据库层歧义。
- Spring 配置前缀直接从能力开始，例如 `compensation.*`、`xxl.job.*`，不增加无意义的 `paytech` 中间层。
- Topic 使用小写 `topic_{source_system}_{business_subject}`，Consumer Group/Queue 使用小写 `cg_{consumer_system}_{consumer_subject}`；消息统一使用 UTF-8 JSON。

### 2.1 对外金额格式

- 第一方 HTTP 请求与响应的金额统一使用**币种主单位的普通十进制字符串**，同时携带 ISO 4217 币种（可位于所属订单等外层对象）。例如 `"currency": "USD", "amount": "10.00"` 表示 10 美元，`"1000"` 表示 1000 美元，不得解释为 10 美元。
- 响应按币种精度补齐小数位：USD/AED 为 `"10.00"`，JPY 为 `"10"`，KWD 为 `"10.000"`。请求允许省略无意义的尾随零，但必须符合业务范围与币种精度；不隐式舍入、不接受 JSON 数值金额或科学计数法。
- Java 内部使用精确 `BigDecimal` 或项目 Money 值对象，不通过 `float/double` 转换，不在 Processor/Service 中乘除 100 来补偿接口单位。
- 第三方 Channel 协议如要求最小单位整数，只在对应渠道适配边界精确转换，遵循该渠道合同及币种精度；该协议不改变第一方 API 金额语义。数据库金额和历史记录不因 Wire 格式调整而换算。

### 2.2 View 使用范围

- 查询接口需要组合多张表的数据作为返回结果时，使用按业务命名的 `*View` 表达查询结果；View 只承载本次查询所需的数据，不承担写入、状态流转或请求上下文职责。
- 除多表组合查询外，一般不建议使用 View。单表查询、写接口及普通内部数据传递默认复用已有 DataObject、Context 或领域结果，不为逐层传递、字段改名或响应组装新增 View 中间层。
- 复用 DataObject 仅限所属业务模块内部；HTTP 响应仍使用 `*ResponseBody`，跨模块仍使用 Owner 公开的领域结果。减少 View 不改变 API 或模块边界，也不能通过换名为 DTO、Result、Snapshot 复制同义中间对象。
- 多表组合仍遵守表归属：同模块由 Owner 查询，跨模块通过各 Owner 的公开查询能力组合；BFF 页面聚合职责保持。这里的 View 指 Java 查询结果模型，不是数据库视图，不授权跨模块 JOIN 或跨库查询。
- 例如支付详情同时返回 Intent、Attempt 和 Action 数据时使用 `PaymentDetailView`；pay 写接口直接从 Context 组装已有 Response，不再增加 `PayView`。

## 3. 包结构与依赖方向

- 复杂核心服务可以按 `JAVA_PROJECT_STRUCTURE.md` 增加少量业务能力 Maven JAR，但模块不是微服务：仍只有一个 `web` 启动进程、一个数据库和一个事务管理器。初始通常控制在 3–5 个模块；没有当前完整业务能力、明确表归属和真实消费者时不拆模块。
- 每张业务表只能由一个业务模块拥有。其他模块不得访问其 Repository、Mapper、DataObject 或表，不得用跨模块 JOIN、复制 SQL、View、存储过程或 `JdbcTemplate` 绕过；需要数据时调用 Owner 模块明确发布的业务能力接口，或由 `web` Processor 编排。该跨模块接口使用 Owner 定义的 Domain / Application Command / Result，不使用 DataObject 或通用 CRUD 模型。所谓“只调用对方的原子 Repository”同样禁止。
- 业务模块只为真实的 Owner 公开能力建立稳定、单向、无环的依赖。用例 Processor 位于所属业务模块，可组合其他模块公开的 Service 或 Processor 及 Owner 定义的领域参数；没有稳定用例 Owner 或会形成环时，由 `web` Processor 组合。不得访问对方 `*Impl`、内部步骤或持久化类型。完整规则见 `JAVA_PROJECT_STRUCTURE.md`。
- `web` 保持启动和协议组合根；业务模块不得依赖 `web`。跨模块写不自动上移 Web：有稳定业务 Owner 时由其 Processor 组合公开 Service，在同一本地事务原子保存；无法保持合法依赖时再由 Web Processor 组合。不能为了未来拆服务把同库原子写改成补偿流程。
- Foundation JAR 中的 `api` 表示允许宿主直接依赖的稳定入口、模型或扩展点，不代表 HTTP API；内部实现不得因为 `public` 修饰符就放入 `api`。
- Foundation 组件（当前主要是 `compensation-event`）使用 `model`、`service`、`persistence` 分层，`persistence` 下再分 `repository` 和 `mapper`；业务服务继续遵守 `JAVA_PROJECT_STRUCTURE.md` 的 `domain` / `domainservice` / `core/dal` 结构，不套用 Foundation 包布局。
- 数据访问抽象统一命名为 `*Repository`，使用 `interface + *RepositoryImpl`；不要新增 `*Store` 表达同一职责。它在 Foundation 中位于 `persistence/repository`，在业务服务中位于专项工程规范指定的位置。
- Foundation 的 `core` 只放不直接参与业务分层、但属于组件核心机制的内部能力，例如 `error`、`observability`、`serialization`、`transaction` 和 `autoconfigure`；业务服务的 `core/dal` 按 `JAVA_PROJECT_STRUCTURE.md` 使用，两者都不能成为杂物目录。
- 外部机制触发内部服务工作时，入站适配器统一放在与 `service` 平级的 `ext` 包下：XXL-Job 等调度入口放 `ext/job`，既有或迁移期 Dubbo Provider 放 `ext/dubbo`，REST Controller 放 `ext/rest`。其他协议只有存在真实入口时才新增 `ext/<protocol>`，不得创建空包。
- 普通 `ext` 适配器只负责协议解析、入口校验、上下文转换和委托 `service`（BFF 简单转发可直接委托本地 Client Port），不承载业务规则、事务编排或持久化逻辑。补偿 Handler 作为任务执行入口，按第 5.2 节直接编排自身任务；出站 HTTP、Provider 和第三方 SDK 适配器放独立 `integration` 边界。`ext/dubbo` 只定义兼容代码的位置，不改变当前禁止新增 Dubbo/OpenFeign 的技术边界。
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
- 所有业务枚举都实现 `CodeEnum`；有安全默认消息的错误枚举实现 `CodeMessageEnum`。对外稳定 Code 定义发布在提供方 `*-api/api/enums`，内部业务或持久化枚举放在所属业务模块的 `core/enums`（未拆模块时为 Web 的对应包）；既有领域枚举按任务迁移，不复制同义 Code。
- 每个枚举常量单独声明，相邻枚举项之间保留一行空行，不将多项挤在同一行；若下一项带 JavaDoc 或注解，空行放在上一项与下一项的 JavaDoc/注解之前。API、内部领域及测试枚举均遵循此排版，不改变 Code 值或声明顺序。
- 数据库、HTTP、MQ、日志和业务比较使用 `getCode()`，禁止依赖 `name()`、`ordinal()` 或枚举声明顺序。Code 发布后不可修改，Java 枚举常量名可以安全重构。
- DataObject 的闭合枚举列直接使用实现 `CodeEnum` 的 Java 枚举：契约已发布的 Code 复用本服务 `*-api` 枚举，内部 Code 使用所属模块 `core/enums` 枚举。通过 `paytech-mybatis-support` 的 `CodeEnumTypeHandler` 为每个枚举类型显式注册；不启用全局扫描或默认枚举替换。未知数据库 Code 必须使当前读取 Fail-Closed，不能映射为 `null`。MySQL `DATETIME(6)` 到 `Instant` 统一使用同模块的 UTC `InstantTypeHandler`，服务不得复制同义 Handler。
- 数据库中由配置或主数据动态维护的目录型 Code 属于开放集合，不得建成 Java 枚举或注册封闭枚举 TypeHandler；使用 `String` 或经过校验的开放值对象承载，使数据库新增合法目录项不会令旧代码反序列化失败。只有由代码控制、取值真正封闭的状态、方向和类型才使用枚举。业务必须依赖的稳定目录项可集中定义在职责明确的 `*Codes` 常量类中，禁止散落魔法字符串，也禁止用不完整枚举冒充完整目录或用长串条件穷举筛选。
- 对外发布的 `*-api` DTO 和 MQ 契约字段仍以 `String` 承载枚举 Code，不使用 Java enum 作为序列化字段类型；`api/enums` 只发布稳定 Code 定义，不改变传输兼容策略。提供方在边界内从 `CodeEnum#getCode()` 转换，消费方显式解析和校验；未知 Code 必须进入可观察、Fail-Closed 的兼容性处理，不能依赖反序列化异常、`null` 或兜底枚举静默放行。
- 错误 Code 使用一眼可读、稳定的英文大写语义，例如 `COMPENSATION_EVENT_CONFLICT`，不使用 `PAYTECH-COMMON-400001` 一类公司前缀加数字流水号。
- 业务、参数与并发异常的 `Exception#getMessage()` 是可安全外显的人类提示，说明必要业务上下文，不包含凭据、敏感明文、SQL 或完整 Payload；MVC 边界把它投影为 Problem `detail`。系统异常只对外返回公共安全说明，不返回内部 Cause/message。BFF 与前端须保留约定公共业务语义，不统一替换成模糊的“操作失败”。
- Spring MVC 服务引入 `paytech-mvc-web-starter` 后统一使用其 `GlobalExceptionHandler`；服务不得重复创建本地 `@RestControllerAdvice`、私有 Problem/Error Body 或同义状态映射。公共错误类别归 Foundation；确有消费契约的业务错误码才由服务发布，Foundation 不依赖业务系统。
- `ApiErrorStatusResolver` 仅用于 401/403/415 等明确外部契约的状态扩展；公共并发冲突 409、上游无效响应 502 与依赖不可用 503 由共享处理器映射。`FailException` 扩展只接受 4xx，`ErrorException` 只接受 5xx，其他结果回退默认。不得借此创建逐业务提示的错误目录，也不得把系统失败改成业务拒绝。
- 调用内部共享错误契约的同步 `@HttpExchange` API 时，复用 `paytech-http-client-support` 的 `HttpServiceClientFactory` 统一转换网络、HTTP 状态、RFC 9457-based Code-MoR Problem 和解码异常，并保留原始 Cause。公共出站组件统一将合法 MDC Request ID 写入 HTTP Header，业务接入配置不得逐服务复制 MDC 传播、响应包装或同义 JSON 解析。接口代理由配置层注册单例 Bean，业务适配器直接注入调用，不重复编写 `execute/invoke` 或逐请求 catch 包装。特殊错误码只在工厂配置中显式注册；只有真正不同的业务转换、恢复或审计职责才在业务层捕获。该公共处理不安装全局 AOP、不自动重试，也不套用到第三方不同的错误协议。旧四字段错误体只允许按[全系统迁移设计](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)在兼容 Reader 内临时读取。
- HTTP 状态是调用结果的第一判断依据：`2xx` 返回当前接口的正常 typed DTO；除 `HEAD` 外，`4xx/5xx` 返回 Foundation 统一的 RFC 9457-based Code-MoR Problem，必填 `type`、`title`、`detail`、`code`、`requestId`、`timestamp`，请求 Body 字段错误可附 `errors[{field,message}]`。Body 不复制 `status`；响应 `X-Request-Id` 必须与 Body 一致。`HEAD` 错误只返回状态和必要 Header，不生成 Body。当前 `type=about:blank` 只承担通用问题标识，业务细分使用应用扩展 `code`；不得为了“统一外壳”包装全部成功响应。
- 未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 记录并映射为 `500`。不得用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、换一个同义异常或把所有失败改成 `503`；只有当前层能够恢复、必须释放资源/回写异步状态，或确实承担不同的外部协议错误契约时才捕获，并保留原始 Cause。
- 同一 Starter 自动记录每个 MVC 请求的一条终态 `INFO` 日志；服务不得再增加重复 Body 日志 Filter。普通端点保留完整 Request/Response Body 与耗时，不脱敏、不截断、不记录 Header/Cookie。2026-09-04 Operations 首版增加共享 `web.logging.metadata-only-path-prefixes`（默认空）：敏感前缀在缓存前分流，仅保留安全元数据，不读取/缓存/记录 Body 或 Query；未知编码路径保守处理。新增流式、SSE、文件或二进制接口前仍须重新评估全量缓存与日志边界。
- 上述完整 Body 行为只适用于没有更窄专项安全契约禁止日志的接口。专项文档优先：例如认证和 Vault 禁止 Token、敏感明文和完整 Payload 进入普通日志，必须完整配置并验证上述共享 metadata-only 边界；不得通过服务本地 Filter 绕过公共边界。

## 5. 事务与代码可读性

事务公理和测试隔离仍按 [2026-09-12 决定](./decisions/2026-09-12-use-case-orchestration-and-transaction-boundaries.md)执行；用例职责由 [2026-09-16 合并决定](./decisions/2026-09-16-payment-based-coding-conventions.md)更新。以下规则适用于新增及本次涉及的代码，不局限于 Payment，也不表示既有系统已迁移。

- 所有新增代码、抽象、接口、配置、模块、表和中间层都必须先通过必要性门禁：能够指出当前真实用例、删除该实现会产生的具体错误，以及现有机制为什么不能承担。无法证明这三点时不新增；“以后可能需要”、形式对称、所谓最佳实践或追求层次完整都不构成理由。
- 优先删除不影响当前正确性、契约和可观测性的代码。评审不能只问“这样写是否正确”，还必须问“完全不写是否仍然正确”；若答案为是，就不保留该实现。
- 业务和 Foundation 事务统一使用 `TransactionTemplate` 显式表达边界、传播行为和返回值，不使用 `@Transactional`。
- **事务公理（2026-09-12 用户确认，全项目）：事务内不允许调用外部服务，只能操作数据库。** 此处数据库仅指当前服务拥有、参与本地事务的数据库；与数据库操作直接相关的纯内存校验、计算和对象构建不属于外部调用。
- 公理约束完整调用链，不只检查事务入口：Processor、Handler、Service、Repository 及其间接调用均不得在数据库事务内发起 HTTP/RPC、调用远端 Owner/Provider/Vault、读写 Redis 或发布 MQ；外部只读查询也没有例外。不得把外部调用藏进本地 Service、Client 包装或异步提交以绕过边界。
- Processor 在事务前完成输入关系、归属、业务资格及下游查询；Service 接收已验证 Context。写入时可能变化的状态、版本、唯一性、额度由条件 SQL/CAS/唯一索引和 Repository 行数检查保护，必要局部数据库锁按不变量决定，不机械重新查询整套上下文。下游操作在提交后执行，结果保存另开短事务。
- Processor 入口统一拒绝调用方已有活动数据库事务，不因当前流程没有外部调用而放宽，也不通过挂起事务或切换线程绕过。Processor 可在本地保存阶段开启事务；调用其他 Processor 和外部步骤必须在该事务之外。事务回滚后不得执行依赖该提交的下游操作。
- 普通展示、列表和详情读取默认不创建显式事务，即使内部执行多条 SQL，只要业务允许它们观察相邻的已提交状态。只有写操作、显式锁，或已证明多次读取混合不同提交时点会违反当前业务不变量时，才使用事务；选择只读事务时必须在代码或权威设计中说明需要保护的一致性快照及其失败后果。
- 一个方法只承担一个明确职责，原则上不超过 30 行；优先卫语句和早期返回，嵌套不超过三层。
- `if`、`else if` 和 `else` 的分支体必须使用 `{}`，即使只有一条 `return`、`throw` 或其他语句也不得省略；卫语句同样适用。
- 每个手写 Java 方法都必须有中文 JavaDoc；Lombok、record accessor 等生成方法不要求补写。简单方法至少用一行说明业务目的；涉及事务、幂等、恢复、并发、外部调用、异常转换或重要副作用的复杂方法使用多行 JavaDoc，解释必要的入参、返回值、边界和特殊结果。`@Override` 不因继承了类型签名而省略当前实现承担的业务语义。
- Processor 的内部辅助方法默认使用包级可见性，不机械声明 `private`；模板扩展点使用 `protected`，真正提供给包外调用方的入口才使用 `public`。包级可见不代表公共 API，调用方仍只能从已批准的 Controller、Service 或 Processor 入口进入。
- 用例入口的阶段顺序、上下文和协作者边界统一按第 5.2 节组织；不依靠方法拆分或层层转发掩盖完整流程。
- 多个入口真实复用、且无副作用的校验可以集中到职责明确的 `*Validator`；一次性卫语句留在所属方法，不把散落的 `require*` 机械搬进总揽式公共校验器。Validator 不打开事务、不访问 Repository/Mapper/外部 Client，也不能替代数据库约束、受影响行数和事务内重证。
- 同一状态对象在多个调用点需要判断“是否属于若干状态之一”时，可由该对象提供类型安全的 `statusIn(Status...)`；单一状态判断继续直接使用 `==`。不建立接受任意枚举/字符串的全局状态工具，也不以该辅助方法绕过合法状态迁移校验。
- 不为不存在的职责预建抽象、空接口或通用上下文。写接口复用 Foundation `AbstractProcessor` / `AbstractLockProcessor` 的无业务依赖入口骨架，不再逐服务创建同义模板；模板仅负责入口事务检查、基础校验顺序及所需资源锁，不承载命令幂等、业务事务、HTTP 日志或错误响应。业务 Service/Domain Service 的 interface + Impl 不等于必须创建空 Service。
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
- 需要跨实例资源协调的同步写 Processor 复用 Foundation `AbstractLockProcessor`；单资源由 `lockCommand` 给出稳定 Key 和 mode，多资源通过 `LockCommand.tryLocks` 一次提供完整逻辑 Key 集合及共享等待预算、有限租约。无锁入口使用 `AbstractProcessor`。业务不复制 Redisson 获取/释放逻辑，不叠加注解锁。未迁移的 `@ResourceLock` 消费者继续按原合同使用；迁移需保持实际互斥域、配置和事务范围，并核对同资源所有入口，不能只替换一个父类。能力合同见 `TECHNOLOGY_STACK.md` 第 6.7 节。
- `LOCK` 持续等待、允许中断；`TRY_LOCK` 在指定等待时间内尝试，默认等待 0，未获取返回并发冲突。等待和租约单位为毫秒，租约为正数或 -1（Watchdog 自动续租），省略租约时为 -1。等待上限只由 TRY_LOCK 表达，不给 LOCK 传无效等待参数；参数由 LockCommand 提供，不读取注解锁的服务级 Wait/Lease 配置。
- 对既有 `@ResourceLock`，固定资源可省略 key，动态 Key 保持原受限表达式合同；对模板锁，Key 由已验证的稳定资源 ID 构造。两者均不得使用邮箱、Token、Secret、敏感明文或自由文本。敏感标识派生仍须批准，不以普通 SHA-256 代替该判断。
- 既有 `@ResourceLock` 注解只放在非 `final` Spring Bean 的公开、非 `static`、非 `final` Processor 具体实现方法，并包住该方法内部完整的 `TransactionTemplate`；不得只标接口，也不得放在 Controller、Repository、Mapper、私有方法或依赖同类自调用触发。一个用例的全部资源必须在该入口一次声明，不通过另一个带锁 Bean、方法引用或嵌套代理拼装锁集合。所有资源 Key 在数据库事务开始前一次性解析并获取，提交或回滚后逆序释放；多锁先规范化、去重，再按完整逻辑 Key 的稳定 ASCII 字典序在一个总 Wait 预算内获取，不能依赖注解声明顺序。
- 既有 `@ResourceLock` 的目标必须是启动期可预测、非懒加载的 singleton Bean；override 必须在具体方法完整复写所有祖先锁声明，只有没有 override 的具体父类方法可以沿用声明。应用必须显式配置服务级有限 Wait/Lease，并在启动期校验协调开关、Foundation Redisson 来源、所有 scope 下唯一且验证后冻结的客户端对象身份、Namespace/Wait/Lease、FactoryBean/Bean 生命周期、实际代理与固定 Advisor/执行链身份和顺序、其他 Advisor 未复用或另建锁 Interceptor、JDK 接口暴露、方法可代理性、参数元数据、表达式、属性读取和声明类型；配置或静态绑定无效时失败启动，不能把错误延迟到首个请求。`Future`、`CompletionStage`、`Callable`/`Runnable`、JDK 函数式或迭代遍历接口、Java `Stream/BaseStream`、Reactive Streams、Java `Flow`、Kotlin Flow、Kotlin suspend 等延迟执行入口不支持持锁。竞争失败或中断不执行业务，中断状态必须恢复；Redis 不可用失败关闭；释放失败或失去 Owner 不得掩盖业务异常，也不得强制解锁或静默切换到另一互斥域。
- Redis Lease 到期、进程暂停、连接中断与释放失败都必须评估；锁不是永久所有权凭证，续租或提交前所有权检查也不等价于数据库原子性。Redis-first 不等于 Redis-only：Expected State/Version CAS、唯一索引、幂等、本地事务，以及关键关联、权限上限、资金与跨行不变量确实需要的数据库行/哨兵保护仍是最终防线；捕获异常只承担明确恢复、资源清理或协议转换职责，保留 Cause。
- 不把数据库事务和悲观锁混为一谈：去掉显式锁不表示取消事务，`UPDATE` 本身仍会获取行锁。幂等与旧版本冲突也不能只交给有 TTL 的 Redis Key。
- 验证至少覆盖相关的同资源竞争、不同资源并行、事务失败后释放、Lease 过期/中断、依赖不可用，以及跨资源不变量。使用真实仓库本地 MySQL/Redis，不用方法 Stub 证明分布式协调。

### 5.2 用例编排与职责划分

每个同步写接口由一个具体 Processor 处理，位于所属业务模块的 `service/processor`。无业务模块拆分时使用 Web 的对应包。纯查询按实际复杂度决定是否创建 Processor：只做协议适配、委托一个本地 Service 的简单查询可以省略；Controller 不查询仓储、不持有事务、不直接调用外部 SDK。

Processor 主方法直接展示阶段和提前返回，不要求每个阶段建立一个类：

| 阶段 | 职责与边界 |
|---|---|
| 1. 基础校验 | HTTP 入口校验 Body 注解；公共 validateBasic 校验内部 Request 非空及默认分组、嵌套字段约束，失败使用 InvalidParameterException。validate 只放首次与重放通用、仅依赖输入的附加判断。 |
| 2. 业务防重（按需） | 核对请求方归属；有业务操作唯一身份时查询原业务记录并比较不可变内容，按业务合同返回或处理。没有此类身份时依据当前状态处理，不为统一模板添加请求幂等。 |
| 3. 业务验证与 Context | Processor 查询本模块 Repository 或公开 Owner 能力，验证新请求资格和关系，一次构造完整 Context；必要下游查询在事务前完成。 |
| 4. 本地保存 | Service 使用已验证 Context，通过本模块 Repository 保存；写时用 SQL 条件、版本、唯一键和行数检查保护并发事实。 |
| 5. 提交后推进 | 仅本次保存成功的新命令推进；外部调用由 Processor/独立执行入口通过 Client/Integration 发起，结果另开短事务保存。 |
| 6. 响应构建 | 从本次 Context 或原业务事实构造最小内部 Response，由入站 Converter 转成 ResponseBody，不为响应重复加载对象。 |

- **Processor 负责查询、校验和编排。** 可以直接查询所属模块 Repository，不能访问 Mapper、逐表写入或跨模块仓储。校验与构建按请求分支排列，不把新建有效期等条件提前到重放之前；复杂度或复用确有需要时才提取纯业务协作者。
- **Service 负责本地业务与保存。** Service（含 Domain Service）不重复验证 Context 中已确认的归属和关系；保留本地状态转换、计算和持久化职责。无论当前是否有事务，都不直接或间接访问 HTTP/RPC、Redis、MQ、KMS 等外部依赖，不调用其他 Service 或 Processor。Service 的组合调用由 Processor 负责；纯计算对象可复用。
- **事务按原子阶段组织。** 单个 Service 可内部使用 TransactionTemplate 完成整个本地保存；多个 Service 必须一起保存时，Processor 开启一个事务并依次调用，参与者加入已有事务，不重复包事务、不独立提交、不使用 REQUIRES_NEW。Repository 不创建或提交事务。外部执行前后的本地事务分别明确，不把全请求包进一个事务。
- **Context 承载本次请求的数据及相关纯业务行为。** 查询和验证完成后一次构造，直接持有本模块 DataObject，贯穿保存、推进和响应；不为同一事实复制 Input/View/Snapshot。可由 Repository 在 SQL 成功后同步对象；事务回滚后丢弃本次 Context，重试读取实际已提交事实。
- 对象不强制只有字段和 getter/setter。仅依赖 Context 已有数据、没有副作用的状态判断、组合条件和派生计算，可封装为有业务含义的方法，例如 `context.canCancelLocally()`；无需为了提取行为另建 Validator/Service，也不要求先有多个调用方。只属于单个领域对象的规则优先由该对象表达，跨所持对象的请求判断可由 Context 承载，不将不相关规则堆入通用 Context。
- Context 的纯业务方法不查询数据库或外部服务，不调用 Service/Processor，不开启事务、不执行持久化或推进流程；需要时间等输入时由调用方显式提供，不隐藏读取当前时间或随机数。判断依据已加载事实，不把未查询当作不存在，不缓存会随所持对象更新而失效的判断结果；这些判断不能替代实际写入时的条件 SQL/CAS、唯一约束和行数检查。
- Service 超过三个业务参数时封装具名 Context，三个以内按业务整体性决定。构造器注入、Repository、Mapper 不套用该数量规则。Context 不进入持久化层，不保存 HTTP 对象、Bean、Repository、事务对象、回调或通用 Map，不重复保存可从对象导航的 ID 和执行开关。
- **跨模块使用公开业务入口。** 纯本地能力调用 Owner Service；需外部访问时，在事务外调用 Owner 公开 Processor 的 process，使用 Owner 领域输入与结果，不传调用方整套 Context 或 DataObject。不调用内部辅助步骤，不把同模块 API Processor 串起来复用几行代码，不形成循环依赖。
- **业务防重来自业务记录。** 按实际操作确定业务唯一身份、作用域、持久化位置及需比较的输入，不把所有写接口都套成原命令重放。HTTP `Idempotency-Key` 由 Gateway 处理；业务系统不得新增公共命令幂等组件的依赖或调用，也不自行复制请求 Hash、租约、命令表或响应快照方案。完整边界见第 5.4 节。
- 业务根 ID 不一定是命令身份；同一资源上的新操作不能一律当作重试。唯一键或 CAS 冲突先回滚；只有接口明确需要时，才在事务外重查原记录并比较内容，不把所有 DuplicateKeyException 当作成功。
- **写时确认依赖数据库。** 不要求逐层重复查询；状态、版本、唯一性和额度必须在实际写入处有效保护，复杂跨行不变量仍保留必要的本地数据库保护。SQL 更新行数不符合预期必须失败，不能用 Redis 锁替代。
- **补偿 Handler 保持独立入口。** 可直接编排本地 Service 和事务外 Integration，无需新增专用 Processor；仍不直接访问 Repository/Mapper。业务状态、未知结果和原操作恢复身份遵循各自合同，不因编码简化新增扣款或伪造终态。
- 主方法使用简短阶段注释、卫语句和业务方法名。无实际需求的阶段直接省略；真实复用且无副作用的规则才提取 Validator，不为形式增加一行转发层。

### 5.3 持久化对象与 Repository

- 一个 Repository 对应一张表，使用 interface + Impl，并只依赖该表 Mapper、时间和 ID 等必要本地能力；SQL 位于 Mapper XML。多表用例放在 Service/Processor，不收敛成全系统总 Repository。
- Service 提取 Context 中的 DataObject、ID、金额和枚举传给 Repository，不以改名 Input 或转成 Map 绕过边界。
- 对已持久化对象，先执行条件更新并确认行数，再直接修改传入的原 DataObject，同步状态、版本、时间及关联指针；失败不改对象。参与状态更新的 DataObject 使用可修改的普通 class，不通过 `toBuilder()` 或复制构造重建替代对象，不要求调用方重新赋回 Context。此类 Repository 更新方法没有其他返回语义时使用 void，Context 保持同一对象引用。新对象可在插入前通过 Builder 等方式准备 ID 和初始值，事务回滚后丢弃本次 Context 及其对象。
- 外部执行的候选结果先保存在用例需要的结果对象中，由结果事务保存后同步原对象；不为没有外部结果或独立语义的接口机械新增 Result。

### 5.4 请求幂等与业务防重

确认依据见[全项目职责决定](decisions/2026-09-17-gateway-request-idempotency.md)。本节适用于所有系统和入口，不限于 Client Gateway API；职责统一不表示每个接口都必须要求 `Idempotency-Key`。

| 能力 | Owner | 边界 |
|---|---|---|
| HTTP 请求幂等 | Gateway | 处理 `Idempotency-Key`、调用方及操作作用域、请求摘要、重复请求控制、响应记录与重放、过期清理。 |
| 业务防重与并发保护 | 对应业务系统 | 依据业务唯一编号、原记录、当前状态、唯一约束、条件更新/CAS 和必要事务保护业务不变量。 |
| 业务恢复与可靠执行 | 对应业务 Owner / 既有补偿机制 | 使用稳定业务操作身份查询或恢复未决业务，不由请求重放组件接管业务阶段或重新执行副作用。 |

**Gateway 请求语义：**

- 请求幂等记录保留 **72 小时**。在同一调用方及操作作用域内，同 Key、同参数返回已记录的首次响应；同 Key、不同参数拒绝复用。摘要由 Gateway 负责，业务 Processor 不参与构造。
- 响应重放覆盖约定的 HTTP 状态码、响应体及必要响应头；已记录的系统异常响应同样固化。首次返回 `202` 时重放原受理响应，最新业务状态通过查询接口获取，不能把重放改成实时业务查询。
- 同 Key 命中记录后不重新执行业务，不接管业务恢复阶段。新 Key 或记录过期后的请求可以进入业务，但能否创建、继续或拒绝，由业务事实决定；72 小时不改变业务编号的唯一期限。
- 同 Key 首次仍在执行时不得并发重复下发。等待或返回处理中、72 小时起算点、前置校验失败的记录范围、进程中断及响应未记录窗口的具体策略留给 Gateway 方案，不在各业务服务自行补齐。没有响应记录不能证明业务未执行。
- 请求记录与响应重放不绕过认证、调用方隔离或既有敏感数据边界；不为生成摘要/快照持久化原始敏感请求。

**业务系统开发规则：**

- BFF、核心系统及内部 API 不处理 HTTP `Idempotency-Key`，不增加业务专用幂等 Filter、AOP、Adapter 或服务本地请求重放组件。内部 Request/Context 不为该 Header 增加字段；Gateway 尚未实现也不是业务侧临时接入旧组件的理由。
- 禁止新增 `paytech-command-idempotency` 依赖、启用或调用。已有调用、Header、表和历史数据属于待迁移范围，按具体任务处理，不作为新接口示例，不通过改名或复制旧实现规避本规则。
- `clientOrderId`、`clientRefundId`、来源提交编号、事件 ID 等有业务含义的身份仍由 Owner 定义；业务根 ID 不一定是操作身份。业务防重可以返回原记录、拒绝重复或依据当前状态继续，不能一律解释为缓存第一次 HTTP 响应。
- 换 Key 不能绕过订单唯一性、退款额度、支付在途/成功事实或取消约束；系统异常不代表业务一定未执行。需要恢复时沿用原业务操作身份，不制造新的扣款或退款操作。
- 保留必要的 MQ 消费防重、补偿事件 Claim/Lease、资源锁和下游业务防重。第三方 Channel 的协议 Key 由对应适配器按该外部合同处理，不是把 Gateway Header 直接用作跨系统业务身份的授权。

## 6. Compensation Event 的当前精简习惯

事件定义与纯工厂的归属依据见 [2026-09-12 决定](./decisions/2026-09-12-handler-owned-compensation-event-factory.md)。以下为项目级编码规则，具体事件身份与恢复合同仍由补偿任务规范及业务 Owner 管理。

涉及补偿时还必须读取 `COMPENSATION_EVENT_GUIDE.md` 和 `paytech-foundation/README.md`。指南中的业务事务、`afterCommit`、Claim/Lease、至少一次、线程池、错误分类、人工恢复、RabbitMQ 与 XXL-Job 职责继续有效；其中所有依赖 `execution_type`、`source_type`、`source_id`、`source_version`、`schema_version`、`payload_hash`、Canonical Envelope、`LocalCompensationEventHandler`、`RabbitMqEventRoute` 或 `mor.compensation.*` 的表/API/配置/测试描述，已被当前精简实现和下列规则替代，不得恢复：

- 一个 `CompensationEventHandler` 只完成一件事，不再使用 `CompensationExecutionType` 区分执行方式。
- **事件定义归执行入口。** 对应 Handler 维护自己的事件类型及纯静态 `buildEvent(业务标识, 必要参数)`，由它封装登记请求的协议字段；Domain Models 保留业务命令与结果，不集中存放 Handler 专属补偿常量。仅为构造一项任务不另建一层 Factory、Service 或 Processor。
- **调用方只提供业务输入。** 业务标识和已冻结参数由调用方传入；工厂只返回不可变登记请求，可执行必要的纯内存参数校验，不查询数据库、生成新的业务身份、登记任务、调度线程或调用下游，也不依赖 Handler 实例与 Spring Bean 状态。
- **构建、登记、执行各守边界。** 同一服务、同一编排模块内，Processor 可直接调用 Handler 的纯事件工厂；任务仍在本地业务事务中通过 Registrar 登记，由 Foundation 在提交后唤醒执行器。不得注入 Handler 实例或直接调用 `handle()` 代替可靠调度；包与模块边界见 `JAVA_PROJECT_STRUCTURE.md` 第 5.6 节。
- Handler 运行时以 `eventKey` 及有业务含义的 `subEventType` 查询宿主业务数据库；无业务子类型时按 `COMPENSATION_EVENT_GUIDE.md` 第 4 节使用占位值。`payloadJson` 可为空，只保存确实没有其他持久化来源的最小补充数据，不能承载完整业务上下文。
- 历史 `sourceId` / `sourceType` 命名分别收敛为 `eventKey` / `subEventType`；身份字段和接口参数顺序固定为 `eventKey, eventType, subEventType`。
- 不再保留 `sourceVersion`、`schemaVersion`、Canonical Envelope 或 Payload Hash。
- Foundation 只要求宿主创建 `t_compensation_event`。人工恢复审计的存储/API/权限由宿主按需实现，不因为引入 JAR 自动要求额外业务表。
- `t_compensation_event` 不定义 `CHECK CONSTRAINT`；状态、次数、Lease、锁版本和 Payload 合法性由入口校验、条件更新与开发共识共同保证。
- 补偿登记、Claim 和结果回写继续使用显式 `TransactionTemplate`。`paytech-xxl-job-starter` 只提供与业务能力无关的通用 Executor 配置，不得依赖或引用补偿包；`paytech-compensation-event` 主动依赖该 Starter、自己持有补偿扫描 Handler，并把 XXL-Job 限定为兜底唤醒和分片，不能替代数据库 Claim/Lease。

## 7. 测试代码习惯

- 测试基础设施、`auto/manual` 边界、本地中间件、外部 DTO Mock 和生命周期以 [UNIT_TEST_GUIDE.md](./UNIT_TEST_GUIDE.md) 为准；落地结构见[公共测试示例](./examples/UNIT_TEST_EXAMPLE.md)；测试复用仓库 `local-environment`，不得连接 Nacos、配置中心或任何真实业务环境。
- **禁止为了测试在业务代码中加入测试实现或测试行为。** Mock、Fake、Simulator、故障注入、固定成功结果、测试计数器和测试专用配置只能放在测试模块的 `src/test/java`、`src/test/resources` 或已批准的测试专用工件中，不得进入生产 `src/main`、运行依赖或可执行制品。生产业务流程不得根据 `test`、`local-mock` 等 Profile/配置切换为模拟行为；仅换类名或默认关闭开关不构成隔离。
- 测试通过测试侧配置组装被测真实链路，真实出站 Client 访问独立的测试 HTTP 接口，由 Mocker 返回按请求匹配的 DTO，不改写生产分支来驱动场景；是否允许端口替身及其证据范围仍服从 `UNIT_TEST_GUIDE.md` 和该切片的明确授权，不能把测试侧模拟通过写成真实 Owner 接口已对接。
- 外部集成只有在本次范围明确允许暂缓时才能保留 TODO，并写明待接 Owner/契约与缺失行为；未实现时显式返回不可用或按现有合同阻止执行，不能在生产路径伪造成功、远端标识或业务状态。临时模拟仍受测试隔离规则约束，TODO 不构成新增运行 Mock 的许可。
- 涉及事务或测试装配的重构，按实际风险验证提交/回滚、外部调用时机、幂等重放、并发与补偿恢复，并检查测试替身及依赖未进入运行制品；测试观察与故障控制留在测试侧，不为验证边界在业务代码加入探针或测试专用分支。
- 仓库隔离且仅监听 Loopback 的本地测试中间件可以使用提交到测试配置的固定本地账号和密码，使 Maven 与 IDE 中的 `auto` 测试无需手工加载 `.env.local` 即可直接运行。该便利仅适用于明确标记为本地测试、没有生产权限且允许随时重建的账号；生产、共享测试、第三方、Vault、Token、密钥及任何真实环境凭据仍必须通过安全配置注入，禁止写入代码或版本库。固定本地凭据必须与 `local-environment` 初始化值一致，并保留显式环境变量覆盖入口。
- 服务级入口测试复用正式应用启动类及生产组件扫描，只附加测试专用配置，不用长串 `@Import` 为接口重新列举生产 Bean；可自动装配的测试组件优先扫描和构造器注入。纯计算与公共组件机制测试按实际需要选择上下文；完整边界见 `UNIT_TEST_GUIDE.md` 第 5、12 节。
- 数据库准备、清理和只读断言集中到注解式 `TestMapper` 或测试 Repository，测试方法和 Mocker 中不要散落大量 `JdbcTemplate` SQL。
- Builder 提供合法默认值并允许负例覆盖，不访问外部资源或自动修复非法输入；本地 Mocker 插入前置数据，外部 Mocker 配置 DTO/故障并记录调用，Checker/Probe 只观察和断言。外部 DTO Mock 不要求完整状态机，也不允许用 Mockito 行为 Stub 替换本服务生产链路。
- 每例执行 MockTestBase 的全局 `@BeforeEach`，再执行测试类自身的 `@BeforeEach`；方法名不同，不覆盖或手动调用 super。基类 `@AfterEach` 在断言失败后仍检查意外调用并清理测试专用环境的全部数据和 Mock；失败聚合报告，异步先结束再清理，污染环境不复用。完整范围见测试规范第 12、13 节。
- 测试从业务能力的实际入口触发：HTTP/API、模块公开 Service/Domain Service 接口、MQ Listener/Event Handler、Job Handler、Compensation Event Handler，或公开静态/纯 Java 合同。同一入口下的一组独立测试用例覆盖真实下游链路和更多相关业务场景；不为了覆盖率给每个 Service 方法或内部类机械配一组测试。纯算法、状态机和转换器在能明显缩小故障定位范围时再补充聚焦单元测试，完整入口与场景矩阵以 `UNIT_TEST_GUIDE.md` 为准。
- Handler 与 HTTP Controller 一样是独立业务入口，新增或修改业务 Handler 必须维护独立入口测试类，不能只用 HTTP 链路的间接覆盖代替。入口、框架调度和事务观察的完整要求见 `UNIT_TEST_GUIDE.md` 第 5.1 节；此要求不扩大为逐个测试内部 Service 或辅助方法。
- 测试方法使用清晰的场景名称，例如 `test_invalidParameter`、`test_normalFlow`、`test_retryableError`；异步断言使用有上限的条件等待，不使用固定 `Thread.sleep`。
- 同一测试专用环境串行执行。多个任务或 Maven 进程并行执行时必须使用独立数据库/schema、Redis 前缀和 MQ 命名空间，禁止共享测试库后互相执行 `DROP` 或全表清理。

## 8. Maven、协作与工具

- `paytech-dependencies` 统一管理依赖版本，`paytech-parent` 统一管理 Java、插件和测试规则；子模块只声明实际使用的依赖，不重复写版本。
- 公共代码拆为边界清晰的小型 Foundation JAR，不创建聚合式 `common.jar`，也不把业务 DTO、Repository 或第三方模型塞入公共包。
- 多任务并行时先检查工作区和路径所有权；共享文件必须有 single-writer，基于最新版逐段增量修改，禁止整文件覆盖或回滚其他任务改动。
- 下列规则是根 `AGENTS.md` 中 `Change discipline` 的开发侧薄投影；发生漂移时修复投影，不把本文解释成第二个工作流 Owner。
- 默认不得调用任何 Superpowers skill。只有当前用户在当前请求中明确指定使用 Superpowers 或点名具体 Superpowers skill 时，才可在该请求授权范围内调用；不得从任务类型、历史回合、计划中的 `REQUIRED SUB-SKILL`、文档路径或其他 Agent 的选择中推断授权，也不得跨请求沿用。
- 未获明确授权时，仍须执行必要设计、测试先行、系统化诊断和完成前验证，但使用普通工作流与工具，不调用 Superpowers skill。`docs/superpowers/**` 只是文档路径，不构成授权。
- 非 Superpowers skill 继续按正常触发规则使用；任何 skill 都不授权自行创建分支、提交、推送或 PR。完整决定见 [`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)。

## 9. 配置与 Nacos

- 自有配置按职责归入所属模块的 `*Properties`，使用 `@ConfigurationProperties` 和构造器注入；业务模块位于 `core/configuration`，应用装配配置位于 Web 的 `configuration`。不新建汇集所有模块配置的大类或空配置类。
- 使用 Duration、boolean 等明确类型；字段首行写含义，单位、默认、合法范围和生效时机用 `<li>`。缺少必填配置时明确失败，不以任意默认值掩盖缺失。
- 不在业务代码分散使用 `@Value`、Environment 或 System.getenv 读取同类配置。数据库、Redis、MyBatis 等公共配置复用已有配置类，不复制进业务 Properties。
- 可刷新配置在使用时读取；同一次处理需要一致的值只取一次。配置变化不重算已冻结的业务事实；启动配置明确要求重启，不把 Nacos 刷新等同于连接池或组件自动重建。
- Nacos 加载、安全及版本按 [TECHNOLOGY_STACK.md 第 8 节](./TECHNOLOGY_STACK.md#8-nacos-配置与注册中心)。变更配置键时同步配置类、消费者、加载入口、模板及项目配置清单，说明兼容和迁移；模板维护不等于已发布。

## 10. 前端界面文案与语言

确认依据见 [2026-10-22 前端界面文案决定](./decisions/2026-10-22-ui-text-localization.md)（含 2026-10-23 `operations-console-web` 全量接入补充）。本节只约束前端静态界面文案；服务端报错与校验文案、运营录入的业务内容另立需求，Exception 消息仍按第 4 节使用中文。

- **接入范围。** `client-console-web` 与 `operations-console-web` 已全量接入：所有页面、弹窗、菜单、表头、按钮、提示、确认框与校验文案都走 key。`checkout-web`、`partner-console-web` 尚未接入，接入时按本节一次完成。
- **不写死界面文案。** 已接入的 `*-web` 在 tsx/ts 中不直接书写给用户看的文字：组件内用 `useT(namespace)` 取得 `t`，非组件代码（模型、校验、API 辅助）用 `translator(namespace)` 在调用时取文案，不在模块加载时取值；以 `t(key, params)` 输出。参数用命名占位符传入，不在代码里拼接句子片段；句中需要嵌入等宽编码、链接或输入框时，整句一个 key，节点作为命名参数传入（`operations-console-web` 用 `src/i18n/richText.ts`）。
- **key 命名。** key 为 `<namespace>.<area>.<name>`，以点分层级，首段必须是所属命名空间编码。新命名空间的段名用小驼峰，例如 `finance.adjustments.column.amount`、`recon.exceptions.action.resolve`；枚举值与资源编码保留原大小写（`<namespace>.enum.<Type>.<VALUE>`、`nav.resource.<RESOURCE_CODE>`）。已发布的蛇形 key（`client-console-web` 全部命名空间、`operations-console-web` 的 `i18n-admin` 与 `enum`）保持不变，不为改名迁移。同一句话在不同页面含义不同时用不同 key，不为去重合并。命名空间由运营在 Platform 后台创建，新增命名空间前先确认已创建，否则导入会被拒绝。
- **英文基准与中文种子。** 英文是唯一基准语言，写在 `src/i18n/en/<namespace>.json`，扁平 key 到文案；基准文件随代码提交、在构建时内置，并经控制台导入 Platform。`operations-console-web` 另在 `i18n/zh-CN/<namespace>.json` 维护简体中文种子，与英文同一组 key，用于首批导入与测试核对；上线后的中文修订在 Platform 翻译并发布。**新增或修改界面文案时，先加英文基准 key 并同步补 zh-CN 种子，再在多语言管理中导入、翻译、发布**；`npm run i18n:export-source` 生成导入文件并校验两边 key 集合与 ICU 参数一致。英文用词遵守 [AGENTS.md 规范术语](../AGENTS.md#canonical-terminology)：菜单、标题、按钮、表头用 Title Case，提示与校验句首大写。
- **ICU MessageFormat 是唯一文案语法。** 复数、性别等变化用 `plural`、`select`、`selectordinal`，并且必须包含 `other` 分支；不另造插值语法，不按语言在代码里写条件分支。日期、数字与金额用浏览器 `Intl` 按当前语言格式化（`operations-console-web` 用 `formatDateTime` 等辅助函数，不写死 `zh-CN`），金额的值与精度仍遵守第 2.1 节。
- **枚举展示名。** 枚举 Code 的展示名放在 `enum` 命名空间（跨模块共用）或本模块命名空间的 `<namespace>.enum.<Type>.<VALUE>`，用 `enumLabel` 取值，未知值显示原始 Code；页面不再维护"Code → 中文名称"映射表。本期没有各端共享的命名空间。菜单、面包屑与页面标题按资源编码取 `nav.resource.<CODE>`，没有 key 时回退 Platform 的 `display_name`。
- **语言选择与本地缓存。** 默认语言为英文 `en`，不读浏览器语言；来源顺序为 `?lang=` > 用户在页头或登录页选择并保存在浏览器本地的语言 > `en`，只列 manifest 已发布的语言。切换语言整页刷新并回到原路由，antd 组件语言、dayjs 与 `<html lang>` 一并切换。`operations-console-web` 把用过的 manifest 与语言包缓存在 `localStorage`，启动时有缓存即直接渲染、后台核对新发布并替换，网络不可用时继续用缓存；缓存只放公开语言包，`?release=` 预览不读写缓存。
- **硬编码检查。** 各 web 以 node 测试扫描源码 tsx/ts（测试文件与注释除外），出现中日韩字符即失败，不用 ESLint 规则代替。`operations-console-web` 的 `src/i18nSourceGuard.test.ts` 对全部 `src` 严格执行，唯一白名单是 Platform 注册数据镜像 `navigation/pageCapabilities.ts`；与服务端原文比较的固定字符串用 `\u` 转义并注明是数据。单元测试按 key 或英文基准断言，不断言中文界面文案。
