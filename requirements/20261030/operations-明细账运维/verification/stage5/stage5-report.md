# operations：明细账运维 · 阶段 5 本机综合验证报告（T6）

日期：2026-10-30（UTC 15:05—16:08）· 环境：本机共享联调栈（`run-stack.sh`），只用 `*_v2e2e` 库，RabbitMQ vhost `/mor-e2e` · 入口：Nginx 18444 → Gateway → ops-bff · 浏览器：私有 Vite 18317（组合树 `Code-MoR-plops-e2e/operations-console-web`，Playwright 真实 Chromium）。
本代理未改生产代码，未提交、未推送、未碰 mor-test。D-1 / D-2 / D-3 由修复代理修改，修复后的 ledger 包 sha256 前缀 `523ff2a7c7fd50a4`。

## 1. 结论

| 项 | 结果 |
|---|---|
| AC-01—AC-11 | 除 AC-06 外通过（D-3 修复后复验，全部经 BFF / 页面）。AC-06 只验到"无过账流"，重建 409 没有复现；AC-02 的"FE 多条流"只覆盖了一条 OE 流。见第 4 节 |
| AC-V1—AC-V4 | 通过：zh-CN / en × 1440 / 390，最终一致态共 32 步，无横向滚动、无裸文案键 |
| 追平 200 批耗时（经 BFF） | 共 8 个样本：第一轮 4.53 / 5.60 / 6.72 / 5.10 s；第二轮直接调接口 5.34 / 4.39 / 5.05 s，浏览器经 Vite 一次 **7.52 s**（ledger 刚重启约 30 s 时）。7 个低于 7 s，1 个超过。按 technique 的门槛（超过 7 s 改为 100），**超了一次，是否改为 100 由你决定**（第 1b 节） |
| 缺陷 | D-1、D-2、D-3、D-4、D-5 以及英文复数，都已修复并回归通过（第 1a、1b 节）。仍有的已知限制：新标签页在 ledger 从未可用时，账簿下拉显示主键 |
| 最终数据（第二轮后） | ZQ344：account 3,555 批，ledger 已消费 3,555；两个 Owner 账户（第二个是为验证复数新增的合成 Client `888315298797128549`）；重建结果 `{accountCount 2, changedAccountCount 0, entryCount 3555}`；OE / FE 两个账簿三段都是 CONSISTENT；规则 36 行，5301/5302/5313/5314 与原行逐字段一致；全部服务 UP；Vite 18317 已停；DLQ 仍是 4 条旧消息 |

## 1a. 修复后回归（523ff2a7）

**时间线证据：**
- 修复后的 ledger（sha256 前缀 `523ff2a7c7fd50a4`）进程从 15:43:59Z 起运行（`ps lstart` 19:43:59 +04）。
- 回归第一步造落后（`t6_lag.py 600`）之前，先核对了 JAR 哈希；之后 billing / account / ledger 的每次重启用的都是同一个 JAR。
- 下表各项都在 15:47Z—16:08Z、修复生效之后执行，全部经运营入口或页面。
- 修复前的结论（D-3 阻塞、绕开 D-3 直接调 ledger）只作为历史记录保留在第 6 节，不作为最终结论。

