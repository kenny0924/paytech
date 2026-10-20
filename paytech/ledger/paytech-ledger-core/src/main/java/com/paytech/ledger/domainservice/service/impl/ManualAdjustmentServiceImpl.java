package com.paytech.ledger.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.domain.ApproveManualAdjustmentContext;
import com.paytech.ledger.domain.ManualAdjustmentQuery;
import com.paytech.ledger.domain.SubmitManualAdjustmentContext;
import com.paytech.ledger.domain.view.ManualAdjustmentDetailView;
import com.paytech.ledger.domain.view.ManualAdjustmentView;
import com.paytech.ledger.domainservice.repository.AccountingInstructionRepository;
import com.paytech.ledger.domainservice.repository.AdjustmentApprovalRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.InstructionLineRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentLineRepository;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentRepository;
import com.paytech.ledger.domainservice.service.ManualAdjustmentService;

import lombok.AllArgsConstructor;

/** 人工调整的本地保存；状态迁移全部携带期望旧状态与版本。 */
@Service
@AllArgsConstructor
public class ManualAdjustmentServiceImpl implements ManualAdjustmentService {
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final InstructionLineRepository instructionLineRepository;
    private final ManualAdjustmentRepository manualAdjustmentRepository;
    private final AdjustmentApprovalRepository adjustmentApprovalRepository;
    private final ManualAdjustmentLineRepository manualAdjustmentLineRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;

    /** 调整头、明细和 Maker 记录同事务写入。 */
    @Override
    public void submit(SubmitManualAdjustmentContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            manualAdjustmentRepository.insert(context.getAdjustment());
            manualAdjustmentLineRepository.insertBatch(context.getLines());
            adjustmentApprovalRepository.insert(context.getMakerApproval());
        });
    }

    /** SUBMITTED → APPROVED，保存 Checker 记录与人工指令；唯一键阻止同一调整生成第二条指令。 */
    @Override
    public void approve(ApproveManualAdjustmentContext context) {
        requireTransaction();
        manualAdjustmentRepository.updateStatus(context.getAdjustment(), AdjustmentStatus.APPROVED);
        adjustmentApprovalRepository.insert(context.getCheckerApproval());
        accountingInstructionRepository.insert(context.getInstruction());
        instructionLineRepository.insertBatch(context.getInstructionLines());
    }

    /** APPROVED → POSTED。 */
    @Override
    public void markPosted(ApproveManualAdjustmentContext context) {
        requireTransaction();
        manualAdjustmentRepository.updateStatus(context.getAdjustment(), AdjustmentStatus.POSTED);
    }

    /** SUBMITTED → REJECTED 与 Checker 记录同事务写入。 */
    @Override
    public void reject(ManualAdjustment adjustment, AdjustmentApproval checkerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            manualAdjustmentRepository.updateStatus(adjustment, AdjustmentStatus.REJECTED);
            adjustmentApprovalRepository.insert(checkerApproval);
        });
    }

    /** 组合调整头、明细、审批记录及可选的过账分录；金额按币种精度补齐小数位。 */
    @Override
    public ManualAdjustmentDetailView findDetail(long manualAdjustmentId) {
        ManualAdjustment adjustment = manualAdjustmentRepository.findById(manualAdjustmentId);
        if (adjustment == null) {
            return null;
        }
        int minorUnit = currencySpecRepository.findByCurrency(adjustment.getCurrency()).getMinorUnit();
        ManualAdjustmentDetailView view = header(adjustment, minorUnit);
        view.setLines(manualAdjustmentLineRepository.findByAdjustment(manualAdjustmentId).stream()
            .map(source -> line(source, minorUnit)).toList());
        view.setApprovals(adjustmentApprovalRepository.findByAdjustment(manualAdjustmentId).stream().map(this::approval).toList());
        AccountingInstruction instruction = accountingInstructionRepository.findByManualAdjustment(manualAdjustmentId);
        JournalEntry entry = instruction == null ? null : journalEntryRepository.findByInstruction(instruction.getId());
        if (entry != null) {
            view.setJournalEntryId(entry.getId());
            view.setJournalSequence(entry.getJournalSequence());
            view.setPostingDate(entry.getPostingDate());
        }
        return view;
    }

    /** 账簿必须存在；摘要金额按账簿币种精度补齐（调整币种恒等于账簿币种）。 */
    @Override
    public PagingResponseBody<ManualAdjustmentView> pageAdjustments(ManualAdjustmentQuery query) {
        LedgerBook book = ledgerBookRepository.findById(query.getLedgerBookId());
        if (book == null) {
            throw new FailException(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND);
        }
        int minorUnit = currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
        List<ManualAdjustmentView> items = manualAdjustmentRepository.findViewPage(query);
        for (ManualAdjustmentView item : items) {
            item.setTotalDebit(item.getTotalDebit().setScale(minorUnit));
            item.setTotalCredit(item.getTotalCredit().setScale(minorUnit));
        }
        return new PagingResponseBody<>(items, query.getPaging().page(), query.getPaging().size(),
            manualAdjustmentRepository.countPage(query));
    }

    /** 复制调整头字段。 */
    ManualAdjustmentDetailView header(ManualAdjustment adjustment, int minorUnit) {
        ManualAdjustmentDetailView view = new ManualAdjustmentDetailView();
        view.setManualAdjustmentId(adjustment.getId());
        view.setAdjustmentNo(adjustment.getAdjustmentNo());
        view.setLedgerBookId(adjustment.getLedgerBookId());
        view.setAccountingPeriodId(adjustment.getAccountingPeriodId());
        view.setCurrency(adjustment.getCurrency());
        view.setReasonCode(adjustment.getReasonCode());
        view.setFileId(adjustment.getFileId());
        view.setFileContentHash(adjustment.getFileContentHash());
        view.setTotalDebit(adjustment.getTotalDebit().setScale(minorUnit));
        view.setTotalCredit(adjustment.getTotalCredit().setScale(minorUnit));
        view.setContentHash(adjustment.getContentHash());
        view.setStatus(adjustment.getStatus());
        view.setMakerId(adjustment.getMakerId());
        view.setSubmittedTime(adjustment.getSubmittedTime());
        return view;
    }

    /** 复制明细字段。 */
    ManualAdjustmentDetailView.Line line(ManualAdjustmentLine source, int minorUnit) {
        ManualAdjustmentDetailView.Line line = new ManualAdjustmentDetailView.Line();
        line.setLineNo(source.getLineNo());
        line.setAccountId(source.getAccountId());
        line.setEntrySide(source.getEntrySide());
        line.setAmount(source.getAmount().setScale(minorUnit));
        line.setLineDescription(source.getLineDescription());
        return line;
    }

    /** 复制审批记录字段。 */
    ManualAdjustmentDetailView.Approval approval(AdjustmentApproval source) {
        ManualAdjustmentDetailView.Approval approval = new ManualAdjustmentDetailView.Approval();
        approval.setApprovalRole(source.getApprovalRole());
        approval.setActorId(source.getActorId());
        approval.setDecision(source.getDecision());
        approval.setApprovedContentHash(source.getApprovedContentHash());
        approval.setDecisionTime(source.getDecisionTime());
        return approval;
    }

    /** 参与型写入只能加入调用方事务，不能自行提交。 */
    void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("人工调整审批写入必须加入调用方事务");
        }
    }
}
