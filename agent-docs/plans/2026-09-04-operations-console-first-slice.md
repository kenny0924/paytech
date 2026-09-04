# Operations Console 第一版实施记录

日期：2026-09-04。用户确认从登录、权限菜单、内部人员管理与审计开始，并明确“Vault 现在还在写，代码中先 Mock TODO 一下”。这份授权允许本次实现与隔离开发适配器；不代表生产基础设施、密钥供应商、手工数据库变更或部署已经完成。

## 同日代码提交

菜单层级、刷新恢复、公共分页及 HTTP DTO / Models 整理已按用户要求分别提交：

- `paytech-foundation`：`37cf854`（`main`）
- `platform-support-service`：`f200748`（`main`）
- `operations-console-bff`：`83e34a3`（`codex/operations-bff-foundation`）
- `operations-console-web`：`8cd8fe0`（`codex/operations-console-first-slice`）

本次为本地 Git 提交，未推送或重新部署。下文保留各功能增量的实际验证与部署边界。

## 同日后续调整：HTTP Models 与 API 分离

用户要求将 API 接口内嵌的 DTO 提取到按业务分组的独立 `*Models`。
Platform 的认证、授权、导航和人员管理模型分别放入 `AuthenticationModels`、`AuthorizationModels`、
`NavigationModels`、`ManagementModels`，仍属于 `platform-support-api` 模块及各自业务包。
容器使用 `public final` 与私有构造器，DTO 使用 `public record`，保留已统一的 Body 后缀、字段校验和敏感输出约束。
三个 Operations API 只声明 HTTP 方法契约；提供方实现、BFF 适配器和测试同步引用新的模型位置。
BFF 的本地 `service/model/*Models` 继续承担页面模型边界，JSON、HTTP 与业务行为保持不变。
API 干净构建后重新安装本机 Maven 缓存，BFF `clean verify` 155 项、Platform 18 项聚焦测试通过；
已有命名测试同步约束 API 接口不再声明内嵌类型。Platform 数据库全量 auto 仍缺本机 local 凭据，未执行。
本次结构调整已提交，未重新部署。

## 同日后续修正：现有 Operations HTTP DTO 命名

前次只改公共分页类型与规范，遗漏现有 Operations 接口 DTO。本次将 BFF 的认证、会话、导航、人员接口，
以及 Platform 对应的认证、授权、人员 HTTP 契约及其全部调用处统一为 `*RequestBody` / `*ResponseBody`。
例如登录现在接收 `LoginRequestBody`、返回 `TokenGrantResponseBody`，刷新接收 `RefreshRequestBody`；
会话和导航分别返回 `SessionContextResponseBody`、`NavigationResponseBody`。
人员创建返回 `AccountResponseBody`，人员分页复用同一类型作为元素；内部 `OperatorContext`、领域会话对象及
仅作嵌套的角色、菜单资源、审计条目继续保留业务名称。

改动保持 JSON 字段、HTTP 方法、状态码、参数位置、校验、敏感对象的 REDACTED 输出及业务处理不变。
`OperationsHttpBodyNamingAutoTest` 检查两个 BFF Controller 与三个 Platform Operations API 的实际 HTTP 方法，
约束请求 Body 类型及非 void 顶层响应的后缀。Platform API 已重新编译安装，本次 BFF `clean verify` 155 项、
Platform 18 项聚焦测试通过。前端的 Token、会话、导航响应和分页查询类型同步新名称，
40 项测试及 TypeScript/Vite 生产构建通过。Platform 数据库全量 auto 仍缺本机 local 凭据，未执行。
本次命名调整已提交、未重新部署；下文的 mor-test 部署记录对应此前功能增量。

## 同日后续决定：公共分页复用（当前）

分页请求与响应统一复用 Foundation `paytech-common-core` 的 `com.paytech.foundation.web.paging`。
按用户后续命名要求，公共分页请求使用 `PagingRequestBody`；公开 HTTP 与发布的内部 `*-api` 顶层 DTO
采用 `*RequestBody` / `*ResponseBody`，嵌套业务模型和内部领域对象不强制后缀。
GET 查询 DTO 的名称不改变 HTTP 传参，分页仍使用 page/size Query 参数。
请求类改名后重新执行干净构建：Foundation Common 36 项、BFF 150 项、Platform 18 项聚焦测试通过；
JAR 中仅保留新分页类型名称。本次命名调整未重新部署，以下部署记录对应前一轮分页实现。
`PagingRequestBody.of(page, size)` 只为缺失参数补 page=1、size=10；显式页码必须在 1 到 100000 之间，
每页条数仅允许 10、20、50、100 或 200。非法值使用公共 `INVALID_REQUEST`，偏移量由 `offset()` 统一计算。

