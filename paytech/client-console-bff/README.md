# Client Console BFF

无状态 Client 浏览器适配层，包含 App/Test 两个 Maven 模块（`paytech-client-console-bff-app`、`paytech-client-console-bff-test`，无 api、无 core）；服务聚合 POM 以 `../build/paytech-parent/pom.xml` 继承 `paytech-parent`，由单一 Reactor `paytech/pom.xml` 列出。通过 Platform Owner API 提供 `/client-auth/v1` 下的九个认证操作；不持有数据库、邮箱挑战、密码或会话业务事实。

## 构建与验证

在仓库的 `paytech/` 目录使用 JDK 25 与唯一 Wrapper；Parent、BOM 与 Foundation 由同一 Reactor 构建，Platform、CCT 与 Risk 轻量 API 在各自 Owner 搬入 Reactor 之前仍从本地 Maven 缓存解析同版本 Snapshot。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./mvnw -B verify -pl client-console-bff/paytech-client-console-bff-test -am
```

`ClientAuthenticationHttpAutoTest` 使用真实随机端口 Spring HTTP 与本机 Platform 协议 Simulator；它证明 BFF 转发、Cookie、错误格式和端口隔离，不替代 Platform 的真实 MySQL/Redis 回归。测试 profile 关闭 Nacos 和自动注册。

## 代码结构

- `ext/rest`：Controller 只做协议适配。可信身份由 `ClientActorResolver` 从 Gateway 单值头解析，入站拦截器先行校验，Controller 以 `ClientActor` 参数接收；工作区接口的 PLT-I02 权限由 `ClientPermissionGuard` 判断。Refresh Cookie 的读写在 Controller 侧完成，规则集中在 `core/ClientRefreshCookie`。
- `service/processor`：只放真实的多 Owner 页面聚合，每个入口一个 Processor，继承 Foundation `AbstractProcessor`：入驻开始页、受控表单目录、申请详情、工作区首页、邀请详情。内部请求在 `service/processor/request`，请求只有可信身份时直接以 `ClientActor` 作为请求；响应使用 BFF 页面模型。
- `integration`：每个 Port 只适配一个 Owner 的接口并校验回执（会话签发、工作区成员、授权、邀请、文件、CCT 摘要与入驻读取、Compliance 材料与审核结果）。纯转发的写命令和查询由 Controller 直接调用 Owner 的 `@HttpExchange` 接口，不增加一行转发层。
- `service/model`：BFF 页面模型使用普通 class，响应在 `response`、请求在 `request`，一个类型一个文件；`ClientActor`、`SessionGrant` 是不出站的内部模型。

## 配置边界

- 内部调用 Token（D26）：依赖 `paytech-internal-auth`，`internal-auth.mode` 默认 `OFF`，行为不变；非 `OFF` 时入站由 MVC Starter 的 `InternalAuthFilter` 校验，出站全部 `HttpServiceClientFactory.create` 客户端经 `InternalCallTokenOptions` 按显式目标服务名附加 Token。`ClientInternalTokenAutoTest` 覆盖 `REQUIRED` 下入站拒绝与出站附加。

- 默认业务监听 `127.0.0.1:18202`，独立管理监听 `127.0.0.1:19202`；管理端口不提供认证业务路径。
- 正常调用经 Nacos 的 `platform-support-service` 私网发现；测试直连仅允许 `auto` profile 下的 loopback HTTP。
- `client.console-origin` 必须是精确 HTTPS Origin。写请求必须匹配该值，不能从代理头或浏览器输入推导配置。
- Refresh Cookie 为 `__Secure-client-refresh`，Path `/api/client/auth/v1`、Secure、HttpOnly、SameSite=Lax、host-only。错误响应复用 Foundation Problem Writer，并显式批准认证防缓存头；只有失效刷新会话清理 Cookie，依赖不可用时保留。
- `/context` 和 `/logout` 只接受 Gateway 重建的 CUSTOMER/CLIENT 身份，实际账号/会话仍由 Platform 验证。BFF 不能直接暴露公网。

Gateway 路由和客户端认证默认启用状态不由本工程启动改变。发布注册使用[既定管理模板](../../requirements/20260914/client-console-注册与客户入驻/registration-design.md)，不能绕过 Owner 直接执行配置 DML。当前实施与验证范围见[台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 入驻与文件适配

`client.onboarding-enabled` 默认 false。按批准环境配置启用后，`/client-onboarding/v1` 提供原申请导航、材料要求、主表单与人员/关系/材料转发；`/onboarding-files/v1` 提供 F01—F04。管理监听仍不暴露这些路径。公共 Gateway 注册与启用是独立交付，启动开关不代替该授权。

client-master 使用 `client-master-service`，compliance（入驻审核与材料要求）使用 `compliance-service`，Platform 使用既有 `platform-support-service` 私网发现。测试专用 `client.cct-test-base-url` / `client.risk-test-base-url` 仅允许 auto 的随机 loopback 根路径；Platform 测试基址必须精确为 `/platform-support`。真实聚合留在 Processor：保留 CCT 引用，组合 Risk 非个人要求与 Platform 状态；部分依赖失败显式 unavailable，权限失效整页失败。写入仍只调用一个 Owner。

`ClientOnboardingHttpAutoTest` 覆盖真实 Spring/HTTP Client、三 Owner 协议模拟器、归属 Header、空 Body、幂等重放、材料聚合、部分失败、会话撤销及监听隔离；默认关闭也有独立认证套件断言。它不替代真实 Owner 数据库测试、供应商验收或多 Owner 端到端。最新验证从[台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取。


C03 同时读取 CCT 受控目录与 Risk 材料要求，返回独立 catalogStatus/requirementsStatus；单方依赖失败不吞掉另一方事实，最终重新校验会话。准确投影与配置边界见[接口合同第 12 节](../../requirements/20260914/client-console-注册与客户入驻/api-contract.md#12-c03-受控表单目录)。
