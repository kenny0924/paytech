# Payment 配置清单

配置编码规则统一见[写接口开发规范第 16 节](processor-conventions.md#16-配置与-nacos)。本文只维护具体配置项、加载方式及生效范围。

## 1. 自有配置

| 配置项 | 配置类 | 默认值 / 约束 | 生效范围 |
|---|---|---|---|
| `payment.execution.instrument-preparation-window` | [PaymentExecutionProperties](../../paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/configuration/PaymentExecutionProperties.java) | `PT0S`；Duration，例如 `PT5M`；零值或负值禁止新工具申请 | 每次新申请读取一次；不修改原 Token 到期时间，不阻止原提交重放 |
| `payment.channel.capability-definitions` | [ChannelProperties](../../paytech-payment-channel/src/main/java/com/paytech/payment/channel/core/configuration/ChannelProperties.java) | 非空目录；每项 value-type 为 BOOLEAN / DECIMAL / CODE，CODE 提供 allowed-codes | 约束五表查询中的开放能力代码；初始目录见服务模板，类型变化须同步审查已有配置 |
| `payment.card.mock-enabled` | [PaymentCardProperties](../../paytech-payment-card/src/main/java/com/paytech/payment/card/core/configuration/PaymentCardProperties.java) | `false`；仅允许本地合成卡 | Card 每次调用读取；关闭后合成卡申请、核对均不可用 |
| `payment.card.mock-pans` | 同上 | `[4111111111111111]`；只能是渠道公布的测试卡号 | 仅 Mock 开启时生效；合成材料只在进程内存，CVV 领取一次即清除 |
| `payment.outbound.platform-context-path` / `platform-read-timeout` | [PaymentOutboundProperties](../../paytech-payment-web/src/main/java/com/paytech/payment/configuration/PaymentOutboundProperties.java) | `/platform-support`、`30s`（须大于渠道流程预算） | 启动时创建客户端，修改需重启；超时按渠道结果未知处理 |
| `payment.outbound.risk-context-path` / `risk-read-timeout` | 同上 | 空串、`3s` | 启动时创建客户端，修改需重启；Risk 合同为 Payment 草案，不可用时风控失败关闭 |
| `payment.identifier.region-id` | [PaymentIdentifierProperties](../../paytech-payment-web/src/main/java/com/paytech/payment/configuration/PaymentIdentifierProperties.java) | 必填，0～31 | 创建 ID 生成器时读取，修改需重启 |
| `payment.identifier.worker-id` | 同上 | 必填，0～31；同一区域每个实例唯一 | 由实例部署环境注入，修改需重启 |

工具有效期从 `payment.instrument.preparation-window` 移至 `payment.execution.instrument-preparation-window`。旧配置键不再读取；现有环境变量 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW` 保留为本地默认值来源。其余已有配置键不变。

## 2. Nacos 组织与加载

- Namespace 按环境隔离，本地为 `mor-local`；Group 默认 `DEFAULT_GROUP`。
- 可选共享 Data ID：`mor-common.yaml`，先加载，关闭动态刷新。本仓库不维护或发布共享内容。
- Payment Data ID：`payment-orchestration-service.yaml`，后加载，可覆盖共享同名配置，开启动态刷新。
- 可审阅的[服务配置模板](../../config/nacos/payment-orchestration-service.yaml)包含当前 Payment 非敏感运行参数。模板使用本地地址，其他环境必须调整。
- 加载入口为 [application.yml](../../paytech-payment-web/src/main/resources/application.yml) 的 `nacos` Profile，使用标准 `spring.config.import`；版本由项目 BOM 管理。参考 [Spring Cloud Alibaba 官方接入说明](https://sca.aliyun.com/docs/2025.x/user-guide/nacos/quick-start/)。

本地默认不连接 Nacos，使用 `application.yml` 中的配置；启用方式：

```bash
export SPRING_PROFILES_ACTIVE=nacos
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=mor-local
export NACOS_GROUP=DEFAULT_GROUP
"$JAVA_HOME/bin/java" -jar paytech-payment-web/target/paytech-payment-web-1.0.0-SNAPSHOT-exec.jar
```

服务配置使用非 optional 导入；启用此 Profile 前应先由部署人员维护对应 Data ID。Nacos 认证按环境通过 `NACOS_USERNAME`、`NACOS_PASSWORD` 注入。仓库只提供模板和加载代码，不会自动向 Nacos 发布配置。

## 3. 基础设施配置

| 配置前缀 | 所属组件 | 当前配置内容 | 修改方式 |
|---|---|---|---|
| `server.*` | Spring Boot | 地址、端口、Context Path | 修改后重启 |
| `spring.datasource.*` | Spring Boot / Hikari | 数据源地址、账号、连接和校验超时 | 修改后重启 |
| `spring.data.redis.*` | Spring Boot / Lettuce | Redis 地址、端口、连接和命令超时 | 修改后重启 |
| `redis.coordination.*` | Foundation Redis Starter | 资源锁开关、等待时间、租约 | 修改后重启，不承诺公共组件支持热更新 |
| `vault.client.*` | Vault Java Client | caller-service、service-id、connect-timeout、read-timeout | 通过私有 HTTP 读取账号 JSON，客户端配置修改按重启生效处理 |
| `mybatis.*` | MyBatis Starter | Mapper 扫描位置 | 应用固定配置 |
| `spring.sql.init.*`、`spring.jmx.*` | Spring Boot | 禁止自动建表、关闭 JMX | 应用固定配置 |
| `logging.*`、`web.logging.*` | 日志 / Foundation MVC | Request ID、仅记录元信息 | 应用固定配置，禁止打开卡请求 Body 日志 |
| `management.*` | Actuator | health/info/prometheus 白名单 | 应用固定配置 |

Nacos 内容进入 Spring Environment 不代表所有组件都能动态重建。业务配置对象支持重新绑定，Processor/Service 直接读取对象；数据库、Redis、监听器、ID 生成器等按重启生效处理。

`redis.coordination.resource-lock.*` 属于 Foundation 的 `@ResourceLock` 配置；当前 `AbstractLockProcessor` 自己的锁调用不使用这组等待/租约参数，具体区别见[写接口规范](processor-conventions.md#62-锁定业务根)。

## 4. 实例和敏感配置

以下值继续由进程环境提供，不放进 Nacos 模板：

| 环境变量 | 用途 |
|---|---|
| `MOR_PAYMENT_DB_PASSWORD` | 数据库密码 |
| `MOR_REDIS_PASSWORD` | Redis 密码 |
| `NACOS_USERNAME`、`NACOS_PASSWORD` | Nacos 认证 |
| `PAYMENT_SNOWFLAKE_REGION_ID`、`PAYMENT_SNOWFLAKE_WORKER_ID` | 实例 ID 坐标，防止多个实例共用一个节点编号 |

`MOR_PAYMENT_DB_URL`、`MOR_PAYMENT_DB_USERNAME`、`MOR_REDIS_HOST`、`MOR_REDIS_PORT`、`PAYMENT_INSTRUMENT_PREPARATION_WINDOW`、`PAYMENT_CARD_MOCK_ENABLED` 继续作为本地配置的环境变量入口。启用 Nacos 后，由导入配置中的同名属性覆盖这些本地默认值；不要同时把它们作为两个运行配置源维护。

应用不接入真实 KMS、Channel 或 Card 存储时，不预留其凭据和参数。

Channel 账号配置只能在数据库事务结束后通过 VaultClient 读取。环境须提供 `vault-service` 的私网服务发现地址（例如 `spring.cloud.discovery.client.simple.instances.vault-service[0].uri`）并维护获准 Allowlist；不在代码中硬编码真实环境地址。策略片段见[实施合同](../../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md#4-vault-账号配置)。
