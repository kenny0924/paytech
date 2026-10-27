package com.paytech.client.master.core.dal.dataobject;
import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.client.master.domain.enums.*;
/**
 * 组织与人员的一条关系（t_party_relation 的入驻读取列）。
 *
 * @param relationType 关系类型目录代码（t_relation_type），开放集合
 */
public record ClientPartyRelation(long relationId,long relatedPartyId,String relationType,
    BigDecimal ownershipPercentage,Instant effectiveFrom,Instant effectiveTo,PartyRelationStatus status,
    String uboBasis,String jobTitle,Boolean nomineeFlag,String controlNature) {}
