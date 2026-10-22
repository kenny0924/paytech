# 卡 BIN-C 报告：operations-console-bff 与 operations-console-web

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin-ops`（detached，基于 `4163f9f9`；任务卡写的 `/Users/kenny/Desktop/Workspace/Code-MoR-bin-ops` 不存在，`git worktree list` 中本卡工作目录位于主工作区下，见“偏离与裁决” D1）。未提交、未推送、未执行任何 SQL、未修改任何 `.pen` 文件（Pencil 只用 `Get` / `TakeScreenshot` 读取 5 张画板）。

## 1. 开工自查

- 已读：`AGENTS.md`、`00-common.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（第 3.3、4 节 BFF / API 规则）、`agent-docs/examples/WRITE_API_EXAMPLE.md`（含第 11 节）、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、本需求 `requirement.md`、`technique.md`（第 2、3 节，重点 3.5.1 / 3.5.3 / 3.5.7 / 3.7）、`db/platform-support/dml.sql` 第 3 段（块 132 路由码、权限、按钮）、`tasks/A-card.md`；原型 5 张画板文字与截图。
- 所属：BFF 简单转发，不建 Processor / Context（technique 3.5.7“简单转发，不建 Processor；B6、B7 直接委托 Platform Client Port”）。Controller `CardBinLibraryController` → 本地端口 `CardBinLibraryClient` / `BinLibraryFileClient` → `*Impl` → Owner `@HttpExchange` 代理。
- 本地原子写入：无（BFF 无数据库、无事务）。事务外步骤：全部是对 Card / Platform 的单次 HTTP 调用；B7 为“完成上传 + 查询文件元数据”两次 Platform 只读/幂等调用（取扫描状态）。

## 2. 改动文件

**paytech-card-api**（与卡 BIN-A 同名同包；已在本卡末尾按 BIN-A 工作目录 `Code-MoR-bin` 的当前版本逐字对齐，见 D2）
- `card/api/CardApiConstants.java`：`ACTOR_HEADER`、`OPERATIONS_CONSOLE_BFF`
- `card/api/binlibrary/CardBinLibraryApi.java`、`request/{CreateBinLibraryVersion,ActivateBinLibraryVersion}RequestBody.java`、`response/{BinLibraryVersion,BinImportError,BinLibraryActivation,BinLookup}ResponseBody.java`
- `card/api/enums/{BinLibraryVersionStatus,BinLibraryActivationType}.java`

**operations-console-bff**
- `pom.xml`（dependencyManagement）、`paytech-operations-console-bff-app/pom.xml`：加 `paytech-card-api`
- 新 `configuration/CardClientConfiguration.java`：Nacos 发现 `card-service`（无 context path）、auto 测试 loopback 直连 `operations.card-test-base-url`、连接 2 秒 / 读取 4 秒、禁止重定向、严格 Problem + 422 字段路径 + Owner detail、内部 Token、超时 504 / 不可达 503
- 新 `configuration/CardBinLibraryRequestConfiguration.java`：`/card-admin/**` 三个写请求体严格解析（未知字段 / 重复键 / 标量强转拒绝，64 KiB 上限），无 Body 命令与查询带 Body 拒绝
- `configuration/PlatformClientConfiguration.java`：新增 `InternalFileApi` 代理（登记 `OnboardingFileErrorCode`），只有该代理带 `X-Caller-Service: operations-console-bff`；抽出 `platformProxy(RestClient, …)` 重载
- 新 `ext/rest/CardBinLibraryController.java`（B1—B9）
- 新 `integration/CardBinLibraryClient.java`、`integration/BinLibraryFileClient.java`、`integration/impl/CardBinLibraryClientImpl.java`、`integration/impl/BinLibraryFileClientImpl.java`
- 新 `service/model/binlibrary/request/StartBinLibraryUploadRequestBody.java`、`service/model/binlibrary/response/BinLibraryUploadCompletionResponseBody.java`
- `application.yml`：`/card-admin/v1/bin-library/uploads` 只记元数据日志（响应含临时 PUT 授权）
- 测试：新 `auto/CardBinLibraryBrowserAutoTest.java`、`test/simulator/CardOwnerSimulator.java`、`resources/card-bin-library-boundary.json`；改 `OperationsBffHttpTestBase`、`OperationsBffTestConfiguration`（注册 Card Simulator）、`OperationsConsoleBffApplicationAutoTest`（9 条路由与元数据前缀登记）

