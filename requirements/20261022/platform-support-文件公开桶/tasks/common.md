# 公共合同（A、B 两卡共同遵守，主会话定稿）

需求：[requirement.md](../requirement.md)（AC-01—AC-15，B1—B8）；方案：[technique.md](../technique.md)（以本卡为准，冲突时本卡优先）。原型：`operations-console-web/docs/operations-console.pen` y=86700 一行 5 个画板（存储管理 01—03、文件记录 01—02）。

基线：main `42879401`。mor-test 已执行文件多存储第二段删除 DDL（Platform Owner 列 / 表已删）。

## 0. 通用

- 访问级别取值 `PRIVATE` / `PUBLIC`（Wire 为字符串，内部为枚举 `FileAccessLevel`）。
- 版本状态 `CANDIDATE` / `CURRENT` / `SUPERSEDED` / `REJECTED`（内部枚举 `FileVersionStatus`，Wire 字符串）。
- 新增检查结果 `PUBLIC_ACCESS_ALLOWED`、`PUBLIC_READ_FAILED`。
- 错误：不新增错误码。替换冲突沿用现有 `FILE_STATE_CONFLICT`（409）；业务拒绝 `BizCheckFailException`（400）；并发版本 `ConcurrentRequestException`（409）；无路由 / 桶不可写 503。technique.md 中的 `FILE_REPLACEMENT_CONFLICT` 作废。
- 所有 ID、字节数在 Wire 上是十进制字符串；时间是 `Instant`（UTC，截到微秒）。

## 1. 内部文件接口 `InternalFileApi`（`/internal/files/v1`）

| 变更 | 内容 |
|---|---|
| 改 `StartFileUploadRequestBody` | 加 `accessLevel`：可选，`@Pattern("PRIVATE\|PUBLIC")`，缺省 `PRIVATE` |
| 新 `POST /files/{fileId}/replacements` | 请求 `StartFileReplacementRequestBody`：`filename`、`mimeType`、`sizeBytes`、可选 `uploaderAccountId`，约束与 `StartFileUploadRequestBody` 同名字段一致；响应 201 `FileUploadResponseBody`（不变） |
| 改 `FileUploadCompletionResponseBody` | 加 `int versionNo`、`String accessLevel` |
| 改 `StoredFileResponseBody` | 加 `String accessLevel`、`int versionNo`、`String publicUrl`（可空）、`String versionedUrl`（可空）、`Integer pendingVersionNo`（可空） |

## 2. 存储管理接口 `FileStorageManagementApi`（`/storage-admin/v1`，Header `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 不变）

### 2.1 桶与路由（修改）

| 变更 | 内容 |
|---|---|
| `StorageBucket` | 加 `accessLevel`、`publicBaseUrl`（可空） |
| `RegisterStorageBucketRequestBody` | 加 `accessLevel`（`@NotBlank`，`PRIVATE\|PUBLIC`）、`publicBaseUrl`（可空，`@Size(max=255)`；业务校验：`PUBLIC` 必填、`https://`、无查询串 / 片段、不以 `/` 结尾；`PRIVATE` 必须为空） |
| 新 `PUT /instances/{instanceCode}/buckets/{bucketName}` | `UpdateStorageBucketRequestBody`：`publicBaseUrl`、`Integer expectedVersion`（必填）、`reason`；仅 `DRAFT` 且 `PUBLIC` 桶可改；返回 `ConfigurationMutationResponseBody`；审计 `OPERATIONS_FILE_STORAGE_BUCKET_UPDATE` |
| `StorageRouteResponseBody` | 加 `accessLevel` |
| `CreateStorageRouteRequestBody` | 加 `accessLevel`（`@NotBlank`） |
| `PUT /routes/{sourceSystem}/{accessLevel}`、`DELETE /routes/{sourceSystem}/{accessLevel}` | 替换原 `/routes/{sourceSystem}`；`(*, PRIVATE)` 不能删 |

### 2.2 检查策略（新增，权限：读 `FILE_STORAGE_READ`，写 `FILE_STORAGE_MANAGE`）

