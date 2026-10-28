# card：BIN 库页面重设计与号段列表 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-27）**。本文件只排步骤，不授权任何动作。**所有改变环境的步骤由主会话执行**（容器 up、root SQL、语言包写入），须用户对阶段 7 明确授权；agent 只做备份、只读核对与记录。
> 依据：technique.md 第 3.4 节（块 140 先休眠后激活）；现网基线 [baseline.md](./baseline.md)。
> 镜像一律钉 sha256（tag `binlist-20261027a` 只供辨认，`:development` 未改动）。不加 `--remove-orphans`；一次只换一个服务。
> **ops-web 步骤 ⑧ 以 c4b99e2d 为基础（D-1，team-lead 已同意，用户在阶段 7 确认点确认）**；未获确认时跳过 ⑧—⑩，其余步骤仍可独立执行（新 BFF / 路由对旧页面无影响）。
> **提交基线**：阶段 8 提交必须基于 c4b99e2d（或之后的 main），按 `harness/release-files.txt` 的 57 个路径放到 main 之上；不得以 5e1f16ab 为父重放（会回退 c4b99e2d 的 ops-web 改动）。

## 0. 前提（执行前逐项清零）

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 4 个候选镜像已在本机构建并核对（镜像内制品与构建产物逐字节相同）：Platform `186347bc…`、card `380064fe…`、ops-bff `25f6a869…`、ops-web `aec066e9…`（D-1） | 已完成 |
| P-2 | 代码冻结：重算指纹（命令见第 1 节 `fp`），研发树与 Java 构建树仍为 `33406c1159595015`、webrel 仍为 `0b8765bdfc415d43`。不一致就停，报告差异文件并重建受影响镜像 | 执行当天 |
| P-3 | 现网未变：`img` 输出的 4 个运行镜像仍等于 baseline 第 1 节（Platform `aec53586`、card `810b6b72`、ops-bff `48113289`、ops-web `2e179d6f`）。**任何一个变了就停**：说明有人在此期间发布了同服务，回滚目标与"候选 = 现网 + 本需求"的证明都失效，需重做该服务的基线比对 | 执行当天 |
| P-4 | 决定 D-1—D-6（baseline 第 10 节）已由用户确认；尤其 D-2：`Code-MoR-refclient` 会话是否已发布 card / Platform / ops-bff | 执行当天 |
| P-5 | 通知并行会话（refclient、legal、fileupload-ux 及当时活跃的会话）：将重启 Platform（约 30—60 秒：运营 / 客户门户登录、Gateway 快照刷新暂停但沿用上一快照、文件 / 多语言 / 工作流 / 数据查询接口）、card（约 30—60 秒：payment 取卡属性 / 3DS、card XXL 执行器重注册）、ops-bff（约 30 秒：运营控制台全部接口）、ops-web（数秒），注册并激活块 140，发布 operations-console 语言包 #18 | 执行当天，主会话 |
| P-6 | 重跑只读预检（第 3 节 ①），结果须与 baseline 第 5 节一致；路由计数不同（他人注册了新块）时只更新期望值（激活后 = 当时 ACTIVE + 1），并用第 2 节新备份重跑离线演练：`"$REL/harness/rehearse.sh" <新备份.gz> /Users/kenny/Desktop/Workspace/Code-MoR-binlist-rel/paytech/platform-support/paytech-platform-support-app/target/paytech-platform-support-app-1.0.0-SNAPSHOT-exec.jar ~/Code-MoR-backups/20261027-binlist-mortest-prep/rehearsal-<UTC>`（工作目录放仓库外：含整库导出），全部 `COMPILE OK` 才继续 | 执行当天 |

