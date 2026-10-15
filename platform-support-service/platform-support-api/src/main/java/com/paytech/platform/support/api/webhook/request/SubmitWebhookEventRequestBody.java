package com.paytech.platform.support.api.webhook.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 来源系统提交的冻结 Webhook 事件；来源系统取自调用方 Header，事件身份与类型取自 Envelope。 */
@Getter
@Setter
public class SubmitWebhookEventRequestBody {

    /**
     * 事件归属的 Client ID
     * <li>规范正整数字符串；Platform 按它解析 CLIENT Tenant，未开通时返回 400。</li>
     */
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "clientId 必须为规范正整数字符串")
    private String clientId;

    /**
     * 来源资源版本
     * <li>1..2147483647；只参与重复提交比较。</li>
     */
    @Positive
    @Max(Integer.MAX_VALUE)
    private long sourceVersion;

    /**
     * data.object 的 RFC 8785 规范字节 SHA-256
     * <li>64 位小写十六进制；Platform 重新计算，不一致返回 400。</li>
     */
    @NotNull
    @Pattern(regexp = "[0-9a-f]{64}", message = "sourcePayloadHash 必须为 64 位小写十六进制")
    private String sourcePayloadHash;

    /**
     * 完整 Envelope 的 RFC 8785 规范字节 SHA-256
     * <li>64 位小写十六进制；Platform 重新计算，不一致返回 400。</li>
     */
    @NotNull
    @Pattern(regexp = "[0-9a-f]{64}", message = "payloadHash 必须为 64 位小写十六进制")
    private String payloadHash;

    /**
     * 冻结的公开 Envelope
     * <li>只允许 eventId、eventType、schemaVersion、createdAt、data 五个顶层键，data 只允许 object。</li>
     * <li>规范化后不超过 4096 字节；Retry 与 Replay 原样发送给 Client。</li>
     */
    @NotNull
    private JsonNode envelope;
}
