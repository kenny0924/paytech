# client-fe 交付记录：client-console-bff + client-console-web（阶段 4 研发）

> 范围：technique.md §2.1 CM-1—CM-9（Client 通道）、§2.2 CP-4、§2.4 Client 门户页面；需求 R10、AC-14。worktree `Code-MoR-onboard`（基于 `ea0272f3`），未提交、未推送、未部署、未执行 SQL。
> 状态：**Web 与 BFF 均完成并通过本地测试**。BFF 已改用 cm-dev 发布的 Owner 接口 `ClientOnboardingCatalogApi`（CM-1/2）、`ClientOnboardingApplicationApi`（CM-6/7/8）与新版 `ClientSubmissionApi.check`（CM-9 `{ready, errors[]}`），过渡用的本地合同已删除；Web 已按 `FormSchemaResponseBody` 实际形状适配（目录项 `code`、规则数值字符串、`fallbackAddressFormat`、关系类型 `ruleRole`、二选一组标题键）。

## 1. client-console-web

### 1.1 文件

| 类型 | 路径 | 说明 |
|---|---|---|
| 新增 | `src/onboarding/model.ts` | 浏览器合同类型与读取校验（jurisdictions、form-schema、申请、需求清单、核对 `{ready, errors[]}`、Problem `errors[]`） |
| 新增 | `src/onboarding/schema.ts` | Schema 纯逻辑：字段键 → 草稿路径、`visibleWhen`、任务归属、证件备选组、本地格式校验（固定位数、`LUHN`、`EG_NID` 校验位注册表）、入驻码格式 |
| 新增 | `src/onboarding/draft.ts` | 本地问题汇总、草稿 PATCH 体、服务端需求级错误 → 字段路径、错误码文案（`onboardingSchema.error.*`） |
| 新增 | `src/onboarding/progress.ts` | 需求键定位（字段 / 人员 / 材料槽 / 声明）、任务状态、结果页阶段、剩余天数 |
| 新增 | `src/onboarding/widgets.tsx` | 控件注册表：TEXT / NAME / TEXTAREA / DATE / COUNTRY / SELECT / BOOLEAN / IDENTIFIER / ADDRESS / URL / EMAIL / PHONE / MONEY |
| 新增 | `src/onboarding/FormTask.tsx` | 草稿表单 hook（失焦自动保存、单飞排队、50 字段分片）与 Schema 渲染器 |
| 新增 | `src/onboarding/{ApplicationRoot,Home,TaskPage,PeopleTask,MaterialsTask,DocumentSlot,DeclarationsTask,ReviewTask,Results,Remediation,StartFlow,Shell,ui,context}.tsx` | 页面与组件 |
| 新增 | `src/onboarding/upload.ts` | 进度传输（XHR，取消）与文件预检 |
| 新增 | `src/onboarding/onboarding.css` | 入驻页面样式（N0 token） |
| 新增 | `src/onboarding/{schema,progress}.test.ts`、`fixture.test-data.ts` | 单测 |
| 新增 | `src/i18n/en/onboardingSchema.json` | 国家包文案英文基准（取自 cm-dev 的 `db/onboardingSchema.en.json`） |
| 改 | `src/style.css` | 全站改为 N0 token（CSS 变量）；保留团队 / 开发者 / 工作区 / 邀请页类名 |
| 改 | `src/main.tsx`、`src/LoginForm.tsx`、`src/TextField.tsx` | 认证 6 页按 C1 重做；新路由 `/onboarding`、`/onboarding/new`、`/onboarding/applications/:id/*` |
| 改 | `src/auth.ts` | `RequestFailure.problem` 保留原始 Problem 体（需求级 errors[]） |
| 改 | `src/onboarding.ts` | 保留命令冻结 / 上传状态机；上传状态与关联体带签发 / 到期日；删除旧审核横幅与类型；`patch` 支持带冒号分区 |
| 新增 | `src/onboarding/documentDates.ts(+test)` | 材料日期规则 |
| 改 | `src/i18n/builtin.ts`、`src/i18n/en/{onboarding,auth}.json`、`i18n/client-console.zh-CN.seed.json` | 文案 |
| 改 | `src/i18n-coverage.test.ts`、`src/workspace.test.ts`、`package.json`（test 脚本、IBM Plex 字体包） | |
| 删 | `src/OnboardingPage.tsx`、`src/catalog.ts(+test)`、`src/submission.ts(+test)`、`src/review-result.test.ts` | 旧单页与 form-options 消费 |

