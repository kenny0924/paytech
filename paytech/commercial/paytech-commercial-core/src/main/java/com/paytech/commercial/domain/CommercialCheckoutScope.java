package com.paytech.commercial.domain;

import java.util.List;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 商务查询的显式范围（design/06 §3），替代原来对 t_client_entity_affiliation、t_entity_source_relation 的子查询。
 * <li>entityNos 为该 Client 全部 Affiliation 的 OE；localPartnerIds 为这些 OE 的全部 PARTNER 来源；均由 client-master 返回。</li>
 */
@Value
@Accessors(fluent = true)
public class CommercialCheckoutScope {
    /** Client ID。 */
    long clientId;
    /** 该 Client 全部 Affiliation 的 OE 编号。 */
    List<String> entityNos;
    /** 上述 OE 的全部 PARTNER 来源 Local Partner ID。 */
    List<Long> localPartnerIds;

    /** 冻结集合结构，空集合合法。 */
    public CommercialCheckoutScope(long clientId, List<String> entityNos, List<Long> localPartnerIds) {
        this.clientId = clientId;
        this.entityNos = List.copyOf(entityNos);
        this.localPartnerIds = List.copyOf(localPartnerIds);
    }
}
