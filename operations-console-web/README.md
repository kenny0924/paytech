# Operations Console Web

租户访问与成员权限查询的现行合同见 [需求 API](../requirements/20260914/platform-support-租户访问与成员权限查询/api.md)：8 项能力，复用既有 IAM 模型；当前研发、验证和部署状态见 [根台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)。

> 本轮规则集整体管理的现行合同见[需求技术方案](../requirements/20260911/operations-console-风控规则集列表优化/technique.md)：沿用原接口版本，一集一行、整体删除及实际生效使用保护；Web 隐藏 Candidate 页面，后端历史合同保留。阶段确认与证据见[交付记录](../requirements/20260911/operations-console-风控规则集列表优化/delivery.md)，实现与环境状态只见[当前台账第 16 节](../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)。


> 2026-09-11 原 V1 风控配置增量的接口与兼容规则见 [需求与接口合同](../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，本次验证、数据库与部署状态见 [当前状态第 15 节](../agent-docs/CURRENT_IMPLEMENTATION.md#15-operations-risk-v1-配置增量2026-09-11)。下文带日期的旧验证记录保留为对应基线证据。

Operations 内部运营平台的独立前端工程。React 19 + TypeScript + Vite 8 + Ant Design 6，所有业务数据通过同源 `/api/operations` 进入 Gateway → Operations BFF → Platform Support。

## 本地开发

```sh
npm ci
npm run dev
```

页面预览：`http://127.0.0.1:18301`。`OPERATIONS_GATEWAY_URL` 只用于 Vite 开发代理，默认 `http://127.0.0.1:18080`；不是前端打包变量，不允许配置任意浏览器 API 域名。

默认登录联调从 Nginx 的 HTTPS Console Origin 打开，默认 `https://operations-console.local.test`；Nginx 将页面转发到 Vite、`/api/operations` 转发到 Gateway。Gateway 和 BFF 必须配置相同的精确 `OPERATIONS_CONSOLE_ORIGIN`。用户明确授权的 Docker test 入口为 **`http://localhost:28080`**，无需证书，启用条件见 [`test-environment/README.md`](../test-environment/README.md)。Vite 预览地址仍仅用于界面开发。

## 当前功能

- Email/Bootstrap 账号登录、当前标签页整页刷新后的会话恢复、Token 轮换、退出；强制改密后重新登录，Bootstrap 改密后创建首位管理员。
- Platform 当前授权的分层导航：系统管理 → 人员管理、角色管理、审计日志、API 路由管理。前端按 `parentResourceId` 组树、按同级 `sortOrder` 排序；目录展开收起，当前页面的祖先自动展开，没有可访问子页面的目录隐藏。
- 页面只识别预先注册的 `OPERATIONS_ACCOUNTS → /accounts`、`OPERATIONS_ROLES → /roles`、`OPERATIONS_AUDIT → /audit`、`OPERATIONS_GATEWAY_CONFIGURATION → /gateway-configuration`；不加载服务端脚本或任意组件，也不补回缺失的父资源或无权限页面。Platform 拥有目录与权限事实，前端仅将授权结果转换为菜单控件。
- 人员分页、创建、分配角色、停用、启用和删除；操作原因、期望版本与命令幂等键。多个角色名称使用中文逗号分隔。
- 角色管理通过 `/iam/v1/roles` 服务端分页展示名称、编码、类型和关联员工数；支持创建/编辑/删除 Operations 自定义角色。创建与编辑在抽屉保存名称、权限与原因，删除使用独立确认弹窗。系统角色只读，角色编码创建后不可改；给人员分配角色仍在人员管理中操作。
- 审计筛选与分页、操作者、操作对象、结果、原因和 Request ID；只读详情展示已记录的字段变更与属性。
- API 路由管理按 Route/Target 分页筛选并展示详情；`OPERATIONS_GATEWAY_CONFIGURATION_MANAGE` 独立控制状态按钮。
- 通知管理（顶层分组「通知管理」）：通知模板列表与详情（`/notification-templates/:templateCode`；发送路由、语言标签页、编辑停顿约 500ms 自动预览、认证模板缺 `${link}` 禁止保存、409 冲突横幅保留草稿）、发送账号（本环境是否已配置、被引用路由、启停并提示受影响模板）、发送记录（默认最近 7 天、跨度不超过 31 天，抽屉查看投递与尝试，不显示邮箱与参数值）。`OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE`、`OPERATIONS_NOTIFICATION_PROVIDER_MANAGE` 控制写操作；写请求按草稿复用 `Idempotency-Key`。设计见 `docs/operations-console.pen` 中「通知管理 ·」画板。
- 加载、空结果、无权限、冲突、服务错误状态；按钮由当前导航中的 BUTTON Resource 控制。服务端仍对每次请求重新授权。
- 导航只在登录进入工作台时加载，失败时可以点击重试。站内切页只加载目标页数据，不重复请求菜单或因菜单加载卸载页面；切换窗口或浏览器标签页也不自动刷新。列表手动刷新及保存后的更新保持原流程，每次 API 请求仍由服务端鉴权。

## 公共分页

人员、角色、审计、Gateway Route 和 Gateway Target 列表统一使用 [`usePaging<T>(path)`](src/hooks/usePaging.ts)，一次接入 Ant Design Table：

```tsx
const { data, loading, error, pagination, refresh } = usePaging<Account>('/iam/v1/accounts');
<Table<Account> rowKey="accountId" dataSource={error ? [] : data.items}
  loading={loading} pagination={pagination} columns={columns} />
```

默认第 1 页、每页 10 条，可选 `10 / 20 / 50 / 100 / 200`。修改每页数量会回到第 1 页；翻页向服务端发送 `page`、`size`，切换或卸载时取消旧请求，迟到结果不会覆盖新页。手动刷新或保存成功后调用 `refresh()`。

分页查询类型为 `PagingRequestBody`，`page`、`size` 仍通过 GET 查询参数发送；响应类型为 `PagingResponseBody<T>`，成功 JSON 沿用 `{ "items": [], "page": 1, "size": 10, "total": 0 }`：`items` 是当前页数据，`total` 是匹配记录总数。`/iam/v1/accounts`、`/iam/v1/roles`、`/audit/v1/events` 和两个 `/gateway-admin/v1` 列表使用相同契约。

人员创建和角色分配弹窗通过 `requestAllPages<Role>()`，对同一角色接口按每页 200 条分批读取全部选项，超过 200 个角色也不会遗漏。全部成功后才显示选项；关闭弹窗会取消请求，中途失败或总数变化不展示部分结果，加载中或失败时禁止提交。

## 公共时间组件

[`DatePicker`](src/components/DatePicker.tsx) 是基础日期控件，复用 Ant Design 日历，提供中文月份/星期、简约弹层和 24 小时时间。默认仅选择日期，`showTime` 开启时分秒。表单值默认是 `YYYY-MM-DD`，含时间时是本地 `YYYY-MM-DDTHH:mm:ss`；清空返回 `undefined`。Enter 只确认日期，不连带提交外层表单，组件内不转换时区。

列表时间筛选统一使用 [`TimeRangePicker`](src/components/TimeRangePicker.tsx)：

```tsx
import { TimeRangePicker } from './components/TimeRangePicker';

<Form.Item name="timeRange" label="时间范围">
  <TimeRangePicker />
</Form.Item>
```

折叠时组件是一个紧凑的范围输入框；展开后左侧提供“今天 / 最近 3 天 / 最近 7 天 / 自定义”，右侧复用中文日期和 24 小时时分秒面板。快捷范围在提交查询时按浏览器本地自然日计算，包含今天，结束值为明日零点且不包含；提交后冻结当次绝对边界，使控件显示与后续刷新保持一致。自定义范围必须同时填写并且结束晚于开始，只有明确确认才写入表单；取消、Escape 或点击外部不会提交草稿。选择和编辑都不自行请求，只有点击“查询”才应用新条件；重置清空整个选择。审计筛选在宽屏中与普通筛选项等宽，1280px 及以下跨两列保证双端时间完整，480px 以下占满单列，控件高度与全局表单一致。窄屏下快捷项移到日历上方，短屏弹层可滚动且保留前后月份导航。

## Operations 角色配置

创建与编辑入口由 `OPERATIONS_ROLE_CREATE` / `OPERATIONS_ROLE_EDIT` 按钮资源控制。创建调用 `GET /iam/v1/role-permission-options` 加载权限树，编辑调用 `GET /iam/v1/roles/{roleId}` 同时加载角色版本与权限树；提交分别使用 `POST /iam/v1/roles`、`PUT /iam/v1/roles/{roleId}`。创建选项与创建命令使用后端 `OPERATIONS_ROLE_CREATE`，详情与修改使用 `OPERATIONS_ROLE_UPDATE`。

树由 BFF 聚合当前可授予的权限及菜单资源。前端按 Permission ID 展示已存授权；勾选目录选择其子孙，勾选子项补齐祖先访问权限，取消只移除对应子树，打开详情不会自动扩大授权。无资源绑定的权限保留在“其他权限”分组。空权限集合表示清空授权。角色保存成功后只重新加载一次导航，使菜单和按钮反映当前权限，不恢复切页或窗口切换时自动刷新。

编辑带 `expectedVersion`，相同输入的手动重试保留 `Idempotency-Key`；409 时需重新加载。失败保留输入，加载失败禁止提交，关闭或切换抽屉取消旧请求。实际越权、系统角色保护和写事务由 Platform 执行，前端不缓存授权事实。

Risk 的根目录与规则配置页共用同一个 READ Permission，四个写按钮分别绑定 SAVE、FREEZE、CREATE CANDIDATE 和 VALIDATE CANDIDATE。选择任一写权限只补齐一份 READ 和该写权限；取消包含已选子项的目录前明确确认，确认后才移除整个子树，不影响树外授权。

## Risk 规则配置

`/risk/rule-configuration` 是面向 Operations 的结构化规则编辑页，包含“规则集”与“TEST Candidate”两个页签。进入新建/编辑后使用同一 V1 页面中的左右分栏：左侧规则列表可收起、选择和排序，右侧编辑一条规则；规则集/规则基本信息在弹窗中编辑，底部显示未保存状态与完整新版本保存入口。空规则集也保留分栏结构。条件使用紧凑字段/操作符/值行及嵌套组，保留 NOT、比较方向和精确 Decimal；PASS/REVIEW/REJECT/不产生决策使用单选控件。拒绝代码和文案手工填写，冷却时长可选单位并以精确整数毫秒提交，主体选项来自 Risk 目录，标签手工输入代码。

页面不暴露 JSON/AST 或 Owner 内部节点字段；只在 Owner 返回的 `CHECKOUT_PREPARE_AUTHORING_V1` 与前端合同匹配时开放编辑。条件目录固定九个字段、十一个比较操作符与三个终态 Action 代码/版本；附加动作、主体绑定与参数由 Risk 返回并严格校验，未知或不完整能力失败关闭。切换规则、收起列表不丢失编辑，返回列表须确认放弃未保存的修改；含未决命令时仍保留原命令恢复入口。

“最新版本”只是浏览器的观察结果：前端使用精确 Decision Point/规则集编码、省略状态条件，以 `size=200` 读完总量稳定且分页窗口完整的版本流，再读取最大业务版本详情。不完整、跨页总数变化、重复身份或坐标不一致都不允许开始新建/编辑/冻结；Risk 的 409 仍是并发事实权威。

保存使用实际 `JSON.stringify` 文本的 UTF-8 字节数：从 49,152 字节预警，65,536 字节仍可提交，65,537 字节起禁止。Save、Freeze 和 Candidate Create 的随机幂等键以精确 Path/Body 保留；Validate 只使用 `risk-validate-v1-{releaseId}-{expectedVersion}`。浏览器不自动重放 POST；超时、断连或 5xx 的未知结果保留原 Path/Body/Key，只能由操作员明确重试同一命令。

TEST Candidate 只有 DRAFT、静态校验中/已通过以及 REJECTED 呈现。只有发起 `DRAFT -> VALIDATING`、且当前仍是 `VALIDATING/version=2` 并没有检查结果的同一 Account，才能恢复原始 `expectedVersion=1` 校验；八项检查的顺序和结果均来自已严格解码的 Owner 响应。Shadow、审批、生产激活和运行时决策评估不在该 Web 切片内。

规则内容、当前编辑基线与未决命令信封只保留在当前 React/页面内存，不写入 Local Storage、Session Storage 或 IndexedDB，也不输出到 console。编辑内容相对加载基线有任何差异，或存在结果未知的命令时，站内路由离开使用异步确认，关闭/刷新页面使用 `beforeunload` 提示；确认或取消离开都不会改写未决命令信封。

本地 Node 回归为 878/878，其中未保存导航覆盖 React 19 `RouterProvider` 与 memory router 的真实状态竞态；TypeScript 严格检查与 Vite 生产构建通过。当前仓库仍没有浏览器 DOM/E2E 测试框架，因此这些证据不声称真实浏览器交互、端到端链路或任何环境部署已验收。当前实现、验证、迁移和部署状态只以工作区 [`agent-docs/CURRENT_IMPLEMENTATION.md`](../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

## 公共错误通知

接口失败由调用方通过 `useErrorNotification()` 统一显示在右上角。通知卡片展示安全的 `message`、`code` 和 `requestId`，支持复制三项信息和手动关闭；同时最多显示 3 条，同一 Request ID 与错误码合并。接口未返回 Request ID 时显示“未返回”，错误体缺少该字段时优先使用响应头 `X-Request-Id`。

登录、会话恢复、菜单、分页列表及人员/改密操作均复用此通知，不再显示页面内的红色接口错误条。字段校验、停用警示、列表空态和手动重试入口保留。取消请求和旧会话响应不弹通知；未知异常使用固定文案，不展示原始错误、请求体或堆栈。新增页面捕获异常时应传入原异常，例如 `notifyError(error, '操作失败')`，不要只传 `error.message`。

## 认证与重试

完整 HTTP 响应类型采用 `ResponseBody` 后缀：登录与刷新使用 `TokenGrantResponseBody`，当前上下文使用 `SessionContextResponseBody`，导航使用 `NavigationResponseBody`。分页中的 `Account`、`Role`、`AuditEvent` 和导航中的 `ConsoleResource` 保持嵌套业务对象名称；类型命名不改变 JSON 字段或请求参数位置。

Login 和 Refresh 的 JSON 响应同时返回 `accessToken`、`expiresIn`、`refreshToken`、`refreshExpiresIn`。按用户 2026-09-04 的最新要求，两种 Token 及对应绝对过期时刻保存在当前标签页的 `sessionStorage`，模块启动时恢复到内存，整页刷新不再直接丢失登录。此决定覆盖此前“Token 仅存模块内存、刷新页面需重新登录”的限制；不使用 localStorage 或 Cookie，不缓存密码、Account 上下文或权限，不把凭据放进 URL 或输出到 console。

恢复后，有效 Access JWT 继续用于正常 Authorization 请求，临近或已过 Access 有效期时发送 JSON Refresh，页面恢复本身不强制轮换。恢复凭据不代表已通过授权：当前身份、菜单和权限继续从服务端读取并逐请求校验；客户端保存的到期时刻不能延长 Platform 的会话期限。

sessionStorage 遵循浏览器的 page session 生命周期。浏览器通过 opener 复制标签页或恢复关闭标签时，可能复制或恢复存储内容；本应用不主动跨标签页共享 Token，也不承诺每个新标签必需登录或关闭标签必然永久清空。

`POST /auth/v1/refresh` 显式发送 JSON `{ "refreshToken": "..." }`，不带 Authorization；`POST /auth/v1/logout` 发送同样的 JSON，并携带当前 Access JWT。所有 fetch 固定使用 `credentials: 'omit'`，不使用 Cookie，不发送 Cookie 专用 CSRF Header。写请求使用 JSON，并继续由服务端检查精确 Origin；受保护请求携带 Access JWT。

Refresh 在同一会话内单飞，成功后把两种 Token 和到期时刻作为一份记录同时替换内存及 sessionStorage，不通过 Web Locks 或 BroadcastChannel 同步到其他标签页。登录、退出和改密完成后的会话版本检查会丢弃迟到响应，防止旧账号结果恢复会话。退出会等待已开始的轮换，使用最新凭据发送撤销请求，并清除当前会话的内存及 sessionStorage；远端失败不会清除后来新建的会话。改密完成、Refresh 返回 401、刷新后的 GET 唯一一次重试仍返回 401、Refresh/会话到期或恢复数据无效时也清理保存的凭据。第一次业务 GET 返回 401 时仍允许刷新，并在成功后最多重试一次；迟到的 401 不清除后来新建的会话。

恢复身份时遇到临时 GET 429、503 或网络错误，保留当前页会话并显示可重试的恢复页，不把临时故障当作退出；GET 429/503 不删除 sessionStorage。站内查询仍遵守已有重试上限，写命令不会因恢复或限流而自动重放。

发送 Refresh 前先移除可恢复的 sessionStorage 快照，成功后保存新一代；只有确认返回 429 时才恢复原快照。Refresh 网络错误或 5xx 不能确认旧 Token 是否已在服务端消费，因此当前页保留内存供重试，但整页 reload 不恢复旧快照，避免重放已消费的 Token。

浏览器不允许访问 sessionStorage 或写入失败时，回退为当前页内存，并尝试删除旧快照；这类环境不保证整页刷新后恢复登录。

查询最多在一次成功刷新后重试；写命令不会自动重试。人员管理弹窗在相同 Payload 的手动重试中保留同一个 `Idempotency-Key`，修改输入或开始新的操作时重新生成。

普通业务校验和参数错误（HTTP 400 的 `BUSINESS_CHECK_FAILED` / `INVALID_REQUEST`）、并发冲突（HTTP 409 的 `CONCURRENT_REQUEST`）直接显示后端约定的安全业务消息。改密、首位管理员及人员管理表单复用该提示；登录表单可显示上述 400 提示，凭据失败仍使用统一文案，避免暴露账号是否存在。401/403、系统错误、未知错误码或无有效消息的响应继续显示固定提示；空响应、非法 JSON、`null` 或数组错误体均安全回退。

## 验证

```sh
npm test
npm run build
```

Node 测试通过真实 loopback HTTP 验证 JSON Token 收发、Refresh 单飞、登录/退出/改密竞态、独立会话实例、迟到响应丢弃、公共业务消息展示与系统消息隐藏、异常错误体安全回退、GET 最多重试一次和写命令不自动重试。菜单测试覆盖层级与排序、空目录/孤立节点隐藏、组件路径白名单和当前页面祖先。构建包含 TypeScript 严格检查。业务页面只有真实 API 数据，不包含伪造人员、角色或审计记录。

2026-09-04 公共分页改动的 `npm test` 40/40 通过，`npm run build`（含 TypeScript 检查）通过。新增分页测试覆盖默认值、切换数量回第 1 页、三个列表的服务端参数、405 个角色分三批读取、部分失败、取消和旧页迟到结果。已与 BFF、Platform 同步部署 mor-test；真实浏览器确认三个列表默认 10 条、下拉含五档数量、角色页切换 200 条、审计第二页切换 20 条回到第一页，以及人员弹窗完整加载角色选项。未提交任何角色变更；验证会话已退出。

此前会话恢复改动的 `npm test` 32/32 通过，含刷新后 GET 重试仍 401 清理存储、迟到 401 不清新登录；生产构建及最终 `tsc --noEmit` 通过。前端镜像已重建，`mor-test` 的 `operations-console-web` 为 running/healthy。真实浏览器在 `http://localhost:28080/roles` 登录后连续整页刷新两次，均保持角色页并显示 2 个角色；退出后刷新仍为登录页。该次只更新前端，没有后端或数据库改动，没有部署到公网。

此前真实登录、IAM 查询、Refresh 轮换、Logout 及撤销后 Refresh 拒绝已通过，部署验收由 `test-environment` 记录。Platform 的认证切片需手工 SQL、Bootstrap/ACTIVE Key 初始化及运行密钥；Vault 按用户要求暂用明确隔离的开发 Mock，详见 `platform-support-service/README.md`。

浏览器支持时额外注册 `open_operations_page` WebMCP 工具，仅导航到当前可见且已注册的菜单；不提供敏感数据或写操作工具。当前未进行支持 WebMCP 的浏览器契约验证。

## Docker 静态部署

先在本地使用锁定依赖生成 `dist`，再由 `test-environment` 统一构建和启动镜像：

```sh
npm ci
npm run build
```

`Dockerfile` 仅复制已构建的 `dist/` 和 `deploy/nginx.conf`，不在镜像内运行 npm 或下载前端依赖。构建上下文通过 `.dockerignore` 限定为这两类文件及 Docker 构建声明，不包含源码、`node_modules` 或环境文件。基础镜像固定为 `nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c`，已核对 `linux/arm64/v8`。

容器以非 root `nginx` 用户监听 `8080`，服务名为 `operations-console-web`；可设只读根目录，并为 `/tmp` 挂载可写 tmpfs。`/healthz` 返回 `200 ok`，页面路由使用 SPA fallback，`index.html` 禁止缓存，Vite 带内容哈希的静态文件缓存一年，缺失的静态资源返回 `404`。

边缘 Nginx 在配置的 Console Origin 下将页面转发到 `operations-console-web:8080`，并单独将 `/api/operations` 转发到 Gateway；当前 test 使用 `http://localhost:28080`。静态容器不持有证书、不代理 API，直接请求其 `/api` 或 `/api/` 子路径返回 `404`；`OPERATIONS_GATEWAY_URL` 仍仅用于 Vite 开发代理，对静态镜像无效。

2026-09-04 自定义角色配置：`npm test` 58/58 和 `npm run build` 通过，前端已部署到 `mor-test`。
`/roles` 的 HTML 与入口静态文件和本次构建匹配，真实 API 验证角色配置、人员授权、即时撤权和防提权通过。

浏览器验证使用独立临时 fixture 驱动真实 App，并在隔离的 headless Chrome 中运行，不读取用户浏览器会话。
创建/编辑抽屉、系统角色只读、同权限多节点同步、目录独立选择、纯分组禁选均通过；
POST 使用去重权限与幂等键，PUT 携带当前版本，每次保存只触发一次导航和角色列表重载。
桌面及 390px 移动端截图已核对，字段和抽屉未溢出。真实业务 HTTP 链路另由 mor-test 烟测验证。

## 权限树联动修正（2026-09-04）

五个操作现在有独立后端权限：创建人员、禁用人员、分配角色、创建角色、编辑角色可分别授权。
勾选父节点选中全部子孙，勾选单个子节点只补齐该路径的上级访问权限，不选择兄弟；取消父节点清空子树，
取消单个子节点保留兄弟，父节点显示半选。纯分组也可以批量勾选，但不作为 Permission ID 提交。
已存权限只用于推导勾选/半选状态，打开编辑器或保存未改表单不会自动扩大权限。

本节覆盖前文“父子独立、不隐式联动”的旧交互。真实重复绑定同一个 Permission ID 仍代表同一业务授权；
本轮通过拆分后端五项操作解决意外连带授权，没有只在前端伪造独立选项。
62 项测试与生产构建通过；隔离 headless Chrome 验证单选创建人员、父级全选、取消边界、保存重开精确授权、
旧父级 READ 不扩权、单次导航/列表重载和 390px 抽屉均通过，临时 fixture 已清理。

## 系统角色查看（2026-09-04）

系统内置角色在已有角色详情授权下显示“查看”，复用原详情接口与授权上限，不增加接口或权限。
抽屉标题为“查看角色”，展示名称、编码、只读权限树及已授予数量；不显示保存和操作原因。
查看模式在打开时明确固定，即使详情返回迟到也不能提交；自定义角色继续使用原编辑入口。
前端62项回归与生产构建通过，test真实HTTP读取PLATFORM_ADMIN/OPERATIONS_VIEWER分别返回9/4项权限。
本次只更新静态前端容器。

实际App/headless验证两个系统角色仅发GET、输入与权限树不可改，关闭后自定义角色仍可编辑保存；临时fixture已清理。test入口与角色模块均匹配本次构建。

## 角色删除与关联员工数（2026-09-04）

列表的 `RoleListItem` 新增 `version`、`employeeCount`，人员内嵌角色仍使用四字段 `Role`。
人数包含当前 Operations 成员的有效角色关联，停用账号也计入；同一员工只计一次。

`OPERATIONS_ROLE_DELETE` 按钮资源允许删除人数为 0 的自定义角色；有关联时提示先在人员管理调整分配，系统角色继续只读。
确认弹窗要求操作原因，发送 `DELETE /iam/v1/roles/{roleId}`，JSON 包含 `expectedVersion`、`reason`，并携带 `Idempotency-Key`。
等待响应时禁止重复提交或关闭，失败保留原因并使用公共错误通知；相同请求的手动重试复用幂等键，409 后需刷新列表。
删除成功刷新当前页，末页只剩这一项时返回上一页；后端保留角色与审计历史，列表和分配选项不再显示已删除角色。

本轮64项测试、TypeScript生产构建和实际App/headless验证通过，覆盖权限按钮、关联禁删、系统只读、原因必填、
重复提交保护、503手动重试、409刷新和末页回退。桌面及390px弹窗已核对，临时浏览器与fixture已清理。


## 人员删除（2026-09-04）

`OPERATIONS_ACCOUNT_DELETE` 独立按钮资源控制删除入口；仅对非当前登录人的 `ACTIVE` / `DISABLED` 账号显示。
确认弹窗展示脱敏 Email 和账号 ID，说明删除后无法登录、角色关联解除且不可恢复；操作原因必填，最多 500 字符。
`DELETE /iam/v1/accounts/{accountId}` 发送 `{expectedVersion, expectedMemberVersion, reason}` 与 `Idempotency-Key`，成功返回 `204`。
账号永久关闭、成员及角色关系撤销、密码与会话处理、最后管理员保护和审计均由 Platform 在事务内执行，前端不替代业务判断。

人员和角色复用 `DeleteConfirmationModal` 的原因输入、提交锁定、冲突与错误通知；两种命令分别构造自己的请求字段。
失败保留输入，相同目标、版本与请求体的手动重试保留幂等键，不自动重试删除；任一版本或原因改变会生成新键。
`409` 后禁止继续提交，需刷新列表获取新版本并重新确认。提交中禁止重复、关闭和修改输入，关闭后不把原因或命令带到其他对象。
成功只刷新人员列表，末页唯一项被删除时回到上一页；不刷新导航，角色关联数在下次角色查询时自然更新。
审计列表支持显示 `OPERATIONS_ACCOUNT_DELETE` 为“删除人员”。

本轮 `npm test` 67/67 与生产构建（含 TypeScript 检查）通过。独立 headless Chrome 使用合成 API 驱动真实 App，
验证了权限/本人/状态限制、中文逗号角色显示、脱敏身份与原因、提交锁定、双版本请求、末页回退、取消与抽屉隔离、
503 同键手动重试、409 刷新后使用新双版本，以及不刷新导航。角色删除的单版本请求与重试行为回归通过；
审计“删除人员”标签和 390px 确认弹窗均已核对，无未捕获页面异常。临时浏览器、fixture 与 Vite 已清理；实际 test 部署由独立部署流程验收。

## 人员启用（2026-09-04）

`OPERATIONS_ACCOUNT_ENABLE` 独立按钮资源控制“启用”入口，仅对列表中的 `DISABLED` 人员显示；
`ACTIVE`、`CLOSED` 等其他状态不显示，Bootstrap 不属于该人员列表。
启用复用停用的人员确认抽屉，展示脱敏身份、必填操作原因（最多 500 字符），并提示启用后需要重新登录。
`POST /iam/v1/accounts/{accountId}/enable` 只发送 `{expectedVersion, reason}` 和 `Idempotency-Key`，成功返回 `204` 后刷新人员状态。

启用和停用均不加载或提交角色字段。提交中禁止修改输入、重复提交和关闭；失败保留原因并展示公共错误通知，
不自动重试，相同目标、动作、版本和原因的手动重试复用幂等键。`409` 后禁止继续提交，须明确刷新列表、获取新版本后重新确认。
成功只刷新人员列表，不重载导航；角色分配、删除与中文逗号角色展示保持原有行为，审计新增“启用人员”标签。

本轮 `npm test` 71/71 与生产构建（含 TypeScript 检查）通过。隔离 headless Chrome 使用合成 API 驱动真实 App，
验证了权限/状态入口、原因必填、精确请求字段、提交锁定、启用状态更新、停用回归、503 同键手动重试、409 刷新与新版本提交，
以及取消、角色/删除抽屉隔离、审计标签和 390px 布局。无未捕获页面异常；临时浏览器、fixture 与 Vite 已清理，实际 test 发布由独立部署流程执行。

## 审计筛选与详情（2026-09-04）

审计列表支持时间范围、操作者 ID、操作类型、结果和 Request ID；文本条件精确匹配，操作类型可选择建议或直接填写历史/未来操作代码。
时间支持今天、最近 3 天、最近 7 天和自定义范围。快捷范围按浏览器本地自然日计算并包含今天；自定义按本地时区输入，必须同时填写开始和结束，且开始早于结束。传输时转为 ISO UTC，开始时间包含、结束时间不含。
只有点击“查询”才应用编辑后的条件，“重置”清空并重新查询。筛选与页码在 `usePaging` 中一起更新，回到第一页并保留当前每页数量；
翻页和手动刷新保留已应用条件，每页仍为 10/20/50/100/200。变更查询会取消旧请求，不因切换窗口自动刷新。

列表请求仍为 `GET /audit/v1/events`，可选 Query 为 `occurredFrom`、`occurredTo`、`actorId`、`actionCode`、`result`、`requestId`，
并沿用 `page`、`size` 和 `{items,page,size,total}`。点击“查看”通过 `GET /audit/v1/events/{auditEventId}` 加载只读抽屉。
详情响应为 `{event,changes,attributes}`，展示原事件、字段变更前后和其他历史属性，Request ID 可复制。
空数组明确显示“未记录”，`null` 显示“已隐藏”，空字符串单独标明；未知字段/值与重复值原样保留，不查询当前名称或补造缺失历史。
历史仅有操作原因的记录仍可正常查看。关闭或切换详情取消旧请求；失败通过公共右上角通知展示，并提供手动重试。

本轮 `npm test` 76/76、TypeScript/Vite 生产构建通过。隔离 headless Chrome 用合成 API 驱动真实 App，
验证点击才查询、未知操作代码、UTC 半开区间、筛选回第一页、保留条件翻页、五档页大小和旧响应取消；
详情缺边/隐藏值/重复值、Request ID 复制、历史仅原因记录、关闭切换与列表/详情失败重试均通过。
桌面及 390px 布局已核对，宽表在窄屏内横向滚动；无未捕获页面异常或焦点切换自动请求。临时 fixture、浏览器和 Vite 已清理，未在此流程部署或提交。

## API 路由管理（2026-09-05）

`/gateway-configuration` 默认展示 Route，并提供 Target 页签。两个页签各自保留已应用的精确筛选、页码和每页数量；访问后切换页签不会卸载或重新请求。Route 支持 Route Code、Target Code、Channel、状态筛选，Target 支持 Target Code、Channel、状态筛选；详情抽屉展示 Platform 返回的当前配置，Route 详情包含授权绑定、Header、Query 与 Guard 规则。

状态按钮只对拥有 `OPERATIONS_GATEWAY_CONFIGURATION_MANAGE` 的当前授权显示。确认弹窗提交 `PATCH /gateway-admin/v1/.../status`，Body 只有目标状态、当前版本和去除两端空白的原因；相同目标、版本、目标状态和原因的手动重试复用原幂等键，前端不自动重试。`409` 要求刷新后重新操作，`403` 重载当前导航，`404` 视为过期对象并在公共通知后关闭弹窗、刷新列表。成功提示只确认 Platform 当前配置已保存，不推断 Gateway 实例已经刷新。

停用 `operations-gateway-route-status` 时弹窗明确提示控制台将无法再执行任何 Route 状态变更；停用其他 `operations-gateway-*` 管理路由时提示可能削弱或切断本页面，并要求先确认 Break-glass 恢复路径。停用 `operations-console-bff` Target 也保留控制台自锁警示。

本轮 `npm test` 88/88 与 TypeScript/Vite 生产构建通过；测试覆盖菜单注册、筛选、分页窗口完整性与安全整数边界、状态请求与同键手动重试、响应版本严格递增一位、成功边界文案、404/409/403 交互判定及管理路由自锁警示。未提交或部署。

## 财务明细账查询与控制科目（2026-10-19）

需求与设计见[技术方案](../requirements/20261019/finance-account明细账与ledger控制科目重构/technique.md) 2.2 / 2.4。新增只读页面 `OPERATIONS_FINANCE_SUBLEDGER → /finance/subledger`（父 `OPERATIONS_FINANCE`，READ 权限），由 `src/pages/FinanceSubledger.tsx` 与 `src/finance/Subledger*.tsx` 实现：主体类型 / 主体标识 / 币种必填后查询 Owner 账户（`GET /finance/owner-accounts/v1`），点击账户进入明细流水（`.../{accountId}/entries`，只接受分页），点击批次序号打开入账批次抽屉（`GET /finance/account-postings/v1/{postingId}`）。主体、筛选、账户、分页与打开的批次都在 URL，面包屑逐级返回保留筛选。

账本查询与账务基础资料同步改为控制科目口径：ledger 账户核算对象只剩 `OPERATING_ENTITY`、`FINANCE_ENTITY`、`CHANNEL_ACCOUNT`、`CASH_ACCOUNT`、`TAX_AUTHORITY`，新建账户的余额桶固定 NONE；流水与分录详情显示 `ACCOUNT_POSTING · account` 来源与 account 批次，控制科目行的「流水 …后 4 位 ↗」以 `/finance/subledger?posting=&entry=` 打开明细账批次抽屉并高亮该流水。余额查询的无账户错误 `OWNER_ACCOUNT_NOT_FOUND` 挂在 Owner ID 字段。

## 会计科目分级维护与可读账户号（2026-10-20）

需求与设计见[技术方案](../requirements/20261019/ledger-会计科目分级维护/technique.md) 2.5。账务基础资料「科目表与币种」页签的科目改为按编号排序的科目树（`SetupChartsTab.tsx`）：展开 / 折叠、按编号 / 名称 / 代码搜索、「显示已停用」开关，科目表被账簿采用时显示提示条；拥有 `OPERATIONS_FINANCE_SETUP_MANAGE` 时显示新增科目与操作菜单（编辑、新增下级、停用 / 启用），只读账号只保留变更记录。新增 / 编辑弹窗、停用 / 启用确认与变更记录抽屉在 `ChartAccountModals.tsx`，科目维护的 8 个 ledger 错误码按 technique 2.3 落到表单字段或弹窗顶部（`financeErrorTargets` 共 23 个）。账本查询新增「试算平衡」页签（`TrialBalanceTab.tsx`，URL `?tab=trial&tbBook=&tbPeriod=&tbView=&tbRun=`）：选择账簿与期间后查看实时科目余额或试算记录（列表 → 详情抽屉含汇总行与异常），一级科目合计校验借 = 贷，末级行进入账簿下钻；本页不发起试算。账簿账户、账本下钻（列表与面包屑）、明细账账户与流水 / 批次抽屉、分录详情显示账户号，两处账户列表可按账户号精确筛选；建账户弹窗的科目选择为「编号 名称」且只列启用的末级科目。

## 存储管理（2026-10-22）

需求与设计见[技术方案](../requirements/20261022/platform-support-文件多存储并存/technique.md) 4.2。新增页面 `OPERATIONS_FILE_STORAGE → /file-storage`（父「平台配置」，按钮 `OPERATIONS_FILE_STORAGE_MANAGE`），由 `src/pages/FileStorage.tsx` 与 `src/fileStorage/` 实现，三个页签：存储实例（新建 / 编辑抽屉、更换密钥弹窗、启停）、桶（按实例筛选、登记、检查并显示 `OK` / `NOT_FOUND` / `VERSIONING_DISABLED` / `ENCRYPTION_DISABLED` / `ACCESS_DENIED` / `UNAVAILABLE` 的中文说明与检查时间、启用 / 只读 / 停用）、写入规则（默认规则 `*` 显示为「默认」且不可删除）。没有维护按钮时整页只读。密钥只在密码框中输入，提交后清空，不缓存、不回显；桶启用前要求 10 分钟内检查通过、被规则引用的桶不能只读或停用，这些前端预检不替代 Platform 判断，Platform 的拒绝原因由错误通知展示。桶检查请求不带 Body。

## 文件公开桶、检查策略与文件记录（2026-10-22）

需求与合同见 [requirements/20261022/platform-support-文件公开桶](../requirements/20261022/platform-support-文件公开桶/tasks/common.md)。存储管理页：桶与写入规则区分私有 / 公开（桶登记选访问级别，公开桶必填 HTTPS 对外地址，草稿公开桶可编辑对外地址；检查结果新增「桶允许匿名访问」「对外地址不可访问」并给出静态排查指引）；写入规则按「来源系统 × 访问级别」矩阵展示，编辑时来源与级别只读、桶只列同级别启用桶、检查策略为下拉（只列有生效修订的策略），路径为 `/routes/{sourceSystem}/{accessLevel}`；新增「检查策略」页签（`src/fileStorage/ScanPolicyTab.tsx`、`scanPolicy.ts`）：列表 + 右侧修订时间线，草稿就地编辑允许类型与最大大小，新建策略、新建修订、废弃、激活、停用均填原因。新增页面 `OPERATIONS_FILE_RECORDS → /file-records`（父「平台配置」，按钮 `OPERATIONS_FILE_RECORD_DOWNLOAD`），由 `src/pages/FileRecords.tsx` 与 `src/fileRecords/` 实现：一行筛选（创建时间必填，默认近 7 天，最长 31 天）、快捷筛选（不显示计数）、分页表格、右侧快速预览；`?file=<id>` 详情含摘要、公开地址条、版本历史（可下载版本需下载按钮）、检查结果、上传记录与嵌入审计的最近下载。下载与图片预览每次申请一次授权（Platform 写下载审计），地址不保存、不展示。
