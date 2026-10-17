# Client Console 团队成员与邀请 — 技术方案

> 状态：2026-10-17 阶段 2 用户确认（“继续”：D1—D7 按建议）；阶段 3 设计见第 6 节起。
>
> 路径缩写：AC = `platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol`；BFF = `client-console-bff/paytech-client-console-bff-web/src/main/java/com/paytech/client/console/bff`；WEB = `client-console-web/src`。

## 1. 需求项差异

| 需求项 | 当前行为及证据 | 缺口 | 处理建议 |
|---|---|---|---|
| F-01 工作区角色 | 每个 CLIENT 工作区只有 `TENANT_OWNER` 一个系统角色，首次 ACTIVE 投影时创建并挂 4 个开发者权限（`AC/service/processor/ProjectTenantStatusProcessor.java:87-93`）；`insertRole` 的角色名 CASE 只有两种（`TenantProjectionMapper.xml:73-96`）；`t_role` 唯一键 `uk_role_scope(scope_key, role_code)` | 没有管理员 / 开发者 / 只读角色；没有团队权限码；存量 ACTIVE 工作区不会再走创建分支 | 新增 2 个权限码 `CLIENT_TEAM_READ/MANAGE`；投影首次 ACTIVE 时一并创建 3 个新角色；存量工作区用 DML 回填（见 D2、D3） |
| F-02 团队页面 | 无成员列表、邀请列表接口；成员表无脱敏邮箱，需要关联 `t_user_account.email_masked` | 未实现 | Platform 新增按当前工作区列出成员、邀请的查询；BFF 透传；Web 新增“团队”页 |
| F-03 发起邀请 | 无 `t_tenant_invitation` 表（PDManer 已建模：`platform-support.pdma.json:2646`，Schema SQL 没有）；`t_authentication_challenge` 已有 `tenant_invitation_id` 列与索引 | 表、接口、领域逻辑全部缺失 | 按 PDManer 模型补 DDL；新增创建邀请 Processor（锁工作区成员域） |
| F-03 邀请邮件 | 认证邮件发送器只区分“注册验证”，**其他用途一律落到重置密码模板和 `/reset-password` 路径**（`platform-support-web/.../integration/mail/DeliveryAuthenticationEmailSender.java:61-66`）；模板白名单在 `application.yml` 的 `notification.transient-template-codes`，模板内容在初始化 DML | 新用途会发错邮件 | 发送器改为按用途分派；新增邀请模板（DML + yml 白名单 + 测试 yml）；链接 `publicOrigin + "/invitations/accept#challengeNo=..&secret=.."` |
| F-03/F-04 挑战 | `ClientChallengePurpose` 只有注册与重置；`ClientEmailChallengePolicy.scopeHash` 要求 `accountId>0`，`latestChallenge(accountId, purpose)` 按账号找上一条；TTL 写死 1800s/1200s；`canIssue`、`matchesProofAccount` 只认两种用途 | 被邀请人可能还没有账号，现有范围计算与续发逻辑不适用 | 新增 `TENANT_INVITATION_ACCEPT` 用途：范围绑定 invitationId，账号为空；有效期与邀请到期一致；复用 HMAC、Secret 生成与尝试次数逻辑 |
| F-05 接受邀请 | 无接口；Web 启动时**无条件清掉 URL 的 hash 和 search**（`WEB/main.tsx:15-17`，只在清之前读取邮件链接）；登录成功后固定跳 `/onboarding`（`main.tsx:89`），没有 return-to | 链接会在读取前被清；登录或注册后回不到接受页 | 接受页在清 URL 前读取链接；Web 新增 return-to（只允许站内固定路径，存 sessionStorage）；Platform 新增“预览邀请”（公开，凭 challengeNo+secret）与“接受邀请”（账号上下文）两个接口 |
| F-05 被邀请成员看工作区 | **CCT 摘要只返回“本账号是申请人”的 Client**（`client-contract-web/.../mapper/ClientApplicationMapper.xml:48-49` `requested_by_account_id=#{accountId}`）；BFF 拿不到摘要时把编号、名称、类型置空（`BFF/service/processor/ClientWorkspaceProcessor.java:143-151`）；**Web 只要有一项缺编号或类型就把整个“我的工作区”判为不可读**（`WEB/workspace.ts:41`），首页同样显示“资料暂时无法读取”（`workspace.ts:86-89`） | 被邀请成员看不到工作区名称、进不了首页，还会连带让申请人自己的其他工作区显示不可读 | 见 D1：CCT 改为按已批准状态返回 BFF 传入的 clientIds（clientIds 只来自 Platform 的成员列表），不再按申请人过滤；Web 对单项缺失降级显示，不再整块失败 |
| F-06 调整角色 | 无接口；权限每次请求实时读库，不缓存（`AC/.../ClientPermissionMapper.xml:4-17` `useCache=false`） | 未实现 | 新增调整角色 Processor（撤销旧授权、授予新授权，同一事务）；降级下一次请求即生效（满足 Q7） |
| F-07 移除成员 | 每次请求都会重验成员状态，非 ACTIVE 返回 401（`CustomerAuthenticationServiceImpl.currentContext:105-113`）；刷新时成员不可访问则降级为账号上下文，原因 `MEMBER_INACTIVE`（`ClientSessionPreparation.java:140-152`）；但**工作区列表不按成员状态过滤**，REVOKED 也会返回（`ClientMemberMapper.xml:23-28`） | 移除接口缺失；被移除的工作区仍出现在列表里，与 AC-11 冲突 | 新增移除 Processor（成员 REVOKED，角色授权 REVOKED）；工作区列表排除 REVOKED 成员（见 D4） |
| F-08 按权限显示 | BFF `ClientPermissionProcessor.capabilities()` 一次批量问 Platform（PLT-I02），映射表固定 4 项（`ClientPermissionProcessor.java:74-80`）；开发者接口的拦截器按路径推导权限码（`ClientDeveloperWebConfiguration.java:41-66`）；Web 菜单 `developerMenu(capabilities)`（`WEB/developer.ts:13-20`） | 团队能力未加入 | 映射表加 `TEAM_READ/MANAGE`；新增团队拦截器；Web 菜单加“团队”，按钮按 `TEAM_MANAGE` 显示 |
| F-09 审计 | 客户自助写操作已有写事务内审计与 Controller `@Audit` 两种用法（`CreateClientApiCredentialProcessor.java:71,87-88`、`ClientApiCredentialController.java:64-88`）；`CustomerAuthenticationOutcome` 的 purpose 只允许两种（`OperationsAuditFact.java:35`） | 新动作与新用途未登记 | 新增审计动作；放开该限制以容纳邀请用途 |

