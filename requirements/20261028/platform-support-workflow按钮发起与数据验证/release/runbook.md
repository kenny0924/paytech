# REQ-05 workflow 按钮发起与数据验证 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-29）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root DDL、注册块、XXL 写、语言包发布、data-query 登记、验收写数据）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权（"你帮我执行吧"）。
> 依据：[technique.md](../technique.md) 第 9.3 节部署 / 回滚顺序、第 10.4 节 XXL；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`wfdq-20261029a` tag 仅供辨认）。长时间执行用 `caffeinate -dimsu` 包裹或另开 `caffeinate -dimsu -w <pid>`。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 3 个候选镜像已构建并核对（stage6-report 第 3 节）；Gateway、card、其他服务**不换镜像** | 已就绪 |
| P-2 | 执行前重读现网镜像：三个服务应仍是第 1 节回滚列；若有人替换过，停止，在新基线上重建（回滚文件也要改写） | 执行当天 |
| P-3 | 候选源码未漂移：`scripts/source_drift_check.sh` 输出 `all 145 files unchanged` 与 `no new files`；wfdq-web 若在阶段 6 之后改过 ops-web（如浏览器走查修复），先重建 ops-web 镜像与 compose 文件 | 执行当天 |
| P-4 | 阶段 7 开始时通知并行会话 **code-mor-2d**（及其他活跃会话）：Platform 重启约 60 秒（运营控制台登录 / 会话、IAM、文件、语言包 manifest、Gateway 快照刷新、Platform XXL 与补偿均受影响；Gateway 保留最后一份快照，现有路由继续可用）、ops-bff 约 30 秒、ops-web 数秒；注册块 142；XXL 新任务；operations-console #21 | 执行当天，主会话 |
| P-5 | 待裁决 D-1（stage6-report）：语言包废弃 13 个旧 workflow key（第 ⑩ 步需 `--accept-deprecations`） | 待用户裁决 |
| P-6 | 验收账号：运营管理员（`test-environment/private/access.md`，脚本读取，不打印） | 已就绪 |

## 1. 现网基线与候选（阶段 6 读取）

| 服务 | 现网镜像（= 回滚目标） | 候选镜像（`wfdq-20261029a`） |
|---|---|---|
| platform-support-service | `sha256:1e54f3498feeaf7831c4fe41247cabda8ce1982d9e872bdc716d49c84f37256d`（refclient-20261028b，JAR `e8eedf4d`） | `sha256:7671724696de0756d1884f64fb2478505f1d775588e1a5e80bd4febe890d567a`（JAR `0345c0dd`） |
| operations-console-bff | `sha256:eca2df8231f999f3f977a830ad8baa758449aa8bceee12234da28d77ae3762b8`（gate-20261029a，JAR `2534a1fa`） | `sha256:6833f484994be41f72ee3fb3ee73006514b9089e5a5d4330af6e3bc245c597bd`（JAR `5cee8709`） |
| operations-console-web | `sha256:8aec3df8901f83e7271e2f83f3eac94884bef3acbc83d26bf7c406fd3870c959`（gate-20261029b，dist 253） | `sha256:96992aba577492309ef4932e2b68cc507bdd587b26c8edb40c7127967ed0312d`（dist 255） |

