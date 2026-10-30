# 明细账运维 · 阶段 6 发布准备报告（2026-10-30）

> 范围：只做发布准备。mor-test 只读（inspect / sha256sum / cp 取出 / `rabbitmqctl list_queues` / READ ONLY 会话 SELECT / mysqldump 导出到本机）；未停 / 起 / 替换 mor-test 容器，未写 SQL / MQ / XXL / Nacos / 语言包。镜像只在本机构建；未提交、未推送；未改 `Code-MoR-plops` 与主工作区源码（本目录 `release/` 除外）。**阶段 7 须用户明确授权。**
> 材料：[baseline.md](./baseline.md)、[runbook.md](./runbook.md)、`compose-*.yaml`、[precheck/](./precheck/)、[evidence/](./evidence/)、`harness/`、`scripts/`、[plops_bundles.py](./plops_bundles.py)、`i18n-bundles/`。

## 1. 结论

| 项 | 结果 |
|---|---|
| 环境 | mor-test（本机 Docker，`mor-test-*`）；写库只有 `mor_platform`（块 148）；不是本机 `mor-local-mysql-1` |
| 基线 | main `331d8e0e`（功能 = `b92c0fde`）。6 个现网容器镜像与 payout 记录一致；account / Platform / ops-bff JAR 与 `331d8e0e` 构建 **0 差异**；ops-web dist 284 个文件**逐字节相同**；ledger（refclient-20261028b）**现网独有 0**、自身类 0 差异，只落后 main 的提供方 api JAR |
| 发布源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-plops-rel` = `331d8e0e` + 本需求 67 个文件（补丁 vs `9eab368c` 三方应用：64 干净含 7 个自动合并、3 个冲突手工合并）+ 1 个测试修正，指纹 `78ccfd1dc42e8c62`，`source_drift_check.sh` NO DRIFT。→ [evidence/merge-notes.md](./evidence/merge-notes.md) |
| 测试 | ledger **341/0**、account **73/0**、Platform 受影响 6 类 **55/0**、ops-bff **2573/0**（首轮 1 失败，见第 3 节）、arch-rules **70/0**；ops-web `tsc` 0 错误、`npm test` **1702/1702**、`build` 通过（dist 291）。测试后重建的 4 个 JAR 与镜像内 JAR 0 差异。→ [evidence/test-summary.txt](./evidence/test-summary.txt) |
| 镜像（tag `plops-20261030a`，未动 `:development`） | 见第 2 节；镜像内 JAR / dist = 构建产物，health-probe 沿用现网（与现网逐文件相同），Entrypoint / User / Healthcheck / 端口 / Env 与现网同服务镜像相同，nginx.conf 相同。→ [evidence/image-verify.txt](./evidence/image-verify.txt) |
| compose | 候选 5 + 回滚 5；10 份渲染后与现网 env **逐键逐值相同**（本需求不改 env；Platform 照抄 `SPRING_APPLICATION_JSON`，ops-bff 带 `OPERATIONS_TAX_BASE_URL`）。→ [evidence/env-check.txt](./evidence/env-check.txt) |
| SQL | 只有块 148（`mor_platform`，51 行，休眠 + 激活两步）；mor-test 预检 148 区间 / 编码 / 路径全 0、最新块 147（已激活）；`mor_ledger` 已有 10-19 科目分级迁移，不需要 ledger SQL |
| 离线演练 | 断网 MySQL 导入 mor-test 只读导出，六件套 15 步全部符合预期；每个状态用现网与候选 Platform JAR 编译、现网 Gateway JAR 解码：休眠 671 路由 / 1 080 333 B 不变，激活 675（+4）/ **1 086 771 B（距 4 MiB 余 3 107 533 B）**，回滚后与导出只差管理员角色 version 一行（预期）。→ `evidence/rehearsal-20261030T170542Z/` |
| 语言包 | 现网 #29 → 计划 **#30**：新增 147 key（finance 144、nav 3），zh-CN 同步导入，不新建命名空间、不废弃、不改基准；只读干跑 12/0 通过。→ [precheck/i18n-plan-dryrun-operations-console.out](./precheck/i18n-plan-dryrun-operations-console.out) |

## 2. 发布清单

| 服务 | 现网（回滚目标） | 候选 `plops-20261030a` | 变化（对现网） |
|---|---|---|---|
| ledger-service | `sha256:51a57a3b721840b079798159f8d149a4362bbb76e763111a7183ab1bb08864e9` | `sha256:b65223c1675ac1ee38f60388c9228393966ac212175aed5737fccc2d98f3330a`（JAR `5f460796e28dace2`） | 本需求 +14 / ~10（进度接口、`PostingProgressService` 抽出、关账第 3 步改调、读 account 4 MiB / billing 8 MiB）；另随 main 带上提供方 api 类 +40 / ~22 |
| account-service | `sha256:8dfd1060d13607f68668b376541c9cf80fb6e9056f891a8b73cb74ac40db0fb5` | `sha256:6ad6a22b92cfbbfd3469014763180691f12a6e71faa74055f053effcb1f339a8`（JAR `4ffb047fde069abb`） | ~5（重建 `changedAccountCount`） |
| platform-support-service | `sha256:0ea9cc2d6e897783080a009da6abaf5754a9599757e7f2cc90f0ae90e54514bb` | `sha256:1bfed8839d4c38ad2440cb8825a630cf6dbf698c4bb13f5cf6c316c0405f35bf`（JAR `7acd6ffa444493ee`） | ~1（能力清单加 `OPERATIONS_FINANCE_POSTING_OPS`） |
| operations-console-bff | `sha256:626a74a740cecb8ca643160b4f8423a1c071744b87f3cb1bf3a07c68ec779a3c` | `sha256:4bdfce6ee9df4bfb6d043d448e3b76b970abceb65dbed8027be95fe57313a0c4`（JAR `16cf615f02962c55`） | +19 / ~11（4 个接口、ledger 进度客户端、account 长耗时读超时） |
| operations-console-web | `sha256:acb33e948c659b2d2acbdb0404914e88626b4c71802785de002dbd3b0c88d0d8` | `sha256:5784fa817c471ebce707821dfd4db0727dd28cbe073776e620fb48a9f5d800b4`（dist 291） | 去掉文件名 hash 后逻辑上 +7（页面 js/css、4 个图标块、`SubledgerPostingDrawer` 拆出的共享块）、0 删除 |
| billing / api-gateway | 不发 | — | 无改动；块 148 快照由 Platform 编译，Gateway 热加载 |

## 3. 合并与测试中发现的问题

| # | 问题 | 处理 |
|---|---|---|
| M-1 | 3 个冲突：`platform-support-initial-data.sql` 末尾（块 147 / 148）、`configurationModel.test.ts` 页面数（85 / 84）、`finance.test.ts` 标题与过滤器 | 块 147 原样 → `COMMIT;` → 块 148 原样（只追加 100 行，块 148 与 plops 逐字节相同）；页面数 86；标题取"财务八页"、过滤器保留 payout 的 PAYOUT 排除。合并片段见 merge-notes 第 1 节 |
| T-1 | ops-bff `OperationsConsoleBffApplicationAutoTest.test_applicationReusesFoundationAndExposesExactOperationsRoutes` 失败：精确路由清单缺本需求 4 个接口（阶段 4 / 5 只跑了 `Finance*` 与 `PostingOpsRegistrationAutoTest`，与合并无关） | 发布树补 4 行（测试代码，不进 JAR），第二轮 2573/0。**`Code-MoR-plops` 尚无此修正**，阶段 8 以发布树为准或先同步回去 |

## 4. 部署顺序与影响（详见 runbook 第 2 节）

| 步 | 动作 | 中断 / 影响 | 依赖 |
|---|---|---|---|
| 1—3 | 备份 `mor_platform` → 预检 → 块 148 休眠写入（routeCount 仍 671） | 无 | — |
| 4 | ledger | 约 30—40 s：财务账本 / 关账 / 记账规则页 503；AccountPosted 在 `ledger.account.p0—p3` 积压后自动消费 | BFF 的 B1 依赖 ledger 新接口 |
| 5 | account | 约 35 s：余额 / 明细账 / payout 余额解释不可用；billing → account 事实积压后自动消费 | BFF 严格反序列化新字段 → account 先于 BFF |
| 6 | Platform | 约 60 s：登录与所有经 Platform 的请求不可用 | 能力清单先于激活 |
| 7 | ops-bff | 约 30 s：运营门户接口 503 | 4 个新接口 |
| 8 | ops-web | 约 5 s | 页面先于菜单激活 |
| 9 | 激活块 148 | Gateway 热加载，无中断；675 路由、1 086 771 B | 上述全部就位 |
| 10 | 语言包 #30 | 无 | 旧 Web 不读新 key，顺序上可提前 |

## 5. SQL 计划（mor-test `mor_platform`）

- 备份：`mysqldump --single-transaction --routines --databases mor_platform` → `~/Code-MoR-backups/plops-stage7/mortest-<UTC>/`（0700，不入库），核对 `Dump completed` 与 SHA256SUMS。
- 执行：`…-precheck.sql`（只读）→ `mor_platform-block-148.sql`（休眠，51 行）→ `…-verify.sql` → Gateway 日志确认快照刷新、routeCount 671 → 应用替换 → `…-activate.sql` → `…-verify.sql` → routeCount 675、快照约 1 086 771 B。文件均在 `db/platform-support/`，与初始化文件末尾块 148 内容相同。
- 回滚：激活中 `…-deactivate.sql`；休眠 / 停用后 `…-rollback.sql`（路由 ACTIVE 时以 ERROR 1242 拒绝、不改任何行）；兜底为第 1 步备份。离线演练已覆盖重复写入拒绝、重复激活 / 停用 / 回滚空操作、激活中回滚拒绝。

## 6. 恢复与不可逆项

- 应用：每个服务各有 `compose-rollback.<svc>.yaml`（钉现网 sha256，env 与现网逐键相同），倒序回退；回退 Web / Platform 前先停用块 148。
- 数据：块 148 见第 5 节；无业务表 / 字段 / 业务 DML。
- 需要说明的部分：
  - **ledger 读响应上限变化影响 ledger 全部经 HTTP 的读取**（追平、关账第 3 步、MQ 消费后按序号补读 account 批次、读 billing 事实），不仅是本页面；上限只放宽、不改语义，回退镜像即回到 8192 B（D-3 追平不可用随之回来）。
  - 块 148 激活 / 停用使 PLATFORM_ADMIN 角色 version + 1，回滚不回退（无副作用）。
  - 语言包发布不可变：回滚是重新发布 #31，#30 留在历史中。
  - 验收中的重建在一致数据上重写同值余额行（值不变，行版本 / 更新时间可能变化）；合成只读运营账号只能停用、不能删除审计。

## 7. 验收计划（详见 runbook 第 4 节）

遵守 S-03，不制造不一致：V-1 / V-2 AE001 OE 账簿与 FE 账簿进度三段 CONSISTENT（AE001 流 account 12 = ledger 12，以执行时读数为准）；V-3 批次浏览与详情；V-4 对已一致的流追平 → `processedCount 0`、已追平；V-5 重建 → `changedAccountCount 0`、余额逐行不变；V-6 合成只读运营账号（仅 `FINANCE_ACCOUNTING_READ`）无执行按钮、B3 / B4 直接调用 403；V-7 Playwright 真实浏览器 zh-CN / en × 1440 / 390（经 28080 中继），确认文案来自语言包 #30；V-8 明细账 / 余额 / 关账 / payout 页回归。写入只有 IAM 合成角色 + 账号（验收后停用）、一次追平（不写分录）、一次重建（同值）。脚本由阶段 5 `t6_*` 改写，阶段 7 授权后先只读干跑。

**可选项 O-1（需单独授权，默认不做）**：在 mor-test 复测"追平 200 批"耗时。需在新合成 OE 上：建日历 / 期间 / 账簿 → 停 ledger 约 2—4 分钟 → 经后端网络 sidecar 登记并批准 600 条合成事实（`mor_billing` / `mor_account` 产生不可经接口删除的会计记录）→ 守卫（队列消息数 = 600 且其他流无新批次，否则放弃并正常起 ledger）→ 只清空 `ledger.account.p0—p3` → 起 ledger → 运营台追平三次计时 → 核对一致。清理需用户在"保留带标记的合成数据"与"另行评审跨三库删除 SQL"之间选择。

## 8. 待用户裁决

| # | 事项 | 建议 |
|---|---|---|
| U-1 | 批准阶段 7：发布清单（第 2 节 5 个服务）、块 148 SQL 与备份、语言包 #30、验收写入范围（第 7 节） | 批准 |
| U-2 | ledger 随发会同时带上 main 的提供方 api 类（现网 ledger 自 10-28 未更新） | 接受（提供方均已在现网，ledger 自身类无其他变化） |
| U-3 | 可选项 O-1（mor-test 200 批耗时复测，需制造落后与不可删除的合成会计数据） | 不做，沿用阶段 5 本机结果（中位数约 5.2 s，最大 7.52 s） |
| U-4 | T-1 测试修正回写 `Code-MoR-plops` 还是阶段 8 直接从发布树提交 | 阶段 8 从发布树提交（与 payout 做法一致） |

## 9. 本机资源

- 发布树 `Code-MoR-plops-rel`（含 `target/`、`operations-console-web/node_modules`（真实目录）、`dist/`），保留到阶段 7 结束。
- 对照树 `Code-MoR-plops-base`（`331d8e0e`；`operations-console-web/node_modules` 是指向发布树的符号链接，删 worktree 前先删链接），阶段 7 后可删。
- `~/Code-MoR-backups/plops-stage6/`（0700）：`live/`、`base/`、`candidate/`（JAR、SHA256SUMS-jars、images.txt）、`ctx/`（镜像构建上下文）、`probe-live/`、`export-20261030T170501Z/`（mor_platform 只读导出）、`rehearsal-*`、`i18n/`、`logs/`、`build-apps.sh`、`run-tests.sh`。
- 本机新镜像 tag：5 个 `mor-test/<svc>:plops-20261030a`。
