# Platform Controller 注解式尽力审计设计

日期：2026-09-05。

设计状态：正式设计已获用户确认；本文定义目标契约，执行步骤见[实施计划](../plans/2026-09-05-platform-controller-best-effort-audit.md)。

确认来源与精确取代范围见[审计决策](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)。当前实现、验证、部署和待迁移状态只由[当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)维护；本文描述目标契约，不宣称代码已变更。

## 1. 目标与范围

- 用 Controller 实现方法上的 `@Audit` 收敛审计入口声明、上下文生命周期和重复的异常处理。
- 主业务不执行审计表写入、不等待审计落库；可恢复的审计采集、提交和写入故障不得改变业务返回值、原始异常、提交或回滚结果。
- 接受审计延迟可见和部分记录永久丢失，不承诺至少一次、恰好一次、完整无遗漏或持久化重放。
- 保留现有成功/拒绝覆盖、详细安全原因、前后快照和业务幂等语义；业务事务内只保留必要的内存采集，不增加审计 SQL。
- 审计事件、属性、链头和关联 Security Event 在独立审计事务内原子提交。

只调整 Platform 已有认证、人员、角色、授权和 Gateway 配置 HTTP 操作的审计路径。既有 HTTP 契约、查询响应及权限不变；不新增表、模型字段、MQ、补偿登记、定时恢复或跨系统审计 JAR。

不把账号锁定、密码失败计数、Session/Refresh 撤销、权限修改、资源 CAS 或业务幂等降级为尽力执行。也不将本决策推广到其他系统 BCR、Finance 账务事实或 Vault 审计。

## 2. 方案选择

| 方案 | 取舍 | 结论 |
|---|---|---|
| Controller 注解 + 有界内存分发 + 独立后台事务 | 贴合本次 HTTP 操作范围；允许丢失，需明确入口盲区 | 采用 |
| Processor 注解 + 同一后台写入器 | 可覆盖直接调用 Processor 的非 HTTP 用例，但请求边界声明不在 Controller | 本次不采用；不在两层重复标注 |
| 业务事务登记持久化待处理事件，再异步写审计 | 能可靠恢复，但登记失败仍与业务耦合 | 不符合本次明确取舍，不引入 |

## 3. 职责与必要性

| 位置 / 能力 | 唯一职责 | 不负责 |
|---|---|---|
| Controller 实现方法上的 `@Audit` | 声明动作、资源类型及成功/拒绝采集策略 | 事务、查库、全量序列化请求 |
| 审计切面与调用级上下文 | 建立/结束一次审计作用域；收集安全请求元数据；合并业务事实与真实事务结果 | 授权判断、重放业务、推测前后快照 |
| Operations 事务执行器 | 复用固定事务画像，并在真实事务回调内自动登记审计完成通知 | 审计事实构造、Redis 资源锁、异常转换 |
| 类型化审计采集入口 | 在业务已取得事实的位置冻结资源 ID、拒绝原因、before/after、重放标记 | 生命周期控制、事务观察、审计 SQL、网络调用、后台重查业务状态 |
| 有界分发器 | 非阻塞接收冻结事件；限制内存与执行并发；统计拒绝/失败 | 持久化、自动重试、请求线程回退 |
| 审计写入 Service 与 Repository | 使用专用连接和独立事务追加现有审计链及事件 | 业务资格校验、锁业务 Tenant、改变业务结果 |

新能力放在现有 Platform Web JAR 内，复用已有审计格式化、Repository 和 Mapper 能力，不改变 Owner JAR 边界。业务 Service/Domain Service 继续使用 `interface + *Impl`；Controller/切面为具体类。不为每个端点生成一个 Handler，不预建没有消费者的 SPI、父类或公共 Foundation 模块。

### 3.1 注解及数据采集

示意声明：

```java
@Audit(action = "OPERATIONS_ROLE_UPDATE", resourceType = "ROLE")
```

注解只用于 Controller 实现方法，不进入 `platform-support-api`。Controller 方法体仍保持委托，不持有 `TransactionTemplate` 或访问 Repository/Mapper。

