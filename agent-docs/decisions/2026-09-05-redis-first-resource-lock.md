# Redis-first 资源锁与 Foundation 公共能力决策

Decision status: Confirmed

Implementation status: Not started

Deployment status: Not deployed

Decision date: 2026-09-05

Supersedes / Superseded sections: 窄范围取代 [`DEVELOPMENT_CONVENTIONS.md` §5.1](../DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制) 中“不得新建通用锁框架”对已批准 Foundation 公共能力的禁止；业务服务自建同义锁框架、锁表或第二客户端仍然禁止。本文不改写历史 [`2026-09-04-platform-resource-lock-refactoring.md`](../plans/2026-09-04-platform-resource-lock-refactoring.md) 的当时范围和验证事实。

完整合同见 [`2026-09-05-foundation-resource-lock-design.md`](../../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)。当前实现、验证、制品发布和部署状态只由 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 与组件 README 记录。

## 背景

Code-MoR 已使用 Foundation `paytech-redis-starter` 提供的 Redisson 客户端做跨实例资源协调，但业务服务仍需自行实现取锁、等待、Lease、中断恢复、异常转换和 owner-safe 释放。Platform Support 当前的本地包装证明了这些机制具有稳定的跨服务基础设施属性，同时也暴露了三个不能由每个业务重复决定的问题：

1. 取锁必须发生在本地数据库事务之前，释放必须发生在提交或回滚之后；
2. Redis Lease 不是 fencing token，超时或进程暂停后可能出现两个执行者，因此数据库仍须保护最终不变量；
3. Key 解析、等待失败、中断、Redis 不可用和释放失败必须在所有服务中保持一致且不泄露敏感值。

继续让服务直接复制 Redisson 模板会形成多个互不一致的互斥域和错误语义。把所有并发正确性都交给 Redis 又会在 Lease 到期后失去最终保护。

## 决定

### 1. Redis-first，不是 Redis-only

需要跨实例串行化的同步资源命令，默认先取得 Redis 资源锁，再进入本地业务事务。Redis 锁用于提前协调、降低数据库冲突和给竞争请求有界反馈；获取失败或 Redis 不可用时 Fail-Closed，不执行受保护业务，也不静默退化成本机锁或另一套互斥机制。

Redis-first 不改变数据库的事实所有权，也不把锁变成永久所有权凭证。每个用例仍按其不变量保留必要的最终保护：

- 单行状态变化使用期望状态或 Version CAS；
- 业务唯一性使用数据库唯一索引并准确识别冲突；
- 跨行计数、授权上限、资金守恒和其他集合不变量使用局部数据库锁、哨兵行、条件更新或经批准的隔离级别；
- 幂等记录和事务回滚继续由本地数据库承担。

删除数据库锁、CAS 或唯一索引必须单独证明 Redis Lease 到期、进程暂停和并发重入后仍满足该不变量，不能因采用公共 Annotation 自动删除。

### 2. 标准公共 API

`paytech-redis-starter` 提供方法级 `@ResourceLock(prefix, key)`。标准 API 只声明：

- `prefix`：编译期固定的逻辑能力/资源类型前缀；
- `key`：可选的受限参数绑定表达式，非空时只允许 `#param` 或 `#param.field`；空值表示 `prefix` 本身就是唯一固定资源。

Annotation 不声明业务资源选择，不启动数据库事务，不执行授权、幂等、审计或数据库保护。业务方负责选择实际资源，并保证同一资源的不同入口产生同一逻辑 Key。

同一方法可以声明多个 `@ResourceLock`。实现必须在进入业务方法前解析全部 Key、去重并按完整逻辑 Key 的稳定字典序获取；失败时逆序释放已经取得的锁，正常完成时同样逆序释放。Annotation 声明顺序不得成为锁顺序。

### 3. 服务级等待和 Lease

Wait 与 Lease 是服务运行画像，不是每个方法的 Annotation 参数。启用资源锁的服务必须通过 `redis.coordination.resource-lock.wait-time` 和 `redis.coordination.resource-lock.lease-time` 显式配置有限 Duration；缺失、非法或 Annotation 存在但协调能力未启用时，应用启动失败。

服务必须根据其最长受保护调用和数据库事务 Timeout 评估 Lease，并测试 Lease 到期后的数据库最终保护。配置值不是 fencing 保证，也不得通过设置极长 Lease 代替数据库并发设计。

### 4. 受限表达式与启动校验

公共能力不接受完整 SpEL。禁止 Bean 引用、类型引用、构造器、方法调用、运算、拼接、索引、集合投影、导航链和任意对象 `toString()`。`#param.field` 只读取一层 Record Component 或 JavaBean 只读属性。

