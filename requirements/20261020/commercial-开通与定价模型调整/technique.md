# commercial 开通与定价模型调整（一期）：技术方案与合同

> 状态：阶段 3 合同（2026-10-20，主会话编写并锁定）。三条线（A commercial、B ops-bff + Platform、C ops-web）按本文件并行开发；合同变更须经主会话。

**规模（本次方案）**：4 个系统（commercial、operations-console-bff、operations-console-web、platform-support 注册）；commercial 新增 13 / 修改 9 个 Owner 接口；新增 2 张表、修改 5 张表（新增 9 字段、废弃 2 字段）；初始化 DML 改目录 + 迁移 DML；Platform 块 124：3 权限、5 资源、约 14 路由；新增 1 页面（标准价目）、改 5 处页面。

## 1. 公共约定

- 遵循 `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`：写命令为模块内 Processor（`AbstractLockProcessor`，锁实际资源）、事务内只操作 `mor_commercial`、所有写带 `expectedChangeVersion`（CAS）并写 BCR（`BusinessChangeHistoryWriter`）；错误为 RFC 9457（400 `INVALID_REQUEST` / `BUSINESS_CHECK_FAILED`、404 `NOT_FOUND`、409 `CONCURRENT_REQUEST`）；HTTP 幂等由 Gateway `Idempotency-Key` 负责，业务不加去重组件。
- ID 为十进制字符串；时间 ISO-8601 带偏移；金额、费率为十进制字符串（费率仍为 0–1 小数，百分比换算只在 Web）。
- 维度代码统一使用 `PricingDimension` 枚举。

## 2. 维度清单（S2，代码登记）

`PricingDimension` 增加元数据（新类 `DimensionDefinition` 或枚举字段）：`factSource`（ORDER / CARD_BIN / THREE_DS / TRANSACTION / PAYOUT / FX）、`evaluationPoint`（CHECKOUT / CARD_ENTRY / AUTHORIZATION / AUTHENTICATION / PAYOUT_CREATION / FX_DEAL）、`acceptanceAllowed`、`pricingAllowed`、`displayName`。

| 维度 | factSource | evaluationPoint | 开通 | 定价 |
|---|---|---|---|---|
| CURRENCY | ORDER | CHECKOUT | ✓ | ✓ |
| CONSUMER_COUNTRY | ORDER | CHECKOUT | ✓ | ✓ |
| CARD_BRAND | CARD_BIN | CARD_ENTRY | ✓ | ✓ |
| FUNDING_TYPE | CARD_BIN | CARD_ENTRY | ✓ | ✓ |
| CARD_LEVEL、CARD_PRODUCT | CARD_BIN | AUTHORIZATION | ✓ | ✓ |
| ISSUER_COUNTRY、ISSUER_REGION | CARD_BIN | CARD_ENTRY | ✓ | ✓ |
| THREE_DS_RESULT | THREE_DS | AUTHENTICATION | ✗ | ✓ |
| TRANSACTION_TYPE | TRANSACTION | AUTHORIZATION | ✗ | ✓ |
| PAYOUT_RAIL、DESTINATION_COUNTRY | PAYOUT | PAYOUT_CREATION | ✓ | ✓ |
| CURRENCY_PAIR | FX | FX_DEAL | ✓ | ✓ |
| DISPUTE_STAGE | TRANSACTION | AUTHORIZATION | ✗ | ✓ |

## 3. 数据库（`mor_commercial`）

> PDManer `commercial.pdma.json`、`commercial-schema.sql`、`commercial-initial-data.sql`、测试 `init/ddl.sql` 同步。增量 DDL `db/commercial/ddl.sql`（只 ADD / MODIFY，不 DROP），迁移 `db/commercial/migration.sql`。无 FK / CHECK。

### 3.1 改表