注解的资源类型是默认值，业务现场的类型化事实可以在该动作批准的资源类型内明确覆盖。例如 Login 成功资源是新 Session、拒绝资源是 Account；Logout 成功与通用拒绝也可能采用不同资源。不能用注解的单个静态值覆盖这些已有契约，或仅根据返回类型猜测资源类型。

资源定位仅支持明确命名的安全标量参数绑定，以及业务现场提供的类型化资源引用；不支持任意 SpEL、深层对象遍历或按对象名称猜测字段。资源尚未创建、参数不可用时沿用现有安全 fallback，不为审计查询数据库。启动和架构测试检查注解、参数绑定及采集入口匹配，不能默默让错误绑定产出伪造资源引用。

请求 ID 使用现有 `RequestIdFilter` 属性。操作者以身份/授权逻辑验证后的事实为准，不能将任意 Header 或请求参数直接视为已认证身份；身份未确定的失败沿用安全 UNKNOWN 表达。Controller 切面不能自行恢复密码、Token 或完整认证请求来补齐身份。

简单成功动作在现有业务成功位置提供稳定 ID 和结果；复杂动作继续提供类型化快照。共同的动作、资源类型和请求元数据从本次上下文取得，避免在每个 Processor 重复包装审计异常。业务拒绝的细分原因必须在被公共 HTTP 错误码折叠前冻结。

业务可见的审计 Service 只提供 `identify(Supplier<Identity>)`、`target(Supplier<Target>)`、`record(Supplier<? extends OperationsAuditFact>)` 和 `suppressReplay()` 四个语义入口。Supplier 在审计隔离边界内立即求值；无 Controller 作用域时不求值，也不会被保存到异步队列。业务接口不暴露 `capture`、`observeTransaction`、通用属性拼装或 DataObject 参数；Processor 不持有审计 Service/Context。这样把生命周期和投影样板移出业务代码，同时保留只有业务现场才知道的精确事实。

### 3.2 上下文生命周期

上下文限定于一次 Controller 方法调用，以作用域栈管理并在 `finally` 恢复/清理，不能使用不清理的静态线程状态。当前只在 Controller 边界采集；嵌套调用不得对同一业务事件重复提交，内部协作不靠调用另一个 Controller 触发审计。

切面只保护自己的采集和分发逻辑，业务委托恰好执行一次。采集失败不能跳过、重试或吞掉业务调用；分发失败不能遮蔽业务异常。异步任务只接收不可变值，不访问 Servlet Request、请求线程上下文、JoinPoint 或延迟执行的 Supplier。工作线程中的诊断上下文同样必须清理。

## 4. 精确覆盖清单

| Controller | 成功和拒绝 | 仅拒绝，不记录正常查询成功 | 本次不增加操作审计 |
|---|---|---|---|
| OperationsAuthenticationController | login、refresh、logout、changePassword、createFirstAdministrator | 无 | context |
| OperationsManagementController | createAccount、disableAccount、enableAccount、deleteAccount、assignRoles、createRole、updateRole、deleteRole | accounts、roles、roleEmployeeCounts、role、permissions、roleResources、auditEvents、auditEvent | 无 |
| OperationsAuthorizationController | 无 | authorize、navigation | 无 |
| GatewayConfigurationManagementController | changeTargetStatus、changeRouteStatus | targetServices、targetService、routes、route | 无 |

现有动作 Code、资源类型、失败分类和属性消费者继续有效。系统异常尝试记录使用已支持的 FAILED 结果及固定安全原因，不将数据库异常文本或完整 Cause 写进属性，也不将系统失败伪装为业务拒绝。

表中的现有“拒绝”是业务分支和 `FailException` 覆盖，不代表此前所有 HTTP 错误均有审计。新切面可观察到 Controller 方法体内原先先于 Processor 包装的分页参数错误；将它作为对应已标注动作的拒绝尝试，不扩展未标注端点。FAILED 是本次补齐的被审计方法内系统失败尝试，不宣称是旧实现已有覆盖。

