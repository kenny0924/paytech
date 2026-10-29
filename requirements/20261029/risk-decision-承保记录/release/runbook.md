# REQ-03 risk-decision 承保记录 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-29）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root DDL / DML / GRANT、注册块、语言包发布、data-query 登记、IAM 账号、验收写数据）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权。
> 依据：[technique.md](../technique.md) 第 9.3 / 9.4 / 10.2 / 13 节；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`rq03-20261029a` tag 仅供辨认）。长时间执行用 `caffeinate -dimsu` 包裹。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 4 个候选镜像已构建并核对（stage6-report 第 2 节）：risk-decision、ops-bff、ops-web，以及只差页面能力清单的 Platform（第 ⑥b 步，可与用户确认后跳过，见 Q-7）；Gateway 与其他服务**不换镜像** | 已就绪 |
| P-2 | 执行前重读现网镜像：三个服务应仍是第 1 节回滚列；若有人替换过，停止，在新基线上重建（回滚文件也要改写） | 执行当天 |
| P-3 | 候选源码未漂移：`scripts/source_drift_check.sh` 输出 `all 181 files unchanged` 与 `no new files`；main 未前进。rq03-web 若在阶段 6 之后改过 ops-web 或文案，先重叠文件、重建 ops-web 镜像、重跑 i18n 计划 | 执行当天 |
| P-4 | 通知并行会话：risk-decision 重启约 30—60 秒（**Checkout / 支付风控评估、名单、出款资格检查同时不可用**；Gateway 与其他服务不受影响）、ops-bff 约 30 秒、Platform 约 60 秒（运营 / Client 登录与会话、IAM、文件、语言包 manifest、Gateway 快照刷新；Gateway 保留最后一份快照）、ops-web 数秒；注册块 143；operations-console #23；新 data-query 数据源 | 执行当天，主会话 |
| P-5 | 待裁决（stage6-report 第 6 节）：Q-1 验收是否批准 L / H 两个在途测试入驻案件；Q-2 菜单排序；Q-3 是否给常设角色授权 | 待用户裁决 |
| P-6 | 验收账号：运营管理员（`test-environment/private/access.md`，脚本读取，不打印）；合成账号由 ⑫ 建 | 已就绪 |

## 1. 现网基线与候选（阶段 6 读取）

| 服务 | 现网镜像（= 回滚目标） | 候选镜像（`rq03-20261029a`） |
|---|---|---|
| risk-decision-service | `sha256:288b0ff6a607a8e82bbf7e7cd03023bcca9159fc72e599281ec2fda81fe28a88`（relsnap-20261025a，JAR `0196699b`） | `sha256:8934f65a6cf85a9b7e42df8abb77208acbacee2b74e2a878b553686cfa114cd3`（JAR `73570443`） |
| operations-console-bff | `sha256:6833f484994be41f72ee3fb3ee73006514b9089e5a5d4330af6e3bc245c597bd`（wfdq-20261029a，JAR `5cee8709`） | `sha256:4f08f1e601beb791f8e61c088e471e8839fc65783acbf513e412e5fcaf200b6b`（JAR `1646cf7a`） |
| operations-console-web | `sha256:0b456b8aed53df0d31719f995a755d8f74d1abbd2a8b05e6de53ef5e0c34ae72`（rq12-20261029a，dist 256） | `sha256:2f7722daa531a012a8a1096a20c6b653f21b17ee6c018b2a703f9fa276bf0002`（dist 259） |
| platform-support-service | `sha256:7671724696de0756d1884f64fb2478505f1d775588e1a5e80bd4febe890d567a`（wfdq-20261029a，JAR `0345c0dd`） | `sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9`（JAR `6d7ab106`；与现网只差 `console-resource-capabilities.json`） |

