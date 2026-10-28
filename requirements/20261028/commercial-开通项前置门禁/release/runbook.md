# REQ-01 开通项前置门禁 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-28）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root SQL、Platform 注册块、XXL 写、语言包发布、验收写数据）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权；data-query 附录（第 8 节）另行单独授权。
> 依据：[technique.md](../technique.md) 8.3 部署顺序、14 注册与 data-query、15.3；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`gate-20261029a` tag 仅供辨认）。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 3 个候选镜像已构建并核对（stage6-report 第 3 节）；Platform、Gateway、compliance **不换镜像** | 已就绪 |
| P-2 | 执行前重读现网镜像：三个服务应仍是第 1 节回滚列。若有人在此期间替换过（尤其法务修订会话的 ops-bff / ops-web），停止，改为在新基线上重建（回滚文件也要改写） | 执行当天 |
| P-3 | 通知并行会话（法务修订 / 入驻 / 主会话以外的活跃会话）：将停 commercial 约 30—60 秒（运营商业页、商业接口、commercial 补偿与 XXL 执行器）、重启 ops-bff 约 30 秒（运营控制台全部接口）、ops-web 数秒；发布 operations-console #20；新增 MQ 队列 `commercial.compliance.*`、XXL 任务 2 个、注册块 141。并请法务修订会话：其 `release/compose-candidate.operations-console-{bff,web}.yaml` 发布后不要再执行（会回退本需求），阶段 8 改指本需求镜像 | 执行当天，主会话 |
| P-4 | 待裁决 D-1（stage6-report）：scope=ALL 每日任务首次运行会把没有 ACTIVE CLIENT_PRICING 版本的 4 份合同上 6 个支付方式开通项改为"前置失效暂停"。裁决前第 ⑩ 步只登记并启用 scope=PENDING，scope=ALL 登记为**停用** | 待用户裁决 |
| P-5 | 待裁决 D-2：语言包废弃 4 个旧 key（`commercial.enum.capabilityStatus.{ENABLED,SUSPENDED,DISABLED}`、`commercial.productSections.capabilityCount`）；第 ⑪ 步需 `--accept-deprecations` | 待用户裁决 |
| P-6 | 验收账号：运营控制台用 `test-environment/private/access.md` 的管理员（脚本读取，不打印） | 已就绪 |

## 1. 现网基线与候选（阶段 6 读取）

| 服务 | 现网镜像（= 回滚目标） | 候选镜像 |
|---|---|---|
| commercial-service | `sha256:e5c1ee6b0d037c4c899f43bf3583dcfdea8175f408f661d6f51580a36d3dd28c`（relsnap-20261025a，JAR `1dae75ab`） | `sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728`（JAR `40be77e2`） |
| operations-console-bff | `sha256:769edbcebd219db68eb9ea41514606f966329a16a458e1d62ea937634e8f924b`（法务修订，JAR `6045fdab`） | `sha256:eca2df8231f999f3f977a830ad8baa758449aa8bceee12234da28d77ae3762b8`（JAR `2534a1fa`） |
| operations-console-web | `sha256:45ee11cb4e8c788d557973c40bc5bea9bbc379b610c4aed8e84b9f65d34c81db`（法务修订，dist 250） | `sha256:7a053544076e82e1f2a016f3b64cd4324d26a6df65c64177a7f627cb306b80ad`（dist 253） |

