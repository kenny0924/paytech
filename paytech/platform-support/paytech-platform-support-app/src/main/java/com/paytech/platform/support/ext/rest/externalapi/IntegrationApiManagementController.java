package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.IntegrationApiManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiSummary;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.CreateIntegrationApiProcessor;
import com.paytech.platform.support.integration.service.processor.DeleteIntegrationApiProcessor;
import com.paytech.platform.support.integration.service.processor.GetIntegrationApiProcessor;
import com.paytech.platform.support.integration.service.processor.ListIntegrationApisProcessor;
import com.paytech.platform.support.integration.service.processor.UpdateIntegrationApiProcessor;
import com.paytech.platform.support.integration.service.processor.ValidateIntegrationApiProcessor;

import lombok.AllArgsConstructor;

/** 原子 API管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class IntegrationApiManagementController implements IntegrationApiManagementApi {

    private final GetIntegrationApiProcessor detailProcessor;
    private final ListIntegrationApisProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationApiProcessor createProcessor;
    private final UpdateIntegrationApiProcessor updateProcessor;
    private final DeleteIntegrationApiProcessor deleteProcessor;
    private final ValidateIntegrationApiProcessor validateProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_APIS", resourceType = "INTEGRATION_API", fallbackResourceId = "INTEGRATION_API_CATALOG",
        recordSuccess = false)
    @Override
    public PagingResponseBody<IntegrationApiSummary> listIntegrationApis(String actor, String session, String integrationCode,
        Integer page, Integer size, String keyword) {
        return converter.toApiPage(listProcessor.process(converter.toChildListRequest(actor, session,
            integrationCode, page, size, keyword, null)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_API", resourceType = "INTEGRATION_API", fallbackResourceId = "INTEGRATION_API_CATALOG",
        recordSuccess = false)
    @Override
    public IntegrationApiResponseBody getIntegrationApi(String actor, String session, String integrationCode, String apiCode) {
        return converter.toApi(detailProcessor.process(
            converter.toChildGetRequest(actor, session, integrationCode, apiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_API_CREATE", resourceType = "INTEGRATION_API", fallbackResourceId = "NEW_INTEGRATION_API")
    @Override
    public ExternalApiMutationResponseBody createIntegrationApi(String actor, String session, String integrationCode,
        CreateIntegrationApiRequestBody request) {
        return converter.toMutation(createProcessor.process(
            converter.toCreateApiRequest(actor, session, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_API_UPDATE", resourceType = "INTEGRATION_API", fallbackResourceId = "INTEGRATION_API_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateIntegrationApi(String actor, String session, String integrationCode,
        String apiCode, UpdateIntegrationApiRequestBody request) {
        return converter.toMutation(updateProcessor.process(
            converter.toUpdateApiRequest(actor, session, integrationCode, apiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_API_DELETE", resourceType = "INTEGRATION_API", fallbackResourceId = "INTEGRATION_API_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationApi(String actor, String session, String integrationCode,
        String apiCode, DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(deleteProcessor.process(
            converter.toChildDeleteRequest(actor, session, integrationCode, apiCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Audit(action = "OPERATIONS_INTEGRATION_API_VALIDATE", resourceType = "INTEGRATION_API", fallbackResourceId = "INTEGRATION_API_CATALOG",
        recordSuccess = false)
    @Override
    public ValidationResponseBody validateIntegrationApi(String actor, String session, String integrationCode,
        ValidateIntegrationApiRequestBody request) {
        return converter.toValidation(validateProcessor.process(
            converter.toValidateApiRequest(actor, session, integrationCode, request)));
    }
}
