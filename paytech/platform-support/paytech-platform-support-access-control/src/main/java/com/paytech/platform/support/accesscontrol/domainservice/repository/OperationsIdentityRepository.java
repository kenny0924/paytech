package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSigningKey;

/** Operations Identity 的本库事实访问接口。 */
public interface OperationsIdentityRepository {
    OperationsAccount account(long id);

    OperationsAccount loginAccount(String token, boolean bootstrap);

    OperationsPassword password(long id);

    OperationsMember member(long id);

    Long internalTenant();

    /** 首次管理员交接事务的最终排他仲裁。 */
    OperationsAccount lockBootstrapAccount(long id);

    /** 管理事务内共享锁定操作者，防止权限检查后账号被并发停用或关闭。 */
    Long lockManagementActor(long id);

    /** 共享保护操作者当前 Member，防止授权检查后并发换权。 */
    Long lockManagementMember(long accountId);

    OperationsSession session(long id);

    OperationsSession lockSession(long id);

    OperationsRefresh refresh(String lookupId);

    OperationsSigningKey signingKey();

    int passwordFailure(long id, int expectedCredentialVersion, int threshold, Instant lockedUntil, Instant now);

    int passwordSuccess(long id, int expectedVersion, int expectedCredentialVersion, int expectedAuthEpoch,
        Instant now);

    void insertSession(OperationsSession session);

    void insertRefresh(OperationsRefresh row, Instant now);

    int consumeRefresh(long id, int expectedGeneration, Instant now);

    int linkRefresh(long id, long nextId);

    int advanceSession(long id, int expectedGeneration, int nextGeneration, int expectedAuthEpoch,
        int expectedCredentialVersion, Instant now, Instant idle);

    void revokeSession(long id, String status, String reason, Instant now);

    void revokeRefresh(long id, Instant now);

    int changeAccountPassword(long id, int expectedVersion, int expectedCredentialVersion, int expectedAuthEpoch,
        Instant now);

    int changePassword(long id, int expectedCredentialVersion, String verifier, Instant now);

    int revokeCurrentSession(long id, long accountId, int expectedAuthEpoch, int expectedCredentialVersion,
        String status, String reason, Instant now);

    int revokeAccountSessions(long accountId, String status, String reason, Instant now);

    void insertAccount(long id, String token, String masked, Instant now);

    void insertPassword(long id, long accountId, String verifier, Instant now);

    void insertMember(long id, long tenantId, long accountId, Instant now);

    int disableAccount(long id, int version, long memberId, int memberVersion, Instant now);

    int disableBootstrapAccount(long id, int version, Instant now);

    int enableAccount(long id, int version, long memberId, int memberVersion, Instant now);

    int closeAccount(long id, int version, Instant now);

    int revokeMember(long id, long tenantId, int version, Instant now);

    int revokeAccountRefresh(long id, Instant now);

    void revokePassword(long id, Instant now);
}