## 1. 约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR                # compose.sh 所在主工作区
REQ=/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/card-BIN库页面重设计与号段列表
REL="$REQ/release"
up()  { ./test-environment/compose.sh -f "$REL/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr() { ./test-environment/compose.sh -f "$REL/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
set -a; . ./test-environment/.env.test; set +a
sqlr()  { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 --init-command="SET SESSION TRANSACTION READ ONLY" -t "$@"; }
img()  { docker inspect "mor-test-$1-1" --format '{{.Image}} {{.State.Health.Status}}'; }
snap() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -E 'Snapshot 刷新|CONTROL_PLANE' | tail -3; }
errs() { docker logs --since "$2" "mor-test-$1-1" 2>&1 | grep -c ' ERROR '; }
fp()   { (cd "$1"; git ls-files --cached --others --exclude-standard -z | grep -zv '^operations-console-web/node_modules' | xargs -0 shasum 2>/dev/null | sort -k2 | shasum | cut -c1-16); }
T0=$(date -u +%Y-%m-%dT%H:%M:%SZ)                         # 发布开始时间：日志窗口与验收 --since
```

SQL 文件在 `release/sql/`（`SHA256SUMS` 核对：`(cd "$REL/sql" && shasum -a 256 -c SHA256SUMS)`）：`00` 只读预检、`10` 注册（= 需求级 `db/platform-support/dml.sql`，与 `platform-support-initial-data.sql` 块 140 逐行相同）、`11` 激活、`12` 停用、`13` 执行后只读核对、`19` 注册回退（路由仍 ACTIVE 时自动拒绝）。

## 2. 备份与回滚列（第 ⓪ 步）

```bash
B=~/Code-MoR-backups/20261027-binlist-mortest-prep/release-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$B"
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces \
  --set-gtid-purged=OFF --routines --triggers --events --databases mor_platform | gzip -c > "$B/mor_platform.sql.gz"
