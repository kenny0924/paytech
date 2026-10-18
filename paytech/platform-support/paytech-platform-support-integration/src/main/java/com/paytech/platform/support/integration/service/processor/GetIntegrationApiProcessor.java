package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.IntegrationApiDetailView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** E12 原子 API 详情：主表、字段与流程引用在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetIntegrationApiProcessor extends AbstractProcessor<GetIntegrationChildRequest, IntegrationApiDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationApiRepository integrationApis;
    private final OperationsManagementPolicyService policy;

    @Override
    protected IntegrationApiDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            IntegrationApi row = support.requireFound(integrationApis.byCode(integrationId, request.getCode()));
            var fields = children.savedFields(row);
            return new IntegrationApiDetailView(row.getApiCode(), row.getApiName(), row.getDescription(),
                fields.get(ApiFieldDirection.INTEGRATION_API_REQUEST),
                fields.get(ApiFieldDirection.INTEGRATION_API_RESPONSE), codec.tree(row.getDefinitionJson()),
                children.apiReferences(integrationId, row.getApiCode()), row.getVersion(), row.getCreateTime(),
                row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
