package com.paytech.platform.support.ext.rest.externalapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.IntegrationScriptManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.CreateIntegrationScriptProcessor;
import com.paytech.platform.support.integration.service.processor.DeleteIntegrationScriptProcessor;
import com.paytech.platform.support.integration.service.processor.GetIntegrationScriptProcessor;
import com.paytech.platform.support.integration.service.processor.ListIntegrationScriptsProcessor;
import com.paytech.platform.support.integration.service.processor.UpdateIntegrationScriptProcessor;
import com.paytech.platform.support.integration.service.processor.ValidateIntegrationScriptProcessor;

import lombok.AllArgsConstructor;

/** 公共脚本管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class IntegrationScriptManagementController implements IntegrationScriptManagementApi {

    private final GetIntegrationScriptProcessor detailProcessor;
    private final ListIntegrationScriptsProcessor listProcessor;
    private final ExternalApiManagementConverter converter;
    private final CreateIntegrationScriptProcessor createProcessor;
    private final UpdateIntegrationScriptProcessor updateProcessor;
    private final DeleteIntegrationScriptProcessor deleteProcessor;
    private final ValidateIntegrationScriptProcessor validateProcessor;

    /** 转换查询参数后委托列表用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPTS", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "INTEGRATION_SCRIPT_CATALOG",
        recordSuccess = false)
    @Override
    public PagingResponseBody<IntegrationScriptSummary> listIntegrationScripts(String actor, String session, String integrationCode,
        Integer page, Integer size, String keyword, String executionStage) {
        return converter.toScriptPage(listProcessor.process(converter.toChildListRequest(actor, session,
            integrationCode, page, size, keyword, executionStage)));
    }

    /** 转换路径编码后委托详情用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPT", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "INTEGRATION_SCRIPT_CATALOG",
        recordSuccess = false)
    @Override
    public IntegrationScriptResponseBody getIntegrationScript(String actor, String session, String integrationCode, String scriptCode) {
        return converter.toScript(detailProcessor.process(
            converter.toChildGetRequest(actor, session, integrationCode, scriptCode)));
    }

    /** 转换请求后委托创建用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPT_CREATE", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "NEW_INTEGRATION_SCRIPT")
    @Override
    public ExternalApiMutationResponseBody createIntegrationScript(String actor, String session, String integrationCode,
        CreateIntegrationScriptRequestBody request) {
        return converter.toMutation(createProcessor.process(
            converter.toCreateScriptRequest(actor, session, integrationCode, request)));
    }

    /** 转换请求后委托修改用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPT_UPDATE", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "INTEGRATION_SCRIPT_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateIntegrationScript(String actor, String session, String integrationCode,
        String scriptCode, UpdateIntegrationScriptRequestBody request) {
        return converter.toMutation(updateProcessor.process(
            converter.toUpdateScriptRequest(actor, session, integrationCode, scriptCode, request)));
    }

    /** 转换请求后委托删除用例。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPT_DELETE", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "INTEGRATION_SCRIPT_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationScript(String actor, String session, String integrationCode,
        String scriptCode, DeleteExternalApiConfigRequestBody request) {
        return converter.toMutation(deleteProcessor.process(
            converter.toChildDeleteRequest(actor, session, integrationCode, scriptCode, request)));
    }

    /** 转换请求后委托只校验用例，问题以列表返回。 */
    @Audit(action = "OPERATIONS_INTEGRATION_SCRIPT_VALIDATE", resourceType = "INTEGRATION_SCRIPT", fallbackResourceId = "INTEGRATION_SCRIPT_CATALOG",
        recordSuccess = false)
    @Override
    public ValidationResponseBody validateIntegrationScript(String actor, String session, String integrationCode,
        ValidateIntegrationScriptRequestBody request) {
        return converter.toValidation(validateProcessor.process(
            converter.toValidateScriptRequest(actor, session, integrationCode, request)));
    }
}
