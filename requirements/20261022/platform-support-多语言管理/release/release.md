# platform-support：多语言管理（前端 UI 文案）· mor-test 发布包

阶段 6（2026-10-23 07:35—08:25Z）。本阶段只做了：只读核对（`docker inspect`、临时容器取出镜像内容、`printenv` / `Config.Env` 只比较不输出值、只读 SELECT、匿名只读 GET、无头浏览器只读打开两个控制台首页并 fetch 公开桶）；在新建的干净 worktree 构建 5 个镜像；在一次性无网络 MySQL 容器里用 mor-test 只读导出的 `mor_platform` 演练全部 SQL；写发布包。**没有停 / 起 / 替换 mor-test 任何容器，没有执行任何写 SQL，没有调用 mor-test 写接口（没有登录），没有提交、没有 `mvn install`、没有改 `:development` tag，没有 `docker cp` 进运行容器。** 阶段 7 须用户另行授权，由主会话按第 6 节执行，结果写第 10 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次内容：**
- **换镜像（5 个）：** platform-support-service、operations-console-bff、client-console-bff、operations-console-web、client-console-web。mor-test 上 5 个容器都已存在，不需要首次上线方案。
- **数据库（`mor_platform`）：** 新建 7 张 `t_i18n_*` 表；块 135 第 1 部分休眠注册 331 行、第 2 部分语言 2 / 应用 4；第 3 部分检查策略 2 条、`platform-support × PUBLIC` 写入路由 1 条、ops-bff 私有路由改指 `OPERATIONS_BFF_IMPORT`；激活块 135（3 / 11 / 27 / 1）。
- **数据（经真实接口）：** 首批语言包——`operations-console`（2 个命名空间、409 个 key）与 `client-console`（8 个命名空间、608 个 key）各发布一次。
- **环境变量：** 不新增、不删除（第 3.3 节）。
- **不动：** Gateway、Nginx、MinIO、Nacos、Vault、XXL、主 compose 与其余容器。

## 1. 源码与镜像

### 1.1 构建源

