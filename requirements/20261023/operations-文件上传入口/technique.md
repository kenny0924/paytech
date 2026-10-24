# operations：运营控制台文件上传入口 · 技术方案

> 状态：阶段 2 已确认（D1 = A，2026-10-23 用户“按建议”）；第 5—10 节阶段 3（设计）已确认（2026-10-24 用户“继续”）。

## 1. 现状核对（2026-10-23，main `069dca04`）

| 需求项 | 现有能力 | 证据 | 结论 |
|---|---|---|---|
| R2 / R3 开始上传、直传、完成上传 | `InternalFileApi.startUpload` 支持 `reasonCode`（`[A-Z][A-Z0-9_]{0,63}`，不受枚举限制）、`accessLevel`、`uploaderAccountId`；`completeUpload` 返回 `fileId`、`fileAddress`、`versionNo`、`accessLevel` | `paytech-platform-support-api/.../files/InternalFileApi.java`、`StartFileUploadRequestBody.java` | 满足，不用改 Platform |
| R3 / R4 轮询状态、展示地址 | `InternalFileApi.getFile` 返回 `scanStatus`、`publicUrl`、`versionedUrl`（仅公开且检查通过时）、`sourceSystem`、`reasonCode` | `StoredFileResponseBody.java` | 满足。**但它不校验调用方是不是文件的来源**：运营控制台后端要自己核对 `sourceSystem = operations-console-bff` 且 `reasonCode = OPERATIONS_PUBLIC_ASSET`，否则这个页面就能查到任意文件的元数据 |
| R5 上传限制提示 | 只有运营端的 `GET /storage-admin/v1/routes`、`/scan-policies`；Platform 处理器按操作人重新校验 `FILE_STORAGE_READ` 或 `FILE_STORAGE_MANAGE` | `ListStorageRoutesProcessor` → `FileStorageManagementSupport.read(...)` | **缺口**：只有上传权限的人调用会得到 403；而且（精确规则 → `*` 规则）的解析是 Platform 的领域规则，不应该在运营控制台后端再实现一遍 |
| R6 最近上传 | 运营端 `GET /storage-admin/v1/files` 支持按 `sourceSystem`、`reasonCode`、`accessLevel` 和时间范围筛选（≤ 31 天）；处理器重新校验 `FILE_RECORD_READ` | `FileStorageManagementApi.listFileRecords`、`ListFileRecordsProcessor` | **缺口**：同上，没有文件记录查看权限就是 403；也不能按上传人筛选（Q4 的「只看我的」） |
| 运营控制台后端直传模式 | 卡 BIN `CardBinLibraryController` + `BinLibraryFileClient`；工作流附件 `WorkflowAttachmentController`；上传开始的响应只记元数据日志 | `paytech/operations-console-bff/.../ext/rest/` | 可以照搬 |
| 运营控制台前端直传 | `card/binLibrary.ts`、`workflow/workflowApi.ts`、`fileRecords/fileRecords.ts`（替换） | `operations-console-web/src/` | 可以照搬；建议抽一个共用的直传函数（阶段 3 决定） |
| 写入规则 | mor-test：`* × 公开 → S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT`；`operations-console-bff` 只有私有规则（`CARD_BIN_CSV`） | 2026-10-23 只读查询 | Q2 已确认：发布时新建（`operations-console-bff`，公开）精确规则 |
| 格式检查 | 只能识别 pdf / png / jpeg / csv | `supportedMimeTypes` | Q3 已确认：本期不扩展 |

