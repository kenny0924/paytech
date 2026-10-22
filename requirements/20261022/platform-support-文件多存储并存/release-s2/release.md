# platform-support：文件多存储并存第二段 · mor-test 发布包

阶段 6（2026-10-22，核对时间 14:40–15:20Z）。本阶段只做了以下几件事：
- 只读核对；
- 在集成工作目录构建镜像；
- 写发布包。

**没有改 mor-test 的任何容器、数据、Vault 或接口状态**，没有提交，也没有 install。阶段 7 须用户另行授权，由主会话按第 5 节执行，结果写第 8 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像：** platform-support、operations-console-bff、client-master（CCT）、client-console-bff、client-console-web 五个容器；
- **Platform 环境变量：**
  - 新增 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID`；
  - 去掉已被忽略的 `FILES_ONBOARDING_ENABLED`。
- **数据库：**
  - CCT `t_application_document` 执行第 1 步：加 `file_address`、回填、放宽 `file_id`、加新唯一键；
  - 最后另行授权执行 CCT 第 2 步和 Platform 删 Owner 列 / 表。
- **不动：** ops-web，以及 Gateway 注册（Q17 路径不变）、Vault、Nacos、XXL。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 集成工作目录 | `/Users/kenny/Desktop/Workspace/Code-MoR-fs2-int` = main `1d39cd94` + 第二段（S2-A/B/C 三卡合并，验证见 [delivery.md](../delivery.md) 与 CURRENT_IMPLEMENTATION 第二段条目） |
| main 现状 | `3a4574c7`，比集成基线只多 Cybersource 一个提交：`paytech/channel-connector` 8 个文件、`requirements/` 11 个、CURRENT_IMPLEMENTATION、README。与本次五个服务无关 |
| ops-web | 集成目录相对 `1d39cd94` **没有** `operations-console-web` 改动。现网 `c7646648` 就是 `1d39cd94` 构建（工作流发布），本次不换 |
| 构建 | Java：`./mvnw -B -o -DskipTests -pl <四个 app> -am clean package`（JDK 25、`~/.m3`，没有 install）。镜像用 `test-environment/java/Dockerfile`，`health-probe/` 从各运行容器 `docker cp` 读出。<br>client-console-web：`npm ci` → `npm run build` → `docker build`。<br>没有改 `:development` tag |
| 镜像核对 | 镜像内 JAR / `dist/` 与构建产物逐字节相同；Entrypoint、Cmd、User、WorkingDir、Env 与对应运行镜像相同。client-console-bff 例外：现网镜像已丢失，见 1.2 |

### 1.2 基线证明（不加第二段，与运行容器逐项比较）

比较方法同第一段：`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目比较，第三方 JAR 按整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引。Web 比较 `dist/` 与 `nginx.conf` 逐字节。

| 容器 | 运行镜像 | 基线 | 结果 |
|---|---|---|---|
| platform-support-service | `b9301714…`（JAR `53c0903f…`） | `1d39cd94` | **0 差异**（2,802 项） |
| operations-console-bff | `f1fbcea1…`（JAR `463a0efb…`） | `1d39cd94` | **0 差异**（2,472 项） |
| operations-console-web | `c7646648…` | `1d39cd94`（工作流发布已证明） | 本次不换 |
| client-master-service | `6f1dce5e…`（10-18 16:14Z 构建，JAR `8ded0f8d…`） | `1d39cd94` | **不一致**：只有前者 1,302 项、只有后者 1,482 项，变化 28 项 |
| | | `e119cbd1`（10-18 step-16） | 应用代码 **0 差异**。只差 `paytech-messaging` 的连接恢复（`339db7b8`、`8d123532`，现网已有、`e119cbd1` 尚无），即现网 = `e119cbd1` 应用代码 + messaging 恢复 |
| client-console-bff | `51f5263b…`（10-18 13:39Z，JAR `3437be76…`） | `1d39cd94` | **不一致** |
| | | `e119cbd1` | **0 差异**（774 项） |
| client-console-web | `b060cd3d…`（10-18 13:39Z） | `e119cbd1` | `dist/` 3 个文件与 `nginx.conf` **逐字节一致**；`1d39cd94` 构建与现网不同 |

