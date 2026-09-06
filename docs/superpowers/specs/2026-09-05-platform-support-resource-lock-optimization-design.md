# Platform Support 资源锁优化设计

日期：2026-09-05。

设计状态：正式设计已获用户确认；本文定义目标契约，尚不表示代码、测试、本地数据库或部署已经变更。实施和验证状态只由[当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)维护。

本文是[开发约定 5.1](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制)在 Platform Support 当前切片的具体化，并在以下窄范围取代[2026-09-04 Platform Operations 资源级锁重构](../../../agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md)的目标方案：

- 不再机械保留 Login、Change Password、Refresh 与 Bootstrap 的原有事务和数据库锁形态；
- 把现有手写 Redisson 执行器迁移为 Foundation 的 @ResourceLock；
- 按真实资源重新判断 Redis 协调、数据库最终保护、CAS 和唯一索引，各层不因历史实现而同时保留；
- 前序计划的历史实现与验证记录仍是历史证据，不被本文改写为新的完成状态。

[Platform Controller 注解式尽力审计设计](2026-09-05-platform-controller-best-effort-audit-design.md)继续拥有操作审计采集、分发和独立写入的契约。本文只决定审计链内部并发保护仍保留，不重新定义审计可用性或业务参与方式。

公共注解 API、服务级配置、受限表达式、Repeatable 多锁、AOP 生命周期和通用错误语义由[Foundation @ResourceLock 设计](2026-09-05-foundation-resource-lock-design.md)拥有，其确认来源为[Redis-first 资源锁决策](../../../agent-docs/decisions/2026-09-05-redis-first-resource-lock.md)。本文只定义 Platform 如何消费该能力；两份设计不得各自演化出不同注解。

## 1. 目标与范围

本次设计实现四个结果：

1. 在 Foundation paytech-redis-starter 提供职责单一的 @ResourceLock，使业务方法以声明式方式复用统一的 Redisson 获取、有限等待、Lease、异常映射和 owner-safe 释放语义。
2. Platform Support 删除 OperationsResourceLock 这类服务内手写锁执行器，把现有账号、角色、角色 Code、创建账号命令和 Gateway Catalog 协调迁移到 @ResourceLock；同一真实资源的不同入口继续共用一个逻辑 Key。
3. 认证链把 Vault、Argon2id、Token HMAC/签名等慢工作移出数据库事务和行锁持有期，并分别按安全的 Account 或 Session 资源协调；任何 Redis Key 和注解 Key 绑定均不得包含 loginIdentifier、邮箱、密码、Refresh Token、Vault Token 或原始幂等 Key。
4. 删除已由原子条件更新/CAS、唯一索引，或 Gateway Catalog Redis + SERIALIZABLE 共同覆盖的显式重数据库锁；保留对当前敏感跨行不变量仍不可替代的局部锁。

本次只覆盖 Foundation Redis 锁能力及 Platform Support 已有 Operations 认证、人员/角色管理、Gateway 配置和 Operations Audit 链。HTTP 路径、请求/响应字段、错误码体系、权限 Code、表、字段、索引、PDManer 模型和初始化数据不变；锁竞争和 Redis 不可用分别沿用已有 409/503 公共错误，不新增业务错误码。

本设计不是“Redis 替代 MySQL 锁”。Redis 负责正常情况下的跨实例资源协调和削峰；数据库条件更新、唯一索引与必要的局部锁负责 Redis Lease 到期、进程暂停、网络中断或双执行时的最终正确性。

## 2. 方案选择

| 方案 | 主要结果 | 结论 |
|---|---|---|
| 保留全部数据库锁，只把手写 Redisson 包成注解 | 代码样板减少，但账号慢事务、Gateway 重复串行和锁竞争不变 | 不采用 |
| 只使用 Redis 锁，删除所有 FOR UPDATE/FOR SHARE | 正常路径简单，但 Lease 到期后 Session、授权集合、首次交接和审计链可能双写或写偏 | 不采用 |
| 实际资源 @ResourceLock + 短事务 + CAS/唯一键 + 必要局部数据库锁 | 每一层只保护明确的不变量，允许删掉重复协调，同时在 Redis 失效边界保持最终正确 | 采用 |
| 本次同时引入 fencing token、业务自定义多锁框架、Gateway Catalog Revision 或审计分片 | Foundation 已为通用 Repeatable Annotation 定义确定排序；其他扩展需要新状态、Schema 或更大验证面 | 列为 P2 非目标 |

选择第三种方案后，评审每个锁必须依次回答：

1. 违反时会破坏哪条业务不变量；
2. 冲突的实际资源是什么；
3. 健康 Redis 下是否需要跨实例协调；
4. Redis Lease 失效后由哪一个数据库原语最终仲裁；
5. 如果删除显式数据库锁，哪条条件 DML、CAS、唯一索引或隔离级别接管；
6. 哪个真实 MySQL/Redis 并发测试能证明上述边界，而不是只证明代码调用过某个方法。

## 3. Platform 消费 @ResourceLock 的合同

### 3.1 公共 API 与配置归属

@ResourceLock 的注解、Repeatable 容器、切面、服务级配置和启动门禁只在 paytech-foundation/paytech-redis-starter 实现。Foundation 不包含 operations、account、role、session 或 gateway-configuration 等 Platform 业务 Key，也不依赖 Platform DTO。

Platform 使用已经确认的公共形态：

    @ResourceLock(prefix = "operations:account", key = "#targetAccountId")

锁定 Gateway Catalog 这类固定资源时省略 `key`：

    @ResourceLock(prefix = "gateway-configuration:catalog")

Annotation 只声明 prefix 和 key；Wait 与 Lease 不写在每个方法上，而由 Platform 的服务运行画像显式配置：

    redis.coordination.resource-lock.wait-time = 500ms
    redis.coordination.resource-lock.lease-time = 30s

