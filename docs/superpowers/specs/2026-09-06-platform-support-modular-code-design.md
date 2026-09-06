# Platform Support 模块化代码设计

- Decision status: Confirmed direction; written spec review pending
- Decision date: 2026-09-06
- Current implementation, verification and deployment: see [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- Superseded scope: this design supersedes only the “当前没有必要增加 Maven 模块” conclusion in [Platform Support 审查问题修复与重构](./2026-09-05-platform-support-review-refactoring-design.md); that document's behavior fixes and other conclusions remain valid
- Audit placement supersession: this design supersedes only §3 of [Platform Controller 注解式尽力审计设计](./2026-09-05-platform-controller-best-effort-audit-design.md) where it places all audit business types, Service, Repository, Mapper, write and query code in `platform-support-web`; all audit behavior, coverage, context, dispatch, transaction and failure-isolation rules in that design remain authoritative

本文定义 Platform Support 完整业务范围的 Maven 模块、63 张表的唯一 Owner、模块协作、事务、可靠执行、审计例外和渐进迁移边界。它不改变现行业务模型、数据库模型、HTTP Wire、部署单元或已确认的锁与审计语义，也不代表目标模块、未落库表或未实现能力已经完成。

## 1. 决策、范围与依据

### 1.1 决策摘要

Platform Support 继续是一个服务、一个数据库和一个 Spring Boot 部署单元，在现有 `api / web / test` 基础上增加四个业务能力 JAR：

1. `platform-support-access-control`：身份认证、Tenant IAM、API Credential、Gateway 与 Console 公共接入控制；
2. `platform-support-file-document`：文件上传、扫描、对象引用、消费者文档、版本与访问；
3. `platform-support-delivery`：Notification 与 Client Webhook 两种对外投递能力；
4. `platform-support-audit-security`：操作审计、完整性点、Security Event 与 Alert。

`platform-support-api`、`platform-support-web` 和 `platform-support-test` 保留。`t_command_idempotency` 与 `t_compensation_event` 是服务级可靠执行事实，统一由 `platform-support-web` 的服务基础设施负责，不成为第五个业务模块，也不在四个业务模块中复制。

四个业务模块是同一服务内部的编译期边界，不是微服务。模块化不增加独立进程、服务发现身份、数据库、DataSource、事务管理器、远程调用或分布式事务。

### 1.2 权威依据

- Maven 模块、分层、Owner、Service 形态和协作门禁以 [`JAVA_PROJECT_STRUCTURE.md`](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md) 与 [`DEVELOPMENT_CONVENTIONS.md`](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md) 为准。
- Platform 63 表业务事实、字段、关系和状态语义以 [`PLATFORM_SUPPORT_DATABASE.md`](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)、[57 表精简设计](./2026-09-01-platform-support-database-simplification-design.md)及[动态公共 API 控制面设计](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)为准。
- 人工身份、Realm、Channel 和 Tenant IAM 以[统一人工认证设计](./2026-08-30-platform-unified-human-authentication-design.md)为准。
- Operations HTTP 审计的尽力语义以[审计决策](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)和[注解式尽力审计设计](./2026-09-05-platform-controller-best-effort-audit-design.md)为准。
- 资源锁的选择与 Key 语义以[Redis 优先资源锁决策](../../../agent-docs/decisions/2026-09-05-redis-first-resource-lock.md)、[Foundation Resource Lock 设计](./2026-09-05-foundation-resource-lock-design.md)和[Platform 锁优化设计](./2026-09-05-platform-support-resource-lock-optimization-design.md)为准。
- 本地异步副作用与恢复以 [`COMPENSATION_EVENT_GUIDE.md`](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)为准。
- 当前已经存在的工程、表、Java 能力、验证和部署状态只以 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)为准。

本设计只把上述权威映射为代码模块，不另建竞争性的业务或基础设施规范。

### 1.3 当前事实与目标状态

完整逻辑模型为 63 表；当前手工 SQL 与 Java 只覆盖已批准的 22 表切片。当前 Reactor 只有 `platform-support-api`、`platform-support-web`、`platform-support-test`，现有 Operations Authentication、IAM、Gateway Target/Route 控制面、PLT-I20 Snapshot、PLT-I22 JWKS、Operations Audit 和 Resource Lock 代码主要集中在 `web`；Console Resource 管理仍不是当前实现。

因此本文的四业务模块是完整 Platform 范围的目标边界，不是当前实现声明。迁移必须按真实纵向用例进行；尚无代码、SQL 和消费者的 File/Document、Notification、Webhook 或完整 Security Alert 能力不得为了目录对称生成空类、空接口或空 Maven 模块。

