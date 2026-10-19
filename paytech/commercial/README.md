# commercial

商务服务：合同、合同参与方与签署文件、产品目录（产品 / 可开通项 / 计费事件）、合同订阅与开通项、商业条款计划 / 版本 / 价格 / 组件 / 阶梯、定价画像，以及两段式 Checkout 解析的编排（[design/06](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/06-cct-two-stage-resolution.md)）。由原 `client-contract-service` 拆出（第 04 步）。

- spring.application.name：`commercial-service`
- 端口：18108，管理端口 19108
- 数据库：`mor_commercial`（20 张表：17 张商务表、本库 BCR 两表、`t_compensation_event`）
- 包根：`com.paytech.commercial`
- 无 MQ、无补偿 Handler、无 Redis：Enforcer 禁止 AMQP / XXL / compensation-event / Redis 依赖

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-commercial-api` | `CheckoutContextApi`（路径不变）、`CheckoutErrorCode`、`AgreementPartyQueryApi`；六层定价闭合枚举（`api/enums`）与开放目录稳定代码 `CommercialCodes`（`api/constant`） |
| `paytech-commercial-core` | 参数化商务查询、两段式解析 `CheckoutResolutionProcessor`、client-master 出站端口、Risk 资格端口（仍为 503 占位，risk-decision 尚无对应合同）；全部 17 张商务表的完整行 DataObject 与写入映射（Mapper XML + Repository，尚无生产写入方）；`CheckoutRecords` 保留为 Checkout 只读投影 |
| `paytech-commercial-app` | 启动类、Controller、DataSource / 事务 / MyBatis、client-master HTTP 客户端、`application.yml` |
| `paytech-commercial-test` | 全部 `auto` 测试，见[测试说明](paytech-commercial-test/README.md) |

## 两段式解析

`POST /checkout-context/v1/resolutions` 由 commercial 提供，调用方须为 `acquiring-service`（第 03 步由 `commerce-service` 改名）：

1. 调 client-master 候选接口取得有序候选、查询范围与根版本。
2. 本库 REPEATABLE READ 只读快照按 `clientId`、`entityNos`、`localPartnerIds` 显式参数读取商务事实（不再子查询主档表），同一快照读取范围内合同的订阅、开通项、约束与引用的可开通项目录；事务结束后经 `party-profiles` 读取签署资料；逐候选惰性解析，fall-through 语义与原实现一致。
3. 六层模型的逐候选门槛（六层定价需求 technique §2.3、§2.5）：资金模式 `FEES_ONLY` 本期落空；合同必须订阅 `ACQUIRING`；请求带 `paymentMethodCode` 时，同合同同 OE 下必须恰有一个 ENABLED、区间含商业时点、`CURRENCY` / `CONSUMER_COUNTRY` 约束满足的开通项（零个落空，多个 409 `CCT_CONTEXT_AMBIGUOUS`；带卡品牌维度的目录项本期按其 `payment_method_code` 匹配）；条款版本在 SCHEDULED / ACTIVE / SUPERSEDED 中按区间选唯一一条，不看 `active_slot`。落空原因写 INFO 日志。
4. 事务外调 Risk 资格端口（risk-decision 尚无 Client / 候选资格合同，固定 503 `OWNER_UNAVAILABLE`）。
5. 调 client-master `verify` 并重读本库合同根比对，任一变化返回 409 `CCT_CONTEXT_CHANGED`。
6. 响应在原 CCT 结构上只做加字段：`clientTerms` 增加 `businessMode`、`fundMode`、`pricingAuthority`、`referralPartnerId`、`agreementProducts`、`capability`，顶层增加 `derived`（MOR：卖方 OE Party、税务 `OPERATING_ENTITY`；PSP：卖方 Client Party、税务 `CLIENT`；资金账簿均为 `OPERATING_ENTITY`）。client-master 或 verify 不可用一律 503 `OWNER_UNAVAILABLE`；两次调用沿用同一 Request ID 并写入解析日志。

接口分层与原解析设计见 [docs/requirements/20260921-checkout-context-resolution/design.md](docs/requirements/20260921-checkout-context-resolution/design.md)。

## 数据库与 PDManer

- 模型：[commercial.pdma.json](docs/database/commercial.pdma.json)；Schema：[commercial-schema.sql](docs/database/sql/commercial-schema.sql)；初始化 DML（产品目录）：[commercial-initial-data.sql](docs/database/sql/commercial-initial-data.sql)；物理决定：[COMMERCIAL_PHYSICAL_DECISIONS.md](docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md)。
- 本机测试库增量：[local/2026-10-19-round3-local.sql](docs/database/sql/local/2026-10-19-round3-local.sql)（只写不执行，由主会话审阅后人工执行；执行状态以 `agent-docs/CURRENT_IMPLEMENTATION.md` 为准）。
- 生成器：`scripts/pdmaner/generate-commercial-model.mjs`；测试库 DDL：`paytech-commercial-test/src/test/resources/init/ddl.sql`。

## 构建与验证

```bash
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -B verify -pl commercial/paytech-commercial-test -am
```

只需要本机 `local-environment` 的 MySQL（`mor_commercial` 已按 `init/ddl.sql` 建好）。
