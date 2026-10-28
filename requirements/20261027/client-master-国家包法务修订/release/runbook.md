# 国家包法务修订 · mor-test 发布运行卡

> 状态：**阶段 6 评审稿（2026-10-28）**。本文件只排步骤，不授权任何动作。所有改变环境的步骤（容器 up、root SQL、语言包写入）在阶段 7 由主会话执行，须用户对阶段 7 明确授权。
> 依据：[technique.md](../technique.md) 第 5 节发布顺序；[delivery.md](../delivery.md) 发布依赖。镜像一律钉 sha256（`:legal-20261028a` 标签仅供辨认）。

## 0. 前提与待决

| # | 事项 | 状态 |
|---|---|---|
| P-1 | 发布顺序：参考数据客户端组件（`e2185c1b`，04:08Z 上线）与卡 BIN（`c4ff0e20`，20:19Z 上线）都已提交并上线；本需求排在它们之后（两个会话已同意） | 已就绪 |
| P-2 | 5 个镜像已构建并核对（第 2 节）；Platform、Gateway、compliance、Vault **不换镜像**；无路由、无注册块、无 XXL 任务 | 已就绪 |
| P-3 | 执行前重读现网镜像：5 个服务应仍是第 1 节的回滚列。若有人在此期间替换过，停止，改为在新提交上重建（`compose-rollback.*.yaml` 也要改写） | 执行当天 |
| P-4 | 通知并行会话：将重启 client-master（约 30—60 秒：客户门户入驻、运营审核页、client-master 的 MQ 消费与 XXL 执行器）、client-console-bff（约 30 秒：客户门户全部接口）、operations-console-bff（约 30 秒：运营控制台全部接口）、两个 Web（数秒），并发布 client-console #7、operations-console #19 | 执行当天，主会话 |
| P-5 | **验收账号（待用户决定）**：mor-test 注册邮件走 SES，无法像本机那样自建客户测试账号；运营账号按规定不由 agent 创建。浏览器验收需要用户先在 Chrome 中登录客户门户（可用自己的客户账号）与运营控制台，再由主会话驱动页面（第 4 节）。不登录则只做第 4 节的接口 / 数据库核验，客户端浏览器项记为未验 | 待用户决定 |
| P-6 | 用户的 Al Waha 申请（240742014373699584，v1 草稿）：发布前后都可照常勾选声明并提交。存量申请的资料版本取默认 0，存量声明为空按 0 解释，发布后仍有效；之后再改资料才需重新勾选 | 已核对，无须处理 |

## 1. 现网基线（2026-10-28 阶段 6 读取，`docker inspect`）

| 服务 | 现网镜像（= 回滚目标） | 来源 | 本需求候选 |
|---|---|---|---|
| client-master-service | `sha256:64e515c69cbd0e0b8c34c1ef3386f757c5d180bff8ab91ebdfcc6601d1e9ad1f`（JAR `ec4a2b14`） | 参考数据客户端组件 | `sha256:c9bb7b8e30f6a44ae528b5054fd15a67e52a52743fc4fbed11436b08f21b8155`（JAR `a416b537`） |
| client-console-bff | `sha256:be79550ff3ed649758b164574e2c2a13fd389466b19cec27330746674a9e6910`（JAR `ca288863`） | 参考数据目录与 client-master 迁移 | `sha256:8fe7f24c3ef7e08fd5473e73a68f80f72248db607492b0223f2e25d327ce2a3b`（JAR `e91a2bc5`） |
| operations-console-bff | `sha256:4bd7574cad11747c54a608c57b51955a7860e1889d99a71f6569c40d9b528b01`（JAR `645b7673`） | 参考数据客户端组件 | `sha256:769edbcebd219db68eb9ea41514606f966329a16a458e1d62ea937634e8f924b`（JAR `6045fdab`） |
| client-console-web | `sha256:c09af02a9c4d84faa2aaf78b5d95b6b4e36e0a28c56050d1ae202c5e000e495e`（dist 11 个文件） | 参考数据目录与 client-master 迁移 | `sha256:3db62571940f24d50c87fa2e07143e3536a2b956ffb6db8542994bcd5011a9eb`（dist 11 个文件） |
| operations-console-web | `sha256:aec066e9d896fd69fe4340ff7cd90eee54f95a780cf335bde7bed4382dbae3e1`（dist 250 个文件） | 卡 BIN 库 | `sha256:45ee11cb4e8c788d557973c40bc5bea9bbc379b610c4aed8e84b9f65d34c81db`（dist 250 个文件） |

