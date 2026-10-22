package com.paytech.acquiring.domain.order;

import java.time.Duration;
import java.time.Instant;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.domain.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 过期收尾上下文，持有本次读取的订单事实及基于这些事实的纯判断；写入时仍由条件 SQL 保护。 */
@Getter
@AllArgsConstructor
public class OrderExpiryContext {
    /** 订单事实；数据库更新成功后同步版本、状态和时间。 */
    private final CommerceOrder order;

    /** 实际关联的会话；Direct Pay 未关联 Checkout 时为空。 */
    private final CheckoutSession session;

    /** 本次读取的支付投影；尚未绑定 Intent 时为空。 */
    private final OrderPaymentProjection payment;

    /** 已保存接受证据，不能排除在途 Intent 创建。 */
    private final boolean paymentStarted;

    /** 本轮扫描判断到期与告警的时刻。 */
    private final Instant now;

    /** 订单已进入终态或已有支付成功时刻，过期收尾不再处理。 */
    public boolean isClosed() {
        return !order.statusIn(OrderStatus.CREATED, OrderStatus.OPEN) || order.getPaidAt() != null;
    }

    /** 本轮时刻不早于订单原到期时刻。 */
    public boolean isDue() {
        return !order.getExpiresAt().isAfter(now);
    }

    /**
     * 判断能否不经 Payment 直接本地过期。
     * 既没有支付投影也没有接受证据时不可能存在 Intent；不能仅凭投影缺失推断没有远端支付。
     */
    public boolean canExpireLocally() {
        return payment == null && !paymentStarted;
    }

    /** 已有 Intent 时终止请求的 Intent ID；没有投影时由终止客户端按来源定位。 */
    public Long paymentIntentId() {
        return payment == null ? null : payment.getPaymentIntentId();
    }

    /**
     * 终止仍未决的时长是否已超过告警阈值
     *
     * @param threshold 自首次请求终止起允许等待的时长
     * @return 已写终止意图且早于本轮时刻减阈值时为 true
     */
    public boolean isPendingBeyond(Duration threshold) {
        var requestedAt = order.getTerminationRequestedAt();
        return requestedAt != null && requestedAt.isBefore(now.minus(threshold));
    }
}
