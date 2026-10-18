package com.paytech.platform.support.accesscontrol.domain.authentication;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ClientMember;

/**
 * 账号的一个 CLIENT 工作区的公开投影
 *
 * @param memberId 成员编号
 * @param tenantId 租户编号
 * @param clientId 来源 Client 编号
 * @param ownerStatus 租户来源状态代码
 * @param securityStatus 租户安全状态代码
 * @param memberStatus 成员状态代码
 * @param accessible 是否可以进入
 * @param reasonCode 不可进入时的公开原因码
 */
public record ClientWorkspaceView(long memberId, long tenantId, long clientId, String ownerStatus,
    String securityStatus, String memberStatus, boolean accessible, String reasonCode) {

    /** 由成员查询投影，不携带运营内部暂停理由。 */
    public static ClientWorkspaceView of(ClientMember member) {
        return new ClientWorkspaceView(member.id(), member.tenantId(), member.clientId(), member.ownerStatus().getCode(),
            member.securityStatus().getCode(), member.status().getCode(), ClientAccountPolicy.accessible(member),
            ClientAccountPolicy.reason(member));
    }
}
