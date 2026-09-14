# test 部署与入口验收记录

2026-09-14，用户对阶段 6 发布清单回复“可以”后执行。当前部署状态统一见[实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)，本文保存本次执行证据。

**本次部署 Platform、BFF、Web 三应用；8 项业务 API、3 个页面；0 DDL、105 INSERT、15 启用 UPDATE，另 3 条新菜单名称修正 UPDATE。**

## 部署结果

- 入口：[租户访问](http://localhost:28080/tenant-access)。重新登录后可见菜单。
- `desktop-linux / mor-test`，数据库 `127.0.0.1:23306/mor_platform`。三个镜像与[阶段 6 清单](./assets/stage6/release-manifest.json)固定 ID 一致，全部 healthy；其他八个容器身份/镜像未变。见[运行容器](./assets/stage7/containers.json)。
- 一次备份八张受影响配置表，174513 字节、权限0600；路径及摘要见[备份回执](./assets/stage7/backup.json)。没有备份整库、凭据或会话。
- 单连接92552分两次事务核对并提交登记/启用；管理员角色19→20，新Route/RolePermission为ACTIVE/version2。见[SQL回执](./assets/stage7/sql-application.json)及[最终数据库核对](./assets/stage7/database-postcheck.tsv)。
- 浏览器发现三条新Resource中文乱码，原因是MySQL交互客户端默认latin1。仅修正这三条新显示名称，逐条精确ID/原字节/version保护，连接92576核对后提交，Resource version2→3。见[修正SQL](./db/repair-test-display-names.sql)及[回执](./assets/stage7/label-repair.json)。客户端后续显式使用`--default-character-set=utf8mb4`。

## 检查证据

| 范围 | 实际结果 |
|---|---|
| Gateway | Snapshot有效、刷新成功、105条有效Route，本期新增8条；[证据](./assets/stage7/snapshot.json) |
| 公开HTTP | 登录、新导航资源、全部/CLIENT/PARTNER空页、未知及INTERNAL租户404、原人员/角色/API Route/审计/风控规则查询、退出，13项通过；[记录](./assets/stage7/public-checks.json) |
| 中文名称 | 修正后三项导航返回文本与批准值逐字一致；[独立复核](./assets/stage7/label-repair.json) |
| Web制品 | 公开HTML及46个静态文件SHA与阶段6固定构建一致 |
| 浏览器 | 8项通过：登录、中文菜单、空页、合作伙伴筛选、刷新保持条件、角色页、返回保持条件、退出；[记录](./assets/stage7/browser-check.json)、[截图](./assets/stage7/tenant-access-test.png) |
| 业务数据 | CLIENT/PARTNER Tenant与Member仍为0，未创建test示例、未执行租户安全状态写入 |

没有在test重复暂停/恢复或成员权限明细的完整业务验收；这些能力使用[阶段5真实链路证据](./acceptance.md)。也没有据此声称CUSTOMER登录或外部Client业务执行已交付。初次快照取证工具使用历史Risk封闭清单而拒绝已有路由，已改为读取当前合同核对；Gateway本身没有故障，也没有修改其代码。

## 恢复与交付边界

如需恢复，使用[修正后停用脚本](./db/deactivate-test-after-label-repair.sql)，前提为Route/RolePermission version2、Resource version3、管理员角色version20且没有后续变更，核对8/3/3/1后显式COMMIT；再按Web→BFF→Platform恢复[旧镜像](./assets/stage6/compose-previous.yaml)。版本继续递增，保留登记与业务数据。阶段6原`deactivate-test.sql`仅留历史，不能用于修正后的Resource版本。本次没有执行任何恢复。

临时浏览器已退出并关闭，正常鉴权/审计记录保留。Git提交、推送、远程制品发布及test业务示例均未执行。等待用户验收后再接续已批准流程。

## 用户验收确认

部署后已按用户单独要求创建[一个带成员权限的test示例](./db/test-demo/README.md)。用户随后表示“我做完了，提交吧。”，确认本期验收并授权Git提交；本文件上方空页/待验收描述保留部署当时的观察。最终确认及提交见[阶段8交付](./delivery.md)和[验证记录](./assets/stage8/verification.json)。
