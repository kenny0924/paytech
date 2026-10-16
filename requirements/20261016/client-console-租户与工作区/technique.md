# Client Console 租户与工作区 — 技术方案

> 状态：阶段 2 已确认（2026-10-16 用户“继续租户与工作区”，D1—D6 按建议）；阶段 3 设计见第 6 节起，评审稿待确认。

## 1. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| F-01 我的工作区列表 | Platform 只有面向 Operations 的租户查询（`TenantAccessManagementApi`）；客户侧没有按账号列出成员的查询；租户名称为 `Client {clientId}`（`TenantProjectionMapper.xml:64-67`）；CCT 没有按 clientId 读取已批准资料摘要的接口；C01 `start` 只聚合入驻申请（BFF `ClientOnboardingProcessor.start:43-63`） | 账号上下文下列出本账号的 CLIENT 工作区（不含 PENDING），显示 CCT 名称、客户编号、类型、可访问 / 受限及原因 | 未实现：Platform 按账号查成员 + 租户；CCT 批量摘要；BFF 聚合 | 新增 Platform 查询、CCT 只读接口，在 BFF C01 聚合 |
| F-02 进入工作区（C02 / PLT-I16） | 无接口；会话写入 SQL 写死账号上下文（`CustomerAuthenticationMapper.xml:69`）；`ClientAccountPolicy:22-27` 拒绝租户上下文会话；Token 已能写租户 Claims（`ClientTokenPreparation:73-77`）；C02 路由在 mor-test 已启用但 BFF 无处理器 | 校验成员属本账号且 ACTIVE、租户来源与安全状态 ACTIVE；新建租户 Session、撤销旧 Refresh Family、签发 Token、替换 Cookie | 未实现，且现有策略主动拒绝 | 新增 Platform Processor（照 `LoginClientProcessor` 模式，判定照 `OperationsSessionPolicy`）；BFF 端点复用 `ClientSessionProcessor.grant` |
| F-03 返回账号 / 切换 | 无接口 | 租户上下文切回账号上下文；切换到另一工作区 = 再走一次 C02 | 未实现 | 新增 `account-context` 接口，与 C02 共用骨架 |
| F-04 工作区首页 | 无 | 显示当前客户名称、类型、编号、状态与已批准资料摘要 | 未实现；CCT 从未写 `approved_time`（只有申请 APPROVED 状态可判定，`ClientReviewOutcomeServiceImpl:28-34`） | CCT 摘要按“申请已 APPROVED”认定；BFF 从租户头取 clientId |
| F-05 外壳与导航 | Web 只有顶栏 `Shell`（`OnboardingPage.tsx:240-271`）；设计基线为侧边栏 + `/workspace`（`ui-design.md` CC-P11） | 工作区外壳写死菜单（Q1） | 未实现 | 新增 `/workspace` 路由与外壳；账号页保持现有顶栏 |
| F-06 入驻结果页入口 | 通过后写死“正在开通”（`onboarding.ts:100-106`） | 工作区可访问时显示“进入工作区” | 未实现 | 结果页读取工作区列表状态 |
| F-07 上下文失效 | 租户会话首次刷新即被撤销并返回失效（`RefreshClientSessionProcessor:71-74`）；Web 401 直接回登录（`auth.ts:85`） | 回到账号上下文的工作区列表并显示原因 | 刷新时没有成员 / 租户重验；租户会话撤销后没有账号会话可回 | 见待决 D4 |
| AC-11 安全 | Token 不含 Email 等（现状满足）；入驻路径已 metadata-only 日志 | 新路径同样只记元数据 | 需配置 | BFF 日志前缀补新路径 |

