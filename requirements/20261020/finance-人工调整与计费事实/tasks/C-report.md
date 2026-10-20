# 卡 C 交付报告：Platform 能力清单、注册 SQL、Compose 与文档（2026-10-20）

结论：能力清单 +3 个组件、LEDGER 加 1 个动作；Platform 初始化 DML 追加块 100—102（共 328 行，全部休眠）；需求目录写了阅读副本和三份激活 / 停用 SQL；Compose 改了 ledger XXL 执行器和 BFF billing 地址两处；数据库文档、ledger / billing README、requirements-site 的 FIN-10 / FIN-22 已改，另新增 FIN-26。能力清单经生产加载类实际加载通过，资源与清单交叉核对 0 处不符；requirements-site 127/137，失败的 10 例与改动前逐条相同。

未执行任何 SQL，未提交，未动容器。只运行过 `docker network inspect mor-test-backend`（只读）。

## 1. 已读与开工说明

- 已读：`tasks/00-common.md`、`tasks/C-platform-docs.md`、`../technique.md`（D2、D3、第 3—7 节）、三个需求的 `requirement.md`；先例为初始化 DML 块 91 / 97 / 98 / 99 / 95，以及 `requirements/20261019/ledger-会计科目分级维护/db/platform-support/*`、`requirements/20261018/.../activation-local.sql` 与 `release/test-deactivate.sql`、`20261019/.../tasks/D-report.md`。
- 合同依据：worktree 里 A / B 当时的 `ManualAdjustmentApi`、`PeriodCloseApi`、`TrialBalanceApi`、`AccountingRuleBundleApi`、`BillingFactCommandApi`、`BillingFactQueryApi`（含 B 已加的 `/search`、`/void`，以及 A / B 已删除的 `StartPeriodCloseRequestBody`、`SubmitRuleVersionRequestBody`、`ReviewRuleVersionRequestBody`、`ApproveBillingFactRequestBody`）。
- WRITE_API_EXAMPLE 第 1 节 / 第 11 节自查：不适用。本卡不实现接口，没有 Processor、Context、本地原子写或事务外步骤，也不改 Java / TS 业务代码。

## 2. 改动文件