| 项 | 内容 |
|---|---|
| 本需求源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-main`（detached `bfb17bc9` + 本需求未提交改动，只读）。取 `git diff --binary bfb17bc9` 与未跟踪文件（排除 `node_modules`、`dist`、`i18n-out`、`target`）：80 个修改文件、298 个未跟踪文件，指纹（补丁 + 未跟踪文件 SHA-256 汇总）`a3c9313a9daecbaf` |
| 构建 worktree | `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-rel`（`git worktree add --detach … bfb17bc9`）。先在纯 `bfb17bc9` 上构建基线（1.2），再应用上述改动；与 `i18n-main` 逐文件比较（`diff -rq`，排除构建产物）只差下面两类叠加文件 |
| 叠加 1：现网未提交的数据查询 10 个文件（Owner code-mor-55，仍未提交） | code-mor-55 的 `src/dataQuery/` 8 个与 `src/pages/{DataSources,QueryTemplates}.tsx`，取自主工作区，SHA-256 与工作流发布包 [ops-web-uncommitted-10.sha256](./evidence/ops-web-uncommitted-10.sha256) 一致（= 现网）。ops-web 候选**必须**带上它们直到 code-mor-55 提交，否则会回退其已上线的数据查询页面体验 |
| 叠加 2：main `ffdf872e` 的 ops-web 改动 | 阶段 6 期间工作流会话两次换 ops-web：07:49:59Z `wfux-20261023b`（`069dca04` 字段键拼音，含 `bfb17bc9` 检查策略列宽）、约 08:10Z `wfpage-20261023`（`ffdf872e` 工作流当前标签页跳转）。按 main 重放：`formModel.ts`、`catalogModel.test.ts`、`EditorPanels.tsx`、`TaskPanels.tsx` 取 `ffdf872e` 原文（本需求不改这 4 个文件）；`package.json` / `package-lock.json` 三方合并（基 `bfb17bc9`、本需求、`069dca04`），**唯一冲突**是同一位置的 `intl-messageformat`（本需求）与 `pinyin-pro`（`069dca04`）两行，两者都保留，`npm ci` 通过。`bfb17bc9..ffdf872e`（中间 `1e3fd0af`、`61aa6283`、`2259e7ba`、`dcd6f1a1` 只有文档）对 Java、client-web、test-environment 无改动，所以其他 4 个服务与 `ffdf872e` 的比较结论同 `bfb17bc9` |
| Java | JDK 25；`./mvnw -B -o -q -DskipTests -Dmaven.repo.local=<scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository -pl <3 个 app> -am clean package`，没有 install。镜像用 `test-environment/java/Dockerfile`（与主工作区逐字节相同），`health-probe/` 从各运行镜像经临时容器取出 |
| Web | 各自 `npm ci` → `tsc --noEmit` → `npm test`（ops-web **1455 / 1455**，client-web **80 / 80**）→ `npm run build` → 各自 `Dockerfile`；`deploy/nginx.conf` 与现网逐字节相同。`npm run i18n:export-source` 的输出复制到 [i18n-bundles/](./i18n-bundles/)（第 8 节） |
| 镜像核对 | 镜像内 `/app/application.jar` / `health-probe/` / `dist/` / `nginx.conf` 与构建产物逐字节相同；Entrypoint、Cmd、User、WorkingDir、Env、ExposedPorts、Healthcheck 与对应运行镜像相同（[precheck_readonly.py](./acceptance/precheck_readonly.py) 复核） |

### 1.2 基线证明（不加本需求，与运行容器比较）

比较方法同前置需求（[evidence/jarcmp.py](./evidence/jarcmp.py)）：`BOOT-INF/classes` 与嵌套 `paytech-*` JAR 逐条目 CRC，第三方 JAR 整包 SHA-256；排除 MANIFEST、`META-INF/maven`、build-info、classpath / layers 索引与目录项。运行 JAR 从运行镜像经临时容器取出，SHA-256 与运行容器内 `sha256sum` 一致。Web 比较 `dist/` 与 `nginx.conf` 逐字节。

| 容器 | 运行镜像 | 基线构建 | 结论 |
|---|---|---|---|
| platform-support-service | `76587ed4…`（wfux-20261023，JAR `c7357ede0b64ac5c`） | `bfb17bc9`：**0 差异**（2,796 项） | 现网 = `bfb17bc9` |
| operations-console-bff | `c9a70b8d…`（wfux-20261023，JAR `6a7a8b4fe3dae51f`） | `bfb17bc9`：**0 差异**（2,530 项） | 现网 = `bfb17bc9` |
| client-console-bff | `b8ecaaea…`（pubbucket-20261023a，JAR `aea366e52763a595`） | `bfb17bc9`：应用类 **0 差异**；只差库 6 项（[明细](./evidence/live-vs-bfb17bc9-client-console-bff.txt)） | 现网 = `22ede435` + 公开桶；差异全部来自已提交的 `f707f2da`（下表） |
| operations-console-web | `f42f545e…`（wfpage-20261023，约 08:10Z 起） | `ffdf872e` + code-mor-55 数据查询 10 个文件：`dist/` **203 个文件逐字节一致**，`nginx.conf` 一致 | 现网 = `ffdf872e` + 10 文件 |
| client-console-web | `25b30a78…`（filestore-s2-20261022） | `bfb17bc9`：`dist/` **3 个文件逐字节一致**，`nginx.conf` 一致 | 现网 = `73a2460d` = `bfb17bc9`（之间无 client-web 改动） |

ops-web 在阶段 6 中被换了两次，每次都在新基线上逐字节复现后重建候选：`a8bb58d7`（`be688db7` + 10 文件）→ 07:49:59Z `4861cd0d`（`069dca04` + 10 文件；第一次一键预检因此 FAIL 1）→ 约 08:10Z `f42f545e`（`ffdf872e` + 10 文件）。三次基线构建都与当时的现网 203 个文件逐字节一致。

client-console-bff 会顺带上线的已提交改动（只报告，不取舍）：

| 库 | 提交 | 内容 | 影响 |
|---|---|---|---|
| `paytech-http-client-support` ~2 | `f707f2da` | `HttpRequestValidationException` 增加 `remoteFieldErrors()`（只增不改，异常 message 仍为公共安全消息），拦截器把已校验的远端字段错误传入 | client-console-bff 不读该字段，行为不变 |
| `paytech-platform-support-api` ~4 | `f707f2da` | 工作流接口与 3 个响应类 | client-console-bff 不调用工作流接口 |

### 1.3 候选镜像（tag `i18n-20261023a`）与差异

| 服务 | 候选镜像 | JAR 前 16 位 | 相对基线（= 现网）。ops-web 候选**必须**带上它们直到 code-mor-55 提交，否则会回退其已上线的数据查询页面体验 |
|---|---|---|---|
| platform-support-service | `sha256:b844b0efd625abdb99984cfaca221585402ce79dc234559129a03141ba44cf16` | `bfbb75a7bd195d66` | +218、~16：localization 模块 158、`paytech-platform-support-api` 50（全部为新增的 localization 类与 `LocalizationErrorCode`，**已有类 0 改动**）、app 5（多语言 HTTP 入口）、core 1（`FileServiceReleaseBundleStore`）、file-document 3（`JsonObjectInspector`、`GrantedUploadHttp` 等）、**第三方 `icu4j-79.1.jar` 1**；改动 16：`application.yml`（只加 `localization:` 段，无占位符）、`PersistenceConfiguration`、`PlatformApiErrorConfiguration`、`console-resource-capabilities.json`、`StartFileUpload*`、file-document 9 项（`StoredContentType`、`FormatFileScanner`、`FileStorageRegistry`、`UploadModels`、`FileUploadServiceImpl`）。[明细](./evidence/jar-diff-platform-support-service.txt) |
| operations-console-bff | `sha256:e4d913e0948bcd92e82eb813050cede96dd28b4f935c03b37e3d286394c3349c` | `d52e541baf7a163b` | +67、~6：本 BFF 17 个类（多语言 3 个 Controller、manifest、上传、转换）、api 50；改动 `application.yml`（metadata-only 前缀加 `/localization-admin/v1/uploads`）、`OperationsErrorStatusResolver`、`OperationsWebConfiguration`、`PlatformClientConfiguration`。[明细](./evidence/jar-diff-operations-console-bff.txt) |
| client-console-bff | `sha256:9033b8fa365a60ff0917906e64280e44cfe574ca421bd11179da90c7c03e2d55` | `948f2c7ad412de27` | +54、~2：manifest Controller、ETag、Localization Client 4 个类，api 50；改动 `PlatformClientConfiguration`。另带 1.2 表中 `f707f2da` 的 6 项。[明细](./evidence/jar-diff-client-console-bff.txt) |
| operations-console-web | `sha256:d7dd45b7fc98ec52bdbda955f45b229a470df1cfc9847a8603720989d70101d7` | — | 源码相对基线（`ffdf872e` + 10 文件）只多本需求 38 个 ops-web 路径（[明细](./evidence/ops-web-source-diff.txt)）：`src/localization/`、`src/i18n/`、`i18n/` seed、导出脚本、`pageCapabilities.ts`、`ConsolePage.tsx`、`configurationModel.test.ts`、`tsconfig.json`、`.gitignore`、`package.json` / lock（`intl-messageformat 12.1.3`）。dist 215 个文件。保留现网工作流体验、字段键拼音、当前标签页跳转、检查策略列宽与 code-mor-55 的数据查询体验 |
| client-console-web | `sha256:280a2f384b80110c69a09630c3389b921a04591aa4dfd90dfdeba7324aab7520` | — | 源码相对基线只多本需求 50 个 client-web 路径（全部页面文案改经 `t()`、`src/i18n/` 运行时、`LocaleSwitcher`、英文基准 8 个命名空间、seed、导出脚本、`intl-messageformat`）。dist 3 个文件 |

**API 消费方：** `paytech-platform-support-api` 本需求只新增类，不改已有类，其他消费方（acquiring、api-gateway、billing、card、client-master、ledger）不需要换。

## 2. 兼容性与执行顺序

| 组合 / 时点 | 结果 |
|---|---|
| DDL（7 张新表）+ 旧 Platform | 无影响：旧代码不读这些表。可在窗口外执行 |
| 块 135 第 1 部分（休眠）+ 任何镜像 | 无影响：路由 DISABLED、授权 REVOKED、菜单 DISABLED（第 1 部分只引用 ops-bff Target 与 client-console-bff Target，均已存在） |
| 块 135 第 2 部分（语言、应用）+ 旧 Platform | 无影响 |
| 新 Platform + 旧 ops-bff / client-bff | 正常：Platform 已有接口与响应类 0 改动，只增 `/internal/localization/v1`；FORMAT 检查多识别 `application/json`，CSV 与其他类型不变 |
| 新 ops-bff / client-bff + 旧 Platform | 正常（多语言端点调用时 404 / 503，路由休眠时不可达） |
| 第 3 部分 + 旧 Platform | 不在本顺序中出现：旧 Platform 不识别 JSON，语言包会被检查拒绝。所以**第 3 部分必须在新 Platform healthy 之后** |
| 第 3 部分生效时机 | Platform 进程内存储快照每 30 秒刷新（`files.storage.snapshot-refresh`）。执行后等待 ≥ 60 秒再上传；本包用“等待”代替“触发刷新”（触发需要一次存储管理写操作，没有必要） |
| ops-bff 私有路由改指 `OPERATIONS_BFF_IMPORT` | 卡 BIN CSV 上传继续可用：同一 FORMAT 检查（UTF-8、无 NUL）、同一 50 MiB 上限，只多允许 `application/json`。code-mor-6b 已确认；发布后跑其 `accept_bin.py`（第 6 节第 15 步） |
| 新 ops-web + 块 135 休眠 | 无影响：导航按数据库菜单生成，多语言菜单不显示；i18n 运行时只在进入多语言页面时启动 |
| 新 client-web + manifest 路由未激活 / 无发布 | 页面用内置英文、切换器隐藏（D-report 裁决 2）。**注意：现网 client-web 是硬编码中文，换上后首批语言包发布前整站显示英文**（第 11 节第 1 项） |
| 激活块 135 后、首批语言包前 | ops 多语言页显示英文（回退内置基准）；client-web manifest 返回 `releaseNo=null`，仍为英文、切换器隐藏 |
| 首批语言包发布后 | ops 多语言页显示中文（应用默认 zh-CN）；client-web 切换器出现，按 `?lang=` > 本机记录 > 浏览器语言 > 应用默认 `en` 选择语言 |

**顺序（按卡片）：** 窗口外：备份 → DDL → 第 1、2 部分 → **窗口：** Platform → 第 3 部分 → 等快照 → ops-bff、client-bff → ops-web、client-web → 激活 → 等 Gateway Snapshot → 首批语言包 → 验收。

**不可用窗口：** 只有常规单服务重启：Platform 约 30—40 秒（登录与全部运营 / 客户入驻中依赖 Platform 的功能）、ops-bff 约 45 秒（运营控制台全部接口）、client-bff 约 30 秒（客户控制台接口）、两个 Web 各约 10 秒。没有跨服务的不兼容窗口。另有一个**文案窗口**：client-web 换上到首批语言包 READY 之间（约 3—5 分钟）客户控制台显示英文。

## 3. 只读核对（阶段 6 已做）

### 3.1 数据库预检

[`sql/00-precheck.sql`](./sql/00-precheck.sql) 最近一次于 08:15Z 在 `mor-test-mysql-1`（root，只读）执行：**31 项全部 ok=1**，输出 [`sql/00-precheck-20261023.out`](./sql/00-precheck-20261023.out)；08:16Z 经 `precheck_readonly.py` 复跑结果相同。

| 组 | 结果 |
|---|---|
| 结构 S01—S02 | 无 `t_i18n_*` 表；`mor_platform` 64 张表 |
| 注册 G01—G16 | 块 135 ID 段在 7 张表均为空；`I18N_VIEW/EDIT/PUBLISH`、`OPERATIONS_I18N*`、`/i18n/*`、`operations-i18n-*` / `client-i18n-manifest`、`/api/operations/localization-admin/*` 与两个 manifest 外部路径都未占用；ops-bff Target `900000000000000001` ACTIVE；client-console-bff Target 恰好 1 个（`236085272176050176`）；PLATFORM_ADMIN ACTIVE；顶级菜单 sort 70 空闲 |
| 文件 F01—F13 | 两个策略代码与 ID 135021—025 未占用；`platform-support × PUBLIC` 路由不存在；ops-bff 私有路由 = `239087906591948800 / CARD_BIN_CSV / 900`（唯一一条，**version 1**）；`MINIO/mor-platform-public` ACTIVE PUBLIC、对外地址 `https://minio.mor-test.localhost:29400/mor-platform-public`；`CARD_BIN_CSV` ACTIVE；无 `I18N_RELEASE_ACTIVATE` 补偿事件；**现有默认 PUBLIC 规则按期望存在**：`*` × PUBLIC → `S3_TEST/mor-test-platform-public-k7q2`（`PUBLIC_DOCUMENT`，pdf / png / jpeg，20 MiB）、`client-console-bff` × PUBLIC → 同一桶（`PUBLIC_IMAGE`，png / jpeg，5 MiB），PUBLIC 规则共 2 条；尚无 `platform-support` 的 PUBLIC 文件 |
| 记录值 | 角色 510 版本 **47**；ACTIVE 网关路由 **542**；ops-bff 已有 BIN 文件 4 个、无进行中的上传；`t_compensation_event` 有 1 条既有 `FILE_PUBLIC_SYNC` FAILED（公开桶需求遗留，与本需求无关） |

**默认 PUBLIC 规则与回落（code-mor-42 应用户要求于 07:43Z 登记）：** 在第 3 部分执行并经存储快照生效之前，任何 `platform-support` 的 PUBLIC 上传都会回落到 `*` × PUBLIC，即 AWS 的 `S3_TEST/mor-test-platform-public-k7q2`（`PUBLIC_DOCUMENT`，不允许 JSON，语言包会被检查拒绝并留在 AWS 桶里）。**在本包顺序中这不会发生**：语言包只由“发布”产生，而发布入口（路由、菜单、授权）在第 11 步激活前一直休眠，第 3 部分（第 7 步）与快照等待（第 8 步）都在激活之前。第 3 部分登记后精确的 `platform-support` × PUBLIC 规则优先于 `*`，语言包写 `MINIO/mor-platform-public`。为防万一，加了三道核对：`first_bundles.py` 发布后逐个读取语言包的文件记录，必须是 `MINIO / mor-platform-public / PUBLIC / platform-support`；`98-bundles-check.sql` B08 要求 `storage_provider = 'MINIO'`，B11 / B12 要求没有任何语言包或 `platform-support` PUBLIC 文件落在 MinIO 公开桶以外；`99-postcheck.sql` P16 / P17 核对两条默认规则不变、PUBLIC 规则共 3 条。

### 3.2 容器、镜像与一键预检

[`acceptance/precheck_readonly.py --browser`](./acceptance/precheck_readonly.py) 最近一次于 08:16:21Z 执行：**PASS 71，FAIL 0**（[输出](./acceptance/precheck-readonly-20261023T0816Z.out)）。内容：SQL 预检 31 项；5 个容器 `.Image` = [compose-rollback.yaml](./compose-rollback.yaml)、healthy、重启 0；5 个候选镜像存在、配置与运行镜像相同、3 个 JAR SHA-256 正确；两个覆盖文件环境逐键相等；MinIO CORS / TLS；两个 manifest 外部路径尚未上线；浏览器严格证书 fetch。阶段 6 第一次运行时 FAIL 1（ops-web 已被换成 `4861cd0d`），之后又被换成 `f42f545e`，两次都据此重建了 ops-web 候选与回滚目标。

### 3.3 环境变量

- 主 compose（`test-environment/compose.applications.yaml`）已含历次覆盖文件中这 5 个服务的全部键；候选 / 回滚文件只钉镜像。
- 扫描全部 94 个 `requirements/**/compose-*.yaml`（容忍 `!reset` 标签）：历史上出现、现网没有的 Platform 键只有 `FILES_ONBOARDING_*`（第二段已显式 `!reset` 去掉）、`WEBHOOK_ALLOWED_SCHEMES`（临时覆盖）、`NOTIFICATION_HTTP_CALLERS_E2ESESEVENTS`（验收覆盖），都不应带回；其余 4 个服务没有遗漏。
- `compose.sh -f <文件> config --format json` 渲染后与 5 个运行容器 `Config.Env` 逐键、逐值比较（只输出键名与是否一致）：platform 53 / 59、ops-bff 14 / 20、client-bff 10 / 16、两个 Web 0 / 7（compose 键 / 运行键），**候选与回滚都 0 缺、0 多、0 不同**，挂载点一致。
- 本需求不新增变量。发布上线依赖本地补偿执行器，现网 `PLATFORM_SUPPORT_FILES_ENABLED=true` 已使其开启；多语言配置只有 `application.yml` 内的锁等待 / 租约与导入上限，无占位符。

### 3.4 浏览器可达性（公开桶跨域与证书）

| 项 | 结果 |
|---|---|
| (a) CORS | MinIO 服务端级 `MINIO_API_CORS_ALLOW_ORIGIN` = `https://localhost:28443,http://localhost:28080`（两个控制台 Origin 正好都在）。对公开桶不带凭据 GET（对象不存在时 404 也带 CORS 头；桶内目前没有对象，公开桶验收清理时已删除）：Origin `http://localhost:28080` 与 `https://localhost:28443` 都返回 `Access-Control-Allow-Origin` 回显，`https://evil.example` 不返回；预检 `OPTIONS`（GET + `if-none-match`）204 允许。**满足，无需配置** |
| (b) 证书 | MinIO 证书 `CN=minio.mor-test.localhost`（SAN 同名，2027-10-14 到期）由 `Code-MoR Test Local CA` 签发；该 CA 在本机登录钥匙串中且为用户信任（`security find-certificate` / `dump-trust-settings`），curl 不带 `--cacert` 也验证通过。**无头 Chromium 不加 `ignore_https_errors`**，分别打开 `http://localhost:28080/` 与 `https://localhost:28443/` 后 fetch 公开桶地址：两者都得到响应且 ACAO 正确，没有证书或 CORS 错误。**本机浏览器满足** |
| 公开桶需求的浏览器验收 | 其 `stage7_browser.py` 使用了 `ignore_https_errors=True`，公开图片预览没有证明证书信任；本包的浏览器验收改为严格校验（第 9 节） |
| 其他机器 | 不信任 test CA 的浏览器拉不到语言包：运行时按设计静默回退内置文案（ops 多语言页英文、client-web 英文且切换器隐藏），不报错。见第 11 节第 3 项 |

