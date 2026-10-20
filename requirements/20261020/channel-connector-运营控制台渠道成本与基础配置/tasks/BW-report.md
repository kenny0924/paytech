# BW 交付报告 · Operations BFF + Web 渠道控制台 + Platform 注册

- 日期：2026-10-20；执行：BW（Opus 5.5 agent），worktree `/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/bw-channel`（基线 4d8f4ec3）。
- 状态：BFF、Web、Platform 注册三部分完成，未提交、未部署；没有执行 git 写命令，没有调用 Superpowers skill，没有接触 mor-test / Nacos。
- 路由：BFF 31 个端点与块 92 的 31 条路由逐条一致。任务卡写的 30 条不含版本对比；BW 提出后主会话已把第 31 条 `operations-channel-cost-version-diff`（ID 900000000000092431）补进 `registration-dml.sql`，BFF 路径与之相同。

## 1. 已读清单

- 任务卡 `tasks/BW.md`；`technique.md` 第 3、7、8、8.1、10 节；`db/platform-support-service/registration-dml.sql`（含第 31 条）；Pencil 15 张画板由 Web 分线逐张读取。
- 规范：`AGENTS.md`「接口开发必读与自查」，`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文，含第 3 节 BFF 聚合与公共分页），`agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 3.3、4.2 节，`agent-docs/UNIT_TEST_GUIDE.md` 第 2、8–14、16–20 节，`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节。
- 商务参照源码：`CommercialClientConfiguration`、`CommercialRequestConfiguration`、`CommercialPricingClient(+Impl)`、`CommercialContractConverter`、`CommercialConsoleQueryClientImpl`（分页回执核对）、`CommercialPricingController`、`OperatorContextArgumentResolver`、`CommercialTermVersionDiffProcessor`、`CommercialConcurrentReads`、`OperationsErrorStatusResolver`；Foundation `HttpServiceClientInterceptor`（登记合同码默认 400）、`MvcProblemExceptionMapper`、`HttpRequestResponseLoggingFilter`（metadata-only 前缀按路径段匹配）。
- 测试参照：`CommercialOwnerSimulator`、`OperationsBffTestConfiguration`、`OperationsBffHttpTestBase`、`CommercialBrowserAutoTestBase`、`commercial-admin-boundary.json`、`CommercialBrowserAutoTest`、`CommercialAggregationAutoTest`、`PlatformOperationsSimulator`、`OperationsConsoleBffApplicationAutoTest`、`OperationsHttpBodyNamingAutoTest`、`RiskListBrowserAutoTest`（日志捕获）。
- channel-connector api 合同：`PaymentChannelApi`、`ChannelAccountApi`、`ChannelMidApi`、`MidCapabilityApi`、`ChannelConfigChangeApi`、`ChannelCostPlanApi`、`ChannelConnectorHeaders`、`ChannelCostErrorCode` 与全部 request / response DTO。`api/change` 包起初未同步到本 worktree，已报主会话并由其同步，内容与 CC-A worktree 一致。

## 2. BFF 分层落点

BFF 不持有业务事实，不编排跨系统事务，也没有本地事务。所有写命令都是一次 channel-connector 调用，不重试。

| 层 | 类 | 职责 |
|---|---|---|
| configuration | `ChannelConnectorClientConfiguration` | 发现 `channel-connector-service`，测试属性为 `operations.channel-connector-test-base-url`；注册 6 个 Api Bean；开启 `withOwnerProblemDetail()`；登记 `ChannelCostErrorCode.values()`（api 模块里唯一的 CodeMessageEnum）；超时、请求头与依赖故障映射同商务 |
| configuration | `ChannelRequestConfiguration` | 对 `/channel-admin/**` 严格解析 `ChannelModels` 内的 `*RequestBody`，上限 65536；GET 与无 Body 命令带 Body 时返回 400 |
| configuration | `OperationsErrorStatusResolver` | `ChannelCostErrorCode` 固定为 400 |
| ext/rest | `ChannelConfigurationController`（21 条）、`ChannelCostController`（9 + 对比） | 协议适配；`requireQueryKeys` 只放行块 92 登记的 Query；分页经 `PagingRequestBody.of` 校验 |
| integration | `ChannelConfigurationClient(+Impl)`、`ChannelCostClient(+Impl)`、`ChannelContractConverter` | 页面模型严格转为 Owner DTO；渠道配置命令把 `operator.accountId()` 填入 `X-Gateway-Actor-Subject`；成本命令把它填入 `makerId`、`checkerId` 或 `operatorId`；回执核对身份与状态，分页核对 page、size 与 total；422 字段投影复用 `ConfigurationValidationConverter` |
| service/processor | `ChannelCostVersionComparisonProcessor`（`AbstractProcessor`）+ `request/ChannelCostVersionComparisonRequest` | 用 `CommercialConcurrentReads.all` 并发读取两版；范围按六维对齐，同键多个按 scopeNo 逐个配对；组成按 componentCode 对齐；十进制按数值比较；输出 added、removed、changed（含组成增删与字段差异）和 unchanged |
| service/model | `ChannelModels` | 普通 class，`*RequestBody` / `*ResponseBody`，字段与 Owner 一一对应；请求约束与 Owner 一致，不含操作人字段 |
| resources | `application.yml` | 新增 `/channel-admin/v1/channel-accounts` 为 metadata-only 前缀，因为账号配置请求含凭据明文 |

## 3. 路由对照表（31 条）

BFF 路径等于块 92 的 `downstream_path_template`；外部路径为 `/api/operations` 加上该路径。`OperationsConsoleBffApplicationAutoTest` 断言 BFF 的路由集合与这 31 条完全相等；Query 白名单与块 92 的 `t_gateway_route_query_param` 逐条一致，未列出 Query 的路由拒绝一切 Query。

| # | 方法 | BFF 路径（= 块 92 downstream） | 块 92 Route Code / 权限 | Query 白名单 | Owner 合同 |
|---|---|---|---|---|---|
| 01 | GET | `/channel-admin/v1/channels` | `operations-channel-channel-list` / CHANNEL_CONFIG_READ | status, keyword, page, size | PaymentChannelApi.listChannels |
| 02 | GET | `/channel-admin/v1/channels/{channelId}` | `operations-channel-channel-detail` / CHANNEL_CONFIG_READ | 无 | PaymentChannelApi.getChannel |
| 03 | POST | `/channel-admin/v1/channels` | `operations-channel-channel-create` / CHANNEL_CONFIG_MANAGE | 无 | PaymentChannelApi.createChannel |
| 04 | PUT | `/channel-admin/v1/channels/{channelId}` | `operations-channel-channel-update` / CHANNEL_CONFIG_MANAGE | 无 | PaymentChannelApi.updateChannel |
| 05 | POST | `/channel-admin/v1/channels/{channelId}/status` | `operations-channel-channel-status` / CHANNEL_CONFIG_MANAGE | 无 | PaymentChannelApi.changeChannelStatus |
| 06 | GET | `/channel-admin/v1/channel-accounts` | `operations-channel-account-list` / CHANNEL_CONFIG_READ | channelId, entityNo, status, page, size | ChannelAccountApi.listChannelAccounts |
| 07 | GET | `/channel-admin/v1/channel-accounts/{channelAccountId}` | `operations-channel-account-detail` / CHANNEL_CONFIG_READ | 无 | ChannelAccountApi.getChannelAccount |
| 08 | POST | `/channel-admin/v1/channel-accounts` | `operations-channel-account-create` / CHANNEL_CONFIG_MANAGE | 无 | ChannelAccountApi.createChannelAccount |
| 09 | PUT | `/channel-admin/v1/channel-accounts/{channelAccountId}` | `operations-channel-account-update` / CHANNEL_CONFIG_MANAGE | 无 | ChannelAccountApi.updateChannelAccount |
| 10 | PUT | `/channel-admin/v1/channel-accounts/{channelAccountId}/configuration` | `operations-channel-account-configuration` / CHANNEL_ACCOUNT_CREDENTIAL | 无 | ChannelAccountApi.configureChannelAccount |
| 11 | POST | `/channel-admin/v1/channel-accounts/{channelAccountId}/status` | `operations-channel-account-status` / CHANNEL_CONFIG_MANAGE | 无 | ChannelAccountApi.changeChannelAccountStatus |
| 12 | GET | `/channel-admin/v1/mids` | `operations-channel-mid-list` / CHANNEL_CONFIG_READ | channelId, channelAccountId, entityNo, status, page, size | ChannelMidApi.listChannelMids |
| 13 | GET | `/channel-admin/v1/mids/{midId}` | `operations-channel-mid-detail` / CHANNEL_CONFIG_READ | 无 | ChannelMidApi.getChannelMid |
| 14 | POST | `/channel-admin/v1/mids` | `operations-channel-mid-create` / CHANNEL_CONFIG_MANAGE | 无 | ChannelMidApi.createChannelMid |
| 15 | PUT | `/channel-admin/v1/mids/{midId}/account` | `operations-channel-mid-rebind-account` / CHANNEL_CONFIG_MANAGE | 无 | ChannelMidApi.rebindChannelMidAccount |
| 16 | POST | `/channel-admin/v1/mids/{midId}/status` | `operations-channel-mid-status` / CHANNEL_CONFIG_MANAGE | 无 | ChannelMidApi.changeChannelMidStatus |
| 17 | GET | `/channel-admin/v1/mid-capabilities/definitions` | `operations-channel-capability-definitions` / CHANNEL_CONFIG_READ | 无 | MidCapabilityApi.listCapabilityDefinitions |
| 18 | POST | `/channel-admin/v1/mid-capabilities` | `operations-channel-capability-create` / CHANNEL_CONFIG_MANAGE | 无 | MidCapabilityApi.createMidCapability |
| 19 | PUT | `/channel-admin/v1/mid-capabilities/{midCapabilityId}/details` | `operations-channel-capability-details` / CHANNEL_CONFIG_MANAGE | 无 | MidCapabilityApi.replaceMidCapabilityDetails |
| 20 | POST | `/channel-admin/v1/mid-capabilities/{midCapabilityId}/status` | `operations-channel-capability-status` / CHANNEL_CONFIG_MANAGE | 无 | MidCapabilityApi.changeMidCapabilityStatus |
| 21 | GET | `/channel-admin/v1/config-changes` | `operations-channel-change-list` / CHANNEL_CHANGE_READ | targetType, targetId, page, size | ChannelConfigChangeApi.listConfigChanges |
| 22 | GET | `/channel-admin/v1/channel-cost-plans` | `operations-channel-cost-plan-list` / CHANNEL_COST_READ | channelId, status, page, size | ChannelCostPlanApi.listChannelCostPlans |
| 23 | GET | `/channel-admin/v1/channel-cost-plans/{planCode}` | `operations-channel-cost-plan-detail` / CHANNEL_COST_READ | 无 | ChannelCostPlanApi.getChannelCostPlan |
| 24 | GET | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}` | `operations-channel-cost-version-detail` / CHANNEL_COST_READ | 无 | ChannelCostPlanApi.getChannelCostVersion |
| 25 | POST | `/channel-admin/v1/channel-cost-plans` | `operations-channel-cost-plan-create` / CHANNEL_COST_MANAGE | 无 | ChannelCostPlanApi.createChannelCostPlan |
| 26 | POST | `/channel-admin/v1/channel-cost-plans/{planCode}/versions` | `operations-channel-cost-version-create` / CHANNEL_COST_MANAGE | 无 | ChannelCostPlanApi.createChannelCostVersion（BFF 补 makerId） |
| 27 | POST | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/submit` | `operations-channel-cost-version-submit` / CHANNEL_COST_MANAGE | 无 | ChannelCostPlanApi.submitChannelCostVersion（BFF 补 makerId，页面无 Body） |
| 28 | POST | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/approve` | `operations-channel-cost-version-approve` / CHANNEL_COST_APPROVE | 无 | ChannelCostPlanApi.approveChannelCostVersion（BFF 补 checkerId） |
| 29 | POST | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/reject` | `operations-channel-cost-version-reject` / CHANNEL_COST_APPROVE | 无 | ChannelCostPlanApi.rejectChannelCostVersion（BFF 补 checkerId） |
| 30 | POST | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{costVer}/cancel` | `operations-channel-cost-version-cancel` / CHANNEL_COST_MANAGE | 无 | ChannelCostPlanApi.cancelChannelCostVersion（BFF 补 operatorId） |
| 31 | GET | `/channel-admin/v1/channel-cost-plans/{planCode}/versions/{baseCostVer}/diff/{targetCostVer}` | `operations-channel-cost-version-diff` / CHANNEL_COST_READ | 无 | 两次 ChannelCostPlanApi.getChannelCostVersion → ChannelCostVersionComparisonProcessor |

## 4. 改动文件

worktree 内共 45 个文件，其中 BFF 25 个、Web 16 个、Platform 2 个、BFF 与父 pom 2 个。api 模块文件由主会话同步，不计入。

| 范围 | 文件 |
|---|---|
| BFF pom | `paytech/operations-console-bff/pom.xml`、`.../paytech-operations-console-bff-app/pom.xml`（依赖 `paytech-channel-connector-api`） |
| BFF 新增（12） | `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/configuration/ChannelConnectorClientConfiguration.java`、`ChannelRequestConfiguration.java`；`ext/rest/ChannelConfigurationController.java`、`ChannelCostController.java`；`integration/ChannelConfigurationClient.java`、`ChannelCostClient.java`、`impl/ChannelConfigurationClientImpl.java`、`impl/ChannelCostClientImpl.java`、`impl/ChannelContractConverter.java`；`service/model/ChannelModels.java`；`service/processor/ChannelCostVersionComparisonProcessor.java`、`request/ChannelCostVersionComparisonRequest.java` |
| BFF 修改（3） | `OperationsErrorStatusResolver.java`、`application.yml`、`README.md`（「渠道」一节，D26 目标服务列表补 commercial-service 与 channel-connector-service） |
| BFF 测试新增（5） | `paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/.../test/simulator/ChannelConnectorOwnerSimulator.java`、`test/base/ChannelBrowserAutoTestBase.java`、`auto/ChannelBrowserAutoTest.java`、`auto/ChannelAggregationAutoTest.java`、`resources/channel-admin-boundary.json`（30 条转发样例，路径、方法、Query 由脚本对照块 92 生成并校验） |
| BFF 测试修改（5） | `OperationsBffTestConfiguration.java`、`OperationsBffHttpTestBase.java`（`@DynamicPropertySource`）、`PlatformOperationsSimulator.java`（31 条 `operations-channel-*` 路由权限）、`OperationsConsoleBffApplicationAutoTest.java`（路由集合与日志前缀）、`OperationsHttpBodyNamingAutoTest.java` |
| Web 新增（12） | `operations-console-web/src/channel/channel.ts`、`channel.test.ts`、`channel.css`、`ChannelParts.tsx`、`useChannelCommand.ts`、`AccountConfigurationModal.tsx`、`CapabilityEditor.tsx`、`CostVersionEditor.tsx`、`CostVersionDiff.tsx`；`src/pages/ChannelChannels.tsx`、`ChannelMids.tsx`、`ChannelCostPlans.tsx` |
| Web 修改（4） | `src/navigation/pageCapabilities.ts`（3 页）、`ConsolePage.tsx`（3 个 lazy）、`menu.ts`（`pageRoutePaths` 4 条详情路径）、`src/configuration/configurationModel.test.ts`（42 改为 45） |
| Platform（2） | `paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json`（3 页，与 `pageCapabilities.ts` 一致）；`docs/database/sql/platform-support-initial-data.sql`（块 93 之后追加块 92 原文，追加段 SHA-256 与 `registration-dml.sql` 相同：`78590b27…b3a4c`） |

## 5. 测试命令与结果

均在 JDK 25 下实际执行（`export JAVA_HOME=$(/usr/libexec/java_home -v 25)`）。

```
cd paytech && ./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am test
```

结果为 BUILD SUCCESS。BFF 测试模块 1710 项通过，失败、错误、跳过均为 0；其中渠道新增 76 项：`ChannelBrowserAutoTest` 71 项，`ChannelAggregationAutoTest` 5 项。`ArchitectureAutoTest`、路由清单与命名测试一并通过。

```
npm --prefix operations-console-web test && npm --prefix operations-console-web run build
```

Web 测试 1135 项通过，0 失败，其中 `channel.test.ts` 10 项。构建通过，包括 tsc；Vite 提示单 chunk 超过 500 kB，这是既有警告。worktree 没有 `node_modules`，运行时临时链接了主工作区的 `node_modules`（package-lock 相同），运行后已删除链接与 `dist/`。

Platform access-control 模块没有测试源码，Platform 其他测试也不引用 `console-resource-capabilities.json`。Web 分线按 `ConsoleResourceCapabilities` 的加载规则用脚本核对：componentKey 与按钮码唯一，3 个新条目与 `pageCapabilities.ts` 及块 92 的 `t_console_resource` 一致。

BFF 测试覆盖的场景：

- **转发与授权：** 30 条转发样例逐条验证方法、路径、Query、Owner Body 与响应原样；PLT-I02 拒绝时 Owner 零调用。
- **操作人与请求头：** 命令带 `X-Gateway-Actor-Subject`，成本命令改为在 Body 中补入操作人，查询都不带；Authorization、Cookie、Session 与 Idempotency-Key 不转发。
- **权限分离：** COST_MANAGE 不能批准或驳回，COST_APPROVE 不能编制、提交或作废；CONFIG_MANAGE 不能写凭据，CREDENTIAL 不能做其他维护；三类读权限互不替代。
- **Owner 错误透传：** 渠道激活被拒、同人复核被拒与查询对象不存在时，Owner detail 都到达浏览器，Owner Request ID 不外露；409 带 Owner 原因。
- **422 投影：** 字段路径只保留页面 Body 内存在的路径，BFF 补入的 `makerId` 被丢弃。
- **依赖失败：** Owner 503 映射为 DEPENDENCY_UNAVAILABLE；状态回执不等于目标状态、详情回执身份不符都映射为 502。
- **本地拒绝：** 未声明的 Query、非法筛选值、非法 size、页面自报 `checkerId`、提交带 Body、非法路径 ID、缺少必填字段（422）、变更记录缺 targetId、GET 带 Body，均在访问 Owner 前拒绝。
- **凭据日志：** 用输出捕获证明凭据值与参数值不进入任何日志，账号接口只记 `uri=/channel-admin/v1/channel-accounts/**`。
- **版本对比：** 覆盖新增、删除、修改、未变；组成增删与字段差异；0.10 与 0.1 按数值相等；同键范围按顺序配对；任一版本读取失败整页失败；相同版本或非法版本号本地拒绝；只认 CHANNEL_COST_READ。

## 6. WRITE_API_EXAMPLE 第 11 节核对（BFF 视角）

- **API 与 Body：** `ChannelModels` 为普通 class，带中文字段注释与 Hibernate Validator 约束；入口用 `@Valid` 触发；对比 Request 的约束由 `AbstractProcessor.validateBasic` 触发。
- **Controller 只做适配：** 写命令属于简单转发，由 Controller 直接委托本地 Client Port，不建空 Processor；唯一的聚合（版本对比）放在 `service/processor`。
- **Processor、Context、Service、Repository、事务、锁：** 不适用。BFF 无数据库、无本地事务、无锁；写入的 Context、Service、事务与锁都在 channel-connector（CC-A / CC-B 线）。
- **重放与幂等：** BFF 不处理也不转发 `Idempotency-Key`，有测试断言；没有引入命令幂等组件；业务防重归 Owner。
- **View 与 DataObject：** 不适用，BFF 没有 DataObject。HTTP 边界只用页面模型，Owner DTO 只停留在 integration 层。
- **公共包复用与无多余抽象：** 复用 Foundation 分页、HTTP 客户端工厂、Owner detail 开关、MVC 错误映射；复用 `ConfigurationValidationConverter` 与 `CommercialConcurrentReads`；生产代码没有 Mock 或测试分支。
- **验证：** 见第 5 节；测试用真实 HTTP 链加 Loopback Owner Simulator，没有 Mockito、MockBean 或 spy。

## 7. 与 Pencil 画板的差异

以下差异由 Web 分线记录，BW 复核后保留。原因都是 Owner 合同没有对应字段。

- **01 渠道列表：** 去掉「Platform 接入方」列，因为列表合同没有该字段；详情页显示渠道编码并注明「激活时核对」。
- **04 账号配置：** Owner 不回显参数名、值或凭据名。已配置时只显示 Token 末 4 位与更新时间，「重新填写」意味着整组重新输入；无法逐行显示已保存凭据的掩码，也不显示「保存人」。
- **05 激活被拒：** Owner 原因显示在状态变更对话框顶部，没有做成详情页横幅。
- **06 MID 列表：** 省略外部 MID 筛选，因为 `listChannelMids` 没有该 Query。
- **10 版本列表：** 省略「范围 / 组成数」列，因为版本摘要没有计数；计数在版本详情页显示。
- **13 变更记录：** 渠道、账号（「记录」按钮）与 MID 详情可查。成本版本不可查，因为版本响应不暴露 `targetId` 所需的版本主键。
- **14 批准确认：** 省略「复核意见」，因为批准与驳回合同只有 `approvedContentHash`。
- **11 版本编制：** 组成的类型、计算基数与舍入为大写代码输入框，不是下拉框，因为没有目录合同，值由 Owner 校验。「渠道合同引用」改为必填，因为 Owner 规定 `@NotBlank`；这一处由 BW 在 Web 分线交付后修正，并补了测试。

## 8. 遗留

1. **成本版本变更记录：** 需要 channel-connector 在版本响应中暴露 `costVerId`（或另给按 planCode + costVer 的查询），页面才能列出 CANCEL 等记录。
2. **共用类命名：** 版本对比复用了 `CommercialConcurrentReads.all`（同包、通用并发只读），没有复制。日后可另行改名为不带商务前缀的共用类，本次没有动商务文件。
3. **内部调用 Token：** 开启 `internal-auth`（DUAL / REQUIRED）前，需要确认 channel-connector 的入站白名单接受 `operations-console-bff`。BFF 侧已按目标服务名 `channel-connector-service` 附加 Token。
4. **既有缺口：** `console-resource-capabilities.json` 原本就缺 9 个 Web 页面（RISK_EVALUATIONS、RISK_EXECUTION_RELEASES、RISK_LISTS 与 6 个 VAULT_* 页面），Web 分线发现，本次未改。
5. **跨会话冲突热点：** 合入前需逐一核对：`pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts`、`configurationModel.test.ts`（计数 45）、`console-resource-capabilities.json`、`OperationsErrorStatusResolver.java`、`PlatformOperationsSimulator.java`、`OperationsConsoleBffApplicationAutoTest.java`（路由集合与日志前缀）、`OperationsHttpBodyNamingAutoTest.java`、BFF `README.md`、`application.yml`、`platform-support-initial-data.sql`（块 92 追加在块 93 之后）。
6. **未执行项：** 没有做与真实 channel-connector 的联调，没有执行 SQL，没有部署；这些属于阶段 5–7。

## 补充（2026-10-20，接入三处 Owner 新字段）

主会话要求接入 CC-B 新增的三处 Owner 字段，api 模块已由主会话同步到 bw-channel。

| 项 | BFF | Web |
|---|---|---|
| `costVerId`（版本详情与摘要） | `ChannelModels` 的 `ChannelCostVersionResponseBody`、`ChannelCostVersionSummaryResponseBody` 增加字段；对比 Processor 的 base / target 摘要带出 | 版本摘要解码 `costVerId`（必填）；版本详情页新增「变更记录」区，调用 `config-changes?targetType=CHANNEL_COST_VERSION&targetId={costVerId}`（权限 CHANNEL_CHANGE_READ），作废等记录可见 |
| `scopeCount`、`componentCount`（版本摘要） | 摘要增加两个字段（Integer，Owner 为 int）；对比摘要按版本详情计数 | 方案详情的版本列表恢复「范围 / 组成」列；版本详情的解码允许为空 |
| `listChannelMids` 的 `externalMidCode`（前缀） | Controller 放行该 Query（`@Size(max = 128)`，加入 `requireQueryKeys`）；Client 按 `(channelId, channelAccountId, entityNo, status, externalMidCode, page, size)` 调用 | MID 列表恢复「外部 MID」前缀筛选 |

- **SQL：** `platform-support-initial-data.sql` 里块 93 之后的块 92 段，已用主工作区最新的 `registration-dml.sql` 原文覆盖，其中含 query_param 900000000000092724。追加段 SHA-256 与源文件相同：`93dbf028ce6436c8…`。
- **测试更新：** `channel-admin-boundary.json` 的版本响应加入 `costVerId` 与计数，M01 加入 `externalMidCode` Query；`ChannelBrowserAutoTest` 增加超长 `externalMidCode` 本地拒绝；`ChannelAggregationAutoTest` 断言对比摘要的 `costVerId` 与计数；`channel.test.ts` 增加摘要新字段解码与缺 `costVerId` 拒绝。

| 检查 | 结果 |
|---|---|
| `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am test`（JDK 25） | BUILD SUCCESS；BFF 测试模块 1710 项，0 失败、0 错误、0 跳过 |
| `npm --prefix operations-console-web test` | 1135 项，0 失败 |
| `npm --prefix operations-console-web run build` | 通过，只有既有的 chunk 大小警告 |

- **Pencil 差异更新：** 第 7 节里 06（外部 MID 筛选）、10（范围 / 组成列）、13（成本版本变更记录）三项已消除。
- **遗留更新：** 第 8 节第 1 条已解决。
- **未执行项：** 仍未提交，也未部署。
