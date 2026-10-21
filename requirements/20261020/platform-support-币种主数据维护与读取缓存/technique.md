# platform-support：币种主数据维护与读取缓存 · 技术方案

状态：**阶段 2—3 已确认（2026-10-20，Q6—Q8 按建议）；阶段 4—5 研发与本地验证中**。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。

**规模：** 6 个部件（Platform、ops-bff、ops-web、ledger、billing，以及 arch-rules 规则数据）。

| 类别 | Platform | ops-bff | ops-web | ledger | billing |
|---|---|---|---|---|---|
| 接口 | 新增维护接口 3 个，查询接口加 `version` 字段 | 新增转发 5 条，`/finance/currencies/v1` 改为读 Platform | 新增「币种」页 1 个 | 删除 `/currencies/v1` | — |
| DDL | `ALTER` 1 条（加 `version`、改表注释） | — | — | `DROP` 1 条 | `DROP` 1 条 |
| 初始化 DML | 运营权限、菜单、按钮、路由一个新块 | — | — | 删 USD 副本行 | 删 USD 副本行 |

## 1. 现状与差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 |
|---|---|---|---|
| R1 维护 | Platform 只有只读 `CurrencySpecQueryApi`（list / get）；`t_currency_spec` 无 `version` 列；无写入口、无审计 | 新建草稿、改草稿、启用、停用、再启用；Platform 内再次鉴权、写 `t_audit_event` | 写链路全部缺失；无乐观版本 |
| R2 页面 | 「平台配置」菜单由 `t_console_resource` 种子生成（组 `…562`）；同类页「权限目录」`Permissions.tsx` + `useConfigurationForm` + `ConfigurationStatusModal` | 新增「币种」页、权限、按钮、路由 | 全部缺失；`ConfigurationStatusModal` 只支持 `resource`/`permission` 两类 |
| R3 读取 | ledger 9 处、billing 4 处读本库 `t_currency_spec`（见下表）；ledger、billing 都已依赖 `paytech-platform-support-api`，但只用其中的枚举 | 读进程内快照，快照来自 Platform | ledger 已有 HTTP 客户端基础设施（`HttpServiceClientFactory`，账户、计费两个分组）；billing 没有任何外呼 |
| R4 事务公理 | 过账精度校验 `JournalPostingServiceImpl.checkBalanced` 在过账事务内 | 事务内只读内存 | 见 §2.3 |
| R5 删副本 | `mor_ledger`、`mor_billing` 各一张同结构表 + 初始化行；arch-rules `table-ownership.yaml` 三处登记，`TableOwnershipAutoTest` 断言副本存在 | 只剩 `mor_platform` | 代码、模型、SQL、测试、规则数据都要改 |
| 运营财务页币种下拉 | ops-web → ops-bff `/finance/currencies/v1` → ledger `/currencies/v1` → ledger 副本 | ops-bff 直接读 Platform | ledger 不再代理 Platform 数据 |
| Q2 停用语义 | billing `SubmitBillingFactProcessor.getActiveCurrencySpec` 要求 ACTIVE；ledger 开账簿要求 ACTIVE；其余只取精度 | 只有“新用途”要求 ACTIVE | billing 登记计费事实放宽为“已启用过”（ACTIVE 或 INACTIVE） |

读取点分类（改造后调用的方法）：

| 系统 | 位置 | 用途 | 事务内 | 改造后 |
|---|---|---|---|---|
| ledger | `CreateLedgerBookProcessor.checkActiveCurrency` | 开账簿 | 锁内 | `requireActive` |
| ledger | `JournalPostingServiceImpl.checkBalanced` | 过账精度 | **是** | `requireUsable` |
| ledger | `SubmitManualAdjustmentProcessor`、`ManualAdjustmentServiceImpl` ×2 | 调整精度、列表 / 详情补位 | 否 | `requireUsable` |
| ledger | `RunTrialBalanceProcessor`、`TrialBalanceServiceImpl`、`StartPeriodCloseProcessor` | 试算 / 关账精度 | 部分在事务内 | `requireUsable` |
| ledger | `LedgerQueryServiceImpl.minorUnit` / `listCurrencies` | 查询补位 / 币种列表 | 否 | `requireUsable`；`listCurrencies` 删除 |
| billing | `SubmitBillingFactProcessor` | 登记计费事实 | 否（事务前校验） | `requireUsable`（Q2 放宽） |
| billing | `BillingFactServiceImpl`、`BillingFactPublicationServiceImpl`、`BillingFactQueryServiceImpl` | 已登记事实的精度 | 发布在事务内 | `requireUsable` |

“可用”（usable）指 ACTIVE 或 INACTIVE，也就是启用过的币种。DRAFT 从未生效，任何用途都拒绝。

