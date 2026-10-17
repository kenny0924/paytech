package com.paytech.client.master.domain.onboarding;
import java.time.Instant;
public record ClientPersonCreation(long applicationId,long partyId,long profileId,long addressId,long identifierId,Instant now) {
    public String partyNo() { return "PT-"+partyId; }
}
