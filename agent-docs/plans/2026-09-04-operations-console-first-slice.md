# Operations Console 第一版实施记录

日期：2026-09-04。用户确认从登录、权限菜单、内部人员管理与审计开始，并明确“Vault 现在还在写，代码中先 Mock TODO 一下”。这份授权允许本次实现与隔离开发适配器；不代表生产基础设施、密钥供应商、手工数据库变更或部署已经完成。

## 同日后续决定：公共 HTTP 客户端异常处理（当前）

用户要求异常处理公共化，只有特殊转换保留业务侧。新增 focused `paytech-http-client-support`，通过 Spring HTTP 代理扩展统一处理公共业务错误、网络故障、HTTP 状态、错误体解析、解码失败、重定向和空 DTO。BFF 配置只注册三个既有 Operations 特殊码及三个单例 API Bean；`PlatformOperationsClientImpl` 删除 `query/command/invoke` 与状态/错误体解析，只保留 DTO 转换、授权结果及字段契约校验。共享 MVC 仍唯一生成外部错误体，公共 `UPSTREAM_INVALID_RESPONSE` 固定 502。没有扩大到 Gateway 的响应式协议或 Platform 的事务审计/认证恢复捕获。

验证：Foundation Common 14、HTTP Client 43、MVC 86 项（共 143 项）及 BFF 134 项测试全部通过；Build/BOM 与 Foundation 已安装本机。已仅更新 mor-test BFF，真实登录、授权查询、参数/业务提示、Refresh 轮换、退出与撤销后拒绝烟测通过。未修改数据库和现有运行凭据；测试环境构建脚本已包含新增公共模块。

## 同日后续决定：公共异常与业务消息

用户要求仅保留少量公共异常，普通业务拒绝使用业务检查异常加具体消息，只有调用方明确需要区别处理才定义错误码。Foundation 复用 `BizCheckFailException` / `ErrorException`，补充公共参数与并发异常，依赖不可用不再要求传入业务错误枚举。Operations 业务错误码从 12 项收敛至登录失败、会话失效、权限拒绝 3 项；密码策略、账号限制等使用公共业务消息，版本与幂等冲突使用公共并发类别。

BFF 对公共业务、参数、并发的 4xx 错误保留安全消息，前端直接展示；系统错误与未知协议消息仍使用安全默认提示。共享 MVC 是唯一异常响应出口，事务、审计、Refresh 轮换与自动重试规则不变。后续编码遵循 `DEVELOPMENT_CONVENTIONS.md` 第 4 节；下文“远端消息一律不透传”和大量错误枚举描述为历史。

验证结果：Foundation 99 项、BFF 134 项、Platform 18 项相关测试和前端 19 项测试全部通过，Java 构件与前端构建成功。Platform 全部测试源码编译通过；3 项本地数据库 HTTP 场景因缺少测试数据库密码未运行，不计入通过数。已重建并更新 mor-test 的 Platform/BFF/前端；真实 HTTP 登录、授权查询、公共参数错误、同密码业务提示、Refresh 轮换、退出及撤销后拒绝均通过。未执行 DDL、初始化 DML 或 Bootstrap 重置。

## 同日后续决定：精简转发层与 JSON Refresh

用户再次审查后要求移除没有实际编排职责的 `OperationsManagementProcessor`，并把 Refresh Token 改为普通请求。当前实现删除管理/认证两个重复转发 Processor、Refresh Cookie Helper 和仅用于隐藏 Refresh 的响应转换。BFF 在统一 MVC 入站拦截器执行 Origin/JSON、可信 Header 与 PLT-I02，只豁免精确 POST Login/Refresh；Controller 直接委托本地 Client Port，ArgumentResolver 只读取已授权的请求级上下文。参数非法可先经过一次授权，但不调用业务接口。

Operations 登录/刷新 JSON 返回 Access/Refresh Token；Refresh 和 Logout 均从 JSON `{refreshToken}` 取值，Logout 仍需 Access JWT。Gateway 目录使用现有 `request_cookie_mode=DROP`、`response_cookie_mode=NONE`，不传 Cookie、不接受 Set-Cookie；不新增 Cookie 模式或表。Operations 不再要求 CSRF Header，仍检查精确 Origin 和 JSON。前端使用 `credentials: omit`，两 Token 只保存在内存，页面刷新和新标签页需重新登录；保留单飞刷新及会话版本竞态保护，移除共享 Cookie 的跨标签页锁/广播。Platform HMAC、轮换事务、重用撤销、期限与身份授权规则不变。

