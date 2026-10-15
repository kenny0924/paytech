# Payment Card 加密存储技术方案

> 状态：阶段 2+3 已确认（2026-10-15，第 3 版：卡 / 卡凭证 / 交易工具分层，卡按卡号唯一）；阶段 4 实现与偏差见 [delivery.md](./delivery.md)。依据 [需求第 2 版](./requirement.md)；第 2 版方案（每个支付工具一条卡记录）作废，其阶段 4 实现需按本版返工。领域模型图见评审页 <https://claude.ai/artifact/HmWCN4KQqkXCzzFbpztiCN>。确认记录见 [delivery.md](./delivery.md)。

**规模：** 只改 Payment 代码；对外接口新增 0、修改 0（工具申请、读卡接口的路径和字段不变）；新增 3 张表（`t_payment_card` 7 个字段、`t_payment_card_credential` 6 个字段、`t_payment_card_instrument` 5 个字段），共 2 个唯一索引，旧表结构不改；无业务 DML；Vault 代码不改，各环境注册的数据类型与访问规则同第 2 版；[Vault 托管卡 DEK 例外决定](../../../agent-docs/decisions/2026-10-15-vault-payment-card-dek.md)沿用；无页面。

## 1. 现状差异

“现状”指工作区里按第 2 版方案完成、尚未提交的阶段 4 实现。

| 需求项 | 现状 | 目标 | 缺口 |
|---|---|---|---|
| AC-01 新卡三层落库 | 每次申请插入一条 `t_payment_card`（含 `client_id`、加密的卡号/有效期/持卡人），主键即 `instrument_ref` | 卡、凭证、交易工具各一条；卡号与凭证分开加密；BIN 6～8 位、后四位 | 表结构与写入流程重做 |
| AC-02 同卡复用 | 每次都新建卡记录、新 DEK、Vault Protect | 按指纹找到已有卡，Reveal DEK，不再 Protect | 新增按指纹查卡 |
| AC-03 新凭证 | 不区分 | 按（卡、凭证摘要）查或建；持卡人规范化 | 新增 |
| AC-04 按卡号定位 | 无索引、无方法 | `card_fingerprint` 唯一索引 + `CardService.findCard(pan)` | 新增 |
| AC-05 并发首用 | 无此场景 | 撞唯一键改读已有记录 | 新增 |
| AC-06 重启可读 | 已满足（无进程内状态） | 不变 | 无 |
| AC-07 CVV | Redis key 按卡记录 ID | 按交易工具 ID；AAD 绑定工具 ID | 改 key 与 AAD |
| AC-08 重放 | Vault Reveal 后解密比较 | 比较指纹与凭证摘要，不访问 Vault | 简化 |
| AC-09 Risk 属性 | 读卡记录 | 经交易工具关联到卡 | 改查询 |
| AC-10 指纹密钥 | 已实现 | 不变 | 无 |
| AC-11 卡号校验 | 已实现 | 不变 | 无 |
| AC-12 不泄漏 | 已实现，日志检查卡号、持卡人、指纹、DEK Token | 加有效期组合检查；新类型同样脱敏 | 补测试 |
| AC-13 事务与 503 | 已实现 | 新增的卡、凭证插入也在主事务外 | 见 §2.3 |
| AC-14 现有测试 | 通过 | 新存储下通过 | 回归 |

### 逐系统影响

