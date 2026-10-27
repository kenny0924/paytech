# 全球合规入驻与资格切片 1 · mor-test 发布运行卡（草稿）

> 状态：**草稿（2026-10-27）**。阶段 7 由主会话执行；本文件只排步骤，不授权任何动作。所有容器变更、root SQL、XXL 写入在主会话执行（agent 只做备份、只读核对与记录）。
> 依据：technique.md 第 3 节发布顺序；现网基线 [baseline.md](./baseline.md)；块 138 门禁 [verification/infra-dev-report.md](../verification/infra-dev-report.md) 第 7 节。
> 镜像一律钉 sha256。尚未构建的镜像写作 `sha256:<TBD-服务>`，构建后回填本文件与 `compose-candidate.yaml`，并核对镜像内 JAR / dist 与构建产物逐字节相同（不用 `:development`）。

## 0. 前提与待决（执行前必须清零）

| # | 事项 | 负责 | 状态 |
|---|---|---|---|
| P-1 | 7 个镜像构建并回填 sha256：client-master、compliance、client-console-bff、client-console-web、operations-console-bff、operations-console-web、**platform-support**（S-11 运营菜单资源能力清单；与现网 JAR 只差 `console-resource-capabilities.json` 与 `catalog/events.yaml` 两个资源）。channel-connector、Gateway、Vault **不换镜像** | rel-build 2026-10-27 | 已构建（tag `onboard-20261027a`，镜像内 JAR / dist 与构建产物逐字节相同；源快照指纹见 [rel-build-report](../verification/rel-build-report.md)） |
| P-2 | `release/compose-candidate.yaml`：合并 [compose-candidate.channel-connector.draft.yaml](./compose-candidate.channel-connector.draft.yaml) 与 6 个服务的条目；**逐键带上基线第 2 节的现网 env**（ops-bff 的 4 个 `OPERATIONS_*_BASE_URL`；relsnap / fileupload / i18n 覆盖文件所在 worktree 不保证存在，键值抄进本文件）；同时写 `compose-rollback.yaml`（全部为基线镜像） | rel-build 2026-10-27 | 已写：合并 `compose-candidate.yaml` / `compose-rollback.yaml` 与 6 个分服务文件（`compose-{candidate,rollback}.<service>.yaml`）；渲染 env 与运行容器逐键逐值核对，见报告第 3 节 |
| P-3 | compliance env：`ONBOARDING_REVIEW_ENABLED=true`（现网已有，对应 `onboarding.review.enabled`；工作台、结果接口与逾期任务都受它控制）；可选 `COMPLIANCE_SCREENING_CHANNEL_CODE`（缺省 `S_SCREENING_MOCK`，mor-test 不设）。出站经服务发现，无新增地址键。**删除** `ONBOARDING_MATERIALS_ENABLED`（现网 compliance 有此键；`onboarding.materials.*` 已移除，留着无害但应在候选覆盖文件中去掉）与 `ONBOARDING_MATERIALS_PROGRAMS_JSON`（现网无） | cmp-dev | 已确认 |
| P-4 | client-master 国家包增量：`changes/2026-10-27-onboarding-country-pack.sql`（发布副本 `22-…`） | cm-sql | 已提供，已试跑 |
| P-5 | compliance 初始化 DML：`compliance-initial-data.sql` 在 DDL 之后、同一会话先 `SET @approved_by=<真实 OPERATIONS 账号 ID>`；`@program_status` 缺省 ACTIVE（mor-test 适用），ID 段 91027…（cmp-dev 2026-10-27） | cmp-dev | 已确认 |
| P-6 | 块 138 CLIENT 补充件：client-fe 2026-10-27 给出 5 条路由，已生成 `02`、`63`—`65`、`68` 并在本机与 mor-test dump 上与 OPERATIONS 部分一起过完整门禁（审核结果沿用既有 c05，无新路由） | infra-dev / client-fe | 已完成 |
| P-7 | 多语言：第 ⑬b 步用 `assets/harness/onboarding_bundles.py`（复用多语言管理发布包 `i18n7_common`，同 #6—#8 的做法）。2026-10-27 只读计划（第三次，`--web-root /Users/kenny/Desktop/Workspace/Code-MoR-onboard-rel`，web 镜像 onboard-20261027a 的源）：client-console 当前 #1，新命名空间 `onboarding-schema`（排序 90）、新增 553、基准变更 19、**废弃 142**（全部旧入驻页 `onboarding.*`，需 `--accept-deprecations`）、zh-CN 导入 572；operations-console 当前 #8，新命名空间 `onboarding-schema`（排序 240）、新增 612、无基准变更、无废弃、zh-CN 导入 612。第一次 `--apply` 因命名空间编码 `onboardingSchema` 不符合 Platform `[a-z][a-z0-9-]{0,63}` 在建命名空间处 422 失败、未写入；计划步骤现已预检命名空间编码、key 规则与文案 / 说明长度。数字以阶段 7 从候选构建工作区重跑的计划为准。**主会话 2026-10-27 已接受这 142 个废弃**（被替换的旧入驻页）；`--apply` 前必须紧接着重跑计划（源码仍在变），废弃集合若超出 `onboarding.*` 旧页 key 则停止并请示 | infra-dev / 前端 agent | 已决定，脚本就绪 |
| P-8 | 存量数据：**不清空 ACTIVE Client**（mor-test 37 ACTIVE + 17 PENDING；commercial / acquiring 引用 ACTIVE，如 DEMO-CL-001..004）。保留 ACTIVE Client 及其 party / profile / role / affiliation 行与 OE、Local Partner、Partner 角色、卖方披露数据，作为遗留行，不做证件 Token 化迁移。只清在途入驻：PENDING Client 及其申请、文件、草稿、party 行，以及只属于它们的 compliance 案件与资格。cm-dev、cmp-dev 各自在 `changes/` 下提供清理 SQL（含预检计数），执行位置见第 3 节 ⑤、⑦。`2026-10-27-onboarding-global-reset.sql`（清空全部 Client）**不再使用** | 主会话 2026-10-27 已决定；清理 SQL 待 cm-dev / cmp-dev | 部分完成 |
| P-9 | 合成筛查服务挂载：本次从 `Code-MoR-onboard` worktree 挂载脚本（与 relsnap / fileupload 覆盖文件引用 worktree 的做法相同）。compose 文件的挂载源可由 `SCREENING_MOCK_SCRIPT` 覆盖，第 3 节 ⑨ 设为该 worktree 的绝对路径（不设则解析到主工作区，那里没有该文件）。Mock 运行期间该 worktree 不得删除；合入主工作区后去掉该变量 | 主会话 2026-10-27 已决定 | 已决定 |
| P-11 | 遗留行脱敏回填：`changes/2026-10-27-onboarding-global-legacy-backfill.sql`（`23-…`），DDL 已把 `schema_id` 改为可空；两环境试跑 `29` 全 0（见 3.1） | cm-sql | 已提供，已试跑 |
| P-12 | compliance 清理：`changes/2026-10-27-onboarding-global-cleanup.sql`（P-8）。离线试跑见 3.2 | cmp-dev | 已提供，已试跑 |
| P-10 | 并行会话通知：本次清空 Client 入驻数据、停用 form-options、重建 channel-connector；执行前 SendMessage 通知活跃会话 | 主会话 | 执行当天 |