**现网镜像丢失：** client-console-bff 的运行镜像 `51f5263b` 已不在本机 Docker 镜像库，推测是 10-22 磁盘事故后被清理；容器仍在运行。
- 回滚镜像改用 `e119cbd1` 重新构建的 `sha256:2ff7bff9ad263d38760e813c8ebcfd524d9db6971e49f6e0d12ca98f9089980c`（tag `rollback-e119cbd1`），其 JAR 与运行容器内 JAR 逐条目 0 差异。
- 另外两个 10-18 镜像 `6f1dce5e`、`b060cd3d` 仍在。
- 这三个容器用的是 `:development` tag，没有钉 sha256。

### 1.3 CCT / 客户侧会顺带上线的未发布改动（只报告，不取舍）

现网 client-master / client-console-bff / client-console-web 停在 `e119cbd1`（10-18）。`e119cbd1..1d39cd94` 中涉及这三个服务及其所含库的提交如下：

| 类别 | 提交 | 内容 | 影响 |
|---|---|---|---|
| CCT 应用代码 | `ab14e118`（经 `a05888f8` 合入） | client-master 入驻入口拆为一入口一 Processor，继承 Foundation 模板（43 个文件，含 `OnboardingResourceLockTest`） | 重构，接口与库结构不变 |
| client-console-bff 应用代码 | `b25d316f`、`26a6a217` | BFF 接口与 Processor 按规范重组；Risk 评估解释聚合进 Processor，页面模型改 class（共 81 个文件） | 重构，浏览器路径不变 |
| client-console-web | `22815887` | 保存资料页按 `errors[]` 逐字段标红（step-16） | BFF / CCT 一侧的 step-16（`e119cbd1`、`4a366641`）已在现网，现网 Web 却没有这一提交。上线后 Web 才用上字段错误 |
| Foundation | `339db7b8`、`8d123532` | messaging 连接恢复后重声明拓扑、失败退避重试 | CCT 现网已带；client-console-bff 不含 messaging |
| Foundation | `beb21618` | HTTP 客户端 Owner Problem detail 显式开关 | 默认关闭，调用方未改用则行为不变 |
| 合同库 | `08c8f794`…`5a103f36` 等 commercial 提交；`13c02c87`、`9a209e6f`、`346a1822`、`1d39cd94`、`ecab136c` 等 platform-support-api 提交 | commercial-api 六层定价 / Checkout 解析扩展字段；platform-support-api 的币种、数据查询、文件存储、工作流、外部 API 迁出等 DTO | CCT 调 commercial 的 Checkout 解析：mor-test 的 commercial 已是新版本，CCT 现网用的是旧 DTO。新镜像改为与现网 commercial 一致的合同。BFF / CCT 不调用新增的 Platform 接口 |
| 文档 | `cf4a429c` | client-master PDManer `name` / `describe` 改写 | 只改模型文档 |

**是否需要 DDL / 数据 / 配置：**
- 上述提交没有改 client-master、client-console-bff 的 `application.yml` 或任何 `src/main/resources`；
- 没有新增 SQL；
- 环境变量渲染与现网逐键相同（第 2 节②）。

所以它们**可以**随本次一并上线，不需要额外步骤。但这相当于把 10-18 以来 CCT / 客户侧的重构第一次带上 mor-test，风险是重构本身的行为回归（各自已有自动测试，没有在 mor-test 验过）。一并发布还是先单独发一版 `1d39cd94` 的 CCT / 客户侧，由主会话和用户决定（第 9 节第 1 项）。

### 1.4 候选镜像（tag `filestore-s2-20261022`）与差异

