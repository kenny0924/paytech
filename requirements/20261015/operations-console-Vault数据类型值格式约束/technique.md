# 技术方案：Vault 数据类型值格式约束

- 状态：**阶段 2 已确认**；**阶段 3 评审稿**（第 6～11 节）。需求见 [requirement.md](./requirement.md)

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| 值格式与正则字段（AC-1） | `t_vault_data_type` 只有编码、Token 模式、级别、说明、人工开关、状态（[ddl.sql](../../20261014/operations-console-Vault数据类型与手动加解密/db/vault/ddl.sql)）；`biz.DataType` 同 | 新增 `value_format`、`value_pattern` | 表、模型、sqlc、管理接口、OpenAPI 均缺 | 加两列：`value_format` 为 NOT NULL，默认 `TEXT`；`value_pattern` 可空。旧版 Vault 用显式列名读写，加列不影响它 |
| 保存时校验（AC-2、AC-3） | `validateDataType` 只校验枚举和说明长度（`internal/biz/policy_admin.go`） | 校验格式与正则的组合，编译正则，用样例验证 | 未实现 | 在 `validateDataType` 中扩展；样例只在请求里出现，不进 `dataTypeSnapshot` |
| Protect 校验（AC-4） | `validateProtect` 只查 UTF-8 和 32KiB；随后授权、计算 HMAC、提交（`usecase.go`、`protect.go`） | 授权通过后、计算 HMAC 前按格式校验 | 未实现 | 放在 `authorize` / `authorizeManual` 之后：这时已经取到数据类型，服务调用和人工加密共用一个校验函数。失败时按现有方式记一条拒绝审计（DENIED，不含原值） |
| 错误码（AC-4） | 共 14 个错误码，400 类只有 4 个（`errors.go`） | 区分 JSON 不合法和正则不符 | 缺 2 个错误码 | 新增 `VALUE_NOT_JSON_OBJECT`、`VALUE_PATTERN_MISMATCH`，都返回 400，审计结果记为 DENIED；保存失败另设 2 个码（见 7.2；阶段 3 发现 Problem 响应没有字段级错误，复用 `INVALID_REQUEST` 无法说明原因） |
| 生效时间（AC-5） | 策略每 10 秒全量刷新，按版本编译快照（`internal/pkg/policy`） | 一个刷新周期内生效 | 快照不含格式 | 编译快照时同时编译正则。库中的正则编译失败时（理论上保存时已拦截），该类型的 Protect 一律拒绝（fail closed），并记录固定诊断信息 |
| 存量记录（AC-6） | Reveal 不读格式 | 不受影响 | 无 | Reveal 不改。注意：如果幂等重放的原请求值不符合新约束，重放会被拒绝（先校验、后查幂等键）。这是预期行为，写入合同说明 |
| 运营页面（AC-9～12） | 6 个页面，数据类型列表和编辑页没有格式字段；变更详情用 `snapshotFieldLabels` 显示字段名（`operations-console-web/src/vault/vault.ts`） | 加列、加表单项、预检、错误提示 | 未实现 | 见第 3 节 |

## 2. 现有写入方核对（Q1 的依据）

| 写入方 | 数据类型 | 实际写入的值 | 是否通过第 7 节约束 |
|---|---|---|---|
| CCT 入驻联系人（`ClientDraftPatch`） | `EMAIL_ADDRESS_V1` | 去首尾空白，整体 `toLowerCase(ROOT)`，再用 Jakarta `@Email` 校验 | 正常地址都能通过。**例外**：`@Email` 允许的单段域名（如 `a@localhost`）和带空格的引号本地部分会被正则拒绝，见风险 R2 |
| CCT 入驻联系人 | `PHONE_E164_V1` | 去掉空格、括号、连字符后匹配 `\+[1-9][0-9]{7,14}` | 通过，与正则完全相同 |
| Platform 登录（Client 注册、登录、Operations 登录、Operations 建账号） | `EMAIL_ADDRESS_V1` | `canonicalEmail`：不含空白，本地部分是 ASCII 并转小写，域名经 IDN 转 ASCII 后小写，且必须含 `.` | 通过 |
| Platform 通知（`SubmitNotificationProcessor`） | `EMAIL_ADDRESS_V1` | `NotificationInput.canonicalEmail`：小写、ASCII、域名必须有顶级域 | 通过 |
| 运营人工加密 | 渠道账号配置、通知凭据 | 运营粘贴的 JSON | 调用方读取时本来就要求 JSON 对象，合规输入都能通过 |
| Payment Card（`CardKeyClientImpl`，已提交 `b2fa7cf`） | `PAYMENT_CARD_DEK`（REFERENCE） | 数据密钥的 Base64 | 本机和 test 都还没登记这个类型。登记时默认 `TEXT`，不受影响；是否加正则由 Payment 会话决定 |
| test 烟测（`test-environment/scripts/vault.py`） | `TEST_STABLE_V1`、`TEST_REFERENCE_V1` | 合成文本 | `TEXT`，不受影响 |
| Vault 自身的 Go 测试、`vault-test` 黑盒测试 | 夹具自行写入策略表 | — | 新列默认 `TEXT`，现有夹具不受影响；新增用例覆盖 JSON 和 REGEX |

