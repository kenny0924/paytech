# Platform Support 认证邮件接入 Delivery 技术方案

> 状态：阶段 2+3 已确认，阶段 4 已实现（2026-10-15）；实现偏差见第 8 节。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。

## 1. 现状差异

| 方面 | 现状 | 目标 |
|---|---|---|
| 发送通道 | `SmtpAuthenticationEmailSender` → `JavaMailSender` → SMTP；SMTP 密码在 `spring.mail.password`，不经 Vault | 经 Delivery 的路由 → 发送账号 → `EmailProvider`（SES），凭据由 `SesEmailProvider.prepare()` 从 Vault 读取 |
| 内容 | 主题、正文写死在适配器，只有中文 | Delivery 模板 `CLIENT_EMAIL_VERIFICATION`、`CLIENT_PASSWORD_RESET`，`zh-CN`；链接以 `${link}` 传入 |
| 发件人 | `client.authentication.mail-from` | SES 发送账号配置 `notification.ses.from-address`（与普通通知相同） |
| 持久化 | 无 | 仍然无：不写通知表、补偿事件 |
| 失败处理 | `MailException` → 计 `failed` 指标 → 抛 `ServiceUnavailableException`，Processor 吞掉后返回原受理响应 | 不变；失败来源扩大到模板/路由/账号/Vault/SES，统一转为 `ServiceUnavailableException` |
| 启用条件 | `client.authentication.enabled=true` + `spring.mail.*` | `client.authentication.enabled=true` 要求 `notification.enabled=true`（启动时校验） |
| 模板用途隔离 | 不涉及 | 普通通知与即时发送的模板互不通用（见 3.3） |
| 测试 | `SmtpMockServer`（本地 SMTP），只有注册入口有测试 | `SesMocker`（现有 SES 测试 HTTP 端点）；注册、重发验证、密码重置三个入口都有测试 |

已核对：Operations 认证不发邮件；`AuthenticationEmailSender` 只有上述三个 Processor 调用，都在本地事务提交后调用并捕获 `ServiceUnavailableException`。

## 2. 主流程

```
Access Control Processor（事务已提交）
  └─ AuthenticationEmailSender.send(email, purpose, link)          ← 端口不变
       DeliveryAuthenticationEmailSender（web/integration/mail）
         1. 按 purpose 选模板编码，拼链接 origin + path + #challengeNo=…&secret=…（规则不变）
         2. 调 SendTransientEmailProcessor.process(templateCode, email, "zh-CN", {link})
         3. 结果 ACCEPTED → 计 accepted；其余结果或任何 RuntimeException → 计 failed(reason) → 抛 ServiceUnavailableException
  Delivery SendTransientEmailProcessor（无事务，无写库）
         a. 校验：邮箱规范化、语言、临时参数有界；模板编码必须在即时发送模板集合内
         b. 读模板、语言内容、路由、主发送账号，状态都须 ACTIVE；找到唯一适配器
         c. 渲染主题和正文（缺参数即失败）
         d. provider.prepare()：事务外从 Vault 读取账号凭据
         e. sender.send(...) 只发一次，不重试、不切备用账号，返回结果分类
```

外部调用（Vault、SES）都在事务外；本流程没有事务。

## 3. 合同与设计要点

### 3.1 Delivery 模块入口

`SendTransientEmailProcessor extends AbstractProcessor<SendTransientEmailRequest, SendTransientEmailResponse>`，`@ConditionalOnProperty(notification.enabled)`，位于 `delivery/service/processor`。

| 请求字段 | 规则 |
|---|---|
| `templateCode` | 非空，`[A-Z][A-Z0-9_]{0,63}`；必须在 `notification.transient-template-codes` 中 |
| `email` | 与普通通知相同的 `NotificationInput.canonicalEmail` |
| `locale` | `NotificationInput.locale` |
| `parameters` | 1–8 个键，键 `[A-Za-z][A-Za-z0-9_]{0,63}`，值非空、≤ 2048 字符；**不做**普通参数的保留字检查（那是"不许落库"的防线，这里本来就不落库），也不序列化成 JSON |

请求类 `toString` 只输出模板编码和语言。响应 `SendTransientEmailResponse` 只有 `outcome`（`EmailProviderResult.Outcome`）和有界的 `code`，不含邮箱、正文或 MessageId 以外的信息；本入口不返回 MessageId（没有消费方）。

失败语义：配置不可用、模板不在集合内、缺语言/参数 → `BizCheckFailException`；无适配器、Vault 不可用 → 现有 `ServiceUnavailableException` / Vault 依赖异常；SES 的拒绝或未知 → 正常返回对应 `outcome`。