语言包当前：client-console **#6**（10 个命名空间、1423 个 ACTIVE key），operations-console **#18**（26 个命名空间、10157 个 ACTIVE key）。

`mor_client_master` 现状（[precheck-mortest-20261028.out](./precheck-mortest-20261028.out)）：新列 0；本 ID 段 0；8 个 v1 Schema ACTIVE；字段 275、规则 79、属性定义 10、标识类型 8、目录项 61；申请 APPROVED 37（无 Schema）/ DRAFT 3 / UNDER_REVIEW 8 / WITHDRAWN 6（均 v1）。

## 2. 构建与核对（阶段 6 已完成）

- **构建源**：`/Users/kenny/Desktop/Workspace/Code-MoR-legal-rel` = `git worktree add --detach … e2185c1b` + `Code-MoR-legal` 的 93 个未提交文件（81 改、12 新，按 `git status --porcelain` 复制，不含本机 `paytech/.mvn/maven.config`）。两棵树指纹相同 `ed85152cba943c84`（全部非忽略文件 sha1 清单再 sha1）。`Code-MoR-legal` 由 `c4b99e2d` 经 `git checkout -m e2185c1b` 迁移，重叠的 `ClientDraftConfiguration`、`ClientSubmissionPreparer` 自动合并、无冲突（改动前备份：`~/Desktop/Workspace/Code-MoR-backups/legal-20261028/legal-worktree-pre-rebase.{patch,untracked.tgz}`）。
- **测试（rebase 后）**：client-master 164/0（`mor_client_master_legal`）、client-console-bff 101/0、operations-console-bff 2433/0、client-console-web 141/141、operations-console-web 1576/1576；两个 Web 构建成功。增量相对 rebase 前（162 / 2423 / 1572）均来自上游新增用例。
- **Web 基线**：`e2185c1b` 干净构建的 dist 与现网逐字节相同（client-web 11 个文件、ops-web 250 个文件），所以候选 = 现网 + 本需求，不回退参考数据与卡 BIN 页面。
- **JAR 范围**（条目 CRC，对现网 JAR）：client-master +3 ~43，全部是本需求文件；ops-bff ~13，全部是 `paytech-client-master-api` 中本需求改的响应体 / 枚举；client-bff +3 −6 ~17，其中 9 个 `paytech-platform-support-api` 类是已上线提交（参考数据删除旧接口、卡 BIN 加账号标签接口）带来的，其余是本需求文件。
- **镜像**：Java 用 `test-environment/java/Dockerfile` + 现场 `javac --release 25` 编译的 health-probe；Web 用各自 Dockerfile。镜像内 JAR / dist 与构建产物逐字节相同。
- **env**：`scripts/env_check.sh` 渲染 10 个覆盖文件（5 个候选 + 5 个回滚），与运行容器逐键逐值相同（client-master 40、client-bff 16、ops-bff 20、两个 Web 各 7 键）。env 抄自现网覆盖文件，不新增键。
- **SQL 试跑**（本机临时库 `mor_client_master_legal_mt`，由 mor-test `mor_client_master` 只读导出恢复）：DDL → DML → 重跑（空操作）→ 回滚拒绝分支（把一个草稿申请指向 v2 Schema，回滚脚本 `STOP` 退出、无写入）→ 恢复后回滚（回到 275 / 79 / 10 / 8 / 61，v1 ACTIVE）→ 再执行。最终 6 张配置表与发布工作树 `schema.sql` + `initial-data` 全新建库逐行相同（不比较时间戳列；Schema 17、字段 602、规则 91、属性 18、标识类型 10、目录项 62）。

