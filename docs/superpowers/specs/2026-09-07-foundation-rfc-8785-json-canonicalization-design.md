# Foundation RFC 8785 JSON Object Profile 设计

状态：Confirmed

日期：2026-09-07

实现、验证、制品发布与部署状态：仅见
[`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)，不得从本文推断已经实现、发布或部署。

## 1. 背景与问题

Commerce Hosted Checkout V1 已经冻结两类真实消费者：

1. 命令幂等以规范语义请求的 RFC 8785 UTF-8 bytes 计算版本化 SHA-256；
2. Client Webhook 对公开 Object 和完整 Envelope 生成相同的规范 bytes，Commerce/Platform 分别保存内容 Hash，
   Platform 还以实际发送的原始 bytes 计算 HMAC-SHA256。

Risk 当前也会为 Rule Set、Decision Point、Release 和管理命令生成稳定内容 Hash。它在共享能力缺失时实现了一个
Risk 私有、受限的 writer；该 writer 同时承担 NFC、金额/时间/ID 和业务数组排序，因此不能直接作为公共 RFC 8785
实现。CCT、Finance 与按类型登记的 BCR Snapshot 也已经有确定的规范 JSON Hash 消费场景。

当前 Foundation 没有共享 JCS 能力。Jackson 的普通属性排序配置不能证明 ECMAScript 数字序列化、I-JSON、重复属性、
非法 Unicode 和 UTF-16 属性排序的完整 RFC 8785 合同。各服务继续复制近似 writer 会使同一逻辑对象在不同语言或版本中
产生不同 bytes，进而破坏幂等恢复、持久化 Hash、Webhook 冲突识别或验签。

## 2. 决策

新增一个独立、显式 opt-in、无 Spring 的 Foundation JAR：

```text
com.paytech:paytech-json-canonicalization
```

它只负责把一个已经由业务建立并验证的 JSON Object 转为规范 UTF-8 bytes。V1 只接受本文冻结的
Code-MoR JSON Object Profile；该输入子集上的输出必须与 RFC 8785 完全一致。Foundation 使用自有的严格输入验证器
包住 `io.github.erdtman:java-json-canonicalization:1.1`，把该 binary 视为可替换的实现细节，而不是完整 RFC 8785
合规证明；第三方类型不进入公共签名。

`paytech-build` 的 `paytech-dependencies` 固定第三方版本和新的 Foundation artifact 版本。业务消费者按需直接依赖新 JAR；
不把它塞入 `paytech-common-core`、`paytech-mvc-web-starter` 或 `paytech-compensation-event`。

这只是一个普通 Java JAR，不是服务、Starter 或部署单元，不创建进程、端口、Bean、配置、数据库、Redis、MQ 或线程池。

## 3. 方案比较

### 3.1 采用：独立 Foundation JAR + 受限实现适配器

优点：

- 在严格限定输入域后，复用已有 Java 实现的属性排序、String escaping 和 primitive 输出；
- 业务代码只依赖 PayTech API，未来替换实现不会改变消费者源码；
- 依赖只进入真实消费者，不通过 Common Core 传递给所有服务；
- 可以为持久化 bytes 建立独立的官方向量、兼容性和依赖升级门禁。

代价：增加一个小型 Maven artifact，并需要对较老的第三方实现增加严格前置验证和
逐字节兼容门禁。该依赖的旧年代和 draft 来源是被显式接受的替换成本，不是对它的通用合规背书。

### 3.2 不采用：把能力放入 `paytech-common-core`

这会让所有基础消费者隐式携带一个只有少数系统需要的协议依赖，并把 JCS 的长期兼容发布节奏耦合到 Money、分页和公共
异常等高扇出基础类型。代码量少不等于职责相同，因此不采用。

### 3.3 后备而非并行实现：Foundation 自研完整 JCS

只有当受限实现无法通过本设计的强制向量或依赖审计时才重新评审该方案。自研完整实现必须同时解决严格
JSON/I-JSON 解析、完整 ECMAScript Number、UTF-16 排序、Unicode/转义与 RFC Errata，风险和维护成本更高。不得把 Risk 当前受限 writer 原样复制后
声明为通用 RFC 8785，也不得同时保留两套可由业务任选的 Foundation 实现。

## 4. 公共 API

公共入口位于：

```text
com.paytech.foundation.jsoncanonicalization.api
```

V1 只发布两个类型：

```java
public final class Rfc8785JsonCanonicalizer {

    private Rfc8785JsonCanonicalizer() {
    }

    public static byte[] canonicalizeObject(byte[] jsonUtf8);
}

public final class JsonCanonicalizationException extends RuntimeException {

    public JsonCanonicalizationException() {
        super("JSON canonicalization failed");
    }

    public JsonCanonicalizationException(Throwable cause) {
        super("JSON canonicalization failed", cause);
    }
}
```

精确合同：

- 输入必须是非空、严格 UTF-8 编码、只有一个 Root Object 的 JSON bytes；
- 输出是新分配的 RFC 8785 Canonical UTF-8 bytes；方法不修改调用方输入；
- Object 属性递归按原始 UTF-16 code unit 排序；Array 元素顺序完全保留；
- String 内容保持原值，不执行 NFC/NFD、trim、大小写或其他业务归一化；
- JSON Number 只接受十进制词法 `0|[1-9][0-9]*`，且数值不大于 `9007199254740991`（2^53 - 1）；
- 负数、小数、指数形式和超出安全整数范围的值一律拒绝；Money、Decimal、64-bit ID 和时间继续由 Owner
  按既有合同编码为 String；
- 任意非法输入抛出 `JsonCanonicalizationException`；外层消息固定且不得包含 JSON、属性名、属性值或底层异常原文，
  但包装底层异常时必须保留原始 Cause；
- 不提供 `canonicalize(Object)`、POJO、Map 或 Jackson `JsonNode` overload；
- 不提供 String 平台默认编码入口；
- 不返回或计算 SHA-256、HMAC、Domain Tag、Schema Version 或业务大小限制。

Root Object 和 Number Profile 都不是对 RFC 的重新定义，而是 V1 对当前所有真实消费者的最小输入域。在这个输入子集内，
规范输出仍必须与 RFC 8785 逐字节一致。需要 Root Array/primitive，或需要负数、小数、指数及其他 IEEE-754 Number 的
真实消费者出现时，必须先提出新设计、补齐官方向量并验证完整 Number 实现，再扩展 API；不得让未验证的
第三方行为自动成为公共合同。

公共 API 不发布第三方类、Jackson 类型、内部验证器或原因枚举。当前只有一个标准和一个实现，不创建没有替换价值的
接口、Factory 或 Spring Bean。

## 5. 业务调用边界

每个 Owner 先建立自己的固定语义投影，再调用 Foundation：

```text
typed business input
  -> Owner validation and normalization
  -> Owner fixed JSON projection
  -> UTF-8 JSON bytes
  -> Rfc8785JsonCanonicalizer.canonicalizeObject(...)
  -> canonical UTF-8 bytes
  -> Owner-selected SHA-256 / HMAC / persistence / transmission
```

Owner 继续独占以下规则：

- 字段白名单、字段是否显式为 `null`、Domain Tag 和 Hash Schema Version；
- Money、Decimal、Snowflake ID 和 UTC 时间的字符串格式；
- NFC、trim、Code 大小写和其他业务字符串规则；
- Array 的业务排序、去重和完整 Tuple 校验；
- 输入/输出大小、Retention、日志和敏感数据边界；
- SHA-256、HMAC-SHA256、签名 Header、密钥和常量时间比较；
- Hash 冲突、命令幂等、Event 冲突及失败恢复语义。

Foundation 只拥有 JSON canonicalization。它不判断两个业务请求是否语义等价，也不接受原始 HTTP DTO 直接反射序列化。

## 6. 严格输入验证与第三方隔离

受限实现承担属性排序、String escaping 和已批准 primitive 输出；Foundation 适配器必须在调用它之前完成一次不改写
输入的严格流式验证。验证至少保证：

1. byte[] 非 `null` 且非空，不带 UTF-8 BOM，并可由严格 UTF-8 decoder 无损解码；禁止用 replacement character
   修复非法 byte sequence，但必须保留输入本来就合法包含的 U+FFFD；
2. 只有一个 Root Object，Root 结束后没有第二个值或非空白内容；
3. JSON 语法严格，不接受 comment、single quote、unquoted name、trailing comma、leading zero 或非标准 literal；
4. 同一 Object 不存在重复的解码后属性名；例如 `"a"` 与 `"\u0061"` 必须视为重复，嵌套 Object 同样适用；
5. 属性名和 String value 不含未配对 UTF-16 surrogate；
6. Number token 必须精确匹配 `0|[1-9][0-9]*`，并在不丢精度的整数解析后落在 `0..9007199254740991`；
7. 负数、小数、指数、NaN/Infinity、上下溢及超出上述范围的值失败关闭；其中必须明确拒绝 `-0`、
   `-0.0`、`-0e2`、`-1e-324` 和 `1e-320`；
8. 不依赖 Jackson 默认值，而是以不可配置常量冻结技术上限：`MAX_DOCUMENT_BYTES=1_048_576`、
   `MAX_TOKEN_COUNT=100_000`、`MAX_NESTING_DEPTH=64`、`MAX_STRING_LENGTH=262_144`、
   `MAX_PROPERTY_NAME_LENGTH=512`、`MAX_NUMBER_LENGTH=16`；Jackson 错误报告另固定
   `MAX_ERROR_TOKEN_LENGTH=0` 与 `MAX_RAW_CONTENT_LENGTH=0`，只抑制 Jackson 可配置的有界 Source excerpt；这些设置不能阻止
   duplicate decoded property name 或非法 escape character 等所有 input-derived diagnostic 出现在原始 Cause 文本中。

`null`、空数组和 `MAX_DOCUMENT_BYTES` 按调用方 byte[] 的不可变长度在分配前检查；通过后只创建一次防御性内容快照。UTF-8 BOM、
UTF-8 解码、JSON token 和其他所有依赖内容的验证及真正 canonicalization 都只读取该同一快照。Token Count 按 Jackson 已解析 JSON token 计数；深度按 Root Object 为第 1 层的
Object/Array nesting 计数；String 和
Property Name 按解码后 UTF-16 code unit 计数；Number 长度按本 Profile 允许的 ASCII digit 计数。各 Owner 可在调用前施加更小的
业务上限，但不得通过配置扩大 Foundation 公共输入域。任何上限调整都是合同变更，必须先评审并运行边界向量。

Jackson 3.1.5 的 UTF-8 parser 在未转义非 ASCII Property Name 快速路径上会先按 UTF-8 byte 数应用 `maxNameLength`，不能直接用它
证明 512 个 UTF-16 code unit 的公共合同。内部 parser threshold 因此固定为 `3_072`，其数值覆盖最坏 JSON 转义长度
（512 × `\uXXXX` 的 6 bytes），随后对每个解码后的 `PROPERTY_NAME` 以 `String.length()` 精确执行 512/513 门禁。该内部 cap 只防止过早物化超大名称，
不是第二个公共长度合同。测试必须分别覆盖 ASCII、原始 BMP、原始 supplementary 和 escaped-equivalent 名称，防止依赖升级重新引入
按 UTF-8 byte 数误拒绝合法输入。`maxStringLength` 则由 ASCII、原始 BMP 和原始 supplementary 的
262,144/262,145 UTF-16-unit 向量证明其解码后语义；超出 Document 上限的最坏 escaped String 不作为 String-limit
边界向量，较小 escaped-equivalence 另行验证。

严格验证使用 Jackson Core 的 streaming API，但 Jackson 类型不进入公共 API。不可变长度 preflight 之后，验证与真正
canonicalization 都读取唯一防御性快照，避免调用方并发修改，也避免预解析再序列化改变字符串或数字。

本地只读探针已经确认第三方 1.1 会把 `-0` 输出为 `0`、把 escaped lone surrogate 写为 replacement character、不支持
Root primitive，并且在当前 JDK 上会对 `1e-320` 产生非 RFC 8785 输出。选定 JAR 的 SHA-256 探针值为
`ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691`；正式实施时必须从 BOM 解析产物重新记录。
该 1.1 binary 早于 RFC 8785 最终发布，上游 POM 仍以 draft 命名。因此上述前置验证、Root Object 与 Number Profile
公共限制是依赖选型的前置门禁，不是可选加固或实施后的补充验收。任一非法输入不得落到第三方的静默改写路径。

适配器只在第三方 constructor/getter 调用边界捕获其 `IOException` 或 `RuntimeException`，以固定安全外层消息包装并保留同一个原始
Cause；不捕获 `Error`，也不在验证器外围宽泛转换编程错误。原始 Cause 是受保护的内部诊断，可能包含 decoded property name、
非法 escape character 或其他 input-derived diagnostic；模块本身不记录日志，也不把 Cause 消息拼接到固定外层 message。
Cause/message、原始 JSON、属性名、属性值和底层异常文本不得进入第一方 HTTP detail、普通日志、Metric Label 或消息 Payload。
共享 `GlobalExceptionHandler` 只向外返回公共 500 说明，不投影 Cause/message。

## 7. 依赖与包边界

目标依赖图：

```text
paytech-build/paytech-dependencies
  ├─ io.github.erdtman:java-json-canonicalization:1.1
  └─ com.paytech:paytech-json-canonicalization:${paytech.version}

paytech-foundation/paytech-json-canonicalization
  ├─ tools.jackson.core:jackson-core
  └─ io.github.erdtman:java-json-canonicalization

risk-decision ---------------------> paytech-json-canonicalization
commerce-web ----------------------> paytech-json-canonicalization   (后继 Checkout 切片)
platform-support webhook owner ----> paytech-json-canonicalization   (后继 Webhook 切片)
cct / finance BCR owners ----------> paytech-json-canonicalization   (后继按类型登记切片)
```

禁止依赖：

```text
paytech-json-canonicalization -X-> Spring / Servlet / MVC / MyBatis / JDBC / Redis / MQ
paytech-common-core ---------- -X-> paytech-json-canonicalization
paytech-compensation-event --- -X-> paytech-json-canonicalization
commerce-client-api ---------- -X-> paytech-json-canonicalization
```

`paytech-json-canonicalization` 自身不依赖 `paytech-common-core`，避免为异常类型反向引入 Money/Jackson Databind 等无关能力。

第三方版本必须只由 BOM 管理，Foundation 子模块不写版本。实施验收记录许可证、坐标、解析仓库、实际 JAR SHA-256、
`dependency:tree` 和公开 package；不得把临时探针下载的文件提交进仓库，也不 vendoring 第三方源码。

## 8. 首个消费者迁移

Foundation 发布前的首个真实消费者验证是 Risk：

- `RiskDecisionCanonicalizer` 的七类业务 Projection、公开方法和 `CanonicalHash` 保持不变；
- NFC、trim、Decimal、UTC、ID 及 Array 业务排序继续由 Risk-owned Adapter 在生成 Source bytes 并调用 Foundation 之前完成；
- Risk 私有节点只生成合法 JSON Source bytes，不再拥有 RFC 8785 属性排序或底层 canonical bytes；
- Source bytes 交给 Foundation，Risk 再用 JDK SHA-256 生成原 `sha256LowerHex`；
- 现有七组 canonical bytes、UTF-8 hex 和 SHA-256 必须逐字节不变；
- Java 与 Node 共享的 Risk fixture 继续作为跨语言回归，不复制到 Foundation 变成业务 fixture；
- Risk 的依赖边界测试允许且只允许新增对该 Foundation JAR 的单向依赖。
- Risk 的 Owner Projection 参数校验仍可映射为 HTTP 400；一旦 Owner 已生成应当合法的 Source bytes，Foundation
  canonicalization 失败就是系统不变量/内部故障，必须安全映射为 HTTP 500，不得被当前宽泛的 `RuntimeException ->
  InvalidParameterException` 降级为 400。
- 当前仅为测试暴露的 root String canonicalization 入口不能绕过 V1 Object 合同；测试应改用 Object fixture，无真实
  消费者的该入口应删除。

Commerce 与 Platform 不在本设计的首次迁移中创建空 Adapter 或占位调用。它们在各自真实 Checkout/Webhook 纵向切片中直接依赖
已发布 JAR，并建立自己的版本化业务黄金向量。

## 9. Hash 与签名边界

共享模块不计算 Hash。当前业务用法为：

```text
Commerce command request_hash
  = lowercaseHex(SHA-256(canonicalSemanticRequestBytes))

Commerce sourcePayloadHash
  = lowercaseHex(SHA-256(canonicalPublicObjectBytes))

Commerce payloadHash
  = lowercaseHex(SHA-256(canonicalWebhookEnvelopeBytes))

Platform webhook signature
  = HMAC-SHA256(endpointSecret,
      ASCII(unixTimestampSeconds) + "." + canonicalWebhookEnvelopeBytes)
```

Checkout Public Token、Idempotency-Key、Order No、Client Order ID 和 Request ID 不因引入本模块而 Hash。Public Token 仍按
Commerce 已确认设计明文、大小写敏感保存并使用三位 CRC 校验码。

## 10. 测试与兼容门禁

Foundation 测试为 JUnit 5 纯单元测试，不启动 Spring、容器或 Mock 框架。测试预期必须来自 RFC 官方向量或手工冻结 fixture，
不能用被测实现生成 expected bytes。

最低覆盖：

- 把 RFC 8785 主要示例中的 Object、String/literal、escaping 和属性排序预期冻结到一个不含 Profile 外 Number 的
  Root Object 正例；官方完整主要示例因含小数和指数，在 V1 必须整体拒绝；
- Appendix B 中只有落在 Number Profile 内的向量是正例，其余 RFC 合法 Number 在 V1 必须作为拒绝测试；
  Appendix B 不足以证明本 Profile，还必须手工冻结不同十进制位宽、Profile 内的 10 的幂/2 的幂、
  `9007199254740990`、`9007199254740991` 和被拒绝的 `9007199254740992` 向量；
- `null`、Boolean、String escape、control character、合法 U+FFFD 与 UTF-8 输出；所有 primitive 正例必须放在 Root Object 内；
- UTF-16 property order，包括 BMP 与 supplementary character 的区分向量；
- nested Object 递归排序、Array 内 Object 排序和 Array 顺序不变；
- 不同属性插入顺序得到完全相同 bytes；
- canonically equivalent 的 whitespace/escape 文本得到相同 bytes；`1.0` 和 `1e0` 虽与 `1` 数学等价，但在 V1 中必须拒绝；
- 不执行 Unicode normalization，NFC 与 NFD String 输入保持不同；
- duplicate literal property、literal/escaped 等价属性名、嵌套 duplicate、lone surrogate、malformed UTF-8、UTF-8 BOM、非法 JSON、
  第二个 Root、非 Object Root、`null` byte[] 和空输入；
- Number 边界 `0`、`1`、`9007199254740991` 通过；`-1`、小数、指数、`9007199254740992`、NaN/Infinity、
  leading zero 和指数上下溢失败；
- `-0`、`-0.0`、`-0e2`、`-1e-324`、`1e-320` 及相邻 subnormal/notation 边界作为必测回归；
- Document bytes、token count、nesting depth、String UTF-16 长度、Property Name UTF-16 长度和 Number digit 长度分别覆盖上限值与
  `limit + 1`；依赖升级不得改变这些结果；
- 输入 byte[] 未修改、每次返回独立 byte[]，调用方修改已返回数组不影响后续调用；
- 并发调用没有共享可变输出或跨请求污染；
- 第三方类型没有出现在 `api` package 的字段、参数、返回值、throws 或 generic signature；
- 模块的依赖树不包含 Spring、数据库、Redis、MQ 或业务 artifact。

执行顺序：

1. Build BOM 聚焦 `clean verify` 并安装本地 Snapshot，使 Foundation 能无版本声明解析新依赖；
2. 新 Foundation 模块聚焦 RED/GREEN；
3. Foundation 全 Reactor `clean verify` 并安装本地 Snapshot；
4. Risk 聚焦 canonicalizer 测试；
5. Risk 全 Reactor `clean verify`；
6. `scripts/verify-java-foundation.mjs`；
7. `npm --prefix requirements-site test`，确认本技术实现未破坏需求目录。

任何官方向量、严格输入或 Risk 既有 bytes/hash 回归失败都阻止发布。不能以“只有 Hash 不同但业务相同”为理由迁移已冻结的
Risk 数据；算法升级必须由 Risk 自己增加 Hash Schema Version，而不是静默覆盖旧值。

## 11. 发布与升级

实现完成只表示源码和本地门禁通过。消费顺序仍是：

```text
paytech-build BOM
  -> paytech-foundation JAR
  -> Risk / Commerce / Platform consumers
```

正式消费者不得依赖同级源码目录或仅存在于开发者本机缓存的未记录 Snapshot。Nexus 发布、目标环境部署和生产切换需要单独
授权并在状态台账记录真实 artifact/version；本设计不授权发布或部署。

升级第三方依赖时必须把旧、新实现对官方向量、Code-MoR Foundation fixture 和全部已落库业务黄金向量做逐字节差异比较。
任何差异都视为协议变更，先由各 Owner 决定新 Hash Schema Version 和历史读取策略，不能只提升 Maven 版本。

## 12. 非目标

- 不实现 JSON Schema、POJO serialization、业务 DTO mapping 或通用 ObjectMapper；
- 不提供 SHA-256、HMAC、签名验证、Secret 管理或 Key rotation；
- 不修改 Checkout Token、Idempotency-Key 或 Request ID 的存储策略；
- 不把 JCS 加入 Client API Wire contract；
- 不为 Compensation Event 恢复一般化 Canonical Envelope；
- 不创建 Spring Starter、自动配置、配置项、Actuator、日志或指标；
- 不在本切片实现 Commerce Controller、15 表、Webhook、Platform Webhook Sender 或 CCT/Finance Java 能力；
- 不发布 Nexus、不部署 `mor-test` 或生产环境。

## 13. 规范与依赖证据

- [RFC 8785: JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785.html)；
- [RFC 8785 verified errata](https://www.rfc-editor.org/errata/rfc8785)；
- [Maven Central: `io.github.erdtman:java-json-canonicalization:1.1`](https://central.sonatype.com/artifact/io.github.erdtman/java-json-canonicalization/1.1)；
- [upstream source repository](https://github.com/erdtman/java-json-canonicalization)。

外部链接只是规范和依赖来源；Code-MoR V1 的真实公共合同以本文的 Object/Number Profile 和仓库冻结测试为准。

## 14. 文档治理

本设计经确认后，在实施计划和代码前完成以下治理路由：

1. 增加一份窄范围决策记录，冻结独立 JAR、Object/Number Profile、Hash 边界和首个 Risk 消费者；
2. 在 `agent-docs/DOCUMENT_GOVERNANCE.md` 增加该主题的 Owner 路由；
3. 在 `agent-docs/README.md` 增加任务路由，在根 `AGENTS.md` 增加一条薄安全投影；
4. 更正 `agent-docs/TECHNOLOGY_STACK.md` 的 Foundation 模块数量和职责；新模块加入后应为 11 个；
5. Foundation README 及 `CURRENT_IMPLEMENTATION.md` 只在真实实现和验证完成后更新，不得在设计阶段声称已实现。

## 15. 完成判定

只有同时满足以下条件，才能把该能力记为本地实现完成：

1. Build BOM、独立 Foundation 模块及公共 API/内部实现边界一致；
2. 官方 RFC/Errata、非法输入、依赖纯度和并发门禁全部通过；
3. Risk 已通过共享模块生成与现有七组 fixture 完全相同的 canonical bytes/hash；
4. Foundation、Risk、Foundation 静态边界和 Requirements Site 的相关门禁通过；
5. README、Technology Stack 和 `CURRENT_IMPLEMENTATION.md` 只记录实际完成的实现与验证，不声称发布或部署；
6. 实施保留并不混入任何与本能力无关的工作区修改。
