package com.paytech.platform.support.ext.rest.externalapi;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody;
import com.paytech.platform.support.api.externalapi.request.ScriptParameterBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.response.ApiFieldDetail;
import com.paytech.platform.support.api.externalapi.response.ApiReferenceDetail;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationApiSummary;
import com.paytech.platform.support.api.externalapi.response.IntegrationEndpointDetail;
import com.paytech.platform.support.api.externalapi.response.IntegrationReference;
import com.paytech.platform.support.api.externalapi.response.IntegrationResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.platform.support.api.externalapi.response.IntegrationSummary;
import com.paytech.platform.support.api.externalapi.response.ScriptParameterDetail;
import com.paytech.platform.support.api.externalapi.response.StandardApiReference;
import com.paytech.platform.support.api.externalapi.response.StandardApiResponseBody;
import com.paytech.platform.support.api.externalapi.response.StandardApiSummary;
import com.paytech.platform.support.api.externalapi.response.ValidationIssueDetail;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.integration.domain.ConfigurationReference;
import com.paytech.platform.support.integration.domain.EndpointDefinition;
import com.paytech.platform.support.integration.domain.ExternalApiMutation;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.IntegrationApiDetailView;
import com.paytech.platform.support.integration.domain.IntegrationApiSummaryView;
import com.paytech.platform.support.integration.domain.IntegrationDetailView;
import com.paytech.platform.support.integration.domain.IntegrationScriptDetailView;
import com.paytech.platform.support.integration.domain.IntegrationScriptSummaryView;
import com.paytech.platform.support.integration.domain.IntegrationSummaryView;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.StandardApiDetailView;
import com.paytech.platform.support.integration.domain.StandardApiSummaryView;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.service.processor.request.CreateIntegrationApiRequest;
import com.paytech.platform.support.integration.service.processor.request.CreateIntegrationRequest;
import com.paytech.platform.support.integration.service.processor.request.CreateIntegrationScriptRequest;
import com.paytech.platform.support.integration.service.processor.request.CreateStandardApiRequest;
import com.paytech.platform.support.integration.service.processor.request.DeleteExternalApiConfigRequest;
import com.paytech.platform.support.integration.service.processor.request.DeleteIntegrationChildRequest;
import com.paytech.platform.support.integration.service.processor.request.GetExternalApiConfigRequest;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;
import com.paytech.platform.support.integration.service.processor.request.ListExternalApiConfigRequest;
import com.paytech.platform.support.integration.service.processor.request.ListIntegrationChildrenRequest;
import com.paytech.platform.support.integration.service.processor.request.UpdateIntegrationApiRequest;
import com.paytech.platform.support.integration.service.processor.request.UpdateIntegrationRequest;
import com.paytech.platform.support.integration.service.processor.request.UpdateIntegrationScriptRequest;
import com.paytech.platform.support.integration.service.processor.request.UpdateStandardApiRequest;
import com.paytech.platform.support.integration.service.processor.request.ValidateIntegrationApiRequest;
import com.paytech.platform.support.integration.service.processor.request.ValidateIntegrationScriptRequest;

/** 外部 API 配置管理的协议解析与投影；字段约束已由入口校验，这里只做规范化与类型转换。 */
@Component
public class ExternalApiManagementConverter {

    /** 编码列的最大长度。 */
    static final int CODE_LENGTH = 64;

    /** 补齐分页并把关键字中的通配符转为文字，每页最多 100 条。 */
    public ListExternalApiConfigRequest toListRequest(String actor, String session, Integer page, Integer size,
        String keyword) {
        return new ListExternalApiConfigRequest(actor, session, ClientApiManagementInputs.paging(page, size),
            ClientApiManagementInputs.contains(keyword, 64, "keyword"));
    }

    /** 校验路径中的精确编码，不改写大小写。 */
    public GetExternalApiConfigRequest toGetRequest(String actor, String session, String code) {
        return new GetExternalApiConfigRequest(actor, session, ConfigurationInputs.code(code, CODE_LENGTH));
    }