mor_platform：RUNNING 带键实例 3、重复 0；最大块 141、路由 ACTIVE 616 / DISABLED 6；XXL 分组 2 在线；operations-console #20；`MOR_COMMERCIAL_REPLICA` ACTIVE。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR-wfdq/requirements/20261028/platform-support-workflow按钮发起与数据验证   # 提交后改为主树同名目录
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`docker logs --since <T> mor-test-<服务>-1 2>&1 | grep -E 'ERROR|Exception'`。**任一步"失败即停"条件成立：停止前进，按第 5 节回滚到该步之前的状态。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核 | P-4 通知；`for s in platform-support-service operations-console-bff operations-console-web; do echo "$s $(img $s)"; done`；`$REQ/release/scripts/source_drift_check.sh`；`$REQ/release/scripts/env_check.sh` | 三个镜像 = 第 1 节回滚列；源码未漂移；env_check 6 行全部 `missing=[] extra=[] valuediff=[]`。否则停（P-2 / P-3） |
| ② | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/wfdq-20261029/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_platform > "$B/mor_platform.sql"`；`… mysqldump … xxl_job xxl_job_info xxl_job_group > "$B/xxl.sql"`；①的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空且以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro mor_platform < $REQ/release/sql/mor_platform-precheck.sql` | 与 baseline 第 3、4 节相同：重复 0 行；新列 / 索引 0 / 0；DATA_QUERY 模板 / 运行 0 / 0；块 142 区间与新 code / 路径 0；依赖 ACTIVE；路由 ACTIVE 616。否则停 |
| ④ | **前滚 DDL**（旧 Platform 仍在运行） | `sqlr mor_platform < $REQ/release/sql/mor_platform-forward.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-verify-after.sql` | 秒级（演练 0.18 s）；第 0 步预检段 0 行；`running_with_key = slotted`（3）、`non_running_slotted = 0`；`business_key` 为 `utf8mb4_bin`；两个新索引存在。SQL 报错 → 停，执行 `mor_platform-rollback.sql`。旧代码在新结构上照常读写（新列可空或有默认值） |
| ⑤ | **Platform 新镜像** | `T5=$(date -u +%FT%TZ); up platform-support-service`；随后再执行一次 `sqlr mor_platform < $REQ/release/sql/mor_platform-forward.sql`（只回填 ④—⑤ 之间旧代码新建的 RUNNING 实例槽位；可重跑） | `img` = `76717246…`，healthy；日志无 ERROR；`xxl_job_registry` 中 `platform-support-service` 心跳晚于 T5；`gwlog $T5` 快照刷新恢复成功（重启期间失败属预期，Gateway 保留旧快照）；运营控制台可登录。180 秒不 healthy → 第 5 节 R3—R4 |
| ⑥ | ops-bff | `up operations-console-bff` | `6833f484…` healthy；运营控制台工作流列表 / 详情、对账页、合同页不 503 |
| ⑦ | 注册块：休眠 → 编译 → 激活 | `T7=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/release/sql/mor_platform-block-142.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-142-verify.sql`；等 10 秒 `gwlog $T7`（休眠行也参与编译，阶段 6 已离线编译通过）；`sqlr mor_platform < $REQ/release/sql/mor_platform-block-142-activate.sql` | 注册：routes 3（DISABLED）、headers 14、queries 3、guards 18；快照刷新无 `CONTROL_PLANE_UNAVAILABLE`、routeCount 仍 616；激活后 5 秒内 routeCount 616 → **619**；未登录 `curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Origin: http://localhost:28080' http://localhost:28080/api/operations/workflow-admin/v1/action-templates/validation` 返回码与已有写路由（如 `…/workflow-admin/v1/action-templates`）相同（激活前为 404）。快照刷新失败 → `…-block-142-deactivate.sql` + `…-block-142-rollback.sql` 立即恢复 |
| ⑧ | ops-web | `up operations-console-web` | `96992aba…` healthy；`docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f \| wc -l'` = 255；工作流页面加载 |
| ⑨ | XXL 登记 | 第 4 节 | 任务 1 行、启用；手动触发 1 次 handle 200、日志 `workflow data-query recheck: checked 0, …`（此时尚无 DATA_QUERY 动作） |
| ⑩ | 语言包 | 计划（只读）`python3 -I $REQ/release/wfdq_bundles.py --accept-deprecations`；发布同一命令加 `--apply` | 计划与阶段 6 相同：当前 **#20**、新增 123、基准变更 4、废弃 13（只在 workflow）、zh-CN 导入 127（只在 workflow）、命名空间 26、`zhChangedNotImported` 0。当前号不符或出现 workflow 以外的变化即停。发布后 **#21** READY 且为当前；匿名 manifest 逐包 200。Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 就不重复发布 |
| ⑪ | data-query 与验收定义登记 | `E2E_TARGET=test python3 -I $REQ/release/acceptance/req05_api.py setup --yes`（第 6 节） | 数据源 `MOR_COMMERCIAL_REPLICA` ACTIVE（不新建、不改）；3 条 GOLIVE_* 模板 ACTIVE（data-query 保存时做只读守卫与模板解析，失败即停）；4 个动作模板；`ACC_REQ05_GOLIVE_COMMERCIAL` 草稿无问题并发布 |
| ⑫ | 验收 | 第 6 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-4 的会话已完成 |

**窗口影响**：⑤ Platform 重启约 60 秒（运营 / Client 控制台登录与会话接口、IAM、文件、语言包 manifest、Platform 补偿与 XXL；Gateway 快照刷新失败但保留旧快照）；⑥ ops-bff 约 30 秒运营接口 503；⑤—⑧ 之间旧 ops-web 不使用新接口，不受影响；⑦ 激活到 ⑧ 之间新路由已开但旧页面不调用；⑧ 到 ⑩ 之间新页面的新文案 zh-CN 显示内置英文（数分钟）。

