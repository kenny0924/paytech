# Code-MoR Test Docker Environment

独立的本机 `mor-test` 环境。沿用 `local-environment` 的 MySQL 8.4.11、Redis 8.2.1、RabbitMQ 4.2.9、Nacos 3.1.2 与同一套基础设施初始化脚本，另外部署 Operations 前端、BFF、API Gateway、Platform Support。可与 `mor-local` 同时运行；不会读取其 `.env.local`、共享数据卷或停止其容器。

本目录由根工作区版本管理，前端和各 Java 项目继续独立版本管理。只有已实现的 Operations 链路被部署，其他核心系统/Vault 不创建空应用容器。可选 scheduler 配置与 local 一致，本轮没有启动 XXL-Job。

## 当前入口与账号

- Operations：**http://localhost:28080**，必须使用这个精确 Origin；无需 HTTPS 或安装证书。
- 测试管理员：`operator@example.test`，密码见不提交的 [`private/access.md`](private/access.md)。Bootstrap 已通过真实 HTTP 流程永久停用，不再使用 `admin` 登录。
- 按用户明确要求，test 使用 HTTP。Gateway/BFF 仅在单独 `test` Profile、显式 `OPERATIONS_TEST_HTTP_ENABLED=true` 且 Origin 为 `http://localhost:<端口>` 时允许此模式。
- 请使用 `localhost` 入口，不要替换为局域网 IP 或其他域名。

| 服务 | 宿主机地址 |
|---|---|
| Operations HTTP | `http://localhost:28080` |
| MySQL | `127.0.0.1:23306` |
| Redis | `127.0.0.1:26379` |
| RabbitMQ AMQP | `127.0.0.1:25672` |
| RabbitMQ 管理台 | `http://127.0.0.1:25673` |
| Nacos Console | `http://127.0.0.1:28088` |
| Nacos Client HTTP / gRPC | `127.0.0.1:18848` / `127.0.0.1:19848` |
| XXL-Job（可选，未启动） | `http://127.0.0.1:28090` |

基础设施密码位于不提交的 `.env.test`；Nacos 管理账号 `nacos`，应用只使用 Namespace `mor-test` 下授权的 `mor-test-services`。RabbitMQ 使用 `mor_test` / `/mor-test`。所有端口只绑定 `127.0.0.1`，Java 应用和静态前端没有宿主机端口映射。

## 日常操作

从工作区根目录运行：

```sh
./test-environment/compose.sh ps
./test-environment/compose.sh up -d --wait --wait-timeout 240
./test-environment/compose.sh down
```

`down` 保留 MySQL、Redis、RabbitMQ 数据卷；不要添加 `-v`，不要使用 Docker 全局 prune。首次初始化见下一节；普通重启不会重新运行业务 Schema、初始化目录或 Bootstrap。`compose.sh` 将 `.env.test` 的字面值优先注入 Compose，防止终端里导出的 local 密码覆盖 test 配置。不要把包含秘密的 `compose config` 完整输出写入日志；验证使用 `config --quiet`。

更新应用代码后：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./test-environment/scripts/build.sh
./test-environment/compose.sh up -d --wait --wait-timeout 240
python3 test-environment/scripts/smoke.py
```

构建使用本机 JDK 25、Maven Wrapper、既有私密 Maven Settings 和 Node/npm，按 Build → Foundation → Platform API/Web → Gateway/BFF → 前端顺序执行。Docker 只 COPY 可执行 JAR 或构建后的 `dist`，不会把 Maven Settings、源码工作区或运行秘密复制进镜像。脚本使用 `-DskipTests` 打包；这不是完整 Maven `verify` 或覆盖率验收。

## 首次准备（已在当前机器执行）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  python3 test-environment/scripts/prepare.py
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./test-environment/scripts/build.sh
./test-environment/compose.sh up -d --wait --wait-timeout 240 mysql redis rabbitmq nacos

# 显式操作员动作：先审阅服务拥有的 Schema/DML 和生成的 private/bootstrap.sql。
python3 test-environment/scripts/initialize.py --apply-platform-sql --nacos
./test-environment/compose.sh up -d --wait --wait-timeout 240

# 仅首次显式完成 test Bootstrap 与首管理员改密；它会写真实 test 业务数据和审计。
python3 test-environment/scripts/smoke.py --complete-bootstrap
```

