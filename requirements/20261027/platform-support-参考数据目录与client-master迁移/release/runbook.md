# 参考数据目录与 client-master 迁移 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（rel-dev，2026-10-27）**。本文件只排步骤，不授权任何动作。**所有改变环境的步骤由主会话执行**（容器 stop / up、root SQL、多语言写入、验收写入），并须用户对阶段 7 明确授权；agent 只做备份、只读核对与记录。
> 依据：technique.md 第 3、5 节（发布顺序与回退）；现网基线 [baseline.md](./baseline.md)；构建与演练 [../verification/rel-build-report.md](../verification/rel-build-report.md)。
> 镜像一律钉 sha256（tag `refdata-20261027a` 只供辨认，`:development` 未改动）。不加 `--remove-orphans`；一次只换一个服务。

## 0. 前提（执行前逐项清零）

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 6 个镜像已在本机 Docker 构建并核对（镜像内 JAR / dist 与构建产物逐字节相同）：Platform `aec53586…`、client-master `80257737…`、client-bff `be79550f…`、ops-bff `48113289…`、client-web `c09af02a…`、ops-web `b1309a45…`。构建源 `Code-MoR-refdata-rel`，清单指纹 `eff2848560062d60` | 已完成 |
| P-2 | 代码冻结：执行前在 `Code-MoR-refdata` 与 `Code-MoR-refdata-rel` 各算一次指纹（命令见报告第 1 节），必须仍为 `eff2848560062d60`；不一致就停，先报告差异文件并重建受影响镜像 | 执行当天 |
| P-3 | 通知并行会话：本次会停 Platform 约 1 分钟（登录、运营控制台、Gateway 快照刷新暂停）、换 client-master / 两个 BFF / 两个 Web、改 `mor_platform` 表名、发布两个应用的语言包 | 执行当天，主会话 |
| P-4 | 重跑只读预检（第 3 节 ①），结果须与 baseline 第 4 节一致；特别是 `t_currency_spec` 行、`t_party_address` 下划线行数、多语言当前发布号。不一致时：数据差异重跑离线演练（P-5），发布号差异只更新期望值 | 执行当天 |
| P-5 | 离线演练已用 12:56Z 备份通过（正向 + 逆向全部成功，Gateway 编译 7 态全部 `COMPILE OK`，逆向后结构与备份逐字相同）。P-4 有数据差异时，用第 2 节新备份重跑：`"$REQ/release/harness/rehearse.sh" <新 platform 备份.gz> <新 client-master 备份.gz> /Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel/paytech/platform-support/paytech-platform-support-app/target/paytech-platform-support-app-1.0.0-SNAPSHOT-exec.jar <空目录>` | 已完成（12:56Z 数据） |
| P-6 | 国旗上传（第 8 节）**不在本次发布范围**；不做时国家、币种显示内置占位图 | 另行决定 |

## 1. 约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR            # compose.sh 所在主工作区
REQ=/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/platform-support-参考数据目录与client-master迁移
O="$REQ/release/compose-candidate.yaml"; RB="$REQ/release/compose-rollback.yaml"
up()  { ./test-environment/compose.sh -f "$O"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
stopc() { ./test-environment/compose.sh stop "$@"; }
set -a; . ./test-environment/.env.test; set +a
sqlr() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
img()  { docker inspect "mor-test-$1-1" --format '{{.Image}} {{.State.Health.Status}}'; }
snap() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -E 'Snapshot 刷新|CONTROL_PLANE' | tail -3; }
errs() { docker logs --since "$2" "mor-test-$1-1" 2>&1 | grep -c ' ERROR '; }
T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)                     # 发布开始时间：日志窗口与验收 --since
```

`release/sql/` 由 [sync_sql.sh](./sync_sql.sh) 生成（不要手改；`sql/SHA256SUMS` 核对）。分服务覆盖文件 `compose-{candidate,rollback}.<service>.yaml` 与合并文件内容相同，单独回滚某个服务时可用。

## 2. 备份与回滚列（第 ⓪ 步）

```bash
B=/Users/kenny/mor-backups/refdata-20261027/release-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$B"
for db in mor_platform mor_client_master; do
  docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces \
    --set-gtid-purged=OFF --routines --triggers --events --databases "$db" | gzip -c > "$B/$db.sql.gz"
