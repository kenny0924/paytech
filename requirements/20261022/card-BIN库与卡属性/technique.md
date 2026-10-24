# card：BIN 库与卡属性 · 技术方案

> 状态：**阶段 2 已确认**（2026-10-22 用户“接收”），阶段 3 设计中。第 1 节为阶段 1 现状证据，第 2 节为阶段 2 差异与影响（只读核对）。

## 1. 现状证据（阶段 1 调研）

- Card 存储：`paytech/card/docs/database/sql/card-schema.sql:13-15`（指纹、`card_bin VARCHAR(8)`、`pan_last4`）；`CardCipher.java:126-127` 取 BIN；`PrepareCardInstrumentProcessor.java:142-144,227` Luhn 校验。没有卡组织识别逻辑。
- Card 输出：`CreateCardInstrumentResponseBody.java:11-30`（`riskAttributes{bin, fingerprint}`）；`StoredCardListResponseBody.java:20-35`（无卡组织）；`RevealChannelCardMaterialResponseBody.java:11-26`。
- Payment：`payment-schema.sql:119` `t_payment_instrument_token.card_bin`（只作风控输入）；`PayRiskCheckActionActor.java:118-125` 传 Risk；`PaymentChannelClientImpl.java:81-88` 不传 `schemeCode`；`PaymentSucceededPayload.java:31-58` 无卡属性。
- Risk：`PaymentAttemptRiskInput.java:28-33` 只有 `cardBin`；`requirements/20261014/risk-支付反欺诈一期/design.md:235` 发卡国规则推迟。
- commercial：`PricingDimension.java:19-34` 六个卡维度事实来源 `CARD_BIN`；`CommercialCodes.java:142-154` 只有 CARD_BRAND 有规范值；`CapabilityConditions.java:64-66` 缺事实失败关闭；运行时唯一调用方是运营预览（`CommercialPricePreviewClientImpl.java:61-65`）。
- billing：`billing-schema.sql:65-67` 三列无写入方。
- channel-connector：MID 能力 `scheme_code`（`channel-connector-schema.sql:62`）；发渠道卡字段无卡组织（`RegisteredFunctionCatalog.java:40-45`）；渠道成本无卡维度。
- 既有决定：无 BIN 数据源或归属决定；冲突见需求第 1 节。

## 2. 差异与影响（阶段 2）

### 2.1 需求项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 BIN 库存储 | 无；Card 模型 `card.pdma.json` 由 `scripts/pdmaner/generate-card-model.mjs` + additions 生成；无 initial-data.sql；表归属登记 `paytech/tools/arch-rules/.../table-ownership.yaml:59-61` | AC-03 | 新表：版本、号段、导入错误行 | 新增 3 张表（阶段 3 定字段）；新建种子 DML（合成号段 + 公开测试卡号段） |
| R2 版本导入 | 仓库无 multipart；Gateway Body 上限硬编码 64 KiB（`GatewayRoutePolicyValidator.java:14,160`），路由响应 10 秒；Platform 内部文件接口（`InternalFileApi.java:26-70`，浏览器直传对象存储）只认 PDF / PNG / JPEG（`StoredContentType.java:7-49`、`CompletedUploadVerifier.java:20`），CSV 在完成上传时被拒；扫描策略与写入路由按来源系统划分；Card 没有 Platform 客户端，也没有服务端下载文件的先例（MinIO + 私有 test-ca） | AC-01、AC-05 | 上传通道；异步校验与写入 | 见 E2：经 Platform 文件服务直传，Platform 增加 CSV 识别与扫描策略，Card 异步下载、校验、写入，页面轮询 |
| R3 卡属性查询 | 无 | AC-03、AC-04 | 查询服务与多实例版本缓存 | 当前版本号缓存 + 激活时失效；8 位再 6 位；6 位 BIN 的卡只能命中 6 位号段 |
| R4 Card 输出 | `CreateCardInstrumentProcessor.result()` :86-94 只返回 BIN 与指纹；`StoredCardListResponseBody.java:18-36` 无卡组织；调用方 Payment、acquiring、counterparty 都用严格 JSON（`HttpServiceClientFactory.java:119-130` 拒绝未知字段） | AC-07 | 响应字段；下游 DTO | 加字段；**部署顺序：消费方先于 Card**。已存卡列表按当前版本实时查（仅展示，回切后可能与交易快照不同） |
| R5 Payment 冻结 | `t_payment_instrument_token`（`payment-schema.sql:108-128`）只有 `card_bin`、指纹；`ApplyCardInstrumentProcessor.bindCardInstrument()` :162-171 写入；重放沿用已存 Token（快照天然冻结） | AC-02、AC-08 | 新列与映射 | Token 加可空列：卡组织、借贷记、卡等级、卡产品、发卡国、发卡区域、BIN 库版本 |
| R5 Risk 输入 | `PaymentAttemptRiskInput` 只有 `cardBin`；Risk 拒绝未知输入字段（`EvaluationInputContract.java:115`）；变量 `t_risk_variable` 下一个 ID 900000000000093014 | AC-08 | 合同、校验、冻结输入、变量 DML | 见 E5；**Risk 先于 Payment 部署** |
| R5 路由 `schemeCode` | `PaymentChannelClientImpl.criteria()` :81-89 不传；channel-connector 匹配已支持（精确行优先，同精确度冲突报错 `MidCapabilityContext.java:83-95`） | AC-09 | 路由与执行两处都要传 | 两处同时传；未识别时不传。上线前清点 mor-test `scheme_code` 配置 |
| R5 `PaymentSucceeded` → billing | 事件 v2（`events.yaml:5-16`）消费方只有 acquiring、card；acquiring 上限 `MAX_SCHEMA_VERSION = 2`；**billing 没有任何 PAYMENT 事实写入方**（需求 B 未开始，`agent-docs/decisions/2026-10-19-billing-fact-engine.md:59`） | AC-10 | billing 写入在本需求内做不到 | 见 E1、E4 |
| R6 运营控制台 | ops-bff 没有 Card 客户端；Card 无运营入口；注册块已用到 131 | AC-12 | BFF 客户端、Card 运营 API、页面、注册块 | Card 新增只允许 `operations-console-bff` 的 `/card/v1/bin-library/**`；ops-bff 走 Nacos 发现；注册块拟 132（阶段 3 复核 mor_platform 号段） |
| 区域推导（Q6） | 仓库无国家 → 区域映射；运营端 `ISSUER_REGION` 建议值是 `DOMESTIC / GCC / INTERNATIONAL`（`capabilityConditions.ts:27,69`），混了相对境内外与地理口径 | — | 映射归属与代码清单 | 见 E3 |
| 取值规范（B3） | 只有 `CommercialCodes.CardBrand` 4 个规范值；运营端建议另有 MADA、DISCOVER、JCB；`FUNDING_TYPE` 建议无 CHARGE；MID `scheme_code` 是自由文本 | — | 三方口径不一 | 见 E6 |

