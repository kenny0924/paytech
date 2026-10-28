# Java 项目结构规范

最后更新：2026-10-27

> 写接口结构按 [2026-09-16 Payment 基准合并决定](decisions/2026-09-16-payment-based-coding-conventions.md)统一；既有代码按具体任务迁移，本规范更新不代表全项目已完成重构。

## 1. 使用范围

当任务涉及以下任一内容时，Agent 必须在修改代码前完整读取本文：

- 创建或调整 Java 服务和 Maven 模块；
- 新增 HTTP API、请求响应、Processor、Domain Service、Repository、Mapper；
- 新增外部系统客户端、MQ 生产者或消费者；
- 集成 Vault、`compensation-event` 或 XXL-Job 异步恢复任务；
- 调整包结构、依赖方向、事务边界或部署单元。

本文定义六个核心 Java 服务的默认工程形态，并说明 Gateway/BFF 的窄工程例外。`api-gateway`、`operations-console-bff`、`client-console-bff` 和 `partner-console-bff` 的业务边界仍以 `APPLICATION_ARCHITECTURE.md` 为准；若它们不需要完整领域分层，不得为了形式一致强行创建空层。涉及可靠异步事件或敏感数据时，还必须分别读取 `COMPENSATION_EVENT_GUIDE.md` 和 `VAULT_SERVICE_ARCHITECTURE.md`。Vault V1 Server 使用 Go，不套用本文的 Java 服务结构；Java 侧只发布内部调用 Client。

Java、Spring、Maven BOM/Parent、MySQL、RabbitMQ、Redis、Nacos、Gateway 和可观测性版本统一以 [TECHNOLOGY_STACK.md](./TECHNOLOGY_STACK.md) 为准。本机运行方式以 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 为准。

组件当前实现范围统一见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)，不在本结构规范中复制某日的切片状态。创建新的核心业务能力、Console BFF/前端或其他垂直切片仍需明确任务授权；Vault V1 已采用独立 Go Server + Java Client，不按本文的 Java 业务服务模板重新创建。

## 2. 参考项目结论

本文参考 `/Users/kenny/Desktop/Workspace/Code-PMX/transaction`。该项目采用一个父 Maven 工程和三个子模块：

```text
transaction/
├── pom.xml
├── transaction-facade-service/  # 对外契约 JAR
├── transaction-web/             # Spring Boot 可部署应用
└── transaction-test/            # 独立自动化测试模块
```

参考项目约有 13 个 Facade Java 文件、117 个 Web Java 文件和 48 个测试 Java 文件。它验证了“独立契约模块 + 可部署 Web 模块 + 测试模块”、极简入站适配、Processor 编排、Domain Service、Repository/Mapper 隔离，以及 Builder/Mocker/Checker 测试模式的可行性。

Code-MoR 继承模块隔离思想，但将 Dubbo `facade-service` 改为 Spring HTTP `*-api`。不复制参考项目的业务模型、公司包名、内部依赖版本、配置中心地址、数据库凭据或历史实现缺陷。

## 3. 标准 Maven 模块

核心服务默认使用以下结构；`<service>` 代表服务根名称，例如未来的 `client-contract`：

```text
<service>-service/
├── pom.xml
├── docs/
│   └── database/
│       └── <service>.pdma.json          # 仅在开始数据库设计时从统一模板创建
├── <service>-api/
│   ├── pom.xml
│   └── src/main/java/<base-package>/api/
├── <service>-web/
│   ├── pom.xml
│   └── src/main/
│       ├── java/<base-package>/
│       └── resources/
└── <service>-test/
    ├── pom.xml
    └── src/test/
        ├── java/<base-package>/
        │   ├── auto/                    # 默认执行的内部闭环测试
        │   ├── manual/                  # 显式 profile 才能运行的人工联调
        │   └── test/                    # Builder/Mocker/Mock HTTP/Checker 等基础设施
        └── resources/                   # auto/manual 配置和 fixture
```

模块职责：

| 模块 | 职责 | 发布/运行 |
|---|---|---|
| 父工程 | 聚合模块、统一版本、插件和依赖管理 | `pom`，不运行 |
| `api` | 内部 HTTP 契约：`@HttpExchange` 接口、传输 DTO 和契约枚举 | 可发布 JAR，不运行 |
| `web` | Spring Boot 入口、业务实现、持久化和适配器 | 唯一业务部署单元 |
| `test` | `auto/manual` 测试、测试数据 DSL、本地中间件连接、外部 DTO Mocker 与独立 Mock HTTP 服务 | 不发布、不部署 |

默认依赖方向：

```text
test ───────→ web ───────→ api

其他服务 ───────────────→ api
```

- `api` 不得依赖 `web` 或 `test`。
- `web` 依赖自己的 `api`，由 HTTP Controller 实现契约接口。
- `test` 可以依赖 `web` 以运行集成测试，但生产模块不得依赖 `test`。
- `auto` 必须从真实本服务入口完成内部闭环；数据库、Redis 和 MQ 复用开发者已启动的仓库 `local-environment`，测试不得创建、停止或重建容器。外部 HTTP 由真实 Client 访问独立的测试 HTTP 接口，按请求返回预设 DTO，不强制外部状态机；MQ 使用真实本地 Broker。两级 BeforeEach 与全局 AfterEach 每例执行，完整边界以 `UNIT_TEST_GUIDE.md` 及其公共示例为准。
- 测试不得使用 `when(...).thenReturn(...)`、`@MockBean`、`@MockitoBean` 等方法级行为 Stub 替换生产链路；详细边界以 `UNIT_TEST_GUIDE.md` 为准。
- `manual` 必须由独立 Maven Profile 显式运行，不能进入默认 `mvn test` 和 CI。
- 其他内部 Java 服务只依赖提供方的 `api`，并在自己的外部集成适配器中使用它。
- 提供方另可发布服务 Java Client（artifactId 以 `-client` 结尾，arch-rules 角色 CLIENT，对调用方等价于 `api`），如 `paytech-vault-client`、`paytech-platform-support-reference-data-client`；包分公共包、`autoconfigure`、`internal`，调用方只用公共包。Client 的测试支持（以 `-client-test-support` 结尾，角色 CLIENT_TEST_SUPPORT）只允许其他服务的 `-test` 模块以 test 范围依赖（R16），Client 自身的测试放在测试支持模块的 `src/test`。参考数据客户端的 `ReferenceData` / `ReferenceCatalog` 只读内存，可在事务内注入（R7 例外），`ReferenceDataRefresher` 仍按外部调用对待。
- 父 POM 用 `dependencyManagement` 固定版本；子模块只声明所需依赖。
- 不添加没有实际使用者的模块、依赖或占位类。
- 拥有数据库的项目使用 `DATABASE_DESIGN_GUIDE.md` 和统一 PDManer 模板；无数据库项目不创建空模型。

### 3.1 统一 Build、BOM 与 Parent

所有服务共享独立的构建项目，不在每个服务中复制版本属性：