| 服务 | 候选 | 相对 `1d39cd94` 构建的差异（全部属于第二段） |
|---|---|---|
| platform-support-service | `sha256:7cb304024b520513dd461e22d6c423d11072ede95b47c9a56cf1c6e4f322f226`（JAR `2d12967f2320998c`） | **删除 115 项：**<br>· `OnboardingFileApi` / `OnboardingFileVerificationApi` / `ReviewFileApi` 及其 DTO、枚举、Controller、Processor；<br>· `CctFileTargetClient`；<br>· 整个 `client-master-api` 依赖（Platform 不再反调 CCT）。<br>**新增 3 项：** `FileErrorCode`、`FileDisposition`、`DownloadPresentation`。<br>**改动 53 项：** 内部文件接口与 DTO（`fileAddress`、`disposition`、操作人审计）、上传 / 完成 / 下载 Processor、工作流附件适配、`FileUpload` 与两个 Mapper（不再读写 Owner）、`TenantProjection*`（DUAL）、`application.yml`。<br>第三方 JAR 无增删 |
| operations-console-bff | `sha256:a5b9a17cf88c4b6cc043ef543d890ca5f5e3e8cf1a99d2fcd1c1c34ceabf4820`（JAR `3d10e562aaa68f80`） | O10 改走 `InternalFileApi`（`ClientReviewFileDownloadProcessor`、`ClientReviewFileClient(Impl)`、本地响应 `ClientReviewFileDownloadResponseBody`）；`PlatformClientConfiguration`、`OperationsErrorStatusResolver` 改用 `FileErrorCode`；删除 `ReviewFileApi` 等 api 类 |
| client-master-service | `sha256:e3fded0b701ddca6d2375b180f40c9d91e759f1f8773dc8457b67a3c1937aad0`（JAR `41a647487f7df657`） | `ClientApplicationDocument.fileAddress`、Mapper / Repository / Service、`ClientMaterialAuthorityClient`（调 Platform `getFile`）、`AssociateClientDocumentProcessor`、`ClientSubmissionPreparer`、审核登记（从地址解析 ID）；删除 `ClientFileTarget*` |
| client-console-bff | `sha256:105b962e775819d41933b41b673910ad768d511fd0ee06a8745aa40b8e4d5b7a`（JAR `82398677c4278014`） | 新增 `PlatformFileClient(Impl)`、4 个入驻文件 Processor 与 `ClientOnboardingFileAccess`；`ClientOnboardingFileController`、申请详情、错误映射改动；删除 `PlatformOnboardingFileClient(Impl)` |
| client-console-web | `sha256:25b30a78547bfe8a510560500ad1ab19623252ede81ebcba2b78c2bc3a19423d` | `onboarding.ts`：开始上传不再带 `applicationId` / `subjectPartyId`，关联提交 `fileAddress`。另外带上 `22815887` |

## 2. 兼容性（决定执行顺序）

**① CCT 第 1 步对旧 CCT 安全。**
- **改动内容：** 加可空 `file_address`，回填 `CONCAT('/files/v1/', file_id)`，`file_id` 放宽为可空，新增三个 `*_addr` 唯一键（旧键保留）。
- **为什么安全：** 旧 CCT 只按显式列读写 `file_id`；它新写的行 `file_address` 为空，新唯一键允许多个 NULL，不冲突。
- **遗留：** 第 1 步之后、换新 CCT 之前，旧 CCT 新关联的材料 `file_address` 为空。第 2 步执行前要先补一次回填（02 文件已内置）。
- **回滚影响：** 新 CCT 只写 `file_address`，新行的 `file_id` 为空。所以回滚到旧 CCT 之前，必须先执行 `sql/90-rollback-cct-file-id.sql`，按地址补回 `file_id`。

**② CCT 第 2 步必须在新 CCT 稳定之后执行。** 它删 `file_id` 与旧唯一键，`file_address` 改 NOT NULL，新唯一键改回原名。之后不能再回滚到旧 CCT。

**③ Platform 删 Owner 放在最后。**
- **对新 Platform：** 不读写 `owner_*` 与 `t_file_owner_ref`，删除前后都能运行。
- **对旧 Platform：** 删除后，任何不早于第一段的旧 Platform 都会因缺列失败（第一段及更早的镜像读写这些列）。
- **执行前：** 先用 `apply_sql.sh platform-owner-backup` 备份两张表。

**④ 新旧混跑：哪些组合会坏。**

| 组合 | 结果 |
|---|---|
| 新 Platform + 旧 client-console-bff | 旧 BFF 调 F01—F04（`/onboarding-files/v1/**` on Platform）返回 404：客户上传、查文件、下载都失败 |
| 新 Platform + 旧 CCT | 旧 CCT 的 C12 / C14 调 `/internal/onboarding-files/.../verification` 返回 404：关联、提交失败 |
| 新 Platform + 旧 ops-bff | 旧 ops-bff 调 `/review-files/v1/**` 返回 404：O10 审核下载失败 |
| 旧 Platform（OFF）+ 新 CCT / 新两个 BFF | 内部文件接口在 OFF 下一律 401。新调用方还要求响应里有 `fileAddress`（新 BFF、新 CCT 严格核对），旧 Platform 不返回：全部失败 |
| 旧 Platform + 新 CCT | 旧 Platform 的 F01 反调 CCT `ClientFileTargetApi`，新 CCT 已删除：旧链路开始上传失败 |
| 新 client-console-web + 旧 client-console-bff | 新 Web 开始上传不带 `applicationId` / `subjectPartyId`，旧 BFF 必填：上传失败 |
| 旧 client-console-web + 新 client-console-bff | 旧 Web 多带两个字段，新 BFF 严格请求体会拒绝；关联提交 `fileId` 而不是 `fileAddress`：上传、关联都失败 |
| 其余（工作流、存储管理、币种、数据查询等） | 不受影响 |