### 2.2 逐系统影响

| 系统 | 职责 | 影响点 | 代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| card | BIN 库 Owner | 3 张新表；运营 API / Controller / Processor（导入、状态、激活、回切、版本列表、BIN 查询）；异步导入任务；查询服务与缓存；交易工具响应与已存卡列表加字段；Platform 文件客户端 | 代码；PDManer（生成器 additions）、Schema、增量 DDL、测试 DDL、新种子 DML、`table-ownership.yaml`；配置：Platform 发现、私有 CA 信任、XXL 任务 | **最后部署**；导入几十万行须异步；导入错误不回显超过 8 位的数字 |
| payment | 交易快照 | Token 加列；Card 客户端映射；Risk 输入；路由与执行传 `schemeCode`；`PaymentSucceeded` 加字段 | 代码；PDManer（生成器）、Schema、增量 DDL、物理决策文档 | 晚于 Risk、早于 Card；带 scheme 后 MID 能力选择可能变化 |
| risk-decision | 风控输入 | `PaymentAttemptRiskInput`、`PaymentRiskInput`、`PaymentAttemptEvaluator`、`DecisionSourceContracts`；3 个变量 | 代码 + DML | 最先部署 |
| platform-support | 文件服务、路由注册、区域主数据 | CSV 识别（`StoredContentType`、`CompletedUploadVerifier`、`FormatFileScanner`）；CSV 扫描策略与 ops-bff 写入路由 DML；国家 → 区域表、查询接口与种子（E3）；注册块（菜单、权限、BFF 路由） | 代码 + DDL + DML | 文件第二段（Platform internal-auth DUAL）由另一会话开发中，须先上线 |
| operations-console-bff | 页面聚合 | `CardClientConfiguration`；BIN 库路由；Platform 上传开始 / 完成 / 查询转发；操作人头 | 代码 + 配置 | 64 KiB / 10 秒约束 → 导入异步 |
| operations-console-web | 页面 | BIN 库页面（版本列表、上传进度、校验失败行、激活 / 回切确认、BIN 查询）；导航登记；`capabilityConditions.ts` 建议值改为规范清单 | 代码 | 阶段 3 画原型 |
| acquiring | 已存卡展示 | Card DTO 新字段；结账已存卡输出加卡组织 | 代码 | 先于 Card 部署 |
| counterparty | 已存卡对外 API | 客户端 DTO；`ClientCustomerApi` 已存卡列表加 `cardBrand`（对外 Client API 合同新增可选字段） | 代码 + API 文档 | 同上 |
| commercial | 维度定义 | 代码不改（只校验格式）；`CommercialCodes.CardBrand` 补规范值；设计文档 `design-discuss/commercial-capability-pricing/design.md:136` 修订 | 小改 + 文档 | mor-test 已有合同若用了 DOMESTIC 等区域值须排查 |
| foundation / messaging | 事件目录 | `PaymentSucceeded` 条目说明新增可空字段 | 配置 / 文档 | — |
| channel-connector | MID 匹配 | 不改代码 | 可能整理 `scheme_code` 数据 | — |
| billing | 支付计费事实 | 本需求不改（E1） | — | 依赖需求 B |

部署顺序：Risk → Payment、acquiring、counterparty → Platform（CSV 支持、区域主数据）→ Card → ops-bff → ops-web；回滚反向。

### 2.3 阶段 2 需确认的决定

