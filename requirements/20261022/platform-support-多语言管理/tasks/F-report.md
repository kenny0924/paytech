# 任务卡 F 报告：公开桶集成——语言包上传与读取、JSON 检查、文件路由与端到端测试

日期：2026-10-23。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-int`（detached，**基于 main `bfb17bc9`**，公开桶已在基线中）。未提交、未推送、未执行任何 SQL、未改或起停容器、未碰 mor-test。Maven 全部使用私有仓库（`-Dmaven.repo.local=<scratchpad>/m2`、`-Dmaven.repo.local.tail=$HOME/.m3/repository` 分开写），Platform 测试另加 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。没有跑整个 reactor 的测试。

## 0. 结论

- 语言包写入、读取与公开地址三个端口已接入真实文件服务。发布三步、检查后自动上线、检查拒绝、发布期间文案变更、旧发布后上线不改指针、回滚与切回、差异、manifest 公开地址、预检与发布摘要、检查进度、真实上传的 JSON 导入，全部有走生产链路与本机 MinIO 的测试，没有替身绕过。
- 文件格式检查已支持 `application/json`：UTF-8、无 BOM、无 NUL、全文恰好一个 JSON 对象。
- 数据脚本：`db/dml.sql` 第 3 部分与回滚（路由改指已获 code-mor-6b 确认，条件见第 4.1 节）；`platform-support-initial-data.sql` 追加块 135；PDManer 手工加入七表与 `localization` 关系图。均未执行。
- 在 `bfb17bc9` 上的回归全部通过（第 6 节），没有失败用例。

## 1. 基线与偏差（请主会话知悉）

1. **基线改为 main `bfb17bc9`。** 主会话通知公开桶已提交（`be688db7` + `bfb17bc9`），`Code-MoR-pubbucket-int` 作废。本卡最初按 `22ede435` + `-int` 搭建并完成了全部开发与验证；重建时先把本卡自己的改动保存为补丁（按各文件正确的基线：公开桶文件以 `bfb17bc9` 为基、本需求文件以 `Code-MoR-i18n` 为基），再在 `bfb17bc9` 上重放本需求与本卡改动，不再搬入任何公开桶 diff。重建后没有再读 `-int`。
2. **补了 Platform 的页面能力清单。** `console-resource-capabilities.json` 是 Platform 的“当前部署代码的页面能力”。本需求的 Web 在 `pageCapabilities.ts` 增加了三个页面，但 Platform 清单没有同步（B-report 第 5 节第 2 条留给主会话）。已按 Web 的内容与位置（`OPERATIONS_QUERY_TEMPLATES` 之后）补上三个 `OPERATIONS_I18N_*` 组件及 7 个按钮，与公开桶的 `OPERATIONS_FILE_RECORDS` 并存。
3. **初始化数据与 PDManer 现在在本卡范围内**（主会话改基线时追加），见第 4.2、4.3 节。

## 2. 工作目录搭建与合并

**步骤**

1. 保存本卡改动：22 个修改文件按各自基线生成补丁，6 个新文件原样保存（scratchpad）。
2. `git worktree remove --force` 旧目录后，`git worktree add --detach /Users/kenny/Desktop/Workspace/Code-MoR-i18n-int bfb17bc9`。
3. 本需求改动：在 `Code-MoR-i18n` 只读生成 `git diff --binary 22ede435` 与未跟踪文件清单（排除 `node_modules`、`dist`、`i18n-out`；与第一次搭建时逐字节相同），在新目录 `git apply --3way` 后解包未跟踪文件。
4. 本卡改动：`patch -p1` 应用保存的补丁（无拒绝块），复制 6 个新文件。
5. `Code-MoR-i18n` 保持不变（改动数 107，前后一致）。

**合并结果**

- `git apply --3way` 自动合并了与 main（公开桶、工作流体验优化）都改过的文件，两边语义都保留，包括：`platform-support-schema.sql`、测试 `init/ddl.sql`、`compensation-event-catalog.yaml`、`PersistenceConfiguration`、`OperationsCatalog`、`PlatformDataCleaner`、Platform `README.md`、`table-ownership.yaml`，ops-BFF 的 `PlatformClientConfiguration` 与 `OperationsConsoleBffApplicationAutoTest`，ops-web 的 `pageCapabilities.ts`、`ConsolePage.tsx` 等。
- 两处冲突手工解决：

| 文件 | 解决 |
|---|---|
| `TableOwnershipAutoTest.java` | 总数表达式末尾为 `+ 1 + 7`；`mor_platform` 为 71 张（公开桶后 64 + 多语言 7）；两段说明都保留。`table-ownership.yaml` 头注释 retained-service 数改为 70 |
| ops-web `configurationModel.test.ts` | 页面数 70（main 的 67 + 多语言 3），保留 main 新增的 `OPERATIONS_WORKFLOW_STARTED` 断言 |

- 语义适配：ops-BFF `LocalizationFileClientImpl` 开始上传时 `accessLevel` 传 `null`（按私有，与 `BinLibraryFileClientImpl` 一致），这是公开桶给 `StartFileUploadRequestBody` 加字段后的必要改动。

## 3. 本卡改动

### 3.1 JSON 格式检查（file-document）

- `StoredContentType`：
  - 新增常量 `JSON = "application/json"`，并加入 `SUPPORTED`，检查策略页的 `supportedMimeTypes` 随之出现 JSON。
  - UTF-8 文本中，第一个非空白字节为左花括号的识别为 JSON，其余仍为 CSV。
  - 带 BOM 的内容首字节不是左花括号，按 CSV 识别。因此声明为 JSON 的带 BOM 文件在完成上传时就返回 422 `FILE_POLICY_REJECTED`。
- `FormatFileScanner`：
  - 文本类型（CSV 与 JSON）都在读取全文时复核 UTF-8 且无 NUL。
  - JSON 另用 Jackson 3 流式解析器复核：根为对象，对象后只有空白。不建对象树，内存有界；解析与摘要共用同一次读取（新内部类 `VerifyingStream`）。
  - 读取存储失败（Jackson 包装的 I/O 异常）仍抛 503 由补偿重试，不当作格式不合法。
- 新增 `integration/scanning/JsonObjectInspector`。

### 3.2 端口实现

| 端口 | 实现 | 要点 |
|---|---|---|
| `ReleaseBundleStore.store` | `paytech-platform-support-core` `core.integration.localization.FileServiceReleaseBundleStore`（`files.enabled` 时装配） | 进程内调用 `StartFileUploadProcessor` → `FileStorageRegistry.putThroughGrant` 按签发授权 PUT 字节 → `CompleteFileUploadProcessor`，再核对文件服务登记的摘要与生成时一致，返回 `/files/v1/{fileId}`。全部在发布事务外，任一步失败为 503，已登记的上传或文件成为孤儿 |
| `ReleaseBundleStore.publicUrl` | 同上 | `FileLifecycleService.describe` 的 `publicUrl`：只有 PUBLIC 且检查通过才有值，否则 503，不伪造 |
| `ReleaseBundleReader` | localization `integration.impl.FileDocumentReleaseBundleReader` | 与卡 A 读导入文件的方式相同：元数据 + 存储注册表按记录坐标读原版本字节，并与登记摘要核对。公开文件同样经 Platform 账号读准确版本，不经公开地址或 CDN。不要求检查已通过（差异与摘要也读检查中或失败的发布）。文件缺失、对象不可用或摘要不符为 500，存储不可读为 503 |
| `ReleaseBundleFileChecker` | 卡 A 已有 `FileDocumentReleaseBundleFileChecker` | 未改，现在读取的是真实上传的公开文件 |

实现放在 core 的原因：store 需要复用文件入口的两个 Processor（锁、存储核对、登记与 FILE_SCAN 任务），它们在 core。localization 模块被 core 依赖，不能反向依赖。

支撑改动（file-document 与 core）：

- `StartFileUploadRequest` 新增可空的 `objectKeyPrefix`，带 `@Pattern` 段格式校验，上限 210 字符。原 7 参数构造器保留，内部 HTTP 入口与工作流附件不受影响，HTTP 合同不变。
- `UploadModels.Start` 增加同名字段；`FileUploadServiceImpl.objectRef` 生成“前缀/UUID”，没有前缀时仍是 UUID。
- `FileStorageRegistry.putThroughGrant` 与新类 `GrantedUploadHttp`：
  - 使用 OkHttp，取实例的超时与私有证书；不跟随重定向、不重试；
  - 请求头原样取自授权（Content-Type、If-None-Match），正文不附加媒体类型，避免改写已签名的头；
  - 非 2xx 为 503。
  - OkHttp 只能出现在 file-document（R15 出口规则），`ArchitectureAutoTest` 通过。

### 3.3 来源系统编码与对象键

- **来源系统**沿用文件服务现有常量 `FileSourceCodes.PLATFORM_SUPPORT = "platform-support"`（工作流附件同用）。访问级别为 `PUBLIC`，原因码为 `I18N_RELEASE_BUNDLE`，类型为 `application/json`，上传人为空（系统上传），展示名为 `{命名空间}.json`。
- **对象键**为 `i18n/{应用}/{发布号}/{语言}/{命名空间}/{SHA-256 前 16 位}/{UUID}`。完整摘要另存于 `t_i18n_release_bundle.content_hash` 与文件记录。
  - 末段 UUID 由文件服务生成，加上首次上传授权的 `If-None-Match: *`，保证每个文件只写一次。409 失败后重发同一发布号也不会碰撞。
  - 最长约 247 字符，不超过 `storage_object_ref` 的 255。
- **公开地址**为 `publicBaseUrl + "/" + 对象键`，由文件服务在检查通过后计算，不落库。

## 4. 数据脚本（均未执行）

### 4.1 `db/dml.sql` 第 3 部分（主工作区本需求目录）

文件头改为三部分；“不在本文件”说明更新为三个原因码都没有登记表，由 (来源系统, 访问级别) 路由决定策略。第 3 部分为一个事务，ID `900000000000135021`—`025`。

| ID | 表 | 内容 |
|---|---|---|
| 135021 / 135022 | `t_file_scan_policy` / `t_file_scan_requirement` | `I18N_BUNDLE_JSON` 修订 1（ACTIVE）：只允许 `application/json`，1048576 字节；`PLATFORM_FORMAT` / `FORMAT` / 最低版本 1 / 只接受 `FORMAT_VALID` |
| 135023 / 135024 | 同上 | `OPERATIONS_BFF_IMPORT` 修订 1（ACTIVE）：允许 `text/csv` 与 `application/json`，52428800 字节（50 MiB）；同一 FORMAT 要求（CSV：合法 UTF-8、无 NUL；JSON：另加第 3.1 节的对象检查） |
| 135025 | `t_file_storage_route` | `platform-support` × `PUBLIC` → 实例 `MINIO` 的桶 `mor-platform-public`，策略 `I18N_BUNDLE_JSON`，授权 300 秒 |
| 改既有行 | `t_file_storage_route` | mor-test route_id `239087906591948800`（`operations-console-bff` × `PRIVATE`）：只改策略引用，`CARD_BIN_CSV` → `OPERATIONS_BFF_IMPORT`，版本加一；route_id、桶与 `max_upload_seconds=900` 不变。WHERE 带 route_id 与旧值，执行后核对 `ROW_COUNT()=1` |

- **来源系统：** 文件服务为 Platform 进程内上传记录的来源是 `FileSourceCodes.PLATFORM_SUPPORT = "platform-support"`（工作流附件同用），`FileServiceReleaseBundleStore` 以它调用文件入口，路由行用同一值。
- **公开桶：** 按 `(实例代码, 桶名)` 子查询得到，变量缺省为 mor-test 的 `MINIO` / `mor-platform-public`（已登记并启用，对外地址 `https://minio.mor-test.localhost:29400/mor-platform-public`，匿名 GetObject 已允许）。桶不存在或未 ACTIVE 时 INSERT 因 NOT NULL 失败，整个事务不提交。
- **路由改指的条件：**route_id 与 `maxUploadSeconds=900` 不变、只改策略引用；`OPERATIONS_BFF_IMPORT` 保留 `text/csv` 与相同 FORMAT 检查且上限 52428800，另加 `application/json`；回滚恢复 `CARD_BIN_CSV`。第 4 条（发布后跑一次卡 BIN 导入验收）由主会话在发布时安排。
- **路由改指：code-mor-6b 已确认（条件见上）。**
- 段首列出只读前置核对 SQL；执行后要等 Platform 存储快照刷新（定时任务或任一次存储管理写操作）才生效。其他环境的 route_id 不同，按前置 SQL 查出后替换变量。

