# 规则集列表优化：test 发布与验收报告

2026-09-11，阶段 7。用户明确回复“发布”后按 [发布包](./release.md)执行；后续“继续”用于完成剩余核验与交付。当前状态由 [CURRENT_IMPLEMENTATION 第 16 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)维护。

## 发布结果

本机 `desktop-linux / mor-test` 已运行新 Risk、Operations BFF、Web，公开入口为 [规则配置](http://localhost:28080/risk/rule-configuration)。三个容器均 healthy；其余八个容器的完整 ID 与 image ID 保持原样。没有推送镜像、发布 Nexus、提交代码或操作生产。

| 应用 | 已部署 image ID 前缀 | 变化 |
|---|---|---|
| risk-service | `46aff45920d3` | 规则集根、单行列表、整体删除、历史保护及 Redis 资源锁；内嵌 Foundation 监控修复 |
| operations-console-bff | `59be81991657` | 原 v1 列表合同及独立删除权限/单次转发 |
| operations-console-web | `83a5141710c3` | 精简列表、编码校验、操作菜单、编辑及 URL 历史 |

完整镜像、容器、步骤与 SQL SHA-256 见 [部署记录](./assets/stage7-deployment.json)。网页 HTML 与公开 JS/CSS 入口资源均与该 Web 制品逐字节一致，见 [最终核验](./assets/stage7-final-verification.json)。

## 数据库、目录和备份

备份目录：`/Users/kenny/Code-MoR-backups/risk-rule-set-list-test-20260911T131033Z/`，目录 0700、备份文件 0600。包含完整 `mor_risk-before.sql`、`mor_platform-before.sql`、原 71 张 Risk 表有序结构/数据快照及发布前元数据；文件大小与 SHA-256 见部署记录。备份和凭据不进入需求目录或 Git。

已按发布包顺序执行维护、Risk DDL/test manifest DML、Platform 注册、路由开放、页面开放共六步 SQL。Risk 新增 `t_rule_set` 并回填 21 个管理根；迁移前后原 71 表的结构与数据有序 dump SHA-256 完全相同。原 23 个版本及 Hash、时间、状态不变，21 个根均指向最高配置版本，初始管理版本为 1。

Platform 注册删除权限与管理员授权，管理员 Role 8→9；最终 Gateway 接受精确 37 条 Route，刷新结果 `SUCCESS/NONE`，未使用 Last Valid fallback；Operations ACTIVE Resource 为 22。Risk 通过 Nacos 暴露唯一健康实例，JVM 内存和 ResourceLock 指标均存在。

临时发布锁已正常释放，探测用临时 Java class 已从 Risk 容器移除。迁移后已产生合法删除事实，不能用老 Risk 或整库旧备份直接覆盖；恢复限制继续遵循发布包第 6 节。

## 真实入口验收

使用真实登录与 Nginx → Gateway → BFF → Risk/Platform，没有 Simulator、HTTP 拦截或伪造可信身份 Header。

- 23 项 HTTP 核对完成：原 21 集一集一行、多版本取当前配置、精确编码/状态查询、旧详情 Hash、非法编码 400、匿名 401、实际只读删除 403，以及第二版本保存、陈旧删除 409、刷新后的整集删除、同键回执重放、历史成功命令重放、删除后编码不可重建、Session 刷新和退出。记录 34 次经核对的请求，登录/改密工具自身调用不计入该数字，见 [HTTP 证据](./assets/stage7-http.json)。
- 浏览器核对标题小字、摘要/版本列和 Candidate Tab 移除；合法编码去首尾空格、非法编码就地拦截且不发送请求；详情/直接编辑/红色删除菜单；Back/Forward 与过滤保持；取消离开保留草稿；真实保存后的内容；删除确认框包含名称和编码、取消无删除请求；整集删除后的列表；Candidate 旧 URL 回到列表；390px 页面无整页横向溢出。记录 14 个检查点（含两次登录）和 41 次请求，JavaScript error 为 0，见 [浏览器证据](./assets/stage7-browser.json)。
- 两组合成规则各两个版本，均已用本次 DELETE API 整集删除；根、不可变版本及命令历史按业务规则保留。临时只读账号仅绑定已有 `PLATFORM_READONLY` 角色，完成初次改密、读取和实际 403 后退出并通过 API 删除；最终账号为 CLOSED。未新建角色、修改只读角色权限或物理清理业务历史。
- 最终只读核对：原 21 个规则集及原 23 个版本完全不变，原 9 个 Candidate 的状态计数不变，无 ACTIVE Release，Evaluation/Action Effect 仍为 0。现存管理根为 21 个 CONFIGURABLE 加 2 个 DELETED，版本共 27 个。

[桌面页面](./assets/stage7-list-test.png)与[窄屏页面](./assets/stage7-list-mobile-test.png)只展示已删除的合成规则查询结果，不含账号资料、凭据或真实规则内容。删除弹窗截图捕获了过渡画面，未作为交付证据；名称/编码和红色动作由实际 DOM 断言核对。

## 验证边界与恢复观察

浏览器快速连续 Back/Forward 触发现有列表读取 Guard，保存 POST 已返回 200，随后三次列表 GET 返回 429。等待原有限流窗口恢复后，以真实新会话读取已保存的第二版，继续完成回退与删除；没有重复保存、增加第三版或修改限流策略。因此本次是保留失败观察并续跑完成验收，不能称为单次浏览器脚本连续全绿。阶段 5 已有保存后导航及回退的独立真实业务接口验证。

验收脚本修正过按钮可访问名称、Navigation 按钮字段、退出参数、HTTP 状态读取及最终账号表名/关闭状态等断言；没有因此修改或重建业务制品。细节见 [验证说明](./assets/stage7-verification.json)。

旧通用 smoke/rollout 分类固定为 36 Route/21 Resource，未将其作为新 37/22 目录的通过门禁，也未修改其合同。已按发布包核对完整 Snapshot 和真实公开接口；通用工具适配作为后续维护项。当前 test 无 ACTIVE Release，使用中保护沿用阶段 4/5 的真实 Risk/MySQL/Redis 回归，本次没有伪造激活或进入运行期规则决策。

## 下一阶段

[待提交清单](./commit-plan.md)列出六个仓库的本轮文件范围与排除项。该发布阶段结束时未暂存、提交或推送；用户后续明确授权本地提交，执行与保留项见 [阶段 8 归档](./completion.md)。
