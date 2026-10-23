# platform-support：文件公开桶与固定访问地址 · 技术方案（阶段 2—3 评审稿 v1）

> 状态：阶段 2—3 合并评审稿 v1（2026-10-22），用户“继续”确认 S1—S6 按建议；页面原型（S5）绘制中。需求见 [requirement.md](./requirement.md)（阶段 1 已确认）。基线：main `73a2460d`（文件多存储并存第二段，mor-test 已执行其删除 DDL）。
>
> **规模（本次方案）：** 涉及 Platform Support、Operations BFF、Operations Web 3 个系统。
> - **接口：** 内部文件接口新增 1 个、修改 3 个；存储管理接口新增 11 个、修改 7 个（BFF 同路径转发，不重复计数）。
> - **数据库（`mor_platform`）：** 新增 1 张表（`t_file_version`，16 个字段）；5 张旧表共加 10 个字段，改 3 个唯一键，加 3 个索引。
> - **DML：** 回填 DML 1 份，按现有文件数生成版本行，mor-test 约 57 行。注册块 134 新增约 80 行，块 130 改 2 条路由路径。
> - **页面：** 存储管理改 2 个页签、加 1 个页签；新增文件记录列表、详情 2 个页面。

## 1. 现状差异（阶段 2）

### 1.1 需求项与现状

| 需求项 | 当前行为及证据 | 缺口 | 处理 |
|---|---|---|---|
| R1 桶访问级别 | `t_file_storage_bucket` 只有实例、桶名、状态、检查结果（schema :`t_file_storage_bucket`） | 无访问级别、无对外地址 | 加 2 列 |
| R2 桶检查 | `S3FileStorage.checkBucket` / `MinioFileStorage`：存在 → 版本控制 → 写探测对象 → 加密 → 读 → 删除；结果 6 种 | 不检查匿名可读性 | 私有桶加"匿名不可读"，公开桶加"基础地址可读"，结果加 2 种 |
| R3 路由 | `FileStorageRegistry.forWrite`：精确来源 → `*`；唯一键 `uk_file_storage_route_source(source_system)` | 一个来源只能一条路由 | 路由键改为来源系统 + 访问级别，不跨级别回落 |
| R4 声明访问级别 | `StartFileUploadRequestBody` 无该字段 | — | 加可选 `accessLevel`，默认 `PRIVATE` |
| R5 公开地址 | 只返回 `fileAddress`；下载签 60 秒、固定 `versionId` 的临时地址 | 无长期地址 | 公开文件返回 `publicUrl` / `versionedUrl`（计算得出，不落库） |
| R7 检查策略 | `t_file_scan_policy` / `t_file_scan_requirement` 只有读取代码（`FileUploadMapper.xml:12-18`、`FileLifecycleMapper.xml:31-37`），没有写接口；策略行只在需求 SQL 中（`CLIENT_ONBOARDING_FORMAT`、`CARD_BIN_CSV`）；路由表单手填策略代码 | 无管理能力 | 新增 8 个管理接口与页签；路由表单改下拉 |
| R8 覆盖 | 一个文件只对应一个存储版本：上传 PUT 带 `If-None-Match: *`；完成时 `requireSingleVersion` 要求键下恰好 1 个版本；`t_file_object` 唯一键含 `object_version`，表注释为"不可变对象存储引用"；扫描结果无版本列 | 模型和流程都不支持同一文件多版本 | 新增 `t_file_version`；`t_file_object` 保留文件身份并镜像"当前版本"；新增替换上传接口 |
| R9—R10 文件记录 | 无列表接口；只有入驻审核、工作流附件两处带业务授权的下载 | 无运维查询入口 | 新增列表、详情、运维下载 3 个接口与 2 个页面 |
| R11 存储管理页 | `FileStorage.tsx` 三页签，表格不分页；`RouteModal` 策略为文本框 | — | 同步字段，加"检查策略"页签 |

