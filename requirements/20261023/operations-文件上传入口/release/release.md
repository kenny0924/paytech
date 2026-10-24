# operations：运营控制台文件上传入口 · mor-test 发布包

阶段 6（2026-10-24 06:05—06:40Z）。本阶段只做了以下几类操作：
- 只读核对：`docker inspect`；用临时容器从**镜像**取出 JAR / dist / health-probe；`Config.Env` 只比较、不输出值；`mor_platform` 只读 SELECT；`mysqldump --single-transaction` 只读备份；匿名 CORS 预检公开桶。
- 构建：在新建的干净 worktree 里构建 3 个镜像。
- 离线演练：在断网的一次性 MySQL 副本上演练全部 SQL，并用候选 Platform 的 Gateway 快照编译器编译（休眠 / 激活 / 停用 / 删除四种状态）。
- 编写发布包与脚本，并全部干跑。

**本阶段没有做的事：** 没有停 / 起 / 替换 mor-test 任何容器；没有对 mor-test 执行写 SQL；没有登录，也没有调用任何接口；没有提交；没有 `mvn install`；没有改 `:development` tag；没有 `docker cp` 进运行容器。

阶段 7 须用户授权，由主会话按第 6 节执行，结果写第 10 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像（3 个）：** platform-support-service、operations-console-bff、operations-console-web。
- **注册 DML：** 块 136（权限 2、授权 2、菜单 1 + 按钮 1、路由 5、请求头 18、查询参数 5、Guard 30，共 64 行）。先注册为休眠状态，三个镜像都换完后再激活。
- **数据（经真实运营接口，不写 SQL）：**
  - 写入规则 `(operations-console-bff, PUBLIC) → S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT / 900 秒`；
  - `operations-console` 新增 69 个 key（`file.upload.*` 67 个 + `nav.resource.OPERATIONS_FILE_UPLOAD`、`…_CREATE`）及 zh-CN 翻译，并发布 #6。
- **数据库 DDL：** 无。**环境变量：** 不新增、不删除（第 3.3 节）。
- **不动：** Gateway、Nginx、MinIO、AWS 桶配置、Nacos、主 compose、其余容器；现有写入规则与已有 8525 个 key 的英文与翻译（第 3.5 节）。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 本需求源码 | 开发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-fileupload`（detached `a73e7398`，改动未提交）。<br>补丁 = `git diff --binary a73e7398` + 未跟踪文件（`git ls-files -o --exclude-standard`，排除截图 `*.png`；没有 node_modules / target / dist）。补丁用独立 `GIT_INDEX_FILE` 生成，没有碰共享 index。<br>共 **72 个文件**（新增 38、修改 34），补丁 SHA-256 `4ad8dd53d581f8a77732ebafb23625a066515a18801d825715d4ea45df538eb9`（[清单](./evidence/source-diff-a73e7398.txt)）。<br>范围：Platform 30（api 2、core 6、app 2、file-document 16、测试 2、README 1、initial-data 1）、ops-bff 17（生产 14 + 测试 3）、ops-web 13、card / client-master 测试 Mock 各 1、需求目录 10（`requirement` / `technique` / `delivery`、`db/`、`verification/` 脚本，不进镜像） |
| 构建 worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-fileupload-rel`（`git worktree add --detach … a73e7398`）。<br>先在纯 `a73e7398` 上构建基线（第 1.2 节），再 `git apply --binary` 上述补丁。<br>与开发目录 `diff -rq`（排除 `node_modules`、`target`、`dist`、`i18n-out`）：platform-support、operations-console-bff、operations-console-web、card、client-master 五个目录**完全相同** |
| Java | JDK 25；`./mvnw -B -o -q -DskipTests -Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository -pl platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`，没有 install。<br>镜像用 `test-environment/java/Dockerfile`（与主工作区逐字节相同）；`health-probe/` 经临时容器从运行镜像取出（两个运行镜像的 health-probe 相同，候选镜像内与之逐字节相同） |
| Web | 依次执行：`npm ci` → `tsc --noEmit` → `npm test`（**1498 / 1498**）→ `npm run build`（dist 219 个文件）→ `operations-console-web/Dockerfile`。<br>`deploy/nginx.conf` 本需求未改，与现网逐字节相同 |
| 镜像核对 | 候选镜像内 `/app/application.jar` = 构建产物、`health-probe/` = 运行镜像；ops-web 镜像内 `dist/` = 构建产物、`nginx.conf` = 现网。<br>三个镜像的 Entrypoint / Cmd / User / WorkingDir / ExposedPorts / Healthcheck / StopSignal / Volumes / Labels 与对应运行镜像相同；Env 逐键比较（只比较是否一致，不输出值）：6 / 6、6 / 6、7 / 7 键全部相同。[precheck_readonly.py](./acceptance/precheck_readonly.py) 会再复核一次 |

### 1.2 基线证明（不加本需求，与运行容器比较）

