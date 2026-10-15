# Vault 数据类型值格式约束（TEXT / JSON / REGEX）

- 日期：2026-10-15
- 状态：**阶段 1 已确认**（2026-10-15，第 2 版：只保留 TEXT、JSON、正则三种，其余格式都用正则；Q1 按建议）
- 主入口：Operations Console（数据类型维护、手动加密）；数据 Owner：Vault（策略与写入校验）
- 前序需求：[Operations 维护 Vault 数据类型与手动加解密](../../20261014/operations-console-Vault数据类型与手动加解密/requirement.md)（数据类型目录、策略入库、人工加/解密）
- 关联：[Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)、[Vault 策略与人工访问决定](../../../agent-docs/decisions/2026-10-14-vault-policy-and-manual-access.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

Vault 的 Protect 目前只检查两件事：值是合法 UTF-8，长度不超过 32KiB（`vault-service/internal/biz/usecase.go`）。值是不是邮箱、是不是 JSON，Vault 都不管，格式全靠调用方。这带来两个问题：

1. **人工录错，要到很晚才暴露。** 运营手动加密渠道账号配置时，如果粘贴了不完整的 JSON，Vault 照样加密并返回 Token。直到 Platform 执行渠道调用、解密时才报“Vault 账号配置结构无效”，离录入已经很远。
2. **STABLE 类型会在没有报错的情况下失去等值匹配。** STABLE 的含义是相同的值得到相同的 Token。比如本机 `EMAIL_ADDRESS_V1` 已开启人工加密，运营录入 `Foo@X.com`，而 Platform 写入的是 `foo@x.com`，两者会得到两个 Token，按 Token 查账号就查不到。

## 2. 目标

1. 每个数据类型声明它接收的值格式。Vault 在**所有** Protect 入口（服务调用和人工加密）上执行同一套校验，不合格的值拒绝写入。
2. 约束由运营在页面上维护，不用改代码：除了不限格式（TEXT）和 JSON，其余格式（邮箱、手机号、长度、字符集、前缀、位数等）一律用正则表达。

## 3. 现状（已核对事实）

| 事实 | 依据 |
|---|---|
| Protect 只校验 UTF-8 与 32KiB 上限 | `vault-service/internal/biz/usecase.go` |
| 策略每 10 秒从数据库全量刷新（`VAULT_POLICY_REFRESH_INTERVAL`） | `vault-service/internal/conf/config.go` |
| 本机 4 个类型：`EMAIL_ADDRESS_V1`（STABLE，人工加/解密均开启）、`PHONE_E164_V1`（STABLE）、`CHANNEL_ACCOUNT_CONFIG_V1`、`NOTIFICATION_PROVIDER_CREDENTIAL_V1`（均为 REFERENCE/SECRET）；test 另有 `TEST_REFERENCE_V1`、`TEST_STABLE_V1` | 2026-10-15 只读查询 `mor_vault.t_vault_data_type` |
| CCT 写入邮箱：去首尾空白、整体小写、Jakarta `@Email` 校验；手机：去掉空格、括号、连字符后匹配 `\+[1-9][0-9]{7,14}` | `ClientDraftPatch` |
| Platform 登录邮箱：NFC、本地部分小写、域名经 `IDN.toASCII` 转 ASCII 后小写 | `OperationsPasswordPolicy` |
| Platform 通知邮箱：去首尾空白、整体小写、只接受 ASCII 地址 | `NotificationInput.canonicalEmail` |
| 渠道账号配置、SES 凭据：调用方解密后要求是 JSON 对象 | `ChannelAccountConfigFunction`、`ChannelAccountConfigReader`、`SesCredentialVault` |

**附带发现：** 三个调用方对同一个 STABLE 类型 `EMAIL_ADDRESS_V1` 的规范化方式并不完全一致。对于 Unicode 域名，CCT 保留 Unicode 原文，Platform 登录则转成 Punycode。同一个邮箱因此可能得到两个 Token。本需求不修正这一点，记作遗留项；第 7 节的邮箱正则对两种写法都放行，避免误拒。

## 4. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 安全/平台管理员 | 新增或编辑数据类型时选择值格式；选正则时填写正则和测试样例 | 保存后在一个刷新周期（约 10 秒）内生效；策略变更记录里能看到格式和正则的前后差异 |
| 运营（手动加密） | 选择数据类型后录入原值 | 页面提示该类型要求的格式；不合格时提示违反的是格式还是正则，不回显原值，也不生成 Token |
| 业务服务（CCT、Platform 等） | 调用 Protect | 合格的值行为不变；不合格时收到明确的 400 错误码，不会拿到 Token |

## 5. 本期范围与不做项

**包含**

- 数据类型新增**值格式**（必填，三选一，默认 `TEXT`）和**正则**（仅值格式为 `REGEX` 时必填，其余格式不允许填写）：

  | 值格式 | 校验内容 |
  |---|---|
  | `TEXT` | 不限格式，只保留现有的 UTF-8 与长度检查 |
  | `JSON` | 严格 JSON，根必须是对象，拒绝重复键（现有 JSON 类调用方都要求对象） |
  | `REGEX` | 按该类型的正则整值匹配 |

- 正则规则：
  - 语法为 Go RE2，最长 256 字符。
  - Vault 自动包成 `^(?:…)$`，按整值匹配。
  - 保存时编译，编译失败拒绝保存。
  - 切换到 `REGEX` 或修改正则时，必须附带至少 1 个“应通过”的样例。样例全部通过才允许保存；样例只用于本次验证，不落库，不进变更记录，也不写日志。
- 校验顺序：现有检查（UTF-8、长度）→ 值格式（JSON 或正则）。服务 Protect 与人工加密走同一段代码。
- **只校验，不规范化**：Vault 不修改原值；不是规范形式就拒绝。
- 格式和正则都可以在页面上修改（用户已选方案 A）：保存即生效，靠必填样例和变更记录兜底；改错了在页面上改回即可。
- 修改只对之后的写入生效：已加密的记录不重新校验，Reveal 不受影响。
- 失败返回 400，错误码区分“不是合法的 JSON 对象”和“不符合正则”。响应、日志、审计中都不出现原值。
- 现有数据类型按第 7 节的建议初始化值格式与正则。
- **Operations 同步改造**：
  - BFF：转发新增字段，映射新的错误码。
  - Web：数据类型列表增加“值格式”列；编辑抽屉增加“值格式”，选 `REGEX` 时出现“正则”“测试样例”；策略变更详情显示值格式和正则的差异；手动加密页提示格式要求，`JSON` 在前端先做解析预检。正则只以 Vault 的校验结果为准，前端不预检，因为浏览器正则和 RE2 语法不完全一致。
- Java `VaultClient` / `VaultAdminClient` 同步新增字段与错误码；OpenAPI 同步更新。

**不做**

- 单独的 `max_length` 字段：长度可以用正则表达，例如 `.{1,64}`。
- Vault 侧的规范化，以及统一各调用方的邮箱规范化（见 Q1）。
- 对已有记录做回溯校验或批量修复。
- “应拒绝”的反例样例、页面上的正则试运行接口。
- `EMAIL`、`PHONE_E164` 等专用内置格式：一律用正则表达。

## 6. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 现有类型的初始值格式与正则（第 7 节）是否照此设置？ | 建议照此设置。阶段 2 会逐个核对现有调用方的实际输入都能通过，确认后才执行 |

## 7. 现有数据类型的建议值格式

| 数据类型 | Token 模式 | 值格式 | 正则 | 现有写入方 |
|---|---|---|---|---|
| `EMAIL_ADDRESS_V1` | STABLE | `REGEX` | `[^\s@\p{Lu}]{1,64}@[^\s@.\p{Lu}]+(?:\.[^\s@.\p{Lu}]+)+` | CCT 联系人、Platform 登录邮箱、Platform 通知邮箱、人工加密（本机已开） |
| `PHONE_E164_V1` | STABLE | `REGEX` | `\+[1-9][0-9]{7,14}`（与 CCT 一致） | CCT 联系人 |
| `CHANNEL_ACCOUNT_CONFIG_V1` | REFERENCE | `JSON` | — | 人工加密 |
| `NOTIFICATION_PROVIDER_CREDENTIAL_V1` | REFERENCE | `JSON` | — | 人工加密 |
| `TEST_REFERENCE_V1`、`TEST_STABLE_V1`（仅 test） | — | `TEXT` | — | test 烟测 |

邮箱正则的含义：本地部分 1～64 个字符，恰好一个 `@`，域名至少两段；全程不允许空白和大写字母。这个正则只拦截非规范形式（大写、首尾空白），不判断地址是否真实有效；Unicode 域名和 Punycode 都放行。

## 8. 验收标准

**功能**

| 编号 | 标准 |
|---|---|
| AC-1 | 数据类型有必填的值格式（`TEXT`/`JSON`/`REGEX`，默认 `TEXT`）；`REGEX` 时正则必填，其余格式不允许填正则；列表、详情、管理接口都能看到这两个字段 |
| AC-2 | 新增或编辑类型时，正则编译失败、超过 256 字符、切换到 `REGEX` 或修改正则却没有样例、样例不通过正则，都会拒绝保存，并给出明确原因；当前生效的策略不受影响 |
| AC-3 | 样例不落库，不进策略变更快照，也不写日志 |
| AC-4 | 服务 Protect 与人工加密都按值格式校验（正则整值匹配）；不合格时返回 400，错误码区分 JSON 和正则；不生成记录或 Token；响应、日志、审计中都不出现原值 |
| AC-5 | 值格式和正则的变更保存后，在一个刷新周期内生效，不需要重启；策略变更记录显示前后差异 |
| AC-6 | 已有记录不受影响：存量 Token 的 Reveal 照常；STABLE 类型对合格值仍返回原有的 Token |
| AC-7 | 现有类型按第 7 节初始化后，CCT、Platform 登录、通知、渠道账号配置、SES 凭据的写入与读取均回归通过 |
| AC-8 | 校验覆盖边界：`JSON` 的数组根、非法 JSON、重复键被拒绝；`TEXT` 不额外限制；第 7 节邮箱正则拒绝大写和首尾空白、放行 Unicode 域名与 Punycode；手机正则拒绝位数越界 |

**视觉/交互（Operations Web）**

| 编号 | 标准 |
|---|---|
| AC-9 | 数据类型列表新增“值格式”列，正则以等宽字体显示，过长时截断并可悬停查看 |
| AC-10 | 编辑抽屉：值格式下拉附带每种格式的说明；选 `REGEX` 时才显示正则与测试样例，正则旁说明“按整值匹配，RE2 语法”，切换到 `REGEX` 或修改正则后样例必填；样例框提示“请勿填写真实数据” |
| AC-11 | 手动加密页在选择数据类型后显示值格式和正则；`JSON` 在前端先做解析预检；Vault 拒绝时显示“不是合法的 JSON 对象”或“不符合该类型的正则”，提示中不回显原值 |
| AC-12 | 策略变更详情的字段对比包含值格式和正则，差异照常高亮 |

## 9. 阶段安排（建议）

八个阶段全部适用：

1. 需求评审
2. 现状差异与影响（逐个核对调用方输入、错误码兼容）
3. 方案、接口、DDL/DML 与页面设计
4. 研发（Vault、Java 客户端、BFF、Web）
5. 本地验证
6. test 发布准备
7. test 部署验收
8. 提交归档

每个阶段完成后停下，等待确认。
