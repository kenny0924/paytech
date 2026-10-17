package com.paytech.client.master.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.ClientApplicationSubmission;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.core.dal.dataobject.PartyProfileIdentifier;
import com.paytech.client.master.domain.enums.PartyRelationStatus;
import com.paytech.client.master.domain.onboarding.ClientReviewSourceView;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientDocumentRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.client.master.domainservice.service.ClientReviewRegistrationService;

import lombok.AllArgsConstructor;

/** 审核登记的提交事实读取与审核中状态保存。 */
@AllArgsConstructor
public class ClientReviewRegistrationServiceImpl implements ClientReviewRegistrationService {
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ClientDraftRepository drafts;
    private final ClientPeopleRepository people;
    private final ClientDocumentRepository documents;
    private final ClientApplicationRepository applications;
    private final PartyProfileIdentifierRepository identifiers;

    /** 只读组合提交事实；SUBMITTED 后资料只读，关系和人员只取 DRAFT/ACTIVE 范围。 */
    @Override
    public ClientReviewSourceView reviewSource(String submissionId) {
        ClientApplicationSubmission application = applications.findBySubmission(submissionId);
        if (application == null) {
            return null;
        }
        ClientDraftIdentity identity = drafts.identity(application.getApplicationId(), application.getAccountId(), false);
        var relations = people.relations(identity).stream()
            .filter(relation -> relation.status() == PartyRelationStatus.DRAFT || relation.status() == PartyRelationStatus.ACTIVE)
            .toList();
        var persons = people.people(identity).stream()
            .filter(person -> relations.stream().anyMatch(relation -> relation.relatedPartyId() == person.identity().partyId()))
            .toList();
        var bindings = new HashMap<Long, PartyProfileIdentifier>();
        bindings.put(identity.profileId(), identifiers.findByProfile(identity.profileId()));
        persons.forEach(person -> bindings.put(person.identity().profileId(), identifiers.findByProfile(person.identity().profileId())));
        return new ClientReviewSourceView(application, identity, drafts.fields(identity), persons, relations,
            documents.documents(application.getApplicationId()), bindings);
    }

    /** 同一提交进入审核中。 */
    @Override
    public boolean startReview(ClientApplicationSubmission application, long caseId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return Boolean.TRUE.equals(transactions.execute(status -> applications.startReview(application, caseId, now)));
    }
}