| 系统/模块 | 影响 | 改动类型 |
|---|---|---|
| `paytech-payment-card` | 三张表的 DAL、`CardCipher` 增加凭证加解密与摘要、三个 Processor 改流程、`CardService` 增加查卡/存卡/存凭证 | 代码 |
| `paytech-payment-execution` | 不改：仍是“重放调核对、新申请事务外调准备、事务内 `applyInstrument`、读卡调领取” | 无 |
| `paytech-payment-web` | 不改（Mapper 包已扫描） | 无 |
| `paytech-payment-test` | 清理三张表、Redis key 改按工具 ID；`CardStorageTest` 扩充；`ChannelTestMapper` 查询改三表 | 测试 |
| 数据库 | 本机测试库删除旧的空表 `t_payment_card`，新建三张表；PDManer、Schema、测试 `init/ddl.sql` 同步 | 模型、SQL |
| Vault | 同第 2 版：`PAYMENT_CARD_DEK` 数据类型和两条访问规则，运行时注册 | 非代码 |
| Platform、Risk、Commerce | 读卡合同、风控字段不变 | 不改 |

## 2. 方案

### 2.1 数据与密钥

| 东西 | 位置 | 形态 |
|---|---|---|
| 卡号 | `t_payment_card.encrypted_pan` | 本卡 DEK 做 AES-256-GCM：`IV(12) ‖ 密文 ‖ Tag(16)`，AAD = `payment-card:{payment_card_id}` |
| 有效期、持卡人（原值） | `t_payment_card_credential.encrypted_details` | 本卡 DEK 加密，AAD = `payment-card-credential:{payment_card_credential_id}` |
| CVV | Redis `card:cvv:{payment_card_instrument_id}`（自动加服务前缀） | 本卡 DEK 加密，AAD = `payment-card-instrument:{payment_card_instrument_id}:cvv`；TTL = 工具期限 − 当前时间；`GETDEL` 只领取一次 |
| 本卡 DEK（32 字节） | Vault `PAYMENT_CARD_DEK`（REFERENCE） | 库中只存 `t_payment_card.dek_vault_token` |
| 卡指纹 | `t_payment_card.card_fingerprint`，唯一 | `HMAC-SHA256(指纹密钥, 卡号)` 小写十六进制 |
| 凭证摘要 | `t_payment_card_credential.credential_digest` | `HMAC-SHA256(指纹密钥, "credential|" + 卡号 + "|" + 月 + "|" + 年 + "|" + 规范化持卡人)` 小写十六进制 |
| BIN、后四位 | `t_payment_card.card_bin`、`pan_last4` | 明文；卡号不少于 16 位取前 8 位，否则前 6 位 |

- **AAD 分别绑定到各自记录**：卡号、凭证、CVV 三类密文互相不能替换，也不能挪到别的记录上。
- **凭证摘要把卡号算进去**：两张卡的有效期和持卡人恰好相同时，摘要也不同，不会暴露“这两张卡属于同一人”。摘要加了 `credential|` 前缀，和卡指纹分开用途。
- **持卡人规范化**（需求 Q-08）：去掉首尾空白，连续空白合并为一个空格，转大写（`Locale.ROOT`）。月份、年份入口已校验为两位和四位数字，直接使用。规范化只用于计算摘要；加密保存的是**首次提交的原值**。之后只有大小写或空格不同的提交复用这条凭证，渠道读到的也是首次的原值。
- **DEK** 只在请求内存中出现，用完清零；不轮换，DEK 外层的保护密钥由 Vault 负责轮换。指纹密钥不可轮换（需求 Q-07）。
- **Vault 调用参数**同第 2 版：Protect（`PAYMENT_CARD_DEK` / `REFERENCE` / `CARD_STORAGE`，幂等键 `payment-card-{payment_card_id}`），Reveal（`CARD_USE`）。

### 2.2 申请工具（`POST /payment-instrument/v1/card-tokens`，合同不变）

