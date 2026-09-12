# 本期四系统模型实施记录

本记录对应用户“Risk 的先不动，其余先改”和“PDManer 本期一起调整”的授权。实施范围为模型及配套工件；既有 Java、运行接口与数据库没有据此迁移。完整新业务合同见 [阶段 3 方案](./stage3-contracts.md)，现行取代范围见 [四系统决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)。

## 1. 可审阅产物

| 系统 | PDManer | 当前完整建表 SQL | 本期变化 |
|---|---|---|---|
| CCT | [client-contract.pdma.json](../../../client-contract-service/docs/database/client-contract.pdma.json) | [client-contract-schema.sql](../../../client-contract-service/docs/database/sql/client-contract-schema.sql) | entityNo 主键；独立 OE Scope；Affiliation 优先级；移出 Assignment；Agreement Party 合并签署；Client Agreement/Plan；Price 结算输入 |
| Commerce | [commerce.pdma.json](../../../commerce-service/docs/database/commerce.pdma.json) | [commerce-schema.sql](../../../commerce-service/docs/database/sql/commerce-schema.sql) | 基础订单先建立；支付准备与终止恢复身份；最终上下文固定准确 OE、合同和条款版本 |
| Payment | [payment-orchestration.pdma.json](../../../payment-orchestration-service/docs/database/payment-orchestration.pdma.json) | [payment-orchestration-schema.sql](../../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql) | 固定 OE 与 Term Versions；成功 Attempt 的不可变 Payment Pricing Fact；混合 Scope ID 支持字符串 |
| Finance | [finance.pdma.json](../../../finance-service/docs/database/finance.pdma.json) | [finance-schema.sql](../../../finance-service/docs/database/sql/finance-schema.sql) | 专用及混合 OE 身份；Price/Component/Band 追溯；结算输入；保留旧 CCT/Provider Cost Scope 分支 |

生成器、字段说明、独立模型 Oracle、场景示例和物理决策已同步。四份完整 SQL 来自 [统一导出器](../../../scripts/pdmaner/generate-oe-client-schemas.mjs)，包含完整字段/索引/表说明、类型、默认值、字符集及排序规则；模型关系不导出成物理 FK，业务检查不导出成 CHECK。SQL 只用于人工审阅后的新空库初始化。

Finance 原 `finance-balance-query-schema.sql` 保留为既有 Java 余额查询切片的历史输入，并已标注其边界；不能与新完整 Schema 混用。没有生成未经业务批准的初始化数据或推测性 OE 映射。

## 2. 实施补充

物理字段 `platform_charge_version_id` 对应接口语义 `platformServiceChargeVersionId`。此短名用于满足物理字段长度限制，不改变业务含义。

除专用 CHAR(5) OE 引用外，Payment Routing Policy 的 `scope_id`、Finance Account 的 `owner_id`、Economic Component 的 `party_id` 也允许 OE 身份，因此同步改为大小写敏感 VARCHAR(64)。它们必须连同类型解释；普通数值主键和 CCT 法律 Party 主键保持 BIGINT。此项补齐阶段 3 字段矩阵未完整列出的混合身份引用，不是新增业务能力。

新模型移出旧表不等于执行旧库 DROP。`entity_assignment_rule`、`client_entity_assignment`、旧独立 Acceptance 及其数据是否保留在运行环境，由历史 Reader 和迁移盘点决定。本期没有修改历史订单、旧 Hash、金额或财务结果。

现实场景中的合成数字可调整为 entityNo；Finance 旧费用示例明确 `LEGACY_SCOPE`，仍验证原金额、Journal、对账与出款关系。CCT 历史字段用 `legacy:` 标识，当前模型映射另行列明；Payment 增加最终定价事实示例。它们均为文档夹具，不是数据库迁移。

## 3. 复现与验证

在工作区根目录运行：

```sh
node scripts/pdmaner/generate-client-contract-model.mjs --check
node scripts/pdmaner/generate-commerce-model.mjs --check
node scripts/pdmaner/generate-payment-model.mjs --check
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node scripts/pdmaner/generate-oe-client-schemas.mjs --check
node --test scripts/pdmaner/{client-contract,commerce,payment,finance,oe-client}-model.test.mjs
node --test scripts/pdmaner/{client-contract,commerce,payment,finance}-scenario-doc.test.mjs
npm run test:content --prefix requirements-site
```

初始新模型断言在旧模型上失败 7 项，修改后专项断言通过。最终验证的范围与数量统一见 [当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#oe-client-model-20260911)。结构检查包括主键、可空性、唯一索引、本库关系、图组、控制值、精度与稳定 ID；Schema 检查还验证无执行性 DROP/ALTER/DML/CHECK/FK 和 MySQL 注释长度。

PDManer 应用已刷新 CCT，确认 Scope 与合并 Party 已载入。规范检查中，OE/Scope/Affiliation/Agreement Party 均通过；`business_change_version` 与 `compensation_event` 各有一条大字段类型建议提示。其 `snapshot_json/payload_json` 的 JSON 类型及 largeText Base Type 与修改前完全相同，自动物理契约和手工 SQL 保持 JSON。本记录不将此视为 GUI 全部通过；其他三个模型的应用内检查仍待完成。

## 4. 运行迁移前置条件

1. 盘点每个环境的真实 OE、历史订单、合同签署、Assignment、Scope、Hash 与 Finance 主体引用，形成经过审核的一对一身份映射及旧格式识别规则。
2. 完成四个系统的 Java/HTTP/MQ/恢复链路；新旧格式显式分流。Risk 当前工作完成并另行授权后接入新身份和审核合同，在此之前禁止激活新 Scope 流程。
3. 为实际存量制定分阶段人工 SQL、备份与回退步骤。新增完整 Schema 不能当作存量迁移脚本；不得自动 DROP、截断数字 ID 或重算旧 JSON/Hash。
4. 应用与数据库版本一致后，执行各 Owner 的真实数据库、HTTP、幂等、支付准备、签署、晚到事实、定价及对账测试；完成 PDManer 应用内剩余检查后，再按独立授权部署。

本期没有执行 SQL、提交、推送、部署，也没有修改 Risk 的代码、模型、接口或场景。
