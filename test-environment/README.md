# Code-MoR Test Docker Environment

独立的本机 `mor-test` 环境。沿用 `local-environment` 的 MySQL 8.4.11、Redis 8.2.1、RabbitMQ 4.2.9、Nacos 3.1.2 与同一套基础设施初始化脚本，另外部署 Operations 前端、BFF、API Gateway、Platform Support。可与 `mor-local` 同时运行；不会读取其 `.env.local`、共享数据卷或停止其容器。

本目录由根工作区版本管理，前端和各服务项目继续独立版本管理。已部署 Operations 链路与独立的 Go Vault，其他核心系统不创建空应用容器。可选 scheduler 配置与 local 一致，没有启动 XXL-Job。

> 规则集列表优化的三应用增量发布使用[专用发布包](../requirements/20260911/operations-console-风控规则集列表优化/release.md)。当前为准备工件，执行状态见台账；新增目录的 37 Route/22 Resource 不适用下文旧 Risk rollout/smoke 的 36/21 闭合分类。不要用全量部署或旧初始化替代该发布包。

## 当前入口与账号

- Operations：**http://localhost:28080**，必须使用这个精确 Origin；无需 HTTPS 或安装证书。
- 测试管理员：`operator@example.test`，密码见不提交的 [`private/access.md`](private/access.md)。Bootstrap 已通过真实 HTTP 流程永久停用，不再使用 `admin` 登录。
- 按用户明确要求，test 使用 HTTP。Gateway/BFF 仅在单独 `test` Profile、显式 `OPERATIONS_TEST_HTTP_ENABLED=true` 且 Origin 为 `http://localhost:<端口>` 时允许此模式。
- 请使用 `localhost` 入口，不要替换为局域网 IP 或其他域名。
- Client Console（2026-10-14 客户入驻发布）：**https://localhost:28443**。Client 链路代码只接受 HTTPS，Nginx 使用 `private/tls/server.crt`（test 自有 CA 签发）。入驻材料直传 MinIO `https://minio.mor-test.localhost:29400`（Chrome 把 `*.localhost` 解析到本机；容器内由网络别名指向 MinIO），浏览器需信任 `private/test-ca.crt`。验证邮件进 Mailpit，不向外投递。发布清单与执行记录见 [客户入驻 release](../requirements/20260914/client-console-注册与客户入驻/release/release.md)；`deploy.sh` 尚未覆盖 CCT、Client BFF/Web、MinIO 与 Mailpit。

| 服务 | 宿主机地址 |
|---|---|
| Operations HTTP | `http://localhost:28080` |
| Client Console HTTPS | `https://localhost:28443` |
| MinIO（预签名上传/下载） | `https://minio.mor-test.localhost:29400` |
| Mailpit 网页 | `http://localhost:28025` |
| MySQL | `127.0.0.1:23306` |
| Redis | `127.0.0.1:26379` |
| RabbitMQ AMQP | `127.0.0.1:25672` |
| RabbitMQ 管理台 | `http://127.0.0.1:25673` |
| Nacos Console | `http://127.0.0.1:28088` |
| Nacos Client HTTP / gRPC | `127.0.0.1:18848` / `127.0.0.1:19848` |
| XXL-Job（可选，未启动） | `http://127.0.0.1:28090` |

基础设施密码位于不提交的 `.env.test`；Nacos 管理账号 `nacos`，应用只使用 Namespace `mor-test` 下授权的 `mor-test-services`。RabbitMQ 使用 `mor_test` / `/mor-test`。所有端口只绑定 `127.0.0.1`，Java 应用和静态前端没有宿主机端口映射。

## Vault test 部署

2026-09-04 已新增 `mor-test-vault-service-1`：Go 1.27.1、LOCAL AES、MySQL 三表与真实 Nacos 注册。
内部地址为 `http://vault-service:18107`，Nacos 为 `mor-test / DEFAULT_GROUP / vault-service`，实例
`172.30.73.24:18107`。只连接 backend 网络，没有宿主机端口或 Gateway/Nginx 路由；浏览器不能直接调用。

```text
POST /vault/v1/records/protect
POST /vault/v1/records/reveal
```

本次只部署 Vault，不替换 Platform 的 Vault Mock、不接生产 KMS、不发布 Java Client 到制品库。
访问策略只从数据库读取（初始化 [dml-test.sql](../requirements/20261014/operations-console-Vault数据类型与手动加解密/db/vault/dml-test.sql)，2026-10-15 已执行，见[发布记录](../requirements/20261014/operations-console-Vault数据类型与手动加解密/release/release.md)；策略此后由 Operations“数据保护”页面维护）；[policy.json](vault/policy.json)（`test-deployment-v2`）仅作转写来源，不再被读取。该策略保留 `vault-test-smoke` / `vault-test-peer` 的合成测试值；
`TEST_STABLE_V1` / `TEST_REFERENCE_V1` 不是业务数据类型。2026-10-14 客户入驻发布另授权 `EMAIL_ADDRESS_V1`、`PHONE_E164_V1`（STABLE）：
Platform 登录邮箱 `PLATFORM_LOGIN_EMAIL_V1`，CCT 申请联系人 `CLIENT_ONBOARDING_CONTACT`，各含 PROTECT/REVEAL。

单独准备或更新 Vault，不运行全应用构建脚本：

```sh
# 只生成缺失的独立 test AES 材料；已有材料保留，库已存在但 Key 丢失时拒绝生成替代 Key。
python3 test-environment/scripts/vault.py prepare

# 显式操作员动作：先审阅 vault-service/docs/database/sql/vault-schema.sql。
# 仅空 mor-test/mor_vault 应用六条 CREATE TABLE；已有表不重放，部分结构拒绝。
python3 test-environment/scripts/vault.py initialize
# 已有三表的 mor-test 升级：先单独授权执行该需求的 db/vault/ddl.sql，再执行 dml-test.sql；
# 空库初始化后同样须执行 dml-test.sql，否则 Vault 无访问规则，smoke 的 Protect 全部被拒绝。

GO_BIN=/path/to/prepared/go1.27.1/bin/go ./test-environment/scripts/build-vault.sh
./test-environment/compose.sh up -d --no-deps --no-build --wait --wait-timeout 90 vault-service
python3 test-environment/scripts/vault.py smoke
```

> 当前 Platform 镜像已不含 curl，`vault.py smoke` 原样运行会在 Nacos 检查后失败；2026-10-15 发布改用接入 `mor-test-backend` 的临时 curl 容器执行同一检查（见上面发布记录第 4 节），脚本待修正。

构建仅使用已缓存依赖离线编译 Linux ARM64 静态二进制，再封装为 scratch 镜像，不下载基础镜像或修改 Go 服务。
容器以 `10001:10001` 运行，只读根目录、移除 Capabilities，SDK 临时文件限定在 64 MiB `/tmp` tmpfs。
无 Collector 时关闭 OTLP 导出；`/metrics` 与健康端点仍仅在内网可用。

`private/vault.env` 是 `0600` 文件，父目录 `0700`，只在运行时注入独立随机 AES Root/Key ID；不进 Git、
镜像或 Nacos Config。重建、重启不得重新生成或轮换这些值，应与 test 数据库一起安全备份。
Compose 对该 env 文件使用可选读取，仅为了未准备 Vault 时仍可操作既有环境；缺失时 Vault 自身拒绝启动，
不会使用默认密钥。`.env.test` 继续提供已有 test `vault_app` 与 Nacos 应用凭据，绝不读取 `.env.local`。

三表 SQL 沿用服务权威，SHA-256：`bd18057813f213b08061d68cad188940217e928aee14583fdbeacbf839ad37f6`。
DDL 不在启动流程中执行，无初始化 DML 或业务数据迁移。需要撤回本部署时，只执行
`./test-environment/compose.sh stop vault-service`，保留三表、AES 材料和镜像供恢复；不要删表、删 Key 或删除卷。

