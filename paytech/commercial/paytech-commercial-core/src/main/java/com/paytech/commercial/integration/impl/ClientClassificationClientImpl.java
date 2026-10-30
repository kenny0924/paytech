package com.paytech.commercial.integration.impl;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

import com.paytech.client.master.api.classification.ClientBusinessClassificationApi;
import com.paytech.client.master.api.classification.response.ClientBusinessClassificationResponseBody;
import com.paytech.commercial.integration.ClientClassificationClient;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/**
 * 经 client-master-api 读取 Client 已确认的主 MCC（REQ-02 technique.md §10.1）
 * <li>失败关闭：任何 4xx / 5xx（含 Client 不存在 404）、超时、无实例或响应不符合合同一律 OWNER_UNAVAILABLE；调用方记 ④ UNKNOWN。</li>
 */
@AllArgsConstructor
public class ClientClassificationClientImpl implements ClientClassificationClient {
    private final ClientBusinessClassificationApi api;

    /** 未确认时不采用分类码。 */
    @Override
    public String confirmedPrimaryMcc(long clientId) {
        ClientBusinessClassificationResponseBody response;
        try {
            response = api.getBusinessClassification(Long.toString(clientId));
        } catch (RuntimeException failure) {
            throw new ErrorException(OWNER_UNAVAILABLE, "client-master 经营分类查询不可用", failure);
        }
        if (!Long.toString(clientId).equals(response.getClientId())
            || response.getMccCode() != null && !response.getMccCode().matches("[0-9]{4}")) {
            throw new ErrorException(OWNER_UNAVAILABLE, "client-master 经营分类响应不符合合同: " + clientId);
        }
        return response.isConfirmed() ? response.getMccCode() : null;
    }
}
