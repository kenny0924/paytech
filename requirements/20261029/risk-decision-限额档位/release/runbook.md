# REQ-04 risk-decision 限额档位 · mor-test 发布运行卡

> 状态：**已执行（阶段 7，2026-10-30 09:3x—10:2x UTC，主会话）**，执行记录见 [delivery.md](../delivery.md)「阶段 7 执行记录」。以下为执行时的版本（含执行中加入的 ⑦a / R7a 与 ⑪ 续跑说明）。原状态：阶段 6 评审稿（2026-10-30）。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root DDL / DML / GRANT、注册块、语言包发布、data-query 登记、风控配置与发布、存量迁移、IAM 账号、验收写数据）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权。
> 依据：[technique.md](../technique.md) 第 9—10、13 节；[limit-tier-configuration.md](../../../../paytech/risk-decision/docs/limit-tier-configuration.md)；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`rq04-20261030b` tag 仅供辨认）。长时间执行用 `caffeinate -dimsu` 包裹。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 4 个候选镜像已构建并核对（stage6-report 第 2 节）；Gateway 与其他服务**不换镜像** | 已就绪 |
| P-2 | 执行前重读现网镜像：四个服务应仍是第 1 节回滚列（联合查询 b、REQ-03）；有人替换过 → 停止，在新基线上重建（回滚文件也要改写） | 执行当天 |
| P-3 | 候选源码未漂移：`scripts/source_drift_check.sh` 输出两段 `all … files unchanged` / `no new files`，且无 `main moved`（联合查询若已提交，改为核对提交内容与 `evidence/jq-source-files.sha256` 一致）。rq04-web 若在阶段 6 后改过 ops-web 或文案，先叠加、重建 ops-web 镜像、重跑 i18n 计划 | 执行当天 |
| P-4 | 通知并行会话：risk-decision 重启 **两次**（⑤ 换镜像、⑪ 迁移后清白名单，各约 30—60 秒，**结账 / 支付风控评估、名单、出款资格检查同时不可用**）、ops-bff 约 30 秒、Platform 约 60 秒；注册块 145；operations-console 语言包下一号；新 data-query 模板；**⑬ 激活后，没有档位的 Client 结账与支付都会被风控拒绝**（只有迁移名单与验收 Client 有档位） | 执行当天，主会话 |
| P-5 | 待裁决（stage6-report 第 6 节）：Q-1 额度建议值、Q-2 F-4 条件放行、Q-3 是否发 Platform、Q-4 验收 L / H 修订 | 待用户裁决 |
| P-6 | 账号：运营管理员（`test-environment/private/access.md`，脚本读取，不打印）；REQ-03 验收账号 uw / lead（`~/Code-MoR-backups/rq03-stage7/accounts.json`）；合成账号由 ⑮ 建 | 已就绪 |

## 1. 现网基线与候选（阶段 6 读取）

| 服务 | 现网镜像（= 回滚目标） | 候选镜像（`rq04-20261030b`） |
|---|---|---|
| risk-decision-service | `sha256:8934f65a6cf85a9b7e42df8abb77208acbacee2b74e2a878b553686cfa114cd3`（rq03-20261029a，JAR `73570443`） | `sha256:1414384e28e6964c3e47add9d648fe483fb685624874f867ceaeed9a1f929462`（JAR `34456b37`） |
| operations-console-bff | `sha256:2452aaa79b35a73d707d11934eb42b308d94758b5d4d3269c5178ed6b39ef6d3`（jq-20261030b，JAR `a8447c75`） | `sha256:4bd4c3dfb16e92f32900910b98dbcb7c73391b0bcb19927ee284e86585c90606`（JAR `9f660a77`） |
| platform-support-service | `sha256:a3003ef6499dcf5680154589f501d48db43e9c8041c02dcd5330e5eeade6a11c`（1b 多语言发布变更记录，JAR `29d7e804`） | `sha256:af13e77a05c3d30bf9652523d52ba302dc59d2ee2b2221500ece647215560012`（JAR `9b2a86e1`；只差页面能力清单） |
| api-gateway（D-9，⑦a） | `sha256:270c8ba294ddbb026a70b030e02eb1f998c8d95a49ba88cecaeab49b516efbf4`（card3ds-20261022b，JAR `fc425b2e`） | `sha256:d51daee3c4caecd720a8d512c6c4d7c43821cb2694cf1fa24d598449eac032cf`（rq04-20261030b-gw，JAR `d3dd5aa2`；Snapshot 上限 1 MiB → 4 MiB） |
| operations-console-web | `sha256:6f8161c279e71b24efc79280bdff5d0b3bbe4822e44e442cde409d88f4bd0173`（jq-20261030c，dist 273） | `sha256:2f7f8780d5abf1991f51eb2c4db242cde930a13c10bc579f908e5b6b74efac06`（dist 277） |

