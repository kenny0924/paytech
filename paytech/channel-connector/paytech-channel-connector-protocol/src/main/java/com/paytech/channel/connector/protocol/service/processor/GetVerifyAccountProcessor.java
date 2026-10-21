package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyCase;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyAccountView;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyCaseRepository;
import com.paytech.channel.connector.protocol.service.processor.request.GetIntegrationChildRequest;

import lombok.AllArgsConstructor;

/** V02 验证账号详情：账号与引用它的用例在同一只读快照中读取。 */
@Component
@AllArgsConstructor
public class GetVerifyAccountProcessor extends AbstractProcessor<GetIntegrationChildRequest, VerifyAccountView> {

    /** 读取引用方时的上限，超过即只展示前若干个。 */
    static final int MAX_REFERENCES = 100;

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiTransactions transactions;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyAccountRepository accounts;
    private final IntegrationVerifyCaseRepository cases;

    @Override
    protected VerifyAccountView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long integrationId = children.integration(request.getIntegrationCode()).getIntegrationId();
            IntegrationVerifyAccount row = support.requireFound(accounts.byCode(integrationId, request.getCode()));
            var referenced = cases.list(integrationId, null, 0, MAX_REFERENCES).stream()
                .filter(item -> item.getVerifyAccountId().equals(row.getVerifyAccountId()))
                .map(IntegrationVerifyCase::getCaseCode).sorted().toList();
            return verify.accountView(row, referenced, true);
        });
    }
}
