package com.paytech.commerce.ext.job;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.commerce.domain.webhook.ClientWebhookEvent;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;
import com.paytech.platform.support.api.webhook.WebhookEventApi;
import com.paytech.platform.support.api.webhook.request.SubmitWebhookEventRequestBody;

import lombok.AllArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Client Webhook 事件提交入口：业务事务冻结的 Envelope 在提交后由 Foundation 唤醒，事务外原样提交 Platform 受理。
 * <li>载荷 Hash 在登记时计算；提交前从取出的 Envelope 重新规范化核对，不一致即明确失败，不按最新业务数据重建。</li>
 * <li>Platform 暂不可用、404 与无法识别的响应可重试；Platform 明确拒绝（400/409/422）不再重试并告警。</li>
 * <li>日志只记事件 ID、类型、Client 与错误码，不记载荷。</li>
 */
@Component
@AllArgsConstructor
public class ClientWebhookSubmissionHandler implements CompensationEventHandler {

    /** 冻结事件的补偿任务类型；子类型为公开事件类型。 */
    public static final String MAIN_TYPE = "CLIENT_WEBHOOK_REQUESTED";

    /** 提交 Platform 时声明的调用方服务名，同时作为载荷中的来源系统。 */
    public static final String CALLER_SERVICE = "commerce-service";

    /** 规范化版本标识。 */
    public static final String CANONICALIZATION_VERSION = "RFC8785";

    /** Envelope 与载荷 Schema 版本。 */
    public static final String SCHEMA_VERSION = "1";

    /** Platform Envelope 与 Foundation 补偿载荷共同的字节上限。 */
    public static final int MAX_BYTES = 4096;

    /** 载荷损坏的明确失败代码。 */
    public static final String PAYLOAD_CORRUPTED = "WEBHOOK_PAYLOAD_CORRUPTED";

    private static final Logger LOG = LoggerFactory.getLogger(ClientWebhookSubmissionHandler.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebhookEventApi webhookEventApi;

    /**
     * 构造冻结事件的登记请求：组装 Envelope，按 RFC 8785 规范化后计算两个 SHA-256，并检查大小。
     * <li>纯内存计算，由业务 Service 在本地事务内交给登记器；不登记、不调用 Platform。</li>
     *
     * @param event 业务写入后构造的事件
     * @return 事件 ID 为 eventKey、公开类型为子类型的登记请求
     * @throws ErrorException 规范化失败或 Envelope/载荷超过 4096 字节，调用方业务事务整体回滚
     */
    public static CompensationEventRequest buildEvent(ClientWebhookEvent event) {
        String eventId = event.eventId();
        var envelope = JSON.createObjectNode();
        envelope.put("eventId", eventId);
        envelope.put("eventType", event.getEventType().getCode());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("createdAt", event.getCreatedAt().toString());
        envelope.putObject("data").set("object", JSON.valueToTree(event.getObject()));
        byte[] canonical = canonicalize(envelope);
        if (canonical.length > MAX_BYTES) {
            throw new ErrorException("Client Webhook Envelope 超过 " + MAX_BYTES + " 字节, 事件=" + eventId);
        }
        var payload = JSON.createObjectNode();
        payload.put("clientId", Long.toString(event.getClientId()));
        payload.put("canonicalizationVersion", CANONICALIZATION_VERSION);
        var source = payload.putObject("source");
        source.put("system", CALLER_SERVICE);
        source.put("eventKey", eventId);
        source.put("eventType", event.getEventType().getCode());
        source.put("sourceVersion", event.getSourceVersion());
        source.put("payloadSchemaVersion", SCHEMA_VERSION);
        source.put("sourcePayloadHash", sha256Hex(canonicalize(envelope.get("data").get("object"))));
        source.put("payloadHash", sha256Hex(canonical));
        payload.set("envelope", envelope);
        if (JSON.writeValueAsBytes(payload).length > MAX_BYTES) {
            throw new ErrorException("Client Webhook 补偿载荷超过 " + MAX_BYTES + " 字节, 事件=" + eventId);
        }
        return new CompensationEventRequest(eventId, MAIN_TYPE, event.getEventType().getCode(), payload);
    }

    /** 返回业务事务登记的稳定事件类型。 */
    @Override
    public String eventType() {
        return MAIN_TYPE;
    }

    /**
     * 核对冻结载荷后原样提交 Platform；Platform 以 Client + 调用方 + eventId 幂等，重试不会产生第二个事件。
     * <li>元数据或载荷不符合登记合同时明确失败，不调用 Platform。</li>
     */
    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        if (!eventType().equals(event.eventType()) || event.payloadJson() == null) {
            return CompensationHandlerResult.terminalFailure("INVALID_WEBHOOK_TASK", "Webhook 任务元数据不符合约定");
        }
        SubmitWebhookEventRequestBody body = verifiedBody(event);
        if (body == null) {
            LOG.warn("Client Webhook 冻结载荷校验失败, eventId={}, eventType={}", event.eventKey(), event.subEventType());
            return CompensationHandlerResult.terminalFailure(PAYLOAD_CORRUPTED, "冻结载荷与登记 Hash 不一致");
        }
        return submit(event, body);
    }