| 表 | 改动 |
|---|---|
| `t_product_capability` | `capability_kind` 允许值增 `FEATURE`；`dimension_code`、`dimension_value` 标「已废弃，恒为 NULL」（保留列）；状态沿用 DRAFT / ACTIVE / RETIRED（运营「停用」= RETIRED） |
| `t_product_fee_event` | 增 `capability_kind VARCHAR(32) NULL`（该计费事件挂哪类开通项；NULL = 无需开通的基础产品事件）、增 `capability_code VARCHAR(64) NULL`（功能类事件精确挂到功能开通项，如 TOKEN_STORED → TOKEN_STORAGE；支付方式类为 NULL 表示任一支付方式开通项） |
| `t_agreement_capability` | 增 `require_3ds TINYINT(1) NOT NULL DEFAULT 0` |
| `t_agreement_capability_limit` | 语义改为「接受条件」：`limit_code` 存 `PricingDimension` 代码（CURRENCY、CARD_BRAND…），`limit_value` 存一个取值；同维度多行 OR；不再存 REQUIRE_3DS（迁移到 `require_3ds`）。表名不变，注释改写 |
| `t_commercial_term_plan` | 增 `owner_type VARCHAR(32) NOT NULL DEFAULT 'CLIENT'`（CLIENT / LOCAL_PARTNER / PLATFORM_TEMPLATE；存量按 client_id / local_partner_id 回填）、增 `template_name VARCHAR(128) NULL`（模板显示名）；模板方案：`agreement_id`、`client_id`、`local_partner_id` 为 NULL，`entity_no` 非空；新唯一索引 `uk_comm_term_template(owner_type, entity_no, plan_code)` 不需要（`uk_comm_term_code(term_type, plan_code)` 已保证编号唯一） |
| `t_commercial_term_version` | 增 `source_template_version_id BIGINT NULL`（基于模板生成时的模板版本）、增 `signature_exempt TINYINT(1) NOT NULL DEFAULT 0`（存量免签）；状态增 `CLIENT_SIGNED`；`RESOLVABLE` 不变 |
| `t_commercial_term_price` | 增 `source_template_price_id BIGINT NULL` |

### 3.2 新表

**`t_product_capability_dimension`**（开通项可配维度）：`capability_dimension_id BIGINT PK`、`capability_code VARCHAR(64) NOT NULL`、`dimension_code VARCHAR(64) NOT NULL`、`required TINYINT(1) NOT NULL DEFAULT 0`、`create_time`、`update_time`；`uk_capability_dimension(capability_code, dimension_code)`。

**`t_term_version_acceptance`**（价格协议签署）：`version_acceptance_id BIGINT PK`、`term_version_id BIGINT NOT NULL`、`party_id BIGINT NOT NULL`（Client 或 Local Partner 的签约 Party）、`acceptance_method VARCHAR(32) NOT NULL`（E_SIGNATURE / WET_SIGNATURE / CLICK_ACCEPTANCE，同合同签署）、`accepted_at DATETIME(6) NOT NULL`、`acceptance_reference VARCHAR(128) NOT NULL`、`file_id BIGINT NOT NULL`、`content_hash CHAR(64) NOT NULL`、`recorded_by VARCHAR(64) NOT NULL`、`create_time`、`update_time`；`uk_version_acceptance(term_version_id)`（一期一个版本一次签署）、`uk_version_acceptance_ref(acceptance_reference)`。

### 3.3 初始化数据（目录）

- 删除 CARD_VISA、CARD_MC、CARD_AMEX、CARD_UNIONPAY 四行，新增 `CARD`（PAYMENT_METHOD，payment_method_code=CARD，「银行卡」）；新增 `TOKEN_STORAGE`（FEATURE，ACQUIRING，「存卡」）。
- 可配维度：CARD = CURRENCY(必填)、CONSUMER_COUNTRY、CARD_BRAND、FUNDING_TYPE、ISSUER_COUNTRY、ISSUER_REGION、CARD_LEVEL；APPLE_PAY、GOOGLE_PAY = CURRENCY(必填)、CONSUMER_COUNTRY、CARD_BRAND、FUNDING_TYPE；PAYOUT_LOCAL / PAYOUT_SWIFT = CURRENCY(必填)、DESTINATION_COUNTRY；FX_SPOT = CURRENCY_PAIR；DISPUTE_HANDLING、ANALYTICS_SUBSCRIPTION、TOKEN_STORAGE = 无。
- 计费事件挂接：ACQUIRING 的 AUTH / CAPTURE / SALE / REFUND / CHARGEBACK → `capability_kind=PAYMENT_METHOD`；TOKEN_STORED → `FEATURE` + `TOKEN_STORAGE`；PAYOUT → `PAYOUT_RAIL`；FX_DEAL → `FX`；DISPUTE CHARGEBACK → `DISPUTE`；PLATFORM 三事件 → NULL。

