# Platform 决策流注册清单

状态：已确认的注册材料；数据库应用及开放状态见 [当前台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。唯一明细为 [registration.json](./registration.json)，对应 [dml.sql](./dml.sql)，[ddl.sql](./ddl.sql) 无 DDL。

| 现有表 | 新增行 | 用途 |
|---|---:|---|
| t_gateway_route | 10 | 与 api.md N01–N10 一一对应 |
| t_gateway_route_header | 28 | Authorization CORS、Origin 转发、两个 POST 的 Content-Type、保存/删除的 Idempotency-Key |
| t_gateway_route_query_param | 13 | 目录 1、列表 5、历史 3、删除 1、引用详情 3 |
| t_gateway_guard_rule | 60 | 每 Route 6 条，复用当前 Operations 的三维速率/并发参数 |
| t_permission | 2 | FLOW_SAVE、FLOW_DELETE；读复用 RISK_DECISION_CONFIG_READ |
| t_role_permission | 2 | 原 Operations 管理角色的两项待开放授权，以 REVOKED 初始化 |
| t_console_resource | 3 | 决策流菜单、保存能力按钮、红色删除能力按钮 |
| 合计 | 118 | 另更新既有 t_role 一行的版本和时间 |

Route 的 permission_id 就是权限绑定；现行 Schema 没有独立 Permission API Route 表，不能再多算一批虚构绑定行。保存能力资源用于创建/编辑/校验/保存，详情/历史复用 READ 菜单权限。

复用 Target `900000000000000001`、读权限 `900000000000000536`、风控父菜单 `900000000000000565`、管理角色 `900000000000000510`。新增 ID：Route 021001–021010、Permission 021101–021102、RolePermission 021201–021202、Resource 021301–021303、Header 022001–022028、Query 023001–023013、Guard 024001–024060，均在前缀 `900000000000` 后补足对应后六位，完整数字以 JSON/SQL 为准。

生成时已与当前仓库完整初始化 DML 检查无 ID 冲突；没有读取本机或 test 数据库，不能据此声称目标环境无冲突。将来执行前核对上述前置对象身份、状态和归属，按各表 PK、Route code/path、Permission code、Resource code 复查无冲突。发现已有任何一项先逐字段比对，不使用 UPSERT/INSERT IGNORE 覆盖。

本文件只注册：Route/Resource 为 DISABLED，写角色绑定为 REVOKED；已有 READ 角色不会提前获得新可见入口。所有 DML 在一个本地事务插入，角色 version 只递增一次；SQL 工具出现错误应回滚并停止，不在半失败状态继续 COMMIT。首次成功执行后不重复执行整份文件。

同一批注册数据已合入 Platform 唯一 [platform-support-initial-data.sql](../../../../../paytech/platform-support/docs/database/sql/platform-support-initial-data.sql)；check-design.mjs 核对逐值一致及每行唯一出现。本目录 SQL 是本次增量执行材料，不是第二份初始化权威。阶段 6 再按实际部署环境准备精确开放清单：10 Route、3 Resource、2 RolePermission 的状态切换与角色版本更新；当前不执行或假定 test 中版本值。Snapshot 验证/收敛沿用 Platform 与 Gateway 能力，Gateway 业务代码和 Target 均无新增。
