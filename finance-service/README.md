# Finance Spring Boot 工程

六模块 Maven 工程已初始化；首个 Client/Partner 余额查询切片已经实现 HTTP、Domain Service、Repository、MyBatis 与 MySQL 只读链路。
记账命令、税务和出款仍未实现。详见 [余额查询接口](docs/balance-query-api.md)。

## 模块

| 目录                         | 职责                                |
|----------------------------|-----------------------------------|
| paytech-finance-api        | 对外 HTTP 契约；Client/Partner 余额查询审核稿 |
| paytech-finance-accounting | 账户余额查询；未来承载经济计算、Journal 与关账       |
| paytech-finance-treasury   | Provider/银行对账、手工出款                |
| paytech-finance-tax        | 税务计算、单据、申报与缴税                     |
| paytech-finance-web        | 唯一 Spring Boot 启动模块及未来用例编排        |
| paytech-finance-test       | 自动启动、HTTP 与模块边界测试                 |

Web 依赖 API 和三个业务 JAR；当前启动切片的三个业务模块没有直接 Maven 依赖，也不访问其他模块表。未来只有稳定、可复用的
Owner 业务能力满足窄接口、Domain/Application 模型、长期单向和无环条件时，才允许业务模块单向依赖；否则由 Web Processor 编排。
完整规则见[业务模块协作决策](../agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md)。余额查询由 Web Controller 直接委托
Accounting 发布的 Domain Service 接口，Accounting 通过自己的 Repository/Mapper 读取其拥有的账簿、科目和账户表。跨模块写事务由
Web Processor 使用一个本地 TransactionTemplate 协调，模块不独立提交。

## 构建与运行

使用 JDK 25、Maven 3.9.16，继承已发布的 com.paytech:paytech-parent:1.0.0-SNAPSHOT。Spring Boot 及 Foundation 版本由统一 BOM
管理。开发者 Maven Settings 需配置项目 Nexus maven-public，凭据不进入工程；不需要同级 Build/Foundation 源码。

```sh
./mvnw clean verify
java -jar paytech-finance-web/target/paytech-finance-web-1.0.0-SNAPSHOT-exec.jar
```

运行前需在 MySQL `mor_finance` 库人工执行 `docs/database/sql/finance-balance-query-schema.sql`。默认连接
`127.0.0.1:13306/mor_finance`，生产密码只通过 `FINANCE_DB_PASSWORD` 注入；可用 `FINANCE_DB_URL`、`FINANCE_DB_USERNAME` 覆盖连接参数。

默认仅监听 127.0.0.1:18105，可用 SERVER_PORT/SERVER_ADDRESS 覆盖；不创建 Gateway 路由、不部署到任何环境。

- /actuator/health
- /actuator/health/liveness
- /actuator/health/readiness
- /actuator/info
- /actuator/prometheus

健康端点 UP 表示应用及其当前数据库依赖可用，不代表尚未实现的记账、税务或出款能力可用。Actuator 不披露健康组件详情，不开放
env/beans/configprops/shutdown 或端点发现。

已知共享依赖问题：当前 Foundation GlobalExceptionHandler 将未映射路径的 NoResourceFoundException 转为
500/INTERNAL_ERROR，而不是 404。测试显式记录此行为，并独立验证实际开放的管理端点只有 health/info/prometheus；这不是 Finance
业务路由。后续应在 Foundation 统一修正，本工程不复制局部 Advice，也不把该问题描述为已修复。

默认 auto 测试启动随机 Loopback 端口，并使用本机开发环境的真实 `mor_finance` MySQL；不连接 Nacos、Redis、MQ 或其他业务系统，
不使用 Mockito，也不自动创建/迁移表。测试使用保留 ID 范围准备和清理自己的数据。manual-test 为继承的显式人工测试 Profile，当前没有人工测试。

Web 同时输出普通 JAR（供 test 依赖）与可执行 -exec.jar；test 不 install/deploy。共享 Foundation MVC Starter 提供日志及错误处理，不复制
Filter/Advice。接入任何敏感业务 API 前，必须按专项契约配置并验证共享 metadata-only 日志边界。

## 当前不包含

无 Nacos/远端 HTTP 客户端、无 MQ/XXL-Job/Compensation，也没有统一身份和 Owner/OE 资源授权。余额路径当前只能作为内部开发接口，
不能直接发布给 Client 或 Partner。父工程 Enforcer 继续阻止未批准基础设施；后续业务切片必须同步更新依赖边界和真实链路测试。

PDManer 与原有数据库文档保持原样。代码初始化不代表旧 Finance 补偿契约、税款事后登记或全资源锁矩阵已完成收口。