mor_risk_decision 54 张表、三张新表不存在；mor_platform 最大块 142、路由 ACTIVE 619 / DISABLED 6；operations-console #22；data-query 无 `MOR_RISK_DECISION_REPLICA`。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR-rq03/requirements/20261029/risk-decision-承保记录   # 提交后改为主树同名目录
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }
errs()  { docker logs --since "$2" "mor-test-$1-1" 2>&1 | grep -E ' ERROR |Exception' | head; }
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`errs <服务> <T>`。**任一步"失败即停"条件成立：停止前进，按第 4 节回滚到该步之前的状态。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核 | P-4 通知；`for s in risk-decision-service operations-console-bff operations-console-web platform-support-service; do echo "$s $(img $s)"; done`；`$REQ/release/scripts/source_drift_check.sh`；`$REQ/release/scripts/env_check.sh`；`bash $REQ/release/harness/image_verify.sh` | 四个镜像 = 第 1 节回滚列；源码未漂移、main 未前进；env_check 8 行全部 `missing=[] extra=[] valuediff=[]`；image_verify 4 行全部 `same`。否则停（P-2 / P-3） |
| ② | 备份 | `B=~/Code-MoR-backups/rq03-20261029/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_risk_decision > "$B/mor_risk_decision.sql"`；同法导出 `mor_platform`；①的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空且以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro mor_risk_decision < $REQ/release/sql/00-mor_risk_decision-precheck.sql`；`sqlro mor_platform < $REQ/release/sql/01-mor_platform-block-143-precheck.sql`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot -e "SHOW GRANTS FOR 'risk_decision_app'@'%'"` | 与 baseline 第 3、4 节相同：新表 0、表数 54、同名索引 / 过程 0；块 143 七表 0、编码 / 路径 0、依赖 ACTIVE、路由 ACTIVE 619；应用账号库级 DML。否则停 |
| ④ | **mor_risk_decision 前滚 DDL + DML**（旧 risk-decision 仍运行，不读新表） | `sqlr mor_risk_decision < $REQ/release/sql/10-mor_risk_decision-forward.sql`；`sqlro mor_risk_decision < $REQ/release/sql/11-mor_risk_decision-verify.sql` | 秒级（演练 0.24 s）；新表 3、`t_client_underwriting` 58 列、3 唯一键 + 2 普通索引；规则 `290001 / 1 / ACTIVE / 1`、阈值 `290101 / USD / 50000.00000000`；承保记录 0。SQL 报错 → 停，执行 `90-…-rollback.sql`（无记录时直接删表） |
| ⑤ | **risk-decision 新镜像** | `T5=$(date -u +%FT%TZ); up risk-decision-service` | `img` = `8934f65a…`，healthy；`errs risk-decision-service $T5` 为空；Nacos 注册恢复（ops-bff 风控页面不 503）；结账风控评估冒烟：运营控制台"风控 · 评估"列表可查。180 秒不 healthy → 第 4 节 R5 |
| ⑥ | ops-bff | `T6=…; up operations-console-bff` | `4f08f1e6…` healthy；`errs` 为空；运营控制台风控名单、工作流、对账、合同页不 503 |
| ⑥b | Platform（页面能力清单） | `T6b=$(date -u +%FT%TZ); up platform-support-service` | `49f71011…` healthy；`errs platform-support-service $T6b` 为空；`gwlog $T6b` 快照刷新恢复成功、routeCount 619（重启期间失败属预期）；运营控制台可登录；XXL 分组 2 心跳晚于 T6b。唯一变化：菜单资源管理页能识别 `OPERATIONS_RISK_UNDERWRITING` 及其两个按钮（⑦ 激活后在菜单资源页显示为受支持、可编辑）。180 秒不 healthy → R6b |
| ⑦ | 注册块：休眠 → 编译 → 激活 | `T7=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/release/sql/20-mor_platform-block-143.sql`；`sqlro mor_platform < $REQ/release/sql/21-mor_platform-block-143-verify.sql`；等 10 秒 `gwlog $T7`；`sqlr mor_platform < $REQ/release/sql/22-mor_platform-block-143-activate.sql`；`sqlro mor_platform < …/21-…-verify.sql`；5 秒后 `gwlog $T7` | 插入：permissions 3 / role_permissions 3 / resources 3 / routes 12 / headers 52 / queries 8 / guards 72，授权 REVOKED、资源与路由 DISABLED；快照刷新成功、routeCount 仍 **619**（DISABLED 行也参与编译，阶段 6 离线编译 loaded 637 / effective 619）。激活后 routeCount **631**；未登录 `curl -s -o /dev/null -w '%{http_code}' -H 'Origin: http://localhost:28080' http://localhost:28080/api/operations/risk-underwriting/v1/underwriting-statistics` 返回码与已有读路由（如 `…/risk-lists/v1/lists`）相同（激活前 404）。快照刷新失败 → 立即 `23-…-deactivate.sql` + `92-…-rollback.sql` |
| ⑧ | ops-web | `up operations-console-web` | `2f7722da…` healthy；`docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f \| wc -l'` = **259**；管理员登录后"风控"下出现"承保"菜单，列表空态、统计全 0 |
| ⑨ | 语言包 | 计划（只读）`python3 -I $REQ/release/rq03_bundles.py`；发布同一命令加 `--apply` | 计划与阶段 6 相同：当前 **#22**、新增 247（risk 242、nav 3、client-review 1、master-data 1）、基准变更 / 废弃 / 恢复 0、zh-CN 导入 247、命名空间 26、`zhChangedNotImported` 0。当前号不符或出现这 4 个命名空间以外的变化即停。发布后 **#23** READY 且为当前；匿名 manifest 逐包 200。Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 不重复发布 |
| ⑩ | data-query：授权 + 登记 | DBA：`sqlr < $REQ/release/sql/40-mor-test-data-query-grant.sql`；`E2E_TARGET=test python3 -I $REQ/release/acceptance/rq03_data_query.py plan`；`… apply --yes` | GRANT 后 `SHOW GRANTS` 多一行表级 `SELECT ON mor_risk_decision.t_client_underwriting`；plan 全部通过（模板对 4 个 Client 只读试跑各 1 行）；apply：连接测试 `connected` 与 `privilegesPassed` 为 true → 数据源 `MOR_RISK_DECISION_REPLICA` ACTIVE → 试跑各 1 行 → 模板 `GOLIVE_UNDERWRITING_APPROVED` ACTIVE。失败即停（不影响已发布的承保功能，可单独回退本步） |
| ⑪ | 角色 | **本次不给常设角色授权**（Q-3 默认）：块 143 激活已给 `PLATFORM_ADMIN` 三项权限；`OPERATIONS_VIEWER` 等不变。用户若决定建业务角色，按 stage6-report 第 5 节的 IAM 调用执行 | — |
| ⑫ | 验收 | 第 5 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-4 的会话已完成 |