### 3.5 SQL 演练（一次性容器，未触及 mor-test 数据）

用 `mysqldump --single-transaction` 只读导出 mor-test `mor_platform`，导入 `--network none` 的一次性 `mysql:8.4.11` 容器（与 mor-test 同一镜像 ID、同一字符集 / 时区参数），经 `scripts/apply_sql.sh`（演练覆盖变量）依次执行：

- 正向：`precheck`（31/31）→ `ddl` → `reg-dry` / `reg`（guard 3/3/11/27/114/15/158）→ `seed-dry` / `seed`（2/4）→ `files-dry` / `files`（2/2/1/1）→ `activate-dry` / `activate`（3/11/27/1）→ `postcheck`（**17/17**，角色版本 48、路由版本 2、ACTIVE 路由 569）。
- guard 有效：重复 `activate-dry` 时 guard 报错 `Subquery returns more than 1 row`，退出码 1，事务未提交。
- 回滚：`deactivate` → `rollback-files` → `rollback-seed` → `rollback-reg` → `rollback-ddl` 全部 guard ok；之后 `precheck` 全部重新 ok（只有角色 / 路由版本递增）。`retire-files`（有文件引用策略时的回滚）另行演练 1/1/2。
- `98-bundles-check.sql` 语法通过（无数据时 B 组为 0，预期）。
- 演练容器已删除，导出文件只在会话临时目录。

