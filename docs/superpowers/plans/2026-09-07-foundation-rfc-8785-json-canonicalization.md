# Foundation RFC 8785 JSON Canonicalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立的 `paytech-json-canonicalization` Foundation JAR，为已批准 JSON Object/Number Profile 产生与 RFC 8785 逐字节一致的 UTF-8 bytes，并将 Risk 现有七类 Hash 迁移到该能力而不改变任何已冻结结果。

**Architecture:** `paytech-build` BOM 唯一管理第三方 1.1 和新 Foundation artifact 版本；`paytech-foundation` 公开一个 byte[] 静态入口与一个安全运行时异常，以 Jackson Core 严格验证后隔离 `org.erdtman.jcs.JsonCanonicalizer`。Risk 继续拥有业务 Projection、NFC、格式化、Array 排序和 SHA-256，只把底层 canonical bytes 交给 Foundation。

**Tech Stack:** Java 25, Maven 3.9.16 Wrapper, Jackson Core 3.1.5（Spring Boot BOM 管理）, `io.github.erdtman:java-json-canonicalization:1.1`, JUnit 5, JDK `MessageDigest`, Node.js 静态门禁。

**Spec:** [`docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md`](../specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)

## Global Constraints

- 执行前必须同时读取上述 Spec、[`agent-docs/decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md`](../../../agent-docs/decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md)、`DEVELOPMENT_CONVENTIONS.md`、`TECHNOLOGY_STACK.md` 和 `UNIT_TEST_GUIDE.md`。
- 工作区包含多个独立 Git 仓库：根仓库、`paytech-build`、`paytech-foundation` 和 `risk-compliance-service`。每次编辑前重新检查对应仓库 `git status --short`，保留并不混入无关修改。
- 当前 `paytech-foundation/paytech-redis-starter/.../RedisResourceLockAutoConfiguration.java` 存在与本任务无关的修改；不编辑、恢复、暂存或提交该文件。
- 未获得用户明确的提交授权时，所有 Commit 步骤都保持未执行；获得授权后仍按四个仓库分别精确 `git add`，不使用递归或全量加入。
- 公共 API 固定为 `Rfc8785JsonCanonicalizer.canonicalizeObject(byte[]) -> byte[]` 和 `JsonCanonicalizationException`；不新增接口、Factory、Spring Bean、POJO/Map/JsonNode/String overload。
- V1 只接受严格 UTF-8 单 Root Object；Number token 必须匹配 `0|[1-9][0-9]*` 并且在 `0..9007199254740991`。Money、Decimal、64-bit ID 和时间继续是 String。
- 固定技术上限为 Document 1,048,576 bytes、Token 100,000、Object/Array nesting 64、String 262,144 UTF-16 code units、Property Name 512 UTF-16 code units、Number 16 digits；Jackson 错误 token/raw-content 引用上限都是 0，只抑制可配置的有界 Source excerpt，不保证原始 Cause 文本没有 duplicate property name 或非法 escape character 等 input-derived diagnostic。
- 底层异常使用固定外层消息 `JSON canonicalization failed` 包装并保留同一个原始 Cause；Cause 是受保护的内部诊断，可能包含输入派生信息，不得投影到第一方 HTTP detail、普通日志、Metric Label 或消息 Payload。Foundation 失败是 500，不是 400。
- Foundation 只返回 canonical bytes，不计算 SHA-256/HMAC、不执行 NFC/trim/大小写/业务 Array 排序、不拥有 Hash Schema/存储/重放语义。
- 新 Foundation 模块的生产依赖只允许 `tools.jackson.core:jackson-core` 和 `io.github.erdtman:java-json-canonicalization`；不依赖 Common Core、Spring、Servlet、MVC、MyBatis、JDBC、Redis、MQ 或业务 artifact。
- Risk 七组 `canonicalJson`、`canonicalUtf8Hex` 和 `sha256` 是不可修改的回归 Oracle；禁止重写 `risk-decision-v1-golden-vectors.json` 以让新实现通过。

---

### Task 1: 冻结 BOM 与跨仓库构建顺序

**Files:**

- Modify: `paytech-build/paytech-dependencies/pom.xml`

**Interfaces:**

- Consumes: Maven Central `io.github.erdtman:java-json-canonicalization:1.1`。
- Produces: 可由 Foundation 和 Risk 无版本声明引用的 BOM 条目 `io.github.erdtman:java-json-canonicalization` 与 `com.paytech:paytech-json-canonicalization:${paytech.version}`。

- [ ] **Step 1: 记录 BOM 缺口**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-build
rg -n "java-json-canonicalization|paytech-json-canonicalization" paytech-dependencies/pom.xml
```

Expected: 命令无匹配，证明新模块尚无版本 Owner；如果已有匹配，先对比本 Spec，不叠加第二份条目。

- [ ] **Step 2: 添加第三方版本和两个受管 artifact**

Add the property:

```xml
<java-json-canonicalization.version>1.1</java-json-canonicalization.version>
```

Add the external managed dependency once:

```xml
<dependency>
    <groupId>io.github.erdtman</groupId>
    <artifactId>java-json-canonicalization</artifactId>
    <version>${java-json-canonicalization.version}</version>
</dependency>
```

Add the Foundation managed dependency beside the other `com.paytech` artifacts:

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-json-canonicalization</artifactId>
    <version>${paytech.version}</version>
</dependency>
```

- [ ] **Step 3: 验证 Effective POM 只解析到一个 1.1 和一个 Foundation 版本**

Run:

```bash
./mvnw -pl paytech-dependencies clean verify
./mvnw -pl paytech-dependencies help:effective-pom \
  -Doutput=paytech-dependencies/target/effective-pom.xml
rg -n -C 3 "java-json-canonicalization|paytech-json-canonicalization" paytech-dependencies/target/effective-pom.xml
```