done
(cd "$B" && shasum -a 256 *.gz > SHA256SUMS)
for c in platform-support-service client-master-service client-console-bff operations-console-bff client-console-web operations-console-web \
         api-gateway vault-service ledger-service billing-service card-service; do echo "$c $(img $c)"; done > "$B/images-before.txt"
```

通过条件：两个 dump 以 `Dump completed` 结尾；`images-before.txt` 前 6 行等于 baseline 第 1 节（= `compose-rollback.yaml`）。

## 3. 执行顺序

预计总时长约 25—35 分钟（其中服务中断：Platform 约 1 分钟、client-master 约 30 秒）。

| 步 | 内容 | 命令 | 预期 / 通过条件 | 预计耗时 |
|---|---|---|---|---|
| ① | 只读预检 | `sqlr mor_platform < $REQ/release/sql/00-platform-release-precheck.sql`；`sqlr mor_platform < $REQ/release/sql/01-platform-block139-precheck.sql`；`sqlr mor_client_master < $REQ/release/sql/02-client-master-release-precheck.sql` | 与 baseline 第 4 节一致：无 `t_ref_*`；币种 USD 1001 + AED（Snowflake）；国家 249；块 139 区间 / code / 路径全 0、依赖 1；ops target 与菜单 562 存在；退役目录 48、`business.currency` 8、下划线地址 12 | 1 分钟 |
| ② | **Platform 窗口**：停旧镜像 → 改表名与装数 → 起新镜像 | `stopc platform-support-service` → `sqlr mor_platform < $REQ/release/sql/10-platform-reference-data.sql` → `sqlr mor_platform < $REQ/release/sql/11-platform-reference-data-verify.sql` → `up platform-support-service` | 10：无输出、退出码 0（演练 0.5 秒）；11：表 = 5 张 `t_ref_*`、过程 0、币种 AED（原 Snowflake 行，784）/ EGP 1004 818 / SAR 1003 682 / USD 1001 840 全 ACTIVE、国家 249/249/242/248/249、行政区 AE 7 / EG 27 / SA 13 无空本地名、行业 10 + 32、建议 MCC 未启用 0、MCC 298 / 高风险 14、旧接口约 583 字节；`img platform-support-service` = `sha256:aec53586… healthy`（启动约 15 秒 + 健康检查，`--wait` 内完成） | stop 约 10 秒；SQL < 1 秒；up 30—60 秒 |
| ②a | Platform 检查 | `snap $T0`；运营控制台登录；`errs platform-support-service $T0`；60—90 秒后 `docker logs --since $T0 mor-test-ledger-service-1 2>&1 \| grep -c 'Currency snapshot refresh failed'`（billing 同） | Gateway 恢复 `刷新成功 … routeCount=607`（停机期间出现 `CONTROL_PLANE_UNAVAILABLE` 属预期，保留上一快照）；登录 200——若 503 且消息含 `No servers available for service: vault-service`，执行 `docker restart mor-test-vault-service-1`，约 20 秒后重试；Platform ERROR 0；ledger / billing 刷新失败 0（旧接口响应不变，S-01） | 2 分钟 |
| ③ | client-console-bff | `up client-console-bff` | `be79550f… healthy`；客户门户打开入驻页 form-schema 200（多出 `countries`，旧 Web 忽略）。新 BFF 与旧 client-master、旧 Web 都兼容（第 4 节） | 30 秒 |
| ④ | **client-master 窗口**：停旧镜像 → DML → 起新镜像 | `stopc client-master-service` → `sqlr mor_client_master < $REQ/release/sql/20-client-master-reference-data-catalog.sql` → `sqlr mor_client_master < $REQ/release/sql/21-client-master-reference-data-catalog-comment.sql` → `up client-master-service` | 20 输出 before 48 / 8 / 2 / **12** / 0 → after 0 / 8 / 2 / 0 / **12**（与演练一致；地址行数以 ① 为准）；21 无输出；`80257737… healthy`（启动约 8 秒）；`errs client-master-service $T0` = 0 | stop 10 秒；SQL < 1 秒；up 20—40 秒 |
| ⑤ | client-console-web | `up client-console-web` | `c09af02a… healthy`；入驻页国家 / 币种 / 酋长国下拉有选项（语言包未发布前行政区显示英文名，预期） | 20 秒 |
| ⑥ | operations-console-bff | `up operations-console-bff` | `48113289… healthy`；对账 7 页不 503（4 个 BASE_URL 已带）；审核详情 `industries` 为 10 个大类带细类 | 30 秒 |
| ⑦ | operations-console-web | `up operations-console-web` | `b1309a45… healthy`；币种页列出 4 个币种与新列；分类面板为两级（参考数据菜单在 ⑨ 激活后才出现） | 20 秒 |
| ⑧ | 块 139 休眠注册 | `sqlr mor_platform < $REQ/release/sql/01-platform-block139-precheck.sql`（再确认）→ `sqlr mor_platform < $REQ/release/sql/60-platform-block139-registration.sql` → 10 秒后 `snap $T0` | 无错误（缺依赖会 ERROR 1242 整体回滚）；快照仍 `刷新成功 routeCount=607`、无 `CONTROL_PLANE_UNAVAILABLE`（离线编译：loaded 618、effective 607） | 1 分钟 |
| ⑨ | 激活块 139 | `sqlr mor_platform < $REQ/release/sql/61-platform-block139-activate.sql` → 10 秒后 `snap $T0` → 运营控制台退出重登 | 61 输出 routes 5、resources 1、grants 1、role 1；快照 `routeCount=612`；平台配置下出现"参考数据"菜单（排序在卡 BIN 库之后） | 1 分钟 |
| ⑩ | 语言包计划（只读） | `python3 -I $REQ/release/refdata_bundles.py --accept-deprecations` | 与阶段 6 计划相同（数字见第 5 节）：client-console 当前 #5、新命名空间 `refdata`、新增 436、基准变更 0、废弃 48（全部 `onboarding-schema.catalog.{CURRENCY,INDUSTRY,STATE_OR_PROVINCE}.*`）、缺 zh 0；operations-console 当前 #12、新命名空间 `reference-data`、`refdata`、新增 645、基准变更 1、废弃 59（48 个同上 + 11 个本需求删除的分类 / 币种页旧文案）、缺 zh 0。废弃集合超出这两组即停 | 1 分钟 |
| ⑪ | 语言包发布 | 同一命令加 `--apply` | 每个应用：建命名空间 → key 导入（预览与计划一致、废弃 = 计划）→ zh-CN 增量导入 → 导入集合外已有 zh-CN 未变 → **确认本次触及的基准已变更译文**（计划预计 0；client-console 线上原有 2 条过期译文 `onboarding.start.applications_title`、`onboarding.upload.choose` 不在本次范围，保持原样）→ 预检可发布 → 发布；期望 **client-console #6**（10 命名空间 × 2 = 20 个语言包）、**operations-console #13**（26 × 2 = 52）READY 且为当前；匿名 manifest 逐包 200 且摘要 = hash | 5—10 分钟 |
| ⑫ | 验收（API） | `python3 -I $REQ/release/acceptance/refdata_mortest.py --since $T0` | 0 FAIL（SKIP：4 个临时停用子步骤、XTS 草稿币种——本机已验证）；结果 `verification/acceptance-mor-test-<UTC>.json` | 5—10 分钟 |
| ⑬ | 验收（浏览器） | `python3 -I $REQ/release/acceptance/ui_provision_mortest.py provision`；`node $REQ/release/acceptance/client_refdata.mortest.mjs en 1440`、`… zh-CN 1440`、`… en 390`、`… zh-CN 390`；`node $REQ/release/acceptance/ops_refdata.mortest.mjs en <caseAE> --currency`、`… zh-CN <caseAE> --currency`（`caseAE` 取 ⑫ 结果文件 `ids.caseAE`） | 全部 PASS（mor-test 版去掉了 MCC 5411 停用 422 与 XTS 面板两处，打印 SKIP）；截图与 findings 写入 `verification/ui-smoke/`（文件名前缀 `mortest-`）；页面无原始 key、无 5xx | 10 分钟 |
| ⑭ | 收尾 | `for c in …; do echo "$c $(img $c)"; done`（同第 2 节）；记录 delivery.md / CURRENT_IMPLEMENTATION | 6 个服务为候选镜像且 healthy；Gateway 612；通知并行会话 | — |

验收数据约束：本需求的参考数据没有生效时间；验收与冒烟的全部业务数据都用执行时刻，不回溯（"业务时间 ≥ 配置生效时间"在本需求不适用，仍不得用固定的历史时间）。冒烟与消费方一致：ledger / billing 用自己的定时刷新（严格 JSON 8192 / 65536）验证旧接口，不另造请求。

## 4. 顺序与兼容性依据

| 边 | 旧 ↔ 新 | 结论（证据） |
|---|---|---|
| Platform 表名 ↔ Platform 镜像 | 旧镜像读不到 `t_ref_*`，新镜像读不到旧表 | **同一窗口**：stop → SQL 10 → up（②）；回退为 stop → SQL 89 → 旧镜像（第 6 节） |
| ledger / billing / card → Platform 旧接口 | 严格 JSON（8192 / 65536） | 不受影响：`/currency-specs`、`/country-regions` 响应结构冻结（S-01；阶段 5 逐字节 / 逐字段比对）；`/currency-specs` 约 583 字节（多 SAR、EGP 两行） |
| client-master、client-bff、ops-bff → Platform 新接口 `/reference-data/*` | 新消费方需要新 Platform | **Platform 必须最先**；回退时这三个必须先于 Platform 回退 |
| 旧 ops-bff → 新 Platform 币种维护 | 新请求体 `numericCode`、`name`、`symbol` 必填 | ② 到 ⑥ 之间运营**新建 / 修改币种会被拒**（列表、启停不受影响）；窗口约 5 分钟，不改数据 |
| client-bff ↔ client-master form-schema | client-bff 对 client-master 用严格 JSON（524288）；`FormSchemaResponseBody` 结构未变（只改 JavaDoc） | 两向都能解析。新 BFF + 旧 client-master：旧目录原样透传，另补国家列表，功能完整；旧 BFF + 新 client-master：`REF_*` 无选项，币种 / 行政区下拉为空。所以 **client-bff（③）先于 client-master（④）**，没有空选项窗口 |
| client-web ↔ client-bff | 新 Web 不再内置国家，读 BFF 下发的 `countries` | **client-web（⑤）在 client-bff 之后**；旧 Web 忽略新字段 |
| client-master 镜像 ↔ DML | 旧镜像把 `REF_*` 当本库目录（选项为空、提交全部"不在目录"）；新镜像对旧数据仍按旧目录工作（每次请求读库，无缓存） | 用"停 → DML → 起"同一窗口（④），不依赖未验证的新镜像 + 旧数据组合；回退为"停 → SQL 80 → 旧镜像" |
| ops-bff ↔ client-master 管理端辖区（`addressComponents`） | ops-bff 调 client-master 用 `HttpServiceClientOptions.strict()`，**未设置严格字节数**，走 RestClient 默认 Jackson 转换器 | 实测（本机 JDK 25 + 两个 ops-bff JAR 的类路径，`RestClient.builder()` 默认转换器）：`FAIL_ON_UNKNOWN_PROPERTIES=false`、`FAIL_ON_MISSING_CREATOR_PROPERTIES=false`；`AdminJurisdictionResponseBody` 是 setter 型 POJO。旧 ops-bff 读新响应（多 `addressComponents`）与新 ops-bff 读旧响应（缺该字段）**都 READ OK**。所以此边不约束顺序（与"旧 ops-bff 会因 FAIL_ON_UNKNOWN_PROPERTIES 失败"的预判相反） |
| ops-bff ↔ client-master 分类 | 新 client-master 删除 `/classification-options`、只接受 2 级行业；旧 client-master 只接受 1 级行业 | 无数据风险，两向都**失败关闭**：旧 ops-bff + 新 client-master → 审核详情行业标 UNAVAILABLE（`ClientReviewDetailsProcessor.result` 对非会话 / 权限错误降级）、保存 1 级行业 422；新 ops-bff + 旧 client-master → 保存 2 级行业被旧目录拒绝。④ 到 ⑦ 之间（约 2 分钟）**分类编辑不可用**，按 ④→⑥→⑦ 连续执行 |
| ops-web ↔ ops-bff | 新 Web 读行业树、MCC 搜索、币种新字段 | **ops-web（⑦）在 ops-bff（⑥）之后** |
| 块 139 ↔ 镜像 | 休眠注册不依赖镜像；激活要求新 ops-bff / ops-web 与新 Platform（能力清单含 `OPERATIONS_REFERENCE_DATA`） | ⑧⑨ 在 ⑦ 之后；离线编译证明休眠与激活态都能编译 |
| 语言包 ↔ Web | 新 Web 未发布前回退内置英文 / 后端 `name` | ⑪ 在 Web 之后；旧 Web 不读 `refdata` / `reference-data` |

## 5. 语言包计划（阶段 6 只读计算，2026-10-27 13:1xZ）

由 `refdata_bundles.py`（切片 1 `onboarding_bundles.py` 改写：合并 `i18n-bundles/<应用>.refdata.{en,zh-CN}.json`，refdata 的 zh-CN 以语言包为准，增加"确认基准已变更译文"一步）生成；导入文件与计划在 `i18n-bundles/<应用>.release.{en.json,zh-CN.delta.json,plan.json}`。

| 应用 | 当前 | 仓库命名空间 | 新命名空间（排序） | 仓库 key | 线上 ACTIVE | 新增 | 基准变更 | 废弃 | zh-CN 导入 | 待确认译文 | 期望发布 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| client-console | #5 | 10 | refdata（100） | 1423 | 1035 | 436（refdata 429 + `onboarding.refdata.*` 7） | 0 | 48 | 436 | 0（另有线上原有 2 条不在本次范围） | #6，20 个语言包 |
| operations-console | #12 | 26 | reference-data（250）、refdata（260） | 10088 | 9502 | 645 | 1 | 59 | 646 | 0 | #13，52 个语言包 |

废弃 key 全部可归因：逐个核对在 `aba76a1a` Web 源码中存在、在发布源码中已删除（client 48 / 48，ops 59 / 59，外来 0）。refdata 429 个 key、zh-CN 429 个（行业 42、行业说明 42、行政区 47、MCC 298）。数字以 ⑩ 当天重跑为准；任何一项与上表不同且无法解释（例如有人在此期间发布了 client #6）时先停。

## 6. 回滚

### 6.1 触发条件（任一即回滚对应范围）

| 步 | 触发 | 回滚范围 |
|---|---|---|
| ② | SQL 10 报 `STOP:`（预检，未改动）→ 不回滚，`upr platform-support-service` 起回旧镜像，查原因；SQL 10 在 DDL 之后失败（BACKFILL 断言）→ 立即执行 SQL 89 再 `upr`；新 Platform 180 秒内不 healthy；起来后 Gateway 持续 `CONTROL_PLANE_UNAVAILABLE` 超过 1 分钟；重启 Vault 后登录仍失败；ledger / billing 出现 `Currency snapshot refresh failed` | Platform（6.2 第 6 步） |
| ④ | DML 计数与 ① 不符；新 client-master 不 healthy；form-schema / 提交核对对合法数据报错；ERROR 日志 | client-master（6.2 第 4 步）；client-bff 可保留（兼容旧 client-master） |
| ③⑤⑥⑦ | 不 healthy；页面 5xx；对账页 503（BASE_URL）；页面原始 key 大量出现（语言包前的英文回退不算） | 对应服务（`upr <service>`） |
| ⑧⑨ | Gateway 刷新失败（`CONTROL_PLANE_UNAVAILABLE`） | 块 139：⑧ 后 → SQL 69；⑨ 后 → SQL 62 → 69 |
| ⑪ | 预览 / 预检与计划不符 | 停止，不发布（已建命名空间与导入的 key 无害，新 Web 仍可用内置英文） |
| ⑫⑬ | 任一 FAIL 且不是验收脚本自身问题 | 按失败所在范围 |

### 6.2 全量回滚（逆序；每步后核对 `img`、healthy、`errs`）

1. **语言包**：发布不可变，不删除。旧 Web 不读 `refdata` / `reference-data`；但 48 个 `onboarding-schema.catalog.*` key（ops 另有 11 个）在 #6 / #13 中已废弃，旧 Web 回退为内置英文。需要中文时用旧源重新导入并发布（恢复这些 key）：`python3 -I $REQ/release/refdata_bundles.py --web-root /Users/kenny/Desktop/Workspace/Code-MoR-refdata-base --accept-deprecations`（计划：恢复上述 key、废弃本次新增 key；refdata 保留）→ 核对后加 `--apply`。
2. **块 139**：`sqlr mor_platform < $REQ/release/sql/62-platform-block139-deactivate.sql`（路由 5、资源 1、授权 1、角色 1；快照回到 607）→ `sqlr mor_platform < $REQ/release/sql/69-platform-block139-rollback.sql`（删 59 行；PLATFORM_ADMIN 角色 version 共 +3，属预期）。
3. **Web / BFF**：`upr operations-console-web` → `upr operations-console-bff` → `upr client-console-web`。
4. **client-master**：`stopc client-master-service` → `sqlr mor_client_master < $REQ/release/sql/80-client-master-reference-data-catalog-rollback.sql`（恢复 48 行目录、目录码改回、AE/EG ISO 省州改回下划线；输出 after 48 / 8 / 2 / 12）→ 可选 `sqlr mor_client_master < $REQ/release/sql/81-client-master-comment-rollback.optional.sql`（列注释恢复为备份原文）→ `upr client-master-service`。
5. **client-bff**：`upr client-console-bff`（必须在 Platform 回退之前：新 BFF 总要读 Platform 国家列表）。
6. **Platform**：先确认步骤 4、5 与 ops-bff 已回退（新接口再无调用方），并确认窗口内没有业务数据引用 SAR / EGP（回退会删除 ID 1003、1004 两行；运营新建的 Snowflake 币种与 AED 原行保留）。`stopc platform-support-service` → `sqlr mor_platform < $REQ/release/sql/89-platform-reference-data-rollback.sql`（反向改名、删列与新表）→ `upr platform-support-service` → 登录检查（Vault 503 则 `docker restart mor-test-vault-service-1`）→ `snap`。
7. **整库兜底**：任何一步回退 SQL 失败，在对应服务停止状态下从第 2 节备份恢复整库：`gunzip -c "$B/<db>.sql.gz" | sqlr`（会丢失窗口内该库的全部写入）。

演练证据（12:56Z 备份，断网容器）：逆序执行 80 + 81、62、69、89 后，两个库的**结构（含注释）与备份逐字相同**；数据差异只有 DML 触及行的 `update_time`（22 行：地址 12、Schema 字段 8、地址组件 2）与 PLATFORM_ADMIN 角色 `version` 51 → 54。

### 6.3 不可逆部分

| 范围 | 说明 |
|---|---|
| 币种 | 窗口内运营改的图标、新建的币种在回退后保留行但丢失新列；SAR / EGP 两行被删除 |
| 地址 | 窗口内新提交的 AE / EG 行政区（ISO）被改回下划线；SA 行政区本来不在旧目录（SA 地址无行政区组件，mor-test 0 行） |
| 分类 | 窗口内保存的 2 级行业分类保留；旧 client-master 可读（显示编码），旧 ops-web 下拉中不存在该值 |
| 语言包 | 发布不可变（见 6.2 第 1 步） |

## 7. 文件

- `sql/`（[sync_sql.sh](./sync_sql.sh) 生成）：`00`—`02` 只读预检；`10` Platform 变更、`11` 核对、`12` 行政区本地名补丁（**mor-test 不需要**：主脚本已含 CLDR 名称，演练证明执行后 0 行变化，可跳过）；`20`/`21` client-master DML 与注释；`60`—`62`/`69` 块 139；`80`/`81` client-master 回退；`88`（不需要，对应 12）/`89` Platform 回退；`SHA256SUMS`。
- 覆盖：`compose-candidate.yaml` / `compose-rollback.yaml` 与 6 组分服务文件。
- 工具：`harness/rehearse.sh` + `harness/BlockSnapshotCheck.java`（离线演练与 Gateway 编译）、`refdata_bundles.py`（语言包）、`acceptance/`（API 验收、浏览器冒烟生成器与预置）。
- 证据：`precheck/*.out`（mor-test 只读预检输出）、`evidence/rehearsal-20261027T1303Z/`（演练日志与每步输出）。

## 8. 可选后续：国旗上传（不在本次范围，需单独授权）

现状：`t_ref_country.logo_file_address` 全为空，前端显示占位图；`generator/upload_flags.py` 只实现了下载与生成 SQL，上传未实现。

| 问题 | 结论（2026-10-27 只读核对） |
|---|---|
| SVG 能否直接上传 | **不能**。mor-test 公开路由对应的扫描策略只收 png / jpeg（PUBLIC_IMAGE）或 pdf / png / jpeg（PUBLIC_DOCUMENT）。也**不建议**为此给公开策略加 `image/svg+xml`：SVG 可携带脚本，公开桶直接返回时有存储型 XSS 风险，且策略对该来源的所有上传生效 |
| 建议格式 | 本机把 249 个 4x3 SVG 转为 PNG（如 120×90，单个约 1—4 KB），保存转换前后 sha256 清单 |
| 上传入口（无需新注册） | 运营控制台公开上传 `/api/operations/file-upload/v1/uploads` → 预签名 PUT → `/uploads/{uploadId}/completion`（来源 operations-console-bff → PUBLIC 路由 → PUBLIC_DOCUMENT，收 png；需 `FILE_UPLOAD_CREATE`）。开始与完成两条路由每主体 10 次 / 分钟，249 个文件至少约 25 分钟。原因码只校验格式 `[A-Z][A-Z0-9_]{0,63}`，没有登记表 |
| 备选（不建议） | Platform 内部 `/internal/files/v1/uploads` 需在 mor-test-backend 内以内部身份调用，且 `platform-support` 来源的 PUBLIC 路由只收 JSON（I18N_BUNDLE_JSON），要换来源名或新增路由 |
| 回填 | `upload_flags.py emit-sql <CC,fileId 映射>` 生成 249 条 `UPDATE t_ref_country SET logo_file_address=…, version=version+1`，root 执行（Platform 没有国家维护接口）；回退：同样 249 行置 NULL、version+1 |
| 验证 | `/reference-data/countries` 的 `logoPublicUrl` 非空；客户门户国家下拉、运营参考数据国家页签显示国旗 |
| 授权 | 上传是对 mor-test 文件服务的写入，回填是 root DML：两项都需要用户单独授权；上传脚本需先补全（PNG 转换 + 运营上传链路），本次未实现、未运行 |
