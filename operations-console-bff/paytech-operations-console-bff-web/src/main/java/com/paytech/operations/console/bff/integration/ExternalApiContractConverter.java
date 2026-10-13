package com.paytech.operations.console.bff.integration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 外部 API 管理合同的 BFF 本地模型与 Platform DTO 逐字段转换。
 *
 * <p>{@code definition}、{@code flowDefinition} 是 Owner 解释的 JSON 对象，原样传递；上游新增字段不会投影到浏览器。
 */
public final class ExternalApiContractConverter {

    private ExternalApiContractConverter() {
    }

    /** ApiFieldBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.ApiFieldBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.ApiFieldBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.ApiFieldBody(
            value.getFieldLocation(),
            value.getFieldPath(),
            value.getFieldName(),
            value.getDataType(),
            value.getRequired(),
            value.getDescription());
    }

    /** ApiFieldDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ApiFieldDetail toBff(com.paytech.platform.support.api.externalapi.response.ApiFieldDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ApiFieldDetail(
            value.getFieldLocation(),
            value.getFieldPath(),
            value.getFieldName(),
            value.getDataType(),
            value.isRequired(),
            value.getDescription());
    }

    /** ApiReferenceDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ApiReferenceDetail toBff(com.paytech.platform.support.api.externalapi.response.ApiReferenceDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ApiReferenceDetail(
            value.getStandardApiCode(),
            value.getNodeIds());
    }

    /** CreateImplementationRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.CreateImplementationRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody(
            value.getStandardApiCode(),
            value.getFlowDefinition(),
            value.getReason());
    }

    /** CreateIntegrationApiRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.CreateIntegrationApiRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationApiRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.CreateIntegrationApiRequestBody(
            value.getApiCode(),
            value.getApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toPlatform),
            list(value.getResponseFields(), ExternalApiContractConverter::toPlatform),
            value.getDefinition(),
            value.getReason());
    }

    /** CreateIntegrationRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody(
            value.getIntegrationCode(),
            value.getIntegrationName(),
            value.getDescription(),
            list(value.getEndpoints(), ExternalApiContractConverter::toPlatform),
            value.getReason());
    }

    /** CreateIntegrationScriptRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationScriptRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody(
            value.getScriptCode(),
            value.getScriptName(),
            value.getExecutionStage(),
            list(value.getParameterDefinition(), ExternalApiContractConverter::toPlatform),
            value.getSourceCode(),
            value.getDescription(),
            value.getReason());
    }

    /** CreateStandardApiRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.CreateStandardApiRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody(
            value.getStandardApiCode(),
            value.getStandardApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toPlatform),
            list(value.getResponseFields(), ExternalApiContractConverter::toPlatform),
            value.getReason());
    }

    /** DeleteExternalApiConfigRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody(
            value.getExpectedVersion(),
            value.getReason());
    }

    /** EditorCapabilitiesResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.EditorCapabilitiesResponseBody toBff(com.paytech.platform.support.api.externalapi.response.EditorCapabilitiesResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.EditorCapabilitiesResponseBody(
            value.getEnums(),
            value.getLimits(),
            list(value.getFunctions(), ExternalApiContractConverter::toBff));
    }

    /** ExternalApiMutationResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody toBff(com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody(
            value.getObjectType(),
            value.getObjectCode(),
            value.getVersion());
    }

    /** FlowApiContractDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.FlowApiContractDetail toBff(com.paytech.platform.support.api.externalapi.response.FlowApiContractDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.FlowApiContractDetail(
            value.getApiCode(),
            value.getApiName(),
            list(value.getRequestFields(), ExternalApiContractConverter::toBff),
            list(value.getParams(), ExternalApiContractConverter::toBff),
            map(value.getOutputs(), ExternalApiContractConverter::toBff));
    }

    /** FlowEditorContextResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.FlowEditorContextResponseBody toBff(com.paytech.platform.support.api.externalapi.response.FlowEditorContextResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.FlowEditorContextResponseBody(
            toBff(value.getStandardApi()),
            list(value.getApis(), ExternalApiContractConverter::toBff));
    }

    /** FlowRequestFieldDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.FlowRequestFieldDetail toBff(com.paytech.platform.support.api.externalapi.response.FlowRequestFieldDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.FlowRequestFieldDetail(
            value.getFieldLocation(),
            value.getFieldPath(),
            value.getFieldName(),
            value.getDataType(),
            value.isRequired(),
            value.getDescription(),
            value.isFixed());
    }

    /** FlowStandardApiDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.FlowStandardApiDetail toBff(com.paytech.platform.support.api.externalapi.response.FlowStandardApiDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.FlowStandardApiDetail(
            list(value.getRequestFields(), ExternalApiContractConverter::toBff),
            list(value.getResponseFields(), ExternalApiContractConverter::toBff));
    }

    /** ImplementationListResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationListResponseBody toBff(com.paytech.platform.support.api.externalapi.response.ImplementationListResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationListResponseBody(
            list(value.getItems(), ExternalApiContractConverter::toBff));
    }

    /** ImplementationResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationResponseBody toBff(com.paytech.platform.support.api.externalapi.response.ImplementationResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationResponseBody(
            value.getStandardApiCode(),
            value.getFlowDefinition(),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            value.getUpdatedBy());
    }

    /** ImplementationSummary 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationSummary toBff(com.paytech.platform.support.api.externalapi.response.ImplementationSummary value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationSummary(
            value.getStandardApiCode(),
            value.getStandardApiName(),
            value.getNodeCount(),
            value.getReturnCount(),
            value.getInitializerIds(),
            value.getTimeoutMs(),
            value.getVersion(),
            value.getUpdateTime());
    }

    /** IntegrationApiResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiResponseBody toBff(com.paytech.platform.support.api.externalapi.response.IntegrationApiResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiResponseBody(
            value.getApiCode(),
            value.getApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toBff),
            list(value.getResponseFields(), ExternalApiContractConverter::toBff),
            value.getDefinition(),
            list(value.getReferencedBy(), ExternalApiContractConverter::toBff),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            value.getUpdatedBy());
    }

    /** IntegrationApiSummary 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiSummary toBff(com.paytech.platform.support.api.externalapi.response.IntegrationApiSummary value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiSummary(
            value.getApiCode(),
            value.getApiName(),
            value.getMethod(),
            value.getEndpointCode(),
            value.getPathTemplate(),
            value.getRequestScriptCount(),
            value.getResponseScriptCount(),
            value.getReferencedBy(),
            value.getVersion(),
            value.getUpdateTime());
    }

    /** IntegrationEndpointBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.IntegrationEndpointBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody(
            value.getEndpointCode(),
            value.getBaseUrl());
    }

    /** IntegrationEndpointDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationEndpointDetail toBff(com.paytech.platform.support.api.externalapi.response.IntegrationEndpointDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationEndpointDetail(
            value.getEndpointCode(),
            value.getBaseUrl(),
            value.getReferencedApiCodes());
    }

    /** IntegrationReference 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationReference toBff(com.paytech.platform.support.api.externalapi.response.IntegrationReference value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationReference(
            value.getIntegrationCode(),
            value.getIntegrationName());
    }

    /** IntegrationResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationResponseBody toBff(com.paytech.platform.support.api.externalapi.response.IntegrationResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationResponseBody(
            value.getIntegrationCode(),
            value.getIntegrationName(),
            value.getDescription(),
            list(value.getEndpoints(), ExternalApiContractConverter::toBff),
            value.getApiCount(),
            value.getScriptCount(),
            value.getImplementationCount(),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            value.getUpdatedBy());
    }

    /** IntegrationScriptResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptResponseBody toBff(com.paytech.platform.support.api.externalapi.response.IntegrationScriptResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptResponseBody(
            value.getScriptCode(),
            value.getScriptName(),
            value.getExecutionStage(),
            list(value.getParameterDefinition(), ExternalApiContractConverter::toBff),
            value.getSourceCode(),
            value.getDescription(),
            value.getReferencedApiCodes(),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            value.getUpdatedBy());
    }

    /** IntegrationScriptSummary 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptSummary toBff(com.paytech.platform.support.api.externalapi.response.IntegrationScriptSummary value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationScriptSummary(
            value.getScriptCode(),
            value.getScriptName(),
            value.getExecutionStage(),
            value.getParameterNames(),
            value.getReferencedApiCodes(),
            value.getVersion(),
            value.getUpdateTime());
    }

    /** IntegrationSummary 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationSummary toBff(com.paytech.platform.support.api.externalapi.response.IntegrationSummary value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationSummary(
            value.getIntegrationCode(),
            value.getIntegrationName(),
            value.getEndpointCount(),
            value.getApiCount(),
            value.getScriptCount(),
            list(value.getImplementedStandardApis(), ExternalApiContractConverter::toBff),
            value.getVersion(),
            value.getUpdateTime());
    }

    /** RegisteredFunctionDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.RegisteredFunctionDetail toBff(com.paytech.platform.support.api.externalapi.response.RegisteredFunctionDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.RegisteredFunctionDetail(
            value.getFunctionCode(),
            value.getKind(),
            list(value.getParams(), ExternalApiContractConverter::toBff),
            value.getReturns(),
            value.getReturnRoots(),
            value.getStages());
    }

    /** ScriptParameterBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.ScriptParameterBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.ScriptParameterBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.ScriptParameterBody(
            value.getName(),
            value.getDataType(),
            value.getRequired());
    }

    /** ScriptParameterDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ScriptParameterDetail toBff(com.paytech.platform.support.api.externalapi.response.ScriptParameterDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ScriptParameterDetail(
            value.getName(),
            value.getDataType(),
            value.isRequired());
    }

    /** StandardApiReference 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiReference toBff(com.paytech.platform.support.api.externalapi.response.StandardApiReference value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiReference(
            value.getStandardApiCode(),
            value.getStandardApiName());
    }

    /** StandardApiResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiResponseBody toBff(com.paytech.platform.support.api.externalapi.response.StandardApiResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiResponseBody(
            value.getStandardApiCode(),
            value.getStandardApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toBff),
            list(value.getResponseFields(), ExternalApiContractConverter::toBff),
            list(value.getImplementations(), ExternalApiContractConverter::toBff),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            value.getUpdatedBy());
    }

    /** StandardApiSummary 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiSummary toBff(com.paytech.platform.support.api.externalapi.response.StandardApiSummary value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiSummary(
            value.getStandardApiCode(),
            value.getStandardApiName(),
            value.getRequestFieldCount(),
            value.getResponseFieldCount(),
            list(value.getImplementedBy(), ExternalApiContractConverter::toBff),
            value.getVersion(),
            value.getUpdateTime());
    }

    /** UpdateImplementationRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.UpdateImplementationRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody(
            value.getFlowDefinition(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** UpdateIntegrationApiRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationApiRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody(
            value.getApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toPlatform),
            list(value.getResponseFields(), ExternalApiContractConverter::toPlatform),
            value.getDefinition(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** UpdateIntegrationRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody(
            value.getIntegrationName(),
            value.getDescription(),
            list(value.getEndpoints(), ExternalApiContractConverter::toPlatform),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** UpdateIntegrationScriptRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationScriptRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody(
            value.getScriptName(),
            value.getExecutionStage(),
            list(value.getParameterDefinition(), ExternalApiContractConverter::toPlatform),
            value.getSourceCode(),
            value.getDescription(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** UpdateStandardApiRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.UpdateStandardApiRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody(
            value.getStandardApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toPlatform),
            list(value.getResponseFields(), ExternalApiContractConverter::toPlatform),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** ValidateImplementationRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.ValidateImplementationRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody(
            value.getStandardApiCode(),
            value.getFlowDefinition(),
            value.getExpectedVersion());
    }

    /** ValidateIntegrationApiRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.ValidateIntegrationApiRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationApiRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.ValidateIntegrationApiRequestBody(
            value.getApiCode(),
            value.getApiName(),
            value.getDescription(),
            list(value.getRequestFields(), ExternalApiContractConverter::toPlatform),
            list(value.getResponseFields(), ExternalApiContractConverter::toPlatform),
            value.getDefinition(),
            value.getExpectedVersion());
    }

    /** ValidateIntegrationScriptRequestBody 逐字段转换；null 保持 null。 */
    public static com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody toPlatform(com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationScriptRequestBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody(
            value.getScriptCode(),
            value.getScriptName(),
            value.getExecutionStage(),
            list(value.getParameterDefinition(), ExternalApiContractConverter::toPlatform),
            value.getSourceCode(),
            value.getDescription(),
            value.getExpectedVersion());
    }

    /** ValidationIssueDetail 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ValidationIssueDetail toBff(com.paytech.platform.support.api.externalapi.response.ValidationIssueDetail value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ValidationIssueDetail(
            value.getSeverity(),
            value.getCode(),
            value.getLocation(),
            value.getMessage());
    }

    /** ValidationResponseBody 逐字段转换；null 保持 null。 */
    public static com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody toBff(com.paytech.platform.support.api.externalapi.response.ValidationResponseBody value) {
        if (value == null) {
            return null;
        }
        return new com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody(
            value.isValid(),
            list(value.getIssues(), ExternalApiContractConverter::toBff));
    }

    /** 列表逐项转换，保持顺序；null 保持 null。 */
    static <S, T> List<T> list(List<S> values, Function<S, T> converter) {
        return values == null ? null : values.stream().map(converter).toList();
    }

    /** 按状态分组的列表逐项转换，保持键顺序；null 保持 null。 */
    static <S, T> Map<String, List<T>> map(Map<String, List<S>> values, Function<S, T> converter) {
        if (values == null) {
            return null;
        }
        var result = new LinkedHashMap<String, List<T>>();
        values.forEach((key, items) -> result.put(key, list(items, converter)));
        return result;
    }
}
