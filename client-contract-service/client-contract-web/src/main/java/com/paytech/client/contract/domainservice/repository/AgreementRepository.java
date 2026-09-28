package com.paytech.client.contract.domainservice.repository;

import java.util.List;

import com.paytech.client.contract.core.dal.dataobject.CheckoutRecords.Agreement;

/** t_agreement 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementRepository {
    /** 读取本 Client Checkout 范围内的 agreement 记录，保留历史和未生效事实供业务判断。 */
    List<Agreement> findForCheckout(long clientId);
}
