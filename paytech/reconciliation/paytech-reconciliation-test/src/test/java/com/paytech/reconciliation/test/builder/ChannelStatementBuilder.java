package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/**
 * 渠道账单（t_channel_statement）与账单行（t_channel_statement_line）的合法模板
 * <li>账单头默认已完成导入（MATCHING），金额由用例按恒等式给出；行默认 UNMATCHED，引用列按行类型由用例给出。</li>
 */
public final class ChannelStatementBuilder extends DataObjectBuilder<ChannelStatement, ChannelStatementBuilder> {

    private ChannelStatementBuilder(ChannelStatement statement) {
        super(statement);
    }

    /** MATCHING 账单头：批次号、净额方向与金额（总额带符号、费用与净额非负），声明行数与已接收行数相同。 */
    public static ChannelStatementBuilder matching(ClearingAccount account, String channelBatchRef, String grossAmount,
        String feeAmount, String netAmount, CashDirection cashDirection, int lineCount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ChannelStatement statement = new ChannelStatement();
        statement.setId(ReconciliationTestConstants.nextId());
        statement.setClearingAccountId(account.getId());
        statement.setChannelBatchRef(channelBatchRef);
        statement.setRevision(1);
        statement.setSettlementDate(LocalDate.ofInstant(now, ZoneOffset.UTC));
        statement.setCurrency(account.getCurrency());
        statement.setGrossAmount(new BigDecimal(grossAmount));
        statement.setFeeAmount(new BigDecimal(feeAmount));
        statement.setNetAmount(new BigDecimal(netAmount));
        statement.setCashDirection(cashDirection);
        statement.setDeclaredLineCount(lineCount);
        statement.setImportedLineCount(lineCount);
        statement.setFileName(channelBatchRef + ".csv");
        statement.setFileContentHash("a".repeat(64));
        statement.setStatus(ChannelStatementStatus.MATCHING);
        statement.setCashConfirmedAmount(BigDecimal.ZERO);
        statement.setMakerId(ReconciliationTestConstants.MAKER_ID);
        statement.setVersion(1L);
        statement.setCreateTime(now);
        statement.setUpdateTime(now);
        return new ChannelStatementBuilder(statement);
    }

    /** 未匹配的交易行（PAYMENT / REFUND / CHARGEBACK / FEE）：外部交易号可为空（ADJUSTMENT、无交易号 FEE）。 */
    public static ChannelStatementLine line(ChannelStatement statement, int lineNo, ChannelLineType lineType,
        SettlementDirection direction, String amount, String feeAmount, String referenceType,
        String externalTransactionId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ChannelStatementLine line = new ChannelStatementLine();
        line.setId(ReconciliationTestConstants.nextId());
        line.setStatementId(statement.getId());
        line.setClearingAccountId(statement.getClearingAccountId());
        line.setLineNo(lineNo);
        line.setLineType(lineType);
        line.setDirection(direction);
        line.setAmount(new BigDecimal(amount));
        line.setFeeAmount(new BigDecimal(feeAmount));
        line.setReferenceType(referenceType);
        line.setExternalTransactionId(externalTransactionId);
        line.setTransactionDate(statement.getSettlementDate());
        line.setMatchStatus(LineMatchStatus.UNMATCHED);
        line.setVersion(1L);
        line.setCreateTime(now);
        line.setUpdateTime(now);
        return line;
    }

    /** 返回自身。 */
    @Override
    protected ChannelStatementBuilder self() {
        return this;
    }
}
