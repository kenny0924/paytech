# channel-connector

- spring.application.name：`channel-connector-service`
- 端口：18110；管理端口 19110；无 context path
- 数据库：`mor_channel_connector` / `channel_connector_app`
- 来源现有工程：`payment-orchestration-service`（`paytech-payment-channel` 与 web 中渠道侧代码，重构第 05 步拆出）

channel-connector 只拥有支付渠道（D06）：渠道身份、技术账号、MID、能力配置与按 OE 的候选查询，以及在冻结坐标上经 Platform 标准 API 执行渠道操作并把 Platform 输出映射为已映射结论。Payment 冻结三元组、推进支付状态；channel-connector 绝不重选三元组。开发规范与写接口自查沿用 [Payment 入口](../payment/AGENTS.md)列出的公共文档。

## 模块

| 模块 | 职责 |
|---|---|
| `paytech-channel-connector-api` | `ChannelCandidateQueryApi`、`ChannelOperationApi` 的 `@HttpExchange` 合同、DTO、稳定代码枚举（`ChannelOperation`、`ChannelOperationOutcome`、`ExecutionPurpose`、`ChannelPaymentStatus`）与能力代码常量 |
| `paytech-channel-connector-core` | 五表 DataObject / Mapper / Repository、能力条件匹配与逐代码解析（只在此一处解析 `ACTION.*`、`AMOUNT.*`）、冻结坐标与 OE 一致性校验、Platform 标准 API 客户端与 PAY 输出映射、执行配置读取（Vault Reveal，无生产调用方） |
| `paytech-channel-connector-app` | 启动类、Controller、数据源 / MyBatis 扫描、Platform 客户端装配、配置 |
| `paytech-channel-connector-test` | 隔离测试（真实 `mor_channel_connector`、Platform / Vault DTO Mock）与架构门禁（R1–R12、渠道表归属） |

## HTTP 合同

| 合同 | 路径 | 说明 |
|---|---|---|
| `ChannelCandidateQueryApi.queryMidCandidates` | `POST /channel-connector/v1/mid-candidates` | 入参 `entityNo` + `criteria{paymentMethodCode, schemeCode?, countryCode, currency, mccCode}`；返回启用候选 `{channelId, channelAccountId, midId, supportedOperations[], minAmount?, maxAmount?}`（ID、金额为十进制字符串）。空候选为 200 空列表；配置损坏 500；校验失败 422 |
| `ChannelOperationApi.execute` | `POST /channel-connector/v1/operations` | 入参冻结三元组 + `entityNo` + `operation` + `purpose` + `criteria?` + `amount?` + 标准 API `input`（支付工具句柄、尝试标识、回跳地址等，不含卡值与账号材料）；返回 `{outcome, paymentStatus?, redirectUrl?, channelTransactionRef?, channelStatus?, failureCode?, failureMessage?, rawOutputKeys[]}`。冻结坐标不可用、OE 不一致或账号未配置为 NOT_SENT；Platform 保证未发出的 Problem 为 NOT_SENT，其余失败为 ERROR |

标准 API 编码取渠道操作代码（PAY → `PAY`）；Platform 接入方编码沿用渠道编码（如 `PAYERMAX`）。执行配置读取（`GetChannelExecutionConfigProcessor`，返回渠道账号明文凭据）没有生产调用方，本步不发布 HTTP 入口。

## 构建与测试

在 `paytech/` 下使用 JDK 25：

```bash
./mvnw clean package -Dmaven.test.skip=true -pl channel-connector/paytech-channel-connector-app -am
./mvnw -pl channel-connector/paytech-channel-connector-test -am clean verify
```

测试前先人工审查 [测试 DDL](paytech-channel-connector-test/src/test/resources/init/ddl.sql)，用 root 应用到本机 `local-environment` MySQL（创建 `mor_channel_connector`、`channel_connector_app` 与 13 张表）。PDManer 规范模型见 [channel-connector.pdma.json](docs/database/channel-connector.pdma.json)（由 `scripts/pdmaner/generate-channel-connector-model.mjs` 生成），物理决策随 Payment 维护于 [PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md](../payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)。Nacos 模板见 [channel-connector-service.yaml](config/nacos/channel-connector-service.yaml)。
