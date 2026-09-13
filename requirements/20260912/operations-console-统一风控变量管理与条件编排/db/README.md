# SQL 评审与后续应用

本目录保存已确认的 SQL 设计与应用边界。各环境实际应用状态由[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)维护，具体授权见[交付记录](../delivery.md)，不得扩展至其他环境或重复应用。字段以[设计清单](../contracts/design-manifest.json)和已同步的 PDManer/Schema 为准。发布修正及已执行环境的单行补丁见[发布材料](../deployment.md)。

| 数据库 Owner | 工件 | 精确范围 |
|---|---|---|
| Risk / mor_risk | [ddl.sql](./risk-compliance-service/ddl.sql) | CREATE 2表/41列；ALTER 4旧表，新增5列、4列注释；新增11索引含主键唯一 |
| Risk / mor_risk | [dml.sql](./risk-compliance-service/dml.sql) | INSERT 17变量+9集合成员=26行；无旧数据UPDATE，无规则或流程绑定 |
| Platform / mor_platform | [dml.sql](./platform-support-service/dml.sql) | INSERT 113行；UPDATE准确PLATFORM_ADMIN角色的1个版本；不开放新入口 |
| BFF / Web / Gateway / Foundation | 无业务SQL | 没有新增这些应用的业务表 |

Risk新表主键2、唯一索引2、普通索引7（含旧表新增3），共11。新增字段总数46。Risk DDL的source_type允许VARIABLE为VARCHAR注释变化，不更改原值。condition_json注释保留8KiB/受控格式/整树解析边界，格式2增加变量引用。没有DROP、TRUNCATE、CHECK、FOREIGN KEY、启动自动seed或全库重建。

## Risk 应用次序与核对

1. 核对目标环境/库、两新表不存在、旧表已有列/注释/索引及未提交模型改动；核对本稿新增ID和编码无冲突。任一冲突停下人工合并，不INSERT IGNORE、不UPSERT。
2. 模型确认/生成一致后按既定人工SQL流程应用DDL。MySQL DDL会隐式提交，不能说START TRANSACTION即可整体回滚DDL。记录每条完成位置；中途失败只按实际已应用项续做，不能盲目重跑。
3. 在单一事务应用dml.sql。父变量、依赖和成员按完整集合校验后提交；无物理外键，初始化允许同批前向引用；应用实现的管理写入仍校验准确引用。代码/来源映射唯一性与系统预留定义必须静态核对。
4. source_type旧数据不改，rule/content/point/hash表不做回填；用户要求的test示例迁移走管理API产生新版本，见[test迁移清单](../test-demo-migration.md)。

主库本地与test分开：开发为127.0.0.1:13306；test为23306，界面localhost:28080、Docker desktop-linux / mor-test。当前仅登记目标，实际执行前重核对。已有别的需求的SQL授权不作为本期SQL授权。

## Platform 注册明细

| 表 | INSERT行数 |
|---|---:|
| t_permission | 2 |
| t_role_permission | 2 |
| t_console_resource | 3 |
| t_gateway_route | 9 |
| t_gateway_route_header | 24 |
| t_gateway_route_query_param | 19 |
| t_gateway_guard_rule | 54 |
| 合计 | 113 |

另更新1个PLATFORM_ADMIN角色version，保留现有绑定和其他角色。复用RISK_DECISION_CONFIG_READ、OPERATIONS_RISK_MANAGEMENT父菜单，以及target_service_code=operations-console-bff/channel=OPERATIONS目标；按业务编码在mor_platform内定位，不写死本机/test角色ID。新Route/Resource为DISABLED，两个角色写权限为REVOKED；Guard参数复用当前已批准Operations配置模式。9个新业务路由，不加Gateway静态白名单。

执行前要求四个前置对象各唯一、状态符合SQL条件，准确Target和BFF listener匹配；新增主键/route_code/Method+Path/resource_code/permission_code及(resource_type,action_code)均无冲突。脚本使用标量子查询，重复前置匹配报错，缺关键非空引用报错；父菜单允许NULL，因此它的“存在且唯一”必须在执行前人工核对，不能只依赖NOT NULL。客户端不得--force继续错误语句；遇错ROLLBACK并停止。

后续启用在阶段6给出环境精确manifest：9Route+3Resource设ACTIVE、2角色绑定设ACTIVE并再次更新该角色version；既有READ授权不自动扩给新角色。当前不提供会被误当作已授权的自动激活脚本。Gateway按数据库Snapshot更新路由，无需Gateway新制品；BFF及Risk自己仍执行授权。

## 失败恢复边界

设计阶段不做备份或真实验证。后续按用户确认的具体部署边界做必要备份/恢复材料，不无目的扩大检查。未写入新变量引用前，可保持新入口禁用并退回应用版本；已有新格式数据后必须保留能读取新格式的Risk/BFF，不能仅回滚到不认识VARIABLE的旧程序。优先关闭新写入口并前滚修复，保留新旧历史。两张新增表不得作为普通应用回滚动作直接删除。
