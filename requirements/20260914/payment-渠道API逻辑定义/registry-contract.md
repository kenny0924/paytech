# 上下文与函数最小注册目录

**公共模型来源修订：** [层级模型设计](./model-library-and-response-scenes.md)允许 Operations 维护公共输入/输出结构，API 显式绑定上下文名称和模型；不再把本页代码注册的 PAYMENT/REFUND 作为唯一上下文来源。系统函数、纯函数与协议实现继续由代码注册，其返回描述符不能在公共模型页任意改写。

**适用边界：** 当前通用合同见[通用执行修订](./generic-execution-amendment.md)。本文 PAYMENT/REFUND、Q1/Q2 及可信身份要求是支付适配的前版示例，不是通用固定目录或必填入参。通用 API 按各自输入 Schema 定义 context；注册函数仍由代码实现并在获准权限范围内执行。

独立脚本方向见[模型整理](./model-and-mapping-design.md)。本文仍只拥有代码注册的上下文、系统数据函数与纯函数；数据库脚本不冒充新增代码函数注册，也不因 sourceCode 引用了函数就自动获得该函数权限。新脚本引擎与允许函数目录尚待补齐，本文现有检查未覆盖 SCRIPT。

本次细化 **2 种上下文、2 个系统数据函数、1 个 Aviator 纯函数**，复用 A02 编辑目录；新增管理接口、页面、表及 SQL 均为 0。这是渠道 API 设计附件，不表示注册器、表达式引擎或函数已经实现。

本文拥有首批目录成员、字段来源及函数业务语义；[定义合同](./definition-contract.md)拥有绑定、类型和表达式结构，[调用合同](./query-contract.md)拥有可信身份、执行及失败边界，[管理 API](./api.md)拥有 A02/A13。机器可读例子见 [registry-catalog.example.json](./assets/registry-catalog.example.json)，它只含 A02 的 contextTypes、functions、expressionProfile 三部分，不是完整响应或导入文件。

## 1. 注册与使用

开发者注册描述符和实现，Operations 读取目录并保存引用。字段选择器根据 Schema 展示字段，函数选择器根据入参 Schema 生成绑定表，根据返回 Schema 插入 `result.xxx`。不执行函数来发现字段，不从运行对象、Java Bean 或真实交易样本自动扩展目录。

| 目录 | Operations 保存什么 | 何时执行 |
|---|---|---|
| 上下文 | contextType 及字段 path，例如 REFUND / ["currency"] | 准备请求时读取调用方投影 |
| 系统数据函数 | functionCode、入参绑定、调用 stepCode，以及每个消费字段的结果表达式 | 执行到该步骤时调用一次；同一步骤的多个消费字段复用结果 |
| Aviator 纯函数 | EXPRESSION 中的函数名、参数表达式及 variables | 对已有值求值，不发起业务查询 |

目录由代码注册，没有新增/编辑函数的管理端点。目录变动必须保留现有引用的兼容性，或显式报告受影响定义；不能删除函数后仍把引用它的配置显示为可执行。保存即生效规则仍适用，不引入发布版本。

### 描述符补充

- `ContextDescriptor = {contextType, contextName, displayName, description, owner, capabilityCode, fields, implementationAvailable}`，fields 为 FieldSet。contextType/contextName 均唯一，contextName 是 UI 根变量名，不是包名或类名。implementationAvailable 指当前环境是否装配该投影适配器，由 capabilityCode 对应的能力注册事实派生。首批 CONTEXT_PAYMENT / CONTEXT_REFUND 缺失进入既有 missingCapabilities，不新增第三套缺失目录。
- FunctionDescriptor 沿用调用合同，增加 `allowedChannelCodes`：null 表示无额外渠道限制，非空数组表示仅允许这些精确 Channel 代码。上下文和渠道限制取交集；本例 Q2 只允许 NI。它不改变 Channel、支付方式、API 或方案的归属模型。
- `identityChecks` 为有序的注册规则代码数组；`missingPolicy` 本期固定 FAIL；`failureContract = {stage, codes}`，stage 固定 FUNCTION，codes 为第 5 节的非空诊断代码集合。这些内容只读，不能由 API 配置关闭。
- PureFunctionDescriptor 增加必填 `argumentOrder`，按位置列出 inputDefinition 中每个参数名，必须恰好各出现一次。JSON 对象键的顺序不作为调用约定。money.minorUnits 的顺序固定 `["amount","currency"]`。
- 本例 `AVIATOR_EXPRESSION`、`MONEY_MINOR_UNITS` 是拟注册 capabilityCode，分别对应表达式运行剖面和金额纯函数。完整 A02 的 capabilities 必须包含对应的类型/阶段/限制描述符；expressionProfile 及纯函数的可用性从同一能力注册事实派生。样例中的 false 是设计占位，不是生产环境探测结果。

