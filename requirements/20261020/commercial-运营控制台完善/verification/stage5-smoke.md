# 阶段 5 本机冒烟（第二切片：commercial + Operations BFF + Web）

> 执行日期 2026-10-20（UTC 05:15–05:30），分支 `commercial/second-slice` 头 `886edda7`，worktree `/Users/kenny/Desktop/Workspace/Code-MoR-slice2`。未改源码，未提交，未执行 SQL（只做只读 SELECT）。
> 结论：HTTP 检查项全部符合实现合同，F9 透传在 API 与页面上都成立，只读接口未写库。发现 **1 个 Web 缺陷**（版本对比页路由未注册，页面显示“暂无访问权限”），以及 3 处验收清单草案与实现不一致，需在阶段 7 前处理。

## 1. 环境

| 项 | 值 |
|---|---|
| 构建 | `cd paytech && JAVA_HOME=jdk-25 ./mvnw -o -q -pl commercial/paytech-commercial-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -DskipTests package`（不带 `-am`，上游 JAR 取 ~/.m3）；Web `npm run build`（tsc + vite 生产构建）。核对：commercial JAR 含 `AgreementQueryController`、`CommercialTermQueryController`、`ProductCatalogController`、`BusinessChangeQueryController`；BFF JAR 含 `CommercialTermVersionDiffProcessor`、`CommercialBusinessChangeProcessor` 与 `paytech-http-client-support`（F9 开关只在 `CommercialClientConfiguration` 调用） |
| 运行副本 | JAR 与 `dist` 复制到会话 scratchpad `.../scratchpad/slice2/` 后运行 |
| commercial | `java -jar commercial.jar --spring.cloud.nacos.discovery.enabled=false`，端口 18108 / 19108，库 `mor_commercial_lineb`（`commercial_app`），RabbitMQ `/mor-local`，Redis 16379，`COMMERCIAL_SNOWFLAKE_WORKER_ID=30`，`COMMERCIAL_XXL_JOB_ENABLED=false` |
| ops-bff | `java -jar ops-bff.jar --server.port=18211 --management.server.port=19211 --spring.cloud.nacos.discovery.register-enabled=false`；经 Nacos `mor-local` **只查找** `platform-support-service`；`commercial-service` 用 Spring Cloud simple discovery 指向 `http://127.0.0.1:18108`；`OPERATIONS_CONSOLE_ORIGIN=https://127.0.0.1:18444` |
| Platform | 沿用主工作树本机全链路已在运行的 platform-support（pid 64440，18106，`mor_platform_v2e2e`，块 93 / 95 路由 ACTIVE）。本切片 Platform 只改能力清单 JSON，不影响导航与授权，因此只打包未替换 |
| Gateway 替身 | 主工作树的 Gateway（18080）与 ops-bff（18201）是旧版本且由其他会话启动，不能动。本次用 scratchpad 中的 `minigw.py`（127.0.0.1:18311）代替 Gateway：提供 Web 生产构建静态文件（SPA 回退），并把 `/api/operations/**` 转发到 18211。它从 `mor_platform_v2e2e.t_gateway_route` 只读加载 201 条 ACTIVE 运营路由，按方法 + 外部路径模板得出 Route Code 与下游路径，执行查询参数白名单，并按 Operations JWT 的 `sub / sid / context_type` 注入 `X-Gateway-*` 身份头。它**不校验 JWT 签名**（会话仍由 Platform 在 PLT-I02 校验），不转发 `Idempotency-Key` 与 `Authorization`，Origin 改写为 BFF 配置值，响应带 `X-Request-Id` |
| 操作者 | A = 本机运营管理员（`secrets.env` 的 E2E_ADMIN，Account `236033080844881920`，PLATFORM_ADMIN）。B = 冒烟期间经 IAM 接口新建的合成审批人（角色 `QA_COMMERCIAL_CHECKER_D8409E` = 238170142725378048，只含 CONTRACT_READ / PRICING_READ / PRICING_APPROVE；账号 238170143652319232） |
| 记录 | 请求 / 响应全量：[stage5-smoke-transcript.jsonl](stage5-smoke-transcript.jsonl)（101 条，登录与含口令的请求体已置空，Token 已脱敏）；截图：[screenshots/](screenshots/) |