### 1.4 评估过的方案

| 方案 | 结论 | 原因 |
|---|---|---|
| 继续只使用 `api + web + test`，全部业务在 Web 内按包组织 | 不作为最终目标 | 当前切片可工作，但完整 63 表范围难以持续用包约定表达唯一表 Owner、持久化可见性和依赖方向 |
| 六个业务 JAR：Identity、Gateway、File/Document、Notification、Webhook、Audit/Security | 不采用 | Identity 与公共接入控制存在稳定授权关系；Notification 与 Webhook共享投递治理；六个边界会增加过多接口、POM 和编排成本 |
| 四个业务 JAR：Access Control、File/Document、Delivery、Audit/Security | 采用 | 每个模块都有明确业务能力与唯一表 Owner，数量符合初始 3–5 个能力模块的项目门禁，并保留必要的模块内子能力边界 |
| 三个业务 JAR：Access、Content/Delivery、Audit | 不采用 | File/Document 与 Delivery 合并后会形成 29 表模块，把扫描、对象存储、渲染和外部发送两类明显不同的失败与恢复模型混在一起 |
| 按 Controller、Service、Repository、Mapper 横向拆分 | 禁止 | 技术层拆分破坏业务内聚，让一个业务变化跨越多个 Maven 模块，且不能表达表 Owner |

## 2. 目标 Reactor 与依赖方向

### 2.1 稳态工程结构

```text
platform-support-service/
├── pom.xml
├── platform-support-api/
├── platform-support-access-control/
├── platform-support-file-document/
├── platform-support-delivery/
├── platform-support-audit-security/
├── platform-support-web/
└── platform-support-test/
```

| 模块 | 唯一职责 | 发布与运行 |
|---|---|---|
| `platform-support-service` | Reactor 聚合、Parent/BOM、插件、依赖与统一质量门禁 | `pom`，不运行 |
| `platform-support-api` | 内部 HTTP `@HttpExchange`、稳定传输 DTO 与契约 Code | 可发布 JAR，不运行 |
| `platform-support-access-control` | Access Control 领域规则、业务 Service、DO/Mapper/Repository 及 24 表访问 | 普通业务 JAR，不独立运行 |
| `platform-support-file-document` | File/Document 领域规则、业务 Service、DO/Mapper/Repository 及 12 表访问 | 普通业务 JAR，不独立运行 |
| `platform-support-delivery` | Notification/Webhook 领域规则、业务 Service、DO/Mapper/Repository 及 17 表访问 | 普通业务 JAR，不独立运行 |
| `platform-support-audit-security` | Audit/Security 领域规则、查询与写入能力、DO/Mapper/Repository 及 8 表访问 | 普通业务 JAR，不独立运行 |
| `platform-support-web` | 唯一启动入口、Controller、Processor、协议适配、跨模块编排、资源锁和服务级可靠执行 | 唯一部署单元 |
| `platform-support-test` | `auto/manual`、真实本地链路、Simulator、架构和模块边界测试 | 不发布、不部署 |

### 2.2 默认依赖图

```text
其他 Java 消费方 ───────────────────────────────→ platform-support-api

platform-support-test ──────────────────────────→ platform-support-web
                                                     │
                                                     ├─→ platform-support-api
                                                     ├─→ platform-support-access-control
                                                     ├─→ platform-support-file-document
                                                     ├─→ platform-support-delivery
                                                     └─→ platform-support-audit-security

platform-support-access-control ─────────────────────→ platform-support-audit-security
                                                        （唯一获准的窄事实采集依赖）
```

四个业务模块默认互不依赖，均不得依赖 `web` 或 `test`。跨模块用例由 `web` Processor 调用各 Owner 发布的窄业务接口。

当前 Operations 审计需要 Access Control 业务现场贡献精确的身份、拒绝原因和前后快照。对此允许一个明确、单向、无环的例外：

```text
platform-support-access-control
  └─→ platform-support-audit-security 发布的 OperationsAuditService 与不可变 Fact 类型
```

- `OperationsAuditService` 就是既有低侵入合同在模块边界上的 Fact Sink，不另建第二套 API；它只保留 `identify(Supplier<Identity>)`、`target(Supplier<Target>)`、`record(Supplier<? extends OperationsAuditFact>)` 和 `suppressReplay()` 四个语义入口；
- Supplier 只在 Web 已建立的 Controller 调用级审计 Scope 内立即求值；无 Scope 时不求值，异步队列只接收已经冻结的不可变值；
- Access Control 只依赖上述接口和业务动作所需的 typed Fact/Identity/Target 值对象；Processor 不持有该接口或 Audit Context；
- `OperationsAuditService` 的调用级上下文实现、`@Audit` 切面和异步分发仍在 `web`；
- Access Control 不依赖审计写入 Service、Repository、Mapper、DataObject 或队列实现；
- `audit-security` 不反向依赖 Access Control。审计查询的操作者认证和授权由 `web` 先调用 Access Control，再调用 Audit Query；
- 后续其他能力确有获批审计消费者时，可以遵守同一单向端口模式，但不能因此形成通用事件总线或强制每个方法审计。