### 3.4 迁移（`migration.sql`，mor-test 执行，事务内，先 SELECT 核对）

1. 目录按 3.3 改写（含 fee_event 两列回填）。
2. 每个合同 × OE：把 CARD_VISA / MC / AMEX / UNIONPAY 开通项合并为一条 `CARD`：保留其中最早创建的一行（改 `capability_code=CARD`），其余删除；`CARD_BRAND` 条件 = 原各项卡组织；CURRENCY 条件 = 原各项币种并集；`status` 取任一 ENABLED 则 ENABLED；`effective_from` 取最早、`effective_to` 取最晚（任一 NULL 则 NULL）；保证金取保留行。**并集放宽或保证金 / 区间不一致的合同写入迁移输出清单，发布记录中人工确认。**
3. 原 `REQUIRE_3DS=1` 行 → `require_3ds=1` 并删除该行。
4. `t_commercial_term_plan.owner_type` 回填；所有 `ACTIVE / SCHEDULED / SUPERSEDED / EXPIRED` 版本 `signature_exempt=1`。
5. 价格不动（画像不变，命中结果不变）。

## 4. commercial Owner 接口

### 4.1 产品目录维护（S1，新增；`/products/v1`）

| Method Path | 请求 | 响应 / 规则 |
|---|---|---|
| POST `/products/v1/capabilities` | `productCode`、`capabilityCode`（`[A-Z][A-Z0-9_]{1,63}`）、`capabilityName`、`capabilityKind`（PAYMENT_METHOD / FEATURE / PAYOUT_RAIL / FX / DISPUTE / PERIODIC_SERVICE）、`paymentMethodCode`（PAYMENT_METHOD 必填、其余必须空）、`dimensions[]: {dimensionCode, required}` | 201 `ProductCapabilitySummary`（含 `dimensions`、`changeVersion`）；代码重复 409；维度须 `acceptanceAllowed`；产品须 ACTIVE；FEATURE 无支付方式 |
| PUT `/products/v1/capabilities/{productCapabilityId}` | `expectedChangeVersion`、`capabilityName`、`productCode`、`dimensions[]` | 200；代码与种类不可改 |
| POST `/products/v1/capabilities/{id}/retire` | `expectedChangeVersion` | 200；已开通合同不受影响，新增开通被拒 |
| POST `/products/v1/capabilities/{id}/activate` | `expectedChangeVersion` | 200（RETIRED → ACTIVE） |
| PUT `/products/v1/fee-events/{productFeeEventId}/capability` | `expectedChangeVersion`、`capabilityKind`（可空）、`capabilityCode`（可空） | 200；FEATURE 时 capabilityCode 必须是该产品下的 FEATURE 开通项 |
| GET `/products/v1/dimensions` | — | `dimensions[]: {dimensionCode, displayName, factSource, evaluationPoint, acceptanceAllowed, pricingAllowed}` |
| GET `/products/v1/capabilities`（改） | 原参数 | `ProductCapabilitySummary` 增 `dimensions[]`、`changeVersion`；`dimensionCode / dimensionValue` 保留但恒为 null |
| GET `/products/v1/fee-events`（改） | 原参数 | 增 `capabilityKind`、`capabilityCode`、`changeVersion` |

目录写命令锁 `CommercialLocks.catalog()`（单一目录锁）；BCR 聚合根 `PRODUCT_CATALOG`。目录行需要 `change_version`：`t_product_capability`、`t_product_fee_event` 若无版本列，则**新增 `change_version BIGINT NOT NULL DEFAULT 1`**（计入 3.1）。