```text
paytech-build/
├── paytech-dependencies/     # packaging=pom；统一依赖 BOM
└── paytech-parent/           # packaging=pom；统一插件和构建规则
```

- `paytech-dependencies` 导入已批准的 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba BOM，并管理未被上游统一管理的依赖版本；准确版本只在 `TECHNOLOGY_STACK.md` 维护。
- `paytech-parent` 导入上述 BOM，统一 Java、编码、Compiler、Surefire、Failsafe、JaCoCo、Enforcer 和 Spring Boot Maven Plugin 等构建规则；准确版本只在 `TECHNOLOGY_STACK.md` 维护。
- 每个服务父工程继承 `paytech-parent`；服务子模块只声明实际使用的依赖，不重复声明版本。
- BOM 只提供 `dependencyManagement`，不会自动引入 Starter 或公共 JAR。
- 共享制品通过批准的 Nexus Snapshot/Release 仓库发布，并从聚合仓库解析；准确的本机发布、可用性和验证状态见 `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 与 `CURRENT_IMPLEMENTATION.md`。服务不得直接配置 Hosted 仓库 URL。
- 发布地址和认证来自开发者 Maven Settings；POM 只保留与 Settings 一致的仓库 ID 和属性占位符，不得保存凭据。

Foundation JAR 的完整清单、依赖边界和准确版本只在 `TECHNOLOGY_STACK.md` 维护，本文不复制第二份清单。MVC 服务不得复制 Foundation 已拥有的本地日志 Filter、异常 Advice 或错误响应模型。Redis 与 Caffeine 的 Manager、Cache Name、类型和 TTL 必须按技术栈定义显式声明；不得让 Cache 承担权威事实、幂等、锁、跨实例一致性、Vault Reveal、凭据或敏感明文，也不得把跨领域 DTO、业务枚举、Repository、Mapper 或第三方 Provider 模型放入公共 JAR。

Foundation 资源锁的 `@ResourceLock` 与 Repeatable 容器属于 `paytech-redis-starter` 的公共包 `com.paytech.foundation.redis.api`；Aspect、受限表达式解析、Key 规范化、启动校验和自动配置均为 Starter 内部实现，不进入业务 `*-api`。业务模块只依赖 Starter 的窄公共注解，不复制本地包装或向跨模块业务契约暴露 Foundation 内部类型。完整能力合同由 [`TECHNOLOGY_STACK.md` 第 6.7 节](./TECHNOLOGY_STACK.md#67-paytech-redis-starter)、[资源锁决策](./decisions/2026-09-05-redis-first-resource-lock.md)和[完整设计](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)拥有。

公共能力按最小证据提取：天然属于跨服务基础设施，或至少两个真实服务已经共享同一稳定契约时，才新增职责单一的 Foundation JAR。单个服务的实现先保留在本服务，不为未来复用预建带系统名前缀的包装类、空模块、空层或总揽式 `common`；新增本地技术基础设施前先检查现有 Foundation 是否已经拥有该职责。

### 3.2 API Gateway 工程例外

API Gateway 是基于 Spring Cloud Gateway WebFlux 的入口适配器，不套用核心服务的 `api → web` DDD 分层。默认只有可运行 `api-gateway-web` 与不可部署的 `api-gateway-test`；只有出现真实可复用 Java 合同时才增加 API JAR。Gateway 不拥有业务数据库、核心授权、页面聚合或业务编排，也不得依赖 MVC、阻塞 Redis 客户端或核心业务持久化模型。

动态 Route/Guard Snapshot、Approved Target Registry、可信协议和绝对安全上限由 Gateway 专项架构拥有；具体仓库结构、端点、端口、依赖、验证与部署状态只见 [`api-gateway/README.md`](../paytech/api-gateway/README.md) 和 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)。

### 3.3 BFF 工程例外

BFF 通常只需要 `web + test`：它是无数据库、无签名密钥的页面聚合与 Channel 适配部署单元。简单转发可由入站适配器直接委托本地 typed Client Port；真实的跨 API 授权查询组合、响应塑形和展示模型组装进入 BFF Processor。BFF 不拥有核心事实，也不编排跨系统业务事务。

没有真实共享 Java 消费者时不创建 `*-api`，没有业务职责时不创建空 Processor/Domain/Repository。各 BFF 当前模块、端点、端口、依赖和验证状态只在自己的 README 与 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 维护。

### 3.4 复杂核心服务的业务能力模块

当一个核心服务包含多个稳定业务能力，且单个 `web` 模块已经难以清楚表达表归属、依赖和职责时，可以在 `api`、`web`、`test` 之外增加少量业务能力 JAR。它们只是同一服务内部的编译期边界，不是微服务、部署单元或独立数据库。

```text
<service>-service/
├── pom.xml
├── <service>-api/             # 对外 HTTP 契约
├── <service>-<capability-a>/  # 业务能力及其表访问
├── <service>-<capability-b>/
├── <service>-<capability-c>/
├── <service>-web/             # 唯一启动、协议入口和跨模块编排
└── <service>-test/            # 完整链路和模块边界测试
```

“模块”在此专指 Maven JAR；“分层”是模块内部的 `domain`、Domain Service、Repository、Mapper 等代码组织；“微服务”则是独立进程和独立数据库。三者不能混用。增加业务模块不授权增加进程、服务发现、远程调用、独立事务管理器、独立数据库或分布式事务。

#### 是否需要拆业务模块

只有同时满足以下条件才增加业务能力模块：

1. 它对应当前已经存在或已经批准的完整业务能力，而不是一个技术名词、单张表或未来设想。
2. 能列出该模块唯一负责的业务规则、聚合和数据库表。
3. 能定义少量稳定的业务能力接口，让调用方不需要知道表结构和持久化步骤。
4. 拆分后即使默认没有业务模块互依也能完成职责；若确有获准依赖，该依赖必须单向无环，且不需要共享 Repository 或 DataObject。

初始通常控制在 3–5 个业务能力模块。不得按 Controller、Processor、Service、Repository 等技术层横向拆 Maven 模块，也不得为每张表、每个接口、每个状态机或每个将来可能拆出的服务建立模块。没有真实代码和消费者时不创建空模块、空包或占位接口。

#### 表和持久化归属

- 每张业务表必须有且只有一个业务模块作为持久化 Owner，并在系统专项设计或模块 README 中记录归属。
- 只有 Owner 模块可以定义该表的 DataObject、Mapper、Repository 和写入规则。其他模块不得直接读取或写入该表。
- 禁止通过注入其他模块的 Repository/Mapper、复制 SQL/映射、跨模块 JOIN、数据库 View、存储过程或直接 `JdbcTemplate` 绕过表归属。
- 同一个物理数据库和本库逻辑关系不代表表可以被任意模块访问。关系字段及其应用校验表达数据完整性，模块接口表达代码访问权限；不得用物理外键替代任一边界。
- 跨模块需要的数据由 Owner 模块通过业务查询/命令接口提供。接口返回所属模块定义的领域结果；不得暴露 Mapper、Repository、DataObject 或任意 CRUD 能力。
- 服务级技术表，例如本服务唯一的 `compensation_event`，由 `web`/服务级基础设施统一负责；不得复制到每个业务模块。存量命令幂等表仅用于待迁移消费者及历史解释，不为新接口增加服务本地请求幂等表。

#### 允许和禁止的依赖

默认生产依赖为：

```text
                         ┌──→ capability-a
