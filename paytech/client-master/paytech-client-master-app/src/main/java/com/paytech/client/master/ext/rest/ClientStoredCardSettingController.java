package com.paytech.client.master.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.storedcard.ClientStoredCardSettingApi;
import com.paytech.client.master.api.storedcard.response.ClientStoredCardSettingResponseBody;
import com.paytech.client.master.domainservice.service.ClientStoredCardSettingQueryService;
import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;

/** Client 级已存卡开关的只读入站（D42）；首期调用方为第 03 步的 consumer。 */
@RestController
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@AllArgsConstructor
public class ClientStoredCardSettingController implements ClientStoredCardSettingApi {
    private final ClientStoredCardSettingQueryService service;

    /** 规范 Client ID 后读取开关；没有记录视为未开通。 */
    @Override
    public ClientStoredCardSettingResponseBody storedCardSetting(String clientId) {
        long id;
        try {
            id = CheckoutCandidateConverter.positiveId(clientId);
        } catch (com.paytech.foundation.common.error.FailException invalid) {
            throw new InvalidParameterException("Client ID 必须是规范正十进制 BIGINT");
        }
        return new ClientStoredCardSettingResponseBody(Long.toString(id), service.isEnabled(id));
    }
}
