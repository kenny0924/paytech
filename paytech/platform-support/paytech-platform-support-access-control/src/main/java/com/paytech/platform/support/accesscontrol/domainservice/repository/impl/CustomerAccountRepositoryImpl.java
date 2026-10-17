package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.CustomerAccount;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerAuthenticationMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.CustomerRegistrationMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.CustomerAccountRepository;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

import lombok.AllArgsConstructor;

/** CustomerAccount 的单表持久化实现。 */
@Repository
@AllArgsConstructor
public class CustomerAccountRepositoryImpl implements CustomerAccountRepository {
    private final CustomerRegistrationMapper registration;
    private final CustomerAuthenticationMapper authentication;

    /** 按稳定邮箱 Token 查找 CUSTOMER 人工账号。 */
    @Override
    public CustomerAccount accountByEmail(String token) {
        return registration.accountByEmail(token);
    }

    /** 读取 CUSTOMER 账号当前事实。 */
    @Override
    public CustomerAccount account(long id) {
        return registration.account(id);
    }

    /** 在本地事务中锁定账号，保护认证与恢复的共同不变量。 */
    @Override
    public CustomerAccount lockAccount(long id) {
        return registration.lockAccount(id);
    }

    /** 创建待验证账号，不建立 Tenant 或 Session。 */
    @Override
    public void insertAccount(CustomerAccount row, Instant now) {
        changed(registration.insertAccount(row.id(), row.emailToken(), row.emailMasked(), now));
    }

    /** 按原版本和邮箱激活待验证账号。 */
    @Override
    public void activateAccount(CustomerAccount row, Instant now) {
        changed(registration.activateAccount(row.id(), row.version(), row.emailToken(), now));
        row.status(OperationsAccountStatus.ACTIVE);
        row.verifiedTime(now);
        row.version(row.version() + 1);
    }

    /** 推进账号凭据版本和安全纪元，并清除密码失败状态。 */
    @Override
    public void resetAccount(CustomerAccount row, Instant now) {
        changed(registration.resetAccount(row.id(), row.version(), now));
        row.credentialVersion(row.credentialVersion() + 1);
        row.authEpoch(row.authEpoch() + 1);
        row.failedAttempts(0);
        row.lockedUntil(null);
        row.version(row.version() + 1);
    }

    /** 推进未验证账号的凭据版本，保护重复注册的密码替换。 */
    @Override
    public void replacePendingAccount(CustomerAccount row, Instant now) {
        changed(registration.replacePendingAccount(row.id(), row.version(), now));
        row.credentialVersion(row.credentialVersion() + 1);
        row.authEpoch(row.authEpoch() + 1);
        row.version(row.version() + 1);
    }

    /** 递增失败次数，达到十次时锁定十五分钟。 */
    @Override
    public void passwordFailure(CustomerAccount row, Instant now) {
        changed(authentication.passwordFailure(row.id(), row.version(), now));
        int failures = row.lockedUntil() != null && !row.lockedUntil().isAfter(now) ? 1 : row.failedAttempts() + 1;
        row.failedAttempts(failures);
        row.lockedUntil(failures >= 10 ? now.plusSeconds(900) : null);
        row.version(row.version() + 1);
    }

    /** 按当前账号快照清除失败计数。 */
    @Override
    public void passwordSuccess(CustomerAccount row, Instant now) {
        changed(authentication.passwordSuccess(row.id(), row.version(), row.credentialVersion(), row.authEpoch(), now));
        row.failedAttempts(0);
        row.lockedUntil(null);
        row.version(row.version() + 1);
    }

    /** 条件更新必须准确修改一行；失败时不更新内存对象。 */
    void changed(int count) {
        if (count != 1) {
            throw new ConcurrentRequestException("认证状态已变化，请重新操作");
        }
    }
}