## 2. 逐系统影响

| 系统 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|
| Platform（access-control、web、delivery 模板） | ① `t_tenant_invitation` 表、DataObject、Mapper、Repository；② 新角色与团队权限码，投影首次 ACTIVE 时创建，存量回填；③ 团队查询（成员、邀请）与写操作（发起、重发、撤销、调整角色、移除）共 7 个能力，锁工作区成员域；④ 邀请预览、接受（锁邀请 + 工作区 + 账号，一个事务完成“创建或恢复成员 + 授予角色 + 消费挑战 + 完成邀请”）；⑤ 挑战新用途；⑥ 邮件发送器按用途分派与新模板；⑦ 工作区列表排除 REVOKED；⑧ 错误码与审计 | 代码；**DDL（新表）**；**DML（2 个权限码、邀请邮件模板、存量工作区的 3 个角色及其权限、TENANT_OWNER 补团队权限）**；yml 模板白名单 | 邮件在事务提交后同步发送，失败不补偿，靠“重发”恢复（与注册邮件一致）；投影锁键前缀 `lock:customer:account:` 与认证侧 `customer:account:` 不一致（既有问题，见风险 4） |
| CCT | 工作区摘要可见性改为“已批准即可返回，clientIds 由 BFF 传入”，不再按申请人过滤 | 代码（Mapper 条件、Processor 注释与测试）；无 DDL | 可见性由 Platform 成员事实决定，CCT 只信任内部调用方 BFF 传入的 clientIds（见 D1） |
| Client BFF | 团队接口（约 7 个，租户上下文，按权限拦截）；邀请预览（公开）与接受（账号上下文）；能力映射加团队；错误映射；空 Body 命令路径登记（`ClientRequestInterceptor.java:34-36`，重发、撤销属于空 Body POST）；日志前缀 | 代码、配置 | 接受接口不能复用强制租户上下文的 `require()` |
| Client Web | “团队”页（成员、邀请、邀请弹窗、二次确认）；接受邀请页（公开，读取链接 → 预览 → 未登录则去登录 / 注册 → 回到接受页 → 接受 → 回“我的工作区”）；return-to；`auth.ts` 路径白名单（`auth.ts:113`）加团队与邀请；`workspace.ts` 单项缺失降级 | 代码 | 只能用纯函数测试（`node --test`），页面验收靠浏览器脚本 |
| Gateway（路由登记） | 新增团队 7 条（`CLIENT_JWT` + `HUMAN_AUTHENTICATED`，Cookie `DROP/NONE`）、邀请预览 1 条（`PUBLIC`）、接受 1 条（账号上下文 JWT） | 只登记，不改代码；登记计划由 `complete_registration_plan.py` 生成 | 公开预览路由需要限流 |
| Risk、Commerce、Operations | 不改 | 无 | Operations 已有的成员只读查询（PLT-15）可以看到新成员，无需改动 |

