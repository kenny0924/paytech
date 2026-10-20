# commercial 运营控制台完善 技术方案

> 状态：阶段 2 / 3 评审稿（2026-10-20）。

## 1. 现状差异与影响（阶段 2）

- commercial 现有读接口：`GET /agreements/v1/{id}`、`GET /pricing-profiles/v1?kind&status`、`GET /commercial-terms/v1/versions/{id}`、`GET /commercial-term-versions/v1/{id}/prices`、`GET /rate-cards/v1`、`POST /commercial-prices/v1/resolutions`、`GET /commercial-prices/v1/{priceId}`。缺：合同 / Plan / Version 列表、目录读、BCR 读。
- Operations BFF `/commercial-admin/v1/**` 24 端点、Web 三页；目录码硬编码；错误 detail 丢失（公共 `HttpServiceClientFactory` 对 4xx 只保留 code）。
- client-master：运营侧已有客户审核页面与 Client 查询（阶段 2 由执行线核实可复用的 Client / Party / OE 查询接口；mor-test 目前无 OE 数据）。
- 受影响系统：commercial（只读接口）、operations-console-bff、operations-console-web、Platform 注册（新菜单 / 路由，块 95 续号或新块）。

## 2. 接口设计（阶段 3）

全部为只读查询，`AbstractProcessor`，无锁、无事务写入；分页统一 `page`（从 0）/ `size`（≤ 200），排序固定。

| 接口 | 入参 | 出参 |
|---|---|---|
| `GET /agreements/v1` | `clientId`、`localPartnerId`、`agreementType`、`status`、`entityNo`、`agreementNo`（前缀）、`page`、`size` | `items[]{agreementId, agreementNo, agreementName, agreementType, status, businessMode, fundMode, clientId, localPartnerId, entityNo, effectiveFrom, effectiveTo, signedTime, activatedTime, changeVersion, updateTime}`、`total` |
| `GET /commercial-terms/v1/plans` | `agreementId`、`clientId`、`localPartnerId`、`termType`、`status`、分页 | Plan 摘要 + 当前生效 / 已排期版本 ID 与生效时间 |
| `GET /commercial-terms/v1/versions` | `planId`、`agreementId`、`status`（多值）、`effectiveFrom/To` 区间、分页；工作台用 `status=PENDING_APPROVAL` / `SCHEDULED` | 版本摘要（含 maker / checker、effectiveFrom/To、priceCount） |
| `GET /products/v1`、`GET /products/v1/capabilities`、`GET /products/v1/fee-events` | 可选 `productCode`、`status` | 目录行（与初始化 DML 同字段） |
| `GET /business-changes/v1` | `aggregateRootType`、`aggregateRootId`（必填）、分页 | 按 `version_no` 倒序的快照：`{historyId, recordType, recordId, versionNo, operationType, createBy, createTime, meta{triggerType, triggerId, reasonCode}, data}`；字段级差异由 BFF 相邻版本比较 |
| F9 错误透传 | 方案 A：Foundation `HttpServiceClientFactory` 解码 Problem 时保留 `detail`，`FailException` 子类带 Owner detail，BFF 的 Problem 投影透传（影响全平台，需 Foundation 小改 + 回归）；方案 B：commercial 为激活校验、签署、订阅等拒绝注册专有错误码并在 BFF 映射文案 | **执行线先核实 Foundation 现状**：若 detail 已在异常对象上只是 BFF 未投影，直接在 BFF 透传；否则取方案 A 并限定为 4xx 业务检查 |

BFF 聚合：工作台一次返回三清单与计数；版本对比接口 `GET /commercial-admin/v1/commercial-terms/versions/{a}/diff/{b}` 在 BFF 拉两版价格后按（OE、币种、画像、事件、组件）键比较；合同列表页支持 Client 名称搜索时先调 client-master 查 ID。Web：菜单重排与仪表盘；表单目录改接口；错误 detail 展示。

### 2.1 F9 结论（H 线核实、I1 线临时实现、主会话决定，2026-10-20）

**核实链路（H 线）：** detail 在 Foundation 出站客户端处丢失，不是 BFF 投影问题。

1. commercial 业务拒绝已带具体中文 detail：Processor / Service 抛 `BizCheckFailException("审批人不能是提交人: …")` 等，`paytech-mvc-web-starter` 的 `GlobalExceptionHandler` 把 message 投影为 Problem `detail`；既有 auto 测试按 detail 片段断言。不需要为 F9 新增 commercial 错误码（方案 B 不采用）。
2. Foundation `paytech-http-client-support` 的 `core/error/HttpProblemDecoder` 严格校验 Problem 后只返回 `DecodedProblem(code, errors, strictProfile)`，不保留 `detail`。
3. `HttpServiceClientInterceptor#classifyClientCode` 对公共 4xx 一律用公共默认消息构造本地异常，方法注释明确"远端 detail 仍不外显"；原始 Body 只留在 Cause（`RestClientResponseException`）里。
4. BFF `CommercialClientConfiguration` 用 `HttpServiceClientFactory.create(...)` 创建代理，BFF 的 `GlobalExceptionHandler` 只能投影公共默认消息。

**I1 线临时实现（将被 F9 线替换）：** `operations-console-bff` 的 `CommercialOwnerProblemDetail` 在 commercial 代理外层读取 Cause Body 的 `detail` 重建同类型异常。它能工作且有测试，但在业务 Adapter 里重复解析 Problem JSON，违反 RFC 9457 设计 §6.3「业务 Adapter 不重复写 JSON 解码器」。I1 的其余部分保留：`CommercialPriceApi` 独立代理工厂登记 `CommercialPriceErrorCode`（422 `PRICING_FACTS_INCOMPLETE` / `PRICE_NOT_FOUND` / `INVALID_PRICING_FACT`、409 `PRICE_AMBIGUOUS`，`OperationsErrorStatusResolver` 状态映射）；Web `ApiError.detail`、`commercialErrorMessage` 对 400 `BUSINESS_CHECK_FAILED` 优先显示 Owner 原因并按场景回退。

