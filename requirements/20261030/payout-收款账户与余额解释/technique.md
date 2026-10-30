# payout：收款账户与余额解释 · 技术方案

> 状态：**阶段 2、3 已确认；阶段 4 实施完成（2026-10-30，合同修订见 8.7）**：第 1—5 节为阶段 2 只读核对，第 6—13 节为阶段 3 方案；未改代码、未执行 SQL。阶段 1 需求见 [requirement.md](./requirement.md)；确认记录见 [delivery.md](./delivery.md)。权威设计：[2026-10-30 payout 决定](../../../agent-docs/decisions/2026-10-30-payout-settlement-payout-model.md)、[design-discuss/payout/design.md](../../../design-discuss/payout/design.md) 第 8.1、9、10 节。
>
> 证据来源：只读核对代码与配置（无 SQL 执行、无环境改动）。路径相对仓库根。

## 1. 核对结论（F-xx）

| # | 事实 | 证据 | 影响 |
|---|---|---|---|
| F-01 | payout 只有模型与 Schema SQL，无任何代码；`paytech/pom.xml` 中 payout 模块被注释；`mor_payout` 库与 `payout_app` 账号在本机 / mor-test 均不存在 | `paytech/payout/`、`paytech/pom.xml:53`、`local-environment/mysql/init/01-create-databases-and-users.sh`（无 payout）、`.env.example`（无 `MOR_PAYOUT_DB_PASSWORD`） | R1 从零建工程与库 |
| F-02 | instance-manifest 与 arch-rules 已登记 payout（端口 18117、`mor_payout`、8 张表归属） | `tools/instance-manifest/.../services.yaml:18`、`tools/arch-rules/.../table-ownership.yaml:261-270` | 只需随表结构调整更新归属清单 |
| F-03 | `t_beneficiary_account` 现有 23 列，无 `cooling_until` / `is_default` / `suspended_reason`；无 FK / CHECK | `payout-schema.sql:7-35` | 按 8.1 加列；单默认账户只能应用内保证 |
| F-04 | Vault 客户端 `protect` 只返回 `token`（`ProtectResult(String token)`），没有 record version / metadata hash，也没有盲索引接口；等值查询的既有做法是 **STABLE Token**（同值同 Token，client-master `t_party_identity_key`） | `vault/paytech-vault-client/.../ProtectResult.java:11`、`client-master/.../ClientIdentifierVaultClientImpl.java:25-43`、`requirements/20261027/client-master-全球合规入驻与资格切片1/db/vault/dml.sql:6-9` | 模型里 `bank_account_blind_index` / `_blind_key_ver` / `vault_record_version` / `vault_metadata_hash` 四列没有来源；AGENTS 要求"无消费者的 Vault 字段一律不加"（S-01） |
| F-05 | Vault 新增数据类型与调用授权只需 `mor_vault` DML（`t_vault_data_type`、`t_vault_access_rule`、`t_vault_policy_change`），10 秒内热加载，不改 Go | `vault/docs/database/sql/vault-schema.sql:62,80,95`；示例 client-master 切片 1 `db/vault/` 四件套 | 假设 A4 成立 |
| F-06 | 运营端已有可复用的 Reveal 组件与入口：`vault/SensitiveValue.tsx`（遮罩 + 倒计时）；Reveal 由业务 Owner 发起 | `operations-console-web/src/vault/SensitiveValue.tsx:27-40`、`channel/ChannelCredential.tsx` | 核验页复用组件；Reveal 走 payout 接口（payout 以自己的调用方身份 Reveal） |
| F-07 | account `BalanceQueryApi` 按 **单一 OE × 币种**查询，entityNo 与 currency 必填；Client 返回 PENDING / AVAILABLE / LOCKED、保证金、追偿；Partner 无保证金字段；Owner 无账户时 400 `OWNER_ACCOUNT_NOT_FOUND`；无成熟时间线接口 | `account/paytech-account-api/.../BalanceQueryApi.java:22,37,54`、`ClientBalanceResponseBody` | 假设 A1 成立（不含时间线）；余额页需要"该 Owner 有哪些 OE × 币种"的列表来源（S-04） |
| F-08 | client-master **没有**供服务间调用的"按 Client / Local Partner ID 取法定名与状态"接口：`ClientWorkspaceApi /client-workspace/v1/client-summaries` 依附 CUSTOMER 会话；`/client-master/v1/party-profiles` 按 party ID 且只允许 commercial / compliance；法定名在运营 Admin 接口 `GET /client-master-admin/v1/clients/{id}`、`/local-partners/{id}` | `client-master/paytech-client-master-api/.../ClientWorkspaceApi.java:25`、`ClientMasterCheckoutApi.java:57`、`ClientOnboardingAdminApi.java:93`、`ClientMasterEntityAdminApi.java:114` | 假设 A3 **不成立**（S-02） |
| F-09 | commercial **没有**"Client × OE × 开通项代码 → 状态"查询；`AgreementQueryApi /agreements/v1` 只能分页列合同、无开通项过滤 | `commercial/.../agreement/AgreementQueryApi.java:32-41`、`ProductCatalogApi.java:53` | 阻断原因"开通项未生效"本期无来源（S-03） |
| F-10 | compliance `SettlementControlApi` 只有接口声明，**无 Controller / Processor 实现** | `compliance/paytech-compliance-api/.../SettlementControlApi.java:21,34,51`；core / app 无引用 | 阻断原因"结算控制"本期无来源（S-03） |
| F-11 | Platform 通知：`POST /notifications/v1/requests` 只接受**显式邮箱**收件人（无"发给某 Client 的成员 / 角色"类型）；调用方按 `notification.http.callers`（服务 → 模板码）白名单，默认全拒；mor-test `NOTIFICATION_ENABLED=true` | `platform-support/.../notification/NotificationCommandApi.java:37`、`application.yml:197,231-234`、`test-environment/compose.applications.yaml:212` | 假设 A2 部分成立：能发，但 payout 拿不到 Client 成员邮箱（S-05） |
| F-12 | Platform Webhook 事件类型是自由字符串，靠 `webhook.http.callers` 白名单约束 | `webhook/WebhookEventApi.java:37`、`application.yml:252-255` | Q-2 已定本期不做，S2 再登记 |
| F-13 | Client BFF 身份来自 Gateway 头（账号、会话、tenant、member），`ClientActorResolver`；Platform `ClientWorkspaceResponseBody` 带 `clientId`（租户 owner_ref_id） | `client-console-bff/.../ext/rest/ClientActorResolver.java:25-53`、`platform-support-api/.../ClientWorkspaceResponseBody.java:15` | Client 门户可得到当前 clientId；BFF 现无任何余额 / 出款接口 |
| F-14 | Client Web 是扁平路由 + `WorkspacePage` 分区；列表 + 弹窗参照 `TeamPage.tsx`，分步向导参照 `onboarding/`；i18n `useT(namespace)` + `i18n/en/*.json` + 覆盖率测试 | `client-console-web/src/main.tsx:173-184`、`TeamPage.tsx`、`i18n/react.tsx:19` | 新增命名空间 `payout`；无既有余额 / 收款账户页 |
| F-15 | 运营端 Maker-Checker 模板：财务人工调整（`FinanceManualAdjustmentController` + `ManualAdjustmentsTab.tsx`）与入驻审核四眼（`clientReview/ReviewDecision.tsx:59-60` `fourEyesBlocked`）；按钮权限由 `navigation/pageCapabilities.ts` 的 `resourceCode` + `hasAction` 控制 | 见路径 | 核验工作台复用 clientReview 的四眼与布局 |
| F-16 | Platform 注册块已用到 145（tax，2026-10-29），144 为 risk-decision 限额档位；块 141 未写入基线 | `requirements/20261029/tax-结账算税与事件登记/release/sql/mor_platform-block-145*`、`CURRENT_IMPLEMENTATION.md:47` | 本需求暂定 146，阶段 6 以 mor-test 只读核对为准 |
| F-17 | 新服务接入清单（以 tax 为参照）：`paytech/pom.xml`、四模块、`services.yaml`、本地 mysql init 与 `.env.example`、mor-test `compose.applications.yaml`（固定 IP，现最高 .42）、`compose.yaml` 密码变量、`initialize.py` / `prepare.py` / `build.sh`、健康探针白名单（`LocalHttpHealthProbe.java` 需重编译）、`test-environment/README.md`；XXL 执行器需从已有服务复制（tax / reconciliation 无） | 见 [mor-test 新服务清单](../../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md) 与上述文件 | R1 工作量主要在环境接入 |

