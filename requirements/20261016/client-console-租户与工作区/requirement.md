# Client Console 租户与工作区 — 需求

> 状态：2026-10-16 阶段 1 用户确认（“按建议”：Q1—Q5 均按建议），进入阶段 2。确认记录见 [delivery.md](./delivery.md)。

## 1. 来源与背景

- 来源：用户 2026-10-16“开始租户和工作区”。Client Console 目前只有注册、登录和入驻申请；入驻审核通过后页面只显示“客户工作区正在开通”，客户无法进入工作区（[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914) 5d 增量的未完成项）。交易、退款、余额、团队、API 凭证等后续页面都需要先有“当前工作区”上下文。
- 已有权威约定（本需求不重新定义，只落地）：
  - [统一人类认证设计](../../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)：CLIENT Channel 有账号上下文与 CLIENT 租户上下文两种 Token；租户上下文携带 tenant_id、tenant_type、tenant_member_id；切换新建 Session、撤销旧 Refresh Family；PENDING、被拒或状态缺失的 Tenant 不签发租户 Token；浏览器不能指定 Tenant ID；Gateway 只校验最小 Claim，成员与授权由 Platform 判定。
  - 需求目录 PLT-I16（选择账号或单一 Member 上下文）、PLT-I21（按 Channel/Account/Member 返回菜单）；全局时序“用户选择已激活的 Client Tenant，经 Gateway、BFF 调 PLT-I16”。
  - 入驻需求的 C02 合同 `POST /api/client/auth/v1/workspace-context`（[api-contract](../../20260914/client-console-注册与客户入驻/api-contract.md)，请求只传 memberId）与工作区页面状态（[ui-design](../../20260914/client-console-注册与客户入驻/ui-design.md)：开通中 / 可访问 / 受限）。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 已通过入驻审核的客户（CUSTOMER 账号，工作区 OWNER） | 登录后看到自己的工作区，进入工作区，看到工作区首页；能回到账号页继续查看入驻申请 |
| 后续 Client Console 功能 | 有统一的“当前工作区”上下文和页面外壳，新页面直接挂到导航下 |

## 3. 本期范围

| 编号 | 功能 | 说明 |
|---|---|---|
| F-01 | 我的工作区列表 | 账号上下文下列出本账号作为成员的 Client 工作区，显示名称、客户编号、类型和状态（可访问 / 开通中 / 受限及公开原因）；与入驻申请列表同页（C01 开始页） |
| F-02 | 进入工作区 | 选择一个可访问工作区，Platform 校验 Tenant 来源状态与安全状态均为 ACTIVE、Member 为 ACTIVE 且属于本账号后，新建租户上下文 Session，撤销原账号上下文的 Refresh Family，签发租户 Token，替换 Refresh Cookie（PLT-I16 / C02） |
| F-03 | 返回账号 / 切换工作区 | 在工作区内可回到账号上下文（同样新建 Session）；有多个工作区时经列表切换到另一个 |
| F-04 | 工作区首页 | 显示当前客户名称、类型、客户编号、工作区状态和已批准的基础资料摘要；明确提示“进入工作区不代表已开通收款”；不显示付款、余额、邀请或收款状态 |
| F-05 | 工作区页面外壳与导航 | 工作区内统一的顶部/侧边外壳：当前工作区名称、工作区切换入口、返回账号、退出；本期菜单只有“工作区首页” |
| F-06 | 入驻结果页入口 | 审核通过且工作区已可访问时，入驻结果提示提供“进入工作区”；仍在开通时保持“正在开通”且不可强行进入 |
| F-07 | 上下文失效处理 | 租户上下文下刷新或访问时，若工作区被暂停、终止或成员被撤销，失败关闭：回到账号上下文的工作区列表并显示受限原因，不能继续使用旧租户上下文 |

## 4. 本期不做

- 团队成员管理与邀请（PLT-15、PLT-I18）、角色与权限配置。
- 交易、退款、余额、Payout、API 凭证、Webhook 等业务页面。
- Platform 驱动的 CLIENT 菜单（PLT-I21）与 Console Resource 注册（见待决问题 Q1）。
- Gateway 对 CLIENT 路由的权限码校验（`HUMAN_PERMISSION`）与新的安全档案；本期工作区接口由 BFF 校验上下文类型。
- Google 登录、OTP、“记住我”。
- Operations 侧工作区管理（已有只读查询，不改）。

## 5. 验收标准

