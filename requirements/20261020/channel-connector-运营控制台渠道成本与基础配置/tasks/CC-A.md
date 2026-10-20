# 任务卡 CC-A · channel-connector：变更记录表、成本方案列表 / 作废 / 渠道校验、MID 只读列表

- 需求：[requirement.md](../requirement.md)（阶段 1 已确认）；方案：[technique.md](../technique.md)（阶段 2–3 已确认，含新表授权）。本卡只做 technique.md 第 10 节 CC-A 行的范围，不做 CC-B（渠道 / 账号 / MID / 能力的写接口、Vault Protect、Platform 接入方查询）。
- 执行者：Opus 5.5 agent，独立 worktree；主会话设计与把关。**不执行任何 git 写命令（不 add / commit / stash / checkout 分支）**；不对共享环境（mor-test、Nacos、RabbitMQ）做任何改动；不调用 Superpowers skill。
- 允许的本机数据库操作：仅对本机 `local-environment` MySQL 的 `mor_channel_connector`（127.0.0.1:13306，root 密码在 `local-environment/.env.local`，不打印）执行本卡新增的 `t_channel_config_change` DDL，供自动测试使用；不碰其他库。

## 1. 先完整读取

- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 全文、`agent-docs/JAVA_PROJECT_STRUCTURE.md` 第 4–7 节、`agent-docs/examples/WRITE_API_EXAMPLE.md` 全文（含第 11 节核对清单）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md` 第 5.1、11 节。
- `paytech/channel-connector/README.md`、`paytech/channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql`、`docs/database/sql/channel-connector-schema.sql`、`docs/database/channel-connector.pdma.json`（模型由 `scripts/pdmaner/generate-channel-connector-model.mjs` 生成：先读该脚本及其依赖的 payment-channel 字段元数据，把新表加进生成输入后重生成，并运行 `scripts/pdmaner` 下相关 `*.test.mjs`；不要手工直接改 JSON 再被生成器覆盖）。
- 既有成本写入口与测试：`CreateChannelCostPlanProcessor`、`CancelChannelCostVersion` 的参照 `ApproveChannelCostVersionProcessor` / `ActivateChannelCostVersionProcessor`、`ChannelCostPlanServiceImpl`、`ChannelCostPlanRepository(+Impl)`、`ChannelCostPlanVerRepository(+Impl)`、`ChannelConnectorLocks`、`ChannelCostPlanController` / `ChannelCostPlanConverter`、`ChannelCostPlanApi` 与 DTO、测试 `ChannelCostPlanApiTest`、`ChannelCostVersionLifecycleTest`、`ChannelCostVersionActivationJobTest`、`AbstractChannelCostApiTest`、`ChannelCostVersionRequestBuilder`、`ChannelCostResponseChecker`、`ChannelDataMocker`、`ChannelTestMapper`、`ChannelConnectorMockTestBase`、`ChannelConnectorArchitectureTest`。
- 分页复用：`paytech-common-core` 的 `com.paytech.foundation.web.paging.PagingRequestBody` / `PagingResponseBody<T>`（见 DEVELOPMENT_CONVENTIONS 「公共分页复用」）；Platform `IntegrationManagementApi.integrations(...)` 是 GET 分页合同的现成写法。

## 2. 范围（technique.md 第 3.1、4、6 节）

### 2.1 新表 `t_channel_config_change`（第 4 节 DDL 原文）

- PDManer 模型加表（字段、类型、默认、索引 `idx_channel_config_change_target(target_type, target_id, id)`、完整中文注释）；`channel-connector-schema.sql` 末尾追加同一段；新增 `docs/database/sql/changes/2026-10-20-channel-config-change.sql`；测试 `init/ddl.sql` 追加；复制一份到本需求 `db/channel-connector/ddl.sql`（执行文件，头注写明目标库、前置状态、可重跑性、回滚 `DROP TABLE`）。
- 代码：`core/enums/ChangeTargetType`（CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION）、`ChangeType`（CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL），枚举常量之间空一行、实现 `CodeEnum`；`core/dal/dataobject/ChannelConfigChange`；`ChannelConfigChangeMapper` + XML（insert、按 target 分页 select / count，`id DESC`）；`ChannelConfigChangeRepository(+Impl)`（`insert`、`findPage`）。
- 读接口：`ChannelConfigChangeApi`，`GET /channel-connector/v1/config-changes?targetType&targetId&page&size` → `PagingResponseBody<ChannelConfigChangeResponseBody{changeId, targetType, targetId, changeType, fromStatus, toStatus, operatorId, reason, createTime}>`；Controller 直接委托一个本地只读 Service（无 Processor、无事务）。

### 2.2 渠道成本

- `ChannelCostPlanApi.listChannelCostPlans`：`GET /channel-connector/v1/channel-cost-plans?channelId&status&page&size` → `PagingResponseBody<ChannelCostPlanSummaryResponseBody{planId, planCode, channelId, channelCode, channelName, status, currentCostVer, currentEffectiveFrom, pendingApprovalCount, updateTime}>`。同库联查 `t_payment_channel` 取编码 / 名称；`pendingApprovalCount` 为 PENDING_APPROVAL 版本数；排序 `update_time DESC, id DESC`；数据库 LIMIT/OFFSET + COUNT，不内存分页。多表查询用 View（`ChannelCostPlanSummaryView`），不泄露 DataObject 到 api。
- `ChannelCostPlanApi.cancelChannelCostVersion`：`POST /channel-connector/v1/channel-cost-plans/{planCode}/versions/{costVer}/cancel`，Body `CancelChannelCostVersionRequestBody{operatorId（必填，与 makerId 同形态）, reason（1–512）}`；返回 `ChannelCostVersionResponseBody`。规则：仅 APPROVED 且 `effective_from` 晚于当前时间可作废 → INACTIVE；ACTIVE / 其他状态 400 BUSINESS_CHECK_FAILED（detail 中文含方案代码、版本号、当前状态）。Processor 为 `CancelChannelCostVersionProcessor extends AbstractLockProcessor`，锁 `ChannelConnectorLocks.channelCostPlan(planCode)`（LOCK）；本地保存在 `ChannelCostPlanService.cancelVersion(context, operatorId, reason, now)` 的一个 `TransactionTemplate` 内：条件更新 `WHERE id=? AND status='APPROVED' AND effective_from>?` 行数必须为 1，再插入变更记录（target CHANNEL_COST_VERSION / CANCEL / from APPROVED / to INACTIVE / operator / reason）。方案的 `current_version_id` 不指向 APPROVED 版本，无需改动；核对 `selectDueApproved` 不再命中已作废版本。
- `createChannelCostPlan`：新增校验 `PaymentChannelRepository.findById(channelId) == null` → 400「渠道不存在: {channelId}」。改既有 Processor，JavaDoc 同步。

### 2.3 MID 只读列表（为 BW-A 成本编制下拉准备；CC-B 再补写接口）

- 新建 `ChannelMidApi`，本卡只放 `listChannelMids`：`GET /channel-connector/v1/mids?channelId&channelAccountId&entityNo&status&page&size` → `PagingResponseBody<ChannelMidSummaryResponseBody{midId, channelId, channelCode, channelAccountId, accountName, externalMidCode, entityNo, status, capabilityGroupCount, enabledCapabilityGroupCount, activatedAt, updateTime}>`。同库联查渠道与账号名称、能力组计数；排序 `update_time DESC, mid_id DESC`。Controller 委托只读 Service；`ChannelMidRepository` 增加分页查询。

## 3. 不做

- 渠道 / 账号 / MID / 能力的任何写接口、Vault Protect、Platform 接入方查询、能力目录接口（CC-B）。
- BFF、Web、Platform 注册（BW / REG）。
- 修改既有合同签名；修改 Payment、billing、Platform 代码；删除旧数据。

## 4. 测试（`paytech-channel-connector-test`，auto）

- 新增 `auto/cost/ChannelCostPlanListTest`（空库、按渠道 / 状态筛选、分页与 total、排序、`pendingApprovalCount`、渠道名称联查）、`auto/cost/CancelChannelCostVersionTest`（未到期 APPROVED 作废成功并写变更记录；ACTIVE / PENDING_APPROVAL / 已到期 APPROVED 拒绝；方案不存在；重复作废；作废后 Job 不切换；校验 422）、`auto/channel/ChannelConfigChangeQueryTest`、`auto/channel/ChannelMidListTest`，以及 `createChannelCostPlan` 渠道不存在用例补进既有 `ChannelCostPlanApiTest`。
- 经真实 HTTP 入口（`HttpTestInvoker`），不使用 Mockito / MockBean；Builder 建数据、`ChannelDataMocker` 插夹具、Checker 断言；`ChannelTestMapper` 补新表计数 / 清理，全局 BeforeEach 空基线与 AfterEach 清理覆盖新表；`ChannelConnectorArchitectureTest` 的 Payment 源码禁止表名列表加 `t_channel_config_change`。
- 命令：`JAVA_HOME` 用 JDK 25（见 `~/.claude/.../memory/maven-jdk25-and-local-test-run.md` 的做法：`export JAVA_HOME=$(/usr/libexec/java_home -v 25)`），`./mvnw -q -pl paytech/channel-connector/paytech-channel-connector-test -am test`（从 `paytech/` 目录运行，仓库 `~/.m3`），要求全绿；运行前本机 `mor_channel_connector` 已执行新表 DDL。

## 5. 交付

- 报告写到 `tasks/CC-A-report.md`：已读规范清单；所属 Processor / Context、本地原子写入与事务外步骤说明；改动文件清单（表格）；接口 / 表 / 事件前后对照；第 11 节核对清单逐项对应真实类 / 方法（不适用项说明原因）；实际执行的测试命令与结果（用例数、0 失败）；DDL 执行记录（库、时间、表数 17 → 18）；遗留与偏差。
- 不要提交；把 worktree 路径写进报告。
