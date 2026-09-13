# Execution 依赖 Mock 合同

> **交易级 Token 修订评审：** 用户已确认统一 pay、交易级工具 Token、Attempt 直接关联及删除旧 Instrument 的方向；详细合同见[修订稿](./instrument-token-amendment.md)。下文保留上一版合同与评审上下文，受影响部分以修订稿的准确取代范围为准；新合同的实现与验证状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

> 本期只设计 Execution；下列接口是 Payment Web 的类型化依赖边界与测试场景，不表示相应 Owner API、数据库、消息或真实渠道已实现。正式接入时由 Web 适配 Owner 合同，Execution 不能直接依赖兄弟业务 JAR。

## 1. 装配与替换规则

Mock 仅在 `paytech-payment-test` 的测试装配中注册。场景必须由测试预置，缺场景返回不可用，不能默认 ALLOW/SUCCEEDED。测试沿真实 Execution Service、Repository、状态机、金额规则及本地技术表运行；禁止把整段业务方法替换成返回值来声称验证流程。

每个替身按测试 namespace 隔离状态，并记录调用次数、稳定业务 ID、顺序、事务是否活动、请求身份是否一致；记录器必须脱敏，不记录 cardUseSessionRef、URL 或将来真实卡输入。支持屏障/挂起点制造竞争、结果丢失和重启恢复。重启 Execution 时，Channel Mock 的外部交易事实保留，用以证明原操作仍可查询；不得把两边状态同时清空掩盖恢复问题。

真实协议尚未定稿时使用类型化、有状态的模块替身；协议定稿后换 HTTP/MQ Simulator 验证真实传输。Foundation MySQL/Redis/补偿机制不是外部业务 Owner，不用 Mock 绕开其最终集成验证。生产缺真实依赖显式启动失败或接口不可用；不得启用测试场景配置作为默认成功路径。

## 2. Owner 合同矩阵

金额、ID、时间在 Owner 内部模型中使用项目类型，HTTP 映射遵循[API 约定](./api-contract.md)。`Result` 使用互斥的类型化结果，不把缺值解释为成功。

| Owner / Web 端口 | 输入 | 返回 | 必备场景 |
|---|---|---|---|
| Commerce context `loadFrozenContext` | sourceOrderContextId、sourceOrderId、clientId、Intent 冻结版本 | 精确 entityNo、商业版本、金额/币种、consumerCountry、已冻结法律接受事实；不回调 Commerce 确认 | 完整、跨 Client、错 OE、错接受集合、过期、查无或不可用 |
| 当前资格 `checkExecutionEligibility`（CCT/Risk/Commerce） | Intent/Attempt、目标 Action 类型、原授权引用、当前 orderVersion | ALLOWED 或明确拒绝/不可用；含评估时间/有效期及 Owner 版本，AUTH 场景含原授权是否可用 | 开放、Order 关闭、OE 关闭、Compliance 限制、版本变更、依赖失败；不伪造数值版本 |
| Risk `assessPayment` | 原 assessmentId、Intent/Attempt、精确方式/目标、受限风险上下文引用 | ALLOW + authenticationRequirement；或 REVIEW/DENY/FAILED/TIMED_OUT | 无认证、普通 3DS、强制 Challenge、拒绝、复核、超时；同 assessmentId 内容不变且可查原结果 |
| Routing `selectRoute` | Attempt、冻结 OE、精确 method、金额/币种、requiredCapabilities | 单一 RouteSelection：channelId、accountId、midId、entityNo、方法和明确能力；或 NO_CANDIDATE/UNAVAILABLE | 同 OE 合格、无候选、错 OE、方式不符、能力缺失、同 Attempt 返回冲突结果 |
| Card `inspectCard` / `acquireUseContext` | clientId、Intent/Attempt、cardReference、可选瞬时 cardUseSessionRef、目标用途 | SAFE_DESCRIPTORS；USABLE 的瞬时用卡上下文，或 INPUT_REQUIRED/UNAVAILABLE | 合成引用正常、错作用域、引用失效、材料过期、恢复缺输入、补输入成功 |
| Channel `authenticate` | 原 AUTHENTICATE Action 身份、冻结路由、认证要求、金额币种、瞬时 Card 上下文 | AUTHENTICATED/ATTEMPTED/FAILED/UNAVAILABLE 或 WAITING/UNKNOWN；稳定 operationRef、适用期限与 challengePerformed | 无感、挑战、挑战失败/到期、结果丢失、强制挑战却仅无感、原引用可只读恢复 |
| Channel `executeFunds` | 原资金 Action、稳定 key、路由坐标、原资金引用?、认证证明引用?、适用瞬时 Card 上下文 | SUCCESS/DECLINED/UNKNOWN；成功资金引用、实际金额币种/时间、可信定价描述 | SALE/AUTHORIZE/CAPTURE/VOID/REFUND 成功、明确失败、超时后成功、超时后仍未知 |
| Channel `queryOperation` / `readInteraction` | 原 Action + account + 原 key + operationRef? | 原操作当前可信结果；或现存安全指令、NOT_FOUND/UNAVAILABLE | 无 txnRef 也能按原 key 查询；晚到成功；重复/乱序；单次 NOT_FOUND 后原请求成功 |
| 事实接收器（Commerce/Finance） | 本地事实发布 Handler 产生的安全事件 | 观察接收或模拟失败/响应丢失 | 重复投递、Confirm 丢失、收款与授权区分、UNKNOWN 不出现退款失败 |

