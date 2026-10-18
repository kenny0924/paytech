# consumer

- spring.application.name：`consumer-service`
- 端口：18109，管理端口 19109；无 Context Path
- 数据库：`mor_consumer`，账号 `consumer_app`
- 来源现有工程：`commerce-service`（第 03 步从 Commerce 拆出 Customer）

## 职责

Client 命名空间内的 Customer（`t_customer`）：身份只来自 Client 服务端传入的不透明 `consumerReference`，删除不可撤销；Customer 删除后经本库补偿任务 `STORED_CARD_INVALIDATION` 在事务外调用 Payment 失效其全部已存卡。`customer_id` 与 `customer_no` 全局稳定（S6）。按订单的消费者上下文（`t_consumer_context`）仍属 acquiring。

## 合同

- Client API（经 Gateway，路径与原 Commerce 相同）：`POST/GET/DELETE /client-gateway-api/v1/customers…`、`GET /client-gateway-api/v1/customers/{customerNo}/stored-cards`、`DELETE …/stored-cards/{storedCardToken}`；错误码 `CUSTOMER_NOT_FOUND`、`INVALID_CONSUMER_REFERENCE`、`STORED_CARD_NOT_FOUND`（及已存卡上游协议错误 `UPSTREAM_PROTOCOL_ERROR`）在 `ConsumerErrorCode`。
- acquiring 内部合同 `ConsumerCustomerApi`（私网，不经 Gateway）：
  - `POST /consumer/v1/customers/resolve`：建或取，`uk(client_id, active_reference)` 幂等，撞唯一键重读。
  - `GET /consumer/v1/customers/{customerId}`：按主键读取（含已删除），不存在 404 `CUSTOMER_NOT_FOUND`。
  - `GET /consumer/v1/card-saving-eligibility?clientId=&customerId=`：组合本库 Customer 与 client-master `GET /client-master/v1/clients/{clientId}/stored-card-setting`（D42）；client-master 不可用或响应无效时 `eligible=false, reason=SETTING_UNAVAILABLE`（失败关闭）。

## 依赖与运行

- 出站发现名：`payment-service`（Context Path `/payment`，`StoredCardApi`）、`client-master-service`（`ClientStoredCardSettingApi`）；`internal-auth.mode` 默认 `"OFF"`。
- 不使用 Redis 锁、不消费 MQ；补偿事件与 XXL-Job 配置随 app。
- PDManer 模型 `docs/database/consumer.pdma.json`（`scripts/pdmaner/generate-consumer-model.mjs` 生成），Schema `docs/database/sql/consumer-schema.sql`，物理决策见 [CONSUMER_PHYSICAL_DECISIONS.md](docs/database/CONSUMER_PHYSICAL_DECISIONS.md)。
- 测试：`cd paytech && ./mvnw -B verify -pl consumer/paytech-consumer-test -am`；需要本机 `mor_consumer`（`paytech-consumer-test/src/test/resources/init/ddl.sql`）。
