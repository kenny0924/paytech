# Client 入驻：路由、菜单与权限注册

> **2026-09-15 用户决定：** 本期继续注册页填写邮箱和密码、邮件链接验证后登录；密码与邮箱所有权绑定的进一步优化延至对外开放前，详见[决定记录](./registration-password-binding-amendment.md)。

阶段 3 评审稿。采用现有 Platform 配置管理命令，配置 ID 由 Platform 生成。这里定义可审阅的请求模板与执行顺序，不连接环境、不创建配置、不授予人员权限。现行 API 依据为 [Gateway 管理合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/gatewayconfiguration/management/GatewayConfigurationManagementApi.java)、[Permission 管理合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/permissionmanagement/PermissionManagementApi.java)及 [Console Resource 管理合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/consolemanagement/ConsoleResourceManagementApi.java)。

## 1. 注册对象和页面入口

[注册清单](./db/registration-plan.json)包含 37 条公共路由、5 项运营权限、1 个运营菜单及 4 个按钮。3 个 CCT→Risk 内部操作不经过公共 Gateway。Client BFF 使用 CLIENT Target；Operations 复用已有 OPERATIONS Target，不修改其状态或服务发现名。

Client 入驻处于 Account Context，没有普通 Tenant 权限。`/onboarding`、`/onboarding/progress` 采用 Web 固定页面入口，由 CCT 的本人申请授权与 allowedActions 决定实际操作。它们不写入需要 permissionId 且当前只管理 OPERATIONS 的 Console Resource 接口。此设计取代上一稿的两个无权限 Client 菜单占位，不改变进入多个已有工作区的 IAM。

Operations 菜单组件为 `OPERATIONS_CLIENT_ONBOARDING_REVIEW`，路径 `/client-onboarding/reviews`。注册前须先在 Platform 的部署能力清单和 Operations Web 的组件/动作映射实现它；未知组件不能靠插入数据变成可用页面。菜单与按钮编码、权限映射见清单；人员检查和材料检查共同使用核验权限，行业/MCC 保存及确认共同使用分类权限，批准和最终拒绝共同使用决定权限。所有 Owner 仍逐次授权，隐藏按钮不替代权限校验。

## 2. 修正后的 HTTP 注册合同

- C02 的外部路径固定为 `/api/client/auth/v1/workspace-context`，下游仍为 `/client-onboarding/v1/workspace-context`。Refresh Cookie 名 `__Secure-client-refresh`，HttpOnly、Secure、SameSite=Lax、host-only、Path=`/api/client/auth/v1`。A05、A06、C02 都处于该路径下；写入和清理必须使用同名、同域、同 Path。A08 仅清 Cookie，不要求携带有效旧 Cookie。
- 路由响应上限改为 10,000ms，正文接收上限 5,000ms，正文 16KiB/64KiB，均不超过现有 Platform 编译器硬上限。此修订替代旧清单中不可注册的 15,000ms。超时不表示业务失败，写命令重试使用原身份。
- 白名单显式列出 Content-Type、必要的 Idempotency-Key 和 Origin。Authorization 仅允许 CORS 声明，由 Gateway 消费，不普通转发；Cookie 使用专用模式，不加入普通 Header 白名单。可信账号、Tenant、代理和 Request ID Header 不能由浏览器声明或透传。
- C03 仅允许 clientType/countryCode；C15 仅 cursor/limit；O01 仅 cursor/limit/status/applicationNo；其他操作不接收查询参数。邮件链接 secret 由页面 POST Body 提交，不能当作 API Query 转发。
- 37 条路由均包含成对的 Rate/Concurrency Guard。公开认证使用 SHARED_ROUTE + CLIENT_IP；认证请求还使用 AUTHENTICATED_PRINCIPAL。Account Context 没有 Tenant，不能要求 TENANT 维度。初始阈值写在清单内，发布前可在硬上限内按实际容量审核；邮件发送仍受 Platform 用途/账号冷却限制。
- 修改请求由 Gateway 执行已批准的 Origin 策略；BFF 对需要 Cookie 的写入核对固定部署 Origin，浏览器不能自选回调域。无需引入本期未设计的 OTP 或第三方登录流程。

## 3. 可审阅的配置命令

[48 个命令模板](./db/management-command-templates.json)包括 1 Target、5 Permission、37 Route、5 Resource 的创建 Body。`bindings` 是执行前解析说明，不属于 HTTP Body；有待绑定 ID 的 null 不是可发送的请求。模板不包含真实账号、会话、凭据或环境 URL。

1. 在具体环境只读检查 Capabilities、同 Code 的现有配置和 Operations Target；Client 的 discoveryServiceName 指向业务监听，不指向管理监听。若同 Code 已存在且字段一致，记录复用；存在差异时列出准确更新及 expectedVersion，不自动覆盖。
2. 按需创建 Client Target，服务固定创建为 DISABLED。先创建 5 项 Permission，服务固定创建为 INACTIVE；通过 GET 按 permissionCode 读取真实 permissionId/current version。
3. 需要引用权限时，使用已有权限状态命令启用准确权限，仍不关联任何真实角色。注册 Route 时将 permissionCode 解析为该 ID；内部 Body 不接收 permissionCode。每个配置命令使用独立 Idempotency-Key，超时原键重试。
4. 部署组件能力后先创建菜单，再按返回的 resourceId 绑定 4 个按钮。资源创建保持 DISABLED；不能传任意 frontendRoute 或跨 Channel 父节点。
5. 回读核对所有父子、权限、路径、Header、Query、Guard 和状态；调用同一编译器校验候选完整 Snapshot。此时还不能声称 Gateway 已接收或页面已可见。
6. 业务验收通过后，在阶段 6 发布清单中列明准确角色、最小权限授予、目标 Origin、CLIENT 认证/JWKS/Cookie 能力、实例和启用顺序。取得该环境授权后才启用 Client Target、新路由和运营菜单/按钮，并核对 Gateway Snapshot 与真实入口。既有 Operations Target 不停用。

管理接口路径均为 Owner 私网路径，应经已有 Operations BFF 和真实管理会话调用；不得伪造 X-Gateway-* 身份或为脚本直连暴露新公共管理入口。权限目录创建不等于创建者获得权限，也不自动授予所有运营人员。

## 4. 阶段边界

注册方式、字段及依赖已定稿为评审方案；数据库 ID 和真实环境状态是阶段 6 的绑定输入，不再视为业务设计未决问题。该路径无需另造固定 ID 初始化 DML，避免与既有 Owner 管理命令竞争。若目标环境不能使用管理 API，必须另交该环境完整人工 SQL 及备份方案，不能临时拼接 INSERT。

当前执行情况只见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。生成器 [complete_registration_plan.py](./db/complete_registration_plan.py)只生成本目录评审 JSON，不执行注册。