| 编号 | 问题 | 建议 |
|---|---|---|
| E1 | billing 没有 PAYMENT 事实写入方，AC-10 的“billing 写入三列”本需求做不到 | 本需求只让 `PaymentSucceeded` 带上卡组织、借贷记、卡等级；billing 写入由计费“需求 B（PaymentSucceeded → PAYMENT 事实）”完成。AC-10 改为“事件带字段”，billing 写入列为下游依赖 |
| E2 | CSV 怎样上传（Gateway 只放 64 KiB） | 经 Platform 文件服务浏览器直传：Platform 增加 CSV 识别与 CSV 扫描策略（上限 50 MB），ops-bff 发起 / 完成上传，Card 按文件 ID 下载后异步校验写入。依赖文件第二段先上线；来不及时退回分段 JSON（对账账单先例） |
| E3 | 国家 → 区域映射放在哪里、区域代码是什么 | 放 Platform 主数据（仿币种：表 + 只读查询接口 + 种子 DML，本期不做维护页面），Card 生成快照时推导并冻结。区域代码：`GCC`、`MEA`（不含 GCC）、`EUROPE`、`NORTH_AMERICA`、`LATAM`、`APAC`。运营端 `ISSUER_REGION` 建议值改为这 6 个，去掉 DOMESTIC / INTERNATIONAL（境内外留给 commercial 执行链） |
| E4 | `PaymentSucceeded` 升版还是追加 | 保持 v2，只追加可空字段（BillingFactRecorded 先例），acquiring 不用改上限；3DS 需求如需追加也同此规则 |
| E5 | Risk 合同升 v2 还是 v1 原地扩展 | v1 原地扩展可选字段：旧 Payment 不传也合法，不需要新点版本、新哈希或运营迁移；在方案中登记 |
| E6 | 卡组织规范值与共同品牌卡 | 规范清单：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS（补进 `CommercialCodes`），MID `scheme_code` 用同一套；每个号段只记一个主品牌（以数据文件为准），共同品牌（如 mada + Visa）本期不建模。`FUNDING_TYPE` 建议值补 CHARGE |

### 2.4 与 3DS 需求的协调

- 两个需求都改 Payment 的 `PayActionActor`、`PaymentChannelClientImpl` 与 `PaymentSucceeded`，研发阶段按同一工作目录顺序合入。
- Platform 注册块：本需求拟用 132（运营页面），3DS 拟用 133（`CHANNEL_CALLBACK` 路由），阶段 3 复核 mor_platform 实际号段后锁定。

## 3. 方案、接口与数据设计（阶段 3 评审稿）

> **规模：** 涉及 10 个系统（card、payment、risk-decision、platform-support、operations-console-bff、operations-console-web、acquiring、counterparty、commercial、messaging 目录；channel-connector、billing 不改）；新增 17 个接口（Owner 内部 8 个：Card 7、Platform 1；Gateway → ops-bff 路由 9 个），调整 5 个接口（Card 2、Risk 1、counterparty 1、acquiring 1）及 `PaymentSucceeded` v2 追加 3 个可空字段；新增 5 张表 49 个字段（mor_card 4 张 44 字段、mor_platform 1 张 5 字段）、8 个索引；修改 2 张表新增 11 个可空字段（Payment 8、Risk 3），不改既有索引；DML 384 行（Card 种子 19、Platform 国家区域 249 + 扫描策略 2 + 注册块 132 共 111、Risk 变量 3）；新增 1 个运营页面（含上传与激活确认 2 个弹窗）。以上为本次方案，均未实现、未执行。

### 3.1 阶段 2 之后的设计调整

| 项 | 阶段 2 写法 | 本方案 | 原因 |
|---|---|---|---|
| 发卡区域推导时点 | Card 生成快照时推导 | **导入时**按 Platform 映射推导，写入号段行 | 交易链路不再依赖 Platform；号段行已冻结区域，快照照样冻结；映射几乎不变，变更后对新导入生效 |
| Card 查询缓存 | 当前版本号缓存 + 激活时失效 | **不缓存**：一条 SQL 关联 ACTIVE 版本按前缀查 | 必要性门禁：建交易工具频率低，唯一索引点查足够；免去多实例失效问题 |
| Card 新表数 | 3 张 | **4 张**（增加激活记录） | B6 要求激活、回切都留操作人与时间；版本行只能存最后一次 |
| Risk 数据库 | 只改代码 + DML | 另加 3 列 DDL | 评估快照 `t_transaction_risk_assessment` 按列冻结输入，重放映射从列读取 |

### 3.2 CSV 标准格式

| 项 | 规则 |
|---|---|
| 编码 | UTF-8，允许开头 BOM（读取时去除）；不得含 NUL；换行 LF 或 CRLF |
| 分隔与引号 | 逗号分隔；RFC 4180 双引号转义，只有 `issuer_name` 可能需要引号 |
| 表头 | 第 1 行必须逐字为 `bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name`；不符直接判文件级失败 |
| 上限 | 文件 50 MiB（扫描策略 `CARD_BIN_CSV`）；数据行 1,000,000 行；超过判文件级失败 |
| `bin_prefix` | 必填，6～8 位数字（`[0-9]{6,8}`）；超过 8 位的值不回显 |
| `card_brand` | 必填，VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS（E6）；大小写敏感 |
| `funding_type` | 可空，CREDIT、DEBIT、PREPAID、CHARGE |
| `card_level` | 可空，`[A-Z0-9_]{1,32}` |
| `card_product` | 可空，CONSUMER、COMMERCIAL |
| `issuer_country` | 必填，`[A-Z]{2}`，且必须在 Platform 国家区域映射中（否则 `COUNTRY_REGION_MISSING`） |
| `issuer_name` | 可空，≤128 字符，去除首尾空白 |
| 前缀重叠 | 同一前缀出现两次判 `DUPLICATE_PREFIX`（两行都记错）；**不同长度的嵌套前缀允许**（如 `411111` 与 `41111111`），查询取最长 |
| 空行 | 文件末尾空行忽略；中间空行判 `COLUMN_COUNT_MISMATCH` |

