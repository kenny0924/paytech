# channel-connector

- spring.application.name：`channel-connector-service`
- 端口：18110；管理端口 19110；无 context path
- 数据库：`mor_channel_connector` / `channel_connector_app`
- 来源现有工程：`payment-orchestration-service`（`paytech-payment-channel` 与 web 中渠道侧代码，重构第 05 步拆出）

channel-connector 只拥有支付渠道（D06）：渠道身份、技术账号、MID、能力配置与按 OE 的候选查询，在冻结坐标上经 Platform 标准 API 执行渠道操作并把 Platform 输出映射为已映射结论，以及预计渠道成本配置（方案 / 版本 / 范围 / 组成的 Maker-Checker、到期切换与命中）。Payment 冻结三元组、推进支付状态；channel-connector 绝不重选三元组。开发规范与写接口自查沿用 [Payment 入口](../payment/AGENTS.md)列出的公共文档。

## 模块

| 模块 | 职责 |
|---|---|
| `paytech-channel-connector-api` | `ChannelCandidateQueryApi`、`ChannelOperationApi`、`cost.ChannelCostPlanApi`、`cost.ChannelCostResolutionApi` 的 `@HttpExchange` 合同、DTO、稳定代码枚举（`ChannelOperation`、`ChannelOperationOutcome`、`ExecutionPurpose`、`ChannelPaymentStatus`）与能力代码常量 |
| `paytech-channel-connector-core` | 五表与渠道成本四表 DataObject / Mapper / Repository、渠道成本 Maker-Checker / 命中 / 到期切换 Processor、能力条件匹配与逐代码解析（只在此一处解析 `ACTION.*`、`AMOUNT.*`）、冻结坐标与 OE 一致性校验、Platform 标准 API 客户端与 PAY 输出映射、执行配置读取（Vault Reveal，无生产调用方） |
| `paytech-channel-connector-app` | 启动类、Controller、数据源 / MyBatis 扫描与渠道成本 TypeHandler、Snowflake 生成器、Platform 客户端装配、XXL-Job 入口、配置 |
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

测试前先人工审查 [测试 DDL](paytech-channel-connector-test/src/test/resources/init/ddl.sql)，用 root 应用到本机 `local-environment` MySQL（创建 `mor_channel_connector`、`channel_connector_app` 与 17 张表；已有 13 表的库只需执行 [2026-10-19 增量](docs/database/sql/changes/2026-10-19-channel-cost-tables.sql)）。测试同时连接本机 `local-environment` Redis（资源锁，前缀 `channel-connector-mock`）。PDManer 规范模型见 [channel-connector.pdma.json](docs/database/channel-connector.pdma.json)（由 `scripts/pdmaner/generate-channel-connector-model.mjs` 生成），物理决策随 Payment 维护于 [PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md](../payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)。Nacos 模板见 [channel-connector-service.yaml](config/nacos/channel-connector-service.yaml)。

## 渠道成本配置（2026-10-19 自 billing 迁入）

需求：[billing 计费事实模型重构与渠道成本迁出](../../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/technique.md) 第 2 节。本服务保存预计渠道成本规则并提供命中；"组成 → 金额"由 billing 计算，本库不算金额、不按币种精度归整。

| 合同 | 路径 | 说明 |
|---|---|---|
| `ChannelCostPlanApi.createChannelCostPlan` | `POST /channel-connector/v1/channel-cost-plans` | 每个 Channel 一个方案，DRAFT，201 |
| `ChannelCostPlanApi.createChannelCostVersion` | `POST .../{planCode}/versions` | 完整范围与组成一次写入为 DRAFT 并冻结 `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1` 内容摘要；范围 MID 须在本库 `t_channel_mid` 存在、属于方案 Channel 且 OE 等于范围 `entityNo` |
| `submit` / `approve` / `reject` | `POST .../{planCode}/versions/{costVer}/submit\|approve\|reject` | Maker 提交；Checker ≠ Maker 并确认同一摘要；批准时已到生效时间即切换当前版本，否则 APPROVED |
| `getChannelCostPlan` / `getChannelCostVersion` | `GET .../{planCode}`、`GET .../{planCode}/versions/{costVer}` | 方案与版本摘要（版本号降序）、版本详情（范围按 scopeNo、组成按 componentCode 升序） |
| `ChannelCostResolutionApi.resolveChannelCost` | `POST /channel-connector/v1/channel-costs/resolutions` | 只读：ACTIVE 方案 → 覆盖 `asOfTime` 的唯一 ACTIVE / INACTIVE 版本（`[effective_from, effective_to)`）→ 六维精确命中唯一范围 → 组成；未命中 400 `CHANNEL_COST_NOT_RESOLVED`，多重命中 400 `CHANNEL_COST_AMBIGUOUS` |

费率与金额输出为去掉末尾零的普通十进制字符串；金额小数位不超过 8 位由入口格式保证，币种只校验格式（本库无币种表）。

**写入并发：** 五个写入口与到期切换共用 Redis 资源锁 `channel-cost-plan:{planCode}`（`ChannelConnectorLocks`，LOCK 模式），事务内先锁方案行再锁版本行，状态以原状态条件更新、当前指针以方案 `version` CAS。

**到期切换 Job：** `ChannelCostVersionActivationJobHandler`（XXL Handler `channelCostVersionActivation`，建议 Cron `0 */5 * * * ?`）每轮读取至多 200 个已到生效时间的 APPROVED 版本，按方案逐个加锁、短事务切换（新版本 ACTIVE、原当前版本 INACTIVE 且 `effective_to` = 新版本生效时间、方案指针 CAS）；重复执行幂等。生效时间不晚于当前版本的到期版本计为失败并保持 APPROVED，Job 以 `handleFail` 标记失败等待人工处理。

**依赖与配置键：**

| 依赖 | 用途 | 配置键 |
|---|---|---|
| `paytech-redis-starter` | `AbstractLockProcessor` 资源锁 | `spring.data.redis.*`（`CHANNEL_CONNECTOR_REDIS_HOST/PORT/PASSWORD`）、`redis.coordination.enabled=true`、`redis.key-prefix`（缺省 `spring.application.name`） |
| `paytech-snowflake-id` | 渠道成本主键 | `channel-connector.identifier.region-id` / `worker-id`（`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID` / `_WORKER_ID`，必填无默认） |
| `paytech-compensation-event`（含 `paytech-xxl-job-starter`） | XXL 执行器承载到期切换 Job；本服务暂无补偿事件类型（`compensation-event-catalog.yaml` 为空） | `compensation.enabled=true`、`compensation.instance-id`（`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`）、`xxl.job.*`（调度中心地址与执行器参数由部署注入） |
