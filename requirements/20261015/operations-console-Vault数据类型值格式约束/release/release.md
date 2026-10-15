# Vault 数据类型值格式约束：mor-test 发布清单（阶段 6 评审稿）

只读核对时间：2026-10-15。执行需另行授权，执行记录写在文末。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次变更：**
- 在 test 的 `mor_vault` 执行 [DDL](../db/vault/ddl.sql)。
- 替换 Vault、Operations BFF、Operations Web 三个应用。
- 经运营入口为 4 个现有数据类型设置值格式。

不改 Platform 登记，也不新增路由、权限或菜单。

## 1. 范围与来源

| 应用 | test 当前镜像 | 当前镜像的来源 | 候选版本 |
|---|---|---|---|
| Vault | `9842248f…bd0c67` | `34ecac9` | `34ecac9` 加本需求；`34ecac9..HEAD` 期间 `vault-service` 没有其他提交 |
| Operations BFF | `68e939f5…22bde80` | 支付风控配置发布，现已提交为 `d02c7dd` | `d02c7dd` 加本需求；`d02c7dd..HEAD` 期间这两个目录没有其他提交 |
| Operations Web | `5fb6911b…3145c34b` | 同上 | 同上 |

- **前置条件：** 本需求的改动先在本地提交（不推送），再用该提交建干净 worktree 构建。工作区里其他会话尚未提交的改动（例如 BFF/Web 的通知管理）不会进入制品。
- **BFF 依赖：** BFF 依赖 `paytech-vault-client`、`risk-api`、`platform-support-api`、`client-contract-api`。沿用上次发布的做法，在 worktree 里用临时聚合 pom 把它们和 BFF 放进同一个 reactor 离线构建，不读取 `~/.m3` 中可能被其他会话覆盖的 SNAPSHOT。
- **不动：** Gateway、Platform、CCT、Risk、Nginx；不推送，不改生产。

## 2. test 现状（只读核对）

| 项 | 值 |
|---|---|
| `t_vault_data_type` | 6 个类型：`CHANNEL_ACCOUNT_CONFIG_V1`、`EMAIL_ADDRESS_V1`、`NOTIFICATION_PROVIDER_CREDENTIAL_V1`、`PHONE_E164_V1`、`TEST_REFERENCE_V1`、`TEST_STABLE_V1`；共 11 列（尚无 `value_format`/`value_pattern`） |
| 策略版本 | 7 |
| 记录与审计 | `t_vault_record` 8 条，`t_vault_access_event` 66 条 |
| 容器 | 16 个 mor-test 容器都在运行；Vault、BFF、Web、Platform 均为 healthy |

## 3. 兼容与顺序

固定顺序：DDL → Vault → BFF → Web → 设置格式。每一步都要兼容上一步的状态：

| 步骤 | 兼容理由 |
|---|---|
| DDL 后、Vault 替换前 | 旧 Vault 用显式列名读写；新列有默认值 `TEXT`，旧 Vault 新建类型也能成功 |
| 新 Vault + 旧 BFF | 旧 BFF 不发送新字段，修改时值格式保持不变；旧客户端会忽略响应里的新字段，旧 Web 看不到新字段，不受影响 |
| 新 BFF + 旧 Web | 旧 Web 不发送新字段 |
| 设置格式之前 | 所有类型都是 `TEXT`，行为与升级前相同 |

CCT、Platform 不需要发布：它们写入的都是规范值，满足新格式；用旧客户端时的错误分类问题见技术方案 R1。

## 4. 执行顺序（任一步不符合预期就停下）