依赖：新增 `@fontsource/ibm-plex-sans@5.3.0`、`@fontsource/ibm-plex-mono@5.3.0`（OFL，本地托管 latin 子集，不引第三方字体请求）。

### 1.2 页面（全部按 N0 / N1—N3 / C1—C4）

认证：登录（错误一行 3px 红竖线 + 密码框标红）、注册（条款勾选）、查收验证邮件（倒计时重发、更换邮箱）、验证成功、找回 / 重置密码。
入驻：入驻码 → 国家与主体（单选卡 + 组织形式）→ 准备清单 → 首页任务清单（6 项，圆点状态、"已完成 n / m 项"、右栏需要准备）→ 公司 / 个人信息、地址与联系方式（Schema 渲染，国家地址组件按组件表排序，目录组件为下拉）→ 人员（最低要求 √ / ○、列表行、侧边抽屉：多角色、持股、UBO 依据、证件类型 + 号码、证件上传）→ 材料（状态机：未上传 → 上传中进度 + 取消 → 检查中 → 已完成；失败重试 / 删除；替换）→ 声明（逐条勾选即记录）→ 核对并提交（汇总页即提交页，分段"编辑"）→ 审核中（撤回）/ 退回首页（期限、逐条去处理、审核记录、撤回）/ 补件聚焦视图 / 已批准（进入工作区）/ 未通过 / 已逾期 / 已撤回。移动端 390px：步骤行"第 n 步，共 m 步 · 名称 + 概览"、底部固定操作栏、人员列表折行。
错误：页顶汇总（3px 红竖线 + 黑标题 + 链接，取焦点，标题加"错误："前缀），字段红框 + 13px 红字；跨页跳转后聚焦并 1200ms 高亮。动效 token 70/110/150/240/400ms，prefers-reduced-motion 下位移归零。

### 1.3 i18n

- `onboarding` 命名空间：新增约 250 个 key（任务、状态、结果页、补件、人员、材料、上传、错误本地校验等），同时删除旧单页不再使用的 key；新增材料文案族 `onboarding.material.<document_type>`（19 种）与 `onboarding.requirement.<requirement_code>`（9 种），覆盖 compliance 程序 DML 全部代码。
- `auth`：新增 / 改 20 个（登录 / 注册 / 验证 / 重置的 C1 文案）。
- `onboarding-schema` 命名空间（新，需在 Platform 为 client-console 创建；Platform 命名空间码规则 `[a-z][a-z0-9-]{0,63}`）：276 个 key，en 与 zh-CN 取自 cm-dev 的 `db/onboardingSchema.{en,zh-CN}.json`，键前缀改为 `onboarding-schema.`。后端 label_key 仍为 `onboardingSchema.*`，页面经唯一映射函数 `schemaI18nKey()`（`src/onboarding/schema.ts`）转换后取文案；覆盖测试按同一映射核对 DML 键，并断言全部命名空间码符合 Platform 规则。
- 种子 `i18n/client-console.zh-CN.seed.json` 同步全部 key。只有 en / zh-CN，无阿语、无 RTL。
- 覆盖测试新增：client-master 初始化 DML 里每个 `onboardingSchema.*` label / help key 都在英文基准中；compliance 程序 DML 每个 requirement_code / document_type 都有 Client 文案；该命名空间键段允许大写（DML 代码）。

### 1.4 验证

