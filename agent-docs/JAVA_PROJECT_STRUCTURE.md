# Java 项目结构规范

最后更新：2026-09-05

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
        │   └── test/                    # Builder/Mocker/Simulator/Checker 等基础设施
        └── resources/                   # auto/manual 配置和 fixture
```

模块职责：

| 模块 | 职责 | 发布/运行 |
|---|---|---|
| 父工程 | 聚合模块、统一版本、插件和依赖管理 | `pom`，不运行 |
| `api` | 内部 HTTP 契约：`@HttpExchange` 接口、传输 DTO 和契约枚举 | 可发布 JAR，不运行 |
| `web` | Spring Boot 入口、业务实现、持久化和适配器 | 唯一业务部署单元 |
| `test` | `auto/manual` 测试、测试数据 DSL、本地中间件连接和外部系统 Simulator | 不发布、不部署 |

默认依赖方向：

```text
test ───────→ web ───────→ api

其他服务 ───────────────→ api
```

- `api` 不得依赖 `web` 或 `test`。
- `web` 依赖自己的 `api`，由 HTTP Controller 实现契约接口。
- `test` 可以依赖 `web` 以运行集成测试，但生产模块不得依赖 `test`。
- `auto` 必须从真实本服务入口完成内部闭环；数据库、Redis 和 MQ 复用开发者已启动的仓库 `local-environment`，测试不得创建、停止或重建容器。外部 HTTP/MQ 通过有状态 Simulator 和真实协议交互；完整边界以 `UNIT_TEST_GUIDE.md` 为准。
- 测试不得使用 `when(...).thenReturn(...)`、`@MockBean`、`@MockitoBean` 等方法级行为 Stub 替换生产链路；详细边界以 `UNIT_TEST_GUIDE.md` 为准。
- `manual` 必须由独立 Maven Profile 显式运行，不能进入默认 `mvn test` 和 CI。
- 其他内部 Java 服务只依赖提供方的 `api`，并在自己的外部集成适配器中使用它。
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

动态 Route/Guard Snapshot、Approved Target Registry、可信协议和绝对安全上限由 Gateway 专项架构拥有；具体仓库结构、端点、端口、依赖、验证与部署状态只见 [`api-gateway/README.md`](../api-gateway/README.md) 和 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)。

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
- 服务级技术表，例如本服务唯一的 `compensation_event` 或命令幂等表，由 `web`/服务级基础设施统一负责；不得复制到每个业务模块。

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
- 业务能力模块默认不建立彼此的直接 Maven 依赖。只有被调用能力已经稳定、可复用，Owner 明确发布窄业务接口及 Domain / Application Command / Result，调用不承担跨模块用例编排，且依赖能够长期保持单向、无直接或间接环时，调用方才可以单向依赖 Owner 模块。
- 获准的调用方只依赖 Owner 发布的业务 Service 或 Domain Service 接口和上述业务模型，不得依赖 `*Impl`、未发布的内部 Domain Service、Spring 内部 Bean 或持久化接口。任一条件不满足时，由 `web` Processor 分别调用相关模块。
- A 模块需要 B 模块的数据时，只有满足上述稳定单向门禁才能调用 B 发布的业务能力接口；否则由 `web` Processor 先调用 B，再把明确的领域输入交给 A。A 绝对不能调用 B 的 Repository、Mapper、DataObject 或表；所谓“只调用原子 Repository”仍然是越权访问。
- `api` 只保存对外传输契约，不用于解决业务模块循环依赖，也不存放内部领域对象、DataObject、Repository 或公共业务实现。

这里的“可以调用 B 的业务能力接口”不等于“可以依赖 B 的任意 Service”。允许的是 B 明确作为模块边界发布的窄接口；若新依赖会形成循环 Maven 依赖，或 A 和 B 互相需要对方，说明协作属于更高层用例或模块边界划错，必须交给 `web` Processor 编排。

```text
正确：PayoutProcessor → AccountingBalanceCommand（锁定余额）
                    └→ TreasuryPayoutCommand（创建出款）

错误：TreasuryService → AccountingRepository → t_account
错误：AccountingService ↔ TreasuryServiceImpl
错误：把 AccountRow 放进 <service>-api/common 供所有模块直接使用
```

#### 跨模块用例和事务

复杂用例由 `web` 中的 Processor 编排。Processor 调用模块公开的业务能力接口，不调用 Mapper/Repository，不实现模块内部业务规则。

同一进程、同一数据库内需要强一致的跨模块写操作，仍使用一个本地 `TransactionTemplate`：

```text
Web Processor
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

