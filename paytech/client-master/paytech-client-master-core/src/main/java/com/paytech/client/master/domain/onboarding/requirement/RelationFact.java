package com.paytech.client.master.domain.onboarding.requirement;

import java.math.BigDecimal;

/**
 * 组织与关联人员的一条进行中关系
 *
 * @param relatedPartyId 关联人员 Party ID
 * @param relationTypeCode 关系类型代码
 * @param ownershipPercentage 持股比例，可空
 * @param uboBasis UBO 认定依据，可空
 */
public record RelationFact(long relatedPartyId, String relationTypeCode, BigDecimal ownershipPercentage, String uboBasis) {
}
