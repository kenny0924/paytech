# platform-support：多语言发布变更记录 · 交付记录

## 适用阶段

默认八阶段全部适用（2026-10-30 用户随阶段 1 一并确认）：

| 阶段 | 本需求内容 |
|---|---|
| 1 需求评审 | [requirement.md](./requirement.md) |
| 2 现状差异 | 写入口清单、现行摘要 / 差异算法细节、mor-test 数据核对 |
| 3 方案设计 | 列与新表定义、PDManer、DDL、基线脚本、算法与测试计划（不改页面，无 Pencil 画板） |
| 4 研发 | Platform Support 多语言模块 |
| 5 验证 | auto 测试、本机 e2e、与现行算法逐项比对 |
| 6 test 发布准备 | Platform 候选镜像、DDL、基线脚本、备份与回滚 |
| 7 test 部署 | mor-test 执行与验收（AC-01—AC-09） |
| 8 提交归档 | 提交与台账 |

## 确认记录

| 日期 | 阶段 | 内容 | 用户原意 |
|---|---|---|---|
| 2026-10-29 | 需求来源 | 预检 10 s 排查结论；否决读文件与全量快照 | 「这么读是不对的」；「方案一二我觉得都做一下」 |
| 2026-10-30 | 1 | 需求、范围、AC-01—AC-09、适用阶段；Q1 采用建议（一次性脚本为已有发布补全变更明细并初始化修订号） | 「按照建议执行」；获准进入阶段 2 |
| 2026-10-30 | 2 | 现状差异与影响分析（technique 第 1—3 节）；D2 调整为只做方案二（变更明细），不加修订号列 | 「可以，就方案二吧」；获准进入阶段 3 |
| 2026-10-30 | 3 | 方案与数据设计（technique 第 4—8 节、db/ddl.sql） | 「继续」；获准进入阶段 4（只改代码、模型与 SQL 文件，不执行环境 SQL） |
| 2026-10-30 | 4 | 研发结果 `69f87bbf`（worktree）与门禁复验 69/69 | 「继续」；获准进入阶段 5（只动本机环境，不碰 mor-test） |
| 2026-10-30 | 5 | 本机验收 12/12、补录 24/24、浏览器 1440 / 390 × 中英；联合查询先发布 mor-test，本需求以其上线源码为基线 | 「继续」；获准进入阶段 6（只准备，mor-test 只读） |
| 2026-10-30 | 6 | 发布包、mor-test 只读试算与修复前读数；联合查询 b 版上线后重建候选 | 「继续，写入验收也执行」；获准阶段 7（含改译文并还原、client-console 相同内容发布） |
| 2026-10-30 | 7 | mor-test 部署与验收（AC-06 最大差异 2/13 次略超 1 s 作为遗留） | 「继续」；获准阶段 8（提交，不推送） |

## 当前阶段与下一确认点

阶段 4 已完成，待确认：执行代理在独立 worktree（`.claude/worktrees/agent-a2e0fade831347934`，分支 `worktree-agent-a2e0fade831347934`）提交 `69f87bbf`（未合入 main、未推送），33 个文件 +1415 / −230。

门禁复验（主会话，2026-10-30）：同一 worktree 运行多语言 HTTP 与模块测试 15 个类 69 个测试，0 失败 0 错误；核对还原 SQL、完整性检查、发布事务内写入、读包端口删除与 PDManer 新实体。本机只在 `mor-local-mysql-1` 的 Platform auto 库执行了新 DDL（可重跑已验证）；未触碰 mor-test。

与设计的一处调整：变化行主键改为在 Repository 每批（≤ 1000 行）写入前分配，避免大基线一次性生成上万个 ID 超出 Snowflake 单毫秒 4096 序列；`test_largeBaseline`（4205 行）覆盖。

## 阶段 5 进展（2026-10-30）

