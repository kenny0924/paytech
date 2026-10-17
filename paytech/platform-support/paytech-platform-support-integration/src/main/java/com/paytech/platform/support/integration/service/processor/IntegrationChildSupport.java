package com.paytech.platform.support.integration.service.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ApiReference;
import com.paytech.platform.support.integration.domain.EndpointDefinition;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ScriptSignature;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.domainservice.validator.ApiFieldValidator;
import com.paytech.platform.support.integration.domainservice.validator.IntegrationApiDefinitionValidator;

import lombok.AllArgsConstructor;

/**
 * 原子 API 与公共脚本 Processor 共用的查询与校验组合：定位接入方、读取脚本签名、整体校验原子 API 配置、扫描引用方。
 *
 * <p>只读取本模块 Repository，不写库；事务由调用方 Processor 持有。
 */
@Component
@AllArgsConstructor
public class IntegrationChildSupport {

    /** 原子 API 请求允许全部位置。 */
    static final Set<ApiFieldLocation> REQUEST_LOCATIONS = Set.of(ApiFieldLocation.values());

    /** 原子 API 响应只允许 HEADER、BODY。 */
    static final Set<ApiFieldLocation> RESPONSE_LOCATIONS = Set.of(ApiFieldLocation.HEADER, ApiFieldLocation.BODY);

    /** 原子 API 的两个字段方向。 */
    static final List<ApiFieldDirection> API_DIRECTIONS =
        List.of(ApiFieldDirection.INTEGRATION_API_REQUEST, ApiFieldDirection.INTEGRATION_API_RESPONSE);

    private final ApiFieldRepository fields;
    private final ApiFieldValidator fieldValidator;
    private final ExternalApiConfigurationCodec codec;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationScriptRepository integrationScripts;
    private final IntegrationApiDefinitionValidator definitionValidator;
    private final IntegrationImplementationRepository implementations;

    /** 读取接入方，不存在返回 404。 */
    Integration integration(String code) {
        return support.requireFound(integrations.byCode(code));
    }

    /** 写入时锁定接入方行，与接入方删除串行。 */
    Integration lockIntegration(String code) {
        return support.requireFound(integrations.lock(code));
    }

    /** 接入方全部脚本签名，按编码索引。 */
    Map<String, ScriptSignature> scriptSignatures(long integrationId) {
        return integrationScripts.allByIntegration(integrationId).stream().map(this::signature)
            .collect(Collectors.toMap(ScriptSignature::getCode, Function.identity()));
    }

    /** 已保存脚本的签名。 */
    ScriptSignature signature(IntegrationScript script) {
        return new ScriptSignature(script.getScriptCode(), script.getExecutionStage(),
            codec.parameters(script.getParameterDefinition()));
    }

    /** 原子 API 的字段与定义整体校验，返回全部问题及定义摘要。 */
    IntegrationApiDefinitionValidator.Result check(Integration integration, List<FieldDefinition> requestFields,
        List<FieldDefinition> responseFields, tools.jackson.databind.JsonNode definition,
        Map<String, ScriptSignature> scripts) {
        var issues = new ArrayList<ValidationIssue>(fieldValidator.validate(requestFields, REQUEST_LOCATIONS,
            "requestFields"));
        issues.addAll(fieldValidator.validate(responseFields, RESPONSE_LOCATIONS, "responseFields"));
        Set<String> endpointCodes = codec.endpoints(integration.getEndpoints()).stream()
            .map(EndpointDefinition::getEndpointCode).collect(Collectors.toSet());
        var result = definitionValidator.validate(definition, requestFields, endpointCodes, scripts);
        issues.addAll(result.getIssues());
        return new IntegrationApiDefinitionValidator.Result(issues, issues.isEmpty() ? result.getDefinition() : null);
    }

    /** 已保存原子 API 的请求与响应字段。 */
    Map<ApiFieldDirection, List<FieldDefinition>> savedFields(IntegrationApi api) {
        var rows = fields.byApis(List.of(api.getIntegrationApiId()), API_DIRECTIONS);
        var result = new LinkedHashMap<ApiFieldDirection, List<FieldDefinition>>();
        for (var direction : API_DIRECTIONS) {
            result.put(direction, support.definitions(rows, direction));
        }
        return result;
    }

    /** 引用某原子 API 的标准 API 实现及节点，按标准 API 编码排序。 */
    List<ApiReference> apiReferences(long integrationId, String apiCode) {
        var rows = implementations.allByIntegration(integrationId);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(rows.stream()
            .map(IntegrationImplementation::getStandardApiId).toList()).stream()
            .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
        var references = new TreeMap<String, List<String>>();
        for (var row : rows) {
            var nodes = codec.nodesUsing(row.getFlowDefinitionJson(), apiCode);
            if (!nodes.isEmpty()) {
                references.put(standardApiById.get(row.getStandardApiId()).getStandardApiCode(), nodes);
            }
        }
        return references.entrySet().stream().map(entry -> new ApiReference(entry.getKey(), entry.getValue())).toList();
    }

    /** 引用某脚本的原子 API，按编码排序。 */
    List<IntegrationApi> scriptUsers(long integrationId, String scriptCode) {
        return integrationApis.allByIntegration(integrationId).stream()
            .filter(api -> codec.scriptCodes(api.getDefinitionJson()).contains(scriptCode)).toList();
    }

    /**
     * 用修改后的脚本签名重校验引用该脚本的原子 API，返回会失效的原子 API 编码（按编码排序）。
     *
     * @param integration 所属接入方
     * @param scriptCode 被修改的脚本编码
     * @param stage 新执行阶段
     * @param parameters 新参数声明
     */
    List<String> brokenScriptUsers(Integration integration, String scriptCode,
        com.paytech.platform.support.api.enums.ScriptExecutionStage stage,
        List<com.paytech.platform.support.integration.domain.ParameterDeclaration> parameters) {
        var users = scriptUsers(integration.getIntegrationId(), scriptCode);
        if (users.isEmpty()) {
            return List.of();
        }
        var signatures = new java.util.HashMap<>(scriptSignatures(integration.getIntegrationId()));
        signatures.put(scriptCode, new ScriptSignature(scriptCode, stage, parameters));
        return users.stream().filter(api -> {
            var saved = savedFields(api);
            return check(integration, saved.get(ApiFieldDirection.INTEGRATION_API_REQUEST),
                saved.get(ApiFieldDirection.INTEGRATION_API_RESPONSE), codec.tree(api.getDefinitionJson()), signatures)
                .getIssues().stream().anyMatch(ValidationIssue::blocking);
        }).map(IntegrationApi::getApiCode).sorted().toList();
    }
}
