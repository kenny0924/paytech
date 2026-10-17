package com.paytech.platform.support.integration.service.processor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.EditorCapabilitiesView;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.integration.domainservice.validator.ApiFieldValidator;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationApiDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationScriptValidator;
import com.paytech.platform.support.integration.domainservice.validator.ValueBindingValidator;
import com.paytech.platform.support.integration.service.processor.request.ExternalApiActorRequest;

import lombok.AllArgsConstructor;

/**
 * E30 编辑器能力目录：枚举、容量上限与注册函数签名全部来自校验器常量与注册目录，保证页面提示与保存规则一致。
 *
 * <p>不读库；第 1 步的函数目录只含已确认签名，实现在第 3 步执行引擎中完成。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetEditorCapabilitiesProcessor extends AbstractProcessor<ExternalApiActorRequest, EditorCapabilitiesView> {

    private final OperationsManagementPolicyService policy;

    @Override
    protected EditorCapabilitiesView processRequest(ExternalApiActorRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        return new EditorCapabilitiesView(enums(), limits(), RegisteredFunctionCatalog.FUNCTIONS);
    }

    /** 方向、位置、类型、Method、编解码、脚本阶段、出口状态与取值方式。 */
    Map<String, List<String>> enums() {
        var result = new LinkedHashMap<String, List<String>>();
        result.put("fieldDirections", Arrays.stream(ApiFieldDirection.values()).map(ApiFieldDirection::getCode).toList());
        result.put("fieldLocations", Arrays.stream(ApiFieldLocation.values()).map(ApiFieldLocation::getCode).toList());
        result.put("dataTypes", Arrays.stream(ApiFieldDataType.values()).map(ApiFieldDataType::getCode).toList());
        result.put("methods", IntegrationApiDefinitionValidator.METHODS.stream().sorted().toList());
        result.put("requestEncodings", List.of("JSON", "FORM_URLENCODED", "NONE"));
        result.put("responseDecodings", List.of("JSON", "NONE"));
        result.put("decimalEncodings", IntegrationApiDefinitionValidator.DECIMAL_ENCODINGS.stream().sorted().toList());
        result.put("scriptStages", Arrays.stream(ScriptExecutionStage.values()).map(ScriptExecutionStage::getCode)
            .toList());
        result.put("returnStatuses", List.of("SUCCESS", "FAIL", "ERROR"));
        result.put("bindingKinds", List.of("PATH", "EXPRESSION", "FUNCTION"));
        return result;
    }

    /** 物理设计 P7 容量与流程预算。 */
    Map<String, Integer> limits() {
        var result = new LinkedHashMap<String, Integer>();
        result.put("jsonBytes", FlowDefinitionValidator.MAX_BYTES);
        result.put("scriptSourceBytes", IntegrationScriptValidator.MAX_SOURCE_BYTES);
        result.put("apisPerIntegration", (int) CreateIntegrationApiProcessor.MAX_APIS);
        result.put("scriptsPerIntegration", (int) CreateIntegrationScriptProcessor.MAX_SCRIPTS);
        result.put("endpointsPerIntegration", IntegrationEndpointValidator.MAX_ENDPOINTS);
        result.put("fieldsPerDirection", ApiFieldValidator.MAX_FIELDS);
        result.put("fieldPathSegments", ApiFieldValidator.MAX_SEGMENTS);
        result.put("apiParams", IntegrationApiDefinitionValidator.MAX_PARAMS);
        result.put("apiScripts", IntegrationApiDefinitionValidator.MAX_SCRIPTS);
        result.put("apiStatusRules", IntegrationApiDefinitionValidator.MAX_RULES);
        result.put("apiOutputs", IntegrationApiDefinitionValidator.MAX_OUTPUTS);
        result.put("scriptParameters", IntegrationScriptValidator.MAX_PARAMETERS);
        result.put("expressionChars", ValueBindingValidator.MAX_EXPRESSION);
        result.put("flowTimeoutMs", FlowDefinitionValidator.MAX_TIMEOUT);
        result.put("flowInitializers", FlowDefinitionValidator.MAX_INITIALIZERS);
        result.put("flowNodes", FlowDefinitionValidator.MAX_NODES);
        result.put("flowReturns", FlowDefinitionValidator.MAX_RETURNS);
        result.put("flowEdges", FlowDefinitionValidator.MAX_EDGES);
        result.put("flowRequestMappings", FlowDefinitionValidator.MAX_REQUEST_MAPPINGS);
        return result;
    }
}