mor_commercial：`t_agreement_capability` 15 行全部 ENABLED；mor_platform：最大块 140、路由 ACTIVE 613 / DISABLED 6；语言包 operations-console #19；XXL commercial 分组 id 14。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
stopc() { ./test-environment/compose.sh stop "$@"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`docker logs --since <T> mor-test-<服务>-1 2>&1 | grep -E 'ERROR|Exception'`。**任一步"失败即停"条件成立：停止前进，按第 5 节回滚到该步之前的状态。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核基线、发布前记录 | P-3 通知；`for s in commercial-service operations-console-bff operations-console-web; do echo "$s $(img $s)"; done`；`E2E_TARGET=test python3 -I $REQ/release/acceptance/gate_api.py before` | 三个镜像 = 第 1 节回滚列，否则停（P-2）。`before` 记录 15 个开通项（旧值）与 7 个 Client 的 Checkout 解析开通项集合，全部检查 PASS |
| ② | 回滚文件按现网复核 | `$REQ/release/scripts/env_check.sh` | 6 行全部 `missing=[] extra=[] valuediff=[]`，回滚行镜像 = ① 读数；否则停 |
| ③ | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/gate-20261029/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF mor_commercial t_agreement_capability > "$B/mor_commercial.t_agreement_capability.sql"`；整库 `--databases mor_commercial mor_platform` 一份 `> "$B/full.sql"`；`docker exec … mysqldump … xxl_job xxl_job_info xxl_job_group > "$B/xxl.sql"`；①的镜像读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 导出非空且以 `Dump completed` 结尾 |
| ④ | 只读预检 | `sqlro < $REQ/release/sql/mor_commercial-precheck.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-141-precheck.sql` | 与 baseline 第 3、5 节相同：execution_status 列 0、15 行 ENABLED、消费表 0；块 141 区间 0、新 code / 路径 0、依赖 ACTIVE、同形路由属性一致；否则停 |
| ⑤ | **停 commercial → 迁移**（窗口开始） | `T5=$(date -u +%FT%TZ); stopc commercial-service`；`sqlr < $REQ/release/sql/mor_commercial-migrate.sql`；`sqlro < $REQ/release/sql/mor_commercial-verify-after.sql` | 迁移秒级（演练 0.32 s）；核对：`ACTIVE / ENABLED 15`、violations 0、lookup 索引第 3 列 `execution_status`、新增 `idx_agreement_capability_execution`、消费表 2。SQL 报错 → 停，执行 `mor_commercial-rollback.sql` 后 `upr commercial-service` |
| ⑥ | **起 commercial 新镜像**（紧接 ⑤） | `up commercial-service` | `img` = `806043a3…`，healthy；日志无 ERROR；`docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues -p /mor-test name consumers \| grep commercial.compliance` 出现 `p0..p3`（各 1 消费者）与 `dlq`；`list_bindings` 出现 `compliance.events → commercial.compliance.hash`；XXL 分组 14 心跳晚于 T5。180 秒不 healthy → 第 5 节 R3—R4 |
| ⑦ | ops-bff（紧接 ⑥） | `up operations-console-bff` | `eca2df82…` healthy；运营合同列表 / 详情 / 对账页不 503 |
| ⑧ | ops-web（紧接 ⑦，窗口结束） | `up operations-console-web` | `7a053544…` healthy；首页加载，入口 JS 与候选 dist 一致（`docker exec mor-test-operations-console-web-1 ls /usr/share/nginx/operations-console/assets \| wc -l` 与候选相同） |
| ⑨ | 发布后核对（不写数据） | `E2E_TARGET=test python3 -I $REQ/release/acceptance/gate_api.py after` | 15 个开通项全部 `ACTIVE + ENABLED`；7 个 Client 的 Checkout 解析与 ① 相同。任一 FAIL → 停，评估回滚（第 5 节全部） |
| ⑩ | 注册块：休眠 → 编译 → 激活 | `sqlr mor_platform < $REQ/release/sql/mor_platform-block-141.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-141-verify.sql`；等 10 秒看 Gateway 日志无 `CONTROL_PLANE_UNAVAILABLE` / 快照刷新失败（休眠行也参与编译，阶段 6 已离线编译通过）；再 `sqlr mor_platform < $REQ/release/sql/mor_platform-block-141-activate.sql` | 注册：route 3（DISABLED）、header 18、guard 18、shape_diff 0 / 0；激活 `routes=3`，5 秒内 Gateway 快照 routeCount 613 → **616**；未登录 `curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Origin: http://localhost:28080' http://localhost:28080/api/operations/commercial-admin/v1/agreements/1/prerequisite-checks` 的返回码与对已有路由 `…/agreements/1/activate` 的同样请求相同（激活前应不同）。快照刷新失败 → `…-deactivate.sql` + `…-rollback.sql` 立即恢复 |
| ⑪ | XXL 登记 | 第 4 节 | 两个任务 `xxl_job_info` 各 1 行；scope=PENDING 启用并手动触发 1 次 handle 200、日志 `capability prerequisite recheck: checked …`；scope=ALL 按 P-4 裁决（缺省停用） |
| ⑫ | 语言包 | 计划（只读）`python3 -I $REQ/release/gate_bundles.py --accept-deprecations`；发布同一命令加 `--apply` | 计划与阶段 6 相同：当前 **#19**、新增 95、基准变更 2、废弃 4（只在 commercial）、zh-CN 导入 97（只在 commercial）、命名空间 26 不变、zhChangedNotImported 0。当前号不符或出现 commercial 以外的变化即停。发布后 **#20** READY 且为当前；匿名 manifest 逐包 200。Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 就不重复发布 |
| ⑬ | 验收 | 第 6 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-3 的会话已完成 |

**窗口影响（⑤—⑧，约 2—3 分钟）**：commercial 停机期间运营商业页与 commercial 接口 503（acquiring 为 MOCK，Checkout 不受影响）；⑥ 之后到 ⑧ 之前，旧 ops-bff / ops-web 读到新取值 `ACTIVE` 与 `executionStatus`，**合同详情页可能报"响应无效"**（阶段 5 本机已复现），⑧ 完成即恢复；⑧ 到 ⑫ 之间新页面的新文案 zh-CN 显示内置英文（数分钟）；⑩ 激活前"检查前置 / 暂停 / 恢复"按钮返回 404。

## 4. XXL 登记（⑪）

经 Admin API（`http://127.0.0.1:28090`）登录后调用，不直接写 `xxl_job` 库（同订单过期收尾 release 第 7 节）：`POST /auth/doLogin`（`admin`，口令取 `.env.test` 的 `MOR_XXL_JOB_ADMIN_PASSWORD`，不回显）→ `POST /jobinfo/insert` → `POST /jobinfo/start`（`ids[]`）。时机：⑥ 之后，`xxl_job_registry` 中 `commercial-service` 心跳晚于新容器启动、分组 14 `address_list` 非空（约 30 秒）。XXL Admin 当天累计重启 127 次，登录失败先等其 healthy。

| 字段 | 任务 A：等前置重查 | 任务 B：全量复核 |
|---|---|---|
| `jobGroup` | `14`（commercial-service） | `14` |
| `jobDesc` | `Commercial 开通项前置重查（等前置 / 前置失效暂停）` | `Commercial 开通项前置全量复核（含已开通，防资格事件丢失）` |
| `author` / `alarmEmail` | `code-mor` / 空 | 同左 |
| `scheduleType` / `scheduleConf` | `CRON` / `0 */5 * * * ?`（每 5 分钟，UTC） | `CRON` / `0 0 3 * * ?`（每日 03:00 **UTC** = 07:00 +04；technique 未写时区，按容器 UTC） |
| `glueType` / `glueRemark` / `glueSource` | `BEAN` / `BEAN` / 空 | 同左 |
| `executorHandler` | `capabilityPrerequisiteRecheck` | `capabilityPrerequisiteRecheck` |
| `executorParam` | `scope=PENDING`（Handler 只认 `scope=ALL`，其余含空都按等前置处理） | `scope=ALL` |
| `executorRouteStrategy` | `SHARDING_BROADCAST`（Handler 按 XXL 分片扫合同；单实例时分片 1/1） | `SHARDING_BROADCAST` |
| `executorBlockStrategy` | `DISCARD_LATER` | `SERIAL_EXECUTION` |
| `executorTimeout` / `executorFailRetryCount` / `misfireStrategy` | `300` / `0` / `DO_NOTHING`（失败合同状态不变，下一轮重试） | `600` / `0` / `DO_NOTHING` |
| 启用 | 登记后 `start`；手动 `trigger` 1 次核对 | **按 P-4 裁决**：缺省只登记不 `start` |

回滚：`POST /jobinfo/stop` → `POST /jobinfo/remove`（`id`）；或按 ③ 的 `xxl.sql` 核对。旧 commercial 镜像没有该 Handler，回滚 commercial 前必须先停用两个任务。

## 5. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy 或快照编译失败时）

| 顺序 | 动作 | 说明 |
|---|---|---|
| R0 | XXL：停用并删除任务 A / B | 旧镜像无 Handler，调度会报错 |
| R1 | `upr operations-console-web` → `upr operations-console-bff` | 回到法务修订镜像（45ee11cb / 769edbce）。旧页面读不懂新取值：R1 到 R4 之间合同详情页报错（同发布窗口） |
| R2 | 注册块：`sqlr mor_platform < …-block-141-deactivate.sql` → `sqlr mor_platform < …-block-141-rollback.sql` | 回退脚本在路由仍 ACTIVE 时拒绝执行（ERROR 1242）；删除 39 行；快照 routeCount 回到 613 |
| R3 | `stopc commercial-service` → `sqlr < $REQ/release/sql/mor_commercial-rollback.sql` → `sqlro < $REQ/release/sql/mor_commercial-precheck.sql` | 反向映射：ENABLED → ENABLED；SUSPENDED_* → SUSPENDED；**PENDING_PREREQUISITES → SUSPENDED**（回滚后不能凭空开通，验收新增的开通项会变 SUSPENDED）；DISABLED 不变；`t_consumed_*` 两表保留（旧代码不读写）。预检回到 execution_status 列 0 |
| R4 | `upr commercial-service` | 旧镜像 `e5c1ee6b…`；`commercial.compliance.p0..p3` 失去消费者，`compliance.events` 继续向其投递并积压——需要时由主会话删除这些队列与 `commercial.compliance.hash` 绑定（另行确认，不在本卡授权内） |
| R5 | 语言包 | 不回退（发布不可变）；旧 Web 读 #20 时 4 个废弃 key 退回内置英文（若 D-2 选择废弃）。需完全恢复时以 `ac3145f4`（国家包法务修订提交）的干净 worktree 为 `--web-root` 重新发布 |

只回退 ops-web 或 ops-bff 而保留新 commercial 不可行（旧页面 / 旧 BFF 读不懂 `ACTIVE`）；只回退注册块可行（按钮 404，其余正常）。

## 6. 验收（⑬）

| # | 内容 | 命令 / 方式 | 通过条件 |
|---|---|---|---|
| A1 | 存量开通项与 Checkout | ⑨ 已做（`gate_api.py after`） | 15 项 ACTIVE + ENABLED；7 个 Client 的 Checkout 解析与发布前一致 |
| A2 | 门禁场景 | `E2E_TARGET=test python3 -I $REQ/release/acceptance/gate_api.py scenario --yes`（缺省合同 DEMO2-CL-103：CLIENT_PRICING v1 ACTIVE，原有 CARD / GOOGLE_PAY / STC_PAY） | 新增 TOKEN_STORAGE、APPLE_PAY 回执 ACTIVE + PENDING；补偿任务 ≤ 90 秒把 TOKEN_STORAGE 核对为 ENABLED；检查前置：资格 SATISFIED、渠道身份 SKIPPED，APPLE_PAY 为 ENABLED 或 PRICE_NOT_COVERED（USD）保持等前置，执行状态与逐项一致；暂停 → SUSPENDED_MANUALLY、重复暂停 400、恢复 → PENDING → ENABLED；**原有三项执行状态不变**（若被改为前置失效暂停 = 发现，停止并报告，不回滚） |
| A3 | 浏览器 | `node $REQ/release/acceptance/gate_browser.mortest.mjs`（en / zh-CN × 1440；`RUNS='[["zh-CN",390]]'` 可补窄屏） | 截图 `acceptance/screens/`；无 5xx、无原始 `commercial.*` key；执行状态徽标、"还差什么"、暂停弹窗、编辑 / 新增抽屉文案正确；回归页（入驻配置、卡 BIN 库、参考数据、对账）可打开 |
| A4 | XXL / MQ | 任务 A 手动触发 handle 200；`rabbitmqctl list_queues` 中 `commercial.compliance.dlq` 0 条 | — |
| A5 | 收尾 | `gate_api.py cleanup --yes` | TOKEN_STORAGE、APPLE_PAY → DISABLED、执行状态为空 |

**验收留下的数据**：DEMO2-CL-103 上两行开通项（TOKEN_STORAGE、APPLE_PAY，收尾后为 DISABLED，行不删除）；该合同 BCR 变更历史约 8—10 条（新增 ×2、门禁核对、暂停、恢复、核对、关闭 ×2，actor 为运营管理员或系统 0）与合同 `changeVersion` 前进；`compensation_event` 中对应的检查 / 发布任务记录；`commercial.events` 上若干条 `AgreementCapabilityStatusChanged`（无消费方，ACK_AND_WARN）；Gateway 访问日志；Checkout 解析探针请求（只读，不落库）。`gate_api.py` 与浏览器脚本的结果 JSON / 截图在 `acceptance/`。

## 7. 文件

| 文件 | 说明 |
|---|---|
| `compose-candidate.<服务>.yaml` / `compose-rollback.<服务>.yaml`（3 + 3） | 固定 sha256；env 与运行容器逐键逐值相同（`scripts/env_check.sh`） |
| `sql/mor_commercial-migrate.sql` / `-rollback.sql` | 与 `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status{,-rollback}.sql` 逐字节相同 |
| `sql/mor_commercial-precheck.sql` / `-verify-after.sql` | 只读 |
| `sql/mor_platform-block-141{-precheck,,-verify,-activate,-deactivate,-rollback}.sql` | 块 141：3 路由 / 18 header / 18 guard |
| `harness/rehearse.sh` + `BlockSnapshotCheck.java` | 离线演练与快照编译（阶段 6 结果 `evidence/rehearsal-20261028T1945Z/`） |
| `gate_bundles.py`、`i18n-bundles/` | 语言包计划与导入文件；key 清单 `i18n-bundles/commercial-key-changes.tsv` |
| `acceptance/gate_api.py`、`acceptance/gate_browser.mortest.mjs` | 第 6 节 |
| `sql/appendix-data-query-reader{,-rollback}.sql` | 第 8 节附录 |

## 8. 附录：data-query（单独授权，不与主发布混合）

前提：主发布完成（模板 3 读 `execution_status`，旧库没有该列）。

| 步 | 内容 | 说明 |
|---|---|---|
| Q1 | 只读账号 | 在 `test-environment/private/` 新建 0600 文件保存 ≥ 32 字符随机口令（不入库）；`{ echo "SET @dq_reader_password = '<从文件读取>';"; cat $REQ/release/sql/appendix-data-query-reader.sql; } \| sqlr`（脚本内经会话变量传入，不打印）。账号 `dq_commercial_golive_reader` 只对 5 张表 SELECT（阶段 6 离线演练：其他表 ERROR 1142） |
| Q2 | 数据源（Vault 存口令） | 运营控制台"数据查询 → 数据源 → 新建"：编码 `MOR_COMMERCIAL_GOLIVE`、MYSQL、`mysql:3306`、库 `mor_commercial`、账号 `dq_commercial_golive_reader`、口令（只在此输入一次，Platform 写入 Vault `DATA_SOURCE_PASSWORD_V1`，库内只存 Token）→ 检查三步通过 → 启用。备选：直接用现有 `MOR_COMMERCIAL_REPLICA`（待裁决 D-4） |
| Q3 | 模板 3 条 | 正文取 `db/data-query-templates.sql` 各段（`#{clientId}` / `#{agreementId}` 为模板语法）；参数 `clientId` / `agreementId` 类型 STRING 必填；约束 maxRows 1、超时 5 秒、不分页。**编码须改为 Platform 规则 `[A-Z][A-Z0-9_]{1,63}`**：建议 `GOLIVE_AGREEMENT_DRAFTED`、`GOLIVE_AGREEMENT_ACTIVE`、`GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（待裁决 D-3，涉及 REQ-00 契约 3.3 的模板名）。保存时后台校验（只读 SQL 守卫、顶层不得 LIMIT——模板 1 的 LIMIT 在子查询内）；校验不过即停，回研发调整 |
| Q4 | 试运行 | 每条模板各用阶段 5 的满足 / 不满足参数试运行，结果单行、`satisfied` / `out_*` / `missing` 与 `verification/stage5/data-query-results.md` 语义一致 |
| 回滚 | 停用 3 条模板与数据源 → `sqlr < $REQ/release/sql/appendix-data-query-reader-rollback.sql` | — |