Expected: Maven PASS；Effective POM 中外部 artifact 精确为 `1.1`，Foundation artifact 精确为 `1.0.0-SNAPSHOT`，且没有重复 dependencyManagement block。

- [ ] **Step 4: 验证并安装整个 Build Reactor**

Run:

```bash
./mvnw clean verify
./mvnw clean install
```

Expected: 两个命令都 PASS；`paytech-parent` 和 `paytech-dependencies` 的新 Snapshot 安装到 Maven Settings 指定的本地仓库。

- [ ] **Step 5: 在获得明确授权后提交 Build 仓库**

```bash
git add paytech-dependencies/pom.xml
git commit -m "build: manage JSON canonicalization dependencies"
```

### Task 2: 以 TDD 实现 Foundation 公共 API 和严格 Object Profile

**Files:**

- Modify: `paytech-foundation/pom.xml`
- Create: `paytech-foundation/paytech-json-canonicalization/pom.xml`
- Create: `paytech-foundation/paytech-json-canonicalization/src/main/java/com/paytech/foundation/jsoncanonicalization/api/Rfc8785JsonCanonicalizer.java`
- Create: `paytech-foundation/paytech-json-canonicalization/src/main/java/com/paytech/foundation/jsoncanonicalization/api/JsonCanonicalizationException.java`
- Create: `paytech-foundation/paytech-json-canonicalization/src/test/java/com/paytech/foundation/jsoncanonicalization/auto/Rfc8785JsonCanonicalizerAutoTest.java`

**Interfaces:**

- Consumes: BOM-managed Jackson Core and `org.erdtman.jcs.JsonCanonicalizer` implementation detail.
- Produces: `public static byte[] Rfc8785JsonCanonicalizer.canonicalizeObject(byte[] jsonUtf8)` and `JsonCanonicalizationException` with fixed safe message.

- [ ] **Step 1: 接入新 Maven 模块，不引入 Common Core 或 Spring**

Add `<module>paytech-json-canonicalization</module>` immediately after `paytech-http-problem` in the Foundation parent. Create the child POM with this exact dependency shape:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>paytech-foundation</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>paytech-json-canonicalization</artifactId>
    <name>PayTech JSON Canonicalization</name>
    <description>Strict RFC 8785 JSON Object Profile canonicalization.</description>

    <dependencies>
        <dependency>
            <groupId>io.github.erdtman</groupId>
            <artifactId>java-json-canonicalization</artifactId>
        </dependency>
        <dependency>
            <groupId>tools.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 先写正向、排序和基本失败测试**

Create `Rfc8785JsonCanonicalizerAutoTest` with these exact cases:

```java
@Test
void test_canonicalizeObjectSortsPropertiesAndPreservesArrays() {
    byte[] actual = Rfc8785JsonCanonicalizer.canonicalizeObject("""
        { "z": 0, "nested": { "b": true, "a": null }, "array": [3, 2, 1] }
        """.getBytes(StandardCharsets.UTF_8));

    assertArrayEquals(
        "{\"array\":[3,2,1],\"nested\":{\"a\":null,\"b\":true},\"z\":0}"
            .getBytes(StandardCharsets.UTF_8),
        actual
    );
}

@Test
void test_canonicalizeObjectUsesUtf16PropertyOrder() {
    assertCanonical(
        "{\"\\uE000\":\"private-use\",\"\\uD800\\uDC00\":\"supplementary\"}",
        "{\"𐀀\":\"supplementary\",\"\":\"private-use\"}"
    );
}

@Test
void test_canonicalizeObjectPreservesUnicodeNormalizationAndValidReplacementCharacter() {
    assertCanonical("{\"value\":\"é\"}", "{\"value\":\"é\"}");
    assertCanonical("{\"value\":\"e\\u0301\"}", "{\"value\":\"é\"}");
    assertCanonical("{\"value\":\"�\"}", "{\"value\":\"�\"}");

    byte[] nfc = canonical("{\"value\":\"é\"}");
    byte[] nfd = canonical("{\"value\":\"e\\u0301\"}");
    assertFalse(Arrays.equals(nfc, nfd));
}
```

`assertCanonical` must compare the hand-frozen expected UTF-8 byte array with `assertArrayEquals`; decoding actual bytes with a replacement decoder is not an exact-byte assertion.

Add an RFC Appendix B table-driven test. Of its non-empty JSON representations, only `0` is inside this V1 Profile and is a positive case. Every other Appendix B representation must be embedded as `{"n":<token>}` and rejected, including:

```text
5e-324, -5e-324, 1.7976931348623157e+308, -1.7976931348623157e+308,
9007199254740992, -9007199254740992, 295147905179352830000,
9.999999999999997e+22, 1e+23, 1.0000000000000001e+23,
999999999999999700000, 999999999999999900000, 1e+21,
9.999999999999997e-7, 0.000001,
333333333.3333332, 333333333.33333325, 333333333.3333333,
333333333.3333334, 333333333.33333343,
-0.0000033333333333333333, 1424953923781206.2
```

The Appendix B minus-zero row serializes its IEEE-754 value as `0`; it does not authorize the source token `-0`. Name the explicit `-0` rejection after verified RFC Errata 7920. Errata 6292 is an editorial section-reference correction and needs no separate behavioral vector.

Add a second hand-frozen table for canonical non-negative integer forms. Each input is embedded in a Root Object and must output the identical digit token:

```text
0, 1, 2, 9, 10, 99, 100, 999, 1000, 1024, 1048576,
1000000, 1000000000000, 1000000000000000,
4294967296, 281474976710656, 4503599627370496,
9007199254740990, 9007199254740991
```

