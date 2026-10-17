package com.paytech.client.master.core.dal.dataobject;
import java.time.Instant;
import java.math.BigDecimal;
import com.paytech.client.master.domain.enums.*;
public record ClientPartyRelation(long relationId,long relatedPartyId,PartyRelationType relationType,
    BigDecimal ownershipPercentage,Instant effectiveFrom,Instant effectiveTo,PartyRelationStatus status) {}