`prepare.py` 生成独立随机密码、RSA 3072 签名密钥和独立 HMAC 密钥，不生成 TLS 证书；文件权限 `0600`，私密目录 `0700`。既有材料会保留；材料不完整时停止，不静默轮换。依赖 Java Crypto JAR 使用仓库既有版本，非标准 Maven 缓存可显式设置 `MAVEN_LOCAL_REPOSITORY`。

基础设施 MySQL 初始化只复用 `../local-environment/mysql/init` 中的数据库/账号及 Nacos/XXL-Job 技术表。业务 SQL 由 `initialize.py --apply-platform-sql` 明确执行到本项目 `mor-test-mysql-1`，不挂进数据库 init 目录、不由 Java 启动执行。已有 22 表/13 Route 目录与账号/Key 会保留；异常或部分初始化会停止，不能用删除 Volume 或恢复 Bootstrap 绕过。Nacos 初始化途中失败时先检查已完成步骤，再显式恢复，不重置已有凭据。

Bootstrap 离线工具只生成 Argon2id PHC 和真实公钥 SQL，不连接数据库；私钥、HMAC Key、明文密码不进入 SQL。`smoke.py --complete-bootstrap` 通过 Nginx → Gateway → BFF → Platform 完成改密/首管理员流程并保存检查点；网络结果不明时先查当前状态，勿手工删除检查点盲目重放。

## 已有环境切换 JSON Refresh

本轮只需重建 BFF 和前端；Gateway 与 Platform 已支持目录中的 `DROP/NONE`，不需要改动其运行代码。
手工增量目录文件为 [`sql/2026-09-04-operations-json-refresh.sql`](sql/2026-09-04-operations-json-refresh.sql)。它在同一事务中更新 13 个现有 Operations Route 中受影响的 Cookie 模式与 CSRF Header，并推进相应 Route Version；重复执行无变化时不推进版本。不得重新执行 Schema/初始 DML 或 Bootstrap。

先构建 BFF/前端并准备镜像，再显式应用该增量、更新两个应用并执行 `scripts/smoke.py`。Gateway 每 5 秒读取当前 Snapshot；手工 SQL 不进入 Compose 或应用启动流程。烟测只在内存持有 Token，不输出或落盘 Token，检查登录/刷新 JSON、无 Set-Cookie、查询授权、轮换及退出后的 Refresh 拒绝。

## 系统管理两级菜单增量

菜单调整为“系统管理 > 人员管理、角色管理、审计日志”。增量文件为
[`sql/2026-09-04-operations-system-menu.sql`](sql/2026-09-04-operations-system-menu.sql)，由操作员审阅后单独应用。
本机 `mor-test` 已于 2026-09-04 执行。不要重跑 `initialize.py`、当前初始化 DML 或 Bootstrap。

该 SQL 使用 UTF-8 和一个显式事务，新增无页面/组件的父菜单、角色列表页面及父菜单可见权限
`OPERATIONS_SYSTEM_READ`，只把该权限授予现有 `PLATFORM_ADMIN` 与 `OPERATIONS_VIEWER`。人员、角色和审计页面
继续分别使用已有的 READ 权限；三个管理按钮仍挂人员页面并使用 MANAGE 权限，BFF 的逐请求授权不变。
角色页面仅查询现有角色，不新增角色创建、修改或删除能力。

已有 Accounts/Audit 的稳定 ID、Code、路径与权限保持，SQL 只调整其名称、父菜单和同级顺序。重复执行不增加
重复行，也不推进未改变资源的版本；已有停用资源或撤销的权限不重新启用。使用默认遇错停止的 MySQL 批处理
客户端，禁止 `--force`；异常时回滚本事务并检查目录前置，不修复或覆盖其他数据。

