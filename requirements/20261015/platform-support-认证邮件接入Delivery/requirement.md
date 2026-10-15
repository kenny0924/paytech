# Platform Support 认证邮件接入 Delivery 需求

> 状态：阶段 1—5 已完成，已提交（未推送，2026-10-15）。方案见 [technique.md](./technique.md)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)约定 OTP、邮箱验证、密码重置和普通通知共用通知能力，但"认证邮件对接后续处理"（Q-01），并要求认证临时秘密的瞬时传参、发送时机和恢复边界单独细化。[SES 接入](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)后，普通通知已经能经 Amazon SES 真实发信。

认证邮件目前仍走另一条独立链路：

| 现状 | 位置 |
|---|---|
| Client 注册验证、重新发送验证、密码重置三个 Processor 在本地事务提交后调用 `AuthenticationEmailSender.send` | `platform-support-access-control` 的 `RegisterClientProcessor`、`RequestClientEmailVerificationProcessor`、`RequestClientPasswordResetProcessor` |
| 唯一实现 `SmtpAuthenticationEmailSender`：Spring `JavaMailSender` 直连 SMTP，主题和正文写死在代码里，只有中文 | `platform-support-web/.../integration/mail/` |
| 发件人、站点 Origin 来自 `client.authentication.mail-from`、`client.authentication.public-origin`；SMTP 地址和账号来自 `spring.mail.*` | `ClientAuthenticationConfiguration`、`application.yml` |
| 发送失败只计指标，仍返回同样的受理响应；用户重发会产生新链接并让旧链接失效；不保存秘密，不做后台重投 | [注册入驻数据设计第 5 节](../../20260914/client-console-注册与客户入驻/data-design.md#5-邮箱链接会话与文件) |
| Operations 认证不发邮件，不在范围内 | — |

结果是：部署时要为认证邮件另配一套 SMTP 账号和凭据（`spring.mail.password` 不经 Vault），发件账号、域名验证和发送指标与普通通知分成两套。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Client 注册/找回密码的用户 | 照常收到验证和重置邮件，链接行为不变 |
| Platform Access Control | 继续负责生成、校验、失效和消费 Challenge；只把"发一封含临时链接的邮件"交给 Delivery |
| Platform Delivery | 用同一套模板、路由、发送账号和 Provider（SES）发送认证邮件；临时秘密只在内存中使用 |
| 运维/部署 | 只维护一套邮件发送账号（SES 凭据在 Vault），不再为认证另配 SMTP |

## 3. 本期范围

1. **Delivery 新增"认证邮件即时发送"入口**：模块内同步调用，输入为模板编码、收件邮箱、语言和临时参数（链接）。Delivery 按现有规则解析模板、语言、路由和发送账号，经 Vault 读取账号凭据，渲染后调用 Provider 立即发送一次。
2. **临时秘密不落地**：链接、正文、收件人不写入通知表、补偿事件、普通日志、审计和错误正文；这条路径不建 Request/Message 记录，不排队，不重试（见 Q-01）。
3. **Access Control 改接 Delivery**：`AuthenticationEmailSender` 端口保留，新实现委托给 Delivery 入口；三个 Processor 的调用时机、失败处理和响应不变。
4. **模板化**：验证邮件和重置邮件的主题、正文改为 Delivery 模板（见 Q-02），链接作为临时参数传入；站点 Origin 校验和 `#challengeNo=…&secret=…` 链接拼装规则保持不变，仍由认证侧负责。
5. **移除 SMTP 链路**（见 Q-03）：删除 `SmtpAuthenticationEmailSender`、`spring-boot-starter-mail` 依赖、`spring.mail.*` 和 `client.authentication.mail-from` 配置；发件人改由 Delivery 发送账号决定。
6. **测试与文档**：从注册、重发验证、重置三个 HTTP 入口跑真实链路，SES 用测试 HTTP 端点返回 DTO；同步 Delivery 规划、注册入驻设计、Platform README 和当前台账。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 认证邮件后台重投、发送记录查询 | 与现有"失败可重发、不保存秘密"的约定一致 |
| OTP 验证码、SMS | 当前没有使用方 |
| Operations 认证邮件 | Operations 目前不发邮件 |
| 改变 Challenge 有效期、冷却、链接格式、受理响应 | 认证语义不变 |
| 执行 SQL、部署、真实 SES 联调 | 初始化 DML 写入 SQL 文件但不执行；真实发信沿用"先不管" |

## 5. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | 注册、重新发送验证、密码重置三个入口成功后，SES 测试端点收到一次发送请求：收件人、语言、主题和含正确站点与路径的链接符合模板；HTTP 响应与改动前一致 |
| AC-02 | 认证邮件不产生 `t_notification_request`、`t_notification_message` 或 `compensation_event` 记录；链接、秘密、邮箱不出现在数据库、普通日志和错误正文中 |
| AC-03 | 模板、路由、发送账号缺失或停用，Vault 不可用，SES 返回错误或超时：三个入口仍返回原受理响应，账号与 Challenge 照常提交，只增加失败指标；用户重发能产生新链接 |
| AC-04 | 普通通知的提交、发送、HTTP 接口行为不变（现有测试全部通过） |
| AC-05 | 生产代码和依赖中不再有 SMTP 发送链路；`spring.mail.*`、`client.authentication.mail-from` 配置删除，`client.authentication.public-origin` 校验保留 |
| AC-06 | 两个认证模板（中/英文，见 Q-02）写入 Platform 初始化 DML 文件，测试数据与之一致；SQL 不执行 |
| AC-07 | 全服务 `./mvnw -o verify` 测试通过；Delivery 与 Access Control 相关覆盖率不低于既有水平 |
| AC-08 | Delivery 规划 Q-01、注册入驻数据设计第 5 节、Platform README、当前台账同步更新 |

## 6. 问题与结论（2026-10-15 已确认）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q-01 | 认证邮件要不要在 Delivery 留发送记录？ | **暂不留**，和现在一样只计指标 |
| Q-02 | 邮件内容放模板还是写在代码里？ | **放模板**：`CLIENT_EMAIL_VERIFICATION`、`CLIENT_PASSWORD_RESET`，本期只提供 `zh-CN`，语言固定 `zh-CN` |
| Q-03 | SMTP 删除还是保留备用？ | **删除** |
| Q-04 | 用哪个路由/发送账号？ | 按建议：复用普通通知的路由规则和同一个 SES 发送账号，不单独设发件地址 |
| Q-05 | 适用阶段 | 按建议：2+3 合并；4、5 照常；6–7 不适用；8 提交单独授权 |

## 7. 依据

- [Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)（Q-01、认证秘密边界）与[技术方案](../../20260920/platform-support-Delivery领域规划/technique.md)
- [注册入驻数据设计第 5 节](../../20260914/client-console-注册与客户入驻/data-design.md#5-邮箱链接会话与文件)：秘密不进数据库、失败可重发、不做后台重投
- [SES 接入](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)：Provider、发送账号与 Vault 凭据
