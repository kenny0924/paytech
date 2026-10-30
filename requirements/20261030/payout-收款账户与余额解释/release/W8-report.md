# payout S1 · 阶段 6 W8 报告（候选源码、测试、镜像、compose）

> 2026-10-30 13:10—14:05Z（恢复运行；上一个 W8 16:59 +04 被主机重启中断，未留报告）。mor-test 只读：只用了 `docker inspect / exec（printenv、sha256sum、cat）/ cp 取出 / network inspect`、mor-test MySQL 只读 SELECT。未停 / 起容器、未写 SQL / XXL / Nacos / MQ、未登录 XXL Admin。本机 SQL 只经测试用 `mor-local-mysql-1`；本机 e2e 共享栈未动。未提交、未推送；未改主工作区（本需求 `release/` 除外）与其他会话 worktree；未改 `test-environment/compose*.yaml`。未调用 Superpowers skill。

## 1. 结论

| 项 | 结论 |
|---|---|
| 基线 | main 仍是 `9eab368c`。7 个现网服务镜像 ID 与任务卡一致（重启后 StartedAt 变化、RestartCount 偏高，镜像未变）。account / Platform / ops-bff / Gateway JAR 与 `9eab368c` 构建 **0 差异**；ops-web dist 277 个文件**逐字节相同**；client-bff（legal-20261028a）**现网独有 0**，只是落后 main 两个提供方 api JAR。→ [baseline.md](./baseline.md) |
| 候选源码 | `/Users/kenny/Desktop/Workspace/Code-MoR-po-rel` = `9eab368c` + **247** 个文件：po-int 241（220 原样、21 三方合并，其中 12 个冲突手工合并）+ test-environment 6。漂移检查 **NO DRIFT**（`scripts/source_drift_check.sh`）。→ [evidence/merge-notes.md](./evidence/merge-notes.md)、[evidence/source-files.sha256](./evidence/source-files.sha256) |
| 合并 / 测试中发现并修正（2 处，都是测试代码，不进 JAR） | ① 上一个 W8 的冲突合并漏掉 `OperationsBffTestConfiguration.payoutOwnerSimulator()` 上的 `@Bean`（ops-bff 12 个用例失败）；② 阶段 5 的 `MockPlatformNotificationController` 用了类级 `@RequestMapping("/platform-support")`，取代了接口的 `/notifications/v1` 前缀（payout 7 个通知用例超时）——**po-int 里也有这个问题**，要以候选树为准提交 |
| 测试 | reactor `test-compile` 126 模块成功；payout **33/0**、account **72/0**、Platform 受影响 6 个类 **41/0**（含加载能力清单的 OperationsRoleManagementTest）、client-bff **108/0**、ops-bff **2543/0**、arch-rules **70/0**；ops-web `tsc` 0 错误、`npm test` **1686/1686**、`build` 通过、`i18n:export-source` 11 129 key（payout 216）。测试后重建的 JAR 与镜像内 JAR 逐条目相同。→ [evidence/test-summary.txt](./evidence/test-summary.txt) |
| 镜像（`payout-s1-20261030a`，没动 `:development` 等其他 tag） | payout `sha256:75a6337cd55f…`、account `8dfd1060d136…`、Platform `0ea9cc2d6e89…`、client-bff `24d3aa05f3f0…`、ops-bff `626a74a740ce…`、ops-web `acb33e948c65…`（完整 ID 见 [evidence/candidate-images.txt](./evidence/candidate-images.txt)）。镜像内 JAR / dist = 构建产物；health-probe 与现网相同（payout 用候选探针，允许列表含 19117）；Entrypoint / User / Healthcheck / 端口 / WorkDir / Env 与现网同类镜像相同（payout 对照 tax）；nginx.conf 相同。→ [evidence/image-verify.txt](./evidence/image-verify.txt) |
| 候选 JAR | 在 `~/Code-MoR-backups/payout-s1-stage6/candidate/`（Platform `f4782de4…`），已告知 W9 |
| compose | 12 个文件。回滚 5 个与现网 env **逐键逐值相同**；候选只差 Platform 的 `SPRING_APPLICATION_JSON`（加了 `notification.http.callers.payout-service` 四个模板，webhook 部分与现网相同）；ops-bff 两份都带 `OPERATIONS_TAX_BASE_URL`。payout 是完整的服务定义（28 键）。→ [evidence/env-check.txt](./evidence/env-check.txt)、`scripts/env_check.sh` |

## 2. 每个服务相对现网的差异与是否随发

