# W9 · payout S1 阶段 6 数据侧报告（SQL、备份、注册块 147、Vault、语言包、验收脚本）

> 2026-10-30 13:10—13:45Z，W9 恢复运行（上一个 W9 在 16:59 +04 主机重启时中断）。mor-test 只做了只读操作：`docker inspect / ps / logs / exec sha256sum / printenv`、root 只读会话 SELECT（`--init-command="SET SESSION TRANSACTION READ ONLY"`，口令从 `test-environment/.env.test` 读进变量，没有打印）、Mailpit `/api/v1/info` 与 `/api/v1/messages`。没有写 SQL，没有调用写接口，没有登录运营 / Client API，没有 `docker cp` 写入，没有停 / 起容器。本机只用了 `mor-local-mysql-1`（只读计数）和我自己起的断网演练容器（已删除，`docker ps -a` 里没有残留）。本机 e2e 栈没碰。没有提交，没有推送。

## 1. 结论

| 项 | 结论 |
|---|---|
| 导出基线 | `~/Code-MoR-backups/payout-s1-stage6/export-20261030T125723Z/` 里 `mor_platform` / `mor_vault` 两个文件都过了 SHA256SUMS 校验和 `gzip -t`，结尾都是 `Dump completed`，`.err` 为空，直接复用，没有重新导出。13:15Z 现网只读预检的块 147 结果和导出时的草跑**逐行相同**（`precheck/mor_platform-block-147-precheck-20261030T131503Z.out`），所以导出仍代表现网。 |
| 现网 JAR | `live-jars/platform-support-af13e77a.jar` 是 `9b2a86e1…`，`api-gateway-d51daee3.jar` 是 `d3dd5aa2…`，和两个容器里的 `/app/application.jar` sha256 一致。 |
| 块号 / ID | 块 147 在 7 张表 + 通知 3 张表的区间都是 0；8 个权限编码、7 个资源编码 / 路由、19 个路由编码 / 路径、4 个模板编码都没被占用；按权限推断的现网最大块号是 146。Vault 的 `9100000000000880—885`、两个数据类型编码、`payout-service` 规则、策略变更键都没被占用；Vault 里没有 PAYOUT_* 记录。`mor_payout` 库和 `payout_app` 账号都不存在。 |
| 依赖 | PLATFORM_ADMIN `…510` ACTIVE（v53）；财务菜单 `…97301` ACTIVE（它的权限是 FINANCE_ACCOUNTING_READ `…97101`）；Operations 目标 `…0001` 和 Client BFF 目标 `236085272176050176` 都 ACTIVE；`SES_PRIMARY` 存在且 ACTIVE，所以 4 条模板路由**会写入**。财务子菜单最大排序 80（对账），新的「出款」组用 85。 |
| Vault 策略版本 | mor-test 当前最大版本是 **21**（本机是 25）。`20-mor_vault-payout.sql` 本来就按 `MAX + 1` 计算版本（FOR UPDATE），没有写死，不需要单独做 mor-test 版。Vault 只从库里读策略，`VAULT_POLICY_REFRESH_INTERVAL` 在 mor-test 没设，用默认 10 s 全量刷新（`paytech/vault/README.md`）。所以**阶段 7 不用重启 vault-service**，DML 执行 10 s 后再起 payout 就行。 |
| SQL 包 | `sql/` 共 17 个文件，带 `SHA256SUMS`。其中 6 个块文件、`12` DDL、`20` Vault DML 和原稿逐字节相同，没改。新加了建库 / 账号、三份预检、三份核对、三份回滚（见第 3 节）。 |
| 离线演练 | 用**现网** Platform JAR 跑一轮，用 **W8 候选** Platform JAR `f4782de4…` 复跑一轮。两轮结果相同：45 个 SQL 步骤全部符合预期，含 9 个预期失败的保护步骤。快照字节数和 sha256 两个 JAR 一模一样（第 4 节）。 |
| 语言包 | ops 控制台基于现网 #28：新增 223 个键（nav 7、payout 216），基准变更 0，废弃 0，zh-CN 导入 223，新建一个命名空间 `payout`（名称"出款"，排序 280）。只读干跑**可执行**。Client 门户本期没有 Web 页面（po-int / po-rel 都没改 `client-console-web`），所以**不需要 client-console 语言包**。 |
| 验收脚本 | `acceptance/payout_acceptance.py`（子命令 plan / accounts / prepare / api / notify / check / cleanup，写操作要加 `--yes`）和 `acceptance/payout_browser.mortest.mjs`。只读子命令已在 mor-test 干跑过：`plan` 8 PASS 1 FAIL，这个 FAIL 是预期的，原因是 Client 凭据文件还没有（D-4）；`cleanup` 识别出 `mor_payout` 还没建表、不生成草稿；写子命令不加 `--yes` 都被拒。语法检查：`py_compile` 和 `node --check` 都通过。 |
| 两项阻塞，需裁决 | **D-3**：mor-test 的邮件走真实 Amazon SES（沙箱），Mailpit 收不到，AC-09 的判定方式要改。**D-4**：mor-test 没有现成的 Client 所有者凭据，Client 侧 API 验收需要主会话提供。见第 7 节。 |

