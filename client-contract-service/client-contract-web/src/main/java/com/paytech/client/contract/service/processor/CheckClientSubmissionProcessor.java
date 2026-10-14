package com.paytech.client.contract.service.processor;

import com.paytech.client.contract.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.contract.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.contract.domain.onboarding.ClientSubmissionPreparation;
import com.paytech.client.contract.domainservice.repository.ClientDraftRepository;
import com.paytech.client.contract.integration.ClientDraftAccountClient;
import com.paytech.client.contract.service.processor.request.CheckClientSubmissionRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.*;

/**
 * 提交前核对：执行与 C14 相同的准备与检查，不加锁、不写入。
 * <li>结果是查询时刻的事实；正式提交仍在锁内重新检查。</li>
 */
public class CheckClientSubmissionProcessor extends AbstractProcessor<CheckClientSubmissionRequest, ClientSubmissionPreparation> {
    private final ClientDraftRepository drafts;
    private final ClientDraftAccountClient accounts;
    private final ClientSubmissionPreparer preparer;

    /** 复用 C14 的准备步骤。 */
    public CheckClientSubmissionProcessor(ClientDraftRepository drafts, ClientDraftAccountClient accounts,
        ClientSubmissionPreparer preparer) {
        this.drafts = drafts;
        this.accounts = accounts;
        this.preparer = preparer;
    }

    @Override
    protected ClientSubmissionPreparation processRequest(CheckClientSubmissionRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        ClientDraftIdentity identity = ClientDraftPolicy.requireValid(
            drafts.identity(request.getApplicationId(), request.getAccountId(), false));
        if (!ClientDraftPolicy.isEditable(identity)) {
            throw new FailException(APPLICATION_NOT_EDITABLE);
        }
        var preparation = preparer.prepare(request.getAccountId(), identity);
        accounts.authorize(request.getAccountId(), request.getSessionId());
        return preparation;
    }
}
