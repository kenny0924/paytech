# Platform Operations 资源级锁重构

> 2026-09-05 窄范围后继：[Controller 尽力审计决策](../decisions/2026-09-05-platform-controller-best-effort-audit.md)
> 取代下文“授权拒绝因审计需要可写事务”的目标理由。账号/Session/Tenant 业务保护与审计链内部原子性仍保留；
> 下文实现和验证记录为原变更的历史证据，不代表新审计方案已实施。

日期：2026-09-04。状态：已语义合并到主工作区，并完成真实本地 MySQL/Redis 全链路与覆盖率门禁；尚未部署。

## 已确认原则

用户要求按实际资源选择锁；给用户分配角色优先锁这个用户，不默认锁整个租户。
允许低并发业务使用适度粗粒度 Redis 锁，但必须评估业务不变量、等待和失效后果。
跨服务规范沉淀于 [开发约定 5.1](../DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制)。

本次只重构现有 Platform Operations，不改 HTTP 契约、模型、Schema/初始化 SQL 源文件、Gateway 或其他服务。
本轮只按用户明确许可手工准备仓库 `local-environment` 的 `mor_platform`；独立 `mor-test` 未访问、未修改。
旧统一认证设计中的本地事务/Session 仲裁规则继续有效；常规人员管理必须锁整个 Tenant 的实现假设由本文取代。

## 场景评估

| 场景 | 选择与理由 |
|---|---|
| 人员角色分配、停用、启用、删除 | 共用账号资源 Redis 锁；当前 OPERATIONS 仅一个固定 INTERNAL Tenant，一个账号唯一映射一个成员，不会锁整个租户或操作者 |
| 编辑/删除角色 | 共用角色资源 Redis 锁；角色分配引用该角色时保留数据库局部共享保护，防止删除与授权穿插 |
| 创建人员/角色 | 尚无实体 ID；创建人员以调用方和命令身份协调，创建角色以其业务 Code 协调；最终由现有唯一索引防重 |
| 普通列表、角色详情 | 不使用 Redis/排他锁；详情的权限判断和内容读取使用一致性事务快照 |
| 操作者授权上限 | 只保护实际账号/成员/角色依赖；多个普通命令共享读取同一操作者，不对操作者或其租户加通用排他锁 |
| 最后一个有效管理员 | 只在可能移除管理员时保护 PLATFORM_ADMIN 角色代表的管理员集合；Redis 租约不是这条跨行不变量的唯一保障 |
| Login、密码、Refresh、Bootstrap | 保留现有账号/Session/首次交接事务仲裁，避免为了替换锁改变失败计数、重用撤销或永久 Bootstrap 终态 |
| 审计链追加 | 保留链头事务锁和顺序校验；同一链必须串行，改为 Redis 不会消除链本身的顺序要求 |

本切片账号级 Key 不应直接复制到未来 CUSTOMER 多租户成员授权。届时成员授权必须使用
`tenantId + memberId`，账号级状态变更仍使用全局账号边界，并评估两者的冲突。

## 执行与失败边界

- 使用现有 Foundation Redisson，不修改公共锁实现或新增锁表。
- Redis 锁在事务外获取，事务结束后释放；同资源不同动作共用 Key。
- 逻辑 Key 为 `operations:account:{id}`、`operations:role:{id}`、`operations:role-code:{code}`、
  `operations:create-account:{actorId}:{commandKey}`。复用服务 Namespace；测试使用独立随机前缀。
- 健康 Redis 上等待竞争锁最多 500ms，Lease 30s，SQL 事务 Timeout 15s；连接/命令超时各 2s，
  传输和客户端重试时间不应误算为 500ms 等待预算。超时不是严格 fencing。
- 等待有上限，Lease 显式；拿不到锁返回公共并发错误，不执行命令；Redis 不可用失败关闭。
- 现有 Expected Version、唯一幂等记录和事务回滚继续有效。不能把 Lease 或一次提交前检查当作严格 fencing。
- 原来的 Tenant 排他锁从常规管理和详情读取中删除；仅首次 Bootstrap 交接保留专项仲裁。
- 普通查询为只读 Repeatable Read；PLT-I02 授权拒绝还会写审计，保留独立可写事务而不套资源锁。
- MyBatis 锁定查询禁缓存并刷新会话缓存；SQL 仍只写 XML。
- 应用和测试不自动初始化/迁移数据库，也不切换 `mor-test` 应用。本轮 local 数据库对齐是备份后的人工操作，
  不属于 Java 启动或测试生命周期。

## TDD 与验证记录

1. 在隔离 worktree 读取主线并执行现有 HTTP 基线。
2. 新增有业务意义的资源竞争/并行及读锁回归；先观察失败，再修改生产链。
3. 检查成员与角色命令、管理员保护、审计与认证回归；使用真实 Redis 验证竞争、释放、租约过期、
   中断和 Redis 不可用。
4. 执行生产 SQL/依赖边界、模型、场景和完整覆盖率门禁，并检查数据库与 Redis 测试残留。

