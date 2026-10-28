# card

- spring.application.name：`card-service`
- 端口：18111（管理端口 19111，无 context path）
- 数据库：`mor_card`（账号 `card_app`）
- 来源现有工程：`payment-orchestration-service`（第 12 步自 `paytech/payment/paytech-payment-card` 迁出，design/05）

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-card-api` | `CardInstrumentApi`（`POST /card/v1/instruments`，响应含 BIN 库卡属性快照 `cardAttributes`）、`CardMaterialTicketApi`（`POST /card/v1/material-tickets`）、`ChannelCardMaterialApi`（`POST /card/v1/channel-card-materials/reveal`）、`StoredCardApi`（`/card/v1/stored-cards…`，元素含 `cardBrand`）、`CardBinLibraryApi`（`/card/v1/bin-library/**`，只允许 `operations-console-bff`）与 DTO、稳定代码（`CardBrand`、`CardFundingType`、`CardProduct`、BIN 库状态 / 激活类型 / 导入错误码） |
| `paytech-card-core` | 卡 / 凭证 / 交易工具 / 已存卡与读卡票据表、BIN 库四表（版本、号段、导入错误、激活记录）、卡加密（`com.paytech.card.core.crypto`）、Vault 托管 DEK、Redis 一次性 CVV、`PaymentSucceeded` 消费 Handler、孤立工具回收、BIN 库导入补偿 Handler `CARD_BIN_LIBRARY_IMPORT`、Platform 文件客户端；国家区域读参考数据客户端（`platform-support.reference-data.client`，`kinds: [COUNTRY]`）的进程内快照 |
| `paytech-card-app` | Spring Boot 入口、Controller、DataSource / MyBatis、Redis（CVV 与 `card-bin-library` 资源锁）、Vault Client（Caller `card-service`）、Platform 服务组（`platform-support-service`，带 `X-Caller-Service: card-service`）与对象存储下载客户端、`paytech-messaging` 消费、补偿与 XXL-Job |
| `paytech-card-test` | 隔离的 `auto` 测试（`mor_card`、本地 Redis / RabbitMQ、Vault 与 Platform DTO Mock）与 R1–R12 |

## BIN 库与卡属性（2026-10-22，`requirements/20261022/card-BIN库与卡属性`）

- 存储：`t_card_bin_library_version`（`active_slot` 唯一保证至多一个 ACTIVE）、`t_card_bin_range`（`version_id + bin_prefix` 唯一）、`t_card_bin_import_error`（每版本前 200 条）、`t_card_bin_library_activation`（激活 / 回切留痕）。种子见 `docs/database/sql/card-initial-data.sql`（合成号段 + 公开测试卡号段，版本 1 ACTIVE）。
- 导入：运营经 Platform 文件服务直传平台标准 CSV（表头 `bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name`）→ `POST /versions` 建 VALIDATING 版本并同事务登记 `CARD_BIN_LIBRARY_IMPORT` → Handler 两遍下载（校验 + SHA-256、每 1000 行一个短事务写入），扫描中可重试、失败写 INVALID 与原因 / 错误明细；可重入。发卡区域在导入时按参考数据客户端的进程内国家快照（全部状态，`regionCode`）推导并冻结在号段上；快照未就绪时导入任务按可重试处理。
- 激活 / 回切：`POST /versions/{versionNo}/activation`，带 `expectedActiveVersionNo`（不符 409），与创建版本共用 `card-bin-library` 锁；查询不缓存，提交即对所有实例生效。
- 查询：一条 SQL 按生效占位关联，8 → 7 → 6 位最长前缀；创建交易工具（含原提交重放）时查询并在响应 `cardAttributes` 返回，Payment 首次绑定时冻结；已存卡列表的 `cardBrand` 按当前版本实时查询，只用于展示。
- 配置：`redis.coordination.enabled=true`（资源锁）；`compensation.lease-duration=20m`（须长于 Handler 15 分钟超时）；`spring.http.serviceclient.platform-support-service`（`lb://platform-support-service/platform-support`）。部署须让 Card 能访问对象存储下载地址（mor-test 为 MinIO 私有域名 + test-ca 信任库），并依赖 Platform 内部文件接口 DUAL 与 CSV 识别先上线。

PCI 边界（D10、R11）：只有 card-core 与 card-app 可以触碰卡表与卡加密包；CVV 不落库、不哈希、不进日志 / MQ / 补偿 Payload。

## 构建与测试

```bash
cd paytech && ./mvnw -B verify -pl card/paytech-card-test -am -Dpaytech.local-environment.dir=<repo>/local-environment
```

测试库须先以 root 人工应用 `paytech-card-test/src/test/resources/init/ddl.sql`（已有库执行 BIN 库需求的 `db/card/ddl.sql` 增量）。
