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

## 3. 数据库变更

不涉及（只读接口；BCR 读复用既有索引 `idx (aggregate_root_type, aggregate_root_id, history_id)`）。Platform 注册 DML：新路由 / 菜单（延续块 95 空余号段，不够则用块 96 以后未占用段，执行线核实）。

## 4. 实施计划（阶段 4）

| 线 | 内容 |
|---|---|
| H commercial 只读接口 | 五组列表 / 目录 / BCR 读接口 + F9 核实与实现（commercial 侧部分） |
| I 控制台 | BFF 聚合（列表、工作台、对比、目录、历史、费率预览、试算）、Web 六页 + 仪表盘 + 导航、Platform 注册 DML、F9 BFF 侧 |

顺序 H → I；验证各自 auto 测试与构建；阶段 5 本机 BFF 冒烟；阶段 6 / 7 mor-test 替换 commercial / ops-bff / ops-web 并注册。

## F9 结论（H 线核实，2026-10-20）

**结论：detail 在 Foundation 出站客户端处就已丢失，不是 BFF 投影问题；取方案 A（Foundation 小改，按客户端显式开启），commercial 侧无需改动。**

核实链路：

1. commercial 业务拒绝已带具体中文 detail：Processor / Service 抛 `BizCheckFailException("审批人不能是提交人: …")` 等，`paytech-mvc-web-starter` 的 `GlobalExceptionHandler` 把 message 投影为 Problem `detail`（commercial 既有 auto 测试按 detail 片段断言，例如 `CommercialTermWriteHttpTest.test_makerEqualsChecker`）。不需要为 F9 新增 commercial 错误码（方案 B 不采用）。
2. Foundation `paytech-http-client-support` 的 `core/error/HttpProblemDecoder` 严格校验 Problem 后只返回 `DecodedProblem(code, errors, strictProfile)`，**不保留 `detail`**。
3. `core/error/HttpServiceClientInterceptor#classifyClientCode` 对公共 4xx 一律用公共默认消息构造本地异常：`new BizCheckFailException(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), failure)`，`ConcurrentRequestException`、`InvalidParameterException` 同理；方法注释明确"远端 detail 仍不外显"。原始 Body 只留在 Cause（`RestClientResponseException`）里。
4. operations-console-bff 的 `CommercialClientConfiguration` 用 `HttpServiceClientFactory.create(client, strict().withRequestValidationFailures()…, new CodeMessageEnum[0])`，适配器不捕获异常；BFF 的 `GlobalExceptionHandler` 只能把上述公共默认消息投影为浏览器 Problem 的 `detail`，因此页面只看到"业务检查失败"。BFF 再解析 Cause Body 属于重复 Problem 解码，违反 RFC 9457 设计 §6.3"业务 Adapter 不重复写 JSON 解码器"，不采用。

最小 Foundation 改动（本线不实施，交由 Foundation 范围执行）：

| 位置 | 改动 |
|---|---|
| `HttpProblemDecoder.DecodedProblem` | 增加 `String detail`；`readProblem` 填入已校验的 `problem.detail()`；旧四字段 Reader 为 null |
| `HttpServiceClientOptions` | 新增显式开关 `withOwnerProblemDetail()`（字段 `ownerProblemDetailEnabled`，默认 false），其他客户端行为不变 |
| `HttpServiceClientFactory.create(...)` / `configureGroup(...)` | 把开关传入 `HttpServiceClientInterceptor` 构造器 |
| `HttpServiceClientInterceptor#classifyClientCode` | 开关开启且 `strictProfile` 时，`BUSINESS_CHECK_FAILED`（400）与 `CONCURRENT_REQUEST`（409）用远端 `detail` 作为本地异常 message（超过 512 字符或含控制字符时回退公共默认消息）；`INVALID_REQUEST`、`NOT_FOUND`、合同 Code 与 5xx 保持现状 |
| BFF `CommercialClientConfiguration#factory` | 选项链增加 `.withOwnerProblemDetail()`；BFF 的 `GlobalExceptionHandler` 不改，仍用 BFF 自己的 Request ID 与时间构造 Problem，只是 detail 改为 Owner 的安全提示（符合 RFC 9457 设计 §6.4"安全 detail"） |

依据：DEVELOPMENT_CONVENTIONS §4 规定业务 / 并发异常的 message 是可安全外显的人类提示，Owner 已承担 detail 的安全责任；开关按客户端显式开启，不影响其他 BFF 与内部调用。验证：Foundation 拦截器 auto 测试覆盖开 / 关两种、超长与控制字符回退；BFF 测试断言 commercial 400 的 detail 到达浏览器 Problem 且 requestId 为 BFF 自己的值。I 线在 Foundation 改动落地前只能显示公共消息。
