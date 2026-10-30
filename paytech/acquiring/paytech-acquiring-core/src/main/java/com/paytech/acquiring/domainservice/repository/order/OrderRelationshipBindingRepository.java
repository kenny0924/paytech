package com.paytech.acquiring.domainservice.repository.order;

import java.util.List;

import com.paytech.acquiring.core.dal.dataobject.order.OrderRelationshipBinding;

/** OrderRelationshipBinding 的单表持久化边界。 */
public interface OrderRelationshipBindingRepository {
    /** 插入同一订单的全部角色绑定，参与调用方本地事务；空集合不执行 SQL。 */
    void insertAll(List<OrderRelationshipBinding> bindings);

    /** 读取订单冻结的全部角色绑定；固定 Mock 未给快照或历史订单为空集合。 */
    List<OrderRelationshipBinding> findByOrderId(long orderId);
}