This covers multiple decimal widths, powers of ten and powers of two without asking the third-party adapter to generate expected values. Add rejection assertions for:

```text
null byte[]
empty byte[]
UTF-8 BOM + {}
malformed UTF-8
[], "value", 1, true, null
{"a":1,"a":2}
{"a":1,"\u0061":2}
{"nested":{"a":1,"a":2}}
{"value":"\uD800"}
{"\uDC00":"value"}
{"n":-1}, {"n":1.0}, {"n":1e0}, {"n":01}
{"n":9007199254740992}, {"n":-0}, {"n":-0.0}, {"n":-0e2}
{"n":-1e-324}, {"n":1e-320}
{"n":NaN}, {"n":Infinity}, {"n":-Infinity}
JSON comment, single quote, unquoted name, trailing comma, and two Root values
```

Every rejection must assert `JsonCanonicalizationException`, outer message exactly `JSON canonicalization failed`, and that the outer message does not contain the canary property/value.
Additionally, call the public entry with a duplicate-name canary, assert that the fixed outer message does not contain the canary and that the preserved Cause is non-null. Do not assert that the original Cause text is sanitized: Jackson may include the decoded duplicate name or offending character, so consumers must protect the Cause as internal diagnostics.

- [ ] **Step 3: 运行测试并确认 RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-json-canonicalization -am \
  -Dtest=Rfc8785JsonCanonicalizerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL during test compilation because `Rfc8785JsonCanonicalizer` and `JsonCanonicalizationException` do not exist. A dependency-resolution failure means Task 1 `clean install` was not completed and is not an acceptable RED result.

- [ ] **Step 4: 实现安全异常**

```java
package com.paytech.foundation.jsoncanonicalization.api;

import java.io.Serial;

/** 表示输入不在已批准 JSON Object Profile 内，或底层规范化失败。 */
public final class JsonCanonicalizationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SAFE_MESSAGE = "JSON canonicalization failed";

    public JsonCanonicalizationException() {
        super(SAFE_MESSAGE);
    }

    public JsonCanonicalizationException(Throwable cause) {
        super(SAFE_MESSAGE, cause);
    }
}
```

- [ ] **Step 5: 实现严格验证和受限第三方适配**

Implement `Rfc8785JsonCanonicalizer` as a final static utility. Keep Jackson and third-party types in private members only. The parser factory must be built from explicit constants:

```java
private static final int MAX_DOCUMENT_BYTES = 1_048_576;
private static final long MAX_TOKEN_COUNT = 100_000L;
private static final int MAX_NESTING_DEPTH = 64;
private static final int MAX_STRING_LENGTH = 262_144;
private static final int MAX_PROPERTY_NAME_LENGTH = 512;
private static final int MAX_ENCODED_PROPERTY_NAME_LENGTH = MAX_PROPERTY_NAME_LENGTH * 6;
private static final int MAX_NUMBER_LENGTH = 16;
private static final BigInteger MAX_SAFE_INTEGER = new BigInteger("9007199254740991");
private static final Pattern NUMBER_PATTERN = Pattern.compile("0|[1-9][0-9]*");

private static final JsonFactory JSON_FACTORY = strictJsonFactory();
```

Build the factory without global Jackson overrides:

```java
private static JsonFactory strictJsonFactory() {
    StreamReadConstraints constraints = StreamReadConstraints.builder()
        .maxDocumentLength(MAX_DOCUMENT_BYTES)
        .maxTokenCount(MAX_TOKEN_COUNT)
        .maxNestingDepth(MAX_NESTING_DEPTH)
        .maxStringLength(MAX_STRING_LENGTH)
        .maxNameLength(MAX_ENCODED_PROPERTY_NAME_LENGTH)
        .maxNumberLength(MAX_NUMBER_LENGTH)
        .build();
    ErrorReportConfiguration errors = ErrorReportConfiguration.builder()
        .maxErrorTokenLength(0)
        .maxRawContentLength(0)
        .build();
    JsonFactoryBuilder builder = JsonFactory.builder()
        .streamReadConstraints(constraints)
        .errorReportConfiguration(errors)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION);
    for (JsonReadFeature feature : JsonReadFeature.values()) {
        builder.disable(feature);
    }
    return builder.build();
}
```

The public method must reject `null`, empty, and oversized arrays from immutable array length before allocating. It must then take exactly one defensive content snapshot, perform BOM/UTF-8/JSON and every other content-dependent validation against that snapshot, canonicalize that same snapshot, and return a clone:

```java
public static byte[] canonicalizeObject(byte[] jsonUtf8) {
    if (jsonUtf8 == null || jsonUtf8.length == 0 || jsonUtf8.length > MAX_DOCUMENT_BYTES) {
        throw new JsonCanonicalizationException();
    }
    byte[] source = jsonUtf8.clone();
    validate(source);
    try {
        return new JsonCanonicalizer(source).getEncodedUTF8().clone();
    } catch (IOException | RuntimeException exception) {
        throw new JsonCanonicalizationException(exception);
    }
}
```

Validation order and behavior are fixed:

```java
private static void validate(byte[] jsonUtf8) {
    if (startsWithUtf8Bom(jsonUtf8)) {
        throw new JsonCanonicalizationException();
    }
    validateUtf8(jsonUtf8);
    validateJsonTokens(jsonUtf8);
}
```