结论：现有服务写入方的规范输入都满足第 7 节的约束。只有 CCT 的少数边界输入会被拒绝（R2），建议仍按第 7 节设置。

## 3. 逐系统影响

| 系统/仓库 | 影响点 | 改动 | 上下游与兼容 |
|---|---|---|---|
| Vault（Go，`vault-service/`） | `t_vault_data_type` 加 2 列；`biz.DataType`、管理用例、Protect 校验、错误码、策略快照、sqlc、OpenAPI（`admin-v1.yaml`、`v1.yaml`）、PDManer（`docs/database/vault.pdma.json`）、Schema SQL | 改代码、模型、文档 | 先执行 DDL，再发布 Vault。加列的默认值保证旧版 Vault 照常运行 |
| Java 客户端（`paytech-vault-client`） | `VaultClientErrorCode` 加 2 个码，并加入 400 合法码集合；管理接口 DTO（`DataType`、`Create/UpdateDataTypeRequestBody`）加 `valueFormat`、`valuePattern`、`sampleValues` | 改代码 | 见风险 R1：旧客户端遇到新错误码会归类为“上游响应无效”；旧客户端遇到响应里的新字段会忽略（Jackson 3 默认不因未知字段失败） |
| Operations BFF | `VaultAdminConverter`、`Create/UpdateVaultDataTypeRequestBody` 加字段和校验；`OperationsErrorStatusResolver`：现有 400 类错误码走默认分支，由异常类型决定状态码。新码同样走这个分支，阶段 4 用测试确认返回 400 | 改代码 | 依赖新客户端；要求 Vault 先发布，因为 Vault 的严格 JSON 会拒绝未知请求字段 |
| Operations Web | `vault.ts` 类型、`isDataType` 校验、`snapshotFieldLabels`；`VaultDataTypes.tsx` 加列和编辑项；`VaultManualProtect.tsx` 显示格式提示，对 JSON 做预检；错误提示 | 改代码 | 最后发布 |
| Platform（`mor_platform`） | 不新增接口、权限或路由（数据类型的保存接口不变） | 不改 | — |
| CCT、Platform 服务、Payment | 不改代码 | 不改 | 规范输入不受影响；客户端升级可以等各自下次发布时再做（R1） |
| `mor_vault` 数据 | 按第 7 节给 4 个（本机）或 6 个（test）现有类型设置格式 | DML（经管理接口或 SQL，阶段 3 决定） | 发布 Vault 并完成回归之后执行；每次设置都会生成一个策略版本 |
| 文档 | `VAULT_SERVICE_ARCHITECTURE.md`（数据类型目录要素）、`CURRENT_IMPLEMENTATION.md` | 更新 | — |

## 4. 风险与处理建议