## 4. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) / [compose-rollback.yaml](./compose-rollback.yaml) | 钉 sha256 的候选 / 回滚覆盖文件，5 个服务 |
| [scripts/backup.sh](./scripts/backup.sh) | 备份：`mor_platform` 全库、5 个容器镜像 / 状态、JAR 与 nginx.conf 容器内 SHA-256（`docker exec sha256sum`，不 docker cp）、环境变量键名、覆盖文件、precheck 输出；目录 0700、文件 0600、SHA256SUMS |
| [scripts/apply_sql.sh](./scripts/apply_sql.sh) | SQL 包装：`<步骤>-dry` 不提交、`<步骤>` 追加 COMMIT；root 口令只经 `MYSQL_PWD`；容器按完整名称 |
| [sql/00-precheck.sql](./sql/00-precheck.sql) | 只读预检（31 项，含两条默认 PUBLIC 规则的期望 + 7 个记录值） |
| [sql/01-platform-ddl.sql](./sql/01-platform-ddl.sql) | 7 表 DDL，与 `i18n-main` 的 `changes/2026-10-23-localization.sql` 逐字节相同 |
| [sql/10-block135-registration.sql](./sql/10-block135-registration.sql) / [11-localization-seed.sql](./sql/11-localization-seed.sql) / [12-file-policy-route.sql](./sql/12-file-policy-route.sql) | `db/dml.sql` 三部分（语句逐字取用，只去掉 START TRANSACTION / COMMIT，加库名守卫与行数 guard；第 3 部分把 `SELECT ROW_COUNT()` 回显改为先存变量再回显） |
| [sql/20-activate-block135.sql](./sql/20-activate-block135.sql) / [21-deactivate-block135.sql](./sql/21-deactivate-block135.sql) | 激活 / 停用（3 / 11 / 27 / 1；含角色 510 版本加一，同块 134） |
| [sql/90-rollback-file-policy-route.sql](./sql/90-rollback-file-policy-route.sql) / [90b-retire-file-policies.sql](./sql/90b-retire-file-policies.sql) / [91-rollback-seed.sql](./sql/91-rollback-seed.sql) / [92-rollback-registration.sql](./sql/92-rollback-registration.sql) / [93-rollback-ddl.sql](./sql/93-rollback-ddl.sql) | 回滚（`db/dml-rollback.sql` 三部分 + DDL 回退；90 在有文件引用策略时由 guard 拒绝，改用 90b） |
| [sql/99-postcheck.sql](./sql/99-postcheck.sql) / [98-bundles-check.sql](./sql/98-bundles-check.sql) | 激活后 / 首批语言包后只读核对 |
| [i18n-bundles/](./i18n-bundles/) | 首批语言包输入（两个应用各 `en` 源文件与 `zh-CN` 翻译文件）与 SHA256SUMS；发布后的 manifest 快照写 `i18n-bundles/published/` |
| [acceptance/](./acceptance/) | `precheck_readonly.py`（只读一键预检）、`first_bundles.py`（首批语言包）、`stage7_acceptance.py`（接口验收）、`stage7_browser.py`（浏览器验收）、`stage7_cleanup.py`（清理）、`i18n7_common.py` |
| [evidence/](./evidence/) | JAR 差异明细、ops-web 源码差异、现网 client-bff 与 `bfb17bc9` 的差异、比较脚本 |

