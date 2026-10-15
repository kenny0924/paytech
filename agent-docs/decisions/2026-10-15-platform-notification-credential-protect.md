# Platform 写入通知发送账号密钥决定

- Decision status: Confirmed
- Implementation status: Implemented（本地，未在任何环境添加 Vault 规则）
- Deployment status: Not deployed
- Decision date: 2026-10-15
- 用户决定：发送账号的密钥跟账号绑定、在运营后台维护（页面录入，Platform 代为 Protect）；只支持 Vault 中的 IAM 密钥，不支持运行环境角色 / AWS 默认凭据链；Vault 用途沿用 `NOTIFICATION_PROVIDER_SETUP`（需求 Q-01、Q-06）。
- 需求与方案：[通知发送账号维护](../../requirements/20261015/operations-console-通知发送账号维护/requirement.md)、[技术方案](../../requirements/20261015/operations-console-通知发送账号维护/technique.md)

## 1. 决定

在 [SES 接入](../../requirements/20261014/platform-support-邮件AmazonSES接入/requirement.md)已批准的数据类型 `NOTIFICATION_PROVIDER_CREDENTIAL_V1`（REFERENCE、SECRET、JSON 值 `{"accessKeyId","secretAccessKey"}`）上新增一条 Protect 授权：

| Caller | Action | Data Type | Purpose |
|---|---|---|---|
| `platform-support-service` | PROTECT | `NOTIFICATION_PROVIDER_CREDENTIAL_V1` | `NOTIFICATION_PROVIDER_SETUP` |

- Platform 只在「录入 / 更换发送账号密钥」管理接口中使用该授权，在数据库事务外 Protect，再以短事务把 Token 写入 `t_notification_provider.credential_vault_token`；密钥原文只在本次请求内存中，不进入日志、审计、异常与响应。
- 既有 `platform-support-service / REVEAL / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_DELIVERY` 不变：发送前按账号 Token 逐次 Reveal，不缓存。
- 浏览器 → Gateway → BFF → Platform 的请求体经过 `/notification-admin` 前缀，BFF 与 Platform 均为 metadata-only 日志；Gateway 访问日志不记 Body。

## 2. 不变的边界

- Vault 仍不拥有业务含义、不增加业务表；Vault 代码不变，只增加策略数据中的一条访问规则。
- Vault V1 没有 Retire：换密钥后旧 Token 不再被账号引用，密文保留，与项目"不自动销毁"一致。
- 人工加密（运营在 Vault 手动加密页 Protect）仍可用；人工解密保持关闭。
- 不扩大卡数据、登录密码、KMS/Vault 根密钥及其他系统 Secret 的边界。

## 3. 精确取代范围

- 取代 SES 接入需求第 6.1 节"Platform 只在部署配置 `notification.ses.credential-token` 中保存 Vault Token、Token 为空时走 AWS 默认凭据链"：Token 改存账号表，默认凭据链删除；账号的区域、发件地址、Configuration Set 同时移入账号表。
- [Vault 架构](../VAULT_SERVICE_ARCHITECTURE.md)顶部例外清单追加本决定。

## 4. 后续实施

本机与 mor-test 的 Vault 规则分别经运营后台 Vault 管理页添加，属于环境动作，在对应阶段单独授权；执行与验证状态只记在 [当前台账](../CURRENT_IMPLEMENTATION.md)。
