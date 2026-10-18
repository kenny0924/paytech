package com.paytech.platform.support.api.clientinvitation.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 邀请预览；不含工作区名称、租户或 Client 标识。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientInvitationPreviewResponseBody {

    /** 受邀邮箱脱敏值。 */
    private String emailMasked;

    /** 初始角色代码。 */
    private String roleCode;

    /** PENDING、EXPIRED（按到期时间推导）、ACCEPTED 或 REVOKED。 */
    private String status;

    /** 到期时间，UTC ISO-8601。 */
    private String expiresAt;
}
