# reconciliation 对账控制台详情页与渠道流水：mor-test 发布清单

阶段 6（2026-10-21）。只做了只读核对、在干净 worktree 里构建镜像和准备发布包，**没有改 mor-test 的任何容器、数据、队列或 XXL 登记**。阶段 7 由主会话按本清单执行（用户已授权 6—7），结果写第 8 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 reconciliation、platform-support、operations-console-bff、operations-console-web 四个容器换成「13c02c87 + 商务未提交 commercialPreview.ts + 本需求 70 个文件」构建的镜像；`mor_platform` 注册并激活块 126（38 行，激活 3 / 1 / 1）。不改任何库结构，不改 `mor_reconciliation` 数据，不新增环境变量，不动 RabbitMQ 和 XXL。其余容器不动。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop 的 `mor-test`；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306`。不是生产 |
| 源码 | BASE = main `13c02c87`（币种会话提交，父 `5a103f36` 商务提交）；B = 商务会话未提交的 `operations-console-web/src/commercial/commercialPreview.ts`（+ test）；本需求 A 22 / M 48（[source-overlay.txt](./source-overlay.txt)：reconciliation 28、ops-bff 15、platform 2、ops-web 25）。见第 2 节 D1 |
| 镜像（4 个） | reconciliation（3 个只读接口 + 2 个 404 错误码）；ops-bff（3 个转发端点、404 映射）；ops-web（6 类详情页、渠道流水页、列表筛选存 URL）；platform（能力清单多 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`） |
| SQL（root） | `mor_platform` ← [db/platform-support/registration-dml.sql](../db/platform-support/registration-dml.sql)（块 126 休眠 38 行）+ [test-activate.sql](../db/platform-support/test-activate.sql)（3 / 1 / 1） |
| 不动 | Gateway、Nginx、ledger、billing、account、commercial 等其他服务；MySQL / Redis / RabbitMQ / Nacos / XXL 容器；`test-environment/compose*.yaml` 与脚本不改（用本目录覆盖文件） |

## 2. 制品与决定

