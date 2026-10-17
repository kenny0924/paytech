package com.paytech.acquiring.service.processor.refund.response;

import java.time.Instant;

import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.RefundStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前退款的查询结果，不携带内部主键、渠道身份或原因原文。 */
@Getter
@AllArgsConstructor
public class GetRefundResponse {
    /** 公开退款号。 */
    private final String refundRequestNo;

    /** Client 业务退款号；Operations 来源为空。 */
    private final String clientRefundId;

    /** 创建来源。 */
    private final RefundRequestOrigin requestOrigin;

    /** 归属当前 Client 的公开订单号。 */
    private final String orderNo;

    /** 按币种精度输出的主单位退款金额。 */
    private final String amount;

    /** 退款币种。 */
    private final String currency;

    /** 当前退款状态。 */
    private final RefundStatus refundStatus;

    /** 创建时冻结的退款原因。 */
    private final String reasonCode;

    /** 失败或取消原因，不适用时为空。 */
    private final String statusReasonCode;

    /** 退款创建时刻。 */
    private final Instant createdAt;

    /** 终态完成时刻，未决时为空。 */
    private final Instant completedAt;

    /** 当前退款状态的更新时间。 */
    private final Instant updatedAt;

    /** 既有退款查询合同的表示版本。 */
    private final long version;
}