## 4. XXL 登记（⑨）

经 Admin API（`http://127.0.0.1:28090`）登录后调用，不直接写 `xxl_job` 库：`python3 -I $REQ/release/scripts/xxl_workflow.py plan` → `apply` → `trigger`。时机：⑤ 之后，`platform-support-service` 心跳晚于新容器启动、分组 2 `address_list` 非空（约 30 秒）。Admin 累计重启 158 次，登录失败先等其 healthy。

| 字段 | 值 |
|---|---|
| `jobGroup` | `2`（platform-support-service；脚本核对 id，不新建分组） |
| `jobDesc` / `author` / `alarmEmail` | `Workflow DATA_QUERY 系统检查定时重查（REQ-05）` / `code-mor` / 空 |
| `scheduleType` / `scheduleConf` | `CRON` / `0 */5 * * * ?`（每 5 分钟，UTC） |
| `glueType` / `glueRemark` / `glueSource` | `BEAN` / `BEAN` / 空 |
| `executorHandler` / `executorParam` | `workflowDataQueryRecheck` / 空 |
| `executorRouteStrategy` | `SHARDING_BROADCAST`（Handler 按实例 ID 取模分片；单实例时 1/1） |
| `executorBlockStrategy` / `executorTimeout` / `executorFailRetryCount` / `misfireStrategy` | `DISCARD_LATER` / `300` / `0` / `DO_NOTHING`（失败动作由下一轮重试） |
| 启用 | 登记后 `start`；`trigger` 1 次核对 |

回滚：`xxl_workflow.py remove`（只删本任务；不动分组与任务 2）。旧 Platform 镜像没有该 Handler，回滚 Platform 前必须先 remove。

## 5. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy 或快照编译失败时）

| 顺序 | 动作 | 说明 |
|---|---|---|
| R0 | `xxl_workflow.py remove` | 旧镜像无 Handler，调度会报错 |
| R1 | `upr operations-console-web` | 回到 `8aec3df8`（REQ-01）。语言包不回退：旧页面不再引用新 key；13 个废弃 key 在旧页面退回内置英文（D-1） |
| R2 | `sqlr mor_platform < …-block-142-deactivate.sql` → `sqlr mor_platform < …-block-142-rollback.sql` | 删除 38 行；快照 routeCount 回到 616 |
| R3 | `upr operations-console-bff` | 回到 `eca2df82` |
| R4 | `upr platform-support-service` | 回到 `1e54f349`；旧代码在前滚结构上照常运行（新列可空 / 有默认值；`business_key` 改为区分大小写排序，旧代码只做等值检索） |
| R5 | DDL 回滚（可选，旧镜像运行后） | 先处理仍 PENDING 的 DATA_QUERY 动作（`mor_platform-rollback.sql` 第 0 段列出；按需在运营控制台终止所在实例）→ `sqlr mor_platform < $REQ/release/sql/mor_platform-rollback.sql` → `sqlro mor_platform < …-precheck.sql`（新列 / 索引回到 0 / 0）。阶段 6 演练：回滚后与导出 0 行差异、可重跑 |
| 注意 | 只回滚 Platform 镜像、保留前滚结构后再次前滚 | 旧代码完成 / 终止实例时不清 `active_slot`，再上新代码前先执行 `UPDATE t_workflow_instance SET active_slot = NULL WHERE status <> 'RUNNING' AND active_slot IS NOT NULL;`，再重跑 `mor_platform-forward.sql` 回填 |

只回退 ops-web 可行（新页面下线，接口保留）；只回退注册块可行（立即检查 / 模板选择 / 配置校验 404，发起与系统检查照常）；回退 Platform 而保留新 ops-bff / ops-web 不可行（新页面调用旧 Platform 不存在的接口与字段）。

## 6. 验收（⑪—⑫）

验收数据由阶段 6 在 mor-test 只读推演（`precheck/golive-templates-mortest-20261029.out`）：

| 标签 | Client（合同） | 预期 |
|---|---|---|
| complete | `237575207592763393`（DEMO-CL-001） | N3 / N4 / N8 全部系统完成 → COMPLETED（APPLE_PAY、CARD 已开通） |
| stuckAtCollection | `237573267559391233`（DEMO2-CL-103） | 停在 N8：`CAPABILITY_NOT_ENABLED`，`pending_capabilities = GOOGLE_PAY,STC_PAY` |
| stuckAtActive | `237573382420406273`（DEMO2-CL-102） | 停在 N4：`TERM_VERSION_NOT_ACTIVE`（价格版本 APPROVED） |
| notDrafted | `237573699337822209`（DEMO-CL-004） | 停在 N3：`AGREEMENT_NOT_DRAFTED`（合同 DRAFT） |

