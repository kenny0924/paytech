# ops-fe：运营控制台（阶段 B）实现与验证记录

> 范围：`paytech/operations-console-bff`、`operations-console-web`、`paytech/platform-support/.../operations/console-resource-capabilities.json`；注册块 139 只写 SQL，未执行。
> 工作区：`/Users/kenny/Desktop/Workspace/Code-MoR-refdata`（detached `aba76a1a`，私有 Maven 仓库 `~/.m3-refdata`）。未提交、未推送、未操作任何远端环境或数据库。
> 开工前已读：requirement.md（R9、AC-08、AC-12—AC-14）、technique.md（F-02/F-10/F-14/F-15、S-07/S-09、8.2、8.5、9、10）、原型 ops/01—09、`DEVELOPMENT_CONVENTIONS.md`（BFF 聚合、公共分页、第 10 节前端文案）、`UNIT_TEST_GUIDE.md`，以及 Platform `api.referencedata` 新合同。

## 1. 实现

### 1.1 operations-console-bff

| 能力 | 实现 |
|---|---|
| 审核详情行业树（F-10、S-07） | `ClientReviewDetailsProcessor` 删除 client-master `/classification-options` 调用，改为并发读 Platform `IndustryQueryApi`（`status=ACTIVE`），组装为 大类 → 细类（细类带 `defaultMccCode`，按 sortNo 排序；上级不在列表中的细类不展示）。随后按"当前分类 MCC ∪ 各细类建议 MCC"批量读 `MccQueryApi`（`codes` 每批 ≤ 50，任意状态），放进 `mccs`，页面用于显示 MCC 名称与"已停用"。新增可用性字段 `mccsStatus`；行业或 MCC 读取失败只把该部分标 UNAVAILABLE，沿用 3 秒总等待上限 |
| 分类 MCC 搜索 | `GET /client-onboarding-admin/v1/mccs?keyword&page&size`（`ClientOnboardingReviewController`），只列 ACTIVE，关键字去首尾空白，分页用公共 `PagingRequestBody` |
| 分类保存 422 | `ClientReviewCctClientImpl.replace` 捕获 `HttpRequestValidationException`，字段路径（如 `items[1].industryCode`）只投影到本次页面请求体内存在的路径；远端文案不外显 |
| 参考数据只读接口 | 新 `ReferenceDataAdminController`：`GET /reference-data-admin/v1/{countries, country-subdivisions, industries, mccs}`，简单转发（不建 Processor），经 `PlatformReferenceDataClient(Impl)` + `ReferenceDataContractConverter` 核对编码 / 状态 / 层级 / 分页窗口后投影到 BFF 页面模型 `ReferenceDataModels`（不返回本地化名称）。Query 走审核页同一严格边界（`ClientReviewRequestConfiguration` 增加 `/reference-data-admin/v1/**`） |
| 币种 | 请求模型与转换器带 `numericCode`、`name`、`symbol`、`logoFileAddress`；币种页列表改读 `ReferenceCurrencyQueryApi.listCurrencies(null, null)`（宽松解码），页面模型加 4 个字段与 `logoPublicUrl`，去掉 `updateTime`（原型无此列，新接口不返回）；财务页币种下拉仍读旧 `CurrencySpecQueryApi`。修改回执不再要求 DRAFT（启用 / 停用后只换图标时状态不变，只要求已知状态） |
| 入驻配置目录 | `catalog-items?catalogCode=REF_*` 直接返回空列表，不访问 client-master |
| Platform 代理 | `PlatformClientConfiguration` 登记 5 个新 QueryApi（沿用 `operationsPlatformProxyFactory`，宽松模式、不带操作人） |

### 1.2 operations-console-web