### 1.2 受影响系统

| 系统 | 影响点 | 改动 | 依赖与风险 |
|---|---|---|---|
| Platform file-document | 存储适配器（检查、替换签名、版本列举、复制恢复）、注册表路由、上传 / 完成 / 扫描 / 描述、策略与文件查询 Repository | 代码、Mapper、模型 | 扫描改按版本运行，是本次最大的改动 |
| Platform core | 新 Processor：替换上传、策略管理 8 个、文件记录 3 个；现有上传、完成、路由 Processor 改造 | 代码 | 单资源写用 `AbstractLockProcessor` |
| Platform api / app | `InternalFileApi`、`FileStorageManagementApi` 契约与 Controller | 代码 | BFF、CCT、Client BFF 依赖 api 模块，新增字段向后兼容 |
| Platform DB | 1 新表、5 表加列、3 个唯一键 | DDL、回填 DML、PDManer、Schema SQL | 需在 `73a2460d` 之后的 Schema 上执行 |
| Platform 注册 | 块 134（新菜单、按钮、权限、路由）、块 130 改 2 条路由路径 | DML | 块 133 已被 3DS 使用，134 当前无人占用（开工前再核对） |
| Operations BFF | `FileStorageAdminController` 转发新增 / 修改路径；请求体类登记 | 代码 | — |
| Operations Web | 存储管理页、新"文件记录"页 | 代码 | ops-web 的 mor-test 现网含未提交的按钮布局改动，发布时须一并带上 |
| CCT、Client BFF、工作流 | 不传 `accessLevel` 即为私有，行为不变 | 无 | AC-08 回归 |

## 2. 方案（阶段 3）

### 2.1 访问级别

- 桶：`access_level` `PRIVATE` / `PUBLIC`。`PUBLIC` 必须登记 `public_base_url`，HTTPS，不带查询串和结尾 `/`。访问级别创建后不可改；基础地址本期只能在 `DRAFT` 时修改（S3）。
- 路由：键为 `(source_system, access_level)`。解析顺序：`(来源, 级别)` → `(*, 级别)` → 503。**不跨级别回落**（B1）。保存路由时，桶的访问级别必须与路由一致（AC-07）。现有 `*` 路由回填为 `PRIVATE`；`(*, PUBLIC)` 默认不存在，需要时在页面上新建。
- 上传：`accessLevel` 冻结到 `t_file_upload`，完成时复制到 `t_file_object`，之后不变。

### 2.2 桶检查（R2）

在现有检查序列的"读回探测对象"之后、删除之前追加一步：

| 桶级别 | 追加检查 | 失败结果 |
|---|---|---|
| `PRIVATE` | 不带凭据 GET 存储地址上的探测对象（虚拟主机或路径风格，按实例设置），必须返回 401 / 403 | `PUBLIC_ACCESS_ALLOWED` |
| `PUBLIC` | 不带凭据 GET `public_base_url/探测对象键`，必须 200 且字节一致 | `PUBLIC_READ_FAILED` |

匿名请求由存储适配器用 JDK HTTP 客户端发出，超时取实例设置，不跟随重定向，不记录响应正文。它和现有 SDK 调用属于同一存储边界，不经 channel-connector。

### 2.3 文件与版本模型（R8 的基础）

```
t_file_object（文件身份，地址不变）      t_file_version（每次内容一行）
 file_id、来源系统、访问级别              file_id + version_no（1、2、3…）
 存储实例 / 桶 / 对象键（各版本共用）       upload_id、object_version、摘要、大小、类型
 current_version_no ──────────────────▶ version_status：CANDIDATE / CURRENT / SUPERSEDED / REJECTED
 当前版本镜像：object_version、摘要、大小、     scan_policy_id、scan_status、available_time
 类型、scan_policy_id、scan_status
```

