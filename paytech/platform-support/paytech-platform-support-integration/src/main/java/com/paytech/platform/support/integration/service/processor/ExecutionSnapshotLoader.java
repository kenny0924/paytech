package com.paytech.platform.support.integration.service.processor;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.integration.core.dal.dataobject.*;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.*;
import com.paytech.platform.support.integration.domain.execution.*;
import com.paytech.platform.support.integration.domainservice.repository.*;
import com.paytech.platform.support.integration.domainservice.validator.ApiFieldValidator;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationEndpointValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationScriptValidator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 标准 API 执行快照加载（研发计划第 3 步第 1 节第 1、2 步）：一次短只读事务读取标准 API、接入方、实现流程、 流程引用的原子 API 与其脚本；事务外用保存时的同一套校验器重新校验，未通过返回 500
 * INTEGRATION_CONFIG_INVALID。
 *
 * <p>
 * 不缓存配置，保存即对新调用生效。只读本库，不调用外部服务。
 */
@Slf4j
@Component
public class ExecutionSnapshotLoader {

    /** 标准 API 字段只允许 BODY。 */
    static final Set<ApiFieldLocation> STANDARD_LOCATIONS = Set.of(ApiFieldLocation.BODY);

    private final ApiFieldRepository fields;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final ExternalApiProcessorSupport support;
    private final IntegrationChildSupport childSupport;
    private final ApiFieldValidator fieldValidator;
    private final FlowDefinitionValidator flowValidator;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationScriptValidator scriptValidator;
    private final IntegrationScriptRepository integrationScripts;
    private final IntegrationEndpointValidator endpointValidator;
    private final IntegrationImplementationRepository implementations;
    private final TransactionTemplate query;

    /** 只读事务模板：REPEATABLE_READ，使全部配置来自同一快照，5 秒上限。 */
    public ExecutionSnapshotLoader(ApiFieldRepository fields, ExternalApiConfigurationCodec codec,
        IntegrationRepository integrations, StandardApiRepository standardApis, ExternalApiProcessorSupport support,
        IntegrationChildSupport childSupport, ApiFieldValidator fieldValidator, FlowDefinitionValidator flowValidator,
        IntegrationApiRepository integrationApis, IntegrationScriptValidator scriptValidator,
        IntegrationScriptRepository integrationScripts, IntegrationEndpointValidator endpointValidator,
        IntegrationImplementationRepository implementations, PlatformTransactionManager manager) {
        this.fields = fields;
        this.codec = codec;
        this.integrations = integrations;
        this.standardApis = standardApis;
        this.support = support;
        this.childSupport = childSupport;
        this.fieldValidator = fieldValidator;
        this.flowValidator = flowValidator;
        this.integrationApis = integrationApis;
        this.scriptValidator = scriptValidator;
        this.integrationScripts = integrationScripts;
        this.endpointValidator = endpointValidator;
        this.implementations = implementations;
        this.query = new TransactionTemplate(manager);
        this.query.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.query.setReadOnly(true);
        this.query.setTimeout(5);
    }

    /**
     * 加载并校验执行快照。
     *
     * @param standardApiCode
     *            标准 API 编码
     * @param integrationCode
     *            接入方编码
     * @return 已通过运行前校验的快照
     * @throws FailException
     *             404 STANDARD_API_NOT_FOUND / INTEGRATION_IMPLEMENTATION_NOT_FOUND
     * @throws ErrorException
     *             500 INTEGRATION_CONFIG_INVALID；5xx 代码只能经 ErrorException 映射
     */
    public ExecutionSnapshot load(String standardApiCode, String integrationCode) {
        Rows rows = query.execute(status -> read(standardApiCode, integrationCode));
        return verify(rows);
    }

    /** 同一只读事务中读取全部配置行。 */
    Rows read(String standardApiCode, String integrationCode) {
        StandardApi standardApi = standardApis.byCode(standardApiCode);
        if (standardApi == null) {
            throw new FailException(StandardApiExecutionErrorCode.STANDARD_API_NOT_FOUND,
                "标准 API 不存在: " + standardApiCode);
        }
        Integration integration = integrations.byCode(integrationCode);
        IntegrationImplementation implementation = integration == null ? null
            : implementations.byKey(integration.getIntegrationId(), standardApi.getStandardApiId());
        if (implementation == null) {
            throw new FailException(StandardApiExecutionErrorCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND,
                "接入方不存在或没有实现该标准 API: " + integrationCode + ", 标准 API: " + standardApiCode);
        }
        JsonNode flow = codec.tree(implementation.getFlowDefinitionJson());
        Set<String> apiCodes = new java.util.HashSet<>();
        flow.path("nodes").forEach(node -> apiCodes.add(node.path("integrationApi").asString("")));
        List<IntegrationApi> apis = integrationApis.allByIntegration(integration.getIntegrationId()).stream()
            .filter(api -> apiCodes.contains(api.getApiCode())).toList();
        var ids = new ArrayList<Long>(List.of(standardApi.getStandardApiId()));
        apis.forEach(api -> ids.add(api.getIntegrationApiId()));
        List<ApiField> fieldRows = fields.byApis(ids, List.of(ApiFieldDirection.values()));
        Set<String> scriptCodes = apis.stream().flatMap(api -> codec.scriptCodes(api.getDefinitionJson()).stream())
            .collect(Collectors.toSet());
        List<IntegrationScript> scripts = integrationScripts.allByIntegration(integration.getIntegrationId()).stream()
            .filter(script -> scriptCodes.contains(script.getScriptCode())).toList();
        return new Rows(standardApi, integration, implementation.getVersion(), flow, apis, fieldRows, scripts);
    }