**`dml-rollback.sql` 第 3 部分：** 按 route_id 恢复 `CARD_BIN_CSV`（WHERE 带新值，核对行数），删路由 135025，删要求与策略。已有文件冻结了策略修订时，改为把策略置 RETIRED，不删除。第 1、2 部分未改。

### 4.2 `platform-support-initial-data.sql`（工作目录，main 上已含块 130 / 134）

末尾追加三段，各自一个事务：

- `db/dml.sql` 第 1 部分（注册块 135）与第 2 部分（语言 en、zh-CN 与四个应用），逐字取用（B-report 第 5 节）；
- 第 3 部分节选：只含两条检查策略及其要求（135021—135024）。两条路由依赖环境内的桶与路由 ID，不放进初始化数据，段首注释说明到需求目录 `db/dml.sql` 第 3 部分或存储管理页登记，做法与块 132 的 `CARD_BIN_CSV` 相同。

### 4.3 PDManer（`paytech/platform-support/docs/database/platform-support.pdma.json`）

- 手工加入七张 `t_i18n_*` 实体（字段、类型、长度、可空、默认值、注释、ascii_bin 排序规则、主键与索引都取自 schema DDL，与 A-report 第 3 节一致），以及 7 条逻辑关系（应用 ← 命名空间 / key / 发布，命名空间 ← key，key ← 翻译，发布 ← 语言包，应用当前发布 → 发布）。
- 新增关系图 `localization`（多语言），结构仿 `data_query` 图。
- 没有运行 `scripts/pdmaner` 的生成器。改动用一次性脚本完成：文件按原格式（2 空格缩进）重写，往返无差异，diff 只有新增行（+3315 行）。实体、字段、索引与关系 ID 为确定的 UUIDv5，全模型 ID 不重复。
- 核对：
  - 自写检查按 `platform-support-schema.test.mjs` 的同一规则比对七表 83 列、表注释与关系图，全部一致；
  - 仓库的三个 Platform 模型脚本测试（`platform-support-schema` 11 项、`platform-support-model` 31 项、`platform-file-upload-model` 2 项）与干净的 `bfb17bc9` 结果逐项相同。前两个在 main 上本就有 5 项、8 项失败（表清单与旧增量脚本过期），与本需求无关。

