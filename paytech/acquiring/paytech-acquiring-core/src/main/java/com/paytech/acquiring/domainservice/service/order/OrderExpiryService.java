package com.paytech.acquiring.domainservice.service.order;

import com.paytech.acquiring.domain.order.OrderExpiryContext;

/** 订单过期收尾的本地保存能力，已有事务时加入，否则以 REQUIRED 创建事务。 */
public interface OrderExpiryService {
    /** 保存终止意图，阻止新的支付提交；已有意图保留原时间，不重复写入。 */
    void requestTermination(OrderExpiryContext context);

    /**
     * 同一事务保存必要的终止意图，关闭 Order、实际关联的 Session，终止已有的支付投影并登记 order.expired
     * <li>任一 CAS 失败则整体回滚，不自动重试。</li>
     */
    void expire(OrderExpiryContext context);
}
