# PLT 报告（功能与定向测试已完成；全量回归受其他会话并发占用本地库阻塞，0 失败尚未达成）

## 恢复后更新（2026-10-20）

- 合同 `IntegrationLookupApi.java` 的 `@return` 已和主工作区同步为"编码格式不合法返回 400 INVALID_REQUEST（路径参数校验按 Foundation 统一映射）"；用 diff 核对，两份合同文件现在都和主工作区一致。遗留 1 已关闭。
- 测试模块 README 末尾新增"接入方存在性内部查询"一节。
- 用 regex 命令重跑 Platform 全量 auto：884 个用例，308 个失败、28 个错误。全部失败在 `MockTestBase.setUpGlobalMocks` 的空基线检查（"测试独占表存在数据"）或 DuplicateKey；各用例报出的有数据的表不断变化，例如 webhook、role、jwt key 等表时有时无。
- 原因：运行期间另一个会话的 Platform 测试 JVM（PID 84857，`Code-MoR-slice2/paytech/platform-support/paytech-platform-support-test`，09:02:47 启动）同时在写同一个本地 `mor_platform` 库（mor-local-mysql-1, 13306）。README 要求同一个库串行执行。数据在不断变化，说明这不是我上次中止留下的固定残留，而是另一个会话正在跑。
- 没有清库：对方还在运行，清空独占表会破坏它的测试。等 84857 退出后，确认独占表为空（不为空就按 README 人工确认后清空），再用第 4 节的 regex 命令重跑。定向测试（第 4 节，37/37 通过）不受影响。

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/agent-ac886849d073de325`（未提交，未执行任何 git 写命令）
- 本报告位于 worktree 的同名需求目录中。worktree 基于 HEAD 创建，不含本需求目录；隔离规则也禁止写主工作区，需要时请复制到主工作区。

## 1. 已读

AGENTS.md「接口开发必读与自查」；`agent-docs/DEVELOPMENT_CONVENTIONS.md` 全文；`agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 4–7 节；`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节；`agent-docs/UNIT_TEST_GUIDE.md` 第 5、6、16–20 节；technique.md 第 9 节第 6 条；任务卡 PLT.md。
Platform 源码：`StandardApiExecutionController`、`StandardApiCommandApi`、`IntegrationRepository`/`IntegrationRepositoryImpl`/`IntegrationMapper.xml`(byCode)、`IntegrationManagementService(Impl)`、`application.yml` 与 `application-mock.yml` 的 `internal-auth` / `internal-token.issuer`、`ConfigurationManagementRequestConfiguration`（人工会话拦截路径）、Foundation `MvcProblemExceptionMapper`；测试 `StandardApiExecutionTest`、`ExternalApiTestBase`/`OperationsApiTestBase`、`InternalAuthRequired*Test`、`InternalTokens`、`ExternalApiMocker`、`LocalMockEnvironmentInitializer`、测试模块 README。

## 2. 落点与鉴权

- Controller：`paytech-platform-support-app/.../ext/rest/standardapi/IntegrationLookupController implements IntegrationLookupApi`，只做协议适配，回显编码 + `exists`。
- Service：integration 模块新增 `IntegrationLookupService` + `IntegrationLookupServiceImpl`，复用既有 `IntegrationRepository.byCode`（无新 SQL），`!= null` 即存在，不区分启停、不返回配置。无 Processor、无事务（单条读取不需要一致性快照）、无缓存、无白名单。
- 鉴权：Foundation internal-auth 入站过滤器对所有非豁免路径生效，豁免仅 `/actuator`、`/public/`、`/platform/v1/internal-tokens`、`/platform/v1/internal-auth/jwks`；人工会话拦截器只挂在 `*-admin` 等前缀，不含 `/standard-apis/v1/**`。因此新接口与 `StandardApiCommandApi` 鉴权完全相同，**无需新增生产配置**。
- 合同两文件（`IntegrationLookupApi`、`IntegrationExistenceResponseBody`）从主工作区逐字复制，未改。

## 3. 改动文件

| 文件（相对 `paytech/platform-support/`） | 类型 |
|---|---|
| `paytech-platform-support-api/.../api/standardapi/IntegrationLookupApi.java` | 复制（未改） |
| `paytech-platform-support-api/.../api/standardapi/response/IntegrationExistenceResponseBody.java` | 复制（未改） |
| `paytech-platform-support-integration/.../domainservice/service/IntegrationLookupService.java` | 新增 |
| `paytech-platform-support-integration/.../domainservice/service/impl/IntegrationLookupServiceImpl.java` | 新增 |
| `paytech-platform-support-app/.../ext/rest/standardapi/IntegrationLookupController.java` | 新增 |
| `paytech-platform-support-test/.../auto/ext/http/standardapi/IntegrationLookupApiTest.java` | 新增 |
| `paytech-platform-support-test/.../auto/ext/http/internalauth/IntegrationLookupInternalAuthTest.java` | 新增 |
| `paytech-platform-support-test/src/test/resources/application-mock.yml` | 修改：签发端测试调用方白名单增加 `integration-lookup-test → platform-support-service / GENERAL` |

## 4. 测试

命令（在 worktree 的 `paytech/` 下；worktree 没有 `local-environment/.env.local`，通过系统属性指向主工作区的本地环境目录，只读）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./mvnw -q -pl platform-support/paytech-platform-support-test -am \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment \
  -Dtest=IntegrationLookupApiTest,IntegrationLookupInternalAuthTest,InternalAuthDualNotificationTest,InternalAuthDualOutboundTest,InternalAuthDualWebhookTest,InternalAuthRequiredExemptionTest,InternalAuthRequiredNotificationTest,InternalAuthRequiredWebhookTest,InternalJwksTest,InternalTokenIssueTest,StandardApiExecutionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：exit 0；11 个类 37 个用例，0 失败 / 0 错误 / 0 跳过。

- `IntegrationLookupApiTest`（OFF）：存在 → 200 `exists=true` 且响应只有 `integrationCode`、`exists` 两个字段；不存在 → 200 `exists=false`；非法编码（小写、数字开头、单字符、65 字符）→ **400 INVALID_REQUEST**（见遗留 1）。
- `IntegrationLookupInternalAuthTest`（REQUIRED）：合法 Token → 200；无 Token → 401 `INTERNAL_TOKEN_INVALID`；篡改签名 → 401。方式与 `InternalAuthRequiredNotificationTest` 相同。
- 回归：其余 internal-auth 测试类与 `StandardApiExecutionTest` 全部通过（白名单新增项不影响签发测试）。

**未完成：** Platform 测试模块全量 auto 回归。
1. 第一次不带 `-Dtest` 运行时，`-am` 也跑了 Foundation 上游测试，`paytech-redis-starter` 的 `ResourceLockRedisIntegrationAutoTest`、`RedisStarterIntegrationAutoTest` 失败（113 个用例中 2 个失败，原因是 worktree 中缺少本地 Redis 凭据，与本次改动无关），构建在 Platform 测试之前就停止了。
2. 改用 `'-Dtest=%regex[com/paytech/platform/support/auto/.+Test.class]'` 只跑 Platform auto，已完成约 44 个测试类报告，然后按用户暂停指令中止（TaskStop），没有拿到最终结果，已确认没有残留 JVM。中止时正在执行的用例可能没来得及运行 AfterEach，本地 `mor_platform` 库里可能留下少量随机 ID 的测试数据；下次运行时全局基线检查会把它报出来。

## 5. 第 11 节核对

- API/Body：合同已给定（class、中文 JavaDoc、`@Pattern`），触发点是 MVC 内置方法校验；Controller 未加 `@Validated`，否则会走 AOP 抛 `ConstraintViolationException`，换成另一套映射。
- Controller 仅适配；纯查询委托一个本地 Service，按规范省略 Processor。
- Processor/Context/锁/原请求比较/业务防重/幂等：只读查询，不适用。
- Service 不调用其他 Service 或外部依赖；事务：无，且无外部调用。
- Repository：复用 `byCode`，DataObject 没有越出 integration 模块（Service 返回 boolean）。
- View：单表查询，不需要。
- 没有生产 Mock、没有新增生产配置、没有多余抽象；测试只改测试侧配置。
- 验证：见第 4 节；全量回归未完成。

## 6. 遗留

1. **422 与 400 不一致（需主会话决定）：** 合同 JavaDoc 和任务卡写的是"编码格式不合法 422"。但 Foundation `MvcProblemExceptionMapper` 按全系统 RFC 9457 规范，只有请求体字段错误才映射为 422；路径参数校验失败一律映射为 **400 INVALID_REQUEST**。实测结果就是 400。测试按实际行为断言 400。建议把合同 JavaDoc 改成"返回 400 INVALID_REQUEST"（我没改合同）；channel-connector 调用方也应按 400 处理。
2. 部署时 internal-auth 非 OFF 的环境，需要在签发端 `internal-token.issuer.callers` 给真实调用方（channel-connector 的服务名）加上 `targets: [platform-support-service]`，否则它申请不到 Token。按约束没有碰 Nacos 和 mor-test。
3. Platform 全量 auto 回归需要在恢复后重跑（用第 4 节第 2 点的 regex 命令）。
4. 测试模块 README 没有补充本接口的运行说明（任务卡没要求）。
