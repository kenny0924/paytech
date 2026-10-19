package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.mapper.AgreementPartyMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementPartyMapper 读取 t_agreement_party。 */
@AllArgsConstructor
public class AgreementPartyRepositoryImpl implements AgreementPartyRepository {
    private final AgreementPartyMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.AgreementParty> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 被任一合同参与方引用的 Party ID；空集合不访问数据库。 */
    @Override
    public java.util.Set<Long> findReferencedPartyIds(java.util.Collection<Long> partyIds) {
        if (partyIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.TreeSet<>(mapper.findReferencedPartyIds(partyIds));
    }

    /** 被任一合同参与方引用的资料 ID；空集合不访问数据库。 */
    @Override
    public java.util.Set<Long> findReferencedPartyProfileIds(java.util.Collection<Long> partyProfileIds) {
        if (partyProfileIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.TreeSet<>(mapper.findReferencedPartyProfileIds(partyProfileIds));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<AgreementParty> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (AgreementParty row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("合同参与方插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementParty findById(long agreementPartyId) {
        return mapper.selectById(agreementPartyId);
    }

    /** 按唯一键（合同、Party、角色）读取。 */
    @Override
    public AgreementParty findByUniqueKey(long agreementId, long partyId, AgreementPartyPartyRole partyRole) {
        return mapper.selectByUniqueKey(agreementId, partyId, partyRole);
    }

    /** 读取一份合同的全部 Party 角色。 */
    @Override
    public List<AgreementParty> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }

    /** 按合同接受事件编号读取已登记的角色行，用于同引用重放判断。 */
    @Override
    public List<AgreementParty> findByAcceptanceReference(String acceptanceReference) {
        return List.copyOf(mapper.selectByAcceptanceReference(acceptanceReference));
    }

    /** 一次性登记，成功后同步原对象。 */
    @Override
    public void recordAcceptance(AgreementParty party, AgreementParty acceptance, Instant now) {
        acceptance.setAcceptedTime(micros(acceptance.getAcceptedTime()));
        acceptance.setAcceptanceRecordedAt(micros(acceptance.getAcceptanceRecordedAt()));
        Instant time = micros(now);
        if (mapper.updateAcceptance(party, acceptance, time) != 1) {
            throw new ConcurrentRequestException("合同参与方已登记签署: " + party.getAgreementPartyId());
        }
        party.setAcceptanceMethod(acceptance.getAcceptanceMethod());
        party.setAcceptedByActorId(acceptance.getAcceptedByActorId());
        party.setAcceptedTime(acceptance.getAcceptedTime());
        party.setEvidenceDocumentId(acceptance.getEvidenceDocumentId());
        party.setAcceptanceReference(acceptance.getAcceptanceReference());
        party.setAcceptanceRefSlot(acceptance.getAcceptanceRefSlot());
        party.setAcceptanceRecordedBy(acceptance.getAcceptanceRecordedBy());
        party.setAcceptanceRecordedAt(acceptance.getAcceptanceRecordedAt());
        party.setUpdateTime(time);
    }
}