## 2. 需求项差异

| 需求项 | 当前 | 目标 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 工程骨架 | 无代码、无库 | 四模块 + `mor_payout` + 本地 / mor-test 接入 | 全部 | 参照 tax 新建；XXL 执行器参照有执行器的服务 |
| R2 收款账户模型 | 23 列模型 | 8.1 目标列 | 缺 3 列；4 列 Vault 字段无来源 | 加 3 列；按 S-01 改造 Vault 字段 |
| R2 事件表 | `t_payout_event` 绑定出款单（`payout_id` 非空、`event_scope` PAYOUT / EXECUTION） | 记录收款账户状态变化 | 结构不适配 | 新建 `t_beneficiary_account_event`（S-06） |
| R3 Vault | 无策略 | 收款账户 Protect / Reveal、等值查重 | DML 未写 | `mor_vault` 新增 2 个数据类型 + 访问规则（S-01） |
| R4 Processor | 无 | 提交、核验、默认、停用 / 恢复、关闭 | 全部 | 新建；单资源写用 `AbstractLockProcessor`（锁收款账户或 Owner × 币种） |
| R5 字段要求 | 无 | AE / SA / EG 本地 + SWIFT 静态清单 | 全部 | payout 配置资源文件（Q-1 已定） |
| R6 余额解释 | account 单 OE × 币种查询 | 按 Owner 列出各 OE × 币种 + 阻断原因 | 列表来源、开通项、结算控制、法定名均缺 | 见 S-02、S-03、S-04 |
| R7 通知 | Platform 只发显式邮箱 | 通知 Owner | 无收件人来源 | 见 S-05 |
| R8 Client 页面 | 无 | 收款账户页、余额页 | 全部 | BFF 新控制器；Web 新命名空间 |
| R9 运营页面 | 无 | 核验工作台、按 Owner 查看 | 全部 | 复用 clientReview 四眼、SensitiveValue |
| R10 注册 / 语言包 | 无 | 块 146、两个命名空间 | 全部 | 阶段 3 定行数 |

