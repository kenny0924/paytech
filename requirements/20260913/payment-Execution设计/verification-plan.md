# Execution 研发与验收计划

> **2026-09-15 删除来源 Binding：** 用户已确认删除 `t_payment_intent_binding` 和 EX-I03 `CloseCreation`，按已有 Intent 终止；创建结果未明确时由 Commerce 用原键恢复创建后再终止。本文涉及提前关闭、Binding 门禁和锁顺序的内容由[字段修订第 8 节](./execution-field-simplification-amendment.md#8-删除来源-binding)取代；其余历史基线保持。

> **字段维护后继：** [整批精简修订](./execution-field-simplification-amendment.md)已取代本文受影响的字段、渠道绑定位置、认证明细、Pricing Fact 与状态历史表要求。本文旧接口/流程细节保留为迁移前基线；后续 Java/HTTP/MQ 改造须逐项对齐，数据库执行状态只见当前台账。

> **状态与 Channel 恢复后继：** capture mode、三表 4/4/5 状态、active_action_id 及移除 QUERY 的现行设计目标以[状态与恢复修订](./execution-state-channel-recovery-amendment.md)为准；下文受影响内容保留为前序基线，未涉及合同继续有效。实现与 SQL 状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

> **交易级 Token 修订评审：** 用户已确认统一 pay、交易级工具 Token、Attempt 直接关联及删除旧 Instrument 的方向；详细合同见[修订稿](./instrument-token-amendment.md)。下文保留上一版合同与评审上下文，受影响部分以修订稿的准确取代范围为准；新合同的实现与验证状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

> 阶段 3 已获用户确认，当前进入阶段 4 Execution 本地切片研发；其他 Owner 仅测试 Mock + TODO。实际实现与验证边界见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)，阶段确认见[交付记录](./delivery.md)。

## 1. 获批后的实施顺序

| 顺序 | Payment 内交付 | 通过条件 |
|---|---|---|
| 1 | 将已批准字段/注释/索引及表 Owner 同步 PDManer、生成器、Schema；准备人工 SQL | 模型差异及 SQL 一致；识别实际目标库和数据前置，未获授权不执行 |
| 2 | Execution 的 Intent/来源绑定、Attempt/Action、退款额度和 Repository 写边界；Web 短事务命令幂等/补偿装配 | 最小并发/状态反例测试先失败，再由真实业务实现通过 |
| 3 | Web 类型化流程分发、Card 首次支付、Owner Mock 场景 | 无认证 SALE、3DS 授权、补输入/未知恢复跑通；未支持方式不回退 Card |
| 4 | Capture、内部 Void、Refund、Reversal；可信结果应用与重复成功处理 | 原授权/支付事实不被失败覆盖，额度及唯一成功约束通过 |
| 5 | 12 个 API 的 DTO、错误/幂等/授权投影；只读查询；安全 nextAction | 契约/字段/权限检查通过，GET 不产生资金操作，日志无敏感值 |
| 6 | 可靠发布/恢复、定价补齐、重启/并发验证、Owner TODO 清单 | 已批准 Execution 切片全部验收标准有运行证据，真实 Owner 仍明确隔离 |

只改 Payment 实现仓库和获批的模型/需求入口；其他系统仍 Mock + TODO。阶段 4 不是自动执行 SQL、配置生产凭据、提交、推送或部署授权。阶段 3 确认前不产生上述业务类。

## 2. 关键验收场景

每个场景同时观察：HTTP/Service 输出、Payment 实际数据库行与版本、待发布事实、Owner 调用次数和参数身份。金额断言不能只检查最终 status。

