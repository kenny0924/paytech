# client-master

客户主档服务：Party、Client、Local Partner、Operating Entity、入驻草稿 / 人员 / 材料 / 提交 / 审核 / 分类、工作区与 Client 生命周期投影，以及两段式 Checkout 解析的主档候选（[design/06](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/06-cct-two-stage-resolution.md)）。由原 `client-contract-service` 拆出（第 04 步）。

- spring.application.name：`client-master-service`（Nacos 发现名；出站 `X-Caller-Service` 与 Vault 调用方同为该名）
- 端口：18101，管理端口 19101
- 数据库：`mor_client_master`（27 张表：21 张主档表、`t_consumer_legal_document`、`t_seller_disclosure`、`t_client_stored_card_setting`、本库 BCR 两表、`t_compensation_event`）
- 包根：`com.paytech.client.master`；`paytech-client-master-api` 中原 onboarding / workspace 合同保留旧包 `com.paytech.client.contract.api` 一个版本，新合同位于 `com.paytech.client.master.api`

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-client-master-api` | 入驻、工作区合同；`ClientMasterCheckoutApi`（候选、复核、参与方资料）；`ClientStoredCardSettingApi` |
| `paytech-client-master-core` | Mapper / Repository / Domain Service / Processor、MQ 消费者 `ClientReviewResultListener`、三个补偿 Handler、出站端口实现 |
| `paytech-client-master-app` | 启动类、Controller、DataSource / 事务 / MyBatis、HTTP 客户端、RabbitMQ 队列声明、补偿装配、`application.yml` |
| `paytech-client-master-test` | 全部 `auto` 测试与测试设施，见[测试说明](paytech-client-master-test/README.md) |

## 对外合同

- 入驻与工作区：路径与语义未变，见[入驻接口合同](../../requirements/20260914/client-console-注册与客户入驻/api-contract.md)。
- `POST /client-master/v1/checkout-candidates`、`POST /client-master/v1/checkout-candidates/verify`、`GET /client-master/v1/party-profiles?ids=`：仅 `commercial-service` 调用，单库 REPEATABLE READ 只读快照。
- `GET /client-master/v1/clients/{clientId}/stored-card-setting`：Client 级已存卡开关（D42），没有记录视为未开通；候选响应的 `client.storedCardSetting` 复用同一读取。
- 人员“共享”标记中的合同引用经 `commercial` 的 `GET /commercial/v1/agreement-parties/exists` 在数据库事务外读取，失败关闭（视为共享，禁止修改）。

## 数据库与 PDManer

- 模型：[client-master.pdma.json](docs/database/client-master.pdma.json)；Schema：[client-master-schema.sql](docs/database/sql/client-master-schema.sql)；初始化 DML：[client-master-initial-data.sql](docs/database/sql/client-master-initial-data.sql)（首期无种子行）；物理决定：[CLIENT_MASTER_PHYSICAL_DECISIONS.md](docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md)。
- 生成器：`scripts/pdmaner/generate-client-master-model.mjs`；Schema 由 `scripts/pdmaner/generate-oe-client-schemas.mjs` 从模型生成。
- 应用与测试都不建表、不执行初始化 DML；测试库 DDL 见 `paytech-client-master-test/src/test/resources/init/ddl.sql`。

## 开关与配置

- `onboarding.draft-enabled`、`onboarding.recovery-enabled`（默认 false）沿用原 CCT 语义；`compensation.enabled` 随 recovery。
- `internal-auth.mode` 默认 `"OFF"`（必须带引号）；全部出站客户端经 `HttpServiceClientFactory` 并按目标服务名附加内部 Token。
- Checkout 候选在提供 `spring.datasource.url` 时装配。

## 构建与验证

```bash
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -B verify -pl client-master/paytech-client-master-test -am
```

需要本机 `local-environment` 的 MySQL（`mor_client_master` 已按 `init/ddl.sql` 建好）、Redis（`PAYTECH_REDIS_PASSWORD`）与 RabbitMQ（`MOR_RABBITMQ_*`）。