| 类型 | 路径（worktree `Code-MoR-fin3`，另注明的除外） | 内容 |
|---|---|---|
| 修改 | `paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` | LEDGER 加动作 `OPERATIONS_FINANCE_LEDGER_MANAGE`「发起试算」；新增 3 个组件（共 31 项），见第 3 节 |
| 修改（只追加） | `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾追加块 100、101、102（+430 行，`git diff` 中没有删除行） |
| 新增（主工作区） | `requirements/20261020/finance-人工调整与计费事实/db/platform-support/registration-dml.sql` | 三块的阅读副本，正文与初始化 DML 追加部分逐字一致（已 `diff` 确认） |
| 新增（主工作区） | 同目录 `activation-local.sql`、`test-activate.sql`、`test-deactivate.sql` | 见第 5 节 |
| 修改 | `test-environment/compose.applications.yaml` | 只改 ledger-service 和 operations-console-bff 两段，见第 6 节 |
| 修改 | `agent-docs/database/FINANCE_DATABASE.md` | 4.2 节取代说明后加计费事实 `VOIDED`；`accounting_rule_bundle_ver` 行加 `VOIDED` 与定时生效 |
| 修改 | `paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md` | 2026-10-19 计费事实说明加 `VOIDED` 一句；4.13 节末尾加"放弃草稿与到时生效"说明 |
| 修改 | `paytech/ledger/README.md` | 新增"Operations 控制台接入"和"到时生效"两条；最新变更脚本改为 `2026-10-20-finance-console-statuses.sql`；「当前不包含」改写（XXL-Job 只承载到时生效任务；Operations 只经 BFF 和 Gateway 访问） |
| 修改 | `paytech/billing/README.md` | 模块表补"作废草稿 / 管理列表 / `VoidBillingFactProcessor`"；操作人一段后补 BFF 转发和块 100 权限。接口表的改动是卡 B 做的，本卡没有动 |
| 修改 | `requirements-site/content/requirements.ts` | FIN-10、FIN-22 补控制台入口与身份来源；新增 FIN-26「维护记账规则包版本」 |

## 3. 能力清单（给卡 E 的 `pageCapabilities.ts`）

| componentKey | 页面名 | frontendRoute | actions（resourceCode / 名称） |
|---|---|---|---|
| `OPERATIONS_FINANCE_LEDGER`（已有） | 账本查询 | `/finance/ledger` | `OPERATIONS_FINANCE_LEDGER_MANAGE` / 发起试算 |
| `OPERATIONS_FINANCE_ADJUSTMENTS` | 调整 | `/finance/adjustments` | `OPERATIONS_FINANCE_ADJUSTMENTS_MANAGE` / 制单；`OPERATIONS_FINANCE_ADJUSTMENTS_APPROVE` / 复核 |
| `OPERATIONS_FINANCE_PERIOD_CLOSE` | 期间关账 | `/finance/period-close` | `OPERATIONS_FINANCE_PERIOD_CLOSE_MANAGE` / 发起；`OPERATIONS_FINANCE_PERIOD_CLOSE_APPROVE` / 复核 |
| `OPERATIONS_FINANCE_ACCOUNTING_RULES` | 记账规则 | `/finance/accounting-rules` | `OPERATIONS_FINANCE_ACCOUNTING_RULES_MANAGE` / 制单；`OPERATIONS_FINANCE_ACCOUNTING_RULES_APPROVE` / 复核 |

technique D3 只写了调整页按钮的中文名（制单 / 复核）。期间关账用「发起 / 复核」、记账规则用「制单 / 复核」是本卡定的，清单与 SQL 中的中文名一致。

## 4. 注册 DML 块 100—102

格式照块 91 / 97 / 99：Target `900000000000000001`，`OPERATIONS_JWT` / `HUMAN_PERMISSION`，Cookie `DROP / NONE`，`idempotency_mode NONE`，超时 5000 / 10000 ms；GET 路由带 2 个 Header，POST 路由带 6 个（`authorization` CORS、`origin` 转发、`content-type` 与 `idempotency-key` 两用途各一）；每条路由 6 条 Guard（SHARED_ROUTE 300 / 50，CLIENT_IP 30 / 3，AUTHENTICATED_PRINCIPAL 的速率 GET 30、POST 10，并发 2，与块 91 一致）。权限 ACTIVE，角色授权 REVOKED，菜单、按钮、路由 DISABLED。外部路径为 `/api/operations` + 下表 BFF 路径，下游路径就是 BFF 路径。

ID 规则：`900000000000` + 块号（3 位）+ 表位 + 序号（2 位），表位为 1 权限、2 角色授权、3 资源、4 路由、5 Header、6 查询参数、7 Guard。

| 块 | 权限 | 角色授权 | 资源 | 路由 | Header | 查询参数 | Guard | 合计 |
|---|---|---|---|---|---|---|---|---|
| 100 | 1 | 1 | 3 | 10 | 44 | 15 | 60 | 134 |
| 101 | 0 | 0 | 4 | 8 | 36 | 3 | 48 | 99 |
| 102 | 0 | 0 | 3 | 8 | 36 | 0 | 48 | 95 |

- 权限 `900000000000100101` `FINANCE_ACCOUNTING_APPROVE`（FINANCE_ACCOUNTING / APPROVE / HIGH）；授权 `900000000000100201` 给 PLATFORM_ADMIN `900000000000000510`，初始 REVOKED。
- 资源：100301 调整 MENU（父 97301，sort 50，READ）、100302 制单（MANAGE）、100303 复核（APPROVE）；101301 期间关账 MENU（sort 60）、101302 发起（MANAGE）、101303 复核（APPROVE）、101304 `OPERATIONS_FINANCE_LEDGER_MANAGE` 发起试算（父 97303，sort 10，MANAGE）；102301 记账规则 MENU（sort 70）、102302 制单（MANAGE）、102303 复核（APPROVE）。
- 块 101、102 依赖块 100 的 APPROVE 权限，三块须按顺序执行。

### 路由清单（给卡 D 的 BFF 映射；权限 R = `FINANCE_ACCOUNTING_READ` 97101，M = `FINANCE_ACCOUNTING_MANAGE` 97102，A = `FINANCE_ACCOUNTING_APPROVE` 100101）

| route_id | Method | BFF 路径 | 权限 | Body 上限 | 查询参数 | 下游 |
|---|---|---|---|---|---|---|
| 100401 | GET | `/finance/manual-adjustments/v1` | R | 0 | ledgerBookId, accountingPeriodId, status, makerId, page, size | ledger L1 `GET /manual-adjustments/v1` |
| 100402 | POST | `/finance/manual-adjustments/v1` | M | 65536 | | ledger 提交 |
| 100403 | GET | `/finance/manual-adjustments/v1/{manualAdjustmentId}` | R | 0 | | ledger 详情 |
| 100404 | POST | `/finance/manual-adjustments/v1/{manualAdjustmentId}/approve` | A | 8192 | | ledger 审批 |
| 100405 | POST | `/finance/manual-adjustments/v1/{manualAdjustmentId}/reject` | A | 8192 | | ledger 驳回 |
| 100406 | GET | `/finance/billing-facts/v1` | R | 0 | entityNo, currency, clientId, status, factType, createdFrom, createdTo, page, size | billing B1 `GET /billing/v1/facts/search` |
| 100407 | POST | `/finance/billing-facts/v1` | M | 65536 | | billing 登记（BFF 固定 `sourceSystem=OPERATIONS`） |
| 100408 | GET | `/finance/billing-facts/v1/{factId}` | R | 0 | | billing 详情 |
| 100409 | POST | `/finance/billing-facts/v1/{factId}/approve` | A | 0 | | billing 批准 |
| 100410 | POST | `/finance/billing-facts/v1/{factId}/void` | M | 0 | | billing B2 作废 |
| 101401 | GET | `/finance/period-close/v1/board` | R | 0 | fiscalCalendarId | BFF 组合 F1 |
| 101402 | GET | `/finance/accounting-periods/v1/{accountingPeriodId}/closes` | R | 0 | page, size | ledger 关账列表 |
| 101403 | GET | `/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}` | R | 0 | | ledger 关账详情 |
| 101404 | POST | `/finance/accounting-periods/v1/{accountingPeriodId}/closes` | M | 0 | | ledger 发起关账 |
| 101405 | POST | `/finance/accounting-periods/v1/{accountingPeriodId}/reopens` | M | 8192 | | ledger 发起重开 |
| 101406 | POST | `/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/approve` | A | 8192 | | ledger 关账 / 重开审批 |
| 101407 | POST | `/finance/accounting-periods/v1/{accountingPeriodId}/closes/{closeNo}/reject` | A | 8192 | | ledger 关账 / 重开驳回 |
| 101408 | POST | `/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances` | M | 0 | | ledger `runTrialBalance` |
| 102401 | GET | `/finance/accounting-rule-bundles/v1` | R | 0 | | ledger L2 |
| 102402 | GET | `/finance/accounting-rule-bundles/v1/{bundleCode}` | R | 0 | | ledger 规则包详情（含版本摘要） |
| 102403 | GET | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}` | R | 0 | | ledger 版本详情 |
| 102404 | POST | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions` | M | 65536 | | ledger 新建版本 |
| 102405 | POST | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/submit` | M | 0 | | ledger 提交 |
| 102406 | POST | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/approve` | A | 0 | | ledger 审批 |
| 102407 | POST | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/reject` | A | 0 | | ledger 驳回 |
| 102408 | POST | `/finance/accounting-rule-bundles/v1/{bundleCode}/versions/{bundleVer}/void` | M | 0 | | ledger L3 作废 |

