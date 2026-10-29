# REQ-12 业务信息补采 · mor-test 发布运行卡（阶段 6 准备 2026-10-29；阶段 7 由主会话执行）

> **阶段 7 前提：用户明确授权**容器停 / 起与 mor-test SQL 写入（含语言包发布、验收注册账号的挑战改写）。本卡全部命令由主会话执行；子会话只做只读核对与记录。不提交、不推送。
> 依据：[technique.md](../technique.md) 第 7—13 节；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。模板：REQ-05、国家包法务修订 release。

## 0. 摘要

| 服务 / 数据 | 现网（回滚目标） | 候选（tag `rq12-20261029a`） |
|---|---|---|
| client-master-service | `sha256:c9bb7b8e30f6a44ae528b5054fd15a67e52a52743fc4fbed11436b08f21b8155` | `sha256:2b12c2e2184bb9e926c5fb6a74822c103ba5a659a53dcbacb9a4d6bb60c414bc`（JAR `ca0b61fb`） |
| client-console-web | `sha256:3db62571940f24d50c87fa2e07143e3536a2b956ffb6db8542994bcd5011a9eb` | `sha256:e17a9a0a43b936ebc2344338df1e934f6cece672081385c2841b0bde358f0cf0`（dist 11） |
| operations-console-web | `sha256:96992aba577492309ef4932e2b68cc507bdd587b26c8edb40c7127967ed0312d` | `sha256:0b456b8aed53df0d31719f995a755d8f74d1abbd2a8b05e6de53ef5e0c34ae72`（dist 256） |
| client-console-bff / operations-console-bff / commercial / gateway | 不发布（baseline 1.2） | — |
| `mor_client_master` | v2 ACTIVE 9、字段 602、无新列 | DDL 4 列 → v3 ACTIVE 9（v2 RETIRED）、字段 965、`FULFILLMENT_TYPE` 4 项 |
| 语言包 | client-console **#7**、operations-console **#21** | #8（+23）、#22（+22） |

**顺序（不可调换）**：DDL → client-master → client-console-web → operations-console-web → 两个语言包 → v3 DML（先 DRAFT 核对，再 ACTIVE）。理由：旧 client-master 不认识 `MULTI_SELECT`（按枚举映射 `widget_code`，读到即失败）；旧门户无法渲染多选；新文案在 DML 前就位。DDL 可先于镜像（新列可空、旧代码不读写）。

## 1. 演练与只读核对结论（阶段 6）

- 离线演练（`harness/rehearse.sh`，mor-test 只读导出 → 本机临时库）：DML 先于 DDL 被拦（STOP）→ DDL / 重跑 → DML DRAFT → 核对 → ACTIVE → 重跑空操作 → 有 v3 申请时 DML 回滚与 DDL 回滚均拒绝 → 回滚 / 重跑 → DDL 回滚 → 再前滚；最终配置表与候选 `schema.sql + initial-data.sql` 全新建库 **0 行差异**（`evidence/rehearsal-20261029T125241Z/summary.txt`）。
- 现网 v2 BUSINESS 最大序号与 DML 静态值相同（D-2 关闭）。
- commercial ACTIVE 支付方式 4 项（STC_PAY、APPLE_PAY、GOOGLE_PAY、CARD）。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR-rq12/requirements/20261028/client-master-业务信息补采   # 提交后改为主树同名目录
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
caffeinate -dims -w $$ &   # 发布期间防止休眠
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`docker logs --since <T> mor-test-<服务>-1 2>&1 | grep -E 'ERROR|Exception'`。**"失败即停"成立即停止前进，按第 4 节回滚到该步之前。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核 | 通知并行会话（只在阶段 7）：client-master / client-console / operations-console 将依次重启；`for s in client-master-service client-console-web operations-console-web client-console-bff operations-console-bff; do echo "$s $(img $s)"; done`；`$REQ/release/scripts/source_drift_check.sh`；`$REQ/release/scripts/env_check.sh` | 镜像 = 第 0 节现网列（client-console-bff `8fe7f24c…`、ops-bff `6833f484…`）；源码未漂移、main 未前进（否则重建候选）；env_check 6 行全部 `missing=[] extra=[] valuediff=[]` |
| ② | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/rq12-20261029/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_client_master > "$B/mor_client_master.sql"`；①的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空、以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro mor_client_master < $REQ/release/sql/00-client-master-bizinfo-precheck.sql` | 与 baseline 第 3 节相同：新列 0、ID 段 0、保留目录 0、新 field_key 0；ACTIVE 9（`…900301—309`）、字段 602；BUSINESS 最大序号 2190 / 2200 / 2240 / 2270 / 2200 / 2250 / 2190 / 2240 / 2250。否则停 |
| ④ | **前滚 DDL**（旧 client-master 仍运行） | `sqlr mor_client_master < $REQ/release/sql/10-client-master-bizinfo-ddl.sql` | 秒级；4 列在 `business_description` 之后、可空、无默认；3 处注释为新文本。SQL 报错 → 停（可重跑；只存在部分列时脚本 STOP，人工核对） |
| ⑤ | **client-master 新镜像** | `T5=$(date -u +%FT%TZ); up client-master-service` | `img` = `2b12c2e2…`，healthy（约 40 秒；期间门户入驻与运营审核读取 503）；日志无 ERROR；门户既有在途草稿可打开（form-schema 仍为 v2，不调用 commercial） |
| ⑥ | client-console-web | `up client-console-web` | `e17a9a0a…` healthy；`docker exec mor-test-client-console-web-1 sh -c 'find /usr/share/nginx/client-console -type f \| wc -l'` = 11；门户登录、入驻页加载 |
| ⑦ | operations-console-web | `up operations-console-web` | `0b456b8a…` healthy；文件数 256；审核工作台、Client 详情、工作流页面加载（REQ-05 页面不回退） |
| ⑧ | 语言包 | 计划（只读）`python3 -I $REQ/release/rq12_bundles.py --app client-console`、`… --app operations-console`；发布同一命令加 `--apply` | 计划与阶段 6 相同：client-console 当前 **#7**、新增 23（onboarding-schema 17、onboarding 6）；operations-console 当前 **#21**、新增 22（client-review 9、onboarding-schema 13）；基准变更 / 废弃 / 恢复 0；不新建命名空间；`zhChangedNotImported` 0。当前号不符或出现其他命名空间变化即停。发布后 **#8 / #22** READY 且为当前；匿名 manifest 200。Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 不重发 |
| ⑨ | **v3 DML：DRAFT 写入核对** | `{ echo "SET @cm_bizinfo_status='DRAFT';"; cat $REQ/release/sql/20-client-master-bizinfo-v3.sql; } \| sqlr mor_client_master`；`sqlro mor_client_master < $REQ/release/sql/30-client-master-bizinfo-verify.sql` | 预检段全 0（ddl_columns 4）；v3 9 行 DRAFT（active_slot NULL）、v2 9 行仍 ACTIVE、v3 字段 363、`FULFILLMENT_TYPE` 4 行 DRAFT；新字段级别：退款政策机构 EVENTUALLY ×4、个人 OPTIONAL ×5；门户新建申请仍得 v2 |
| ⑩ | **v3 DML：ACTIVE 发布** | `sqlr mor_client_master < $REQ/release/sql/20-client-master-bizinfo-v3.sql`；`sqlro mor_client_master < $REQ/release/sql/30-client-master-bizinfo-verify.sql` | v3 9 行 ACTIVE / 1、v2 9 行 RETIRED、字段 965、目录 ACTIVE；重跑为空操作。在途 v1 申请照常（冻结版本不变） |
| ⑪ | API 验收 | `python3 -I $REQ/release/acceptance/rq12_api.py --env test` | 全部 PASS（本机演练 32 项；mor-test 支付方式期望值由只读 SQL 取，含 STC_PAY 共 4 项）。注册邮件收不到时沿用 `mint_signup_link`（改写本次新账号自己的注册挑战，同法务修订验收）；两份草稿最后撤回 |
| ⑫ | 浏览器走查 | `acceptance/browser-plan.md` | en / zh-CN × 1440 / 390，门户与运营；全部通过后更新 delivery、CURRENT_IMPLEMENTATION；通知 ① 的会话已完成 |