| 回归项（team-lead 清单） | 结果 | 证据 |
|---|---|---|
| 1 造落后 | 共造 7 次：600（15:47Z）、5、5、3、250、800（用于 409），另有一次因下文提到的脚本故障误造的 5。方法都是：停 ledger → 登记事实 → 只清空 `ledger.account.p0–p3` → 启动 ledger | `t6_lag.py` 输出；B1 显示的落后数 |
| 2 追平 200 批 + 继续追平 | 经 Gateway 实测 4.53 / 5.60 / 6.72 / 5.10 s，都低于 7 s，**保留 200**。zh-CN-1440：落后 250 → "已处理 200 批，尚未追平 · 继续追平 · 剩余 50" → 继续追平后"已追平 本次处理 50 批"。en-390：小批量追平，显示 "Caught up · Processed 5 batches"。en-1440：剩 200 批时处理 200 批（显示问题见 D-5） | `PARTIAL-zh-CN-1440-C2-after-catchup-1/2.png`、`FIX-en-390-C2-after-catchup-2.png`、`FIX-en-1440-C2-after-catchup-1.png` |
| 3 规则拒绝（落后 ≥ 10） | zh-CN-1440 在**落后 10 批**时删除 5301 / 5302 → 400 就地显示错误码、说明和卡住的第 1,207 批 → 按原行恢复 → 重新追平，显示"已追平 本次处理 10 批"。en-390 在落后 5 批时重做一遍，也通过 | `FIX-zh-CN-1440-C2-after-catchup-1/2.png`、`FIX-en-390-C2-after-catchup-1/2.png` |
| 409 | 两个有执行权限的账号同时追平：一个 200，另一个 409 `CONCURRENT_REQUEST`；页面 409 结果条在 zh-CN-1440 和 en-390 都已截图 | `FIX409-*.png` |
| 4 D-1 / D-2 | zh / en × 1440 / 390 四个组合各停一次 billing、account、ledger：D-1 文案已更正；D-2 整页错误居中、不再弹多余通知、重试后恢复；每次重启后都确认 UP | `FIX-*-P8*.png` |
| 5 收尾 | 全部追平（2,274 = 2,274）；重建 `changedAccountCount 0`；规则 36 行，与原行逐字段一致；服务全部 UP；Vite 已停；DLQ 中 4 条旧消息未动 | 第 1 节"最终数据" |

**过程中的两次脚本故障：**
- 第一次批量循环用 zsh 的 `set -- $combo` 拆参数，zsh 默认不拆词，浏览器没有运行，规则删除后也没有自动恢复。
- en-390 有一次登录后跳回登录页，同样导致规则删除后没有自动恢复。
- 两次都在发现后立即手动恢复规则，删除持续不到 1 分钟。
- 第一次故障多造了 5 批落后，已并入后续的规则拒绝验证。

## 1b. 第二轮修复回归

时间 16:38Z—16:51Z。改动范围：只改 Web（`Code-MoR-plops-e2e`，私有 Vite 18317 热更新）；ledger 仍是 `523ff2a7c7fd50a4`。
环境：本机 Platform（066550a1）和 ops-bff（e5767759）是 REQ-06 会话换上的三方组合包，本轮没有停、换、重启这两个服务。只停启过 ledger：造落后 10 次，D-2 回归 2 次。造落后只用 ZQ344。

