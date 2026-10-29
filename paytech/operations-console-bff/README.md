# Operations Console BFF

租户访问与成员权限查询的现行合同见 [需求 API](../../requirements/20260914/platform-support-租户访问与成员权限查询/api.md)：8 项能力，复用既有 IAM 模型；当前研发、验证和部署状态见 [根台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)。

通知管理见[需求方案](../../requirements/20261015/operations-console-通知管理/technique.md)：`/notification-admin/v1` 下 12 个端点由 `NotificationManagementController` 简单转发到 `PlatformNotificationManagementClient`，本地 record 模型与 Platform 合同字段一一对应（严格转换，缺少基本类型字段视为上游无效响应，Owner 多出的字段不投影），写入核对模板或账号编码；422 只保留本次请求体内的字段路径；不接收、不转发 `Idempotency-Key`；`/notification-admin` 只记元数据日志。

Webhook 管理见[需求方案](../../requirements/20261016/operations-console-Webhook管理/technique.md)：`/webhook-admin/v1` 下 10 个端点由 `WebhookManagementController` 简单转发到 `PlatformWebhookManagementClient`，本地 record 模型与 Platform 合同字段一一对应（严格转换，时间保持 UTC 字符串，投递 Envelope 原样透传）；新建端点与 Replay 返回 201，修改、启停、详情核对端点或投递 ID；写 Body 上限 8192 字节，测试事件不接受 Body（仍需 `Content-Type: application/json`）；422 只保留本次请求体内的字段路径；新建与轮换响应含密钥明文，依赖全局 `Cache-Control: no-store`，`/webhook-admin` 只记元数据日志；不接收、不转发 `Idempotency-Key`。

文件公开桶与文件记录见[公共合同](../../requirements/20261022/platform-support-文件公开桶/tasks/common.md)：同一 Controller 增加桶对外地址修改、检查策略 8 个端点与文件记录 3 个端点（列表、详情、下载），写入路由编辑与删除改为 `/routes/{sourceSystem}/{accessLevel}`。新增请求类都在 `filestorage.request` 包内，沿用严格解析；文件记录列表与下载的 Query 按入口声明的白名单接收，下载不接受 Body；`FileStorageManagementApi` 代理额外登记 Platform 文件错误码（文件不存在 404、版本不可下载 409），下载地址只出现在响应中，`/storage-admin` 只记元数据日志。

存储管理见[需求方案](../../requirements/20261022/platform-support-文件多存储并存/technique.md) 4.2：`/storage-admin/v1` 下 12 个端点（实例列表 / 新建 / 编辑 / 更换密钥 / 启停，桶登记 / 检查 / 启停，写入路由列表 / 新建 / 修改 / 删除）由 `FileStorageAdminController` 简单转发到 `PlatformFileStorageClient`（`FileStorageManagementApi`）。页面请求与响应直接使用 Platform 管理合同的模型，请求体同样走配置管理的严格解析（未知字段、类型强转、查询与桶检查带 Body 均在访问 Platform 前拒绝）；查询与写入都携带操作人 Header；422 只保留本次请求体内的字段路径；业务拒绝外显 Platform 原因；Platform 读取超时 9 秒（桶检查在 Platform 事务外访问 Vault 与存储，Gateway 响应上限 10 秒）。新建实例与更换密钥的请求含存储密钥，`/storage-admin` 只记元数据日志，BFF 不保存、不回显；不接收、不转发 `Idempotency-Key`。

客户入驻审核材料下载（O10，`POST /client-onboarding-admin/v1/cases/{caseId}/files/{fileId}/download`）见[文件多存储并存第二段](../../requirements/20261022/platform-support-文件多存储并存/segment2.md) 4.4：`ClientReviewFileDownloadProcessor` 先读 Risk 审核任务，文件不在任务证据中按 `FILE_NOT_FOUND` 拒绝且不调用 Platform；再经 `ClientReviewFileClient` 调 Platform 内部文件接口 `InternalFileApi` 查询元数据（文件名、类型、大小）并签发下载地址，下载请求带 `X-Operator-Account-Id` = 当前操作人，由 Platform 记操作人、文件与调用服务审计。浏览器响应字段不变（`fileId`、`displayName`、`mimeType`、`sizeBytes`、`url`、`expiresAt`），ops-web 不改。所有 Platform 出站请求带 `X-Caller-Service: operations-console-bff`，Platform 以 DUAL 模式据此识别调用服务。

主档维护与资格登记见[商业关系快照与真实解析接入技术方案](../../requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md) 3.3.5：`EntityAdminController`（`/entity-admin/v1`，B1—B6）与 `QualificationAdminController`（`/qualification-admin/v1`，B7—B11）经 `EntityAdminClient` / `QualificationAdminClient` 调用 client-master `ClientMasterEntityAdminApi` 与 compliance `QualificationAdminApi`（`EntityQualificationClientConfiguration` 复用入驻审核工厂与测试直连地址，会话 Account/Session 填入 Gateway Actor 头）。请求体直接使用两个 Owner 管理合同的模型，由 `ClientReviewRequestConfiguration` 严格解析（64 KiB、未知 Query 400）。B2 / B5 由 `OperatingEntityDetailsProcessor`、`LocalPartnerDetailsProcessor` 先读主档、再并发读 compliance 资格，资格失败只把对应部分标为 `UNAVAILABLE`（CLIENT 来源的提供方资格为 `NOT_APPLICABLE`）；B8 由 `QualificationListProcessor` 为页内主体去重后并发补名称（最多 8 个并发、3 秒），失败该行 `subjectName` 为 null；B10 由 `RecordManualQualificationProcessor` 先核对主体（不存在保留 client-master 404，OE `CLOSED`、Partner / Client `TERMINATED` 返回 422 `QUALIFICATION_SUBJECT_TERMINATED`），再单次写 compliance。Owner 404 / 409 / 422 错误码原样到达浏览器；两个前缀只记元数据日志；不接收、不转发 `Idempotency-Key`。

外部 API 配置管理的现行合同见[管理接口](../../requirements/20260914/platform-support-外部API配置与执行/admin-api.md)，2026-10-20 起 Owner 为 channel-connector（[统一对外渠道出口](../../requirements/20261020/channel-connector-统一对外渠道出口/technique.md)，路径、JSON 字段与状态码不变）：`/external-api-admin/v1` 下 39 个端点（含 14 个沙箱验证端点；2026-10-22 第二段接入方并入渠道，下线接入方 5 个 CRUD，其余路径中的 `{integrationCode}` 即渠道编码，渠道身份与出站地址改经 `/channel-admin` 维护）按对象拆成 6 组 Controller → `*Client` Port → Impl，经 `ChannelConnectorClientConfiguration` 的独立工厂调用 channel-connector（不开启 Owner Problem detail，连接 3 秒、读取 5 秒，与迁移前经 Platform 时一致）；19 个写命令传会话操作者 `X-Gateway-Actor-Subject`，查询与 4 个静态校验不传身份，均不转发 Session ID；逐字段转换 `service/model/externalapi` 本地模型，`definition`、`flowDefinition` 作为 JSON 原样传递；不接收、不转发 `Idempotency-Key`（由 Gateway 处理）。`ExternalApiRequestConfiguration` 严格解析写 Body（未知属性、重复键含定义内部、尾随内容与标量强转均返回 400，上限 65,536 字节（Gateway 绝对上限））；Owner 422 由 `ExternalApiValidationConverter` 只保留以本次 Body 顶层属性开头的字段路径，说明统一为安全文案，具体问题由三个校验接口返回。

> 本轮规则集整体管理的现行合同见[需求技术方案](../../requirements/20260911/operations-console-风控规则集列表优化/technique.md)：沿用原接口版本，一集一行、整体删除及实际生效使用保护；Web 隐藏 Candidate 页面，后端历史合同保留。阶段确认与证据见[交付记录](../../requirements/20260911/operations-console-风控规则集列表优化/delivery.md)，实现与环境状态只见[当前台账第 16 节](../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)。


