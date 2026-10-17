package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.EntitySourceRelation;

/** t_entity_source_relation 的持久化查询。 */
public interface EntitySourceRelationMapper {
    /** 读取本 Client Checkout 范围内的 entity_source_relation 记录，保留历史和未生效事实供业务判断。 */
    List<EntitySourceRelation> findForCheckout(@Param("clientId") long clientId);
}
