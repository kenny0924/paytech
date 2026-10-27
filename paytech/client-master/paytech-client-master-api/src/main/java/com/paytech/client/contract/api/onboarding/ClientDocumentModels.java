package com.paytech.client.contract.api.onboarding;
/** 申请材料只保存经 Owner 核验的引用，不保存客户端 Hash 或文件内容。 */
public final class ClientDocumentModels {
    /** 仅作为模型容器。 */
    private ClientDocumentModels() {}
    /**
     * @param subjectPartyId 当前申请主主体或当前关联人员
     * @param requirementCode Risk 材料要求代码
     * @param documentType Risk 指定的材料类型
     * @param fileAddress 当前账号上传的 Platform 文件地址，格式 /files/v1/{fileId}
     */
    /**
     * C12 关联材料
     *
     * @param issueDate 材料签发日期 YYYY-MM-DD，可空；程序 maxAgeDays 按它校验
     * @param expiryDate 材料到期日 YYYY-MM-DD（含当日有效），可空
     */
    public record DocumentReferenceRequestBody(String subjectPartyId,String requirementCode,String documentType,String fileAddress,
        String issueDate,String expiryDate) {}
    /**
     * @param commandId 已停用，恒为空；请求幂等由 Gateway 处理
     * @param applicationId 原申请
     * @param documentId 唯一材料引用
     * @param subjectPartyId 原主体
     * @param fileAddress 原文件地址
     * @param operation 固定 ASSOCIATED
     * @param completedAt 首次成功时间
     */
    public record DocumentCommandReceiptResponseBody(String commandId,String applicationId,String documentId,String subjectPartyId,String fileAddress,String operation,String completedAt) {}
    /**
     * @param documentId 当前材料引用
     * @param subjectPartyId 原材料主体
     * @param requirementCode 原材料要求
     * @param documentType 原材料类型
     * @param fileAddress 原文件地址
     * @param subjectInScope 主体是否仍在当前申请范围内
     */
    public record DocumentResponseBody(String documentId,String subjectPartyId,String requirementCode,String documentType,String fileAddress,
        boolean subjectInScope,String issueDate,String expiryDate) {}
}
