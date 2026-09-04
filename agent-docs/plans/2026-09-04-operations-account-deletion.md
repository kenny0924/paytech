# Operations 员工删除

用户要求在人员管理中增加删除员工。沿用统一人工认证设计中已有的 Account `CLOSED` 终态，不引入软删除字段或新表。

## 行为与权限

- 人员列表为非 CLOSED 的 OPERATIONS HUMAN 账号；分页数据与总数使用同一条件。
- 新增独立 `OPERATIONS_ACCOUNT_DELETE` 权限和按钮，默认仅授予系统管理员，其他自定义角色需明确配置。
- 当前登录员工不能删除自己；Bootstrap 和 CUSTOMER 不属于该命令范围。ACTIVE、DISABLED 员工可以删除。
- 最后一个当前有效的平台管理员不可删除；已停用账号或暂停成员本身不计入可用管理员数，删除它们不会误触发此保护。
- 确认弹窗显示脱敏账号和 ID，要求填写原因。删除成功后人员从列表移除，所属角色的关联员工数随下一次查询更新。
- 删除不可恢复；保留原账号、成员、关联、审计和 Email 唯一性历史，不复用该邮箱创建新身份，不调用 Vault 销毁数据。

## 契约与事务

公共接口：`DELETE /api/operations/iam/v1/accounts/{accountId}`，BFF 下游路径 `/iam/v1/accounts/{accountId}`。
请求 `DeleteAccountRequestBody(expectedVersion, expectedMemberVersion, reason)`，同时要求 `Idempotency-Key`，成功204。
参数错误、普通业务拒绝和版本/幂等冲突分别复用公共400/400/409；权限拒绝沿用既有403，无新增专用错误码。

Platform 在已有 Processor 本地事务内按 Account ID 排序锁定账号，再锁固定 INTERNAL Tenant，与人员分配及角色删除保持一致。
当前权限校验后先检查成功幂等记录，再检查目标和两个版本。成功重放不依赖目标账号仍可操作。
同事务关闭账号并推进版本/auth_epoch，撤销成员、所有 ACTIVE 角色关联（包含未来关联）、密码和会话/未消费 Refresh，
追加状态、账号版本、成员版本和角色前后属性审计，再完成幂等记录。历史已消费 Token 不改写。

`requireHuman` 拒绝 CLOSED，防止删除后再次分配角色或停用；Bootstrap 的历史 Human 计数仍包含 CLOSED，不能重新初始化。
BFF 直接通过本地 Client Port 委托单个 Platform 命令，业务事务不转移到 BFF。
前端人员和角色删除共用确认交互，分别构建业务请求；等待时防重复/关闭，失败保留原因，同体手动重试同键，409需刷新。
末页删除最后一项返回上一页，仍保持默认10条及五档公共分页。

## 目录与发布

Permission532、管理员Grant576、Resource560（父人员页550，排序40）、Route1015；Header5030–5035、Guard6040–6045。
新库唯一初始化DML为19条Route、11个Console Resource、11个ACTIVE Permission。
既有test使用 `test-environment/sql/2026-09-04-operations-account-delete.sql` 一次性手工增量，不重放初始化或Bootstrap。
仅更新Platform、BFF和前端三个应用，保留其他容器、网络、卷及密钥。

## 验证与部署结果

2026-09-04：前端67项测试、生产构建及实际App/headless交互通过，覆盖确认取消、重复点击、失败重试、版本冲突、
末页删除和共用角色删除弹窗回归；BFF232项 `clean verify` 通过；Platform49项纯测试通过。
13个Platform真实HTTP/MySQL自动用例完成编译，因本地测试DB凭据缺失未运行，未指向mor-test，也未声明完整覆盖率门禁通过。
最后管理员统计已限定固定INTERNAL Tenant，并校验成员与授权的Tenant及Role Scope一致。

仅重建Platform、BFF、前端三个镜像并按服务名替换容器；确认保留ID未占用后显式应用一次目录增量。
实库当前19条Operations Route、11个Console Resource、11个ACTIVE Permission，Gateway动态读取新目录。
未重放初始化、Bootstrap或修改其他服务；10个test容器均running/healthy。
`/accounts`、`/roles`及Accounts/Roles/入口JS与本次dist逐字一致，入口为 `index-BOsKYZp_.js`。

`test-environment/scripts/account_deletion_smoke.py` 已通过真实公开HTTP/MySQL链路：独立删除权限与兄弟动作拒绝、
禁止删除本人、账号/成员双版本冲突、ACTIVE与DISABLED删除、同键重放及异体冲突、旧密码/Access/Refresh拒绝、
成员/角色/会话撤销、列表与分页总数排除CLOSED、角色人数归零、终态不可重新授权和邮箱历史唯一性。
同键重放只产生一条成功审计，核验账号/成员状态与版本、角色数量和原角色ID属性。仅删除本次合成的3名员工和2个角色，
管理员会话已注销，既有业务员工未删除。现有 `scripts/smoke.py` 登录、导航、分页、错误响应、Refresh与退出回归也通过。
