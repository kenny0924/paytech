package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.domain.ApproveManualAdjustmentContext;
import com.paytech.ledger.domainservice.repository.AccountingInstructionRepository;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.AdjustmentApprovalRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentLineRepository;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentRepository;
import com.paytech.ledger.domainservice.service.JournalPostingService;
import com.paytech.ledger.domainservice.service.ManualAdjustmentService;
import com.paytech.ledger.domainservice.validator.adjustment.ManualAdjustmentReviewValidator;
import com.paytech.ledger.domainservice.validator.adjustment.OwnerAccountAdjustmentRule;
import com.paytech.ledger.service.processor.request.ReviewManualAdjustmentRequest;
import com.paytech.ledger.service.processor.response.ReviewManualAdjustmentResponse;

import lombok.AllArgsConstructor;

/**
 * 批准人工调整并在同一事务内过账
 * <li>与驳回共用调整资源锁；状态迁移仍以版本 CAS 保护，锁不是唯一防线。</li>
 * <li>一个事务内依次：调整 SUBMITTED→APPROVED 与 Checker 记录、生成 VALIDATED 人工指令、过账、调整→POSTED；任一步失败整体回滚。</li>
 * <li>同一复核人以同一摘要重复批准已 POSTED 的调整时返回原过账结果，其他重复复核拒绝。</li>
 * <li>过账日期取批准时刻在日历时区的日期，并收敛到期间起止日期之内。</li>
 * <li>事务前复验调整行不涉及 Client 或 Local Partner 账户（OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN，design/04 §4），拦截第 11b 步前提交的存量调整；
 * 账户身份只增不改，事务前读取即可。</li>
 */