- **补录脚本**：[release/scripts/backfill_release_changes.py](./release/scripts/backfill_release_changes.py)（`plan` 生成可审阅 SQL，`verify` 逐发布核对）。本机 e2e（`mor_platform_v2e2e`）只读试算：2 个应用 24 个发布、124 个语言包（去重）全部通过 SHA-256 校验，约 4 s；在临时副本库（复制 4 张表 + 执行 DDL，验证后删除）执行生成的 SQL 约 4 s，`verify` 24/24 一致，重复执行行数不变（25,791 = `change_count` 合计）。
- **合并构建**：本机 e2e 的 Platform 由联合查询会话（design-discuss-19）管理，其源码 `Code-MoR-jq-m` 未提交。在自建 worktree `Code-MoR-i18nchg` 中以其源码为本地临时提交 `ad532fb8`，再挑选 `69f87bbf` 得 `87757efb`（仅 `PlatformDataCleaner` 表清单冲突，已合并保留两边新表；PDManer 89 个实体）。多语言 + 数据查询测试 22 个类 143 个测试 0 失败 0 错误；Platform 可执行 JAR 已打包。
- **环境事件**：期间本机 Docker 守护进程卡死，经用户同意重启 Docker Desktop；`mor-local-mysql-1`、`mor-test-mysql-1` 因 containerd 残留任务目录无法启动，经用户同意删除容器后按原 compose 只重建 mysql（同镜像、同命名卷，数据核对一致），其余容器按原样 `docker start`，`mor-test-vault-service-1` 手动重启后健康。中断的测试在 Platform auto 库留下夹具残留（独占表、4 个租户、2 个 API 凭证与 59 条授权范围，均为夹具时间 2026-09-17 12:00:00.123456），已清除后重跑。
- **本机 e2e 换装（联合查询会话完成后）**：`mor_platform_v2e2e` 执行 DDL；补录 `plan` → 执行 → `verify` 24/24 一致，重跑 24 个全部跳过；Platform 换为合并版 JAR（旧 JAR 备份 `*.pre-i18nchg`，以原进程 argv/env 重启，含 `DATAQUERY_HTTP_CALLERS`），就绪 UP；ops-bff、Vite 未动。
- **接口验收** [verification/local-e2e/e2e_i18nchg.py](./verification/local-e2e/e2e_i18nchg.py)（结果 [e2e-result.json](./verification/local-e2e/e2e-result.json)，独立基准 [expected_summary.py](./verification/local-e2e/expected_summary.py) 按现行算法由库与线上语言包计算）：12/12 通过。
  - AC-02 预检中位数：operations-console 479 ms（5 次 1544 / 1086 / 479 / 417 / 470，首次冷启动）、client-console 126 ms；mor-test 修复前 5.6—8 s。
  - AC-03 摘要与独立基准一致；改一条 zh-CN 译文、另一条改了又改回 → 只计 1 条（`access.accounts.action.assignRoles`）；还原后回到无变化（两条译文已还原原文）。
  - AC-06 差异：#19 默认基准 604—839 ms（430 条）、`baseReleaseNo=1` 361—667 ms（4528 / 18 / 22）、client #5 107—137 ms。
  - AC-01 预检、差异、改译文期间存储凭据解密（`FILE_STORAGE_ACCESS` REVEAL）0 次。
  - AC-05 client-console 发布 #6（与 #5 内容相同）：READY 并上线，`change_count = 0`，无变化行，差异为空；发布后 `verify` 25/25 一致（含 Platform 运行期写入的 #6）。
- **浏览器** [browser_i18nchg.mjs](./verification/local-e2e/browser_i18nchg.mjs)（截图 `verification/local-e2e/screens/`）：1440 / 390 × zh-CN / en，发布记录详情、切回确认弹窗（以线上为基准的差异「215 个新 key 不在 #18」= 430 条 / 2 种语言）、发布弹窗（「与 #19 相比 · 0 处变化」）均正常，无横向溢出；应用报错只有既有的 antd `Space direction` 弃用警告。语言包经 MinIO 19400 在 Vite 18316 下跨域失败、界面回退内置英文，为本机既有问题，与本需求无关。
- **遗留**：本机 e2e 当前运行含本提交的合并版 Platform。若之后有会话换回不含本提交的 Platform 并发布新版本，该版本不会写明细（`change_count` 为 NULL）；再换回新 Platform 时预检会报「发布变更明细缺失」，重新运行补录脚本即可补齐（脚本幂等）。

