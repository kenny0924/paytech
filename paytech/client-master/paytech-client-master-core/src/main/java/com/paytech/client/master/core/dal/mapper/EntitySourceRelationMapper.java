package com.paytech.client.master.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.EntitySourceRelation;
import com.paytech.client.master.domain.entityadmin.ProvidedEntityView;

/** t_entity_source_relation 的持久化查询。 */
public interface EntitySourceRelationMapper {
    /** 读取本 Client Checkout 范围内的 entity_source_relation 记录，保留历史和未生效事实供业务判断。 */
    List<EntitySourceRelation> findForCheckout(@Param("clientId") long clientId);

    /** 读取 Local Partner 以 PARTNER 来源提供（含已结束）的经营主体，按 entityNo 与生效时间升序。 */
    List<ProvidedEntityView> findProvidedByPartner(@Param("localPartnerId") long localPartnerId);

    /** Local Partner 当前 ACTIVE 的 PARTNER 来源关系数。 */
    long countActiveByPartner(@Param("localPartnerId") long localPartnerId);
}