实现过程中闭合了三个只在真实边界出现的问题：

- Redisson 4.6.1 会把等待中断包装为 `RedisException`；资源锁只在原因为 `InterruptedException` 时恢复线程
  中断位并映射 409，其他 Redis 异常保持 503。
- 同一固定时刻撤销后重新授予相同角色会碰到历史唯一键；现在先精确恢复本次刚撤销的同一成员、租户、
  角色和生效时间记录，再尝试新增，不再宽泛捕获并吞掉未知主键冲突。
- 两个创建账号请求可使用不同命令标识绕过 Redis 互斥；角色 Code 锁也可能在 Lease 到期后发生重叠。
  现由数据库唯一索引最终仲裁，且只有 Spring 明确归类为 `DuplicateKeyException`、最具体原因命中
  `uk_user_account_email` / `uk_role_scope`，并重新读取到冲突业务事实时才转为业务 400；未知唯一键
  （包括主键冲突）或其他数据库异常继续由全局边界返回 500。

隔离实现验证：

- `./mvnw -q clean verify`：106/106，通过；JaCoCo instruction 12,773/13,095（97.54%）、
  branch 978/1,086（90.06%）、line 1,910/1,999（95.55%）。
- 真实 HTTP/MySQL/Redis 覆盖同资源跨动作竞争、不同账号并行、提交/回滚释放、无 Tenant/操作者排他锁、
  最后管理员竞争、角色删除与分配竞争、同邮箱/角色 Code 唯一键竞争、租约过期后的 owner-safe 释放、
  中断恢复和 Redis Fail-Closed。跨行不变量测试通过仅测试用 MyBatis 两阶段屏障：首个真实 HTTP 事务
  先执行 `lockRole` 并持有实际数据库锁，第二个事务进入同一 SQL 后必须保持阻塞 250ms，随后才释放首个
  事务；把生产 SQL 临时变异为无条件 `FOR SHARE` 时两个测试均按预期转红，恢复 `FOR UPDATE` 后转绿。
- Platform 生成器 `--check` 通过；PDManer 模型 31/31、场景文档 12/12。模型仍为
  63/822/123/99/107/5/7。
- `git diff --check` 通过。测试完成后，local 业务测试表为空；使用 Redis `SCAN` 检查
  `platform-lock-test:*` 为 0，没有清库或使用 `KEYS`。

### local 准备与数据保护

最初只读检查确认 local 是 21 表旧投影，既有固定目录为 Target 1、Route 3、Header 14、Guard 14；
账号、租户、角色、会话、审计和 Key 数据均为空，Schema 含 23 个 FK、44 个 CHECK。获得用户对“备份、
保留现有目录并增量对齐”的明确许可后执行：

1. 先备份 21 表到 `/private/tmp/code-mor-resource-locks.64qcze/backup/mor_platform-before-resource-locks.sql`；
   SHA-256 为 `b3da7805c286704ac1874c4abcf29be832e430cb02a0a10991c2edd919ff6c18`。
2. 增加已批准的 `t_console_resource`，应用一次性代码约束对齐 SQL，再按当前初始化 DML 做保留式增量写入；
   没有重放全库 Schema，也没有覆盖既有行。
3. 删除四条已被 JSON Token 契约取代的固定 CSRF Header 205/208/211/214，并把 Route 101/102/103
   对齐到当前 JSON Token 模式；没有创建默认账号、密码或伪造 ACTIVE Key。
4. 最终为 22 表、0 FK、0 CHECK；固定目录为 Tenant 1、Role 2、Permission 12、Role Permission 16、
   Console Resource 12、ACTIVE Route 20。

一次聚焦测试后发现 28 条 CUSTOMER 夹具账号残留。根因是清理器按精确持有 ID 删除子记录后，仍额外限定
`realm='OPERATIONS'`，而场景已把这些账号迁为 CUSTOMER。修复为只按夹具登记的精确 `account_id` 删除；
删除前复核总账号和固定夹具特征均恰为 28 且无任何子记录，删除行数恰为 28。随后聚焦套件和完整门禁结束后
账号表均保持 0。该清理只发生在 local `mor_platform`，没有访问 `mor-test`。

隔离工作路径：root `/private/tmp/code-mor-resource-locks.64qcze/root`，独立 Platform worktree 位于其
`platform-support-service`。合并时以共同基线做三方语义合并，保留主工作区既有 Audit 与公共规范改动。

主工作区合并后验证：

- `./mvnw -q clean verify`：115/115，通过；JaCoCo instruction 13,127/13,452（97.58%）、
  branch 1,003/1,112（90.20%）、line 1,961/2,052（95.57%）。
- Platform 生成器 `--check` 通过；PDManer 模型 31/31、场景文档 12/12、22 表物理 Schema 4/4。
- local `mor_platform` 通过已审查的一次性 Audit 详情增量从 20 条对齐到当前 21 条 Operations Route；
  该增量未重建 Schema、未重放初始化数据，也未访问 `mor-test`。