- **当前版本镜像：** `t_file_object` 原有的 `object_version`、`content_hash`、`size_bytes`、`mime_type`、`storage_encryption_key_ref`、`scan_policy_id`、`scan_status`、`available_time` 保留，含义变为"当前对外提供的版本"。下载、描述、入驻审核、工作流等现有读路径基本不用改。
- **首次上传：** 完成时写 `t_file_object` 和 `t_file_version` v1（`CANDIDATE`）。v1 检查通过后两边都变为 `AVAILABLE`，v1 变为 `CURRENT`；不通过时两边都变为 `REJECTED`，与现在一致。
- **替换：** v*n* 作为 `CANDIDATE` 单独检查，`t_file_object` 保持旧版本不变。通过后在一个短事务里锁住文件行：镜像改为 v*n*，`current_version_no=n`，旧 `CURRENT` 改为 `SUPERSEDED`。不通过时 v*n* 为 `REJECTED`，文件不变（B7、AC-13）。
- **检查结果：** `t_file_scan_result` 加 `file_version_no`，唯一键改为 `(file_id, file_version_no, scan_requirement_id, attempt_no)`。
- **扫描事件：** `FILE_SCAN` 的事件键 v1 仍为 `fileId`（存量兼容），替换为 `fileId:vN`。`FileScanHandler` 按版本行执行，`valid()` 改为核对版本行的 `object_version` 和摘要。

### 2.4 替换流程（R8）

```
业务服务 ──POST /internal/files/v1/files/{fileId}/replacements──▶ Platform
   锁 file:replace:{fileId}（Redis）→ 文件须属同一来源（否则 404）、AVAILABLE + ACTIVE、桶 ACTIVE、无未结束替换——上传未完成或候选版本检查中（否则 409）
   → 冻结该文件策略代码当前的 ACTIVE 修订 → 记录 base_object_version = 存储中该键的最新版本
   → 短事务写 t_file_upload（replaces_file_id、replacement_no=上次+1，对象键 = 原文件对象键）
   → 事务外签 PUT（只签 Content-Type，不带 If-None-Match）
浏览器 / 服务 ──PUT──▶ 存储（版本控制产生新版本）
业务服务 ──POST /uploads/{uploadId}/completion（与首次上传同一接口）──▶ Platform
   列举该键的版本：比 base 新的版本必须恰好 1 个（0 个返回 409 未检测到上传；多于 1 个返回 409 并作废本次替换）
   → 读该版本核对大小、摘要、类型 → 短事务写 t_file_version v(n)（CANDIDATE）+ 登记 FILE_SCAN fileId:vN
```

- **并发（B6、AC-12）：** 同一文件同时只允许一个未结束的替换（替换上传未完成，或已完成但候选版本仍在检查）。第二个开始请求在 Redis 锁内看到未结束的替换，返回 409。`(实例, 桶, 对象键, replacement_no)` 唯一键是数据库兜底。
- **为什么不用条件写（`If-Match`）：** 各家对条件 PUT 的支持不一致（需求 Q6）。"比 base 新的版本恰好 1 个"只依赖版本控制，私有文件的读取又固定到已核对的 `versionId`，所以即使有多余的 PUT，也不会有未核对的内容被读出。
- **私有文件：** 检查通过前，下载签发的仍是镜像中的旧 `versionId`（B7、AC-10）。
- **公开文件：** 公开地址读的是存储中的最新版本，所以 PUT 一完成，新内容就对外可见（需求 Q9 已接受）。新增补偿事件 `FILE_PUBLIC_SYNC`（事件键 `fileId:uploadId`，开始替换时登记）：替换仍未结束时返回重试，结束后（通过、不通过、过期、作废）比较"最新版本"与"当前版本"的 ETag 和大小，不一致就用 `CopyObject`（源为当前 `versionId`）把当前版本复制成最新版本。重试和退避沿用补偿框架，超过上传期限后最迟一个退避周期内收敛。

### 2.5 公开地址（R5、需求 Q8）