| 回归项 | 结果 | 证据（`screenshots/`） |
|---|---|---|
| D-5 正好落后 200 批 | **通过**。zh-CN-1440：一次追平后显示"已追平 · 本次处理 200 批，ledger 已消费到第 2,474 批，与 account 一致"，没有"继续追平"。en-390：显示 "Caught up · Processed 200 batches in this run…"，同样没有继续按钮 | `R2-D5exact-zh-CN-1440-C2-after-catchup-1.png`、`R2-D5exact-en-390-C2-after-catchup-1.png` |
| D-5 落后 250 批 | **通过**。zh-CN-1440：第一次显示"已处理 200 批，尚未追平…剩余 50 批"并出现"继续追平 · 剩余 50"，点继续后显示"已追平 本次处理 50 批" | `R2-D5partial-zh-CN-1440-C2-after-catchup-1/2.png` |
| D-4 成功条、部分追平条 | **通过**。两种结果条都显示"时间 · 操作人 o***@example.test"（en 为 "· by o***@example.test"） | 同上 |
| D-4 规则拒绝：一批都没处理 | **通过**。zh-CN-1440 落后 12 批、en-390 落后 10 批，删除 5301/5302 → 400，拒绝条显示"时间 · 操作人"、错误码和说明，以及"本次未处理任何批次。"（en："No batches were processed in this run."）→ 按原行恢复 → 重新追平，显示"已追平 本次处理 12 / 10 批" | `R2-D4none-zh-CN-1440-C2-*.png`、`R2-D4none-en-390-C2-*.png` |
| D-4 规则拒绝：处理到一半停止 | **通过**。造落后时 3 条普通事实加 1 条带平台服务费的事实，删除 5313/5314（服务费 × 交易 OE）。zh-CN-1440：拒绝条显示"停止前已处理 3 批，已处理的批次已保留。"，进度同步刷新（已消费 3,539，卡住第 3,540 批）→ 恢复后追平 1 批。en-390：显示 "2 batches were processed before it stopped"（account 入账顺序里服务费事实排第 3），恢复后追平 2 批。5313/5314 原行事先导出到 [migration/rule-lines-5313-5314.backup.sql](migration/rule-lines-5313-5314.backup.sql) | `R2-D4adv-zh-CN-1440-C2-*.png`、`R2-D4adv-en-390-C2-*.png` |
| D-4 重建结果条 | **通过**。"已纠正"和"无需纠正"两种结果条都显示"时间 · 操作人 o***@example.test"（en-390、zh-CN-1440、en-1440） | `R2-one-en-390-P5*.png`、`R2-two-*-P5*.png` |
| D-2 残留：同一标签页 | **通过**。已正常打开 ZQ344 账簿的标签页，停 ledger 后点"刷新"：账簿下拉仍显示"ZQ344 · USD 经营主体账簿"（en："ZQ344 · USD Operating entity book"），整页错误居中，没有弹出通知 | `R2-zh-CN-1440-D2b-same-tab-ledger-down.png`、`R2-en-390-D2b-*.png` |
| D-2 残留：新标签页（已知限制） | 新标签页在 ledger 从未可用时，下拉显示主键 `241946219587354624`，没有弹出通知。启动 ledger 后点"重试"即恢复。每次停 ledger 后都立即启动并确认 UP（4—5 s） | `R2-*-D2c-new-tab-ledger-down.png`、`R2-*-D2d-retry.png` |
| 英文复数 | **通过**。追平："Processed 1 batch"（新增 1 条第二 Client 事实后追平）、"Processed 200 / 10 / 2 batches"。重建："Recalculated 1 account, 1 corrected" / "Recalculated 1 account; no balance changed"（新增第二个账户之前），"Recalculated 2 accounts, 1 corrected" / "Recalculated 2 accounts; no balance changed"（新增之后）。中途停止："2 batches were processed before it stopped"。未覆盖："1 batch was"、"1 entry"（流水数总是多条） | `R2-one-en-390-*.png`、`R2-two-en-1440-P5*.png` |
| 200 批耗时 | 直接调接口（经 Nginx → Gateway → BFF）：ledger 重启后第 1、2、3 次分别为 5.34 / 4.39 / 5.05 s。浏览器经私有 Vite 代理、ledger 刚重启（约 30 s）时一次：**7.52 s**（Playwright 记录的请求 timing）。结合第一轮共 8 个样本，最大 7.52 s、中位数约 5.2 s | `t6-browser-result-r2-d5exact-en.json`（`ms: 7516`） |
| 横向滚动 / 裸文案键 | 本轮 38 步，全部 `scrollWidth == clientWidth`，`rawKeys` 为空 | `t6-browser-result-r2-*.json` |
| 收尾 | 全部追平（3,555 = 3,555）；重建 `changedAccountCount 0`；规则 36 行，4 条被删过的行与导出原行逐字段一致；全部服务 UP；Vite 已停；DLQ 4 条旧消息未动 | — |

本轮其他数据写入：
- billing 新增 MANUAL_ADJUSTMENT 共 1,281 条：落后 200 + 250 + 200 + 600 + 12 + 4 + 10 + 4，再加 1 条给第二个合成 Client。
- 规则行删除 4 次：5301/5302 删 2 次（zh-CN-1440、en-390 各 1 次），5313/5314 删 2 次（同上）。每次都由脚本在第一次点击后恢复，脚本结束后再兜底检查一次，均没有出现中断。
- Owner 余额用 SQL 改写 3 次（每次只改一个账户 +5），都由重建恢复。

## 2. 数据与账号

- **写入前备份**：`~/Code-MoR-backups/20261030T150513Z-plops-stage5-t6/`，含 `mor_account_v2e2e` / `mor_ledger_v2e2e` / `mor_billing_v2e2e`（gzip mysqldump）。ledger dump 解压后 87,941 B，以 "Dump completed" 结尾。
- **合成主体**：
  - 新 OE `ZQ344`；Finance Entity `241941236133445632`（`PLOPS_ZQ344`，legalPartyId 为合成值）。
  - 各有日历和 2026-10 OPEN 期间。
  - USD 账簿在迁移后重建：OE `241946219587354624`、FE `241946219776098304`。