烟测通过真实 Docker 内网 HTTP 调用（复用现有 Platform 容器的 curl 作为探针，不修改其应用），验证健康、
Nacos 唯一实例、STABLE 跨调用方收敛、幂等重放与冲突、REFERENCE 独立 Token、Reveal、权限拒绝、旧 URL 404、
审计落库和敏感日志扫描。Vault 单独重启后历史 STABLE Token 不变，既有数据仍能解密；本任务未重建或重启其他容器。
合成测试记录与审计保留在 test Vault，不清表、不运行 Bootstrap。
独立密钥准备的 3 项离线测试、健康探针的 200/503/302/204/超时/连接失败检查与需求站 123 项测试通过。
部署烟测不替代完整 Go/Java 自动门禁、Java Nacos LoadBalancer 专项 manual 测试或 PDManer 人工评审。

## 日常操作

从工作区根目录运行：

```sh
./test-environment/compose.sh ps
./test-environment/compose.sh down
```

不要用无服务参数的 `compose.sh up` 更新 Operations 链路；应使用下面两个显式部署阶段，Vault 继续使用上一节的独立流程。无服务参数的 `down` 会停止完整环境，但保留 MySQL、Redis、RabbitMQ 数据卷；不要添加 `-v`，不要使用 Docker 全局 prune。首次初始化见下一节；普通重启不会重新运行业务 Schema、初始化目录或 Bootstrap。`compose.sh` 将 `.env.test` 的字面值优先注入 Compose，防止终端里导出的 local 密码覆盖 test 配置。不要把包含秘密的 `compose config` 完整输出写入日志；验证使用 `config --quiet`。

更新应用代码后：

```sh
./test-environment/deploy.sh --phase risk-bff-web
./test-environment/deploy.sh --skip-build --phase platform-gateway-nginx
```

第一条命令先构建已审阅镜像，等待 MySQL、Redis、RabbitMQ、Nacos 健康，再依次只替换 Risk、BFF、Web；本阶段不执行公开烟测。只有第一条命令成功后才能运行第二条；任一命令中的第一处失败都立即停止，不继续替换后续服务。第二条命令依次只替换 Platform、Nginx、Gateway，全部健康后执行一次现有公开烟测。六个应用更新都使用 `--force-recreate`，保证实际进程加载当前镜像和只读挂载配置；Operations 的两个 API location 都由 Nginx 覆盖写入单值 `X-Entry-Channel: OPERATIONS`，不信任调用方同名 Header，并且先于强制 Channel 的 Gateway 更新。`--phase` 对正常、`--skip-build` 和 `--check-only` 都是必填参数；`--timeout <秒>` 调整默认 240 秒的健康等待时间。健康端点只证明进程状态，不证明 Risk 数据目录已经准备完成。

第一阶段要求已经存在且只能存在一个 Compose-owned `mor-test` MySQL 容器；基础设施启动前后必须是同一个
no-trunc container ID。MySQL、Redis、RabbitMQ 使用 `--no-recreate --no-build --pull never`，Nacos 才单独使用
`--no-deps --no-build --pull never` 进入获准的配置驱动替换。MySQL 缺失、多实例或 ID 改变都会在任何应用容器更新前
失败；部署脚本不会为本流程新建或替换 MySQL 容器或 `mor-test-mysql-data`。

两个阶段通过持久检查点 `private/risk-bff-web-phase.ready` 关联。第一阶段先使旧证据失效，只有本阶段全部成功后，
才以 `0600` 发布 Nacos、Risk、Platform、Gateway、BFF、Web 六个经审阅本地镜像的精确 image ID，不包含 Nginx 或 Vault。
第二阶段在任何服务替换之前校验检查点，
缺失、不安全或与当前六个镜像不符的过期证据都会立即拒绝；不能单独运行第二阶段，也不能用旧检查点跳过第一阶段。

脚本面向已经完成首次准备的 `mor-test`，不会运行 Schema、初始化 DML、增量 SQL 或 Bootstrap，也不会更新独立 Vault。Vault 继续使用上一节的显式流程。构建使用本机 JDK 25、Maven Wrapper、既有私密 Maven Settings 和 Node/npm，按 Build → Foundation → Risk（随即复制可执行 JAR）→ Platform → Gateway → BFF → Web → 其余制品复制顺序执行。Risk 只加入 backend 网络，没有宿主机端口、Gateway/Nginx 路由或 Web/ingress 网络。第二阶段的现有烟测不会修改 Platform 目录或核心业务事实；其登录、Refresh、Logout 和忽略目录中的 `private/access.md` 更新保持原有行为。Docker 只 COPY 可执行 JAR 或构建后的 `dist`，不会把 Maven Settings、源码工作区或运行秘密复制进镜像。脚本使用 `-DskipTests` 打包；部署烟测不是完整 Maven `verify` 或覆盖率验收。

## 首次准备（已在当前机器执行）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  python3 test-environment/scripts/prepare.py
./test-environment/compose.sh up -d --wait --wait-timeout 240 mysql redis rabbitmq nacos

# 显式操作员动作：先审阅服务拥有的 Schema/DML 和生成的 private/bootstrap.sql。
python3 test-environment/scripts/initialize.py --apply-platform-sql
python3 test-environment/scripts/initialize.py --nacos

# Risk 目录建表/初始化和只读检查属于下游受审步骤；仅当检查明确返回 PREPARED 后继续。
# PREPARED 之前必须停止，不得启动任何应用服务。
python3 test-environment/scripts/initialize.py --apply-risk-sql
python3 test-environment/scripts/initialize.py --verify-risk-catalog
./test-environment/deploy.sh --phase risk-bff-web
./test-environment/deploy.sh --skip-build --phase platform-gateway-nginx

