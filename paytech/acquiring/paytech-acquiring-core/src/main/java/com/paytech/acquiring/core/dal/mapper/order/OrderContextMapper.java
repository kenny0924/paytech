package com.paytech.acquiring.core.dal.mapper.order;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.domain.customer.CheckoutLegalView;
import com.paytech.acquiring.domain.order.OrderCommercialContextView;

/** 冻结上下文关联的法律展示查询。 */
public interface OrderContextMapper {
    /** 返回上下文、法律事实与消费者合同的有限展示字段。 */
    CheckoutLegalView findCheckoutLegal(@Param("orderId") long orderId);

    /** 以订单为主表左连接冻结上下文与法律事实，只返回商业字段；订单不存在时返回 null。 */
    OrderCommercialContextView findCommercialContext(@Param("orderId") long orderId);
}