## 2. 逐系统影响

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| Platform（access-control） | 账号、会话、租户成员事实 | ① 会话写入支持租户上下文（Mapper + `Issue` 记录）；② 新增成员 / 租户查询端口（照 `OperationsIdentityMapper.xml:82-91`）；③ 新增 C02 与返回账号 Processor（`AbstractLockProcessor`，锁 `customer:account:{id}`，与租户投影同一互斥域）；④ `ClientAccountPolicy` 增加租户分支并在刷新 / 查询时重验；⑤ 按账号列工作区查询；⑥ 新错误码（如 `CLIENT_WORKSPACE_UNAVAILABLE`）与审计动作 | 代码；无 DDL（`t_auth_session` 已有上下文列） | 入驻文件上传等调用 `authentication.context` 的用例在租户上下文下的行为要一并确认；Platform 目前没有 Client 登录 / 刷新的 auto 测试，需补夹具（测试基础设施可造 CUSTOMER 账号、CLIENT 租户与会话） |
| Gateway | 认证、路由 | 新增路由登记：返回账号（Cookie 模式同 C02）、工作区列表 / 首页（不带 Cookie）；C02 已登记 | 只登记，不改代码 | Gateway 没有“路由要求租户上下文”的配置，由 BFF 校验；已有缺陷：A05 刷新失败时 BFF 清 Cookie（`Max-Age=0`），而路由响应 Cookie 模式为 `ISSUE_ONLY`，Gateway 判为非法上游响应（`ConsoleRefreshCookiePolicy:137`）——浏览器拿到的不是 401 |
| CCT | Client 资料事实 | 新增只读批量摘要接口：按 clientIds 返回编号、类型、状态、名称；只返回本账号作为申请人且申请 APPROVED 的 Client，其余静默不返回 | 代码；无 DDL | 归属目前按“申请人账号”判断，以后有受邀成员要改由 Platform 判定；需新增 APPROVED 夹具与租户上下文的会话 Mock |
| Client BFF | 页面聚合、Cookie | C02、返回账号端点（身份头 + Cookie → Platform → `grant`）；C01 增加“我的工作区”聚合；工作区首页端点（读取 `X-Gateway-Tenant-*` 头，校验上下文类型）；日志前缀 | 代码、配置 | C02 的 BFF 内部路径（`/client-onboarding/v1/workspace-context`）与 Cookie Path 不同，登记时对齐；租户上下文下的退出需确认撤销的是当前租户 Session |
| Client Web | 页面 | 开始页“我的工作区”；`auth.ts` 新增选择上下文 / 返回账号（换 Token、使进行中请求失效、加入跨标签锁、放宽路径白名单）；`/workspace` 外壳与首页；结果页入口；失效回列表 | 代码 | 测试用 `node --test`，构建 `tsc && vite build`；设计基线图里“查看已审核资料 / 客户资料”菜单超出本期，不实现 |
| Risk、Operations BFF / Web | — | 不改 | 无 | — |

## 3. 主要风险

1. **租户会话刷新**：现在第一次刷新就被撤销。必须同时改 Platform 策略与刷新重验，否则进入工作区 15 分钟后会被踢出。
2. **刷新失败时清 Cookie 的路由缺陷**（已有问题，账号上下文同样存在）：失效会表现为“上游错误”而不是 401，Web 难以区分。建议本需求一并修正路由 Cookie 模式或 Gateway 策略。
3. **“已批准资料”认定**：CCT 的 `approved_time` 从未写入。本期按申请 APPROVED 认定；字段补写另行处理。
4. **工作区内的入驻接口**：CCT 的 `authorize` 同时接受两种上下文；需明确租户上下文下入驻页是否可用（建议只在账号上下文使用，Web 先切回账号）。

## 4. 待决项（阶段 2 确认）

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 进入工作区后新 Session 的绝对有效期 | 沿用原登录 Session 的绝对到期时间，切换不延长登录寿命 |
| D2 | 租户上下文允许的操作（现为 `ONBOARDING, LOGOUT`） | 租户上下文：`WORKSPACE, SWITCH_CONTEXT, LOGOUT`；入驻只在账号上下文 |
| D3 | 进入工作区是否额外要求有效的 `TENANT_OWNER` 角色授权 | 不要求；按认证设计只校验成员与租户状态，角色留给后续权限判断 |
| D4 | F-07“回到列表”：租户会话失效后已无账号会话 | 刷新时若租户或成员失效但账号仍有效，Platform 撤销租户会话并改签账号上下文（携带公开原因码），BFF 返回新账号 Token，Web 回到列表显示原因；账号也失效时才回登录 |
| D5 | 已有缺陷：刷新失败清 Cookie 被 Gateway 判为非法响应 | 本需求一并修正（阶段 3 给出具体改法：路由 Cookie 模式或 Gateway 策略） |
| D6 | 页面外壳 | 账号页保持现有顶栏和 `/onboarding`；新增 `/workspace` 侧边栏外壳，为后续菜单预留 |