- **合成计费事实**：2,274 条 MANUAL_ADJUSTMENT。
  - 每条是 CLIENT_PRINCIPAL 1.00，付给 Client `888315298797128548`；第 1 条另带 PLATFORM_SERVICE_CHARGE 0.10，付给 FE。
  - 编制人是 E2E 管理员，复核人是合成 ID `236033080844881921`。
  - 全部经 billing 私网命令登记（请求头与 ops-bff `FinanceBillingFactClientImpl` 相同），经 MQ 进入 account。
- **运营账号**：
  - 新建只读账号 `plops-t6-readonly-1030191229@example.test`（Account `241942349058412544`，角色 `PLOPS_T6_READONLY_1030191229` / `241942346302754816`，只含 `FINANCE_ACCOUNTING_READ`），**保留**。
  - 复用 payout 阶段 5 的两个账号：
    - `s1e2e-noperm-1030142229@example.test`：无查看权限，用于 AC-09；
    - `s1e2e-checker-1030142229@example.test`：PLATFORM_ADMIN，用于 AC-08 并发 409。
- **运行状态**（含合成账号口令）：`~/Code-MoR-backups/plops-stage5-t6/state.json`，不放仓库。

## 3. 环境迁移（用户授权方案 A，仅本机 `mor_ledger_v2e2e`）

**原因**：v2e2e 缺少 2026-10-19「ledger-会计科目分级维护」的结构变更（该需求阶段 5 当时改在 mor-test 执行）。ledger 消费 AccountPosted 时报 `BadSqlGrammarException: Unknown column 'account_no'`（ledger.log 15:08:23Z），任何过账都失败。

| 步 | 文件 | 结果 |
|---|---|---|
| 0 | 备份完整性 | ledger dump 非空，以 "Dump completed on 2026-10-30 15:05:13" 结尾 |
| 1 | [migration/00-delete-t6-books.sql](migration/00-delete-t6-books.sql)：删除 T6 刚建的两个 STANDARD 账簿（dml 的前置条件：没有账簿采用 STANDARD） | 删前核对账户 0、分录 0；删除 `t_book_sequence` 2 行、`t_ledger_book` 2 行 |
| 2 | `requirements/20261019/ledger-会计科目分级维护/db/ledger/ddl.sql` 第 1 段（拆出为 [migration/ddl-part1.sql](migration/ddl-part1.sql)） | 2 个 ALTER、2 个 CREATE TABLE 成功 |
| 3 | 同目录 `dml.sql` | `books_adopting_standard=0`；插入 45 行、更新 29 行；核对 total 74 / numbered 74 / level3_leaves 29，bad_parent 0 |
| 4 | `ddl.sql` 第 2 段（[migration/ddl-part2.sql](migration/ddl-part2.sql)） | 回填 0 行（没有存量账户）；两项核对都是 0；改为 NOT NULL 并加唯一索引成功 |
| 5 | 结构比对 | `mor_ledger_v2e2e` 与 `mor_ledger` 的列、类型、可空、默认值、排序规则、索引（456 行）逐项 diff：**一致** |
| 6 | 用 `run-stack.sh` 重启 ledger，经运营入口重建两个账簿 | 成功；DLQ 里的旧消息没有手动重投，序号 1、2 由追平补上 |

执行日志：[migration/execution.log](migration/execution.log)。

**回滚**：
- 从 `~/Code-MoR-backups/20261030T150513Z-plops-stage5-t6/mor_ledger_v2e2e.sql.gz` 整库恢复。这样会同时撤回本次全部 T6 ledger 数据。
- 或者按 ddl.sql / dml.sql 文末"恢复"段逆序执行，但要先删除所有采用 STANDARD 的账簿。

## 4. 验收明细（D-3 修复后的结果；修复前的情况见第 6 节）

