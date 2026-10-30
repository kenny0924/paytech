# 联合查询 · 阶段 6 报告（mor-test 发布准备，2026-10-30 09:55—11:00 +04）

> 范围：只读核对 mor-test、本机构建与离线演练、写发布文件。**未改 mor-test 任何东西**：未停 / 起容器，未执行写 SQL / GRANT，未写注册块、XXL、MQ、Platform 数据，未登录 mor-test 运营 API，未发布语言包，未 `docker cp` 写入容器。未提交、未推送；未改主工作区与研发 worktree。阶段 7 的环境变更由主会话执行，须用户授权。任务卡：[task-card-stage6.md](./task-card-stage6.md)；ops-web 部分见 jq-web 的 [web-stage6.md](./web-stage6.md)。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | Platform = REQ-03 `49f71011`；ops-bff / ops-web = 税务 `e243d57b` / `748ca7b9`；Gateway `270c8ba2` 不换。详见 [baseline.md](./baseline.md) |
| 发布源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel`（detached worktree）= **main `6eaf1bd3`** + 联合查询 236 个文件（Code-MoR-jq-m，排除 `.jq-notes` / `node_modules` / `.vite-cache`）。税务已提交为 `fa21d5ce`，与 tax-20261029a 的构建源 `Code-MoR-tax-rel` 379 个文件逐字节相同，因此不再从 `Code-MoR-tax` 手工叠加（待裁决 D-1）。226 个文件直接复制，10 个与税务重叠的文件三方合并：7 个自动合并，3 个冲突已解决（ops-web 页面数 82、arch-rules 保留表 74 / 物理表 +1 / mor_platform 75）。清单与 SHA-256：`evidence/rel-status.txt`、`evidence/jq-source-files.sha256`；`scripts/source_drift_check.sh` 当前 `all 236 files unchanged; dev tree in sync; main = 6eaf1bd3` |
| 现网是否有未提交内容 | **没有**。`6eaf1bd3` 干净构建：ops-bff 2943 项 0 差异；Platform 3122 项只差税务提交的 2 个共享条目（Platform 未随税务重发）；ops-web 266 个文件逐字节相同（jq-web） |
| 构建与测试（发布 worktree 内） | BUILD SUCCESS；data-query 48 / 0、workflow 71 / 0、arch-rules 70 / 0、platform-test 132 / 1（唯一失败是 worktree 缺 gitignored 的 MinIO 公钥；补上后该类 4 / 0）；ops-bff 测试模块 **2519 / 0**；ops-web tsc、1654 / 0、i18n 7 / 7、build（jq-web）。`evidence/test-summary.txt` |
| 候选 = 阶段 5 验证内容 + 税务 | Platform 候选与阶段 5 本机运行 JAR（`.pre-i18nchg`）3228 项只差同样 2 个税务共享条目；ops-bff 候选与阶段 5 联合构建 3003 项只差 `paytech-billing-api!FactRefType.class`（阶段 5 用的是税务早期版本，候选与现网相同） |
| 候选相对现网 | Platform +106 / ~41 项（data-query 键类型、联合查询、模板 joint 与 rowCap、`application.yml` 只加 `data-query.joint.*` 默认值、税务 2 个共享条目）；ops-bff +60 / ~16 项（`evidence/jar-candidate-*-vs-live.txt`） |
| 第 2 节镜像 | 3 个，tag `jq-20261030a`，钉 sha256 |
| compose | 6 个覆盖文件渲染后与运行容器 env **逐键逐值相同**（Platform 59、ops-bff 21、ops-web 7；`evidence/env-check-20261030.txt`）。Platform 显式带 `DATAQUERY_HTTP_CALLERS_0=operations-console-bff`（主 compose 已有，同值）；ops-bff 带税务的 `OPERATIONS_TAX_BASE_URL`；不新增 env |
| DDL 与块 144 | mor-test 预检：新表 / 新列不存在；块 144 区间、8 个路由编码、(方法, 路径)、2 个菜单编码与前端路由全部未占用；依赖权限、菜单组 131301、target ACTIVE。离线演练（mor-test 只读导出 → 断网 MySQL 8.4.11 → 候选 Platform JAR 的生产 `GatewayConfigurationCompiler`）：基线 / 休眠 / 激活 / 重复激活 / 停用 / 删除 6 次编译全部 COMPILE OK，effective **633 → 633 → 641 → 641 → 633 → 633**；现网 Platform JAR 编译休眠 / 激活两种状态也 OK（633 / 641）；重复注册被主键拒绝；DDL 前滚 0.10 s、重跑无变化；块删除后与前滚状态 0 行差异；DDL 回滚后与导出 0 行差异，回滚可重跑，再前滚与首次前滚 0 差异（`evidence/rehearsal-20261030T0627Z/`） |
| 只读授权 | `data_query_demo_reader` 缺 mor_payment / mor_billing / mor_account → `sql/root-demo-reader-grants{,-rollback}.sql`（库级 SELECT，可重跑；待裁决 D-4） |
| 语言包 | operations-console 当前 **#24** → 计划 #25：新增 215（data-query 213、nav 2），基准变更 / 废弃 / 恢复 0，zh-CN 只导入这 215 条，不新建命名空间，tax 无变化；只读计划 12 项门禁全部通过（`precheck/i18n-plan-dryrun.out`，导入文件 `i18n-bundles/`） |
| 演示数据 | 数据源：复用 3 个 ACTIVE、启用 `MOR_CHANNEL_CONNECTOR_REPLICA`、新建 `MOR_PAYMENT_REPLICA` / `MOR_BILLING_REPLICA` / `MOR_ACCOUNT_REPLICA`。34 个 JQ_* 模板覆盖收单（订单、收银台会话、退款申请）、支付（意图、尝试、动作、渠道交易引用、渠道退款）、渠道（交互、回调）、计费事实、账户（记账批次、账户分录）、总账（会计凭证、凭证分录）、对账（平台流水、渠道对账单明细），业务名称（如“订单”“支付尝试”“会计凭证”，多入口括注“按 xx”），列带中文标签；IN 列表全部包在 `{{#if}}`；多态引用按类型拆列（Q4）；不读令牌 / 原始报文 / 哈希。定义 `demo/jq_demo_spec.py`，登记 `demo/register_jq_demo.py`（plan / apply / verify，可重跑），本机只读演练 `demo/local-dryrun/` |
| 模板校验 | 34 个模板：联合配置规则 0 错误 0 提醒；候选 JAR 的生产引擎（保存校验 + 只读守卫）0 错误；本机 v2e2e 32 个直接执行通过，2 个（账户分录、凭证分录）因 v2e2e 结构落后改在本机 auto 库执行通过；**mor-test 只读会话实际执行全部通过**（`precheck/demo-templates-local-v2e2e.json`、`demo-chains-mortest-20261030.json`） |
| 验收链路 | mor-test 没有一条完全真实的端到端链路（只有 AE001，无 SA / EG、无 SAAS_AUTOMATED；计费事实的支付引用是合成 ID）。按页面同款规则（已访问键去重、单键 50、单模板 100 行、6 层）在 mor-test 只读模拟 5 个入口，拼起来覆盖 6 个泳道（第 4 节）；全部在护栏内（单步 ≤ 14 次执行、累计 ≤ 24 次、≤ 5 层） |
| 运行卡与验收 | [runbook.md](./runbook.md)（12 步、回滚 R1—R6）；`acceptance/jq_api.py`（A—G，预期取阶段 6 模拟）；`acceptance/browser-plan.md`（jq-web） |

## 2. 候选镜像（本机 Docker 构建，mor-test 同主机直接可用，未推送任何仓库，`:development` 未动）

| 服务 | 镜像 | 产物 | 核对 |
|---|---|---|---|
| platform-support-service | `sha256:9444b483a6353960ad8d6f5e3f69c14667b5b1570616ea015ea356c4a4111298` | JAR `96d24bed8dd469fd` | 镜像内 JAR = 构建产物；health-probe 取自现网容器，相同；Entrypoint / User / Healthcheck / 端口 / WorkDir 与现网镜像相同 |
| operations-console-bff | `sha256:34ce01c1d775276287cf3c12c6133d4f08b6a5fa4737d0f00c76ddfbe74983a8` | JAR `ac050134d024c47f` | 同上（`evidence/image-verify.txt`） |
| operations-console-web | `sha256:da81d4b06dd20b3b53bd77abd0fc68bdd6d8686b3e022634fad99efcd8f94656` | dist 273 | jq-web（web-stage6.md） |

## 3. 发现

1. **税务已提交**：`fa21d5ce` 与 tax-20261029a 构建源逐字节相同，基线因此可以直接用 main，后续提交也不必再处理税务未提交改动。
2. **Platform 候选顺带上线税务的 2 个共享条目**：`OPERATIONS_FINANCE_TAX` 页面能力（菜单资源管理页从此能识别并编辑块 146 的税务菜单）、events.yaml 说明。不改行为。
3. **本机 v2e2e 结构落后**：`t_owner_account`、`t_ledger_account`、`t_chart_account` 缺 `account_no` 等列（mor-test 与本机 auto 库都有）。不影响 mor-test；本机 e2e 若要跑总账 / 账户模板需先补 v2e2e 结构（不在本需求范围）。
4. **本机 e2e Platform 已被多语言发布变更记录会话替换**（pid 14518，09:49 +04）；阶段 5 的 JAR 保存在 `.pre-i18nchg`。
5. **mor-test 数据零散**：渠道交互、回调、渠道退款三张表为空，对应节点只能演示“无数据”；退款全部 PROCESSING。
6. **重复行属设计行为**：同一行可能经两个不同触发键各被查到一次（如订单先按订单号、再按订单 ID），按 Q5 分别展示。

## 4. 验收入口（mor-test 只读模拟，`precheck/demo-chains-mortest-20261030.out`）

| 入口 | 层 / 执行 | 有数据的节点 |
|---|---|---|
| 交易订单号 `ord_3LOyZii4ct3BgT48vPZhmHbyC302a8`（税务阶段 7 验收订单） | 5 / 24 | 订单、收银台会话、计费事实 → 记账批次、平台流水 → 账户分录、会计凭证 → 凭证分录 6 行 |
| 支付意图 ID `239094951181373440`（PAYERMAX 3DS 成功） | 4 / 16 | 意图、尝试、2 个动作、渠道交易引用 |
| 退款单号 `rfd_3L5B25y5BGmxTcJDRyZcBx8OCN9BJK` | 3 / 9 | 退款申请、订单、收银台会话 |
| 渠道交易号 `T2026102218153708006502` | 5 / 19 | 渠道交易引用 → 动作 → 尝试 → 意图（上游） |
| 会计凭证 ID `241801561934753792` | 4 / 23 | 凭证、分录 → 记账批次 → 计费事实 → 订单、平台流水（上游） |

## 5. 待裁决

| # | 事项 | 建议 |
|---|---|---|
| D-1 | 发布基线改为 main `6eaf1bd3`（含已提交的税务 `fa21d5ce`），而不是任务卡写的“30361be7 + Code-MoR-tax 未提交改动” | 接受：两者代码逐字节相同，main 更干净，提交时也只剩本需求文件 |
| D-2 | 多语言发布变更记录（design-discuss-1b）也要换 Platform（提交 `69f87bbf` 在独立分支） | 约定先后：先上线的一方不动；后上线的一方在其候选里并入先上线一方的改动、重做基线证明。若对方先上，本需求的 Platform 候选、回滚文件与语言包当前号（#25）都要重做 |
| D-3 | mor-test 没有完全真实的端到端链路（无 SA / EG、无 SAAS_AUTOMATED，计费的支付引用是合成 ID） | 接受用 5 个入口拼出全链路做验收；要一条真实链路需在 mor-test 走一笔真实托管收银台支付（渠道沙箱），另起事项 |
| D-4 | `mor_payment` 授权粒度：库级（与其他从库一致）或只授模板用到的 5 张表（意图、尝试、动作、渠道交易引用、渠道退款） | 库级（与既有 6 个从库一致；模板不读令牌类字段，data-query 不允许读系统库）；如需更严，改为表级 GRANT，脚本只需换 5 行 |
| D-5 | 34 个演示模板与 3 个新数据源在验收后保留 | 保留作 mor-test 演示（模板由各系统 Owner 后续接管或替换，Q7） |

## 6. 需要用户授权的动作（阶段 7，由主会话执行）

1. 在 mor-test `mor_platform` 执行前滚 DDL（root）。
2. 替换 `platform-support-service`（`9444b483…`）、`operations-console-bff`（`34ce01c1…`）、`operations-console-web`（`da81d4b0…`）。
3. 在 `mor_platform` 执行注册块 144（休眠写入 → 激活）。
4. root 执行 `sql/root-demo-reader-grants.sql`（三个库只读）。
5. 发布 operations-console 语言包 #25。
6. 经运营 API 登记演示数据源与 34 个 JQ_* 模板，运行接口验收与浏览器走查。
7. 失败时按 runbook 第 4 节回滚。

不在授权范围：提交、推送；改 `test-environment/compose*.yaml` 基础文件；新建数据库账号（不需要）。

## 7. 文件

本目录（`/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel/.jq-notes/release/`）：`task-card-stage6.md`、`baseline.md`、`runbook.md`、本报告、`web-stage6.md`（jq-web）；`compose-{candidate,rollback}.{platform-support-service,operations-console-bff,operations-console-web}.yaml`；`sql/`（11 个 SQL + `SHA256SUMS`）；`harness/`；`scripts/`；`jq_bundles.py`、`i18n-bundles/`；`demo/`；`acceptance/`；`precheck/`；`evidence/`。
mor-test 只读导出与取出的 JAR（含数据，不入库）：`~/Desktop/Workspace/Code-MoR-backups/jq-20261030/`（`live/`、`candidate/`、`stage6-readonly-20261030T062658Z/mor_platform.sql.gz`、`rehearsal-20261030T0627Z/` 的 canon 文件）。
