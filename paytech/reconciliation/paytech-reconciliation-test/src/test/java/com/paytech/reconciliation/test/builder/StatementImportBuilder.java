package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.api.statement.request.AppendChannelStatementLinesRequestBody;
import com.paytech.reconciliation.api.statement.request.StartChannelStatementImportRequestBody;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 标准渠道账单 CSV 的请求模板（technique §2.5）：账单头 + 明细行，供分段导入三步使用
 * <li>账单头金额默认按恒等式由明细算出（合法默认值）；用例可用 {@link #totals} 覆盖为不一致的负例，build 不修复。</li>
 * <li>声明行数默认等于明细行数，可用 {@link #declaredLineCount} 覆盖。</li>
 */
public final class StatementImportBuilder {
    private final ClearingAccount account;

    private final String channelBatchRef;

    private final List<AppendChannelStatementLinesRequestBody.Line> lines = new ArrayList<>();

    private String grossAmount;

    private String feeAmount;

    private String netAmount;

    private CashDirection cashDirection;

    private Integer declaredLineCount;

    private String currency;

    private StatementImportBuilder(ClearingAccount account, String channelBatchRef) {
        this.account = account;
        this.channelBatchRef = channelBatchRef;
        this.currency = account.getCurrency();
    }

    /** 某清算账户的一张账单。 */
    public static StatementImportBuilder of(ClearingAccount account, String channelBatchRef) {
        return new StatementImportBuilder(account, channelBatchRef);
    }

    /** PAYMENT 行（CREDIT，PAY 交易号）。 */
    public StatementImportBuilder payment(String externalTransactionId, String amount, String fee) {
        return line(ChannelLineType.PAYMENT, SettlementDirection.CREDIT, amount, fee,
            ReconciliationTestConstants.PAY_REFERENCE_TYPE, externalTransactionId);
    }

    /** REFUND 行（DEBIT，REFUND 交易号）。 */
    public StatementImportBuilder refund(String externalTransactionId, String amount, String fee) {
        return line(ChannelLineType.REFUND, SettlementDirection.DEBIT, amount, fee,
            ReconciliationTestConstants.REFUND_REFERENCE_TYPE, externalTransactionId);
    }

    /** ADJUSTMENT 行（不带交易号）。 */
    public StatementImportBuilder adjustment(SettlementDirection direction, String amount) {
        return line(ChannelLineType.ADJUSTMENT, direction, amount, "0", null, null);
    }

    /** FEE 行（DEBIT）；交易号可为空。 */
    public StatementImportBuilder fee(String amount, String referenceType, String externalTransactionId) {
        return line(ChannelLineType.FEE, SettlementDirection.DEBIT, amount, "0", referenceType, externalTransactionId);
    }

    /** 任意明细行；行号按追加顺序从 1 起。 */
    public StatementImportBuilder line(ChannelLineType lineType, SettlementDirection direction, String amount,
        String fee, String referenceType, String externalTransactionId) {
        AppendChannelStatementLinesRequestBody.Line line = new AppendChannelStatementLinesRequestBody.Line();
        line.setLineNo(lines.size() + 1);
        line.setLineType(lineType.getCode());
        line.setDirection(direction.getCode());
        line.setAmount(amount);
        line.setFeeAmount(fee);
        line.setReferenceType(referenceType);
        line.setExternalTransactionId(externalTransactionId);
        line.setTransactionDate(settlementDate());
        lines.add(line);
        return this;
    }

    /** 覆盖账单头金额与净额方向（恒等式负例）。 */
    public StatementImportBuilder totals(String gross, String fee, String net, CashDirection direction) {
        this.grossAmount = gross;
        this.feeAmount = fee;
        this.netAmount = net;
        this.cashDirection = direction;
        return this;
    }

    /** 覆盖声明行数。 */
    public StatementImportBuilder declaredLineCount(int count) {
        this.declaredLineCount = count;
        return this;
    }

    /** 覆盖账单币种。 */
    public StatementImportBuilder currency(String value) {
        this.currency = value;
        return this;
    }

    /** 开始导入请求：未覆盖的金额按恒等式由明细算出。 */
    public StartChannelStatementImportRequestBody header() {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        for (AppendChannelStatementLinesRequestBody.Line line : lines) {
            BigDecimal amount = new BigDecimal(line.getAmount());
            fee = fee.add(new BigDecimal(line.getFeeAmount()));
            if (ChannelLineType.FEE.getCode().equals(line.getLineType())) {
                fee = fee.add(amount);
            } else {
                gross = SettlementDirection.CREDIT.getCode().equals(line.getDirection()) ? gross.add(amount)
                    : gross.subtract(amount);
            }
        }
        BigDecimal net = gross.subtract(fee);
        StartChannelStatementImportRequestBody body = new StartChannelStatementImportRequestBody();
        body.setClearingAccountCode(account.getClearingAccountCode());
        body.setChannelBatchRef(channelBatchRef);
        body.setSettlementDate(settlementDate());
        body.setCurrency(currency);
        body.setGrossAmount(grossAmount != null ? grossAmount : gross.toPlainString());
        body.setFeeAmount(feeAmount != null ? feeAmount : fee.toPlainString());
        body.setNetAmount(netAmount != null ? netAmount : net.abs().toPlainString());
        body.setCashDirection((cashDirection != null ? cashDirection : direction(net)).getCode());
        body.setFileName(channelBatchRef + ".csv");
        body.setFileContentHash("b".repeat(64));
        body.setDeclaredLineCount(declaredLineCount != null ? declaredLineCount : lines.size());
        return body;
    }

    /** 明细行（同一对象，追加请求复用）。 */
    public List<AppendChannelStatementLinesRequestBody.Line> lines() {
        return lines;
    }

    /** 一段明细请求。 */
    public static AppendChannelStatementLinesRequestBody segment(List<AppendChannelStatementLinesRequestBody.Line> lines) {
        AppendChannelStatementLinesRequestBody body = new AppendChannelStatementLinesRequestBody();
        body.setLines(lines);
        return body;
    }

    /** 结算日：当天 UTC。 */
    public static LocalDate settlementDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** 由带符号净额推出方向。 */
    static CashDirection direction(BigDecimal net) {
        if (net.signum() == 0) {
            return CashDirection.NONE;
        }
        return net.signum() > 0 ? CashDirection.CREDIT : CashDirection.DEBIT;
    }
}
