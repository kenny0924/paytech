package com.paytech.acquiring.core.dal.dataobject.customer;

/** 由本订单上下文与法律记录连接得到的合同绑定，不读取展示字段。 */
public record CheckoutContractView(
    /** 原子冻结的订单上下文主键。 */
    long orderContextId,
    /** 同订单、同法律记录下的消费者合同主键。 */
    long consumerContractId) {}
