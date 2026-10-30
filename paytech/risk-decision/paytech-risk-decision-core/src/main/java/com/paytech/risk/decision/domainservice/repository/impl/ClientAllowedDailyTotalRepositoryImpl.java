package com.paytech.risk.decision.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedCurrencyTotal;
import com.paytech.risk.decision.core.dal.dataobject.ClientAllowedDailyTotal;
import com.paytech.risk.decision.core.dal.mapper.ClientAllowedDailyTotalMapper;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domainservice.repository.ClientAllowedDailyTotalRepository;

import lombok.AllArgsConstructor;

/** Client 日放行汇总持久化。 */
@Repository
@AllArgsConstructor
public class ClientAllowedDailyTotalRepositoryImpl implements ClientAllowedDailyTotalRepository {
    private final ClientAllowedDailyTotalMapper mapper;

    /** 逐行累加；插入返回 1、累加到已有行返回 2（MySQL 约定），其他行数说明语句未生效。 */
    @Override
    public void addAll(List<ClientAllowedDailyTotal> increments) {
        for (ClientAllowedDailyTotal increment : increments) {
            int rows = mapper.upsertAdd(increment);
            if (rows != 1 && rows != 2) {
                throw new ErrorException("日放行汇总未写入: client=" + increment.getClientId() + ", rows=" + rows);
            }
        }
    }

    /** 合计区间内同币种放行金额。 */
    @Override
    public BigDecimal sumAmount(long clientId, VelocityEventCode eventCode, String currency, LocalDate fromDate,
        LocalDate toDate) {
        return mapper.sumAmount(clientId, eventCode, currency, fromDate, toDate);
    }

    /** 按币种合计当日与近 30 日放行金额。 */
    @Override
    public List<ClientAllowedCurrencyTotal> totalsByCurrency(long clientId, VelocityEventCode eventCode, LocalDate today,
        LocalDate fromDate) {
        return mapper.totalsByCurrency(clientId, eventCode, today, fromDate);
    }
}
