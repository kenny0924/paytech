package com.paytech.channel.connector.ext.rest.externalapi;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ScriptParameterBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateIntegrationScriptRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ApiFieldDetail;
import com.paytech.channel.connector.api.externalapi.response.ApiReferenceDetail;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationApiSummary;
import com.paytech.channel.connector.api.externalapi.response.IntegrationReference;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptResponseBody;
import com.paytech.channel.connector.api.externalapi.response.IntegrationScriptSummary;
import com.paytech.channel.connector.api.externalapi.response.ScriptParameterDetail;
import com.paytech.channel.connector.api.externalapi.response.StandardApiResponseBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiSummary;
import com.paytech.channel.connector.api.externalapi.response.ValidationIssueDetail;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.ext.rest.converter.ChannelConfigurationWire;
import com.paytech.channel.connector.protocol.domain.ConfigurationReference;
import com.paytech.channel.connector.protocol.domain.ExternalApiMutation;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.IntegrationApiDetailView;
import com.paytech.channel.connector.protocol.domain.IntegrationApiSummaryView;
import com.paytech.channel.connector.protocol.domain.IntegrationScriptDetailView;
import com.paytech.channel.connector.protocol.domain.IntegrationScriptSummaryView;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.StandardApiDetailView;
import com.paytech.channel.connector.protocol.domain.StandardApiSummaryView;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.service.processor.request.CreateIntegrationApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.CreateIntegrationScriptRequest;
import com.paytech.channel.connector.protocol.service.processor.request.CreateStandardApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteExternalApiConfigRequest;
import com.paytech.channel.connector.protocol.service.processor.request.DeleteIntegrationChildRequest;
import com.paytech.channel.connector.protocol.service.processor.request.GetExternalApiConfigRequest;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;
import com.paytech.channel.connector.protocol.service.processor.request.ListExternalApiConfigRequest;
import com.paytech.channel.connector.protocol.service.processor.request.ListIntegrationChildrenRequest;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateIntegrationApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateIntegrationScriptRequest;
import com.paytech.channel.connector.protocol.service.processor.request.UpdateStandardApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.ValidateIntegrationApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.ValidateIntegrationScriptRequest;

/** 外部 API 配置管理的协议解析与投影；字段约束已由入口校验，这里只做规范化与类型转换。 */
@Component
public class ExternalApiManagementConverter {

    /** 编码列的最大长度。 */
    static final int CODE_LENGTH = 64;

    /** 补齐分页并把关键字中的通配符转为文字，每页最多 100 条。 */
    public ListExternalApiConfigRequest toListRequest(Integer page, Integer size,
        String keyword) {
        return new ListExternalApiConfigRequest(ExternalApiInputs.paging(page, size),
            ExternalApiInputs.contains(keyword, 64, "keyword"));
    }

    /** 校验路径中的精确编码，不改写大小写。 */
    public GetExternalApiConfigRequest toGetRequest(String code) {
        return new GetExternalApiConfigRequest(ExternalApiInputs.code(code, CODE_LENGTH));
    }

