package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发起、重发或撤销邀请的结果。 */
@Getter
@AllArgsConstructor
public class TenantInvitationResult {
    /** 邮件发送结果：供应商已受理。 */
    public static final String EMAIL_SENT = "SENT";

    /** 邮件发送结果：未发出，邀请仍有效，可重发。 */
    public static final String EMAIL_FAILED = "FAILED";

    /** 邀请当前事实。 */
    private final TenantInvitationView invitation;

    /** 本次结果计算时刻。 */
    private final Instant asOf;

    /** 邮件发送结果 SENT / FAILED；撤销为空。 */
    private final String emailDelivery;
}