## 5. 工作量依据

Platform 改动最多（会话写入、两个新 Processor、策略重验、两条查询、测试夹具与 Client 认证首批 auto 测试）；CCT 一个只读接口；BFF 三个端点加一处聚合；Web 新增一个路由与外壳、改动认证模块；Gateway 只登记路由（D5 可能改一处）。无业务表 DDL；有 Gateway 路由注册数据。

---

# 阶段 3：方案、接口与数据设计（评审稿）

**规模（本次方案）：** 涉及 5 个系统（Platform、CCT、Client BFF、Client Web、Gateway 路由登记）；按业务操作去重新增 4 个接口（我的工作区、进入工作区、返回账号、工作区首页）、调整 3 个（开始页、刷新、Token 上下文）；新增表 0、字段 0、修改表 0、索引 0、业务 DML 0；Gateway 路由登记新增 2 条、修改 2 条（经 Platform 管理接口，不写 SQL）；页面新增 1 个（工作区首页，含侧边栏外壳），修改 2 处（开始页工作区区域、入驻结果提示）。

## 6. 业务与架构

```
浏览器 ──C01──► BFF ─► Platform 我的工作区（成员+租户）──┐
                    └► CCT 客户摘要（名称/编号/类型）  ──┴─► 合并 = “我的工作区”
浏览器 ──C02{memberId} + Refresh Cookie──► BFF ─► Platform 进入工作区 ─► 新租户 Session + 新 Cookie
浏览器 ──W01 + Cookie──► BFF ─► Platform 返回账号 ─► 新账号 Session + 新 Cookie
浏览器 ──W02（租户 Token）──► BFF（校验租户头）─► CCT 客户摘要 ─► 工作区首页
浏览器 ──A05 刷新──► Platform：租户会话若租户/成员失效 → 撤销 + 改签账号会话（带 fallbackReason）
```

- **数据 Owner**：成员、租户状态、会话归 Platform；客户名称与已批准资料归 CCT；BFF 只聚合，不保存。
- **进入工作区（C02）**：一个本地事务，锁 `customer:account:{accountId}`（与租户投影同一互斥域）。顺序：事务外校验 Cookie 证明并预签名；事务内锁账号 → 当前 Session → Refresh Family → 成员与租户；校验账号 `eligible`、成员属本账号且 ACTIVE、租户 `tenant_type=CLIENT` 且 `owner_status`、`security_status` 均为 ACTIVE；撤销当前 Session（原因 `CONTEXT_SWITCH`）与 Refresh Family；写新 Session（`context_type=TENANT`、tenant_id、member_id，**绝对到期沿用原 Session**——D1）与新 Refresh；任一校验失败统一返回 `CLIENT_WORKSPACE_UNAVAILABLE`（不区分“不存在 / 不属于你 / 被暂停”——AC-05）。
- **返回账号（W01）**：同一骨架，不需要成员校验，写账号上下文 Session（绝对到期同样沿用）。
- **刷新（A05，D4）**：账号会话行为不变。租户会话：事务内重读成员与租户；有效则照常轮换；失效而账号仍 `eligible` 时撤销租户 Session 与 Family，改签账号上下文 Session，响应 `context.fallbackReason` 取公开原因码；账号也失效时返回 `CLIENT_SESSION_INVALID`。
- **查询上下文（A09）**：租户会话按当前成员与租户重验，失效返回 `CLIENT_SESSION_INVALID`（Web 随后刷新即走 D4 降级）。
- **允许的操作（D2）**：账号上下文 `ONBOARDING, SELECT_WORKSPACE, LOGOUT`；租户上下文 `WORKSPACE, SELECT_WORKSPACE, ACCOUNT_CONTEXT, LOGOUT`。入驻页只在账号上下文使用。
- **角色（D3）**：进入工作区不检查 `TENANT_OWNER` 授权。
- **审计**：新增动作 `CLIENT_WORKSPACE_SELECT`、`CLIENT_ACCOUNT_CONTEXT_SELECT`、`CLIENT_CONTEXT_FALLBACK`，Target 写新 Session ID 与撤销标记；不写邮箱等原值。

