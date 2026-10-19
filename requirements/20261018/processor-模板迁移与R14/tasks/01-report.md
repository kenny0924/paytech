# 任务卡 01 报告 · Processor 统一到 Foundation 模板与 R14

- 分支：`refactor/processor-template-r14`（起点 `main@4a32a2c0`）；提交见第 6 节。
- 结论：client-master 原 5 个类拆为 13 个入口 Processor，platform-support 网关配置拆为 11 个、审核下载改为继承模板；acquiring 死代码 `AbstractGatewayProcessor` 已删除；新增 R14 并纳入 `ArchitectureRules.all()`。`known-deviations.yaml` 无 R14 条目，main 代码（Foundation 除外）`@ResourceLock` 为 0。
- 统计更正：`ReadOnboardingFileProcessor`、`DownloadOnboardingFileProcessor` 在 main 已经通过 `AbstractOnboardingFileAccessProcessor extends AbstractProcessor` 挂到模板，本次未改动，R14 实测通过。核心服务真实未继承的是 7 个类。

## 1. 已读与实施前说明

- 已读：`requirement.md`、本任务卡；`DEVELOPMENT_CONVENTIONS.md` 第 4、5 节；`WRITE_API_EXAMPLE.md`；`UNIT_TEST_GUIDE.md`（重点 7.2 锁验证）；Foundation `AbstractProcessor`、`AbstractLockProcessor`、`ResourceLockKeyResolver`、`ResourceLockExecutor`；`tools/arch-rules` 全部规则、`ServiceArchitectureScope`、`known-deviations.yaml`、design/02；参照实现 `OnboardingFileCompletionProcessor` + `OnboardingFileLocks`、`ChangeSubjectTagStatusProcessor` + `DecisionResourceLocks`，以及 client-master 已迁移的 `SubmitClientApplicationProcessor`。
- 本地原子写入与事务外步骤：未改变。各入口仍在原 `TransactionTemplate`（CCT 为 READ COMMITTED 写 / REPEATABLE READ 只读，Platform 为 SERIALIZABLE 写 / 一致性读）内调用原 Service；Platform 会话核验、Vault 保护与 Reveal、commercial 合同引用、Risk 材料要求与 Platform 文件核验、存储签名都留在事务外，顺序与拆分前逐行一致。
- 迁移原则：保持原调用顺序。输入规范化原先在注解锁内执行，迁移后仍在 `processWithinLock` 内，不前移到 `validate`，因此“锁竞争 + 非法输入”仍先返回 409。

## 2. 逐个 Processor 前后对照

### 2.1 client-master（锁参数沿用 `RedisResourceLockProperties` 500ms / 30s，经装配类注入，与 `SubmitClientApplicationProcessor` 同一做法）

| 入口 | 迁移前 | 迁移后 Processor（模板） | 锁 Key：注解 `prefix:key` → `LockCommand.tryLocks` | Request / Response |
|---|---|---|---|---|
| POST /applications | `ClientDraftProcessor.create` | `CreateClientApplicationProcessor`（Lock） | `client-onboarding:account-slot:{accountId}` → `ClientOnboardingLocks.accountSlot` | `CreateClientApplicationRequest` / `CommandReceiptResponseBody` |
| GET /start | `ClientDraftProcessor.start` | `GetClientOnboardingStartProcessor`（无锁） | 无 | `GetClientOnboardingStartRequest` / `StartResponseBody` |
| GET /applications/{id} | `ClientDraftProcessor.read` | `GetClientDraftProcessor`（无锁） | 无 | `GetClientDraftRequest` / `ClientDraftResponseBody` |
| PATCH /{id}/draft | `ClientDraftProcessor.save` | `SaveClientDraftProcessor`（Lock） | `client-onboarding:application:{id}` | `SaveClientDraftRequest` / `CommandReceiptResponseBody` |
| POST /{id}/people | `ClientPeopleProcessor.createPerson` | `CreateClientPersonProcessor`（Lock） | 同上申请 Key | `CreateClientPersonRequest` / `PeopleCommandReceiptResponseBody` |
| PATCH /{id}/people/{partyId} | `ClientPeopleProcessor.savePerson` | `SaveClientPersonProcessor`（Lock，两把） | 申请 Key + `cct:party:{partyId}` → `applicationAndParty`，模板去重排序、共享等待预算 | `SaveClientPersonRequest` / 同上 |
| POST /{id}/relations | `ClientPeopleProcessor.createRelation` | `CreateClientRelationProcessor`（Lock，两把） | 申请 Key + `cct:party:{relatedPartyId}` | `CreateClientRelationRequest` / 同上 |
| PATCH /{id}/relations/{rid} | `ClientPeopleProcessor.saveRelation` | `SaveClientRelationProcessor`（Lock） | 申请 Key | `SaveClientRelationRequest` / 同上 |
| DELETE /{id}/relations/{rid} | `ClientPeopleProcessor.removeRelation` | `RemoveClientRelationProcessor`（Lock） | 申请 Key | `RemoveClientRelationRequest` / `Void` |
| POST /{id}/documents | `ClientDocumentProcessor.associate` | `AssociateClientDocumentProcessor`（Lock） | 申请 Key | `AssociateClientDocumentRequest` / `DocumentCommandReceiptResponseBody` |
| DELETE /{id}/documents/{did} | `ClientDocumentProcessor.remove` | `RemoveClientDocumentProcessor`（Lock） | 申请 Key | `RemoveClientDocumentRequest` / `Void` |
| GET /form-options | `ClientFormOptionsProcessor.options` | `GetClientFormOptionsProcessor`（无锁） | 无 | `GetClientFormOptionsRequest` / `ClientFormOptions` |
| GET /client-workspace/v1/client-summaries | `ClientWorkspaceProcessor.summaries` | `GetClientWorkspaceSummariesProcessor`（无锁） | 无 | `GetClientWorkspaceSummariesRequest` / `List<ClientWorkspaceSummary>` |