| Method / Path | 请求 | 响应 |
|---|---|---|
| `GET /scan-policies` | — | `ScanPolicyListResponseBody { List<ScanPolicySummary> items; List<String> supportedMimeTypes }` |
| `GET /scan-policies/{policyCode}` | — | `ScanPolicyDetailResponseBody` |
| `POST /scan-policies` | `CreateScanPolicyRequestBody { policyCode; List<String> allowedMimeTypes; String maxSizeBytes; reason }` | `ConfigurationMutationResponseBody` |
| `POST /scan-policies/{policyCode}/revisions` | `CreateScanPolicyRevisionRequestBody { reason }`（以 ACTIVE 为模板；已有 DRAFT 时 409） | 同上 |
| `PUT /scan-policies/{policyCode}/revisions/{revisionNo}` | `UpdateScanPolicyRevisionRequestBody { allowedMimeTypes; maxSizeBytes; Integer expectedVersion; reason }` | 同上 |
| `DELETE /scan-policies/{policyCode}/revisions/{revisionNo}` | `DiscardScanPolicyRevisionRequestBody { Integer expectedVersion; reason }` | 同上 |
| `POST /scan-policies/{policyCode}/revisions/{revisionNo}/activation` | `ActivateScanPolicyRevisionRequestBody { Integer expectedVersion; reason }` | 同上 |
| `POST /scan-policies/{policyCode}/retirement` | `RetireScanPolicyRequestBody { Integer expectedVersion; reason }`（作用于当前 ACTIVE 修订；有路由引用 400） | 同上 |

- `ScanPolicySummary { policyCode; Integer activeRevisionNo; Integer draftRevisionNo; List<String> allowedMimeTypes; String maxSizeBytes; int routeCount; String status; Instant updateTime }`。展示内容取 ACTIVE，没有 ACTIVE 时取 DRAFT；`status` = `ACTIVE`（有生效修订）/ `DRAFT`（只有草稿）/ `RETIRED`。
- `ScanPolicyDetailResponseBody { policyCode; List<ScanPolicyRouteRef> routes; List<ScanPolicyRevision> revisions }`；`ScanPolicyRouteRef { sourceSystem; accessLevel; instanceCode; bucketName }`；`ScanPolicyRevision { revisionNo; status; allowedMimeTypes; maxSizeBytes; List<ScanPolicyRequirement> requirements; int version; Instant createTime; Instant updateTime }`；`ScanPolicyRequirement { scannerCode; scanType; minimumScannerVersion; List<String> acceptedResultCodes }`。修订按 `revisionNo` 倒序。
- 规则：`policyCode` `[A-Z][A-Z0-9_]{0,63}`；`allowedMimeTypes` 非空、去重、必须属于 `supportedMimeTypes`（当前 `application/pdf`、`image/png`、`image/jpeg`，来自格式检查器）；`maxSizeBytes` 1—104857600。新建修订自动写一条检查要求 `PLATFORM_FORMAT` / `FORMAT` / 最低版本 `1` / `["FORMAT_VALID"]`。激活：原 ACTIVE → RETIRED（`active_slot` 置空），草稿 → ACTIVE（`active_slot=1`），同一事务。
- 审计动作：`OPERATIONS_FILE_SCAN_POLICIES`（列表与详情，不记成功）、`OPERATIONS_FILE_SCAN_POLICY_CREATE`、`_REVISION_CREATE`、`_REVISION_UPDATE`、`_REVISION_DISCARD`、`_ACTIVATE`、`_RETIRE`；资源类型 `FILE_SCAN_POLICY`。
- 锁：`file-scan-policy:{policyCode}`（与路由保存时的策略引用校验无需同锁，路由保存仍按"有 ACTIVE 修订"查询；停用与路由新建的竞态由停用时在事务内重查引用计数兜底）。

### 2.3 文件记录（新增，权限：查看 `FILE_RECORD_READ`，下载 `FILE_RECORD_DOWNLOAD`）

| Method / Path | 请求 | 响应 |
|---|---|---|
| `GET /files` | Query：`createdFrom`、`createdTo`（ISO-8601，必填，`createdTo` 不含，跨度 ≤ 31 天）、`fileId`、`sourceSystem`、`reasonCode`、`accessLevel`、`scanStatus`、`hasPendingReplacement`（boolean）、`page`、`size` | `PagingResponseBody<FileRecordSummary>`，按 `create_time DESC, file_id DESC` |
| `GET /files/{fileId}` | — | `FileRecordDetailResponseBody` |
| `POST /files/{fileId}/download` | Query：可选 `versionNo`（缺省当前版本） | `FileDownloadResponseBody`（现有类） |