进入 Controller 前发生的 JSON 解析、参数绑定/校验、Filter/Gateway 拒绝，以及未匹配路由，不在方法切面覆盖范围。保留现有访问日志、安全边界和共享异常处理；本次不新增全局审计 Filter/异常处理器来扩大覆盖，不重复记录 Body。

未经过这些 Controller 的定时任务、MQ 或直接 Java 调用不自动获得操作审计；未来有真实消费者时明确接入同一采集/分发能力，不调用 Controller 绕层。业务现场不允许因缺少审计上下文而失败，但测试必须发现获批 HTTP 覆盖意外绕过切面的情况。

## 5. 业务结果与事务结果

审计状态至少区分“业务用例结果”和“所属业务事务完成情况”，不能只看方法返回、异常类型或 HTTP 状态。统一 Operations 事务执行器封装现有显式 `TransactionTemplate` 画像，并在每个真实事务回调激活后通过 Spring 完成通知观察结果；Processor 只选择 `write`、`query` 或 Gateway 串行化写画像，不直接调用审计生命周期 API，也不引入 `@Transactional`。

| 事实 | 审计行为 | 业务行为 |
|---|---|---|
| 真实变更且所属事务确认提交 | 发送冻结的成功事件 | 原结果不变 |
| 登录被拒绝，但失败计数/锁定已提交 | 发送带细分原因的 DENIED，不误认为整笔回滚 | 提交后仍返回既有公共认证错误 |
| Refresh 重用触发撤销并提交 | 发送对应 DENIED，保留撤销已完成的事实 | 撤销不受审计故障影响 |
| 授权返回 `allowed=false` | 发送 DENIED，不能因正常返回而记成功 | 返回原授权结果 |
| 写事务确认回滚 | 丢弃成功快照；按安全分类记录失败尝试 | 保留原异常和回滚 |
| 提交结果未知 | 不生成成功变更事件；FAILED 仅表示尝试未获确认，固定原因说明提交结果未知 | 不声称业务必然回滚，不自动重试 |
| 同命令幂等重放 | 不新增成功变更事件，不补造首次已丢的历史 | 保留原幂等结果 |
| 无业务事务的查询拒绝 | 方法结果确认后直接尝试入队 | 不为审计新建业务事务 |
| 写命令在获取 Redis 锁、业务校验等阶段被拒绝，事务尚未开始 | 将“未开始事务”作为合法终态，按原安全分类记录拒绝/失败尝试 | 保留原异常，不为审计启动业务事务 |

发送须同时满足：业务事实已冻结、Controller 方法调用已终结、所属业务事务终态已确定（包括明确没有启动事务）、该事件尚未分发。事务回调只登记事务终态，切面退出只登记调用终态；两者经同一个原子完成门闩汇合，最多提交一次。不能在回滚通知先到、最终业务异常尚未到达切面时，抢先将 DENIED 记成 FAILED。

结果合并时，已确认提交的明确业务事实优先：登录被拒绝且失败计数提交仍是 DENIED；业务变更提交后响应转换异常不能覆盖为回滚失败。没有已提交成功事实时，再结合明确业务拒绝、原始异常和事务终态选择 DENIED 或 FAILED；提交结果未知不声称变更成功或确定回滚。Controller 返回不代表响应一定已发送到客户端；网络发送错误不再为同一业务变更追加矛盾事件。正常查询成功继续不生成事件。

如果所属事务在 Controller 返回时仍未完成，只保存冻结事件并等待真实完成通知；不能把“切面结束”作为外层事务已提交的替代。所有事务回调内的审计逻辑也属于隔离边界，不允许审计异常传播给提交调用者。

## 6. 业务锁与审计锁拆开

现有 `OperationsAuditService.lockTenant` 被认证路径用于 Tenant 存在性、资格重读和锁序保护，这部分不是可丢弃审计。将其职责迁回身份/授权 Owner，在原业务事务中保留必要的读取、局部锁和重校验；不得随审计写入搬到后台。

后台只锁审计链头，不锁业务 Tenant 或重读账号当前权限来裁决历史记录。既有账号/Session 仲裁、Redis 资源锁、最后管理员保护、唯一键和业务 CAS 不变。仅为同步审计写入而建立的可写事务理由移除；其他事务是否必要仍按各自业务不变量判断，不借本次任务全量改造普通查询。