**窗口影响**：⑤ client-master 重启约 40 秒（门户入驻、运营审核 / Client 详情、Checkout 候选解析 / 卡存储设置等 client-master 接口 503；MQ 消费在重启后继续）；⑥ ⑦ nginx 秒级；⑧ 到 ⑩ 之间新页面已有新文案、尚无新字段；⑩ 之后新申请才出现四个字段。

## 4. 回滚

| 场景 | 动作 | 注意 |
|---|---|---|
| R1 ⑩ 之后、尚无 v3 申请 | `sqlr mor_client_master < $REQ/release/sql/90-client-master-bizinfo-v3-rollback.sql` | 删除 v3 字段 / Schema 与 `FULFILLMENT_TYPE`，v2 恢复 ACTIVE / 1。**一旦存在引用 v3 的申请或任一资料已填交付方式，脚本拒绝（STOP），只能向前修复**（演练已验证拒绝分支） |
| R2 ⑥ / ⑦ Web 回退 | `upr client-console-web` / `upr operations-console-web` | 须先完成 R1（旧门户不认识 `MULTI_SELECT`）；ops-web 回滚目标是 REQ-05 镜像，不会回退 REQ-05 |
| R3 ⑤ client-master 回退 | `upr client-master-service` | 须先完成 R1（旧镜像读到 v3 字段的 `MULTI_SELECT` 会失败）；DDL 可保留 |
| R4 ④ DDL 回退 | `sqlr mor_client_master < $REQ/release/sql/91-client-master-bizinfo-ddl-rollback.sql` | 须先 R1；删除 4 列会丢失已采集数据（执行前看脚本输出的 `rows_with_collected_values`）。一般保留新列 |
| R5 语言包 | 以 6753faba 干净 worktree（两个 Web dist 与现网逐字节相同）重新发布：`rq12_bundles.py --app … --web-root <6753faba worktree> --apply` | 本次只新增 key，旧 Web 读新包无影响，一般不回滚 |

## 5. 失败即停清单

- ① 任一镜像与第 0 节不符（他人已换）、源码漂移、env 不等 → 停，重新核对 / 重建候选。
- ③ 预检任一读数不符 → 停，查清是否已被执行或数据变化。
- ⑤ 180 秒不 healthy 或日志 ERROR → `upr client-master-service`（此时尚无 DML，可直接回退；DDL 保留）。
- ⑧ 计划与第 3 节不符 → 不发布，先查线上语言包变化。
- ⑨ 核对不符 → 不执行 ⑩；DRAFT 行用 R1 删除（DRAFT 不被新申请使用，回滚不会被拒）。
- ⑪ 任一 FAIL → 先判定是否本需求缺陷；新申请已用 v3 后只能向前修复。
