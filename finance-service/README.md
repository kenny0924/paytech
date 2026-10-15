# Finance Spring Boot 工程

六模块 Maven 工程已初始化。Accounting 第一批账务内核已实现：会计基础资料（财务主体、账簿、统一账户、财务日历与期间）、
单账簿 Journal 过账内核，以及人工会计调整 Maker-Checker；Client/Partner 余额查询已迁移到新 Schema 的 entityNo 身份。
交易经济、AUTO 记账、成熟、对账、出款、税务与关账仍未实现。需求与设计见
[账务内核需求](../requirements/20261015/finance-账务内核/requirement.md)、[技术方案](../requirements/20261015/finance-账务内核/technique.md)，
余额语义见 [余额查询接口](docs/balance-query-api.md)。

## 模块

| 目录                         | 职责                                |
|----------------------------|-----------------------------------|
| paytech-finance-api        | 内部 HTTP 契约：会计基础资料、人工调整、余额查询 |
| paytech-finance-accounting | 账簿/账户/期间、Journal 过账内核、人工调整、余额查询；未来承载经济计算与关账 |
| paytech-finance-treasury   | Provider/银行对账、手工出款                |
| paytech-finance-tax        | 税务计算、单据、申报与缴税                     |
| paytech-finance-web        | 唯一 Spring Boot 启动模块及未来用例编排        |
| paytech-finance-test       | 自动启动、HTTP 与模块边界测试                 |

Web 依赖 API 和三个业务 JAR；三个业务模块之间没有直接 Maven 依赖，也不访问其他模块表。未来只有稳定、可复用的
Owner 业务能力满足窄接口、Domain/Application 模型、长期单向和无环条件时，才允许业务模块单向依赖；否则由 Web Processor 编排。
完整规则见[业务模块协作决策](../agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md)。

Accounting 内部按 `core`（枚举、常量、DataObject/Mapper）、`domain`（Context、View、纯规则）、`domainservice`（Repository、
Domain Service、Validator）与 `service/processor`（写入口）组织。写接口由 Web Controller 委托 Accounting Processor；余额与调整详情查询
直接委托 Domain Service。人工调整批准由 Processor 开启一个 TransactionTemplate，依次完成调整状态、人工指令、`JournalPostingService`
过账与调整 POSTED；过账锁顺序固定为期间共享锁 → 账簿序号行排他锁 → 账户主键升序排他锁。复核与期间创建另用 Foundation
`AbstractLockProcessor` 的 Redis 资源锁，数据库状态 CAS 与行锁仍是最终防线。

## 构建与运行

使用 JDK 25、Maven 3.9.16，继承已发布的 com.paytech:paytech-parent:1.0.0-SNAPSHOT。Spring Boot 及 Foundation 版本由统一 BOM
管理。开发者 Maven Settings 需配置项目 Nexus maven-public，凭据不进入工程；不需要同级 Build/Foundation 源码。

```sh
./mvnw clean verify
java -jar paytech-finance-web/target/paytech-finance-web-1.0.0-SNAPSHOT-exec.jar
```

运行前需在 MySQL `mor_finance` 库人工依次执行 `docs/database/sql/finance-schema.sql` 与 `finance-initial-data.sql`。默认连接
`127.0.0.1:13306/mor_finance`，生产密码只通过 `FINANCE_DB_PASSWORD` 注入；可用 `FINANCE_DB_URL`、`FINANCE_DB_USERNAME` 覆盖连接参数。
Redis 默认 `127.0.0.1:16379`，密码通过 `FINANCE_REDIS_PASSWORD` 注入；Snowflake 标识必须通过 `FINANCE_SNOWFLAKE_REGION_ID` 与
`FINANCE_SNOWFLAKE_WORKER_ID` 提供，缺失时启动失败。

默认仅监听 127.0.0.1:18105，可用 SERVER_PORT/SERVER_ADDRESS 覆盖；不创建 Gateway 路由、不部署到任何环境。

- /actuator/health
- /actuator/health/liveness
- /actuator/health/readiness
- /actuator/info
- /actuator/prometheus

健康端点 UP 表示应用及其当前数据库依赖可用，不代表尚未实现的记账、税务或出款能力可用。Actuator 不披露健康组件详情，不开放
env/beans/configprops/shutdown 或端点发现。

默认 auto 测试复用正式启动类、`mock` Profile 与随机 Loopback 端口，连接仓库 local-environment 的 `mor_finance` MySQL 与 Redis
（Key 前缀 `finance-mock`）；不连接 Nacos、MQ 或其他业务系统，不使用 Mockito，也不自动创建/迁移表。每例 BeforeEach 只检查空基线：
发现非测试数据会失败而不是删除；AfterEach 清空 Accounting 16 张表的测试写入并保留初始化 DML 固定行。因此 `mor_finance` 应视为
本机测试专用库，本地运行应用产生的数据需先自行处理。测试环境准备见 `paytech-finance-test/src/test/resources/init/ddl.sql`。
manual-test 为继承的显式人工测试 Profile，当前没有人工测试。

Web 同时输出普通 JAR（供 test 依赖）与可执行 -exec.jar；test 不 install/deploy。共享 Foundation MVC Starter 提供日志及错误处理，不复制
Filter/Advice。接入任何敏感业务 API 前，必须按专项契约配置并验证共享 metadata-only 日志边界。

## 当前不包含

无 Nacos/远端 HTTP 客户端、无 MQ/XXL-Job/Compensation，也没有统一身份和 Owner/OE 资源授权。全部接口当前只能作为内部开发接口：
人工调整的 Maker/Checker 由请求传入，OE 只校验 entityNo 格式而不向 CCT 核对，均不能直接发布给 Client、Partner 或 Operations。
父工程 Enforcer 继续阻止未批准基础设施（本期已批准 Redis 资源锁）；后续业务切片必须同步更新依赖边界和真实链路测试。

不提供期间关账/重开、账簿或账户停用、账簿/账户列表与账本流水查询。PDManer 未改动；代码不代表旧 Finance 补偿契约、税款事后登记或
全资源锁矩阵已完成收口。
