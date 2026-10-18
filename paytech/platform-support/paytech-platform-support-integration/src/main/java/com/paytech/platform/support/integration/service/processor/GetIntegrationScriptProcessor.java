package com.paytech.platform.support.integration.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.IntegrationScriptDetailView;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** E18 公共脚本详情：脚本与引用它的原子 API 在同一只读快照中读取，删除保护提示与实际状态一致。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetIntegrationScriptProcessor
    extends AbstractProcessor<GetIntegrationChildRequest, IntegrationScriptDetailView> {

    private final ExternalApiConfigurationCodec codec;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final OperationsManagementPolicyService policy;
    private final IntegrationScriptRepository integrationScripts;

    @Override
    protected IntegrationScriptDetailView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            IntegrationScript row = support.requireFound(integrationScripts.byCode(integrationId, request.getCode()));
            var referencedBy = children.scriptUsers(integrationId, row.getScriptCode()).stream()
                .map(IntegrationApi::getApiCode).sorted().toList();
            return new IntegrationScriptDetailView(row.getScriptCode(), row.getScriptName(), row.getExecutionStage(),
                codec.parameters(row.getParameterDefinition()), row.getSourceCode(), row.getDescription(),
                referencedBy, row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getUpdatedBy());
        });
    }
}
