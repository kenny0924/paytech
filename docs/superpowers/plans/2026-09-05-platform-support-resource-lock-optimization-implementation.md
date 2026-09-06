# Platform Support 资源锁优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变 HTTP、JWT、Header、PDManer、Schema 与初始化数据的前提下，让 Platform Support 单版本消费 Foundation `@ResourceLock(prefix, key = "")`，迁移 Operations 管理与 Gateway 手写 Redis 锁，并把认证慢工作移到短数据库事务之外，同时删除已由条件 DML、唯一键、SERIALIZABLE 或 CAS 接管的重锁。

**Architecture:** Foundation 独立计划先交付公共 Annotation、启动门禁、受限 Key 和服务级 Wait/Lease；Platform 不复制 Foundation 内部实现。Platform 先在无写事务阶段完成确定资源所必需的最小 Vault 查询或带域分隔 HMAC 摘要，再调用代理可达的具体 `*CommandProcessor` public 方法取得一个实际资源锁；密码校验、Vault Reveal/Protect、Argon2id 编码与 Token 签名在锁内但仍位于 `OperationsTransactionExecutor` 事务之外。Redis 只优化跨实例竞争；Session X、Actor Account/Member 与 Role 授权锁、最后管理员 Role X、Bootstrap Account X、Audit Chain X、Gateway SERIALIZABLE/Root CAS、条件 UPDATE 和唯一索引继续做数据库最终仲裁。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Foundation `paytech-redis-starter`、Spring AOP、Redisson、Spring JDBC `TransactionTemplate`、MyBatis XML、MySQL 8.4、Redis 8.2.1、JUnit 5、RestTestClient、Awaitility、仓库 `local-environment`。

**Specs:** [`Foundation @ResourceLock design`](../specs/2026-09-05-foundation-resource-lock-design.md)、[`Platform Support resource-lock optimization design`](../specs/2026-09-05-platform-support-resource-lock-optimization-design.md)、[`Foundation implementation plan`](2026-09-05-foundation-resource-lock-implementation.md)

## Global Constraints

- Foundation 源码、公共 API、Advisor、异常和通用测试只由其独立 implementation plan 拥有。本计划只在 Foundation 真实 Redis/启动门禁/事务顺序/JAR API 全部通过后消费本机 Snapshot。
- 执行前分别记录根工作区、`paytech-foundation`、`platform-support-service` 的 Branch、HEAD 与 `git status --short`。保留已有用户/并行任务修改，只编辑本计划列出的文件。
- 不创建分支，不暂存或提交，不推送，不发布制品，不部署，不启动/停止/重建容器，不迁移或清空数据库。Foundation 本机 Maven `install` 仅供独立 Platform Reactor 解析，不是发布或部署。
- 每个 Task 先运行最小 RED，再写最小实现并运行同一命令至 GREEN。环境缺失、编译工具错误或既有无关失败不能作为行为 RED。
- 自动测试只复用仓库 `local-environment` 的 `127.0.0.1:13306/mor_platform` 和 `127.0.0.1:16379`；不用 H2、Testcontainers、嵌入式 Redis、Mockito、`@MockBean`、Spy、固定 sleep、Docker API或自动 Schema/DML。
- HTTP/OpenAPI、JWT Claim、Gateway/BFF Header、表/列/索引、PDManer 与初始化 DML 不变。不新增锁表、认证状态表、Catalog Revision、fencing、自动续租或第二 Redis Client。
- 每个 Platform command 最多一个 Redis 资源锁。Annotation 只放在 Spring Proxy 可达的具体 public Processor/协调组件方法；Controller、Service、Repository、Mapper、private/final 方法和同类自调用不得持锁。
- `loginIdentifier`、邮箱、Vault Token、密码、Refresh Token、lookupId、JWT、HMAC、原始 `Idempotency-Key` 和请求体不得进入 Key、异常、普通日志、Trace 或 Metric Label。未知 Login/Refresh 与 Create Account 只使用现有认证 HMAC 的带域分隔摘要；HMAC/Vault 不可用按 503 失败关闭。
- Platform 服务级配置固定为 `wait-time=500ms`、`lease-time=30s`；仅 Lease-overlap 测试类可覆盖更短 Lease。
- Controller 尽力审计及 Audit append 算法不在本次重构范围；业务 Processor 不新增审计生命周期依赖。

### 数据库锁删留验收表