## 2. 方案

### 2.1 Platform 维护（R1）

**数据：** `t_currency_spec` 增加 `version INT NOT NULL DEFAULT 1`，用于页面乐观并发，和权限目录、集成配置一致。表注释去掉“Ledger 与 Billing 持只读副本”。不新增历史表，变更记录写 Platform 统一审计 `t_audit_event`（运营 `/audit` 页可查）。

**接口：** 新增 `CurrencySpecManagementApi`，路径 `@HttpExchange("/currency-admin/v1/currency-specs")`，请求头带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`，与 `PermissionManagementApi` 同形。

| 方法 | 路径 | 请求 | 规则 |
|---|---|---|---|
| POST | `/` | `currency`、`minorUnit`、`roundingMode`、`reason` | 代码 `^[A-Z]{3}$`；小数位 0..8；舍入目前只有 `HALF_UP`；建为 DRAFT；代码重复返回 400（业务校验失败，与权限目录一致） |
| PUT | `/{currency}` | `minorUnit`、`roundingMode`、`expectedVersion` | 只有 DRAFT 可改（Q1）；版本不符返回 409 |
| PATCH | `/{currency}/status` | `status`、`expectedVersion`、`reason` | 允许 DRAFT→ACTIVE、ACTIVE→INACTIVE、INACTIVE→ACTIVE，其他转换拒绝；`reason` 必填 |

现有 `CurrencySpecQueryApi` 不变，响应加 `version`。它继续作为 ledger、billing 和 ops-bff 的读接口，只在私网可达。

**Processor：** `CreateCurrencySpecProcessor`、`UpdateCurrencySpecProcessor`、`ChangeCurrencySpecStatusProcessor`，放在 `paytech-platform-support-core` 的 `referencedata` 包。core 已依赖 access-control 与 audit-security，不新增模块依赖。结构照搬 `UpdateIntegrationProcessor`：

1. `AbstractLockProcessor` 加锁：`tryLock("currency-spec:" + currency)`。
2. 本地事务内依次执行：
   - `OperationsManagementPolicyService.authorizeManagement(..., "CURRENCY_SPEC_MANAGE")`；
   - 锁行并核对 `expectedVersion`；
   - 校验状态转换；
   - 条件 UPDATE，`version + 1`；
   - `OperationsAuditService.record(...)`，记录变更前后的字段和原因。
3. 事务内没有外部调用。

### 2.2 运营入口（R2）

- **Platform 种子：** 在 `platform-support-initial-data.sql` 增加一个独立块。
  - **块号：** 125（2026-10-20 核对：mor-test `mor_platform` 已用到 122，仓库中 123 为对账、124 由商务需求预留）。
  - **权限：** `CURRENCY_SPEC_READ`、`CURRENCY_SPEC_MANAGE`。
  - **菜单页：** `OPERATIONS_CURRENCY_SPECS`，挂在「平台配置」下，路由 `/currency-specs`，组件键 `OPERATIONS_CURRENCY_SPECS`（与资源编码一致，沿用现有约定）。
  - **按钮：** `OPERATIONS_CURRENCY_SPEC_MANAGE`。
  - **网关路由：** 4 条，指向 ops-bff：列表 GET 用 READ；新建、修改、改状态用 MANAGE。
  - **初始状态：** 沿用惯例，菜单、按钮、路由先 DISABLED，PLATFORM_ADMIN 授权为 REVOKED。发布时另附激活脚本。
  - **能力清单：** `console-resource-capabilities.json` 与 web 端 `pageCapabilities.ts` 同步登记。
- **ops-bff：** 新增 `CurrencySpecManagementController`（`/currency-admin/v1/currency-specs`），只做转发，带 `OperatorContext`。
  - 列表走 `CurrencySpecQueryApi`，写操作走 `CurrencySpecManagementApi`，两者都在 `PlatformClientConfiguration` 登记。
  - 原 `/finance/currencies/v1` 改为读 Platform `CurrencySpecQueryApi`，按 `status` 参数在 BFF 过滤。路径和响应不变，ops-web 财务页不用改。
- **ops-web：** 新页 `src/pages/CurrencySpecs.tsx`，仿照 `Permissions.tsx`。
  - **表格列：** 币种、小数位、舍入方式、状态标签、最后更新、操作。
  - **新建 / 编辑弹窗：** 编辑时代码只读，只有草稿显示「编辑」。
  - **启用确认：** 再次展示小数位与舍入方式，提示“启用后不可修改，币种代码也无法换一个重建”。
  - **停用：** 提示“已有账簿与计费事实照常处理，只拦截新建账簿等新用途”。
  - **原因输入：** 状态变更填写原因。
  - **复用组件：** 扩展 `ConfigurationStatusModal`，支持币种这一类。
  - **权限：** 没有管理按钮权限时只读。

### 2.3 ledger、billing 读取快照（R3、R4）

**组件（两个服务各一份，各自属于本服务的集成层，不抽公共库）：**

| 组件 | 职责 |
|---|---|
| `PlatformCurrencyClient`（interface + Impl，`integration/platform`） | 事务外调用 `CurrencySpecQueryApi.listCurrencySpecs()`，并校验响应：代码格式、小数位 0..8、舍入方式和状态可识别、代码不重复 |
| `CurrencySpecSnapshot`（`@Component`） | 用 `AtomicReference` 持有不可变 `Map<String, CurrencyRule>`、加载时间、连续失败次数和“待刷新”标记；读方法只读内存，从不外呼 |
| `CurrencyRuleService`（interface + Impl，领域服务） | 业务代码唯一入口：`requireUsable(currency)` 与 `requireActive(currency)`。查不到或状态不符时抛 `BizCheckFailException`，说明币种与原因；查不到时同时置“待刷新” |
| `CurrencySnapshotRefreshScheduler` | `@Scheduled(fixedDelay = 5s)`，事务外运行。尚未加载、有待刷新标记、或距上次成功满 60 秒时，调用 Client 整体替换快照；失败则保留旧快照，记 WARN 并累计失败次数 |
| `CurrencySnapshotHealthIndicator` | 首次加载成功前为 DOWN，并接入 readiness 组。详情含 `loadedAt` 和连续失败次数 |

**为什么用定时器而不用 XXL-Job：** 每个实例都要刷新自己的内存，XXL 一次只触发一个实例，不适用。网关的 Platform 快照（`GatewaySnapshotRefreshScheduler` + 首次加载健康指示器）就是这种写法，这里照搬。

**关于 Caffeine（Q6）：** 项目的 `paytech-caffeine-starter` 是 Spring Cache 加 `expireAfterWrite`，目前没有任何服务在用。用在这里有两个问题：

- 条目过期后的下一次读取会同步回源，这次读取可能正发生在过账事务里，违反事务公理。
- Platform 不可用时，过期条目已被清掉，过账就会失败，做不到“保留上次快照”。

币种只有几行，全量快照加原子替换就满足全部要求，也不用设置过期。所以**建议不引入 Caffeine**。这个结论和你“可以用 Caffeine”的说法不同，请确认。

**与 Q3 对应的行为：**

- 启动后立即加载，失败每 5 秒重试，期间服务不就绪。
- 就绪后每 60 秒全量刷新；刷新失败保留旧快照，不影响过账。
- Platform 新启用的币种最迟 60 秒被识别（AC-08）。
- 快照查不到时置待刷新，下一个 5 秒周期就会回源。被拒的过账批次由 ledger 既有追赶处理（`PostingStreamCatchUpProcessor`）重试恢复；计费事实登记由调用方重试。
- **什么时候会查不到：**
  - ledger 账簿只能用当时 ACTIVE 的币种开立，所以只在多实例刷新先后不一致的几十秒内才可能查不到；
  - billing 只在新币种刚启用、本实例还没刷新时可能查不到。

**配置：**

- ledger 增加 HTTP 分组 `platform-support-service`，地址取 `LEDGER_PLATFORM_SERVICE_URL`，默认 `http://127.0.0.1:18106/platform-support`；超时同现有分组（连接 2 秒、读 5 秒）。
- billing 新增 `paytech-http-client-support` 依赖和同样的分组配置，地址取 `BILLING_PLATFORM_SERVICE_URL`。
- 两者都增加 `@EnableScheduling`，并加开关 `*.currency-snapshot.scheduling-enabled`，自动测试中关闭。
- 两者的 readiness 组都加入快照健康指示器。

