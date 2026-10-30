# W4 报告：ops-bff / ops-web 税务查询页（阶段 4）

- 范围：技术方案第 10.3、12（只写注册 SQL，未执行）、13 节；原型 16—21。
- 工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-tax`（未提交）。注册 SQL 在主工作树本需求目录 `db/platform-registration.sql`。
- 已读规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（第 3.3、4.1—5.5 节）、`verification/task-card-stage4.md`、`verification/stage4/tax-api-ready.md`、technique 第 10、12—14 节、requirement AC-13 / AC-14。
- 所属 Processor：`GetOrderTaxProcessor`（只读聚合）、`AuthorizeTaxDocumentDownloadProcessor`（只读 + Platform 下载授权）。BFF 无数据库，无本地原子写入；两次外部调用（tax 查询、Platform authorizeDownload）均在无事务的 Processor 中顺序执行。

## 1. 改动文件

**operations-console-bff（`paytech/operations-console-bff/`）**

| 文件 | 内容 |
|---|---|
| `pom.xml`、`paytech-operations-console-bff-app/pom.xml` | 依赖 `paytech-tax-api`（reactor 内，版本 `${project.version}`） |
| `app/.../configuration/TaxClientConfiguration.java` | `TaxOrderQueryApi` 代理：固定私网地址 `operations.tax-base-url`（tax 不注册 Nacos，同 billing / reconciliation 写法），连接 2 秒 / 读取 4 秒，只登记 `TaxErrorCode` |
| `app/.../configuration/OperationsErrorStatusResolver.java` | `TAX_ORDER_NOT_FOUND`、`TAX_CALCULATION_NOT_FOUND` → 404，`TAX_CALCULATION_CONFLICT` → 409 |
| `app/src/main/resources/application.yml` | `operations.tax-base-url`（默认 `http://127.0.0.1:18116`，环境变量 `OPERATIONS_TAX_BASE_URL`）；`/tax/v1` 加入只记元数据的日志前缀（下载响应含 60 秒签名地址） |
| `app/.../integration/TaxClient.java` + `impl/TaxClientImpl.java` | 单次读取 tax 订单；订单号不一致、无计算或列表缺失 → 502 |
| `app/.../integration/TaxDocumentFileClient.java` + `impl/TaxDocumentFileClientImpl.java` | Platform `InternalFileApi.authorizeDownload`，带操作人，默认附件方式 |
| `app/.../domain/TaxOrderPageContext.java` | 一次请求的 tax 事实与纯判断（当前 / 首个 / 最近最终报价、交易、税票、供应地证据、注册与地区包查找、页面状态） |
| `app/.../domain/enums/TaxPageStatus|TaxProgressStep|TaxProgressState|TaxCalloutKind.java` | 页面模型 Code |
| `app/.../service/processor/GetOrderTaxProcessor.java` | 页面状态、进度、提示卡、金额、逐行明细、报价记录、阻断原因、本次匹配 |
| `app/.../service/processor/TaxOrderPageSections.java` | 右栏组装（税票、登记、底稿、依据、不由平台计税）与一句话说明输入；只为控制 Processor 篇幅拆出 |
| `app/.../service/processor/AuthorizeTaxDocumentDownloadProcessor.java` | 读 tax 订单单据 → 单据须属于该订单且 ISSUED 并有文件地址（否则 404）→ `FileAddress.parse` → Platform 签发 |
| `app/.../service/processor/request/GetOrderTaxRequest|AuthorizeTaxDocumentDownloadRequest.java` | 内部请求 |
| `app/.../service/model/tax/response/TaxOrderPageResponseBody|TaxDocumentDownloadResponseBody.java` | 页面模型与下载响应（`toString` 不含地址） |
| `app/.../ext/rest/TaxOrderController.java` | `GET /tax/v1/orders/{orderNo}`、`POST /tax/v1/orders/{orderNo}/documents/{documentId}/download`；拒绝任何 Query |
| `test/.../auto/TaxOrderBrowserAutoTest.java` | 15 个入口测试（见第 2 节） |
| `test/.../test/builder/TaxOrderResponseBuilder.java` | tax 响应 DTO（原型 16/17/18 + 暂估 / 待支付），用 tax-api 枚举 |
| `test/.../test/simulator/TaxOwnerSimulator.java` | tax Loopback 测试端点（复用 `FinanceOwnerSimulator`，未配置调用计入 unexpected） |
| `test/.../configuration/OperationsBffTestConfiguration.java`、`base/OperationsBffHttpTestBase.java` | 注册 TAX Simulator 与 `operations.tax-base-url` |
| `test/.../simulator/PlatformOperationsSimulator.java` | 路由码 `operations-finance-tax-order-detail`、`operations-finance-tax-document-download` → 合成 TAX_VIEW 权限 |
| `test/.../auto/OperationsConsoleBffApplicationAutoTest.java`、`OperationsHttpBodyNamingAutoTest.java` | 精确路由清单 + 2 条；日志前缀 + `/tax/v1`；命名检查覆盖 `TaxOrderController` 与 `TaxOrderQueryApi` |