| 场景 | 删除 | 明确保留 |
|---|---|---|
| Login | Account X、Tenant SHARE | password failure 原子条件 UPDATE；成功 version/credentialVersion/authEpoch CAS；Session/Refresh 唯一键 |
| Change Password | Account X、Tenant SHARE、ACTIVE Sessions 锁定读取 | Account/Password 条件更新；Session/Refresh 集合 UPDATE 实际行锁 |
| Refresh / Logout | Account X、Tenant SHARE | **Session `FOR UPDATE`**，Refresh consume CAS，Session generation 条件推进 |
| Bootstrap | Internal Tenant X | **Bootstrap Account `FOR UPDATE`** 与交接终态重检 |
| Account 管理 | Target Account 提前 X | **Actor Account 与当前 INTERNAL Member `FOR SHARE`**、相关 Role SHARE/X、最后管理员 PLATFORM_ADMIN Role X、Target Account/Member expectedVersion DML |
| Gateway | Target/Route 内层 Redis、Root 提前 X | **Catalog Redis + SERIALIZABLE + Root expectedVersion CAS** |
| Audit | 无 | **ensureChain + Chain Head `FOR UPDATE`** 与独立审计事务 |

任何删除都必须和替代 SQL、affected-row 检查及真实 Lease-overlap 测试在同一 Task 转绿。

---

## P0：Foundation 消费、Operations 管理与 Gateway

### Task 0: 冻结基线并通过 Foundation 消费 Gate

**Read-only files:**

- `docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md`
- `paytech-foundation/paytech-redis-starter/target/paytech-redis-starter-1.0.0-SNAPSHOT.jar`
- `agent-docs/CURRENT_IMPLEMENTATION.md`
- `platform-support-service/README.md`

**Interfaces consumed:**

```java
@Repeatable(ResourceLocks.class)
public @interface ResourceLock {
    String prefix();
    String key() default "";
}

public @interface ResourceLocks {
    ResourceLock[] value();
}
```

- [ ] 记录三个工作树基线：

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service status --short --branch
```

- [ ] 按 Foundation 独立计划完成其 Task 0–10，再运行消费 Gate：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-redis-starter -am \
  -Dtest=ResourceLockArchitectureAutoTest,ResourceLockAnnotationContractAutoTest,ResourceLockMetadataCompilerAutoTest,ResourceLockKeyResolverAutoTest,RedisResourceLockAutoConfigurationAutoTest,ResourceLockExecutorAutoTest,ResourceLockMethodInterceptorAutoTest,ResourceLockTransactionBoundaryAutoTest,RedisStarterIntegrationAutoTest,ResourceLockRedisIntegrationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw clean verify
```

Expected GREEN: real Redis 同 Key/不同 Key、跨 Client/JVM、受限表达式、排序/去重/总 Wait、事务外获取、Lease owner-safe、中断、不可用、释放失败和秘密不泄漏均通过；POM/源码不含 Testcontainers。

- [ ] 检查 JAR 公共面并本机安装同一 Snapshot；若公共类型/配置/测试缺失则停止，不能在 Platform 临时复制实现：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
javap -classpath paytech-redis-starter/target/paytech-redis-starter-1.0.0-SNAPSHOT.jar \
  com.paytech.foundation.redis.api.ResourceLock \
  com.paytech.foundation.redis.api.ResourceLocks
./mvnw -pl paytech-redis-starter -am install -DskipTests
```

- [ ] 运行 Platform 当前基线并记录测试数/覆盖率/已有失败：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -q -pl platform-support-test -am \
  -Dtest=PlatformSupportConfigurationAutoTest,PlatformArchitectureAutoTest,OperationsLockContractAutoTest,OperationsPersistenceContractAutoTest,OperationsAuthenticationHttpAutoTest,GatewayConfigurationManagementPersistenceContractAutoTest,GatewayConfigurationManagementHttpAutoTest,OperationsAuditBestEffortHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

### Task 1: P0 RED/GREEN 配置、安全 Key、管理注解与 Target 锁缩小

**Files:**

- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportConfigurationAutoTest.java:21-93`
- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsLockContractAutoTest.java:1-25`
- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java:38-63,132-156`
- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java:337-365,712-780`
- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/architecture/PlatformArchitectureAutoTest.java:100-164`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsResourceKeyService.java`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsResourceKeyServiceImpl.java`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsManagementCommandProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsManagementProcessor.java:26-32,47-115,224-245`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsManagementPolicyService.java:9-14`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsManagementPolicyServiceImpl.java:39-68`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/OperationsIdentityRepository.java:30-34`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/OperationsIdentityRepositoryImpl.java:31-38`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/OperationsIdentityMapper.java:32-35`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsIdentityMapper.xml:57-65`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/GatewayConfigurationManagementServiceImpl.java:202-211`
- Modify: `platform-support-service/platform-support-web/src/main/resources/application.yml:84-86`
- Modify: `platform-support-service/platform-support-test/src/test/resources/application-auto.yml:1-18`

**Interfaces produced:**

```java
public interface OperationsResourceKeyService {
    String accountSegment(String candidate);
    String roleSegment(String candidate);
    String roleCodeSegment(String candidate);
    String createAccountSegment(String actorId, String idempotencyKey);
    String unknownLoginSegment(String loginIdentifier);
    String unknownRefreshSegment(String refreshToken);
}

Long OperationsIdentityRepository.lockManagementActor(long accountId);
```