## 5. 新增与修改的测试

| 测试 | 场景 |
|---|---|
| `auto/ext/http/localization/ReleasePublicationApiTest`（5 例，新增） | `test_normalFlow`：预检 `changeSummary`；发布 PENDING_CHECK；4 个公开 JSON 上传，核对来源、级别、类型、上传人、对象键格式、摘要；FILE_SCAN 后经正式 Scanner / Dispatcher 上线 READY 并切指针；详情 `checkProgress` 4/4；manifest 地址等于公开桶地址，匿名读到原字节且摘要一致；`I18N_RELEASE_PUBLISH` 审计 |
| 同上 | `test_bundleRejected`：检查结果不被接受，发布 FAILED、原因列出语言包、不切指针；切到它返回 400；manifest 指定它返回 404 |
| 同上 | `test_contentChangedDuringPublish`：12 个命名空间共 24 个语言包；第一个上传落库后绕过锁把修订号加一，模拟锁租约失效，结果 409 `I18N_CONTENT_CHANGED`；没有发布与上线任务，24 个孤儿文件都已登记 |
| 同上 | `test_twoReleases`：第 2 次预检摘要以发布 1 为基准；发布 2 上线后把发布 1 恢复为等待检查再上线，变 READY 而指针仍为 2；差异 2 对 1 只有 zh-CN 一条修改，发布 1 无基准时 6 条全为新增；回滚到 1 后 manifest 返回发布 1 地址，`releaseNo=2` 预览返回发布 2；切回 2；两条 `I18N_RELEASE_ACTIVATE` 审计 |
| 同上 | `test_checkTimeoutAndInvalidTask`：检查器未部署时 Handler 可重试，`checkProgress` 全部 pending；第 60 次标 FAILED，原因“文件检查超时”；事件键非法与发布不存在为终态失败 |
| `auto/ext/http/localization/LocalizationFileImportApiTest`（3 例，新增，`internal-auth.mode=DUAL`） | 经内部文件接口真实上传 key 导入 JSON（开始、直传 MinIO、完成），检查通过后预览与确认成功；检查未完成时 key 预览与翻译导入都返回 409 `I18N_IMPORT_FILE_NOT_READY`；带 BOM 的 JSON 完成上传 422 |
| `FileScanHandlerTest`（+2 例） | JSON 小对象与 12 KB 对象通过；带 BOM、根为数组、对象后还有内容、未闭合、8 KiB 之后才缺右括号都为 FORMAT_INVALID |
| `MinioFileStorageTest` / `S3FileStorageTest`（识别参数 +3） | JSON 识别为 `application/json`；带 BOM 的 JSON 与 JSON 数组识别为 `text/csv` |

