# 公共写接口开发示例：以 Payment pay 为例

本文是各系统开发同步写接口时的完整参考。规则由[开发规范](../DEVELOPMENT_CONVENTIONS.md)和[Java 工程规范](../JAVA_PROJECT_STRUCTURE.md)拥有；本文把这些规则落实到调用顺序、对象和代码位置，不另建一套规范。决定依据见 [Payment 基准合并决定](../decisions/2026-09-16-payment-based-coding-conventions.md)。

示例使用 Payment 的业务名称帮助阅读。其他系统沿用职责划分，按自己的合同选择请求身份、状态、字段和后续操作；不必引入 Intent、Attempt、Token、路由或 Action。[Payment 专项补充](../../paytech/payment/docs/development/processor-conventions.md)只维护支付特有语义。

本文的原提交比较是业务防重，不是 HTTP 响应重放。按[全项目职责决定](../decisions/2026-09-17-gateway-request-idempotency.md)，`Idempotency-Key` 由 Gateway 处理，业务不接入公共命令幂等组件。下方 API/Converter 片段已按此规则规范化；源码中尚存的 Header 参数或检查属于待迁移差异，不得复制到新接口。

## 1. 开发前先落实本次接口

首次修改接口实现前，实际读取两份公共规范、本文和所属系统的专项合同；已经在本任务中完整读取且未变化的内容可以复用，不凭旧对话记忆或文件名推断。先用几句话说明以下内容，再直接实施已获授权的工作，无须另设用户确认步骤：

| 本次需要说明 | pay 示例 |
|---|---|
| 已读规范与专项合同 | 两份公共规范、本文、Payment 受理合同 |
| 业务归属、入口与对象 | Execution 的 PayProcessor；PayContext 持有 Intent、Attempt、InstrumentToken |
| 原请求身份与新请求分支 | sourceSubmissionId；命中后比较原内容并返回，新请求才选路和保存 |
| 原子保存与事务外操作 | 同一事务插入 Attempt、绑定 Intent；路由在事务前，执行在提交后 |
| 复用与新增 | 扩展所属 Repository/Mapper；只新增确有职责的类型 |

例如：“已读公共规范及写接口示例。本次取消入口由订单模块 Processor 处理，Context 使用已查询的订单和取消记录；原命令命中直接响应，本地状态变更集中提交，远端调用放在事务外。”其中的对象和顺序必须由本次真实合同确定，不能把这段话当作另一系统的业务设计。

规范缺失时先查导航或定位文件；仍不可读则说明缺失路径，暂停依赖该规范的实现。发现代码与规范不同，核对适用范围和已确认决定，不能以“附近代码如此”为由复制偏差。

## 2. 一条看得懂的调用链

```text
API：字段校验
  → Controller / Converter：协议转换
  → Foundation Processor 模板：拒绝已有事务 → 基础判断 → 资源锁（需要时）
  → 业务 Processor：查询归属 → 原请求查询与比较
      ├─ 已存在：构造原 Context → 响应 → 返回
      └─ 新请求：业务验证 → 完整 Context → 事务前依赖（需要时）
          → 本地事务：Service → 各表 Repository → Mapper XML
          → 提交后推进（需要时）→ 响应
  → Converter / Controller：HTTP 响应
```

没有外部依赖或后续动作就省略对应步骤；纯查询不机械增加锁、事务、保存或空 Processor。锁范围与数据库事务范围分别判断，持有资源锁不代表可以把远程调用放进事务。

| 层 | 放什么 | 不放什么 |
|---|---|---|
| API | HttpExchange 契约、RequestBody/ResponseBody class、字段注释与约束 | 业务实现、DataObject |
| Controller / Converter | 协议解析、必要类型转换、委托、HTTP 状态/Header | 数据库查询、业务资格、事务 |
| Processor | 输入附加判断、业务查询与校验、Context 构造、调用顺序 | SQL、逐表写入、其他模块持久化访问 |
| Context | 本模块 DataObject、必要领域值及已有数据上的纯业务行为 | HTTP 对象、Bean、Repository、事务或执行回调 |
| Service + Impl | 已验证 Context 上的本地业务与保存 | 重复关系校验、其他 Service、Processor、直接或间接外部访问 |
| Repository + Impl | 单表读写、行数检查、SQL 成功后同步原对象 | Context、跨表用例、创建或提交事务 |
| Mapper / XML | 方法绑定、明确列和条件 SQL | 业务流程、Service、Context |