`accountSegment`/`roleSegment` 返回正整数或固定 fallback；Role Code 只接受 `[A-Z][A-Z0-9_]{0,63}`。Create Account 返回 `{safeActor}:{hmacVersion}:{digest}`，purpose 为 `operations-resource-lock:create-account:v1`；未知 Login/Refresh 分别使用 `operations-resource-lock:login-attempt:v1` 和 `operations-resource-lock:refresh-attempt:v1`。摘要只能调用现有 `OperationsTokenCryptography`，且该端口以一次原子调用返回同一密钥快照的 Version 与 Digest；不得分开读取当前 Version 和计算 HMAC。

`OperationsManagementCommandProcessor` 提供八个具体 public command：

```java
@ResourceLock(prefix = "operations:create-account", key = "#resourceKey")
AccountDetails createAccount(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, CreateAccountCommand command);

@ResourceLock(prefix = "operations:account", key = "#resourceKey")
void disableAccount(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String accountId, DisableAccountCommand command);

@ResourceLock(prefix = "operations:account", key = "#resourceKey")
void enableAccount(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String accountId, EnableAccountCommand command);

@ResourceLock(prefix = "operations:account", key = "#resourceKey")
void deleteAccount(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String accountId, DeleteAccountCommand command);

@ResourceLock(prefix = "operations:account", key = "#resourceKey")
void assignRoles(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String accountId, AssignRolesCommand command);

@ResourceLock(prefix = "operations:role-code", key = "#resourceKey")
RoleDetails createRole(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, CreateRoleCommand command);

@ResourceLock(prefix = "operations:role", key = "#resourceKey")
RoleDetails updateRole(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String roleId, UpdateRoleCommand command);

@ResourceLock(prefix = "operations:role", key = "#resourceKey")
void deleteRole(String resourceKey, String actorId, String sessionId,
    String idempotencyKey, String roleId, DeleteRoleCommand command);
```

- [ ] RED：配置测试要求生产/auto YAML 都绑定 `500ms/30s`；Key 测试要求确定性、purpose 隔离、key version、fallback 与秘密不出现，并要求 Create Account 的 HMAC 不可用时在事务前以 503 失败而不生成明文 Key；架构测试要求八个注解方法、跨 Bean 代理调用且查询无锁；持久化/HTTP 测试要求只锁 Actor Account/Member SHARE，不锁 Target Account X，并在 Lease-overlap 下由 Target expectedVersion/CAS 决出唯一终态。
- [ ] 运行 RED：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -q -pl platform-support-test -am \
  -Dtest=PlatformSupportConfigurationAutoTest,OperationsLockContractAutoTest,OperationsPersistenceContractAutoTest,PlatformArchitectureAutoTest,OperationsAuthenticationHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] GREEN：实现 `interface + *Impl` 安全 Key；两份 YAML 增加 `redis.coordination.resource-lock.wait-time/lease-time`；外层 Management Processor 只映射并调用新代理 Bean；Command Processor 在 Annotation 内执行一次 `transactions.write(callback)`。
- [ ] GREEN：将 `lockManagementAccount(id, exclusive)` 改成固定 `lockManagementActor(id) FOR SHARE`，并用 `lockManagementMember(actorId) FOR SHARE` 锁住权限检查依赖的 Actor 当前 INTERNAL Member；不恢复 Target Account 或 Tenant 大锁。Policy 按稳定 ID 锁 Actor Role、requested Role、edited/deleted Role 与必要 PLATFORM_ADMIN Role；只有 Enable 为恢复既有授权而共享锁定目标全部当前 Role，Disable/Delete/Assign 不锁即将移除且非 PLATFORM_ADMIN 的普通旧 Role，最后执行 Target expectedVersion DML。Gateway 授权检查使用同一 Actor Account/Member SHARE 合同。
- [ ] 重跑相同命令至 GREEN；再运行角色删除/分配和最后管理员的真实两阶段锁测试，确认 Role SHARE/X 与 PLATFORM_ADMIN X 仍能阻塞冲突事务；同时证明 Disable/Delete/Assign 不等待无关目标旧 Role，Enable 会等待其待恢复 Role。

### Task 2: P0 RED/GREEN Gateway 三层协调并删除手写 Redis 锁

**Files:**

- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationManagementPersistenceContractAutoTest.java:77-104`
- Modify tests first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationManagementHttpAutoTest.java:272-319`
- Create test: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationLeaseOverlapHttpAutoTest.java`
- Create test: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsResourceLockFailureAutoTest.java`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java:337-435`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/architecture/PlatformArchitectureAutoTest.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/GatewayConfigurationManagementProcessor.java:1-36,48-70,136-151`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/GatewayConfigurationManagementRepository.java:21-35`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/GatewayConfigurationManagementRepositoryImpl.java:39-80`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/GatewayConfigurationManagementMapper.java:21-46`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/GatewayConfigurationManagementMapper.xml:35-54,98-143`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/GatewayConfigurationManagementServiceImpl.java:90-120,145-183,213-227`
- Delete after all callers migrate: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsResourceLock.java`
- Delete after all callers migrate: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/Locks.java`

**Interfaces produced:**

```java
@ResourceLock(prefix = "gateway-configuration:catalog")
public GatewayTargetStatusResponseBody changeTargetStatus(String actor, String session,
    String idempotencyKey, String targetServiceCode,
    ChangeGatewayTargetStatusRequestBody request);