| AC | 结果 | 证据 |
|---|---|---|
| AC-01 | 通过 | B1 的 `factSegment` 中 billingPublished 与 accountApplied 相等（从 2 到 2,274），最大 factId 相同，状态 CONSISTENT。页面第 ① 段显示"已一致"（`FINAL-*-P2-oe-behind.png`） |
| AC-02 | 通过（形态有限） | FE 账簿返回 OE.ZQ344.USD.0 流（来源是服务费事实），页面标注"服务费所在 OE"（`*-P9-fe-book.png`）。合成数据只有一个 OE，没有做出原型 P9 的"FE 自身流 + 多条 OE 流"；FE 自身没有流，因为服务费只落平台侧，不产生 Owner 流水 |
| AC-03 | 通过 | 落后时显示 ledgerConsumedSequence、lag、BEHIND 和 `stuckPosting`（序号、事实号、类型、生成时间、AccountPosted 是否已发出、流水条数）。第 ③ 段和流行用警示色，有"追平"入口，"查看流水"能打开抽屉（`FIX-*-C1-behind.png`、`zh-CN-1440-P2-oe-behind.png`） |
| AC-04 | 通过 | B2 `limit=200` 返回 200 条，用时 0.064 s，nextFromSequence 为 201；尾页 next 为 null；`limit=30` 返回 400 INVALID_REQUEST。页面从流行进入、按序号升序、卡住的批次高亮，抽屉与明细账页一致，返回时保留账簿（`*-P6*.png`） |
| AC-05 | 通过 | 用 SQL 把余额多加 5 → 重建返回 `changedAccountCount 1`，余额恢复；再重建为 0。account.log 有 `入账流余额已重建: OPERATING_ENTITY/ZQ344/USD, 操作人 236033080844881920, 账户 1, 余额有变化的账户 1, 流水 2`。浏览器四个组合都走过"确认 → 已纠正 → 无需纠正"（`*-P4/P5/P5b*.png`）。收尾重建结果：`{accountCount 1, changedAccountCount 0, entryCount 2274, maxPostingSequence 2274}` |
| AC-06 | 部分通过 | 无过账流：FE 主体和 OE+EUR 都返回 400 `STREAM_NOT_FOUND`「该主体与币种下没有过账流」。重建 409 没有复现：Gateway 对同一主体限并发 1，8 个并发请求被串行化后全部 200。要复现得让两个账号的重建与入账真正重叠，重建只需 0.05—0.1 s，成本高，没有做 |
| AC-07 | 通过 | 落后 600 批时经页面追平：第一次显示 `已处理 200 批，尚未追平…剩余 200 批` 和"继续追平 · 剩余 200"（文字证据在 `t6-browser-result-fix-catchup-zh.json`，截图后来被同名的 AC-08a 截图覆盖）。之后另造落后 250 批重拍：先剩余 50（`PARTIAL-zh-CN-1440-C2-after-catchup-1.png`），点"继续追平"后变为 `已追平 本次处理 50 批`（`PARTIAL-zh-CN-1440-C2-after-catchup-2.png`）。恰好剩 200 批时的问题见 D-5 |
| AC-08 | 通过（并发冲突） | 管理员和 checker 两个账号经运营入口同时追平同一条流：checker 得到 409 `CONCURRENT_REQUEST`「当前资源正在执行」（0.12 s），管理员得到 200（处理 200 批）。页面上让 checker 在后台追平、管理员点"追平"，结果条显示"暂时无法追平（409）…已处理的批次已保留，稍后重新追平即可"（`FIX409-zh-CN-1440-C2-after-catchup-1.png`、`FIX409-en-390-C2-after-catchup-1.png`），刷新后能看到 checker 已处理的批次。"没有 OPEN 期间"这一种 409 需要走关账流程，没有做 |
| AC-08a | 通过 | 每次先造少量落后，再删除规则行 5301 / 5302（原行先导出到 [migration/rule-lines-5301-5302.backup.sql](migration/rule-lines-5301-5302.backup.sql)）。追平返回 400，卡住批次卡片内显示 `BUSINESS_CHECK_FAILED: 控制科目过账拒绝: … 费用类型 CLIENT_PRINCIPAL × 角色 OPERATING_ENTITY × DEBIT 匹配 0 条规则行: 行 1`，并给出"前往「记账规则」补齐规则 / 重新追平"。按原行恢复后点"重新追平"成功。zh-CN-1440（`FIX-zh-CN-1440-C2-after-catchup-1/2.png`）和 en-390（`FIX-en-390-C2-after-catchup-1/2.png`）各做一遍。恢复后与原行逐字段比对一致（含 create_time） |
| AC-09 | 通过 | 只读账号：导航只有 `OPERATIONS_FINANCE_POSTING_OPS`；B1、B2 返回 200，B3、B4 返回 **403** `OPERATIONS_PERMISSION_DENIED`；页面没有"追平""重建余额"，显示"需执行权限"（`*-P10-readonly.png`，四个组合）。无查看权限账号：导航没有这一页，B1 返回 403，页面显示"暂无访问权限"（`*-R-noperm.png`） |
| AC-10 | 通过（修复后） | 停 billing：只有第 ① 段不可用，文案说明 ②③ 段不受影响。停 account：三段都不可用（第 ① 段要比对 account 已应用的事实），第 ① 段文案改为"account 暂时无法访问…account 恢复后才能判断"（D-1 已修）。停 ledger：B1 返回 503，整页错误居中显示，不再弹额外通知（D-2 已修）；重试后恢复。四个组合都有截图（`FIX-*-P8*.png`）。每次重启约 4 s 后 UP |
| AC-11 | 通过 | 余额被改写 → 重建恢复（见 AC-05）。ledger 落后（停 ledger、登记事实、只清空 `ledger.account.p0–p3`）→ 经页面追平恢复（见 AC-07）。最终全部一致 |
| AC-V1 | 通过 | 1440 下三段从左到右，390 下纵向排列；有差距的段用警示色并显示差额 |
| AC-V2 | 通过 | 追平在流行和卡住批次卡片上，重建在页面头部；确认弹窗是一句话加三条影响 |
| AC-V3 | 通过 | 批次浏览的表格风格与明细账一致；返回时保留账簿（URL 保留 `ledgerBookId`） |
| AC-V4 | 通过 | 最终一致态四个组合 32 步，以及落后、失败、结果各状态的截图，`scrollWidth == clientWidth`，`rawKeys` 为空 |

