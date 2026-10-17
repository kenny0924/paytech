package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsTenantOwnerStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块公开的当前来源位置，不公开持久化对象。 */
@Getter
@AllArgsConstructor
public class TenantProjectionResult {
    /** CCT Client 标识。 */
    private final long ownerRefId;

    /** 原申请账号标识。 */
    private final long requestedByAccountId;

    /** 稳定工作区标识。 */
    private final long tenantId;

    /** 初始 OWNER 标识。 */
    private final long memberId;

    /** 已提交的来源业务状态。 */
    private final OperationsTenantOwnerStatus ownerStatus;

    /** 已提交的来源序号。 */
    private final int sourceVersion;

    /** 该来源位置的摘要。 */
    private final String payloadHash;
}
