# platform-support：文件公开桶与固定访问地址 · mor-test 发布包

阶段 6（2026-10-22 20:25—21:10Z，即本地 10-23 凌晨）。本阶段只做了以下几件事：只读核对、干净 worktree 构建基线、集成目录构建候选镜像、写发布包。**没有停 / 起 / 替换 mor-test 任何容器，没有执行写 SQL，没有调用 mor-test 写接口**，没有提交、没有 install、没有改 `:development` tag，没有向容器 `docker cp` 写入。阶段 7 须用户另行授权，由主会话按第 6 节执行，结果写第 10 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像（6 个）：** platform-support-service、operations-console-bff、operations-console-web，以及 `paytech-platform-support-api` 文件 / 存储接口的另外三个生产消费方 card-service、client-master-service（CCT）、client-console-bff。
- **数据库（`mor_platform`）：** 本需求 DDL（新表 `t_file_version`、5 表加 10 列、3 个唯一键变宽、2 个索引）与 v1 回填；块 134 注册 157 行并激活（12 / 2 / 2 / 1）；块 130 两条路由路径加 `/{accessLevel}`。
- **环境变量：** 不新增、不删除（第 3.3 节）。
- **存储侧（用户 / 主会话配合，第 8 节）：** 准备一个开启匿名读取的公开测试桶；按需补 IAM / MinIO 策略。
- **不动：** Gateway、Nginx、Nacos、Vault、XXL、主 compose 与其余容器。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 集成目录 | `/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-int` = main `22ede435` + A 209 + B 72 个路径（重叠 32 个，共 249 个路径，未提交），验证见 [delivery.md](../delivery.md) 阶段 5。构建前改动指纹（`git diff HEAD --binary` + 未跟踪文件 SHA-256 的汇总）`4fdb671ec8054b47` |
| main 现状 | `32dbdd18`，比 `22ede435` 多一个提交：恢复 PDManer 模型（card / channel-connector / payment / **platform-support**`.pdma.json`）与 card 两个测试类的调用方。**不涉及任何生产代码或镜像内容**；但它与集成目录同改 `paytech/platform-support/docs/database/platform-support.pdma.json`（A 卡以 `22ede435` 的生成器版本为底拼入本需求实体，而 `32dbdd18` 正是把那个生成器版本换回手工维护版本），**本需求提交时须以 `32dbdd18` 的模型为底重新加入 `t_file_version` 等 6 个实体**，见第 11 节第 5 项 |
| Java | JDK 25、`~/.m3`、`./mvnw -B -o -DskipTests -pl <5 个 app> -am clean package`，没有 install。镜像用 `test-environment/java/Dockerfile`（集成目录与主工作区逐字节相同），`health-probe/` 从运行容器读出（六个容器同一份） |
| ops-web | 构建目录在会话临时目录：集成目录 `operations-console-web`（不含 `node_modules` / `dist`）+ 主工作区 10 个数据查询页面体验文件（code-mor-55，未提交；清单见 [3DS 发布包 2.4.4](../../payment-3DS全链路/release/release.md)：`src/dataQuery/` 下 `DeclarationPanel.tsx`、`TemplateEditor.tsx`、`TrialPanel.tsx`、`VersionDrawer.tsx`、`dataQuery.css`、`templateModel.ts`、`templateSync.ts`、`templateSync.test.ts`，`src/pages/DataSources.tsx`、`QueryTemplates.tsx`）。按钮布局 6 个文件（`ApiCredentials.tsx`、`Audit.tsx`、`GatewayConfiguration.tsx`、`configuration.css`、`styles.css`、`RefreshButton.tsx`）集成目录已有，与主工作区逐字节相同。`npm ci` → `tsc --noEmit` 通过 → `npm test` **1362 / 1362** → `npm run build` → `docker build`（`deploy/nginx.conf` 与现网逐字节相同）。集成目录本身未被改动 |
| 镜像核对 | 镜像内 JAR / `health-probe/` / `dist/` / `nginx.conf` 与构建产物逐字节相同；Entrypoint、User、WorkingDir、Env（Web 另含 Cmd）与对应运行镜像相同 |

### 1.2 基线证明（`22ede435`，不加本需求，与运行容器比较）

基线 worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-base`（`git worktree add --detach … 22ede435`，阶段 6 结束时已删除）。比较方法同前置需求：`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目，第三方 JAR 整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引、目录项。Web 比较 `dist/` 与 `nginx.conf` 逐字节。

| 容器 | 运行镜像（JAR 前 16 位） | 与 `22ede435` 构建 | 结论 |
|---|---|---|---|
| platform-support-service | `96da579b…`（`8f35483745704c94`） | **0 差异**（2,702 项） | 现网 = `22ede435` |
| operations-console-bff | `9c382db3…`（`9a310cf3af3e53d3`） | **0 差异**（2,509 项） | 现网 = `22ede435` |
| card-service | `c39a3990…`（`221dd930caf4a95e`） | **0 差异**（1,098 项） | 现网 = `22ede435` |
| operations-console-web | `8bdbdd44…` | `22ede435` + 主工作区 16 个文件（原样复制）：`dist/` **191 个文件逐字节一致**，`nginx.conf` 一致 | 现网 = `22ede435` + 16 文件 |
| client-master-service | `e3fded0b…`（`41a647487f7df657`，文件多存储第二段发布） | 应用类与 Foundation **0 差异**；只差库：+3、~4 | 现网 = `73a2460d` 等价源码。差异全部来自 `73a2460d..22ede435` 的已提交合同库（下表） |
| client-console-bff | `105b962e…`（`82398677c4278014`，同上） | 应用类 **0 差异**；只差库：+3、~1 | 同上 |