| 服务 | 上一版（运行中，回滚目标） | 候选（tag `recon-detail-20261021`，部署只用 ID） | 候选 JAR SHA-256 前缀 | 与运行版比较 |
|---|---|---|---|---|
| reconciliation-service | `sha256:f181a04cad2d…`（切片 1，JAR `c66e59bd935d64e5`） | `sha256:b31a853da15c90e6e6f7b0e95a681ac6f5c0784aa97b1a4513244998343501e3` | `7b66f7dd78133bd3` | 新增 12 项、改 18 项，全部是本需求的 reconciliation api / core / app 类与两个 Mapper XML |
| platform-support-service | `sha256:b2a63ff4eadc…`（币种会话 currency-20261021，JAR `90d10c2ba8620ed6`） | `sha256:3d8bc3752c37da4f57e6bfad764a6712758bef45b484980262ddbe68414ebd72` | `6e44506745810b7d` | 只差 1 项：`console-resource-capabilities.json`，48 → 49 个组件，只多 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`，原 48 项逐项相同 |
| operations-console-bff | `sha256:fddb1993f88f…`（币种会话 currency-20261021，JAR `044e43c12bb6d2a6`） | `sha256:e822301cf35cab0dbce8ea87619d37d86c6ca5bed51c04fb2b3fa5b16968eab4` | `35c31f76cee2ed22` | 新增 7 项、改 14 项，全部是对账类（3 个控制器 / 客户端 / 模型、`OperationsErrorStatusResolver`、`ReconciliationClientConfiguration`、reconciliation-api） |
| operations-console-web | `sha256:c6d2118fd9dc…`（商务 capability-20261021g） | `sha256:70829a3a9213f680b4ce8c100115996799fd3a800997faa1a85414dcfca19ef8` | 入口 `assets/index-*.js` 换名 | 多 `ReconChannelLines`、`ReconDetail`、`ReconParts`(+css)、`useReconDetail`、`useReconListQuery`；`recon.js/css` 并入 `ReconParts`；对账页内容变化；其余 chunk 只有 Vite 哈希引用级联（±5 字节）；`nginx.conf` 一致 |

构建：`git worktree add --detach <scratchpad>/wt 13c02c87`；叠加 B 与本需求文件；`./mvnw -B -o -DskipTests -pl platform-support/…-app,operations-console-bff/…-app,reconciliation/…-app -am clean package`（JDK 25）；`npm ci && npm run build`；Java 镜像用 `test-environment/java/Dockerfile`（与主工作区一致），`health-probe/` 从运行容器 `docker cp`（三个服务逐字节一致）；Web 用 `operations-console-web/Dockerfile`。镜像内 JAR 与构建产物 SHA-256 一致，镜像 Entrypoint / Cmd / User / Env / WorkingDir / ExposedPorts 与运行镜像一致。未改 `:development` tag 与 `test-environment/artifacts`。

**D1 基线复现（先证明，再叠加）。**
- 只用 `13c02c87` 构建：platform、ops-bff、reconciliation 三个 JAR 与运行容器的 JAR 逐项比较（含嵌套 JAR 内每个条目的内容哈希）**0 差异**（76,974 / 36,599 / 61,710 项）。
- ops-web：只用 `13c02c87` 构建的 `dist/` 与运行版只差 `commercialPreview` 一个 chunk（运行版多 `limitConditions`）。主工作区该文件有商务会话未提交的修复（blob `git hash-object` 见 source-overlay.txt 的 B 行）；叠加后重建，`dist/` 与运行容器 158 个文件 **逐字节一致**（`diff -r` 无输出），`nginx.conf` 一致。所以候选 ops-web 不会回退商务已发布的内容。
- `Code-MoR-currency-rel` 只读核对：其工作区相对 `13c02c87` 在 `paytech/` 下只差 `platform-support-initial-data.sql`（文档，不进 JAR），在 ops-web 下只差 6 个较旧的商务文件；与上面结论一致。

**D2 共享文件只取本需求 hunk。** 主工作区里其他会话的未提交改动（channel-connector 对外出口迁移：ops-bff `Integration*` / `ExternalApi*` / `ChannelConnectorClientConfiguration` / `PlatformClientConfiguration`、platform-support 400+ 文件删除、arch-rules、`compose.applications.yaml`、`docs/design.pen`）都不进叠加清单。`paytech/operations-console-bff/README.md` 只叠加本需求一段（另一段属 channel-connector 会话）。其余共享文件（`OperationsErrorStatusResolver`、`OperationsConsoleBffApplicationAutoTest`、`PlatformOperationsSimulator`、`console-resource-capabilities.json`、`platform-support-initial-data.sql`、`menu.ts`、`pageCapabilities.ts`、`ConsolePage.tsx`）相对 `13c02c87` 的差异逐个看过，全部属于本需求，整文件取用。

**D3 块 126 阅读副本。** `registration-dml.sql` 第 26–80 行（`START TRANSACTION` 到 `COMMIT`）与 `platform-support-initial-data.sql` 末尾块 126 逐行一致（`diff` 无输出）。执行用阅读副本（文件头写着"不要单独执行"是指本机按主文件执行；mor-test 沿用切片 1 的做法用阅读副本，内容相同）。

**D4 环境变量不变。** 两个覆盖文件都写了 reconciliation 的 18 个切片 1 变量和 BFF 的 `OPERATIONS_RECONCILIATION_BASE_URL`，值与运行容器相同。原因：`13c02c87` 的 `compose.applications.yaml` 不含这些变量，它们目前只在主工作区的未提交 hunk 里；写进覆盖文件后，即使主工作区那段丢失，渲染结果也不变。渲染后逐键比较运行容器（只比较键和值是否相等，不输出值）：四个服务**完全一致**（ops-web 只多基础镜像自带的 `ACME_VERSION`）。

## 3. 只读核对结果（2026-10-21 05:4x–06:0xZ）

| 项 | 结果 |
|---|---|
| 运行镜像 | reconciliation `f181a04c…`（03:12:47Z 起）、platform `b2a63ff4…`（05:01:31Z）、ops-bff `fddb1993…`（05:11:03Z）、ops-web `c6d2118f…`（05:35:56Z），重启都是 0，均 healthy；另 ledger `fa21520d…`、billing `90d7401a…`（本需求不动） |
| 运行 JAR / dist | 见第 2 节 D1：基线 0 差异 / 逐字节一致 |
| `mor_platform` 块 126 | 七张注册表按 ID 段（含子表的 route_id）、3 个路由码、3 个外部路径（GET）、资源码 / componentKey / 前端路由都是 0 行（[sql/00-precheck-20261021.out](./sql/00-precheck-20261021.out)） |
| `mor_platform` 块 123 | 路由 38、资源 10、授权 3 都是 ACTIVE；父菜单 `900000000000123301` ACTIVE；读权限 `900000000000123101` 存在；Target `900000000000000001` 存在；PLATFORM_ADMIN 版本 **39**（激活后应为 40） |
| `mor_reconciliation` | 15 张表；演示数据：清算账户 2、银行账户 2、渠道账单 7、账单行 25、平台流水 17、银行对账单 4、银行流水 5、银行匹配 2、异常 11（未结 8）。验收只读，执行后这些数字不应变 |
| 覆盖文件 | `./test-environment/compose.sh -f <候选 / 回滚> config --quiet` 都通过；渲染镜像为第 2 节的 ID |
| 候选测试（构建 worktree） | Maven 打包通过；Web `tsc` 通过、`npm run build` 通过、`npm test` 1241/1242，失败 1 例见第 9 节 R1（只是测试断言，不影响镜像） |

## 4. SQL 清单

| 步 | 目标 | 文件 | 前置核对 | 预期 | 回滚 |
|---|---|---|---|---|---|
| SQL-0 | 只读 | [sql/00-precheck.sql](./sql/00-precheck.sql) | — | 与第 3 节一致（块 126 全 0；PLATFORM_ADMIN 版本记下来） | — |
| SQL-1 | `mor_platform` | [db/platform-support/registration-dml.sql](../db/platform-support/registration-dml.sql)（自带事务，休眠） | 块 126 全 0 | 资源 1、路由 3、Header 6、Query 10、限流 18 = 38 行，均 DISABLED；不可重跑 | [sql/rollback-unregister.sql](./sql/rollback-unregister.sql)（先停用；只删 DISABLED 行） |
| SQL-2 | `mor_platform` | [db/platform-support/test-activate.sql](../db/platform-support/test-activate.sql) | SQL-1 已提交；四个服务已是候选版且 healthy | 路由 3、资源 1、角色版本 1；PLATFORM_ADMIN +1 | [test-deactivate.sql](../db/platform-support/test-deactivate.sql)（3 / 1 / 1） |
| 核对 | 只读 | [sql/99-postcheck.sql](./sql/99-postcheck.sql) | — | 每个键的期望值写在键名里 | — |

执行包装：[scripts/apply_sql.sh](./scripts/apply_sql.sh) `<备份目录> precheck|platform-reg|platform-act|platform-deact|platform-unreg|postcheck`，root 口令从 `test-environment/.env.test` 读，经 `MYSQL_PWD` 传，不回显；输出写到备份目录的 `sql-<步>.out`。

## 5. 执行步骤（阶段 7，主会话执行）

停容器、起容器、root SQL 都由主会话执行（mor-test-deploy-lessons）。`$O` 指本目录 `compose-candidate.yaml` 的绝对路径，`$B` 指备份目录，`up` 指 `./test-environment/compose.sh -f $O up -d --no-deps --no-build --pull never --wait --wait-timeout 180`。

0. **协调**：先 SendMessage 给商务会话 **code-mor-98**（ops-web 现网 `c6d2118f` 是它的 capability-20261021g）和币种会话 **code-mor-50**（platform `b2a63ff4`、ops-bff `fddb1993` 是它的 currency-20261021）：本次会替换 platform、ops-bff、ops-web；候选已包含 `13c02c87` 全部内容与商务未提交的 `commercialPreview.ts`。确认双方当前不在部署、没有待发布的新改动，约定窗口；完成后通知新的 ID 和源码基线（13c02c87 + source-overlay.txt）。**如果在此期间任何一方又发布了这三个服务之一，本包作废，要在新基线上重建。**
1. **复核基线**：`docker inspect` 四个容器的 `.Image`，必须等于 [compose-rollback.yaml](./compose-rollback.yaml)；全部 healthy；`apply_sql.sh` 前先跑第 2 步的备份（备份里带 precheck 输出），结果与第 3 节一致（PLATFORM_ADMIN 版本可能因其他会话激活而更高，记下实际值即可）。有任何不同就停下。
2. **备份**：`B=$(release/scripts/backup.sh)` → `~/Code-MoR-backups/<UTC>-recon-detail-mor-test/`（0700 / 0600，SHA256SUMS）：`mor_platform` 注册 8 表、四个运行镜像 ID、三个运行 JAR 的 SHA-256、两个覆盖文件、precheck 输出。
3. **reconciliation**：`up reconciliation-service`。核对：healthy、重启 0、启动日志 ERROR 0、`.Image` = 候选；RabbitMQ `reconciliation.billing.p0..p3` 各 1 个消费者（与切片 1 相同）；XXL 执行器约 30 秒后重新注册到分组 17。
4. **Platform 注册**：`apply_sql.sh $B platform-reg`（休眠 38 行）。
5. **platform → ops-bff → ops-web**：依次 `up platform-support-service`、`up operations-console-bff`、`up operations-console-web`，每次都做第 3 步那组核对（platform 另核对币种快照：ledger / billing 就绪不受影响，日志无 `currency-specs` 读取失败）。
6. **激活**：`apply_sql.sh $B platform-act`（3 / 1 / 1），然后 `apply_sql.sh $B postcheck`，全部等于期望值。等 Gateway Snapshot 刷新（≤ 60 秒）。
7. **冒烟（管理员，只读）**：`GET /api/operations/authorization/v1/navigation` 里对账分组下有 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`（排序 45）；`GET /api/operations/finance/reconciliation/v1/channel-statement-lines?page=1&size=10` 200；`/bank-statements/999999999999`、`/bank-statement-lines/999999999999` 都是 **404**（`BANK_STATEMENT_NOT_FOUND` / `BANK_STATEMENT_LINE_NOT_FOUND`）。
8. **浏览器验收（只读）**：`python3 -I release/acceptance/stage7_browser.py`（管理员 operator@example.test，口令运行时从 `test-environment/private/access.md` 读，不打印）。内容见第 7 节。
9. **记录**：把第 8 节填完，`delivery.md` 补环境操作记录，再通知 code-mor-98、code-mor-50 新的镜像 ID 与源码基线。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| 激活 | `apply_sql.sh $B platform-deact`（3 / 1 / 1）；菜单和三条路由立即失效，数据不受影响 |
| 注册 | 休眠行可以留着；要删就先停用，再 `apply_sql.sh $B platform-unreg`（18 / 10 / 6 / 3 / 1） |
| ops-web / ops-bff / platform | 先停用激活（避免出现能看到入口但调用失败），再 `./test-environment/compose.sh -f <compose-rollback.yaml> up … <service>`，回到 `c6d2118f` / `fddb1993` / `b2a63ff4`。旧 ops-web 不认识渠道流水页，旧 platform 能力清单没有该组件，所以回滚这三项时必须同时停用块 126 |
| reconciliation | 用回滚文件 up，回到 `f181a04c`（只少 3 个只读接口，表结构相同，不需要 SQL）。若 ops-bff 仍是候选版，渠道流水 / 两个详情接口会返回 5xx，因此要与 ops-bff 一起回滚或先停用激活 |
| 数据 | 本需求不写业务数据，验收只读，没有清理步骤 |