- `npm test`：97 通过 / 0 失败（新增材料日期规则与日期随关联提交 / 恢复坐标测试）（含 i18n 覆盖、CJK 扫描、DML 同步、Schema / 进度单测、既有上传状态机测试）。
- `npm run build`：通过（主包 ~500 kB 触发 Vite 体积提示，未拆包）。
- 浏览器验收：`verification/preview-onboarding-v2.mjs`（loopback 模拟器，Schema 由仓库 DML 现场解析）+ 本机 Playwright 无头 Chromium 截图，1440 与 390 宽度逐页核对：首页、公司信息、地址、人员 + 抽屉、材料、声明、核对并提交、退回首页、补件视图、审核中、已批准、未通过、已逾期、已撤回、入驻码 / 国家主体 / 准备清单、登录错误、注册、查收邮件。文件直传在模拟器中不可用（只看到失败态）。

## 2. client-console-bff

### 2.1 已完成

- 删除 `form-options`（Controller 方法、`ClientFormOptionsProcessor`、请求 / 响应体）、Compliance `MaterialRequirementsApi` 装配与按资格类型查询材料要求（`MaterialSubject`、`OnboardingQualifications`）；申请详情不再返回 `materialSubjects`（材料槽随 form-schema 下发）。
- CP-4：`ReviewResult` 改为 `{caseStatus, outcome, roundNo, publicNote, decidedAt, dueTime, errors[{requirementKey, subjectPartyId, code, reason, remediation, dueTime}]}`；结果与申请状态一致性：RETURNED / APPROVED / REJECTED 一一对应，WITHDRAWN 接受 WITHDRAWN 或 EXPIRED；错误码 / 补救码须为大写代码、条数 ≤ 200，否则整段不可用。审核结果读取扩展到 WITHDRAWN。
- 测试：`ClientOnboardingHttpAutoTest` 去掉 form-options / 材料要求用例，退回用例改断言需求级错误与期限，新增"撤回区分逾期""退回错误码异常不可用"。

### 2.2 新接口（BFF 路径 `/client-onboarding/v1/...`，外部 `/api/client/onboarding/v1/...`）

| 路由码（块 138 已登记） | 方法 / 路径 | 说明 |
|---|---|---|
| client-onboarding-jurisdictions | GET `/jurisdictions` | CM-1 透传，形状须为数组或 `{items[]}` |
| client-onboarding-form-schema | GET `/form-schema?jurisdiction&partyType[&organizationType][&applicationId]` | CM-2；拦截器只放行这四个单值 Query（applicationId 为正整数，Web 在已有申请时传入以取冻结版本与责任方）；辖区 / 组织形式须为大写代码，主体类型限 INDIVIDUAL / ORGANIZATION；回执须含 schemaId 与 sections[] |
| client-onboarding-declaration-create | POST `/applications/{id}/declarations` | CM-6；只接受声明代码 + TRUE/FALSE（+ termsVersion ≤64），转发 User-Agent 与 `X-Gateway-Client-Ip`（如有） |
| client-onboarding-requirements | GET `/applications/{id}/requirements` | CM-7 透传 |
| client-onboarding-withdrawal | POST `/applications/{id}/withdrawal` | CM-8；空 Body 命令（同 submissions，不要求 JSON Content-Type） |
| 既有 c16 | GET `/applications/{id}/submission-check` | 改为转发 CM-9 新体 `{ready, errors[]}`，旧 `issues[]` 形状按依赖不可用 |
| 既有 c05 | GET `/applications/{id}` | 透传草稿新字段 `jurisdictionCode, organizationTypeCode, schemaId, reviewDueTime`；DRAFT / RETURNED 并发合入需求清单（`requirementsStatus` NONE / AVAILABLE / UNAVAILABLE + `requirements[]`，≤1000 项） |

错误映射：`OnboardingErrorCode` 注册到 client-master 客户端；ONBOARDING_CODE_INVALID / JURISDICTION_NOT_ACCEPTED → 422，APPLICATION_NOT_WITHDRAWABLE → 409。已把路由清单发给 infra-dev（块 138 CLIENT 增补；c03 form-options 停用）。