页面还会用到的既有路由不重复登记：账簿列表 97408、账簿账户 97410（`accountNo`）、日历 97403、日历期间 97405、科目 97414 / 97415、试算列表 / 详情 91407 / 91408。

### 冲突核对

- ID：所有 worktree 的初始化 DML 中都没有 `90000000000010[0-2]xxxx`；channel-connector 用块 92、commercial I2 用块 93，commercial 方案也写明"块 100–102 已由财务会话预定"。
- 唯一键：新 `route_code`、`resource_code`、(`http_method`, `external_path_template`) 与现有 91—99 块全部不重复（脚本逐条 grep 确认）。新 POST 试算路径与已有 GET 91407 路径相同、方法不同，`uk_gateway_route_path` 允许。
- 结构：脚本校验每行列数与列名数一致，主键无重复（328 行），三个 `START TRANSACTION` 各对应一个 `COMMIT`。

## 5. 激活 / 停用 SQL（需求目录 `db/platform-support/`）

三份脚本只动本需求的 ID，均为一个事务、每步 `SELECT ROW_COUNT()`：

- 路由 100401–100410、101401–101408、102401–102408 置 ACTIVE（26 条）。
- 资源 100301–100303、101301–101304、102301–102303 置 ACTIVE（10 个，含账本查询页按钮 101304）。
- 授权 100201 由 REVOKED 改为 ACTIVE（1 条）。
- 角色 900000000000000510 的 version 加 1（1 行）。

预期行数均为 26 / 10 / 1 / 1。`activation-local.sql` 目标库是本机 `mor_platform_v2e2e`，`test-activate.sql` 是 mor-test `mor_platform`；`test-deactivate.sql` 反向置 DISABLED / REVOKED 并再次递增角色 version，不动 READ / MANAGE 授权（属 20261018 需求），328 行注册数据保留。所有改状态的语句都带当前状态条件，重复执行影响 0 行。

## 6. Compose