## 2. D1：限制提示和最近上传怎么取数（已定：A）

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A（建议）** Platform 新增两个只读内部接口，按调用服务限定范围 | `GET /internal/files/v1/upload-constraints?accessLevel=PUBLIC`：按调用方（`X-Caller-Service`）解析生效的写入规则，返回是否已配置、实例 / 桶、检查策略代码与生效修订、允许的类型、大小上限、上传期限。`GET /internal/files/v1/files?reasonCode=&accessLevel=&uploaderAccountId=&createdFrom=&createdTo=&page=&size=`：只返回调用方自己来源的文件，时间范围 ≤ 31 天 | 规则解析只在 Platform 一处；页面只需要「文件上传」自己的权限；其他上传方（例如 client-console-bff）以后也能用 | Platform 要改代码：2 个处理器、api 新增方法和响应类、auto 测试 |
| B 给使用者叠加权限 | 页面角色同时授予 `FILE_STORAGE_READ` 和 `FILE_RECORD_READ`，运营控制台后端用运营端接口，自己解析规则 | Platform 不改代码 | 能上传的人同时也能看全部存储配置和所有文件记录；规则解析在运营控制台后端重复实现，以后容易不一致；仍然不能按上传人筛选 |
| C 去掉这两块 | 不做 R5；R6 只显示本次浏览器会话内的上传 | 改动最小 | 和已确认的 R5、R6、Q4 不一致 |

选 A 时的影响：
- `InternalFileApi` 只新增方法和响应类，不改已有字段。旧消费方不调用新方法，所以不受「先发消费方」的顺序约束。
- 但运营控制台后端依赖这两个新接口，发布顺序变成 **Platform → 运营控制台后端 → 运营控制台前端**。
- 需求第 7.3 节「Platform 不改代码」要相应修改。

## 3. 影响分析（按 A）

| 系统 | 影响 | 风险与处理 |
|---|---|---|
| platform-support-service | 新增 2 个只读内部接口（处理器 + 领域服务查询 + 映射），复用现有的规则解析和文件记录分页查询；新增注册块 136（权限 2 条、菜单 1、按钮 1、运营控制台后端路径的网关路由，初始停用） | 规则解析必须和上传时使用同一份逻辑，否则提示会和实际结果不一致：复用 Platform 上传时的规则解析，并用测试覆盖精确规则、`*` 规则、未配置三种情况 |
| operations-console-bff | 5 个接口；文件查询要核对来源和原因；开始上传的响应只记元数据日志 | 临时授权地址不能进日志：沿用卡 BIN 的 metadata-only 配置并加测试 |
| operations-console-web | 新页面、导航、能力表；页面数量相关的测试 +1 | 基线须 ≥ `069dca04` 并带上数据查询那 10 个未提交文件（发布时） |
| mor-test 配置 | 新建（`operations-console-bff`，公开）→ AWS 公开桶 / `PUBLIC_DOCUMENT` / 900 秒；激活块 136 | 不改现有规则；卡 BIN 私有规则不动 |
| 其他会话 | 块 136 开工前再核对占用（code-mor-db 用 135，commercial 快照需求尚未申请块号）；运营控制台三个服务的发布要和 code-mor-ae / code-mor-db 协调 | 开工前发消息申请块号 |

## 4. 已知前提

- 内部 HTTP 客户端严格反序列化（FAIL_ON_UNKNOWN_PROPERTIES）：本方案不给已有响应加字段，所以不触发「先发消费方」的问题。
- 本地 `mor_platform` 测试库是多个会话共用的：Platform auto 测试运行前要先确认表为空，见文件公开桶交付记录。

## 5. Platform：新增两个只读内部接口

两个接口都加在 `InternalFileApi`（`/internal/files/v1`）上。调用方由 `InternalFileConverter.callerService()` 确定，只能看到自己的数据；都不加事务，不写库。

### 5.1 `GET /upload-constraints?accessLevel=PUBLIC|PRIVATE`

- **处理器**：`QueryFileUploadConstraintsProcessor`。
- **解析逻辑**：
  - 复用 `FileStorageRegistry.forWrite(callerService, accessLevel)`：精确规则 → `*` 规则 → 503，和上传时完全一致；
  - 再读策略唯一的生效修订，复用 `FileUploadServiceImpl.admit` 中读取修订和允许类型的部分，抽成 `FileUploadService.constraints(policyCode, now)`。
- **不可用时不报错**：`forWrite` 或读取修订会 503 的情况，都返回 `configured=false` 的 200，字段只保留 `accessLevel`。这样页面能直接显示「未配置」。