### 2.3 验证

`./mvnw -o -f client-console-bff/pom.xml test`（私有仓库 `~/.m3-onboard`）：9 个测试类 92 个用例全部通过（改用 Owner 接口后复跑），其中 `ClientOnboardingHttpAutoTest` 32 个（新增：辖区与 Schema 严格 Query 转发 / 形状不符 503 / 422 透传、声明校验与转发、撤回空 Body、详情合入需求清单与 Schema 坐标、核对新体与旧形状拒绝、撤回区分逾期、退回错误码异常不可用），`ArchitectureAutoTest` 通过。未运行全 reactor。

## 3. 合同假设与遗留

- form-schema：有 applicationId 时只需该参数（Owner 返回冻结版本与责任方材料），否则辖区 + 主体类型必填；BFF 校验同此规则。
- 关系人证件槽：CM-2 已合并关系人程序材料，subjectRole 为 `ANY_RELATED`（cm-dev 确认）；Web 把非 PRIMARY 槽位渲染到人员抽屉，通用角色适用于每位关系人，若角色等于某关系类型 / 规则角色则只对担任该角色的人员生效（`people.ts`，有测试）。缺证件的需求键为 `document.IDENTITY` + 该人员 subjectPartyId，422 字段为 `people[<partyId>].document.IDENTITY`，均可定位到人员抽屉的证件槽。
- 是 / 否控件写 "true" / "false"（cm-dev 确认）；证件日期字段为 `identifier.issueDate / expiryDate`。
- 提交 422：Foundation HTTP 客户端只为带 Body 的请求透传 Problem `errors[]`，正式提交为空 Body，cm-dev 的 `errors[{field, message}]` 不会到达浏览器（BFF 测试记录此行为）。Web 兜底：`problemErrors()` 解析 `{field: 需求键 | people[<partyId>].<键>, message: 错误码/补救码}` 为需求级错误（若将来透传即生效）；SUBMISSION_INCOMPLETE 不带逐项问题时自动重跑核对接口，错误汇总逐条链接到字段 / 人员 / 材料 / 声明。若要透传需改 Foundation `HttpServiceClientInterceptor`（不在本范围）。
- 材料日期（已做）：选定文件后先填签发日期 / 到期日（标签带文件类型名），槽位有 maxAgeDays 时签发日期必填并按时效校验，Schema 下发 `expiryRequired=true` 时到期日必填，其余可选；签发日期不晚于今天、到期日须晚于今天；错误为红框 + 红字。日期存入上传恢复坐标并随关联（C12）提交；已上传材料行显示签发 / 到期日。当前 compliance 程序 DML 无到期要求字段，故到期日在 AE/SA/EG 均为可选。
- 删除材料与撤回改为行内确认（不再使用浏览器 confirm 或遮罩对话框）。
- 审核方显示名：页面不写死任何审核方。cm-dev 已在 C05 草稿读出增加 `responsiblePartnerId / responsiblePartnerName / declarations`，BFF 申请详情原样透传（测试覆盖）；有名称时顶栏"由 X 审核"及以 X 为主语的标题，否则中性文案且不显示审核方一行。声明勾选优先读 `declarations`（同类型取最新一条），无记录时退回需求清单推断。
- 撤回（与原型不同）：按状态机 SM-A 只在 DRAFT / RETURNED 提供——草稿首页右栏与退回首页右栏各有"撤回申请"行内确认；审核中页面不提供（C4 原型画了，主会话 2026-10-27 确认原型有误）。Owner 若仍返回 409 APPLICATION_NOT_WITHDRAWABLE，行内确认里显示失败原因。
- 审核方显示名 `responsiblePartnerName` 不在合同中，缺省显示"Paytech"；入驻码不做提交前预查（B5 不提供 Partner 查询），无效码在创建时提示。
- 人员证件槽：按 form-schema `materials[].subjectRole` 非 PRIMARY 视为关系人材料（预期 `RELATED_PERSON`）。
- 注册页"条款与隐私"勾选只做前端门槛，不记录同意（平台条款同意在入驻声明 `PLATFORM_TERMS` 中记录）；认证页页脚"隐私 / 条款 / 帮助"链接因无确定地址未放。
- 注册验证邮件有效期文案已按 Platform 实际值改为"30 分钟"（`ClientEmailChallengePolicy.expiresAt`：SIGN_UP_EMAIL 1800 秒，PASSWORD_RESET 1200 秒）；重置密码页不写时长。

