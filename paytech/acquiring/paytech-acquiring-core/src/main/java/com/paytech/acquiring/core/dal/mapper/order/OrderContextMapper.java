package com.paytech.acquiring.core.dal.mapper.order;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.domain.customer.CheckoutLegalView;

/** 冻结上下文关联的法律展示查询。 */
public interface OrderContextMapper {
    /** 返回上下文、法律事实与消费者合同的有限展示字段。 */
    CheckoutLegalView findCheckoutLegal(@Param("orderId") long orderId);
}