**operations-console-web**
- 新 `src/card/binLibrary.ts`（路径、按钮、解码、动作 / 进度、BIN 输入、CSV 本地预检）、`binLibrary.test.ts`、`useBinLibraryCommand.ts`、`directUpload.ts`（XHR 直传 + 进度）、`BinLibraryModals.tsx`（导入三步对话框、激活 / 回切确认）、`binLibrary.css`
- 新 `src/pages/CardBinLibrary.tsx`
- 导航：`navigation/pageCapabilities.ts`（`OPERATIONS_CARD_BIN_LIBRARY` + 按钮 `OPERATIONS_CARD_BIN_LIBRARY_MANAGE`）、`navigation/ConsolePage.tsx`、`navigation/menu.test.ts`（新用例）、`configuration/configurationModel.test.ts`（页面数 60→61）
- `commercial/capabilityConditions.ts`：`CARD_BRAND` 补 DINERS（8 个）、`FUNDING_TYPE` 补 CHARGE（含中文“签账”）、`ISSUER_REGION` 改为 GCC / MEA / EUROPE / NORTH_AMERICA / LATAM / APAC（去掉 DOMESTIC / INTERNATIONAL，中文名同步）；`capabilityConditions.test.ts` 新用例

## 3. 对应 AC

- AC-12（运营控制台）：版本列表（状态筛选、分页、空状态“还没有导入过版本”）、导入（本地表头预检、前 3 行预览、示例模板、直传进度、失败重试、创建版本后选中新版本）、激活 / 回切（号段数对比、影响说明、二次勾选、409“生效版本已被他人修改，请刷新后重试”）、单个 BIN 查询（6～8 位，非数字 / 超过 8 位即时提示且不发送，未识别显示“未识别（版本 vN）”）、校验中进度（3 秒轮询，离开 / 切换停止）、校验失败横幅 + 错误明细分页、激活历史。浏览器验收按卡片留给阶段 5。
- AC-11（BFF 侧）：上传开始响应的 PUT 授权不入日志（测试以输出捕获断言）；页面只按 BIN 查询，前端拒绝 >8 位输入。
- technique 3.5.8 前端建议值。

## 4. SQL 与备份

无（本卡无本机 SQL）。

## 5. 测试命令与结果

- `JAVA_HOME=jdk-25 ./mvnw -o -Dmaven.repo.local=~/.m3/repository -pl operations-console-bff/paytech-operations-console-bff-test -am test`：**2154 通过，0 失败，0 错误，0 跳过**（其中新 `CardBinLibraryBrowserAutoTest` 28 例：11 行边界 × 转发 / 授权拒绝 + 上传开始、上传完成、上传 Owner 故障、业务拒绝 400 / 404 / 409、依赖故障 503 / 502、本地参数拒绝）。BFF 测试不连数据库。
- 全 reactor `./mvnw -o … test-compile`：通过（只编译，未跑测试）。
- `operations-console-web`：`npm test` **1299 通过 / 0 失败**；`npm run build`（含 `tsc --noEmit`）通过（仅既有 chunk 体积告警）。构建时临时把主工作区 `operations-console-web/node_modules` 软链到本工作目录，结束后已删除软链；`dist/` 为 gitignore。

## 6. 偏离与裁决