## 3. 逐系统影响

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| payout | 收款账户 Owner；余额解释聚合 | 工程、`mor_payout` 5—6 表、api 合同、Processor、出站客户端（account、client-master、Vault、Platform 通知 / 文件 / 工作区）、补偿事件、XXL（冷却到期通知） | 全部新建 | 本需求的主体；测试需本地 Loopback MySQL / Redis / RabbitMQ |
| account | 余额 Owner | 无改动；按 OE × 币种逐个查询 | 否 | 列表来源见 S-04 |
| client-master | 法定名与 Owner 状态 | 新增服务间只读接口（S-02 方案 A） | 小改代码 + 调用方配置 | 需 client-master Owner 认可；否则走方案 B |
| commercial | 开通项 | 本期不改（S-03） | 否 | S2 前置：开通项状态查询 |
| compliance | 结算控制 | 本期不改（S-03） | 否 | S2 前置：`SettlementControlApi` 实现 |
| Vault | 原值保管 | `mor_vault` DML：数据类型 `PAYOUT_BENEFICIARY_V1`（REFERENCE，JSON：户名、账号 / IBAN、银行代码、SWIFT）与 `PAYOUT_BANK_ACCOUNT_KEY_V1`（STABLE，账号规范值，只用于等值查重）；访问规则 payout-service PROTECT 两类、REVEAL 前者（用途 `PAYOUT_BENEFICIARY_REVIEW`） | DML | 原值不进 payout 日志与事件；Reveal 审计在 Vault 侧 |
| Platform Support | 通知、注册、语言包、工作区 | 通知模板 4 个（核验通过 / 拒绝 / 冷却结束 / 停用）+ `notification.http.callers` 加 payout-service；注册块 146；语言包 client-console / operations-console | DML + 配置 | 收件人见 S-05 |
| Client Console BFF / Web | 门户 | BFF `PayoutBeneficiaryController`、`PayoutBalanceController`（clientId 取工作区）；Web 收款账户页、余额页、导航项、`payout` 命名空间 | 代码 / 页面 | 先 Pencil |
| Operations Console BFF / Web | 运营 | BFF 核验队列、详情、Maker / Checker、Reveal、按 Owner 查询；Web 核验工作台、Owner 余额解释、`pageCapabilities` | 代码 / 页面 | 先 Pencil |
| Gateway | 入口 | 无代码；块 146 激活后快照离线编译 | 否 | — |
| 环境 | 本地 / mor-test | F-17 清单；固定 IP `172.30.73.44`、Snowflake Region 0 / Worker 16（阶段 6 核对：.43 已被 screening-mock 占用，Worker 17—31 已占） | 配置 | 共享 Compose 改动需用户合入（既往） |
| 文档 | — | CURRENT_IMPLEMENTATION、LOCAL_DEVELOPMENT_ENVIRONMENT、requirements-site FIN-16 / FIN-I16 | 文档 | — |

## 4. 待决问题（阶段 2 → 阶段 3）

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| S-01 | Vault 字段：模型里的盲索引、盲键版本、Vault 记录版本、元数据哈希四列在现有 Vault 客户端里都没有来源 | A：删这四列；原值整体存一个 REFERENCE Token，另用账号规范值的 STABLE Token（`bank_account_token`）做等值查重（同 client-master 做法）<br>B：保留盲索引列，payout 本地用 Vault SECRET 密钥算 HMAC（card 做法）<br>C：给 Vault 加盲索引接口（改 Go） | **A**：零 Vault 代码、与既有模式一致；同 Owner 重复用 `(owner, bank_account_token)` 唯一，跨 Owner 共用用 `bank_account_token` 普通索引查 |
| S-02 | 法定名来源（核验页"户名 = 法定名"对照） | A：client-master 新增服务间只读接口 `GET /client-master/v1/owners/{ownerType}/{ownerId}/legal-profile`（法定名、状态、国家），调用方白名单 payout-service<br>B：payout 不取法定名，由 Operations BFF 调现有 Admin 接口拼到核验页 | **B**：核验是运营页面行为，BFF 聚合是 AGENTS 规定的 BFF 职责；payout 本期没有其他用到法定名的业务规则，不为 S1 改 client-master |
| S-03 | 余额解释的阻断原因"开通项未生效""结算控制"本期无 Owner 接口 | A：本期只给可判定的原因（无可用账户、账户冷却中、追偿未清、无余额），这两项显示"S2 起检查"<br>B：本期补 commercial 开通项查询与 compliance 结算控制只读实现 | **A**：两者是 S2 出款单创建的前置，在 S2 一起做更完整；本期接口预留 `reasons[]` 枚举值 |
| S-04 | 余额页需要"该 Owner 在哪些 OE × 币种有余额"，account 只支持单个查询 | A：account 新增 `GET /balances/v1/clients/{id}/positions`（列出全部 OE × 币种余额）<br>B：payout 先从 commercial 合同列表取 OE，再按币种逐个查 account<br>C：余额页让用户先选 OE 与币种 | **A**：余额 Owner 给列表是自然职责、一次查询；改动小（同一表按 owner 过滤） |
| S-05 | 通知收件人：Platform 只收显式邮箱，payout 无从得知 Client 成员邮箱 | A：Platform 通知新增收件人类型"某租户内具备某权限的成员"（服务端解析邮箱）<br>B：收款账户提交时记录提交人账号 ID，通知只发给提交人（payout 调 Platform 按账号 ID 取邮箱的内部接口）<br>C：本期不发邮件，只在门户站内显示状态变化，邮件随 S2 一起解决 | **A**：出款、退回、核验都需要"通知 Client 的财务成员"，这是 Platform 通知的通用缺口，S1 一次补齐；工作量在 Platform（解析租户成员与权限），payout 只传 `tenant=CLIENT:{id}` + `permission=PAYOUT_NOTIFY`。若 Platform 本期排不进，退到 C |
| S-06 | 收款账户状态变化的审计 | A：新建 `t_beneficiary_account_event`（账户 ID、序号、前后状态、原因、操作人、来源键）<br>B：扩展 `t_payout_event` 加 `beneficiary_account_id` 与 scope | **A**：两种聚合各自一条连续事件链，S2 的 `t_payout_event` 保持纯出款语义 |
| S-07 | 冷却到期通知的触发 | A：XXL 定时扫 `cooling_until <= now` 且未通知的账户<br>B：不通知冷却结束，只在核验通过的邮件里写"预计可用时间" | **B**：少一个定时任务和一个字段；状态由 `cooling_until` 实时判定，不需要任务改状态 |

## 5. 规模与风险

