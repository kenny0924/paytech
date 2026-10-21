package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.FlowEditorContextView;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/**
 * E29 流程编辑器上下文：一次返回标准 API 字段与本接入方全部原子 API 合同，避免前端逐个读取详情。
 *
 * <p>与保存时的流程校验使用同一份合同组装逻辑，并在同一只读快照中读取。
 */
@Component
@AllArgsConstructor
public class GetFlowEditorContextProcessor extends AbstractProcessor<GetIntegrationChildRequest, FlowEditorContextView> {

    private final IntegrationChildSupport children;
    private final ImplementationSupport flows;
    private final ExternalApiTransactions transactions;

    @Override
    protected FlowEditorContextView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            var standard = flows.standardFields(flows.standardApi(request.getCode()));
            return new FlowEditorContextView(standard.get(ApiFieldDirection.STANDARD_API_REQUEST),
                standard.get(ApiFieldDirection.STANDARD_API_RESPONSE),
                List.copyOf(flows.apiContracts(integrationId).values()));
        });
    }
}
