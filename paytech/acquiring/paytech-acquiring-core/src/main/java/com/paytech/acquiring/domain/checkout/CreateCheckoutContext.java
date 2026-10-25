package com.paytech.acquiring.domain.checkout;

import java.util.List;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 创建 Checkout 的本地上下文，不承担请求幂等和恢复职责。 */
@Getter
@AllArgsConstructor
public class CreateCheckoutContext {
    /** 创建时一次取得的冻结商业、税务和法律事实。 */
    @Setter
    private FrozenCheckoutRecord frozen;

    /** 与冻结事实同时取得的商业关系角色绑定；固定 Mock 解析时为空集合。 */
    @Setter
    private List<OrderRelationshipBinding> relationshipBindings;

    /** 订单创建时的基础事实。 */
    private final CommerceOrder order;
    /** 原顺序商品事实，不包含后续计税金额。 */
    private final List<OrderItem> items;
    /** 初始退款仓位。 */
    private final OrderRefundPosition refundPosition;
    /** 初始资金可用投影。 */
    private final OrderFundsAvailabilityProjection fundsAvailability;
    /** 会话事实，持有创建时一次生成的 Token。 */
    private final CheckoutSession session;
}