先构建新版前端，再显式应用该增量并部署前端；无需重建 Platform、BFF 或 Gateway。`scripts/smoke.py` 已增加
七个资源、父子关系、人员/角色/审计顺序、页面注册 Key 与角色查询的断言，应在目录和前端都更新后执行。

本次已更新前端容器且健康，真实 HTTP 烟测通过；增量 SQL 重跑验证未改变资源版本，目录权限只有两条现有角色授权。
浏览器确认目录展开/收起、三个页面及选中状态、390px 折叠侧栏的子菜单切换正常，角色列表使用真实接口。
前端 23 项测试与生产构建、Platform 3 项 SQL/Mapper 契约测试通过；未运行 Platform 数据库纵向自动测试。

同日修复切页反复请求导航引发的 429：实际 Gateway 指标显示 `operations-navigation` 的账号维度出现 5 次限流。
前端原先将 `location.pathname` 作为导航加载依赖，切页会重读菜单并卸载/重建页面。现在只在登录挂载工作台或
导航失败后显式重试时读取菜单，路径切换只加载目标页；原有 Gateway 限流配置保持。前端已重新构建、部署且健康。
浏览器连续切换三个页面，12 次明确验收均保持登录；该验证期间网关的导航、Refresh 请求和限流拒绝增量均为 0。
新增查询/Refresh 返回 429 时保留会话的回归测试，前端共 25 项测试通过。排查时最近五次用户登录会话仍为 ACTIVE，
未发现对应后台撤销；排查当时整页刷新按仅内存 Token 契约要求重新登录，该限制现已被下一节的新决定覆盖。
429 不等同于认证失效。

## 整页刷新恢复登录

用户随后明确要求刷新浏览器页面后保留登录。最新契约改为：当前标签页的 `sessionStorage` 保存 Access/Refresh
Token 及对应绝对过期时刻，模块启动时恢复；有效 Access 仍直接用于 Authorization 请求，临近或已过
Access 有效期时发 JSON Refresh，页面恢复本身不强制轮换。成功轮换把两 Token 与到期时刻一起更新，不使用 localStorage 或 Cookie，
不保存密码、Account 上下文或权限，不做跨标签页 Token 广播。

存储遵循浏览器 page session 生命周期；opener 复制或恢复标签页可能复制/恢复 sessionStorage，不能把
“新标签必需登录”或“关标签必然清空”作为保证。应用不主动跨标签页共享凭据。

退出、改密完成、Refresh 401、刷新后的 GET 唯一一次重试仍 401、Refresh/会话到期或恢复数据无效时清理内存及 sessionStorage。恢复时的
429、503 或网络错误保留凭据并显示可重试的恢复页，不能直接跳回登录。恢复后的身份、菜单和每次 API 权限
仍由服务端检查，浏览器存储不改变 Platform 的绝对/空闲期限或一次性 Refresh 轮换。

2026-09-04 验收：`npm test` 32/32 通过，包含最终 GET 重试 401 清理存储和迟到 401 不清新登录；生产构建通过。
前端镜像已重建，mor-test `operations-console-web` 为 running/healthy。真实浏览器在
`http://localhost:28080/roles` 登录后连续整页刷新两次，均保持角色页并显示 2 个角色；退出后刷新仍为登录页。
临时验证页已关闭。此次只更新前端，没有后端或数据库改动，没有执行 SQL 或重跑 Bootstrap。
Python `scripts/smoke.py` 继续仅在进程内存持有 Token，不能代替浏览器 sessionStorage 与整页刷新验收。

## 公共分页增量

2026-09-04 人员、角色、审计列表统一使用 `PagingResponseBody<T>`，响应为 `{items, page, size, total}`。
前后端缺省为第 1 页、每页 10 条，每页数量仅允许 10、20、50、100、200；调整数量回到第 1 页。
角色接口从数组改为分页对象，更新时须同步构建并部署 Platform、BFF 与前端。人员弹窗的角色选项按每批 200 条取全。