**窗口影响**：⑥b Platform 重启约 60 秒（登录 / 会话、IAM、文件、语言包 manifest；Gateway 保留旧快照）；⑤ risk-decision 重启约 30—60 秒——结账风控评估、风控配置 / 名单 / 评估页面、出款资格检查失败（调用方按各自降级 / 重试；mor-test 无真实交易）；⑥ ops-bff 约 30 秒运营接口 503；⑤—⑧ 之间旧 ops-web 不调用新接口；⑦ 激活到 ⑧ 之间新路由已开但旧页面不调用；⑧ 到 ⑨ 之间新页面中文环境显示内置英文（数分钟）。

## 4. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy 或快照编译失败时）

| 编号 | 回到 | 命令 | 说明 |
|---|---|---|---|
| R10 | ⑩ 之前 | 经运营 API 把模板 `GOLIVE_UNDERWRITING_APPROVED`、数据源 `MOR_RISK_DECISION_REPLICA` 置 INACTIVE（`POST /data-query-admin/v1/templates/{id}/status`、`…/data-sources/{id}/status`，`{"status":"INACTIVE","expectedVersion":…}`）；DBA `sqlr < $REQ/release/sql/41-mor-test-data-query-revoke.sql` | 数据源 / 模板记录保留（审计） |
| R9 | ⑨ 之前 | 一般不需要：只新增 key，旧 Web 不读这些 key。必须回退时：`rq03_bundles.py --web-root <e3e0c12f 干净 worktree>` 重新发布（KEY_IMPORT 废弃 247 个新 key） | 发布不可变，回退 = 再发一版 |
| R8 | ⑧ 之前 | `upr operations-console-web` | 菜单入口消失；其他页面不变 |
| R7 | ⑦ 之前 | `sqlr mor_platform < $REQ/release/sql/23-mor_platform-block-143-deactivate.sql`（停用，routeCount 631 → 619）；需要彻底删除再执行 `92-mor_platform-block-143-rollback.sql`（同时删除任何角色对 1431xx 权限的授权行） | 先 R8 再 R7；演练：停用、删除、重复删除均编译通过，删除后 mor_platform 与导出逐行相同 |
| R6b | ⑥b 之前 | `upr platform-support-service` | 菜单资源管理页把块 143 的菜单 / 按钮标为"不支持"、拒绝编辑；导航、权限、路由不受影响 |
| R6 | ⑥ 之前 | `upr operations-console-bff` | 承保路由（若未停用）返回 404；其他功能不变 |
| R5 | ⑤ 之前 | `upr risk-decision-service` | 旧镜像不读三张新表；表与数据保留不影响旧代码 |
| R4 | ④ 之前 | 先 R10（DBA 回收授权）与 R5；`sqlr mor_risk_decision < $REQ/release/sql/90-mor_risk_decision-rollback.sql` | **`t_client_underwriting` 有任何行时 SIGNAL 拒绝**（承保记录是审计依据，演练已验证）。验收已产生记录时：由用户决定保留表（只回退镜像，推荐）还是先人工删除第 5 节列出的验收记录再执行 |

