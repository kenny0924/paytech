package com.paytech.channel.connector.protocol.service.processor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.EditorCapabilitiesView;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.RegisteredFunctionCatalog;
import com.paytech.channel.connector.protocol.domainservice.validator.ApiFieldValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.FlowDefinitionValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationApiDefinitionValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationScriptValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.ValueBindingValidator;

/**
 * E30 编辑器能力目录：枚举、容量上限与注册函数签名全部来自校验器常量与注册目录，保证页面提示与保存规则一致。
 *
 * <p>不读库、无请求输入（迁入 channel-connector 后不再复核操作者），因此不经 AbstractProcessor 模板；Controller 直接读取。
 */
@Component
public class EditorCapabilitiesCatalog {

    /** 返回当前编辑器能力目录。 */
    public EditorCapabilitiesView capabilities() {
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
