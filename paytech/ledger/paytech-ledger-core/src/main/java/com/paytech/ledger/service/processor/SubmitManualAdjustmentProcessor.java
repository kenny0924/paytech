package com.paytech.ledger.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustmentLine;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domain.ManualAdjustmentContentHash;
import com.paytech.ledger.domain.SubmitManualAdjustmentContext;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.ManualAdjustmentService;
import com.paytech.ledger.domainservice.validator.adjustment.OwnerAccountAdjustmentRule;
import com.paytech.ledger.service.processor.request.SubmitManualAdjustmentRequest;
import com.paytech.ledger.service.processor.response.SubmitManualAdjustmentResponse;

import lombok.AllArgsConstructor;

/**
 * 创建并直接提交人工会计调整
 * <li>账簿、期间、币种、账户、精度与借贷平衡全部校验通过后，冻结内容摘要并保存为 SUBMITTED，同时写入 Maker 记录。</li>
 * <li>每次请求都是一笔新调整；HTTP 重复提交由 Gateway Idempotency-Key 处理，不做业务防重。</li>
 * <li>审批过账时会在锁内复验期间、账户与平衡，提交时的校验只用于尽早拒绝。</li>
 * <li>调整行涉及 Client 或 Local Partner 账户时以 OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN 拒绝：影响 Owner 余额的调整必须经 billing 登记
 * MANUAL_ADJUSTMENT 经济事实（design/04 §4）；纯科目重分类不受影响。</li>
 */
