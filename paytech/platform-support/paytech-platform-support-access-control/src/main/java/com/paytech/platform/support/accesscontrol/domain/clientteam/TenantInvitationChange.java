package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantInvitation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起、重发或撤销邀请的本地保存上下文；对象均已由 Processor 在本事务内锁定或新建
 * <li>发起：invitation 为新对象；expiredInvitation 为同邮箱已到期、需先置 EXPIRED 的旧邀请（可空）。</li>
 * <li>重发：invitation 为原对象，expiresTime 为新到期；previousChallenge 为被替代的活动挑战（可空）。</li>
 * <li>撤销：invitation 为原对象；previousChallenge 为一并撤销的活动挑战（可空），challenge 为空。</li>
 */
@Getter
@AllArgsConstructor
public class TenantInvitationChange {
    /** 本次发起或变更的邀请。 */
    private final TenantInvitation invitation;

    /** 发起时需先释放活动位的已到期旧邀请。 */
    private final TenantInvitation expiredInvitation;

    /** 本次签发的新挑战；撤销为空。 */
    private final InvitationChallenge challenge;

    /** 被替代或撤销的原活动挑战。 */
    private final InvitationChallenge previousChallenge;

    /** 重发后的新到期时间；发起与撤销为空。 */
    private final Instant expiresTime;

    /** 本次统一 UTC 微秒时间。 */
    private final Instant now;
}
