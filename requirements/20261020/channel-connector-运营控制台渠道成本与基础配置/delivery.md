# channel-connector 运营控制台：渠道成本方案与渠道基础配置 交付记录

## 适用阶段

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-20，"按建议"）：A 线渠道成本方案控制台、B 线渠道基础配置管理、C 共同项；Q1–Q6 全部按建议 |
| 2–3. 分析与设计 | 已确认（2026-10-20，"按方案"）：technique.md 全部内容含第 9 节偏差、新表 `t_channel_config_change` 授权、Pencil 15 画板 |
| 4. 研发 | A 线先行，B 线其后；Opus 5.5 agent 执行，主会话设计与把关 |
| 5. 本机验证 | channel-connector / BFF / Web 自动测试与构建；本机 e2e 以控制台配置替代种子 SQL |
| 6–7. mor-test | Vault 策略 DML、镜像替换、Platform 注册 DML、页面回归 |
| 8. 提交 | 私有索引本地提交 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-20 | 评估 | "先做前两个吧，路由策略暂时不考虑" | 登记需求目录，交付阶段 1 评审稿 |
| 2026-10-20 | 1 | "按建议" | 阶段 1 确认（Q1–Q6 按建议）；交付阶段 2–3 评审稿 technique.md |
| 2026-10-20 | 2–3 | "按方案" | 确认方案、接口合同、五项偏差与新表；进入阶段 4（CC-A 先行，CC-B 其后，REG 由主会话；BFF/Web 等 I1 合入）。不含 SQL 应用到共享环境、提交、部署 |
| 2026-10-20 | 4 | "CC-B 可以并行启动吗" | CC-A / CC-B 并行 |
| 2026-10-20 | 6→7 | "继续" | 阶段 6 清单确认；执行阶段 7：mor-test 备份、构建、SQL、替换四容器、启用、验收、清理、记录（环境变更由主会话执行） |
| 2026-10-20 | 5→6 | "继续" | 阶段 5 确认；进入阶段 6 mor-test 发布准备（只产出清单、SQL、恢复方案与验收步骤，不部署） |
| 2026-10-20 | 4→5 | "可以" | 阶段 4 确认；进入阶段 5 本机综合验证（含本机 SQL 执行：块 92 / 激活 / Vault 策略；不含提交与 mor-test） |
| 2026-10-20 | 4 | 选择「Platform 加内部查询接口（推荐）」 | 阶段 1 不做 Platform 接口变更的例外：新增 `IntegrationLookupApi` 内部免会话查询，PLT 线启动；mor-test 发布范围增加 Platform 镜像 |

## SQL 执行记录

| 时间（UTC） | 库 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-20 03:54 | 本机 `mor_channel_connector` | technique.md 第 4 节 DDL（研发线用非 utf8mb4 客户端执行，注释乱码） | 建表，0 行 |
| 2026-10-20 03:59 | 本机 `mor_channel_connector` | DROP 后按 CC-A `db/channel-connector/ddl.sql` 以 `--default-character-set=utf8mb4` 重建（主会话） | 18 张表，注释正常，0 行 |
| 2026-10-20 | 本机 `mor_platform_e2e`、`mor_vault` | 块 92 注册 DML、Vault PROTECT DML 以 ROLLBACK 事务做语法与约束校验 | 通过，未落数据 |
| 2026-10-20 07:24:42Z | mor-test `mor_channel_connector`（root） | `db/channel-connector/ddl.sql` | 17 → 18 张表 |
| 2026-10-20 07:24:55Z | mor-test `mor_vault`（root） | `db/vault/dml.sql`（先 ROLLBACK 试跑） | 规则 9100000000000720 ACTIVE，策略版本 16 → 17 |
| 2026-10-20 07:25:12Z | mor-test `mor_platform`（root） | `db/platform-support-service/registration-dml.sql`（先 ROLLBACK 试跑） | 7 / 7 / 9 / 31 / 138 / 24 / 186，休眠 |
| 2026-10-20 07:28:02Z | mor-test `mor_platform`（root） | `release/test-activate.sql`（先 ROLLBACK 试跑） | 31 / 9 / 7 / 1，PLATFORM_ADMIN 35 → 36 |

