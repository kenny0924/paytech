package com.paytech.reconciliation.test.builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.test.support.ReconciliationTestConstants;

/** 对账异常（t_recon_exception）的合法模板：系统开立、OPEN、占用活动槽位 1；挂靠对象与差异金额由用例给出。 */
public final class ReconExceptionBuilder extends DataObjectBuilder<ReconException, ReconExceptionBuilder> {

    private ReconExceptionBuilder(ReconException exception) {
        super(exception);
    }

    /** 渠道账单行上的交易层阻塞异常（如 CHANNEL_LINE_UNMATCHED、AMOUNT_MISMATCH）。 */
    public static ReconExceptionBuilder onChannelLine(ChannelStatementLine line, ReconExceptionType type,
        String differenceAmount) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        ReconException exception = new ReconException();
        exception.setId(ReconciliationTestConstants.nextId());
        exception.setExceptionType(type);
        exception.setReconLayer(ReconLayer.TRANSACTION);
        exception.setBlocking(true);
        exception.setSourceType(ReconExceptionSourceType.CHANNEL_STATEMENT_LINE);
        exception.setSourceId(line.getId());
        exception.setStatementId(line.getStatementId());
        exception.setClearingAccountId(line.getClearingAccountId());
        exception.setEntityType(ReconEntityType.OPERATING_ENTITY);
        exception.setEntityId(ReconciliationTestConstants.ENTITY_NO);
        exception.setCurrency(ReconciliationTestConstants.CURRENCY);
        exception.setDifferenceAmount(new BigDecimal(differenceAmount));
        exception.setStatus(ReconExceptionStatus.OPEN);
        exception.setActiveSlot(1);
        exception.setOpenedTime(now);
        exception.setVersion(1L);
        exception.setCreateTime(now);
        exception.setUpdateTime(now);
        return new ReconExceptionBuilder(exception);
    }

    /** 返回自身。 */
    @Override
    protected ReconExceptionBuilder self() {
        return this;
    }
}
