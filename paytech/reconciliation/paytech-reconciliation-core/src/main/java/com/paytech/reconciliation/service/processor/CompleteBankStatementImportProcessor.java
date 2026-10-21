package com.paytech.reconciliation.service.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.paytech.foundation.money.Money;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.domain.BankStatementDuplicates;
import com.paytech.reconciliation.domain.LineRejection;
import com.paytech.reconciliation.domain.OpenReconExceptionCommand;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;
import com.paytech.reconciliation.domainservice.repository.CashAccountRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.service.BankStatementService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.service.processor.request.BankStatementCommandRequest;
import com.paytech.reconciliation.service.processor.response.CompleteBankStatementResponse;
import lombok.AllArgsConstructor;

/**
 * 完成银行对账单导入（UC-R11，#18，设计 F2 G7，AC-09）：一次性校验，通过 IMPORTED 并登记匹配建议任务，不通过整单 REJECTED
 * <li>锁 bank-statement:{id}；操作人须为上传人。已 IMPORTED / REJECTED 时返回当前状态（逐行原因只在首次完成时返回）。</li>
 * <li>校验：已接收行数 + 重复行数 = 声明行数；没有银行交易号重复的行（追加时记下的逐行原因，见 BankStatementDuplicates）；每行起息日在
 * 对账单期间内；期初余额 + Σ 带符号流水（CREDIT 为正）= 期末余额（有重复行时文件不完整入库，不再给出余额原因）。REJECTED 时删除
 * 已接收流水，释放其银行交易号；同编号可更正重传。</li>
 * <li>与同一银行账户上一张已导入对账单（期间结束日最晚）比较：期末 ≠ 本张期初或期间不相接时，同一事务开不阻塞的 BANK_STATEMENT_GAP
 * （差异 = 本张期初 − 上张期末），导入仍成功。</li>
 */