## 2. 上下文目录

输入是有限 JSON 对象。页面输入 `refundContext.currency`，持久化为 `{"source":"CONTEXT","path":["currency"]}`；调用数据本身没有外层 refundContext 字段。REFUND 的一个最小输入是：

```json
{"originalTransactionRefId":"3101","refundAmount":"10.00","currency":"AED"}
```

| contextType / contextName | 字段 | 类型及边界 | 数据来源 |
|---|---|---|---|
| PAYMENT / paymentContext | amount | DECIMAL(24,8)，大于 0 | 本次资金 Action 的 amount，不取整笔 Intent 金额代替本次部分 Capture 金额 |
| PAYMENT / paymentContext | currency | STRING，恰好 3 字符 | 同一个 Action 的精确 currency |
| REFUND / refundContext | originalTransactionRefId | STRING，1～19 字符；有效正 long 的十进制文本 | 当前退款 Action.targetChannelTxnRefId |
| REFUND / refundContext | refundAmount | DECIMAL(24,8)，大于 0 | 当前退款 Action.amount |
| REFUND / refundContext | currency | STRING，恰好 3 字符 | 当前退款 Action.currency |

以上 5 个字段均 required=true、nullable=false、dataClass=INTERNAL。金额单位为注册单位 MAJOR_CURRENCY_UNIT；范围为 0.00000001～9999999999999999.99999999。币种还须匹配本次加载的获准币种目录；三字符长度校验不代表币种存在。ID 格式、精确币种及正金额是投影适配器的固定校验，不能由配置改写。

这里 required 表示该上下文投影自身的必备字段；API.requiredPaths 另外记录 API 实际依赖，不能放宽投影合同，也不要求未来新增的可选字段全部出现。NI 本例依赖 REFUND 的全部三个字段。未知字段拒绝，不把整个 Execution Snapshot 序列化后交给表达式。

Client、当前 Action、业务根、冻结 Channel/Account/MID/OE 和支付方式属于 InvocationIdentity，不重复出现在这些普通字段里。入口适配器从已授权事实产生投影，核对金额、币种和原目标，不能接受自报 JSON 来改变本次资金指令。系统函数返回数据也不能覆盖它们。