## 2. 只读预检读数（2026-10-30 13:15—13:22Z）

| 读数 | 值 | 证据 |
|---|---|---|
| 块 147 七表 + 通知三表占用 | 全 0 | `precheck/mor_platform-block-147-precheck-20261030T131503Z.out` |
| 编码 / 路径占用 | permission 0、resource 0、route 0、template 0 | 同上 |
| 现网路由 | ACTIVE 652、DISABLED 6、max route `900000000000146402`；最大块 146 | 同上 |
| Client 系统角色回填规模 | 39 个已开通 CLIENT 工作区 × 5 个系统角色。B2 预计 390 行（演练 verify `client_grant ACTIVE 390`，`missing_client_grants_0 = 0`） | 同上；`evidence/rehearsal-*/c31-verify-active.out` |
| 高位授权主键 | 当前最大 `9100000000000000223`（B2 从这里递增） | 同上 |
| Vault | 编码 / ID / 规则 / 变更键全 0；版本 21；数据类型 12、规则 33；PAYOUT 记录 0 | `precheck/mor_vault-payout-precheck-20261030T132234Z.out` |
| MySQL | 没有 `mor_payout` / `payout_app`；8.4.11、utf8mb4 / utf8mb4_0900_ai_ci；`tax_app` / `reconciliation_app` 都只有 `SELECT, INSERT, UPDATE, DELETE ON mor_<服务>.*` | `precheck/mysql-payout-database-precheck-20261030T132234Z.out` |
| 邮件 | `SES_PRIMARY` = AMAZON_SES、`ap-southeast-1`、`noreply@payorigo.com`、有密钥、有事件队列；Platform `NOTIFICATION_ENABLED / NOTIFICATION_SES_ENABLED = true`；`t_notification_request / delivery` 都是 0 行；Mailpit v1.29.7 用内存库，0 封；Platform 的 `SPRING_APPLICATION_JSON` 只有 `webhook.http.callers.acquiring-service[0..7]`，**没有** `notification.http.callers.*`（W8 的 Platform 候选已经合并进去，没覆盖 webhook 段） | 只读 SQL / printenv（只看键名） / Mailpit API |
| data-query | S1 不需要数据源，也不需要授权：没有查询模板需求；`data_query_demo_reader` 不加 `mor_payout`。已写进 `10` 的文件头 | — |

## 3. SQL 包（`release/sql/`，按编号顺序执行；`SHA256SUMS` 覆盖 17 个文件）

