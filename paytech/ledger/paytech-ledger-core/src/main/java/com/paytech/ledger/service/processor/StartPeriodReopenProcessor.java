package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.CloseBookStatus;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.core.enums.PeriodReopenReason;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domain.PeriodReopenContext;
import com.paytech.ledger.domain.view.PeriodCloseView;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.PeriodCloseBookRepository;
import com.paytech.ledger.domainservice.repository.PeriodCloseRepository;
import com.paytech.ledger.domainservice.service.PeriodCloseService;
import com.paytech.ledger.service.processor.request.StartPeriodReopenRequest;

import lombok.AllArgsConstructor;

/**
 * 发起期间重开：冻结冲回计划与重开摘要，进入 PENDING_CHECK，不过账任何分录
 * <li>事务前：期间存在且 CLOSED；同日历中开始晚于本期的期间均不处于 CLOSED/CLOSING/REOPENING（只能重开最近关闭期间）；
 * 原关账为本期间序号最大的 CLOSE 流程且必须 COMPLETED。</li>
 * <li>冲回计划按原关账每个账簿结转指令的行逐行借贷互换、金额不变；原关账无结转指令的账簿没有冲回行。</li>
 * <li>一个事务内完成：期间 CLOSED → REOPENING、流程登记并占用活动槽、按原关账账簿冻结水位、3 个步骤（1—2 成功、3 待审批执行）、
 * 冻结 {@code CODE-MOR:FINANCE:PERIOD_REOPEN:1} 摘要与 Maker 记录。</li>
 * <li>与关账发起、审批、驳回共用期间资源锁；期间行锁、活动槽唯一键与状态 CAS 仍是最终防线。</li>
 */
