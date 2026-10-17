# 第 02a 步报告 · Platform Support 搬入并拆 api / core / app / test

- 分支：`refactor/step-02a-platform`（起点 `refactor-main@24122de`）。实现提交 `1cc7204`；本报告单独提交。
- 结论：第 4 节 6 项全部真实执行。
  - 4.1 至 4.3、4.5、4.6 通过；`-Pcoverage-gate` 按预期失败（0.76 / 0.56，低于 0.90，既有问题）。
  - 4.4 应用能以 mock Profile 启动，liveness 为 UP；`/actuator/health` 总体为 DOWN，原因只有 `gatewaySnapshot`。搬迁前的 web 制品在同一数据库上结果完全相同，详见第 2 节第 4 项。
  - Node 与 Python 脚本的失败与 HEAD 基线逐项一致，属既有问题。
- **需要先看的两项偏差：**
  - 签发端没有使用"现有 Operations 密钥对"，改用配置注入的 ES256 密钥，见第 4 节。
  - 配置前缀改为 `internal-token.issuer.*`，没有使用任务卡写的 `internal-auth.issuer.*`，见第 5 节第 1 项。

## 1. 改动摘要

1. `git mv` 六个模块到 `paytech/platform-support/paytech-platform-support-{api,access-control,audit-security,file-document,delivery,integration,test}`；web 整体移为 `-app`，其 `core`、`service` 包与 `mapper/reliability` 移入新建 `-core`。web 的 `integration` 包改名为 `...support.core.integration`；被 service 引用的 `NotificationManagementProperties` 移到 `...support.core.configuration`。
2. 删除 `platform-support-service/{mvnw,.mvn,.gitignore}`，该目录已不存在。`paytech/pom.xml` 只取消了 `platform-support` 一行的注释。
3. 服务聚合 POM：parent 改为 `../build/paytech-parent/pom.xml`；dependencyManagement 改为 8 个新坐标加 `paytech-vault-client`，版本均为 `${project.version}`；Enforcer 原样保留，只删除两条 AMQP 禁令（C5）。`failIfNoSpecifiedTests` 保持聚合 POM `false`、test 模块 `true`。api 的 Lombok 由 optional 改为 provided。
4. 覆盖率 antrun 整段移入 Profile `coverage-gate`；fileset 改为 `${project.basedir}/../paytech-platform-support-{api,access-control,audit-security,file-document,delivery,integration,core,app}`，classes 与 sources 各 8 个。
5. `LocalMockEnvironmentInitializer` 改读系统属性 `paytech.local-environment.dir`；未设置时回退到 `${user.dir}/../../../local-environment`；目录中没有 `.env.example` 时明确报错。已删除按目录名向上搜索的逻辑。`OnboardingFileTestBase`、`LocalMockEnvironmentInitializerTest` 与 `application-mock.yml` 的证书路径同步修改。
6. messaging：`-app` 依赖 `paytech-messaging`，`-core` 依赖 `paytech-messaging-api`。没有引入 `spring-boot-starter-amqp`，因此不连接 Broker，也不声明队列。配置 `messaging.enabled=true`，未设置 producer / consumer。
7. 内部 Token 签发端（D26），合同见第 3 节：
   - `IssueInternalTokenProcessor` 执行白名单授权、内存签名与 WORKLOAD 审计，签名用的 `InternalTokenKeyRing` 与 `InternalTokenIssuerProperties` 都在 `-core`。
   - Controller 位于 `-app` 的 `ext/rest/internalauth`。
   - 签发路径加入 `web.logging.metadata-only-path-prefixes`，不记录 Body。