两个客户侧服务会顺带上线的已提交改动（只报告，不取舍）：

| 库 | 提交 | 内容 | 影响 |
|---|---|---|---|
| `paytech-platform-support-api` +3 | `de4f7ba4`（卡 BIN + 3DS） | `CountryRegionQueryApi` 与两个响应类 | 两者不调用 |
| `paytech-platform-support-api` ~1 | `42879401`（工作流附件内嵌预览） | `WorkflowAttachmentApi` 可选 `disposition` 参数 | 两者不调用 |
| `paytech-commercial-api` ~2（只 CCT） | `de4f7ba4` | `CommercialCodes.CardBrand` 加 MADA / DISCOVER / JCB / DINERS 常量、`BillingFactType` 注释 | 编译期常量，CCT 不用 |
| `paytech-messaging` `catalog/events.yaml`（只 CCT） | `de4f7ba4`、`22ede435` | 新增 `ChannelPaymentNotified`（channel-connector → payment）、已存卡事件注释 | CCT 不生产不消费；目录与 mor-test 上的生产方一致 |

### 1.3 消费方清单（在 `22ede435` 上 grep 生产代码确认）

依赖 `paytech-platform-support-api` 的生产模块有 acquiring、api-gateway、billing、card、client-console-bff、client-master、ledger、operations-console-bff（及 Platform 自身）。其中调用本需求改动的 `InternalFileApi` / `FileStorageManagementApi` 的只有四个：

| 服务 | 生产代码调用 | 读到的响应类（本需求加字段） |
|---|---|---|
| card-service | `PlatformFileClientImpl`：`getFile`、`authorizeDownload`（BIN 库导入读取 CSV） | `StoredFileResponseBody` |
| client-master-service | `ClientMaterialAuthorityClient`：`getFile`（关联材料时核对文件） | `StoredFileResponseBody` |
| client-console-bff | `PlatformFileClientImpl`：`startUpload`、`completeUpload`、`getFile`、`authorizeDownload` | `FileUploadCompletionResponseBody`、`StoredFileResponseBody` |
| operations-console-bff | `PlatformFileStorageClientImpl`（存储管理）、`ClientReviewFileClientImpl`（O10）、`BinLibraryFileClientImpl`、`PlatformWorkflowClientImpl`（工作流附件） | 上两者 + `StorageBucket`、`StorageRouteResponseBody` |

acquiring、api-gateway、billing、ledger 不调用这两个接口，不需要换。内部客户端开启 `FAIL_ON_UNKNOWN_PROPERTIES`：**旧消费方读新 Platform 的响应会因新字段失败**，所以四个消费方必须先于 Platform 换。

### 1.4 候选镜像（tag `pubbucket-20261023a`）与差异

| 服务 | 候选镜像 | JAR 前 16 位 | 相对 `22ede435` 构建 |
|---|---|---|---|
| card-service | `sha256:810b6b72b6ea019c60b0afff4750b0d858aaec5c2ee0acd5102a7d488459a81f` | `5cc2fd8dad707c11` | 只 `paytech-platform-support-api`：+19、~10（应用类 0 差异） |
| client-master-service | `sha256:2e495b40adee0d918d7fe96a93be837514f83936e2537139122de6155c5c604f` | `795461b0b2962663` | 同上 |
| client-console-bff | `sha256:b8ecaaea2b2f0e642cb21400bb904cf7b3d65da165ef099871091ae937ac7748` | `aea366e52763a595` | 同上 |
| operations-console-bff | `sha256:06d850d16a3f697f98f8bcc8fe455eed09cbb0cc1dc5adcd8e5935c323d524da` | `fef610e60dac3754` | 应用类 +1、~6；api +19、~10 |
| platform-support-service | `sha256:68ba631faa8dfb65cb09296cd28a37f6e8c2bcf406bfdecd4de70016e3a6475f` | `9d735ffd2321621b` | 应用类 +2 ~6；api +19 ~10；core +25 ~16；file-document +50 ~58（含 4 个新 Mapper XML、4 个改动 XML）；audit-security ~13；access-control ~1（`console-resource-capabilities.json`）。**第三方 JAR 无增删，`application.yml` 未变** |
| operations-console-web | `sha256:f8b5dc3234a587285a5d47c4016240aedaf07248b43185c954343381251d7e5e` | — | 源码相对基线（`22ede435` + 16 文件）只多 B 卡的改动：新增 `src/fileRecords/`（7）、`src/fileStorage/{ScanPolicyTab.tsx, fileStorage.css, scanPolicy.ts, scanPolicy.test.ts}`、`src/pages/FileRecords.tsx`；修改 `fileStorage.ts`、`fileStorage.test.ts`、`StorageModals.tsx`、`FileStorage.tsx`、`auditView.ts`、`ConsolePage.tsx`、`pageCapabilities.ts`、`configurationModel.test.ts`、`README.md`。不回退现网按钮布局与数据查询页面体验 |

三个消费方的 api 差异都是同一组：新增 `StartFileReplacementRequestBody`、检查策略 / 文件记录 / 桶修改的请求与响应类；`StoredFileResponseBody`、`FileUploadCompletionResponseBody`、`StorageBucket`、`StorageRouteResponseBody`、`StartFileUploadRequestBody`、`CreateStorageRouteRequestBody`、`RegisterStorageBucketRequestBody`、`FileStorageCheckResult`、两个接口类变化。`FileDownloadResponseBody` 只改注释，类文件不变。

## 2. 兼容性（决定执行顺序）