(cd "$B" && shasum -a 256 *.gz > SHA256SUMS); gunzip -c "$B/mor_platform.sql.gz" | tail -1
for c in platform-support-service card-service operations-console-bff operations-console-web api-gateway vault-service payment-service; do echo "$c $(img $c)"; done > "$B/images-before.txt"
```

通过条件：dump 以 `Dump completed` 结尾；`images-before.txt` 前 4 行等于 baseline 第 1 节（= 4 个 `compose-rollback.*.yaml` 的镜像）。本需求不改 `mor_card`、不改表结构；整库恢复只作兜底（第 6.3 节）。

## 3. 执行顺序

预计总时长约 25—35 分钟（服务中断：Platform 30—60 秒、card 30—60 秒、ops-bff 约 30 秒、ops-web 数秒）。

| 步 | 内容 | 命令 | 预期 / 通过条件 | 预计耗时 |
|---|---|---|---|---|
| ① | 只读预检 | `sqlro mor_platform < "$REL/sql/00-platform-block140-precheck.sql"` | 与 baseline 第 5 节一致：块 140 区间 0；新 code / 路径 / 下游路径 0；权限 132101 ACTIVE、target …001 ACTIVE、132405 ACTIVE 且守卫同形；ACTIVE 612（不同则按 P-6 处理） | 1 分钟 |
| ② | Platform | `up platform-support-service` → `img platform-support-service` → 10 秒后 `snap $T0` → 运营控制台登录 | `sha256:186347bc… healthy`；Gateway 恢复 `刷新成功 … routeCount=612`（重启期间出现 `CONTROL_PLANE_UNAVAILABLE` 属预期，沿用上一快照）；登录 200——若 503 且含 `No servers available for service: vault-service`，`docker restart mor-test-vault-service-1`，约 20 秒后重试；`errs platform-support-service $T0` = 0。新增接口只供新 ops-bff 调用，旧 ops-bff 不受影响 | 1—2 分钟 |
| ③ | card | `up card-service` → `img card-service` | `sha256:380064fe… healthy`；`errs card-service $T0` = 0；`docker logs --since $T0 mor-test-payment-service-1 2>&1 \| grep -ci 'card-service'` 只在重启窗口内出现。旧 ops-bff 不调用新接口 | 1—2 分钟 |
| ④ | ops-bff | `up operations-console-bff` → `img operations-console-bff` | `sha256:25f6a869… healthy`；`errs operations-console-bff $T0` = 0；现网旧页面打开卡 BIN 库：版本 / 激活列表 200（新字段 `createdByLabel` / `operatorLabel` 旧前端忽略）；对账页不 503（4 个 BASE_URL 已带） | 1 分钟 |
| ⑤ | 块 140 休眠注册 | `sqlr mor_platform < "$REL/sql/10-platform-block140-registration.sql"` → `sqlro mor_platform < "$REL/sql/13-platform-block140-verify.sql"` → 10 秒后 `snap $T0` | 10 无输出、退出码 0；13：route DISABLED v1、header 2、query 6、guard 6、guard_shape_diff 0、routes_active 612；快照仍 `刷新成功 routeCount=612`、无 `CONTROL_PLANE_UNAVAILABLE`（离线编译：loaded 619、effective 612） | 1 分钟 |
| ⑥ | 激活块 140 | `sqlr mor_platform < "$REL/sql/11-platform-block140-activate.sql"` → 10 秒后 `snap $T0` → `sqlro mor_platform < "$REL/sql/13-platform-block140-verify.sql"` | 11 输出 `routes 1`；快照 **`routeCount=613`**；13：ACTIVE v2、routes_active 613。此时旧页面不调用新路由，无用户可见变化 | 1 分钟 |
| ⑦ | 接口冒烟（新路由） | 运营控制台登录后，浏览器开发者工具或 ⑨ 的脚本只跑 R 组：`python3 -I "$REL/acceptance/binlist_mortest.py" --since $T0`（全量，2—3 分钟） | R / G / L 组 0 FAIL 才继续换 Web；失败按第 6 节处理 | 3 分钟 |
| ⑧ | ops-web（**D-1**） | `up operations-console-web` → `curl -s http://localhost:28080/ \| grep -o 'assets/index-[^"]*\.js'` | `sha256:aec066e9… healthy`；入口 `assets/index-D0P_eShl.js`；卡 BIN 库为新页面。语言包 #18 发布前 zh-CN 下新文案显示内置英文（预期，数分钟） | 1 分钟 |
| ⑨ | 语言包计划（只读） | `python3 -I "$REL/binlist_bundles.py" --accept-deprecations` | 与阶段 6 计划相同：当前 **#17**、新增 121、基准变更 7、废弃 68（全部 `card`）、zh-CN 导入 128、缺 0、zhChangedNotImported 0、命名空间不变 26。当前发布号不是 #17 或废弃集合超出 `card` 即停（有人在此期间发布过，先查清） | 1 分钟 |
| ⑩ | 语言包发布 | 同一命令加 `--apply` | key 导入预览 = 计划 → zh-CN 增量导入 128 → 集合外 zh-CN 未变 → 确认基准已变更译文 1 条 → 预检可发布 → 发布 **#18** READY 且为当前（52 个语言包）→ 匿名 manifest 逐包 200、摘要 = hash。**发布请求经 Gateway 可能 504 但 Platform 已完成**（文件上传页面整理 #15—#17 均如此）：504 时先 `sqlro mor_platform -e "SELECT release_no,status FROM t_i18n_release r JOIN t_i18n_application a USING (application_id) WHERE a.application_code='operations-console' ORDER BY release_no DESC LIMIT 2"`，#18 READY 就只重跑不带 `--apply` 的计划确认无差异，不要重复发布 | 5—10 分钟 |
| ⑪ | 验收（API） | `python3 -I "$REL/acceptance/binlist_mortest.py" --since $T0` | 0 FAIL；结果 `verification/acceptance-mor-test-<UTC>.json`。H 组 4 个服务 ERROR 0、healthy | 3 分钟 |
| ⑫ | 验收（浏览器） | `for l in en zh-CN; do for w in 1440 390; do node "$REL/acceptance/bin_smoke.mortest.mjs" $l $w; done; done` | 4 组全部 PASS，退出码 0：入口为候选 dist；默认 v1 17 条；前缀 401288 → 2 条 + 匹配条；Visa chip；999999 无结果；16 位卡号拦截不发请求；v2 → 8 条；版本 v1—v6；操作人为脱敏邮箱（无 18 位 ID）；v3 错误行；v2 预览；激活 / 导入弹窗只打开不提交；**zh-CN 页签为"号段数据 / 版本"**；**国旗图片全部加载**；findings 无 pageerror / console-error / 4xx/5xx / 原始 key / 横向溢出。截图与 findings 写 `verification/ui-smoke/`（前缀 `mortest-`） | 5 分钟 |
| ⑬ | 收尾 | `for c in …; do echo "$c $(img $c)"; done`（同第 2 节）；记录 delivery.md 阶段 7 行、`CURRENT_IMPLEMENTATION.md`；把 refclient / legal 会话需要的新基线（本次镜像源 = 5e1f16ab + 本需求；ops-web = c4b99e2d + 本需求）通知它们 | 4 个服务为候选镜像且 healthy；Gateway 613 | — |

