# Platform Support 通知内部 HTTP 接口技术方案

> 状态：阶段 2+3 已确认（2026-10-15），阶段 4 已实现，偏差见第 9 节。需求与验收标准见 [requirement.md](./requirement.md)。

**规模（本次方案）：** 1 个系统（Platform Support）；新增 1 个内部接口 `POST /platform-support/notifications/v1/requests`，修改 0 个接口；新增 0 张表，修改 0 张表/字段/索引；DDL 0、DML 0；页面 0。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理 |
|---|---|---|---|---|
| 跨服务提交（AC-01） | 只有模块内入口 `SubmitNotificationProcessor`；[Delivery api.md](../../20260920/platform-support-Delivery领域规划/api.md) 写明"不注册 HTTP Controller 或路由" | 私网 HTTP 提交，复用同一 Processor | 无 API 合同、无 Controller | 新增 `NotificationCommandApi` + `NotificationController` |
| 调用方识别（AC-02） | Delivery 规划："不信任自报 X-Caller-Service"；Platform 内先例 `OnboardingFileVerificationController` 用硬编码服务名比对 | `X-Caller-Service` + 部署配置白名单，按调用方限定模板 | 无白名单配置 | 新增 `notification.http.callers` 配置 |
| 来源系统（AC-01/03） | `SubmitNotificationRequest.sourceSystem` 校验为 `[A-Z][A-Z0-9_]{0,63}`，只有测试使用（`NOTIFICATION_TEST`） | 直接保存服务名，如 `payment-orchestration-service` | 现有格式不接受小写和连字符 | 改为服务名格式 `[a-z][a-z0-9-]{0,63}`；列 `source_system VARCHAR(64)` 与注释"非固定枚举"无需改 DDL |
| 重复提交（AC-04） | Processor 先查 `findSource`，并发时唯一键 `uk_notify_source (source_system, source_event_key)` 兜底，均抛 `ConcurrentRequestException` | HTTP 409 | 无（公共异常已映射 409） | 保持 |
| 错误合同（AC-05） | Processor 使用公共异常：`InvalidParameterException`/`BizCheckFailException`（400）、`ConcurrentRequestException`（409）、`ServiceUnavailableException`（503） | 稳定 Problem `code` | 无 | 不新增错误码（见 §3.4） |
| 日志（AC-06） | `web.logging.metadata-only-path-prefixes` 未包含通知路径；普通端点会记录完整 Body | 只记元数据 | 缺少前缀 | 增加 `/notifications` |
| 开关（AC-07） | Processor `@ConditionalOnProperty(notification.enabled)` | 关闭时不暴露接口 | Controller 未存在 | Controller 同一条件 |

| 系统/仓库 | 影响点 | 是否改代码/模型/配置 | 依赖与风险 |
|---|---|---|---|
| Platform `platform-support-api` | 新增 API 接口与 2 个 Body class | 改代码 | 轻量模块，不增加依赖 |
| Platform `platform-support-delivery` | `sourceSystem` 校验格式 | 改代码 | 只影响测试 Builder；无生产调用方 |
| Platform `platform-support-web` | Controller、Converter、调用方配置、日志前缀 | 改代码与配置 | 默认白名单为空，全部拒绝 |
| Platform 测试 | HTTP 入口测试 | 新增测试 | 复用现有 Vault/SES/Mock Provider 设施 |
| 数据库 / PDManer | 无 | 否 | 唯一键已按来源系统区分 |
| Gateway / BFF / 其他业务系统 | 无 | 否 | 内部调用不经 Gateway；首个调用方另立需求 |
| Vault | 无 | 否 | 沿用已有 `PLATFORM_NOTIFICATION_EMAIL_V1` 授权 |

## 2. 主流程

```text
调用方（本地事务已提交）
  → POST /platform-support/notifications/v1/requests   Header: X-Caller-Service
    → NotificationController（不开事务）
      1. NotificationCallers.authorize(caller, templateCode)   未登记或越权 → 400，不调用 Vault、不写库
      2. NotificationConverter：Body → SubmitNotificationRequest，sourceSystem = caller
      3. SubmitNotificationProcessor.process（现有）
           校验 → 查重 → 模板/路由/账号 → 事务外 Vault Protect → 一次本地事务保存并登记补偿任务
      4. 返回 requestId、acceptedCount
```

调用方授权在 Processor 之前完成，属于 HTTP 边界的身份规则；Processor 仍只接受可信的 `sourceSystem`，模块内入口行为不变。

## 3. 接口合同

### 3.1 API

`platform-support-api` 新增包 `api/notification`：

