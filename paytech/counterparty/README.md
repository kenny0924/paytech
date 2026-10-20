# counterparty

- spring.application.name：`counterparty-service`（2026-10-19 由 consumer 服务改名，不保留旧名别名，见[重新定位决定](../../agent-docs/decisions/2026-10-19-counterparty-repositioning.md)）
- 端口：18109，管理端口 19109；无 Context Path
- 数据库：`mor_counterparty`，账号 `counterparty_app`
- 来源现有工程：`commerce-service`（第 03 步从 Commerce 拆出 Customer，初名 consumer）

## 职责

Client 命名空间内、不与平台签约的交易相对方主档。当前只有付款角色 Customer（`t_customer`）：身份只来自 Client 服务端传入的不透明 `consumerReference`，删除不可撤销；Customer 删除后经本库补偿任务 `STORED_CARD_INVALIDATION` 在事务外调用 card-service 失效其全部已存卡。`customer_id` 与 `customer_no` 全局稳定（S6）。按订单的消费者上下文（`t_consumer_context`）仍属 acquiring。

## 合同

- Client API（经 Gateway，路径与原 Commerce 相同）：`POST/GET/DELETE /client-gateway-api/v1/customers…`、`GET /client-gateway-api/v1/customers/{customerNo}/stored-cards`、`DELETE …/stored-cards/{storedCardToken}`；错误码 `CUSTOMER_NOT_FOUND`、`INVALID_CONSUMER_REFERENCE`、`STORED_CARD_NOT_FOUND`（及已存卡上游协议错误 `UPSTREAM_PROTOCOL_ERROR`）在 `CounterpartyErrorCode`。
- acquiring 内部合同 `CounterpartyCustomerApi`（私网，不经 Gateway）：
  - `POST /counterparty/v1/customers/resolve`：建或取，`uk(client_id, active_reference)` 幂等，撞唯一键重读。
  - `GET /counterparty/v1/customers/{customerId}`：按主键读取（含已删除），不存在 404 `CUSTOMER_NOT_FOUND`。
  - `GET /counterparty/v1/card-saving-eligibility?clientId=&customerId=`：组合本库 Customer 与 client-master `GET /client-master/v1/clients/{clientId}/stored-card-setting`（D42）；client-master 不可用或响应无效时 `eligible=false, reason=SETTING_UNAVAILABLE`（失败关闭）。

## 依赖与运行

- 出站发现名：`card-service`（`StoredCardApi`，`X-Caller-Service: counterparty-service`，经 `integration/card/StoredCardClient`）、`client-master-service`（`ClientStoredCardSettingApi`）；`internal-auth.mode` 默认 `"OFF"`。
- 配置前缀 `counterparty.*`；运行环境变量 `MOR_COUNTERPARTY_DB_URL` / `MOR_COUNTERPARTY_DB_USERNAME` / `MOR_COUNTERPARTY_DB_PASSWORD`、`COUNTERPARTY_SNOWFLAKE_REGION_ID` / `COUNTERPARTY_SNOWFLAKE_WORKER_ID`、`COUNTERPARTY_INTERNAL_AUTH_MODE`。
- 不使用 Redis 锁、不消费 MQ；补偿事件与 XXL-Job 配置随 app。
- PDManer 模型 `docs/database/counterparty.pdma.json`（`scripts/pdmaner/generate-counterparty-model.mjs` 生成），Schema `docs/database/sql/counterparty-schema.sql`，物理决策见 [COUNTERPARTY_PHYSICAL_DECISIONS.md](docs/database/COUNTERPARTY_PHYSICAL_DECISIONS.md)。
- 测试：`cd paytech && ./mvnw -B verify -pl counterparty/paytech-counterparty-test -am`；需要本机 `mor_counterparty`（`paytech-counterparty-test/src/test/resources/init/ddl.sql`）。
