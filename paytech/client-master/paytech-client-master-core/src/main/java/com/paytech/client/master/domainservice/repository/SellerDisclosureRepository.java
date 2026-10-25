package com.paytech.client.master.domainservice.repository;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.SellerDisclosure;

/** t_seller_disclosure 的本地仓储；不负责其他表的加载或事务。 */
public interface SellerDisclosureRepository {
    /** 读取本 Client Checkout 范围内的 seller_disclosure 记录，保留历史和未生效事实供业务判断。 */
    List<SellerDisclosure> findForCheckout(long clientId);

    /** 读取一个 OE 的全部卖方披露。 */
    List<SellerDisclosure> findByEntityNo(String entityNo);
}