**冒烟前已有数据（不是本次创建）：** 上一位被中止的冒烟 agent 在 UTC 04:10 留下方案 `SMK-CLIENT-PRICING-01` 的 v4（APPROVED，checker = A）、v5（PENDING_APPROVAL，maker = A），以及第二个方案 `SMK-SLICE2-PLAN-02`（PLATFORM_FEE，DRAFT）及其 v1（DRAFT）。本次计数以冒烟开始时为基线。

## 2. 本次创建的数据

| 对象 | ID | 说明 |
|---|---|---|
| 版本 vA | 238170172819628032（versionNo 6） | 复制 ACTIVE v3（5 条价格），A 提交，B 批准，现 APPROVED |
| 版本 vB | 238170374213328896（versionNo 7） | 复制 vA 后整体替换价格，现 DRAFT |
| 合成审批角色 / 人员 | 238170142725378048 / 238170143652319232 | 已清空权限 / 已停用（见第 5 节） |

vB 相对 vA 的改动：删除 USD × `SMK_CARD_DEFAULT`；新增 USD × `SMK_APPLE_PAY`（priority 2）；AED × `SMK_CARD_VISA_CREDIT_DOM` 的 `CAPTURE_PCT` 0.022 → 0.025。

## 3. 逐项结果

### 3.1 导航（F8）—— 通过

`GET /authorization/v1/navigation` → 200。「商务」（sort 55）下 8 个菜单按 sort：工作台 5、合同 10、价目 20、费率预览 22、价格试算 24、定价画像 30、产品目录 40、变更历史 50；另有 4 个按钮（合同维护、画像维护、价目维护、价目审批）。

### 3.2 工作台（F2）—— 通过

| 时点 | 状态 | metrics（待审批 / 已排期 / 草稿合同 / 待签署 / 生效合同） | approvalQueue |
|---|---|---|---|
| 初始 | 200 | 1 / 0 / 0 / 0 / 1 | v5（maker A，priceCount 5，submittedTime 04:11:11Z） |
| A 提交 vA 后 | 200 | **2** / 0 / 0 / 0 / 1 | v5、v6，按提交时刻升序 |
| B 批准 vA 后 | 200 | **1** / 0 / 0 / 0 / 1 | 只剩 v5 |
| B 读工作台 | 200 | — | — |

metrics 恰好 5 项；队列行带 `makerAccountId`、`submittedTime`、`effectiveFrom`、`priceCount`；没有合同号（I2-gaps G2，已知）。

### 3.3 合同列表（F1）—— 通过

| 请求 | 状态 | 结果 |
|---|---|---|
| 无参 | 200 | 1 条 `SMK-CLIENT-20261020-01`（ACTIVE、AE001），`page=1, size=10, total=1` |
| `status=ACTIVE&status=DRAFT` | 200 | 重复参数被接受，1 条 |
| `status=DRAFT&status=PENDING_SIGNATURE` | 200 | 0 条 |
| `agreementNo=SMK-CLI` | 200 | 前缀命中 1 条；`agreementNo=ZZZ` → 0 条 |
| `clientId=770000000001&agreementType=CLIENT&entityNo=AE001&page=1&size=10` | 200 | 1 条 |
| `size=7` | **400** | `INVALID_REQUEST`：每页条数必须为 10、20、50、100 或 200 |
| `size=201` | 400 | 同上 |
| `page=0` | **400** | `INVALID_REQUEST`：页码必须在 1 到 100000 之间（见第 4 节 D2） |
| `status=FOO` | 400 | `INVALID_REQUEST` |
| `foo=1` | 400 | 被 Gateway 替身按 `t_gateway_route_query_param` 白名单拒绝（白名单与 Platform 注册一致：agreementNo、agreementType、clientId、entityNo、localPartnerId、page、size、status） |