**operations-console-web（`operations-console-web/`）**

| 文件 | 内容 |
|---|---|
| `src/pages/FinanceTax.tsx` | 页面：订单号在地址 `?orderNo=`；初始 / 加载 / 未找到 / 失败 / 结果五态 |
| `src/tax/TaxOrderView.tsx` | 结果页（标题、徽标、进度、提示卡、两栏） |
| `src/tax/useTaxDownload.tsx` | 下载与原型 21 的失败提示条 |
| `src/tax/tax.ts` | 页面模型类型、严格解码、路径、最近查询（localStorage，try/catch）、格式化辅助 |
| `src/tax/tax.css` | 页面样式；≤1100px 两栏变单栏，≤760px 进度纵排 |
| `src/tax/tax.test.ts` | 11 个测试（第 2 节） |
| `src/i18n/en/tax.json`、`i18n/zh-CN/tax.json` | 新命名空间 `tax`，203 个键，en / zh-CN 同键同参数 |
| `src/i18n/namespaces.ts`、`src/i18n/builtin.ts` | 登记 `tax` 命名空间 |
| `src/i18n/en/nav.json`、`i18n/zh-CN/nav.json` | `nav.resource.OPERATIONS_FINANCE_TAX` = Tax Lookup / 税务查询 |
| `src/navigation/pageCapabilities.ts`、`src/navigation/ConsolePage.tsx` | `OPERATIONS_FINANCE_TAX` → `/finance/tax`，懒加载 `FinanceTax` |
| `src/finance/finance.test.ts`、`src/configuration/configurationModel.test.ts` | 财务页清单 + 税务查询；页面总数 78 → 79 |

**platform-support（只改数据镜像）**

| 文件 | 内容 |
|---|---|
| `paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` | 增加 `OPERATIONS_FINANCE_TAX`（与 Web `pageCapabilities.ts` 逐项一致，Web 测试强制校验；资源管理页据此识别组件） |

**注册 SQL（主工作树，未执行）**：`requirements/20261029/tax-结账算税与事件登记/db/platform-registration.sql`。

## 2. 构建与测试

| 命令 | 结果 |
|---|---|
| `./mvnw -o … -pl operations-console-bff/paytech-operations-console-bff-app -am compile` | 通过 |
| `./mvnw -o … -pl operations-console-bff/paytech-operations-console-bff-test -am package -Dtest='com/paytech/operations/console/bff/**/*'`（任务卡第 3 节更新后改用 package 重跑） | 2467 通过 / 0 失败（含 TaxOrderBrowserAutoTest 15、OperationsHttpBodyNamingAutoTest 55、ArchitectureAutoTest 1、OperationsConsoleBffApplicationAutoTest 8）。BFF 无数据库，不触碰本机共享库 |
| `npm test`（operations-console-web） | 1612 通过 / 0 失败（含 tax.test.ts 11、i18n 源码守卫、catalog 一致性） |
| `npm run build`（`tsc --noEmit && vite build`） | 通过 |
| `npm run i18n:export-source` | 通过：26 个命名空间，tax 203 键 |

Web 测试借用主工作树的 `node_modules`（临时符号链接，两边 `package-lock.json` 一致），跑完已删除；未执行 `npm install`。未跑全 reactor（仅本模块 `-am`）。

**AC 对照**

- AC-13：`TaxOrderBrowserAutoTest` 覆盖 READY / BLOCKED / NOT_MANAGED 徽标、逐行税额与税率、阻断原因（行号与品类解析）、规则包版本、正式交易与底稿状态（PENDING 与 READY）、暂估与待支付、无计算 404 空状态、订单号非法 400、无权限 403、tax 响应无效 502、tax 不可用 503。en / zh-CN 由 `tax.test.ts` 与 i18n catalog 测试覆盖。
- AC-14：下载成功（先 tax 后 Platform、操作人 Header、不带 Query、不转发浏览器凭据与 Idempotency-Key）、单据不属于订单 404、税票未开具 404、文件服务 503（不外露地址、带 Request ID）、下载带 Query 400。Customer API 部分不在 W4。