手工增量 [`sql/2026-09-04-operations-paging.sql`](sql/2026-09-04-operations-paging.sql) 已应用到 mor-test：
只为现有 `operations-role-list` 增加 `page`、`size` 查询白名单，新增 2 条，Route Version 从 1 到 2。
重复执行验证新增 0 条、更新 0 条；原有 13 条 Route、权限、限流、账号、密钥及 Bootstrap 终态保持。
新环境使用 Platform 当前初始化 DML；已有环境不要重跑 Schema、初始化 DML 或 Bootstrap。

三个应用的镜像已重建、同步部署并健康。`scripts/smoke.py` 通过真实 HTTP/MySQL 链验证三个接口默认值、
五档数量、角色空第二页保留 total、审计前后页不重叠、非法 size 返回 `INVALID_REQUEST`，以及原登录/Refresh/Logout 流程。
浏览器确认三个列表默认 10 条、五档选项、角色切换 200 条；审计第 2 页显示 10 条，切换 20 条后显示 20 条且回到第 1 页；
人员角色选项完整可用，未提交人员或角色变更，验收会话已退出。

前端 40 项、Foundation 82 项、BFF 完整 verify 150 项和 Platform 18 项聚焦测试通过。
Platform 数据库 auto 测试源码已更新并编译，但缺少 local-environment 专用凭据，未运行该全量门禁；部署烟测不代替它。

## 公共异常与提示更新

2026-09-04 已重建并部署 Platform Support、Operations BFF 与前端，使用 Foundation 的公共业务、参数、并发与系统异常。无需数据库增量或重新初始化；现有账号、密钥和路由目录保留。烟测增加非法分页返回 `INVALID_REQUEST`，以及同密码修改返回 `BUSINESS_CHECK_FAILED` / “新密码不能与当前密码相同”；随后仍验证 Refresh 轮换和退出。

同日进一步将出站异常处理集中到 `paytech-http-client-support`，测试构建脚本已安装该模块。BFF 经共享客户端处理网络、HTTP 状态与错误体，业务方法直接调用注入的 API；143 项公共模块及 134 项 BFF 回归通过，更新 BFF 后上述烟测再次通过。

## 中文目录编码修复

2026-09-04 修复初始化 MySQL 客户端默认 `latin1` 导致的菜单、按钮及角色名称乱码。`scripts/initialize.py` 现在明确使用 `--default-character-set=utf8mb4`，SQL 文件读写使用 UTF-8；Platform 初始化 DML 同时声明 `SET NAMES utf8mb4`。

现有 test 数据已显式应用 [`sql/2026-09-04-operations-catalog-utf8.sql`](sql/2026-09-04-operations-catalog-utf8.sql)：仅修复 2 个角色名和 5 个资源名称，按固定 ID、业务 Code 和确切乱码字节匹配，版本各推进一次；重复执行验证为 0 行变更。无需重跑 Schema、初始 DML 或 Bootstrap，也无需重建应用。`scripts/smoke.py` 已增加所有 7 个中文名称的 HTTP 响应断言，修复后完整烟测通过。已打开的页面刷新并重新登录后加载新名称。

## 运行边界

