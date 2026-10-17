package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.InvitationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.ClientTeamMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.InvitationChallengeRepository;

import lombok.AllArgsConstructor;

/** 邀请接受挑战的持久化实现；不开启事务。 */
@Repository
@AllArgsConstructor
public class InvitationChallengeRepositoryImpl implements InvitationChallengeRepository {
    private final ClientTeamMapper mapper;

    /** 按公开编号读取；不存在为空。 */
    @Override
    public InvitationChallenge challenge(String challengeNo) {
        return mapper.challenge(challengeNo);
    }

    /** 行锁读取；不存在为空。 */
    @Override
    public InvitationChallenge lock(long challengeId) {
        return mapper.lockChallenge(challengeId);
    }

    /** 行锁读取邀请当前的活动挑战；不存在为空。 */
    @Override
    public InvitationChallenge lockActive(long invitationId) {
        return mapper.lockActiveChallenge(invitationId);
    }

    /** 写入新挑战。 */
    @Override
    public void insert(InvitationChallenge challenge) {
        requireOne(mapper.insertChallenge(challenge));
    }

    /** 替代活动挑战。 */
    @Override
    public void supersede(InvitationChallenge challenge, Instant now) {
        requireOne(mapper.supersedeChallenge(challenge.id(), challenge.version(), now));
        challenge.status(ClientChallengeStatus.SUPERSEDED);
        challenge.version(challenge.version() + 1);
    }

    /** 撤销活动挑战。 */
    @Override
    public void revoke(InvitationChallenge challenge, Instant now) {
        requireOne(mapper.revokeChallenge(challenge.id(), challenge.version(), now));
        challenge.status(ClientChallengeStatus.REVOKED);
        challenge.version(challenge.version() + 1);
    }

    /** 单次消费。 */
    @Override
    public void consume(InvitationChallenge challenge, Instant now) {
        requireOne(mapper.consumeChallenge(challenge.id(), challenge.version(), now));
        challenge.status(ClientChallengeStatus.CONSUMED);
        challenge.version(challenge.version() + 1);
    }

    /** 记一次失败尝试，与 SQL 相同地推导到期或用尽。 */
    @Override
    public void fail(InvitationChallenge challenge, Instant now) {
        requireOne(mapper.failChallenge(challenge.id(), challenge.version(), now));
        challenge.usedAttempts(Math.min(challenge.usedAttempts() + 1, challenge.maxAttempts()));
        challenge.status(!challenge.expiresTime().isAfter(now) ? ClientChallengeStatus.EXPIRED
            : challenge.usedAttempts() >= challenge.maxAttempts() ? ClientChallengeStatus.REVOKED
                : ClientChallengeStatus.PENDING);
        challenge.version(challenge.version() + 1);
    }

    /** 写入必须准确影响一行；失败时不修改内存对象。 */
    void requireOne(int rows) {
        if (rows != 1) {
            throw new ConcurrentRequestException("邀请链接状态已变化，请重新打开邮件链接");
        }
    }
}
