# commercial

商务服务：合同、合同参与方与签署文件、商业条款计划 / 版本 / 价格 / 组件 / 阶梯、支付方式 Profile，以及两段式 Checkout 解析的编排（[design/06](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/06-cct-two-stage-resolution.md)）。由原 `client-contract-service` 拆出（第 04 步）。

- spring.application.name：`commercial-service`
- 端口：18108，管理端口 19108
- 数据库：`mor_commercial`（14 张表：11 张商务表、本库 BCR 两表、`t_compensation_event`）
- 包根：`com.paytech.commercial`
- 无 MQ、无补偿 Handler、无 Redis：Enforcer 禁止 AMQP / XXL / compensation-event / Redis 依赖

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-commercial-api` | `CheckoutContextApi`（路径不变）、`CheckoutErrorCode`、`AgreementPartyQueryApi` |
| `paytech-commercial-core` | 参数化商务查询、两段式解析 `CheckoutResolutionProcessor`、client-master 出站端口、Risk 资格端口（仍为 503 占位） |
| `paytech-commercial-app` | 启动类、Controller、DataSource / 事务 / MyBatis、client-master HTTP 客户端、`application.yml` |
| `paytech-commercial-test` | 全部 `auto` 测试，见[测试说明](paytech-commercial-test/README.md) |

## 两段式解析

`POST /checkout-context/v1/resolutions` 由 commercial 提供，调用方须为 `acquiring-service`（第 03 步由 `commerce-service` 改名）：

1. 调 client-master 候选接口取得有序候选、查询范围与根版本。
2. 本库 REPEATABLE READ 只读快照按 `clientId`、`entityNos`、`localPartnerIds` 显式参数读取商务事实（不再子查询主档表），事务结束后经 `party-profiles` 读取签署资料；逐候选惰性解析，fall-through 语义与原实现一致。
3. 事务外调 Risk 资格端口（尚未接入，固定 503 `OWNER_UNAVAILABLE`）。
4. 调 client-master `verify` 并重读本库合同根比对，任一变化返回 409 `CCT_CONTEXT_CHANGED`。
5. 响应结构与原 CCT 一致。client-master 或 verify 不可用一律 503 `OWNER_UNAVAILABLE`；两次调用沿用同一 Request ID 并写入解析日志。

接口分层与原解析设计见 [docs/requirements/20260921-checkout-context-resolution/design.md](docs/requirements/20260921-checkout-context-resolution/design.md)。

## 数据库与 PDManer

- 模型：[commercial.pdma.json](docs/database/commercial.pdma.json)；Schema：[commercial-schema.sql](docs/database/sql/commercial-schema.sql)；物理决定：[COMMERCIAL_PHYSICAL_DECISIONS.md](docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md)。
- 生成器：`scripts/pdmaner/generate-commercial-model.mjs`；测试库 DDL：`paytech-commercial-test/src/test/resources/init/ddl.sql`。

## 构建与验证

```bash
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -B verify -pl commercial/paytech-commercial-test -am
```

只需要本机 `local-environment` 的 MySQL（`mor_commercial` 已按 `init/ddl.sql` 建好）。