mor-test 全部合同为 MOR，没有 PSP 分支数据；也没有慢查询 fixture 库与受限账号——PSP 默认边、`QUERY_TIMEOUT`、检查中终止、权限 403 / 404 只在阶段 5 本机验证（38 / 38）。

| # | 内容 | 命令 / 方式 | 通过条件 |
|---|---|---|---|
| A1 | 登记 | ⑪ `req05_api.py setup --yes` | 见 ⑪ |
| A2 | 只读核对 | `E2E_TARGET=test python3 -I $REQ/release/acceptance/req05_api.py check` | 模板选择 3 条带 versionNo / timeoutSeconds / 参数；配置校验 4 种情况与 itemKey 定位正确 |
| A3 | 运行场景 | `… req05_api.py scenario --yes` | 4 个 Client 按上表；同键重复 created=false；编码不存在 404；二选一 400；我的待办 actionType=DATA_QUERY；立即检查返回检查块；DATA_QUERY 人工完成 400 |
| A4 | 定时重查 | A3 之后等待 ≥ 5 分钟（XXL 一轮 + `recheck-min-age` 4 分钟）→ `… req05_api.py recheck` | 3 个未满足动作最近检查时间前进、仍未满足；XXL 日志 `checked 3`（或更多） |
| A5 | 浏览器 | 以阶段 5 `verification/stage5/stage5_browser.mjs` 改写 mor-test 版（入口 `http://localhost:28080`，凭据读 `test-environment/private/access.md`，ID 取 A1 / A3 结果 JSON），zh-CN / en × 1440 / 390 | 截图对照原型 17—20：按编码发起、DATA_QUERY 动作配置与校验（保存前只调校验接口）、检查状态（未满足 / 已满足 / 系统完成）、立即检查、我的待办动作类型；无 5xx、无原始 `workflow.*` key、390 无横向滚动；回归页（合同详情开通项、对账、卡 BIN 库、参考数据）可打开 |
| A6 | 收尾 | `… req05_api.py cleanup --yes`（按用户意见；也可保留 RUNNING 实例作演示） | 3 个 RUNNING 验收实例 TERMINATED |

**验收留下的数据**：3 条 GOLIVE_* 查询模板（数据源 `MOR_COMMERCIAL_REPLICA`，长期保留，供 REQ-00 编排复用）；4 个动作模板 `REQ05_*`；流程定义 `ACC_REQ05_GOLIVE_COMMERCIAL` v1；4 个实例（1 COMPLETED，3 RUNNING 或收尾后 TERMINATED）及其节点 / 动作运行、活动记录、通知补偿任务；data-query 执行审计；XXL 任务 1 个及其日志；Gateway 访问日志。`req05_api.py` 结果 JSON 在 `acceptance/`；本机演练结果在 `acceptance/local-dryrun/`。

## 7. 文件

| 文件 | 说明 |
|---|---|
| `compose-candidate.<服务>.yaml` / `compose-rollback.<服务>.yaml`（3 + 3） | 固定 sha256；env 与运行容器逐键逐值相同（`scripts/env_check.sh`） |
| `sql/mor_platform-forward.sql` / `-rollback.sql` | 与 `paytech/platform-support/docs/database/sql/changes/2026-10-29-workflow-data-query{,-rollback}.sql` 逐字节相同 |
| `sql/mor_platform-precheck.sql` / `-verify-after.sql` | 只读 |
| `sql/mor_platform-block-142{,-verify,-activate,-deactivate,-rollback}.sql` | 块 142：3 路由 / 14 header / 3 query / 18 guard（注册与激活、回滚与需求 `db/registration-142*.sql` 逐字节相同） |
| `harness/rehearse.sh` + `BlockSnapshotCheck.java` | 离线演练与快照编译（阶段 6 结果 `evidence/rehearsal-20261029T0855Z/`） |
| `wfdq_bundles.py`、`i18n-bundles/` | 语言包计划与导入文件；key 清单 `i18n-bundles/workflow-key-changes.tsv` |
| `scripts/xxl_workflow.py` | 第 4 节 |
| `scripts/source_drift_check.sh`、`scripts/env_check.sh`、`harness/image_verify_java.sh` | ① 复核 |
| `acceptance/req05_api.py` | 第 6 节（本机演练 `acceptance/local-dryrun/`） |
