# Operations 通知管理：现状差异与影响分析（阶段 2）

> 状态：阶段 2 已确认（2026-10-15），第 5 节全部按建议。只读分析，未改代码。需求见 [requirement.md](./requirement.md)。

## 1. 结论摘要

- 后端只有"发送"能力：4 张配置表**只有按 ID / 编码查询**，没有新增、修改、列表；3 张记录表没有列表或搜索。管理与查询要从 Mapper 起全部新增。
- 配置每次发送都直接读库、**没有缓存**，所以页面修改能立即生效（AC-02 不需要额外的缓存失效机制）。
- 运营管理的现成模式清楚可复用：Platform `*-admin/v1` 接口 + Processor 内鉴权（`OperationsManagementPolicyService`）+ `@Audit` + `expectedVersion` 乐观锁（409）；BFF 按 `ApiScope` 方式转发；Web 参照 Vault 数据类型和外部标准 API 页面。
- 有三处需要在阶段 3 做取舍（见第 5 节）：管理代码放在哪个模块、`notification.enabled=false` 时管理页面是否可用、发送记录按时间查询要不要加索引。
- 改动面：Platform（Delivery、Web、Access Control 能力清单、初始化 SQL）、Operations BFF、Operations Web 三个目录；后两者有 5～6 个共享文件要做增量修改，正与其他会话的改动并行。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| 模板列表 / 详情 / 新建 / 启停（范围 1） | `NotificationTemplateMapper` 只有 `find(id)`、`findCode(code)`（`platform-support-delivery/src/main/resources/mapper/delivery/`） | 分页列表、详情、新建、带版本的启停 | 无列表、插入、更新 | 新增 Mapper 方法、Repository、管理 Processor |
| 语言内容增改 + 预览（范围 2） | 内容表只有 `find`、`findLocale`；渲染规则在 `NotificationInput.render/substitute`（`${name}`、标题 ≤255 无换行、正文 ≤64 KiB） | 保存前按发送规则校验；预览只渲染 | 无写入；渲染只在发送链路内调用 | 保存校验与预览**复用** `NotificationInput.render`，不另写一套规则 |
| 路由设置与启停（范围 3） | 路由表只有 `find`、`findTemplate`；发送时 `NotificationConfigurationServiceImpl.resolve` 校验主备账号 | 设置主 / 备账号（不同、均为启用的 EMAIL 账号）、启停 | 无写入 | 新增写入；保存规则与 `resolve` 的发送规则一致 |
| 发送账号列表与启停（范围 4） | 账号表只有 `find(id)`；`EmailProviders` 只有 `require(code)`，不匹配时抛异常（`integration/provider/EmailProviders.java:21-27`） | 列表、是否已配置适配器、被哪些路由引用、启停 | 无列表；无"是否已配置"查询 | `EmailProviders` 增加只读判断方法；路由表加按账号查引用的查询 |
| 发送记录查询（范围 5，Q-01） | 记录表只有按 ID、来源、当前尝试的查询；请求表索引为 `uk_notify_source`、`idx tenant / template / route`，**没有 `create_time` 索引** | 按来源、事件键、模板、状态、时间筛选分页；详情含投递和尝试 | 无列表；按时间范围排序分页没有索引；"投递状态"在投递表上，需要关联 | 新增查询；索引见第 5.3 节 |
| 保护规则（范围 6） | 配置表都有 `version`；现有管理接口用 `expectedVersion` + `ConcurrentRequestException` → 409（`UpdateApiScopeProcessor.java:93-94`） | 冲突 409；危险停用二次确认；即时模板必须含 `${link}`；不删除 | 无 | 沿用；`${link}` 检查读 `notification.transient-template-codes` |
| 权限、菜单、审计（范围 7） | 权限、Console Resource、Gateway 路由都是初始化 SQL 中的数据（API Scope 示例：`platform-support-initial-data.sql:1420-1530`）；审计用 Controller 上的 `@Audit` 加 Processor 内的 `ConfigurationChanged` | 只读 / 维护两级权限、菜单与按钮、每个写操作有审计 | 无任何通知相关注册数据 | 新增一个 ID 块（阶段 5 更正：`900000000000086xxx`～`89xxx`；`81xxx`～`84xxx` 已被 Vault 管理注册 SQL 占用）；默认 `DISABLED`，与先例一致 |
| BFF 转发（范围 8） | 无通知相关代码；鉴权由拦截器统一调用 Platform 的路由授权（`OperationsRequestInterceptor.java:42-44`） | 浏览器路由 `/notification-admin/v1/...` | 全部新增 | 以简单转发为主，参照 `ApiScopeManagementController` |
| 页面（范围 8、AC-10—12） | 无通知页面；没有现成的"多语言内容编辑 + 预览"组件 | 模板、发送账号、发送记录三个菜单页 | 全部新增；内容编辑器需新做 | 列表 / 抽屉参照 `VaultDataTypes`，详情 / 冲突横幅参照 `ExternalStandardApis`，记录参照 `VaultPolicyChanges` |

