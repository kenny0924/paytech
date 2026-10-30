package com.paytech.acquiring.domainservice.repository.order;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;

/** OrderTaxCalculation 的单表持久化边界。 */
public interface OrderTaxCalculationRepository {
    /** 追加一条计算记录，参与调用方本地事务；同订单同 revision 或同一 tax 计算重复时唯一键冲突原样抛出。 */
    void insert(OrderTaxCalculation row);

    /** 读取订单当前有效报价（最大 revision）；存量订单没有计算行时返回 null。 */
    OrderTaxCalculation findLatestByOrderId(long orderId);
}
