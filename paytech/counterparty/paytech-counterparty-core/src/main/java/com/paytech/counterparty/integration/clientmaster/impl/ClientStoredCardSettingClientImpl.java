package com.paytech.counterparty.integration.clientmaster.impl;

import org.springframework.stereotype.Component;

import com.paytech.client.master.api.storedcard.ClientStoredCardSettingApi;
import com.paytech.counterparty.domain.CounterpartyUpstreamProtocolException;
import com.paytech.counterparty.integration.clientmaster.ClientStoredCardSettingClient;

import lombok.AllArgsConstructor;

/** 只适配 client-master 已存卡开关 DTO；HTTP、MDC 和错误解码交给公共客户端。 */
@Component
@AllArgsConstructor
public class ClientStoredCardSettingClientImpl implements ClientStoredCardSettingClient {
    private final ClientStoredCardSettingApi settings;

    /** 响应必须回显请求的 Client。 */
    @Override
    public boolean storedCardEnabled(long clientId) {
        var body = settings.storedCardSetting(Long.toString(clientId));
        if (body == null || !Long.toString(clientId).equals(body.getClientId())) {
            throw new CounterpartyUpstreamProtocolException("client-master 已存卡开关响应无效");
        }
        return body.isEnabled();
    }
}
