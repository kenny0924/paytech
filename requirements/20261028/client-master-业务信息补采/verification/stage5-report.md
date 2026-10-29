# REQ-12 阶段 5 · 本机联调报告（后端与 API，2026-10-29 15:57—16:10 +04）

> 只操作本机 `local-environment`：MySQL 13306 的 `mor_client_master_v2e2e`、本机 e2e 的 client-master / client-console-bff JAR 进程，以及本机 commercial 进程（短暂停止后按原参数恢复）。未碰 mor-test，未提交、未推送。**新 JAR 保持运行。** 浏览器走查由 rq12-web 进行，本报告不操作 Vite 端口与语言包。
> 方法参照 [国家包法务修订 verification](../../../20261027/client-master-国家包法务修订/verification/sql-execution-log.md) 与 [REQ-05 阶段 5](../../platform-support-workflow按钮发起与数据验证/verification/stage5-report.md)。真实令牌经 Nginx 18443（Client）/ 18444（Operations）→ Gateway → BFF → client-master。脚本、日志、备份在 [stage5/](./stage5/)。

## 1. 结论

| 项 | 结果 |
|---|---|
| `mor_client_master_v2e2e` 升级 | v1（ACTIVE 8、字段 275、规则 79，无 `draft_revision`）→ 法务修订 DDL + DML（v2 ACTIVE 9）→ REQ-12 DDL（4 列）→ v3 DML：先 DRAFT（9 个 v3 DRAFT、363 字段、`FULFILLMENT_TYPE` 4 项 DRAFT）核对，再 ACTIVE 发布（ACTIVE 9 / RETIRED 17、字段 965）；各脚本退出 0、停止条件全部为 0 |
| client-master 换 worktree JAR | 运行中 JAR 不比 base 新（见第 2 节）；新 pid **18374**，8 秒 ready |
| client-console-bff 换 worktree JAR（用户授权） | 原 JAR 为法务修订前构建，严格解码与新 client-master 不兼容；新 pid **20527**，4 秒 ready |
| ops-bff | 不换：运行中 pid 92955 为今天 REQ-05 构建（base 6753faba，已含法务修订），兼容 |
| commercial | 本机目录 ACTIVE 支付方式为 **APPLE_PAY、GOOGLE_PAY、CARD 3 项**（无 STC_PAY）；场景 U 期间停止两次、均按原 argv / 环境恢复，当前 pid **22407** ready |
| API 联调 `stage5/stage5_e2e.py`（第 2 次运行，结果 `stage5/stage5-e2e-result.json`） | **179 PASS / 0 FAIL / 0 SKIP / 5 INFO** |
| 第 1 次运行（`stage5-e2e-result-run1.json`） | 169 PASS / 4 FAIL：3 项为断言口径（门户 503 的 code，见第 5 节发现 1），1 项为复用脚本的行业目录结构旧写法（已在本脚本内改写）；均非产品缺陷 |

## 2. 环境变更清单（时间 +04）