错误明细每版本最多记录前 200 条，`error_rows` 记全部错误行数。

### 3.3 版本状态、导入任务与激活

**状态：** `VALIDATING → VALID / INVALID`；`VALID → ACTIVE`；激活其他版本时原 `ACTIVE → RETIRED`；`RETIRED → ACTIVE`（回切）。`INVALID` 为终态，不能激活。种子版本 1 直接为 `ACTIVE`。

**创建版本（Card `CreateBinLibraryVersionProcessor`，`AbstractLockProcessor`，锁 `card-bin-library` TRY_LOCK）：**
1. 业务防重：按 `file_address` 查已有版本，存在则直接返回（200）。
2. 事务外调 Platform `getFile`：`sourceSystem=operations-console-bff`、`reasonCode=CARD_BIN_LIBRARY_IMPORT`、`mimeType=text/csv`、`objectStatus=ACTIVE`，`scanStatus` 不是 `QUARANTINED / REJECTED`（扫描中允许创建）。不符返回 400 业务检查失败。
3. 本地事务：`version_no = MAX + 1`（锁 + `uk_bin_library_version_no` 兜底），插入 `VALIDATING` 版本，冻结 `file_content_hash`、`file_name`；同事务登记补偿事件 `CARD_BIN_LIBRARY_IMPORT`（eventKey = versionId，subEventType `-`）。
4. 返回 201 与版本详情。

**导入任务（`CardBinLibraryImportHandler`，补偿事件，按 COMPENSATION_EVENT_GUIDE 至少一次、可重入）：**
- 选补偿事件而不是 XXL 任务：与版本创建同事务登记，提交后立即唤醒，进程崩溃由扫描兜底；XXL 只做兜底唤醒。
- 执行策略：超时 15 分钟、最多 30 次、退避 10 秒起 ×2、上限 2 分钟（覆盖扫描排队）。
- 步骤（每次执行从头开始，事务内只写本库）：
  1. 读版本，非 `VALIDATING` 直接成功返回。
  2. 调 Platform `getFile`：扫描中返回可重试失败；`QUARANTINED / REJECTED` 或对象不可用 → 事务写 `INVALID` + `failure_reason`。
  3. 调 Platform `GET /platform/v1/country-regions` 取全量映射（内存）。
  4. **第一遍：** 申请下载地址，流式读取并计算 SHA-256，逐行校验（表头、列、取值、映射、重复前缀用内存 Set）；哈希与冻结值不符或文件级失败 → `INVALID`；有行错误 → 一个事务写前 200 条错误明细 + `INVALID`、`total_rows`、`error_rows`。
  5. **第二遍：** 先分批删除该版本可能残留的号段（上次中断，`DELETE … LIMIT 5000` 循环），再申请新下载地址流式读取，每 1000 行一个短事务批量插入号段并 CAS 更新 `processed_rows`（页面进度）。
  6. 最后一个事务 `VALIDATING → VALID`（CAS 版本号），写 `total_rows`。
- 下载地址最长 60 秒有效，只需在 60 秒内建立连接；两遍各申请一次。读流在事务之外，每批先读入内存再开事务。

**激活 / 回切（`ActivateBinLibraryVersionProcessor`，同一把锁）：**
- 请求带 `expectedActiveVersionNo`（当前页面看到的生效版本，没有时为 null），不符返回 409，防止两个运营人员同时切换。
- 目标状态 `VALID` → 类型 `ACTIVATE`；`RETIRED` → 类型 `ROLLBACK`；其他状态 400。目标已是 `ACTIVE` 返回 400。
- 一个本地事务：原 ACTIVE 行 CAS → `RETIRED`、`active_slot=NULL`；目标行 CAS → `ACTIVE`、`active_slot=1`；插入激活记录（操作人 = `X-Gateway-Actor-Subject`）。任一行数不符整体回滚。
- **原子生效与多实例：** 查询每次在数据库内按 `active_slot=1` 关联，不缓存，提交即对所有实例生效。

**保留：** 本期不删除任何版本（MVP 不做自动销毁）。估算每版本 30 万行约 45 MB，按月更新一年约 0.5 GB；需要清理时另立需求。

### 3.4 查询算法与卡属性快照

```sql
-- PaymentCardBinRangeMapper.selectActiveMatch（示意）
SELECT v.version_no, r.*
FROM t_card_bin_library_version v
LEFT JOIN t_card_bin_range r
  ON r.version_id = v.version_id AND r.bin_prefix IN (#{bin8}, #{bin7}, #{bin6})
WHERE v.active_slot = 1
ORDER BY r.prefix_length DESC
LIMIT 1;
```

- `card_bin` 为 8 位时依次取 8、7、6 位前缀；为 6 位时只有 6 位。
- 没有 ACTIVE 版本：快照全空、`recognized=false`、`binLibraryVersionNo=null`；有版本但未命中：`recognized=false`、版本号有值。
- 交易工具创建（含原提交重放）时查询；Payment 只在首次绑定时写入快照，重放沿用已存 Token（B4）。

**快照（Card 响应 `cardAttributes`，Payment 冻结同名列）：**