结论：
- **没有可以单独先换的服务。** Platform 先换（新调用方离不开它），然后**一次命令**同时替换另外四个服务。
- **不可用窗口：** 客户入驻上传 / 下载 / 关联 / 提交，以及运营 O10 审核下载，从 Platform 换完到四个服务 healthy，约 2–4 分钟。
- **其他功能不受影响。**

**与 Q16“分两次替换 Platform”的偏差：** segment2.md 第 8 节设想第一次替换时“旧入口暂不删”。但集成代码已经一次删掉 F01—F04、`/internal/onboarding-files` 与 `/review-files`，没有“保留旧入口”的中间镜像，所以做不到真正的两次替换。现在的“两次”是：
1. 本次一次换 Platform 与四个调用方；
2. 验收后另行执行 CCT 第 2 步与 Platform 删 Owner。

在执行任何删除 DDL 之前，任一步都能按 `compose-rollback.yaml` 回滚（CCT 回滚前先执行 90）。如果一定要“保留旧入口的过渡镜像”，需要另做一个分支构建，不建议。

**⑤ Platform 环境变量。**
- **核对方法：** 先 `printenv` 读运行容器，再 grep 历次 `compose-candidate*.yaml`。工作流发布的覆盖文件渲染后与五个运行容器**逐键、逐值相同**。本包以它为起点。
- **候选与现网的差异只有三处：**
  - `+ PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`；
  - `+ PAYTECH_INSTANCE_ID=platform-support-test`；
  - `− FILES_ONBOARDING_ENABLED`。
- **`PAYTECH_INSTANCE_ID` 的取值规则：** 实例清单 schema `instance.id` 为 `^[a-z][a-z0-9-]{0,31}$`；mor-test 各服务命名为 `<服务>-test`（payment-test、channel-connector-test、card-test、billing-test、reconciliation-test），各实例唯一。
- **它的副作用：** 工作流 MQ 回调信封的 `event-instance-id` 原来默认 `main`，会变为 `platform-support-test`。目前没有订阅方。
- **保留项：** 工作流 5 个 `PLATFORM_SUPPORT_RABBITMQ_*`、`DATAQUERY_HTTP_CALLERS_0`、`PLATFORM_SUPPORT_FILES_ENABLED=true`、8 个旧存储键的置空。
- **渲染核对：** 候选渲染 53 键，现网 52 键；只多 2 个、少 1 个，其余逐键相同。回滚文件渲染与现网 52 = 52，逐键相同。`compose.sh -f <文件> config --quiet` 两份都通过。

**⑥ 其他服务不需要新增环境变量。**
- **调用方头写在代码里：** client-console-bff、ops-bff、CCT 调 Platform 时，`X-Caller-Service` 由各自 `PlatformClientConfiguration` / `ClientDocumentConfiguration` 固定写入。
- **配置文件：** 三个服务的 `src/main/resources` 自 `e119cbd1` 起没有变化。
- **渲染核对：** 四个服务的候选渲染与现网逐键相同。

**⑦ DUAL 的影响。**
- **判定逻辑（Foundation `InternalCallerAuthenticator`）：**
  - 合法 Token 优先；
  - 否则回退唯一且格式合法的 `X-Caller-Service`；
  - 两者都没有时**放行且不带身份**。
- **不会拒绝请求：** Token 不合法或公钥不可用时也回退到上一步，DUAL 不拒绝任何请求。
- **现有内部入口：** 都不依赖身份，行为不变。集成测试 `InternalAuthDualPassThroughTest` 已覆盖租户投影与币种查询两个代表。
- **mor-test 现状：**
  - 所有服务的 internal-auth 都是 OFF，没有服务发 Token；
  - Gateway 没有直达 Platform 的 Target（只有 client-console-bff、operations-console-bff、acquiring-service），BFF 调 Platform 不转发 `Authorization`。
  - 所以 DUAL 下不会触发 Token 校验，也不会出现 JWKS 拉取告警。
- **带头的调用方：** 已带 `X-Caller-Service` 的调用方（acquiring 的 Webhook、ops-bff 的数据查询，加上本次的三个调用方）会被识别为对应服务；它们本来就以这个头自报身份，不会被误判。
- **仍需显式身份：** 只有内部文件接口（`/internal/files/v1`）要求显式身份，无身份返回 401（第 7 节验收）。

