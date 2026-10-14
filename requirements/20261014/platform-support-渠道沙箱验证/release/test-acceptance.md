# mor-test 发布执行记录（2026-10-14）

用户批准 [release.md](./release.md) 第 7 节范围后执行（回复“可以”；首次执行 DDL 被工具权限拦截后，用户回复“你执行吧”）。

## 结果

| 步骤 | 结果 |
|---|---|
| 制品 | 干净 worktree `ba36b63` 离线构建；JAR SHA-256：Platform `a327ca28…47c5`、Gateway `db5b7e6c…6b20`、BFF `27fef9c6…cef3`；镜像 ID 见 [compose-candidate.yaml](./compose-candidate.yaml)。构建 worktree 已删除。 |
| 核对 | 四个应用原镜像与 MySQL 容器身份同只读核对一致。 |
| 备份 | `~/Code-MoR-backups/20261014-sandbox-verify-test/20261014T094144Z/`（0700/0600）：`mor_platform` 25 表结构、8 张配置表 1489 行数据，附 SHA256SUMS。 |
| 建表 | `existing_must_be_0=0`；执行后 50 表，与当前完整 Schema 字段、索引逐项一致。 |
| 登记 | 外部 API 344 行（权限 2、授权 2、资源 5、路由 30、Header 111、Query 14、Guard 180），沙箱验证 155 行（路由 14、Header 51、Query 6、Guard 84）；44 路由 DISABLED，ACTIVE 仍 105。 |
| 应用 | Vault、Risk 原镜像启动；Platform（`COMPENSATION_ENABLED=true`）→ Gateway → BFF → Web 候选镜像逐个 healthy；Nginx 原镜像启动；11 个容器 healthy。 |
| 启用 | 试运行 ROLLBACK 后正式 COMMIT：44/5/2/1；ACTIVE 路由 149，PLATFORM_ADMIN v21；Gateway 快照 routeCount=149。 |
| 部署检查 | 管理员经 `http://localhost:28080` 登录；16 个菜单页（人员、角色、权限、菜单、路由、凭证、Scope、租户访问、审计、5 个风控页、标准 API、接入方）无页面错误与 4xx/5xx。 |
| PayerMax | 经管理接口写入 `PAY`、`PAY_QUERY`、接入方 `PAYERMAX`（脚本 2、原子 API 2、实现 2）；验证账号 `UAT_MAIN`、用例 2 个。 |
| UAT 验收 | 未标沙箱时运行 FAILED、`NOT_EXECUTED`（未发请求）；标记沙箱后 `UAT_PAY_THEN_QUERY`（PENDING → PENDING）与 `UAT_QUERY_NOT_FOUND`（NOT_FOUND）PASSED，渠道 HTTP 200；验证证据中检索私钥片段为 0；补偿事件已处理完毕。[回执](./test-acceptance-receipt.json) |

## 与清单的偏差

1. **外部 API 登记提前提交。** 两份登记在同一连接试运行时，第二份文件自带的 `START TRANSACTION` 隐式提交了第一份的 344 行，跳过了“核对后再 COMMIT”。提交后的数据与预期逐表一致（2/2/5/30/111/14/180，全部 DISABLED/REVOKED，ACTIVE 路由不变），未暴露新入口。之后每个文件单独执行：试运行 ROLLBACK 核对后再 COMMIT。今后同一连接不得串接含 `START TRANSACTION` 的多个脚本。
2. **启动顺序。** 环境整体处于停止状态（旧 Gateway 未运行），“先 Gateway”的规则不适用；按 Vault/Risk → Platform → Gateway → BFF → Web → Nginx 启动，避免新 Gateway 等待 Platform 快照。
3. **Gateway 占位配置。** 当前 Gateway 代码因 `@ConfigurationPropertiesScan` 在 Client 通道关闭时仍绑定并校验 `ClientSecurityProperties`，缺少 `gateway.client.security.*` 时启动失败（NPE）。停止崩溃重启的容器后，经用户选择在候选 Compose 中为 Gateway 补 `GATEWAY_CLIENT_SECURITY_ISSUER=https://client-console.local.test`、`GATEWAY_CLIENT_SECURITY_AUDIENCE=client-console`；`gateway.client.enabled` 保持 false。**待 Gateway 负责方修复后删除占位。** 本地端到端未发现，是因为 harness 使用测试模块配置。
4. **部署检查中的 429。** 快速连续整页刷新 16 个页面触发 Gateway 对 `auth/v1/context` 的限流；放慢后复查两个页面正常，属限流生效，不是回归。

## 当前状态与遗留

- test 现运行候选镜像；回退见 [release.md](./release.md) 第 6 节与 [compose-previous.yaml](./compose-previous.yaml)。
- 正式标准 API 执行在 test 仍不可用（Vault 渠道账号读取策略未部署）。
- 付款后查询 `SUCCEEDED` 未验收（需人工在 UAT 收银台付款）。
- 客户入驻（D）由其会话随后单独发布；本次未登记、未初始化其数据，相关开关保持关闭。