| 文件 | 库 / 身份 | 性质 | 说明 |
|---|---|---|---|
| `01-mysql-payout-database-precheck.sql` | root 只读，不指定库 | 新增 | 库 / 账号不存在、字符集、同类账号授权粒度（走 `information_schema.schema_privileges`，账号不存在时不报错） |
| `02-mor_vault-payout-precheck.sql` | root 只读 | 新增 | ID / 编码 / 规则 / 变更键占用、当前版本、PAYOUT 记录数 |
| `03-mor_platform-block-147-precheck.sql` | root 只读 | 原稿 | 与 `Code-MoR-po-int/release-draft/` 相同 |
| `10-mysql-payout-database-account.sql` | root | 新增 | 三句照 `initialize.py --create-service-databases`：`CREATE DATABASE IF NOT EXISTS`、`CREATE USER IF NOT EXISTS`（用 PREPARE 注入 `@payout_app_password`）、`GRANT SELECT, INSERT, UPDATE, DELETE`。**密码不在文件里**，从 `.env.test` 的 `MOR_PAYOUT_DB_PASSWORD` 经管道注入（W8 的 compose 用同一个键）。变量缺失或格式不符（与 initialize.py 同一正则）时报 ERROR 1242，什么都不做。另一种做法是用 W8 候选树的 `initialize.py --create-service-databases`（它已登记 payout，`applies_sql=False`），**两种只用一种**。Platform 的通知白名单只能放在 `SPRING_APPLICATION_JSON` 里，环境变量的宽松绑定会把 `payout-service` 变成 `payoutservice`。 |
| `11-mysql-payout-database-verify.sql` | root 只读 | 新增 | 库、账号、恰好两行授权、表数 |
| `12-mor_payout-ddl.sql` | root，库 `mor_payout` | 原稿（= `db/payout/ddl.sql` = payout 测试 DDL） | 3 张表。`ddl-20261030-notes.sql` 是本机已建库用的增量补列，`12` 已经包含这两列（31 列），**mor-test 首建不要执行它** |
| `13-mor_payout-verify.sql` | 只读 | 新增 | 3 表；列 31 / 13 / 15；索引 5 / 1 / 3；生成列 `active_key`；无 FK / CHECK；0 行 |
| `20-mor_vault-payout.sql` | root，`mor_vault` | 原稿 | 版本按 MAX + 1 算；mor-test 会变成 22 |
| `21-mor_vault-payout-verify.sql` | 只读 | 新增 | 2 个数据类型、3 条规则、变更行是最新版本 |
| `30` / `31` / `32` / `33` 块 147 | root，`mor_platform` | 原稿 | 休眠写入 / 核对 / 激活 / 停用；**按 mor-test 现网核对后不需要改**（Client 目标按服务码取，SES_PRIMARY 存在） |
| `90-mor_vault-payout-rollback.sql` | root | 新增 | 情形 A（还没有 PAYOUT 记录，且本变更行仍是最新版本）：删除 6 行。否则报 ERROR 1242 拒绝，转情形 B：只把 3 条规则改成 DISABLED |
| `91-mor_payout-ddl-rollback.sql` | root | 新增 | 三张表只存在一部分时拒绝，有任何一行时也拒绝；否则 DROP 三表；可以重跑 |
| `92-mysql-payout-database-rollback.sql` | root | 新增 | 库里还有表时拒绝；否则删账号和库；可以重跑。一般用不到（technique 第 9 节要求保留库） |
| `93-mor_platform-block-147-rollback.sql` | root | 原稿 | 路由 ACTIVE 时拒绝；已有通知请求引用模板时拒绝 |

## 4. 离线演练（断网 `mysql:8.4.11` `sha256:b3b90af2…`，`--network none`）

脚本是 `harness/rehearse.sh`，改写自 REQ-04。它按 runbook 顺序跑完 A 建库 / DDL、B Vault、C 块 147 三段，块 147 的每个状态都用指定的 Platform JAR 编译、序列化，再用现网 Gateway JAR `d3dd5aa2` 的生产解析解码。夹具 `fixture-payout-row.sql`、`fixture-vault-record.sql` 只在演练容器里用，用来验证"有数据时回滚会被拒"。

| 轮次 | Platform JAR | 证据 |
|---|---|---|
| 现网 | `af13e77a` / `9b2a86e1` | `evidence/rehearsal-live-20261030T132619Z/`（45 个 `.out`、4 个 `.diff`、`rehearse.log`、`snapshot-sizes.txt`） |
| W8 候选 | `candidate/platform-support.jar` `f4782de4` | `evidence/rehearsal-cand-20261030T132747Z/` |

| 状态 | 加载路由 / 有效路由 | 块 147 有效路由 | 快照字节（sha256 前 16 位） | Gateway 解码 |
|---|---|---|---|---|
| 基线 / 休眠 / 停用 / 回滚后 | 658 → 677 → 677 → 658 / **652** | 0 | 1 049 930（`38dd0a46c8e31b65`） | OK，routes 652 |
| 激活 / 重复激活 | 677 / **671（+19）** | 19 | 1 080 333（`e3e64cebd08a3bbb`），+30 403 | OK，routes 671 |