比较方法同前置需求（[evidence/jarcmp.py](./evidence/jarcmp.py)）：
- `BOOT-INF/classes` 与嵌套的 `paytech-*` JAR 逐条目比较 CRC；第三方 JAR 整包比较 SHA-256；
- 排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引和目录项；
- 运行 JAR 经临时容器从运行镜像取出，SHA-256 与 `docker exec … sha256sum /app/application.jar` 一致；
- Web 逐字节比较 `dist/` 与 `nginx.conf`。

| 容器 | 运行镜像（起始时间） | `a73e7398` 基线构建 | 结论 |
|---|---|---|---|
| platform-support-service | `4803299926…`（i18n-20261023b，10-23 08:45:30Z 起，JAR `e45c406f1d313df2`） | 3014 项，只差 **1 项**：`paytech-messaging!catalog/events.yaml`（[明细](./evidence/jar-diff-live-vs-a73e7398-platform-support-service.txt)）。<br>该项来自已提交的 `98210e0b`（billing 支付事实自动登记）：事件目录新增 `OrderPaymentSucceeded`（acquiring → billing）的说明条目。这是随 Foundation JAR 打包的事件目录文档，Platform 既不发布也不消费该事件 | 现网 = `ffdf872e` + 多语言管理（已提交为 `ed361c26`）。`ffdf872e..a73e7398` 之间碰到 platform-support / foundation 的提交只有 `ed361c26` 与 `98210e0b`，前者即现网内容，后者只带来上述 1 项文档。**没有不能由已提交改动解释的差异** |
| operations-console-bff | `3751638e…`（opsi18n-20261023a，10-23 20:04:28Z 起，JAR `3c7e6f647261115c`） | 2599 项：**0 差异**（[明细](./evidence/jar-diff-live-vs-a73e7398-operations-console-bff.txt)） | 现网 = `93746677`；`93746677..a73e7398` 没有 BFF 改动 |
| operations-console-web | `4c1f1ec3…`（opsi18n-20261024b，10-23 21:50:15Z 起） | `dist/` **214 个文件逐字节一致**，`nginx.conf` 一致 | 现网 = `a73e7398`，没有未提交的现网改动需要带上 |

### 1.3 候选镜像（tag `fileupload-20261024a`）与差异

**platform-support-service**
- 候选镜像：`sha256:4859efd78dd92de7c8f97dca29ef7894e7d2e1115bd72f471960553375075d17`，JAR `be0abf79fc3ca9eb`。
- 相对基线（= 现网 + 已提交改动）：**+7、~26**，全部在 `platform-support-api / core / file-document` 与 app 的两个文件接口类；Foundation、其他模块和第三方 JAR 0 改动（[明细](./evidence/jar-diff-candidate-platform-support-service.txt)）。具体改动：
  - ① `InternalFileApi` 新增两个只读方法，新增 `FileUploadConstraintsResponseBody`；
  - ② `QueryFileUploadConstraintsProcessor`、`ListCallerFilesProcessor` 及它们的请求 / 响应；
  - ③ `FileUploadService.constraints`（`UploadModels$Constraints`；`admit` 改为与它共用读修订的逻辑，行为不变）；
  - ④ `StorageWriteTarget` 加命中路由与公开基础地址；`FileScanPolicy` 加 `revisionNo`（`FileUploadMapper.xml` 的 SELECT 多一列）；
  - ⑤ `FileRecordQuery` 加上传人，`FileRecordMapper.xml` 加等值条件与 `selectDescriptionPage`；`FileRecordService.listFiles`；`StoredFileView.of`（由 `describe` 抽出）。
  - `UploadModels` 其余嵌套类型的字节码变化，只来自同一外部类新增了嵌套 record（InnerClasses 属性），源码未改。
- 不改已有接口字段；没有 DDL。

**operations-console-bff**
- 候选镜像：`sha256:0bc674106d7b75f4857f5e299d6fdfeacd030c6e8514d8f92dadb4bc2f51a3ed`，JAR `f84caa925b7105b6`。
- 相对基线：**+13、~5**（[明细](./evidence/jar-diff-candidate-operations-console-bff.txt)）。具体改动：
  - 新增 `FileUploadController`、`PlatformPublicFileClient(Impl)`、3 个 Processor（列表、详情、完成上传复查）、请求 / 响应模型；
  - `ConfigurationManagementRequestConfiguration` 登记 `/file-upload/v1/**`（查询白名单、无 Body 命令拒绝 Body、`StartPublicFileUploadRequestBody` 严格解析）；
  - `application.yml` 的只记元数据日志前缀加 `/file-upload/v1/uploads`；
  - 依赖的 `paytech-platform-support-api` 随 Platform 同步（+1 类、`InternalFileApi` 变化）。

**operations-console-web**
- 候选镜像：`sha256:70d3fdfbb6be16ed48edd3883338c08264122b34432ad68246b938e3c10dffea`，dist 219 个文件。
- 新增「平台配置 → 文件上传」页（`OPERATIONS_FILE_UPLOAD`）：`FileUpload` chunk 与样式；直传函数与文件记录模型抽成共享 chunk（`directUpload`、`fileRecords`）；`useForm` 被拆成独立 chunk。
- 由于 Vite 产物名带内容哈希，多数文件名随之变化（[清单](./evidence/web-dist-diff-candidate.txt)）。
- 文案 `file.upload.*` 与两个 `nav.resource.*` 内置英文。依赖不变（`package.json` / lock 未改）。

