# 业务上下文、系统函数与单次调用合同

**本文是通用化前的调用草稿。** 当前合同统一见[通用执行修订](./generic-execution-amendment.md)：execute 仅需要 integrationCode、apiCode 和 API 声明的 context；删除固定 InvocationIdentity、必填 channelIdempotencyKey、对外 prepare/execute 及旧返回外壳。下文业务门禁、身份和恢复协调不得作为通用执行器要求；代码函数的只读、受控输入和明确返回合同继续适用。

本文归属[详细设计](./design.md)，定义配置运行所依赖的本地能力，不新增公网执行 API、不改变 Execution HTTP 合同，也不要求调用者事先补齐所有渠道字段。

## 1. 可信身份和业务数据分开

调用输入由 Web 的受控适配器组装，分为三部分：

| 部分 | 必需内容 | 约束 |
|---|---|---|
| API 身份 | channelCode、paymentMethodCode、apiCode | 精确定位一份当前定义，不接收配置正文替代持久化定义 |
| 可信 InvocationIdentity | Client ID、业务根/当前 Action ID、冻结 Channel/Account/MID、OE、支付方式 | 从已授权业务事实得到；不是从 JSON 自由对象中自报身份 |
| 业务上下文 | contextType 与该类型允许的字段 | 由调用方拥有的显式投影生成；只读，保留缺失/null/零差别 |

首批上下文为 PAYMENT（amount、currency）和 REFUND（originalTransactionRefId、refundAmount、currency），完整类型和来源见[最小注册目录](./registry-contract.md)。调用身份及冻结路由留在 InvocationIdentity，不复制进普通上下文；PAN/CVV 不进入该投影。具体 API 通过 requiredPaths 声明依赖；它不能放宽上下文自身的必需字段合同。

运行材料另由 `RuntimeMaterialProvider` 按可信冻结 Account 解析 endpointCode、允许属性与凭据用途。账户停用及特殊后续资金行为按其 Owner 合同判断；不能把“当前路由可用”机械套为原退款可执行。需要远程材料服务时在事务外调用。

## 2. 开发者实现并注册的系统函数

查询方法作为系统函数注册，Operations 只使用函数，不维护它的实现或查询策略。本期数据函数仍限于既定的只读业务数据获取范围；字段处理按 Aviator 合同收敛，编码和保护能力的边界保持不变，不因统一的函数使用方式开放任意代码。

每个 FunctionDescriptor 必须包含以下内容；它们来自代码/受控目录，不是 Operations 可以修改的函数定义：

| 字段 | 含义 |
|---|---|
| functionCode / displayName / description | 稳定函数标识及使用者可理解的用途 |
| owner | 事实所属模块/系统，不是可由配置选择的数据库 |
| allowedContextTypes / allowedChannelCodes | 获准上下文与渠道范围；渠道 null 表示无额外限制，非空数组限制精确 Channel 代码，两项取交集 |
| inputDefinition / outputDefinition | 完整有类型字段、必填/可空、单位、大小、数据分类；不是返回任意对象 |
| identityChecks | 必须与 InvocationIdentity 匹配的 Client、原交易及冻结路由规则 |
| freshness | IMMUTABLE_FACT 或 CURRENT_READ；当前读取不冒充多 Owner 的一致快照 |
| timeoutMs / maxResultBytes | 最大 2000ms / 65,536 字节；调用剩余 deadline 可以进一步收紧 |
| implementationAvailable | 当前环境是否装配真实实现；与描述符存在性分开 |
| missingPolicy / failureContract | 缺失/null、未找到或不唯一时的既定行为；函数实现负责，页面只读展示 |

注册器拒绝重复 functionCode、无身份校验合同或非只读数据函数。部署必须使各实例目录兼容；含不同描述符或实现集合的实例不得被同时视为可执行。配置保存校验不能靠一个实例有插件就承诺所有实例已具备能力。

执行端口概念形状为 `invoke(functionCode, typedArguments, trustedIdentity) -> typedResult`。注册实现可以通过 Owner 的已发布 Service/HTTP 读取数据；不能越过模块边界返回 DataObject，不能创建退款、刷新支付状态、发送第二个渠道请求或访问任意 URL。端口在 Channel 定义，跨模块实现与装配放在 Web，Channel 不引入 Execution JAR 依赖。