- **规模：** L。新服务 + 6 个受影响系统（payout、Vault DML、Platform、Client BFF / Web、Operations BFF / Web；S-02 / S-04 / S-05 按建议另加 account 一个接口、Platform 通知一种收件人类型）。主要工作量在 payout 骨架与环境接入（F-17）、两端页面。
- **风险：**
  1. 新服务首次进 mor-test：固定 IP、探针白名单、XXL 根路径、密码变量都要一次做对（参照 mor-test 新服务清单）。
  2. S-05 选 A 时 Platform 通知改动跨团队并行，需要先定合同。
  3. Vault 原值只进 Vault：payout 日志、事件载荷、补偿事件、BCR 都不得带原值，阶段 4 加测试断言。
  4. 静态字段清单（Q-1）与 S3 channel-connector 能力是两份来源，S3 切换时要退役清单，阶段 3 在配置里标注到期。

## 6. 方案总览

**一句话规模：** 本次方案涉及 7 个系统（payout 新建、account、Platform Support、Vault、Client BFF / Web、Operations BFF / Web）；新增 Owner 接口 15 个（payout 12、account 2、Platform 通知请求体 1 处增量），BFF 转发 / 聚合 Client 7 个、Operations 12 个（阶段 4 加字段要求接口）；新建库 `mor_payout` 3 张表（`t_beneficiary_account` 31 列含生成列与阶段 4 两列备注、`t_beneficiary_account_event` 13 列、`t_compensation_event` 标准 15 列），修改旧表 0 张（Platform 通知表不改结构）；Vault DML 2 个数据类型 + 3 条访问规则 + 1 条策略变更；Platform 注册块 1 个（阶段 4 实际：块 147，权限 8、管理员授权 5、Client 租户系统角色授权按工作区回填、资源 7、Gateway 路由 19 及其头 / 查询参数 / 守卫、通知模板 4 × 2 语言）、通知模板 4 个 × 2 语言；页面原型 Client 5 张、Operations 4 张。

**主流程：**

```
Client 门户 ──提交账户──▶ Client BFF ──▶ payout
                                         ├─(事务外) Vault Protect ×2 → 原值 Token + 账号 STABLE Token
                                         ├─ 本地事务：查重 → 建账户 PENDING_REVIEW + 事件 + 补偿事件(通知:SUBMITTED 可选)
运营 Maker ──意见──▶ Ops BFF ──▶ payout：记录 Maker 意见（仍 PENDING_REVIEW）+ 事件
运营 Checker ─决定─▶ Ops BFF ──▶ payout：不同人 + content_hash 一致 → VERIFIED(cooling_until) / REJECTED + 事件 + 补偿事件(通知)
补偿事件 ──▶ Platform 通知（tenantRecipients：租户 × 权限）──▶ 邮件
Client 余额页 ──▶ Client BFF ──▶ payout 余额解释 ──(事务外) account positions + 本库可用账户 → 阻断原因
```

**事务与并发：** 所有出站（Vault、account、Platform）在本地事务之外；写命令用 Foundation `AbstractLockProcessor` 锁 `PAYOUT_BENEFICIARY_OWNER:{ownerType}:{ownerId}`（同 Owner 的新建、默认切换、查重串行），数据库层由 `uk_beneficiary_owner_token`（见 7.1）兜底；单账户命令另带 `expectedVersion` 乐观锁。

**幂等：** HTTP 请求幂等归 Gateway（`Idempotency-Key`）；业务层：新建以 `(owner, bank_account_token)` 唯一 + 同内容返回原账户；Maker / Checker 以 `(account_id, stage)` 只允许一次；通知以 `sourceEventKey = BENEFICIARY:{id}:{eventNo}`。

## 7. 数据设计（`mor_payout`）

### 7.1 `t_beneficiary_account`（改造，24 列）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `id` | BIGINT | N | Snowflake 主键 |
| `owner_type` | VARCHAR(32) | N | CLIENT、LOCAL_PARTNER（PAYEE 预留，不开放） |
| `owner_id` | BIGINT | N | Client / Local Partner ID |
| `account_code` | VARCHAR(16) | N | Owner 内可读编号 `BA-0001` 递增（锁 Owner 后取 max+1） |
| `rail_type` | VARCHAR(16) | N | **新增** LOCAL、SWIFT（决定字段要求；S3 后由渠道能力校验） |
| `bank_country` | CHAR(2) | N | ISO 3166-1 |
| `currency` | CHAR(3) | N | ISO 4217 |
| `bank_name` | VARCHAR(128) | N | **新增** 银行显示名（非敏感，来自字段清单或用户输入） |
| `bank_code` | VARCHAR(32) | Y | 本地银行代码 / SWIFT BIC（非敏感）；**改为可空**（部分国家只要 IBAN） |
| `beneficiary_vault_token` | VARCHAR(128) | N | REFERENCE Token：JSON{holderName, accountNumber/iban, bankCode, swiftBic, accountType} |
| `bank_account_token` | VARCHAR(128) | N | **替换盲索引** STABLE Token：账号规范值（去空格、大写） |
| `holder_name_masked` | VARCHAR(255) | N | 户名脱敏（改名自 `beneficiary_name_masked`） |
| `account_last4` | CHAR(4) | N | 账号末四位 |
| `content_hash` | CHAR(64) | N | 冻结字段 Canonical SHA-256（提交即冻结，改为非空） |
| `submitted_by_type` | VARCHAR(16) | N | **新增** CLIENT_MEMBER、OPERATIONS |
| `submitted_by` | BIGINT | N | **改名自 `maker_id`** 提交人 Platform 账号 ID |
| `review_maker_id` | BIGINT | Y | **新增** 核验 Maker 账号 ID |
| `review_maker_decision` | VARCHAR(16) | Y | **新增** APPROVE、REJECT |
| `checker_id` / `checker_decision` / `decision_time` | — | Y | 不变；`checker_decision` APPROVED、REJECTED |
| `reason_code` | VARCHAR(64) | Y | **新增** 拒绝 / 停用 / 关闭原因（受控目录，见 7.4） |
| `review_maker_note` | VARCHAR(500) | Y | **阶段 4 新增** Maker 内部备注，只给运营；恢复时清空 |
| `reason_note` | VARCHAR(500) | Y | **阶段 4 新增** 拒绝时给 Owner 的说明，随拒绝通知 `reasonText` 发送；停用 / 关闭 / 恢复清空 |
| `cooling_until` | DATETIME(6) | Y | **新增** VERIFIED 时 = decision_time + `payout.beneficiary.cooling-hours` |
| `is_default` | TINYINT | N | **新增** 默认 0 |
| `status` | VARCHAR(16) | N | PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED |
| `version` / `create_time` / `update_time` | — | N | 标准 |