本切片保持已验证的 500ms/30s，不增加本地默认值或第二套配置。@ResourceLock 固定失败关闭。Foundation 虽支持 Repeatable 多锁和确定排序，但第 4 节的 Platform 用例每次只需要一个真实 Redis 资源；不得为了复刻旧嵌套包装而声明冗余多锁。

### 3.2 Key 绑定与敏感信息

Foundation key 只接受完整的 #param 或 #param.field，不是完整 SpEL；禁止拼接、Bean/类型引用、方法调用、索引、集合投影、导航链和任意对象 toString。Prefix 固定在 Annotation，求值结果按 Foundation 的字符、类型和长度合同校验，物理 Key 由现有 Redisson NameMapper 自动增加服务 Namespace。

Platform 进一步约束：

- Account ID、Session ID、Role ID 这类已验证稳定 ID 直接绑定命名标量参数；
- Role Code 只有经过 API 边界限制且确认是公开、非敏感、有限业务 Code 时，才可绑定一层只读属性；迁移实现仍优先传入规范化安全标量；
- Login、Refresh、Change Password 和人员创建不得把认证 Request/Command 的任一属性直接作为注解 key；
- 复合 Key 段由业务侧先规范化并组成一个安全、有限标量，再传给代理方法；Annotation 不做字符串拼接。

尤其禁止以下绑定及其变体：

- #request.loginIdentifier；
- #request.password、#request.currentPassword、#request.newPassword；
- #request.refreshToken 或 Refresh Token 的公开查找段；
- #request.email、Vault Token、Blind Index；
- 原始 Idempotency-Key；
- 整个 request、command、account、session 对象。

需要规范化、解析数据库事实或计算键控摘要时，必须先在未开启数据库写事务的阶段得到安全标量，再通过另一个 Spring 代理 Bean 的 public 注解方法传入。不能为图方便扩张 Foundation 表达式语言，也不能把 loginIdentifier 明文或普通 SHA-256 摘要包装进 #param 来绕过禁止项。

### 3.3 获取、执行与释放

通用运行流程以 Foundation 设计为准：启动期编译并验证元数据；调用时先解析全部 Key、去重和排序，再用单个服务级 Wait 总预算取得锁；业务只在全部锁明确取得后执行一次；内部 TransactionTemplate 提交/回滚后逆序 owner-safe 释放。

未取得锁映射为 ConcurrentRequestException。等待被中断时恢复线程中断位并保留 Cause；Redisson 包装 InterruptedException 时同样处理。Redis 连接、命令或未知获取结果失败映射为 ServiceUnavailableException，目标方法不得执行。

Lease 已自然到期时旧线程不得释放新 Owner。释放阶段故障记录安全诊断，但不能覆盖已经提交的业务结果或原始业务异常。切面不重试业务、不跨线程持锁，也不支持异步/Reactive 返回。

### 3.4 代理与事务顺序

注解只能标在由另一个 Bean 经 Spring 代理调用的 public、非 final 同步方法上。private 方法、类内自调用和实现类绕过代理均不算已加锁。

Platform 的标准顺序是：

    Controller
      -> Processor / 专用协调组件的代理方法（获取 @ResourceLock）
        -> 事务外准备
        -> OperationsTransactionExecutor.write / serializableWrite
          -> 短数据库事务
        -> 释放 @ResourceLock

可直接安全确定 Key 的命令使用公开 Processor 入口。Login/Refresh 等需要先查库或做键控摘要的命令，由 Processor 先解析，再调用职责明确的公开协调组件；该组件接收安全 lock key 与类型化命令，不能把 Request 交给注解表达式。

不得把 @ResourceLock 放在 Controller、Repository、Mapper、TransactionTemplate 回调或使用 @Transactional 的同一方法上。存在 Annotation 却未启用协调 Starter、未配置 Wait/Lease、缺少 RedissonClient 或方法不可代理时必须启动失败，不能静默无锁运行。

## 4. Platform 逻辑资源与 Key

| 用例 | 实际资源 | 逻辑 Key | 说明 |
|---|---|---|---|
| Login，已解析账号 | 全局 Account | operations:account:{accountId} | 与改密、账号状态变更共用 |
| Login，未知账号 | 同一未知登录标识的尝试桶 | operations:login-attempt:{hmacKeyVersion}:{keyedDigest} | keyedDigest 使用现有认证 HMAC 的独立 purpose；不使用普通 SHA-256 |
| Change Password | 全局 Account | operations:account:{accountId} | 不按 Session 或 Tenant 建第二互斥域 |
| Refresh / Logout，已解析 Session | Session | operations:session:{sessionId} | 原始 Refresh Token 永不进入 Key |
| Refresh，无法解析 Session | 同一未知 Refresh 尝试桶 | operations:refresh-attempt:{hmacKeyVersion}:{keyedDigest} | 只形成安全拒绝协调，不获得业务权限 |
| Create First Administrator | Bootstrap Account | operations:account:{bootstrapAccountId} | 与该账号改密共用；数据库仍有首次交接最终仲裁 |
| Disable / Enable / Delete Account、Assign Roles | 目标 Account | operations:account:{accountId} | 当前固定 INTERNAL Tenant 下，一个 Account 唯一映射一个成员 |
| Create Account | 创建命令 | operations:create-account:{actorId}:{keyedCommandDigest} | 原始 Idempotency-Key 和 Email 均不入 Key；唯一邮箱索引最终防重 |
| Update / Delete Role | Role | operations:role:{roleId} | 分配入口的数据库 Role 共享保护与它配合 |
| Create Role | 规范 Role Code | operations:role-code:{normalizedCode} | Code 是批准的非敏感业务标识，唯一索引最终防重 |
| Change Gateway Target / Route Status | 全局 Gateway Catalog | gateway-configuration:catalog | 两种入口共用，不再叠加 Target/Route Redis Key |