| 时间 | 动作 | pid / 结果 | 回滚 |
|---|---|---|---|
| 15:57:12 | `mysqldump --single-transaction` 整库 `mor_client_master_v2e2e` | `stage5/backup/mor_client_master_v2e2e-pre-rq12.sql` | 见第 7 节 |
| 15:57:13 | `changes/2026-10-27-legal-pack-revision-ddl.sql`（日志 `logs/01`） | 两列 `draft_revision` 就位；旧 client-master 健康 | 同上 |
| 15:57:56—15:58:18 | client-master：核对运行中 JAR（pid 91115，sha256 `c2c228fd…`，10-28 00:48，法务修订阶段 5 恢复的 refclient 构建）——类清单是 worktree 构建的子集，e2185c1b..6753faba 之间 client-master 只有 ac3145f4（法务修订，base 已含），**不比 base 新**；`cp -p` 备份 → `.tmp` + `mv -f` 安装 worktree 构建（sha256 `89e2c6d7…`）→ 按捕获的 argv / 环境（131 项）重启（`stage5/swap.py`，日志 `logs/02`） | 91115 → **18374**，ready 8 秒，重启后无非预期 ERROR | `jars/paytech-client-master-app-exec.jar.pre-rq12` |
| 15:58—15:59 | `changes/2026-10-27-legal-pack-revision.sql`（ACTIVE，日志 `logs/03`）→ `changes/2026-10-29-business-info-collection-ddl.sql`（`logs/04`） | v2 ACTIVE 9 / v1 RETIRED 8、字段 602、规则 91；4 列就位 | 法务修订回退脚本（已有 v2 / v3 申请时拒绝）；DDL 回退脚本 |
| 15:59 | `SET @cm_bizinfo_status='DRAFT'` + `changes/2026-10-29-business-info-collection.sql`（`logs/05`），核对后再以 ACTIVE 执行（`logs/06`） | DRAFT：v3 9 行 DRAFT、v3 字段 363、目录项 4 行 DRAFT；ACTIVE：ACTIVE 9 / RETIRED 17、字段 965、目录项 ACTIVE | `changes/2026-10-29-business-info-collection-rollback.sql`（已有 v3 申请，**当前会拒绝**，见第 7 节） |
| 16:02:45—16:03:01 | client-console-bff（用户授权）：原 JAR pid 91340、sha256 `b7c34ec6…`、10-28 00:48（法务修订前构建，缺 ac3145f4 的 BFF 改动，出站严格 JSON 解码）；`cp -p` 备份 → 安装 worktree 构建（sha256 `168e0c15…`，base 6753faba，BFF 生产代码与 base 相同）→ 按捕获参数（128 项环境）重启（`logs/07`） | 91340 → **20527**，ready 4 秒，无 ERROR | `jars/paytech-client-console-bff-app-exec.jar.pre-rq12`；**client-master 与 client-bff 必须一起回滚** |
| 16:04 前后、16:07:24 | 场景 U：停本机 commercial（第 1 次运行原 pid 27614 → 恢复为 21331；第 2 次运行 21331 → 恢复为 **22407**），每次停机约 1 分钟，按捕获的 argv / 环境重启，JAR 未变 | ready 6 秒；恢复后目录照常 | 无（进程恢复即可） |
| 16:05、16:08 | 场景 L 本机测试数据：仅把本次新建的测试申请改冻结 v2——`UPDATE t_client_application SET schema_id=9270000000900303 WHERE application_id IN (241532771674107904, 241533481480368128) AND status='DRAFT'`（模拟在途申请，同 auto 测试夹具做法） | 2 行 | 测试数据，随清理删除 |
| 16:09 | 浏览器预置 `stage5/provision_browser.py`：账号 D / E / F；F 的申请同样改冻结 v2 机构 `9270000000900301`（application 241533940257533952，1 行） | 见第 3 节 | 同上 |
| 16:09:51 | `mysqldump` 验收后状态 | `stage5/backup/mor_client_master_v2e2e-post-stage5.sql` | — |

Schema 版本：开始前 v1（8 ACTIVE）；结束时 v3 ACTIVE 9（含 SA 自由职业者 v2），v1 / v2 共 17 RETIRED。

非本需求写入：本机 Platform 新增 9 个 `onb-rq12*@example.test` 测试账号（注册 → 邮箱验证用既有 `mint_signup_link` 机制改写该账号自己的注册挑战 → 登录）及其租户 / 文件上传；compliance 新增本次申请的审核案件；复核人沿用已有合成复核人 `240674286610681856`（不新建）。

## 3. 演示数据（给浏览器走查）

凭据：`~/Code-MoR-backups/rq12-stage5/accounts.json`（0600；A—F 的邮箱、口令与 accountId）。运营登录同 REQ-05（`local-environment/e2e/secrets.env` 的 `E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`）。