### 4.2 合同开通项（S3，修改 `/agreements/v1`）

- POST `/{agreementId}/capabilities`、PUT `/{agreementId}/capabilities/{id}`：请求体 `limits[]` 替换为 `conditions[]: {dimensionCode, values[]}` + `require3ds: boolean`；其余字段不变。校验：维度在该开通项可配维度内；必填维度（CURRENCY）有值；值格式按维度（币种 3 位、国家 2 位、其他 `[A-Z0-9_]{1,64}`）；开通项目录状态 ACTIVE（新增时）；所属产品已订阅。
- 读：`AgreementCapability` 响应 `limits` 改为 `conditions[]` + `require3ds`（BFF 与 Web 同步改）。
- 兼容：旧 `limits` 字段删除（只有 ops-bff 一个调用方，同次发布）。

### 4.3 标准价目模板（S4，新增）

| Method Path | 说明 |
|---|---|
| POST `/commercial-terms/v1/templates` | `planCode`、`templateName`、`termType`（CLIENT_PRICING / PAYOUT_FEE / PLATFORM_FEE 等 Client 类）、`entityNo` → 模板方案（owner_type=PLATFORM_TEMPLATE） |
| GET `/commercial-terms/v1/templates` | 参数 `entityNo`、`termType`、`status`；返回模板方案 + 当前生效版本号 + 最新版本号 |
| 版本 / 价格 / 提交 / 复核 / 排期 / 激活 | 复用 `/commercial-terms/v1/plans/{planId}/versions`、`/versions/{id}/prices|submit|approve|reject|schedule|activate`；模板版本：无合同校验、无开通相关性 / 完整性校验（只做结构校验）、无签署要求；激活即可，同一模板方案生效版本唯一 |

权限隔离在 BFF / Gateway：模板的写与复核走独立路由与权限（第 6 节），Owner 接口按 `owner_type` 区分校验逻辑。价格命中（`/commercial-prices/v1/resolutions`、Checkout 解析）**排除** PLATFORM_TEMPLATE 方案。

### 4.4 合同价格生成（S5）

- POST `/commercial-terms/v1/plans/{planId}/versions`（改）：请求增 `sourceTemplateVersionId`（与 `copyFromVersionId` 互斥）。基于模板时：取模板版本价格中 `entityNo` = 合同 OE、画像支付方式 ∈ 合同已开通 PAYMENT_METHOD 开通项、币种 ∈ 该开通项 CURRENCY 条件、画像条件与开通项接受条件相容（画像某维度的取值集合与开通项该维度取值集合有交集；开通项未写的维度视为全集）；功能类计费事件组件只在对应功能已开通时保留；结算四字段 Client 条款用模板值。每行写 `source_template_price_id`，版本写 `source_template_version_id`。`copyFromVersionId` 复制时保留来源字段。
- POST `/commercial-terms/v1/versions/{versionId}/fill-from-template`（新）：`expectedChangeVersion`、`templateVersionId`；只追加当前版本缺失的（OE × 币种 × 画像）价格行，规则同上；DRAFT 才可用。
- PUT `/versions/{versionId}/prices`（改）：请求每条价格可带 `sourceTemplatePriceId`（Web 回传，保持来源）；响应增 `relevanceWarnings[]: {entityNo, currency, pricingProfileCode, reasonCode, message}`（reasonCode：PAYMENT_METHOD_NOT_ENABLED / CURRENCY_NOT_ENABLED / CONDITION_OUT_OF_SCOPE / FEATURE_NOT_ENABLED / NO_CAPABILITY_ON_ENTITY）。

### 4.5 版本读、破标与「标准价已更新」（S7、AC-10）