`PagingResponseBody<T>` 保持 `{items, page, size, total}`，total 为 long，只用于分页列表，不包装其他成功响应。
BFF 使用 `map(...)` 转换元素并保留全部分页元数据；核心服务在相同授权范围和查询条件下执行数据库
`LIMIT/OFFSET` 与 `COUNT`，采用稳定排序，不先查全量再在内存截取。

人员、角色与审计列表统一接入分页。角色列表由原数组改为分页对象，Platform API、BFF 与前端必须同步发布。
前端复用 `usePaging` 管理页码、条数和加载状态；人员管理中的角色选项按每批 200 条加载后续页，避免只显示第一页。
角色 Route `operations-role-list` 通过手工增量
[`2026-09-04-operations-paging.sql`](../../test-environment/sql/2026-09-04-operations-paging.sql) 增加 page/size
查询参数白名单；只在实际新增时推进 Route 版本，不改变 13 条 Route、权限或 Guard，不重跑初始化与 Bootstrap。

本轮前端 40 项、Foundation 82 项、BFF 150 项、Platform 18 项聚焦测试通过。Platform、BFF 与前端三个应用镜像
已重建并部署到 mor-test，均为 healthy。分页 SQL 首次新增 2 条白名单，角色 Route 版本从 1 推进到 2；
重复执行新增 0 条、版本变化 0 次。

Python smoke 已通过真实 HTTP/MySQL 链路：三个列表接口默认 page=1、size=10，五档 size 均可用；角色第二页为空
但保留 total，审计第二页与第一页无重叠，size=11 被拒绝；原登录、Refresh 和 Logout 流程继续通过。

真实浏览器确认三个列表默认每页 10 条且下拉提供五档选项，角色页可切换到 200；审计下一页显示 10 条，
切换每页 20 条后显示 20 条且上一页按钮禁用，确认已回到第一页。人员管理的角色选项完整显示 2 项，
未执行人员或角色授权写操作。本机 local 数据库自动测试凭据仍未提供，Platform 数据库全量 auto 未运行；
上述 test 环境烟测不替代该自动测试门禁。

## 同日后续决定：整页刷新恢复登录（当前）

用户明确要求修复浏览器整页刷新后丢失登录的问题。本决定覆盖此前 Operations JSON Refresh 阶段的“Token
仅存模块内存、整页刷新需重新登录”限制；JSON 双 Token、单飞轮换、Platform Session 与服务端授权规则保持。

当前标签页通过 `sessionStorage` 保存 Access/Refresh Token 及对应绝对过期时刻，模块启动时恢复内存状态。
有效 Access 仍正常随 Authorization 发送；临近或超过 Access 有效期时发 JSON Refresh，页面恢复本身不强制
轮换。每次成功轮换将两种 Token 与期限作为同一记录一起更新；不使用 localStorage、Cookie 或跨标签页广播，
不缓存密码、Account 上下文、角色或权限，也不把 Token 放入 URL、日志或 console。

sessionStorage 的生命周期由浏览器 page session 决定；opener 复制标签页或恢复关闭标签时可能复制或恢复存储。
应用不主动跨标签页共享 Token，不保证所有新标签都必须登录，也不承诺关闭标签必然永久删除存储。

退出、改密完成、Refresh 返回 401、刷新后的 GET 唯一一次重试仍返回 401、Refresh/会话到期或恢复记录无效时，清除当前会话的内存和
sessionStorage。会话版本保护继续丢弃迟到响应，旧请求不得清除或恢复后来建立的会话。恢复时遇到临时
429、503 或网络故障，保留凭据并提供恢复页与显式重试，不假装用户已退出；写命令不自动重放。

恢复凭据只用于重新请求当前身份和导航，不恢复缓存权限，不绕过 Gateway JWT、BFF PLT-I02 或 Platform
实时会话校验。此增量只调整前端保存与恢复逻辑，不要求数据库、路由或 Bootstrap 变更。

