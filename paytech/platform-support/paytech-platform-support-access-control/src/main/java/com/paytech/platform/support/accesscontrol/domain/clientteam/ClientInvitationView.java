package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 凭链接查看邀请的结果；预览时邮箱是否一致与 Client 均为空。 */
@Getter
@AllArgsConstructor
public class ClientInvitationView {
    /** 受邀邮箱脱敏值。 */
    private final String emailMasked;

    /** 初始角色代码。 */
    private final String roleCode;

    /** 显示状态代码（含按到期推导的 EXPIRED）。 */
    private final String status;

    /** 到期时间。 */
    private final Instant expiresTime;

    /** 当前账号邮箱是否等于受邀邮箱；预览为空。 */
    private final Boolean emailMatches;

    /** 工作区 Client；预览或邮箱不一致为空。 */
    private final Long clientId;
}
