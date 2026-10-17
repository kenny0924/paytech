package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.domain.JournalPostingContext;
import com.paytech.ledger.domain.PeriodCloseContentHash;
import com.paytech.ledger.domain.PeriodCloseContext;
import com.paytech.ledger.domain.PeriodReopenContentHash;
import com.paytech.ledger.domain.PeriodReopenContext;
import com.paytech.ledger.domain.view.PeriodCloseView;
import com.paytech.ledger.domainservice.repository.PeriodCloseRepository;
import com.paytech.ledger.domainservice.service.JournalPostingService;
import com.paytech.ledger.domainservice.service.PeriodCloseService;
import com.paytech.ledger.service.processor.request.ReviewPeriodCloseRequest;

import lombok.AllArgsConstructor;

/**
 * 批准关账或重开流程，按流程类型分派
 * <li>CLOSE：复验账簿证据后流程 COMPLETED、释放活动槽、期间 CLOSING → CLOSED。</li>
 * <li>REOPEN：事务前重算冲回计划并与冻结摘要比对；一个事务内锁期间并复验 REOPENING，按账簿主键升序为有冲回行的账簿生成
 * AUTO 指令（来源 FINANCE / PERIOD_REOPEN:{重开ID}:{账簿ID}，类型 PERIOD_PROFIT_REOPEN，无规则 Bundle）并以 REOPENING 分录
 * 在期末日过账，账簿证据 FINALIZED、步骤 3 成功、流程 COMPLETED、期间 REOPENING → OPEN 且重开次数加一。</li>
 * <li>与发起关账、发起重开共用期间资源锁；流程状态 CAS 与期间行锁仍是最终防线。</li>
 */