## 7. 接口

### 7.1 Platform（私网，`ClientAuthenticationApi`，前缀 `/platform-support/authentication/v1/client`）

| 操作 | Method / Path | 调用方 | 状态 |
|---|---|---|---|
| 我的工作区 | `GET /workspaces` | Client BFF | 新增 |
| 进入工作区 | `POST /workspace-context` | Client BFF | 新增 |
| 返回账号 | `POST /account-context` | Client BFF | 新增 |
| 刷新 | `POST /refresh` | Client BFF | 调整：租户分支与降级 |
| 查询上下文 | `GET /context` | Client BFF、CCT | 调整：租户分支重验 |

公共 Header：`X-Gateway-Actor-Subject`（账号 ID）、`X-Gateway-Session-Id`（会话 ID），由 BFF 从 Gateway 重建的身份头透传。

`GET /workspaces` 响应（不含 PENDING 租户，按 tenantId 升序，上限 50）：

```json
{"items":[{"memberId":"9100000000001","tenantId":"9100000000002","clientId":"123456789",
  "ownerStatus":"ACTIVE","securityStatus":"ACTIVE","memberStatus":"ACTIVE",
  "accessible":true,"reasonCode":null}]}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| memberId / tenantId / clientId | String（规范十进制） | clientId 即租户 `owner_ref_id` |
| ownerStatus | String | `ACTIVE` / `SUSPENDED` / `TERMINATED` |
| securityStatus | String | `ACTIVE` / `SUSPENDED` |
| memberStatus | String | 成员当前状态 |
| accessible | boolean | 三项都允许时为 true |
| reasonCode | String，可空 | 不可访问时取第一个原因：`TENANT_TERMINATED`、`TENANT_SUSPENDED`、`TENANT_SECURITY_SUSPENDED`、`MEMBER_INACTIVE`；运营填写的内部暂停理由不返回 |

`POST /workspace-context` 请求 `{"refreshToken":"<cookie 值>","memberId":"9100000000001"}`；`POST /account-context` 请求 `{"refreshToken":"…"}`。两者响应沿用 `AuthenticationResponseBody`（context + accessToken + expiresAt + refreshToken + refreshExpiresAt）。

`AccountContextResponseBody` 新增可空字段 `fallbackReason`（仅刷新降级时有值，取值同 reasonCode）。

| 错误码 | HTTP | 场景 |
|---|---|---|
| `CLIENT_WORKSPACE_UNAVAILABLE`（新增） | 403 | 成员不存在、不属于本账号、未激活，租户非 CLIENT、PENDING、暂停或终止 |
| `CLIENT_SESSION_INVALID`（已有） | 401 | Cookie 证明无效、Session 已撤销或过期、账号失效 |
| `ConcurrentRequestException`（已有） | 409 | 同账号并发切换 / 刷新冲突 |

### 7.2 CCT（私网，新增 `ClientWorkspaceApi`）

`GET /client-workspace/v1/client-summaries?clientId=…&clientId=…`（1—50 个，重复或非规范 ID 拒绝 400）。Header：`X-Caller-Service=client-console-bff`、`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；沿用现有 `authorize`（实时调 Platform `context`）。只返回“本账号是申请人且存在 APPROVED 申请”的 Client，其余静默省略。

```json
{"items":[{"clientId":"123456789","clientNo":"CL-123456789","clientType":"ORGANIZATION",
  "clientStatus":"ACTIVE","displayName":"Example Trading LLC","country":"AE",
  "businessModelCode":"DIGITAL_GOODS","approvedAt":"2026-10-16T08:00:00.000000Z"}]}
```