### 2.4 删除副本（R5）

- **数据库：**
  - ledger、billing 各写一份 `docs/database/sql/changes/2026-10-2x-drop-currency-spec-copy.sql`（`DROP TABLE t_currency_spec`），回滚为原建表语句加 USD 行。
  - 从各自的 schema SQL、初始化 SQL 和 PDManer 模型中移除该表。
- **代码：**
  - 删除 `CurrencySpec` DataObject、Mapper、XML、Repository。
  - 删除两个持久化配置中 `CurrencySpecStatus` 的 TypeHandler 注册。
  - 删除 ledger `LedgerSetupQueryApi.listCurrencies`、`LedgerQueryConverter` 相关方法和 `ListCurrenciesTest`。
- **arch-rules：**
  - 修改 `scripts/generate-table-ownership.mjs` 中的副本说明，重新生成 `table-ownership.yaml`。
  - `TableOwnershipAutoTest` 改为断言 ledger 30 表、billing 10 表，且两库都不含 `t_currency_spec`。

### 2.5 文档（R6）

- **新决定记录：** `agent-docs/decisions/2026-10-20-currency-spec-platform-snapshot-read.md`，取代 D41 的副本部分，D41 所在表行加指向。
- **同步更新：**
  - `PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md` SCN-PLT-09；
  - `FINANCE_PHYSICAL_DECISIONS.md` 开头说明；
  - `design-discuss/billing/database.md` 的副本行；
  - `agent-docs/database/README.md`，如有表数统计则一并改；
  - 交付时更新 `CURRENT_IMPLEMENTATION.md`。
