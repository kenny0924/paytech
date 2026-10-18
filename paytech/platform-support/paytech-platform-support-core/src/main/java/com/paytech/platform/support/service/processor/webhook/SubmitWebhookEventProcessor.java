package com.paytech.platform.support.service.processor.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.ClientTenantQueryService;
import com.paytech.platform.support.delivery.service.processor.AcceptWebhookEventProcessor;
import com.paytech.platform.support.delivery.service.processor.request.AcceptWebhookEventRequest;
import com.paytech.platform.support.service.processor.webhook.request.SubmitWebhookEventRequest;

import lombok.AllArgsConstructor;

/**
 * 内部 HTTP 事件受理：按 Client 解析 CLIENT 租户后交给 Delivery 模块受理
 * <li>Client 未开通平台访问时返回业务错误，不写库</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "webhook.enabled", havingValue = "true")
public class SubmitWebhookEventProcessor extends AbstractProcessor<SubmitWebhookEventRequest, Void> {

    private final ClientTenantQueryService tenants;
    private final AcceptWebhookEventProcessor accept;

    @Override
    protected Void processRequest(SubmitWebhookEventRequest request) {
        Long tenantId = tenants.findClientTenantId(request.getClientId());
        if (tenantId == null) {
            throw new BizCheckFailException("Client 未开通平台访问: " + request.getClientId());
        }
        return accept.process(acceptRequest(request, tenantId));
    }

    /** 来源系统取已授权的调用方服务名。 */
    static AcceptWebhookEventRequest acceptRequest(SubmitWebhookEventRequest request, long tenantId) {
        var accept = new AcceptWebhookEventRequest();
        accept.setSourceSystem(request.getCallerService());
        accept.setTenantId(tenantId);
        accept.setSourceVersion(request.getSourceVersion());
        accept.setSourcePayloadHash(request.getSourcePayloadHash());
        accept.setPayloadHash(request.getPayloadHash());
        accept.setEnvelope(request.getEnvelope());
        return accept;
    }
}