## 4. 发布后修正（mor-test ui-smoke，2026-10-27，代码冻结例外，仅 Web）

- i18n 命名空间 `onboardingSchema` → `onboarding-schema`（Platform 命名空间码规则）；后端 label_key 经 `schemaI18nKey()` 映射。
- 草稿保存：每份草稿串行队列（同时只有一个 PATCH 在途），失焦后 800ms 合并、执行时计算改动；429 按 0.8 / 1.6 / 3.2 秒退避重发同一体；失败不前移基线，页脚显示"未保存 · 重试"，"继续"不离开本页；422 字段错误挂到字段（N0 红框红字）与页顶汇总；离开页面时发出未到时的延迟保存。
- 错误文案按代码本地化（含 Request ID），不再显示服务端 detail；人员抽屉汇总的证件子路径标签（证件类型名 / 签发国 / 到期日）。
- 不再提交 `address.addressFormatCode`（cm-dev 同时让后端可选接受该字段）。
- 已核对真实形状 form-schema（含 `organizationTypeCode: "*"`）对 AE / SA / EG 机构与个人各 Schema 的解码，新增 `countrypack.test.ts`。
- 验证：`npm test` 111 / 111，build 通过；rel-build 重建镜像 `sha256:4aaecccb035a4de03a8bebeb886bb732b023b747019ba95dc7bf75ff483f044d`。
- 外国地址（2026-10-27）：地址国家不是辖区国家时（如阿联酋申请中住在英国的受益所有人、沙特申请的阿联酋经营地址）改用 form-schema 的 `fallbackAddressFormat`（GENERIC）组件；国家改变时切换布局，保留两种格式共有组件、清空其余；必填按当前格式校验；仍不提交 addressFormatCode（client-master 按国家推导）。纯函数 `activeAddressFormat / addressPathsToClear`（`schema.ts`），测试见 `countrypack.test.ts`。`npm test` 113 / 113，build 通过；rel-build 重建镜像 `sha256:9f85e61cb663dc328dfefbf4d1ff72d9462f9a60876a552c4f49b8161bac456f`。
- ui-smoke 第二批（2026-10-27）：每页只 PATCH 本页字段；422 指明的字段标红并暂停重发（直到再次修改），未指明时逐字段隔离，其余字段照常保存（`sendIsolating`，有测试）；390px 顶栏审核方截断 / 480px 以下隐藏；窄屏撤回放入主列；zh-CN 列表用 conjunction；上传 / 检查中禁止离开材料步骤并提示，离页后仍在检查的上传由申请页后台续传关联（剩余缺口：传输字节途中经左侧任务栏离开会取消该次传输）。`npm test` 116 / 116；镜像 `sha256:14690d94f97caffee856070358174a58d5499b772965fd8165231956bc0d4c00`。
- 遗留（主会话记为后续）：C05 不提供 submittedAt / withdrawnAt，结果页省略"提交时间 / 撤回时间"行。
- 人员抽屉（2026-10-27）：新关系带 `effectiveFrom`（今天，cm-dev 同时让服务端默认）；422 的 `relation.*` / 人员字段错误挂到抽屉控件（持股、受益所有人依据、角色、资料字段，`drawerErrors` 有测试）；无字段的 INVALID_DRAFT 显示本地化通用说明 + 请求编号。`npm test` 117 / 117；镜像 `sha256:34d3d6e303fd7f62fa7da825599391a2f34ba569e4bbf92a8839d5859a133998`（现行 client-web）。