```text
ApplyCardInstrumentProcessor（Intent 锁内，Execution 不改）
  ├─ 原提交：CheckCardInstrumentProcessor.process（事务外）
  │     读交易工具 → 关联凭证与卡 → 用本次输入算指纹和凭证摘要 → 与记录比较
  │     不一致：409“原提交不可更换卡工具”；不访问 Vault、不碰 CVV
  └─ 新申请：
      ├─ 事务外：PrepareCardInstrumentProcessor.process(cardContext)
      │     1. 校验 Luhn、卡未过期；期限缩短到卡有效期
      │     2. 卡：CardService.findCard(pan)
      │          ├─ 有 → Vault Reveal DEK
      │          └─ 无 → 生成 DEK → Vault Protect → 加密卡号 → CardService.saveCard
      │                 撞唯一键 → 读取已有卡 → Vault Reveal 它的 DEK（本次 Protect 的 DEK 成为无引用残留）
      │     3. 凭证：按（卡、摘要）查 → 无则加密原值 → CardService.saveCredential（撞唯一键 → 读取已有）
      │     4. 分配交易工具 ID → 用 DEK 加密 CVV → Redis 写入
      │     5. 上下文写回：instrumentRef = 工具 ID、expiresAt、待保存的交易工具
      └─ 事务内：CardService.applyInstrument（插入 t_payment_card_instrument）
                 + bindCardInstrument + saveApplyCardInstrumentContext
```

### 2.3 事务与并发

| 写入 | 时机 | 事务 |
|---|---|---|
| `t_payment_card` | 准备阶段，Vault Protect 之后 | 单条 INSERT 自动提交，不在主事务内 |
| `t_payment_card_credential` | 准备阶段 | 单条 INSERT 自动提交 |
| `t_payment_card_instrument` | 主事务 | 与工具 Token、Execution 上下文同一本地事务 |

- **卡和凭证是可复用的事实**：主事务失败时它们保留，下次同卡申请直接复用；只有交易工具记录随主事务回滚。
- **并发首用同一卡号**：不同 Intent 的申请不在同一把锁里，可能同时插入卡记录，由 `uk_payment_card_fingerprint` 保证只留一条。输的一方捕获 `DuplicateKeyException`，读取赢的那条后继续。凭证同理，由（卡、摘要）唯一索引保护。
- **无引用的 DEK**：输掉竞争的一方已经在 Vault 中 Protect 了一把 DEK，没有任何卡记录引用它。它只是一把随机密钥，不能单独解出卡数据；Vault 不提供删除，作为已知残留接受（与第 2 版主事务失败时的残留相同）。
- **同一 Intent**：仍由 Intent 锁串行，`uk_pay_token_scope` 是最终保护。

### 2.4 读卡（`POST /payment-card/v1/channel-card-materials/reveal`，合同不变）

```text
ClaimChannelCardMaterialProcessor.process(instrumentRef)
  读交易工具并关联凭证、卡（一次 JOIN 查询）
  → Vault Reveal DEK → 解密卡号与凭证（失败则报错，不领取 CVV）
  → Redis GETDEL card:cvv:{工具 ID} → 不存在：400“卡安全码已领取或已过期”
  → 解密 CVV → 返回 ChannelCardMaterial
```

### 2.5 风控属性与按卡号定位

- `CardService.riskAttributes(instrumentRef)`：用交易工具关联到卡，返回 `card_bin` 与 `card_fingerprint`；不访问 Vault。记录不存在时返回 null。
- `CardService.findCard(pan)`：算出指纹，按唯一索引查卡，没有则返回 null。只在 Card 模块内部使用（准备阶段就是它的使用方），不新增外部接口。

### 2.6 分层与类