- **D1 工作目录**：卡片写 `/Users/kenny/Desktop/Workspace/Code-MoR-bin-ops`，实际 worktree 为 `/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin-ops`（同一基线 `4163f9f9`），只在此目录改动。
- **D2 Card API 以 BIN-A 为准**：开工时向 impl-bin-card 发了命名提案，BIN-A 采纳了包名 / 类名 / 方法名 / 字段名；差异在 BIN-A 的 `versionNo` 路径与 Query 用 `String`（`[1-9][0-9]{0,8}`）、DTO 用基本类型与 `Instant` 时间。已把 BIN-A 当前 `binlibrary/`、两个枚举与 `CardApiConstants` 逐字复制到本目录（`diff -r` 一致），BFF 实现按其签名适配。BIN-A 若之后再改合同，合并时以 BIN-A 为准、BFF Impl 需随之核对（只涉及 `CardBinLibraryClientImpl` 4 处参数转换）。
- **D3 fileAddress 由 BFF 组装**：technique 3.5.3 依赖“文件第二段”在完成回执带 `fileAddress`，本基线的 `FileUploadCompletionResponseBody` 还没有该字段（第二段在 `Code-MoR-fs2-*` 开发中）。BFF 暂按 Platform 文件地址格式 `/files/v1/{fileId}` 组装，第二段合入后应改用 Owner 返回值（`BinLibraryFileClientImpl.completeUpload` 一处）。
- **D4 B7 扫描状态**：Platform 完成回执“不包含当前扫描状态”，B7 在完成后再调一次 `getFile` 取 `scanStatus`（同一 Owner 的只读调用，仍属简单转发，未建 Processor）。
- **D5 X-Caller-Service 范围**：文件第二段的 fs2-bff 草稿把该头加到所有 Platform 调用，会使既有 DataQuery 测试（断言无该头）失败；本卡只给 `InternalFileApi` 代理加该头。两段合并时需择一（若第二段改为全局，删除本卡的局部写法即可）。
- **D6 Platform 文件策略拒绝的状态码**：Platform 422 `FILE_POLICY_REJECTED` 经现有 BFF 错误映射外显为 400（保留 Code、不外露 Platform detail），与既有入驻审核文件接口一致；页面按 400 + Code 提示。
- **D7 Card Owner 错误码**：technique 3.5.1 规定不新增专有错误码，`CardClientConfiguration` 不登记 Owner 枚举（卡片“Owner 错误码登记”即为空集），开启 Owner detail 让 400 / 404 / 409 原因到达页面。
- **D8 menu.ts 未改**：`/card-bin-library` 只有根路由，`pageRoutePaths` 默认分支已覆盖；按必要性门禁不加冗余 case，只在 `menu.test.ts` 加注册断言。
- **D9 原型差异**：(a) 摘要卡“8 位 / 7 位 / 6 位号段数拆分”与“校验三阶段（扫描 / 校验 / 写入）”需要接口没有的字段，页面只显示号段总数与单一写入进度（总行数未知时不定进度“等待扫描 / 校验中”）；(b) 导入人 / 操作人显示 Account ID（接口只返回 ID，未做姓名查询）；(c) 版本详情按原型为右侧常驻面板（主从布局），激活历史放在详情面板下方分页表。
- **D10 严格请求解析**：为与其他运营写接口一致，新增 `CardBinLibraryRequestConfiguration`（页面多带 `operatorAccountId` 等未知字段直接 400），卡片未明确要求，属同范围补强。

## 7. 遗留项

- 阶段 5 浏览器验收（本机与 mor-test）未做；块 132 注册与 Gateway Snapshot 由卡 BIN-B / 发布阶段负责。
- D3、D5 需在文件第二段合入时收敛。
- `paytech/operations-console-bff/README.md` 未更新（不在本卡文档范围），建议发布阶段补一段“卡 BIN 库 `/card-admin/v1/bin-library` 9 个端点”说明。

## 8. WRITE_API_EXAMPLE 第 11 节核对

- API 与 Body：BFF 请求体 `StartBinLibraryUploadRequestBody` 为 class、中文字段注释、Hibernate Validator（.csv、≤255、正整数大小）；Card 请求体沿用 Card 合同；入口 `@Valid` + 严格解析器。✔
- Controller / Converter 仅适配：Controller 只做 Query 白名单、分页参数规范化与委托；无写 Processor（BFF 简单转发，规范允许）。✔
- Processor 查询 Repository / Context / Service / 本地事务 / Repository 回写 / 锁：不适用——BFF 无数据库、无本地写；锁、事务、业务防重归 Card（创建版本按 fileAddress 防重、激活 expectedActiveVersionNo）。
- 原请求比较：不适用于 BFF；B8 保留 Card 的 201 / 200（同文件重复提交）原样返回。
- Gateway 请求重放与业务防重区分：BFF 不读取、不转发 `Idempotency-Key`（测试断言下游无该头）；前端写命令按既有约定携带 Key 给 Gateway。✔
- View / DataObject：无；页面直接使用 Card 合同模型，B7 用 BFF 自有响应模型。✔
- 复用公共包、无生产 Mock：复用 `HttpServiceClientFactory`、`PagingRequestBody`、`OwnerDiscoveryInterceptor`、`ConfigurationValidationConverter`；测试替身只在 test 模块（Loopback Simulator，未用方法级 Stub）。✔
- 实际验证：见第 5 节。✔