请求号：`provider.requestNo(ids.nextId())`，例如 SES 为 `ses-<id>`，只作为 SES EmailTag 发出，不落库。

### 3.2 配置检查复用

`SubmitNotificationProcessor.validateConfiguration / validateProvider` 与即时发送的检查相同（模板 EMAIL+ACTIVE、路由 ACTIVE、账号 EMAIL+ACTIVE、唯一适配器）。抽成 Delivery 内部组件 `NotificationConfigurationResolver`（`delivery/service`，接口 + Impl，只读仓储），两个 Processor 共用；错误文字保持现状，普通通知现有测试不变。即时发送只取主账号，不使用备用账号。

### 3.3 模板用途隔离

新增 `notification.transient-template-codes`（`NotificationProperties`，默认空列表；编码格式同上、不可重复，否则启动失败）：

- 即时发送只接受集合内的模板；
- `SubmitNotificationProcessor` 拒绝集合内的模板（`BizCheckFailException("该模板只用于即时发送")`），防止普通通知调用方借认证模板发带任意链接的邮件；
- `NotificationCallerProperties` 启动校验：HTTP 白名单里出现集合内模板 → 启动失败。

`application.yml` 默认值为两个认证模板编码。不加 DDL。

### 3.4 Access Control 适配器

`platform-support-web/.../integration/mail/DeliveryAuthenticationEmailSender implements AuthenticationEmailSender`，替换 `SmtpAuthenticationEmailSender`：

- 站点 Origin 校验（HTTPS 或本机 http）原样保留；
- `SIGN_UP_EMAIL → CLIENT_EMAIL_VERIFICATION`，`/verify-email`；其余 → `CLIENT_PASSWORD_RESET`，`/reset-password`；
- 语言固定 `zh-CN`（常量）；
- 指标沿用 `client.authentication.email.delivery`，`result=accepted|failed`，失败加有界 `reason`：`CONFIGURATION`（模板/路由/账号/参数）、`DEPENDENCY`（适配器/Vault）、`REJECTED`、`UNKNOWN`；
- 不记录日志；捕获异常时不把原异常交给日志或向上抛出原因链（与现 SMTP 适配器一致）。

`ClientAuthenticationConfiguration`：构造器检查 `notification.enabled=true`，否则启动失败并提示；Bean 改为注入 `SendTransientEmailProcessor`；删除 `JavaMailSender`、`mail-from`。

### 3.5 删除 SMTP

| 删除 | 位置 |
|---|---|
| `SmtpAuthenticationEmailSender` | web `integration/mail` |
| `spring-boot-starter-mail` 依赖 | `platform-support-web/pom.xml` |
| `spring.mail.*` | `application.yml`、`application-mock.yml` |
| `client.authentication.mail-from` | `application-mock.yml`、README |
| `management.health.mail` | `application-mock.yml` |
| `SmtpMockServer` 及其注册、全局 BeforeEach/AfterEach 钩子 | test 模块 |

## 4. 模板与初始化 DML

在 `docs/database/sql/platform-support-initial-data.sql` 末尾追加独立的"认证邮件"块（可单独执行，已初始化的环境只执行该块）。本期**只写文件不执行**。

| 表 | 行 |
|---|---|
| `t_notification_provider` | `SES_PRIMARY`，EMAIL，ACTIVE（部署配置 `NOTIFICATION_SES_PROVIDER_CODE=SES_PRIMARY`） |
| `t_notification_template` | `CLIENT_EMAIL_VERIFICATION`、`CLIENT_PASSWORD_RESET`，EMAIL，ACTIVE |
| `t_notification_template_content` | 两个模板各一行 `zh-CN` |
| `t_notification_route` | 两个模板各一条，主账号 `SES_PRIMARY`，无备用 |

主键使用 `900000000000070001` 起的保留段。内容沿用现有文字：

| 模板 | 主题 | 正文 |
|---|---|---|
| `CLIENT_EMAIL_VERIFICATION` | `PAYTECH · 验证您的邮箱` | `验证您的邮箱\n\n请打开以下链接，并在页面中确认操作：\n${link}\n\n如果不是您发起的请求，请忽略此邮件。` |
| `CLIENT_PASSWORD_RESET` | `PAYTECH · 重置登录密码` | 同上，首行为"重置登录密码" |

## 5. 测试计划

