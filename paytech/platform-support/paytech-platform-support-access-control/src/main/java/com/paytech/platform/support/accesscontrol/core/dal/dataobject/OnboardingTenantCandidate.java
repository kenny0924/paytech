package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

/**
 * 具有有效入驻 Owner 授权的 PENDING Client Tenant。
 * @param tenantId 唯一租户
 * @param memberId 当前 ACTIVE OWNER 成员
 * @param clientId CCT 来源 Client，不从外部 Tenant Header 推断
 */
public record OnboardingTenantCandidate(long tenantId, long memberId, long clientId) {}