| 字段 | 类型 | 说明 |
|---|---|---|
| `recognized` | boolean | 是否命中号段 |
| `binLibraryVersionNo` | integer \| null | 查询时的生效版本号 |
| `matchedPrefixLength` | integer \| null | 6、7、8；未命中为 null |
| `cardBrand` | string \| null | E6 规范值 |
| `fundingType` | string \| null | CREDIT、DEBIT、PREPAID、CHARGE |
| `cardLevel` | string \| null | 开放代码 |
| `cardProduct` | string \| null | CONSUMER、COMMERCIAL |
| `issuerCountry` | string \| null | ISO alpha-2 |
| `issuerRegion` | string \| null | GCC、MEA、EUROPE、NORTH_AMERICA、LATAM、APAC |

`issuerName` 只在运营 BIN 查询返回，交易快照不带（没有交易消费者）。

### 3.5 接口

部署与兼容顺序（硬约束）：**Risk → Payment、acquiring、counterparty → Platform → Card → ops-bff → ops-web**；回滚反向。原因：Payment、acquiring、counterparty 的 Card 客户端与 Payment 的 Risk 客户端都拒绝未知字段，消费方须先认识新字段。

#### 3.5.1 Card 运营接口（新增，提供方 card-service，唯一调用方 `operations-console-bff`）

`CardApiConstants` 增加 `OPERATIONS_CONSOLE_BFF = "operations-console-bff"`；API `CardBinLibraryApi`，前缀 `/card/v1/bin-library`。调用方不在白名单返回 404（沿用 `CardCallerServiceImpl`）。写接口要求 `X-Gateway-Actor-Subject`（`[1-9][0-9]{0,18}`），缺失 400。

| # | Method / Path | 说明 | 成功 |
|---|---|---|---|
| C1 | `GET /versions?status&page&size` | 版本列表，按 `version_no` 倒序，公共分页 | 200 |
| C2 | `GET /versions/{versionNo}` | 版本详情（含进度、当前是否 ACTIVE） | 200；不存在 404 |
| C3 | `GET /versions/{versionNo}/errors?page&size` | 错误明细，按行号 | 200 |
| C4 | `POST /versions` | 用已上传文件创建版本 | 201；同文件重复提交 200 返回原版本 |
| C5 | `POST /versions/{versionNo}/activation` | 激活或回切 | 200；期望生效版本不符 409；状态不允许 400 |
| C6 | `GET /activations?page&size` | 激活历史，按时间倒序 | 200 |
| C7 | `GET /bins/{bin}?versionNo` | 按 6～8 位 BIN 查询；不带 versionNo 查 ACTIVE 版本 | 200（未识别也 200，`recognized=false`）；BIN 格式错误 400 |

C4 请求 / 响应示例：

```json
POST /card/v1/bin-library/versions
X-Caller-Service: operations-console-bff
X-Gateway-Actor-Subject: 900000000000000123
{ "fileAddress": "/files/v1/7409912387612" }

201
{
  "versionNo": 3,
  "status": "VALIDATING",
  "active": false,
  "fileAddress": "/files/v1/7409912387612",
  "fileName": "bin-2026-10.csv",
  "fileContentHash": "9f2c…（64 位）",
  "totalRows": null,
  "processedRows": 0,
  "errorRows": 0,
  "failureReason": null,
  "createdBy": "900000000000000123",
  "createTime": "2026-10-22T08:00:00.000000Z",
  "updateTime": "2026-10-22T08:00:00.000000Z"
}
```

C5 请求：`{ "expectedActiveVersionNo": 2 }`；响应 `{ "activationType": "ACTIVATE", "versionNo": 3, "previousVersionNo": 2, "operatorAccountId": "…", "activatedAt": "…" }`。

C3 元素：`{ "lineNo": 1024, "columnName": "issuer_country", "errorCode": "COUNTRY_REGION_MISSING", "errorMessage": "发卡国 XX 未登记区域", "valueExcerpt": "XX" }`。

C7 响应：快照字段（3.4）加 `issuerName`、`binPrefix`（命中前缀，≤8 位）。

错误码：只用公共 `INVALID_REQUEST`（400）、`BUSINESS_CHECK_FAILED`（400，文件来源 / 类型 / 扫描不符、状态不允许激活）、`NOT_FOUND`（404）、`CONCURRENT_REQUEST`（409，锁竞争或期望生效版本不符）、`SERVICE_UNAVAILABLE`（503，Platform 不可用）；不新增专有错误码。

#### 3.5.2 Card 交易接口调整

| 接口 | 调整 | 消费方 |
|---|---|---|
| `POST /card/v1/instruments`（`CreateCardInstrumentResponseBody`） | 新增 `cardAttributes`（3.4 字段，对象恒在，未识别时各属性为 null）；`riskAttributes` 保持 | Payment `PaymentCardClientImpl` |
| `GET /card/v1/stored-cards`（`StoredCardListResponseBody.StoredCard`） | 新增可空 `cardBrand`（按当前 ACTIVE 版本实时查，只用于展示，回切后可能与历史交易快照不同） | acquiring、counterparty |

#### 3.5.3 Platform