## 3. 只读核对（阶段 6 已做）

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 于 2026-10-22T15:12:13Z 执行，20 项全部等于期望，输出见 [`sql/00-precheck-20261022.out`](./sql/00-precheck-20261022.out)。

| 核对项 | 结果 |
|---|---|
| CCT `t_application_document` | 尚无 `file_address`；`file_id` NOT NULL；旧唯一键 3 个、`*_addr` 0 个；52 行，引用的文件都在 Platform |
| Platform | `owner_*` 三列与 `t_file_owner_ref`（55 行）仍在；上传 / 文件各 55，全部 AVAILABLE / ACTIVE，没有进行中的上传；`mor_platform` 63 张表 |
| 存储与路由 | 默认路由 `*` → MINIO / mor-platform-files ACTIVE；没有 `client-console-bff` 精确路由，新上传走默认路由（扫描策略 `CLIENT_ONBOARDING_FORMAT`，只允许 pdf / jpeg / png） |
| 审核与网关 | 52 个文件被入驻审核证据引用；Client 入驻文件 4 条路由与 O10 路由 ACTIVE；`FILE_DOWNLOAD_AUTHORIZE` 审计 0 条（旧的 `OPERATIONS_REVIEW_FILE_DOWNLOAD` 14 条） |
| CCT 申请状态 | APPROVED 35、UNDER_REVIEW 5、DRAFT 4、RETURNED 3、REJECTED 3 |
| 容器 | 五个容器 healthy。client-master 重启计数 15、client-console-bff 29，均为 10-18 以来的历史累计（最近一次启动在 10-22 05:36Z，磁盘事故恢复时） |

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件，五个服务 |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 执行前只读核对 |
| [sql/01-cct-file-address-step1.sql](./sql/01-cct-file-address-step1.sql) | CCT 第 1 步（`changes/2026-10-22-application-document-file-address.sql` 文件头与第 1 步段逐字副本） |
| [sql/02-cct-file-address-step2.sql](./sql/02-cct-file-address-step2.sql) | CCT 第 2 步（同一文件第 2 步段，去注释，前面加补回填与空值核对）。另行授权 |
| [sql/03-platform-file-owner-removal.sql](./sql/03-platform-file-owner-removal.sql) | Platform 删 Owner（`changes/2026-10-22-file-owner-removal.sql` 逐字副本）。另行授权 |
| [sql/90-rollback-cct-file-id.sql](./sql/90-rollback-cct-file-id.sql) | CCT 镜像回滚配套：按地址补回 `file_id` |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) / [sql/98-final-check.sql](./sql/98-final-check.sql) | 替换后 / 两条删除 DDL 后的只读核对 |
| [scripts/backup.sh](./scripts/backup.sh) / [scripts/apply_sql.sh](./scripts/apply_sql.sh) | 备份与 SQL 包装：root 口令只经 `MYSQL_PWD`；`platform-owner-removal` 要求先做 `platform-owner-backup` |
| [acceptance/](./acceptance/) | 下列脚本 |

`acceptance/` 中的脚本：

| 脚本 | 用途 |
|---|---|
| `stage7_s2_acceptance.py` | 端到端与授权矩阵 |
| `stage7_s2_cleanup.py` | 清理 |
| `stage7_s2_browser.py` | 浏览器冒烟 |
| `s2_common.py` | 公共件；复用 V2 全链路脚本的 Client 会话、Mailpit 验证与私网探针 |

两个 SQL 源文件与主工作区逐字节相同。

## 5. 执行步骤（阶段 7，另需用户授权；主会话执行）

`$O` 为 `compose-candidate.yaml` 绝对路径，`$B` 为备份目录，`up` 指：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service...>
```

0. **协调：** 通知商务、对账、channel-connector、工作流、数据查询、卡 BIN 等会话，本次会换 platform、ops-bff 和客户侧三件，约一个窗口。期间有人发布了这五个服务中的任何一个，本包作废，在新基线上重建。
1. **复核基线：** 五个容器 `.Image` 必须等于 `compose-rollback.yaml`，client-console-bff 例外，其现网仍是已丢失的 `51f5263b`。mor-test 全部 healthy。
2. **备份：** `B=$(release-s2/scripts/backup.sh)`，确认 `$B/00-precheck.out` 与第 3 节一致。
3. **CCT 第 1 步（旧 CCT 运行中）：** `apply_sql.sh $B cct-step1`，按文件末尾的核对查询确认 missing 0、mismatched 0。
4. **Platform（不可用窗口开始）：**
   - `up … platform-support-service`；
   - 核对 healthy、重启 0、ERROR 0，`.Image` 等于候选；
   - `printenv` 有 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID`，没有 `FILES_ONBOARDING_ENABLED`。
