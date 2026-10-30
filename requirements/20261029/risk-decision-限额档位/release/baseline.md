# REQ-04 risk-decision 限额档位 · mor-test 现网基线（阶段 6，2026-10-30 08:40—09:00 UTC 只读读取）

> 读取方式：`docker inspect`、`docker exec … sha256sum`、`docker cp` 从容器**取出** JAR / dist / health-probe、root 只读会话（`SET SESSION TRANSACTION READ ONLY`，`@@transaction_read_only = 1` 已确认）、`mysqldump --single-transaction` 只读导出到本机 `~/Code-MoR-backups/rq04-stage6/`（0600 / 0700，不入库）。
> 未停 / 起 / 重启任何容器，未写任何库，未调用运营写接口，未 `docker cp` 写入容器。

## 1. 容器镜像（= 回滚目标）

| 服务 | 运行镜像 ID | 容器启动（UTC） | JAR / dist | 来源（compose 覆盖） |
|---|---|---|---|---|
| risk-decision-service | `sha256:8934f65a6cf85a9b7e42df8abb77208acbacee2b74e2a878b553686cfa114cd3` | 10-30 05:37 | JAR `73570443ba972ea1`（容器内 `sha256sum` 与取出文件一致） | REQ-03 rq03-20261029a：`Code-MoR-rq03/…/compose-candidate.risk-decision-service.yaml`（只设 image） |
| operations-console-bff | `sha256:2452aaa79b35a73d707d11934eb42b308d94758b5d4d3269c5178ed6b39ef6d3` | 10-30 08:37 | JAR `a8447c752285a07c` | 联合查询 b（jq-20261030b）：`Code-MoR-jq-rel/.jq-notes/release/compose-candidate-b.operations-console-bff.yaml`（含 `OPERATIONS_TAX_BASE_URL`） |
| platform-support-service | `sha256:a3003ef6499dcf5680154589f501d48db43e9c8041c02dcd5330e5eeade6a11c` | 10-30 08:56 | JAR `29d7e8043e244336` | design-discuss-1b 多语言发布变更记录：源码 `Code-MoR-i18nrel` c633fd0e（= 联合查询 b + 69f87bbf，已提交为 main `1c8341a2`）；镜像 = c82b6f34 只换 JAR（含 `DATAQUERY_HTTP_CALLERS_0`） |
| operations-console-web | `sha256:6f8161c279e71b24efc79280bdff5d0b3bbe4822e44e442cde409d88f4bd0173` | 10-30 09:05 | dist 273 个文件 | 联合查询 c（jq-20261030c） |

注意：联合查询 b 在本阶段读取前（08:37—08:39 UTC）刚上线；第一次读到的是 a（`34ce01c1` / `9444b483` / `da81d4b0`），随即复读为 b。候选以 b 为基线构建。

### 1.1 基线证明（候选源码不回退现网）

候选基线 = main **`022da95a`**（含联合查询提交 `d673817f` = jq-20261030c 源码、多语言发布变更记录提交 `1c8341a2` = 69f87bbf）。终版重建于 2026-10-30 09:20—09:45 UTC；发布 worktree `Code-MoR-rq04-rel` 与 `022da95a` 的代码差异只有本需求文件。

| 项 | 方法 | 结果 |
|---|---|---|
| 联合查询 / 多语言提交 = 现网 | 发布 worktree 相对 main `022da95a`（联合查询提交 `d673817f`、多语言发布变更记录提交 `1c8341a2`）的代码差异只有本需求文件（`scripts/source_drift_check.sh`）；ops-bff、ops-web 的联合查询源构建产物与现网取出物对比（`harness/jarcmp.py`；dist 逐文件 SHA-256）；Platform 候选与现网 `a3003ef6` JAR 对比 | ops-bff 3007 项 **0 差异**；ops-web 273 个文件**逐字节相同**（jq-20261030c）；Platform 3238 项只有能力清单 1 项不同（含 69f87bbf） |
| risk-decision 现网 = main | 现网 JAR 为 REQ-03 构建（`9d272ae6` 内容）；`git log 9d272ae6..6eaf1bd3 -- paytech/risk-decision` 为空；之后只有税务提交改了 foundation 消息目录（不进 risk-decision JAR） | 候选与现网：现网独有 **0** |
| 候选 vs 现网 | `evidence/jar-candidate-{risk,opsbff,platform}-vs-live.txt` | risk-decision：1472 → 1556，现网独有 0，新增 84、改动 35（全部为 REQ-04 文件）；ops-bff：3007 → 3061，现网独有 0，新增 54、改动 18（REQ-04 档位页聚合与严格解析）；Platform：3232 项只 `console-resource-capabilities.json` 1 项不同；ops-web：现网 273 → 277 个文件（构建哈希随内容变化，基线已由上一行证明） |

结论：现网四个服务没有候选基线以外的内容；候选 = 基线 + 本需求，不回退任何现网工作。

## 2. 容器环境变量

