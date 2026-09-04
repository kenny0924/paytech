# Operations 员工启用

用户要求增加人员启用按钮，落实统一认证设计已有的 DISABLED → ACTIVE 恢复能力，不增加新表或状态。

## 操作与边界

- 仅停用的普通 Operations 员工显示“启用”，需独立 `OPERATIONS_ACCOUNT_ENABLE` 权限、确认和原因。
- 新建员工已是ACTIVE；ACTIVE/PENDING_VERIFICATION/CLOSED不能通过启用命令修改，Bootstrap永久停用不能恢复。
- 目标固定INTERNAL Member必须ACTIVE，密码必须ACTIVE且Credential Version匹配；不恢复暂停/撤销的Member。
- 启用恢复账号原有访问能力，复用现有角色授予上限，目标全部ACTIVE关联角色（含未来授权）不能超出操作者权限。
- 不调整密码、角色、强制改密标记或密码错误锁定。账号Epoch/Version推进，已撤销Session/Refresh保持终态；人员需重新登录。
- 本轮不调整邮箱展示或Vault集成。

## 契约与实现

`POST /api/operations/iam/v1/accounts/{accountId}/enable`，BFF下游 `/iam/v1/accounts/{accountId}/enable`，
Platform内部 `/operations/v1/accounts/{accountId}/enable`。
请求 `EnableAccountRequestBody(expectedVersion, reason)` 和 `Idempotency-Key`，成功204。
公共参数/业务拒绝400，版本与幂等冲突409，未授权沿用已有403；不增加普通业务专用错误码。

Controller只适配协议，BFF通过本地Client Port转发，Platform Processor持有本地事务。
按Account ID顺序锁账号、锁固定INTERNAL Tenant；当前授权检查后识别成功幂等记录，再检查目标与版本，
CAS推进DISABLED到ACTIVE、递增Epoch、记录完整状态/版本审计和幂等结果。同键同体204，不同体409。

## 目录与验证计划

Permission533，管理员Grant577，Resource561（人员页550，sort50），Route1016；Header5040–5045、Guard6050–6055。
唯一初始化DML更新为20条Route、12个Console Resource和12个ACTIVE Permission；新权限仅默认授予平台管理员。
既有mor-test仅手工应用 `test-environment/sql/2026-09-04-operations-account-enable.sql` 一次性增量。

先验证前端交互、BFF真实HTTP契约、Platform纯逻辑/SQL目录及编译，再更新三个应用并通过合成员工验收
停用→启用→重新登录、旧Token仍失效、独立权限与超范围拒绝、版本和幂等、原密码/角色保留及审计。
本地DB自动测试与mor-test显式验收分开记录，不使用test替代local自动测试库。

## 验证记录

2026-09-04：Platform50项纯/目录测试和BFF245项 `clean verify` 通过。Platform15个真实HTTP/MySQL自动用例
完成编译；因本地自动测试DB凭据缺失未运行，未指向mor-test，也不声明完整覆盖率门禁通过。
静态测试确认权限先于Route创建，独立启用权限仅授予ADMIN，且Header/Guard完整。

已仅更新Platform、BFF并应用一次test目录增量；实库20条Operations Route、12个资源、12个ACTIVE Permission。
`test-environment/scripts/account_enablement_smoke.py` 经公开HTTP完成真实链路：停用→启用→原密码重新登录，
旧Access/Refresh在启用后仍拒绝；凭据、强制改密、安全锁字段、Member和角色关联逐项保持；账号Version/Epoch递增。
独立enable-only操作员不能执行停用/创建/分配/删除，也不能启用超自身权限的管理员。
版本冲突409、同键同体204/异体409、新键重复启用400、已删除与Bootstrap拒绝均符合契约；成功审计只有一条且
前后状态/版本/Epoch及原因一致。测试创建的3名合成员工和2个合成角色均已删除，实际管理员只用于授权操作并已退出。

前端71项测试与tsc/Vite生产构建通过。实际App/headless验证启用与停用、确认原因、pending防重复/关闭、
503保留原因和同键手动重试、409要求刷新最新版本、取消、角色/删除抽屉隔离、无权限不显示、390px布局与审计文案；
临时fixture、浏览器、开发服务器已清理。
前端镜像已发布，10个test容器均running/healthy；`/accounts`、Accounts/Audit/入口JS与dist逐字一致，
最终入口 `index-CCLDKR7s.js`。既有 `scripts/smoke.py` 的登录、导航、分页、错误响应、Refresh与退出回归通过。
