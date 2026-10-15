# Operations 通知管理交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2. 现状差异与影响分析 | 已确认 |
| 3. 方案、接口与数据设计 | 已确认（含 Pencil 10 个画板） |
| 4. 研发实现 | 已确认；SQL 只写未执行 |
| 5. 综合验证与本地验收 | 已完成 |
| 6–7. test 发布准备与部署 | 已完成：Platform 升级并启用，见 [发布记录](./release/release.md) |
| 8. 提交 | 已授权并提交（未推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 立项 | "作为一个新需求开发吧，需要在operations上面能维护/管理通知这些" | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | "可以，先做"：确认范围、验收标准，Q-01—Q-09 全部按建议（Q-05 邮箱 Reveal 不纳入；Q-08 允许本会话在 Operations BFF/Web 新增文件、共享文件只做增量修改） | 阶段 2 现状差异与影响分析（只读分析与文档） |
| 2026-10-15 | 2 | "按照建议来"：5.1 管理 Processor 放 platform-support-web；5.2 管理与查询不依赖 notification.enabled；5.3 新增请求表 create_time 索引 | 阶段 3 方案、接口、数据与页面设计（只写文档） |
| 2026-10-15 | 3 | "可以，继续"：接受 technique.md 方案与 Pencil 画板 | 阶段 4 研发（Platform、BFF、Web 代码与测试；SQL 只写不执行；不提交） |
| 2026-10-15 | 4 | "继续"：接受阶段 4 实现 | 阶段 5 综合验证（浏览器核对、打包与制品检查、测试复核；不提交、不部署、不执行 SQL） |
| 2026-10-15 | 5 动作授权 | 选择"本机全链路（推荐）"：在本机 `mor_platform_e2e` 执行新索引 DDL、注册 DML 160 行与本机启用 SQL；调整本机 `run-service.sh` 的 Platform 启动参数；重新打包并重启本机 Platform、BFF；经运营入口做接口与浏览器验收，结束后清理测试数据 | 执行上述本机动作；不涉及 mor-test、不提交 |
| 2026-10-15 | 8 | "继续"：确认阶段 5 并授权提交本需求改动（Platform、BFF、Web、需求文档、`run-service.sh`；共享文档只含本需求部分） | 本地提交；不推送、不部署；`design.pen` 未保存，未纳入 |
| 2026-10-15 | 6 | "可以，先出清单"：在测试环境看不到菜单，要求先出 test 发布清单 | 只读核对 test 并交付发布清单；不执行任何 test 变更 |
| 2026-10-15 | 6→7 | "按建议来，执行吧"：D1 接受 test 认证邮件暂不可达（先上线）；D2 本次先发 BFF/Web；D3 在 test 执行认证邮件初始化块；授权按发布清单执行 test 备份、构建、SQL、Compose 修改与镜像替换、验收与清理 | 执行阶段 7；不推送 Git 与镜像、不碰生产 |
| 2026-10-15 | 7 | 执行：复核发现 BFF/Web 已由 Vault 值格式需求以 `994d5b0`（含本需求）发布，改为只升级 Platform；备份、构建、DDL、注册 160 行、认证邮件初始化块、Compose 修改、Platform 替换（中断约 24 秒）、启用 12/6/2/1；验收接口 22/22、浏览器 16/16、回归 28 页、日志无泄露；验收数据已清理 | 待确认；Compose 与发布记录待提交（需授权） |
| 2026-10-15 | 7→8 | 确认阶段 7；授权本地提交阶段 7 的 Compose 修改、发布记录、截图与状态文档 | 本地提交；不推送 |

## 当前阶段与下一确认点

阶段 5 已完成，阶段 8 已提交到本地 main（未推送）。Pencil `design.pen` 在应用中尚未保存，本次只提交导出的 PNG / PDF；保存后需单独提交。

## 验收映射（阶段 4）

| AC | 实现 / 证据 | 状态 |
|---|---|---|
| AC-01 | 权限 `NOTIFICATION_READ` / `NOTIFICATION_MANAGE`、菜单与按钮注册 DML；Platform 测试中只读人员可查、不能写（403），运营只读人员看不到（403）；BFF 授权拒绝时不访问 Owner（12 个端点） | 通过（DML 未执行） |
| AC-02 | 配置每次发送读库、无缓存；`NotificationTemplateManagementTest.test_saveContentTakesEffect` 修改标题后下一封普通通知即用新标题 | 通过 |
| AC-03 | `test_contentRejected`：缺 `${link}`、标题换行、占位符非法均 422 定位字段；语言非法 400、模板不存在 404；`test_preview` 预览列出缺失参数；`NotificationInputTest` 覆盖全部字段规则 | 通过 |
| AC-04 | 模板、内容、路由、账号各写入用例的版本过期均 409，数据不变 | 通过 |
| AC-05 | `NotificationRouteManagementTest`：主备相同、账号停用 / 不存在 / 非 EMAIL 拒绝；主账号停用后不能启用路由；页面停用认证模板与被引用账号时弹窗说明影响 | 通过（页面待阶段 5 核对） |
| AC-06 | `NotificationRecordQueryTest`：按时间、来源、事件键、模板、状态筛选与分页；详情含投递与尝试；响应不含 Token、参数值、供应商请求号与消息标识 | 通过 |
| AC-07 | 6 类写操作的审计事件与字段差异；内容审计只含摘要（`assertNoSecret`） | 通过 |
| AC-08 | Platform 617/617、BFF 1331/1331、Web 1019/1019；Delivery 指令 96.78%、分支 90.83%（改动前 96.39%/90.49%） | 通过 |
| AC-09 | DDL（Schema、changes、测试 ddl.sql、PDManer）与注册 DML 160 行写入文件 | 通过（未执行） |
| AC-10—12 | 3 个页面已实现并通过 `tsc` 与生产构建 | 待阶段 5 浏览器核对 |