| 编号 | 风险 | 建议 |
|---|---|---|
| R1 | Java 客户端对错误码做严格白名单，旧客户端遇到 `VALUE_NOT_JSON_OBJECT`、`VALUE_PATTERN_MISMATCH` 会报“上游响应无效”（`UPSTREAM_INVALID_RESPONSE`）。CCT、Platform 等服务短期内仍用旧客户端 | 可以接受：它们的规范输入不会触发这两个码；即使触发，结果仍是失败并拒绝写入，只是错误分类不够准确。这些服务随下次发布升级客户端，不单独发版 |
| R2 | CCT 的 `@Email` 比 Vault 的正则宽，单段域名或带空格的引号地址会通过 CCT 校验，却在 Vault 被拒，入驻时会表现为“依赖不可用” | 建议保留正则要求域名至少两段：这类地址本来就无法正常投递。如果要更平滑，由 CCT 在自己的校验中补上同样的限制（属于 CCT 改动，不在本需求内，记作遗留项） |
| R3 | 运营把正则改严后，服务的写入开始被拒 | 用户已选方案 A：必填样例 + 变更记录兜底。另外建议在编辑页提示“影响所有服务写入，含业务服务” |
| R4 | 库中的正则因 Go 版本升级等原因编译失败 | 快照编译时该类型 fail closed，并记录固定诊断信息；保存时已校验，正常情况下不会发生 |
| R5 | 三个调用方对 Unicode 域名邮箱的规范化不一致（需求第 3 节的附带发现） | 不在本需求内，记作遗留项 |
| R6 | 发布顺序：新 BFF 发送新字段时，旧 Vault 会因严格 JSON 拒绝 | 发布顺序固定为：DDL → Vault → BFF → Web → 现有类型的格式设置 |

## 5. 工作量粗估

| 部分 | 估计 |
|---|---|
| Vault（模型、校验、快照、错误码、sqlc、OpenAPI、PDManer、测试） | 主要工作量 |
| Java 客户端（错误码、DTO、测试） | 小 |
| BFF（字段、校验、状态码映射、测试） | 小 |
| Web（列表、编辑抽屉、手动加密页提示与预检、变更详情、测试） | 中 |
| 本机与 test 的 DDL、格式设置和验收 | 中 |

## 6. 规则定义（阶段 3）

| 值格式 | 取值规则 | 保存时要求 |
|---|---|---|
| `TEXT` | 只做现有检查（UTF-8，不超过 32KiB） | `valuePattern` 必须为空 |
| `JSON` | 整个值是一个 JSON 对象：根是 `{}`，任意层级都没有重复键，末尾不能跟其他内容；用流式解析逐层检查重复键（Go 标准库 `json.Unmarshal` 不检查重复键） | `valuePattern` 必须为空 |
| `REGEX` | `^(?:valuePattern)$` 整值匹配，用 Go `regexp`（RE2，线性时间） | `valuePattern` 为 1～256 个字符，能编译，并且样例规则见下 |

**样例 `sampleValues`**
- 只在新建或修改请求中出现，最多 5 个，每个不超过 4096 个字符。
- 结果是 `REGEX`，并且属于“新建”“从其他格式切换过来”或“正则有变化”时，至少要有 1 个样例。
- 其他情况可以不带样例；如果带了，也会按结果规则逐个校验。
- 任一样例不通过就拒绝保存。
- 样例不落库，不进 `before/after` 快照，Vault 和 BFF 都不记录。BFF 的 `/vault-admin` 本来只记元数据日志。

**修改语义（兼容旧 BFF）**
- 新建请求不带 `valueFormat` 时，按 `TEXT` 处理。
- 修改请求不带 `valueFormat` 时，格式和正则都保持不变。这样在 Vault 已升级、BFF 还没升级的窗口期，旧 BFF 编辑类型不会把格式清掉。
- 修改请求带了 `valueFormat` 时，`valueFormat` 和 `valuePattern` 一起替换。

## 7. 接口变更

### 7.1 Vault 管理接口（`api/openapi/admin-v1.yaml`）

| 对象 | 变更 |
|---|---|
| `DataType`（响应） | 新增 `valueFormat`（必有，`TEXT`/`JSON`/`REGEX`）和 `valuePattern`（仅 `REGEX` 时出现） |
| `CreateDataTypeRequest` | 新增 `valueFormat`、`valuePattern`、`sampleValues`，都是可选 |
| `UpdateDataTypeRequest` | 同上，语义见第 6 节 |
| 策略变更快照 | `dataTypeSnapshot` 增加 `valueFormat`、`valuePattern`，不含样例 |

### 7.2 错误码（新增 4 个，都返回 400，审计结果记为 DENIED）

