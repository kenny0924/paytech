package com.paytech.acquiring.core.dal.mapper.order;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;

/** 订单税务计算表的追加写入与当前报价查询。 */
public interface OrderTaxCalculationMapper {
    /** 插入一条计算记录，返回插入行数。 */
    int insert(@Param("row") OrderTaxCalculation row);

    /** 读取订单最大 revision 的计算；没有计算行（存量订单）时返回 null。 */
    OrderTaxCalculation findLatestByOrderId(@Param("orderId") long orderId);
}
