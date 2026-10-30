# 明细账运维 · mor-test 发布 runbook（阶段 7 用）

> 阶段 7 须用户明确授权后由主会话执行。任何一步不符合预期就停在该步，按"回滚"列处理。
> 工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR`；`REQ=requirements/20261030/operations-明细账运维`；`REL=$REQ/release`。
> 公共：`TP=$(grep '^MOR_MYSQL_ROOT_PASSWORD=' test-environment/.env.test | cut -d= -f2-)`；
> `sqlr() { docker exec -i -e MYSQL_PWD="$TP" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t "$@"; }`；
> `sqlro() { sqlr --init-command="SET SESSION TRANSACTION READ ONLY" "$@"; }`；
> `gwlog() { docker logs --since "$1" mor-test-api-gateway-1 2>&1 | grep -iE 'snapshot|CONTROL_PLANE|routeCount' | tail -5; }`；
> `up() { ./test-environment/compose.sh -f "$PWD/$REL/compose-$1.$2.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 "$2"; }`（`$1` = candidate / rollback）。
> 只用 sha256 钉住的覆盖文件；不用 `:development` tag、不加 `--remove-orphans`、不叠加历史覆盖文件。

## 1. 硬性顺序与原因

1. **ledger、account 先于 BFF。** BFF 的 B1 调 ledger 新接口 `/posting-progress/v1`；BFF 严格反序列化 account 重建响应，新字段 `changedAccountCount` 要求 account 先上（technique 7.2）。ledger 与 account 互不依赖新接口，可先后任意（建议 ledger → account）。
2. **Platform（能力清单）先于激活块 148。** 现网 Platform 的能力清单没有 `OPERATIONS_FINANCE_POSTING_OPS`，激活后菜单资源页会把它标为不支持。块 148 休眠写入可在 Platform 替换前后任意时刻做（离线演练：现网与候选 Platform 编译休眠状态都通过且快照不变）。
3. **ops-web 先于激活块 148。** 激活后菜单出现，旧 Web 没有该页面。
4. **激活块 148 → 发布语言包 #30。** 旧 Web 不读新增 key，语言包也可提前发布；按 technique §10 放在最后。

## 2. 步骤

| # | 动作 | 预期 / 停止条件 | 中断 | 回滚 |
|---|---|---|---|---|
| 0 | 复核：`bash $REL/scripts/source_drift_check.sh` → `NO DRIFT`；`bash $REL/scripts/env_check.sh` → 10 行 `missing=[] extra=[] valuediff=[]`；`bash $REL/harness/image_verify.sh` → 5 行全 same；与 REQ-06 / 其他会话确认无并行 mor-test 发布、块 148 与语言包 #30 未被占用 | 任一变化即停：重取现网镜像 ID，必要时重建候选与回滚文件 | 无 | — |
| 1 | **备份**：`B=~/Code-MoR-backups/plops-stage7/mortest-$(date -u +%Y%m%dT%H%M%SZ); mkdir -p -m 0700 "$B"; docker exec -e MYSQL_PWD="$TP" mor-test-mysql-1 mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --databases mor_platform > "$B/mor_platform.sql"; shasum -a 256 "$B"/* > "$B/SHA256SUMS"` | 文件非空、末行 `Dump completed` | 无 | — |
| 2 | **预检**：`sqlro mor_platform < $REQ/db/platform-support/mor_platform-block-148-precheck.sql`；`python3 -I $REL/plops_bundles.py`（只读干跑） | 与 [precheck/](./precheck/) 相同：块 148 七表 / 编码 / 路径全 0；ACTIVE 671 / DISABLED 6；最大块 147；语言包现网 #29、计划 added 147（finance 144、nav 3）、不新建命名空间 | 无 | 不一致即停 |
| 3 | **块 148 休眠写入**：`T3=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/db/platform-support/mor_platform-block-148.sql`；`sqlro mor_platform < $REQ/db/platform-support/mor_platform-block-148-verify.sql`；10 s 后 `gwlog $T3` | 行数 1 / 1 / 3 / 4 / 16 / 2 / 24 = 51；授权 REVOKED、资源与路由 DISABLED、权限 ACTIVE；同形核对 route_same / headers_same 除 148404 body 外全 1；快照刷新成功，routeCount 仍 **671**（休眠快照 1 080 333 B，与基线相同） | 无 | 刷新失败立即 `sqlr mor_platform < …-rollback.sql`（路由 DISABLED，可直接删）→ 快照回到原状 |
| 4 | 替换 **ledger-service**：`up candidate ledger-service` | healthy；日志无 ERROR；`ledger.account.p0—p3` 各恢复 1 个消费者、积压回到 0 | 约 30—40 s：ledger 全部接口不可用（运营财务账本 / 关账 / 记账规则页 503）；AccountPosted 在队列中积压，恢复后自动消费 | `up rollback ledger-service` |
| 5 | 替换 **account-service**：`up candidate account-service` | healthy；`account.billing.p0—p3` 消费者恢复、积压 0 | 约 35 s：account 接口不可用（余额 / 明细账 / payout 余额解释）；billing → account 事实在队列积压，恢复后自动消费 | `up rollback account-service` |
| 6 | 替换 **platform-support-service**：`up candidate platform-support-service` | healthy；日志无 notification callers / 能力清单加载错误；Gateway 快照刷新正常（routeCount 671） | 约 60 s：运营 / Client 登录与所有经 Platform 的请求不可用 | `up rollback platform-support-service` |
| 7 | 替换 **operations-console-bff**：`up candidate operations-console-bff` | healthy | 约 30 s：运营门户接口 503 | `up rollback operations-console-bff` |
| 8 | 替换 **operations-console-web**：`up candidate operations-console-web`；`curl -s http://localhost:28080/ \| grep -o 'assets/index-[^"]*\.js'` 与发布树 `dist/index.html` 引用一致 | 返回的入口脚本属于新 dist（291 个文件） | 约 5 s | `up rollback operations-console-web` |
| 9 | **激活块 148**：`T9=$(date -u +%FT%TZ); sqlr mor_platform < $REQ/db/platform-support/mor_platform-block-148-activate.sql`；`sqlro mor_platform < …-verify.sql`；5 s 后 `gwlog $T9` | 授权 / 资源 / 路由 ACTIVE；routeCount **675（+4）**，快照约 **1 086 771 B**（距 4 MiB 余 3 107 533 B）；管理员角色 version + 1 | Gateway 热加载，无中断 | `sqlr mor_platform < …-deactivate.sql`（回到 671） |
| 10 | **语言包 #30**：`python3 -I $REL/plops_bundles.py --apply` | finance 新增 144、nav 新增 3（zh-CN 同步导入），发布号 #30，匿名 manifest 200、各命名空间摘要一致 | 无 | 发布不可变：以 `Code-MoR-plops-base`（= 现网源码）为 `--web-root` 重新发布 #31（新增 key 标为废弃）；一般不需要 |
| 11 | **验收**（第 4 节） | 0 FAIL | — | 按服务回滚；验收数据按第 4 节清理 |

**整体回滚（倒序）**：`…-deactivate.sql`（671）→ ops-web → ops-bff → Platform → account → ledger（各 `up rollback …`）→ 需要时 `…-rollback.sql`（路由仍 ACTIVE 时自动拒绝，必须先停用）→ 语言包按第 10 步回滚列（可选）。

## 3. 恢复方案

| 对象 | 触发条件 | 应用回退 | 数据恢复 | 不可逆 / 需说明的部分 |
|---|---|---|---|---|
| ledger | 启动失败、消费报错、关账第 3 步结果与替换前不同 | `compose-rollback.ledger-service.yaml`（现网 `51a57a3b`） | 无表变更 | 读 account / billing 的响应上限 8192 B → 4 MiB / 8 MiB **影响 ledger 全部经 HTTP 的读取**（追平、关账第 3 步、MQ 消费后按序号补读），不只本页面；回退镜像即回到 8192 B（D-3 的追平不可用问题随之回来）。期间已消费的 AccountPosted 写入的分录不随回退撤销（属正常记账，旧版本同样会写） |
| account | 启动失败、重建结果异常 | `compose-rollback.account-service.yaml`（`8dfd1060`） | 无表变更 | 验收中的重建会重写同一流的余额行（一致数据上值不变，见第 4 节） |
| Platform | 启动失败、登录 / 快照刷新异常 | `compose-rollback.platform-support-service.yaml`（`0ea9cc2d`） | — | 回退后能力清单没有本页：须先停用块 148，否则菜单资源页标"不支持" |
| ops-bff / ops-web | 页面或接口异常 | 各自 rollback 文件（`626a74a7` / `acb33e94`） | — | 回退 Web 前先停用块 148（菜单入口） |
| 块 148 | 快照刷新失败、权限错误 | — | 激活中：`…-deactivate.sql`；休眠且无他用：`…-rollback.sql`（按 ID 删除，含其他角色对 148101 的授权）；兜底：第 1 步备份 | 激活 / 停用各令 PLATFORM_ADMIN 角色 version + 1，回滚不回退该计数（离线演练确认这是唯一残留差异）；运营若已把 `FINANCE_SUBLEDGER_MANAGE` 授给其他角色，回滚会一并删除这些授权 |
| 语言包 #30 | 文案错误 | — | 以现网源码重新发布 #31 | 发布记录不可删除（#30 保留在历史中） |

## 4. 验收计划（遵守 S-03：mor-test 不制造不一致）

脚本：以阶段 5 `verification/stage5/t6lib.py`、`t6_readonly.py`、`t6_rebuild.py`、`t6_browser.mjs` 为底改写到 mor-test（`E2E_TARGET=test`，运营入口 `http://localhost:28080`，库只读用 root READ ONLY 会话、库名 `mor_*`；浏览器经 Playwright 把 `/api/operations/**` 中继到 28080，并带该 Origin / Host——见 memory「ops-web shared-tree release」），阶段 7 授权后由主会话生成并先做只读干跑。凭据只放 `~/Code-MoR-backups/plops-stage7/`（0600）。

| # | 场景 | 入口 | 预期 | 写入 |
|---|---|---|---|---|
| V-0 | 健康 | `docker inspect` 5 个容器 healthy；Gateway routeCount 675；`curl` Web 入口为新 dist | — | 无 |
| V-1 | 进度（AE001 · USD，OE 账簿 `238137396670066688`） | B1 `GET /api/operations/finance/posting-progress/v1/ledger-books/238137396670066688`（管理员） | ① 计费事实段、② 过账流段、③ ledger 段均 CONSISTENT；流 `OE.AE001.USD.0` account 最大序号 = ledger 已消费（阶段 6 读数 12/12，以执行时 SQL 读数为准）；`stuckPosting` 为 null | 无 |
| V-2 | 进度（FE 账簿 `238324787049492480`） | B1 | 流清单与关账第 3 步同口径（服务费所在 OE 流），全部 CONSISTENT | 无 |
| V-3 | 批次浏览 | B2 `…/posting-streams/v1/OE.AE001.USD.0/postings?fromSequence=1&limit=20`；详情抽屉（已有路由 99403） | 返回 12 批（或执行时的实际数）且与 `mor_account.t_account_posting` 一致；`nextFromSequence` null | 无 |
| V-4 | 追平（已一致的流） | B3 `POST …/posting-streams/v1/OE.AE001.USD.0/catch-up`（管理员） | 200，`processedCount "0"`、`caughtUp true`；ledger 已消费序号不变。经接口执行：流已一致时页面按设计不显示"追平"按钮，页面只核对按钮不出现（阶段 7 实测更正） | ledger 只读本次请求、不写分录；运营审计日志一条 |
| V-5 | 重建（AE001 · USD） | 页面"重建余额"→ 确认（P4）→ 结果条 | `changedAccountCount "0"`、页面"无需纠正"；重建前后 `mor_account.t_owner_account` 该流各账户余额 / 桶金额逐行相同（执行前后各 SELECT 一次比较） | 重建在流锁内重写同值余额行（行版本 / 更新时间可能变化，值不变）；account INFO 日志一条。执行瞬间 AE001 入账短暂等待锁 |
| V-6 | 权限 | 合成只读运营账号（自定义角色仅 `FINANCE_ACCOUNTING_READ`） | 菜单可见、可查看进度与批次；无"追平""重建余额"按钮（P10）；直接调 B3 / B4 → **403** | IAM：1 个角色 + 1 个账号（`plops-mt-readonly-<时间>@example.test`），首登改密 |
| V-7 | 浏览器 | Playwright 真实 Chromium：zh-CN / en × 1440 / 390 | 进度总览、批次浏览、抽屉、重建确认与结果、只读视图；390 无横向滚动（P11）；中英文为语言包 #30 文案（不是内置回退）；截图入 `release/acceptance/screens/` | 同 V-4 / V-5（只在 zh-CN-1440 与 en-390 两个组合执行写操作） |
| V-8 | 回归 | 明细账查询、余额查询、期间关账页（只读打开）；payout 两页 | 正常 | 无 |

**清理**：停用 V-6 的合成运营账号并撤销其角色（经 IAM 接口，不删审计）；不删除操作审计与日志。V-4 / V-5 无业务数据需要清理。

### 可选项 O-1：在 mor-test 复测"追平 200 批"耗时（**需用户单独授权**，S-03 默认不允许）

目的：用户希望在 mor-test 复测阶段 5 的 200 批耗时（本机 8 个样本中位数约 5.2 s，最大 7.52 s，上限 9 s 读超时 / 10 s Gateway）。复测必须先制造落后，因此需要以下写入，均限定在一个新的合成 OE 上：

| 步 | 动作 | 写入 / 影响 |
|---|---|---|
| a | 选一个现网未用的合成 OE 编号（如 `ZQ9nn`，以 SQL 核对 mor_ledger / mor_account / mor_billing 均无），经运营入口建日历、当月 OPEN 期间、OE USD 账簿（同阶段 5 `t6_setup.py`） | `mor_ledger`：日历 1、期间 1、账簿 1（无删除接口，保留为带 `PLOPS-MT` 标记的合成数据） |
| b | `docker stop mor-test-ledger-service-1` | ledger 停约 2—4 分钟：期间所有 AccountPosted（含 AE001）在 `ledger.account.p0—p3` 积压 |
| c | 经 mor-test 后端网络内一次性 sidecar 调 billing 私网命令登记并批准 600 条 `MANUAL_ADJUSTMENT` 事实（每条 1.00 USD → 合成 Client，同阶段 5 `t6_facts.py`）；等 account 全部入账并发出 | `mor_billing` 600 条事实、`mor_account` 600 批过账 + 1 个合成 Owner 账户（会计记录，**不可通过接口删除**） |
| d | **守卫**：`rabbitmqctl list_queues -p /mor-test` 中 `ledger.account.p0—p3` 消息合计必须 = 600，且 `mor_account` 中合成 OE 以外的流在 b 之后没有新批次；不满足则不清空、直接 `up candidate ledger-service` 让 ledger 正常消费，本次测量作废 | 只读 |
| e | 只清空 `ledger.account.p0—p3`（不碰 DLQ 与其他服务队列），`up candidate ledger-service` | 600 批只存在于 account，ledger 落后 600 |
| f | 运营台对该 OE 账簿点"追平"三次，记录每次 200 批的浏览器耗时与 ledger 日志耗时；第三次后显示"已追平" | `mor_ledger` 600 张凭证（合成 OE 账簿内） |
| g | 核对：B1 三段 CONSISTENT；重建 changedAccountCount 0 | — |

清理选项（二选一，需用户决定）：**保留**（推荐：账簿、事实、过账、凭证都是带标记的合成数据，与 AE001 隔离），或另写按合成 OE / 合成 Client 删除的 SQL（跨 `mor_billing` / `mor_account` / `mor_ledger` 多表，需单独评审、先备份三库）。若 d 的守卫因并发入账而失败，AE001 不受影响（ledger 照常消费积压）。默认**不执行** O-1，以阶段 5 本机结果为准。