## 3. 主要风险

1. **CCT 可见性变更（D1）**：从“只看自己申请的”改为“已批准的都可返回”，安全性依赖 BFF 只传 Platform 成员列表里的 clientIds。CCT 接口是内部接口，不经 Gateway 对外。
2. **存量工作区回填**：test 上已有若干 ACTIVE 工作区，需要用 DML 为每个工作区补 3 个角色和权限，并给 TENANT_OWNER 补团队权限；ID 规则沿用接入自助 DML 的固定偏移做法，可重复执行。
3. **邮件失败不补偿**：邀请创建成功但邮件发送失败时，邀请仍为 PENDING，界面提示“邮件未发出，可重发”。与现有注册邮件一致，不新增补偿事件。
4. **既有问题：投影与认证锁键前缀不一致**：两边不互斥。本需求的接受邀请会锁账号，这里统一用认证侧前缀；投影侧的不一致单独记录，不在本需求里修改（见 D7）。
5. **新用户要收两封邮件**：被邀请的新用户先收邀请邮件，注册时再收一封注册验证邮件。按设计注册必须验证邮箱；邀请链接本身也能证明邮箱归属，但合并两步会改变注册流程，本期不做。

## 4. 待决项（阶段 2 确认）

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 被邀请成员如何拿到工作区名称和首页资料 | CCT 摘要接口去掉“申请人”条件，只要求 Client 已批准；clientIds 只由 BFF 从 Platform 当前账号的成员列表取得。不让 CCT 反查 Platform（避免新增跨系统调用） |
| D2 | 新角色何时创建 | 首次 ACTIVE 投影时与 TENANT_OWNER 一起创建；存量工作区用 DML 回填。不做“首次邀请时懒创建” |
| D3 | 角色与权限矩阵 | `TENANT_OWNER`：全部 6 个权限；管理员：全部 6 个；开发者：`TEAM_READ` + API 凭证、Webhook 读与管理；只读成员：`TEAM_READ` + API 凭证、Webhook 只读。OWNER 与管理员的区别只在“不能动 OWNER、不能移除自己”这类规则，不靠权限码区分 |
| D4 | 工作区列表是否显示已移除的工作区 | 排除 REVOKED 成员（满足 AC-11）；SUSPENDED 成员仍显示为受限（本期不产生 SUSPENDED，只保留现状） |
| D5 | 未登录打开邀请链接时显示什么 | 只显示“受邀邮箱（脱敏）、角色、到期时间”，并提示用该邮箱登录或注册；工作区名称在登录后（账号上下文）由 BFF 从 CCT 读取再显示。不在公开接口暴露工作区名称 |
| D6 | 新用户是否要收两封邮件 | 是（见风险 5），本期不合并 |
| D7 | 投影锁键前缀不一致（既有问题） | 本需求不改，登记为遗留；接受邀请与成员管理使用认证侧前缀 |

## 5. 工作量依据