## 5. 执行前提

1. 用户授权阶段 7（镜像替换、`mor_platform` 写 SQL、经接口写入首批语言包与验收数据分别确认）。
2. 第 12 节各项已裁决。
3. 构建 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-i18n-rel` 与 5 个候选镜像保留到发布完成（镜像只按 sha256 引用）。

## 6. 执行步骤（阶段 7，主会话执行）

在主工作区根目录：

```
R="$PWD/requirements/20261022/platform-support-多语言管理/release"
O="$R/compose-candidate.yaml"; RB="$R/compose-rollback.yaml"
up() { ./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
chk() { for s in "$@"; do docker inspect mor-test-$s-1 --format "$s {{.Image}} {{.State.Health.Status}} rc={{.RestartCount}}"; done; }
```

任一步不符合预期就停，按第 7 节回滚。

0. **协调：** 通知会发布 platform / ops-bff / client-bff / ops-web / client-web 的会话（工作流 / 公开桶 code-mor-42、数据查询 code-mor-55、卡 BIN / 3DS code-mor-6b、文件多存储、wf-file-web 等）本窗口；期间有人替换了这 5 个服务中的任何一个，本包作废，在新基线上重建并重做第 1 节。
1. **复核基线（只读）：** `python3 -I "$R/acceptance/precheck_readonly.py" --browser` → `FAIL 0`。
2. **备份（窗口外）：** `B=$("$R/scripts/backup.sh")`；`$B/00-precheck.out` 第一段 31 项 ok=1，记下角色 510 版本 **V_role**、ops-bff 私有路由版本 **V_route**、ACTIVE 路由数 **N_route**（阶段 6：47 / 1 / 542）。
3. **DDL（窗口外，旧 Platform 运行中）：** `"$R/scripts/apply_sql.sh" "$B" ddl`；随后 `docker exec … SHOW TABLES LIKE 't\_i18n\_%'` 为 7 行（或直接看第 5 步 dry 输出）。
4. **块 135 第 1 部分（窗口外，休眠）：** `apply_sql.sh "$B" reg-dry`，期望 `registration 3/3/11/27/114/15/158 ok`；`apply_sql.sh "$B" reg`，同上并 `committed`。
5. **块 135 第 2 部分（窗口外）：** `seed-dry` → `seed`，期望 `seed 2/4 ok`、`committed`。
6. **Platform（窗口开始）：** `up platform-support-service`；`chk platform-support-service` 为 `sha256:b844b0ef… healthy rc=0`；`docker logs --since <本步开始> mor-test-platform-support-service-1 2>&1 | grep -c ' ERROR '` 为 0，无 `snapshot refresh failed`；`docker exec mor-test-platform-support-service-1 printenv | cut -d= -f1 | sort | diff "$B/platform-support-service-env-keys-before.txt" -` 为空。
7. **块 135 第 3 部分（新 Platform healthy 之后）：** `files-dry` → `files`，期望 `file policy/route 2/2/1/1 ok`、`operations_bff_route_updated = 1`、`committed`。
8. **等待存储快照：** 等 ≥ 60 秒（快照每 30 秒刷新）。
9. **两个 BFF：** `up operations-console-bff`，`chk`（`e4d913e0…`）、ERROR 0、env 键名一致；`up client-console-bff`，`chk`（`9033b8fa…`）、ERROR 0、env 键名一致。
10. **两个 Web：** `up operations-console-web client-console-web`，`chk`（`d7dd45b7…`、`280a2f38…`）。（若第 12 节第 2 项选择“client-web 后换”，这里只换 ops-web，client-web 移到第 13 步之后。）
11. **激活：** `activate-dry` → `activate`，期望 `activate 3/11/27/1 ok`、`committed`。
12. **等 Gateway Snapshot 并核对：** `docker logs --since <第 11 步开始> mor-test-api-gateway-1 2>&1 | grep -i snapshot | tail -3` 看到刷新（≤ 60 秒）；`apply_sql.sh "$B" postcheck`：P01—P17 全部 ok=1，V01 = V_role + 1、V02 = V_route + 1、V03 = N_route + 27；`python3 -I "$R/acceptance/stage7_browser.py" --phase no-release`（client-web 英文、切换器隐藏；ops 多语言页可打开）。提醒用户刷新已打开的运营控制台标签页。
13. **首批语言包：** 先 `python3 -I "$R/acceptance/first_bundles.py" --dry-run`（14/14），再 `python3 -I "$R/acceptance/first_bundles.py"`：两个应用各建命名空间、导入英文 key、导入中文、发布并等待 READY，匿名读取 manifest 与全部语言包（摘要、内容、CORS）。脚本对每个新语言包核对文件记录为 `MINIO / mor-platform-public`（不是 `S3_TEST`）。完成后 `apply_sql.sh "$B" bundles-check`：B01—B12 全部 ok=1（B08 / B11 / B12 即“未回落到默认 AWS 规则”）。若出现 `S3_TEST` 即停：说明第 3 部分未生效，先 `deactivate`，再核对路由与快照。
14. **验收：** `python3 -I "$R/acceptance/stage7_acceptance.py"`（第 9 节）；`python3 -I "$R/acceptance/stage7_browser.py"`（`--phase released`，严格证书）；对照 `acceptance/screens/index.md` 人工核对 9 张设计稿。
15. **卡 BIN 回归（code-mor-6b 条件 4）：** 通知 code-mor-6b 路由已改指 `OPERATIONS_BFF_IMPORT`；按其安排在 `requirements/20261022/payment-3DS全链路/release/acceptance/` 下运行 `python3 accept_bin.py import good`（及其要求的 `lookup` 等子命令），确认 CSV 上传仍通过检查、导入成功。
16. **清理：** `python3 -I "$R/acceptance/stage7_cleanup.py"`（是否加 `--purge-files` 按第 12 节第 6 项）。
17. **记录与通知：** 填第 10 节，`delivery.md` 补环境操作记录；通知第 0 步各会话新镜像 ID 与源码基线：今后 platform / ops-bff / client-bff / ops-web / client-web 候选须包含本需求（直到其提交），ops-web 另须带上 code-mor-55 的数据查询 10 个未提交文件（直到其提交）。

## 7. 回滚

`upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }`

| 范围 | 做法 |
|---|---|
| 只关闭功能 | `deactivate-dry` → `deactivate`（3 / 11 / 27 / 1）：多语言菜单与 27 条路由停用；两个 manifest 不可达，两个控制台按设计回退内置文案（ops 多语言页英文、client-web 英文） |
| 两个 Web | `upr operations-console-web client-console-web`。可单独进行：旧 ops-web 不认识多语言页（菜单若仍激活会显示但点开为空，先 `deactivate`）；旧 client-web 恢复硬编码中文 |
| 两个 BFF | 先 `deactivate`，再 `upr operations-console-bff client-console-bff`（新旧 BFF 都与新 Platform 兼容；旧 BFF 没有多语言端点） |
| Platform | 先 `deactivate`、BFF 可不回滚。再处理第 3 部分：已有语言包 / 导入文件引用两条策略时 `retire-files-dry` → `retire-files`（恢复 ops-bff 路由 `CARD_BIN_CSV`、删公开路由、两策略 RETIRED），否则 `rollback-files-dry` → `rollback-files`（guard 会在有引用时拒绝）。等 ≥ 60 秒后 `upr platform-support-service`。旧 Platform 不读 `t_i18n_*` 表 |
| 第 1、2 部分 | 一般保留（休眠注册与语言 / 应用行对旧代码无影响）。彻底移除：`rollback-seed`（需 `t_i18n_*` 业务表为空，否则先 DDL 回退）→ `rollback-reg` → 等 Gateway Snapshot |
| DDL | 一般保留。彻底撤销：Platform 已回滚后 `apply_sql.sh "$B" rollback-ddl`（删 7 表，隐式提交，无 dry）；会丢掉全部文案、翻译与发布记录（备份目录有全库） |
| 语言包数据 | 发布记录与文件保留不删：公开桶中 `i18n/{应用}/{发布号}/…` 对象匿名可读，内容只是界面文案。需要彻底清除时，DDL 回退后按 `t_file_object`（`source_system='platform-support' AND reason_code='I18N_RELEASE_BUNDLE'`）列出对象，在 MinIO 手工删除对象与版本，再删文件行（做法同 `stage7_cleanup.py --purge-files`） |

注意：`90` / `90b` / `91` / `92` / `93` 只在一次性容器演练过（第 3.5 节），在 mor-test 先跑 dry。备份 `$B/mor_platform-full.sql` 为最后手段（整库恢复会丢失窗口后的所有 `mor_platform` 写入）。

## 8. 首批语言包

| 应用 | 命名空间 | 英文 key | 中文翻译 | 输入文件（SHA-256 前 16 位） |
|---|---|---|---|---|
| operations-console（默认 zh-CN） | `i18n-admin` 401、`enum` 8 | 409 | 409 / 409 | `operations-console.en.json` `72d70a56f7f9b0b3`、`operations-console.zh-CN.json` `e96260950a509b02` |
| client-console（默认 en） | `common` 18、`auth` 64、`onboarding` 177、`workspace` 28、`team` 72、`invitation` 32、`developer` 143、`enum` 74 | 608 | 608 / 608 | `client-console.en.json` `d61067bec1595648`、`client-console.zh-CN.json` `b20315f61872e934` |

- 输入来自候选构建 worktree 的 `npm run i18n:export-source`（与 `i18n-main` 的导出逐字节相同），复制到 [i18n-bundles/](./i18n-bundles/) 并写 SHA256SUMS；脚本运行前逐个核对。发布后逐个读取语言包文件记录，确认在 `MINIO/mor-platform-public`。
- [`first_bundles.py`](./acceptance/first_bundles.py) 经 Gateway 走真实运营接口，管理员取 `test-environment/private/access.md`（同公开桶 / 数据查询验收）。可重跑：已有命名空间跳过；预览无变化跳过确认；与当前发布无差异时跳过发布。
- 每个应用：建命名空间 → 上传英文源文件（KEY_IMPORT，走 ops-bff 私有路由与 `OPERATIONS_BFF_IMPORT`）→ 预览（遇 409 `I18N_IMPORT_FILE_NOT_READY` 退避重试）→ 确认 → 上传中文（TRANSLATION_IMPORT）→ 导入 → 预检 → 发布 → 轮询到 READY（语言包 ops 4 个、client 16 个，写 `MINIO/mor-platform-public`）→ 匿名 manifest → 逐个匿名读取语言包，核对摘要、内容与 CORS。
- 产物：运行记录 `~/Code-MoR-backups/i18n-stage7/first-bundles-*.json`（0600）；manifest 快照 `i18n-bundles/published/{应用}-r{发布号}-manifest.json`。
- 干跑（阶段 6 已做，不调用接口）：14/14。

## 9. mor-test 验收

**接口（[`stage7_acceptance.py`](./acceptance/stage7_acceptance.py)，写数据）。** 破坏性场景都在合成应用 `qa-i18n-<run>` 与私用语言 `qaa` 上做，真实控制台只做一次“内容不变再发布 + 切回 + 切回来”（界面文案不变）。运行记录 `~/Code-MoR-backups/i18n-stage7/run-<run>.json`。干跑（阶段 6）8/8。

| 场景 | 检查 |
|---|---|
| 前置 | 5 个容器为候选镜像；27 条路由 ACTIVE；两个控制台都有当前发布 |
| AC-01 语言目录 | en、zh-CN ENABLED；新增 `qaa`、重复被拒、非 BCP 47 被拒；改 RTL 并停用；过期版本 409；`en` 不能停用；停用语言不能设为必须覆盖；`qaa` 不出现在 manifest 语言列表 |
| AC-02 应用 / 命名空间 / key 导入 | 新建应用、重复编码被拒；非法命名空间编码被拒；建 / 删空命名空间；导入引用未建命名空间时预览不可导入并列出缺口、确认 422 `I18N_NAMESPACE_NOT_FOUND`；补建后导入 6 个 key；第二次导入差异 新增 1 / 变更 1 / 废弃 1 / 不变 4；预览摘要不符 409 `I18N_IMPORT_PREVIEW_STALE`；文件检查中 409 `I18N_IMPORT_FILE_NOT_READY`（mor-test 扫描过快构造不出时记“未能验证”，由本机测试覆盖） |
| AC-03 翻译 | 列表给出基准文案与参数；保存记录操作人与版本；过期版本 409；plural 缺 other、参数名被翻译两类 ICU 错误 422 `I18N_MESSAGE_INVALID`，字段 `translations[0].text`、中文原因；批量一次保存 5 条；超过 65536 字节的批量请求被拒且文案未变 |
| AC-09 导出 / 导入 | 导出再原样导入更新 0；非法文案导入 422（定位 `namespaces.common.common.items`）；带 BOM 的 JSON 完成上传被检查拒绝 422 |
| AC-04 发布 | 全部翻译时预检可发布；新增 key 未翻译时预检列出缺口、发布 422 `I18N_COVERAGE_INSUFFICIENT`（`missing.zh-CN`）；补齐后发布 → PENDING_CHECK → READY、当前、语言包 8/8；每个语言包经文件记录为 PUBLIC、对象键 `i18n/{应用}/{发布号}/…`、匿名读取摘要一致 |
| AC-10 差异 | 第 2 版预检摘要以 #1 为基准；差异 #2 对 #1 只有 1 条 zh-CN 修改；#1 无基准全部为新增 |
| AC-05 切回 | 合成应用切回 #1：当前为 #1、语言包地址与摘要与首次发布一致；不存在的发布被拒；切回 #2。operations-console：切回旧发布后 manifest 立即返回旧地址，再切回新发布 |
| AC-06 manifest | 再发布后地址全部变化、摘要不变；同一地址重复读取内容不变；`releaseNo=` 预览旧发布；不存在的发布 404；manifest 经 Gateway 为 `no-store`（B-report 7.1 已接受）；client-console manifest 与语言包 CORS；语言包对象的 `Cache-Control` 记录实际值（第 12 节第 4 项） |
| 权限 | 合成只读人员（只有 I18N_VIEW）可查看，保存翻译、发布、开始上传均 403 |
| 运行 | 5 个容器 healthy、重启 0；3 个 Java 服务验收期间 ERROR 0 |

**浏览器（[`stage7_browser.py`](./acceptance/stage7_browser.py)，严格证书）。** 客户控制台（匿名页）：切换器出现、选简体中文后“欢迎回来”、`<html lang=zh-CN>`、本机记录并刷新保持；语言包从公开桶直接拉取且 200；`/login`、`/register` 在 1280 / 390 宽 × en / zh-CN 无横向溢出、无裸 key（AC-07、AC-13）；manifest 被拦截为 503 时英文渲染、切换器隐藏（AC-08）。运营控制台：翻译页、发布记录、语言与应用三页与 6 个弹窗按设计稿 01—09 截图（弹窗只打开再取消，校验错误只在输入框里演示后还原，批量粘贴只做“检查”）；新页面中文来自公开桶语言包；`?lang=en` 英文界面。判定控制台错误 0、4xx / 5xx 0；截图与设计稿对照索引写 `acceptance/screens/index.md`。`--phase no-release` 用于第 12 步。

**清理（[`stage7_cleanup.py`](./acceptance/stage7_cleanup.py)）：** 删除合成应用的全部多语言行（先确认其发布没有未结束的上线补偿事件）、`qaa`、合成人员与角色；保留两个控制台的首批发布与 operations-console 的再发布；本次文件默认保留，`--purge-files` 删除文件行并列出 MinIO 待删对象。

本机已覆盖、mor-test 不重复的：检查超时（第 60 次标 FAILED）、发布期间文案变更 409 `I18N_CONTENT_CHANGED`、旧发布后上线不移动指针、检查拒绝后切换 400（F-report 第 5 节）。

## 10. 执行记录（阶段 7 填写）

执行人：主会话 code-mor-db；用户授权：2026-10-23“继续”（采用第 12 节全部建议：client-console 默认 en、client-web 在首批语言包 READY 后再换、其他机器证书信任按现状、长期缓存头留待生产 CDN、激活递增角色 510 版本、清理带 `--purge-files`）。备份目录：`~/Code-MoR-backups/20261023T082205Z-i18n-mor-test`。

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 08:2x | 0 协调 | 通知 code-mor-42 / 6b / d6 / 78 / bf；均确认窗口内不替换这 5 个服务（ae、55 已不在线） |
| 08:21 | 1 只读预检 | 71/71 |
| 08:22:05 | 2 备份 | 00-precheck 31/31；角色 510 版本 47、ops-bff 私有路由版本 1、ACTIVE 路由 542 |
| 08:22:20 | 3 DDL | 7 张 `t_i18n_*`，mor_platform 64 → 71 |
| 08:22:44 | 4 块 135 第 1 部分（初版） | 提交 3/3/11/27/114/15/158。**故障**：随即 Gateway 每次拉快照失败（`CONTROL_PLANE_UNAVAILABLE`；Platform `GatewayConfigurationCompiler` 对 DISABLED 行同样校验，抛 `IllegalArgumentException`）；Gateway 沿用旧快照，现有路由不受影响 |
| 08:22:49 | 5 块 135 第 2 部分 | 2 语言 / 4 应用，committed |
| 08:22:57 | 6 Platform（候选 a） | `b844b0ef…` healthy、rc=0、环境键一致；ERROR 均来自快照故障 |
| 08:27:25 | 回撤 | `rollback-reg` 撤回第 1 部分；08:27:27 快照恢复（542）。快照中断约 4 分 43 秒 |
| 08:32 | 根因与修正（卡 B） | 两条 manifest 路由 SHARED_ROUTE 守卫 1200/min、并发 100 超过编译器上限 600 / 50（`RATE_LIMIT Guard 数值形状不合法`）。改为 600 / 50（`db/dml.sql`、`sql/10-block135-registration.sql`、initial-data 三处），断网副本上编译器在休眠与激活两种状态下均通过 |
| 08:32:51 | 4' 块 135 第 1 部分（修正版） | reg-dry / reg 通过；快照持续成功（542） |
| 08:33:20 | 7 块 135 第 3 部分 | 策略 2 / 要求 2 / 公开路由 1 / ops-bff 路由改指 1，committed |
| 08:34 | 8 存储快照等待 | 65 秒，Platform ERROR 0、Gateway 失败 0 |
| 08:34 | 9 两个 BFF | `e4d913e0…`、`9033b8fa…` healthy、rc=0、ERROR 0、环境键一致 |
| 08:35 | 10 ops-web（client-web 延后） | `d7dd45b7…` healthy |
| 08:35:47 | 11 激活 | activate 3/11/27/1；08:36:00 快照 569 路由 |
| 08:36 | 12 后核对 | P01—P17 全部 ok；V01 48、V02 2、V03 569；浏览器 no-release：ops 登录与翻译页通过（首次登录偶发失败，重跑通过），C0 因 client-web 尚未替换按预期失败 |
| 08:37:01 | 13 首批语言包 | 86/86；ops 409 key、client 608 key、中文 1017；B01—B12 全部 ok（20 个语言包全部在 MINIO/mor-platform-public，未落到 S3_TEST） |
| 08:38 | 10' client-web | `280a2f38…` healthy |
| 08:38—08:40 | 14 接口验收 | 103 通过；1 项"失败"为脚本断言过严（期望错误信息恰为 key，实际为“extra.note 未翻译”，与本机测试一致），已修正脚本；1 项未能验证（mor-test 扫描过快无法构造“检查中”，本机测试覆盖） |
| 08:41 | 14 浏览器验收 | 发现产品缺陷：`manifest?locale=en` 返回 503（`GetManifestProcessor` 用 `Set.of(en, en)` 触发重复元素异常），英文浏览器的客户控制台退回内置英文并隐藏切换器 |
| 08:45 | Platform 修复（候选 b） | 修正为目标语言等于基准时只取 en，补测试（本机 localization HTTP 25/25、模块 36/36）；新 JAR 与候选 a 逐条目比较只差 `GetManifestProcessor.class`；镜像 `4803299926…`（配置与候选 a 一致）替换后 healthy、ERROR 0，两端 `locale=en/zh-CN` 均 200 |
| 08:47—08:52 | 浏览器验收复跑 | 发现第二个产品缺陷：“语言与应用”页并行请求各应用详情，超过 Operations 路由每用户并发 2，Gateway 返回 429。其余失败为脚本选择器问题（key 列表行是 button、工作台列出 key 编码被当作裸 key、发布记录是自定义表格），已修正脚本 |
| 08:53 | ops-web 修复（候选 b） | `SettingsPage` 改为逐个加载应用详情；还原该改动的构建与候选 a 逐字节一致，证明差异只来自此文件；镜像 `fc3abaa7…` 替换后 healthy |
| 08:54 | 浏览器验收终跑 | 30 通过；剩 2 项为预期：脚本自身模拟的 manifest 503（C3）与离页时取消的 diff 请求（ERR_ABORTED）；对照设计稿截图人工核对通过（`acceptance/screens/`） |
| 08:56:28 | 15 卡 BIN 回归 | `accept_bin.py import good`：经 `OPERATIONS_BFF_IMPORT` 上传检查通过，BIN 版本 6 VALID、8/8、0 错误、未激活；6b 的 `accept-bin.json` 运行前备份、运行后原样恢复；已通知 6b |
| 08:57—08:59 | 16 清理 | 合成应用、`qaa`、合成人员与角色、本次验收 21 个文件行删除；MinIO 21 个对象（含全部版本）删除并复查为 0；公开桶 `i18n/` 只剩 client-console 16、operations-console 8 个正式语言包 |
| 09:00 | 17 记录与通知 | 五个服务 healthy、rc=0，修复后 ERROR 0，快照 569；已通知 78 / 6b / 42 / d6 / bf 最终镜像与源码基线 |

**最终镜像：** platform-support-service `4803299926245956db24f0260e46f8ca98d31b7f55fdfce306cb19a156d33515`（i18n-20261023b）；operations-console-bff `e4d913e0948b…`；client-console-bff `9033b8fa365a…`；operations-console-web `fc3abaa711b8…`（i18n-20261023b）；client-console-web `280a2f384b80…`。回滚文件不变（现网前镜像）。

**阶段 7 中的源码修改（同时写入 `Code-MoR-i18n-main` 与构建目录 `Code-MoR-i18n-rel`）：** `GetManifestProcessor`（locale=en）与 `ReleasePublicationApiTest`（新增断言）、ops-web `SettingsPage.tsx`（逐个加载）；SQL：块 135 两条 manifest 路由的 SHARED_ROUTE 守卫；验收脚本：`stage7_acceptance.py` 一处断言、`stage7_browser.py` 四处选择器。

**备注：** 删除 MinIO 对象时查看容器配置，`docker inspect` 输出中包含 mor-test MinIO root 凭据（容器环境变量 `MC_HOST_local`），只出现在主会话工具输出中，未写入任何文件。

**经验：** 注册块提交前需用 Gateway 编译器离线编译整套目录（休眠与激活两态），SQL 演练不能代替。

## 11. 风险与窗口影响

1. **客户控制台语言变化。** 现网 client-web 是硬编码中文；本版改为英文基准、中文经语言包提供。换上后到首批语言包 READY 之间（约 3—5 分钟）客户控制台显示英文；之后中文浏览器（`navigator.languages` 含 zh）显示中文，其他浏览器显示英文（应用默认 `en`），用户可用切换器改选并记在本机。
2. **运营控制台。** 只影响新增的多语言三页：首批发布前英文，之后中文；其余页面不变（全局 antd 仍为 `zh_CN`）。
3. **其他机器的浏览器。** 不信任 test CA 时语言包拉取失败、静默回退（ops 多语言页英文、client-web 英文且无切换器）。本机浏览器已验证可用。
4. **发布期间 Gateway 超时。** 发布在 Platform 锁内生成并上传语言包，管理端读取超时 9 秒；client-console 16 个语言包本机约 1—2 秒，mor-test 上若接近 9 秒，浏览器会看到 504 而发布仍会完成（F-report 7.4），脚本以发布记录为准。
5. **孤儿文件。** 发布 409 / 503 后已上传的语言包成为孤儿（公开可读，内容为文案），不自动清理（裁决 S9）。
6. **卡 BIN 路由改指。** 第 3 部分改的是 code-mor-6b 的写入路由；回归由其 `accept_bin.py` 完成（第 6 节第 15 步）。
7. **基线漂移。** 阶段 6 期间 ops-web 被换过两次（07:49:59Z、约 08:10Z）；阶段 7 之前若再有人换这 5 个服务中的任何一个，第 1 步预检会 FAIL，本包需重建。

## 12. 需要主会话裁决

1. **客户控制台默认语言。** 第 2 部分把 `client-console` 默认语言设为 `en`（用户确认的“基准语言英文”）。现网用户目前看到的是中文；上线后非中文浏览器会看到英文。是否接受，或把 `client-console` 默认语言改为 `zh-CN`（发布后在“语言与应用”页修改即可，不需改 SQL）。
2. **client-web 的替换时机。** 卡片顺序是先换两个 Web 再激活、再发首批语言包，会有约 3—5 分钟客户控制台英文窗口。可选：第 10 步只换 ops-web，client-web 等首批语言包 READY 后再换（首批语言包只依赖 Platform、ops-bff 与激活），客户侧没有英文窗口。建议采用后者。
3. **其他机器浏览器的证书信任（第 3.4 节）。** mor-test 只在本机浏览器可用；如需让其他机器看到中文，需要在那些机器上信任 `test-environment/private/test-ca.crt`。是否接受现状。
4. **语言包长期缓存头（AC-06 / technique 3.7）。** technique 要求桶对 `i18n/` 前缀返回 `Cache-Control: public, max-age=31536000, immutable`；代码上传时不设置该元数据，MinIO 也没有按前缀配置响应头的能力，所以 mor-test 上语言包响应预计不带长期缓存头（功能不受影响：地址含发布号与摘要，内容不可变）。选项：接受，留待生产 CDN 配置；或另立改动在上传授权中加入 `Cache-Control` 元数据（需改代码与签名头）。验收脚本会记录实际值。
5. **激活时递增角色 510 版本。** `db/dml.sql` 文件头的激活段没有这一步；本包按块 134 的做法加上，使已登录管理员立即看到多语言菜单。确认保留。
6. **验收数据保留范围。** 合成应用、`qaa`、合成人员由清理脚本删除；operations-console 会多一个内容相同的发布（当前发布为它）。导入文件与合成应用语言包（私有桶导入文件、公开桶合成语言包）是否 `--purge-files` 并在 MinIO 手工删除对象。
7. **初始化数据与 F-report 的一处不一致（只报告）。** F-report 9.3 说初始化数据追加了第 3 部分全部内容；`i18n-main` 当前的 `platform-support-initial-data.sql` 第 3 部分只含两条策略，路由按环境另行登记（与块 132 做法一致）。本包执行的是需求目录 `db/dml.sql`，不受影响；提交时请以当前文件为准并修正报告措辞。