5. **四个调用方（一次命令；窗口结束）：**
   - `up … operations-console-bff client-master-service client-console-bff client-console-web`；
   - 逐个核对 healthy、ERROR 0、`.Image` 等于候选；
   - CCT 的 XXL 执行器约 30 秒后重新注册。
6. **只读核对：** `apply_sql.sh $B postcheck`，全部等于期望。
7. **验收：** `python3 -I release-s2/acceptance/stage7_s2_acceptance.py`（第 7 节），然后 `python3 -I release-s2/acceptance/stage7_s2_cleanup.py <run.json>`。
8. **浏览器冒烟：** `python3 -I release-s2/acceptance/stage7_s2_browser.py <run.json>`。提醒用户刷新已打开的客户控制台与运营控制台标签页。
9. **（另行授权，新链路稳定后）删除 DDL：**
   - `apply_sql.sh $B cct-step2`；
   - `apply_sql.sh $B platform-owner-backup`；
   - `apply_sql.sh $B platform-owner-removal`；
   - `apply_sql.sh $B final-check`。

   此后不能再回滚到旧 CCT 或第二段之前的 Platform。
10. **记录：**
    - 填第 8 节，`delivery.md` 补环境操作记录；
    - 通知各会话新的镜像 ID；
    - 提醒数据查询、工作流等会话：今后的 Platform 候选必须基于 `1d39cd94` + 第二段，并带上本包的三处环境变量变化。

## 6. 回滚（删除 DDL 执行之前）

| 范围 | 做法 |
|---|---|
| 客户侧与 ops-bff | `compose.sh -f compose-rollback.yaml up … operations-console-bff client-console-bff client-console-web`。client-console-bff 回滚用重建的 `2ff7bff9`（JAR 与现网 0 差异） |
| CCT | **先** `apply_sql.sh $B cct-rollback` 按地址补回 `file_id`，再 `up … client-master-service`（回滚文件）。第 1 步结构保留，旧 CCT 能运行 |
| Platform | 在三个调用方都已回滚之后执行 `up … platform-support-service`（回滚文件：去 DUAL、去 `PAYTECH_INSTANCE_ID`、加回 `FILES_ONBOARDING_ENABLED`）。新链路上传的文件没有 Owner，旧链路 F03 / F04 读不到它们；旧文件不受影响 |
| CCT 第 1 步结构 | 一般保留，旧 CCT 兼容。确需回退时，用 01 文件头“第 1 步回退” |
| 删除 DDL 之后 | 不能回滚镜像。只能按 02 / 03 文件头的回退语句与备份先恢复结构和数据，风险高，不建议 |
| 验收数据 | 见第 7 节清理说明 |

## 7. mor-test 验收

`acceptance/stage7_s2_acceptance.py`。运行记录写 `~/Code-MoR-backups/filestore-s2-stage7/run-*.json`（0600，含合成账号口令）。

| 场景 | 检查 |
|---|---|
| 账号与申请 | 新注册合成账号 `fs2-<run>@example.test`，用 Mailpit 验证；收不到邮件时脚本按 V2 做法在 `mor_platform` 铸造验证挑战（写库）。之后建个人申请草稿并填资料。原因：mor-test 已有的入驻测试账号口令没有留存 |
| 上传（P1） | 开始（只带文件名 / 类型 / 大小）→ 直传 MinIO → 完成（回执带 `fileAddress`）→ 轮询扫描到 AVAILABLE |
| 扫描未完成 409 | 完成后立即下载、立即关联，期望都返回 409；若扫描已先完成，则如实记为“未能验证”，由本机测试覆盖 |
| 本人下载 | 字节与 SHA-256 一致；下载地址签入原文件名（`filename` 与 `filename*`） |
| 地址格式错误 422 | C12 提交 `/files/v2/{id}` |
| C12 关联（P2） | 提交 `fileAddress`；CCT 行 `file_address` 等于地址、`file_id` 为空、`content_hash` 等于文件 SHA-256；申请详情材料带 `fileAddress` |
| 他人文件 | 用 55 个旧文件之一：查询、下载都返回 404 |
| O10（P3） | 用已有审核任务的旧文件：下载字节与 SHA-256 等于登记值；`FILE_DOWNLOAD_AUTHORIZE` 审计增加，操作人不为空 |
| 旧数据 | 52 行旧材料都有地址且与 `file_id` 一致（库内只读）。旧申请的客户口令不可得，不能用客户身份打开旧申请详情；旧文件的实际下载由 O10 覆盖 |
| 内部接口（私网探针） | 无身份 401；带 `X-Caller-Service` 查询 200 且带 `fileAddress`；`disposition=INLINE` 对 pdf 返回 INLINE；缺省为 ATTACHMENT；未知值 400。“非 pdf / png / jpeg 降为 ATTACHMENT”在 mor-test 无法构造（扫描策略只允许这三种），由本机测试覆盖 |
| 运行 | 五个容器 healthy；四个 Java 服务验收期间 ERROR 0 |
| C14 提交 | **默认不做。** 提交会在 compliance 生成审核任务，共享环境没有删除接口，只能由运营退回或拒绝。需要时加 `--submit`，须主会话先确认（第 9 节第 3 项） |

