# 任务卡 A · 持久化层、枚举、目录初始化与测试基座

> 需求：[requirement.md](../requirement.md)；设计：[design.md](../../../../design-discuss/commercial/design.md)、[database.md](../../../../design-discuss/commercial/database.md)；接口：[technique.md 第 2 节](../technique.md)。执行者：Opus 5.5 agent（主树 `paytech/commercial`，不建分支、不提交、不执行 SQL）。

## 目标

让 B（合同 / 开通 / 画像写入）、C（条款 / 命中 / 费率卡）、D（解析扩展）三线可以只写 Processor、Domain Service 与 API，不再碰表映射。

## 范围

1. **测试 DDL**：`paytech-commercial-test/src/test/resources/init/ddl.sql` 补齐六张产品 / 开通表与 `t_commercial_term_price.currency`，逐字取自 `docs/database/sql/commercial-schema.sql`；删除文件头"尚未接入"两行。另产出本机增量脚本 `docs/database/sql/local/2026-10-19-round3-local.sql`（新建目录）：对已按 round2 变更过的本机库新增六张表、给 `t_commercial_term_price` 加 `currency` 并重建两个唯一键与 `idx_term_price_oe`（表为空，直接 `NOT NULL`）。脚本只写不执行，由主会话审阅后执行。
2. **枚举**（`paytech-commercial-api/.../api/enums`，实现 `CodeEnum`，每项之间空一行）：`BusinessMode`、`FundMode`、`PricingAuthority`、`ProductCode`、`ProductBusinessModeScope`、`ProductStatus`、`CapabilityKind`、`AgreementCapabilityStatus`、`CapabilityLimitCode`、`PricingProfileKind`、`PricingDimension`（14 值）、`PricingModel`、`FeeTaxTreatment`、`RefundBehavior`、`BandBasis`、`CalculationBase`、`CommercialTermPlanStatus`；既有 `CommercialTermComponentBandMode` 增 RETROACTIVE。`term_type`、`trigger_code`、`fee_event_code`、`capability_code`、`payment_method_code`、`dimension value` 为开放目录，用 String 承载，稳定项放 `*Codes` 常量类。闭合枚举经 `paytech-mybatis-support` 的 `CodeEnumTypeHandler` 逐个显式注册。
3. **DataObject / Mapper / Repository**（`paytech-commercial-core`，`interface + *RepositoryImpl`，SQL 放 XML）：六张新表全量 CRUD（insert、按主键 / 唯一键查、按父查、条件更新带影响行数）；既有表补新列：`Agreement`（4 列）、`PricingProfile.profileKind`、`CommercialTermVersion.pricingModel/feeTaxTreatment`、`CommercialTermComponent.refundBehavior/bandBasis`、`CommercialTermPrice.currency`。既有只读查询（`findForCheckout` 等）保持行为，补新列映射。中文 JavaDoc 按规范。
4. **目录初始化 DML**：`docs/database/sql/commercial-initial-data.sql`（本服务唯一初始化文件，幂等 `INSERT ... ON DUPLICATE KEY UPDATE` 不可用时用 `INSERT IGNORE` 并写明）。内容：
   - `t_product`：ACQUIRING（BOTH，可选）、PAYOUT（BOTH，可选）、FX（BOTH，可选）、DISPUTE（BOTH，可选）、TAX_INVOICE（BOTH，基座）、DATA_ANALYTICS（BOTH，可选）、PLATFORM（BOTH，基座），status ACTIVE。
   - `t_product_capability`：ACQUIRING → CARD_VISA / CARD_MC / CARD_AMEX / CARD_UNIONPAY（kind PAYMENT_METHOD，payment_method_code CARD，dimension CARD_BRAND = VISA / MASTERCARD / AMEX / UNIONPAY）、APPLE_PAY、GOOGLE_PAY（payment_method_code 同名，无维度）；PAYOUT → PAYOUT_LOCAL / PAYOUT_SWIFT（PAYOUT_RAIL）；FX → FX_SPOT（FX）；DISPUTE → DISPUTE_HANDLING（DISPUTE）；DATA_ANALYTICS → ANALYTICS_SUBSCRIPTION（PERIODIC_SERVICE）。status ACTIVE。
   - `t_product_fee_event`（fee_event_code × term_type → billing_fact_type）：ACQUIRING：AUTH / CAPTURE / SALE × CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE → PAYMENT；REFUND × 三类 → REFUND；CHARGEBACK × CLIENT_PRICING → DISPUTE；TOKEN_STORED × CLIENT_PRICING → PAYMENT。PAYOUT：PAYOUT_SUBMITTED / PAYOUT_RETURNED × PAYOUT_FEE → PAYOUT_FEE。FX：FX_DEAL × FX_SPREAD → FX_SELL。DISPUTE：CHARGEBACK × DISPUTE_FEE → DISPUTE。PLATFORM：MONTHLY_CYCLE / SETUP / KYB_REVIEW × PLATFORM_FEE → PLATFORM_FEE。ID 用固定 Snowflake 风格常量（9100 开头），时间 UTC。
   - 同一内容的测试固件（JSON 或 Mocker）供 B / C 使用。
5. **测试基座**（按 `agent-docs/UNIT_TEST_GUIDE.md` 与现有 `CheckoutFixture` 风格）：六张新表与新列的 Builder（`template()` 合理默认）、本地 Mocker、`CommercialDataCleaner` 表集合更新、`CommercialTestMapper` 清理方法；`checkout-query.json` 固件补 `currency`。为每个 Repository 写最小 auto 测试（真实本地 MySQL，不 Mock）。
6. **S5 收口（本模块内）**：Plan `status` 改用 `CommercialTermPlanStatus`；`calculation_base` 改用 `CalculationBase`；`business_calendar_code` 固定常量 `AE_BUSINESS_DAYS` 放 `CommercialCodes`。`CheckoutErrorCode` 改名与 BCR 注释不在本卡（D 线 / 白名单拆分）。

## 不做

Processor、Domain Service、HTTP API、事件、BFF、页面；不改 client-master；不改 `paytech-commercial-api` 既有 DTO 字段；不动其他会话的脏文件。

## 验证与交付

- `./mvnw -o -pl commercial/paytech-commercial-test -am verify`（JDK 25，env 来自 `local-environment/.env.local`）：新 Repository 测试与既有 24 个测试全绿。本机库缺六张表前，新表测试会失败：把 round3 脚本先交主会话执行后再跑；报告里写清哪次运行在脚本执行前 / 后。
- `node --test scripts/pdmaner/client-contract-model.test.mjs`：93/96 不变（3 项既有失败）。
- arch-rules（`paytech/tools/arch-rules`）通过。
- 报告：文件清单、新增类清单、测试计数、round3 脚本路径、解释性选择、未做项。