Card 的 USABLE 不是可存入任意 Context 的通用对象；只允许当次事务外调用透传给指定适配器，使用结束即释放引用。Mock 不保存或构造真实 PAN/CVV，不通过测试证明未来真实卡数据处理已满足要求。

## 3. Routing 和认证能力

requiredCapabilities 是类型化需求集合：PAY_NOW 要 SALE；AUTHORIZE_ONLY 要 AUTHORIZE + 后续 CAPTURE + VOID；需要 3DS 时还要 **独立认证、认证后指定资金动作、原认证查询**；强制 Challenge 要显式可请求挑战及可信结果证明。完整支付还要求原资金操作查询与退款能力。不能用一个 `supports3ds=true` 代替这些不同能力。

RouteSelection 只能冻结一次。当前替身不写 Routing 的 t_routing_decision；Execution 在创建第一个 Action 时冻结该 Action 的坐标，后续复制原 Action。真实 Routing 仍拥有唯一 Attempt 决策和证据，接入 TODO 必须核对其唯一结论与 Execution Action 一致，不把候选排序/路由规则表搬进 Execution。

Channel 能力还须明确 `fundsIdempotency = GUARANTEED / NOT_GUARANTEED` 及其幂等期限、按 key 查询能力、认证证明可用期限。Mock 两种幂等模式都测试；未知模式视为 NOT_GUARANTEED。需要新幂等期限或跨账户证明转用时另行设计，不从 Mock 通用能力推断真实 NI。

本期只支持预先由内部 Risk 决定认证。若资金调用得到渠道主动要求额外认证：适配器能保证未产生资金才返回受控不支持失败；不能保证则 UNKNOWN 查询。不得在资金状态不明时再开认证/另路由。

## 4. 归一化结果约束

`ChannelResult` 公共部分：`actionId, channelId, accountId, midId, channelIdempotencyKey, operationRef?, result, amount, currency, occurredAt`。result 只能为 `SUCCESS / DECLINED / WAITING / UNKNOWN / NOT_FOUND`，NOT_FOUND 仅查询返回，不等于确认未执行。

认证结果的附加记录包含 `authenticationOutcome, challengePerformed?, interactionType?, interactionExpiresAt?, authenticationValidUntil?`；WAITING 要稳定操作引用及等待期限，SUCCESS 要可信 outcome 和证明有效期。临时指令单独返回，不能挂到需持久化的 ChannelResult、补偿 payload 或状态历史上。

资金 SUCCESS 要 `transactionReference` 及与请求一致的金额/币种；退款/捕获/撤销还要原资金关联。适用定价事实为类型化记录；即使缺少某些定价字段，真实成功资金仍先落库。无法关联、跨 Account/MID、错金额/币种、伪造来源结果均拒绝应用，保持原未决状态并记录安全诊断。

## 5. 替换 TODO 与完成条件

| ID | 责任 Owner | 接入完成条件 |
|---|---|---|
| TODO-EX-COM | Commerce + Payment Web/Card | 现有 CheckoutCardPaymentPort 的回执、requestHash、原 submission、瞬时 CardInput 与新 Execution 合同逐项映射；不新增反向接受确认；Cancel/关闭及 Reversal 退款接续联调 |
| TODO-EX-RISK | Risk + CCT + Commerce | 风险上下文最小合同、原评估查询、实时资格版本/有效期和 OE 关闭事实接入；不得默认通过 |
| TODO-EX-ROUTE | Routing | 真正的候选能力、唯一决策持久化、同 OE 冻结及原 Action 沿路恢复证明 |
| TODO-EX-CARD | Card | 稳定 Card 引用/作用域、瞬时使用材料与补输入合同、授权取用和日志边界验证；真实卡数据专项实施另行授权 |
| TODO-EX-CHANNEL | Channel | 验证目标渠道独立认证/资金语义、key 幂等保证与期限、操作查询、验签/回调、稳定非 bearer 引用、认证与资金事实映射 |
| TODO-EX-FACT | Commerce + Finance | 正式事件 Wire、消息路由/幂等、晚到 Pricing Fact、重复收款补偿的会计闭环；不能用 Mock 接收代替账务验证 |

以上 TODO 不是 Execution 规则的替代：Attempt/Action 幂等、状态、额度、可靠登记、成功唯一性与错误映射必须在后续本地切片中真实实现。未完成真实 Owner 接入时，只能交付明确标注的本地模拟切片。
