package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.IntegrationManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationSummary;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.CreateIntegrationProcessor;
import com.paytech.platform.support.integration.service.processor.DeleteIntegrationProcessor;
import com.paytech.platform.support.integration.service.processor.GetIntegrationProcessor;
import com.paytech.platform.support.integration.service.processor.ListIntegrationsProcessor;
import com.paytech.platform.support.integration.service.processor.UpdateIntegrationProcessor;

import lombok.AllArgsConstructor;

/** 外部接入方管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class IntegrationManagementController implements IntegrationManagementApi {

    private final GetIntegrationProcessor detailProcessor;
    private final ListIntegrationsProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationProcessor createProcessor;
    private final UpdateIntegrationProcessor updateProcessor;
    private final DeleteIntegrationProcessor deleteProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Audit(action = "OPERATIONS_INTEGRATIONS", resourceType = "INTEGRATION",
        fallbackResourceId = "INTEGRATION_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<IntegrationSummary> integrations(String actor, String session, Integer page,
        Integer size, String keyword) {
        return converter.toIntegrationPage(listProcessor.process(
            converter.toListRequest(actor, session, page, size, keyword)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION", resourceType = "INTEGRATION",
        fallbackResourceId = "INTEGRATION_CATALOG", recordSuccess = false)
    @Override
    public IntegrationResponseBody integration(String actor, String session, String integrationCode) {
        return converter.toIntegration(detailProcessor.process(converter.toGetRequest(actor, session, integrationCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_CREATE", resourceType = "INTEGRATION",
        fallbackResourceId = "NEW_INTEGRATION")
    @Override
    public ExternalApiMutationResponseBody createIntegration(String actor, String session,
        CreateIntegrationRequestBody request) {
        return converter.toMutation(createProcessor.process(converter.toCreateRequest(actor, session, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_UPDATE", resourceType = "INTEGRATION",
        fallbackResourceId = "INTEGRATION_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateIntegration(String actor, String session, String integrationCode,
        UpdateIntegrationRequestBody request) {
        return converter.toMutation(
            updateProcessor.process(converter.toUpdateRequest(actor, session, integrationCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_DELETE", resourceType = "INTEGRATION",
        fallbackResourceId = "INTEGRATION_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteIntegration(String actor, String session, String integrationCode,
        DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(
            deleteProcessor.process(converter.toDeleteRequest(actor, session, integrationCode, request)));
    }
}