- GET `/commercial-terms/v1/versions/{versionId}`、GET `/commercial-term-versions/v1/{versionId}/prices`（改）：价格增 `sourceTemplatePriceId`；组件增 `benchmark: {status: BELOW_STANDARD | AT_OR_ABOVE | NO_BENCHMARK, standardRate, standardFixedAmount, standardMinAmount, standardMaxAmount}`（阶梯：任一档费率或固定额低于模板同档 → BELOW_STANDARD；档位结构不同 → NO_BENCHMARK）；版本增 `belowStandardCount`、`sourceTemplateVersionId`、`templateUpdate: {latestTemplateVersionId, latestVersionNo} | null`（来源模板方案已有更新版本生效时）、`signatureExempt`、`acceptance`（签署记录或 null）、`relevanceWarnings[]`（按当前开通状态计算）。
- 比较规则：费率 / 固定额 / 最低收费 / 最高收费，合同值 < 模板值即破标；模板有值合同为空：最低收费为空算破标，最高收费为空不算；模板组件不存在 → NO_BENCHMARK。
- GET `/commercial-terms/v1/versions`（改，列表）：行增 `belowStandardCount`、`signatureExempt`、`hasAcceptance`；工作台待复核队列同字段。

### 4.6 客户签署（S8）

- POST `/commercial-terms/v1/versions/{versionId}/acceptance`（新）：`expectedChangeVersion`、`partyId`、`acceptanceMethod`、`acceptedAt`、`acceptanceReference`、`fileId`、`contentHash`。前置：版本 APPROVED、非模板、`partyId` 是合同中 CLIENT（Client 方案）或 LOCAL_PARTNER（Partner 方案）角色 Party。结果：写签署记录，版本 → CLIENT_SIGNED。同编号同内容重放返回原结果。
- POST `/versions/{id}/schedule`（改）：合同方案非免签版本须为 CLIENT_SIGNED（APPROVED 被拒，detail「价格协议尚未签署」）；模板版本 APPROVED 即可排期。
- 状态机：DRAFT → PENDING_APPROVAL → APPROVED →（合同方案）CLIENT_SIGNED → SCHEDULED → ACTIVE；reject 回 DRAFT 不变。

### 4.7 校验（S6，激活与排期时）

`TermVersionActivationValidator` 对 Client 方案：
- **零开通**：价格所在 OE 在合同中无任何与该方案计费事件相关的 ENABLED 开通项 → 拒绝（替换现「不限制」）。
- **相关性**：每条价格须对应一个 ENABLED 开通项（画像支付方式相等；币种 ∈ CURRENCY 条件；画像各维度取值与开通项条件相容）；功能类事件组件须有对应功能开通项 → 否则拒绝，detail 含 `OE/币种/画像` 与原因。
- **完整性**：每个 ENABLED PAYMENT_METHOD 开通项 × CURRENCY 每个值 × 该开通项各有条件维度的每个取值组合，至少命中一条价格（`CommercialPriceCandidate.matches`，已知事实 = 支付方式 + 币种 + 该组合；未写维度为未知 → 需要无该维度条件的价格兜底）；组合数上限 1000，超过拒绝并提示收窄条件。
- Partner 方案只做结构与零开通外的原有校验。

### 4.8 Checkout 解析与价格命中（S9）

- `ResolveCheckoutContextResponseBody.Capability` 增 `paymentMethodCode`、`capabilityKind`、`conditions[]: {dimensionCode, values[]}`、`require3ds`；`dimensionCode / dimensionValue` 保留恒为 null（acquiring 当前未消费，见台账）。
- 只输出「有生效价格」的开通项：存在 RESOLVABLE 且当前生效的 Client 版本中有命中该开通项的价格（相关性规则）。
- 价格命中 / 费率卡 / 解析均排除 PLATFORM_TEMPLATE 方案。费率卡按开通项分组的 `compatibleWith` 改为按条件判断。

## 5. ops-bff（B 线）

