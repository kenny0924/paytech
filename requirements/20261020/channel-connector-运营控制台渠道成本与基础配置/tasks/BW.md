# 任务卡 BW · Operations BFF + Web：渠道控制台三页（渠道成本 / 渠道与账号 / MID 与能力）+ Platform 注册

- 需求：[requirement.md](../requirement.md)；方案：[technique.md](../technique.md) 第 3、7、8、8.1、10 节；Pencil 画板导出 [assets/stage3/pencil](../assets/stage3/pencil/)（15 张，页面以此为准）。
- 执行者：Opus 5.5 agent。**工作目录固定为 worktree `/Users/kenny/Desktop/Workspace/Code-MoR/.claude/worktrees/bw-channel`**（基于 `commercial/second-slice` 4d8f4ec3，含商务第二切片 I1 / I2 / F9；主工作区与其他 worktree 只读）。不执行任何 git 写命令；不调用 Superpowers skill；不碰 mor-test / Nacos；不用 Mockito / MockBean。
- channel-connector 的 api 模块源码已从 CC-B worktree 同步到本 worktree（`paytech/channel-connector/paytech-channel-connector-api/src`，6 个新增文件夹），BFF 以它编译；若后续 CC-B 调整 DTO，由主会话再同步，你不要改 api 模块。

## 1. 先读

- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（第 3 节「BFF 聚合层职责」「公共分页复用」）、`agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 3.3 节、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节。
- 商务第二切片作为形态参照（本 worktree 内）：BFF `configuration/CommercialClientConfiguration`（含 Foundation `HttpServiceClientOptions.withOwnerProblemDetail()` 的用法，F9 已下沉 Foundation，**不要**复制旧的 `CommercialOwnerProblemDetail`）、`CommercialRequestConfiguration`、`integration/CommercialPricingClient(+Impl)`、`ext/rest/CommercialPricingController`、`OperatorContextArgumentResolver`、`service/processor/CommercialTermWorkspaceProcessor` 与 I2 的版本对比 Processor、`OperationsErrorStatusResolver`；测试 `test/simulator/CommercialOwnerSimulator`、`OperationsBffTestConfiguration`、`OperationsBffHttpTestBase`、`CommercialBrowserAutoTestBase`、`commercial-admin-boundary.json`、`CommercialBrowserAutoTest`、`CommercialAggregationAutoTest`。Web：`src/commercial/**`、`pages/Commercial*.tsx`、`navigation/pageCapabilities.ts`、`navigation/ConsolePage.tsx`、`navigation/menu.ts`（`pageRoutePaths`）、`configuration/configurationModel.test.ts`（页面计数断言）、`api/session.ts`（ApiError.detail）、`tenantAccess/TenantAccessView` 的 `useDetail` / `Loading` / `QueryFailure`、I2 的 Client 选择器。
- Platform：`paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json`、`docs/database/sql/platform-support-initial-data.sql` 末尾块 95 / 93 的形态。
- channel-connector 合同：api 模块里的 `ChannelCostPlanApi`（含 CC-A 新增 `listChannelCostPlans`、`cancelChannelCostVersion`）、`ChannelConfigChangeApi`、`PaymentChannelApi`、`ChannelAccountApi`、`ChannelMidApi`、`MidCapabilityApi`、`ChannelConnectorHeaders`、全部 request / response DTO。

## 2. 范围

### 2.1 BFF（`paytech/operations-console-bff`）

- `configuration/ChannelConnectorClientConfiguration`：服务名 `channel-connector-service`，测试地址属性 `operations.channel-connector-test-base-url`，6 个 Api Bean（`ChannelCostPlanApi`、`ChannelConfigChangeApi`、`PaymentChannelApi`、`ChannelAccountApi`、`ChannelMidApi`、`MidCapabilityApi`），开启 `withOwnerProblemDetail()`，登记 `ChannelCostErrorCode.values()`（及 api 模块里其他 CodeMessageEnum 若有）。
- `configuration/ChannelRequestConfiguration`：`/channel-admin` 前缀严格 JSON（照 Commercial）。
- `integration/ChannelCostClient(+Impl)`、`ChannelConfigurationClient(+Impl)`：命令把 `operator.accountId()` 填入 `X-Gateway-Actor-Subject`；成本命令另把它填入请求体 `makerId` / `checkerId` / `operatorId`（页面模型不含操作人字段）；查询不带操作人。
- `ext/rest/ChannelCostController`、`ChannelConfigurationController`，前缀 `/channel-admin/v1`，30 条路径与 technique.md 第 8 节 / 本目录 `db/platform-support-service/registration-dml.sql` 的路由逐条一致（路径、方法、Query 白名单）。`requireQueryKeys` 只放行登记的 Query。
- `service/processor/ChannelCostVersionComparisonProcessor`（`AbstractProcessor`）：读两个版本详情，按六维范围对齐、按组成代码对齐，输出 added / removed / changed / unchanged；照 I2 对比 Processor 的并发读取方式。
- 页面模型 `service/model/ChannelModels`（内部 class，`*RequestBody` / `*ResponseBody`）；`OperationsErrorStatusResolver` 登记新错误码；README 追加「渠道」一节。
- 测试：`test/simulator/ChannelConnectorOwnerSimulator`、`OperationsBffTestConfiguration` 注册与 `@DynamicPropertySource`、`channel-admin-boundary.json`（30 条）、`ChannelBrowserAutoTest`（转发、授权拒绝、Maker/Checker 分离、业务拒绝 detail 透传、409、422 投影、依赖失败、未声明 Query 拒绝）、`ChannelAggregationAutoTest`（对比）。

### 2.2 Web（`operations-console-web`）

- `src/channel/channel.ts`（路径、解码、命令、文案、状态按钮可见性模型）、`channel.test.ts`、`channel.css`、`ChannelParts.tsx`、`useChannelCommand.ts`、`AccountConfigurationModal.tsx`（参数 / 凭据键值行，凭据 `Input.Password`，已配置显示掩码与 Token 末 4 位、"重新填写"）、`CapabilityEditor.tsx`（按目录接口渲染：BOOLEAN 开关、DECIMAL 输入、CODE 下拉）、`CostVersionEditor.tsx`（范围 / 组成增删行，MID 下拉来自 `listChannelMids(channelId, status=ACTIVE)`）、`CostVersionDiff.tsx`；页面 `pages/ChannelChannels.tsx`（列表 + 详情，详情页三标签：技术账号 / MID / 变更记录）、`ChannelMids.tsx`（列表 + 详情含能力组与明细）、`ChannelCostPlans.tsx`（列表、方案详情与版本列表、版本编制、版本对比、批准 / 驳回 / 作废对话框）。
- 注册：`pageCapabilities.ts` 加 3 页（componentKey / 路由 / 按钮码见 technique.md 第 8 节）、`ConsolePage.tsx` 加 3 个 lazy、`menu.ts` 的 `pageRoutePaths` 加详情路径（`/channel/channels/:channelId`、`/channel/mids/:midId`、`/channel/cost-plans/:planCode`、`/channel/cost-plans/:planCode/versions/:costVer`）、`configurationModel.test.ts` 计数 +3（当前 42）。
- 视觉与交互以 Pencil 15 张画板为准：错误显示 Owner detail；状态按钮只显示当前状态允许的下一步；CLOSED 后无操作；表单内联校验（四位 MCC、两位国家、三位币种、十进制费率）；空状态 / 加载失败 / 409 冲突沿用商务页做法。
- 测试：`channel.test.ts` 覆盖解码、按钮可见性、表单校验、对比模型；`npm --prefix operations-console-web test` 与 `npm --prefix operations-console-web run build` 通过。

### 2.3 Platform 注册

- `console-resource-capabilities.json` 加 3 页（与 `pageCapabilities.ts` 完全一致）。
- 把本目录 `db/platform-support-service/registration-dml.sql`（块 92，已写好、已做回滚校验）**原文**追加到 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾（块 93 之后）；不改其内容；若 BFF 路径最终与之不一致，以 SQL 为准改 BFF，或在报告中列出需要我改 SQL 的差异。
- Platform access-control 若有对 capabilities.json 的测试（`ConsoleResourceCapabilities` 相关），跑通。

## 3. 不做

channel-connector 代码；Platform 代码（除 capabilities.json 与 initial-data.sql 追加）；Vault；mor-test；提交。

## 4. 验证命令

JDK 25，`paytech/` 下：`./mvnw -q -pl operations-console-bff/paytech-operations-console-bff-test -am test`（或该 BFF 测试模块的既定命令，见其 README）；`npm --prefix operations-console-web test && npm --prefix operations-console-web run build`；Platform access-control 模块测试。本机 MySQL/Redis 需要时用主工作区 `local-environment`（`-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`）。

## 5. 交付

`tasks/BW-report.md`（写在主工作区本目录；worktree 内无此目录时写在 worktree 同路径并在回复里说明）：已读清单、BFF 分层落点、30 条路由对照表（BFF 路径 ↔ 块 92 路由 ↔ Owner 合同）、改动文件表、测试命令与结果、第 11 节核对、与 Pencil 画板的差异、遗留。不要提交。