| 账号 | 邮箱 | 申请 / Client | 状态 | 业务信息（三项） | 用途 |
|---|---|---|---|---|---|
| A | onb-rq12a-1029120613@example.test | 241533217348268032 / Client 241533217348268033，案件 241533301817364480 | **APPROVED**，Client ACTIVE | APPLE_PAY,GOOGLE_PAY；退款政策**空**；INSTANT | 运营 Client 详情（机构未填退款政策 → 空）；门户已批准申请只读 |
| B | onb-rq12b-1029120613@example.test | 241533316505808896 / 241533316505808897，案件 241533467257491456 | UNDER_REVIEW | CARD；`https://shop.example.test/refunds?v=2`；PRE_ORDER 14 天 | 运营"申请"页签：标签、链接新开页、"14 天" |
| C | onb-rq12c-1029120613@example.test | 241533481480368128 / 241533481480368129，案件 241533546651471872 | UNDER_REVIEW（冻结 v2） | 全空 | 运营"申请"页签：在途旧版本 → "未采集" |
| D | onb-rq12d-1029120907@example.test | 无 | — | — | 门户新建 AE 机构 / 个人申请（v3 表单、多选控件、交付方式切换天数） |
| E | onb-rq12e-1029120907@example.test | 241533895114240000 / 241533895114240001 | **DRAFT**（v3） | APPLE_PAY,CARD；退款政策空；PRE_ORDER 21 天 | 门户编辑：多选回显、改交付方式清空天数、http 报错；需求清单退款政策"批准前需补" |
| F | onb-rq12f-1029120907@example.test | 241533940257533952 / 241533940257533953 | **DRAFT**（冻结 v2 机构） | — | 门户在途旧版本：不出现新字段 |

另有第 1 次运行的同类数据（账号 `onb-rq12{a,b,c}-1029120319@example.test`，口令未保留）：申请 241532493927297024（UNDER_REVIEW，案件 241532585883226112，材料已接受、人工检查已 PASS、未批准——审核进行中示例）、241532599334350848（UNDER_REVIEW，PRE_ORDER 14 天）、241532771674107904（UNDER_REVIEW，冻结 v2）。

既有在途 v1 申请：`240923939717525504`（UNDER_REVIEW，案件 **240924025168089088**），三项为空，运营页应显示"未采集"。

i18n：本阶段未发布本机语言包（新增 key 属两个 Web，由 rq12-web 处理）；未发布前页面可能显示 key 或代码。

## 4. API 联调明细（第 2 次运行，179 PASS）

| 场景 | 覆盖 | 关键证据 |
|---|---|---|
| F form-schema（AC-01 / AC-02） | AE 机构 / 个人 v3（`9281200000900301` / `…303`）；四个新字段控件、目录、要求级别（退款政策机构 EVENTUALLY、个人 OPTIONAL，`^https://\S+$`）；天数 `visibleWhen=business.fulfillmentType=PRE_ORDER`；`catalogs.COMMERCIAL_PAYMENT_METHOD` = 本机 commercial ACTIVE 支付方式且顺序一致、`labelKey=onboardingSchema.paymentMethod.<代码>`；`FULFILLMENT_TYPE` 4 项（经 client-bff 透传） | `['APPLE_PAY','GOOGLE_PAY','CARD']` |
| O 机构申请（AC-01—AC-03） | `" card , apple_pay,CARD"` → `APPLE_PAY,CARD`，`"007"` → `7`；改 INSTANT 同一写入清空天数；非预售单独填天数 422；`http://` 422 `须为 https 网址`；非法代码 422；天数 0 / 400 草稿可存、清单 `FIELD_FORMAT_INVALID`；PRE_ORDER 缺天数 `FIELD_MISSING`；机构缺退款政策 `EVENTUALLY_DUE`；填满（主体、2 名关系人、5 份材料、8 项带文档版本的声明）后 ready 且退款政策仍 EVENTUALLY_DUE → 提交 202 → UNDER_REVIEW | 申请 241533217348268032 |
| I 个人申请 | 个人缺退款政策无清单项，支付方式 / 交付方式 CURRENTLY_DUE；填满后 ready | 申请 241533316505808896 |
| U commercial 不可用 | 停 commercial：form-schema、需求清单、提交均 503（门户 `SERVICE_UNAVAILABLE`；client-master 日志同一 requestId 为 `ONBOARDING_DEPENDENCY_UNAVAILABLE`），申请仍 DRAFT、`draftRevision` 不变、草稿保存照常；恢复后目录照常，B 提交 202 → UNDER_REVIEW | requestId 见日志 `logs/11` |
| L 在途 v2（AC-04） | form-schema 为 v2、无新字段与 commercial 目录；填满、清单无新字段、提交 202 | 申请 241533481480368128 |
| R 运营（AC-05） | 案件详情 `application.values.business` 三项（A：APPLE_PAY,GOOGLE_PAY / null / INSTANT / null；B：CARD / https / PRE_ORDER / 14）；Client 详情 `values.business` 三项；在途 v1 案件 240924025168089088 三项全为 null | — |
| P 批准（AC-06） | 接受材料、人工检查 PASS、分类（大类 → 细类首项 + 默认 MCC）、CRR、运营 A 推荐、复核人 B 批准 → APPROVED；`t_party_profile.content_hash` = 按运营读到的资料（含四键）以 RFC 8785 + SHA-256 复算值（`ea9d0531…`），去掉四键复算不等 | 案件 241533301817364480 |

