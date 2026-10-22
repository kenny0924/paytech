package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ApiReference;
import com.paytech.channel.connector.protocol.domain.EndpointDefinition;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.ScriptSignature;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelScriptRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.validator.ApiFieldValidator;
import com.paytech.channel.connector.protocol.domainservice.validator.IntegrationApiDefinitionValidator;

import lombok.AllArgsConstructor;

/**
 * 原子 API 与公共脚本 Processor 共用的查询与校验组合：定位渠道、读取脚本签名、整体校验原子 API 配置、扫描引用方。
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
    private final ChannelRepository channels;
    private final StandardApiRepository standardApis;
    private final ExternalApiProcessorSupport support;
    private final ChannelApiRepository channelApis;
    private final ChannelScriptRepository channelScripts;
    private final IntegrationApiDefinitionValidator definitionValidator;
    private final ChannelImplementationRepository implementations;

    /** 读取渠道，不存在返回 404。 */
    Channel channel(String code) {
        return support.requireFound(channels.byCode(code));
    }

    /** 写入时锁定渠道行，与渠道删除串行。 */
    Channel lockChannel(String code) {
        return support.requireFound(channels.lock(code));
    }

    /** 渠道全部脚本签名，按编码索引。 */
    Map<String, ScriptSignature> scriptSignatures(long channelId) {
        return channelScripts.allByChannel(channelId).stream().map(this::signature)
            .collect(Collectors.toMap(ScriptSignature::getCode, Function.identity()));
    }

    /** 已保存脚本的签名。 */
    ScriptSignature signature(ChannelScript script) {
        return new ScriptSignature(script.getScriptCode(), script.getExecutionStage(),
            codec.parameters(script.getParameterDefinition()));
    }

    /** 原子 API 的字段与定义整体校验，返回全部问题及定义摘要。 */
    IntegrationApiDefinitionValidator.Result check(Channel integration, List<FieldDefinition> requestFields,
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
    Map<ApiFieldDirection, List<FieldDefinition>> savedFields(ChannelApi api) {
        var rows = fields.byApis(List.of(api.getChannelApiId()), API_DIRECTIONS);
        var result = new LinkedHashMap<ApiFieldDirection, List<FieldDefinition>>();
        for (var direction : API_DIRECTIONS) {
            result.put(direction, support.definitions(rows, direction));
        }
        return result;
    }

    /** 引用某原子 API 的标准 API 实现及节点，按标准 API 编码排序。 */
    List<ApiReference> apiReferences(long channelId, String apiCode) {
        var rows = implementations.allByChannel(channelId);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(rows.stream()
            .map(ChannelImplementation::getStandardApiId).toList()).stream()
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
    List<ChannelApi> scriptUsers(long channelId, String scriptCode) {
        return channelApis.allByChannel(channelId).stream()
            .filter(api -> codec.scriptCodes(api.getDefinitionJson()).contains(scriptCode)).toList();
    }

    /**
     * 用修改后的脚本签名重校验引用该脚本的原子 API，返回会失效的原子 API 编码（按编码排序）。
     *
     * @param integration 所属渠道
     * @param scriptCode 被修改的脚本编码
     * @param stage 新执行阶段
     * @param parameters 新参数声明
     */
    List<String> brokenScriptUsers(Channel integration, String scriptCode,
        com.paytech.channel.connector.api.enums.ScriptExecutionStage stage,
        List<com.paytech.channel.connector.protocol.domain.ParameterDeclaration> parameters) {
        var users = scriptUsers(integration.getChannelId(), scriptCode);
        if (users.isEmpty()) {
            return List.of();
        }
        var signatures = new java.util.HashMap<>(scriptSignatures(integration.getChannelId()));
        signatures.put(scriptCode, new ScriptSignature(scriptCode, stage, parameters));
        return users.stream().filter(api -> {
            var saved = savedFields(api);
            return check(integration, saved.get(ApiFieldDirection.INTEGRATION_API_REQUEST),
                saved.get(ApiFieldDirection.INTEGRATION_API_RESPONSE), codec.tree(api.getDefinitionJson()), signatures)
                .getIssues().stream().anyMatch(ValidationIssue::blocking);
        }).map(ChannelApi::getApiCode).sorted().toList();
    }
}
