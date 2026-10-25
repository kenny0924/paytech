package com.paytech.client.master.domainservice.repository;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntityScope;

/** t_operating_entity_scope 的本地仓储；不负责其他表的加载或事务。 */
public interface OperatingEntityScopeRepository {
    /** 读取本 Client Checkout 范围内的 operating_entity_scope 记录，保留历史和未生效事实供业务判断。 */
    List<OperatingEntityScope> findForCheckout(long clientId);

    /** 读取一个 OE 的全部准入范围。 */
    List<OperatingEntityScope> findByEntityNo(String entityNo);
}