| 模块 | 类 | 变化 |
|---|---|---|
| card `service/processor` | `PrepareCardInstrumentProcessor` | 改为 §2.2 的查或建流程 |
| card `service/processor` | `CheckCardInstrumentProcessor` | 改为比较指纹和凭证摘要，去掉 Vault 依赖 |
| card `service/processor` | `ClaimChannelCardMaterialProcessor` | 改为经交易工具关联读取，CVV key 用工具 ID |
| card `service` | `CardService` / `CardServiceImpl` | 本地能力：`findCard`、`saveCard`、`findCredential`、`saveCredential`、`applyInstrument`、`findInstrument`、`riskAttributes`；不访问 Vault 和 Redis |
| card `core/crypto` | `CardCipher` | 拆成卡号加解密与凭证加解密；新增 `credentialDigest`、`normalizeCardholderName`；CVV AAD 改为工具 ID；`riskAttributes` 拆为 `fingerprint`、`bin`、`last4` |
| card `core/dal` | `PaymentCard`、`PaymentCardCredential`、`PaymentCardInstrument` 及 Mapper/XML | 三张表 |
| card `core/dal` | `CardInstrumentRecord`（查询结果） | 交易工具关联凭证与卡的只读视图 |
| card `domainservice/repository` | `PaymentCardRepository`（卡 + 凭证）、`PaymentCardInstrumentRepository` | 新增或拆分 |
| card `domain` | `PreparedCard` → `PreparedInstrument` | 待保存的交易工具（工具 ID、凭证 ID） |
| card `integration` | `CardKeyClient`、`CardSecurityCodeClient` | 不变（后者的参数改为工具 ID） |

外部访问只在三个 Processor 中、主事务外进行；Vault 或 Redis 不可用统一转 `ServiceUnavailableException`（503），保持第 2 版行为。

### 2.7 配置

不变：`payment.card.fingerprint-key` / `PAYMENT_CARD_FINGERPRINT_KEY`，Base64，解码后至少 32 字节，缺失则启动失败。卡指纹和凭证摘要共用这把密钥，用前缀区分用途。

## 3. 数据库

### 3.1 `t_payment_card`（卡，每个卡号一条）

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `payment_card_id` | BIGINT | 否 | 主键，Snowflake |
| `card_fingerprint` | CHAR(64) ascii_bin | 否 | 卡指纹；唯一索引 `uk_payment_card_fingerprint` |
| `card_bin` | VARCHAR(8) ascii_bin | 否 | 卡号不少于 16 位为前 8 位，否则前 6 位 |
| `pan_last4` | CHAR(4) ascii_bin | 否 | 卡号后 4 位 |
| `encrypted_pan` | VARBINARY(128) | 否 | 本卡 DEK 加密的卡号（19 位卡号密文约 50 字节） |
| `dek_vault_token` | CHAR(43) ascii_bin | 否 | 本卡 DEK 的 Vault REFERENCE Token |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC 微秒 |

不存 `client_id`：卡是平台级事实，同一卡号在不同 Client 下是同一张卡；归属记在交易工具上。

### 3.2 `t_payment_card_credential`（卡凭证）

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `payment_card_credential_id` | BIGINT | 否 | 主键，Snowflake |
| `payment_card_id` | BIGINT | 否 | 所属卡 |
| `credential_digest` | CHAR(64) ascii_bin | 否 | 凭证摘要；与 `payment_card_id` 组成唯一索引 `uk_payment_card_credential` |
| `encrypted_details` | VARBINARY(1024) | 否 | 本卡 DEK 加密的有效期与持卡人原值（持卡人最多 128 字符） |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC 微秒 |

### 3.3 `t_payment_card_instrument`（交易工具）

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `payment_card_instrument_id` | BIGINT | 否 | 主键，Snowflake；十进制字符串即 `instrument_ref` |
| `payment_card_credential_id` | BIGINT | 否 | 本次使用的凭证 |
| `client_id` | BIGINT | 否 | 所属 Client，取自已核对的 Payment Intent |
| `create_time` / `update_time` | DATETIME(6) | 否 | UTC 微秒 |

只有主键：当前的查询都是按 `instrument_ref`。按卡或按 Client 查交易工具没有使用方，等存卡需求再加索引。

### 3.4 SQL