## 阶段 4 交付报告（2026-10-20）

**范围完成情况（对照 technique.md 第 10 节）：** CC-A、CC-B、PLT、REG、BW 五条线全部完成；代码未提交。

| 线 | 位置 | 内容 | 验证 |
|---|---|---|---|
| CC-A + CC-B（channel-connector） | 主工作区（152 个文件） | `t_channel_config_change` 表（PDManer 18 表、schema、增量 SQL、测试 ddl）；成本方案列表 / 作废 / 渠道存在校验；变更记录查询；渠道 / 账号 / MID / 能力四组管理合同、13 个写 Processor、Vault Protect、Platform 接入方检查；版本主键 / 计数 / 外部 MID 筛选补充 | 主工作区 channel-connector-test **187 例 0 失败**；arch-rules 61 + 3 例 0 失败；PDManer `--check` 18 表最新 |
| PLT（Platform） | 主工作区（api 2 文件 + app/integration/test 7 文件） | `IntegrationLookupApi`：`GET /standard-apis/v1/integrations/{code}/existence`，鉴权同标准 API 执行，无会话 | worktree 内定向 11 类 37 例 0 失败；主工作区与全量回归待本机 `mor_platform` 空闲（其他会话仍在跑 Platform 测试） |
| REG（主会话） | 本目录 `db/` | 块 92（权限 7、授权 7、资源 9、路由 31、Header 138、Query 24、限流 186，共 402 行，休眠）；Vault PROTECT 规则 DML；本机 `policy.local.json` 已加规则 | 两份 SQL 在本机库以回滚事务校验通过 |
| BW（BFF + Web + Platform 注册） | worktree `.claude/worktrees/bw-channel`（基于 `commercial/second-slice` 4d8f4ec3，46 个文件） | BFF 6 个客户端、31 条路由、版本对比聚合；Web 三页（渠道与账号、MID 与能力、渠道成本）与 15 张画板对应；`console-resource-capabilities.json` 加 3 页；块 92 追加到 `platform-support-initial-data.sql` | BFF 测试模块 **1710 例 0 失败**（新增 76）；Web **1135 例 0 失败** + build 通过；31 条路由与块 92 逐条一致 |

**与确认方案的偏差（研发期决定，均已写回 technique.md）：** 锁统一 `tryLocks`（第 6 节）；Vault 幂等键分隔符改点（第 5 节）；Platform 查询编码非法 400（第 9 节第 6 条）；路由 31 条（第 8 节）；成本作废时 `effective_to = effective_from`（CC-A 报告）。

**第 11 节核对：** 各线报告（`tasks/CC-A-report.md`、`CC-B-report.md`、`PLT-report.md`、`BW-report.md`）逐项对应真实类；主会话抽查写入口的事务外 Protect / Platform 查询、条件更新行数、凭据不入日志。

**遗留（进入阶段 5–7 处理或登记）：**
1. 主工作区 Platform 定向测试与全量回归未跑（本机库被占用）。
2. BW 须在商务第二切片合入 main 后重新应用到新 main 再验证（跨会话约束）；冲突热点见 BW 报告第 8 节。
3. 部署前置：内部 Token 开启的环境要把 `channel-connector-service` 加进 Platform `internal-token.issuer.callers`；channel-connector 入站白名单需接受 `operations-console-bff`。
4. 旧 Vault Token 不回收；OE 只校验格式；`console-resource-capabilities.json` 原本缺 9 个既有页面（非本需求）。
5. `payment.pdma.json` 过期与场景文档引用 `adapter_code` 的 2 个既有 PDManer 测试失败（非本需求引入）。

**未执行：** 任何 git 提交、共享环境 SQL、部署；本机只建了测试库新表。

**阶段 5 计划：** 等商务合入 main → 把 BW 差异应用到主工作区 → 主工作区全 Reactor：channel-connector、platform-support（含 PLT 定向与全量）、ops-bff、ops-web、arch-rules、`generate-channel-connector-model --check`；本机 e2e 用控制台接口登记 PayerMax mock 渠道 / 账号（真实 Vault Protect）/ MID / 能力，候选查询与执行走到 Platform，退役种子 SQL；本机执行块 92 + 激活 SQL 与 Vault 策略，经 Nginx 运营入口做 API 与浏览器验收。