响应 `FileUploadConstraintsResponseBody`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `configured` | `boolean` | 是否能开始上传 |
| `accessLevel` | `String` | 回显 |
| `routeSourceSystem` | `String` | 命中的是精确规则（调用方自己）还是 `*` |
| `instanceCode` / `bucketName` | `String` | 目标存储（不含凭据） |
| `publicBaseUrl` | `String` | 公开桶的对外地址，私有桶为空 |
| `scanPolicyCode` | `String` | 检查策略代码 |
| `scanPolicyRevisionNo` | `Integer` | 生效修订号 |
| `allowedMimeTypes` | `List<String>` | 允许的类型 |
| `maxSizeBytes` | `String` | 大小上限（十进制字符串） |
| `maxUploadSeconds` | `Integer` | 上传期限 |

### 5.2 `GET /files?createdFrom=&createdTo=&reasonCode=&accessLevel=&uploaderAccountId=&page=&size=`

- **处理器**：`ListCallerFilesProcessor`。
- **校验**：时间范围必填、左闭右开、≤ 31 天，和运营端列表一致；`size` 只能是 10 / 20 / 50。
- **范围限定**：来源系统**强制为调用方自己**，调用方不能指定。
- **查询**：复用 `FileRecordService.list`。`FileRecordQuery` 新增可空字段 `uploaderAccountId`，Mapper 的 XML 增加对应的等值条件；运营端列表传空，行为不变。
- **响应**：`PagingResponseBody<StoredFileResponseBody>`，每条和 `getFile` 的响应结构相同（含 `uploaderAccountId`、`publicUrl`、`versionedUrl`），不新增字段，也不改已有响应。

### 5.3 兼容性

- `InternalFileApi` 只新增方法和响应类。已发布的消费方（card、client-master、client-console-bff、ops-bff）不调用新方法，不受影响。
- 新版运营控制台后端依赖这两个接口，所以发布顺序是 **Platform → 运营控制台后端 → 运营控制台前端**。
- 完成上传时 Platform 是否核对调用方和来源一致，在实现时确认；如果不核对，就在运营控制台后端完成上传后再查一次文件，核对来源和原因，不一致返回 404。

## 6. 运营控制台后端：`/file-upload/v1`

| 方法 | 路径 | 权限 | 实现 |
|---|---|---|---|
| GET | `/constraints` | `FILE_UPLOAD_READ` | 转发 `upload-constraints?accessLevel=PUBLIC` |
| GET | `/files?createdFrom=&createdTo=&mine=&page=&size=` | `FILE_UPLOAD_READ` | 转发 `files`：固定 `reasonCode=OPERATIONS_PUBLIC_ASSET`、`accessLevel=PUBLIC`；`mine=true` 时把 `uploaderAccountId` 设为当前账号；时间范围缺省为近 7 天 |
| GET | `/files/{fileId}` | `FILE_UPLOAD_READ` | `getFile` 之后核对 `sourceSystem = operations-console-bff` 且 `reasonCode = OPERATIONS_PUBLIC_ASSET`，否则返回 404（不暴露其他文件） |
| POST | `/uploads` | `FILE_UPLOAD_CREATE` | 请求体只有 `{filename, mimeType, sizeBytes}`（严格解析，多余字段返回 400）；补上 `reasonCode`、`accessLevel=PUBLIC` 和当前账号作为 `uploaderAccountId` 后转发；201；响应只记元数据日志 |
| POST | `/uploads/{uploadId}/completion` | `FILE_UPLOAD_CREATE` | 转发；响应带 `fileId`，供页面轮询 |

- **分层**：Controller → `FileUploadEntryProcessor`（列表和详情的字段整形、来源核对、`mine` 转换）→ `PlatformPublicFileClient(Impl)`。开始上传和完成上传是纯转发，不经过处理器层（符合「简单转发可以省略处理器层」）。
- **页面用的结构** `FileUploadRecordResponseBody`：`fileId`、`fileAddress`、`displayName`、`mimeType`、`sizeBytes`、`scanStatus`、`versionNo`、`uploaderAccountId`、`uploadedByMe`、`createTime`、`publicUrl`、`versionedUrl`。
- **配置**：
  - `application.yml` 的 metadata-only 前缀加上 `/file-upload/v1/uploads`；
  - 严格请求体解析登记 `StartPublicFileUploadRequestBody`；
  - 原因代码写成常量 `OPERATIONS_PUBLIC_ASSET`。

