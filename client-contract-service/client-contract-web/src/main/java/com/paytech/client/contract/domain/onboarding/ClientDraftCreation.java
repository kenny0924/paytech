package com.paytech.client.contract.domain.onboarding;
import java.time.Instant;
import com.paytech.client.contract.domain.enums.ClientType;
/** 同一原子创建使用的稳定内部身份，不承担资料版本。 */
public record ClientDraftCreation(long applicationId, long clientId, long partyId, long profileId,
    long businessProfileId, long addressId, long identifierId, long historyId, long changeId,
    long commandRecordId, long accountId, ClientType type, Instant now) {
    public String applicationNo() { return "CA-"+applicationId; }
    public String clientNo() { return "CL-"+clientId; }
    public String partyNo() { return "PT-"+partyId; }
    public String qualificationType() { return type==ClientType.INDIVIDUAL?"INDIVIDUAL_KYC":"ORGANIZATION_KYB"; }
    public String addressType() { return type==ClientType.INDIVIDUAL?"RESIDENTIAL":"REGISTERED"; }
}
