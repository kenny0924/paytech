# Platform Support 接口自动测试

测试规范以 [UNIT_TEST_GUIDE.md](../../agent-docs/UNIT_TEST_GUIDE.md) 和[公共示例](../../agent-docs/examples/UNIT_TEST_EXAMPLE.md)为准。
PLT-I03 实际验证结果见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-api-credential-validation-tests-20260917)。

## PLT-I03 validate

`auto/ext/http/apicredential/validate` 按成功、认证拒绝、数据异常、并发快照分组；每个类的 `@see` 均引用
`ApiCredentialValidationApi#validate(String)`。请求经过随机端口的正式应用、Controller、Processor 和真实 MyBatis/MySQL。
本接口没有外部 HTTP 调用；共享测试应用另为 Notification 启用补偿与 Vault HTTP Mock，PLT-I03 本身不调用它们。人工认证、Redis 协调和文件能力仍关闭。

- `MockTestBase` 每例检查空基线，具体类的 `BeforeEach` 插入独立业务事实。
- Builder 允许非法字段；Mocker 只通过 TestMapper 准备数据，PHC 使用真实 Argon2id 计算。
- HTTP Checker 复用 Foundation 公共断言，数据库 Checker 检查完整行未改变和零审计。
- 固定 UTC 时钟验证微秒级有效期边界；MyBatis Observer 继续执行真实 SQL，只观察事务与协调并发时序。
- `TestBase` 的 `AfterEach` 执行全部 `Clear`；只清理已成功插入的 ID 及本例 Request ID。清理失败后拒绝复用。
- 共享本地库不做整表清理；随机合成 ID/Prefix 隔离其他任务，JUnit 串行执行。

## 本地运行

使用 JDK 25 和已手动启动的 `local-environment` MySQL（`127.0.0.1:13306/mor_platform`）。
`src/test/resources/init/ddl.sql` 是初始化空本地库的人工入口，保留正式表的完整结构和注释；
已有数据库勿重复建表、重建或重置。测试不执行 DDL，不启动或停止共享容器。

固定配置和本地默认凭据集中在 `application-mock.yml`。从 IDEA 或 Maven 启动继承 `MockTestBase`
的测试时，`LocalMockEnvironmentInitializer` 自动定位所属工作区，并读取已授权的
`local-environment/.env.local` 中的 `MOR_PLATFORM_DB_PASSWORD`、`PAYTECH_REDIS_PASSWORD`，无需另配启动环境变量。
加载仅限 `mock` Profile，不执行 Shell，不导入其他字段，不输出或持久化密码；文件不存在时沿用 YAML 默认值。
显式提供的 `PLATFORM_SUPPORT_TEST_DB_PASSWORD` / `PLATFORM_SUPPORT_TEST_REDIS_PASSWORD` 仍可覆盖本地配置。
认证失败、连接失败与表结构缺失分别给出原因及安全的 SQLState/错误码。

从服务根目录运行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -pl platform-support-test -am \
  -Dtest=ApiCredentialValidationSuccessTest,ApiCredentialValidationRejectionTest,ApiCredentialValidationUnavailableTest,ApiCredentialValidationConsistencyTest,LocalMockEnvironmentInitializerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

公共依赖存在未发布的本地修改时，使用包含 Foundation、Platform 和 CCT API 的联合 Reactor，确保测试引用当前源码。
这组命令只验证指定接口，不代表其他接口回归、全服务覆盖率门禁或部署验收。

## Notification 普通 Email

入口测试通过模块公开 `SubmitNotificationProcessor.process`，不启动通知 HTTP API。测试使用真实 MySQL、正式 VaultClient、独立 Vault HTTP Mock 及测试制品内的 EmailProvider；不会发送真实邮件。

- 手工准备 `init/ddl.sql` 中的七张 Notification 表和公共 `t_compensation_event`，已有表不得重建；缺失时测试快速失败。
- `NotificationFixture` 每例检查空任务基线及保留配置身份，再事务性准备模板、语言、账号和路由；等待真实 Dispatcher 停止后才清理。
- 测试通知来源固定为 `NOTIFICATION_TEST`，配置 ID 保留 890001..890006；占用时拒绝继续，不覆盖既有数据。运行期间不要启动其他 Notification 扫描器。
- 覆盖独立收件人、覆盖参数、重复邮箱拒绝、来源冲突、整单回滚、UNKNOWN 原发送查询、当前文案重试、发送上限及旧 Worker 回写保护。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -pl platform-support-test -am \
  -Dtest=NotificationInputTest,SubmitNotificationTest,NotificationDeliveryHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

真实 Provider、Vault Purpose 授权、强服务身份与 HTTP、认证邮件接入均不属于此 Mock 验证。实际结果见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920)。
