# Platform 去除 Vault Mock：mor-test 发布清单（评审稿）

阶段 6 发布准备。只读核对已完成（2026-10-15）。用户选择执行 mor-test Platform 发布，并决定 `1***@xx.com` 账号不迁移（2026-10-15），按本清单执行；结果见文末执行记录。

**本次：test Platform 从 `7e9eddc` 升级到 `84b3174`（干净构建）；test Vault 补 2 个数据类型、4 条规则；把 ACTIVE 运营账号的登录邮箱 Token 从 Mock 值迁移为真实 Vault Token。**

## 1. 为什么必须迁移

`84b3174` 删除了 `LocalLoginEmailVault`。test 现在的 Platform 用 `test` profile，登录时用这个 Mock 把邮箱算成 `mock_…` Token 并按 Token 查账号。新版本改走真实 Vault，同一邮箱得到另一个 Token，**不迁移则所有运营账号无法登录**。Mock Token 是邮箱的哈希，无法反推邮箱，只能用已知邮箱重新 Protect 后写回。

## 2. 范围与边界

| 项 | 内容 |
|---|---|
| Platform 代码 | `7e9eddc..84b3174` 共 3 个 Platform 提交：PayerMax 卡直付经 Platform（`81d546e`）、SES 邮件（`b793bdd`）、本次去 Mock（`84b3174`）。Schema 无变化；只改了 1 个 Mapper 的查询方法名 |
| 新功能是否生效 | SES 受 `notification.enabled` 控制，test 关闭；卡直付读卡调用 Payment 时才做服务发现，test 没有 Payment，不影响启动，相关流程本来也不在 test |
| 配置 | 运行中容器与仓库 Compose 环境变量、挂载一致，无需改 Compose |
| 依赖 | `client-contract-api`、`paytech-payment-api`、`paytech-vault-client`、Foundation 工作区均无未提交改动；仍在干净 worktree 与 Platform 同一 reactor 构建，避开 `~/.m3` 漂移 |
| 不动 | Gateway、BFF、Web、Risk、CCT、Nginx、Vault 镜像；不推送、不改生产 |

## 3. 账号现状与迁移方案

| 账号 | 状态 | 邮箱（掩码） | 处理 |
|---|---|---|---|
| `221546852372267008` | ACTIVE | `o***@example.test` | 管理员 `operator@example.test`（来自 `private/access.md`），迁移 |
| `221592347010482176` | ACTIVE | `1***@xx.com` | 用户 2026-10-15 决定不迁移：保持原值，发布后该账号无法登录 |
| 3 个账号 | DISABLED | `a***@example.test`、`r***@example.test` ×2 | 邮箱未知，保持原值：停用期间不能登录，不受影响；以后若重新启用需重新创建或另行修复 |
| 24 个账号 | CLOSED | — | 保持原值，不受影响 |
| Bootstrap `admin` | DISABLED | 无 Token | 不涉及 |

- CUSTOMER 账号（1 个）已是真实 Vault Token，不涉及。审计属性中没有 Mock Token。
- 迁移方式：以 `platform-support-service` 身份调用 test Vault Protect（`EMAIL_ADDRESS_V1`/STABLE/`PLATFORM_LOGIN_EMAIL_V1`，规则已存在），按与 Platform `OperationsPasswordPolicy.canonicalEmail` 相同的规范化（NFC、本地部分小写、域名 IDN 转 ASCII 后小写）得到 Token；核对新 Token 解密回同一邮箱、掩码与库中一致后，`UPDATE t_user_account SET email_vault_token=?, version=version+1 WHERE account_id=? AND email_vault_token=<原 mock 值>`，核对 1 行后 COMMIT。邮箱原文只在执行进程内存中，不写入文件或日志。

## 4. 执行顺序（任一步不符即停止）

1. **核对**：重跑第 3 节查询，确认账号与 Token 未变；Platform 运行镜像仍为 `b211ce16…`。
2. **备份**：`t_user_account` 全表数据与 `mor_vault` 策略三表，存 `~/Code-MoR-backups/20261015-platform-vault-test/<UTC>/`（0700/0600），记录 SHA。
3. **构建**：干净 worktree `84b3174`，Platform 与依赖 API 同 reactor `package -DskipTests`；健康探针从 `test-environment/java/*.java` 编译；镜像 tag `mor-test/platform-support-service:vault-login-20261015`，写入候选 Compose。
4. **Vault 策略**：经 `http://localhost:28080` 管理接口补 2 个类型（`CHANNEL_ACCOUNT_CONFIG_V1`、`NOTIFICATION_PROVIDER_CREDENTIAL_V1`，SECRET/REFERENCE）与 4 条规则（同本机）。对旧 Platform 无影响。
5. **替换 Platform**（`up -d --no-deps --no-build --pull never --wait`），healthy。**从这一步到第 6 步完成，运营端无法登录**（预计 1～2 分钟）。
6. **迁移账号 Token**（第 3 节），逐个核对后 COMMIT。
7. **部署检查**：管理员经 `http://localhost:28080` 登录；人员管理列表与详情、修改密码页可用；23 个页面回归；Platform 日志无邮箱原文；以新规则实测渠道账号配置解密与越权拒绝（合成数据随后清理）。

## 5. 恢复

- 第 6 步前失败：用 `b211ce16…` 回退 Platform 镜像即可。
- 第 6 步后失败：先按备份把已迁移账号的 `email_vault_token` 改回原 `mock_…` 值（version+1），再回退 Platform 镜像。
- Vault 新增的类型与规则：旧 Platform 不使用，保留；如需撤销，在页面停用规则、停用类型。

## 6. 需要你确认

1. 按本清单执行（第 2～7 步）。
2. 第二个 ACTIVE 账号 `1***@xx.com` 的完整邮箱；或确认不迁移（发布后该账号无法登录）。

## 7. 执行记录（2026-10-15）

| 步骤 | 结果 |
|---|---|
| 核对 | 两个 ACTIVE 账号仍为 `mock_` Token；Platform 运行镜像 `b211ce16…` |
| 备份 | `~/Code-MoR-backups/20261015-platform-vault-test/20261015T065941Z/`：`t_user_account`（SHA-256 `30ff8df9…`）、Vault 策略三表（`14540b29…`） |
| 构建 | 干净 worktree `84b3174`，Platform 与 `client-contract-api`、`paytech-payment-api`、`paytech-vault-client` 同 reactor；JAR SHA-256 前缀 `1c33ec877f7dad46`；镜像 `sha256:d5794c9c…95bd566`，见 [compose-platform-candidate.yaml](./compose-platform-candidate.yaml)；worktree 已删除 |
| Vault 策略 | 经管理接口新增 2 类型、4 规则，test 策略版本 1→7 |
| 替换与迁移 | 07:00:57Z 开始替换，Platform healthy 后立即迁移管理员 Token（核对 Reveal 与掩码，按原 mock 值条件更新 1 行），07:01:23Z 完成；运营端不可登录约 26 秒。`1***@xx.com` 按用户决定未迁移 |
| 部署检查 | 管理员接口与浏览器登录成功；23 个页面回归无失败请求；渠道账号配置解密 200、换用途 403、通知邮箱加/解密 200、通知凭据自行加密 403；Platform 日志无邮箱原文。实测数据（审计 7、幂等 3、记录 2）已清理 |
| 偏差 | 首轮浏览器回归因短时间多次登录被限流停在登录页（各页误判 OK）；脚本加入登录完成等待后重跑通过 |
