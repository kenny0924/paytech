# Foundation RFC 8785 JSON Object Profile 决策

- Decision status: Confirmed
- Implementation status: Not started
- Deployment status: Not deployed
- Decision date: 2026-09-07
- Supersedes: Risk 现有设计中“Foundation 缺失时临时保留私有 Canonical JSON writer”的长期方向；不改变已冻结的 Risk Hash Schema、
  Commerce 幂等/Webhook 或其他 Owner 的业务投影与 Hash/签名语义

完整合同见
[`2026-09-07-foundation-rfc-8785-json-canonicalization-design.md`](../../docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)。
实现、验证、制品发布和部署状态只由 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 与组件 README 记录。

## 1. 背景

Risk 已有七类真实的规范 JSON Hash 消费者，Commerce Hosted Checkout V1 也已冻结命令幂等 Request Hash、
Webhook Object/Envelope Hash 和 Platform Webhook HMAC 边界。CCT 与 Finance 后继 BCR Snapshot 同样需要稳定 bytes。

普通 Jackson 属性排序不能自动证明 RFC 8785 的 UTF-16 属性排序、String escaping、重复属性、Unicode 与 Number 合同。
各服务复制私有 writer 会使同一语义对象产生不同 bytes，进而破坏幂等恢复、持久化 Hash、Webhook 冲突检查或验签。

## 2. 已确认决定

### D1. 独立、按需引入的 Foundation JAR

新增 `com.paytech:paytech-json-canonicalization`。它是无 Spring 的普通 Java JAR，不是 Starter、服务或部署单元；不创建
Bean、线程、配置、网络、数据库、Redis 或 MQ 资源。

模块不进入 `paytech-common-core`、MVC Starter 或 Compensation Event 的传递依赖。只有真实生成规范 bytes 的消费者才直接声明依赖。

### D2. V1 是严格 Object/Number Profile

公共入口只接受非空、严格 UTF-8、唯一 Root Object 的 byte[]。在该 Object 内，JSON Number 只允许词法
`0|[1-9][0-9]*` 且数值不大于 `9007199254740991`。负数、小数、指数、超出安全整数范围的值和非 Object Root 都失败关闭。

Money、Decimal、64-bit ID 和时间由 Owner 按各自已冻结合同编码为 String。String 内容不执行 Unicode normalization、trim
或大小写改写；Array 顺序保留。在这个输入子集内，输出必须与 RFC 8785 逐字节一致。

### D3. 第三方实现受 Foundation 适配器隔离

V1 在严格前置验证后使用 `io.github.erdtman:java-json-canonicalization:1.1`。该 binary 是可替换的实现细节，
不是对完整 RFC 8785 Number 域的合规声明。Foundation 公共 API 不暴露第三方或 Jackson 类型。

严格验证拒绝 malformed UTF-8、BOM、duplicate property、lone surrogate、非标准 JSON、Profile 外 Number 和超限输入。
Document、nesting、String、Property Name 和 Number 的固定技术上限以完整设计为准，不依赖 Jackson 可漂移默认值。

### D4. Foundation 不拥有 Hash 或业务语义

Foundation 只把 Owner 生成的固定 JSON Projection 转为规范 UTF-8 bytes。它不接收 POJO、Map、JsonNode 或原始 HTTP DTO，
不计算 SHA-256/HMAC，不决定字段集、Domain Tag、Schema Version、Money/ID/Time 格式、Array 业务排序、冲突或重试语义。

所有这些决策继续归真实业务 Owner。`commerce-client-api`、Gateway、BFF 和不生成规范 bytes 的服务不因此改变。

### D5. 失败是内部系统故障

Owner 在调用 Foundation 前完成业务参数和 Projection 验证。Foundation 以固定安全消息抛出自有运行时异常，包装底层异常时
保留原始 Cause。Cause/message 不进入第一方 HTTP Problem。

一旦 Owner 已生成应当合法的 Source bytes，canonicalization 失败就是系统不变量或内部故障，统一安全映射为 HTTP 500。
不得被宽泛 RuntimeException catch 改成参数错误 400。

### D6. Risk 是首个真实消费者

首次实施同时迁移 Risk，但保持 `RiskDecisionCanonicalizer` 的业务 Projection、公开方法和 Hash Schema 不变。
Risk-owned Adapter 仍负责 NFC、Decimal、UTC、ID 与 Array 业务排序；Foundation 只取代底层属性排序与 canonical bytes。

现有七组 canonical bytes、UTF-8 hex 和 SHA-256 必须逐字节不变。Commerce、Platform、CCT 和 Finance 只在各自真实纵向切片中按需接入，
本决定不授权创建空 Adapter 或占位调用。

## 3. 文档与状态归属

- 本决策冻结独立 JAR、Object/Number Profile、Owner Hash 边界和首个 Risk 消费者；
- 完整设计拥有公共 API、严格验证、技术上限、测试向量、依赖隔离和发布升级合同；
- [`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 拥有 Foundation 模块、依赖与允许/禁止技术投影；
- [`DEVELOPMENT_CONVENTIONS.md`](../DEVELOPMENT_CONVENTIONS.md) 继续拥有异常、公共包和通用编码规则；
- [`UNIT_TEST_GUIDE.md`](../UNIT_TEST_GUIDE.md) 继续拥有 Java 测试方法；
- 各业务文档继续拥有自己的 Projection、Hash Schema、存储、重放与签名语义；
- [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 独占实现、验证、制品发布和部署状态。

## 4. 结果与限制

正向结果是非消费服务零侵入，真实消费者只引入一个窄 JAR，且未来替换第三方实现时不改变业务源码。

限制是 V1 有意不接受完整 RFC 8785 Number 域。新消费者若需 Root Array/primitive、负数、小数、指数或其他 IEEE-754 Number，
必须先增加新设计、官方向量和完整数字实现验证，不得直接扩大适配器输入域。

截至本文确认时，BOM、Foundation JAR 和 Risk 迁移均未实施，没有新制品发布或环境部署。