- ledger-service 增加 `XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_ADMIN_ACCESSTOKEN`、`XXL_JOB_EXECUTOR_ENABLED`、`XXL_JOB_EXECUTOR_APPNAME`、`XXL_JOB_EXECUTOR_IP`、`XXL_JOB_EXECUTOR_PORT`、`XXL_JOB_EXECUTOR_LOGPATH`、`XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES`。其中 APPNAME 为 `ledger-service`，IP 为 `172.30.73.38`，端口 9999；ADMIN 地址、TOKEN 变量、日志路径和排除包与 account / billing 相同。
- **IP 依据：** `docker network inspect mor-test-backend` 显示 `mor-test-ledger-service-1 172.30.73.38/24`，与该服务 `networks.backend.ipv4_address` 及 `SPRING_CLOUD_NACOS_DISCOVERY_IP` 一致。同网段内 billing 是 .39、account 是 .40，不冲突。
- operations-console-bff 增加 `OPERATIONS_BILLING_BASE_URL: http://billing-service:18113`。
- YAML 用 `yaml.safe_load` 解析通过，两项值读取正确。mor-test 的 XXL 任务（ledger 执行器分组 + `accountingRuleVersionActivation`）属于发布阶段，本卡没有登记。

## 7. 验证（实际执行）

| 命令 / 检查 | 结果 |
|---|---|
| `./mvnw -o -q -pl platform-support/paytech-platform-support-access-control -am -DskipTests compile`（JDK 25，`paytech/`） | 成功 |
| 用生产类 `ConsoleResourceCapabilities(JsonMapper)` 加载清单（`java -cp target/classes:<dependency:build-classpath> CapCheck.java`，脚本在 scratchpad） | 成功：31 个组件，没有触发"页面能力或动作编码重复"；4 个财务组件的 route 与 actions 按预期输出 |
| Python 按 `ConsoleResourcePolicy.supported` 规则把 SQL 中财务资源（97xxx / 99xxx / 100—102）与清单交叉核对 | 16 个资源全部 OK，0 处不符 |
| SQL 结构检查（列数、主键唯一、事务配对）；阅读副本与初始化 DML 追加部分 `diff` | 0 错误；内容一致 |
| `npm --prefix requirements-site test` 改前基线 | 137 例，127 通过，10 失败 |
| `npm --prefix requirements-site test` 改后 | 137 例，127 通过，10 失败；失败用例名逐条与基线相同（Commerce ×3、checkout、Java tests organization、Payment refund、Project context、shared writing conventions、current implementation ×2） |

**没有运行：platform-support 带数据库的 auto 测试。** 该模块没有直接针对能力清单的测试；其 Spring 测试固定连接共享的本地 `mor_platform`，`PlatformDataCleaner` 与 `ApiCredentialMocker` 会断言库名必须是 `mor_platform` 并清理数据，不能改连本需求专用库，违反 00-common 的资源隔离约定，所以改用上面的生产加载类检查和交叉核对。worktree 原本没有 `requirements-site/node_modules`，测试时临时软链到主工作区的 node_modules，跑完已删除。

## 8. 偏离与需要卡 D / E 注意的地方

1. **billing 的 BFF 路径**定为 `/finance/billing-facts/v1/**`，没有照搬下游的 `/billing/v1/facts/search`：管理列表是 `GET /finance/billing-facts/v1`，由 BFF 转发到 `/billing/v1/facts/search`。这样与其他 `/finance/<资源>/v1` 路径形态一致。
2. **路径变量名**：accounting-periods 下沿用 BFF 既有的 `{accountingPeriodId}`（与 97407 一致，ledger 用 `{periodId}`）；试算路径沿用 91407 的 `{periodId}`。Gateway 不依赖变量名，BFF 映射时要对应上。
3. **Body 上限 0 的 POST**：billing 批准、发起关账、规则版本提交 / 审批 / 驳回，A / B 已删除这些接口的请求体；另有两个新接口（作废、发起试算）本身没有请求体。**BFF 和 Web 调用这些接口不要带请求体（包括 `{}`）**，否则会被 Gateway 按 Body 上限拒绝。如果 D / E 确实需要带请求体，告诉我改成 8192。
4. **超时**：发起关账在 ledger 同步执行 7 步，Gateway 响应超时 10000 ms（各块统一），BFF 现有 `read-timeout: 5s`。卡 D 要评估本机 / mor-test 数据量下是否会超时，需要时给关账类转发单独放宽 BFF 超时。
5. **新增 FIN-26**（requirements-site）：卡片只要求改"相关 FIN 用例"，但规则包维护没有对应的既有用例，参照 FIN-25（科目维护）的先例新增一条。
6. **billing README**：卡 B 也改了同一文件（接口表与操作人一段）。本卡只改模块表两处、并在操作人一段后追加两句，没有覆盖 B 的内容，合并时要留意。

## 9. 未完成 / 不归本卡

- `agent-docs/CURRENT_IMPLEMENTATION.md` 未更新（卡片没有要求，留给交付或发布阶段统一登记）。
- mor-test 的 XXL 任务登记、SQL 执行与激活属于发布阶段，均未执行。
- Web `pageCapabilities.ts`（卡 E）与 BFF 转发（卡 D）未改，组件与路由合同见第 3、4 节。
