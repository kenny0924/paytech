package com.paytech.commerce.core.dal.dataobject.customer;

import lombok.Getter;
import lombok.Setter;

/** 普通查询返回前重验版本，不重新加载完整事实。 */
@Getter
@Setter
public class CheckoutVersionView {
    /** 订单状态及冻结事实发生变化时递增。 */
    private long orderVersion;
    /** Session 生命周期版本。 */
    private long sessionVersion;
    /** 消费者资料版本；尚未保存时为空。 */
    private Long consumerVersion;
}
