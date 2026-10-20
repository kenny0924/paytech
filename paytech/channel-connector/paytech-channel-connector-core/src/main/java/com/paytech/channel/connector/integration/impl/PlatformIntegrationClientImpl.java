package com.paytech.channel.connector.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.integration.PlatformIntegrationClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.standardapi.IntegrationLookupApi;
import com.paytech.platform.support.api.standardapi.response.IntegrationExistenceResponseBody;

import lombok.AllArgsConstructor;

/**
 * 经 Platform 内部接入方存在性合同查询；不存在时 Platform 返回 200 与 exists=false
 * <li>任何调用失败（422、5xx、超时、网络、无效响应）都按系统错误报告，调用方不据此写库。</li>
 */
@Component
@AllArgsConstructor
public class PlatformIntegrationClientImpl implements PlatformIntegrationClient {
    private final IntegrationLookupApi integrationLookupApi;

    /** 查询接入方是否存在；失败统一转为 ErrorException 并保留原因。 */
    @Override
    public boolean exists(String integrationCode) {
        IntegrationExistenceResponseBody body;
        try {
            body = integrationLookupApi.integrationExistence(integrationCode);
        } catch (RuntimeException failure) {
            throw new ErrorException("Platform 接入方查询失败: " + integrationCode, failure);
        }
        if (body == null || !integrationCode.equals(body.getIntegrationCode())) {
            throw new ErrorException("Platform 接入方查询失败: " + integrationCode);
        }
        return body.isExists();
    }
}