INFO 5 项：本机 commercial 目录、两次停 / 恢复 commercial、场景 L 的测试数据 SQL、"C 不调用 commercial"（本机 commercial 日志不记录请求路径，计数恒为 0，**不能作为证据**；第 2 次运行结果中该项已由 PASS 改记 INFO，零调用由 auto 测试 `BusinessInfoCollectionTest.test_previousVersionApplicationUnaffected` 断言）。

## 5. 发现

1. **门户 503 的错误码为 `SERVICE_UNAVAILABLE`，不是 `ONBOARDING_DEPENDENCY_UNAVAILABLE`**：client-master 按设计返回 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`，client-console-bff 把上游 503 统一映射为自身的 `SERVICE_UNAVAILABLE`（既有行为，Platform 参考数据 / compliance 不可用时相同）。technique 8.1 的 503 码描述的是 client-master 合同；门户页面只看到通用"服务暂时不可用"。不影响本需求，如需在门户区分请另议。
2. commercial 不可用期间 client-master 记录的 `ONBOARDING_DEPENDENCY_UNAVAILABLE` ERROR 行数多于门户请求数（两次停机共 10 行对 6 个门户请求），与阶段 4 观察到的出站重发一致（用户已接受该延迟）。
3. 复用的切片 1 验收脚本 `classify` 仍按平铺行业目录取 `industries[0].code`，ops-bff 已改为大类 → 细类结构，脚本旧写法会 KeyError；本脚本改用细类 `industryCode` + `defaultMccCode`。切片 1 脚本本身未改。
4. 本机 commercial 无 STC_PAY（mor-test 有 4 项）；门户选项随 commercial 目录，无需改动。

## 6. 其他服务影响

- ops-bff 未换（已含法务修订）；其他服务不依赖本次改动。
- commercial 进程两次重启（JAR 未变），停机各约 1 分钟；期间依赖 commercial 的本机流程会失败。REQ-05 会话的数据查询直接读 `mor_commercial_v2e2e`（只读账号），不经 commercial 服务。
- 新 client-master 识别法务修订的 `DECLARATION` / `ATTRIBUTE` 规则作用域；**旧 JAR（`.pre-rq12`）不能在现在的 `_v2e2e` 数据上运行**。

## 7. 回滚方案

已有 v3 申请（A、B、E 等）与 v2 申请，DML 回滚脚本按设计会拒绝；回到原状态的可行办法同法务修订阶段 5：

1. 先导出当前状态（已有 `backup/mor_client_master_v2e2e-post-stage5.sql`，或重新导出）。
2. 停新 client-master 与 client-bff，`DROP` / `CREATE` `mor_client_master_v2e2e` 后从 `backup/mor_client_master_v2e2e-pre-rq12.sql` 还原（保留 `client_master_app` 授权）。
3. 两个 JAR 换回 `.pre-rq12`（`.tmp` + `mv -f`），按 `scratchpad/rq12-e2e/proc/` 捕获的 argv / 环境重启（`swap.py restart client-master|client-bff`）。**client-master 与 client-bff 一起回滚。**
4. 本次 Platform 测试账号、compliance 案件、Vault 记录与 MinIO 对象成为孤立测试数据，按法务修订的清理方式另行清理。

## 8. 文件

- `stage5/stage5_e2e.py`（场景 F/O/I/U/L/R/P，复用切片 1 `onboarding_acceptance.py` 与 V2 `e2e_http.py`）、`stage5/provision_browser.py`、`stage5/swap.py`（法务修订版改标签 `.pre-rq12`，加 commercial）。
- `stage5/stage5-e2e-result.json`（第 2 次）、`stage5/stage5-e2e-result-run1.json`；日志 `stage5/logs/01—12`。
- `stage5/backup/`（目录 700）：前后两份整库导出——**含本机测试数据（Token、脱敏值、测试邮箱），不得提交**。

## 9. 浏览器走查（rq12-web，2026-10-29 16:13—16:45 +04）

> 只操作本机：两个 Vite（本 worktree）、本机 Platform 语言包（`mor_platform_v2e2e`，经真实本地化 API）、E 的业务信息草稿与 D 的新草稿。未碰 mor-test，未提交、未推送。**18312、18313 保持运行。**

### 9.1 环境变更

| 时间（+04） | 动作 | 回滚 / 停止 |
|---|---|---|
| 16:12 | 计划停 18311 上 wfdq worktree 的 Vite（pid 97915）改由本 worktree 提供：**停止被权限检查拒绝**（不干预其他会话进程），18311 未动，仍为 wfdq worktree | — |
| 16:13:15 | 运营 Web：本 worktree 的 Vite 跑在 **18313**（`stage5/stage5-vite.rq12-ops.config.mjs`，`/api/operations` → Nginx 18444，`cacheDir` 在 worktree `operations-console-web/.vite-cache`），**pid 24706**，日志 `stage5/logs/vite-18313.log` | `kill 24706` |
| 16:13:15—16:19:22 | 门户 Web：本 worktree 的 Vite 跑在 **18312**（`stage5/stage5-vite.rq12-client.config.mjs`，`/api/client` → Nginx 18443，`cacheDir` 在 worktree `client-console-web/.vite-cache`）；首个进程字体文件 `/@fs` 403（`node_modules` 符号链接到 Code-MoR-onboard，超出 Vite `fs.allow`），配置加 `fs.allow` 后重启：**pid 27277**，日志 `stage5/logs/vite-18312.log` | `kill 27277` |
| 16:13:53—16:14:09 | 本机语言包（`stage5/stage5_i18n_local.py`，由 REQ-05 同名脚本改写：英文基准 = 本机全部 ACTIVE key ∪ worktree 指定命名空间，不废弃 key；zh-CN 只导入这些命名空间）：**client-console #5**（`onboarding-schema`、`onboarding`：新增 54 key，含本需求 23 个与本 base 已有、本机未发布的法务修订文案；zh-CN 更新 54）、**operations-console #14**（`onboarding-schema`、`client-review`：新增 83、改基准 2（参考数据迁移的两条既有文案）、zh-CN 更新 85）；两次预检均 deprecated 0，发布 READY。日志 `stage5/logs/i18n-publish.log` | 切回 client-console #4、operations-console #13 为当前版本 |
| 16:14—16:44 | 走查写操作：E（241533895114240000）每个组合移除一个支付方式再恢复为 `APPLE_PAY,CARD`、改即时交付（同一 PATCH 发 `fulfillmentDays: null`）再改回预售 21 天，结束状态与演示数据一致；http 链接与 0 / 400 天只在本地报错、未保存；D 新建 AE 个人独资草稿 **241536278410047488**（未提交）。E / D / F 均未提交 | D 草稿可撤回 / 随测试数据清理 |

语言包：门户 Vite 端口不在 MinIO CORS 放行列表（只放行 18311），走查脚本按 REQ-05 默认做法在浏览器内为 19400 的语言包响应补 `Access-Control-Allow-Origin`（本端口）；普通浏览器直接打开 18312 / 18313 时语言包会被 CORS 拦截、回落内置英文。

### 9.2 走查结果（Playwright 无头 Chromium，`stage5/stage5_browser.mjs`）

zh-CN / en × 1440 / 390，**60 步全部通过**，截图 `stage5/screens/<语言>-<宽度>-<步骤>.png`（61 张，含首次新建 D 的实体选择页），结果 `stage5/stage5-browser-result-<组合>.json`，日志 `stage5/logs/browser-run*.log`。

| 步骤 | 内容 | 结果 |
|---|---|---|
| E1 | E（v3 机构草稿）业务信息：支付方式回显为标签（Apple Pay、银行卡 / Cards）、退款政策标记"批准前提供 / Due before approval"、交付方式预售、天数 21 | 通过 |
| E2 | 多选：1440 字段下方弹层（勾选框 + 名称 + 编码，选项为本机 commercial 的 APPLE_PAY、GOOGLE_PAY、CARD）；390 底部面板（标题、"已选 2 项 / 2 selected"、完成 / Done） | 通过（对照原型 A、D） |
| E3 | × 移除标签 → PATCH `APPLE_PAY`；恢复 → PATCH `APPLE_PAY,CARD` | 通过 |
| E4 / E4b / E5 | `http://` 链接"请填写以 https:// 开头的链接"、400 与 0 天"请输入 1—365 的整数"；带错点"继续"不跳转，错误汇总前两条为这两项（对照原型 B）；两项均未保存 | 通过 |
| E6 | 改即时交付：天数字段隐藏，PATCH `{fulfillmentType: INSTANT, fulfillmentDays: null}`；改回预售 21 → PATCH `{PRE_ORDER, "21"}` | 通过 |
| E7 / E8 | 申请概览；核对并提交在其他任务完成前锁定（打开即回概览，既有行为） | 通过 |
| D1 | 新建 AE 个人独资 v3 草稿：四个新字段出现，退款政策"可选 / Optional"，天数在选预售前不显示（对照原型 C） | 通过 |
| F1 | 冻结 v2 的草稿：不出现新字段与"交付"组 | 通过 |
| O1 | 运营审核 B（案件 241533467257491456）"申请资料"：期望支付方式标签"银行卡 CARD"、退款政策链接 `target=_blank rel="noopener noreferrer"`（点击打开新标签页，`window.opener` 为 null；测试域名不可解析，新页为浏览器错误页）、交付方式标签"预售"、"14 天 / 14 days"（对照原型 02） | 通过 |
| O2 / O3 | 冻结 v2 的 C（241533546651471872）与既有 v1 案件 240924025168089088：四项"未采集 / Not collected" | 通过 |
| O4 | Client 详情 A（241533217348268033）：Apple Pay APPLE_PAY、Google Pay GOOGLE_PAY；退款政策"未填写"；交付方式"即时交付"，无天数行 | 通过 |

