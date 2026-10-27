package com.paytech.client.master.domainservice.service;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.PeopleCommandReceiptResponseBody;
import com.paytech.client.master.domain.onboarding.*;
/** 组织申请的关联人员与关系；事务由 Processor 包裹，标识与属性已在事务外准备。 */
public interface ClientPeopleService {
    /** C07 新建人员及其草稿关系；关系类型按申请辖区的关系类型目录校验。 */
    PeopleCommandReceiptResponseBody createPerson(ClientDraftCommand command,long applicationId,ClientDraftChanges person,ClientRelationValues relation);
    /**
     * 保存人员资料；usage 为事务外取得的合同引用结果，被引用或无法确认时拒绝为 PERSON_NOT_EDITABLE。
     */
    PeopleCommandReceiptResponseBody savePerson(ClientDraftCommand command,long applicationId,long partyId,ClientDraftChanges person,
        ClientAgreementPartyUsage usage);
    PeopleCommandReceiptResponseBody createRelation(ClientDraftCommand command,long applicationId,long partyId,ClientRelationValues relation);
    PeopleCommandReceiptResponseBody saveRelation(ClientDraftCommand command,long applicationId,long relationId,Map<String,String> patch);
    void removeRelation(ClientDraftCommand command,long applicationId,long relationId);
}
