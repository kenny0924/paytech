package com.paytech.client.contract.domain.onboarding;
/**
 * @param applicationId 原申请
 * @param clientId 原 Client
 * @param subjectPartyId 原主体
 * @param qualificationType 由主体类型派生的资格类型
 * @param countryCode 个人居住国或组织注册国
 */
public record ClientMaterialTarget(long applicationId,long clientId,long subjectPartyId,com.paytech.risk.api.enums.MaterialQualificationType qualificationType,String countryCode) {}
