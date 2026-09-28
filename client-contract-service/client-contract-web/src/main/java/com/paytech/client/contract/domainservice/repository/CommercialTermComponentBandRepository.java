package com.paytech.client.contract.domainservice.repository;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.CommercialTermComponentBand;

/** t_commercial_term_component_band 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermComponentBandRepository {
    /** 读取本 Client Checkout 范围内的 commercial_term_component_band 记录，保留历史和未生效事实供业务判断。 */
    List<CommercialTermComponentBand> findForCheckout(long clientId);
}