    /** 删除请求只携带版本与原因。 */
    public DeleteExternalApiConfigRequest toDeleteRequest(String actor, String session, String code,
        DeleteExternalApiConfigRequestBody body) {
        return new DeleteExternalApiConfigRequest(actor, session, ConfigurationInputs.code(code, CODE_LENGTH),
            body.getExpectedVersion(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 创建标准 API：规范化文本并转换字段。 */
    public CreateStandardApiRequest toCreateRequest(String actor, String session, CreateStandardApiRequestBody body) {
        return new CreateStandardApiRequest(actor, session, body.getStandardApiCode(),
            ConfigurationInputs.text(body.getStandardApiName(), 128, "standardApiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改标准 API：编码来自路径。 */
    public UpdateStandardApiRequest toUpdateRequest(String actor, String session, String code,
        UpdateStandardApiRequestBody body) {
        return new UpdateStandardApiRequest(actor, session, ConfigurationInputs.code(code, CODE_LENGTH),
            ConfigurationInputs.text(body.getStandardApiName(), 128, "standardApiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 创建接入方：规范化文本并转换出站地址。 */
    public CreateIntegrationRequest toCreateRequest(String actor, String session, CreateIntegrationRequestBody body) {
        return new CreateIntegrationRequest(actor, session, body.getIntegrationCode(),
            ConfigurationInputs.text(body.getIntegrationName(), 128, "integrationName"),
            description(body.getDescription()), endpoints(body.getEndpoints()),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改接入方：编码来自路径。 */
    public UpdateIntegrationRequest toUpdateRequest(String actor, String session, String code,
        UpdateIntegrationRequestBody body) {
        return new UpdateIntegrationRequest(actor, session, ConfigurationInputs.code(code, CODE_LENGTH),
            ConfigurationInputs.text(body.getIntegrationName(), 128, "integrationName"),
            description(body.getDescription()), endpoints(body.getEndpoints()), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 写入结果。 */
    public ExternalApiMutationResponseBody toMutation(ExternalApiMutation result) {
        return new ExternalApiMutationResponseBody(result.getObjectType(), result.getObjectCode(), result.getVersion());
    }

    /** 标准 API 列表投影。 */
    public PagingResponseBody<StandardApiSummary> toStandardApiPage(PagingResponseBody<StandardApiSummaryView> page) {
        return page.map(view -> new StandardApiSummary(view.getCode(), view.getName(), view.getRequestFieldCount(),
            view.getResponseFieldCount(), integrationReferences(view.getImplementedBy()), view.getVersion(),
            view.getUpdateTime()));
    }

    /** 标准 API 详情投影，账号标识按十进制字符串输出。 */
    public StandardApiResponseBody toStandardApi(StandardApiDetailView view) {
        return new StandardApiResponseBody(view.getCode(), view.getName(), view.getDescription(),
            fieldDetails(view.getRequestFields()), fieldDetails(view.getResponseFields()),
            integrationReferences(view.getImplementations()), view.getVersion(), view.getCreateTime(),
            view.getUpdateTime(), Long.toString(view.getUpdatedBy()));
    }

    /** 接入方列表投影。 */
    public PagingResponseBody<IntegrationSummary> toIntegrationPage(PagingResponseBody<IntegrationSummaryView> page) {
        return page.map(view -> new IntegrationSummary(view.getCode(), view.getName(), view.getEndpointCount(),
            view.getApiCount(), view.getScriptCount(), standardApiReferences(view.getImplementedStandardApis()),
            view.getVersion(), view.getUpdateTime()));
    }

    /** 接入方详情投影。 */
    public IntegrationResponseBody toIntegration(IntegrationDetailView view) {
        var endpoints = view.getEndpoints().stream().map(endpoint -> new IntegrationEndpointDetail(
            endpoint.getEndpointCode(), endpoint.getBaseUrl(), endpoint.isSandbox(), endpoint.getReferencedApiCodes())).toList();
        return new IntegrationResponseBody(view.getCode(), view.getName(), view.getDescription(), endpoints,
            view.getApiCount(), view.getScriptCount(), view.getImplementationCount(), view.getVersion(),
            view.getCreateTime(), view.getUpdateTime(), Long.toString(view.getUpdatedBy()));
    }

    /** 下级配置列表：补齐分页、转义关键字，解析可选的执行阶段筛选。 */
    public ListIntegrationChildrenRequest toChildListRequest(String actor, String session, String integrationCode,
        Integer page, Integer size, String keyword, String executionStage) {
        return new ListIntegrationChildrenRequest(actor, session, code(integrationCode),
            ClientApiManagementInputs.paging(page, size), ClientApiManagementInputs.contains(keyword, 64, "keyword"),
            executionStage == null ? null : stage(executionStage));
    }

    /** 下级配置详情。 */
    public GetIntegrationChildRequest toChildGetRequest(String actor, String session, String integrationCode,
        String childCode) {
        return new GetIntegrationChildRequest(actor, session, code(integrationCode), code(childCode));
    }

    /** 下级配置删除。 */
    public DeleteIntegrationChildRequest toChildDeleteRequest(String actor, String session, String integrationCode,
        String childCode, DeleteExternalApiConfigRequestBody body) {
        return new DeleteIntegrationChildRequest(actor, session, code(integrationCode), code(childCode),
            body.getExpectedVersion(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 创建原子 API。 */
    public CreateIntegrationApiRequest toCreateApiRequest(String actor, String session, String integrationCode,
        CreateIntegrationApiRequestBody body) {
        return new CreateIntegrationApiRequest(actor, session, code(integrationCode), body.getApiCode(),
            ConfigurationInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改原子 API：编码来自路径。 */
    public UpdateIntegrationApiRequest toUpdateApiRequest(String actor, String session, String integrationCode,
        String apiCode, UpdateIntegrationApiRequestBody body) {
        return new UpdateIntegrationApiRequest(actor, session, code(integrationCode), code(apiCode),
            ConfigurationInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            body.getExpectedVersion(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 校验原子 API。 */
    public ValidateIntegrationApiRequest toValidateApiRequest(String actor, String session, String integrationCode,
        ValidateIntegrationApiRequestBody body) {
        return new ValidateIntegrationApiRequest(actor, session, code(integrationCode), body.getApiCode(),
            ConfigurationInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            body.getExpectedVersion());
    }

    /** 创建公共脚本。 */
    public CreateIntegrationScriptRequest toCreateScriptRequest(String actor, String session, String integrationCode,
        CreateIntegrationScriptRequestBody body) {
        return new CreateIntegrationScriptRequest(actor, session, code(integrationCode), body.getScriptCode(),
            ConfigurationInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
            parameters(body.getParameterDefinition()), body.getSourceCode(), description(body.getDescription()),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改公共脚本：编码来自路径。 */
    public UpdateIntegrationScriptRequest toUpdateScriptRequest(String actor, String session, String integrationCode,
        String scriptCode, UpdateIntegrationScriptRequestBody body) {
        return new UpdateIntegrationScriptRequest(actor, session, code(integrationCode), code(scriptCode),
            ConfigurationInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
            parameters(body.getParameterDefinition()), body.getSourceCode(), description(body.getDescription()),
            body.getExpectedVersion(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 校验公共脚本。 */
    public ValidateIntegrationScriptRequest toValidateScriptRequest(String actor, String session,
        String integrationCode, ValidateIntegrationScriptRequestBody body) {
        return new ValidateIntegrationScriptRequest(actor, session, code(integrationCode), body.getScriptCode(),
            ConfigurationInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
            parameters(body.getParameterDefinition()), body.getSourceCode(), description(body.getDescription()),
            body.getExpectedVersion());
    }

    /** 原子 API 列表投影。 */
    public PagingResponseBody<IntegrationApiSummary> toApiPage(PagingResponseBody<IntegrationApiSummaryView> page) {
        return page.map(view -> new IntegrationApiSummary(view.getCode(), view.getName(), view.getMethod(),
            view.getEndpointCode(), view.getPathTemplate(), view.getRequestScriptCount(), view.getResponseScriptCount(),
            view.getReferencedBy(), view.getVersion(), view.getUpdateTime()));
    }

    /** 原子 API 详情投影。 */
    public IntegrationApiResponseBody toApi(IntegrationApiDetailView view) {
        var references = view.getReferencedBy().stream()
            .map(reference -> new ApiReferenceDetail(reference.getStandardApiCode(), reference.getNodeIds())).toList();
        return new IntegrationApiResponseBody(view.getCode(), view.getName(), view.getDescription(),
            fieldDetails(view.getRequestFields()), fieldDetails(view.getResponseFields()), view.getDefinition(),
            references, view.getVersion(), view.getCreateTime(), view.getUpdateTime(),
            Long.toString(view.getUpdatedBy()));
    }

    /** 公共脚本列表投影。 */
    public PagingResponseBody<IntegrationScriptSummary> toScriptPage(
        PagingResponseBody<IntegrationScriptSummaryView> page) {
        return page.map(view -> new IntegrationScriptSummary(view.getCode(), view.getName(), view.getStage().getCode(),
            view.getParameterNames(), view.getReferencedApiCodes(), view.getVersion(), view.getUpdateTime()));
    }

    /** 公共脚本详情投影。 */
    public IntegrationScriptResponseBody toScript(IntegrationScriptDetailView view) {
        var parameters = view.getParameters().stream().map(parameter -> new ScriptParameterDetail(parameter.getName(),
            parameter.getDataType(), parameter.isRequired())).toList();
        return new IntegrationScriptResponseBody(view.getCode(), view.getName(), view.getStage().getCode(), parameters,
            view.getSourceCode(), view.getDescription(), view.getReferencedApiCodes(), view.getVersion(),
            view.getCreateTime(), view.getUpdateTime(), Long.toString(view.getUpdatedBy()));
    }

    /** 校验结果：没有 ERROR 级问题即可保存。 */
    public ValidationResponseBody toValidation(List<ValidationIssue> issues) {
        return new ValidationResponseBody(issues.stream().noneMatch(ValidationIssue::blocking),
            issues.stream().map(issue -> new ValidationIssueDetail(issue.getSeverity(), issue.getCode(),
                issue.getLocation(), issue.getMessage())).toList());
    }

    /** 路径中的编码。 */
    String code(String value) {
        return ConfigurationInputs.code(value, CODE_LENGTH);
    }

    /** 执行阶段代码，未知值返回 400。 */
    ScriptExecutionStage stage(String value) {
        var stage = ScriptExecutionStage.fromCode(value);
        if (stage == null) {
            throw new InvalidParameterException("执行阶段只允许 REQUEST、RESPONSE");
        }
        return stage;
    }

    /** 脚本参数声明转为领域对象，名称与类型规则由静态校验器检查。 */
    List<ParameterDeclaration> parameters(List<ScriptParameterBody> bodies) {
        return bodies.stream().map(body -> new ParameterDeclaration(body.getName(), body.getDataType(),
            body.getRequired())).toList();
    }

    /** 空白说明规范为 null，非空说明去除首尾空白后最多 512 字符。 */
    String description(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ConfigurationInputs.text(value, 512, "description");
    }

    /** 字段请求转为领域定义；位置与类型已由入口按允许值校验。 */
    List<FieldDefinition> fields(List<ApiFieldBody> bodies) {
        return bodies.stream().map(body -> new FieldDefinition(ApiFieldLocation.fromCode(body.getFieldLocation()),
            body.getFieldPath(), ConfigurationInputs.text(body.getFieldName(), 128, "fieldName"),
            ApiFieldDataType.fromCode(body.getDataType()), body.getRequired(), description(body.getDescription())))
            .toList();
    }

    /** 出站地址转为领域定义，保持提交顺序。 */
    List<EndpointDefinition> endpoints(List<IntegrationEndpointBody> bodies) {
        return bodies.stream().map(body -> new EndpointDefinition(body.getEndpointCode(), body.getBaseUrl(),
            Boolean.TRUE.equals(body.getSandbox()))).toList();
    }

    /** 字段定义投影为响应字段。 */
    List<ApiFieldDetail> fieldDetails(List<FieldDefinition> fields) {
        return fields.stream().map(field -> new ApiFieldDetail(field.getLocation().getCode(), field.getPath(),
            field.getName(), field.getDataType().getCode(), field.isRequired(), field.getDescription())).toList();
    }

    /** 接入方引用投影。 */
    List<IntegrationReference> integrationReferences(List<ConfigurationReference> references) {
        return references.stream().map(item -> new IntegrationReference(item.getCode(), item.getName())).toList();
    }

    /** 标准 API 引用投影。 */
    List<StandardApiReference> standardApiReferences(List<ConfigurationReference> references) {
        return references.stream().map(item -> new StandardApiReference(item.getCode(), item.getName())).toList();
    }
}
