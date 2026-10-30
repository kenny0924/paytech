package com.paytech.acquiring.core.dal.dataobject.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 消费者合同的支付绑定字段。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerContract {
    /** 不可变消费者合同主键。 */
    private long consumerContractId;

    /** 合同所属订单。 */
    private long orderId;

    /** 同一订单上下文下的法律事实主键。 */
    private long orderLegalId;

    /**
     * 合同渲染所依据的 tax 计算
     * <li>历史订单为空。</li>
     */
    private Long taxCalculationId;
}

