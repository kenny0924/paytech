package com.paytech.operations.console.bff.service.model;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

/**
 * Operations 代 Client 维护 Webhook 端点、签名密钥，查询投递并 Replay
 * <li>沿用 BFF 配置管理的 record 模型，供严格 Body 解析器逐字段检查；字段与 Platform 合同一一对应</li>
 * <li>时间保持 Platform 的 UTC 字符串原样；新建与轮换响应含只出现一次的密钥明文，字符串形式不输出</li>
 */
public final class WebhookManagementModels {
    /** 只承载类型，禁止创建实例。 */
    private WebhookManagementModels() {}

    /** Client 搜索结果项。
     * @param clientId CCT Client ID。
     * @param clientName Client 名称。
     * @param ownerStatus 来源业务状态：PENDING、ACTIVE、SUSPENDED、TERMINATED。
     * @param securityStatus Platform 安全状态：ACTIVE、SUSPENDED。
     * @param endpointCount 端点数量（含已停用）。
     */
    public record WebhookClientSummary(String clientId, String clientName, String ownerStatus, String securityStatus,
        int endpointCount) {}

    /** 当前签名密钥概况。
     * @param rotationNo 当前 ACTIVE 密钥是第几把。
     * @param createTime 当前 ACTIVE 密钥的创建时间，UTC。
     * @param previousValidUntil 上一把密钥宽限截止时间，UTC；不在宽限期内为 null。
     */
    public record SigningSecretItem(int rotationNo, String createTime, String previousValidUntil) {}

    /** 端点及当前签名密钥概况；不含密钥值。
     * @param endpointId 端点 ID。
     * @param url HTTPS 接收地址。
     * @param eventTypes 订阅的事件类型。
     * @param status 端点状态：ACTIVE、DISABLED。
     * @param signingSecret 当前签名密钥概况。
     * @param version 当前版本。
     * @param createTime 创建时间，UTC。
     * @param updateTime 更新时间，UTC。
     */
    public record WebhookEndpointResponseBody(String endpointId, String url, List<String> eventTypes, String status,
        SigningSecretItem signingSecret, int version, String createTime, String updateTime) {}

    /** 新建端点结果；密钥明文只在本响应中出现一次。
     * @param endpointId 新端点 ID。
     * @param version 端点版本，固定为 1。
     * @param signingSecret 首个签名密钥明文。
     */
    public record CreateWebhookEndpointResponseBody(String endpointId, int version, String signingSecret) {

        /** 不输出密钥。 */
        @Override
        public String toString() {
            return "CreateWebhookEndpointResponseBody[endpointId=" + endpointId + ", version=" + version + "]";
        }
    }

    /** 端点修改或启停结果。
     * @param endpointId 端点 ID。
     * @param version 写入后的版本。
     */
    public record WebhookEndpointMutationResponseBody(String endpointId, int version) {}

    /** 轮换结果；新密钥明文只在本响应中出现一次。
     * @param rotationNo 新密钥是第几把。
     * @param signingSecret 新签名密钥明文。
     * @param previousValidUntil 旧密钥宽限截止时间，UTC；立即作废时为 null。
     */
    public record RotateWebhookSecretResponseBody(int rotationNo, String signingSecret, String previousValidUntil) {

        /** 不输出密钥。 */
        @Override
        public String toString() {
            return "RotateWebhookSecretResponseBody[rotationNo=" + rotationNo + "]";
        }
    }

    /** 测试事件的同步发送结果。
     * @param result SUCCEEDED 或 FAILED。
     * @param httpStatus 端点返回的状态码，未收到响应为 null。
     * @param failureCode 失败原因代码，成功为 null。
     * @param durationMs 发送耗时毫秒数，未发出请求为 null。
     */
    public record WebhookTestResponseBody(String result, Integer httpStatus, String failureCode, Integer durationMs) {}

