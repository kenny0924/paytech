# Operations 通知发送账号维护需求

> 状态：阶段 1 已确认（2026-10-15，Q-01～Q-10 全部按建议），阶段 2 已确认（[analysis.md](./analysis.md)），阶段 3 已确认（[technique.md](./technique.md)），阶段 1—5 已确认，已提交 `0301719`（未推送）；阶段 6—7 已部署 mor-test，待确认。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[通知管理](../operations-console-通知管理/requirement.md)上线后，运营后台能看到发送账号并启停，但账号的区域、发件地址和密钥仍在部署配置里：

- 部署配置 `notification.ses.*` 只能声明**一个** SES 账号，进程启动时创建适配器；
- 改发件地址或换密钥要改 Compose / 环境变量并重启 Platform；
- 页面上会出现「本环境已配置：否」的账号，路由到它的邮件发不出去（mor-test 的 `SES_PRIMARY` 就是这种情况）。

通知管理需求 Q-02 决定"新建账号与凭据维护单独立项"，本需求即该立项。用户 2026-10-15 的方向：密钥跟账号绑定、在页面维护；只支持 Vault 中的 IAM 密钥，**不支持**不用密钥（运行环境 IAM 角色 / AWS 默认凭据链）的方式。

当前事实（2026-10-15）：

| 项 | 现状 |
|---|---|
| 账号表 | `t_notification_provider`：编码、介质、状态、`version`，无区域、发件地址、凭据字段 |
| 部署配置 | `notification.ses.enabled`、`provider-code`、`region`（默认 `me-central-1`）、`from-address`、`configuration-set`、`endpoint`（测试用终端覆盖）、`credential-token`、`connect-timeout`、`read-timeout` |
| 适配器 | 启动时创建一个 `SesV2Client`；`EmailProviders` 按 `provider_code` 精确匹配，匹配不到即"未配置" |
| 凭据 | 密钥对 `{"accessKeyId","secretAccessKey"}` 存 Vault（数据类型 `NOTIFICATION_PROVIDER_CREDENTIAL_V1`），由运营在 Vault 手动加密页 Protect 后把 Token 填进部署配置；Platform 只有 `REVEAL / NOTIFICATION_DELIVERY` 权限，每次发送前读取、不缓存 |
| Token 为空时 | 走 AWS 默认凭据链 |
| Vault 能力 | V1 只有 Protect / Reveal；Retire（解除引用）不在 V1 范围 |
| 日志 | BFF、Platform 的 MVC 请求 / 响应日志会记完整 Body；已有 `web.logging.metadata-only-path-prefixes` 机制可让指定路径只记元数据 |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 运营配置人员 | 在后台新建发送账号，维护区域、发件地址、Configuration Set；修改即时生效，不重启 |
| 有凭据权限的管理员 | 录入或更换账号的 IAM 密钥对；录入后任何人（包括自己）都看不到原文 |
| 运营排查人员 | 确认某账号配置是否完整、能否发信（测试发送） |

## 3. 本期范围（建议）

1. **账号字段入库**：账号表新增服务商类型（本期只有 `AMAZON_SES`）、区域、发件地址、Configuration Set（可空）、凭据 Vault Token。
2. **新建账号**：编码创建后不可改；新建默认「停用」。
3. **编辑账号**：修改区域、发件地址、Configuration Set；带乐观锁；被启用路由引用时二次确认并提示影响。
4. **录入 / 更换密钥**：页面只写不读（Access Key ID、Secret Access Key 两个输入框，提交后清空，任何接口都不返回原文或 Token）；Platform 在事务外 Protect 到 Vault，提交后在本地事务里替换 Token。
5. **启用条件**：区域、发件地址、密钥都齐全才能启用；列表「本环境已配置」改为「配置完整」，并显示密钥是否已录入、最近更换时间。
6. **发送端改造**：按账号读取配置，按区域创建并缓存 SES 客户端；账号修改后下一次发送即用新配置。每次发送前仍从 Vault 读取密钥、不缓存原文。删除"Token 为空走默认凭据链"的分支。
7. **测试发送**（建议纳入，见 Q-04）：指定收件邮箱，用该账号发一封固定内容的测试邮件，同步返回结果码；不进入发送记录，只记审计。
8. **部署配置收敛**：`notification.ses.provider-code`、`region`、`from-address`、`configuration-set`、`credential-token` 删除；`enabled`（环境总开关）、`endpoint`（测试终端覆盖）、超时保留。
9. **日志与泄露防护**：密钥接口放在已是 metadata-only 的 `/notification-admin` 前缀下（阶段 2 核对，见 analysis.md）；异常、审计、补偿 Payload 不含原文与 Token。
10. **权限与审计**：新建、编辑沿用 `NOTIFICATION_MANAGE`；录入 / 更换密钥用新权限（见 Q-03）；每次写操作记审计，密钥只记"已更换"及操作人，不记摘要以外的任何值。
11. **Vault 策略**：新增 `platform-support-service / PROTECT / NOTIFICATION_PROVIDER_CREDENTIAL_V1` 规则（用途见 Q-06）；需要一份 Vault 边界决定记录，策略按环境单独授权执行。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 运行环境 IAM 角色 / AWS 默认凭据链 | 用户决定不支持；只用 Vault 中的密钥 |
| 查看密钥原文、Reveal 到页面 | 只写不读 |
| 旧密钥在 Vault 中解除引用（Retire）或销毁 | Vault V1 无 Retire；换密钥后旧 Token 不再被引用，密文保留（与项目"不自动销毁"一致） |
| SMS、其他邮件服务商 | 类型字段预留，本期只有 Amazon SES |
| 保存时在线校验发件身份（调 SES `GetEmailIdentity`） | 需要额外 IAM 权限；用测试发送验证（见 Q-05） |
| 删除账号 | 只停用（同通知管理 Q-04） |
| 修改终端覆盖、超时、环境总开关 | 仍是部署配置 |
| 为 mor-test 配置真实 SES 账号 | 需要用户提供真实账号与密钥，另行处理 |

