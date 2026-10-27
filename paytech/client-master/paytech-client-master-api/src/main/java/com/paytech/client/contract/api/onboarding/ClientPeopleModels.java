package com.paytech.client.contract.api.onboarding;
import java.util.List;
import java.util.Map;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
/** 组织人员不是登录账号；写入只返回固定回执，当前资料通过 C05 读取。 */
public final class ClientPeopleModels {
    private ClientPeopleModels() {}
    /**
     * 新建人员时一起创建的关系
     *
     * @param relationType 关系类型目录代码（form-schema 的 relationTypes）
     * @param ownershipPercentage 持股比例，仅 ownershipPctRequired 的类型可填
     * @param effectiveFrom 生效日期 YYYY-MM-DD
     * @param effectiveTo 结束日期 YYYY-MM-DD，可空
     * @param uboBasis UBO 认定依据 OWNERSHIP、CONTROL、SENIOR_MANAGING_OFFICIAL，仅 uboFlag 的类型可填
     * @param jobTitle 职务，可空
     * @param nomineeFlag 是否名义持有，true/false，可空
     * @param controlNature 控制方式说明，可空
     */
    public record RelationFields(String relationType,String ownershipPercentage,String effectiveFrom,String effectiveTo,
        String uboBasis,String jobTitle,String nomineeFlag,String controlNature) {}
    public record CreatePersonRequestBody(DraftPatchRequestBody person,RelationFields relation) {
        @Override public String toString() { return "CreatePersonRequestBody[redacted]"; }
    }
    /** 为已有人员新建关系；字段同 {@link RelationFields}。 */
    public record CreateRelationRequestBody(String relatedPartyId,String relationType,String ownershipPercentage,String effectiveFrom,
        String effectiveTo,String uboBasis,String jobTitle,String nomineeFlag,String controlNature) {}
    public record RelationPatchRequestBody(List<String> dirtyFields,Map<String,String> values) {}
    public record PeopleCommandReceiptResponseBody(String commandId,String applicationId,String partyId,
        String relationId,String operation,String completedAt) {}
    public record PersonResponseBody(String partyId,boolean editable,Map<String,Map<String,String>> values) {
        @Override public String toString() { return "PersonResponseBody[redacted]"; }
    }
    public record RelationResponseBody(String relationId,String relatedPartyId,String relationType,
        String ownershipPercentage,String effectiveFrom,String effectiveTo,String status,boolean editable,
        String uboBasis,String jobTitle,Boolean nomineeFlag,String controlNature) {}
}
