package com.paytech.platform.support.ext.rest.externalapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.response.EditorCapabilitiesResponseBody;
import com.paytech.platform.support.api.externalapi.response.FlowApiContractDetail;
import com.paytech.platform.support.api.externalapi.response.FlowEditorContextResponseBody;
import com.paytech.platform.support.api.externalapi.response.FlowRequestFieldDetail;
import com.paytech.platform.support.api.externalapi.response.FlowStandardApiDetail;
import com.paytech.platform.support.api.externalapi.response.ImplementationListResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ImplementationSummary;
import com.paytech.platform.support.api.externalapi.response.RegisteredFunctionDetail;
import com.paytech.platform.support.api.externalapi.response.ScriptParameterDetail;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.EditorCapabilitiesView;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.FlowEditorContextView;
import com.paytech.platform.support.integration.domain.ImplementationDetailView;
import com.paytech.platform.support.integration.domain.ImplementationSummaryView;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.RegisteredFunction;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.integration.service.processor.request.CreateImplementationRequest;
import com.paytech.platform.support.integration.service.processor.request.ExternalApiActorRequest;
import com.paytech.platform.support.integration.service.processor.request.UpdateImplementationRequest;
import com.paytech.platform.support.integration.service.processor.request.ValidateImplementationRequest;

import lombok.AllArgsConstructor;

/** 渠道标准 API 实现与编辑器目录的协议解析与投影；编码与原因规则复用管理转换器。 */
@Component
@AllArgsConstructor
public class ExternalApiFlowConverter {

    private final ExternalApiManagementConverter common;

    /** 创建实现。 */
    public CreateImplementationRequest toCreateRequest(String actor, String session, String integrationCode,
        CreateImplementationRequestBody body) {
        return new CreateImplementationRequest(actor, session, common.code(integrationCode), body.getStandardApiCode(),
            body.getFlowDefinition(), ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 修改实现：标准 API 编码来自路径。 */
    public UpdateImplementationRequest toUpdateRequest(String actor, String session, String integrationCode,
        String standardApiCode, UpdateImplementationRequestBody body) {
        return new UpdateImplementationRequest(actor, session, common.code(integrationCode),
            common.code(standardApiCode), body.getFlowDefinition(), body.getExpectedVersion(),
            ConfigurationInputs.text(body.getReason(), 500, "reason"));
    }

    /** 校验实现。 */
    public ValidateImplementationRequest toValidateRequest(String actor, String session, String integrationCode,
        ValidateImplementationRequestBody body) {
        return new ValidateImplementationRequest(actor, session, common.code(integrationCode),
            body.getStandardApiCode(), body.getFlowDefinition(), body.getExpectedVersion());
    }

    /** 只需操作者身份的查询。 */
    public ExternalApiActorRequest toActorRequest(String actor, String session) {
        return new ExternalApiActorRequest(actor, session);
    }

    /** 实现列表投影。 */
    public ImplementationListResponseBody toList(List<ImplementationSummaryView> views) {
        return new ImplementationListResponseBody(views.stream().map(view -> new ImplementationSummary(
            view.getStandardApiCode(), view.getStandardApiName(), view.getNodeCount(), view.getReturnCount(),
            view.getInitializerIds(), view.getTimeoutMs(), view.getVersion(), view.getUpdateTime())).toList());
    }

    /** 实现详情投影。 */
    public ImplementationResponseBody toDetail(ImplementationDetailView view) {
        return new ImplementationResponseBody(view.getStandardApiCode(), view.getFlowDefinition(), view.getVersion(),
            view.getCreateTime(), view.getUpdateTime(), Long.toString(view.getUpdatedBy()));
    }

    /** 编辑器上下文投影。 */
    public FlowEditorContextResponseBody toEditorContext(FlowEditorContextView view) {
        return new FlowEditorContextResponseBody(new FlowStandardApiDetail(common.fieldDetails(view.getRequestFields()),
            common.fieldDetails(view.getResponseFields())), view.getApis().stream().map(this::contract).toList());
    }

    /** 编辑器能力目录投影。 */
    public EditorCapabilitiesResponseBody toCapabilities(EditorCapabilitiesView view) {
        return new EditorCapabilitiesResponseBody(view.getEnums(), view.getLimits(),
            view.getFunctions().stream().map(this::function).toList());
    }

    /** 原子 API 合同投影，请求字段标出是否已有固定值。 */
    FlowApiContractDetail contract(ApiContract contract) {
        var requestFields = contract.getRequestFields().stream()
            .map(field -> requestField(field, contract.getFixedValueKeys().contains(fixedKey(field)))).toList();
        var outputs = new LinkedHashMap<String, List<ScriptParameterDetail>>();
        contract.getOutputs().forEach((branch, declarations) -> outputs.put(branch, declarations(declarations)));
        return new FlowApiContractDetail(contract.getCode(), contract.getName(), requestFields,
            declarations(contract.getParams()), outputs);
    }

    /** 请求字段投影。 */
    FlowRequestFieldDetail requestField(FieldDefinition field, boolean fixed) {
        return new FlowRequestFieldDetail(field.getLocation().getCode(), field.getPath(), field.getName(),
            field.getDataType().getCode(), field.isRequired(), field.getDescription(), fixed);
    }

    /** 固定值键 &lt;LOCATION&gt;:&lt;path&gt;，HEADER 名称按小写比较。 */
    static String fixedKey(FieldDefinition field) {
        String code = field.getLocation().getCode();
        return code + ":" + ("HEADER".equals(code) ? field.getPath().toLowerCase(Locale.ROOT) : field.getPath());
    }

    /** 注册函数投影。 */
    RegisteredFunctionDetail function(RegisteredFunction function) {
        return new RegisteredFunctionDetail(function.getCode(), function.getKind(), declarations(function.getArgs()),
            function.getReturns(), Map.copyOf(function.getReturnRoots()), RegisteredFunctionCatalog.usages(function));
    }

    /** 参数声明投影。 */
    List<ScriptParameterDetail> declarations(List<ParameterDeclaration> declarations) {
        return declarations.stream().map(item -> new ScriptParameterDetail(item.getName(), item.getDataType(),
            item.isRequired())).toList();
    }
}