除此例外，若未来提出业务模块直连，必须重新证明被调用能力稳定、接口足够窄、依赖长期单向无环且不承担跨模块用例编排；否则继续由 `web` 编排。

### 2.3 明确禁止的依赖方式

- 不创建 `platform-support-common`、`platform-support-domain`、`platform-support-dal`、`platform-support-messaging` 等技术横层 JAR；
- 不把内部 Domain、DataObject、Mapper、Repository 或业务实现放入 `platform-support-api` 解决循环依赖；
- 不注入其他模块的 Mapper/Repository，不复制 SQL，不建立跨模块 JOIN、View、存储过程或 `JdbcTemplate` 绕过 Owner；
- 不依赖其他模块的 `*Impl`、未发布 Domain Service、Spring 内部 Bean 或持久化类型；
- 不让 Maven 模块间通过 HTTP、MQ 或序列化 DTO 通信；它们属于同一进程中的普通 Java 调用；
- 不把模块化解释为未来可直接拆微服务。若真拆服务，必须重新设计数据库、远程契约、幂等、顺序、失败恢复和迁移。

## 3. 63 张表的唯一 Owner

### 3.1 Access Control：24 表

`platform-support-access-control` 合并“身份、授权与接入”18 表和“公共 API 控制面”6 表。两组共同回答外部主体、凭据、权限、Route 与 Console Resource 是否允许接入，但在模块内仍保持独立子能力、Service 与 Mapper，不创建覆盖 24 表的巨型 Directory 或通用 CRUD。