@Component
@AllArgsConstructor
public class ApproveManualAdjustmentProcessor
    extends AbstractLockProcessor<ReviewManualAdjustmentRequest, ReviewManualAdjustmentResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final JournalPostingService journalPostingService;
    private final ChartAccountRepository chartAccountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final ManualAdjustmentService manualAdjustmentService;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ManualAdjustmentRepository manualAdjustmentRepository;
    private final AdjustmentApprovalRepository adjustmentApprovalRepository;
    private final ManualAdjustmentLineRepository manualAdjustmentLineRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;

    /** 与同一资源的其他写入口共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ReviewManualAdjustmentRequest request) {
        return LockCommand.tryLock(AccountingLocks.manualAdjustment(request.getManualAdjustmentId()));
    }

    @Override
    protected ReviewManualAdjustmentResponse processWithinLock(ReviewManualAdjustmentRequest request) {
        // 查询原复核
        ManualAdjustment adjustment = getAdjustment(request.getManualAdjustmentId());
        if (adjustment.getStatus() == AdjustmentStatus.POSTED) {
            return replayResponse(adjustment, request);
        }

        // 构建上下文
        ManualAdjustmentReviewValidator.checkReviewable(adjustment, request.getCheckerId(),
            request.getApprovedContentHash());
        ApproveManualAdjustmentContext context = buildContext(adjustment, request.getCheckerId());
        checkNoControlAccounts(adjustment, context.getInstructionLines());

        // 批准并过账
        transactionTemplate.executeWithoutResult(status -> {
            manualAdjustmentService.approve(context);
            context.setJournalEntry(journalPostingService.post(context.postingContext()));
            manualAdjustmentService.markPosted(context);
        });
        return response(adjustment, context.getJournalEntry());
    }

    /** 调整行引用的账户不得属于 Owner 控制科目。 */
    void checkNoControlAccounts(ManualAdjustment adjustment, List<InstructionLine> lines) {
        List<Long> ids = lines.stream().map(InstructionLine::getAccountId).distinct().sorted().toList();
        List<LedgerAccount> accounts = ledgerAccountRepository.findByIds(ids);
        List<Long> chartIds = accounts.stream().map(LedgerAccount::getChartAccountId).distinct().sorted().toList();
        OwnerAccountAdjustmentRule.checkNoControlAccounts("调整 " + adjustment.getId(), accounts,
            chartAccountRepository.findByIds(chartIds));
    }

    /** 读取调整，不存在时业务拒绝。 */
    ManualAdjustment getAdjustment(long manualAdjustmentId) {
        ManualAdjustment adjustment = manualAdjustmentRepository.findById(manualAdjustmentId);
        if (adjustment == null) {
            throw new BizCheckFailException("人工调整不存在: " + manualAdjustmentId);
        }
        return adjustment;
    }

    /** 同一复核人、同一摘要的批准重放返回原分录；否则按已处理拒绝。 */
    ReviewManualAdjustmentResponse replayResponse(ManualAdjustment adjustment, ReviewManualAdjustmentRequest request) {
        List<AdjustmentApproval> approvals = adjustmentApprovalRepository.findByAdjustment(adjustment.getId());
        if (!ManualAdjustmentReviewValidator.isSameReview(approvals, request.getCheckerId(), ApprovalDecision.APPROVED,
            request.getApprovedContentHash())) {
            throw new FailException(LedgerErrorCode.REVIEW_STATUS_INVALID);
        }
        AccountingInstruction instruction = accountingInstructionRepository.findByManualAdjustment(adjustment.getId());
        return response(adjustment, journalEntryRepository.findByInstruction(instruction.getId()));
    }

    /** 构建 Checker 记录、人工指令及指令行，并确定过账日期。 */
    ApproveManualAdjustmentContext buildContext(ManualAdjustment adjustment, long checkerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountingInstruction instruction = buildInstruction(adjustment, checkerId, now);
        List<InstructionLine> instructionLines =
            buildInstructionLines(instruction, manualAdjustmentLineRepository.findByAdjustment(adjustment.getId()), now);
        return new ApproveManualAdjustmentContext(adjustment, buildCheckerApproval(adjustment, checkerId, now),
            instruction, instructionLines, postingDate(adjustment.getAccountingPeriodId(), now));
    }

    /** 批准时刻在日历时区的日期，收敛到期间区间内。 */
    LocalDate postingDate(long accountingPeriodId, Instant now) {
        AccountingPeriod period = accountingPeriodRepository.findById(accountingPeriodId);
        ZoneId zone = ZoneId.of(fiscalCalendarRepository.findById(period.getCalendarId()).getTimezone());
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (today.isBefore(period.getStartDate())) {
            return period.getStartDate();
        }
        return today.isAfter(period.getEndDate()) ? period.getEndDate() : today;
    }

    /** Checker 对同一内容摘要的批准记录。 */
    AdjustmentApproval buildCheckerApproval(ManualAdjustment adjustment, long checkerId, Instant now) {
        AdjustmentApproval approval = new AdjustmentApproval();
        approval.setId(idGenerator.nextId());
        approval.setAdjustmentId(adjustment.getId());
        approval.setApprovalRole(ApprovalRole.CHECKER);
        approval.setActorId(checkerId);
        approval.setDecision(ApprovalDecision.APPROVED);
        approval.setApprovedContentHash(adjustment.getContentHash());
        approval.setDecisionTime(now);
        approval.setCreateTime(now);
        return approval;
    }

    /** MANUAL 指令：来源字段为空，冻结调整内容摘要与 Maker/Checker。 */
    AccountingInstruction buildInstruction(ManualAdjustment adjustment, long checkerId, Instant now) {
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(adjustment.getLedgerBookId());
        instruction.setInstructionType(AccountingCodes.MANUAL_ADJUSTMENT_INSTRUCTION);
        instruction.setOriginType(InstructionOrigin.MANUAL);
        instruction.setManualAdjustmentId(adjustment.getId());
        instruction.setInputHash(adjustment.getContentHash());
        instruction.setStatus(InstructionStatus.VALIDATED);
        instruction.setMakerId(adjustment.getMakerId());
        instruction.setCheckerId(checkerId);
        instruction.setVersion(0L);
        instruction.setCreateTime(now);
        instruction.setUpdateTime(now);
        return instruction;
    }

    /** 指令行与调整明细逐行一致。 */
    List<InstructionLine> buildInstructionLines(AccountingInstruction instruction, List<ManualAdjustmentLine> source,
        Instant now) {
        List<InstructionLine> lines = new ArrayList<>();
        for (ManualAdjustmentLine adjustmentLine : source) {
            InstructionLine line = new InstructionLine();
            line.setId(idGenerator.nextId());
            line.setInstructionId(instruction.getId());
            line.setLineNo(adjustmentLine.getLineNo());
            line.setManualAdjustmentLineId(adjustmentLine.getId());
            line.setAccountId(adjustmentLine.getAccountId());
            line.setEntrySide(adjustmentLine.getEntrySide());
            line.setAmount(adjustmentLine.getAmount());
            line.setCreateTime(now);
            lines.add(line);
        }
        return lines;
    }

    /** 从调整与分录构造响应。 */
    ReviewManualAdjustmentResponse response(ManualAdjustment adjustment, JournalEntry entry) {
        ReviewManualAdjustmentResponse response = new ReviewManualAdjustmentResponse();
        response.setManualAdjustmentId(adjustment.getId());
        response.setStatus(adjustment.getStatus());
        response.setJournalEntryId(entry.getId());
        response.setJournalSequence(entry.getJournalSequence());
        response.setPostingDate(entry.getPostingDate());
        return response;
    }
}