- 共用步骤放在包内 `ClientOnboardingCommands`：账号名额唯一键 `uk_client_app_account_active` 回滚重试（`duplicateRace`，最多重试两次，create 与 save 都保留），关系唯一键转 `RELATION_ALREADY_EXISTS`，材料唯一键转 `DOCUMENT_ALREADY_EXISTS`，以及人员补丁规范化。
- `INVALID_DRAFT` 字段级 `errors[]`：`clientType` 校验与 `ClientDraftPatch.normalize` 原样保留，仍在会话核验之后执行。
- 两个只读类原先自查事务并抛 `IllegalStateException`，现由模板统一拒绝，抛 `ErrorException`，同为 500。
- `SubmitClientApplicationProcessor` 改用 `ClientOnboardingLocks.application`，Key 字符串不变。

### 2.2 platform-support（锁参数放 `GatewayConfigurationLocks`：等待 500ms、租约 30s，与本服务注解锁配置一致）

| 入口 | 迁移前 | 迁移后 Processor（模板） | 锁 | Request / Response |
|---|---|---|---|---|
| GET /target-services | `targets` | `QueryGatewayTargetsProcessor`（无锁） | 无 | `TargetPageQuery` / `Page<TargetSummaryResult>` |
| GET /target-services/{code} | `target` | `GetGatewayTargetProcessor` | 无 | `TargetDetailQuery` / `TargetDetailResult` |
| PATCH /target-services/{code}/status | `changeTargetStatus` | `ChangeGatewayTargetStatusProcessor`（Lock） | `gateway-configuration:catalog` | `ChangeGatewayTargetStatusRequest` / `TargetStatusResult` |
| GET /routes | `routes` | `QueryGatewayRoutesProcessor` | 无 | `RoutePageQuery` / `Page<RouteSummaryResult>` |
| GET /routes/{code} | `route` | `GetGatewayRouteProcessor` | 无 | `RouteDetailQuery` / `RouteDetailResult` |
| PATCH /routes/{code}/status | `changeRouteStatus` | `ChangeGatewayRouteStatusProcessor`（Lock） | 同上 | `ChangeGatewayRouteStatusRequest` / `RouteStatusResult` |
| POST /target-services | `createTarget` | `CreateGatewayTargetProcessor`（Lock） | 同上 | `CreateGatewayTargetRequest` / `ConfigurationMutation` |
| PUT /target-services/{code} | `updateTarget` | `UpdateGatewayTargetProcessor`（Lock） | 同上 | `UpdateGatewayTargetRequest` / `ConfigurationMutation` |
| POST /routes | `createRoute` | `CreateGatewayRouteProcessor`（Lock） | 同上 | `CreateGatewayRouteRequest` / `ConfigurationMutation` |
| PUT /routes/{code} | `updateRoute` | `UpdateGatewayRouteProcessor`（Lock） | 同上 | `UpdateGatewayRouteRequest` / `ConfigurationMutation` |
| GET /capabilities | `capabilities` | `GetGatewayConfigurationCapabilitiesProcessor`（无锁，原本也不开事务） | 无 | `GatewayConfigurationCapabilitiesRequest` / `Capabilities` |
| GET 审核材料下载 | `ReviewFileDownloadProcessor.download`（普通 Bean） | `ReviewFileDownloadProcessor extends AbstractProcessor` | 无 | `ReviewFileDownloadRequest` / `ReviewFileDownloadResponse` |

