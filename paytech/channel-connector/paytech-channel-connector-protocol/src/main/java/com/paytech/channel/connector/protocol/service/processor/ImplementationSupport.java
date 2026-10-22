package com.paytech.channel.connector.protocol.service.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelApi;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelImplementation;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ApiContract;
import com.paytech.channel.connector.protocol.domain.ExternalApiConfigurationCodec;
import com.paytech.channel.connector.protocol.domain.FieldDefinition;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domainservice.repository.ApiFieldRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelApiRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelImplementationRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.StandardApiRepository;
import com.paytech.channel.connector.protocol.domainservice.validator.FlowDefinitionValidator;

import lombok.AllArgsConstructor;

import tools.jackson.databind.JsonNode;

/**
 * 标准 API 实现相关 Processor 共用的查询与流程校验组合：读取标准 API 字段、组装原子 API 合同，
 * 以及在标准 API 或原子 API 修改时复核受影响的流程。
 *
 * <p>只读取本模块 Repository，不写库；事务由调用方 Processor 持有。
 */
@Component
@AllArgsConstructor
public class ImplementationSupport {

    /** 标准 API 的两个字段方向。 */
    static final List<ApiFieldDirection> STANDARD_DIRECTIONS =
        List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE);

    private final ApiFieldRepository fields;
    private final ExternalApiConfigurationCodec codec;
    private final ChannelRepository channels;
    private final StandardApiRepository standardApis;
    private final ExternalApiProcessorSupport support;
    private final FlowDefinitionValidator flowValidator;
    private final ChannelApiRepository channelApis;
    private final ChannelImplementationRepository implementations;

    /** 读取标准 API，不存在返回 404。 */
    StandardApi standardApi(String code) {
        return support.requireFound(standardApis.byCode(code));
    }

    /** 写入时锁定路径中的标准 API，与标准 API 修改、删除在数据库层串行；不存在返回 404。 */
    StandardApi lockStandardApi(String code) {
        return support.requireFound(standardApis.lock(code));
    }

    /**
     * 写入时锁定请求体引用的标准 API，与标准 API 修改、删除在数据库层串行。
     * 不存在属于引用错误，按业务校验失败返回而不是 404。
     */
    StandardApi referencedStandardApi(String code) {
        StandardApi standardApi = standardApis.lock(code);
        if (standardApi == null) {
            throw new BizCheckFailException("标准 API 不存在：" + code);
        }
        return standardApi;
    }

    /** 已保存标准 API 的请求与响应字段。 */
    Map<ApiFieldDirection, List<FieldDefinition>> standardFields(StandardApi standardApi) {
        var rows = fields.byApis(List.of(standardApi.getStandardApiId()), STANDARD_DIRECTIONS);
        var result = new LinkedHashMap<ApiFieldDirection, List<FieldDefinition>>();
        STANDARD_DIRECTIONS.forEach(direction -> result.put(direction, support.definitions(rows, direction)));
        return result;
    }

    /** 渠道全部原子 API 合同，按编码排序并索引。 */
    Map<String, ApiContract> apiContracts(long channelId) {
        var apis = channelApis.allByChannel(channelId).stream()
            .sorted(Comparator.comparing(ChannelApi::getApiCode)).toList();
        if (apis.isEmpty()) {
            return Map.of();
        }
        var rows = fields.byApis(apis.stream().map(ChannelApi::getChannelApiId).toList(),
            List.of(ApiFieldDirection.INTEGRATION_API_REQUEST));
        var result = new LinkedHashMap<String, ApiContract>();
        for (var api : apis) {
            var own = rows.stream().filter(row -> row.getApiId().equals(api.getChannelApiId())).toList();
            result.put(api.getApiCode(), codec.apiContract(api.getApiCode(), api.getApiName(),
                support.definitions(own, ApiFieldDirection.INTEGRATION_API_REQUEST), api.getDefinitionJson()));
        }
        return result;
    }

    /** 用已保存的标准 API 字段与原子 API 合同校验流程。 */
    FlowDefinitionValidator.Result check(StandardApi standardApi, long channelId, JsonNode flow) {
        var standard = standardFields(standardApi);
        return flowValidator.validate(flow, standard.get(ApiFieldDirection.STANDARD_API_REQUEST),
            standard.get(ApiFieldDirection.STANDARD_API_RESPONSE), apiContracts(channelId));
    }

    /**
     * 用修改后的标准 API 字段复核全部实现，返回会失效的渠道编码（按编码排序）。
     *
     * @param standardApi 被修改的标准 API
     * @param requestFields 新请求字段
     * @param responseFields 新响应字段
     */
    List<String> brokenByStandardApi(StandardApi standardApi, List<FieldDefinition> requestFields,
        List<FieldDefinition> responseFields) {
        var rows = implementations.allByStandardApi(standardApi.getStandardApiId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Channel> integrationById = channels.byIds(rows.stream()
            .map(ChannelImplementation::getChannelId).toList()).stream()
            .collect(Collectors.toMap(Channel::getChannelId, Function.identity()));
        var broken = new ArrayList<String>();
        for (var row : rows) {
            var issues = flowValidator.validate(codec.tree(row.getFlowDefinitionJson()), requestFields, responseFields,
                apiContracts(row.getChannelId())).getIssues();
            if (issues.stream().anyMatch(ValidationIssue::blocking)) {
                broken.add(integrationById.get(row.getChannelId()).getChannelCode());
            }
        }
        return broken.stream().sorted().toList();
    }

    /**
     * 用修改后的原子 API 合同复核本渠道引用它的流程，返回会失效的标准 API 编码（按编码排序）。
     *
     * @param channelId 所属渠道
     * @param contract 修改后的原子 API 合同
     */
    List<String> brokenByIntegrationApi(long channelId, ApiContract contract) {
        var rows = implementations.allByChannel(channelId).stream()
            .filter(row -> !codec.nodesUsing(row.getFlowDefinitionJson(), contract.getCode()).isEmpty()).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        var contracts = new LinkedHashMap<>(apiContracts(channelId));
        contracts.put(contract.getCode(), contract);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(rows.stream()
            .map(ChannelImplementation::getStandardApiId).toList()).stream()
            .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
        var broken = new ArrayList<String>();
        for (var row : rows) {
            StandardApi standardApi = standardApiById.get(row.getStandardApiId());
            var standard = standardFields(standardApi);
            var issues = flowValidator.validate(codec.tree(row.getFlowDefinitionJson()),
                standard.get(ApiFieldDirection.STANDARD_API_REQUEST),
                standard.get(ApiFieldDirection.STANDARD_API_RESPONSE), contracts).getIssues();
            if (issues.stream().anyMatch(ValidationIssue::blocking)) {
                broken.add(standardApi.getStandardApiCode());
            }
        }
        return broken.stream().sorted().toList();
    }
}