# 仅首次显式完成 test Bootstrap 与首管理员改密；它会写真实 test 业务数据和审计。
python3 test-environment/scripts/smoke.py --complete-bootstrap
```

`prepare.py` 生成独立随机密码、RSA 3072 签名密钥和独立 HMAC 密钥，不生成 TLS 证书；文件权限 `0600`，私密目录 `0700`。既有材料会保留；材料不完整时停止，不静默轮换。依赖 Java Crypto JAR 使用仓库既有版本，非标准 Maven 缓存可显式设置 `MAVEN_LOCAL_REPOSITORY`。

基础设施 MySQL 初始化只复用 `../local-environment/mysql/init` 中的数据库/账号及 Nacos/XXL-Job 技术表。业务 SQL 由 `initialize.py --apply-platform-sql` 明确执行到本项目 `mor-test-mysql-1`，不挂进数据库 init 目录、不由 Java 启动执行。当前环境已经通过独立增量推进到 27 条 Route，不再运行首次初始化参数；异常或部分初始化会停止，不能用删除 Volume 或恢复 Bootstrap 绕过。Nacos 初始化途中失败时先检查已完成步骤，再显式恢复，不重置已有凭据。

Bootstrap 离线工具只生成 Argon2id PHC 和真实公钥 SQL，不连接数据库；私钥、HMAC Key、明文密码不进入 SQL。`smoke.py --complete-bootstrap` 通过 Nginx → Gateway → BFF → Platform 完成改密/首管理员流程并保存检查点；网络结果不明时先查当前状态，勿手工删除检查点盲目重放。

## 已有环境切换 JSON Refresh

本轮只需重建 BFF 和前端；Gateway 与 Platform 已支持目录中的 `DROP/NONE`，不需要改动其运行代码。
手工增量目录文件为 [`sql/2026-09-04-operations-json-refresh.sql`](sql/2026-09-04-operations-json-refresh.sql)。它在同一事务中更新 13 个现有 Operations Route 中受影响的 Cookie 模式与 CSRF Header，并推进相应 Route Version；重复执行无变化时不推进版本。不得重新执行 Schema/初始 DML 或 Bootstrap。

先构建 BFF/前端并准备镜像，再显式应用该增量、更新两个应用并执行 `scripts/smoke.py`。Gateway 每 5 秒读取当前 Snapshot；手工 SQL 不进入 Compose 或应用启动流程。烟测只在内存持有 Token，不输出或落盘 Token，检查登录/刷新 JSON、无 Set-Cookie、查询授权、轮换及退出后的 Refresh 拒绝。

## 系统管理两级菜单增量

菜单调整为“系统管理 > 人员管理、角色管理、审计日志”。增量文件为
[`sql/2026-09-04-operations-system-menu.sql`](sql/2026-09-04-operations-system-menu.sql)，由操作员审阅后单独应用。
本机 `mor-test` 已于 2026-09-04 执行。不要重跑旧的 Platform/Nacos 首次变更动作、当前初始化 DML 或 Bootstrap；
`initialize.py --verify-risk-catalog` 是只读检查，可在不改变状态的前提下单独运行。

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

2026-09-04 已重建并部署 Platform Support、Operations BFF 与前端，使用 Foundation 的公共业务、参数、并发与系统异常。无需数据库增量或重新初始化；现有账号、密钥和路由目录保留。烟测增加非法分页返回 `INVALID_REQUEST`，以及同密码修改经 BFF 安全重建后返回 `BUSINESS_CHECK_FAILED` / “业务校验未通过”；不向浏览器透出 Platform 内部细节。这些反例和退出后的 Refresh 拒绝均验证当前 RFC 9457 Problem 媒体类型、Header/Body Request ID 一致性、六位 UTC 时间和已确认错误码；随后仍验证 Refresh 轮换和退出。

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


## Operations 自定义角色配置（2026-09-04）

本轮为 Operations 内部角色增加创建、编辑和权限树配置。Platform 保存角色与授权事实，BFF 聚合页面权限树，
前端按当前资源显示创建/编辑操作；系统角色保持只读。目录从 13 条 Route / 7 个资源变为 17 条 Route / 9 个资源，
新增 `OPERATIONS_ROLE_MANAGE` 只授予现有平台管理员。旧只读角色不获得管理能力。

既有 test 只应用一次 [`sql/2026-09-04-operations-role-management.sql`](sql/2026-09-04-operations-role-management.sql)。
先确认四条新 Route 和新 Permission 均不存在，再通过 `scripts/initialize.py` 的 `mysql` 辅助函数显式执行该文件。
文件使用一个事务，没有忽略冲突或覆盖现有行；已应用后不要重复执行。全量初始化 DML 同步维护于 Platform 仓库，
`initialize.py` 识别旧 13 条和新 17 条目录但不会自动升级旧库。

仅重新构建 Platform、Operations BFF、Operations 前端并按服务名重建容器；Gateway 自动读取更新后的目录。
不执行全量初始化、Bootstrap 或密钥生成，不重建其他应用。角色详情/保存仍使用普通 JSON 与 Authorization。

显式验收脚本 [`scripts/roles_smoke.py`](scripts/roles_smoke.py) 经公开 HTTP 入口创建合成角色和人员，
验证权限配置、幂等/版本冲突、系统角色保护、只读人员访问、同一 JWT 的即时撤权与重新授权及审计。
结束时停用合成人员、清空合成角色权限并注销验收会话；不删除业务或审计数据。

本次已完成增量应用和三个镜像更新，10 个 test 容器均健康。`roles_smoke.py` 和既有 `smoke.py` 全部通过；
受限管理员无法超范围创建角色、给自己授予平台管理员或读取高权限角色配置。另行只读核验确认重复创建只产生一次
成功审计，6 次成功修改的完整前后属性、各属性 SHA-256 与 Attribute Root Hash 全部匹配。
合成人员已停用，合成角色权限已清空；页面及静态资源与新构建匹配。
Platform 47 项纯测试、BFF 182 项测试、前端 58 项测试及生产构建通过。
本地数据库 auto 套件仍缺少外部凭据，未执行也未指向 test。


## 操作权限与树联动修复（2026-09-04）

现已部署独立的创建人员、禁用人员、分配角色、创建角色、编辑角色权限。原来同级联动是因为多个操作共用MANAGE；
现在五个按钮与相应API使用五个实际Permission ID。选择父级选全部下级，单选子级保留必要祖先，部分选择显示半选，
取消子级不影响兄弟；重新打开旧角色不会扩张权限。

一次性增量 [`sql/2026-09-04-operations-action-permissions.sql`](sql/2026-09-04-operations-action-permissions.sql)
已应用：迁移前验证旧授权全部位于Operations范围、新Permission和保留Grant ID段不存在；迁移后逐角色对比，
确认旧MANAGE等价展开为原来的实际操作范围，未增加主体或权限能力。旧权限变INACTIVE、旧grant撤销、Role版本推进，
7条Route与5个按钮同步重绑。当前17条Route、9个资源、9个ACTIVE Permission；历史旧Permission行保留用于审计解释。

三个应用镜像已更新，10个容器均健康，入口HTML与静态资源和新构建匹配。显式
[`scripts/action_permissions_smoke.py`](scripts/action_permissions_smoke.py) 已通过真实HTTP：依次只授予五种单独权限，
每种对应操作实际成功且四种兄弟操作被拒绝；菜单按钮仅显示被授予动作，原JWT不变。create-only创建重放成功，
update-only详情与编辑成功且创建选项拒绝。验收合成人员已停用，角色权限已清空。

前端62项、BFF189项、Platform47项纯测试及最终7项目录聚焦回归通过（7项属于47项中的重复验证）。
9个Platform真实HTTP/MySQL自动用例已编译，但仍因本地测试凭据缺失未执行；与mor-test真实HTTP验收分开记录。
隔离headless浏览器验证用户报告的单选/全选、取消、保存重开及移动端抽屉通过，临时fixture与浏览器已清理。


2026-09-04 系统角色查看：仅重建/更新 Operations 前端，在系统内置角色行增加“查看”入口，详情为只读，
隐藏保存与操作原因。继续复用原有详情接口和授权，不变更数据库/后端。62项前端测试与构建通过，
真实HTTP读取两个系统角色正常（管理员9项权限、只读角色4项权限），验收会话已注销。

## 角色删除与关联员工数（2026-09-04）

本轮已更新 Platform、Operations BFF 和前端三个镜像，并显式执行一次
[`sql/2026-09-04-operations-role-delete.sql`](sql/2026-09-04-operations-role-delete.sql)。
执行前确认保留 ID 均未占用；当前目录为18条Route、10个Console Resource、10个ACTIVE Permission。
独立 `OPERATIONS_ROLE_DELETE` 只初始授予系统管理员，其角色版本推进一次；只读角色及已有自定义角色未补授删除权限。
Gateway 动态加载新增 DELETE Route，无新增公开计数接口。未重放 Schema/初始化/Bootstrap，也未重启其他服务。

列表由 BFF 聚合角色分页与一次批量计数，显示去重关联员工数（含停用账号）。系统角色、有员工关联的自定义角色不可删除；
调整关联后才可删除。删除使用当前角色版本、原因和幂等键，后端同事务逻辑删除、撤权并记录完整前后审计。

[`scripts/role_deletion_smoke.py`](scripts/role_deletion_smoke.py) 已通过真实HTTP/MySQL链路，覆盖：
零关联→分配1人→停用仍1人→转移关联归零、独立删除权限、系统与关联角色保护、过期版本、成功删除、
同键同体重放204、异体同键409、新键重复删除400、已删除角色不可分配，以及仅1条成功删除审计和完整前后属性。
合成角色通过DELETE清理，合成员工停用并转到只读角色，验证会话已退出；历史记录保留。
既有 `scripts/smoke.py` 的登录、导航、三类分页、业务/参数错误、Refresh和退出回归通过。

前端64项测试与生产构建、实际App/headless交互通过；BFF214项 `clean verify` 通过；Platform48项纯测试通过。
11个Platform真实HTTP/MySQL自动用例只完成编译，仍因本地DB凭据缺失未运行，未指向test，未声明完整JaCoCo门禁通过。
10个test容器均为running/healthy，`/roles`及角色/入口静态模块与已验证构建逐字一致。

## 员工删除（2026-09-04）

已更新Platform、Operations BFF与前端三个镜像，并显式应用一次
[`sql/2026-09-04-operations-account-delete.sql`](sql/2026-09-04-operations-account-delete.sql)。
执行前确认新增权限和保留ID未占用；当前目录19条Route、11个Console Resource、11个ACTIVE Permission。
新增独立 `OPERATIONS_ACCOUNT_DELETE`，默认仅授予系统管理员，不自动扩张只读角色或自定义角色权限。
Gateway动态加载路由，未重放初始化/Bootstrap或重启其他应用。

人员删除沿用Account CLOSED终态，保留历史与Email唯一性；删除后从列表及总数移除，撤销成员、角色、密码和会话。
确认需原因、账号/成员双版本及幂等键；禁止删除自己和最后一个当前有效管理员，管理员统计限定固定INTERNAL Tenant。
人员与角色删除共用确认弹窗，保留各自业务请求和权限边界。

[`scripts/account_deletion_smoke.py`](scripts/account_deletion_smoke.py) 已通过真实HTTP/MySQL验收：独立权限、本人保护、
双版本冲突、启用/停用员工删除、幂等、旧登录凭据与会话失效、角色人数变化、分页排除CLOSED、终态拒绝再授权、
历史邮箱唯一性和只一条完整删除审计。验收仅删除本次创建的3名合成员工和2个合成角色，管理员已退出。
现有 `scripts/smoke.py` 的登录、菜单、分页、错误响应、Refresh与退出回归通过。

前端67项测试及生产构建、headless交互，BFF232项 `clean verify`，Platform49项纯测试通过。
13个Platform真实HTTP/MySQL自动用例仅编译，本地DB凭据缺失，未运行或指向test，不声明完整覆盖率门禁通过。
10个容器均running/healthy，`/accounts`、`/roles`和两个页面/入口JS与本次构建逐字一致。

## 员工启用（2026-09-04）

已更新Platform、Operations BFF与前端三个镜像，手工应用一次
[`sql/2026-09-04-operations-account-enable.sql`](sql/2026-09-04-operations-account-enable.sql)。
执行前确认保留ID未占用；目录现为20条Route、12个Console Resource、12个ACTIVE Permission。
独立 `OPERATIONS_ACCOUNT_ENABLE` 默认仅授予系统管理员，Gateway动态读取新目录；未重放初始化、Bootstrap或重启其他服务。

仅DISABLED普通员工可启用，需原因、版本和幂等键。固定INTERNAL成员和密码必须有效，目标已有角色不得超出操作者
授权上限；不恢复CLOSED、Bootstrap或暂停/撤销的Member。保留密码、角色、强制改密及安全锁定，旧会话不恢复，需重新登录。
前端复用停用抽屉，等待时防重复/关闭，503保留输入并允许同键手动重试，409提示刷新最新版本。

[`scripts/account_enablement_smoke.py`](scripts/account_enablement_smoke.py) 已通过真实HTTP/MySQL链路：独立权限与超范围拒绝、
停用→启用→原密码重新登录、旧Token拒绝、版本/幂等、角色和凭据保持、CLOSED/Bootstrap拒绝及完整单次成功审计。
仅变更和删除本次创建的3名合成员工与2个合成角色，实际管理员已退出。既有 `scripts/smoke.py` 回归通过。

前端71项测试、生产构建与实际App/headless验证，BFF245项 `clean verify`，Platform50项纯/目录测试通过。
15个Platform真实HTTP/MySQL自动用例仅编译，本地测试DB凭据缺失，未运行或指向test，不声明完整覆盖率门禁通过。
10个容器均running/healthy，`/accounts`及Accounts/Audit/入口JS与本次构建逐字一致，入口 `index-CCLDKR7s.js`。

## 审计筛选与详情（2026-09-04）

已更新 Platform、BFF 与前端三个镜像，并一次性应用
[`sql/2026-09-04-operations-audit-details.sql`](sql/2026-09-04-operations-audit-details.sql)。
目录现为 21 条 Route、12 个 Console Resource、12 个 ACTIVE Permission；详情复用 `OPERATIONS_AUDIT_READ`，
列表增加时间、操作者、动作、结果和 Request ID 筛选。保留公共分页及原 Guard，不新增角色授权或审计表。

Platform 按同一筛选查询列表及总数，按属性分类返回安全值；BFF 组装历史前后对比，保留重复值、隐藏值和缺失历史。
时间查询包含起点、不包含终点，边界向上取整到 MySQL 微秒刻度，避免驱动舍入导致漏查。前端支持条件查询/重置、
详情抽屉、失败重试与 Request ID 复制，不因切屏刷新。

[`scripts/audit_smoke.py`](scripts/audit_smoke.py) 已通过真实 HTTP/MySQL 链路验证筛选、分页、纳秒/微秒边界、
详情前后值、历史记录、公共错误与权限拒绝；合成人员和角色已删除、管理员已退出，审计历史保留。
既有 `scripts/smoke.py` 登录、导航、三类分页、Refresh 与退出回归通过。

Platform 59 项纯测试、BFF 270 项、前端 76 项测试及生产构建通过；桌面/窄屏交互检查通过。
19 个 Platform HTTP/MySQL 自动场景仅编译，本地专用 DB 凭据缺失，未执行或改指向 test。
10 个容器健康，仅三个应用容器更新；`/audit` 及页面/入口静态文件与构建逐字一致，入口 `index-C2bST4fN.js`。
完整契约及验证记录见[实施记录](../agent-docs/plans/2026-09-04-operations-audit-query-details.md)。

## 公共日期组件（2026-09-04）

仅更新 Operations 前端镜像，将审计筛选的原生日期框替换为公共 `DatePicker`：中文日历、24 小时时分秒、
手动输入/清空，窄屏上下排列；组件也支持独立使用的仅日期模式。Enter 只确认日期，不触发表单隐式查询；
本地时间转 UTC、单端条件及起点包含/终点不包含的查询规则保持不变。

76 项前端测试与生产构建通过；隔离浏览器验证日期/时间选择、手输、Enter、清空/重置、错误区间、UTC 参数、
独立中文日期模式及 390px 弹层。`/audit` 与 8 个静态资源和构建逐字一致，入口 `index-CdhstRa9.js`。
10 个 test 容器健康，仅前端容器更新；无 SQL 或后端变更。

## 公共时间范围组件（2026-09-04）

仅更新 Operations 前端镜像。审计页使用公共 `TimeRangePicker` 替代独立起止框，提供“今天 / 最近 3 天 /
最近 7 天 / 自定义”；初始和重置均不默认选择范围。快捷项在点击查询时按浏览器本地自然日计算，包含今天；
Custom 展开双端日期时间，要求完整且结束晚于开始。选择与编辑本身不请求，切换快捷项清除旧自定义值。

78 项前端测试与生产构建通过。独立浏览器在 Asia/Dubai 验证三种快捷范围的精确 UTC 边界、自定义双端及倒序校验、
Enter 键、重置、390px 两列快捷项和纵向自定义框；无页面或弹层溢出、无未捕获异常。`/audit` 与 8 个静态资源
和构建逐字一致，入口 `index-XH4weB_J.js`。10 个 test 容器健康，仅前端容器更新；无 SQL 或后端变更。

## Risk Operations 手工目录与离线门禁（Task 5）

以下入口每次只接受一个动作。先只读核对已持久化的 Platform 目录：

```bash
python3 test-environment/scripts/initialize.py --verify-risk-operations-stage
```

只在人工审阅并获准应用既有环境目录增量时执行：

```bash
python3 test-environment/scripts/initialize.py --apply-risk-operations-sql
```

[`禁用态目录增量`](sql/2026-09-07-risk-operations-rule-configuration.sql)仅接受完整的历史目录，
或已经完整准备好的新目录；任何部分准备、孤立子项、错误 ID、大小写、空格、NULL 或不可变内容漂移均停止。
历史目录只允许当前初始化目录，或本文件已记录的 2026-09-04 操作权限拆分完整形态：旧 Permission `521/525`
保持 `INACTIVE`、旧 Grant `531/539` 保持 `REVOKED`，且 `570..574` 被整组替换为同业务授权的
`7001..7005`；不接受两种血统混搭或任意额外历史行。Risk Permission 完整建立后，普通非系统角色可使用非保留
Grant ID 获授 Risk 权限；系统角色的六个预置 Grant 与 `581..586` 保留 ID 仍逐项精确校验。
首次应用新增五个 ACTIVE Permission、六个 ACTIVE Role Grant、六个 DISABLED Resource、九个 DISABLED Route，
以及精确 Header/Query/Guard 子项；报告 Operations 阶段 `BASELINE`。已经完整准备的目录只做只读验证，
保留当前 Route/Resource 状态；这里的 Operations 阶段与 Risk 数据库目录的 `PREPARED` 是两个不同概念。

Route 发布收敛后，只有九个 Risk Route 全部 ACTIVE 才能显式公开六个 Resource：

```bash
python3 test-environment/scripts/initialize.py --enable-risk-operations-resources
```

回滚必须先隐藏 Resource，再按已审阅的 Route 回滚步骤操作：

```bash
python3 test-environment/scripts/initialize.py --disable-risk-operations-resources
```

[`Resource enable SQL`](sql/2026-09-07-risk-operations-rule-configuration-resources-enable.sql) 与
[`Resource disable SQL`](sql/2026-09-07-risk-operations-rule-configuration-resources-disable.sql)
分别独立锁定并逐字段检查完整行；只接受六项全 ACTIVE 或全 DISABLED，每次实际状态变化递增一次版本，
重复执行不更新。Disable 不要求 Route 状态，因此可以作为回滚第一步。三个文件只有事务 DML，
不创建 Schema、不删除数据、不写 Bootstrap；MySQL batch 遇错停止且禁用自动重连。

所有本机 Platform 目录、Resource 和后续 Route 发布操作共用
`test-environment/private/risk-operations-rollout.lock`。它是持久、不可 unlink、0600 的普通文件，
以非阻塞 `fcntl.flock` 获取；竞争或不安全文件立即停止。同一个描述符覆盖权威前读、SQL/HTTP 变更及后读。
该锁只协调遵守此流程的本机操作者；SQL 自身仍使用数据库行锁、精确前置与提交前后置断言。
不要手工删除锁文件来绕过竞争。只读阶段查询和默认 smoke 不获取写锁。

默认 [`smoke.py`](scripts/smoke.py) 先读取同一个只读一致快照验证器，以数据库 ACTIVE Resource 投影为权威，
再核对管理员 Navigation，并完整分页核对 HTTP Route 状态：`LEGACY` 为 15 项导航/27 条 Route；
准备后隐藏阶段为 15/36；只有 `RESOURCES_EXPOSED` 为 21/36。分页总数变化、重复、漏项、状态或导航不一致均失败。
脚本输出一个闭合 Operations 阶段；既有登录/刷新/退出和私有登录说明保留，不自动应用目录或修改 Risk 业务事实。
`Browser` 保留对象请求 API，并提供 `request_raw` 发送原始 JSON bytes；只接受明确的 loopback HTTP origin 和端口。

Snapshot waiter 不依赖 Java 运行镜像中并不存在的 `curl`。构建会把 JDK 25 编译的
`LocalSnapshotEvidenceFetch` 与 readiness probe 一起复制到 `/app/health-probe`；waiter 通过固定 argv 在
`api-gateway` 内执行它。Java helper 与 Python 调用方分别只允许本机 Gateway Prometheus 和内部 Platform
Public Snapshot 两个完整 URL；请求固定 GET、禁止重定向、仅接受 2xx，并在 JDK HTTP body subscriber、Python
bytes 边界同时限制为 1 MiB，随后严格解码 UTF-8。失败时不输出响应或子进程 stderr，且外层 subprocess timeout
始终不大于当前 monotonic deadline 的剩余时间。共享 Java 镜像不会为此恢复包安装、`RUN` 或远程 `ADD`。

离线验证（不访问 MySQL、网络或真实私有凭据）：

```bash
python3 test-environment/scripts/test_risk_rule_configuration_catalog.py
python3 test-environment/scripts/test_risk_database_initialization.py
python3 test-environment/scripts/test_smoke_stage_contract.py
python3 test-environment/scripts/test_gateway_snapshot_wait.py
python3 test-environment/scripts/test_java_image_health_probe.py
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
```

本节提供源码与离线验证入口，不表示 SQL 已应用或服务已部署。Compose、build、deploy 和应用启动均不选择上述写动作。

## Risk Operations 显式动作手册（Task 6）

下面 12 个动作全部是单次、显式、opt-in 的操作员入口；`deploy.sh`、默认 `smoke.py`、Compose 和应用启动
不会自动调用它们，也不会自动清理失败现场。所有命令只接受明确的 loopback HTTP origin。除 Snapshot waiter
外，HTTP 动作都会登录并退出；即使业务读取不变，也会产生 Session、安全审计和 Guard 计数影响，因此执行前
必须获得覆盖这些影响的授权。命令只输出固定结果或闭合阶段；出现失败、超时、漂移或
`UNKNOWN_REQUIRES_RECONCILIATION` 时立即停在当前步骤，不凭报错正文猜测结果。

九条 Route 的唯一相邻阶段如下。`--activate-route` 只能从左侧到右侧，`--disable-route` 和恢复动作只能从右侧
回到左侧；只要六个 Risk Resource 已公开，就必须先执行 Resource disable SQL。

| Route code | 前置阶段 → 成功阶段 | 对应 route smoke |
|---|---|---|
| `operations-risk-rule-authoring-context` | `BASELINE → AUTHORING_READ_ACTIVE` | 只读 |
| `operations-risk-rule-set-list` | `AUTHORING_READ_ACTIVE → RULE_SET_LIST_READ_ACTIVE` | 只读 |
| `operations-risk-rule-set-detail` | `RULE_SET_LIST_READ_ACTIVE → RULE_SET_DETAIL_READ_ACTIVE` | 只读 404 区分 |
| `operations-risk-test-candidate-list` | `RULE_SET_DETAIL_READ_ACTIVE → CANDIDATE_LIST_READ_ACTIVE` | 只读 |
| `operations-risk-test-candidate-detail` | `CANDIDATE_LIST_READ_ACTIVE → READ_ACTIVE` | 只读 404 区分 |
| `operations-risk-rule-set-save` | `READ_ACTIVE → SAVE_ACTIVE` | 创建不可变 Rule Set 版本 |
| `operations-risk-rule-set-freeze` | `SAVE_ACTIVE → FREEZE_ACTIVE` | 创建并冻结不可变 Rule Set 版本 |
| `operations-risk-test-candidate-create` | `FREEZE_ACTIVE → CREATE_ACTIVE` | 创建冻结版本和 TEST Candidate |
| `operations-risk-test-candidate-validate` | `CREATE_ACTIVE → ALL_ROUTES_ACTIVE` | 创建并校验 TEST Candidate，同 key 重放 |

### Smoke 1：准备授权夹具

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --prepare-authorization-fixture
```