| 服务 | 条目差异（候选 vs 现网） | 必须随发？ |
|---|---|---|
| payout-service | 新服务（JAR 1 156 项） | 是，主体 |
| account-service | +1、~8（positions 接口：Controller / Api / Service / Repository / Mapper + XML），0 删除 | 是：payout 余额解释调 `/balances/v1/{clients,partners}/{id}/positions`，不发则余额页 4xx / 5xx |
| platform-support-service | +11、~10（tenantRecipients 通知、收件人解析、Client 角色加 3 个权限、能力清单加 OPERATIONS_FINANCE_PAYOUT_* 6 项、application.yml 注释），0 删除 | 是：没有 tenantRecipients，四种通知全部 4xx；能力清单不发则运营菜单资源页把 payout 菜单标为不支持（同 tax 的 F-9）；Client 角色授权由代码 `ClientTeamPermissions` 决定 |
| client-console-bff | 对 main：+43、~9（`/client-payout/v1` 七个接口、payout-api、通知 api），0 删除；对现网另外带上 main 已有的两个提供方 api JAR 改动（39 新增 + 23 改动，提供方已在现网） | **建议发**：Client Web 本期未做，但不发的话块 147 的 Client 路由会转发到不存在的接口，Client 侧 AC-01/02/04 也无法用 API 验收。不发则块 147 的 Client 路由要保持休眠 |
| operations-console-bff | +58、~11（`/payout-admin/v1` 十二个接口、payout-api、account-api、通知 api），0 删除 | 是 |
| operations-console-web | 277 → 284；去掉文件名 hash 后逻辑上只新增 7 个（2 个页面、payout.js/css、3 个图标块）、**0 删除**；其余 232 个文件只是 hash 换名（共享 chunk 变了） | 是 |
| api-gateway | 不发（无代码改动；块 147 激活后快照由 Platform 编译，W9 负责离线复跑） | 否 |
| vault-service | 不发（只有 W9 的 `mor_vault` DML） | 否 |

## 3. payout-service 落位

- IP **172.30.73.44**（技术方案写的 .43 已被 `screening-mock` 占用）；Snowflake **Region 0 / Worker 16**（17—31 已占）；18117 / 19117；内存 448m + small JVM（同 tax / account）。
- DB：`mor_payout` 与 `payout_app` 在 mor-test 都不存在。密钥机制照 tax：候选树 `prepare.py` 把 `MOR_PAYOUT_DB_PASSWORD` 加进 `V2_DATABASE_PASSWORDS`（已有 `.env.test` 时只追加缺失键），`initialize.py` 的 `SERVICE_DATABASES` 加 payout（`applies_sql=False`：`docs/.../payout-schema.sql` 是与代码不一致的 9 表草稿，W9 指出）。阶段 7：`python3 test-environment/scripts/prepare.py` → 建库与账号：`initialize.py --create-service-databases`（IF NOT EXISTS，已有密码不变），或 W9 的 `sql/10-mor_payout-database-account.sql`（同一个键 `MOR_PAYOUT_DB_PASSWORD`），二选一 → W9 的 `sql/12-mor_payout-ddl.sql`。**这两个脚本必须用候选树版本**（或先合入 main）。不要用 `--apply-service-sql`（它会检查所有服务库）。
- Redis：`redis:6379`，`PAYTECH_REDIS_PASSWORD`。RabbitMQ：不用。
- Nacos：test Profile 不读配置中心，**不需要** `payout-service.yaml`；只注册服务（`mor-test` / `DEFAULT_GROUP` / `mor-test-services`）。两个 BFF 按服务名 `payout-service` 经发现调用（代码里固定，所以**没有新增地址键**——这一点和任务卡的预期不同）；payout 经发现找 Vault 和 Platform（acquiring 在 mor-test 已用同样的 `lb://platform-support-service/platform-support`），account 用 `PAYOUT_ACCOUNT_SERVICE_URL=http://account-service:18114`。
- XXL：新建分组 `payout-service`（预计 id 19）+ `compensationEventScan`（预计 id 29，参数照抄现有 15 个），用 `scripts/xxl_payout.py plan|apply|trigger|remove`（由 xxl_tax.py 只改常量而来）。payout 没有其他 XXL Handler。
- 内部认证：Platform 是 DUAL，payout / account / BFF 都是 OFF → **不需要登记 Token 或受众**。Platform 通知按 `X-Caller-Service=payout-service` + `notification.http.callers` 放行，后者在 mor-test 只能写在 `SPRING_APPLICATION_JSON` 里（服务名带连字符，环境变量绑定会丢掉连字符）。account 的 positions 接口没有调用方白名单。Vault 按调用服务名授权（W9 DML）。
- health-probe：19117 已加进 `LocalHttpHealthProbe.java` 与 `test_java_image_health_probe.py`，并编译进 payout 镜像。

## 4. 风险