8. 入站：`InternalCallerResolver` 供 Webhook、Notification 与签发入口使用。携带已验证 Token 时以 `sub` 为调用方，与 `X-Caller-Service` 不一致时返回 400。`internal-auth.mode` 默认 OFF；签发与 JWKS 路径加入 `exempt-path-prefixes`。
9. 出站：`InternalTokenClientOptions` 接到 Payment 读卡与 CCT 文件两个客户端。mode=OFF 时不附加 Token；mode 非 OFF 时，Foundation 以进程内签发通道 `selfInternalTokenIssueClient` 创建带缓存的出站 Token 来源。
10. audit-security：`AuditModels.Identity` 增加 `AuditActorType`（USER / WORKLOAD），原两参构造仍为 USER；`actor_type` 由原来写死 `'USER'` 改为参数。V1 事件哈希不变，`actor_type` 本来就不参与哈希。
11. 其他 Platform 代码：`ClientWebhookController` 计算默认投递时间窗时改用注入的 `Clock`。原代码用 `Instant.now()`，与固定测试时钟（2026-09-17T12:00Z）相差超过 30 天后，`ClientWebhookSelfServiceTest.test_deliveryFlow` 必然失败。今天搬迁前两次基线都因此失败。生产 `Clock` 为 `systemUTC`，行为不变。
12. arch-rules：
    - `generate-table-ownership.mjs` 从 Platform 评审 Schema 读取 56 张保留表，加入白名单，不计入 design/03 §4.2 的核对。
    - 新增 `known-deviations.yaml` 与 `KnownDeviations`，登记 3 条 Platform api 的 R2 偏差。
    - R1 新增同服务多 core 的 fixture 用例。
    - `TableOwnershipAutoTest` 与 `ReactorArchitectureAutoTest` 同步修改。
13. 测试：`ArchitectureAutoTest` 执行 R1–R12，除登记的偏差外零违规。另新增 `compensation-event-catalog.yaml`（4 个 eventType）。新增与修改的测试见第 2 节第 2 项。
14. 路径：`initialize.py` 第 24、124 行，`test_risk_rule_configuration_catalog.py` 第 21 行，PDManer 生成器与 5 个 Platform 测试。其中 `platform-support-schema.test.mjs` 第 133、171 行派生的 test 模块路径也已修改，09 文档未列这两行。
15. 文档：`README.md`、`docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` 与 test README 中指向仓库级目录的 55 条链接已改为新深度，61 条链接全部能解析。README 重写了模块一节，并新增 V2 构建命令与签发端说明。

## 2. 第 4 节验证（实际命令与结果）

所有 Maven 命令前均执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并导出 `PAYTECH_REDIS_PASSWORD`（取自 `.env.local` 的 `MOR_REDIS_PASSWORD`，未打印）。`settings.xml` 未改动，没有 up/down/restart 任何容器。

1. **搬迁前基线**：在 `platform-support-service/` 下执行 `./mvnw -B test -pl platform-support-test -am`。
   - 第一次在 worktree 中运行：719 个测试，5 个失败。其中 4 个是 MinIO 证书缺失导致的类级失败：worktree 没有 `local-environment/minio/certs`，旧初始化器只能找到 worktree。
   - 为得到真实基线，把旧工程复制到 scratchpad，`local-environment` 以只读符号链接指向主工作区，再运行一次：**759 个测试，1 个失败**，即 `ClientWebhookSelfServiceTest.test_deliveryFlow:108 expected: <1> but was: <0>`，原因见第 1 节第 11 项。
2. **搬迁后 Platform**：在 `paytech/` 下执行 `./mvnw -B verify -pl platform-support/paytech-platform-support-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，结果 **BUILD SUCCESS**，用时 5:23。

   | 模块 | 测试数 | 失败 / 错误 |
   |---|---|---|
   | paytech-platform-support-test | 794 | 0 / 0 |
   | Foundation 16 个模块（-am 带入） | 935 | 0 / 0 |
   | paytech-arch-rules | 53 | 0 / 0 |

   794 = 基线 759 + 新增 35，新增明细如下：
   - 签发：`InternalTokenIssueTest` 7。
   - JWKS：`InternalJwksTest` 2。
   - DUAL：Webhook 4，Notification 3，出站 1。
   - REQUIRED：Webhook 3，Notification 4，豁免路径 2。
   - 消息装配：`MessagingAssemblyTest` 1。
   - 启动校验：`InternalTokenIssuerConfigurationTest` 4。
   - 架构：`ArchitectureAutoTest` 1。
   - 锚点：`LocalMockEnvironmentInitializerTest` +3。
   - 另外，`StandardApiCardPaymentTest` 增加了"OFF 时出站不带 Authorization"的断言。

   **Profile `coverage-gate`**：`./mvnw -B verify -pl platform-support/paytech-platform-support-test -am -Pcoverage-gate -Dpaytech.local-environment.dir=…`。
   - 结果：**BUILD FAILURE**（允许失败，既有问题），794 个测试 0 失败，之后 antrun 门禁报 `instructions covered ratio is 0.76`、`branches covered ratio is 0.56`，均低于 0.90。
   - 8 个 fileset 均已正确解析：数值来自真实报告，不是空报告；此前台账记录为 0.54 / 0.37。
   - 默认 `verify` 不再执行该门禁，见本节第 2、3 项。
3. **全 Reactor**：`cd paytech && ./mvnw -B verify -Dpaytech.local-environment.dir=…`，结果 **BUILD SUCCESS**，用时 4:56，**1802 个测试，0 失败 / 0 错误**。
   - Foundation 17 个模块共 946 个（含 command-idempotency 11）。
   - arch-rules 53，platform-support-test 794，data-service-test 16，instance-manifest 16，migration-runner 27，arch-rules-reactor-test 3。
   - `node tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `table-ownership.yaml is current (265 physical tables + 56 retained).`
