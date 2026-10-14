# 标准 API 内部调用接口

- 日期：2026-10-13
- 状态：**已确认**（2026-10-13）。W1～W6 均由用户确认，记录见第 7 节；`code` 名称随实现落定。2026-10-13 确认物理设计 P4 时，W3 的调用方白名单改为暂不实现（见第 4 节）。不是已发布的 OpenAPI，也没有 Java 源码。
- 覆盖待决项：[技术说明第 6 节](./technique.md#6-待决事项)第 3 项（请求响应合同、调用授权、Problem 映射）与第 4 项（两段 HTTP 超时）。
- 不覆盖：账号材料交接（第 5 项）、管理接口、Operations 页面。
- 依据：[内部 HTTP 契约规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md#4-api-模块与内部-http-契约)、[全系统 Problem 决定](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)、[内部 HTTP 简化设计](../../../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md)、[流程结构](./flow-definition-contract.md)。

## 1. 接口

| 项 | 值 |
|---|---|
| 提供方 | Platform Support，轻量 `*-api` 模块中的 `StandardApiCommandApi` |
| Method / Path | `POST /platform-support/standard-apis/v1/{standardApiCode}/execute`（`/platform-support` 为 Platform 现有 context-path） |
| 调用方 | Payment 等业务系统，事务外通过 `@HttpExchange` 代理、服务发现和私有网络调用 |
| 不经过 | API Gateway、BFF；不注册 Gateway Route，不提供公网入口 |
| 请求幂等 | 无 `Idempotency-Key`；调用方不得自动重试 |

```java
@HttpExchange(url = "/standard-apis/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface StandardApiCommandApi {

    /**
     * 执行标准 API
     *
     * @param standardApiCode 标准 API 编码，例如 PAY
     * @param requestBody     接入方编码与标准 API 输入
     * @return 执行状态与标准 API 输出；只要返回 200，就可能已经发出渠道请求
     */
    @PostExchange("/{standardApiCode}/execute")
    ExecuteStandardApiResponseBody executeStandardApi(@PathVariable String standardApiCode,
        @RequestBody ExecuteStandardApiRequestBody requestBody);
}
```

以上只示意契约形状，不是已提交源码。

## 2. 请求

```http
POST /platform-support/standard-apis/v1/PAY/execute
Content-Type: application/json
```

```json
{
  "integrationCode": "NI",
  "input": {
    "paymentContext": {
      "attemptId": "301000000000000001",
      "channelAccountId": "302000000000000001",
      "amount": {"value": "10.00", "currency": "AED"}
    }
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `standardApiCode` | Path String | 是 | `t_standard_api` 的编码 |
| `integrationCode` | String | 是 | 接入方编码；由调用方显式选择，Platform 不从 Payment Channel 推导 |
| `input` | Object | 是 | 标准 API 输入，按 `STANDARD_API_REQUEST` 字段校验；流程中读作 `input.*` |

- 金额、64 位 ID、精确小数使用十进制字符串，时间使用 UTC ISO-8601，沿用项目第一方 JSON 规则。
- 请求体最多 64 KiB。未声明的 `input` 字段拒绝，不静默丢弃。
- `input` 不得包含原始卡数据、密钥或凭据明文；账号材料由初始化函数通过受控接口获取。卡数据只以支付工具 Token 进入 `input`，由初始化函数 `PAYMENT_CARD` 向 Payment 读取，只存在于本次调用内存（2026-10-14，[卡数据决定](../../../agent-docs/decisions/2026-10-14-card-data-platform-execution.md)）。

## 3. 响应与 HTTP 状态

### 3.1 200：已进入渠道执行

```json
{
  "executeStatus": "SUCCESS",
  "outputs": {
    "transactionRef": "ord_7f3a",
    "paymentStatus": "SUCCEEDED"
  }
}
```

| 字段 | 说明 |
|---|---|
| `executeStatus` | `SUCCESS`、`FAIL`、`ERROR`，由流程出口决定 |
| `outputs` | 按 `STANDARD_API_RESPONSE` 字段返回；`ERROR` 时为空对象 |

**返回 200 表示流程已经开始执行第一个原子节点**，因此可能已经发出渠道请求。`ERROR` 表示结果无法确认，不能据此判定渠道未执行（W2 已确认）。不返回节点轨迹、原始报文或 `resources`。

### 3.2 Problem：没有发出任何渠道请求

第一个原子节点开始发送之前发现的问题，返回项目统一 Problem。下表中的 `code` **保证没有发出任何渠道请求**，调用方可以按业务规则处理（例如换 MID 或结束），仍不得自动重放同一请求：

| HTTP | `code` | 场景 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 请求体格式错误，或 `input` 不满足标准 API 请求字段 |
| 404 | `STANDARD_API_NOT_FOUND` | 标准 API 编码不存在 |
| 404 | `INTEGRATION_IMPLEMENTATION_NOT_FOUND` | 接入方不存在，或没有实现该标准 API |
| 500 | `INTEGRATION_CONFIG_INVALID` | 加载的流程或原子 API 配置未通过运行前校验 |
| 503 | `INTEGRATION_INITIALIZATION_FAILED` | 必需初始化项失败（例如账号资料接口不可用） |
| 503 | `INTEGRATION_BUDGET_EXHAUSTED` | 第一个节点开始前流程预算已耗尽 |

上表的 `code` 名称是拟定值。

**实现说明（2026-10-13 第 3 步 3c-1）：** `input` 不满足标准 API 请求字段时按[全系统 Problem 设计](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)第 4.1 节返回 **422** `VALIDATION_ERROR` 及 `errors[]`（位置以 `input.` 开头）；请求体不是合法 JSON 时为 400。两种情况都保证没有发出渠道请求。

### 3.3 其他情况：结果未知

调用方遇到以下情况，必须按“可能已经发出渠道请求”处理，由 Payment 按原操作身份恢复：

- 连接或读取超时、连接中断；
- 不在 3.2 表中的 4xx/5xx，包括框架兜底的 500；
- 200 但 `executeStatus=ERROR`。

为保证 3.2 的承诺成立，执行器在第一个节点开始后必须捕获全部异常并返回 `200 + ERROR`，不能让异常落入全局 500。

## 4. 调用授权

**本期不做调用方授权（2026-10-13 用户决定，取代原 W3 的白名单方案）。** 与现行内部 HTTP 规范一致，只依靠私有网络与部署网络策略限制访问：

- 不要求 `X-Caller-Service`，不维护调用方白名单，不返回 403。
- 私有网络内的任何服务都能调用本接口，这是已知风险；后续出现统一的工作负载身份机制，或确有需要恢复白名单时，再单独设计。

## 5. 超时（两段 HTTP）

| 段 | 设置 | 说明 |
|---|---|---|
| Platform 内部流程 | `flow_definition_json.timeoutMs`，上限 60000 | 已确认；初始化与全部节点共享 |
| 原子节点 | 连接 2000、响应 10000（默认，可配） | 取配置值与流程剩余预算的较小者 |
| Payment → Platform 连接超时 | 1000 ms | 拟定 |
| Payment → Platform 读取超时 | 65000 ms | 流程上限 60 秒加 5 秒余量；保证 Platform 正常结束时 Payment 能拿到结果 |

- Payment 对本接口关闭自动重试，熔断、负载均衡重试同样关闭。
- Payment 读取超时后断开，Platform 仍可能把流程执行完；结果丢弃，不持久化，不回调。

## 6. 日志与数据处理

- `input` 与 `outputs` 可能包含跳转 URL、交互参数等只能临时使用的值，不得持久化。
- **敏感日志暂不处理（W5，2026-10-13 用户决定）：** 本接口沿用现行 MVC 公共日志组件的[完整 Body 日志规则](../../../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md)，不申请例外、不加掩码。全项目敏感日志后续统一处理，届时一并覆盖本接口；该冲突列为已知遗留风险。
- Platform 自有日志只记录 Request ID、`standardApiCode`、`integrationCode`、节点 ID、`executeStatus`、HTTP 状态与耗时，不记录字段值。

## 7. 评审点与确认记录

2026-10-13 用户决定 W5，随后对其余各项回复“按建议”。

| 编号 | 问题 | 结论 |
|---|---|---|
| W1 | 路径与命名 | `POST /standard-apis/v1/{standardApiCode}/execute`，接入方编码放在请求体；`StandardApiCommandApi.executeStandardApi`，DTO 为 `ExecuteStandardApiRequestBody` / `ExecuteStandardApiResponseBody` |
| W2 | HTTP 状态怎样表达“是否已发出渠道请求” | 第一个节点开始前的问题返回 Problem（保证未发送）；开始后一律 200，`ERROR` 表示结果未知。**这会修改已确认的流程结构第 3 节**：必需初始化失败从“返回 ERROR”改为返回 503 Problem |
| W3 | 调用授权 | 原确认：必填 `X-Caller-Service` + 按标准 API 的静态白名单。**2026-10-13 用户在物理设计 P4 改为暂时不做**，见第 4 节 |
| W4 | 超时 | Payment 连接 1 秒、读取 65 秒，关闭一切自动重试；超时按结果未知处理 |
| W5 | 公共 Body 日志与临时敏感值冲突 | **已决定（2026-10-13）：暂时不考虑，后续统一处理。** 本接口沿用现行完整 Body 日志，不限制输入输出内容；冲突作为已知遗留风险 |
| W6 | 接入方由谁选择 | 调用方在请求体传 `integrationCode`；Payment 自己维护 Channel 与接入方编码的对应关系，Platform 不推导 |
