# Platform 租户访问登记 SQL（方案已确认，执行另行批准）

本目录方案已经阶段 3 确认，**本机独立验证库及 test 均已获批准并完成应用，回执见[部署报告](../test-acceptance.md)**。目标为 Platform 自有库；本机候选 `127.0.0.1:13306/mor_platform_config_validation`，test 候选 `127.0.0.1:23306/mor_platform`，均 Docker desktop-linux。任何应用前都应重新明确目标和授权，不因连接可用直接应用。

| 文件 | 性质 |
|---|---|
| [ddl.sql](./ddl.sql) | 纯注释索引，0 可执行 DDL；不改既有七表字段或索引 |
| [dml.sql](./dml.sql) | 本需求唯一新增登记执行稿；105 INSERT、0 UPDATE/DELETE、无 COMMIT |
| [local-application.md](./local-application.md) | 本机具体执行方案及回执，含已批准的独立验证库安装标识例外 |
| [registration-counts.json](./registration-counts.json) | 逐表精确数量、初始状态与未来启用更新规模 |
| [activate-test.sql](./activate-test.sql) | 阶段 6 冻结执行稿，8/3/3/1 共 15 UPDATE；阶段 7 已执行 |
| [deactivate-test.sql](./deactivate-test.sql) | 阶段 6 原始恢复候选；菜单名称修正后已由下项接续，勿直接执行 |
| [repair-test-display-names.sql](./repair-test-display-names.sql) | 阶段 7 已执行的三条中文名称修正，3 UPDATE，精确 ID/旧字节/版本保护 |
| [deactivate-test-after-label-repair.sql](./deactivate-test-after-label-repair.sql) | 当前版本对应的停用恢复候选；未执行，保留全部登记及业务数据 |

登记 ID：Route 900000000000061001–061008；Permission 061101–061103；RolePermission 061201–061203；Resource 061301–061303；Header 062001–062020；Query 063001–063020；Guard 064001–064048（后六位区间均使用相同 900000000000 前缀）。完整数值以 SQL/清单为准。

只读碰撞核对见 [registration-observation.json](../assets/stage3/registration-observation.json)：两个候选库所查 ID/代码/路径/权限 resource-action 均未占用，ACTIVE Operations Target 与 PLATFORM_ADMIN 各 1 个。这仅是当时观察，不代替执行前检查、完整 Snapshot 校验或实际 SQL 验证。

## 人工执行前置与核对

1. 阶段 4 先同步正式单一初始化 DML、Web/Platform 页面能力和 API；全新库用服务初始化文件，增量环境用本文件，两者不可重复应用。正式模型结构/完整 Schema 不需要改。
2. 获取该环境具体 SQL 应用授权，核对库、Target、角色、权限、已有版本和 ID 唯一性；受影响目录记录与必要元数据按项目规则备份。不存在或多匹配的 Target/角色立即停止。
3. 操作者必须是审批清单中的真实有效 Operations 账号，手工设置 `@plt_tenant_operator`；不能填示例用户/租户 ID。该值只用于 Route 的创建/更新者，不修改用户记录。
4. 显式 `--default-character-set=utf8mb4`，一条交互 MySQL 连接执行；禁止 `--force`。按文件前置查询核对变量，再执行 START 和登记；任何错误/影响行不符立即 ROLLBACK，不继续部分提交。会话中核对每表新增数 3/3/3/8/20/20/48、全部关联和初始状态后由执行人 COMMIT。
5. 人工 SQL 不可盲目重跑；若记录已存在，核对执行回执与精确差异，不能用 INSERT IGNORE、REPLACE 或 ON DUPLICATE UPDATE 覆盖。没有自动建表、存储过程、FK、CHECK 或启动迁移。

当前 SQL 不创建 CLIENT/PARTNER Tenant、CUSTOMER 用户、Member、Credential、Scope 或业务角色。没有为现有普通角色授权；新 PLATFORM_ADMIN 关联也是 REVOKED。

## 启用与恢复

新 Route/Resource 在应用兼容前禁用。启用为 8 Route + 3 Resource + 3 RolePermission + 1 Role 共 15 行更新；阶段 6 已按 test 角色 version=19 准备精确候选，包含版本/目标检查，阶段 7 经授权执行。显示名称修正使 Resource version=3，其他新 Route/RolePermission version=2，角色 version=20；当前恢复候选以修正后脚本为准。具体目标、顺序和恢复见[发布清单](../release.md)。动态 Snapshot 通过既有机制发布/消费，无 Gateway 代码白名单。

事务提交前失败可 ROLLBACK。登记提交后尚未启用通常保留禁用记录即可；若需要删除，先核对新引用并另列精确 ID，不能整表恢复覆盖其他任务。启用后的应用回退先禁用本期 Route/Resource/授权再回退应用；业务安全暂停值不随代码回退自动恢复，恢复 Tenant 是单独的正常业务命令。

注册传输规则：GET 两项 Header；POST 六项 Header，允许/转发 Content-Type 与 Idempotency-Key；Query 只按接口清单 20 项登记。每 Route 复用既有三维度限流及并发配置共六项 Guard。requestCookie=DROP，responseCookie=NONE；GET body=0、POST body<=8192 字节；接收超时 5000ms、响应超时 10000ms。所有配置服从既有全局验证和绝对安全上限。
