package com.paytech.payout.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.payout.core.dal.dataobject.BeneficiaryAccountEvent;
import com.paytech.payout.core.dal.mapper.BeneficiaryAccountEventMapper;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountEventRepository;

import lombok.AllArgsConstructor;

/** BeneficiaryAccountEventRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BeneficiaryAccountEventRepositoryImpl implements BeneficiaryAccountEventRepository {
    private final BeneficiaryAccountEventMapper mapper;

    /** 追加一条事件。 */
    @Override
    public void insert(BeneficiaryAccountEvent record) {
        mapper.insert(record);
    }

    /** 当前最大序号 + 1。 */
    @Override
    public int nextEventNo(Long beneficiaryAccountId) {
        return mapper.selectMaxEventNo(beneficiaryAccountId) + 1;
    }

    /** 账户的全部事件。 */
    @Override
    public List<BeneficiaryAccountEvent> listByAccount(Long beneficiaryAccountId) {
        return mapper.selectByAccount(beneficiaryAccountId);
    }

    /** 账户内的一条事件。 */
    @Override
    public BeneficiaryAccountEvent findByEventNo(Long beneficiaryAccountId, Integer eventNo) {
        return mapper.selectByEventNo(beneficiaryAccountId, eventNo);
    }
}