| 错误码 | 中文说明 | 出现位置 |
|---|---|---|
| `VALUE_NOT_JSON_OBJECT` | 值不是合法的JSON对象 | 服务 Protect（`v1.yaml`）、人工加密 |
| `VALUE_PATTERN_MISMATCH` | 值不符合数据类型的正则 | 服务 Protect、人工加密 |
| `VALUE_PATTERN_INVALID` | 值格式与正则的组合无效或正则无法编译 | 新建、修改数据类型 |
| `SAMPLE_VALUE_INVALID` | 测试样例缺失或不符合值格式 | 新建、修改数据类型 |

说明：
- 错误信息只给出上表中的固定文字，不带原值、样例，也不带正则编译器的报错原文。
- 保存时的错误单独设码，不复用 `INVALID_REQUEST`：Vault 的 Problem 响应没有字段级错误，复用的话页面只能显示“请求参数无效”。

### 7.3 校验位置（Vault 内部）

```text
Process: validateProtect（UTF-8、32KiB）→ authorize / authorizeManual（取到数据类型）
       → checkValue（新增：按值格式校验，失败记 DENIED 审计）→ 计算 HMAC → 本地事务（幂等、写入）
```

- 服务调用路径的 `authorize` 改成通过 `policy.DataType(code)` 取完整数据类型，Token 模式也从这里取，不再单独调用 `DataTypeMode`。
- `policy.Build` 编译快照时为每个 `REGEX` 类型预编译正则。如果编译失败，这个类型标记为不可写，`checkValue` 返回 `INTERNAL_ERROR`（fail closed），失败以 `INTERNAL_ERROR`（FAILED）写入访问审计作为诊断，不带正则原文。
- 幂等重放同样先经过 `checkValue`。原值不符合新约束时，重放会被拒绝；这一点写入 OpenAPI 说明。

### 7.4 Java 客户端（`paytech-vault-client`）

- `VaultClientErrorCode` 新增上述 4 个错误码，并加入 400 合法码集合。
- `admin/response/DataType`、`admin/request/Create/UpdateDataTypeRequestBody` 新增 `valueFormat`、`valuePattern`、`sampleValues`，字段注释写明规则。
- 业务客户端 `VaultClient` 不改接口。
- 客户端不做格式校验，以 Vault 结果为准。

### 7.5 Operations BFF

- `CreateVaultDataTypeRequestBody`、`UpdateVaultDataTypeRequestBody` 新增三个字段，只做形状校验：
  - `valueFormat` 取值为 `TEXT|JSON|REGEX`；
  - `valuePattern` 不超过 256 字符；
  - `sampleValues` 不超过 5 个，每个不超过 4096 字符。

  格式与正则的组合规则只在 Vault 校验。
- `VaultAdminConverter` 原样转发。
- `OperationsErrorStatusResolver`：新码不单独映射，走默认分支返回 400，用测试确认。
- 数据类型列表、人工加密接口的出参和路由都不变，不需要 Platform 登记新数据。

## 8. 数据设计

- [db/vault/ddl.sql](./db/vault/ddl.sql)：`t_vault_data_type` 新增 2 列。
  - `value_format`：ascii，NOT NULL，DEFAULT `'TEXT'`。这里设默认值，是为了让旧版 Vault 在发布窗口内新增类型时不失败。
  - `value_pattern`：VARCHAR(256)，utf8mb4，可空。正则里可能出现中文等非 ASCII 字面量，所以用 utf8mb4。
- 同步更新 `vault-service/docs/database/sql/vault-schema.sql`、PDManer 生成器 `scripts/pdmaner/vault/generate-vault-model.mjs` 与生成的 `vault.pdma.json`、sqlc 查询和生成代码。
- **DML：不涉及。** 现有类型的格式经 Operations 页面（管理接口）逐个设置，每次设置留下策略版本、操作人和理由，同时也作为页面验收。前序需求已执行的 `dml-local.sql`、`dml-test.sql` 是历史文件，不修改。

