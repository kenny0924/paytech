# 外部 API 配置：初始化、运行上下文与流程

本文是[现行需求](./requirement.md)的流程设计评审材料。`flow_definition_json` 的完整 JSON 结构与静态校验见[结构评审稿](./flow-definition-contract.md)（待用户确认），下文只固定已确认的语义。

## 1. 一次调用的顺序

```text
加载配置（一次，短只读事务）
  → 校验 input（按标准 API 请求字段）
  → initializers 按依赖顺序执行，写入 resources
  → 从入口节点开始：执行原子 API → 写入 steps.<nodeId>
      → 按 priority 判断出边 → 下一节点 / 结束
  → 返回映射 → { executeStatus, outputs }
```

## 2. 依赖初始化

初始化项保存在 `flow_definition_json.initializers`，属于渠道标准 API 实现的一部分。

1. 配置加载时确定依赖清单，校验注册函数、参数与上下文引用。
2. 调用开始时先校验输入，再按依赖顺序执行初始化，通过受控系统函数获取本次所需的外部资料。
3. 必需资料初始化失败时结束调用，不进入目标渠道操作，返回 503 Problem，保证没有发出渠道请求（见[内部接口](./internal-api.md#32-problem没有发出任何渠道请求)）。
4. 初始化结果只存在于本次调用，不是应用启动时的全局账号缓存，不覆盖调用方冻结的账号或交易身份。

账号等资料通过所属系统的受控接口获取，不跨库读取；具体参数、Caller/Purpose 与材料交接待定。

## 3. 运行上下文

| 区域 | 用途 | 读写 | 示例 |
|---|---|---|---|
| `input` | 调用方业务数据 | 只读 | `input.paymentContext.amount.value` |
| `resources` | 初始化得到的外部资料 | 初始化后只读 | `resources.channelAccount.outletId` |
| `steps` | 执行器保存的本次节点结果 | 仅执行器写入 | `steps.createPayment.outputs.transactionRef` |

只有调用前就能取得的数据可以放进初始化；渠道执行后才产生的交易号等信息保存在 `steps`，供后续节点读取。

## 4. 流程与条件连线

`flow_definition_json` 包含初始化项、节点、连线、节点取值及最终返回映射。

连线字段：`from`、`to`、`condition`、`priority`、`default`。

- 当前节点完成并写入 `steps` 后，按 `priority` 从小到大判断出边，命中第一条为真的条件即流转（单分支）。
- 均未命中时走唯一的兜底分支（`default`）；静态校验要求每个有出边的节点恰好一条兜底。
- 条件是 Aviator 布尔表达式，只读上下文，不发起外部查询、不修改运行数据。
- 表达式执行异常或结果不是布尔时，结束本次调用并返回 `ERROR`，不当作 false（2026-10-13 用户确认）。
- 流程暂不支持环，每个节点在一次调用中最多执行一次（2026-10-13 用户确认）。

示例（结果未确认时查询原操作）：

| from | to | condition | priority | default |
|---|---|---|---|---|
| `createPayment` | `end` | `steps.createPayment.executeStatus == 'SUCCESS'` | 1 | false |
| `createPayment` | `queryPayment` | `steps.createPayment.executeStatus == 'ERROR' && 渠道合同允许查询的条件` | 2 | false |
| `createPayment` | `end` | — | — | true |

`queryPayment` 执行成功只说明查询拿到了结果，支付是否成功仍由其业务 `outputs` 判断。

## 5. 取值与脚本

- 取值方式：上下文路径、Aviator 表达式、注册系统函数及函数结果表达式；不增加独立的格式转换选择器。
- 公共脚本处理组装、转换、判断与函数调用；复杂签名、加密由代码注册函数提供。
- 请求脚本操作当前节点的 `http.requestHeaders`、`http.requestBody`；响应脚本操作 `http.responseHeaders`、`http.responseBody`，响应阶段不能改写已发送请求。原始响应验签与最终发送字节的约束保留，详见[公共脚本设计](./public-script-http-context-design.md)。

## 6. 结果、持久化与危险操作边界

- 返回 `executeStatus: SUCCESS / FAIL / ERROR` 与业务 `outputs`；执行状态不自动等于支付资金状态。`ERROR` 不证明外部未执行。
- 原子 API 只执行本 API 的一次目标操作；标准 API 按显式流程调用多个原子节点，不是 HTTP 客户端自动重试。
- **流程不编排冲正、撤销、退款等资金反向操作**，也不把它们作为超时或 `ERROR` 的自动补救。该约束由流程配置的人工评审保证，系统不自动识别（2026-10-13 用户确认）。查询原操作只在符合具体渠道合同时执行，不能把所有 `ERROR` 或超时当作失败依据。
- 不保存流程实例、节点执行记录、运行上下文、请求/响应报文，不登记后台补偿任务。调用结束或进程重启后不自动继续（此前讨论的持续恢复与两张运行表方案已撤回）。
- 不引入发布版本：修改对新调用生效，已开始调用使用一次加载的完整配置。
- Payment→Platform 与 Platform→渠道是两段网络边界，任一段超时都不能证明外部未执行；业务恢复仍由 Payment 按原操作身份处理。Payment 调用超时须大于流程总预算，具体数值待定。
