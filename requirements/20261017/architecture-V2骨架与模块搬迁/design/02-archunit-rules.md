# 02 · ArchUnit 边界规则集

仓库现无 ArchUnit 依赖，`paytech-dependencies` BOM 新增 `com.tngtech.archunit:archunit-junit5`（1.3.x）。规则实现放在 `paytech/tools/arch-rules`（普通 JAR，`com.paytech.arch`），每个服务的 `-test` 模块引入并以一个 `ArchitectureAutoTest` 执行全部规则；另有 Reactor 级 `paytech/tools/arch-rules-reactor-test` 对全部模块的依赖图做一次整体校验。任何规则失败即构建失败。

| 编号 | 规则 | 范围 | 对应决策 |
|---|---|---|---|
| R1 | `-core` 不得依赖任何其他服务的 `-core` 或 `-app`（Maven 依赖图与 import 双重检查） | 全部服务 | D09 |
| R2 | `-api` 只能依赖 `paytech-common-core`、spring-web、validation；不得依赖任何 core / Foundation starter | 全部服务 | 01 §2 |
| R3 | 领域层服务（client-master、commercial、consumer、card、risk-decision、compliance、channel-connector、billing、account、ledger、reconciliation、tax、fx-rate、data-service）的 `-core` 不得依赖流程层服务（acquiring、payment、payout、dispute、fx）的 `-api` | 领域层 | D08 |
| R4 | `-core` 中 Mapper XML 只能引用本服务数据库的表（表名白名单来自 03 文档的归属表；用正则扫描 `src/main/resources/mapper/**/*.xml` 的 `t_` 标识） | 全部服务 | D16、一库一 Owner |
| R5 | `-core` 不得声明 `@SpringBootApplication`、`@RestController`、`@RabbitListener` 之外的 Web / 启动注解；Controller 只在 `-app` | 全部服务 | 01 §2 |
| R6 | `-app` 中对其他服务 `-api` 接口的实现 Bean 只能来自 `HttpServiceClientFactory`（禁止本地实现类实现他服务 api 接口） | 全部 app | D09 修订 |
| R7 | 数据库事务边界内禁止调用外部客户端：标注 `@Transactional` 或使用 `TransactionTemplate` 的方法所在类，不得直接注入任何 `-api` HTTP 接口、`VaultClient`、`RabbitTemplate`（已有 Payment 的 `VaultTransactionObserver` 运行期检查继续保留） | 全部 core | 事务公理 |
| R8 | 只有 `-app` 可以依赖 `paytech-messaging` 的发布端实现与 `-core` 的事件 Handler 注册；`-core` 只依赖 `paytech-messaging-api`（信封与发布端口接口） | 全部 | 08 |
| R9 | DataObject（`core/dal/dataobject`）不得出现在 `-api` 中，也不得被其他服务引用 | 全部 | 现行 DataObject 边界 |
| R10 | 枚举常量之间保留一个空行（现行规范，静态检查以文本规则实现） | 全部 | DEVELOPMENT_CONVENTIONS |
| R11 | 卡域：除 `paytech-card-core` 与 `paytech-card-app` 外，任何模块的 Mapper XML 不得引用 `t_payment_card*`、`t_payment_stored_card`；任何模块不得 import `com.paytech.card.core.crypto` | 全部 | D10、PCI |
| R12 | 补偿事件 Handler 只能注册在 `-core`，且 `eventType` 必须在本服务的事件目录文件中登记 | 全部 | 08 |
| R13 | Reactor 内任何模块声明的 `com.paytech` 依赖（含 test 范围）必须能归入 Reactor 命名体系（Build / Foundation / Tool / 服务目录）；旧工程坐标（如 `platform-support-api`）只会从本地仓库解析到过期制品，POM 层直接拒绝 | 全部（依赖图） | D42；2026-10-18 Gateway / BFF 旧坐标事故 |
| R14 | 服务 `-core` 的 `..service.processor..` 包中类名以 `Processor` 结尾的具体类必须 `isAssignableTo(AbstractProcessor)`（需要资源锁时经 `AbstractLockProcessor`）；抽象中间类、接口与非 `*Processor` 协作者不检查；BFF（client-console-bff、operations-console-bff、partner-console-bff）的页面聚合 Processor 与 Foundation 排除 | 服务 core（排除 BFF） | 2026-09-16 决定第 8 条；2026-10-18「Processor 模板迁移与 R14」 |

实现要点：

- R1、R3、R6、R8 用 ArchUnit `classes().that().resideInAPackage(...)` 加 Maven 依赖图解析（读取 Reactor 的 `pom.xml` 树）双重判断，避免只靠包名。
- R4、R11 用自定义扫描：从 03 文档生成 `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`，规则按服务读取白名单。
- 现有的 `ChannelModuleBoundaryTest`、`FinanceModuleBoundaryTest`、`RiskModuleBoundaryAutoTest` 中与模块集合、依赖集合相关的断言并入本规则集后删除；它们对 Mapper 路径与 Enforcer 清单的断言保留在各自服务的 `-test`。