| 编号 | 输入/竞争 | 必须观察到的结果 | PEX |
|---|---|---|---|
| V01 | 无认证 PAY_NOW | 单 Attempt、单 SALE；SUCCESS 后单资金引用/Position/PaymentSucceeded | 01、02、09 |
| V02 | 强制 3DS + AUTHORIZE_ONLY | AUTHENTICATE→AUTHORIZE；只有 AUTHORIZED，captured=0，无收款事件 | 01、02、06、07 |
| V03 | 强制 Challenge 却收到无感成功；挑战失败/到期 | 保存实际认证事实但不执行资金；要求不满足有安全原因 | 02、07 |
| V04 | Risk 拒绝/复核/不可用、Routing 无候选/错 OE | 无资金调用；失败 Attempt 可诊断，未配置场景不默认成功 | 02、08、11 |
| V05 | 同 submission 并发、同 key 异内容、新 key 重复 source | 同内容只受理一 Attempt；冲突拒绝，不改变原 Card/目标/接受事实 | 03、05 |
| V06 | 创建超时后原键恢复再终止、同来源重复创建、pay 与终止并发 | 唯一 Intent；终止仅作用于已有 Intent，未决资金返回 PENDING，不谎报取消成功；没有 Binding 或 CloseCreation | 03、05 |
| V07 | 认证/资金等待中切 Wallet 或新 Card | 新 Attempt 被活动/UNKNOWN 占用挡住，原查询/结果仍可完成 | 04、05、13 |
| V08 | Capture 成功/失败/UNKNOWN；新 key 重复 Capture | 成功只一次全额；失败保留 authorized；UNKNOWN 不再 Capture/新支付 | 06、09 |
| V09 | Capture 与 Reversal 同时到 | 锁内只选择一个资金分支；Void 已接受后不再允许 Capture，Void 失败保留授权 | 05、06 |
| V10 | 两笔各 70 的退款竞争原金额 100 | 至多一笔预占成功；UNKNOWN 占用不释放，成功/失败重复回调只更新一次额度 | 06、09 |
| V11 | 已扣款 Reversal、已有部分退款、已有预占 | REFUND_REQUIRED 由 Commerce Mock 建标准 Refund；不私造 ID；超额/不适用拦截 | 06、12 |
| V12 | T3 后发送前/发送后分别崩溃 | 均按可能已发恢复；同 key 幂等模式无二次资金效果，非幂等模式不盲重发 | 03、10 |
| V13 | 无 channelOperationRef、QUERY NOT_FOUND 后迟到成功 | 能按原 key 查询；不因 NOT_FOUND 创建第二个资金动作 | 03、08、10 |
| V14 | 3DS 后丢失瞬时用卡材料、补输入、原 key 换卡 | 原提交 PROVIDE_CARD_INPUT；补正确材料续原流程，换卡冲突；DB/日志/任务无材料 | 02、03、07、12 |
| V15 | 成功回调先到、等待响应后到、同通知重复、浏览器伪造成功 | 成功不降级，不重复历史/事实；伪造输入不修改资金或认证事实 | 07、09 |
| V16 | 第一笔成功后原失败动作出现第二笔真实扣款 | canonical 指针不变；单 Duplicate Case + 单补偿退款；无第二次 PaymentSucceeded | 09、10 |
| V17 | 支付成功但定价字段缺失，随后补齐 | 收款始终成功；EX-I11 PENDING→READY，单 Fact，不猜分类/费率 | 09 |
| V18 | 消息 Confirm 丢失；恢复任务超限 | 稳定 eventId 重投；消费者只应用一次；任务 FAILED 不释放 UNKNOWN 资金占用 | 09、10 |
| V19 | 方式无匹配/重复注册/测试侧非 Card 流程 | 无匹配拒绝、重复启动失败；非 Card 替身不被强迫 3DS；原 Card 恢复不选新路由 | 04、13 |
| V20 | 跨 Client/来源/Account/MID 的查询/回调、错金额 | 拒绝泄露/应用；原事实不变；当前资格依赖失败阻止首次资金提交 | 05、08、12 |
| V21 | 所有外部依赖观察事务、Redis 锁失效后双执行 | 外部调用无数据库活动事务；数据库 CAS/唯一/额度规则仍防重复 | 03、10、11 |

## 3. 代表性持久化记录

以下 ID 为说明性短号，金额均为 AED 100；省略公共字段，实际测试用 Builder 完整构造。

| 场景 | 关键业务行 | 不应出现的数据 |
|---|---|---|
| 直接扣款 | B(订单201→I601,开放)；A701(SUCCEEDED,goal=PAY_NOW,captured=100)；X801(SALE,SUCCEEDED)；T901(X801)；P1001(original=100,reserved=0,refunded=0) | AUTHENTICATE、Customer Action、伪造 Instrument |
| 认证后授权 | A702(AUTHORIZED,authorized=100,captured=0,auth=CHALLENGE_REQUIRED)；X802(AUTHENTICATE,SUCCEEDED,challenge=1)；X803(AUTHORIZE,SUCCEEDED,authentication_action_id=802)；T902(X803) | 收款事件、Refund Position、收款 Pricing Fact |
| 后续 Capture | 原 A702；X804(CAPTURE,SUCCEEDED,target_ref=902)；T903(X804,原授权=902)；P1002(original=100) | 新 Attempt、重新 Routing、丢弃原授权 T902 |
| 授权 Void | 另一原授权 A703；X806(VOID,SUCCEEDED,target_ref=904)；A703 VOIDED；Intent CANCELLED | Commerce Refund、退款预占、Finance Journal |
| 退款未知 | R1101(commerceRefund=1201,UNKNOWN,amount=40)；X807(REFUND,UNKNOWN,target_ref=903)；P1002(reserved=40,refunded=0) | RefundFailed、释放预占、另一笔同身份 Refund |
| 认证等待 | X808(AUTHENTICATE,REQUIRES_ACTION,operation_ref=非秘密引用,interaction=THREE_DS_CHALLENGE,expires=确定时刻) | 交互表、URL、SDK Token、CVV、恢复 payload 中的 Card 引用 |

## 4. 验证环境与证据边界

遵循[测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)：纯状态转换/匹配/金额算法可以聚焦单测；跨层用公开 Service/HTTP 入口走真实 Execution 与本地数据库，使用仓库 `local-environment` MySQL/Redis/RabbitMQ。缺依赖应快速失败并提供设置指引；不用 Flyway、Testcontainers 或启动建表。

基础测试入口扩展现有[模块边界测试](../../../paytech/payment/paytech-payment-test/src/test/java/com/paytech/payment/auto/architecture)与[应用自动测试](../../../paytech/payment/paytech-payment-test/src/test/java/com/paytech/payment/auto)。新的用例测试按项目 auto/manual 和 Builder/Simulator/Checker 规范落位，实际 Maven 命令由研发时已存在的测试类与 POM 决定，不在本稿给出尚不存在类的“已通过”命令。

获批后验证制品包括：模型/DDL 差异报告、构建结果、场景运行记录、脱敏调用/数据断言、并发及重启证据、未接入 Owner 清单。若本地数据库变更尚未授权，先完成代码和可独立验证部分，数据库集成保留为明确未验收项，不能以纯 Mock 测试宣布完成。