测试设施改动：

- `LocalizationApiTestBase`：
  - `publicBundleBucket()`：临时公开桶、管理员实例与 `platform-support` × `PUBLIC` 路由；
  - `runActivations()`：任务到期后经正式 Scanner 执行；
  - 每例开始前检查临时桶与上线任务为空基线，结束时等待 FILE_SCAN 与上线任务停止。
- `LocalizationMocker` 实现 `Clear`，按类型回收 `I18N_RELEASE_ACTIVATE` 任务。
- `LocalizationTestMapper` 增加发布、语言包、上传、上线任务、审计查询，以及两项前置改写：修订号加一、READY 改回 PENDING_CHECK。
- `FileMocker` 测试策略允许类型加入 `application/json`；`FileBuilder` 增加 JSON 样本。
- `ReleaseApiTest` 的类注释改为指向新测试。

## 6. 测试命令与结果（基线 `bfb17bc9`）

所有命令都在 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-int/paytech` 执行。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
R="-Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository"
L="-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment"
```

| 范围 | 命令（`./mvnw $R … -am test -Dsurefire.failIfNoSpecifiedTests=false`） | 结果 |
|---|---|---|
| Platform：files、filestorage、eventhandler（files + workflow）、integration/storage、localization、workflow HTTP、operations、api、architecture、configuration | `$L -pl platform-support/paytech-platform-support-test -Dtest='…/auto/ext/http/files/**,…/filestorage/**,…/eventhandler/**,…/integration/storage/**,…/ext/http/localization/**,…/ext/http/workflow/**,…/ext/http/operations/**,…/auto/api/**,…/architecture/**,…/configuration/**'` | **431/431**（其中 localization 25、files 30、filestorage 33、operations 192、workflow 41、eventhandler 22、storage 40） |
| localization、workflow 模块 + arch-rules | `-pl …-localization,…-workflow,tools/arch-rules -Dtest='com/paytech/platform/support/localization/**,com/paytech/platform/support/workflow/**,com/paytech/arch/**'` | localization **36/36**，workflow **65/65**，arch-rules **68/68** |
| ops-BFF 全量 | `-pl operations-console-bff/paytech-operations-console-bff-test -Dtest='com/paytech/operations/console/bff/**'` | **2344/2344** |
| client-BFF 全量 | `-pl client-console-bff/paytech-client-console-bff-test -Dtest='com/paytech/client/console/bff/**'` | **91/91** |
| 整个 reactor 编译 | `./mvnw -o -q $R test-compile -DskipTests` | 退出码 0 |
| operations-console-web | `npm ci`、`npm test`、`npm run build` | **1451/1451**，构建通过 |
| client-console-web | 同上 | **80/80**，构建通过 |
| PDManer 模型脚本测试 | `node --test scripts/pdmaner/platform-support-schema.test.mjs` 等三个 | 与干净 `bfb17bc9` 逐项相同（见第 4.3 节） |