## 7. 运营控制台前端：页面「文件上传」

- **路由与组件**：路由 `/file-upload`，组件 `OPERATIONS_FILE_UPLOAD`，按钮能力 `OPERATIONS_FILE_UPLOAD_CREATE`。代码放在 `src/pages/FileUpload.tsx` 和 `src/fileUpload/`（`fileUpload.ts` 负责严格解码和请求体，`UploadPanel.tsx`，`RecentUploads.tsx`）。
- **原型**：用户后来要求简单画一版，见 `operations-console-web/docs/operations-console.pen` 的「平台配置 · 文件上传 01 主页面」「02 状态」。下面的线框保留作文字说明。
- **文案与多语言**（按 code-mor-db「运营控制台多语言迁移」的约定，见 `operations-console-web/docs/i18n-migration-guide.md` 和需求 `operations-控制台多语言迁移/technique.md` 3.1）：
  - 页面文案放 `file` 命名空间，key 形如 `file.upload.<区域>.<名称>`；英文基准写进 `src/i18n/en/file.json`，中文写进 `i18n/zh-CN/file.json`；
  - 菜单名用 `nav.resource.OPERATIONS_FILE_UPLOAD`，同时加 en key 和 zh-CN 种子，`pageCapabilities` 登记后由一致性测试检查；
  - 源码里不得出现中文，由全量守卫测试强制；
  - 以 main ≥ `3aac93f2`（迁移提交 `93746677`）为基线；开发约定第 10 节同步更新，实现前读取。
- **线框**（最初按 Q5 不画原型）：
  ```
  ┌ 文件上传 ──────────────────────────────────────────────────────────┐
  │ ⓘ 公开文件 · 写入 S3_TEST / mor-test-platform-public-k7q2           │
  │   允许 pdf、png、jpeg · 不超过 20 MiB · 策略 PUBLIC_DOCUMENT r1      │
  │ ┌───────────────────────────────────────────────────────────────┐ │
  │ │        拖入文件或点击选择（单个文件）                            │ │
  │ │   ⚠ 文件将公开，任何人都能通过地址访问                          │ │
  │ └───────────────────────────────────────────────────────────────┘ │
  │ 结果卡：文件名 · 状态（上传中 / 检查中 / 可用 / 未通过）              │
  │   公开地址 [复制][打开]  带版本地址 [复制]  文件地址 [复制]            │
  ├ 最近上传 ── [近 7 天 ▾] [只看我的 ○] [刷新] ────────────────────────┤
  │ 文件名 │ 类型 │ 大小 │ 上传人 │ 时间 │ 状态 │ 公开地址 │ 操作          │
  └──────────────────────────────────────────────────────────────────┘
  ```
- **前端预校验**：按限制提示里的允许类型和大小，在开始上传前拦截；被拦截时显示原因，不调用接口。Platform 返回的 422 / 503 原样显示。
- **上传状态**：开始上传 → PUT 直传（复用现有直传写法，不带 `If-None-Match`）→ 完成上传 → 每 1 秒查一次文件，最多 30 秒。超时显示「检查仍在进行，可稍后在最近上传中查看」。
- **权限**：
  - 没有按钮权限：不显示上传区，只显示限制提示和最近上传；
  - `configured=false`：显示「公开上传未配置，请在存储管理中配置公开写入规则」，上传区禁用；
  - 有「文件记录」页面能力时，行操作里有「文件记录」链接，跳到 `/file-records?file=<id>`。
- **上传人**：显示账号 ID；`uploadedByMe` 为 true 时显示「我」。

## 8. Platform 注册块 136