## 测试证据（阶段 4）

- Platform：JDK 25 `./mvnw -o clean verify` 测试 **617/617**（改动前 589）；全服务 90% 聚合门禁仍为既有失败（指令 0.54、分支 0.37，未降低门槛）。新增 `NotificationTemplateManagementTest`（6）、`NotificationRouteManagementTest`（4）、`NotificationProviderManagementTest`（2）、`NotificationRecordQueryTest`（3），`NotificationInputTest` 新增 13 例。日志 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/127a8113-989d-4dae-925f-99aec229b7de/scratchpad/platform-notif-admin-verify2.log`（临时目录）。
- BFF：`./mvnw -o clean verify` **1331/1331**，覆盖率门禁通过；新增 `NotificationManagementBrowserAutoTest`（31 例：12 个端点转发与授权拒绝、Owner 错误透传、422 字段投影、无效上游响应、非法参数）。
- Web：`npm test` **1019/1019**（新增 `notification/notification.test.ts`）；`npm run build` 通过。
- 未做：执行 SQL、浏览器联调与页面核对、打包与制品检查（阶段 5）。

## 阶段 5 综合验证（2026-10-15）

**本机执行的动作（已授权）**

| 动作 | 结果 |
|---|---|
| `mor_platform_e2e` 执行索引 DDL（`changes/2026-10-15-notification-request-create-time-index.sql`） | 索引存在 |
| 注册 DML（初始化文件末尾独立块）：先试跑回滚核对 160 行，再正式执行 | 160 行；中文名正常（客户端 utf8mb4） |
| [activation-local.sql](./db/platform-support-service/activation-local.sql) | 12 / 6 / 2 / 1 行 |
| [acceptance-fixture-local.sql](./db/platform-support-service/acceptance-fixture-local.sql)（2 个 E2E 账号、1 条合成发送记录） | 已插入，验收后由 [acceptance-cleanup-local.sql](./db/platform-support-service/acceptance-cleanup-local.sql) 清理，通知七表回到空表 |
| 本机启动脚本 `requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/run-service.sh`：删除已移除的 SMTP 参数，加 `--notification.enabled=true` | 新 Platform 包正常启动（认证邮件经 Delivery 后，开启 Client 认证须开启通知） |
| 重新打包并重启本机 Platform 与 Operations BFF | 均启动成功 |

**验收结果**

- 接口验收 [local_acceptance.py](./verification/local_acceptance.py)：经 `https://127.0.0.1:18444` 运营入口（Nginx → Gateway → BFF → Platform）**22/22** 通过，覆盖 AC-01～AC-07（导航含菜单与按钮、新建 / 内容 422 / 预览 / 启用、409、路由与账号规则、发送记录筛选与不泄露、审计）。
- 浏览器验收 [local_browser.py](./verification/local_browser.py)：**16/16** 通过，覆盖 AC-10～AC-12；截图在 [assets/stage5](./assets/stage5/)，已对照 Pencil 画板 01、02、03、05、06、07、09、10。进入通知管理后控制台只有冲突用例预期的 409。
- 发现并修正：409 错误通知遮挡冲突横幅（改为只显示横幅）；记录页时间范围被截断（加宽）；antd 弃用写法 2 处；注册 ID 段与 Vault 管理注册 SQL 冲突（`81xxx`～`84xxx` 改为 `86xxx`～`89xxx`，见 technique.md 第 7 节）。
- 制品检查：Platform exec jar 含 `notificationmanagement` 与 `processor/notification` 共 28 个类、打包配置含 `/notification-admin` 与 `notification.management`；BFF exec jar 含 21 个通知管理类、`/notification-admin` 只记元数据；Web 产物含 3 个通知页面分包。
- 测试复核：Platform 修正后 `clean verify` **617/617**（全服务 90% 聚合门禁仍为既有失败 0.54/0.37），完成后重启本机 Platform 以加载新包；BFF 1331/1331（阶段 4，之后未改 BFF）；Web 修正后 `npm test` 1025/1025（含其他会话新增的测试）、`npm run build` 通过。
- 未做：mor-test 环境、真实邮件发送（本机未配置 SES）。