每次新增或调整业务模块时必须同步验证：模块清单、表唯一归属、获准的单向依赖、Maven 依赖图、跨模块接口、跨模块用例事务范围，以及至少一个阻止越权 `*Impl`、未发布 Domain Service、DataObject、Repository、Mapper、SQL 访问和循环依赖的架构测试。完整决定见 [`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)。

## 4. API 模块与内部 HTTP 契约

推荐结构：

```text
api/
├── <capability>/
│   ├── *CommandApi.java
│   ├── *QueryApi.java
│   └── *Models.java        # public record *RequestBody / *ResponseBody 及相关嵌套模型
└── enums/
    └── *Status.java
```

规则：

- 内部同步接口使用 Spring `@HttpExchange`，具体方法使用 `@GetExchange`、`@PostExchange` 等明确 HTTP 注解。
- API 按业务能力拆分，例如 `ClientEligibilityQueryApi`、`AgreementCommandApi`；禁止创建包含所有接口的巨型 `ClientContractApi`。
- `*Api` 只声明 HTTP 方法与协议注解；请求、响应及相关嵌套模型按业务能力组织到独立 `*Models` 容器，不直接定义在 API 接口中。容器使用 `public final` 与私有构造器，传输对象使用 `public record`，保留 `*RequestBody` / `*ResponseBody` 后缀及字段校验。模型仍属于提供方的 `*-api` 模块，BFF 自己的页面模型留在 BFF。
- 接口方法使用业务语言命名，并通过 JavaDoc 说明参数、结果、幂等和主要错误语义。
- 请求、响应和模型是 HTTP 传输契约，不暴露数据库映射对象、Repository 类型、内部聚合或第三方 SDK 类型。MQ 事件契约不因复用 DTO 而混入 HTTP API。
- 公开 HTTP 与发布的内部 `*-api` 顶层请求 DTO 使用 `*RequestBody`，顶层响应 DTO 使用 `*ResponseBody`。GET 查询 DTO 同样使用 `*RequestBody`，但命名不改变既有 Query/Path 传参，不要求 GET 携带 Body 或增加 `@RequestBody`。
- 嵌套业务模型及内部领域、应用层对象不强制该后缀，不为统一命名添加同义包装。新增和调整 HTTP DTO 时统一遵循规则；已发布 Java 类型重命名时同步更新提供方和调用方。详细规则以 `DEVELOPMENT_CONVENTIONS.md` 第 2 节为准。
- 对外发布且调用方需要理解的稳定 Code 枚举定义统一放在 `api/enums` 并实现 `CodeEnum`；提供方 Web 直接复用，不在领域包复制同义枚举。
- API DTO 字段仍使用 `String` 承载稳定枚举 Code，不声明 Java enum 字段；`api/enums` 是 Code 目录而不是 JSON 字段类型。仅服务内部使用的枚举放在 Web 的 `domain/enums`，边界转换使用 `getCode()`，消费方显式校验未知 Code。
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

    @Override
    public ClientEligibilityResponseBody getEligibility(String clientId) {
        return clientEligibilityService.getEligibility(clientId);
    }
}
```

上例仅适用于方法少于 10 条有效可执行语句、只调用一个 Domain Service，并且没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排的简单用例。空行、注释、花括号和格式换行不计入语句数；任一条件不满足时都必须使用 Processor。Controller 不包含事务、Repository/Mapper/SDK 调用或跨系统编排。

`@HttpExchange` 接口是内部 HTTP Method、Path、Header 和 DTO 签名的唯一来源。Controller 必须直接实现该接口，不得再次声明等价的 `@RequestMapping`、`@GetMapping` 或 `@PostMapping`；这些 MVC 注解只用于没有共享 Java API 契约的服务端入口。应用配置的 `context-path` 表达系统边界，接口注解只表达能力内路径，最终地址统一为 `{context-path}/{capability-path}`。接口是否属于内部调用由私有网络、服务发现和 Gateway Catalog 边界决定，不在能力路径中增加调用范围标记。

HTTP 状态是调用方判断结果的第一依据：`2xx` 返回接口定义的正常 typed DTO；除 `HEAD` 外，`4xx/5xx` 返回 `paytech-mvc-web-starter` 的统一 RFC 9457-based Code-MoR Problem，必填 `type`、`title`、`detail`、`code`、`requestId` 和 UTC 六位小数 `timestamp`，请求 Body 字段错误可附 `errors[{field,message}]`；Body 不复制 `status`，响应 `X-Request-Id` 必须与 Body 一致。`HEAD` 错误只返回状态和必要 Header，不生成 Body。当前 `type=about:blank` 只承担通用问题标识，业务细分使用应用扩展 `code`。Controller 不为成功响应增加通用包装层，也不复制服务本地错误响应类型。完整 Wire 与迁移规则见[全系统 Problem Profile 设计](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)。

未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 映射为 `500`。Controller、Processor 和 Domain Service 不使用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、同义包装或统一改成 `503`；只有当前层能恢复、必须释放资源/更新异步状态，或承担明确不同的外部协议契约时才捕获，并把原始异常保留为 Cause。

### 4.2 调用方使用方式

调用方依赖提供方发布的 `*-api` JAR，通过 Spring HTTP Service Client 创建接口代理：

```text
调用方 Domain Service
  → 本地 *Client 端口
    → integration 中的 *ClientImpl
      → 注入的 @HttpExchange API 代理
        → RestClient/WebClient
          → Spring Cloud LoadBalancer
            → Nacos 中的服务实例
```

- 调用方不重复定义远程 HTTP DTO，也不重新声明 URL。
- 远程 DTO 只停留在 integration/infrastructure 边界；转换为调用方自己的领域对象后再进入领域逻辑。
- 内部共享错误协议的 HTTP 代理由配置层通过 Foundation `HttpServiceClientFactory` 创建并注册为单例 Bean，业务 Client 直接注入调用。公共异常转换只作用于这些显式创建的代理；业务类不重复包装 HTTP 状态、网络与错误体处理，特殊恢复或业务转换仍由拥有该语义的层负责。
- `@HttpExchange` 本身只定义 HTTP 契约。Nacos 负责服务发现，Spring Cloud LoadBalancer 负责实例选择，底层 `RestClient` 或 `WebClient` 通过部署网络限制的私有 HTTP 发送请求；Java 服务不持有 X.509 应用证书。
- 超时、统一错误映射、追踪和熔断在调用方基础配置或适配器中处理；命令请求不得在缺少幂等保障时自动重试。
- 内部服务调用不经过 API Gateway。

### 4.3 OpenAPI 与外部接口

- 内部全 Java 服务以手写 `@HttpExchange` 契约和共享 `*-api` JAR 为默认，使用 springdoc/构建检查输出或校验 OpenAPI 文档。
- Gateway 暴露的 Client API、Checkout API、Provider Callback 和未来第三方 API 必须保持语言无关的 HTTP/OpenAPI 契约，不要求外部消费者依赖 Java JAR。
- 内部 API 与外部 API 使用不同路径、权限和契约包，不能因为 DTO 相似而直接暴露内部接口。
- 新项目不新增 Dubbo 或 OpenFeign 契约；历史系统迁移必须另行设计兼容期。

## 5. Web 模块分层

推荐包结构：

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
│   └── processor/                   # 按需创建：复杂用例编排、校验、事务边界
├── integration/
│   ├── *Client.java                 # 出站端口
│   └── impl/                        # Provider/RPC/HTTP 适配实现
├── domain/                          # 聚合、值对象和纯领域模型
│   └── enums/                       # 仅服务内部使用的业务/持久化枚举
├── domainservice/
│   ├── service/                     # 领域服务接口及 impl
│   ├── repository/                  # 领域仓储接口及 impl
│   ├── process/                     # 领域流程/策略（确有需要时）
│   └── convert/                     # 明确边界之间的转换
└── core/
    ├── common/                      # 本服务共享类型
    ├── constant/                    # 稳定常量
    └── dal/
        ├── dataobject/              # 数据库映射对象（类型名不加 DO 后缀）
        ├── mapper/                  # MyBatis Mapper
        └── configuration/           # 数据访问配置
```

目录代表职责，不代表必须创建。没有对应能力时不要创建空目录。

`ext` 和 `service` 必须保持平级。外部机制触发本服务工作时，入口适配器按协议放入 `ext/<protocol>`，只完成协议解析、入口校验、调用上下文转换和服务委托；需要编排的内部用例留在 `service`。`ext/job`、`ext/dubbo`、`ext/rest` 是当前标准目录，MQ Listener 在存在实际消费能力时使用 `ext/mq`。出站调用不属于外部触发入口，统一放在平级 `integration` 包。

`ext/dubbo` 只规定已有或迁移期兼容入口的包位置，不授权新项目引入 Dubbo。Code-MoR 内部同步服务调用仍使用轻量 `*-api` 与 Spring `@HttpExchange`，新项目不得新增 Dubbo 或 OpenFeign 契约。

### 5.1 HTTP 入站适配器

`ext/rest` 中的 Controller 实现 `*-api` 中按能力拆分的 `@HttpExchange` 接口。符合下节简单用例规则时直接委托一个 Domain Service，否则委托一个明确的 Processor。统一参数校验、错误响应、请求 ID 和鉴权上下文由 HTTP 基础设施处理，Controller 不复制业务规则。

### 5.2 Processor（按需）

`service/processor` 中的 Processor 负责需要独立应用层的参数校验和业务流程编排，不是每个入口都必须创建的转发类：

```text
validate request
  → 调用领域服务
  → 组合本系统内的业务步骤
  → 填充 response
```

- Controller 方法少于 10 条有效可执行语句，且只委托一个 Domain Service、没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排时，可以省略 Processor。空行、注释、花括号和格式换行不计入语句数，不能通过调整格式规避边界。
- 任一条件不满足时，无论物理代码行数多少都必须使用 Processor。
- 复杂用例的默认事务边界位于 Processor；省略 Processor 的简单一致性读由 Domain Service 持有只读 `TransactionTemplate`。Controller 不持有事务边界。
- 需要 Foundation 资源锁的写用例把 `@ResourceLock` 标在启动期可发现、非懒加载 singleton、非 `final` Spring Bean 的公开、非 `static`、非 `final` Processor 具体实现方法，而不是只标接口；override 必须在具体方法重复声明。锁 Aspect 必须包住该方法内部完整的 `TransactionTemplate`，确保全部 Redis 锁先获得、数据库事务后开始，提交或回滚后才逆序释放。Controller、Domain Service、Repository、Mapper、private/final 方法、同类自调用、直接 `new` 的对象、scoped/lazy Bean、异步返回方法和已有外层事务的调用点都不是支持入口；多个资源必须在该用例入口一次声明完整，不能靠嵌套代理调用拼装锁集合。启动门禁同时验证实际代理包含适用的 Resource Lock Advisor，且其顺序位于适用事务 Advisor 外层。
- Processor 只能调用 Domain Service 或明确的应用端口，不得直接调用 Repository、Mapper 或外部 SDK。
- Processor 的请求和结果使用应用语义，不依赖 `ResponseEntity`、HTTP Header、Servlet 或客户端代理类型；协议参数在 Controller/Web 基础设施完成解析和转换。
- 一个命令只由一个核心系统拥有。BFF 和 Gateway 不承担该命令的业务编排。
- 方法使用卫语句，嵌套不超过三层，单个方法原则上不超过 30 行。
- 不为单个 Processor 创建抽象基类。只有至少两个真实 Processor 共享稳定模板流程和上下文字段时才提取 `Abstract*Processor` 或公共请求上下文。

参考 `transaction` 的 `AbstractProcessor` 同时注入了 Repository；这是参考项目现状，不是 Code-MoR 允许的依赖方式。

### 5.3 Domain Service

Domain Service 承载业务规则和跨聚合协调：

- 所有业务 Service 必须使用 `interface + *Impl`；所有 Domain Service 必须使用 `interface + *Impl`。接口按业务能力命名，实现使用相同名称加 `Impl`，调用方依赖接口，只有实现类标注 `@Service`；
- 该规则不要求创建没有业务职责的空 Service；一旦职责需要 Service，就不因模块内单实现而省略接口。Processor、Controller 保持具体类，Repository 保持 `interface + *RepositoryImpl`；
- 实现依赖 Repository 和出站端口，不依赖 HTTP Controller 或 Processor；
- 普通业务检查优先使用 `BizCheckFailException` 和包含必要上下文的中文消息；只有调用方确实需要按 Code 区别处理时才使用本服务 `CodeMessageEnum` 与对应 `FailException`；
- 状态转换必须校验旧状态，防止静默覆盖并发更新。

### 5.4 Repository 与 Mapper

Repository 使用领域语言屏蔽 MyBatis 细节：

```text
Domain Service → Repository interface → RepositoryImpl → Mapper → 本系统数据库
```

- Repository 不包含跨系统业务规则。
- Mapper 只表达本系统数据库操作。
- 生产 SQL 只写在资源目录的 `*Mapper.xml`，接口保留方法签名和参数绑定；禁止 SQL 注解及 `*Provider` 注解。测试源码中的 TestMapper 可保留注解 SQL，具体边界见 [DEVELOPMENT_CONVENTIONS.md](./DEVELOPMENT_CONVENTIONS.md) 的“MyBatis SQL 存放规则”。
- 每个核心系统只能访问自己的数据库，禁止跨库查询、跨库写入或共享表。
- 更新状态时携带期望旧状态或版本号；更新行数不为预期值时抛出包含上下文的异常。
- `dataobject` 中的数据库映射对象直接使用业务实体名，不追加 `DO` 后缀。它可以在 Owner 业务 JAR 内部使用，但不得作为 HTTP API 或跨 Maven 模块业务接口对象返回。
- 表、字段、索引、手工 DDL/DML 与 DataObject/Mapper 的设计必须和项目 PDManer 模型同步；详细规则见 `DATABASE_DESIGN_GUIDE.md`。

### 5.5 外部集成

外部 Provider、RPC、HTTP 和第三方 SDK 必须封装在平级 `integration` 包，不能放入表示入站触发的 `ext`：

- 业务层依赖本地 `*Client` 或端口接口，不直接依赖其他服务的 HTTP API 或第三方 SDK。
- `*ClientImpl` 可以依赖其他服务的 `*-api` JAR，但必须把远程 DTO 转换为本地领域语义。
- 实现层负责超时、错误映射、脱敏日志和外部状态转换。
- 外部原始状态先转换为本系统统一语义，再进入领域逻辑。
- 不把外部响应对象穿透到领域层。
- Vault 作为内部 HTTP 集成放在同一边界：本地端口方法显式表达当前 V1 的 `purpose`、Protect 数据类型/Token 模式或 Reveal Token；适配器通过部署网络限制的私有 HTTP 携带 OpenAPI 要求的 `X-Caller-Service`、幂等/请求 Header，并接受静态 Allowlist 只能防止误用而不能认证调用方的限制。V1 不接受 Reason、Resource 或字段选择，也不返回 Binding、Blind Index、Masked Value、Record Version 或 Metadata Hash。Reveal 结果不得写数据库、缓存、事件或日志；业务库只持久化获批的 opaque Token 和自身权威派生事实。

### 5.6 MQ 与业务事件

- 存在真实 MQ 入站能力时，`ext/mq` 负责验签/反序列化之后的事件接收、幂等入口和调用领域能力。
- 业务状态、状态历史和 `compensation_event` 在 Processor 本地事务内原子提交；MQ 发布只能由事务提交后的执行器完成，不得出现数据库回滚但消息已发送。
- 正常路径通过 `afterCommit` 唤醒本地 Dispatcher；XXL-Job 只做兜底扫描、分片或串行隔离，数据库 Claim/Lease/Version 仍是多实例互斥边界。
- 本地 `compensation_event` 重试复用相同 `event_key + event_type + sub_event_type`，Handler 从本库权威事实重建业务事件；它不保存来源版本、Schema、Canonical Envelope 或 Payload Hash。业务 MQ 信封是否包含领域来源版本、Schema 和 Payload Hash 由该业务事件契约独立决定。Broker Confirm 后本地成功记录允许删除；失败达到上限必须保留、告警并支持带 Actor/Reason/Ticket 的人工重开。
- MVP 不建立通用 Inbox/Outbox。消费者在本地业务事务中用业务唯一键、`source_event_key`、Payload Hash、来源版本条件更新和状态机支持重复、延迟和乱序消息，并在事务提交后 Ack。
- 事件 Payload 不得包含敏感明文、Vault 内部密文或完整 Vault Token，只能携带批准的必要业务字段、`file_id + content_hash` 和事实拥有方产生的必要非敏感派生事实。当前 V1 没有跨接收方 Copy 能力；需要敏感数据的消费者通过事实拥有方受控同步接口取得批准结果。
- Finance 的账务事实不可变；更正使用补偿事实和追加分录。

完整的表结构、执行状态、并发和测试要求见 `COMPENSATION_EVENT_GUIDE.md`。

## 6. 依赖方向

允许的主路径：

```text
简单用例：
ext/* 入站适配器
  → 一个 Domain Service / Domain
    → Repository interface / Integration port
      → RepositoryImpl / IntegrationImpl
        → Mapper / 外部系统

复杂用例：
ext/* 入站适配器
  → Processor
    → Domain Service / Domain
      → Repository interface / Integration port
        → RepositoryImpl / IntegrationImpl
          → Mapper / 外部系统
```

禁止：

- HTTP Controller 直接访问 Repository 或 Mapper；
- Processor 直接访问 Mapper 或另一个系统数据库；
- Repository 调用 Domain Service；
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
- 接口、公共类型和关键业务方法写清晰 JavaDoc。
- 常量和枚举使用业务语义名称，不使用 `getData`、`handle`、`process2` 等模糊名称。
- 金额使用整数最小货币单位或项目批准的 Money 类型，并显式携带 ISO 4217 币种；禁止浮点金额。
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
- [ ] API DTO 的枚举语义使用 `String code`；对外 Code 枚举定义位于 `*-api/api/enums`，内部枚举位于 `*-web/domain/enums`，均实现 `CodeEnum`，错误枚举按需实现 `CodeMessageEnum`，且没有复制同义 Code 目录。
- [ ] HTTP DTO 按业务能力放入独立 `*Models`，API 接口不内嵌 DTO；顶层类型使用 `*RequestBody` / `*ResponseBody`，GET 参数位置保持，嵌套模型和内部领域对象不机械追加后缀。
- [ ] Controller 实现 `@HttpExchange` API、没有重复 MVC Mapping；简单用例只委托一个 Domain Service，其他用例委托 Processor。
- [ ] 外部触发入口位于与 `service` 平级的 `ext/job`、`ext/dubbo`、`ext/rest` 或实际协议子包，且没有承载业务逻辑。
- [ ] 调用方通过本地 `*Client` 适配器隔离远程 API DTO，没有让远程 DTO进入领域层。
- [ ] 未新增 Dubbo 或 OpenFeign 契约，内部调用未绕行 Gateway。
- [ ] 未创建仅做一行转发的 Processor；省略 Processor 时满足少于 10 条有效语句及全部简单用例条件，且 Controller 没有持有事务或访问 Repository、Mapper、SDK。
- [ ] 实际存在的 Processor 未直接调用 Repository、Mapper 或第三方 SDK。
- [ ] 实际存在的 Processor 未依赖 HTTP 类型，也没有为单一实现创建空泛的抽象基类或公共上下文。
- [ ] Domain Service 与 Repository 使用业务语言命名。
- [ ] 只访问本系统数据库。
- [ ] DataObject 类型名没有 `DO` 后缀；仅在 Owner 业务 JAR 内使用，HTTP API 和跨 Maven 模块业务接口使用 Domain/Application Command/Result；数据库结构已与 PDManer、手工 DDL/DML、Mapper 和 Repository 保持一致。
- [ ] 多业务模块默认没有直接 Maven 依赖；每一项直接依赖都已证明是稳定、可复用的 Owner 窄业务接口及 Domain/Application 模型，并保持长期单向、无环且不承担跨模块用例编排。
- [ ] 循环风险、多 Owner 命令或跨 Owner 写事务由 `web` Processor 使用一个本地 `TransactionTemplate` 编排，模块没有依赖对方的 `*Impl` 或持久化对象。
- [ ] 事务覆盖本地原子操作，事件在提交后可靠发布。
- [ ] 需要跨实例排他协调时，`@ResourceLock` 位于非 `final` Spring Bean 的公开 Processor 具体实现方法并由 Proxy 可达，包住完整 `TransactionTemplate`；所有资源在进入数据库事务前已知并按确定顺序获取，没有在接口、Controller、Repository、Mapper、private/static/final 方法或同类自调用上依赖 AOP。
- [ ] Redis-first 协调没有取代幂等、Expected State/Version CAS、唯一索引或业务不变量需要的数据库最终保护；资源锁声明、配置和代理入口通过启动校验与架构测试。
- [ ] 状态更新校验旧状态/版本并处理并发冲突。
- [ ] 异常信息包含必要业务上下文。
- [ ] 未预期异常交给共享全局处理器；局部 `catch` 具有实际恢复、清理、异步状态或协议转换职责，并保留原始 Cause。
- [ ] 没有为尚未批准的技术方案创建占位基础设施。
- [ ] 没有一行代理、空层或猜测性公共类；新增 Foundation 能力已有跨服务复用证据且职责单一。
- [ ] 已按 `UNIT_TEST_GUIDE.md` 补充适当层级的测试。
- [ ] 新增出站依赖使用已启动的仓库 `local-environment` 或有状态 Simulator，未通过方法级 Stub 绕过真实适配器。