@Component
@AllArgsConstructor
public class ApprovePeriodCloseProcessor extends AbstractLockProcessor<ReviewPeriodCloseRequest, PeriodCloseView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PeriodCloseService periodCloseService;
    private final TransactionTemplate transactionTemplate;
    private final JournalPostingService journalPostingService;
    private final PeriodCloseRepository periodCloseRepository;

    /** 与同一期间的发起关账、发起重开共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ReviewPeriodCloseRequest request) {
        return LockCommand.tryLock(AccountingLocks.periodClose(request.getPeriodId()));
    }

    @Override
    protected PeriodCloseView processWithinLock(ReviewPeriodCloseRequest request) {
        PeriodCloseContext context = periodCloseService.loadForReview(request.getPeriodId(), request.getCloseNo());
        checkReviewable(context.getClose(), request);
        CloseApproval approval = buildApproval(context.getClose(), request.getCheckerId());
        if (context.getClose().getCloseType() == PeriodCloseType.REOPEN) {
            approveReopen(buildReopenContext(context), approval);
        } else {
            periodCloseService.approve(context, approval);
        }
        return periodCloseService.getClose(request.getPeriodId(), request.getCloseNo()).getSummary();
    }

    /** 只能复核 PENDING_CHECK 流程；复核人不同于编制人；确认的摘要等于关账摘要。 */
    void checkReviewable(PeriodClose close, ReviewPeriodCloseRequest request) {
        if (close.getStatus() != PeriodCloseStatus.PENDING_CHECK) {
            throw new BizCheckFailException("关账流程不在待审批状态: " + close.getId() + ", 状态 " + close.getStatus().getCode());
        }
        if (close.getMakerId().equals(request.getCheckerId())) {
            throw new BizCheckFailException("复核人不能与编制人相同: 关账 " + close.getId() + ", 用户 " + request.getCheckerId());
        }
        if (!close.getCloseHash().equals(request.getApprovedCloseHash())) {
            throw new BizCheckFailException("复核确认的关账摘要与流程不一致: " + close.getId());
        }
    }

    /** 读取原关账并重算冲回计划；重算摘要必须等于冻结摘要。 */
    PeriodReopenContext buildReopenContext(PeriodCloseContext context) {
        PeriodClose close = context.getClose();
        PeriodClose reopensClose = periodCloseRepository.findById(close.getReopensCloseId());
        if (reopensClose == null) {
            throw new ErrorException("重开引用的原关账流程不存在: " + close.getId() + ", 原关账 " + close.getReopensCloseId());
        }
        PeriodReopenContext reopen = new PeriodReopenContext(context.getPeriod(), reopensClose, close,
            context.getCloseBooks(), context.getSteps(), periodCloseService.planReopenReversal(reopensClose.getId()));
        if (!PeriodReopenContentHash.compute(reopen).equals(close.getCloseHash())) {
            throw new BizCheckFailException("重开冲回计划与冻结摘要不一致: " + close.getId());
        }
        return reopen;
    }

    /** 一个事务内：锁期间并复验 REOPENING、按账簿主键升序过账冲回、完成流程并重开期间。 */
    void approveReopen(PeriodReopenContext context, CloseApproval approval) {
        transactionTemplate.executeWithoutResult(status -> {
            periodCloseService.lockReopeningPeriod(context);
            StringBuilder summary = new StringBuilder("POST_REOPEN_REVERSAL");
            for (PeriodCloseBook book : context.getCloseBooks()) {
                Long instructionId = postBookReversal(context, book);
                summary.append('|').append(book.getLedgerBookId()).append('=')
                    .append(instructionId == null ? "NONE" : instructionId);
            }
            periodCloseService.completeReopen(context, approval, summary.toString());
        });
    }

    /**
     * 单账簿冲回：保存 AUTO 指令、以 REOPENING 分录在期末日过账并冻结账簿证据；必须在调用方事务内
     *
     * @return 冲回指令主键；原关账该账簿无结转指令时为空
     */
    Long postBookReversal(PeriodReopenContext context, PeriodCloseBook book) {
        List<InstructionLine> planned = context.getReversalLines().getOrDefault(book.getLedgerBookId(), List.of());
        if (planned.isEmpty()) {
            periodCloseService.finalizeReopenBook(book, null);
            return null;
        }
        AccountingInstruction instruction = buildInstruction(context, book.getLedgerBookId(), planned);
        List<InstructionLine> lines = buildInstructionLines(instruction, planned);
        periodCloseService.saveProfitCloseInstruction(instruction, lines);
        journalPostingService.post(new JournalPostingContext(instruction, lines, context.getPeriod().getId(),
            context.getPeriod().getEndDate(), JournalEntryType.REOPENING));
        periodCloseService.finalizeReopenBook(book, instruction.getId());
        return instruction.getId();
    }

    /** 系统重开 AUTO 指令：来源 FINANCE、事件键 PERIOD_REOPEN:{重开ID}:{账簿ID}、无规则 Bundle，输入摘要覆盖冲回行。 */
    AccountingInstruction buildInstruction(PeriodReopenContext context, long ledgerBookId, List<InstructionLine> planned) {
        Instant now = now();
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(ledgerBookId);
        instruction.setInstructionType(AccountingCodes.PERIOD_PROFIT_REOPEN_INSTRUCTION);
        instruction.setOriginType(InstructionOrigin.AUTO);
        instruction.setSourceSystem(AccountingCodes.FINANCE_SOURCE_SYSTEM);
        instruction.setSourceEventKey("PERIOD_REOPEN:" + context.getClose().getId() + ":" + ledgerBookId);
        instruction.setSourceVersion(1L);
        instruction.setInputHash(PeriodCloseContentHash.sha256(
            planned.stream().map(PeriodReopenContentHash::lineText).collect(Collectors.joining("\n"))));
        instruction.setStatus(InstructionStatus.VALIDATED);
        instruction.setVersion(0L);
        instruction.setCreateTime(now);
        instruction.setUpdateTime(now);
        return instruction;
    }

    /** 由冲回行构造指令行，沿用计划行号。 */
    List<InstructionLine> buildInstructionLines(AccountingInstruction instruction, List<InstructionLine> planned) {
        List<InstructionLine> lines = new ArrayList<>();
        for (InstructionLine plannedLine : planned) {
            InstructionLine line = new InstructionLine();
            line.setId(idGenerator.nextId());
            line.setInstructionId(instruction.getId());
            line.setLineNo(plannedLine.getLineNo());
            line.setAccountId(plannedLine.getAccountId());
            line.setEntrySide(plannedLine.getEntrySide());
            line.setAmount(plannedLine.getAmount());
            line.setCreateTime(instruction.getCreateTime());
            lines.add(line);
        }
        return lines;
    }

    /** Checker 对同一关账摘要的批准记录。 */
    CloseApproval buildApproval(PeriodClose close, long checkerId) {
        Instant now = now();
        CloseApproval approval = new CloseApproval();
        approval.setId(idGenerator.nextId());
        approval.setPeriodCloseId(close.getId());
        approval.setApprovalRole(ApprovalRole.CHECKER);
        approval.setActorId(checkerId);
        approval.setDecision(ApprovalDecision.APPROVED);
        approval.setApprovedCloseHash(close.getCloseHash());
        approval.setDecisionTime(now);
        approval.setCreateTime(now);
        return approval;
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
