# Operations 审计筛选与详情

用户已确认下一步完善审计日志。本增量复用现有审计表与 `OPERATIONS_AUDIT_READ` 权限，不改变事件写入、Hash Chain 或历史事实。

## 契约与职责

- 列表保留公共 `PagingResponseBody`（默认 10，可选 10/20/50/100/200），增加 `occurredFrom`、`occurredTo`、`actorId`、`actionCode`、`result`、`requestId`。时间为 ISO Instant，下界包含、上界不包含；其他字段精确匹配，空白不参与筛选。列表与 count 使用相同条件，按时间和事件 ID 稳定排序。查询边界先按原始 Instant 校验，随后均向上取整到微秒，防止 JDBC 舍入改变 DATETIME(6) 上的闭开区间语义；取整后相同表示空区间。
- 新增公开 `GET /api/operations/audit/v1/events/{auditEventId}`，BFF 下游路径去除 `/api/operations`。Platform 内部对应 `/operations/v1/audit-events/{auditEventId}`。详情与列表使用同一 Operations 事实范围和权限。
- Platform 返回事件与按 `attr_no` 排序的属性，不合并重复键。PUBLIC/INTERNAL 仅返回合规文本，CONFIDENTIAL/RESTRICTED 仅返回脱敏值；不输出 Vault Token、Hash 或敏感明文。未知分类拒绝读取。
- BFF 的 `AuditDetailsProcessor` 将 `before.*` / `after.*` 组装成前后值数组，提供字段显示名称和其他属性。缺失历史值保持空数组，隐藏值保持 null；不从当前角色/员工状态推测历史。
- 前端点击查询后应用条件并回到第一页，详情抽屉支持加载、重试与 Request ID 复制。保留公共错误通知和取消过期请求，不增加切屏自动刷新。

## 发布与验证

先完成 API、BFF 和前端契约及聚焦测试，再构建三个应用。现有 mor-test 只手工执行 `test-environment/sql/2026-09-04-operations-audit-details.sql`；目录从 20 到 21 条 Route，权限与资源数量不变。列表增加六个查询白名单，详情复用既有读接口 Guard。

按服务名更新 Platform、BFF 与前端，保留其他容器、数据卷、密钥和 Bootstrap 终态。通过公开 HTTP 验证筛选、分页、详情、历史对比、权限拒绝、错误响应及原登录链路。local-environment 的 Java 数据库测试与 mor-test 部署验收分别报告。

## 执行结果

- Platform 59 项纯规则、XML 与目录测试通过；19 个真实 HTTP/MySQL 场景完整编译，但因缺少 local-environment 专用凭据未运行，未改指向 test。BFF `clean verify` 270 项、前端 76 项测试和生产构建通过；Schema/注释门禁 4 项通过。
- 桌面及窄屏 headless 检查通过：点击查询才应用、未知动作代码、UTC 时间与单端条件、五档分页、筛选回第一页、旧列表响应取消、详情切换/关闭取消、503 重试、重复历史值、null/缺边区分和 Request ID 复制。临时 fixture、浏览器与 Vite 进程已清理。
- 已在 mor-test 核对保留 ID 未占用后执行一次目录增量：21 条 Route、12 个 Console Resource、12 个 ACTIVE Permission；审计列表有 8 个查询白名单。只增加详情路由与六个筛选参数，未更新角色授权或审计事实。
- 已更新 Platform、BFF、前端三个容器；其余七个容器 ID 保持，十个容器均健康。`/audit` HTML 与 Audit/入口 JS/CSS 和构建结果逐字一致，最终入口 `/assets/index-C2bST4fN.js`；容器 JAR SHA-256 与验证产物一致。
- `test-environment/scripts/audit_smoke.py` 真实 Gateway → BFF → Platform → MySQL 验收通过：逐项及组合筛选、精确 Request ID、空结果、闭开时间边界、纳秒边界映射、稳定分页与总数、完整角色前后属性、多值权限、仅原因的历史记录、公共参数/业务错误、未登录 401 和无权限 403。只创建并清理本轮合成角色/员工，管理员会话退出；未删除历史审计。
- 原 `scripts/smoke.py` 的登录、菜单、三类分页、业务/参数错误、Refresh 轮换和 Logout 回归通过。真实部署烟测不替代上面的本地数据库自动门禁。

时间精度修复有独立实证：初版真实 HTTP 查询 `[事件时刻, 事件时刻+1ns)` 返回 0；Driver 检查证实 JDBC 将纳秒调整为微秒。查询层修正后，相同边界返回 1，同时 `事件时刻+1ns` 下界排除早一个微秒刻度的记录。共享 TypeHandler 与事件写入规则没有改变。

## 前端时间范围组件补充（2026-09-04）

审计筛选后续改为公共 `TimeRangePicker`：今天、最近 3 天和最近 7 天按浏览器本地完整自然日计算，均包含今天，结束值为明日零点；只有点击查询时才解析并转 UTC。自定义模式要求同时提供开始和结束日期时间，继续沿用下界包含、上界不包含的后端契约。初始与重置保持无时间条件，选择快捷项或编辑自定义范围不自行发送请求。

前端 78 项测试和生产构建通过；隔离浏览器验证 Asia/Dubai 的快捷 UTC 边界、自定义完整性/顺序、键盘 Enter、切换清理、重置和 390px 布局。mor-test 只替换前端容器，10 个容器健康，已发布入口为 `/assets/index-XH4weB_J.js`；Platform、BFF、Gateway、目录、权限和数据库均未改变。

## 前端时间范围下拉补充（2026-09-05）

时间范围改为单个紧凑输入框。弹层桌面端左侧为今天、最近 3 天、最近 7 天和自定义快捷项，右侧为中文日期及 24 小时时分秒面板；移动端快捷项置于日历上方。快捷项只更新表单，自定义范围只有确认后写入；取消、Escape、点击外部、清空和重置均保留明确语义，任何选择都不会绕过“查询”按钮发请求。快捷范围仍在查询时按浏览器本地自然日解析，提交后冻结同一组绝对边界，保证控件显示与后续刷新一致。

审计筛选中的范围框在宽屏中与普通筛选项等宽并统一为 34px 高；1280px 及以下跨两列以容纳双端时间，480px 以下占满单列。前端 79 项测试和生产构建通过。独立浏览器回归覆盖 Asia/Dubai 到 UTC 的三组快捷边界、自定义秒值、确认/取消、外部关闭、清空/重置、月份前后导航，以及 1024、1440、2048、390×844 和 390×500 布局；短屏弹层可滚至确认区，未发生横向溢出或页面异常。mor-test 仅替换前端实例，10 个容器健康；`/audit` 与 9 个构建文件逐字一致，入口为 `/assets/index-DJtOmMm4.js`。此次不修改 BFF、Platform、Gateway、Vault、权限目录或数据库。