- 4 MiB 上限 4 194 304：激活后还剩 **3 113 971 字节**（用了 25.8%）；路由数 671，上限 4096。
- 保护都生效：没设密码 / 密码太短时建账号失败；DDL 重跑报 1050，结构不变；`payout_app` 不能建表，也不能读 `mor_platform`；有一行数据时 DDL 回滚被拒；库里还有表时删库被拒；Vault DML 重跑报重复主键；有 PAYOUT 记录时 Vault 回滚被拒；块 147 还没写入就激活被拒；块 147 重复写入报 1062；路由 ACTIVE 时块回滚被拒。
- 回滚后比对：`mor_vault` 和导出 **0 行差异**；`mor_payout` 首建、失败重跑后、回滚再建三次结构 **0 差异**；`mor_platform` 只差 2 行，都在 PLATFORM_ADMIN 角色 `…510` 这一行（version 53 → 57、update_time）。原因：块 147 每次激活 / 停用都把这个角色 version + 1 来刷新会话权限缓存（同块 143），回滚不撤销这个计数器。这是预期差异，其余 0 行。
- 每步 SQL 耗时 0.07—0.27 s。

## 5. 语言包（`payout_bundles.py`，改写自 tax_bundles.py；干跑结果 `precheck/i18n-plan-dryrun-operations-console.out`，导入文件 `i18n-bundles/`）

- 源码根：W8 候选 worktree `Code-MoR-po-rel`。`payout.json`（en / zh-CN）和 po-int 逐字节相同；`nav.json` 相对各自基线的增量也相同。
- 现网 #28；线上 ACTIVE 11 335 个键全部在仓库里；新增 223 个（nav 7、payout 216）；基准变更 0、废弃 0、恢复 0；zh-CN 导入 223，缺 0；仓库 zh-CN 和线上不同却不导入的 0；新建命名空间恰好 `payout`（"出款"，280）。
- 发布号用阶段 7 当时的下一个空闲号（现在看是 #29）。回滚：旧 Web 不读这些键，一般不用回滚；真要回滚，用 `9eab368c` 的干净 worktree 重发。
- 门禁写在脚本里：新增 / 变更 / 废弃 / 导入只允许出现在 `payout`、`nav`。如果 W8 之后又改了 ops-web 文案，阶段 7 先重跑干跑。

## 6. 验收脚本与数据（`release/acceptance/`）

**API 脚本 `payout_acceptance.py`**：阶段 5 的场景函数原样复用，合成前缀改成 S1MT。改动的部分：mor-test 入口；root 只读 `q()`；Maker / Checker 改为两个只带 PAYOUT_* 权限的合成运营账号；余额预期按 `mor_account` 现值计算；AC-09 按 SES 判定。

| 子命令 | 性质 | 覆盖 |
|---|---|---|
| `plan` | 只读 | 四个 Client 状态与 account 现值、块 147 / Vault / mor_payout 状态、凭据文件 |
| `accounts --yes` | 写 IAM | maker / checker（READ、SUBMIT、REVIEW、REVEAL、MANAGE + FINANCE_ACCOUNTING_READ 显示「财务」菜单）、ro（READ + 菜单）、noperm（DATA_QUERY_READ），首登改密；凭据存 `~/Code-MoR-backups/payout-s1-stage7/accounts.json`（0600）；顺带核对两个 Client 所有者能登录 |
| `prepare --yes [--billing-fact]` | 写 Platform 团队 | Client A 邀请 viewer（TENANT_VIEWER）和 manager（TENANT_ADMIN，邮箱 `success+s1mt-<时间>@simulator.amazonses.com`），用既有的邀请 / 注册链接铸造夹具；`--billing-fact` 见 D-5 |
| `api --yes` | 写 payout | AC-01、AC-02、AC-03、AC-04、AC-05、AC-06、AC-07、AC-08、AC-10、R10 权限（viewer 403、ro 只读、noperm 403、未登录 401），生命周期；另建两条浏览器目标账户；他方回归（REQ-04 档位列表与配额、联合查询键类型、多语言发布历史，只读） |
| `notify` | 只读 | AC-09（第 7 节 D-3）：补偿任务清空；四类模板都受理；投递数 = A 持 MANAGE 的有效成员数；终态分布；模拟器地址投递成功；参数里没有原值；地址是邮箱 Token |
| `check` | 只读 | 事件序号连续、没有 ERROR / FAILED 补偿事件、三表没有原值、Vault 两类记录在、5 个服务日志自本次开始没有原值 |
| `cleanup` | 只读 | 生成 `cleanup-<UTC>.sql` 草稿，不执行 |