**对其他消费方：** `InternalFileApi` 只新增方法，已发布的消费方（card、client-master、client-console-bff、ops-bff 现网版本）不调用新方法，不受影响。card、client-master 测试里实现该接口的 Mock 补了两个"意外调用"方法，只在测试模块。

## 2. 兼容性与执行顺序

| 组合 / 时点 | 结果 |
|---|---|
| 新 Platform + 旧 ops-bff / 旧 ops-web | 无影响：新接口只读、旧 BFF 不调用；上传、替换、文件记录、写入规则解析与现网行为相同（Platform 回归 52 例 + 新增 10 例，阶段 4） |
| 新 ops-bff + 旧 Platform | `/file-upload/v1/*` 调 Platform 新接口会得到 404。这些路由此时为 DISABLED，经 Gateway 不可达。**所以 Platform 先换** |
| 新 ops-web + 块 136 未激活 | 页面组件已在，但菜单与路由 DISABLED，导航不显示；其他页面不变 |
| 块 136 注册（休眠）在任意镜像组合下 | 无影响；离线编译：休眠态快照 574 → 579 条路由，生效仍 569（第 3.4 节） |
| 激活之后、写入规则建立之前 | 页面"已配置"：命中默认规则 `(*, PUBLIC)`，目标与精确规则相同（S3_TEST 公开桶 / PUBLIC_DOCUMENT）；限制提示显示 `routeSourceSystem = *`。这时上传同样可用。建立精确规则后改为命中 `operations-console-bff` |
| 语言包 #6 发布之前 | 选中文的用户在文件上传页看到内置英文（这 69 个 key 回退 en），其余页面不变 |
| 公开桶 CORS | 匿名预检：`Origin: http://localhost:28080`、`PUT`、`content-type, if-none-match` → 200，允许该 Origin、PUT、这两个头（与浏览器直传实际携带的签名头一致） |

**顺序（用户指定）：** ① 通知与预检 → ② Platform → ③ ops-bff → ④ ops-web → ⑤ SQL 注册 → 激活 → ⑥ 建写入规则 → ⑦ 导入并发布语言包 → ⑧ 验收 → ⑨ 记录与通知。

**不可用窗口：**
- Platform 重启约 15—60 秒（10-23 实测启动 12.4 秒，以 `--wait` 健康为准）。期间运营 / 客户控制台登录与授权（PLT-I02）、内部文件接口不可用；Gateway 沿用上一份快照。
- ops-bff 约 45 秒（运营控制台全部接口）。
- ops-web 约 10 秒。

## 3. 只读核对（阶段 6 已做）

### 3.1 一键预检

[`acceptance/precheck_readonly.py`](./acceptance/precheck_readonly.py) 于 06:26Z 执行：**PASS 51，FAIL 0**（[输出](./acceptance/precheck-readonly-20261024T0626Z.out)）。

| 组 | 结果 |
|---|---|
| 容器 | 3 个 `.Image` = [compose-rollback.yaml](./compose-rollback.yaml)，healthy、重启 0 |
| 候选 | 3 个镜像存在、= 候选覆盖文件、配置与运行镜像相同、两个 JAR 的 SHA-256 正确 |
| 环境 | 候选 / 回滚覆盖文件渲染后，与运行容器逐键、逐值相同（第 3.3 节） |
| SQL（只读） | [00-precheck.sql](./sql/00-precheck.sql) 的 9 个计数全部为 0。<br>存在性：角色 510 PLATFORM_ADMIN（版本 48）、Target `operations-console-bff` ACTIVE、父菜单 562 ACTIVE。<br>写入目标：S3_TEST / `mor-test-platform-public-k7q2` 为 PUBLIC 桶，桶与实例 ACTIVE；PUBLIC_DOCUMENT 有唯一 ACTIVE 修订。<br>生效路由 569 |
| 写入规则 | `(*, PUBLIC)` → S3_TEST 公开桶 / PUBLIC_DOCUMENT；`(operations-console-bff, PRIVATE)` → MINIO / mor-platform-files / OPERATIONS_BFF_IMPORT（不变）；`(operations-console-bff, PUBLIC)` 尚不存在 |
| 多语言 | 当前发布 #5 READY（8525 key，默认 en），没有进行中的发布；只增不改核对见第 3.5 节 |

阶段 7 第 ⑦ 步之后用 `--after` 复跑（期望容器 = 候选、块 136 计数 2/2/2/5/18/5/30 且 ACTIVE、精确规则已建、69 个 key 已上线）。

### 3.2 占用核对（mor-test 备份副本，与 3.1 的现网只读 SELECT 结论相同）