## 3. 本机 SQL

无。注册 SQL 只写文件，未在任何库执行；块号用 `__TAX_BLOCK__` 占位，未替换时文件语法错误，不会误执行。启用（第 D 段）与回滚（第 E 段）以注释形式写在同一文件，未验证。

## 4. 偏离技术方案之处

1. **下载路径带订单号。** 方案 10.3 为 `POST /documents/{documentId}/download`，但 tax-api 只有按订单读取（`GET /tax/v1/orders/{orderNo}`），没有按单据 ID 读取，BFF 无法仅凭单据 ID 找到 `documentFileAddress`。实现为 `POST /tax/v1/orders/{orderNo}/documents/{documentId}/download`。**主会话 2026-10-29 已批准**：不新增 tax 接口；该路径同时证明单据属于该订单，单据不存在或未 ISSUED 均返回 404。
2. **BFF 路径 `/tax/v1/...`。** 方案写 `/operations/tax/v1/...`，按 Operations 入口前缀理解：外部路径 `/api/operations/tax/v1/...`，BFF 路径去掉 `/api/operations`，与既有路由一致（**主会话已批准**；注册 SQL 使用同一组路径）。路由码沿用财务前缀 `operations-finance-tax-*`。
3. **页面上的名称用 ID 显示。** 原型的 Client 名称（Nimbus Notes）、商品名称（Nimbus Notes Pro · 年付）、纳税主体名称（Demo PayFac OE）不在 tax 响应中，页面显示 Client ID、行引用（Client 商品 ID）× 数量、纳税主体 Party ID 与 entityNo。**已在第 4a 节处理。**
4. **原型 18 的"支付成功"改为"税务服务不记录"。** PSP 订单 tax 不建正式事实（AC-04），BFF 无从得知支付结果；该步状态为 `UNTRACKED`，文案"税务服务不记录，以订单为准"，不伪造支付成功。
5. **原型 16 的"平台运营代操作"（ManagedBy）未显示。** tax 订单响应不含主体托管方式。**已在第 4a 节处理。**
6. **申报底稿待补汇率时不显示期间与截止日。** `PENDING` 时 tax 不返回底稿行（`reportingEntries` 为空），页面显示申报频率、订单币种税额与"SAR · 待补汇率"；READY 时显示期间、申报币种金额与截止日。
7. **财务运营角色。** 方案 12 写"授予现有财务运营角色"，库中没有独立财务角色，财务各页都授予平台管理员 `900000000000000510`，注册 SQL 沿用（休眠为 REVOKED）。
8. **文件写入路由的桶与扫描策略。** 注册 SQL 以 `INSERT … SELECT` 复制默认私有路由 `(*, PRIVATE)` 的桶、扫描策略与期限；执行前须确认该策略允许 `application/pdf`，否则阶段 6 改用专用策略。
9. **阻断原因路径。** `lines[n].categoryCode` 中的 n 按行号（1 起）解析，W1 已确认（第 4a 节）。

## 4a. 主会话复核后的跟进（2026-10-29 晚）

主会话接受偏离 1、2、4、6、7、8（1、2 已批准：下载路径带订单号、BFF 路径 `/tax/v1/...` 经 Gateway 暴露为 `/api/operations/tax/v1/...`，注册 SQL 同一组路径；单据不属于该订单或未 ISSUED 均返回 404，不新增 tax 接口）。偏离 3、5、9 按下列方式处理：

