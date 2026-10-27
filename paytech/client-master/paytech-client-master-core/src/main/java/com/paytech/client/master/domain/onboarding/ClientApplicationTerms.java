package com.paytech.client.master.domain.onboarding;

/**
 * 创建申请时确定并冻结的条款
 *
 * @param jurisdictionCode 辖区代码
 * @param organizationTypeCode 组织类型代码；个人且无个人经营形态时为空
 * @param schemaId 按辖区、主体类型与组织类型解析的 ACTIVE Schema 版本
 * @param onboardingCodeId 使用的入驻码；无码为空
 * @param responsiblePartnerId 入驻码解析出的责任 PayFac；平台自营为空
 */
public record ClientApplicationTerms(String jurisdictionCode, String organizationTypeCode, long schemaId, Long onboardingCodeId,
    Long responsiblePartnerId) {
}
