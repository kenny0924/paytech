package com.paytech.commerce.domainservice.repository.checkout;

import java.util.List;
import java.util.Optional;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutCreationView;
import com.paytech.commerce.core.dal.dataobject.order.OrderItem;

/** Order 与 Hosted Checkout 首片持久化边界。 */
public interface CheckoutRepository {

    /** 准确读取新格式 Order 与 Session；锁读取只在调用方短事务内使用。 */
    Optional<CheckoutCreationView> findBasic(long clientId, long orderId, String orderNo, boolean lock);

    /** 读取基础商品事实，税额仍由后续 Finance Snapshot 拥有。 */
    List<OrderItem> findBasicItems(long clientId, long orderId);

}
