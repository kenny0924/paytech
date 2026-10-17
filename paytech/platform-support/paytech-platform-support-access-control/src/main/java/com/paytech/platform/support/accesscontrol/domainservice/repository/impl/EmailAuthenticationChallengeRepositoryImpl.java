package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.EmailAuthenticationChallenge;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerRegistrationMapper;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.EmailAuthenticationChallengeRepository;

import lombok.AllArgsConstructor;

/** EmailAuthenticationChallenge 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class EmailAuthenticationChallengeRepositoryImpl implements EmailAuthenticationChallengeRepository {
    private final CustomerRegistrationMapper registration;

    /** 读取该账号同用途的最近挑战，供重发冷却判断。 */
    @Override
    public EmailAuthenticationChallenge latestChallenge(long accountId, ClientChallengePurpose purpose) {
        return registration.latestChallenge(accountId, purpose);
    }

    /** 按公开编号查找 CUSTOMER 邮件挑战。 */
    @Override
    public EmailAuthenticationChallenge challenge(String challengeNo) {
        return registration.challenge(challengeNo);
    }

    /** 在账号锁之后锁定原挑战并刷新查询缓存。 */
    @Override
    public EmailAuthenticationChallenge lockChallenge(long id) {
        return registration.lockChallenge(id);
    }

    /** 保存新挑战，仅持久化 HMAC。 */
    @Override
    public void insertChallenge(EmailAuthenticationChallenge row) {
        changed(registration.insertChallenge(row));
    }

    /** 按状态与版本关闭原活动挑战。 */
    @Override
    public void supersedeChallenge(EmailAuthenticationChallenge row, Instant now) {
        changed(registration.supersedeChallenge(row.id(), row.version(), now));
        row.status(ClientChallengeStatus.SUPERSEDED);
        row.version(row.version() + 1);
    }

    /** 单次消费仍在有效期和尝试上限内的挑战。 */
    @Override
    public void consumeChallenge(EmailAuthenticationChallenge row, Instant now) {
        changed(registration.consumeChallenge(row.id(), row.version(), now));
        row.status(ClientChallengeStatus.CONSUMED);
        row.version(row.version() + 1);
    }

    /** 保存失败次数，并在到期或耗尽时关闭挑战。 */
    @Override
    public void failChallenge(EmailAuthenticationChallenge row, Instant now) {
        changed(registration.failChallenge(row.id(), row.version(), now));
        row.usedAttempts(Math.min(row.usedAttempts() + 1, row.maxAttempts()));
        row.status(!row.expiresTime().isAfter(now) ? ClientChallengeStatus.EXPIRED
            : row.usedAttempts() >= row.maxAttempts() ? ClientChallengeStatus.REVOKED : ClientChallengeStatus.PENDING);
        row.version(row.version() + 1);
    }

    /** 条件更新必须准确修改一行；失败时不更新内存对象。 */
    void changed(int count) {
        if (count != 1) {
            throw new ConcurrentRequestException("认证状态已变化，请重新操作");
        }
    }
}