    /** 投递列表项。
     * @param deliveryId 投递 ID。
     * @param eventId 公开 eventId。
     * @param eventType 事件类型。
     * @param endpointId 目标端点 ID。
     * @param status 投递状态：CREATED、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD、CANCELLED。
     * @param attemptCount 已创建的尝试数量。
     * @param lastAttemptTime 最近一次尝试开始时间，UTC；尚未尝试为 null。
     * @param replayOfDeliveryId Replay 来源投递 ID；原始投递为 null。
     * @param createTime 创建时间，UTC。
     */
    public record WebhookDeliverySummary(String deliveryId, String eventId, String eventType, String endpointId,
        String status, int attemptCount, String lastAttemptTime, String replayOfDeliveryId, String createTime) {}

    /** 冻结事件。
     * @param eventId 公开 eventId。
     * @param eventType 事件类型。
     * @param schemaVersion Envelope 的 schemaVersion。
     * @param sourceSystem 来源系统服务名。
     * @param envelope 冻结的公开 Envelope，原样透传。
     */
    public record WebhookEventItem(String eventId, String eventType, int schemaVersion, String sourceSystem,
        JsonNode envelope) {}

    /** 一次投递尝试。
     * @param attemptNo 尝试序号，从 1 开始。
     * @param requestUrl 本次请求地址。
     * @param rotationNo 主签名密钥是第几把。
     * @param graceRotationNo 宽限签名密钥是第几把，无宽限为 null。
     * @param status 尝试状态：PROCESSING、SUCCEEDED、FAILED。
     * @param httpStatus 响应状态码，未收到响应为 null。
     * @param failureCode 失败原因代码，成功为 null。
     * @param durationMs 耗时毫秒数，未完成为 null。
     * @param startedTime 开始时间，UTC。
     * @param finishedTime 结束时间，UTC；未完成为 null。
     */
    public record WebhookAttemptItem(int attemptNo, String requestUrl, int rotationNo, Integer graceRotationNo,
        String status, Integer httpStatus, String failureCode, Integer durationMs, String startedTime,
        String finishedTime) {}

    /** 投递详情：投递、冻结事件与全部尝试。
     * @param deliveryId 投递 ID。
     * @param eventId 公开 eventId。
     * @param eventType 事件类型。
     * @param endpointId 目标端点 ID。
     * @param status 投递状态。
     * @param attemptCount 已创建的尝试数量。
     * @param lastAttemptTime 最近一次尝试开始时间，UTC。
     * @param replayOfDeliveryId Replay 来源投递 ID。
     * @param createTime 创建时间，UTC。
     * @param requestedBy Replay 操作人账号 ID；原始投递为 null。
     * @param event 冻结事件。
     * @param attempts 按尝试序号升序的全部尝试。
     */
    public record WebhookDeliveryDetailResponseBody(String deliveryId, String eventId, String eventType,
        String endpointId, String status, int attemptCount, String lastAttemptTime, String replayOfDeliveryId,
        String createTime, String requestedBy, WebhookEventItem event, List<WebhookAttemptItem> attempts) {}

    /** Replay 结果。
     * @param deliveryId 新投递 ID。
     */
    public record ReplayWebhookDeliveryResponseBody(String deliveryId) {}

    /** 新建或修改端点的地址与订阅事件类型。
     * @param url HTTPS 接收地址，最长 1000 字符。
     * @param eventTypes 订阅的事件类型，1—8 项。
     * @param expectedVersion 当前版本；新建为 0。
     * @param reason 操作原因。
     */
    public record SaveWebhookEndpointRequestBody(
        @NotBlank @Size(max = 1000) String url,
        @NotEmpty @Size(max = 8) List<@NotNull String> eventTypes,
        @PositiveOrZero int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}

    /** 启用或停用端点。
     * @param status 目标状态 ACTIVE 或 DISABLED。
     * @param expectedVersion 当前版本。
     * @param reason 操作原因。
     */
    public record ChangeWebhookEndpointStatusRequestBody(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}

    /** 轮换签名密钥。
     * @param immediate 是否立即作废旧密钥；false 时旧密钥再参与签名 1 小时。
     * @param reason 操作原因。
     */
    public record RotateWebhookSecretRequestBody(boolean immediate, @NotBlank @Size(max = 500) String reason) {}

    /** 重新投递一条已结束的投递。
     * @param reason 操作原因。
     */
    public record ReplayWebhookDeliveryRequestBody(@NotBlank @Size(max = 500) String reason) {}
}
