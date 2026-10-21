package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.BankMatching;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.OpenReconExceptionRequest;
import lombok.AllArgsConstructor;

/**
 * 人工开立不阻塞的对账异常（technique D-02，#24）：类型、主体与差异金额由来源对象决定，不让人填
 * <li>PLATFORM_TXN → PLATFORM_ITEM_UNSETTLED，差异 = 预期 − 已结清，应收为正、应付为负；所属清算账户与经营主体取自平台流水。</li>
 * <li>EXPECTED_BANK_MOVEMENT → BANK_AMOUNT_MISMATCH，差异 = 预期 − 已确认 − 已归类（不足为正、超出为负）；银行账户与经营主体取自预期流水。</li>
 * <li>BANK_STATEMENT_LINE → BANK_LINE_UNMATCHED，差异 = 金额 − 已匹配，贷方为正、借方为负；主体取自所属银行账户。</li>
 * <li>差异为 0、对象已结清或已取消、同对象同类型已有未结异常时拒绝；并发开立由 uk(source, type, active_slot) 兜底为 409，不加锁。</li>
 */
@Component
@AllArgsConstructor
public class OpenReconExceptionProcessor extends AbstractProcessor<OpenReconExceptionRequest, ReconException> {
    private final PlatformTxnRepository platformTxnRepository;
    private final CashAccountRepository cashAccountRepository;
    private final ReconExceptionService reconExceptionService;
    private final ReconExceptionRepository reconExceptionRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;

    @Override
    protected ReconException processRequest(OpenReconExceptionRequest request) {
        // 由来源对象构造命令
        OpenReconExceptionCommand command = switch (request.getSourceType()) {
            case PLATFORM_TXN -> platformTxnCommand(request);
            case EXPECTED_BANK_MOVEMENT -> movementCommand(request);
            case BANK_STATEMENT_LINE -> bankLineCommand(request);
            default -> throw new BizCheckFailException("该来源对象不能人工开异常: " + request.getSourceType().getCode());
        };
        if (command.getDifferenceAmount().signum() == 0) {
            throw new BizCheckFailException("来源对象已无未完成金额，不能开异常: " + request.getSourceType().getCode() + " "
                + request.getSourceId());
        }
        if (reconExceptionRepository.findActive(command.getSourceType(), command.getSourceId(),
            command.getExceptionType()) != null) {
            throw new BizCheckFailException("同一对象已有未结的同类型异常: " + request.getSourceType().getCode() + " "
                + request.getSourceId() + ", " + command.getExceptionType().getCode());
        }

        // 本地保存
        return reconExceptionService.open(command);
    }

    /** 平台流水 → PLATFORM_ITEM_UNSETTLED。 */
    OpenReconExceptionCommand platformTxnCommand(OpenReconExceptionRequest request) {
        PlatformTxn txn = platformTxnRepository.findById(request.getSourceId());
        if (txn == null || txn.getStatus() == PlatformTxnStatus.SETTLED) {
            throw new BizCheckFailException("平台流水不存在或已结清: " + request.getSourceId());
        }
        BigDecimal unsettled = txn.getExpectedAmount().subtract(txn.getSettledAmount());
        return base(request, ReconExceptionType.PLATFORM_ITEM_UNSETTLED, txn.getCurrency())
            .clearingAccountId(txn.getClearingAccountId()).entityType(ReconEntityType.OPERATING_ENTITY)
            .entityId(txn.getEntityNo())
            .differenceAmount(txn.getDirection() == ClearingDirection.RECEIVABLE ? unsettled : unsettled.negate())
            .detail("平台流水 " + txn.getExternalTransactionId() + " 预期 " + txn.getExpectedAmount().toPlainString()
                + "，已结清 " + txn.getSettledAmount().toPlainString()).build();
    }

    /** 预期银行流水 → BANK_AMOUNT_MISMATCH。 */
    OpenReconExceptionCommand movementCommand(OpenReconExceptionRequest request) {
        ExpectedBankMovement movement = expectedBankMovementRepository.findById(request.getSourceId());
        if (movement == null || movement.getStatus() == ExpectedMovementStatus.CONFIRMED
            || movement.getStatus() == ExpectedMovementStatus.CANCELLED) {
            throw new BizCheckFailException("预期银行流水不存在、已确认或已取消: " + request.getSourceId());
        }
        return base(request, ReconExceptionType.BANK_AMOUNT_MISMATCH, movement.getCurrency())
            .cashAccountId(movement.getCashAccountId()).entityType(ReconEntityType.OPERATING_ENTITY)
            .entityId(movement.getEntityNo()).differenceAmount(BankMatching.movementRemaining(movement))
            .detail("预期 " + movement.getExpectedAmount().toPlainString() + "，已确认 "
                + movement.getConfirmedAmount().toPlainString() + "，已归类 "
                + movement.getClassifiedAmount().toPlainString()).build();
    }

    /** 银行流水 → BANK_LINE_UNMATCHED。 */
    OpenReconExceptionCommand bankLineCommand(OpenReconExceptionRequest request) {
        BankStatementLine line = bankStatementLineRepository.findById(request.getSourceId());
        if (line == null || line.getMatchStatus() == LineMatchStatus.MATCHED) {
            throw new BizCheckFailException("银行流水不存在或已匹配: " + request.getSourceId());
        }
        CashAccount account = cashAccountRepository.findById(line.getCashAccountId());
        return base(request, ReconExceptionType.BANK_LINE_UNMATCHED, account.getCurrency())
            .cashAccountId(account.getId()).entityType(account.getEntityType()).entityId(account.getEntityId())
            .differenceAmount(BankMatching.lineSignedRemaining(line))
            .detail("银行交易号 " + line.getBankTransactionRef() + " 金额 " + line.getAmount().toPlainString() + "，已匹配 "
                + line.getMatchedAmount().toPlainString()).build();
    }

    /** 共同字段：类型、来源、币种与开立人。 */
    static OpenReconExceptionCommand.OpenReconExceptionCommandBuilder base(OpenReconExceptionRequest request,
        ReconExceptionType type, String currency) {
        return OpenReconExceptionCommand.builder().exceptionType(type).sourceType(request.getSourceType())
            .sourceId(request.getSourceId()).currency(currency).openedBy(request.getActorId());
    }
}
