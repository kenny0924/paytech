package com.paytech.billing.integration.channelconnector;

import com.paytech.billing.domain.ChannelCostResolution;
import com.paytech.billing.domain.OrderPaymentSucceededFact;

/** 命中 channel-connector 渠道成本方案；必须在数据库事务外调用。 */
public interface ChannelCostClient {

    /**
     * 按支付成功的 Channel、MID、OE、消费者国家、币种、支付方式与成功时间命中 PAY 动作的成本组件
     *
     * @param payment 订单支付成功事实（消费者国家已确认非空）
     * @return 命中的方案版本、范围与组件
     * @throws com.paytech.foundation.common.error.ErrorException 未命中、命中不唯一、上游不可用或响应无效；事实停在重试
     */
    ChannelCostResolution resolve(OrderPaymentSucceededFact payment);
}