## 5. 耗时

| 场景（经 Nginx → Gateway → BFF → ledger） | 结果 |
|---|---|
| 追平 200 批，落后 600，单独调用 | 4.534 s，返回 `{processedCount 200, caughtUp false}` |
| 追平 200 批，同时有另一账号的请求（那一个得到 409） | 5.595 s |
| 追平 200 批，同时有浏览器操作（两次） | 6.718 s、5.102 s |
| B1 进度 | 0.03—0.09 s |
| B2 200 条 | 0.064 s |
| B4 重建（2,274 条流水） | 0.075 s |

- 200 批平均 5.49 s、最大 6.72 s，都没超过 7 s，按 technique 保留 `maxBatches=200`。
- 但裕量不大：每批约 23—34 ms，随并发负载变化；生产中每批流水行更多时可能超过 7 s。建议阶段 6 在 mor-test 再测一次。
- D-3 修复前的参考数据：直接调 ledger，每次 9 批，平均每批 27.8 ms，外推 200 批约 5.6 s，与修复后的实测一致。

## 6. 缺陷

### D-3（严重，已修复）：ledger 追平在落后 10 批及以上时一定失败

- **位置**：`paytech/ledger/paytech-ledger-app/.../AccountClientConfiguration.java:26` 的 `withStrictJsonResponses(8192)`，自 `080909fb` 起就是这样。`BillingClientConfiguration` 也是 8192。
- **复现（修复前）**：
  - 任一流落后 10 批以上时，在运营台点"追平"。
  - account 响应大小：limit=9 为 7,485 B，limit=10 为 8,312 B，limit=200 为 165,544 B。
  - 直接调 ledger 二分：`maxBatches` ≤ 9 正常，≥ 10 一律返回 `UPSTREAM_INVALID_RESPONSE`，日志没有原因。
  - BFF 把它显示成 503"依赖服务暂时不可用"（`D3-zh-CN-1440-C2-after-catchup-1.png`、`D3-en-390-*`）。
- **修复**（修复代理完成）：account 客户端上限改为 4 MiB，billing 客户端改为 8 MiB。
- **回归**：AC-07、08、08a 全部经 BFF 通过（第 4 节）。
- **遗留建议**：BFF 把 ledger 的 5xx 一律显示为 503，会掩盖这类问题，可以另行评估。

### D-1（中，已修复）：account 不可用时，第 ① 段把原因写成了 billing

- 修复前显示"billing 暂时无法访问…其余两段不受影响"（`zh-CN-1440-P8c-account-down.png`）。
- 修复后显示"account 暂时无法访问。这一段要比对 billing 与 account 已应用的事实，account 恢复后才能判断。"；停 billing 时改为"第 ②、③ 段来自 account 与 ledger，不受影响"。
- 四个组合回归通过（`FIX-*-P8c-account-down.png`、`FIX-*-P8-billing-down.png`）。