## 3. 执行顺序（阶段 7）

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
REQ=/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261027/client-master-国家包法务修订
up()  { ./test-environment/compose.sh -f "$REQ/release/compose-candidate.$1.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
upr() { ./test-environment/compose.sh -f "$REQ/release/compose-rollback.$1.yaml"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$1"; }
TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)
sqlr()  { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `docker inspect mor-test-<服务>-1 --format '{{.Image}}'`、healthy、`docker logs --since <T> … | grep -E 'ERROR|Exception'`。

| 步 | 内容 | 命令 | 通过条件 |
|---|---|---|---|
| ① | 备份 | `B=~/Desktop/Workspace/Code-MoR-backups/legal-20261028/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p "$B"`；`docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --databases mor_client_master > "$B/mor_client_master-full.sql"`；5 个服务 `docker inspect … {{.Image}}` 写 `$B/images-before.txt` | 镜像与第 1 节回滚列一致（P-3）；导出非空 |
| ② | 只读预检 | `sqlro mor_client_master < $REQ/release/sql/00-client-master-legal-precheck.sql` | 与第 1 节现状相同（新列 0、本段 0、v1 ACTIVE 8、275 / 79 / 10 / 8 / 61） |
| ③ | DDL | `sqlr mor_client_master < $REQ/release/sql/10-client-master-legal-ddl.sql` | 两列存在；不可重跑（列已存在报错时先查 `information_schema.COLUMNS`） |
| ④ | client-master | `up client-master-service` | `c9bb7b8e…` healthy；现有入驻草稿可打开；日志无 ERROR |
| ⑤ | DML | `sqlr mor_client_master < $REQ/release/sql/20-client-master-legal-pack.sql`（`@cm_legal_status` 缺省 ACTIVE） | 核验段 present_rows = pack_rows；Schema RETIRED 8 / ACTIVE 9、字段 602、规则 91、属性 18、标识类型 10、目录 62（同试跑） |
| ⑥ | BFF | `up client-console-bff` → `up operations-console-bff` | 各自 healthy；客户门户入驻页、运营审核页、对账页不 503 |
| ⑦ | Web | `up client-console-web` → `up operations-console-web` | 首页加载；入口 JS 与候选 dist 一致 |
| ⑧ | 语言包计划（只读） | `python3 -I $REQ/release/legal_bundles.py` | 与阶段 6 相同：client-console 当前 **#6**、新增 31、废弃 0、zh-CN 导入 31、缺 0；operations-console 当前 **#18**、新增 61、废弃 0、zh-CN 导入 61、缺 0；两应用命名空间不变、zhChangedNotImported 0。当前号不符或出现废弃即停 |
| ⑨ | 语言包发布 | 同一命令加 `--apply` | client-console **#7**、operations-console **#19** READY 且为当前；未触及的 zh-CN 不变；匿名 manifest 逐包 200。**Gateway 可能 504 但 Platform 已完成**：先 `sqlro mor_platform -e "SELECT a.application_code, r.release_no, r.status FROM t_i18n_release r JOIN t_i18n_application a USING (application_id) ORDER BY r.release_no DESC LIMIT 4"`，已 READY 就只重跑不带 `--apply` 的计划确认无差异，不要重复发布 |
| ⑩ | 验收 | 第 4 节 | 全部通过后更新 delivery.md、CURRENT_IMPLEMENTATION |

⑦ 与 ⑨ 之间新 Web 的中文新文案显示内置英文（预期，数分钟）。

## 4. 验收

**接口 / 数据库（不需登录）**
- ⑤ 的核验输出；`sqlro mor_client_master -e "SELECT scope_type, rule_code, COUNT(*) FROM t_rule_setting WHERE rule_setting_id BETWEEN 9270000000900000 AND 9270000000999999 GROUP BY 1, 2"`：JURISDICTION / UBO_THRESHOLD_COMPARISON 3；IDENTIFIER_TYPE（EG 两个 CR）ACQUIRE_MODE、VAULT_REQUIRED、EXPIRY_REQUIRED 各 2；DECLARATION / DOCUMENT_VERSION 2；ATTRIBUTE / DATE_NOT_PAST 1（同试跑）。
- client-master、两个 BFF 日志在 ④—⑦ 之后无 `No enum constant`、`IllegalArgumentException`（旧枚举读到新 scope_type 的典型错误）。

**浏览器（P-5，用户登录后；en / zh-CN × 1440 / 390，记录错误横幅、控制台错误、失败请求 requestId、原始 i18n key、溢出）**
- 客户门户：新建一份 AE 机构草稿（v2 表单：ERN、"其他"签发机关名称）→ 声明任务：阅读面板显示 "Platform Terms of Use" / "Privacy Notice" 草案正文，无正文时勾选框禁用 → 勾选后改一项资料 → 声明显示需重新确认 → 重新勾选；SA 个人自由职业者表单出现自由职业证件号与到期日；EG 机构统一 / 地方 CR 二选一。草稿可在验收后撤回。
- 运营控制台：入驻配置的规则视图出现 DECLARATION / ATTRIBUTE 行与中文标签、UBO 比较 GTE；审核详情的声明区（取值、文档版本、是否当前、声明人）；标志与布尔值显示 是 / 否。
- 回归：卡 BIN 库页、参考数据页（币种、国家、行业 / MCC）、对账页可打开（确认没有回退其他会话的页面）。

## 5. 回滚（逆序；发生在验收失败或新镜像 180 秒内不 healthy 时）

| 顺序 | 动作 | 说明 |
|---|---|---|
| 1 | `upr operations-console-web` → `upr client-console-web` | 回到第 1 节镜像 |
| 2 | `upr operations-console-bff` → `upr client-console-bff` | 同上 |
| 3 | DML 回退：`sqlr mor_client_master < $REQ/release/sql/90-client-master-legal-pack-rollback.sql` | **有 v2 / 自由职业者申请或引用新代码的资料时拒绝执行**（`STOP:`，无写入）。此时不能回退 client-master 镜像（旧镜像读不了 DECLARATION / ATTRIBUTE 规则行），只能前向修复或先由业务处理这些申请 |
| 4 | `upr client-master-service` | 只在第 3 步成功后执行 |
| 5 | DDL | 两列保留（无害，旧镜像不读）；需要时按 `10-…` 文件头的 DROP COLUMN 语句执行 |
| 6 | 语言包 | 本次只新增 key、不废弃，旧 Web 读 #7 / #19 不受影响，**不回退**；发布不可变 |

只回退单个 Web 或 BFF 时，其余服务可保持新版本（新增字段对旧调用方是可忽略的附加字段）。

## 6. 文件

- `compose-candidate.<服务>.yaml` / `compose-rollback.<服务>.yaml`（5 + 5）；`scripts/env_check.sh` + `scripts/envcmp.py`（抄自参考数据客户端组件需求）。
- `sql/00-…precheck.sql`（只读）、`sql/10-…ddl.sql`、`sql/20-…pack.sql`、`sql/90-…rollback.sql`：后三个与 `paytech/client-master/docs/database/sql/changes/2026-10-27-legal-pack-revision{-ddl,,-rollback}.sql` 逐字节相同。
- `legal_bundles.py`（包装参考数据发布的 `refdata_bundles.py`，同卡 BIN 的做法）与 `i18n-bundles/`（计划与导入文件）。例外：client-console 的 `onboarding.page.not_found` / `reload` 线上中文来自多语言管理 #1，与仓库种子不同，保持线上（与本需求无关）。
- `precheck-mortest-20261028.out`：阶段 6 只读预检输出。
