# CreateCheckout 测试技术说明

## 测试组织

测试代码位于 `commerce-service/paytech-commerce-test/src/test`。

| 部分 | 职责 |
|---|---|
| `checkout/create/CreateCheckoutSuccessTest` | 创建成功、金额计算、合法边界与扩展字段 |
| `checkout/create/CreateCheckoutDuplicateTest` | 业务订单号防重、Client 隔离及并发竞争 |
| `checkout/create/CreateCheckoutValidationTest` | 请求绑定、字段/金额/期限校验及提前拒绝 |
| `checkout/create/CreateCheckoutPrecheckTest` | CCT 拒绝、协议错误、超时、重新提交和预检期间过期 |
| `test/base/checkout/AbstractCreateCheckoutApiTest` | 公共路径、调用与可复用辅助；不包含测试或生命周期方法 |
| `CheckoutCreationTransactionTest` | 真实公开 Service 的末步回滚及 REQUIRED 事务传播 |
| `MockTestBase` | 全局基线、统一清理、串行共享环境 |
| `CheckoutTestApplication` | 显式装配创建链路的真实生产类及公共自动配置 |
| `test/builder/checkout` | 请求及事务 Context 构造，无 I/O、无负例修复 |
| `test/builder/external/CctPrecheckResponseBuilder` | 预检通过的独立默认模板，用例按字段覆盖差异，保留非法组合和显式 null |
| `CheckoutDataMocker` / `CheckoutCreationTestMapper` | 前置事实 INSERT、按自有资源清理及只读检查 SQL |
| `CctCheckoutMocker` / `MockCheckoutEligibilityApi` | 配置预检 DTO、故障和调用记录；公共 MockHttpServer 承载 API 实现，MVC 继承正式接口映射 |
| `test/checker/checkout` | 独立期望值下的响应、五类创建事实和零副作用检查 |

原有其他入口暂沿用 `HttpTestBase` 和 `TestApplication`；它们与新切片复用同一个真实 HTTP 测试客户端配置。新切片不替换内部 Bean，也不扫描无关 Refund/补偿 Handler。四个 HTTP 场景类共用同一测试应用和接口基类，参数源留在使用它的类中；不按测试类重复创建 Spring 配置。

## 生命周期与边界

`MockTestBase.setUpGlobalMocks` 每例检查环境干净、重置固定 UTC Clock 和事务观察。各具体场景类的 `setUpClassMocks` 只配置本类所需的 CCT 预检 DTO；具体方法覆盖故障或准备既有订单。事务测试类只构造自己的 Context，不通过被测命令准备数据。

`tearDownGlobalMocks` 先等待本例 Mock HTTP 请求结束，再检查意外调用，聚合执行数据库、DTO、调用记录和 Clock 清理。数据库清理按进程隔离的两个 Client ID 及显式预留 Order ID 限定，绝不清空共享开发表。清理失败标记环境不可复用。超时请求无法结束时保留其可能访问的数据并停止复用，避免清理后回写。

TestMapper 的缺列检查只读；所有 DDL 均由人工授权执行。CCT 事务观察使用测试侧 HTTP 拦截器，不在生产类增加探针。CCT Mock 无默认成功，未知方法/路径/参数和未配置 DTO 都记录为意外调用。

## 执行

先按[本地环境规范](../../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md)准备仓库 MySQL 和当前 Commerce Schema。在 `commerce-service` 使用 JDK 25：

```bash
./mvnw -Pcheckout-create-tests clean test
./mvnw -Pcheckout-create-tests '-Dtest=CreateCheckout*Test' test
./mvnw -Pcheckout-create-tests -Dtest=CreateCheckoutSuccessTest#test_createCheckout test
./mvnw -Pcheckout-create-tests -Dtest=CheckoutCreationTransactionTest test
```

该显式 Profile 限定新测试源码及辅助类，供旧测试类型迁移期间独立运行。默认 auto/manual 集合和全量覆盖率门槛不变；Profile 的成功不代表全量 `verify` 通过。

## Mock 启动配置

`MockTestBase` 自动启用 `mock` Spring Profile。[application-mock.yml](../../../commerce-service/paytech-commerce-test/src/test/resources/application-mock.yml) 配齐当前切片所需的固定配置，包括本地数据库默认凭据、随机应用端口、Checkout Origin、ID 节点、CCT HTTP 策略和服务发现绑定、日志/管理端点、关闭 Nacos 与 XXL-Job 外部调度。旧请求幂等组件在该切片关闭；不改变生产业务分支。

该 YAML 同时作为配置字段清单，完整覆盖 Commerce 两个 Properties 类、Foundation 补偿/XXL-Job/Web 日志配置及旧幂等开关。可选/默认/关闭字段仍显式展示，注释给出来源、单位、默认值、Mock 取值和启用条件；当前生产 YAML 中采用的框架键也纳入核对。不会为展示字段而开启 Nacos 或 XXL-Job。

CCT Mock 服务实际端口由 `DynamicPropertySource` 注入 `commerce.test.cct.base-url`，YAML 引用它配置 SimpleDiscovery，不在 Java 中重复服务绑定。DTO 响应仍逐例准备。标准本地配置无需设置生产环境变量；`application-auto.yml` 仅保留给未迁移旧测试。数据库缺列仍按前置检查失败，不通过配置自动迁移。

## 数据库前置

本需求不设计新表。当前 Mock 初始化语句汇总至测试资源 [init/ddl.sql](../../../commerce-service/paytech-commerce-test/src/test/resources/init/ddl.sql)，参考 Transaction 测试项目的集中 SQL 组织方式，由本地管理员手工创建 `mor_commerce`、`commerce_app` 并授予本库 DML 权限，账号与 Mock YAML 默认值一致；已有库和用户不删除、不重置密码。表定义逐字复用 Commerce 正式 Schema，包含创建写入的 5 张表，以及检查零副作用的 Payment Projection、Order Context、Consumer Contract 三张表；后续按 Mock 依赖扩充。建表部分仅用于空测试 Schema，不自动执行、不插入场景数据。

已有数据库缺少当前创建代码所需的两列时，仍使用已有唯一增量 SQL 来源，不复制迁移：

- [creation_mode 增量](../../20260916/commerce-订单查询合并Checkout/db/creation-mode.sql)：增加带 `HOSTED_CHECKOUT` 默认值的字段。
- [consumer_country_code 增量](../../../commerce-service/docs/requirements/20260916-create-checkout-business/ddl.sql)：增加历史行允许 NULL 的字段。

执行前核对目标为 `mor-local / 127.0.0.1:13306 / mor_commerce`，备份 `t_commerce_order` 结构并确认两列均不存在。脚本不可重复执行，不授权修改 `mor-test`，不变更订单金额或状态。执行与批准记录见交付记录。