`scripts/env_check.sh` 渲染本目录候选 / 回滚覆盖，与运行容器**逐键逐值相同**（risk-decision 33、ops-bff 21、ops-web 7、Platform 59；`evidence/env-check-20261030.txt`）。迁移窗口覆盖 `compose-candidate-migration.risk-decision-service.yaml` 只多 `RISK_LIMIT_TIER_MIGRATION_CALLERS`。本需求不新增常设 env：`/risk-limit-tiers` 只记元数据的前缀在镜像内 `application.yml`；迁移白名单缺省为空。

## 3. mor_risk_decision（只读，`precheck/mor_risk_decision-precheck-20261030.out`）

| 项 | 读数 |
|---|---|
| 三张新表 / 同名索引 / 回滚过程 | 均不存在（0 / 0 / 0） |
| 表数 | 57（发布后 60） |
| 名单 ID 300001—300004、编码 `TIER_*`、`TIER` 用途名单 | 均未占用（0 / 0 / 0） |
| 五处改注释的列 | 类型与可空与前滚脚本一致（只改注释） |
| `DAILY_SUM_AMOUNT` 定义 / `*_ALLOWED` 事件 | 0 / 0 |
| 应用账号 | `risk_decision_app`：`SELECT, INSERT, UPDATE, DELETE ON mor_risk_decision.*`（覆盖新表，不需 GRANT） |
| 当前 ACTIVE 发布（回滚基线） | 支付尝试 `236518950932942848`（流程 `PAY_ATTEMPT_FLOW` v1 = `236518950786142209`：拦截 → 拒绝 / 审核 / 认证 → 放行 / 3DS 放行）；结账 `237117104682274816`（`CHECKOUT_BASELINE_ALLOW` v1 = `236943227222069249`：开始 → 放行）；两个风控点超时预算 500 ms |
| 评估历史 | 支付 11 次（1 次 TIMED_OUT，10-22 冷启动）、结账 59 次（1 次 TIMED_OUT，10-30 03:36 冷启动）；见 `evidence/f4-latency.md` |

## 4. mor_platform 注册与 Gateway（只读，`precheck/mor_platform-block-145-precheck-20261030.out`）

| 项 | 读数 |
|---|---|
| 块 145 | 七张表区间**均 0**；`RISK_LIMIT_TIER%` 权限、`OPERATIONS_RISK_LIMIT_TIER%` 资源 / `/risk/limit-tiers` 前端路由、`operations-risk-limit-tier-%` 路由 / `/api/operations/risk-limit-tiers/%` 路径**均 0** |
| 现网最大 | 路由 `900000000000146402`、权限 `…146101`、资源 `…146301`：**块 146 已被并行需求占用**，块 144 为联合查询；145 空闲，与两边都不冲突 |
| 路由计数 | ACTIVE **641**、DISABLED 6（离线编译：loaded 647、effective 641） |
| 依赖 | 角色 `900000000000000510 PLATFORM_ADMIN`、父菜单 `900000000000000565 OPERATIONS_RISK_MANAGEMENT`、target `900000000000000001`（ops-bff）、权限 `900000000000000536 RISK_DECISION_CONFIG_READ` 均 ACTIVE |
| 风控子菜单 | 10 个，sort 10—90（资格登记、承保都是 90）；"限额档位"排序见块正文 |

## 5. data-query（只读，`precheck/result-dq-plan-*.json`）

数据源 `MOR_RISK_DECISION_REPLICA`（`241634939987116032`）ACTIVE（REQ-03）；账号 `data_query_demo_reader` 现有 `SELECT ON mor_risk_decision.t_client_underwriting`（表级），**无** `t_client_limit_tier` → 需 DBA 表级 GRANT（`sql/40-…`）；模板 `GOLIVE_LIMIT_TIER` 未占用。plan 的两项未满足（缺授权、缺表）属发布前预期。

## 6. 语言包（只读，`precheck/i18n-plan-dryrun-operations-console.out`）

operations-console 当前 **#27**；27 个命名空间、11 125 个 ACTIVE key。计划：新增 **210**（risk 205、nav 4、master-data 1），基准变更 1（`risk.feature.metricHint` 加入"日放行合计"）、废弃 / 恢复 0，zh-CN 导入 211，不新建命名空间，`zhChangedNotImported` 0。发布号取阶段 7 当时的下一个空闲号。

## 7. 迁移名单（只读，`precheck/result-migrate-export-*.json`）

| 类别 | Client |
|---|---|
| 有 ENABLED 开通项（迁移为 STANDARD） | `237573040244891649`（DEMO2-CL-901，TOKEN_STORAGE）、`237573267559391233`（DEMO2-CL-103，CARD）、`237573476041465857`（DEMO2-CL-101，CARD / TOKEN_STORAGE）、`237575207592763393`（DEMO-CL-001，APPLE_PAY / CARD / PAYOUT_LOCAL）；均 ACTIVE、AE、个人 |
| 有有效承保、无开通项（不迁移） | L `240686705999454209`（LOW）、H `241049681294106625`（HIGH）：REQ-03 验收；F-1 后在下次承保批准时得到 VERIFICATION（验收 prepare 步做这件事） |
| 近期有评估的其他 Client | `800001`、`236087245667409921`、`239260386824658945`、`991016000000000401`：都没有 ENABLED 开通项（REQ-01 门禁本已不放行其交易）；激活后若再评估会走"无档位拒绝"，属预期 |