输入输出使用[FieldSet/FieldDefinition](./definition-contract.md#31-类型合同的固定形状)。系统函数返回 typedResult，CALL_SYSTEM_FUNCTION 能力对外提供 `{value: typedResult}`，API 可将 value 命名为 target，再引用其明确子字段。字段选择工具读取函数返回 Schema 展示树形字段，不执行查询来发现结构；配置阶段只记录绑定，运行阶段读取当次返回值。

函数调用由现有 Step 表达，参数、返回值别名及字段引用沿用定义合同。统一取值工具允许上下文路径、Aviator 表达式及系统函数；函数成功返回后，以 result 为变量执行当前字段的返回值表达式，具体结构、精度和运行剖面见[定义合同第 3.2 节](./definition-contract.md#32-aviator-取值与处理)。业务数据函数不直接注册到 Aviator 中；表达式调用的纯函数只处理已有值。多个位置引用同一调用步骤只执行一次，各字段的结果表达式分别求值；不同调用步骤不自动合并。调用条件不再由“什么时候查询 / 先检查哪个字段”表单维护；如有接纳已有值的真实需求，应由开发者注册具备明确入参及处理语义的函数，不能恢复自由查询策略表单。

## 3. 首批数据函数

[最小注册目录](./registry-contract.md#3-系统数据函数目录)集中定义 Q1 READ_ORIGINAL_CHANNEL_TRANSACTION 与 Q2 READ_REFUND_TARGET 的参数、返回字段、类型、渠道/上下文范围、身份规则和失败语义；此处不再维护第二份成员合同。

Q1 使用 Execution 现有公开查询的事实，但尚需函数适配与注册；Q2 只用于本例 NI Capture 退款，缺少真实 NI 导航事实提供方。描述符示例均标为 implementationAvailable=false，配置能够引用不代表运行可用。完整返回对象经 CALL_SYSTEM_FUNCTION 包装在 value 中，后续字段统一通过 result 表达式取值。

## 4. prepare / execute

这是 Channel Owner 的本地能力边界，最终 Java 类型遵守项目 Service 接口与实现规则，不生成独立的 HTTP 执行入口。

```text
prepare(apiIdentity, businessContext, trustedIdentity)
  读取完整当前 API、引用方案和脚本 → 结束只读事务
  校验基础字段与实现可用性
  按需读取运行材料、执行配置引用的系统函数
  核对完整请求、编码及保护处理
  返回 PreparedCall 或 PrepareFailure(NOT_SENT)

execute(preparedCall)
  原子领取该对象的一次使用权
  检查 deadline 与受控材料仍可用
  发送一次目标渠道请求
  响应保护/解析/投影
  返回 ChannelApiResult
  释放瞬时材料
```

PreparedCall 是进程内、不可变、不可序列化的单次对象，包含最终请求与所用配置的完整当次内容；不持久化，不发 MQ，不返回浏览器，不成为配置历史版本。只能由创建它的组件消费一次，第二次调用立即拒绝且不发送；调用方放弃时显式 close。所有路径包括异常和 deadline 到期都释放敏感临时材料。

独立脚本按[模型整理](./model-and-mapping-design.md)纳入当次完整内容，包括响应阶段引用的脚本；不能在 execute 中重新读取最新源码。脚本只处理显式输入，数据查询继续是独立步骤；脚本合同/编译器/运行实现未齐备时不能声明可执行。本段是运行边界修订，不代表已有实现。

从 prepare 开始使用一个单调 deadline，execute 不能重置计时。准备与执行间配置改变不重新装配本次请求；账户/材料失效仍可阻止发送，不因准备过就绕过运行授权。资金受理门禁由调用者的业务 Owner 控制，本期不把它放进配置步骤。

ChannelApiResult 包含 `sendState / failureStage / diagnosticCode / protocolEvidence / resultCode / outputs`。sendState 为 NOT_SENT、MAY_HAVE_BEEN_SENT 或 RESPONSE_RECEIVED；failureStage 可为空或 CONFIG、CONTEXT、FUNCTION、REQUEST、TRANSPORT、RESPONSE、RESULT。没有可信业务输出时 outputs 为空；协议头/体不整包返回，诊断只包含允许的字段定位和错误类型。

从开始可能发送第一个请求字节起，无法证明未发送就使用 MAY_HAVE_BEEN_SENT。收到 HTTP 响应只证明收到协议响应，不能据此推导业务成功。错误不触发隐式重试、下一渠道 API 或状态更新。

## 5. 与 Operations 检查功能的区别

管理检查只读取定义及注册描述符，不执行 Q1/Q2、获取 Token 或进入 prepare。UI 使用字段元数据展示绑定合法性、引用影响和缺失的实现；真实输入和查询结果不被保存成调试配置。

本轮不增加任意业务上下文试跑 HTTP。未来若需要离线试算，必须另定义合成数据入口、保护字段处理和受控能力范围，不能把 prepare 直接暴露给运营浏览器。
