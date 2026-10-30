# tax 结账算税与事件登记 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-29）**。本文件只排步骤，不授权任何动作。改变环境的步骤（停 / 起容器、root DDL / DML、注册块、XXL 写、语言包发布、验收写数据、合成事件发布）在阶段 7 由**主会话**执行，须用户对阶段 7 明确授权（auto 模式下停容器须用户明说"执行"，见 memory `capability-gate-req01-delivered`）。
> 依据：[technique.md](../technique.md) 8.4、12、15.3；[baseline.md](./baseline.md)；[stage6-report.md](./stage6-report.md)。镜像一律钉 sha256（`tax-20261029a` tag 仅供辨认）。
> 与任务卡顺序的差异：注册块拆成两步——**休眠写入提前到 ④**（第 C 段 tax-service 文件写入路由须在 tax 开票前存在，休眠路由不对外），**激活放到 ops-bff 之后、ops-web 之前（⑫）**，避免新菜单 / 路由先于 BFF 与页面生效（同 REQ-03）。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 6 个候选镜像与 tax 回滚镜像已构建并核对（`evidence/image-verify.txt` 全 same）；Platform、Gateway、ledger、payment **不换** | 已就绪 |
| P-2 | 执行前跑 `scripts/source_drift_check.sh`：tax 研发 worktree 378 个文件未变、main 自 `30361be7` 起没有改 `paytech/` 与 ops-web 源码、7 个现网镜像仍是基线。任一 DRIFT → 停，回阶段 6 在新基线上重建（回滚文件也要改写）。**联合查询（块 144）、REQ-04（块 145）、限额档位等会话若先发布 ops-bff / ops-web / Platform，必然触发 DRIFT** | 执行当天 |
| P-3 | 通知并行会话（主会话发）：见第 1 节窗口影响；发布后 MOCK 订单除 AE / SA / EG 且税类 `SAAS_AUTOMATED` 外一律 BLOCKED、不可支付（D-1）；注册块按跨会话约定：144 联合查询（design-discuss-19）、145 REQ-04（design-discuss-b8）、146 本需求；语言包本需求发布后联合查询从下一号起；工作流节点编辑体验会话的 `compose-candidate.operations-console-web.yaml`（`a9d50000`）发布后不要再执行 | 执行当天，主会话 |
| P-4 | **时间窗：⑯ 合成事件（若 D-2 选 A）须在 2026-10-31 23:59Z 前**——ledger 2026-11 期间为 FUTURE，过账会失败进 DLQ | 待排期 |
| P-5 | 待裁决 D-1（STANDARD / 非 AE·SA·EG 订单被阻断）、D-2（billing → ledger 端到端验证方式）、D-3（Platform 是否随发）、D-4（tax 回滚镜像）——stage6-report 第 4 节 | 待用户裁决 |
| P-6 | 验收账号：运营控制台管理员（`test-environment/private/access.md`，脚本读取，不打印） | 已就绪 |

## 1. 现网基线与候选

| 服务 | 现网（= 回滚目标） | 候选 `tax-20261029a` |
|---|---|---|
| tax-service | 现网容器 `aeebac7a…`（镜像记录已删除）→ 回滚用重建镜像 `sha256:884728dc7be5d69a45f9f9692d939f468707475aff3da1ebf2fbe5eeac83155f`（现网 JAR `a67caf6d`） | `sha256:a5616e20b18c04e2e5bb158031898f8c7bef9ab26540d5a025d5be3975cf1c31`（JAR `b26621ba`） |
| acquiring-service | `sha256:e0a1f618a050e30b85059aa4214fea43bedeb35cf48438374d444e735e107299`（relsnap） | `sha256:843382e35d401341a8984bcbc02ceb98b7c9774d4b48524fe602e44058bed01c`（JAR `77e6351d`） |
| billing-service | `sha256:cf580cfbd05786e55c0e09b9749e1c1ba1a4fe54acef383e355bef7ac6c05650`（refclient） | `sha256:adfd081728ae50759ba9dc2ce18aa9a019c0097997912925c7c19b7c75dfc05e`（JAR `b52f655e`） |
| account-service | `sha256:17831407811fefb3e747208d0bda1a4c5e0481e19ea4423ee98f28723cfa6284`（billing-b） | `sha256:8a21dab8e54bde0945e52dcace5a4a2722a581dbba79d7b1e4d8acc257e2b68e`（JAR `67e2ab60`） |
| operations-console-bff | `sha256:4f08f1e601beb791f8e61c088e471e8839fc65783acbf513e412e5fcaf200b6b`（rq03） | `sha256:e243d57bd5406a2ac8efa133b52586cc38acfd60ec2c739e6fe5ff1dabd6fe27`（JAR `3080a027`） |
| operations-console-web | `sha256:a9d5000088e9bc239c85734c967a17f4d7f40f43efae94438f2241bba8078aa9`（wfpanel d，dist 258） | `sha256:748ca7b9c6b417104bbb7e3a09ba4b2630d8c4c513a4de2c43644a769b66a97a`（dist 266） |

