# Platform 统一人工身份认证与租户授权设计

日期：2026-08-30

状态：已逐项确认的认证设计并已反映在当前生成模型；Java、Migration 与认证运行态仍未实施

> **2026-09-01 公共控制面后继：** 本文继续拥有 Account、Realm、人工 Channel、Password、Google OIDC、Session 与 Tenant IAM 语义；其中“Platform 不拥有 Route/Guard 当前配置”的旧结论已由 [统一公共 API 动态控制面设计](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 及其 [PDManer 实施计划](../plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 窄幅取代。57 表精简阶段是未受影响领域的直接前序；当前模型为 63 表 / 822 字段 / 123 唯一索引 / 99 访问索引 / 107 关系 / 5 个 1:1 / 7 图组 / 31 复合 FK / 85 CHECK / 36 个 `_hash` 字段。Gateway 动态 Snapshot 尚未切换，静态 Operations Catalog 只是切换前运行态。

本文取代 agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md 中“只服务
Operations、Client/Partner 认证留待未来”的范围限制，并取代当前 Platform Support 92 表模型中受影响的
外部 IdP-only、无本地登录 Session、动态 Gateway Route 发布和 Guard 跨维度覆盖语义。旧文档未受影响的
Platform Support 能力继续有效。

本文不取代 docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md 的 Header/Cookie、JWT/JWKS、
Redis、传输与运行时硬保护边界。其静态 Operations Route Catalog 只在动态 Snapshot 切换前继续生效；公共 Route
当前配置所有权以后继动态设计为准。

## 1. 目的

在不新增第七个核心系统的前提下，为三个后台渠道建立统一、可审计、可撤销且租户隔离的人工身份体系：

- Operations 只允许本地账号密码登录；普通账号的登录标识只能是 Email；
- Client Console 与 Partner Console 支持 Email 注册/密码登录和 Google OIDC 登录；
- 一个 CUSTOMER 账号是全局登录身份，可同时加入多个 Client/Partner Tenant；
- 每个租户成员关系独立授权，一个 Access Token 只绑定一个当前 Tenant；
- Platform Support 拥有认证、Session、Token 签发和 Tenant IAM，CCT 继续拥有 Client、Local Partner、Party、
  Application 与业务资格；
- 新增 Local Partner 入驻申请，并用 Typed Source Namespace 接入 Risk 尽调；
- 删除 Platform 数据库对 Gateway 动态 Route 的错误所有权，只保留对预部署 Route Code 的受控保护策略。

## 2. 目标与非目标

### 2.1 目标

- Operations 与外部 CUSTOMER Realm 物理、密码、Session、签名密钥和 Token Issuer 隔离。
- Email/password 与 Google 登录最终映射到同一个全局 CUSTOMER Account，而不是各自创建租户用户。
- Google 账号绑定不会凭相同 Email 静默接管已有账号。
- Email 注册在验证所有权前不能获得业务访问能力。
- Refresh Token 一次性轮换，检测旧 Token 重用后撤销整个 Session Family。
- Client/Partner 租户创建、邀请、成员和角色授权有清晰的跨库边界与幂等契约。
- 密码、Token、OIDC Secret、私钥和完整 Email 不进入日志、MQ 或普通审计载荷。
- 生成后的 Platform、CCT、Risk 模型具有精确统计、关系、CHECK、场景和自动门禁。

### 2.2 非目标

- 不实现 MFA、Passkey、SAML、LDAP、企业域自动加入或社交账号自动合并。
- MVP 不启用 Microsoft、Apple 或其他 IdP；模型只为未来受控增加 Provider 留出稳定边界。
- 不支持 Consumer 登录，也不改变机器 API Credential。
- 不把 Platform Support 变成通用公共 IdP，不允许第三方任意注册 OIDC Client。
- 不在 Gateway、BFF 或 CCT 保存密码、Refresh Session、签名私钥或角色事实。
- 不让一个 Token 同时携带多个 Tenant，也不把全部 Role/Permission 固化进 JWT。
- 不建立通用登录历史、通用 Auth Workflow、通用 Identity Attribute EAV 或 JSON Credential 表。
- 本设计阶段不创建 Java 模块、Migration、物理数据库、KMS/Secret、BFF、前端或部署配置。

## 3. 固定术语与身份分区

### 3.1 Realm

Realm 是不可配置的闭合枚举，不建立通用 Realm 表：

| Realm | 使用者 | 登录方式 | Tenant 类型 | Token Issuer/Key Ring |
|---|---|---|---|---|
| OPERATIONS | 平台内部人员 | Password only | INTERNAL | Operations 专属 |
| CUSTOMER | Client/Local Partner 人员 | Password、Google OIDC | CLIENT、PARTNER | Customer 专属 |

同一 Email 可在 OPERATIONS 和 CUSTOMER 各有一个独立账号。两个账号不共享 Account ID、密码、OIDC
Binding、Session、Token、失败次数或授权。Realm 不能由请求参数跨界切换；外部 Route、BFF 和 Audience 固定
目标 Realm。

### 3.2 Channel 与 Audience

| Channel | Audience | 允许 Realm | 允许上下文 |
|---|---|---|---|
| OPERATIONS | operations-console | OPERATIONS | INTERNAL Tenant；Bootstrap 限制上下文 |
| CLIENT | client-console | CUSTOMER | Account 或 CLIENT Tenant |
| PARTNER | partner-console | CUSTOMER | Account 或 PARTNER Tenant |

Client 和 Partner 使用不同的外部 Route、BFF、Origin、Cookie 名和 Audience。一个 CUSTOMER Account 可在两个
Channel 分别建立 Session，但 Client Token 不可用于 Partner BFF，反之亦然。

### 3.3 Account、Member 与业务主体

- Account 是 Platform Support 全局登录身份。
- Tenant Member 是 Account 在一个 Access Tenant 下的独立授权关系。
- Client、Local Partner、Party、Legal Signatory 是 CCT 业务事实，不能等同于 Account 或 Member。
- requested_by_account_id 只证明哪一个已认证 Account 发起申请，不证明其是法律主体、UBO、Director 或
  Signatory。

## 4. 系统与应用边界

```text
Browser
  -> API Gateway
    -> channel-specific BFF
      -> Platform Support Auth/IAM
      -> CCT onboarding/business APIs

Platform Support Auth/IAM
  -> Vault (login Email Value Token and authorized display/reveal)
  -> Google OIDC (authorization-code exchange and claim validation)
  -> KMS/Secret Manager (password pepper where used, HMAC keys, OIDC secret, JWT private key)

CCT local transaction
  -> CCT compensation_event
    -> after-commit Platform internal command

CCT Application
  -> after-commit Risk typed due-diligence submission
```

职责固定如下：

- Gateway：精确静态路由、基础 JWT/JWKS 验证、网络级 Rate/Concurrency、可信 Header、Request ID 和访问日志；
  不处理密码、Google Code、Refresh 状态或业务授权。
- BFF：固定 Channel/Audience，适配页面请求，处理 Origin、CSRF、HttpOnly Cookie 和 OIDC Redirect/Callback
  传输；不保存认证事实、不访问 Platform 数据库、不签发 Token。
- Platform Support：Account、Password Verifier、OIDC Binding、Challenge、Session、Refresh Rotation、签名
  Key Metadata、Tenant/Member/Role/Permission 的唯一权威。
- CCT：Client、Local Partner、Party、Application、资料、资格和业务状态的唯一权威。
- Risk：尽调 Case、Qualification Decision 和关联人 KYC 的唯一权威。
- 核心业务系统：继续校验资源所有权和业务状态；Platform Permission 不能替代领域不变量。

任一系统只能读写自己的数据库。跨系统稳定 ID 不建立物理外键。

## 5. 登录能力矩阵

| 账号类型 | 标识 | Password | Google | 自助注册 | 备注 |
|---|---|---:|---:|---:|---|
| Bootstrap Admin | 固定 admin | 是 | 否 | 否 | 仅初始化首个 Email PLATFORM_ADMIN |
| Operations Human | Email | 是 | 否 | 否 | 由 PLATFORM_ADMIN 创建 |
| CUSTOMER Human | Email | 可选 | 可选 | 是 | 至少保留一种 ACTIVE 登录方式 |

CUSTOMER Account 可以先由 Password 注册，也可以先由 Google 创建。Google-only Account 没有空 Password
Credential 行；用户完成当前登录方式的重新认证后可以设置 Password。Password Account 可以在先验证现有
Password 后绑定 Google。至少一种 ACTIVE 登录方式约束适用于 ACTIVE Human Account；已永久禁用的 Bootstrap
和 CLOSED Human Account 可以没有活动方式。

## 6. Bootstrap Admin

Bootstrap 是唯一允许非 Email 登录名的账号，固定为 admin，且只存在于 OPERATIONS Realm。

### 6.1 初始化

1. 全新数据库初始化器从部署 Secret 读取一次性高熵密码。
2. 只有数据库从未出现 Bootstrap Account 时，才创建 BOOTSTRAP_ADMIN Account 与 Argon2id Credential。
3. 已存在的 DISABLED Bootstrap 行是永久完成标记；重启或更换 Secret 不能重新启用或重建它。
4. Secret 不写入数据库、配置文件、日志、审计或部署输出；完成初始化后必须从运行环境移除。

### 6.2 受限生命周期

1. admin 使用初始化密码登录，只获得 Bootstrap 限制上下文。
2. 第一次成功登录必须先修改 Bootstrap 密码；修改前只能调用修改密码和退出。
3. 修改完成后，只能创建第一个 Email Operations Account，并原子授予固定 INTERNAL Tenant 下的
   PLATFORM_ADMIN 系统角色。
4. 创建事务同时永久 DISABLE Bootstrap Account、撤销其全部 Session，并使其 Password Credential 失效。
5. 第一个 Email Admin 获得一次性初始密码，首次登录同样必须修改密码，之后才可进入普通 Operations 功能。

数据库约束保证 BOOTSTRAP_ADMIN 只能是 OPERATIONS + admin + 无 Email；服务事务保证它只能创建首个
PLATFORM_ADMIN，不能创建业务 Tenant、调用核心业务接口或给自身增加其他权限。

## 7. Account 与 Email

### 7.1 t_user_account

该表取代 t_user_identity 作为人工登录身份根，关键字段为：

- account_id；
- realm：OPERATIONS/CUSTOMER；
- account_type：HUMAN/BOOTSTRAP_ADMIN；
- login_name：只允许 Bootstrap 的 admin；
- email_vault_token、email_masked、email_verified_time：Human 成组使用；
- status：PENDING_VERIFICATION/ACTIVE/DISABLED/CLOSED；
- consecutive_password_failures、locked_until、last_password_failure_time；
- credential_version、auth_epoch、version；
- create_time、update_time。

关键约束：

- uk_user_account_email(realm,email_vault_token) 保证 Realm 内 Email 唯一；
- uk_user_account_login(realm,login_name) 保证 admin 唯一；
- BOOTSTRAP_ADMIN 必须为 OPERATIONS、login_name=admin、Email 三列全空；
- HUMAN 必须 login_name 为空且 Email Token/Masked 非空；
- CUSTOMER Password 注册初始为 PENDING_VERIFICATION；Google 新建账号可直接为 ACTIVE，因为 Google
  必须给出已验证 Email；
- locked_until 是安全临时锁，不把 Account 生命周期改成 LOCKED；到期后可重新尝试；
- CLOSED 不可恢复，DISABLED 只能由授权 Operations 管理或安全处置恢复。

### 7.2 Email 规范化和 Vault

- 输入先做 Unicode/域名安全校验，再按平台 Email Canonicalization V1 生成大小写不敏感的规范值。
- 不执行 Gmail dot folding、加号标签剥离或 Provider 特有重写。
- Platform 调用 Vault 的稳定 Value Token Purpose 固定为 PLATFORM_LOGIN_EMAIL_V1；相同规范 Email 返回同一
  不可枚举 Token，供 Realm 内等值查询和唯一约束。
- 数据库不保存明文 Email、普通 SHA-256、无密钥 Blind Index 或可逆密文。
- Masked Email 只用于受控展示；完整 Email 的发送/Reveal 仍需 Workload、Purpose、Resource 与审计授权。
- Google 后续返回不同 Email 时不自动修改 Account Email；登录身份继续以 Provider Subject 为准。平台 Email
  变更必须走已认证的独立 EMAIL_CHANGE Challenge。

## 8. Password Credential 与策略

### 8.1 t_password_credential

每个 Account 至多一行当前 Password Credential，关键字段为：

- password_credential_id、account_id；
- password_verifier_phc；
- algorithm_code=ARGON2ID、algorithm_version；
- memory_kib、iterations、parallelism；
- credential_version、force_change_flag；
- status=ACTIVE/REVOKED；
- password_changed_time、create_time、update_time、version。

uk_password_account(account_id) 保证 1:0..1。Verifier 使用标准 PHC 字符串并与显式参数交叉校验；不保存
明文、可逆密文、找回材料或旧 Verifier 历史。Google-only Account 不创建占位行。

创建、修改、重置 Password 必须在同一事务更新 Credential 与 Account.credential_version。Password Reset、
Email Change、Account Disable、确认泄露和强制安全处置还必须递增 auth_epoch 并撤销 Account 全部 Session。

### 8.2 密码规则

- 允许 15 至 128 个 Unicode 字符；比较和存储前做 NFC，不静默裁剪空格。
- 请求解析另设 512 UTF-8 bytes 硬上限，防止异常编码消耗资源。
- 不要求大小写、数字、符号组合，不周期性强制过期，允许粘贴和密码管理器。
- 拒绝常见密码、已泄露密码和包含当前 Email/平台名等明显上下文的密码。
- MVP Argon2id 最低参数为 19 MiB memory、2 iterations、parallelism=1；部署基准测试可向上校准，不能低于
  此下限。参数升级在下次成功 Password 验证后原子重算。
- Operations 连续 5 次 Password 失败锁定 30 分钟；CUSTOMER 连续 10 次失败锁定 15 分钟。成功 Password
  验证原子清零计数。
- Login/Reset 同时应用 Account、规范 Email、Client IP 和设备/请求摘要维度的限流与反枚举响应；Gateway
  网络限制不能替代 Platform Account 锁定。

## 9. Google OIDC 与账号绑定

### 9.1 t_oidc_provider

MVP 只允许一条 GOOGLE Provider，且只服务 CUSTOMER Realm。表保存 provider_code、realm、issuer、client_id、
discovery/jwks 公共元数据、status、config_version 和 version；Client Secret 只在 Secret Manager，通过部署引用
提供给 Platform Auth 运行时，不进入该表、Vault、日志或 BFF。

未来增加 Microsoft 或 Apple 必须新增受审 Provider 和场景，不得通过自由 issuer、自助 Client 注册或请求参数
动态接入。MVP UI 只展示 Google。

### 9.2 t_federated_identity

关键字段为 federated_identity_id、account_id、oidc_provider_id、provider_subject、binding_method、status、
bound_time、revoked_time、version。

- uk_federated_provider_subject(oidc_provider_id,provider_subject) 防止一个 Google Subject 绑定多个 Account；
- uk_federated_account_provider(account_id,oidc_provider_id) 保证一个 Account 对一个 Provider 至多一个 Binding；
- Revoke 保留 Subject 历史并置为 REVOKED，不硬删除后允许静默重绑；
- 不复制 Google Access Token、Refresh Token、ID Token、完整 Profile 或任意 Claims JSON。

### 9.3 授权码流程

使用 OIDC Authorization Code Flow、PKCE、state 和 nonce：

1. Browser 从 Client 或 Partner BFF 发起 Google 登录。
2. Platform 生成受签名、短时、一次性语义的 state/nonce/PKCE 上下文；BFF 只转发 Authorization URL，并把
   Platform 返回的密封临时上下文写入对应 Channel 的 Secure、HttpOnly、SameSite=Strict 临时 Cookie。
3. Google 回调到同一 Channel BFF；BFF 将 code、state 和密封上下文原样交给 Platform，不自行换 Token。
4. Platform 校验 state、nonce、PKCE、issuer、audience、签名、时间和 email_verified=true，再用 sub 定位
   Federated Identity。
5. 临时上下文过期、重放、Channel 不符或任一验证失败都失败关闭；Google Token 和 Code 不落库。

### 9.4 新建、登录与安全绑定

- 已有 ACTIVE provider_subject：登录其 Account；Google Email 变化不改平台 Email。
- 新 Subject + 新的已验证 Email：创建 ACTIVE CUSTOMER Account 和 Binding。
- 新 Subject + 已存在 CUSTOMER Email：绝不自动绑定。用户必须先用已有 Password 完成重新认证，然后在同一
  短时流程绑定 Google。
- 已有 Account 没有 Password 且只有另一个 Google Subject 时，不能凭相同 Email 接管；用户必须通过仍有效的
  原登录方式设置 Password，或进入人工恢复流程。MVP 不创建自动人工合并功能。
- Password Account 绑定 Google 前必须重新验证 Password；Google Account 设置 Password 前必须重新完成当前
  Google 登录。
- 解绑或撤销登录方式的事务必须锁定 Account，并验证操作后至少仍有一个 ACTIVE Password 或 Federated
  Identity；最后一种方式不可删除。

当 Google Callback 命中“Subject 未绑定但 Email 已存在”时，Platform 只返回一个短时、Audience/Channel
绑定且由 Platform 密封的 Pending Binding Proof。Proof 只含 Provider、Subject、目标 Account、过期时间和随机
JTI，不含 Google Token；BFF 把它保存在对应 Channel 的临时 HttpOnly Cookie。用户完成目标 Account 的 Password
重新认证后，Platform 才原子消费该语义、创建 Binding。唯一索引和命令幂等保证 Proof 重放只能返回既有绑定，
不能创建第二条关系。Google-only Account 设置 Password 同样要求本 Session 的近期 Google 重新认证，而不是仅凭
一个仍未过期的普通 Access Token。

重复 Callback 以 Provider Subject、密封上下文和命令幂等键返回原 Session 结果或明确识别已消费，不能创建
第二个 Account/Binding/Session。

## 10. Authentication Challenge

现有 t_email_otp_challenge 继续只服务业务 Email OTP，不用于注册、Password Reset、Email Change 或登录。
认证使用独立的 t_authentication_challenge。

### 10.1 结构

关键字段为 challenge_id、challenge_no、realm、purpose、account_id、tenant_invitation_id、
email_vault_token、email_masked、active_scope_hash、secret_hmac、hmac_key_version、expires_time、
max_attempts、used_attempts、status、active_slot、consumed_time、supersedes_challenge_id、version。

purpose 仅允许：

- SIGN_UP_EMAIL；
- PASSWORD_RESET；
- EMAIL_CHANGE；
- TENANT_INVITATION_ACCEPT。

Secret 是至少 256-bit CSPRNG 值。数据库仅保存 HMAC-SHA-256(KMS key, challenge_id || purpose ||
active_scope_hash || secret)，链接只返回一次明文。SIGN_UP_EMAIL/PASSWORD_RESET/EMAIL_CHANGE 有效期 30 分钟，
TENANT_INVITATION_ACCEPT 的 Challenge 不得晚于 Invitation 自身到期。每类 Scope 只有一条 ACTIVE slot；Resend
创建新 Challenge 并原子 SUPERSEDE 旧行。status 只允许 PENDING/CONSUMED/EXPIRED/SUPERSEDED/REVOKED，只有
PENDING 写 active_slot=1；max_attempts 固定为 5。

### 10.2 Purpose 矩阵

| Purpose | Account | Invitation | Email | 消费副作用 |
|---|---|---|---|---|
| SIGN_UP_EMAIL | PENDING CUSTOMER 必填 | 空 | 等于 Account Email | 验证并激活 Account |
| PASSWORD_RESET | Human Account 必填 | 空 | 等于 Account Email | 替换 Password、提升版本/Epoch、撤销 Session |
| EMAIL_CHANGE | ACTIVE Human 必填 | 空 | 新 Email | 更新 Token/Masked、验证时间、提升 Epoch |
| TENANT_INVITATION_ACCEPT | 可在消费时解析 | 必填 | 等于 Invitation Email | 创建/恢复 Member 并授予初始 Role |

Public Reset 请求无论账号是否存在都返回相同响应；不存在账号时不创建可观察 Challenge。尝试次数、过期、消费
和安全事件均不保存输入 Secret。PASSWORD_RESET 只替换已经存在的 ACTIVE Password Credential；Google-only
Account 必须先用 Google 重新认证后设置 Password，不能用 Reset 绕过现有登录方式。

## 11. Session、Refresh 与签名密钥

### 11.1 t_auth_session

关键字段为 session_id、account_id、realm、channel、context_type、tenant_id、tenant_member_id、
authentication_method、account_auth_epoch、credential_version_snapshot、issued_time、last_reauthenticated_time、
last_activity_time、idle_expire_time、absolute_expire_time、current_generation_no、status、revocation_reason、
revoked_time、version。

- context_type=ACCOUNT 时 tenant_id/member_id 都为空；只允许账号管理、邀请接受和入驻申请。
- context_type=TENANT 时 tenant_id/member_id 都非空，Member 必须 ACTIVE，Tenant 类型必须匹配 Channel。
- OPERATIONS 普通 Session 只能绑定 INTERNAL Tenant；Bootstrap 使用受限 Bootstrap 上下文，不能伪装普通 Tenant。
- authentication_method 只允许 PASSWORD/GOOGLE；GOOGLE 仅 CUSTOMER。
- Account auth_epoch 或 Credential Version 与快照不一致时 Refresh 失败并撤销 Session。
- ACTIVE/REVOKED/EXPIRED/COMPROMISED 状态通过 Version 条件更新，终态不可恢复。

Session 有效期固定：

| Realm/Channel | Absolute | Idle | Remember me |
|---|---:|---:|---|
| OPERATIONS | 8 小时 | 30 分钟 | 不支持 |
| CLIENT/PARTNER | 7 天 | 24 小时 | 不支持 |

每次成功 Refresh 可推进 last_activity_time 和 idle_expire_time，但不能超过 absolute_expire_time。

### 11.2 t_refresh_token_generation

Refresh Cookie 的不透明值由公开 lookup_id 与 256-bit 随机 secret 两段组成。表保存
refresh_generation_id、session_id、generation_no、token_lookup_id、token_secret_hmac、hmac_key_version、
issued_time、expires_time、status、active_slot、consumed_time、replaced_by_generation_id、version。

- uk_refresh_lookup(token_lookup_id)；
- uk_refresh_session_generation(session_id,generation_no)；
- uk_refresh_active(session_id,active_slot)，只有当前 ISSUED 行 slot=1；
- Secret 仅保存 HMAC，不保存明文或普通 Hash；
- Refresh 在锁定 Session 和当前 Generation 后，原子将旧行置 CONSUMED、清 slot、插入下一 Generation、更新
  Session current_generation_no，再签发 Access Token；
- 已 CONSUMED Generation 的 lookup_id + 正确 HMAC 再出现即判定 Token 重用，把 Session 置 COMPROMISED 并
  撤销整个 Family；错误 Secret 只记录失败，不泄露是否存在；
- 浏览器必须对每个 Channel 实现单飞 Refresh。并发使用同一 Token 时只允许一笔成功，另一笔按重用处置，
  不能返回两个有效后继 Token。

### 11.3 Cookie

| Channel | Cookie | Exact Path |
|---|---|---|
| OPERATIONS | __Secure-operations-refresh | /api/operations/auth/v1 |
| CLIENT | __Secure-client-refresh | /api/client/auth/v1 |
| PARTNER | __Secure-partner-refresh | /api/partner/auth/v1 |

全部固定 Secure、HttpOnly、SameSite=Strict、不设置 Domain，Max-Age 不超过 Session 剩余 Absolute 时间。Access
Token 只保存在浏览器内存，不写 Local Storage、Session Storage、Cookie、URL 或页面持久化状态。BFF 负责精确
Origin、Credentials 和 CSRF；Platform 负责 Refresh Secret 与 Session 状态。

### 11.4 Access Token

- OPERATIONS 与 CUSTOMER 使用不同 HTTPS issuer、KMS Key Ring 和 JWKS；算法固定 RS256，Header 必须有 ACTIVE
  kid。
- 当前 Operations 本地 issuer 固定为 `https://platform-support.local.test`，生产 issuer 必须由环境提供 HTTPS
  标识；未来 CUSTOMER issuer 必须与 Operations 不同。issuer 是 Token 身份标识，不要求内部 JWKS 传输使用 TLS。
- 有效期最长 15 分钟，Audience 必须按当前 Channel 精确映射：`OPERATIONS -> operations-console`、
  `CLIENT -> client-console`、`PARTNER -> partner-console`，不得把 Channel Code 本身当作 Audience。
- 必需 Claims：iss、sub、aud、token_use=ACCESS、sid、jti、realm、channel、actor_type、context_type、iat、nbf、
  exp。
- Tenant Context 额外包含 tenant_id、tenant_type、tenant_member_id；Account Context 不得包含这些 Claims。
- 不包含 Email、全部 Tenant 列表、Role、Permission、Google Subject 或业务主体 Profile。
- Gateway 只校验签名、时效、issuer、audience、realm/channel/actor_type 和最小 Claim 矩阵；当前 Account/Member/
  Tenant/Role/Permission 仍由 BFF 调 Platform 授权接口并由领域系统复核资源。

### 11.5 t_jwt_signing_key

关键字段为 signing_key_id、realm、kid、algorithm=RS256、kms_key_ref、public_jwk_schema_version、
public_jwk_canonical_json、status、not_before、signing_end_time、verification_end_time、version；没有可推导且无消费方的
`public_jwk_hash`。

uk_jwt_signing_realm_kid(realm,kid) 保证 Realm 内唯一。状态只允许
PREPARED -> ACTIVE -> RETIRING -> RETIRED；每个 Realm 同时最多一个 ACTIVE 签名 Key。旧 Key 在所有已签 Access
Token 的最长生命周期结束前保留在 JWKS；数据库只保存公钥和非秘密 KMS 引用，永不保存私钥材料。

当前 PLT-I22 通过私有 HTTP `GET /platform-support/authentication/v1/realms/OPERATIONS/jwks` 发布 Operations
完整公钥集合，不使用 ETag 或应用证书。响应只允许唯一 ACTIVE 与仍有效 RETIRING Key，最多 16 把/64 KiB；每把
必须是 RS256、RSA-2048+、`use=sig`、`key_ops=[verify]`、合法稳定 `kid` 和规范无填充 Base64URLUInt `n/e`，
任何未知/私钥字段或生命周期不一致都 Fail-Closed。Client 与 Partner 未来共用 CUSTOMER JWKS/Key Ring，但继续使用
不同 Audience/Channel；本期不发布 CUSTOMER endpoint。

## 12. Tenant、Member、Role 与 Invitation

### 12.1 t_access_tenant

保留该表但拆分 CCT 业务状态与 Platform 安全状态，关键字段为 tenant_id、tenant_type、owner_ref_type、
owner_ref_id、tenant_name、owner_status、source_version、source_payload_hash、security_status、version。

- tenant_type：CLIENT/PARTNER/INTERNAL；
- owner_ref_type：CLIENT/LOCAL_PARTNER/PLATFORM；
- CLIENT 必须绑定 CCT Client，PARTNER 必须绑定 CCT Local Partner，INTERNAL 只能绑定 Platform；
- uk_access_tenant(tenant_type,owner_ref_id)；
- owner_status 由 CCT 投影 PENDING/ACTIVE/SUSPENDED/TERMINATED；INTERNAL 使用 ACTIVE；
- security_status 只由 Platform 控制 ACTIVE/SUSPENDED；
- 有效访问要求 owner_status=ACTIVE 且 security_status=ACTIVE，任一不满足都失败关闭；
- CCT 的后续 ACTIVE 事件不能覆盖 Platform security_status=SUSPENDED。

### 12.2 t_tenant_member

将旧 user_id 改为 account_id，关键字段为 member_id、tenant_id、account_id、member_type、status、invited_by、
version。member_type 只允许 OWNER/ADMIN/MEMBER，状态只允许 ACTIVE/SUSPENDED/REVOKED。

- uk_tenant_member(tenant_id,account_id)；
- OPERATIONS Account 只能加入 INTERNAL Tenant；CUSTOMER Account 只能加入 CLIENT/PARTNER Tenant；
- 一个 CUSTOMER Account 可同时拥有多个 Client 和 Partner Member；
- Member 状态独立，暂停一个 Tenant 不暂停其他 Tenant 或全局 Account；
- OWNER 只表达门户管理责任，不推导 CCT Party 或法定 Signatory。

### 12.3 Role/Permission

t_role、t_permission、t_tenant_member_role、t_role_permission 延续当前复合 FK、Scope、有效期、系统角色不可删除和
授权审计规则。Token 不携带它们；授权查询按当前 Member、Tenant、Role/Permission 和资源条件计算。

系统至少提供：

- INTERNAL 的 PLATFORM_ADMIN；
- CLIENT/PARTNER 的 TENANT_ONBOARDING_OWNER；
- CLIENT/PARTNER 的 TENANT_OWNER。

PENDING Tenant 的 Onboarding Owner 只能管理 Account、申请资料和入驻状态。CCT 投影 ACTIVE 后，Platform 在同一
本地事务撤销 Onboarding Role 并授予 Tenant Owner；SUSPENDED/TERMINATED 不删除历史授权，但授权决策失败关闭。

### 12.4 t_tenant_invitation

关键字段为 invitation_id、invitation_no、tenant_id、invited_email_vault_token、invited_email_masked、
member_type、initial_role_id、invited_by_member_id、expires_time、status、active_slot、accepted_account_id、
accepted_member_id、accepted_time、version。

- status：PENDING/ACCEPTED/EXPIRED/REVOKED/SUPERSEDED；
- uk_tenant_invitation_no(invitation_no)；
- uk_tenant_invitation_active(tenant_id,invited_email_vault_token,active_slot) 保证同 Tenant+Email 只有一条活跃邀请；
- initial_role_id 必须是同 Tenant Scope 的 ACTIVE Role；一个邀请只授予一个初始 Role，不增加 invitation_role 子表；
- 邀请接受链接使用独立 Authentication Challenge；Invitation 本身不保存 Secret；
- 接受时 Account Email Token 必须等于邀请 Token，Channel 必须匹配 Tenant 类型；
- 一个事务创建或恢复 Tenant Member、授予初始 Member Role、消费 Challenge、完成 Invitation；
- 已有 Member 时按当前状态和邀请意图幂等返回或明确拒绝，不能创建重复 Member。

## 13. CCT Client/Partner 入驻与 Risk

### 13.1 Account 注册与业务 Tenant 创建分离

CUSTOMER 注册只创建 Platform Account。创建 Client/Partner 的 Account Context 命令随后调用 CCT：

- Client 入驻复用 t_client_application ONBOARDING；
- Local Partner 入驻新增 t_partner_application；
- CCT 在本地事务创建 PENDING 业务 Role、冻结 Target Profile/Application，并记录 requested_by_account_id；
- Platform Account ID 是外部稳定引用，无跨库 FK，也不赋予 CCT 法律身份。

### 13.2 t_partner_application

首期只支持 Local Partner ONBOARDING，不预建通用 Partner Profile Change。关键字段为 application_id、
application_no、local_partner_id、target_party_profile_id、requested_by_account_id、status、active_slot、
due_diligence_case_id、qualification_decision_id、qualification_decision_version、
qualification_type=PARTNER_DUE_DILIGENCE、submitted_time、submission_hash_schema_version、
submission_content_hash、create_by、create_time、update_time。

状态为 DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED/REJECTED/WITHDRAWN。创建申请的同一事务创建稳定 PENDING
Local Partner、Organization Party 和 Target Party Profile；拒绝后的重提复用该 PENDING Role，不能创建第二个
Local Partner ID。进行中 Application 使用 active_slot 保证每个 Local Partner 最多一条。

t_client_application 增加 requested_by_account_id；ONBOARDING 必填，历史/Operations 发起的 Profile Change 可按
明确 Actor Contract 填写，但不得用它替代 create_by 或 CCT Actor 审计。

### 13.3 t_application_document

增加 partner_application_id，并把父级约束从二选一改为三选一：

```text
exactly one of
  client_application_id
  partner_application_id
  operating_entity_application_id
```

三个父列都是真实 CCT RESTRICT FK。subject_party_id 仍必须属于主 Party 或其受审关联人图；提交后文件引用和
Content Hash 不可覆盖。

### 13.4 CCT -> Risk Typed Namespace

Risk 的 source_namespace 增加 CCT_LOCAL_PARTNER_APPLICATION。APPLICATION Case 唯一身份保持：

```text
source_namespace
  + source_application_id
  + role_type
  + role_id
  + qualification_type
```

Namespace 矩阵：

| Namespace | 主 Role/Qualification | 允许关联人 |
|---|---|---|
| CCT_CLIENT_APPLICATION | CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB | RELATED_PERSON + RELATED_PERSON_KYC |
| CCT_OPERATING_ENTITY_APPLICATION | OPERATING_ENTITY + ENTITY_UNDERWRITING | RELATED_PERSON + RELATED_PERSON_KYC |
| CCT_LOCAL_PARTNER_APPLICATION | LOCAL_PARTNER + PARTNER_DUE_DILIGENCE | RELATED_PERSON + RELATED_PERSON_KYC |
| RISK_COMPLIANCE | Continuous Review/Manual 自有来源 | 按既有规则 |

CCT Handler 携带 application_id、role_type、role_id、qualification_type、submission_content_hash。Risk 用完整五元
身份定位 Case，并以 Hash 区分幂等重放和冲突提交。CCT 只回填主 Role Case/Decision，关联人 KYC 子 Case 由 Risk
内部依赖关系管理。

### 13.5 CCT -> Platform Tenant 投影与入驻文件

CCT-I15 将入驻拆成两个明确命令。`START_OR_RESUME` 在 Account Context 创建或复用 PENDING Client/Partner 与
DRAFT Application，冻结 `requested_by_account_id`；该本地事务只登记 Platform compensation_event，不调用 Risk。
After-commit Handler 调用 Platform 内部幂等命令：

```text
ProvisionAccessTenant(
  tenant_type,
  owner_ref_id,
  requested_by_account_id,
  source_version,
  payload_hash
)
```

Platform 在一个事务创建 PENDING Access Tenant、OWNER Member 和 TENANT_ONBOARDING_OWNER Role。完整命令身份是
`tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash`。后续 CCT 状态变化使用同一
Source Identity 发送版本化更新；Platform 只接受单调递增 `source_version` 和匹配 `payload_hash`。

同幂等键同 Hash 返回既有结果；同键不同 Hash 进入 CONFLICT。Status Update 早于 Provision 到达时标记可重试，
不能凭空创建缺 requested_by_account_id 的 Tenant。BFF 不双写两个数据库。

Application 仍处于 PENDING 流程、已 REJECTED/WITHDRAWN，或 CCT Role 已 SUSPENDED/TERMINATED 时，都不签发
普通 Tenant Context Token；Application 拒绝/撤回不会擅自把可重提的 PENDING CCT Role 改成 TERMINATED。
Account Context 仍可按 `requested_by_account_id` 查询自己的申请结果。

入驻材料不使用无 Tenant 的 staging 表，也不允许浏览器指定 Tenant ID。Client/Partner BFF 在 Account Context
调用 PLT-I04；Platform 必须以已认证 Account 唯一解析同一 PENDING Tenant 下 ACTIVE Member 与
`TENANT_ONBOARDING_OWNER`，再把 File 写入解析出的本地 `tenant_id`。零个、多个或状态不一致均 Fail-Closed。
CCT-I15 `SUBMIT` 校验并冻结全部受控 File ID 与 Profile/材料快照后，才登记 Risk compensation_event。

只有 CCT APPROVED 且 Role ACTIVE 后才允许普通 Tenant 业务权限。激活投影必须撤销
`TENANT_ONBOARDING_OWNER` 并授予 `TENANT_OWNER`，不能把临时入驻角色带入日常 Tenant Context。

## 14. Gateway 数据库边界清理（97 表历史阶段）

动态 Snapshot 切换前，当前 Gateway 运行时继续使用代码内静态 Route Catalog。以下删除 Route、保留 Policy/Release
的列表只记录 97 表认证阶段，已被 57 表精简前序及当前 63 表六表控制面后继取代，不得作为当前建模要求。该阶段曾要求：

- 删除 t_gateway_route 和 t_gateway_route_ver；
- t_gateway_policy_binding_ver 以非空 route_code 引用部署中预先存在的精确 Route Code，不保存 Route ID、Path、
  Method、Upstream、runtime_route_ref 或 Route Version；
- Gateway 在应用 Release 前用本地静态 Catalog 校验 route_code 存在，并校验所有参数不超过代码硬上限；
- t_gateway_release_item 的 item_type 只允许 POLICY/BINDING，删除 Route Version FK 和 ROUTE 类型；
- 保留 Policy、Policy Version、Binding/Version、Selector、Maker-Checker Release、State Change 和 Instance Ack；
- 此数据库设计本身不授权当前 Gateway 接入动态 Platform Release；首次接入仍需单独实现与安全评审。

t_gateway_guard_rule 增加 counter_dimension：

- SHARED_ROUTE；
- CLIENT_IP；
- AUTHENTICATED_SUBJECT。

RATE_LIMIT 和 CONCURRENCY 必须指定 Dimension；其他 Rule Type 的 Dimension 为空。唯一约束改为 Policy Version +
Rule Type + Dimension，允许同一 Policy 同时定义三个独立容量边界。所有匹配维度累计执行：任一 Rate Bucket
拒绝即拒绝；所有 Concurrency Lease 都成功才进入下游，结束时逐一幂等释放。高 Tier 只能收紧或在批准边界内
覆盖同 Dimension 的值，不能用 Subject 规则消除 Shared Route/IP 基线。

Token Bucket Counter、Concurrency Lease、Fencing 和 TTL 继续只在 Redis；MySQL 不保存运行计数。

## 15. 事务、锁序与并发仲裁

### 15.1 统一锁序

同一 Platform 本地事务按以下顺序获取行锁：

```text
Account
  -> Password Credential / Federated Identity / Authentication Challenge
  -> Access Tenant
  -> Tenant Member / Invitation / Member Role
  -> Auth Session
  -> Refresh Token Generation
```

不存在的根先依靠唯一索引尝试 INSERT，再读取冲突行；不得通过反向锁序重试规避死锁。

### 15.2 关键并发规则

- 两个 Email 注册：Realm+Email Vault Token 唯一索引裁决，只产生一个 Account。
- 两个 Google Callback：Provider+Subject 与 Account+Provider 两个唯一索引共同裁决，只产生一个 Binding。
- Password 修改/Reset 与 Refresh：先提升 auth_epoch/credential_version 并撤销 Session；旧快照不能刷新。
- Challenge Resend/Consume：active_scope_hash+active_slot 裁决；只能一个消费成功。
- Invitation Accept：锁 Invitation、Tenant、Account/Member；只能创建一个 Member 和一份初始授权。
- Tenant 状态投影：按 source_version 条件更新；旧版本忽略，同版本不同 Hash 冲突，跳版本失败并重试缺失事件。
- Refresh：Session+Active Generation 锁定；只产生一个下一代，旧代正确重放撤销 Family。
- Signing Key 激活：Realm Active Slot 裁决；新 Key 激活和旧 Key 进入 RETIRING 同事务完成。

## 16. 失败模式、审计与敏感信息

### 16.1 失败关闭

| 依赖/异常 | 行为 |
|---|---|
| Vault 不可用或 Email Token 不一致 | 注册、登录 Email 定位、邀请和 Email Change 失败关闭 |
| Google/JWKS/Token Exchange 不可用 | Google 登录失败，不回退成 Email 自动绑定 |
| KMS/Signing Key 不可用 | 不签发 Access/Refresh 结果，不使用旧私钥缓存越过状态 |
| Platform Auth/IAM 不可用 | BFF 不自行认证或授权 |
| CCT 状态缺失/过期 | 不签发 Tenant Context Token |
| Redis Guard 不可用 | 按 Gateway Hardening 规则失败关闭 |
| Source Version/Hash 冲突 | 进入可审计 CONFLICT，不最后写覆盖 |

### 16.2 外部错误

Login、Reset、Invite 查询和 Google 绑定使用稳定的通用错误，不泄露 Email 是否存在、账号登录方式、Tenant
成员关系、锁定精确原因或 Google Subject。HTTP 状态与 Retry-After 由固定错误目录决定；内部 Security Event 保存
精确原因码和 Request ID。

### 16.3 复用统一审计

不新增 login_history 表。现有 audit_event/security_event 记录至少：

- Login/Logout/Refresh 成功与失败；
- Account Create/Verify/Disable/Enable/Close；
- Password Create/Change/Reset/Rehash；
- Google Bind/Revoke/Conflict；
- Challenge Create/Supersede/Consume/Expire；
- Session Revoke/Expire/Reuse Compromise；
- Invitation Create/Revoke/Accept；
- Tenant/Member/Role/Permission 变化；
- Signing Key Prepare/Activate/Retire。

事件只保存 Account/Tenant/Member/Session/Provider 等稳定引用、结果、Reason Code、Request ID、时间和批准的
IP/User-Agent 不可逆摘要。以下内容永不进入数据库普通 Payload、日志、Trace、Metric、MQ、文件或错误信息：

- 明文 Password、Password Verifier；
- Access/Refresh Token、Challenge Secret、Authorization Code；
- OIDC Client Secret、Google Token/完整 Claims；
- JWT 私钥、HMAC/Pepper Key；
- 完整 Email、Cookie、Authorization Header 或认证请求 Body。

## 17. 端到端主流程

### 17.1 Operations Login

1. Gateway 只接受静态 Operations Login Route 并执行 IP/Route Guard。
2. Operations BFF 固定 Realm/Channel 调 Platform Password Login。
3. Platform 用 Email Token 或 Bootstrap login_name 定位 Account，执行锁定和 Argon2id 验证。
4. 强制改密账号只获得受限上下文；普通账号选择固定 INTERNAL Tenant Member。
5. Platform 创建 Session+Generation，签发 operations-console Access Token；BFF 写 Operations Cookie。
6. 后续授权读取当前 Platform Role/Permission，核心系统继续校验业务不变量。

### 17.2 CUSTOMER Email 注册

1. Client/Partner BFF 提交 Email+Password，Channel 不决定 Account 所属 Tenant。
2. Platform 规范化 Email、取得 Vault Token、检查唯一性和 Password 策略。
3. 创建 PENDING_VERIFICATION Account、Password Credential 和 SIGN_UP_EMAIL Challenge。
4. 通知只携带受限的一次性链接 Secret，持久层只保留 HMAC。
5. 消费 Challenge 后 Account ACTIVE；用户登录取得 Channel 对应 Account Context Session。

### 17.3 Google 新建与已有账号绑定

1. Platform 完成 Code/PKCE/state/nonce 和 Google Claims 验证。
2. provider_subject 已绑定则直接登录。
3. Subject 未绑定且 Email 不存在则创建 ACTIVE Account+Binding。
4. Email 已存在则返回需要现有方式重新认证的通用结果，不创建 Binding。
5. Password 重新认证成功后，在同一短时命令绑定 Google，再创建 Session。

### 17.4 创建 Client/Partner Tenant

1. ACTIVE CUSTOMER Account 使用 Account Context 提交 CCT Onboarding。
2. CCT 创建 PENDING Role/Application，并写 ProvisionAccessTenant Compensation Event。
3. Platform 幂等创建 PENDING Tenant、OWNER Member 和 Onboarding Role。
4. CCT/Risk 完成主 Case 与关联人 Case，CCT 决定 APPROVED/REJECTED。
5. CCT 版本化投影到 Platform；只有 ACTIVE+security ACTIVE 才可换取 Tenant Context Token。

### 17.5 邀请成员

1. 当前 Tenant Admin 创建邀请，Platform 冻结 Email Token、Member Type 和一个 Initial Role。
2. 被邀请者登录或注册全局 CUSTOMER Account。
3. 接受 Challenge 时校验 Account Email Token、Tenant 类型和 Channel。
4. 单事务创建 Member、Member Role 并消费 Invitation/Challenge。
5. 新 Session 或显式 Tenant Switch 才签发绑定该 Member 的 Token。Tenant Switch 创建新的固定上下文 Session、
   撤销当前 Channel 的旧 Refresh Family 并替换 Cookie；旧 Access Token 只在原 Tenant 内残余有效至最多 15 分钟，
   不能跨 Tenant 使用。

### 17.6 Refresh 与重用

1. BFF 从精确 Channel Cookie 取 lookup_id.secret，调用 Platform。
2. Platform 校验 Session 时间、Realm/Channel、Account Epoch、Member/Tenant 当前状态和 Generation HMAC。
3. 原子消费旧 Generation、创建新 Generation、推进 Idle 时间并签发新 Access Token。
4. BFF 原子替换 Cookie；前端单飞更新内存 Access Token。
5. 旧 Token 再现则 Session Family 进入 COMPROMISED，Cookie 被清除，要求重新登录。

## 18. 被拒绝的替代方案

- Operations 接 Google：违反内部账号密码唯一入口和 Realm 风险隔离。
- Client/Partner 各建一套 Account：会把同一自然用户复制为多个登录身份，破坏多租户关系。
- Email 相同即自动绑定 Google：可把已有 Password Account 交给新 Provider Subject，存在账号接管风险。
- 复用业务 Email OTP：业务 OTP 的 Purpose、短码风险和数据生命周期不适合认证恢复。
- JWT 携带全部 Tenant/Role/Permission：授权变更无法及时生效，Token 体积和越权面扩大。
- Refresh Token 只做普通 Hash 或可逆加密：无法满足密钥化离线防护或最小 Secret 暴露。
- BFF 保存 Session/签名：会产生三个认证事实源并破坏 Channel Adapter 边界。
- CCT 保存账号/密码：混淆业务主体和登录身份，并形成跨系统授权数据库。
- 动态 Gateway Route 表：Platform 不拥有部署 Path/Method/Upstream，且会绕过静态安全 Catalog。
- 一个最高优先 Guard 规则覆盖所有维度：Subject 限制可能意外取消 Shared Route/IP 的容量基线。
- 预建通用 MFA、Provider Claim、Identity Attribute 或 Invitation Role 集合：没有当前消费者，增加敏感面和状态组合。

## 19. 验收与自动门禁

物理模型修订实施时必须覆盖：

### 19.1 Platform Auth/IAM

- Bootstrap 只能初始化一次、强制改密、只能创建首个 Email PLATFORM_ADMIN、随后永久禁用；
- Operations 普通账号拒绝 login_name 和 Google，只允许 Email+Password；
- CUSTOMER Email 注册、验证、Reset、Email Change 和反枚举；
- Google 新账号、已有 Subject、已有 Email 拒绝自动绑定、Password 重新认证绑定、Google-only 设置 Password；
- 至少一种 ACTIVE 登录方式约束；
- Realm 内 Email 唯一、跨 Realm 可重复；
- 多 Client/Partner Tenant、独立 Member、Invite 接受和 Role Scope；
- Account Context 与 Tenant Context Claim 矩阵，一个 Token 只有一个 Tenant；
- Refresh 单次轮换、并发刷新、旧 Token 重用、Epoch/Version 撤销、Idle/Absolute 到期；
- Realm/Audience/Channel/Cookie/Issuer/Key Ring 隔离；
- 签名 Key 状态、JWKS 重叠和数据库无私钥。

### 19.2 CCT/Risk

- Partner Application 创建稳定 PENDING Local Partner/Organization Party/Profile；
- Application 状态、active_slot、Submission Hash 和 Decision Tuple；
- Application Document 三父 XOR 与真实 FK；
- requested_by_account_id 仅作外部稳定引用；
- Provision/Status 事件的同键同 Hash 幂等、异 Hash 冲突、乱序重试；
- CCT_LOCAL_PARTNER_APPLICATION Namespace、LOCAL_PARTNER 主 Case 和 RELATED_PERSON 子 Case 不串案。

### 19.3 Gateway 与敏感信息

- 模型不存在 Gateway Route/Route Version 表和 ROUTE Release Item；
- Binding 只接受静态 Catalog Route Code；
- Rate/Concurrency 三维累计，Redis 运行状态不进入 MySQL；
- 全模型扫描禁止明文 Email、Password、Token、Authorization Code、Google Token、OIDC Secret、JWT 私钥和
  无边界 Claims JSON。

### 19.4 全仓验证

- Platform、CCT、Risk Generator 生成结果与 committed PDManer 一致，全部 --check 通过；
- 三系统 Model Policy、Oracle、Scenario Tests 覆盖新增/删除表、字段、索引、关系、CHECK 和枚举；
- 六系统 PDManer/Scenario 全量测试通过；
- requirements-site build、全部 tests 和 lint 通过；
- Markdown 相对链接、冲突标记、旧统计/旧语义扫描和 git diff --check 通过；
- 由生成器报告最终精确表/字段/唯一索引/访问索引/本库关系/1:1/图组/复合 FK/CHECK 统计，不预设目标数字；
- 六个模型仍保留 PDManer 4.9.3 应用内人工复核门禁。

## 20. 交付边界与文档同步

设计获准实施后，必须原子同步：

- Platform Support Manifest、Model Policy、Oracle、Generator、Tests、PDManer、逻辑数据库文档、物理决策和真实场景；
- CCT Partner Application Manifest/Model/Tests/PDManer/逻辑与场景文档；
- Risk Typed Namespace Manifest/Model/Tests/PDManer/逻辑与场景文档；
- requirements.ts、相关全局 Use Case Sequence、架构图、站点测试；
- AGENTS.md、README.md、agent-docs 导航、PROJECT_CONTEXT、DATABASE_DESIGN_GUIDE 和 database/README 的最新统计与权威链接。

不得用旧 worktree 整文件覆盖主工作区。共享文件必须基于合并时主线做语义增量，并与正在修改 Gateway、Risk、
Finance 或其他共享文档的任务协调。最终统计只能来自生成后的模型，不手工猜测。

本设计不授权 Java、Migration、数据库部署或云资源。完成上述设计资产并再次获得实现授权后，才可规划运行时
Platform Auth、BFF、前端、KMS/Secrets 和部署工作。

## 21. 参考安全标准

- [NIST SP 800-63B Digital Identity Guidelines](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OAuth 2.0 Security Best Current Practice, RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)

这些标准提供密码、Verifier、OAuth/OIDC 与 Token 防护基线；Code-MoR 的 Realm、Tenant、CCT/Risk 和 Gateway
边界仍以本文更严格的业务不变量为准。