- `/commercial-admin/v1/**` 增加对 4.1、4.3、4.4、4.6 的转发；修改 4.2、4.5 的模型（`conditions`、`require3ds`、`benchmark`、`relevanceWarnings`、`acceptance`…）。
- 新路由（BFF 路径）：`POST /products/capabilities`、`PUT /products/capabilities/{id}`、`POST /products/capabilities/{id}/retire|activate`、`PUT /products/fee-events/{id}/capability`、`GET /products/dimensions`、`GET /commercial-terms/templates`、`POST /commercial-terms/templates`、`POST /commercial-terms/versions/{id}/fill-from-template`、`POST /commercial-terms/versions/{id}/acceptance`；模板的版本写 / 复核复用现有版本路由但需按模板权限授权——**做法**：BFF 新增模板专用路由前缀 `/commercial-terms/templates/{planId}/versions`、`/commercial-terms/template-versions/{id}/prices|submit|approve|reject|schedule|activate`，内部转发到同一 Owner 接口，并在转发前读取版本校验 `ownerType=PLATFORM_TEMPLATE`（合同路由遇模板版本返回 403 / 400，反之亦然）。
- operatorAccountId 取登录身份（同现有）。
- 测试：沿用 `OperationsBffHttpTestBase` + commercial HTTP 模拟（DTO 端点），覆盖新路由转发、权限前缀隔离、错误 detail 透传。

## 6. Platform 注册（块 124，B 线）

- 权限：`900000000000124101 COMMERCIAL_CATALOG_MANAGE`（产品目录维护）、`…124102 COMMERCIAL_TEMPLATE_MANAGE`（标准价目维护）、`…124103 COMMERCIAL_TEMPLATE_APPROVE`（标准价目复核）。签署登记用现有 `COMMERCIAL_PRICING_MANAGE`；读模板用 `COMMERCIAL_PRICING_READ`。
- Console Resource：菜单 `OPERATIONS_COMMERCIAL_TEMPLATES`「标准价目」（父 `OPERATIONS_COMMERCIAL`，sort 26，路由 `/commercial/templates`）；按钮 `OPERATIONS_COMMERCIAL_CATALOG_MANAGE`、`OPERATIONS_COMMERCIAL_TEMPLATE_MANAGE`、`OPERATIONS_COMMERCIAL_TEMPLATE_APPROVE`、`OPERATIONS_COMMERCIAL_TERM_SIGN`。
- 路由：第 5 节全部新路由（OPERATIONS 通道，写为 `idempotency_mode` 同现有商务写路由，GET 为 NONE），绑定上述权限；Header / Query 参数白名单按块 95 写法。
- 角色：PLATFORM_ADMIN 授予三个新权限（`test-activate.sql` 启用，`test-deactivate.sql` 回退）；注册行初始 DISABLED / REVOKED，与块 95 一致。
- `console-resource-capabilities.json` 增新菜单与按钮键；`platform-support-initial-data.sql` 末尾追加块 124（与 `db/platform-support-service/registration-dml.sql` 逐字一致）。
- ops-web `pageCapabilities.ts` 增 `OPERATIONS_COMMERCIAL_TEMPLATES`（C 线）；注意主工作区该文件有对账会话未提交改动，C 线在 cp-web 基线上改，集成时叠加。

## 7. ops-web（C 线）