external caller → api ← web ─→ capability-b
                         └──→ capability-c

test ───────────────────────→ web
```

- `web` 是组合根，可以依赖自己的 `api` 和各业务能力模块。
- `test` 依赖 `web`；生产模块不得依赖 `test`。
- 业务能力模块不得依赖 `web`、`test` 或其他模块的实现包。
- 业务模块可因真实的 Owner 公开能力建立单向依赖：被调用方提供窄业务入口和自己的领域输入/结果，命令归属明确且依赖长期无环。由用例所属模块的 Processor 组合能力，不因涉及两个模块或两张表就强制上移 Web。
- 获准调用只依赖 Owner 发布的 Service/Domain Service 接口，或负责外部访问的公开 Processor 及其领域参数。不得访问 `*Impl`、未公开的内部步骤、Repository、Mapper 或 DataObject；调用具体 Processor 不授权穿透其辅助方法。
- A 模块需要 B 模块的数据时，由 A 的 Processor 调用 B 公开的本地 Service 或事务外 Processor；若无法明确用例 Owner 或依赖会成环，由 Web Processor 分别调用。A 不能调用 B 的 Repository、Mapper、DataObject 或表。
- `api` 只保存对外传输契约，不用于解决业务模块循环依赖，也不存放内部领域对象、DataObject、Repository 或公共业务实现。

跨模块调用只针对 Owner 明确发布的完整业务能力。各模块 Service 不直接调用其他 Service；顺序和事务由用例 Processor 组合。若 A、B 互相依赖或无法形成稳定用例 Owner，由 Web Processor 协调，不借 api/common 包掩盖循环依赖。

```text
正确：PayoutProcessor → AccountingBalanceCommand（锁定余额）
                    └→ TreasuryPayoutCommand（创建出款）

错误：TreasuryService → AccountingRepository → t_account
错误：AccountingService ↔ TreasuryServiceImpl
错误：把 AccountRow 放进 <service>-api/common 供所有模块直接使用
```

#### 跨模块用例和事务

复杂用例由业务 Owner 模块的 `service/processor` 编排；无稳定 Owner 或存在依赖环时，使用 Web Processor。Processor 可查询本模块 Repository 完成关系与资格验证；跨模块只能调用公开能力，不能读取对方持久化对象。

同一进程、同一数据库内需要强一致的跨模块写操作，仍使用一个本地 `TransactionTemplate`：

```text
用例 Owner Processor（必要时为 Web Processor）
  └─ TransactionTemplate
       ├─ capability-a business command
       ├─ capability-b business command
       └─ service-level compensation registration
```

参与该用例的模块方法加入调用方已有事务，不得自行 `REQUIRES_NEW`、提前提交或切换线程。事务边界属于完整用例，而不是某一张表；模块归属限制表访问者，但不妨碍多个模块的合法业务接口参与同一个本地事务。

不得为了“以后可能拆微服务”把当前本地原子事务改成 Compensation Event。Compensation Event 只用于事务提交后的异步副作用、外部系统调用恢复，或模块未来真正成为独立服务和独立数据库之后的最终一致性流程。真正拆服务时必须重新设计远程契约、幂等、事件顺序、失败恢复和数据迁移，不能声称 Maven 模块拆分已经自动解决这些问题。

#### 模块接口检查

一个模块公开接口必须表达业务动作或业务查询，例如“锁定可出款余额”“创建税额快照”，不得暴露 `updateStatus`、`saveAny`、`queryTable` 等通用数据库操作。所有业务 Service 必须使用 `interface + *Impl`；所有 Domain Service 必须使用 `interface + *Impl`，不因只在 Owner 模块内部使用或只有一个实现而例外。跨模块接口的参数和结果使用 Owner 定义的 Domain / Application Command / Result，不能返回 API Request/Response 或 DataObject。

DataObject 的可见性边界是其 Owner 业务能力 Maven JAR，而不是 Repository 类本身。Owner JAR 内的 Repository、Service、Domain Service 和必要内部应用代码可以使用或返回自己的 DataObject；Accounting 等其他模块不得使用 Tax 等 Owner 模块的 DataObject，只能调用其公开业务能力。Controller/HTTP API 也不得把 DataObject 直接作为传输对象。需要保护领域不变量、聚合多表、隔离持久化变化或跨模块发布时，在 Owner 边界转换为 Domain/Application 对象；没有这些价值时不强制在同一 JAR 内复制同义对象。

[公共写接口开发示例](examples/WRITE_API_EXAMPLE.md)以 Payment pay 展示上述规则的完整调用链：Processor 位于所属模块的 service/processor，使用内部 Request/Response 和本模块 DataObject Context，Web Converter 只转换协议模型；开发前读取，交付前按示例第 11 节自查。[Payment 专项补充](../paytech/payment/docs/development/processor-conventions.md)仅维护支付业务映射。此前 REST Processor 直接使用 RequestBody 的例外已被 [2026-09-16 合并决定](decisions/2026-09-16-payment-based-coding-conventions.md)取代。

每次新增或调整业务模块时必须同步验证：模块清单、表唯一归属、获准的单向依赖、Maven 依赖图、跨模块接口、跨模块用例事务范围，以及至少一个阻止越权 `*Impl`、未发布 Domain Service、DataObject、Repository、Mapper、SQL 访问和循环依赖的架构测试。完整决定见 [`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)。

## 4. API 模块与内部 HTTP 契约