- 前置阶段：Platform 目录和链路可用，持久化目录处于已准备好 36 条 Route 的
  `BASELINE` 至 `RESOURCES_EXPOSED` 任一闭合阶段；27 条 Route 的 `LEGACY` 不属于本入口。
- 写入范围：只创建或核对当前随机代次的两个 synthetic Role、Account、Tenant membership、独立密码及
  `private/risk-rule-configuration-smoke.json`。文件中的 12 位大写十六进制 `fixtureGeneration` 会派生 Role code
  后缀和 Account email 后缀，避免已软删除历史记录的唯一键冲突；不创建 Permission，不改变 Route、Resource
  或 Risk 业务事实。
- 成功证据：仅输出 `PREPARE_AUTHORIZATION_FIXTURE_OK`，文件父目录为 `0700`、文件为 `0600`。
- 失败停点：夹具任何部分状态不精确即停止，不执行后续授权或 Route 动作。
- 重跑/对账：从同一私有文件读取当前代次，按派生角色码和登录 Context 对账；只在状态已确认后重跑同一命令，
  失败后不会自动 cleanup。旧四密码格式文件在不存在 ACTIVE legacy fixture Role 时会原子补入代次。
- 登录限流恢复：同一条夹具命令中的管理员和 synthetic 账号登录共享一个 90 秒 monotonic deadline，中间的权威观察
  和不同账号登录都不会重置它。每个登录只会在收到严格 RFC 9457 `429 GATEWAY_RATE_LIMITED`、单值规范且不超过
  60 秒的 `Retry-After` 后重试一次，等待 `max(Retry-After, 6 秒)`；多个不同登录可能分别等待。
  `401` 是正常的账号发现结果，不重试；`GATEWAY_CONCURRENCY_LIMITED`、其他 429、异常 Header、第二次 429 或传输结果未知都会立即停止。