keyedDigest 必须使用域分隔 purpose，例如 resource-lock:login-identifier、resource-lock:refresh-attempt 和 resource-lock:idempotency，并携带 HMAC Key Version。密码学端口必须用一次调用返回由同一密钥快照产生的 `keyVersion + digest`，禁止先读取当前版本、再独立请求 HMAC 后自行拼接；否则轮换交错会生成错误版本的互斥域。摘要只用于短期协调，不成为身份查询、业务唯一性、审计事实或持久化 Token。HMAC 轮换期间旧、新版本本身仍可能形成两个短暂互斥域，因此最终正确性必须落到数据库条件更新和唯一索引。

本文对 Platform 的具体 Key 选择优先于 2026-09-04 历史计划及本次 Decision 中所述的历史基线；Foundation 设计只拥有通用 Annotation 合同，不拥有 Platform Key。Account/Role/Role Code 和 Gateway Catalog 的逻辑域保持不变；Create Account 的原始 commandKey 改为 keyedCommandDigest，Gateway Target/Route 的内层 Key 删除。若目标环境存在旧、新实例并行，必须采用单版本切换或经证明的兼容阶段，不能让原始/摘要 Key 或单层/双层 Gateway 锁形成互不相见的互斥域。

同一个已知 loginIdentifier 与未知 loginIdentifier 都会经过确定性的安全资源协调；Redis 竞争或不可用不能通过“已知账号返回 409/503、未知账号直接返回认证失败”形成账号存在性旁路。认证响应、耗时平衡和安全日志继续使用统一外显语义。

当前 Operations 只有一个固定 INTERNAL Tenant，可沿用 Account 到 Member 的唯一映射。未来 CUSTOMER 多租户成员授权必须改用 tenantId + memberId，不能直接复制本切片 Account Key；该扩展是 P2。

## 5. 认证链：慢工作与短事务

### 5.1 共同阶段

Login、Refresh、Logout、Change Password、Create First Administrator，以及人员创建时的初始凭据准备，统一遵循以下阶段：

1. 解析：在数据库写事务外规范化输入、执行必要的普通查询，得到 Account/Session 等安全资源 ID；未知输入得到键控摘要 fallback，而不是原文 Key。Login 为解析账号而必需的幂等 Vault Protect 允许发生在这一步、即 Redis 锁之前，但绝不进入数据库事务。
2. 协调：调用独立 Spring 代理的 @ResourceLock public 方法。资源能够在 Processor 入口直接安全确定时可标注 Processor；需要查询或摘要后才能确定时使用职责明确的专用协调组件，禁止 self-invocation。
3. 准备：持有 Redis 锁、但尚未开启数据库写事务时完成 Vault Protect/Reveal、Argon2id matches/encode、Token HMAC/签名、命令幂等 HMAC/Canonical JSON 及不可变命令材料构造。
4. 提交：开启短 TransactionTemplate，重新读取或锁定本文指定的最小数据库资源，并用条件 DML、CAS、唯一索引校验准备阶段的版本/状态；不匹配就回滚并返回现有安全错误或并发冲突。
5. 返回：只在事务确认提交后返回已准备 Token/结果；未使用的签名材料直接丢弃，不能在事务失败后外显。

Redis 锁覆盖准备和提交两个阶段，数据库锁只覆盖提交阶段。Redis Lease 到期后的重叠执行由第 4 步的最终仲裁处理，因此任何安全写入都不能假设“拿到 Redis 锁就仍然拥有资源”。

Vault Protect 若会创建或复用稳定 Token，必须保持幂等；事务最终失败时不得假装能回滚外部 Vault。Platform 数据库不会留下半条引用，未被引用 Token 的生命周期仍由 Vault 权威管理，本次不新增跨系统补偿。

Operations/Gateway 命令幂等也遵循同一外部依赖边界：在 `@ResourceLock` 内、`TransactionTemplate` 前一次性校验 Idempotency-Key，并把 caller scope、request hash 和审计用 opaque reference 计算成不可变 `PreparedCommandIdempotency`。事务内的 replay/complete 只读写数据库并消费该准备结果，不得再次调用密码学端口、重复序列化请求或读取当前 HMAC Key Version；KMS/HMAC 不可用必须在数据库事务和行锁开始前以 503 失败关闭。

当前开发适配器只使用一个固定 HMAC Key Version；它不能证明持久化命令幂等在生产密钥轮换期间仍连续有效。现有命令记录按本次密钥计算的 caller scope 和 request hash 查询，但没有持久化可供旧、新密钥兼容查找的 HMAC Key Version。接入可轮换 KMS 前必须另行批准并实现明确方案，例如持久化版本并进行受限多版本查找/迁移，或使用生命周期独立且稳定的幂等派生密钥；还必须验证同一 Idempotency-Key 在轮换前后仍回放原结果、不同请求仍被拒绝。本轮不修改 Schema，也不得把固定本地 Key 的测试结果外推为已经支持生产轮换。

### 5.2 Login

Login 先在事务外规范化 loginIdentifier、取得稳定邮箱查询 Token 并查找账号。找到账号后锁 Account；标识格式错误或确认未找到账号时，以现有认证 HMAC 对规范化的安全输入计算未知尝试桶。Vault/HMAC 依赖不可用时直接按 503 失败关闭，不把依赖故障伪装成 UNKNOWN_ACCOUNT。能够进入认证判断的分支都先协调再做真实或等价的 Argon2id 工作，不能把 loginIdentifier、邮箱 Token 或完整 LoginCommand 交给注解 Key 绑定。

密码验证、账号禁用/锁定时的等价 Argon2id、Token HMAC 和签名均不在数据库事务内。成功或失败写事务只执行必要的重读与 DML：

- 密码错误计数使用原子条件更新，基于数据库当前 failures 递增并计算 locked_until；条件至少约束账号仍是原 credentialVersion 和可计数状态。它不能使用会在 Lease 到期双执行时丢掉其中一次失败的“先读 failures，再无条件写回”。
- 正确密码的失败计数重置和 Session/Refresh 创建必须在一个事务中；账号状态、credentialVersion、authEpoch、密码状态及 Member/Tenant 资格均按准备快照重新校验。
- 账号成功路径使用 expected version/credentialVersion 的条件更新；受并发改密、禁用或版本变化影响时不得创建可用 Session。
- Session、Refresh 的唯一索引及受影响行数继续作为最终约束；签名成功但事务失败的 Token 永不返回。