旧基线（`22ede435` + `-int`）上的同类运行也全部通过；其中 `ReleasePublicationApiTest` 连续 3 次 5/5，用于确认并发用例稳定。ops-BFF 在旧基线首次全量时有 1 例日志捕获断言（`RiskDecisionConfigurationBrowserBoundaryAutoTest`）失败，单独重跑与后续全量都通过，新基线上没有复现。未运行 `npm --prefix requirements-site test`，本卡没有改需求站点。

## 7. 遗留与待裁决

1. **以 `{` 开头的 CSV 现在会被识别为 JSON。** 如果有人上传首字符为左花括号的 CSV 并声明 `text/csv`，完成上传会被拒绝。卡 BIN 库的 CSV 以表头开头，不受影响。是否接受，请确认。
2. **执行 DML 后不会立即生效。** 新路由与新策略要等 Platform 存储快照刷新。mor-test 的 Platform 必须已是包含本卡代码的镜像，否则 JSON 检查不通过、发布返回 503。发布后由主会话跑一次卡 BIN 导入验收（code-mor-6b 条件 4）。
3. **发布失败留下的孤儿文件。** 409 或中途 503 后，已上传的语言包成为孤儿文件，没有自动清理，与技术方案一致。公开桶上这些对象匿名可读（内容只是文案）。是否需要清理任务，请确认。
4. **语言包数量多时会有耗时。** 每个语言包是 3 次进程内调用加 1 次存储 PUT，本机 24 个约 1—2 秒。语言数 × 命名空间数很大时，接近锁租约 120 秒，管理端读取超时为 9 秒，届时需要评估。
5. **仓库的 Platform PDManer 脚本测试在 main 上本就有失败**（schema 测试 5 项、model 测试 8 项：表清单与旧增量脚本过期），本卡没有修，结果与 main 相同。
6. **审计。** 发布与切换审计已在测试中按 action / resourceType / resourceId / result 断言。补偿 Handler 上线不写审计，与技术方案一致。

