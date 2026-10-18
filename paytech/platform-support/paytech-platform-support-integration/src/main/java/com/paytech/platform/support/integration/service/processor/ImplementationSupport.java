package com.paytech.platform.support.integration.service.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ApiContract;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.FieldDefinition;
import com.paytech.platform.support.integration.domain.ValidationIssue;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.domainservice.validator.FlowDefinitionValidator;

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
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final ExternalApiProcessorSupport support;
    private final FlowDefinitionValidator flowValidator;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationImplementationRepository implementations;

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

    /** 接入方全部原子 API 合同，按编码排序并索引。 */
    Map<String, ApiContract> apiContracts(long integrationId) {
        var apis = integrationApis.allByIntegration(integrationId).stream()
            .sorted(Comparator.comparing(IntegrationApi::getApiCode)).toList();
        if (apis.isEmpty()) {
            return Map.of();
        }
        var rows = fields.byApis(apis.stream().map(IntegrationApi::getIntegrationApiId).toList(),
            List.of(ApiFieldDirection.INTEGRATION_API_REQUEST));
        var result = new LinkedHashMap<String, ApiContract>();
        for (var api : apis) {
            var own = rows.stream().filter(row -> row.getApiId().equals(api.getIntegrationApiId())).toList();
            result.put(api.getApiCode(), codec.apiContract(api.getApiCode(), api.getApiName(),
                support.definitions(own, ApiFieldDirection.INTEGRATION_API_REQUEST), api.getDefinitionJson()));
        }
        return result;
    }

    /** 用已保存的标准 API 字段与原子 API 合同校验流程。 */
    FlowDefinitionValidator.Result check(StandardApi standardApi, long integrationId, JsonNode flow) {
        var standard = standardFields(standardApi);
        return flowValidator.validate(flow, standard.get(ApiFieldDirection.STANDARD_API_REQUEST),
            standard.get(ApiFieldDirection.STANDARD_API_RESPONSE), apiContracts(integrationId));
    }

    /**
     * 用修改后的标准 API 字段复核全部实现，返回会失效的接入方编码（按编码排序）。
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
        Map<Long, Integration> integrationById = integrations.byIds(rows.stream()
            .map(IntegrationImplementation::getIntegrationId).toList()).stream()
            .collect(Collectors.toMap(Integration::getIntegrationId, Function.identity()));
        var broken = new ArrayList<String>();
        for (var row : rows) {
            var issues = flowValidator.validate(codec.tree(row.getFlowDefinitionJson()), requestFields, responseFields,
                apiContracts(row.getIntegrationId())).getIssues();
            if (issues.stream().anyMatch(ValidationIssue::blocking)) {
                broken.add(integrationById.get(row.getIntegrationId()).getIntegrationCode());
            }
        }
        return broken.stream().sorted().toList();
    }

    /**
     * 用修改后的原子 API 合同复核本接入方引用它的流程，返回会失效的标准 API 编码（按编码排序）。
     *
     * @param integrationId 所属接入方
     * @param contract 修改后的原子 API 合同
     */
    List<String> brokenByIntegrationApi(long integrationId, ApiContract contract) {
        var rows = implementations.allByIntegration(integrationId).stream()
            .filter(row -> !codec.nodesUsing(row.getFlowDefinitionJson(), contract.getCode()).isEmpty()).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        var contracts = new LinkedHashMap<>(apiContracts(integrationId));
        contracts.put(contract.getCode(), contract);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(rows.stream()
            .map(IntegrationImplementation::getStandardApiId).toList()).stream()
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