```java
/**
 * 普通通知内部提交契约；业务系统在本地事务提交后经私网调用，不经过 Gateway 与 BFF。
 * <li>X-Caller-Service 为过渡信任：部署必须限制私网访问，它不是强身份认证。</li>
 * <li>无 Idempotency-Key；去重依据为调用方 + sourceEventKey，超时后用原 sourceEventKey 重试。</li>
 */
@HttpExchange(url = "/notifications/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface NotificationCommandApi {

    /**
     * 提交普通通知
     *
     * @param callerService 调用方服务名，须在 notification.http.callers 中登记
     * @param requestBody 模板、语言、参数与收件人
     * @return 受理结果
     *         <li>200 表示已受理，不表示邮件已发出或送达。</li>
     *         <li>409 表示同一调用方的该 sourceEventKey 已受理过，调用方应视为成功，不得换新 key 重发。</li>
     *         <li>400/422 不重试；503 未写库，可用原 sourceEventKey 重试。</li>
     */
    @PostExchange("/requests")
    SubmitNotificationResponseBody submitNotification(@RequestHeader("X-Caller-Service") String callerService,
        @Valid @RequestBody SubmitNotificationRequestBody requestBody);
}
```

### 3.2 请求 `SubmitNotificationRequestBody`

| 字段 | 类型 | 必填 | 约束 | 说明 |
|---|---|---|---|---|
| `tenantId` | String | 否 | 规范正整数字符串 `[1-9][0-9]{0,18}` | 请求归属；平台通知为空。过渡信任下 Platform 不校验租户权限 |
| `sourceEventKey` | String | 是 | ≤128，无首尾空白与控制字符 | 调用方内的一次通知意图 |
| `templateCode` | String | 是 | `[A-Z][A-Z0-9_]{0,63}` | 须在该调用方的白名单内 |
| `defaultLocale` | String | 是 | BCP 47，≤35 | 无语言回退 |
| `defaultParameters` | Map<String,String> | 否 | ≤64 项；键 `[A-Za-z][A-Za-z0-9_]{0,63}`；值非空 ≤2048 | 禁止 OTP、认证链接、密码、凭据 |
| `recipients` | List<Recipient> | 是 | 1–100；规范化后重复邮箱整单拒绝 | 每人一封 |
| `recipients[].email` | String | 是 | ≤254 | 服务端规范化后存 Vault |
| `recipients[].locale` | String | 否 | BCP 47，≤35 | 覆盖默认语言 |
| `recipients[].parameters` | Map<String,String> | 否 | 同 `defaultParameters` | 按键覆盖 |

Body 没有 `sourceSystem` 字段。按平台默认 Jackson 配置，未知字段被忽略，因此传入同名字段也不会影响落库值（AC-03）。

示例：

```http
POST /platform-support/notifications/v1/requests
X-Caller-Service: payment-orchestration-service
Content-Type: application/json

{
  "tenantId": "10001",
  "sourceEventKey": "refund-succeeded:880001",
  "templateCode": "PAYMENT_REFUND_SUCCEEDED_EMAIL",
  "defaultLocale": "en-US",
  "defaultParameters": { "refundAmount": "12.50", "currency": "USD" },
  "recipients": [ { "email": "buyer@example.com" } ]
}
```

### 3.3 响应 `SubmitNotificationResponseBody`

```json
{ "requestId": "7350001234567890", "acceptedCount": 1 }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `requestId` | String | 通知请求 ID（64 位 ID 用字符串） |
| `acceptedCount` | int | 受理的独立收件人数 |

### 3.4 错误

全部使用公共异常和 Foundation Problem，不新增业务错误码：调用方只需要区分"成功 / 已受理过 / 不重试 / 可重试"，HTTP 状态已经够用。

| 情况 | 状态 | `code` | 写库 / Vault |
|---|---|---|---|
| 缺少 `X-Caller-Service` | 400 | `INVALID_REQUEST` | 否 |
| 调用方未登记、模板不在其白名单（含空 Header） | 400 | `BUSINESS_CHECK_FAILED` | 否 |
| Body 字段校验失败 | 422 | `VALIDATION_ERROR` + `errors[]` | 否 |
| Body 超过 1 MB | 413 | 公共映射 | 否 |
| 重复邮箱、非法语言/参数、`tenantId` 超出 long | 400 | `INVALID_REQUEST` | 否 |
| 模板/路由/账号未启用、缺少所需语言 | 400 | `BUSINESS_CHECK_FAILED` | 否 |
| 同一调用方 `sourceEventKey` 已受理 | 409 | `CONCURRENT_REQUEST` | 否 |
| Vault 不可达 | 503 | `DEPENDENCY_UNAVAILABLE`（Vault Client 既有码） | 否 |
| 发送账号无适配器 | 503 | `SERVICE_UNAVAILABLE` | 否 |

`detail` 只用固定中文提示，不包含邮箱、参数值、模板正文或调用方以外的输入。上表 code 已由阶段 4 的 HTTP 测试固定。

## 4. 调用方白名单配置

位于 `platform-support-web`（HTTP 边界规则），`NotificationCallerProperties`，前缀 `notification.http`：

```yaml
notification:
  http:
    # 调用方服务名 → 可用的普通邮件模板编码；默认空，全部拒绝
    callers:
      payment-orchestration-service:
        - PAYMENT_REFUND_SUCCEEDED_EMAIL
