# 任务卡 02 报告 · 阶段 2：在 mor-test 执行迁移与 V2 部署

- 分支：`test-env/v2-migration-stage2`（worktree `Code-MoR-mt2`，起点 `main@e5467400`）。运行命令以主仓库为 `$REPO`；脚本改动与本报告在 worktree 提交。
- **结论：mor-test 已切换到 V2 并保留全部数据。**
  - 迁移核对 150 项全部一致，三个拆分旧库 4,210 行守恒。
  - 29 个容器全部 healthy：18 个 Java 服务、Vault、2 个 Web、Nginx 与 7 个基础设施。
  - 登记核对全部符合，或已说明原因。
  - 验收 6 条链路全部通过；runbook 第 7 节的两个运营烟测脚本被一个早于本次迁移的目录门禁挡住，用等价的只读核对补齐（第 6 节）。
- 需要先看：第 7 节偏差与第 9 节待确认。

## 0. 已读输入与执行要点

- 已读：本需求 `requirement.md`、任务卡 02、`runbook.md`、`design/01-migration-map.md`、`tasks/01-report.md`；`migrate.sh`、`compose*.yaml`、`compose.sh`、`build.sh`、`initialize.py`、`vault.py`、`smoke.py`、`risk_rule_configuration_smoke.py`；第 14 步 `verification/local-e2e/` 与 20260914、20261017 两个 test 版验收脚本；acquiring `PaymentSucceededListener` / `PaymentSucceededMessageConverter`、`ApplyPaymentSucceededProcessor`、paytech-messaging `DomainEventCodec` / `PayloadHasher` / `RabbitDomainEventPublisher`。
- 本步不改业务代码，不涉及同步业务接口开发；AGENTS 的接口开发必读不适用。
- 对 mor-test 的写操作都先做 runbook 对应的只读核对。root 执行迁移 SQL 被权限分类器拦截后，没有换写法，由主会话执行同一条命令（第 1 节第 5 步）。
- 备份目录按任务卡放在 `test-environment/private/backup-20261018/`（runbook 写的是 `~/Code-MoR-backups/…`，以任务卡为准）。

## 1. 逐步记录（2026-10-18，时间为 +04:00）

| 步 | 时间 | 动作与关键输出 | 结果 |
|---|---|---|---|
| 0.1 | 17:30 | `main@e5467400`（含阶段 1 与任务卡）；工作区只有与本次无关的 2 个改动 | 通过 |
| 0.2 | 17:30 | `prepare.py`：`Existing test material retained`，未追加 | 通过 |
| 0.3 | 17:30 | Docker 20.49 GiB；gitlab 已停；nexus 1.39 GiB、mor-local 约 1.8 GiB；磁盘可用 43 GiB | 通过 |
| 0.4 | 17:32 | `containers.txt`、`container-image-ids.txt`（21 个容器）、`repo-head.txt` | 已记录 |
| 0.5 | 17:32 | `migrate.sh plan`：表数 56/6/38/17/85；指纹 `ae3f720a…8216` 与演练一致；旧服务账号无连接；14 个口令齐全；`mor_payment` EMPTY、其余 13 个 ABSENT；**无 FOREIGN**，不需要 `--park-legacy` | 通过（`migrate-plan.log`） |
| 1 | 17:32 | 旧服务容器在开始前已全部 Exited，只有 `mor-test-mysql-1` 运行；不删除任何旧容器 | 无需操作 |
| 2.1 | 17:32 | 9 个库逻辑转储（含空库 `mor_payment`、`mor_finance`） | 完成 |
| 2.2 | 17:33 | 停 mysql / redis / rabbitmq / minio，4 个卷 tgz（mysql 412 项、rabbitmq 91、redis 6、minio 142，均可读）；`env.test`、`private/` 副本（排除备份目录自身，避免递归）；`SHA256SUMS` 18 项；目录 0700 | 完成（32 MB） |
| 3.1 | 17:34 | 重建 mysql、rabbitmq，起 redis / nacos / minio / mailpit / xxl-job-admin，全部 healthy；`max_connections=400`；`rabbitmq_consistent_hash_exchange` 已启用 | 通过 |
| 3.2 | 17:35 | `migrate.sh apply`（主会话执行）：10 → 14 个库 Schema → 20 → 21 → 30/31/32 → 33 → 50 → 核对通过 | 通过（`migrate-apply.log`、`verify-detail.txt`） |
| 3.3 | — | 再次 `apply`：全部跳过（32 处），核对仍通过 | 通过（`migrate-apply-rerun.log`） |
| 4 | 17:36—17:38 | `build.sh` BUILD SUCCESS（Maven 26.8 秒，全程 1:03）；`client-console-web` 构建；`build-vault.sh` 16.5 秒 | 20 个 `:development` 镜像 + Vault（`v2-images.txt`） |
| 5 | 17:38—17:40 | 按 runbook 顺序 `up -d --no-deps --wait`，每组一次通过，共 91 秒 | 29 个容器 healthy |
| 6 | 17:41—17:46 | Nacos / Gateway / Vault / XXL / Webhook / RabbitMQ / 内部 Token 核对（第 4 节） | 符合（2 项偏差见第 7 节） |
| 7 | 17:47—18:05 | 验收脚本 V2 化与验收（第 5、6 节） | 通过 |
| 8 | 18:06 | 全部 mor-test 容器近 60 分钟日志检索 `BEGIN PRIVATE KEY`、`whsec_`、`password=` | 0 条 |

