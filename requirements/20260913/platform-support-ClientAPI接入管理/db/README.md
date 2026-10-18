# 手工 SQL 评审与应用入口

已确认的手工 SQL 合同。业务表归 Platform 自有数据库；本期开发回归使用其独立本机验证库，目标及精确应用范围见 [local-application.md](./local-application.md)。不跨库读写、不自动执行；阶段 3 方案确认和阶段 4 研发授权不包含数据库应用授权，实际执行状态见 [CURRENT_IMPLEMENTATION](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。

## 1. 唯一执行文件与顺序

1. 缺原 Credential/Grant 两表的环境，先用服务已有 [2026-09-08-add-api-credential-validation.sql](../../../../paytech/platform-support/docs/database/sql/changes/2026-09-08-add-api-credential-validation.sql)。它包含两表全部 21 列及原索引，不在需求目录复制。
2. 模型/正式 Schema 已按获准方案同步且旧表匹配后，应用 [ddl.sql](./ddl.sql)：新 t_api_scope 7 列、1 主键/1 唯一；旧 Grant/Route 各加 1 个索引。逐语句记录结果，DDL 自动提交。
3. Platform/BFF 已部署支持新接口和 Query、Web 包含两个页面能力后，人工应用 [dml.sql](./dml.sql)：127 条新记录 + 2 条原 Route 更新。该文件不含 COMMIT，使用同一交互连接核数并决定 COMMIT/ROLLBACK；不使用 `--force`。
4. 发布准备已按用户“部署test”授权冻结 test 的账号/角色版本和 9 Route/4 Resource/4 RolePermission 激活清单，见 [发布单](../release.md)及 [activate-test.sql](./activate-test.sql)。其他环境仍需按自身状态另行核对和授权。新权限只给确认的管理员，普通角色仍通过已有角色管理明确授权。四条初始 RolePermission 为 REVOKED。

test 只读观察目前两旧表均缺，但执行前重新核对；local、test 的连接身份分开，不根据这一观察自动决定另一个库的状态。仅缺一表或有漂移时，停止并从当前唯一 Schema 准备实际差异，不重跑全量初始化。

## 2. 执行前只读核对

以下 SQL 为执行前核对模板，不是已执行记录。发布时人工先核对 Docker context/宿主端口/数据库身份，读取 `SELECT DATABASE(),@@hostname,@@port`；其结果应与已确认连接一致。

```sql
SELECT table_name FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN
 ('t_access_tenant','t_api_credential','t_api_credential_scope','t_api_scope','t_gateway_route');
SELECT table_name,index_name,column_name,seq_in_index FROM information_schema.statistics
 WHERE table_schema=DATABASE() AND index_name IN
 ('uk_api_scope_code','idx_api_scope_credential','idx_gateway_route_scope')
 ORDER BY table_name,index_name,seq_in_index;
SELECT target_id,target_service_code,channel,status FROM t_gateway_target
 WHERE target_service_code='operations-console-bff';
SELECT role_id,role_code,scope_key,status,system_role,version FROM t_role
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM';
SELECT route_id,route_code,status,version FROM t_gateway_route
 WHERE route_code IN ('operations-gateway-route-list','operations-audit-list');
SELECT resource_id,resource_code,channel,status FROM t_console_resource
 WHERE resource_code='OPERATIONS_PLATFORM_CONFIGURATION';
SELECT permission_id,permission_code,status FROM t_permission
 WHERE permission_code IN ('GATEWAY_CONFIG_READ','API_CREDENTIAL_READ',
 'API_CREDENTIAL_REVOKE','API_SCOPE_READ','API_SCOPE_MANAGE');
```

Operator、Target、Role、Parent、原权限及两旧 Route 必须各是已审阅的准确对象。`@plt_api_operator` 必须在同一连接设为获准的真实普通 Operations 管理账号 ID，并核对其账号/会话/管理权限；SQL 不猜操作人，不填写占位账号冒充真人。新四个权限 code/资源 code/Route code 和固定 ID 均不得已存在；固定 ID 保留范围为 900000000000051001..051304、052001..052030、053001..053022、054001..054054，只核对具体清单，不占用整个数段的无关记录。

旧 Route 新增 Query 均应缺失：route-list 的 requiredScopeCode；audit-list 的 resourceType/resourceId。精确查询差异后再运行，防止重复登记。DML 锁定这两条 Route 更新，不覆盖其他配置字段；等待锁/身份不同/并发人工变更时重新审阅。合成 Tenant、凭证和 PHC 绝不混入本注册文件。

对含旧 API_SCOPE Route/Grant 的环境，先读取 **代码、对象 ID、数量** 盘点合法未登记值，再人工决定目录名称/说明；不读取 PHC 或完整凭证，不自动以 DISTINCT 值插入目录。t_api_scope 当前为空是合法起点，新建/改绑必须先通过 UI 登记。

## 3. 行数、启用与恢复

精确行数见 [registration-counts.json](./registration-counts.json)。同连接中，核对所有 INSERT 影响行数与新 code/parent/permission/target，并确认 2 个旧 Route 的版本各递增 1；再 COMMIT，不符则 ROLLBACK。新接口/菜单/授权维持关闭状态，不需要 Gateway 静态列表发布。

后续启用不重跑 dml.sql，也不删除/重新插入已有记录。按已确认当前版本更新 status/version，关联变更推进 Role 版本；届时给出实际执行文件与行数。本阶段不假定任何特定角色当前版本。

发布前仅针对本次对象准备一次必要 Schema/配置前像。DDL 若部分完成先记录实际状态；不要假设事务能回滚 CREATE/ALTER。应用恢复可保留新增表/索引及目录数据并停用本期入口；撤销是一项业务事实，不能用部署回退把 REVOKED 恢复为 ACTIVE。删除新目录/索引或回写配置前像必须重新核对后续写入，不能覆盖用户新增数据。本期没有编写自动 DROP/清库脚本。

## 4. 本机回归与 test 示例

开发自动测试仅连接仓库 local-environment；需要本机 SQL 时，阶段 4 先核对缺口、给出真实目标再申请该次执行。test 当前无 CLIENT Tenant/凭证，不表示可伪造正式客户；综合验收的少量合成记录与临时秘密由阶段 6 单独列明，包含生成、使用与清理范围，不写入 Git，不提前创建。
