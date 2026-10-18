package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.SellerDisclosure;

/** t_seller_disclosure 的持久化查询。 */
public interface SellerDisclosureMapper {
    /** 读取本 Client Checkout 范围内的 seller_disclosure 记录，保留历史和未生效事实供业务判断。 */
    List<SellerDisclosure> findForCheckout(@Param("clientId") long clientId);
}
