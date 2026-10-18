package com.paytech.platform.support.api.clientinvitation.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接受邀请的结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvitationAcceptanceResponseBody {

    /**
     * 结果
     * <li>JOINED：本次创建或恢复了成员。</li>
     * <li>ALREADY_MEMBER：本账号已是该工作区成员（含同一邀请的重复接受）。</li>
     */
    private String outcome;

    /** 租户 ID，十进制字符串。 */
    private String tenantId;

    /** 成员 ID，十进制字符串；可用于进入工作区。 */
    private String memberId;

    /** Client ID，十进制字符串。 */
    private String clientId;
}