入口与 Mock：真实 HTTP 入口 → Access Control Processor → 新适配器 → `SendTransientEmailProcessor` → 真实 `SesEmailProvider`（SDK + SigV4）→ 现有 SES 测试 HTTP 端点（`SesMocker`）；Vault 走现有 `NotificationVaultMock`。测试 Fixture 新增两个认证模板，路由指向测试 SES 账号。

| 用例（入口） | 断言 |
|---|---|
| 注册 `test_normalFlow` | 202；SES 收到 1 次：收件人、主题、正文含 `https://client.example.test/verify-email#challengeNo=` 的链接，发件人为 SES 配置；通知三张表与补偿事件无新增 |
| 重新发送验证 `test_normalFlow` | 同上；旧 Challenge 失效 |
| 密码重置 `test_normalFlow` | 链接指向 `/reset-password` |
| 注册 `test_emailDeliveryFailure` | SES 拒绝、SES 断连/超时、Vault 不可用、模板停用：仍 202，账号和 Challenge 已提交，`failed` 指标按 reason 计数，无重试请求 |
| 日志与错误正文 | `CapturedOutput` 不含邮箱、`secret=` 和链接 |
| 普通通知提交认证模板 | 400 `BUSINESS_CHECK_FAILED`，不调用 Vault、不落库 |
| 配置 | 即时模板集合非法/重复 → 启动失败；HTTP 白名单含即时模板 → 启动失败；Client 认证开启而通知关闭 → 启动失败 |

即时发送入口的其他分支（缺语言、缺参数、参数越界、模板不在集合内）在 `SendTransientEmailProcessor` 公开入口上补少量用例。现有普通通知和 HTTP 接口测试全部保留。

## 6. 代码落点

| 模块 | 改动 |
|---|---|
| delivery | 新增 `SendTransientEmailProcessor` 及 request/response；新增 `NotificationConfigurationResolver`(+Impl)；`SubmitNotificationProcessor` 改用 Resolver 并拒绝即时模板；`NotificationProperties` 加 `transientTemplateCodes`；`NotificationInput` 加临时参数校验 |
| web | 新增 `DeliveryAuthenticationEmailSender`；删除 SMTP 适配器和依赖；`ClientAuthenticationConfiguration`、`NotificationCallerProperties` 校验；`application.yml` |
| test | 删除 `SmtpMockServer`；Fixture 加认证模板；改写 `ClientRegistrationTest`，新增重发验证、密码重置、即时发送测试 |
| 文档/SQL | 初始化 DML 追加块；Delivery 规划 Q-01、注册入驻数据设计第 5 节、Platform README、测试 README、当前台账 |

## 7. 风险与取舍

| 风险 | 处理 |
|---|---|
| SES 会在其服务端保留发出的邮件内容（含链接） | 与 SMTP 服务商相同，属于邮件通道固有边界；链接有效期 20/30 分钟，单次消费 |
| 认证邮件不切备用账号 | 与"只发一次、失败用户重发"一致；明确拒绝时切备用虽安全，但当前没有备用账号，暂不做 |
| Client 认证与通知强绑定 | Client 认证本就需要发信；启动校验让配置缺失立即暴露 |
| 认证邮件依赖数据库模板 | 模板缺失按配置失败计指标，用户仍得到受理响应；上线检查清单要求先执行初始化 DML |
| 即时模板集合是配置而不是表字段 | 不做 DDL；集合与 DML 不一致时，即时发送报配置失败，普通通知也拿不到该模板，失败方向是安全的 |

## 8. 实现偏差（阶段 4）

| 方案 | 实际 | 原因 |
|---|---|---|
| 3.1：SES 的拒绝或未知正常返回 `outcome` | 不变；另外 Provider 抛出的异常不在 Processor 内捕获，原样抛给调用方 | 认证适配器已统一捕获 RuntimeException 计 `DEPENDENCY`；去掉一层重复且无法测试的兜底 |
| 3.1：结果缺失按未知 | 只对"缺分类/缺结果码/结果码不合法"按 `UNKNOWN`；Provider 返回 null 不再兜底 | 合同不允许返回 null，属程序错误，抛出即可 |
| 5：测试计划 | 另加 `DeliveryAuthenticationEmailSenderTest`（Origin 校验与送达/无法送达映射，经测试 Provider）、`ClientAuthenticationEmailConfigurationTest`（通知关闭时启动失败）；`SesMocker` 的意外请求检查移到全局 AfterEach（取代原 SMTP 全局检查） | SES 不会返回送达类结果；认证邮件可从任意测试类触发 |
| 4：DML | 只写文件，未在任何数据库执行，也未做语法试跑 | SQL 执行需单独授权；测试 Fixture 使用同样的主题与正文 |