- 原 6 处 `@ResourceLock(prefix = "gateway-configuration:catalog")` 没有 key，逻辑 Key 就是前缀本身。现在统一为 `LockCommand.tryLocks(List.of("gateway-configuration:catalog"), 500ms, 30s)`，与参照实现一样使用普通 RLock 和完整逻辑 Key，Namespace 只由 NameMapper 添加一次。
- 状态与版本规范化、`permissionId` 解析、SERIALIZABLE 写事务的唯一键与并发异常转换，原样移到包内 `GatewayConfigurationWrites`。
- 结果到 ResponseBody 的投影原样移到 app 层 `GatewayConfigurationManagementConverter`，Controller 只构造 Request 并转换。查询结果的投影从事务内移到事务后，投影是纯内存计算，读取内容不变。
- 审核下载的 Controller 把内部 Response 的 long 转成十进制字符串，Response 的 `toString` 已脱敏。

### 2.3 acquiring

- `AbstractGatewayProcessor` 已删除，代码引用为 0；只有历史需求文档和 `CURRENT_IMPLEMENTATION.md` 第 1509 行仍以文字提到它。

## 3. 第 11 节核对

- API 与 Body：未改。路径、`@Valid` 与 Body 校验保持原样；内部 Request 使用 class 与 `@Positive`。入口已保证这些 ID 是规范正整数，`validateBasic` 不会改变原有错误。
- Controller 与 Converter 只做适配：原来的 Caller、账号、ID 解析和 HTTP 状态（201/204）保持不变。每个写入口一个 Processor，位于 `-core` 的 `service/processor`。
- Processor 查询：只用本模块 Repository（SaveClientPerson 读身份）和原 Service，没有 Mapper 或逐表写入。
- Context：本次不涉及，原 Service 的参数形态未改。
- Service：未修改，没有新增 Service 调用 Service。
- 事务位置：同第 1 节，事务内没有外部调用或 Processor 调用。
- Repository 与条件 SQL：未改。唯一键、版本与 SERIALIZABLE 等数据库保护全部保留。
- 锁：针对实际资源，Key 与同资源的其他入口一致；原注解锁已全部移除，没有叠加两种锁机制。由第 4 节的竞争测试证明。
- 原请求比较与重放：沿用原 Service 行为，即重复关系和材料返回原记录、重复删除仍返回 204。`OnboardingDuplicateWriteTest` 在本次运行中通过。
- Gateway 幂等与业务防重：未接入命令幂等组件，也没有引入 Idempotency-Key。
- View 与 DataObject 边界：未新增 View。工作区摘要仍在本服务 app 层投影 DataObject，与迁移前一致。
- 公共包与配置：复用 Foundation 模板；锁参数分别沿用两个服务既有的配置或常量，没有新增配置项。
- 测试：见第 4 节。没有使用 `when().thenReturn` 或 `@MockBean`；锁竞争使用真实本地 Redis，由测试线程持有同一 Key。

## 4. 测试

### 4.1 测试先行

两个新测试在迁移前先运行，证明它们固定的是原注解锁的互斥域。

| 测试 | 迁移前 | 内容 |
|---|---|---|
| `OnboardingResourceLockTest` | 4/4 通过 | 申请锁：草稿、人员、关系、材料共 6 个写入口返回 409 且数据不变。人员锁：保存人员与新建关系返回 409。账号名额锁：开始申请返回 409。账号名额锁被占用时开始页仍为 200。 |
| `GatewayConfigurationLockTest` | 2/2 通过 | Catalog 锁被占用时 6 个写入口返回 409，释放后版本与状态均未变化。查询与能力接口在锁占用期间仍为 200。 |
| `ProcessorTemplateRuleAutoTest` | 先写测试与 fixture | 正例：直接继承、经锁模板间接继承、抽象类、非 Processor 命名、其他包、-app 模块。反例：2 条。BFF 排除。 |

### 4.2 定向验证

验证命令为任务卡第 5 条，未跑全 Reactor。

结果：`BUILD SUCCESS`，用时 16:41。各模块计数取自 surefire 报告，全部 0 失败、0 错误、0 跳过。

| 模块 | 用例数 |
|---|---|
| tools/arch-rules | 60（含 R14 规则测试 3 条） |
| tools/arch-rules-reactor-test | 3 |
| client-master-test | 67（含 `OnboardingResourceLockTest` 4 条、`OnboardingDuplicateWriteTest` 4 条，以及 `ArchitectureAutoTest` 含 R14） |
| client-console-bff-test | 81 |
| platform-support-test | 878（含 `GatewayConfigurationLockTest` 2 条，以及 `ArchitectureAutoTest` 含 R14） |
| operations-console-bff-test | 1390 |
| api-gateway-test | 1306 |
| acquiring-test | 804 |