- `validateUtf8` creates a fresh `StandardCharsets.UTF_8.newDecoder()`, sets malformed and unmappable actions to `CodingErrorAction.REPORT`, and decodes `ByteBuffer.wrap(jsonUtf8)`; wrap `CharacterCodingException` with its Cause.
- `validateJsonTokens` requires the first token to be `START_OBJECT`, walks until that Root Object closes, then requires `parser.nextToken() == null`.
- For every `PROPERTY_NAME` and `VALUE_STRING`, call `parser.getString()` and reject unpaired UTF-16 surrogate code units without normalizing the returned String. Additionally reject a decoded `PROPERTY_NAME` whose Java `String.length()` exceeds `MAX_PROPERTY_NAME_LENGTH`; the wider 3,072-byte Jackson name cap exists only to avoid prematurely rejecting a legal 512-unit raw/escaped name.
- Only `VALUE_NUMBER_INT` is eligible. Its original token text must match `NUMBER_PATTERN`, contain at most 16 ASCII digits, and parse with `new BigInteger(text)` to a value no greater than `MAX_SAFE_INTEGER`. Reject `VALUE_NUMBER_FLOAT`, `VALUE_EMBEDDED_OBJECT`, `NOT_AVAILABLE`, malformed structure, and any second Root.
- Wrap Jackson parsing/constraint failures in `JsonCanonicalizationException(cause)` and retain the identical original Cause. Zero error-token/raw-content settings suppress only configurable bounded Source excerpts; the Cause may still include duplicate decoded property names or offending characters and must remain protected internal diagnostics. Catch `RuntimeException` only around the third-party constructor/getter call and wrap it with the same safe exception; do not catch `Error` or broadly translate validator/programming failures outside that adapter boundary, and never project Cause/message into first-party HTTP detail, ordinary logs, Metric Labels, or message Payloads.

- [ ] **Step 6: 重跑聚焦测试并确认 GREEN**

Run the Step 3 command again.

Expected: all `Rfc8785JsonCanonicalizerAutoTest` cases PASS; output bytes are exact, all Profile exclusions fail closed, and no Spring context or external infrastructure starts.

### Task 3: 冻结容量边界、并发行为和公共表面

**Files:**

- Create: `paytech-foundation/paytech-json-canonicalization/src/test/java/com/paytech/foundation/jsoncanonicalization/auto/Rfc8785JsonCanonicalizerBoundaryAutoTest.java`
- Create: `paytech-foundation/paytech-json-canonicalization/src/test/java/com/paytech/foundation/jsoncanonicalization/auto/JsonCanonicalizationContractAutoTest.java`
- Modify only if a test exposes a defect: `paytech-foundation/paytech-json-canonicalization/src/main/java/com/paytech/foundation/jsoncanonicalization/api/Rfc8785JsonCanonicalizer.java`
- Modify only if a test exposes a defect: `paytech-foundation/paytech-json-canonicalization/src/main/java/com/paytech/foundation/jsoncanonicalization/api/JsonCanonicalizationException.java`

**Interfaces:**

- Consumes: Task 2 public API and immutable constants.
- Produces: exact `limit`/`limit + 1`, defensive-copy, concurrency, exception and reflection gates; it does not add another public type.

- [ ] **Step 1: 先写每个上限的双边测试**

Create `Rfc8785JsonCanonicalizerBoundaryAutoTest` with small helper methods that generate test input in memory. Freeze these exact cases:

```text
Document bytes: {} followed by 1,048,574 ASCII spaces = 1,048,576 accepted;
                one additional space = 1,048,577 rejected.
Token count:    {"values":[0,...]} with 99,995 zero values = 100,000 tokens accepted;
                99,996 zero values = 100,001 tokens rejected.
Nesting depth:  Root Object + 63 nested Arrays = 64 accepted;
                Root Object + 64 nested Arrays = 65 rejected.
String length:  for each of ASCII raw, BMP raw and supplementary raw forms,
                exactly 262,144 decoded UTF-16 code units accepted; 262,145 rejected.
Property name:  for each of ASCII raw, BMP raw, supplementary raw and escaped forms,
                exactly 512 decoded UTF-16 code units accepted; 513 rejected.
Number length:  9007199254740991 (16 digits) accepted;
                10000000000000000 (17 digits) rejected.
```

For every rejected case assert the fixed outer message and absence of the input canary. For accepted cases assert the exact compact canonical output; do not derive expected canonical bytes by invoking the third-party implementation.

Use `"a".repeat(262_144)`, `"é".repeat(262_144)` and `"😀".repeat(131_072)` as the three accepted String values, with one extra same-kind UTF-16 unit (or one ASCII unit after the supplementary pairs) for rejection. Use `"a".repeat(512)`, `"é".repeat(512)`, `"😀".repeat(256)` and `"\\u0061".repeat(512)` as the four accepted Property Name shapes. Their `limit + 1` counterparts are respectively one additional ASCII/BMP unit, 256 supplementary characters plus one ASCII unit, and one additional escape. Also assert raw and escaped representations of the same 512-unit name canonicalize to identical bytes. This is mandatory because Jackson 3.1.5's UTF-8 field-name fast path counts raw bytes before decoding; `.maxNameLength(512)` would incorrectly reject legal BMP/supplementary names.

- [ ] **Step 2: 增加官方语义与防御性回归**

In the same test class add:

- a frozen, Root-Object adaptation of the RFC 8785 Section 3.2.2 example covering `null`, Boolean, control escaping, quotation/backslash escaping, nested sorting and Array preservation while excluding Profile-forbidden Number forms;
- rejection of the unmodified RFC example because it contains decimal/exponent Number tokens, proving the Profile is narrower without changing RFC output semantics inside the accepted subset;
- the exact RFC 8785 Section 3.2.3 property-order sample (`CR`, `1`, U+0080, `ö`, Euro, grinning-face, U+FB33 order) with a hand-frozen canonical JSON expectation;
- equivalent whitespace and escaped-property inputs producing byte-identical output;
- two different property insertion orders producing byte-identical output;
- input-array equality before/after the call;
- mutation of one returned byte array not changing either a second call or a previously obtained independent return;
- 256 parallel invocations with different source insertion order all producing the same frozen bytes.