删除：`bank_account_blind_index`、`bank_account_blind_key_ver`、`vault_record_version`、`vault_metadata_hash`（S-01）；`suspended_reason` 合并入通用 `reason_code`。

索引：PK；`uk_beneficiary_acct (owner_type, owner_id, account_code)`；`uk_beneficiary_owner_token (owner_type, owner_id, bank_account_token, active_key)` —— `active_key` 为生成列 `IF(status IN ('REJECTED','CLOSED'), id, 0)`，使同 Owner 同账号只能有一个未终结账户（MySQL 8 生成列 + 唯一索引，非 CHECK / FK）；`idx_beneficiary_owner (owner_type, owner_id, status)`；`idx_beneficiary_token (bank_account_token)`（跨 Owner 共用提示）；`idx_beneficiary_review (status, review_maker_id, create_time)`（核验队列）。

单默认账户：应用内保证（锁 Owner，先清旧默认再设新默认，同事务）。

### 7.2 `t_beneficiary_account_event`（新建，13 列）

`id`、`beneficiary_account_id`、`event_no`（账户内从 1 连续）、`from_status`（首条为空）、`to_status`、`action`（SUBMIT、MAKER_REVIEW、CHECKER_DECIDE、SET_DEFAULT、UNSET_DEFAULT、SUSPEND、RESUME、CLOSE、REVEAL）、`reason_code`、`actor_type`（CLIENT_MEMBER、OPERATIONS、SYSTEM）、`actor_id`、`source_key`（命令幂等键 / Gateway 请求 ID）、`account_version_after`、`occurred_time`、`create_time`。唯一 `(beneficiary_account_id, event_no)`。REVEAL 也记一行（不改状态，`to_status = from_status`），与 Vault 侧审计互证。

### 7.3 `t_compensation_event`（标准表，照抄 reconciliation 同名表）

handler：`BENEFICIARY_NOTIFICATION`（sub_event_type = 模板码；event_key = `{accountId}:{eventNo}`）。

### 7.4 受控目录（api 枚举）

- `BeneficiaryRejectReason`：HOLDER_NAME_MISMATCH、INVALID_ACCOUNT、UNSUPPORTED_BANK、DOCUMENT_REQUIRED、OTHER。
- `BeneficiarySuspendReason`：RISK_REVIEW、OWNER_REQUEST、PAYOUT_FAILURE（S2/S3 使用）、OTHER。
- `BeneficiaryCloseReason`：OWNER_REQUEST、REPLACED、OPS。
- `BeneficiaryBlockReason`（余额解释）：NO_AVAILABLE_BALANCE、NO_USABLE_ACCOUNT、ACCOUNT_COOLING、RECOVERY_OUTSTANDING；预留 CAPABILITY_NOT_ENABLED、SETTLEMENT_CONTROLLED（S2）。

### 7.5 字段要求静态清单（Q-1）

`payout-app/src/main/resources/beneficiary-requirements.yaml`，键 `rail × bankCountry × currency`，值为字段数组 `{code, required, pattern, maxLength, labelKey}`；首批：

| rail | 国家 | 币种 | 字段 |
|---|---|---|---|
| LOCAL | AE | AED、USD | holderName、iban（`AE\d{21}`）、bankName |
| LOCAL | SA | SAR、USD | holderName、iban（`SA\d{22}`）、bankName |
| LOCAL | EG | EGP | holderName、accountNumber、bankCode、bankName |
| SWIFT | *（ISO 国家） | USD、EUR | holderName、iban 或 accountNumber（二选一）、swiftBic（`[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?`）、bankName、bankCountry |

文件头标注"S3 由 channel-connector 出款账户能力取代后删除"。

### 7.6 PDManer 与 Schema

直接编辑 `paytech/payout/docs/database/payout.pdma.json`（不用生成器）：改 `t_beneficiary_account`、新增 `t_beneficiary_account_event`；其余 5 张出款表本期不动、不建库（S2 改造后再建）。`payout-schema.sql` 同步；本需求 `db/payout/ddl.sql` 只含 S1 三张表的建表语句（空库首建）。arch-rules `table-ownership.yaml` 加 `t_beneficiary_account_event`。

## 8. 接口设计

约定：payout 内部 HTTP，`@HttpExchange` 在 `paytech-payout-api`；调用方以 `X-Caller-Service` + internal-auth（沿用各服务现有模式）；错误为 RFC 9457 Problem；金额、ID 用字符串。

### 8.1 payout（12 个）