## 2. 迁移核对

| 核对 | 结果 |
|---|---|
| 40 逐表行数与内容校验和 | 150 项，MISMATCH 0（与演练相同口径） |
| 行数守恒 | mor_cct + mor_commerce + mor_risk = 4,210 = V2 六库 4,110 + 归档幂等 100 + 归档争议 0 |
| V2 库表集合 | 14 个库与评审 Schema 一致 |
| Platform | 57 表；USD；`commerce-client-api` 发现名 `acquiring-service` |
| Vault | 7 条 V2 调用方规则（client-master 4、card 2、channel-connector 1） |
| risk-decision 种子 | 61 个种子主键齐全 |
| 重复执行 | 第二次 `apply` 全部跳过，`verify` 仍通过 |

数字与阶段 1 演练完全相同，说明转储之后 mor-test 数据没有变化。

## 3. 服务与内存

全部 healthy：vault、platform-support、client-master、commercial、compliance、risk-decision、consumer、channel-connector、card、payment、billing、ledger、account、reconciliation、tax、acquiring、operations-console-bff、client-console-bff、api-gateway、operations-console-web、client-console-web、nginx，以及 mysql、redis、rabbitmq、nacos、minio、mailpit、xxl-job-admin。

V2 服务日志 ERROR 0 行。Nacos 有 3 行含 "ERROR" 的 logback 启动信息，不是错误。

| 时点 | mor-test 合计 | mor-local | nexus |
|---|---:|---:|---:|
| 启动后 20 秒 | 6,252 MiB | 1,794 MiB | 1,424 MiB |
| 全部验收后 | 7,388 MiB | 1,842 MiB | 1,465 MiB |

验收后接近 `mem_limit` 的服务：

| 服务 | 占用 / 上限 |
|---|---|
| platform-support | 704 / 768 MiB（92%） |
| xxl-job-admin | 448 / 512 MiB（87%） |
| nacos | 823 / 1,024 MiB（80%） |

其余 Java 服务为 130–335 MiB，低于上限的 60%。

## 4. 登记与配置核对（runbook 第 6 节）

