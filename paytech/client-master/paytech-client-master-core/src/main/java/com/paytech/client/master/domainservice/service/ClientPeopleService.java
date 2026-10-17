package com.paytech.client.master.domainservice.service;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.domain.onboarding.*;
public interface ClientPeopleService {
    PeopleCommandReceiptResponseBody createPerson(ClientDraftCommand command,long applicationId,ClientDraftPatch person,ClientRelationValues relation);
    /**
     * 保存人员资料；usage 为事务外取得的合同引用结果，被引用或无法确认时拒绝为 PERSON_NOT_EDITABLE。
     */
    PeopleCommandReceiptResponseBody savePerson(ClientDraftCommand command,long applicationId,long partyId,ClientDraftPatch person,
        ClientAgreementPartyUsage usage);
    PeopleCommandReceiptResponseBody createRelation(ClientDraftCommand command,long applicationId,long partyId,ClientRelationValues relation);
    PeopleCommandReceiptResponseBody saveRelation(ClientDraftCommand command,long applicationId,long relationId,Map<String,String> patch);
    void removeRelation(ClientDraftCommand command,long applicationId,long relationId);
}
