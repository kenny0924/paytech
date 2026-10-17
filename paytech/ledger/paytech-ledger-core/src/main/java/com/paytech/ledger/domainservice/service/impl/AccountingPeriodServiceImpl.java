package com.paytech.ledger.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.service.AccountingPeriodService;

import lombok.AllArgsConstructor;

/** 财务日历与会计期间的本地保存。 */
@Service
@AllArgsConstructor
public class AccountingPeriodServiceImpl implements AccountingPeriodService {
    private final TransactionTemplate transactionTemplate;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;

    /** 单行插入，主体唯一键保护重复。 */
    @Override
    public void createFiscalCalendar(FiscalCalendar calendar) {
        fiscalCalendarRepository.insert(calendar);
    }

    /** 锁日历 → 当前读检查重叠 → 插入，三步同一事务。 */
    @Override
    public void createAccountingPeriod(AccountingPeriod period) {
        transactionTemplate.executeWithoutResult(status -> {
            if (fiscalCalendarRepository.lockById(period.getCalendarId()) == null) {
                throw new ErrorException("财务日历在创建期间时消失: " + period.getCalendarId());
            }
            int overlapping = accountingPeriodRepository.countOverlapping(period.getCalendarId(),
                period.getStartDate(), period.getEndDate());
            if (overlapping > 0) {
                throw new BizCheckFailException("会计期间日期与已有期间重叠: 日历 " + period.getCalendarId() + ", "
                    + period.getStartDate() + " 至 " + period.getEndDate());
            }
            accountingPeriodRepository.insert(period);
        });
    }

    /** 状态与版本由条件更新保护。 */
    @Override
    public void openAccountingPeriod(AccountingPeriod period) {
        accountingPeriodRepository.updateStatus(period, PeriodStatus.OPEN);
    }
}