块 136 在 mor-test 上全部未占用：
- ID 段 `900000000000136001—136999` 在 7 张表都为 0 行；
- 权限码 `FILE_UPLOAD_READ` / `FILE_UPLOAD_CREATE` 与资源类型 `FILE_UPLOAD` 未用；
- 资源码 `OPERATIONS_FILE_UPLOAD` / `OPERATIONS_FILE_UPLOAD_CREATE` 与前端路由 `/file-upload` 未用；
- 路由码 `operations-file-upload-*` 与外部路径 `/api/operations/file-upload/*` 未用；
- 「平台配置」（562）下的菜单排序为 10、20、30、40、50、60、70、80、85（文件记录）、90（卡 BIN 库），**86 无冲突**。

### 3.3 环境变量

- 主 compose（`test-environment/compose.applications.yaml`）已含这 3 个服务的全部现行键；候选 / 回滚文件只钉镜像。
- 扫描 93 个 `requirements/**/release/**/compose-*.yaml`：
  - ops-bff 历史上出现过的 4 个键，渲染结果全部包含；
  - Platform 历史上的 19 个键中，10 个不在渲染结果里：`FILES_ONBOARDING_*` 8 个（已退役，README 规定残留即启动失败）、`NOTIFICATION_HTTP_CALLERS_E2ESESEVENTS`、`WEBHOOK_ALLOWED_SCHEMES`。运行容器也没有这 10 个键，属于既往需求已移除的临时键，不应带回。
- `compose.sh -f <文件> config --format json` 渲染后与运行容器 `Config.Env` 逐键、逐值比较（只输出键名与是否一致）。compose 键 / 运行键：Platform 53 / 59、ops-bff 14 / 20、ops-web 0 / 7。**候选与回滚都是 0 缺、0 多、0 不同**。挂载：Platform 两个只读 bind（`test-ca.crt`、`platform-truststore.p12`）与运行容器相同，另两个服务无挂载。
- 本需求不新增变量。

### 3.4 离线编译网关快照

**方法：**
1. 用 `mysqldump --single-transaction` 只读导出 mor-test `mor_platform`（06:17Z，SHA-256 `4a2f478ac60696a3`，`~/Code-MoR-backups/20261024-fileupload-stage6/`，0700 / 0600）。
2. 导入 `--network none` 的一次性 `mysql:8.4.11@sha256:b3b90af2…` 容器（与 mor-test 同一镜像 ID、同一字符集 / 校对 / 时区参数）。
3. 每一步导出 6 张网关表，按生产 `GatewayConfigurationRepositoryImpl` 的方式组装 `LoadedConfiguration`。
4. 调用**候选 Platform JAR（`be0abf79fc3ca9eb`）内的** `GatewayConfigurationCompiler.compile`（classpath = 候选 JAR 解出的 `BOOT-INF/classes` 与 `BOOT-INF/lib/*`，JDK 25 单文件运行）。脚本：[evidence/snapshot/](./evidence/snapshot/)，做法同多语言管理 B-report。

**结果**（[完整输出](./evidence/offline-rehearsal.txt)）：

| 步骤 | 结果 |
|---|---|
| 基线（备份原样） | COMPILE OK：574 条路由，生效 569 |
| `10-registration-block136.sql`（休眠） | COMPILE OK：579 条，生效 569，本入口生效 0 |
| `20-activate-block136.sql` | 影响行数 5 / 2 / 2 / 1；COMPILE OK：579 条，**生效 574，本入口生效 5**（无 Method / Path 冲突） |
| `21-deactivate-block136.sql` | 5 / 2 / 2 / 1；COMPILE OK：579 / 569 |
| `90-rollback-block136.sql` | 30 / 5 / 18 / 5 / 2 / 2 / 2 / 1；COMPILE OK：574 / 569；00-precheck 复查为全 0 |

另用 [scripts/apply_sql.sh](./scripts/apply_sql.sh) 在同一备份的新副本上把 `reg-dry → reg → activate-dry → activate → deactivate-dry → deactivate → rollback-reg-dry → rollback-reg` 全部跑通（[输出](./evidence/apply-sql-rehearsal.txt)）。`*-dry` 后 postcheck 不变，证明 dry 不提交。两个一次性容器及其数据卷都已删除（`docker rm -f -v`）。

### 3.5 多语言：只增不改

- 输入 = 候选 worktree 的 `npm run i18n:export-source`：21 个命名空间、**8594 key**（[i18n-bundles/](./i18n-bundles/)，`operations-console.en.json` `2a893c937aca85cf`、`operations-console.zh-CN.json` `a1e208aea76072d2`）。
- 源码层面：`file.json` en / zh-CN 各 403 → 470（+67）、`nav.json` 各 188 → 190（+2）。已有 key **修改 0、删除 0**（与 `a73e7398` 逐 key 比较；diff 里 `file.storage.unavailable` 一行只是行尾逗号变化）。
- 线上层面（`precheck_readonly.py` 只读 SELECT）：
  - 线上 8525 个 ACTIVE key 全部在输入中（废弃 0）；
  - 英文基准、说明、命名空间逐条相同（基准变更 0）；
  - 线上 zh-CN 翻译逐条与输入相同（覆盖 0）；
  - 输入只多 69 个 `file.upload.*` 与 `nav.resource.OPERATIONS_FILE_UPLOAD*`；`file`、`nav` 命名空间已存在。
