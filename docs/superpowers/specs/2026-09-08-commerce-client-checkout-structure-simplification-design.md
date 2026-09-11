# Commerce Client Checkout 结构简化设计

日期：2026-09-08
状态：实施中；结构重构、HTTP 测试组织与 90% 覆盖率门禁已落盘，真实 MySQL HTTP 和覆盖率正向验收待完成；Owner 失败协议测试等待真实合同与 Simulator
恢复阶段后继（2026-09-09）：本文关于 Create Checkout“五阶段保持不变”的内容已由
[`2026-09-09-commerce-checkout-recovery-simplification.md`](../../../agent-docs/decisions/2026-09-09-commerce-checkout-recovery-simplification.md)
取代，现行恢复检查点为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`；本文现行结构与完成条件统一投影三个真实检查点，其余职责与测试组织决定继续有效。
命令幂等归属后继（2026-09-09）：通用 Command DataObject、Mapper/XML、Repository、技术状态与 Claim/Lease/CAS 已由
[`2026-09-09-foundation-command-idempotency.md`](../../../agent-docs/decisions/2026-09-09-foundation-command-idempotency.md)
收敛到 opt-in `paytech-command-idempotency`。Commerce 继续拥有 Checkout Hash、Snapshot、业务阶段、Validator、Converter、错误、资源和事务编排；本次没有把幂等转移给 Gateway 或中央服务。
取代范围：仅取代
[`2026-09-07-commerce-client-checkout-local-mock-slice.md`](../plans/2026-09-07-commerce-client-checkout-local-mock-slice.md)
中 Task 5 至 Task 7 的 Java 类职责划分和测试组织方式。原计划及
[`2026-09-05-commerce-client-api-v1-design.md`](./2026-09-05-commerce-client-api-v1-design.md)
拥有的 Wire、业务、幂等、恢复、数据库和安全合同继续有效。

## 1. 背景与目标

当前首片行为虽已覆盖 Create/Get Checkout，但实现把多项职责集中在少数类中：

- `CreateCheckoutProcessor` 同时承载阶段编排、四个 Owner 调用、异常归类和稳定失败重放；
- `ClientCheckoutServiceImpl` 同时承载 Owner 请求转换、金额计算、税务校验、七表聚合构造、Payment 绑定和 GET 查询；
- `CommandIdempotencyServiceImpl` 同时承载 ID/Token 分配、Lease/阶段状态机、JCS 快照编解码、成功回执编解码和持久化模型转换；
- Create 请求和 Prepared Checkout 在相邻层之间存在同义模型与纯字段复制；
- 多个只做依赖字段赋值的显式构造器没有遵守项目 Lombok 约定。

本设计只降低结构复杂度和样板代码，不改变任何外部可观察行为。目标是让每个类有一个可直接说明的职责，让 POST 的三个真实恢复检查点从入口代码即可顺序阅读，并用 HTTP 入口测试覆盖完整生产链。

## 2. 不变边界

本轮固定保持：

- 仍为 `paytech-commerce-client-api -> paytech-commerce-web -> paytech-commerce-test` 三模块，不新增 Maven 模块；
- 仍只实现 `POST /external/client-api/v1/checkouts` 和
  `GET /external/client-api/v1/orders/{orderNo}/checkout`；
- Java API、OpenAPI、RFC 9457、金额 String Wire、默认 30 分钟及显式 5 分钟到 24 小时过期合同不变；
- `CLAIMED -> ORDER_COMMITTED -> COMPLETED` 三个真实持久化检查点、Lease、稳定失败和成功回执重放语义不变；
- CCT、Risk、Finance、Payment 调用仍在 Commerce 写事务之外；
- 七表创建和 Payment 绑定/过期仍使用短 `TransactionTemplate` 事务与既有 CAS/唯一索引保护；
- 15 表模型、Mapper XML、人工 SQL、Gateway、Nginx 和 Checkout Web 均不在本重构范围内；
- Local Mock 仍只是当前已批准的本地例外，不表示真实 Owner 合同或生产集成完成。

## 3. 目标结构

```text
ClientCheckoutController
├── POST -> CreateCheckoutProcessor
│   ├── CheckoutCreationIdempotencyService
│   │   ├── CheckoutRequestHasher
│   │   ├── CheckoutIdempotencySnapshotCodec
│   │   ├── CheckoutCreationIdempotencyValidator
│   │   └── Foundation CommandIdempotencyService
│   ├── CheckoutCreationService
│   │   ├── CheckoutOwnerCommandConverter
│   │   ├── PreparedCheckoutAssembler
│   │   └── CheckoutRepository
│   ├── CctClient / RiskClient / FinanceClient / PaymentClient
│   └── CommerceTransactionExecutor
└── GET -> CheckoutQueryService
    └── CheckoutRepository
