package com.paytech.client.master.domain.onboarding;

import com.paytech.client.master.domain.enums.ClientType;

/**
 * compliance 程序解析范围（CP-1）
 *
 * @param jurisdictionCode 申请辖区
 * @param partyType 申请主主体类型
 * @param tier 尽调档次：辖区准入策略为 ENHANCED_ONLY 时为 ENHANCED，否则 STANDARD
 * @param responsiblePartnerId 责任 PayFac；平台自营为空
 * @param includeRelated 是否同时解析关联人员（RELATED_PERSON）程序；组织申请为 true
 */
public record ProgramScope(String jurisdictionCode, ClientType partyType, String tier, Long responsiblePartnerId,
    boolean includeRelated) {
}