删除 Login 的 Account FOR UPDATE 和 Tenant FOR SHARE。数据库 UPDATE 自身仍会取得必要行锁；失败计数原子更新、成功 CAS 和唯一索引接管最终正确性。

### 5.3 Change Password 与人员凭据创建

Change Password 锁 Account。在写事务外完成：

- 当前 Session 的普通读取和安全资源解析；
- 当前密码 Argon2id 验证；
- Human Account 邮箱 Vault Reveal；
- 新密码策略检查、与旧密码比较和新 Argon2id 编码。

短事务重新校验 Account、Password、当前 Session 的 credentialVersion、authEpoch 和状态。Account 版本/credentialVersion 条件推进、Password 条件替换与 Session/Refresh 批量撤销必须原子完成，任一受影响行不符合预期则整笔回滚。

删除 Change Password 的 Account FOR UPDATE、Tenant FOR SHARE，以及“先 SELECT 所有 ACTIVE Session FOR UPDATE、再逐条撤销”的形态。改为有状态条件的集合 UPDATE 撤销 Session 和 Refresh；UPDATE 获得的实际行锁足够保护被修改行。它与并发 Refresh 的 Session X 在数据库内串行，最终不得存在新凭据提交后仍可使用的旧 credentialVersion Session。

Operations 创建人员与首次管理员的邮箱 Vault Protect、初始密码 Argon2id 编码也在写事务外准备。唯一邮箱索引、命令幂等记录和短事务继续最终仲裁；不同命令 Key 创建同一邮箱不能只依赖 Redis。

### 5.4 Refresh 与 Logout

Refresh 在事务外解析 Token 结构和公开查找记录，得到 Session ID 后锁 Session；原始 Token、公开 lookupId 和保存的 secret HMAC 均不入 Redis Key。无法解析 Session 时只使用键控摘要 fallback 完成统一拒绝。

新一代 Token 的 HMAC/签名在数据库写事务和 Session X 之外准备。短事务内保留 Session SELECT FOR UPDATE，并重新读取 Refresh、Account、Password、Member/Tenant 当前状态：

- 已消费 Refresh 的重用检测与 Session/Refresh 撤销在同一短事务提交；
- 当前 Refresh 消费必须是带状态、代次和受影响行数的条件更新；
- Session 代次推进必须约束 expected generation、ACTIVE 状态及准备阶段冻结的 authEpoch/credentialVersion；
- 新 Refresh 插入、旧 Refresh 关联和 Session 推进任一步失败都回滚，已签名 Token 不返回。

删除 Refresh 的 Account FOR UPDATE 和 Tenant FOR SHARE。Session X 是同一 Session 代次单写、Refresh 重用撤销与 Logout 竞争的数据库最终保护，本次不删除。

Logout 直接按安全 Session ID 协调，原始 Refresh Token 的 HMAC 校验在事务外准备；短事务保留 Session X，重读并验证 Session 所有权和 Refresh 归属后，原子撤销 Session 与其 Refresh。删除 Logout 的 Account FOR UPDATE 和 Tenant FOR SHARE。

不同 Session 即使属于同一 Account，正常 Refresh/Logout 不因 Tenant 或 Account 大锁被串行。账号禁用或改密的集合撤销与单 Session X 通过真实 UPDATE 行锁交汇；固定锁顺序和受影响行数校验必须避免把 Redis 当作二者共同互斥域。

### 5.5 Bootstrap

Create First Administrator 使用 Bootstrap Account 的 operations:account:{bootstrapAccountId}，与该账号改密共用资源锁。邮箱 Protect 和初始密码 Argon2id 编码在事务外准备。

短事务保留 Bootstrap Account FOR UPDATE 作为首次交接的唯一数据库最终仲裁。取得该锁后必须重新校验：

- Bootstrap Account 仍为批准类型和状态，初始密码已按既有规则变更；
- INTERNAL Tenant 和 PLATFORM_ADMIN 固定目录仍有效；
- Human Account 数量仍为零；
- 创建 Human Account/Member/Role binding、停用 Bootstrap Account、撤销 Bootstrap Password 和 Session 全部原子完成。

删除 INTERNAL Tenant FOR UPDATE。它与 Bootstrap Account X 同时存在只是为同一个首次交接做重复串行；Bootstrap Account 是稳定、唯一且被交接动作本身修改的更小资源。即使 Redis Lease 到期或 HMAC Key 轮换导致两个执行重叠，第二个事务也必须在取得 Bootstrap Account X 后看到终态并失败，不能创建第二个首次管理员。

## 6. 人员、角色与授权锁

### 6.1 保留原因

Operations 管理继续以 Account、Role 或 Role Code 的 @ResourceLock 处理正常跨实例竞争。数据库内保留以下敏感授权保护：

- Actor Account FOR SHARE：保证授权判断到业务写入期间，操作者账号不会被并发停用或删除；
- Actor 的当前 INTERNAL Member FOR SHARE：保证权限检查依赖的 Actor→Member→Role 授权关系在业务提交前不会被另一个角色分配命令推进 Member Version 并撤销；它只锁当前操作者的一行 Member，不恢复 Tenant 或 Target Account 大锁；
- Actor Account/Member 保护取得后，从数据库读取一次 `UTC_TIMESTAMP(6)` 作为本命令的 `effectiveAt`。Actor 当前 Role、权限、可授予权限、目标是否贡献有效管理员及有效管理员总数必须全部绑定这个时点；授权查询之间刚到生效时间的未来 Role 不得在命令中途取得权限，也不得让同一命令混用多个数据库“现在”；
- Actor 在 `effectiveAt` 有效的 Role、命令请求的新 Role，以及 Enable 将恢复的目标现有 ACTIVE Role 使用 FOR SHARE：保证实际参与授权判断或即将恢复的授权集合不会在检查后被并发降级或删除；Disable、Delete 与 Assign Roles 不依赖即将移除的普通自定义 Role 定义，不得为这些命令无条件锁住目标全部旧 Role；
- 被编辑/删除 Role 的 FOR UPDATE，以及角色分配读取该 Role 时的 FOR SHARE：保证 Role 状态、权限集合与成员绑定不会穿插成非法组合；
- 会撤销目标当前或未来 ACTIVE `PLATFORM_ADMIN` Binding 的命令使用 PLATFORM_ADMIN Role FOR UPDATE：判断绑定时忽略 `effective_from`，但要求 `effective_to` 为空或晚于 `effectiveAt`，并严格验证 Operations Human Account、INTERNAL Tenant、ACTIVE Member 及内置平台管理员 Role 元组；请求明确保留该 Role 时不升级为排他锁。这样两个不同目标的命令也会串行进入同一个管理员集合保护，再按同一 `effectiveAt` 重算当前有效管理员，维护“至少一个有效管理员”不变量。