| 接口 | 调整 |
|---|---|
| `GET /platform/v1/country-regions`（新，`CountryRegionQueryApi`，仿 `CurrencySpecQueryApi`） | 返回 `{ "items": [ { "countryCode": "AE", "regionCode": "GCC" }, … ] }`，按国家代码升序；内部接口不经 Gateway，调用方 card-service |
| 内部文件接口（行为调整，合同不变） | `StoredContentType`：没有 PDF / PNG / JPEG 文件头，且首 8 KiB 不含 NUL、是合法 UTF-8（容忍末尾截断的多字节字符）时识别为 `text/csv`；`FormatFileScanner` 对 `text/csv` 全文校验 UTF-8 与无 NUL；`CompletedUploadVerifier` 不变（检测值 = 声明值） |
| 文件写入路由（数据，不是 DML） | 发布时在「存储管理」登记 `operations-console-bff → 现有桶 + CARD_BIN_CSV`。注意：写入路由按来源系统划分，之后 ops-bff 的其他上传会共用 CSV 策略；届时需按上传原因细分路由（另立）。**2026-10-23 更新（code-mor-db，多语言管理）**：路由 239087906591948800 的扫描策略已由 `CARD_BIN_CSV` 改指通用 `OPERATIONS_BFF_IMPORT`（version 1→2；text/csv + application/json；上限 52428800；maxUploadSeconds=900 与 FORMAT 检查不变；`CARD_BIN_CSV` 保持 ACTIVE 作为回滚目标）。改指后 `release/acceptance/accept_bin.py import good` 于 08:56:28Z 回归通过：8 行 CSV → 检查通过 → BIN 库版本 6 VALID、8/8、0 错误、未激活。Card 侧仍要求 mimeType=text/csv 与 reasonCode=CARD_BIN_LIBRARY_IMPORT |

依赖：文件第二段（Platform internal-auth `DUAL`、`X-Caller-Service`、响应带 `fileAddress`）已上线。Card 调 Platform 带 `X-Caller-Service: card-service`；ops-bff 调 Platform 带 `operations-console-bff`。

#### 3.5.4 Payment

- `PaymentCardClientImpl` / `CardInstrumentResult` 读取 `cardAttributes`（各属性可空；`recognized=true` 时 `cardBrand`、`issuerCountry`、`issuerRegion` 必须有值，否则按 Card 合同违例抛 `ErrorException`）。
- `ApplyCardInstrumentProcessor.bindCardInstrument()` 写入 Token 新 8 列（3.4）。
- `PayRiskCheckActionActor.fillCard()` → `PaymentRiskCommand` → `PaymentRiskClientImpl`：`PaymentAttemptRiskInput` 增加 `issuerCountry`、`cardBrand`、`fundingType`。
- 路由与执行同时传 `schemeCode = token.card_brand`：`PayProcessor.route()`（`RouteContext` 加字段）与 `PayActionActor.command()`（`ChannelExecutionCommand` 加字段）→ `PaymentChannelClientImpl.criteria()`；未识别不传（只命中 `*`）。
- `PaymentSucceededPayload.json()` 在 v2 末尾追加 `cardBrand`、`fundingType`、`cardLevel`（可空，`schemaVersion` 仍为 2，E4）；`events.yaml` 注释同步。billing 对应 `card_network ← cardBrand`、`funding_type ← fundingType`、`card_level ← cardLevel`（需求 B 消费）。

#### 3.5.5 Risk（`POST /risk-decisions/v1/evaluations`，合同 v1 原地扩展，E5）

- `PaymentAttemptRiskInput` 增加可选 `issuerCountryCode`（`[A-Z]{2}`；阶段 4 修正：线上键须为字段码 `issuer_country_code` 的驼峰）、`cardBrand`、`fundingType`（`[A-Z0-9_]{1,32}`）；`DecisionSourceContracts` PAYMENT_ATTEMPT v1 增加 `optional("issuer_country_code")`、`optional("card_brand")`、`optional("funding_type")`。
- `PaymentRiskInput.contractValues()` 与评估快照 3 列；重放从列读取。三字段进入稳定输入摘要的方式与 `card_bin` 相同（缺省不进入，旧评估哈希不变）。
- **阶段 4 修正：** 支付点版本内容哈希由输入字段计算，新增字段会改变 220002 的哈希，新建发布会被拒绝。改为新增冻结点版本 220003（`version_no=2`，合同仍为 v1，运行时点版本号 2）；220002 及其已生效发布照常运行，新字段要等运营在 220003 上发布新执行版本后才能被规则使用。规则页可选 3 个新变量（DML）。

#### 3.5.6 对外展示

| 接口 | 调整 |
|---|---|
| counterparty Client API `GET /customers/{customerNo}/stored-cards`（`ListStoredCardsResponseBody`） | 元素新增可空 `cardBrand`；OpenAPI 同步（新增可选响应字段，向后兼容） |
| acquiring `GET` Checkout（`GetCheckoutResponseBody.StoredCardInfo`） | 新增可空 `cardBrand`（收银台以后展示图标；本期无前端） |

#### 3.5.7 operations-console-bff（Gateway 路由 9 条，注册块 132）

外部路径 `/api/operations/card-admin/v1/bin-library/...` → ops-bff `/card-admin/v1/bin-library/...`：

| # | Method / Path（ops-bff） | 下游 | 权限 |
|---|---|---|---|
| B1 | `GET /versions` | Card C1 | READ |
| B2 | `GET /versions/{versionNo}` | Card C2 | READ |
| B3 | `GET /versions/{versionNo}/errors` | Card C3 | READ |
| B4 | `GET /activations` | Card C6 | READ |
| B5 | `GET /bins/{bin}` | Card C7 | READ |
| B6 | `POST /uploads` | Platform `startUpload`（`reasonCode=CARD_BIN_LIBRARY_IMPORT`、`mimeType=text/csv`、上传人 = 当前账号） | MANAGE |
| B7 | `POST /uploads/{uploadId}/completion` | Platform `completeUpload`；返回 `fileAddress`、扫描状态 | MANAGE |
| B8 | `POST /versions` | Card C4 | MANAGE |
| B9 | `POST /versions/{versionNo}/activation` | Card C5 | MANAGE |