| # | 风险 | 缓解 |
|---|---|---|
| R-1 | client-bff 候选同时带上 main 已有、但现网 client-bff 还没有的提供方 api 类（REQ-12 client-master 表单、Platform 联合查询 / 工作流数据查询） | 提供方已在现网（client-master rq12、Platform = main）；client-bff 测试 108/0；回滚镜像 8fe7f24c 可用 |
| R-2 | Platform 重启约 60 秒：运营 / Client 登录与所有经 Platform 的请求 503 | 放在低峰；Gateway 快照有缓存 |
| R-3 | 首次真实运行在 mor-test：448m 内存、Vault 规则、Platform 通知路由（阶段 5 本机未证实受理，因为没有 SES 路由） | 起服务后看 `docker stats` 和日志；通知失败只影响补偿事件，不影响账户状态 |
| R-4 | 阶段 7 前别的会话换掉 Platform / ops-bff / ops-web / account，或 main 前移 | runbook 第一步跑 `scripts/source_drift_check.sh` 并重新取现网镜像 ID；有变化就停下重建 |
| R-5 | base compose 没有 payout-service：`build.sh` 的 `compose.sh build … payout-service` 和两个 Python 测试在补丁合入前会失败；之后若有人不带覆盖文件执行 `compose.sh up`，payout 会被当成 orphan | 合入 `compose-base-payout.patch`（D-1）；不要加 `--remove-orphans` |

## 5. 待裁决

| # | 事项 | 建议 |
|---|---|---|
| D-1 | base compose 补丁 [compose-base-payout.patch](./compose-base-payout.patch)（`compose.applications.yaml` 加 payout-service；`compose.yaml` 给 mysql 加 `MOR_PAYOUT_DB_PASSWORD`，只在新建 MySQL 卷时生效，**现有 mysql 容器不用重建**）由用户合入 | 随提交一起合入；阶段 7 不依赖它（只用覆盖文件） |
| D-2 | client-bff 是否随发（第 2 节） | 发 |
| D-3 | IP 改为 .44，Worker 16 | 接受；technique / LOCAL_DEVELOPMENT_ENVIRONMENT 里的 .43 要改（主会话） |
| D-5 | `paytech/payout/docs/database/sql/payout-schema.sql` 与代码、需求 DDL 不一致（9 表、旧补偿表形状） | 提交前用需求 `db/payout/ddl.sql` 更新它，然后才能把 `applies_sql` 改回 True |
| D-4 | 两处测试修正（第 1 节）同步回 po-int 或直接从 po-rel 提交 | 从 po-rel 提交 |

## 6. 阶段 7 容器动作清单（主会话执行，须用户授权）

顺序原则：提供方先于消费方；payout 起来之后再上两个 BFF；块 147 最后激活。

| 步 | 动作 | 中断 |
|---|---|---|
| 0 | `scripts/source_drift_check.sh`；重新取 7 个容器的镜像 ID 与现网一致；`scripts/env_check.sh` | 无 |
| 1 | `prepare.py`（追加密钥）→ `initialize.py --create-service-databases` → W9：payout DDL、Vault DML、块 147 **休眠**写入 | 无 |
| 2 | 替换 **account-service**（`compose-candidate.account-service.yaml`） | 约 30—40 秒 account 不可用（ops-bff 余额页、billing → account 消费暂停，MQ 积压后自动追上） |
| 3 | 替换 **platform-support-service** | 约 60 秒，Platform 全部不可用 |
| 4 | 启动 **payout-service**（新建容器，`up -d --no-deps … payout-service`）；约 30 秒后 `xxl_payout.py apply` → `trigger` | 无（新服务） |
| 5 | 替换 **operations-console-bff**、**client-console-bff**（可并行） | 各约 30 秒，对应门户接口 503 |
| 6 | 替换 **operations-console-web** | 约 5 秒 |
| 7 | W9：激活块 147 → Gateway 快照（不换 Gateway 镜像）→ 语言包 | Gateway 热加载，无中断 |
| 回滚 | 倒序：块 147 停用 → ops-web / BFF 回滚文件 → `xxl_payout.py remove` → 停止并删除 payout-service（保留库）→ Platform → account | 同上 |

## 7. 本机资源

- 候选树 `Code-MoR-po-rel`（含 `target/`、`operations-console-web/node_modules`（真实目录，`npm ci --offline`）、`dist/`）保留到阶段 7 结束。
- 对照树 `/Users/kenny/Desktop/Workspace/Code-MoR-po-base`（`9eab368c`，W8 新建；`operations-console-web/node_modules` 是指向 po-rel 的**符号链接**，删 worktree 前先删链接），阶段 7 后可以删。
- `~/Code-MoR-backups/payout-s1-stage6/`（0700）：`live/`（现网 JAR / dist）、`base/`（main 构建）、`candidate/`（候选 JAR + SHA256SUMS）、`probe/`、`ctx/`（docker build 上下文）、`logs/`。
- 本机新镜像 tag：6 个 `mor-test/<svc>:payout-s1-20261030a`。