**窗口影响**：⑥ tax 重启（现网 tax 空壳，无调用方）无影响；⑧ billing、⑨ account 各约 30—60 秒消费暂停（消息在分区队列等待）；⑩ **acquiring 停机约 1—2 分钟**（Client API 建单 / 查询、Hosted Checkout、acquiring 消费与订单到期任务 23 暂停；mor-test 无 OPEN 订单）；⑪ ops-bff 约 30 秒运营接口 503；⑫ 激活到 ⑬ 之间旧 ops-web 不显示新菜单（旧页面能力表无该组件）；⑬ 到 ⑭ 之间税务页中文显示内置英文（数分钟）。⑩ 之后**所有新建 MoR 订单走真实算税**（D-1）。

## 2. 命令约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ="/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261029/tax-结账算税与事件登记"
up()    { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr()   { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
stopc() { ./test-environment/compose.sh stop "$@"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
img()   { docker inspect "mor-test-$1-1" --format '{{.Image}}'; }
errs()  { docker logs --since "$2" "mor-test-$1-1" 2>&1 | grep -E ' ERROR |Exception' | head -5; }
gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }
mq()    { docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues -p /mor-test name messages consumers 2>/dev/null | grep -E "$1"; }
ACC="python3 -I $REQ/release/acceptance/tax_api.py"
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `img <服务>`、healthy、`errs <服务> <T>`。**任一"失败即停"条件成立：停止前进，按第 5 节回滚到该步之前。**

## 3. 执行顺序（阶段 7）

| 步 | 内容 | 命令 | 预期 / 失败即停 |
|---|---|---|---|
| ① | 通知、复核、发布前记录 | P-3 通知；`bash $REQ/release/scripts/source_drift_check.sh`；`bash $REQ/release/scripts/env_check.sh`；`bash $REQ/release/harness/image_verify.sh`；`$ACC --step before` | drift 全 same；env_check 回滚 6 行 `missing=[] extra=[] valuediff=[]`、候选只多 tax 17 / acquiring 1 / ops-bff 1 个键；image_verify 6 行全 same；`before`：STANDARD / US / 12.34 建单 201，记录现网金额与状态（零税）。否则停（P-2） |
| ② | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/tax-20261029/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"`；`for db in mor_tax mor_acquiring mor_billing mor_platform; do docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases $db > "$B/$db.sql"; done`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF xxl_job xxl_job_group xxl_job_info > "$B/xxl.sql"`；①的 `img` 读数写 `$B/images-before.txt`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 每个导出非空且以 `Dump completed` 结尾 |
| ③ | 只读预检 | `sqlro < $REQ/release/sql/00-precheck.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-146-precheck.sql` | 与 `precheck/00-precheck.out`、`precheck/block-146-precheck.out` 相同：mor_tax 24 表 0 行、AE001 = 880000000702、acquiring 新表 / 列 0、无 OPEN 订单、billing 注释无 TAX_CALCULATION；块 146 区间 / 编码 / 路径 / 写入路由 0、依赖 ACTIVE、默认私有路由 `pdf_allowed_expect_1 = 1`、ACTIVE 631 / DISABLED 6。否则停 |
| ④ | 注册块 146 休眠写入 | `T4=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/release/sql/mor_platform-block-146.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-146-verify.sql`；等 10 秒 `gwlog $T4` | 1 / 1 / 1 / 2 / 8 / 12 / 1；授权 REVOKED、资源与路由 DISABLED；写入路由与默认私有路由同桶 / 策略 / 期限；快照刷新成功、routeCount 仍 **631**（阶段 6 离线：loaded 639 / effective 631）。快照刷新失败 / `CONTROL_PLANE_UNAVAILABLE` → 立即 `mor_platform-block-146-rollback.sql`（R4） |
| ⑤ | mor_tax 重建 + 初始化 | `sqlr mor_tax < $REQ/release/sql/mor_tax-rebuild.sql`；`sqlr mor_tax < $REQ/release/sql/mor_tax-initial-data.sql`；`sqlro mor_tax < $REQ/release/sql/mor_tax-verify.sql` | 16 表、旧表 0；种子 1 / 3 / 3 / 3 / 3、主体只有 880000000702、AE 0.05 / EG 0.14 / SA 0.15、序列 next 1（演练 0.94 s + 0.30 s）。初始化报错 → `mor_tax-rebuild.sql` 重跑（会清空）再初始化；仍失败 → R5 |
| ⑥ | tax-service 新镜像 | `T6=$(date -u +%FT%TZ); up tax-service` | `img` = `a5616e20…`、healthy、`errs tax-service $T6` 空；`mq tax.acquiring` 出现 `p0..p3`（各 1 消费者）与 `dlq`；`docker exec mor-test-rabbitmq-1 rabbitmqctl list_bindings -p /mor-test source_name destination_name 2>/dev/null \| grep -E '^acquiring.events'` 含 `tax.acquiring.hash`；`$ACC --step preflight` 中 tax 种子一项通过（images 与块两项此时尚未满足，属预期）。180 秒不 healthy → R6 |
| ⑦ | XXL 登记 | 约 30 秒后（执行器心跳）`python3 -I $REQ/release/scripts/xxl_tax.py plan` → `… apply` → `… trigger` | plan：分组 MISSING；apply：新建分组 `tax-service`（id 应为 18）与任务（id 应为 28）并启动；trigger：调度 200、执行 handle 200（空扫描）。登录失败先确认 XXL Admin healthy（阶段 6 读数：当日刚重启） |
| ⑧ | billing（消费方先上） | `sqlr mor_billing < $REQ/release/sql/mor_billing-comment.sql`；`T8=$(date -u +%FT%TZ); up billing-service` | 注释含 TAX_CALCULATION；`adfd0817…` healthy、无 ERROR；`mq billing.acquiring` p0..p3 各 1 消费者、dlq 0。→ 否则 R8 |
| ⑨ | account | `T9=$(date -u +%FT%TZ); up account-service` | `8a21dab8…` healthy；`mq account.billing` p0..p3 各 1。→ 否则 R9 |
| ⑩ | **停 acquiring → 迁移 → 新镜像**（紧接执行） | `T10=$(date -u +%FT%TZ); stopc acquiring-service`；`sqlr mor_acquiring < $REQ/release/sql/mor_acquiring-migrate.sql`；`sqlro mor_acquiring < $REQ/release/sql/mor_acquiring-verify-after.sql`；`up acquiring-service` | 迁移秒级（演练 0.64 s）；`new_table_1`、`null_amount_status_0`、51 行 FINAL、三列可空、唯一键两列；`843382e3…` healthy、无 ERROR；`mq acquiring.payment` p0..p3 各 1。迁移报错 → 不起新镜像，`upr acquiring-service`（旧镜像，库未变或回滚后）；新镜像 180 秒不 healthy → R10（**此刻尚无新订单，可回滚**） |
| ⑪ | ops-bff | `T11=$(date -u +%FT%TZ); up operations-console-bff` | `e243d57b…` healthy；运营已有页面（财务 · 对账、风控 · 承保）不 503。→ 否则 R11 |
| ⑫ | 注册块 146 激活 | `T12=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/release/sql/mor_platform-block-146-activate.sql`；`sqlro mor_platform < $REQ/release/sql/mor_platform-block-146-verify.sql`；5 秒后 `gwlog $T12` | 授权 / 资源 / 2 路由 ACTIVE、`route_same_1 / headers_same_1 / guards_same_1` 全 1；routeCount **631 → 633**；未登录 `curl -s -o /dev/null -w '%{http_code}' -H 'Origin: http://localhost:28080' http://localhost:28080/api/operations/tax/v1/orders/x` 与已有读路由 `…/storage-admin/v1/files/1` 返回码相同（激活前 404）。快照失败 → R12 |
| ⑬ | ops-web | `T13=$(date -u +%FT%TZ); up operations-console-web` | `748ca7b9…` healthy；`docker exec mor-test-operations-console-web-1 sh -c 'find /usr/share/nginx/operations-console -type f \| wc -l'` = 266；首页可登录、财务菜单出现"税务查询" |
| ⑭ | 语言包 | 计划（只读）`python3 -I $REQ/release/tax_bundles.py`；发布同一命令加 `--apply` | 计划与阶段 6 相同（`precheck/i18n-plan-dryrun.out`）：当前 **#23**、新建命名空间恰好 `tax`（sort 270）、新增 214（tax 213、nav 1）、基准变更 / 废弃 / 恢复 0、zh-CN 导入 214、`zhChangedNotImported` 0。**当前号不是 #23（他人已发布）→ 重新跑计划**：门禁仍须全过（他人新 key 不在本需求源码中会被算作"废弃"而拦截 → 停，在新基线上重建 ops-web 源码后再发）。发布后新号 READY 且为当前；匿名 manifest 200。Gateway 504 但 Platform 已完成时先只读查 `t_i18n_release`，已 READY 不重复发布 |
| ⑮ | 验收（API、浏览器） | 第 6 节 A1—A6 | 全部 PASS；**A2 起会建新订单，之后 mor_acquiring 不能再回滚**（R10 前置检查拦截） |
| ⑯ | 合成事件端到端（D-2 = A 时） | 第 6 节 A7 | A7 PASS |
| ⑰ | 记录 | 更新 delivery.md、CURRENT_IMPLEMENTATION；通知 P-3 的会话；释放 `Code-MoR-tax-rel` 前确认阶段 8 已从研发 worktree 提交 | — |

## 4. XXL 登记参数（⑦，`scripts/xxl_tax.py`）

| 字段 | 分组 | 任务 |
|---|---|---|
| appname / title / addressType | `tax-service` / `Tax` / `0`（自动注册） | — |
| jobDesc / author | — | `Tax 本地补偿扫描（compensation_event：简化税票开具重试）` / `code-mor` |
| scheduleType / scheduleConf | — | `CRON` / `*/30 * * * * ?`（UTC，同其余 12 个补偿扫描） |
| executorHandler / executorParam | — | `compensationEventScan` / 空 |
| route / block / timeout / retry / misfire | — | `SHARDING_BROADCAST` / `SERIAL_EXECUTION` / `60` / `0` / `DO_NOTHING` |

回滚：`xxl_tax.py remove`（停任务、删任务、删分组）。**回滚 tax 镜像之前必须先 remove**（旧镜像无执行器，调度会报"registry address is empty"）。

## 5. 回滚（逆序；验收失败、新镜像 180 秒内不 healthy 或快照编译失败时）

| 顺序 | 动作 | 说明 |
|---|---|---|
| R14 | 语言包 | 一般不回退：只新增 `tax` 命名空间与 1 个 nav key，旧 Web 不读。必须回退时以 `30361be7` 干净 worktree 为 `--web-root` 重新发布（KEY_IMPORT 废弃 214 个新 key；`tax` 命名空间保留为空） |
| R13 | `upr operations-console-web` | 回到 `a9d50000`（wfpanel d） |
| R12 | `sqlr mor_platform < $REQ/release/sql/mor_platform-block-146-deactivate.sql` | routeCount 633 → 631；只回退注册块激活可行（税务页按钮 404，其余正常） |
| R11 | `upr operations-console-bff` | 回到 `4f08f1e6`（rq03） |
| R10 | 先 `sqlro mor_acquiring < $REQ/release/sql/mor_acquiring-rollback-precheck.sql`：**前两项均 0** 才可 `stopc acquiring-service` → `sqlr mor_acquiring < $REQ/release/sql/mor_acquiring-rollback.sql` → `upr acquiring-service` | 有新订单（快照为空或多合同）时回滚 SQL 第一条 ALTER 即失败、不做任何改动（演练已证）；此时**不能**只回退镜像（旧镜像插入订单不写 `amount_status` 会失败）——保留新 acquiring 与新库结构，另行评审数据处理（stage6-report R-1）。回滚后 acquiring 事件不含税务字段，billing、tax 自然退回无税路径 |
| R9 | `upr account-service` | 旧 account 不认 `SELLER_TAX` 的 CHANNEL 付方：须在 acquiring 已回滚（或确认无含税事件在途）后执行 |
| R8 | `upr billing-service`；`sqlr mor_billing < $REQ/release/sql/mor_billing-comment-rollback.sql` | 同 R9 的前提；已写入的 TAX_CALCULATION 引用行保留（旧代码只读不认，不影响） |
| R7 | `python3 -I $REQ/release/scripts/xxl_tax.py remove` | 先于 R6 |
| R6 | `upr tax-service` | 回滚镜像 `884728dc…`（重建，见 baseline 1.1）；`tax.acquiring.p0..p3` 失去消费者后 `acquiring.events` 继续向其投递并积压——需要时由主会话删除 `tax.acquiring.*` 队列与绑定（另行确认，不在本卡授权内） |
| R5 | `sqlr mor_tax < $REQ/release/sql/mor_tax-rebuild-rollback.sql` | 只在新表无业务数据时（演练：回滚后 mor_tax 与导出逐行相同）；有计算 / 交易 / 税票时不回退表（旧镜像不读这些表），保留 |
| R4 | `sqlr mor_platform < $REQ/release/sql/mor_platform-block-146-rollback.sql` | 路由仍 ACTIVE 时拒绝（ERROR 1242，先 R12）；tax-service 已上传过税票（`t_file_upload.source_system='tax-service'`）时拒绝——只保留休眠（演练均已证）；删除后 mor_platform 与导出只差管理员角色 version（52 → 54，激活 / 停用各 +1，设计如此） |

只回退 ops-web / ops-bff / 注册块而保留后端可行（税务页不可见或 404，其余正常）；只回退 tax 而保留新 acquiring 可行（acquiring 调 tax 失败 → 建单与资料提交返回 502 / 依赖不可用）——不建议，应整体逆序。

## 6. 验收（⑮—⑯）

| # | 内容 | 命令 / 方式 | 通过条件 |
|---|---|---|---|
| A1 | 发布后检查 | `$ACC --step preflight` | 6 个镜像 = 候选；`tax.acquiring.p0..p3` 各 1 消费者；tax 种子 1/3/3/1、主体 880000000702；块 146 两条路由 ACTIVE；记录三个 DLQ 基线 |
| A2 | STANDARD 影响实证 + 存量订单 | `$ACC --step after` | 与 ① `before` 同一请求（STANDARD / US）→ 201、`amountStatus=BLOCKED`（`MARKET_NOT_ENABLED`）；STANDARD / AE → 201、BLOCKED（`UNKNOWN_TAX_CATEGORY`）；存量 PAID 订单 FINAL、税 0、tax 查询 404（AC-12） |
| A3 | 两阶段报价、阻断 | `$ACC --step scenario` | S1：SAAS_AUTOMATED 100.00、声明 AE → 201 ESTIMATED 105.00；资料 SA → FINAL 15.00 / 115.00；计算 r1 PROVISIONAL READY AE、r2 FINAL READY SA；合同 2 份；tax 订单查询 200（2 计算 0 交易）（AC-01、AC-03）。S2：资料 KW → BLOCKED，付款 409（AC-05） |
| A4 | 浏览器 | `node $REQ/release/acceptance/tax_browser.mortest.mjs`（zh-CN / en × 1440 / 390；A7 之后再跑一次以拍"已登记 + 已开票"与下载） | 截图 `acceptance/screens/`；无原始 `tax.*` key、无横向溢出；S1（A7 前）显示两条报价、无交易；S2 阻断；STANDARD 单阻断原因；存量单与不存在单为"未找到"；回归：风控 · 承保列表、工作流定义页可打开；HTTP ≥ 400 只有预期的 404 / 模拟 503 |
| A5 | NOT_MANAGED | 不可造：mor-test 收单为 MOCK，所有订单 TAX_RESPONSIBLE = AE001。保留阶段 5 本机证据（S3）与 auto 测试 | 记录"不适用" |
| A6 | 付款 | 不执行真实卡支付：mor-test 无可用卡渠道（baseline 第 3 节）。门禁由 S2 的 409 证明 | 记录"不适用" |
| A7 | 合成事件端到端（D-2 = A；**2026-10-31 23:59Z 前**） | ① `$ACC --step synthetic`（生成 `acceptance/params-synthetic.json`、只打印信封）；② 上游只读冒烟 `docker run --rm --network mor-test-backend -v "$REQ/../../20261022/billing-支付事实自动登记/release/acceptance:/b:ro" -v "$REQ/release/acceptance:/a:ro" python:3.13-alpine python -I /b/smoke_upstreams.py /a/params-synthetic.json`；③ `$ACC --step synthetic --publish --yes`；④ `$ACC --step check`；⑤ 重跑 A4 | ② commercial APPLE_PAY 200（SA 返回 422 时以 `TAX_SYN_CONSUMER_COUNTRY=AE` 重新生成并重跑 ②）、channel-connector 200、reconciliation 200、ledger by-party 200；③ routed=true；④ tax 正式交易 1、底稿 PENDING(FX_RATE_MISSING)、税票 `SA-RCPT-2026-000001` ISSUED、tax 查询含交易与单据；billing PAYMENT 事实 1、`SELLER_TAX 15.00 → TAX_AUTHORITY 880000000702`、`TAX_CALCULATION` 引用 = 冻结计算；account 入账；ledger `TAX_PAYABLE` 贷 15.00；三个 DLQ 无新增（AC-07、AC-09、AC-11）。任一 FAIL → 停，记录发现，不回滚（属验收数据） |

**验收留下的数据**：DEMO-CL-001 下约 6 笔 OPEN 订单（`tax-s7-*`，到期后由任务 23 收为 EXPIRED）与其 tax 计算；A7 时 S1 订单的 tax 正式交易、底稿、税票 1 张（PDF 存于 `mor-platform-files`）、billing PAYMENT 事实（合成支付 ID 段 `9999000000000003xx`，`SYN-TAX-P-0001`）、account / ledger 过账（AE001 USD 账簿 TAX_PAYABLE +15.00）。S1 订单本身在 acquiring 仍为未付（合成事件只到 tax 与 billing），到期后 EXPIRED——**这是验收数据的已知不一致**（同 billing B 合成事件先例），在 delivery.md 记录。结果 JSON 与截图在 `acceptance/`。

## 7. 文件

| 文件 | 说明 |
|---|---|
| `compose-candidate.<服务>.yaml` / `compose-rollback.<服务>.yaml`（6 + 6） | 钉 sha256；env 由 `scripts/env_check.sh` 核对 |
| `sql/mor_tax-rebuild{,-rollback}.sql`、`sql/mor_tax-initial-data.sql`、`sql/mor_acquiring-{migrate,rollback}.sql`、`sql/mor_billing-comment.sql` | 与研发 worktree 中仓库文件**逐字节相同**（`sql/SHA256SUMS`） |
| `sql/mor_billing-comment-rollback.sql`、`sql/mor_tax-verify.sql`、`sql/mor_acquiring-{verify-after,rollback-precheck}.sql`、`sql/00-precheck.sql` | 阶段 6 新写（回退、核对、预检） |
| `sql/mor_platform-block-146{,-precheck,-verify,-activate,-deactivate,-rollback}.sql` | 由 `db/platform-registration.sql` 生成（块号 146） |
| `harness/rehearse.sh` + `BlockSnapshotCheck.java` | 离线演练与快照编译（`evidence/rehearsal-20261029T2040Z-block146/`） |
| `harness/image_verify.sh`、`harness/jarcmp.py`、`harness/envcmp.py`、`scripts/env_check.sh`、`scripts/source_drift_check.sh`、`scripts/xxl_tax.py` | 核对与登记 |
| `tax_bundles.py`、`i18n-bundles/` | 语言包计划与导入文件 |
| `acceptance/tax_api.py`、`acceptance/tax_browser.mortest.mjs` | 第 6 节 |