| 子能力 | 表数 | 唯一持久化 Owner |
|---|---:|---|
| Account / Authentication / Session | 9 | `t_user_account`、`t_password_credential`、`t_federated_identity`、`t_authentication_challenge`、`t_auth_session`、`t_refresh_token_generation`、`t_jwt_signing_key`、`t_email_otp_challenge`、`t_email_otp_attempt` |
| Tenant IAM | 7 | `t_access_tenant`、`t_tenant_member`、`t_tenant_invitation`、`t_role`、`t_permission`、`t_tenant_member_role`、`t_role_permission` |
| Client API Credential | 2 | `t_api_credential`、`t_api_credential_scope` |
| Gateway / Console Control Plane | 6 | `t_gateway_target`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_console_resource` |

计数为 9 + 7 + 2 + 6 = 24。`t_access_tenant` 是 Authentication、Credential 和 IAM 共用的本地 Tenant 投影，物理 Owner 只属于 Tenant IAM 子能力；其他子能力只能经该模块内部的窄查询能力使用，不得复制 Mapper。

Identity 与 Gateway Control Plane 放在同一 Maven JAR 的理由是稳定的授权关系，而不是允许任意跨表访问：

- Permission/Scope 与 Route、Console Resource 的绑定在同一 Access Control 边界验证；
- Gateway Snapshot 必须组合 Route、Guard 和相应授权元数据；
- Operations 管理 Gateway 配置时必须使用相同 Account/Tenant/IAM 权威；
- `authentication`、`tenantiam`、`credential`、`gatewayconfiguration`、`consoleresource` 仍各自拥有 Service、Repository 和 Mapper；现有混合 IAM、Gateway、幂等与审计 SQL 的大 Mapper 必须拆开。

Snapshot 中只包含 Route 执行所需的批准授权配置元数据。Gateway 不执行 PLT-I02 或核心资源授权：Human Route 进入对应 BFF 后由 BFF 调用 PLT-I02；API Key 由 Gateway 调用 PLT-I03 得到可信 Credential/Scope 结果，再与 Route 要求的 Scope 比较。把这些配置放进同一 Maven JAR 不会把业务授权决策转移给 Gateway。

### 3.2 File / Document：12 表

`platform-support-file-document` 独占：

`t_file_upload`、`t_file_scan_policy`、`t_file_scan_requirement`、`t_file_object`、`t_file_owner_ref`、`t_file_scan_result`、`t_document_template`、`t_document_template_translation`、`t_consumer_document`、`t_document_version`、`t_document_delivery`、`t_document_access_grant`。

模块内保留 `file` 与 `document` 两个子能力。它们放在一起，是因为 Document Version、Delivery 和 Access Grant 必须引用已经扫描、内容完整且可访问的 File Object；这种关系是同一内容生命周期，不等同于 Notification/Webhook 的外部发送生命周期。

### 3.3 Delivery：17 表

`platform-support-delivery` 包含两个彼此独立但共享投递治理模式的子能力：

| 子能力 | 表数 | 唯一持久化 Owner |
|---|---:|---|
| Notification | 9 | `t_notification_provider`、`t_notification_request`、`t_notification_delivery`、`t_notification_attempt`、`t_notification_route`、`t_notification_provider_callback`、`t_notification_suppression`、`t_notification_template`、`t_notify_template_translation` |
| Client Webhook | 8 | `t_webhook_endpoint`、`t_webhook_endpoint_revision`、`t_webhook_endpoint_verification`、`t_webhook_signing_secret`、`t_webhook_subscription`、`t_webhook_event`、`t_webhook_delivery`、`t_webhook_attempt` |

计数为 9 + 8 = 17。二者共同拥有 Tenant-owned 配置、请求/事件、Delivery、Attempt、外部回调或响应、Claim/Lease/Fencing、超时与 `UNKNOWN` 恢复等投递问题，因此不为每种 Channel 单建 Maven JAR。

合并不意味着统一业务模型：必须保留 `notification` 与 `webhook` 包、状态机、签名、Provider 回调、抑制、订阅和重放语义；禁止抽象 `GenericDelivery`、通用 Attempt 表或把两套生命周期压成一个枚举。

### 3.4 Audit / Security：8 表

`platform-support-audit-security` 独占：

`t_audit_chain`、`t_audit_event`、`t_audit_event_attr`、`t_audit_integrity_point`、`t_security_event`、`t_security_alert`、`t_security_alert_event`、`t_security_alert_action`。

模块内保留 `audit` 与 `security` 子能力。它负责 Hash Chain、事件与属性原子追加、完整性点、Security Event/Alert 生命周期和审计查询，不负责 HTTP 入口、操作者授权或业务事务。

### 3.5 Web 服务级可靠性：2 表

`platform-support-web` 的服务基础设施独占：

- `t_command_idempotency`：整个 Platform 服务统一的命令重放与请求哈希保护；
- `t_compensation_event`：本地事务提交后的可靠副作用唤醒与恢复。

这两张表不形成业务域。模块业务状态与可靠执行登记可以参加同一个本地事务，但业务模块不得直接拥有或复制它们的 DO/Mapper/Repository。现有混在 Operations Directory Mapper 内的命令幂等 SQL 应迁移到 Web 服务级可靠性 Repository。

总计为 24 + 12 + 17 + 8 + 2 = 63，无遗漏、无重复。

## 4. 模块内部结构与公开边界

### 4.1 业务模块分层

每个业务 JAR 只为真实代码创建适用目录，并继续遵守项目分层：

```text
<business-module>/
└── src/main/java/<base-package>/
    ├── domain/<sub-capability>/
    ├── domainservice/
    │   ├── service/<sub-capability>/
    │   ├── repository/<sub-capability>/
    │   ├── process/<sub-capability>/
    │   └── convert/<sub-capability>/
    ├── integration/<external-owner>/
    └── core/dal/
        ├── dataobject/<sub-capability>/
        ├── mapper/<sub-capability>/
        └── configuration/
