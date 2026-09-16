# Platform 邮箱认证：本机单表执行候选

这是阶段 4 当前认证批次的精确执行范围；用户随后回复“可以”，已授权本文件限定的本机单表执行。实际执行与验证状态以[实现台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)为准，[单表回执](../../assets/stage4/platform-auth-table-execution-20260915.json)保留语句指纹与结构备份坐标。本文件不是可重复执行的建表入口。

## 目标与唯一 SQL

| 项目 | 范围 |
|---|---|
| Docker | `desktop-linux` / Compose `mor-local` / 容器 `mor-local-mysql-1` |
| 连接 | `127.0.0.1:13306` |
| 数据库 | 已有 `mor_platform_config_validation` |
| 变更 | 新增 `t_authentication_challenge` 一张表，用于注册邮箱验证、密码重置的一次性链接 |
| SQL 来源 | [platform-support-additions.sql](./platform-support-additions.sql) 第一个完整 `CREATE TABLE t_authentication_challenge` 语句（第 4–33 行） |
| 语句 SHA-256 | `75d3593444014a252fdcfa6b864b3e55840e40e5015308b373cb778e05a6ad30`；精确 UTF-8 CREATE 语句，从 CREATE 起至分号止，不含尾部换行 |

该语句与 [Platform 完整 Schema](../../../../../platform-support-service/docs/database/sql/platform-support-schema.sql) 的同名表逐字一致；不另建一份 SQL 权威。执行时只提取这一条 CREATE，并校验源文件与语句指纹。后续六张文件表、其他系统模型变更和任何初始化 DML 均不在本次范围。

## 已取得的只读证据

[预检记录](../../assets/stage4/platform-auth-preflight-20260915.json)记录实际目标和列、索引比对：现有验证库 25 张表，缺少上述挑战表；认证所需其余 7 张表的 120 列、30 个索引与当前 Schema 一致，包括列顺序、类型、可空性、默认值、列注释、唯一性及索引列顺序/注释。比较显式归一化 MySQL `BOOLEAN` 的实际 `tinyint(1)` 类型。

只读聚合确认 CUSTOMER 账号、CUSTOMER 签名密钥、CLIENT 会话和测试专用日期 `2001-02-04` 的审计链均为空。`platform_app` 已有该库 SELECT/INSERT/UPDATE/DELETE 权限，不具备 CREATE 权限；因此单次建表使用同一已核验本机容器的管理员凭据，之后测试仍使用 `platform_app`，不新增账号或授权。

凭据从已运行且目标核验通过的仓库本机容器读取，只在内存中注入子进程；不读取 `.env.local`，不输出、写入文件或带入命令行参数。

## 获得授权后的执行和验证

1. 重新检查 Docker/端口/库、源指纹、挑战表仍不存在、CUSTOMER 密钥及测试专用审计日期仍为空。使用 Platform 现有 `platform-support:auto:global-catalog` 协调锁串行执行；锁或前置条件失败立即停止。
2. 保存目标库现有表的 `SHOW CREATE TABLE` 结构备份和本次只读盘点，保存权限限制为本机用户可读；不导出业务行。已有表不做 ALTER、DROP、覆盖或清空。
3. 通过指定数据库连接执行唯一 CREATE。禁止执行整个 additions.sql 或完整 Schema，禁止 `--force` 和 `IF NOT EXISTS` 掩盖漂移。DDL 自动提交，不将数据库事务称为回滚保证。
4. 核对新增表的全部字段、类型、可空性、默认值、索引、表/列注释及空表状态；核对已有表结构未变。失败时保留现场并报告，不自动 DROP。
5. 在现有批准验证库和 `127.0.0.1:16379` 的仓库 Redis 上运行 `ClientAuthenticationHttpAutoTest`，通过真实 HTTP → Platform → MySQL/Redis 验证注册/验证/登录/刷新重用撤销、密码重置、锁定与重复退出。Vault 和邮件使用严格本机协议 Simulator，不发送真实外部邮件。
6. 测试只创建并回收本轮随机合成账号、对应会话/挑战、内存测试密钥对应的公钥记录及专属日期审计记录；不运行全库重置或整库初始化。核对回收结果，再登记真实测试证据。任何后续 HTTPS 联通或功能修复继续遵循阶段 4 既定范围，不把单表成功等同完整链路验收。

新增表无需改写已有数据。若需要撤销建表，由人工重新核对该表及引用是否仍为空，再单独决定删除；本候选不预先授权 DROP。

SQL 执行授权要求来自根 [AGENTS.md 的 Change discipline](../../../../../AGENTS.md#change-discipline)：SQL 应用与普通代码研发分别判断授权。本次获准范围仅为上述一个本机验证库的一条 CREATE；不涉及共享测试环境、路由启用或部署。
