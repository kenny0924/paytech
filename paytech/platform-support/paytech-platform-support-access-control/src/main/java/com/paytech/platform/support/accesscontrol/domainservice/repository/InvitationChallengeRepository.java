package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;

/** 邀请接受挑战（t_authentication_challenge 的 TENANT_INVITATION_ACCEPT 用途）持久化合同；成功后同步传入对象。 */
public interface InvitationChallengeRepository {
    /** 按公开编号读取；不存在为空。 */
    InvitationChallenge challenge(String challengeNo);

    /** 行锁读取；不存在为空。 */
    InvitationChallenge lock(long challengeId);

    /** 行锁读取邀请当前的活动挑战；不存在为空。 */
    InvitationChallenge lockActive(long invitationId);

    /** 写入新挑战，仅持久化 HMAC。 */
    void insert(InvitationChallenge challenge);

    /** 替代活动挑战。 */
    void supersede(InvitationChallenge challenge, Instant now);

    /** 撤销活动挑战。 */
    void revoke(InvitationChallenge challenge, Instant now);

    /** 单次消费。 */
    void consume(InvitationChallenge challenge, Instant now);

    /** 记一次失败尝试。 */
    void fail(InvitationChallenge challenge, Instant now);
}
