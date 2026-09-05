# Commerce 模块化代码设计

- Decision status: Confirmed
- Written spec review: Completed (2026-09-05)
- Decision date: 2026-09-05
- Current implementation, verification and deployment: see [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- Superseded sections: the fixed 14-table target and the assumption that an API JAR belongs to the immediate steady structure are superseded by [Commerce Client API Hosted Checkout V1](./2026-09-05-commerce-client-api-v1-design.md); the package-first modular-monolith decision remains current

本文记录 Commerce Java 工程的 Maven 边界、包级业务归属、协作方式和实施门禁。它不授权或代表 Java、SQL、物理数据库、PDManer、部署或生产能力已经实施；当前实现、验证与部署状态只以 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 和未来的 Commerce 仓库 README 为准。

## 1. 决策、范围与依据

### 1.1 决策摘要

Commerce 采用“基础构建模块分离、业务能力先在 Web 内按包分域”的模块化单体：

- 一个 Commerce 服务、一个数据库、一个 Spring Boot 部署单元和一个事务管理器；
- 当前获批结构为父工程、`paytech-commerce-web` 和 `paytech-commerce-test`；
- `paytech-commerce-api` 只有在首个真实内部 HTTP 契约进入实施时才创建，不建立空模块；
- Order、Checkout、Refund 暂不拆成独立 Maven 业务能力 JAR；
- 三类业务边界先通过 `paytech-commerce-web` 内的包、表访问规则和架构测试表达；
- 当真实代码证明单个 Web JAR 已难以表达职责，并同时通过全部拆分门禁后，才另行批准和提取能力 JAR。

2026-09-05 的后续用户授权允许在首个纵向业务切片前先实施一个窄化的应用基础工程里程碑。该里程碑只能创建父 Reactor、`paytech-commerce-web`、`paytech-commerce-test`、Spring Boot 启动入口、最小生产配置、健康/指标端点、Maven Wrapper、README 和对应自动门禁；不得创建空 `paytech-commerce-api`、业务 Controller/Service/Repository、数据库/SQL、外部集成、MQ/任务、业务配置或部署资源。它只证明工程可构建、可启动和基础边界可执行，不证明任何 Commerce 业务能力已经实现。

这里的 Maven 模块、模块内分层和微服务是三个不同概念。本设计不增加独立进程、服务发现身份、数据库、事务管理器或分布式事务。

### 1.2 权威依据

- Java Maven 模块、分层、API 和跨模块边界以 [`JAVA_PROJECT_STRUCTURE.md`](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md) 为唯一通用权威。
- 编码、事务、Service 形态和依赖习惯以 [`DEVELOPMENT_CONVENTIONS.md`](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md) 为准。
- Commerce 业务事实、15 表目标、不变量和状态机以 [`COMMERCE_DATABASE.md`](../../../agent-docs/database/COMMERCE_DATABASE.md) 及其当前专项设计为准。
- 面向 Client Server 的 Hosted Checkout V1 Wire、Route、幂等与外部包落位以 [Commerce Client API Hosted Checkout V1](./2026-09-05-commerce-client-api-v1-design.md) 为准。
- 外部入口、内部服务调用和部署边界以 [`APPLICATION_ARCHITECTURE.md`](../../../agent-docs/APPLICATION_ARCHITECTURE.md) 为准。
- 需求和跨系统时序以 [`requirements.ts`](../../../requirements-site/content/requirements.ts) 与 [`global-use-case-sequences.mjs`](../../../requirements-site/content/global-use-case-sequences.mjs) 为准。
- 本地可靠执行以 [`COMPENSATION_EVENT_GUIDE.md`](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md) 为准。

本设计只做上述通用规则到 Commerce 的具体映射，不另建一套竞争规范。

### 1.3 当前事实

当前工程、模型、验证与部署事实只读取 [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。本设计描述目标边界，不以任何历史快照推断实现状态。

### 1.4 评估过的方案

| 方案 | 结论 | 原因 |
|---|---|---|
| 所有契约、运行代码和测试放进一个 JAR | 不采用 | 混合可发布内部契约、唯一部署单元和不可发布测试；不符合核心服务的长期职责边界 |
| `api + web + test`，业务在 `web` 内按包分域 | 采用 | 与当前零 Java 实现相称；保留真实本地事务；不预建空业务 JAR；以后仍可渐进提取 |
| 立即增加 `order + checkout + refund` 三个业务 JAR | 暂缓 | 当前没有真实代码证明单个 Web JAR 已失控；多数核心用例跨越这些主题，立即拆分只会先增加接口和组合根编排 |
| 按单表、状态机或 Controller/Service/Repository 技术层细拆 | 禁止 | 破坏业务内聚并违反现行 Java 工程规范 |

PDManer Diagram Group、需求中的 `internalDomains` 和数据库表分组都是业务理解工具，不自动等于 Maven 模块。

## 2. 目标工程结构

### 2.1 稳态结构

```text
commerce-service/
├── pom.xml
├── docs/
│   └── database/
├── paytech-commerce-web/
└── paytech-commerce-test/
```

只有真实内部 Java HTTP 消费者出现时，才在独立决策后增加非空 `paytech-commerce-api`；外部 OpenAPI 固定为语言无关文件，不能触发该 JAR。

Git 仓库目录继续使用 `commerce-service`；父 POM 的 Artifact 使用 `paytech-commerce-service`，子模块目录与 Artifact 使用上图 `paytech-commerce-*` 名称，Maven `groupId` 使用公司级 `com.paytech` 前缀。

| 模块 | 职责 | 发布/运行 |
|---|---|---|
| `paytech-commerce-service` 父工程 | Reactor 聚合、Parent/BOM、插件和依赖管理 | `pom`，不运行 |
| `paytech-commerce-api` | 内部 Java 服务共享的 `@HttpExchange`、HTTP DTO 和稳定契约 Code | 可发布 JAR，不运行 |
| `paytech-commerce-web` | 唯一 Spring Boot 入口、业务实现、持久化、协议适配、跨能力编排和服务级可靠执行 | 唯一部署单元 |
| `paytech-commerce-test` | `auto/manual`、测试 DSL、外部 HTTP/MQ Simulator 和架构门禁 | 不发布、不部署 |

默认依赖方向为：

```text
paytech-commerce-test -> paytech-commerce-web -> paytech-commerce-api
其他 Java 服务 --------------------------------> paytech-commerce-api
```

没有真实内部 HTTP 契约的首个切片只创建父工程、`paytech-commerce-web` 和 `paytech-commerce-test`。当 COM-I04、COM-I09、COM-I10、COM-I13，或供真实 BFF 使用的 COM-I03、COM-I05、COM-I06、COM-I07、COM-I08 私有契约中第一个进入同一实施切片时，创建非空 `paytech-commerce-api`，随后启用完整依赖方向。不得为了目标目录对称预建空 JAR、空包或占位接口。

### 2.2 明确不创建的工程

本设计不创建：

- `checkout-service`、`client-api-service`、`refund-service` 或 MQ Worker 等新部署单元；
- `paytech-commerce-order`、`paytech-commerce-checkout`、`paytech-commerce-refund` 等业务能力 JAR；
- `paytech-commerce-common`、`paytech-commerce-domain`、`paytech-commerce-repository`、`paytech-commerce-messaging`、`paytech-commerce-vault` 或其他技术横层 JAR；
- Order Context、Acceptance、Network Evidence、Refund Position、Reversal 等单表或单状态机模块。

`paytech-commerce-api` 也不是独立服务。Gateway 暴露的 Client API 和 Hosted Checkout API 仍路由到 `paytech-commerce-web`。

## 3. HTTP 契约边界

### 3.1 内部 API JAR

`paytech-commerce-api` 只承载其他 Java 服务和 BFF 通过私有 HTTP 使用的契约。不能把一个 COM-I 编号整体判定为“外部”或“内部”：同一 Commerce 领域能力可以分别拥有 Client API 外部适配器和 BFF 私有适配器，但二者必须使用不同路径、权限和 DTO。

核心服务调用的内部契约包括：

- COM-I04：不可变 Order Context 查询，消费者包括 Payment、Risk 和 Finance；
- COM-I09：消费者凭证来源事实查询，消费者为 Platform Support；
- COM-I10：Payment Attempt 准备状态校验，消费者为 Payment；
- COM-I13：Payment Attempt 受限风险输入查询，消费者为 Payment。

BFF 私有调用至少包括 Client/Operations Console 实际使用的 COM-I03、COM-I05、COM-I06、COM-I07 和 COM-I08 能力。其中 COM-I08 只能由 Operations Console 经 `operations-console-bff` 调用 Commerce，Gateway 不得把 Operations 浏览器请求直接路由到核心服务。只有出现真实 BFF 消费者时才发布对应接口，不预建未使用契约。

API 按能力和调用边界定义小型 `*QueryApi` / `*CommandApi` 与对应 `*Models`，不创建覆盖全部 Commerce 接口的巨型 API。传输 DTO 不暴露 DataObject、Repository、内部聚合、Vault 明文或第三方 SDK 类型。调用方只在自己的 `integration` 适配器中依赖该 JAR，并转换为本方领域对象。

### 3.2 外部 HTTP

Gateway 直达 Commerce 的 Client API 和 Hosted Checkout 路由使用语言无关的 HTTP/OpenAPI 契约。它们包括实际暴露的 COM-I01、COM-I02、COM-I03、COM-I05、COM-I06、COM-I07、COM-I11 和 COM-I12 入口；具体 Route Channel 和 Scope 仍由入口架构与 Platform Route Catalog 决定。外部 Controller 位于 `paytech-commerce-web` 的 `ext/rest/external`。

Client/Operations 浏览器请求先到各自 BFF，再由 BFF 调用 `paytech-commerce-api` 发布的私有 Commerce 契约；Operations 的 COM-I08 不在 Commerce 创建 Gateway 直达的外部 Controller。COM-I03、COM-I05、COM-I06 和 COM-I07 同时存在 Client API 与 BFF 用途时，两个适配器复用同一 Processor/Domain 能力，但绝不复用路径、权限或传输 DTO。内部 Controller 位于 `ext/rest/internal` 并实现对应 `@HttpExchange` 接口。

外部契约与内部 API 必须保持不同路径、权限和契约包；不得因为 DTO 相似而直接公开 `paytech-commerce-api` 类型。

Gateway、BFF 和逻辑 Client API 只做所属入口职责，不拥有 Commerce 业务规则，也不编排 Commerce 到 Payment、CCT、Risk、Finance 或 Vault 的业务事务。

## 4. Web 内部结构与逻辑归属

### 4.1 相对包结构

具体 Java 基础包名不由本文批准；首次 Java 实施计划须依据技术规范单独确认。基础包以下使用现行分层，并只为真实代码创建目录：

```text
Application.java
configuration/
ext/
├── rest/
│   ├── external/
│   └── internal/
├── mq/
└── job/
service/
└── processor/
    ├── order/
    ├── checkout/
    └── refund/
integration/
├── cct/
├── risk/
├── finance/
├── payment/
├── vault/
└── mq/
domain/
├── order/
├── checkout/
└── refund/
domainservice/
├── service/
├── repository/
├── process/
└── convert/
core/
└── dal/
    ├── dataobject/
    ├── mapper/
    └── configuration/
```

Order、Checkout、Refund 是各层下面的业务子包，不是替代 `ext/service/integration/domain/domainservice/core` 的顶级横向架构。Controller 只做协议适配；复杂写入、幂等、跨系统或多步骤用例进入具体 Processor；业务规则进入 Domain Service；持久化只经 Repository 和 Mapper。

同一逻辑 Owner 名称必须贯穿相关层，例如 `domain/order`、`domainservice/service/order`、`domainservice/repository/order`、`core/dal/dataobject/order` 和 `core/dal/mapper/order`。`checkout` 内可以按真实代码继续使用 `session`、`consumer`、`legal`、`evidence` 子包，但这些子包不形成新的 Maven 模块或独立表访问边界。

一旦创建业务 Service 或 Domain Service，必须采用 `interface + *Impl`；Repository 使用 `interface + *RepositoryImpl`。Processor 和 Controller 保持具体类。没有业务职责时不建立空 Service 来满足形态。

### 4.2 15 表的 Maven 与包级 Owner

在本设计的目标状态中，15 表的 Maven Owner 全部是 `paytech-commerce-web`。在 Web 内进一步建立以下逻辑包 Owner，作为代码访问规则和未来提取的迁移边界：

| 逻辑 Owner | 唯一持久化职责 |
|---|---|
| `order` | `t_commerce_order`、`t_order_context`、`t_order_legal`、`t_order_item`、`t_order_payment_projection`、`t_order_funds_availability_projection` |
| `checkout` | `t_checkout_session`、`t_consumer_context`、`t_consumer_contract`、`t_checkout_network_evidence`、`t_consumer_acceptance` |
| `refund` | `t_order_refund_position`、`t_refund_request` |
| Web 服务级可靠性 | `t_command_idempotency`、`t_compensation_event` |

计数为 6 + 5 + 2 + 2 = 15，无遗漏、无重复。

只有逻辑 Owner 包定义对应 DataObject、Mapper、Repository 和写规则。其他能力包不得注入其 Mapper/Repository、复制 SQL、建立跨 Owner JOIN、使用 View/存储过程或 `JdbcTemplate` 绕过边界。跨 Owner 用例由 `service/processor` 组合 Owner 的业务接口。虽然当前同属一个 JAR，架构测试仍应把这些访问规则变成可执行约束。

服务级可靠性不是第四个业务域。它在 `paytech-commerce-web` 内统一接入命令幂等与 Foundation Compensation Event，不复制到 Order、Checkout 或 Refund。

## 5. 用例编排与事务

### 5.1 本地原子边界

复杂用例的 Processor 使用一个本地 `TransactionTemplate` 覆盖完整本库原子范围。Owner 业务接口加入调用方已有事务，不自行 `REQUIRES_NEW`、提前提交、切换线程或用 Compensation Event 拆散本地强一致写入。

| 用例 | 必须保留的本地原子范围 |
|---|---|
| 创建 Order | `commerce_order + order_context + order_legal + order_item + order_refund_position` 一并建立；业务变更所需可靠任务在同一事务登记 |
| 锁定法律接受 | 同 Order/Session 的 `LEGAL_ACCEPTANCE` Network Evidence 与三种不可变 Acceptance 同一事务插入 |
| PaymentSucceeded 收敛 | 幂等更新 Payment Projection、Order 和 Checkout Session；Refund Position 的 `refundable_base_amount` 只允许从零按唯一成功金额建立一次，冲突失败关闭 |
| 创建 Refund | Refund Request 与 Refund Position 的 `reserved_amount` CAS 预占同一事务提交 |
| Refund 终态 | Request 终态与 Position 的预占释放或已退款金额消费同一事务提交 |
| Cancel/Void 终止收敛 | Payment 给出确定权威结果后，Order 与 Checkout Session 使用 CAS 一致关闭，并在需要时同事务登记 `OrderCancelled` |

COM-I10 是跨 `order` 与 `checkout` 的实时一致性查询；Processor 或一个明确的组合查询能力负责组合结果，任何包不得绕过另一个 Owner 直接查询其表。

### 5.2 远程调用边界

Commerce 通过本地 Integration Port 调用 CCT、Risk、Finance、Payment 和 Vault，通过可靠事件向 Platform Support 等消费者发布事实，并且不读写其他数据库。远程调用不放在持有 Commerce 数据库行锁的长事务中；涉及“远程准备、局部提交、远程提交或恢复”的流程使用稳定幂等键、版本复验和批准的阶段协议。

具体业务权威保持不变：

- Create Order 先取得 CCT、Risk 和 Finance 的权威结果，再冻结本地 Order 事实；Payment Intent 的创建使用稳定上下文和恢复协议；
- 本地 Refund Position 预占成功后直接调用 PAY-I04，Payment 自己锁定其 Refund Position；
- Cancel 只有在 PAY-I12 确认安全终止后才能关闭 Order/Session；
- Reversal 由 PAY-I13 在 Payment 权威锁内选择 Use Cancel、Void、标准全额 Refund、Pending 或不支持分支；Commerce 不增加 Reversal 表或 Payment Action；
- 远程返回不确定时不得把未知结果猜成业务失败、支付失败或可安全取消。

### 5.3 MQ 与可靠执行

当前切片实际消费的已批准事件由 `ext/mq` 适配并委托具体 Processor 或 Domain Service。业务状态变更和所需 `compensation_event` 同一本地事务提交，MQ Ack 只能发生在事务提交后；不得根据完整事件目录预建空 Listener、Queue 或 Handler。

出站 Handler 使用 `event_key + event_type + sub_event_type` 查询本服务权威事实，每次只执行一项明确副作用，并按至少一次执行设计。可从本库恢复的上下文不得复制进 `compensation_event.payload_json`；MQ Handler 应回查本库权威事实并构建已批准的最小非敏感业务事件。Vault Token、网络原值、敏感明文和凭据不得进入补偿 Payload、普通日志或普通 MQ 事件。

## 6. 错误、安全与状态语义

- CCT、Risk、Finance、Payment 或 Vault 权威缺失、歧义、过期、不可用或不一致时，相关命令 Fail-Closed；不得生成默认 Operating Entity、零税、默认 Scope、默认 Acceptance 或猜测的 Payment 结果。
- 内部 HTTP 正常结果使用契约定义的 typed DTO；错误使用项目统一错误响应。Controller、Processor 和 Domain Service 不宽泛捕获异常后改成同义 `503` 或重复日志。
- Consumer Email/Phone/Profile Token 仅留在批准的内部边界。任何 Vault Token 都不得进入外部响应、普通日志、MQ 或通用审计 JSON。
- Checkout Network Evidence 的 IP、User-Agent、可信代理链和批准派生维度直接存于 Commerce，但属于受限数据；不得复制到普通页面、MQ、通用审计 JSON或默认完整 Body 日志。
- Acceptance Readiness 每次实时校验三个显式 Acceptance 及其同 Order/Session 的 Evidence，不建立 Summary、完成指针或集合 Hash。
- Payment Refund `UNKNOWN` 只属于 Payment；Commerce 保持 `PROCESSING` 和本地预占，不增加 `UNKNOWN` 状态。
- Cancel、Void、Reversal 和 Refund 保持不同语义；捕获后的 Reversal 完成 Refund 后 Order 仍为 `PAID`。

## 7. 实施前门禁

### 7.1 首个业务 Java 切片门禁

首个实施计划必须明确：

1. 一个可独立验收的纵向业务用例，而不是把全部 15 表机械生成 Java CRUD；
2. 该切片真实需要的 Maven 模块和依赖，不创建空 `paytech-commerce-api` 或空包；
3. 涉及表的逻辑 Owner、DataObject/Mapper/Repository、事务范围和锁后复验；
4. 内部与外部 HTTP 契约的路径、权限和 DTO 分离；
5. 所有外部依赖的本地 typed Client、超时、错误映射、幂等和有状态 Simulator；
6. PDManer、手工 Schema SQL、初始化 DML、Java 映射和本地数据库测试在同一获批数据库实施变更中一致。

### 7.2 现有横切迁移缺口

以下缺口会阻塞相应实现，但不由本模块设计擅自解决：

- Vault V1 当前只批准 Protect/Reveal；Commerce 需求和数据库文档中仍存在 Bind、Binding、Retire 等旧语义。实现 COM-I11 前必须完成 V1 适配，或另行批准窄 V2 的 API、模型、安全和测试设计。
- 六库 `compensation_event` 仍有旧 source/version/schema/hash/大 Payload 契约；接入当前 Foundation 前必须按 `COMP-01` 一次性对齐 Manifest、模型、SQL、Policy、场景和测试。
- 现有模型中的物理 CHECK/FK 与当前禁止新建 CHECK/FK 的规则存在迁移工作；Java 切片不得把尚未迁移的物理约束误写成应用不变量已验证。
- COM-I11、COM-I12 和 COM-I13 的 Vault Token、网络原值与 Consumer 输入受专项禁日志规则保护，与通用 MVC 完整 Body 日志默认值存在接入门禁。涉及这些接口的切片必须先批准可执行的 metadata-only 日志边界；不得通过服务本地重复 Filter 或静默放宽禁日志规则绕过。
- COM-I12 仍需在对应接口切片中冻结 Gateway 到 Commerce 的可信 Client IP、User-Agent 和可选代理链传递契约。Commerce 不得重新解析调用方可伪造的 `Forwarded` / `X-Forwarded-*`，也不得在契约缺失时猜测网络证据。

以下行为细节不属于 Maven 模块设计，必须由命中的纵向切片在实施计划前引用现行业务权威并闭合：Payment Intent 创建后 Payment Projection 与 Checkout Session 的阶段/恢复协议；COM-I10 的一致性读取和并发失效语义；每个实际入站事件的幂等键、Owner 方法和乱序策略。没有闭合这些细节的切片不得进入代码实施。

### 7.3 业务能力 JAR 的重新评估门禁

未来只有同时满足以下条件，才提交新的模块设计并逐个提取能力 JAR：

1. 候选能力已经有完整、在用且边界稳定的业务代码；
2. 单个 `paytech-commerce-web` 已经出现可复现的表归属、依赖或职责表达困难；
3. 能完整列出唯一业务规则、聚合和表 Owner；
4. 能发布少量稳定的业务 Command/Query 接口，使调用方无需知道持久化步骤；
5. 候选模块默认独立，或只有经批准的长期单向无环依赖；
6. 不共享 Repository、Mapper、DataObject、SQL 或通用 CRUD；
7. 架构测试可以阻止实现包、持久化对象和循环依赖越界；
8. 跨 Owner 本地事务仍由 `paytech-commerce-web` 的单个 `TransactionTemplate` 保持原子性。

`paytech-commerce-refund` 可以作为第一个重新评估候选，因为 Refund Position 与 Refund Request 具有集中金额不变量；这不是当前创建该模块的授权。`paytech-commerce-checkout` 的敏感数据特点本身也不足以证明应拆 JAR，因为同进程 JAR 不是安全隔离边界。

## 8. 测试与验收

### 8.1 基础结构门禁

实施时必须用自动测试证明：

- Reactor 模块清单与依赖方向准确、无环；
- 1.1 节的基础工程里程碑只包含父 Reactor、`paytech-commerce-web` 和 `paytech-commerce-test`，`paytech-commerce-api` 必须不存在；只有 Web 可执行，Test 不发布，生产 Java 源码只能包含启动入口；
- 首个真实内部 HTTP 契约创建 `paytech-commerce-api` 时，再证明 API JAR 可发布但不可运行，且不含 Spring Boot 启动、数据库、Mapper、Repository、业务实现或客户端配置；
- 首个 Owner 业务包进入实现时，再启用包级架构测试，拒绝跨 Owner Mapper/Repository/DataObject/SQL 访问以及 Controller 直达持久化；
- 首个业务 Service、Domain Service 或 Repository 进入实现时，再证明其形态符合现行 interface/Impl 规则；
- 没有空模块、空业务包、占位 Service 或巨型 Common。

### 8.2 业务行为门禁

每个切片从真实 Controller 或 MQ Listener 进入生产链，覆盖正常路径及相关反例：

- 幂等重放和同键异内容冲突；
- Expected Version/CAS 冲突、并发预占和中途失败全回滚；
- 远程依赖临时不可用、永久不一致和结果未知；
- MQ 重投、重复、乱序、迟到和 Ack/事务边界；
- Payment Attempt/Refund `UNKNOWN` 不被错误收敛；
- Vault Token、Network Evidence 和受限输入不进入外部响应、普通日志、MQ 或补偿 Payload；
- Cancel、Void、Refund 和 Reversal 的分支不可互换。

测试涉及相应能力时，复用仓库 `local-environment` 中人工启动的 MySQL、Redis 和 RabbitMQ；当前切片不使用的基础设施不为形式接入。外部 HTTP/MQ 使用真实协议的有状态 Simulator。禁止 Flyway、Liquibase、Testcontainers、应用启动建表/Seed，以及 `when(...).thenReturn(...)`、`@MockBean`、`@MockitoBean` 等方法级行为 Stub 替换生产链。

具体通过数量、环境和命令只记录到 Commerce README 与 `CURRENT_IMPLEMENTATION.md`，不回写本文成为永久状态。

## 9. 显式非目标与下一步

本设计不自行改变业务状态机、Vault V1、Foundation Compensation Event、六系统职责或部署拓扑。15 表、Checkout Public Token、Hosted Checkout Client API 与相关事件语义由后继专项设计拥有；本文只继续约束 Maven 和包边界，不授权创建业务 Java 代码、Schema SQL、初始化数据、外部基础设施配置、分支、提交或部署。

基础工程里程碑完成后，下一份实施计划只能针对一个明确的首个 Commerce 纵向业务切片。该计划不得把“完整 Commerce 服务”作为一个不可分解任务，也不得把本设计中的未来能力 JAR 候选当作已批准工程。