- `publicUrl = public_base_url + "/" + storage_object_ref`；`versionedUrl = publicUrl + "?v=" + current_version_no`。两者都在读取时计算，不落库。只有文件为 `PUBLIC` 且当前版本 `AVAILABLE` 时才返回，否则为 `null`。
- 完成接口返回时通常还没检查完，所以 `publicUrl` 一般为空。调用方在检查完成后用 `GET /files/{fileId}` 取。
- `versionedUrl` 用于需要绕开 CDN 缓存的场景，要求 CDN 的缓存键包含查询串。S3 直连时会忽略未知查询参数，这一点在阶段 5 实测确认。

### 2.6 检查策略管理（R7）

- 策略 = 代码 + 若干修订，每个修订有 `DRAFT` / `ACTIVE` / `RETIRED` 状态。同一代码最多一个 `ACTIVE`（沿用 `active_slot`）和一个 `DRAFT`。
- 修订内容：允许的类型和最大字节数。允许的类型只能从格式检查能识别的类型中选，由接口返回 `supportedMimeTypes`，取自格式检查器（main `de4f7ba4` 起为 `application/pdf`、`image/png`、`image/jpeg`、`text/csv`）；不在其中的类型页面标出"含无法识别的类型"。最大字节数为 1 到 100 MiB。
- 检查要求：新建修订时自动生成一条 `PLATFORM_FORMAT` / `FORMAT` / `["FORMAT_VALID"]`。本期只有这一种检查器，页面不让编辑，只读展示。
- 激活：草稿变为 `ACTIVE`，原 `ACTIVE` 变为 `RETIRED`，同一事务完成（B8）。已开始的上传冻结了旧修订 ID，不受影响。
- 停用：把 `ACTIVE` 改为 `RETIRED` 且不补新修订。有路由引用该代码时拒绝（AC-14）。
- 事务：全部为本地单库写，单资源锁 `file-scan-policy:{code}`，写 Operations 审计 `ConfigurationChanged`，与存储管理一致。

### 2.7 文件记录（R9、R10）

- 列表：条件为文件 ID（精确）、来源系统、`reason_code`、访问级别、检查状态、创建时间范围（必填，最长 31 天），按创建时间倒序分页。使用已有索引 `idx_file_source`、`idx_file_scan_status`，另加 `idx_file_create_time`。
- 详情：文件元数据、存储坐标（实例、桶、对象键，不含凭据）、版本历史（每版的状态、检查结果、大小、摘要、上传人、时间）、该文件的上传记录（含过期 / 作废的替换），以及公开地址。
- 运维下载：`POST .../files/{fileId}/download?versionNo=`，复用 `AuthorizeFileDownloadProcessor`。可以下载 `CURRENT` 或 `SUPERSEDED` 版本，`CANDIDATE` / `REJECTED` 不能下载。操作人取自 Gateway 身份，写 `FILE_DOWNLOAD_AUTHORIZE` 审计（额外记录 `version_no`），审计写失败就不返回地址。

## 3. 接口

### 3.1 内部文件接口（`/internal/files/v1`，私网）

| 变更 | Method / Path | 说明 |
|---|---|---|
| 改 | `POST /uploads` | 请求加可选 `accessLevel`（`PRIVATE` 默认 / `PUBLIC`），其他非法值返回 422。`PUBLIC` 没有公开路由返回 503 `FILE_STORAGE_UNAVAILABLE` |
| 新 | `POST /files/{fileId}/replacements` | 请求：`filename`、`mimeType`、`sizeBytes`、可选 `uploaderAccountId`（不允许 `accessLevel`、`reasonCode`，沿用原文件的值）。响应与开始上传相同（201，`uploadId` + PUT 授权 + 到期时间）。错误：其他来源或不存在 404；文件不可用、有未结束替换 409；类型或大小不符合策略 422；桶不可写 503 |
| 改 | `POST /uploads/{uploadId}/completion` | 替换上传也走这里。响应加 `versionNo`、`accessLevel`；替换核对失败返回 409 `FILE_REPLACEMENT_CONFLICT` |
| 改 | `GET /files/{fileId}` | 响应加 `accessLevel`、`versionNo`（当前版本号）、`publicUrl`、`versionedUrl`、`pendingVersionNo`（有检查中的替换时） |
| 不变 | `POST /files/{fileId}/download` | 签发当前版本 |

