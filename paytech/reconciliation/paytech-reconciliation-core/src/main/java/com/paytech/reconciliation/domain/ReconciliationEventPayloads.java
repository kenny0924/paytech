package com.paytech.reconciliation.domain;

import com.paytech.reconciliation.api.event.ChannelSettlementConfirmedPayload;
import com.paytech.reconciliation.api.event.ReconAdjustmentApprovedPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.paytech.foundation.money.Money;
import com.paytech.reconciliation.api.event.ActualChannelCostConfirmedPayload;
import com.paytech.reconciliation.api.event.ChannelBatchReconciledPayload;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 对账事件载荷的 JSON 形态（Foundation JSON Object Profile）：所有标量都是字符串，缺省为 JSON null，金额按币种精度补齐
 * <li>字段顺序与 reconciliation-api 载荷类声明一致；纯转换，不访问数据库。</li>
 */
public final class ReconciliationEventPayloads {
    private ReconciliationEventPayloads() {
    }

    /**
     * ChannelBatchReconciled 载荷 JSON
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(ChannelBatchReconciledPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("statementId", text(payload.getStatementId()));
        node.put("clearingAccountId", text(payload.getClearingAccountId()));
        node.put("clearingAccountCode", payload.getClearingAccountCode());
        node.put("entityNo", payload.getEntityNo());
        node.put("currency", payload.getCurrency());
        node.put("channelBatchRef", payload.getChannelBatchRef());
        node.put("revision", text(payload.getRevision()));
        node.put("settlementDate", text(payload.getSettlementDate()));
        node.put("cashDirection", payload.getCashDirection());
        node.put("netAmount", text(payload.getNetAmount()));
        node.put("receivableTotal", text(payload.getReceivableTotal()));
        node.put("payableTotal", text(payload.getPayableTotal()));
        node.put("actualFeeTotal", text(payload.getActualFeeTotal()));
        ArrayNode lines = node.putArray("classifiedLines");
        for (ChannelBatchReconciledPayload.ClassifiedLine line : payload.getClassifiedLines()) {
            ObjectNode item = lines.addObject();
            item.put("lineNo", text(line.getLineNo()));
            item.put("classificationCode", line.getClassificationCode());
            item.put("amount", text(line.getAmount()));
            item.put("resolutionId", text(line.getResolutionId()));
        }
        node.put("platformTxnCount", text(payload.getPlatformTxnCount()));
        node.put("expectedMovementId", text(payload.getExpectedMovementId()));
        node.put("reconciledTime", text(payload.getReconciledTime()));
        return node;
    }

    /**
     * ActualChannelCostConfirmed 载荷 JSON
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(ActualChannelCostConfirmedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("platformTxnId", text(payload.getPlatformTxnId()));
        node.put("billingFactId", text(payload.getBillingFactId()));
        node.put("clearingAccountId", text(payload.getClearingAccountId()));
        node.put("clearingAccountCode", payload.getClearingAccountCode());
        node.put("entityNo", payload.getEntityNo());
        node.put("currency", payload.getCurrency());
        node.put("txnSource", payload.getTxnSource());
        node.put("expectedFee", text(payload.getExpectedFee()));
        node.put("actualFee", text(payload.getActualFee()));
        node.put("difference", text(payload.getDifference()));
        node.put("channelTransactionRefId", payload.getChannelTransactionRefId());
        node.put("referenceType", payload.getReferenceType());
        node.put("externalTransactionId", payload.getExternalTransactionId());
        node.put("statementId", text(payload.getStatementId()));
        node.put("confirmedTime", text(payload.getConfirmedTime()));
        return node;
    }

    /**
     * ChannelSettlementConfirmed 载荷 JSON
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(ChannelSettlementConfirmedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("bankMatchId", text(payload.getBankMatchId()));
        node.put("expectedMovementId", text(payload.getExpectedMovementId()));
        node.put("statementId", text(payload.getStatementId()));
        node.put("clearingAccountId", text(payload.getClearingAccountId()));
        node.put("clearingAccountCode", payload.getClearingAccountCode());
        node.put("cashAccountId", text(payload.getCashAccountId()));
        node.put("entityNo", payload.getEntityNo());
        node.put("currency", payload.getCurrency());
        node.put("direction", payload.getDirection());
        node.put("matchedAmount", text(payload.getMatchedAmount()));
        node.put("bankStatementLineId", text(payload.getBankStatementLineId()));
        node.put("valueDate", text(payload.getValueDate()));
        node.put("confirmedTime", text(payload.getConfirmedTime()));
        return node;
    }

    /**
     * ReconAdjustmentApproved 载荷 JSON
     *
     * @param payload 载荷
     * @return JSON Object
     */
    public static ObjectNode json(ReconAdjustmentApprovedPayload payload) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("resolutionId", text(payload.getResolutionId()));
        node.put("exceptionId", text(payload.getExceptionId()));
        node.put("sourceType", payload.getSourceType());
        node.put("sourceId", text(payload.getSourceId()));
        node.put("classificationCode", payload.getClassificationCode());
        node.put("entityType", payload.getEntityType());
        node.put("entityId", payload.getEntityId());
        node.put("currency", payload.getCurrency());
        node.put("amount", text(payload.getAmount()));
        node.put("clearingAccountId", text(payload.getClearingAccountId()));
        node.put("cashAccountId", text(payload.getCashAccountId()));
        node.put("makerId", text(payload.getMakerId()));
        node.put("checkerId", text(payload.getCheckerId()));
        node.put("approvedContentHash", payload.getApprovedContentHash());
        node.put("approvedTime", text(payload.getApprovedTime()));
        return node;
    }

    /**
     * 按币种精度补齐的金额（如 USD 2.0000 → 2.00）
     *
     * @param currency 币种
     * @param amount 金额，可为空
     * @return 补齐后的金额；为空时为空
     */
    public static BigDecimal scaled(String currency, BigDecimal amount) {
        return amount == null ? null : new BigDecimal(Money.of(currency, amount).toAmountString());
    }

    /** 十进制、日期或时间文本；为空时为空。 */
    static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Instant || value instanceof LocalDate) {
            return value.toString();
        }
        return value == null ? null : value.toString();
    }
}
