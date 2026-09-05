# Platform Support Review Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 修复本任务确认的 Platform Support 行为、结构和测试问题。

**Architecture:** 保留三个 Maven 模块及所有 HTTP 契约；在 web 内拆分真实能力，Processor 保留事务和锁，Service 使用接口。Gateway 仅同步修正相同路由模板冲突算法。

**Tech Stack:** 仓库现有 Java 25、Spring Boot、MyBatis、Lombok、JUnit、手工准备的 Loopback MySQL/Redis；版本来自既有 Parent/BOM。

**Spec:** `docs/superpowers/specs/2026-09-05-platform-support-review-refactoring-design.md`

## Global Constraints

- 不提交、建分支、推送、部署、启动容器或应用 Schema/DML；保留既有用户改动。
- 所有生产 SQL 留在 Mapper XML；所有业务 Service 为 interface + 同名 Impl。
- 真实 HTTP/MySQL/Redis 链路，不使用 Mockito、方法 Stub、Testcontainers 或自动 Schema 初始化。
- 用户已经批准审查建议和 Lombok 修正，继续完成实现；无需重复询问是否执行。
- 子任务分配不同文件；数据库测试只由主代理协调串行执行。并行代理不得运行共享 reactor 构建或数据库测试。

## Task 1: 测试隔离及 Lombok

**Owner:** test-isolation implementer.

**Files:** Platform `platform-support-test` 的 `GatewaySnapshotHttpAutoTest`、`PlatformSupportHealthAutoTest`、`GatewayConfigurationManagementHttpAutoTest`、`GatewayConfigurationManagementPersistenceContractAutoTest`、`test/operations/OperationsTestMapper`、`OperationsFixtureMocker`、`OperationsHttpChecker`、测试 POM/配置及必要测试基础设施。不修改 `OperationsAuthenticationHttpAutoTest` 与 Compiler 测试。

- [x] 用真实本地目录快照和自有故障 Fixture 证明测试结束不会修改手工 Route；移除固定恢复常量写入。
- [x] 固定 `gateway-management-http` 改为合法唯一运行 marker；默认 setUp 删除两种历史清理调用；清理只按精确持有 ID。
- [x] 为共享全局 Snapshot/JWKS 观察增加明确的跨进程测试独占边界；作用于全部相关 DB 测试，禁止用业务代码实现测试清理。
- [x] 删除按源码截取/`indexOf` 验证锁顺序和完整中文异常文本的测试，保留真实并发/事务与结构门禁。
- [x] `OperationsHttpChecker` 纯赋值构造器使用 `@AllArgsConstructor`；test 显式声明所需 Lombok 处理器，不复制版本。
- [x] 提供失败证据和聚焦命令给主代理串行执行。更新架构门禁使普通包依赖检查覆盖全部 Service，而不是锁死少数类字段列表。

## Task 2: 路由模板交集

**Owner:** route-overlap implementer.

**Files:** Platform `domain/gatewayconfiguration/GatewayConfigurationCompiler.java` 与 `GatewayConfigurationCompilerAutoTest.java`；Gateway 的 `GatewaySnapshotCompiler.java` 及其聚焦测试。只触及必要对应文件。

**Contract:** 固定模板优先；带变量模板之间的交集一律拒绝。

- [x] 回归同 Method 的 `/items/{id}/history` 和 `/items/{id}/{action}` 必须被拒绝；不同 Method、互斥固定 Segment 和完全固定路径优先继续通过。
- [x] 先执行纯编译器反例并保留失败结果；Platform 分组不再按非零变量数量分开，Gateway 删除相等变量数才检查的条件。
- [x] 覆盖现有线性/索引阈值两侧，保留最大目录边界与运行复杂度。
- [x] 主代理运行 `./mvnw -q -Dtest=GatewayConfigurationCompilerAutoTest test` 和 Gateway 对应聚焦命令。不得在另一代理构建 Platform 时并发启动 Maven。

## Task 3: 认证接口与精确审计

**Owner:** authentication implementer.

**Files:** Platform 的 `OperationsAuthenticationService`、`OperationsSessionService`、`OperationsTokenService`、`OperationsAuditService` 及新的 `impl` 实现、内部失败原因枚举、`OperationsAuthenticationHttpAutoTest`。后续经主代理协调扩大到 AuthenticationProcessor、Owner 认证模型和 Identity/Audit Repository；不争改其他代理正在持有的文件。

**Contract:** 原 Service 名变为接口，真实嵌套上下文保留；业务方法改用 Owner Command/Result，Processor 显式转换。HTTP 方法、载荷与外部登录错误不变。

