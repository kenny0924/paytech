package com.paytech.commerce.domainservice.repository.customer;

import com.paytech.commerce.core.dal.dataobject.customer.CheckoutNetworkEvidence;

/** CheckoutNetworkEvidence 的本库持久化边界。 */
public interface CheckoutNetworkEvidenceRepository {
    /** 只读取法律接受证据的身份、订单、会话和场景，不加载网络原值。 */
    CheckoutNetworkEvidence findBindingById(long evidenceId);

    /** 保存服务端采集的法律接受证据，不包含卡信息。 */
    void insert(CheckoutNetworkEvidence evidence);
}
