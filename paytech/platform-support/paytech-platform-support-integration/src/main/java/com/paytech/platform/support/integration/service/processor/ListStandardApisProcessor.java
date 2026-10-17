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
import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ConfigurationReference;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.StandardApiSummaryView;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.service.processor.request.ListExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/**
 * E01 标准 API 列表：分页读取主表后批量组合字段数量与实现方。
 *
 * <p>普通列表不开显式事务，允许各查询观察相邻的已提交状态。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListStandardApisProcessor
    extends AbstractProcessor<ListExternalApiConfigRequest, PagingResponseBody<StandardApiSummaryView>> {

    private final ApiFieldRepository fields;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final OperationsManagementPolicyService policy;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected PagingResponseBody<StandardApiSummaryView> processRequest(ListExternalApiConfigRequest request) {
        policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
        var paging = request.getPaging();
        List<StandardApi> rows = standardApis.list(request.getKeyword(), paging.offset(), paging.size());
        long total = standardApis.count(request.getKeyword());
        List<Long> ids = rows.stream().map(StandardApi::getStandardApiId).toList();
        List<ApiField> fieldRows = ids.isEmpty() ? List.of() : fields.byApis(ids,
            List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE));
        List<IntegrationImplementation> implementationRows = implementations.allByStandardApis(ids);
        Map<Long, Integration> implementers = integrationsById(implementationRows);
        var items = rows.stream().map(row -> summary(row, fieldRows, implementationRows, implementers)).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** 批量读取实现方接入方，空集合不查询。 */
    Map<Long, Integration> integrationsById(List<IntegrationImplementation> rows) {
        var ids = rows.stream().map(IntegrationImplementation::getIntegrationId).distinct().toList();
        return integrations.byIds(ids).stream()
            .collect(Collectors.toMap(Integration::getIntegrationId, Function.identity()));
    }

    /** 组合单个列表项；实现方按接入方编码排序。 */
    StandardApiSummaryView summary(StandardApi row, List<ApiField> fieldRows,
        List<IntegrationImplementation> implementationRows, Map<Long, Integration> implementers) {
        long id = row.getStandardApiId();
        int requestCount = (int) fieldRows.stream()
            .filter(field -> field.getApiId() == id && field.getDirection() == ApiFieldDirection.STANDARD_API_REQUEST)
            .count();
        int responseCount = (int) fieldRows.stream()
            .filter(field -> field.getApiId() == id && field.getDirection() == ApiFieldDirection.STANDARD_API_RESPONSE)
            .count();
        var implementedBy = implementationRows.stream().filter(item -> item.getStandardApiId() == id)
            .map(item -> implementers.get(item.getIntegrationId()))
            .map(integration -> new ConfigurationReference(integration.getIntegrationCode(),
                integration.getIntegrationName()))
            .sorted(java.util.Comparator.comparing(ConfigurationReference::getCode)).toList();
        return new StandardApiSummaryView(row.getStandardApiCode(), row.getStandardApiName(), requestCount,
            responseCount, implementedBy, row.getVersion(), row.getUpdateTime());
    }
}