### 3.4 方案 / 版本列表（F3）—— 通过

| 请求 | 状态 | 结果 |
|---|---|---|
| `GET /commercial-terms/plans` | 200 | 2 条；行带 `activeVersionId / activeVersionNo / activeEffectiveFrom`、`scheduledVersionId / scheduledVersionNo / scheduledEffectiveFrom`、`changeVersion` |
| `…/plans?agreementId=238041051456200704&termType=CLIENT_PRICING` | 200 | 1 条 `SMK-CLIENT-PRICING-01` |
| `…/versions?planId=238041176094138368&size=50` | 200 | 5 条（冒烟前），版本号倒序；行带 maker / checker、effectiveFrom / To、`priceCount`（v1 为 4，其余 5） |
| `…/versions?status=PENDING_APPROVAL` | 200 | 1 条 v5 |
| `…/versions?status=APPROVED&status=SCHEDULED` | 200 | 2 条（v4、v1） |
| `…/versions?status=NOPE` | 400 | `INVALID_REQUEST` |

### 3.5 新建版本、提交、审批（F2 / F9）—— 通过

| 请求 | 操作者 | 状态 | 结果 |
|---|---|---|---|
| `POST …/plans/{plan}/versions`（copyFrom v3，cv 23） | A | 201 | vA versionNo 6，DRAFT，cv 24 |
| `POST …/versions/{vA}/submit`（cv **1**） | A | **409** | `CONCURRENT_REQUEST`，detail「业务版本已变化，请刷新后重试: COMMERCIAL_TERM_PLAN 238041176094138368，当前版本 24，期望版本 1」（Owner 原文） |
| `POST …/versions/{vA}/submit`（cv 24） | A | 200 | PENDING_APPROVAL，cv 25 |
| `POST …/versions/{vA}/approve` | A | **400** | `BUSINESS_CHECK_FAILED`，detail「审批人不能是提交人: 236033080844881920, 版本 238170172819628032」（Owner 原文） |
| `POST …/versions/{vA}/approve` | B | 200 | APPROVED，cv 26 |
| `POST …/versions/{vB}/submit` | B（无 MANAGE） | 403 | `OPERATIONS_PERMISSION_DENIED`，PLT-I02 拒绝，未到 commercial |
| `POST …/plans/{plan}/versions`（copyFrom vA） | A | 201 | vB versionNo 7，DRAFT |
| `PUT …/versions/{vB}/prices`（5 条，见第 2 节） | A | 200 | cv 28 |

### 3.6 版本对比（F3）—— 接口通过，页面失败（D1）

| 请求 | 状态 | 结果 |
|---|---|---|
| `GET …/versions/{vA}/diff/{vB}` | 200 | added 1（AE001 / USD / SMK_APPLE_PAY）、removed 1（AE001 / USD / SMK_CARD_DEFAULT）、changed 1（AE001 / AED / SMK_CARD_VISA_CREDIT_DOM：`components[CAPTURE_PCT].rate` 0.022 → 0.025）、unchanged 3、`versionDifferences=[]`；带 `base`、`target` 版本摘要 |
| `GET …/versions/{vB}/diff/{vA}` | 200 | 方向相反：added = USD DEFAULT，removed = USD APPLE_PAY，changed 0.025 → 0.022 |
| vA 对另一方案的 v1（238152008643764224） | **400** | `BUSINESS_CHECK_FAILED`：对比的两个版本不属于同一方案: 版本 … 属于方案 238041176094138368, 版本 … 属于方案 238151983989645312 |
| vA 对不存在的 ID | 404 | `NOT_FOUND`：商业条款版本不存在: 999999999999999999（Owner 原文） |
| vA 对 vA | 400 | `INVALID_REQUEST`：对比的两个版本不能相同 |

### 3.7 产品目录（F4）—— 通过，与库一致