- B6 请求 `{ "fileName": "bin-2026-10.csv", "sizeBytes": "1834221" }`，响应透传 Platform 的上传 ID、PUT 地址与到期时间；浏览器直传对象存储（CORS 第一段已验证）。
- 新建 `CardClientConfiguration`（Nacos 发现 `card-service`，连接 2 秒、读取 4 秒，严格 Problem 解码），pom 加 `paytech-card-api`；Platform 文件客户端加默认头 `X-Caller-Service: operations-console-bff`。
- 简单转发，不建 Processor；B6、B7 直接委托 Platform Client Port。

#### 3.5.8 commercial

`CommercialCodes.CardBrand` 补 `MADA`、`DISCOVER`、`JCB`、`DINERS`；设计文档 `design-discuss/commercial-capability-pricing/design.md:136` 改为“交易时查询 BIN 库并冻结在 Payment”。ops-web `capabilityConditions.ts` 建议值：`CARD_BRAND` 8 个、`FUNDING_TYPE` 补 CHARGE、`ISSUER_REGION` 改为 6 个地理区域（去掉 DOMESTIC / INTERNATIONAL）。发布前查 mor-test `mor_commercial` 是否已有合同使用 DOMESTIC / INTERNATIONAL / GCC 以外的区域值。

### 3.6 数据工件（均为未执行草案）

| 文件 | 目标库 | 内容 |
|---|---|---|
| [db/card/ddl.sql](./db/card/ddl.sql) | mor_card | 4 张新表 |
| [db/card/dml.sql](./db/card/dml.sql) | mor_card | 种子版本 1（ACTIVE）、17 个号段、1 条激活记录 |
| [db/payment/ddl.sql](./db/payment/ddl.sql) | mor_payment | Token 加 8 列 |
| [db/risk-decision/ddl.sql](./db/risk-decision/ddl.sql) | mor_risk_decision | 评估快照加 3 列 |
| [db/risk-decision/dml.sql](./db/risk-decision/dml.sql) | mor_risk_decision | 变量 93014—93016 |
| [db/platform-support/ddl.sql](./db/platform-support/ddl.sql) | mor_platform | `t_country_region` |
| [db/platform-support/dml.sql](./db/platform-support/dml.sql) | mor_platform | 三段：国家区域 249 行；扫描策略 `CARD_BIN_CSV`；注册块 132（111 行，休眠，含激活与回滚说明） |

执行顺序：Risk DDL+DML → Payment DDL → Platform DDL+DML 第 1、2 段 → Card DDL+DML → Platform DML 第 3 段 → 激活块 132 → 重新发布 Gateway Snapshot。

PDManer 与生成器同步：`paytech/card/docs/database/card.pdma.json`（`scripts/pdmaner/generate-card-model.mjs` + `card-model-additions.mjs` + `card-model-baseline.json`，`card-model.test.mjs`）；`payment.pdma.json`（`generate-payment-model.mjs`、`payment-execution-field-overrides.mjs`，`payment-model.test.mjs`）；risk-decision 模型与 `risk-decision-initial-data.test.mjs`；`platform-support.pdma.json`。另同步 `paytech/tools/arch-rules/.../table-ownership.yaml`（mor_card +4、mor_platform +1）、Card 新建 `card-initial-data.sql`、Card / Payment / Risk / Platform 测试库 DDL。

### 3.7 运营页面规格（供主会话绘制 Pencil 原型）

**入口：** 平台配置 → 卡 BIN 库（`/card-bin-library`，sort 90）。无 MANAGE 权限时隐藏“导入”“激活”“回切”按钮。

**页面布局（单页，上下两区）：**

1. **顶部摘要条：** 当前生效版本号、激活时间、操作人、号段数；没有生效版本时显示“尚未激活任何版本，所有卡都为未识别”。右侧按钮“导入新版本”。
2. **BIN 查询卡片：** 输入框（6～8 位数字，非数字即时提示）+“查询”；结果显示命中前缀、卡组织、借贷记、卡等级、卡产品、发卡国、发卡区域、发卡行、所用版本；未识别显示灰色“未识别（版本 N）”。可选下拉“查询版本”（默认当前生效）。
3. **版本列表（表格，公共分页）：** 列：版本号、状态标签（校验中 / 可激活 / 校验失败 / 生效中 / 已停用）、文件名、行数、错误行数、导入人、创建时间、操作。筛选：状态。操作：查看；`VALID` 显示“激活”；`RETIRED` 显示“回切”。
4. **版本详情抽屉：** 基本信息 + 状态区：
   - 校验中：进度条（`processedRows / totalRows`；`totalRows` 未知时显示“等待扫描 / 校验中”不定进度），每 3 秒轮询，离开抽屉停止。
   - 校验失败：文件级原因横幅；错误明细表（行号、列、错误代码、说明、值摘录），分页，提示“只显示前 200 条，共 N 行有错误”。
   - 可激活 / 已停用：操作按钮同列表。
5. **激活历史（折叠面板或标签）：** 时间、类型（激活 / 回切）、版本、替换的版本、操作人。

**导入弹窗（3 步）：** 选择文件（只接受 `.csv`，≤50 MiB，前端先读首行核对表头并提示标准格式与下载示例模板）→ 上传进度（直传对象存储，失败可重试）→ 完成后自动创建版本并关闭弹窗、在列表顶部出现“校验中”行、打开其详情抽屉。