4. **`-app` 以 mock Profile 启动**：
   - 启动命令：`java -jar paytech-platform-support-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock --spring.config.additional-location=file:<test>/application-mock.yml --server.port=28106 --test.mock-http.base-url=http://127.0.0.1:9 --paytech.local-environment.dir=…`。数据库与 Redis 密码取自 `.env.local` 的环境变量。
   - 4.4 秒后输出 `Started PlatformSupportApplication`，日志中 0 条 ERROR。
   - `/platform-support/actuator/health/liveness` 返回 200 UP；`/readiness` 与 `/health` 返回 503 DOWN；`/platform-support/platform/v1/internal-auth/jwks` 返回 200。
   - 打开健康详情后，各组件状态为：db UP（主池与审计写入池）、diskSpace UP、ssl UP、ping UP、livenessState UP、readinessState UP、refreshScope UP、discoveryComposite UNKNOWN（Nacos 关闭），**只有 `gatewaySnapshot` 为 DOWN**。
   - 该探针要求 `mor_platform` 中存在可编译的 Gateway 目录，这些数据只能由人工初始化 SQL 提供；测试结束后数据库为空，而本步禁止执行 SQL。
   - 对照：用搬迁前的 `platform-support-web-1.0.0-SNAPSHOT-exec.jar` 以同样方式启动（端口 28107），组件结果逐项相同，总体同样为 DOWN。两个进程验证后均已停止。
5. **脚本**（新旧对照时用 `git archive HEAD` 导出到 scratchpad）：
   - PDManer：`node --test scripts/pdmaner/pdmaner-canonical-paths.test.mjs scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-scenario-doc.test.mjs scripts/pdmaner/platform-support-schema.test.mjs scripts/pdmaner/platform-file-upload-model.test.mjs`：58 项，50 通过，8 失败。HEAD 基线同样是 58 / 50 / 8，失败测试名逐个相同。这些失败是模型、增量 SQL 与当前 Schema 的内容漂移，例如 `t_notification_request` 缺少 `idx_notify_request_create_time`，与路径无关；`pdmaner-canonical-paths.test.mjs` 全部通过。
   - Python：`python3 -m unittest test_risk_rule_configuration_catalog`：26 项，errors=41。HEAD 基线同为 26 项、errors=41，原因都是 `sqlite3.OperationalError: no such function: UTC_TIMESTAMP`。新路径的文件已能读取：`initialize.PLATFORM_CATALOG` 存在。
6. **历史与目录**：
   - `git log --follow --oneline -- paytech/platform-support/paytech-platform-support-access-control/pom.xml` 输出 `1cc7204`、`fff35d2`、`274fac8`。
   - `…/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor/webhook/SubmitWebhookEventProcessor.java` 输出 `1cc7204`、`ee469e0`。
   - `LocalMockEnvironmentInitializer.java` 最早一条是 `fff35d2`。
   - `ls platform-support-service` 返回 No such file or directory。
   - 09 §8 中 `paytech` 下空 `<relativePath/>` 计数为 0；`multiModuleProjectDirectory` 只剩父 POM 中第 00 步设计的 `paytech.local-environment.dir` 默认值。

## 3. 新 api 合同（`paytech-platform-support-api`，包 `com.paytech.platform.support.api.internalauth`）

| 合同 | 方法与路径（context path `/platform-support`） | 请求 / 响应 |
|---|---|---|
| `InternalTokenIssueApi` | `issue(@RequestHeader X-Caller-Service, @Valid InternalTokenIssueRequestBody)`，`POST /platform/v1/internal-tokens` | `InternalTokenIssueRequestBody{targetService, purpose}` → `InternalTokenIssueResponseBody{token, expiresAt}` |
| `InternalJwksQueryApi` | `queryInternalJwks()`，`GET /platform/v1/internal-auth/jwks` | `InternalJwksResponseBody{keys: [InternalPublicKey{kty, kid, use, alg, crv, x, y}]}` |

