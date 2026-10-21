package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.operations.console.bff.integration.PlatformCurrencySpecClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.ChangeCurrencySpecStatusRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CreateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CurrencySpecListResponseBody;
import com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.UpdateCurrencySpecRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CurrencyListResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.CurrencySpecManagementApi;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;

import lombok.AllArgsConstructor;

/**
 * Platform 币种规则的 HTTP 转发：每个入口只调用一次 Platform，不重试
 * <li>写操作把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；请求幂等由 Gateway 负责。</li>
 * <li>Platform 422 的字段路径只投影到本次页面请求体内存在的路径。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformCurrencySpecClientImpl implements PlatformCurrencySpecClient {
    private final CurrencySpecQueryApi queryApi;
    private final CurrencySpecManagementApi managementApi;

    /** 全部币种规则，逐条核对后投影。 */
    @Override
    public CurrencySpecListResponseBody listCurrencySpecs() {
        return CurrencySpecContractConverter.toBff(queryApi.listCurrencySpecs());
    }

    /** Platform 查询接口不带筛选，按状态在 BFF 筛选。 */
    @Override
    public CurrencyListResponseBody listCurrencies(String status) {
        return CurrencySpecContractConverter.toFinance(listCurrencySpecs(), status);
    }

    /** 新建草稿；回执须为本次币种且状态为 DRAFT。 */
    @Override
    public ConfigurationMutationResponseBody createCurrencySpec(OperatorContext context,
        CreateCurrencySpecRequestBody request) {
        try {
            var result = managementApi.createCurrencySpec(context.accountId(), context.sessionId(),
                CurrencySpecContractConverter.toPlatform(request));
            return CurrencySpecContractConverter.toBff(result, request.getCurrency(), CurrencySpecStatus.DRAFT.getCode());
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 修改草稿；回执须为本次币种且仍为 DRAFT。 */
    @Override
    public ConfigurationMutationResponseBody updateCurrencySpec(OperatorContext context, String currency,
        UpdateCurrencySpecRequestBody request) {
        try {
            var result = managementApi.updateCurrencySpec(context.accountId(), context.sessionId(), currency,
                CurrencySpecContractConverter.toPlatform(request));
            return CurrencySpecContractConverter.toBff(result, currency, CurrencySpecStatus.DRAFT.getCode());
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 启用或停用；回执须为本次币种且状态等于目标状态。 */
    @Override
    public ConfigurationMutationResponseBody changeCurrencySpecStatus(OperatorContext context, String currency,
        ChangeCurrencySpecStatusRequestBody request) {
        try {
            var result = managementApi.changeCurrencySpecStatus(context.accountId(), context.sessionId(), currency,
                CurrencySpecContractConverter.toPlatform(request));
            return CurrencySpecContractConverter.toBff(result, currency, request.getStatus());
        } catch (HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }
}
