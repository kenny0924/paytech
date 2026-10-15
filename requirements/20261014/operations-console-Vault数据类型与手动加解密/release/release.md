# Vault 数据类型与手动加解密：mor-test 发布记录

阶段 6/7。用户 2026-10-15 授权“可以”，执行中 SQL 与容器替换被权限策略拦截后，用户回复“放行”继续。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：Vault、Operations BFF、Operations Web 三个应用升级到提交 `34ecac9`；test `mor_vault` 执行策略 DDL 与初始化 DML；`mor_platform` 登记 174 行并启用；经 `http://localhost:28080` 验收后清理验收数据。**

## 1. 范围与边界

- 不升级 Gateway、Platform、Risk、CCT、Nginx：新路由由 Gateway 从 Platform 快照动态加载，无需改代码。
- BFF 自 `ba36b63` 起只有本需求提交与一次格式提交；Web 另带已提交的 `349df5e`、`81d546e` 前端改动。
- BFF 依赖的 `risk-api` 在工作区有风控会话未提交改动，`~/.m3` 中的 SNAPSHOT 不可信：在干净 worktree 用临时聚合 pom 把 `risk-api`、`platform-support-api`、`client-contract-api`、`paytech-vault-client` 与 BFF 同一 reactor 构建。`risk-api` 自 `ba36b63` 起的已提交改动全部为新增，BFF 用到的 8 个接口不变。
- 不推送镜像、不改生产配置、不推送 Git。

## 2. 执行前核对（2026-10-15）

- `mor_vault` 只有三表（记录 6、审计 42）；`vault_app` 对 `mor_vault.*` 有全部权限。
- `mor_platform`：Operations Target `900000000000000001` ACTIVE；PLATFORM_ADMIN `900000000000000510` ACTIVE/v22；操作者 `221546852372267008`（OPERATIONS、ACTIVE）；本次 ID 段与 `/vault-admin` 路径无冲突；当前 188 条路由 ACTIVE。
- 运行中容器与新 Compose 配置比对：BFF、Web 只换镜像；Vault 去掉 `policy.json` 挂载与 `VAULT_POLICY_FILE`、加入 `VAULT_ADMIN_CALLERS`。
- 运行中 Vault 的原镜像 `7906efea…` 在本机已不存在，回退镜像改为从 `34ecac9^` 构建。

## 3. 制品

| 服务 | 镜像 ID | 说明 |
|---|---|---|
| Vault | `sha256:9842248f…bd0c67` | Go 1.27.1 离线交叉编译 linux/arm64 |
| Operations BFF | `sha256:eb67cff1…834354` | 健康探针由 `34ecac9` 源码重新编译（首个镜像 `666ddfc5…` 误用了 `artifacts/health-probe` 中过期的探针，退出码 64，已替换） |
| Operations Web | `sha256:9f8c27cb…98821b` | `npm run build` |
| Vault 回退 | `sha256:4e654dd2…15799b` | `34ecac9^`，读取策略文件 |

候选 Compose：[compose-candidate.yaml](./compose-candidate.yaml)。制品位于 `test-environment/artifacts/vault-admin-20261015/`（不提交）。BFF/Web 回退镜像：`f73da253…`、`7455978a…`（本机仍在）。

## 4. 执行记录

1. 备份 `~/Code-MoR-backups/20261015-vault-admin-test/20261015T050913Z/`（0700/0600）：`mor_vault.sql`（SHA-256 `47c841d6…`）、Platform 8 张登记表数据（`708a1c6c…`）。
2. `mor_vault` 以 utf8mb4 执行 [ddl.sql](../db/vault/ddl.sql)；四表定义（含注释）与本机逐字一致。
3. [dml-test.sql](../db/vault/dml-test.sql) 在同一连接中核对 4/11/1 后 COMMIT（4 类型、11 规则、版本 1）。
4. 替换 Vault，healthy。`vault.py smoke` 原探针依赖 Platform 容器中的 curl，而当前 Platform 镜像已无 curl；改用接入 `mor-test-backend` 的临时 nginx-alpine curl 容器执行同一套检查，全部通过（含审计 `policy_version LIKE 'db-%'` 与日志扫描）。
5. [Platform 登记](../db/platform-support-service/registration-dml.sql)（`@vlt_operator=221546852372267008`）核对 6/6/11/13/42/18/78 后 COMMIT，全部 DISABLED/REVOKED。
6. 替换 BFF（首次健康探针失败，换镜像后 healthy）与 Web（healthy）。
7. [test-activate.sql](./test-activate.sql) 核对 13/11/6、PLATFORM_ADMIN v23 后 COMMIT；Gateway 快照立即生效。

## 5. 验收

- API（[local_acceptance.py](../verification/local_acceptance.py)，`E2E_TARGET=test`）：27/27 通过（AC-1～9、11）；新类型 12.3 秒生效，规则 4.3 秒生效、停用 9.5 秒生效。
- 浏览器（[local_browser.py](../verification/local_browser.py)）：19/19 通过（AC-10）。
- 回归：管理员可见的 23 个页面逐一打开，无失败请求（首轮连续整页刷新触发 `auth/v1/context` 429 限流，间隔 8 秒重测通过）。
- 日志：BFF、Vault、Gateway 中无验收原值。
- 清理：删除 T0（05:19:43Z）后的验收审计 35、记录 5、幂等映射 5，`E2E_S5_*` 类型 3、规则 1，版本 2～6 变更 5；`mor_vault` 回到记录 6、审计 52、幂等映射 8、版本 1。Vault 烟测合成数据按 test 惯例保留。

## 6. 恢复

- 停用入口：[test-deactivate.sql](./test-deactivate.sql)（核对 13/11/6 后 COMMIT）。
- 回退镜像（逐个 `up -d --no-deps --no-build --pull never --wait`）：Web `7455978a…`、BFF `f73da253…`；Vault 用 `mor-test/vault-service:rollback-pre-vault-admin`，并恢复 `policy.json` 挂载与 `VAULT_POLICY_FILE=/app/policy.json`（按 `34ecac9^` 的 `compose.applications.yaml`）。
- 新表与新增列保留：旧版本不读取，新列可空。只有确认不再需要时，才经单独授权按 ddl.sql 头部说明回滚。

## 7. 遗留

- `test-environment/scripts/vault.py smoke` 依赖 Platform 容器内 curl，当前 Platform 镜像已不含 curl，脚本原样无法运行；需要改为独立探针容器（本次未改脚本）。
- `test-environment/artifacts/health-probe` 中的探针类早于 `06f46b9`，单独复用会导致 BFF 健康检查失败；构建应始终从 `test-environment/java/*.java` 编译。
