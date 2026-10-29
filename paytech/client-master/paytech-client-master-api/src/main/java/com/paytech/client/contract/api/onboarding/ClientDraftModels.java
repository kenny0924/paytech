package com.paytech.client.contract.api.onboarding;
import java.util.List;
import java.util.Map;
/** CCT 自有主表单合同；不包含 Risk 任务、文件授权或 BFF 页面聚合。 */
public final class ClientDraftModels {
    private ClientDraftModels() {}
    /** 最近申请摘要；不包含客户资料、文件或工作区权限。
     * @param applications 原账号最近申请，活动申请优先，最多 20 项
     * @param hasMore 历史超过显示上限，不代表可以新建申请
     */
    public record StartResponseBody(List<ApplicationSummary> applications, boolean hasMore) {}
    /** 稳定申请导航，主主体 ID 由 CCT 返回，不能用 clientId 替代。
     * @param active 是否仍占用账号的未完成申请名额
     */
    public record ApplicationSummary(String applicationId, String applicationNo, String clientType,
        String status, boolean active, String savedAt) {}
    /**
     * C02 开始新申请
     *
     * @param clientType INDIVIDUAL 或 ORGANIZATION
     * @param jurisdictionCode 辖区代码（jurisdictions 接口返回的 ACTIVE 辖区）
     * @param organizationTypeCode 组织类型代码，可空；个人只在选择个人经营形态（如 AE SOLE_ESTABLISHMENT）时填写
     * @param onboardingCode PayFac 线下发放的入驻码 XXXX-XXXX，可空；无码为平台自营
     */
    public record StartApplicationRequestBody(String clientType, String jurisdictionCode, String organizationTypeCode,
        String onboardingCode) {
        @Override public String toString() { return "StartApplicationRequestBody[redacted]"; }
    }
    /**
     * values 的叶字段与 dirtyFields 精确对应，null 表示清空。
     * <li>business.expectedPaymentMethods 为英文逗号分隔的代码，服务端规范化为大写、去重、按代码升序（最多 20 项）；
     * business.refundPolicyUrl 只收 https；business.fulfillmentDays 为整数，仅交付方式为 PRE_ORDER 时可填，交付方式改为其他值时服务端同时清空。</li>
     */
    public record DraftPatchRequestBody(List<String> dirtyFields, Map<String, Map<String, String>> values) {
        @Override public String toString() { return "DraftPatchRequestBody[redacted]"; }
    }
    public record CommandReceiptResponseBody(String commandId, String applicationId, String applicationNo,
        String clientId, String clientType, String operation, String completedAt) {}
    /**
     * C05 申请资料
     * <li>values 分区：individual / organization / address / identifier / business / contact / attributes；identifier 只给
     * identifierType、issuingCountry、identifierMasked（末 4 位）、identifierCheckResult、issueDate、expiryDate，不回显号码原值。</li>
     * <li>business 另含 expectedPaymentMethods（规范化逗号串，如 APPLE_PAY,CARD）、refundPolicyUrl、fulfillmentType、fulfillmentDays（十进制字符串）；
     * 未填为 null。</li>
     * <li>jurisdictionCode、organizationTypeCode、schemaId 决定 form-schema；reviewDueTime 为退回补件截止时间（可空）。</li>
     * <li>responsiblePartnerId / responsiblePartnerName 为入驻码冻结的责任 PayFac 及其法定名称，平台自营为空。</li>
     * <li>declarations 为每种声明的最新一条（声明属于申请，重新申请须重签）。</li>
     * <li>draftRevision 为申请资料版本（十进制字符串），每次实际改动草稿加一；声明的 draftRevision 与之不等即失效。</li>
     */
    public record ClientDraftResponseBody(String applicationId, String applicationNo, String clientId,
        String clientType, String subjectPartyId, String status, String savedAt, boolean editable, Map<String, Map<String, String>> values,
        List<ClientPeopleModels.PersonResponseBody> people,List<ClientPeopleModels.RelationResponseBody> relations,
        List<ClientDocumentModels.DocumentResponseBody> documents, String jurisdictionCode, String organizationTypeCode,
        String schemaId, String reviewDueTime, String responsiblePartnerId, String responsiblePartnerName,
        List<com.paytech.client.master.api.onboarding.response.DeclarationResponseBody> declarations, String draftRevision) {
        @Override public String toString() { return "ClientDraftResponseBody[redacted]"; }
    }
}