| 页面 | 实现 |
|---|---|
| 分类（原型 07、08） | `ReviewRisk.tsx` 的 `ClassificationPanel` 重写：每行 主要 / 大类（带图标）/ 细类（编码 + 名称，可搜索）/ MCC 搜索框 / 标记 / 删除。选细类自动带出建议 MCC 并标"建议"；改选后标"已改 · 建议 xxxx"。MCC 搜索 Popover：300 ms 防抖、编码前缀或英文名、前 10 条，"细类建议"、卡组织高风险红标、已选勾。保存前本页预检（只选到大类、行业不在 ACTIVE 树、MCC 格式、已知停用 MCC、重复、恰好一个主要）；client-master 422 字段路径映射回行与控件（原因按本地数据判断）；标题旁"N 个问题 · 定位"，右侧"第 N 行的依据" / "保存未成功"汇总；底栏 未保存 · N 处修改 / 保存未成功：N 处需要修改 / 参考数据服务暂不可用（503）＋ 放弃修改 / 保存分类 / 确认分类。存量只到大类的编码只选中大类、细类留空（AC-10）；确认后与只读视图显示 大类或细类名称 · MCC 名称 |
| 参考数据（原型 01—04） | 新页 `pages/ReferenceData.tsx`（平台配置下，`OPERATIONS_REFERENCE_DATA`，`/reference-data`）：页签 国家 / 行政区 / 行业 / MCC 带条数；四类数据依次读取（参考数据路由按账号并发 2），每类单独失败可重试；国家：搜索（编码、三位码、英文名、当前语言名）+ 区域 + 状态，表格 50 条分页，右侧详情（国旗、英文名、区号、法定货币 Intl 名称、区域、国旗文件地址、版本、本国行政区预览与跳转）；行政区：国家条（AE / SA / EG 由数据得出）、搜索、状态；行业：树形表（展开 / 收起全部），右侧详情（上级、建议 MCC 名称与"查看"跳 MCC 页签、选择提示、refdata 键）；MCC：全量分页读入后本地搜索、"全部 / 卡组织高风险 / 被细类引用"、状态，右侧详情。`?tab=subdivisions&country=AE` 可直接打开页签 |
| 币种（原型 05、06） | 列表 + 右侧面板；状态筛选带计数；列 图标 / 币种 / 数字码 / 名称（Intl）/ 符号 / 小数位 / 舍入 / 状态 / 版本。草稿（或新建）面板可改全部属性，底栏 有未保存的修改 · 取消 / 保存草稿 / 保存并启用（先保存再用回执版本打开启用确认）；非草稿只读带锁标、冻结说明、"更换图标"弹窗（当前 → 新、变更原因必填，PUT 时标准属性按当前值提交）。图标上传复用"文件上传"页的公开上传链路（新 `fileUpload/usePublicFileUpload.ts`）；账号没有 `OPERATIONS_FILE_UPLOAD_CREATE` 时改为手工填写文件地址。Platform 422 字段错误挂在对应控件下 |
| 入驻配置目录（原型 09） | 目录页签改为 目录 / 编码 / 来源 / 被引用字段 清单 + 右侧面板；`REF_*` 标"Platform 参考数据"，右侧说明来源、引用字段、行政区按辖区国家预览（无参考数据权限时静默不预览）并跳转参考数据页或币种页；入驻目录仍显示目录项 |
| 多语言 | 新命名空间 `reference-data`（页面文案，en + zh-CN 83 键）；`currency`、`client-review`、`nav` 补键（`nav.resource.OPERATIONS_REFERENCE_DATA`）；`onboarding-schema` 删除 `catalog.{INDUSTRY,CURRENCY,STATE_OR_PROVINCE}.*` 48 键（与 client-console 镜像一致）。行业、行政区、MCC 名称经 `refdata` 命名空间：不内置、不列入 `OPS_NAMESPACES`，`useT('refdata')` 首次使用时按需加载 Platform 发布的语言包，取不到回退后端 `name`，再回退编码（B7、AC-14）。资料值中的币种改用 Intl、省州改用 refdata（存量 `AE_DU` 按 `AE-DU` 查找） |
| 注册镜像 | `pageCapabilities.ts` 与 `console-resource-capabilities.json` 在 卡 BIN 库 后加 `OPERATIONS_REFERENCE_DATA`（无按钮），两者顺序一致；`ConsolePage.tsx` 懒加载 |

### 1.3 注册块 139（只写，未执行）