| 请求 | 状态 | 条数 | `mor_commercial_lineb` 只读 SELECT |
|---|---|---|---|
| `GET /products`（及 `status=ACTIVE`） | 200 | 7（ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM） | `t_product` 7 |
| `GET /products/capabilities` | 200 | 11 | `t_product_capability` 11 |
| `…/capabilities?productCode=ACQUIRING` | 200 | 6；`CARD_VISA` 带 `CARD_BRAND=VISA` | 6 |
| `GET /products/fee-events` | 200 | 21 | `t_product_fee_event` 21 |
| `…/fee-events?productCode=ACQUIRING&termType=CLIENT_PRICING` | 200 | 6（AUTH、CAPTURE、SALE、REFUND、CHARGEBACK、TOKEN_STORED） | 6 |
| `…/fee-events?productCode=ACQUIRING&termType=PLATFORM_FEE` | 200 | 0 | 0 |
| `products?status=FOO`、`capabilities?productCode=NOPE` | 400 | `INVALID_REQUEST` | — |
| `fee-events?termType=FOO` | 200 | 0（见观察 O1） | — |

响应外层分别为 `{products}`、`{capabilities}`、`{feeEvents}`，不分页。

### 3.8 变更历史（F5）—— 通过

`GET /business-changes?aggregateRootType=AGREEMENT&aggregateRootId=238041051456200704&size=50` → 200，11 条，按 `versionNo` 倒序（同一版本内合同本身在前、子记录随后）。每条带 `historyId、recordType、recordId、versionNo、operationType、createBy、createTime、meta{triggerType, triggerId, reasonCode}、data、previousData、previousVersionNo、changedFields、comparison`：

- v1 `AGREEMENT_CREATED`：`comparison=FIRST_VERSION`，changedFields 为全部 17 个字段。
- v2 `AGREEMENT_SUBMITTED_FOR_SIGNATURE`：`COMPARED`，prev 1，changedFields `[status]`。
- v4 `AGREEMENT_PARTY_ACCEPTED`：`[signedTime, status]`；v5 `AGREEMENT_ACTIVATED`：`[activatedTime, status]`。
- v6–v8 的子记录 `AGREEMENT_PRODUCT` / `AGREEMENT_CAPABILITY` 为 `FIRST_VERSION`。

`COMMERCIAL_TERM_PLAN` 238041176094138368 → 200，total 57，本次 vA / vB 的创建、提交、批准、价格替换都在列，操作者分别为 A 与 B 的 Account ID。`PAYMENT_METHOD_PROFILE` 238040976449462272 → 200，2 条。

负向：`aggregateRootType=FOO` → 400；缺 `aggregateRootId` → 400；不存在的根（ID 1）→ 200 空页。

### 3.9 F9 错误透传 —— 通过

| 场景 | 浏览器 Problem |
|---|---|
| 审批人 = 提交人（API） | 400 `BUSINESS_CHECK_FAILED`，detail 为 commercial 原文 |
| 激活未排期版本：`POST …/versions/{vA}/activate` | 400 `BUSINESS_CHECK_FAILED`，detail「激活校验 1（合同与状态）：版本状态须为 SCHEDULED，实际 APPROVED: 238170172819628032」，vA 状态不变 |
| 过期 changeVersion 提交 | 409 `CONCURRENT_REQUEST`，detail 为 commercial 原文 |
| 价格试算缺 `FUNDING_TYPE` | 422 `PRICING_FACTS_INCOMPLETE`，detail「缺少定价事实: FUNDING_TYPE」 |
| 版本对比不存在的 ID | 404 `NOT_FOUND`，detail 为 commercial 原文 |
| **非商务路由对照**：`PUT /iam/v1/roles/{合成角色}`，`expectedVersion=999` | 409 `CONCURRENT_REQUEST`，detail 仍为通用「请求发生并发冲突，请稍后重试」，证明开关只在商务客户端工厂打开 |

requestId：浏览器 Problem 的 `requestId` 是本次入站请求 ID（由 Gateway 替身生成并经 `X-Request-Id` 传入），BFF 日志中同一 requestId 的行记录了 BFF 自己构造的 400 响应体与 timestamp；Problem 由 BFF 的 MVC 边界输出，不是转发 commercial 的响应体。commercial 日志格式不含 requestId，无法在 commercial 侧逐条对照。