@Component
@AllArgsConstructor
public class StartPeriodReopenProcessor extends AbstractLockProcessor<StartPeriodReopenRequest, PeriodCloseView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PeriodCloseService periodCloseService;
    private final PeriodCloseRepository periodCloseRepository;
    private final PeriodCloseBookRepository periodCloseBookRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;

    /** 与同一期间的关账发起、审批、驳回共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(StartPeriodReopenRequest request) {
        return LockCommand.tryLock(AccountingLocks.periodClose(request.getPeriodId()));
    }

    /** 重开原因必须属于目录。 */
    @Override
    protected void validate(StartPeriodReopenRequest request) {
        if (!PeriodReopenReason.isValid(request.getReasonCode())) {
            throw new InvalidParameterException("重开原因代码不在目录内: " + request.getReasonCode());
        }
    }

    @Override
    protected PeriodCloseView processWithinLock(StartPeriodReopenRequest request) {
        // 校验期间、重开顺序与原关账
        AccountingPeriod period = getClosedPeriod(request.getPeriodId());
        checkNoLaterClosedPeriod(period);
        PeriodClose reopensClose = getCompletedClose(period);

        // 规划冲回并发起重开
        PeriodReopenContext context = buildContext(period, reopensClose, request);
        periodCloseService.startReopen(context, buildMakerApproval(context.getClose()));
        return periodCloseService.getClose(period.getId(), context.getClose().getCloseNo()).getSummary();
    }

    /** 期间必须存在且 CLOSED。 */
    AccountingPeriod getClosedPeriod(long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId);
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + periodId);
        }
        if (period.getStatus() != PeriodStatus.CLOSED) {
            throw new BizCheckFailException("只有 CLOSED 期间可以发起重开: " + periodId + ", 状态 "
                + period.getStatus().getCode());
        }
        return period;
    }

    /** 只能重开最近关闭期间：同日历中更晚的期间不得已关闭、关账中或重开中。 */
    void checkNoLaterClosedPeriod(AccountingPeriod period) {
        AccountingPeriod later = accountingPeriodRepository.findFirstClosedAfter(period.getCalendarId(),
            period.getStartDate());
        if (later != null) {
            throw new BizCheckFailException("只能重开最近关闭的会计期间: " + period.getId() + ", 更晚期间 " + later.getId()
                + " 状态 " + later.getStatus().getCode());
        }
    }

    /** 原关账：本期间序号最大的 CLOSE 流程，必须已 COMPLETED。 */
    PeriodClose getCompletedClose(AccountingPeriod period) {
        PeriodClose close = periodCloseRepository.findLatestByType(period.getId(), PeriodCloseType.CLOSE);
        if (close == null || close.getStatus() != PeriodCloseStatus.COMPLETED) {
            throw new BizCheckFailException("会计期间没有已完成的关账流程，不能重开: " + period.getId());
        }
        return close;
    }

    /** 以原关账的账簿范围构造重开流程根、账簿证据、3 个步骤与冲回计划。 */
    PeriodReopenContext buildContext(AccountingPeriod period, PeriodClose reopensClose, StartPeriodReopenRequest request) {
        Instant now = now();
        PeriodClose close = buildClose(period, reopensClose, request, now);
        List<PeriodCloseBook> closeBooks = periodCloseBookRepository.findByClose(reopensClose.getId()).stream()
            .map(original -> buildCloseBook(close, original.getLedgerBookId(), now)).toList();
        List<CloseStepType> types = CloseStepType.reopenSteps();
        List<CloseStep> steps = types.stream().map(type -> buildStep(close, types.indexOf(type) + 1, type, now)).toList();
        Map<Long, List<InstructionLine>> reversalLines = periodCloseService.planReopenReversal(reopensClose.getId());
        return new PeriodReopenContext(period, reopensClose, close, closeBooks, steps, reversalLines);
    }

    /** RUNNING 重开流程根，占用活动槽并引用原关账；序号在发起事务内分配。 */
    PeriodClose buildClose(AccountingPeriod period, PeriodClose reopensClose, StartPeriodReopenRequest request, Instant now) {
        PeriodClose close = new PeriodClose();
        close.setId(idGenerator.nextId());
        close.setPeriodId(period.getId());
        close.setCloseType(PeriodCloseType.REOPEN);
        close.setReopensCloseId(reopensClose.getId());
        close.setReopenReasonCode(request.getReasonCode());
        close.setReopenRequestRef(request.getRequestRef());
        close.setStatus(PeriodCloseStatus.RUNNING);
        close.setActiveSlot(1);
        close.setMakerId(request.getMakerId());
        close.setVersion(0L);
        close.setCreateTime(now);
        close.setUpdateTime(now);
        return close;
    }

    /** LOCKED 账簿证据；水位在发起事务内冻结。 */
    PeriodCloseBook buildCloseBook(PeriodClose close, long ledgerBookId, Instant now) {
        PeriodCloseBook closeBook = new PeriodCloseBook();
        closeBook.setId(idGenerator.nextId());
        closeBook.setPeriodCloseId(close.getId());
        closeBook.setLedgerBookId(ledgerBookId);
        closeBook.setAsOfSequence(0L);
        closeBook.setStatus(CloseBookStatus.LOCKED);
        closeBook.setCreateTime(now);
        closeBook.setUpdateTime(now);
        return closeBook;
    }

    /** PENDING 步骤。 */
    CloseStep buildStep(PeriodClose close, int stepNo, CloseStepType type, Instant now) {
        CloseStep step = new CloseStep();
        step.setId(idGenerator.nextId());
        step.setPeriodCloseId(close.getId());
        step.setStepNo(stepNo);
        step.setStepType(type);
        step.setStatus(CloseStepStatus.PENDING);
        step.setCreateTime(now);
        step.setUpdateTime(now);
        return step;
    }

    /** Maker 批准记录；确认的摘要在发起事务内冻结后写入。 */
    CloseApproval buildMakerApproval(PeriodClose close) {
        Instant now = now();
        CloseApproval maker = new CloseApproval();
        maker.setId(idGenerator.nextId());
        maker.setPeriodCloseId(close.getId());
        maker.setApprovalRole(ApprovalRole.MAKER);
        maker.setActorId(close.getMakerId());
        maker.setDecision(ApprovalDecision.APPROVED);
        maker.setDecisionTime(now);
        maker.setCreateTime(now);
        return maker;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
