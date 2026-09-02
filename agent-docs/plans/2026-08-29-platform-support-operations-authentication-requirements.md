# Platform Support 内部运营认证需求

> **2026-09-01 当前读取规则：** 本文仍只记录 Operations-only 历史阶段。统一 Account/Tenant/Google 认证以 [统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 为准；Gateway 的 TLS、JWT、Header、Cookie、Redis 和传输安全以 [Operations Gateway hardening 设计](../../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md) 为准；57 表精简阶段是未受影响领域的直接前序，[统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 与其 [PDManer 实施计划](../../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 定义当前六表后继。当前模型为 63 表 / 822 字段 / 123 唯一索引 / 99 访问索引 / 107 关系 / 5 个 1:1 / 7 图组 / 31 复合 FK / 85 CHECK / 36 个 `_hash` 字段。

最后更新：2026-08-29

状态：**历史/已被替代。** 本文记录 Operations-only 的阶段性需求；[2026-08-30 统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)、[Operations Gateway hardening 设计](../../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md) 和当前 [63 表模型](../../platform-support-service/docs/database/platform-support.pdma.json) 已取代其认证与 Gateway 语义。本文本身不授权业务 API、Migration、物理数据库、Secrets/KMS、BFF、前端或部署实现。

> **历史读取规则：** 后文的 Operations-only 范围、92 表数字与“未来物理修订”均是当时决策记录，不能作为当前导航或实现依据。当前模型为 63 表、822 字段、123 个唯一索引、99 个访问索引、107 条关系、5 条 1:1、7 图组、31 条复合 FK、85 条 CHECK 和 36 个 `_hash` 字段；Business Email OTP 仍独立且不得复用为认证。

## 1. 决策

Platform Support 作为 Operations 渠道的唯一认证权威，自主管理内部 Operations 账号、不可逆密码校验值、可撤销 Refresh Session 和 Operations Token 签发。首期只支持账号密码登录，不接入外部 IdP，也不使用业务 Email OTP 代替登录或 MFA。

该能力只服务内部 `operations-console-*` 渠道，不把 Platform Support 建成面向 Client、Local Partner、Consumer 或第三方应用的通用公共 IdP。Client/Partner/Consumer 的后续认证方案不由本文决定。

唯一公网认证链路为：

```text
operations-console-web
  -> API Gateway
    -> operations-console-bff
      -> Platform Support 内部认证接口
```

Platform Support 不暴露浏览器或公网 Login/Refresh/Logout Endpoint。Gateway 只做 Token 验证；BFF 只做渠道固定、Cookie/Origin/CSRF 和传输适配。BFF 不校验或保存密码、不持久化 Session、不生成/签名 Token，也不成为角色权限事实源。

## 2. 账号生命周期

- 只有具有 `PLATFORM_ADMIN` 管理能力的内部人员可创建、禁用、启用、重置 Operations 账号；不提供公开注册。
- 新账号获得一次性初始密码，首次成功登录后必须修改密码，完成修改前不得进入普通运营功能。
- 密码只保存经过批准的自适应不可逆校验值；优先采用参数化、可升级的 Argon2id。记录算法、版本和参数以支持安全升级，不保存明文、可逆密文、Vault Token 或可用于恢复密码的材料。
- 管理员重置密码、账号禁用和安全处置必须撤销该账号的全部 Refresh Session，并立即阻止新的 Login/Refresh。
- 连续失败、锁定/解锁、凭据版本和认证 Epoch 必须是 Platform Support 的权威状态，不能只依赖 Gateway IP 限流。
- 密码策略、失败阈值、锁定时长和密码历史在物理实现设计时定稿；不得在 Gateway 中复制这些业务规则。

## 3. Token 与 Session

### 3.1 Access Token

- JWT 使用 `RS256`，Header 必须包含有效 `kid`；公钥通过受信任 HTTPS JWKS 发布。
- Access Token 最长 15 分钟，Audience 固定为 `operations-console`；签发必须满足 `iat <= nbf < exp` 和 `exp - iat <= 15 分钟`。
- 必需 Claim 为 `iss`、`sub`、`aud`、`token_use=ACCESS`、`channel=OPERATIONS`、`actor_type=PLATFORM_USER`、`sid`、`jti`、`iat`、`nbf` 和 `exp`。
- Token 不携带 PII，也不把 Role/Permission 嵌入为长期授权事实。当前角色、权限、账号状态和资源访问仍由 Platform Support/BFF 与业务拥有系统校验。

### 3.2 Refresh Session

- 一个登录工作日 Session 的绝对有效期最多 8 小时，不通过滚动 Refresh 延长到该绝对期限之外。
- Refresh Token 是高熵、不透明、一次性轮换的 Bearer Secret；Platform Support 只保存不可逆 Verifier、Session/Family、Rotation Generation、签发/到期/最后使用/撤销信息，不保存 Token 明文。
- 每次 Refresh 原子撤销旧 Token 并签发新 Token；检测到旧 Token 重用时撤销整个 Session Family 并记录 Security Event。
- Logout 至少撤销当前 Session；密码重置、账号禁用或确认的凭据泄露撤销账号全部 Session。
- 自包含 Access JWT 在到期前不承诺立即吊销。若未来需要逐请求在线 Revocation，必须另行批准，不能让 Gateway 隐式依赖 Platform 数据库或同步授权接口。

### 3.3 签名密钥

- Platform Support 数据库只可保存 `kid`、算法、状态、有效区间和公钥/JWKS 元数据或引用；私钥只在 KMS/Secrets Manager 中使用，不进入业务数据库、Vault、配置文件或日志。
- 密钥轮换必须支持新旧公钥安全重叠，直至所有由旧 Key 签发且尚未过期的 Access Token 自然失效。

## 4. BFF 认证适配责任

Operations BFF 对三个认证动作提供外部页面友好的接口：

- Login：固定 `channel=OPERATIONS` 调用 Platform Support，返回 Access Token；将 Refresh Token 写入名为 `__Secure-operations-refresh` 的 Cookie，属性固定为 `Secure; HttpOnly; SameSite=Strict; Path=/api/operations/auth/v1`，不设置 `Domain`，`Max-Age` 不超过 Session 剩余绝对期限。
- Refresh：校验精确 Origin/CSRF 约束，从该 HttpOnly Cookie 取 Refresh Token 调用 Platform Support，完成 Cookie 单次轮换并返回新 Access Token；Gateway CORS 对精确 Origin 启用 Credentials。
- Logout：使用 Gateway 传入的可信 Subject/Session 上下文和 Refresh Cookie 调用 Platform Support 撤销 Session，并用相同 Name/Path/SameSite/Secure 属性及 `Max-Age=0` 清除 Cookie。

BFF 不直接访问 Platform Support 数据库。内部调用使用正常的 `@HttpExchange` 契约和服务信任边界；具体内部接口、DTO、幂等和错误枚举留到 Platform Support/BFF 实现设计。

## 5. 授权边界

Gateway 只证明请求携带一个签名、时效、Audience、Channel 和 Actor Type 正确的 Operations Access Token。

Operations BFF 在调用页面查询或命令前，使用 Platform Support 当前授权事实校验 Role/Permission/Resource；核心系统仍验证资源归属、状态机、Maker-Checker、金额边界和其他业务规则。任何一层缺失所需事实或依赖不可用时失败关闭。

账号密码、Token、Session 和权限事实都不得由 Gateway、BFF 或浏览器作为 Source of Truth。

## 6. 安全与审计

- 密码、密码校验值、Access JWT、Refresh Token、签名私钥和凭据材料不得进入 Vault、普通审计 Payload、应用日志、MQ、文件、Trace、Metric 或浏览器可见诊断。
- 现有统一 Audit/Security Event 能力记录 Login/Refresh/Logout/Reset/Disable 的结果、原因码、Request ID、Actor/Account/Session 引用、时间和安全上下文摘要；没有明确查询/保留需求时不新增重复的 Login Audit 链。
- 审计只记录 IP/User-Agent 等获批最小网络证据或不可逆摘要，不记录 Authorization/Cookie/Body。
- Login/Refresh 同时受 Gateway 网络级限流和 Platform 账号级失败控制；两者不能互相替代。
- 首期公网可访问只是部署阶段选择。未来 VPN/零信任接入是额外防线，不改变认证与授权职责。

## 7. 对历史 92 表模型的影响

2026-08-29 当时的 Platform Support `92 表 / 1,076 字段 / 167 唯一索引 / 116 访问索引 / 145 本库关系 / 19 个 1:1 / 8 个 Diagram Groups` 冻结模型按“外部 IdP 登录、Platform 只做 Subject Mapping”设计，缺少本地 Operations Account、Password Verifier、Refresh Session 和 Signing Key Metadata，不能直接实现本文。

本文只在未来实现语义上取代该模型中的以下内容：

1. “Platform 永不管理登录密码/Session、外部 IdP 是唯一登录事实源”的绝对表述；
2. `user_identity` 必须只能映射外部 IdP Subject 的假设；
3. Platform 可动态创建或改变 Gateway Path/Upstream 的假设；
4. Rate Limit/Concurrency 在不同计数维度也只取一个最高层规则的假设。

旧模型对其他 Platform Support 领域仍是当前冻结 V1 设计资产。未来实现 Operations 认证前必须单独批准物理模型修订，决定本地账号与现有 User/Identity 的关系，重新生成并验证 PDManer 精确计数，并同步 Database 文档、Scenario、Migration、DO/Mapper/Repository 和自动化测试。不得把现有必填 IdP 字段简单改成可空字段来绕过该设计。

Gateway Guard 的未来 Platform 配置只能引用部署中预先存在的 Route Code，并叠加不超过硬上限的保护策略；不能发布 Route/Path/Method/Upstream。Rate/Concurrency 按独立计数维度累计生效，Redis 保存运行计数/Lease，Platform MySQL 不保存运行时限流状态。

## 8. 最小未来数据需求（非表设计）

- Operations Local Account：关联现有 Platform User/授权身份，保存唯一登录名、生命周期、Password Verifier 算法/版本/参数、凭据版本、认证 Epoch 和锁定/节流状态；
- Refresh Session：账号、Session/Family、Token Verifier、Rotation Generation、签发/到期/最后使用/撤销、重用检测结果；
- Signing Key Metadata：`kid`、`RS256`、状态、有效区间、公钥/JWKS 元数据或 Secrets/KMS 引用；
- Audit/Security：复用现有统一能力保存无 Secret 的认证结果和安全事件。

这些是能力和不变量，不是表数量、表名或字段清单。任何物理实现都需要新的明确授权。

## 9. 非目标

- 不实现 Platform Support、BFF 或前端代码；
- 不修改 Platform Support PDManer、Manifest、Migration 或数据库计数；
- 不实现 MFA、SSO、外部 IdP 联邦、Client/Partner/Consumer 登录或机器 Credential；
- 不把业务 Email OTP 用于登录、管理员提权或财务 Maker-Checker；
- 不让 Gateway 调用 Platform Support 的登录、Refresh、Logout 或权限接口；
- 不让 Platform Support 管理 Gateway Route/Upstream 或核心系统业务流程。
