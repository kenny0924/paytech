# 联合查询 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-30）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root DDL 与 GRANT、注册块、语言包发布、运营 API 登记、验收）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权。
> 依据：[technique.md](../../../Code-MoR/requirements/20261029/platform-support-联合查询/technique.md) 第 9 节；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`jq-20261030a` tag 仅供辨认）。长时间执行用 `caffeinate -dimsu` 包裹。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 三个候选镜像已构建并核对（stage6-report 第 2 节；ops-web 由 jq-web 在同一发布 worktree 构建，见 [web-stage6.md](./web-stage6.md)）；Gateway 与其他服务**不换镜像** | 已就绪 |
| P-2 | 执行前重读现网镜像：三个服务应仍是第 1 节回滚列；若有人替换过（尤其多语言发布变更记录会话的 Platform），停止，在新基线上重建（回滚文件也要改写） | 执行当天 |
| P-3 | `scripts/source_drift_check.sh` 输出 `all 236 files unchanged; dev tree in sync; main = 6eaf1bd3`；main 有新提交时评估是否要并入候选 | 执行当天 |
| P-4 | 阶段 7 开始时通知并行会话（多语言发布变更记录 design-discuss-1b、税务、其他活跃会话）：Platform 重启约 60 秒（登录 / 会话、IAM、文件、语言包 manifest、Gateway 快照刷新、Platform 补偿与 XXL 受影响；Gateway 保留最后一份快照）、ops-bff 约 30 秒、ops-web 数秒；注册块 144；operations-console #25 | 执行当天，主会话 |
| P-5 | 待裁决 D-1—D-4（stage6-report 第 5 节） | 待用户 |
| P-6 | 验收账号：运营管理员（`test-environment/private/access.md`，脚本读取，不打印）；只读账号口令 `test-environment/private/data-query-demo.env` | 已就绪 |

## 1. 现网基线与候选（阶段 6 读取）

| 服务 | 现网镜像（= 回滚目标） | 候选镜像（`jq-20261030a`） |
|---|---|---|
| platform-support-service | `sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9`（rq03-20261029a，JAR `6d7ab106`） | `sha256:9444b483a6353960ad8d6f5e3f69c14667b5b1570616ea015ea356c4a4111298`（JAR `96d24bed`） |
| operations-console-bff | `sha256:e243d57bd5406a2ac8efa133b52586cc38acfd60ec2c739e6fe5ff1dabd6fe27`（tax-20261029a，JAR `3080a027`） | `sha256:34ce01c1d775276287cf3c12c6133d4f08b6a5fa4737d0f00c76ddfbe74983a8`（JAR `ac050134`） |
| operations-console-web | `sha256:748ca7b9c6b417104bbb7e3a09ba4b2630d8c4c513a4de2c43644a769b66a97a`（tax-20261029a，dist 266） | `sha256:da81d4b06dd20b3b53bd77abd0fc68bdd6d8686b3e022634fad99efcd8f94656`（dist 273） |