## 7. mor-test 验收（只读，现有演示数据）

[acceptance/stage7_browser.py](./acceptance/stage7_browser.py)：管理员登录后只做导航、筛选查询、点行进详情、刷新、后退和「返回列表」，**不点任何写操作按钮**；主键从列表页现场取得。Gateway 每路由每人 30 次/分钟，脚本每次导航间隔 2.5 秒（全程约 6–8 分钟）。

| 段 | 检查 |
|---|---|
| 菜单（AC-V3） | 对账分组含「渠道流水」，紧跟「平台流水」；点菜单进入 `/finance/reconciliation/channel-lines` |
| 渠道流水（AC-05） | 默认列表与交易日倒序；页大小 10/20/50/100；按清算账户、匹配状态、行类型、账单状态各筛一次；外部交易号精确匹配（去掉末位应为空，显示「没有符合条件的渠道流水」）；交易日范围；点行进入所属账单详情 `?line=` 并高亮；后退回列表 |
| 六类详情（AC-01、AC-V1、AC-V2） | 清算账户、银行账户、渠道账单、银行对账单、银行流水、对账异常：列表点行进详情（无抽屉）、面包屑「财务/对账/…」、「返回列表」带回筛选、刷新仍在详情、后退回带筛选的列表 |
| 详情内容（AC-02–04） | 账单详情的全部行与对平门禁；对账单详情流水表；银行流水详情的匹配区块；异常详情的处置区块（只看） |
| 未找到（AC-01） | 六类详情用 `999999999999` 打开：显示「未找到该…」与返回按钮、无错误通知（既有 400 与新 404 两种） |
| 回归 | 对账平台流水 / 在途汇总；商务 9 页、财务 7 页、渠道 3 页、平台配置 → 币种 |
| 期望 | 全部通过；控制台错误 0；非预期 4xx/5xx 0（未找到用例的 400/404 单列）。结果写 `acceptance/stage7-browser-result.json`，截图 `acceptance/screenshots/` |

