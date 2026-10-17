package com.paytech.commerce.service.processor.customer.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成保存的最小结果，不返回数据库对象或消费者原值。 */
@Getter
@AllArgsConstructor
public class SaveConsumerResponse {
    /** 原订单公开编号。 */
    private final String orderNo;

    /** 保存结果状态。 */
    private final String consumerStatus;
}