### Smoke 2：授予五项 Risk 权限

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --grant-authorization-fixture
```

- 前置阶段：精确为 `ALL_ROUTES_ACTIVE` 或 `RESOURCES_EXPOSED`，且准备动作已成功。
- 写入范围：只把 authorized synthetic Role 更新为 `OPERATIONS_SYSTEM_READ + 536..540`；denied Role 保持 legacy-only。
- 成功证据：新登录的两个账号分别证明授权和拒绝，最后仅输出 `GRANT_AUTHORIZATION_FIXTURE_OK`。
- 失败停点：权限、Role 或 Account 关系不精确即停止，不公开 Resource。
- 重跑/对账：重新发现两个 Role/Account 并确认权限全集；对账完成后该动作可幂等重跑。

### Smoke 3：探测 Gateway 实际运行阶段

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --probe-runtime-stage EXPECTED_RUNTIME_STAGE
```

仅在 Snapshot waiter 已证明 invalid-refresh Last Valid 坐标后，使用下面的相邻差异形式：

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --probe-runtime-stage PRIOR_STAGE \
  --expected-persisted-stage NEXT_STAGE
```

- 前置阶段：默认要求持久化阶段等于运行阶段；差异形式只允许持久化相邻下一阶段、运行相邻上一阶段且 Resource 隐藏。
  `BASELINE` 只使用 repository test Platform Administrator，不要求 synthetic identity；
  `AUTHORING_READ_ACTIVE` 至 `ALL_ROUTES_ACTIVE` 使用管理员作为 authorized actor 和 synthetic denied actor；
  `RESOURCES_EXPOSED` 由 synthetic authorized/denied 两个 actor 验证，且前者必须看到精确 Risk Navigation。
- 写入范围：不改变 Platform/Risk 业务数据；仅有登录、Session、安全审计和 Guard 影响。
- 成功证据：所有已启用 Route 行为及下一条未启用 Route 都符合预期，仅输出 `PROBE_RUNTIME_STAGE_OK`。
- 失败停点：阶段、Navigation、权限或行为任一不一致即停止，禁止继续下一个 Route mutation。
- 重跑/对账：重新运行对应 Snapshot waiter，再用明确的 persisted/runtime 阶段重跑；不得省略行为证据。

### Smoke 4：聚合验证五条读 Route

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --read-stage
```