目录 `db/platform-support/`：`block139-spec.json` → `gen_block139.py`（复制切片 1 的 `gen_block138.py`，形状规则不变）生成 `block139-precheck.sql`、`registration-block139.sql`、`registration-block139.initial-data.sql`、`block139-activate.sql`、`block139-deactivate.sql`、`block139-rollback.sql`。

| 类别 | 内容 |
|---|---|
| 权限 | `REFERENCE_DATA_READ`（REFERENCE_DATA / READ / LOW）`900000000000139101`；PLATFORM_ADMIN 授权 `…139201` 为 REVOKED |
| 菜单 | `OPERATIONS_REFERENCE_DATA` `…139301`，父级 平台配置 `900000000000000562`，sort 95（卡 BIN 库 90 之后），DISABLED |
| 路由 | `…139401—139405`：`operations-reference-data-{countries,subdivisions,industries,mccs}`（REFERENCE_DATA_READ）与 `client-onboarding-mcc-search` `/api/operations/client-onboarding-admin/v1/mccs`（既有 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`，按 code 解析为变量，缺失即 ERROR 1242 中止） |
| Header / Query / Guard | header 10（`…139501—510`，GET：authorization CORS + origin FORWARD）；query 11（`…139601—611`：status；countryCode,status；status；keyword,status,page,size；keyword,page,size）；guard 30（`…139901—930`，SHARED_ROUTE 300/min + 50、CLIENT_IP 30/min + 3、principal 30/min + 2） |
| 合计 | 59 行；启用 = 路由 5 + 资源 1 + 授权 1 + role.version；回滚按 ID 段删除 |

## 2. 验证

| 命令 | 结果 |
|---|---|
| `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-app -am -DskipTests install` | 通过（依赖从本 worktree 源码构建，含 Platform / client-master api 当前状态） |
| `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test test -Dtest='CurrencySpecBrowserAutoTest,ClientOnboardingReviewBrowserAutoTest,ReferenceDataBrowserAutoTest,OnboardingAdminBrowserAutoTest,OperationsConsoleBffApplicationAutoTest,OperationsHttpBodyNamingAutoTest,ArchitectureAutoTest'` | 148 例全部通过（ReferenceData 23、ClientOnboardingReview 29、CurrencySpec 25、OnboardingAdmin 9 等） |
| `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test test`（整个 BFF 测试模块） | 2421 例全部通过 |
| 按 pf-dev 答复复核：`CurrencySpecBrowserAutoTest` | 26 例全部通过；web `npm test` 1564 例、tsc、build 通过 |
| 按 cm-dev 定稿复核：`install` worktree 的 client-master-api（已删除 options / CatalogOptionResponseBody）后编译 ops-bff，`ClientOnboardingReviewBrowserAutoTest` | 编译通过；30 例全部通过（新增分类目录拒绝用例）；web `npm test` 1564 例、tsc、build、i18n 导出（9658 键）通过 |
| `npx tsc --noEmit`（operations-console-web） | 通过 |
| `npm test` | 1564 例全部通过（含 i18nCatalog、i18nSourceGuard、onboardingSchemaCatalog、uiSmokeReplay） |
| `npm run build` | 通过（仅既有的 chunk 体积提示） |
| `npm run i18n:export-source` | 通过：25 个命名空间、9633 键，en / zh-CN 键集合与 ICU 参数一致 |
| `python3 -I gen_block139.py block139-spec.json` | 生成 6 个 SQL，计数 59 行；SQL 未执行，未跑 `block_compile_check.sh` |

新增 / 改写的 auto 测试（测试端点返回配置的 DTO，记录调用，AfterEach 断言无意外调用）：
- `ReferenceDataBrowserAutoTest`（新）：4 端点转发与投影、不带操作人与浏览器凭据、授权拒绝、非法 / 重复 / 空 Query 与带 Body 在访问 Platform 前拒绝、上游未知状态 / 非法编码 / 重复 / 层级与上级不符 / 分页窗口不符 → 502、Platform 503 → DEPENDENCY_UNAVAILABLE、合同外字段不投影。
- `ClientOnboardingReviewBrowserAutoTest`：详情组装行业树（排序、孤儿细类剔除、`status=ACTIVE`、不带操作人）、MCC 批量读取的 `codes`；Platform 行业不可用只标该部分；分类 MCC 搜索（只列 ACTIVE、去空白、非法分页 / 未知 Query 前置拒绝）；分类 422 字段路径投影且远端文案不外露。
- `CurrencySpecBrowserAutoTest` + `currency-spec-boundary.json`：列表读参考数据币种接口（`ownerResponse` 与页面形状分开）、新字段入站校验、已启用币种只换图标回执为 ACTIVE、上游数字码 / 图标地址非法 → 502；财务下拉改用旧接口夹具。
- `OnboardingAdminBrowserAutoTest`：`REF_*` 目录不访问 client-master。
- web：`referenceData.test.ts`（新）、`clientReview.test.ts`（行业树、行映射、建议 / 已改标记、预检、422 映射）、`currencySpecModel.test.ts`、`onboardingConfig.test.ts`、`configurationModel.test.ts`（页面数 78）。

### 2.1 整个 BFF 测试模块

`./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test test`：**2421 例，0 失败，0 错误，0 跳过**（BUILD SUCCESS）。

## 3. 偏差与说明

1. **分类页 MCC 搜索用独立路径** `/client-onboarding-admin/v1/mccs`：一条 Gateway 路由只能绑定一个权限，参考数据页的 `/reference-data-admin/v1/mccs` 绑 `REFERENCE_DATA_READ`，分类页搜索复用 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（technique 8.5"复用审核权限"；只有编辑分类时才搜索）。两者共用同一 BFF 端口。
2. **审核详情多一个 `mccs` / `mccsStatus`**：为显示当前与建议 MCC 的名称和"已停用"标记，由 BFF 按编码批量读取；不改变 `industries` 以外的既有字段。
3. **参考数据页的 MCC 页签全量读入后本地分页**（`size=200` 依次读完，约 2 次请求）：Platform 合同没有"高风险""被细类引用"筛选，全量读入后搜索、标签与状态筛选都准确；分类页的搜索仍是服务端分页。
4. **币种页去掉"最后更新时间"列**：原型没有此列，`ReferenceCurrencyQueryApi` 不返回；财务页下拉不受影响。
5. **"application.yml 路由配置"**：BFF 没有按路径的 yml 路由表；新路径登记在严格 Query 边界（`ClientReviewRequestConfiguration`）与端点清单测试（`OperationsConsoleBffApplicationAutoTest`）。
6. **目录来源由前端按 `REF_` 前缀标出**；BFF 只对 `REF_*` 目录项短路返回空列表（Schema 响应仍原样透传 client-master 类型，未建 BFF 自有模型）。
7. **`refdata` 不是内置命名空间**：数据键由 pf-dev 的生成脚本导入、发布；前端未内置任何 refdata 键。
8. **`uiSmokeBodies.json` 中 3 份案件详情抓包按新合同改写**（`industries` 改为大类形状、补 `mccsStatus=UNAVAILABLE`、`mccs=[]`），测试注释已写明；不是新的 mor-test 抓取。
9. **注册块的 initial-data 变体**：依赖 `CLIENT_ONBOARDING_CLASSIFICATION_EDIT`（由管理 API 生成），全新环境上整块跳过（与块 138 相同的条件插入规则）；是否追加到 `platform-support-initial-data.sql` 由主会话决定。

## 4. 未决与依赖

| # | 事项 | 状态 |
|---|---|---|
| O-1 | client-master 分类 422 形状（cm-dev 2026-10-27 定稿）已核对：`CLASSIFICATION_INVALID` 已在 `ClientDraftErrorCode`（BFF 已登记），带 `errors[]` 时 Foundation 以 `RequestBodyValidationException` 原样带出逐行字段路径，页面按 `items[i].industryCode / mccCode` 挂到行与控件，原因按本地数据判断（不解析远端中文文案）；不带 `errors[]` 的整组错误（重复组合、主要分类数）为表单级 422，页面显示 `client-review.enum.commandError.CLASSIFICATION_INVALID`。Platform 不可用时 client-master 返回 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`：BFF 对任何 5xx 都走依赖故障映射，浏览器收到 **503 `DEPENDENCY_UNAVAILABLE`**（不是 502，原 Code 不保留），页面按 503 显示"参考数据服务暂不可用，请稍后重试"。新增 `test_classificationCatalogRejections` 覆盖三种情况 | 已完成 |
| O-2 | Platform 币种修改的拒绝形状（pf-dev 2026-10-27 答复）已核对：冻结字段改动与图标不存在 / 非公开都是 422 `VALIDATION_ERROR` + 字段错误，管理接口只用公共错误码，BFF 无需登记新枚举。BFF 把字段路径投影到本次请求体（不依赖顺序、远端文案不外露），新增 `test_updateFrozenFieldAndLogoRejectionsProjected`；页面在表单控件下显示，另在编辑面板 / 换图标弹窗给出明确提示（图标被拒请重新上传；换图标时冻结字段被拒说明页面数据已过期、请刷新）。清空图标（null）Platform 允许，换图标弹窗本期只提供替换 | 已完成 |
| O-3 | Platform 命名空间：`operations-console` 应用需先在 Platform 建 `reference-data`（页面文案）与 `refdata`（数据名称，pf-dev 生成）两个命名空间，再导入、翻译、发布 | 发布清单项 |
| O-4 | 浏览器冒烟（AC-12、AC-13 的视觉 / 交互）未做：本 worktree 没有可连的 Platform / client-master；需在阶段 5 或 mor-test 部署后按原型 01—09 核对 1440 宽与 zh-CN / en | 待阶段 5 |
| O-5 | 块 139 发布前需在 mor-test 只读核对 ID 段与 code / 路径未占用（`block139-precheck.sql`），并用候选 Platform JAR 跑五态预编译 | 阶段 6 |
| O-6 | MCC 搜索按账号 30 次/分钟限流，搜索框 300 ms 防抖；连续快速输入极端情况下可能触发 429（页面显示搜索失败可重试） | 观察 |

