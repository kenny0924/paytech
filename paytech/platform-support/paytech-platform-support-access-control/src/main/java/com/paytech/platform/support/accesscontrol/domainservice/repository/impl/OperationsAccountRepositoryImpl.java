package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsDirectoryMapper;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsAccountRepository;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

import lombok.AllArgsConstructor;

/** 条件写成功后同步原对象；事务由完整用例控制。 */
@Repository
@AllArgsConstructor
public class OperationsAccountRepositoryImpl implements OperationsAccountRepository {
    private final OperationsIdentityMapper mapper;
    private final OperationsDirectoryMapper directory;
    /** 读取指定 OPERATIONS 账号。 */
    @Override
    public OperationsAccount account(long id) {
        return mapper.account(id);
    }

    /** 按批准的邮箱 Token 或 Bootstrap 标识读取账号。 */
    @Override
    public OperationsAccount loginAccount(String token, boolean bootstrap) {
        return mapper.loginAccount(token, bootstrap);
    }

    /** 首次管理员交接的最终数据库仲裁。 */
    @Override
    public OperationsAccount lockBootstrapAccount(long id) {
        return mapper.lockBootstrapAccount(id);
    }

    /** 统计已有 Operations 人员账号。 */
    @Override
    public long humanAccountCount() {
        return directory.humanAccountCount();
    }

    /** 保存密码失败次数及临时锁定。 */
    @Override
    public void passwordFailure(OperationsAccount account, Instant now) {
        requireOne(mapper.passwordFailure(account.id(), account.credentialVersion(), 5, now.plusSeconds(1800), now));
        account.failures(account.failures() + 1);
        account.lockedUntil(account.failures() >= 5 ? now.plusSeconds(1800) : null);
        account.version(account.version() + 1);
    }

    /** 按当前版本接受密码并清除失败次数。 */
    @Override
    public void passwordSuccess(OperationsAccount account, Instant now) {
        requireOne(mapper.passwordSuccess(account.id(), account.version(), account.credentialVersion(), account.authEpoch(), now));
        account.failures(0);
        account.lockedUntil(null);
        account.version(account.version() + 1);
    }

    /** 提升密码版本及账号撤销纪元。 */
    @Override
    public void changePassword(OperationsAccount account, Instant now) {
        requireOne(mapper.changeAccountPassword(account.id(), account.version(), account.credentialVersion(), account.authEpoch(), now));
        account.credentialVersion(account.credentialVersion() + 1);
        account.authEpoch(account.authEpoch() + 1);
        account.version(account.version() + 1);
    }

    /** 插入新人员账号；唯一邮箱由数据库保护。 */
    @Override
    public void insert(OperationsAccount account, Instant now) {
        requireOne(mapper.insertAccount(account.id(), account.emailToken(), account.emailMasked(), now));
    }

    /** 永久关闭 Bootstrap，不允许后续恢复。 */
    @Override
    public void disableBootstrap(OperationsAccount account, Instant now) {
        requireOne(mapper.disableBootstrapAccount(account.id(), account.version(), now));
        account.status(OperationsAccountStatus.DISABLED);
        account.authEpoch(account.authEpoch() + 1);
        account.version(account.version() + 1);
    }

    /** 条件写必须影响恰好一行，否则使整个本地事务回滚。 */
    void requireOne(int rows) {
        if (rows != 1) { throw new ConcurrentRequestException("认证事实已变化，请重新操作"); }
    }

    /** 成功后直接同步账号原对象。 */
    @Override
    public void disable(OperationsAccount account, OperationsMember member, Instant now) {
        requireOne(mapper.disableAccount(account.id(), account.version(), member.id(), member.version(), now));
        account.status(OperationsAccountStatus.DISABLED);
        advance(account);
    }

    /** 成功后直接同步账号原对象，不清理密码安全状态。 */
    @Override
    public void enable(OperationsAccount account, OperationsMember member, Instant now) {
        requireOne(mapper.enableAccount(account.id(), account.version(), member.id(), member.version(), now));
        account.status(OperationsAccountStatus.ACTIVE);
        advance(account);
    }

    /** 条件关闭保留身份历史及邮箱唯一占用。 */
    @Override
    public void close(OperationsAccount account, Instant now) {
        requireOne(mapper.closeAccount(account.id(), account.version(), now));
        account.status(OperationsAccountStatus.CLOSED);
        advance(account);
    }

    /** 与账号状态 SQL 的版本和安全纪元更新保持一致。 */
    void advance(OperationsAccount account) {
        account.version(account.version() + 1);
        account.authEpoch(account.authEpoch() + 1);
    }
}