## 7. 分发、资源隔离与故障策略

### 7.1 有界内存执行

首版使用单个专用后台写线程和有界队列。入队使用非阻塞 offer；队列满或关闭时丢弃新事件并计数，保留已经排队的事件。禁止阻塞式入队、无界线程/队列、公共 ForkJoinPool 和 CallerRunsPolicy。

已确认采用以下保守初值；它们是审计资源预算，不是业务参数合法性上限或吞吐承诺：

| 资源 | 初始预算 |
|---|---|
| 后台写线程 / 专用连接池最大连接 | 各 1 |
| 队列容量 | 256 个冻结事件 |
| 单事件安全字段总 UTF-8 数据量 | 64 KiB；包含元数据与属性，集合最多 256 个属性 |
| 单个属性值 UTF-8 数据量 | 4 KiB，且不得超过现有列的可存储长度 |
| 连接获取 / 建连 / InnoDB 锁等待 | 各最多 1 秒 |
| SQL / 审计事务 / 网络读取 | 分别 2 秒 / 3 秒 / 3 秒 |
| 正常停机排空等待 | 最多 5 秒 |

所有采集长度限制在构造大字符串或复制大集合前检查；超限丢弃整条审计并计数，不截断权限集合或 before/after 伪造完整事实，也不拒绝主业务。队列和单事件上限共同限制审计内存；不能只限制事件数量。诊断标签使用有限枚举，不使用请求 ID 或资源 ID 作为 Metric Label。

连接、锁、SQL、事务和网络超时分别落实，不能把 `TransactionTemplate` timeout 当成所有阻塞的绝对截止时间。测试证明调用线程不等待后台审计；预算不等于共享 JVM/MySQL 在任意故障下物理零影响。

### 7.2 数据库连接隔离

使用独立的小型审计写连接池、对应事务管理器和 MyBatis 写会话，连接同一个 Platform 数据库并复用宿主安全连接配置，不新增库或复制凭据。

业务 Mapper/事务管理器仍明确绑定主数据源；审计查询继续使用原查询链。不能因新增 DataSource 导致 Spring 自动配置退让、业务 Mapper 误绑定或所有读取走审计池。审计写会话复用现有 SQL 定义和类型映射，不复制两份 SQL 权威。

专用池最小空闲连接为 0，审计连接暂时不可用不能单独阻止主服务启动；审计健康单独观测，不纳入主业务 readiness 判定。主业务数据库、Redis、认证依赖仍按原规则失败关闭，这不是将整个服务改为忽略基础设施错误。

### 7.3 丢失与诊断

采集错误、大小超限、队列满、后台 SQL/序列化/Hash/事务失败、关闭后提交和已封链迟到，均丢弃本条并记录固定分类计数及限频安全日志。后台单条失败不应终止后续消费；不递归调用审计自身记录审计故障。

首版不做自动重试、落盘、补偿登记或补写历史。提交结果未知时也不重新生成新 ID 重试，避免制造重复事实。进程被强制终止可能同时丢失队列和未导出的指标；监控不能证明每条丢失都能被追踪。

正常关闭停止接收，有限等待队列排空；超时后丢弃剩余事件，不无限阻止应用退出。计数与日志不得携带请求/响应 Body、密码、Token、完整异常消息或 SQL 参数。严重 JVM/操作系统故障不在“审计异常必然可恢复”的承诺内。

## 8. 审计持久化与时间

每条事件在独立 `TransactionTemplate` 中执行现有流程：创建/锁定日链、分配序号、计算 Hash、写事件和全部属性、推进链头、必要时写关联 Security Event。任一步失败整条审计回滚，不能逐条 SQL 自动提交。

Hash 算法、Canonical 版本和历史单属性兼容编码不变；不重写历史记录。保留现场发生时间作为 `occurred_time`，实际后台写入时取得 `received_time`；沿用现有 UTC 发生日分区。迟到事件遇到已关闭链时丢弃并计数，不重开旧链、不改记为今天发生。