@Component
@AllArgsConstructor
public class SubmitManualAdjustmentProcessor
    extends AbstractProcessor<SubmitManualAdjustmentRequest, SubmitManualAdjustmentResponse> {
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChartAccountRepository chartAccountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBookRepository ledgerBookRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final ManualAdjustmentService manualAdjustmentService;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;

    /** 仅依赖输入、首次与重放通用的附加校验。 */
    @Override
    protected void validate(SubmitManualAdjustmentRequest request) {
        if (request.getLines().size() < 2) {
            throw new InvalidParameterException("人工调整至少需要两行明细");
        }
        if ((request.getFileId() == null) != (request.getFileContentHash() == null)) {
            throw new InvalidParameterException("证据文件主键与文件内容摘要必须同时提供或同时为空");
        }
        if (request.getFileContentHash() != null && !SHA256_HEX.matcher(request.getFileContentHash()).matches()) {
            throw new InvalidParameterException("证据文件内容摘要必须是 64 位小写十六进制");
        }
    }

    @Override
    protected SubmitManualAdjustmentResponse processRequest(SubmitManualAdjustmentRequest request) {
        LedgerBook book = getOpenBook(request);
        checkOpenPeriod(book, request.getAccountingPeriodId());
        checkAccounts(book, request.getLines());
        int minorUnit = currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
        BigDecimal total = checkAmounts(request.getLines(), minorUnit);
        SubmitManualAdjustmentContext context = buildContext(request, total, minorUnit);
        manualAdjustmentService.submit(context);
        return response(context.getAdjustment());
    }

    /** 账簿必须存在、开放，且币种与请求一致。 */
    LedgerBook getOpenBook(SubmitManualAdjustmentRequest request) {
        LedgerBook book = ledgerBookRepository.findById(request.getLedgerBookId());
        if (book == null || book.getBookStatus() != BookStatus.OPEN) {
            throw new BizCheckFailException("账簿不存在或未开放: " + request.getLedgerBookId());
        }
        if (!book.getCurrency().equals(request.getCurrency())) {
            throw new BizCheckFailException("调整币种与账簿币种不一致: 账簿 " + book.getId() + " 为 " + book.getCurrency()
                + ", 请求为 " + request.getCurrency());
        }
        return book;
    }

    /** 期间必须开放，且属于账簿主体的财务日历。 */
    void checkOpenPeriod(LedgerBook book, long accountingPeriodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(accountingPeriodId);
        if (period == null || period.getStatus() != PeriodStatus.OPEN) {
            throw new BizCheckFailException("会计期间不存在或未开放: " + accountingPeriodId);
        }
        FiscalCalendar calendar = fiscalCalendarRepository.findById(period.getCalendarId());
        if (calendar.getEntityType() != book.getEntityType() || !calendar.getEntityId().equals(book.getEntityId())) {
            throw new BizCheckFailException("会计期间不属于账簿主体: 期间 " + accountingPeriodId + ", 账簿 " + book.getId());
        }
    }

    /** 每个明细账户必须存在、属于该账簿且开放，且不得是 Client 或 Local Partner 账户。 */
    void checkAccounts(LedgerBook book, List<SubmitManualAdjustmentRequest.Line> lines) {
        List<Long> ids = lines.stream().map(SubmitManualAdjustmentRequest.Line::getAccountId).distinct().toList();
        Map<Long, LedgerAccount> accounts = ledgerAccountRepository.findByIds(ids).stream()
            .collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));
        for (Long id : ids) {
            LedgerAccount account = accounts.get(id);
            if (account == null || !account.getLedgerBookId().equals(book.getId())) {
                throw new BizCheckFailException("账户不存在或不属于账簿: 账户 " + id + ", 账簿 " + book.getId());
            }
            if (account.getAccountStatus() != AccountStatus.ACTIVE) {
                throw new BizCheckFailException("账户未开放过账: " + id + ", 状态 " + account.getAccountStatus().getCode());
            }
        }
        List<Long> chartIds = accounts.values().stream().map(LedgerAccount::getChartAccountId).distinct().sorted().toList();
        OwnerAccountAdjustmentRule.checkNoControlAccounts("账簿 " + book.getId(), accounts.values(),
            chartAccountRepository.findByIds(chartIds));
    }

    /** 金额大于零、不超过币种精度且借贷平衡，返回借方（=贷方）合计。 */
    BigDecimal checkAmounts(List<SubmitManualAdjustmentRequest.Line> lines, int minorUnit) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (SubmitManualAdjustmentRequest.Line line : lines) {
            BigDecimal amount = line.getAmount();
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > minorUnit) {
                throw new BizCheckFailException("调整金额必须大于零且不超过币种精度 " + minorUnit + " 位: "
                    + amount.toPlainString());
            }
            debit = line.getEntrySide() == EntrySide.DEBIT ? debit.add(amount) : debit;
            credit = line.getEntrySide() == EntrySide.CREDIT ? credit.add(amount) : credit;
        }
        if (debit.compareTo(credit) != 0 || debit.signum() <= 0) {
            throw new BizCheckFailException("调整借贷不平衡: 借方 " + debit.toPlainString() + ", 贷方 " + credit.toPlainString());
        }
        return debit;
    }

    /** 构建调整头、明细和 Maker 记录，并计算内容摘要。 */
    SubmitManualAdjustmentContext buildContext(SubmitManualAdjustmentRequest request, BigDecimal total, int minorUnit) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ManualAdjustment adjustment = buildAdjustment(request, total, now);
        List<ManualAdjustmentLine> lines = buildLines(adjustment, request.getLines(), minorUnit, now);
        adjustment.setContentHash(ManualAdjustmentContentHash.compute(adjustment, lines, minorUnit));
        return new SubmitManualAdjustmentContext(adjustment, lines, buildMakerApproval(adjustment, now));
    }

    /** 构建 SUBMITTED 调整头；内容摘要由调用方随后填入。 */
    ManualAdjustment buildAdjustment(SubmitManualAdjustmentRequest request, BigDecimal total, Instant now) {
        ManualAdjustment adjustment = new ManualAdjustment();
        adjustment.setId(idGenerator.nextId());
        adjustment.setAdjustmentNo(AccountingCodes.MANUAL_ADJUSTMENT_NO_PREFIX + adjustment.getId());
        adjustment.setLedgerBookId(request.getLedgerBookId());
        adjustment.setAccountingPeriodId(request.getAccountingPeriodId());
        adjustment.setCurrency(request.getCurrency());
        adjustment.setReasonCode(request.getReasonCode());
        adjustment.setFileId(request.getFileId());
        adjustment.setFileContentHash(request.getFileContentHash());
        adjustment.setTotalDebit(total);
        adjustment.setTotalCredit(total);
        adjustment.setSubmittedTime(now);
        adjustment.setStatus(AdjustmentStatus.SUBMITTED);
        adjustment.setMakerId(request.getMakerId());
        adjustment.setVersion(0L);
        adjustment.setCreateTime(now);
        adjustment.setUpdateTime(now);
        return adjustment;
    }

    /** 按请求顺序从 1 编号构建明细，金额按币种精度规范化。 */
    List<ManualAdjustmentLine> buildLines(ManualAdjustment adjustment, List<SubmitManualAdjustmentRequest.Line> source,
        int minorUnit, Instant now) {
        List<ManualAdjustmentLine> lines = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            SubmitManualAdjustmentRequest.Line requestLine = source.get(i);
            ManualAdjustmentLine line = new ManualAdjustmentLine();
            line.setId(idGenerator.nextId());
            line.setAdjustmentId(adjustment.getId());
            line.setLineNo(i + 1);
            line.setAccountId(requestLine.getAccountId());
            line.setEntrySide(requestLine.getEntrySide());
            line.setAmount(requestLine.getAmount().setScale(minorUnit));
            line.setLineDescription(requestLine.getLineDescription());
            line.setCreateTime(now);
            line.setUpdateTime(now);
            lines.add(line);
        }
        return lines;
    }

    /** Maker 以提交动作确认同一内容摘要。 */
    AdjustmentApproval buildMakerApproval(ManualAdjustment adjustment, Instant now) {
        AdjustmentApproval approval = new AdjustmentApproval();
        approval.setId(idGenerator.nextId());
        approval.setAdjustmentId(adjustment.getId());
        approval.setApprovalRole(ApprovalRole.MAKER);
        approval.setActorId(adjustment.getMakerId());
        approval.setDecision(ApprovalDecision.APPROVED);
        approval.setApprovedContentHash(adjustment.getContentHash());
        approval.setDecisionTime(now);
        approval.setCreateTime(now);
        return approval;
    }

    /** 从已保存的调整构造响应。 */
    SubmitManualAdjustmentResponse response(ManualAdjustment adjustment) {
        SubmitManualAdjustmentResponse response = new SubmitManualAdjustmentResponse();
        response.setManualAdjustmentId(adjustment.getId());
        response.setAdjustmentNo(adjustment.getAdjustmentNo());
        response.setContentHash(adjustment.getContentHash());
        response.setStatus(adjustment.getStatus());
        return response;
    }
}