接口演进与代码类型命名遵循 [DEVELOPMENT_CONVENTIONS.md 第 2 节](./DEVELOPMENT_CONVENTIONS.md#2-项目标识与命名)：用户未指定时沿用原接口版本，新接口默认 `/v1`；类型名不带协议版本后缀。业务版本字段和历史持久化格式继续保持各自语义。

推荐结构：

```text
api/
├── <capability>/
│   ├── *CommandApi.java
│   ├── *QueryApi.java
│   ├── request/           # 独立的 *RequestBody class
│   └── response/          # 独立的 *ResponseBody class
└── enums/
    └── *Status.java
```

规则：

- 内部同步接口使用 Spring `@HttpExchange`，具体方法使用 `@GetExchange`、`@PostExchange` 等明确 HTTP 注解。
- API 按业务能力拆分，例如 `ClientEligibilityQueryApi`、`AgreementCommandApi`；禁止创建包含所有接口的巨型 `ClientContractApi`。
- `*Api` 只声明 HTTP 方法与协议注解；请求、响应分别使用独立 class，按能力放在 request/response 包，必要嵌套模型也使用 class。不强制 Models 容器或 record，不在 API 接口内定义 DTO。提供方模型属于轻量 `*-api` 模块，BFF 页面模型仍由 BFF 维护。
- 接口方法使用业务语言命名，并通过 JavaDoc 说明参数、结果、幂等和主要错误语义。
- RequestBody 使用正常 Jackson 属性绑定和 Jakarta Bean Validation / Hibernate Validator，嵌套对象使用 `@Valid`。内部 Request 注解由公共 `AbstractProcessor.validateBasic` 触发，子类不重复调用 Validator；仅依赖输入且首次、重放通用的附加关系放在 validate。非模板入口仍需明确校验触发点，不认为注解会自行执行。
- RequestBody、ResponseBody 及字段使用中文 JavaDoc：首行简短含义，单位、可空、默认及兼容约束以 `<li>` 补充。API 方法用 `@param/@return` 说明输入输出及必要 HTTP 语义；外部接口同步 OpenAPI 描述。
- 请求、响应和模型是 HTTP 传输契约，不暴露数据库映射对象、Repository 类型、内部聚合或第三方 SDK 类型。MQ 事件契约不因复用 DTO 而混入 HTTP API。
- 公开 HTTP 与发布的内部 `*-api` 顶层请求 DTO 使用 `*RequestBody`，顶层响应 DTO 使用 `*ResponseBody`。GET 查询 DTO 同样使用 `*RequestBody`，但命名不改变既有 Query/Path 传参，不要求 GET 携带 Body 或增加 `@RequestBody`。
- 嵌套业务模型及内部领域、应用层对象不强制该后缀，不为统一命名添加同义包装。新增和调整 HTTP DTO 时统一遵循规则；已发布 Java 类型重命名时同步更新提供方和调用方。详细规则以 `DEVELOPMENT_CONVENTIONS.md` 第 2 节为准。
- 对外发布且调用方需要理解的稳定 Code 枚举定义统一放在 `api/enums` 并实现 `CodeEnum`；提供方 Web 直接复用，不在领域包复制同义枚举。
- API DTO 字段仍使用 `String` 承载稳定枚举 Code，不声明 Java enum 字段；`api/enums` 是 Code 目录而不是 JSON 字段类型。仅内部使用的枚举放在所属业务模块的 `core/enums`，未拆分时为 Web 的对应包，边界转换使用 `getCode()`，消费方显式校验未知 Code。
- 契约变更先判断兼容性。新增可选字段优先，删除、改名或改变语义必须明确版本策略。
- 有界的内部同步查询默认直接返回完整 typed DTO。没有真实负载指标和已批准的兼容契约时，不预先增加 ETag、`If-None-Match`、`304`、Canonical Body Hash 或其他 HTTP 缓存协议。
- `api` 模块保持轻量，只引入 HTTP 契约、序列化和校验所需依赖；不引入数据库、Spring Boot 应用启动器、服务发现配置或业务实现。
- 不在 `api` 模块放通用工具、Repository、Mapper、领域服务、HTTP 客户端配置或异常堆栈实现。

典型接口：

```java
@HttpExchange(url = "/clients/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientEligibilityQueryApi {

    /**
     * 查询 Client 交易资格
     *
     * @param clientId Client ID
     * @return 交易资格及不可交易原因
     */
    @GetExchange("/{clientId}/eligibility")
    ClientEligibilityResponseBody getEligibility(@PathVariable String clientId);
}
```

### 4.1 提供方使用方式

以下 Domain Service 与 Processor 规则适用于核心服务。`*-bff` 的职责是前端查询聚合和展示模型组装：简单转发可由 Controller 直接委托一个本地 Client Port，无需补空 Domain Service；真实多接口查询与页面组装由 BFF 的 `service` / Processor 编排，再通过 Client Port 访问上游，不下沉到核心服务。Controller 仍只做协议适配。

`*-web` 依赖自己的 `*-api`。位于 `ext/rest` 的 Controller 只做 HTTP 协议适配；符合简单用例规则时可直接委托一个 Domain Service，否则委托 `service` 中的 Processor：

```java
@RestController
@AllArgsConstructor
public class ClientEligibilityController implements ClientEligibilityQueryApi {
    private final ClientEligibilityService clientEligibilityService;
    private final ClientEligibilityConverter converter;

    @Override
    public ClientEligibilityResponseBody getEligibility(String clientId) {
        return converter.toResponseBody(clientEligibilityService.getEligibility(clientId));
    }
}
```

上例仅适用于无请求关系判断、写事务、防重、跨系统调用或多步编排的本地简单查询。Service 返回自己的领域结果，Converter 完成协议转换，不由 Service 组装 ResponseBody。写操作使用对应 Processor；Controller 不持有事务，不访问 Repository/Mapper/SDK。

`@HttpExchange` 接口是内部 HTTP Method、Path、Header 和 DTO 签名的唯一来源。Controller 必须直接实现该接口，不得再次声明等价的 `@RequestMapping`、`@GetMapping` 或 `@PostMapping`；这些 MVC 注解只用于没有共享 Java API 契约的服务端入口。应用配置的 `context-path` 表达系统边界，接口注解只表达能力内路径，最终地址统一为 `{context-path}/{capability-path}`。接口是否属于内部调用由私有网络、服务发现和 Gateway Catalog 边界决定，不在能力路径中增加调用范围标记。

HTTP 状态是调用方判断结果的第一依据：`2xx` 返回接口定义的正常 typed DTO；除 `HEAD` 外，`4xx/5xx` 返回 `paytech-mvc-web-starter` 的统一 RFC 9457-based Code-MoR Problem，必填 `type`、`title`、`detail`、`code`、`requestId` 和 UTC 六位小数 `timestamp`，请求 Body 字段错误可附 `errors[{field,message}]`；Body 不复制 `status`，响应 `X-Request-Id` 必须与 Body 一致。`HEAD` 错误只返回状态和必要 Header，不生成 Body。当前 `type=about:blank` 只承担通用问题标识，业务细分使用应用扩展 `code`。Controller 不为成功响应增加通用包装层，也不复制服务本地错误响应类型。完整 Wire 与迁移规则见[全系统 Problem Profile 设计](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)。

未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 映射为 `500`。Controller、Processor 和 Domain Service 不使用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、同义包装或统一改成 `503`；只有当前层能恢复、必须释放资源/更新异步状态，或承担明确不同的外部协议契约时才捕获，并把原始异常保留为 Cause。

### 4.2 调用方使用方式

调用方依赖提供方发布的 `*-api` JAR，通过 Spring HTTP Service Client 创建接口代理：

```text
调用方 Processor / 独立执行入口（事务外）
  → 本地 *Client 端口
    → integration 中的 *ClientImpl
      → 注入的 @HttpExchange API 代理
        → RestClient/WebClient
          → Spring Cloud LoadBalancer
            → Nacos 中的服务实例
```

- 调用方不重复定义远程 HTTP DTO，也不重新声明 URL。
- 远程 DTO 只停留在 integration/infrastructure 边界；转换为调用方自己的领域对象后再进入领域逻辑。
- 内部共享错误协议的 HTTP 代理优先通过 Spring `@ImportHttpServices(group = "服务名", types = OwnerApi.class)` 自动注册为单例 Bean，业务 Client 直接注入。配置层返回 Foundation `HttpServiceClientFactory.configureGroup(...)`，为对应 Group 安装公共异常边界；Spring Boot/Cloud 负责客户端创建、传输配置和负载均衡。Group 不配置固定实例 `base-url`，以 Group 名作为服务 ID；Nacos 提供实例。既有显式传输宿主仍可通过 `HttpServiceClientFactory.create(...)` 接入相同异常边界，不自动扩大其他消费者迁移。业务类不重复包装 HTTP 状态、网络与错误体处理，特殊恢复或业务转换仍由拥有该语义的层负责。
- `@HttpExchange` 本身只定义 HTTP 契约。Nacos 负责服务发现，Spring Cloud LoadBalancer 负责实例选择，底层 `RestClient` 或 `WebClient` 通过部署网络限制的私有 HTTP 发送请求；Java 服务不持有 X.509 应用证书。
- 超时、统一错误映射、追踪和熔断在调用方基础配置或适配器中处理；命令请求不得在缺少幂等保障时自动重试。
- 内部服务调用不经过 API Gateway。

### 4.3 OpenAPI 与外部接口

- 内部全 Java 服务以手写 `@HttpExchange` 契约和共享 `*-api` JAR 为默认，使用 springdoc/构建检查输出或校验 OpenAPI 文档。
- Gateway 暴露的 Client API、Checkout API、Channel Callback 和未来第三方 API 必须保持语言无关的 HTTP/OpenAPI 契约，不要求外部消费者依赖 Java JAR。
- 内部 API 与外部 API 使用不同路径、权限和契约包，不能因为 DTO 相似而直接暴露内部接口。
- 新项目不新增 Dubbo 或 OpenFeign 契约；历史系统迁移必须另行设计兼容期。

## 5. Web 模块分层

以下按未拆分业务能力的服务展示；已拆分时，Processor、Service、Context、Integration 及持久化代码位于各自 Owner 模块，Web 保留启动、协议适配和必要的跨模块组合。

```text
<base-package>/
├── Application.java
├── configuration/                  # 本服务运行配置
├── ext/
│   ├── job/                         # XXL-Job 等外部调度入口
│   ├── dubbo/                       # 仅既有或迁移期 Dubbo Provider 入口
│   ├── rest/                        # Controller 与 REST 入站适配器
│   └── mq/                          # 仅存在真实 MQ 入站能力时创建
├── service/
│   ├── *Service.java / impl/        # 本地业务与保存
│   └── processor/                   # 写操作入口及复杂查询，含内部 request/response
├── integration/
│   ├── *Client.java                 # 出站端口
│   └── impl/                        # Provider/RPC/HTTP 适配实现
├── domain/                          # Context、聚合、值对象和纯领域模型
├── domainservice/
│   ├── service/                     # 领域服务接口及 impl
│   ├── repository/                  # 领域仓储接口及 impl
│   ├── validator/                   # 按需创建的无副作用领域规则校验
│   ├── process/                     # 领域流程/策略（确有需要时）
│   └── convert/                     # 明确边界之间的转换
└── core/
    ├── common/                      # 本服务共享类型
    ├── constant/                    # 稳定常量
    ├── enums/                       # 模块内部业务/持久化枚举
    ├── configuration/               # 模块自有 Properties
    └── dal/
        ├── dataobject/              # 数据库映射对象（类型名不加 DO 后缀）
        ├── mapper/                  # MyBatis Mapper
        └── configuration/           # 数据访问配置
```

目录代表职责，不代表必须创建。没有对应能力时不要创建空目录。

`ext` 和 `service` 必须保持平级。外部机制触发本服务工作时，入口适配器按协议放入 `ext/<protocol>`，通常完成协议解析、入口校验、调用上下文转换和服务委托；HTTP 复杂用例编排留在 `service`。补偿 Handler 按第 5.6 节直接编排自身任务，无须另设 Processor。`ext/job`、`ext/dubbo`、`ext/rest` 是当前标准目录，MQ Listener 在存在实际消费能力时使用 `ext/mq`。出站适配实现不属于外部触发入口，统一放在平级 `integration` 包。

`ext/dubbo` 只规定已有或迁移期兼容入口的包位置，不授权新项目引入 Dubbo。Code-MoR 内部同步服务调用仍使用轻量 `*-api` 与 Spring `@HttpExchange`，新项目不得新增 Dubbo 或 OpenFeign 契约。

### 5.1 HTTP 入站适配器

`ext/rest` 中的 Controller 实现 `*-api` 中按能力拆分的 `@HttpExchange` 接口。符合下节简单用例规则时直接委托一个 Domain Service，否则委托一个明确的 Processor。统一参数校验、错误响应、请求 ID 和鉴权上下文由 HTTP 基础设施处理，Controller 不复制业务规则。

查询接口组合多张表的返回数据时使用具名 `*View`；单表查询、写接口及普通内部传递一般不增加 View。查询 View 按归属放在所属模块的 `domain`，BFF 页面聚合结果由 BFF 维护；HTTP 顶层响应仍使用 `*ResponseBody`，不得直接暴露 DataObject。完整选择规则见 [开发规范第 2.2 节](DEVELOPMENT_CONVENTIONS.md#22-view-使用范围)，不为每层创建同义结果或扩大持久化访问权限。

### 5.2 Processor（按需）

每个同步写接口对应一个具体 Processor，放在所属业务模块的 `service/processor`；内部 Request/Response 放在该 Processor 目录下的 request/response。纯查询没有业务判断或多步编排时，可以由 Controller 委托一个本地 Service，不创建空 Processor。

```text
API 字段校验 → Controller/Converter
  → AbstractProcessor.process：拒绝已有事务 → validateBasic → validate
  → 获取实际资源锁（需要时）
  → 请求方归属 → 原提交查询/比较 → 已存在则构建响应返回
  → 新请求业务验证 → 一次构造 Context
  → 事务前依赖（需要时）
  → Service 本地保存 / Processor 组合多个 Service 的原子事务
  → 提交后推进（需要时）
  → 从 Context 构造 Response → 释放锁 → Converter 构造 ResponseBody
```

完整顺序和职责由 [DEVELOPMENT_CONVENTIONS.md 第 5 节](./DEVELOPMENT_CONVENTIONS.md#5-事务与代码可读性)拥有。DB（数据库映射/DataObject）、Context、RequestBody、ResponseBody 均使用普通 Java class，不使用 record；包括只读 DB 对象，完整对象形态约定见同文第 2 节。

- Processor 可查询所属模块 Repository，完成归属、原请求一致性和新请求资格检查；不访问 Mapper，不直接逐表写入，不访问其他模块的仓储或数据库对象。
- Processor 入口不得在调用方已有事务内执行；模板统一检查，子类不重复。外部访问通过 Client/Integration，需其他模块完成时调用其公开 Processor 的 process；不能通过事务挂起、线程切换或内部方法绕过约束。
- 单个 Service 的完整保存阶段可以内部开事务；多个 Service 原子保存由 Processor 开启同一本地事务并依次调用。Service 间不自行编排；本地保存提交后才推进外部步骤。
- Context 一次构造后贯穿请求，直接持有本模块 DataObject；不携带 HTTP Body、Servlet、Bean、回调或重复身份字段，不进入 Repository/Mapper。超过三个 Service 业务参数使用具名 Context。
- Context 可封装基于已有数据的纯业务判断和派生计算，不限定为字段容器；查询、外部调用、事务和流程编排仍由原层负责。完整边界见开发规范第 5.2 节。
- 输入和结果使用内部 Request/Response 或明确领域模型，不依赖 ResponseEntity、HTTP Header 和 Servlet。API Body 与内部类型只为真实模块边界转换，不增加同义 Input/View/Snapshot。
- HTTP `Idempotency-Key` 的校验、摘要与响应重放统一在 Gateway；BFF、Controller/Converter、Processor、Context 和 Service 不承担该机制，不新增公共命令幂等 JAR 的消费或同义 Adapter。内部调用使用明确的业务操作身份；存量 Header/组件按任务迁移。完整规则见[开发规范第 5.4 节](./DEVELOPMENT_CONVENTIONS.md#54-请求幂等与业务防重)。
- 写入口复用 Foundation AbstractProcessor；需要资源锁时使用 AbstractLockProcessor，单资源通过 LockCommand 选择 LOCK/TRY_LOCK，多资源通过 tryLocks 一次提供完整资源集合。无锁需求不创建假 Key；既有 @ResourceLock 不叠加在模板上，未迁移消费者的多锁、代理和配置合同仍保留。
- 主方法显示顺序和提前返回，细节放入业务命名的辅助方法；不为每一步建立新类。Body 与内部 Request 用注解声明字段约束，内部 Request 由父类 validateBasic 统一执行；validate 只含输入相关且首次/重放通用的附加判断，查询和当前状态检查留在相应分支。
- 确需业务防重时，查询原记录使用业务操作唯一身份，不使用当前活动指针或 HTTP Key 替代；比较及重复请求行为由业务合同决定。原提交查询不等同于 Gateway 响应重放，也不是所有写接口的强制阶段。
- 没有本地保存、路由或后续执行的用例省略对应阶段。一个命令仍由一个核心系统拥有，BFF 和 Gateway 不编排跨系统业务事务。

参考 transaction 时读完整调用链，采用其清晰 Context 和阶段组织；不复制 Dubbo 响应对象、通用 catch Throwable 或与当前 HTTP/事务边界冲突的协议实现。

### 5.3 Domain Service

业务 Service 和 Domain Service 处理本地业务与数据保存，均使用 `interface + *Impl`；只有实现标注 `@Service`，调用方依赖接口。不创建没有业务职责的空 Service。Processor、Controller 保持具体类；Repository 保持 `interface + *RepositoryImpl`。

- 接收已验证的 Context 或少量明确业务参数；不重复查询、比对已经确认的 Client/资源归属、工具关系或请求字段。
- 保留本地计算、状态转换与保存职责，通过所属模块 Repository 操作数据库。当前状态、版本、唯一性和额度通过写入条件及更新行数确认，必要局部数据库保护仍按业务不变量保留。
- Service 不调用其他 Service 或 Processor，不直接或间接访问 HTTP/RPC、Redis、MQ、KMS 等外部能力，也不调用会发起外部操作的 Flow/Actor；此限制不取决于当前是否有事务。
- 单个 Service 可以完成整个本地保存事务；多个 Service 的组合和原子提交由 Processor 负责，各参与者加入已有事务，不独立提交。
- Service 的业务参数超过三个时使用具名 Context。Context 不进入 Repository/Mapper；Service 从中取出本模块对象、ID、金额和状态。
- 同一 Owner 模块内可以使用 DataObject；公开跨模块能力使用 Owner 领域参数/结果，不传 HTTP Body 或调用方的整套 Context。Service 不返回 ResponseBody。
- 多个用例真正复用的纯规则可以提取 Validator 或业务对象方法；不为复用几行代码建立另一 Service 调用链。

### 5.3.1 Validator 与 Converter

- HTTP 结构、类型和简单字段约束优先由 Request DTO、Jackson 与 Jakarta Validation 在入站边界完成；Processor/Domain Service 不重复解析协议字段。
- Body 与内部 Request 的注解负责基础字段约束，分别由 HTTP 入口及公共 validateBasic 触发；Processor 的 validate 只放通用输入关系，查询分支负责归属、原提交和新建资格。依赖当前状态、版本、唯一性和额度的并发保护在写 SQL 与 Repository 行数检查处完成，不要求 Service 再查询整套关系。
- 同一校验不在 Controller、Processor、Domain Service 和 Repository 逐层重复。一次性、含义清晰的卫语句保留在职责 Owner 中；多个用例共享且足以形成独立职责时才提取 `domainservice/validator/<capability>/*Validator`，不创建全系统 `CommonValidator`。
- Wire DTO 与 Application/Domain 对象的转换器靠近 `ext/rest` 入站边界；领域对象、Owner Command/Result 或通用持久化记录与业务状态之间的转换放在拥有该语义的 `domainservice/convert/<capability>`。转换组件不访问数据库、不调用外部 Client、不打开事务，也不偷偷执行业务流程。
- 上述非持久化转换类型统一使用 `*Converter`；`*Mapper` 只用于 `core/dal/mapper` 或 Foundation `persistence/mapper` 中的 MyBatis 持久化接口。简单的一次性构造无需为命名规则机械创建 Converter。

### 5.4 Repository 与 Mapper

Repository 使用领域语言屏蔽 MyBatis 细节：

```text
Domain Service → Repository interface → RepositoryImpl → Mapper → 本系统数据库
```

- 一个 Repository 对应一张表，不接收 Context，不负责跨表用例或跨系统业务规则。
- Mapper 只表达本系统数据库操作。
- 生产 SQL 只写在资源目录的 `*Mapper.xml`，接口保留方法签名和参数绑定；禁止 SQL 注解及 `*Provider` 注解。测试源码中的 TestMapper 可保留注解 SQL，具体边界见 [DEVELOPMENT_CONVENTIONS.md](./DEVELOPMENT_CONVENTIONS.md) 的“MyBatis SQL 存放规则”。
- 每个核心系统只能访问自己的数据库，禁止跨库查询、跨库写入或共享表。
- 更新状态时携带期望旧状态或版本号；行数不符即失败，SQL 更新成功后直接同步传入原对象的状态、版本、时间与关联指针，不重建副本或替换 Context 引用。参与更新的 DataObject 使用可修改 class，无其他返回语义的更新方法使用 void。Repository 不开新事务或自行提交；外层事务回滚后丢弃本次 Context。
- `dataobject` 中的数据库映射对象直接使用业务实体名，不追加 `DO` 后缀。它可以在 Owner 业务 JAR 内部使用，但不得作为 HTTP API 或跨 Maven 模块业务接口对象返回。
- 表、字段、索引、手工 DDL/DML 与 DataObject/Mapper 的设计必须和项目 PDManer 模型同步；详细规则见 `DATABASE_DESIGN_GUIDE.md`。

### 5.5 外部集成

外部 HTTP/RPC、Provider SDK、Vault/KMS 等访问由所属模块 `integration` 中的 Client/适配器封装，用例 Processor 或独立执行入口在数据库事务外调用。业务 Service 不依赖出站端口。

- 内部同步 HTTP 使用提供方轻量 `*-api` 的 HttpExchange 合同及公共 HTTP Client 能力，不新增 Dubbo/OpenFeign，不在业务 Processor 中直接操作第三方 SDK。
- Integration 负责远程协议与 Owner 领域输入/结果转换，不持有本地业务事务，不把外部 DTO 或凭据扩散到持久化上下文。
- 需要另一个本地模块完成外部准备或操作时，调用其公开 Processor；若只有纯本地规则和保存则调用 Service。模块依赖保持单向无环，不通过内部辅助步骤绕过公开入口。
- 下游失败、未知结果和恢复按业务合同解释，不把异常全部包装成拒绝，不伪造成功或新请求身份；公共错误映射和 Request ID 传播复用 Foundation。

### 5.6 MQ 与业务事件

- 补偿 Handler 可以直接承担任务元数据校验、本地事务准备、事务外下游操作、本地事务结果落库与补偿结果返回，不单独增加只为该 Handler 服务的 Processor。Handler 依赖 Domain Service、Integration Port 和事务执行器，业务规则仍归 Domain Service；不直接访问 Repository/Mapper，不自行创建重试线程或替代 Foundation Claim/Lease。
- 同一服务、同一编排模块内的 Processor 可引用 Handler 的纯静态 `buildEvent(...)` 构造登记请求，定义归属与纯函数要求见 [开发约定第 6 节](./DEVELOPMENT_CONVENTIONS.md#6-compensation-event-的当前精简习惯)。该例外仅允许构造数据，不允许注入、创建 Handler 实例或直接调用其执行入口，也不允许业务模块依赖 `web/ext`；架构检查应区分纯工厂引用与 Handler 实例依赖。
- 存在真实 MQ 入站能力时，`ext/mq` 负责验签/反序列化之后的事件接收、幂等入口和调用领域能力。
- 业务状态、状态历史和 `compensation_event` 在用例入口明确编排的本地事务内原子提交；MQ 发布只能由事务提交后的执行器完成，不得出现数据库回滚但消息已发送。事务边界完整遵守 `DEVELOPMENT_CONVENTIONS.md` 第 5 节公理。
- 正常路径通过 `afterCommit` 唤醒本地 Dispatcher；XXL-Job 只做兜底扫描、分片或串行隔离，数据库 Claim/Lease/Version 仍是多实例互斥边界。
- 本地 `compensation_event` 重试复用相同 `event_key + event_type + sub_event_type`，Handler 从本库权威事实重建业务事件；它不保存来源版本、Schema、Canonical Envelope 或 Payload Hash。业务 MQ 信封是否包含领域来源版本、Schema 和 Payload Hash 由该业务事件契约独立决定。Broker Confirm 后本地成功记录允许删除；失败达到上限必须保留、告警并支持带 Actor/Reason/Ticket 的人工重开。
- MVP 不建立通用 Inbox/Outbox。消费者在本地业务事务中用业务唯一键、`source_event_key`、Payload Hash、来源版本条件更新和状态机支持重复、延迟和乱序消息，并在事务提交后 Ack。
- 事件 Payload 不得包含敏感明文、Vault 内部密文或完整 Vault Token，只能携带批准的必要业务字段、`file_id + content_hash` 和事实拥有方产生的必要非敏感派生事实。当前 V1 没有跨接收方 Copy 能力；需要敏感数据的消费者通过事实拥有方受控同步接口取得批准结果。
- Finance 的账务事实不可变；更正使用补偿事实和追加分录。

完整的表结构、执行状态、并发和测试要求见 `COMPENSATION_EVENT_GUIDE.md`。

## 6. 依赖方向

允许的主路径：

```text
本地简单查询：Controller → Service → 本模块 Repository → Mapper

写操作/复杂查询：Controller + Converter → 所属模块 Processor
  ├─ 本模块 Repository 查询 → 关系/资格验证 → 完整 Context
  ├─ Owner 公开 Service（本地能力）或 Processor（事务外外部能力）
  ├─ 本地事务 → 本模块/Owner Service → 各自 Repository → Mapper
  └─ 提交后 Integration / 独立执行入口 → 结果短事务

补偿任务：Handler → 本地 Service / 事务外 Integration → 分段本地事务
```

禁止：

- HTTP Controller 直接访问 Repository 或 Mapper；
- Processor 直接访问 Mapper 或另一个系统数据库；
- Service 调用其他 Service、Processor 或外部依赖；
- Repository 调用 Domain Service 或接收 Context；
- 在已有事务内调用 Processor；
- Domain Service 依赖入站协议对象；
- BFF、Gateway 或入口应用拥有核心业务事实；
- 为复用方便让两个服务共享数据库或可变数据库映射对象。

## 7. Spring 与代码风格

- 六个核心 Java 服务和 BFF 默认使用 Spring MVC；Vault V1 Server 使用 Go，Java 调用方使用发布的 `@HttpExchange` Client；API Gateway 使用 Spring Cloud Gateway Server WebFlux，不为了统一形式改成 MVC。
- MVC 服务的内部同步 HTTP 调用默认使用 `RestClient`；只有真实 Reactive/Streaming 边界才使用 `WebClient`。
- 默认不全局启用虚拟线程；必须先验证连接池、Listener、第三方 SDK、MDC 和背压行为。
- 优先构造器注入并使用 `final` 字段；纯字段赋值构造器使用 Lombok `@AllArgsConstructor`，包含校验、防御性复制、派生对象创建或资源配置的构造器保留显式实现；禁止新增字段注入。
- Service 实现类使用 `@Service`，Service 接口不标注 Spring 组件注解；Repository 实现使用 `@Repository`，无更精确语义的组件才使用 `@Component`。
- 数据类可使用 Lombok `@Data`，但领域对象应谨慎暴露无约束 setter。
- 每个手写 Java 方法都写清晰中文 JavaDoc；简单方法至少说明业务目的，复杂方法同时解释必要的入参、返回值、事务、副作用和特殊结果。完整规则见 `DEVELOPMENT_CONVENTIONS.md`。
- 常量和枚举使用业务语义名称，不使用 `getData`、`handle`、`process2` 等模糊名称。
- 金额在 Java 内使用精确 BigDecimal 或项目 Money 类型，并显式携带 ISO 4217 币种；第一方 HTTP 使用币种主单位十进制字符串，输出按币种补齐小数位，见 [金额格式规范](./DEVELOPMENT_CONVENTIONS.md#21-对外金额格式)。禁止浮点金额；渠道最小单位转换只属于协议适配边界。
- 时间、时区、ID 和序列生成由基础能力统一提供，不在业务代码中散落静态随机实现。
- 先实现当前获批用例需要的最小类和配置；不要用一行代理、空抽象或未来扩展点代替清晰的直接调用。只有实际复杂度达到第 5.2 节门槛时才增加 Processor 或应用层抽象。

## 8. 资源目录

典型资源：

```text
src/main/resources/
├── application.yml
├── logback.xml
└── META-INF/
    ├── mybatis-config.xml
    └── sqlmap/
        └── *Mapper.xml
```

- 配置文件不得写入真实密码、Token 或内部环境默认凭据。
- 环境差异通过配置中心或环境变量注入。
- Spring Cloud Alibaba 2025.1 使用 `application.yml` 的 `spring.config.import` 导入 Nacos 配置，不创建 `bootstrap.yml`。
- MyBatis XML 的 namespace、方法名、参数名和结果映射必须与 Mapper 一致。
- 不复制参考项目的 Spring Cloud Config、Dubbo、MySQL 或插件版本；所有版本和客户端边界必须遵守 `TECHNOLOGY_STACK.md`。

## 9. 新增功能检查清单

Agent 完成 Java 功能前逐项检查：

- [ ] 业务事实属于当前系统，而不是其他五个核心系统。
- [ ] 公共契约和内部实现没有混在同一模块。
- [ ] `*-api` 只包含轻量 HTTP 契约，没有业务实现或持久化依赖。
- [ ] API DTO 的枚举语义使用 String code，稳定 Code 定义在 api/enums，内部枚举位于所属模块 core/enums；使用 CodeEnum/按需 CodeMessageEnum，不复制同义目录。
- [ ] RequestBody/ResponseBody 使用独立 class，按能力放入 request/response；API 不内嵌 DTO，不强制 Models 容器；GET 参数位置不因类型命名变化。
- [ ] 多表组合查询使用具名 View；其他场景一般不增加 View 中间层，必要的 API 响应和跨模块领域合同仍保持。
- [ ] RequestBody 使用正常 Jackson 属性绑定与 Hibernate Validator，嵌套使用 @Valid；没有多余的 DELEGATING 工厂，非 HTTP 入口明确校验触发点。
- [ ] `*Api`、顶层 Request/Response 和非显然字段具有与 OpenAPI 一致的语义、单位、可空及默认值说明。
- [ ] Controller 实现 `@HttpExchange` API、没有重复 MVC Mapping；简单用例只委托一个 Domain Service，其他用例委托 Processor。
- [ ] 外部触发入口位于与 `service` 平级的 `ext/job`、`ext/dubbo`、`ext/rest` 或实际协议子包；补偿 Handler 可编排自身任务，业务规则仍委托 Domain Service，不直接操作持久化。
- [ ] 调用方通过本地 `*Client` 适配器隔离远程 API DTO，没有让远程 DTO进入领域层。
- [ ] `*ClientImpl` 只承担本地端口到实际代理的适配，公共调用模板没有吸收业务恢复、事务或领域决策，Processor 也没有复制远程调用骨架。
- [ ] 未新增 Dubbo 或 OpenFeign 契约，内部调用未绕行 Gateway。
- [ ] 每个写操作使用对应 Processor；简单本地查询按实际职责省略，不为形式新建一行转发类。Controller 不持有事务或访问 Repository/Mapper/SDK。
- [ ] Processor 只查询本模块 Repository，业务写入通过 Service；不调用 Mapper、外部 SDK 或其他模块仓储。
- [ ] 实际存在的 Processor 未依赖 HTTP 类型，也没有为单一实现创建空泛的抽象基类或公共上下文。
- [ ] Processor 主处理方法可以按阶段顺序阅读，内部辅助方法使用包级可见性；所有手写方法都有与复杂度相称的中文 JavaDoc。
- [ ] Domain Service 与 Repository 使用业务语言命名。
- [ ] 无副作用共享校验位于职责明确的 Validator；没有跨层重复校验，也没有让 Validator 访问持久化、外部 Client 或替代事务内重证。
- [ ] 非持久化边界转换使用 `*Converter`，`*Mapper` 只表达 MyBatis/数据库映射职责；一次性简单构造没有被机械包装。
- [ ] 只访问本系统数据库。
- [ ] DataObject 类型名没有 `DO` 后缀；仅在 Owner 业务 JAR 内使用，HTTP API 和跨 Maven 模块业务接口使用 Domain/Application Command/Result；数据库结构已与 PDManer、手工 DDL/DML、Mapper 和 Repository 保持一致。
- [ ] 模块依赖用于真实 Owner 公开能力，保持长期单向无环；跨模块只使用公开 Service/Processor 和 Owner 领域输入/结果。
- [ ] 多 Service 原子保存由用例所属模块 Processor 开一个本地事务；没有稳定 Owner 或存在循环风险时由 Web Processor 组合，未访问对方 Impl 或持久化对象。
- [ ] 事务只覆盖本服务数据库原子操作；包含间接调用在内的外部查询、命令和 MQ 发布均在事务外，结果落库使用后续短事务。
- [ ] 写锁复用 AbstractLockProcessor，单资源模式或完整多资源集合、Key、等待和租约明确；既有注解锁保留代理与配置合同，同入口不叠加，迁移核对相同互斥域的所有消费者。
- [ ] Redis 锁没有取代数据库最终保护；验证范围按实际机制覆盖竞争、释放、中断与租约，未把注解代理门禁当作模板已具备的能力。
- [ ] Service 不调用其他 Service、Processor 或外部依赖；Processor 入口不在已有事务中执行。
- [ ] Context 完整构造且只持有本模块数据，不进入 Repository/Mapper；超过三个 Service 业务参数使用具名 Context。
- [ ] Repository 按表维护；SQL 条件和行数检查通过后同步对象，事务回滚后丢弃 Context。
- [ ] 原提交查询早于新建资格检查；请求身份和不可变内容比较明确，不以活动指针或通用 DuplicateKeyException 代替防重语义。
- [ ] 自有配置归入模块 Properties，公共配置复用原组件；模板、加载入口和配置清单一致。
- [ ] 异常信息包含必要业务上下文。
- [ ] 未预期异常交给共享全局处理器；局部 `catch` 具有实际恢复、清理、异步状态或协议转换职责，并保留原始 Cause。
- [ ] 没有为尚未批准的技术方案创建占位基础设施。
- [ ] 没有一行代理、空层或猜测性公共类；新增 Foundation 能力已有跨服务复用证据且职责单一。
- [ ] 已按 `UNIT_TEST_GUIDE.md` 补充适当层级的测试。
- [ ] 新增出站依赖使用已启动的仓库 `local-environment` 或独立的测试 HTTP DTO Mock，保留真实适配器、意外调用检查和逐用例全局清理。