```

- 业务 Service 与 Domain Service 使用 `interface + *Impl`；Repository 使用 `interface + *RepositoryImpl`；
- 模块对外公开的接口表达业务动作或业务查询，例如 `CredentialValidationQuery`、`DocumentVersionCommand`、`WebhookDeliveryCommand`，不暴露 `saveAny`、`updateStatus`、`queryTable`；
- 跨模块参数和结果使用 Owner 定义的 Domain/Application Command/Result；不返回 API Request/Response、DataObject 或第三方 SDK 类型；
- DataObject 只在其 Owner JAR 内可用。Controller 和其他模块不得把它当传输或共享模型；
- 一个 Maven JAR 内仍按子能力维护 Mapper/Repository 边界，不因为物理模块合并而恢复巨型 Directory、Mapper 或 Service。

### 4.2 各模块建议子包

| 模块 | 业务子包 |
|---|---|
| `access-control` | `authentication`、`tenantiam`、`credential`、`gatewayconfiguration`、`consoleresource` |
| `file-document` | `file`、`document` |
| `delivery` | `notification`、`webhook` |
| `audit-security` | `audit`、`security` |

这些是模块内业务归属，不是新的 Maven 模块，也不要求预建没有真实类的目录。

### 4.3 API JAR

`platform-support-api` 继续按对外能力组织 `@HttpExchange`、HTTP DTO 和稳定 Code。它只服务真实的 Gateway、BFF 或其他 Java 服务消费者：

- 不因内部 Maven 模块拆分而增加内部接口；
- 不用 API DTO 编排同进程业务模块；
- 不暴露 DO、Mapper、Repository、内部 Domain、Vault 值或第三方 SDK 类型；
- 外部 Gateway Route/OpenAPI 与内部 Java HTTP 契约保持各自的路径、权限和 DTO 边界。

### 4.4 Web 组合根

`platform-support-web` 保留：

```text
Application.java
configuration/
ext/
├── rest/
├── mq/
└── job/
service/processor/
core/reliability/
audit/context-and-aspect/
integration/composition-adapters/
```

Controller 只做协议适配和声明入口关注点。Processor 负责认证/授权复核、Reason、命令幂等、资源锁、TransactionTemplate、跨模块步骤与返回装配。业务不变量、状态机和表访问留在 Owner 模块。

## 5. 事务、锁与连接池

### 5.1 同库单事务

四个业务模块共享同一个 Platform 主 DataSource 和主事务管理器。模块拆分不创建每模块连接池、Schema 或事务管理器。同一进程、同一数据库内需要强一致的跨模块写入仍由 Web Processor 的一个 `TransactionTemplate` 完成：

```text
Controller
  └─ ResourceLock（确有并发不变量时）
      └─ Web Processor
          └─ TransactionTemplate
              ├─ Owner A business command
              ├─ Owner B business command
              ├─ command idempotency
              └─ compensation registration（存在提交后副作用时）
          └─ commit
      └─ release lock