**清理（`stage7_s2_cleanup.py`）：**
- **会删：** 只删本次 C12 关联的材料，用合成账号调 C13，走正常业务规则。
- **保留：**
  - 合成账号及其 Client / 租户 / 草稿申请：跨 Platform IAM 与 CCT 多表，没有业务删除接口；
  - 本次上传的 Platform 文件与 MinIO 对象：文件服务不提供删除；
  - 审计。

  这与 2026-10-14 入驻验收“test 留有验收数据”的做法一致。

**浏览器（`stage7_s2_browser.py`）：**
- **客户控制台：** 合成账号登录 → 草稿申请 → 页面上传一份 PDF 直到“材料已关联” → 材料列表“查看”下载；
- **运营控制台：** 审核详情下载、存储管理、工作流我的待办 / 流程实例。mor-test 目前没有工作流附件，附件下载由本机测试覆盖；
- **判定：** 控制台错误 0、4xx/5xx 0（预签名转下载的 ERR_ABORTED 不计）；截图写 `acceptance/screens/`。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 15:20—15:22 | 0 协调 | code-mor-6b（卡 BIN / 3DS）、code-mor-f8、code-mor-42、code-mor-ae（工作流）均无冲突、近期不发这五个服务。ops-web 现网 `7551dc3b` 为 code-mor-42 的按钮布局发布（1d39cd94 + 页面改动），本包不换 ops-web。工作流确认 `PAYTECH_INSTANCE_ID` 改变 MQ `event-instance-id` 无影响 |
| 15:22 | 1 复核基线 | 五个容器 `.Image` 等于 compose-rollback.yaml（client-console-bff 现网仍为已丢失的 `51f5263b`，回滚用重建的 `2ff7bff9`），全部 healthy |
| 15:22:29 | 2 备份 | `~/Code-MoR-backups/20261022T152229Z-filestore-s2-mor-test/`；precheck 与阶段 6 输出一致 |
| 15:22:40 | 3 CCT 第 1 步 | 52 行全部回填，missing 0、mismatched 0；新旧唯一键共 6 个 |
| 15:22:53—15:23:28 | 4 Platform | `7cb30402…` healthy、重启 0、ERROR 0；`PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID=platform-support-test` 生效，`FILES_ONBOARDING_ENABLED` 已去，工作流 5 个 RabbitMQ 键与 `DATAQUERY_HTTP_CALLERS_0` 保留 |
| 15:23:35—15:24:24 | 5 四个调用方 | ops-bff `a5b9a17c…`、client-master `e3fded0b…`、client-console-bff `105b962e…`、client-console-web `25b30a78…` 均 healthy、重启 0、ERROR 0。**不可用窗口 15:22:53—15:24:24，约 1.5 分钟** |
| 15:24 | 6 postcheck | 全部等于期望；Platform Owner 列与表仍在；55 个旧文件仍 AVAILABLE / ACTIVE |
| 15:24—15:27 | 7 验收 / 清理 | 第一次运行 27 项中 26 通过、内部接口段因探针镜像 `:development` 已不存在中断。① “扫描未完成时关联返回 409”得到 201：核对时间戳，扫描 15:24:52.858 已完成、关联 15:24:53.137 在其后，CCT 放行正确；扫描约 0.1 秒完成，无法在 mor-test 构造，按第 7 节约定记为“未能验证”，由本机测试覆盖。② 设 `E2E_PROBE_IMAGE=<Platform 候选>` 补跑内部接口 6/6：无身份 401、带 `X-Caller-Service` 200 且带 `fileAddress`、来源 `client-console-bff`、INLINE 对 pdf 生效、缺省 ATTACHMENT、未知值 400。关键结果：上传回执带地址、本人下载 SHA-256 一致且带原文件名、地址错 422、CCT 存地址 + 冻结哈希、他人文件 404、O10 旧文件下载一致并写 `FILE_DOWNLOAD_AUTHORIZE` 审计、旧 52 行地址一致。清理：本次 C12 关联已删（204），旧 52 行不变。运行记录 `~/Code-MoR-backups/filestore-s2-stage7/run-20261022152433.json` |
| 15:3x | 8 浏览器冒烟 | **11/11**，控制台错误 0、4xx/5xx 与失败请求 0。客户侧：登录 → 上传弹窗内直传、扫描、关联到“材料已关联” → 列表“查看”下载；运营侧：审核详情下载、存储管理、工作流我的待办与流程实例。脚本修正：密码框选择器、上传流程改在 `<dialog class="editor">` 内操作。本次关联材料已用 C13 删除（204）。截图 `acceptance/screens-s2/` |
| 16:18—16:20 | 9 删除 DDL | 用户“执行删除 DDL”。前置：四个新镜像在线、CCT 52 行地址齐全；先另备份 `t_application_document`（`before-cct-step2-20261022T161850Z.sql`）。16:18:56 CCT 第 2 步：补回填 0 行，删 `file_id` 与旧唯一键，`file_address` NOT NULL，三个唯一键恢复原名；16:18:57 Platform Owner 备份 `before-owner-removal-20261022T161857Z.sql`；16:19:07 删 `t_file_upload` 三个 Owner 列与 `t_file_owner_ref`。final-check 7/7（`mor_platform` 62 张表）。删除后复验：上传（S3_TEST）→ 扫描未完成 409 → 下载 → 关联 C12 → CCT 行地址与哈希正确，O10 下载旧 MinIO 文件并写审计，内部接口 6 项，均通过；验收脚本中两处查询 `file_id` 的语句改为按列是否存在取口径。此后不能回滚到旧 CCT 或第二段之前的 Platform |
| 15:4x | 10 记录与通知 | 主 compose 已补 `PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL`、`PAYTECH_INSTANCE_ID=platform-support-test`，删 `FILES_ONBOARDING_ENABLED`（`compose.sh config` 确认）；已通知各会话新镜像与基线 |

