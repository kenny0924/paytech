package com.paytech.client.master.domainservice.service.impl;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.PartyRelationStatus;
import com.paytech.client.master.domain.onboarding.ClientDraftPolicy;
import com.paytech.client.master.domain.onboarding.ClientFileTarget;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.service.ClientFileTargetService;
import com.paytech.foundation.common.error.FailException;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.PERSON_NOT_FOUND;

/** 原申请及人员关系使用同一只读快照，避免混合提交时点产生错误的上传授权。 */
public final class ClientFileTargetServiceImpl implements ClientFileTargetService {
    private final TransactionTemplate reads;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final com.paytech.client.master.domainservice.repository.ClientDocumentRepository documents;

    /** 接收仅访问 CCT 本库的 Repository 和 REPEATABLE_READ 只读事务。 */
    public ClientFileTargetServiceImpl(TransactionTemplate reads, ClientDraftRepository drafts,
                                       ClientPeopleRepository people,com.paytech.client.master.domainservice.repository.ClientDocumentRepository documents) {
        this.reads = reads;
        this.drafts = drafts;
        this.people = people;this.documents=documents;
    }

    /** 查询不会调用 Platform、Vault、Redis，也不修改任何申请、资料或关系。 */
    @Override
    public ClientFileTarget read(long accountId, long applicationId, long subjectPartyId) {
        return reads.execute(transaction -> target(accountId, applicationId, subjectPartyId));
    }

    /** 在同一快照内确认本人申请，主主体与相关人员分别核对。 */
    private ClientFileTarget target(long accountId, long applicationId, long subjectPartyId) {
        var application = ClientDraftPolicy.requireValid(drafts.identity(applicationId, accountId, false));
        boolean currentSubject = application.partyId() == subjectPartyId
            || relatedSubject(application, subjectPartyId);
        return new ClientFileTarget(applicationId, application.clientId(), subjectPartyId,
            application.clientType(), ClientDraftPolicy.isEditable(application) && currentSubject, true);
    }

    /** 原草稿人员仍可读；只有本申请的未结束关系才允许继续向该人员上传材料。 */
    private boolean relatedSubject(ClientDraftIdentity application, long subjectPartyId) {
        if (application.clientType()!=ClientType.ORGANIZATION) throw new FailException(PERSON_NOT_FOUND);
        if (people.person(application,subjectPartyId,false)==null) {
            if (documents.retainedSubject(application.applicationId(),subjectPartyId)) return false;
            throw new FailException(PERSON_NOT_FOUND);
        }
        return people.relations(application).stream().anyMatch(relation ->
            relation.relatedPartyId() == subjectPartyId
                && (relation.status() == PartyRelationStatus.DRAFT || relation.status() == PartyRelationStatus.ACTIVE));
    }
}