Processor 可以直接查询本模块 Repository。Service 不调用其他 Service 或 Processor；多个 Service 的调用组合由 Processor 负责。Context 不进入 Repository/Mapper。

## 3. 文件放在哪里

以下是已拆业务模块的 Payment 路径；未拆分的服务使用 Web 的对应业务包，不因参考示例而新增 Maven 模块。

```text
paytech-payment-api/.../api/execution/
  PaymentExecutionApi.java
  request/PayRequestBody.java
  response/PayResponseBody.java

paytech-payment-web/.../ext/rest/
  execution/PaymentExecutionController.java
  converter/PayConverter.java

paytech-payment-execution/.../execution/
  service/processor/PayProcessor.java
  service/processor/request/PayRequest.java
  service/processor/response/PayResponse.java
  domain/PayContext.java
  service/PaymentExecutionService.java
  service/impl/PaymentExecutionServiceImpl.java
  domainservice/repository/PaymentAttemptRepository.java
  domainservice/repository/impl/PaymentAttemptRepositoryImpl.java
  core/dal/dataobject/PaymentAttempt.java
  core/dal/mapper/PaymentAttemptMapper.java
  core/enums/AttemptStatus.java
  core/configuration/PaymentExecutionProperties.java
src/main/resources/mapper/PaymentAttemptMapper.xml
```

一个写操作对应一个 Processor。已有业务 Service、Repository 和 Mapper 继续扩展，不能每加一个接口就复制一整套类型。Service 和 Repository 使用 interface + Impl；Controller、Processor、Converter 不机械创建接口。

## 4. API 与协议转换

### 4.1 定义最小合同