| 项 | 实测 | 判定 |
|---|---|---|
| Nacos | `mor-test` 命名空间 14 个服务各 1 个健康实例：13 个 V2 Java 服务 + `vault-service`；旧名 `risk-service`、`client-contract-service`、`commerce-service` 无实例 | 符合（runbook 写 18 个，见第 7 节） |
| Gateway Snapshot | 3 个 Target；`commerce-client-api` → `acquiring-service`；277 条路由，其中 CLIENT_API 9 条 | 符合 |
| Vault 规则（ACTIVE） | client-master 4、card 2、channel-connector 1；旧调用方 client-contract 4 条保留 | 符合 |
| XXL-Job | `acquiring-service`、`platform-support-service` 执行器在线；经 Admin API 启动 acquiring `compensationEventScan`（任务 4），之后每 30 秒触发 200；commerce-service 任务保持停止 | 符合 |
| Webhook 调用方 | `webhook.http.callers` 只有 `acquiring-service`（8 种事件） | 符合 |
| RabbitMQ 旧队列 | 活动节点上不存在 `cg_*` 与 `topic_*`；旧节点数据中的 4 个旧队列消息数均为 0（第 7 节） | 无需删除 |
| 内部 Token | compose 未设置 `internal-auth`，即默认 OFF | 符合 |

## 5. 验收脚本 V2 化（阶段 1 待确认第 8 项）

采用任务卡的优先方案：第 14 步 V2 版本加 `E2E_TARGET=test` 分支，V1 版本停用 test 分支。

| 文件 | 改动 |
|---|---|
| `20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py` | 新增 `E2E_TARGET=test`：入口 28080 / 28443 / 28444 与 test CA；口令读主仓库 `test-environment`（`E2E_TEST_ENV` 可改）；库为 V2 正式库名 `mor_<服务>`（23306）；私网接口经接入 `mor-test-backend` 的一次性探针容器；RabbitMQ 管理口 25673、vhost `/mor-test`。本机行为不变 |
| 同目录 `PrivateHttpProbe.java` | 由 20260914 目录复制，私网探针 |
| 同目录 `developer_flow.py` | 恢复 V1 的 `webhook_flow_test`：test 只允许公网 HTTPS 端点，不推送 |
| 同目录 `client_api_flow.py` | test 分支取代 20261017 test 版。使用 test 验收接收端；`order.paid` 沿用原夹具方式：插入 Intent 投影，再把 PaymentSucceeded 新信封直接发到 `acquiring.payment.hash`，不触发 card 等订阅方。保留 `--outage` |
| 20260914 V1 `e2e_http.py`、20261017 `client_api_flow.py` / `register_client_api.py` | 对 test 直接退出并指向 V2 脚本；`register_client_api.py` 会把 Target 写回 `commerce-service`，已禁止执行 |
| `test-environment/scripts/vault.py` | Platform 镜像无 curl：改在一次性 `nginx:1.30.4-alpine` 容器（`mor-test-backend`）内执行同一 curl 请求 |
| `initialize.py`、`vault.py` | `ROOT` 支持 `MOR_TEST_ENVIRONMENT_DIR`，副本可指向持有 `.env.test` 的目录；默认不变 |
| `risk_rule_configuration_smoke.py` 与其测试 | Nacos 服务名 `risk-service` → `risk-decision-service`（IP、端口不变） |
| 本需求 `verification/test_sample_reads.py`（新增） | 迁移数据抽样：运营入口读取结果与 V2 库实时计数比较，只读 |
| `test-environment/README.md` | 更新 Vault 烟测说明与 V2 切换记录 |

`test-environment/scripts` 全部 17 个单测文件在 worktree 与 main 的结果完全相同，14 个通过。另外 3 个在 main 上同样失败，属既有问题：`smoke_stage_contract` 10 errors、`risk_rule_configuration_catalog` 3 failures / 27 errors、`authorization_inventory` 1 failure，对应 CURRENT_IMPLEMENTATION 已列的"rollout 工具钉值需重定"。

`requirements-site` 测试：worktree 没有 `node_modules`，`npm test` 的构建步骤无法运行；直接运行 `node --test tests/*.test.mjs`。

| 位置 | 通过 / 总数 |
|---|---|
| main（有既有 `dist/`） | 127/137 |
| worktree | 105/137 |

