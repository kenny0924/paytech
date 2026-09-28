package com.paytech.client.contract.api.onboarding;
/** 申请材料只保存经 Owner 核验的引用，不保存客户端 Hash 或文件内容。 */
public final class ClientDocumentModels {
    /** 仅作为模型容器。 */
    private ClientDocumentModels() {}
    /**
     * @param subjectPartyId 当前申请主主体或当前关联人员
     * @param requirementCode Risk 材料要求代码
     * @param documentType Risk 指定的材料类型
     * @param fileId 原申请与主体的 Platform 文件
     */
    public record DocumentReferenceRequestBody(String subjectPartyId,String requirementCode,String documentType,String fileId) {}
    /**
     * @param commandId 原命令 UUID
     * @param applicationId 原申请
     * @param documentId 唯一材料引用
     * @param subjectPartyId 原主体
     * @param fileId 原文件
     * @param operation 固定 ASSOCIATED
     * @param completedAt 首次成功时间
     */
    public record DocumentCommandReceiptResponseBody(String commandId,String applicationId,String documentId,String subjectPartyId,String fileId,String operation,String completedAt) {}
    /**
     * @param documentId 当前材料引用
     * @param subjectPartyId 原材料主体
     * @param requirementCode 原材料要求
     * @param documentType 原材料类型
     * @param fileId 原文件
     * @param subjectInScope 主体是否仍在当前申请范围内
     */
    public record DocumentResponseBody(String documentId,String subjectPartyId,String requirementCode,String documentType,String fileId,boolean subjectInScope) {}
}