- 前置阶段：`READ_ACTIVE` 或其后的闭合阶段，两个 synthetic 夹具均已准备且可重新发现；
  repository test Platform Administrator 作为 authorized actor，另用 synthetic denied actor 验证 403。
- 写入范围：不创建 Risk 业务事实；只有登录、Session、安全审计和 Guard 影响。
- 成功证据：五条读 Route 的 Owner/404/denied-403 契约全部成立，仅输出 `READ_STAGE_OK`。
- 失败停点：任何一条不一致都停止，不启用 Save。
- 重跑/对账：先重做阶段检查和 runtime probe，再重跑本动作。

### Smoke 5：验证刚启用的一条 Route

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --route-smoke ROUTE_CODE
```

- 前置阶段：必须精确等于上表中该 Route 的成功阶段，两个 synthetic 夹具均已准备且可重新发现；不能在更晚阶段补跑。
- 写入范围：repository test Platform Administrator 作为 authorized actor，synthetic denied actor 验证 403；前五条 Route
  只读，Save/Freeze/Create/Validate 会创建唯一命名的不可变 Risk synthetic facts，且永不自动删除；
  不可变 Risk facts 的审计 actor 是该管理员。
- 成功证据：authorized 与 denied 结果都符合该 Route 的精确契约，仅输出 `ROUTE_SMOKE_OK`。
- 失败停点：停止发布；即使失败也不得假设已创建事实不存在。
- 重跑/对账：读动作可在同阶段重跑；写动作先按唯一 code/ID 和 idempotency 结果对账，再决定重跑或执行 recovery。

### Smoke 6：完整可见链路与 64 KiB 边界

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --full
```

- 前置阶段：精确 `RESOURCES_EXPOSED`，authorized Role 是五项 Risk 权限全集，denied Role 仍为 legacy-only。
- 写入范围：创建唯一命名的不可变 Risk synthetic facts；临时撤销并恢复 authorized Role 的 read 权限；发送精确
  65,536-byte 成功体和另一个 65,537-byte 拒绝体；不会删除此前事实。
- 成功证据：Navigation、权限分离、完整读写链、同 key replay、413 RFC 9457 和唯一 Nacos 实例全部成立，输出 `FULL_OK`。
- 失败停点：立即停止验收；保留已产生事实，权限恢复结果必须单独对账。
- 重跑/对账：先核对 Role 权限及唯一 Rule Set code，再使用新一轮唯一 code 重跑，不能把重复事实当作清理目标。

### Smoke 7：清理 synthetic IAM 夹具

```bash
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --cleanup-authorization-fixture
```

- 前置阶段：完整验收或明确中止后，管理员链路可用；它不是 Route 失败后的自动步骤。
- 写入范围：只清理两个 synthetic Account/Role/membership 和凭据文件；不删除 Permission、Route、Resource 或不可变 Risk facts。
- 成功证据：当前代次的夹具不再 ACTIVE，凭据文件已删除，历史软删除记录可以保留；重复清理仍成立，仅输出
  `CLEANUP_AUTHORIZATION_FIXTURE_OK`。
- 失败停点：保留剩余对象并停止，不扩大到其他 IAM 对象。
- 重跑/对账：按凭据文件中的当前代次派生精确角色码，并通过 Context 对账残留；修复明确阻塞后可幂等重跑。