@ResourceLock(prefix = "gateway-configuration:catalog")
public GatewayRouteStatusResponseBody changeRouteStatus(String actor, String session,
    String idempotencyKey, String routeCode,
    ChangeGatewayRouteStatusRequestBody request);

GatewayTargetManagement target(String code);
GatewayRouteRoot routeRoot(String code);
```

删除 `lockTarget(String)`、`lockRoute(String)`；普通 Root read 无 `FOR UPDATE`。`transactions.serializableWrite`、完整候选 Catalog 编译、Root `WHERE version=?` CAS 和 `ConcurrencyFailureException -> ConcurrentRequestException(cause)` 保持。

- [ ] RED：持久化合同要求旧 lock methods/SQL 消失而 CAS 保持；HTTP 测试要求 Target/Route 竞争同一个固定 Catalog Key且 Redis 无 target/route 子 Key；300ms test-only Lease 到期后让“停用 Target”和“启用引用 Route”基于旧 Catalog 重叠，真实 SERIALIZABLE 至多提交一个。
- [ ] RED：消费者失败测试经 Spring Proxy持有/竞争业务 Key，断言 wait timeout/interruption 是 409且业务 SQL零执行；独立 Context 指向未监听 Loopback Redis端口，经真实 HTTP断言 503且零业务写入。不能直接 new Foundation Executor或关闭共享 Redis。
- [ ] 运行 RED：

```bash
./mvnw -q -pl platform-support-test -am \
  -Dtest=GatewayConfigurationManagementPersistenceContractAutoTest,GatewayConfigurationManagementHttpAutoTest,GatewayConfigurationLeaseOverlapHttpAutoTest,OperationsResourceLockFailureAutoTest,PlatformArchitectureAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] GREEN：两个 Gateway command 直接使用固定 Annotation并只包 `serializableWrite`；Target 用普通 `target`，Route 新增普通 `routeRoot`；删除内层 Redis、显式 Root X、旧 `auditCode/command` helper。
- [ ] 确认 Management/Gateway 已无手写调用后删除 `OperationsResourceLock` 与 `Locks`。架构测试拒绝 Platform 生产中的 `RedissonClient`、`RLock`、`getLock`、同义 wrapper、Controller/Service/Repository/Mapper Annotation、self-invocation和敏感表达式。
- [ ] 重跑相同命令至 GREEN，并确认陈旧 expectedVersion 仍由 CAS 409，序列化/死锁错误保留 Cause。

### Task 3: P0 业务回归 Gate

**Files:**

- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuditBestEffortHttpAutoTest.java:473-504`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsDirectoryRefactoringHttpAutoTest.java`

- [ ] 运行 P0 聚焦 Gate：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -q -pl platform-support-test -am \
  -Dtest=PlatformSupportConfigurationAutoTest,PlatformArchitectureAutoTest,OperationsLockContractAutoTest,OperationsPersistenceContractAutoTest,OperationsResourceLockFailureAutoTest,OperationsAuthenticationHttpAutoTest,OperationsDirectoryRefactoringHttpAutoTest,GatewayConfigurationManagementPersistenceContractAutoTest,GatewayConfigurationManagementHttpAutoTest,GatewayConfigurationLeaseOverlapHttpAutoTest,OperationsAuditBestEffortHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] Expected GREEN：Management Account/Role/Role Code/Create Account 与 Gateway Catalog 已单版本切换到 Foundation；同资源跨动作互斥、不同资源并行、提交/回滚后释放、409/503、唯一键、Target CAS、Actor Account/Member、Role/PLATFORM_ADMIN锁、Gateway SERIALIZABLE/CAS和锁拒绝审计均有证据。
- [ ] 使用每测试随机 `redis.key-prefix` 以 SCAN 确认本次 Namespace 无残留且相邻 Sentinel 未被删除。P0 未 GREEN不得进入 P1。

---

## P1：认证短事务与数据库锁删留

### Task 4: P1 RED/GREEN 分阶段认证边界、Token 预签名与 Login CAS

**Files:**