- Namespace、Container、Network、Volume 均独立。`mor-test-ingress` 只连接边缘 Nginx 和 Gateway；Gateway Application Listener 只绑定 `172.30.72.3`。另一侧通过独立 Backend Network 连接 Nacos、Redis、BFF、Platform。Frontend 只连接 Web Network。
- Nginx 接收 test HTTP、覆盖一个 `X-Real-IP`，保留 `/api/operations` 原路径；Gateway 负责下游重写与 JWT/Guard。Nginx 不合成 Origin、不更改 Cookie、不记录请求 Body/Query/Header，也不挂载证书。
- Java 25 容器与两个 Nginx 进程都以非 root UID 运行、只读根目录、限定 `/tmp`、去掉 Linux Capabilities。
- Platform 显式启用 `test` Profile 的 Vault Mock 与本地签名适配器；`prod`、`production`、混合生产及未知 Profile 继续拒绝启用。Issuer 仍为协议固定的 `https://platform-support.local.test`，它是 JWT 标识，不是浏览器地址；浏览器 Origin 为 `http://localhost:28080`。
- HTTP test 与 HTTPS 共用 JSON Token 契约：登录/刷新返回 Access 与 Refresh Token，刷新/退出的请求体是 `{ "refreshToken": "..." }`。Gateway 不转发 Cookie、不接受 Set-Cookie。test 开关只允许 localhost HTTP Origin；混合 Profile、非 localhost、隐式或非法端口均拒绝 HTTP test 模式。
- 私密材料不进 Git/镜像/Nacos Config。停止或重启不销毁材料；精确 Origin、JSON、JWT、当前 IAM 授权和审计继续校验。按最新恢复契约，两 Token 及绝对过期时刻由当前标签页 sessionStorage 保存并恢复到内存，不保存密码或权限；请求使用 `credentials: omit`，无 Cookie/CSRF Header。恢复增量的部署状态见上节。
- Nacos 使用相同固定版本/摘要的派生镜像，只关闭官方启动脚本中的 `set -x`，防止内部认证材料被 shell 追踪打印；服务器逻辑与鉴权保持。首次发现的两项 test 服务器认证材料已轮换，旧 test 容器已替换。

## 验证记录

2026-09-04：9 个容器健康。首次部署已完成真实 Bootstrap/管理员改密；随后按用户要求切换为 HTTP，保留已有账号、密钥、目录和 Bootstrap 永久终态。此前 `scripts/smoke.py` 已通过 Cookie 版本的 HTTP 登录、当前上下文、菜单、人员、角色、审计、Refresh 轮换和退出；首页和 `/accounts` 返回 `200`，未认证的上下文接口返回 `401`，原 HTTPS `28443` 端口已关闭。

HTTP 增量的 BFF 84 项测试、Gateway 73 项无 Redis 聚焦测试通过，两个可执行 JAR 已重新打包并部署；Gateway 边界脚本、Compose 配置和 Python 脚本语法检查通过。

同日 BFF 分层与异常重构后，BFF 99 项 `clean verify` 测试通过，Foundation MVC 80 项及 Common Core 10 项通过。已仅更新 BFF 镜像并重新运行 `scripts/smoke.py`，HTTP 登录、上下文、菜单、人员/角色/审计、Refresh 轮换和退出全部通过；业务数据、Bootstrap 终态与 HTTP 入口保留。

该记录是部署烟测，不代替 Java 完整测试门禁；没有修改 `local-environment`、在其数据库写数据或运行其秘密驱动的测试。

镜像版本及摘要固定在 Compose/Dockerfile，基础设施沿用本机已验证的 ARM64 镜像。新增镜像来源：[NGINX 官方镜像](https://hub.docker.com/_/nginx)、[Eclipse Temurin 官方镜像](https://hub.docker.com/_/eclipse-temurin)；Nacos 初始化接口参考[官方认证说明](https://nacos.io/en/docs/latest/manual/admin/auth/)与[Admin API](https://nacos.io/en/docs/latest/manual/admin/admin-api/)。

2026-09-04 JSON Refresh 后续验收：BFF/前端镜像已更新并健康，13 条现有 Operations Route 已采用 DROP/NONE 且移除 CSRF Header；Gateway/Platform 继续运行原实例。`scripts/smoke.py` 已通过 JSON 登录、Context、菜单、人员、角色、审计、Refresh 轮换、Logout 与撤销后的 Refresh 401，所有响应均无 Set-Cookie。BFF 131 项回归及前端 15 项测试/生产构建通过。

Gateway 本轮 14 个相关测试类共 331 项、Platform 目录编译器 17 项通过，无失败或跳过。Gateway 完整测试源码编译成功；这是本轮相关范围验证，不宣称整个 Gateway reactor 全量测试。
