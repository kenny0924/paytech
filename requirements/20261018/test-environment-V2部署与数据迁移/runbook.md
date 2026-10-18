# mor-test 切换到 V2 · 阶段 2 Runbook

- 状态：阶段 1 评审稿（2026-10-18），阶段 2 经用户确认后按本文执行。所有命令在主仓库根目录（下称 `$REPO`）执行；任何对 mor-test 的写操作只从第 3 步开始。
- 依据：[requirement.md](requirement.md)、[design/01-migration-map.md](design/01-migration-map.md)、[阶段 1 演练报告](tasks/01-report.md)；启动顺序取自第 13 步报告第 6 节第 7 项。
- 约定：口令只经环境变量传入，命令不回显口令；`compose.sh` 已包含三份基础 Compose 文件，只需追加 `--profile` 以外的参数。
- 每步写"预期"；与预期不符即停止，按第 11 节回滚或报告，不临场改 SQL。

```bash
export REPO=/Users/kenny/Desktop/Workspace/Code-MoR
export REQ="$REPO/requirements/20261018/test-environment-V2部署与数据迁移"
export TE="$REPO/test-environment"
export BACKUP=~/Code-MoR-backups/20261018-mor-test-v2/$(date -u +%Y%m%dT%H%M%SZ)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
mysql_root() { MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' "$TE/.env.test" | cut -d= -f2-)" \
  mysql -h127.0.0.1 -P23306 -uroot --protocol=tcp --default-character-set=utf8mb4 "$@"; }
```

## 0. 前置核对（只读）

1. 代码：`git -C "$REPO" log -1 --oneline` 为已合入阶段 1 的 main；`git status` 无与本次无关的未提交改动影响 `test-environment/`、`paytech/`。
2. 口令：`.env.test` 已含 15 个 V2 键（`prepare.py` 只追加）：
   ```bash
   python3 "$TE/scripts/prepare.py"   # 预期：Existing test material retained；不再追加
   ```
3. 内存与磁盘：
   ```bash
   docker info --format '{{.MemTotal}}'; docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' | sort; df -h /
   ```
   预期：Docker 总内存约 20.5 GiB；nexus、mor-local、gitlab 等其他容器实际占用加上 mor-test 预计常驻约 7 GiB 后仍有余量（演练报告第 4 节）。磁盘可用不少于 15 GiB（镜像约 1.5 GB、卷备份约 1 GB）。gitlab 占用超过 8 GiB 时先报告再继续。
4. 记录现状（回滚用，不含口令）：
   ```bash
   mkdir -p -m 700 "$BACKUP"
   docker ps -a --filter name=mor-test- --format '{{.Names}}\t{{.Image}}\t{{.Status}}' > "$BACKUP/containers.txt"
   for c in $(docker ps -a --filter name=mor-test- --format '{{.Names}}'); do
     printf '%s\t%s\n' "$c" "$(docker inspect -f '{{.Image}}' "$c")"; done > "$BACKUP/container-image-ids.txt"
   git -C "$REPO" rev-parse HEAD > "$BACKUP/repo-head.txt"
   ```
   预期：旧服务容器 `client-contract-service`、`commerce-service`、`risk-service`、`platform-support-service`、`api-gateway` 等均在列表中；回滚使用 V1 Compose（commit `b59ec6b8`，main 上 V2 合入前最后一次修改 `compose.applications.yaml`）与此处记录的镜像 ID。
5. 迁移前置（只读）：
   ```bash
   export MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' "$TE/.env.test" | cut -d= -f2-)" MT_PORT=23306
   "$REQ/scripts/migrate.sh" plan
   ```
   预期：五个旧库表数 56/6/38/17/85；**旧库结构指纹与演练一致**（不一致说明转储后 mor-test 结构变化，停止并重新演练）；V2 口令齐全；各 V2 库为 ABSENT / EMPTY。若 `mor_payment` 或 `mor_channel_connector` 报 FOREIGN，记录其表与行数，经用户确认后在第 3 步加 `--park-legacy`。
   注意：此时旧服务已停（容器 Exited），所以"旧服务账号无连接"通过；若有连接，先完成第 1 步。

## 1. 冻结与停旧服务