| # | Method / Path | 调用方 | 说明 |
|---|---|---|---|
| P1 | `POST /payout/v1/beneficiary-accounts` | Client BFF、Ops BFF | 提交新账户。Body：`ownerType, ownerId, railType, bankCountry, currency, fields{holderName, iban?, accountNumber?, bankCode?, swiftBic?, bankName, accountType?}, actorType, actorId`。校验字段清单 → Vault Protect ×2 → 查重 → PENDING_REVIEW。201 返回账户视图；同 Owner 同账号已有未终结账户 → 409 `BENEFICIARY_DUPLICATE`（含已有账户 ID）；组合不支持 → 422 `BENEFICIARY_UNSUPPORTED_ROUTE` |
| P2 | `GET /payout/v1/beneficiary-accounts?ownerType&ownerId&status?&currency?` | 两个 BFF | Owner 账户列表（视图含 `usable`、`coolingUntil`、`sharedWithOtherOwners`（仅运营调用方返回）） |
| P3 | `GET /payout/v1/beneficiary-accounts/{id}` | 两个 BFF | 详情（不含原值） |
| P4 | `POST /payout/v1/beneficiary-accounts/{id}/default` | 两个 BFF | Body `{ownerType, ownerId, expectedVersion, actor…}`；非 VERIFIED → 409 |
| P5 | `POST /payout/v1/beneficiary-accounts/{id}/close` | 两个 BFF | Body `{reasonCode, expectedVersion, actor…}`；清默认 |
| P6 | `POST /payout/v1/beneficiary-accounts/{id}/suspend` | Ops BFF | `{reasonCode, expectedVersion, actorId}` |
| P7 | `POST /payout/v1/beneficiary-accounts/{id}/resume` | Ops BFF | SUSPENDED → PENDING_REVIEW（清 Maker / Checker 字段，重核） |
| P8 | `POST /payout/v1/beneficiary-accounts/{id}/maker-reviews` | Ops BFF | `{decision APPROVE/REJECT, reasonCode?, contentHash, expectedVersion, actorId}`；已有 Maker 意见 → 409 |
| P9 | `POST /payout/v1/beneficiary-accounts/{id}/checker-decisions` | Ops BFF | `{decision APPROVED/REJECTED, reasonCode?, contentHash, expectedVersion, actorId}`；actorId = review_maker_id 或 submitted_by（运营代提交时）→ 403 `FOUR_EYES_REQUIRED`；contentHash 不一致 → 409 |
| P10 | `POST /payout/v1/beneficiary-accounts/{id}/reveal` | Ops BFF | `{actorId, reason}` → 原值字段（不缓存、不日志）；Vault 用途 `PAYOUT_BENEFICIARY_REVIEW`；记 REVEAL 事件 |
| P11 | `GET /payout/v1/beneficiary-reviews?stage=MAKER\|CHECKER\|DONE&ownerType?&ownerId?&page&size` | Ops BFF | 核验队列：MAKER = 待 Maker；CHECKER = 有 Maker 意见待决定；DONE = 近 30 天已决定；每页 ≤ 50 |
| P12 | `GET /payout/v1/beneficiary-requirements?railType&bankCountry&currency` 与 `GET /payout/v1/balance-explanations?ownerType&ownerId` | 两个 BFF | 前者返回字段清单（无则 404）；后者返回 `positions[{entityNo, currency, pending, available, locked, securityDeposit?, recoveryReceivable, blockReasons[]}]` + `usableAccounts[{currency, count, defaultAccountId}]` |

另：`GET /payout/v1/beneficiary-accounts/{id}/events`（事件时间线）归入 P3 响应的 `events` 字段，不单列接口。

### 8.2 account（2 个，新增）

`GET /balances/v1/clients/{clientId}/positions`、`GET /balances/v1/partners/{partnerId}/positions` → `[{entityNo, currency, pendingAmount, availableAmount, lockedAmount, securityDepositAmount?, recoveryReceivableAmount}]`，按 `idx_owner_account_owner` 一次查询后按 entityNo × currency 聚合；Owner 无账户返回空数组（不再 400）。只读，调用方白名单加 payout-service。

### 8.3 Platform 通知（请求体增量，不改表）

`SubmitNotificationRequestBody` 增加可选 `tenantRecipients{tenantType: CLIENT|PARTNER, ownerRefId, permissionCode, locale?}`，与 `recipients` 二选一（同时给或都不给 → 422）。服务端在受理事务前解析：`t_access_tenant(tenant_type, owner_ref_id)` → ACTIVE 成员 `t_tenant_member` → 当前有效角色 `t_tenant_member_role` → `t_role_permission` 含该权限 → `t_user_account.email_vault_token`（已验证邮箱）→ 每人一条 `t_notification_delivery`（直接复用账号上的邮箱 Token，不 Reveal）。无收件人 → 200 + `deliveryCount=0`。上限 100 人沿用。白名单 `notification.http.callers.payout-service: [PAYOUT_BENEFICIARY_VERIFIED_EMAIL, PAYOUT_BENEFICIARY_REJECTED_EMAIL, PAYOUT_BENEFICIARY_SUSPENDED_EMAIL, PAYOUT_BENEFICIARY_CLOSED_EMAIL]`。

### 8.4 Client BFF（7 个，`/client-payout/v1`）

`GET /beneficiary-accounts`、`POST /beneficiary-accounts`、`GET /beneficiary-accounts/{id}`、`POST /beneficiary-accounts/{id}/default`、`POST /beneficiary-accounts/{id}/close`、`GET /beneficiary-requirements`、`GET /balances`。clientId 取当前工作区（Platform `ClientWorkspaceResponseBody.clientId`），不接受浏览器提交；读接口要求 `CLIENT_PAYOUT_ACCOUNT_READ`，写接口 `CLIENT_PAYOUT_ACCOUNT_MANAGE`，余额 `CLIENT_BALANCE_READ`。`GET /balances` 聚合 P12 余额解释 + OE 显示名（client-master 工作区摘要）。

### 8.5 Operations BFF（11 个，`/payout-admin/v1`）

