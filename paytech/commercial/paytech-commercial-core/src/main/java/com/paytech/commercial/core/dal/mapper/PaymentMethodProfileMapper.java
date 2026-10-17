package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.PaymentMethodProfile;

/** t_payment_method_profile 的持久化查询。 */
public interface PaymentMethodProfileMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 payment_method_profile 记录，保留历史和未生效事实供业务判断。 */
    List<PaymentMethodProfile> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);
}