以下四种 Rollout 的完整权威观察固定为共 14 次 GET：4 页/36 条 Route 列表、9 条 Risk Route Detail 和 1 次 Operations
Navigation。仅这些 GET 在收到严格 RFC 9457 `429 GATEWAY_RATE_LIMITED` 且单值、规范、当前配置范围
`1..6` 秒的 `Retry-After` 时最多重试一次。Gateway 先执行 ingress IP Guard、再执行 authenticated-principal
Guard，因此 Header 是当前拒绝层的最短等待；唯一重试前固定等待 `max(Retry-After, 6 秒)`，覆盖当前
gateway-admin 最慢的账号维度补充周期。任何并发限流、错误/畸形 Header、第二次 429 或超界值都停止。
该入口固定为无 Body、无 Idempotency-Key 的 GET，绝不用于 PATCH/POST。每次操作使用一个不在 post-read 或逐 Route
重置的 monotonic deadline：只读检查 90 秒、单条启用/禁用 180 秒、完整回退 720 秒；每个 HTTP 连接 timeout 取
`min(20, remaining)`，每次本地数据库权威读取也使用当时的 remaining 作为子进程 timeout，并在返回后再次检查
deadline。变更动作的等待位于同一个持久 rollout lock 内；PATCH 前到期不写，PATCH 后到期要求显式对账，
不会继续下一条 Route。

### Rollout 1：只读检查闭合阶段

```bash
python3 test-environment/scripts/risk_rule_configuration_rollout.py \
  --base-url http://localhost:28080 --inspect-stage
```

- 前置阶段：Platform 目录已准备，管理员与公开链路可用。
- 写入范围：不改目录；登录会产生 Session、安全审计和 Guard 影响。
- 成功证据：数据库、36 条 HTTP Route 列表、9 条 Risk Route Detail 和 Navigation 一致，仅输出一个闭合阶段。
- 失败停点：任何分页、版本、详情或投影漂移立即停止。
- 重跑/对账：先用 initializer 的只读阶段检查定位数据库/HTTP 差异，再重跑。

### Rollout 2：启用一条相邻 Route

```bash
python3 test-environment/scripts/risk_rule_configuration_rollout.py \
  --base-url http://localhost:28080 --activate-route ROUTE_CODE
```

- 前置阶段：六个 Risk Resource 全部隐藏，当前阶段必须是上表中该 Route 的左侧阶段；已在右侧时只读 no-op。
- 写入范围：持久 rollout lock 下只 PATCH 该 Route，一次版本递增；另有 Session、安全审计和 Guard 影响。
- 成功证据：同锁 post-read 精确到右侧阶段并输出该阶段。
- 失败停点：并发漂移、409 不一致或 `UNKNOWN_REQUIRES_RECONCILIATION` 立即停止，绝不启用下一条。
- 重跑/对账：先执行 `--inspect-stage`、Snapshot waiter 和 runtime probe；未知结果使用 recovery，不直接重放新 key。

### Rollout 3：禁用一条相邻 Route

```bash
python3 test-environment/scripts/risk_rule_configuration_rollout.py \
  --base-url http://localhost:28080 --disable-route ROUTE_CODE
```

- 前置阶段：Resource 已隐藏，当前阶段必须是该 Route 的右侧阶段；已在左侧时只读 no-op。
- 写入范围：持久 rollout lock 下只 PATCH 该 Route为 `DISABLED`，版本递增一次。
- 成功证据：同锁 post-read 精确到左侧阶段并输出该阶段。
- 失败停点：未知、漂移或 post-read 不精确即停止，不能继续禁用更早 Route。
- 重跑/对账：先检查持久化与 runtime 证据；未知结果必须走 recovery 协调器。

### Rollout 4：回退到 BASELINE

```bash
python3 test-environment/scripts/risk_rule_configuration_rollout.py \
  --base-url http://localhost:28080 --reconcile-baseline
```

- 前置阶段：Resource 已隐藏，Route 必须构成任一获批闭合前缀。
- 写入范围：在一个 rollout lock 内按逆序逐条禁用，绝不改 Resource 或删除 Risk facts。
- 成功证据：完整权威 post-read 为 `BASELINE` 并只输出该阶段。
- 失败停点：任一 PATCH 未知即停止；此前已确认的逆序步骤可能已经持久化。
- 重跑/对账：重新 `--inspect-stage` 确认当前闭合前缀；只有已确认的下一条逆向边才可继续。

### Recovery：恢复一条失败的相邻发布

```bash
python3 test-environment/scripts/risk_rule_configuration_recovery.py \
  --base-url http://localhost:28080 --recover-route ROUTE_CODE --timeout-seconds 60
```

- 前置阶段：Resource 隐藏，持久化状态只能是该 Route 的左侧或右侧阶段；阶段名由共享表推导，操作员不输入。
- 写入范围：左侧已稳定时只读；右侧时先证明 next SUCCESS，或证明 prior Last Valid 坐标并通过行为探针，随后才禁用该 Route。
- 成功证据：输出 `ALREADY_AT_PRIOR_STAGE`，或在 prior Snapshot 与行为都成立后输出 `RECOVERED`。
- 失败停点：未知/malformed runtime、行为探针失败、disable 未知或 prior 未收敛均停止；不触碰 Resource 和 Risk facts。
- 重跑/对账：重新执行 stage inspector；只在仍是同一相邻边时重跑 recovery。任何未知 Route 结果都不会自动 cleanup。

`--timeout-seconds 60` 分别约束 Recovery 的 Snapshot transition observer / stage waiter，不是整个协调器的
wall-clock 上限，也不替代 RouteRollout 单条禁用自己的 180 秒 action deadline；Recovery 各段均有界，但总时长可超过
60 秒。

## 时间范围下拉组件（2026-09-05）

仅更新 Operations 前端镜像。公共 `TimeRangePicker` 折叠为单个范围输入框；展开后桌面端左侧显示今天、最近 3 天、
最近 7 天和自定义快捷项，右侧显示中文日期及 24 小时时分秒面板。移动端快捷项置于日历上方，390×500 短屏弹层
固定在视口安全边距内并支持垂直滚动。自定义范围须明确确认；取消、Escape、点击外部、清空及重置不会提交草稿。
选择仍只更新表单，点击查询后才发送条件；快捷边界在提交时冻结，后续刷新与控件显示保持一致。
范围框在宽屏中缩为普通筛选项的一列并统一为 34px 高；1024px 等中等宽度跨两列保证完整时间，390px 占满单列。

79 项前端测试和生产构建通过。隔离浏览器验证三组 Asia/Dubai 自然日到 UTC 边界、自定义秒值、确认/取消、
清空/重置、月份前后导航、390×844 与 390×500 布局，未出现横向溢出或页面异常。`/audit` 与 9 个构建文件
逐字一致，入口为 `/assets/index-DJtOmMm4.js`。最终浏览器回归同时覆盖 1024、1440、2048 和 390px 的紧凑布局。10 个 test 容器健康，仅 `operations-console-web` 实例更新；
Platform、BFF、Gateway、Vault、基础设施、权限目录和数据库均未改变。

## API 路由管理（2026-09-05 已部署）

本轮 Operations Gateway 控制面对应的手工增量为
[`sql/2026-09-05-operations-gateway-control-plane.sql`](sql/2026-09-05-operations-gateway-control-plane.sql)。该文件新增
6 条公开 Route、3 个 Console Resource 和 2 个 ACTIVE Permission。该文件已使用 MySQL batch 模式显式应用一次；
当前目录为 27 条 Route、15 个 Console Resource、14 个 ACTIVE Permission。

该 SQL 仍不进入 Compose、应用启动或 `initialize.py` 的自动流程。此次只定向更新 Platform、Gateway、Operations BFF
和前端；没有重放全量初始化或 Bootstrap，也没有重建 MySQL、Redis、RabbitMQ、Nacos、Nginx 或 Vault。

Platform 首次启动暴露出 test Compose 未注入 Redis 容器地址的问题：资源锁启用后 Redisson 使用了本机默认
`127.0.0.1:16379`。Compose 现为 Platform 显式注入 `redis:6379` 和 test 密码，并等待 Redis healthy；
[`scripts/test_platform_redis_deployment.py`](scripts/test_platform_redis_deployment.py) 保留该部署配置门禁。