验收数据约束：全部只读（GET）+ 管理员登录产生的会话与审计记录；不新建人员、不导入 BIN 文件、不激活 / 回切版本、不写业务表；无"业务时间 ≥ 配置生效时间"问题（本需求无生效时间配置）。

**可选步骤 O-1（不在默认范围，需用户单独批准）**：在 mor-test 真实激活 v6 再回切 v1，验证新弹窗端到端。激活逻辑本需求未改（只改样式），本机验收亦未真实提交；会改变 mor-test 生效 BIN 版本两次（payment 在窗口内按 v6 取卡属性），默认不做。

## 4. 顺序与兼容性依据

| 边 | 旧 ↔ 新 | 结论 |
|---|---|---|
| 新 ops-bff → Platform labels | 新 BFF 需要新 Platform；Platform 不可用 / 旧 Platform 返回 404 时 BFF 只记日志、显示名为 null，列表仍 200（阶段 4 测试覆盖） | **Platform（②）先于 ops-bff（④）**；即使顺序颠倒也不致故障 |
| 新 ops-bff → card ranges | 新转发需要新 card；旧 card 返回 404 → 只影响新页签 | **card（③）先于 ops-bff（④）** |
| 旧 ops-web ↔ 新 ops-bff | 版本 / 激活响应只加可空字段；旧解码器忽略未知字段 | ④ 后旧页面照常 |
| 块 140 ↔ 镜像 | 休眠注册不依赖镜像；激活后路由指向 ops-bff `/card-admin/v1/bin-library/versions/{versionNo}/ranges` | **⑥ 在 ④ 之后**（旧 BFF 无此路径会 404）；离线编译证明休眠 / 激活都能编译 |
| 新 ops-web ↔ 路由 | 新页面默认页签即调用 ranges | **⑧ 在 ⑥ 之后**，否则打开页面即 403 / 404 |
| 语言包 ↔ Web | 新 Web 未发布 #18 前 zh-CN 回退内置英文；旧 Web 读 #18 时被废弃的 68 个旧 key 回退内置英文 | **⑩ 在 ⑧ 之后**，两步连续执行 |

## 5. 语言包计划（阶段 6 只读计算，2026-10-27 19:35Z）

`binlist_bundles.py` 复用参考数据发布的 `refdata_bundles.py`（mor-test 已用于 #13、#15—#17），只改应用（operations-console）、源（`Code-MoR-binlist-webrel` + refdata 语言包）、发布说明、记录目录（`~/Code-MoR-backups/20261027-binlist-i18n`），dry-run 时所有 SQL 走 READ ONLY 会话，并新增两道门禁：废弃只允许在 `card`、仓库 zh-CN 与线上不同却不会导入的 key 为 0（3 个 `nav.sidebar` 用户批准例外除外）。导入文件：`i18n-bundles/operations-console.release.{en.json,zh-CN.delta.json,plan.json}`。

| 当前 | 仓库命名空间 | 仓库 key | 线上 ACTIVE | 新增 | 基准变更 | 废弃 | zh-CN 导入 | 待确认译文 | 期望发布 |
|---|---|---|---|---|---|---|---|---|---|
| #17 | 26 | 10157 | 10104 | 121（card） | 7（card） | 68（card） | 128 | 1 | #18，52 个语言包 |

## 6. 回滚

### 6.1 触发条件