`GET /beneficiary-reviews`、`GET /beneficiary-accounts`（按 Owner）、`GET /beneficiary-accounts/{id}`（聚合法定名：Client → `GET /client-master-admin/v1/clients/{id}`，Partner → `/local-partners/{id}`，S-02）、`POST /beneficiary-accounts`（代提交）、`POST …/{id}/maker-reviews`、`POST …/{id}/checker-decisions`、`POST …/{id}/reveal`、`POST …/{id}/suspend`、`POST …/{id}/resume`、`POST …/{id}/close`、`GET /balances?ownerType&ownerId`。actorId 取 Operations 会话账号。

### 8.6 权限（8 个）

| 渠道 | 权限码 | 风险 | 授予 |
|---|---|---|---|
| CLIENT | `CLIENT_PAYOUT_ACCOUNT_READ` | LOW | Client 系统角色 OWNER、ADMIN、MEMBER |
| CLIENT | `CLIENT_PAYOUT_ACCOUNT_MANAGE` | HIGH | OWNER、ADMIN（也是通知收件人判定权限） |
| CLIENT | `CLIENT_BALANCE_READ` | LOW | OWNER、ADMIN、MEMBER |
| OPERATIONS | `PAYOUT_BENEFICIARY_READ` | LOW | 平台管理员（休眠授权，激活时启用） |
| OPERATIONS | `PAYOUT_BENEFICIARY_SUBMIT` | MEDIUM | 同上 |
| OPERATIONS | `PAYOUT_BENEFICIARY_REVIEW` | HIGH | 同上（Maker 与 Checker 同一权限，四眼由服务端按人校验） |
| OPERATIONS | `PAYOUT_BENEFICIARY_REVEAL` | CRITICAL | 同上 |
| OPERATIONS | `PAYOUT_BENEFICIARY_MANAGE` | HIGH | 同上（停用 / 恢复 / 关闭） |

（余额查询复用 `PAYOUT_BENEFICIARY_READ`。）

### 8.7 阶段 4 合同修订（2026-10-30，集成时按原型补齐）

- P2 / P3 / P11 运营视图：`sharedWithOtherOwners` 布尔改为 `sharedOwners[{ownerType, ownerId, accountCode, status}]`（其他 Owner 的未终结账户，最多 5 条）与 `reviewMakerNote`；所有视图返回 `reasonNote`。
- P8：Body 加 `note?`（≤500，任意意见）与 `clientNote?`（≤500，只在 REJECT）。P9：Body 加 `clientNote?`（≤500，只在 REJECTED；未填沿用 Maker 的说明）。拒绝通知 `reasonText` = `原因代码` 或 `原因代码: 说明`。
- P10：`{actorId, reasonCode}`，`reasonCode` 受控 MAKER_REVIEW / CHECKER_REVIEW（替代自由文本 `reason`）。
- 请求体 Bean Validation 失败 422 `VALIDATION_ERROR`；字段清单规范化后的缺失 / 格式 / 不适用 400 `INVALID_REQUEST`。
- Checker 只有在 Maker 意见为 APPROVE 时可以批准；Local Partner Owner 不发邮件（无 Partner 门户与权限码）。
- Operations BFF 第 12 个接口 `GET /payout-admin/v1/beneficiary-requirements`（代提交用，权限 `PAYOUT_BENEFICIARY_SUBMIT`）；队列 / 详情响应加 `sharedOwnerProfiles`；Maker / Checker 请求体加 `note` / `clientNote`。
- Gateway：Client `/api/client/payout/v1/**` → `/client-payout/v1/**`；Operations `/api/operations/payout-admin/v1/**` → `/payout-admin/v1/**`；路径变量 `{accountId}`。
- P1 重复提交 409 `BENEFICIARY_DUPLICATE`：已有账户 ID 与状态写在 Problem `detail` 中（阶段 5 决定：Foundation 的 Problem 体不支持扩展字段，本需求不改 Foundation；需要完整账户时 BFF 调 P2 / P3）。
- Platform 业务拒绝（如邮件路由未启用）视为通知终态失败、不自动重试；配置修正后如需补发，按补偿事件受控重开处理。
- payout 访问 account 用 `PAYOUT_ACCOUNT_SERVICE_URL`（account 不注册服务发现）；访问 Platform 用 `lb://platform-support-service/platform-support`（阶段 5 修正）。
- 未补：O3 按名称搜索（client-master 无按名称的运营 Admin 接口，保持类型 + 编号）；Client 余额只给 OE 编号（Client 侧无 OE 名称合同）。

## 9. 数据库与注册 SQL（待执行文件，本阶段只评审）

| 文件 | 目标库 | 内容 | 性质 |
|---|---|---|---|
| [db/payout/ddl.sql](./db/payout/ddl.sql) | `mor_payout`（新建空库） | 3 张表 | 首建执行文件 |
| [db/vault/dml.sql](./db/vault/dml.sql) | `mor_vault` | 数据类型 `PAYOUT_BENEFICIARY_V1`（REFERENCE、JSON）、`PAYOUT_BANK_ACCOUNT_KEY_V1`（STABLE、TEXT）；访问规则 payout-service PROTECT ×2、REVEAL ×1（用途 `PAYOUT_BENEFICIARY_SUBMIT` / `PAYOUT_BENEFICIARY_REVIEW`）；策略变更 1 行 | 增量 |
| [db/platform-support/dml.sql](./db/platform-support/dml.sql) | `mor_platform` | 注册块 `__PAYOUT_BLOCK__`（暂定 146）：权限 8、角色授权、菜单 / 页面资源、Gateway 路由 18 及头 / 守卫（休眠写入 + 激活脚本）；通知模板 4 × zh-CN / en | 增量；块号阶段 6 只读核对后替换 |
| 库与账号 | MySQL root | `CREATE DATABASE mor_payout`、`payout_app` 账号（SELECT、INSERT、UPDATE、DELETE） | 本机走 `local-environment` init 脚本新增一行；mor-test 走 `initialize.py` |