mor_platform：`t_query_key_type` / `joint_json` 不存在；块 144 区间与新编码 0；路由 ACTIVE 633 / DISABLED 6；operations-console #24；`MOR_CHANNEL_CONNECTOR_REPLICA` INACTIVE；无 payment / billing / account 数据源。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
R=/Users/kenny/Desktop/Workspace/Code-MoR-jq-rel/.jq-notes/release   # 提交后改为 requirements/20261029/platform-support-联合查询/release
up()    { ./test-environment/compose.sh -f "$R/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$R/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`docker logs --since <T> mor-test-<服务>-1 2>&1 | grep -E 'ERROR|Exception'`。**任一步“失败即停”条件成立：停止前进，按第 4 节回滚到该步之前。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核 | P-4 通知；`for s in platform-support-service operations-console-bff operations-console-web; do echo "$s $(img $s)"; done`；`$R/scripts/source_drift_check.sh`；`SERVICES="platform-support-service operations-console-bff operations-console-web" $R/scripts/env_check.sh` | 三个镜像 = 第 1 节回滚列；源码未漂移；env_check 6 行全部 `missing=[] extra=[] valuediff=[]`（Platform 59、ops-bff 21、ops-web 7）。否则停（P-2 / P-3） |
| ② | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/jq-20261030/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_platform > "$B/mor_platform.sql"`；`sqlr -e "SHOW GRANTS FOR 'data_query_demo_reader'@'%'" > "$B/reader-grants-before.txt"`；① 的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空且以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro mor_platform < $R/sql/mor_platform-precheck.sql` | 与 baseline 第 3 节相同：新表 / 新列 0 / 0；块 144 区间、新编码与路径 0；依赖 ACTIVE；路由 ACTIVE 633。否则停 |
| ④ | **前滚 DDL**（旧 Platform 仍在运行） | `sqlr mor_platform < $R/sql/mor_platform-forward.sql`；`sqlro mor_platform < $R/sql/mor_platform-verify-after.sql` | 秒级（演练 0.10 s）；`t_query_key_type` 17 列、`PRIMARY` / `uk_query_key_type_code` / `idx_query_key_type_status`；`joint_json` 在 `constraints_json` 之后、可空、存量版本全空。SQL 报错 → 停，执行 `mor_platform-rollback.sql`。旧代码只用显式列清单读写 `t_query_template_version`，新列不影响 |
| ⑤ | **Platform 新镜像** | `T5=$(date -u +%FT%TZ); up platform-support-service` | `img` = `9444b483…`，healthy；日志无 ERROR；`gwlog $T5` 快照刷新恢复成功、routeCount 仍 633（重启期间刷新失败属预期，Gateway 保留旧快照）；运营控制台可登录；数据查询模板列表可打开。180 秒不 healthy → R4 |
| ⑥ | ops-bff | `up operations-console-bff` | `34ce01c1…` healthy；运营控制台税务查询、承保记录、数据查询模板、对账页不 503 |
| ⑦ | 注册块：休眠 → 编译 → 激活 | `T7=$(date -u +%FT%TZ); sqlr mor_platform < $R/sql/mor_platform-block-144.sql`；`sqlro mor_platform < $R/sql/mor_platform-block-144-verify.sql`；等 10 秒 `gwlog $T7`；`sqlr mor_platform < $R/sql/mor_platform-block-144-activate.sql`；等 5 秒 `gwlog $T7` | 注册：key_types 26、resources 2、routes 8（DISABLED）、headers 36、queries 5、guards 48；休眠后快照刷新无 `CONTROL_PLANE_UNAVAILABLE`、routeCount 仍 **633**（休眠行也参与编译，阶段 6 已离线编译通过）；激活后 routeCount 633 → **641**；未登录 `curl -s -o /dev/null -w '%{http_code}' -H 'Origin: http://localhost:28080' http://localhost:28080/api/operations/data-query/v1/joint-query/settings` 的返回码与已有路由 `…/data-query-admin/v1/templates` 相同（激活前为 404）。快照刷新失败 → `…-block-144-deactivate.sql` + `…-block-144-rollback.sql` 立即恢复 |
| ⑧ | ops-web | `up operations-console-web` | `da81d4b0…` healthy；`docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f \| wc -l'` = 273；数据查询菜单下出现“联合查询”“键类型” |
| ⑨ | 只读授权 | `sqlr < $R/sql/root-demo-reader-grants.sql` | `SHOW GRANTS` 多出 `mor_payment` / `mor_billing` / `mor_account` 三条 SELECT，其余不变；无任何写权限 |
| ⑩ | 语言包 #25 | 计划（只读）`python3 -I $R/jq_bundles.py`；发布同一命令加 `--apply` | 计划与阶段 6 相同：当前 **#24**、新增 215（data-query 213、nav 2）、基准变更 / 废弃 / 恢复 0、zh-CN 导入 215、不新建命名空间、`zhChangedNotImported` 0。当前号不是 #24 或出现 data-query / nav 以外的变化即停（多语言发布变更记录会话若先发了 #25，按新当前号重跑计划并复核差异）。发布后 **#25** READY 且为当前；Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 就不重复发布 |
| ⑪ | 演示数据登记 | `E2E_TARGET=test python3 -I $R/demo/register_jq_demo.py plan`；`… apply --yes` | plan：3 个数据源新建、1 个启用、34 个模板新建；apply：7 个数据源 ACTIVE（新建的连接检查 PASSED）；34 个 JQ_* 模板 ACTIVE 且 `jointQuery.participating = true`；verify 5 个入口无 excluded、阶段 6 模拟到的模板全部可达。任一模板保存报错即停（Platform 保存时做只读守卫与模板解析） |
| ⑫ | 验收 | 第 5 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-4 的会话已完成 |

**窗口影响**：⑤ Platform 重启约 60 秒；⑥ ops-bff 约 30 秒运营接口 503；⑤—⑧ 之间旧 ops-web 不调用新接口；⑦ 激活到 ⑧ 之间新路由已开但旧页面不调用；⑧ 到 ⑩ 之间新页面的新文案 zh-CN 显示内置英文（数分钟）；⑪ 之前联合查询页面能打开但没有可执行的 JQ_* 模板（只有 26 个键类型）。

## 4. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy 或快照编译失败时）

| 顺序 | 动作 | 说明 |
|---|---|---|
| R1 | `upr operations-console-web` | 回到 `748ca7b9`（税务）。语言包不回退：旧页面不引用新 key |
| R2 | `sqlr mor_platform < $R/sql/mor_platform-block-144-deactivate.sql` → `sqlr mor_platform < $R/sql/mor_platform-block-144-rollback.sql` | 停用后删除 125 行（含 26 个键类型）；快照 routeCount 回到 633 |
| R3 | `upr operations-console-bff` | 回到 `e243d57b`（税务） |
| R4 | `upr platform-support-service` | 回到 `49f71011`（REQ-03）；旧代码在前滚结构上照常运行（新表不读、`joint_json` 可空且旧代码不写） |
| R5 | DDL 回滚（可选，旧镜像运行后） | `sqlr mor_platform < $R/sql/mor_platform-rollback.sql` → `sqlro mor_platform < $R/sql/mor_platform-precheck.sql`（新表 / 新列 0 / 0）。会丢失 JQ_* 模板版本里的联合查询配置；模板本身保留（旧代码照常执行单模板）。阶段 6 演练：回滚后与导出 0 行差异、可重跑 |
| R6 | 演示数据（可选） | 运营 API 把 34 个 JQ_* 模板与 3 个新数据源改 INACTIVE（data-query 不提供模板删除）；`sqlr < $R/sql/root-demo-reader-grants-rollback.sql`（须先停用三个数据源）。`MOR_CHANNEL_CONNECTOR_REPLICA` 可改回 INACTIVE |

只回退 ops-web 可行（新页面下线，接口保留）；只回退注册块可行（联合查询与键类型接口 404，模板与单模板执行照常）；回退 Platform 而保留新 ops-bff / ops-web 不可行（新页面与模板编辑器调用旧 Platform 不存在的接口与字段）。

## 5. 验收（⑫）

| # | 内容 | 命令 / 方式 | 通过条件 |
|---|---|---|---|
| A1 | 登记 | ⑪ | 见 ⑪ |
| A2 | 接口 | `E2E_TARGET=test python3 -I $R/acceptance/jq_api.py` | 全部通过（A 键类型 26、B 设置与护栏、C 5 个入口可达预览、D 5 条链路逐层展开全部 SUCCEEDED 且不少于阶段 6 模拟、覆盖 6 个泳道、E 单模板分页、F 反例 400 / 422、G 税务 / 承保 / 模板列表回归） |
| A3 | 浏览器 | jq-web 按 `acceptance/browser-plan.md`（zh-CN / en × 1440 / 390） | 联合查询 5 个入口出图与分组、键类型页、模板编辑器联合查询页签；无 5xx、无原始 key、390 无横向滚动；回归页可打开 |

验收入口（阶段 6 在 mor-test 只读模拟，`precheck/demo-chains-mortest-20261030.out`）：

| 入口 | 值 | 模拟结果 |
|---|---|---|
| 交易订单号 | `ord_3LOyZii4ct3BgT48vPZhmHbyC302a8`（税务阶段 7 验收订单，115 USD） | 5 层 24 次执行：订单 → 收银台会话 / 计费事实 → 记账批次、平台流水 → 账户分录、会计凭证 → 凭证分录（6 行）；支付引用是合成 ID，支付节点 0 行 |
| 支付意图 ID | `239094951181373440`（PAYERMAX 3DS 成功支付） | 4 层 16 次：意图 → 尝试 → 2 个动作 → 渠道交易引用 `T2026102218153708006502`；无订单 / 计费 |
| 退款单号 | `rfd_3L5B25y5BGmxTcJDRyZcBx8OCN9BJK` | 3 层 9 次：退款申请 → 订单（PAID）→ 收银台会话；渠道退款未建 |
| 渠道交易号 | `T2026102218153708006502` | 5 层 19 次：渠道交易引用 → 动作 → 尝试 → 意图（上游方向） |
| 会计凭证 ID | `241801561934753792` | 4 层 23 次：凭证 / 分录 → 记账批次 → 计费事实 → 订单、平台流水（上游方向） |

**验收留下的数据**：3 个新数据源 + `MOR_CHANNEL_CONNECTOR_REPLICA` 启用；34 个 JQ_* 模板（长期保留作演示）；26 个键类型；data-query 执行审计；Gateway 访问日志；登记与验收结果 JSON（`demo/`、`acceptance/`）。

## 6. 文件

| 文件 | 说明 |
|---|---|
| `compose-candidate.<服务>.yaml` / `compose-rollback.<服务>.yaml`（3 + 3；ops-web 两份由 jq-web 编写） | 固定 sha256；env 与运行容器逐键逐值相同（`scripts/env_check.sh`） |
| `sql/mor_platform-forward.sql` / `-rollback.sql` | 与 `paytech/platform-support/docs/database/sql/changes/2026-10-29-joint-query{,-rollback}.sql` 逐字节相同 |
| `sql/mor_platform-block-144{,-activate,-rollback}.sql` | 与 `changes/2026-10-29-joint-query-registration-144{,-activate,-rollback}.sql` 逐字节相同；`-deactivate`、`-verify`、`-precheck`、`-verify-after` 为发布专用 |
| `sql/root-demo-reader-grants{,-rollback}.sql` | root：三个库的库级 SELECT |
| `harness/rehearse.sh` + `BlockSnapshotCheck.java`、`jarcmp.py`、`envcmp.py`、`image_verify.sh` | 离线演练与快照编译（结果 `evidence/rehearsal-20261030T0627Z/`）、JAR / env / 镜像核对 |
| `jq_bundles.py`、`i18n-bundles/` | 语言包计划与导入文件 |
| `demo/jq_demo_spec.py`、`register_jq_demo.py`、`validate_templates.py`、`TemplateCheck.java` | 演示数据定义、登记脚本（本机只读演练 `demo/local-dryrun/`）、模板校验 |
| `acceptance/jq_api.py`、`acceptance/browser-plan.md` | 第 5 节 |
| `scripts/source_drift_check.sh`、`scripts/env_check.sh` | ① 复核 |