## 5. 验收标准

**功能验收**

| 编号 | 验收标准 |
|---|---|
| AC-01 | 有维护权限的账号可新建发送账号（编码唯一、创建后不可改、默认停用），编辑区域、发件地址、Configuration Set；并发修改返回冲突不覆盖 |
| AC-02 | 有凭据权限的账号可录入或更换密钥；无该权限时页面不显示入口，直接调接口返回 403 |
| AC-03 | 录入的密钥以 `NOTIFICATION_PROVIDER_CREDENTIAL_V1` 存入 Vault，账号表只存 Token；接口响应、页面、审计、BFF / Platform / Gateway 日志、异常、补偿 Payload 中都不出现 Access Key、Secret 原文或 Token |
| AC-04 | Vault Protect 失败时账号的旧 Token 不变；数据库事务内不调用 Vault 或 SES |
| AC-05 | 区域、发件地址、密钥任一缺失时不能启用；列表正确显示「配置完整」与密钥状态 |
| AC-06 | 修改账号配置或更换密钥后，下一次发送（普通通知与认证邮件）使用新配置，无需重启；多个账号可同时启用，主备路由按各自配置发送 |
| AC-07 | 发送端不再有默认凭据链分支；账号缺 Token 时发送失败关闭（不调用 SES，按现有规则重试 / 记录） |
| AC-08 | 测试发送用指定账号向指定邮箱发一封固定内容的邮件并返回结果码，不生成发送记录，记审计（若 Q-04 纳入） |
| AC-09 | 部署配置删除账号级字段后 Platform 正常启动；本机初始化 DML 与 mor-test 迁移 SQL 把现有 `SES_PRIMARY` 的字段补齐（密钥 Token 为空，需在页面录入） |
| AC-10 | 每个写操作产生 Platform 审计（操作人、对象、动作、结果）；密钥更换只记"已更换" |
| AC-11 | Platform、BFF、Web 自动化测试覆盖主流程与失败分支（含 Vault 不可用、SES 拒绝凭据），既有通知测试全部通过，覆盖率不低于既有水平 |

**视觉 / 交互验收**

| 编号 | 验收标准 |
|---|---|
| AC-12 | 发送账号页增加「新建账号」，行内「编辑」「更换密钥」「测试发送」，风格与通知模板页一致 |
| AC-13 | 密钥输入框为密码样式，提交后清空；弹窗说明"保存后无法查看，只能更换" |
| AC-14 | 编辑被启用路由引用的账号、停用账号时二次确认并列出受影响模板；错误就地显示，冲突用横幅 |

## 6. 问题与结论（2026-10-15 已确认：全部按建议）

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 密钥怎么进 Vault？ | **页面录入，Platform 代为 Protect**（用户已同意）；对应接口走 metadata-only 日志 |
| Q-02 | 账号级配置是否全部从部署配置移到数据库？ | **是**；保留 `enabled` 总开关、测试终端覆盖与超时；删除"Token 为空走默认凭据链" |
| Q-03 | 录入 / 更换密钥是否用单独权限？ | **单独权限** `NOTIFICATION_CREDENTIAL_MANAGE`，默认只授 PLATFORM_ADMIN；新建、编辑仍用 `NOTIFICATION_MANAGE` |
| Q-04 | 测试发送纳入本期吗？ | **纳入**：管理员填收件邮箱，固定标题正文，同步发送，不进发送记录，只记审计（收件邮箱不记原文） |
| Q-05 | 保存时要不要在线校验发件身份？ | **不校验**，用测试发送验证；避免给 IAM 用户增加 `GetEmailIdentity` 权限 |
| Q-06 | Platform Protect 用什么 Vault 用途？ | 沿用现有 `NOTIFICATION_PROVIDER_SETUP`（之前给运营手动加密用的），Caller 增加 `platform-support-service` |
| Q-07 | 换密钥后旧 Token 怎么处理？ | **不处理**：Vault V1 无 Retire，旧 Token 不再被引用，密文保留；审计记录更换时间 |
| Q-08 | 现有 `SES_PRIMARY` 如何迁移？ | 本机与 mor-test 用 DML 补区域 `me-central-1` 和发件地址（取当前部署配置值），Token 为空，页面录入后才能发信；mor-test 当前本就发不出，不影响现状 |
| Q-09 | 编辑范围 | 允许本会话修改 Platform、`operations-console-bff`、`operations-console-web` 中通知相关文件，共享文件只做增量修改；Vault 侧只改策略数据与决定文档，不改 Vault 代码 |
| Q-10 | 适用阶段 | 1—5 照常（阶段 3 含 Pencil 画板）；6—7 test 部署需要时再提；8 提交单独授权 |

## 7. 依据

- [通知管理](../operations-console-通知管理/requirement.md)（Q-02）、[SES 接入](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)（第 76—81 行密钥决定）
- [Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)：V1 只有 Protect / Reveal，新增 Caller 权限需单独决定
- [Platform 读取渠道账号配置决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)：同类"Platform 增加 Vault 权限"的决定格式
- Platform Schema：`t_notification_provider`；配置：`platform-support-web` `application.yml` 的 `notification.ses.*`、`web.logging.metadata-only-path-prefixes`
