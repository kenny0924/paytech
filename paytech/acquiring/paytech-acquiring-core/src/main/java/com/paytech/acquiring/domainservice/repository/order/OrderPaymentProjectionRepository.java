package com.paytech.acquiring.domainservice.repository.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;

/** 订单支付投影表的查询边界。 */
public interface OrderPaymentProjectionRepository {
    /** 返回前只核对本地支付版本；未绑定 Intent 时为空。 */
    Long findVersionByOrderId(long orderId);

    /** 读取取消判断所需的支付事实；尚无投影时返回空。 */
    OrderPaymentProjection findByOrderId(long orderId);
    /** 保存原订单的唯一 Intent 关联。 */
    void insert(OrderPaymentProjection payment);

    /**
     * 记录支付成功事件：意图与最后尝试均为成功，写入成功尝试、已捕获金额、事件键与载荷 Hash
     * <li>以投影版本和尚无成功尝试为条件；不满足时抛出并发冲突，成功后同步原对象。</li>
     */
    void recordSucceeded(OrderPaymentProjection payment, long attemptId, BigDecimal capturedAmount, String eventKey,
        String payloadHash, long sourceVersion, Instant eventTime, Instant now);

    /**
     * 记录 Intent 已因订单过期终止：意图状态写为 EXPIRED
     * <li>以投影版本、无成功尝试且未捕获为条件；不满足时抛出并发冲突，成功后同步原对象。</li>
     */
    void recordTerminated(OrderPaymentProjection payment, Instant now);
}