- Create tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationConcurrencyHttpAutoTest.java`
- Create test support: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsSlowWorkProbe.java`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java:132-156`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/architecture/PlatformArchitectureAutoTest.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domain/authentication/OperationsAuthenticationModels.java:1-53`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationCommandProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationProcessor.java:26-95`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsAuthenticationService.java:1-32`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsAuthenticationServiceImpl.java:41-128`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsTokenService.java:1-23`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsTokenServiceImpl.java:36-88`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/OperationsIdentityRepository.java:43-49`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/OperationsIdentityRepositoryImpl.java:86-108`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/OperationsIdentityMapper.java:44-51`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsIdentityMapper.xml:141-165`

**Interfaces produced:**

```java
record LoginResolution(Long accountId, String resourceKey) {}
record PreparedTokens(OperationsRefresh refresh, TokenResult response) {}
record PreparedLogin(OperationsAccount account, OperationsPassword password,
    OperationsMember member, OperationsSession session, PreparedTokens tokens,
    LoginFailureReason failureReason, Instant preparedAt) {}

LoginResolution resolveLogin(LoginCommand command);
PreparedLogin prepareLogin(long accountId, LoginCommand command);
AuthenticationOutcome commitLogin(PreparedLogin prepared);
AuthenticationOutcome rejectUnknownLogin(LoginCommand command);

PreparedTokens OperationsTokenService.prepare(OperationsAccount account,
    OperationsPassword password, OperationsSession session, int generation, Instant now);
void OperationsTokenService.persist(PreparedTokens prepared, Instant now);

int passwordFailure(long accountId, int expectedCredentialVersion,
    int threshold, Instant lockedUntil, Instant now);
int passwordSuccess(long accountId, int expectedVersion,
    int expectedCredentialVersion, int expectedAuthEpoch, Instant now);
```

Command Processor 精确入口：

```java
@ResourceLock(prefix = "operations:account", key = "#resourceKey")
AuthenticationOutcome loginAccount(String resourceKey, long accountId, LoginCommand command);

@ResourceLock(prefix = "operations:login-attempt", key = "#resourceKey")
AuthenticationOutcome loginAttempt(String resourceKey, LoginCommand command);
```

- [ ] RED：test-only `OperationsSlowWorkProbe` 只观察并 `proceed()` 真实 Vault/Argon2/HMAC/sign，断言 `transactionActive=false`；慢点期间另一连接可取得旧 Account/Tenant锁，但相同标识仍被 Redis协调。300ms Lease-overlap错误密码必须基于 DB当前 failures 原子累计并准确触发第 5 次锁定。
- [ ] RED：正确 Login 与改密/停用竞争时，只有 version/credentialVersion/authEpoch CAS成功者能插 Session/Refresh；已知/未知标识外显同一安全错误且 Key不含标识/Vault token；Login 资源解析的 Vault 或未知标识 HMAC 不可用时必须事务前返回 503，不得降级为 UNKNOWN_ACCOUNT。运行：

```bash
./mvnw -q -pl platform-support-test -am \
  -Dtest=OperationsAuthenticationConcurrencyHttpAutoTest,OperationsPersistenceContractAutoTest,PlatformArchitectureAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] GREEN：外层 Processor 先 `resolveLogin` 再跨 Bean 选择已知/未知 Annotation；`prepareLogin` 在 Account Redis内、事务外完成真实/等价 Argon2并预签 Token；`commitLogin` 短事务重读并执行失败原子 UPDATE或成功 CAS + Session insert + prepared Refresh insert。Token只在提交后返回。
- [ ] 所有 `Prepared*` 内部 Record 只要包含密码、Token、Vault Token、PHC 或其他敏感材料，必须覆盖安全 `toString()`，禁止使用 Record 默认全字段输出。
- [ ] `passwordFailure` 用 `failures=failures+1` 和 expected credentialVersion/可计数状态；`passwordSuccess` 用 expected version/credentialVersion/authEpoch/ACTIVE。删除 Login 的 Account X与 Tenant SHARE，不删除 UPDATE实际锁或唯一键。
- [ ] 重跑相同命令 GREEN，并回归 disabled/locked/损坏 Credential、Bootstrap Login、Token claims与审计。

### Task 5: P1 RED/GREEN Refresh/Logout 仅保留 Session X

**Files:**

- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationConcurrencyHttpAutoTest.java`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java:77-93,132-156`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domain/authentication/OperationsAuthenticationModels.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationCommandProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationProcessor.java:50-67`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsAuthenticationService.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsAuthenticationServiceImpl.java:130-239`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/OperationsIdentityRepository.java:49-59`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/OperationsIdentityRepositoryImpl.java:106-132`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/OperationsIdentityMapper.java:51-63`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsIdentityMapper.xml:165-209`

**Interfaces produced:**

