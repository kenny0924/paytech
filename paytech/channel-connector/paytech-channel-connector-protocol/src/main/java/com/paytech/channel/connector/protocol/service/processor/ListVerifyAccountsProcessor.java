package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyAccountView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;

/** V01 验证账号列表：按编码排序，不带参数与凭据；每个接入方最多 50 个，不分页。 */
@Component
@AllArgsConstructor
public class ListVerifyAccountsProcessor extends AbstractProcessor<ListIntegrationChildrenRequest, List<VerifyAccountView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final IntegrationVerifyAccountRepository accounts;

    @Override
    protected List<VerifyAccountView> processRequest(ListIntegrationChildrenRequest request) {
        long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
        return accounts.allByIntegration(integrationId).stream().map(row -> verify.accountView(row, List.of(), false))
            .toList();
    }
}