先确定操作目的、路径、字段、请求唯一身份、重复请求和响应语义，再写代码。沿用已有接口版本；新接口版本规则见[开发规范第 2 节](../DEVELOPMENT_CONVENTIONS.md#2-项目标识与命名)。

RequestBody、ResponseBody 使用普通 class，必要嵌套对象也使用 class。字段首行简短说明，额外约束用 `<li>`；必填、长度、格式和值域由 Hibernate Validator 标注，嵌套使用 `@Valid`。以下是 [PayRequestBody](../../paytech/payment/paytech-payment-api/src/main/java/com/paytech/payment/api/execution/request/PayRequestBody.java) 的字段片段：

```java
/**
 * 捕获模式
 * <li>AUTOMATIC：自动收款；MANUAL：授权后等待显式捕获。</li>
 * <li>必须显式传入，受理后不可修改。</li>
 */
@NotBlank(message = "捕获模式必填")
@Pattern(regexp = "MANUAL|AUTOMATIC", message = "captureMode 只能为 MANUAL 或 AUTOMATIC")
private String captureMode;
```

API 方法用 `@param/@return` 解释输入输出及必要 HTTP 语义。接口、Body 字段和 OpenAPI 保持一致；HTTP 成功不自动表示业务已完成。完整合同参考 [PaymentExecutionApi](../../paytech/payment/paytech-payment-api/src/main/java/com/paytech/payment/api/execution)。

以下是规范化后的内部 pay 签名，业务来源身份来自 Body 的 `sourceSubmissionId`，不增加 HTTP `Idempotency-Key` 参数；类级路径由 `@HttpExchange("/payment-execution/v1")` 声明。此片段说明目标职责，不表示当前源码及调用方已完成协议迁移：

```java
/**
 * 受理支付提交或返回已有业务提交
 *
 * @param intentId 支付意图 ID
 * @param body 请求
 * @return 响应
 *         <li>PROCESSING 返回 202，终态返回 200。</li>
 */
@PostExchange("/intents/{intentId}/pay")
ResponseEntity<PayResponseBody> pay(@PathVariable("intentId") String intentId,
    @Valid @RequestBody PayRequestBody body);
```

API 枚举使用稳定 Code 字符串，内部使用相应枚举；金额和 ID 遵循已批准 Wire 格式。含 Token 的对象和日志按敏感数据规则处理，不使用打印所有字段的默认 toString。

### 4.2 Controller 只适配协议

参照 [PaymentExecutionController](../../paytech/payment/paytech-payment-app/src/main/java/com/paytech/payment/ext/rest/execution) 的分层、移除请求 Key 后的规范化片段：

```java
PayRequest request = payConverter.convertRequest(intentId, body);
PayResponse response = payProcessor.process(request);
PayResponseBody responseBody = payConverter.convertResponse(response);
return ResponseEntity.status("PROCESSING".equals(responseBody.getStatus()) ? 202 : 200)
    .cacheControl(CacheControl.noStore()).body(responseBody);
```

200/202 和 no-store 是 pay 合同，不是所有写接口的统一返回规则。Controller 不查 Repository、不开事务，不把同一 API 路径再声明一遍。

[PayConverter](../../paytech/payment/paytech-payment-app/src/main/java/com/paytech/payment/ext/rest/converter/PayConverter.java) 的目标职责是字符串 ID、内部类型和响应 Code 的转换，以及 Path/Body 等协议对应关系；不校验 Gateway 请求 Key，不查询 Intent 或验证工具有效期。Converter 只在确有协议转换时存在，不增加一行透传层。

内部 Request/Response 是真实的模块边界，不能再在中间增加同义 Input、Command、View、Snapshot。DataObject 只在 Owner 业务模块内使用，HTTP 或跨模块不能直接返回它。

## 5. Processor 主流程

### 5.1 复用公共模板

调用链由 [AbstractProcessor](../../paytech/foundation/paytech-common-core/src/main/java/com/paytech/foundation/processor/AbstractProcessor.java) 与 [AbstractLockProcessor](../../paytech/foundation/paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/processor/AbstractLockProcessor.java)负责：

```text
process(request)
  → 拒绝调用方已有事务
  → validateBasic(request)
  → validate(request)
  → processRequest(request)
      有锁：lockCommand → 获取一次锁 → processWithinLock → finally 释放
      无锁：子类直接实现 processRequest
```

业务不重写公共 process，不复制事务入口检查或锁的 try/finally。默认 validateBasic 检查请求非空，并执行内部 Request 的 Bean Validation 默认分组及 `@Valid` 嵌套约束；业务子类不再通过 validate 重复调用 Validator。父类复用 Spring 管理的 Validator，非 Spring 创建的实例须先调用 setValidator；没有装配时失败关闭。HTTP Body 继续由入口 `@Valid` 校验，非模板入口仍需明确触发点。已存在的特殊认证错误合同可以保留 validateBasic 重写，例如缺失或畸形 API Credential 统一返回 401。

需要单资源锁时通过 LockCommand 选择模式：

```java
/** 立即尝试获取 Intent 资源锁。 */
@Override
protected LockCommand lockCommand(PayRequest request) {
    return LockCommand.tryLock(Locks.getIntentLock(request.getIntentId()));
}
```

`tryLock(key)` 立即尝试，`lock(key)` 持续等待且可中断；限时等待用 `tryLock(key, waitTime, leaseTime)`。时间单位毫秒，省略租约使用 -1 自动续租。锁实际业务资源，同一不变量的入口采用一致 Key；无互斥需求不创建假 Key。既有 `@ResourceLock` 不叠加或自动迁移，详细合同见[开发规范第 5.1 节](../DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制)。数据库唯一性、版本和状态保护继续保留。

### 5.2 阶段和提前返回清楚可见

以下是基于 [PayProcessor](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/service/processor/PayProcessor.java) 整理的**规范化主流程片段**，省略注入字段及辅助方法。`executeActions(context)` 是示意的事务外推进步骤，不是已有方法或要求新增的通用类；具体入口见第 9 节。

```java
/** 受理支付并推进新尝试。 */
@Override
protected PayResponse processWithinLock(PayRequest request) {
    // 查询原提交
    PaymentIntent intent = getAndCheckPaymentIntent(request);
    PayContext context = checkAndGetContext(intent, request);
    if (context != null) {
        return processResponse(context);
    }

    // 构建上下文
    context = buildPayContext(intent, request);

    // 支付路由
    route(context);

    // 保存上下文
    paymentExecutionService.savePayContext(context);

    // 执行动作
    executeActions(context);

    // 响应处理
    return processResponse(context);
}
```

业务主流程通常只有这几个可读阶段。辅助方法按业务含义命名，不用一个总揽式 handle 隐藏全部控制流；也不为每次赋值创建类或方法。普通本地写接口没有路由或动作时删除这两个阶段，不保留空方法或 TODO。

### 5.3 校验放到对应阶段

| 判断 | 位置 |
|---|---|
| 单字段必填、格式、长度、值域 | Body 注解及实际校验入口 |
| Path 解析、Header 与 Body 的对应 | Converter |
| 只依赖输入且首次/重放都适用的组合判断 | Processor.validate，锁前，无数据库或外部调用 |
| 请求方与资源归属 | Processor 查询业务根后 |
| 原请求身份、不可变内容 | Processor 原请求分支 |
| 当前状态、有效期、是否可新增 | Processor 新请求分支 |
| 已有对象的纯组合判断、派生计算 | 对象或 Context 的业务方法 |
| 写入时状态、版本、唯一性和额度 | 条件 SQL、约束与 Repository 行数检查 |

例如“两个可选版本字段必须一起提供”可放 validate；“本次首次创建的 expiresAt 必须晚于当前时间”放在未查到原记录之后。否则原请求到期后重放会在查询前被拒绝。

## 6. 原请求与 Context

### 6.1 按原请求身份查记录

先检查请求方对业务根的归属，再按已定义作用域查询原命令记录。记录存在后比较原输入，不是查到任何记录都能返回成功。业务根 ID、活动指针与命令身份可能不同。

pay 示例中 `sourceSubmissionId` 定位原 Attempt；原记录比较支付方式、captureMode 和原工具。`activeAttemptId` 会清空或改指向，不能用于定位旧提交。原记录存在时不再要求满足首次创建的有效期、开放状态或工具资格。

| 场景 | 示例预期 |
|---|---|
| 同键同内容，原记录处理中或已结束 | 返回原记录当前状态，不再次推进 |
| 同键改内容或归属不符 | 明确拒绝 |
| 原记录存在，但资源或工具后来到期 | 比较原内容后返回，不重新检查新建资格 |
| 新命令身份 | 进入对应操作的新请求业务验证 |
| 保存竞争失败 | 整个事务回滚；按合同处理冲突 |

业务系统不新增请求幂等命令表、Hash、租约或响应快照，不接入旧公共命令幂等组件；存量按专项任务迁移。业务唯一键及原记录比较继续保留，只有合同明确要求同次调用消化竞争失败，才在回滚后的事务外读取原记录并比较；不吞所有 DuplicateKeyException 后直接当成功。

### 6.2 一个 Context 贯穿请求

来自 pay 的构造片段：

```java
/** 构建支付上下文 */
PayContext buildPayContext(PaymentIntent intent, PayRequest request) {
    checkNewAttemptAllowed(intent);
    PaymentInstrumentToken token = getAndCheckPaymentInstrumentToken(intent, request);
    return new PayContext(intent, buildPaymentAttempt(intent, token, request), token);
}
```

[PayContext](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/domain/PayContext.java) 使用普通 class，直接持有 `intent`、`attempt`、`instToken` 三个本模块 DataObject。Context 一次构造后用于保存、推进和响应；不先建空 Context 再逐层补必需数据，不复制每张表的 Snapshot，也不重复保存可由对象取得的 ID。

DataObject（含只读 DB 对象）、Context、RequestBody、ResponseBody 均使用普通 class，不使用 record；字段可变性按业务需要决定，既有类型按当前任务范围调整。

Context 可以封装仅依赖已有数据的业务判断，例如 `canCancelLocally()`，无需为提取行为另建 Service；单个对象的规则优先放回该对象。需要时间等输入时显式传入，不在 Context 查询数据库、读取外部服务、开事务或缓存易失效判断。纯内存判断不能替代写时数据库保护。

Service 超过三个业务参数时封装具名 Context，三个以内按整体性决定；构造器、Repository 和 Mapper 不套用此数量规则。Context 不进入 Repository/Mapper，Service 提取对象或明确参数传递。

## 7. 本地事务集中保存

### 7.1 一个 Service 完成原子阶段

Service 接收已验证的 Context，默认归属与关系已校验。它不重新查询验证同一事实；并发变化交给实际写入条件保护。Service 使用 interface + Impl，调用方依赖接口。

来自 [PaymentExecutionServiceImpl](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/service/impl/PaymentExecutionServiceImpl.java) 的受理阶段：

```java
/** 保存支付受理上下文 */
@Override
public void savePayContext(PayContext context) {
    transactionTemplate.executeWithoutResult(status -> {
        paymentAttemptRepository.insertAttempt(context.getAttempt());
        paymentIntentRepository.bindAttempt(context.getIntent(), context.getAttempt());
    });
}
```

插入 Attempt 与绑定 Intent 必须一起提交或回滚。Processor 不逐表写入，Repository 不自行开启事务。新建对象可在插入前准备 ID、初始状态和时间；已持久化对象遵循第 8 节的 SQL 成功后回写规则。

### 7.2 多个 Service 由 Processor 组合

多个模块的纯本地写入必须原子完成时，由 Processor 开一个 TransactionTemplate，调用各模块公开 Service。各 Service 参与已有事务，不独立提交、不再嵌套创建事务、不使用 REQUIRES_NEW，也不调用其他 Service。

申请卡工具的事务片段来自 [ApplyCardInstrumentProcessor](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/service/processor/ApplyCardInstrumentProcessor.java)：

```java
transactionTemplate.executeWithoutResult(status -> {
    cardService.applyInstrument(cardContext);
    bindCardInstrument(token, cardContext);
    paymentExecutionService.saveApplyCardInstrumentContext(context);
});
```

这里的 Card 能力必须是本地操作，绑定方法只处理已有结果。需要外部准备时由事务外 Processor 先完成，跨模块使用 Owner 公开领域输入/结果，不能传调用方 DataObject 或整套 Context。该片段展示事务组合，不证明现有 Card 已接通真实存储。

无论有没有事务，Service 都不直接或间接调用 HTTP/RPC、Redis、MQ、KMS 等外部依赖，不调用 Processor 或执行外部动作的 Flow。Processor 入口不能处于调用方已有事务，不通过挂起事务或切换线程绕过。

任意 SQL、行数或提交失败均停止推进、向上抛出并丢弃本次 Context。后续外部执行失败不能把已经提交的本地受理事实描述成已回滚；结果落库需要明确的后续短事务。

## 8. Repository、Mapper 与对象同步

一个 Repository 对应一张表。多表写入的先后顺序由本地 Service 控制；各模块只通过自己的仓储访问自己的表。Mapper 方法使用明确的参数，业务 SQL 位于 `*Mapper.xml`，不使用注解 SQL。

下面选用 [PaymentAttemptRepositoryImpl.bindAction](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/domainservice/repository/impl/PaymentAttemptRepositoryImpl.java) 展示更新规则；它是动作阶段的片段，不要求其他写接口增加 Action：

```java
/** 绑定当前动作 */
@Override
public void bindAction(PaymentAttempt attempt, long actionId) {
    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (paymentAttemptMapper.activateAction(attempt, actionId, now) != 1) {
        throw new ConcurrentRequestException("支付尝试已有当前动作或已变更");
    }
    attempt.setActiveActionId(actionId);
    attempt.setVersion(attempt.getVersion() + 1);
    attempt.setUpdateTime(now);
}
```

[Mapper](../../paytech/payment/paytech-payment-core/src/main/java/com/paytech/payment/execution/core/dal/mapper/PaymentAttemptMapper.java) 声明参数，[XML](../../paytech/payment/paytech-payment-core/src/main/resources/mapper/PaymentAttemptMapper.xml) 使用旧状态与版本更新：

```java
int activateAction(@Param("attempt") PaymentAttempt attempt, @Param("actionId") long actionId,
    @Param("now") Instant now);
```

```xml
<update id="activateAction">
    UPDATE t_payment_attempt
    SET active_action_id = #{actionId}, version = version + 1, update_time = #{now}
    WHERE payment_attempt_id = #{attempt.paymentAttemptId} AND version = #{attempt.version}
    AND active_action_id IS NULL AND attempt_status = 'PROCESSING'
</update>
```

必须先确认 SQL 行数，再直接修改传入原 DataObject 的实际变更字段，时间和值与 SQL 一致。更新无其他返回语义时使用 void，不用 toBuilder 或复制构造替换对象，不重新赋回 Context。失败时不回写，不重读版本后盲目重试。

语句成功后同步内存不等于事务已提交；后续语句失败仍整体回滚，调用方丢弃 Context。读取方法返回所属模块对象或不存在结果，由业务入口解释含义。多表查询 View 的边界见第 10 节，不能以查询结果为由绕过表归属。

## 9. 事务外依赖与提交后推进

需要远端查询或准备时，在本地保存事务前经 Client/Integration 调用；提交后的业务操作只由本次保存成功的新请求推进。其他模块需要访问外部服务时调用其公开 Processor.process，不绕过入口调用内部步骤，不形成循环依赖。

示例主流程的 `executeActions(context)` 表达这一位置：由当前 Processor 或职责明确的事务外执行入口推进，具体外部协议仍归 Client/Integration；结果保存委托本地 Service。它不是空方法、通用流程框架或另一个同模块 API Processor 的占位要求。普通接口保存即完成时直接响应。

**源码与规范的已知差异：** Payment 当前 PayProcessor 调用的是 `ActionExecutionService.execute(context)`；该类会进入动作流程。新接口不能据此让 Service 间接执行外部动作。当前 RouteService/CardService 的 Mock 也不是可复制到生产实现的默认方式。示例采用规范化推进位置，现有代码的后续改造和真实能力接入须在各自任务中完成。

暂缓外部能力必须有当前任务授权；不能通过默认成功、随机远端 ID 或生产 Mock 掩盖缺失，也不把“重复请求”作为自动恢复入口。补偿、异步与未知结果按[补偿规范](../COMPENSATION_EVENT_GUIDE.md)及所属业务合同处理，不由这个写接口模板统一引入。

## 10. 响应、View、异常与配置

### 10.1 从已有 Context 构造最小响应

来自 pay 的响应片段：

```java
/** 响应处理 */
PayResponse processResponse(PayContext context) {
    var attempt = context.getAttempt();
    PayResponse response = new PayResponse();
    response.setAttemptId(attempt.getPaymentAttemptId());
    response.setStatus(attempt.getAttemptStatus());
    return response;
}
```

Converter 再将内部 Response 转换成 ResponseBody。不要为响应重查整套 Context，也不默认返回金额、路由、版本、执行标志或通用成功外壳。字段必须有真实调用方用途。

### 10.2 View 用于多表组合查询

查询接口组合多张表的返回数据时使用具名 `*View`，例如 PaymentDetailView。单表查询、写接口及普通内部传递一般不增加 View；不能改名 Result/DTO 来复制同义中间对象。DataObject 仍只在 Owner 模块内复用，跨模块和 HTTP 边界保留必要合同。完整规则见[开发规范第 2.2 节](../DEVELOPMENT_CONVENTIONS.md#22-view-使用范围)。

### 10.3 公共异常和配置直接复用

参数错误、业务拒绝、并发冲突、系统错误按[公共异常规则](../DEVELOPMENT_CONVENTIONS.md#4-异常与枚举)选择；有调用方确需区分的错误才定义业务 Code。HTTP 错误交给公共 Problem 处理，不新增宽泛 catch 或同义异常包装。

自有配置集中在所属模块的 ConfigurationProperties class，通过构造器注入；时长、开关等使用明确类型，注释说明单位和生效范围，不分散使用 @Value 或重复封装框架配置。Nacos 变更同步加载入口、模板和配置清单，具体合同见[配置规范](../DEVELOPMENT_CONVENTIONS.md#9-配置与-nacos)。

## 11. 交付前逐项核对

完成后说明下面各项对应的真实类、方法或检查结果；不适用的项说明原因，不机械勾选。发现偏差先修复已授权范围，再给用户结果，不把规范检查留给用户反复提醒。

- [ ] API 与 Body 定义最小合同，使用 class、字段注释、Hibernate Validator；非 HTTP 入口有真实校验触发点。
- [ ] Controller/Converter 仅适配；写操作 Processor 位于所属模块，主方法显示原请求提前返回与新请求完整阶段。
- [ ] Processor 查询本模块 Repository，完成归属、原输入和新请求资格检查；不调用 Mapper 或逐表写入。
- [ ] 同一个具名 Context 持有本模块 DataObject，构造完整；纯业务方法无外部访问，没有同义 ID、执行标志、Input/View/Snapshot。
- [ ] Service 不重复查询已确认关系，不调用其他 Service、Processor 或外部依赖；多个 Service 由 Processor 组合。
- [ ] 本地原子写入及事务位置明确，事务内没有直接或间接外部调用、Processor 调用；保存失败不推进。
- [ ] Repository 按表维护，Context 不进入持久化层；条件 SQL 和影响行数保护并发，成功后直接同步原对象。
- [ ] 锁针对实际资源，模式与同资源其他入口一致；没有叠加锁机制或用 Redis 替代数据库保护。
- [ ] 原请求比较通过后直接响应，终态/过期重放不受首次资格拦截；不同命令身份不被误当重试。
- [ ] 已区分 Gateway 请求重放与业务防重；业务模型不携带 HTTP Idempotency-Key，没有新增旧命令幂等组件消费或同义实现。原提交比较仅在业务确有操作身份时适用。
- [ ] 多表组合查询使用 View，写接口一般不增加 View；模块与 HTTP 边界没有泄露 DataObject。
- [ ] 复用了确有需要的公共包；配置按模块集中，没有生产 Mock、伪造成功、无消费者字段或多余抽象。
- [ ] 按[测试规范](../UNIT_TEST_GUIDE.md)和任务风险完成实际验证，说明未覆盖项；没有把 Payment 历史暂缓测试授权推广到本任务。

业务验证场景按本次操作选择：首次处理、同业务操作身份同内容/改内容、归属错误、原记录终态/过期查询、保存回滚、行数冲突、保存失败不调用下游、响应丢失后的业务行为；存在资源锁或外部步骤时再验证相关竞争和调用时机。HTTP Key、72 小时及原响应重放属于 Gateway 验证，不要求各业务系统复制这些场景。编译通过不能替代事务、数据库或外部交互证据。

交付说明可以很短：“入口和 Context 位于某模块；某方法原子保存两项事实，某步骤在提交后执行；已核对分层、重放、View 和对象回写；实际执行了哪些检查，剩余哪些限制。”无需另建每接口的规范报告文件。

## 12. 示例与源码的维护方式

本文维护一份公共完整示例；两份公共规范拥有规则，Payment 本地文档只保留业务映射与补充。修改公共接口写法时，同步示例、Agent 阅读入口和必要的静态检查，不在每个系统复制完整模板。

文中代码为讲解片段，省略 import、装配及非当前讲解部分，不是独立可运行工程。真实源码用于核对名字与调用链；明确标注的规范化步骤以规范为准，不把历史实现或暂缓接入当作范本。既有系统是否已迁移、经过何种验证仍只读[当前台账](../CURRENT_IMPLEMENTATION.md)。
