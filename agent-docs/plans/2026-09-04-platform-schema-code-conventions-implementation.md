# Platform 代码与数据库准则同步

## 已确认边界

用户明确要求：生产 MyBatis SQL 使用 XML；数据库不建 CHECK/FOREIGN KEY，但保留主键、唯一索引、NOT NULL；字段、默认值及完整注释与 PDManer 一致；Java 枚举常量之间空一行。实际数据库只操作独立 `mor-test`，不操作 `local-environment`。

根仓隔离分支基于 `ac7dd87`，Platform 隔离分支已先同步主线 `e624c78` 的格式调整，再语义保留本轮 XML 改造。没有覆盖其他任务的 Vault 或部署配置文件。主线集成采用根仓、Platform 两个独立仓库分别提交并快进合并；测试镜像先使用隔离工作区已验证的同一份产物，合并不重复执行 SQL 或重建服务。

## 实施结果

- 三个 Operations Mapper 共 68 条生产语句移入 XML；保留绑定类型、锁读缓存行为及 Mapper 方法契约。测试辅助 SQL 仍可使用注解/JdbcTemplate。
- 权限、导航和可授予权限查询验证完整 Member/Tenant、Role Scope Tuple；授权写入在既有锁/事务内再校验成员、账号与角色归属。会话查询显式限定 Operations Channel 和 PASSWORD，不依赖外键或 CHECK 过滤错误身份。
- 24 个枚举文件仅调整常量间空行，代码值、声明顺序和方法保持不变。
- 22 张已实现表、273 个字段与完整模型对应；补齐表、字段、索引注释，保留 22 个主键、37 个唯一索引、26 个普通索引、全部 NOT NULL，以及既有路径/Header 字符集规则。
- 63 表 PDManer、逻辑关系、复合 Tuple 和 sidecar 业务规则保留，不创建其余 41 张表，也不把保留的逻辑关系误写为物理外键。

新库使用服务的当前 Schema 和唯一初始化 DML。既有库仅显式应用
[`2026-09-04-code-owned-constraints.sql`](../../paytech/platform-support/docs/database/sql/changes/2026-09-04-code-owned-constraints.sql)。
SQL 是一次性操作，不用于自动启动或重放；MySQL DDL 自动提交，失败应核对进度而非盲目重跑。

## mor-test 执行记录

已完成只读全结构预检、停止单个 Platform 应用、备份、增量 SQL、逐字段/索引/注释核验及恢复健康。25 个外键和 47 个 CHECK 已删除；没有清表、改业务行、重跑初始化或恢复 Bootstrap。

备份位于本机 `test-environment/private/platform-schema-backup.UTy0Y5/`（目录受限、文件 0600，不提交）。迁移前后确定性业务数据导出逐字一致，SHA-256 均为 `431137b3ce373125d05d79cae0423b688941381a4ae9cc8421a3efe3e1f2b10c`。恢复应用后的 HTTP 验收正常新增会话、合成记录与审计，不属于迁移造成的数据变更。

## 验证与限制

- TDD：新 Schema/注释/索引门禁先失败，补齐后通过；新增完整 Tuple 查询门禁先失败，代码调整后通过。
- Java 非数据库测试 53 项通过；新增真实 HTTP/MySQL 测试源码完整编译。缺少 `local-environment` 专用凭据，因此未运行其全量 Java 数据库套件，也未把该套件改指向含人工数据的 mor-test。
- Platform 模型、场景和 Schema 测试共 47 项通过，生成器 `--check` 通过。
- 已更新 mor-test Platform 镜像；既有登录、导航、分页、业务/参数错误、Refresh、Logout 烟测通过。
- [`schema_constraints_smoke.py`](../../test-environment/scripts/schema_constraints_smoke.py) 通过真实 Gateway → BFF → Platform → MySQL 验证错租户 Grant、错 Role Scope、错 Channel、错认证方式均拒绝；仅修改本次合成身份，错误数据在 finally 恢复，合成账号及角色通过正常 API 关闭。
- 既有角色删除/成员计数/幂等/审计验收通过。连续执行人员启用验收曾触发真实 Gateway 429，未放宽限流；剩余合成角色已通过 API 关闭。窗口恢复后单独补跑人员启用验收通过，包含权限上限、禁用→启用、幂等/版本、保留密码及角色、旧会话拒绝、重新登录、CLOSED/Bootstrap 保护和完整审计；所有本次合成记录已按脚本正常关闭。

执行后只读审查原 47 条单行规则及 25 条关联 Tuple，全部零违规；10 个 mor-test 容器均 running/healthy。不能把“数据库不建约束”等同于“不再校验业务关系”。
