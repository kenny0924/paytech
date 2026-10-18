package com.paytech.acquiring.domain.customer;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.CheckoutNetworkEvidence;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerAcceptance;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContract;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.SubmissionReceipt;
import com.paytech.acquiring.domain.enums.CheckoutEvidenceScene;
import com.paytech.acquiring.domain.enums.ConsumerAcceptanceType;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 本次受理的完整本地上下文，不持有公开 Token、卡输入或 HTTP 对象。 */
@Getter
@AllArgsConstructor
public class SubmitCustomerPaymentContext {
    /** 原订单及写入版本。 */
    private final CommerceOrder order;
    /** 原会话及写入版本。 */
    private final CheckoutSession session;
    /** 本订单准确冻结的合同。 */
    private final ConsumerContract contract;
    /** 本订单唯一 Payment Intent 投影。 */
    @Setter
    private OrderPaymentProjection payment;
    /** 当前支付提交的业务编号，不引用命令幂等表。 */
    private final long submissionId;
    /** 是否需要首次保存当前合同的三份接受事实。 */
    private final boolean newAcceptances;
    /** 本次新增或复用的同一份法律接受网络证据。 */
    private final CheckoutNetworkEvidence evidence;
    /** 完整三份接受，已有接受保持原时间与身份。 */
    private final List<ConsumerAcceptance> acceptances;

    /** 已存在的接受事实只读复用，不改写原身份和时刻。 */
    public boolean hasNewAcceptances() {
        return newAcceptances;
    }

    /** 验证三份准确接受及同一证据；部分接受或跨订单证据不得补造。 */
    public EnumMap<ConsumerAcceptanceType, ConsumerAcceptance> validatedAcceptances() {
        if (acceptances.size() != 3) {
            throw new FailException(CustomerCheckoutErrorCode.LEGAL_ACCEPTANCE_REQUIRED);
        }
        if (evidence == null || evidence.getOrderId() != order.getOrderId()
            || evidence.getCheckoutSessionId() != session.getCheckoutSessionId()
            || evidence.getEvidenceScene() != CheckoutEvidenceScene.LEGAL_ACCEPTANCE) {
            throw new ErrorException("法律接受网络证据归属不一致");
        }
        var byType = new EnumMap<ConsumerAcceptanceType, ConsumerAcceptance>(ConsumerAcceptanceType.class);
        for (var row : acceptances) {
            if (row.getAcceptanceType() == null || byType.put(row.getAcceptanceType(), row) != null
                || row.getConsumerContractId() != contract.getConsumerContractId()
                || row.getNetworkEvidenceId() != evidence.getNetworkEvidenceId() || row.getAcceptedAt() == null) {
                throw new ErrorException("法律接受记录与合同或证据不一致");
            }
        }
        return byType;
    }

    /** 从已加载的接受事实构造稳定回执，不查询数据库或重新生成身份。 */
    public SubmissionReceipt submission() {
        var byType = validatedAcceptances();
        var completed = acceptances.stream().map(ConsumerAcceptance::getAcceptedAt).max(Instant::compareTo).orElseThrow();
        var receipt = new CheckoutAcceptanceReceipt(Long.toString(submissionId),
            Long.toString(order.getOrderId()), Long.toString(session.getCheckoutSessionId()),
            Long.toString(payment.getPaymentIntentId()), Long.toString(contract.getConsumerContractId()),
            Long.toString(byType.get(ConsumerAcceptanceType.CONSUMER_TERMS).getConsumerAcceptanceId()),
            Long.toString(byType.get(ConsumerAcceptanceType.REFUND_POLICY).getConsumerAcceptanceId()),
            Long.toString(byType.get(ConsumerAcceptanceType.PRIVACY_NOTICE).getConsumerAcceptanceId()),
            Long.toString(evidence.getNetworkEvidenceId()), completed);
        return new SubmissionReceipt(receipt, order.getExpiresAt());
    }
}