| 项 | 处理 |
|---|---|
| 3 商品名称 | W1 变更 2 新增 `lines[].description`。BFF 页面模型 `lines[].description` 透传；页面明细显示"说明 × 数量"，没有说明时退回行引用（Client 商品 ID） |
| 3 Client / 经营主体名称 | ops-bff 已有 client-master 端口：`OnboardingAdminClient.client` 取 Client 法定名称，`EntityAdminClient.operatingEntity` 取经营主体法定名称。新增 `TaxOrderPartyNames`：tax 读取成功后并发读取两路，不在任何事务内，2 秒上限；任何失败（不存在、超时、依赖不可用）都降级为 null，页面显示 ID。纳税主体名称只在纳税主体 Party 等于经营主体 Party 时填写。页面标题行与最近查询显示"名称（ID）" |
| 3 名称不扩大访问 | client-master 管理接口只核对会话存在、不做查看授权（`ClientMasterEntityAdminApi` 类注释；Controller 只调用 `actor(...)`），属于主会话所列情形 (b)。因此每一路名称读取前，BFF 以操作人会话向 PLT-I02 核对既有详情页路由：`operations-client-admin-detail`（Client 详情，块 138）、`operations-operating-entity-detail`（经营主体详情，块 137），与这两个页面自身的 Gateway 绑定权限相同。被拒或 Platform 不可用时不调用 client-master，名称为 null |
| 5 操作方 | W1 变更 2 新增 `registrations[].managedBy`。BFF 页面模型 `managedBy` 取当前报价（已登记时取登记依据报价）引用的注册；页面标题行末尾显示"平台运营代操作"（PLATFORM_OPERATIONS）或"持有方操作"（SUBJECT_HOLDER），没有注册（阻断、不由平台计税）时不显示 |
| 9 路径行号 | W1 确认：变更 2 起 `lines[n]` 的 n 是行号 `lineNo`（从 1 开始），与 BFF 解析一致；注释已更新 |
| 附带：不由平台计税原因 | W1 变更 2 新增 `notManagedReason`（SUBJECT_NOT_REGISTERED / TAX_RESPONSIBLE_UNRESOLVED），且 TAX_RESPONSIBLE_UNRESOLVED 时纳税主体为空、没有证据。BFF `notManaged.reason` 透传；页面右栏增加"原因"行，提示卡文案按原因区分（PSP 合同 / Client 提供收单主体）；纳税主体可为空 |
| 附带：变更 3 | 税类改为 Client 自由文本（≤64，可含空格与非 ASCII），未知税类为 BLOCKED UNKNOWN_TAX_CATEGORY、每行一条。BFF 原样透传（行号解析不变）；页面以文本节点输出（React 转义，ICU 只把参数当数据），解码只校验非空与 ≤64。新增 `test_freeTextCategoriesPassThroughPerLine` 与 Web 用例（含 `<b>`、引号、大括号、中文），并断言税务页源码不使用 innerHTML |
| 附带：变更 1 | INCLUSIVE 且 BLOCKED / NOT_MANAGED 时 `netAmount` 可为 null，NOT_MANAGED 行税额为 null；页面解码已放宽，显示"—" |

**新增与修改的文件（在第 1 节基础上）：** ops-bff `service/processor/TaxOrderPartyNames.java`（新增）、`GetOrderTaxRequest`（带操作人）、`TaxOrderController`、`GetOrderTaxProcessor`、`TaxOrderPageSections`、`TaxOrderPageResponseBody`（`buyerClientName`、`entityName`、`taxSubjectName`、`managedBy`、`lines[].description`、`notManaged.reason`）；测试 `TaxOrderBrowserAutoTest`、`TaxOrderResponseBuilder`、`PlatformOperationsSimulator`（两个详情页路由码映射到 Client / 经营主体查看权限，默认授予）。ops-web `tax.ts`、`TaxOrderView.tsx`、`FinanceTax.tsx`、`tax.test.ts`、`tax.json`（en / zh-CN，211 键）。

**新增测试：**

- `test_taxViewOnlyOperatorSeesIdsOnly`：只有 TAX_VIEW 的操作人，PLT-I02 收到两个详情页路由核对且被拒，client-master 零调用，三个名称都为 null，页面显示 ID。
- `test_namesFollowEachViewPermission`：只有 Client 查看权限时只显示 Client 名称，不读取经营主体。
- `test_partyNamesDegradeToIds`：client-master 503 / 403 / 404、经营主体不是纳税主体时页面仍 200，名称为 null。
- `test_notManagedWithUnresolvedResponsibleParty`：CLIENT 来源 OE，纳税主体为空，原因 TAX_RESPONSIBLE_UNRESOLVED，三步进度。
- 既有用例补充断言：`managedBy`、行说明与退回、名称读取带操作人可信头。
- Web `tax.test.ts`：新字段解码（说明超长失败关闭）、操作方与原因文案、提示卡按原因区分、明细退回行引用。

**验证（全部用 `package` / 前台执行）：**