**激活 / 回切确认弹窗：** 显示“当前生效 vN → 将生效 vM”、两版本号段数对比；回切额外提示“回切只影响之后的新交易，已创建的交易保留原卡属性”；确认按钮二次确认文案。409 时提示“生效版本已被他人修改，请刷新后重试”。

**状态：** 列表空（“还没有导入过版本”）、加载、接口错误（保留公共错误文案）、上传失败、校验中、校验失败、激活成功提示。

**原型（已绘制，`operations-console-web/docs/operations-console.pen`，y=85500 一行 5 张，1920×1080）：**

| 画板 | 内容 |
|---|---|
| 平台配置 · 卡 BIN 库 01 主页面｜生效版本与版本列表 | 摘要卡（当前生效、号段数、最近导入、待处理版本）、BIN 查询与结果、状态筛选、版本表、右侧选中版本详情与激活历史 |
| 02 导入新版本｜表头核对与上传进度 | 三步对话框（选择文件 → 上传 → 创建版本并校验）、本地表头与前 3 行预览、直传进度、标准格式说明与示例模板 |
| 03 版本详情｜校验中（进度） | 新版本行置顶、成功提示条、三阶段进度（扫描、校验、写入）与写入进度条、激活按钮置灰 |
| 04 版本详情｜校验失败（错误明细） | 失败横幅、错误明细分页表（行号、列、错误代码、说明；超过 8 位数字隐藏） |
| 05 激活确认｜v3 → v4（含回切说明） | 激活与回切两个确认对话框（版本对比、影响说明、二次勾选）及 409 并发冲突提示 |

与规格的差异：版本详情由抽屉改为右侧常驻详情面板（主从布局，与“数据源”页一致）。

### 3.8 研发与验证计划（按部署顺序）

| 顺序 | 系统 | 内容 | 测试入口（UNIT_TEST_GUIDE） | 验收 |
|---|---|---|---|---|
| 1 | risk-decision | 合同 v1 扩展、评估快照 3 列、变量 DML | `POST /risk-decisions/v1/evaluations` HTTP：带 / 不带新字段、格式错误、按发卡国规则命中、重放 | AC-08 |
| 2 | payment | Token 8 列、Card 映射、Risk 输入、路由与执行 `schemeCode`、`PaymentSucceeded` 追加字段 | 申请工具 HTTP（Card DTO Mock 返回识别 / 未识别）；pay HTTP（Risk、channel-connector DTO Mock 断言请求字段，MID 精确 `scheme_code` 与 `*`）；`PaymentSucceededHandler` 入口测试断言 Payload | AC-02、AC-04、AC-08、AC-09、AC-10 |
| 2 | acquiring、counterparty | Card DTO 新字段透出 `cardBrand` | 结账 GET、Client API 已存卡 HTTP（Card DTO Mock） | AC-07 |
| 3 | platform-support | CSV 识别、扫描、国家区域表与查询接口、种子 | `InternalFileApi` 上传 CSV 成功 / 二进制伪装 CSV 被拒；`GET /platform/v1/country-regions` | AC-01 前置 |
| 4 | card | 4 表、运营 7 接口、导入 Handler、查询服务、交易接口调整、种子 | 运营 HTTP（Platform DTO Mock：文件元数据、下载地址指向测试端点提供 CSV 字节）；`CardBinLibraryImportHandler` 独立入口测试（经 Dispatcher / Scanner：扫描中重试、合法文件、行错误、表头错、哈希不符、中断后重入）；激活并发（同时激活两个版本只成功一个）；交易工具 HTTP 断言快照、8 位优先、未识别；日志检索无长数字 | AC-01—AC-07、AC-11 |
| 5 | operations-console-bff | 9 条路由 | BFF HTTP（Card、Platform DTO Mock）：转发、权限头、上游错误透传 | AC-12 |
| 6 | operations-console-web | 页面、导入流程、激活确认；`capabilityConditions.ts` 建议值 | 组件测试 + 浏览器验收（本机与 mor-test） | AC-12 |
| — | Platform 注册 | 块 132 | 注册后 Gateway Snapshot 编译通过 | AC-12 |

文档：Card README、`card-initial-data.sql`、事件目录注释、commercial 设计 :136、`CURRENT_IMPLEMENTATION.md`（阶段 7 后）。

### 3.9 风险与限制

- 依赖文件第二段（Platform `DUAL`）先上线；未上线时 Card、ops-bff 调内部文件接口一律 401。
- Card 需要访问对象存储下载地址（mor-test：MinIO 私有域名 + test-ca），须在 Card 容器配置网络别名与信任库；这是业务服务首次服务端下载文件。
- 带 `schemeCode` 后 MID 能力取精确行，可能出现同精确度冲突（`MidCapabilityContext.java:88-95`）；阶段 6 清点 mor-test `t_mid_capability.scheme_code`。
- 号段只按前缀建模，不支持超过 8 位的号段区间（如 9～11 位账号区间）；卡号不足 16 位时只有 6 位 BIN，只能命中 6 位号段。
- 共同品牌卡只记主品牌（E6）。

### 3.10 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q7 | 发卡区域改为导入时推导（3.1），而不是每笔交易时推导 | 接受：交易链路不依赖 Platform，号段行和快照都冻结区域 |
| Q8 | 本期不提供版本删除，历史版本全部保留 | 接受：MVP 不做自动销毁，按估算存储可控 |
