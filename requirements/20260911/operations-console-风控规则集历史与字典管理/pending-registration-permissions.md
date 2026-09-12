# 待发布权限注册冲突：已确认修正

状态：2026-09-12 用户回复“可以”确认修正及精确本机补丁；工件已同步，补丁已备份后应用。当前验收结果及阶段确认点见 [gateway-acceptance.md](./gateway-acceptance.md)。

**仅调整权限绑定的注册/开放顺序：0 新 API、0 新表或字段；独立本机库修正 7 条角色权限绑定并递增 1 条管理员角色版本。原 test 已开放的删除权限不在本机补丁目标内。**

## 已确认问题

完整初始化包含 7 条 ACTIVE 管理员权限绑定，但它们的全部 Operations Route/Resource 仍为 DISABLED。角色详情/人员角色分配要求目标角色的全部 ACTIVE 绑定落在操作者的可授集合内；可授集合只包括存在 ACTIVE Operations Route 或 Resource 的权限。这使管理员自己的角色也无法通过检查，真实 HTTP 返回 `403 OPERATIONS_PERMISSION_DENIED`。

影响为已有管理员角色详情、授予管理员角色，以及需要检查此角色权限范围的人员管理命令。注册前没有新增绑定，发布全部开放后存在有效入口，中间“绑定已生效而入口禁用”的状态才触发本缺陷。数据库实测名单见[冲突记录](./assets/gateway-acceptance/pending-permission-conflict.json)。完整回归中 4 个失败用例直接命中该问题，未把预期状态改成 403 来绕过。

现行代码依据：

- [OperationsDirectoryMapper](../../../platform-support-service/platform-support-access-control/src/main/resources/mapper/access-control/OperationsDirectoryMapper.xml)：`OperationsPermission` 按 ACTIVE Route/Resource 定义 Operations 可授范围，`rolePermissionIds` 返回全部 ACTIVE 角色绑定。
- [OperationsRoleServiceImpl](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsRoleServiceImpl.java)：角色详情执行完整授权上限检查。
- [OperationsManagementPolicyServiceImpl](../../../platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsManagementPolicyServiceImpl.java)：角色分配执行同一检查。

## 已确认修正

1. 待发布的新角色权限绑定使用现有 `REVOKED` 状态，Permission 定义、禁用 Route/Resource 仍可提前注册。`REVOKED` 是现有绑定的非生效状态，不增加状态机或字段。
2. 在对应 Route 开放的同一数据库事务中将本轮绑定改为 ACTIVE，并递增绑定/角色版本。回退与失败关闭同时恢复相应绑定状态；维护阶段关闭既有 Risk 入口时，也须核对失去全部有效入口的既有绑定（包括 Viewer 的读取绑定），按备份协调恢复。不影响仍有有效入口的其他权限，不能只处理新增绑定后就宣称整个发布流程安全。
3. 同步单一初始化 SQL、独立目录 Oracle、RCO-16 注册 DML 和阶段 6 开放/关闭/回退 SQL。逐一核对各环境实况：本机全量初始化有 7 条待发布绑定；原 test 已部署删除功能，本期只新增 6 条字典写权限，不能套用本机 7 条修正 SQL。
4. 保持既有 Java 授权上限、无权限拒绝和 Route/Resource 关闭规则。

## 已执行的本机操作

[correct-pending-permissions.sql](./db/platform-support-service/local-validation/correct-pending-permissions.sql)只接受 `mor_platform_rco16_validation`。停掉本库测试与应用后，先备份 7 条绑定和管理员角色；校验本库没有账号/Session、管理员 version=2、7 条绑定 ACTIVE/version=1 且均无有效入口，再在一个事务中改为 REVOKED/version=2、角色 version=3。包含精确行数门禁，不删除或重置版本，不开放接口。原 `mor_platform`、test 均不适用。

这份 SQL 已按原审核 Hash 执行，执行前备份目录为 `/Users/kenny/Code-MoR-backups/rco16-permission-correction-20260912T053228Z/`；[执行记录](./assets/gateway-acceptance/permission-correction-application.json)包含授权、备份 Hash 和结果。原 SQL 保留历史审核内容，不能重复应用。

完整初始化与独立 Oracle 已同步到这 7 条 `REVOKED/version=2` 及管理员 `version=3`。本期增量注册为 6 条 `REVOKED/version=1`，并对执行时管理员版本 CAS +1；两种初始化路径的版本不同是预期结果，不能拿全量初始化覆盖既有环境。

## 发布工件的修正边界

- 只读预检增加失去全部有效 Operations 入口的权限、其原 ACTIVE 绑定及受影响角色版本快照，包含 Viewer 和自定义角色。仍有其他有效入口的权限不撤销。
- SQL 生成器按精确快照生成维护、开放、页面开放、失败关闭和迁移前恢复事务。注册不会生效新授权；Route 与绑定同事务生效。恢复仅恢复快照中的原 ACTIVE 绑定，不恢复此前已撤销的权限。角色版本随有效授权变化递增。
- 旧阶段 6 预检没有绑定/角色快照，不能可靠生成这些事务。五份旧 test SQL 已加入执行前失败门禁；下一阶段需重新只读预检、重生成并复核完整发布包，不猜测当前角色版本。
- [21 项离线状态验证](./assets/gateway-acceptance/permission-release-sql-check.json)覆盖正常顺序、各阶段失败关闭、重复关闭、版本漂移、额外授权、部分注册及回退不复活旧撤销权限。使用 SQLite 内存事务模拟，不代表 MySQL 并发锁或 test 环境验收。

## 后续验证

- 禁用注册态：原管理员角色详情、人员交接/分配/版本冲突均恢复原语义；字典路由仍不可访问、按钮仍不可见。
- 开放态：51 条路由可编译，管理员拥有本期写权限，Viewer 不获得写权限。
- 关闭/回退：失效新入口与本期绑定同步，既有 IAM 管理仍可用。
- 全量 Platform 重新通过后执行覆盖率门禁，逐项比较完整目录并清理自有夹具；再提交阶段 5 结果供确认。