## 5. 阶段 5 冒烟 P2 修复（F-3—F-7，2026-10-27）

| # | 修复 | 文件 |
|---|---|---|
| F-3 | 参考数据各表改 `size="small"`；国家表列宽重排（状态 104、区域 128 + 省略号、法定货币 96），右侧详情宽 `clamp(300px, 24vw, 400px)`；状态 / 层级等短列 `white-space: nowrap` | `pages/ReferenceData.tsx`、`referenceData/referenceData.css` |
| F-4 | 行业表"编码"与"名称"合为一列（编码等宽 + 名称第二行，不再截断），层级 112 不折行；新键 `reference-data.industry.column.codeAndName`（删除 `column.code / column.name`） | 同上、`i18n/{en,zh-CN}/reference-data.json` |
| F-5 | 目录页签改为单列（清单在上、所选目录在下），来源标签不折行、被引用字段可换行，目录项表固定布局，取值列不再逐字竖排，脚注不再限宽。地址格式组件引用的目录并入清单（`address.<组件> (<地址格式>)`），解码可选字段 `addressComponents`；**依赖 client-master 管理接口返回该字段**（已请 cm-dev 在 `AdminJurisdictionResponseBody` 增加 `addressComponents`，未答复前清单中不会出现 `REF_COUNTRY_SUBDIVISION`；ops-bff 透传该类型，cm-dev 改完后需按新 api 重建 ops-bff） | `onboardingConfig/{onboardingConfig.ts,OnboardingConfigView.tsx,onboardingConfig.css,onboardingConfig.test.ts}` |
| F-6 | 分类行：依据面板默认在行下方（≥ 1680px 才放右侧，不再与行重叠），行网格 `52px / 大类 0.8fr / 细类 1.5fr（≥ 240）/ MCC 1.1fr / 170px`，尾列标签最大 150px 省略、表头不折行；大类与细类下拉弹层不随控件宽度截断，选中值带 title；细类说明取 `refdata.industry.<CODE>.description`，取不到回退后端 description（新函数 `industryDescription`，参考数据页行业详情同样使用） | `clientReview/{ReviewRisk.tsx,clientReview.css}`、`referenceData/referenceData.ts`、`pages/ReferenceData.tsx` |
| F-7 | 币种表 `size="small"`，数字码 108、小数位 124、舍入 112、状态 104、版本 76 不折行，`scroll.x` 800；右侧面板宽同 F-3 | `pages/CurrencySpecs.tsx` |