- 导入方式（[fu_bundles.py](./acceptance/fu_bundles.py)）：
  - key 导入必须是全集，新增 69 ≤ 每批 3000，一批完成；
  - 翻译导入只上传**仅含 69 个新 key** 的 zh-CN 文件（Platform 只写文件中出现的 key），已有翻译不经过导入；
  - 导入后再用 SQL 复核：已有 8525 条翻译的文案**与版本号**都不变。任何一项不符，脚本在写之前就停止。
- 离线 ICU 校验：用候选 JAR 内的 `IcuMessageValidator` 校验 8594 条英文与 8594 条中文，**0 问题**（[evidence/icu-validate.txt](./evidence/icu-validate.txt)）。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件（3 个服务） |
| [sql/](./sql/) | `00-precheck`（只读）、`10-registration-block136`（与 `db/platform-support/registration-block136.sql` 逐字节相同）、`20-activate-block136`、`21-deactivate-block136`、`90-rollback-block136`（按 ID 段删除）、`99-postcheck`（只读） |
| [scripts/apply_sql.sh](./scripts/apply_sql.sh) | SQL 执行包装：`backup`、各步与 `*-dry`（把 COMMIT 换成 ROLLBACK），输出写备份目录 |
| [i18n-bundles/](./i18n-bundles/) | 两个导出文件与 `SHA256SUMS`；发布后的 manifest 快照写 `i18n-bundles/published/` |
| [acceptance/fu7_common.py](./acceptance/fu7_common.py) | 公共件：import 前例 `platform-support-文件公开桶/release/acceptance/pb7_common.py`（登录、运营入口请求、合成人员、只读 SQL、对象读写、日志），改指本包常量 |
| [acceptance/precheck_readonly.py](./acceptance/precheck_readonly.py) | 只读一键预检（第 3.1 节；`--after` 为发布后复核） |
| [acceptance/setup_write_rule.py](./acceptance/setup_write_rule.py) | 第 ⑥ 步写入规则（写数据；`--rollback` 删除） |
| [acceptance/fu_bundles.py](./acceptance/fu_bundles.py) | 第 ⑦ 步语言包（写数据；import `operations-控制台多语言迁移/release/acceptance/opsi18n_common.py`） |
| [acceptance/stage7_browser.mjs](./acceptance/stage7_browser.mjs) + [stage7_api.py](./acceptance/stage7_api.py) | 第 ⑧ 步验收（改自本需求阶段 5 的 `verification/local-e2e/stage5_*`；结果 `stage7-result.json`，截图 `screens/`） |
| [evidence/](./evidence/) | 源码差异清单、JAR 差异（现网对基线、基线对候选）、dist 差异、离线编译与 SQL 演练输出、ICU 校验、比较与编译脚本 |

**干跑（阶段 6，没有调用任何接口）：**
- `fu_bundles.py --dry-run` 15 / 15；
- `setup_write_rule.py --dry-run` 2 / 2；
- `stage7_api.py --dry-run <命令>` 10 / 10 个子命令；
- `stage7_browser.mjs --dry-run` 通过（playwright-core 存在、管理员凭据可解析、9 段计划）；
- Python 全部能编译，`node --check` 通过；运行时不写 `__pycache__`（前例目录也没有新产生）。

## 5. 执行前提