```

业务 Service 继续遵守 `interface + *Impl`；Processor、Controller、Hasher、Codec、Converter 和 Assembler 保持具体类。Hasher、Codec、Converter 和 Assembler 都只有一个当前实现，且不是业务 Service，不为它们创建形式接口。

### 3.1 Controller

`ClientCheckoutController` 只处理可信 Header、Wire DTO 与应用模型转换、HTTP 委托及成功响应映射。它不判断恢复阶段、不访问 Repository、不计算金额，也不拥有事务。

### 3.2 CreateCheckoutProcessor

Processor 只拥有应用编排：

1. 计算语义请求 Hash 并 Claim/恢复命令；
2. 根据唯一的持久化阶段决定下一步；
3. 在事务外调用 CCT、Risk、Finance 和 Payment Client；
4. 用短事务提交 Prepared Root、绑定 Payment 并冻结结果；
5. 对稳定业务失败执行冻结，对技术暂态保留可恢复状态。

`create(...)` 主流程和每个阶段方法遵守单一职责与原则上不超过 30 行的项目规则。不创建阶段 Handler 层、抽象 Processor、通用工作流引擎或一行转发类。

阶段职责只保留两处且语义不同：Processor 决定当前阶段应执行哪一步；`CheckoutCreationIdempotencyService` 校验并持久化合法迁移。Repository 只执行带 expected stage/version 的 CAS，不再自行决定业务下一阶段。

### 3.3 CheckoutCreationService 与 CheckoutQueryService

原 `ClientCheckoutService` 拆为两个真实业务能力：

- `CheckoutCreationService`：负责本地 Checkout 创建规则、Prepared Root 创建/恢复、Payment 绑定或过期，以及创建回执构造；
- `CheckoutQueryService`：负责 trusted Client 归属查询、过期 URL 隐藏和 GET 投影。

两者分别使用 `interface + *Impl`。查询不再与创建依赖和写入规则混在一个 Service 中。

`CheckoutOwnerCommandConverter` 只把已验证的应用/领域对象转换为四个 Owner Port 的 Command/Query。`PreparedCheckoutAssembler` 只负责行金额计算、Finance 税务结果校验和七表 Prepared Aggregate 构造。它们不访问数据库、不调用外部 Client、不拥有事务。

### 3.4 CheckoutCreationIdempotencyService

原本地 `CommandIdempotencyService` 收窄并改名为 `CheckoutCreationIdempotencyService`。它组合 Foundation API，并只拥有 Create Checkout 的业务解释与恢复编排：

- Claim 与命令状态恢复；
- Lease 获取/续租/接管；
- 合法阶段迁移；
- 成功或稳定失败完成；
- 已冻结成功/失败结果恢复。

ID、Order No、Token、默认过期值等首次 Claim 必须冻结的值仍由该服务分配，但序列化细节不留在服务实现中。通用记录构造、技术状态、Mapper/XML、Repository、Claim winner、Lease 与 Version CAS 由 Foundation JAR 拥有；Foundation 不解释 `CREATE_CHECKOUT` 或任一业务恢复阶段，也不创建事务。

具体类 `CheckoutCreationIdempotencyValidator` 集中承载无副作用校验：当前请求与数据库 winner 的
Caller Scope、Idempotency Key 和语义 Hash 一致性，命令记录生命周期，快照/Claim 坐标，以及冻结结果完整性。
Processor 在 Claim 后先调用它校验当前请求，再判断成功或失败重放；Service 在执行 Lease、阶段迁移和终态写入前
仍于原事务内调用相应状态前置校验。Validator 可以校验 Idempotency Service 传入的只读 `CommandRecord`
投影，但不注入或调用 Repository、不打开事务，也不替代数据库唯一索引、CAS 或受影响行数检查。

具体类 `CheckoutRequestHasher` 只从统一的 `CheckoutCreationRequest` 构造语义投影、调用 Foundation JCS 并计算版本化 SHA-256；不再发布一套嵌套 Request/Item，也不为单实现创建接口。

`CheckoutIdempotencySnapshotCodec` 统一拥有版本化 Request Snapshot 与成功 Receipt 的四个 encode/decode 操作、RFC 8785 canonical bytes、大小上限和闭合投影校验。它不计算业务 Hash、不访问 Repository，也不决定幂等状态。

实施期的 fail-closed 审查发现，语义请求 Hash 只覆盖 Client 请求字段，不能独立保护首次 Claim
生成的内部 ID、Order No、Token 候选和有效过期时间。由于该切片尚未发布、部署或写入已批准的
Commerce Schema，本轮在 V1 首次落地前把 Request Snapshot 定稿为 `{h,p}` 闭合文档：`p`
保存完整恢复 Payload，`h` 保存该 Payload 的 RFC 8785 canonical bytes 的 SHA-256。该 Hash
只用于检测内部持久化快照的局部损坏，不是认证签名，也不替代外部请求的语义 Hash。恢复时还须把
快照的 command/client/order/operation-time 坐标与命令行、把冻结成功回执与实际已提交 Root 逐项
核对；任何不一致均 fail closed。Response Snapshot 和外部 HTTP/Wire 合同不变。

### 3.5 Repository

Commerce 继续保留一个 `CheckoutRepository`，因为七表创建与 Payment 绑定是同一 Commerce 聚合的原子写入，拆成多个仓储会隐藏事务边界。通用 Command Idempotency Repository 已迁到 Foundation，不再在 Commerce 复制。

Repository 只负责 MyBatis 调用、唯一冲突识别、expected state/version CAS 和 DataObject 到领域投影的转换。金额、Owner 结果、阶段合法性和 HTTP 回执规则留在上层职责 Owner 中。

## 4. 模型收敛

- 保留 `*-client-api` Wire DTO，不让它们进入 Domain Service 或 Repository；
- Create HTTP 请求只转换成一套传输无关 `CheckoutCreationRequest`/Item 模型，Idempotency Key 作为 Processor 的命令元数据单独传入；Hasher 不再定义第二套同义 Request/Item；
- `PreparedCheckoutRecord` 继续作为 MyBatis 查询 DataObject；Repository 和 Service 之间只使用一套 `PreparedCheckout` 领域投影，删除中间同义复制模型；
- 创建回执和 GET 结果使用独立的传输无关应用结果，不嵌套在庞大的 Service 接口中；
- compact record constructor 中的格式、金额恒等式、防御性复制和时间精度校验继续保留。

本轮不把所有 DTO 合并成一个模型：Wire、持久化 DataObject 和领域/Application 模型承担不同边界，仍需分开。

## 5. Lombok 边界

`paytech-commerce-web` 增加 `org.projectlombok:lombok` 的 `provided` 依赖，并在 `maven-compiler-plugin.annotationProcessorPaths` 显式注册 Lombok；版本继续由现有 BOM 管理。

纯依赖字段赋值构造器改用 `@AllArgsConstructor`，覆盖 Processor、Controller、Creation/Query/Idempotency Service 实现、两个 Repository 实现、Foundation ID Adapter 及本轮新增的同类组件。字段先按项目规定排序，Lombok 生成顺序以字段顺序为准。

以下构造器不使用 Lombok 替换：

- 创建并配置 `TransactionTemplate` 的 `CommerceTransactionExecutor`；
- 初始化或校验 `SecureRandom` 的 Token/Order No Generator；
- 调用特定父构造器的异常类；
- 含业务校验、防御性复制或派生值的 value object/record；
- Servlet Response Wrapper 等必须调用父构造器的类型。

不对领域对象添加 `@Data`，不为 record 重复生成构造器/访问器，也不为少量静态方法引入 `@UtilityClass`。

## 6. HTTP 入口测试与覆盖率

本切片的业务行为自动测试只从真实 HTTP 入口发起：

```text
随机 Loopback HTTP Client
  -> ClientCheckoutController
    -> CreateCheckoutProcessor / CheckoutQueryService
      -> 真实 Service
        -> 真实 RepositoryImpl / Mapper / local MySQL
        -> 当前批准的确定性 Local Mock Client