mor_risk_decision 57 张表、三张新表不存在；mor_platform 块 144 / 146 已占用、145 空闲，路由 ACTIVE 641 / DISABLED 6；operations-console #27；`MOR_RISK_DECISION_REPLICA` ACTIVE、账号无 `t_client_limit_tier` 授权；当前风控发布 = 第 4 节 R13 的回滚基线。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR-rq04/requirements/20261029/risk-decision-限额档位   # 提交后改为主树同名目录
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upm()   { ./test-environment/compose.sh -f "$REQ/release/compose-candidate-migration.risk-decision-service.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 risk-decision-service; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }
errs()  { docker logs --since "$2" "mor-test-$1-1" 2>&1 | grep -E ' ERROR |Exception' | head; }
export E2E_TARGET=test
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`errs <服务> <T>`。**任一步"失败即停"条件成立：停止前进，按第 4 节回滚到该步之前的状态。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核 | P-4 通知；`for s in risk-decision-service operations-console-bff operations-console-web platform-support-service api-gateway; do echo "$s $(img $s)"; done`；`$REQ/release/scripts/source_drift_check.sh`；`$REQ/release/scripts/env_check.sh`；`bash $REQ/release/harness/image_verify.sh` | 四个镜像 = 第 1 节回滚列；源码未漂移；env_check 11 行 `missing=[] valuediff=[]`、`extra=[]`（migration 行只多 `RISK_LIMIT_TIER_MIGRATION_CALLERS`）；image_verify 5 行全部 `same`。否则停（P-2 / P-3） |
| ② | 备份 | `B=~/Code-MoR-backups/rq04-20261030/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_risk_decision > "$B/mor_risk_decision.sql"`；同法导出 `mor_platform`；①的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空且以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro mor_risk_decision < $REQ/release/sql/00-mor_risk_decision-precheck.sql`；`sqlro mor_platform < $REQ/release/sql/01-mor_platform-block-145-precheck.sql`；`python3 -I $REQ/release/scripts/rq04_risk_config.py plan` | 与 baseline 第 3、4 节相同：新表 / 名单 / 编码 0、表数 57；块 145 七表 0、依赖 ACTIVE、路由 ACTIVE 641；风控 plan：两个点仍是回滚基线、48 个变量编码与 `LIMIT_TIER_DENIED` 未占用。否则停 |
| ④ | **mor_risk_decision 前滚**（旧 risk-decision 仍运行，不读新表；现有发布不引用 TIER 名单与 ALLOWED 事件，行为不变） | `sqlr mor_risk_decision < $REQ/release/sql/10-mor_risk_decision-forward.sql`；`sqlro mor_risk_decision < $REQ/release/sql/11-mor_risk_decision-verify.sql` | 秒级（演练 0.33 s，可重跑）；新表 3、表数 60；名单 300001—300004 / TIER / CLIENT / ACTIVE；新表空；五处注释已更新。SQL 报错 → 停，执行 `90-…-rollback.sql`（无数据时直接删） |
| ⑤ | **risk-decision 新镜像** | `T5=$(date -u +%FT%TZ); up risk-decision-service` | `img` = `1414384e…`，healthy；`errs` 为空；运营控制台"风控 · 评估 / 名单"不 503；现有发布照常（放行时新增写 ALLOWED 事件与日汇总）。180 秒不 healthy → R5 |
| ⑥ | ops-bff | `T6=…; up operations-console-bff` | `4bd4c3df…` healthy；`errs` 为空；运营控制台风控、数据查询（联合查询）、税务页面不 503 |
| ⑥b | Platform（页面能力清单，Q-3） | `T6b=…; up platform-support-service` | `af13e77a…` healthy；`errs` 为空；`gwlog $T6b` 快照刷新恢复、routeCount 641；运营控制台可登录。180 秒不 healthy → R6b |
| ⑦a | **api-gateway 新镜像（D-9）**，须在 22-activate 之前 | `T7a=$(date -u +%FT%TZ); up api-gateway` | `img api-gateway` = `d51daee3…`，healthy；`errs api-gateway $T7a` 为空；重启后 `gwlog $T7a` 快照刷新成功、routeCount 641（块 145 仍休眠）；运营 / Client 登录与已有路由正常（Gateway 重启约 30 秒，期间全部外部入口不可用，需通知）。180 秒不 healthy 或刷新失败 → R7a |
| ⑦ | 注册块：休眠 → 编译 → 激活 | `T7=…; sqlr mor_platform < $REQ/release/sql/20-mor_platform-block-145.sql`；`sqlro mor_platform < …/21-…-verify.sql`；10 秒后 `gwlog $T7`；`sqlr mor_platform < …/22-…-activate.sql`；`sqlro … 21-…-verify.sql`；5 秒后 `gwlog $T7` | 插入：permissions 4 / role_permissions 4 / resources 4 / routes 11 / headers 46 / queries 6 / guards 66（共 141），授权 REVOKED、资源与路由 DISABLED；快照 routeCount 仍 **641**（离线编译 loaded 658 / effective 641）。激活后 **652（+11）**，Snapshot 响应体 1 049 930 字节（新上限 4 194 304；离线演练 `evidence/rehearsal-gw-*`，旧 Gateway 复现失败、候选通过）。快照刷新失败 → 立即 `23-…-deactivate.sql`（回到 641），再查 |
| ⑧ | ops-web | `up operations-console-web` | `2f7f8780…` healthy；容器内文件数 **277**；管理员登录后"风控"下出现"限额档位"，列表空、统计全 0 |
| ⑨ | 语言包 | 计划（只读）`python3 -I $REQ/release/rq04_bundles.py`；发布同一命令加 `--apply` | 计划与阶段 6 相同（当前号以执行时为准，阶段 6 终读 #27，本次发 #28）：新增 210（risk 205、nav 4、master-data 1）、基准变更 1（`risk.feature.metricHint`）、废弃 / 恢复 0、zh-CN 导入 211、不新建命名空间、`zhChangedNotImported` 0。多出这三个命名空间以外的变化即停 |
| ⑩ | data-query：授权 + 模板 | DBA：`sqlr < $REQ/release/sql/40-mor-test-data-query-grant.sql`；`python3 -I $REQ/release/acceptance/rq04_data_query.py plan`；`… apply --yes` | GRANT 后多一行表级 `SELECT ON mor_risk_decision.t_client_limit_tier`；plan 全部通过（4 个样本 × 2 目标只读试跑各 1 行）；apply：试跑各 1 行 → 模板 `GOLIVE_LIMIT_TIER` ACTIVE（数据源复用 REQ-03）。失败即停（不影响档位功能，可单独回退） |
| ⑪ | **风控配置与发布（不激活）** | `python3 -I $REQ/release/scripts/rq04_risk_config.py build --yes`（可续跑：遇 504 按编码复核、对象已存在则复用；失败后重跑同一命令，D-10） | 建拒绝原因、48 个变量、7 个规则集、2 个流程、2 个执行发布并校验到 SHADOWING（状态写 `~/Code-MoR-backups/rq04-stage7/risk-config.json`）。额度为 Q-1 确认后的值。任一步报错即停（未激活，无业务影响；已建对象保留，重跑复用） |
| ⑫ | **存量迁移** | `upm`（risk-decision 以迁移覆盖重启）；`python3 -I $REQ/release/scripts/rq04_migrate.py export`（只读，名单应与 baseline 第 7 节相同）；`… run --yes`；`… verify`；`up risk-decision-service`（清白名单）；`… forbidden` | run：4 个 CREATED（重跑 SKIPPED_EXISTING）；**verify 缺口 0**；清白名单后 forbidden 返回 403 `RISK_LIMIT_TIER_MIGRATION_FORBIDDEN`。缺口 ≠ 0 → 停，不激活 |
| ⑬ | **激活档位发布** | `python3 -I $REQ/release/scripts/rq04_risk_config.py activate --yes` | 每个发布先影子运行（技术门槛 PASSED；**影子结果不用来核对迁移**：迁移前样本会全部判为"无档位拒绝"，见 F-2）后激活；两个点 ACTIVE |
| ⑭ | **激活后实时冒烟**（兼作预热，F-4） | `python3 -I $REQ/release/scripts/rq04_migrate.py smoke --yes` | 4 个迁移 Client 各一笔 USD 10 支付评估：ALLOW、路径经 `tier_standard`；Client `1`：DENY、`deny_no_tier`；记录各次 `elapsed_millis`。任一迁移 Client 被拒 → 立即 R13（回到旧流程版本），再查 |
| ⑮ | 验收 | 第 5 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-4 的会话已完成 |

**窗口影响**：⑤ 与 ⑫ 两次 risk-decision 重启（各约 30—60 秒）期间结账 / 支付风控评估、风控页面、出款资格检查失败；⑥ ops-bff 约 30 秒运营接口 503；⑥b Platform 约 60 秒；⑦ 激活到 ⑧ 之间新路由已开但旧页面不调用；⑧ 到 ⑨ 之间新页面中文环境显示内置英文（数分钟）；**⑬ 起**无档位 Client 的结账与支付被拒（业务开关，mor-test 只有 DEMO 迁移 Client 在交易）。

## 4. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy、快照编译失败、迁移有缺口或冒烟失败时）

| 编号 | 回到 | 命令 | 说明 |
|---|---|---|---|
| R13 | ⑬ 之前 | `python3 -I $REQ/release/scripts/rq04_risk_config.py rollback --yes` | 以回滚基线流程版本（支付 `236518950786142209`、结账 `236943227222069249`）**新建**执行发布 → 校验 → 影子 → 激活（已作废的发布不能重新激活）。档位数据、名单、变量保留不动。**回滚 risk-decision 镜像（R5）之前必须先做 R13**：旧镜像不认 `DAILY_SUM_AMOUNT`，档位流程仍生效时会评估失败 |
| R12 | ⑫ 之前 | 不删迁移数据（审计依据）；如须撤销业务影响，R13 即可（档位只在档位分支流程里起作用） | — |
| R11 | ⑪ 之前 | 不需要（未激活的发布与变量无影响）；如要清理，经运营页面停用变量 | — |
| R10 | ⑩ 之前 | 经运营 API 把模板 `GOLIVE_LIMIT_TIER` 置 INACTIVE（`POST /data-query-admin/v1/templates/{id}/status`）；DBA `sqlr < $REQ/release/sql/41-mor-test-data-query-revoke.sql` | 数据源属 REQ-03，不动 |
| R9 | ⑨ 之前 | 一般不需要（只新增 key 与一条提示改写，旧 Web 不读新 key）。必须回退时：`rq04_bundles.py --web-root /Users/kenny/Desktop/Workspace/Code-MoR-jq-rel` 重新发布 | 发布不可变，回退 = 再发一版 |
| R8 | ⑧ 之前 | `upr operations-console-web` | 菜单入口消失；其他页面不变（回到联合查询 b） |
| R7 | ⑦ 之前 | `sqlr mor_platform < $REQ/release/sql/23-mor_platform-block-145-deactivate.sql`（routeCount 652 → 641）；需要彻底删除再执行 `92-mor_platform-block-145-rollback.sql`（同时删除任何角色对 1451xx 权限的授权行） | 先 R8 再 R7；演练：停用、删除、重复删除均编译通过，删除后 mor_platform 与导出逐行相同 |
| R7a | ⑦a 之前 | **先 R7（停用块 145，回到 641 条 / 1 032 106 字节）**，再 `upr api-gateway` | 旧 Gateway 上限 1 MiB：块 145 激活状态下回退会持续刷新失败（保留最后一份快照但不再更新） |
| R6b | ⑥b 之前 | `upr platform-support-service` | 菜单资源管理页把块 145 的菜单 / 按钮标为"不支持"；导航、权限、路由不受影响 |
| R6 | ⑥ 之前 | `upr operations-console-bff` | 档位路由（若未停用）返回 404；联合查询 b、税务等不变 |
| R5 | ⑤ 之前 | **先 R13**；`upr risk-decision-service` | 旧镜像不读三张新表；表与数据保留不影响旧代码；新写入的 `*_ALLOWED` 事件旧代码不读 |
| R4 | ④ 之前 | 先 R10、R13、R5；`sqlr mor_risk_decision < $REQ/release/sql/90-mor_risk_decision-rollback.sql` | **有档位、变更记录、日汇总、`DAILY_SUM_AMOUNT` 变量、引用 TIER 名单的名单变量或 TIER 名单成员时 SIGNAL 拒绝**（演练已验证）。迁移 / 验收后一般只做 R13 + 镜像回退，保留表 |

回滚顺序：R13 →（R10）→ R9（一般跳过）→ R8 → R7 → R7a → R6b → R6 → R5 →（可选）R4。

## 5. 验收（⑮）

脚本 [acceptance/rq04_acceptance.py](./acceptance/rq04_acceptance.py)（结果 `acceptance/result-<子命令>-<UTC>.json`，不含口令 / 令牌）：

1. `python3 -I $REQ/release/acceptance/rq04_acceptance.py accounts --yes`：IAM 建 4 个合成角色 / 账号（`RQ04_ACCEPTANCE_{REQ,APPR,ELEV,RO}_<时间>`，均含 `RISK_DECISION_CONFIG_READ`），凭据 `~/Code-MoR-backups/rq04-stage7/accounts.json`（0600）。
2. `… prepare --yes`（**Q-4 同意后**）：REQ-03 验收账号对 L（LOW）、H（HIGH，负责人复核）各做一次承保修订并批准 → F-1：两个 Client 得到 VERIFICATION（UNDERWRITING_INIT，记修订版本），名单一致。
3. `… api --yes`：只读与权限 403；额度页无配置问题、四币种；TIER 名单手工加成员被拒；迁移接口外部 404；L：VERIFICATION 额度（USD 600 拒、USD 100 放、AED 100 放、GBP 拒）、越级拒、上线复核因无 ENABLED 开通项被拒、RESTRICTED 往返（本人审批拒、非申请人撤回拒、普通审批人批准、受限拒绝、模板 RESTRICTED、解除受限）；H：普通审批人批 ELEVATED 被拒、高级驳回、撤回、模板 BELOW_TARGET。
4. `… check`（只读，可重复）：全部档位与名单一致、无孤立名单成员、统计与库一致、今日放行评估与日汇总一致、激活后 TIMED_OUT 计数（F-4）、模板只读试跑、近 60 分钟日志只记元数据且无 ERROR。
5. 浏览器走查（主会话 / rq04-web，en / zh-CN × 1440 / 390）：列表（统计、筛选）、L 的 Client 档位页（VERIFICATION、额度与已用、上线复核清单中"开通项"系统项未满足）、H 的待审批视角、历史时间线、只读账号无写按钮。

验收产生的数据（全部保留）：L、H 各一个承保修订版本；L、H 的档位与变更记录；4 个迁移档位；评估记录、放行事件与日汇总；4 个合成 IAM 角色 / 账号（验收后可在 IAM 停用）。