## 8. 改动文件（相对 `bfb17bc9` 与本需求原始改动之外，本卡新增或修改）

**新增**

- `paytech-platform-support-core/.../core/integration/localization/FileServiceReleaseBundleStore.java`
- `paytech-platform-support-file-document/.../integration/scanning/JsonObjectInspector.java`
- `paytech-platform-support-file-document/.../integration/storage/GrantedUploadHttp.java`
- `paytech-platform-support-localization/.../integration/impl/FileDocumentReleaseBundleReader.java`
- 测试 `auto/ext/http/localization/ReleasePublicationApiTest.java`、`LocalizationFileImportApiTest.java`

**修改**

- file-document：`StoredContentType`、`FormatFileScanner`、`FileStorageRegistry`、`UploadModels`、`FileUploadServiceImpl`。
- core：`StartFileUploadRequest`、`StartFileUploadProcessor`。
- localization：`ReleaseBundleStore`、`ReleaseBundleReader`（仅 JavaDoc）。
- app：`application.yml`（仅注释）。
- Platform 文档与数据：`README.md`（公开桶一节加两条）、`docs/database/platform-support.pdma.json`（七表与关系图）、`docs/database/sql/platform-support-initial-data.sql`（块 135）。
- access-control：`operations/console-resource-capabilities.json`，增加三个 `OPERATIONS_I18N_*` 组件。
- ops-BFF：`LocalizationFileClientImpl`。
- arch-rules：`table-ownership.yaml` 头注释、`TableOwnershipAutoTest`（冲突解决）。
- ops-web：`configurationModel.test.ts`（冲突解决）。
- 测试：
  - `LocalizationApiTestBase`、`LocalizationMocker`、`LocalizationTestMapper`；
  - `FileMocker`、`FileBuilder`；
  - `FileScanHandlerTest`、`MinioFileStorageTest`、`S3FileStorageTest`、`ReleaseApiTest`。
