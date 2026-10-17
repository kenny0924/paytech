package com.paytech.platform.support.api.clientteam.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 团队邀请摘要；只含脱敏邮箱，不含链接或 Secret。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientTeamInvitationResponseBody {

    /** 邀请 ID，十进制字符串。 */
    private String invitationId;

    /** 受邀邮箱脱敏值。 */
    private String emailMasked;

    /** 初始角色代码。 */
    private String roleCode;

    /**
     * 邀请状态
     * <li>PENDING、ACCEPTED、REVOKED；PENDING 且已到期时返回 EXPIRED。</li>
     */
    private String status;

    /** 到期时间，UTC ISO-8601。 */
    private String expiresAt;

    /** 发起人账号邮箱脱敏值。 */
    private String invitedByEmailMasked;

    /** 创建时间，UTC ISO-8601。 */
    private String createdAt;

    /**
     * 本次邀请邮件发送结果
     * <li>SENT 或 FAILED；只在发起与重发响应中出现，列表与撤销为空。</li>
     * <li>FAILED 时邀请仍有效，可以重发。</li>
     */
    private String emailDelivery;
}