上述投影来源在[阶段 2 历史核查](./gap-analysis.md#1-本轮核查入口)中曾以 ChannelRequest、请求装配和 ExecutionSnapshots 为证据；这些旧源码路径已在后续重构中变化。当前实现与迁移状态以[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)为准，落地时须按当前 Owner 合同重新核对适配，不把旧类名当作现行实现。本文字段是拟注册输入合同，不表示已有 JSON 投影注册器。

PAYMENT 当前只定义普通金额/币种投影，不宣称覆盖所有支付协议字段；卡原值和受控卡材料继续走 Card 专项边界。没有具体消费方时，不预加 consumer、card、device、order 全量对象。

## 3. 系统数据函数目录

两个函数的 inputDefinition 都只有 `originalTransactionRefId`，类型与退款上下文一致。Client 和当前 Action 从可信调用身份取得，不出现在可编辑的入参表。每次调用最多 2000ms、返回最多 65,536 字节，受总 deadline 收紧；freshness=IMMUTABLE_FACT 表示返回已冻结事实，不承诺多 Owner 当前读事务的一致快照。

| functionCode | Owner / 限制 | 返回字段 | 采用条件 |
|---|---|---|---|
| READ_ORIGINAL_CHANNEL_TRANSACTION | Execution；REFUND；不额外限制渠道 | channelTransactionRefId、paymentActionId、referenceType、externalTransactionId、amount、currency | 渠道只需原交易的不透明 externalTransactionId 等既有事实 |
| READ_REFUND_TARGET | Channel（依赖 Execution 事实）；REFUND；仅 NI | originalTransactionRefId、referenceType、outletId、orderRef、paymentRef、captureId、currency | 当前 NI Refund a Capture 的四段路径导航 |

两个函数返回值都是这些字段组成的对象，无额外 value 层；**CALL_SYSTEM_FUNCTION 能力**再包装为 `{value: typedResult}`。例如调用 originalTarget 配置 `outputBindings={"target":["value"]}`，字段取值表达式 `result.orderRef` 的 result 指向该步骤的 target。不要写成 `result.value.orderRef`。

返回字段均必填、非 null、INTERNAL：ID 为正 long 文本；金额为 DECIMAL(24,8) 正值；currency 为获准三位币种；referenceType 为非空 STRING(64)，按固定语义校验。Q1 用于退款原目标时只接受 SALE/CAPTURE，Q2 只接受 CAPTURE；externalTransactionId 为非空 STRING(255)。NI 四段坐标暂用非空 STRING(128) 作为本配置系统的接纳上限，**不是 NI 官方长度承诺**；生产接入前必须核对实际协议样本，路径发送仍逐段编码。

### 身份与来源校验

1. `CURRENT_ACTION_OWNED_BY_CLIENT`：读取可信 Client 下的当前 Action；校验其业务根与 InvocationIdentity 一致。
2. `TARGET_REFERENCE_MATCHES_ARGUMENT`：通过现有 targetReference(clientId, actionId) 取得原引用，且引用 ID 等于函数实参；不能按任意传入 ID 直接查询其他交易。
3. `FROZEN_ROUTE_MATCHES`：读取原引用关联 Action/Attempt 等 Owner 事实，核对冻结 Channel/Account/MID、OE 和精确支付方式。Ref 自身没有 Account/OE 字段，不能只检查 Ref 的 Channel/MID 就宣称完整匹配。
4. `REFUND_ORIGINAL_FUNDS_MATCHES`：原引用类型符合函数限制，原币种与本次 Action 一致；查询不替代 Execution 的退款受理、额度预占或业务状态判断。
5. Q2 额外执行 `NI_CAPTURE_COORDINATES_MATCHES`：四段坐标必须来自同一个经核验的原 Capture，禁止跨记录拼接、从不透明 externalTransactionId 猜测或再向 NI 查订单。

Q1 的基础事实可由现有 [PaymentExecutionService](../../../payment-orchestration-service/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/PaymentExecutionService.java)公开查询取得，但函数适配与注册仍待实现。Q2 尚缺真实 NI 导航事实提供方；两个描述符示例均为 implementationAvailable=false，不因底层 Service 存在就标为可执行。

NI 本例只配置 Q2 一次，四个 Path 字段引用同一个 originalTarget。Q2 内部的 Owner 读取由其合同管理；本例不额外配置一个 Q1 步骤重复读取。显式配置两个调用步骤则按两次计费时限与调用预算，不隐式合并。

## 4. Aviator 纯函数目录

首批只登记 `money.minorUnits(amount, currency)`；常量、字段访问、字符串拼接及条件表达式使用已限定的语言运算，不再包装成一组工具函数。日期格式、枚举映射、数组选择等有实际消费后再添加有类型合同。

| 项 | 合同 |
|---|---|
| name / capabilityCode | money.minorUnits / MONEY_MINOR_UNITS |
| 参数顺序 | amount、currency |
| amount | DECIMAL(24,8)，0～9999999999999999.99999999，MAJOR_CURRENCY_UNIT |
| currency | 获准三位币种；本次 prepare 冻结的有限币种精度目录 |
| outputDefinition.value | INTEGER，0～999999999999999999999999，MINOR_CURRENCY_UNIT；这是项目计算上限，目标协议可更窄 |
| 副作用 | 无 I/O、不读业务服务、不修改入参；同一输入和同一冻结币种目录返回同一值 |
| 精度 | 精确乘以 10 的币种小数位次方，结果必须可无损表示为整数；不使用二进制浮点数，不舍入 |
| 可用性 | 示例 false；AVIATOR_EXPRESSION 与 MONEY_MINOR_UNITS 均需真实实现 |

本剖面接受币种小数位 0～8；币种目录缺失、币种不存在、精度不受支持、不能无损转整数或超出结果边界均失败。目录在 prepare 读取一次，表达式不现场查询币种或在本次调用中途切换精度。`10.000` 在两位精度下可无损表示为 1000，`10.001` 不可；拒绝非零的多余精度，不把尾随零当舍入。

第一方金额 JSON 使用字符串。Aviator 变量依据注册类型转换为精确 Decimal/Integer；普通 STRING 不因为长得像金额就自动变成数字。函数结果的第一方展示是整数文本，NI JSON 编码器按目标 INTEGER 输出数值 token `1000`，不是字符串 `"1000"`，也不先转 JavaScript Number。

参数名和顺序使补全工具可以插入 `money.minorUnits(amount, currency)`，再让用户用目录变量替换实参。示例表达式为 `money.minorUnits(refundContext.refundAmount, refundContext.currency)`；函数返回值也可组合使用，例如 `money.minorUnits(result.amount, result.currency)` 读取 Q1 的原交易金额，不能因此替换本次 refundAmount。

## 5. 缺失与失败

| 情况 | 诊断 / 行为 |
|---|---|
| 引用未注册上下文/字段/函数、渠道或上下文类型不匹配 | 保存失败；UNKNOWN_CONTEXT、UNKNOWN_FIELD、UNKNOWN_FUNCTION、FUNCTION_SCOPE_MISMATCH |
| 已有描述符但投影/函数/表达式实现缺失 | 结构有效可保存，显示缺失项；prepare 在任何业务查询/发送前失败，sendState=NOT_SENT。投影缺失用 CONTEXT_UNAVAILABLE，data function 缺失用 FUNCTION_UNAVAILABLE，能力缺失用 CAPABILITY_UNAVAILABLE |
| 函数入参非法、原目标未找到、不唯一、部分数据、身份冲突、超时或 Owner 不可用 | failureStage=FUNCTION；分别 FUNCTION_ARGUMENT_INVALID、FUNCTION_NOT_FOUND、FUNCTION_AMBIGUOUS、FUNCTION_RESULT_INVALID、FUNCTION_IDENTITY_MISMATCH、FUNCTION_TIMEOUT、FUNCTION_OWNER_UNAVAILABLE；不返回默认对象 |
| 表达式求值失败、目标类型错误、数值不能无损转换、超时 | 请求期 failureStage=REQUEST；响应期为 RESPONSE；分别 EXPRESSION_EVALUATION、EXPRESSION_TYPE、EXPRESSION_PRECISION、EXPRESSION_LIMIT；币种/目录不支持另用 EXPRESSION_CURRENCY_UNAVAILABLE |

函数未找到交易时不区分向页面透露其他 Client 是否拥有该 ID；只返回允许的错误代码与字段位置。不能通过置为可选吞掉函数或表达式异常。运行实现与静态编译检查器分别判断可用性。编译检查器不可用时，A13 只能检查结构与目录引用，报告 EXPRESSION_COMPILE_UNAVAILABLE 错误且 valid=false；只有静态检查完整通过、仅缺运行实现时，才能按缺能力警告保存。本文的例子核查不是 A13 或 Aviator 编译结果。

## 6. NI 退款逐项核对

精确绑定片段见 [ni-refund-mapping.example.json](./assets/ni-refund-mapping.example.json)。它包含 InputDefinition、CALL_SYSTEM_FUNCTION Step 及各字段的 ValueBinding；外层 fieldMappings 仅为本设计核对表，不是新的持久化模型。它不包含完整 codec/保护/响应能力目录，不能当成可直接保存的完整 API。

| 位置 | 配置 | 10.00 AED 合成输入的结果 |
|---|---|---|
| Q2.originalTransactionRefId | refundContext.originalTransactionRefId | "3101"，查询一次 |
| Path.outletId / orderRef / paymentRef / captureId | 系统函数返回值：result.outletId / result.orderRef / result.paymentRef / result.captureId | 来自同一个 originalTarget.target；四次取值不等于查询四次 |
| Body.amount.currencyCode | refundContext.currency | "AED" |
| Body.amount.value | money.minorUnits(refundContext.refundAmount, refundContext.currency) | 精确 INTEGER 1000 |
| Content-Type / Accept | 表达式字面量 'application/vnd.ni-payment.v2+json' | 两个固定 Header |
| Authorization | NI_PAYMENT_BEARER 显式方案引用，使用冻结 Account 的受控材料 | 只进入获准 Header，普通表达式看不到 Token |
| POST / JSON 编码 | Body 对象按目标类型编码，Path 参数逐段编码 | 一次退款请求，完整协议说明见 NI 样例 |
| HTTP 200 / 解析及身份校验 | 成功完成解码、白名单投影及父级匹配后匹配结果规则 | resultCode=UNRESOLVED，输出观察到的状态、两条退款记录、父 Payment 引用及响应媒体类型 |
| 非 200 | 不强求成功响应字段，默认结果分支 | UNRECOGNIZED；保留执行事实，输出为空 |

请求前先校验全部依赖的实现可用性。当前描述符例子会在预检停止；表内后续值是合成输入的设计预期，不是已运行的函数、Aviator 或 NI 测试。

本次核对没有消除三个接入缺口：Q2 的 NI 原交易导航事实来源；当前请求与退款记录的唯一关联合同；获准支付方式/实际协议接纳边界和运行材料。金额/字段目录与[后续补齐的协议处理合同](./capability-contract.md)共同定义完整配置内容；运行实现仍待接入，不能把 resultCode=UNRESOLVED 改成退款成功。

## 7. 可复验范围

执行 `python3 requirements/20260914/payment-渠道API逻辑定义/assets/check-registry-examples.py`，检查目录结构、参数顺序、字段引用、同次函数复用、表达式变量引用和合成金额向量；报告见 [registry-verification.json](./assets/registry-verification.json)。脚本不会连接 Owner/NI、不解释执行 Aviator、不模拟整个渠道引擎；算术向量用 Python Decimal 独立验证预期值。