| 页面 | 要点 |
|---|---|
| 产品目录 | 在 opsux-20261020o 版本上：产品区块头「+ 新增开通项」（按钮 `OPERATIONS_COMMERCIAL_CATALOG_MANAGE`）；开通项行操作：编辑（名称、可配维度及必填）、停用 / 启用；可开通项表加「可配维度」列；计费事件行显示挂接开通项并可编辑；种类标签含「功能」。新增弹窗：种类、代码（支付方式代码输入带建议 CARD、APPLE_PAY、GOOGLE_PAY、STC_PAY、MADA、BNPL_TABBY）、名称、归属产品、可配维度多选（来自 `/products/dimensions` 的 `acceptanceAllowed`）与必填勾选 |
| 标准价目（新，`/commercial/templates`） | 按 OE 筛选的模板方案列表（名称、编号、条款类型、生效版本、最新版本状态）；新建模板；进入模板版本工作区复用价格矩阵与编辑价格抽屉（组件表格），无合同信息、无签署；复核按钮按 `TEMPLATE_APPROVE` |
| 合同 · 开通项 | 新增 / 编辑开通项弹窗：可开通项卡片单选（只列已订阅产品、ACTIVE、未开通）；按该开通项可配维度渲染条件：CURRENCY / 国家为标签输入，CARD_BRAND / FUNDING_TYPE 等为多选（常见值建议），必填标记；3DS 开关；保证金按百分比输入（5 → 0.05）；列表「约束」列改为条件标签 + 3DS |
| 价目 · 新建版本 | 「复制上一版本 / 基于标准价目（选模板 + 版本）/ 空白」三选一 |
| 版本编辑器 / 价格抽屉 | 价格按开通项分组（挂不上的单列「未对应开通项」并标红）；每行来源标签（模板名 vN）与破标标记（组件格标红「破标」+ 悬停显示模板值）；保存后展示 `relevanceWarnings`；DRAFT 显示「从标准价目补齐」；版本头显示「标准价已更新（vN）」提示 |
| 版本流程 | APPROVED 后显示「登记客户签署」（按钮 `OPERATIONS_COMMERCIAL_TERM_SIGN`），弹窗：签署方（从合同 CLIENT / LOCAL_PARTNER 参与方带出）、方式、时刻、编号、文件 ID、SHA-256；状态标签含「客户已签署」「历史免签」；排期按钮在 CLIENT_SIGNED 或免签时可用 |
| 工作台 / 版本列表 | 待复核项显示破标数；列表增「破标」「签署」列 |

- 不得回退 opsux 已上线改动（cp-web 基线已包含）；新类型与工具函数优先放新文件（`capabilityConditions.ts`、`templates.ts` 等）；必须改 `commercial.ts` / `commercial.css` 时只追加。
- 单测：条件 ↔ 请求体转换、保证金百分比、破标展示、生成方式选择、签署表单校验、分组（挂不上开通项）。

## 8. 任务拆分

