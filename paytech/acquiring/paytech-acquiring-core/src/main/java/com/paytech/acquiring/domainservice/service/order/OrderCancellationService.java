package com.paytech.commerce.domainservice.service.order;

import com.paytech.commerce.domain.order.OrderCancellationContext;

/** 订单取消的本地保存能力，已有事务时加入，否则以 REQUIRED 创建事务。 */
public interface OrderCancellationService {
    /** 保存终止意图；已有意图保留原时间，不重复写入。 */
    void requestTermination(OrderCancellationContext context);

    /** 同一事务保存必要的终止意图并关闭 Order 及实际关联的 Session，任一 CAS 失败则整体回滚。 */
    void cancel(OrderCancellationContext context);
}
