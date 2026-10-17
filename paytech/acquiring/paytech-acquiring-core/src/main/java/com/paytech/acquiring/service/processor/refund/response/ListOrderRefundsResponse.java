package com.paytech.commerce.service.processor.refund.response;

import java.time.Instant;
import java.util.List;

import com.paytech.commerce.domain.enums.RefundRequestOrigin;
import com.paytech.commerce.domain.enums.RefundStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已校验的退款查询页，只包含对外查询需要的业务结果。 */
@Getter
@AllArgsConstructor
public class ListOrderRefundsResponse {
    /** 按创建时间和退款号倒序排列的当前页。 */
    private final List<Refund> items;

    /** 当前页最后一条退款的游标；没有更多数据时为空。 */
    private final String nextCursor;

    /** 单条退款的安全查询结果，不携带内部主键、渠道身份或原因原文。 */
    @Getter
    @AllArgsConstructor
    public static class Refund {
        /** 公开退款号。 */
        private final String refundRequestNo;

        /** Client 业务退款号；Operations 来源为空。 */
        private final String clientRefundId;

        /** 创建来源。 */
        private final RefundRequestOrigin requestOrigin;

        /** 公开订单号。 */
        private final String orderNo;

        /** 已按币种精度输出的主单位退款金额。 */
        private final String amount;

        /** 退款币种。 */
        private final String currency;

        /** 当前退款状态。 */
        private final RefundStatus refundStatus;

        /** 创建时冻结的退款原因。 */
        private final String reasonCode;

        /** 失败或取消原因，不适用时为空。 */
        private final String statusReasonCode;

        /** 创建时刻。 */
        private final Instant createdAt;

        /** 终态完成时刻，未决时为空。 */
        private final Instant completedAt;

        /** 本次观察到的状态更新时间。 */
        private final Instant updatedAt;

        /** 既有退款查询合同的表示版本。 */
        private final long version;
    }
}