```

参与用例的业务模块方法加入调用方已有事务，不得自行 `REQUIRES_NEW`、提前提交或切换线程。Maven Owner 限制谁能访问表，不妨碍多个 Owner 的合法业务接口参加同一个本地事务。

### 5.2 资源锁

- `@ResourceLock` 放在 Web 的用例入口/Processor 边界，由真实资源 Key 表达并发不变量；
- 优先评估 Redis 跨实例协调，再独立评估 CAS 和必要的局部 MySQL 行保护；三者不是互相替代的固定套餐；
- 不按 Tenant、操作者或整个模块做粗粒度锁，除非它们本身就是被保护资源；
- 获取锁后必须在事务内重新读取并验证权威状态；
- MySQL `FOR UPDATE` 只保留给余额、唯一活动槽、不可重复终态等确有数据库行串行化必要的敏感场景；不能因为模块化或“更谨慎”扩大悲观锁范围；
- 多资源写入使用确定顺序，锁等待、Lease、Fencing 和 Key 表达式启动校验继续服从现有 Resource Lock 专项设计。

### 5.3 审计的专用连接例外

业务模块共用一个主连接池。唯一已确认的例外是 Operations HTTP 尽力审计写入：它使用同一 Platform 数据库上的小型专用审计连接池和独立审计事务，使审计写入不消耗主业务连接池，也不有意改变主业务语义。同一 JVM 和 MySQL 的锁、CPU、IO、网络与故障仍可能造成有限物理竞争；这里不承诺基础设施层面的零影响。

该例外由现有审计专项决定拥有，本模块设计不增加第二套数据库、不为其他模块创建专用 DataSource，也不把审计的独立事务推广到可靠业务事实。`audit-security` 自身不声明 DataSource、Transaction Manager 或 SqlSession：`web/configuration` 复用主库的安全连接属性，创建专用 Audit Write DataSource、Transaction Manager、SqlSessionFactory/Template，并装配 Audit Writer；Audit Mapper/XML/SQL 只在 `audit-security` Owner 保留一份。业务查询 Mapper 仍绑定主数据源。Audit Chain/Event/Attr/Security Event 在专用写事务内部必须原子提交；审计池不可用不得使主应用失去 Ready。

## 6. 审计低侵入协作

模块拆分后继续保持 Controller 注解式尽力审计：

1. `web` Controller 实现方法使用 `@Audit` 声明动作和默认资源类型；
2. `web` Aspect 创建调用级上下文并观察真实业务/事务结果；
3. Access Control 等业务代码只在已经掌握事实的位置调用窄 `OperationsAuditService` 四入口，使用 Supplier 提供类型化 Identity、Target、拒绝原因或 before/after Fact；无审计 Scope 时 Supplier 不求值；
4. `web` 的有界内存分发器在提交结果确定后非阻塞入队；
5. `audit-security` 的写入能力使用专用连接和独立事务追加审计与关联安全事实。

业务 Service 不包围 try/catch 写审计、不直接调用审计 Repository、不等待落库，也不通过 MQ、`t_compensation_event` 或自动重试补写。队列满、进程退出、序列化失败或审计数据库异常允许永久丢失，均不得改变主业务返回、原始异常、提交、回滚、认证锁定、Session 撤销、资源 CAS 或幂等结果。

Audit Query 是跨模块读取：Web 先调用 Access Control 完成操作者认证和权限判断，再调用 Audit Query。`audit-security` 不读取 Account、Role、Permission 或 Console Resource 表，也不反向依赖 Access Control。

本节只调整原审计设计中“所有能力留在 Web JAR”的物理放置：`@Audit`/Aspect/上下文/分发属于 Web，审计业务类型、写入、查询和 8 表持久化属于 `audit-security`。原设计的覆盖范围、事实采集、事务结果判定、允许丢失和故障隔离语义全部保持不变。

## 7. 可靠执行与外部副作用

### 7.1 服务级可靠执行

业务状态变更和必要的 `t_compensation_event` 在同一个主业务事务提交。Handler 只在提交后执行，并按 Event Key 回查对应 Owner 模块的权威状态来重建最小请求或消息；可从本库恢复的完整上下文、Vault Token、密钥、敏感正文和凭据不得复制到 Payload。

`t_compensation_event` 是本服务可靠副作用唤醒器，不是模块间消息总线、通用 Workflow、业务历史、Inbox 或中央 Outbox。XXL-Job 只作为丢失唤醒和分片恢复机制。MQ 只用于跨服务事实；同一 Platform 进程内的 Maven 模块不通过 MQ 协作。

审计尽力写入与可靠执行具有不同失败承诺：审计明确接受丢失，因此不得登记 Compensation Event；Notification、Webhook、文件扫描、对象写入、文档渲染和获批 MQ 发布需要恢复时，必须使用服务级可靠执行。

### 7.2 外部调用规则

- 任何尚未提交的本地业务事务都不得执行不可逆的 MQ Publish、Provider/Webhook HTTP、对象存储、扫描或 Renderer 副作用；尤其不得在持有数据库行锁时调用外部系统；
- 本地事务先冻结业务事实和需要的可靠任务，提交后再执行单一外部副作用；
- 超时或连接中断不能自动解释为外部失败，领域存在 `UNKNOWN` 时按其查询/恢复协议处理；
- Claim/Lease/Fencing 保护多实例执行，业务唯一键、状态条件更新和远端幂等键仍是重复执行的最后防线；
- 外部返回写回只由其业务 Owner 完成，Web 和其他模块不得直接更新其 Attempt/Delivery 表。

## 8. 关键跨模块用例

| 用例 | Web 编排与本地边界 | 禁止做法 |
|---|---|---|
| PLT-I13 Integration Readiness | `ReadinessProcessor` 只为 CLIENT Tenant 在一个只读一致性事务中读取 ACTIVE Owner/Security、Credential/所需 ACTIVE Scope；仅当固定 Integration Mode 要求 Webhook 时，再读取同 Tenant 的 VERIFIED/ACTIVE Endpoint Revision 与匹配 ACTIVE Subscription。缺少必需配置为 `NOT_READY`，Owner/Security 限制为 `RESTRICTED`；歧义、超时、数据库或依赖失败返回接口错误且不携带 `status` | 不建 Readiness 表、Root、Hash 或变更事件；不让 Access Control 读 Webhook 表；不把技术失败映射为 `NOT_READY` |
| API Credential 管理 | Access Control 在一个命令事务内校验 Tenant、Credential、Scope、状态和版本；Web 统一处理该命令的资源锁与命令幂等 | 不把 Secret/Verifier 放入审计、MQ 或补偿 Payload |
| PLT-I03 API Credential 验证 | Access Control 在一个有界只读一致查询中关联 Credential、同一 ACTIVE CLIENT Tenant 与当前有效 ACTIVE Scope，并验证 PHC；返回可信 Tenant/Credential/Scope 结果 | 不写表、不登记命令幂等；Gateway 不直接读取 Platform 数据库 |
| 业务 Email OTP | Access Control 拥有 Challenge/Attempt；Web 在需要发送时同事务调用 Delivery 创建 Notification Request 并登记可靠任务 | 不由 Access Control 调 Notification Repository；不把发送成功当作 OTP 业务验证成功 |
| File/Document 交付 Notification | Web 在一个本地事务中调用 File/Document 与 Notification 的窄命令；Document Owner 保存稳定 Notification Delivery 引用 | 不跨模块 JOIN/SQL；不由 Notification 读取 Document/File 表；外部发送只在提交后 |
| Audit Integrity Point 文件 | File/Document 返回稳定 File ID、Object Ref/Content Hash；Web 将批准的稳定结果交给 Audit/Security 创建完整性点 | Audit 不访问 File Mapper，不复制受限文件内容，不用物理 FK 代替业务校验 |
| Gateway Route/Permission/Console/Snapshot | 全部在 Access Control Maven 模块，但分别由 Gateway、IAM、Credential、Console 子能力协作；Web 负责 HTTP、锁、事务和幂等 | 不保留混合所有表的巨型 Mapper；Platform 不保存 Gateway Runtime Bucket/Counter/Lease |
| 外部业务事实触发 Webhook | Web MQ Listener 在本地事务中校验事实、幂等创建 Webhook Event/Delivery 并原子登记发送任务；提交后才手动 Ack，事务失败不 Ack；需要顺序的契约只接受允许的更高来源版本。Delivery Owner 管理 Attempt 与结果 | 不轮询其他服务数据库；不在 MQ 消费事务内同步调用外部 Endpoint |
| Notification/Webhook 外发 | Delivery Owner 冻结投递事实；提交后每次已 Claim 的 Handler 只执行一个明确外部副作用；整体按至少一次语义设计，使用稳定幂等键，未知结果先查询或进入领域恢复 | Notification 与 Webhook 不共享通用状态机或通用表；`UNKNOWN` 不猜成失败 |
| Audit Query | Web 使用 Access Control 授权后调用 Audit Query，返回批准的脱敏投影 | Audit 不反向依赖 IAM；Controller 不直接查 Mapper |

强一致的本库事实不因跨 Maven 模块改为最终一致；只有真实外部副作用和跨服务事实才使用 Compensation Event/MQ。

## 9. 渐进迁移策略

### 9.1 不预建空模块

迁移按真实代码与批准切片进行：

1. 先以本文和现有 Web 架构测试建立模块 Owner 清单与依赖规则，再移动代码；任何新的 Reactor `<module>`、POM 和扫描配置必须与该模块第一批真实生产类、Mapper/SQL、Repository 和测试在同一迁移变更中出现；
2. 当前已有非空代码的 Access Control 与 Audit/Security 可以作为第一阶段提取对象；
3. `platform-support-file-document` 只在第一个 File/Document 纵向切片同时带来真实业务类、SQL/Mapper/Repository 和测试时创建；
4. `platform-support-delivery` 只在第一个 Notification 或 Webhook 纵向切片进入实现时创建；先出现其中一个子能力不要求为另一个生成占位类；
5. 每次提取一个 Owner，先保持 HTTP、状态机、事务、锁、SQL 和响应行为不变，再处理下一 Owner；模块迁移与业务功能变更不得混成不可验证的大改。

当前 `api / web / test` Foundation 设计仍是已实施历史形态和阶段证据，但不再是完整 63 表的最终模块结论。

### 9.2 第一阶段建议切面

第一阶段只迁移当前手工 SQL 已存在的 22 表：

- Access Control 接收 17 表：`t_user_account`、`t_password_credential`、`t_access_tenant`、`t_auth_session`、`t_refresh_token_generation`、`t_jwt_signing_key`、`t_tenant_member`、`t_role`、`t_permission`、`t_tenant_member_role`、`t_role_permission`、`t_gateway_target`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_console_resource`，以及对应已实现的 Password/Authentication、Tenant IAM、Gateway Target/Route、Snapshot/JWKS 代码；这不包含 `t_federated_identity`、`t_authentication_challenge`、Email OTP 或 API Credential/Scope；
- Audit/Security 接收 4 表：`t_audit_chain`、`t_audit_event`、`t_audit_event_attr`、`t_security_event`，以及对应 DO/Mapper/Repository/业务接口；
- Web 保留 Controller、Processor、资源锁、事务画像、审计 Aspect/Context/Dispatcher、HTTP 配置和 1 张 `t_command_idempotency`；
- 拆分现有混合 IAM/Gateway/Audit/Idempotency 的 Mapper 与 Directory Service，确保每张表只有一个 Owner；
- 更新 `@MapperScan`、MyBatis XML 资源扫描、Jacoco 汇总、Spring Boot 测试装配、POM dependency management 和现有三模块硬编码的边界测试。