回滚顺序：R10 → R9（一般跳过）→ R8 → R7 → R6b → R6 → R5 →（可选）R4。

## 5. 验收（⑫）

脚本 [acceptance/rq03_acceptance.py](./acceptance/rq03_acceptance.py)（结果 `acceptance/result-<子命令>-<UTC>.json`，不含口令 / 令牌）：

1. `E2E_TARGET=test python3 -I $REQ/release/acceptance/rq03_acceptance.py accounts --yes`：IAM 建 3 个合成角色 / 账号（`RQ03_ACCEPTANCE_{UW,LEAD,RO}_<时间>`，均含 `RISK_DECISION_CONFIG_READ` 以显示父菜单），凭据 `~/Code-MoR-backups/rq03-stage7/accounts.json`（0600）。
2. `… prepare --yes`（**Q-1 同意后**）：经运营 API 审完并批准 L（`240686862329565184`，代 Partner `900000000001370001`）与 H（`241049829650845696`，MCC 5967、CRR HIGH）；四眼由切片 1 合成复核人完成（mor-test 尚无时由切片 1 脚本经 IAM 新建，凭据 `~/Code-MoR-backups/onboarding-slice1-acceptance/reviewer-mor-test.json`）。副作用：两个 Client 变为 ACTIVE、投影 CLIENT 资格（与入驻批准相同）。
3. `… api --yes`：P 权限（只读 403、承保员复核 403）；A（L）自动承保（若输入不符合则改人工 LOW）→ 修订 → 批准 v2、v1 作废；C（H）提交 DECLINE 409 → 承保员提交 → 负责人拒绝 → 负责人重建并提交 → 本人复核 409 → 管理员批准；E（M）CRR 为空按 MEDIUM、自动承保 409、拒绝（遗留申请工作台若不是 READY 只记录现状）；随后自动执行 check。
4. `… check`（只读，可重复）：统计 / 全部列表（每个 Client 最新版本）/ 已作废与已拒绝页签与库计数一致；4 个 Client 模板只读试跑；规则 v1；两个服务近期日志不含 Client 名称。
5. 浏览器走查（主会话 / rq03-web，en / zh-CN × 1440 / 390）：列表（统计卡、页签、筛选、"待我复核"）、新建 / 自动承保（L 已承保时用 H 的详情）、复核视角（lead 打开本人提交的记录时按钮禁用）、已批准详情与版本历史、修订弹窗；Client 审核 / 详情页的承保入口；ro 账号无写按钮。

验收产生的数据（全部保留，回滚 R4 时人工处理）：L 两版、H 两版、M 一版承保记录；L、H 两个入驻案件的审批记录与 Client 激活；3 个合成 IAM 角色 / 账号（验收后可在 IAM 停用）；切片 1 合成复核人（如新建）。