## 9. 风险与需要主会话 / 用户决定的事项

1. **CCT / 客户侧会顺带上线 10-18 以来的未发布改动（1.3 节）。**
   - **内容：** client-master Processor 重构、client-console-bff 重组、Web 字段错误标红，以及合同库更新。
   - **不需要额外步骤：** 不需要 DDL、数据或配置。
   - **风险：** 第一次在 mor-test 运行，属于行为回归风险。
   - **选择：**
     - (a) 一并发布：本包现状；
     - (b) 先单独发一版 `1d39cd94` 的 CCT / 客户侧并验收，再发第二段。
2. **Q16 偏差（第 2 节④）：** 没有“保留旧入口”的过渡 Platform，只能一次换五个服务。客户入驻文件链路与 O10 约有 2–4 分钟不可用。删除 DDL 之前仍可整体回滚。
3. **验收数据：**
   - **新建合成账号与草稿申请：** 是否接受？已有入驻测试账号的口令没有留存，无法复用。
   - **验证邮件不到时：** 是否允许按 V2 做法铸造验证挑战（写 `mor_platform`）？
   - **C14 提交：** 是否做？会留下 compliance 审核任务，只能运营退回或拒绝。
4. **client-console-bff 现网镜像已丢失：** 回滚用重建的 `2ff7bff9`，JAR 与现网 0 差异，但镜像 ID 不同。客户侧三件现网用 `:development` tag。本包已全部钉 sha256；建议今后的发布也钉。
5. **`PAYTECH_INSTANCE_ID=platform-support-test`：** 取值按 mor-test 现有命名推定，请确认。工作流 MQ 回调的 `event-instance-id` 会随之由 `main` 变为该值（目前没有订阅方）。
6. **主 compose：** 本包不改 `test-environment/compose.applications.yaml`。建议在阶段 7 完成后另做一个补丁：Platform 加 DUAL 与 `PAYTECH_INSTANCE_ID`、删 `FILES_ONBOARDING_ENABLED`。否则下次有人用主 compose 重建 Platform，会退回 OFF，客户入驻与 O10 都会因内部文件接口 401 失效。
