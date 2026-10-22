# platform-support：文件多存储并存（第一段）· mor-test 发布包

阶段 6（2026-10-22，核对时间 04:15–04:40Z）。本阶段只做了只读核对、干净 worktree 构建镜像和发布包准备，**没有改 mor-test 的任何容器、数据、Vault、Gateway 注册或 compose 主文件**，也没有调用任何写接口。阶段 7 由主会话按第 5 节执行，结果写第 8 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：**
- **镜像：** mor-test 的 platform-support、operations-console-bff、operations-console-web 三个容器换成新镜像。
- **环境变量：** Platform 删除 8 个旧存储键，新增 `PLATFORM_SUPPORT_FILES_ENABLED=true`。
- **数据库：**
  - `mor_vault` 登记 1 个数据类型和 2 条规则；
  - `mor_platform` 执行增量 DDL：3 张新表，2 张表加列、回填、改 NOT NULL，放宽 5 列；
  - `mor_platform` 注册块 130（152 行）并激活（12 / 2 / 2 / 1）。
- **存储登记：** 经运营「存储管理」登记现有 MinIO：实例 `MINIO`、桶 `mor-platform-files`、默认路由 `*`。
- **之后：** 验收，清理合成数据。

其余容器不动。

## 1. 源码与镜像

| 项 | 内容 |
|---|---|
| 发布 worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-filestore-rel`（detached `281a217d`） |
| 基线证明 | 未加补丁时构建三件制品，与运行容器逐项比较，**全部 0 差异**：<br>· **platform-support：** 2,160 项；<br>· **ops-bff：** 2,306 项；<br>· **Java 口径：** `BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目比较，第三方 JAR 按整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引；<br>· **ops-web：** `dist/` 162 个文件逐字节一致，`nginx.conf` 一致。<br>结论：`281a217d` 就是现网三件的源码，没有他人未提交的内容在线上 |
| 补丁 | 两个工作目录相对 `281a217d` 的 `git diff`（含新增与删除，`requirements/` 除外）：<br>· **A：** `Code-MoR-filestore`，196 个路径；<br>· **B：** `Code-MoR-filestore-ops`，22 个路径，排除 api 副本；B 复制的 api 文件与 A 逐字节相同。<br>两份补丁无重叠，`git apply` 无冲突 |
| 与主工作区核对 | 发布树 218 个路径中 189 个与主工作区逐字节相同，29 个不同，结论：**主工作区版本 = 发布树版本 + 他人 hunk**，差异里没有本需求的内容。<br>**他人来源**（主工作区三方合并，未提交）：<br>· **数据查询：** HEAD `9a209e6f`，父 `281a217d`，未推送、未上 mor-test，块 131；<br>· **工作流：** `Code-MoR-workflow` 的未提交改动，块 127；<br>· **channel-connector 第二段：** `Code-MoR-ccu2` 的未提交改动，块 129，移除接入方页面等。<br>**核对方法：** 对每个文件取“发布树→主工作区”的增删行，与三个来源各自的增删行比对。<br>**结果：**<br>· **OK 13 个：** 差异行全部能在来源中找到；<br>· **REV 16 个：** 有合并产物，已逐条人工确认只是他人内容与本需求同一行或相邻行的合并：<br>&nbsp;&nbsp;– 数量断言：页面 59→64、表 63→73、`table-ownership` 49→62、Schema 头 63 张表；<br>&nbsp;&nbsp;– 合并后的列表：BFF 严格边界路径加 `/workflow-admin`、`/workflow`，`WorkflowModels`、`OperationsCatalog` 末行加逗号，`LocalMockEnvironmentInitializer` 加 RabbitMQ / 数据查询键；<br>&nbsp;&nbsp;– `policy.local.json`：数据查询在本需求两行后追加，产生逗号；<br>&nbsp;&nbsp;– `PlatformClientConfiguration`：数据查询把读取超时参数改为 `platformClient(…)`，并加了 3 个工作流 Bean；<br>&nbsp;&nbsp;– Platform `application.yml` 的 `compensation.enabled` 合并行（第 2 节②）；<br>&nbsp;&nbsp;– PDManer 模型：工作流重生成后，15 行连线 ID 只是换了位置，增删集合完全相同；<br>&nbsp;&nbsp;– `PLATFORM_SUPPORT_DATABASE.md`：主工作区把 `ecab136c` 已有的“渠道协议迁出”说明重复了一遍（合并产物，只在文档里）。<br>逐文件明细见 [main-tree-attribution.txt](./main-tree-attribution.txt)。块 130 注册段在主工作区逐字存在；DDL 增量文件与主工作区逐字节相同 |
| 候选不含他人内容 | **Platform JAR：**<br>· 与运行版的差异（下表）只有本需求的类和资源；<br>· `console-resource-capabilities.json` 从运行版 49 个组件变为 50 个，只多 `OPERATIONS_FILE_STORAGE`，其余 49 个（含动作）逐项相同；<br>· 不含主工作区新增的 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`、4 个 `OPERATIONS_WORKFLOW_*`；<br>· 仍保留第二段在主工作区删除的 `OPERATIONS_EXTERNAL_INTEGRATIONS`；<br>· 没有 workflow / data-query 的模块、类或 JSqlParser；`application.yml` 没有 `workflow:`、`data-query:`、`rabbitmq:` 段和 `/workflow`、`/data-query` 日志前缀，`compensation.enabled` 是本需求原样。<br>**ops-bff JAR：** 没有 Workflow / DataQuery 类和对应前缀。<br>**ops-web：** 源码相对 `281a217d` 只改了 B 的 7 处路径（含 `src/fileStorage/` 4 个文件）。`dist/` 中搜不到上述 6 个他人组件键和 `/workflow-admin`、`/data-query`。第二段改动的 `channel/`、`externalApi/`、`menu.ts`、`App.tsx` 与各渠道页都是 `281a217d` 原样，`OPERATIONS_EXTERNAL_INTEGRATIONS` 仍在。<br>**`sql/10-registration-block130.sql`：** 取自发布树，块 130 之后没有块 129；文件中没有 127 / 129 / 131 号段 ID，只在块头注释里提到这些保留号段 |
| 构建 | Java：`./mvnw -B -o -DskipTests -pl <两个 app> -am clean package`（JDK 25，`~/.m3`），**未 install**；镜像用 `test-environment/java/Dockerfile`，`health-probe/` 从运行容器 `docker cp` 读出（两个服务逐字节相同）。<br>Web：`npm ci` → `npm test`（1252/1252）→ `npm run build` → `docker build`（`operations-console-web/Dockerfile`）。<br>没有改 `:development` tag 和 `test-environment/artifacts` |
| 镜像核对 | 镜像内 `/app/application.jar` 与构建产物 SHA-256 相同；Web 镜像内 `dist/`、`nginx.conf` 与构建产物逐字节相同。三个镜像的 Entrypoint、Cmd、User、WorkingDir、Env 与对应运行镜像相同 |
| 测试证据 | 阶段 5：Platform auto 791/791、BFF 2057/2057、Web 1252/1252（见 [A 报告](../reports/A-platform-report.md)、[B 报告](../reports/B-operations-report.md)、[delivery.md](../delivery.md)）。<br>发布树补跑：Web 1252/1252 与构建；BFF `FileStorageBrowserAutoTest` 37、`OperationsHttpBodyNamingAutoTest` 50、`OperationsConsoleBffApplicationAutoTest` 8，共 95/95。Platform 需库测试未在发布树重跑：代码与阶段 5 逐字节相同 |

| 服务 | 运行（回滚目标） | 候选（tag `filestore-20261022`） | 与运行版逐类比较的差异 |
|---|---|---|---|
| platform-support-service | `sha256:2d40c1ba7f9aa1cc0f4da838b23e880f414201574cba36fc4d4dc1587d7ba330`（ccu-20261021，JAR `aa66765d3bf1139c`） | `sha256:04c12879527bc885dd673d3f0417f3807946f46d62e56ab5f5663af97ea77901`（JAR `415a3639c046975b`） | **新增 117 项：**<br>· 存储管理 Controller / Converter、内部文件 Controller / Converter、`FileStorageConfiguration`；<br>· api `filestorage/**`、`files/**` 与 5 个枚举；<br>· core 15 个 Processor 及请求 / 响应；<br>· file-document 的 3 个 DataObject / Mapper（含 XML）/ Repository、`FileStorageManagementService(Impl)`、`FileStorageRegistry`、`StorageCredentialVault`、`FileObjectStorage`、`ProbeObject`、快照刷新任务、`FileStorageProperties`。<br>**删除 4 项：** `Minio/S3StorageConfiguration`、`Minio/S3StorageProperties`。<br>**改动 50 项（含内部类）：**<br>· `application.yml`、`OnboardingFileConfiguration`、`PersistenceConfiguration`；<br>· `console-resource-capabilities.json`（+`OPERATIONS_FILE_STORAGE`）、`OperationsAuditFact`；<br>· 入驻 / 审核下载 / 扫描 Processor 与 Handler；<br>· `FileUpload`、两个 Mapper（含 XML）、Repository、Service；<br>· `Minio/S3FileStorage`、`StorageTrust`、`CompletedUploadVerifier`、`FormatFileScanner`。<br>第三方 JAR 无增删 |
| operations-console-bff | `sha256:4839c249e59c86d1f507b75ed25858ad7dbeabcfd816c1885fcb62d200ff206b`（ccu-20261021，JAR `274fed6fb50ed779`） | `sha256:e81b84242c36510722a2a0fbc21f73842a350bf1b7bcc464fc59ba7bedaa5f5c`（JAR `97fc918028ea1edd`） | 新增 `FileStorageAdminController`、`PlatformFileStorageClient(Impl)`，以及 platform-support-api 的 27 个类。改动 `ConfigurationManagementRequestConfiguration`（含 2 个内部类）、`PlatformClientConfiguration`（含 1 个内部类）、`application.yml`（metadata-only 前缀 `/storage-admin`） |
| operations-console-web | `sha256:d1e5619f23f2bce4b038336966ce921cd886cab0305dc791d44048cd654d8dfa`（capability-ui-20261022d，即 `281a217d`） | `sha256:8c224936a1b73d8c140108cd6af8360f2e649054b85c6d318c9d38003a7a6350`（入口 `assets/index-BtjpH_Wt.js`） | 163 个文件，多出「存储管理」页。其中 67 个与运行版同名同内容，其余因 Vite 内容哈希级联改名 |

## 2. 兼容性（决定执行顺序）

**① 增量 DDL 对旧 Platform 并不完全安全。** 现状见第 3 节：`t_file_upload` / `t_file_object` 各 55 行，全部 `MINIO / mor-platform-files`、COMPLETED / AVAILABLE；最后一次上传在 2026-10-18；没有进行中的上传和补偿任务；MySQL 是 `STRICT_TRANS_TABLES`。

| DDL 段 | 对旧 Platform |
|---|---|
| 1 建 3 表、2 加可空列、3 回填、5 放宽 5 列 | 无影响：旧 Mapper 的 INSERT / SELECT 都是显式列 |
| 4 `source_system`、`reason_code` 改 NOT NULL（无默认值） | **旧 Platform 写 `t_file_upload`（入驻开始上传）和 `t_file_object`（完成上传）会被拒绝**；读取不受影响 |

所以 DDL 放在 Platform 替换的**前一刻**执行，窗口约等于 `up --wait` 的时长（60–90 秒）。

**不拆成两段的理由：** 拆成“加列可空 + 回填 → 换 Platform → 再回填 + 收紧 NOT NULL”能做到零窗口，但会偏离仓库里的单一 DDL 文件。mor-test 四天没有上传，这几十秒的窗口可以接受。若主会话要求零窗口，可以把 01 文件第 4 段挪到 Platform 替换之后执行，执行前补一次第 3 段回填（见 [90](./sql/90-rollback-relax-new-columns.sql) 文件头的“再次前滚”写法）。

**② 新 Platform 的启动条件。**

| 条件 | 处理 |
|---|---|
| 旧键残留即拒绝启动：`files.onboarding.storage-provider`、`storage-container-ref`、`scan-policy-code`、`upload-seconds`、`minio.*`、`s3.*`（按宽松绑定，环境变量也算） | 候选覆盖文件对 8 个 `FILES_ONBOARDING_STORAGE_*` / `SCAN_POLICY_CODE` / `MINIO_*` 键写 `KEY: !reset null`。已用 Compose v5.0.2 验证：`!reset` 删除单个环境变量键；对不存在的键是空操作（`compose-applications.patch` 应用前后都适用） |
| `files.onboarding.enabled=true`（现有 `FILES_ONBOARDING_ENABLED=true`）要求 `files.enabled=true` | 新增 `PLATFORM_SUPPORT_FILES_ENABLED: "true"` |
| `files.enabled` 要求 Redis 协调与补偿执行器 | 现有 `OPERATIONS_AUTHENTICATION_ENABLED=true` 打开 Redis 协调，`COMPENSATION_ENABLED=true` 打开补偿，均满足 |
| `compensation.enabled` 的解析（补偿扫描不能被意外关掉） | **候选写法：** `application.yml` 为 `${PLATFORM_SUPPORT_COMPENSATION_ENABLED:${files.enabled:false}}`，`files.enabled` 为 `${PLATFORM_SUPPORT_FILES_ENABLED:false}`。<br>**容器环境变量优先：** 候选环境保留 `COMPENSATION_ENABLED=true`（第 2 节②的逐键比较中不变）。Spring Boot 把它按宽松绑定映射为 `compensation.enabled`，系统环境变量的优先级高于 `application.yml`，所以 YAML 默认值不参与；读取方 `@ConditionalOnProperty(prefix="compensation", name="enabled")` 和 `CompensationEventProperties` 都看到 true。<br>**即使去掉这个变量：** YAML 默认值经 `PLATFORM_SUPPORT_FILES_ENABLED=true` 仍得到 true。两条路径都开启补偿。<br>**对照：** 运行版写法 `${…:${files.onboarding.enabled:false}}` 同样被 `COMPENSATION_ENABLED` 覆盖。主工作区合并后的工作流写法 `${…:${PLATFORM_SUPPORT_FILES_ENABLED:${operations.authentication.enabled:false}}}` 不在候选里，在 mor-test 上结果也是 true。<br>**阶段 7 核对：** 第 6 步换 Platform 后，看补偿扫描执行器重新注册，日志里没有缺少补偿 Registrar 的启动错误 |
| 主 compose 有工作流会话未发布的 `PLATFORM_SUPPORT_RABBITMQ_*`（未提交） | 一并 `!reset`，避免把他人未发布的配置带上线 |
| Vault 客户端 | 沿用 `vault.client.caller-service=platform-support-service`，经 Nacos 发现，与通知 / Webhook 相同；无新变量 |

`compose.sh -f <候选> config` 渲染结果与运行容器逐键比较（只比较值是否相等，不输出值）：

| 服务 | 结果 |
|---|---|
| platform-support | 渲染 46 键、运行 53 键，差异恰好是删除的 8 个旧键和新增的 `PLATFORM_SUPPORT_FILES_ENABLED`，其余键值全部相同 |
| ops-bff | 14 = 14，全部相同 |
| ops-web | 无环境变量 |

回滚文件渲染后与运行容器 53 = 53，全部相同。它显式写回 8 个旧键：主 compose 应用 patch 删掉旧键后，回滚仍然成立。

**③ 新 Platform 在没有任何实例 / 桶 / 路由时的行为。**

- **启动：** 启动和就绪不读存储目录。快照刷新任务在启动时（`initialDelay=0`）和之后每 30 秒读 3 张新表，所以 **DDL 必须先于 Platform**；否则只记 WARN（`File storage snapshot refresh failed`），服务仍可用。
- **登记完成前：**
  - 入驻开始上传：`forWrite("platform-support")` 找不到路由，返回 **503**；
  - 审核下载 / 入驻读取 / 扫描已有文件：记录坐标 `MINIO / mor-platform-files` 尚未登记，返回 **503**，不发起存储调用。
- **登记完成后：** 实例代码必须是 `MINIO`，桶必须是 `mor-platform-files`（第 3 节已核对 55 条记录全部是这对坐标），否则旧文件读不到。
- **不可用窗口：** 从 Platform 替换开始，到第 5 节第 9 步登记完成，约 3–5 分钟。期间入驻上传和审核下载都不可用。

**④ Vault。**

- **策略来源：** mor-test Vault 是 Go 服务，策略只从 `mor_vault` 读，没有策略文件；按 `VAULT_POLICY_REFRESH_INTERVAL` 全量刷新，compose 未设置，默认 10 秒，不需重启。
- **DML 核对：** `db/vault/dml.sql` 用的 ID 9100000000000740—742 在三张表中都未占用，也没有 `FILE_STORAGE_CREDENTIAL_V1`。策略版本当前为 18，DML 取 MAX+1，即 19。
- **ID 冲突：** 数据查询会话的 Vault DML 用 750—752，不冲突。
- **执行顺序：** DML 先于 Platform 替换执行，否则登记实例时 Protect 返回 503。

**⑤ 新旧组合。**

| 组合 | 结果 |
|---|---|
| 旧 ops-bff + 新 Platform | 正常：新 Platform 保留全部旧接口，只新增 `/storage-admin/v1`、`/internal/files/v1` |
| 新 ops-bff + 旧 Platform | 正常：存储管理路由在块 130 激活前是 DISABLED，不会被调用 |
| 新 ops-web + 旧 BFF | 正常：菜单在激活前不下发 |
| 块 130 休眠注册早于 Platform 替换 | 安全：旧 Platform 对未知组件只在读取时忽略，DISABLED 资源和路由不进导航和 Snapshot。币种块 125 有同样的先例 |
| 激活 | 必须在新 Platform、新 ops-bff 都起来之后 |

**⑥ MinIO 实例登记参数。** 新模型下全部是实例字段，不再保留任何环境变量：

| 参数 | 取值 |
|---|---|
| 实例 | `instanceCode=MINIO`、`storageType=MINIO` |
| 地址与区域 | `endpoint=https://minio.mor-test.localhost:29400`（容器内由网络别名解析，也是浏览器下载地址）、`region=us-east-1` |
| 访问方式 | `pathStyleAccess=true`；超时取默认 2000 / 10000 ms |
| 证书 | `trustedCertificatePem` 为 `test-environment/private/test-ca.crt` 的**文本**，替代原 `FILES_ONBOARDING_MINIO_TRUSTED_CERTIFICATE` 文件路径；实例只信任这一张 CA |
| 凭据 | `credentialMode=STATIC`；`accessKey` / `secretKey` 取 `test-environment/.env.test` 的 `MOR_MINIO_PLATFORM_ACCESS_KEY` / `SECRET_KEY`（与原 compose 同一账号）。脚本只在内存中使用，不回显、不写文件 |
| 默认路由 | `* → MINIO / mor-platform-files`，扫描策略 `CLIENT_ONBOARDING_FORMAT`（mor-test 有 1 条 ACTIVE），`maxUploadSeconds=900`（旧 `upload-seconds` 默认值） |
| 账号权限 | `local-environment/minio/init/init.sh` 给 Platform 账号的策略已覆盖桶检查所需动作：`GetBucketVersioning`、`PutObject`、`GetObject`、`DeleteObject(Version)`。桶已开版本控制和 SSE-S3 |

**⑦ 内部文件接口在 mor-test 的可验范围。** mor-test Platform 没有设置 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE`，即 OFF。新接口在 OFF 下一律 401（设计如此），所以 mor-test 只能验证“无身份拒绝”。上传→完成→查询→下载的正向链路由本机 `InternalFileApiTest` 覆盖，详见第 9 节待决定事项 2。

## 3. 只读核对（阶段 6 已做）

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22T04:30:15Z 执行，27 项全部等于期望，输出见 [`sql/00-precheck-20261022.out`](./sql/00-precheck-20261022.out)。

| 核对项 | 结果 |
|---|---|
| 存储三表、`source_system` / `reason_code` 列 | 不存在；`uploader_id` / `upload_id` 定位列存在；五个将放宽的列仍 NOT NULL |
| `mor_platform` 表数 | 47 |
| 文件数据 | `t_file_upload` 55、`t_file_object` 55、`t_file_owner_ref` 55。全部 `MINIO / mor-platform-files`；上传全部 COMPLETED，文件全部 AVAILABLE / ACTIVE；补偿任务 0；52 个文件被入驻审核证据引用 |
| 块 130 | 6 张表全空；`FILE_STORAGE_*` 权限、`OPERATIONS_FILE_STORAGE*` 资源、`/api/operations/storage-admin/` 路径都不存在 |
| 已用最大路由块号 | 126（127—129、131 尚未上 mor-test） |
| 父菜单与角色 | 平台配置 562 ACTIVE；`PLATFORM_ADMIN` 版本 40 |
| Vault | 数据类型、规则、策略变更 ID 740—742 均未占用；策略版本 18 |
| 运行镜像与环境 | 三个容器 `.Image` 等于 [compose-rollback.yaml](./compose-rollback.yaml)，healthy、重启 0；Platform 环境变量见第 2 节② |

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件，只含三个服务。`compose.sh -f <本文件> config --quiet` 已通过；环境变量逐键核对见第 2 节② |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 执行前只读核对 |
| [sql/02-vault-file-storage-credential.sql](./sql/02-vault-file-storage-credential.sql) | `mor_vault` DML（`db/vault/dml.sql` 逐字副本，自带 USE 与事务） |
| [sql/10-registration-block130.sql](./sql/10-registration-block130.sql) | 块 130 休眠注册 152 行（初始化 DML 末尾「Operations file storage console」块逐字副本，自带事务） |
| [sql/01-platform-file-storage.sql](./sql/01-platform-file-storage.sql) | `mor_platform` 增量 DDL（`changes/2026-10-22-file-storage-management.sql` 逐字副本） |
| [test-activate.sql](./test-activate.sql) / [test-deactivate.sql](./test-deactivate.sql) | 激活 / 停用（12 / 2 / 2 / 1） |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) | 激活后只读核对 |
| [sql/98-storage-registration-check.sql](./sql/98-storage-registration-check.sql) | 存储登记后只读核对 |
| [sql/90-rollback-relax-new-columns.sql](./sql/90-rollback-relax-new-columns.sql) | 镜像回滚配套：把 4 个新列放宽为可空，旧 Platform 才能写入 |
| [scripts/backup.sh](./scripts/backup.sh) / [scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份与 SQL 执行包装。root 口令只经 `MYSQL_PWD`，不回显；输出进备份目录 |
| [acceptance/](./acceptance/) | `stage7_register_storage.py`（登记）、`stage7_acceptance.py`（验收；`--legacy-only` 为替换前基线）、`stage7_cleanup.py`、`fs7_common.py`、`InternalFileProbe.java` |
| [main-tree-attribution.txt](./main-tree-attribution.txt) | 发布树与主工作区 29 个不同文件的逐文件归属（数据查询 / 工作流 / channel-connector 第二段） |
| [compose-applications.patch](./compose-applications.patch) | 主工作区 `test-environment/compose.applications.yaml` 的补丁：删 8 个旧键，加 `PLATFORM_SUPPORT_FILES_ENABLED`。**只准备，未应用**；已对主工作区当前文件做过 `git apply --check` |

执行包装：`scripts/apply_sql.sh <备份目录> precheck|vault|platform-reg|platform-ddl|platform-act|platform-deact|postcheck|storage-check|rollback-relax`。

## 5. 执行步骤（阶段 7，主会话执行）

`$O` 为本目录 `compose-candidate.yaml` 的绝对路径，`$B` 为备份目录。`up` 命令统一写作：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service>
```

0. **协调：** SendMessage 给以下会话，确认对方此刻不在部署，约一个窗口。
   - 数据查询会话：块 131，main `9a209e6f`，发布包正在准备，同样替换这三个服务；
   - 工作流会话（code-mor-ae）：块 127，主 compose 有未提交的 RabbitMQ 键；
   - channel-connector 第二段（code-mor-0e）：块 129，已合入主工作区但未发布；
   - 最近发布过这三个服务的会话（channel-connector code-mor-0e、对账 code-mor-93、商务 code-mor-98）。

   **失效条件：** 期间有人先发布了这三个服务中的任何一个，本包作废，在新基线上重建并重做第 1 节的证明。
1. **复核基线：** 三个容器 `.Image` 必须等于 [compose-rollback.yaml](./compose-rollback.yaml)，mor-test 全部 healthy，有任何不同就停。
2. **备份：** `B=$(release/scripts/backup.sh)`，得到 `~/Code-MoR-backups/<UTC>-filestore-mor-test/`。
   - 打开 `$B/00-precheck.out`，确认与第 3 节一致；
   - 可选：`python3 -I release/acceptance/stage7_acceptance.py --legacy-only`，记录旧 Platform 下一份已有材料能下载、字节一致，作为对照。
3. **Vault：** `apply_sql.sh $B vault`，影响 1 / 2 / 1 行。等 10 秒刷新。
4. **块 130 休眠注册：** `apply_sql.sh $B platform-reg`，152 行，全部 DISABLED / REVOKED，对现网无影响。
5. **ops-bff → ops-web：** 依次 `up … operations-console-bff`、`up … operations-console-web`。每次核对 healthy、重启 0、启动日志 ERROR 0、`.Image` 等于候选。存储菜单此时还不可见。
6. **DDL → Platform（不可用窗口开始）：**
   - 先 `apply_sql.sh $B platform-ddl`，紧接着 `up … platform-support-service`；
   - 核对 healthy、重启 0、ERROR 0、`.Image` 等于候选，日志没有 `File storage snapshot refresh failed`；
   - 用 `docker exec … printenv | cut -d= -f1` 确认没有 `FILES_ONBOARDING_STORAGE_*` / `FILES_ONBOARDING_MINIO_*` / `FILES_ONBOARDING_SCAN_POLICY_CODE`，并有 `PLATFORM_SUPPORT_FILES_ENABLED`。
7. **激活：**
   - `apply_sql.sh $B platform-act`，期望 12 / 2 / 2 / 1；
   - `apply_sql.sh $B postcheck`，全部等于期望，`PLATFORM_ADMIN` 版本从 40 变为 41，Vault 策略版本 19；
   - 等 Gateway Snapshot 刷新（≤ 60 秒）。
8. **登记存储（窗口结束）：** `python3 -I release/acceptance/stage7_register_storage.py`。
   - 依次：新建实例 → 登记桶 → 检查 OK → 启用桶 → 启用实例 → 默认路由 `*`；
   - 脚本幂等，失败后修正原因再重跑；
   - 然后 `apply_sql.sh $B storage-check`，全部等于期望。
9. **验收：** `python3 -I release/acceptance/stage7_acceptance.py`（第 7 节），然后 `python3 -I release/acceptance/stage7_cleanup.py <run.json>`。
10. **浏览器冒烟（管理员，只读为主）：**
    - **方式：** Playwright 经 `/api/operations/**` 中继访问 http://localhost:28080，同币种发布的 `stage7_browser.py`；
    - **新页面：** 「平台配置 → 存储管理」三个页签。实例列表显示 `MINIO`（STATIC、密钥已配置）；桶页 `mor-platform-files` ACTIVE、检查 OK；写入规则显示「默认」且没有删除按钮；
    - **回归：** 客户入驻审核详情点一次材料下载；币种页、渠道 MID 详情、外部接入方各打开一次；
    - **提醒用户：** 换 ops-web 后要刷新已打开的标签页。
11. **主 compose：** 经用户同意后，在主工作区 `git apply release/compose-applications.patch`，否则下次有人用主 compose 重建 Platform 会因旧键残留启动失败。应用后用 `compose.sh config` 确认渲染的 Platform 环境与候选相同。
12. **记录：**
    - 填第 8 节，`delivery.md` 补环境操作记录；
    - 把新镜像 ID 和源码基线（`281a217d` + 本需求补丁）通知第 0 步各会话；
    - 特别告诉数据查询和工作流会话：今后的 Platform 候选必须包含本需求代码，覆盖文件不得写回旧存储键。

## 6. 回滚

> **2026-10-22 之后：** 数据查询发布（code-mor-76）已在本包之上替换三个服务（Platform `985d00ea…`、ops-bff `d221817d…`、ops-web `e29197ba…`，基线 `9a209e6f` + 本需求）。本包的 compose-rollback.yaml 会同时回退数据查询，不能再直接使用；回滚须以数据查询发布包（`requirements/20261022/platform-support-数据查询服务/release/`）的当前版本为准，任何新构建都须基于 `9a209e6f` + 本需求。

| 范围 | 做法 |
|---|---|
| 激活 | `apply_sql.sh $B platform-deact`，期望 12 / 2 / 2 / 1。只关闭运营入口；已登记的存储继续服务 |
| 镜像 | 1. 停用激活。<br>2. **先** `apply_sql.sh $B rollback-relax`：4 列放宽为可空，新 Platform 不受影响。<br>3. 再按 **ops-web → ops-bff → platform** 的顺序 `compose.sh -f <compose-rollback.yaml> up … <service>`。回滚文件会写回旧存储键、去掉 `PLATFORM_SUPPORT_FILES_ENABLED`，旧 Platform 照旧从环境变量装配 MinIO。<br>4. 若已应用 `compose-applications.patch`，同时 `git apply -R` |
| 回滚后再前滚 | 按 [90](./sql/90-rollback-relax-new-columns.sql) 文件头：先回填旧 Platform 期间产生的空值，再恢复 NOT NULL，然后换新 Platform |
| 存储登记 | 通常保留（旧 Platform 不读这三张表）。需要清空时直接删 `t_file_storage_route` / `bucket` / `instance` 行（子表先删）；Vault Record 保留 |
| DDL 结构 | 一般不回退。确需回退时，先回滚镜像，再按 01 文件末尾“回退”段逆序执行（丢弃新列与 3 张表） |
| Vault | 数据类型和规则保留无害。删除只能在没有 Token 引用时进行，见 02 文件头 |
| 块 130 注册 | 保持休眠。彻底移除按 ID 范围 900000000000130101—900000000000130799 删除，子表先删 |
| 验收数据 | `stage7_cleanup.py` 删除合成桶行、合成人员 / 角色，审计保留 |

## 7. mor-test 验收

`acceptance/stage7_acceptance.py`：经运营入口 http://localhost:28080，管理员取 `test-environment/private/access.md`。运行记录写 `~/Code-MoR-backups/filestore-stage7/run-*.json`（0600，含合成人员口令，不含存储密钥）。

| AC | mor-test 检查 | 本机覆盖 |
|---|---|---|
| AC-01 | 实例列表：MINIO ACTIVE / STATIC / 密钥已配置，桶 ACTIVE 且检查 OK。以下位置都不含密钥原值，列表响应也不含 Vault Token：<br>· 列表与更换密钥的响应；<br>· `t_file_storage_instance` 行；<br>· 存储审计属性；<br>· Platform / ops-bff / Gateway 自验收开始的日志。<br>存储写入有审计（`FILE_STORAGE_BUCKET`、`FILE_STORAGE_INSTANCE`）。<br>合成只读人员能看实例和规则；新建实例、检查桶返回 403；导航有页面、没有维护按钮 | S3 实例并存（`FileStorageInstanceManagementTest`） |
| AC-02 | 登记不存在的桶 `fs7-missing-<run>`，检查结果为 NOT_FOUND / ACCESS_DENIED，启用返回 400 | 未开版本控制 / 未开加密 / 无写权限（本机 MinIO 管理员临时建桶） |
| AC-03 | 默认路由 `*` 指向 MINIO / mor-platform-files / CLIENT_ONBOARDING_FORMAT | 精确路由与默认路由分流（`InternalFileApiTest`） |
| AC-04 / AC-10 | 已有记录全部回填 `platform-support / CLIENT_ONBOARDING_MATERIAL`。从审核任务找一份已有材料，经 O10 审核下载签发地址，**实际下载**（只信任 test-ca，按原 Host / SNI 连接），字节数与 SHA-256 等于 `t_file_object` 登记值 | 改路由到 S3 后读旧 MINIO 文件；入驻全链路回归 |
| AC-05 / AC-06 | 不在 mor-test 做：桶被默认路由引用，设只读 / 停用会被拒绝；停用实例会中断共享环境的入驻 | `test_multipleStorages`、`test_disabledStorageRetries` |
| AC-07 | 用同一账号更换一次密钥：版本 +1，Token 改变，旧 Token 不再被引用；之后桶检查 OK，且 Vault REVEAL 计数增加 | `test_replaceCredential` |
| AC-08 | 不在 mor-test 做（不能停共享 Vault） | `test_vaultUnavailable*`、`test_iamRoleUnaffectedByVault` |
| AC-09 | 旁路容器在 Platform 私网内调用 4 个内部接口（另加 1 个带 `X-Caller-Service` 的开始上传），共 5 次全部 401（mor-test internal-auth=OFF） | 有身份时 `source_system` 取自 Token、跨来源完成拒绝（`test_callerIdentity`） |
| AC-11 | 不在 mor-test 做。替换前已用 `compose.sh config` 证明旧键已从候选环境中删除 | `FileStorageConfigurationTest` |
| AC-12 | 运行检查：三个服务 healthy、未重启；Platform、ops-bff 验收期间 ERROR 0 | Platform auto 791/791 |

清理 `stage7_cleanup.py <run.json> [--dry-run]`：
- **合成桶：** 只删运行记录里的那一行，要求属于 MINIO、DRAFT、创建时间不早于本次运行、无路由和文件引用；
- **合成人员：** 删合成只读人员和角色，审计保留；
- **探针：** 删本机探针 class；
- **复核：** 最后核对存储登记只剩 `MINIO / mor-platform-files / *`。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 04:40—04:41 | 0 协调 | 数据查询 code-mor-76 约定本包先发，之后以 `9a209e6f` 加本发布树重建；channel-connector code-mor-0e、工作流 code-mor-ae 近期不发 mor-test；code-mor-46 已通知 |
| 04:41:50 | 1 复核基线 | 三个容器 `.Image` 等于 compose-rollback.yaml，mor-test 全部 healthy |
| 04:41:56 | 2 备份 / 基线下载 | `~/Code-MoR-backups/20261022T044156Z-filestore-mor-test/`；precheck 与阶段 6 输出相同（只差文件头）。`--legacy-only` 3/3：旧 Platform 下载已有材料 193 字节，SHA-256 与登记一致 |
| 04:42:17 | 3 Vault | 数据类型 `FILE_STORAGE_CREDENTIAL_V1` 与 PROTECT / REVEAL 两条规则 ACTIVE，策略版本 19 |
| 04:42:17 | 4 块 130 注册 | 152 行休眠：权限 2、授权 2 REVOKED、资源 2 与路由 12 DISABLED；mor-test 无块 129 |
| 04:42:31—04:43:00 | 5 ops-bff → ops-web | `e81b8424…`、`8c224936…` healthy、重启 0、ERROR 0 |
| 04:43:07—04:43:38 | 6 DDL → Platform | DDL 后紧接替换，`04c12879…` healthy、重启 0、ERROR 0，无快照刷新失败；旧存储键已不在环境中，有 `PLATFORM_SUPPORT_FILES_ENABLED` |
| 04:48:30 | 6 补充：补偿执行器 | XXL `xxl_job_registry` 中 `platform-support-service` 心跳 04:48:30（`http://172.30.73.21:9999/`）；04:46:32 一次注册读超时，下一次心跳恢复；`COMPENSATION_ENABLED=true` 仍在 |
| 04:43:43 | 7 激活 / postcheck | 12 / 2 / 2 / 1；postcheck 全部等于期望，`PLATFORM_ADMIN` 41，55 条旧记录回填完成 |
| 04:43:51 | 8 登记存储（第一次） | 实例 `MINIO`、桶已登记；**桶检查 415 `OPERATIONS_UNSUPPORTED_MEDIA_TYPE`**。原因：ops-bff 对所有写方法要求 JSON Content-Type，块 130 原稿让桶检查路由 130407 不登记 content-type，Web 无请求体时也不发该头，所以页面点“检查”同样失败。修正（同 Webhook 测试路由 `900000000000090407` 先例）：① [sql/11](./sql/11-check-route-content-type.sql) 补 130563 / 130564 两行请求头（04:46:51 执行，2 / 1），初始化 DML 同步，块 130 共 154 行；② Web `sendStorageCommand` 一律声明 JSON；③ 脚本改为声明类型的空正文 |
| 04:46:56—04:47 | 8 登记存储 / storage-check | 6/6：检查 OK、启用桶与实例、默认路由 `*`；storage-check 全部等于期望（Vault PROTECT 1、REVEAL 1）。**不可用窗口 04:43:07—约 04:47:00，约 4 分钟** |
| 04:47:03 | 9 验收 / 清理 | **33/33**（AC-01/02/03/04/07/09/10 与运行检查），运行记录 `~/Code-MoR-backups/filestore-stage7/run-20261022044703.json`；清理后存储登记只剩 `MINIO / mor-platform-files / *` |
| 04:47:42 | 9a ops-web 修正版 | 发布树 Web 1252/1252、构建通过；镜像 `sha256:d58172d9a291c99f1c1b37ef76e0a77284ab49ede84dafd294f05d1244e8ea9e`（`filestore-20261022b`）内 `dist/` 与构建产物逐字节一致；替换后 healthy、重启 0。首版 `8c224936…` 作废 |
| 04:5x | 10 浏览器冒烟 | 第二轮 9 项中 8 项通过，控制台错误 0，4xx/5xx 0（[stage7_browser.py](./acceptance/stage7_browser.py)，截图 `acceptance/screens/`）。存储管理三个页签正常，页面上真实点「检查」2 次均 200 / OK，无 415；币种、渠道 MID 能力组、外部接入方各页正常。**失败 1 项（既有缺陷，非本次引入）：** 入驻审核材料下载签发 200，但 `expiresAt` 为纳秒精度（如 `…04:53:30.875887712Z`），ops-web `decodeFileDownload` 只接受 ≤ 6 位小数，页面提示「文件下载未完成」。`ReviewFileDownloadProcessor` 与 MinIO `authorize` 的时间构造在 `281a217d` 与候选中相同；签发地址本身可下载（193 字节）。待用户决定修复范围。第三轮加跑时入口已 500，未完成 |
| 04:5x | 10 浏览器冒烟 | **中止**：宿主机磁盘写满（/System/Volumes/Data 剩约 170 MB，code-mor-76 04:5x 通报），Docker VM 出现 input/output error：`docker system df`、读日志、exec 均报错，ops-web nginx 返回 500，**mor-test-mysql-1 已停止**。三个候选容器在此之前均 healthy（Platform `04c12879…`、ops-bff `e81b8424…`、ops-web `d58172d9…`）。本会话删除自己三个 worktree 的 target / node_modules 与临时目录约 1.6 GB；未重启 Docker、未动任何容器，等待用户决定 |
| 04:49 | 11 主 compose 补丁 | 已应用到主工作区：删 8 个旧键，加 `PLATFORM_SUPPORT_FILES_ENABLED`；`compose.sh config` 渲染确认 |
| 05:3x | 10a 环境恢复 | 用户清理磁盘（可用 103 GB）并重启 Docker；mor-test 全部 healthy。三个容器仍为候选镜像；Platform 05:34—05:37 因依赖未就绪启动失败数次后于 05:37:05 正常启动。数据核对：文件 55 / 55 且全部回填，块 130 路由头 64、路由 12 ACTIVE，Vault 策略 19，存储登记 `MINIO / mor-platform-files / *` 在，五张文件与存储表 CHECK TABLE OK |
| 05:44:07—05:44:35 | 10b Platform 修正版 | 修复审核材料下载：`MinioFileStorage`、`S3FileStorage` 的 `clock.instant()` 截到微秒（上传与下载授权）；两个适配器测试类 24/24。候选 `sha256:6dd8ffffae5e86f6869d9d04a5b0cb52583936b0805a842e648d555f8551cb0a`（`filestore-20261022c`），与 `04c12879…` 的 JAR 只差这两个类。替换后 healthy、重启 0、ERROR 0，环境无旧存储键 |
| 05:45—05:46 | 10c 浏览器冒烟重跑 | **10/10**：存储管理三个页签；页面点「检查」200 / OK（累计真实检查 3 次，只回写最近检查结果）；审核页下载 `identity.pdf` 193 字节，`expiresAt` 为微秒精度；签发地址单独下载 193 字节；币种、MID 能力组、外部接入方正常；控制台错误 0、4xx/5xx 0。Chromium 把预签名下载记为 `ERR_ABORTED`（响应 200），脚本已改为“转为下载”。结果 `acceptance/stage7-browser-result.json`，截图 `acceptance/screens/` |
| 05:5x | 12 记录与通知 | 最终镜像：Platform `6dd8ffff…`、ops-bff `e81b8424…`、ops-web `d58172d9…`；源码 `Code-MoR-filestore-rel`（`281a217d` + 本需求，含 sql/11 与两处修正）。已通知 code-mor-76（可以开始数据查询发布）、code-mor-0e、code-mor-ae、code-mor-46 |

## 9. 风险与需要主会话决定的事项

1. **与数据查询发布的先后（必须先定）。**
   - **现状：** 数据查询（main `9a209e6f`，块 131，Vault 750—752）已在主线提交，`requirements/20261022/platform-support-数据查询服务/release/` 正在准备，同样替换这三个服务。
   - **本包先发：** 数据查询的候选必须以 `9a209e6f + 本需求补丁` 构建，覆盖文件保留本包对 Platform 环境变量的处理。否则会出两种问题：存储管理被回退；或者旧文件代码因缺少 `FILES_ONBOARDING_*` 键无法装配 MinIO，新代码则因旧键残留拒绝启动。
   - **数据查询先发：** 本包基线失效，需要在其镜像上重建。00 / 99 中 Vault 策略版本的期望值也要相应 +1。
2. **AC-09 正向链路。**
   - **问题：** mor-test Platform 的 internal-auth 是 OFF，内部文件接口一律 401，mor-test 上无法做“上传→完成→查询→下载”。
   - **建议：** 接受由本机测试覆盖；mor-test 用审核下载旧文件和桶检查（真实读写探测对象）证明新存储运行时可用。
   - **备选：** 临时把 Platform 改为 DUAL。这会影响所有内部入口的身份校验，不建议在本次顺带做。
3. **DDL 窗口：**
   - **默认：** 单文件 DDL 紧贴 Platform 替换，约 60–90 秒内入驻上传失败；
   - **之后：** 到登记完成前约 3–5 分钟，入驻上传和审核下载返回 503；
   - **要求零窗口时：** 按第 2 节①拆分。
4. **`compose-applications.patch` 何时应用：**
   - **建议：** 随第 5 节第 11 步应用到主工作区；
   - **注意：** 主 compose 当前还有工作流会话的未提交改动，补丁与其不重叠，`git apply --check` 已通过；
   - **不应用的后果：** 任何人用主 compose 重建 Platform（包括 `build.sh` 之后的 `up`），新镜像都会拒绝启动。
5. **AC-07 在 mor-test 更换一次密钥：** 用同一账号，仍是一次真实写入，Vault 多一条 Record。如不希望做，删去 `stage7_acceptance.py` 中的 `replace_credential` 调用即可。
6. **共享会话：**
   - 换 ops-web 后，打开中的标签页要刷新；
   - 发布期间要求其他会话不要部署这三个服务，商务、对账、channel-connector 也都会用到它们。