## 阶段 6 test 发布准备（2026-10-30）

发布手册与全部读数见 [release/runbook.md](./release/runbook.md)。

- **基线**：联合查询会话 07:12Z 完成 mor-test 发布，现网 Platform `9444b483`（jq-20261030a），源码 = main `6eaf1bd3` + `Code-MoR-jq-rel` 未提交的 236 个文件。在 `Code-MoR-i18nrel` 以其为临时提交 `899c07d8` 构建，与现网 JAR 83,969 个条目 0 差异；再挑选 `69f87bbf` 得 `15890412`（冲突同阶段 5）。首次构建曾因本机 `~/.m3` 中 10-22 的旧 `paytech-messaging` 出现 1 处差异，改为 reactor `-am` 构建后消除，未改 `~/.m3`。
- **候选镜像** `a90a8829`（`i18nchg-20261030a`）：现网镜像之上只替换应用 JAR；相对现网 23 个条目差异全部属于本需求；多语言模块与阶段 5 验收 JAR 逐字节相同。候选 / 回滚 compose 渲染核对：候选只差 image，回滚与现网相同。
- **补录**：mor-test 只读试算 33 个发布、168 个语言包 SHA-256 全部一致、26,606 行；复制 mor-test 4 张表到本机临时库演练 DDL（两次）→ 补录 → `verify` 33/33 → 重复执行不变 → 回滚 SQL，临时库已删除。
- **修复前读数（mor-test 旧 Platform，只读）**：ops 预检首轮 504、之后 4.5—7.0 s；差异 2.4—8.9 s；测量期间存储凭据解密 958 次；浏览器发布弹窗预检 5.6—9.8 s，390 宽度出现 504。预检摘要与独立基准一致。
- **验收脚本**：`e2e_i18nchg.py`、`expected_summary.py`、`browser_i18nchg.mjs` 改为可经环境变量指向 mor-test，失败（504）记入耗时而不中断；`I18NCHG_WRITE=0` 为只读模式。
- **阶段 7 需要的授权**：mor-test `mor_platform` 备份、DDL、补录 SQL（root）；换 Platform 镜像（约 60 s 不可用）；写入验收（改并还原 2 条 zh-CN 译文，以相同内容发布 client-console #9）。

- **按 b 版重建**：联合查询随后发布 jq-20261030b（Platform `c82b6f34`、ops 多语言 #26），候选 `a90a8829` 作废。以 `Code-MoR-jq-rel` b 版 241 个文件为临时提交 `7c7d8110`，`clean package` 构建与现网 JAR 83,973 个条目 0 差异（首次未 clean 时残留 1 个本需求 Mapper XML，clean 后消除）；挑选 `69f87bbf` 得 `c633fd0e`，候选 `a3003ef6`（`i18nchg-20261030b`，JAR `29d7e804…`）相对现网仍是同样 23 个条目；回滚改为 `c82b6f34`；compose 渲染核对同前。

## 阶段 7 test 部署与验收（2026-10-30，08:55—09:05Z）

证据目录 `~/Desktop/Workspace/Code-MoR-backups/i18nchg-20261030/mortest-20261030T085512Z/`（备份、补录 SQL、plan / verify 输出，不入库）；验收结果 [verification/mor-test/](./verification/mor-test/)。

| # | 步骤 | 结果 |
|---|---|---|
| ① | 现网读数 | Platform `c82b6f34` healthy；ops 26 个发布、client 8 个；新表不存在 |
| ② | 通知 | 联合查询会话确认窗口内不发布 Platform、ops 多语言与 ops-web |
| ③ | 备份 | `mor_platform` 全库 18.7 MB，`Dump completed` |
| ④ | DDL | 表与列创建；34 个发布 `change_count` 全部 NULL |
| ⑤ | 补录 | 相对阶段 6 试算只多 ops #26（14 行 = 联合查询 7 个新 key × 2 种语言）；执行约 1 s，26,620 行（主键无重复），`verify` 34/34 一致、未补录 0 |
| ⑥ | 换 Platform | 08:56:33Z 起，镜像 `a3003ef6` healthy，启动 27 s；之后日志 ERROR 0 |
| ⑦ | 补缺 | 重新 `plan` 0 条待写入 |
| ⑧ | 只读验收 | 8/8（[after-readonly.json](./verification/mor-test/after-readonly.json)） |
| ⑨ | 写入验收 | 11/12（[after-write.json](./verification/mor-test/after-write.json)，唯一不达标见下）；client-console #9 READY 上线、`change_count=0`、无变化行；`verify` 35/35 |
| ⑩ | 浏览器 | 1440 / 390 × zh-CN / en：发布记录、切回确认、发布弹窗均正常，无横向溢出，应用报错 0（[screens/](./verification/mor-test/screens/)） |