    /** 事务外重新校验并组装快照。 */
    ExecutionSnapshot verify(Rows rows) {
        var issues = new ArrayList<ValidationIssue>();
        var standardRows = own(rows.fieldRows, rows.standardApi.getStandardApiId());
        List<FieldDefinition> request = support.definitions(standardRows, ApiFieldDirection.STANDARD_API_REQUEST);
        List<FieldDefinition> response = support.definitions(standardRows, ApiFieldDirection.STANDARD_API_RESPONSE);
        issues.addAll(fieldValidator.validate(request, STANDARD_LOCATIONS, "standardApi.requestFields"));
        issues.addAll(fieldValidator.validate(response, STANDARD_LOCATIONS, "standardApi.responseFields"));
        List<EndpointDefinition> endpoints = codec.endpoints(rows.integration.getEndpoints());
        issues.addAll(endpointValidator.validate(endpoints));
        var scripts = new LinkedHashMap<String, ExecutionScript>();
        var signatures = new LinkedHashMap<String, ScriptSignature>();
        for (IntegrationScript script : rows.scripts) {
            var signature = childSupport.signature(script);
            signatures.put(script.getScriptCode(), signature);
            scriptValidator.validate(signature.getParameters(), script.getSourceCode())
                .forEach(issue -> issues.add(located(issue, "scripts." + script.getScriptCode())));
            scripts.put(script.getScriptCode(), new ExecutionScript(script.getScriptCode(), script.getExecutionStage(),
                signature.getParameters(), script.getSourceCode()));
        }
        var apis = new LinkedHashMap<String, ExecutionApi>();
        var contracts = new LinkedHashMap<String, ApiContract>();
        for (IntegrationApi api : rows.apis) {
            var own = own(rows.fieldRows, api.getIntegrationApiId());
            var apiRequest = support.definitions(own, ApiFieldDirection.INTEGRATION_API_REQUEST);
            var apiResponse = support.definitions(own, ApiFieldDirection.INTEGRATION_API_RESPONSE);
            JsonNode definition = codec.tree(api.getDefinitionJson());
            childSupport.check(rows.integration, apiRequest, apiResponse, definition, signatures).getIssues()
                .forEach(issue -> issues.add(located(issue, "apis." + api.getApiCode())));
            apis.put(api.getApiCode(), new ExecutionApi(api.getApiCode(), definition, apiRequest, apiResponse));
            contracts.put(api.getApiCode(),
                codec.apiContract(api.getApiCode(), api.getApiName(), apiRequest, api.getDefinitionJson()));
        }
        issues.addAll(flowValidator.validate(rows.flow, request, response, contracts).getIssues());
        requireValid(issues, rows);
        return new ExecutionSnapshot(rows.standardApi.getStandardApiCode(), rows.integration.getIntegrationCode(),
            FieldSchema.of(request), FieldSchema.outputs(response), ExecutionFlow.from(rows.flow), Map.copyOf(apis),
            Map.copyOf(scripts), endpoints.stream().collect(
                Collectors.toUnmodifiableMap(EndpointDefinition::getEndpointCode, EndpointDefinition::getBaseUrl)),
            endpoints.stream().filter(EndpointDefinition::isSandbox).map(EndpointDefinition::getEndpointCode)
                .collect(Collectors.toUnmodifiableSet()),
            versions(rows));
    }

    /** 本次加载的各项配置版本，键顺序固定。 */
    static Map<String, Object> versions(Rows rows) {
        var result = new LinkedHashMap<String, Object>();
        result.put("standardApi", rows.standardApi.getVersion());
        result.put("integration", rows.integration.getVersion());
        result.put("implementation", rows.implementationVersion);
        result.put("apis", rows.apis.stream().collect(Collectors.toMap(IntegrationApi::getApiCode,
            IntegrationApi::getVersion, (left, right) -> left, java.util.TreeMap::new)));
        result.put("scripts", rows.scripts.stream().collect(Collectors.toMap(IntegrationScript::getScriptCode,
            IntegrationScript::getVersion, (left, right) -> left, java.util.TreeMap::new)));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** 存在阻断问题时记录问题代码与位置（不含配置内容）并返回 500。 */
    void requireValid(List<ValidationIssue> issues, Rows rows) {
        var blocking = issues.stream().filter(ValidationIssue::blocking).toList();
        if (blocking.isEmpty()) {
            return;
        }
        log.warn("外部 API 配置未通过运行前校验 standardApiCode={} integrationCode={} issues={}",
            rows.standardApi.getStandardApiCode(), rows.integration.getIntegrationCode(),
            blocking.stream().map(issue -> issue.getCode() + "@" + issue.getLocation()).toList());
        throw new ErrorException(StandardApiExecutionErrorCode.INTEGRATION_CONFIG_INVALID, "外部 API 配置未通过运行前校验: "
            + rows.integration.getIntegrationCode() + ", 标准 API: " + rows.standardApi.getStandardApiCode());
    }

    /** 给问题位置加上所属配置前缀。 */
    static ValidationIssue located(ValidationIssue issue, String prefix) {
        return new ValidationIssue(issue.getSeverity(), issue.getCode(), prefix + "." + issue.getLocation(),
            issue.getMessage());
    }

    static List<ApiField> own(List<ApiField> rows, long apiId) {
        return rows.stream().filter(row -> row.getApiId().equals(apiId)).toList();
    }

    /** 同一事务读取的配置行。 */
    record Rows(StandardApi standardApi, Integration integration, int implementationVersion, JsonNode flow,
        List<IntegrationApi> apis,
        List<ApiField> fieldRows, List<IntegrationScript> scripts) {
    }
}
