# CreateCheckout 测试重建交付记录

日期：2026-09-17。当前状态统一见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-tests-20260917)。

## 用户确认与实施范围

- 用户明确要求删除该接口旧测试并按新规范重建，沿用已确认的 DTO Mock 与两级生命周期规范。
- 删除九个旧创建/快照合同测试文件，移除混合查询测试中的创建场景；保留其他接口测试。两个新测试类覆盖 HTTP 创建和本地原子写入边界。
- 生产链没有新增 Mock、Profile 分支、探针或测试开关。仅补充 `GetOrderProcessor` 原本漏掉的 `java.util.Objects` import，解决基线编译错误，不改变逻辑。
- Maven 新增显式 `checkout-create-tests` Profile 和测试用 Lombok 编译配置；默认完整测试和覆盖率门槛保持。

## 验证证据

1. 初始全量测试编译：发现上述 import 缺失；补齐后仍有 Orders、Customer 等旧测试引用已删除类型的编译错误。日志 `/tmp/commerce-create-checkout-baseline-after-import.log`，没有扩展本次范围迁移这些测试。
2. 完整应用启动：发现 `RefundServiceImpl → CompensationEventRegistrar → CompensationHandlerRegistry → RefundSubmissionHandler → RefundServiceImpl` 既有循环依赖。新测试切片仅装配真实创建链，未修改该生产依赖。
3. 本地 `mor-local-mysql-1` 原处于停止状态，本次手工启动已有容器；没有创建容器、重建数据卷或访问 `mor-test`。测试代码不控制中间件生命周期。
4. 首轮 55 个 API 场景：33 通过、18 断言失败、4 SQL 错误。暴露本地缺少 `creation_mode`、`consumer_country_code`，以及测试侧字段路径、Problem title/timestamp 构造问题；后者已校正。日志 `/tmp/commerce-create-checkout-real.log`。此轮不构成成功写入验证。
5. 校正后独立运行参数/可信 Header/CCT 场景：**48 通过，0 失败，0 错误，0 跳过**，包含逐例 AfterEach 清理。日志 `/tmp/commerce-create-checkout-validation-final.log`。这些场景在写入前拒绝，不能据此推断创建成功、并发写入及回滚已验证。
6. 最终补充缺列前置检查，缺失结构将直接给出环境准备提示；成功落库、并发防重及两个事务场景需补列后重跑。单例复核已在 BeforeEach 明确提示缺列（`/tmp/commerce-create-checkout-schema-preflight.log`）。最终编译与静态检查见台账。

## 未决项

- 已向用户请求备份结构并应用上述两份已有增量 SQL；尚未收到本次执行授权，未执行 DDL。
- 非 USD 币种只在响应转换阶段被拒绝、重复 `clientItemId` 缺少业务校验，是源码走查发现的待验证问题。保留期望提前拒绝的回归用例，不以改变业务或降低断言掩盖；当前 Schema 阻塞了独立确认。
- 尚未验证全量 auto、全量覆盖率及部署。未提交、推送、发布制品。

## 规范自查

真实 HTTP 入口、出站 Client、数据库、DTO 按身份匹配、未配置失败、两级 BeforeEach、统一 AfterEach、零调用检查、响应及数据库 Checker、并发屏障、事务回滚和传播均有对应实现。Builder 无 I/O；Checker 不改变事实；同一 JVM 禁止并行。创建没有 Handler、Redis、MQ 消费者，这些项目不适用。没有恢复旧请求 Key 组件；现行接口必填 Key 只传固定占位 Header，不断言请求重放。

## application-mock.yml 补充（2026-09-17）

- 按用户反馈新增完整 Mock 配置，MockTestBase 从 `auto` 改为 `mock`；移除注解中的固定端口属性，Java 只注入随机 Mock 地址。数据库、业务参数、HTTP 策略及服务发现绑定等集中于 YAML，未修改生产代码。
- 清除本次进程的 Checkout/ID/数据库/Nacos 环境变量后，使用 `-Pcheckout-create-tests -Dtest=CreateCheckoutApiTest#test_createCheckout test` 验证：17 个测试源文件编译通过，`mock` Profile 的 Spring/Tomcat 启动成功，Hikari 连接本地 MySQL 成功。随后在既有 BeforeEach 因缺列失败；该结果只证明启动配置可用，不表示业务用例通过。日志 `/tmp/commerce-mock-profile-startup.log`。
- 测试规范、示例及 Java 测试 AI 规则同步区分 `auto` 执行集合与 `mock` Spring Profile。本轮未执行 DDL、提交或部署。

## 配置字段清单补充（2026-09-17）

- 用户明确 Mock 文件应能展示系统定义的配置字段；补齐已接入 Foundation 补偿及 XXL-Job 的默认/可选字段、关闭 Nacos 的连接字段及动态 CCT 地址，保持原组件开关不变。注释标明来源、用途、单位、默认/Mock 值与启用条件。
- 使用实际测试 Classpath 的配置类反射核对全部 25 个 Commerce/Foundation 字段，并比较生产 YAML 全部 47 个叶子键：Mock 文件均显式包含，动态 CCT 地址亦已列出，Spring YAML 解析通过。临时检查程序未加入仓库，日志 `/tmp/commerce-mock-config-catalog-check.log`。
- 更新后清除相关环境变量再次运行同一单例：Mock Spring/Tomcat 启动和 MySQL 连接成功；BeforeEach 仍因既有 `creation_mode` 缺列失败，未执行业务用例，未执行 DDL。日志 `/tmp/commerce-mock-config-catalog-startup.log`。
- 规范、确认记录、Java 测试 AI 规则及服务说明同步字段清单维护要求。本轮无生产代码修改、提交或部署。

## 场景拆分补充（2026-09-17）

按用户反馈，将原单个 `CreateCheckoutApiTest` 拆为成功、防重、参数校验、CCT 预检四个独立场景类，集中在 `checkout/create` 包。`AbstractCreateCheckoutApiTest` 只复用调用和辅助；每类仍有 BeforeEach，MockTestBase 的全局生命周期保持。参数源与零副作用检查留在使用它们的校验类；原测试方法、参数组合和断言逐字核对无遗漏。Maven Profile、README 和技术说明中的当前命令同步更新；前文旧类名是原验证基线。

本次只调整测试组织及相关规范文档，不改生产业务、Mock 服务、DDL 或公共测试组件。测试执行数量、失败集合及默认选择验证见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-split-20260917)。

## 被测入口引用补充（2026-09-17）

参考 Transaction 测试类的 JavaDoc `@see`，四个 CreateCheckout 场景类各自引用 `ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)`；事务测试引用实际调用的 `CheckoutCreationService#saveCreateCheckoutContext(CreateCheckoutContext)`。测试规范、AI 规则与示例同步要求引用具体入口方法，不以测试调用器代替。

只调整类注释与所需 import。移除 JavaDoc/import 后核对五个类的可执行源码完全一致；JDK 25 JavaDoc 的 `-Xdoclint:reference` 校验退出码为 0，日志 `/tmp/commerce-checkout-test-see-javadoc.log`。未重跑单元测试，前序业务失败状态未因此改变；未改业务代码、执行 SQL 或提交。

## 两项输入校验缺陷修复（2026-09-17）

用户提供原失败日志后，本轮进入业务缺陷修复范围，按已有合同补齐提前拒绝；没有通过降低测试期望变绿。原因、修改和写接口规范自查见[修复说明](validation-fix.md)，执行证据见[最新台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-validation-20260917)。前文关于两个失败的内容为修复前验证记录，当前结果以最新台账为准。
