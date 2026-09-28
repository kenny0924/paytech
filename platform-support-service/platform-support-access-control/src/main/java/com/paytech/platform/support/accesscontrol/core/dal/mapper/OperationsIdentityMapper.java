package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsAccount;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsPassword;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsRefresh;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSession;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.OperationsSigningKey;

/** Operations Account、Credential、Member 与会话的本库 SQL 边界。 */
public interface OperationsIdentityMapper {

    OperationsAccount account(long id);

    OperationsAccount loginAccount(@Param("token") String token, @Param("bootstrap") boolean bootstrap);

    OperationsPassword password(long id);

    OperationsMember member(long id);

    Long internalTenant();

    OperationsAccount lockBootstrapAccount(long id);

    Long lockManagementActor(long id);

    Long lockManagementMember(long accountId);

    OperationsSession session(long id);

    OperationsSession lockSession(long id);

    OperationsRefresh refresh(String lookupId);

    OperationsSigningKey signingKey();

    int passwordFailure(@Param("id") long id, @Param("expectedCredentialVersion") int expectedCredentialVersion,
        @Param("threshold") int threshold, @Param("lockedUntil") Instant lockedUntil, @Param("now") Instant now);

    int passwordSuccess(@Param("id") long id, @Param("expectedVersion") int expectedVersion,
        @Param("expectedCredentialVersion") int expectedCredentialVersion,
        @Param("expectedAuthEpoch") int expectedAuthEpoch, @Param("now") Instant now);

    int insertSession(OperationsSession session);

    int insertRefresh(@Param("row") OperationsRefresh row, @Param("now") Instant now);

    int consumeRefresh(@Param("id") long id, @Param("expectedGeneration") int expectedGeneration,
        @Param("now") Instant now);

    int linkRefresh(@Param("id") long id, @Param("nextId") long nextId);

    int advanceSession(@Param("id") long id, @Param("expectedGeneration") int expectedGeneration,
        @Param("nextGeneration") int nextGeneration, @Param("expectedAuthEpoch") int expectedAuthEpoch,
        @Param("expectedCredentialVersion") int expectedCredentialVersion, @Param("now") Instant now,
        @Param("idle") Instant idle);

    int revokeSession(@Param("id") long id, @Param("status") String status, @Param("reason") String reason,
        @Param("now") Instant now);

    void revokeRefresh(@Param("id") long id, @Param("now") Instant now);

    int changeAccountPassword(@Param("id") long id, @Param("expectedVersion") int expectedVersion,
        @Param("expectedCredentialVersion") int expectedCredentialVersion,
        @Param("expectedAuthEpoch") int expectedAuthEpoch, @Param("now") Instant now);

    int changePassword(@Param("id") long id, @Param("expectedCredentialVersion") int expectedCredentialVersion,
        @Param("verifier") String verifier, @Param("now") Instant now);

    int revokeCurrentSession(@Param("id") long id, @Param("accountId") long accountId,
        @Param("expectedAuthEpoch") int expectedAuthEpoch,
        @Param("expectedCredentialVersion") int expectedCredentialVersion, @Param("status") String status,
        @Param("reason") String reason, @Param("now") Instant now);

    int revokeAccountSessions(@Param("accountId") long accountId, @Param("status") String status,
        @Param("reason") String reason, @Param("now") Instant now);

    int insertAccount(@Param("id") long id, @Param("token") String token, @Param("masked") String masked,
        @Param("now") Instant now);

    int insertPassword(@Param("id") long id, @Param("accountId") long accountId, @Param("verifier") String verifier,
        @Param("now") Instant now);

    int insertMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("accountId") long accountId,
        @Param("now") Instant now);

    int disableAccount(@Param("id") long id, @Param("version") int version, @Param("memberId") long memberId,
        @Param("memberVersion") int memberVersion, @Param("now") Instant now);

    int disableBootstrapAccount(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    int enableAccount(@Param("id") long id, @Param("version") int version, @Param("memberId") long memberId,
        @Param("memberVersion") int memberVersion, @Param("now") Instant now);

    int closeAccount(@Param("id") long id, @Param("version") int version, @Param("now") Instant now);

    int revokeMember(@Param("id") long id, @Param("tenantId") long tenantId, @Param("version") int version,
        @Param("now") Instant now);

    /** 关闭账号时一并撤销非活动会话遗留的未消费 Refresh，不改写已消费历史。 */
    int revokeAccountRefresh(@Param("id") long id, @Param("now") Instant now);

    int revokePassword(@Param("id") long id, @Param("now") Instant now);
}
