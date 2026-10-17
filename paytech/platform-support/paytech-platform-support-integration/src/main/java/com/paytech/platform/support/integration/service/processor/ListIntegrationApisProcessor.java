package com.paytech.platform.support.integration.service.processor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.IntegrationApiSummaryView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/**
 * E11 原子 API 列表：分页读取后从已保存定义提取传输与脚本摘要，并扫描本接入方流程得到引用方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListIntegrationApisProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<IntegrationApiSummaryView>> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final StandardApiRepository standardApis;
    private final IntegrationApiRepository integrationApis;
    private final OperationsManagementPolicyService policy;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected PagingResponseBody<IntegrationApiSummaryView> processRequest(ListIntegrationChildrenRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
        var paging = request.getPaging();
        var rows = integrationApis.list(integrationId, request.getKeyword(), paging.offset(), paging.size());
        long total = integrationApis.count(integrationId, request.getKeyword());
        var flows = implementations.allByIntegration(integrationId);
        Map<Long, StandardApi> standardApiById = standardApis.byIds(flows.stream()
            .map(IntegrationImplementation::getStandardApiId).toList()).stream()
            .collect(Collectors.toMap(StandardApi::getStandardApiId, Function.identity()));
        var items = rows.stream().map(row -> summary(row, flows, standardApiById)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 从已保存定义提取摘要，引用方按标准 API 编码排序。 */
    IntegrationApiSummaryView summary(IntegrationApi row, List<IntegrationImplementation> flows,
        Map<Long, StandardApi> standardApiById) {
        var definition = codec.tree(row.getDefinitionJson());
        var transport = definition.path("transport");
        int requestScripts = 0;
        int responseScripts = 0;
        for (var script : definition.path("scripts")) {
            if ("REQUEST".equals(script.path("stage").asString(""))) {
                requestScripts++;
            } else {
                responseScripts++;
            }
        }
        var referencedBy = flows.stream()
            .filter(flow -> !codec.nodesUsing(flow.getFlowDefinitionJson(), row.getApiCode()).isEmpty())
            .map(flow -> standardApiById.get(flow.getStandardApiId()).getStandardApiCode()).sorted().toList();
        return new IntegrationApiSummaryView(row.getApiCode(), row.getApiName(), transport.path("method").asString(""),
            transport.path("endpointCode").asString(""), transport.path("pathTemplate").asString(""), requestScripts,
            responseScripts, referencedBy, row.getVersion(), row.getUpdateTime());
    }
}
