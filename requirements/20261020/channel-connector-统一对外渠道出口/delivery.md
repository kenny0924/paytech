# channel-connector：统一对外渠道出口 交付记录

## 适用阶段

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-20，"按建议，继续"）：范围 A–G；Q1–Q8 全部按建议 |
| 2–3. 分析与设计 | 已确认（2026-10-20，"按建议，继续"）：technique.md 全部内容；S1–S6 按建议 |
| 4. 研发 | 第一段完成并合入主工作区（2026-10-21，未提交）：隔离库需库测试 channel-connector 330/330、Platform 734/734、card 7/7；合入后无库测试全部通过（见验证记录）；报告 [tasks/P1-report.md](tasks/P1-report.md)。第二段：2026-10-22 页面画板 12 张待确认（`operations-console-web/docs/design.pen`「渠道 · 第二段 01–12」，x 0–9140、y 80680–82860），2026-10-22 画板确认，第二段研发开工：worktree `Code-MoR-ccu2`（基于 `281a217d`），任务卡 [P2.md](tasks/P2.md)（后端 + BFF + 注册块 129）与 [P2-web.md](tasks/P2-web.md)（页面）由两个 Opus 5.5 agent 并行执行。2026-10-22 第二段研发完成，同日合入主工作区（512 个文件直接覆盖，9 个与文件存储会话同改的文件三方合并，保留其块 130、存储管理页与 3 张存储表），并同步当前台账、Payment 物理决策与 Platform 数据库文档：报告 [tasks/P2-report.md](tasks/P2-report.md)、[tasks/P2-web-report.md](tasks/P2-web-report.md)；隔离库需库测试 channel-connector 375/375、card 9/9、Platform 全过（通知两表预存漂移在隔离库按完整 Schema 重建后 210/210）；BFF 2025/2025、arch-rules 68/68、Web 1251/1251 + tsc + build；迁移 SQL 在本机 e2e 副本（加合成 MATCHED / INTEGRATION_ONLY / CHANNEL_ONLY 行）演练通过，迁移后结构含注释与 Schema 文件逐项一致 |
| 5. 本机验证 | 完成（2026-10-21，任务卡 [tasks/P1-stage5.md](tasks/P1-stage5.md)，报告 [verification/stage5-report.md](verification/stage5-report.md)）：旧版本基线与新版本对照一致（候选查询、REFUND 到合成 PayerMax 验签有效、两类 NOT_SENT、沙箱用例 PASSED、34 个管理 GET 逐字段一致）；迁移演练 10 表行数 / CHECKSUM / 15 列 SHA-256 一致；新版本真实读卡 PAY SUCCESS（票据已用、重复读卡 400、Vault Reveal 调用方 channel-connector-service）。本机 e2e 现运行新版本（旧 JAR 保留为 `*.pre-ccu`）；第二段完成（2026-10-22，任务卡 [tasks/P2-stage5.md](tasks/P2-stage5.md)，报告 [verification/stage5p2-report.md](verification/stage5p2-report.md)）：`*_v2e2e` 执行第二段迁移（precheck 0 行、verify 全部符合、定义 / 脚本 / 流程 / 运行快照 SHA-256 前后一致）与块 129 注册加激活（本机块 92 已激活；块 71 未重放），换 card / channel-connector / platform / ops-bff 后回归与第一段基线一致（链路 5 项、真实读卡 PAY、沙箱、44 个管理 GET；差异仅接入方名改渠道名、`entityNo` 非必填）；新能力 43 项通过（通用入口、调用授权 403 / 409 / 422、入口隔离、出站地址 422 / 409、暂停、分舱 503 `CHANNEL_EXECUTION_BUSY` 时支付不受影响、导航）；发现并修复 2 个缺陷：D-1 出站地址变更摘要前后不可比（`endpointsDigest`，新增测试，cc 376/376），D-2 渠道列表 4 个并发 GET 超过 Gateway 每人并发 2 必定 429（计数改串行，新增单测），本机复验通过；另删除第二段重新带回的列表页底提示、修正一处 Vault 调用方文案 |
| 6. test 发布准备 | 完成（2026-10-21，[release/release.md](release/release.md)）：发布清单 `release/`，以 mor-test 当前运行源码为基线叠加本需求 diff 构建 `ccu-20261021` 镜像；只读核对，不改环境 |
| 7. test 部署与验收 | 完成（2026-10-21 10:09–10:36Z，[release.md 第 13 节](release/release.md#13-执行记录阶段-7-填写)）：mor-test 四服务切换为 `ccu-20261021`，10 表迁入 `mor_channel_connector` 并核对一致，Platform 删 10 表（57→47）；A 277 项 0 差异、B 7 例逐步一致、C1 / D 一致、C2 Vault 调用方切换（未真正出站，B 已证明出站）；冒烟 14/14；XXL 任务 22 已登记 |
| 8. 提交 | 第一段已本地提交（2026-10-21，私有 index；提交号见 `git log`），未推送；第二段未开工 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-20 | 讨论 | "我其实想统一管理对外渠道的请求，这样会更安全一点"；"还是放 channel 系统好点……只是说分支付渠道还是非支付渠道" | 方向：channel-connector 作为统一对外出口，渠道分两类 |
| 2026-10-20 | 讨论 | "认可，不需要写 discuss，直接出需求" | 登记需求目录，交付阶段 1 评审稿 |
| 2026-10-20 | 1 | "按建议，继续" | 阶段 1 确认（Q1 服务渠道 `SERVICE`、Q2 扩大 Channel 术语、Q3 合并为一张渠道表、Q4 复用技术账号、Q5 授权存表、Q6 网络层另立、Q7 SES/S3/Webhook 登记例外、Q8 研发分两段）；写决定记录并同步路由（AGENTS.md、文档治理、四条旧决定后继标记）；交付阶段 2–3 评审稿 technique.md。不含代码、SQL、部署 |
| 2026-10-20 | 2–3 | "按建议，继续" | 阶段 2–3 确认（S1–S6 按建议）；进入阶段 4 第一段（等价迁移）研发：Opus 5.5 agent 在独立 worktree 执行，主会话把关。本机 SQL（含隔离测试库）另行请求授权；不含 mor-test、提交、推送 |
| 2026-10-21 | 4 | "可以" | 接受 `channel-connector-api` 依赖 Jackson 登记为 R2 已知偏差，接受 P1 报告偏差 D1–D12；授权本机建隔离测试库 `mor_channel_connector_ccu` / `mor_platform_ccu` / `mor_card_ccu` 运行需库测试，用后删除；通过后合入主工作区。不含本机端到端、mor-test、提交 |
| 2026-10-21 | 5 | "继续，做阶段 5 本机端到端" | 本机端到端与数据迁移演练：本机 SQL 限 `mor_platform_v2e2e` / `mor_channel_connector_v2e2e` 备份与迁移、临时库删表语法试跑、本机 auto 库 `mor_channel_connector` 增量 DDL；停启本机 e2e 的 card / channel-connector / platform / ops-bff。不含 mor-test、提交 |
| 2026-10-21 | 5→6 | "继续" | 阶段 5 结果确认；进入阶段 6 发布准备：盘点 mor-test、建发布 worktree、构建带显式标签的镜像、写清单；只读核对，不停启容器、不写 SQL |
| 2026-10-21 | 6→7（预授权） | "按建议，继续"（发布清单尚未交付时） | 阶段 6 清单交付后由主会话审核；仅含已建议事项（列注释不改、card 先行的顺序、验收后备份再删表）时直接进入阶段 7 执行；出现新决策、计划外写入、镜像基线冲突或其他会话占用时先停下请示 |
| 2026-10-21 | 6→7 | 清单审核后问答：XXL 兜底"登记"；验收写入"做 C2，不做 C3" | 阶段 6 清单（[release/release.md](release/release.md)）确认，进入阶段 7：基线 main `1b60ae43` + 本需求 diff，镜像 `ccu-20261021`（card `aee93799`、channel-connector `1571ce18`、ops-bff `4839c249`、platform `2d40c1ba`）；SQL 10 / 20 / 30（DDL、冻结核对、复制核对）；验收 A、B、C1、D、C2；为 channel-connector 登记 XXL `compensationEventScan`；列注释不改；`CARD_FINGERPRINT_KEY` 渲染差异接受（解码一致）；验收通过后再备份并部署 platform、执行 40 删表 |
| 2026-10-21 | 8 | "部署到test了吗"；"提交 吧" | 阶段 7 结果确认；本地提交本需求文件（代码与 mor-test 运行镜像源码逐字节一致），不推送 |
| 2026-10-22 | 8→第二段 | "可以，继续吧"；"可以" | 清理两个临时 worktree（核对改动全部含于 `ecab136c` 后删除；`ccu-20261021` 镜像标签是 mor-test 运行容器的唯一引用，保留）；推送由用户自行执行；第二段先出 Pencil 画板，确认前不改代码、不写 SQL |
| 2026-10-22 | 第二段画板 | "继续" | 画板 01–12 与 4 个设计点按建议确认（服务渠道凭据复用技术账号、授权只停用不删除、激活只要求出站地址、调用方名按格式校验）；进入第二段研发，本机隔离库 `*_ccu2` 需库测试沿用第一段同类授权；不含 mor-test、提交 |
| 2026-10-22 | 第二段研发→合入与阶段 5 | "可以" | 第二段合入主工作区并同步三份文档；进入第二段阶段 5 本机端到端（`*_v2e2e` 执行第二段迁移与块 129 注册 SQL，换新版 card / channel-connector / ops-bff / ops-web 并回归）；不含 mor-test、提交 |

## SQL 执行记录

| 日期 | 环境 | 内容 | 结果 |
|---|---|---|---|
| 2026-10-21 | 本机 13306 | 按 P1 报告第 6 节用 worktree 测试 DDL 建隔离库 `mor_channel_connector_ccu` / `mor_platform_ccu`（去掉 `DROP DATABASE` 行）/ `mor_card_ccu` | 表数 28 / 47 / 8；共享 `mor_*` 库未改动 |
| 2026-10-21 03:21–03:22Z | 本机 13306 | 阶段 5 迁移演练：备份 → `mor_channel_connector_v2e2e` 执行 `2026-10-20-protocol-tables.sql` → `mysqldump --no-create-info`（另加 `--single-transaction --set-gtid-purged=OFF --no-tablespaces`）导出 `mor_platform_v2e2e` 10 表并导入 → 临时库 `mor_ccu_stage5_droptrial` 试跑 `drop-integration.sql` 后删除 → 本机 auto 库 `mor_channel_connector` 执行同一增量 DDL（无数据）；明细 [verification/stage5-sql-log.txt](verification/stage5-sql-log.txt) | 10 表行数与 CHECKSUM 一致，15 个 JSON / 脚本列逐行 SHA-256 一致；`mor_platform_v2e2e` 原表保留；备份 `local-environment/e2e/backup/20261021T032138Z-ccu-stage5/` |
| 2026-10-21 03:29Z | 本机 13306 | 主会话在空库 `mor_card_v2e2e` 执行 `card-schema.sql`（0 → 8 表），为补验真实读卡 PAY | 完成 |
| 2026-10-21 | 本机 13306 | `mor_platform_ccu` 中按 `platform-support-schema.sql` 重建 `t_notification_attempt`、`t_notification_provider`（Platform 测试 DDL 在 4ab205e0 已缺 SES 事件拉取 9 列，属预存漂移，非本需求引入） | Platform auto 734/734 通过 |
| 2026-10-21 10:20–10:35Z | mor-test 13306 | 阶段 7：备份 → `mor_channel_connector` 执行 `10-channel-connector-ddl.sql`（18→28）→ mysqldump 复制 `mor_platform` 10 表数据并 `30` 核对一致 → 删表前再备份 → `mor_platform` 执行 `40-platform-drop-integration.sql`（57→47）→ 按 ID 清理 C2 验收渠道数据（channel_connector_app）；XXL 新增任务 22 | 备份 `~/Code-MoR-backups/20261021T101005Z-ccu-outbound-mor-test/`（含 `predrop/`）；明细见 release.md 第 13 节 |

## 验证记录

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-10-22 | 第二段隔离库（`*_ccu2`）与无库测试 | 首轮 channel-connector 373/375：Repository 仍按旧唯一索引名识别重复键（主会话改为 `uk_channel_*`）；通用入口注入 `entityNo:null` 触发 422（agent 改为不注入）；修复后 375/375。主会话另修：注册 SQL 3 处 17 位 ID、迁移与注册 SQL 缺 `SET NAMES utf8mb4` |
| 2026-10-21 | Platform auto 全量（隔离库） | 734/734 |
| 2026-10-21 | card `ChannelCardMaterialRevealTest`（隔离库；修正过渡场景读卡次数断言为增量） | 7/7 |
| 2026-10-21 | channel-connector 全量（隔离库，clean） | 首轮 330 中 40 失败：管理接口 404→400（迁移丢失 NOT_FOUND→404 映射）、`VerifyCaseManagementTest` 500（`VerifySupport` 未捕获 `StandardApiNotSentException`）、进程内执行读卡失败（合成票据不符合 card 合同 43 位格式）、签名渠道 2 例（漏迁测试固定时钟）；修复后 330/330（报告“需库测试修复”节） |
| 2026-10-21 | 主工作区合入后 | 9 模块 test-compile；arch-rules 63/63；channel-connector 架构与领域 53/53；Platform 架构 1/1；ops-bff 1984/1984（含其他会话新增用例） |

合入方式：worktree 555 个新增或修改文件、449 个删除文件直接应用；与对账会话共用的 5 个文件三方合并（`TableOwnershipAutoTest` 两侧断言均保留，`generate-table-ownership.mjs` 去掉重复登记的 `t_channel_config_change`，`table-ownership.yaml` 重新生成并 `--check` 通过）。隔离库 `mor_channel_connector_ccu` / `mor_platform_ccu` / `mor_card_ccu` 已删除。

遗留（非本需求）：Platform 测试 `init/ddl.sql` 与 `platform-support-schema.sql` 在通知两表上不一致，需通知能力 Owner 补齐。