## 1. 约定

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR            # compose.sh 所在主工作区（见 P-9）
REQ=/Users/kenny/Desktop/Workspace/Code-MoR-onboard/requirements/20261027/client-master-全球合规入驻与资格切片1
O="$REQ/release/compose-candidate.yaml"; RB="$REQ/release/compose-rollback.yaml"
up()  { ./test-environment/compose.sh -f "$O"  up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
upr() { ./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$@"; }
set -a; . ./test-environment/.env.test; set +a
sqlr() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }
# 用法：sqlr mor_platform < "$REQ/release/sql/60-platform-block138-registration.sql"
```

一次只换一个服务；不加 `--remove-orphans`；每步后核对 `docker inspect <c> --format '{{.Image}}'` 与 healthy、`docker logs --since` 无 ERROR。

## 2. 备份（第 ① 步）

```bash
B=~/Code-MoR-backups/$(date -u +%Y%m%dT%H%M%SZ)-onboarding-slice1-mor-test; mkdir -p "$B"
for db in mor_client_master mor_compliance mor_platform mor_vault mor_channel_connector; do
  docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-test-mysql-1 \
    mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --databases "$db" > "$B/$db-full.sql"
done
(cd "$B" && shasum -a 256 *.sql > SHA256SUMS)
for c in client-master-service compliance-service channel-connector-service client-console-bff client-console-web \
         operations-console-bff operations-console-web platform-support-service api-gateway; do
  docker inspect mor-test-$c-1 --format "$c {{.Image}}"; done > "$B/images-before.txt"       # = 回滚列，应与 baseline.md 第 1 节一致
for c in client-master-service compliance-service channel-connector-service client-console-bff operations-console-bff; do
  docker exec mor-test-$c-1 printenv | cut -d= -f1 | sort > "$B/keys-before-$c.txt"; done     # 只记键名
```

## 3. 执行顺序

| 步 | 内容 | 命令 / 文件 | 通过条件 |
|---|---|---|---|
| ① | 备份 + 回滚列 | 第 2 节 | 9 个镜像与 baseline 一致；SHA256SUMS 已记 |
| ② | 只读预检 | `sqlr mor_vault < sql/00-vault-precheck.sql`；`sqlr mor_platform < sql/01-platform-block138-precheck.sql`；client-master / compliance 预检（cm-dev / cmp-dev 提供，放 `03-`、`04-`） | Vault 五项 0；块 138：自身区间 0、新 code 0、新路径 0、依赖每行 1、c03 ACTIVE；补充件 `02` 同理（在 `60` 之后执行时也应全 0） |
| ③ | 块 138 发布前门禁（离线） | 用第 ① 步的 `mor_platform-full.sql` 与**候选** Platform JAR（`local-environment/e2e/jars/paytech-platform-support-app-exec.onboard-20261027a.jar`，与镜像 `sha256:cad6dc14…` 内 JAR 相同；与现网只差两个资源文件，路由编译逻辑未变，用现网 JAR 结果相同）跑 `assets/harness/block_compile_check.sh`，步骤顺序同 infra 报告第 11 节（01 → 60 → 02 → 63 → 61 → 64 → 65 → 62 → 68 → 69） | 全部步骤 `COMPILE OK`；两部分都激活后有效路由 = baseline + 18 + 5 − 1；回滚后回到 baseline |
| ④ | Vault | `sqlr mor_vault < sql/10-vault-legal-identifier.sql`；`sqlr mor_vault < sql/11-vault-verify.sql` | 1 类型 + 2 规则 ACTIVE，版本 = 预检值 + 1；等 10 s 刷新 |
| ⑤ | client-master 结构与数据（cm-sql 顺序） | `sqlr mor_client_master < sql/20-client-master-ddl.sql`（DDL 隐式提交，失败时核对已完成语句，**不得整段重跑**；`schema_id` 可空）→ `sql/21-client-master-cleanup.sql`（P-8，只删 PENDING Client；SIGNAL 预检与事后断言，单事务，可重跑）→ `sql/22-client-master-country-pack.sql`（AE / SA / EG 增量，重复键不改，缺省 ACTIVE；生产先 `SET @cm_pack_status='DRAFT'`）→ `sql/23-client-master-legacy-backfill.sql`（P-11 脱敏与申请列回填；依赖 ACTIVE 辖区，故在国家包之后）→ `sql/29-client-master-legacy-redaction-verify.sql`。`…-reset.sql` 已删除 | 21 的断言通过（mor-test 试跑：PENDING 17 → 0、删除申请 20，ACTIVE 37 不变）；`29` 前 6 项全 0（试跑：legacy_redacted_keys 37、apps_schema_null 37） |
| ⑥ | client-master 镜像 | `up client-master-service`（`sha256:0af5b79239ef791cf17400fc6f479ec21089a65d9c03af6dd2c1068f915bec33`，兼容 ClientReviewDecided v1 + v2） | healthy；入驻草稿写证件 → `t_vault_record` 出现 `LEGAL_IDENTIFIER_V1` STABLE 记录；日志无证件原文 |
| ⑦ | compliance 结构与数据（cmp-dev 顺序） | ① `./test-environment/compose.sh stop compliance-service` → ② 补一份 `mor_compliance` 备份（第 2 节命令，单库）→ ③ `sqlr mor_compliance < sql/30-compliance-ddl.sql`（**保留行**，不可重跑；中途失败从备份恢复）→ ④ 同一会话：`{ echo 'SET @approved_by = 221546852372267008;'; cat sql/31-compliance-initial-data.sql; } \| sqlr mor_compliance`（mor-test 运营管理员，ACTIVE / OPERATIONS，已只读核对）→ ⑤ `sqlr mor_compliance < sql/32-compliance-cleanup.sql`（整文件执行；预检 0.4 nested_dependency_count、0.5 mixed_qualification_count、0.6 processing_event_count 任一非 0 即停止） | 清理预检与核验同 3.2 的试跑值（mor-test 删除主案件 17；与 client-master 的 PENDING Client 不一一对应，见 3.2 更正）；核验段全部 orphan、legacy_resolvable_programs、bad_file_address、active_slot_count 为 0 |
| ⑧ | compliance 镜像 | `up compliance-service`（`sha256:f5a9b13a0f0551d407f87e9efba138a45faafd7b37556b6d75f8a2a5f40a1b81`，含新出站配置 P-3） | healthy；`/compliance/v1/programs/resolve` AE / SA / EG 返回程序 |
| ⑧b | XXL 逾期扫描任务 | compliance 新镜像 healthy 后（旧镜像无该 Handler）：Admin `http://127.0.0.1:28090`，`POST /auth/doLogin`（admin，口令取 `.env.test` 的 `MOR_XXL_JOB_ADMIN_PASSWORD`，不回显）→ `POST /jobinfo/insert`：`jobGroup=6`（compliance-service）、`jobDesc=Compliance 退回补件逾期扫描（clientReviewReturnExpiryScan，RETURNED 超期 → EXPIRED + ClientReviewDecided）`、`author=code-mor`、`scheduleType=CRON`、`scheduleConf=0 */10 * * * ?`、`glueType=BEAN`、`executorHandler=clientReviewReturnExpiryScan`、`executorRouteStrategy=FIRST`（单实例）、`executorBlockStrategy=SERIAL_EXECUTION`（cmp-dev：DISCARD_LATER 亦可，扫描幂等、每批 200）、`executorTimeout=300`、`executorFailRetryCount=0`、`misfireStrategy=DO_NOTHING` → `POST /jobinfo/start`（`ids[]=<新 id>`，用 `id` 参数会 400） | `xxl_job_info` 新行 `trigger_status=1`；手动触发一次成功（执行器心跳约 30 秒后才有注册地址） |
| ⑨ | 合成筛查服务 | `SCREENING_MOCK_SCRIPT=/Users/kenny/Desktop/Workspace/Code-MoR-onboard/test-environment/acceptance/screening-mock/screening_mock_server.py ./test-environment/compose.sh -f /Users/kenny/Desktop/Workspace/Code-MoR-onboard/test-environment/compose.screening-mock.yaml --profile acceptance up -d --no-deps --pull never --wait screening-mock`（P-9） | healthy，IP 172.30.73.43；`docker inspect` 挂载源为 onboarding worktree 路径 |
| ⑩ | channel-connector 放行 + 渠道登记 | `up channel-connector-service`（同一镜像 `sha256:23679d45…`，只加 `ALLOWEDSCHEMES=https,http`、`TRUSTEDNETWORKS=172.30.73.43/32`）→ backend 网络内跑 `screening_channel_setup.py --smoke`（命令见 `assets/harness/README.md`，`--mock-url http://172.30.73.43:8080`） | 7 项登记 PASS、冒烟 6/6；未授权调用方 403 |
| ⑪ | 块 138 休眠注册 | `sqlr mor_platform < sql/60-platform-block138-registration.sql` → `sqlr mor_platform < sql/02-platform-block138-client-precheck.sql`（全 0、依赖 1）→ `sqlr mor_platform < sql/63-platform-block138-client-registration.sql` | 插入 227 + 52 行；Gateway 快照刷新仍成功（`docker logs mor-test-api-gateway-1` 无 `CONTROL_PLANE_UNAVAILABLE`） |
| ⑪b | Platform 镜像（S-11） | 块 138 休眠注册之后、激活（⑭）之前：`up platform-support-service`（`sha256:cad6dc14cc7c2c7687cc7aacdaf0a77804e529c6f492adacab56ba1045659ba2`；env 全部来自主 compose 与 env_file，覆盖文件只换镜像）。回滚：`upr platform-support-service`（`sha256:4859efd7…`） | healthy；Gateway 快照版本不变（只换能力清单，不动路由）；运营控制台「菜单资源」页能列出 `OPERATIONS_ONBOARDING_CONFIG`、`OPERATIONS_ONBOARDING_CODES`、`OPERATIONS_CLIENTS` 组件与 `CLIENT_ONBOARDING_SCREENING / RECOMMEND / WAIVE` 动作；数据查询、文件上传、多语言、工作流页冒烟 |
| ⑫ | BFF | `up client-console-bff`（`sha256:83386ff51561cb26311571a7c952e39a762da3a1266c6fd477bd0b30c24323d4`）→ `up operations-console-bff`（`sha256:c5ed58df68333390bc7c99eedf737e73d4e54d9c02bcd6da54d58089cb332f44`，带 4 个 `OPERATIONS_*_BASE_URL`） | healthy；既有页面冒烟（入驻草稿、审核列表、对账 7 页不 503） |
| ⑬ | Web | `up client-console-web`（`sha256:34d3d6e303fd7f62fa7da825599391a2f34ba569e4bbf92a8839d5859a133998`）→ `up operations-console-web`（`sha256:88ab111a7fdabeea6893c307f36a32baec8082b74b218fc2400f12b2cd031872`） | 首页加载（语言包发布前显示内置英文） |
| ⑬b | 语言包 | **紧接执行前**重跑计划（只读）：`python3 -I $REQ/assets/harness/onboarding_bundles.py --web-root <候选 Web 构建工作区> --accept-deprecations`，核对 `release/i18n-bundles/*.plan.json`（新增、基准变更、废弃 142 只限 `onboarding.*`、缺 zh-CN 0）→ 执行：同一命令加 `--apply`（两个应用各建 `onboarding-schema` 命名空间 → key 导入 → zh-CN 增量导入 → 复核已有翻译未变 → 发布 → 匿名 manifest 核对） | client-console #2、operations-console #9 READY 且为当前；语言包数 = 命名空间数 × 2（client 9 × 2 = 18、operations 24 × 2 = 48）；已有 zh-CN 文案与版本不变；两端页面切换中文无 key 原文 |
| ⑭ | 激活块 138 | `sqlr mor_platform < sql/61-platform-block138-activate.sql` → `sqlr mor_platform < sql/64-platform-block138-client-activate.sql` | 61：路由 18、资源 7、授权 7、c03 1、角色 1；64：路由 5 |
| ⑮ | Gateway 快照 | 等 Gateway 刷新：`python3 -I test-environment/scripts/gateway_snapshot_wait.py`（按脚本帮助传入期望快照）或观察 `docker logs` 快照版本递增；重新登录运营控制台使导航重算 | 新路由可达（新菜单出现）；`/api/client/onboarding/v1/form-options` 返回 404 |
| ⑯ | 验收 | 第 4 节 | 全部通过后记录 delivery.md / CURRENT_IMPLEMENTATION |

## 3.1 遗留 ACTIVE Client 处理（P-8 / P-11，主会话 2026-10-27 已决定）

**决定**：遗留 ACTIVE 行**脱敏，不做 Token 化**。cm-dev 在 DDL 之后的回填中处理：
- 个人号码身份键 `identifier_key_value` = `'LEGACY-REDACTED-<identity_key_id>'`；
- 个人号码 `public_identifier_value` = NULL，保留 `identifier_masked_value`（`****` + 末 4 位）；
- 遗留申请 `application_type` = `'ONBOARDING'`，`jurisdiction_code` = 国家码（无则 `'LEGACY'`），`schema_id` = NULL（**DDL 需把 `t_client_application.schema_id` 改为可空**，当前为 NOT NULL）。

**核验（步骤 ⑤ 通过条件）**：`sqlr mor_client_master < sql/29-client-master-legacy-redaction-verify.sql`，前 6 项计数**全部为 0**（零明文个人证件号、零空申请类型 / 辖区、零 schema_id=0）。

不回填时的离线试跑（mor-test 只读 dump，2026-10-27）证明核验能识别问题：

| 核验项 | 只跑 DDL（无回填） | 期望（回填后） |
|---|---|---|
| identity_key_plaintext | 47 | 0 |
| profile_public_identifier_personal | 50 | 0 |
| application_type_blank / jurisdiction_blank / schema_zero | 57 / 57 / 57 | 0 / 0 / 0 |

**定稿文件试跑（2026-10-27，infra-dev 独立复跑，新只读 dump，20 → 21 → 22 → 23 → 29，再重跑 21—23 后再核验）**：

| 项 | mor-test（`mor_client_master`） | 本机（`mor_client_master_v2e2e`） |
|---|---|---|
| Client 前 → 后 | ACTIVE 37 + PENDING 17 → ACTIVE 37 | ACTIVE 2 → ACTIVE 2 |
| `29` 前 6 项 | 全 0（重跑后仍全 0） | 全 0（重跑后仍全 0） |
| legacy_redacted_keys / apps_schema_null / LEGACY 辖区申请 | 37 / 37 / 0 | 0 / 0 / 0 |

cm-sql 自己的试跑日志：`verification/cm-sql-dryrun-{mortest,local}.log`。

验收要回归：遗留 ACTIVE Client 的 Checkout 候选解析、commercial 读 party-profiles、运营 Client 列表 / 详情（遗留申请与 LEGACY 辖区的显示）、新入驻护照号与遗留号码不比对（遗留键已脱敏）。

## 3.2 compliance 离线试跑（2026-10-27 定稿文件，新只读 dump，断网容器，`assets/harness/db_dryrun.sh`）

顺序 30 → 31 → 32；同一容器另载入 client-master 库做 3.7 交叉核对。两个环境全部成功：

| 项 | mor-test（`mor_compliance`） | 本机（`mor_compliance_v2e2e`） |
|---|---|---|
| `@approved_by` | 221546852372267008（mor_platform `t_user_account`：OPERATIONS、ACTIVE） | 236033080844881920（本机 OPERATIONS、ACTIVE） |
| 预检 0.4 / 0.5 / 0.6 | 0 / 0 / 0 | 0 / 0 / 0 |
| 案件 前 → 后 | APPROVED 37、RETURNED 9、SUBMITTED 8、REJECTED 3、WITHDRAWN 3 → APPROVED 37 | 0 → 0 |
| 删除（主案件 / 案件 / 决定 / 资格） | 17 / 23 / 3 / 0 | 0 / 0 / 0 / 0 |
| 资格 前 = 后 | CLIENT 37、OE 1、LP 1（均 APPROVED） | CLIENT 2、OE 1、LP 1 |
| 决定 前 → 后 | 54 → 51 | 40 → 40 |
| 程序 后 | 旧 3 个 RETIRED、新 9 个 ACTIVE | 新 9 个 ACTIVE |
| 核验段 | orphan 全 0；legacy_resolvable_programs、bad_file_address、active_slot_count 0 | 同左全 0 |
| 3.7 交叉核对 | 保留的 37 个 CLIENT 资格全部对应 ACTIVE Client，无孤儿 | 2 个全部 ACTIVE，无孤儿 |

mor-test **更正（cm-sql 报告第 4 节，跨库只读比对）**：数量相同只是巧合，二者不一一对应。compliance 删除的 17 个主案件属于 12 个 Client：13 个属于 8 个 PENDING Client，4 个是 4 个 ACTIVE Client 被取代的早期 RETURNED 轮次（其 APPROVED 案件与资格保留）；另外 9 个 PENDING Client 只有 DRAFT 申请，compliance 中无案件。两边口径各自自洽（client-master 按 Client，compliance 按非 APPROVED 主案件族）；client-master 中 2 条早期轮次的消费记录与 1 条 PENDING Client 的早期 Case 消费记录成为无害孤儿。

## 4. 验证（摘要，完整用例见验收脚本）

- 基础设施：Vault 规则生效（client-master Protect 成功、无 `ACCESS_DENIED`）；筛查冒烟 6/6；`channel.outbound.access` 日志只有编码与状态，无姓名。
- 入驻全链：AE 组织 Client 用入驻码建申请 → Schema 驱动填写 → 证件只显示掩码 → 声明 → 提交 → compliance 立案 → 筛查运行（含 `TESTHIT` 命中）→ 运营工作台处置命中、检查结果、CRR、推荐、四眼决定 → `ClientReviewDecided` v2 → client-master 状态；撤回、退回需求级错误、逾期 EXPIRED 各一例。
- 资格：撤销 → `QualificationChanged` → Client SUSPENDED + 租户投影。
- 运营控制台：入驻配置 / 入驻码 / Client 三页与四个新按钮；每主体并发 2 下无 429。
- 回归：Checkout 候选解析（9 处 SQL 加 current_slot 后）、commercial 读 party-profiles、既有审核页、对账页。

## 5. 回滚（逆序；每项与其依赖同窗口）

| 范围 | 动作 | 不可逆部分 |
|---|---|---|
| 语言包 | 发布不可变，不删除。只回滚 Web 时：旧 client-web 的 142 个 `onboarding.*` key 已废弃，显示内置英文；如需中文，用旧 Web 构建的英文全集重新执行 `onboarding_bundles.py --app client-console --apply`（恢复这些 key）并发布新版本 | 无 |
| 块 138 | 停用：`65`（补充件）→ `62`（恢复 c03）；彻底移除：`68`（补充件）→ `69`（同样恢复 c03） | 无（注册行按 ID 段删除） |
| Platform | `upr platform-support-service`（现网 `sha256:4859efd7…`；块 138 已停用或移除之后再回滚，避免菜单资源页显示未知组件） | 无 |
| Web / BFF | `upr operations-console-web client-console-web`，`upr operations-console-bff client-console-bff` | 无 |
| channel-connector | `upr channel-connector-service`（同镜像、去掉两键）；渠道配置不删：`S_SCREENING_MOCK` 置 CLOSED、授权 DISABLED（运营接口）；`./test-environment/compose.sh -f /Users/kenny/Desktop/Workspace/Code-MoR-onboard/test-environment/compose.screening-mock.yaml rm -sf screening-mock` | 筛查运行记录在 compliance，随 compliance 回滚 |
| compliance | 先停用并删除 ⑧b 的 XXL 任务（`/jobinfo/stop`、`/jobinfo/remove`）→ `upr compliance-service` → 从第 ① 步备份恢复 `mor_compliance`（重写表不可逆；只撤资格列时用变更文件末尾 DROP COLUMN） | 窗口内新案件、筛查、资格变更丢失 |
| client-master | `upr client-master-service` → 从备份恢复 `mor_client_master`（DDL 改列与清理均不可逆）；须与 compliance 同窗口（旧代码不认 v2 事件、旧表结构） | 窗口内新入驻数据丢失；Vault 中新证件 Token 成为孤儿（不影响运行） |
| Vault | `sqlr mor_vault < sql/89-vault-rollback.sql`（已有 `LEGAL_IDENTIFIER_V1` 记录时改为文件末尾的停用段） | 无 |

## 6. 文件

- `release/sql/`（由 [sync_sql.sh](./sync_sql.sh) 从 `db/` 复制并写 `SHA256SUMS`；不要手改）：`00` Vault 预检、`01` 块 138 预检、`10`/`11` Vault 执行与核验、`60`—`62`/`69` 块 138、`89` Vault 回滚；CLIENT 补充件就绪后 `02`、`63`—`65`、`68`。
- client-master / compliance 的变更 SQL 以 `paytech/*/docs/database/sql/changes/2026-10-27-*.sql` 为准，发布冻结时由各自 agent 复制到 `release/sql/20—39` 并加入 `SHA256SUMS`。
- 镜像 / env：`compose-candidate.yaml`、`compose-rollback.yaml`（P-2）。
