package com.paytech.client.master.domain.onboarding.requirement;

import java.time.LocalDate;

/**
 * 一份已关联的材料
 *
 * @param documentId 材料引用 ID
 * @param subjectPartyId 材料所属主体
 * @param requirementCode 材料要求代码
 * @param documentType 材料类型
 * @param issueDate 签发日期，可空
 * @param expiryDate 到期日，可空
 * @param usable Platform 文件当前可用且内容未变
 */
public record DocumentFact(long documentId, long subjectPartyId, String requirementCode, String documentType,
    LocalDate issueDate, LocalDate expiryDate, boolean usable) {
}
