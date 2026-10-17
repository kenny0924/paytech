package com.paytech.commerce.core.dal.mapper.customer;

import com.paytech.commerce.core.dal.dataobject.customer.CheckoutNetworkEvidence;

import org.apache.ibatis.annotations.Param;

/** CheckoutNetworkEvidence 的 XML SQL 入口。 */
public interface CheckoutNetworkEvidenceMapper {
    /** 只读取证据的订单、会话和场景，不加载网络原值。 */
    CheckoutNetworkEvidence findBindingById(@Param("evidenceId") long evidenceId);

    /** 保存服务端采集的法律接受证据，不包含卡信息。 */
    int insert(CheckoutNetworkEvidence evidence);
}
