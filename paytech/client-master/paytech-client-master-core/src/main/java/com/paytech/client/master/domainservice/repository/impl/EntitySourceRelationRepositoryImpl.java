package com.paytech.client.master.domainservice.repository.impl;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.EntitySourceRelation;
import com.paytech.client.master.core.dal.mapper.EntitySourceRelationMapper;
import com.paytech.client.master.domain.entityadmin.ProvidedEntityView;
import com.paytech.client.master.domainservice.repository.EntitySourceRelationRepository;

import lombok.AllArgsConstructor;

/** 只通过 EntitySourceRelationMapper 读取 t_entity_source_relation。 */
@AllArgsConstructor
public class EntitySourceRelationRepositoryImpl implements EntitySourceRelationRepository {
    private final EntitySourceRelationMapper mapper;

    /** 读取本 Client Checkout 范围内的 entity_source_relation 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<EntitySourceRelation> findForCheckout(long clientId) {
        return List.copyOf(mapper.findForCheckout(clientId));
    }

    /** 读取 Local Partner 以 PARTNER 来源提供（含已结束）的经营主体。 */
    @Override
    public List<ProvidedEntityView> findProvidedByPartner(long localPartnerId) {
        return List.copyOf(mapper.findProvidedByPartner(localPartnerId));
    }

    /** ACTIVE 来源关系数大于 0。 */
    @Override
    public boolean isProvidingActive(long localPartnerId) {
        return mapper.countActiveByPartner(localPartnerId) > 0;
    }
}