```

不再直接调用 Processor、Service 或 Repository 建立各层行为测试，也不因新增 Codec、Converter、Assembler 而为每个内部类复制一套测试。原内部 Checkout 测试只有在等价 HTTP 场景已覆盖且覆盖率门禁通过后才删除或迁移。

原 `ClientCheckoutHttpAdapterAutoTest` 使用手工 Controller Harness 和内存替身，绕过了真实 Processor、Service、Repository 与 Mapper，不满足本设计。它重写为随机端口真实应用测试，并按公开场景拆成 `ClientCheckoutCreateHttpAutoTest`、`ClientCheckoutIdempotencyHttpAutoTest` 和 `ClientCheckoutQueryAndErrorHttpAutoTest`；公共测试支持只保留 HTTP Client、请求 Builder、可调 UTC Clock，以及精确准备、清理和核验测试自有数据库事实的 Fixture。

HTTP 场景至少覆盖：

- 请求字段、金额、Item、默认/显式过期边界和 RFC 9457；
- 创建成功、GET、Client 隔离、过期 URL 隐藏；
- `clientOrderId` 永久唯一；
- 同 Key 同请求重放、同 Key 异请求冲突、处理中 Lease 和各恢复阶段；
- 当前确定性 Local Mock 可达的 CCT/Risk/Finance/Payment 成功路径；
- Payment 成功后的冻结回执，以及 Order 后续状态变化后的成功重放；
- 顺序唯一性、并行请求结果 smoke、事务回滚、Token 候选冲突，以及 Request Snapshot 完整性、命令生命周期、
  成功回执与实际 Root 不一致时的 Fail-Closed。

测试通过 HTTP 断言响应，并按场景联合验证本地数据库事实和不应发生的后续副作用。测试不使用 Mockito、`@MockBean`、Spy、方法级 Stub、H2、Testcontainers 或自动 Schema 迁移。

Owner `@HttpExchange` 合同尚未批准，本轮不得为了覆盖率在测试中用 `@Primary` Client、动态 Fake、调用计数器或 Barrier 替换生产 Integration Client。当前两个线程的不同 Key 用例只证明并行 HTTP 请求最终稳定返回一个 `201` 和一个 `409`，不作为确定性唯一键/CAS 竞争证据。Risk 拒绝/处理中、Client 配置未就绪、四个 Owner 的超时/不可用/协议错误，以及依赖真实调用时序的确定性并发场景，等待 Owner 发布合同后通过“生产 HTTP Client → 随机 Loopback 有状态 Simulator”补齐；在此之前不把这些分支描述为合规自动化证据，也不下调 90% 门禁。

`paytech-commerce-test` 收集真实 HTTP 测试执行数据并生成覆盖 `paytech-commerce-web` 生产类的 JaCoCo aggregate report；Maven `verify` 使用能够读取聚合结果的门禁，对 Commerce Web 的 Hosted Checkout 生产包设置不低于 90% 的行覆盖率。不能在 Test 模块对自身空生产类运行普通 `jacoco:check` 后产生假绿。不得排除 Checkout Controller/Filter、Processor、Service、Repository、Integration Adapter 或错误分支来提高数字；覆盖率不足时补充 HTTP 业务场景，不添加只执行不断言的测试。

Java API/OpenAPI 漂移、模块依赖和文档/模型生成器等静态门禁可以继续独立存在，因为它们不替代业务行为测试，也不直接调用某个 Service 来伪造用例通过。

## 7. 实施顺序与安全

1. 先把现有 POST/GET 行为迁移为 HTTP 入口特征测试，并建立覆盖率基线；
2. 增加 Lombok 构建配置并替换纯赋值构造器；
3. 收敛应用/领域模型，保持 Wire 与数据库模型不变；
4. 提取 Snapshot Codec，并收窄 Checkout Creation Idempotency Service；
5. 拆分 Creation/Query Service，提取 Converter 与 Assembler；
6. 简化 Processor 和 Repository，集中阶段与事务职责；
7. 删除已被 HTTP 场景完整取代的内部行为测试，运行完整 Commerce、模型和 Requirements 门禁。

每一步都必须保持 HTTP 特征测试为 Green。任何幂等回执、请求 Hash、恢复阶段、SQL 写入集合、状态/version 或错误状态的变化都视为回归，不以“重构”为理由接受。

## 8. 完成条件

- API/Wire、三个真实恢复检查点、数据库和错误合同与当前权威一致；
- Processor 可顺序读出 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`，不包含金额、JCS 或 SQL 细节；
- Idempotency Service 不再直接包含 Jackson/JCS 文档 record 和编解码实现；
- Creation Service 与 Query Service 职责分离；
- Repository 不再拥有业务阶段决策，Service/Repository 不再重复 `PreparedCheckout` 模型；
- 所有纯赋值注入构造器按规范使用 Lombok，特殊构造器保留显式实现；
- Checkout 业务行为测试全部从 HTTP 入口发起，当前可达场景完整执行生产 Controller、Processor、Service、Repository/Mapper 与确定性 Local Mock Client；
- Owner 错误和真实调用时序场景在合同发布后只通过生产 HTTP Client 与有状态协议 Simulator 覆盖，不引入内部方法 Stub；
- Commerce Web JaCoCo 行覆盖率不低于 90%，相关 Maven、模型和 Requirements 门禁通过；
- 未应用数据库 SQL、未启用 Gateway Route、未部署，也未把 Local Mock 描述为生产能力。