| 环境 | 设置内容 |
|---|---|
| 本机 | `EMAIL_ADDRESS_V1`、`PHONE_E164_V1` 设为 `REGEX`（正则见需求第 7 节）；`CHANNEL_ACCOUNT_CONFIG_V1`、`NOTIFICATION_PROVIDER_CREDENTIAL_V1` 设为 `JSON`；共 4 个策略版本 |
| test | 同上 4 个；`TEST_*` 两个类型保持 `TEXT`，不做变更 |

## 9. 页面设计（Operations Web）

**数据类型列表**（`VaultDataTypes.tsx`）
- 在“数据级别”后加一列“值格式”，宽 220：
  - `TEXT` 显示“不限格式”，`JSON` 显示“JSON 对象”；
  - `REGEX` 显示“正则”标签，下一行用等宽字体显示正则，超长时省略，悬停显示全文。

**编辑抽屉**
- 在“数据级别”之后加入：
  - “值格式”下拉，三个选项附带说明：不限格式 / 必须是 JSON 对象 / 按正则整值匹配。
  - 选 `REGEX` 时显示“正则”输入框：等宽字体，最长 256 字符。说明文字为“按整值匹配，无需写 ^ $；RE2 语法；保存后约 10 秒内对所有服务和人工加密生效”。
  - 选 `REGEX` 时显示“测试样例”：可动态增减的输入框，1～5 个，占位提示“示例值，请勿填写真实数据”。从其他格式切换过来或正则有改动时，样例必填；其余情况可以不填。
- 页面每次都提交 `valueFormat`；只有 `REGEX` 时才提交 `valuePattern`；样例非空时提交 `sampleValues`。

**手动加密**（`VaultManualProtect.tsx`）
- 选好数据类型后，在“原值”输入框下方显示格式要求：
  - “不限格式”；
  - “必须是 JSON 对象”；
  - “必须整值匹配正则：`…`”。
- `JSON` 类型提交前在前端用 `JSON.parse` 预检，要求根是对象（重复键由 Vault 判断）。`REGEX` 不在前端预检。
- Vault 拒绝时显示错误码对应的固定中文说明，并保留表单内容，方便修改。提示中不回显原值。

**策略变更详情**
- `snapshotFieldLabels` 增加“值格式”“正则”两个标签，前后对比和差异高亮复用现有组件。

**数据校验**
- `vault.ts` 的 `VaultDataType` 类型与 `isDataType` 运行时校验增加这两个字段。为兼容 Vault 已升级、Web 还没升级的窗口期，`valueFormat` 缺失时按 `TEXT` 处理。

## 10. 研发与验证计划

| 部分 | 实现 | 自动测试（入口级） |
|---|---|---|
| Vault | 模型、sqlc、管理用例、`checkValue`、快照编译、错误码、OpenAPI、Schema SQL、PDManer | 管理接口：三种格式新建和修改；正则编译失败、超长、组合不合法；样例缺失、不通过、不落库也不进快照；旧请求不带 `valueFormat` 时保持原值。Protect（服务和人工）：JSON 的根、重复键、末尾多余内容；正则整值匹配，包括部分匹配被拒；失败时有 DENIED 审计且不含原值；改动在刷新周期后生效；快照编译失败时 fail closed。现有 `scripts/verify.sh` 全量门禁 |
| Java 客户端 | 错误码、DTO | 新码按 400 解析；黑盒测试对真实 Go Vault 走一遍新字段的往返 |
| BFF | 字段、形状校验、状态码 | 管理接口的字段透传；新码返回 400；样例不写日志 |
| Web | 列表、编辑抽屉、手动加密提示与预检、变更详情 | `vault.test.ts` 等单元测试；`npm run build` |
| 本机联调（阶段 5） | 执行 DDL，发布 Vault、BFF、Web，在页面上设置 4 个类型的格式 | 扩展 `local_acceptance.py` 和 `local_browser.py`；回归 CCT 入驻联系人、Platform 登录、通知邮箱的写入，以及渠道配置、SES 凭据的读取 |

## 11. 兼容与发布顺序

`ddl.sql` → Vault → BFF → Web → 在页面上设置现有类型的格式（本机先做，test 在阶段 6/7 另行授权）。

- 每一步都能单独回退：
  - Vault 回退后，新列虽然保留，但旧版本不会读取；
  - 格式设置在页面上改回 `TEXT` 即可撤销。
- CCT、Platform 不需要同步发布，原因见 R1。
