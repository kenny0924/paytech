# Platform Support 认证邮件接入 Delivery 交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 合并；已确认 |
| 4. 研发实现 | 已确认；初始化 DML 只写文件未执行 |
| 5. 综合验证与本地验收 | 已完成 |
| 6–7. test 发布准备与部署 | 不适用：本期不部署 |
| 8. 提交 | 已授权并提交（未推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | "先做规模小的"：在认证邮件迁移与通知状态查询中先做规模小的 | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | "1. 暂时不存吧 2. 放模版 3. 删掉 4. 按建议 5. 按建议"：不留发送记录；内容放模板（只做 zh-CN）；删除 SMTP；复用同一 SES 账号；阶段按建议 | 阶段 2+3 方案设计 |
| 2026-10-15 | 2+3 | "可以"：接受 technique.md 方案 | 阶段 4 研发（Platform 代码、配置、测试、文档；DML 只写不执行；不提交） |
| 2026-10-15 | 4 | "可以"：接受阶段 4 实现 | 阶段 5 综合验证（打包与制品检查、测试复核；不提交、不部署） |
| 2026-10-15 | 8 | "提交"：授权提交本需求改动 | 本地提交；不推送、不部署、不执行 SQL |

## 当前阶段与下一确认点

阶段 5 已完成，阶段 8 已提交到本地 main（未推送）。不部署、不执行 SQL。实现偏差见 [technique.md 第 8 节](./technique.md#8-实现偏差阶段-4)。

## 验收映射（阶段 4）

| AC | 实现 / 证据 | 状态 |
|---|---|---|
| AC-01 | `ClientRegistrationTest`、`ClientEmailVerificationRequestTest`、`ClientPasswordResetRequestTest` `test_normalFlow`：SES 测试端点收到 1 次，收件人、发件人、主题、`/verify-email` 或 `/reset-password` 链接正确；重置用例用邮件链接完成激活与重置；响应码不变 | 通过 |
| AC-02 | 上述用例与 `SendTransientEmailTest` 断言通知三表与补偿任务计数为 0；`CapturedOutput` 不含秘密与邮箱 | 通过 |
| AC-03 | `ClientRegistrationTest.test_emailDeliveryFailure`：SES 拒绝/断连、Vault 不可用、模板停用均 202，账号与挑战已提交，按 `REJECTED/UNKNOWN/DEPENDENCY/CONFIGURATION` 计指标，不重试 | 通过 |
| AC-04 | 普通通知与 HTTP 接口原有测试全部通过；`test_ordinaryNotificationRejectsTransientTemplate`、白名单含即时模板启动失败 | 通过 |
| AC-05 | 删除 `SmtpAuthenticationEmailSender`、`spring-boot-starter-mail`、`spring.mail.*`、`mail-from`、`SmtpMockServer`；Origin 校验保留并有用例 | 通过 |
| AC-06 | `docs/database/sql/platform-support-initial-data.sql` 末尾独立块：`SES_PRIMARY`、两模板、两条 `zh-CN` 内容、两条路由；Fixture 文案一致；**未执行** | 通过（文件） |
| AC-07 | 全服务 589/589；Delivery 指令 96.39%、分支 90.49%（改动前 96.30% / 90.34%）；`DeliveryAuthenticationEmailSender` 指令/分支 100% | 通过（模块） |
| AC-08 | Delivery 规划 requirement/technique/api 修订说明、注册入驻数据设计第 5 节、Platform README、测试 README、TECHNOLOGY_STACK 邮件行、当前台账 | 通过 |

## 测试证据

- 全量：JDK 25 `./mvnw -o verify` 测试 **589/589**（改动前 543）；全服务 90% 聚合门禁仍为既有失败（指令 0.52、分支 0.36，改动前 0.50 / 0.33），未降低门槛。日志 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/127a8113-989d-4dae-925f-99aec229b7de/scratchpad/platform-verify-authmail.log`（临时目录）。
- `npm --prefix requirements-site test`：128/137，9 项失败与改动前相同，与本需求无关。
- 未做：真实 SES 发信、SQL 执行。

## 阶段 5 综合验证（2026-10-15）

- `./mvnw -o clean verify`（JDK 25）：测试 589/589；构建以既有的全服务 90% 聚合门禁失败结束（指令 0.52、分支 0.36，与阶段 4 相同）。日志 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/127a8113-989d-4dae-925f-99aec229b7de/scratchpad/platform-stage5.log`（临时目录）。
- 新增/改写用例：`ClientRegistrationTest` 6、`ClientEmailVerificationRequestTest` 2、`ClientPasswordResetRequestTest` 2、`SendTransientEmailTest` 20、`DeliveryAuthenticationEmailSenderTest` 15、`ClientAuthenticationEmailConfigurationTest` 1、`NotificationPropertiesTest` 7、`NotificationCallerPropertiesTest` 8；普通通知原有用例（Submit、SES 投递、Handler、HTTP 接口）全部通过；无跳过。
- 覆盖率（JaCoCo exec 直接分析）：Delivery 指令 96.39%、分支 90.49%；`SendTransientEmailProcessor` 与 `DeliveryAuthenticationEmailSender` 指令/分支均 100%，`NotificationConfigurationServiceImpl` 指令 100%、分支 94.44%。
- 制品检查（`platform-support-web-1.0.0-SNAPSHOT-exec.jar`）：`BOOT-INF/lib` 无 `spring-boot-starter-mail`、`jakarta.mail`、`angus-mail`；无 `SmtpAuthenticationEmailSender`；含 `DeliveryAuthenticationEmailSender` 与 Delivery 新类；打包的 `application.yml` 无 `spring.mail`、`mail-from`，含 `transient-template-codes` 两项；`client.authentication.public-origin` 与改动前一样无默认值、部署时必填。
- 源码检索：Platform 的 Java/YAML/XML/properties 无 SMTP、`JavaMailSender`、`spring.mail`、`mail-from` 残留。
- 构建日志未出现测试链接秘密或 `secret=`。
- `npm --prefix requirements-site test`：128/137，9 项失败与改动前逐项相同。
- 部署时注意（本期不部署）：执行初始化 DML 中的独立块，并设置 `NOTIFICATION_SES_PROVIDER_CODE=SES_PRIMARY`；开启 `client.authentication.enabled` 时必须同时开启 `notification.enabled`。