修复前后（同一脚本，修复前见 [before-old-platform.json](./verification/mor-test/before-old-platform.json)）：

| 指标 | 修复前（旧 Platform） | 修复后 |
|---|---|---|
| AC-02 ops 预检 5 次 | 首轮 504；4.5—7.0 s | 中位数 1.05 s（首轮冷启动）/ 0.50 s |
| client 预检 | 1.3—1.7 s | 0.07—0.20 s |
| AC-06 差异 #19（122 条） | 6.8—8.9 s | 0.33—0.58 s |
| 差异 #19 对 #1（19,618 条） | 3.9—5.0 s | 中位数 0.53 s，13 次中 2 次 1.1—1.2 s |
| 差异 client #5 | 2.4—4.1 s | 0.09—0.31 s |
| AC-01 存储凭据解密 | 958 次 | 0 次 |
| 浏览器弹窗出结果 | 5.6—9.8 s，390 宽度出现 504 | 切回确认 0.57—0.94 s，发布弹窗 0.65—1.83 s |
| 差异条数 | 122 / 19,618 / 2 | 相同 |

- **AC-03**：改一条 zh-CN 译文（`i18n-admin.activate.after`）、另一条改了又改回 → 只计 1 条，与独立基准一致；还原后预检回到无变化（两条译文已还原原文）。
- **AC-05 / AC-09**：client-console #9 与 #8 内容相同，变化 0 条、无变化行、差异为空。
- **不达标项**：AC-06「单次 ≤ 1 s」在最大差异（#19 对 #1，响应 2.65 MB JSON，几乎是全部文案）上 13 次中有 2 次 1.1—1.2 s；耗时主要在大响应经 Platform → BFF → Gateway 传输。常规差异与切回确认均 < 1 s，不再出现 504。建议作为遗留接受，不在本需求内优化。
- **真实发布核对**：联合查询 c 版在新 Platform 上发布 ops #27（新增 1 个 key × 2 种语言）：`change_count=2`、2 行 ADDED，与实际改动一致；`verify` 36/36 一致、未补录 0。
- **对其他会话的影响**：之后任何 Platform 构建都须包含 `69f87bbf`；不含它的 Platform 发布不会写明细，再换回时预检报「发布变更明细缺失」，重跑补录脚本即可（已告知联合查询会话）。

- 浏览器补充：截图脚本在 0.85 s 截图时弹窗仍在「正在检查」，另用 [browser_settle.mjs](./verification/mor-test/browser_settle.mjs) 等到出结果：切回确认 0.57—0.94 s、发布弹窗 0.65—1.83 s（`*-settled.png`）。

## 阶段 8 提交归档（2026-10-30）

- **代码**：`69f87bbf` 在 main `6eaf1bd3` 上挑选为 `1c8341a2`（无冲突，平台模块含测试编译通过；多语言代码与 mor-test 运行 JAR 逐字节相同），main 快进到 `1c8341a2`。
- **文档**：本目录（需求、方案、交付、发布手册、补录脚本、验收脚本与结果、截图）、`requirements/README.md` 登记行、`agent-docs/CURRENT_IMPLEMENTATION.md` 新增本需求一节，单独提交（私有 index，只含本需求改动）。备份、补录 SQL 与试算 SQL 不入库。
- **未做**：推送；临时 worktree `Code-MoR-i18nchg`、`Code-MoR-i18nrel`（a3003ef6 的构建源，限额档位会话正在引用）与 `Code-MoR-i18nmain` 暂保留。

当前阶段：8 已完成。