| 线 | worktree | 拥有 | 完成定义 |
|---|---|---|---|
| A commercial | `.claude/worktrees/cp-commercial`（HEAD 4ab205e0） | `paytech/commercial/**`、本需求 `db/commercial/*` | 第 2–4 节全部实现；PDManer / schema / 初始化 DML / 测试 DDL 一致；`db/commercial/ddl.sql`、`migration.sql`（含核对 SELECT 与输出清单）；commercial auto 测试全绿（只跑 commercial 模块：`-pl commercial/paytech-commercial-test -am`，本地 `mor_commercial` 测试库）；`mvn -o -DskipTests package` 通过 |
| B ops-bff + Platform | `.claude/worktrees/cp-bff`（HEAD 4ab205e0） | `paytech/operations-console-bff/**` 商务部分、`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（只追加块 124）、`console-resource-capabilities.json`（只加键）、本需求 `db/platform-support-service/*` | 第 5–6 节；依赖 A 的 `paytech-commercial-api`：B 线在自己 worktree 先按本合同修改 api 模块的 DTO（**与 A 线同一份定义**，以 A 线为准，集成时取 A 线版本）；BFF auto 测试与 platform-support 相关测试通过 |
| C ops-web | `.claude/worktrees/cp-web`（HEAD 4ab205e0，已含 opsux 提交） | `operations-console-web/src/commercial/**`、`src/pages/Commercial*.tsx`、新页面文件、`src/navigation/pageCapabilities.ts`（只加一项）、`src/navigation/ConsolePage.tsx`（只加一项） | 第 7 节；`npm test`、`npm run build` 通过；不碰非商务文件 |

依赖与集成：A 线先定 API DTO 并在 30 分钟内提交到其 worktree（`git commit` 在 worktree 内，供 B 线复制）；主会话集成顺序 A → B → C，本地起服务联调后进入阶段 5。

## 9. 测试数据计划（AC-13，阶段 7 由脚本经运营入口执行）

1. 标准价目：AE001「标准价」（CLIENT_PRICING：CARD 兜底、CARD Visa 借记、CARD 境外卡、CARD Amex、APPLE_PAY、GOOGLE_PAY × AED / USD；TOKEN_STORED 存卡费）与「优惠价」各一套，复核生效；平台费模板 PLATFORM_FEE 一套。
2. 演示合同（新建，编号 `DEMO2-*`，真实 ACTIVE Client）：
   - `DEMO2-CL-101` 只收 Visa 借记卡（CARD + VISA + DEBIT + AED），基于标准价生成，Visa 借记 AED 降价 → 破标，签署后生效；
   - `DEMO2-CL-102` Apple Pay 限 Visa / MC（APPLE_PAY + VISA,MASTERCARD + AED,USD）+ CARD 全卡组织，基于优惠价生成，复核通过、**待客户签署**；
   - `DEMO2-CL-103` 已生效后新开 GOOGLE_PAY → 「已开通 · 待定价」；
   - `DEMO2-PT-101` Partner 分润方案，签署后生效；
   - 存卡功能开通 + TOKEN_STORED 价格示例（并入 101）。
3. 模板出新版本（标准价 v2，Visa 借记 AED 下调）→ DEMO2-CL-101 显示「标准价已更新」。
4. 迁移后的 DEMO-CL-001 / 002 与 CL-F1105E 保留，作为迁移结果示例。

## 10. 风险与开放点

- 完整性按条件组合展开的组合数可能较大（多卡组织 × 多币种 × 多借贷记），设上限 1000。
- acquiring 当前未消费 Checkout 开通项字段；本期只扩展输出，执行在二期。
- 模板方案复用 term 表，查询 / 列表须全部显式按 `owner_type` 过滤，A 线须排查所有 `t_commercial_term_plan` 查询。
- 主工作区 ops-bff / platform-support / ops-web 有其他会话未提交改动：集成与发布在干净 worktree 进行，合入主工作区只叠加本需求文件 / 片段。

## 11. 合同补充（主会话裁决，2026-10-20）

- C1（B 线请求）：commercial 版本响应、方案响应、方案列表行、版本列表行均增 `ownerType`（CLIENT / LOCAL_PARTNER / PLATFORM_TEMPLATE），模板另带 `templateName`；新增 GET `/commercial-terms/v1/plans/{planId}`（返回方案详情，含 ownerType）。BFF 转发前据此校验模板 / 合同路由隔离。
- C2：模板版本读取复用现有 GET `/commercial-terms/versions/{id}`（PRICING_READ）；工作区聚合对模板版本（ownerType=PLATFORM_TEMPLATE）按模板 `entityNo` 取价，不新增模板读路由。
- C3：GET `/products/dimensions` 绑定 COMMERCIAL_CONTRACT_READ（同目录读）。
- C9（集成对齐）：BFF 不提供 GET `/commercial-terms/plans/{planId}`；模板版本编辑页聚合返回 `template` 摘要（planId、planCode、templateName、entityNo 等），C 线据此显示；模板列表 BFF 参数为 entityNo / termType / status（非分页），C5 的分页包装作废。Owner 侧 GET `/commercial-terms/v1/plans/{planId}` 由 A 线视情况提供，不作为 BFF 路由。
- C10（更正 C9）：B 线终版为 cp-bff `acfd9922` 起：BFF 提供 GET `/commercial-terms/plans/{planId}`（PRICING_READ，块 124 共 18 路由）、模板列表分页 `{items,page,size,total}`（page / size 可选，默认 1 / 100）；编辑页 / 对比聚合仍返回 `template` 摘要。C 线 f48fe526 的用法（取 template 摘要、不传分页参数）与之兼容。
- C11（最终，取代 C10）：B 线终版为 cp-bff `56ba38fe`（内容同 `7f6d8657`）：17 路由，无 BFF GET `/commercial-terms/plans/{planId}`，模板列表非分页（参数 entityNo / termType / status）；编辑页 / 对比聚合返回 `template` 摘要。C 线终版 `f48fe526` 与之一致。A 线不提供 getPlan。
- C12（集成基线锁定）：集成取 B 线 cp-bff `56ba38fe`（= `7f6d8657` 内容）、C 线 cp-web 截至 `f48fe526` 的提交（其后 `e34154ef` 按已作废的分页形态编写，不纳入）。
