# test 环境验收记录

实际阶段状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)。本文件保存 2026-09-13 按用户确认的 release.md 执行所取得的证据。

## 发布及数据证据

- 环境：`desktop-linux / mor-test / http://localhost:28080`，数据库 `127.0.0.1:23306/mor_platform`。
- [备份记录](./assets/stage7/backup.json)：8 张配置/角色表的一致性备份，136434 字节，完成标记及表结构存在；位置 `~/Code-MoR-backups/20260913-154927-plt16-17-test/`，备份文件不进入 Git。
- [注册记录](./assets/stage7/registration.json)：同一连接检查七条 INSERT 分别影响 `4/4/4/18/76/13/108` 行后提交，总计 227 行。
- [镜像切换](./assets/stage7/deployment.json)：Gateway → Platform → BFF → Web，全部达到既有 healthy 条件，完整镜像 ID 与阶段 6 候选一致。
- [启用记录](./assets/stage7/activation.json)：同一事务检查 `18/4/4/1` 行后提交；新入口、资源、授权均生效，管理员角色 version=18。没有 DDL、回调数据迁移或业务 Fixture 写入。
- [最终容器](./assets/stage7/final-containers.json)：11 个均 healthy，四个按清单更新，其余七个容器 ID 和镜像未变。[最终数据](./assets/stage7/final-database.tsv)记录新增 18 Route、4 Resource、4 授权均 ACTIVE；全环境有效 Route 88。

## 真实公开入口

[12 项 API 检查](./assets/stage7/public-api.json)覆盖新菜单导航、资源树、权限目录、两个能力目录、A18 权限选择器、Target/Route 列表，以及原变量、规则集和决策流查询。六类入口包含 CHANNEL_CALLBACK；A18 返回 200，不存在的权限返回 HTTP 404 和正确 Problem 字段。

验收脚本最初把可选的 Problem body.status 当成必填，调整为校验真实 HTTP 状态及合同字段后通过；没有修改应用错误合同。最终 API 验收会话已正常 logout。

[Snapshot 记录](./assets/stage7/snapshot.json)确认 88 条 Route、刷新 SUCCESS、valid=1、连续失败 0、Last Valid Snapshot 回退为 0。[静态资源记录](./assets/stage7/served-web.json)确认由 Nginx 实际返回的 HTML 和 39 个静态文件与候选 dist Hash 一致。

## 页面观察

使用独立 Chromium 浏览器，从真实入口登录既有 test 管理员，1440×1000 视口实际渲染；没有使用本机开发服务器或模拟 API。结果见[浏览器记录](./assets/stage7/browser.json)。

- [菜单资源树](./assets/stage7/browser-resources.png)：42 个资源，菜单/按钮目录及新增入口可见。
- [权限目录](./assets/stage7/browser-permissions.png)：36 个权限，新增权限、筛选及操作菜单可见。
- [API 路由管理](./assets/stage7/browser-gateway.png)：88 条配置，路由/目标服务页签及新增入口可见。
- [路由详情](./assets/stage7/browser-route-detail.png)：A18 内容可读，没有保存按钮。
- [权限编辑](./assets/stage7/browser-permission-edit.png)：从详情进入编辑后有保存按钮；浏览器回退恢复只读详情，没有保存任何配置。最终页面验收会话已正常退出，未捕获 pageerror。

中途探针修正了导航名“菜单与按钮”与页面标题“菜单资源”的不同，以及 Ant Design 中文双字按钮的插空格匹配；这些是验收定位调整，不是应用修改。首次仅用于读取首页的临时浏览器已关闭，该普通 test 会话按既有 TTL 结束；后续验收均明确退出。没有保留凭据、Token、临时浏览器存储或合成业务对象。

## 待用户验收

首次部署发现一项视觉问题：权限目录中的较长编码（如 OPERATIONS_ACCOUNT_ASSIGN_ROLES）挤到相邻资源类型列。上方保留首次部署截图；用户随后明确要求修复，同阶段的后续证据见下。

请用户检查菜单树编辑、权限维护/分配以及各 Channel 的 Target/Route 配置。写入能力此前已在阶段 5 验收，本轮 test 仅做约定的轻量入口验证。后续 Git 操作按[待提交审阅范围](./commit-review.md)另行确认。

## Web 样式反馈修复

仅修改三个 Web 源文件，未修改 API、授权逻辑、数据模型或 SQL；通过现有 test 发布范围继续交付。新的固定 Web 镜像、源码/静态文件 Hash 及恢复前镜像见[制品清单](./assets/stage7/web-style-fix/manifest.json)，单服务 Compose 覆盖为[compose.yaml](./assets/stage7/web-style-fix/compose.yaml)。首次四应用制品证据保留，不重写历史 Hash。

- [1440 菜单树](./assets/stage7/web-style-fix/resources-1440.png)、[1024 菜单树](./assets/stage7/web-style-fix/resources-1024.png)：按钮数与名称同一行，13px 按钮图标，16px 高度；选中不再额外增加描述行。
- [1440 权限列表](./assets/stage7/web-style-fix/permissions-1440.png)、[1024 权限列表](./assets/stage7/web-style-fix/permissions-1024.png)：列宽明确，长文本 overflow/ellipsis 生效，编码链接未越出单元格，完整 title 与原编码一致。当前首页数据在调整后的列宽内可完整显示，无需强制截断能放下的内容。
- 权限编码点击仍进入原完整编码的详情；两种视口都没有文档级横向溢出，较窄窗口由表格内部横向滚动。最终会话已退出，无 pageerror；40 个实际公开 HTML/静态文件 Hash 匹配新 dist，其他十个容器 ID/镜像未变，11 个均 healthy。详见[验证记录](./assets/stage7/web-style-fix/verification.json)。

## 权限含义及维护入口

“可见性权限”是菜单/按钮引用的 Permission，用于决定当前用户是否能看到该资源；用户通过角色取得权限，资源本身及父级还需满足可用条件。它不是另一份权限目录，也不替代后端的接口授权检查。

菜单/按钮在“菜单与按钮 → 编辑 → 可见性权限”绑定。API 在“API 路由管理 → 路由编辑 → 安全与授权”维护，HUMAN_PERMISSION 授权方式下选择“权限”；其他授权方式沿用各自的安全合同。权限详情下“使用关系 → API 路由”用于反向查询和跳转，实际绑定在路由编辑维护。同一个权限可以被多个菜单、按钮和 API 分别引用；内置受保护配置仍遵守现有只读/受限编辑规则。
