# 任务卡 S4：channel-connector 新增 Cybersource 签名所需的 3 个脚本函数

- 需求：[requirement.md](../requirement.md)；设计：[technique.md 第 1.2、3、4 节](../technique.md)（决定 C2、C3 与第 4 节函数规格，已经用户确认）
- 基线：main `1d39cd94`；只改 `paytech/channel-connector/**`，不改其他模块、表、接口、Nacos。
- 不提交、不推送；完成后报告 diff 与测试结果，由主会话复核。

## 1. 先读

- `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- `agent-docs/UNIT_TEST_GUIDE.md`
- `agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- 现有实现与测试：
  - `ScriptFunctions.java`
  - `RegisteredFunctionCatalog.java`
  - `ScriptSigningFunctionsTest.java`
  - `StandardApiSignedChannelTest.java`
  - `src/test/resources/externalapi/script/*.av`

## 2. 实现

在 `paytech-channel-connector-protocol/.../domainservice/function/ScriptFunctions.java` 中新增 3 个函数，并在 `RegisteredFunctionCatalog` 登记签名与说明，写法照现有函数：

| 函数 | 签名 | 语义 |
|---|---|---|
| `sha256Base64` | `(text) → String` | UTF-8 字节的 SHA-256，标准 Base64（带 `=`）；参数类型不对时，按现有 `text(...)` 规则抛固定文字的异常 |
| `hmacSha256Base64DecodedKey` | `(key: 受控句柄, message) → String` | 在 `handle.apply` 内对密钥做 Base64 解码（`java.util.Base64.getDecoder()`），计算 HmacSHA256，输出标准 Base64。key 不是受控句柄时，按现有 `handle(...)` 规则拒绝；密钥不是合法 Base64 时，抛 `ExecutionErrorException("hmacSha256Base64DecodedKey 的密钥不是合法 Base64")`，消息里不带任何值 |
| `nowRfc1123` | `() → String` | 用注入的 `Clock`（与 `nowRfc3339` 同一个），格式 `EEE, dd MMM yyyy HH:mm:ss 'GMT'`，`Locale.US`，`ZoneOffset.UTC`；日期补零到两位（`Wed, 21 Oct 2026 08:30:00 GMT`） |

注意：
- 新增常量、类和注释的风格与现有代码保持一致，用中文 JavaDoc；
- `all(Clock)` 中注册新函数；
- 不改动已有函数的行为。

## 3. 测试

测试只放在 `paytech-channel-connector-test`，遵守 UNIT_TEST_GUIDE：不使用 Mockito 打桩，外部 HTTP 走已有测试端点机制。

1. **`ScriptSigningFunctionsTest` 增加纯 Java 合同测试。** 以下是对接包 `test-vectors.json` 里本机随机生成的测试专用密钥和样本（`testOnly: true`，与任何真实商户无关），可以写入测试：
   - `sha256Base64('{"a":1}')` → `AVq9f1zFei3ZS3WQ8ErYCEJzkF7jPsXOvq5iJ2qX+GI=`
   - `hmacSha256Base64DecodedKey(<句柄：v9EJBDh1IuYowEzGJ4Wr74fy/eFcxHs+tzby2ypVChc=>, "host: api.cybersource.com\ndate: Wed, 21 Oct 2026 08:30:00 GMT\nrequest-target: post /pts/v2/payments\ndigest: SHA-256=AVq9f1zFei3ZS3WQ8ErYCEJzkF7jPsXOvq5iJ2qX+GI=\nv-c-merchant-id: testonly_merchant")` → `dEKh5lsiIfAITWPFPZu9sAl8grzT2UrM1BdCQVEhh2U=`
   - 固定时钟 `2026-10-21T08:30:00Z` 下，`nowRfc1123()` → `Wed, 21 Oct 2026 08:30:00 GMT`；再加一个个位日期（如 2026-03-05）验证补零。
   - 非法 Base64 密钥被拒绝，异常消息不含密钥；key 传普通字符串被拒绝。
   - 目录（catalog）测试中能看到 3 个新函数。
2. **`StandardApiSignedChannelTest` 增加一条端到端用例**，从真实执行入口出发：
   - 原子 API 配一个 REQUEST 脚本（放 `src/test/resources/externalapi/script/`，如 `cybs-signature.av`），按 Cybersource 方式生成 `Digest`、`Date`、`v-c-merchant-id`、`Signature` 四个头；
   - 脚本里的 `sealJsonBody` → `sha256Base64` → 5 行签名串 → `hmacSha256Base64DecodedKey`；
   - 测试 HTTP 端点收到请求后，用测试代码独立按同一算法重算，对实际收到的 body 字节核对 `Digest`，并核对 `Signature`；
   - 执行轨迹或日志中不出现密钥原值。
   - 可参照该类现有用例的搭建方式；路径和 host 作为脚本参数传入（设计 C2）。

## 4. 验证

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`
- Maven 本地仓库为 `~/.m3/repository`，使用离线模式（`-o`）。
- 测试依赖本机 `local-environment` 的 MySQL/Redis，密码从 `local-environment/.env.local` 读取，只读取需要的键，不打印。
- 只跑 channel-connector 的测试模块（带 `-am` 编译依赖），**不要**跑整个 paytech reactor 的测试：其他会话共用本机库。
- 所有命令在前台执行，不要后台等待。
- 报告内容：
  - 改动文件清单；
  - 新增测试数与结果；
  - channel-connector 测试模块的总通过数（与基线比较，基线约 375）；
  - 任何失败要附原文。

## 5. 完成标准

- 3 个函数实现并登记；
- 新增测试全部通过，channel-connector 既有测试无回归；
- `git diff` 只涉及 `paytech/channel-connector/**`；
- 未提交。