```java
record RefreshResolution(Long sessionId, String resourceKey) {}
record PreparedRefresh(OperationsRefresh refresh, OperationsSession session,
    OperationsAccount account, OperationsPassword password, OperationsMember member,
    PreparedTokens tokens, Instant preparedAt) {}
record PreparedLogout(long accountId, OperationsSession session,
    OperationsRefresh refresh, Instant preparedAt) {}

RefreshResolution resolveRefresh(RefreshCommand command);
PreparedRefresh prepareRefresh(long sessionId, RefreshCommand command);
AuthenticationOutcome commitRefresh(PreparedRefresh prepared);
AuthenticationOutcome rejectUnknownRefresh(RefreshCommand command);
PreparedLogout prepareLogout(String accountId, String sessionId, RefreshCommand command);
void commitLogout(PreparedLogout prepared);

int consumeRefresh(long refreshId, int expectedGeneration, Instant now);
int linkRefresh(long refreshId, long replacementId);
int advanceSession(long sessionId, int expectedGeneration, int nextGeneration,
    int expectedAuthEpoch, int expectedCredentialVersion, Instant now, Instant idleExpireTime);
```

Command Processor 的已知 Refresh/Logout 使用 `@ResourceLock(prefix="operations:session", key="#resourceKey")`；未知 Refresh 使用 `operations:refresh-attempt`。raw token、lookupId和saved HMAC不绑定 Annotation。

精确入口为：

```java
@ResourceLock(prefix = "operations:session", key = "#resourceKey")
AuthenticationOutcome refreshSession(String resourceKey, long sessionId, RefreshCommand command);

@ResourceLock(prefix = "operations:refresh-attempt", key = "#resourceKey")
AuthenticationOutcome refreshAttempt(String resourceKey, RefreshCommand command);

@ResourceLock(prefix = "operations:session", key = "#resourceKey")
void logoutSession(String resourceKey, String accountId, String sessionId, RefreshCommand command);
```

- [ ] RED：HMAC/JWT准备必须在事务和 Session X之前；持有真实 Session X时准备完成后才等待。覆盖同 Session双 Refresh、Refresh vs Logout、reuse撤销、Refresh vs改密/停用，以及同 Account不同 Session并行；资源解析或 Token 校验的 HMAC 不可用时必须事务前返回 503，不得当作未知 Refresh。
- [ ] RED：持久化合同要求 `lockSession FOR UPDATE` 保持；consume约束 ISSUED/active_slot/generation/expiry；advance约束 ACTIVE/expected generation/authEpoch/credentialVersion；affected rows逐项校验。运行：

```bash
./mvnw -q -pl platform-support-test -am \
  -Dtest=OperationsAuthenticationConcurrencyHttpAutoTest,OperationsPersistenceContractAutoTest,OperationsAuthenticationHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] GREEN：`resolveRefresh` 事务外解析公开 lookup并选择 Session/HMAC fallback；`prepareRefresh` 在 Redis内、事务外验证 HMAC和预签下一代；`commitRefresh` 第一项数据库仲裁为 Session X，随后重读并按 consume → persist → link → advance 执行，任一步失败整笔回滚且不返回 Token。
- [ ] Logout 在 Redis内、事务外做 HMAC，短事务保留 Session X并重读 ownership/Refresh affiliation后撤销。删除 Refresh/Logout Account X与 Tenant SHARE，重跑同一命令 GREEN。

### Task 6: P1 RED/GREEN Change Password、人员凭据与 Bootstrap；清除废弃大锁

**Files:**

- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationConcurrencyHttpAutoTest.java`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java:38-63,132-156`
- Modify tests: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/architecture/PlatformArchitectureAutoTest.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domain/authentication/OperationsAuthenticationModels.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationCommandProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsAuthenticationProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/OperationsManagementCommandProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsAuthenticationService.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsAuthenticationServiceImpl.java:241-275`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsAccountProvisioningService.java:1-15`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsAccountProvisioningServiceImpl.java:27-78`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsAccountService.java:17-21`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsAccountServiceImpl.java:90-125`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsManagementPolicyService.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsManagementPolicyServiceImpl.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsSessionService.java:1-25`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsSessionServiceImpl.java:23-40`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/OperationsIdentityRepository.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/OperationsIdentityRepositoryImpl.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/OperationsIdentityMapper.java`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsIdentityMapper.xml`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsTestMapper.java`

**Interfaces produced:**

```java
record PreparedPasswordChange(OperationsAccount account, OperationsPassword password,
    OperationsSession session, String nextVerifier, Instant preparedAt) {}
record PreparedHumanCredentials(String canonicalEmail, String vaultToken,
    String maskedEmail, String verifier) {}

PreparedPasswordChange preparePasswordChange(String accountId, String sessionId,
    ChangePasswordCommand command);
void commitPasswordChange(PreparedPasswordChange prepared);

PreparedHumanCredentials OperationsAccountProvisioningService.prepareHuman(
    String email, String initialPassword);
AccountCreatedResult OperationsAccountProvisioningService.createHuman(
    PreparedHumanCredentials prepared, List<String> roleIds, long actorId);

int changeAccountPassword(long accountId, int expectedVersion,
    int expectedCredentialVersion, int expectedAuthEpoch, Instant now);