**浏览器脚本 `payout_browser.mortest.mjs`**：运营 O1—O5 和无权限分支，zh-CN / en × 1440 / 390，组间暂停 20 s（Operations 每人每分钟 30 次 GET、并发 2）。用真实语言包，去掉了本机的模拟。账号读 `accounts.json`，目标账户读 `run.json`。AC-V1、AC-V2（Client 页面）本期没有 Web，不适用。他方页面回归在 API 里只读覆盖；如果还要页面回归，可以复用 REQ-04 / tax 的浏览器脚本，本脚本没包含。

**验收数据（只读选定）**：

| 标签 | Client | 现值 | 用途 |
|---|---|---|---|
| A | `237575207592763393`（DEMO-CL-001） | AE001 × USD：AVAILABLE 97.00、PENDING 137.69 | 主场景。因为可用余额大于 0，所以**不会触发** NO_AVAILABLE_BALANCE；冷却中账户会触发 ACCOUNT_COOLING |
| B | `240686705999454209`（REQ-03 验收 L） | 没有 account 账户 | 共用账号、待复核；positions 为空 |
| Z | `237092466744991745` | 只有 PENDING 51.10 | 运营只读视图触发 NO_AVAILABLE_BALANCE + NO_USABLE_ACCOUNT |
| C | `241049681294106625`（REQ-03 验收 H） | 没有 account 账户 | 运营只读视图：positions = [] |

RECOVERY_OUTSTANDING：mor-test 上没有 Client 追偿应收余额，正式入口也造不出来（与阶段 5 相同），由 payout auto 测试覆盖。阶段 7 要遵守卡片规则：验收用的业务时间不能早于任何配置的生效时间。本需求没有按时间解析的配置，冷却期取决定时间。

**合成数据清理**（`release/cleanup/`）：
- mor-test：`mor-test-acceptance-cleanup-plan.sql` 是模板，`cleanup` 子命令按实际 ID 生成草稿。`mor_payout` 删本次验收的账户、事件、补偿事件。Vault 记录保留（外键和审计，先例 data-query）。通知审计保留（同时它也是块 147 回滚的保护依据）。IAM 账号和团队成员只停用，不删。计费事实不删。
- 本机：`local-stage5-cleanup-draft.sql`（没执行）。修正了阶段 5 的原草稿：Vault 记录被 95 条访问事件和 48 条保护请求以外键引用，不能直接删，改成保留。`mor_payout_v2e2e` 现有 9 个账户、46 条事件、8 条 FAILED 补偿事件。另外发现一个残留库 `reh_payout_d`（87 张 Platform 表，建于 07:49），见 D-6。

## 7. 待裁决

| # | 问题 | 建议 |
|---|---|---|
| D-3 | **AC-09 在 mor-test 没法用 Mailpit 验**：mor-test 的 `SES_PRIMARY` 是 2026-10-16 起的真实 AWS SES，在沙箱里，只能发给已验证地址，每天 200 封；Platform 不经过 Mailpit。现有 Client 成员邮箱都是 `@example.test`，SES 会拒收，投递记 FAILED。 | **A（建议）**：在 Client A 加一个邮箱是 SES 邮箱模拟器 `success+…@simulator.amazonses.com` 的 TENANT_ADMIN 成员（沙箱允许，不占配额）。判定依据改成 Platform 受理记录 + 投递 SENT / DELIVERED；`@example.test` 那份 FAILED 正好用来证明"失败不影响状态"。脚本已按 A 写好。B：再加一个用户本人的已验证地址，用户在收件箱里亲眼看到邮件，需要用户提供地址并同意。 |
| D-4 | mor-test 没有可用的 Client 所有者凭据（`private/access.md` 只有运营管理员），Client 侧 API 验收要用 A、B 两个工作区的所有者登录。 | 主会话或用户提供 `~/Code-MoR-backups/payout-s1-stage7/clients.json`（0600，`{"A":{"clientId","email","password"},"B":{…}}`），或者换成你们有凭据的两个 ACTIVE Client（改脚本里的 `CLIENT_A_ID / CLIENT_B_ID`，再重跑 `plan`）。没有凭据时只能跑运营侧，AC-01、AC-04、AC-05、AC-10 的 Client 部分做不了。另一条路是走 onboarding 验收的 SA 场景新建两个 ACTIVE Client，会写 compliance / 风控，约 10 分钟以上，不建议。 |
| D-5 | 要不要在 mor-test 给 B 登记一条 MANUAL_ADJUSTMENT 计费事实，让 B 有余额（会产生不可删除的会计分录）。 | 不登记（脚本缺省就是不登记）。四种阻断原因里有三种已经能用 A / Z 现值触发，第四种（RECOVERY_OUTSTANDING）本来就构造不了。 |
| D-6 | 本机 `mor-local-mysql-1` 里的残留库 `reh_payout_d` 是谁的。 | 确认是阶段 4 的块 147 本机演练拷贝后 DROP。W9 没动它。 |