Platform 内部模块（工作流）使用的 Service 入口同步加入相同字段和替换方法。

### 3.2 存储管理接口（Platform `/storage-admin/v1`，经 Operations BFF 同路径转发）

| 变更 | Method / Path | 权限 | 说明 |
|---|---|---|---|
| 改 | `GET /instances` | 存储查看 | 桶加 `accessLevel`、`publicBaseUrl` |
| 改 | `POST /instances/{code}/buckets` | 存储维护 | 请求加 `accessLevel`（必填）、`publicBaseUrl`（`PUBLIC` 必填） |
| 新 | `PUT /instances/{code}/buckets/{bucketName}` | 存储维护 | 只在 `DRAFT` 时可改 `publicBaseUrl` |
| 改 | `POST /instances/{code}/buckets/{bucketName}/check` | 存储维护 | 结果加 `PUBLIC_ACCESS_ALLOWED`、`PUBLIC_READ_FAILED` |
| 改 | `GET /routes` | 存储查看 | 加 `accessLevel` |
| 改 | `POST /routes` | 存储维护 | 请求加 `accessLevel`（必填） |
| 改 | `PUT /routes/{sourceSystem}/{accessLevel}` | 存储维护 | 原 `PUT /routes/{sourceSystem}`，路径加级别 |
| 改 | `DELETE /routes/{sourceSystem}/{accessLevel}` | 存储维护 | 同上；`(*, PRIVATE)` 不能删 |
| 新 | `GET /scan-policies` | 存储查看 | 每个代码一行：当前修订、草稿修订号、允许类型、最大字节、引用路由数；附 `supportedMimeTypes` |
| 新 | `GET /scan-policies/{code}` | 存储查看 | 全部修订及其检查要求 |
| 新 | `POST /scan-policies` | 存储维护 | 新建代码，同时建修订 1（草稿） |
| 新 | `POST /scan-policies/{code}/revisions` | 存储维护 | 以当前 `ACTIVE` 为模板建草稿；已有草稿时 409 |
| 新 | `PUT /scan-policies/{code}/revisions/{revisionNo}` | 存储维护 | 改草稿（带 `expectedVersion`） |
| 新 | `DELETE /scan-policies/{code}/revisions/{revisionNo}` | 存储维护 | 废弃草稿（物理删除，草稿从未被引用） |
| 新 | `POST /scan-policies/{code}/revisions/{revisionNo}/activation` | 存储维护 | 激活草稿，原 `ACTIVE` 改为 `RETIRED` |
| 新 | `POST /scan-policies/{code}/retirement` | 存储维护 | 停用当前修订；有路由引用时 400 |
| 新 | `GET /files` | 文件记录查看 | 列表与筛选，`page` / `size`（最大 100） |
| 新 | `GET /files/{fileId}` | 文件记录查看 | 详情、版本、检查结果、上传记录 |
| 新 | `POST /files/{fileId}/download` | 文件下载 | 可选 `versionNo`；写下载审计 |

写操作都带 `reason`，并写 Operations 审计，资源类型新增 `FILE_SCAN_POLICY`、`FILE`。新增审计动作：`OPERATIONS_FILE_STORAGE_BUCKET_UPDATE`、`OPERATIONS_FILE_SCAN_POLICIES`（查询，不记成功）、`OPERATIONS_FILE_SCAN_POLICY_CREATE / REVISION_CREATE / REVISION_UPDATE / REVISION_DISCARD / ACTIVATE / RETIRE`、`OPERATIONS_FILE_RECORDS`（查询，不记成功），下载沿用 `FILE_DOWNLOAD_AUTHORIZE`。