worktree 多出的 22 项全部是读取构建产物的 "rendered" 测试；两边共有的 10 项内容失败完全相同，属既有问题（阶段 1 报告同样记录 10 项）。

## 6. 验收结果（runbook 第 7 节）

| 链路 | 脚本（V2 目录，`E2E_TARGET=test`） | 结果 | 对照 |
|---|---|---|---|
| Vault | `vault.py smoke`（worktree） | PASS：Nacos、健康、跨调用方 STABLE、幂等与冲突、REFERENCE、Reveal、拒绝、旧 URL 404、审计、日志检索 | — |
| Client 入驻 | `acceptance_flow.py --approve` | **41/41** | V1 39 步 + V2 的两次 `ClientReviewDecided` 消费核对 |
| 入驻场景 | `scenario_flow.py` | **85/85** | — |
| Client Console 工作区 | `workspace_flow.py` | **53/53** | 与 2026-10-16 test 一致 |
| 接入自助 | `developer_flow.py` | **78/78** | 与 2026-10-16/17 test 一致 |
| 团队与邀请 | `team_flow.py` | **123/123** | 与 2026-10-17 test 一致 |
| Client API | `client_api_flow.py` | **90/90**，GAP 1 | 2026-10-17 test 81 步；V2 版默认含 Gateway 幂等 |
| Client API + Platform 中断 | `client_api_flow.py --outage` | 第 1 次 90/92，第 2 次 **92/92**，GAP 1 | 补偿扫描由 acquiring 执行器完成（XXL 日志 `candidate=1, submitted=1`） |
| 迁移数据抽样 | 本需求 `verification/test_sample_reads.py` | **8/8** | 接入方 5、运营人工账号 5、Target 3 / acquiring 1、决策流 3、规则集 6、入驻案件 48（迁移 34 + 验收新增 14），迁移带入的 34 件详情逐件可读 |
| 运营登录与基础管理 | `smoke.py` | **未运行到业务步骤**：前置门禁报 `Partial or drifted Platform catalog (t_gateway_target)` | 见第 7 节第 4 项；登录与管理读写已由上面各链路覆盖 |
| 风控规则 | `risk_rule_configuration_smoke.py --read-stage` | **未运行到业务步骤**：同一门禁 | 规则集与决策流列表由抽样覆盖（6 / 3，与演练一致） |

- GAP 1 项是已知生产缺口：acquiring 退款客户端仍失败关闭，Payment 没有 PAY-I04，退款停在 PROCESSING。第 14 步报告已登记，不计入通过数。
- `--outage` 第 1 次失败的两项是时序问题：
  - Platform 恢复后，13:57:00 与 13:57:30 两次扫描都补交了任务；13:57:30 的调用已被 Platform 受理并投递，但 acquiring 一侧没有记录成功，任务行保持 ERROR，直到下一次扫描以重复受理收尾。
  - 脚本对"任务行删除"只等 15 秒，因此判失败。
  - 最终状态正确：1 个 Platform 事件、1 次投递、任务行已删除。
  - 2026-10-17 的 test 记录同样写明"AC-05 在第 1 次运行中通过"，这一项受时序影响。

## 7. 偏差与发现

1. **备份位置**按任务卡放在 `test-environment/private/backup-20261018/`。复制 `private/` 时排除了备份目录自身；runbook 原命令会在目录内递归复制。
2. **Nacos 注册 14 个，不是 runbook 写的 18 个。** billing、ledger、account、reconciliation、tax 不依赖 Nacos，彼此以 compose 中的直连 URL 调用（如 `LEDGER_ACCOUNT_SERVICE_URL`），属设计如此。
3. **RabbitMQ 重建后启用了一个新节点。** compose 没有固定 `hostname`，节点名随容器 ID 变化，`force-recreate` 后启用新的 `rabbit@<新容器 ID>`。旧节点目录（含 `cg_*` 队列）仍在卷内但不再使用。
   - 为执行"先导出消息数再删除"，用备份 tgz 的副本，以旧主机名起了一个无网络的一次性 RabbitMQ 读取。
   - 结果：`cg_cct_client_review`、`cg_commerce_payment_result` 及两个 DLQ 消息数均为 0（`rabbitmq-legacy-queues.txt`）。临时容器与卷已删除。
   - 活动节点上没有旧队列，无需删除；卷内旧节点目录留待阶段 3 清理。
