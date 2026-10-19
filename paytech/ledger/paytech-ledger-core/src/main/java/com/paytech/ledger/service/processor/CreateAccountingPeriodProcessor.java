package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.service.AccountingPeriodService;
import com.paytech.ledger.service.processor.request.CreateAccountingPeriodRequest;
import com.paytech.ledger.service.processor.response.CreatedResourceResponse;

import lombok.AllArgsConstructor;

/**
 * 在财务日历下创建 FUTURE 期间
 * <li>同一日历的期间创建按日历资源锁串行；日期不重叠由服务内日历行锁哨兵最终保证。</li>
 * <li>年度与期号唯一由唯一键保护。</li>
 */
@Component
@AllArgsConstructor
public class CreateAccountingPeriodProcessor
    extends AbstractLockProcessor<CreateAccountingPeriodRequest, CreatedResourceResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountingPeriodService accountingPeriodService;
    private final FiscalCalendarRepository fiscalCalendarRepository;

    /** 仅依赖输入、首次与重放通用的附加校验。 */
    @Override
    protected void validate(CreateAccountingPeriodRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new InvalidParameterException("期间开始日期不能晚于结束日期: " + request.getStartDate() + " / "
                + request.getEndDate());
        }
        if (request.getPeriodNo() < 1) {
            throw new InvalidParameterException("期号必须为正整数: " + request.getPeriodNo());
        }
    }

    /** 与同一资源的其他写入口共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(CreateAccountingPeriodRequest request) {
        return LockCommand.tryLock(AccountingLocks.fiscalCalendar(request.getFiscalCalendarId()));
    }

    @Override
    protected CreatedResourceResponse processWithinLock(CreateAccountingPeriodRequest request) {
        if (fiscalCalendarRepository.findById(request.getFiscalCalendarId()) == null) {
            throw new FailException(LedgerSetupErrorCode.FISCAL_CALENDAR_NOT_FOUND);
        }
        AccountingPeriod period = buildPeriod(request);
        accountingPeriodService.createAccountingPeriod(period);
        CreatedResourceResponse response = new CreatedResourceResponse();
        response.setId(period.getId());
        return response;
    }

    /** 构建新的 FUTURE 期间。 */
    AccountingPeriod buildPeriod(CreateAccountingPeriodRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountingPeriod period = new AccountingPeriod();
        period.setId(idGenerator.nextId());
        period.setCalendarId(request.getFiscalCalendarId());
        period.setFiscalYear(request.getFiscalYear());
        period.setPeriodNo(request.getPeriodNo());
        period.setStartDate(request.getStartDate());
        period.setEndDate(request.getEndDate());
        period.setStatus(PeriodStatus.FUTURE);
        period.setReopenCount(0);
        period.setVersion(0L);
        period.setCreateTime(now);
        period.setUpdateTime(now);
        return period;
    }
}
