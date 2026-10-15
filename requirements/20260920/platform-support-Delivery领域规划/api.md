# Notification 内部服务接口

日期：2026-09-20；2026-09-21 确认首期只开放模块内 Processor。本文收录模块请求/响应及暂缓接口草案，不是已发布的 OpenAPI。Owner 为 Platform Support / Notification；Operations 管理接口与 Webhook 不在本文。

需求边界及未决项见 [requirement.md](./requirement.md)，执行规则见 [technique.md](./technique.md)，字段来源见 [data-design.md](./data-design.md)。

首期仅处理 Email，邮件 Provider 先 Mock。当前请求使用 EMAIL 模板及 recipients[].email；下文 phoneNumber 和 SMS 示例只保留为后续契约规划，不属于首期可用能力。Provider Mock 不改变 submitNotification 的受理响应含义，也不表示真实邮件已经发出。

## 1. 接口状态

| 能力 | Method / Path | 当前状态 |
|---|---|---|
| sendTransientEmail | SendTransientEmailProcessor.process | 2026-10-15 新增模块内即时发送（认证邮件），不落库、不重试、无 HTTP 入口，见[认证邮件接入方案](../../20261015/platform-support-认证邮件接入Delivery/technique.md) |
| submitNotification | SubmitNotificationProcessor.process；内部 HTTP `POST /notifications/v1/requests` | 模块内入口；2026-10-15 起另有内部 HTTP 入口（`NotificationCommandApi`，过渡信任，见[需求](../../20261015/platform-support-通知内部HTTP接口/requirement.md)与[方案](../../20261015/platform-support-通知内部HTTP接口/technique.md)），不注册 Gateway 路由 |
| getNotification | GET /notifications/v1/requests/{requestId} | 用户已认可草案，随后要求查询接口暂不继续展开；第 3 节仅保留讨论结果 |
| findNotificationBySource | 不冻结 | 暂不提供 |
| listNotificationDeliveries | 不冻结 | 暂不提供 |
| getDelivery | 不冻结 | 暂不提供 |
| 取消、人工重试、主动刷新等操作 | 不冻结 | 后续单独讨论 |
| 模板、语言内容、Provider、路由管理 | 不在本文定义 | 2026-10-15 由 [Operations 通知管理](../../20261015/operations-console-通知管理/technique.md) 提供 `/notification-admin/v1` 管理接口与发送记录只读查询（Platform 管理入口，不属于本文的模块接口） |

不因记录 getNotification 草案而恢复其他查询。提交响应不增加曾建议的 deliveryIds；也不为已暂缓的列表接口加入 address_masked。

仅同服务业务模块调用公开 Processor，并负责来源、租户和业务授权；sourceSystem 是受信任 Java 调用参数。跨服务 HTTP 待强身份方案确定后再开放，不能把本请求直接绑定外部 Body/Header。本文不注册 Gateway 路由。

## 2. submitNotification

### 2.1 请求示例

以平台向两个 Client 分别发送维护通知为例：

~~~java
SubmitNotificationResponse response = submitNotificationProcessor.process(request);
~~~

以下 JSON 仅展示 Java 请求字段，不表示存在 HTTP 路由：

~~~json
{
  "sourceSystem": "PLATFORM_SUPPORT",
  "tenantId": null,
  "sourceEventKey": "maintenance-20260925:email",
  "templateCode": "MAINTENANCE_NOTICE_EMAIL",
  "defaultLocale": "zh-CN",
  "defaultParameters": {
    "serviceName": "Client Console",
    "maintenanceStart": "2026-09-25T22:00:00Z",
    "maintenanceEnd": "2026-09-25T23:00:00Z"
  },
  "recipients": [
    {
      "email": "client-a@example.com",
      "parameters": {
        "clientName": "客户 A"
      }
    },
    {
      "email": "client-b@example.com",
      "locale": "en-US",
      "parameters": {
        "clientName": "Client B"
      }
    }
  ]
}
~~~

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| sourceSystem | String | 是 | 可信模块填写的来源系统编码 |
| tenantId | Long | 否 | 请求归属租户；平台通知为空，服务端校验相应权限 |
| sourceEventKey | String | 是 | 来源系统内一次通知的稳定业务标识 |
| templateCode | String | 是 | 模板编码；媒介由模板确定 |
| defaultLocale | String | 是 | 收件人未覆盖时使用的默认语言 |
| defaultParameters | Object | 否 | 允许持久化的公共模板参数 |
| recipients | Array | 是 | 1..100 个收件人的非空列表 |
| recipients[].email | String | 条件 | 首期必填，Email 模板使用的邮箱 |
| recipients[].phoneNumber | String | 后续 | SMS 模板使用的国际格式手机号，与 email 二选一；首期不支持 |
| recipients[].locale | String | 否 | 当前收件人的语言，缺失时继承默认语言 |
| recipients[].parameters | Object | 否 | 当前收件人的差异参数，按键覆盖默认参数 |

单人发送使用只有一项的 recipients，不增加 sendMode。一个 Request 不混用 Email 和 SMS；要使用另一媒介，应选择对应模板并提交另一通知意图。