本增量 `npm test` 32/32 通过，包含最终 GET 重试 401 清理存储和迟到 401 不清新登录；生产构建通过。
前端镜像已重建，mor-test `operations-console-web` 为 running/healthy。真实浏览器在
`http://localhost:28080/roles` 登录后连续整页刷新两次，均保持角色页并显示 2 个角色；退出后刷新仍为登录页。
临时验证页已关闭；此次没有后端或数据库改动。

## 同日后续决定：公共 HTTP 客户端异常处理

用户要求异常处理公共化，只有特殊转换保留业务侧。新增 focused `paytech-http-client-support`，通过 Spring HTTP 代理扩展统一处理公共业务错误、网络故障、HTTP 状态、错误体解析、解码失败、重定向和空 DTO。BFF 配置只注册三个既有 Operations 特殊码及三个单例 API Bean；`PlatformOperationsClientImpl` 删除 `query/command/invoke` 与状态/错误体解析，只保留 DTO 转换、授权结果及字段契约校验。共享 MVC 仍唯一生成外部错误体，公共 `UPSTREAM_INVALID_RESPONSE` 固定 502。没有扩大到 Gateway 的响应式协议或 Platform 的事务审计/认证恢复捕获。

验证：Foundation Common 14、HTTP Client 43、MVC 86 项（共 143 项）及 BFF 134 项测试全部通过；Build/BOM 与 Foundation 已安装本机。已仅更新 mor-test BFF，真实登录、授权查询、参数/业务提示、Refresh 轮换、退出与撤销后拒绝烟测通过。未修改数据库和现有运行凭据；测试环境构建脚本已包含新增公共模块。

## 同日后续决定：公共异常与业务消息

用户要求仅保留少量公共异常，普通业务拒绝使用业务检查异常加具体消息，只有调用方明确需要区别处理才定义错误码。Foundation 复用 `BizCheckFailException` / `ErrorException`，补充公共参数与并发异常，依赖不可用不再要求传入业务错误枚举。Operations 业务错误码从 12 项收敛至登录失败、会话失效、权限拒绝 3 项；密码策略、账号限制等使用公共业务消息，版本与幂等冲突使用公共并发类别。

BFF 对公共业务、参数、并发的 4xx 错误保留安全消息，前端直接展示；系统错误与未知协议消息仍使用安全默认提示。共享 MVC 是唯一异常响应出口，事务、审计、Refresh 轮换与自动重试规则不变。后续编码遵循 `DEVELOPMENT_CONVENTIONS.md` 第 4 节；下文“远端消息一律不透传”和大量错误枚举描述为历史。

验证结果：Foundation 99 项、BFF 134 项、Platform 18 项相关测试和前端 19 项测试全部通过，Java 构件与前端构建成功。Platform 全部测试源码编译通过；3 项本地数据库 HTTP 场景因缺少测试数据库密码未运行，不计入通过数。已重建并更新 mor-test 的 Platform/BFF/前端；真实 HTTP 登录、授权查询、公共参数错误、同密码业务提示、Refresh 轮换、退出及撤销后拒绝均通过。未执行 DDL、初始化 DML 或 Bootstrap 重置。

## 同日后续决定：精简转发层与 JSON Refresh

用户再次审查后要求移除没有实际编排职责的 `OperationsManagementProcessor`，并把 Refresh Token 改为普通请求。当前实现删除管理/认证两个重复转发 Processor、Refresh Cookie Helper 和仅用于隐藏 Refresh 的响应转换。BFF 在统一 MVC 入站拦截器执行 Origin/JSON、可信 Header 与 PLT-I02，只豁免精确 POST Login/Refresh；Controller 直接委托本地 Client Port，ArgumentResolver 只读取已授权的请求级上下文。参数非法可先经过一次授权，但不调用业务接口。

Operations 登录/刷新 JSON 返回 Access/Refresh Token；Refresh 和 Logout 均从 JSON `{refreshToken}` 取值，Logout 仍需 Access JWT。Gateway 目录使用现有 `request_cookie_mode=DROP`、`response_cookie_mode=NONE`，不传 Cookie、不接受 Set-Cookie；不新增 Cookie 模式或表。Operations 不再要求 CSRF Header，仍检查精确 Origin 和 JSON。前端使用 `credentials: omit`；本阶段当时采用两 Token 仅存内存、页面刷新后重新登录，现已被本文顶部的 sessionStorage 恢复决定覆盖。单飞刷新及会话版本竞态保护继续有效，不恢复共享 Cookie 的跨标签页锁/广播。Platform HMAC、轮换事务、重用撤销、期限与身份授权规则不变。

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
