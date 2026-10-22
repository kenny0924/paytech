package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyAccountView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
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
    private final ChannelVerifyAccountRepository accounts;
    private final ChannelVerifyCaseRepository cases;

    @Override
    protected VerifyAccountView processRequest(GetIntegrationChildRequest request) {
        return transactions.query(() -> {
            long channelId = children.channel(request.getIntegrationCode()).getChannelId();
            ChannelVerifyAccount row = support.requireFound(accounts.byCode(channelId, request.getCode()));
            var referenced = cases.list(channelId, null, 0, MAX_REFERENCES).stream()
                .filter(item -> item.getVerifyAccountId().equals(row.getVerifyAccountId()))
                .map(ChannelVerifyCase::getCaseCode).sorted().toList();
            return verify.accountView(row, referenced, true);
        });
    }
}
