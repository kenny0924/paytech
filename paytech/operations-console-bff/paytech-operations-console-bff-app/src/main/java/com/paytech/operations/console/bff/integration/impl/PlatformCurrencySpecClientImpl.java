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
import com.paytech.platform.support.api.referencedata.ReferenceCurrencyQueryApi;

import lombok.AllArgsConstructor;

/**
 * Platform 币种规则的 HTTP 转发：每个入口只调用一次 Platform，不重试
 * <li>写操作把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；请求幂等由 Gateway 负责。</li>
 * <li>Platform 422 的字段路径只投影到本次页面请求体内存在的路径。</li>
 * <li>币种页列表与财务页下拉都读参考数据币种接口；前者取全部状态与完整字段，后者由 Platform 按状态筛选。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformCurrencySpecClientImpl implements PlatformCurrencySpecClient {
    private final ReferenceCurrencyQueryApi referenceApi;
    private final CurrencySpecManagementApi managementApi;

    /** 全部币种（含数字代码、名称、符号与图标），逐条核对后投影。 */
    @Override
    public CurrencySpecListResponseBody listCurrencySpecs() {
        return CurrencySpecContractConverter.toBff(referenceApi.listCurrencies(null, null));
    }

    /** 状态筛选交给 Platform（为空返回全部状态），按币种代码升序；回执逐条核对后投影。 */
    @Override
    public CurrencyListResponseBody listCurrencies(String status) {
        return CurrencySpecContractConverter.toFinance(referenceApi.listCurrencies(status, null), status);
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

    /** 修改；回执须为本次币种（修改不改变状态，DRAFT 与已启用 / 停用均可，后者只能换图标）。 */
    @Override
    public ConfigurationMutationResponseBody updateCurrencySpec(OperatorContext context, String currency,
        UpdateCurrencySpecRequestBody request) {
        try {
            var result = managementApi.updateCurrencySpec(context.accountId(), context.sessionId(), currency,
                CurrencySpecContractConverter.toPlatform(request));
            return CurrencySpecContractConverter.toBff(result, currency, null);
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