```bash
docker ps --filter name=mor-test- --format '{{.Names}}'          # 预期只有 mor-test-mysql-1（或基础设施）在运行
for c in client-contract-service commerce-service risk-service platform-support-service operations-console-bff \
         client-console-bff api-gateway nginx operations-console-web client-console-web vault-service xxl-job-admin; do
  docker stop "mor-test-$c-1" 2>/dev/null || true
done
```

预期：只剩 `mor-test-mysql-1` 运行（主会话为读数据起过它）。不删除任何旧容器（阶段 3 才删）。

## 2. 备份（卷快照 + 逻辑转储）

1. 逻辑转储（MySQL 在线、应用已停）：
   ```bash
   for db in mor_platform mor_vault mor_cct mor_commerce mor_risk mor_payment mor_finance nacos_config xxl_job; do
     mysql_root -N -e "SELECT 1 FROM information_schema.schemata WHERE schema_name='$db'" | grep -q 1 || continue
     MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' "$TE/.env.test" | cut -d= -f2-)" \
       mysqldump -h127.0.0.1 -P23306 -uroot --protocol=tcp --single-transaction --routines --triggers --databases "$db" \
       > "$BACKUP/$db.sql"
   done
   ```
2. 卷快照（停 mysql、redis、rabbitmq 后打包；MinIO 不变更，也一并打包）：
   ```bash
   docker stop mor-test-mysql-1 mor-test-redis-1 mor-test-rabbitmq-1 mor-test-minio-1 2>/dev/null || true
   for v in mor-test-mysql-data mor-test-redis-data mor-test-rabbitmq-data mor-test-minio-data; do
     docker run --rm -v "$v":/src:ro -v "$BACKUP":/dst alpine:3.22 tar -C /src -czf "/dst/$v.tgz" .
   done
   cp -p "$TE/.env.test" "$BACKUP/env.test"; cp -Rp "$TE/private" "$BACKUP/private"; chmod -R go-rwx "$BACKUP"
   (cd "$BACKUP" && shasum -a 256 * > SHA256SUMS)
   ```
   预期：4 个 `.tgz`、各库 `.sql`、`SHA256SUMS`；目录 0700。

## 3. 基础设施与迁移

1. 起基础设施（MySQL 按新 `--max-connections=400` 重建；RabbitMQ 按 `enabled_plugins` 挂载重建以启用一致性哈希插件）：
   ```bash
   "$TE/compose.sh" up -d --no-deps --wait --force-recreate mysql rabbitmq
   "$TE/compose.sh" up -d --no-deps --wait redis nacos minio mailpit xxl-job-admin
   mysql_root -N -e "SHOW VARIABLES LIKE 'max_connections'"            # 预期 400
   docker exec mor-test-rabbitmq-1 rabbitmq-plugins list -e | grep consistent_hash   # 预期已启用
   ```
2. 迁移（V2 口令取自 `.env.test`；`--park-legacy` 只在第 0 步 plan 报 FOREIGN 且已确认时追加）：
   ```bash
   export MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' "$TE/.env.test" | cut -d= -f2-)" MT_PORT=23306
   MT_VERIFY_OUT="$BACKUP/verify-detail.txt" "$REQ/scripts/migrate.sh" apply 2>&1 | tee "$BACKUP/migrate-apply.log"
   ```
   顺序：10 建库与账号 → 14 个 V2 库 Schema（8 个空库加初始化 DML）→ 20 Platform → 21 Vault → 30 / 31 / 32 迁移 → 33 归档 → 50 XXL-Job → 核对。
   预期：末尾"核对通过"：40 全部 OK（演练为 150 项）；三旧库总行数 = V2 六库 + 归档；14 个库表集合与 Schema 一致；`mor_platform` 57 表、USD、Target 发现名 acquiring-service、Vault 7 条规则；risk-decision 61 个种子主键齐全。
   **不要执行** `initialize.py --apply-risk-sql`（会向已迁入目录的 `mor_risk_decision` 重放种子）与 `--apply-service-sql`（由 migrate.sh 取代）。
3. 重复执行安全：`migrate.sh apply` 再执行一次应全部"跳过"，`migrate.sh verify` 仍通过。

## 4. 构建镜像