这些锁不是为了审计，也不是因为所有操作都属于同一 Tenant。只有命令实际读取、恢复相应授权集合或改变相应 Role 时才取得；多个 Role 先汇总最强锁模式，再按稳定 ID 顺序锁定，避免同一 Role 先共享后升级。Disable、Delete 与 Assign Roles 若会撤销当前或未来 ACTIVE PLATFORM_ADMIN，仍必须把该 Role 升为排他锁以保护管理员集合，但不因此共享锁定其余待移除 Role。

### 6.2 删除与替代

Disable、Enable、Delete Account 和 Assign Roles 删除 Target Account FOR UPDATE。目标 Account 已由同一 operations:account:{accountId} 协调，数据库端的 expectedVersion/状态条件更新、Member 条件更新、唯一索引与受影响行数负责 Lease 到期后的最终冲突。

删除 Target Account X 不等于取消事务，也不删除 Actor Account/Member、Role 授权锁或管理员集合保护。Account UPDATE 仍取得目标行 X；如果命令需要先读取目标快照，提交时必须以 expectedVersion 和预期状态重新验证，不能只靠事务开始时的普通 SELECT。授权、可授予上限和最后管理员判断复用 Actor 锁定后冻结的数据库 `effectiveAt`。Enable Account 还必须把授权上限校验时读取的 Member Version 带入最终条件 DML；这样 Lease 到期后若 Assign Roles 已先推进 Member，Enable 明确并发失败，若 Enable 先完成后再 Assign，则形成合法串行顺序。

Create Account 继续按 actorId + keyedCommandDigest 协调命令重放，但同邮箱不同命令由唯一邮箱索引最终仲裁；Create Role 同理由唯一 scope/code 索引最终仲裁。只有明确命中批准唯一索引且能重读到相同业务事实时才转换为既有业务冲突，未知主键或数据库异常不能吞掉。

## 7. Gateway 配置：从五层缩为三层

当前 Target/Route 状态命令存在五种协调：Catalog Redis、Target/Route Redis、SERIALIZABLE、Root SELECT FOR UPDATE 和 Root Version CAS。目标方案只保留三种职责不同的保护：

| 层 | 保留/删除 | 唯一职责 |
|---|---|---|
| gateway-configuration:catalog @ResourceLock | 保留 | 健康 Redis 下把所有会改变全局候选 Catalog 的命令正常串行 |
| Target/Route 独立 Redis 锁 | 删除 | 已被外层 Catalog 锁完全覆盖，不能提供额外正确性 |
| SERIALIZABLE TransactionTemplate | 保留 | Redis Lease 到期后，阻止两个不同 Root 的事务各自验证旧 Catalog 后形成跨行写偏 |
| Target/Route Root SELECT FOR UPDATE | 删除 | 同 Root 冲突已有 Version CAS，跨 Root 冲突由 SERIALIZABLE 处理；提前 X 只增加锁升级和等待 |
| Root expectedVersion CAS | 保留 | 精确拒绝同一 Target/Route 的陈旧客户端版本，并验证受影响行数 |

Catalog Redis、SERIALIZABLE 和 Version CAS 不是重复：

- Redis 锁优化正常竞争，不是永久所有权；
- SERIALIZABLE 保护全 Catalog 的跨 Root 候选不变量；
- CAS 保护调用方声明的单 Root 版本契约。

Root 的普通读取在 MySQL SERIALIZABLE 下仍可能取得共享/间隙保护，UPDATE 也会取得行 X；本文只删除显式、提前的 Root FOR UPDATE，不声称 Gateway 已无数据库锁。

命令继续在同一 SERIALIZABLE 事务读取完整候选 Catalog、编译并验证唯一 Route/Target、协议边界和引用有效性，再执行 Root CAS。捕获 MySQL 序列化失败或死锁时保留 Cause 并映射为现有 ConcurrentRequestException；不得在失败后绕开 Catalog 锁或降级为普通隔离级别重试。

## 8. Audit 链与锁边界

Operations Audit 每个 UTC 日链的 sequenceNo、previousEventHash、eventHash 和 Chain Head 构成单前驱线性结构。同一链上的两个追加者不能同时认领相同前驱或序号。

本次完整保留：

- ensureChain 的并发安全初始化；
- Audit Chain Head SELECT FOR UPDATE；
- Event、Attributes、关联 Security Event 与 Chain Head CAS 在一个独立审计事务内提交；
- 失败时整条回滚，下一条从最后已提交 Chain Head 继续。

不把 Audit Chain X 替换成 Redis。审计写入已经是独立尽力事务，Redis Lease 不能证明链前驱唯一；在没有新的数据库追加算法前，Chain X 是必要的最终仲裁。

ensureChain 的 INSERT ... ON DUPLICATE KEY UPDATE 与后续 FOR UPDATE 在物理上可能对已有行重复触锁，但本次不重写该协议。将初始化和锁头合成一个 Repository 原语、CAS-only 重试、分链或批量追加均属于 P2，不能在本轮顺手删除显式 Chain X。

## 9. 数据库锁决策矩阵