int changePassword(long accountId, int expectedCredentialVersion,
    String verifier, Instant now);
int revokeAccountSessions(long accountId, String status, String reason, Instant now);
int revokeAccountRefresh(long accountId, Instant now);
OperationsAccount lockBootstrapAccount(long accountId);
```

Change Password 与 Create First Administrator command 都用 `@ResourceLock(prefix="operations:account", key="#resourceKey")`。Management Create Account仍用 P0 create-account摘要锁，但先 `prepareHuman` 再开事务。

Command Processor 和 Account Service 的精确边界为：

```java
@ResourceLock(prefix = "operations:account", key = "#resourceKey")
void changePassword(String resourceKey, String accountId, String sessionId,
    ChangePasswordCommand command);

@ResourceLock(prefix = "operations:account", key = "#resourceKey")
AccountCreatedResult createFirstAdministrator(String resourceKey, String actorId,
    String sessionId, FirstAdministratorCommand command);

AccountDetails OperationsAccountService.createAccount(String actorId, String sessionId,
    String idempotencyKey, CreateAccountCommand command, PreparedHumanCredentials prepared);

AccountCreatedResult OperationsAccountService.createFirstAdministrator(String actorId,
    String sessionId, FirstAdministratorCommand command, PreparedHumanCredentials prepared);
```

`OperationsManagementCommandProcessor.createAccount(...)` 在已取得 create-account Redis 锁后先调用 `prepareHuman`，再把 `PreparedHumanCredentials` 传入事务回调中的 `OperationsAccountService.createAccount(...)`。`createFirstAdministrator(...)` 同样在 Bootstrap Account Redis 锁内准备凭据，再开短事务；不在外层 Processor 锁前重复 Vault/Argon2 准备。

- [ ] RED：Vault Reveal/Protect、current-password Argon2、new-password validate/compare/encode都必须在 Redis内、事务外；stale version/credentialVersion使 Account/Password CAS整笔回滚。Mapper不得先 `activeSessions FOR UPDATE`，改为 Session/Refresh有状态集合 UPDATE并与单 Session X正确串行。
- [ ] RED：不同 command digest创建同邮箱仍由 `uk_user_account_email` 只留一个账号；未知主键/数据库异常不能吞掉。两个 Bootstrap命令在300ms Lease到期后由 Bootstrap Account X最终只允许一个管理员且交接全有或全无。另覆盖 Actor 权限检查后被撤权的双事务，Actor Member SHARE 必须让撤权等待；覆盖 Enable 与 Assign Roles 的 Lease-overlap，Enable 的最终 DML 必须约束其授权上限校验时读取的 Member Version。再构造两个管理员分别操作对方账号的共享到排他等待环，MySQL 死锁/锁等待牺牲者必须回滚并由 Command Processor 将 `ConcurrencyFailureException` 保留 Cause 地映射为 409，不能外显 500 或自动重放事务。
- [ ] RED：最终静态锁表必须仅含 Identity `lockBootstrapAccount` X、`lockManagementActor` SHARE、`lockManagementMember` SHARE、`lockSession` X；Directory Role SHARE/X；Audit `lockChain` X。`lockTenant`、`lockInternalTenant`、`lockAccounts`、`activeSessions` 和动态 `lockManagementAccount`必须不存在。运行：

```bash
./mvnw -q -pl platform-support-test -am \
  -Dtest=OperationsAuthenticationConcurrencyHttpAutoTest,OperationsAuthenticationHttpAutoTest,OperationsPersistenceContractAutoTest,PlatformArchitectureAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] GREEN：Change Password短事务重读快照，执行 Account CAS → Password CAS → Session set revoke → Refresh set revoke；任一必需 affected row不符则回滚。删除 Account X、Tenant SHARE、activeSessions锁定读取。
- [ ] `passwordFailure/passwordSuccess/consumeRefresh/linkRefresh/advanceSession/changeAccountPassword/changePassword` 都以已批准的状态、Version、Credential Version、Auth Epoch 或 Generation 条件做 DML，需要唯一成功的步骤逐项校验 affected rows = 1；Session/Refresh 账号级撤销使用集合 UPDATE，不恢复预读全部 Session 的锁定查询。
- [ ] GREEN：Provisioning拆为 `prepareHuman/createHuman`；Management Create与Bootstrap均先慢准备再事务。Bootstrap事务第一项为明确的 `lockBootstrapAccount` 且 SQL 以 `FOR UPDATE` 结束，随后对已锁 Bootstrap 快照做纯重检，再校验 Tenant/目录/Human count 并完成交接；不能再通过 `requireBootstrapAdministrator` 隐式调用泛化 Account X，删除 Internal Tenant X。
- [ ] 删除 `OperationsSessionService.lockTenant`、`lockTenant`、`lockInternalTenant`、泛化 `lockAccounts`、`activeSessions` 及所有调用，不留 deprecated双路径。重跑同一命令 GREEN并对保留锁做真实两阶段 mutation test。

