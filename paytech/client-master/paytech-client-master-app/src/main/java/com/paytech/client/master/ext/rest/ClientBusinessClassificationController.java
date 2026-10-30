package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.classification.ClientBusinessClassificationApi;
import com.paytech.client.master.api.classification.response.ClientBusinessClassificationResponseBody;
import com.paytech.client.master.domain.classification.ClientBusinessClassificationView;
import com.paytech.client.master.domainservice.service.ClientBusinessClassificationQueryService;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;

/** Client 主经营分类的服务间只读入站（REQ-02）；首期调用方为 commercial 开通项前置门禁。 */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@AllArgsConstructor
public class ClientBusinessClassificationController implements ClientBusinessClassificationApi {
    private final ClientBusinessClassificationQueryService service;

    /** 规范 Client ID 后读取；非规范 ID 400，Client 不存在 404 CLIENT_NOT_FOUND。 */
    @Override
    public ClientBusinessClassificationResponseBody getBusinessClassification(String clientId) {
        long id;
        try {
            id = CheckoutCandidateConverter.positiveId(clientId);
        } catch (FailException invalid) {
            throw new InvalidParameterException("Client ID 必须是规范正十进制 BIGINT");
        }
        return response(service.getBusinessClassification(id));
    }

    /** 转换响应。 */
    static ClientBusinessClassificationResponseBody response(ClientBusinessClassificationView view) {
        var body = new ClientBusinessClassificationResponseBody();
        body.setClientId(Long.toString(view.getClientId()));
        body.setConfirmed(view.isConfirmed());
        body.setIndustryCode(view.getIndustryCode());
        body.setMccCode(view.getMccCode());
        return body;
    }
}