| 场景 | 当前显式锁 | 目标 | 删除后的最终保护 / 保留理由 |
|---|---|---|---|
| Login | Account X、Tenant SHARE | 删除两者 | Account Redis；错误计数原子条件更新；成功 CAS；Session/Refresh 唯一键 |
| Change Password | Account X、Tenant SHARE、ACTIVE Sessions X | 删除三种显式查询锁 | Account Redis；Account/Password 条件更新；Session/Refresh 集合 UPDATE 的实际行锁 |
| Refresh | Account X、Tenant SHARE、Session X | 仅保留 Session X | Session Redis；Refresh consume CAS；Session generation 条件推进；Session X 处理重用/Logout |
| Logout | Account X、Tenant SHARE、Session X | 仅保留 Session X | Session Redis；Session X 后重读归属并撤销 |
| Create First Administrator | Bootstrap Account X、Internal Tenant X | 保留 Account X，删除 Tenant X | Account Redis；Bootstrap Account X 串行首次交接；唯一键与终态重读 |
| Account 管理 | Actor Account SHARE、Target X、相关 Role SHARE/X | 删除 Target X；保留 Actor Account SHARE，并补齐 Actor Member SHARE | Target Account Redis + Account/Member expectedVersion 条件 DML；Actor Account/Member 与 Role 锁保护敏感授权 |
| 最后管理员变更 | PLATFORM_ADMIN Role X | 保留 | 跨多账号/成员/绑定的集合不变量无法由单 Target CAS 覆盖 |
| Role 更新/删除与分配 | Role X / SHARE | 保留 | 防止权限集合、Role 状态和成员绑定交叉提交 |
| Gateway Target/Route | Root X | 删除 | Catalog Redis + SERIALIZABLE + Root version CAS |
| Audit 追加 | Chain Head X | 保留 | 单前驱 Hash 链的序号与前驱唯一性 |

所有“删除”都以对应条件 SQL、受影响行数检查和真实并发测试同时落地为前提。不能先删锁、保留无条件 UPDATE，再声称 Redis 已覆盖；也不能因为保留了 TransactionTemplate 就把普通 SELECT 描述成最终 CAS。

## 10. 锁顺序与失败关闭

每个命令最多取得一个 Redis 资源锁；本轮不引入 Redis 多锁。Redis 必须先于数据库事务取得，并在提交/回滚之后释放。

数据库内按场景固定：

- Refresh/Logout：Session X 后重读和条件写 Refresh、Account、Password、Member/Tenant；不再先锁 Account/Tenant；
- 管理命令：Actor Account SHARE、Actor 当前 INTERNAL Member SHARE，再按稳定 ID 顺序取得授权相关 Role SHARE/X，必要时取得 PLATFORM_ADMIN Role X，最后执行 Target Account/Member 条件 DML；
- Bootstrap：Bootstrap Account X 后检查 Tenant、Human Account Count 和固定 Role，再完成全部交接写入；
- Gateway：完整 Catalog SERIALIZABLE 读取/验证后执行 Root CAS，不显式提前锁 Root；
- Audit：只在独立审计事务锁目标 Chain Head，不与业务 Account/Role/Session 锁组成一条跨事务锁序。

Redis 获取失败、超时、中断或状态未知一律不开始业务事务。数据库 CAS、唯一约束、锁等待、死锁或序列化失败一律回滚，不在部分节点静默跳过 Redis 或切到另一套互斥域。

管理命令允许两个不同目标资源并行，因此 `A` 操作 `B`、`B` 操作 `A` 时，双方持有各自 Actor Account/Member 共享锁后再修改对方 Target，MySQL 仍可能检测到合法的共享到排他等待环。这里不为消除低概率死锁而恢复所有 Target 的提前排他锁；Spring 数据访问层报告的死锁、锁等待超时和同类 `ConcurrencyFailureException` 必须保留 Cause、回滚并统一映射为 `ConcurrentRequestException`（HTTP 409），由持有幂等键和最新 Version 的调用方决定是否重试，不能外显 500 或在事务内自动重放。

明确取得 Redis 锁后也不承诺一直持有。测试强制 Lease 到期时，业务仍必须依靠数据库最终保护得到一个合法终态；“提交前检查 isHeldByCurrentThread”只能用于诊断，不能替代条件 SQL。

## 11. 分阶段实施边界

### 11.1 P0：公共能力与现有手写锁迁移

- 按 Foundation 权威设计在 paytech-redis-starter 实现并验证 @ResourceLock(prefix, key)、服务级 Wait/Lease、受限 Key 绑定、启动门禁和统一失败/释放语义；
- 将 Operations Management 的 Account、Role、Role Code、Create Account 锁迁移到注解；
- 以 Target expectedVersion/CAS 和已保留的 Actor Account/Member、Role、最后管理员保护接管最终正确性后，删除 Management Target Account 的提前排他读；
- 将 Gateway 两层手写 Redis 锁收敛成一个 Catalog 注解锁；
- 删除 Platform 的 OperationsResourceLock 手写执行器；旧 Locks 若只剩字符串拼接职责也删除，必要的安全摘要/规范化留在有业务语义的解析组件；
- Gateway 删除 Target/Route 独立 Redis 锁和 Root FOR UPDATE，保留 SERIALIZABLE 与 Version CAS；
- 先保留现有对外行为并跑真实 Redis/MySQL 回归，再进入认证事务缩短。

### 11.2 P1：认证慢工作与数据库锁优化

- 为 Login/Refresh 的安全资源解析和代理调用建立明确协调边界；
- 将 Vault、Argon2id、Token HMAC/签名和人员初始凭据准备移出写事务；
- 为 Login 失败计数、成功 Session 创建、Change Password、Refresh generation 和批量撤销补齐条件 SQL 及受影响行数验证；
- 按第 9 节删除认证 Account/Tenant/ACTIVE Session 和 Bootstrap Internal Tenant 的显式重锁；Management Target 已在 P0 与其 CAS 并发验证同步删除；
- 保留 Session X、Actor Account/Member 与 Role 授权锁、PLATFORM_ADMIN Role X、Bootstrap Account X 和 Audit Chain X；
- 删除旧锁路径后运行持久化 SQL 合同、架构门禁和真实 HTTP 并发回归。