    /**
     * 从冻结载荷重建提交请求，并以重新规范化的 Envelope 核对两个 Hash 及事件身份。
     *
     * @return 校验通过的提交请求；载荷损坏时返回 null
     */
    SubmitWebhookEventRequestBody verifiedBody(CompensationEvent event) {
        try {
            JsonNode payload = JSON.readTree(event.payloadJson());
            JsonNode source = payload.path("source");
            JsonNode envelope = payload.path("envelope");
            if (!envelope.path("data").path("object").isObject()
                || !CANONICALIZATION_VERSION.equals(payload.path("canonicalizationVersion").asString(""))
                || !event.eventKey().equals(envelope.path("eventId").asString(""))
                || !event.subEventType().equals(envelope.path("eventType").asString(""))
                || !source.path("sourceVersion").canConvertToLong()) {
                return null;
            }
            String payloadHash = source.path("payloadHash").asString("");
            String sourcePayloadHash = source.path("sourcePayloadHash").asString("");
            if (!payloadHash.equals(sha256Hex(canonicalize(envelope)))
                || !sourcePayloadHash.equals(sha256Hex(canonicalize(envelope.path("data").path("object"))))) {
                return null;
            }
            var body = new SubmitWebhookEventRequestBody();
            body.setClientId(payload.path("clientId").asString(""));
            body.setSourceVersion(source.path("sourceVersion").longValue());
            body.setSourcePayloadHash(sourcePayloadHash);
            body.setPayloadHash(payloadHash);
            body.setEnvelope(envelope);
            return body;
        } catch (JacksonException | ErrorException corrupted) {
            return null;
        }
    }

    /**
     * 事务外调用 Platform 受理接口，并把公共出站异常映射为补偿结果。
     * <li>503/断连/超时、404（Webhook 未开启或路由缺失）及无法识别的响应可重试。</li>
     * <li>400 业务或参数拒绝、409 同一事件内容冲突、422 字段校验失败明确失败并告警。</li>
     */
    CompensationHandlerResult submit(CompensationEvent event, SubmitWebhookEventRequestBody body) {
        try {
            webhookEventApi.submitEvent(CALLER_SERVICE, body);
            return CompensationHandlerResult.success();
        } catch (ServiceUnavailableException unavailable) {
            return CompensationHandlerResult.retryableFailure("PLATFORM_UNAVAILABLE", "Platform Webhook 受理暂不可用");
        } catch (BizCheckFailException | InvalidParameterException | ConcurrentRequestException
            | HttpRequestValidationException rejected) {
            LOG.warn("Client Webhook 事件被 Platform 拒绝, eventId={}, eventType={}, clientId={}, code={}",
                event.eventKey(), event.subEventType(), body.getClientId(), rejected.getCode());
            return CompensationHandlerResult.terminalFailure(rejected.getCode(), "Platform 拒绝受理 Webhook 事件");
        } catch (FailException notFound) {
            return CompensationHandlerResult.retryableFailure("PLATFORM_NOT_FOUND", "Platform Webhook 受理接口不存在");
        } catch (ErrorException invalidResponse) {
            return CompensationHandlerResult.retryableFailure("PLATFORM_INVALID_RESPONSE", "Platform 响应无法识别");
        }
    }

    /** 按已批准 JSON Object Profile 输出 RFC 8785 规范字节，与 Platform 受理端的规范化一致。 */
    static byte[] canonicalize(JsonNode node) {
        try {
            return Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(node));
        } catch (JacksonException | JsonCanonicalizationException invalid) {
            throw new ErrorException("Client Webhook 内容不符合 JSON Object Profile", invalid);
        }
    }

    /** SHA-256 小写十六进制。 */
    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new ErrorException("运行环境缺少 SHA-256", missing);
        }
    }
}