本轮验证：BFF JDK 25 `clean verify` 131 项全通过，前端 15 项协议/竞态测试及生产构建通过；Gateway 14 个相关测试类的 331 项和 Platform 目录编译器 17 项通过。Gateway 测试修正了本地 Simple Discovery 总开关、旧静态目录期望与 Cookie 指标断言，不改变生产行为。已更新 mor-test 的 BFF/前端和手工路由增量，真实 JSON 登录、授权查询、Refresh 轮换、退出及撤销后 Refresh 401 烟测通过；无 Cookie 响应。Gateway/Platform 沿用现有运行代码。

这是用户当前要求对统一认证设计 11.3/17.6 节 **Operations 浏览器传输** 的后继决定；Client/Partner 的旧 Cookie 设计不受影响。下文 Cookie、双 Processor 与跨标签页锁描述保留历史实施过程，不再代表当前实现。

## 同日 test 部署与 HTTP 增量

用户随后授权创建独立 `test-environment` 并部署前后端，进一步明确“测试环境不需要 https”。当前入口调整为 `http://localhost:28080`，无证书依赖；只有 Gateway/BFF 显式 `OPERATIONS_TEST_HTTP_ENABLED=true`、Active Profiles 恰好为 `test`、Origin 为规范 `http://localhost:<端口>` 时允许 HTTP。专用 `operations-refresh-test` Cookie 不设置 Secure，仍要求 HttpOnly、SameSite Strict、固定 Path、无 Domain；默认 HTTPS 模式不变，Origin/CSRF/JWT/PLT-I02 校验不变。

`mor-test` 使用独立数据库、Redis、Nacos、网络与私密材料，已显式完成 Schema/目录/密钥/Bootstrap 初始化及真实登录、改密、首管理员、IAM 查询、Refresh 和退出链路。HTTP 切换保留这些运行事实，不重新初始化业务数据；运行指令与最新烟测见 [`test-environment/README.md`](../../test-environment/README.md)。下文未部署或必须 HTTPS 的措辞保留首期实施时点，受此增量更新。

## 同日 BFF 代码质量重构

用户审查后要求纠正常量、异常捕获和层级混杂。原 `OperationsBrowserBoundary` 把配置、入站协议、授权、Cookie 和错误体构造放在一起，已拆为 MVC 入站防护/身份转换、两个按需 Processor、只使用本地模型的 Platform Client 端口与 HTTP 实现。Controller 不再注入 Platform API 或返回 `ResponseEntity<?>`，Processor 不接触 Servlet/HTTP 类型；真实授权先于单个 Platform 操作，没有增加业务领域事实或空持久化层。

Cookie 保留统一认证设计第 11.3 节的既定职责，仅在 `OperationsRefreshCookie` 中处理；HTTP test 例外不变。Platform 错误复用公开枚举，BFF 协议错误有独立 `CodeMessageEnum`。移除私有异常和请求级通用 catch；只在出站 HTTP 边界转换并保留 Cause，Refresh 凭据失效清 Cookie 后原样抛出。Foundation 增加不含业务码的 `ApiErrorStatusResolver`，使浏览器状态可配置而仍由共享处理器唯一生成错误体。空授权响应改为 502，未知上游错误码拒绝透传，5xx 不因正文伪装成 401。

此次重构的 BFF `clean verify` 共 99 项通过；Foundation MVC 80 项和 Common Core 10 项通过并安装到本机 Maven。退出请求通过入站验证后即使 PLT-I02 拒绝也清理本地 Cookie，但保留原 HTTP 错误且不宣称 Platform Session 已撤销；该语义有独立 HTTP 回归用例。

## 交付范围与所有权

1. `operations-console-web`：React/TypeScript/Vite/Ant Design 独立仓库；登录、强制改密、首管理员创建、授权菜单、人员列表/创建/角色分配/停用、审计分页。
2. `operations-console-bff`：无状态私网 HTTP 适配；精确 Origin、JSON/CSRF Header、单一 Refresh Cookie、Gateway 可信身份、每次 PLT-I02 授权、类型化 Platform HTTP Client；不保存业务事实。
3. `platform-support-service`：Operations 认证/Session/Refresh、实时 IAM、Console Resource、人员命令与审计；只在明确配置下启用。沿用当前批准的模型和手工 SQL，不恢复启动 Seed。
4. `api-gateway`：沿用已经实现的动态 Snapshot；批准四个 BFF 下游前缀，验证 TENANT/INTERNAL 与 BOOTSTRAP Claim 矩阵，重建全部可信 Header。所有业务 Route 由 Platform 当前 Catalog 提供。
5. `paytech-foundation`：共享 MVC 增加默认空的 `web.logging.metadata-only-path-prefixes`。受保护请求在缓存前分流，普通日志绝不读取/缓存 Body、Query、Header、Cookie；错误与未知编码路径保守处理。普通接口既有行为保持。
6. `paytech-build`：统一管理 Argon2id 所需 Bouncy Castle 版本，服务不另定版本。

