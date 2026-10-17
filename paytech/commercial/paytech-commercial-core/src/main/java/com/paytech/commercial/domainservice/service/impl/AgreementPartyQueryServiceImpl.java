package com.paytech.commercial.domainservice.service.impl;

import java.util.Collection;

import com.paytech.commercial.domain.AgreementPartyUsage;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.service.AgreementPartyQueryService;

import lombok.AllArgsConstructor;

/** 两条按主键集合的独立查询，允许观察相邻提交；调用方失败关闭，不需要一致快照。 */
@AllArgsConstructor
public class AgreementPartyQueryServiceImpl implements AgreementPartyQueryService {
    private final AgreementPartyRepository repository;

    /** 不区分合同状态，任一引用即返回。 */
    @Override
    public AgreementPartyUsage referenced(Collection<Long> partyIds, Collection<Long> partyProfileIds) {
        return new AgreementPartyUsage(repository.findReferencedPartyIds(partyIds),
            repository.findReferencedPartyProfileIds(partyProfileIds));
    }
}