### 功能验收

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | 账号下没有任何工作区 | 开始页工作区区域显示空状态，入驻申请功能不受影响 |
| AC-02 | 审核通过、工作区已 ACTIVE | 工作区列表显示“可访问”；入驻结果页显示“进入工作区” |
| AC-03 | 审核通过、Platform 投影尚未完成 | 显示“开通中”，没有进入按钮；调用 C02 也被拒绝 |
| AC-04 | 进入工作区 | 返回租户上下文 Token（含 tenant_id、tenant_type=CLIENT、tenant_member_id），Refresh Cookie 被替换；旧账号上下文的 Refresh 不能再刷新 |
| AC-05 | 浏览器提交不属于本账号、或不存在的 memberId | 拒绝，且不泄露该成员或租户是否存在 |
| AC-06 | Tenant 来源状态或安全状态为 SUSPENDED/TERMINATED、Member 被撤销 | 列表显示“受限”及可公开原因；C02 拒绝；已在工作区内的用户下次刷新失败并回到列表 |
| AC-07 | 多个工作区 | 列表逐项显示；在工作区内可切换到另一个，切换后首页显示新工作区；切换失败留在列表并重新读取 |
| AC-08 | 返回账号 | 回到账号上下文，可继续查看入驻申请；工作区接口在账号上下文下被拒绝 |
| AC-09 | 工作区首页内容 | 显示已批准资料摘要，不含付款、余额、邀请、收款状态 |
| AC-10 | 拒绝或进行中的入驻申请对应的工作区 | 不作为可访问工作区出现（见 Q2） |
| AC-11 | 安全 | Token 不含 Email、全部 Tenant、Role、Permission；工作区接口日志不记录敏感资料原值 |

### 视觉 / 交互验收

| 编号 | 期望 |
|---|---|
| UI-01 | 开始页同时有“我的工作区”和“我的入驻申请”两个区域，状态用文字和颜色区分，受限项显示原因 |
| UI-02 | 工作区外壳始终显示当前工作区名称与身份；切换入口、返回账号、退出位置固定 |
| UI-03 | 进入、切换过程中按钮有处理中状态，防止重复点击；失败有明确提示 |
| UI-04 | 桌面与 390px 宽度下布局可用，无横向滚动 |

## 6. 已确认事实与假设

- 已确认（文档）：PENDING Tenant 不签发租户 Token；浏览器只提交 memberId，Platform 从实际 Member 解析 Tenant；切换总是新建 Session；Gateway 不做角色权限计算。
- 已确认（代码）：Token 结构与 Gateway 校验已支持租户上下文 Claims；Platform 只签发账号上下文（`ClientAccountPolicy` 拒绝租户上下文会话）；C02 路由已登记但 DISABLED；Operations 有可参照的租户会话与导航实现。
- 假设：本期每个工作区只有申请人一名 OWNER 成员（没有邀请）；工作区显示名称取 CCT 已批准资料中的法定名称/姓名，而不是 Platform 的 `Client {ownerRefId}` 标签（见 Q3）。

## 7. 待决问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 工作区导航：本期在 Web 写死菜单，还是同时实现 Platform 的 CLIENT 菜单查询（PLT-I21）并注册 CLIENT Console Resource？ | 本期 Web 写死（只有一个页面，没有按权限隐藏的需要）；第一个需要按权限显示的页面上线时再接 PLT-I21 |
| Q2 | 入驻进行中或被拒的申请也有一个 PENDING 工作区（开通时创建）。工作区列表是否显示它们？ | 不显示：工作区列表只列来源状态已非 PENDING 的工作区；审核通过但投影未完成的“开通中”由入驻结果页表达 |
| Q3 | 工作区显示名称用什么？ | BFF 从 CCT 读取已批准的法定名称（组织）或姓名（个人）；读取失败时显示客户编号 |
| Q4 | 只有一个可访问工作区时，登录后是否自动进入？ | 不自动进入，停在开始页并突出“进入工作区”；避免每次登录都新建租户 Session，也保留查看入驻申请的入口 |
| Q5 | 文档冲突：Refresh Cookie 的 SameSite，认证设计写 Strict，入驻合同和现有实现为 Lax | 本期保持现有实现，不改；冲突记录到技术方案，另行收口 |

## 8. 适用阶段

建议走完整 8 个阶段；阶段 3 需要确认接口、页面和 Gateway 路由注册（预计无业务表 DDL，有路由注册数据）。
