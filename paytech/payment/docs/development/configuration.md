# Payment 配置清单

配置编码规则统一见[写接口开发规范第 16 节](processor-conventions.md#16-配置与-nacos)。本文只维护具体配置项、加载方式及生效范围。

## 1. 自有配置

| 配置项 | 配置类 | 默认值 / 约束 | 生效范围 |
|---|---|---|---|
| `payment.execution.instrument-preparation-window` | [PaymentExecutionProperties](../../paytech-payment-core/src/main/java/com/paytech/payment/execution/core/configuration/PaymentExecutionProperties.java) | `PT0S`；Duration，例如 `PT5M`；零值或负值禁止新工具申请 | 每次新申请读取一次；不修改原 Token 到期时间，不阻止原提交重放 |
| `payment.card.fingerprint-key` | [PaymentCardProperties](../../paytech-payment-card/src/main/java/com/paytech/payment/card/core/configuration/PaymentCardProperties.java) | 无默认值；必填，Base64 且解码后至少 32 字节，否则启动失败；环境变量 `PAYMENT_CARD_FINGERPRINT_KEY` | 卡指纹与卡凭证摘要（HMAC-SHA256）密钥，启动时读取；不写入模板或 Nacos。单一密钥、不可轮换：卡按指纹唯一、按卡号定位都依赖它，更换后已存的卡与凭证都无法再匹配。每张卡的数据加密密钥由 Vault 托管（[决定](../../../../agent-docs/decisions/2026-10-15-vault-payment-card-dek.md)），不在本服务配置 |
| `payment.outbound.channel-connector-context-path` / `channel-candidate-read-timeout` / `channel-operation-read-timeout` | [PaymentOutboundProperties](../../paytech-payment-app/src/main/java/com/paytech/payment/configuration/PaymentOutboundProperties.java) | 空串（channel-connector 无 context path）、`2s`、`35s`（须大于 channel-connector 调用 Platform 的 30s） | 启动时创建客户端，修改需重启；渠道操作超时按渠道结果未知处理 |
| `payment.event.source-service` / `instance-id` | [PaymentEventProperties](../../paytech-payment-core/src/main/java/com/paytech/payment/execution/core/configuration/PaymentEventProperties.java) | `payment`；`${paytech.instance-id}`（`PAYTECH_INSTANCE_ID`，默认 `main`） | `PaymentSucceeded` 信封的 sourceService 与 instanceId（design/08 §2）；sourceService 必须等于 `messaging.producer.service` |
| `payment.outbound.risk-context-path` / `risk-read-timeout` | 同上 | 空串、`1s` | 启动时创建客户端，修改需重启；调用 Risk 统一评估 `/risk-decisions/v1/evaluations`，409、超时与 5xx 用同一请求重试一次，仍失败则风控失败关闭（[需求](../../../../requirements/20261015/payment-接入Risk统一评估/technique.md)） |
| `payment.identifier.region-id` | [PaymentIdentifierProperties](../../paytech-payment-app/src/main/java/com/paytech/payment/configuration/PaymentIdentifierProperties.java) | 必填，0～31 | 创建 ID 生成器时读取，修改需重启 |
| `payment.identifier.worker-id` | 同上 | 必填，0～31；同一区域每个实例唯一 | 由实例部署环境注入，修改需重启 |

工具有效期从 `payment.instrument.preparation-window` 移至 `payment.execution.instrument-preparation-window`。旧配置键不再读取；现有环境变量 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW` 保留为本地默认值来源。

重构第 05 步：`payment.channel.capability-definitions` 随渠道配置迁到 channel-connector（`channel-connector.capability-definitions`，D06）；`payment.outbound.platform-context-path` / `platform-read-timeout` 随 Platform 标准 API 客户端迁到 channel-connector（`channel-connector.outbound.*`）。Payment 不再读取这些键。

## 2. Nacos 组织与加载

- Namespace 按环境隔离，本地为 `mor-local`；Group 默认 `DEFAULT_GROUP`。
- 可选共享 Data ID：`mor-common.yaml`，先加载，关闭动态刷新。本仓库不维护或发布共享内容。
- Payment Data ID：`payment-service.yaml`（原 `payment-orchestration-service.yaml`，随应用名改名），后加载，可覆盖共享同名配置，开启动态刷新。
- 可审阅的[服务配置模板](../../config/nacos/payment-service.yaml)包含当前 Payment 非敏感运行参数。模板使用本地地址，其他环境必须调整。
- 加载入口为 [application.yml](../../paytech-payment-app/src/main/resources/application.yml) 的 `nacos` Profile，使用标准 `spring.config.import`；版本由项目 BOM 管理。参考 [Spring Cloud Alibaba 官方接入说明](https://sca.aliyun.com/docs/2025.x/user-guide/nacos/quick-start/)。

- **服务注册与发现（2026-10-14）：** Payment 依赖 `spring-cloud-starter-alibaba-nacos-discovery`，启动即按 `spring.cloud.nacos.discovery.server-addr`（`NACOS_SERVER_ADDR`，默认 `127.0.0.1:8848`）与 `namespace`（`NACOS_NAMESPACE`，默认 `mor-local`）注册 `payment-service`（原 `payment-orchestration-service`），并经 Nacos 发现 channel-connector（`channel-connector-service`）、Risk（`risk-service`）与 Vault（`vault-service`）。Platform 执行卡直付时回调 Payment 读卡接口，依赖该注册（Platform 侧发现名需改为 `payment-service`）。与配置中心的 `nacos` Profile 相互独立；自动测试关闭 Nacos 发现与注册，外部服务由 simple discovery 指向测试 Mock HTTP 服务。

本地默认不从 Nacos 加载配置，使用 `application.yml` 中的配置；启用方式：

```bash
export SPRING_PROFILES_ACTIVE=nacos
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=mor-local
export NACOS_GROUP=DEFAULT_GROUP
"$JAVA_HOME/bin/java" -jar paytech-payment-app/target/paytech-payment-app-1.0.0-SNAPSHOT-exec.jar
```

服务配置使用非 optional 导入；启用此 Profile 前应先由部署人员维护对应 Data ID。Nacos 认证按环境通过 `NACOS_USERNAME`、`NACOS_PASSWORD` 注入。仓库只提供模板和加载代码，不会自动向 Nacos 发布配置。

## 3. 基础设施配置

| 配置前缀 | 所属组件 | 当前配置内容 | 修改方式 |
|---|---|---|---|
| `server.*` | Spring Boot | 地址、端口、Context Path | 修改后重启 |
| `spring.datasource.*` | Spring Boot / Hikari | 数据源地址、账号、连接和校验超时 | 修改后重启 |
| `spring.data.redis.*` | Spring Boot / Lettuce | Redis 地址、端口、连接和命令超时 | 修改后重启 |
| `spring.rabbitmq.*` | Spring Boot / Spring AMQP | 地址（`PAYMENT_RABBITMQ_HOST`，默认 `127.0.0.1`）、端口（`PAYMENT_RABBITMQ_PORT`，默认 `5672`）、vhost（`PAYMENT_RABBITMQ_VHOST`，默认 `/mor-local`）、连接超时 2s；`publisher-confirm-type: correlated`、`publisher-returns: true`、`template.mandatory: true` 为固定值 | 修改后重启。PaymentSucceeded 双写：发布到 Durable Topic `topic_payment_result`（路由键 `payment.succeeded.v1`，Exchange 由 Payment 声明），再经 `paytech-messaging` 发布到 Durable Fanout `payment.events`（启动时声明）；各自等待 Broker 确认 10 秒，未确认或被退回按发布失败重试 |
| `messaging.*` | Foundation Messaging | `producer.service: payment`；`producer.confirm-timeout` 使用组件默认 10s | 启动时声明 `payment.events` 并创建发布端，修改需重启 |
| `internal-auth.*`、`paytech.instance-id` | Foundation Internal Auth | `mode: "OFF"`（`PAYMENT_INTERNAL_AUTH_MODE`，YAML 必须带引号）；实例 ID `PAYTECH_INSTANCE_ID`，默认 `main` | 非 OFF 时出站 channel-connector / Risk 客户端附加内部 Token，缺少 Token 来源拒绝启动（D26） |
| `compensation.*` | Foundation Compensation Event | `enabled: true`；其余 instance-id、lease-duration、scanner-batch-size、coordinator、handler 使用组件默认值 | 启动时构造执行器，修改需重启。`PAYMENT_SUCCEEDED` Handler 每次 20 秒、最多 12 次、5 秒起指数退避至 30 分钟；失败到上限保留 FAILED 记录待人工处理 |
| `xxl.job.*` | Foundation XXL-Job Starter | 未配置，执行器默认关闭 | 补偿任务的兜底扫描需要部署环境另行开启并配置调度中心；关闭时依靠事务提交后唤醒，进程在提交后、发布前退出的任务需人工或开启 XXL-Job 后恢复 |
| `redis.coordination.*` | Foundation Redis Starter | 资源锁开关、等待时间、租约 | 修改后重启，不承诺公共组件支持热更新 |
| `vault.client.*` | Vault Java Client | caller-service `payment-service`、service-id、connect-timeout、read-timeout | 卡 DEK 的 Protect / Reveal（第 12 步随卡域迁到 card），客户端配置修改按重启生效处理 |
| `mybatis.*` | MyBatis Starter | Mapper 扫描位置 | 应用固定配置 |
| `spring.sql.init.*`、`spring.jmx.*` | Spring Boot | 禁止自动建表、关闭 JMX | 应用固定配置 |
| `logging.*`、`web.logging.*` | 日志 / Foundation MVC | Request ID、仅记录元信息 | 应用固定配置，禁止打开卡请求 Body 日志 |
| `management.*` | Actuator | 管理端口 19103；health/info/prometheus 白名单 | 应用固定配置 |

Nacos 内容进入 Spring Environment 不代表所有组件都能动态重建。业务配置对象支持重新绑定，Processor/Service 直接读取对象；数据库、Redis、监听器、ID 生成器等按重启生效处理。

`redis.coordination.resource-lock.*` 属于 Foundation 的 `@ResourceLock` 配置；当前 `AbstractLockProcessor` 自己的锁调用不使用这组等待/租约参数，具体区别见[写接口规范](processor-conventions.md#62-锁定业务根)。

## 4. 实例和敏感配置

以下值继续由进程环境提供，不放进 Nacos 模板：

| 环境变量 | 用途 |
|---|---|
| `MOR_PAYMENT_DB_PASSWORD` | 数据库密码 |
| `MOR_REDIS_PASSWORD` | Redis 密码 |
| `PAYMENT_RABBITMQ_USERNAME`、`PAYMENT_RABBITMQ_PASSWORD` | RabbitMQ 账号密码（缺省为空） |
| `NACOS_USERNAME`、`NACOS_PASSWORD` | Nacos 认证 |
| `PAYMENT_SNOWFLAKE_REGION_ID`、`PAYMENT_SNOWFLAKE_WORKER_ID` | 实例 ID 坐标，防止多个实例共用一个节点编号 |

`MOR_PAYMENT_DB_URL`、`MOR_PAYMENT_DB_USERNAME`、`MOR_REDIS_HOST`、`MOR_REDIS_PORT`、`PAYMENT_INSTRUMENT_PREPARATION_WINDOW`、`PAYMENT_CARD_FINGERPRINT_KEY` 继续作为本地配置的环境变量入口。启用 Nacos 后，由导入配置中的同名属性覆盖这些本地默认值；不要同时把它们作为两个运行配置源维护。

应用不接入真实 KMS、Channel 或 Card 存储时，不预留其凭据和参数。

Channel 账号配置只能在数据库事务结束后通过 VaultClient 读取。环境须提供 `vault-service` 的私网服务发现地址（例如 `spring.cloud.discovery.client.simple.instances.vault-service[0].uri`）并维护获准 Allowlist；不在代码中硬编码真实环境地址。策略片段见[实施合同](../../../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md#4-vault-账号配置)。