- [ ] **Step 3: 运行容量/并发测试并确认 RED，然后只修实现缺口**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-json-canonicalization -am \
  -Dtest=Rfc8785JsonCanonicalizerBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected RED: at least one newly frozen limit, defensive-copy or concurrency assertion fails if Task 2 omitted the corresponding boundary. If the complete Task 2 implementation already satisfies every new test, record that fact instead of introducing an artificial production defect. Make only the minimum change required by a real failure.

- [ ] **Step 4: 先写公共 API 和异常契约反射测试**

Create `JsonCanonicalizationContractAutoTest` and assert:

```text
Rfc8785JsonCanonicalizer is public and final, has no public constructor,
and exposes exactly one public declared method:
  static byte[] canonicalizeObject(byte[])

JsonCanonicalizationException is public and final and exposes exactly:
  JsonCanonicalizationException()
  JsonCanonicalizationException(Throwable)

Both constructors use "JSON canonicalization failed".
The cause constructor retains the identical Throwable instance.
No public/protected field, constructor, method parameter, return, throws,
generic type or superclass signature mentions tools.jackson or org.erdtman.
```

Also assert that a representative Jackson syntax failure is wrapped with a non-null Cause, while direct precondition failures keep the same safe outer message. Reflection checks must inspect the declared public/protected surface recursively, not the private implementation fields.

- [ ] **Step 5: 运行新模块的完整测试**

Run:

```bash
./mvnw -pl paytech-json-canonicalization -am test
```

Expected: all three `*AutoTest` classes PASS; no Spring context, socket, database, Redis, MQ, temporary committed fixture or mutable shared state is used.

### Task 4: 把模块、依赖纯度和选定 binary 纳入静态门禁

**Files:**

- Modify: `scripts/verify-java-foundation.mjs`

**Interfaces:**

- Consumes: Build BOM and Foundation Maven/source layout.
- Produces: repository-level failure when the module disappears, dependency versions drift, API implementation gains a framework dependency, or a high-fan-out Foundation module starts re-exporting JCS.

- [ ] **Step 1: 先运行现有验证器并记录它尚未识别新模块**

Run from the root repository:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/verify-java-foundation.mjs
```

Expected: the current script may still PASS because it has no JCS assertions. This is the static-test RED: temporarily removing the new module from a local in-memory copy or adding a deliberately failing expected artifact assertion must make the new assertion fail; do not mutate production POMs merely to manufacture RED.

- [ ] **Step 2: 扩展文件、Reactor、BOM 和 artifact 门禁**

Update the verifier so all of the following are required exactly once:

- the new child POM, the two production Java files, and all three test classes in `requiredFiles`;
- all eleven child POMs in `modulePoms` (also add the currently omitted existing `paytech-http-client-support` and `paytech-mybatis-support` entries), and `<module>paytech-json-canonicalization</module>` in the Foundation Reactor;
- `com.paytech:paytech-json-canonicalization:${paytech.version}` in managed Foundation artifacts;
- `io.github.erdtman:java-json-canonicalization:${java-json-canonicalization.version}` with property value exactly `1.1` and exactly one managed dependency block;
- `paytech-json-canonicalization` in `expectedArtifactIds`.

Use the existing `tagValue(...)` and `dependencyBlocks(...)` helpers. Do not add another XML parser or shell out from the verifier.

- [ ] **Step 3: 增加 opt-in 和生产依赖纯度门禁**

Parse the new child POM and require its non-test dependency set to equal exactly:

```text
io.github.erdtman:java-json-canonicalization
tools.jackson.core:jackson-core
```

Require both entries to omit a local `<version>`. Scan its production source imports and fail on Spring, Servlet, MyBatis, JDBC, Redis, MQ and any `com.paytech` business package. Separately assert that none of these high-fan-out Foundation POMs declares `paytech-json-canonicalization`:

```text
paytech-common-core
paytech-http-problem
paytech-http-client-support
paytech-mybatis-support
paytech-snowflake-id
paytech-mvc-web-starter
paytech-caffeine-starter
paytech-redis-starter
paytech-compensation-event
paytech-xxl-job-starter
```

The new module may import `tools.jackson` and `org.erdtman` privately; only its public surface is forbidden from exposing them, which remains the responsibility of `JsonCanonicalizationContractAutoTest`.

- [ ] **Step 4: 证明新门禁会失败再恢复 GREEN**

After confirming the BOM hunk belongs only to this task, use `apply_patch` to change only the temporary value `1.1 -> 1.2`, run the verifier and require a non-zero exit naming the approved-version drift, then use `apply_patch` to restore `1.2 -> 1.1`. Do not use `git checkout`, `git reset` or a whole-file replacement for this negative check. Rerun:

```bash
node scripts/verify-java-foundation.mjs
```

Expected: PASS with explicit checks for the 1.1 version, the eleventh Foundation module, exact production dependencies and non-transitive opt-in boundary.

- [ ] **Step 5: 审计实际依赖树和选定 JAR**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-json-canonicalization dependency:tree -Dscope=compile
/usr/bin/shasum -a 256 /Users/kenny/.m3/repository/io/github/erdtman/java-json-canonicalization/1.1/java-json-canonicalization-1.1.jar
```

Expected: the module declares exactly Jackson Core and the erdtman implementation as direct production dependencies. The resolved compile tree has no additional compile/runtime library because the selected erdtman POM has only a test-scoped JUnit dependency and Jackson Core 3.1.5 is self-contained; in all cases it contains no Spring, PayTech Common, Servlet, database, Redis or MQ artifact. The selected JAR digest is:

```text
ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691
```

If Maven Settings resolves a different local repository, first obtain the path with `./mvnw help:evaluate -Dexpression=settings.localRepository -q -DforceStdout` and run the checksum against that resolved file. Inspect the resolved POM/license (`Apache License, Version 2.0`), `_remote.repositories` origin marker, JAR manifest, and `jar tf` output; record coordinate, repository, license and public package `org.erdtman.jcs` in the verification evidence. A different digest, license, coordinate or unexpected production dependency blocks implementation and requires dependency investigation; do not update the expected evidence reflexively.

- [ ] **Step 6: 全量验证 Foundation，但只定向安装新模块**

Run:

```bash
./mvnw clean verify
./mvnw -pl paytech-json-canonicalization -am clean install
```

Expected: the full Reactor verification PASS, then the targeted install Reactor contains only the Foundation parent/new module and installs the new Snapshot for Task 5. Do not run a full Foundation `install`: the unrelated dirty Redis source must not overwrite the local `paytech-redis-starter` Snapshot used by other work. If that unrelated source causes the full verification to fail, compare with its owner before changing it; the JCS implementation remains incomplete until a clean full-Foundation verification can be evidenced.

- [ ] **Step 7: 在获得明确授权后提交 Foundation 仓库**

From `paytech-foundation`, inspect the exact diff and stage only:

```bash
git add pom.xml paytech-json-canonicalization
git diff --cached --check
git commit -m "feat: add strict JSON canonicalization"
```

Do not stage the unrelated `RedisResourceLockAutoConfiguration.java` modification.

### Task 5: 以 Risk 七组冻结 Hash 验证首个真实消费者

**Files:**

- Modify: `risk-compliance-service/risk-decision/pom.xml`
- Modify: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerImpl.java`
- Modify: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidatorImpl.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domain/RiskDecisionCanonicalizerAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domain/DecisionReleaseStaticValidatorAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerTestAccess.java`
- Verify unchanged: `risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json`
- Verify unchanged: `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Verify unchanged: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- Verify unchanged: `scripts/pdmaner/risk-compliance-initial-data.test.mjs`

**Interfaces:**

- Consumes: `com.paytech:paytech-json-canonicalization` directly from `risk-decision`.
- Produces: unchanged seven Risk public canonicalization methods and `CanonicalHash`; Foundation owns only the final JSON bytes, while Risk retains projection, NFC, formatting, Array ordering, schema tags and SHA-256.

- [ ] **Step 1: 冻结迁移前 Oracle 和文件校验和**

Run before editing Risk:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
/usr/bin/shasum -a 256 risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json
node -e "const f=require('./risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json'); for(const v of f.canonicalProjections) console.log(v.id, v.expected.sha256)"
```

Expected fixture SHA-256:

```text
9a5ad56d2edc300a1223afa1df22d88f0334f772a9ca2796500cfc22c30803e5
```

Expected seven business hashes:

```text
rule-set-version-primary        c0c264fc814e152e58504c65ebb3b310e3821ad9c199fedf97a6870eba87871f
decision-point-version-primary  764b0f16fa0d238897476baa49fe4a66bbcb911e13cd3318bcc8ed237d35f691
decision-release-primary        edfecb39902e0d0a01f111bbc7fbefb626d4b7ee0756c77146c4c39fe5e71ba1
create-rule-set-primary         3f03a296cd7515c59713b6e86f44b3144154a5801174c58b499e9516ae24d9a8
freeze-rule-set-primary         eaab2898c2553b40850d79b10f38e421735191feaad094b6c10ae05de31f6183
create-test-release-primary     7131d6f878fe9920ac8a4f44b7477252b2d84cc3279f699468ebc7b9076c14fe
validate-test-release-primary   ea405a2ef710738b4fe0007f62e85cba741216409d6590b16d9690ed56f48173
```

- [ ] **Step 2: 先更新消费依赖和边界测试**

Add this versionless direct dependency to `risk-decision/pom.xml`:

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-json-canonicalization</artifactId>
</dependency>
```

Update the `decision` branch of `RiskModuleBoundaryAutoTest.test_businessLibrariesHaveOnlyApprovedDependencies` so the approved artifact set adds only `paytech-json-canonicalization`. Add a classpath assertion for `com/paytech/foundation/jsoncanonicalization/api/Rfc8785JsonCanonicalizer.class`; keep all existing forbidden-infrastructure assertions.

- [ ] **Step 3: 写迁移 RED，覆盖 Object-only 和 500 边界**

Update `RiskDecisionCanonicalizerAutoTest` before production code:

- retain all seven `assertExpected(...)` calls unchanged;
- change both lone-surrogate checks to `canonicalizeKeyValueObject(...)` with the surrogate in a value, then delete uses of `canonicalizeString(...)`;
- add a duplicate decoded key case (`"a"` and `"\u0061"`) that expects `JsonCanonicalizationException` with the fixed safe message;
- construct a valid `RuleSetVersionProjection` whose `ruleSetName` is `"x".repeat(262_145)` and assert `JsonCanonicalizationException`, not `InvalidParameterException`; this proves a Foundation invariant failure cannot be translated into the public 400 category;
- retain current explicit invalid-owner-input assertions as `InvalidParameterException`, including null top-level projection, null rules list and null required enum;
- retain defensive-byte, UTF-16 order, NFC, numeric-ID tie-break and semantic Array-permutation assertions.

In `DecisionReleaseStaticValidatorAutoTest`, start from `validClosure()`, replace only its `RuleSetVersionProjection.ruleSetName` with `"x".repeat(262_145)`, and assert `validator.validate(...)` throws `JsonCanonicalizationException` with the fixed safe message. It must not return a `REFERENCE_INTEGRITY/CHECK_INPUT_INVALID` row; otherwise Release validation would persist an internal canonicalization defect as a business static-check result.

Delete `canonicalizeString(...)` from `RiskDecisionCanonicalizerTestAccess`; the test helper must expose Object input only.

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw -pl risk-test -am \
  -Dtest=RiskDecisionCanonicalizerAutoTest,DecisionReleaseStaticValidatorAutoTest,RiskModuleBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected RED: the oversized source is still caught by the broad `RuntimeException -> InvalidParameterException` mapping and/or the private writer still owns Object sorting. A dependency-resolution failure means Task 4 `clean install` was not completed and is not an acceptable RED result.

- [ ] **Step 4: 最小迁移 Risk writer 到 Foundation bytes**

Make these constrained production changes:

```java
private static byte[] canonicalBytes(JNode node) {
    StringBuilder source = new StringBuilder();
    node.write(source);
    return Rfc8785JsonCanonicalizer.canonicalizeObject(
        source.toString().getBytes(StandardCharsets.UTF_8));
}