### D-2（低，已修复；残留项已在第二轮修复）：ledger 不可用时整页错误布局异常并重复弹通知

- **修复前**：说明文字被挤成左侧一窄列，另外弹出"账簿加载失败 / 财务主体加载失败"两个通知，390 下遮住"重试"按钮（`zh-CN-1440-P8d-ledger-down.png`、`en-390-P8e-ledger-retry.png`）。
- **修复后**：整页错误居中，宽 560 px，不再弹通知，重试后恢复（`FIX-*-P8d/P8e*.png`）。
- **残留**：账簿下拉仍显示原始 ID `241946219587354624`（账簿列表同样来自 ledger），原型 P8 显示的是"AE001 · USD"。建议改为显示上次已知的标题，或注明"账簿列表暂不可用"。

### D-5（低，第二轮已修复，见第 1b 节）：恰好剩 200 批时，结果显示"尚未追平 · 剩余 0 批"

- **复现**：落后正好 200 批时点"追平"。ledger 处理满 `maxBatches` 后不再看下一页，返回 `caughtUp=false`。页面显示"已处理 200 批，尚未追平…剩余 0 批"（`FIX-en-1440-C2-after-catchup-1.png`；zh-CN-1440 的文字证据在 `t6-browser-result-fix-catchup-zh.json`），但进度已经是"已一致"，也没有"继续追平"按钮。
- **建议**：页面按刷新后的剩余数（0）判定为"已追平"；或者 ledger 处理满后再看一眼下一页是否为空。

### D-4（低，第二轮已修复，见第 1b 节）：追平 / 重建结果条缺少操作人

- **设计出处**：
  - technique.md 第 9 节画板表 P3「财务 · 24 追平结果与失败原因」：卡住批次卡片里的规则拒绝显示"错误码 + 说明 + **操作人时间**"。原型 `prototypes/P3-追平结果与失败原因.png` 的拒绝条是"10:24:31 · 操作人 ops.zhang"。
  - 同节 P5「财务 · 26 重建余额结果」：结果条包含"流水数、最大序号、**操作人时间**"。
  - requirement.md 的验收标准不要求页面显示操作人：AC-05 只要求"account 日志里记录了操作人"，这一项已满足。
- **实现**：追平拒绝条（`FIX-zh-CN-1440-C2-after-catchup-1.png`）和重建结果条（`*-P5-rebuild-corrected.png`）都只有时间，没有操作人。拒绝条也没有原型里的"本次未处理任何批次"。
- **判断**：与设计稿 P3 / P5 不一致，但不影响验收。要补的话，可以用当前会话操作人的显示名。

### 观察（不判为本需求缺陷）

- 浏览器走查偶发"登录成功后，下一次整页跳转回到登录页"：十多次运行中出现约 5 次，多为 en 组合，Nginx 日志里没有 401 / 429。脚本已加保护：遇到时重新登录一次并记录 `relogin`，最终一致态那一轮触发了 2 次。原因没有查明，建议另外排查会话恢复逻辑。
- 英文文案 "Recalculated 1 accounts" 单复数不分（第二轮已修复，见第 1b 节）。
- 侧栏底部 "Operations Console / Hide Navigation" 在 zh-CN 下仍是英文（属于应用外壳）。
- `ledger.account.dlq` 里有 4 条旧消息：AE001 的 2 条（10:36Z，早于本次）；ZQ344 序号 1、2 各 1 条（迁移前进入 DLQ，对应批次已由追平补记）。按要求没有重投，也没有清理。
- 带 PLATFORM_SERVICE_CHARGE 的人工调整只在 OE 账簿生成分录，FE 账簿是 0 张。这是 ledger 的记账口径，不在本需求范围。
- 修复前，我曾绕过 BFF 直接调 ledger（每次 9 批、共 66 次），补完了当时落后的 593 批；叫停消息是在那之后才收到的。修复后的验收全部基于重新造的落后数据（600 批、250 批和几次小批量），全部经 BFF / 页面执行。

## 7. 与原型的差异

