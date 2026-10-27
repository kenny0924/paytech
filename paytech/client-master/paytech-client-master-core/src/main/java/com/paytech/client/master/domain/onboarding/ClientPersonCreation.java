package com.paytech.client.master.domain.onboarding;
import java.time.Instant;
/** 新关联人员的稳定内部身份；资料采集辖区跟随申请。 */
public record ClientPersonCreation(long applicationId,long partyId,long profileId,long addressId,long identifierId,
    String jurisdictionCode,Instant now) {
    public String partyNo() { return "PT-"+partyId; }
}
