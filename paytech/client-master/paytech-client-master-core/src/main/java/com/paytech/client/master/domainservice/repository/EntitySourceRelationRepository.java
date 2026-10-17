package com.paytech.client.master.domainservice.repository;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.EntitySourceRelation;

/** t_entity_source_relation 的本地仓储；不负责其他表的加载或事务。 */
public interface EntitySourceRelationRepository {
    /** 读取本 Client Checkout 范围内的 entity_source_relation 记录，保留历史和未生效事实供业务判断。 */
    List<EntitySourceRelation> findForCheckout(long clientId);
}