- JSON 字段与 Foundation `paytech-internal-auth` 的 `contract.InternalTokenIssueRequest/Response` 一致。api 模块不能依赖 internal-auth（R2），所以定义了等价 DTO。
- 签发响应：Token 的 `sub` 为调用方、`aud` 为目标服务、`inst` 为 `paytech.instance-id`、`purpose` 为请求值、`jti` 为 UUID；`exp - iat` 等于 `token-lifetime`（默认且最长 300s）；`expiresAt` 为 UTC 秒精度。
- 错误码：
  - 缺 Header 或调用方名不合法：400 `INVALID_REQUEST`。
  - Body 字段不合法：422 `VALIDATION_ERROR`。
  - 未登记调用方、越权目标或越权 Purpose：400 `BUSINESS_CHECK_FAILED`，同时写 DENIED 审计与安全事件。
  - mode=REQUIRED 下其他路径缺少 Token 或 Token 无效：401 `INTERNAL_TOKEN_INVALID`。
- JWKS 可直接被 Foundation `JwksInternalTokenKeySource` 读取，测试已证明。

## 4. 密钥用途的处理方式与 SQL 草案

**结论：表驱动的 INTERNAL 用途待 Schema 变更；签发端改用配置注入的 ES256 密钥先行实现（`key-usage=STATIC`）。**

- 现表以 `realm` 列区分用途（OPERATIONS / CUSTOMER），算法只有 RS256。AGENTS.md 固定人工 Realm 恰为 `{OPERATIONS, CUSTOMER}`，所以不能新增 `realm=INTERNAL`，必须加用途列，这需要评审 SQL。
- 任务卡要求的兜底是"用现有 Operations 密钥对先行实现"，但它不可行：
  - 现有 Operations 密钥是 RSA（RS256，`LocalHumanAuthenticationCryptography`）。
  - Foundation `InternalTokenVerifierImpl` 只接受 ES256，`JwksInternalTokenKeySource` 只保留 P-256 公钥。
  - 因此用 Operations 密钥签出的 Token，任何服务都无法验证。
- 实际实现：
  - `internal-token.issuer.key-usage` 当前只有 `STATIC` 一个取值。私钥 `private-jwk`（含 kid 的 P-256 JWK）经安全配置注入；轮换中的旧公钥放在 `retiring-public-jwks`，JWKS 中签名公钥在前。
  - prod Profile 下使用 STATIC 时拒绝启动。
  - 表驱动来源（例如取值 `SIGNING_KEY_TABLE`）随下面的 SQL 一起评审后再加，本步不保留占位代码。
- 现有人工认证密钥同样经本地适配器注入私钥（KMS 尚未接入）；内部 Token 待 KMS 方案确定后一并接入。

SQL 草案（**未执行**；须同步 PDManer 模型、`platform-support-schema.sql` 与测试 `init/ddl.sql`。另外，PDManer 是否支持函数索引需确认）：

```sql
-- t_jwt_signing_key 增加用途列：HUMAN_ACCESS 保持现有 OPERATIONS / CUSTOMER RS256；INTERNAL_CALL 为内部调用 ES256，realm 为空。
ALTER TABLE `t_jwt_signing_key`
  ADD COLUMN `key_usage` VARCHAR(64) NOT NULL DEFAULT 'HUMAN_ACCESS'
    COMMENT '签名密钥用途的稳定业务代码。允许值：HUMAN_ACCESS、INTERNAL_CALL。' AFTER `signing_key_id`,
  MODIFY COLUMN `realm` VARCHAR(64) NULL
    COMMENT '认证域的稳定业务代码。HUMAN_ACCESS 必填，允许值：OPERATIONS、CUSTOMER；INTERNAL_CALL 为空。',
  MODIFY COLUMN `algorithm` VARCHAR(64) NOT NULL
    COMMENT '签名算法的稳定业务代码。允许值：RS256（HUMAN_ACCESS）、ES256（INTERNAL_CALL）。',
  DROP INDEX `uk_jwt_signing_realm_kid`,
  ADD UNIQUE KEY `uk_jwt_signing_usage_kid` (`key_usage`, (COALESCE(`realm`, '')), `kid`)
    COMMENT '保证业务唯一性：key_usage、realm、kid',
  DROP INDEX `uk_jwt_signing_active`,
  ADD UNIQUE KEY `uk_jwt_signing_active` (`key_usage`, (COALESCE(`realm`, '')), `active_slot`)
    COMMENT '保证业务唯一性：key_usage、realm、active_slot',
  COMMENT = '保存人工认证 RS256 与内部调用 ES256 JWT 签名密钥的公开材料、KMS 引用和轮换状态；Canonical JSON 仅允许有界版本化公钥 JWK';
-- 初始化：每个环境插入一行 INTERNAL_CALL / ES256 / ACTIVE / active_slot=1 记录，公钥 JWK 取自该环境的 KMS 密钥（按环境人工生成，不进版本库）。
-- 回滚：先删除 INTERNAL_CALL 行，再反向 ALTER：恢复 realm NOT NULL、原两个唯一索引与注释，删除 key_usage。
```