## 8. 风险

- **R-1 发布顺序会让通知终态失败**：technique 8.7 规定 Platform 业务拒绝就算终态失败、不自动重试。所以如果 payout 在 Platform 候选（带 `tenantRecipients` 和 `notification.http.callers.payout-service`）**之前**处理核验决定，那条通知就永久失败，只能受控重开。要求：Platform 候选先上，并确认白名单生效，再起 payout；验收的第一个核验决定放在激活之后。
- **R-2 `initialize.py --apply-service-sql` 会建错表**：po-int 的 `paytech/payout/docs/database/sql/payout-schema.sql` 有 9 张表，`t_compensation_event` 还是旧 Finance 的形状，和代码 / 测试 DDL / `12` 不一致。W8 已在 po-rel 的 `initialize.py` 里给 payout 设了 `applies_sql=False`，并写了注释说明原因；DDL 一律走 `12`。刷新 `payout-schema.sql` 之前，不能把它改回 True。这个 Schema 文件和代码对不上，属于文档缺陷，要另外修。
- **R-3 现网可能漂移**：导出是 12:57Z 的。之后如果别的会话写了块 148 / 150 或 Vault 策略，阶段 7 的 `02` / `03` 预检会看到差异。Vault 回滚要求本变更是最新版本，这时会转情形 B（只停用规则）。阶段 7 要重新导出、重新预检。
- **R-4 SES 配额**：一次验收大约 4 类通知 × 2 个收件人 + 2 封邀请 / 注册邮件，远低于每天 200 封；模拟器地址不占配额。
- **R-5 Client 系统角色回填**：每个已开通工作区 10 行，现网 390 行。阶段 7 之前如果又有新工作区开通，`32` 的补齐语句会处理（NOT EXISTS）。

## 9. 阶段 7 数据侧动作清单（由主会话执行；需用户授权阶段 7）

公共：`cd /Users/kenny/Desktop/Workspace/Code-MoR; REQ=requirements/20261030/payout-收款账户与余额解释; TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-); sqlr() { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }; sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }; gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }`。任何一步失败就停在该步，按"回滚"列执行。