Platform 改动最多：1 张新表；约 9 个接口能力（团队 7 个，邀请预览与接受 2 个）；挑战新用途、邮件分派、投影与回填 DML；补 Client 团队与邀请的 auto 测试，可复用 `ClientWorkspaceMocker`、`LoginEmailVaultMocker`、`SesMocker`（用于抓取邮件链接）。CCT 改一条查询条件和测试。BFF 约 9 个端点和配置。Web 新增两个页面并修改认证模块。Gateway 登记 9 条路由。本需求有 DDL、DML 和路由注册数据。

## 6. 业务与架构

```
团队管理（租户上下文）
浏览器 ──团队接口──► BFF（校验租户头 + 权限）─► Platform ClientTeamApi ─► 成员 / 角色授权 / 邀请 / 挑战
                                                         └─提交后─► Delivery 邀请邮件
接受邀请
邮件链接 /invitations/accept#challengeNo=..&secret=..
浏览器 ──预览（公开）──► BFF ─► Platform：受邀邮箱（脱敏）、角色、到期、状态
浏览器 ──登录 / 注册（现有）──► 回到接受页
浏览器 ──详情（账号上下文）──► BFF ─► Platform（校验邮箱一致，返回 clientId）─► CCT 摘要（工作区名称）
浏览器 ──接受（账号上下文）──► BFF ─► Platform：一个事务创建或恢复成员 + 授予角色 + 消费挑战 + 完成邀请
```

- **数据 Owner**：成员、角色、权限、邀请、挑战均归 Platform；工作区名称归 CCT；BFF 只聚合，不保存。
- **角色（D2、D3）**：每个 CLIENT 工作区 4 个系统角色（`system_role=1`，`scope_key=tenantId`）。新角色在首次 ACTIVE 投影时与 `TENANT_OWNER` 同一事务创建；存量工作区由 DML 回填。

  | 角色代码 | 名称 | 成员类型 | TEAM_READ | TEAM_MANAGE | API 凭证 / Webhook 读 | API 凭证 / Webhook 管理 |
  |---|---|---|---|---|---|---|
  | `TENANT_OWNER`（已有） | 所有者 | OWNER | ✓ | ✓ | ✓ | ✓ |
  | `TENANT_ADMIN` | 管理员 | ADMIN | ✓ | ✓ | ✓ | ✓ |
  | `TENANT_DEVELOPER` | 开发者 | MEMBER | ✓ | | ✓ | ✓ |
  | `TENANT_VIEWER` | 只读成员 | MEMBER | ✓ | | ✓ | |

  可邀请或改授的只有后三个；成员类型随角色确定（管理员 → ADMIN，其他 → MEMBER）。
- **保护规则（Q2）**：不能修改或移除 OWNER；不能修改或移除自己（防止管理员把自己锁在外面）。OWNER 永远存在，所以不需要“最后一个管理员”保护。
- **邀请生命周期**：`PENDING` → `ACCEPTED` / `REVOKED`；到期不改状态，读取时按 `expires_time` 推导为 `EXPIRED`。对同一邮箱再次邀请时，若旧的 PENDING 已过期，在同一事务内先把它置为 `EXPIRED`（释放活动位）再新建。`SUPERSEDED` 本期不使用。重发不新建邀请：同一邀请重新计算 7 天到期，新挑战替代旧挑战（旧挑战 `SUPERSEDED`）。每个工作区最多 20 条未过期的 PENDING 邀请。
- **挑战新用途 `TENANT_INVITATION_ACCEPT`**：`account_id` 为空，`tenant_invitation_id` 必填；范围 = SHA-256(`CLIENT-INVITATION-SCOPE` ‖ invitationId)，每个邀请只有一个活动挑战；到期 = 邀请到期；HMAC、Secret 生成与 5 次尝试沿用现有实现。
- **邮箱一致性**：受邀邮箱经现有 `clientLoginEmailVault` 以 STABLE 模式换成 Token（事务外），与账号的 `email_vault_token` 直接比较；接受时不再访问 Vault。
- **邮件**：事务提交后同步发送，失败不回滚邀请，响应 `emailDelivery=FAILED`，界面提示可重发（风险 3）。
- **锁（按实际资源，第 5.1 节）**：

  | 操作 | 锁 | 数据库保护 |
  |---|---|---|
  | 发起邀请 | 无（`AbstractProcessor`） | `uk_tenant_invite_active` 裁决同邮箱并发；冲突返回 409 |
  | 重发、撤销 | `client-invitation:{invitationId}` | 状态 + 版本 CAS |
  | 接受 | `client-invitation:{invitationId}` + `customer:account:{accountId}`，已有成员时再加 `tenant-member:{tenantId}:{memberId}` | 挑战 `active_slot` + 状态 CAS 只允许一次消费；`uk_tenant_member` 防重复成员 |
  | 调整角色、移除 | `tenant-member:{tenantId}:{memberId}` | 成员版本 CAS；授权行按状态更新 |

  接受时不锁租户：租户状态在事务内读取，并发的暂停相当于“接受后立即暂停”，下一次请求即失败关闭。