**页面：** 在工作台对 v5 点「批准」（A 是 v5 的提交人），弹窗显示「业务校验未通过：审批人不能是提交人: 236033080844881920, 版本 238151920773095424」（[10-workbench-f9-error.png](screenshots/10-workbench-f9-error.png)），v5 状态不变。

### 3.10 价格试算与费率卡（F6 / F7）—— 通过

- 试算 v3（ACTIVE）、USD、VISA / CREDIT / DOMESTIC、amount 100.00 → 200，命中 `SMK_CARD_VISA_CREDIT_DOM`；`illustration` 按事件给出 AUTH 0.10、CAPTURE 3.20（100 × 0.022 + 1.00）、CHARGEBACK 100.00、REFUND 2.00。
- 去掉 `FUNDING_TYPE` → 422（见 3.9）；`CARD_BRAND=AMEX` → 200，命中兜底 `SMK_CARD_DEFAULT`。
- 用已 SUPERSEDED 的 v2 在当前时点试算 → 409 `PRICE_AMBIGUOUS`，detail「价格父链冲突: …时点适用 false」（与首切片观察 O1 相同）。
- `GET /rate-cards?clientId=770000000001&asOf=<now>&currency=AED` → 200，合同 `SMK-CLIENT-20261020-01` 下 `termVersions` 为 v3 ACTIVE / CURRENT。

### 3.11 Web 生产构建 —— 8 页可打开，版本对比页失败

Headless Chromium（playwright-core 1.58.1）经 Gateway 替身登录 A，逐页 `goto` + networkidle，截图存 [screenshots/](screenshots/)：

| 页面 | HTTP | 页面内容 | 截图 |
|---|---|---|---|
| `/commercial/workbench` | 200 | 5 张卡片 + 待审批队列 | 01 |
| `/commercial/agreements` | 200 | 列表含 `SMK-CLIENT-20261020-01` | 02 |
| `/commercial/terms` | 200 | 方案列表含 `SMK-CLIENT-PRICING-01` | 03 |
| `/commercial/terms/compare/{vA}/{vB}` | 200 | **「暂无访问权限」**（D1） | 04 |
| `/commercial/history?aggregateRootType=AGREEMENT&aggregateRootId=…` | 200 | 时间线、字段级摘要正常 | 05 |
| `/commercial/rate-cards` | 200 | 正常 | 06 |
| `/commercial/price-simulator` | 200 | 正常 | 07 |
| `/commercial/pricing-profiles` | 200 | 正常 | 08 |
| `/commercial/catalog` | 200 | 含 ACQUIRING | 09 |

全程无 ≥ 400 的网络响应（除 3.9 中预期的批准拒绝 400），无控制台错误。在价目页「版本」页签按方案筛选、勾选 vA / vB、点「对比所选」，同样跳到 `/commercial/terms/compare/…` 并显示「暂无访问权限」（[11-terms-compare-click.png](screenshots/11-terms-compare-click.png)）。

## 4. 发现的问题

### 缺陷

- **D1 Web 版本对比页不可达。** `operations-console-web/src/navigation/menu.ts` 的 `pageRoutePaths` 对 `OPERATIONS_COMMERCIAL_TERMS` 只注册了 `/commercial/terms` 与 `/commercial/terms/versions/:versionId`，没有 `CommercialTerms.tsx` 与 `consoleRoutes.compare` 使用的 `/commercial/terms/compare/:baseVersionId/:targetVersionId`。路由落到 `*`，页面显示 NoAccess。直接打开链接和点「对比所选」都复现。BFF 对比接口本身正常。修复方向：在该 case 中补上 compare 路径，并加一条 Web 测试覆盖。AC-03 页面部分在修复前不通过。

### 验收清单草案（release/acceptance.md）与实现不一致