> 2026-09-11 原 V1 风控配置增量的接口与兼容规则见 [需求与接口合同](../../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，本次验证、数据库与部署状态见 [当前状态第 15 节](../../agent-docs/CURRENT_IMPLEMENTATION.md#15-operations-risk-v1-配置增量2026-09-11)。下文带日期的旧验证记录保留为对应基线证据。

Operations Console 的无状态 Spring MVC 聚合层。浏览器接口覆盖登录、会话、强制改密、权限菜单、人员管理、自定义角色与权限配置、审计查询、Gateway Target/Route 管理，以及 Risk Rule Set 和 TEST Candidate 及整体删除的十路配置适配。Platform Support 拥有身份与当前入口授权，Risk 拥有规则、版本、状态与静态检查事实；本服务负责面向运营前端的查询聚合、展示模型组装与协议适配。

本 README 记录组件能力、运行方法与可复现验证证据，不证明任何环境已部署或已启用对应公共 Route。当前实现、验证、部署和待迁移状态的唯一跨仓库台账是 [`agent-docs/CURRENT_IMPLEMENTATION.md`](../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 工程结构

```text
paytech/operations-console-bff/
├── pom.xml                              # com.paytech:paytech-operations-console-bff（服务聚合 POM，承载 Enforcer 禁令）
├── paytech-operations-console-bff-app/   # Spring Boot 部署模块
└── paytech-operations-console-bff-test/  # 默认 auto，显式 manual-test；含 ArchitectureAutoTest
```

基础包为 `com.paytech.operations.console.bff`，启动类为 `OperationsConsoleBffApplication`。服务聚合 POM 以真实相对路径继承 `com.paytech:paytech-parent:1.0.0-SNAPSHOT`（`../build/paytech-parent/pom.xml`），并由单一 Reactor `paytech/pom.xml` 列出；唯一 Maven Wrapper 是 `paytech/mvnw`。由统一 BOM 管理 Java 25、Spring Boot 4.0.8、Spring Cloud 2025.1.3 和 Spring Cloud Alibaba 2025.1.0.0。

当前没有需要供其他 Java 服务依赖的 HTTP 契约，因此只有 `app + test`（design/03 §1.2：无 api、无 core），不建立空 `api` 模块或没有业务职责的领域服务、持久化层。生产依赖包括 MVC、Foundation MVC Starter / HTTP Client Support、Actuator、Prometheus、Nacos Discovery、LoadBalancer、参数校验和轻量 `platform-support-api`、`paytech-risk-decision-api`、`paytech-compliance-api`；构建门禁继续禁止数据库、缓存、MQ、Nacos Config 和本地认证框架。

### 代码分层

Operations BFF 是面向运营前端的聚合层，负责页面所需的授权查询组合、数据裁剪转换与展示模型组装。核心服务保留业务事实、领域规则、授权决策和业务事务；页面组装不因此全部下沉到 Platform 或其他核心服务。

当前单接口用例的调用路径：

```text
OperationsRequestInterceptor（可信身份 → 当前 PLT-I02 完成 → Origin/JSON）
  → MVC 参数绑定与校验 → ext/rest Controller（强类型请求/响应）
    → integration Client Port（只声明 BFF 本地模型，单个 Owner 调用）
      → integration/impl（注入的 Owner API、DTO 转换与字段校验）
        → Foundation HTTP Client（统一异常转换）
```

管理与认证 Processor 原来只重复执行授权和转发，已删除。统一入站拦截器默认覆盖全部业务 HTTP 路径，仅排除 Actuator 和 Boot error；只对精确的 POST Login/Refresh 豁免当前授权。授权成功后的 `OperatorContext` 仅存于本次请求，`OperatorContextArgumentResolver` 只读取该已授权上下文；缺失时拒绝。GET 同样执行授权，不依赖 Controller 是否声明上下文参数。可信上下文合法的受保护请求必须先完成一次 PLT-I02，再检查 Origin/媒体类型及绑定参数；本地拒绝不得产生后续业务调用，授权拒绝也不解析 Risk Body。

当前简单转发用例的 Controller 直接调用本服务的 Client Port，不接触远端 API DTO、第三方 SDK、持久化或事务。没有为简单转发再增加同义 Service 或 Processor。需要多接口查询组合或页面响应组装时，由 BFF 的 `service` / Processor 承担，通过 Client Port 访问上游；真实聚合职责不适用删除空转发层的规则。BFF 保留各上游授权约束，不编排跨系统写事务。

聚合 Processor 位于 `service/processor`，每个入口一个，继承 Foundation `AbstractProcessor`（入口事务检查、内部请求字段校验、`validate` 附加校验，再执行 `processRequest`）。内部请求放在 `service/processor/request`；响应直接使用 BFF 页面 ResponseBody，不复制同义 Response。Controller 只构造内部请求并调用 `process`。Risk 适配一般是单 Owner 转发，不建 Risk Processor；唯一例外是 `RiskEvaluationExplanationProcessor`，它组合评估解释与规则集详情两个 Risk 接口组装条件树，规则集详情失败只降级对应规则。

角色详情和权限配置是实际聚合用例：`RoleDetailsProcessor` 通过本地 Client 获取角色事实、当前可授予权限 Catalog 和 Console Resource 绑定，`RolePermissionOptionsProcessor` 只获取后两者，均交给纯页面组装器 `RolePermissionTree` 生成 UI 树。`RoleListProcessor` 获取角色分页后，仅对当前页发起一次批量员工数查询，再按 Role ID 合并展示模型；空页不调用统计接口。角色创建、更新和删除仍由 Controller 直接委托单个 Platform 命令；Platform 持有角色版本、权限集合、系统角色保护、当前授权、幂等和审计事务，BFF 不保存或计算业务权限。

审计详情由 `AuditEventDetailsProcessor` 将 Platform 已授权的事件及安全历史属性组装为前后对比和属性列表。Client 只映射远端 DTO；Processor 按原始序号与稳定键归并，保留重复值及隐藏值 `null`，不查询当前角色或人员名称来改写历史。列表筛选仍直接委托 Platform，不增加空转发层。

`PlatformClientConfiguration` 使用 `paytech-http-client-support` 的 `HttpServiceClientFactory` 创建公共工厂，注册 `OperationsErrorCode` 与 `GatewayConfigurationErrorCode`，并将四个 Platform `@HttpExchange` 接口代理注册为 Spring 单例 Bean。`PlatformOperationsClientImpl` 与 `PlatformGatewayConfigurationClientImpl` 通过构造器注入并直接调用 API，不重复实现网络异常、HTTP 状态或错误体解析。Gateway 配置不存在保留为浏览器 404，供过期详情关闭并刷新列表；其他集成组件需要同一接口时复用该 Bean。

`OperationsAuthenticationApi`、`OperationsAuthorizationApi`、`OperationsManagementApi` 和 `GatewayConfigurationManagementApi` 只声明 HTTP 方法。BFF 的 Client Adapter 显式映射这些 Platform DTO，BFF 自己的 `service.model` 保留独立展示模型。Gateway 配置的分页 Item 和详情内嵌结构同样使用 `*ResponseBody`，不保留无后缀别名。`OperationsHttpBodyNamingAutoTest` 同时约束 Body 类型后缀和 API 接口不声明内嵌 DTO。

#### 固定地址下游（ledger / account）

账务基础资料、账本查询与余额查询见[需求方案](../../requirements/20261018/operations-console-财务基础资料与账本余额查询/technique.md)：`/finance` 下 18 个端点（第一期）由 `FinanceSetupController`、`FinanceLedgerBookController`、`FinanceBalanceController` 简单转发到 `FinanceSetupClient`、`FinanceLedgerBookClient`、`FinanceBalanceClient`，浏览器路径为 `/finance` + Owner 路径。ledger 与 account 不注册 Nacos，`FinanceClientConfiguration` 以 `operations.ledger-base-url`、`operations.account-base-url`（环境变量 `OPERATIONS_LEDGER_BASE_URL` / `OPERATIONS_ACCOUNT_BASE_URL`）固定私网地址直连，不加 LoadBalancer、Discovery 拦截器或重试；启动时只接受不含路径、Query 与 Fragment 的 http(s) 地址。JDK HttpClient 连接 2 秒、读取 4 秒、禁止重定向，严格 Problem 解码；超时 504、不可达 503。两个 Owner 各用独立工厂、只登记本 Owner 的合同错误码：ledger 为 `LedgerSetupErrorCode`；account 为 `BalanceQueryErrorCode`（`OWNER_ACCOUNT_NOT_FOUND`）、`AccountPostingErrorCode` 与明细流水使用的 `BalanceMovementErrorCode.ACCOUNT_NOT_FOUND`，业务拒绝以 400 与原 Code 到达浏览器。命令把会话 Account/Session 填入 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`，不转发 Authorization、Cookie 或 `Idempotency-Key`；分页、命令主键与查询身份回执不一致按 502 处理。`FinanceRequestConfiguration` 严格解析写 Body（上限 8192 字节），GET 与开放会计期间不接受 Body；`/finance` 不含个人原值，不加入元数据日志前缀。auto 测试把两个地址指向 `LedgerOwnerSimulator`、`AccountOwnerSimulator`。

明细账查询见[需求方案](../../requirements/20261019/finance-account明细账与ledger控制科目重构/technique.md) 2.2：`FinanceSubledgerController` 新增 3 个只读端点（R19 `GET /finance/owner-accounts/v1`、R20 `GET /finance/owner-accounts/v1/{accountId}/entries`、R21 `GET /finance/account-postings/v1/{postingId}`，Route Code `operations-finance-owner-accounts`、`operations-finance-owner-account-entries`、`operations-finance-account-posting`，权限 `FINANCE_ACCOUNTING_READ`），经 `FinanceSubledgerClient` 简单转发 account `OwnerAccountQueryApi` 与 `AccountPostingApi.getPosting`；过账进度、追平读取与按流重建不接控制台。R19 主体类型、主体标识与币种必填，Query 白名单为 `entityType, entityId, currency, ownerType, ownerId, accountCode, balanceBucket, page, size`，R20 为 `page, size`。ledger 账户（R10 / R11）的核算对象类型只剩 `LedgerOwnerType` 五值，Client / Local Partner 明细只在 account；分录流水与详情透传 `ACCOUNT_POSTING` 来源的 `accountPostingId`、`accountPostingSequence` 与行 `accountEntryId`。`/finance` 共 21 个端点。

科目分级与维护见[需求方案](../../requirements/20261019/ledger-会计科目分级维护/technique.md) 2.3：`FinanceSetupController` 新增科目维护 L1—L4（`POST /finance/chart-of-accounts/v1/{chartCode}/accounts` 及 `.../{chartAccountId}/update|disable|enable`，`FINANCE_ACCOUNTING_MANAGE`，停用 / 启用不接受 Body）与变更记录 L5（`GET .../{chartCode}/changes`，Query `chartAccountId, page, size`），经 `FinanceSetupClient` 转发 ledger `ChartOfAccountsCommandApi` 与 `LedgerSetupQueryApi.listChartAccountChanges`；`FinanceLedgerBookController` 新增科目余额 L6（`GET /finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances`）与试算记录列表、详情（`.../trial-balances`、`.../trial-balances/{runNo}`），经 `FinanceLedgerBookClient` 转发 `TrialBalanceApi`，执行试算不接入。科目列表 Query 增加 `accountStatus`，账簿账户与明细账账户 Query 增加精确匹配的 `accountNo`；科目、账户、分录行与 account 批次流水模型同步加 `accountNo`、科目层级与状态字段。维护回执的科目主键、科目余额与试算详情的账簿 / 期间 / 序号必须与请求一致，否则 502。`/finance` 共 29 个端点。

财务控制台三项见[需求方案](../../requirements/20261020/finance-人工调整与计费事实/technique.md)第 2—5 节：新增 26 个端点（Gateway 块 100—102）。`FinanceManualAdjustmentController`（`/finance/manual-adjustments/v1` 列表、提交、详情、`approve`、`reject`）、`FinancePeriodCloseController`（`/finance/accounting-periods/v1/{accountingPeriodId}/closes` 列表、详情、发起、`approve`、`reject`，`.../reopens` 发起重开）、`FinanceRuleBundleController`（`/finance/accounting-rule-bundles/v1` 列表、详情、版本详情、新建、`submit`、`approve`、`reject`、`void`）简单转发 ledger；`FinanceLedgerBookController` 新增发起试算 `POST .../periods/{periodId}/trial-balances`；`FinanceBillingFactController`（`/finance/billing-facts/v1` 列表、登记、详情、`approve`、`void`）转发 billing `/billing/v1/facts`（列表对应 `/search`），登记时 BFF 固定 `sourceSystem=OPERATIONS` 并为每次登记生成 `sourceReference = OPS- + UUID`。期间看板 `GET /finance/period-close/v1/board?fiscalCalendarId=` 由 `PeriodCloseBoardProcessor` 组合：分页读完日历期间（size 200），再逐期取关账流程第 1 页的第一条作为 `latestClose`。权限：查询 READ；制单、发起、作废 MANAGE；审批、驳回 `FINANCE_ACCOUNTING_APPROVE`。billing 不注册 Nacos，以 `operations.billing-base-url`（`OPERATIONS_BILLING_BASE_URL`，默认 `http://127.0.0.1:18113`）固定地址直连，独立工厂、登记 `BillingFactErrorCode`（4 个）；ledger 工厂增加登记 `LedgerErrorCode`（含 technique D4 的 14 个控制台错误码）。Web 按这些 Code 区分提示，其余业务拒绝仍为公共 `BUSINESS_CHECK_FAILED`。发起关账、发起重开与关账、重开审批走独立的长耗时 ledger 代理，读取超时 `operations.ledger-long-running-read-timeout`（`OPERATIONS_LEDGER_LONG_RUNNING_READ_TIMEOUT`，默认 9 秒，须在 4 秒与 Gateway 路由响应超时上限 10 秒之间），其他调用仍为 4 秒。无请求体的命令（billing 批准与作废、发起关账、发起试算、规则版本提交、审批、驳回与作废）向上游不发送 Body，浏览器携带 Body 时 400；人工调整提交、计费事实登记与规则版本新建的 Body 上限为 65536 字节，其余写 Body 仍为 8192。`/finance` 共 55 个端点；auto 测试把 billing 地址指向 `BillingOwnerSimulator`。

对账控制台见[对账切片 1 方案](../../requirements/20261020/reconciliation-渠道资金对账切片1/technique.md)第 2.3、2.6 节：新增 38 个端点（Gateway 块 123），浏览器路径为 `/finance/reconciliation/v1` + reconciliation 路径。`ReconciliationClearingController`、`ReconciliationCashController`、`ReconciliationStatementController`、`ReconciliationBankController`、`ReconciliationExceptionController` 简单转发 reconciliation 的 ClearingAccountApi、CashAccountApi、ChannelStatementApi / PlatformTxnApi、BankStatementApi / ExpectedBankMovementApi / BankMatchApi、ReconExceptionApi / ReconResolutionApi / ReconSummaryApi（billing 用的清算账户解析与 payout / tax 用的当前银行账户不经 BFF）；清算账户详情由 `ReconClearingAccountDetailProcessor` 并发读取 channel-connector MID 详情补外部 MID 编码与状态，异常详情由 `ReconExceptionDetailProcessor` 补所属清算账户编码或银行账户名称与末四位。权限：查询 `FINANCE_RECONCILIATION_READ`，写命令 `FINANCE_RECONCILIATION_OPERATE`，银行账户与处置复核 `FINANCE_RECONCILIATION_APPROVE`；BFF 不另判权限点，提交人不能复核由 reconciliation 判断。reconciliation 以 `operations.reconciliation-base-url`（`OPERATIONS_RECONCILIATION_BASE_URL`，默认 `http://127.0.0.1:18115`）固定地址直连，独立工厂、读取超时 9 秒（渠道账单完成导入同步校验最多 2 万行）、开启 Owner Problem detail（业务拒绝原因到达浏览器）、登记 `ReconciliationErrorCode`。分段导入的追加行请求体上限 65536 字节，其余写 Body 8192；`/finance/reconciliation/v1/cash-accounts` 在 `web.logging.metadata-only-path-prefixes` 中只记元数据（新建请求含银行账号明文）。auto 测试把地址指向 `ReconciliationOwnerSimulator`。

对账控制台详情页与渠道流水（[2026-10-21 需求](../../requirements/20261021/reconciliation-对账控制台详情页与渠道流水/technique.md)，Gateway 块 126）再加 3 个只读端点：`GET /finance/reconciliation/v1/channel-statement-lines`（ChannelStatementLineApi，跨账单查账单行）、`/bank-statements/{bankStatementId}`、`/bank-statement-lines/{bankStatementLineId}`（流水 + 银行匹配与预期银行流水 + 未结异常），均为 `FINANCE_RECONCILIATION_READ`；`OperationsErrorStatusResolver` 把 `BANK_STATEMENT_NOT_FOUND`、`BANK_STATEMENT_LINE_NOT_FOUND` 映射为 404。

#### commercial（Nacos 发现）

商务控制台见[需求方案 §2.9](../../requirements/20261019/commercial-六层产品定价模型/technique.md) 与[任务卡 E](../../requirements/20261019/commercial-六层产品定价模型/tasks/E-运营控制台.md)：`/commercial-admin/v1` 下 24 个端点（第二切片 I1 起 27 个，见下）。`CommercialAgreementController`（合同详情聚合、创建、提交签署、签署登记、激活、终止、订阅替换、开通项增改）与 `CommercialPricingController`（画像列表 / 创建 / 修改 / 启用 / 退役，条款方案、版本、价格整体替换、提交、批准、驳回、排期、激活、版本编辑页聚合、单 OE 价格）经 `CommercialAgreementClient`、`CommercialPricingClient` 调用 commercial。两个页面聚合在 Processor：`CommercialAgreementOverviewProcessor` 组合合同与费率卡投影的当前 / 已排期 Client 条款版本，费率卡失败或 3 秒超时只把版本部分标为 `UNAVAILABLE`，PARTNER 或非 ACTIVE 合同为 `NOT_APPLICABLE`；`CommercialTermWorkspaceProcessor` 读取版本、冻结合同的可定价 OE 与开通项，再逐 OE 读取价格全集合并，任一失败整页失败。

Route Code 为 `operations-commercial-*`，权限 `COMMERCIAL_CONTRACT_READ / MANAGE`、`COMMERCIAL_PRICING_READ / MANAGE / APPROVE`：批准与驳回只认 APPROVE，提交、排期、激活只认 MANAGE；注册 DML 块 95 见需求目录 `db/platform-support-service/`。`CommercialClientConfiguration` 按 `commercial-service` 发现（auto 测试用 `operations.commercial-test-base-url` loopback 直连），连接 2 秒、读取 4 秒、不重试，传 `X-Request-Id` 与 `X-Caller-Service: operations-console-bff`。命令把会话 Account ID 填入 `X-Gateway-Actor-Subject`（commercial 只接受数字操作人），不转发 Authorization、Cookie、Session 与 `Idempotency-Key`。commercial 合同、画像、条款与费率卡只返回公共错误码：业务拒绝到浏览器为 400 `BUSINESS_CHECK_FAILED`，版本冲突 409，422 字段路径只投影到本次 Body。两个 commercial 代理工厂都显式开启 Foundation `HttpServiceClientOptions.withOwnerProblemDetail()`（第二切片 F9）：BUSINESS_CHECK_FAILED、CONCURRENT_REQUEST、NOT_FOUND 与已登记的价格命中码把 Owner 已校验 Problem 的 `detail` 作为本地异常消息，BFF 的 MVC 边界以自己的 Request ID 与时间输出浏览器 Problem；INVALID_REQUEST、字段校验、5xx 与协议错误不变，detail 超过 512 字符或含控制字符时保留公共说明。BFF 不再自行解析 Owner Problem。条款写入直接调用 `CommercialTermApi`，操作人同样走请求头。`CommercialRequestConfiguration` 严格解析写 Body，上限 65536 字节（价格整体替换），其他写路由由 Gateway 限 8192。合同、方案、版本列表自第二切片 I2 起由 commercial 只读列表接口提供（见下）。

第二切片 I1（[需求](../../requirements/20261020/commercial-运营控制台完善/requirement.md)）新增 3 个只读端点，由 `CommercialPricePreviewController` 承接：`GET /commercial-admin/v1/dashboard`（`COMMERCIAL_CONTRACT_READ`，`CommercialDashboardProcessor` 组装四张指标卡片，列表接口接入前 `count=null`、`status=PENDING_INTEGRATION`，不调用上游）、`GET /commercial-admin/v1/rate-cards?clientId&asOf&currency`（`COMMERCIAL_PRICING_READ`，经 `CommercialPricePreviewClient` 原样转发 `RateCardApi`，回执 Client 必须一致）、`POST /commercial-admin/v1/commercial-prices/simulate`（`COMMERCIAL_PRICING_READ`，只读 POST，`CommercialPriceSimulationProcessor` 转发 `POST /commercial-prices/v1/resolutions` 并补固定追溯 ID `paymentFactId=1`，再由 `CommercialFeeIllustrator` 按命中价格计算示例费用：百分比 + 固定、阶梯 WHOLE_BASE / PROGRESSIVE、最低 / 最高约束、币种精度 HALF_UP；依赖其他费用或周期累计量的组件标为未计算）。价格命中用独立代理工厂登记 `CommercialPriceErrorCode`：`PRICING_FACTS_INCOMPLETE`、`PRICE_NOT_FOUND`、`INVALID_PRICING_FACT` 到浏览器为 422，`PRICE_AMBIGUOUS` 为 409，并带 Owner 原因。注册 DML 续块 95 见第二切片需求目录 `db/platform-support-service/`。auto 测试把地址指向 `CommercialOwnerSimulator`（支持按 Query 区分响应）。

第二切片 I2 接入 commercial H 线只读接口（`AgreementQueryApi`、`CommercialTermQueryApi`、`ProductCatalogApi`、`BusinessChangeQueryApi`，同样经开启 Owner Problem detail 的代理工厂透传 Owner 原因），新增 8 个只读端点（共 35 个）：

- `GET /commercial-admin/v1/agreements`（合同列表，`COMMERCIAL_CONTRACT_READ`）、`GET .../commercial-terms/plans` 与 `GET .../commercial-terms/versions`（方案 / 版本列表，`COMMERCIAL_PRICING_READ`）简单转发：分页按公共 `PagingRequestBody` 先行校验，合同与版本的 `status` 允许重复（`CommercialQueryParameters`，最多 7 个值），分页回执的 page / size 与请求不一致或条目超页按 502。
- `GET .../commercial-terms/versions/{baseVersionId}/diff/{targetVersionId}`（`COMMERCIAL_PRICING_READ`）：`CommercialTermVersionDiffProcessor` 读取两版及各自合同的可定价 OE 价格（与版本编辑页共用 `CommercialConcurrentReads`），两版不同方案 400 `BUSINESS_CHECK_FAILED`；`CommercialPriceDiffer` 按 OE + 币种 + 定价画像（`uk_term_price_profile`）配对输出新增 / 删除 / 修改（字段级，组件按 componentCode、档位按 bandNo，十进制按数值）/ 未变。
- `GET .../products`、`/products/capabilities`、`/products/fee-events`（`COMMERCIAL_CONTRACT_READ`）转发产品目录。
- `GET .../business-changes?aggregateRootType&aggregateRootId&page&size`（`COMMERCIAL_CONTRACT_READ`）：`CommercialBusinessChangeProcessor` 为每条快照补充与同一 recordType + recordId 上一快照的 `changedFields` / `previousData`；本页找不到时按 200 条一页向后回溯至多 5 页，回溯到末尾为 `FIRST_VERSION`（全部字段），超出上限为 `NOT_COMPARED`。
- `GET /commercial-admin/v1/dashboard` 改为并发 5 次按状态计数（待审批 / 已排期版本，草稿 / 待签署 / 生效合同，每次 size=10 取 total），并返回提交最早的 10 条待审批版本作为审批队列；任一失败整页失败。

注册 DML 为 Platform ID 块 93（8 条路由，休眠），见第二切片需求目录 `db/platform-support-service/`。

开通与定价模型调整一期（[需求](../../requirements/20261020/commercial-开通与定价模型调整/requirement.md)，[技术方案 §5](../../requirements/20261020/commercial-开通与定价模型调整/technique.md)）新增 17 个端点（共 52 个），合同开通项请求与读取的 `limits` 改为 `conditions[]{dimensionCode, values[]}` + `require3ds`，版本与价格读取增加 `ownerType`、来源模板、破标（组件 `benchmark`、版本 `belowStandardCount`）、`templateUpdate`、`acceptance`、`relevanceWarnings`，版本状态增 `CLIENT_SIGNED`：

- 产品目录维护（`CommercialCatalogController` → `CommercialCatalogClient` → `ProductCatalogApi`，`COMMERCIAL_CATALOG_MANAGE`）：`POST /products/capabilities`、`PUT /products/capabilities/{productCapabilityId}`、`POST .../{productCapabilityId}/retire|activate`、`PUT /products/fee-events/{productFeeEventId}/capability`；维度清单 `GET /products/dimensions`（`COMMERCIAL_CONTRACT_READ`）。
- 标准价目模板（`CommercialTemplateController`）：`GET /commercial-terms/templates`（`COMMERCIAL_PRICING_READ`）、`POST /commercial-terms/templates` 与模板专用版本路由 `POST /commercial-terms/templates/{planId}/versions`、`PUT|POST /commercial-terms/template-versions/{versionId}/prices|submit|schedule|activate`（`COMMERCIAL_TEMPLATE_MANAGE`）、`.../approve|reject`（`COMMERCIAL_TEMPLATE_APPROVE`），内部转发到与合同同一组 commercial 版本接口。
- 路由范围隔离：`CommercialTermScopedCommandProcessor` 在转发前读取版本概要的 `ownerType`（建版本时按模板方案列表判定 planId），模板路由只接受 `PLATFORM_TEMPLATE`，块 95 的合同版本写入路由（建版本、价格、提交、批准、驳回、排期、激活）拒绝模板，均为 400 `BUSINESS_CHECK_FAILED` 且不写入。模板版本的详情、价格全集、版本列表与对比复用价目只读路由：编辑页与对比对模板版本不读合同，按模板方案列表取模板 OE（响应 `agreement=null`、`template` 为模板方案）。
- 合同版本：`POST /commercial-terms/versions/{versionId}/fill-from-template` 与 `POST .../acceptance`（登记客户签署，`COMMERCIAL_PRICING_MANAGE`），由 commercial 拒绝模板版本。

注册 DML 为 Platform ID 块 124（3 权限、5 资源、17 路由，休眠），见本需求目录 `db/platform-support-service/`。auto 测试 `CommercialTemplateCatalogAutoTest` 覆盖权限分离、范围隔离、Owner 原因透传与本地边界，边界清单逐行覆盖转发与授权拒绝。

#### 渠道（channel-connector，Nacos 发现）

渠道控制台见[需求方案 §7–§8](../../requirements/20261020/channel-connector-运营控制台渠道成本与基础配置/technique.md)：`/channel-admin/v1` 下 35 个端点，与 Platform 注册 DML 块 92 的 31 条及块 129 的 4 条路由逐条一致（Query 白名单同两块的 `t_gateway_route_query_param`，渠道列表新增 `channelType`，其余路由不接受 Query）。

- `ChannelConfigurationController`（25 条：渠道 6（含出站地址整组替换 `PUT /channels/{channelId}/endpoints`）、技术账号 6、MID 5、能力 4、变更记录 1、调用授权 3）经 `ChannelConfigurationClient` 简单转发 `ChannelApi`、`ChannelAccountApi`、`ChannelMidApi`、`MidCapabilityApi`、`ChannelConfigChangeApi`、`ChannelCallGrantApi`；命令把会话 Account ID 填入 `X-Gateway-Actor-Subject`，查询不带操作人。渠道透传 `channelType`（PAYMENT/SERVICE，创建必填）、`description`、`endpoints`、`version`，列表透传 `endpointCount` 与 `activeGrantCount`（支付渠道为 null）；技术账号的 OE 只校验格式，按渠道类型的必填 / 必空由 channel-connector 判定；变更记录对象类型放开到 12 种（含 CHANNEL_API、CHANNEL_CALL_GRANT 等）。回执核对对象身份，状态变更回执的状态必须等于目标状态，筛选列表的每条回执必须满足筛选条件，渠道回执必须带出站地址集合，不一致按 502。路由权限：调用授权列表 `CHANNEL_CONFIG_READ`，新建与启停 `CHANNEL_CALL_GRANT_MANAGE`，出站地址 `CHANNEL_CONFIG_MANAGE`（由 Gateway Route 与 PLT-I02 判定）。
- `ChannelCostController`（9 条成本 + 1 条版本对比）经 `ChannelCostClient` 转发 `ChannelCostPlanApi`：成本合同的操作人在请求体，BFF 以会话 Account ID 填入 `makerId`（创建版本、提交）、`checkerId`（批准、驳回）、`operatorId`（作废）；页面模型不含这些字段，浏览器携带即按未知属性 400。提交无 Body。
- `GET .../channel-cost-plans/{planCode}/versions/{baseCostVer}/diff/{targetCostVer}`：`ChannelCostVersionComparisonProcessor` 并发读取两版详情（复用 `CommercialConcurrentReads`），范围按 MID、OE、国家、币种、支付方式、动作类型六维对齐（同键多个按 scopeNo 逐个配对），组成按 componentCode 对齐，输出 added / removed / changed（含组成增删与字段差异，十进制按数值）/ unchanged；任一版本读取失败整页失败。
- 权限：`CHANNEL_CONFIG_READ / MANAGE`、`CHANNEL_ACCOUNT_CREDENTIAL`（只有账号配置写入）、`CHANNEL_COST_READ / MANAGE / APPROVE`（批准与驳回只认 APPROVE）、`CHANNEL_CHANGE_READ`（变更记录）；Route Code 为 `operations-channel-*`。
- `ChannelConnectorClientConfiguration` 按 `channel-connector-service` 发现（auto 测试用 `operations.channel-connector-test-base-url` loopback 直连），与 commercial 相同的超时、请求头与严格 Problem 解码，开启 Foundation `withOwnerProblemDetail()`：业务拒绝（如“渠道未配置出站地址”、私网出站地址）、409（版本冲突、地址仍被原子 API 引用、授权组合重复）与 NOT_FOUND 的 Owner 原因到达浏览器；422 字段路径只投影到本次页面 Body（BFF 补入的操作人字段不外显）。登记 `ChannelCostErrorCode`，状态固定 400。
- `ChannelRequestConfiguration` 严格解析 `ChannelModels` 内的写 Body，上限 65536 字节（账号配置与成本版本创建），其他写路由由 Gateway 限 8192；GET 与提交带 Body 返回 400。账号配置请求含凭据明文，`/channel-admin/v1/channel-accounts` 只记元数据日志。
- auto 测试：`ChannelConnectorOwnerSimulator`、`channel-admin-boundary.json`（34 条转发样例）、`ChannelBrowserAutoTest`、`ChannelAggregationAutoTest`。

### 内部调用 Token（D26）

依赖 `paytech-internal-auth`，`internal-auth.mode` 默认 `OFF`：不创建任何 Token Bean，入站与出站行为与引入前一致。设为 `DUAL` / `REQUIRED` 时，MVC Starter 的 `InternalAuthFilter` 校验入站 Token；`InternalCallTokenOptions` 让本 BFF 全部 `HttpServiceClientFactory.create` 客户端按显式目标服务名（`platform-support-service`、`risk-decision-service`、`compliance-service`、`client-master-service`、`ledger-service`、`account-service`、`billing-service`、`commercial-service`、`channel-connector-service`、`reconciliation-service`）附加 `Authorization: Bearer`。Token 来源为 Foundation 的 `InternalTokenSupplier`；本步只在测试中用 `internal-auth.static-key.*` 本地密钥对，Platform 签发客户端的接线留待 Platform 签发接口落地。Vault 管理客户端由 `paytech-vault-client` 自行装配，不在本接线范围内。`OperationsInternalTokenAutoTest` 覆盖 `REQUIRED` 下入站拒绝与出站附加。

### Risk 配置适配边界

风控承保（REQ-03）见[需求技术方案](../../requirements/20261029/risk-decision-承保记录/technique.md)第 10.1 节：`RiskUnderwritingController`（`/risk-underwriting/v1`，12 个）。工作台 `UnderwritingWorkspaceProcessor` 经 `UnderwritingInputAssembler` 按 Client 取最近已批准申请的尽调案件，并发读取 compliance 案件、client-master 申请资料与分类、Platform MCC 组装输入快照（来源失败只降级该来源），按预览路由实时授权判断维护权限；写命令只接受结论字段（`RiskUnderwritingRequestConfiguration` 严格解析），由 `UnderwritingCommandProcessor` 重新读取输入（任一来源不可用 503）后转发 `RiskUnderwritingClient`，回执经 `UnderwritingLabeler` 补操作人显示名。

名单维护与执行发布的现行合同见[需求技术方案](../../requirements/20261015/operations-console-支付风控配置接入/technique.md)第 8 节：`RiskListController`（`/risk-lists/v1/lists`，5 个）与 `RiskExecutionReleaseController`（`/decision-releases/v1/executions`，6 个）只委托 `RiskListClient`、`RiskExecutionReleaseClient`，由 `RiskControlConverter` 逐字段转换、`RiskControlResponsePolicy` 核对回执身份与分页；状态编码不做闭合枚举。名单成员只返回 Risk 的脱敏主体键，`/risk-lists` 只记元数据日志。校验与激活为无 Body 的 JSON POST。

`RiskDecisionConfigurationController` 的具体 Handler 只使用本地 `RiskRuleSetModels`、`RiskTestCandidateModels` 和唯一 `RiskDecisionConfigurationClient` Port。唯一实现 `RiskDecisionConfigurationClientImpl` 显式逐字段转换 Owner 请求/响应；不创建 Risk Processor、业务 Service、Repository、数据库或恢复状态缓存。

`RiskClientConfiguration` 使用独立 `operationsRiskProxyFactory` 和 `risk-decision-service` Discovery（入驻审核代理 `ClientReviewClientConfiguration` 使用 `compliance-service`），分别注册一个 `DecisionRuleSetManagementApi` 和 `DecisionReleaseManagementApi` 代理，不复用 Platform 工厂或拦截器。私网 Base URL 固定为 `http://risk-decision-service`，连接超时 2 秒、Socket Read 超时 4 秒，禁止跟随 Redirect，使用 Foundation `HttpServiceClientOptions.strict()`，不接受旧四字段错误体。仅 `auto` Profile 可配置严格 Loopback 的 `operations.risk-test-base-url`。每个成功入口只调用一次 Risk，写命令最多一次传输且无应用自动重试；这不承诺 URLConnection 对早期失败的幂等读永不发生底层重试。

四个 Risk POST 严格拒绝重复键、尾随 JSON Root、未知/缺失成员和错误 Token；显式 nullable 与 missing 保持不同。Header/Path/Query 或 JSON Shape 错误返回 400，成功解码后的 Body Bean Validation 返回带安全字段路径的 422。AST 的业务语义仍由 Risk 判定。适配器严格校验成功响应的完整 Shape、标量、状态矩阵、顺序及请求关联；Authoring 中的 `handlerCode`、`implementationVersion`、`implementationHash` 必须先通过校验，再从浏览器模型中删除字段和值。

原始合法 `Idempotency-Key`（`[A-Za-z0-9_-]{16,128}`）、可信 Actor/Session 和当前 `X-Request-Id` 原样转发。Validate 还必须在 PLT-I02 后精确匹配 `risk-validate-v1-{releaseId}-{expectedVersion}`；不匹配返回 400、Risk 零调用，不替换调用者的 Key。Risk 不存在映射 404、依赖不可用 503、超时 504、损坏成功响应 502 `UPSTREAM_INVALID_RESPONSE`。浏览器 Problem 使用本次请求 ID；系统详情及未获准的上游原文不对外披露，获准的公共业务拒绝保留安全说明；`200 + REJECTED` 仍是正常静态检查结果。

Gateway Route Detail 的通用 `bodyLimitBytes` 描述字段接受闭区间 `0..65_536`，不按 Risk 路径分支。它不是 BFF 的请求体大小限制器：实际字节计数、Chunked 拒绝与 body-size 413 均由 Gateway 拥有，BFF 不添加大小 Filter 或 direct-BFF 413 测试。

2026-09-04 按用户要求，Refresh 改为普通 JSON 请求。Login/Refresh 直接返回 `TokenGrantResponseBody`，包含 `accessToken`、`tokenType`、`expiresIn`、`refreshToken`、`refreshExpiresIn` 和两项认证限制标记。Refresh 请求体为 `{"refreshToken":"..."}`，不要求 Access Token；Logout 使用同一请求体并保留当前 Access Token 鉴权。Cookie、Query 和 Header 都不能替代 JSON 凭据，BFF 不读写 Cookie，也不再维护专为 Cookie 设置的 CSRF Header。

现有 Operations HTTP 请求类型统一使用 `*RequestBody`，顶层响应统一使用 `*ResponseBody`，例如 `LoginRequestBody`、`TokenGrantResponseBody` 和 `AccountResponseBody`。分页复用 `PagingResponseBody<T>`；嵌套元素 `RoleListItem`、`RoleSummary`、`AuditEvent`、`ConsoleResource` 和内部 `OperatorContext` 不要求 Body 后缀。角色列表使用 `RoleListItem`，人员响应中的 `RoleSummary` 仍只包含角色 ID、编码、名称和系统角色标记；凭据类型的 `toString()` 继续只输出 `REDACTED`。

### 常量与异常归属

- 普通业务拒绝使用 `BizCheckFailException(message)`，参数与并发冲突复用 Foundation 公共类别。Platform 的 `OperationsErrorCode` 只保留登录失败/会话失效（401）和权限拒绝（403）。BFF 声明 Origin/媒体类型与依赖超时等入口契约；上游无效响应复用公共 `UPSTREAM_INVALID_RESPONSE`（502）。
- 可信 Header 和客户端超时分别留在拥有该协议的类中；不创建全局 `Constants` 杂物类。Origin 与 HTTP test 模式由 `OperationsBrowserProperties` 在启动时校验。
- `FailException` 表示明确拒绝，`ErrorException` 表示协议/系统失败，`ServiceUnavailableException` 表示依赖不可用；不再使用 `BrowserRequestRejected` 或 `PlatformProtocolException`。
- `GlobalExceptionHandler` 是唯一错误响应出口。BFF 的 `OperationsErrorStatusResolver` 声明认证/协议状态、Owner 配置不存在的 404 与依赖超时 504；公共并发冲突 409、上游无效响应 502 和依赖不可用 503 由 Foundation 处理，不自行构造错误体、Request ID 或时间戳。
- 公共 HTTP 客户端统一转换已知调用异常并保留 Cause：远端 5xx 优先按系统失败处理；4xx 完整错误体中的三个公共业务类别保留安全消息，显式注册的认证/权限码使用契约默认消息。未知 Code、解码失败、空 DTO、重定向返回 502，网络故障返回 503。BFF 只保留 Token 期限、必需字段等自身契约校验；未预期 RuntimeException 直接交给共享 MVC 返回 500。
- Web 层只在媒体类型解析时捕获明确格式异常；没有包住整个请求的通用 `execute(Supplier)` 或 `ResponseEntity<?>`。

## 构建与测试

在仓库的 `paytech/` 目录执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw -B verify -pl operations-console-bff/paytech-operations-console-bff-test -am
```

PayTech Parent、BOM 与 Foundation 由同一 Reactor 构建；`paytech-risk-decision-api`、`paytech-compliance-api` 已在 Reactor 内（第 06 步），`platform-support-api`、`client-contract-api`、`paytech-vault-client` 在各自 Owner 搬入 Reactor 之前仍从开发者已有的 Settings/Nexus 与 Maven 缓存解析同版本 Snapshot（版本写 `${project.version}`）。不在本仓库保存制品库凭据。环境准备见 [本地开发环境](../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md)。

默认 `auto` 测试启动真实随机 Loopback HTTP 端口，关闭 Nacos 和自动注册，无需 MySQL、Redis、RabbitMQ、Nacos 或其他业务服务。测试验证健康检查、管理端点暴露、Request ID、公共 MVC 组件复用及真实 BFF HTTP → Platform/Risk 有状态协议 Simulator，不使用方法 Stub 替代生产链路。Simulator 不代替 Owner 的真实领域/数据库测试。

2026-09-07 Risk 配置适配组件的 JDK 25 `clean verify` 通过 **1,005 项测试（13 个测试类）**，失败/错误/跳过均为 0。包含九路授权完成前的独立响应屏障、精确映射、严格输入/上游反例、恢复 Actor、无写重试、Problem 隔离、日志 Canary、空 Discovery、Owner/装配边界及非 Risk Route 的 65,536/65,537 通用上限回归。本轮配置门先得到 261 项中唯一的 65,536 预期 RED，修复后 261 项全绿。当前构建仅管理 JaCoCo 版本而未绑定执行，未生成数值覆盖率报告，不能据此声称达到覆盖率目标。以上仅为组件验证，不证明公共 Route 已启用、跨组件联调通过或已部署。

公共分页与 HTTP DTO 命名统一后的 JDK 25 `clean verify` 已通过 155 项测试，无失败或跳过。覆盖三个列表默认每页 10 条、五种可选条数、分页参数透传、后续空页与总数、无效分页短路和损坏分页响应 502；原有 JSON Token、无 Set-Cookie、当前授权、可信 Header、公共业务消息、5xx 状态优先和敏感日志测试全部保留。

角色员工数与角色删除阶段的 JDK 25 `clean verify` 通过 214 项测试，无失败、错误或跳过。新增真实 HTTP 回归覆盖当前页单次批量统计、空页跳过、按 ID 合并乱序结果、统计缺失/null/重复/额外/负数时返回 502，以及删除的 204、JSON、版本、幂等键、独立权限、Origin 和敏感日志边界。

员工删除阶段的 JDK 25 `clean verify` 通过 232 项测试，无失败、错误或跳过。新增回归覆盖 19 条公开路由的可信身份与 PLT-I02 边界、独立删除权限及导航按钮、双版本/原因校验、Origin/JSON/幂等键短路、204 无 Cookie、上游公共 400/409 安全消息与 metadata-only 日志；既有角色聚合和人员响应结构测试继续保留。

当前人员启用增量的 JDK 25 `clean verify` 通过 245 项测试，无失败、错误或跳过。20 条公开路由均覆盖可信身份与当前授权；新增测试验证停用权限不能替代启用权限，版本、原因、幂等键和 Origin/JSON 校验失败不调用命令，并验证方法、路径、Body 和 Header 透传、204、上游公共 400/409 及日志边界。

审计筛选与详情增量的 JDK 25 `clean verify` 通过 270 项测试，无失败、错误或跳过。新增真实 HTTP 回归覆盖全部筛选参数、时间闭开区间、默认分页与单端时间、非法筛选短路、21 条路由的可信身份和当前授权、历史属性排序/重复值/null/缺边、未知键、损坏详情 502、公共业务消息与 metadata-only 日志。本轮部署与真实数据库验收由 test 环境单独记录。

Gateway Target/Route 管理增量的 JDK 25 `clean verify` 通过 297 项测试，无失败、错误或跳过。新增回归覆盖六条浏览器路由、精确筛选与分页透传、上游分页总数和页窗口完整性、详情一次管理调用、Platform/Gateway 共同支持的 1000 字符 Path 上限、状态 PATCH 的身份/版本/原因/幂等键及版本严格递增一位、损坏聚合响应 502、配置不存在的安全 404，以及 `/gateway-admin` 只记录元数据的日志边界。本轮未提交、部署或执行真实环境验收。

`-Pmanual-test` 只选择 `manual` 测试；当前尚无人工联调用例，测试模块会明确报告无测试，而不把空执行当作验证成功。测试模块不安装、不发布、不产出部署 JAR。

## 本地启动

先按仓库 [local-environment](../../local-environment/README.md) 准备 Nacos，并确认 Namespace 已存在。仓库 Nacos 已启用鉴权，启动前须从开发者 Secret Store 或当前终端私密注入已初始化账号的 `SPRING_CLOUD_NACOS_DISCOVERY_USERNAME` 和 `SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD`；不在 YAML 或命令历史中填写真实凭据：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=mor-local
"$JAVA_HOME/bin/java" -Xms128m -Xmx512m \
  -jar paytech-operations-console-bff-app/target/paytech-operations-console-bff-app-1.0.0-SNAPSHOT-exec.jar
```

业务默认监听 `127.0.0.1:18201`，注册名为 `operations-console-bff`，由 Platform Target 的 `discoveryServiceName` 引用；Gateway 不再硬编码这个业务目标。管理监听独立绑定 `127.0.0.1:19201`，不注册 Nacos、不映射宿主端口。BFF、Gateway 和 Platform 必须配置同一个 Nacos Namespace；不要直接混用各项目不同的默认值。当前使用 `DEFAULT_GROUP`，若通过 Spring 标准配置修改分组，相关调用方也须一致。

不设置 Servlet context-path，Gateway 显式下游路径直接匹配 BFF 的 `/auth/`、`/authorization/`、`/iam/`、`/audit/`、`/gateway-admin/`、`/decision-rule-sets/`、`/decision-releases/`。完整登录依赖 Platform 手工 SQL、Bootstrap 和 ACTIVE Signing Key 初始化；Risk 路由还依赖对应 Owner、Platform 权限/Route 与 Gateway 配置。仅注册成功不能代表这些链路就绪。

只验证应用基础、暂不连接 Nacos 时可执行：

```bash
"$JAVA_HOME/bin/java" -Xms128m -Xmx512m \
  -jar paytech-operations-console-bff-app/target/paytech-operations-console-bff-app-1.0.0-SNAPSHOT-exec.jar \
  --spring.cloud.discovery.enabled=false \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.service-registry.auto-registration.enabled=false
```

在同一主机检查管理监听（test 容器健康探针在 BFF 容器内执行；业务端口的管理路径返回 404）：

```bash
curl --fail http://127.0.0.1:19201/actuator/health
curl --fail http://127.0.0.1:19201/actuator/health/liveness
curl --fail http://127.0.0.1:19201/actuator/health/readiness
```

使用 `Ctrl+C` 停止应用。

| 配置 | 默认值 | 用途 |
|---|---|---|
| `OPERATIONS_BFF_BIND_ADDRESS` | `127.0.0.1` | 监听与注册 IP；跨主机使用实际可达的私网地址，不填 `0.0.0.0` |
| `SERVER_PORT` | `18201` | 业务监听及 Nacos 注册端口 |
| `management.server.port` | `19201` | 独立 Actuator 管理监听，只绑定 `127.0.0.1` |
| `OPERATIONS_CONSOLE_ORIGIN` | `https://operations-console.local.test` | 精确浏览器 Origin，必须与 Gateway 一致；默认要求 HTTPS |
| `OPERATIONS_TEST_HTTP_ENABLED` | `false` | 仅允许独立 `test` Profile 下启用 localhost HTTP 测试入口 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos Discovery 地址 |
| `NACOS_NAMESPACE` | `mor-local` | 与 Gateway、Platform 一致的 Namespace ID |
| `SPRING_CLOUD_NACOS_DISCOVERY_USERNAME` | 无 | Nacos 客户端账号，由运行环境私密注入 |
| `SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD` | 无 | Nacos 客户端密码，由运行环境私密注入 |

Java 应用只使用私有 HTTP；公网 TLS 属于 Nginx。`forward-headers-strategy=none`，BFF 不重新解释 Gateway 已消费的代理 Header。部署时由网络限制 BFF Listener 的访问范围。

### 独立 test-environment 的 HTTP 入口

按用户要求，独立 Docker 测试环境使用 `http://localhost:28080`，由入口 Nginx 转发页面与 API。BFF 和 Gateway 必须同时显式设置：

```text
SPRING_PROFILES_ACTIVE=test
OPERATIONS_TEST_HTTP_ENABLED=true
OPERATIONS_CONSOLE_ORIGIN=http://localhost:28080
```

对应应用属性是 `operations.test-http-enabled`。开关为 `true` 时，激活 Profile 必须恰好只有 `test`，Origin 必须为规范的 `http://localhost:<1–65535>`，不能带路径、用户信息、查询或 Fragment；混合 Profile、其他主机或无端口均启动失败。开关关闭时继续只接受 HTTPS Origin，不根据请求 Header 自动降级。

HTTP test 与默认 HTTPS 使用同一 JSON Token 契约，都不读写 Cookie。Origin、JSON、Gateway 可信身份和每请求 PLT-I02 授权规则保持一致。

## 健康与公共 MVC 能力

同一 Listener 仅暴露 `/actuator/health`、`/actuator/health/liveness`、`/actuator/health/readiness`、`/actuator/info` 和 `/actuator/prometheus`。关闭端点发现页、JMX、组件详情与健康详情。Liveness/Readiness 只反映本进程状态；后续实际依赖的就绪条件随用例补充。

`paytech-mvc-web-starter` 提供 Request ID/MDC、请求响应与耗时 INFO 日志、`GlobalExceptionHandler` 和统一错误体；本服务不复制 Filter、Advice 或错误模型。通过标准 `logging.pattern.level` 的 `%kvp` 输出 Foundation 字段。`web.logging.metadata-only-path-prefixes` 按顺序配置七项：`/auth`、`/authorization`、`/iam`、`/audit`、`/gateway-admin`、`/decision-rule-sets`、`/decision-releases`。这些请求不读取或缓存日志 Body/Query，仅记录 Request ID、方法、安全路径、状态和耗时；Gateway Reason、管理筛选、Risk AST/Literal、原始幂等键及 Handler 指纹不得进入普通日志。

## 浏览器接口

Gateway 公共地址统一在下表路径前加 `/api/operations`，由 Platform Route Catalog 管理，不存在通配转发。

| 方法 | BFF 路径 | 用途 |
|---|---|---|
| POST | `/auth/v1/login` | Email / Bootstrap 登录；JSON 返回 Access/Refresh Token |
| POST | `/auth/v1/refresh` | 从 JSON refreshToken 读取凭据并轮换 |
| POST | `/auth/v1/logout` | JSON 传入 Refresh Token，撤销当前 Session |
| GET | `/auth/v1/context` | 当前身份与强制改密状态 |
| POST | `/auth/v1/password` | 修改本人密码，前端清除 Token 并重新登录 |
| POST | `/auth/v1/bootstrap/administrator` | 创建首个管理员并关闭 Bootstrap |
| GET | `/authorization/v1/navigation` | 当前权限过滤的菜单与按钮 |
| GET / POST | `/iam/v1/accounts` | 分页 / 创建人员 |
| POST | `/iam/v1/accounts/{accountId}/disable` | 停用人员 |
| POST | `/iam/v1/accounts/{accountId}/enable` | 按期望版本启用已停用人员，成功返回 204 |
| DELETE | `/iam/v1/accounts/{accountId}` | 按账号与成员双版本删除人员，成功返回 204 |
| PUT | `/iam/v1/accounts/{accountId}/roles` | 分配角色 |
| GET | `/iam/v1/roles` | 聚合角色分页、版本与关联员工数 |
| GET | `/iam/v1/role-permission-options` | 聚合当前可授予权限树，供创建角色使用 |
| GET | `/iam/v1/roles/{roleId}` | 聚合角色详情、版本、已选权限与可配置权限树 |
| POST | `/iam/v1/roles` | 创建自定义角色及权限集合 |
| PUT | `/iam/v1/roles/{roleId}` | 按期望版本原子更新角色名称与完整权限集合 |
| DELETE | `/iam/v1/roles/{roleId}` | 按期望版本删除角色，成功返回 204 |
| GET | `/audit/v1/events` | 按时间、操作者、动作、结果和请求 ID 筛选审计分页 |
| GET | `/audit/v1/events/{auditEventId}` | 历史事件详情、前后变更及安全属性 |
| GET | `/gateway-admin/v1/target-services` | 按 Code、Channel、状态筛选 Target 分页 |
| GET | `/gateway-admin/v1/target-services/{targetServiceCode}` | Target 详情 |
| PATCH | `/gateway-admin/v1/target-services/{targetServiceCode}/status` | 按期望版本切换 Target 状态 |
| GET | `/gateway-admin/v1/routes` | 按 Route Code、Target Code、Channel、状态筛选 Route 分页 |
| GET | `/gateway-admin/v1/routes/{routeCode}` | 一次返回 Route Root、Target、Permission、Header、Query 与 Guard 详情 |
| PATCH | `/gateway-admin/v1/routes/{routeCode}/status` | 按期望版本切换 Route 状态 |
| GET | `/decision-rule-sets/v1/authoring-context/{pointCode}` | 读取裁剪 Handler 指纹后的 Authoring Context |
| GET | `/decision-rule-sets/v1/rule-sets` | Rule Set 版本分页 |
| GET | `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` | Rule Set 详情 |
| POST | `/decision-rule-sets/v1/rule-sets` | 创建不可变 DRAFT 版本 |
| POST | `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` | Freeze 指定版本 |
| GET | `/decision-releases/v1/candidates` | TEST Candidate 分页 |
| GET | `/decision-releases/v1/candidates/{releaseId}` | Candidate 详情与 Owner 恢复事实 |
| POST | `/decision-releases/v1/candidates` | 从 FROZEN Rule Set 创建 Candidate |
| POST | `/decision-releases/v1/candidates/{releaseId}/validate` | 按确定性 Key 执行静态检查 |

账号、角色和审计三个列表均复用 Foundation `PagingRequestBody` 与 `PagingResponseBody<T>`：省略参数时为 `page=1&size=10`，页码范围为 1–100000，每页数量只接受 `10/20/50/100/200`。成功 JSON 统一为 `{"items":[],"page":1,"size":10,"total":0}`，超出末页返回空 `items` 并保留当前页和总数；角色查询也返回分页对象，调用方不能再按裸数组解析。角色分配弹窗可用 `size=200` 分页读取全部可分配角色。

审计 `AuditQueryRequestBody` 通过 GET Query 传递六个可选筛选：ISO Instant `occurredFrom`（包含）、`occurredTo`（不包含），以及精确匹配的 `actorId`（最多 128 字符，允许 `UNKNOWN`）、`actionCode`（最多 64）、`result`（`SUCCEEDED/DENIED/FAILED`）、`requestId`（最多 128）。字符串去除两端空白后为空则不筛选；支持单端时间，双端要求开始早于结束。未指定筛选时保留原列表行为和 `PagingResponseBody<ManagementModels.AuditEvent>` 形状。

审计详情返回本地 `AuditModels.AuditEventResponseBody`：`{event, changes:[{key,label,before,after}], attributes:[{key,label,values}]}`。三组值数组中的元素为字符串或 `null`；缺失前值或后值保留 `[]`。`before.`/`after.` 属性去掉前缀后按键归并，重复 Permission/Role ID 不去重；普通属性排除 `reason`（已在事件中）及前后变更项。常见键提供中文名称，未知键按原键展示。详情使用可信 Route Code `operations-audit-detail`，与列表复用 `OPERATIONS_AUDIT_READ`，仍执行当前 PLT-I02 与 Platform 资源授权；不存在或不可访问事件保留公共业务拒绝，损坏上游响应返回 502。

角色列表元素 `RoleListItem` 为 `{roleId,roleCode,roleName,systemRole,version,employeeCount}`。Client 将 Platform 的角色列表事实转换为本地 `RoleOverview`，`RoleListProcessor` 再通过内部 `/operations/v1/roles/employee-counts` 批量补充人数，完整保留 `page/size/total`。统计结果必须与当前页 Role ID 集合精确一致且人数非负；缺失、重复、额外或无效数据返回 502，不按零处理或返回部分列表。员工关联口径与删除规则由 Platform 决定。

BFF 不维护私有 Page 类或复制分页校验，出站转换只映射页面元素并保留 Platform 返回的 `page/size/total`。非法分页在入站授权后返回公共 `INVALID_REQUEST`（400），不发起对应管理查询。人员创建、停用、启用、删除和角色分配命令必须提供 `Idempotency-Key`；修改携带期望版本。BFF 不自动重试命令，不拥有数据库或业务事务。

启用人员使用独立 `OPERATIONS_ACCOUNT_ENABLE` 权限与可信 Route Code `operations-account-enable`。JSON `EnableAccountRequestBody` 为 `{"expectedVersion":1,"reason":"停用检查完成"}`，版本为正整数，原因非空且最多 500 字符。BFF 通过 Client Port 直接委托一个 Platform 命令，成功返回 204 空响应；是否允许启用、并发版本、幂等、会话与审计规则均由 Platform 执行，不新增转发 Processor 或本地异常处理。

删除人员使用独立 `OPERATIONS_ACCOUNT_DELETE` 权限与可信 Route Code `operations-account-delete`。JSON `DeleteAccountRequestBody` 为 `{"expectedVersion":1,"expectedMemberVersion":1,"reason":"人员离职"}`，账号与成员版本均为正整数，原因非空且最多 500 字符。Controller 直接委托 Client Port 的单个 Platform 删除命令，不增加空 Processor；成功返回 204 空响应，公共业务拒绝 400 和版本冲突 409 保留安全消息。当前授权、双版本检查、本人及管理员保护、关联撤权、Session 撤销、审计和幂等事务由 Platform 执行；BFF 不自行判断或修改人员事实。

创建角色与创建选项路由要求 `OPERATIONS_ROLE_CREATE`，详情与编辑路由要求 `OPERATIONS_ROLE_UPDATE`；每次仍先通过可信 Route Code 执行 PLT-I02。创建和更新同样必需 `Idempotency-Key`，`roleCode` 为 `[A-Z][A-Z0-9_]{0,63}`、`roleName` 非空且最多 255 字符、`reason` 非空且最多 500 字符；`permissionIds` 必填但允许空数组表示撤销全部，Platform 拒绝重复、无效和超出可授予范围的权限。更新只接受 `roleName`、`permissionIds`、`expectedVersion`、`reason`，不修改角色编码。

删除路由要求独立 `OPERATIONS_ROLE_DELETE`，可信 Route Code 为 `operations-role-delete`。请求必须包含 `Idempotency-Key` 和 JSON `DeleteRoleRequestBody`：`{"expectedVersion":1,"reason":"停止使用此角色"}`；版本为正整数，原因非空且最多 500 字符。BFF 直接透传单个删除命令，成功返回 204 空响应；Platform 检查当前权限上限、系统角色保护、员工关联与版本，并在本地事务中完成逻辑删除、撤权、审计和幂等记录。

创建和更新响应 `RoleResponseBody` 为 `{roleId,roleCode,roleName,systemRole,version,permissionIds}`；详情 `RoleDetailsResponseBody` 另外包含 `nodes`，创建选项 `RolePermissionOptionsResponseBody` 只包含 `nodes`。每个 `PermissionNode` 固定为 `{nodeId,displayName,permissionId,children}`：资源节点用 `resource:` 前缀、无资源权限用 `permission:` 前缀，后者放在 `group:other` 的“其他权限”组内并显示 Permission Code。分组 `permissionId=null`；其余节点对应独立 Permission ID。

目录权限独立，BFF 不根据父子关系隐式补授权限；前端选择父节点时勾选全部下级，选择子节点时保留必要祖先，部分选择显示半选。重开详情只恢复已保存的 Permission ID，不因父子联动扩张历史授权。同一 Permission 绑定多个资源时同步勾选，提交时按 Permission ID 去重。树只包含 Platform 返回的可授予范围；缺失父资源时只提升当前已授权节点，不补回父级名称或权限。重复标识、循环绑定或角色已选权限超出当前 Catalog 都失败关闭，任一上游失败不返回部分可提交树。可授予权限为空时正常返回空树。此次角色增量的部署结果由根任务与 test 环境单独记录。

## 认证与授权边界

- 所有写请求严格匹配 Origin 和 JSON。浏览器请求显式 `credentials: omit`，无 Cookie 认证或 CSRF 标记。同源 GET 可以没有 Origin；一旦带 Origin 则必须精确匹配。
- BFF 只接收 Gateway 重建的 Subject、Session、Realm、Channel、Actor、Context、Tenant、Member 与 Route Code。普通人员限 `TENANT/INTERNAL`，Bootstrap 不携带 Tenant 字段。部署网络必须确保外部不能绕过 Gateway 访问本 Listener。
- 每个受保护请求都以可信 Route Code 完成一次 Platform PLT-I02，再处理 Origin/媒体类型与 MVC 绑定；Platform 决定当前授权及强制改密/Bootstrap 限制，Owner 业务接口再次执行自己的规则。BFF 不相信浏览器给出的权限，也不缓存 Permission。
- Access/Refresh Token 都通过登录/刷新 JSON 返回。按用户最新要求，前端在当前标签页 sessionStorage 保存两 Token 及绝对过期时刻，模块启动时恢复，覆盖此前仅内存/整页刷新需登录的限制；不使用 localStorage、Cookie 或 URL，不缓存密码、Account 上下文或权限。有效 Access 继续正常请求，临近到期或 GET 首次 401 时可 JSON Refresh；成功轮换一起替换两 Token，退出、改密完成、Refresh 401、刷新后的 GET 唯一一次重试仍 401 或会话到期时清理，临时 429/503/网络错误保留凭据供恢复重试。sessionStorage 遵循浏览器 page session 生命周期，复制或恢复标签页可能复制/恢复内容，应用不主动跨标签页共享 Token。Platform 的一次性轮换、重用撤销、15 分钟 Access 与 8 小时绝对/30 分钟空闲期限不变；本次恢复增量的验证与部署记录见前端及 test 环境 README。
- Platform HTTP Client 使用共享 `@HttpExchange`、有界超时、Nacos/LoadBalancer、固定私网服务名，传播 `X-Request-Id`；不经 Gateway、不记录 Body、不重试。只有 `auto` Profile 可通过 `operations.platform-test-base-url` 指定 loopback Simulator。
- 外部错误由 Foundation 唯一 MVC Writer 生成为统一 Code-MoR Problem；除 `HEAD` 外使用
  `application/problem+json` 和六个必填成员，`HEAD` 保持无 Body。BFF 保留公共业务提示与约定浏览器状态，系统异常详情及未知远端消息不对外返回；Platform 已知 Target 的显式 Legacy Reader 仅保留到部署切换与观测完成。

环境当前部署与真实链路验收状态只查阅 [`CURRENT_IMPLEMENTATION.md`](../../agent-docs/CURRENT_IMPLEMENTATION.md)，运行步骤见 [`test-environment/README.md`](../../test-environment/README.md)。BFF 中没有 Mock 登录、Session Store 或私钥；本文的 Loopback 测试与产物构建不代表共享或生产环境就绪。

继续开发请读取根 `agent-docs` 路由文档与 [首版实施记录](../../agent-docs/plans/2026-09-04-operations-console-first-slice.md)。

2026-09-04 角色配置的历史组件记录：JDK 25 `clean verify` 182 项全通过。环境部署与跨组件验收事实统一由上述当前状态台账及其链接证据维护。

2026-09-04 操作权限拆分：五个按钮使用独立 Permission ID，BFF 仍按可信 Route Code 查询 Platform 的实时授权。189 项 clean verify 测试通过，含七条新绑定的允许/拒绝边界。

2026-09-04 角色员工数与删除：新增 `RoleListProcessor` 当前页批量聚合及独立删除接口，214 项 `clean verify` 测试通过。此轮部署和真实数据库验收由根任务与 test 环境另行记录。

2026-09-04 员工删除：增加账号与成员双版本删除命令，复用单个 Platform 调用，232 项 `clean verify` 测试通过。部署及真实数据库验收由根任务与 test 环境另行记录。

2026-09-04 人员启用：增加独立启用权限及单命令接口，245 项 `clean verify` 测试通过。部署及真实数据库验收由根任务与 test 环境另行记录。