**① DDL 对旧 Platform 安全，可以先于镜像执行。**
- 新列都有默认值且等于存量取值（`PRIVATE`、`current_version_no=1`、`replacement_no=0`、`file_version_no=1`）；旧代码按显式列读写，写入都落在默认值上。
- 三个变宽唯一键对旧代码仍成立：`uk_upload_storage_target` 加 `replacement_no`（旧写入恒为 0）、`uk_file_storage_route_source` 加 `access_level`（恒为 PRIVATE）、`uk_file_scan_attempt` 加 `file_version_no`（恒为 1）。本机已验证：按旧代码构建的会话在新 Schema 上跑测试正常（delivery.md 阶段 4）。
- `MODIFY COLUMN` 的 11 列在 mor-test 的类型 / 可空与 DDL 完全相同，只改注释（预检 S07）；`AFTER` 锚点列都在（S08）。表都很小（文件 69、上传 70、结果 69 行）。

**② mor-test 现有数据在回填与新唯一键下成立（预检 D 组，全部等于期望）。**
- 69 个文件全部 AVAILABLE / ACTIVE → 回填 69 个 v1，全部 `CURRENT`；`upload_id` 不重复，策略修订都存在。
- 写入规则 3 条：`*` → MINIO / `mor-platform-files` / `CLIENT_ONBOARDING_FORMAT`；用户登记的 `client-console-bff` → `S3_TEST` / `mor-test-platform-files-k7q2`；code-mor-6b 的 `operations-console-bff` → `mor-platform-files` / `CARD_BIN_CSV`（route `239087906591948800`）。回填为 PRIVATE 后 `(来源, 级别)` 三条互不相同。
- 两个桶（`mor-platform-files`、`mor-test-platform-files-k7q2`）回填为 PRIVATE。阶段 6 匿名读取核对：两个桶对已有对象的不带凭据请求都返回 **403**，新 Platform 的私有桶检查（“匿名不可读”）应仍为 OK。
- 两个策略（`CLIENT_ONBOARDING_FORMAT` r1、`CARD_BIN_CSV` r1）均 ACTIVE、各一条 `PLATFORM_FORMAT` 检查要求，允许类型都在格式检查器可识别范围（含 `text/csv`），新的策略管理页可直接读取。
- 没有进行中的上传（1 条 2026-10-22 17:34Z 已过期仍为 CREATED 的工作流附件上传，与本需求无关），没有待执行的 `FILE_SCAN` 补偿事件。

**③ 新旧混跑。**

| 组合 | 结果 |
|---|---|
| 新消费方（新 api）+ 旧 Platform | 正常：新字段为包装类型，旧 Platform 不返回时为 null（阶段 6 用私网探针读旧 Platform 的文件元数据确认没有这些字段）；四个服务的应用代码不读新字段 |
| 旧消费方 + 新 Platform | **失败**：严格反序列化遇到 `accessLevel`、`versionNo` 等未知字段，入驻上传 / 关联、O10 下载、BIN 导入、工作流附件、存储管理全部报错。所以 Platform 必须最后换（Web 除外） |
| 旧 ops-web + 新 ops-bff | 存储管理页不可用：旧 Web 对桶、路由响应做“字段集合精确匹配”，新 BFF 输出多了 `accessLevel` / `publicBaseUrl`（null 也输出）；登记桶时旧 Web 不带 `accessLevel`，新 BFF / Platform 拒绝。**其他页面不受影响**：工作流、BIN 库对完成回执与下载授权的解码是宽松的 |
| 新 ops-web + 旧 ops-bff | 存储管理页不可用（反向同理，且旧 BFF 严格请求体拒绝新字段）。本顺序不会出现 |
| 块 130 路径：旧路径 + 新 ops-bff / 新路径 + 旧 ops-bff | 写入规则编辑、删除 404（新 ops-bff 只有 `/routes/{s}/{l}`，旧 ops-bff 只有 `/routes/{s}`）。改路径放在新 ops-bff 与新 Platform 都在线之后、换 Web 之前 |
| 块 134 激活 + 旧 ops-web | 无影响：旧 Web 按组件代码注册页面，不认识的 `OPERATIONS_FILE_RECORDS` 不显示（`menu.test.ts` 有用例）；新路由转发到新 ops-bff → 新 Platform |
| 块 134 休眠注册 + 任何镜像 | 无影响：路由 DISABLED、授权 REVOKED、菜单 DISABLED；与块 130 无行级依赖（B 的草稿写了“前置 block130-route-path”，实际块 134 不引用 130411 / 130412，只是 initial-data 里两者相邻；本包把注册提前到窗口外以缩短窗口） |
| 旧 Platform 写入的文件（DDL 之后、新 Platform 之前） | 没有 v1 版本行；回填 DML 可重跑，新 Platform healthy 后立即再跑一次补齐（第 6 节第 7 步），`99-postcheck` P06 核对为 0 |

**④ 替换顺序与不可用窗口。**
1. 窗口外：DDL + 回填（旧 Platform 运行中）→ 块 134 休眠注册 → **card、client-master、client-console-bff 一次命令替换**（各自重启数十秒：卡支付、客户入驻、客户控制台在各自重启期间短暂不可用，属于常规单服务重启；新 api 容忍旧 Platform）。
2. 窗口开始：**ops-bff** → **Platform** → 再跑回填 → 块 130 改路径 → 块 134 激活 → 等 Gateway Snapshot（≤ 60 秒）→ **ops-web**，窗口结束。
3. **不可用窗口只影响运营控制台「存储管理」页**（实例 / 桶 / 写入规则的查看与维护），约 **3—4 分钟**（参照前两次发布：Platform 替换约 35 秒、ops-bff 约 45 秒，加 60 秒 Snapshot 与 Web 约 10 秒）。客户入驻上传 / 下载、O10、工作流附件、BIN 导入在整个过程中保持可用（Platform 重启的约 30—40 秒内所有依赖 Platform 的功能照常短暂不可用）。

