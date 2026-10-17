package com.paytech.commerce.domain.order;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 取消上下文，持有本次业务事实及基于这些事实的纯判断。 */
@Getter
@AllArgsConstructor
public class OrderCancellationContext {
    /** 订单事实；数据库更新成功后同步版本、状态和时间。 */
    private final CommerceOrder order;

    /** 实际关联的会话；Direct Pay 未关联 Checkout 时为空。 */
    private final CheckoutSession session;

    /** 本次读取的支付投影；已取消订单无需查询，尚未绑定 Intent 时也为空。 */
    private final OrderPaymentProjection payment;

    /** 已保存接受证据，不能排除在途 Intent 创建。 */
    private final boolean paymentStarted;

    /**
     * 判断当前已加载事实是否允许进入本地取消分支。
     * 未受理过支付的订单可本地取消，不能仅凭投影缺失推断没有远端支付；写入时仍由 SQL 条件保护并发事实。
     */
    public boolean canCancelLocally() {
        return payment == null && !paymentStarted && order.getPreparationCommandId() == null;
    }
}
