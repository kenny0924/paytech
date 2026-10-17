package com.paytech.ledger.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domain.TrialBalanceContext;
import com.paytech.ledger.domain.view.TrialBalanceRunView;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.service.TrialBalanceService;
import com.paytech.ledger.service.processor.request.RunTrialBalanceRequest;

import lombok.AllArgsConstructor;

/**
 * 对一个账簿和会计期间执行试算平衡
 * <li>同一账簿期间的试算由 Redis 资源锁串行，序号唯一键兜底；事务前校验账簿、期间归属与期间非 FUTURE。</li>
 * <li>试算同步执行并直接写入终态 SUCCEEDED/FAILED，不落中间状态。</li>
 */
@Component
@AllArgsConstructor
public class RunTrialBalanceProcessor extends AbstractLockProcessor<RunTrialBalanceRequest, TrialBalanceRunView> {
    private final TrialBalanceService trialBalanceService;
    private final CurrencySpecRepository currencySpecRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;

    /** 同一账簿期间的试算共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(RunTrialBalanceRequest request) {
        return LockCommand.tryLock(AccountingLocks.trialBalance(request.getLedgerBookId(), request.getPeriodId()));
    }

    @Override
    protected TrialBalanceRunView processWithinLock(RunTrialBalanceRequest request) {
        LedgerBook book = trialBalanceService.getBook(request.getLedgerBookId());
        AccountingPeriod period = trialBalanceService.getPeriod(request.getPeriodId());
        checkPeriod(book, period);
        int minorUnit = currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
        TrialBalanceContext context = new TrialBalanceContext(book, period, minorUnit);
        trialBalanceService.run(context);
        return response(context);
    }

    /** 期间必须属于账簿主体的财务日历，且不能是 FUTURE。 */
    void checkPeriod(LedgerBook book, AccountingPeriod period) {
        FiscalCalendar calendar = fiscalCalendarRepository.findById(period.getCalendarId());
        if (calendar == null || calendar.getEntityType() != book.getEntityType()
            || !calendar.getEntityId().equals(book.getEntityId())) {
            throw new BizCheckFailException("会计期间不属于账簿主体: 期间 " + period.getId() + ", 账簿 " + book.getId());
        }
        if (period.getStatus() == PeriodStatus.FUTURE) {
            throw new BizCheckFailException("会计期间未开放，不能试算: " + period.getId());
        }
    }

    /** 从本次试算结果构造摘要。 */
    TrialBalanceRunView response(TrialBalanceContext context) {
        TrialBalanceRun run = context.getRun();
        TrialBalanceRunView view = new TrialBalanceRunView();
        view.setTrialBalanceRunId(run.getId());
        view.setLedgerBookId(run.getLedgerBookId());
        view.setPeriodId(run.getPeriodId());
        view.setRunNo(run.getRunNo());
        view.setAsOfSequence(run.getAsOfSequence());
        view.setStatus(run.getStatus());
        view.setTotalDebit(run.getTotalDebit());
        view.setTotalCredit(run.getTotalCredit());
        view.setContentHash(run.getContentHash());
        view.setLineCount(context.getLines().size());
        view.setExceptionCount(context.getExceptions().size());
        view.setCreateTime(run.getCreateTime());
        return view;
    }
}
