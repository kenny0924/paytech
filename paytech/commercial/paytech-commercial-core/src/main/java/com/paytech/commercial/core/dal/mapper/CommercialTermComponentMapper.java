package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermComponent;

/** t_commercial_term_component 的持久化查询。 */
public interface CommercialTermComponentMapper {
    /** 读取本 Client Checkout 范围内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    List<CommercialTermComponent> findForCheckout(@Param("clientId") long clientId);
}