| 命令 | 结果 |
|---|---|
| `./mvnw -o … -pl operations-console-bff/paytech-operations-console-bff-test -am package -Dtest='com/paytech/operations/console/bff/**/*'` | 2472 通过 / 0 失败（TaxOrderBrowserAutoTest 20，含变更 3 用例） |
| `npm test` | 1614 通过 / 0 失败（tax.test.ts 13） |
| `npm run build` | 通过 |
| `npm run i18n:export-source` | 通过，tax 211 键 |

未使用任何本机中间件容器（BFF 测试无数据库；Web 测试借用主工作树 `node_modules` 的临时符号链接，已删除）。

## 5. 原型各状态的实现

| 原型 | 实现 |
|---|---|
| 16 已登记 | 标题订单号 + 徽标"已登记 · 已开票"+ 一行归属（Client、模式、纳税主体）；右上角常驻搜索框；五步进度（前四步绿色，申报底稿琥珀色沙漏"待补 SAR 法定汇率"）；琥珀提示卡 `REPORTING_FX_MISSING`（只影响申报，计费已按 USD 记税额）；左栏三个大号数字（净额 / 税额蓝色 / 应付）+ 灯泡一句话说明（按辖区 + 税种 + 税率，账单国家，注册方案，规则包与修订）+ 明细表（行引用 × 数量，副文字品类 · 处理 · 规则码；税率、税基、税额、合计）+ 报价记录（r2 当前高亮，写明 AE → SA、税额 +10.00 与条款已重新生成；r1 暂估不可支付）；右栏税票卡（缩略图、单号、开具时间、不含个人资料、下载 PDF）+ 一张分段卡（正式登记 / 申报底稿 / 计算依据）；页脚演示规则包说明。无页内标签，徽标只在标题处一个（测试断言） |
| 17 阻断 | 徽标"阻断 · 不可支付"；进度停在"资料提交重算"（红色叉），其后为灰色待定，支付步骤"等待可支付报价"；红色提示卡 `PAYMENT_BLOCKED`；左栏"为什么被阻断"逐条：白话标题、说明（含国家与行号品类）+ 原因码、"由谁处理"；报价记录 r2 红色当前；右栏"税票与登记"空状态"尚未支付，没有正式登记和税票"+ "本次匹配"（证据国家与类型、市场准入无记录、规则包未命中） |
| 18 不由平台计税 | 徽标"不由平台计税"（中性灰）；三步进度（建单自报 → 支付不由 tax 记录 → 平台登记不适用，后两步灰色横线图标）；中性提示卡说明由 Client 负责、不是免税；左栏"金额"三数字，税额标"Client 自报税额"灰色，注明 calculation ID 只备追溯；右栏"为什么不由平台计税"（模式、责任方、未在税务服务登记、税票由 Client 自行开具）；无报价记录与页脚 |
| 19 进入页面 | 地址无 `orderNo` 时显示：标题"税务查询"、居中图标与大搜索框 + 查询按钮、说明一句、最近查询卡（订单号、Client 与国家或 PSP、税额 / 自报 / —、紧凑徽标、箭头），右上注"仅保存在本浏览器"。最近查询最多 5 条，读写都包 try/catch，存储不可用或内容损坏时为空 |
| 20 订单未找到 | tax 404 `TAX_ORDER_NOT_FOUND` → 标题"税务查询"+ 右上搜索框保留输入 + 空状态卡"没有找到 {订单号} 的税务记录"与三种可能原因。其他失败显示可重试的失败卡并弹统一错误提示 |
| 21 税票下载失败 | 文件服务 5xx 或网络失败 → 右上角持久错误提示条：标题"税票下载失败"、税票本身不受影响的说明、"重试下载"按钮、"复制请求编号 7c1e…9a02"（复制完整 ID）、可关闭。4xx（不属于该订单或未开具，均为 404）走统一错误提示 |

## 6. 未完成项与建议

- **浏览器检查未做。** 阶段 4 只有单元测试与构建；阶段 5 本机联调时按惯例做 en / zh-CN、1440 / 390 宽度的真实浏览器检查（重点：进度条在窄屏纵排、两栏折叠、下载失败提示条）。
- **发布前置（阶段 6）：** 分配注册块号；Platform 多语言管理创建 `tax` 命名空间后导入 en / zh-CN；ops-bff 部署配置加 `OPERATIONS_TAX_BASE_URL`；platform-support 镜像需包含新的能力清单；Gateway 快照先离线编译本块。
- **ops-bff README** 未更新端点清单（文档由主会话统一处理）。
