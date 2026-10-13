# 原子 API definition_json 结构

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。用户对第 9 节 A1～A7 回复“按建议”；注册函数签名、材料交接、Wire 与物理类型仍待定。
- 上游：[六表数据设计](./data-design.md)（请求/响应结构由 `t_api_field` 定义）、[流程结构](./flow-definition-contract.md)（节点提供请求值）、[流程设计](./flow-design.md)（脚本阶段、执行状态、无持久化）。
- 沿用的历史原则：[公共脚本与 HTTP 工作对象](./public-script-http-context-design.md)的脚本读写权限、原始响应验签与最终发送字节约束；[原传输合同](./definition-contract.md#5-http-传输)中的路径安全、禁止自动重试/重定向/Cookie 与受控出站目标。
- 不包含：注册函数完整签名、账号材料交接、内部 HTTP Wire、物理列类型。

## 1. 职责划分

`t_integration_api.definition_json` 描述**一次外部协议操作怎样发送、怎样解释结果**。它不决定字段值从哪里来：

| 内容 | 位置 |
|---|---|
| 请求、响应字段结构（位置、路径、类型、必填） | `t_api_field`（`INTEGRATION_API_REQUEST` / `INTEGRATION_API_RESPONSE`） |
| 每次调用的请求字段值、调用参数值 | 流程节点的 `request`、`params` |
| 协议固定值、传输、编码、脚本、状态规则、输出映射 | 本文的 `definition_json` |

同一个原子 API（例如查询订单）可以被 `PAY`、`REFUND` 等多个流程复用，各流程只是填入不同的值。

## 2. 顶层结构

```text
{
  "params": [ ... ],
  "fixedValues": { ... },
  "transport": { ... },
  "requestEncoding": "JSON",
  "responseDecoding": "JSON",
  "scripts": [ ... ],
  "statusRules": [ ... ],
  "outputs": { "SUCCESS": [ ... ], "FAIL": [ ... ] }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `params` | 是 | 调用参数声明，可为空数组（第 3 节） |
| `fixedValues` | 是 | 协议固定值，可为空对象（第 4 节） |
| `transport` | 是 | HTTP 传输（第 5 节） |
| `requestEncoding` / `responseDecoding` | 是 | 报文编解码（第 5.2 节） |
| `scripts` | 是 | 有序脚本引用，可为空数组（第 6 节） |
| `statusRules` | 是 | 执行状态判定（第 7 节） |
| `outputs` | 是 | 按状态分支的输出映射（第 8 节） |

所有对象拒绝未知字段与重复属性。

## 3. 调用参数 params

请求字段以外、由调用方每次提供的值，例如签名所需的密钥引用、账号标识：

```json
[
  {"name":"signingKey","dataType":"PROTECTED_HANDLE","required":true,"description":"签名密钥受控句柄"},
  {"name":"apiVersion","dataType":"STRING","required":false}
]
```

- 名称使用 `[a-z][A-Za-z0-9]{0,63}`，在本 API 内唯一。
- 值由流程节点的 `params` 绑定（[流程结构](./flow-definition-contract.md#41-节点)第 4.1 节已同步，A3）。
- 在脚本参数、`transport.pathTemplate` 以外的位置以 `params.<name>` 只读引用。`PROTECTED_HANDLE` 只能传给注册函数，不能写进报文字段、输出或日志（沿用原受控句柄原则）。

## 4. 协议固定值 fixedValues

```json
{
  "HEADER:Accept": {"kind":"EXPRESSION","expression":"'application/vnd.ni-payment.v2+json'"},
  "HEADER:Content-Type": {"kind":"EXPRESSION","expression":"'application/vnd.ni-payment.v2+json'"}
}
```

- 键与流程节点 `request` 相同：`<field_location>:<field_path>`，必须是本 API 的请求字段。
- 值只能是**常量表达式**（不引用 `input`、`resources`、`steps`、`params`），保证原子 API 与具体流程无关。
- 已有固定值的字段不能再由节点赋值，保存流程时拒绝冲突。
- 节点赋值与固定值合起来必须覆盖全部必填请求字段（流程保存时校验）。
- 请求没有 `Content-Type` 时，执行按 `requestEncoding` 补默认值（`JSON` 为 `application/json`，`FORM_URLENCODED` 为 `application/x-www-form-urlencoded`）。只有渠道要求其他值（如上例的厂商类型）时，才把它定义为请求字段并设固定值。

## 5. 传输与编解码

### 5.1 transport

```json
{
  "protocol": "HTTP",
  "method": "POST",
  "endpointCode": "NI_GATEWAY",
  "pathTemplate": "/transactions/outlets/{outletRef}/orders",
  "connectTimeoutMs": 2000,
  "responseTimeoutMs": 10000
}
```

| 字段 | 规则 |
|---|---|
| `protocol` | 本期只接受 `HTTP` |
| `method` | `GET`、`POST`、`PUT`、`PATCH`、`DELETE` |
| `endpointCode` | 指向 `t_integration.endpoints` 中本接入方维护的 base URL（A6 已确认，存放位置按物理设计 P5 确定为 Platform 数据库），不能由调用值提供域名 |
| `pathTemplate` | 以单个 `/` 开头的相对路径；禁止 scheme、host、query、fragment、反斜线、`.`/`..` 段。`{name}` 变量必须与本 API 的 `PATH` 字段一一对应，值按单个路径段编码 |
| `connectTimeoutMs` | 默认 2000，范围 100～10000 |
| `responseTimeoutMs` | 默认 10000，范围 100～30000 |

- 实际超时取配置值与流程剩余预算的较小者，预算不足时不发送，返回 `ERROR`。
- 关闭自动重试、重定向、Cookie 与隐式认证重发。`Host`、`Content-Length`、`Transfer-Encoding`、`Connection` 由传输实现处理，不能配置。
- `QUERY` 字段拼成查询串；`HEADER` 字段名大小写不敏感。请求体、响应体各最多 1 MiB，Header 总量最多 16 KiB。

### 5.2 编解码

| 字段 | 取值（拟定） | 说明 |
|---|---|---|
| `requestEncoding` | `JSON`、`FORM_URLENCODED`、`NONE` | 按 BODY 字段组装出的对象编码成字节；`NONE` 表示无请求体，此时不允许 BODY 字段 |
| `responseDecoding` | `JSON`、`NONE` | `JSON` 把原始字节解析为 `http.responseBody`；`NONE` 不解析，留给响应脚本（如先解密）处理 |

不根据报文内容猜测格式；解码失败返回 `ERROR`。XML 等格式保留扩展位置，本期不支持（A5 已确认）。

## 6. 脚本

```json
[
  {"id":"signRequest","scriptCode":"NI_SIGN","stage":"REQUEST",
   "params":{"key":{"kind":"PATH","path":"params.signingKey"}}},
  {"id":"verifyResponse","scriptCode":"NI_VERIFY","stage":"RESPONSE","params":{}}
]
```

- 按数组顺序执行；`scriptCode` 必须是同一接入方的 `t_integration_script`，且其执行阶段与 `stage` 一致。
- 脚本参数的取值只能引用 `params.*` 或常量，不能读流程的 `input`、`resources`、`steps`。
- 读写权限沿用公共脚本设计：REQUEST 阶段读写 `http.requestHeaders`、`http.requestBody`；RESPONSE 阶段读写响应工作副本，只读已发送请求和状态码；响应验签读取原始响应字节。
- 需要对最终字节签名时，由注册函数完成序列化并锁定请求体，锁定后不能再修改请求体；具体函数合同随注册目录定稿。

## 7. 执行顺序与状态规则

```text
组装请求：fixedValues + 节点 request → http.requestHeaders / requestBody / query / path
  → REQUEST 脚本（按顺序）
  → 按 requestEncoding 编码，发送最终字节
  → 接收 status / headers / 原始 body
  → 按 responseDecoding 解码到 http.responseBody
  → RESPONSE 脚本（按顺序）
  → 校验响应字段（第 7.2 节）
  → statusRules 判定 executeStatus
  → 按 outputs 分支映射 → steps.<nodeId>
```

### 7.1 statusRules

```json
[
  {"condition":"http.responseStatus >= 200 && http.responseStatus < 300 && http.responseBody.state != 'FAILED'","executeStatus":"SUCCESS"},
  {"condition":"http.responseStatus == 400 || http.responseBody.state == 'FAILED'","executeStatus":"FAIL"}
]
```

- 条件是 Aviator 布尔表达式，与流程连线使用同一剖面（A1 已确认）；可读 `http.responseStatus`、`http.responseHeaders`、`http.responseBody`、`params`（不含受控句柄）。
- 按数组顺序，第一条为真即决定结果；全部为假固定为 `ERROR`，不能配置成功兜底。
- 表达式异常或结果非布尔：`ERROR`。
- 不存在“HTTP 200 即成功”的默认规则。

### 7.2 响应字段校验

`INTEGRATION_API_RESPONSE` 字段在出现时校验类型；必填只在判定为 `SUCCESS` 时校验（A4 已确认）。失败响应往往结构不同，不能因为缺少成功字段把明确失败变成 `ERROR`。

## 8. 输出 outputs

```json
{
  "SUCCESS": [
    {"name":"transactionRef","dataType":"STRING","required":true,
     "value":{"kind":"PATH","path":"http.responseBody.reference"}}
  ],
  "FAIL": [
    {"name":"errorCode","dataType":"STRING","required":false,
     "value":{"kind":"EXPRESSION","expression":"http.responseBody.errors == nil ? nil : #http.responseBody.errors[0].code"}}
  ]
}
```

- 分支键只能是 `SUCCESS`、`FAIL`；`ERROR` 固定输出空对象。
- 输出结构保存在 `definition_json`，不进入 `t_api_field`（A2 已确认）。流程保存时据此校验 `steps.<id>.outputs.*` 引用：只要任一分支声明了该字段即可引用，运行时实际缺失按[流程结构第 5 节](./flow-definition-contract.md#5-运行时缺失与类型)处理。
- **数组下标写法（2026-10-13 第 3 步 3a 实测补充）：** Aviator 5.3.3 不支持 `a.b[0].c`，带下标的嵌套读取须加 `#` 前缀写成 `#a.b[0].c`；本节与第 10 节示例已按此修正。
- 取值可用 `PATH`、`EXPRESSION`、纯 `FUNCTION`，可读 `http.*` 与 `params`（不含受控句柄）。`[]` 数组路径只用于定义；按数组取值必须用表达式明确选择元素（如上例 `errors[0]`），`PATH` 不能使用 `[]` 或隐式取第一个。
- 必填输出缺失或类型不符：整个结果改为 `ERROR`。

## 9. 评审点与确认记录

2026-10-13 用户回复“按建议”，下列建议全部确认。

| 编号 | 问题 | 结论 |
|---|---|---|
| A1 | 状态规则的条件写法 | 用 Aviator 布尔表达式，与流程连线一致；取代旧稿受限的结构化条件（`EQ/NE/EXISTS/ALL/ANY/NOT`） |
| A2 | 原子 API 的输出结构放哪里 | 放 `definition_json`，按 SUCCESS/FAIL 分支；不给 `t_api_field` 增加第五种方向 |
| A3 | 签名密钥、账号标识这类“非报文字段”的调用值怎样传入 | 原子 API 声明 `params`，流程节点新增 `params` 绑定（会扩展已确认的流程节点结构） |
| A4 | 响应字段的“必填”何时生效 | 只在判定为 SUCCESS 时校验；其他情况只校验出现字段的类型 |
| A5 | 本期支持的编解码 | 请求 `JSON`、`FORM_URLENCODED`、`NONE`；响应 `JSON`、`NONE`；XML 以后再加 |
| A6 | base URL 从哪里来 | `endpointCode` 指向接入方按环境维护的受控地址；不接受调用值提供域名。存放位置已在[物理设计](./physical-design.md) P5 确定为 `t_integration.endpoints` |
| A7 | 固定值能否引用调用数据 | 不能，只能是常量；需要动态值就由节点赋值 |

## 10. 示例（合成数据，非 NI 协议事实）

```json
{
  "params": [
    {"name":"signingKey","dataType":"PROTECTED_HANDLE","required":true}
  ],
  "fixedValues": {
    "HEADER:X-Api-Version": {"kind":"EXPRESSION","expression":"'2'"}
  },
  "transport": {
    "protocol":"HTTP","method":"POST","endpointCode":"DEMO_GATEWAY",
    "pathTemplate":"/outlets/{outletRef}/orders",
    "connectTimeoutMs":2000,"responseTimeoutMs":10000
  },
  "requestEncoding": "JSON",
  "responseDecoding": "JSON",
  "scripts": [
    {"id":"signRequest","scriptCode":"DEMO_HMAC_SIGN","stage":"REQUEST",
     "params":{"key":{"kind":"PATH","path":"params.signingKey"}}}
  ],
  "statusRules": [
    {"condition":"http.responseStatus == 201 && http.responseBody.state == 'CAPTURED'","executeStatus":"SUCCESS"},
    {"condition":"http.responseStatus == 201 && http.responseBody.state == 'DECLINED'","executeStatus":"FAIL"}
  ],
  "outputs": {
    "SUCCESS": [
      {"name":"transactionRef","dataType":"STRING","required":true,
       "value":{"kind":"PATH","path":"http.responseBody.reference"}},
      {"name":"state","dataType":"STRING","required":true,
       "value":{"kind":"PATH","path":"http.responseBody.state"}}
    ],
    "FAIL": [
      {"name":"errorCode","dataType":"STRING","required":false,
       "value":{"kind":"EXPRESSION","expression":"http.responseBody.errors == nil ? nil : #http.responseBody.errors[0].code"}}
    ]
  }
}
```

对应的请求字段（`t_api_field`，`INTEGRATION_API_REQUEST`）：`PATH outletRef`、`HEADER X-Api-Version`、`BODY merchantOrderReference`、`BODY amount.value`、`BODY amount.currencyCode`。其中 `X-Api-Version` 由固定值提供，`Content-Type` 按 `JSON` 编码自动补 `application/json`，其余由流程节点 `request` 赋值，`signingKey` 由节点 `params` 绑定。HTTP 201 但状态不是 `CAPTURED`/`DECLINED`，或任何非 201 响应，都落入 `ERROR`。