- **不改：** `data-service/cdc/whitelist.yaml` 与本地 Flink 示例仍指向已不存在的 `mor_finance`，这是早先切分遗留，属于 data-service 范围，本需求只在交付记录中登记。

## 3. 测试计划

| 场景 | 验收 | 入口 |
|---|---|---|
| 新建草稿、代码重复、格式非法、小数位越界 | AC-01 | Platform `CurrencySpecManagementApiTest`（HTTP） |
| 改草稿成功；改 ACTIVE / INACTIVE 被拒；版本冲突 409 | AC-01、AC-02 | 同上 |
| 启用、停用、再启用；非法转换被拒；无 `CURRENCY_SPEC_MANAGE` 被拒；每次成功各有一条审计 | AC-01 | 同上 |
| ops-bff 转发与 `/finance/currencies/v1` 改读 Platform | AC-01 | ops-bff 现有自动测试，Platform 侧使用 DTO Mock |
| ops-web 页面：权限控制、草稿才可编辑、启用确认文案 | AC-01、AC-02 | vitest |
| 开账簿：ACTIVE 通过；INACTIVE、DRAFT、未知币种被拒 | AC-03 | ledger `CreateLedgerBookTest` |
| 过账、调整、试算、关账精度不变 | AC-04 | ledger、billing 现有测试全部通过；全局 BeforeEach 由 Platform Mock 提供 USD 并主动刷新一次快照 |
| 过账事务中币种不在快照内：拒绝且无残留；处理期间 Platform Mock 没有收到调用；刷新后重试成功 | AC-05 | ledger 自动记账测试新增 1 例 |
| Platform Mock 返回 503 或非法数据：保留旧快照、过账成功；首次加载失败时健康为 DOWN | AC-06 | ledger、billing 快照测试（调用刷新入口和健康端点） |
| 新增币种经一次刷新后被识别；INACTIVE 币种的计费事实可以登记 | AC-08、Q2 | billing `SubmitBillingFactTest` 新增 |
| 两库不再有 `t_currency_spec` | AC-07 | `TableOwnershipAutoTest` |

**测试基础设施：**

- ledger 增加 `MockCurrencySpecApi`（`mock/external`）和 `CurrencySpecMocker`（`mocker/external`），沿用现有 `@EnableMockHttpServer` 与全局“未配置调用”检查。
- billing 第一次引入外部 HTTP Mock，需要新增 `@EnableMockHttpServer` 配置、全局 BeforeEach / AfterEach 钩子和清理，规模与 ledger 现有的相当。
- 各服务只跑本模块测试；ops-web 跑测试与构建。

## 4. 发布（阶段 6—7，届时另行授权）

顺序：先保证新代码不依赖旧表，最后才删表，以便回滚。

1. `mor_platform` 执行：`ALTER`、新种子块、激活脚本。
2. 依次替换 platform-support、ops-bff、ledger、billing 镜像：
   - ledger、billing 增加 Platform 地址环境变量；
   - compose 中对 `platform-support-service` 增加 `service_healthy` 依赖。
3. ops-web 由另一个会话从主工作区部署，需先协调，确认构建内容不夹带对方未提交的商务页面。
4. 验收通过后再执行 `mor_ledger`、`mor_billing` 的 `DROP`。回滚时先恢复旧镜像；表已删的情况按回滚 SQL 重建。

## 5. 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| Q6 | 缓存实现：用 Caffeine，还是用全量快照（原子替换 + 定时刷新） | **全量快照，不引入 Caffeine**，原因见 §2.3 |
| Q7 | billing 登记计费事实，INACTIVE 币种是否放行 | **放行**，按 Q2 只拦新用途；DRAFT 和未知币种仍拒绝 |
| Q8 | ledger 的 `/currencies/v1` 删除，财务页币种下拉改由 ops-bff 读 Platform | **删除**，避免 ledger 代理 Platform 数据；页面无感知 |
