# flow_definition_json 结构与静态校验（评审稿）

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。Q1～Q6 均已由用户确认，记录见第 9 节；同日确认原子 API 评审点 A3 后，节点增加 `params` 绑定（第 4.1 节）；确认内部接口 W2 后，第一个节点开始前的失败改为返回 Problem（第 3、5、7 节）。
- 上游：[流程设计](./flow-design.md)固定的语义（初始化、三区上下文、条件连线、无持久化、不编排资金反向操作）在本文中不再重复论证。
- 不包含：原子 API `definition_json` 内部结构（传输、脚本、响应解析、状态规则）、内部 HTTP Wire、物理列类型。

## 1. 顶层结构

`t_integration_standard_api.flow_definition_json` 保存一份渠道对一个标准 API 的实现：

```text
{
  "timeoutMs": 25000,
  "initializers": [ ... ],
  "entry": "createPayment",
  "nodes": [ ... ],
  "returns": [ ... ],
  "edges": [ ... ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `timeoutMs` | 是 | 整个流程（初始化 + 全部节点）的总预算，正整数，上限见第 7 节 |
| `initializers` | 是 | 依赖初始化项，可为空数组 |
| `entry` | 是 | 第一个执行的节点 ID |
| `nodes` | 是 | 原子 API 节点，至少 1 个 |
| `returns` | 是 | 返回出口，至少 1 个 |
| `edges` | 是 | 条件连线 |

所有对象拒绝未知字段与重复属性；所有 ID 使用 `[a-z][A-Za-z0-9]{0,63}`，在 `initializers`、`nodes`、`returns` 三者之间全局唯一。

## 2. 取值绑定 ValueBinding

节点请求字段、初始化参数和返回字段使用同一种取值结构。界面仍只呈现三种取值方式：

| kind | 结构 | 说明 |
|---|---|---|
| `PATH` | `{"kind":"PATH","path":"input.paymentContext.amount.value"}` | 只读路径，根必须是 `input`、`resources`、`steps` 之一；数组按 `[]` 定义的字段不能用固定下标读取 |
| `EXPRESSION` | `{"kind":"EXPRESSION","expression":"'NI-' + input.paymentContext.orderNo"}` | Aviator 表达式；环境只暴露三区的只读投影，常量用字面量表达 |
| `FUNCTION` | `{"kind":"FUNCTION","function":"MONEY_MINOR_UNITS","args":{...},"result":"result"}` | 调用注册函数，`args` 的每项仍是 ValueBinding（不允许再嵌套 FUNCTION）；`result` 是对返回值的 Aviator 表达式，完整使用时写 `result` |

- 表达式不再单独声明 `variables`：校验器从表达式 AST 提取引用路径，按第 6 节检查。沿用[原表达式剖面](./definition-contract.md#32-aviator-取值与处理)的白名单、精确十进制与时限要求。
- **节点内的 `FUNCTION` 只允许无外部 I/O 的纯函数**（如金额换算）。需要读取其他系统数据的函数只能出现在 `initializers` 中（Q3 已确认）。

## 3. 初始化项

```json
{
  "id": "channelAccount",
  "function": "PAYMENT_CHANNEL_ACCOUNT_MATERIAL",
  "args": {
    "channelAccountId": {"kind":"PATH","path":"input.paymentContext.channelAccountId"}
  },
  "required": true
}
```

- 结果写入 `resources.<id>`，结构取自该函数注册的返回描述。
- 依赖关系由 `args` 中对 `resources.*` 的引用推导，不另写 `dependsOn`；按拓扑顺序**串行**执行，存在环时拒绝保存。
- `required=true` 失败：结束调用，不执行任何节点，返回 503 Problem `INTEGRATION_INITIALIZATION_FAILED`（W2 确认，见[内部接口](./internal-api.md#32-problem没有发出任何渠道请求)）。
- `required=false` 失败：`resources.<id>` 不存在，后续引用按第 5 节缺失规则处理。
- 函数名、参数、返回描述与 Caller/Purpose 由注册目录提供；示例函数名只说明形状，不代表已注册（材料交接仍待定）。

## 4. 节点、返回与连线

### 4.1 节点

```json
{
  "id": "createPayment",
  "integrationApi": "CREATE_ORDER",
  "request": {
    "BODY:order.amount.value": {"kind":"FUNCTION","function":"MONEY_MINOR_UNITS",
      "args":{"amount":{"kind":"PATH","path":"input.paymentContext.amount.value"},
              "currency":{"kind":"PATH","path":"input.paymentContext.amount.currency"}},
      "result":"result"},
    "BODY:order.amount.currencyCode": {"kind":"PATH","path":"input.paymentContext.amount.currency"},
    "PATH:outletRef": {"kind":"PATH","path":"resources.channelAccount.outletId"}
  }
}
```

- `integrationApi` 是同一接入方下 `t_integration_api` 的 API 编码，跨接入方引用拒绝。
- `request` 的键是 `<field_location>:<field_path>`，必须是该原子 API `INTEGRATION_API_REQUEST` 字段；这里只填值，请求结构仍由字段表定义（Q2 已确认）。
- 节点执行后写入 `steps.<id> = {"executeStatus": ..., "outputs": {...}}`，与原子 API 返回形状一致；`ERROR` 时 `outputs` 为空对象。
- `params`（可省略，等同空对象）为原子 API `definition_json.params` 声明的调用参数赋值，键为参数名，值为 ValueBinding；必填参数必须赋值，未声明的参数拒绝（2026-10-13 A3 确认，见[原子 API 结构](./integration-api-definition-contract.md#3-调用参数-params)）。
- 原子 API `fixedValues` 已提供的字段不能再出现在 `request` 中。
- 每个节点在一次调用中最多执行一次。

### 4.2 返回出口

```json
{
  "id": "paid",
  "executeStatus": "SUCCESS",
  "outputs": {
    "transactionRef": {"kind":"PATH","path":"steps.createPayment.outputs.transactionRef"},
    "paymentStatus": {"kind":"EXPRESSION","expression":"steps.createPayment.outputs.state == 'CAPTURED' ? 'SUCCEEDED' : 'PROCESSING'"}
  }
}
```

- `executeStatus` 在出口上固定为 `SUCCESS` 或 `FAIL`；`ERROR` 出口不写 `outputs`，返回空对象。
- `outputs` 的键是标准 API `STANDARD_API_RESPONSE` 的 BODY 字段路径（去掉 `outputs.` 前缀）。
- 一个流程可以有多个出口，例如“已完成”“处理中”“明确失败”“结果不确定”。

### 4.3 连线

```json
[
  {"from":"createPayment","to":"paid","condition":"steps.createPayment.executeStatus == 'SUCCESS'","priority":1},
  {"from":"createPayment","to":"queryPayment","condition":"steps.createPayment.executeStatus == 'ERROR'","priority":2},
  {"from":"createPayment","to":"unknown","default":true}
]
```

- `from` 只能是节点；`to` 可以是节点或出口。
- 每个节点恰好一条 `default:true` 连线（无 `condition`、无 `priority`）；其他连线必须有 `condition` 和 `priority`，同一 `from` 内 `priority` 唯一。
- 按 `priority` 升序判断，命中第一条为真即流转；全部为假走 `default`。
- 条件必须求值为布尔；非布尔或执行异常按 `ERROR` 结束本次调用（Q5 已确认）。

## 5. 运行时缺失与类型

| 情况 | 处理 |
|---|---|
| 路径不存在，目标字段必填 | 结束调用，返回 `ERROR` |
| 路径不存在，目标字段非必填 | 省略该字段（不写 null） |
| 值为 null | 只有目标字段允许 null 时才写入，否则 `ERROR` |
| 表达式或函数异常 | `ERROR`，不当作字段省略 |
| 类型、长度、精度不符 | `ERROR`，不隐式转换或舍入 |
| 进入出口前，标准 API 必填输出缺失 | `ERROR`，即使出口声明为 `SUCCESS` |

先校验 `input` 是否满足 `STANDARD_API_REQUEST`，失败时不执行初始化和节点，返回 400 Problem `VALIDATION_ERROR`。上表的 `ERROR` 只适用于第一个节点开始之后；之前的失败一律返回[内部接口](./internal-api.md#32-problem没有发出任何渠道请求)中的 Problem。

## 6. 静态校验（保存时）

保存时只读元数据，不执行函数、表达式或外部请求。

1. **结构：** 未知字段、重复属性、ID 格式与全局唯一、数量上限（第 7 节）。
2. **引用存在：** `integrationApi` 属于同一接入方；`function` 已注册且参数完整；`request` 键存在于原子 API 请求字段且不与其 `fixedValues` 冲突；`params` 键是原子 API 声明的参数；出口 `outputs` 键存在于标准 API 响应字段。
3. **图结构：** `entry` 是节点；所有节点与出口从 `entry` 可达；**无环**（Q1 已确认：暂不支持环）；每个节点恰好一条兜底；从 `entry` 出发的每条路径都终止于出口。
4. **路径可用性：**
   - `input.*` 必须是标准 API 请求字段；
   - `resources.<id>.*` 必须是已声明初始化项的返回字段，且引用它的初始化项不能形成环；
   - `steps.<id>.*` 只能在 `<id>` **必经**的位置引用：从 `entry` 到该节点、连线或出口的每条路径上都已执行过 `<id>`，否则拒绝（防止读取另一分支未执行的节点）。
5. **覆盖：** 节点 `request` 与原子 API `fixedValues` 合起来覆盖每个必填请求字段，节点 `params` 覆盖每个必填调用参数；`SUCCESS`/`FAIL` 出口必须覆盖标准 API 全部必填响应字段。
6. **表达式：** 在白名单剖面下编译；条件表达式可推导为布尔；类型能静态推导的做静态检查，其余标记“运行时校验”。
7. **危险操作：** 系统不自动识别资金反向操作，不增加 `t_integration_api` 标记列（Q4 已确认）。流程配置由人工评审确认没有引用冲正、撤销、退款等资金反向操作的原子 API。

## 7. 预算（拟定）

| 项 | 上限 |
|---|---|
| `timeoutMs` | 60000 |
| 初始化项 | 8 |
| 节点 | 16 |
| 出口 | 16 |
| 连线 | 64 |
| 单节点 `request` 映射 | 128 |
| 整份 JSON | 65,536 UTF-8 字节 |

执行时每个节点的超时取原子 API 自身超时与流程剩余预算中较小者；预算耗尽时结束调用：第一个节点开始前返回 503 Problem `INTEGRATION_BUDGET_EXHAUSTED`，开始后返回 `ERROR`。Payment 调用 Platform 的读取超时为 65000 ms（W4 确认）。

## 8. 完整示例（合成数据）

```json
{
  "timeoutMs": 25000,
  "initializers": [
    {"id":"channelAccount","function":"PAYMENT_CHANNEL_ACCOUNT_MATERIAL",
     "args":{"channelAccountId":{"kind":"PATH","path":"input.paymentContext.channelAccountId"}},
     "required":true}
  ],
  "entry": "createPayment",
  "nodes": [
    {"id":"createPayment","integrationApi":"CREATE_ORDER","request":{
      "PATH:outletRef":{"kind":"PATH","path":"resources.channelAccount.outletId"},
      "BODY:merchantOrderReference":{"kind":"PATH","path":"input.paymentContext.attemptId"}}},
    {"id":"queryPayment","integrationApi":"QUERY_ORDER","request":{
      "PATH:outletRef":{"kind":"PATH","path":"resources.channelAccount.outletId"},
      "QUERY:merchantOrderReference":{"kind":"PATH","path":"input.paymentContext.attemptId"}}}
  ],
  "returns": [
    {"id":"paid","executeStatus":"SUCCESS","outputs":{
      "transactionRef":{"kind":"PATH","path":"steps.createPayment.outputs.transactionRef"}}},
    {"id":"paidAfterQuery","executeStatus":"SUCCESS","outputs":{
      "transactionRef":{"kind":"PATH","path":"steps.queryPayment.outputs.transactionRef"}}},
    {"id":"declined","executeStatus":"FAIL","outputs":{
      "failureReason":{"kind":"PATH","path":"steps.createPayment.outputs.errorCode"}}},
    {"id":"unknown","executeStatus":"ERROR"}
  ],
  "edges": [
    {"from":"createPayment","to":"paid","condition":"steps.createPayment.executeStatus == 'SUCCESS'","priority":1},
    {"from":"createPayment","to":"declined","condition":"steps.createPayment.executeStatus == 'FAIL'","priority":2},
    {"from":"createPayment","to":"queryPayment","condition":"steps.createPayment.executeStatus == 'ERROR'","priority":3},
    {"from":"createPayment","to":"unknown","default":true},
    {"from":"queryPayment","to":"paidAfterQuery","condition":"steps.queryPayment.executeStatus == 'SUCCESS' && steps.queryPayment.outputs.state == 'CAPTURED'","priority":1},
    {"from":"queryPayment","to":"unknown","default":true}
  ]
}
```

说明：示例中 `createPayment` 返回 `ERROR` 后按渠道合同查询一次；查询确认已扣款才返回成功，其余情况（包括查询本身失败或未扣款）都返回 `ERROR`，由 Payment 按原操作身份处理，流程不冲正。`paidAfterQuery` 出口只引用 `steps.queryPayment`，满足必经校验；如果 `paid` 出口引用了 `steps.queryPayment`，保存会被拒绝。字段名、API 编码与函数名均为合成示例，不是 NI 协议事实或已注册目录。

## 9. 评审点与确认记录

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | 流程是否允许环（例如轮询查询多次）？ | **已确认（2026-10-13）：暂时不能有环。** 只支持有向无环图，每个节点最多执行一次；需要多次查询时配置多个查询节点 |
| Q2 | 原子 API 的请求值在哪里配置？ | **已确认：在流程节点里配置。** 原子 API 定义请求结构、传输、脚本和响应解析，可被多个流程复用；协议固定值放在原子 API 的 `definition_json`，随该结构定稿 |
| Q3 | 节点取值能否调用读取外部数据的函数？ | **已确认（2026-10-13）：按建议，不能。** 用户先询问实现难度，答复：调用本身可复用初始化的函数调用，实现不难；复杂度来自隐藏的外部调用、渠道动作后查询失败带来更多“已执行但返回 ERROR”、重复调用、失败语义重叠以及回调调用方的循环依赖。建议维持“外部数据只在初始化读取”；若确有渠道需要，改为在图中增加显式“函数节点”（最多执行一次，结果写入 `steps`，失败由连线处理，复用必经校验），工作量中等，待真实需求出现再做 |
| Q4 | 怎样防止把冲正类 API 编进流程？ | **已确认：人工评审。** 不加标记列，保存时不自动拦截 |
| Q5 | 条件表达式异常或结果不是布尔怎么办？ | **已确认：** 结束调用并返回 `ERROR`，不当作 false |
| Q6 | 第 7 节的数值上限 | **已确认：** 按表中拟定值，后续可按实际渠道调整 |
