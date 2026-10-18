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
    public record StartApplicationRequestBody(String clientType) {}
    /** values 的叶字段与 dirtyFields 精确对应，null 表示清空。 */
    public record DraftPatchRequestBody(List<String> dirtyFields, Map<String, Map<String, String>> values) {
        @Override public String toString() { return "DraftPatchRequestBody[redacted]"; }
    }
    public record CommandReceiptResponseBody(String commandId, String applicationId, String applicationNo,
        String clientId, String clientType, String operation, String completedAt) {}
    public record ClientDraftResponseBody(String applicationId, String applicationNo, String clientId,
        String clientType, String subjectPartyId, String status, String savedAt, boolean editable, Map<String, Map<String, String>> values,
        List<ClientPeopleModels.PersonResponseBody> people,List<ClientPeopleModels.RelationResponseBody> relations,
        List<ClientDocumentModels.DocumentResponseBody> documents) {
        @Override public String toString() { return "ClientDraftResponseBody[redacted]"; }
    }
}
