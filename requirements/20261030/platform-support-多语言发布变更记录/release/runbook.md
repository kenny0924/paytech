# 多语言发布变更记录 · mor-test 发布手册（阶段 6 准备，阶段 7 执行）

> **2026-10-30 已执行**（阶段 7，结果见 delivery.md）。候选按联合查询 jq-20261030b 重建：现网 / 回滚 `c82b6f34`，候选 `a3003ef6`（`i18nchg-20261030b`，构建临时提交 `7c7d8110` → `c633fd0e`）；下表第 1 节为最初基于 jq-20261030a 的记录。

## 1. 候选与基线

| 项 | 值 |
|---|---|
| 现网（= 回滚镜像） | `sha256:9444b483…` `mor-test/platform-support-service:jq-20261030a`（联合查询发布，2026-10-30 07:12Z 完成） |
| 候选 | `sha256:a90a882900ef07ead58f81877f48da439a54f4bd6276df30d52cd5f11c62c55a` `mor-test/platform-support-service:i18nchg-20261030a` |
| 候选构建 | 现网镜像之上只替换 `/app/application.jar`（Dockerfile 两行，`COPY --chown=10001:10001`）；镜像 Config 与现网逐项相同，层数 8 → 9 |
| JAR 源 | `/Users/kenny/Desktop/Workspace/Code-MoR-i18nrel`：main `6eaf1bd3` + 联合查询未提交源码（`Code-MoR-jq-rel` 236 个文件，临时提交 `899c07d8`）+ `69f87bbf`（挑选为 `15890412`，`PlatformDataCleaner` 表清单冲突，取两边新表） |
| 基线证明 | `899c07d8` 构建（`paytech/pom.xml -pl platform-support/paytech-platform-support-app -am package`）与现网 JAR：83,969 个条目（含内层 JAR）**0 差异** |
| 候选差异 | 相对现网 23 个条目，全部是本需求：多语言模块 21 个类 / Mapper XML（新增 6、删除读包端口 2）与 `PersistenceConfiguration` |
| 与阶段 5 验收 JAR | 多语言模块逐字节相同；差异只在联合查询（jq-m → jq-rel）与税务共享条目 |
| compose | [compose-candidate.platform-support-service.yaml](./compose-candidate.platform-support-service.yaml) / [compose-rollback.platform-support-service.yaml](./compose-rollback.platform-support-service.yaml)；`compose.sh config` 渲染：候选相对现网只差 `image` 一行，回滚与现网完全相同 |

## 2. 只读预检结果（2026-10-30 07:42Z）

- mor-test `mor_platform`：operations-console 25 个发布、client-console 8 个，共 33；`t_i18n_release_change` 与 `change_count` 均不存在；语言包行 1,284。
- 补录试算（[mor-test/plan-dryrun.out](./mor-test/plan-dryrun.out)）：168 个语言包（去重）SHA-256 全部一致，约 7 s；变化行合计 26,606（ops #3 16,228 行最大）。试算 SQL 7 MB 不入库，存 `~/Desktop/Workspace/Code-MoR-backups/i18nchg-20261030/dryrun-backfill-plan.sql`。
- 离线演练（本机 `mor-local-mysql-1` 临时库，复制 mor-test 4 张表，演练后删除）：DDL 执行两次（第二次跳过）→ 补录 SQL 约 1 s → `verify` 33/33 一致、未补录 0（[mor-test/rehearsal-verify.out](./mor-test/rehearsal-verify.out)）→ 再执行一次行数不变（26,606，主键无重复）→ 重新 `plan` 全部「已补录，跳过」→ 回滚 SQL 后表与列均不存在。
- 修复前读数（旧 Platform，只读，[verification/mor-test/before-old-platform.json](../verification/mor-test/before-old-platform.json)）：ops 预检第一轮 504，第二轮 4.5—7.0 s；client 1.3—1.7 s；差异 2.4—8.9 s；测量期间 `FILE_STORAGE_ACCESS` REVEAL 958 次；预检摘要与独立基准一致（证明基准脚本在 mor-test 数据上可用）。

## 3. 执行步骤（阶段 7）