| 原型 | 差异 |
|---|---|
| 全部 | 应用统一规则隐藏了页面大标题 h1（`.page-heading h1.is-trail-title`，用面包屑代替），所以"重建余额"出现在左侧；原型里在标题右侧 |
| P1 / P2 | 第 ② 段第三项"未发出 AccountPosted"改成了过账流数量（阶段 4 已接受）；第 ③ 段"最后消费"改成"卡住批次生成于"（technique 7.1 修订） |
| P3 | 错误码是通用的 `BUSINESS_CHECK_FAILED`（ledger 没有专用码，阶段 4 已核对），原型写的是 `LEDGER_RULE_NOT_FOUND`；缺少操作人（D-4）；部分追平、409、已追平三种结果条与原型一致 |
| P6 | 多了"起始序号 + 跳转"输入；面包屑里没有"批次浏览"；事实类型列偏窄，`MANUAL_ADJUSTMENT` 会折行 |
| P8 | 修复后整页错误与原型一致；残留问题是账簿下拉显示原始 ID（D-2 残留）。另外 account 不可用时三段都不可用，原型只画了 billing 不可用的情形 |
| P9 | 只有一条流（服务费所在 OE），没有 FE 自身流 |
| P4 / P5 / P7 / P10 / P11 | 一致（确认弹窗位置略偏上；P11 窄屏卡片布局一致） |

## 8. 全部数据写入与恢复

| 写入 | 恢复 / 去向 |
|---|---|
| ledger：Finance Entity 1 个、日历 2 本、OPEN 期间 2 个、账簿 4 本（迁移前删掉 2 本、后来重建 2 本） | 合成数据保留 |
| ledger 结构迁移（第 3 节） | 保留，与 `mor_ledger` 一致；回滚方式见第 3 节 |
| ledger 规则行 5301 / 5302：共删除 6 次（修复前接口 1 次、浏览器 2 次；修复后浏览器 3 次，其中 2 次脚本中途失败） | 每次都按导出的原行恢复（脚本中断的 2 次是立即手动恢复的）；最终 36 行，与原行逐字段一致 |
| billing：2,274 条 MANUAL_ADJUSTMENT（APPROVED） | 保留，已全部进入 account 和 ledger |
| account：用 SQL 改写 Owner 余额 6 次（每次 +5） | 每次都由重建恢复；收尾重建 changedAccountCount 为 0，余额 2,274.00 等于流水合计 |
| RabbitMQ：清空 `ledger.account.p0–p3` 共 8 次（只清 ledger 自己的分区队列，不碰 DLQ 和其他服务） | 这些批次都已由追平补记 |
| ledger 追平：修复前绕过 BFF 直接调私网 66 次（每次 9 批），其余全部经运营入口 | 最终已消费 2,274，与 account 一致 |
| Platform：只读角色 1 个、运营账号 1 个 | 保留（见第 2 节） |
| 服务启停：billing / account / ledger 多次 stop / start（AC-10、造落后、迁移） | 全部 UP；ledger 运行修复包 `523ff2a7c7fd50a4` |

## 9. 脚本与产物（`verification/stage5/`）

- **脚本**：
  - `t6lib.py`：公共函数
  - `t6_setup.py`：日历、期间、账簿
  - `t6_facts.py`：计费事实
  - `t6_lag.py`：造落后
  - `t6_rebuild.py`：AC-05 / 06
  - `t6_readonly.py`：AC-09 账号
  - `t6_segments.py`：AC-10 接口
  - `t6_concurrent.py`：AC-08 的 409
  - `t6_direct_catchup.py`：修复前绕开 D-3
  - `t6_rules.sh`：删除 / 恢复规则行
  - `t6_browser.mjs`：Playwright 走查，`GROUPS` 可选 base / readonly / rebuild / segments / catchup
  - `migration/`：迁移 SQL 与日志
- **截图** `screenshots/`，共 161 张，按前缀区分：
  - 无前缀：修复前的落后态
  - `OK-`：修复前的一致态
  - `D3-`：缺陷 D-3
  - `FIX-`：修复后的追平、规则拒绝、分段失败
  - `FIX409-`：409
  - `PARTIAL-`：部分追平和继续追平
  - `FINAL-`：最终一致态，四个组合
- **浏览器结果**：`t6-browser-result-*.json`、`t6-browser-*.log`。