- 主工作区本需求目录：`db/dml.sql`、`db/dml-rollback.sql`、本报告。

## 9. 重放到 main（F2）

**结论：** `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-main`（detached，`bfb17bc9`，即当前最新的本地 main）已包含本需求与本卡的全部改动，F §6 的回归全部通过。未提交、未执行 SQL、未动容器与 mor-test。

**主会话对第 7 节的裁决（已记录）：** 1、2 接受（code-mor-6b 同意）；3 写入发布手册（先换 Platform 镜像，再执行 DML，再触发存储快照刷新）；4 暂不做孤儿清理（S9）；5 作为已知限制保留；7 无异议。

### 9.1 重放方式

- 本地 main 仍是 `bfb17bc9`，与 `Code-MoR-i18n-int` 的基线相同。第 2 节在该基线上已完成全部合并（`git apply --3way` 自动合并加两处手工冲突），所以重放直接取 `Code-MoR-i18n-int` 相对 `bfb17bc9` 的改动：`git diff --binary bfb17bc9` 加未跟踪文件（排除 `node_modules`、`dist`、`i18n-out`）。
- 新目录 `git apply` 无冲突；应用后与 `Code-MoR-i18n-int` 逐文件比对（`diff -rq`，排除构建产物）完全一致：80 个修改文件、45 个未跟踪路径。
- 改动只含本需求（`Code-MoR-i18n` 原始改动）与本卡（第 8 节清单）的内容，不含任何公开桶 diff（公开桶已在基线）。

### 9.2 与 main 其他提交的合并点（两边都保留）

| 文件 | 涉及的 main 提交 | 处理 |
|---|---|---|
| `TableOwnershipAutoTest.java` | 公开桶 `be688db7`（`t_file_version`） | 手工：总数表达式 `… + 4 + 1 + 7`，说明两段都保留；`mor_platform` 断言 71（main 的 64 + 七张 `t_i18n_*`） |
| `table-ownership.yaml` | 公开桶 | 自动合并 `t_file_version` 与七表；头注释 retained-service 数 63 → 70 |
| ops-web `configurationModel.test.ts` | 工作流体验 `f707f2da`（`OPERATIONS_WORKFLOW_STARTED`）、公开桶（`OPERATIONS_FILE_RECORDS`） | 手工：页面数 70（main 67 + 多语言 3），保留 `/workflow/started` 断言 |
| ops-web `pageCapabilities.ts`、`ConsolePage.tsx` | 工作流体验、公开桶 | 自动合并，多语言三个页面与“我发起的”“文件记录”并存 |
| `platform-support-schema.sql`、测试 `init/ddl.sql` | 公开桶 | 自动合并：`t_file_version` 与五表变更，加多语言七表 |
| `compensation-event-catalog.yaml` | 公开桶 | 自动合并：`FILE_PUBLIC_SYNC` 与 `I18N_RELEASE_ACTIVATE` |
| `PersistenceConfiguration.java` | 公开桶 | 自动合并：文件两个枚举 TypeHandler，加 localization MapperScan 与五个枚举 |
| `OperationsCatalog.java`、`PlatformDataCleaner.java`（测试） | 公开桶 | 自动合并：块 134 权限与 `t_file_version` 回收，加块 135 权限与七表回收 |
| Platform `README.md` | 公开桶 | 自动合并，另加本卡两条（JSON 检查、进程内上传） |
| ops-BFF `PlatformClientConfiguration.java`、`OperationsConsoleBffApplicationAutoTest.java` | 公开桶（文件存储代理与新路由）、工作流体验 `92d2d1db` | 自动合并：多语言代理工厂、路由与 metadata-only 前缀与 main 内容并存 |
| `console-resource-capabilities.json` | 公开桶（`OPERATIONS_FILE_RECORDS`） | 在 `OPERATIONS_QUERY_TEMPLATES` 之后加三个 `OPERATIONS_I18N_*` |
| ops-BFF `LocalizationFileClientImpl.java` | 公开桶（`StartFileUploadRequestBody.accessLevel`） | 语义适配：传 `null`（按私有） |
| `platform-support-initial-data.sql` | 公开桶块 134、工作流体验对块 127 的修改 | 块 135 追加在块 134 之后，见 9.3 |
| `platform-support.pdma.json` | 公开桶（`t_file_version` 等手工修改） | 只追加，见 9.4 |