验证：
- `npx tsc --noEmit`、`npm test`（1565 例全部通过，新增 `industryDescription`、`addressComponents` 解码与目录合并用例）、`npm run build` 通过。本轮未改 ops-bff 代码，未重跑 BFF 测试、未换 BFF JAR。
- Playwright 布局检查（本机 e2e，Vite :18301 经 https://127.0.0.1:18444，管理员账号，案件 240789978148261888），脚本 `scratchpad/opsfe-layout/check.mjs`：en / zh-CN × 1440 / 1920，检查参考数据国家 / 行业、币种、分类、入驻配置目录五处表头截断、单词折行、标签截断、单元格越界、分类标签越出网格或压到依据面板、细类选中值截断——**8 组全部 PASS**（首轮发现的"NORTH_AMERICA 折行""Decimal Places 表头截断""已改 · 建议 5651 标签截断""细类截断"均已修复后复测）。`REF_COUNTRY_SUBDIVISION listed: false`（等 client-master 字段，见 F-5）。中途两次分类页超时为连续登录触发的限流，单独重跑通过。

### 5.1 F-5 接入 client-master `addressComponents`（cm-dev 2026-10-27）

- client-master 管理接口 `GET /client-master-admin/v1/jurisdictions` 新增 `addressComponents`（`component`、`catalogCode` 可空、`required`、`order`；恒存在，没有地址格式时为空列表）。ops-bff 原样透传 `AdminJurisdictionResponseBody`，代码不变；按 worktree 的 client-master-api（`install` 到 `~/.m3-refdata`）重建。web 解码与目录合并已在第 5 节完成。
- `OnboardingAdminBrowserAutoTest` 夹具加入 3 个组件（含 `REF_COUNTRY_SUBDIVISION` 与 `catalogCode=null`），断言原样到达页面。
- 受影响 BFF 测试：OnboardingAdmin 9、ClientOnboardingReview 30、CurrencySpec 26、ReferenceData 23、OperationsConsoleBffApplication 8，共 96 例全部通过。
- 执行 JAR：`paytech/operations-console-bff/paytech-operations-console-bff-app/target/paytech-operations-console-bff-app-1.0.0-SNAPSHOT-exec.jar`，sha256 `ee26358f49afdae226fd315d548025b883e2e669bc348c4c93e8f05eef464ff8`（`-B -o -DskipTests ... -am clean package`，BUILD SUCCESS；内含的 client-master-api 有 `AddressComponentItem`、无 ClassificationOptions）。已交 e2e-dev 先换 client-master 再换 ops-bff 并重跑冒烟；`i18n-out/` 已重新导出（9659 键）供重新导入。

### 5.2 F-5 复测缺陷：点击目录行后页签回到"采集 Schema"（e2e-dev 2026-10-27）

- 原因：`refdata` 命名空间原先在页面里首次 `useT('refdata')` 时才按需加载；加载完成触发 i18n revision 变化，控制台外壳随之重新渲染并重新挂载当前页面，页内状态（所选页签、所选目录）丢失。点击目录行时右侧首次渲染用到 `refdata` 的组件，因此必现；同一机制也会让参考数据页、分类页在首次加载 refdata 时丢失页内状态。
- 修复：`i18n/namespaces.ts` 新增 `RUNTIME_DATA_NAMESPACES = ['refdata']`（仍不内置、不进 `OPS_NAMESPACES`），`main.tsx` 根部 I18nGate 与全部命名空间一起预加载，页面中再用到时不再触发重新挂载。
- 验证：e2e-dev 的复现脚本 `scratchpad/refdata-e2e/f5.mjs`：点击 `REF_COUNTRY_SUBDIVISION` 后页签保持"Catalogs"，右侧出现 AE 行政区预览（`has AE-DU true`）；web `tsc`、`npm test`（1565）、`build` 通过。仅改 web，Vite 已实时生效，无需换 JAR。