@Component
@AllArgsConstructor
public class CompleteBankStatementImportProcessor
    extends AbstractLockProcessor<BankStatementCommandRequest, CompleteBankStatementResponse> {
    /** 汇总原因列上限。 */
    static final int REASON_LIMIT = 1000;

    private final TransactionTemplate transactionTemplate;
    private final BankStatementService bankStatementService;
    private final CashAccountRepository cashAccountRepository;
    private final ReconExceptionService reconExceptionService;
    private final BankStatementRepository bankStatementRepository;
    private final ReconExceptionRepository reconExceptionRepository;
    private final BankStatementLineRepository bankStatementLineRepository;

    /** 银行对账单资源锁。 */
    @Override
    protected LockCommand lockCommand(BankStatementCommandRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.bankStatement(request.getStatementId()), ReconciliationLocks.WAIT,
            ReconciliationLocks.LEASE);
    }

    @Override
    protected CompleteBankStatementResponse processWithinLock(BankStatementCommandRequest request) {
        // 查询原对账单
        BankStatement statement = load(request);
        if (statement.getStatus() != BankStatementStatus.RECEIVING) {
            return new CompleteBankStatementResponse(statement, List.of(), existingGapId(statement));
        }

        // 一次性校验
        List<LineRejection> rejections = validate(statement, bankStatementLineRepository.listByStatement(
            statement.getId()));
        if (!rejections.isEmpty()) {
            bankStatementService.reject(statement, summary(rejections));
            return new CompleteBankStatementResponse(statement, rejections, null);
        }

        // 连续性与本地保存
        OpenReconExceptionCommand gap = gapCommand(statement);
        ReconException opened = transactionTemplate.execute(status -> {
            bankStatementService.accept(statement);
            return gap == null ? null : reconExceptionService.open(gap);
        });
        return new CompleteBankStatementResponse(statement, List.of(), opened == null ? null : opened.getId());
    }

    /** 对账单存在且操作人为上传人。 */
    BankStatement load(BankStatementCommandRequest request) {
        BankStatement statement = bankStatementRepository.findById(request.getStatementId());
        if (statement == null) {
            throw new BizCheckFailException("银行对账单不存在: " + request.getStatementId());
        }
        if (!statement.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("只有开始导入的上传人可以完成导入: 对账单 " + statement.getId());
        }
        return statement;
    }

    /** 行数、逐行起息日与余额恒等式；返回全部拒绝原因（整单原因在前）。 */
    static List<LineRejection> validate(BankStatement statement, List<BankStatementLine> lines) {
        List<LineRejection> rejections = new ArrayList<>();
        int duplicates = statement.getDuplicateLineCount();
        if (lines.size() + duplicates != statement.getDeclaredLineCount()) {
            rejections.add(new LineRejection(null, "已接收 " + lines.size() + " 行、交易号重复 " + duplicates
                + " 行，与声明行数 " + statement.getDeclaredLineCount() + " 不一致"));
        }
        BigDecimal closing = statement.getOpeningBalance().add(signedTotal(lines));
        if (duplicates == 0 && closing.compareTo(statement.getClosingBalance()) != 0) {
            rejections.add(new LineRejection(null, balanceReason(statement, lines, closing)));
        }
        List<LineRejection> lineReasons = new ArrayList<>(BankStatementDuplicates.rejections(statement.getRejectReason()));
        if (duplicates > lineReasons.size()) {
            rejections.add(new LineRejection(null, "另有 " + (duplicates - lineReasons.size())
                + " 行银行交易号重复，原因超出记录长度未逐行列出"));
        }
        for (BankStatementLine line : lines) {
            if (line.getValueDate().isBefore(statement.getPeriodStart())
                || line.getValueDate().isAfter(statement.getPeriodEnd())) {
                lineReasons.add(new LineRejection(line.getLineNo(), "起息日 " + line.getValueDate() + " 不在对账单期间 "
                    + statement.getPeriodStart() + " 至 " + statement.getPeriodEnd() + " 内"));
            }
        }
        lineReasons.sort(Comparator.comparing(LineRejection::getLineNo));
        rejections.addAll(lineReasons);
        return rejections;
    }

    /** 余额恒等式不成立的原因。 */
    static String balanceReason(BankStatement statement, List<BankStatementLine> lines, BigDecimal closing) {
        String currency = statement.getCurrency();
        return "期初余额 " + text(currency, statement.getOpeningBalance()) + " 加流水合计 " + text(currency,
            signedTotal(lines)) + " 等于 " + text(currency, closing) + "，与期末余额 "
            + text(currency, statement.getClosingBalance()) + " 不一致";
    }

    /** 按币种精度展示的金额。 */
    static String text(String currency, BigDecimal amount) {
        return Money.of(currency, amount).toAmountString();
    }

    /** 带符号流水合计：CREDIT 为正、DEBIT 为负。 */
    static BigDecimal signedTotal(List<BankStatementLine> lines) {
        return lines.stream().map(line -> line.getDirection() == SettlementDirection.CREDIT ? line.getAmount()
            : line.getAmount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 与上一张已导入对账单接不上时的 BANK_STATEMENT_GAP 命令；接得上或没有上一张为空。 */
    OpenReconExceptionCommand gapCommand(BankStatement statement) {
        BankStatement previous = bankStatementRepository.findLatestImported(statement.getCashAccountId());
        if (previous == null || (previous.getClosingBalance().compareTo(statement.getOpeningBalance()) == 0
            && previous.getPeriodEnd().plusDays(1).equals(statement.getPeriodStart()))) {
            return null;
        }
        CashAccount account = cashAccountRepository.findById(statement.getCashAccountId());
        String currency = statement.getCurrency();
        String detail = "上一张对账单 " + previous.getStatementRef() + " 期间截至 " + previous.getPeriodEnd() + "、期末余额 "
            + text(currency, previous.getClosingBalance()) + "；本张期间自 " + statement.getPeriodStart() + "、期初余额 "
            + text(currency, statement.getOpeningBalance());
        return OpenReconExceptionCommand.builder().exceptionType(ReconExceptionType.BANK_STATEMENT_GAP)
            .sourceType(ReconExceptionSourceType.BANK_STATEMENT).sourceId(statement.getId())
            .cashAccountId(account.getId()).entityType(account.getEntityType()).entityId(account.getEntityId())
            .currency(statement.getCurrency())
            .differenceAmount(statement.getOpeningBalance().subtract(previous.getClosingBalance())).detail(detail).build();
    }

    /** 已完成对账单的连续性异常主键（任意状态的最近一条）；没有为空。 */
    Long existingGapId(BankStatement statement) {
        ReconException gap = reconExceptionRepository.findLatestBySource(ReconExceptionSourceType.BANK_STATEMENT,
            statement.getId(), ReconExceptionType.BANK_STATEMENT_GAP);
        return gap == null ? null : gap.getId();
    }

    /** 汇总原因：条数加原因，截断到列上限。 */
    static String summary(List<LineRejection> rejections) {
        String detail = rejections.stream().map(rejection -> rejection.getLineNo() == null ? rejection.getReason()
            : "第 " + rejection.getLineNo() + " 行：" + rejection.getReason()).collect(Collectors.joining("；"));
        String text = "校验不通过，共 " + rejections.size() + " 条原因：" + detail;
        return text.length() <= REASON_LIMIT ? text : text.substring(0, REASON_LIMIT);
    }
}