## 阶段 5 交付报告（2026-10-20）

主工作区基线：main `b04b77f1`（商务第二切片 + 财务三项已合入）+ 本需求未提交改动。

| 检查 | 结果 |
|---|---|
| channel-connector-test | 187 例 0 失败 |
| arch-rules + reactor | 61 + 3 例 0 失败 |
| platform-support-test：PLT 定向 11 类 / 全量 auto | 37 / 37；884 / 884 |
| ops-bff 测试模块（含财务合并后） | 1844 例 0 失败 |
| ops-web | 1155 例 0 失败；build 通过 |
| PDManer `generate-channel-connector-model --check` | 18 表，最新 |
| requirements-site | 127 / 137，同样 10 例在干净 HEAD 失败，非本需求引入 |
| 本机 API 验收（31 条路由，经 Nginx 运营入口） | 103 / 103 |
| 本机全链路（控制台登记渠道 / 账号（真实 Vault Protect）/ MID / 能力 → 候选查询 → 渠道执行） | 5 / 5：候选返回控制台登记的 MID；REFUND 经 Platform 标准 API 到达 payermax-mock 且签名有效（凭据来自控制台写入的 Vault Token）；PAY 到达 Platform 后因本机无 card-service 停止 |
| 本机浏览器验收 | 35 / 35，15 张截图逐一对应画板 01–15（`assets/stage5/`） |
| 产品缺陷 | 0 |

**本机 SQL 执行（详见 `tasks/VER-report.md`）：** `mor_channel_connector_v2e2e` 建表（去掉文件内 `USE` 行执行）；`mor_vault` PROTECT 规则 + 策略变更（版本 21）；`mor_platform_v2e2e` 块 92（402 行）+ 激活 31 / 9 / 7 / 1；local-e2e `sql/51` 外部 API 路由（44 / 5 / 2 / 1，经授权）。验收数据已清理（渠道 / 成本 / 变更记录 / 7 个合成 Platform 用户与角色），块 92、激活、Vault 规则、sql/51 路由保留；Vault 新增 3 条 `CHANNEL_ACCOUNT_CONFIG_V1` 记录未删。

**本机环境变化：** local-e2e 的 platform / channel-connector / ops-bff 已换成含本需求的 JAR（PID 50814 / 50957 / 51063，sha256 见报告），payermax-mock 已起；经控制台把 Platform 接入方改为 `PAYERMAX_MOCK`（旧的不完整 `PAYERMAX` 接入方与 `PAY` / `PAY_QUERY` 标准 API 已删），`50-channel-connector-payermax-mock.sql` 与 `channel_setup.py` 的渠道种子不再需要（旧脚本若硬编码旧接入方编码需同步，见 VER 报告）。

**遗留：** Payment 进程本机未跑（缺 payment / acquiring / card / client-master 的 v2e2e 库），AC-B05 的 Payment 侧留到阶段 7 在 mor-test 验；`db/channel-connector/ddl.sql` 的 `USE mor_channel_connector;` 为 mor-test 目标库，本机 v2e2e 执行须去掉；验证 agent 的一次 `docker inspect` 在其工具输出中显示过本机 Vault 库密码（未写入任何文件），本机开发口令，可按需轮换。

**阶段 6 输入：** [release/baseline.md](./release/baseline.md)（财务发布后的镜像基线、块 120–122、`OPERATIONS_BILLING_BASE_URL`）；发布清单将包含 channel-connector、platform-support、ops-bff、ops-web 四个镜像（固定 sha256）、`mor_channel_connector` DDL、`mor_vault` PROTECT 规则、`mor_platform` 块 92 + test 激活 SQL、Platform 内部 Token 白名单（若 mor-test 开启 internal-auth）、Compose 变量保留。

## 阶段 7 交付报告（2026-10-20）