后续 SMS 收件人的对象示例（首期不支持；所属请求必须使用 SMS 模板）：

~~~json
{
  "phoneNumber": "+12025550123",
  "locale": "en-US",
  "parameters": {
    "clientName": "Client A"
  }
}
~~~

### 2.2 地址与参数规则

- 首期请求仅有 email，且模板必须为 EMAIL；phoneNumber 只属于后续草案，不加入当前 Java Request。
- 两种字段分别按其规范校验、规范化，服务端固定映射到相应已批准的 Vault dataType/Purpose；调用方不传 dataType。
- Vault 保护后统一写入 Delivery.address_vault_token，具体注册及授权见 Q-05。
- defaultParameters 与每个 parameters 按键合并，收件人同名参数优先；仅一层非 null 字符串 Map，限额见 technique.md 第 8 节。
- 根据有效语言选择当前模板内容，不自动语言回退，不允许收件人覆盖模板或媒介。
- 调用方不传 Provider、Route、技术连接配置或任意正文。
- 受理前检查权限、所有收件人和当前必要配置/参数；输入或配置错误整单拒绝。受理后的外部结果按 Delivery 独立记录。
- 规范化后重复邮箱整单拒绝，不做静默去重或合并。

示例创建 1 条 Request、2 条 Delivery，实际发送两封各含一个收件人的独立邮件。Client A 继承 zh-CN，Client B 使用 en-US；两者继承公共维护时间并分别覆盖 clientName。

### 2.3 受理响应

模块方法成功返回以下字段（ID 在 Java 内为 Long，JSON 展示使用字符串）：

~~~json
{
  "requestId": "204000000000000001",
  "acceptedCount": 2
}
~~~

| 字段 | 类型 | 说明 |
|---|---|---|
| requestId | String | 通知请求业务 ID，不是 HTTP 链路追踪 Request ID |
| acceptedCount | Integer | 已受理收件人数，即创建的 Delivery 数量 |

方法成功返回表示 Request、全部 Delivery 及补偿任务已在本地提交；不代表 Provider 已受理，更不代表邮件或短信已送达。请求与接收人集合的原子受理规则见 technique.md。

此响应不包含 Provider ID、地址、Vault Token、deliveryIds 或通用成功外壳。同来源身份重复提交抛 ConcurrentRequestException，不重放旧响应，不能据此引入 HTTP 响应重放组件。

### 2.4 认证临时材料尚未定义

统一入口覆盖认证通知的方向已确认，但本 Demo 仅定义允许持久化的普通业务参数。OTP、重置令牌和认证链接的可消费秘密不得放入 defaultParameters / parameters。

临时材料的瞬时传参、发送时机、丢失后的恢复边界需要 Q-01 的独立细化；不得把当前请求形状直接宣称为完整 OTP/密码重置发送合同，也不新增一个持久化 content_mode 或正文 Token 来替代这项工作。

## 3. getNotification：已认可、暂缓继续细化的草案

用户认可下列查询形状后，要求查询类接口先不继续展开。本节保留该讨论结果，不把它或取消统计扩展为本轮新的研发范围。

~~~http
GET /notifications/v1/requests/204000000000000001
~~~

唯一输入为 Path 中的 requestId，不需要 Body。响应示例：

~~~http
HTTP/1.1 200 OK
Content-Type: application/json
~~~

~~~json
{
  "requestId": "204000000000000001",
  "tenantId": null,
  "sourceSystem": "PLATFORM_SUPPORT",
  "sourceEventKey": "maintenance-20260925:email",
  "templateCode": "MAINTENANCE_NOTICE_EMAIL",
  "medium": "EMAIL",
  "defaultLocale": "zh-CN",
  "createTime": "2026-09-20T10:00:00Z",
  "summary": {
    "totalCount": 2,
    "pendingCount": 0,
    "processingCount": 0,
    "retryWaitCount": 0,
    "sentCount": 1,
    "deliveredCount": 0,
    "failedCount": 0,
    "unknownCount": 1,
    "cancelledCount": 0
  }
}
~~~

请求信息来自 Request 及其关联模板；summary 按 Delivery 当前状态计算，不保存到 Request。计数互斥相加等于 totalCount；sentCount 表示 Provider 已受理，不能与 deliveredCount 混为累计成功数。

查询只读取本地记录，不触发 Provider 查询或发送；访问仍校验来源和租户范围。cancelledCount 是原草案中保留的字段，不代表本轮已批准取消接口。

## 4. 公共协议与待细化边界

新接口路径按项目默认 v1 编写；内部 ID 示例使用十进制字符串，时间使用 UTC ISO-8601。后续 Java 顶层 DTO 使用 RequestBody/ResponseBody class，遵守项目开发规范，不在本轮生成这些工件。

错误响应沿用项目[Problem 合同](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)，不另外设计统一错误外壳。模块限额与异常见 technique.md 第 8 节。未来 HTTP 的强身份和错误映射需单独定义；统一见[后续事项](./requirement.md#open-items)。

本期模块入口不新增 HTTP API 源码、OpenAPI、Controller、BFF 或 Gateway 注册。HTTP Demo 的存在不代表接口已发布。