- `db/ddl.sql` 改为三张表的 `CREATE TABLE`（新环境执行）。
- **本机测试库额外的一步**：第 2 版已在本机 `mor_payment_channel_test` 建过旧的 `t_payment_card`（0 行），需要先 `DROP TABLE t_payment_card` 再执行新的 `db/ddl.sql`。这一步只在本机做，写进 delivery.md 的执行记录，不放进 `ddl.sql`。其他环境从未建过旧表。
- 回滚：按 `t_payment_card_instrument`、`t_payment_card_credential`、`t_payment_card` 的顺序 `DROP TABLE`（仅在没有需要保留的数据时）。
- 同步：PDManer（第 2 版新增、尚未提交的 `t_payment_card` 实体改为新结构，新增两个实体，Card 图更新）、Schema、测试 `init/ddl.sql`。业务 DML 无。

## 4. 例外决定

[Vault 托管卡 DEK 决定](../../../agent-docs/decisions/2026-10-15-vault-payment-card-dek.md)沿用，内容不变：仍是每张卡一把 DEK。卡按卡号唯一以后，DEK 的数量从“每笔交易一把”降为“每个卡号一把”。

## 5. 测试计划

| 测试 | 入口 | 覆盖 |
|---|---|---|
| `CardStorageTest` | HTTP 工具申请 | AC-01：三表各一条；卡号、持卡人、有效期不以明文出现在任何列；16 位卡 BIN 8 位、15 位卡（Amex 测试卡）BIN 6 位；后四位正确；Vault Protect 一次 |
| 同上 | 两个 Intent 先后用同一张卡 | AC-02：卡 1、凭证 1、工具 2；Protect 1 次、Reveal 1 次 |
| 同上 | 同卡换有效期、换持卡人、持卡人只有大小写和空格不同 | AC-03：凭证分别为 2、3、不增加 |
| 同上 | `CardService.findCard`（模块公开 Service） | AC-04：已存卡号返回该卡；未存卡号返回 null |
| 同上 | 两个线程用不同 Intent 并发申请同一张新卡 | AC-05：两次都成功，卡 1 条 |
| 同上 | 同一张卡两笔交易分别读卡 | AC-07：各自读到自己的 CVV，互不影响；第二次读同一工具失败 |
| 同上 | 重放 | AC-08：同卡重放不新增任何记录、不调用 Vault；换卡重放 409 |
| 同上 | 申请 + pay，`OutputCaptureExtension` | AC-12：日志不含卡号、持卡人、指纹、DEK Token 及“月/年”组合 |
| 同上 | 无效卡、Vault 故障 | AC-11、AC-13（第 2 版已有，调整断言到三表） |
| `PayCardChannelTest`、`PayRiskEvaluationTest` | 现有 | AC-06、AC-09、AC-14 |
| `CardCipherTest` | 纯 Java | 卡号、凭证、CVV 三类密文互不可替换；篡改、错 DEK 失败；摘要随卡号变化；持卡人规范化 |
| `PaymentCardPropertiesTest` | 纯 Java | AC-10（不变） |
| 清理 | `ChannelDataMocker` | 每例按工具 ID 删除 Redis CVV，再清三张表；`assertEmpty` 检查三张表 |

已知未验证项同第 2 版：Redis 不可用分支、真实 Vault 联调、“进程重启”不单独测。

## 6. 研发顺序与阶段 4 需要的授权

1. PDManer、Schema、测试 `init/ddl.sql`、需求 `db/ddl.sql`。
2. **本机测试库 `mor_payment_channel_test`：执行 `DROP TABLE t_payment_card`（0 行），再执行新的 `db/ddl.sql`**（需要你授权）。
3. Card：`CardCipher`、三表 DAL、Repository、`CardService`、三个 Processor；`PreparedCard` 换成 `PreparedInstrument`。
4. 测试：Mocker/TestMapper 清理与查询、`CardStorageTest` 扩充、`CardCipherTest` 调整。
5. 文档：`CURRENT_IMPLEMENTATION.md` 本需求一行、Payment README、delivery.md 阶段 4 证据重写。
6. `./mvnw -o verify` 全量通过。
