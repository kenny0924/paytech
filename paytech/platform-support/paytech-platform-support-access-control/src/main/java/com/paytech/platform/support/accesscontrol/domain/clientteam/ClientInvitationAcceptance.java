package com.paytech.platform.support.accesscontrol.domain.clientteam;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接受邀请的结果。 */
@Getter
@AllArgsConstructor
public class ClientInvitationAcceptance {
    /** 本次创建或恢复了成员。 */
    public static final String JOINED = "JOINED";

    /** 本账号已是该工作区成员（含同一邀请的重复接受）。 */
    public static final String ALREADY_MEMBER = "ALREADY_MEMBER";

    /** JOINED：本次创建或恢复成员；ALREADY_MEMBER：本账号已是成员（含同一邀请重复接受）。 */
    private final String outcome;

    /** 工作区租户。 */
    private final long tenantId;

    /** 成员。 */
    private final long memberId;

    /** 工作区 Client。 */
    private final long clientId;
}