## 3. 逐系统影响

| 系统 / 目录 | 职责 | 影响点 | 改动类型 | 依赖与风险 |
|---|---|---|---|---|
| `platform-support-delivery` | 通知配置与记录的事实 Owner | 4 张配置表写入与列表、3 张记录表查询的 Mapper / Repository；配置校验规则（主备不同、即时模板含 `${link}`）；`EmailProviders` 判断方法 | 代码 | 现有 7 个 Repository 实现和 Service 都以 `notification.enabled` 为启用条件；见 5.2 |
| `platform-support-web` | HTTP 入口、管理 Processor、审计切面 | 新 Controller；管理 Processor（鉴权、锁、事务、审计） | 代码、配置 | Delivery 模块不依赖 access-control 和 audit-security，管理 Processor 放置位置见 5.1 |
| `platform-support-api` | 内部 HTTP 合同 | 新 `NotificationManagementApi` 及请求 / 响应 Body | 代码 | BFF 依赖此 jar |
| `platform-support-access-control` | 权限常量、Console 能力清单 | 新权限编码常量；`console-resource-capabilities.json` 增加通知页面 | 代码、资源 | 该 JSON 现有 17 项，Web 侧 25 项，**已不同步**（既有问题）；本需求只追加自己的条目 |
| Platform 初始化 SQL | 注册数据 | 权限 2 条、角色授权、菜单与按钮、Gateway 路由及其 Header / Query / 限流规则 | DML（只写不执行） | 每条路由约 10 行附属数据，预计 DML 行数较多（阶段 3 给出精确数） |
| Platform Schema | 表结构 | 可能新增 1 个索引（5.3） | 视决定而定 | 加索引需 DDL 并同步 PDManer 模型 |
| `operations-console-bff` | 页面聚合与转发 | 新 Controller、Platform Client 端口与实现、页面模型；测试 | 代码 | 需增量修改的共享文件：`PlatformClientConfiguration`、`application.yml`（metadata-only 前缀）、`ConfigurationManagementRequestConfiguration`（路径与模型白名单）、`OperationsConsoleBffApplicationAutoTest`（路由全集）、`OperationsHttpBodyNamingAutoTest`。其中 `application.yml` 和应用测试正被其他会话修改 |
| `operations-console-web` | 页面 | 3 个页面、通知模型与解码、内容编辑与预览组件、测试 | 代码 | 需增量修改的共享文件：`navigation/pageCapabilities.ts`、`navigation/ConsolePage.tsx`、`navigation/menu.ts`、`configuration/configurationModel.test.ts`（写死的页面数量 25，Risk 会话也可能同时修改） |
| Gateway | 路由与限流 | 无代码改动；路由来自 Platform 注册数据 | 无 | 依赖 DML 执行后才能从真实入口访问 |
| Vault | 凭据 | 无改动（Q-02、Q-05 不涉及 Reveal） | 无 | — |
| 其他核心系统 | — | 无 | 无 | 调用方提交通知的接口不变 |

## 4. 现有约定（阶段 3 直接沿用）

| 项 | 约定 | 证据 |
|---|---|---|
| Platform 路径 | `/{领域}-admin/v1/...`，本需求用 `/notification-admin/v1`；Gateway 外部路径为 `/api/operations/notification-admin/v1/...` | `ApiScopeManagementApi.java:23`、初始化 SQL 路由行 |
| 身份 | 每个方法带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；Processor 内 `requirePermission` / `authorizeManagement` | `ApiScopeManagementApi.java:42-43`、`ListApiScopesProcessor.java:46` |
| 写 Processor | `AbstractLockProcessor` + `LockCommand.tryLock`，编程式事务（READ_COMMITTED、15s） | `UpdateApiScopeProcessor.java:41-70` |
| 错误 | 字段校验 422；业务规则 400；不存在 404；版本冲突 409 | `MvcProblemExceptionMapper`、`PlatformApiErrorConfiguration.java:38-57` |
| 分页 | `page` / `size`（最大 100），返回 `items/page/size/total` | `ListApiScopesProcessor.java:47-51` |
| 幂等 | Gateway 负责 `Idempotency-Key`；BFF 和 Platform 不处理；新 Web 代码不发送（Vault 写法） | AGENTS.md、`vault/vault.ts:158-162` |
| BFF 错误 | Owner 的 400/404/409 原样透传，422 投影字段路径，依赖不可用 503 | BFF `ExternalApiBrowserAutoTest.java:130-174` |
| 测试 | Platform：`OperationsApiTestBase` + `operationsMocker`，权限需加进 `OperationsCatalog`；BFF：`PlatformOperationsSimulator` + JSON 用例；Web：Node 内置测试，只测模型与解码 | 各测试目录 |