P0/P1 是实施依赖顺序，不是完成声明。实现可以按最小红灯测试拆分提交候选，但未经用户另行授权不得创建分支、提交、推送、部署或修改共享基础设施。

### 11.3 P2：明确非目标

本次不做：

- 用 Session CAS-only 状态机取代 Refresh/Logout 的 Session X；
- 引入 Account/Auth Epoch 的新授权快照协议以删除 Actor Account/Member 与 Role SHARE；
- 把 Operations 固定单 Tenant 的 Account Key 推广成 CUSTOMER 多租户 Member Key；
- Platform 新增需要多个 Redis 资源锁的业务用例，以及 Foundation 设计以外的读写锁、自动续租、fencing token、RedLock、Sentinel/Cluster/Master-Replica 扩展；
- Gateway Catalog Revision 表、全局版本 CAS，或用它取代 SERIALIZABLE；
- Audit CAS-only 追加、重试队列、分链、分片、批量、Redis Audit 锁或 Chain 初始化协议合并；
- 新建通用数据库锁表、幂等表或认证状态表；
- 修改 PDManer、Schema、索引、初始化 DML、HTTP/OpenAPI、JWT Claim、Header 或 Gateway/BFF 责任；
- 自动 Vault 孤儿清理、Retention/Legal Hold、跨系统补偿或 KMS/Vault 生产接入；
- 可轮换生产 KMS 下持久化命令幂等的 Key Version 兼容、历史摘要查找或迁移；
- 把普通 Context、列表或详情查询加 Redis/排他锁；
- 推广到其他五个核心系统，或声称主工作区实现等于生产部署。

## 12. TDD 与验收矩阵

测试遵循[单元测试指南](../../../agent-docs/UNIT_TEST_GUIDE.md)：从真实 HTTP Controller 进入生产 Processor/协调组件、Domain Service、Repository/Mapper，复用仓库 local-environment 的真实 MySQL 与 Redis；不使用 H2、Testcontainers、@MockBean、Mockito 方法 Stub 或固定 sleep 证明锁行为。

### 12.1 Foundation 前置门禁

