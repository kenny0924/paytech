# 以 Payment 为基准统一写接口编码规范

- Decision status: Confirmed
- Decision date: 2026-09-16
- 依据：用户在公共规范与 Payment 规范差异对比后明确要求“以 payment 为主，结合一下”。
- 范围：将已确认的 Payment 通用编码方式合并到公共规范和规则摘要；本次只维护文档，不迁移业务源码、数据库或已发布合同。

## 1. 合并原则

公共编码规则由 [DEVELOPMENT_CONVENTIONS.md](../DEVELOPMENT_CONVENTIONS.md) 拥有，模块、包和接口边界由 [JAVA_PROJECT_STRUCTURE.md](../JAVA_PROJECT_STRUCTURE.md) 拥有。Payment 的[写接口规范](../../paytech/payment/docs/development/processor-conventions.md)作为具体应用和阅读示例，不再维护一套相反的通用分层规则。

1. API 的 RequestBody、ResponseBody 独立使用 class；字段首行简短说明，细节用 `<li>`。正常绑定和 Hibernate Validator 负责字段基础校验，不逐层重复。
2. 写操作使用对应 Processor，位于所属业务模块的 `service/processor`；处理顺序为基础校验、原提交判断、业务验证与完整 Context 构造、本地保存、提交后推进、响应。
3. Processor 可以查询本模块 Repository；关系和请求资格在这里验证。Service 接收已验证的 Context，处理本地业务与持久化；并发变化通过 SQL 条件、版本、唯一键和更新行数确认。
4. Context 直接持有本模块 DataObject，完整构造后贯穿请求；不复制同义 Snapshot/View，不进入 Repository/Mapper。Service 超过三个业务参数时封装具名 Context。
5. Service（含 Domain Service）不直接或间接访问外部依赖，不调用其他 Service 或 Processor。服务间的调用组合放在用例 Processor；外部协议由 Integration/Client 封装。
6. Processor 入口不得处于调用方已有事务。单个 Service 可承载完整本地保存事务；多个 Service 必须原子保存时，由 Processor 使用一个 TransactionTemplate，参与者不独立提交。事务内只操作本系统数据库。
7. 有明确业务 Owner 且依赖单向无环时，由 Owner 模块的 Processor 组合其他模块公开能力；本地能力通过 Service，需外部访问的能力通过公开 Processor。不能形成稳定 Owner 或存在循环风险时，保留 Web Processor 组合。跨模块仍禁止访问对方 DataObject、Repository、Mapper 和实现类。
8. 同步写 Processor 复用 Foundation AbstractProcessor；需要单资源锁时使用 AbstractLockProcessor，以 LOCK/TRY_LOCK 及等待时间、租约表达行为。既有 @ResourceLock 消费者继续遵守原配置与多锁合同，不叠加两套锁，不由文档调整推断代码已迁移。
9. 一个 Repository 对应一张表；数据库更新成功后同步当前对象。优先以业务请求唯一键及原记录比较防重；[2026-09-17 后继决定](2026-09-17-gateway-request-idempotency.md)进一步明确 HTTP Idempotency-Key 归 Gateway，禁止业务系统新增公共命令幂等组件消费，存量按任务迁移。
10. 自有配置按模块归集为 ConfigurationProperties，使用明确类型和字段注释；公共框架配置复用原配置类，Nacos 的加载、生效和安全边界保持。

## 2. 精确取代与保留

取代通用规则中 HTTP DTO 必须采用 Models + record、Processor 一律不得查 Repository、业务校验必须经 Service、跨 Owner 事务只能由 Web Processor 编排、Service 可在事务外访问出站端口及所有复杂用例只能由 Processor 开事务的对应表述。内部枚举归属所属业务模块的 `core/enums`；已发布 API Code 定义仍在 `api/enums`。

2026-09-05 模块协作决定、资源锁决定和 2026-09-12 用例编排决定的受影响段落由本决定及更新后的主题 Owner 接替；其数据所有权、事务公理、数据库最终保护、既有注解锁合同和测试隔离继续有效。旧的 Payment REST Processor 特例由统一规则取代。

业务 Service/Domain Service 的 interface + Impl、按表 Repository、公共异常、CodeEnum、轻量 HttpExchange API、公共 TypeHandler、手工 SQL 和测试规范继续保留。Foundation 已发布的机制模型不因 HTTP 业务 DTO 的 class 约定而批量改写。

Payment 的 Intent/Attempt/Action、路由顺序、支付状态和 Card 交互属于 Payment 业务规则，不推广为所有系统必备结构。本期 Mock + TODO、暂不编写 Java 单元测试属于阶段授权，不推广为长期编码规则。

## 3. 适用方式

新增和本次涉及的接口按合并后的规范维护；既有代码按具体任务逐步调整，不自动授权跨仓库批量重构、API 发布、幂等表迁移或锁协议切换。已有命令身份、失败、恢复及响应语义不因简化编码而改变。

文档维护和验证状态见 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md#coding-conventions-payment-20260916)。本文是规则决定，不是既有 Java 实现、测试或部署已完成的证明。

## 4. View 使用范围补充（2026-09-16）

用户明确要求：查询接口需要组合多张表的数据作为返回结果时使用 View，其他场景一般不建议使用。该补充将原先“不复制同义 View”的原则细化到查询与写接口：多表组合查询使用具名 `*View`；单表查询、写接口及普通内部数据传递默认复用已有对象，不增加 View 中间层。

完整选择规则由 [开发规范第 2.2 节](../DEVELOPMENT_CONVENTIONS.md#22-view-使用范围)维护，工程规范、Payment 示例及规则摘要同步引用。必要的 API ResponseBody、跨模块领域结果及 DataObject 的 Owner 边界保持；不授权跨模块 JOIN、跨库查询或批量改写已有接口。

## 5. 公共完整示例与执行要求补充（2026-09-16）

用户确认将 Payment 的接口开发写法提炼为[公共写接口开发示例](../examples/WRITE_API_EXAMPLE.md)，供各系统参考，减少接口实现偏离已确认规范。示例展示 API、协议转换、Processor、Context、事务、Repository/Mapper、响应和自查，并标明支付专有合同及既有源码与规范的差异。

完整通用教程只在公共示例维护；Payment 本地文档保留业务补充和旧章节路由。两份公共规范继续拥有规则，根 `AGENTS.md` 要求实现前实际读取、简述本次职责与事务范围、完成后自查；Payment 独立仓库的入口同样指向公共规范。该要求不增加用户审批，不代表模型必然遵守，也不授权批量迁移其他系统源码。