```

- 服务名 `[a-z][a-z0-9-]{0,63}`，与 `X-Caller-Service` 精确比较，大小写敏感。
- 模板编码同 `templateCode` 格式；每个调用方至少一个，列表内不重复。不合法时启动失败（AC-07）。
- 只在 `notification.enabled=true` 时绑定和校验。
- 不检查模板是否在库中存在：模板状态由提交时的 Processor 检查。

## 5. 代码落点

| 模块 | 类 / 文件 | 改动 |
|---|---|---|
| api | `api/notification/NotificationCommandApi` | 新增 |
| api | `api/notification/request/SubmitNotificationRequestBody`（含嵌套 `Recipient`） | 新增 |
| api | `api/notification/response/SubmitNotificationResponseBody` | 新增 |
| delivery | `SubmitNotificationRequest.sourceSystem` | 校验改为服务名格式，注释改为"调用方服务名" |
| web | `ext/rest/notification/NotificationController` | 新增：实现 API，`@ConditionalOnProperty(notification.enabled)`；授权 → 转换 → Processor |
| web | `ext/rest/notification/NotificationConverter` | 新增：Body ↔ 模块 Request/Response，`tenantId` 字符串转 Long，`requestId` 转字符串 |
| web | `configuration/NotificationCallerProperties` | 新增：白名单绑定、启动校验、`authorize(caller, templateCode)` |
| web | `application.yml` | `notification.http.callers: {}`；`web.logging.metadata-only-path-prefixes` 增加 `/notifications` |

不开事务、不加锁：唯一键已保护并发重复提交；Processor 原有的事务外 Vault 和单次本地事务不变。

## 6. 测试计划

新增 `auto/notification/NotificationHttpApiTest`，经真实 HTTP（测试端口）调用 Controller，走真实 Processor、Vault Mock 端点、本地 MySQL，复用现有通知 Fixture；测试 Profile 登记一个测试调用方及其模板。

| 用例 | 覆盖 |
|---|---|
| `test_normalFlow` | 200；`source_system` 为调用方服务名；Request/Delivery/补偿任务落库（AC-01） |
| `test_missingCaller` / `test_unknownCaller` / `test_templateNotAllowed` | 400；Vault Protect 次数 0；无落库（AC-02） |
| `test_sourceSystemInBodyIgnored` | Body 带 `sourceSystem` 仍保存调用方服务名（AC-03） |
| `test_duplicateSourceEventKey` / `test_sameKeyDifferentCaller` | 409 且只有一条 Request；另一调用方同 key 成功（AC-04） |
| `test_invalidBody` / `test_templateDisabled` / `test_vaultUnavailable` | 422 + `errors[]`；400；503 且无落库（AC-05） |
| 上述用例断言 | 日志与 Problem 正文不含邮箱、参数值（AC-06） |
| `NotificationCallerPropertiesTest` | 非法服务名、空模板列表、重复模板启动失败；`notification.enabled=false` 时无 Controller Bean（AC-07） |

已有 `SubmitNotificationTest` 等改用服务名格式的 `sourceSystem`。验证：全服务 `./mvnw -o verify`、Delivery 覆盖率、`npm --prefix requirements-site test`（AC-08）。

## 7. 文档同步（AC-09）

- Delivery 规划 `requirement.md`、`api.md`、`technique.md`：HTTP 开放时机与过渡信任方式的修订说明，链接本需求。
- `requirements-site/content/requirements.ts` 的 PLT-I09：只在描述与实现状态不一致时调整，不改编号和调用方范围。
- Platform README、测试 README、`CURRENT_IMPLEMENTATION.md` 新节。

## 8. 风险与限制

1. `X-Caller-Service` 可伪造（已接受）：影响限于以被授权模板向任意邮箱发信；部署必须保证 Platform 只在私网可达。
2. 白名单变更需要改配置并重启；没有管理页面。
3. Body 上限 1 MB（阶段 2+3 确认时加入），超限 413。
4. `tenantId` 不校验：调用方必须自己保证归属正确。

## 9. 实现偏差（阶段 4）

1. **1 MB 上限的实现**：`NotificationRequestConfiguration` 只为 `SubmitNotificationRequestBody` 安装有界 JSON 转换器（照 `ConfigurationManagementRequestConfiguration` 的做法），读取超过 1 MB 即 413；并关闭 Jackson 错误位置中的源内容，解析失败只给固定提示，避免邮箱进入异常消息。其他 JSON 策略沿用应用默认（未知字段忽略）。
2. **Vault 不可达的错误码**是 Vault Client 既有的 `DEPENDENCY_UNAVAILABLE`（503），不是 Foundation 的 `SERVICE_UNAVAILABLE`；调用方按 503 处理即可。
3. **白名单配置类放在 `DeliveryConfiguration` 上启用**（同为 `notification.enabled` 条件），没有新增配置类。
4. **测试来源系统**由 `NOTIFICATION_TEST` 改为服务名格式 `notification-test`；TestMapper 按前缀清理，覆盖第二个测试调用方 `notification-test-peer`。
