package com.paytech.client.master.domain.onboarding.requirement;

import java.util.List;

/**
 * compliance 程序解析返回的一项材料要求
 *
 * @param requirementCode 材料槽代码
 * @param subjectRole 适用主体：PRIMARY、ANY_RELATED 或关系规则角色（UBO、DIRECTOR、SIGNATORY 等）
 * @param documentTypes 接受的材料类型；为空表示不限
 * @param required 是否必交
 * @param maxAgeDays 签发日起的最长天数，可空
 * @param waivable 审核是否可豁免
 */
public record MaterialRequirement(String requirementCode, String subjectRole, List<String> documentTypes, boolean required,
    Integer maxAgeDays, boolean waivable) {
}