**决定（方案 A，Foundation 显式开关，F9 线实施）：**

| 位置 | 改动 |
|---|---|
| `HttpProblemDecoder.DecodedProblem` | 增加 `String detail`；`readProblem` 填入已校验的 `problem.detail()`；旧四字段 Reader 为 null |
| `HttpServiceClientOptions` | 新增显式开关 `withOwnerProblemDetail()`（默认关闭），其他客户端行为不变 |
| `HttpServiceClientFactory.create(...)` / `configureGroup(...)` | 把开关传入 `HttpServiceClientInterceptor` |
| `HttpServiceClientInterceptor#classifyClientCode` | 开关开启且 `strictProfile` 时，`BUSINESS_CHECK_FAILED`（400）、`CONCURRENT_REQUEST`（409）、`NOT_FOUND`（404）与已登记合同码（如 422 价格命中码）用远端 `detail` 作为本地异常 message；detail 空白、超过 512 字符或含控制字符时回退公共默认消息；`INVALID_REQUEST`（字段错误已由 `errors` 投影）与 5xx 保持现状 |
| BFF `CommercialClientConfiguration` | 两个 commercial 代理工厂的选项链加 `.withOwnerProblemDetail()`；删除 `CommercialOwnerProblemDetail` 与其 `wrap`；BFF 透传测试改为断言经 Foundation 开关到达的 detail |

依据：DEVELOPMENT_CONVENTIONS §4 规定业务 / 并发异常 message 是可安全外显的人类提示，Owner 已承担 detail 的安全责任；开关按客户端显式开启，不影响其他 BFF 与内部调用（RFC 9457 设计 §6.4「安全 detail」）。验证：Foundation 拦截器 auto 测试覆盖开 / 关、各错误类别、超长与控制字符回退；BFF 测试断言 commercial 400 / 404 / 409 / 422 的 detail 到达浏览器 Problem 且 `requestId` 为 BFF 自己的值。

### 2.2 Client 选择器可复用的查询（I1 线调研，I2 实现）

- **没有现成的"运营按名称 / 编号搜索 Client"接口**。client-master 对运营只暴露入驻审核相关的 `ClientApplicationAdminApi`（按 caseId 读申请）与 `ClientClassificationAdminApi`；`ClientWorkspaceApi.summaries`（`GET /client-workspace/v1/client-summaries?clientId=…`，返回 clientNo、displayName、clientType、clientStatus、country、approvedAt）按 ID 批量读，但合同限定调用方为 client-console-bff 并要求 CUSTOMER 会话头。
- **运营控制台已在用的列表**：compliance `ClientReviewAdminApi.listCases`（`GET /client-onboarding-admin/v1/cases?page&size&status&qualificationType&applicationId`，Operations BFF 已接入），每条含 `clientId`、`caseNo`、`applicationId`、状态与时间，不含 Client 名称。
- **Party / OE**：`ClientMasterCheckoutApi.partyProfiles`（`GET /client-master/v1/party-profiles?ids=…`）按 ID 批量读 Party 资料；`checkout-candidates` 是按 Client 解析 Checkout 候选 OE 的私网接口，不是 OE 列表。client-master 没有 OE 列表接口，mor-test 也还没有 OE 数据。
- **建议**：I2 的 Client 选择器先用审核任务列表（`status=APPROVED`）取 clientId 候选，名称经 client-master 新增运营可调用的按 ID 摘要接口（或放开 `client-summaries` 给 operations-console-bff，并去掉 CUSTOMER 会话头要求）补齐；按名称 / 编号搜索、Party 与 OE 选择需要 client-master 另行提供运营查询接口，属于 client-master 的独立需求。

### 2.3 I2 线设计决定（2026-10-20）

- H 线实际合同：分页 `page` 从 1 起、`size` 只允许 10/20/50/100/200（上表“从 0”已被实现取代）；详见 `paytech/commercial/README.md`「运营控制台只读接口」。
- Client 选择器本期为「ID 输入 + 本机最近使用 + 从已通过审核任务选」，不新增 client-master 接口；按名称搜索与 Party / OE 下拉记为 client-master 遗留需求。
- 版本对比路径定为 `GET /commercial-admin/v1/commercial-term-versions/{baseId}/diff/{targetId}`，两版须同方案；变更历史字段级差异在 BFF 补 `changedFields`。
- 工作台不做“最近变更”卡片（没有全局 BCR 流）。Platform 登记用 ID 块 93（块 95 已 356 行）。完整任务书见 `notes/I2-brief.md`。

## 3. 数据库变更

不涉及（只读接口；BCR 读复用既有索引 `idx (aggregate_root_type, aggregate_root_id, history_id)`）。Platform 注册 DML：新路由 / 菜单（I1 续用块 95 至 95962，I2 用块 93；块 100–102 已由财务会话预定）。

## 4. 实施计划（阶段 4）

| 线 | 内容 |
|---|---|
| H commercial 只读接口 | 五组列表 / 目录 / BCR 读接口 + F9 核实与实现（commercial 侧部分） |
| I 控制台 | BFF 聚合（列表、工作台、对比、目录、历史、费率预览、试算）、Web 六页 + 仪表盘 + 导航、Platform 注册 DML、F9 BFF 侧 |

顺序 H → I；验证各自 auto 测试与构建；阶段 5 本机 BFF 冒烟；阶段 6 / 7 mor-test 替换 commercial / ops-bff / ops-web 并注册。
