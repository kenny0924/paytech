# Payment 写接口开发规范

通用写法统一阅读[公共写接口开发示例](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md)。本文只保留 Payment 业务映射、特殊分支和旧章节导航，不再维护第二份完整教程；规则权威仍为[开发规范](../../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[工程规范](../../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)，依据见[2026-09-16 合并决定](../../../../agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md)。

实际调用链见[pay 阅读说明](../pay-interface-walkthrough.md)，实现与验证状态见[当前台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。本文不证明真实渠道、数据库并发或恢复能力已经验收。

## 1. 适用范围与优先级

Payment 写接口先读两份公共规范和公共示例，再读本页及各自业务合同。Intent、Attempt、支付工具、captureMode、Action 和路由顺序仅属于 Payment，不推广到其他系统。公共范例已明确标注源码偏差，新代码按现行规范实现。

## 2. 一个写接口应当呈现的完整顺序

通用调用链见[公共示例第 2 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#2-一条看得懂的调用链)，Processor 片段见[第 5 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#5-processor-主流程)。pay 仅对新提交执行工具资格检查、路由、本地受理和后续 Action；原提交比较通过直接响应。

## 3. 文件位置与依赖边界

完整目录与职责表统一维护在[公共示例第 3 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#3-文件放在哪里)。Payment API 位于 paytech-payment-api，Controller/Converter 位于 Web，PayProcessor、PayContext 和受理持久化属于 Execution；Card 和 Routing 通过各自公开能力协作。

## 4. API、请求与响应模型

### 4.1 对外合同先于实现

方法、路径及字段见 [PaymentIntentApi](../../paytech-payment-api/src/main/java/com/paytech/payment/api/execution/PaymentIntentApi.java)、[PayRequestBody](../../paytech-payment-api/src/main/java/com/paytech/payment/api/execution/request/PayRequestBody.java)和[PayResponseBody](../../paytech-payment-api/src/main/java/com/paytech/payment/api/execution/response/PayResponseBody.java)。pay 的金额、币种、OE 从 Intent 取得；不推导 Capture 等其他命令也无需金额。

### 4.2 字段格式与注释

类型、注释和校验示例见[公共示例第 4 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#4-api-与协议转换)。Payment 的 Long ID 使用无前导零的正十进制字符串并限制 Long 上界，内部使用 Long；工具 Token 对象维护脱敏 toString。captureMode 必须显式提供且受理后不可修改。

### 4.3 基础校验实际由谁触发

HTTP 通过 API 的 @Valid 触发 Hibernate Validator；非 HTTP 入口的触发点与公共模板边界见[公共示例第 5.1 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#51-复用公共模板)。内部 Request 仅有约束注解不代表已执行校验。

### 4.4 API 方法注释

统一按[公共示例第 4.1 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#41-定义最小合同)编写 @param/@return 和必要 HTTP 说明。CreateIntent 的 200 表示请求成功，pay 的 200/202 按 Attempt 状态处理，不能混同支付资金成功。

## 5. Controller 与 Converter

具体片段见[公共示例第 4.2 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#42-controller-只适配协议)。Payment 的 `Idempotency-Key = submission:<sourceSubmissionId>` 对应检查留在 PayConverter；内部 PayRequest 不重复携带该 Header。此格式不推广为所有写接口的固定前缀。

## 6. Processor 模板与锁

### 6.1 公共模板的职责

继承、validate 和锁模板统一见[公共示例第 5 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#5-processor-主流程)。CreateIntent 的两项 Partner 定价版本成组判断放 validate；expiresAt 的首次创建资格判断放在未查到原 Intent 后，不能阻止到期原请求的重放。

### 6.2 锁定业务根

Pay 使用 AbstractLockProcessor 的 TRY_LOCK 立即尝试；CreateIntent 和 ApplyCardInstrument 使用 LOCK 等待。Pay 与 ApplyCardInstrument 共用 Intent Key，CreateIntent 按来源业务 Key 协调。获取模式、等待与租约遵循[公共模板说明](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#51-复用公共模板)。`redis.coordination.resource-lock.*` 属于既有注解锁配置，不作为此模板的参数来源，不叠加两套锁。

### 6.3 主方法参考

只维护[公共示例第 5.2 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#52-阶段和提前返回清楚可见)这一份完整片段；以公共示例的职责边界为准，不能把既有 ActionExecutionService 的命名和外部流程作为新接口例外。

## 7. 原提交查询与重复请求

### 7.1 先查原记录，再检查能否新建

pay 的原提交查询必须使用来源提交唯一键，而不是当前活动 Attempt 指针：

```java
PaymentAttempt getAndCheckOriginalAttempt(PaymentIntent intent, PayRequest request) {
    PaymentAttempt attempt = paymentAttemptRepository.findAttemptBySubmission(request.getSourceSubmissionId());
    if (attempt == null) {
        return null;
    }
    if (!Objects.equals(attempt.getPaymentIntentId(), intent.getPaymentIntentId())) {
        throw new ConcurrentRequestException("来源提交已被其他支付意图使用");
    }
    return attempt;
}
```

原记录存在时继续比较支付方式、capture mode 和原工具 Token，再构造原 Context 并响应。Client 归属由此前读取 Intent 时校验。不能查到记录就不经归属和内容比较直接返回。

`activeAttemptId` 用来判断能否开始另一笔尝试。旧尝试终态后指针会清空，也可能已经指向新尝试，因此它不能替代原请求的永久身份。

### 7.2 需要保持的行为

以下场景均指进入业务处理、成功取得资源锁后的判断：

| 请求场景 | 预期处理 |
|---|---|
| 同一来源提交、相同内容、原 Attempt 处理中 | 返回原 Attempt 当前状态，不再执行 Action |
| 同一来源提交、相同内容、原 Attempt 已结束 | 返回原终态，不创建新尝试 |
| 原 Attempt 已结束，Intent 已有另一活动 Attempt | 仍可返回旧提交结果 |
| 原提交存在，Intent 已关闭/到期或原工具已过期/撤销 | 核对原内容后返回，不重新要求新建资格 |
| 同一来源提交改方式、捕获模式或工具 | 拒绝内容变更 |
| 来源提交属于另一个 Intent | 拒绝归属冲突 |
| 新来源提交，但已有活动 Attempt | 按新建资格检查拒绝 |
| 新来源提交、资格通过 | 构造新尝试，选路并保存 |

同键比较从原记录导航原工具，不先查询请求中替换后的工具而把内容冲突变成“新工具不存在”。原工具记录缺失或关联数据损坏属于数据问题，不能默认匹配、返回伪造结果或当作一次新提交。

### 7.3 推广到其他接口

通用请求身份与竞争处理见[公共示例第 6.1 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#61-按原请求身份查记录)。当前 pay 的 source_submission_id 全局唯一；Capture 等后续操作必须确定自己的命令身份，不能沿用“Attempt 存在即重试”的判断。

pay 不捕获保存异常自动重查。唯一键/CAS 冲突使事务回滚，不把所有 DuplicateKeyException 解释成成功；若未来合同要求消化竞争失败，另行明确回滚后的原记录比较。

## 8. Context 的构造与使用

当前 [PayContext](../../paytech-payment-core/src/main/java/com/paytech/payment/execution/domain/PayContext.java) 直接持有 intent、attempt、instToken 三个本模块 DataObject；新建与重放分别完整构造后贯穿请求。工具类型与 PAY 组成 actionFlowKey，是 Payment 的业务派生值。

构造代码、纯业务方法、Service 参数与 Repository 边界统一见[公共示例第 6.2 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#62-一个-context-贯穿请求)。不恢复 newAttempt/allowExecution，也不为各表复制 Snapshot。

## 9. 业务验证与事务前依赖

### 9.1 验证放在明确的位置

通用校验位置见[公共示例第 5.3 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#53-校验放到对应阶段)。pay 原请求分支核对 Intent 归属、来源提交与原工具/方式/捕获模式；新请求分支才检查 Intent 开放且未过期、没有活动或成功 Attempt，以及工具的 Client、Intent、提交、方式、期限与撤销状态。

Action 的渠道/风控结果及必需字段由对应 Actor 解释，公共 Flow 不按动作类型集中校验。

### 9.2 外部依赖在事务外完成

pay 构造 Context 后调用 Routing 能力，将 Channel、Account、MID 绑定到待保存 Attempt，再进入受理事务。已冻结坐标沿原业务记录使用，不能在重放或后续操作中重新选路。

当前 RouteService 为本地 Mock；真实外部选路由 Routing 的事务外公开 Processor 通过 Client/Integration 完成，不能把远程访问加进 Service。Mock 的存在不代表真实路由已接入，也不允许在其他系统照搬默认成功。

### 9.3 跨模块 Processor 的调用边界

跨模块公开入口与事务约束统一见[公共示例第 9 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#9-事务外依赖与提交后推进)。申请卡工具需要外部准备时，由 Execution Processor 在事务外调用 Card 公开 Processor；Card 纯本地能力不预建空 Processor。

## 10. Service：本地保存与 Processor 事务组合

完整代码统一见[公共示例第 7 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#7-本地事务集中保存)。pay 由 PaymentExecutionService.savePayContext 在一次事务中插入 Attempt、绑定 Intent；CreateIntent 的原子阶段只插入 Intent。

ApplyCardInstrument 由所属 Processor 的一个 TransactionTemplate 组合 CardService.applyInstrument、纯内存工具引用绑定与 PaymentExecutionService.saveApplyCardInstrumentContext。两个 Service 参与同一事务，不互相调用。跨模块不传 Card DataObject；当前 Card 合成替身不代表真实卡记录已落库，真实准备结果不能夹带 CVV 等禁止持久化的数据。

## 11. Repository、Mapper 与对象同步

### 11.1 按表维护

Intent、Attempt、InstrumentToken、Action 分别由对应 Repository 管理。通用职责和 XML 规则见[公共示例第 8 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#8-repositorymapper-与对象同步)。

### 11.2 先写库，再同步原对象

公共示例第 8 节以 PaymentAttemptRepositoryImpl.bindAction 展示 Mapper、条件 SQL、行数检查与原对象回写。Payment 不另维护第二段范例；对原状态、版本、时间和执行位的修改均遵循公共规则，结果事务失败后丢弃 Context。

## 12. 提交后的业务推进

Processor 调用一个明确的业务执行入口。pay 调用 `ActionExecutionService.execute(context)`；纯本地写接口保存后直接响应，不为符合模板而引入 Action。

上述 `ActionExecutionService` 是当前源码的流程入口名称；按新约定，承载外部动作的流程编排应放回事务外的 Processor/动作执行入口，Service 仅保留本地业务与保存。此处用于阅读既有代码，不构成 Service 可以间接执行外部调用的例外。

对于支付动作流程，沿用已有抽象：ActionFlowFactory 按支付工具类型和入口操作选择 Flow，ActionFactory 按 ActionType 选择 Actor。Actor 执行当前动作，返回 ActionResult；后继选择属于 Flow，不能由一个 Actor 调用另一个 Actor 来推进流程。Actor 如需目标模块的外部能力，可在事务外按第 9.3 节调用其公开 Processor；事务内的结果保存或 `doInTransaction` 钩子不能调用 Processor。

- 具体业务结果的解释和必需字段校验放在对应 Actor，不集中到公共 Flow 中用动作类型 switch 判断。
- 执行结果先放入 ActionResult；保存成功后由 Repository 同步原 Action，不在外部调用返回时直接改已持久化对象。
- 当前 Action 结果、后继 Action 和执行位切换按一个结果事务处理，提交成功后才能执行下一动作。
- 业务更新若仍需原 Action ID 作为 SQL 条件，应在清空或替换执行位前完成；不能先清空指针再用它更新金额。
- Flow 到 End 表示本次入口不再推进，不自动等于 Attempt 终态。例如 AUTHORIZE 完成后仍可等待独立 Capture。
- 当前 CARD + PAY 的失败处理不能直接推广为 Capture/Void 失败时也将整个 Attempt 置为失败。
- 重复请求返回原记录，不承担自动恢复。需要恢复的入口必须明确恢复身份和执行资格，不能见到 PROCESSING/UNKNOWN 就再次扣款。

这是后续执行的组织边界，不要求每个写接口建设流程图、状态机、工厂或补偿任务。真实 Owner 接入和中断恢复按各自需求实现，不把当前 Mock/TODO 写成已经具备的能力。

## 13. 响应、异常和公共类型

### 13.1 响应只返回调用方需要的结果

公共写法见[公共示例第 10 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#10-响应view异常与配置)。pay 仅返回 attemptId/status；PROCESSING 返回 202，SUCCEEDED/FAILED/CANCELLED 返回 200，并设置 no-store。业务终态失败不等于本次读取原提交发生 HTTP 错误。

多表支付详情查询使用具名 View；pay、createIntent、applyCardInstrument 等写接口一般不新增 View。具体类型选择遵循[公共 View 规则](../../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#22-view-使用范围)。

### 13.2 异常表达明确业务含义

普通输入、业务拒绝、并发和系统错误直接复用公共异常；资源不存在继续沿用 CommonErrorCode.NOT_FOUND，工具过期或撤销使用已有 PaymentErrorCode。不得把未知渠道结果伪造为明确扣款失败。HTTP 错误仍由 Foundation Problem 处理。

## 14. 新接口的开发顺序

开发前按[公共示例第 1 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#1-开发前先落实本次接口)说明本次职责与事务范围，再按其调用链实施。Payment 新操作必须明确所属入口 Flow、原操作引用和资金/状态语义，不机械复制 pay 创建 Attempt 的行为。

## 15. 评审与验收清单

### 15.1 代码结构

统一使用[公共示例第 11 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#11-交付前逐项核对)，本页不复制清单。Payment 额外核对第 7 节原提交场景、第 9 节工具和路由关系，以及第 12 节 Action 事务切换。

### 15.2 需要验证的业务行为

按本次需求和[测试规范](../../../../agent-docs/UNIT_TEST_GUIDE.md)验证首次处理、原提交重放、归属/内容冲突、保存回滚和执行时机。本期暂缓测试记录仅见[状态台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)，不构成后续免测规则。构建命令见 [README](../../README.md#构建)，编译不等于真实数据库/Redis/渠道验证。

## 16. 配置与 Nacos

通用配置方式统一见[公共示例第 10.3 节](../../../../agent-docs/examples/WRITE_API_EXAMPLE.md#103-公共异常和配置直接复用)及其规范链接；具体键与模板只维护在[配置清单](configuration.md)。

### 16.1 按所属模块归集

Payment 自有配置按 Payment<职责>Properties 与 payment.<职责> 维护；Execution、Card、Identifier 各自拥有自己的配置，业务配置位于所属模块 core/configuration，Web 装配配置位于 configuration。Spring Boot/Foundation 已有配置不复制到自有大类。

### 16.2 类型、注释与读取

沿用公共规范的 class、明确类型、字段注释和构造器注入；不分散使用 @Value，不预建没有消费者的配置。

### 16.3 明确生效时机

工具有效期配置只影响新申请，已保存 Token 和原提交重放使用原期限。Snowflake 节点坐标在启动时确定，改变需重启；Nacos 支持刷新不代表所有消费者能动态生效。

### 16.4 Nacos 工件与验证

变更同步 Properties、消费者、application.yml 加载入口、模板和配置清单。密码、Token、密钥不写入模板或 Nacos；实例 Snowflake 坐标由部署环境提供。仓库配置检查、绑定验证、真实 Nacos 拉取和推送刷新分别报告；维护模板不授权操作配置中心。