## 5. 需要在阶段 3 决定的问题（附建议）

### 5.1 管理代码放在哪个模块

Delivery 模块不依赖 access-control（鉴权）和 audit-security（审计）。选项：

- **A（建议）**：Delivery 只提供本地读写与配置规则（Repository、Domain Service）；管理 Processor、鉴权与审计放在 `platform-support-web`，与现有 Gateway 配置管理同处。不新增模块依赖。
- B：Delivery 增加对 access-control 和 audit-security 的依赖，管理 Processor 放在 Delivery。更符合"模块自有 Processor"，但会让通知模块依赖鉴权模块。

### 5.2 `notification.enabled=false` 时管理页面是否可用

现有 Delivery 的 Repository、Service 都要求 `notification.enabled=true`；Mapper 本身始终注册。

- **建议**：管理与查询**不依赖** `notification.enabled`，只依赖 `operations.authentication.enabled`。这样未开启发送的环境也能先配好模板；"是否已配置适配器"在未开启发送时显示为否。实现上，管理用的 Repository 不加该条件（或调整现有 Repository 的条件），`EmailProviders` 不存在时按"未配置"处理。

### 5.3 发送记录按时间筛选的索引

请求表没有 `create_time` 索引；列表按时间倒序分页会全表扫描。

- **建议**：新增 `idx_notify_request_create_time (create_time)`（1 条 DDL，同步 PDManer）；来源 + 事件键筛选走已有唯一索引；投递状态筛选用关联子查询，不加冗余字段。
- 备选：不加索引，只允许"来源 + 事件键"精确查询或限定最近 N 天。数据量小时可行，但随数据增长会变慢。

### 5.4 其他（不需要你决定，按此处理）

- 模板详情一次返回模板、全部语言内容、路由和账号摘要，由 Platform 单个接口返回（同库查询），BFF 直接转发，不在 BFF 拼多个请求。
- 预览不调用 Vault 和 SES，只调用渲染。
- 新权限：`NOTIFICATION_READ`、`NOTIFICATION_MANAGE`；初始化 SQL 中授权给平台管理员，状态沿用先例（默认 `REVOKED` / `DISABLED`，激活另行执行）。

## 6. 依赖与风险

| 风险 | 影响 | 应对 |
|---|---|---|
| Operations 共享文件与其他会话并行修改 | 合并冲突或计数类测试（页面总数、路由全集）互相覆盖 | 只做增量编辑；提交时按"HEAD + 自己的行"暂存；阶段 4 开始前再看一次工作区 |
| `console-resource-capabilities.json` 与 Web 已不同步（17 / 25） | 既有问题，不由本需求引起 | 只追加通知条目，不顺手修其他条目；在交付中说明 |
| BFF README 路由表、metadata-only 前缀说明已过时 | 文档漂移 | 只补本需求的行，不重写 |
| Gateway 路由注册 DML 行数多 | 审阅成本高、易错 | 阶段 3 列出完整清单；测试用的权限与路由数据与 DML 对齐 |
| 停用即时发送模板会让注册和找回密码邮件发不出 | 运营误操作影响注册 | 二次确认并说明影响（Q-07）；审计记录 |
| 工作区里 `NotificationProviderRepositoryImpl.java` 有一处**非本会话**的改动（只调整 import） | 与本需求的 Delivery 改动在同一文件 | 不覆盖；阶段 4 改该文件前先确认归属 |

## 7. 工作量依据（粗估，阶段 3 细化）

| 系统 | 预计内容 |
|---|---|
| Platform | 约 12 个接口（模板 4、内容 2、路由 2、账号 2、记录 2）；约 10 个 Mapper 方法；Processor 约 12 个；HTTP 测试 5～6 类 |
| BFF | 1 个 Controller、1 组 Client 端口与实现、模型；JSON 驱动的边界测试 1 类 |
| Web | 3 个页面、1 个内容编辑与预览组件、模型与解码测试 |
| SQL | 注册 DML（权限、菜单、按钮、约 12 条路由及附属行）；可能 1 条索引 DDL |