- **D2 分页从 1 开始。** 实现（Foundation `PagingRequestBody`）只接受 `page` 1–100000，`page=0` 返回 400。acceptance.md 的 F1-3、F3-2、F5-1 写了 `page=0`，technique.md §2 也写“`page`（从 0）”，阶段 7 照抄会得到 400。I2 任务书写的是“默认 1”，与实现一致。建议改清单与 technique.md。
- **D3 `versionDifferences` 不含版本号。** acceptance F3-4 期望“`versionDifferences` 至少含 `versionNo`”。实现（`CommercialPriceDiffer.versionDifferences`）只比较 `roundingMode`、`pricingModel`、`feeTaxTreatment` 三个条款级设置，版本号与状态在 `base` / `target` 摘要中。同为复制得来的两版，`versionDifferences` 为空数组。建议改清单期望，或由主会话决定是否要把版本号纳入。
- **D4 F1-6 / F6-3 的查询参数白名单检查依赖 Gateway。** 本机没有经过真实 Gateway，`foo=1` 的 400 来自替身按同一张白名单表的模拟，真实 Gateway 行为仍需阶段 7 验证。

### 观察项（不阻塞）

- **O1** `fee-events?termType=FOO` 返回 200 空列表，而 `capabilities?productCode=NOPE` 与 `products?status=FOO` 返回 400，校验口径不一致。
- **O2** commercial 版本快照的字段集合不稳定：创建与价格替换快照带 `priceCount`，提交与批准快照不带。BFF 因此把提交那一条的 `changedFields` 算成含 `priceCount`（实际只是字段缺失）。价格替换不改变版本快照的任何字段（条数相同时 `changedFields=[]`），变更历史看不到价格层面的改动。
- **O3** 变更历史页“首个快照”标签与摘要文字重复（截图 05 中显示为「首个快照 首个快照：…」）。
- **O4** 工作台、版本列表“合同”列只有合同 ID（I2-gaps G2，已知）；驳回不收原因（G1，已知，页面已注明）。
- **O5** commercial 日志格式不输出 requestId，跨 BFF / commercial 对照只能靠时间。

## 5. 只读写库核对与清理

`mor_commercial_lineb` 各表 `COUNT(*)` 冒烟前 / 后：

| 表 | 前 | 后 | 增量来源 |
|---|---|---|---|
| t_commercial_term_version | 6 | 8 | vA、vB |
| t_commercial_term_price | 24 | 34 | vA 5 条 + vB 5 条（替换后） |
| t_commercial_term_component | 110 | 154 | vA 23 个 + vB 21 个（逐价格核对） |
| t_business_change_record | 16 | 18 | vA、vB 两个版本记录 |
| t_business_change_version | 70 | 80 | 本次 10 次写命令的快照 |
| 其余 16 张表（合同、参与方、开通、产品、画像、目录、补偿事件等） | — | 不变 | — |

增量全部属于本次创建的 vA / vB 及其 BCR；列表、目录、历史、对比、工作台、试算、费率卡均未写库。`t_compensation_event` 仍为 0。

清理：

- 合成审批人员 B 已停用（`POST /iam/v1/accounts/{id}/disable` → 204，之后 B 登录 401）；合成角色权限已清空（`PUT /iam/v1/roles/{id}` → 200，permissionIds 为空）。这两步与角色、账号的创建都经 IAM 接口写入 `mor_platform_v2e2e`，Platform 审计不回滚。
- 本次启动的 commercial（pid 98462）、ops-bff（pid 1470）、Gateway 替身（pid 8660）均已停止，18108 / 19108 / 18211 / 19211 / 18311 无监听，`ps` 无残留；主工作树的 platform（64440）、Gateway（64850）、ops-bff（64775）未触碰，仍在运行。
- 含口令与 Token 的 scratchpad 状态文件已删除。`mor_commercial_lineb` 中的 vA / vB 保留，供主会话决定是否清理。
- 服务日志：commercial、ops-bff 均 0 条 ERROR，日志中没有口令或 Bearer Token。