| 步 | 命令 | 预期 / 失败即停 | 回滚 |
|---|---|---|---|
| D1 备份 | `B=~/Code-MoR-backups/payout-s1-stage7/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_platform > "$B/mor_platform.sql"`，`mor_vault` 同样导出；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 非空，结尾是 `Dump completed` | — |
| D2 预检 | `sqlro < $REQ/release/sql/01-…`；`sqlro mor_vault < …/02-…`；`sqlro mor_platform < …/03-…`；`python3 -I $REQ/release/payout_bundles.py` | 与第 2 节相同：库 / 账号 0 行；Vault 全 0、版本 21（变了要记下）；块 147 全 0、路由 ACTIVE 652、max_block 146；语言包仍可执行、基线 #28 | 不一致就停 |
| D3 建库与账号 | （`.env.test` 没有 `MOR_PAYOUT_DB_PASSWORD` 时先跑 `prepare.py` 追加，需要 W8 候选树的 prepare.py；或者手工追加 `secrets.token_hex(24)`）`{ printf "SET @payout_app_password='%s';\n" "$(grep '^MOR_PAYOUT_DB_PASSWORD=' test-environment/.env.test | cut -d= -f2-)"; cat $REQ/release/sql/10-mysql-payout-database-account.sql; } \| sqlr`；`sqlro < …/11-…` | 秒级；授权恰好两行；0 张表 | `92-…`（库里没有表时） |
| D4 DDL | `sqlr mor_payout < …/12-mor_payout-ddl.sql`；`sqlro mor_payout < …/13-…` | 3 表、31 / 13 / 15 列、索引 5 / 1 / 3、0 行、无 FK / CHECK | `91-…` → `92-…` |
| D5 Vault | `sqlr mor_vault < …/20-mor_vault-payout.sql`；`sqlro mor_vault < …/21-…`；等 ≥ 10 s | 数据类型 2、规则 3、变更版本 = 22（或 D2 读数 + 1）且是最新 | `90-…`（还没有 PAYOUT 记录时），否则停用规则 |
| D6 块 147 休眠 | `T6=$(date -u +%FT%TZ); sqlr mor_platform < …/30-…`；`sqlro mor_platform < …/31-…`；10 s 后 `gwlog $T6` | permission 8、admin grant 5、client grant 390（REVOKED）、resource 7、route 19、header 71、query 19、guard 114、template 4、content 8、template route 4；快照 routeCount 仍是 **652** | 刷新失败马上执行 `93-…`（仍休眠，可以直接删） |
| — | （W8 / 主会话：Platform 候选 → account → payout → BFF → ops-web；顺序见 W8-report 和 R-1） | — | — |
| D7 语言包 | `python3 -I $REQ/release/payout_bundles.py --apply` | 新建命名空间 payout，导入 223 键，发布号是下一个（约 #29） | 一般不回滚；必要时用 9eab368c 源码重发 |
| D8 激活 | `T8=…; sqlr mor_platform < …/32-…`；`sqlro … 31-…`；5 s 后 `gwlog $T8` | 授权 / 资源 / 路由 ACTIVE；routeCount **671（+19）**，响应体约 1 080 333 B | `33-…`（回到 652），再查原因 |
| D9 验收 | `python3 -I $REQ/release/acceptance/payout_acceptance.py plan` → `accounts --yes` → `prepare --yes` → `api --yes` → `notify` → `check`；`node $REQ/release/acceptance/payout_browser.mortest.mjs` | 0 FAIL（plan 的 D-4 项需先补齐）；AC-09 按 D-3 判定 | 失败按服务回滚；数据按 `cleanup` 处理 |
| D10 清理 | `python3 -I …/payout_acceptance.py cleanup` → 审阅 `acceptance/cleanup-<UTC>.sql` → `sqlr < …`；IAM / 团队成员停用；删除 `~/Code-MoR-backups/payout-s1-stage7/{accounts,run}.json` | 本次账户为 0 | — |
| 整体回滚（逆序） | `33` 停用 → 回退 Web / BFF / payout → `93`（有通知请求时会拒绝，那就保留块在休眠状态）→ `90`（有 PAYOUT 记录时停用规则）→ `91` / `92`（有数据时保留库） | 演练已验证保护行为 | — |

## 10. 文件清单

- `release/sql/`（17 个文件 + `SHA256SUMS`）、`release/precheck/`（4 份只读读数）
- `release/harness/`：`rehearse.sh`、`BlockSnapshotCheck.java`、`GatewaySnapshotDecodeCheck.java`（复制自 REQ-04，逻辑未改）、两份夹具
- `release/evidence/rehearsal-live-20261030T132619Z/`、`release/evidence/rehearsal-cand-20261030T132747Z/`；完整工作目录（含 6 份快照 JSON）在 `~/Code-MoR-backups/payout-s1-stage6/rehearsal-{live,cand}-*`
- `release/payout_bundles.py`、`release/i18n-bundles/`（plan / en / zh-CN delta）
- `release/acceptance/payout_acceptance.py`、`payout_browser.mortest.mjs`、`dryrun-plan-*.json`、`dryrun-cleanup-*.json`
- `release/cleanup/local-stage5-cleanup-draft.sql`、`mor-test-acceptance-cleanup-plan.sql`
- `~/Code-MoR-backups/payout-s1-stage6/i18n/` 是语言包运行记录目录（干跑写入，不含口令）
