# card

- spring.application.name：`card-service`
- 端口：18111（管理端口 19111，无 context path）
- 数据库：`mor_card`（账号 `card_app`）
- 来源现有工程：`payment-orchestration-service`（第 12 步自 `paytech/payment/paytech-payment-card` 迁出，design/05）

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-card-api` | `CardInstrumentApi`（`POST /card/v1/instruments`）、`CardMaterialTicketApi`（`POST /card/v1/material-tickets`）、`ChannelCardMaterialApi`（`POST /card/v1/channel-card-materials/reveal`）、`StoredCardApi`（`/card/v1/stored-cards…`）与 DTO |
| `paytech-card-core` | 卡 / 凭证 / 交易工具 / 已存卡与读卡票据表、卡加密（`com.paytech.card.core.crypto`）、Vault 托管 DEK、Redis 一次性 CVV、`PaymentSucceeded` 消费 Handler、孤立工具回收 |
| `paytech-card-app` | Spring Boot 入口、Controller、DataSource / MyBatis、Redis、Vault Client（Caller `card-service`）、`paytech-messaging` 消费、补偿与 XXL-Job |
| `paytech-card-test` | 隔离的 `auto` 测试（`mor_card`、本地 Redis / RabbitMQ、Vault DTO Mock）与 R1–R12 |

PCI 边界（D10、R11）：只有 card-core 与 card-app 可以触碰卡表与卡加密包；CVV 不落库、不哈希、不进日志 / MQ / 补偿 Payload。

## 构建与测试

```bash
cd paytech && ./mvnw -B verify -pl card/paytech-card-test -am -Dpaytech.local-environment.dir=<repo>/local-environment
```

测试库须先以 root 人工应用 `paytech-card-test/src/test/resources/init/ddl.sql`。