这一步不创建 `t_compensation_event` 及其余 40 张未落库业务表，不实现 CUSTOMER Authentication、API Credential、File/Document、Notification、Webhook、完整 Security Alert、MQ 或生产外部集成。

### 9.3 后续纵向切片

每个后续切片必须同时闭合：业务设计、表 Owner、PDManer、手工 Schema SQL、初始化 DML、DO/Mapper/Repository、公开业务接口、Web 编排、资源锁/CAS/事务、外部 Simulator、自动测试和文档状态。不能先批量生成 63 表 CRUD 再寻找消费者。

## 10. 架构与验收门禁

### 10.1 编译期和源码门禁

至少验证：

- Reactor 清单和 Maven DAG 与本文一致，无循环依赖；
- 唯一业务模块直连边是 Access Control 到 Audit/Security 的四入口事实采集合同；不存在其他 capability-to-capability 依赖；
- Web 是唯一 Spring Boot `main` 和唯一可执行 JAR；业务模块不含启动类、Server 配置或独立 DataSource；
- 每张表、DO、Mapper XML、Mapper 和 Repository 只出现在唯一 Owner；
- 其他模块不能依赖 Owner 的 DO、Mapper、Repository、`*Impl`、未发布 Domain Service 或 SQL；
- `platform-support-api` 不含内部 Domain/DAL 类型；
- 业务 Service、Domain Service、Repository 的接口/实现形态符合项目规范；
- 禁止跨模块 JOIN、数据库 View/存储过程和直接 JDBC 越界；
- `@MapperScan` 与 XML 扫描覆盖各业务模块，且不会重复注册或漏加载；
- Jacoco、Surefire/Failsafe、架构测试和构建脚本不再硬编码旧三模块布局。