**⑤ 本需求不需要新的 XXL / MQ / Nacos 配置。** `FILE_PUBLIC_SYNC` 是 Platform 本地补偿事件（`compensation-event-catalog.yaml` 只是登记表），由已有的补偿执行器处理；匿名检查超时取存储实例设置。

## 3. 只读核对（阶段 6 已做）

### 3.1 数据库预检

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22T20:51:02Z 在 `mor-test-mysql-1`（按完整名称，root）执行：**37 项全部等于期望**，输出见 [`sql/00-precheck-20261022T2048Z.out`](./sql/00-precheck-20261022T2048Z.out)（文件名取首次执行时间；内容为 20:51Z 的复跑）。

| 组 | 结果 |
|---|---|
| 结构 S01—S09 | `t_file_version` 不存在、10 个新列与 2 个新索引不存在；三个唯一键为旧列组合；MODIFY 的 11 列类型与 DDL 相同；7 个 AFTER 锚点列都在；`mor_platform` 63 张表 |
| 数据 D01—D18 | 见第 2 节②；实例 MINIO、S3_TEST ACTIVE；桶 2 个、路由 3 条 |
| 注册 G01—G10 | 块 130 两条路由仍为旧路径且 ACTIVE、块 130 共 12 条 ACTIVE；块 134 ID 段在 7 张表均为 0；权限代码 / 资源代码 / 12 个路由代码 / 14 个（方法, 外部路径）都未占用；ops-bff Target、父菜单 562、PLATFORM_ADMIN、FILE_STORAGE_READ / MANAGE 都在；平台配置下 sort 85 空闲；工作流 `disposition` query_param 127030 仍在 |
| 记录 | 文件 69、上传 70（1 条过期未收尾）、扫描结果 69；PLATFORM_ADMIN 版本 **45**；块 130 两条路由版本和 **4**；`FILE_DOWNLOAD_AUTHORIZE` 审计 4 条 |

`sql/99-postcheck.sql` 已在本机 `mor-local-mysql-1`（已执行本需求 DDL，只读）试跑一遍确认语法；期望值按上表推算（角色版本 46、路由版本和 6）。

### 3.2 容器与镜像

六个容器 healthy、重启 0；`.Image` 与 [compose-rollback.yaml](./compose-rollback.yaml) 一致。回滚镜像都还在本机，其中三个的本机 tag 被写坏（如 `mor-test/platform-support-serviceard3ds-20261022b`），一律按 sha256 引用。

### 3.3 环境变量

- 主 compose（`test-environment/compose.applications.yaml`，`de4f7ba4` 已提交）已包含历次覆盖文件中这六个服务的全部键：Platform 的 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID`、5 个 RabbitMQ 键、`PLATFORM_SUPPORT_FILES_ENABLED`、`DATAQUERY_HTTP_CALLERS_0` 与信任库；card 的 `JAVA_TOOL_OPTIONS` 信任库与挂载。所以候选 / 回滚文件只钉镜像。
- 用 `compose.sh -f <文件> config` 渲染（两份 `--quiet` 都通过）后与运行容器 `printenv` 逐键、逐值比较（只比较是否相等，不输出值）：platform 53 键、ops-bff 14、ops-web 0、card 33、client-master 34、client-console-bff 10，**候选与回滚都 0 缺、0 多、0 不同**；挂载点也一致（Platform 2 个、card 1 个）。
- 本需求不新增变量（匿名检查超时取实例的 `connect_timeout_ms` / `read_timeout_ms`，OkHttp 信任证书取实例私有证书或 JDK 默认信任库）。

### 3.4 存储侧（不带凭据的只读请求）

| 项 | 结果 |
|---|---|
| `S3_TEST` 桶 `mor-test-platform-files-k7q2` CORS | 预检 `OPTIONS`（`Access-Control-Request-Method: PUT`）：Origin `https://localhost:28443` 与 `http://localhost:28080`、请求头只有 `content-type` 时均 **200**，允许方法 PUT / GET / HEAD；带 `if-none-match` 也允许。**替换上传（不带 If-None-Match）的 CORS 已满足**，不需要改 |
| mor-test MinIO CORS | 服务端级 `MINIO_API_CORS_ALLOW_ORIGIN` 已含两个 Origin，预检 `content-type` 返回 204 允许 PUT。满足 |
| 两个私有桶匿名读 | 不带凭据 HEAD 已有对象：S3 **403**、MinIO **403**（旁路 curl 容器，`--network container:platform`） |