序号表示同一链上的数据库追加顺序，不保证跨实例业务发生的绝对顺序。链只保护已写入记录间的完整性关系，不能证明未丢失、完整收集或独立外部锚定。本次不新增封链任务、完整性证明服务或自动保留/销毁流程。

查询可能在业务完成后短时间内尚未看到对应审计，这是新契约。现有审计列表/详情的权限、筛选、分页、响应字段和敏感属性展示不变；测试改为有上限的条件等待，不能在 API 返回后立即断言必有审计行。

## 9. 验收契约

从真实 HTTP Controller 入口走生产链，复用仓库自有 Loopback MySQL/Redis；不使用方法级 Stub，不创建或迁移测试 Schema，不连接 `mor-test`、其他业务服务或生产基础设施。

1. 覆盖清单中的原成功/拒绝事件及全部前后属性仍正确；普通查询成功、context、JWKS/Snapshot 不新增操作审计。
2. 登录失败计数/锁定与 Refresh 重用撤销在审计故障时仍提交，外部公共认证错误不变；成功写入的事件保留内部细分原因，敏感值不泄露，不要求故障时仍能找回允许丢失的事件。
3. 真正提交、回滚、提交结果未知、未开始事务、`allowed=false` 和响应转换失败区分正确；验证事务通知与切面退出的先后顺序及最多一次分发，审计故障不覆盖同时发生的业务异常。
4. 同键同请求重放不重复生成变更事件；同键异请求仍按原业务幂等规则拒绝，不以审计状态裁决幂等。
5. 用独立连接持有真实审计链锁，并用受控测试屏障填满队列、耗尽审计池，验证业务照常完成、请求线程不执行写入、资源有界且可恢复；不靠固定 sleep 或方法 Stub 制造结论。
6. 在现有审计写 SQL 的不同阶段触发真实失败，验证事件/属性/链头/Security Event 不出现半条记录；失败后的下一事件仍可处理。
7. 排队后改变源集合，写入仍是原快照；超限、采集失败、关闭和复用线程不串用户、不泄漏上下文或敏感字段。
8. UTC 跨日积压、封闭旧链、独立实例并发追加及未知提交不生成错误顺序承诺、不重开旧链、不自动重试。
9. 专用审计数据库连接失败不单独使主服务 readiness 失败；主依赖真正不可用仍按原规则失败关闭；验证业务 Mapper 没有误绑定。
10. 测试结束前有界排空/关闭各自分发器，再按测试持有 ID 清理数据，避免后台任务在清理后重新写入。不同进程/套件遵循现有测试隔离契约。

随后执行 Platform 架构、持久化与 HTTP 回归、完整 Maven 门禁、需求站点测试及变更范围的差异/链接检查。只有实际执行后才能在当前状态台账登记验证结果。

## 10. 后续实施与文档治理

正式设计已确认，后续按[实施计划](../plans/2026-09-05-platform-controller-best-effort-audit.md)及 TDD 推进。实施顺序需先建立业务不受审计故障影响的失败证据，再迁移 Tenant 业务保护、采集/分发/写入边界，最后替换 Controller 声明及旧 Processor 包装；不能先删除审计调用再补覆盖。

本次决策对现有规范的作用域由独立决策记录路由。前序设计保留历史背景并附窄范围后继链接；当前状态台账单列待实施项，不将新的目标语义写成已运行能力。没有新增表或字段，不授权 PDManer/DDL 或运行数据库迁移。

不提交、推送、部署或调整外部资源；这些动作需要用户另行明确授权。

## 11. 技术依据

- [Spring AOP 代理语义](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)：方法切面依赖代理调用，不能用类内自调用假设覆盖。
- [Spring MVC 参数校验](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)：部分失败会阻止 Controller 方法调用，必须显式列为覆盖边界。
- [Spring 事务完成回调](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html)：提交后回调不自动提供新事务，且部分回调异常仍可能传播。
- [JDK ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)：队列与拒绝策略决定过载行为，CallerRunsPolicy 不满足本次请求线程隔离要求。
