package com.paytech.platform.support.accesscontrol.domainservice.repository.impl;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSigningKey;
import com.paytech.platform.support.accesscontrol.core.dal.mapper.OperationsIdentityMapper;
import com.paytech.platform.support.accesscontrol.domainservice.repository.OperationsIdentityRepository;

import lombok.AllArgsConstructor;

/** 封装 Operations Identity 的本库查询、锁与条件写入。 */
@Repository
@AllArgsConstructor
public class OperationsIdentityRepositoryImpl implements OperationsIdentityRepository {
    private final OperationsIdentityMapper mapper;

    @Override
    public Long internalTenant() {
        return mapper.internalTenant();
    }

    @Override
    public OperationsAccount lockBootstrapAccount(long id) {
        return mapper.lockBootstrapAccount(id);
    }

    @Override
    public Long lockManagementActor(long id) {
        return mapper.lockManagementActor(id);
    }

    @Override
    public Long lockManagementMember(long accountId) {
        return mapper.lockManagementMember(accountId);
    }

    @Override
    public OperationsAccount account(long id) {
        return mapper.account(id);
    }

    @Override
    public OperationsAccount loginAccount(String token, boolean bootstrap) {
        return mapper.loginAccount(token, bootstrap);
    }

    @Override
    public OperationsPassword password(long id) {
        return mapper.password(id);
    }

    @Override
    public OperationsMember member(long id) {
        return mapper.member(id);
    }

    @Override
    public OperationsSession session(long id) {
        return mapper.session(id);
    }

    @Override
    public OperationsSession lockSession(long id) {
        return mapper.lockSession(id);
    }

    @Override
    public OperationsRefresh refresh(String lookupId) {
        return mapper.refresh(lookupId);
    }

    @Override
    public OperationsSigningKey signingKey() {
        return mapper.signingKey();
    }

    @Override
    public int passwordFailure(long id, int expectedCredentialVersion, int threshold, Instant lockedUntil,
        Instant now) {
        return mapper.passwordFailure(id, expectedCredentialVersion, threshold, lockedUntil, now);
    }

    @Override
    public int passwordSuccess(long id, int expectedVersion, int expectedCredentialVersion, int expectedAuthEpoch,
        Instant now) {
        return mapper.passwordSuccess(id, expectedVersion, expectedCredentialVersion, expectedAuthEpoch, now);
    }

    @Override
    public void insertSession(OperationsSession session) {
        mapper.insertSession(session);
    }

    @Override
    public void insertRefresh(OperationsRefresh row, Instant now) {
        mapper.insertRefresh(row, now);
    }

    @Override
    public int consumeRefresh(long id, int expectedGeneration, Instant now) {
        return mapper.consumeRefresh(id, expectedGeneration, now);
    }

    @Override
    public int linkRefresh(long id, long nextId) {
        return mapper.linkRefresh(id, nextId);
    }

    @Override
    public int advanceSession(long id, int expectedGeneration, int nextGeneration, int expectedAuthEpoch,
        int expectedCredentialVersion, Instant now, Instant idle) {
        return mapper.advanceSession(id, expectedGeneration, nextGeneration, expectedAuthEpoch,
            expectedCredentialVersion, now, idle);
    }

    @Override
    public void revokeSession(long id, String status, String reason, Instant now) {
        mapper.revokeSession(id, status, reason, now);
    }

    @Override
    public void revokeRefresh(long id, Instant now) {
        mapper.revokeRefresh(id, now);
    }

    @Override
    public int changeAccountPassword(long id, int expectedVersion, int expectedCredentialVersion,
        int expectedAuthEpoch, Instant now) {
        return mapper.changeAccountPassword(id, expectedVersion, expectedCredentialVersion, expectedAuthEpoch, now);
    }

    @Override
    public int changePassword(long id, int expectedCredentialVersion, String verifier, Instant now) {
        return mapper.changePassword(id, expectedCredentialVersion, verifier, now);
    }

    @Override
    public int revokeCurrentSession(long id, long accountId, int expectedAuthEpoch, int expectedCredentialVersion,
        String status, String reason, Instant now) {
        return mapper.revokeCurrentSession(id, accountId, expectedAuthEpoch, expectedCredentialVersion, status, reason,
            now);
    }

    @Override
    public int revokeAccountSessions(long accountId, String status, String reason, Instant now) {
        return mapper.revokeAccountSessions(accountId, status, reason, now);
    }

    @Override
    public void insertAccount(long id, String token, String masked, Instant now) {
        mapper.insertAccount(id, token, masked, now);
    }

    @Override
    public void insertPassword(long id, long accountId, String verifier, Instant now) {
        mapper.insertPassword(id, accountId, verifier, now);
    }

    @Override
    public void insertMember(long id, long tenantId, long accountId, Instant now) {
        mapper.insertMember(id, tenantId, accountId, now);
    }

    @Override
    public int disableAccount(long id, int version, long memberId, int memberVersion, Instant now) {
        return mapper.disableAccount(id, version, memberId, memberVersion, now);
    }

    @Override
    public int disableBootstrapAccount(long id, int version, Instant now) {
        return mapper.disableBootstrapAccount(id, version, now);
    }

    @Override
    public int enableAccount(long id, int version, long memberId, int memberVersion, Instant now) {
        return mapper.enableAccount(id, version, memberId, memberVersion, now);
    }

    @Override
    public int closeAccount(long id, int version, Instant now) {
        return mapper.closeAccount(id, version, now);
    }

    @Override
    public int revokeMember(long id, long tenantId, int version, Instant now) {
        return mapper.revokeMember(id, tenantId, version, now);
    }

    @Override
    public int revokeAccountRefresh(long id, Instant now) {
        return mapper.revokeAccountRefresh(id, now);
    }

    @Override
    public void revokePassword(long id, Instant now) {
        mapper.revokePassword(id, now);
    }

}