## 5. 待确认项

1. **配置前缀**：任务卡写的是 `internal-auth.issuer.key-usage` 与 `internal-auth.issuer.callers`。Foundation `InternalAuthProperties` 以 `ignoreUnknownFields=false` 绑定整个 `internal-auth.*`，一旦 mode 非 OFF，宿主在其下的任何键都会导致启动失败。因此改为 Platform 自有前缀 `internal-token.issuer.*`，与现有 `webhook.http.callers`、`notification.http.callers` 同风格。若坚持原前缀，需要修改 Foundation，这超出本步范围。
2. **签发端调用方认证**：阶段 0 只用 `X-Caller-Service` 白名单（过渡信任），签发路径在 REQUIRED 下也豁免。Foundation 签发合同 `contract.InternalTokenIssueApi` 没有该 Header 参数，因此调用方须在 RestClient 上设置默认 Header `X-Caller-Service`。是否把该 Header 加进 Foundation 合同，需要确认。
3. **签发端依赖人工认证开关**：签发与拒绝都必须写审计；审计写入及其 `IdGenerator` 只在 operations 或 client 人工认证启用时存在，因此签发端启用时若两者都未启用，会拒绝启动。是否把审计写入独立于人工认证，需要确认。
4. **签发时钟**：签发使用独立的墙钟 Bean `internalTokenClock`（`Clock.systemUTC()`，`defaultCandidate=false`），不使用可替换的业务时钟，因为各服务的 Foundation 校验器都按系统 UTC 判定 `iat` / `exp`。Platform 自身的出站 Token 经同一签发流程产生，同样受白名单与审计约束。Foundation 来源会缓存到过期前 60 秒，所以审计量约为每个目标每 4 分钟一条。
5. **R2 偏差**：Platform api 依赖 `jackson-annotations`、`jackson-databind`（DTO 注解与 `JsonNode`）与 `reactor-core`（`ApiCredentialValidationApi` 返回 `Mono`），已登记在 `known-deviations.yaml`。规则库不做豁免，服务测试与 Reactor 测试都要求结果与登记一致。后续各业务 api 大概率也需要 Jackson 注解，是否放宽 R2 允许清单需要统一决定（第 00 步待确认第 6 项）。
6. **R4 白名单扩展**：保留服务 Platform 的 56 张表从其评审 Schema 自动加入，其中包括待迁移的 `t_command_idempotency`；design/03 §4.2 的 265 张核对口径不变。是否把保留服务的表写入 design/03，需要确认。
7. **`-core` 的边界**：任务卡要求的应用编排层 core 放的是原 web 的 `core`、`service`、`integration`，包名保持 `com.paytech.platform.support.{core,service}`，按 01 §8 先不改包根。design/03 §1.2 只写了"保留 5 个业务模块"，没有这个新模块，需要同步。
8. **mock Profile 健康为 DOWN**：`gatewaySnapshot` 依赖人工初始化数据，见第 2 节第 4 项。若要求 health 为 UP，需要在执行初始化 SQL 后复测，本步未执行 SQL。
9. **YAML 陷阱**：裸 `OFF` 在 YAML 1.1 中是布尔 false，Foundation 的模式条件会把它当成非 OFF。测试配置第一次就因此误启用了出站 Token。现已在 mock 配置中加引号并注释；各服务接入 internal-auth 时需要注意这一点。是否在 Foundation 条件中兼容 false，需要确认。
10. **磁盘**：验证期间宿主可用空间约 4.4 GiB（99% 已用）。本步的 scratch 副本已删除，`target/` 目录保留。
11. **本步未改**：`test-environment/scripts/build.sh` 与 `test_deploy_script.py` 仍引用 `platform-support-service`，由主会话统一修改。仓库级文档中指向 Platform 的链接留到第 13 步处理。