1. 用户授权阶段 7（第 11 节清单）。
2. 第 12 节各项已裁决。
3. 构建 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-fileupload-rel` 与 3 个候选镜像保留到发布完成（镜像只按 sha256 引用）。开发目录若在阶段 6 之后又有改动（ops-web 由另一代理开发），本包作废，按第 1 节重建（补丁 SHA 会变化）。

## 6. 执行步骤（阶段 7，主会话执行）

在主工作区根目录执行（发布包所在目录如已随提交进入主工作区，把 `R` 改为主工作区下的同名目录）：

```
R="/Users/kenny/Desktop/Workspace/Code-MoR-fileupload/requirements/20261023/operations-文件上传入口/release"; A="$R/acceptance"
O="$R/compose-candidate.yaml"; RB="$R/compose-rollback.yaml"; BK="$HOME/Code-MoR-backups/$(date -u +%Y%m%dT%H%M%SZ)-fileupload-mor-test"
up() { ./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
chk() { for s in "$@"; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done; }
sqlx() { "$R/scripts/apply_sql.sh" "$BK" "$@"; }
```

任一步不符合预期就停，按第 7 节回滚。

1. **① 协调与预检：**
   - 通知 code-mor-db、code-mor-ae（第 9 节）本窗口要替换 platform-support-service、operations-console-bff、operations-console-web；期间如有人替换了这三个服务，本包作废，重做第 1 节。
   - `python3 -I "$A/precheck_readonly.py"` → `FAIL 0`；
   - `sqlx backup`（记下 SHA-256）→ `sqlx precheck`（9 个计数为 0，记下角色 510 版本与生效路由数）。
2. **② Platform：**
   - `T2=$(date -u +%FT%TZ)`；`up platform-support-service`；
   - `chk platform-support-service` 为 `sha256:4859efd7… healthy rc=0`；
   - `docker logs --since "$T2" mor-test-platform-support-service-1 2>&1 | grep -c ' ERROR '` 为 0；
   - `docker exec mor-test-platform-support-service-1 printenv | cut -d= -f1 | sort` 与替换前键名一致；
   - Gateway 快照刷新正常：`docker logs --since "$T2" mor-test-api-gateway-1 2>&1 | grep -c CONTROL_PLANE_UNAVAILABLE` 为 0。
3. **③ ops-bff：** `up operations-console-bff`；`chk operations-console-bff` 为 `sha256:0bc67410… healthy rc=0`，ERROR 0，键名一致。
4. **④ ops-web：** `up operations-console-web`；`chk operations-console-web` 为 `sha256:70d3fdfb… healthy rc=0`。
5. **⑤ SQL 注册与激活：**
   - `sqlx reg-dry`（无报错）→ `sqlx reg` → `sqlx postcheck`（2/2/2/5/18/5/30，REVOKED / DISABLED）；
   - 等 10 秒，Gateway 无 `CONTROL_PLANE_UNAVAILABLE`；
   - `sqlx activate-dry`（5 / 2 / 2 / 1）→ `sqlx activate` → `sqlx postcheck`（全部 ACTIVE，角色版本 +1，生效路由 +5）；
   - 等 10 秒，Gateway 无 `CONTROL_PLANE_UNAVAILABLE`，快照路由数 +5。
6. **⑥ 写入规则：** `python3 -I "$A/setup_write_rule.py" --dry-run` → `python3 -I "$A/setup_write_rule.py"`。预期：新建 `(operations-console-bff, PUBLIC)`，其余规则逐条不变；120 秒内 `/file-upload/v1/constraints` 为 configured，命中 `operations-console-bff`，S3_TEST 公开桶、PUBLIC_DOCUMENT、pdf / png / jpeg、20971520 字节、900 秒。
7. **⑦ 语言包：** `python3 -I "$A/fu_bundles.py" --dry-run`（15/15）→ `python3 -I "$A/fu_bundles.py"`。预期：
   - 只增不改核对通过；
   - key 预览新增 69 / 变更 0 / 废弃 0 → 确认 → 再预览全部不变；
   - zh-CN 写入 69、不变 0；已有 8525 条翻译文案与版本都不变；
   - 预检相对 #5 新增 69 → 发布 **#6** READY 且为当前，语言包 42/42 在 MINIO / mor-platform-public；
   - 匿名 manifest #6，42 个语言包内容 = 输入、CORS 正确。
   - 完成后 `python3 -I "$A/precheck_readonly.py" --after` → `FAIL 0`。
8. **⑧ 验收：** `cd "$A" && node stage7_browser.mjs --dry-run && node stage7_browser.mjs`（AC-01—AC-08 与 ZH，见第 8 节）；`python3 -I stage7_api.py runtime "$T2"`（3 个服务为候选、healthy，自 ② 起 ERROR 0）。
9. **⑨ 记录与通知：**
   - 填第 10 节；`delivery.md` 补环境操作记录；
   - 通知 code-mor-db、code-mor-ae：新镜像 ID 与源码基线（今后这三个服务的候选须包含本需求，直到提交）；语言包当前为 #6（8594 key）；块 136 已用；下一个新块从 137 起。

## 7. 回滚

`upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }`

| 范围 | 做法 |
|---|---|
| 写入规则（第 ⑥ 步） | `python3 -I "$A/setup_write_rule.py" --rollback`：删除 `(operations-console-bff, PUBLIC)`，回落到 `(*, PUBLIC)`（目标相同）。已上传的公开文件不受影响（地址不变）。通常**不需要回滚** |
| 入口停用 | `sqlx deactivate-dry` → `sqlx deactivate`：5 条路由、菜单与按钮 DISABLED，授权 REVOKED，角色版本 +1。注册行保留；离线编译 579 / 569 通过 |
| 彻底移除注册行 | 先停用，再 `sqlx rollback-reg-dry` → `sqlx rollback-reg`（64 行，按 ID 段删除）；离线编译 574 / 569 通过 |
| ops-web | `upr operations-console-web`（先于 BFF 与 Platform）。旧前端没有文件上传页；入口未停用时菜单项点进去为空白占位，所以回滚 ops-web 前先停用入口 |
| ops-bff | `upr operations-console-bff`。旧 BFF 没有 `/file-upload/v1`；入口未停用时 Gateway 转发会得到 404 |
| Platform | 最后 `upr platform-support-service`。旧 Platform 没有两个只读接口；新 BFF 仍在时限制提示与最近上传会报错，所以须在 BFF 回滚之后 |
| 语言包 | **一般不回滚**：#6 只多 69 个 key，旧前端不读。确需切回：`POST /api/operations/localization-admin/v1/applications/operations-console/current-release`，正文 `{"releaseNo": 5, "reason": "运营控制台文件上传入口回滚"}`（或在"多语言管理 → 发布记录"选 #5"切回此版本"）。key 建议保留（Platform 没有删除 key，只能靠导入把它们标为废弃） |
| 已上传的验收文件 | 保留：本需求不提供删除；公开文件按设计任何人可读（第 11 节第 3 项） |

## 8. mor-test 验收（第 ⑧ 步）

[`stage7_browser.mjs`](./acceptance/stage7_browser.mjs) 用无头 Chromium + [`stage7_api.py`](./acceptance/stage7_api.py) 执行。入口 `http://localhost:28080`，严格证书（对象存储为 AWS，不忽略证书错误）。