- **事务**：所有写操作用 `TransactionTemplate`；Vault 换 Token、挑战 HMAC 计算、邮件发送都在事务外。
- **审计（F-09）**：新增动作 `CLIENT_TEAM_INVITATION_CREATE`、`_RESEND`、`_REVOKE`、`_ACCEPT`、`CLIENT_TEAM_MEMBER_ROLE_CHANGE`、`CLIENT_TEAM_MEMBER_REMOVE`；Target 为邀请 ID 或成员 ID，不写邮箱原值和 Secret。
- **工作区列表（D4）**：`GET /workspaces` 排除 REVOKED 成员。

## 7. 接口

### 7.1 Platform（私网）

公共 Header：`X-Gateway-Actor-Subject`（账号 ID）、`X-Gateway-Session-Id`（会话 ID），由 BFF 透传；租户只取自当前租户上下文会话，不接受调用方指定。

**新增 `ClientTeamApi`，前缀 `/client-team/v1`**（租户上下文；读需要 `CLIENT_TEAM_READ`，写和邀请列表需要 `CLIENT_TEAM_MANAGE`；按 ID 操作不属于当前工作区时返回 404）

| 操作 | Method / Path | 请求 | 响应 |
|---|---|---|---|
| 成员列表 | `GET /members?page&size` | 公共分页 | `PagingResponseBody<TeamMember>`：memberId、emailMasked、memberType、roleCode、status、joinedAt、self；只列 ACTIVE 成员，OWNER 在前，其余按加入时间 |
| 邀请列表 | `GET /invitations?status&page&size` | status 可选 `PENDING`（默认）/ `EXPIRED` / `ACCEPTED` / `REVOKED` | `PagingResponseBody<TeamInvitation>`：invitationId、emailMasked、roleCode、status、expiresAt、invitedByEmailMasked、createdAt |
| 发起邀请 | `POST /invitations` | `{"email":"…","roleCode":"TENANT_DEVELOPER"}` | 201：邀请摘要 + `emailDelivery`（`SENT` / `FAILED`） |
| 重发 | `POST /invitations/{invitationId}/resend` | 空 Body | 200：邀请摘要 + `emailDelivery` |
| 撤销 | `POST /invitations/{invitationId}/revocation` | 空 Body | 200：邀请摘要；已撤销返回原结果 |
| 调整角色 | `POST /members/{memberId}/role` | `{"roleCode":"TENANT_VIEWER"}` | 200：成员摘要；角色未变返回原结果 |
| 移除成员 | `POST /members/{memberId}/removal` | 空 Body | 200：成员摘要；已移除返回原结果 |

**新增 `ClientInvitationApi`，前缀 `/client-invitations/v1`**

| 操作 | Method / Path | 上下文 | 请求 | 响应 |
|---|---|---|---|---|
| 预览 | `POST /preview` | 公开（不需要账号 Header） | `{"challengeNo":"…","secret":"…"}` | emailMasked、roleCode、expiresAt、status（`PENDING` / `EXPIRED` / `ACCEPTED` / `REVOKED`） |
| 详情 | `POST /details` | 账号上下文 | 同上 | 预览字段 + `clientId`、`emailMatches`；邮箱不一致时不返回 clientId |
| 接受 | `POST /acceptance` | 账号上下文 | 同上 | `{"outcome":"JOINED"/"ALREADY_MEMBER","tenantId","memberId","clientId"}` |

