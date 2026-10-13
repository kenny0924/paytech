package com.paytech.platform.support.integration.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsManagementPolicyService;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.ExternalApiPermissions;
import com.paytech.platform.support.integration.domain.FlowEditorContextView;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E29 流程编辑器上下文：一次返回标准 API 字段与本接入方全部原子 API 合同，避免前端逐个读取详情。
 *
 * <p>与保存时的流程校验使用同一份合同组装逻辑，并在同一只读快照中读取。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetFlowEditorContextProcessor extends AbstractProcessor<GetIntegrationChildRequest, FlowEditorContextView> {

    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;
    private final OperationsManagementPolicyService policy;

    @Override
    protected FlowEditorContextView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            policy.requirePermission(request.getActor(), request.getSession(), ExternalApiPermissions.READ);
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            var standard = flows.standardFields(flows.standardApi(request.getCode()));
            return new FlowEditorContextView(standard.get(ApiFieldDirection.STANDARD_API_REQUEST),
                standard.get(ApiFieldDirection.STANDARD_API_RESPONSE),
                List.copyOf(flows.apiContracts(integrationId).values()));
        });
    }
}
