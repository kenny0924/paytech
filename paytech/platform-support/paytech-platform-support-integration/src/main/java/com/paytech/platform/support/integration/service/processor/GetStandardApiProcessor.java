package com.paytech.platform.support.integration.service.processor;

import java.util.Comparator;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ConfigurationReference;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.StandardApiDetailView;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
import com.paytech.platform.support.integration.service.processor.request.GetExternalApiConfigRequest;

import lombok.AllArgsConstructor;

/** E02 标准 API 详情：主表、字段与实现方在同一只读快照中读取，避免组合出从未同时存在的配置。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetStandardApiProcessor extends AbstractProcessor<GetExternalApiConfigRequest, StandardApiDetailView> {

    private final ApiFieldRepository fields;
    private final IntegrationRepository integrations;
    private final StandardApiRepository standardApis;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final OperationsManagementPolicyService policy;
    private final IntegrationImplementationRepository implementations;

    @Override
    protected StandardApiDetailView processRequest(GetExternalApiConfigRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            StandardApi row = support.requireFound(standardApis.byCode(request.getCode()));
            var fieldRows = fields.byApis(List.of(row.getStandardApiId()),
                List.of(ApiFieldDirection.STANDARD_API_REQUEST, ApiFieldDirection.STANDARD_API_RESPONSE));
            return new StandardApiDetailView(row.getStandardApiCode(), row.getStandardApiName(),
                row.getDescription(), support.definitions(fieldRows, ApiFieldDirection.STANDARD_API_REQUEST),
                support.definitions(fieldRows, ApiFieldDirection.STANDARD_API_RESPONSE), implementers(row),
                row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getUpdatedBy());
        });
    }

    /** 实现该标准 API 的接入方，按编码排序。 */
    List<ConfigurationReference> implementers(StandardApi row) {
        var ids = implementations.allByStandardApi(row.getStandardApiId()).stream()
            .map(IntegrationImplementation::getIntegrationId).toList();
        return integrations.byIds(ids).stream()
            .map(item -> new ConfigurationReference(item.getIntegrationCode(), item.getIntegrationName()))
            .sorted(Comparator.comparing(ConfigurationReference::getCode)).toList();
    }
}