    /** 删除请求只携带版本与原因。 */
    public DeleteExternalApiConfigRequest toDeleteRequest(String actor, String code,
        DeleteExternalApiConfigRequestBody body) {
        return new DeleteExternalApiConfigRequest(ChannelConfigurationWire.actor(actor), ExternalApiInputs.code(code, CODE_LENGTH),
            body.getExpectedVersion(), ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 创建标准 API：规范化文本并转换字段。 */
    public CreateStandardApiRequest toCreateRequest(String actor, CreateStandardApiRequestBody body) {
        return new CreateStandardApiRequest(ChannelConfigurationWire.actor(actor), body.getStandardApiCode(),
            ExternalApiInputs.text(body.getStandardApiName(), 128, "standardApiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()),
            ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改标准 API：编码来自路径。 */
    public UpdateStandardApiRequest toUpdateRequest(String actor, String code,
        UpdateStandardApiRequestBody body) {
        return new UpdateStandardApiRequest(ChannelConfigurationWire.actor(actor), ExternalApiInputs.code(code, CODE_LENGTH),
            ExternalApiInputs.text(body.getStandardApiName(), 128, "standardApiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getExpectedVersion(),
            ExternalApiInputs.text(body.getReason(), 500, "reason"));
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

    /** 下级配置列表：补齐分页、转义关键字，解析可选的执行阶段筛选。 */
    public ListIntegrationChildrenRequest toChildListRequest(String integrationCode,
        Integer page, Integer size, String keyword, String executionStage) {
        return new ListIntegrationChildrenRequest(code(integrationCode),
            ExternalApiInputs.paging(page, size), ExternalApiInputs.contains(keyword, 64, "keyword"),
            executionStage == null ? null : stage(executionStage));
    }

    /** 下级配置详情。 */
    public GetIntegrationChildRequest toChildGetRequest(String integrationCode,
        String childCode) {
        return new GetIntegrationChildRequest(code(integrationCode), code(childCode));
    }

    /** 下级配置删除。 */
    public DeleteIntegrationChildRequest toChildDeleteRequest(String actor, String integrationCode,
        String childCode, DeleteExternalApiConfigRequestBody body) {
        return new DeleteIntegrationChildRequest(ChannelConfigurationWire.actor(actor), code(integrationCode), code(childCode),
            body.getExpectedVersion(), ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 创建原子 API。 */
    public CreateIntegrationApiRequest toCreateApiRequest(String actor, String integrationCode,
        CreateIntegrationApiRequestBody body) {
        return new CreateIntegrationApiRequest(ChannelConfigurationWire.actor(actor), code(integrationCode), body.getApiCode(),
            ExternalApiInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改原子 API：编码来自路径。 */
    public UpdateIntegrationApiRequest toUpdateApiRequest(String actor, String integrationCode,
        String apiCode, UpdateIntegrationApiRequestBody body) {
        return new UpdateIntegrationApiRequest(ChannelConfigurationWire.actor(actor), code(integrationCode), code(apiCode),
            ExternalApiInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            body.getExpectedVersion(), ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 校验原子 API。 */
    public ValidateIntegrationApiRequest toValidateApiRequest(String integrationCode,
        ValidateIntegrationApiRequestBody body) {
        return new ValidateIntegrationApiRequest(code(integrationCode), body.getApiCode(),
            ExternalApiInputs.text(body.getApiName(), 128, "apiName"), description(body.getDescription()),
            fields(body.getRequestFields()), fields(body.getResponseFields()), body.getDefinition(),
            body.getExpectedVersion());
    }

    /** 创建公共脚本。 */
    public CreateIntegrationScriptRequest toCreateScriptRequest(String actor, String integrationCode,
        CreateIntegrationScriptRequestBody body) {
        return new CreateIntegrationScriptRequest(ChannelConfigurationWire.actor(actor), code(integrationCode), body.getScriptCode(),
            ExternalApiInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
            parameters(body.getParameterDefinition()), body.getSourceCode(), description(body.getDescription()),
            ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改公共脚本：编码来自路径。 */
    public UpdateIntegrationScriptRequest toUpdateScriptRequest(String actor, String integrationCode,
        String scriptCode, UpdateIntegrationScriptRequestBody body) {
        return new UpdateIntegrationScriptRequest(ChannelConfigurationWire.actor(actor), code(integrationCode), code(scriptCode),
            ExternalApiInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
            parameters(body.getParameterDefinition()), body.getSourceCode(), description(body.getDescription()),
            body.getExpectedVersion(), ExternalApiInputs.text(body.getReason(), 500, "reason"));
    }

    /** 校验公共脚本。 */
    public ValidateIntegrationScriptRequest toValidateScriptRequest(String integrationCode, ValidateIntegrationScriptRequestBody body) {
        return new ValidateIntegrationScriptRequest(code(integrationCode), body.getScriptCode(),
            ExternalApiInputs.text(body.getScriptName(), 128, "scriptName"), stage(body.getExecutionStage()),
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
        return ExternalApiInputs.code(value, CODE_LENGTH);
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
        return ExternalApiInputs.text(value, 512, "description");
    }

    /** 字段请求转为领域定义；位置与类型已由入口按允许值校验。 */
    List<FieldDefinition> fields(List<ApiFieldBody> bodies) {
        return bodies.stream().map(body -> new FieldDefinition(ApiFieldLocation.fromCode(body.getFieldLocation()),
            body.getFieldPath(), ExternalApiInputs.text(body.getFieldName(), 128, "fieldName"),
            ApiFieldDataType.fromCode(body.getDataType()), body.getRequired(), description(body.getDescription())))
            .toList();
    }

    /** 字段定义投影为响应字段。 */
    List<ApiFieldDetail> fieldDetails(List<FieldDefinition> fields) {
        return fields.stream().map(field -> new ApiFieldDetail(field.getLocation().getCode(), field.getPath(),
            field.getName(), field.getDataType().getCode(), field.isRequired(), field.getDescription())).toList();
    }

    /** 渠道引用投影。 */
    List<IntegrationReference> integrationReferences(List<ConfigurationReference> references) {
        return references.stream().map(item -> new IntegrationReference(item.getCode(), item.getName())).toList();
    }

}