部署后 10 个容器均 healthy，Gateway 连续成功加载 1 个 Target / 27 条 Route，Snapshot 失败计数为 0。
[`scripts/smoke.py`](scripts/smoke.py) 已覆盖登录、15 项导航、Target/Route 列表、筛选和详情；
[`scripts/gateway_control_plane_smoke.py`](scripts/gateway_control_plane_smoke.py) 通过公开 HTTP 验证未认证拒绝、Route
停用、幂等重放、旧版本冲突、Snapshot 移除以及恢复。验收 Route 最终为 ACTIVE；Break-glass SQL 未执行。

### Break-glass 恢复（仅管理链路误停用）

独立恢复资产为
[`sql/2026-09-05-operations-gateway-control-plane-break-glass.sql`](sql/2026-09-05-operations-gateway-control-plane-break-glass.sql)。
它只用于 `operations-console-bff` Target 或六条 `operations-gateway-*` 管理 Route 被误停用，且普通 Operations
页面已无法恢复的事故场景。该文件不得自动执行，不进入 Compose、`initialize.py`、应用启动、自动测试或日常发布；
本轮没有在任何数据库执行它。

执行前必须完成以下事项：

1. 建立事故/变更工单，取得当班 Platform Owner、Security 与 DBA 所要求的审批，并记录恢复范围；
2. 确认数据库语句审计已启用，使用短时、个人可归属的 Break-glass 数据库账号，禁止使用应用账号、共享 root
   或把应用凭据复制给操作员；
3. 只读保存目标、六条 Route、两个 Permission 及当前 Platform Snapshot 的事前证据，核对脚本校验和与已审阅版本；
4. 从仓库根目录使用 Oracle `mysql` 的 batch mode 执行，禁止 `--force`，也不得在交互式会话中用 `SOURCE`
   运行。连接信息从获批的短时凭据来源提供，工单号必须作为审核过的字面量写入 `--init-command`，不得拼接未校验的
   Shell 输入：

   ```bash
   mysql --batch --host='<approved-host>' --port='<approved-port>' \
     --user='<personal-break-glass-user>' --password \
     --init-command="SET @break_glass_ticket_ref = 'INC-2026-000001';" \
     < test-environment/sql/2026-09-05-operations-gateway-control-plane-break-glass.sql
   ```

   必须以客户端退出码 `0` 和 Procedure 返回的成功结果共同判定执行成功；任何非零退出码都视为失败并立即停止。
   Oracle `mysql 8.0.42` 已用“初始化会话变量 → 查询不存在表 → 后续哨兵语句”验证此 batch 调用：返回码为 `1`，
   后续语句未执行。该账号还需按已审批时窗具备脚本创建、调用和删除临时恢复 Procedure 所需的最小权限。

脚本以本次事务专用的 `SERIALIZABLE` 隔离启动，确保即使调用会话默认使用 `READ COMMITTED`，Header、Query 和
Guard 的范围锁仍会阻止六条 Route 子项并发插入。随后锁定并断言稳定 Target ID/Code/Service/Channel、两个 ACTIVE Permission、六条 Route 的 ID、Method、上下游
Path、Target、Profile、授权模式、Permission、Cookie、Body/Timeout，以及完整 Header、Query 和 Guard 配置。任一身份
缺失、被占用或配置漂移都会 `SIGNAL` 并回滚。更新 Root 后、提交前，脚本还会按
`GatewayConfigurationCompiler` 的相同规则检查全目录所有有效 Route：只有 Route 与 Target 同时为 `ACTIVE` 才参与，
并按 HTTP Method、Path Segment 数、大小写敏感的 Variable 名数量和逐段固定值判断模板重叠；任何冲突都会回滚，
避免提交一份 Gateway 必然拒绝的 Snapshot。此时不得删改异常数据后盲目重跑。恢复只把 `DISABLED` Root 改为
`ACTIVE`，逐行执行 `version + 1` 并更新 UTC 时间；原本已为 `ACTIVE` 的行不推进版本。它不新增 Route、权限、应用
Audit Event 或 Rollback 表。

`updated_by` 按 `PRESERVE_APPLICATION_UPDATED_BY` 约定保留原值，因为该列只表示最后一次真实应用命令的 Account。
脚本不会写管理员 ID、应用 Actor、`0` 或负数伪 Actor。此次异常操作的真实归属由 Break-glass 数据库账号审计与
必填审批工单共同承担。成功时临时 Procedure 会被删除；校验失败时 Procedure 可能保留以保存原始失败现场，需在
完成数据库审计取证后由 DBA 显式删除，且不得调用其绕过新的审批。

执行后必须保存脚本末尾验证查询与数据库审计记录，并完成运行态恢复验证：

1. 数据库中唯一 `operations-console-bff` Target 和六条精确管理 Route 均为 `ACTIVE`，只有执行前为
   `DISABLED` 的行版本增加一次；
2. 通过 Platform 私有只读 `GET /platform-support/gateway-config/v1/public-snapshot` 确认 Snapshot 包含该 Target
   与六条完整 Route，且没有新增或修改其他配置；
3. 等待 Gateway 正常轮询，确认其私有 Snapshot 指标出现晚于本次恢复的成功加载时间、连续失败归零，Target/Route
   数量与 Platform 一致；不得重启后依赖静态 Route 或人工写 Gateway 缓存；
4. 从公开 HTTP 入口以获授权账号验证 Target/Route 列表和详情，再由正常 Operations 命令处理后续配置并生成应用
   Audit Event。Break-glass 只恢复可管理性，不代替正常配置发布或审计链路。

## Risk V1 配置增量部署（2026-09-11）

本次三个应用的合同和旧 Hash 兼容要求见 [V1 配置接口合同](../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，实际部署/验证状态只见 [当前状态台账](../agent-docs/CURRENT_IMPLEMENTATION.md)。已有 test 库使用服务拥有的 [人工增量 SQL](../risk-compliance-service/docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql)，不运行空库初始化。`--verify-risk-catalog` 核对一个 Point、五个 Action 和四个 Parameter 的完整目录。

在更新前保留三个应用的旧 image ID，备份 test Risk 库及涉及的 Platform Resource 行。镜像须先构建完成；暂时隐藏六个风控 Resource，再停止 Web/BFF/Risk 以结束旧进程对规则库的访问，核对零连接后应用增量 SQL。仅按 Risk → BFF → Web 顺序替换这三个已审阅镜像；现有 Platform、Gateway、Nginx、Vault 和基础设施不因本增量自动重建。健康及目录核对成功后恢复原 Resource 状态。此过程有短暂维护窗口，既有九条 Route 与权限保持原配置。

现有 `risk_rule_configuration_smoke.py --full` 使用新字段齐全的 Rule，并验证纯终态 Freeze/Candidate 和精确 64 KiB 边界。新增显式配置验收：

```sh
python3 test-environment/scripts/risk_rule_v1_configuration_smoke.py --verify --base-url http://localhost:28080
```

默认不带 `--verify` 不执行操作。此命令只接受仓库既有 Loopback 入口和完整 `RESOURCES_EXPOSED` 阶段，复用已准备的 synthetic authorized/denied IAM 夹具。它通过实际 Nginx/Gateway/BFF/Risk 验证主体目录、参数、冷却/标签保存与规范化重放、基于旧版保存新版、旧版退役、拒绝文案及幂等冲突、非终态冻结拒绝和纯终态冻结；读取 test Risk 的运行表计数，验证没有执行副作用。合成规则是不可变证据，不自动删除；IAM 夹具按既有显式 cleanup 命令收尾。

写入新格式后不能将 Owner 直接退回忽略新增字段的旧代码，不能以删列或删规则恢复旧版本；异常时保持入口关闭，保留备份和已提交事实，按状态核对后修复。