另外核查了两项：
- `node scripts/verify-markdown-links.mjs`：本次删除类导致的两个失效链接已修正。剩余 1 个失效链接 `tasks/14-stage5-report.md:170` 为既有问题，与本任务无关。
- `requirements-site` 测试：worktree 无 node_modules，未执行。

## 5. R14 规则范围

- 规则类：`tools/arch-rules/.../rule/ProcessorTemplateRule.java`，编号 R14，已加入 `ArchitectureRules.all()`，`ArchitectureRulesAutoTest` 的编号清单与 design/02 表已加 R14 行。
- 检查对象：被检服务、`ModuleIdentity.isServiceModule()` 且角色为 CORE 的模块；`..service.processor..` 包；类名以 `Processor` 结尾；非抽象、非接口、非匿名或局部类。条件是 `isAssignableTo(com.paytech.foundation.processor.AbstractProcessor)`，`AbstractLockProcessor` 子类间接满足。
- 排除：`client-console-bff`、`operations-console-bff`、`partner-console-bff` 按服务名排除，原因写在 Javadoc：它们是页面聚合层，本期由用户另行安排。Foundation 不是服务模块，自然排除。`-app`、`-api`、`-test` 模块不检查。
- 迁移前用源码扫描全部服务 `-core`：330 个具体 `*Processor` 中只有本次 7 个不满足。`known-deviations.yaml` 不新增 R14 条目。

## 6. 提交

按 client-master、platform-support、R14 与收尾三批提交，哈希见本报告所在提交的前两个父提交及主会话回复。

## 7. 待确认

1. **内部 Response 形态。** client-master 的写入口沿用 Service 已生成的 API 回执作为 Processor Response，读入口沿用 core 内 `ClientDraftConverter` 生成的 ResponseBody。另建同义内部 Response 只会多一次往返转换。若要求 Service 返回领域结果、由 app Converter 投影，需要单独任务改 Service 签名。Platform 网关配置使用领域结果作为 Response，与 Risk 参照实现一致。
2. **409 的提示文字。** 锁竞争的 `detail` 由注解锁的“该资源正在处理其他请求，请稍后重试”变为模板的“当前资源正在执行”。错误码 `CONCURRENT_REQUEST` 与 409 不变，与此前 Platform 入驻文件、Risk、C14 的迁移一致。注解锁按前缀上报的竞争与获取观测，模板没有对应上报。
3. **注解锁基础设施的去留。** client-master 与 platform-support 已无 `@ResourceLock` 消费者，`redis.coordination.resource-lock` 配置继续作为 Wait/Lease 来源（client-master）或保持原样（platform）保留。是否关闭注解锁自动配置留待 Foundation 决定。
4. **`CURRENT_IMPLEMENTATION.md` 未更新。** 第 1509 行“所有 Processor 继承 `AbstractGatewayProcessor`”已过时。台账由主会话维护，本任务未改，避免与并行会话冲突。
5. **`GetClientFormOptionsProcessor` 的类型解析。** 它仍按 `ClientType.valueOf`（枚举 name）解析客户类型，与开发规范第 4 节“禁止依赖 name()”不符。这是原有行为，本次未改。

## 8. 合并门禁记录（主会话，2026-10-19）

- 合并方式：`main`（4c7892a8）与 `refactor/processor-template-r14`（45b79b76）在隔离 worktree 以 `--no-ff` 合并，无冲突；两侧无共同改动文件。合并期间主工作区 `ReviewFileDownloadProcessor.java` 有一处仅重排 `@param` 换行的未提交格式改动，该方法已被 `processRequest` 取代，补丁另存，不进入提交。
- 复核：合并基线以来 `main` 新增的核心服务 `*Processor` 均已继承模板，R14 在合并树上无新增违例；全 Reactor `clean verify` 结果见 `delivery.md`。
- 第 7 节待确认项裁决：
  1. 内部 Response 形态——接受。写入口沿用 Service 回执、读入口沿用 core 内 ResponseBody，不另建同义 Response；如需 Service 返回领域结果再单独立项。
  2. 409 提示文字——接受。错误码与状态不变，与 Platform 入驻文件、Risk、C14 迁移一致。
  3. 注解锁自动配置去留——不在本任务裁决，登记为 Foundation 遗留项。
  4. `CURRENT_IMPLEMENTATION.md` 第 1533 行“所有 Processor 继承 `AbstractGatewayProcessor`”——确认过时（仓库内已无该类）。该文件当前被并行会话占用，台账修订顺延到其提交之后，由主会话单独提交。
  5. `GetClientFormOptionsProcessor` 按 `ClientType.valueOf` 解析——登记为小型遗留项，改为按 code 解析时需补 400 用例，另开任务卡。