- 预览和详情不消费挑战；Secret 校验失败计入尝试次数（满 5 次挑战失效）。
- 接受已 ACCEPTED 且接受人是同一账号时返回 `ALREADY_MEMBER`（AC-08 幂等）；已是其他身份进入的 ACTIVE 成员也返回 `ALREADY_MEMBER` 并完成邀请；已移除的成员恢复为 ACTIVE 并授予邀请角色（AC-12）。

**调整 `ClientAuthenticationApi`**：`GET /workspaces` 排除 REVOKED 成员（不改字段）。

| 错误码（新增除注明外） | HTTP | 场景 |
|---|---|---|
| `CLIENT_INVITATION_INVALID` | 404 | 链接不存在、Secret 不正确、尝试次数用尽；不区分原因 |
| `CLIENT_INVITATION_CLOSED` | 409 | 邀请已过期、已撤销、已被其他账号接受 |
| `CLIENT_INVITATION_EMAIL_MISMATCH` | 403 | 登录账号邮箱与受邀邮箱不同 |
| `CLIENT_INVITATION_DUPLICATE` | 409 | 同邮箱已有有效邀请（提示改用重发） |
| `CLIENT_INVITATION_LIMIT_REACHED` | 409 | 有效邀请已达 20 条 |
| `CLIENT_MEMBER_EXISTS` | 409 | 受邀邮箱已是本工作区 ACTIVE 成员 |
| `CLIENT_MEMBER_PROTECTED` | 409 | 修改或移除 OWNER、修改或移除自己 |
| `CLIENT_WORKSPACE_UNAVAILABLE`（已有） | 403 | 工作区暂停或终止时发起或接受邀请 |
| `CLIENT_PERMISSION_DENIED`（已有） | 403 | 缺少团队权限 |
| `NOT_FOUND`（已有） | 404 | 邀请或成员不属于当前工作区 |
| `ConcurrentRequestException`（已有） | 409 | 同一邀请或成员并发操作 |

### 7.2 CCT

`GET /client-workspace/v1/client-summaries`：去掉 `requested_by_account_id=#{accountId}` 条件，改为“存在 APPROVED 申请的 Client”；其余参数、鉴权（实时校验调用方会话）与字段不变（D1）。接口注释写明“clientIds 由 BFF 从 Platform 成员列表取得，CCT 不判断成员关系”。

### 7.3 Client BFF（对外经 Gateway）

| 操作 | 对外 Method / Path | BFF 路径 | 拦截 |
|---|---|---|---|
| 成员列表 | `GET /api/client/team/v1/members` | `/client-team/v1/members` | 租户上下文 + `CLIENT_TEAM_READ` |
| 邀请列表 | `GET /api/client/team/v1/invitations` | `/client-team/v1/invitations` | 租户上下文 + `CLIENT_TEAM_MANAGE` |
| 发起邀请 | `POST /api/client/team/v1/invitations` | 同上 | 同上 |
| 重发 | `POST /api/client/team/v1/invitations/{id}/resend` | 同上 | 同上，空 Body |
| 撤销 | `POST /api/client/team/v1/invitations/{id}/revocation` | 同上 | 同上，空 Body |
| 调整角色 | `POST /api/client/team/v1/members/{id}/role` | 同上 | 同上 |
| 移除成员 | `POST /api/client/team/v1/members/{id}/removal` | 同上 | 同上，空 Body |
| 预览邀请 | `POST /api/client/invitations/v1/preview` | `/client-invitations/v1/preview` | 公开，仅 Origin / Content-Type |
| 邀请详情 | `POST /api/client/invitations/v1/details` | `/client-invitations/v1/details` | 账号上下文；BFF 再按 clientId 调 CCT 摘要，补 `workspaceName` |
| 接受邀请 | `POST /api/client/invitations/v1/acceptance` | `/client-invitations/v1/acceptance` | 账号上下文 |