## 4. 数据设计（`mor_platform`）

### 4.1 新表 `t_file_version`

| 字段 | 类型 | 说明 |
|---|---|---|
| `version_id` | BIGINT PK | 版本行主键；回填的 v1 使用 `file_id` 的值 |
| `file_id` | BIGINT NOT NULL | 文件 ID |
| `version_no` | INT NOT NULL | 1 起，逐次加 1 |
| `upload_id` | BIGINT NULL | 产生该版本的上传；非上传来源的文件为空 |
| `object_version` | VARCHAR(128) NOT NULL | 存储 `versionId` |
| `content_hash` | CHAR(64) NOT NULL | SHA-256 |
| `size_bytes` | BIGINT NOT NULL | |
| `mime_type` | VARCHAR(255) NOT NULL | |
| `storage_encryption_key_ref` | VARCHAR(255) NOT NULL | |
| `scan_policy_id` | BIGINT NOT NULL | 冻结的策略修订 |
| `scan_status` | VARCHAR(64) NOT NULL | 与 `t_file_object` 同一组取值 |
| `version_status` | VARCHAR(16) NOT NULL | `CANDIDATE`、`CURRENT`、`SUPERSEDED`、`REJECTED` |
| `available_time` | DATETIME(6) NULL | |
| `version` | INT NOT NULL | 乐观并发 |
| `create_time` / `update_time` | DATETIME(6) NOT NULL | |

索引：`uk_file_version_no(file_id, version_no)`、`uk_file_version_upload(upload_id)`、`idx_file_version_scan(scan_status, create_time)`。

### 4.2 现有表增量

| 表 | 改动 |
|---|---|
| `t_file_object` | 加 `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'、`current_version_no` INT NOT NULL DEFAULT 1；加 `idx_file_create_time(create_time)`；表注释改为"文件身份与当前版本镜像" |
| `t_file_upload` | 加 `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'、`replaces_file_id` BIGINT NULL、`replacement_no` INT NOT NULL DEFAULT 0、`base_object_version` VARCHAR(128) NULL；唯一键 `uk_upload_storage_target` 改为 `(storage_provider, storage_container_ref, storage_object_ref, replacement_no)`；加 `idx_file_upload_replaces(replaces_file_id, status)` |
| `t_file_storage_bucket` | 加 `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'、`public_base_url` VARCHAR(255) NULL；`last_check_result` 注释加 2 个取值 |
| `t_file_storage_route` | 加 `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'；唯一键改为 `uk_file_storage_route_source(source_system, access_level)` |
| `t_file_scan_result` | 加 `file_version_no` INT NOT NULL DEFAULT 1；唯一键 `uk_file_scan_attempt` 改为 `(file_id, file_version_no, scan_requirement_id, attempt_no)` |

不加 CHECK / FK（数据库规范 5.1）。访问级别、版本状态的取值由应用校验，并写进字段注释。

### 4.3 DML

- **回填（`db/ddl.sql` 之后执行的 `db/dml.sql`）：** 为每个 `t_file_object` 插入 v1 版本行，`version_status` 由 `scan_status` 推导（`AVAILABLE`→`CURRENT`，`REJECTED` / `QUARANTINED`→`REJECTED`，其余→`CANDIDATE`）。新列默认值即为存量取值，无需另行更新。可重跑（`INSERT … SELECT … WHERE NOT EXISTS`）。
- **注册块 134（文件记录）：** 权限 2 个（`FILE_RECORD_READ` LOW、`FILE_RECORD_DOWNLOAD` HIGH），授予 Platform 管理员角色；菜单"文件记录"（平台配置下，`/file-records`）+ 按钮"下载"；Gateway 路由 3 条及对应请求头 / Guard。
- **注册块 134（存储管理新增）：** 路由 9 条（桶编辑 1、策略 8），沿用 `FILE_STORAGE_READ` / `FILE_STORAGE_MANAGE` 权限。
- **块 130 修改：** `operations-file-storage-route-update` / `route-delete` 两条路由的外部路径和下游路径加 `/{accessLevel}`。
- 激活 / 停用脚本按块 130 的做法另附（`test-activate.sql` / `test-deactivate.sql`）。

