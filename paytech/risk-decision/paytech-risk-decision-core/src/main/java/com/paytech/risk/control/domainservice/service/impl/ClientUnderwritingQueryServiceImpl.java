package com.paytech.risk.control.domainservice.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.domain.underwriting.EffectiveUnderwriting;
import com.paytech.risk.control.domainservice.repository.ClientUnderwritingRepository;
import com.paytech.risk.control.domainservice.service.ClientUnderwritingQueryService;

import lombok.AllArgsConstructor;

/** 有效承保结论读取。 */
@Service
@AllArgsConstructor
public class ClientUnderwritingQueryServiceImpl implements ClientUnderwritingQueryService {
    private final ClientUnderwritingRepository underwritings;

    /** 读取有效槽位版本；APPROVED 必有观察期，缺失属于数据不变量被破坏。 */
    @Override
    public Optional<EffectiveUnderwriting> findEffective(long clientId) {
        return underwritings.findEffective(clientId).map(ClientUnderwritingQueryServiceImpl::effective);
    }

    /** 版本行转换为结论摘要。 */
    static EffectiveUnderwriting effective(ClientUnderwriting row) {
        if (row.getObservationDays() == null || row.getObservationTransactions() == null || row.getDecidedTime() == null) {
            throw new ErrorException("有效承保结论缺少观察期或决定时间: " + row.getUnderwritingId());
        }
        return new EffectiveUnderwriting(row.getUnderwritingId(), row.getVersionNo(), row.getObservationDays(),
            row.getObservationTransactions(), row.isRequireDeposit(), row.getDepositAmount(), row.getDepositCurrency(),
            row.isDepositBlocksCollection(), row.getSuggestedSettlementDays(), row.isHighRiskMcc(), row.getDecidedTime());
    }
}
