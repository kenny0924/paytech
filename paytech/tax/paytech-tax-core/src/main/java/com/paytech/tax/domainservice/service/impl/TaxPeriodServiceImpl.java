package com.paytech.tax.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.tax.core.dal.dataobject.TaxPeriod;
import com.paytech.tax.core.dal.dataobject.TaxRegistration;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.TaxPeriodStatus;
import com.paytech.tax.domain.policy.TaxPolicyRules;
import com.paytech.tax.domainservice.repository.TaxPeriodRepository;
import com.paytech.tax.domainservice.service.TaxPeriodService;

import lombok.AllArgsConstructor;

/**
 * TaxPeriodService 实现
 * <li>MONTHLY 为日历月，QUARTERLY 为日历季度（1–3、4–6、7–9、10–12 月）；日期已是注册法定时区的本地日期。</li>
 * <li>截止日：PERIOD_END_PLUS_DAYS 为期末 + dueDays，END_OF_NEXT_MONTH 为期末次月最后一日；缴款截止日本期与申报截止日相同。</li>
 * <li>单条 INSERT … ON DUPLICATE KEY 不需要显式事务；并发生成同一期间时只保留先提交的一行，随后按业务身份重读。</li>
 */
@Service
@AllArgsConstructor
public class TaxPeriodServiceImpl implements TaxPeriodService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TaxPeriodRepository taxPeriodRepository;

    /** 已有期间直接返回；没有则生成后重读。 */
    @Override
    public TaxPeriod ensurePeriod(TaxRegistration registration, TaxPolicyRules rules, LocalDate taxPointDate) {
        LocalDate start = periodStart(registration.getFilingFrequency(), taxPointDate);
        LocalDate end = periodEnd(registration.getFilingFrequency(), start);
        TaxPeriod existing = taxPeriodRepository.findByRange(registration.getId(), start, end);
        if (existing != null) {
            return existing;
        }
        taxPeriodRepository.insertIfAbsent(newPeriod(registration, start, end, dueDate(rules, end)));
        TaxPeriod period = taxPeriodRepository.findByRange(registration.getId(), start, end);
        if (period == null) {
            throw new ErrorException("申报期间生成后未读到: 注册 " + registration.getId() + ", " + start);
        }
        return period;
    }

    /** 新期间：OPEN，申报币种取注册。 */
    TaxPeriod newPeriod(TaxRegistration registration, LocalDate start, LocalDate end, LocalDate due) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TaxPeriod period = new TaxPeriod();
        period.setId(idGenerator.nextId());
        period.setRegistrationId(registration.getId());
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        period.setFilingDueDate(due);
        period.setPaymentDueDate(due);
        period.setReportingCurrency(registration.getReportingCurrency());
        period.setStatus(TaxPeriodStatus.OPEN);
        period.setCreateTime(now);
        period.setUpdateTime(now);
        return period;
    }

    /** 期间开始日。 */
    static LocalDate periodStart(FilingFrequency frequency, LocalDate date) {
        if (frequency == FilingFrequency.QUARTERLY) {
            int firstMonth = (date.getMonthValue() - 1) / 3 * 3 + 1;
            return LocalDate.of(date.getYear(), firstMonth, 1);
        }
        return date.withDayOfMonth(1);
    }

    /** 期间结束日（含）。 */
    static LocalDate periodEnd(FilingFrequency frequency, LocalDate start) {
        return start.plusMonths(frequency == FilingFrequency.QUARTERLY ? 3 : 1).minusDays(1);
    }

    /** 申报截止日。 */
    static LocalDate dueDate(TaxPolicyRules rules, LocalDate periodEnd) {
        if ("PERIOD_END_PLUS_DAYS".equals(rules.getDueDateRule())) {
            return periodEnd.plusDays(rules.getDueDays());
        }
        return periodEnd.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
    }
}
