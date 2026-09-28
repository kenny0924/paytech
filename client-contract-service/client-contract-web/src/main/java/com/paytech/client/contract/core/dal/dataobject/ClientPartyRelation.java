package com.paytech.client.contract.core.dal.dataobject;
import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.client.contract.domain.enums.*;
public record ClientPartyRelation(long relationId,long relatedPartyId,PartyRelationType relationType,
    BigDecimal ownershipPercentage,Instant effectiveFrom,Instant effectiveTo,PartyRelationStatus status) {}