### Task 7: 全场景验收、文档与准确状态

**Files:**

- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationConcurrencyHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuditBestEffortHttpAutoTest.java:321-398,505-529`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationLeaseOverlapHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsSqlProbe.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsSqlBarrier.java`
- Modify after verification: `platform-support-service/README.md:328-388,430-545`
- Modify after verification: `agent-docs/CURRENT_IMPLEMENTATION.md:19-21,57,95-110`
- Verify unchanged: `agent-docs/README.md:20-30`（Foundation plan owns this routing entry；若缺失，停止本任务并先完成 Foundation Task 9）
- Preserve: both confirmed Specs and historical `agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md` facts

- [ ] 以随机 `redis.key-prefix` + SCAN证明所有业务 Key不含 loginIdentifier、canonical email、Vault token、密码、Refresh raw/lookup/HMAC、JWT或原始 Idempotency-Key；只清理本次 Namespace并保留相邻 Sentinel。
- [ ] 用 SlowWorkProbe/SQL Probe 冻结事件顺序 `redis acquire -> slow prepare(tx=false) -> transaction -> retained lock/CAS/DML -> commit/rollback -> redis release`；慢点期间不占 Hikari connection。
- [ ] 重跑 Lease-overlap矩阵：wrong-password count、Login vs change/disable、Change Password stale snapshot、double Refresh、Refresh vs Logout/change/disable、Create Account same email、Bootstrap handoff、management Target CAS、Gateway cross-root SERIALIZABLE。
- [ ] 重跑 Audit：ensureChain并发初始化只有一个 head；同日独立 writer sequence/hash连续；持有 Chain X时下一 writer等待；任何 append步骤失败整体回滚。不得用 Redis替代 Chain X证据。
- [ ] 运行 Platform聚焦与完整门禁：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -q -pl platform-support-test -am \
  -Dtest=OperationsAuthenticationConcurrencyHttpAutoTest,OperationsAuthenticationHttpAutoTest,OperationsDirectoryRefactoringHttpAutoTest,OperationsResourceLockFailureAutoTest,GatewayConfigurationManagementHttpAutoTest,GatewayConfigurationLeaseOverlapHttpAutoTest,OperationsAuditBestEffortHttpAutoTest,OperationsPersistenceContractAutoTest,GatewayConfigurationManagementPersistenceContractAutoTest,PlatformArchitectureAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q clean verify
git diff --check
```

- [ ] 运行 Foundation与根门禁：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -q clean verify
git diff --check

cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/verify-java-foundation.mjs
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
npm --prefix requirements-site test
git diff --check
```

- [ ] 只有全部真实验证完成后更新 README/台账：记录 Foundation本机可解析、Platform P0/P1 source、实际测试数/覆盖率、未发布/未部署；TEST-01只关闭已经真实修复的 Redis Testcontainers子偏差，不误关其他偏差。历史计划只保留后继路由，不改写当时数字。
- [ ] 最后扫描删除项和敏感绑定；第一、二条应无匹配，`FOR UPDATE/SHARE` 只剩本计划明确保留项：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
rg -n 'OperationsResourceLock|class Locks|lockTarget|lockRoute|lockInternalTenant|lockTenant|activeSessions|lockAccounts' \
  platform-support-service/platform-support-web/src/main
rg -n '@ResourceLock.*(loginIdentifier|password|refreshToken|idempotencyKey|request|body)' \
  platform-support-service/platform-support-web/src/main
rg -n 'FOR (UPDATE|SHARE)' \
  platform-support-service/platform-support-web/src/main/resources/mapper/OperationsIdentityMapper.xml \
  platform-support-service/platform-support-web/src/main/resources/mapper/OperationsDirectoryMapper.xml \
  platform-support-service/platform-support-web/src/main/resources/mapper/OperationsAuditMapper.xml \
  platform-support-service/platform-support-web/src/main/resources/mapper/GatewayConfigurationManagementMapper.xml
```

- [ ] 重新记录三个工作树状态，核对只有授权文件和执行前已有修改；交付时列出每条实际命令、exit code、测试数、覆盖率、Redis/MySQL前置、未运行项和未部署状态。

## Explicit P2 Non-goals

- 不以 Session CAS-only 取代 Refresh/Logout Session X；不设计 Auth Epoch新协议删除 Actor Account/Member 与 Role SHARE。
- 不推广到 CUSTOMER Member Key，不增加 Foundation V1以外的多锁、读写锁、续租、fencing或 Redis拓扑方案。
- 不新增 Gateway Catalog Revision替代 SERIALIZABLE；不重写 Audit为 CAS retry、分链、批量或 Redis锁。
- 不修改 Schema、PDManer、索引、初始化 DML、HTTP/OpenAPI、JWT Claim、Header、Gateway/BFF职责、Vault生命周期或其他核心系统。