private static CanonicalHash hash(String schema, JNode node) {
    byte[] bytes = canonicalBytes(node);
    try {
        String digest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes));
        return new CanonicalHash(schema, bytes, digest);
    } catch (NoSuchAlgorithmException exception) {
        throw new IllegalStateException(exception);
    }
}
```

- `ObjectNode.write(...)` writes properties in the projection's existing insertion order and no longer sorts them or claims RFC ownership; Foundation recursively sorts and rejects duplicate decoded names.
- Keep Risk's existing `UTF16` comparator where it expresses a business Array/tie-break order; do not use it for JSON Object canonicalization.
- Delete `canonicalizeTestString(...)`; `canonicalizeTestObject(...)` remains package-private test access and still produces a Root Object.
- Preserve every public method and its existing `project(() -> ...)` shape. Narrowly exempt the Foundation exception before the existing Owner-side fallback so prior invalid-projection behavior remains unchanged:

```java
private static CanonicalHash project(Supplier<CanonicalHash> operation) {
    try {
        return operation.get();
    } catch (JsonCanonicalizationException exception) {
        throw exception;
    } catch (InvalidParameterException exception) {
        throw exception;
    } catch (RuntimeException exception) {
        throw invalid("规范投影无效");
    }
}
```

- Do not add new enum/list validation helpers in this migration. Existing null list, missing required enum and other malformed Owner Projection cases must continue through the unchanged `InvalidParameterException` behavior.
- No caller may translate `JsonCanonicalizationException` into `InvalidParameterException`, a Static Violation or another 4xx/business result. A boundary with an existing broad catch may catch it only to rethrow the identical instance toward the shared unexpected-error path and safe RFC 9457 HTTP 500 response.

The existing Release validator is one additional translation boundary and must be changed explicitly. Before its current `catch (RuntimeException)` add:

```java
} catch (JsonCanonicalizationException exception) {
    throw exception;
} catch (RuntimeException exception) {
    violations = List.of(new StaticViolation("CHECK_INPUT_INVALID", "closure", null, null, null));
}
```

Keep the fallback for ordinary malformed closure data so the eight-check business behavior does not change. Search all production references to `RiskDecisionCanonicalizer` together with `catch (RuntimeException)` and confirm no other caller converts `JsonCanonicalizationException` into a 4xx/business result; existing callers that wrap unexpected failures as `ErrorException` remain valid 500 paths.

- [ ] **Step 5: 重跑 Risk 聚焦测试并逐字节核对 Oracle**

Run the Step 3 Maven command again, then rerun both Step 1 commands.

Expected: tests PASS; the fixture file digest and all seven printed SHA-256 values are byte-for-byte identical to the frozen values. If any differs, stop and inspect the source projection/canonical bytes; do not rewrite the fixture or introduce a new Hash Schema in this task.

- [ ] **Step 6: 验证 JavaScript Oracle 未漂移**

Run from the root repository:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
node --test \
  scripts/pdmaner/risk-decision-engine-contract.test.mjs \
  scripts/pdmaner/risk-compliance-initial-data.test.mjs
```

Expected: PASS without modifying either JavaScript implementation/test or the shared fixture. Their role is an independent cross-language Oracle, not a second Foundation adapter.

- [ ] **Step 7: 审计 Risk 依赖树并运行完整 Reactor**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw -pl risk-decision dependency:tree -Dincludes=com.paytech:paytech-json-canonicalization
./mvnw clean verify
```

Expected: `risk-decision -> paytech-json-canonicalization` is a direct versionless dependency; no other Risk business module declares it directly, and the full Reactor passes. If local infrastructure prerequisites fail, follow `UNIT_TEST_GUIDE.md`, report the exact precondition separately, and do not convert an unrun full suite into a PASS claim.

- [ ] **Step 8: 在获得明确授权后提交 Risk 仓库**

Stage exactly the seven modified Risk files listed above, verify the golden fixture and the three JavaScript Oracle files are unstaged and unchanged, then:

```bash
git diff --cached --check
git commit -m "refactor: use Foundation JSON canonicalization"
```

### Task 6: 全量验收并只记录真实实现状态

**Files:**

- Modify after successful implementation only: `paytech-foundation/README.md`
- Modify after successful implementation only: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Verify: `AGENTS.md`
- Verify: `agent-docs/DOCUMENT_GOVERNANCE.md`
- Verify: `agent-docs/README.md`
- Verify: `agent-docs/TECHNOLOGY_STACK.md`
- Verify: `agent-docs/decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md`
- Verify: `docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md`
- Verify: `docs/superpowers/plans/2026-09-07-foundation-rfc-8785-json-canonicalization.md`
- Verify: `scripts/verify-java-foundation.mjs`

**Interfaces:**

- Consumes: evidence from Tasks 1–5.
- Produces: an auditable local implementation record that distinguishes source implementation, local verification, local Maven installation, Nexus publication and environment deployment.

- [ ] **Step 1: 运行每个仓库的 whitespace 和改动范围检查**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git diff --check
git -C paytech-build diff --check
git -C paytech-foundation diff --check
git -C risk-compliance-service diff --check
git status --short
git -C paytech-build status --short
git -C paytech-foundation status --short
git -C risk-compliance-service status --short
```

