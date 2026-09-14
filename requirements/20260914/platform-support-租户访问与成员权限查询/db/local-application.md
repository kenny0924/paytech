# 本机登记应用准备

目标仅 `desktop-linux / mor-local-mysql-1 / 127.0.0.1:13306 / mor_platform_config_validation`。

- 执行 [dml.sql](./dml.sql)，105 行新增登记；0 DDL、0 旧行更新/删除。
- 新路由与资源为 DISABLED，新管理员权限关联为 REVOKED；不启用、创建业务样例或修改 test。
- 该独立验证库当前没有 Operations 账号。本次拟沿用其正式初始化的安装执行标识 `1` 作为 Route 创建/更新元数据，不假设存在账号 1，不创建账号或授予访问权。此本机准备明确申请批准；实际 test 发布仍须使用有效 Operations 操作者。
- 这是对 db/README 第 3 项“真实操作者”的独立本机初始化例外，不适用于其他环境。操作前再次核对目标、七表结构、ID/代码/路径及 Target/管理员唯一性；备份结构和受影响目录到 `~/Code-MoR-backups/20260914-tenant-access-local/`。
- 在一个 MySQL 会话中设置 `@plt_tenant_operator=1`，执行文件，核对 7 表增量分别 3/3/3/8/20/20/48、关联与禁用状态，再提交；任意错误回滚，禁止强制继续或覆盖已有登记。
- 回归用例只建立和清理自身的合成 Tenant、账号、成员和授权，使用目录中的三项权限；不会把初始化 SQL 放入测试。

2026-09-14 用户回复“继续”后按上述目标完成备份及单连接事务应用。7 表新增数为 3/3/3/8/20/20/48，COMMIT 成功；未启用。见 [执行回执](../assets/stage4/local-application.json) 及 [备份摘要](../assets/stage4/local-backup.json)。