4. **`smoke.py` 与 `risk_rule_configuration_smoke.py` 的目录门禁早于本次迁移就已失败。**
   - 门禁要求 `t_gateway_target` 与 `platform-support-initial-data.sql` 逐行相同，即只有 operations-console-bff 1 行。
   - 迁移前的转储里已有 3 个 Target：client-console-bff（2026-10-14 起）、commerce-client-api、operations-console-bff。
   - 迁移只改了其中一个的发现名，所以任何时点运行都会停在这里，不是 V2 回归。
   - 本次没有改门禁逻辑（属 2026-09-07 rollout 工具，见 CURRENT_IMPLEMENTATION 既有遗留）。
5. **两个 Web 镜像构建命中缓存。** 镜像 ID 与创建时间沿用前一天的 `:development`，内容与当前代码一致。
6. **XXL Admin API**（3.4.2）启动任务的参数为 `ids[]`，不是 `id`。
7. **验收新增的测试数据保留在 mor-test**，与以往验收相同：验收 Client 与申请、凭证、端点、订单与退款、夹具投影、Vault 合成记录。

## 8. 回滚点

- 卷快照与逻辑转储：`test-environment/private/backup-20261018/`，含 `SHA256SUMS`、`env.test` 与 `private/` 副本。
- V1 容器（Exited）与镜像保留；`container-image-ids.txt` 记录了每个旧容器的镜像 ID。
- 回滚步骤按 runbook 第 11 节；回滚后核对 `mor_platform` 56 表、Target 发现名 `commerce-service`、Vault 规则 18 条。
- 迁移只读旧库：`mor_cct`、`mor_commerce`、`mor_risk` 原样保留，`mor_v2_migration_archive` 保留。

## 9. 待确认（进入阶段 3 前）

1. **阶段 3 清理范围**，按 runbook 第 10 节：
   - 旧容器与镜像；`DROP DATABASE mor_cct, mor_commerce, mor_risk` 与旧账号；
   - Vault `client-contract-service` 4 条规则；XXL commerce-service 分组；
   - 加上本次发现的 RabbitMQ 卷内旧节点目录，以及 orphan 容器 `webhook-receiver` 是否常驻；
   - 归档库保留期。
2. **`smoke.py` / Risk rollout 工具的目录门禁**：是否另立任务，让它接受运营已登记的 Target。在此之前，这两个脚本无法作为 mor-test 冒烟使用。
3. **内存**：Platform 已达上限的 92%。是否把 large 级上限从 768m 调到 896m，或减小元空间上限。
4. **RabbitMQ `hostname`**：是否在 compose 中固定，避免以后重建再换节点、丢失队列与消息。
5. **`--outage` 的时序判定**：是否把"任务行删除"的等待从 15 秒放宽到一个扫描周期以上（≥ 45 秒）。

## 10. 阶段 2 完成判定（runbook 第 9 节）

| 判定项 | 结果 |
|---|---|
| 迁移核对通过（第 3 步） | **通过**：150/150，守恒，重复执行安全 |
| 全部服务 healthy（第 5 步） | **通过**：29 个容器 |
| 第 6 步登记全部符合预期 | **通过**：Nacos 数量与 RabbitMQ 旧队列两项的偏差已说明原因 |
| 第 7 步验收通过或已记录明确缺口 | **通过并记录缺口**：6 条链路通过，抽样 8/8；`smoke.py` 与风控烟测被既有门禁挡住（第 7 节第 4 项）；退款 GAP 为已知缺口 |
| `delivery.md` 与 `CURRENT_IMPLEMENTATION.md` 已更新 | **完成**（本提交） |
| 旧容器、旧镜像、旧库与归档库保留 | **是** |