| 步 | 触发 | 回滚范围 |
|---|---|---|
| ② | 180 秒内不 healthy；Gateway 持续 `CONTROL_PLANE_UNAVAILABLE` 超过 1 分钟；重启 Vault 后登录仍失败；ERROR 日志 | `upr platform-support-service`（ops-bff 若已换新，可保留：显示名降级为 null） |
| ③ | 不 healthy；ERROR；payment 窗口外仍报 card 调用失败 | `upr card-service`（若 ⑥ 已激活，先执行 `12` 停用块 140，避免新路由指向不支持的接口） |
| ④ | 不 healthy；旧页面版本 / 激活列表非 200；对账页 503 | `upr operations-console-bff`（同上，先停用块 140） |
| ⑤⑥ | 快照刷新失败（`CONTROL_PLANE_UNAVAILABLE`）或 routeCount 不符 | ⑤ 后 → `19`；⑥ 后 → `12` → `19` |
| ⑦⑪ | 任一 FAIL 且不是验收脚本自身问题 | 按失败所在服务 |
| ⑧⑫ | 页面 5xx / pageerror / 原始 key 大量出现（#18 前的英文回退不算） | `upr operations-console-web`（若 ⑩ 已发布，再按 6.2 第 1 步恢复旧 key 的中文） |
| ⑨⑩ | 计划 / 预览 / 预检与计划不符 | 停止，不发布（已导入未发布的 key 无害，新 Web 仍可用内置英文） |

### 6.2 全量回滚（逆序；每步后核对 `img`、healthy、`errs`）

1. **语言包**：发布不可变，不删除。旧 Web（c4b99e2d）读 #18 时，68 个被废弃的旧 BIN 页 key 回退内置英文（功能不受影响）。需要中文时以现网源码重新发布：`python3 -I "$REL/binlist_bundles.py" --web-root /Users/kenny/Desktop/Workspace/Code-MoR-binlist-c4b --accept-deprecations`（计划应为：恢复 68、废弃 121、基准变更 7，均在 `card`）→ 核对后加 `--apply` → 期望 #19。
2. **块 140**：`sqlr mor_platform < "$REL/sql/12-platform-block140-deactivate.sql"`（routes 1；快照回到 612）→ `sqlr mor_platform < "$REL/sql/19-platform-block140-rollback.sql"`（guard 6、query 6、header 2、route 1；路由仍 ACTIVE 时脚本以 ERROR 1242 拒绝、不删任何行）。离线演练证明执行后 `mor_platform` 结构与数据与执行前**逐字相同**。
3. **Web / BFF**：`upr operations-console-web` → `upr operations-console-bff`。
4. **card**：`upr card-service`。
5. **Platform**：`upr platform-support-service` → 登录检查（Vault 503 则 `docker restart mor-test-vault-service-1`）→ `snap $T0`。

### 6.3 不可逆 / 兜底

| 范围 | 说明 |
|---|---|
| 数据库 | 本需求只在 `mor_platform` 写块 140 的 15 行网关配置，回退脚本可精确删除；整库兜底：Platform 停止状态下 `gunzip -c "$B/mor_platform.sql.gz" \| sqlr`（会丢失窗口内 `mor_platform` 的全部写入，含他人操作与登录会话），仅在 `19` 失败且无法修复时使用 |
| 语言包 | 发布不可变（见 6.2 第 1 步） |
| 业务数据 | 无写入（本需求只读接口；验收不激活 BIN 版本） |

## 7. 文件

- `sql/`：`00` 预检、`10` 注册、`11` 激活、`12` 停用、`13` 核对、`19` 注册回退、`SHA256SUMS`。
- 覆盖：`compose-{candidate,rollback}.{platform-support-service,card-service,operations-console-bff,operations-console-web}.yaml`（ops-web 候选标注 D-1）。
- 工具：`harness/rehearse.sh` + `harness/BlockSnapshotCheck.java`（离线演练与 Gateway 编译）、`harness/tools/{jarcmp,distcmp,envcmp}.py`、`harness/release-files.txt`（本需求 57 个路径）、`binlist_bundles.py`（语言包）、`acceptance/binlist_mortest.py`（接口验收）、`acceptance/bin_smoke.mortest.mjs`（浏览器冒烟）。
- 证据：`precheck/mortest-block140-precheck-*.out`、`evidence/rehearsal-20261027T1932Z/`（日志与每步输出；整库导出在 `~/Code-MoR-backups/20261027-binlist-mortest-prep/`，不入库）、`i18n-bundles/`。