执行顺序：库与账号 → payout DDL → Vault DML → Platform DML（休眠）→ 部署 payout / account / Platform / BFF / Web → 激活块 → Gateway 快照。恢复：payout 是新库，回退 = 停服务 + 保留库（不删数据）；Vault 规则 DELETE 三行 + 策略版本回退行；注册块走 deactivate 脚本。

## 10. 页面设计

原型画进 `client-console-web/client-console.pen` 与 `operations-console-web/docs/operations-console.pen`（需在 Pencil 中打开对应文件），导出到 [prototypes/](./prototypes/)。

| 端 | 画板 | 关键状态与交互 |
|---|---|---|
| Client | C1 收款账户列表 | 卡片：银行名、`•••• 4321`、币种、状态徽标（待核验 / 冷却中 至 HH:MM / 可用 / 已拒绝·原因 / 已停用）、默认星标；空状态"添加收款账户后才能提款"+ 主按钮 |
| Client | C2 添加向导（3 步） | ① 国家、币种、方式（本地 / SWIFT）② 动态字段 + 就地格式校验（IBAN 位数、BIC）③ 确认（脱敏回显 + "核验约 1 个工作日，通过后 24 小时可用"） |
| Client | C3 账户详情抽屉 | 字段（脱敏）、状态时间线（事件）、设为默认 / 停用使用（确认弹窗说明影响） |
| Client | C4 余额页 | 按 OE × 币种卡片：分段条（待结算 / 可用 / 锁定）+ 数字；保证金、待收回单列；"暂不能提款"原因列表，每条带去处理链接；无账户直接引导 C2 |
| Client | C5 移动端（390） | C1 / C4 单列 |
| Ops | O1 核验工作台 · 待核验 | 页签 待核验 N / 待复核 N / 已处理；左列表（Owner、银行、币种、提交时间、共用提示图标）+ 右详情：账户信息（`SensitiveValue` 显示原值）、法定名对照（一致 / 不一致标签）、同账号共用其他 Owner 警示、Maker 意见（通过 / 拒绝 + 原因） |
| Ops | O2 待复核 | Maker 意见只读 + Checker 决定；Checker = Maker 时按钮禁用并说明"需由另一人复核" |
| Ops | O3 Owner 账户与余额 | 按 Owner 搜索 → 账户表 + 余额解释（同 C4 结构）；停用 / 恢复 / 关闭（原因必选）；代提交入口 |
| Ops | O4 拒绝 / 停用弹窗 | 原因下拉 + 说明 + 影响提示 |

## 11. 研发计划

| # | 任务 | 系统 | 依赖 | 验收映射 |
|---|---|---|---|---|
| T1 | payout 工程骨架、配置、测试基座；本地库与账号；arch-rules / manifest / pom | payout、local-environment | — | R1 |
| T2 | PDManer 与 Schema、DDL；DataObject / Mapper / Repository；事件与补偿事件 | payout | T1 | R2 |
| T3 | Vault 客户端接入与 DML；字段清单加载与校验 | payout、Vault | T1 | R3、R5、AC-01、AC-02、AC-07 |
| T4 | Processor：提交、Maker、Checker、默认、停用 / 恢复 / 关闭、Reveal、队列；`AbstractLockProcessor` | payout | T2、T3 | R4、AC-03—AC-07、AC-10 |
| T5 | account positions 接口 + 测试 | account | — | AC-08 |
| T6 | 余额解释 Processor | payout | T4、T5 | R6、AC-08 |
| T7 | Platform 通知 `tenantRecipients` + 测试；模板与白名单 | Platform | — | R7、AC-09 |
| T8 | 通知补偿 Handler | payout | T4、T7 | AC-09 |
| T9 | Client BFF + Web（C1—C5）、i18n `payout` 命名空间 | Client 端 | T4、T6、原型确认 | R8、AC-V1、AC-V2、AC-V4 |
| T10 | Ops BFF + Web（O1—O4）、`pageCapabilities` | Ops 端 | T4、T6、原型确认 | R9、AC-V3、AC-V4 |
| T11 | Platform 注册块与语言包 | Platform | T9、T10 | R10 |
| T12 | mor-test 接入（Compose、IP .44 / Worker 16、探针、initialize / prepare / build） | 环境 | T1 | 阶段 6 |

并行：T5、T7 与 T1—T4 可并行；T9 / T10 在原型确认后与后端并行（按合同 Mock BFF 响应仅用于前端本地开发，不进入 auto 测试）。

## 12. 验证策略

- payout `auto`：从 HTTP 入口跑场景矩阵（AC-01—AC-10），本地 Loopback MySQL / Redis / RabbitMQ；Vault 与 account、Platform 用测试 HTTP 端点返回配置的 DTO（不 Mock 生产 Client）；断言原值不出现在日志、事件、补偿载荷。
- account、Platform：各自 `auto` 覆盖新增接口 / 字段。
- BFF：转发与权限；Web：tsc、单测、i18n 覆盖率、构建；浏览器 en / zh × 1440 / 390（阶段 5 本机、阶段 7 mor-test）。

## 13. 风险与兼容

- Platform 通知 `tenantRecipients` 是新语义：旧调用方不受影响（字段可选）；若 T7 延误，T8 先只写事件、不发邮件（与 S-05 退路一致）。
- account `positions` 是新接口，原单查询不变。
- `mor_payout` 首建，无历史数据；PDManer 中 5 张出款表本期不建库，S2 改造后首建，避免两次迁移。
- 静态字段清单与 S3 渠道能力并存期间以清单为准，S3 切换时删除清单（配置文件头已标注）。
