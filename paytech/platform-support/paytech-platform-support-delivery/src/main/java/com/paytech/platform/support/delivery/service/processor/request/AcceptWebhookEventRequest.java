package com.paytech.platform.support.delivery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 模块内受理冻结事件；来源系统与租户由调用方确认，不直接照搬外部输入。 */
@Getter
@Setter
public class AcceptWebhookEventRequest {

    /**
     * 来源系统：调用方服务名
     * <li>HTTP 入口取已通过白名单的 X-Caller-Service，不取自 Body</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String sourceSystem;

    /** 已解析的 CLIENT 租户。 */
    @NotNull
    @Positive
    private Long tenantId;

    /** 来源资源版本，只用于重复比较。 */
    @Positive
    private int sourceVersion;

    /** data.object 规范字节摘要。 */
    @NotNull
    @Pattern(regexp = "[0-9a-f]{64}")
    private String sourcePayloadHash;

    /** Envelope 规范字节摘要。 */
    @NotNull
    @Pattern(regexp = "[0-9a-f]{64}")
    private String payloadHash;

    /** 冻结的公开 Envelope。 */
    @NotNull
    private JsonNode envelope;
}
