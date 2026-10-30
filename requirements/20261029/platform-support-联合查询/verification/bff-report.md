# 联合查询 · Operations BFF 交付报告（jq-bff，2026-10-29）

范围：仅 `paytech/operations-console-bff/**`。未提交、未部署，没有写数据库。

## 实现
- `ext/rest/DataQueryKeyTypeController`：实现 K1—K5，挂在 `/data-query-admin/v1`，路径与 Platform 相同（`/key-types`、`/key-types/{keyTypeId}`、`/key-types/{keyTypeId}/status`、`/key-type-order`）。
- `ext/rest/DataQueryJointController`：实现 J1—J3，挂在 `/data-query/v1/joint-query/{settings,graph,steps}`。`entryKeyType` 在本地按 `[A-Z][A-Z0-9_]{1,63}` 校验，不合格返回 400。
- `PlatformDataQueryClient` 和 `PlatformDataQueryClientImpl` 新增 8 个方法，Header 与现有方法一致：所有方法都带 actor/session；J1—J3 还带 `X-Caller-Service=operations-console-bff`。核对规则：
  - K1 复用现有分页核对，元素身份为 keyTypeId。
  - K2—K4 核对写回执：`objectType=QUERY_KEY_TYPE`。K2 还要求 objectCode 等于本次编码、状态为 ACTIVE；K3 要求 objectId 一致；K4 要求状态等于目标状态。
  - K5 要求结果的 keyTypeId 顺序与请求一致，且 sortNo、version 都大于 0。
  - J3 要求返回的 depth 与请求一致。
  - 其他情况一律做严格投影，出错按 502 处理。
  - 带 Body 的请求遇到 422，字段错误按本次 Body 投影，保留 Platform 文案，与现有数据查询处理一致。
- `PlatformClientConfiguration`：`QueryKeyTypeManagementApi` 和 `JointQueryApi` 复用 `operationsDataQueryPlatformProxyFactory`。因此读超时同样是 `operations.data-query-read-timeout`（9s），同样登记 DataQueryErrorCode、开启 Owner detail，并保留 503 DATA_SOURCE_UNAVAILABLE。
- 新增 BFF 模型：从 Platform 合同逐字段复制，只改包名，包括 request 10 个、response 17 个。
- 模板模型的改动：
  - Create/Update/Validate 请求新增 `joint`（@Valid）。
  - Detail 新增顶层 `joint` 和 `jointQuery`。
  - Version 新增 `joint` 和 `jointQuery`。
- `application.yml`：在 metadata-only 前缀中新增 `/data-query-admin/v1/key-types` 和 `/data-query-admin/v1/key-type-order`。

## 测试
命令：JDK 25，`./mvnw -q -o -Dmaven.repo.local=~/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ -pl operations-console-bff/paytech-operations-console-bff-app,operations-console-bff/paytech-operations-console-bff-test install -Dtest='com/paytech/operations/**/*' -Dsurefire.failIfNoSpecifiedTests=false`。依赖的 Platform api 模块此前已用 `-am` 构建过一次。

结果：BFF 测试模块 63 个类共 2483 个测试，0 失败、0 错误、0 跳过。其中新增的 `DataQueryJointBrowserAutoTest` 有 33 个测试，覆盖：
- 8 个端点的转发：方法、路径、Query、Body，Header（actor/session/caller），以及不得转发 Authorization、Cookie、Idempotency-Key。
- 8 个端点在 PLT-I02 拒绝时返回 403。
- Platform 的 400/403/404/409 原样到达浏览器，并带浏览器 Request ID。
- J3 的 503 DATA_SOURCE_UNAVAILABLE。
- 422 字段投影：K2 的 `valuePattern`、J3 的 `keys[0].values[n]`/`depth`、模板保存的 `joint.keyParameter.name` 和 `joint.columns[1].keyType`。
- 模板 joint 往返：读取详情后按原样 PUT，joint 原样送到 Platform；校验接口同样带上 joint。
- 本地拒绝：未知字段，包括 joint 内的未知字段；标量强转；格式、枚举或必填不符；路径中的非法 ID；缺少 entryKeyType。
- 502 核对：回执的类型、身份或状态不符，排序结果顺序不符，sortNo 为 0，depth 不符。

现有测试的调整：
- `data-query-boundary.json`：Q09、Q14 响应和 Q10、Q11、Q16 请求加上 joint/jointQuery，Q10 和 Q16 带完整配置，其余为 null。这样现有的 Q 转发测试也能证明 joint 不会被丢掉。
- `OperationsConsoleBffApplicationAutoTest`：路由清单加上 8 个新入口，metadata-only 清单加上 2 个前缀。

## 合同问题
1. **J2 的 422 到不了浏览器。** Foundation `HttpServiceClientInterceptor` 只在方法带 @RequestBody 时把 422 转成字段错误。GET 上的 422 VALIDATION_ERROR 不是登记过的合同 Code，BFF 会返回 502 UPSTREAM_INVALID_RESPONSE（已用测试验证）。建议：入口键类型不存在、未启用或不允许作为入口时，Platform 返回 400 BUSINESS_CHECK_FAILED 并带安全 detail；BFF 已测试会原样透传。页面应把 J2 失败当作 400 的 detail 提示，而不是字段错误。
2. **路由 Code 是我自拟的，块 N 登记时请对齐或修改测试清单。** BFF 测试清单 `data-query-joint-boundary.json` 用的是：
   - `operations-query-key-type-list/-create/-update/-status/-order`
   - `operations-joint-query-settings/-graph/-step`

   `DataQueryRegistrationAutoTest` 只核对块 131 和 18 条现有路由，没有检查新路由。块 N 落地后，应在该测试中加入两个新 Controller 和新块。
3. **现有偏差，未改，与本需求无关：** BFF 模板 `body` 的上限是 `@Size(max = 65536)`，Platform 合同已经是 49152。超过 48KB 的正文仍由 Platform 返回 422。