### 9.3 初始化数据

`platform-support-initial-data.sql` 末尾，在块 134 之后按需求 `db/dml.sql` 原样追加**全部三部分**，各自一个事务，已逐字节核对。这替换了第 4.2 节原来只放两条策略的节选。`db/dml.sql` 仍是发布执行文件。

- 第 1 部分：注册块 135。
- 第 2 部分：语言与应用。
- 第 3 部分：只含两条检查策略及其要求。（F2 初稿曾整段追加，包括 `platform-support` × `PUBLIC` 路由与 route `239087906591948800` 改指；主会话复核时移除这两条依赖环境的语句，只保留在需求目录 `db/dml.sql` 第 3 部分随各环境发布执行。）

需要知悉：第 3 部分的路由 INSERT 按 `MINIO` / `mor-platform-public` 查桶，UPDATE 按 mor-test 的 route_id。在没有该桶的全新环境里按初始化文件执行时，这一个事务会因 `bucket_id` 为 NULL 整体失败，UPDATE 也会影响 0 行。块 132 当时把路由排除在初始化数据之外，就是因为这一点。新环境初始化时请改这两个变量，或跳过第 3 部分的路由语句。

### 9.4 PDManer

- `platform-support.pdma.json` 已加入七张 `t_i18n_*` 实体与 7 条逻辑关系，放在新关系图 `localization`（多语言）中。编码方式仿 `data_query` 图与 `t_query_template` 实体：表头、`logicalTableKey`、baseType、domain、ascii_bin 的 `extProps`。
- 字段类型、长度、可空、默认值、注释、索引逐项取自 `platform-support-schema.sql`。
- 没有运行 `scripts/pdmaner` 生成器，文件只有新增行。
- 自写检查（同 `platform-support-schema.test.mjs` 的列规则）七表 83 列全部一致，全模型 ID 不重复。
- `scripts/pdmaner` 漂移测试的结果与干净 `bfb17bc9` 逐项相同，测试文件未改：

| 测试 | 失败数 | 干净 main 上的失败数 |
|---|---|---|
| `platform-support-schema.test.mjs` | 5/11 | 5/11 |
| `platform-support-model.test.mjs` | 8/31 | 8/31 |
| `platform-file-upload-model.test.mjs` | 0/2 | 0/2 |

### 9.5 回归（`Code-MoR-i18n-main`，私有 Maven 仓库，local-environment 参数同第 6 节）

| 范围 | 结果 |
|---|---|
| Platform：files、filestorage、eventhandler、integration/storage、localization、workflow、operations、api、architecture、configuration | **431/431** |
| localization 模块 / workflow 模块 / arch-rules（`TableOwnershipAutoTest` 7/7，总数与 `mor_platform` 71 张已按 main 复核） | **36/36** / **65/65** / **68/68** |
| ops-BFF 全量 | **2344/2344** |
| client-BFF 全量 | **91/91** |
| 整个 reactor `test-compile` | 退出码 0 |
| operations-console-web `npm test` / `build` | **1451/1451**，构建通过 |
| client-console-web `npm test` / `build` | **80/80**，构建通过 |

第一次 Platform 全量为 388/431。失败的 4 个类全部报“测试独占表存在数据”与 `t_access_tenant` 主键重复：同一时间有其他会话在共用的本地 `mor_platform` 上跑 Platform 测试。确认没有并发进程后，这 4 个类单独重跑 54/54，完整重跑 431/431。代码与 `Code-MoR-i18n-int` 完全相同，后者此前也是 431/431。
