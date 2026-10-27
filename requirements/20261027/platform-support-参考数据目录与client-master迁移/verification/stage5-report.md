# 阶段 5：综合验证与本地验收（e2e-dev，2026-10-27）

范围：本机 e2e 栈（不涉及 mor-test）；未提交、未推送。代码来自 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refdata`（基于 `aba76a1a`）。时间为 UTC。阶段 5 分两轮：第一轮综合验收（10:39—11:54），用户接受后第二轮修复 P2 并复测（12:00—12:47）。

## 1. 制品

构建：`./mvnw -B -o -DskipTests -pl <app> -am clean package`（JDK 25，私有仓库 `~/.m3-refdata`），构建前后 worktree 状态指纹一致。

| 服务 | 第一轮（`e2e/staging/refdata-20261027a/`） | 最终运行（`e2e/staging/refdata-20261027b/`） |
|---|---|---|
| platform-support | `ab02b0b7900183c1041fa65b6da380ce4ed2c4ec6b17995530d4fae88fe92e13` | 同左（未变） |
| client-master | `4e1505cf4b15484b13e8402efcbc67c617e8da6d9732ff46e937f79639f2e21c` | `910bfc85c174e05542ee2d9cc30bfc6ed838622871bccbe6b6583e584be0b3b7`（cm-dev F-5：管理端辖区返回 `addressComponents`） |
| client-console-bff | `b7c34ec6385d47889b34d9a33fe80d57094a014050b499258cb2a60b22837909` | 同左（未变） |
| operations-console-bff | `556f78a381d576f9eeb8e8cb4a4afa87481d604700729eec4f29554fe1147900` | `ee26358f49afdae226fd315d548025b883e2e669bc348c4c93e8f05eef464ff8`（ops-fe 构建，含 worktree client-master-api） |

两个 Web 由 worktree 的 Vite 直接提供（无构建产物）。Platform 代码在阶段 5 未变：E-2 / MCC / 行政区英文名 / 行业说明都是数据、SQL、语言包与测试改动。

## 2. SQL（详见 [sql-execution-log.md](./sql-execution-log.md)）

- e2e 服务实际读的库：`mor_platform_v2e2e`、`mor_client_master_v2e2e`（阶段 4 只执行了 auto 测试库 `mor_platform` / `mor_client_master`）。执行前备份 `/Users/kenny/mor-backups/refdata-20261027/e2e-*.sql.gz`。
- Platform：块 139 预检 → 停旧 Platform → `changes/2026-10-27-reference-data.sql` → 新 Platform → 块 139 休眠注册（59 行）→ 激活。币种 4、国家 249（补全）、行政区 47、行业 42、MCC 298（高风险 14）；Gateway routeCount 497 → 497 → **502**。
- client-master：`2026-10-27-reference-data-catalog.sql`（退役目录项 48 → 0、8 个币种字段与 2 个地址组件改为 `REF_*`、存量省州 13 行 `AE_AZ` → `AE-AZ`）+ 注释 DDL（列属性不变）。
- **环境修复（用户批准，不是本需求的 SQL）**：本机 `mor_platform_v2e2e` 从未注册币种控制台，补注册并激活**块 125**（54 行），Gateway 502 → **506**。其他缺失的块未补。
- **E-2**：新增 `changes/2026-10-27-reference-data-subdivision-local-name.sql`（+ 回退），在 `mor_platform` 与 `mor_platform_v2e2e` 执行：47 行全部有值，30 行改变，重复执行为空操作；auto 测试库再把这 30 行 version 改回 1，与测试 `ddl.sql` 种子一致。
- 验收中的临时改动（数行置 INACTIVE、两表临时改名）均已恢复并回读核对；验收用 DRAFT 币种 XTS 已于 12:47 删除，币种为 AED / EGP / SAR / USD 4 个 ACTIVE。

## 3. 替换、回归与 AC-02

- 第一轮用 `verification/local-e2e/swap.py`（`*.pre-refdata` 备份、临时文件 + 改名、按捕获的 argv / 环境重启）依次替换 Platform → client-master → client-console-bff → operations-console-bff；第二轮依次替换 client-master → operations-console-bff（`*.pre-refdata` 仍是本需求之前的原始 JAR）。每次 readiness 均 UP，运营登录正常。第二轮之后 client-master、ops-bff 无新 ERROR（ops-bff 只有换 JAR 前就存在的 `/authorization/**` "Cannot replace a committed response"）。
- 旧接口（S-01）：`/platform/v1/country-regions` 与替换前逐字节相同（249 行，元素只有 `countryCode` / `regionCode`）；`/platform/v1/currency-specs` 外层与元素字段（`currency, minorUnit, roundingMode, status, updateTime, version`）不变，只多了 AED / SAR / EGP 三行；响应 535 字节（XTS 存在期间为 665），远小于 ledger 严格模式上限 8192。
- **ledger / billing（AC-02，按 team-lead 决定 a 记录现有证据）**：替换后 10:40—12:47 每 60 秒一次刷新，两服务日志 `Currency snapshot refresh failed` 0 条、ERROR 0 条，readiness（含 `currencySnapshot`）始终 UP；刷新失败（含严格 JSON 解析失败）都会记 WARN，因此可确认刷新一直解析成功。
- **"重启后首次加载"的更强证据推迟**，原因是一个本机环境问题：`local-environment/e2e/jars/paytech-{ledger,billing}-app-exec.jar` **已不在磁盘上**，只被运行中的进程（pid 63824 / 63723，10-19 启动）以已删除的打开文件持有，任何位置都找不到副本。重启会丢失这两个服务且无法恢复，因此按决定 a 既没有重启，也没有重建或替换它们的 JAR。
- card：旧国家接口逐字节相同，card 日志 0 ERROR（未执行 BIN 导入）。
- Web：client-web（:18302）、ops-web（:18301）改由 worktree 的 Vite 提供。原 :18301 是主工作区 Vite（他人共用）；旧版 ops-web 读不了新 ops-bff 的行业树，所以没有改回。恢复命令：`cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web && nohup node ./node_modules/.bin/vite --host 127.0.0.1 --port 18301 --strictPort >> ../local-environment/logs/ops-web.log 2>&1 &`。

## 4. 多语言

脚本 `verification/local-e2e/i18n_local.py`（经 Nginx → Gateway → ops-bff → Platform；KEY_IMPORT 是整应用 key 集合，所以 en = worktree Web 导出 + refdata）。

| 应用 | 过程 | 当前发布 |
|---|---|---|
| client-console | 本机此前没有任何命名空间与发布：新建 auth、common、developer、enum、invitation、onboarding-schema、onboarding、team、workspace、refdata，导入 1381 key（#1）→ MCC 中文（#2）→ 行业说明 + 行政区英文名（#3）→ 确认基准变更的译文（#4） | **#4** READY，20 / 20 |
| operations-console | 新建 onboarding-schema、reference-data、refdata，新增 1245、基准变更 1、废弃 11（#3）→ MCC 中文（#4）→ 行业说明 + 行政区英文名（#5）→ 确认基准变更的译文（#6）→ ops-fe 文案改动（新增 1、废弃 `reference-data.industry.column.{code,name}`，#7） | **#7** READY，52 / 52 |

refdata（两个应用相同）：**429 个 key，zh-CN 429 个**——行业名称 42、行业说明 42（`refdata.industry.<CODE>.description`，F-6）、行政区 47、MCC 298。线上 en / zh-CN refdata 与 `release/i18n-bundles/*.refdata.{en,zh-CN}.json` 逐条相同。

- **MCC 中文（用户决定全部翻译）**：生成源 `db/platform-support/generator/sources/mcc-zh-CN.json`（平台自编，按国内收单常用叫法；原 32 个建议 MCC 译文不变，由生成脚本断言；覆盖不全即失败）。上游 `mcc_codes.csv` 中 6011 的英文与 6010 相同（"Manual Cash Disbursements"），6011 实为 ATM 自动取款，中文写"自动取款"，已在 README 说明。
- **行政区英文名（F-2）**：`sources/subdivision-en.json`（47 个通用英文名，如 Dubai、Abu Dhabi、Eastern Province）只用于 en 文案，库中 `name` 保持 ISO 拉丁转写。
- **行业说明（F-6）**：en = 库中 description，zh-CN 来自 `sources/industry-description-zh-CN.json`；生成脚本断言 zh-CN key 集合等于 en。
- 重新生成后 SQL 输出逐字节不变（只有语言包与 summary 变化）。
- 发现：英文基准改变而译文不变时，翻译导入记为"不变"，不会把译文的依据修订更新到新基准（46 条被标为"基准已变更"）。已用批量保存原文确认到当前基准（与运营在翻译页确认等效），两应用剩余 0；`i18n_local.py` 已内置此步骤，阶段 6 发布同样需要。

## 5. 验收（API，经真实入口）

脚本 `verification/local-e2e/refdata_acceptance.py`（复用切片 1 验收辅助函数）。结果文件：
- `acceptance-local-20261027T113104Z.json`：全量 P / R / F / A / K / U / S，231 PASS / 0 FAIL；
- `acceptance-local-20261027T113334Z.json`：A 复跑（真实停用币种）；
- `acceptance-local-20261027T114316Z.json`：C 币种 6 / 6（块 125 补注册后）；
- `acceptance-local-20261027T123130Z.json`：第二轮换 JAR 后 R / C / F / A / K，98 PASS / 0 FAIL；
- `acceptance-local-20261027T105213Z.json`：P / R 及块 125 补注册前的币种 404 记录。

| AC | 结果 | 证据 |
|---|---|---|
| AC-01 | 通过（库） | 旧表不存在，五张 `t_ref_*` 存在；PDManer / schema / 测试 DDL 一致性由 pf-dev 核对；local_name 注释在 schema、pdma.json、变更脚本、测试 DDL 四处同步 |
| AC-02 | 通过（重启证据推迟） | 见第 3 节 |
| AC-03 | 通过 | 5 个新接口全字段；status / codes / level / parentCode 筛选；MCC 分页（共 298、两页衔接、越界页保留总数）与关键字（名称包含、编码前缀）；8 类 400；不返回本地化名称；运营 4 个只读接口经块 139 路由 |
| AC-04 | 通过 | 249 国三位码 / 数字码 / 英文名齐全；区号 242（AQ BV GS HM PN TF UM 无）；法定货币 248（AQ 无） |
| AC-05 | 通过 | 大类 10、细类 32；32 个建议 MCC 都存在且 ACTIVE |
| AC-06 | 通过 | `t_catalog_item` 三类 0 行；守卫测试（阶段 4）；form-schema 无旧三类目录 |
| AC-07 | 通过 | 不存在 / 停用的国家、币种、行政区及行政区与地址国家不一致 → `FIELD_VALUE_NOT_IN_CATALOG` 挂在字段路径；正式提交 422 `SUBMISSION_INCOMPLETE`、仍 DRAFT、无提交号；旧格式 `AE_DU` 草稿保存 422；AE、SA、EG 机构合法提交进入 UNDER_REVIEW |
| AC-08 | 通过 | 只选大类、未知行业、未知 / 停用 MCC、第 2 行大类 → 422 `CLASSIFICATION_INVALID`，`errors[]` 为 `items[i].industryCode / mccCode`；被拒不写入；细类 + 建议 MCC 保存；改选 MCC 5699 后保存的是运营选择；MCC 搜索 |
| AC-09 | 通过 | 只让参考数据接口不可用（临时改名 `t_ref_country`、`t_ref_industry`，Platform 在线）：form-schema 503 `DEPENDENCY_UNAVAILABLE`；提交核对 / 正式提交 503，仍 DRAFT、无提交号；分类保存 503、原分类不变；审核详情可打开、行业标 UNAVAILABLE。整个 Platform 停止时同样 503 且不写入 |
| AC-10 | 通过 | 存量 RETAIL / 5999 案件详情可读 |
| AC-11 | 通过 | Client 冒烟 en / zh-CN × 1440 / 390 |
| AC-12 | 通过 | 运营分类冒烟 en / zh-CN |
| AC-13 | 通过 | 参考数据页（4 页签、搜索、筛选、空状态、失败重试）；币种接口：ACTIVE 改冻结字段 422（逐字段）、logo 不存在 / 非公开 422、被拒不写入、DRAFT 可改全部属性；币种页草稿编辑与换图标弹窗 |
| AC-14 | 通过 | 缺译回退英文 `name`；页面无原始 key；refdata 现已全部有中文 |

E-2：行政区 `local_name` 47 / 47 来自 CLDR（0 缺失）；运营参考数据 · 行政区页签显示 AE 7、SA 13、EG 27 个阿拉伯文名。Platform referencedata 测试包 45 / 45（9 个类）。

## 6. 修复与改动

| # | 级别 / 来源 | 内容 | 文件 | 复验 |
|---|---|---|---|---|
| 1 | P1（e2e-dev） | Client：已保存的参考数据编码失效时任务页不提示字段 → 打开任务页即挂到字段并显示页顶汇总（原型 D），语言包加载后刷新文案，已改动字段不回挂 | `client-console-web/src/onboarding/progress.ts`（`fieldRequirementIssues`）、`TaskPage.tsx`、`progress.test.ts`（+1） | `tsc --noEmit` 通过；`npm test` 129 / 129；Client 冒烟 4 × 21 / 21 |
| 2 | E-2（e2e-dev） | 行政区 local_name 改用 CLDR 阿拉伯文名 | 生成脚本改读 `generator/sources/cldr.common.subdivisions.ar.xml`（tag release-48-2，去掉手写列）；`changes/2026-10-27-reference-data.sql`、`platform-support-initial-data.sql`、测试 `init/ddl.sql` 的 30 行种子；四处列注释（schema、pdma.json 直接编辑、变更脚本、测试 DDL）；新增 `changes/2026-10-27-reference-data-subdivision-local-name.sql` 与回退；`CountrySubdivisionQueryApiTest` 预期改为 `امارة دبي` | referencedata 45 / 45 |
| 3 | MCC 中文（e2e-dev） | 298 个 MCC 全部中文 | `generator/sources/mcc-zh-CN.json`、生成脚本加载与断言、README；`release/i18n-bundles/*refdata.zh-CN.json` 与 plan | 本机重新发布并逐条核对 |
| 4 | F-2、F-6 数据（e2e-dev） | 行政区通用英文名；行业说明 i18n key | `generator/sources/subdivision-en.json`、`industry-description-zh-CN.json`、生成脚本、README；`release/i18n-bundles/*refdata.{en,zh-CN,plan}.json` | 本机重新发布并逐条核对；冒烟见 ui-smoke 报告 |
| 5 | F-3—F-7（ops-fe、cm-dev） | 运营页面布局、目录并入地址格式目录与预览、根 I18nGate 预加载 refdata（修复复测中发现的"点目录行重置页签"）；client-master 管理端辖区返回 `addressComponents`（client-master 测试 152 / 152，cm-dev） | 见 ops-fe / cm-dev 记录 | 运营冒烟 en / zh-CN 各 29 / 29 |
| 6 | 工具（e2e-dev） | `i18n_local.py` 增加 refdata 译文替换与"基准已变更"译文确认；验收、冒烟脚本与 Platform 不可用隔离方式 | `verification/local-e2e/`、`verification/ui-smoke/` | — |

CLDR 注意：47 条均为 CLDR `draft="provisional"`，前缀不统一（如 `إمارة أبوظبي`、无 hamza 的 `امارة دبي`、`عجمان`），按用户决定照用，已写入生成脚本 README。

## 7. 浏览器冒烟

见 [ui-smoke/report.md](./ui-smoke/report.md)：Client 4 组各 21 / 21；运营每种语言 29 / 29。P1 F-1 已修；P2 F-2—F-7 全部已修并有断言证据。

## 8. 回退（本机，按顺序）

1. 停 ops-bff、client-bff、client-master、Platform（`python3 verification/local-e2e/swap.py stop <name>`）。
2. `mor_platform_v2e2e`：`block139-deactivate.sql` → `block139-rollback.sql`（需求目录 `db/platform-support/`）。
3. `mor_client_master_v2e2e`：`changes/2026-10-27-reference-data-catalog-rollback.sql`。
4. `mor_platform_v2e2e`：`changes/2026-10-27-reference-data-subdivision-local-name-rollback.sql`（可选）→ `changes/2026-10-27-reference-data-rollback.sql`。块 125 如需撤销：`requirements/20261020/platform-support-币种主数据维护与读取缓存/release/test-deactivate.sql`，再按 ID 900000000000125101—125799 删除（guard → header → route → console_resource → role_permission → permission）。
5. 换回 `jars/paytech-{platform-support,client-master,client-console-bff,operations-console-bff}-app-exec.jar.pre-refdata`（临时文件 + `mv`），`python3 swap.py restart <name>`（捕获的 argv / 环境在 scratchpad `refdata-e2e/proc`，0700）。只回到第一轮版本时用 `staging/refdata-20261027a/`。Platform 登录如 503 `vault-service`：`docker restart mor-local-vault-service-1`。
6. 也可从 `e2e-*.sql.gz` 整库恢复两个库（同时撤销以上 SQL、语言包与块 125）。语言包发布未验证撤销方式；旧前端不读 refdata / reference-data 命名空间。
7. Web：恢复主工作区 ops-web（第 3 节命令）；client-web 原为 `Code-MoR-onboard` worktree 的 Vite。

## 9. 待决与遗留

| # | 事项 |
|---|---|
| O-1 | 本机 ledger / billing JAR 文件缺失（只由运行进程持有）：重启前需要重建或找回；AC-02 的重启证据待此后补 |
| O-2 | 阶段 6：client-console-web 构建须含第 6 节修复 1；ops-web 须含 ops-fe 的 F-3—F-7 与 refdata 预加载；client-master 与 ops-bff 用最终版本；语言包按 refdata 429 / 429 发布，并执行"确认基准已变更译文"一步；E-2 变更脚本列入 mor-test 运行卡（新环境由主变更脚本直接得到相同结果） |
| O-3 | MCC 中文名、行业说明中文、行政区通用英文名都是平台自编，与行业草案一样生产前需合规确认；CLDR 阿拉伯文名为 provisional |
| O-4 | 国旗上传桩未执行，图标全部为占位（阶段 6 发布清单项） |