displayName：组织取 `legal_name`，个人取名 + 姓；country：组织注册国 / 个人居住国；approvedAt：最近一份 APPROVED 申请的结论时间。不返回证件号、地址、联系方式。

### 7.3 Client BFF（对外经 Gateway）

| 操作 | 对外 Method / Path | BFF 路径 | 状态 |
|---|---|---|---|
| 开始页（C01） | `GET /api/client/onboarding/v1/start` | `/client-onboarding/v1/start` | 调整：响应新增 `workspaces` |
| 进入工作区（C02） | `POST /api/client/auth/v1/workspace-context` | `/client-auth/v1/workspace-context`（**原登记的 `/client-onboarding/v1/…` 改为认证路径**） | 新实现 |
| 返回账号（W01） | `POST /api/client/auth/v1/account-context` | `/client-auth/v1/account-context` | 新增 |
| 工作区首页（W02） | `GET /api/client/workspace/v1/home` | `/client-workspace/v1/home` | 新增 |
| 刷新（A05） | `POST /api/client/auth/v1/refresh` | 不变 | 调整：透传 `fallbackReason` |

C01 新增字段（账号与租户上下文均可调用）：

```json
"workspaces":{"status":"AVAILABLE","items":[{"memberId":"9100000000001","clientId":"123456789",
  "clientNo":"CL-123456789","displayName":"Example Trading LLC","clientType":"ORGANIZATION",
  "state":"ACCESSIBLE","reasonText":null,"current":false}]}
```

- `status`：`AVAILABLE` / `UNAVAILABLE`（Platform 或 CCT 失败时只标记不可用，不影响入驻申请部分）。
- `state`：`ACCESSIBLE` / `RESTRICTED`；`reasonText` 由 BFF 把原因码映射为公开文案（如“工作区已暂停”“工作区已终止”“工作区暂时不可访问”“您的成员资格已失效”）。
- CCT 没返回名称时 `displayName` 为空，Web 显示客户编号（Q3）。
- `current`：租户上下文下标记当前工作区。

C02 请求 `{"memberId":"…"}`，W01 请求 `{}`；响应与登录相同（`context`、`accessToken`、`expiresAt`），并 `Set-Cookie` 替换 `__Secure-client-refresh`。

W02：要求 `X-Gateway-Context-Type=TENANT`，从 `X-Gateway-Tenant-Id`、`X-Gateway-Tenant-Member-Id` 取值，先调 Platform `context` 重验并取得当前成员对应的 clientId（不信任浏览器），再调 CCT 摘要。账号上下文调用返回 403 `CLIENT_WORKSPACE_CONTEXT_REQUIRED`（新增）。响应：

```json
{"workspace":{"clientId":"123456789","clientNo":"CL-123456789","displayName":"Example Trading LLC",
  "clientType":"ORGANIZATION","clientStatus":"ACTIVE"},
 "profile":{"country":"AE","businessModelCode":"DIGITAL_GOODS","approvedAt":"2026-10-16T08:00:00.000000Z"},
 "notice":"PAYMENTS_NOT_YET_ENABLED"}
```

日志：新路径加入 metadata-only 前缀。

## 8. 数据库与注册数据

- 业务 Schema DDL：**0**（`t_auth_session` 已有 `context_type`、`tenant_id`、`member_id` 列）。
- 业务 DML：**0**。
- Gateway 路由（经 Platform 管理接口登记，更新入驻目录的登记计划与脚本，不生成 SQL）：

| 路由 | 变化 |
|---|---|
| C02 `POST /api/client/auth/v1/workspace-context` | 修改下游路径为 `/client-auth/v1/workspace-context` |
| A05 `POST /api/client/auth/v1/refresh` | 响应 Cookie 模式 `ISSUE_ONLY` → `ISSUE_OR_CLEAR`（D5：刷新失败清 Cookie 不再被判非法） |
| W01 `POST /api/client/auth/v1/account-context` | 新增：`CLIENT_JWT`、`HUMAN_AUTHENTICATED`、请求 `REQUIRE_PROFILE_COOKIE`、响应 `ISSUE_ONLY` |
| W02 `GET /api/client/workspace/v1/home` | 新增：`CLIENT_JWT`、`HUMAN_AUTHENTICATED`、Cookie `DROP` / `NONE` |

