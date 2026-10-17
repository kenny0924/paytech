package com.paytech.client.master.domain.onboarding;
/**
 * @param applicationId 原申请
 * @param clientId 原 Client
 * @param subjectPartyId 原主体
 * @param qualificationType 由主体类型派生的资格类型；材料按资格类型通用，不按国家区分
 */
public record ClientMaterialTarget(long applicationId,long clientId,long subjectPartyId,com.paytech.compliance.api.enums.MaterialQualificationType qualificationType) {}
