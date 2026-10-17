package com.paytech.platform.support.service.processor.webhook.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 内部 HTTP 提交的冻结事件；调用方已通过白名单，租户在处理器内按 Client 解析。 */
@Getter
@AllArgsConstructor
public class SubmitWebhookEventRequest {

    /** 已授权的调用方服务名，作为来源系统。 */
    private final String callerService;

    /** CCT Client ID。 */
    private final long clientId;

    /** 来源资源版本。 */
    private final int sourceVersion;

    /** data.object 规范字节摘要。 */
    private final String sourcePayloadHash;

    /** Envelope 规范字节摘要。 */
    private final String payloadHash;

    /** 冻结的公开 Envelope。 */
    private final JsonNode envelope;
}
