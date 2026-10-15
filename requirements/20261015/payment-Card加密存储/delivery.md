# Payment Card 加密存储交付记录

## 适用阶段

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（第 2 版：卡模型分层） |
| 2+3. 现状差异与方案设计 | 已确认（第 3 版） |
| 4. 研发实现 | 已确认（第 3 版） |
| 5. 综合验证与本地验收 | 已完成（含本机真实 Vault 联调） |
| 6–7. test 发布准备与部署 | 不适用：Payment 尚未部署到 mor-test |
| 8. 提交 | 已本地提交（不推送、不部署） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | “讨论一下如何做 payment card”；对存储方案回复：卡数据要长期存在库里、不放 Redis，CVV 可以放 Redis；读卡不需要审计；暂不需要调用方认证 | 登记需求并交付阶段 1 |
| 2026-10-15 | 1 | “不用管，假设我们已经有了PCI资质；其余的按照你的来” | Q-03 不限制测试卡，其余按建议；进入阶段 2+3 |
| 2026-10-15 | 2+3 | 讨论密钥：每张卡一把 DEK；“我主要是想维护到一起”；“我的意思还是每张卡的 dek 放到 vault 中” | 方案改为每张卡的 DEK 存 Vault（第 2 版），待批准例外决定 |
| 2026-10-15 | 2+3 | “可以，三项都同意，开始做吧”（方案第 2 版、Vault 托管卡 DEK 例外、本机建表） | 阶段 4 研发；在本机 `mor_payment_channel_test` 执行 `db/ddl.sql` |
| 2026-10-15 | 4 后 | “卡信息应该要是唯一的”“能通过明文卡号定位到这条卡信息”；期望未来存卡、存卡支付、对外卡 Token 与卡要素、定价使用卡要素；“card bin 统一 8 位”“不要 card brand 和 cardType，issuerCountry……以后 card bin 库查”；对分层模型回复“可以” | 重开阶段 1，修订需求为第 2 版（卡 / 卡凭证 / 交易工具分层） |
| 2026-10-15 | 1 | “少于16位的时候存前6吧……卡号>=16位的时候存8，<16的时候存6” | Q-06 定为 BIN 6～8 位，后 4 位都存 |
| 2026-10-15 | 1 | “没问题” | 需求第 2 版确认；进入阶段 2+3，出方案第 3 版 |
| 2026-10-15 | 2+3 | “可以，继续吧”（方案第 3 版及本机删旧表、建三表） | 阶段 4 按第 3 版返工 |
| 2026-10-15 | 4 | “继续” | 进入阶段 5；本机 Vault 注册未获单独授权，不做真实 Vault 联调 |
| 2026-10-15 | 5 | “要做，要提交”（真实 Vault 联调、提交） | 本机 Vault 注册被会话的自动权限检查拦截，注册脚本交用户执行；先本地提交本需求范围的改动 |
| 2026-10-15 | 5 | “继续执行” | 执行本机 Vault 注册脚本并联调，结果补记后再次本地提交 |
| 2026-10-15 | 5 | “做1”（整应用与真实 Vault 联调） | 本机启动 Payment 联调，结果补记后本地提交 |

## 当前阶段与下一确认点

已本地提交（提交说明含本需求目录名），未推送、未部署。本机 Vault 已注册并完成联调。

## 阶段 4 证据（2026-10-15，第 3 版）

第 2 版的阶段 4 实现（每个支付工具一条卡记录）已按第 3 版返工，其证据不再保留。

### SQL 执行记录