写在 `platform-support-initial-data.sql`（自包含，初始为 DISABLED / REVOKED），同时提供 `db/platform-support/registration-block136.sql`、`test-activate.sql`、`test-deactivate.sql`。

| 类别 | ID | 内容 |
|---|---|---|
| 权限 | 136101 / 136102 | `FILE_UPLOAD_READ`（`FILE_UPLOAD`，READ，LOW）；`FILE_UPLOAD_CREATE`（`FILE_UPLOAD`，CREATE，HIGH） |
| 角色权限 | 136201 / 136202 | 平台管理员角色 `900000000000000510`（Q1），初始 REVOKED |
| 控制台资源 | 136301 / 136302 | 菜单 `OPERATIONS_FILE_UPLOAD`：父菜单 562「平台配置」、`/file-upload`、排序 86（文件记录 85 之后，实现时核对是否冲突）；按钮 `OPERATIONS_FILE_UPLOAD_CREATE`「上传」 |
| 网关路由 | 136401—136405 | 第 6 节的 5 个路径，外部路径为 `/api/operations/file-upload/v1/...`；安全配置 `OPERATIONS_JWT` / `HUMAN_PERMISSION`；两个 POST 允许 `content-type` 和 `idempotency-key` 请求头 |
| 请求头 / 查询参数 / Guard | 1365xx / 1366xx / 1367xx | 按块 134 的写法：`authorization`、`origin`；列表接口登记 `createdFrom`、`createdTo`、`mine`、`page`、`size`；Guard 规则照搬块 134 同类路由 |

块号已由 code-mor-db 登记给本需求（2026-10-23；之后的新块从 137 起），mor-test 上已核对无占用；本机库开工前再核对。

**Gateway 快照校验**（code-mor-db 经验）：块里只要有 `t_gateway_route` 或 Guard 行，即使是 DISABLED，Platform 的 Gateway 快照编译器也会校验。限流、并发超过上限（600/min、50）会导致快照刷新全部失败。所以 Guard 取值一律照搬块 134 同类路由，不新增共享限流档位。提交前，用 mor-test 备份起一个断网的 MySQL，分别在休眠态和激活态各跑一次编译器。

## 9. mor-test 配置（发布时）

- 新建写入规则（`operations-console-bff`，公开）→ `S3_TEST / mor-test-platform-public-k7q2`、`PUBLIC_DOCUMENT`、900 秒（Q2）。走运营接口，放在阶段 7 的步骤里。
- 激活块 136。
- 多语言：在「多语言管理」里导入 `file.upload.*` 和 `nav.resource.OPERATIONS_FILE_UPLOAD` 的 en / zh-CN 文案，并发布 operations-console 语言包。不发布也能用，但页面只显示内置英文。

## 10. 测试计划

| 层 | 入口 | 场景 |
|---|---|---|
| Platform auto | 内部 HTTP 接口（真实链路，使用本机 `local-environment` 的 MySQL 和 MinIO） | 限制接口：精确规则、`*` 规则、未配置、桶停用、策略没有生效修订、类型列表和大小与上传时实际校验一致（同一份数据下，限制通过则开始上传通过）。列表接口：只返回调用方来源；按上传人、原因、级别筛选；时间范围缺失、颠倒或超过 31 天返回 400；`size` 不合法返回 400。运营端文件记录列表行为不变（回归） |
| 运营控制台后端 auto | 控制器 HTTP 接口，Platform 用测试专用的 HTTP 端点返回 DTO | 5 个接口的正常流程；固定的原因、级别和上传人确实传给了 Platform；`mine` 转换；查询他人来源的文件返回 404；多余字段返回 400；Platform 的 422 / 503 原样透传；日志里没有授权地址；没有发生意外调用 |
| 运营控制台前端 | vitest | 严格解码器、请求体、预校验、轮询状态机（可用 / 未通过 / 超时）、权限显示、未配置提示；页面数量相关测试 +1；`npm run build` |
| mor-test 验收（阶段 7） | 浏览器 + 接口 | 需求 AC-01—AC-08；截图 |