- 团队接口照接入自助的做法：控制器直接注入 Platform `ClientTeamApi`，独立的 `ClientTeamWebConfiguration` 拦截器按路径和方法推导权限码（GET 成员 → READ，其余 → MANAGE）。
- 能力映射增加 `TEAM_READ`、`TEAM_MANAGE`；W02 首页和团队页通过能力列表决定菜单和按钮。
- 空 Body 命令路径（重发、撤销、移除）登记到 `ClientRequestInterceptor`。
- 新错误码在 `ClientErrorStatusResolver` 映射；`/client-team/`、`/client-invitations/` 加入 metadata-only 日志前缀（请求含邮箱和 Secret）。

## 8. 数据库与注册数据

需求目录下新增 `db/ddl.sql`、`db/dml.sql`，同步正式 Schema 与初始化 DML；PDManer 模型已有该表，按模型核对，不改字段。

- **DDL**：`CREATE TABLE t_tenant_invitation`（字段与索引按 PDManer 模型：`uk_tenant_invitation_no`、`uk_tenant_invite_active(tenant_id, invited_email_vault_token, active_slot)`、`idx_invite_inviter`、`idx_invite_accepted_member`、`idx_tenant_invit_initial_role_i`、`idx_tenant_invit_accepted_accou`）。回滚：`DROP TABLE`（无存量数据时）。
- **DML（可重复执行）**：
  1. 权限码 `CLIENT_TEAM_READ`（`900000000000058105`，LOW）、`CLIENT_TEAM_MANAGE`（`…058106`，HIGH）。
  2. 邀请邮件模板 `CLIENT_TENANT_INVITATION`（zh-CN，参数 `${link}`），同时登记到 `notification.transient-template-codes`。
  3. 存量非 PENDING 的 CLIENT 工作区：创建 `TENANT_ADMIN/DEVELOPER/VIEWER` 三个角色及其权限；为 `TENANT_OWNER` 补两个团队权限。已存在（按 `uk_role_scope`、`uk_role_permission`）则跳过。新行 ID 取固定高位区间：从 `9100000000000000000` 起，按该区间当前最大值依次递增分配（Snowflake 运行期 ID 远低于该区间，BIGINT 上限前留有约 1.2×10¹⁷ 空间）。不沿用接入自助 `role_id × 10` 的做法，因为对新角色再乘一次会超出 BIGINT。
- **配置**：Platform `application.yml` 模板白名单；邀请链接复用 `client.authentication.public-origin`，路径 `/invitations/accept`。
- **Gateway 路由**（更新入驻目录的登记计划生成脚本，不生成 SQL）：团队 7 条（`CLIENT_JWT` + `HUMAN_AUTHENTICATED`，Cookie `DROP/NONE`，列表允许 `page/size/status` 查询参数）；预览 1 条（`PUBLIC`，Cookie `DROP/NONE`，按 IP 限流）；详情、接受 2 条（`CLIENT_JWT` + `HUMAN_AUTHENTICATED`）。共 10 条。

本机与 mor-test 的 SQL 执行和路由登记属于发布动作，在阶段 6 / 7 另行授权。

## 9. 页面

沿用工作区外壳（`/workspace` 侧边栏）；不新建 Pencil 画板，以下线框为本期设计基线。

```
/workspace/team（导航“团队”，有 TEAM_READ 时显示）
┌ 成员 ───────────────────────────────────── [邀请成员]* ┐
│ a***@example.com  所有者    已加入 2026/10/16            │
│ b***@example.com  管理员    已加入 2026/10/17  [更改角色]* [移除]* │
│ c***@example.com  开发者 ·你 已加入 2026/10/17            │
└────────────────────────────────────────────────┘
┌ 邀请*（待接受 / 已过期 / 已接受 / 已撤销 筛选）──────────────┐
│ d***@example.com  只读成员  7 天后到期   [重发] [撤销]          │
│ （空状态：还没有待接受的邀请）                                 │
└────────────────────────────────────────────────┘
* 只有 TEAM_MANAGE 可见；OWNER 与自己所在行不显示操作

邀请弹窗：邮箱 · 角色（单选，附说明：管理员可管理团队；开发者可管理 API 凭证与 Webhook；只读成员只能查看）
         [取消] [发送邀请]（处理中禁用；邮件未发出时提示“邀请已创建，邮件未发出，可稍后重发”）

/invitations/accept（公开页，无外壳）
未登录：你被邀请加入一个 Client 工作区 · 角色：开发者 · 受邀邮箱 d***@example.com · 2026/10/24 到期
       [登录] [注册]（登录或注册后自动回到本页）
已登录：工作区 Example Trading LLC · 角色：开发者 · 当前账号 d***@example.com   [接受邀请]
邮箱不一致：当前登录账号不是受邀邮箱，请退出后用 d***@example.com 登录   [退出并切换账号]
已失效：该邀请已过期或已撤销，请联系工作区管理员重新邀请
接受成功 → 回到“我的工作区”，新工作区显示可进入
```