## 本次明确的协议决策

- Browser 只访问同源 `/api/operations`，公网/本地联调 TLS 由 Nginx 终止；Vite HTTP 地址只用于页面开发。Access Token 只在内存，Refresh 只在 BFF 的 HttpOnly Cookie。
- CSRF 使用精确 Origin + JSON + 非简单 `X-CSRF-Token` Header，不引入第二个 Cookie 或 BFF Session。Login 不发送该 Header；同源 GET 可缺 Origin，但仍需要 JWT 和当前权限，显式 Origin 始终严格检查。
- Login/Refresh/Logout/改密/首管理员创建共用浏览器跨标签页 Web Lock；只广播失效通知，不共享 Token。Web Locks/BroadcastChannel 缺失时拒绝认证，不采用危险的每标签页独立轮换。会话版本变化后丢弃迟到结果。
- Platform 仍使用共享错误类型；BFF 只对明确 Operations Code 做浏览器状态适配，不更改 Foundation 为某一业务服务识别 Code。
- 前端组件只接受 `OPERATIONS_ACCOUNTS → /accounts` 与 `OPERATIONS_AUDIT → /audit`。按钮使用 `OPERATIONS_ACCOUNT_CREATE / DISABLE / ASSIGN_ROLES`；导航不是服务端权限的替代。
- 普通人员强制改密仍绑定 INTERNAL Tenant；Bootstrap 使用不含 Tenant 的 BOOTSTRAP Context。Platform 按当前状态限制可用操作。
- 初始密码从管理员输入，不在响应中回显；创建账号、分配角色、停用都记录原因、版本和幂等键。未增加自助注册、邮箱验证、邮件 Reset、CUSTOMER、角色编辑或其他业务系统页面。

## Vault、签名与数据库前置

Vault 按明确用户要求保留开发 Mock 和 TODO 接入点，生产路径不能静默回退到 Mock。KMS/Secrets 供应商仍未选定，本地签名仅用于显式开发/测试环境；实际私钥/HMAC 必须外部注入并与数据库有效公钥匹配。

业务 Schema/初始化 DML 始终由操作员手工执行。运行环境的 Bootstrap Credential 和 ACTIVE Signing Key 不由 Java 启动或测试代为初始化；初始化必须保留 Bootstrap 永久终态，不能每次重建。数据库测试只允许在没有人工 Account/ACTIVE Key 的已准备目录中建立并清理专属测试 Fixture，不覆盖运行数据。原设计中的生产 Vault/KMS、密钥生命周期和敏感边界继续有效。

## 验证与限制

- 前端严格类型检查与生产构建；真实 Node loopback HTTP 验证 Refresh 单飞、Cookie 变更串行、旧查询丢弃、错误内容保护、写命令不自动重试。
- Foundation 新日志保护与原行为的聚焦测试。
- Gateway JWT/Trusted Header/Snapshot/Origin/Cookie 的聚焦测试及边界脚本。
- BFF 真实 HTTP 入口到隔离 Platform 协议 Simulator 的验证。
- Platform 密码、开发适配器、真实 RSA 签名、配置与契约测试；真实数据库命令链另受手工 Schema 和 Secret 注入前置约束。

本次已通过的聚焦验证：前端 5 项 HTTP 会话测试及 TypeScript/Vite 构建；BFF 46 项 HTTP/协议测试及最终 Platform API 下的 Maven package；Foundation MVC 59 项、Common Core 10 项；Gateway 58 项聚焦测试及另外 14 项 Origin/Cookie 测试、边界脚本与构建；Platform 42 项纯策略/签名/Mapper/依赖/配置回归及 API/Web clean install。上述是各边界的验证结果，不是完整仓库 `verify` 或数据库全链路覆盖率结果。

Platform 另新增 3 项真实 HTTP/MyBatis 场景及专属 Builder/Mocker/TestMapper/Checker，全部 19 个测试源文件编译通过。实际触发一个数据库用例时因缺少测试密码在数据源启动前明确失败，并给出本地 MySQL/手工 SQL 准备提示；三项数据库业务链路尚未执行通过。

当前进程没有 MySQL/Redis 测试密码；没有读取 `.env.local`、启动/重建容器、执行 Schema/DML 或部署。不能将上述隔离测试等同于真实平台登录闭环验收。最新具体结果与运行指令以各独立仓库 README 为准。