```bash
"$TE/scripts/build.sh"                                      # 18 个 Java 服务 + operations-console-web + nacos
"$TE/compose.sh" build client-console-web
GO_BIN=~/sdk/go1.27.1/bin/go "$TE/scripts/build-vault.sh"   # Vault 镜像（Linux ARM64）
docker image ls --format '{{.Repository}}:{{.Tag}}\t{{.ID}}' | grep 'mor-test/.*:development' > "$BACKUP/v2-images.txt"
```

预期：20 个 `mor-test/*:development` 镜像（18 Java + 2 Web）与 Vault、Nacos 镜像更新。`build.sh` 打印的 Maven 结果为 BUILD SUCCESS。

## 5. 按顺序启动（每次 `up -d --no-deps --wait`）

```bash
up() { "$TE/compose.sh" up -d --no-deps --wait "$@"; }
up vault-service
up platform-support-service
up client-master-service commercial-service compliance-service risk-decision-service
up consumer-service channel-connector-service card-service payment-service
up billing-service ledger-service account-service reconciliation-service tax-service
up acquiring-service
up operations-console-bff client-console-bff
up api-gateway
up operations-console-web client-console-web
up nginx
"$TE/compose.sh" ps --format '{{.Service}}\t{{.Status}}'
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}' | grep mor-test- | sort > "$BACKUP/memory-after-start.txt"
```

预期：全部 healthy（18 个 Java 服务、Vault、2 个 Web、Nginx 与基础设施）。任一服务失败：`"$TE/compose.sh" logs --tail 200 <服务>`，不继续后续服务。内存记录与演练报告第 4 节对照，接近 `mem_limit` 的服务报告。

## 6. 登记与配置核对

| 项 | 命令 / 动作 | 预期 |
|---|---|---|
| Nacos | `docker exec mor-test-nacos-1 curl -s 'http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=100&namespaceId=mor-test'`（需登录 Token 时按 `initialize.py` 的登录方式取） | 18 个 V2 服务名与 `vault-service` 已注册；旧名 `risk-service`、`client-contract-service`、`commerce-service` 无实例 |
| Gateway Target | `docker exec mor-test-api-gateway-1 java -cp /app/health-probe LocalSnapshotEvidenceFetch http://platform-support-service:18106/platform-support/gateway-config/v1/public-snapshot`（或运营后台"网关路由"） | 3 个 Target，`commerce-client-api` 发现名 `acquiring-service`；CLIENT_API 9 条路由不变 |
| Vault 规则 | `mysql_root -N -e "SELECT caller_service, COUNT(*) FROM mor_vault.t_vault_access_rule WHERE status='ACTIVE' GROUP BY 1"` | `client-master-service` 4、`card-service` 2、`channel-connector-service` 1（Vault 10 秒内刷新，无需重启） |
| XXL-Job | Admin `http://127.0.0.1:28090/`：执行器 `acquiring-service`、`platform-support-service` 在线约 30 秒后出现；启动 acquiring 的 `compensationEventScan` 任务 | acquiring 任务调度成功；commerce-service 任务保持停止 |
| Webhook 调用方 | `docker exec mor-test-platform-support-service-1 printenv SPRING_APPLICATION_JSON` 只看键名 | `webhook.http.callers` 仅 `acquiring-service` |
| RabbitMQ 旧队列 | `docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues -p /mor-test name messages \| grep -E 'cg_(commerce\|cct)_'` | 消息数为 0 后删除：`rabbitmqctl delete_queue -p /mor-test <队列>`（含 `_dlq`）；有消息先导出并报告 |
| 内部 Token | `internal-auth.mode` 全部 OFF（compose 默认） | 不登记签发白名单 |

## 7. 验收

V1 时期的 mor-test 验收脚本仍引用旧库与旧服务名（`mor_cct`、`mor_commerce`、`cct_app`、`commerce-service`），**执行前须改为 V2 名称**（阶段 2 的第一项代码工作，见报告待确认第 8 项）；`register_client_api.py` 会把 Target 改回 `commerce-service`，**不得再执行**。