### 4.4 兼容与回退

- **接口兼容（阶段 4 发现）：** 内部 `HttpServiceClientFactory` 开启 `FAIL_ON_UNKNOWN_PROPERTIES` 与 `FAIL_ON_NULL_FOR_PRIMITIVES`。文件 / 存储 api 的消费方为 client-console-bff、client-master（CCT）、operations-console-bff：旧消费方读到新 Platform 响应的新字段会失败。因此已有响应类新增字段一律用可空类型，发布顺序为**先发三个消费方（新 api，可容忍旧 Platform），再发 Platform**；同一窗口内执行块 130 路径修改与块 134 激活。

- 新列都有默认值。旧代码不读新列，但 `uk_upload_storage_target`、`uk_file_storage_route_source`、`uk_file_scan_attempt` 变宽后对旧代码仍然成立（旧代码的写入都落在默认值上），所以 DDL 可以先于镜像执行。
- 回退镜像：已产生 v2+ 版本的文件，旧代码只认镜像，读到的是当前版本，不出错；替换中的上传会被旧代码当作普通上传，因为 `replacement_no` 不同，不会冲突，完成时会因为单版本校验失败而停住。回退 SQL：删除新表和新列，前提是不存在 `PUBLIC` 桶和 v2+ 版本。

## 5. 页面（Operations Web）

| 页面 | 变化 |
|---|---|
| 存储管理 · 桶 | 列表加"访问级别"标签（私有 / 公开）和"对外地址"列；登记弹窗加访问级别（单选）和对外地址（公开时必填）；草稿桶可编辑对外地址；检查结果文案加 2 种 |
| 存储管理 · 写入规则 | 列表加"访问级别"；新建 / 编辑：来源系统 + 访问级别，桶下拉只列同级别的 `ACTIVE` 桶，检查策略改为下拉（只列有 `ACTIVE` 修订的代码） |
| 存储管理 · 检查策略（新页签） | 列表：代码、当前修订、允许类型（标签）、最大大小、引用路由数、草稿标记。右侧抽屉为修订时间线：每个修订的状态、内容，以及只读的检查要求。操作：新建策略、新建修订、编辑草稿、激活（确认弹窗写明影响）、废弃草稿、停用（有引用时禁用并说明） |
| 文件记录（新菜单） | 顶部一行筛选（时间范围必填，默认近 7 天）；表格列：文件（展示名 + ID）、来源 / 原因、访问级别、当前版本、检查状态、大小、创建时间；分页参数写入 URL |
| 文件记录详情（子路由） | 头部：展示名、状态标签；公开文件显示公开地址（复制、打开）。分区：基本信息、存储位置、版本历史（表格，每行可下载）、检查结果、上传记录 |

视觉沿用现有存储管理页和本次按钮布局规范（刷新为条数旁的图标按钮、筛选尽量一行）。

原型（`operations-console-web/docs/operations-console.pen`，y=86700 一行，5 个画板）：