Expected: no whitespace error. Every JCS change is within the file lists above. Unrelated pre-existing root/test-environment and Foundation Redis changes remain present but untouched and unstaged.

- [ ] **Step 2: 重跑发布顺序门禁**

Run in order:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-build
./mvnw clean verify
./mvnw clean install

cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw clean verify
./mvnw -pl paytech-json-canonicalization -am clean install

cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/verify-java-foundation.mjs
rg -l '<artifactId>paytech-json-canonicalization</artifactId>' -g 'pom.xml' | sort

cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw clean verify

cd /Users/kenny/Desktop/Workspace/Code-MoR
node --test \
  scripts/pdmaner/risk-decision-engine-contract.test.mjs \
  scripts/pdmaner/risk-compliance-initial-data.test.mjs
npm --prefix requirements-site test
```

Expected: every command PASS. Foundation is fully verified but only the new module's targeted Reactor is installed, so the unrelated dirty Redis implementation does not overwrite its local Snapshot. The repository-wide POM scan prints exactly `paytech-build/paytech-dependencies/pom.xml`, `paytech-foundation/paytech-json-canonicalization/pom.xml`, and `risk-compliance-service/risk-decision/pom.xml`; Commerce Client API, Gateway, BFFs and non-consumers remain untouched. Record actual test totals and any local-infrastructure prerequisites from output rather than copying historical counts. These commands install local Snapshots only; they do not publish Nexus artifacts or deploy an application.

- [ ] **Step 3: 更新 Foundation README**

Only after Step 2 evidence exists:

- add `paytech-json-canonicalization` as the eleventh module;
- document the single byte[] API, Root Object/Number Profile, strict limits and direct opt-in usage;
- state that canonicalization does not own JSON projection, SHA-256/HMAC or business normalization;
- record the actually resolved dependency coordinates/checksum and actual local test results;
- state explicitly whether the Snapshot is only locally installed, and do not claim Nexus publication or deployment.

- [ ] **Step 4: 更新唯一当前状态台账**

Edit `agent-docs/CURRENT_IMPLEMENTATION.md` carefully because it may contain unrelated concurrent changes. Add a narrow `JSON-CANONICALIZATION-01` entry and recent-verification section containing only observed facts:

```text
Implemented: BOM entries, independent Foundation JAR, strict Object Profile, Risk migration.
Verified: exact commands and actual test totals/checksum/golden-vector invariance.
Installed: local Maven repository only, if Task 1/4 install actually ran.
Published: no, unless separately authorized and evidenced.
Deployed: no; this is a library and no consumer environment was changed.
Remaining: Commerce/Platform/CCT/Finance adopt only in their real future slices.
```

Do not copy implementation status into the design, decision, governance router or `AGENTS.md`.

- [ ] **Step 5: 验证文档链接、禁止占位语句和最终 diff**

Run a relative-link check for every Markdown file touched by this task and verify every local target exists. Then run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
rg -n "T[B]D|T[O]DO|implement l[a]ter|fill i[n]|similar t[o]|appropriate error handlin[g]" \
  agent-docs/decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md \
  docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md \
  docs/superpowers/plans/2026-09-07-foundation-rfc-8785-json-canonicalization.md
git diff --check
```

Expected: all local links resolve, placeholder search has no matches, and diff check passes.

- [ ] **Step 6: 做需求到证据的最终对照**

Confirm each design completion condition has one concrete artifact and one executed test/evidence line:

```text
BOM/version Owner       -> Task 1 POM + Effective POM
minimal public API      -> Task 2 source + Task 3 reflection test
strict accepted domain  -> Task 2 invalid vectors + Task 3 limit pairs
dependency isolation    -> Task 4 verifier/tree/checksum
Risk first consumer     -> Task 5 direct dependency + seven immutable vectors
HTTP failure category   -> Task 5 canonicalizer + Release-validator passthrough regressions + shared MVC mapping
repository health       -> Task 6 full builds/static/requirements checks
state honesty           -> README + CURRENT_IMPLEMENTATION only
```

Any missing or failed evidence remains an explicit open item; do not mark the design locally implemented merely because source files exist.

- [ ] **Step 7: 在获得明确授权后提交根仓库文档和验证器**

The root working tree already contains unrelated edits, including an existing modification to `CURRENT_IMPLEMENTATION.md`. Use `git add -p` for overlapping files and explicit paths for new files. Stage only this task's governance/design/plan/verifier/status hunks, inspect `git diff --cached --check` and `git diff --cached --stat`, then:

```bash
git commit -m "docs: govern Foundation JSON canonicalization"
```

If this task's `CURRENT_IMPLEMENTATION.md` hunk cannot be isolated safely from another owner's changes, leave the root commit pending instead of staging the whole file.

## Completion Gate

The implementation is complete only when every non-conditional checkbox above has evidence, the two immutable Risk fixture checks still match, every mandatory build/test/static gate actually passes, and no unrelated workspace change was absorbed. An environment or dirty-worktree gap must be recorded as an open blocker and permits only a narrower “source implemented, verification incomplete” statement; it never satisfies this completion gate. Commits, Nexus publication and deployment remain separate actions requiring their own user authorization.