| 时间 | 目标 | 内容 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor-local-mysql-1`（127.0.0.1:13306）库 `mor_payment_channel_test`，root 账号（测试账号无 CREATE 权限） | 第 2 版 [db/ddl.sql](./db/ddl.sql)：`CREATE TABLE t_payment_card`（旧结构） | 成功，0 行（已被下一行取代） |
| 2026-10-15 | 同上 | `DROP TABLE t_payment_card`（执行前确认 0 行），再执行第 3 版 [db/ddl.sql](./db/ddl.sql) | 三张表建成，均 0 行；唯一索引 `uk_payment_card_fingerprint`、`uk_payment_card_credential` 已核对；未触碰 mor-test MySQL（23306） |

### 代码与工件

| 范围 | 改动 |
|---|---|
| 决定 | [Vault 托管卡 DEK 决定](../../../agent-docs/decisions/2026-10-15-vault-payment-card-dek.md)：补充第 3 版后 DEK 为每个卡号一把、Reveal 也用于已有卡再次申请、无引用 DEK 只来自并发首用；`VAULT_SERVICE_ARCHITECTURE.md` 顶部例外说明不变 |
| 模型/SQL | PDManer 新增 `t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument` 三个实体及关系、`Card` 图（相对已提交模型只增不改）；Schema、测试 `init/ddl.sql`、需求 `db/ddl.sql` 同步 |
| Card | `CardCipher` 拆为卡号、凭证、CVV 三类加解密（AAD 分别绑定卡、凭证、交易工具 ID），新增 `fingerprint`、`credentialDigest`、`bin`、`last4`、`normalizeCardholderName`；三表各一套 DataObject/Mapper/XML/Repository；新增模块内 `CardStorageService`（`findCard(pan)`、`saveCard`、`saveCredential` 撞唯一键改读已有、`findInstrument`）；`CardService` 只保留 `applyInstrument`（插入交易工具）与 `riskAttributes`；三个 Processor 按 §2.2/2.4 改写，核对不再访问 Vault；`PreparedCard` 换为 `PreparedInstrument`；CVV Redis Key 改按交易工具 ID |
| Execution、Web | 不改 |
| Test | `ChannelTestMapper` 三表计数、清理与查询；`ChannelDataMocker` 按交易工具清理 CVV、清三表；`CardStorageTest` 14 例；`CardCipherTest` 5 例 |
| 文档 | Payment README、配置清单、Nacos 模板注释、`CURRENT_IMPLEMENTATION.md` |

### 验收映射

| AC | 实现 | 验证 |
|---|---|---|
| AC-01 新卡三层 | Prepare：新卡 → Protect → 加密卡号 → 存卡；新凭证；事务内插入交易工具 | `test_applyStoresEncryptedCard`（三表各 1、密文不含卡号/持卡人、BIN 8 位、后四位、Protect 1 次、CVV TTL）；`test_shortPanBin`（15 位卡 BIN 6 位） |
| AC-02 同卡复用 | `CardStorageService.findCard` → Reveal | `test_sameCardReused`（卡 1、凭证 1、工具 2；Protect 1、Reveal 1） |
| AC-03 新凭证与规范化 | `credentialDigest` + 唯一索引 | `test_credentialPerExpiryAndName`（换年份、换姓名各 +1，大小写/空白不同不增加）；`CardCipherTest.test_credentialDigest` |
| AC-04 按卡号定位 | `CardStorageService.findCard(pan)` | `test_findCardByPan` |
| AC-05 并发首用 | `saveCard` 捕获 `DuplicateKeyException` 改读 | `test_concurrentFirstUse`（两次 200，卡 1、凭证 1、工具 2）；是否实际撞键取决于时序，未单独断言撞键分支 |
| AC-06 重启可读 | Card 无进程内状态 | `test_revealReturnsStoredCardOnce`；重启场景未单独测试 |
| AC-07 CVV | 按交易工具 ID 暂存、`GETDEL` | `test_revealReturnsStoredCardOnce`（领取后删除、再读 400）、`test_securityCodePerInstrument`（同卡两笔互不影响） |
| AC-08 重放 | Check 比较指纹与凭证摘要 | `test_replaySameCard`（不新增、不访问 Vault、CVV 不变）、`test_replayDifferentCardRejected`（409） |
| AC-09 Risk 属性 | `riskAttributes` 经交易工具关联到卡 | `PayRiskEvaluationTest` 20 例通过 |
| AC-10 指纹密钥 | `PaymentCardProperties` | `PaymentCardPropertiesTest` 6 例 |
| AC-11 卡号校验 | Luhn、有效期 | `test_invalidCardRejected`（2 例，三表与 Token 均为空、不访问 Vault） |
| AC-12 不泄漏 | 新类型 `toString` 只含 ID；异常不含卡值 | `test_cardValuesNotLogged`（卡号、持卡人、指纹、凭证摘要、DEK Token）；CVV 与有效期为短数字，易与日志中其他数字巧合，未纳入日志检索 |
| AC-13 事务与 503 | Vault/Redis 只在 Processor、主事务外 | `test_vaultUnavailableFailsClosed`；全局 `assertOutsideTransactions` |
| AC-14 现有测试 | — | `PayCardChannelTest` 7、`PayRiskEvaluationTest` 20 通过 |

### 开发检查

- `./mvnw -o verify`（JDK 25）：**108/108** 通过。日志在 scratchpad `verify-v3.log`。
- 测试后本机库三张卡表与 `t_payment_instrument_token` 均为 0 行，Redis 无 `card:cvv:*` 残留。

### 偏离与说明

- 技术方案写的是 `CardService.findCard(pan)`。实现放在模块内的 `CardStorageService`，因为它返回 DataObject，不宜放进 Execution 也在使用的 `CardService`。
- 技术方案写的是读卡时一次 JOIN 查询。仓库规定一个 Repository 对应一张表，所以改为三次主键查询（交易工具 → 凭证 → 卡），由 `CardStorageService.findInstrument` 组合。
- Vault 幂等键、503、测试客户端不重试三点与第 2 版相同。

### 阶段 4 时的未完成项（处理结果见阶段 5）

- 真实 Vault 联调（需在本机 Vault 注册 `PAYMENT_CARD_DEK` 与两条访问规则）未做。
- Redis 不可用分支未在共享本地环境模拟。
- 需求站测试 `npm --prefix requirements-site test` 尚未运行。

## 阶段 5 证据（2026-10-15）

| 检查 | 结果 |
|---|---|
| Payment `./mvnw -o verify`（阶段 4 末次） | 108/108 通过；本机库与 Redis 无残留 |
| `npm --prefix requirements-site test` | 137 项：128 通过、9 失败。9 项失败与本需求开始前的基线（同一工作区更早一次运行）名称完全一致，涉及 Commerce、Gateway、台账快照、测试规范等，与本需求无关 |
| 本机 Vault 注册 | 经用户授权，以管理接口在本机 Vault（127.0.0.1:18107）新建数据类型 `PAYMENT_CARD_DEK`（REFERENCE、SECRET、人工加解密关闭）与两条访问规则（Protect/`CARD_STORAGE`、Reveal/`CARD_USE`），策略版本 8 → 11；未触碰 mor-test |
| 真实 Vault 联调 | 以 `payment-orchestration-service` 身份按 `CardKeyClientImpl` 的参数直接调用 Vault Protect/Reveal 一把随机测试 DEK，7 项全部通过：Protect 200；Token 为 43 位 Base64URL（与 `dek_vault_token CHAR(43)` 一致）；同幂等键重放返回同一 Token；`CARD_USE` Reveal 取回原 32 字节 DEK；Reveal 用 `CARD_STORAGE`、Protect 用 `CARD_USE` 均 403 `ACCESS_DENIED`；另一把 DEK 得到不同 Token。联调留下测试 DEK 记录（Vault 不提供删除） |
| 整应用联调 | 本机启动 Payment 可执行 JAR（关闭 Nacos，Vault 经 simple discovery 指向 127.0.0.1:18107，数据库为当前结构的测试库 `mor_payment_channel_test` 与测试账号，指纹密钥为本次随机值），经真实 HTTP 入口：两个 Intent 用同一张公开测试卡申请、原提交重放、第三个 Intent 换有效期，均 200，重放返回原 Token。落库：卡 1（BIN `41111111`、后四位 `1111`、Token 43 位、卡号密文 47 字节且不含明文）、凭证 2（不含持卡人明文）、交易工具 3、Redis CVV 3。Vault 审计：本次 Payment 产生 Protect/`CARD_STORAGE` 1 次、Reveal/`CARD_USE` 2 次，重放未访问 Vault。Payment 日志无 ERROR，不含卡号与持卡人。结束后停止应用并清空本次写入的测试库行与 Redis CVV。读卡接口需 pay 链路（Risk、Platform）未在此覆盖，其 Reveal 路径与申请共用同一 `CardKeyClient` |
| Redis 不可用分支 | 未验证：共享本地环境中不能停 Redis |
| 进程重启后读卡 | 未单独测试：Card 没有进程内状态，读卡只依赖数据库、Vault、Redis |

### 部署前清单（各环境）

1. 执行 [db/ddl.sql](./db/ddl.sql)（三张表）。
2. 在 Vault 注册数据类型 `PAYMENT_CARD_DEK`（`REFERENCE`、`dataClass=SECRET`、人工加解密关闭）及访问规则 `payment-orchestration-service` + `PROTECT` + `CARD_STORAGE`、`payment-orchestration-service` + `REVEAL` + `CARD_USE`。
3. 配置环境变量 `PAYMENT_CARD_FINGERPRINT_KEY`（Base64，解码后至少 32 字节，不可轮换，不写入 Nacos 或版本库）。