- `FileRecordSummary { fileId; fileAddress; displayName; sourceSystem; reasonCode; accessLevel; int currentVersionNo; Integer pendingVersionNo; scanStatus; objectStatus; mimeType; sizeBytes; Instant createTime; Instant updateTime }`
- `FileRecordDetailResponseBody` = Summary 全部字段 + `contentHash; uploaderAccountId; instanceCode; bucketName; objectRef; publicUrl; versionedUrl; List<FileVersionRecord> versions; List<FileScanRecord> scanResults; List<FileUploadRecord> uploads`
  - `FileVersionRecord { versionNo; versionStatus; scanStatus; displayName; mimeType; sizeBytes; contentHash; uploadId; uploaderAccountId; boolean downloadable; Instant availableTime; Instant createTime }`（倒序；`downloadable` = CURRENT 或 SUPERSEDED 且检查 AVAILABLE）
  - `FileScanRecord { versionNo; scanPolicyCode; int revisionNo; scannerCode; scanType; resultCode; int attemptNo; Instant scannedTime }`
  - `FileUploadRecord { uploadId; kind（ORIGINAL / REPLACEMENT）; int replacementNo; status; displayName; expectedMime; expectedSize; uploaderAccountId; Integer resultVersionNo; Instant expiresTime; Instant createTime; Instant updateTime }`（倒序）
- 下载：版本须 `downloadable`，否则 409；写 `FILE_DOWNLOAD_AUTHORIZE` 审计，操作人取 Gateway Actor，附加属性 `version_no`；审计失败不返回地址（503）。
- 审计动作：`OPERATIONS_FILE_RECORDS`（列表、详情，不记成功）；资源类型 `FILE`。
- "最近下载"不单独做接口：详情页嵌入现有审计组件，筛选 `resourceType=FILE, resourceId=fileId`。

## 3. Platform 注册（块 134，B 卡负责；块 130 修改两条）

- 权限：`900000000000134101 FILE_RECORD_READ`（资源 `FILE_RECORD`，读，LOW）、`900000000000134102 FILE_RECORD_DOWNLOAD`（高风险，动作按现有权限表允许值选择最贴切者并说明），授 Platform 管理员角色（同块 130 做法，初始 REVOKED，激活脚本启用）。
- Console Resource：菜单 `OPERATIONS_FILE_RECORDS`"文件记录"（父：平台配置 `900000000000000562`，路由 `/file-records`，组件 `OPERATIONS_FILE_RECORDS`，权限 READ，排序紧随存储管理）；按钮 `OPERATIONS_FILE_RECORD_DOWNLOAD`"下载"（权限 DOWNLOAD）。
- 路由（`operations-*` 命名沿用块 130）：桶编辑 1、策略 8、文件记录 3，共 12 条；请求头、Guard 按块 130 同类路由照抄；`GET /files` 与下载的查询参数白名单按上表登记。
- 块 130：`operations-file-storage-route-update` / `route-delete` 的外部与下游路径加 `/{accessLevel}`（修改既有行，不新增）。
- ID 段：1341xx 权限、1342xx 角色权限、1343xx 控制台资源、1344xx 路由、1345xx 请求头、1346xx 查询参数、1347xx Guard。开工前核对 134 未被占用。

## 4. 前端与 BFF 关键约定

- BFF 同路径转发全部新增 / 修改路径；请求体类登记到严格解析配置；`/storage-admin/v1/**` 已在 metadata-only 日志前缀内。
- Web：存储管理加"检查策略"页签；桶、写入规则按原型改；新增"文件记录"页（列表 + `?file=<id>` 详情，参照 `ApiCredentials.tsx` 的列表 / 详情切换和 URL 分页）。刷新统一用 `src/components/RefreshButton.tsx`（条数旁图标按钮）。原型中的"策略说明文字""快捷筛选计数"无后端字段，不实现。