- 链接参数在 Web 启动清 URL 之前读取，存入 sessionStorage；登录成功后若有待处理邀请，回到 `/invitations/accept`（return-to 只允许这一条站内路径）。注册需要在新标签页完成邮箱验证；验证后若在新标签页登录，再次点开邀请邮件即可。
- 当前处于租户上下文时，接受前先切回账号上下文（W01）。
- 移除、撤销弹二次确认；更改角色为行内下拉，即时保存并反馈（UI-03）。
- 无 TEAM_MANAGE 的成员只看到成员列表，不显示邀请区和操作（Q3、UI-05）。
- 390px：成员行改为上下两行，操作收进“更多”按钮（UI-06）。
- `workspace.ts` 改为单个工作区缺资料时只降级该项（显示客户编号或“名称暂不可用”），不再整块失败。

## 10. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证入口 |
|---|---|---|---|
| 1 | Platform | 邀请表的 DataObject / Mapper / Repository；投影创建新角色；挑战新用途；邮件分派与模板；`ClientTeamApi` 7 个、`ClientInvitationApi` 3 个；`GET /workspaces` 排除 REVOKED；错误码与审计；Schema、初始化 DML | HTTP 入口 auto：发起 → 抓取 `SesMocker` 邮件链接 → 预览 → 详情 → 接受 → 成员列表；重复邀请 / 已是成员 / 上限；重发后旧链接失效；撤销；过期；邮箱不一致；工作区暂停；已接受幂等；移除后重邀恢复；同一邀请并发接受只产生一个成员；调整角色后权限立即变化；OWNER / 自己受保护；跨工作区 404；投影创建 4 个角色 |
| 2 | CCT | 摘要查询去掉申请人条件 | 既有 `ClientSummariesTest` 调整：非申请人请求已批准 Client 也返回 |
| 3 | Client BFF | 团队 7 个、邀请 3 个端点；能力映射；拦截器；错误映射；日志前缀；详情聚合工作区名称 | loopback 上游 Mock 风格的 auto：权限拦截、上下文校验、空 Body、公开预览不带身份、详情聚合与 CCT 不可用降级 |
| 4 | Client Web | 团队页、邀请弹窗、接受页、return-to、`auth.ts` 白名单、`workspace.ts` 降级 | `node --test` + 构建；浏览器验收（桌面 / 390px） |
| 5 | 登记计划与验收脚本 | 路由生成脚本；本机 `team_flow.py`（API 级）、`team_browser.mjs`（浏览器） | 生成结果核对；本机全链路验收 |

验收映射：AC-01—04 → 发起、重发、撤销与邀请列表；AC-05—08 → 预览、详情、接受；AC-09 → 工作区状态检查；AC-10、AC-11、AC-12 → 调整角色、移除、重邀恢复与 `GET /workspaces`；AC-13 → BFF 拦截与能力列表；AC-14 → 按 ID 归属检查；AC-15 → 并发接受测试；AC-16 → HMAC 存储、日志前缀、审计字段与 Token Claims；UI-01—06 → Web 与浏览器验收。

兼容与发布顺序：DDL、DML → Platform → CCT → BFF → Web；新接口不影响旧 Web。CCT 放宽可见性后，旧 BFF 行为不变（只是多返回被邀请成员的摘要）。test 上的认证邮件可能因发件身份未配置而发不出，验收脚本沿用入驻验收“重算挑战”的兜底做法，并在验收记录中说明。