部署与验收记录见 [release/release.md 第 6 节](./release/release.md)、[verification/stage7-acceptance.md](./verification/stage7-acceptance.md)、截图 [assets/stage7](./assets/stage7/)。

| 项 | 结果 |
|---|---|
| 备份 | `~/Code-MoR-backups/20261020T072007Z-channel-console-test/`（含回滚 compose 与四容器旧镜像 ID） |
| SQL | `mor_channel_connector` 18 表；`mor_vault` 规则 720 / 版本 17；`mor_platform` 块 92 402 行并启用 31 / 9 / 7 / 1，PLATFORM_ADMIN 35 → 36 |
| 镜像 | platform-support `e5a06814…`、channel-connector `4d31d529…`、ops-bff `bec85826…`、ops-web `3ae27f3d…`（D1 修复后）；均 healthy、重启 0 |
| 验收 | 接口 105 / 105；浏览器 35 / 35；既有商务 / 财务页 17 / 17；Payment 侧候选查询 2 / 2；渠道执行未跑（`PAYERMAX` 指向真实 UAT、合成凭据） |
| 缺陷 | D1 展示缺陷已修复并重发 ops-web，mor-test 重验 7 / 7 通过（截图 `assets/stage7/D1-未配置账号-配置列.png`），重验数据已清理 |
| 清理 | 验收数据按 ID 清理完毕，渠道各表 0 行；残留：3 个停用合成用户、3 个空角色、1 条 Vault 记录、审计 |

**待提交差异（阶段 8 清单）：** `paytech/channel-connector/**`、`paytech/platform-support/**`（api 2、app / integration / test 7、`console-resource-capabilities.json`、`platform-support-initial-data.sql` 块 92）、`paytech/operations-console-bff/**`、`operations-console-web/**`（含 D1 修复与 `configurationModel.test.ts` 计数 48）、`paytech/tools/arch-rules`、`scripts/pdmaner`、`local-environment/vault/policy.local.json`、本需求目录、`requirements/README.md`、`agent-docs/CURRENT_IMPLEMENTATION.md`、`operations-console-web/docs/design.pen`（Pencil 保存后）。

## 阶段 8 交付报告（2026-10-20）

用户确认「提交」后，以私有 `GIT_INDEX_FILE` 从当时的 `main` 顶端构建树，只收入本需求清单内的路径，`commit-tree` 后前移 `main`，不推送。

- **收入：** `paytech/channel-connector/**`、`paytech/platform-support/**`、`paytech/operations-console-bff/**`、`operations-console-web/**` 中渠道相关文件（`src/channel/**`、三张渠道页、`ConsolePage.tsx`、`menu.ts`、`pageCapabilities.ts`、`configurationModel.test.ts`、`docs/design.pen`）、`paytech/tools/arch-rules` 表归属、`scripts/pdmaner` 的 payment 生成输入与测试（5 个文件）、`local-environment/vault/policy.local.json`、本需求目录、`requirements/README.md` 本需求一行、`agent-docs/CURRENT_IMPLEMENTATION.md` 本需求条目。
- **排除（其他会话的未提交内容，保留在工作区）：** 财务页面与样式（`src/finance/**`、`FinanceSetup.tsx`、`FinanceAccountingRules.tsx`）、控制台面包屑（`App.tsx`、`styles.css`、`menuTrail*`、`useTrailTitleHeading.ts`）、对账与计费（`paytech/reconciliation/**`、`paytech/billing/**`、`scripts/pdmaner/billing-model.test.mjs`、`AGENTS.md`、`design-discuss/**`、对账决定）、财务与 ledger、对账、控制台体验需求目录及其在 `requirements/README.md` 的行。
- **design.pen：** 文件保存时间晚于 15 张画板导出时间，随本次提交收入；该文件为二进制，若其他会话在同一文件上有未提交画板，会一并进入。
- **口令：** 提交前扫描本需求目录与 Vault 本地策略，未发现明文口令；验证脚本只从 `.env.local` / `.env.test` 读取。

## 当前阶段与下一确认点

阶段 8 完成，本需求全部阶段结束（本地提交，未推送）。推送、合并或再次发布需另行授权。
