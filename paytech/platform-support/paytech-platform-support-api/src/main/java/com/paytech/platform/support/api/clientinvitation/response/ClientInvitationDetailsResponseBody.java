package com.paytech.platform.support.api.clientinvitation.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已登录账号查看的邀请详情。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvitationDetailsResponseBody {

    /** 受邀邮箱脱敏值。 */
    private String emailMasked;

    /** 初始角色代码。 */
    private String roleCode;

    /** PENDING、EXPIRED（按到期时间推导）、ACCEPTED 或 REVOKED。 */
    private String status;

    /** 到期时间，UTC ISO-8601。 */
    private String expiresAt;

    /** 当前账号邮箱是否等于受邀邮箱。 */
    private boolean emailMatches;

    /** 工作区对应的 Client ID；邮箱不一致时为空，BFF 据此向 CCT 读取工作区名称。 */
    private String clientId;
}