IAM 策略本身无法在不使用 Platform 凭据的情况下读取，见第 8 节。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件，六个服务 |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 只读预检（阶段 6 已执行；阶段 7 备份时再执行一次） |
| [sql/01-platform-ddl.sql](./sql/01-platform-ddl.sql) / [sql/02-platform-backfill.sql](./sql/02-platform-backfill.sql) | 本需求 DDL 与 v1 回填，与 `db/ddl.sql`、`db/dml.sql` 逐字节相同（DDL 也与集成目录 `changes/2026-10-22-file-public-bucket.sql` 相同） |
| [sql/10-block130-route-path.sql](./sql/10-block130-route-path.sql) | 块 130 两条路由路径加 `/{accessLevel}`（预期 2 行，取自 `db/platform-support/`） |
| [sql/11-registration-block134.sql](./sql/11-registration-block134.sql) | 块 134 休眠注册 157 行（取自 `db/platform-support/`，与集成目录 initial-data 块 134 逐字相同） |
| [sql/98-registration-check.sql](./sql/98-registration-check.sql) | 注册后只读核对（2 / 2 / 2 / 12 / 56 / 11 / 72，全部休眠） |
| [test-activate.sql](./test-activate.sql) / [test-deactivate.sql](./test-deactivate.sql) | 块 134 激活 / 停用（预期 12 / 2 / 2 / 1） |
| [sql/90-rollback-block130-route-path.sql](./sql/90-rollback-block130-route-path.sql) | 回滚用：路径改回 `/{sourceSystem}` |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 发布后只读核对（23 项） |
| [scripts/backup.sh](./scripts/backup.sh) / [scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份与 SQL 包装：root 口令只经 `MYSQL_PWD`；容器按完整名称 `mor-test-mysql-1` |
| [acceptance/](./acceptance/) | `stage7_acceptance.py`（接口验收）、`stage7_cleanup.py`（清理）、`stage7_browser.py`（浏览器冒烟）、`pb7_common.py`、`PrivateHttpProbe.java`（私网探针） |

## 5. 执行前提

1. 用户授权阶段 7（镜像替换、`mor_platform` 写 SQL、存储侧建桶 / 改策略分别确认）。
2. 存储侧公开测试桶已按第 8 节建好（可在发布前任意时间做，不影响现网）。
3. 第 11 节第 1—3 项已决定。

## 6. 执行步骤（阶段 7，另需用户授权；主会话执行）

`$O` 为 `compose-candidate.yaml` 绝对路径，`$B` 为备份目录，`up` 指：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service...>
```

0. **协调：** 通知会发布 Platform / ops-bff / ops-web / card / CCT / client-console-bff 的会话（卡 BIN / 3DS code-mor-6b、多语言管理 code-mor-db、工作流、数据查询、按钮布局等）本窗口；期间有人发布了这六个服务中的任何一个，本包作废，按第 1 节在新基线上重建。
1. **复核基线：** 六个容器 `.Image` 等于 `compose-rollback.yaml`，mor-test 全部 healthy。
2. **备份：** `B=$(requirements/20261022/platform-support-文件公开桶/release/scripts/backup.sh)`；确认 `$B/00-precheck.out` 与第 3.1 节一致（记录值可增长，期望行必须全等）。
3. **DDL + 回填（旧 Platform 运行中，无影响）：** `apply_sql.sh $B platform-ddl`，核对末尾两段输出（10 列、9 个索引）；`apply_sql.sh $B platform-backfill`，`file_rows = v1_rows`、`files_without_v1 = 0`。
4. **块 134 休眠注册：** `apply_sql.sh $B block134-reg`；`apply_sql.sh $B reg-check` 全等。
5. **三个消费方（窗口外）：** `up … card-service client-master-service client-console-bff`；逐个核对 healthy、重启 0、启动日志 ERROR 0、`.Image` 等于候选；card / CCT 的 XXL 执行器约 30 秒后重新注册。
6. **ops-bff（窗口开始）：** `up … operations-console-bff`，核对同上。
7. **Platform：** `up … platform-support-service`，核对同上，日志无 `snapshot refresh failed`；随即 `apply_sql.sh $B platform-backfill` 补齐 DDL 之后旧 Platform 新建的文件（`files_without_v1 = 0`）。
8. **路径与激活：** `apply_sql.sh $B block130-path`（2 行）→ `apply_sql.sh $B activate`（12 / 2 / 2 / 1）→ 等 Gateway Snapshot 刷新（≤ 60 秒）。
9. **ops-web（窗口结束）：** `up … operations-console-web`；`apply_sql.sh $B postcheck` 全等。提醒用户刷新已打开的运营控制台标签页。
10. **验收：** `python3 -I requirements/20261022/platform-support-文件公开桶/release/acceptance/stage7_acceptance.py`（公开桶在 AWS 时加 `--public-instance S3_TEST --public-bucket <桶> --public-base-url <地址>`），第 9 节。
11. **清理：** `python3 -I …/acceptance/stage7_cleanup.py <run.json>`（是否带 `--purge-files` / `--remove-public-bucket` 按第 11 节第 3 项）。
12. **浏览器冒烟：** `python3 -I …/acceptance/stage7_browser.py <run.json>`，截图写 `acceptance/screens/`。
13. **记录与通知：** 填第 10 节，`delivery.md` 补环境操作记录；通知各会话新镜像 ID 与源码基线（`22ede435` + 本需求）；**本需求提交后把提交号发给 code-mor-db**（delivery.md 待办）；提醒今后的 ops-web 候选须同时带上本需求与仍未提交的 16 个文件。

## 7. 回滚

| 范围 | 做法 |
|---|---|
| 激活 | `apply_sql.sh $B deactivate`（12 / 2 / 2 / 1）：只关闭检查策略、桶对外地址编辑与文件记录入口 |
| ops-web | `compose.sh -f compose-rollback.yaml up … operations-console-web`。只回滚 Web 时存储管理页不可用（旧 Web 精确字段匹配），所以通常与下一行一起做 |
| Platform（+ ops-bff） | 前提：**先删除所有 PUBLIC 写入规则**（旧 Platform 按来源查路由，同一来源有两条会出错；验收清理会删掉 `pubbucket-qa/PUBLIC`）。顺序：deactivate → `rollback-path`（只在 ops-web 与 ops-bff 都回滚时）→ ops-web → **Platform** → ops-bff。旧 ops-bff 不能面对新 Platform（严格反序列化），所以 ops-bff 在 Platform 之后回滚或保持新版本（新 ops-bff 容忍旧 Platform，但写入规则编辑 / 删除会 404） |
| card / CCT / client-console-bff | 一般不必回滚（新 api 容忍新旧 Platform）。确需回滚，必须在 Platform 回滚之后 |
| DDL 结构 | 一般保留（旧代码兼容）。确需回退，按 `01-platform-ddl.sql` 文件头“回退”段，前提是没有 PUBLIC 桶 / 路由、没有 v2+ 版本、没有 `replacement_no > 0` 的上传；先恢复镜像 |
| 回滚后已有 v2+ 版本的文件 | 旧 Platform 只读 `t_file_object` 镜像（当前版本），能正常下载；进行中的替换上传会在完成时因单版本校验停住 |
| 块 134 注册 | 保持休眠；彻底移除按 `11-registration-block134.sql` 文件头的 ID 范围删除（子表先删） |

## 8. 需要用户 / 主会话在存储侧配合的操作

### 8.1 公开测试桶（二选一，建桶在阶段 7 授权后执行）

| | A. mor-test MinIO（推荐作为必做验收） | B. AWS 新加坡区（technique 第 6 节要求的真实 S3 实测） |
|---|---|---|
| 桶 | `mor-platform-public`（`stage7_acceptance.py` 缺省值） | 新建，如 `mor-test-platform-public-<后缀>`，区域 `ap-southeast-1` |
| 版本控制 / 加密 | `mc version enable`；`mc encrypt set sse-s3`（与 `local-environment/minio/init/init.sh` 现有两个桶一致） | Versioning Enabled；默认加密 **SSE-S3（AES256）**。不要用 SSE-KMS：匿名请求无法解密 KMS 对象 |
| 匿名只读 | 桶策略只允许 `s3:GetObject`，Principal `*`，Resource `arn:aws:s3:::<桶>/*`（同本机测试 `TempBucketMocker.publicReadable()`；`mc anonymous set-json`）。不要给 `ListBucket` | 关闭该桶的 Block Public Access（至少 BlockPublicPolicy / RestrictPublicBuckets），Object Ownership = Bucket owner enforced，桶策略同左 |
| Platform 账号 | 把新桶加进 MinIO `platform-files` 策略的两个 Resource 列表（桶级 `GetBucketLocation / ListBucket / ListBucketVersions / GetBucketVersioning`，对象级 `PutObject / GetObject / GetObjectVersion / DeleteObject / DeleteObjectVersion`）；**不改 init.sh 也行，但下次 init 会覆盖策略**，建议同时更新 init.sh（本机 / mor-test 共用，属于代码改动，另行确认） | S3_TEST 的 IAM 用户 / 角色对新桶授予同一组动作（见 8.2） |
| CORS | 服务端级已允许两个 Origin，无需操作 | 复制 `k7q2` 的 CORS（PUT / GET / HEAD，`content-type`、`if-none-match`，两个 Origin） |
| 对外地址 | `https://minio.mor-test.localhost:29400/mor-platform-public`（路径风格；容器内经网络别名、浏览器经 `*.localhost` 都能到） | `https://<桶>.s3.ap-southeast-1.amazonaws.com`（或日后 CDN 域名）；不带结尾 `/` |
| 验收参数 | 缺省 | `--public-instance S3_TEST --public-bucket <桶> --public-base-url <地址>` |

两种都只建桶，不经 Platform；登记、检查、启用由验收脚本经运营接口完成。可以先做 A，再按需对 B 补跑同一脚本。

### 8.2 `S3_TEST` 的 IAM 权限（按 A 报告与 `S3FileStorage` 调用核对）

本需求新增的 SDK 调用：`copyObject`（公开文件被外部改写或替换检查不通过时，把当前版本复制回最新，带 `sourceVersionId`）、`listObjectVersionsPaginator`、按 `versionId` 的 `headObject` / `getObject`。需要的动作（桶 ARN / 对象 ARN 分开）：

| 动作 | 用途 | 现状 |
|---|---|---|
| `s3:ListBucketVersions`（桶） | 完成替换时列出比基准新的版本；公开同步判断 | 第二段已在用（`listObjectVersions`），应已有，需确认 |
| `s3:GetBucketVersioning`、`s3:GetEncryptionConfiguration`（桶） | 桶检查 | 已在用 |
| `s3:GetObjectVersion`（对象） | 按版本读取 / 复核、**复制源版本读取** | 已在用（下载固定 versionId），需确认 |
| `s3:PutObject`（对象） | 浏览器直传（预签名）、**CopyObject 目标写入**、桶检查探测对象 | 已在用 |
| `s3:GetObject`（对象） | 读最新版本、桶检查读回 | 已在用 |
| `s3:DeleteObject`、`s3:DeleteObjectVersion`（对象） | 桶检查删除探测对象（含其版本） | 已在用 |
| `kms:Decrypt`、`kms:GenerateDataKey` | 只在桶使用 SSE-KMS 时：复制需要解密源、加密目标 | `k7q2` 的加密方式未知（Platform 只记录检查 OK）；若为 SSE-KMS 需确认 |

`k7q2` 是私有桶，本次只做重查与回归上传（不做替换），现有权限即可通过；**替换与公开同步只在 8.1 的公开桶上验收**，B 方案的新桶需要上表全部权限。

### 8.3 其他

- 建议给两个公开 / 私有桶都配置“非当前版本 30 天后过期”的生命周期规则（technique 第 7 节风险；基础设施配置，不在本期代码范围，可后做）。

## 9. mor-test 验收

`acceptance/stage7_acceptance.py`，运行记录写 `~/Code-MoR-backups/pubbucket-stage7/run-*.json`（0600）。内部接口经接入 `mor-test-backend` 的一次性探针容器（Platform 当前镜像，`--rm`，只读挂载）带 `X-Caller-Service` 调用；探针机制在阶段 6 用只读 GET 试过（旧 Platform 返回 200，无新字段）。

| 场景 | 检查 |
|---|---|
| 运行前提 | 六个容器为候选镜像且 healthy |
| 私有桶（AC-02） | `MINIO/mor-platform-files`、`S3_TEST/…-k7q2` 重查仍 OK；列表回填为 PRIVATE、无对外地址 |
| 桶登记校验（AC-01） | 私有桶带地址、公开桶缺地址、`http://`、结尾 `/` 均被拒 |
| 公开桶（AC-02） | 以错误地址（指向私有桶）登记 → 检查 `PUBLIC_READ_FAILED` → 启用被拒 → 草稿改地址 → 检查 OK → 启用 → 启用后改地址被拒 |
| 写入规则（AC-03） | 存量三条不变；公开规则指向私有桶被拒；新建 `pubbucket-qa/PUBLIC`；`PUT /routes/{来源}/{级别}` 编辑（验证块 130 新路径）；`*/PRIVATE` 不能删；无公开规则的来源声明 PUBLIC 返回 503（不回落） |
| 公开文件（AC-04、05、10—12） | 上传 PNG → 检查通过后 `publicUrl` 与 `?v=1` → **不带凭据读取**为 v1；替换时改级别被拒；两个线程同时开始替换：一个 201、一个 409（后到者 `FILE_STATE_CONFLICT`，同时抢锁时 `CONCURRENT_REQUEST`）；直传 v2（不带 If-None-Match）→ 完成 versionNo 2 → 检查通过后同一 `publicUrl` 读到 v2、`?v=2` |
| 私有文件（AC-04、13） | 未声明级别 → 默认 `*/PRIVATE` 写 MinIO，无 `publicUrl`；替换直传后、完成前下载仍是 v1；候选检查中下载仍是 v1（扫描过快构造不出时如实记录“未能验证”，由本机测试覆盖）；通过后为 v2 |
| 文件记录（AC-15） | 按来源 / 访问级别 / 有替换进行中筛选；跨度 > 31 天被拒；详情 v2 CURRENT、v1 SUPERSEDED、检查结果覆盖两版、上传记录 ORIGINAL + REPLACEMENT；下载 v1 内容一致并写 `FILE_DOWNLOAD_AUTHORIZE` 审计、审计属性无地址；不存在的版本被拒；合成只读人员能看详情、下载 403、导航有文件记录无下载按钮 |
| 检查策略（AC-07、14） | 列表 `supportedMimeTypes` 含 csv、存量策略 ACTIVE；不可识别类型被拒；新建 → 改草稿 → 激活 → 新修订 / 有草稿再建 409 → 废弃 → 新修订激活（r1 → RETIRED）→ 自动检查要求；被写入规则引用时停用 400 → 删规则 → 停用成功 |
| 回归（AC-08） | `client-console-bff` 身份上传入驻材料仍写 `S3_TEST/k7q2`、下载一致；O10 下载已有材料 SHA-256 一致；工作流附件上传、已有附件下载一致；BIN CSV 上传按 `CARD_BIN_CSV` 写 `mor-platform-files`（只上传不建版本） |
| 运行 | 五个 Java 服务验收期间 ERROR 0；Platform 无快照刷新失败；六个容器 healthy、重启 0 |

**清理（`stage7_cleanup.py`）：** 缺省删除本次写入规则（接口）、合成检查策略（接口停用后按行删除，前提是无任何引用）、合成人员与角色；保留公开测试桶登记、本次上传的文件与存储对象、审计。`--purge-files` 按运行记录删除本次文件的数据库行并列出需手工删除的存储对象；`--remove-public-bucket` 在其后删除公开桶登记行。最后核对写入规则恢复为存量三条。

**浏览器（`stage7_browser.py`，只读）：** 存储管理四个页签（实例、桶的私有 / 公开标签与对外地址、写入规则矩阵、检查策略修订）、文件记录列表与验收公开文件详情（版本历史、检查结果、上传记录、公开地址）、审计页；控制台错误 0、4xx/5xx 0。

本机已覆盖、mor-test 不重复的：替换过期与多余 PUT 作废、`FILE_PUBLIC_SYNC` 恢复复制（需要外部改写或检查不通过的公开替换）、策略冻结 AC-14 的完整时序（`ScanPolicyRevisionFreezeTest`）、权限矩阵细项。

## 10. 执行记录（阶段 7，2026-10-23，主会话执行）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 05:15 前后 | 0 协调 | 通知 code-mor-6b / 78 / ae / bf / db，均回复无冲突并暂停这 6 个服务的发布 |
| 05:21 | 1 复核基线 | 6 个容器镜像等于 `compose-rollback.yaml`，全部 healthy |
| 05:21 | 2 备份 | `/Users/kenny/Code-MoR-backups/20261023T052124Z-pubbucket-mor-test`（脚本备份 + `mor_platform-full.sql` 全库）；预检与阶段 6 相比只有文件 / 上传 / 扫描结果各多 1 行（新上传），其余全等 |
| 05:22 | 8.1 A 公开桶 | mor-test MinIO 建 `mor-platform-public`：版本控制、SSE-S3、匿名只读 `s3:GetObject`（匿名列桶 403）；`platform-files` 策略加入该桶（**未改 `local-environment/minio/init/init.sh`，下次执行 minio-init 会覆盖该策略**） |
| 05:22:36 | 3 DDL + 回填 | 10 列、9 个索引；回填 70 行 v1（全部 CURRENT / AVAILABLE） |
| 05:22 | 4 块 134 休眠注册 | reg-check 8/8 |
| 05:22:49—05:23:50 | 5 三个消费方 | card / client-master / client-console-bff 候选镜像 healthy、重启 0、ERROR 0 |
| 05:23:57 | 6 ops-bff（窗口开始） | healthy |
| 05:24:23—05:25:03 | 7 Platform | healthy、无快照刷新失败；补跑回填 `files_without_v1=0` |
| 05:25:08 | 8 块 130 改路径 / 块 134 激活 | 2 行；12 / 2 / 2 / 1；Gateway Snapshot 05:25:13 刷新（543 条路由） |
| 05:25:27 | 9 ops-web（窗口结束） | healthy；**实际窗口约 1.5 分钟**；postcheck 23/23 |
| 05:25:42 | 10 验收 | 87/89。两项失败均非缺陷：① 工作流附件"已有实例"查询写错位置（附件在 `t_workflow_action_run.output_json.files`），已修正脚本并手工补验两个已有附件下载 200、SHA-256 一致；② Platform ERROR 1 条为验收自身"无公开路由 → 503"用例的预期日志。AC-13"检查中读旧版本"在 mor-test 扫描过快未能构造，由本机测试覆盖 |
| 05:28 | 12 浏览器冒烟 | 6/8 自动判定；2 项为脚本选择器与实际文案不一致（默认来源显示"* （默认）"、表头含"私有 / 公开"标签），截图 `acceptance/screens/s03-routes.png`、`s04-scan-policies.png` 人工核对页面正确；控制台错误 0、失败请求 0。发现检查策略表"状态"列在 1512 宽度下被右侧详情遮挡（界面小问题，遗留） |
| 05:28 | 11 清理 | `--purge-files`：删除本次 5 个文件的数据库行、合成策略、合成人员与角色、`pubbucket-qa/PUBLIC` 路由；写入规则恢复为存量 3 条；保留公开桶 `MINIO/mor-platform-public` 登记（供多语言管理复用）。MinIO 上本次 4 个对象（6 个版本）已手工删除；入驻回归上传写在 AWS `k7q2` 的 1 个测试对象无法由主会话删除（无 AWS 凭据），数据库行已删，对象可由用户在 AWS 控制台删除或保留 |
| 05:30 | 收尾 | 6 个服务 healthy、重启 0 |

**最终镜像：** card `810b6b72…`、client-master `2e495b40…`、client-console-bff `b8ecaaea…`、ops-bff `06d850d1…`、platform `68ba631f…`、ops-web `f8b5dc32…`（tag `pubbucket-20261023a`，源 = main `22ede435` + 本需求，ops-web 另含 16 个未提交文件）。

## 11. 风险与需要主会话 / 用户决定的事项

1. **公开测试桶用 MinIO 还是 AWS（第 8.1 节）。** 建议先 A（mor-test MinIO，可控、零费用，覆盖全部流程，但走的是 `MinioFileStorage` 适配器），再按 technique 第 6 节对 B（AWS）补跑一次以实测 `S3FileStorage` 的 CopyObject 与真实匿名读取。A 需要改 MinIO `platform-files` 策略；是否同步改 `local-environment/minio/init/init.sh`（代码改动）需决定。
2. **验收会顺带上线 client-master / client-console-bff 的已提交合同库变化（第 1.2 节表格）。** 都是两者不调用的类与常量，不需要额外步骤；请确认一并发布。
3. **验收数据保留范围：** 公开测试桶登记是否保留为 mor-test 常驻配置（code-mor-db 多语言管理随后会接入公开上传，保留可直接复用）；本次合成文件（公开 PNG 可被匿名读取、内容为 1×1 合成图）与回归上传是否用 `--purge-files` 删除数据库行，存储对象是否手工删除。
4. **块 134 注册提前于窗口执行（第 2 节③）：** 与 B 草稿写的“前置 block130-route-path”不同；块 134 与 130411 / 130412 无行级依赖，提前可缩短窗口。如需严格按草稿顺序，把第 4 步挪到第 8 步之前，窗口会多约 10 秒。
5. **提交时的 PDManer 模型冲突（第 1.1 节）：** main `32dbdd18` 已把 `platform-support.pdma.json` 换回手工维护版本，集成目录的模型以被覆盖的生成器版本为底；提交本需求时须以 `32dbdd18` 为底手工加入本需求 6 个实体与 `file_document` 图（`32dbdd18` 提交说明：“PDManer 今后直接手改，不再使用生成器”），不能直接覆盖。
6. **ops-web 仍依赖 16 个未提交文件：** 本候选与现网一样带着按钮布局 6 个与数据查询体验 10 个文件；本需求提交时**不应**把数据查询 10 个文件算进本需求（它们是 code-mor-55 的改动），提交前需与其 Owner 协调先后。主工作区 ops-web 另有其他会话更晚的未提交改动（工作流、导航等），本候选不含，也不在现网。
7. **`S3_TEST` 的 IAM 与加密方式未核实（第 8.2 节）：** 只能由用户在 AWS 控制台确认；私有桶回归不依赖新增权限，公开桶验收依赖。
8. **残余风险（A 报告 6.2）：** 公开桶被外部改写且没有待执行的同步任务时，开始替换会一直 409，需要人工复制恢复；CDN 缓存会延长替换后的“检查空窗”。mor-test 无 CDN，不影响验收。
