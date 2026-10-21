package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import com.paytech.reconciliation.api.bank.request.AppendBankStatementLinesRequestBody;
import com.paytech.reconciliation.api.bank.request.StartBankStatementImportRequestBody;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;

/**
 * 标准银行对账单 CSV 的请求模板（technique §2.5）：所选银行账户、对账单头与流水行，供分段导入三步使用
 * <li>默认期间为当天、期初 0、期末按恒等式由流水算出（合法默认值）；用例可覆盖期末、期间、末四位等构造负例，build 不修复。</li>
 */
public final class BankStatementImportBuilder {
    private final CashAccount account;

    private final String statementRef;

    private final List<AppendBankStatementLinesRequestBody.Line> lines = new ArrayList<>();

    private LocalDate periodStart = today();

    private LocalDate periodEnd = today();

    private String openingBalance = "0";

    private String closingBalance;

    private String last4;

    private String currency;

    private Integer declaredLineCount;

    private BankStatementImportBuilder(CashAccount account, String statementRef) {
        this.account = account;
        this.statementRef = statementRef;
        this.last4 = account.getAccountLast4();
        this.currency = account.getCurrency();
    }

    /** 某银行账户的一张对账单。 */
    public static BankStatementImportBuilder of(CashAccount account, String statementRef) {
        return new BankStatementImportBuilder(account, statementRef);
    }

    /** 一条流水，起息日取期间结束日；行号按追加顺序从 1 起。 */
    public BankStatementImportBuilder line(String bankTransactionRef, SettlementDirection direction, String amount) {
        return line(bankTransactionRef, direction, amount, periodEnd);
    }

    /** 一条指定起息日的流水。 */
    public BankStatementImportBuilder line(String bankTransactionRef, SettlementDirection direction, String amount,
        LocalDate valueDate) {
        AppendBankStatementLinesRequestBody.Line line = new AppendBankStatementLinesRequestBody.Line();
        line.setLineNo(lines.size() + 1);
        line.setBankTransactionRef(bankTransactionRef);
        line.setValueDate(valueDate);
        line.setDirection(direction.getCode());
        line.setAmount(amount);
        line.setCounterpartyName("Channel NI");
        line.setReferenceText("SETTLEMENT " + bankTransactionRef);
        lines.add(line);
        return this;
    }

    /** 覆盖期间。 */
    public BankStatementImportBuilder period(LocalDate start, LocalDate end) {
        this.periodStart = start;
        this.periodEnd = end;
        return this;
    }

    /** 覆盖期初余额。 */
    public BankStatementImportBuilder opening(String value) {
        this.openingBalance = value;
        return this;
    }

    /** 覆盖期末余额（恒等式负例）。 */
    public BankStatementImportBuilder closing(String value) {
        this.closingBalance = value;
        return this;
    }

    /** 覆盖文件头末四位。 */
    public BankStatementImportBuilder last4(String value) {
        this.last4 = value;
        return this;
    }

    /** 覆盖币种。 */
    public BankStatementImportBuilder currency(String value) {
        this.currency = value;
        return this;
    }

    /** 覆盖声明行数。 */
    public BankStatementImportBuilder declaredLineCount(int count) {
        this.declaredLineCount = count;
        return this;
    }

    /** 开始导入请求：未覆盖的期末余额 = 期初 + Σ 带符号流水。 */
    public StartBankStatementImportRequestBody header() {
        BigDecimal closing = new BigDecimal(openingBalance);
        for (AppendBankStatementLinesRequestBody.Line line : lines) {
            BigDecimal amount = new BigDecimal(line.getAmount());
            closing = SettlementDirection.CREDIT.getCode().equals(line.getDirection()) ? closing.add(amount)
                : closing.subtract(amount);
        }
        StartBankStatementImportRequestBody body = new StartBankStatementImportRequestBody();
        body.setCashAccountId(account.getId().toString());
        body.setBankAccountLast4(last4);
        body.setStatementRef(statementRef);
        body.setPeriodStart(periodStart);
        body.setPeriodEnd(periodEnd);
        body.setCurrency(currency);
        body.setOpeningBalance(openingBalance);
        body.setClosingBalance(closingBalance != null ? closingBalance : closing.toPlainString());
        body.setFileName(statementRef + ".csv");
        body.setFileContentHash("d".repeat(64));
        body.setDeclaredLineCount(declaredLineCount != null ? declaredLineCount : lines.size());
        return body;
    }

    /** 流水行（同一对象，追加请求复用）。 */
    public List<AppendBankStatementLinesRequestBody.Line> lines() {
        return lines;
    }

    /** 一段流水请求。 */
    public static AppendBankStatementLinesRequestBody segment(List<AppendBankStatementLinesRequestBody.Line> lines) {
        AppendBankStatementLinesRequestBody body = new AppendBankStatementLinesRequestBody();
        body.setLines(lines);
        return body;
    }

    /** 当天 UTC。 */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