| 项 | 检查 |
|---|---|
| AC-01 | 管理员上传 1×1 PNG：检查通过，展示三个地址；`publicUrl` 以 `https://mor-test-platform-public-k7q2.s3.ap-southeast-1.amazonaws.com/` 开头；不带凭据 GET 返回 200，摘要 = 原字节 |
| AC-06 | 内容不是 PNG 的 `.png`：完成上传 422，页面显示未通过、没有地址；最近上传里没有该文件 |
| AC-02 | svg 与超过 20 MiB：页面预校验拦截，不调开始上传；脚本直接调用均为 422，最近上传数量不变 |
| AC-05 | 最近上传包含 AC-01 的文件（上传人"Me"、检查通过）；"只看我的"返回 200；"File Record"链接跳到 `/file-records?file=<id>` 详情 |
| ZH | `?lang=zh-CN`：菜单与页面为"文件上传""最近上传"（依赖第 ⑦ 步的 #6） |
| AC-08 | 文件记录、存储管理（写入规则页显示 ops-bff 两条规则与 AWS 公开桶）、卡 BIN 库页面加载无 4xx / 5xx；`(operations-console-bff, PRIVATE)` 规则不变（只读 SQL） |
| AC-04 | 合成人员两名：<br>① 只有 `FILE_UPLOAD_READ`：接口可读（constraints / files 200），开始上传 403，页面因父菜单 562 需要 534 而不可达；<br>② 加上 534：页面只读形态，有限制提示与最近上传、没有上传区，开始上传 403。<br>验收后由脚本删除这两个人员与角色 |
| AC-03 | 已配置并命中精确规则（`routeSourceSystem = operations-console-bff`、S3_TEST / PUBLIC_DOCUMENT），上传按钮可用。**"未配置"在 mor-test 不构造**：存在默认规则 `(*, PUBLIC)`，删除精确规则只会回落；构造需要同时删除默认公开规则，会影响其他调用方。这一场景由阶段 5 本机验收与 Platform / BFF auto 测试覆盖 |
| AC-07 | 自验收开始起 `docker logs mor-test-operations-console-bff-1` 不含 `X-Amz-Signature` / `X-Amz-Credential` / `X-Amz-Algorithm` |

判定：各项 PASS；浏览器 4xx / 5xx 只允许脚本登记的预期项（AC-02 / AC-04 / AC-06）；控制台错误 0。

**本机已覆盖、mor-test 不重复：**
- Platform：新增 10 例 + 文件回归 52 例；
- ops-bff：全模块 2359 例，以及完成上传复查用例；
- ops-web：1498 例；
- 阶段 5 本机端到端 AC-01—AC-08（含未配置），截图见 `verification/local-e2e/screenshots/`。

## 9. 需要通知的会话

| 会话 | 原因 |
|---|---|
| code-mor-db | 运营控制台多语言的负责会话：本窗口替换 ops-bff / ops-web（其最新镜像 `3751638e…` / `4c1f1ec3…`）与 Platform（其 `4803299926…`）；语言包 #5 → #6（只新增 69 个 key）；块 136 已登记给本需求 |
| code-mor-ae | 同一窗口可能替换这三个服务中的任一个（据任务卡）；需确认窗口内不替换，之后的候选须包含本需求 |

## 10. 执行记录（阶段 7 填写）

2026-10-24（UTC）主会话 code-mor-42 执行，用户“继续”授权第 11 节清单。