## 9. Client API 入站边界与文档简化补充

2026-09-08 的实现复审进一步批准以下收敛。它修正 Java 适配层和请求反序列化的重复实现，
不改变已批准的公开 Route、Header 名称、既有字段、幂等或业务语义。唯一的外部合同放宽是：
合法请求中的未知 JSON 属性由拒绝改为忽略，以便 V1 增加可选字段时新旧双方可以滚动升级。
这条规则取代
[`2026-09-05-commerce-client-api-contract-module-design.md`](./2026-09-05-commerce-client-api-contract-module-design.md)
第 6.1 节“请求对象拒绝未知字段”的旧决定；响应忽略未知字段的规则不变。

### 9.1 Converter 与 Mapper 命名

非持久化的 Wire/Application 转换组件统一使用 `*Converter`。`Mapper` 只用于 MyBatis
持久化接口和明确的 DataObject 映射边界；因此 `CheckoutWireMapper` 改名为
`CheckoutWireConverter`。Jackson `ObjectMapper`/`JsonMapper` 不属于该命名规则。

### 9.2 Header Owner

- Foundation 在现有 `paytech-common-core` 中只拥有稳定、框架无关的公共 Header 名称；
  不新增 Maven 模块，也不拥有各业务 API 的长度、字符集或幂等语义；