### 10.2 行为门禁

至少覆盖：

- 跨模块写事务任一步失败时全部本地事实、命令幂等和 Compensation Event 一起回滚；
- 同 Key 同请求重放原结果，同 Key 异载荷冲突；
- Resource Lock Key 表达式启动即校验，锁后复验权威状态，Lease/Fencing 和多资源顺序正确；
- Redis 协调、CAS 与 MySQL 局部保护分别验证，不能以一个锁的成功掩盖另一个不变量缺失；
- Notification/Webhook/File 外部副作用只在 commit 后发生，after-commit 唤醒丢失可由 Job 恢复；
- Claim 前递增尝试次数；最后允许尝试在副作用后崩溃时，接管者必须在调用 Handler 前转 `FAILED`；
- Handler timeout、线程池拒绝、最大次数、失败安全摘要、人工重开、不可变 Policy Snapshot 和 Event Type 到唯一 Handler 的启动期映射；
- Delivery 多实例 Claim、Lease 接管、回调重复、超时与 `UNKNOWN` 分支；
- MQ 重复/乱序消费、source event 唯一性、事务提交后手动 Ack；Publish 只有 Broker Confirm 后成功，Confirm 后删除前崩溃允许按至少一次语义重复发布；
- Integration Readiness 技术失败 Fail-Closed 且不产生持久化 Readiness；
- Platform PLT-I20 全量构建且非法候选整体 Fail-Closed、不返回部分或 Last-Known Snapshot；Gateway 消费方自行保留 Last Valid，并与 Approved Target Registry 双校验；
- 审计队列满、专用连接耗尽、SQL/事务失败均不改变主业务结果，Audit Chain/Event/Attr/Security Event 内部仍原子；
- Audit Query 授权由 Access Control 执行，Audit 模块没有越权表访问。

### 10.3 完成验证

实施完成前按变更风险运行 Platform 全量 Maven 门禁、依赖图/架构测试、本地 MySQL/Redis/MQ 行为测试、Mapper/SQL 映射检查、`git diff --check`，以及命中需求目录时的 `npm --prefix requirements-site test`。数据库切片仍需 PDManer Open/Validate/Diagram 人工门禁。

局部模块编译、单元测试或模型生成成功不能表述为 63 表实现、Gateway/BFF 接入、外部 Provider 可用或生产部署完成；这些状态只更新到 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 11. 非目标与后续动作

本设计不授权：

- 拆出新的微服务、数据库、Schema、连接池或部署单元；
- 新增、迁移或初始化尚未批准实施的 41 张表；
- 改变公开 HTTP、内部 `@HttpExchange`、Gateway Snapshot、JWT/JWKS 或 API Credential Wire；
- 改变 Operations 尽力审计的覆盖、允许丢失、专用连接和独立事务语义；
- 改变 Resource Lock 的 Redis 优先、真实资源 Key、锁后复验和 MySQL 谨慎使用原则；
- 创建通用 Delivery、通用 Workflow、Inbox/Outbox、中央 Compensation 数据库或模块间 MQ；
- 因模块化重命名业务状态、合并生命周期或批量生成无消费者抽象。

本文完成书面评审后，下一步才是编写独立实施计划。实施计划必须按可验证的迁移阶段列出精确文件、移动顺序、失败回归、依赖门禁和回滚点；在用户明确要求前不创建分支、不提交、不推送、不部署。