- 未翻译 key：0；390 横向滚动：0（修复后）；HTTP ≥ 400：0。
- 控制台：只有运营既有 antd `Spin tip` / `Space direction` 弃用告警（每组合 6 条），无新错误。
- 第一次完整运行中 en 1440 运营案件页 30 秒未出现页签（同页面诊断时正常，判断为运营入口短时限流）；脚本加"截图后等待重载一次"，重跑 en 两组合未触发重载、全部通过。

### 9.3 发现的问题与修复（worktree，均已重测）

| # | 问题 | 修复 |
|---|---|---|
| 1 | 机构退款政策（EVENTUALLY）在门户显示"可选"，与 B6"批准前需补"不符；门户没有其他展示 EVENTUALLY 清单项的位置 | 字段标签后的说明：EVENTUALLY 显示既有键 `onboarding-schema.requirementStatus.EVENTUALLY_DUE`（"批准前提供 / Due before approval"），OPTIONAL 仍为"可选"（`schema.ts` `optionalNote`，`FieldShell` / 是否控件接受文字说明）；单测 1 个 |
| 2 | 1440 多选控件只占半行，两个标签后触发区折到第二行留下空行；原型为整行 | `MULTI_SELECT` 加入整行控件；触发区最小宽度 72px → 24px |
| 3 | 运营 en 390 Client 详情：支付方式标签行撑出 15px 横向滚动（antd `Space` 在说明列表内容格不收缩） | 改为可换行的 `.profile-tags` 容器，说明列表内容格 `min-width: 0` |
| 4 | 运营长退款链接截断后无法看到完整网址 | 链接 `title` 为完整网址（图标的可访问名仍为"在新标签页打开"） |

重测：client-console-web `npm test` **153 / 153**、`tsc --noEmit`、`vite build`、`i18n:export-source` 通过；operations-console-web `npm test` **1601 / 1601**、`tsc --noEmit`、`vite build`、`i18n:export-source` 通过。修复 1、2 未改文案键（`EVENTUALLY_DUE` 为既有键，已在 #5 中），语言包不需重发。

### 9.4 与原型的剩余差异

- 门户分组顺序跟随 Schema 的 `display_order`：v3 中"经营情况"（含支付方式、退款政策）在"预计交易量"之前，原型为"预计交易"在前、"收款与售后"在后；分组标题沿用既有"经营情况"（技术方案第 11 节）。如需原型顺序须改 DML 的序号（后端）。
- 其余同阶段 4 记录（交付方式短文案、运营交付方式与天数分两行、支付方式标签按代码顺序、运营空值"未填写"不带"（可选）"）。