本机与 mor-test 的实际登记属于发布动作，在阶段 6 / 7 另行授权。

## 9. 页面

参照入驻设计稿 CC-P07 / CC-P11（`client-console-web/design-previews/lZPz3.png`、`V5yMxR.png`、`UF6gv.png`），去掉超出本期的“客户资料”“查看已审核资料”菜单。

```
开始页（账号上下文，现有顶栏不变）
┌ 我的工作区 ───────────────────────────────┐
│ Example Trading LLC  组织 · CL-123456789  [进入工作区] │
│ Old Shop             个人 · CL-987654321  已暂停：工作区已暂停 │
│ （无工作区时：审核通过后，您的工作区会显示在这里）           │
└──────────────────────────────────────────┘
┌ 我的入驻申请（现有） ┐

/workspace（租户上下文，新侧边栏外壳）
┌──────────┬──────────────────────────────────┐
│ 工作区名称 │  Example Trading LLC   组织 · CL-…   状态：已启用 │
│ ▸ 工作区首页│  ⓘ 进入工作区不代表已开通收款                        │
│          │  注册国家 / 经营模式 / 审核通过时间                    │
│ 切换工作区 │                                                  │
│ 返回账号   │                                                  │
│ 退出      │                                                  │
└──────────┴──────────────────────────────────┘
```

- 进入 / 切换 / 返回按钮处理中禁用，失败留在原页并重新读取列表（UI-03、AC-07）。
- 刷新得到 `fallbackReason` 时跳到开始页，顶部提示原因（F-07）。
- 入驻结果页：审核通过时读取 C01 的工作区；有可访问工作区则显示“进入工作区”，否则保持“正在开通”（F-06）。
- 390px 下侧边栏收为顶部菜单按钮（UI-04）。

## 10. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证入口 |
|---|---|---|---|
| 1 | Platform | 会话写入支持租户上下文；成员查询端口；`GET /workspaces`；C02、W01 Processor；`ClientAccountPolicy` 租户分支与刷新降级；错误码、审计；首批 Client 认证 auto 夹具 | HTTP 入口 auto：登录→进入→刷新→暂停后降级→返回账号；他人成员 / PENDING / 暂停 → 403；并发切换 409 |
| 2 | CCT | `ClientWorkspaceApi` 与只读查询；APPROVED 夹具 | HTTP 入口 auto：本人 APPROVED 返回、他人 / 未批准静默省略、参数校验 |
| 3 | Client BFF | C02、W01、W02 端点；C01 聚合；原因文案映射；日志前缀 | 现有 loopback 上游 Mock 风格的 auto：Cookie 替换、上下文校验、部分不可用、fallbackReason 透传 |
| 4 | Client Web | `auth.ts` 选择 / 返回上下文与降级处理；开始页工作区区域；`/workspace` 外壳与首页；结果页入口 | `node --test` 单元测试 + `tsc && vite build`；浏览器验收（桌面 / 390px） |
| 5 | 登记计划 | 更新 `complete_registration_plan.py` 与本机同步脚本 | 生成结果差异核对 |

验收映射：AC-01/02/03/10 → Platform `GET /workspaces` + BFF C01 + Web 开始页；AC-04/05 → C02；AC-06 → 刷新降级与 A09；AC-07/08 → C02、W01、W02 上下文校验；AC-09 → W02；AC-11 → Token Claim 与日志前缀；UI-01—04 → Web 与浏览器验收。

兼容：旧 Web 不调用新接口，不受影响；BFF 先于 Web 发布，Platform 先于 BFF。`fallbackReason` 为新增可空字段，旧 Web 的严格校验需要同步放宽（同批发布 Web）。