1. **核对：** 重跑第 2 节查询，确认三个运行中镜像与第 1 节一致。
2. **备份：** 用 `mysqldump` 导出 `mor_vault` 的 `t_vault_data_type`、`t_vault_access_rule`、`t_vault_policy_change` 三张表，存到 `~/Code-MoR-backups/20261015-vault-value-format-test/<UTC>/`（目录 0700，文件 0600），记录 SHA-256。
3. **构建**（干净 worktree，候选标签 `candidate-vf-20261015`；制品目录 `test-environment/artifacts/vault-value-format-20261015/` 已被 git 忽略）：
   - **Vault：** Go 1.27.1 离线交叉编译 linux/arm64 的 `vault-server` 与 `vault-probe`，使用 `test-environment/vault/Dockerfile`。
   - **BFF：** 按第 1 节同 reactor 执行 `package -DskipTests`；健康探针从 `test-environment/java/*.java` 现编译。
   - **Web：** 在同一 worktree 中构建镜像。
   - **收尾：** 把三个镜像的 ID 写入 `compose-candidate.yaml`，记录 jar 的 SHA-256。
4. **DDL：** 用 utf8mb4 客户端在 `mor_vault` 执行 `ddl.sql`。核对：新增 2 列，中文注释显示正常，6 行都是 `TEXT`。
5. **替换 Vault：** 执行 `./test-environment/compose.sh -f <candidate> up -d --no-deps --no-build --pull never --wait vault-service`，等待 healthy。
   - **影响：** 替换期间（约数秒），Platform 登录、通知等 Vault 调用会失败。
   - **烟测：** Platform 镜像里没有 curl，改用接入 `mor-test-backend` 的临时 curl 探针容器，对 `TEST_*` 类型执行 Protect/Reveal。
6. **替换 BFF，再替换 Web：** 逐个替换并等待 healthy，期间运营端约 30 秒不可用。替换后确认 `http://localhost:28080/` 返回的 `index.html` 引用的是新构建的资源。
7. **验收 A 段：** 执行 `E2E_TARGET=test python3 -I verification/value_format_acceptance.py a`，使用临时 `E2E_VF_*` 类型，预期 20 项通过。
   - **清理：** 按本机同样的 SQL（只删 `E2E_VF_*` 类型及其规则、记录、审计、幂等映射和策略变更），在同一连接里核对计数后再 COMMIT。清理后策略版本回到 7。
8. **设置格式与回归（B 段）：** 执行 `… value_format_acceptance.py b`，预期 13 项通过。
   - **格式设置：** `EMAIL_ADDRESS_V1` 和 `PHONE_E164_V1` 设为 `REGEX`，两个配置类型设为 `JSON`，共产生 4 个策略版本（8～11）。`TEST_*` 两个类型保持 `TEXT`。
   - **回归内容：** STABLE Token 不变、存量 Token 可解密，以及 CCT 联系人、Platform 登录、渠道账号配置和 SES 凭据读取。
   - **清理：** 删除 B 段起点之后的回归记录、审计和幂等映射，保留策略版本 8～11。
9. **浏览器验收：** 对 test 入口执行 `value_format_browser.py`，预期 14 项通过（AC-9～12）。之后执行 23 个页面的回归。
10. **日志检查：** Vault、BFF、Gateway 的日志中不应出现验收用的原值和样例。

## 5. 回退

| 情况 | 操作 |
|---|---|
| 只回退格式设置 | 在“数据类型”页把 4 个类型改回 `TEXT`（每次改动记一个策略版本），约 10 秒后生效 |
| 应用回退 | 按 Web `5fb6911b…` → BFF `68e939f5…` → Vault `9842248f…` 的顺序，逐个执行 `up -d --no-deps --no-build --pull never --wait`；三个镜像都还在本机 |
| 表结构 | 两个新列保留：旧版本不读取它们，`value_format` 有默认值。只有确认不再需要时，才另行授权按 `ddl.sql` 头部的回滚语句删除 |

## 6. 需要你确认

1. 在本地提交本需求的改动（只提交本需求的文件，不推送）。
2. 按第 4 节在 mor-test 执行：备份、构建、DDL、替换三个应用、设置 4 个类型的格式、验收和清理。

## 7. 执行记录

（待执行）