| 画板 | 内容 |
|---|---|
| 存储管理 01 检查策略 | 策略列表（含无法识别类型提示、最近变更）+ 右侧修订时间线：草稿 r3 在时间线内编辑类型与大小、激活影响说明、只读检查要求、引用的写入规则 |
| 存储管理 02 桶 | 桶列表（访问级别、对外地址、最近检查）+ 公开桶检查未通过弹窗：逐步检查结果、失败原因、AWS 公开只读策略指引 |
| 存储管理 03 写入规则 | 来源系统 × 私有 / 公开矩阵（未配置格提示 503）+ 编辑抽屉：级别只读、同级别桶、策略下拉（无生效修订不可选）、生效预览 |
| 文件记录 01 列表 | 一行筛选 + 快捷筛选、文件表格分页、右侧快速预览（图片、公开地址复制、替换中提示） |
| 文件记录 02 详情 | 摘要卡、公开地址条、版本历史（候选不可下载）、检查结果、上传记录时间线（含作废的替换）、最近下载审计 |

## 6. 研发与验证计划

| 顺序 | 内容 | 对应验收 |
|---|---|---|
| 1 | DDL / 回填 / PDManer / Schema SQL，本机 `mor_platform` 执行（需单独授权） | — |
| 2 | file-document：模型与 Mapper、适配器（匿名检查、替换签名、版本列举、复制恢复）、注册表路由、版本化扫描、`FILE_PUBLIC_SYNC` | AC-02—AC-07、AC-10—AC-13 |
| 3 | core / api / app：替换上传、策略 8 个、文件记录 3 个 Processor 与 Controller | AC-01、AC-14、AC-15 |
| 4 | 注册块 134、块 130 修改；BFF 转发 | AC-09、AC-15 |
| 5 | Web 页面与解码器、测试 | AC-09、AC-15 |

测试（auto，本机 MySQL / Redis / MinIO）：

- 内部文件接口 HTTP：AC-04—AC-08、AC-10—AC-13；替换的并发、过期与多余 PUT。
- 存储管理 HTTP：AC-01—AC-03、AC-07、AC-14；公开桶用本机 MinIO 的匿名只读策略桶，基础地址为 MinIO 路径风格地址。
- 补偿处理器：`FILE_SCAN`（v1 / vN）、`FILE_PUBLIC_SYNC` 的恢复复制。
- 现有入驻上传、审核下载、工作流附件回归（AC-08）。
- BFF 定向测试，Web 测试与构建；mor-test 用 AWS 新加坡区的公开测试桶实测 `versionedUrl`。

## 7. 风险

- **扫描改为按版本运行**：影响所有上传。用回填和 v1 事件键不变保证存量兼容，回归覆盖入驻和工作流。
- **公开文件的检查空窗**（已接受，需求 Q3 / Q9）：替换后到恢复前，不合格内容可能短暂对外可见；CDN 缓存会延长这个时间。
- **存储版本增长**：每次替换和恢复都留下版本。建议公开桶和私有桶都配置非当前版本过期（如 30 天），过期后版本历史里的旧版本将无法下载，页面会如实提示。这是基础设施配置，不在本期代码范围。
- **匿名检查与 CDN**：公开桶检查依赖 CDN 能回源读到探测对象；探测对象删除后 CDN 可能缓存一段时间，内容无害。

## 8. 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| S1 | 权限：文件记录查看、文件下载是否单独设两项权限？ | **单独两项**。文件内容可能含入驻资料等个人信息，下载权限应比存储配置更严 |
| S2 | 公开桶的对外地址本期只允许草稿时修改？ | **是**。启用后改地址会让所有已给出的公开地址失效，换 CDN 域名另立需求 |
| S3 | 运营人员能否下载历史版本？ | **能**（`SUPERSEDED`），检查中和被拒绝的版本不能下载 |
| S4 | 检查策略的最大大小上限 | **100 MiB**，与浏览器直传和单次 PUT 的实际能力匹配 |
| S5 | 页面原型：新增的"检查策略"页签、"文件记录"列表和详情 3 个画板，是否需要先在 `operations-console-web/docs/operations-console.pen` 画出评审？ | **需要**。确认本稿后先画原型，原型确认后再进入阶段 4 |
| S6 | 本机执行 DDL / DML 的授权（研发前需要）：目标本机 `mor_platform`，执行前先备份 | 阶段 4 开始时单独确认 |
