package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.referencedata.CurrencySpecManagementApi;
import com.paytech.platform.support.api.referencedata.request.ChangeCurrencySpecStatusRequestBody;
import com.paytech.platform.support.api.referencedata.request.CreateCurrencySpecRequestBody;
import com.paytech.platform.support.api.referencedata.request.UpdateCurrencySpecRequestBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.referencedata.service.processor.ChangeCurrencySpecStatusProcessor;
import com.paytech.platform.support.referencedata.service.processor.CreateCurrencySpecProcessor;
import com.paytech.platform.support.referencedata.service.processor.UpdateCurrencySpecProcessor;

import lombok.AllArgsConstructor;

/** 币种规则维护的协议适配器，规则、授权与事务委托 referencedata 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CurrencySpecManagementController implements CurrencySpecManagementApi {

    private final CreateCurrencySpecProcessor createProcessor;
    private final UpdateCurrencySpecProcessor updateProcessor;
    private final CurrencySpecManagementConverter converter;
    private final ChangeCurrencySpecStatusProcessor statusProcessor;

    /** 转换请求后委托新建用例。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_CURRENCY_SPEC_CREATE", resourceType = "CURRENCY_SPEC",
        fallbackResourceId = "CURRENCY_SPEC_CATALOG")
    @Override
    public ConfigurationMutationResponseBody createCurrencySpec(String actor, String session,
        CreateCurrencySpecRequestBody request) {
        return converter.mutation(createProcessor.process(converter.create(actor, session, request)));
    }

    /** 转换请求后委托修改用例。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_CURRENCY_SPEC_UPDATE", resourceType = "CURRENCY_SPEC",
        fallbackResourceId = "CURRENCY_SPEC_CATALOG")
    @Override
    public ConfigurationMutationResponseBody updateCurrencySpec(String actor, String session, String currency,
        UpdateCurrencySpecRequestBody request) {
        return converter.mutation(updateProcessor.process(converter.update(actor, session, currency, request)));
    }

    /** 转换请求后委托启停用例。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_CURRENCY_SPEC_STATUS", resourceType = "CURRENCY_SPEC",
        fallbackResourceId = "CURRENCY_SPEC_CATALOG")
    @Override
    public ConfigurationMutationResponseBody changeCurrencySpecStatus(String actor, String session, String currency,
        ChangeCurrencySpecStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(converter.status(actor, session, currency, request)));
    }
}
