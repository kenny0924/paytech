package com.paytech.client.master.service.processor.request;

import com.paytech.client.contract.api.onboarding.ClientDocumentModels.DocumentReferenceRequestBody;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 关联入驻材料的内部请求；要求、类型和文件由 Risk 与 Platform 在事务外核验。 */
@Getter
@AllArgsConstructor
public class AssociateClientDocumentRequest {
    /** 当前 CLIENT 账号。 */
    @Positive
    private final long accountId;
    /** 当前 CLIENT 会话，由 Platform 实时核验。 */
    @Positive
    private final long sessionId;
    /** 申请 ID，也是申请资源锁的 Key。 */
    @Positive
    private final long applicationId;
    /** 材料引用；为空或代码形状不符时按草稿无效拒绝。 */
    private final DocumentReferenceRequestBody document;
}