若演示数据缺某类对象（例如没有银行对账单），脚本会把该项记为"跳过"而不失败；执行人在第 8 节注明。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 2026-10-21 05:5x—06:1xZ | 0 协调 | code-mor-98、code-mor-50 均回复不在部署、可以开始 |
| 2026-10-21 05:5x—06:1xZ | 1 复核基线 | 四个容器 `.Image` 等于 compose-rollback.yaml，均 healthy |
| 2026-10-21 05:5x—06:1xZ | 2 备份 | `~/Code-MoR-backups/20261021T055349Z-recon-detail-mor-test/` |
| 2026-10-21 05:5x—06:1xZ | 3 reconciliation | `b31a853d…` healthy、重启 0、ERROR 0；`reconciliation.billing.p0..p3` 各 1 消费者 |
| 2026-10-21 05:5x—06:1xZ | 4 Platform 注册 | 块 126 休眠行写入 |
| 2026-10-21 05:5x—06:1xZ | 5 platform → ops-bff → ops-web | `3d8bc375…`、`e822301c…`、`70829a3a…` 均 healthy、重启 0、ERROR 0；ledger / billing 仍 healthy |
| 2026-10-21 05:5x—06:1xZ | 6 激活 + postcheck | 3 / 1 / 1；postcheck 全部等于期望（PLATFORM_ADMIN 39 → 40） |
| 2026-10-21 05:5x—06:1xZ | 7 冒烟 | 导航含 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`；渠道流水 200（18 行，已排除作废 / 被拒）；两个新详情接口不存在 → 404；size=200 → 400 |
| 2026-10-21 05:5x—06:1xZ | 8 浏览器验收 | 首轮 56/66：8 项为脚本连续整页加载触发 `auth/v1/context` 限流（429），2 项为脚本按 `.page-heading h1` 判断而平台流水 / 在途汇总页无该标题（截图确认正常渲染）；脚本加节流与选择器修正后重跑回归 21/23 + 截图核对，合计 66 项均满足；详情页、未找到、渠道流水、跨页链接全部通过；控制台 0 错误 |
| 2026-10-21 05:5x—06:1xZ | 9 记录与通知 | 已通知 code-mor-98、code-mor-50 新镜像 ID 与源码基线 |

## 9. 风险与遗留

- **R1 Web 测试断言要改（阶段 8 前）**：`configurationModel.test.ts` 在 `13c02c87` 里断言页面数 57（币种会话已把渠道流水之前的 56 改成 57），加上本需求的渠道流水后实际是 58，`npm test` 1241/1242。主工作区同样会失败。只影响测试，不影响镜像；主会话把 57 改成 58 后复跑。
- **R2 基线漂移**：ops-web 基线依赖商务会话**未提交**的 `commercialPreview.ts`（blob 记在 source-overlay.txt）。执行前若商务会话改了这个文件并重新发布 ops-web，本包作废重建。platform / ops-bff 同理（币种会话）。
- **R3 环境变量来源**：切片 1 的 reconciliation / BFF 变量仍是主工作区 `compose.applications.yaml` 的未提交 hunk；覆盖文件已兜底（D4），阶段 8 需要把那段提交。
- **R4 四个服务分 4 次重启**，期间对账页可能短暂不可用；三个与其他会话共用，必须先协调（第 5 节第 0 步）。
- **R5 限流**：渠道流水沿用块 123 的 GET 限流（每人每路由 30 次/分钟），快速连续筛选可能遇到 429（阶段 5 已观察到）。
- **R6 索引**：只按外部交易号或不带清算账户查渠道流水时会扫描账单行表（后端报告 §6.3），mor-test 只有 25 行，不受影响。
- 构建 worktree 在会话 scratchpad，阶段 7 结束后删除（镜像保留）。
