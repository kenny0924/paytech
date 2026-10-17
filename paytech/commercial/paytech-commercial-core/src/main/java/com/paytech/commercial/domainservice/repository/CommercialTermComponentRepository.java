package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.CommercialTermComponent;

/** t_commercial_term_component 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermComponentRepository {
    /** 读取本 Client Checkout 范围内的 commercial_term_component 记录，保留历史和未生效事实供业务判断。 */
    List<CommercialTermComponent> findForCheckout(long clientId);
}
