package com.paytech.commerce.core.dal.dataobject.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.paytech.commerce.domain.enums.RefundRequestOrigin;
import com.paytech.commerce.domain.enums.RefundStatus;
import com.paytech.foundation.common.error.ErrorException;

import lombok.*;

/** t_refund_request 的本库事实；不混入关联订单的查询字段。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    /** 本库退款主键。 */
    private long refundRequestId;

    /** 永久唯一的公开退款号。 */
    private String refundRequestNo;

    /** 所属 Commerce 订单主键。 */
    private long orderId;

    /** 与 Order 一致的归属 Client。 */
    private long clientId;

    /** 创建来源。 */
    private RefundRequestOrigin requestOrigin;

    /** Client 内永久唯一业务号；Operations 为 null。 */
    private String clientRefundId;

    /** 申请退款金额，必须满足订单币种精度。 */
    private BigDecimal refundAmount;

    /** 继承订单的 ISO 4217 币种。 */
    private String currency;

    /** 创建时冻结的退款原因代码。 */
    private String reasonCode;

    /** 可空的非敏感原因说明，不对外查询或写入任务 Payload。 */
    private String reasonSummary;

    /** Commerce 退款状态；未知结果保持 PROCESSING。 */
    private RefundStatus refundStatus;

    /** 失败或取消原因；其他状态为 null。 */
    private String statusReasonCode;

    /** Payment 返回的稳定 Channel Refund 身份；未知时为 null。 */
    private Long channelRefundId;

    /** 终态完成 UTC 时刻；未决为空。 */
    private Instant completedAt;

    /** 本行并发及表示版本。 */
    private long version;

    /** 退款创建 UTC 时刻。 */
    private Instant createTime;

    /** 当前状态最近更新 UTC 时刻。 */
    private Instant updateTime;

    /** 同一业务号只能对应原订单、金额、币种及原因；金额忽略无意义的尾随零。 */
    public boolean matchesCreation(long expectedOrderId, BigDecimal amount, String expectedCurrency,
        String expectedReasonCode, String expectedReasonSummary) {
        return orderId == expectedOrderId && refundAmount.compareTo(amount) == 0
            && Objects.equals(currency, expectedCurrency) && Objects.equals(reasonCode, expectedReasonCode)
            && Objects.equals(reasonSummary, expectedReasonSummary);
    }

    /** 检查已加载的退款状态、时间及渠道身份，不查询外部事实。 */
    public void validateFacts() {
        if (refundStatus == null || requestOrigin == null || version < 1 || refundAmount == null
            || refundAmount.signum() <= 0 || createTime == null || updateTime == null || updateTime.isBefore(createTime)
            || refundStatus.terminal() != (completedAt != null)
            || completedAt != null && (completedAt.isBefore(createTime) || completedAt.isAfter(updateTime))
            || requestOrigin == RefundRequestOrigin.CLIENT && clientRefundId == null
            || requestOrigin == RefundRequestOrigin.OPERATIONS && clientRefundId != null
            || channelRefundId != null && channelRefundId <= 0
            || refundStatus == RefundStatus.SUCCEEDED && channelRefundId == null) {
            throw new ErrorException("退款持久化事实不一致");
        }
    }
}