Foundation 的完整自动测试矩阵由其[权威设计](2026-09-05-foundation-resource-lock-design.md#13-测试与验收)拥有，本切片不能复制或削弱。Platform 迁移开始前至少必须取得以下可消费证据：

1. 真实 Redis 下同 Key 互斥、不同 Key 并行、Repeatable 多锁确定排序和单个 Wait 总预算均通过。
2. 锁在 TransactionTemplate 之前取得、在真实提交和回滚后释放；成功获取时目标方法恰好执行一次。
3. Platform 服务级 wait-time = 500ms、lease-time = 30s 被准确绑定，不启用无界 Watchdog。
4. 强制 Lease 到期并由第二线程取得同 Key 后，旧线程结束不能 unlock 新 Owner。
5. 中断恢复、Redis 不可用、未知获取结果、非法表达式/值、缺少配置和不可代理方法均按 Foundation 合同失败关闭。
6. 业务结果与原异常不被释放故障覆盖；Namespace 只增加一次，日志、Trace、Metric Label 和异常不包含动态 Key 或参数。

### 12.2 认证 HTTP 并发测试

1. 在真实 Vault、Argon2id、HMAC/签名端口的目标调用处分别设置“先记录事务/连接状态、再继续真实 `proceed()`”的观察屏障，覆盖 Login、Refresh、Change Password 与人员凭据准备；不得只在外层 `prepare*` 返回后观察。另一个 MySQL 连接在每个慢点期间可立即取得原 Account/Tenant 锁，证明慢工作不在写事务和行锁内。
2. 并发相同 Login 的错误密码，在强制 Redis Lease 到期后仍累计每一次已受理失败并准确触发第 5 次锁定；不存在读改写丢失。
3. 正确 Login 与 Change Password/Disable Account 竞争；只有通过最终版本校验的事务能创建 Session，获胜的改密/禁用提交后不存在可用旧凭据 Session。
4. Redis 扫描仅检查测试 Namespace，证明 Key 不包含 loginIdentifier、规范邮箱、邮箱 Token、Vault Token、密码、Refresh Token、lookupId 或原始 Idempotency-Key。
5. 已知与未知 loginIdentifier 在各自的同标识并发竞争、Redis 不可用和 Argon2id 平衡路径下使用相同外显安全分类，不形成账号存在性 Oracle。
6. Change Password 在事务外完成 Reveal、校验与 Encode；强制 Lease 到期后的过期快照由 Account/Password 条件更新拒绝，失败事务不部分撤销 Session。
7. 同 Session 双 Refresh、Refresh 与 Logout、Refresh 与 Change Password/Disable Account 竞争；Session X 和条件代次保证最多一个当前 generation，重用撤销完整，账号安全变更提交后旧 Token 不可用。
8. 不同 Session 的 Refresh/Logout 可并行，不被同 Account 或 Tenant 的显式大锁无谓串行。
9. 两个 Create First Administrator 在 Redis Lease 到期后重叠；Bootstrap Account X 最终只允许一个 Human Administrator，Bootstrap Account/Password/Session 进入完整终态，无部分身份。
10. Create Account 的 Vault/Argon2id 在事务外；不同命令 Key 创建同邮箱由真实唯一索引最终只产生一个账号，未知唯一冲突不被错误吞掉。

### 12.3 管理、Gateway 与 Audit 测试

1. 现有同 Account 跨动作竞争、不同 Account 并行、Role 更新/删除与分配竞争、Actor 授权上限和最后管理员竞争继续通过；另制造 Redis Lease 到期，验证 Enable 与 Assign Roles 由 Member Version 条件形成合法串行结果。
2. 在 Actor Role 即将生效时把一个管理命令阻塞在相邻授权查询之间，验证整个命令只使用 Actor Account/Member 锁定后取得的一个数据库 `effectiveAt`，未来 Role 不能在命令中途赋权；Operations 与 Gateway 管理入口必须同构。
3. 两个不同目标都持有未来 ACTIVE PLATFORM_ADMIN Binding 时，并发执行会撤销全部 Role 的命令；验证两者先竞争同一管理员 Role X，最终至少保留一个管理员 Binding，而不是分别通过旧计数后都提交。
4. 持有真实 Actor Account SHARE、Actor Member SHARE、Role SHARE/X 或 PLATFORM_ADMIN Role X 的两阶段测试证明第二个 HTTP 事务确实等待；把对应生产 SQL 变异为普通 SELECT 时测试转红。尤其要在首请求完成权限检查后尝试撤销 Actor 所需角色，证明旧授权不能越过业务提交点。反向测试还要证明 Disable、Delete 与 Assign Roles 不被未请求且非 PLATFORM_ADMIN 的目标旧 Role 更新阻塞，而 Enable 会等待其即将恢复的目标 Role。
5. 通过 SQL 记录和两阶段屏障证明管理命令不再执行 Target Account 锁定读取；两个 Redis Lease 到期后的目标账号写由 expectedVersion CAS 得到单一合法结果，普通快照本身不是最终仲裁。
6. 构造两个管理员分别操作对方账号的真实交叉事务；即使 MySQL 选择一个事务作为死锁牺牲者，也只能得到一个合法提交和一个保留 Cause 的 409，不得外显 500、部分提交或在事务内自动重试。
7. 在命令幂等 caller scope、request hash 和 opaque reference 的真实 HMAC 调用处观察事务状态，证明 Operations 与 Gateway 都在 `TransactionTemplate` 前完成且每项只计算一次；HMAC 故障返回 503，事务与业务 DML 均未开始。
8. Gateway Target 与 Route 两种入口竞争同一个 Catalog Key；Redis 中不再出现 gateway-configuration:target:* 或 gateway-configuration:route:*。
9. 强制 Catalog Lease 到期，让两个不同 Root 基于冲突候选重叠执行；真实 SERIALIZABLE 至多提交一个，另一方得到保留 Cause 的并发错误。
10. 同 Root 陈旧 expectedVersion 即使取得 Catalog 锁也由 CAS 拒绝；删除 Root FOR UPDATE 后全 Catalog 编译、引用和安全上限验证不变。
11. Audit 同 UTC 日独立写入者保持连续 sequence/hash；两个首次 Chain 创建者并发时只产生一个 Chain Head，所有已提交事件可验证。
12. 持有真实 Audit Chain X 时第二个写入事务等待；不得用 Redis 锁测试替代数据库链头证据。

### 12.4 静态与回归门禁

- Mapper 与调用图合同测试按场景断言 Login/Change Password/Refresh/Logout 不再调用 Account/Tenant 锁，Change Password 不再调用 ACTIVE Session 锁，Bootstrap 不再调用 Internal Tenant 锁，管理命令不再锁 Target Account，Gateway 不再锁 Root；同时断言 Bootstrap Account X、Session X、Actor Account/Member SHARE、Role/PLATFORM_ADMIN 与 Audit Chain 锁仍存在；
- 架构测试断言 Platform 生产代码不再直接使用 RedissonClient/RLock 或 OperationsResourceLock，所有业务 Key 通过批准的 @ResourceLock 入口；
- OperationsResourceLock 与旧嵌套 Gateway 锁没有残留调用，普通 Context/列表/详情没有新增资源锁；
- 执行 Foundation 与 Platform 聚焦测试、各自完整 Maven verify、Platform 生成/模型门禁、npm --prefix requirements-site test、git diff --check 和链接检查；
- 测试按自己持有的业务 ID 和 Redis Namespace 清理，不使用 KEYS、flushdb、清表或自动迁移 Schema。

只有实际获得上述证据后，才能在 CURRENT_IMPLEMENTATION.md 登记实现与验证；本文自身不构成通过证明。

## 13. 可观测性与安全

按有限枚举记录 acquire-success、contention、interrupted、redis-unavailable、lease-expired-before-return、release-failed 和 invalid-key；资源 ID、摘要、请求 ID 和完整 Key 不作为 Metric Label。

安全日志只记录能力类型、固定错误分类、等待/持有时间区间和安全 Cause 类名。不得记录 Key 绑定实参、loginIdentifier、邮箱、密码、Token、Vault Token、HMAC、JWT、Idempotency-Key、SQL 参数或完整请求对象。

需要区分“Redis 正常协调成功”和“Redis Lease 已失效但数据库最终仲裁成功/拒绝”，以便验证两层职责；观测不得演变为提交前锁所有权检查或业务决策来源。

## 14. 文档治理

- 项目通用锁选择、Key 安全、Redis/数据库边界和测试原则仍由[开发约定 5.1](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制)拥有；
- [Foundation @ResourceLock 设计](2026-09-05-foundation-resource-lock-design.md)拥有公共 API、配置、表达式、多锁、AOP、异常和通用测试合同；本文不得重定义；
- 本文拥有 Platform Support 本切片的具体资源、删留矩阵、认证阶段和验收合同；
- Foundation 设计中的 Platform 迁移清单只是公共能力的候选基线；Create Account 摘要 Key、Gateway 单 Catalog Key 及数据库锁删留发生冲突时，以本文的 Platform 专项矩阵为准；
- [2026-09-04 资源级锁重构](../../../agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md)保留历史实施证据，但其“认证原事务/锁形态整体保留”不再是当前目标；
- [Controller 尽力审计设计](2026-09-05-platform-controller-best-effort-audit-design.md)拥有审计采集与后台写入；本文只保留 Audit Chain X；
- 代码、测试、主工作区、local-environment、部署和已知迁移差距只能在[当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)登记；
- 本设计不授权提交、推送、部署、修改外部资源或迁移共享数据库。