公共变量（不打印密码）：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
R="requirements/20261030/platform-support-多语言发布变更记录"
SQL=/Users/kenny/Desktop/Workspace/Code-MoR-i18nrel/paytech/platform-support/docs/database/sql/changes
export MYSQL_PWD="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)"
sqlr() { docker exec -i -e MYSQL_PWD mor-test-mysql-1 mysql -uroot mor_platform "$@"; }
BF() { python3 "$R/release/scripts/backfill_release_changes.py" "$1" --container mor-test-mysql-1 --db mor_platform --storage-url https://127.0.0.1:29400 --insecure "${@:2}"; }
B=~/Desktop/Workspace/Code-MoR-backups/i18nchg-20261030/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"
```

| # | 步骤 | 命令 | 通过条件 |
|---|---|---|---|
| ① | 现网读数 | `docker inspect mor-test-platform-support-service-1 --format '{{.Image}} {{.State.Health.Status}}'`；`sqlr -N -e "SELECT a.application_code, COUNT(*), MAX(r.release_no) FROM t_i18n_release r JOIN t_i18n_application a USING(application_id) GROUP BY 1"` | 镜像仍是 `9444b483` 且 healthy（否则停止，重新确定基线）；发布数记录到 `$B/before.txt` |
| ② | 通知 | 告知联合查询等并行会话：窗口内不要发布多语言、不要发布 Platform | 已发送 |
| ③ | 备份 | `docker exec -e MYSQL_PWD mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --databases mor_platform > "$B/mor_platform.sql"`；`shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 非空，以 `Dump completed` 结尾 |
| ④ | DDL | `sqlr < "$SQL/2026-10-30-i18n-release-change.sql"` | 表存在；`change_count` 列存在且全部 NULL；旧 Platform 照常（不读新表 / 列） |
| ⑤ | 补录 | `BF plan --out "$B/backfill-plan.sql" > "$B/plan.out"`；与 [plan-dryrun.out](./mor-test/plan-dryrun.out) 比较（只允许多出 ① 之后的新发布）；`sqlr < "$B/backfill-plan.sql"`；`BF verify > "$B/verify-1.out"` | `verify` 退出码 0：不一致 0、未补录 0 |
| ⑥ | 换 Platform | `./test-environment/compose.sh -f "$PWD/$R/release/compose-candidate.platform-support-service.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service` | 容器镜像 = `a90a8829`、healthy；启动日志无 ERROR；登录正常（约 60 s 不可用） |
| ⑦ | 补缺 | `BF plan --out "$B/backfill-gap.sql"`；若有「待写入」则 `sqlr < "$B/backfill-gap.sql"`；`BF verify > "$B/verify-2.out"` | ⑤ 与 ⑥ 之间旧 Platform 发布的版本被补齐；`verify` 退出码 0 |
| ⑧ | 只读验收 | `E2E_TARGET=test I18NCHG_CONTAINER=mor-test-mysql-1 I18NCHG_DB=mor_platform I18NCHG_STORAGE=https://127.0.0.1:29400 I18NCHG_VAULT=mor-test-vault-service-1 I18NCHG_WRITE=0 python3 "$R/verification/local-e2e/e2e_i18nchg.py" "$R/verification/local-e2e/expected_summary.py" "$R/release/scripts/backfill_release_changes.py" "$R/verification/mor-test/after-readonly.json"` | AC-01/02/03/06 全部 PASS；差异条数与修复前读数相同（ops #19 = 122、#19 对 #1 = 19,618、client #5 = 2） |
| ⑨ | 写入验收（须单独确认） | 同上，`I18NCHG_WRITE=1 I18NCHG_PUBLISH=client-console`，输出 `after-write.json`：改一条 zh-CN 译文并还原、另一条改了又改回；以相同内容发布 client-console #9 | AC-03（只计 1 条）、AC-05/09（#9 `change_count=0`、差异为空）；两条译文还原原文；之后 `BF verify` 34/34 |
| ⑩ | 浏览器 | `I18NCHG_ORIGIN=http://localhost:28080 node "$R/verification/local-e2e/browser_i18nchg.mjs" "$R/verification/mor-test/screens"`（凭据由 `e2e_http.secrets()` 经环境变量传入，不落盘） | AC-08：1440 / 390 × zh-CN / en，发布记录详情、切回确认、发布弹窗正常，无横向溢出 |
| ⑪ | 收尾 | 通知并行会话窗口结束；delivery.md 记录镜像、读数、验收结果 | — |

## 4. 回滚

| 情形 | 操作 |
|---|---|
| ⑥ 之后 Platform 异常 | `./test-environment/compose.sh -f "$PWD/$R/release/compose-rollback.platform-support-service.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 platform-support-service`；确认镜像 = `9444b483`、healthy。旧代码不读写新表 / 列，**DDL 与补录数据可保留**，下次前滚时补录脚本只补缺失的发布 |
| 放弃本需求 | 旧镜像起来后执行 `sqlr < "$SQL/2026-10-30-i18n-release-change-rollback.sql"`（删表、删列，可重跑，演练已验证） |
| ④ / ⑤ 失败 | Platform 未换，直接执行回滚 SQL；补录按发布单事务提交，失败的发布 `change_count` 仍为 NULL，修正后重跑 `plan` 只写剩余发布 |

注意：回滚到旧镜像后若又发布了新版本，该版本不写明细；再前滚前重新执行 ⑤ 即可（脚本幂等）。