| 链路 | 脚本 | 预期 |
|---|---|---|
| 运营登录与基础管理 | `python3 "$TE/scripts/smoke.py"` | 全部通过 |
| Vault | `python3 "$TE/scripts/vault.py" smoke`（Platform 镜像无 curl，按 README 用临时探针容器） | 就绪、STABLE 重放、未授权 403 |
| Client 入驻 | `E2E_TARGET=test python3 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/acceptance_flow.py --approve`（读库改为 `mor_client_master`） | 39 步 + V2 新增的审核结论落地核对 |
| Client Console | 同目录 `workspace_flow.py`、`developer_flow.py`、`team_flow.py`（调用方改为 `acquiring-service`） | 与 2026-10-16/17 mor-test 步数一致 |
| Client API | `E2E_TARGET=test python3 requirements/20261017/commerce-ClientAPI测试环境联通/verification/client_api_flow.py`（读库改为 `mor_acquiring`） | 81 步通过；补偿扫描由 acquiring 执行器完成 |
| 风控规则 | `python3 "$TE/scripts/risk_rule_configuration_smoke.py"`（查询库改为 `mor_risk_decision`，Nacos 名改为 `risk-decision-service`） | 规则列表、流程、发布查询正常 |
| 迁移数据抽样 | 运营后台抽看：外部 API 配置（5 个接入方）、Gateway 路由、运营账号（5 个人工账号）、风控规则集（6 个可配置）、客户申请审核（34 个 Client 案件） | 与演练报告第 3 节数量一致 |

结果写入 `delivery.md`，并在 `agent-docs/CURRENT_IMPLEMENTATION.md` 的"mor-test V2 部署"Pending 更新为已部署（镜像 ID、迁移核对、验收结果）。

## 8. 日志与敏感信息检查

```bash
for c in $(docker ps --filter name=mor-test- --format '{{.Names}}'); do docker logs --since 30m "$c" 2>&1; done \
  | grep -c -E 'BEGIN PRIVATE KEY|whsec_|password=' || true      # 预期 0
```

## 9. 阶段 2 完成判定

- 迁移核对通过（第 3 步）；全部服务 healthy（第 5 步）；第 6 步登记全部符合预期；第 7 步验收通过或已记录明确缺口；`delivery.md` 与 `CURRENT_IMPLEMENTATION.md` 已更新。
- 旧容器、旧镜像、旧库（`mor_cct`、`mor_commerce`、`mor_risk`）与归档库保留，进入阶段 3 前由用户确认。

## 10. 阶段 3 预告（不在阶段 2 执行）

删除旧服务容器与镜像；迁移核对通过并经用户确认后 `DROP DATABASE mor_cct, mor_commerce, mor_risk` 与对应旧账号；停用 Vault 中 `client-contract-service` 的 4 条规则；删除 XXL 的 commerce-service 分组；`mor_v2_migration_archive` 的保留期由用户决定。

## 11. 回滚

适用：第 3 步之后任一步失败且不能就地修正。回滚 = 恢复卷快照 + V1 Compose 与镜像。

```bash
# 1) 停 V2 应用与基础设施
"$TE/compose.sh" stop
# 2) 恢复卷（先清空再解包，四个卷同法）
for v in mor-test-mysql-data mor-test-redis-data mor-test-rabbitmq-data mor-test-minio-data; do
  docker run --rm -v "$v":/dst -v "$BACKUP":/src:ro alpine:3.22 sh -c "find /dst -mindepth 1 -delete && tar -C /dst -xzf /src/$v.tgz"
done
cp -p "$BACKUP/env.test" "$TE/.env.test"
# 3) V1 Compose：检出 b59ec6b8 的 test-environment 到临时 worktree，镜像按 container-image-ids.txt 固定
git -C "$REPO" worktree add /private/tmp/mor-test-v1-rollback b59ec6b8
#    在该 worktree 的 test-environment 下写 compose.rollback.yaml：每个服务 image: <container-image-ids.txt 中的镜像 ID>，
#    复制 $TE/.env.test 与 $TE/private 后，按 V1 README 的顺序 up -d --no-deps --wait。
```

回滚后核对：`mor_platform` 56 表、Gateway Target 发现名 `commerce-service`、Vault 规则 18 条、旧服务 healthy、运营登录可用。迁移只读旧库，单独回滚某个库时也可只 `DROP DATABASE` 对应 V2 库后重跑 `migrate.sh apply`（各 SQL 文件头部有逐项回滚语句）。