- [x] 增加真实 HTTP 场景断言：未知账号、DISABLED、锁定、无有效凭据、错误密码、成员/Tenant 不可用均返回统一外部失败，内部 reason 不同且无敏感值。
- [x] 先记录失败；引入实现 CodeEnum 的内部 LoginFailureReason，各失败分支传入明确原因。
- [x] 四个 Service 使用 interface + `impl/*Impl`，实现使用 Lombok 及原条件装配；普通调用只依赖接口。
- [x] 清理涉及类中的同义状态复制只在不会破坏当前公开嵌套类型和隐私约束时进行；必要信息报给主代理统筹。
- [x] 主代理串行执行认证 HTTP 回归；保留失败计数、重用撤销、审计链提交顺序和对外反枚举测试。

## Task 4: Directory 能力拆分、管理员规则与分页

**Owner:** main implementation.

**Files:** 原 `OperationsDirectoryService`；新增授权/角色/人员/审计查询接口与实现；`OperationsManagementProcessor`、`OperationsAuthenticationProcessor`；Directory/Identity Repository、Mapper XML、展示摘要 DataObject；新 `OperationsDirectoryRefactoringHttpAutoTest` 与架构门禁。

- [x] 增加管理员降权回归：A 唯一有效管理员，B 为停用或强制改密管理员，B 改普通角色成功；移除 A 仍拒绝。
- [x] 使用现有业务链测试和 SQL Probe 验证 200 人页面结果完整且查询数不随人数逐行增长；不读取 Password verifier。
- [x] 将 authorization/navigation、role CRUD/catalog、account/bootstrap、audit query 分为有职责的服务；共同授权上限/管理锁规则集中，事务仍由 Processor 包住。
- [x] 管理员资格判断集中到可复用业务规则，停用/删除/角色分配调用；保留原资源锁命名、局部共享锁和 CAS。
- [x] 人员摘要分页查询包含成员/forceChange 展示事实，再一次批量加载本页角色，保留稳定排序和 COUNT 条件。
- [x] 移除 Owner web JAR 内纯镜像状态转换，保留聚合和安全审计属性投影；统一三个 MyBatis Repository 实现为 `*RepositoryImpl` 并更新引用。
- [x] 新增 Service 使用接口和 Lombok；有实际初始化的 Processor 构造器保持显式，不为了注解制造配置抽象。

## Task 5: 集成、独立审查与验收

**Owner:** main coordination, independent reviewer.

- [x] 每次行为更改记录 RED/GREEN；子任务 diff 核对规范、范围和接口兼容性。
- [x] 合并后运行 Platform `./mvnw -q clean verify`；读取 Surefire 和 JaCoCo 报告确认结果。
- [x] 运行 Gateway 对应纯编译器/路由门禁及必要完整构建；将已有无关失败单独记录。
- [x] 执行 `npm --prefix requirements-site test` 和触及仓库 `git diff --check`，核对本次 fixture 与 Redis 前缀清理。
- [x] 独立评审所有变更，修正有根据的问题后再跑覆盖范围内检查。
- [x] 更新 Platform/Gateway README 与 `agent-docs/CURRENT_IMPLEMENTATION.md` 的本次实现/验证记录；部署状态不变。

## Progress

- 2026-09-05：用户批准本次审查问题及 Lombok 修正，五组任务实现与验收已完成；主工作区和各应用仓库的其他用户改动保留。
- 初始分工随后按实际边界扩展：Repository 命名/镜像收敛、共享管理规则、Processor 显式转换和 Snapshot/JWKS 领域边界分别交接给独立文件 Owner；没有新增 Maven 模块或改变 HTTP 契约。
- Directory 拆分为人员、身份创建、角色、授权导航、审计查询与管理规则六个能力；保留资源锁、事务、CAS 和幂等载荷字段/命名空间。审计筛选仍先鉴权再构造已验证的数据库查询。
- 认证 RED 最初因缺少环境凭据未执行到断言；获用户明确许可后，只为 local 测试在内存读取凭据。随后以受控变异重现旧审计原因的失败，并恢复修复后完成完整验证。
- Platform Maven/数据库测试始终串行；Gateway 的纯算法门禁使用独立构建目录。完成只读交叉评审、真实跨 JVM 测试锁竞争/释放检查、目录快照与测试残留核验；未更改部署环境或数据库结构。
- 当前实现、验证数量/覆盖率及部署边界统一见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 第 6 节。完整 Gateway 套件未重跑，不将聚焦成功外推为全库通过。
