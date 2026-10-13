package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.externalapi.IntegrationImplementationManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationListResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.*;

import lombok.AllArgsConstructor;

/** 渠道标准 API 实现管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class IntegrationImplementationManagementController implements IntegrationImplementationManagementApi {

    private final ExternalApiFlowConverter converter;
    private final ExternalApiManagementConverter common;
    private final GetImplementationProcessor detailProcessor;
    private final ListImplementationsProcessor listProcessor;
    private final CreateImplementationProcessor createProcessor;
    private final UpdateImplementationProcessor updateProcessor;
    private final DeleteImplementationProcessor deleteProcessor;
    private final ValidateImplementationProcessor validateProcessor;

    /** 校验接入方编码后委托列表用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATIONS", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG", recordSuccess = false)
    @Override
    public ImplementationListResponseBody listImplementations(String actor, String session, String integrationCode) {
        return converter.toList(listProcessor.process(common.toGetRequest(actor, session, integrationCode)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATION", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG", recordSuccess = false)
    @Override
    public ImplementationResponseBody getImplementation(String actor, String session, String integrationCode,
        String standardApiCode) {
        return converter.toDetail(
            detailProcessor.process(common.toChildGetRequest(actor, session, integrationCode, standardApiCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATION_CREATE", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "NEW_INTEGRATION_IMPLEMENTATION")
    @Override
    public ExternalApiMutationResponseBody createImplementation(String actor, String session, String integrationCode,
        CreateImplementationRequestBody request) {
        return common
            .toMutation(createProcessor.process(converter.toCreateRequest(actor, session, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATION_UPDATE", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateImplementation(String actor, String session, String integrationCode,
        String standardApiCode, UpdateImplementationRequestBody request) {
        return common.toMutation(updateProcessor
            .process(converter.toUpdateRequest(actor, session, integrationCode, standardApiCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATION_DELETE", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteImplementation(String actor, String session, String integrationCode,
        String standardApiCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteProcessor
            .process(common.toChildDeleteRequest(actor, session, integrationCode, standardApiCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Audit(action = "OPERATIONS_INTEGRATION_IMPLEMENTATION_VALIDATE", resourceType = "INTEGRATION_IMPLEMENTATION",
        fallbackResourceId = "INTEGRATION_IMPLEMENTATION_CATALOG", recordSuccess = false)
    @Override
    public ValidationResponseBody validateImplementation(String actor, String session, String integrationCode,
        ValidateImplementationRequestBody request) {
        return common.toValidation(
            validateProcessor.process(converter.toValidateRequest(actor, session, integrationCode, request)));
    }
}
