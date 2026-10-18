package com.paytech.commercial.domainservice.service;

import java.util.Collection;

import com.paytech.commercial.domain.AgreementPartyUsage;

/** 合同参与方引用查询能力（供 client-master 人员共享标记）；只读本库，不开事务。 */
public interface AgreementPartyQueryService {
    /**
     * 返回被任一合同参与方引用的 Party 与资料。
     *
     * @param partyIds 待查 Party ID
     * @param partyProfileIds 待查资料 ID
     * @return 被引用的子集
     */
    AgreementPartyUsage referenced(Collection<Long> partyIds, Collection<Long> partyProfileIds);
}