| 步 | 时间 | 结果 |
|---|---|---|
| ① | 08:01 | 开发目录与构建 worktree 源码逐文件一致（发布包有效）；现网 3 个镜像仍为回滚目标；通知 code-mor-db（回复：窗口内不动这三个服务、不发语言包）；code-mor-ae 已不在线。`precheck_readonly.py` 51/51；备份 `~/Code-MoR-backups/20261024T080159Z-fileupload-mor-test/mor_platform-full.sql`（SHA-256 `8d66a99e965b71d558f35041b7ca5f1ec655fd330b9a6a26e7e48cba995c4d36`）；SQL 预检 9 个计数为 0，角色 510 版本 48，生效路由 569 |
| ② | 08:02:19 | platform → `sha256:4859efd7…` healthy rc=0，ERROR 0，Env 键名一致。Gateway 在 08:02:26—51 记 6 次 `CONTROL_PLANE_UNAVAILABLE`，均在 Platform 重启窗口内（08:02:54 启动完成），之后快照刷新全部成功（569） |
| ③ | 08:04 | ops-bff → `sha256:0bc67410…` healthy rc=0，ERROR 0，Env 键名一致 |
| ④ | 08:04 | ops-web → `sha256:70d3fdfb…` healthy rc=0，Env 键名一致，入口 200 |
| ⑤ | 08:05 | `reg-dry` → `reg` → postcheck 2/2/2/5/18/5/30（REVOKED / DISABLED），快照 569、无错误；`activate-dry`（5/2/2/1）→ `activate` → 全部 ACTIVE，角色 510 版本 49，生效路由 574，快照 574、无错误 |
| ⑥ | 08:06 | 新建 `(operations-console-bff, PUBLIC)` → S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT / 900 秒；其他 6 条规则不变；constraints 立即命中精确规则。5/5（`~/Code-MoR-backups/fileupload-stage7/write-rule-2026-10-24T080608Z.json`） |
| ⑦ | 08:06 | key 预览新增 69 / 变更 0 / 废弃 0 → 导入（keySetRevision 6）→ 复预览全部不变；zh-CN 写入 69；已有 8525 条翻译文案与版本不变；发布 **#6** 为当前，42 个语言包匿名可读、内容 = 输入、CORS 正确。118/118（`fu-bundles-2026-10-24T080621Z.json`）；`precheck_readonly.py --after` 49/49 |
| ⑧ | 08:09—08:11 | AC-01、AC-06、AC-02、AC-05、ZH、AC-08、AC-04、AC-03（只核对已配置）、AC-07 全部 PASS（[stage7-result.json](./acceptance/stage7-result.json)，截图 `acceptance/screens/`）。浏览器 4xx：AC-06 的 422（预期）；第二名合成人员登录 1 次 429（登录接口既有限流，连续登录多名人员触发，脚本 30 秒后重试成功，与本需求无关）。合成人员 2 名与角色已删除（204）。`stage7_api.py runtime`：3 个服务为候选、healthy，自 ② 起 ERROR 0 |

留存：AC-01 文件 `239661523461554176`（`stage7-logo-20261024080910.png`，69 B），公开地址 `https://mor-test-platform-public-k7q2.s3.ap-southeast-1.amazonaws.com/dc18c12d-2a9b-41b9-9942-7d154b94b829` 匿名 200。AC-06 伪装文件完成时被拒，无文件记录。

## 11. 阶段 7 授权清单

1. **替换 mor-test 镜像 3 个**：platform-support-service → `4859efd7…`、operations-console-bff → `0bc67410…`、operations-console-web → `70d3fdfb…`（顺序 Platform → ops-bff → ops-web；含约 1 分钟 Platform 不可用窗口）。
2. **mor-test `mor_platform` 写 SQL**：备份后执行块 136 注册（64 行，休眠）与激活（路由 5、资源 2、授权 2、角色 510 版本 +1）；必要时执行停用 / 删除。
3. **经运营接口写数据**：
   - 新建写入规则 `(operations-console-bff, PUBLIC)`；
   - `operations-console` key 导入（新增 69）、zh-CN 翻译导入（69）、发布 #6；会留下 2 个私有导入文件记录（KEY_IMPORT、TRANSLATION_IMPORT）。
4. **验收写入**：
   - 向 AWS 公开桶上传验收文件：AC-01 的 PNG 保留且匿名可读；AC-06 的伪装文件完成时被拒，对象可能留在桶里，但没有文件记录、地址未公开；AC-02、AC-04 的开始上传被拒，不产生上传；
   - 新建并删除 2 个合成人员与角色。
5. **通知** code-mor-db、code-mor-ae。
6. **不包括**：提交、推送、改 `:development` tag、改 Gateway / Nginx / 主 compose、删除任何已有写入规则或 key。

## 12. 需要主会话裁决

1. **AC-03 在 mor-test 的取舍。** 建议只核对"已配置 + 命中精确规则"，"未配置"以本机与 auto 测试为证。备选：临时删除 `(*, PUBLIC)` 构造未配置——会影响所有依赖默认公开规则的调用方，不建议。
2. **验收文件留在公开桶。** AC-01 的 1×1 PNG 匿名可读、无删除入口（需求"不做删除"）。建议保留并在第 10 节记下文件 ID；如需清理，只能另走 SQL + 存储删除（前例公开桶阶段 7 的做法），不在本次授权内。
3. **激活与建规则之间的窗口。** 建议按第 6 节连续执行（几秒）；窗口内命中 `(*, PUBLIC)`，目标相同，可以接受。
4. **验收后语言包缓存（只报告）。** 新 ops-web 的本地缓存会在后台核对到 #6 后替换；已打开页面的用户刷新即可。