- `X-Request-Id` 的格式、生成、MDC 和响应回写继续由 Foundation MVC Request ID
  基础设施负责，业务 Controller 方法不再重复接收或校验它；
- Gateway 负责 Client Credential 的认证、Scope 校验以及可信 Client/Credential ID 注入；
  Commerce 信任该身份结论，不重复鉴权；
- Commerce Web 入站边界仍对两个可信身份 Header 做单值、必填和强类型绑定并失败关闭。
  “信任 Gateway”不表示接受缺失、重复或无法解析的内部协议输入；
- `Idempotency-Key` 的名称可以复用 Foundation 常量，但重复 Header、Commerce V1
  格式和幂等认领语义仍只由 Commerce 拥有。Converter 不重复校验；
- Client Reference、币种、国家和十进制字符串约束继续留在 Commerce Client API。

当前 Gateway 尚未完成 Client/Credential ID 的生产注入，因此本补充不声称公开 Route
已经可以启用。该实现缺口继续只记录在 `agent-docs/CURRENT_IMPLEMENTATION.md`。

### 9.3 请求反序列化

Request Record 使用 Jackson 正常属性绑定和 Jakarta Bean Validation，不再通过
`@JsonCreator(mode = DELEGATING)` 接收 `Map<String, ?>` 后手工复制字段：

- V1 请求必须忽略未知 JSON 属性，以兑现向后兼容规则；
- `@NotNull`、`@NotBlank`、`@Size`、`@Pattern` 和级联 `@Valid` 负责对象构造后的字段语义；
- 可选但一旦出现便不可为 `null` 的字段使用一个经过验证的通用反序列化器区分“缺失”和“显式
  `null`”。当前 Jackson 3 的 Record Creator 会让 `@JsonSetter(nulls = FAIL)` 同时拒绝缺失字段，
  因此不使用该注解，也不为每个 DTO 恢复 Map Creator；
- Commerce Web 关闭 JSON scalar 到目标 scalar 的隐式转换，金额和数量等 String Wire
  不接受 JSON number、boolean 或其他标量冒充字符串；
- Malformed JSON、错误 JSON 类型和无法反序列化的时间属于结构/解析错误，使用统一
  RFC 9457 `400 INVALID_REQUEST`，不伪装成字段 Bean Validation；
- 成功反序列化后的缺失、格式和值域错误使用 `422 VALIDATION_ERROR`，可携带安全的
  `errors[]` 字段投影。

Foundation MVC 继续统一映射标准 Jackson/MVC 异常。本轮不为每个 DTO 维护字段名集合、
Map Creator 或业务 API 专用的 Foundation 反序列化异常。

### 9.4 公共合同注释

`paytech-commerce-client-api` 中 Checkout、Order、Refund 和公共模型的所有公开 API 方法、
Request/Response Record 组件及非显然枚举语义必须有可生成的 JavaDoc。JavaDoc 说明业务含义、
格式/单位、可空性、默认值和幂等/快照语义，不复述注解。公开 OpenAPI 同步补齐 operation、
parameter、schema 和 property 描述；JavaDoc 与 OpenAPI 不得给同一字段定义不同语义。