应用在对外提供 Readiness 前必须完成静态校验，包括 Annotation 所在方法可被 Spring 代理、参数名存在、表达式语法合法、参数/属性可解析、结果静态类型受支持、Prefix 与服务级配置合法。任何错误都 Fail-Fast，不延迟到第一笔请求。

运行期仍须在取得任何锁前验证所有求值结果非空、格式有界且可安全组成 Key。邮箱、Token、Secret、敏感明文和无界请求内容不得成为 Key；错误和日志不得回显求值结果。

### 5. AOP、事务与失败语义

`@ResourceLock` 标在 Spring 代理可达的公开同步用例入口。对于写命令，该入口包住完整 `TransactionTemplate`，保证顺序为：解析全部 Key、取得全部 Redis 锁、开始数据库事务、提交或回滚、逆序释放 Redis 锁。

标准失败语义为：

- 等待预算内未取得全部锁：`ConcurrentRequestException`；
- 等待被中断：恢复线程中断位并抛出 `ConcurrentRequestException`；
- Redis 连接、命令或协议失败：保留 Cause 的 `ServiceUnavailableException`；
- 业务方法异常：原样传播，不被释放异常覆盖；
- 释放时已不再持有或 Redis 不可用：记录固定低基数诊断并等待有限 Lease 回收，不伪造业务失败，不使用 `forceUnlock`。

Foundation 能力不依赖 Controller Audit、MVC、业务 Repository 或某个服务的错误枚举。Gateway WebFlux 的 Reactive Redis/Lua Guard 不使用该阻塞式 Annotation。

## 文档归属

- [`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 拥有 Foundation 提供的能力、依赖和允许/禁止边界；
- [`DEVELOPMENT_CONVENTIONS.md` §5.1](../DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制) 拥有锁选择、资源粒度和数据库最终保护规范；
- [`JAVA_PROJECT_STRUCTURE.md`](../JAVA_PROJECT_STRUCTURE.md) 拥有 Annotation 的包、代理入口和 Processor/事务位置；
- [`UNIT_TEST_GUIDE.md`](../UNIT_TEST_GUIDE.md) 拥有真实 Redis、并发、Lease 和失败测试方法；
- [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 只记录 Designed、Implemented、Verified、Deployed 和 Pending 状态。

根 `AGENTS.md`、文档导航和各服务 README 只做路由或记录本组件实际配置/验证，不复制上述规范。

## 取代与保留范围

本决定取代的是“禁止存在任何通用锁框架”这一过宽表述，并将标准排他资源锁的重复模板收敛到 Foundation。它不授权：

- 新建数据库锁表；
- 在业务服务中复制 Annotation、Aspect、锁执行器或 Redisson Client；
- 把 Redis 用作业务权威、幂等事实或数据库事务替代品；
- 自动删除现有数据库 `FOR UPDATE/FOR SHARE`、CAS、唯一索引或幂等记录；
- 把一个服务的账号级 Key 直接复制到不同 Tenant/Member 语义；
- 给 API Gateway WebFlux 引入阻塞 Redisson。

Platform 2026-09-04 资源锁计划中记录的资源粒度、逻辑 Key、500ms Wait、30s Lease、409/503、最后管理员保护、角色共享/排他数据库锁和验证结果继续作为该切片的历史证据，不是新迁移的现行 Key 权威。现行 Platform 资源与数据库锁矩阵由 [`2026-09-05-platform-support-resource-lock-optimization-design.md`](../../docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md) 拥有；Foundation 设计只拥有通用 Annotation 合同。前序计划的“不修改公共锁实现”只描述当次任务，并非永久禁止后继 Foundation 能力。

## 结果与后续

正向结果：

- 服务只声明资源，不再复制 Redisson 生命周期和异常模板；
- 启动期发现错误绑定，避免请求到达后才暴露未加锁路径；
- 多锁有全局确定顺序，降低交叉命令死锁风险；
- Redis 与数据库职责分离，Lease 失效不被误当成正确性证明。

代价与限制：

- Annotation 依赖 Spring AOP 代理，private/final/self-invocation 和异步/Reactive 返回不在支持范围；
- 受限表达式有意不支持完整 SpEL；复杂或运行中才发现资源的用例必须重新设计边界，不能扩张表达式语言；
- 每个消费者仍需完成真实 Redis 与本地数据库并发验证；公共能力通过不代表业务不变量已经通过。

截至本文确认时，Foundation 尚未实现 `@ResourceLock`，Platform 尚未迁移本地 `OperationsResourceLock`，没有新制品发布或环境部署，也没有以本决定名义完成新的 Maven/Redis/MySQL 验证。
