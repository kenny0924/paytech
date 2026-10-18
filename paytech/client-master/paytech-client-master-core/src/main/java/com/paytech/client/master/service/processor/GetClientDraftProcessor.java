package com.paytech.client.master.service.processor;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.master.domain.onboarding.ClientAgreementPartyUsage;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.ClientPersonView;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.ext.rest.ClientDraftConverter;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ClientDraftContactClient;
import com.paytech.client.master.service.processor.request.GetClientDraftRequest;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 读取申请资料：本库快照、Vault 联系方式与 commercial 合同引用组合为一份响应。
 * <li>会话在事务外核验；申请、人员、关系与材料在同一 REPEATABLE READ 只读快照内读取，不加锁。</li>
 * <li>读取事务结束后再查 commercial 合同引用；申请不可编辑时人员标记恒为 false，无需查询。</li>
 */
@AllArgsConstructor
public class GetClientDraftProcessor extends AbstractProcessor<GetClientDraftRequest, ClientDraftResponseBody> {
    private final ClientDraftService service;
    private final ClientDraftAccountClient accounts;
    private final ClientDraftContactClient contacts;
    private final ClientAgreementPartyClient agreementParties;
    private final TransactionTemplate reads;

    @Override
    protected ClientDraftResponseBody processRequest(GetClientDraftRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var view = reads.execute(tx -> service.read(request.getAccountId(), request.getApplicationId()));
        var usage = ClientDraftPolicy.isEditable(view.identity())
            ? agreementParties.usage(view.people().stream().map(ClientPersonView::identity).toList())
            : ClientAgreementPartyUsage.unavailable();
        return ClientDraftConverter.response(view, contacts.reveal(view.contacts()), usage);
    }
}
