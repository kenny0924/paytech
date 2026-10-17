package com.paytech.client.contract.api.onboarding;
import java.util.List;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
/** 组织人员不是登录账号；写入只返回固定回执，当前资料通过 C05 读取。 */
public final class ClientPeopleModels {
    private ClientPeopleModels() {}
    public record RelationFields(String relationType,String ownershipPercentage,String effectiveFrom,String effectiveTo) {}
    public record CreatePersonRequestBody(DraftPatchRequestBody person,RelationFields relation) {
        @Override public String toString() { return "CreatePersonRequestBody[redacted]"; }
    }
    public record CreateRelationRequestBody(String relatedPartyId,String relationType,String ownershipPercentage,String effectiveFrom,String effectiveTo) {}
    public record RelationPatchRequestBody(List<String> dirtyFields,Map<String,String> values) {}
    public record PeopleCommandReceiptResponseBody(String commandId,String applicationId,String partyId,
        String relationId,String operation,String completedAt) {}
    public record PersonResponseBody(String partyId,boolean editable,Map<String,Map<String,String>> values) {
        @Override public String toString() { return "PersonResponseBody[redacted]"; }
    }
    public record RelationResponseBody(String relationId,String relatedPartyId,String relationType,
        String ownershipPercentage,String effectiveFrom,String effectiveTo,String status,boolean editable) {}
}
